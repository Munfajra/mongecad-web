package monge.input.ruledsurface
import utils.replaceAll

import androidx.compose.ui.geometry.Offset
import model.LineStyle
import model.Offset3D
import model.classes.CurveAxo
import model.classes.CurveBokorys
import model.classes.CurveNarys
import model.classes.CurvePudorys
import model.classes.RuledSurface3D
import model.classes.projectPoint3DToAxoLocal
import draw.mongescreen.fills.ruledSurfaceGridsOutline
import draw.mongescreen.fills.ruledSurfaceGridsOutlines
import state.MongeState
import utils.allocIndex
import kotlin.math.abs

private const val OUTLINE_RULINGS = 256
private const val OUTLINE_ALONG = 8
// Fold křivka potřebuje jemnější vzorkování podél tvořic, jinak by byla hranatá.
private const val OUTLINE_ALONG_FOLD = 24
private const val OUTLINE_GEOMETRY_VERSION = 21

private data class OutlinePolyline(
    val points: List<Offset>,
    val closed: Boolean,
)

private data class RuledOutlineGeometry(
    val pudorys: OutlinePolyline,
    val narys: OutlinePolyline,
    val bokorys: OutlinePolyline,
    val axo3D: List<Offset3D>,
    val axoClosed: Boolean,
)

/**
 * 3D obrys přímkové plochy pro libovolné aktuální promítání. Používá jej OpenGL
 * highlight, protože uložený axo obrys odpovídá Monge axonometrické bázi, nikoli
 * volně otáčené OpenGL kameře. Síť je ořezaná uživatelským ořezem/přesahem
 * tvořic, takže obrys sleduje skutečné konce vykreslené plochy (stejně jako
 * fill a řezací hrany occlusion, které tuto funkci konzumují také).
 */
fun ruledSurfaceViewOutlines3D(
    state: MongeState,
    surface: RuledSurface3D,
    project: (Offset3D) -> Offset,
): List<Pair<List<Offset3D>, Boolean>> {
    val closedFamily = ruledSurfaceFamilyIsClosed(state, surface)
    val grids = ruledSurfaceTrimmedPrimaryGrids(state, surface, OUTLINE_RULINGS, OUTLINE_ALONG)
    if (grids.isEmpty()) return emptyList()
    val projectedContours = ruledSurfaceGridsOutlines(grids, closedFamily, project)
    // Analytický hyperboloid je konečná uzavřená regula mezi dvěma podstavami.
    // Jeho highlight proto musí být vnější obálka celé promítnuté sítě, ne jen
    // jeden fold řetěz. Při pohledu podél osy může union vrátit také vnitřní
    // konturu hrdla; vnějším obrysem je vždy kontura s největší plochou.
    val contoursForHighlight = if (ruledSurfaceIsThreeLines(surface)) {
        listOfNotNull(projectedContours.maxByOrNull(::projectedContourArea))
    } else projectedContours
    return contoursForHighlight.mapNotNull { contour ->
        val simplified = simplifyClosedProjectedContour(contour)
        liftProjectedContourToSurface(simplified, grids, closedFamily, project)
            .takeIf { it.size >= 2 }
            ?.let { it to true }
    }
}

private fun projectedContourArea(points: List<Offset>): Float {
    if (points.size < 3) return 0f
    var twiceArea = 0f
    for (index in points.indices) {
        val next = points[(index + 1) % points.size]
        twiceArea += points[index].x * next.y - next.x * points[index].y
    }
    return abs(twiceArea) * 0.5f
}

/**
 * Path union často vrací dlouhé série prakticky kolineárních bodů. Pro OpenGL
 * highlight je zjednodušíme v obrazových souřadnicích; tolerance pod jeden pixel
 * nemění viditelný tvar, ale výrazně zmenšuje ribbon posílaný každý snímek na GPU.
 */
private fun simplifyClosedProjectedContour(points: List<Offset>, tolerance: Float = 0.6f): List<Offset> {
    val unique = ArrayList<Offset>(points.size)
    val duplicateToleranceSquared = 0.01f
    for (point in points) {
        if (unique.lastOrNull()?.let { (point - it).getDistanceSquared() > duplicateToleranceSquared } != false) {
            unique += point
        }
    }
    if (unique.size > 1 && (unique.first() - unique.last()).getDistanceSquared() <= duplicateToleranceSquared) {
        unique.removeAt(unique.lastIndex)
    }
    if (unique.size < 4) return unique

    val split = (1 until unique.size).maxByOrNull { index ->
        (unique[index] - unique.first()).getDistanceSquared()
    } ?: return unique
    if (split <= 0) return unique

    val firstHalf = simplifyOpenPolyline(unique.subList(0, split + 1), tolerance)
    val secondHalf = simplifyOpenPolyline(unique.subList(split, unique.size) + unique.first(), tolerance)
    return firstHalf + secondHalf.drop(1).dropLast(1)
}

private fun simplifyOpenPolyline(points: List<Offset>, tolerance: Float): List<Offset> {
    if (points.size <= 2) return points
    val a = points.first()
    val b = points.last()
    val ab = b - a
    val abLengthSquared = ab.getDistanceSquared()
    var furthestIndex = -1
    var furthestDistanceSquared = 0f
    for (index in 1 until points.lastIndex) {
        val ap = points[index] - a
        val t = if (abLengthSquared <= 1e-8f) 0f else
            ((ap.x * ab.x + ap.y * ab.y) / abLengthSquared).coerceIn(0f, 1f)
        val nearest = a + ab * t
        val distanceSquared = (points[index] - nearest).getDistanceSquared()
        if (distanceSquared > furthestDistanceSquared) {
            furthestDistanceSquared = distanceSquared
            furthestIndex = index
        }
    }
    if (furthestIndex < 0 || furthestDistanceSquared <= tolerance * tolerance) return listOf(a, b)
    val left = simplifyOpenPolyline(points.subList(0, furthestIndex + 1), tolerance)
    val right = simplifyOpenPolyline(points.subList(furthestIndex, points.size), tolerance)
    return left.dropLast(1) + right
}

/** Vytvoří odvozenou uzavřenou Curve pro půdorys, nárys, bokorys a axo. */
fun createRuledSurfaceOutlines(state: MongeState, surface: RuledSurface3D) {
    val geometry = deriveRuledSurfaceOutlines(state, surface) ?: return
    val baseName = "obrys ${surface.name}"

    val pudorys = CurvePudorys(
        parentId = surface.id,
        name = "${baseName}₁",
        color = surface.color,
        strokeWidth = surface.wireWidth,
        lineStyle = LineStyle.Solid,
        points = emptyList(),
        closed = geometry.pudorys.closed,
        creationIndex = allocIndex(state),
        showInAxoInitial = false,
        polylineLocal = geometry.pudorys.points,
    )
    val narys = CurveNarys(
        parentId = surface.id,
        name = "${baseName}₂",
        color = surface.color,
        strokeWidth = surface.wireWidth,
        pointIds = emptyList(),
        closed = geometry.narys.closed,
        lineStyle = LineStyle.Solid,
        creationIndex = allocIndex(state),
        showInAxoInitial = false,
        polylineLocal = geometry.narys.points,
    )
    val bokorys = CurveBokorys(
        parentId = surface.id,
        name = "${baseName}₃",
        color = surface.color,
        strokeWidth = surface.wireWidth,
        pointIds = emptyList(),
        closed = geometry.bokorys.closed,
        lineStyle = LineStyle.Solid,
        creationIndex = allocIndex(state),
        showInAxoInitial = false,
        polylineLocal = geometry.bokorys.points,
    )
    val axo = CurveAxo(
        parentId = surface.id,
        name = "${baseName}ₐ",
        color = surface.color,
        strokeWidth = surface.wireWidth,
        pointIds = emptyList(),
        closed = geometry.axoClosed,
        lineStyle = LineStyle.Solid,
        creationIndex = allocIndex(state),
        showInAxoInitial = true,
        polyline3D = geometry.axo3D,
    )

    state.curvesPudorys += pudorys
    state.curvesNarys += narys
    state.curvesBokorys += bokorys
    state.curvesAxo += axo
    surface.outlineCurveIdPudorys = pudorys.id
    surface.outlineCurveIdNarys = narys.id
    surface.outlineCurveIdBokorys = bokorys.id
    surface.outlineCurveIdAxo = axo.id
    surface.outlineGeometryVersion = OUTLINE_GEOMETRY_VERSION
}

private fun deriveRuledSurfaceOutlines(state: MongeState, surface: RuledSurface3D): RuledOutlineGeometry? {
    // Sedlo/zborcená kvadrika ze 3 přímek: obrys je jen PRAVÁ silueta (fold
    // křivka – parabola/hyperbola zdánlivého obrysu). Hranice rozsahu tvořic
    // do obrysu nepatří, tu ukazují samotné tvořice.
    val threeLines = ruledSurfaceIsThreeLines(surface)
    val grids = ruledSurfacePrimaryGrids(state, surface, OUTLINE_RULINGS, OUTLINE_ALONG)
    if (grids.isEmpty()) return null
    val grid = grids.first()
    val closedFamily = ruledSurfaceFamilyIsClosed(state, surface)
    // Fold se počítá na regularizované síti (hladký střed, pevná délka řádku):
    // ořezová parametrizace není přes rodinu spojitá (krajní průsečík přeskakuje
    // mezi řídicími přímkami) a vyráběla by falešné foldy podél tvořic.
    val foldGrid = if (threeLines) ruledSurfaceFoldGrid(state, surface, OUTLINE_RULINGS, OUTLINE_ALONG_FOLD) else null

    fun outline2D(project: (Offset3D) -> Offset): OutlinePolyline = if (threeLines) {
        val fold = foldGrid?.let { gridFoldContour(it, closedFamily, project) }
        OutlinePolyline(fold?.points3D?.map(project).orEmpty(), fold?.closed == true)
    } else {
        OutlinePolyline(ruledSurfaceGridsOutline(grids, closedFamily, project), closed = true)
    }

    val pudorysOutline = outline2D { Offset(it.x, it.y) }
    val narysOutline = outline2D { Offset(it.x, it.z) }
    val bokorysOutline = outline2D { Offset(it.y, it.z) }

    val axoBasis = state.basis
    val axo3D: List<Offset3D>
    val axoClosed: Boolean
    if (threeLines) {
        val fold = if (axoBasis != null && foldGrid != null) {
            gridFoldContour(foldGrid, closedFamily) { projectPoint3DToAxoLocal(it, axoBasis) }
        } else null
        axo3D = fold?.points3D.orEmpty()
        axoClosed = fold?.closed == true
    } else {
        val axoOutline2D = axoBasis?.let { basis ->
            ruledSurfaceGridsOutline(grids, closedFamily) { projectPoint3DToAxoLocal(it, basis) }
        }.orEmpty()
        axo3D = if (axoBasis != null && axoOutline2D.isNotEmpty()) {
            liftProjectedContourToSurface(axoOutline2D, grids, closedFamily) {
                projectPoint3DToAxoLocal(it, axoBasis)
            }
        } else {
            grid.map { it.first() } + grid.asReversed().map { it.last() }
        }
        axoClosed = true
    }
    return RuledOutlineGeometry(pudorysOutline, narysOutline, bokorysOutline, axo3D, axoClosed)
}

/**
 * Regularizovaná síť pro výpočet foldu: každý řádek je tvořice převzorkovaná
 * symetricky kolem bodu nejblíž těžišti rodiny, se společnou délkou a spojitě
 * orientovaným směrem. Taková parametrizace je regulární, takže nulová hladina
 * orientace odpovídá jen skutečné siluetě plochy.
 */
private fun ruledSurfaceFoldGrid(
    state: MongeState,
    surface: RuledSurface3D,
    rulings: Int,
    along: Int,
): List<List<Offset3D>>? {
    if (along < 3) return null
    val family = sampleRuledSurfacePrimaryFamily(state, surface, rulings)
    if (family.size < 3) return null
    var sum = Offset3D(0f, 0f, 0f)
    for (generator in family) sum = sum + generator.start + generator.end
    val centroid = sum * (1f / (2 * family.size))

    data class FoldRow(val center: Offset3D, val dir: Offset3D, val halfLength: Float)
    var previousDir: Offset3D? = null
    val rows = family.map { generator ->
        var dir = (generator.end - generator.start).foldNormalized()
        previousDir?.let { if ((dir dot it) < 0f) dir = dir * -1f }
        previousDir = dir
        val center = generator.start + dir * ((centroid - generator.start) dot dir)
        val halfLength = maxOf(
            abs((generator.start - center) dot dir),
            abs((generator.end - center) dot dir),
        )
        FoldRow(center, dir, halfLength)
    }
    val halfLength = rows.maxOf { it.halfLength }
    if (halfLength < 1e-4f) return null
    return rows.map { row ->
        List(along) { j -> row.center + row.dir * (halfLength * (2f * j / (along - 1) - 1f)) }
    }
}

private fun Offset3D.foldNormalized(): Offset3D {
    val length = kotlin.math.sqrt(this dot this)
    return if (length < 1e-8f) Offset3D(1f, 0f, 0f) else this * (1f / length)
}

private class GridFoldChain(val points3D: List<Offset3D>, val closed: Boolean)

/**
 * Pravá silueta plochy v daném promítání: nulová hladina orientace promítnuté
 * tečné báze na síti (marching squares s lineární interpolací po hranách
 * buněk). Vrací nejdelší souvislý řetěz; když promítnutí žádný fold nemá
 * (např. půdorys sedla z=xy), vrací null a obrys se v tomto pohledu nekreslí.
 */
private fun gridFoldContour(
    grid: List<List<Offset3D>>,
    closedFamily: Boolean,
    project: (Offset3D) -> Offset,
): GridFoldChain? {
    val rows = grid.size
    if (rows < 3) return null
    val along = grid[0].size
    if (along < 3) return null
    val projected = grid.map { row -> row.map(project) }
    // Orientace promítnuté tečné báze (∂u × ∂v) – mění znaménko přes obrys.
    val field = Array(rows) { i ->
        val iPrev = if (closedFamily) (i - 1 + rows) % rows else maxOf(i - 1, 0)
        val iNext = if (closedFamily) (i + 1) % rows else minOf(i + 1, rows - 1)
        FloatArray(along) { j ->
            val du = projected[iNext][j] - projected[iPrev][j]
            val dv = projected[i][minOf(j + 1, along - 1)] - projected[i][maxOf(j - 1, 0)]
            du.x * dv.y - du.y * dv.x
        }
    }

    // Průsečíky nulové hladiny s hranami buněk; klíč hrany slouží k řetězení.
    data class FoldSegment(val aKey: Long, val bKey: Long, val a: Offset3D, val b: Offset3D)
    fun familyEdgeKey(i: Int, j: Int): Long = (i.toLong() * along + j) * 2
    fun alongEdgeKey(i: Int, j: Int): Long = (i.toLong() * along + j) * 2 + 1
    val segments = ArrayList<FoldSegment>()
    val rowCount = if (closedFamily) rows else rows - 1
    for (i in 0 until rowCount) {
        val next = (i + 1) % rows
        for (j in 0 until along - 1) {
            val crossingKeys = ArrayList<Long>(4)
            val crossingPoints = ArrayList<Offset3D>(4)
            fun tryEdge(key: Long, i0: Int, j0: Int, i1: Int, j1: Int) {
                val f0 = field[i0][j0]
                val f1 = field[i1][j1]
                if (f0 * f1 > 0f || f0 == f1) return
                val t = f0 / (f0 - f1)
                if (t.isNaN() || t < 0f || t > 1f) return
                crossingKeys += key
                crossingPoints += grid[i0][j0] + (grid[i1][j1] - grid[i0][j0]) * t
            }
            tryEdge(familyEdgeKey(i, j), i, j, next, j)
            tryEdge(alongEdgeKey(next, j), next, j, next, j + 1)
            tryEdge(familyEdgeKey(i, j + 1), i, j + 1, next, j + 1)
            tryEdge(alongEdgeKey(i, j), i, j, i, j + 1)
            var k = 0
            while (k + 1 < crossingKeys.size) {
                segments += FoldSegment(
                    crossingKeys[k], crossingKeys[k + 1],
                    crossingPoints[k], crossingPoints[k + 1],
                )
                k += 2
            }
        }
    }
    if (segments.isEmpty()) return null

    val adjacency = HashMap<Long, MutableList<Int>>()
    segments.forEachIndexed { index, segment ->
        adjacency.getOrPut(segment.aKey) { ArrayList() } += index
        adjacency.getOrPut(segment.bKey) { ArrayList() } += index
    }
    val used = BooleanArray(segments.size)
    var best: GridFoldChain? = null
    var bestLength = -1f
    for (seed in segments.indices) {
        if (used[seed]) continue
        used[seed] = true
        val points = ArrayDeque<Offset3D>()
        points.addLast(segments[seed].a)
        points.addLast(segments[seed].b)
        var headKey = segments[seed].aKey
        var tailKey = segments[seed].bKey
        var closedChain = false
        fun extend(fromTail: Boolean) {
            while (true) {
                val key = if (fromTail) tailKey else headKey
                val nextIndex = adjacency[key]?.firstOrNull { !used[it] } ?: return
                used[nextIndex] = true
                val segment = segments[nextIndex]
                val nextKey = if (segment.aKey == key) segment.bKey else segment.aKey
                val nextPoint = if (segment.aKey == key) segment.b else segment.a
                if (fromTail) {
                    points.addLast(nextPoint)
                    tailKey = nextKey
                } else {
                    points.addFirst(nextPoint)
                    headKey = nextKey
                }
                if (tailKey == headKey) {
                    closedChain = true
                    return
                }
            }
        }
        extend(fromTail = true)
        if (!closedChain) extend(fromTail = false)
        var length = 0f
        for (k in 1 until points.size) {
            length += (project(points[k]) - project(points[k - 1])).getDistance()
        }
        if (length > bestLength) {
            bestLength = length
            best = GridFoldChain(points.toList(), closedChain)
        }
    }
    return best?.takeIf { it.points3D.size >= 2 }
}

/** Přepočítá view-dependent obrysy bez změny jejich ID a showInAxo. */
fun refreshRuledSurfaceOutlineGeometry(state: MongeState) {
    state.ruledSurfaces.forEach { surface ->
        val geometry = deriveRuledSurfaceOutlines(state, surface) ?: return@forEach
        state.curvesPudorys.replaceAll { old ->
            if (old.id == surface.outlineCurveIdPudorys) old.copy(polylineLocal = geometry.pudorys.points, closed = geometry.pudorys.closed).also { it.showInAxo = old.showInAxo } else old
        }
        state.curvesNarys.replaceAll { old ->
            if (old.id == surface.outlineCurveIdNarys) old.copy(polylineLocal = geometry.narys.points, closed = geometry.narys.closed).also { it.showInAxo = old.showInAxo } else old
        }
        state.curvesBokorys.replaceAll { old ->
            if (old.id == surface.outlineCurveIdBokorys) old.copy(polylineLocal = geometry.bokorys.points, closed = geometry.bokorys.closed).also { it.showInAxo = old.showInAxo } else old
        }
        state.curvesAxo.replaceAll { old ->
            if (old.id == surface.outlineCurveIdAxo) old.copy(polyline3D = geometry.axo3D, closed = geometry.axoClosed).also { it.showInAxo = old.showInAxo } else old
        }
        surface.outlineGeometryVersion = OUTLINE_GEOMETRY_VERSION
    }
}

private fun liftProjectedContourToSurface(
    contour: List<Offset>,
    grids: List<List<List<Offset3D>>>,
    closedFamily: Boolean,
    project: (Offset3D) -> Offset,
): List<Offset3D> {
    data class Edge(val a3: Offset3D, val b3: Offset3D, val a2: Offset, val b2: Offset)
    val edges = ArrayList<Edge>()
    fun addEdge(a: Offset3D, b: Offset3D) { edges += Edge(a, b, project(a), project(b)) }
    for (grid in grids) {
        val rows = grid.size
        val along = if (rows > 0) grid[0].size else 0
        val rowCount = if (closedFamily) rows else rows - 1
        for (i in 0 until rows) {
            for (j in 0 until along - 1) addEdge(grid[i][j], grid[i][j + 1]) // podél tvořice
        }
        for (i in 0 until rowCount) {
            val next = (i + 1) % rows
            for (j in 0 until along) addEdge(grid[i][j], grid[next][j]) // napříč tvořicemi
        }
    }
    if (edges.isEmpty()) return emptyList()
    return contour.map { point ->
        var bestPoint = edges.first().a3
        var bestDistance = Float.POSITIVE_INFINITY
        for (edge in edges) {
            val d = edge.b2 - edge.a2
            val d2 = d.getDistanceSquared()
            val t = if (d2 < 1e-9f) 0f else (((point - edge.a2) dot2 d) / d2).coerceIn(0f, 1f)
            val projected = edge.a2 + d * t
            val distance = (point - projected).getDistanceSquared()
            if (distance < bestDistance) {
                bestDistance = distance
                bestPoint = edge.a3 + (edge.b3 - edge.a3) * t
            }
        }
        bestPoint
    }
}

private infix fun Offset.dot2(other: Offset): Float = x * other.x + y * other.y

/** Doplní obrysy starším uloženým plochám, které ještě obsahovaly jen jejich ID pole. */
fun ensureRuledSurfaceOutlines(state: MongeState) {
    state.ruledSurfaces.toList().forEach { surface ->
        val complete = surface.outlineCurveIdPudorys?.let { id -> state.curvesPudorys.any { it.id == id } } == true &&
            surface.outlineCurveIdNarys?.let { id -> state.curvesNarys.any { it.id == id } } == true &&
            surface.outlineCurveIdBokorys?.let { id -> state.curvesBokorys.any { it.id == id } } == true &&
            surface.outlineCurveIdAxo?.let { id -> state.curvesAxo.any { it.id == id } } == true
        if (!complete || surface.outlineGeometryVersion < OUTLINE_GEOMETRY_VERSION) {
            state.curvesPudorys.removeAll { it.id == surface.outlineCurveIdPudorys }
            state.curvesNarys.removeAll { it.id == surface.outlineCurveIdNarys }
            state.curvesBokorys.removeAll { it.id == surface.outlineCurveIdBokorys }
            state.curvesAxo.removeAll { it.id == surface.outlineCurveIdAxo }
            createRuledSurfaceOutlines(state, surface)
        }
    }
}

/** Migruje staré pořadí 1. krajní, 2. krajní, 3. pomocná na 1.–2.–3. výběr. */
fun repairRuledSurfaceDirectrixOrder(state: MongeState) {
    state.ruledSurfaces.replaceAll { surface ->
        val guide = surface.thirdDirectrix
        if (surface.directorPlaneId == null && guide != null && surface.directrixOrderVersion < 1) {
            surface.copy(
                secondBoundaryDirectrix = guide,
                thirdDirectrix = surface.secondBoundaryDirectrix,
                directrixOrderVersion = 1,
                outlineGeometryVersion = 0,
            )
        } else surface
    }
}

fun updateRuledSurfaceOutlineStyle(state: MongeState, surface: RuledSurface3D) {
    val baseName = "obrys ${surface.name}"
    state.curvesPudorys.replaceAll { curve ->
        if (curve.id == surface.outlineCurveIdPudorys) curve.copy(name = "${baseName}₁", color = surface.color, strokeWidth = surface.wireWidth).also { it.showInAxo = curve.showInAxo } else curve
    }
    state.curvesNarys.replaceAll { curve ->
        if (curve.id == surface.outlineCurveIdNarys) curve.copy(name = "${baseName}₂", color = surface.color, strokeWidth = surface.wireWidth).also { it.showInAxo = curve.showInAxo } else curve
    }
    state.curvesBokorys.replaceAll { curve ->
        if (curve.id == surface.outlineCurveIdBokorys) curve.copy(name = "${baseName}₃", color = surface.color, strokeWidth = surface.wireWidth).also { it.showInAxo = curve.showInAxo } else curve
    }
    state.curvesAxo.replaceAll { curve ->
        if (curve.id == surface.outlineCurveIdAxo) curve.copy(name = "${baseName}ₐ", color = surface.color, strokeWidth = surface.wireWidth).also { it.showInAxo = curve.showInAxo } else curve
    }
}

fun deleteRuledSurface(state: MongeState, surfaceId: String) {
    val surface = state.ruledSurfaces.firstOrNull { it.id == surfaceId } ?: return
    removeRuledSurfaceGeneratorLines(state, surface)
    state.curvesPudorys.removeAll { it.id == surface.outlineCurveIdPudorys }
    state.curvesNarys.removeAll { it.id == surface.outlineCurveIdNarys }
    state.curvesBokorys.removeAll { it.id == surface.outlineCurveIdBokorys }
    state.curvesAxo.removeAll { it.id == surface.outlineCurveIdAxo }
    state.ruledSurfaces.removeAll { it.id == surfaceId }
    if (state.selectedRuledSurfaceId == surfaceId) state.selectedRuledSurfaceId = null
}
