package monge.input.segments
import utils.getOrDefault
import utils.putIfAbsent
import utils.replaceAll

import model.Point3D
import model.classes.RegularPolygon3D
import model.classes.Segment3D
import model.classes.SegmentSolid3D
import model.classes.SegmentSolidType
import state.MongeState
import utils.allocIndex
import kotlin.math.abs

private const val POINT_EPS = 1e-4f

fun addSegment3DAndDetectSolids(state: MongeState, segment: Segment3D) {
    state.segments3D.add(segment)
    val solid = detectSegmentSolidAfterAdd(state, segment)
    if (solid == null) {
        detectSegmentPolygonAfterAdd(state, segment)
    }
}

fun removeSegmentSolidsContaining(state: MongeState, segmentIds: Set<String>) {
    if (segmentIds.isEmpty()) return
    val removedIds = state.segmentSolids3D
        .filter { solid -> solid.segmentIds3D.any { it in segmentIds } }
        .map { it.id }
        .toSet()
    state.segmentSolids3D.removeAll { it.id in removedIds }
    state.selectedSegmentSolids3D.removeAll { it.id in removedIds }

    val removedPolygonIds = state.polygons3D
        .filter { polygon -> polygon.segmentIds3D.any { it in segmentIds } }
        .map { it.id }
        .toSet()
    state.polygons3D.removeAll { it.id in removedPolygonIds }
    state.selectedPolygons.removeAll { it.id in removedPolygonIds }
    if (state.selectedPolygon?.id in removedPolygonIds) state.selectedPolygon = null
}

fun refreshSegmentSolidPolygonIds(state: MongeState) {
    if (state.segmentSolids3D.isEmpty() || state.polygons3D.isEmpty()) return
    state.segmentSolids3D.replaceAll { solid ->
        val segmentIds = solid.segmentIds3D.toSet()
        val polygonIds = state.polygons3D
            .filter { polygon -> polygon.segmentIds3D.isNotEmpty() && polygon.segmentIds3D.all { it in segmentIds } }
            .map { it.id }
        if (polygonIds == solid.polygonIds) solid else solid.copy(polygonIds = polygonIds)
    }
    state.selectedSegmentSolids3D.replaceAll { selected ->
        state.segmentSolids3D.firstOrNull { it.id == selected.id } ?: selected
    }
}

fun detectSegmentSolidAfterAdd(state: MongeState, addedSegment: Segment3D): SegmentSolid3D? {
    val component = connectedSegmentComponent(state.segments3D, addedSegment)
    if (component.size < 6) return null

    val candidate = findSegmentSolidCandidate(state, component, addedSegment.id) ?: return null
    val expandedSegmentIds = expandedSolidSegmentIds(component, candidate)
    val solid = candidate.solid.copy(segmentIds3D = expandedSegmentIds)
    if (state.segmentSolids3D.any { it.segmentIds3D.toSet() == solid.segmentIds3D.toSet() }) return null

    removeOverlappingSegmentSolids(state, solid.segmentIds3D.toSet())

    val color = solid.segmentIds3D
        .firstNotNullOfOrNull { id -> state.segments3D.firstOrNull { it.id == id }?.color }
        ?: solid.color
    val width = solid.segmentIds3D
        .firstNotNullOfOrNull { id -> state.segments3D.firstOrNull { it.id == id }?.strokeWidth }
        ?: solid.width

    val stored = solid.copy(
        color = color,
        width = width,
        creationIndex = allocIndex(state)
    )
    state.segmentSolids3D.add(stored)
    promoteSegmentEndpointVertices(state, stored)
    ensureSegmentSolidFacePolygons(state, candidate.copy(solid = stored))
    refreshSegmentSolidPolygonIds(state)
    applySegmentSolidHiddenLines(state, stored)
    state.consInfo.value = when (stored.type) {
        SegmentSolidType.HRANOL -> "Detekován hranol z navazujících 3D úseček."
        SegmentSolidType.JEHLAN -> "Detekován jehlan z navazujících 3D úseček."
        SegmentSolidType.MNOHOSTEN -> "Detekován mnohostěn z navazujících 3D úseček."
    }
    return stored
}

private fun expandedSolidSegmentIds(component: List<Segment3D>, candidate: SolidCandidate): List<String> {
    val solidEdgePairs = candidate.faceCycles
        .flatMap { face -> face.edges }
        .map { edge -> unorderedPair(edge.a, edge.b) }
        .toSet()

    val ids = LinkedHashSet<String>()
    ids += candidate.solid.segmentIds3D
    component.forEach { segment ->
        val pair = unorderedPair(segment.start.vertexKey(), segment.end.vertexKey())
        if (pair in solidEdgePairs) ids += segment.id
    }
    return ids.toList()
}

private fun removeOverlappingSegmentSolids(state: MongeState, newSegmentIds: Set<String>) {
    val removedSolids = state.segmentSolids3D
        .filter { solid -> solid.segmentIds3D.any { it in newSegmentIds } }
    val removedIds = removedSolids.map { it.id }.toSet()
    if (removedIds.isEmpty()) return

    val removedSolidSegmentSets = removedSolids.map { it.segmentIds3D.toSet() }
    val removedPolygonIds = state.polygons3D
        .filter { polygon ->
            polygon.id in removedSolids.flatMap { it.polygonIds } ||
                removedSolidSegmentSets.any { oldSegments ->
                    polygon.segmentIds3D.isNotEmpty() && polygon.segmentIds3D.all { it in oldSegments }
                }
        }
        .map { it.id }
        .toSet()

    state.segmentSolids3D.removeAll { it.id in removedIds }
    state.selectedSegmentSolids3D.removeAll { it.id in removedIds }
    state.polygons3D.removeAll { it.id in removedPolygonIds }
    state.selectedPolygons.removeAll { it.id in removedPolygonIds }
    if (state.selectedPolygon?.id in removedPolygonIds) state.selectedPolygon = null
}

private fun ensureSegmentSolidFacePolygons(state: MongeState, candidate: SolidCandidate) {
    val existingFaceSets = state.polygons3D.map { it.segmentIds3D.toSet() }.toHashSet()
    val existingFaceEdgePairSets = state.polygons3D
        .mapNotNull { polygon -> segmentIdsToEdgePairSet(state, polygon.segmentIds3D) }
        .toHashSet()

    for (face in candidate.faceCycles) {
        if (face.vertices.size < 3 || face.edges.size < 3) continue
        if (face.segmentSet in existingFaceSets) continue
        val faceEdgePairs = face.edges.map { edge -> unorderedPair(edge.a, edge.b) }.toSet()
        if (faceEdgePairs in existingFaceEdgePairSets) continue

        val vertexPointIds = face.vertices.mapNotNull { key -> candidate.pointsByKey[key]?.id }
        if (vertexPointIds.size != face.vertices.size) continue

        val segmentIds = face.segmentIds
        val color = segmentIds
            .firstNotNullOfOrNull { id -> state.segments3D.firstOrNull { it.id == id }?.color }
            ?: candidate.solid.color
        val width = segmentIds
            .firstNotNullOfOrNull { id -> state.segments3D.firstOrNull { it.id == id }?.strokeWidth }
            ?: candidate.solid.width
        val style = segmentIds
            .firstNotNullOfOrNull { id -> state.segments3D.firstOrNull { it.id == id }?.lineStyle }
            ?: state.segments3D.firstOrNull { it.id in segmentIds }?.lineStyle

        val polygon = RegularPolygon3D(
            name = "P",
            n = vertexPointIds.size,
            planeId = "",
            vertexPointIds = vertexPointIds,
            segmentIds3D = segmentIds,
            color = color,
            width = width,
            style = style ?: state.currentLineStyleSettings.style,
            vertexPointIdsPudorys = vertexPointIds.mapNotNull { pointId ->
                state.pointsPudorys.firstOrNull { it.parent?.id == pointId }?.id
            },
            vertexPointIdsNarys = vertexPointIds.mapNotNull { pointId ->
                state.pointsNarys.firstOrNull { it.parent?.id == pointId }?.id
            },
            segmentIdsPudorys = segmentIds.mapNotNull { segmentId ->
                state.segmentsPudorys.firstOrNull { it.parent?.id == segmentId || it.parentId == segmentId }?.id
            },
            segmentIdsNarys = segmentIds.mapNotNull { segmentId ->
                state.segmentsNarys.firstOrNull { it.parent?.id == segmentId || it.parentId == segmentId }?.id
            },
            segmentIdsAxo = segmentIds.mapNotNull { segmentId ->
                state.segmentsAxo.firstOrNull { it.parent?.id == segmentId || it.parentId == segmentId }?.id
            },
            creationIndex = allocIndex(state)
        )
        state.polygons3D.add(polygon)
        existingFaceSets += face.segmentSet
        existingFaceEdgePairSets += faceEdgePairs
    }
}

private fun segmentIdsToEdgePairSet(state: MongeState, segmentIds: List<String>): Set<Pair<VertexKey, VertexKey>>? {
    if (segmentIds.isEmpty()) return null
    val segmentById = state.segments3D.associateBy { it.id }
    val pairs = segmentIds.mapNotNull { id ->
        segmentById[id]?.let { segment ->
            unorderedPair(segment.start.vertexKey(), segment.end.vertexKey())
        }
    }
    return pairs.toSet().takeIf { pairs.size == segmentIds.size && it.isNotEmpty() }
}

fun detectSegmentPolygonAfterAdd(
    state: MongeState,
    addedSegment: Segment3D,
    allowedSegmentIds: Set<String>? = null,
    updateConsInfo: Boolean = true
): RegularPolygon3D? {
    val component = connectedSegmentComponent(state.segments3D, addedSegment)
        .filter { allowedSegmentIds == null || it.id in allowedSegmentIds }
    if (component.size < 3) return null

    val graph = buildSegmentGraph(component)
    val cycle = findCycleClosedByAddedSegment(graph, addedSegment.id) ?: return null
    if (!isPlanarSimplePolygon(cycle, graph)) return null

    val segmentIds = cycle.map { it.segmentId }
    val segmentSet = segmentIds.toSet()
    if (state.polygons3D.any { it.segmentIds3D.toSet() == segmentSet }) return null

    val vertexKeys = cycle.map { it.a }
    val vertexPointIds = vertexKeys.map { graph.pointsByKey.getValue(it).id }
    val color = segmentIds
        .firstNotNullOfOrNull { id -> state.segments3D.firstOrNull { it.id == id }?.color }
        ?: addedSegment.color
    val width = segmentIds
        .firstNotNullOfOrNull { id -> state.segments3D.firstOrNull { it.id == id }?.strokeWidth }
        ?: addedSegment.strokeWidth
    val style = segmentIds
        .firstNotNullOfOrNull { id -> state.segments3D.firstOrNull { it.id == id }?.lineStyle }
        ?: addedSegment.lineStyle

    val polygon = RegularPolygon3D(
        name = "P",
        n = vertexPointIds.size,
        planeId = "",
        vertexPointIds = vertexPointIds,
        segmentIds3D = segmentIds,
        color = color,
        width = width,
        style = style,
        vertexPointIdsPudorys = vertexPointIds.mapNotNull { pointId ->
            state.pointsPudorys.firstOrNull { it.parent?.id == pointId }?.id
        },
        vertexPointIdsNarys = vertexPointIds.mapNotNull { pointId ->
            state.pointsNarys.firstOrNull { it.parent?.id == pointId }?.id
        },
        segmentIdsPudorys = segmentIds.mapNotNull { segmentId ->
            state.segmentsPudorys.firstOrNull { it.parent?.id == segmentId || it.parentId == segmentId }?.id
        },
        segmentIdsNarys = segmentIds.mapNotNull { segmentId ->
            state.segmentsNarys.firstOrNull { it.parent?.id == segmentId || it.parentId == segmentId }?.id
        },
        segmentIdsAxo = segmentIds.mapNotNull { segmentId ->
            state.segmentsAxo.firstOrNull { it.parent?.id == segmentId || it.parentId == segmentId }?.id
        },
        creationIndex = allocIndex(state)
    )

    state.polygons3D.add(polygon)
    promoteSegmentEndpointVertices(state, polygon)
    refreshSegmentSolidPolygonIds(state)
    if (updateConsInfo) {
        state.consInfo.value = "Detekován mnohoúhelník z navazujících 3D úseček."
    }
    return polygon
}

/**
 * "Sdruží" vrcholy tělesa do jednotných bodů: zruší u průmětů vrcholů příznak
 * [Point3DPudorys.isSegmentEndpoint] (jinak se jim nevykreslují labely) a případně
 * chybějící `parent` naváže na odpovídající 3D vrchol, aby šel vrchol pojmenovat.
 *
 * Prochází se strukturálně přes 2D průměty hran tělesa (ne jen přes `parent?.id`),
 * takže se podchytí i ručně kreslené hrany, jejichž koncové body nemají parent nebo
 * míří na koincidenční (duplicitní) 3D bod.
 */
/**
 * Znovu "sdruží" vrcholy všech těles a polygonů – volá se po undo/redo i po načtení
 * souboru, aby i dříve uložené scény měly vrcholy jako jednotné, pojmenovatelné body
 * (bez příznaku segmentendpoint, který blokuje labely).
 */
fun promoteSolidAndPolygonVertices(state: MongeState) {
    state.segmentSolids3D.forEach { promoteSegmentEndpointVertices(state, it) }
    state.polygons3D.forEach { promoteSegmentEndpointVertices(state, it) }
}

private fun promoteSegmentEndpointVertices(state: MongeState, solid: SegmentSolid3D) =
    promoteSegmentEndpointVertices(state, solid.segmentIds3D, solid.vertexPointIds)

private fun promoteSegmentEndpointVertices(state: MongeState, polygon: RegularPolygon3D) =
    promoteSegmentEndpointVertices(state, polygon.segmentIds3D, polygon.vertexPointIds)

private fun promoteSegmentEndpointVertices(
    state: MongeState,
    segmentIds3D: Collection<String>,
    vertexPointIds: Collection<String>,
) {
    val segById = state.segments3D.associateBy { it.id }
    val segments = segmentIds3D.mapNotNull { segById[it] }

    fun matchEnd(ends: List<Point3D>, px: Float, py: Float, coords: (Point3D) -> Pair<Float, Float>): Point3D? =
        ends.firstOrNull { v ->
            val (vx, vy) = coords(v)
            abs(vx - px) < POINT_EPS * 5f && abs(vy - py) < POINT_EPS * 5f
        }

    segments.forEach { seg ->
        val ends = listOf(seg.start, seg.end)

        state.segmentsPudorys.filter { it.parentId == seg.id || it.parent?.id == seg.id }.forEach { s2 ->
            listOf(s2.start, s2.end).forEach { p ->
                p.isSegmentEndpoint = false
                if (p.parent == null) p.parent = matchEnd(ends, p.x, p.y) { it.x to it.y }
            }
        }
        state.segmentsNarys.filter { it.parentId == seg.id || it.parent?.id == seg.id }.forEach { s2 ->
            listOf(s2.start, s2.end).forEach { p ->
                p.isSegmentEndpoint = false
                if (p.parent == null) p.parent = matchEnd(ends, p.x, p.z) { it.x to it.z }
            }
        }
        state.segmentsBokorys.filter { it.parentId == seg.id || it.parent?.id == seg.id }.forEach { s2 ->
            listOf(s2.start, s2.end).forEach { p ->
                p.isSegmentEndpoint = false
                if (p.parent == null) p.parent = matchEnd(ends, p.y, p.z) { it.y to it.z }
            }
        }
        state.segmentsAxo.filter { it.parentId == seg.id || it.parent?.id == seg.id }.forEach { s2 ->
            listOf(s2.start, s2.end).forEach { p -> p.isSegmentEndpoint = false }
        }
    }

    // Fallback: doraz i případné průměty svázané přes parent na 3D vrchol,
    // které nejsou koncovým bodem žádného 2D průmětu hrany.
    val vertexIds = vertexPointIds.toHashSet()
    if (vertexIds.isNotEmpty()) {
        state.pointsPudorys.filter { it.parent?.id in vertexIds }.forEach { it.isSegmentEndpoint = false }
        state.pointsNarys.filter { it.parent?.id in vertexIds }.forEach { it.isSegmentEndpoint = false }
        state.pointsBokorys.filter { it.parent?.id in vertexIds }.forEach { it.isSegmentEndpoint = false }
        state.pointsAxo.filter { it.parent?.id in vertexIds }.forEach { it.isSegmentEndpoint = false }
    }
}

/**
 * Speciální tvar [SegmentSolid3D] rozpoznaný z geometrie:
 *  - [KRYCHLE]   – kolmý hranol s 8 vrcholy, všemi hranami stejné délky a kolmými hranami v každém vrcholu,
 *  - [CTYRSTEN]  – pravidelný čtyřstěn (jehlan se 4 vrcholy a 6 stejně dlouhými hranami).
 */
enum class SegmentSolidShape { KRYCHLE, CTYRSTEN }

/**
 * Vrátí speciální tvar tělesa (krychle / pravidelný čtyřstěn), nebo `null`,
 * pokud jde o obecný hranol/jehlan. Slouží jen pro popisek v UI.
 */
fun segmentSolidSpecialShape(state: MongeState, solid: SegmentSolid3D): SegmentSolidShape? {
    val edges = solid.segmentIds3D
        .mapNotNull { id -> state.segments3D.firstOrNull { it.id == id } }
        .map { it.start to it.end }

    return when (solid.type) {
        SegmentSolidType.HRANOL ->
            if (isCube(solid, edges)) SegmentSolidShape.KRYCHLE else null
        SegmentSolidType.JEHLAN ->
            if (isRegularTetrahedron(solid, edges)) SegmentSolidShape.CTYRSTEN else null
        SegmentSolidType.MNOHOSTEN -> null
    }
}

private const val SHAPE_LEN_TOL = 0.02f   // relativní tolerance délek hran
private const val SHAPE_PERP_TOL = 0.03f  // tolerance kolmosti (|cos úhlu|)

private fun edgeLen(a: Point3D, b: Point3D): Float {
    val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
    return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
}

private fun allEdgesEqual(edges: List<Pair<Point3D, Point3D>>): Boolean {
    if (edges.isEmpty()) return false
    val lengths = edges.map { edgeLen(it.first, it.second) }
    val ref = lengths.first()
    if (ref < 1e-4f) return false
    return lengths.all { abs(it - ref) <= SHAPE_LEN_TOL * ref }
}

private fun isCube(solid: SegmentSolid3D, edges: List<Pair<Point3D, Point3D>>): Boolean {
    if (solid.vertexPointIds.size != 8 || edges.size != 12) return false
    if (!allEdgesEqual(edges)) return false

    // V každém vrcholu musí být 3 incidentní hrany navzájem kolmé.
    val incident = mutableMapOf<String, MutableList<Point3D>>()
    edges.forEach { (a, b) ->
        incident.getOrPut(a.id) { mutableListOf() }.add(b)
        incident.getOrPut(b.id) { mutableListOf() }.add(a)
    }
    val vertices = solid.vertexPointIds.mapNotNull { id ->
        edges.firstNotNullOfOrNull { (a, b) -> if (a.id == id) a else if (b.id == id) b else null }
    }
    if (vertices.size != 8) return false

    for (v in vertices) {
        val neighbors = incident[v.id] ?: return false
        if (neighbors.size != 3) return false
        val dirs = neighbors.map { Triple(it.x - v.x, it.y - v.y, it.z - v.z) }
        for (i in dirs.indices) for (j in i + 1 until dirs.size) {
            if (!perpendicular(dirs[i], dirs[j])) return false
        }
    }
    return true
}

private fun perpendicular(u: Triple<Float, Float, Float>, w: Triple<Float, Float, Float>): Boolean {
    val dot = u.first * w.first + u.second * w.second + u.third * w.third
    val lu = kotlin.math.sqrt(u.first * u.first + u.second * u.second + u.third * u.third)
    val lw = kotlin.math.sqrt(w.first * w.first + w.second * w.second + w.third * w.third)
    if (lu < 1e-5f || lw < 1e-5f) return false
    return abs(dot / (lu * lw)) <= SHAPE_PERP_TOL
}

private fun isRegularTetrahedron(solid: SegmentSolid3D, edges: List<Pair<Point3D, Point3D>>): Boolean {
    if (solid.vertexPointIds.size != 4 || edges.size != 6) return false
    return allEdgesEqual(edges)
}

private data class VertexKey(val x: Int, val y: Int, val z: Int)

private data class GraphEdge(
    val segmentId: String,
    val a: VertexKey,
    val b: VertexKey
)

private data class SegmentGraph(
    val pointsByKey: Map<VertexKey, Point3D>,
    val edges: List<GraphEdge>,
    val adjacency: Map<VertexKey, List<VertexKey>>
) {
    fun isSimpleConnectedClosed(): Boolean {
        if (edges.isEmpty() || pointsByKey.size < 4) return false
        if (edges.any { it.a == it.b }) return false
        if (edges.map { unorderedPair(it.a, it.b) }.toSet().size != edges.size) return false
        if (adjacency.values.any { it.size < 2 }) return false

        val start = pointsByKey.keys.first()
        val seen = mutableSetOf<VertexKey>()
        val stack = ArrayDeque<VertexKey>()
        stack.add(start)
        while (stack.isNotEmpty()) {
            val v = stack.removeLast()
            if (!seen.add(v)) continue
            adjacency[v].orEmpty().forEach { if (it !in seen) stack.add(it) }
        }
        return seen.size == pointsByKey.size
    }

    fun isSingleCycle(): Boolean {
        if (edges.size < 3 || pointsByKey.size != edges.size) return false
        if (edges.any { it.a == it.b }) return false
        if (edges.map { unorderedPair(it.a, it.b) }.toSet().size != edges.size) return false
        if (adjacency.values.any { it.size != 2 }) return false
        return isSimpleConnectedClosed()
    }
}

private fun connectedSegmentComponent(allSegments: List<Segment3D>, addedSegment: Segment3D): List<Segment3D> {
    val graph = buildSegmentGraph(allSegments)
    val addedEdge = graph.edges.firstOrNull { it.segmentId == addedSegment.id } ?: return listOf(addedSegment)
    val keysInComponent = mutableSetOf<VertexKey>()
    val stack = ArrayDeque<VertexKey>()
    stack.add(addedEdge.a)
    stack.add(addedEdge.b)

    while (stack.isNotEmpty()) {
        val key = stack.removeLast()
        if (!keysInComponent.add(key)) continue
        graph.adjacency[key].orEmpty().forEach { if (it !in keysInComponent) stack.add(it) }
    }

    return allSegments.filter { segment ->
        val a = segment.start.vertexKey()
        val b = segment.end.vertexKey()
        a in keysInComponent && b in keysInComponent
    }
}

private fun buildSegmentGraph(segments: List<Segment3D>): SegmentGraph {
    val pointsByKey = linkedMapOf<VertexKey, Point3D>()
    val edges = segments.map { segment ->
        val a = segment.start.vertexKey()
        val b = segment.end.vertexKey()
        pointsByKey.putIfAbsent(a, segment.start)
        pointsByKey.putIfAbsent(b, segment.end)
        GraphEdge(segment.id, a, b)
    }
    val adjacency = mutableMapOf<VertexKey, MutableList<VertexKey>>()
    edges.forEach { edge ->
        adjacency.getOrPut(edge.a) { mutableListOf() }.add(edge.b)
        adjacency.getOrPut(edge.b) { mutableListOf() }.add(edge.a)
    }
    return SegmentGraph(pointsByKey, edges, adjacency)
}

private fun findSegmentSolidCandidate(
    state: MongeState,
    component: List<Segment3D>,
    addedSegmentId: String
): SolidCandidate? {
    val graph = buildSegmentGraph(component)
    val candidates = mutableListOf<SolidCandidate>()

    candidates += findPolyhedronSubgraphs(graph, addedSegmentId)
    candidates += findPrismSubgraphs(graph, addedSegmentId)
    candidates += findPyramidSubgraphs(graph, addedSegmentId)

    return candidates
        .maxWithOrNull(
            compareBy<SolidCandidate> { facePolygonCoverageScore(state, it) }
                .thenBy { solidPolygonCoverageScore(state, it.solid) }
                .thenBy { it.solid.segmentIds3D.size }
                .thenBy { segmentSolidTypeRank(it.solid.type) }
                .thenBy { it.solid.vertexPointIds.size }
        )
}

private fun segmentSolidTypeRank(type: SegmentSolidType): Int =
    when (type) {
        SegmentSolidType.HRANOL -> 2
        SegmentSolidType.JEHLAN -> 1
        SegmentSolidType.MNOHOSTEN -> 0
    }

private fun solidPolygonCoverageScore(state: MongeState, solid: SegmentSolid3D): Int {
    val segmentIds = solid.segmentIds3D.toSet()
    val assignedPolygonIds = state.segmentSolids3D.flatMap { it.polygonIds }.toSet()
    val existingSolidSegmentSets = state.segmentSolids3D.map { it.segmentIds3D.toSet() }
    return state.polygons3D.sumOf { polygon ->
        if (polygon.id in assignedPolygonIds) return@sumOf 0
        val polygonSegments = polygon.segmentIds3D.toSet()
        if (existingSolidSegmentSets.any { oldSegments -> polygonSegments.isNotEmpty() && polygonSegments.all { it in oldSegments } }) {
            return@sumOf 0
        }
        if (polygon.segmentIds3D.isNotEmpty() && polygon.segmentIds3D.all { it in segmentIds }) {
            polygon.segmentIds3D.size
        } else {
            0
        }
    }
}

private data class CycleCandidate(
    val vertices: List<VertexKey>,
    val edges: List<GraphEdge>
) {
    val segmentIds: List<String> get() = edges.map { it.segmentId }
    val segmentSet: Set<String> get() = segmentIds.toSet()
    val edgePairs: Set<Pair<VertexKey, VertexKey>> get() = edges.map { unorderedPair(it.a, it.b) }.toSet()
}

private data class SolidCandidate(
    val solid: SegmentSolid3D,
    val faceCycles: List<CycleCandidate>,
    val pointsByKey: Map<VertexKey, Point3D>
) {
    val faceSegmentSets: List<Set<String>> get() = faceCycles.map { it.segmentSet }
}

private fun facePolygonCoverageScore(state: MongeState, candidate: SolidCandidate): Int {
    if (candidate.faceSegmentSets.isEmpty()) return 0
    val faceSets = candidate.faceSegmentSets.toSet()
    val assignedPolygonIds = state.segmentSolids3D.flatMap { it.polygonIds }.toSet()
    val existingSolidSegmentSets = state.segmentSolids3D.map { it.segmentIds3D.toSet() }
    return state.polygons3D.sumOf { polygon ->
        if (polygon.id in assignedPolygonIds) return@sumOf 0
        val polygonSegments = polygon.segmentIds3D.toSet()
        if (existingSolidSegmentSets.any { oldSegments -> polygonSegments.isNotEmpty() && polygonSegments.all { it in oldSegments } }) {
            return@sumOf 0
        }
        if (polygonSegments in faceSets) polygon.segmentIds3D.size * 10 else 0
    }
}

private fun findPolyhedronSubgraphs(graph: SegmentGraph, addedSegmentId: String): List<SolidCandidate> {
    val maxFaceSize = graph.pointsByKey.size.coerceAtMost(8)
    val faces = (3..maxFaceSize)
        .flatMap { simpleCycleCandidates(graph, it) }
        .filter { isPlanarSimpleVertexFace(it.vertices, graph) }
        .distinctBy { it.edgePairs }

    if (faces.size < 4 || faces.size > 28) return emptyList()

    val candidates = mutableListOf<SolidCandidate>()
    val selected = ArrayList<CycleCandidate>()
    val edgeCounts = LinkedHashMap<Pair<VertexKey, VertexKey>, Int>()
    val segmentIds = LinkedHashSet<String>()

    fun addFace(face: CycleCandidate) {
        selected += face
        face.edgePairs.forEach { edgeCounts[it] = edgeCounts.getOrDefault(it, 0) + 1 }
        segmentIds += face.segmentIds
    }

    fun removeFace(face: CycleCandidate) {
        selected.removeAt(selected.lastIndex)
        face.edgePairs.forEach { edge ->
            val next = edgeCounts.getValue(edge) - 1
            if (next == 0) edgeCounts.remove(edge) else edgeCounts[edge] = next
        }
        segmentIds.clear()
        selected.forEach { segmentIds += it.segmentIds }
    }

    fun canAdd(face: CycleCandidate): Boolean =
        face.edgePairs.all { edgeCounts.getOrDefault(it, 0) < 2 }

    fun maybeAddCandidate() {
        if (selected.size < 4) return
        if (addedSegmentId !in segmentIds) return
        if (edgeCounts.values.any { it != 2 }) return
        if (!selectedFacesConnected(selected)) return

        val vertexKeys = selected.flatMap { it.vertices }.distinct()
        if (vertexKeys.size < 4) return

        candidates += SolidCandidate(
            solid = SegmentSolid3D(
                type = SegmentSolidType.MNOHOSTEN,
                segmentIds3D = segmentIds.toList(),
                vertexPointIds = vertexKeys.map { graph.pointsByKey.getValue(it).id },
                polygonIds = emptyList()
            ),
            faceCycles = selected.toList(),
            pointsByKey = graph.pointsByKey
        )
    }

    fun dfs(startIndex: Int) {
        maybeAddCandidate()
        if (selected.size >= 12) return

        for (i in startIndex until faces.size) {
            val face = faces[i]
            if (!canAdd(face)) continue
            addFace(face)
            dfs(i + 1)
            removeFace(face)
        }
    }

    dfs(0)
    return candidates
        .distinctBy { it.faceSegmentSets.toSet() }
}

private fun selectedFacesConnected(faces: List<CycleCandidate>): Boolean {
    if (faces.isEmpty()) return false
    val seen = mutableSetOf<Int>()
    val stack = ArrayDeque<Int>()
    stack.add(0)
    while (stack.isNotEmpty()) {
        val index = stack.removeLast()
        if (!seen.add(index)) continue
        val edges = faces[index].edgePairs.toSet()
        faces.forEachIndexed { otherIndex, other ->
            if (otherIndex !in seen && other.edgePairs.any { it in edges }) {
                stack.add(otherIndex)
            }
        }
    }
    return seen.size == faces.size
}

private fun findPrismSubgraphs(graph: SegmentGraph, addedSegmentId: String): List<SolidCandidate> {
    val candidates = mutableListOf<SolidCandidate>()
    val vertexCount = graph.pointsByKey.size
    if (vertexCount < 6) return emptyList()

    for (baseCount in (vertexCount / 2) downTo 3) {
        val cycles = simpleCycleCandidates(graph, baseCount)
        for (i in cycles.indices) {
            val base = cycles[i]
            val baseSet = base.vertices.toSet()
            for (j in i + 1 until cycles.size) {
                val other = cycles[j]
                val otherSet = other.vertices.toSet()
                if (baseSet.any { it in otherSet }) continue

                val matchings = findLateralMatchings(graph, base.vertices, other.vertices)
                for (matching in matchings) {
                    if (!isValidPrismGeometry(graph, base.vertices, other.vertices, matching)) continue

                    val selected = base.edges + other.edges + matching
                    val segmentIds = selected.map { it.segmentId }
                    if (addedSegmentId !in segmentIds) continue

                    val solid = classifyAsPrism(buildSegmentGraphFromEdges(graph, selected)) ?: continue
                    candidates += SolidCandidate(
                        solid = solid,
                        faceCycles = prismFaceCycles(graph, base, other, matching),
                        pointsByKey = graph.pointsByKey
                    )
                }
            }
        }
    }

    return candidates
}

private fun prismFaceCycles(
    graph: SegmentGraph,
    base: CycleCandidate,
    other: CycleCandidate,
    matching: List<GraphEdge>
): List<CycleCandidate> {
    val oppositeByBase = matching.associate { edge -> edge.a to edge.b }
    val faces = mutableListOf<CycleCandidate>()
    faces += base
    faces += other

    for (i in base.vertices.indices) {
        val a = base.vertices[i]
        val b = base.vertices[(i + 1) % base.vertices.size]
        val c = oppositeByBase[b] ?: continue
        val d = oppositeByBase[a] ?: continue
        val baseEdge = graph.edgeBetween(a, b) ?: continue
        val topEdge = graph.edgeBetween(c, d) ?: continue
        val lateralA = graph.edgeBetween(a, d) ?: continue
        val lateralB = graph.edgeBetween(b, c) ?: continue
        faces += CycleCandidate(
            vertices = listOf(a, b, c, d),
            edges = listOf(baseEdge, lateralB, topEdge, lateralA)
        )
    }

    return faces
}

private fun isValidPrismGeometry(
    graph: SegmentGraph,
    base: List<VertexKey>,
    other: List<VertexKey>,
    matching: List<GraphEdge>
): Boolean {
    if (!isPlanarSimpleVertexFace(base, graph)) return false
    if (!isPlanarSimpleVertexFace(other, graph)) return false

    val oppositeByBase = matching.associate { edge -> edge.a to edge.b }
    if (base.any { it !in oppositeByBase }) return false
    if (!hasPrismTranslationGeometry(graph, base, other, oppositeByBase)) return false

    for (i in base.indices) {
        val a = base[i]
        val b = base[(i + 1) % base.size]
        val c = oppositeByBase[b] ?: return false
        val d = oppositeByBase[a] ?: return false
        if (!isPlanarSimpleVertexFace(listOf(a, b, c, d), graph)) return false
    }

    return true
}

private fun hasPrismTranslationGeometry(
    graph: SegmentGraph,
    base: List<VertexKey>,
    other: List<VertexKey>,
    oppositeByBase: Map<VertexKey, VertexKey>
): Boolean {
    val basePoints = base.map { graph.pointsByKey.getValue(it) }
    val otherPoints = other.map { graph.pointsByKey.getValue(it) }
    val baseNormal = polygonNormal(basePoints)
    val otherNormal = polygonNormal(otherPoints)
    if (!parallelNormals(baseNormal, otherNormal)) return false

    val lateralVectors = base.map { key ->
        val opposite = oppositeByBase[key] ?: return false
        vectorBetween(graph.pointsByKey.getValue(key), graph.pointsByKey.getValue(opposite))
    }
    val ref = lateralVectors.firstOrNull() ?: return false
    val scale = (basePoints + otherPoints)
        .maxOfOrNull { edgeLen(basePoints.first(), it) }
        ?.coerceAtLeast(ref.length())
        ?.coerceAtLeast(1f)
        ?: 1f
    val tol = 1e-3f * scale + POINT_EPS * 20f
    return lateralVectors.all { (it - ref).length() <= tol }
}

private fun parallelNormals(a: Vec3f, b: Vec3f): Boolean {
    val denom = a.length() * b.length()
    if (denom < POINT_EPS) return false
    return cross(a, b).length() / denom <= 0.02f
}

private fun vectorBetween(a: Point3D, b: Point3D): Vec3f =
    Vec3f(b.x - a.x, b.y - a.y, b.z - a.z)

private fun findPyramidSubgraphs(graph: SegmentGraph, addedSegmentId: String): List<SolidCandidate> {
    val candidates = mutableListOf<SolidCandidate>()
    val vertices = graph.pointsByKey.keys.toList()
    if (vertices.size < 4) return emptyList()

    for (baseCount in (vertices.size - 1) downTo 3) {
        val cycles = simpleCycleCandidates(graph, baseCount)
        for (cycle in cycles) {
            val baseSet = cycle.vertices.toSet()
            val apexCandidates = vertices.filter { it !in baseSet }
            for (apex in apexCandidates) {
                val sideEdges = cycle.vertices.map { baseVertex ->
                    graph.edgeBetween(apex, baseVertex) ?: return@map null
                }
                if (sideEdges.any { it == null }) continue

                val selected = cycle.edges + sideEdges.filterNotNull()
                val segmentIds = selected.map { it.segmentId }
                if (addedSegmentId !in segmentIds) continue

                val solid = classifyAsPyramid(buildSegmentGraphFromEdges(graph, selected)) ?: continue
                candidates += SolidCandidate(
                    solid = solid,
                    faceCycles = pyramidFaceCycles(graph, cycle, apex),
                    pointsByKey = graph.pointsByKey
                )
            }
        }
    }

    return candidates
}

private fun pyramidFaceCycles(
    graph: SegmentGraph,
    base: CycleCandidate,
    apex: VertexKey
): List<CycleCandidate> {
    val faces = mutableListOf<CycleCandidate>()
    faces += base
    for (i in base.vertices.indices) {
        val a = base.vertices[i]
        val b = base.vertices[(i + 1) % base.vertices.size]
        val baseEdge = graph.edgeBetween(a, b) ?: continue
        val sideA = graph.edgeBetween(apex, a) ?: continue
        val sideB = graph.edgeBetween(apex, b) ?: continue
        faces += CycleCandidate(
            vertices = listOf(apex, a, b),
            edges = listOf(sideA, baseEdge, sideB)
        )
    }
    return faces
}

private fun simpleCycleCandidates(graph: SegmentGraph, length: Int): List<CycleCandidate> {
    return simpleCycles(graph, length).mapNotNull { vertices ->
        val edges = cycleEdges(graph, vertices) ?: return@mapNotNull null
        CycleCandidate(vertices, edges)
    }
}

private fun cycleEdges(graph: SegmentGraph, vertices: List<VertexKey>): List<GraphEdge>? {
    if (vertices.size < 3) return null
    return vertices.indices.map { i ->
        graph.edgeBetween(vertices[i], vertices[(i + 1) % vertices.size]) ?: return null
    }
}

private fun findLateralMatchings(
    graph: SegmentGraph,
    base: List<VertexKey>,
    other: List<VertexKey>
): List<List<GraphEdge>> {
    val otherSet = other.toSet()
    val usedOther = mutableSetOf<VertexKey>()
    val result = ArrayList<GraphEdge>(base.size)
    val all = mutableListOf<List<GraphEdge>>()

    fun dfs(index: Int) {
        if (index == base.size) {
            all += result.toList()
            return
        }
        val from = base[index]
        val candidates = graph.edges.filter { edge ->
            (edge.a == from && edge.b in otherSet && edge.b !in usedOther) ||
                (edge.b == from && edge.a in otherSet && edge.a !in usedOther)
        }

        for (edge in candidates) {
            val to = edge.other(from) ?: continue
            usedOther += to
            result += if (edge.a == from) edge else GraphEdge(edge.segmentId, from, to)
            dfs(index + 1)
            result.removeAt(result.lastIndex)
            usedOther -= to
        }
    }

    dfs(0)
    return all
}

private fun buildSegmentGraphFromEdges(source: SegmentGraph, selectedEdges: List<GraphEdge>): SegmentGraph {
    val pointsByKey = linkedMapOf<VertexKey, Point3D>()
    selectedEdges.forEach { edge ->
        pointsByKey.putIfAbsent(edge.a, source.pointsByKey.getValue(edge.a))
        pointsByKey.putIfAbsent(edge.b, source.pointsByKey.getValue(edge.b))
    }
    val adjacency = mutableMapOf<VertexKey, MutableList<VertexKey>>()
    selectedEdges.forEach { edge ->
        adjacency.getOrPut(edge.a) { mutableListOf() }.add(edge.b)
        adjacency.getOrPut(edge.b) { mutableListOf() }.add(edge.a)
    }
    return SegmentGraph(pointsByKey, selectedEdges, adjacency)
}

private fun SegmentGraph.edgeBetween(a: VertexKey, b: VertexKey): GraphEdge? =
    edges.firstOrNull { (it.a == a && it.b == b) || (it.a == b && it.b == a) }?.let { edge ->
        if (edge.a == a) edge else GraphEdge(edge.segmentId, a, b)
    }

private fun classifyAsPyramid(graph: SegmentGraph): SegmentSolid3D? {
    val vertexCount = graph.pointsByKey.size
    val baseCount = vertexCount - 1
    if (baseCount < 3 || graph.edges.size != 2 * baseCount) return null

    val apexCandidates = graph.adjacency.entries.filter { it.value.size == baseCount }.map { it.key }
    for (apex in apexCandidates) {
        val baseVertices = graph.pointsByKey.keys.filter { it != apex }
        if (baseVertices.any { graph.adjacency[it].orEmpty().size != 3 }) continue

        val baseEdges = graph.edges.filter { it.a != apex && it.b != apex }
        if (baseEdges.size != baseCount) continue
        if (!formsSingleCycle(baseVertices, baseEdges)) continue
        if (baseVertices.any { apex !in graph.adjacency[it].orEmpty() }) continue

        return SegmentSolid3D(
            type = SegmentSolidType.JEHLAN,
            segmentIds3D = graph.edges.map { it.segmentId },
            vertexPointIds = graph.pointsByKey.values.map { it.id },
            apexPointId = graph.pointsByKey.getValue(apex).id,
            baseVertexPointIds = baseVertices.map { graph.pointsByKey.getValue(it).id }
        )
    }

    return null
}

private fun classifyAsPrism(graph: SegmentGraph): SegmentSolid3D? {
    val vertexCount = graph.pointsByKey.size
    if (vertexCount < 6 || vertexCount % 2 != 0) return null
    val baseCount = vertexCount / 2
    if (graph.edges.size != 3 * baseCount) return null
    if (graph.adjacency.values.any { it.size != 3 }) return null

    val cycles = simpleCycles(graph, baseCount)
    for (cycle in cycles) {
        val cycleSet = cycle.toSet()
        val other = graph.pointsByKey.keys.filter { it !in cycleSet }
        if (other.size != baseCount) continue

        val cycleEdges = graph.edges.filter { it.a in cycleSet && it.b in cycleSet }
        val otherEdges = graph.edges.filter { it.a in other && it.b in other }
        val lateralEdges = graph.edges.filter { (it.a in cycleSet) != (it.b in cycleSet) }

        if (cycleEdges.size != baseCount) continue
        if (otherEdges.size != baseCount) continue
        if (lateralEdges.size != baseCount) continue
        if (!formsSingleCycle(other, otherEdges)) continue
        if (cycle.any { v -> lateralEdges.count { it.a == v || it.b == v } != 1 }) continue
        if (other.any { v -> lateralEdges.count { it.a == v || it.b == v } != 1 }) continue

        return SegmentSolid3D(
            type = SegmentSolidType.HRANOL,
            segmentIds3D = graph.edges.map { it.segmentId },
            vertexPointIds = graph.pointsByKey.values.map { it.id },
            baseVertexPointIds = cycle.map { graph.pointsByKey.getValue(it).id }
        )
    }

    return null
}

private fun simpleCycles(graph: SegmentGraph, length: Int): List<List<VertexKey>> {
    val cycles = mutableListOf<List<VertexKey>>()
    val seen = mutableSetOf<Set<Pair<VertexKey, VertexKey>>>()
    val vertices = graph.pointsByKey.keys.toList()

    fun dfs(start: VertexKey, current: VertexKey, path: List<VertexKey>) {
        if (path.size == length) {
            if (start in graph.adjacency[current].orEmpty()) {
                val edgeSet = path.indices
                    .map { i -> unorderedPair(path[i], path[(i + 1) % path.size]) }
                    .toSet()
                if (seen.add(edgeSet)) cycles += path
            }
            return
        }
        for (next in graph.adjacency[current].orEmpty()) {
            if (next == start || next in path) continue
            dfs(start, next, path + next)
        }
    }

    vertices.forEach { dfs(it, it, listOf(it)) }
    return cycles
}

private fun formsSingleCycle(vertices: List<VertexKey>, edges: List<GraphEdge>): Boolean {
    if (vertices.size < 3 || edges.size != vertices.size) return false
    val vertexSet = vertices.toSet()
    val adjacency = mutableMapOf<VertexKey, MutableList<VertexKey>>()
    edges.forEach { edge ->
        if (edge.a !in vertexSet || edge.b !in vertexSet) return false
        adjacency.getOrPut(edge.a) { mutableListOf() }.add(edge.b)
        adjacency.getOrPut(edge.b) { mutableListOf() }.add(edge.a)
    }
    if (vertices.any { adjacency[it].orEmpty().size != 2 }) return false

    val seen = mutableSetOf<VertexKey>()
    val stack = ArrayDeque<VertexKey>()
    stack.add(vertices.first())
    while (stack.isNotEmpty()) {
        val v = stack.removeLast()
        if (!seen.add(v)) continue
        adjacency[v].orEmpty().forEach { if (it !in seen) stack.add(it) }
    }
    return seen.size == vertices.size
}

private fun orderedCycle(graph: SegmentGraph): List<GraphEdge>? {
    if (!graph.isSingleCycle()) return null
    val edgesByVertex = mutableMapOf<VertexKey, MutableList<GraphEdge>>()
    graph.edges.forEach { edge ->
        edgesByVertex.getOrPut(edge.a) { mutableListOf() }.add(edge)
        edgesByVertex.getOrPut(edge.b) { mutableListOf() }.add(edge)
    }

    val start = graph.edges.first().a
    var current = start
    var previous: VertexKey? = null
    val ordered = ArrayList<GraphEdge>(graph.edges.size)
    val used = mutableSetOf<String>()

    repeat(graph.edges.size) {
        val nextEdge = edgesByVertex[current]
            .orEmpty()
            .firstOrNull { it.segmentId !in used && it.other(current) != previous }
            ?: edgesByVertex[current].orEmpty().firstOrNull { it.segmentId !in used }
            ?: return null
        used += nextEdge.segmentId
        val next = nextEdge.other(current) ?: return null
        ordered += if (nextEdge.a == current) nextEdge else GraphEdge(nextEdge.segmentId, current, next)
        previous = current
        current = next
    }

    return if (current == start && used.size == graph.edges.size) ordered else null
}

private fun findCycleClosedByAddedSegment(graph: SegmentGraph, addedSegmentId: String): List<GraphEdge>? {
    val added = graph.edges.firstOrNull { it.segmentId == addedSegmentId } ?: return null
    val previousByVertex = mutableMapOf<VertexKey, Pair<VertexKey, GraphEdge>>()
    val queue = ArrayDeque<VertexKey>()
    val seen = mutableSetOf<VertexKey>()
    queue.add(added.b)
    seen += added.b

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == added.a) break

        graph.edges
            .asSequence()
            .filter { it.segmentId != addedSegmentId && (it.a == current || it.b == current) }
            .forEach { edge ->
                val next = edge.other(current) ?: return@forEach
                if (seen.add(next)) {
                    previousByVertex[next] = current to edge
                    queue.add(next)
                }
            }
    }

    if (added.a !in seen) return null

    val path = ArrayList<GraphEdge>()
    var current = added.a
    while (current != added.b) {
        val (previous, edge) = previousByVertex[current] ?: return null
        path += if (edge.a == previous) edge else GraphEdge(edge.segmentId, previous, current)
        current = previous
    }

    if (path.size < 2) return null
    path.reverse()
    return listOf(added) + path
}

private fun GraphEdge.other(vertex: VertexKey): VertexKey? =
    when (vertex) {
        a -> b
        b -> a
        else -> null
    }

private data class Vec3f(val x: Float, val y: Float, val z: Float) {
    fun dot(other: Vec3f): Float = x * other.x + y * other.y + z * other.z
    fun length(): Float = kotlin.math.sqrt(dot(this))
}

private operator fun Vec3f.minus(other: Vec3f): Vec3f =
    Vec3f(x - other.x, y - other.y, z - other.z)

private fun cross(a: Vec3f, b: Vec3f): Vec3f =
    Vec3f(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )

private data class Vec2f(val x: Float, val y: Float)

private fun isPlanarSimplePolygon(cycle: List<GraphEdge>, graph: SegmentGraph): Boolean {
    return isPlanarSimpleVertexFace(cycle.map { it.a }, graph)
}

private fun isPlanarSimpleVertexFace(vertices: List<VertexKey>, graph: SegmentGraph): Boolean {
    val points = vertices.map { graph.pointsByKey.getValue(it) }
    val normal = polygonNormal(points)
    if (normal.length() < POINT_EPS) return false
    if (!points.all { pointPlaneDistance(it, points.first(), normal) <= POINT_EPS * 10f }) return false

    val projected = projectToDominantPlane(points, normal)
    return !hasSelfIntersection(projected)
}

private fun polygonNormal(points: List<Point3D>): Vec3f {
    var nx = 0f
    var ny = 0f
    var nz = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        nx += (a.y - b.y) * (a.z + b.z)
        ny += (a.z - b.z) * (a.x + b.x)
        nz += (a.x - b.x) * (a.y + b.y)
    }
    return Vec3f(nx, ny, nz)
}

private fun pointPlaneDistance(point: Point3D, origin: Point3D, normal: Vec3f): Float {
    val len = normal.length()
    if (len < POINT_EPS) return Float.MAX_VALUE
    val v = Vec3f(point.x - origin.x, point.y - origin.y, point.z - origin.z)
    return abs(normal.dot(v)) / len
}

private fun projectToDominantPlane(points: List<Point3D>, normal: Vec3f): List<Vec2f> {
    val ax = abs(normal.x)
    val ay = abs(normal.y)
    val az = abs(normal.z)
    return when {
        ax >= ay && ax >= az -> points.map { Vec2f(it.y, it.z) }
        ay >= ax && ay >= az -> points.map { Vec2f(it.x, it.z) }
        else -> points.map { Vec2f(it.x, it.y) }
    }
}

private fun hasSelfIntersection(points: List<Vec2f>): Boolean {
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        for (j in i + 1 until points.size) {
            if (i == j) continue
            if ((i + 1) % points.size == j) continue
            if (i == (j + 1) % points.size) continue
            val c = points[j]
            val d = points[(j + 1) % points.size]
            if (segmentsIntersect2D(a, b, c, d)) return true
        }
    }
    return false
}

private fun segmentsIntersect2D(a: Vec2f, b: Vec2f, c: Vec2f, d: Vec2f): Boolean {
    fun orient(p: Vec2f, q: Vec2f, r: Vec2f): Float =
        (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)

    val o1 = orient(a, b, c)
    val o2 = orient(a, b, d)
    val o3 = orient(c, d, a)
    val o4 = orient(c, d, b)

    return o1 * o2 < -POINT_EPS && o3 * o4 < -POINT_EPS
}

private fun Point3D.vertexKey(): VertexKey =
    VertexKey(
        quantize(x),
        quantize(y),
        quantize(z)
    )

private fun quantize(value: Float): Int =
    if (abs(value) < POINT_EPS) 0 else (value / POINT_EPS).toInt()

private fun unorderedPair(a: VertexKey, b: VertexKey): Pair<VertexKey, VertexKey> =
    if (compareKeys(a, b) <= 0) a to b else b to a

private fun compareKeys(a: VertexKey, b: VertexKey): Int =
    compareValuesBy(a, b, VertexKey::x, VertexKey::y, VertexKey::z)
