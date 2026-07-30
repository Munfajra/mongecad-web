package gl3d.scene

import gl3d.math.Vec3
import gl3d.math.toVec3
import gl3d.render.Mesh3D
import model.Point3D
import model.classes.SegmentSolid3D
import model.classes.SegmentSolidType
import state.MongeState
import kotlin.math.abs

/**
 * Sítě těles z úseček (hranol, jehlan, mnohostěn) – port
 * `opengl/model/SegmentSolids.kt`.
 *
 * Stěny se berou přednostně z uložených polygonů tělesa; když je těleso nemá,
 * odvodí se z grafu hran podle typu. Vzniklý polygon se zorientuje ven
 * a otrojúhelníkuje uchem („ear clipping“) v rovině dominantní osy.
 */
internal fun buildSegmentSolidMesh(state: MongeState, solid: SegmentSolid3D): Mesh3D? {
    val segmentById = state.segments3D.associateBy { it.id }
    val solidSegments = solid.segmentIds3D.mapNotNull { segmentById[it] }
    if (solidSegments.isEmpty()) return null

    // Koncové body úseček, které leží na stejném místě, jsou obecně různé
    // instance Point3D s různým id (každý klik alokuje nový bod). Detekce
    // těles je slučuje podle kvantované polohy a mesh musí dělat totéž –
    // jinak se graf vrcholů roztříští a stěny se nepostaví (hranol by
    // nevykreslil nic, jehlanu by zbyla jen podstava).
    val rawPointById = LinkedHashMap<String, Point3D>()
    solidSegments.forEach { segment ->
        rawPointById[segment.start.id] = segment.start
        rawPointById[segment.end.id] = segment.end
    }
    state.sharedPoints3D.forEach { rawPointById.getOrPut(it.id) { it } }

    val canonicalIdByKey = LinkedHashMap<SolidVertexKey, String>()
    val pointById = LinkedHashMap<String, Point3D>()
    solidSegments.forEach { segment ->
        listOf(segment.start, segment.end).forEach { p ->
            val canonId = canonicalIdByKey.getOrPut(p.vertexKey()) { p.id }
            pointById.getOrPut(canonId) { p }
        }
    }

    fun canonicalId(id: String): String? =
        rawPointById[id]?.let { canonicalIdByKey[it.vertexKey()] }

    val vertexIds = canonicalIdByKey.values.toList()
    if (vertexIds.size < 4) return null
    val center = vertexIds
        .map { pointById.getValue(it).toVec3() }
        .reduce { acc, p -> acc + p } * (1f / vertexIds.size.toFloat())

    val adjacency = LinkedHashMap<String, MutableSet<String>>()
    fun addEdge(a: String, b: String) {
        adjacency.getOrPut(a) { linkedSetOf() }.add(b)
        adjacency.getOrPut(b) { linkedSetOf() }.add(a)
    }
    solidSegments.forEach { segment ->
        val a = canonicalId(segment.start.id) ?: return@forEach
        val b = canonicalId(segment.end.id) ?: return@forEach
        if (a != b) addEdge(a, b)
    }

    val canonSolid = solid.copy(
        apexPointId = solid.apexPointId?.let { canonicalId(it) },
        baseVertexPointIds = solid.baseVertexPointIds.mapNotNull { canonicalId(it) }.distinct(),
    )

    val faces = (
        polygonFacesFromSolidPolygons(state, solid, ::canonicalId) ?: when (solid.type) {
            SegmentSolidType.JEHLAN -> pyramidFaces(canonSolid, adjacency)
            SegmentSolidType.HRANOL -> prismFaces(canonSolid, vertexIds, adjacency)
            SegmentSolidType.MNOHOSTEN -> emptyList()
        }
        ).filter { face -> face.size >= 3 && face.all { id -> pointById[id] != null } }

    if (faces.isEmpty()) return null

    val positions = ArrayList<Float>()
    val normals = ArrayList<Float>()
    val indices = ArrayList<Int>()

    for (rawFace in faces) {
        val uniqueFace = rawFace.distinct()
        if (uniqueFace.size < 3) continue
        val ordered = orientFaceOutward(uniqueFace, pointById, center)
        val normal = faceNormal(ordered, pointById)
        val startIndex = positions.size / 3
        ordered.forEach { id ->
            val p = pointById.getValue(id)
            positions += p.x
            positions += p.y
            positions += p.z
            normals += normal.x
            normals += normal.y
            normals += normal.z
        }
        triangulateFace(ordered, pointById).forEach { tri ->
            indices += startIndex + tri.a
            indices += startIndex + tri.b
            indices += startIndex + tri.c
        }
    }
    if (indices.isEmpty()) return null

    return Mesh3D(positions.toFloatArray(), normals.toFloatArray(), indices.toIntArray())
}

/** Podpis geometrie tělesa pro cache sítě – port textového podpisu z desktopu. */
internal fun segmentSolidMeshSignature(state: MongeState, solid: SegmentSolid3D): Long {
    val segmentById = state.segments3D.associateBy { it.id }
    var hash = solid.type.ordinal.toLong()
    solid.polygonIds.sorted().forEach { hash = hash * 31 + it.hashCode() }
    solid.segmentIds3D.sorted().forEach { id ->
        hash = hash * 31 + id.hashCode()
        val segment = segmentById[id] ?: return@forEach
        listOf(segment.start, segment.end).forEach { p ->
            hash = hash * 31 + p.id.hashCode()
            hash = hash * 31 + p.x.toRawBits()
            hash = hash * 31 + p.y.toRawBits()
            hash = hash * 31 + p.z.toRawBits()
        }
    }
    return hash
}

private class FaceTri(val a: Int, val b: Int, val c: Int)

private class Vec2(val x: Float, val y: Float)

private fun polygonFacesFromSolidPolygons(
    state: MongeState,
    solid: SegmentSolid3D,
    canonicalId: (String) -> String?,
): List<List<String>>? {
    if (solid.polygonIds.isEmpty()) return null

    val solidSegmentIds = solid.segmentIds3D.toSet()
    val polygonIds = solid.polygonIds.toSet()
    val faces = state.polygons3D
        .filter { polygon ->
            polygon.id in polygonIds &&
                polygon.segmentIds3D.isNotEmpty() &&
                polygon.segmentIds3D.all { it in solidSegmentIds }
        }
        .mapNotNull { polygon ->
            val ids = polygon.vertexPointIds.mapNotNull { canonicalId(it) }.distinct()
            ids.takeIf { it.size >= 3 }
        }

    return faces.takeIf { it.isNotEmpty() }
}

private fun pyramidFaces(
    solid: SegmentSolid3D,
    adjacency: Map<String, Set<String>>,
): List<List<String>> {
    val apex = solid.apexPointId ?: return emptyList()
    val base = orderCycle(solid.baseVertexPointIds, adjacency)
    if (base.size < 3) return emptyList()
    val faces = mutableListOf<List<String>>()
    faces += base
    for (i in base.indices) {
        val a = base[i]
        val b = base[(i + 1) % base.size]
        if (b in adjacency[a].orEmpty() && apex in adjacency[a].orEmpty() && apex in adjacency[b].orEmpty()) {
            faces += listOf(apex, a, b)
        }
    }
    return faces
}

private fun prismFaces(
    solid: SegmentSolid3D,
    vertexIds: List<String>,
    adjacency: Map<String, Set<String>>,
): List<List<String>> {
    val base = orderCycle(solid.baseVertexPointIds, adjacency)
    if (base.size < 3) return emptyList()
    val baseSet = base.toSet()
    val other = vertexIds.filter { it !in baseSet }
    if (other.size != base.size) return emptyList()

    val lateralByBase = base.associateWith { id ->
        adjacency[id].orEmpty().firstOrNull { it !in baseSet }
    }
    if (lateralByBase.values.any { it == null }) return emptyList()

    val otherOrdered = base.mapNotNull { lateralByBase[it] }
    if (otherOrdered.size != base.size) return emptyList()

    val faces = mutableListOf<List<String>>()
    faces += base
    faces += otherOrdered.asReversed()
    for (i in base.indices) {
        val j = (i + 1) % base.size
        faces += listOf(base[i], base[j], otherOrdered[j], otherOrdered[i])
    }
    return faces
}

private fun orderCycle(ids: List<String>, adjacency: Map<String, Set<String>>): List<String> {
    if (ids.size < 3) return ids
    val allowed = ids.toSet()
    val start = ids.first()
    val ordered = ArrayList<String>(ids.size)
    var prev: String? = null
    var current = start

    repeat(ids.size) {
        ordered += current
        val next = adjacency[current]
            .orEmpty()
            .filter { it in allowed && it != prev }
            .firstOrNull { it !in ordered || (ordered.size == ids.size - 1 && it == start) }
            ?: return ids
        prev = current
        current = next
    }

    return if (current == start && ordered.toSet().size == ids.size) ordered else ids
}

private fun orientFaceOutward(
    face: List<String>,
    pointById: Map<String, Point3D>,
    solidCenter: Vec3,
): List<String> {
    val normal = faceNormal(face, pointById)
    val faceCenter = face
        .map { pointById.getValue(it).toVec3() }
        .reduce { acc, p -> acc + p } * (1f / face.size.toFloat())
    return if ((normal dot (faceCenter - solidCenter)) < 0f) face.asReversed() else face
}

private fun faceNormal(face: List<String>, pointById: Map<String, Point3D>): Vec3 {
    if (face.size < 3) return Vec3.UNIT_Z
    val a = pointById.getValue(face[0]).toVec3()
    for (i in 1 until face.lastIndex) {
        val b = pointById.getValue(face[i]).toVec3()
        val c = pointById.getValue(face[i + 1]).toVec3()
        val n = (b - a) cross (c - a)
        if ((n dot n) > 1e-10f) return n.normalized()
    }
    return Vec3.UNIT_Z
}

private fun triangulateFace(face: List<String>, pointById: Map<String, Point3D>): List<FaceTri> {
    if (face.size < 3) return emptyList()
    if (face.size == 3) return listOf(FaceTri(0, 1, 2))

    val points = face.map { pointById.getValue(it).toVec3() }
    val normal = faceNormal(face, pointById)
    val axis = dominantAxis(normal)
    val projected = points.map { p ->
        when (axis) {
            0 -> Vec2(p.y, p.z)
            1 -> Vec2(p.x, p.z)
            else -> Vec2(p.x, p.y)
        }
    }

    val ccw = signedArea(projected) > 0f
    val remaining = face.indices.toMutableList()
    val triangles = mutableListOf<FaceTri>()
    var guard = 0

    while (remaining.size > 3 && guard++ < face.size * face.size) {
        var clipped = false
        for (idx in remaining.indices) {
            val prev = remaining[(idx - 1 + remaining.size) % remaining.size]
            val curr = remaining[idx]
            val next = remaining[(idx + 1) % remaining.size]

            if (!isConvex(projected[prev], projected[curr], projected[next], ccw)) continue
            val containsPoint = remaining.any { other ->
                other != prev &&
                    other != curr &&
                    other != next &&
                    pointInTriangle(projected[other], projected[prev], projected[curr], projected[next])
            }
            if (containsPoint) continue

            triangles += FaceTri(prev, curr, next)
            remaining.removeAt(idx)
            clipped = true
            break
        }
        if (!clipped) break
    }

    if (remaining.size == 3) {
        triangles += FaceTri(remaining[0], remaining[1], remaining[2])
    }

    return triangles.ifEmpty {
        (1 until face.lastIndex).map { FaceTri(0, it, it + 1) }
    }
}

private fun dominantAxis(n: Vec3): Int {
    val ax = abs(n.x)
    val ay = abs(n.y)
    val az = abs(n.z)
    return when {
        ax >= ay && ax >= az -> 0
        ay >= ax && ay >= az -> 1
        else -> 2
    }
}

private fun signedArea(points: List<Vec2>): Float {
    var area = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        area += a.x * b.y - b.x * a.y
    }
    return area * 0.5f
}

private fun isConvex(a: Vec2, b: Vec2, c: Vec2, ccw: Boolean): Boolean {
    val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    return if (ccw) cross > 1e-6f else cross < -1e-6f
}

private fun pointInTriangle(p: Vec2, a: Vec2, b: Vec2, c: Vec2): Boolean {
    fun sign(p1: Vec2, p2: Vec2, p3: Vec2): Float =
        (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y)

    val d1 = sign(p, a, b)
    val d2 = sign(p, b, c)
    val d3 = sign(p, c, a)
    val hasNeg = d1 < -1e-6f || d2 < -1e-6f || d3 < -1e-6f
    val hasPos = d1 > 1e-6f || d2 > 1e-6f || d3 > 1e-6f
    return !(hasNeg && hasPos)
}

// Stejná kvantizace jako v detekci těles (POINT_EPS = 1e-4f), aby splývající
// koncové body s různými id spadly na jeden kanonický vrchol.
private const val SOLID_POINT_EPS = 1e-4f

private data class SolidVertexKey(val x: Int, val y: Int, val z: Int)

private fun solidQuantize(value: Float): Int =
    if (abs(value) < SOLID_POINT_EPS) 0 else (value / SOLID_POINT_EPS).toInt()

private fun Point3D.vertexKey(): SolidVertexKey =
    SolidVertexKey(solidQuantize(x), solidQuantize(y), solidQuantize(z))
