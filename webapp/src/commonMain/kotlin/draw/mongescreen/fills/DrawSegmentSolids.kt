package draw.mongescreen.fills
import utils.putIfAbsent

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import model.Offset3D
import model.Point3D
import model.classes.SegmentSolid3D
import model.classes.SegmentSolidType
import model.runtimeDrawColor
import monge.input.axo.AxoRenderBasis
import state.MongeState
import kotlin.math.abs

// Velmi průhledná výplň vnitřku průmětu hranolu/jehlanu.
// DOČASNĚ 0.5f kvůli ověření occlusion (křivky prosvítají) – vrátit na 0.13f.
internal const val SOLID_FILL_ALPHA = 0.5f
private const val VERTEX_EPS = 1e-4f

private typealias VertexKey = Triple<Int, Int, Int>

// --- Monge / KOTO: výplň v půdorysu, body (x, y) ---
fun DrawScope.drawSegmentSolidsFillPudorys(state: MongeState, clips: Map<String, Path> = emptyMap()) {
    if (state.segmentSolids3D.isEmpty()) return
    fillSolids(state, FillView.pudorys(state), clips)
}

// --- Monge: výplň v nárysu, body (x, z) kreslené jako (x, -z) ---
fun DrawScope.drawSegmentSolidsFillNarys(state: MongeState, clips: Map<String, Path> = emptyMap()) {
    if (state.segmentSolids3D.isEmpty()) return
    fillSolids(state, FillView.narys(state), clips)
}

// --- Axo: výplň prostorového průmětu hranolu/jehlanu ---
fun DrawScope.drawSegmentSolidsFillAxo(state: MongeState, basis: AxoRenderBasis, clips: Map<String, Path> = emptyMap()) {
    if (state.segmentSolids3D.isEmpty()) return
    fillSolids(state, FillView.axo(state, basis), clips) { solid -> !solidHiddenInAxo(state, solid) }
}

// [clips] = mapa id → LOGICKÁ oblast k VYNECHÁNÍ (occlusion); odečte se plátnem přes clipPath.
private fun DrawScope.fillSolids(
    state: MongeState,
    view: FillView,
    clips: Map<String, Path>,
    isVisible: (SegmentSolid3D) -> Boolean = { true },
) {
    for (solid in state.segmentSolids3D) {
        if (!solid.show || !solid.fillFaces) continue
        if (!isVisible(solid)) continue
        val logical = buildSolidFillPathLogical(state, solid, view) ?: continue
        if (logical.isEmpty) continue
        val screen = Path().also { it.addPath(logical); it.transform(view.screenMatrix) }
        val color = solidFillColor(solid)
        val clip = clips[solid.id]
        if (clip == null || clip.isEmpty) {
            drawPath(path = screen, color = color)
        } else {
            val screenClip = Path().also { it.addPath(clip); it.transform(view.screenMatrix) }
            clipPath(screenClip, clipOp = ClipOp.Difference) {
                drawPath(path = screen, color = color)
            }
        }
    }
}

// Logická silueta tělesa (sjednocení stěn / fallback konvexní obal) v souřadnicích daného pohledu.
internal fun buildSolidFillPathLogical(state: MongeState, solid: SegmentSolid3D, view: FillView): Path? =
    buildSolidFillPath(state, solid) { p -> view.project(Offset3D(p.x, p.y, p.z)) }

// Těleso je v axu schované, pokud existují jeho axonometrické průměty úseček
// a všechny mají showInAxo == false (toggle "A" v inspektoru tělesa).
private fun solidHiddenInAxo(state: MongeState, solid: SegmentSolid3D): Boolean {
    val ids = solid.segmentIds3D.toHashSet()
    val axoSegments = state.segmentsAxo.filter { it.parent?.id in ids || it.parentId in ids }
    return axoSegments.isNotEmpty() && axoSegments.none { it.showInAxo }
}

// Sjednocení průmětů jednotlivých stěn -> správný obrys i pro nekonvexní podstavy,
// bez dvojitého ztmavení v překryvech. Fallback na konvexní obal, pokud stěny
// nelze zrekonstruovat (např. nekompletní data).
private fun buildSolidFillPath(
    state: MongeState,
    solid: SegmentSolid3D,
    project: (Point3D) -> Offset
): Path? {
    val faces = segmentSolidFaces(state, solid)
    if (faces != null) {
        var combined: Path? = null
        for (face in faces) {
            val points = face.map(project)
            if (points.size < 3) continue
            val facePath = polygonPath(points)
            val previous = combined
            combined = if (previous == null) {
                facePath
            } else {
                Path().also { it.op(previous, facePath, PathOperation.Union) }
            }
        }
        if (combined != null) return combined
    }

    // Fallback: konvexní obal všech vrcholů.
    val vertices = segmentSolidFallbackVertices(state, solid)
    if (vertices.size < 3) return null
    val hull = convexHull(vertices.map(project))
    if (hull.size < 3) return null
    return polygonPath(hull)
}

private fun polygonPath(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

private fun solidFillColor(solid: SegmentSolid3D): Color =
    solid.color.runtimeDrawColor().copy(alpha = SOLID_FILL_ALPHA)

// ----- Topologie stěn -----

private class SolidTopology(
    val pointByKey: Map<VertexKey, Point3D>,
    val faces: List<List<VertexKey>>
)

// Stěny tělesa jako seřazené seznamy 3D vrcholů (podstavy + boční stěny).
// null, pokud topologii nelze zrekonstruovat -> volající použije konvexní obal.
internal fun segmentSolidFaces(state: MongeState, solid: SegmentSolid3D): List<List<Point3D>>? {
    val topology = solidTopology(state, solid) ?: return null
    val faces = topology.faces.map { face -> face.mapNotNull { topology.pointByKey[it] } }
        .filter { it.size >= 3 }
    return faces.takeIf { it.isNotEmpty() }
}

// Všechny unikátní vrcholy tělesa (fallback pro konvexní obal).
internal fun segmentSolidFallbackVertices(state: MongeState, solid: SegmentSolid3D): List<Point3D> =
    solidVertices3D(state, solid)

// Konvexní obal 2D bodů – sdílený s exportem.
internal fun segmentSolidConvexHull(points: List<Offset>): List<Offset> = convexHull(points)

private fun solidTopology(state: MongeState, solid: SegmentSolid3D): SolidTopology? {
    val ids = solid.segmentIds3D.toHashSet()
    if (ids.isEmpty()) return null

    val pointByKey = LinkedHashMap<VertexKey, Point3D>()
    val adjacency = HashMap<VertexKey, LinkedHashSet<VertexKey>>()
    val pointById = HashMap<String, Point3D>()

    for (segment in state.segments3D) {
        if (segment.id !in ids) continue
        val ka = vertexKey(segment.start)
        val kb = vertexKey(segment.end)
        pointByKey.putIfAbsent(ka, segment.start)
        pointByKey.putIfAbsent(kb, segment.end)
        pointById.putIfAbsent(segment.start.id, segment.start)
        pointById.putIfAbsent(segment.end.id, segment.end)
        if (ka != kb) {
            adjacency.getOrPut(ka) { LinkedHashSet() }.add(kb)
            adjacency.getOrPut(kb) { LinkedHashSet() }.add(ka)
        }
    }
    if (pointByKey.size < 4) return null

    val faces = polygonFacesFromSolidPolygons(state, solid, pointByKey, pointById) ?: when (solid.type) {
        SegmentSolidType.JEHLAN -> pyramidFaces(solid, pointByKey, adjacency, pointById)
        SegmentSolidType.HRANOL -> prismFaces(solid, adjacency, pointById)
        SegmentSolidType.MNOHOSTEN -> emptyList()
    } ?: return null

    return SolidTopology(pointByKey, faces)
}

private fun polygonFacesFromSolidPolygons(
    state: MongeState,
    solid: SegmentSolid3D,
    pointByKey: Map<VertexKey, Point3D>,
    pointById: Map<String, Point3D>
): List<List<VertexKey>>? {
    if (solid.polygonIds.isEmpty()) return null

    val solidSegmentIds = solid.segmentIds3D.toSet()
    val polygonIds = solid.polygonIds.toSet()
    val sharedPointById = state.sharedPoints3D.associateBy { it.id }
    val faces = state.polygons3D
        .filter { polygon ->
            polygon.id in polygonIds &&
                polygon.segmentIds3D.isNotEmpty() &&
                polygon.segmentIds3D.all { it in solidSegmentIds }
        }
        .mapNotNull { polygon ->
            val keys = polygon.vertexPointIds.mapNotNull { pointId ->
                val point = pointById[pointId] ?: sharedPointById[pointId]
                point?.let { vertexKey(it) }?.takeIf { it in pointByKey }
            }
            keys.takeIf { it.size >= 3 && it.toSet().size == it.size }
        }

    return faces.takeIf { it.isNotEmpty() }
}

private fun pyramidFaces(
    solid: SegmentSolid3D,
    pointByKey: Map<VertexKey, Point3D>,
    adjacency: Map<VertexKey, Set<VertexKey>>,
    pointById: Map<String, Point3D>
): List<List<VertexKey>>? {
    val apexKey = solid.apexPointId?.let { pointById[it] }?.let { vertexKey(it) } ?: return null
    if (apexKey !in adjacency) return null

    val baseKeys = pointByKey.keys.filter { it != apexKey }
    if (baseKeys.size < 3) return null
    val baseSet = baseKeys.toHashSet()

    val baseAdjacency = HashMap<VertexKey, List<VertexKey>>()
    for (b in baseKeys) {
        baseAdjacency[b] = adjacency[b].orEmpty().filter { it != apexKey && it in baseSet }
    }
    val ordered = orderCycle(baseKeys, baseAdjacency) ?: return null

    val faces = mutableListOf<List<VertexKey>>()
    faces.add(ordered)
    for (i in ordered.indices) {
        val a = ordered[i]
        val b = ordered[(i + 1) % ordered.size]
        faces.add(listOf(apexKey, a, b))
    }
    return faces
}

private fun prismFaces(
    solid: SegmentSolid3D,
    adjacency: Map<VertexKey, Set<VertexKey>>,
    pointById: Map<String, Point3D>
): List<List<VertexKey>>? {
    val storedBaseKeys = solid.baseVertexPointIds.mapNotNull { id -> pointById[id]?.let { vertexKey(it) } }
    val baseKeys = orderedPrismBaseCycle(storedBaseKeys, adjacency) ?: inferPrismBaseCycle(adjacency) ?: return null
    val n = baseKeys.size
    val baseSet = baseKeys.toHashSet()

    // Pro každý vrchol podstavy najdeme jeho laterální (boční) protějšek v horní podstavě.
    val topKeys = ArrayList<VertexKey>(n)
    for (i in 0 until n) {
        val bi = baseKeys[i]
        val prev = baseKeys[(i - 1 + n) % n]
        val next = baseKeys[(i + 1) % n]
        val lateral = adjacency[bi].orEmpty()
            .firstOrNull { it != prev && it != next && it !in baseSet } ?: return null
        topKeys.add(lateral)
    }
    if (topKeys.toHashSet().size != n) return null

    val faces = mutableListOf<List<VertexKey>>()
    faces.add(baseKeys)                 // spodní podstava
    faces.add(topKeys.asReversed())     // horní podstava
    for (i in 0 until n) {              // boční stěny (čtyřúhelníky)
        val a = baseKeys[i]
        val b = baseKeys[(i + 1) % n]
        faces.add(listOf(a, b, topKeys[(i + 1) % n], topKeys[i]))
    }
    return faces
}

private fun orderedPrismBaseCycle(
    storedBaseKeys: List<VertexKey>,
    adjacency: Map<VertexKey, Set<VertexKey>>
): List<VertexKey>? {
    val n = storedBaseKeys.size
    if (n < 3 || storedBaseKeys.toHashSet().size != n) return null
    val baseSet = storedBaseKeys.toHashSet()
    val baseAdjacency = HashMap<VertexKey, List<VertexKey>>()
    for (b in storedBaseKeys) {
        baseAdjacency[b] = adjacency[b].orEmpty().filter { it in baseSet }
    }
    return orderCycle(storedBaseKeys, baseAdjacency)
}

private fun inferPrismBaseCycle(adjacency: Map<VertexKey, Set<VertexKey>>): List<VertexKey>? {
    val vertices = adjacency.keys.toList()
    if (vertices.size < 6 || vertices.size % 2 != 0) return null
    val n = vertices.size / 2
    if (adjacency.values.any { it.size != 3 }) return null

    val seenCycles = HashSet<Set<VertexKey>>()

    fun dfs(start: VertexKey, current: VertexKey, path: List<VertexKey>): List<VertexKey>? {
        if (path.size == n) {
            if (start !in adjacency[current].orEmpty()) return null
            if (!seenCycles.add(path.toSet())) return null
            val other = vertices.filter { it !in path.toSet() }
            val otherSet = other.toHashSet()
            val otherAdjacency = HashMap<VertexKey, List<VertexKey>>()
            for (v in other) otherAdjacency[v] = adjacency[v].orEmpty().filter { it in otherSet }
            if (orderCycle(other, otherAdjacency) == null) return null
            return path
        }

        for (next in adjacency[current].orEmpty()) {
            if (next == start || next in path) continue
            dfs(start, next, path + next)?.let { return it }
        }
        return null
    }

    for (v in vertices) {
        dfs(v, v, listOf(v))?.let { return it }
    }
    return null
}

// Seřadí vrcholy do jednoduchého cyklu podle hran (funguje i pro nekonvexní polygon).
private fun orderCycle(vertices: List<VertexKey>, adjacency: Map<VertexKey, List<VertexKey>>): List<VertexKey>? {
    if (vertices.size < 3) return null
    val vertexSet = vertices.toHashSet()
    val result = mutableListOf<VertexKey>()
    val visited = HashSet<VertexKey>()

    var current = vertices.first()
    var prev: VertexKey? = null
    while (current !in visited) {
        visited.add(current)
        result.add(current)
        val neighbours = adjacency[current].orEmpty().filter { it in vertexSet && it != prev }
        val next = neighbours.firstOrNull { it !in visited } ?: neighbours.firstOrNull()
        prev = current
        if (next == null) break
        current = next
    }
    return if (result.size == vertices.size) result else null
}

// ----- Pomocné -----

// Unikátní vrcholy tělesa z jeho 3D úseček (deduplikace podle kvantovaných souřadnic).
private fun solidVertices3D(state: MongeState, solid: SegmentSolid3D): List<Point3D> {
    val ids = solid.segmentIds3D.toHashSet()
    if (ids.isEmpty()) return emptyList()

    val byKey = LinkedHashMap<VertexKey, Point3D>()
    for (segment in state.segments3D) {
        if (segment.id !in ids) continue
        byKey.putIfAbsent(vertexKey(segment.start), segment.start)
        byKey.putIfAbsent(vertexKey(segment.end), segment.end)
    }
    return byKey.values.toList()
}

private fun vertexKey(p: Point3D): VertexKey =
    Triple(quantize(p.x), quantize(p.y), quantize(p.z))

private fun quantize(value: Float): Int =
    if (abs(value) < VERTEX_EPS) 0 else (value / VERTEX_EPS).toInt()

// Konvexní obal 2D bodů (Andrew's monotone chain) – fallback pro konvexní tělesa.
private fun convexHull(points: List<Offset>): List<Offset> {
    val pts = points
        .distinctBy { quantize(it.x) to quantize(it.y) }
        .sortedWith(compareBy({ it.x }, { it.y }))
    if (pts.size < 3) return pts

    fun cross(o: Offset, a: Offset, b: Offset): Float =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    val lower = mutableListOf<Offset>()
    for (p in pts) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
            lower.removeAt(lower.size - 1)
        }
        lower.add(p)
    }

    val upper = mutableListOf<Offset>()
    for (p in pts.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
            upper.removeAt(upper.size - 1)
        }
        upper.add(p)
    }

    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)
    return lower + upper
}
