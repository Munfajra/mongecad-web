package draw.mongescreen.objects.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.ELLIPSE_EPS
import draw.mongescreen.objects.pathEffectFor
import draw.mongescreen.objects.toScreenAxoLocal
import draw.mongescreen.conicarcs.ParabolaBasis
import draw.mongescreen.conicarcs.fromLocal
import draw.mongescreen.conicarcs.parabolaBasis
import draw.mongescreen.conicarcs.projectParabolaAndParam
import model.LineStyle
import model.runtimeDrawColor
import kotlin.math.*

fun DrawScope.drawDegenerateParabolaAxoLocal(
    origin: Offset,
    dirIn: Offset,
    isLine: Boolean,
    scale: Float,
    canvasOffset: Offset,
    axoOrigin: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    drawDegenerateParabolaProjected(
        origin = origin,
        dirIn = dirIn,
        isLine = isLine,
        project = { it.toScreenAxoLocal(scale, canvasOffset, axoOrigin) },
        color = color.runtimeDrawColor(),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle
    )
}
fun DrawScope.drawDegenerateParabolaProjected(
    origin: Offset,
    dirIn: Offset,
    isLine: Boolean,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val len = dirIn.getDistance()
    val dir = if (len < ELLIPSE_EPS) Offset(1f, 0f) else dirIn / len
    val span = max(size.width, size.height).coerceAtLeast(1f)
    val a = if (isLine) origin - dir * span else origin
    val b = origin + dir * span
    drawLine(
        color = color.runtimeDrawColor(),
        start = project(a),
        end = project(b),
        strokeWidth = strokeWidth,
        pathEffect = pathEffectFor(lineStyle),
        cap = StrokeCap.Round
    )
}
fun DrawScope.drawParabolaAxoLocal(
    vertex: Offset,
    focus: Offset,
    scale: Float,
    canvasOffset: Offset,
    axoOrigin: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    drawParabolaProjected(
        vertex = vertex,
        focus = focus,
        project = { it.toScreenAxoLocal(scale, canvasOffset, axoOrigin) },
        color = color.runtimeDrawColor(),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle
    )
}
fun DrawScope.drawParabolaProjected(
    vertex: Offset,
    focus: Offset,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val axis = focus - vertex
    val p = axis.getDistance()
    if (p < ELLIPSE_EPS) return

    val dir = axis / p
    val normal = Offset(-dir.y, dir.x)
    val span = max(size.width, size.height).coerceAtLeast(1f)
    val maxT = min(40f * sqrt(p), 2f * sqrt(span * 4f * p))

    val path = Path()
    val steps = 600
    for (i in 0..steps) {
        val t = -maxT + (2f * maxT * i / steps)
        val point = vertex + normal * t + dir * (t * t / (4f * p))
        val projected = project(point)
        if (i == 0) path.moveTo(projected.x, projected.y) else path.lineTo(projected.x, projected.y)
    }

    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(lineStyle), cap = StrokeCap.Round))
}
/**
 * Draws a parabola arc projected through a lambda (used for axo projections).
 * vertex, focus, A, B are in the conic's own 2D coordinate space.
 */
fun DrawScope.drawParabolaArcProjected(
    vertex: Offset,
    focus: Offset,
    A: Offset,
    B: Offset,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val basis = parabolaBasis(vertex, focus)
    val p = basis.p
    if (p < 1e-6f) return

    val (u1, _) = projectParabolaAndParam(vertex, focus, A)
    val (u2, _) = projectParabolaAndParam(vertex, focus, B)

    strokeParabolaParamArc(
        basis = basis, u1 = u1, u2 = u2,
        style = lineStyle, project = project,
        color = color, strokeWidth = strokeWidth, dashScale = 1f
    )
}

/**
 * Vykreslí jeden úsek paraboly zadaný přímo nativními parametry [u1]..[u2]
 * (souřadnice podél normály osy). Jádro sdílené obloukem i po částech
 * stylovanou parabolou.
 */
fun DrawScope.strokeParabolaParamArc(
    basis: ParabolaBasis,
    u1: Float, u2: Float,
    style: LineStyle,
    project: (Offset) -> Offset,
    color: Color, strokeWidth: Float, dashScale: Float = 1f
) {
    val p = basis.p
    if (p < 1e-6f) return

    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    if (L < 1e-6f) return
    val steps = max(2, ceil(L / 0.5f).toInt()).coerceAtLeast(200)

    fun pointAt(u: Float): Offset {
        val x = (u * u) / (4f * p)
        return fromLocal(basis, Offset(x, u))
    }

    val path = Path()
    val projStart = project(pointAt(u1))
    path.moveTo(projStart.x, projStart.y)
    for (i in 1 until steps) {
        val u = u1 + dir * (i * (L / steps))
        val projected = project(pointAt(u))
        path.lineTo(projected.x, projected.y)
    }
    val projEnd = project(pointAt(u2))
    path.lineTo(projEnd.x, projEnd.y)

    drawPath(path, color.runtimeDrawColor(), style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(style, dashScale), cap = StrokeCap.Round))
}