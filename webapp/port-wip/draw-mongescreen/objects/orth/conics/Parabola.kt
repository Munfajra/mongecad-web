package draw.mongescreen.objects.orth.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.lineStyleDashPathEffectPx
import draw.mongescreen.objects.pathEffectFor
import draw.mongescreen.previews.conicsarcs.toScreenNarys
import model.LineStyle
import model.runtimeDrawColor
import utils.toScreenOld
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

fun DrawScope.drawParabolaDegenerateArcNarys(
    origin: Offset,
    dirIn: Offset,
    isLine: Boolean,
    A: Offset,
    B: Offset,
    scale: Float,
    canvasOffset: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    var dir = dirIn
    val len = dir.getDistance()
    dir = if (len < 1e-6f) Offset(1f, 0f) else dir / len

    fun projT(p: Offset): Float = (p.x - origin.x) * dir.x + (p.y - origin.y) * dir.y
    var tA = projT(A)
    var tB = projT(B)
    if (!isLine) {
        if (tA < 0f) tA = 0f
        if (tB < 0f) tB = 0f
    }
    val p1 = origin + dir * tA
    val p2 = origin + dir * tB
    if ((p2 - p1).getDistance() < 1e-6f) return

    drawLine(
        color = color.runtimeDrawColor(),
        start = p1.toScreenNarys(scale, canvasOffset),
        end = p2.toScreenNarys(scale, canvasOffset),
        strokeWidth = strokeWidth,
        pathEffect = pathEffectFor(lineStyle, scale),
        cap = StrokeCap.Round
    )
}
fun DrawScope.drawConicParabolaPudorys(
    vertex: Offset,
    focus: Offset,
    canvasOffset: Offset,
    scale: Float,
    color: Color = Color.Black,
    strokeWidth: Float = 1.5f,
    lineStyle: LineStyle = LineStyle.Solid,
    tStep: Float = 0.5f,
    // ⬇️ NEW
    degenerateRay: Boolean = false,
    rayExtendFactor: Float = 2f
) {
    val axis = focus - vertex
    val p = axis.getDistance()
    if (p < 1e-6f) return

    val dir = Offset(axis.x / p, axis.y / p)

    if (degenerateRay) {
        // ⚑ vykresli polopřímku z vrcholu ve směru dir
        val rayLenPx = max(size.width, size.height) * rayExtendFactor
        val rayEndWorld = vertex + dir * (rayLenPx / max(1e-6f, scale))  // převod délky okna do world jednotek
        val a = vertex.toScreenOld(scale, canvasOffset)
        val b = rayEndWorld.toScreenOld(scale, canvasOffset)

        val pathEffect = lineStyleDashPathEffectPx(lineStyle, scale = scale)

        drawLine(
            color = color.runtimeDrawColor(),
            start = a,
            end = b,
            strokeWidth = strokeWidth,
            pathEffect = pathEffect,
            cap = if (lineStyle == LineStyle.Solid) StrokeCap.Butt else StrokeCap.Round
        )
        return
    }

    // ▼ původní kreslení paraboly (jen pár drobných ochran)
    val normal = Offset(-dir.y, dir.x)

    // Bezpečnější limit t: omezíme na to, co dává rozumný rozsah v okně
    // y_par = vertex + normal*t + dir*(t^2/(4p))
    // hrubý limit – ať kvadratická složka nepřeleze ~2x větší rozměr okna v world jednotkách
    val worldSpan = max(size.width, size.height) / max(1e-6f, scale)
    val maxT = min(40f * sqrt(p),  // tvůj původní dynamický
        2f * sqrt(worldSpan * 4f * p)) // ochrana proti „ulítlým“ hodnotám
    val tStart = -maxT
    val tEnd   = +maxT

    val path = Path()
    var started = false

    var t = tStart
    while (t <= tEnd) {
        val point = vertex + normal * t + dir * (t * t / (4f * p))
        val screen = point.toScreenOld(scale, canvasOffset)

        if (!started) {
            path.moveTo(screen.x, screen.y)
            started = true
        } else {
            path.lineTo(screen.x, screen.y)
        }
        t += tStep
    }
    drawPath(
        path = path,
        color = color.runtimeDrawColor(),
        style = strokeForStyle(
            strokeWidthPx = strokeWidth,
            lineStyle = lineStyle,
            phase = 0f,
            scale = scale
        )
    )
}


fun DrawScope.drawConicParabolaNarys(
    vertex: Offset,
    focus: Offset,
    canvasOffset: Offset,
    scale: Float,
    color: Color = Color.Black,
    strokeWidth: Float = 1.5f,
    lineStyle: LineStyle = LineStyle.Solid,
    tStep: Float = 0.5f,
    // ⬇️ NEW
    degenerateRay: Boolean = false,
    rayExtendFactor: Float = 2f
) {
    val axis = focus - vertex
    val p = axis.getDistance()
    if (p < 1e-6f) return

    val dir = Offset(axis.x / p, axis.y / p)

    if (degenerateRay) {
        // ⚑ polopřímka z vrcholu ve směru dir (pozor na převod do nárysu – invertuje se Y)
        val rayLenPx = max(size.width, size.height) * rayExtendFactor
        val rayEndWorld = vertex + dir * (rayLenPx / max(1e-6f, scale))

        val a = Offset(vertex.x, -vertex.y).toScreenOld(scale, canvasOffset)
        val b = Offset(rayEndWorld.x, -rayEndWorld.y).toScreenOld(scale, canvasOffset)


        val pathEffect = lineStyleDashPathEffectPx(lineStyle, scale = scale)

        drawLine(
            color = color.runtimeDrawColor(),
            start = a,
            end = b,
            strokeWidth = strokeWidth,
            pathEffect = pathEffect,
            cap = if (lineStyle == LineStyle.Solid) StrokeCap.Butt else StrokeCap.Round
        )
        return
    }

    // ▼ původní kreslení paraboly + pár ochran (a jednotná logika s XY variantou)
    val normal = Offset(-dir.y, dir.x)

    val worldSpan = max(size.width, size.height) / max(1e-6f, scale)
    val maxT = min(50f * sqrt(p), 2f * sqrt(worldSpan * 4f * p))  // bezpečnější limit
    val tStart = -maxT
    val tEnd   = +maxT

    val path = Path()
    var started = false

    var t = tStart
    while (t <= tEnd) {
        val logical = vertex + normal * t + dir * (t * t / (4f * p))
        // nárys = invertovat Y před mapováním na screen
        val screen = Offset(logical.x, -logical.y).toScreenOld(scale, canvasOffset)

        if (!started) {
            path.moveTo(screen.x, screen.y)
            started = true
        } else {
            path.lineTo(screen.x, screen.y)
        }
        t += tStep
    }

    drawPath(
        path = path,
        color = color.runtimeDrawColor(),
        style = strokeForStyle(
            strokeWidthPx = strokeWidth,
            lineStyle = lineStyle,
            phase = 0f,
            scale = scale
        )
    )
}
fun strokeForStyle(
    strokeWidthPx: Float,
    lineStyle: LineStyle,
    @Suppress("UNUSED_PARAMETER")
    dashMul: Float = 1.0f,
    @Suppress("UNUSED_PARAMETER")
    gapMul: Float = 1.0f,
    phase: Float = 0f,
    scale: Float = 1f
): Stroke {
    return Stroke(
        width = strokeWidthPx,
        pathEffect = lineStyleDashPathEffectPx(lineStyle, phase = phase, scale = scale),
        cap = if (lineStyle == LineStyle.Solid) StrokeCap.Butt else StrokeCap.Round,
        join = StrokeJoin.Round
    )
}