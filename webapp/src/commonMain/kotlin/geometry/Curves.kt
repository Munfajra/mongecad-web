package geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.lineStyleDashPathEffectPx
import model.LineStyle
import model.classes.CurvePudRef
import model.runtimeDrawColor
import state.MongeState
import kotlin.math.max
import kotlin.math.min

/*
 * Vzorkování a kreslení hladké křivky (Catmull-Rom) + převody souřadnic.
 * Struktura odpovídá desktopové `geometry/Curves.kt` a `geometry/ScreenProjection.kt`.
 */

fun sampleSmoothCurvePoints(
    points: List<Offset>,
    closed: Boolean,
    stepsPerSegment: Int = 18
): List<Offset> = sampleCatmullRom(points, stepsPerSegment, closed)

fun catmullRomPoint(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val t2 = t * t
    val t3 = t2 * t

    val x =
        0.5f * (
                (2f * p1.x) +
                        (-p0.x + p2.x) * t +
                        (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                        (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
                )

    val y =
        0.5f * (
                (2f * p1.y) +
                        (-p0.y + p2.y) * t +
                        (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                        (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
                )

    return Offset(x, y)
}

fun sampleCatmullRom(
    points: List<Offset>,
    stepsPerSegment: Int = 18,
    closed: Boolean = false
): List<Offset> {
    if (points.size < 2) return points
    if (points.size == 2) return points // jen úsečka

    val pts = points
    val out = ArrayList<Offset>(pts.size * stepsPerSegment)

    fun get(i: Int): Offset {
        val n = pts.size
        return if (closed) {
            pts[(i % n + n) % n]
        } else {
            pts[min(max(i, 0), n - 1)]
        }
    }

    val segCount = if (closed) pts.size else (pts.size - 1)

    for (i in 0 until segCount) {
        val p0 = get(i - 1)
        val p1 = get(i)
        val p2 = get(i + 1)
        val p3 = get(i + 2)

        val steps = max(1, stepsPerSegment)
        for (s in 0..steps) {
            val t = s / steps.toFloat()
            // U první segmentu u ne-closed přidej t=0 jen jednou, aby nebyly duplikáty
            if (!closed && i > 0 && s == 0) continue
            out.add(catmullRomPoint(p0, p1, p2, p3, t))
        }
    }

    return out
}


fun DrawScope.drawSmoothCurve(
    controlPoints: List<Offset>,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    closed: Boolean = false,
    stepsPerSegment: Int = 18,
    scale: Float = 1f
) {
    if (controlPoints.size < 2) return

    val sampled = sampleCatmullRom(
        points = controlPoints,
        stepsPerSegment = stepsPerSegment,
        closed = closed
    )

    val path = Path().apply {
        moveTo(sampled[0].x, sampled[0].y)
        for (i in 1 until sampled.size) {
            lineTo(sampled[i].x, sampled[i].y)
        }
        if (closed) close()
    }

    val pathEffect = lineStyleDashPathEffectPx(lineStyle, scale = scale)
    drawPath(
        path = path,
        color = color.runtimeDrawColor(),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = pathEffect
        )
    )
}

fun CurvePudRef.toLogical(state: MongeState): Offset? {
    return when (this) {
        is CurvePudRef.P -> {
            val p = state.pointsPudorys.find { it.id == pointId }
            p?.let { Offset(it.x, it.y) }
        }

        is CurvePudRef.A -> {
            val a = state.aidPointsLogical.find { it.id == aidId }
            a?.let { Offset(it.x, it.y) }
        }
    }
}



/**
 * Převod logické souřadnice na obrazovku.
 * Na desktopu je v `monge/input/tools/GetAngle.kt` – nástroj úhlu, ale
 * samotný převod s ním nesouvisí a používá ho i kreslení křivek.
 */
fun logicalToScreen(
    logical: Offset,
    canvasOffset: Offset,
    scale: Float
): Offset = Offset(
    x = logical.x * scale + canvasOffset.x,
    y = logical.y * scale + canvasOffset.y
)

