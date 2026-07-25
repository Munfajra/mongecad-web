package monge.input.segments

import model.ProjectionMode
import model.classes.HelpSegmentPudorys
import model.classes.PlanePolygon2D
import model.classes.Point3DPudorys
import state.MongeState
import utils.allocIndex
import kotlin.math.abs

private const val PLANE_POINT_EPS = 1e-4f

/**
 * Jednotné místo pro dokončení pomocné půdorysné úsečky. V režimu PLANE po
 * přidání zkontroluje, zda nová hrana neuzavřela jednoduchý 2D mnohoúhelník.
 */
fun addHelpSegmentPudorysAndDetectPlanePolygon(
    state: MongeState,
    segment: HelpSegmentPudorys,
    allowedSegmentIds: Set<String>? = null
): PlanePolygon2D? {
    state.helpSegmentsPudorys.add(segment)
    return if (state.projectionMode == ProjectionMode.PLANE) {
        detectPlanePolygonAfterAdd(state, segment, allowedSegmentIds = allowedSegmentIds)
    } else {
        null
    }
}

fun detectPlanePolygonAfterAdd(
    state: MongeState,
    addedSegment: HelpSegmentPudorys,
    allowedSegmentIds: Set<String>? = null,
    updateConsInfo: Boolean = true
): PlanePolygon2D? {
    val graph = buildPlaneSegmentGraph(
        state.helpSegmentsPudorys.filter {
            allowedSegmentIds == null || it.id in allowedSegmentIds
        }
    )
    val addedEdgeIndex = graph.edges.indexOfFirst { it.segment.id == addedSegment.id }
    if (addedEdgeIndex < 0) return null

    val addedEdge = graph.edges[addedEdgeIndex]
    val path = findPathExcludingEdge(
        graph = graph,
        start = addedEdge.start,
        end = addedEdge.end,
        excludedEdgeIndex = addedEdgeIndex
    ) ?: return null
    if (path.edgeIndices.size < 2) return null

    val vertices = path.vertexIndices.map { graph.vertices[it] }
    if (!isSimplePlanePolygon(vertices)) return null

    val pathSegments = path.edgeIndices.map { graph.edges[it].segment }
    val segmentIds = pathSegments.map { it.id } + addedSegment.id
    val segmentSet = segmentIds.toSet()
    if (state.planePolygons2D.any { it.segmentIdsPudorys.toSet() == segmentSet }) return null

    val polygon = PlanePolygon2D(
        vertexPointIdsPudorys = vertices.map { it.point.id },
        segmentIdsPudorys = segmentIds,
        color = pathSegments.firstOrNull()?.color ?: addedSegment.color,
        width = pathSegments.firstOrNull()?.strokeWidth ?: addedSegment.strokeWidth,
        style = pathSegments.firstOrNull()?.lineStyle ?: addedSegment.lineStyle,
        creationIndex = allocIndex(state)
    )
    state.planePolygons2D.add(polygon)
    if (updateConsInfo) {
        state.consInfo.value = "Detekován 2D mnohoúhelník z navazujících úseček."
    }
    return polygon
}

fun removePlanePolygonsContainingSegments(state: MongeState, segmentIds: Set<String>) {
    if (segmentIds.isEmpty()) return
    val removedIds = state.planePolygons2D
        .filter { polygon -> polygon.segmentIdsPudorys.any { it in segmentIds } }
        .mapTo(mutableSetOf()) { it.id }
    if (removedIds.isEmpty()) return

    state.planePolygons2D.removeAll { it.id in removedIds }
    state.selectedPlanePolygons2D.removeAll { it.id in removedIds }
}

fun removePlanePolygonsContainingAidPoints(state: MongeState, pointIds: Set<String>) {
    if (pointIds.isEmpty()) return
    val removedIds = state.planePolygons2D
        .filter { polygon -> polygon.vertexAidPointIds.any { it in pointIds } }
        .mapTo(mutableSetOf()) { it.id }
    if (removedIds.isEmpty()) return

    state.planePolygons2D.removeAll { it.id in removedIds }
    state.selectedPlanePolygons2D.removeAll { it.id in removedIds }
}

fun deletePlanePolygon2D(state: MongeState, polygonId: String): Boolean {
    val polygon = state.planePolygons2D.firstOrNull { it.id == polygonId } ?: return false
    val segmentIds = polygon.segmentIdsPudorys.toSet()
    val pointIds = polygon.vertexPointIdsPudorys.toSet()
    val aidPointIds = polygon.vertexAidPointIds.toSet()

    // Sdílená hrana může patřit více detekovaným cyklům. Po jejím smazání proto
    // nesmí v seznamu zůstat žádný polygon s neplatnou vazbou.
    removePlanePolygonsContainingSegments(state, segmentIds)
    state.selectedSegmentsPudorys.removeAll { it.id in segmentIds }
    state.selectedPointsPudorys.removeAll { it.id in pointIds }
    state.selectedAidPointIds.removeAll { it in aidPointIds }
    state.helpSegmentsPudorys.removeAll { it.id in segmentIds }
    state.pointsPudorys.removeAll { it.id in pointIds }
    state.aidPointsLogical.removeAll { it.id in aidPointIds }
    return true
}

private data class PlaneVertex(
    val x: Float,
    val y: Float,
    val point: Point3DPudorys
)

private data class PlaneEdge(
    val segment: HelpSegmentPudorys,
    val start: Int,
    val end: Int
)

private data class PlaneSegmentGraph(
    val vertices: List<PlaneVertex>,
    val edges: List<PlaneEdge>,
    val adjacency: List<List<Int>>
)

private data class PlanePath(
    val vertexIndices: List<Int>,
    val edgeIndices: List<Int>
)

private fun buildPlaneSegmentGraph(segments: List<HelpSegmentPudorys>): PlaneSegmentGraph {
    val vertices = mutableListOf<PlaneVertex>()

    fun vertexIndex(point: Point3DPudorys): Int {
        val existing = vertices.indexOfFirst {
            abs(it.x - point.x) <= PLANE_POINT_EPS &&
                abs(it.y - point.y) <= PLANE_POINT_EPS
        }
        if (existing >= 0) return existing
        vertices += PlaneVertex(point.x, point.y, point)
        return vertices.lastIndex
    }

    val edges = segments.map { segment ->
        PlaneEdge(
            segment = segment,
            start = vertexIndex(segment.start),
            end = vertexIndex(segment.end)
        )
    }
    val adjacency = List(vertices.size) { mutableListOf<Int>() }
    edges.forEachIndexed { index, edge ->
        adjacency[edge.start] += index
        if (edge.end != edge.start) adjacency[edge.end] += index
    }
    return PlaneSegmentGraph(vertices, edges, adjacency)
}

private fun findPathExcludingEdge(
    graph: PlaneSegmentGraph,
    start: Int,
    end: Int,
    excludedEdgeIndex: Int
): PlanePath? {
    if (start == end) return null

    val parentVertex = IntArray(graph.vertices.size) { -1 }
    val parentEdge = IntArray(graph.vertices.size) { -1 }
    val visited = BooleanArray(graph.vertices.size)
    val queue = ArrayDeque<Int>()
    visited[start] = true
    queue.add(start)

    while (queue.isNotEmpty() && !visited[end]) {
        val vertex = queue.removeFirst()
        for (edgeIndex in graph.adjacency[vertex]) {
            if (edgeIndex == excludedEdgeIndex) continue
            val edge = graph.edges[edgeIndex]
            val next = if (edge.start == vertex) edge.end else edge.start
            if (visited[next]) continue
            visited[next] = true
            parentVertex[next] = vertex
            parentEdge[next] = edgeIndex
            queue.add(next)
        }
    }
    if (!visited[end]) return null

    val reversedVertices = mutableListOf(end)
    val reversedEdges = mutableListOf<Int>()
    var current = end
    while (current != start) {
        val edge = parentEdge[current]
        val parent = parentVertex[current]
        if (edge < 0 || parent < 0) return null
        reversedEdges += edge
        reversedVertices += parent
        current = parent
    }
    return PlanePath(
        vertexIndices = reversedVertices.asReversed(),
        edgeIndices = reversedEdges.asReversed()
    )
}

private fun isSimplePlanePolygon(vertices: List<PlaneVertex>): Boolean {
    if (vertices.size < 3) return false

    var twiceArea = 0f
    for (i in vertices.indices) {
        val a = vertices[i]
        val b = vertices[(i + 1) % vertices.size]
        twiceArea += a.x * b.y - b.x * a.y
    }
    if (abs(twiceArea) <= PLANE_POINT_EPS * PLANE_POINT_EPS) return false

    for (i in vertices.indices) {
        val a1 = vertices[i]
        val a2 = vertices[(i + 1) % vertices.size]
        for (j in i + 1 until vertices.size) {
            val adjacent = j == i ||
                j == (i + 1) % vertices.size ||
                i == (j + 1) % vertices.size
            if (adjacent) continue
            val b1 = vertices[j]
            val b2 = vertices[(j + 1) % vertices.size]
            if (segmentsIntersect(a1, a2, b1, b2)) return false
        }
    }
    return true
}

private fun segmentsIntersect(a: PlaneVertex, b: PlaneVertex, c: PlaneVertex, d: PlaneVertex): Boolean {
    fun cross(p: PlaneVertex, q: PlaneVertex, r: PlaneVertex): Float =
        (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)

    val abC = cross(a, b, c)
    val abD = cross(a, b, d)
    val cdA = cross(c, d, a)
    val cdB = cross(c, d, b)

    val properIntersection =
        ((abC > PLANE_POINT_EPS && abD < -PLANE_POINT_EPS) ||
            (abC < -PLANE_POINT_EPS && abD > PLANE_POINT_EPS)) &&
            ((cdA > PLANE_POINT_EPS && cdB < -PLANE_POINT_EPS) ||
                (cdA < -PLANE_POINT_EPS && cdB > PLANE_POINT_EPS))
    if (properIntersection) return true

    fun onSegment(p: PlaneVertex, q: PlaneVertex, r: PlaneVertex): Boolean =
        r.x >= minOf(p.x, q.x) - PLANE_POINT_EPS &&
            r.x <= maxOf(p.x, q.x) + PLANE_POINT_EPS &&
            r.y >= minOf(p.y, q.y) - PLANE_POINT_EPS &&
            r.y <= maxOf(p.y, q.y) + PLANE_POINT_EPS

    return (abs(abC) <= PLANE_POINT_EPS && onSegment(a, b, c)) ||
        (abs(abD) <= PLANE_POINT_EPS && onSegment(a, b, d)) ||
        (abs(cdA) <= PLANE_POINT_EPS && onSegment(c, d, a)) ||
        (abs(cdB) <= PLANE_POINT_EPS && onSegment(c, d, b))
}
