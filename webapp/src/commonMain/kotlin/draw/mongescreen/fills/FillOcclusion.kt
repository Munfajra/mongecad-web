package draw.mongescreen.fills

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asSkiaPath
import model.Offset3D
import model.classes.ConicSection3D
import model.classes.IntersectionGroup
import model.classes.IntersectionPartKind
import model.normalize
import monge.input.intersections.ops.pointInPolygon2D
import monge.input.intersections.ops.sorOpenRimCircles3D
import monge.input.intersections.ops.sorProjectedSilhouette
import geometry.conics.computeEllipseAxes3D
import state.MongeState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Prefix id obrysových křivek rotační plochy v axonometrii. Na desktopu ho
 * vlastní axonometrické kreslení (draw.mongescreen.objects.axo), které web
 * nemá – větve níž tak nikdy nic nenajdou, ale zůstávají kvůli shodě s desktopem.
 */
private const val SOR_AXO_CONTOUR_ID_PREFIX = "sorAxoContour"

/**
 * Vektorový occlusion výplní podle průnikové křivky.
 *
 * Pro každou [IntersectionGroup] (dvojice těles):
 *  1. siluety obou těles jako polygony (SoR může být nekonvexní) → překryv = jejich průnik,
 *  2. průniková křivka (oblouky/úsečky/curve) sešitá do souvislých řetězců a odfiltrovaná na
 *     VIDITELNÉ části (bod je viditelný, jen když leží na přední ploše obou těles – test hloubkou),
 *  3. překryv postupně rozřežeme viditelnými řetězci na plošky,
 *  4. každou plošku klasifikujeme hloubkou (kdo je vpředu) a výplň poraženého tělesa = jeho plošky.
 *
 * Hranice = přesně viditelná průniková křivka. Rozdělení je úplné → žádné díry, žádné pixely.
 * Výstup: `solidId → clip Path` (logické souřadnice), aplikuje se v Draw*Fills přes Path.op(Difference).
 */

private const val PI_F = PI.toFloat()
private const val CURVE_SAMPLES = 96
private const val VIS_TOL_FRAC = 0.04f   // tolerance hloubky pro test viditelnosti (podíl rozsahu tělesa)

private class Poly3D(val pts: List<Offset3D>, val closed: Boolean)
private class ProjectedArc(val pts: List<Offset>, val closed: Boolean)
private class OcclusionSilhouette(
    val polygon: List<Offset>,
    val clipPath: Path,
    val preciseSorPath: Boolean = false,
)
private class FaceRegion(
    val outer: List<Offset>,
    val path: Path,
    val holes: List<List<Offset>> = emptyList(),
)
private class RegionMask(
    val path: org.jetbrains.skia.Path,
)

private object OcclusionCache {
    // Dost položek, aby přepínání tabů (každý tab má 1–2 pohledy) nevyhazovalo
    // cache ostatních výkresů a nevynucovalo přepočet při každém návratu.
    private const val MAX_ENTRIES = 24
    private val cache = LinkedHashMap<String, Map<String, Path>>()
    private val inFlight = HashSet<String>()

    // Přístup je synchronizovaný – během výpočtu průniku warmuje cache pozadní vlákno,
    // zatímco UI vlákno se dál překresluje (animace spinneru) a cache čte.
    fun peek(signature: String): Map<String, Path>? = cache[signature]

    private fun store(signature: String, result: Map<String, Path>) {
        cache[signature] = result
        if (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
    }

    /** Rezervuje podpis pro asynchronní výpočet; false = hotovo nebo už se počítá. */
    fun tryBegin(signature: String): Boolean {
        if (signature in cache || signature in inFlight) return false
        inFlight.add(signature)
        return true
    }

    fun end(signature: String) {
        inFlight.remove(signature)
    }

    fun getOrCompute(signature: String, compute: () -> Map<String, Path>): Map<String, Path> {
        peek(signature)?.let { return it }
        val result = compute()           // drahý výpočet mimo zámek (Path.op)
        store(signature, result)
        return result
    }
}

/**
 * Předpočítá occlusion cache pro aktuální projekční mód MIMO kreslicí (UI) vlákno. Volá se
 * z pozadí po dokončení průniku, aby drahý výpočet (rozřezání siluet + `Path.op`) neproběhl
 * až při prvním překreslení a nezmrazil UI. Podpis cache ignoruje scale/offset, takže se
 * warm hodnota trefí i po posunu/zoomu; kreslení pak jen čte hotovou cestu.
 *
 * Bezpečnost: běží, dokud je nahoře modální „počítám průnik", takže do stavu nikdo nezapisuje.
 * Čte jen 3D kolekce a promítání – žádný Compose objekt nemodifikuje.
 */
fun warmOcclusionCache(state: MongeState) {
    if (state.intersectionGroups.isEmpty()) return
    runCatching {
        when (state.projectionMode) {
            model.ProjectionMode.AXO -> {
                val basis = state.basis ?: return
                val view = FillView.axo(state, basis)
                occlusionClipsForView(state, view, quadricFillsAxo(state, basis))
            }
            else -> {
                val pudView = FillView.pudorys(state)
                occlusionClipsForView(state, pudView, quadricFillsPudorys(state))
                val narView = FillView.narys(state)
                occlusionClipsForView(state, narView, quadricFillsNarys(state))
            }
        }
    }
}

/**
 * Vrací mapu `solidId → LOGICKÁ cesta oblasti, kterou má výplň tělesa VYNECHAT` (occlusion clip).
 * Je to jen sjednocení poražených plošek (pár polygonů) – kreslení ho odečte přes
 * `clipPath(Difference)`, takže se **nedělá žádný `Path.op` nad výplní** (u rotačních ploch by
 * to byly tisíce kontur → sekundy a rozbitý winding díry). Výsledek je cachovaný podle pohledu.
 *
 * Volá se z kreslení (UI vlákno) → NIKDY nepočítá synchronně. Při cache miss (otevření
 * souboru, návrat do vyhozeného tabu, nová axonometrie) naplánuje výpočet na pozadí a vrátí
 * prázdno; výplně se pár framů kreslí bez occlusionu a po dokončení se vyžádá překreslení.
 * Dřív tu výpočet běžel synchronně a zamrzl celou aplikaci včetně progress dialogů.
 */
internal fun computeOcclusionClips(
    state: MongeState,
    view: FillView,
    quadricFills: List<QuadricFill>,
): Map<String, Path> {
    if (state.intersectionGroups.isEmpty()) return emptyMap()
    val signature = occlusionSignature(state, view)
    OcclusionCache.peek(signature)?.let { return it }
    // Během výpočtu průniku cache warmuje pozadí (warmOcclusionCache) – neplánovat duplicitně.
    if (!state.intersectionComputing) scheduleOcclusionCompute(state, view, quadricFills, signature)
    return emptyMap()
}

private fun scheduleOcclusionCompute(
    state: MongeState,
    view: FillView,
    quadricFills: List<QuadricFill>,
    signature: String,
) {
    if (!OcclusionCache.tryBegin(signature)) return
    // Desktop tenhle výpočet pouští na vlastním vlákně a do stavu zapisuje přes EDT.
    // Wasm je jednovláknový, takže se místo toho jen odloží do další smyčky událostí –
    // rekompozice tak stihne doběhnout dřív, než výpočet zabere UI.
    occlusionScope.launch {
        yield()
        try {
            // Selhání se cachuje jako prázdný clip – jinak by ho každý frame plánoval znovu.
            val result = runCatching { computeClips(state, view, quadricFills) }.getOrElse { emptyMap() }
            OcclusionCache.getOrCompute(signature) { result }
        } finally {
            OcclusionCache.end(signature)
            state.triggerRedraw++
        }
    }
}

private val occlusionScope = CoroutineScope(Dispatchers.Main)

/**
 * Synchronní varianta pro exporty (PDF/bitmap preview) – tam výsledek musí být hned
 * a volající už běží mimo UI vlákno.
 */
internal fun computeOcclusionClipsBlocking(
    state: MongeState,
    view: FillView,
    quadricFills: List<QuadricFill>,
): Map<String, Path> = occlusionClipsForView(state, view, quadricFills)

// Vlastní výpočet clipů (cachovaný podle podpisu) BEZ guardu na intersectionComputing – volá ho
// jak kreslení (mimo výpočet průniku), tak `warmOcclusionCache` z pozadního vlákna.
private fun occlusionClipsForView(
    state: MongeState,
    view: FillView,
    quadricFills: List<QuadricFill>,
): Map<String, Path> {
    if (state.intersectionGroups.isEmpty()) return emptyMap()
    val signature = occlusionSignature(state, view)
    return OcclusionCache.getOrCompute(signature) {
        computeClips(state, view, quadricFills)
    }
}

/**
 * Odečte occlusion clip od (libovolné) logické výplně přes `Path.op(Difference)`. Používá jen
 * diagnostický harness – u interaktivního kreslení i exportů se místo toho klipuje plátno
 * (`clipPath` / even-odd clip v PDF), aby se drahý a křehký boolean nad tisíci konturami
 * rotační plochy vůbec nedělal.
 */
internal fun differencedFillPath(logical: Path, clip: Path?): Path {
    if (clip == null || clip.isEmpty) return logical
    return Path().also { it.op(logical, clip, PathOperation.Difference) }
}

/**
 * Occlusion clipy pro PDF export – stejné oblasti jako [computeOcclusionClipsBlocking], navíc
 * normalizované self-unionem na nepřekrývající se kontury. PDF neumí Difference clip; emuluje
 * se even-odd clipem (krycí obdélník + kontury clipu) a even-odd vyžaduje, aby se kusy clipu
 * nepřekrývaly – průnik dvou kusů by jinak vyšel jako díra (occlusion by tam zmizel).
 */
internal fun computeOcclusionClipsForPdf(
    state: MongeState,
    view: FillView,
    quadricFills: List<QuadricFill>,
): Map<String, Path> =
    occlusionClipsForView(state, view, quadricFills).mapValues { (_, clip) ->
        val normalized = Path()
        // Selhání pathops → původní clip; kusy se překrývají jen výjimečně (více průniků
        // nad týmž tělesem), takže výsledek je nejhůř stejný jako dřív.
        if (normalized.op(clip, clip, PathOperation.Union)) normalized else clip
    }

// Diagnostika occlusion pipeline: OCCL_DEBUG=1 zapne výpisy rozhodování (siluety,
// řezy, hlasování, dělení) – hodí se při ladění špatné klasifikace vpředu/vzadu.
// Na wasm nejsou proměnné prostředí – přepni ručně při ladění occlusion.
private const val OCCL_DEBUG = false
private fun occlLog(msg: String) { if (OCCL_DEBUG) println("[occl] $msg") }

private fun computeClips(
    state: MongeState,
    view: FillView,
    quadricFills: List<QuadricFill>,
): Map<String, Path> {
    val silhouettes = collectSilhouettes(state, view, quadricFills)
    occlLog("rayDir=${view.rayDir} silhouettes=${silhouettes.mapValues { it.value.polygon.size }}")
    if (silhouettes.isEmpty()) return emptyMap()

    // Poražené kusy per těleso. Kusy s vítězem se na konci oříznou skutečnou
    // výplní vítěze (clip ⊆ výplň vítěze). coveredPieces jsou už hotové kusy,
    // které se dál neořezávají.
    val coveredPieces = HashMap<String, Path>()                  // loserId → Path (hotové)
    val facePieces = HashMap<String, HashMap<String, Path>>()    // loserId → (winnerId → Path)
    fun addLoserPath(id: String, winnerId: String?, piece: Path) {
        if (piece.isEmpty) return
        if (winnerId == null) coveredPieces.getOrPut(id) { Path() }.addPath(piece)
        else facePieces.getOrPut(id) { HashMap() }.getOrPut(winnerId) { Path() }.addPath(piece)
    }

    for (group in state.intersectionGroups) {
        val a = resolveOcclusionSolid(state, group.operandAId) ?: continue
        val b = resolveOcclusionSolid(state, group.operandBId) ?: continue
        val silhouetteA = silhouettes[a.id] ?: continue
        val silhouetteB = silhouettes[b.id] ?: continue
        val polyA = silhouetteA.polygon
        val polyB = silhouetteB.polygon

        // Stejná cesta jako SoR × koule: pokud je jedna projekční silueta konvexní,
        // použije se jako clip a druhá (klidně nekonvexní) jako subject. U SoR × SoR
        // je to podstatné hlavně pro kuželové rotační plochy, jejichž silueta je
        // v daném pohledu často konvexní a není důvod padat do hrubého obalu. Když
        // jsou nekonvexní obě, vezme se skutečná silueta jedné proti obalu druhé
        // a vybere se menší platný překryv.
        fun mayBeNonConvex(solid: OcclusionSolid): Boolean =
            solid is OcclusionSolid.Sor || solid is OcclusionSolid.Ruled
        val aConvex = !mayBeNonConvex(a) || polygonIsConvex(polyA)
        val bConvex = !mayBeNonConvex(b) || polygonIsConvex(polyB)
        val overlap = when {
            bConvex -> convexPolygonIntersection(polyA, polyB)
            aConvex -> convexPolygonIntersection(polyB, polyA)
            else -> {
                val hullA = segmentSolidConvexHull(polyA)
                val hullB = segmentSolidConvexHull(polyB)
                val aAgainstBHull = convexPolygonIntersection(polyA, hullB)
                val bAgainstAHull = convexPolygonIntersection(polyB, hullA)
                when {
                    aAgainstBHull.size < 3 -> bAgainstAHull
                    bAgainstAHull.size < 3 -> aAgainstBHull
                    abs(polygonSignedArea(aAgainstBHull)) <= abs(polygonSignedArea(bAgainstAHull)) -> aAgainstBHull
                    else -> bAgainstAHull
                }
            }
        }
        if (overlap.size < 3) continue
        val diag = polygonDiagonal(overlap)
        val tol = diag * 0.02f + 1e-3f
        occlLog("group ${a.id.take(8)}x${b.id.take(8)} overlap=${overlap.size} area=${polygonSignedArea(overlap)} tol=$tol")

        val probeA = buildSolidProbe(state, a, view.rayDir) ?: continue
        val probeB = buildSolidProbe(state, b, view.rayDir) ?: continue
        occlLog("probeA t=[${probeA.tLo},${probeA.tHi}] probeB t=[${probeB.tLo},${probeB.tHi}]")
        val classificationMasks =
            if (a is OcclusionSolid.Sor && b is OcclusionSolid.Sor) {
                listOf(
                    RegionMask(silhouetteA.clipPath.asSkiaPath()),
                    RegionMask(silhouetteB.clipPath.asSkiaPath()),
                )
            } else {
                emptyList()
            }

        // viditelné 2D řetězce průnikové křivky + hrany/obrysy rotačních ploch. Přes hranu
        // otevřeného konce meridiánu (a přes fold pravého axo-obrysu) skáče přední hloubka
        // SoR mezi přední a zadní stěnou – bez řezu by region vyšel hloubkově smíšený a
        // většinové hlasování by přebarvilo i viditelný kus druhého tělesa (koule viditelná
        // otvorem hyperboloidu v AXO). visibleRuns hrany ořeže na úseky před oběma tělesy.
        val chains3D = stitch3D(intersectionCurves3D(state, group)) +
            sorEdgeChains(state, view, a) + sorEdgeChains(state, view, b) +
            ruledEdgeChains(state, view, a) + ruledEdgeChains(state, view, b)
        val arcs = ArrayList<ProjectedArc>()
        for (chain in chains3D) arcs += visibleRuns(chain, view, probeA, probeB)
        val openArcs = arcs.filter { !it.closed }
        val closedArcs = arcs.filter { it.closed }
        occlLog(
            "chains=${chains3D.map { it.pts.size }} " +
                "openRuns=${openArcs.map { it.pts.size }} closedRuns=${closedArcs.map { it.pts.size }}"
        )

        // postupné řezání překryvu
        var faces = listOf(overlap)
        for (arc in openArcs) {
            if (arc.pts.size < 2) continue
            val mid = arc.pts[arc.pts.size / 2]
            val next = ArrayList<List<Offset>>(faces.size + 1)
            for (face in faces) {
                if (pointInPolygon2D(mid, face)) {
                    // protáhni konce oblouku k hranici plošky (řez proběhne i s mezerou v křivce)
                    val cutArc = bridgeArcToBoundary(face, arc.pts, tol)
                    val cut = cutPolygonByArc(face, cutArc, tol)
                    if (cut != null) {
                        next.add(cut.first); next.add(cut.second)
                    } else {
                        occlLog("CUT FAILED arc=${arc.pts.size} faceArea=${polygonSignedArea(face)}")
                        next.add(face)
                    }
                } else next.add(face)
            }
            faces = next
        }
        occlLog("faces=${faces.map { polygonSignedArea(it) }}")
        val regions = splitFacesByClosedArcs(
            faces.map { face -> FaceRegion(ensureCcw(face), facesToPath(listOf(face))) },
            closedArcs,
            tol,
        )
        occlLog("regions=${regions.size} holes=${regions.count { it.holes.isNotEmpty() }}")

        // Klasifikace hotových vektorových regionů. Hranice regionů smí být jen obrys A,
        // obrys B nebo viditelná průniková křivka (otevřený oblouk i uzavřená smyčka).
        // Pokud zůstane region numericky smíšený, použije se většina hlasů, ale tvar
        // clipu se už neodvozuje z hloubkové mřížky.
        val regionVotes = regions.map { classifyRegionVotes(view, probeA, probeB, it, classificationMasks) }
        val anyMixed = regionVotes.any { it != null && it.first > 0 && it.second > 0 }
        if (!anyMixed) {
            for ((region, votes) in regions.zip(regionVotes)) {
                if (votes == null) {
                    occlLog("region area=${polygonSignedArea(region.outer)} UNDECIDED")
                    continue
                }
                val (aVotes, bVotes) = votes
                occlLog("region area=${polygonSignedArea(region.outer)} winner=${if (bVotes == 0) a.id.take(8) else b.id.take(8)} ($aVotes:$bVotes)")
                val winner = if (bVotes == 0) a else b
                val loser = if (bVotes == 0) b else a
                if (mayBeNonConvex(winner)) {
                    // AXO SoR (a přímková plocha) může mít uvnitř projekce díru nebo
                    // samo-překryv. Samotná obrysová silueta nestačí: otevřené viditelné
                    // větve by se při ručním uzavření změnily na rovnou hranu. Proto
                    // region nejdřív zmenšíme přesnou siluetou a konečný tvar necháme
                    // oříznout skutečnou výplní vítěze v závěrečném kroku.
                    addLoserPath(loser.id, winner.id, clipPathToSilhouetteOrKeep(region.path, silhouettes[winner.id]))
                } else {
                    addLoserPath(loser.id, winner.id, region.path)
                }
            }
        } else {
            occlLog("group has MIXED regions (${regionVotes.map { "${it?.first}:${it?.second}" }}) → vector majority fallback")
            for ((region, votes) in regions.zip(regionVotes)) {
                if (votes == null) continue
                val (aVotes, bVotes) = votes
                val winner = if (aVotes >= bVotes) a else b
                val loser = if (aVotes >= bVotes) b else a
                if (mayBeNonConvex(winner)) {
                    addLoserPath(loser.id, winner.id, clipPathToSilhouetteOrKeep(region.path, silhouettes[winner.id]))
                } else {
                    addLoserPath(loser.id, winner.id, region.path)
                }
            }
        }
    }
    if (coveredPieces.isEmpty() && facePieces.isEmpty()) return emptyMap()

    val coverCache = HashMap<String, Path?>()
    fun winnerCover(id: String): Path? =
        coverCache.getOrPut(id) {
            val solid = resolveOcclusionSolid(state, id)
            val silhouette = silhouettes[id]
            val precise = silhouette?.preciseSorPath == true &&
                (solid is OcclusionSolid.Sor || solid is OcclusionSolid.Ruled)
            if (precise) {
                // SoR/přímková plocha: fill je tvořen tisíci malých kontur. Intersect
                // s ním je pomalý a rozbíjí winding/díry; precizní projekční silueta už
                // reprezentuje viditelnou výplňovou oblast včetně děr.
                silhouette!!.clipPath
            } else {
                actualFillPathLogical(state, view, quadricFills, id)
            }
        }

    val out = HashMap<String, Path>()
    for ((loserId, piece) in coveredPieces) {
        out.getOrPut(loserId) { Path() }.addPath(piece)
    }
    for ((loserId, byWinner) in facePieces) {
        val acc = out.getOrPut(loserId) { Path() }
        for ((winnerId, piecePath) in byWinner) {
            val cover = winnerCover(winnerId)
            var piece = piecePath
            if (cover != null && !cover.isEmpty) {
                val clipped = Path()
                // Selhání pathops → ponech neoříznutý kus (jen se ztratí garance
                // clip ⊆ výplň vítěze, geometrie kusu je jinak platná).
                if (clipped.op(piecePath, cover, PathOperation.Intersect)) piece = clipped
                else occlLog("winnerCover intersect FAILED for winner=${winnerId.take(8)}")
            }
            acc.addPath(piece)
        }
    }
    return out
}

// ───────────────────────── vektorové dělení podle průnikových smyček ─────────────────────────

/**
 * Mrtvé pásmo pro srovnání hloubek: kde se přední plochy liší méně, jsou prakticky tečné
 * (okolí průnikové křivky) a znaménko rozdílu je jen šum vzorkování (chordy profilu SoR).
 * Takové místo se nechá bez clipu – obě výplně se překryjí, což je u tečného dotyku
 * vizuálně korektní; tvrdé rozhodnutí by tam kreslilo náhodné pruhy.
 */
private const val DEPTH_TIE_FRAC = 5e-3f

private fun depthTieTolerance(probeA: SolidProbe, probeB: SolidProbe): Float =
    DEPTH_TIE_FRAC * min(probeA.tHi - probeA.tLo, probeB.tHi - probeB.tLo) + 1e-4f

private fun splitFacesByClosedArcs(
    faces: List<FaceRegion>,
    arcs: List<ProjectedArc>,
    tol: Float,
): List<FaceRegion> {
    if (arcs.isEmpty()) return faces
    var regions = faces
    for (arc in arcs) {
        val loop = closedArcLoop(arc.pts, tol) ?: continue
        val loopPath = facesToPath(listOf(loop))
        val next = ArrayList<FaceRegion>(regions.size + 1)
        for (region in regions) {
            val split = splitRegionByLoop(region, loop, loopPath)
            if (split == null) next += region else next += split
        }
        regions = next
    }
    return regions
}

private fun closedArcLoop(pts: List<Offset>, tol: Float): List<Offset>? {
    if (pts.size < 3) return null
    val cleaned = ArrayList<Offset>(pts.size)
    val eps = tol * 0.15f + 1e-4f
    for (p in pts) {
        if (cleaned.isEmpty() || (p - cleaned.last()).getDistance() > eps) cleaned += p
    }
    if (cleaned.size > 1 && (cleaned.first() - cleaned.last()).getDistance() <= eps) {
        cleaned.removeAt(cleaned.lastIndex)
    }
    if (cleaned.size < 3) return null
    if (!plausibleClosingSegment(cleaned, eps)) return null
    if (abs(polygonSignedArea(cleaned)) < eps * eps) return null
    return ensureCcw(cleaned)
}

private fun plausibleClosingSegment(loop: List<Offset>, eps: Float): Boolean {
    if (loop.size < 4) return true
    val gap = (loop.first() - loop.last()).getDistance()
    if (gap <= eps * 2f) return true

    val segments = ArrayList<Float>(loop.size - 1)
    for (i in 0 until loop.lastIndex) {
        val d = (loop[i + 1] - loop[i]).getDistance()
        if (d > eps) segments += d
    }
    if (segments.isEmpty()) return false

    segments.sort()
    val median = segments[segments.size / 2]
    val maxSegment = segments.last()
    val diagonal = polygonDiagonal(loop).coerceAtLeast(eps)
    val allowed = maxOf(eps * 8f, median * 8f, maxSegment * 3f, diagonal * 0.12f)
    val ok = gap <= allowed
    if (!ok) occlLog("reject nonlocal loop closure gap=$gap allowed=$allowed n=${loop.size}")
    return ok
}

private fun splitRegionByLoop(region: FaceRegion, loop: List<Offset>, loopPath: Path): List<FaceRegion>? {
    val loopInsideRegion = regionContains(region, polygonCentroid(loop)) || loop.any { regionContains(region, it) }
    if (!loopInsideRegion) return null

    val inside = Path()
    if (!inside.op(region.path, loopPath, PathOperation.Intersect) || inside.isEmpty) return null

    val out = ArrayList<FaceRegion>(2)
    out += FaceRegion(loop, inside)

    val outside = Path()
    if (outside.op(region.path, loopPath, PathOperation.Difference) && !outside.isEmpty) {
        out += FaceRegion(region.outer, outside, region.holes + listOf(loop))
    }
    return out
}

private fun regionContains(region: FaceRegion, p: Offset): Boolean =
    pointInPolygon2D(p, region.outer) && region.holes.none { pointInPolygon2D(p, it) }

private fun polygonIsConvex(poly: List<Offset>): Boolean {
    if (poly.size < 3) return false
    val eps = polygonDiagonal(poly) * 1e-5f + 1e-6f
    var sign = 0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val c = poly[(i + 2) % poly.size]
        val ab = b - a
        val bc = c - b
        val cross = ab.x * bc.y - ab.y * bc.x
        if (abs(cross) <= eps) continue
        val s = if (cross > 0f) 1 else -1
        if (sign == 0) sign = s
        else if (sign != s) return false
    }
    return sign != 0
}

private fun clipPathToSilhouetteOrKeep(path: Path, silhouette: OcclusionSilhouette?): Path {
    if (silhouette == null || path.isEmpty || silhouette.clipPath.isEmpty) return path
    val out = Path()
    return if (out.op(path, silhouette.clipPath, PathOperation.Intersect)) out
    else {
        occlLog("  silhouette intersect FAILED → unclipped vector region")
        path
    }
}

/**
 * Skutečná logická výplň tělesa – přesně to, co Draw*Fills vybarví (u kvadrik/SoR kontury
 * s nonzero windingem včetně díry anuloidu, u solidu sjednocení stěn). Null, když těleso
 * výplň nemá (pak se ploška nechá bez ořezu – dnešní chování).
 */
private fun actualFillPathLogical(
    state: MongeState,
    view: FillView,
    quadricFills: List<QuadricFill>,
    id: String,
): Path? {
    quadricFills.firstOrNull { it.id == id }?.let { return quadricFillLogicalPath(it) }
    val solid = state.segmentSolids3D.firstOrNull { it.id == id } ?: return null
    return buildSolidFillPathLogical(state, solid, view)
}

// ───────────────────────── klasifikace plošky ─────────────────────────

// Hlasování „kdo je v regionu vpředu" přes několik vnitřních bodů. Vrací (hlasy A, hlasy B);
// null = žádný vzorek nerozhodl (paprsky tělesa míjí / remíza). Jednoznačný výsledek
// (0 na jedné straně) → region patří celý vítězi; smíšený → volající vezme většinu.
private const val CLASSIFY_GRID = 7

private fun classifyRegionVotes(
    view: FillView,
    probeA: SolidProbe,
    probeB: SolidProbe,
    region: FaceRegion,
    masks: List<RegionMask> = emptyList(),
): Pair<Int, Int>? {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (p in region.outer) {
        if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
    }

    fun inMasks(p: Offset): Boolean {
        if (masks.isEmpty()) return true
        return masks.all { mask -> mask.path.contains(p.x, p.y) }
    }

    fun usableSample(p: Offset): Boolean =
        regionContains(region, p) && inMasks(p)

    val samples = ArrayList<Offset>()
    val centroid = polygonCentroid(region.outer)
    if (usableSample(centroid)) samples += centroid
    for (i in 0 until CLASSIFY_GRID) for (j in 0 until CLASSIFY_GRID) {
        val x = minX + (maxX - minX) * (i + 0.5f) / CLASSIFY_GRID
        val y = minY + (maxY - minY) * (j + 0.5f) / CLASSIFY_GRID
        val p = Offset(x, y)
        if (usableSample(p)) samples += p
    }

    val dir = view.rayDir
    val tie = depthTieTolerance(probeA, probeB)
    var aWins = 0
    var bWins = 0
    var aMiss = 0
    var bMiss = 0
    for (p in samples) {
        val base = view.rayBase(p)
        val dA = probeFrontDepth(probeA, base, dir)
        val dB = probeFrontDepth(probeB, base, dir)
        when {
            dA == null && dB == null -> Unit
            dA == null -> bMiss++
            dB == null -> aMiss++
            abs(dA - dB) < tie -> Unit
            dA > dB -> aWins++
            else -> bWins++
        }
    }
    // Hloubkové hlasy (paprsek zasáhl OBĚ tělesa) mají přednost. Miss hlasy („A mine,
    // B zasaženo" → vidět B) rozhodují jen regiony bez jediného hloubkového vzorku –
    // typicky pohled SKRZ trubku pasu hyperboloidu na kouli za ním; dřív vycházely
    // UNDECIDED → žádný clip → o barvě rozhodovalo pořadí kreslení. U hranic siluet
    // ale polygon mírně přesahuje skutečné těleso, takže by miss hlasy jinak
    // překlápěly tenké okrajové regiony – proto jen jako fallback.
    return when {
        aWins > 0 || bWins > 0 -> aWins to bWins
        aMiss > 0 || bMiss > 0 -> aMiss to bMiss
        else -> null
    }
}

// ───────────────────────── siluety jako polygony/paths ─────────────────────────

private fun collectSilhouettes(
    state: MongeState,
    view: FillView,
    quadricFills: List<QuadricFill>,
): Map<String, OcclusionSilhouette> {
    val out = HashMap<String, OcclusionSilhouette>()
    for (qf in quadricFills) {
        // SoR: profilová obálka kopíruje pas/komolost. I v axo je pro occlusion lepší
        // než konvexní obal všech quadů, který u hourglass/anuloid tvarů vyplní pas a
        // následně vyrábí velké klínové clip artefakty.
        val sorSilhouette = sorSilhouetteOrNull(state, qf.id, view)
        if (sorSilhouette != null) {
            if (sorSilhouette.polygon.size >= 3) out[qf.id] = sorSilhouette
            continue
        }
        // Přímková plocha: přesný průmět (union buněk sítě) místo konvexního obalu –
        // silueta bývá nekonvexní a uzavřená rodina má uprostřed díru (prstenec).
        val ruledSilhouette = ruledSilhouetteOrNull(state, qf.id, view)
        if (ruledSilhouette != null) {
            if (ruledSilhouette.polygon.size >= 3) out[qf.id] = ruledSilhouette
            continue
        }
        val pts = qf.polygons.flatten()
        val hull = segmentSolidConvexHull(pts)
        if (hull.size >= 3) out[qf.id] = silhouetteFromPolygon(hull)
    }
    for (solid in state.segmentSolids3D) {
        if (!solid.show || !solid.fillFaces) continue
        val pts = segmentSolidFallbackVertices(state, solid).map { view.project(Offset3D(it.x, it.y, it.z)) }
        val hull = segmentSolidConvexHull(pts)
        if (hull.size >= 3) out[solid.id] = silhouetteFromPolygon(hull)
    }
    return out
}

private fun silhouetteFromPolygon(poly: List<Offset>): OcclusionSilhouette {
    val ccw = ensureCcw(poly)
    return OcclusionSilhouette(ccw, facesToPath(listOf(ccw)))
}

// Skutečný obrys rotační plochy daného id v pohledu (null, když id není SoR a nejde ani
// sjednocení frustumů → volající použije konvexní obal).
private fun sorSilhouetteOrNull(state: MongeState, id: String, view: FillView): OcclusionSilhouette? {
    val nar = state.solidsOfRevolutionNarys.firstOrNull { it.id == id }
    val pud = state.solidsOfRevolutionPudorys.firstOrNull { it.id == id }
    if (nar == null && pud == null) return null

    // AXO (a pohled podél osy): meridiánová obálka right+left se u profilu s více stěnami
    // v téže výšce (límec, kalich, anuloid) sama překrývá a even-odd testy pak hlásí uvnitř
    // průmětu falešné „venku" – overlap tam chybí a occlusion vůbec neproběhne. Přesný
    // průmět = sjednocení průmětů komolých kuželů mezi sousedními rovnoběžkami (konvexní
    // obal dvou elips je exaktní průmět frustumu). Dutiny tím vyjdou vyplněné; skutečné
    // díry (anuloid) vrátí zpět odečet ověřených fold smyček (sorAxoContourHoles).
    val degenerateCircles = run {
        val o = view.project(Offset3D(0f, 0f, 0f))
        val axisDir3 = when {
            nar != null -> Offset3D(0f, 0f, 1f)
            else -> Offset3D(0f, 1f, 0f)
        }
        val radial = if (nar != null) Offset3D(0f, 1f, 0f) else Offset3D(0f, 0f, 1f)
        val projU = view.project(Offset3D(1f, 0f, 0f)) - o
        val projV = view.project(radial) - o
        val projAxis = view.project(axisDir3) - o
        val uvScale = projU.getDistance() + projV.getDistance()
        abs(projU.x * projV.y - projU.y * projV.x) <= 1e-4f * uvScale * uvScale &&
            projAxis.getDistance() >= 1e-4f
    }
    if (!degenerateCircles) {
        sorUnionSilhouette(state, id, view)?.let { union ->
            var path = union.clipPath
            if (view.kind == FillViewKind.AXO) {
                val holes = sorAxoContourHoles(state, id, view, union.polygon)
                path = subtractClosedContours(path, holes)
                if (holes.isNotEmpty()) occlLog("sor ${id.take(8)} axo holes=${holes.size}")
            }
            return OcclusionSilhouette(union.polygon, path, preciseSorPath = true)
        }
    }

    val outline = sorProjectedSilhouette(state, nar, pud, view.project) ?: return null
    if (outline.size < 3) return null
    val outer = ensureCcw(outline)
    var path = facesToPath(listOf(outer))

    if (view.kind == FillViewKind.AXO) {
        val holes = sorAxoContourHoles(state, id, view, outer)
        path = subtractClosedContours(path, holes)
        if (holes.isNotEmpty()) occlLog("sor ${id.take(8)} axo holes=${holes.size}")
    }
    return OcclusionSilhouette(outer, path, preciseSorPath = true)
}

// Vzorkování siluety/hran přímkové plochy pro occlusion (shodné s výplní, viz RULED_FILL_RULINGS).
private const val RULED_OCCLUSION_RULINGS = 96
private const val RULED_OCCLUSION_ALONG = 2

/**
 * Přesný průmět přímkové plochy: sjednocení promítnutých buněk husté sítě primární
 * reguly – stejná geometrie jako výplň a obrys. Nekonvexní silueta (fold zborcené
 * plochy) i vnitřní díra uzavřené rodiny (prstenec) tak vstupují do occlusion přesně.
 */
private fun ruledSilhouetteOrNull(state: MongeState, id: String, view: FillView): OcclusionSilhouette? {
    val surface = state.ruledSurfaces.firstOrNull { it.id == id } ?: return null
    val grids = monge.input.ruledsurface.ruledSurfaceTrimmedPrimaryGrids(
        state, surface, RULED_OCCLUSION_RULINGS, RULED_OCCLUSION_ALONG,
    )
    if (grids.isEmpty()) return null
    val closedFamily = monge.input.ruledsurface.ruledSurfaceFamilyIsClosed(state, surface)
    val path = ruledSurfaceGridsUnionPath(grids, closedFamily, view.project) ?: return null
    val outer = largestPathContour(path) ?: return null
    if (outer.size < 3) return null
    return OcclusionSilhouette(ensureCcw(outer), path, preciseSorPath = true)
}

/**
 * Řezací hrany přímkové plochy: obě krajní řídicí křivky (řádky sítě), u otevřené
 * rodiny i krajní tvořice, a pravá silueta plochy v aktuálním pohledu (fold).
 * Přes tyto křivky skáče přední hloubka mezi přední a zadní stěnou – bez řezu by
 * region vyšel hloubkově smíšený a většina by ho přiřkla špatně (stejný mechanismus
 * jako rim/fold u SoR). Řetězce splývající se siluetou zahodí sliver guard.
 */
private fun ruledEdgeChains(state: MongeState, view: FillView, solid: OcclusionSolid): List<Poly3D> {
    if (solid !is OcclusionSolid.Ruled) return emptyList()
    val surface = solid.surface
    val grids = monge.input.ruledsurface.ruledSurfaceTrimmedPrimaryGrids(
        state, surface, RULED_OCCLUSION_RULINGS, RULED_OCCLUSION_ALONG,
    )
    if (grids.isEmpty()) return emptyList()
    val closedFamily = monge.input.ruledsurface.ruledSurfaceFamilyIsClosed(state, surface)
    val out = ArrayList<Poly3D>()
    for (grid in grids) {
        if (grid.size < 2 || grid[0].size < 2) continue
        out += Poly3D(grid.map { it.first() }, closed = closedFamily)
        out += Poly3D(grid.map { it.last() }, closed = closedFamily)
        if (!closedFamily) {
            out += Poly3D(listOf(grid.first().first(), grid.first().last()), closed = false)
            out += Poly3D(listOf(grid.last().first(), grid.last().last()), closed = false)
        }
    }
    for ((points, closed) in monge.input.ruledsurface.ruledSurfaceViewOutlines3D(state, surface, view.project)) {
        if (points.size >= 2) out += Poly3D(downsamplePolyline(points, MAX_CURVE_OCCLUSION_POINTS), closed)
    }
    return out
}

/**
 * Silueta jako sjednocení průmětů frustumů (viz [sorSilhouetteOrNull]). Vrací vnější konturu
 * jako polygon pro polygonové algoritmy (overlap, point-in-polygon) a normalizovaný Path
 * pro masky/winner cover. Null při selhání pathops → volající spadne do meridiánové obálky.
 */
private fun sorUnionSilhouette(state: MongeState, id: String, view: FillView): OcclusionSilhouette? {
    val rings = sorSilhouetteRings(state, id, view.project) ?: return null
    if (rings.size < 2) return null
    var acc: Path? = null
    for (i in 0 until rings.size - 1) {
        val hull = segmentSolidConvexHull(rings[i] + rings[i + 1])
        if (hull.size < 3) continue
        val hullPath = facesToPath(listOf(ensureCcw(hull)))
        acc = if (acc == null) {
            hullPath
        } else {
            val next = Path()
            if (!next.op(acc, hullPath, PathOperation.Union)) {
                occlLog("sor ${id.take(8)} frustum union FAILED at $i")
                return null
            }
            next
        }
    }
    val path = acc ?: return null
    val outer = largestPathContour(path) ?: return null
    if (outer.size < 3) return null
    return OcclusionSilhouette(ensureCcw(outer), path, preciseSorPath = true)
}

// Všechny samostatné kontury normalizovaného Path složeného z úseček.
// PathOperation.Union může vrátit více ostrovů; jejich zahození přes jedinou
// největší konturu rozbíjelo obrys ploch se dvěma větvemi hyperboly.
internal fun pathContours(path: Path): List<List<Offset>> {
    val iterator = org.jetbrains.skia.PathSegmentIterator.make(path.asSkiaPath(), false)
    val contours = ArrayList<List<Offset>>()
    var current = ArrayList<Offset>()
    fun flush() {
        if (current.size >= 3) contours += current
        current = ArrayList()
    }
    while (iterator.hasNext()) {
        val seg = iterator.next() ?: break
        when (seg.verb) {
            org.jetbrains.skia.PathVerb.MOVE -> {
                flush()
                current.add(Offset(seg.p0!!.x, seg.p0!!.y))
            }
            org.jetbrains.skia.PathVerb.LINE -> current.add(Offset(seg.p1!!.x, seg.p1!!.y))
            org.jetbrains.skia.PathVerb.QUAD -> current.add(Offset(seg.p2!!.x, seg.p2!!.y))
            org.jetbrains.skia.PathVerb.CONIC -> current.add(Offset(seg.p2!!.x, seg.p2!!.y))
            org.jetbrains.skia.PathVerb.CUBIC -> current.add(Offset(seg.p3!!.x, seg.p3!!.y))
            org.jetbrains.skia.PathVerb.CLOSE -> flush()
            org.jetbrains.skia.PathVerb.DONE -> break
        }
    }
    flush()
    return contours
}

// Vnější kontura (největší plochou) pro starší volající, kteří reprezentují
// siluetu jediným polygonem.
internal fun largestPathContour(path: Path): List<Offset>? =
    pathContours(path).maxByOrNull { abs(polygonSignedArea(it)) }

private fun sorAxoContourHoles(
    state: MongeState,
    id: String,
    view: FillView,
    outer: List<Offset>,
): List<List<Offset>> {
    if (outer.size < 3) return emptyList()
    val outerArea = abs(polygonSignedArea(outer))
    if (outerArea <= 1e-6f) return emptyList()
    val tol = polygonDiagonal(outer) * 0.002f + 1e-4f
    val minHoleArea = outerArea * 0.002f
    val maxHoleArea = outerArea * 0.85f

    // Skutečná díra (anuloid) = paprsek uvnitř smyčky těleso MÍJÍ. Fold větev pasu
    // (hourglass) má konce v průmětu blízko sebe, takže se falešně uzavře a bez tohoto
    // testu by se odečetla jako díra → v té oblasti by se druhé těleso nikdy neořezalo
    // (koule „před" pasem hyperboloidu). Sonda je líná – staví se jen když kandidát je.
    var probe: SolidProbe? = null
    var probeBuilt = false
    fun rayHitsSolid(p: Offset): Boolean {
        if (!probeBuilt) {
            probeBuilt = true
            probe = resolveOcclusionSolid(state, id)?.let { buildSolidProbe(state, it, view.rayDir) }
        }
        val pr = probe ?: return false
        return probeFrontDepth(pr, view.rayBase(p), view.rayDir) != null
    }

    return state.curvesAxo.mapNotNull { curve ->
        if (curve.parentId != id || !curve.id.startsWith(SOR_AXO_CONTOUR_ID_PREFIX)) return@mapNotNull null
        val pts3D = curve.polyline3D ?: return@mapNotNull null
        val projected = pts3D.map(view.project)
        val closes = curve.closed || (projected.first() - projected.last()).getDistance() <= tol
        if (!closes) return@mapNotNull null
        val loop = closedArcLoop(projected, tol) ?: return@mapNotNull null
        val area = abs(polygonSignedArea(loop))
        if (area !in minHoleArea..maxHoleArea) return@mapNotNull null
        val centroid = polygonCentroid(loop)
        if (!pointInPolygon2D(centroid, outer)) return@mapNotNull null
        val interiorSamples = listOf(centroid) + (0 until 4).map { k ->
            val q = loop[(k * loop.size) / 4]
            Offset((centroid.x + q.x) * 0.5f, (centroid.y + q.y) * 0.5f)
        }
        if (interiorSamples.any { rayHitsSolid(it) }) {
            occlLog("  hole candidate rejected (ray hits solid) area=$area")
            return@mapNotNull null
        }
        loop
    }
}

private fun subtractClosedContours(base: Path, holes: List<List<Offset>>): Path {
    var current = base
    for (hole in holes.sortedByDescending { abs(polygonSignedArea(it)) }) {
        val next = Path()
        if (next.op(current, facesToPath(listOf(hole)), PathOperation.Difference)) current = next
        else occlLog("  hole difference FAILED")
    }
    return current
}

// ───────────────────────── průniková křivka (3D) ─────────────────────────

private fun intersectionCurves3D(state: MongeState, group: IntersectionGroup): List<Poly3D> {
    val out = ArrayList<Poly3D>()
    for (part in group.parts) when (part.kind) {
        IntersectionPartKind.SEGMENT3D ->
            state.segments3D.firstOrNull { it.id == part.id }?.let {
                out += Poly3D(
                    listOf(Offset3D(it.start.x, it.start.y, it.start.z), Offset3D(it.end.x, it.end.y, it.end.z)),
                    closed = false,
                )
            }

        IntersectionPartKind.LINE3D ->
            state.lines3D.firstOrNull { it.id == part.id }?.let {
                val s = Offset3D(it.start.x, it.start.y, it.start.z)
                val d = it.direction.normalize()
                out += Poly3D(listOf(s - d * 1e4f, s + d * 1e4f), closed = false)
            }

        IntersectionPartKind.CONIC3D ->
            state.conics3D.firstOrNull { it.id == part.id }?.let { conic ->
                conicArc3D(state, conic)?.let { out += it }
            }

        IntersectionPartKind.CURVE3D ->
            state.curves3D.firstOrNull { it.id == part.id }?.let { curve ->
                curve.polyline3D?.let {
                    // Bodově vzorkované průnikové křivky (rotační plochy) mají stovky bodů; pro
                    // occlusion (viditelnost + řezání siluet) stačí řídčeji – jinak roste cena
                    // s délkou křivky. Silueta i klasifikace jsou vůči tomu tolerantní.
                    if (it.size >= 2) out += Poly3D(downsamplePolyline(it, MAX_CURVE_OCCLUSION_POINTS), closed = curve.closed)
                }
            }

        IntersectionPartKind.POINT3D -> Unit
    }
    return out
}

private const val MAX_CURVE_OCCLUSION_POINTS = 160

/**
 * Řezací hrany rotační plochy: kružnice otevřených konců meridiánu (všechny pohledy)
 * a v AXO navíc pravé obrysové křivky (fold větve uvnitř průmětu, už ořezané na
 * viditelné části). Řetězce splývající se siluetou nebo hranicí plošky neškodí –
 * řez tam vyjde jako sliver a zahodí se.
 */
private fun sorEdgeChains(state: MongeState, view: FillView, solid: OcclusionSolid): List<Poly3D> {
    if (solid !is OcclusionSolid.Sor) return emptyList()
    val out = ArrayList<Poly3D>()
    for (rim in sorOpenRimCircles3D(state, solid.narys, solid.pudorys)) {
        out += Poly3D(rim, closed = true)
    }
    if (view.kind == FillViewKind.AXO) {
        for (curve in state.curvesAxo) {
            if (curve.parentId != solid.id || !curve.id.startsWith(SOR_AXO_CONTOUR_ID_PREFIX)) continue
            val pts = curve.polyline3D ?: continue
            if (pts.size >= 2) out += Poly3D(downsamplePolyline(pts, MAX_CURVE_OCCLUSION_POINTS), closed = curve.closed)
        }
    }
    return out
}

private fun downsamplePolyline(pts: List<Offset3D>, maxPoints: Int): List<Offset3D> {
    if (pts.size <= maxPoints) return pts
    val step = (pts.size - 1).toFloat() / (maxPoints - 1)
    return List(maxPoints) { i -> pts[(i * step).toInt().coerceAtMost(pts.lastIndex)] }
}

private fun conicArc3D(state: MongeState, conic: ConicSection3D): Poly3D? {
    val axes = runCatching { computeEllipseAxes3D(conic) }.getOrNull() ?: return null
    val au = axes.uRotated.normalize()
    val av = axes.vRotated.normalize()
    val a = axes.a; val b = axes.b
    if (!a.isFinite() || !b.isFinite() || a < 1e-6f || b < 1e-6f) return null
    val arc = state.ellipseArcParams3D[conic.id]
    val t1 = arc?.first ?: -PI_F
    val t2 = arc?.second ?: PI_F
    val closed = arc == null
    val pts = (0..CURVE_SAMPLES).map { i ->
        val t = t1 + (t2 - t1) * i / CURVE_SAMPLES
        conic.p0 + au * (a * cos(t)) + av * (b * sin(t))
    }
    return Poly3D(pts, closed)
}

// Sešije otevřené polylinie sdílející konce do souvislých řetězců (uzavřené nechá být).
private fun stitch3D(polys: List<Poly3D>): List<Poly3D> {
    val closed = polys.filter { it.closed }
    val open = polys.filter { !it.closed }.map { it.pts.toMutableList() }.toMutableList()
    if (open.isEmpty()) return closed

    val scale = open.flatten().let { pts ->
        if (pts.isEmpty()) 1f else {
            val xs = pts.map { it.x }; val ys = pts.map { it.y }; val zs = pts.map { it.z }
            hypot(hypot((xs.max() - xs.min()), (ys.max() - ys.min())), (zs.max() - zs.min()))
        }
    }
    val tol = scale * 8e-3f + 1e-4f
    fun near(a: Offset3D, b: Offset3D) = (a - b).length() <= tol

    val result = ArrayList<Poly3D>()
    while (open.isNotEmpty()) {
        val chain = open.removeAt(open.size - 1)
        var extended = true
        while (extended) {
            extended = false
            val it = open.iterator()
            while (it.hasNext()) {
                val o = it.next()
                when {
                    near(chain.last(), o.first()) -> { chain.addAll(o.drop(1)); it.remove(); extended = true }
                    near(chain.last(), o.last()) -> { chain.addAll(o.reversed().drop(1)); it.remove(); extended = true }
                    near(chain.first(), o.last()) -> { chain.addAll(0, o.dropLast(1)); it.remove(); extended = true }
                    near(chain.first(), o.first()) -> { chain.addAll(0, o.reversed().dropLast(1)); it.remove(); extended = true }
                }
                if (extended) break
            }
        }
        result += Poly3D(chain, closed = false)
    }
    return result + closed
}

// Rozdělí (projektovaný) řetězec na VIDITELNÉ 2D úseky – kde bod leží na přední ploše obou těles.
private fun visibleRuns(chain: Poly3D, view: FillView, probeA: SolidProbe, probeB: SolidProbe): List<ProjectedArc> {
    if (chain.pts.size < 2) return emptyList()
    val dir = view.rayDir
    val tolA = (probeA.tHi - probeA.tLo) * VIS_TOL_FRAC + 1e-3f
    val tolB = (probeB.tHi - probeB.tLo) * VIS_TOL_FRAC + 1e-3f

    fun visible(q: Offset3D, p2: Offset): Boolean {
        val base = view.rayBase(p2)
        val qt = q dot dir
        val fa = probeFrontDepth(probeA, base, dir) ?: return false
        val fb = probeFrontDepth(probeB, base, dir) ?: return false
        return (fa - qt) <= tolA && (fb - qt) <= tolB
    }

    val projected = chain.pts.map(view.project)
    val flags = BooleanArray(chain.pts.size) { i -> visible(chain.pts[i], projected[i]) }

    // Hystereze: krátkou mezeru (pár neviditelných bodů, typicky šum na přechodu) přemostíme.
    // Hranice regionu zůstává původní průniková křivka; pouze se netrhá na kousky.
    val maxGap = 2
    bridgeSmallVisibilityGaps(flags, chain.closed, maxGap)

    // Přesný konec běhu bisekcí mezi posledním viditelným a prvním neviditelným bodem –
    // jinak běh končí na posledním VZORKU a most k hranici usekne roh plošky (nevyplněný
    // klínek u styku řezu, siluety a druhého tělesa; velikost závisela na hustotě vzorků).
    fun refineEnd(visIdx: Int, invisIdx: Int): Offset {
        var a = chain.pts[visIdx]
        var b = chain.pts[invisIdx]
        repeat(VIS_END_REFINE_STEPS) {
            val m = (a + b) * 0.5f
            if (visible(m, view.project(m))) a = m else b = m
        }
        return view.project(a)
    }

    if (chain.closed) {
        if (flags.all { it }) return listOf(ProjectedArc(projected, closed = true))
        if (flags.none { it }) return emptyList()
        val n = flags.size
        val start = flags.indexOfFirst { !it }
        val runs = ArrayList<ProjectedArc>()
        var step = 1
        while (step <= n) {
            var idx = (start + step) % n
            if (!flags[idx]) {
                step++
                continue
            }
            val firstIdx = idx
            var lastIdx = idx
            val pts = ArrayList<Offset>()
            pts += refineEnd(firstIdx, (firstIdx - 1 + n) % n)
            while (step <= n) {
                idx = (start + step) % n
                if (!flags[idx]) break
                pts += projected[idx]
                lastIdx = idx
                step++
            }
            pts += refineEnd(lastIdx, (lastIdx + 1) % n)
            if (pts.size >= 3) runs += ProjectedArc(pts, closed = false)
        }
        return runs
    }

    val runs = ArrayList<ProjectedArc>()
    var i = 0
    while (i < flags.size) {
        if (!flags[i]) {
            i++
            continue
        }
        val firstIdx = i
        val pts = ArrayList<Offset>()
        if (firstIdx > 0) pts += refineEnd(firstIdx, firstIdx - 1)
        while (i < flags.size && flags[i]) {
            pts += projected[i]
            i++
        }
        if (i < flags.size) pts += refineEnd(i - 1, i)
        if (pts.size >= 2) runs += ProjectedArc(pts, closed = false)
    }
    return runs
}

// Bisekce hranice viditelnosti: přechod je skok (hloubka přední plochy se přes hranu/obrys
// mění nespojitě), takže konverguje k přesnému bodu styku nezávisle na toleranci.
private const val VIS_END_REFINE_STEPS = 20

private fun bridgeSmallVisibilityGaps(flags: BooleanArray, closed: Boolean, maxGap: Int) {
    val src = flags.copyOf()
    val n = src.size
    if (n == 0) return
    if (closed) {
        if (src.all { it } || src.none { it }) return
        for (i in 0 until n) {
            if (src[i] || !src[(i - 1 + n) % n]) continue
            var len = 0
            while (len < n && !src[(i + len) % n]) len++
            if (len <= maxGap && src[(i + len) % n]) {
                for (k in 0 until len) flags[(i + k) % n] = true
            }
        }
    } else {
        var i = 1
        while (i < n - 1) {
            if (src[i] || !src[i - 1]) {
                i++
                continue
            }
            var len = 0
            while (i + len < n && !src[i + len]) len++
            if (len <= maxGap && i + len < n && src[i + len]) {
                for (k in 0 until len) flags[i + k] = true
            }
            i += len.coerceAtLeast(1)
        }
    }
}

// ───────────────────────── pomocné ─────────────────────────

private fun polygonDiagonal(poly: List<Offset>): Float {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (p in poly) {
        if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
    }
    return hypot(maxX - minX, maxY - minY)
}

private fun facesToPath(faces: List<List<Offset>>): Path {
    val path = Path()
    for (face in faces) {
        if (face.size < 3) continue
        path.moveTo(face[0].x, face[0].y)
        for (i in 1 until face.size) path.lineTo(face[i].x, face[i].y)
        path.close()
    }
    return path
}

// ───────────────────────── cache signature ─────────────────────────

private fun occlusionSignature(state: MongeState, view: FillView): String {
    val sb = StringBuilder()
    fun off(o: Offset) { sb.append(o.x).append(',').append(o.y).append(';') }
    off(view.project(Offset3D(1f, 0f, 0f)))
    off(view.project(Offset3D(0f, 1f, 0f)))
    off(view.project(Offset3D(0f, 0f, 1f)))
    off(view.project(Offset3D(0f, 0f, 0f)))
    sb.append(view.rayDir.x).append(',').append(view.rayDir.y).append(',').append(view.rayDir.z).append('|')
    for (g in state.intersectionGroups) {
        sb.append(g.operandAId).append('~').append(g.operandBId).append('#')
        resolveOcclusionSolid(state, g.operandAId)?.let { sb.append(geometryTag(state, it)).append(fillTag(it)) }
        resolveOcclusionSolid(state, g.operandBId)?.let { sb.append(geometryTag(state, it)).append(fillTag(it)) }
        sb.append('|')
    }
    return sb.toString()
}

private fun fillTag(solid: OcclusionSolid): String =
    when (solid) {
        is OcclusionSolid.Seg -> if (solid.solid.show && solid.solid.fillFaces) "F1" else "F0"
        is OcclusionSolid.Sph -> if (solid.sphere.fillFaces) "F1" else "F0"
        is OcclusionSolid.Cyl -> if (solid.cylinder.fillFaces) "F1" else "F0"
        is OcclusionSolid.Con -> if (solid.cone.fillFaces) "F1" else "F0"
        is OcclusionSolid.Sor -> {
            val shown = solid.narys?.let { it.show && it.fillFaces }
                ?: solid.pudorys?.let { it.show && it.fillFaces }
                ?: false
            if (shown) "F1" else "F0"
        }
        is OcclusionSolid.Ruled -> if (solid.surface.show && solid.surface.fillFaces) "F1" else "F0"
    }

private fun geometryTag(state: MongeState, solid: OcclusionSolid): String {
    val sb = StringBuilder(solid.id)
    when (solid) {
        is OcclusionSolid.Seg ->
            for (v in segmentSolidFallbackVertices(state, solid.solid)) {
                sb.append(v.x).append(',').append(v.y).append(',').append(v.z).append(';')
            }
        is OcclusionSolid.Sph -> {
            sphereCenter(state, solid.sphere)?.let { sb.append(it.x).append(',').append(it.y).append(',').append(it.z) }
            sb.append('r').append(solid.sphere.radius)
        }
        is OcclusionSolid.Cyl -> {
            val c = state.conics3D.find { it.id == solid.cylinder.directrixId }
            if (c != null) appendConicTag(sb, c)
            val d = solid.cylinder.direction
            sb.append('d').append(d.x).append(',').append(d.y).append(',').append(d.z)
            sb.append("lo").append(solid.cylinder.lowerConicId)
            sb.append("up").append(solid.cylinder.upperConicId)
            solid.cylinder.upperConicId?.let { id ->
                state.conics3D.find { it.id == id }?.let { appendConicTag(sb, it) }
            }
            sb.append("tp").append(solid.cylinder.topPlaneId)
            solid.cylinder.topPlaneId?.let { id ->
                state.planes3D.find { it.id == id }?.equation?.let { eq ->
                    sb.append(eq.a).append(',').append(eq.b).append(',').append(eq.c).append(',').append(eq.d)
                }
            }
        }
        is OcclusionSolid.Con -> {
            coneApex(state, solid.cone)?.let { sb.append(it.x).append(',').append(it.y).append(',').append(it.z) }
            val c = state.conics3D.find { it.id == solid.cone.directrixId }
            if (c != null) sb.append('/').append(c.p0.x).append(',').append(c.p0.y).append(',').append(c.p0.z)
        }
        is OcclusionSolid.Sor -> {
            val axisId = solid.narys?.axisLine3DId ?: solid.pudorys?.axisLine3DId
            axisId?.let { aid -> state.lines3D.firstOrNull { it.id == aid } }?.let { axis ->
                sb.append(axis.start.x).append(',').append(axis.start.y).append(',').append(axis.start.z)
            }
            // meridián se po vytvoření nemění bod po bodu → stačí hash vzorkované polyline
            sb.append('m').append(
                solid.narys?.sampledMeridianPolylineXZ?.hashCode()
                    ?: solid.pudorys?.sampledMeridianPolylineXY?.hashCode()
                    ?: 0
            )
            val axoContours = state.curvesAxo
                .filter { it.parentId == solid.id && it.id.startsWith(SOR_AXO_CONTOUR_ID_PREFIX) }
                .sortedBy { it.id }
            if (axoContours.isNotEmpty()) {
                sb.append("axoC")
                for (curve in axoContours) {
                    sb.append(curve.id).append(':')
                        .append(curve.closed).append(':')
                        .append(curve.polyline3D?.hashCode() ?: 0).append(';')
                }
            }
        }
        is OcclusionSolid.Ruled -> {
            // Geometrii určují snapshoty řídicích křivek (po vytvoření se nemění bod
            // po bodu) + verze obrysu, která se zvedá při přepočtu starších souborů.
            sb.append('r').append(solid.surface.directrixSnapshots.hashCode())
            sb.append('v').append(solid.surface.outlineGeometryVersion)
            // Ořez/přesah tvořic mění rozsah výplně i siluety occlusion.
            sb.append('t').append(
                monge.input.ruledsurface.ruledSurfaceGeneratorTrimSignature(solid.surface)
            )
        }
    }
    return sb.toString()
}

private fun appendConicTag(sb: StringBuilder, c: ConicSection3D) {
    sb.append(c.p0.x).append(',').append(c.p0.y).append(',').append(c.p0.z)
    sb.append("u").append(c.u.x).append(',').append(c.u.y).append(',').append(c.u.z)
    sb.append("v").append(c.v.x).append(',').append(c.v.y).append(',').append(c.v.z)
    val m = c.matrix
    sb.append("m")
        .append(m.m00).append(',').append(m.m01).append(',').append(m.m02).append(',')
        .append(m.m10).append(',').append(m.m11).append(',').append(m.m12).append(',')
        .append(m.m20).append(',').append(m.m21).append(',').append(m.m22)
}
