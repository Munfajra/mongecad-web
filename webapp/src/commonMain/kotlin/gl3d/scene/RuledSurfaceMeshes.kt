package gl3d.scene

import gl3d.math.Vec3
import gl3d.render.Mesh3D
import model.classes.RuledSurface3D
import monge.input.ruledsurface.RuledSurfaceGenerator3D
import monge.input.ruledsurface.ruledSurfaceDirectrixGeometrySignature
import monge.input.ruledsurface.ruledSurfaceFamilyIsClosed
import monge.input.ruledsurface.ruledSurfaceGeneratorTrimSignature
import monge.input.ruledsurface.sampleRuledSurfaceTrimmedPrimaryFamilies
import state.MongeState
import kotlin.math.sqrt

/**
 * Sítě přímkových ploch – webová varianta `opengl/model/RuledSurfaces.kt`.
 *
 * Plocha se pro mesh převzorkuje na [RULED_SURFACE_MESH_SAMPLES] tvořic, takže
 * je hladká bez ohledu na to, kolik tvořic uživatel zrovna zobrazuje.
 * `generatorCount` řídí jen vykreslené tvořice ve 2D, na síť nemá vliv.
 */
private const val RULED_SURFACE_MESH_SAMPLES = 96

internal fun buildRuledSurfaceMesh(state: MongeState, surface: RuledSurface3D): Mesh3D? {
    // Jedna regula pro každou nesouvislou komponentu. U dvojně přímkové
    // komponenty se mesh neduplikuje, ale obě větve hyperboly zůstanou.
    val families = sampleRuledSurfaceTrimmedPrimaryFamilies(state, surface, RULED_SURFACE_MESH_SAMPLES)
    return buildMeshFromFamilies(families, ruledSurfaceFamilyIsClosed(state, surface))
}

/**
 * Podpis geometrie pro cache sítě. Sleduje i uživatelský ořez/přesah tvořic,
 * ne jen řídicí geometrii – shodně s desktopem.
 */
internal fun ruledSurfaceMeshSignature(state: MongeState, surface: RuledSurface3D): Long =
    (31 * ruledSurfaceDirectrixGeometrySignature(state, surface) +
        ruledSurfaceGeneratorTrimSignature(surface)).toLong()

/** Port `buildRuledSurfaceMesh` z desktopu – pásy mezi sousedními tvořicemi. */
private fun buildMeshFromFamilies(
    families: List<List<RuledSurfaceGenerator3D>>,
    closed: Boolean,
): Mesh3D? {
    val usableFamilies = families.filter { it.size >= 2 }
    if (usableFamilies.isEmpty()) return null

    val vertexCount = usableFamilies.sumOf { it.size * 2 }
    val positions = FloatArray(vertexCount * 3)
    var vertexBase = 0
    usableFamilies.forEach { generators ->
        generators.forEachIndexed { index, generator ->
            listOf(generator.start, generator.end).forEachIndexed { side, point ->
                val offset = (vertexBase + index * 2 + side) * 3
                positions[offset] = point.x
                positions[offset + 1] = point.y
                positions[offset + 2] = point.z
            }
        }
        vertexBase += generators.size * 2
    }

    val indexList = ArrayList<Int>()
    vertexBase = 0
    usableFamilies.forEach { generators ->
        val stripCount = if (closed) generators.size else generators.lastIndex
        for (i in 0 until stripCount) {
            val next = (i + 1) % generators.size
            val a = vertexBase + i * 2
            val b = vertexBase + next * 2
            indexList += a
            indexList += b
            indexList += b + 1
            indexList += a
            indexList += b + 1
            indexList += a + 1
        }
        vertexBase += generators.size * 2
    }
    if (indexList.isEmpty()) return null
    val indices = indexList.toIntArray()

    val normals = FloatArray(positions.size)
    for (i in indices.indices step 3) {
        val ia = indices[i]
        val ib = indices[i + 1]
        val ic = indices[i + 2]
        val a = positions.pointAt(ia)
        val normal = (positions.pointAt(ib) - a) cross (positions.pointAt(ic) - a)
        for (vertex in intArrayOf(ia, ib, ic)) {
            val offset = vertex * 3
            normals[offset] += normal.x
            normals[offset + 1] += normal.y
            normals[offset + 2] += normal.z
        }
    }
    for (vertex in 0 until vertexCount) {
        val offset = vertex * 3
        val length = sqrt(
            normals[offset] * normals[offset] +
                normals[offset + 1] * normals[offset + 1] +
                normals[offset + 2] * normals[offset + 2]
        )
        if (length > 1e-7f) {
            normals[offset] /= length
            normals[offset + 1] /= length
            normals[offset + 2] /= length
        } else {
            normals[offset + 2] = 1f
        }
    }
    return Mesh3D(positions, normals, indices)
}

private fun FloatArray.pointAt(index: Int): Vec3 {
    val offset = index * 3
    return Vec3(this[offset], this[offset + 1], this[offset + 2])
}
