package draw.mongescreen.fills

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import model.Offset3D
import model.ProjectionMode
import model.classes.ConicSection2D
import model.classes.projectPoint3DToAxoLocal
import model.runtimeDrawColor
import monge.input.axo.AxoRenderBasis
import monge.input.ruledsurface.ruledSurfaceFamilyIsClosed
import monge.input.ruledsurface.ruledSurfaceTrimmedPrimaryGrids
import state.MongeState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Výplň průmětů kvadrik (koule, kužely, válce) a rotačních ploch (SoR).
// Kužely/válce/koule jsou v průmětech vždy konvexní → silueta = konvexní obal navzorkovaného
// obrysu (řídicí kuželosečky + vrchol u kuželu). Rotační plocha je stoh rovnoběžných kružnic;
// její siluetu skládáme z promítnutých čtyřúhelníků boční plochy (nonzero winding) – tím se
// správně zachová i středová díra anuloidu (viz sorSurfaceQuads).
// Pracujeme v logických souřadnicích daného pohledu; převod na obrazovku (resp. do PDF) řeší volající.

private const val ELLIPSE_FILL_SAMPLES = 96
private const val QUADRIC_FILL_EPS = 1e-4f

// Vzorkování rotační plochy
private const val SOR_RING_SAMPLES = 56   // bodů po obvodu jedné rovnoběžkové kružnice
private const val SOR_MAX_RINGS = 96      // max počet kružnic (poledník se případně podvzorkuje)
// Stejná tolerance jako u konstrukce rovnoběžek: bod takhle blízko osy je vrchol/špička, ne otvor.
private const val SOR_AXIS_SNAP = 0.75f
private const val SOR_ZERO_RADIUS_EPS = 1e-4f
// Polygon kružnice je vepsaný → mezi hranou a křivkou by zůstal nevybarvený srpek u obrysu.
// Mírně poloměr nafoukneme, aby polygon kružnici opsal (přesah se schová pod tah obrysu).
private val SOR_RING_INFLATE = 1f / cos(PI.toFloat() / SOR_RING_SAMPLES)

// Jedna výplň = sjednocení 1+ konvexních polygonů (nonzero winding), v logických souřadnicích.
// [id] = id tělesa (koule/kužel/válec/SoR) – slouží k napojení occlusion clip regionů.
internal data class QuadricFill(val id: String, val polygons: List<List<Offset>>, val color: Color)

// Navzorkuje celou elipsu zadanou sdruženými průměry (p1,p2 hlavní, p3 vedlejší) – logické body.
private fun sampleConicLogical(p1: Offset, p2: Offset, p3: Offset): List<Offset> {
    if (p1 == Offset.Unspecified || p2 == Offset.Unspecified || p3 == Offset.Unspecified) return emptyList()
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val u = (p2 - p1) / 2f
    val a = u.getDistance()
    val mirror = Offset(2f * center.x - p3.x, 2f * center.y - p3.y)
    val v = (p3 - mirror) / 2f
    val b = v.getDistance()
    // Degenerovaný průmět (úsečka): p3 pokrývá jen jednu polovinu druhého průměru,
    // bez zrcadleného konce by konvexnímu obalu chyběl roh (např. kolmý válec v půdorysu).
    if (a < QUADRIC_FILL_EPS || b < QUADRIC_FILL_EPS) return listOf(p1, p2, p3, mirror)
    val uN = u / a
    val vN = v / b
    if (!uN.x.isFinite() || !uN.y.isFinite() || !vN.x.isFinite() || !vN.y.isFinite()) return emptyList()
    return (0 until ELLIPSE_FILL_SAMPLES).map { i ->
        val t = 2f * PI.toFloat() * i / ELLIPSE_FILL_SAMPLES
        center + Offset(
            a * cos(t) * uN.x + b * sin(t) * vN.x,
            a * cos(t) * uN.y + b * sin(t) * vN.y
        )
    }
}

// Body všech 2D kuželoseček (řídicích křivek) patřících danému tělesu v daném pohledu.
private fun conicSamplesFor(
    matchIds: Set<String>,
    conics: List<ConicSection2D>,
    inputs: Map<String, Triple<Offset, Offset, Offset>>,
    requireShown: Boolean
): List<Offset> {
    if (matchIds.isEmpty()) return emptyList()
    val out = ArrayList<Offset>()
    for (c in conics) {
        if (requireShown && !c.showInAxo) continue
        val pid = c.parentId ?: c.parent?.id ?: continue
        if (pid !in matchIds) continue
        val ip = inputs[c.id] ?: continue
        out += sampleConicLogical(ip.first, ip.second, ip.third)
    }
    return out
}

// Orientace proti směru hodin (v logickém prostoru) – aby nonzero winding sjednotil překryvy.
private fun orientCcw(poly: List<Offset>): List<Offset> {
    var area = 0f
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        area += a.x * b.y - b.x * a.y
    }
    return if (area < 0f) poly.asReversed() else poly
}

private data class SorProfileSample(val t: Float, val rSigned: Float)

private fun buildSorProfileWithAxisIntersections(
    samples: List<Offset>,
    axisX: Float,
    axisSnap: Float = SOR_AXIS_SNAP
): List<SorProfileSample> {
    if (samples.size < 2) return emptyList()

    fun signedRadius(p: Offset): Float {
        val r = p.x - axisX
        return if (abs(r) <= axisSnap) 0f else r
    }

    val out = ArrayList<SorProfileSample>(samples.size + 4)
    fun add(t: Float, r: Float) {
        val last = out.lastOrNull()
        if (last == null || abs(last.t - t) > 1e-4f || abs(last.rSigned - r) > 1e-4f) {
            out += SorProfileSample(t, r)
        }
    }

    add(samples.first().y, signedRadius(samples.first()))
    for (i in 0 until samples.lastIndex) {
        val a = samples[i]
        val b = samples[i + 1]
        val raRaw = a.x - axisX
        val rbRaw = b.x - axisX
        val ra = signedRadius(a)
        val rb = signedRadius(b)
        if (ra != 0f && rb != 0f && raRaw * rbRaw < 0f) {
            val alpha = -raRaw / (rbRaw - raRaw)
            add(a.y + alpha * (b.y - a.y), 0f)
        }
        add(b.y, rb)
    }
    return out
}

// Stoh rovnoběžných kružnic z navzorkovaného poledníku → boční plocha jako proužky čtyřúhelníků.
// circleAt(vzorek, theta) vrací 3D bod na rovnoběžkové kružnici; project promítne do logiky pohledu.
// Pozn.: NEpoužíváme konvexní obal dvojic kružnic – ten by u anuloidu zaplnil i středovou díru.
// Promítnutá boční plocha vyplní siluetu plného tělesa (paprsek protne stěnu 2×, nonzero winding
// po vynucení CCW dá winding 2) a u anuloidu nechá díru prázdnou (paprsek stěnu mine → winding 0).
private fun sorSurfaceQuads(
    samples: List<SorProfileSample>,
    project: (Offset3D) -> Offset,
    circleAt: (sample: SorProfileSample, theta: Float) -> Offset3D
): List<List<Offset>> {
    if (samples.size < 2) return emptyList()

    // Případné podvzorkování poledníku, aby počet proužků zůstal rozumný.
    val step = ((samples.size - 1) / SOR_MAX_RINGS) + 1
    val picked = ArrayList<SorProfileSample>()
    var i = 0
    while (i < samples.size) {
        picked += samples[i]
        i += step
    }
    if (picked.last() != samples.last()) picked += samples.last()
    if (picked.size < 2) return emptyList()

    val radii = picked.map { abs(it.rSigned) }
    val rings = picked.map { s ->
        (0 until SOR_RING_SAMPLES).map { k ->
            project(circleAt(s, 2f * PI.toFloat() * k / SOR_RING_SAMPLES))
        }
    }

    val quads = ArrayList<List<Offset>>((rings.size - 1) * SOR_RING_SAMPLES)
    for (r in 0 until rings.size - 1) {
        val a = rings[r]
        val b = rings[r + 1]
        val aOnAxis = radii[r] <= SOR_ZERO_RADIUS_EPS
        val bOnAxis = radii[r + 1] <= SOR_ZERO_RADIUS_EPS
        if (aOnAxis && bOnAxis) continue
        for (k in 0 until SOR_RING_SAMPLES) {
            val k2 = (k + 1) % SOR_RING_SAMPLES
            quads += when {
                aOnAxis -> orientCcw(listOf(a[0], b[k2], b[k]))
                bOnAxis -> orientCcw(listOf(a[k], a[k2], b[0]))
                else -> orientCcw(simpleQuadOrder(a[k], a[k2], b[k2], b[k]))
            }
        }
    }
    return quads
}

// Tam, kde se promítnutý pás mezi sousedními kružnicemi překlopí (obrysový fold v axonometrii),
// vyjde čtyřúhelník zkřížený („motýlek"). Jeho druhý lalok má opačný winding a vynuluje
// winding sousedních čtyřúhelníků → díry ve výplni podél obrysu. Přerovnání obvodu na
// jednoduchý čtyřúhelník laloky odstraní.
private fun simpleQuadOrder(a: Offset, b: Offset, c: Offset, d: Offset): List<Offset> = when {
    segmentsCross(a, b, c, d) -> listOf(a, c, b, d)
    segmentsCross(b, c, d, a) -> listOf(a, b, d, c)
    else -> listOf(a, b, c, d)
}

private fun segmentsCross(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Boolean {
    fun orient(o: Offset, a: Offset, b: Offset): Float =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val d1 = orient(p3, p4, p1)
    val d2 = orient(p3, p4, p2)
    val d3 = orient(p1, p2, p3)
    val d4 = orient(p1, p2, p4)
    return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
        ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
}

/**
 * Vnější hranice stejného sjednocení čtyřúhelníků, které používá fill
 * přímkové plochy. Výsledkem je největší uzavřená kontura v logických
 * souřadnicích zvoleného pohledu.
 */
internal fun ruledSurfaceProjectedOutline(
    generators: List<monge.input.ruledsurface.RuledSurfaceGenerator3D>,
    closedFamily: Boolean,
    project: (Offset3D) -> Offset,
): List<Offset> = ruledSurfaceProjectedFamiliesOutline(listOf(generators), closedFamily, project)

/**
 * Sjednocení mnoha malých ploch po vyváženém stromu dvojic. Lineární řetězení
 * (union := union ∪ face) prohání celý dosavadní obrys každou další buňkou a u
 * hustých sítí (256 tvořic, sedlové průměty s překryvy) trvá stovky ms na
 * snímek; párové slučování složí stejnou geometrii v O(n log n). Pořadí buněk
 * je prostorově souvislé, takže mezivýsledky zůstávají kompaktní.
 */
private fun unionPathsBalanced(faces: List<Path>): Path? {
    if (faces.isEmpty()) return null
    var level = faces
    while (level.size > 1) {
        val next = ArrayList<Path>((level.size + 1) / 2)
        var index = 0
        while (index < level.size) {
            if (index + 1 == level.size) {
                next += level[index]
            } else {
                val merged = Path()
                if (!merged.op(level[index], level[index + 1], PathOperation.Union)) {
                    merged.reset()
                    merged.addPath(level[index])
                    merged.addPath(level[index + 1])
                }
                next += merged
            }
            index += 2
        }
        level = next
    }
    return level.first()
}

private fun quadFacePath(quad: List<Offset>): Path? {
    if (quad.distinct().size < 3) return null
    return Path().apply {
        moveTo(quad[0].x, quad[0].y)
        for (j in 1 until quad.size) lineTo(quad[j].x, quad[j].y)
        close()
    }
}

/** Sjednocení projekcí všech souvislých rodin bez propojení jejich konců. */
internal fun ruledSurfaceProjectedFamiliesOutline(
    families: List<List<monge.input.ruledsurface.RuledSurfaceGenerator3D>>,
    closedFamily: Boolean,
    project: (Offset3D) -> Offset,
): List<Offset> {
    val faces = ArrayList<Path>()
    for (generators in families) {
        if (generators.size < 2) continue
        val stripCount = if (closedFamily) generators.size else generators.lastIndex
        for (i in 0 until stripCount) {
            val a = generators[i]
            val b = generators[(i + 1) % generators.size]
            val quad = simpleQuadOrder(project(a.start), project(b.start), project(b.end), project(a.end))
            quadFacePath(quad)?.let(faces::add)
        }
    }
    val contour = unionPathsBalanced(faces)?.let(::largestPathContour)
    if (!contour.isNullOrEmpty()) return contour
    val fallback = families.maxByOrNull { it.size }.orEmpty()
    return fallback.map { project(it.start) } + fallback.asReversed().map { project(it.end) }
}

/**
 * Zdánlivý obrys (apparent contour) přímkové plochy z její husté souřadnicové
 * sítě. Vnější hranice sjednocení promítnutých buněk sítě je silueta plochy:
 * u zborcených ploch ji tvoří obálka promítnutých tvořic (fold), u rozvinutelných
 * krajní tvořice a oblouky řídicích křivek. Vstupem je jedna souvislá regula, takže
 * výsledek nezávisí na tom, kolik tvořic se právě kreslí, ani se opticky neprotíná.
 */
internal fun ruledSurfaceGridOutline(
    grid: List<List<Offset3D>>,
    closedFamily: Boolean,
    project: (Offset3D) -> Offset,
): List<Offset> = ruledSurfaceGridsOutline(listOf(grid), closedFamily, project)

internal fun ruledSurfaceGridsOutline(
    grids: List<List<List<Offset3D>>>,
    closedFamily: Boolean,
    project: (Offset3D) -> Offset,
): List<Offset> = ruledSurfaceGridsOutlines(grids, closedFamily, project)
    .maxByOrNull(::absoluteOutlineArea)
    .orEmpty()

/**
 * Všechny souvislé kontury sjednocení. Dvě oddělené větve hyperboly musejí
 * zůstat dvěma obrysy; pokud se jejich průměty překryjí, Path union je naopak
 * sloučí do správného společného vnějšího obrysu.
 */
internal fun ruledSurfaceGridsOutlines(
    grids: List<List<List<Offset3D>>>,
    closedFamily: Boolean,
    project: (Offset3D) -> Offset,
): List<List<Offset>> {
    val contours = ruledSurfaceGridsUnionPath(grids, closedFamily, project)
        ?.let(::pathContours).orEmpty().filter { it.size >= 3 }
    if (contours.isNotEmpty()) return contours
    // Fallback: samostatný obvod parametrického obdélníku každé komponenty.
    return grids.mapNotNull { grid ->
        grid.takeIf { it.isNotEmpty() && it.first().isNotEmpty() }?.let {
            it.map { row -> project(row.first()) } +
                it.asReversed().map { row -> project(row.last()) }
        }
    }
}

/**
 * Sjednocení promítnutých buněk sítě jako Path – přesný průmět plochy včetně
 * vnitřní díry uzavřené rodiny (prstenec). Slouží occlusion siluetě, obrysy si
 * z něj berou jen kontury.
 */
internal fun ruledSurfaceGridsUnionPath(
    grids: List<List<List<Offset3D>>>,
    closedFamily: Boolean,
    project: (Offset3D) -> Offset,
): Path? {
    val faces = ArrayList<Path>()
    for (grid in grids) {
        val rows = grid.size
        if (rows < 2 || grid[0].size < 2) continue
        val along = grid[0].size
        val rowCount = if (closedFamily) rows else rows - 1
        for (i in 0 until rowCount) {
            val next = (i + 1) % rows
            for (j in 0 until along - 1) {
                val quad = simpleQuadOrder(
                    project(grid[i][j]),
                    project(grid[next][j]),
                    project(grid[next][j + 1]),
                    project(grid[i][j + 1]),
                )
                quadFacePath(quad)?.let(faces::add)
            }
        }
    }
    return unionPathsBalanced(faces)
}

private fun absoluteOutlineArea(points: List<Offset>): Float {
    if (points.size < 3) return 0f
    var twiceArea = 0f
    for (index in points.indices) {
        val current = points[index]
        val next = points[(index + 1) % points.size]
        twiceArea += current.x * next.y - next.x * current.y
    }
    return abs(twiceArea) * 0.5f
}

/**
 * Prstence rovnoběžek rotační plochy promítnuté do pohledu – vstup pro siluetu occlusion
 * (sjednocení konvexních obalů sousedních prstenců = přesný průmět tělesa s vyplněnými
 * dutinami). Hrubší vzorkování než výplň: silueta jde do polygonových booleanů.
 */
internal fun sorSilhouetteRings(
    state: MongeState,
    id: String,
    project: (Offset3D) -> Offset,
    maxRings: Int = 64,
    ringSamples: Int = 48,
): List<List<Offset>>? {
    state.solidsOfRevolutionNarys.firstOrNull { it.id == id }?.let { solid ->
        val axis = state.lines3D.firstOrNull { it.id == solid.axisLine3DId } ?: return null
        val profile = buildSorProfileWithAxisIntersections(solid.sampledMeridianPolylineXZ, axis.start.x)
        return sorProjectedRings(profile, maxRings, ringSamples, project) { sample, theta ->
            val r = abs(sample.rSigned)
            Offset3D(axis.start.x + r * cos(theta), axis.start.y + r * sin(theta), sample.t)
        }
    }
    state.solidsOfRevolutionPudorys.firstOrNull { it.id == id }?.let { solid ->
        val axis = state.lines3D.firstOrNull { it.id == solid.axisLine3DId } ?: return null
        val profile = buildSorProfileWithAxisIntersections(solid.sampledMeridianPolylineXY, axis.start.x)
        return sorProjectedRings(profile, maxRings, ringSamples, project) { sample, theta ->
            val r = abs(sample.rSigned)
            Offset3D(axis.start.x + r * cos(theta), sample.t, axis.start.z + r * sin(theta))
        }
    }
    return null
}

private fun sorProjectedRings(
    samples: List<SorProfileSample>,
    maxRings: Int,
    ringSamples: Int,
    project: (Offset3D) -> Offset,
    circleAt: (sample: SorProfileSample, theta: Float) -> Offset3D,
): List<List<Offset>>? {
    if (samples.size < 2) return null
    val step = ((samples.size - 1) / maxRings) + 1
    val picked = ArrayList<SorProfileSample>()
    var i = 0
    while (i < samples.size) {
        picked += samples[i]
        i += step
    }
    if (picked.last() != samples.last()) picked += samples.last()
    if (picked.size < 2) return null
    return picked.map { s ->
        if (abs(s.rSigned) <= SOR_ZERO_RADIUS_EPS) {
            listOf(project(circleAt(s, 0f)))
        } else {
            (0 until ringSamples).map { k ->
                project(circleAt(s, 2f * PI.toFloat() * k / ringSamples))
            }
        }
    }
}

// Spočítá výplně všech kvadrik i rotačních ploch pro jeden pohled (logické souřadnice).
internal fun buildQuadricFills(
    state: MongeState,
    conics: List<ConicSection2D>,
    inputs: Map<String, Triple<Offset, Offset, Offset>>,
    requireShown: Boolean,
    viewKind: FillViewKind,
    project: (Offset3D) -> Offset
): List<QuadricFill> {
    val result = ArrayList<QuadricFill>()

    fun addHull(id: String, points: List<Offset>, color: Color) {
        if (points.size < 3) return
        val hull = segmentSolidConvexHull(points)
        if (hull.size >= 3) result += QuadricFill(id, listOf(hull), color)
    }

    // Koule – silueta je celá kružnice/elipsa (2D kuželosečka má parentId = id koule).
    // Viditelnost řešíme přes přítomnost 2D obrysu (conicSamplesFor + requireShown), aby výplň
    // existovala právě tehdy, když je vidět obrys – stejně jako 2D kreslení kuželoseček.
    for (sphere in state.spheres3D) {
        if (!sphere.fillFaces) continue
        addHull(sphere.id, conicSamplesFor(setOf(sphere.id), conics, inputs, requireShown), sphere.color)
    }

    // Kužely – základna (řídicí elipsa) + vrchol; tečné površky leží uvnitř konvexního obalu.
    for (cone in state.conicalSurfaces) {
        if (!cone.fillFaces) continue
        val basePoints = conicSamplesFor(setOf(cone.directrixId), conics, inputs, requireShown)
        if (basePoints.isEmpty()) continue
        val apex = state.sharedPoints3D.firstOrNull { it.id == cone.apexId }
        val all = if (apex != null) basePoints + project(Offset3D(apex.x, apex.y, apex.z)) else basePoints
        addHull(cone.id, all, cone.color)
    }

    // Válce – dolní i horní podstava; boční površky leží uvnitř konvexního obalu.
    for (cyl in state.cylindricalSurfaces) {
        if (!cyl.fillFaces) continue
        val ids = buildSet {
            add(cyl.lowerConicId ?: cyl.directrixId)
            add(cyl.directrixId)
            cyl.upperConicId?.let { add(it) }
        }
        addHull(cyl.id, conicSamplesFor(ids, conics, inputs, requireShown), cyl.color)
    }

    // Rotační plochy s poledníkem v nárysu (osa svisle = z, kružnice v rovinách x,y).
    for (solid in state.solidsOfRevolutionNarys) {
        if (!solid.show || !solid.fillFaces) continue
        val axis = state.lines3D.firstOrNull { it.id == solid.axisLine3DId } ?: continue
        val axisX = axis.start.x
        val axisY = axis.start.y
        val profile = buildSorProfileWithAxisIntersections(solid.sampledMeridianPolylineXZ, axisX)
        val hulls = sorSurfaceQuads(profile, project) { sample, theta ->
            val r0 = abs(sample.rSigned)
            val r = if (r0 <= SOR_ZERO_RADIUS_EPS) 0f else r0 * SOR_RING_INFLATE
            Offset3D(axisX + r * cos(theta), axisY + r * sin(theta), sample.t)
        }
        if (hulls.isNotEmpty()) result += QuadricFill(solid.id, hulls, solid.color)
    }

    // Rotační plochy s poledníkem v půdorysu (osa = y, kružnice v rovinách x,z).
    for (solid in state.solidsOfRevolutionPudorys) {
        if (!solid.show || !solid.fillFaces) continue
        val axis = state.lines3D.firstOrNull { it.id == solid.axisLine3DId } ?: continue
        val axisX = axis.start.x
        val axisZ = axis.start.z
        val profile = buildSorProfileWithAxisIntersections(solid.sampledMeridianPolylineXY, axisX)
        val hulls = sorSurfaceQuads(profile, project) { sample, theta ->
            val r0 = abs(sample.rSigned)
            val r = if (r0 <= SOR_ZERO_RADIUS_EPS) 0f else r0 * SOR_RING_INFLATE
            Offset3D(axisX + r * cos(theta), sample.t, axisZ + r * sin(theta))
        }
        if (hulls.isNotEmpty()) result += QuadricFill(solid.id, hulls, solid.color)
    }

    // Přímkové plochy – výplň z téže husté sítě jedné reguly jako obrys, takže
    // hranice výplně splývá s obrysem (hrubší vzorkování ho přesahovalo) a u
    // uzavřené plochy zůstane vnitřní díra (prstenec v půdorysu). Jedna regula
    // pokryje celý povrch, druhá by se jen překryla.
    for (surface in state.ruledSurfaces) {
        if (!surface.show || !surface.fillFaces) continue
        if (requireShown && surface.outlineCurveIdAxo?.let { id ->
                state.curvesAxo.firstOrNull { it.id == id }?.showInAxo == false
            } == true
        ) continue
        val grids = ruledSurfaceTrimmedPrimaryGrids(state, surface, RULED_FILL_RULINGS, RULED_FILL_ALONG)
        if (grids.isEmpty()) continue
        val closedFamily = ruledSurfaceFamilyIsClosed(state, surface)
        val quads = ArrayList<List<Offset>>()
        for (grid in grids) {
            if (grid.size < 2 || grid[0].size < 2) continue
            val along = grid[0].size
            val rowCount = if (closedFamily) grid.size else grid.size - 1
            for (i in 0 until rowCount) {
                val next = (i + 1) % grid.size
                for (j in 0 until along - 1) {
                    val quad = simpleQuadOrder(
                        project(grid[i][j]), project(grid[next][j]),
                        project(grid[next][j + 1]), project(grid[i][j + 1]),
                    )
                    val clipped = clipRuledFillPolygonAtX12(quad, state, viewKind)
                    if (clipped.distinct().size >= 3) quads += orientCcw(clipped)
                }
            }
        }
        if (quads.isNotEmpty()) result += QuadricFill(surface.id, quads, surface.color)
    }

    return result
}

private const val RULED_FILL_RULINGS = 96
private const val RULED_FILL_ALONG = 4

/**
 * Ořez jedné buňky výplně přímkové plochy osou x₁₂. Půdorys používá logické
 * y >= 0, zatímco nárys je už promítnut jako (x, -z), proto v něm z >= 0
 * odpovídá logickému y <= 0. Mimo Monge se geometrie nemění.
 */
private fun clipRuledFillPolygonAtX12(
    polygon: List<Offset>,
    state: MongeState,
    viewKind: FillViewKind,
): List<Offset> {
    val keepPositive = when (viewKind) {
        FillViewKind.PUDORYS ->
            if (state.projectionMode == ProjectionMode.MONGE && state.defaultClipBelowX12Pudorys) true else return polygon
        FillViewKind.NARYS ->
            if (state.projectionMode == ProjectionMode.MONGE && state.defaultClipAboveX12Narys) false else return polygon
        FillViewKind.AXO -> return polygon
    }
    if (polygon.isEmpty()) return polygon

    fun inside(point: Offset): Boolean = if (keepPositive) point.y >= 0f else point.y <= 0f
    fun intersection(a: Offset, b: Offset): Offset {
        val t = -a.y / (b.y - a.y)
        return Offset(a.x + t * (b.x - a.x), 0f)
    }

    val result = ArrayList<Offset>(polygon.size + 2)
    var previous = polygon.last()
    var previousInside = inside(previous)
    for (current in polygon) {
        val currentInside = inside(current)
        when {
            previousInside && currentInside -> result += current
            previousInside && !currentInside -> result += intersection(previous, current)
            !previousInside && currentInside -> {
                result += intersection(previous, current)
                result += current
            }
        }
        previous = current
        previousInside = currentInside
    }
    return result
}

internal fun quadricFillsPudorys(state: MongeState): List<QuadricFill> =
    buildQuadricFills(state, state.conicsPudorys, state.conicInputPointsPudorys, requireShown = false, viewKind = FillViewKind.PUDORYS) { p ->
        Offset(p.x, p.y)
    }

internal fun quadricFillsNarys(state: MongeState): List<QuadricFill> =
    buildQuadricFills(state, state.conicsNarys, state.conicInputPointsNarys, requireShown = false, viewKind = FillViewKind.NARYS) { p ->
        // Nárys: (x, z) se kreslí jako (x, -z) – stejně jako sdružené průměry kuželoseček.
        Offset(p.x, -p.z)
    }

internal fun quadricFillsAxo(state: MongeState, basis: AxoRenderBasis): List<QuadricFill> =
    buildQuadricFills(state, state.conicsAxo, state.conicInputPointsAxo, requireShown = true, viewKind = FillViewKind.AXO) { p ->
        projectPoint3DToAxoLocal(p, basis)
    }

// ----- Compose Canvas -----

// Silueta jedné výplně jako logický Path (nonzero winding → překryvy frustumů se nesčítají v alfě).
internal fun quadricFillLogicalPath(fill: QuadricFill): Path? {
    val path = Path()
    var any = false
    for (poly in fill.polygons) {
        if (poly.size < 3) continue
        path.moveTo(poly[0].x, poly[0].y)
        for (i in 1 until poly.size) path.lineTo(poly[i].x, poly[i].y)
        path.close()
        any = true
    }
    return if (any) path else null
}

// Vykreslí výplně kvadrik. [clips] = mapa id → LOGICKÁ oblast k VYNECHÁNÍ (occlusion). Výplň se
// kreslí se svým vlastním nonzero windingem (díra anuloidu zůstane) a clip se odečte plátnem přes
// clipPath(Difference) – žádný Path.op nad výplní, takže i tisíce kontur rotační plochy jsou levné.
internal fun DrawScope.drawQuadricFills(
    fills: List<QuadricFill>,
    screenMatrix: Matrix,
    clips: Map<String, Path>,
) {
    for (fill in fills) {
        val logical = quadricFillLogicalPath(fill) ?: continue
        if (logical.isEmpty) continue
        val screen = Path().also { it.addPath(logical); it.transform(screenMatrix) }
        val color = fill.color.runtimeDrawColor().copy(alpha = SOLID_FILL_ALPHA)
        val clip = clips[fill.id]
        if (clip == null || clip.isEmpty) {
            drawPath(path = screen, color = color)
        } else {
            val screenClip = Path().also { it.addPath(clip); it.transform(screenMatrix) }
            clipPath(screenClip, clipOp = ClipOp.Difference) {
                drawPath(path = screen, color = color)
            }
        }
    }
}

fun DrawScope.drawQuadricFillsPudorys(state: MongeState, clips: Map<String, Path> = emptyMap()) {
    drawQuadricFills(quadricFillsPudorys(state), FillView.pudorys(state).screenMatrix, clips)
}

fun DrawScope.drawQuadricFillsNarys(state: MongeState, clips: Map<String, Path> = emptyMap()) {
    drawQuadricFills(quadricFillsNarys(state), FillView.narys(state).screenMatrix, clips)
}

fun DrawScope.drawQuadricFillsAxo(state: MongeState, basis: AxoRenderBasis, clips: Map<String, Path> = emptyMap()) {
    drawQuadricFills(quadricFillsAxo(state, basis), FillView.axo(state, basis).screenMatrix, clips)
}
