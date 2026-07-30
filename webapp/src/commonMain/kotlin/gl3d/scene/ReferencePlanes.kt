package gl3d.scene

import androidx.compose.ui.graphics.Color
import gl3d.math.Vec3
import gl3d.render.TriangleBatch
import model.ProjectionMode
import state.MongeState
import kotlin.math.abs

/**
 * Referenční průmětny π (z = 0), ν (y = 0) a μ (x = 0).
 *
 * Port `renderReferencePlanesGeoGebraStyle` z `opengl/model/Prumetny.kt`:
 * plná vnitřní část a kolem ní prstence, kterými průhlednost plynule klesá
 * k okraji, takže rovina nikde nekončí ostrou hranou.
 *
 * Míchají se přes OIT stejně jako na desktopu. Řazení podle vzdálenosti od
 * oka je tak vlastně nadbytečné, ale drží správný výsledek i na nouzové cestě
 * bez OIT, kde se roviny míchají přímo.
 */
internal fun collectReferencePlanes(
    state: MongeState,
    batch: TriangleBatch,
    size: Float,
    viewDirection: Vec3,
    eye: Vec3,
) {
    val planes = ArrayList<ReferencePlane>(3)

    if (state.showReferencePlanesP) {
        planes += ReferencePlane(Vec3(0f, 0f, 1f), 0f, state.colorReferencePlanesP)
    }
    // KOTO nárysnu nekreslí, bokorysna dává smysl jen v axonometrii –
    // stejné podmínky jako na volajícím místě v `renderScene3D`.
    if (state.showReferencePlanesN && state.projectionMode != ProjectionMode.KOTO) {
        planes += ReferencePlane(Vec3(0f, 1f, 0f), 0f, state.colorReferencePlanesN)
    }
    if (state.projectionMode == ProjectionMode.AXO && state.showReferencePlanesB) {
        planes += ReferencePlane(Vec3(1f, 0f, 0f), 0f, state.colorReferencePlanesB)
    }
    if (planes.isEmpty()) return

    // Od nejvzdálenější k nejbližší – bez zápisu do hloubky je pořadí
    // míchání jediné, co drží správný výsledek.
    planes.sortByDescending { abs((it.normal dot eye) + it.d) }

    for (plane in planes) {
        val facing = abs(plane.normal.normalized() dot viewDirection).coerceIn(0f, 1f)
        addFadedPlane(
            plane = plane,
            batch = batch,
            size = size,
            alpha = REF_FRONT_ALPHA * (0.48f + 0.52f * facing),
            edgeAlpha = REF_BEHIND_ALPHA * (0.48f + 0.52f * facing),
            colorScale = 0.82f + 0.18f * facing,
        )
    }
}

private class ReferencePlane(val normal: Vec3, val d: Float, val color: Color)

private fun addFadedPlane(
    plane: ReferencePlane,
    batch: TriangleBatch,
    size: Float,
    alpha: Float,
    edgeAlpha: Float,
    colorScale: Float,
) {
    val frame = planeFrame(plane.normal, plane.d) ?: return
    val red = plane.color.red * colorScale
    val green = plane.color.green * colorScale
    val blue = plane.color.blue * colorScale

    val inner = size * 0.82f
    val fadeEnd = size * 0.992f

    addSquare(batch, frame, inner, red, green, blue, alpha)

    var previous = inner
    for (i in 1..RING_COUNT) {
        val next = inner + (fadeEnd - inner) * (i.toFloat() / RING_COUNT)
        val mid = (i - 0.5f) / RING_COUNT
        val smooth = mid * mid * (3f - 2f * mid)
        val fade = 1f - smooth
        val ringAlpha = edgeAlpha + (alpha * 0.78f - edgeAlpha) * fade * fade
        addRing(batch, frame, previous, next, red, green, blue, ringAlpha)
        previous = next
    }

    addRing(
        batch, frame, fadeEnd, size,
        red * 0.82f, green * 0.82f, blue * 0.82f,
        maxOf(edgeAlpha * 0.9f, alpha * 0.035f),
    )
}

/** Střed roviny a dva na sebe kolmé směry v ní. Sdílené s [collectUserPlanes]. */
internal class PlaneFrame(val center: Vec3, val tangent: Vec3, val bitangent: Vec3)

internal fun planeFrame(normal: Vec3, d: Float): PlaneFrame? {
    val lengthSquared = normal dot normal
    if (lengthSquared < 1e-8f) return null

    val unit = normal.normalized()
    val center = normal * (-d / lengthSquared)
    val up = if (abs(unit.z) < 0.9f) Vec3.UNIT_Z else Vec3(0f, 1f, 0f)
    val tangent = (up cross unit).normalized()
    return PlaneFrame(center, tangent, (unit cross tangent).normalized())
}

internal fun PlaneFrame.point(u: Float, v: Float): Vec3 =
    center + tangent * u + bitangent * v

private fun addSquare(
    batch: TriangleBatch,
    frame: PlaneFrame,
    half: Float,
    red: Float, green: Float, blue: Float, alpha: Float,
) {
    batch.addQuad(
        frame.point(-half, -half),
        frame.point(half, -half),
        frame.point(half, half),
        frame.point(-half, half),
        red, green, blue, alpha,
    )
}

/** Mezikruží mezi dvěma soustřednými čtverci, poskládané ze čtyř pásů. */
private fun addRing(
    batch: TriangleBatch,
    frame: PlaneFrame,
    inner: Float,
    outer: Float,
    red: Float, green: Float, blue: Float, alpha: Float,
) {
    if (outer <= inner) return
    val i = inner
    val o = outer

    batch.addQuad(
        frame.point(-o, -o), frame.point(o, -o), frame.point(i, -i), frame.point(-i, -i),
        red, green, blue, alpha,
    )
    batch.addQuad(
        frame.point(o, -o), frame.point(o, o), frame.point(i, i), frame.point(i, -i),
        red, green, blue, alpha,
    )
    batch.addQuad(
        frame.point(o, o), frame.point(-o, o), frame.point(-i, i), frame.point(i, i),
        red, green, blue, alpha,
    )
    batch.addQuad(
        frame.point(-o, o), frame.point(-o, -o), frame.point(-i, -i), frame.point(-i, i),
        red, green, blue, alpha,
    )
}

/** Hodnoty z `opengl/Viz.kt`. */
private const val REF_FRONT_ALPHA = 0.2f
private const val REF_BEHIND_ALPHA = 0.035f
private const val RING_COUNT = 12
