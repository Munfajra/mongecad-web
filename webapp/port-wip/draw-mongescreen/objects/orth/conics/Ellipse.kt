package draw.mongescreen.objects.orth.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.*
import draw.mongescreen.objects.axo.conics.degenerateEllipseCarrier
import draw.mongescreen.objects.axo.conics.ellipseDrawBasisFromDiameters
import draw.mongescreen.objects.axo.conics.isDegenerateEllipseAxes
import model.ArcMode
import model.LineStyle
import model.runtimeDrawColor
import monge.input.ConicArcs.single.ellipseBasisFromDiameters
import monge.input.ConicArcs.single.ellipseParamAndProjection
import utils.dot
import utils.toScreenOld
import kotlin.math.*

private fun DrawScope.drawDegenerateEllipse(
    points: List<Offset>,
    scale: Float,
    canvasOffset: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    arcEnds: Pair<Offset, Offset>? = null
) {
    val (carrierA, carrierB) = degenerateEllipseCarrier(points)
    val length = (carrierB - carrierA).getDistance()

    if (length < ELLIPSE_EPS) {
        drawCircle(
            color = color.runtimeDrawColor(),
            radius = max(1f, strokeWidth * 0.5f),
            center = carrierA.toScreenOld(scale, canvasOffset)
        )
        return
    }

    val (a, b) = arcEnds
        ?.let { (a, b) -> projectToSegmentCarrier(a, carrierA, carrierB) to projectToSegmentCarrier(b, carrierA, carrierB) }
        ?: (carrierA to carrierB)

    drawLine(
        color = color.runtimeDrawColor(),
        start = a.toScreenOld(scale, canvasOffset),
        end = b.toScreenOld(scale, canvasOffset),
        strokeWidth = strokeWidth,
        pathEffect = pathEffectFor(lineStyle, scale),
        cap = StrokeCap.Round
    )
}
fun DrawScope.drawEllipseFromDiameters(
    p1: Offset,
    p2: Offset,
    p3: Offset,
    scale: Float,
    canvasOffset: Offset,
    color: Color = Color.Black,
    strokeWidth: Float = 1.5f,
    lineStyle: LineStyle = LineStyle.Solid
) {
    val drawBasis = ellipseDrawBasisFromDiameters(p1, p2, p3)
    if (!drawBasis.isRegular) {
        drawDegenerateEllipse(listOf(p1, p2, p3), scale, canvasOffset, color, strokeWidth, lineStyle)
        return
    }

    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

    val u = (p2 - p1) / 2f
    val a = u.getDistance()

    val mirrorP3 = Offset(2 * center.x - p3.x, 2 * center.y - p3.y)
    val v = (p3 - mirrorP3) / 2f
    val b = v.getDistance()

    // Degenerace: elipsa se promítla na úsečku / bod.
    if (isDegenerateEllipseAxes(u, a, v, b)) {
        drawDegenerateEllipse(listOf(p1, p2, p3), scale, canvasOffset, color, strokeWidth, lineStyle)
        return
    }

    val uNorm = Offset(u.x / a, u.y / a)
    val vNorm = Offset(v.x / b, v.y / b)

    // ✅ pojistka na NaN/Inf
    if (!uNorm.x.isFiniteF() || !uNorm.y.isFiniteF() || !vNorm.x.isFiniteF() || !vNorm.y.isFiniteF()) {
        return
    }

    val path = Path()
    val steps = 1000
    for (i in 0..steps) {
        val t = 2f * PI.toFloat() * i / steps
        val point = center + Offset(
            a * cos(t) * uNorm.x + b * sin(t) * vNorm.x,
            a * cos(t) * uNorm.y + b * sin(t) * vNorm.y
        )
        val screen = point.toScreenOld(scale, canvasOffset)
        if (i == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }

    val pathEffect = when (lineStyle) {
        LineStyle.Solid -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot -> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }

    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = pathEffect))
}
fun DrawScope.drawEllipseArcFromDiameters(
    p1: Offset, p2: Offset, p3: Offset,
    A: Offset, B: Offset,
    mode: ArcMode = ArcMode.SHORTEST,
    scale: Float,
    canvasOffset: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    through: Offset? = null
) {
    // --- stejný úvod jako máš ---
    val drawBasis = ellipseDrawBasisFromDiameters(p1, p2, p3)
    if (!drawBasis.isRegular) {
        drawDegenerateEllipseArcFromDiameters(
            p1 = p1,
            p2 = p2,
            p3 = p3,
            A = A,
            B = B,
            mode = mode,
            scale = scale,
            canvasOffset = canvasOffset,
            color = color.runtimeDrawColor(),
            strokeWidth = strokeWidth,
            lineStyle = lineStyle
        )
        return
    }

    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val u = (Offset(p2.x - p1.x, p2.y - p1.y)) / 2f
    val a = u.getDistance().coerceAtLeast(1e-6f)
    val mirrorP3 = Offset(2 * center.x - p3.x, 2 * center.y - p3.y)
    val v = (Offset(p3.x - mirrorP3.x, p3.y - mirrorP3.y)) / 2f
    val b = v.getDistance().coerceAtLeast(1e-6f)
    val uN = Offset(u.x / a, u.y / a)
    val vN = Offset(v.x / b, v.y / b)

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
    fun containsOnCCW(a1: Float, len: Float, t: Float): Boolean {
        val twoPi = (2f * PI).toFloat()
        val x = normAngle(t) - normAngle(a1)
        val dx = if (x < 0f) x + twoPi else x
        return dx in 0f..len
    }
    fun pickIntervalByMode(t1: Float, t2: Float): Pair<Float, Float> {
        val twoPi = (2f * PI).toFloat()
        val a1 = normAngle(t1); val a2 = normAngle(t2)
        val ccw = ccwSpan(a1, a2)
        val cw  = twoPi - ccw
        return when (mode) {
            ArcMode.CCW      -> a1 to (a1 + ccw)
            ArcMode.CW       -> a1 to (a1 - cw)
            ArcMode.SHORTEST -> if (ccw <= cw) a1 to (a1 + ccw) else a1 to (a1 - cw)
            ArcMode.LONGEST  -> if (ccw >= cw) a1 to (a1 + ccw) else a1 to (a1 - cw)
        }
    }

    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    val (tA, Aon) = ellipseParamAndProjection(basis, A)
    val (tB, Bon) = ellipseParamAndProjection(basis, B)

    // ⬇️ KLÍČ: když je „through“, vyber půlku, která ho obsahuje
    val (tStart, tEnd) = if (through != null) {
        val (tT, _) = ellipseParamAndProjection(basis, through)
        val twoPi = (2f * PI).toFloat()

        // kandidát 1: půlka A → A+π (CCW)
        val half = PI.toFloat()
        val cand1Start = normAngle(tA)
        val cand1End   = cand1Start + half
        val tEnd1 = cand1End

        // kandidát 2: půlka B → B+π (CCW) je totéž jako A → A-π (CW)
        val cand2Start = normAngle(tB)
        val cand2End   = cand2Start + half
        val tEnd2 = cand2End

        when {
            containsOnCCW(cand1Start, half, tT) -> cand1Start to tEnd1
            containsOnCCW(cand2Start, half, tT) -> cand2Start to tEnd2
            else -> {
                // fallback kdy A,B nejsou přesně antipodní: vezmi směr A→B, který prochází through
                val ccw = ccwSpan(tA, tB)
                val a1 = normAngle(tA)
                val a2 = normAngle(tB)
                val inABccw = containsOnCCW(a1, ccw, tT)
                val inBAccw = containsOnCCW(a2, twoPi - ccw, tT)
                when {
                    inABccw -> a1 to (a1 + ccw)
                    inBAccw -> a2 to (a2 + (twoPi - ccw))
                    else    -> pickIntervalByMode(tA, tB) // poslední záchrana
                }
            }
        }
    } else {
        pickIntervalByMode(tA, tB)
    }

    val dir = if (tEnd >= tStart) +1f else -1f
    val length = abs(tEnd - tStart)
    val steps = max(2, ceil(length / 0.05f).toInt())

    val path = Path()
    path.moveTo(Aon.toScreenOld(scale, canvasOffset).x, Aon.toScreenOld(scale, canvasOffset).y)
    for (i in 1 until steps) {
        val t = tStart + dir * (i * (length / steps))
        val cosT = cos(t)
        val sinT = sin(t)
        val pt = center + Offset(
            (a * cosT * uN.x + b * sinT * vN.x),
            (a * cosT * uN.y + b * sinT * vN.y)
        )
        val s = pt.toScreenOld(scale, canvasOffset)
        path.lineTo(s.x, s.y)
    }
    val sEnd = Bon.toScreenOld(scale, canvasOffset)
    path.lineTo(sEnd.x, sEnd.y)

    val pathEffect = when (lineStyle) {
        LineStyle.Solid  -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot-> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = pathEffect))
}
private fun DrawScope.drawDegenerateEllipseArcFromDiameters(
    p1: Offset,
    p2: Offset,
    p3: Offset,
    A: Offset,
    B: Offset,
    mode: ArcMode,
    scale: Float,
    canvasOffset: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val g = degenerateEllipseParamFromDiameters(p1, p2, p3)
    if (g == null) {
        drawDegenerateEllipse(listOf(p1, p2, p3), scale, canvasOffset, color, strokeWidth, lineStyle, A to B)
        return
    }

    fun pointAt(t: Float): Offset {
        val s = g.su * cos(t) + g.sv * sin(t)
        return g.center + g.dir * s
    }

    val (tStart, tEnd) = pickDegenerateInterval(
        degenerateParamCandidates(g, A),
        degenerateParamCandidates(g, B),
        mode
    )
    val length = abs(tEnd - tStart)
    val dirSign = if (tEnd >= tStart) 1f else -1f
    val steps = max(2, ceil(length / 0.05f).toInt())
    val path = Path()

    for (i in 0..steps) {
        val t = tStart + dirSign * (i * (length / steps))
        val s = pointAt(t).toScreenOld(scale, canvasOffset)
        if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
    }

    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(lineStyle, scale), cap = StrokeCap.Round))
}
fun degenerateEllipseParamFromDiameters(p1: Offset, p2: Offset, p3: Offset): DegenerateEllipseParam? {
    val basis = ellipseDrawBasisFromDiameters(p1, p2, p3)
    val dirRaw = if (basis.a >= basis.b) basis.u else basis.v
    val dirLen = dirRaw.getDistance()
    if (dirLen < ELLIPSE_EPS) return null

    val dir = dirRaw / dirLen
    val su = basis.u.dot(dir)
    val sv = basis.v.dot(dir)
    val radius = sqrt(su * su + sv * sv)
    if (!radius.isFiniteF() || radius < ELLIPSE_EPS) return null
    return DegenerateEllipseParam(basis.center, dir, radius, su, sv)
}
fun pickDegenerateInterval(
    candidatesA: List<Float>,
    candidatesB: List<Float>,
    mode: ArcMode
): Pair<Float, Float> {
    val twoPi = (2f * PI).toFloat()
    val intervals = candidatesA.flatMap { a ->
        candidatesB.map { b ->
            val ccw = ccwEllipseSpan(a, b)
            val cw = twoPi - ccw
            val interval = when (mode) {
                ArcMode.CCW -> a to (a + ccw)
                ArcMode.CW -> a to (a - cw)
                ArcMode.SHORTEST -> if (ccw <= cw) a to (a + ccw) else a to (a - cw)
                ArcMode.LONGEST -> if (ccw >= cw) a to (a + ccw) else a to (a - cw)
            }
            interval to abs(interval.second - interval.first)
        }
    }

    return when (mode) {
        ArcMode.LONGEST -> intervals.maxByOrNull { it.second }?.first
        else -> intervals.minByOrNull { it.second }?.first
    } ?: (candidatesA.first() to candidatesB.first())
}
fun degenerateParamCandidates(g: DegenerateEllipseParam, point: Offset): List<Float> {
    val s = (point - g.center).dot(g.dir).coerceIn(-g.radius, g.radius)
    val phase = atan2(g.sv, g.su)
    val base = acos((s / g.radius).coerceIn(-1f, 1f))
    return listOf(normEllipseAngle(phase + base), normEllipseAngle(phase - base))
}
private fun normEllipseAngle(t: Float): Float {
    val twoPi = (2f * PI).toFloat()
    var x = t % twoPi
    if (x < 0f) x += twoPi
    return x
}

private fun ccwEllipseSpan(a1: Float, a2: Float): Float {
    val x1 = normEllipseAngle(a1)
    val x2 = normEllipseAngle(a2)
    val twoPi = (2f * PI).toFloat()
    return if (x2 >= x1) x2 - x1 else x2 - x1 + twoPi
}




