package monge.input.quadrics

import androidx.compose.ui.geometry.Offset
import model.Offset3D
import model.Point3D
import model.ProjectionMode
import model.classes.*
import monge.input.quadrics.conicalsurface.ellipseFromConic3D
import monge.input.quadrics.conicalsurface.hideConeNonAxoProjectionsAfterAxoConstruction
import monge.input.quadrics.conicalsurface.rebuildConicalSilhouette2D
import monge.input.quadrics.cylindricalsurface.applyCylinderOuterArcsBokorysAxo
import monge.input.quadrics.cylindricalsurface.applyPendingCylinderOuterArcs2D
import monge.input.quadrics.cylindricalsurface.buildCylindricalSurfaceFrom
import monge.input.quadrics.cylindricalsurface.hideCylinderNonAxoProjectionsAfterAxoConstruction
import monge.input.quadrics.cylindricalsurface.rebuildCylindricalSilhouetteAxo
import monge.input.quadrics.cylindricalsurface.resolveCylinderInteriorVisibility2D
import state.MongeState
import utils.allocIndex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Relativní tolerance geometrických testů (vůči velikosti elipsy / délce tvořicí).
// Vstupy vznikají snapováním, takže sedí téměř přesně; 1 % pokryje float šum
// a zároveň nespustí detekci na "skoro" konstrukcích.
private const val REL_TOL = 0.01f
private const val SHAPE_SAMPLES = 8

// Tolerance tečnosti obrysové úsečky = sinus povolené odchylky od tečny (~3°).
// Netečné tvořice se za obrys nepočítají – konstrukce musí být opravdu tečná.
private const val TANGENT_TOL = 0.05f

/**
 * Auto-detekce válcové/kuželové plochy z ručně sestrojených objektů, analogie
 * [monge.input.segments.detectSegmentSolidAfterAdd] pro hranol/jehlan.
 *
 * Obrysové tvořice jsou z podstaty věci 2D objekty (v každém pohledu jde o jiné
 * tvořice), proto se detekce vyhodnocuje nad 2D úsečkami bez 3D parenta:
 *
 * - Válcová plocha: dvě volné 3D elipsy, z nichž jedna je posunutou kopií druhé,
 *   a v každém pohledu, kde obrys existuje (Monge: půdorys i nárys; AXO: axo),
 *   aspoň dvě 2D úsečky spojující průměty podstav ve směru průmětu posunutí.
 * - Kuželová plocha: volná 3D elipsa a samostatný 3D bod vrcholu mimo její rovinu;
 *   v každém pohledu, kde tečny z vrcholu existují, aspoň dvě 2D úsečky
 *   z průmětu elipsy do průmětu vrcholu.
 *
 * Při shodě se ruční 2D úsečky nahradí obrysovými segmenty plochy a elipsy
 * podstav se zapojí jako řídicí/horní kuželosečka – výsledek je stejný objekt,
 * jaký vytváří nástroj CYLINDER/CONE.
 *
 * Volat po přidání single 2D úsečky, před commitSnapshot (plocha pak spadne
 * do stejného undo kroku).
 */
fun detectQuadricSurfaceAfter2DSegmentAdd(state: MongeState) {
    if (state.projectionMode != ProjectionMode.MONGE && state.projectionMode != ProjectionMode.AXO) return
    try {
        if (detectCylindricalSurface2D(state)) return
        detectConicalSurface2D(state)
    } catch (e: Exception) {
        println("⚠️ Auto-detekce plochy selhala: ${e.message}")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Geometrické pomůcky
// ─────────────────────────────────────────────────────────────────────────────

private data class FreeEllipse(
    val conic: ConicSection3D,
    val el: EllipseParam,
    val normal: Offset3D,
    val size: Float
)

private fun dot3(a: Offset3D, b: Offset3D) = a.x * b.x + a.y * b.y + a.z * b.z
private fun cross3(a: Offset3D, b: Offset3D) = Offset3D(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x
)

private fun len3(v: Offset3D) = sqrt(dot3(v, v))
private fun len2(v: Offset) = sqrt(v.x * v.x + v.y * v.y)
private fun near2(a: Offset, b: Offset, tol: Float) = len2(a - b) <= tol
private fun Point3D.pos() = Offset3D(x, y, z)

/** Elipsy, které zatím nepatří žádné ploše – jen ty smí detekce použít jako podstavy. */
private fun freeEllipses(state: MongeState): List<FreeEllipse> =
    state.conics3D.mapNotNull { conic ->
        if (conic.directrixOfSurfaceIds.isNotEmpty()) return@mapNotNull null
        if (state.cylindricalSurfaces.any {
                conic.id == it.directrixId || conic.id == it.lowerConicId || conic.id == it.upperConicId
            }) return@mapNotNull null
        if (state.conicalSurfaces.any { conic.id == it.directrixId }) return@mapNotNull null
        val el = ellipseFromConic3D(conic) ?: return@mapNotNull null
        val n = cross3(el.uRot, el.vRot)
        val nLen = len3(n)
        if (nLen < 1e-8f) return@mapNotNull null
        FreeEllipse(conic, el, n * (1f / nLen), max(el.a, el.b))
    }

private fun onEllipse3D(p: Offset3D, e: FreeEllipse): Boolean {
    val w = p - e.el.center3D
    if (abs(dot3(w, e.normal)) > REL_TOL * e.size) return false
    val du = dot3(w, e.el.uRot) / e.el.a
    val dv = dot3(w, e.el.vRot) / e.el.b
    return abs(du * du + dv * dv - 1f) <= 3f * REL_TOL
}

/**
 * Průmět 3D elipsy do pohledu: množina bodů c + u·cosθ + v·sinθ.
 * Zvládá i degenerovaný průmět (elipsa kolmá k průmětně → úsečka).
 */
private class ProjEllipse(val c: Offset, val u: Offset, val v: Offset) {
    val size: Float = max(len2(u), len2(v))
    private val det = u.x * v.y - u.y * v.x
    val degenerate = abs(det) <= REL_TOL * size * max(size, 1e-6f)

    // degenerovaný průmět = úsečka c ± g·h
    private val g: Offset = run {
        val m = if (len2(u) >= len2(v)) u else v
        m * (1f / max(len2(m), 1e-9f))
    }
    private val h: Float = sqrt(sq(u.x * g.x + u.y * g.y) + sq(v.x * g.x + v.y * g.y))

    /** (cosθ, sinθ) bodu v parametrizaci elipsy; null u degenerovaného průmětu. */
    private fun param(p: Offset): Offset? {
        if (degenerate) return null
        val w = p - c
        return Offset((w.x * v.y - w.y * v.x) / det, (u.x * w.y - u.y * w.x) / det)
    }

    /** Bod leží na průmětu křivky (u degenerace kdekoli na úsečce průmětu). */
    fun contains(p: Offset): Boolean {
        val ab = param(p) ?: run {
            val w = p - c
            val perp = abs(-w.x * g.y + w.y * g.x)
            if (perp > REL_TOL * size) return false
            return abs(w.x * g.x + w.y * g.y) <= h + REL_TOL * size
        }
        return abs(ab.x * ab.x + ab.y * ab.y - 1f) <= 3f * REL_TOL
    }

    /** Bod leží ostře uvnitř průmětu elipsy (odtud nelze vést tečny). */
    fun containsInterior(p: Offset): Boolean {
        val ab = param(p) ?: return false
        return ab.x * ab.x + ab.y * ab.y < 1f - 3f * REL_TOL
    }

    /**
     * Úsečka vycházející z bodu p (na křivce) ve směru dir je tečnou průmětu
     * elipsy v p. U degenerovaného průmětu (úsečka) je "tečný" jen dotyk
     * na jejích krajích.
     */
    fun isTangentAt(p: Offset, dir: Offset): Boolean {
        val ab = param(p) ?: run {
            val w = p - c
            return abs(w.x * g.x + w.y * g.y) >= h - REL_TOL * size
        }
        // tečný směr parametrizace: −u·sinθ + v·cosθ
        val tangent = Offset(-u.x * ab.y + v.x * ab.x, -u.y * ab.y + v.y * ab.x)
        val tLen = len2(tangent)
        val dLen = len2(dir)
        if (tLen < 1e-9f || dLen < 1e-9f) return false
        val cross = abs(tangent.x * dir.y - tangent.y * dir.x)
        return cross <= TANGENT_TOL * tLen * dLen
    }
}

private fun sq(x: Float) = x * x

private fun projectEllipse(e: FreeEllipse, project: (Offset3D) -> Offset): ProjEllipse {
    val c = project(e.el.center3D)
    val u = project(e.el.center3D + e.el.uRot * e.el.a) - c
    val v = project(e.el.center3D + e.el.vRot * e.el.b) - c
    return ProjEllipse(c, u, v)
}

/** Kandidátní 2D úsečka: bez 3D parenta a nepatřící žádné ploše. */
private data class Seg2D(val id: String, val p: Offset, val q: Offset)

/** Pohled = projekce 3D→2D + kandidátní 2D úsečky v něm. */
private class ViewCtx(
    val name: String,
    val project: (Offset3D) -> Offset,
    val segments: List<Seg2D>,
    val removeIds: MutableSet<String> = mutableSetOf()
)

private fun detectionViews(state: MongeState): List<ViewCtx> {
    if (state.projectionMode == ProjectionMode.AXO) {
        val basis = state.basis ?: return emptyList()
        return listOf(ViewCtx(
            name = "axo",
            project = { projectPoint3DToAxoLocal(it, basis) },
            segments = state.segmentsAxo
                .filter { it.parent == null && it.parentId == null && !it.isConicalSilhouette }
                .map { Seg2D(it.id, Offset(it.start.x, it.start.y), Offset(it.end.x, it.end.y)) }
        ))
    }
    return listOf(
            ViewCtx(
                name = "půdorys",
                project = { Offset(it.x, it.y) },
                segments = state.segmentsPudorys
                    .filter { it.parent == null && it.parentId == null && !it.isConicalSilhouette }
                    .map { Seg2D(it.id, Offset(it.start.x, it.start.y), Offset(it.end.x, it.end.y)) }
            ),
            ViewCtx(
                name = "nárys",
                project = { Offset(it.x, it.z) },  // Point3DNarys ukládá nativní (x, z)
                segments = state.segmentsNarys
                    .filter { it.parent == null && it.parentId == null && !it.isConicalSilhouette }
                    .map { Seg2D(it.id, Offset(it.start.x, it.start.z), Offset(it.end.x, it.end.z)) }
            )
    )
}

/** Počet vzájemně různých dotykových bodů na podstavě (dvě tečny = dva různé body). */
private fun distinctCount(points: List<Offset>, tol: Float): Int {
    val distinct = mutableListOf<Offset>()
    points.forEach { p -> if (distinct.none { near2(it, p, tol) }) distinct += p }
    return distinct.size
}

// ─────────────────────────────────────────────────────────────────────────────
// Válcová plocha
// ─────────────────────────────────────────────────────────────────────────────

private fun detectCylindricalSurface2D(state: MongeState): Boolean {
    val ellipses = freeEllipses(state)
    if (ellipses.size < 2) return false
    val views = detectionViews(state)
    if (views.isEmpty() || views.all { it.segments.isEmpty() }) return false

    for (base in ellipses) {
        for (top in ellipses) {
            if (base.conic.id == top.conic.id) continue
            // podstava = dříve narýsovaná elipsa (druhé pořadí projde obrácená iterace)
            if (base.conic.creationIndex > top.conic.creationIndex) continue
            if (tryFinalizeCylinder(state, base, top, views)) return true
        }
    }
    return false
}

private fun tryFinalizeCylinder(
    state: MongeState,
    base: FreeEllipse,
    top: FreeEllipse,
    views: List<ViewCtx>
): Boolean {
    val t = top.el.center3D - base.el.center3D
    val tLen = len3(t)
    if (tLen < REL_TOL * base.size) return false
    // koplanární elipsy (dvě podstavy vedle sebe v jedné rovině) nejsou válec
    if (abs(dot3(top.normal, t)) < 1e-3f * tLen) return false

    // horní podstava musí být posunutou kopií dolní
    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    for (i in 0 until SHAPE_SAMPLES) {
        val ang = twoPi * i / SHAPE_SAMPLES
        val sample = base.el.center3D +
            base.el.uRot * (base.el.a * cos(ang)) +
            base.el.vRot * (base.el.b * sin(ang)) + t
        if (!onEllipse3D(sample, top)) return false
    }

    // v každém pohledu, kde obrys existuje, musí být narýsované obě tvořice
    val matches = mutableListOf<Pair<ViewCtx, List<String>>>()
    for (view in views) {
        val baseProj = projectEllipse(base, view.project)
        val tView = view.project(t) - view.project(Offset3D(0f, 0f, 0f))
        val tol = REL_TOL * max(baseProj.size, len2(tView))
        if (len2(tView) <= 1e-3f * baseProj.size) continue  // směr ⊥ průmětna → obrys neexistuje

        val gens = view.segments.filter { s ->
            val touch = when {
                baseProj.contains(s.p) && near2(s.q, s.p + tView, tol) -> s.p
                baseProj.contains(s.q) && near2(s.p, s.q + tView, tol) -> s.q
                else -> return@filter false
            }
            baseProj.isTangentAt(touch, tView)
        }
        val touches = gens.map { if (baseProj.contains(it.p)) it.p else it.q }
        if (distinctCount(touches, tol) < 2) return false
        matches += view to gens.map { it.id }
    }
    if (matches.isEmpty()) return false
    matches.forEach { (view, ids) -> view.removeIds += ids }

    val topEq = PlaneEquation(
        top.normal.x, top.normal.y, top.normal.z,
        -dot3(top.normal, top.el.center3D)
    )
    val surface = buildCylindricalSurfaceFrom(
        state = state,
        baseConic3D = base.conic,
        dir = t,
        topPlaneEq = topEq,
        surfaceName = "σ"
    )
    surface.upperConicId = top.conic.id
    base.conic.directrixOfSurfaceIds += surface.id
    top.conic.directrixOfSurfaceIds += surface.id

    // ruční obrysové úsečky nahradí obrysové segmenty plochy
    removeMatched2DSegments(state, views)

    applyPendingCylinderOuterArcs2D(state, surface)
    applyCylinderOuterArcsBokorysAxo(state, surface)
    resolveCylinderInteriorVisibility2D(state, surface)
    if (state.projectionMode == ProjectionMode.AXO) {
        state.basis?.let { rebuildCylindricalSilhouetteAxo(state, surface, it) }
        hideCylinderNonAxoProjectionsAfterAxoConstruction(state, surface, base.conic)
    }

    state.consInfo.value = "Rozpoznána válcová plocha – konstrukce seskupena pod plochu ${surface.name}."
    println("✅ Auto-detekce: válcová plocha z '${base.conic.name}' a '${top.conic.name}'.")
    state.triggerRedraw++
    return true
}

// ─────────────────────────────────────────────────────────────────────────────
// Kuželová plocha
// ─────────────────────────────────────────────────────────────────────────────

private fun detectConicalSurface2D(state: MongeState): Boolean {
    val ellipses = freeEllipses(state)
    if (ellipses.isEmpty()) return false
    val views = detectionViews(state)
    if (views.isEmpty() || views.all { it.segments.isEmpty() }) return false

    for (base in ellipses) {
        for (apex in state.sharedPoints3D) {
            // vrchol v rovině řídicí elipsy → degenerace (vějíř)
            if (abs(dot3(apex.pos() - base.el.center3D, base.normal)) < REL_TOL * base.size) continue
            if (tryFinalizeCone(state, base, apex, views)) return true
        }
    }
    return false
}

private fun tryFinalizeCone(
    state: MongeState,
    base: FreeEllipse,
    apex: Point3D,
    views: List<ViewCtx>
): Boolean {
    val matches = mutableListOf<Pair<ViewCtx, List<String>>>()
    for (view in views) {
        val baseProj = projectEllipse(base, view.project)
        val apexProj = view.project(apex.pos())
        // vrchol uvnitř průmětu podstavy → v tomto pohledu tečny neexistují
        if (baseProj.containsInterior(apexProj)) continue
        val tol = REL_TOL * max(baseProj.size, len2(apexProj - baseProj.c))

        val gens = view.segments.filter { s ->
            val touch = when {
                baseProj.contains(s.p) && near2(s.q, apexProj, tol) -> s.p
                baseProj.contains(s.q) && near2(s.p, apexProj, tol) -> s.q
                else -> return@filter false
            }
            baseProj.isTangentAt(touch, apexProj - touch)
        }
        val touches = gens.map { if (near2(it.q, apexProj, tol)) it.p else it.q }
        if (distinctCount(touches, tol) < 2) return false
        matches += view to gens.map { it.id }
    }
    if (matches.isEmpty()) return false
    matches.forEach { (view, ids) -> view.removeIds += ids }

    val surface = ConicalSurface3D(
        apexId = apex.id,
        directrixId = base.conic.id,
        name = "κ",
        wireWidth = base.conic.strokeWidth,
        color = base.conic.color,
        creationIndex = allocIndex(state)
    )
    base.conic.directrixOfSurfaceIds += surface.id
    state.conicalSurfaces += surface

    removeMatched2DSegments(state, views)

    rebuildConicalSilhouette2D(state, surface, apex)
    if (state.projectionMode == ProjectionMode.AXO) {
        hideConeNonAxoProjectionsAfterAxoConstruction(state, surface, apex, base.conic)
    }

    state.consInfo.value = "Rozpoznána kuželová plocha – konstrukce seskupena pod plochu ${surface.name}."
    println("✅ Auto-detekce: kuželová plocha z '${base.conic.name}', vrchol '${apex.name}'.")
    state.triggerRedraw++
    return true
}

// ─────────────────────────────────────────────────────────────────────────────
// Odstranění ručních obrysových 2D úseček (nahradí je obrysy plochy)
// ─────────────────────────────────────────────────────────────────────────────

private fun removeMatched2DSegments(state: MongeState, views: List<ViewCtx>) {
    for (view in views) {
        val ids = view.removeIds
        if (ids.isEmpty()) continue
        when (view.name) {
            "půdorys" -> {
                val ptIds = state.pointsPudorys.filter { it.parentSegment?.id in ids }.map { it.id }.toHashSet()
                ptIds.forEach { state.labelOffsetsPointsPudorys.remove(it) }
                state.selectedPointsPudorys.removeAll { it.id in ptIds }
                state.selectedSegmentsPudorys.removeAll { it.id in ids }
                state.pointsPudorys.removeAll { it.id in ptIds }
                state.segmentsPudorys.removeAll { it.id in ids }
            }
            "nárys" -> {
                val ptIds = state.pointsNarys.filter { it.parentSegment?.id in ids }.map { it.id }.toHashSet()
                ptIds.forEach { state.labelOffsetsPointsNarys.remove(it) }
                state.selectedPointsNarys.removeAll { it.id in ptIds }
                state.selectedSegmentsNarys.removeAll { it.id in ids }
                state.pointsNarys.removeAll { it.id in ptIds }
                state.segmentsNarys.removeAll { it.id in ids }
            }
            "axo" -> {
                val ptIds = state.pointsAxo.filter { it.parentSegment?.id in ids }.map { it.id }.toHashSet()
                ptIds.forEach { state.labelOffsetsPointsAxo.remove(it) }
                state.selectedPointsAxo.removeAll { it.id in ptIds }
                state.selectedSegmentsAxo.removeAll { it.id in ids }
                state.pointsAxo.removeAll { it.id in ptIds }
                state.segmentsAxo.removeAll { it.id in ids }
            }
        }
    }
}
