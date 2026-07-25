package draw.mongescreen.objects.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.*
import model.ArcMode
import model.LineStyle
import model.runtimeDrawColor
import kotlin.math.*


data class EllipseDrawBasis(
    val center: Offset,
    val u: Offset,
    val a: Float,
    val v: Offset,
    val b: Float
) {
    val isRegular: Boolean
        get() = !isDegenerateEllipseAxes(u, a, v, b)
}
fun DrawScope.drawEllipseFromDiametersProjected(
    p1: Offset,
    p2: Offset,
    p3: Offset,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val u = (p2 - p1) / 2f
    val a = u.getDistance()
    val mirrorP3 = Offset(2 * center.x - p3.x, 2 * center.y - p3.y)
    val v = (p3 - mirrorP3) / 2f
    val b = v.getDistance()
    val pathEffect = when (lineStyle) {
        LineStyle.Solid -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot -> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }

    if (isDegenerateEllipseAxes(u, a, v, b)) {
        val (start, end) = degenerateEllipseCarrier(listOf(p1, p2, p3))
        drawLine(
            color = color.runtimeDrawColor(),
            start = project(start),
            end = project(end),
            strokeWidth = strokeWidth,
            pathEffect = pathEffect
        )
        return
    }

    val uNorm = u / a
    val vNorm = v / b
    if (!uNorm.x.isFiniteF() || !uNorm.y.isFiniteF() || !vNorm.x.isFiniteF() || !vNorm.y.isFiniteF()) return

    val path = Path()
    val steps = 1000
    for (i in 0..steps) {
        val t = 2f * PI.toFloat() * i / steps
        val point = center + Offset(
            a * cos(t) * uNorm.x + b * sin(t) * vNorm.x,
            a * cos(t) * uNorm.y + b * sin(t) * vNorm.y
        )
        val projected = project(point)
        if (i == 0) path.moveTo(projected.x, projected.y) else path.lineTo(projected.x, projected.y)
    }

    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = pathEffect))
}
/**
 * Draws an ellipse arc projected through a lambda (used for axo projections).
 * The arc endpoints A, B and the ellipse-defining points p1, p2, p3 are in the
 * conic's own 2D coordinate space; they are all projected through [project].
 */
fun DrawScope.drawEllipseArcFromDiametersProjected(
    p1: Offset, p2: Offset, p3: Offset,
    A: Offset, B: Offset,
    mode: ArcMode = ArcMode.SHORTEST,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val u = (p2 - p1) / 2f
    val a = u.getDistance()
    val mirrorP3 = Offset(2f * center.x - p3.x, 2f * center.y - p3.y)
    val v = (p3 - mirrorP3) / 2f
    val b = v.getDistance()
    if (isDegenerateEllipseAxes(u, a, v, b)) {
        drawLine(
            color = color.runtimeDrawColor(),
            start = project(A),
            end = project(B),
            strokeWidth = strokeWidth,
            pathEffect = pathEffectFor(lineStyle)
        )
        return
    }

    val uN = Offset(u.x / a, u.y / a)
    val vN = Offset(v.x / b, v.y / b)
    if (!uN.x.isFiniteF() || !uN.y.isFiniteF() || !vN.x.isFiniteF() || !vN.y.isFiniteF()) return

    val basis = monge.input.ConicArcs.single.ellipseBasisFromDiameters(p1, p2, p3)
    val (tA, Aon) = monge.input.ConicArcs.single.ellipseParamAndProjection(basis, A)
    val (tB, Bon) = monge.input.ConicArcs.single.ellipseParamAndProjection(basis, B)

    fun normAngle(t: Float): Float {
        val twoPi = (2f * PI).toFloat()
        var x = t % twoPi
        if (x < 0f) x += twoPi
        return x
    }
    fun ccwSpan(a1: Float, a2: Float): Float {
        val twoPi = (2f * PI).toFloat()
        val x1 = normAngle(a1); val x2 = normAngle(a2)
        return if (x2 >= x1) x2 - x1 else x2 - x1 + twoPi
    }

    val spanCCW = ccwSpan(tA, tB)
    val spanCW = (2f * PI).toFloat() - spanCCW

    val (tStart, tEnd) = when (mode) {
        ArcMode.SHORTEST -> if (spanCCW <= spanCW) tA to (tA + spanCCW) else tA to (tA - spanCW)
        ArcMode.LONGEST  -> if (spanCCW >= spanCW) tA to (tA + spanCCW) else tA to (tA - spanCW)
        ArcMode.CCW      -> tA to (tA + spanCCW)
        ArcMode.CW       -> tA to (tA - spanCW)
    }

    strokeEllipseParamArc(
        center = center, a = a, b = b, uN = uN, vN = vN,
        tStart = tStart, tEnd = tEnd,
        style = lineStyle, project = project,
        color = color, strokeWidth = strokeWidth, dashScale = 1f
    )
}

/**
 * Vykreslí jeden úsek elipsy zadaný přímo nativními parametry [tStart]..[tEnd]
 * (úhel na elipse). Jádro sdílené obloukem i po částech stylovanou elipsou.
 * Geometrie (center/a/b/uN/vN) je view-space, [project] mapuje do plátna.
 */
fun DrawScope.strokeEllipseParamArc(
    center: Offset, a: Float, b: Float, uN: Offset, vN: Offset,
    tStart: Float, tEnd: Float,
    style: LineStyle,
    project: (Offset) -> Offset,
    color: Color, strokeWidth: Float, dashScale: Float = 1f
) {
    val dir = if (tEnd >= tStart) +1f else -1f
    val length = abs(tEnd - tStart)
    if (length < 1e-6f) return
    val steps = max(2, ceil(length / 0.05f).toInt())

    fun pointAt(t: Float): Offset = center + Offset(
        a * cos(t) * uN.x + b * sin(t) * vN.x,
        a * cos(t) * uN.y + b * sin(t) * vN.y
    )

    val path = Path()
    val projStart = project(pointAt(tStart))
    path.moveTo(projStart.x, projStart.y)
    for (i in 1 until steps) {
        val t = tStart + dir * (i * (length / steps))
        val projected = project(pointAt(t))
        path.lineTo(projected.x, projected.y)
    }
    val projEnd = project(pointAt(tEnd))
    path.lineTo(projEnd.x, projEnd.y)

    drawPath(path, color.runtimeDrawColor(), style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(style, dashScale), cap = StrokeCap.Round))
}
fun isDegenerateEllipseAxes(u: Offset, a: Float, v: Offset, b: Float): Boolean {
    if (!a.isFiniteF() || !b.isFiniteF() ||
        !u.x.isFiniteF() || !u.y.isFiniteF() ||
        !v.x.isFiniteF() || !v.y.isFiniteF()
    ) return true

    val major = max(a, b)
    val minor = min(a, b)
    if (major < ELLIPSE_EPS) return true
    if (minor < max(ELLIPSE_EPS, major * ELLIPSE_REL_DEGENERATE_EPS)) return true

    val det = abs(u.x * v.y - u.y * v.x)
    val radiusScale = (a * a + b * b).coerceAtLeast(ELLIPSE_EPS)
    return det < max(ELLIPSE_EPS, radiusScale * ELLIPSE_REL_DEGENERATE_EPS)
}
fun degenerateEllipseCarrier(points: List<Offset>): Pair<Offset, Offset> {
    if (points.size < 3) return longestPair(points)
    val basis = ellipseDrawBasisFromDiameters(points[0], points[1], points[2])
    val dirRaw = if (basis.a >= basis.b) basis.u else basis.v
    val len = dirRaw.getDistance()
    if (len < ELLIPSE_EPS) return basis.center to basis.center
    val dir = dirRaw / len
    val radius = sqrt(basis.a * basis.a + basis.b * basis.b)
    return (basis.center - dir * radius) to (basis.center + dir * radius)
}
private fun longestPair(points: List<Offset>): Pair<Offset, Offset> =
    points.flatMapIndexed { i, a -> points.drop(i + 1).map { b -> a to b } }
        .maxByOrNull { (a, b) -> (a - b).getDistance() }
        ?: ((points.firstOrNull() ?: Offset.Zero) to (points.firstOrNull() ?: Offset.Zero))
fun ellipseDrawBasisFromDiameters(p1: Offset, p2: Offset, p3: Offset): EllipseDrawBasis {
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val u = (p2 - p1) / 2f
    val v = p3 - center
    return EllipseDrawBasis(center, u, u.getDistance(), v, v.getDistance())
}

fun DrawScope.drawEllipseFromDiametersAxoLocal(
    p1: Offset,
    p2: Offset,
    p3: Offset,
    scale: Float,
    canvasOffset: Offset,
    axoOrigin: Offset,
    color: Color = Color.Black,
    strokeWidth: Float = 1.5f,
    lineStyle: LineStyle = LineStyle.Solid
) {
    val drawBasis = ellipseDrawBasisFromDiameters(p1, p2, p3)

    if (!drawBasis.isRegular) {
        drawDegenerateEllipseAxoLocal(
            points = listOf(p1, p2, p3),
            scale = scale,
            canvasOffset = canvasOffset,
            axoOrigin = axoOrigin,
            color = color.runtimeDrawColor(),
            strokeWidth = strokeWidth,
            lineStyle = lineStyle
        )
        return
    }

    val center = Offset(
        x = (p1.x + p2.x) / 2f,
        y = (p1.y + p2.y) / 2f
    )

    val u = (p2 - p1) / 2f
    val a = u.getDistance()

    val mirrorP3 = Offset(
        x = 2f * center.x - p3.x,
        y = 2f * center.y - p3.y
    )

    val v = (p3 - mirrorP3) / 2f
    val b = v.getDistance()

    if (isDegenerateEllipseAxes(u, a, v, b)) {
        drawDegenerateEllipseAxoLocal(
            points = listOf(p1, p2, p3),
            scale = scale,
            canvasOffset = canvasOffset,
            axoOrigin = axoOrigin,
            color = color.runtimeDrawColor(),
            strokeWidth = strokeWidth,
            lineStyle = lineStyle
        )
        return
    }

    val uNorm = u / a
    val vNorm = v / b

    if (
        !uNorm.x.isFiniteF() || !uNorm.y.isFiniteF() ||
        !vNorm.x.isFiniteF() || !vNorm.y.isFiniteF()
    ) {
        return
    }

    val path = Path()
    val steps = 1000

    for (i in 0..steps) {
        val t = 2f * PI.toFloat() * i / steps

        val point = center + Offset(
            x = a * cos(t) * uNorm.x + b * sin(t) * vNorm.x,
            y = a * cos(t) * uNorm.y + b * sin(t) * vNorm.y
        )

        val screen = point.toScreenAxoLocal(
            scale = scale,
            canvasOffset = canvasOffset,
            axoOrigin = axoOrigin
        )

        if (i == 0) {
            path.moveTo(screen.x, screen.y)
        } else {
            path.lineTo(screen.x, screen.y)
        }
    }

    drawPath(
        path = path,
        color = color.runtimeDrawColor(),
        style = Stroke(
            width = strokeWidth,
            pathEffect = pathEffectFor(lineStyle, scale),
            cap = StrokeCap.Round
        )
    )
}
private fun DrawScope.drawDegenerateEllipseAxoLocal(
    points: List<Offset>,
    scale: Float,
    canvasOffset: Offset,
    axoOrigin: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val (carrierA, carrierB) = degenerateEllipseCarrier(points)
    val length = (carrierB - carrierA).getDistance()

    if (length < ELLIPSE_EPS) {
        drawCircle(
            color = color.runtimeDrawColor(),
            radius = max(1f, strokeWidth * 0.5f),
            center = carrierA.toScreenAxoLocal(scale, canvasOffset, axoOrigin)
        )
        return
    }

    drawLine(
        color = color.runtimeDrawColor(),
        start = carrierA.toScreenAxoLocal(scale, canvasOffset, axoOrigin),
        end = carrierB.toScreenAxoLocal(scale, canvasOffset, axoOrigin),
        strokeWidth = strokeWidth,
        pathEffect = pathEffectFor(lineStyle, scale),
        cap = StrokeCap.Round
    )
}