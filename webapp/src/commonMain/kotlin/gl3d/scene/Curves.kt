package gl3d.scene

import gl3d.math.Vec3
import gl3d.math.toVec3
import gl3d.render.LineBatch
import gl3d.render.LineStyle3D
import gl3d.render.ScreenProjector
import model.classes.Curve3D
import state.MongeState
import model.gl3dLineColor

/**
 * Obecné 3D křivky – port `drawCurve3D` a `sampleCatmullRom3D`
 * z `opengl/model/Curves.kt`.
 *
 * Křivka má buď hotovou lomenou čáru (`polyline3D` – tak přicházejí průniky),
 * nebo jen řídicí body, mezi kterými se prokládá Catmull-Rom spline.
 */
internal fun collectCurves(
    state: MongeState,
    batch: LineBatch,
    projector: ScreenProjector,
    depthBias: Float = 0f,
) {
    for (curve in state.curves3D) {
        if (!curve.show) continue
        val points = sampleCurve3D(curve, state)
        if (points.size < 2) continue
        batch.addPolyline(
            points,
            LineStyle3D.of(
                color = curve.color.gl3dLineColor(),
                width = curve.strokeWidth,
                pattern = curve.lineStyle.toPatternValue(),
                depthBias = depthBias,
            ),
            projector,
        )
    }
}

/** Body křivky ve světových souřadnicích; prázdné, když se nedá sestavit. */
internal fun sampleCurve3D(curve: Curve3D, state: MongeState): List<Vec3> {
    val baked = curve.polyline3D
    if (baked != null) {
        val base = baked.map { it.toVec3() }
        return if (curve.closed && base.size >= 2) base + base.first() else base
    }
    val pointsById = state.sharedPoints3D.associateBy { it.id }
    val control = curve.pointIds.mapNotNull { pointsById[it]?.toVec3() }
    if (control.size < 2) return emptyList()
    return sampleCatmullRom3D(control, curve.closed)
}

private fun catmullRom(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Float): Vec3 {
    val t2 = t * t
    val t3 = t2 * t

    fun cr(a0: Float, a1: Float, a2: Float, a3: Float): Float =
        0.5f * (
            2f * a1 +
                (-a0 + a2) * t +
                (2f * a0 - 5f * a1 + 4f * a2 - a3) * t2 +
                (-a0 + 3f * a1 - 3f * a2 + a3) * t3
            )

    return Vec3(
        cr(p0.x, p1.x, p2.x, p3.x),
        cr(p0.y, p1.y, p2.y, p3.y),
        cr(p0.z, p1.z, p2.z, p3.z),
    )
}

private fun sampleCatmullRom3D(
    control: List<Vec3>,
    closed: Boolean,
    stepsPerSegment: Int = 18,
): List<Vec3> {
    if (control.size < 2) return emptyList()
    if (control.size == 2) return control // nic k vyhlazení

    val out = ArrayList<Vec3>(control.size * stepsPerSegment)

    fun get(i: Int): Vec3 {
        val n = control.size
        return if (closed) control[(i % n + n) % n] else control[i.coerceIn(0, n - 1)]
    }

    val segments = if (closed) control.size else control.size - 1
    for (i in 0 until segments) {
        val p0 = get(i - 1)
        val p1 = get(i)
        val p2 = get(i + 1)
        val p3 = get(i + 2)

        // Aby se body neduplikovaly: první segment začíná v t = 0, další v 1.
        val start = if (i == 0) 0 else 1
        for (s in start..stepsPerSegment) {
            out += catmullRom(p0, p1, p2, p3, s.toFloat() / stepsPerSegment.toFloat())
        }
    }

    // U uzavřené křivky nechceme poslední bod shodný s prvním – ve vzoru čáry
    // by v tom místě vznikl „špunt".
    if (closed && out.size >= 2) {
        val delta = out.last() - out.first()
        if ((delta dot delta) < 1e-8f) out.removeAt(out.lastIndex)
    }

    return out
}
