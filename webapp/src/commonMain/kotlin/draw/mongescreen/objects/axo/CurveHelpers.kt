package draw.mongescreen.objects.axo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import draw.mongescreen.lineStyleDashPathEffectPx
import geometry.drawSmoothCurve
import model.runtimeDrawColor
import model.LineStyle
import model.classes.CurveNarys
import model.classes.CurvePudorys
import state.MongeState

/**
 * Pomocné funkce pro křivky (hover/výběr a kreslení cesty).
 * Na desktopu jsou uvnitř axo vykreslování, ale s axonometrií nesouvisí –
 * volá je i ortogonální drawCurves.
 */
fun isCurveHoveredNarys(state: MongeState, curve: CurveNarys) =
    state.snappedCurveNarys?.id == curve.id

fun isCurveSelectedNarys(state: MongeState, curve: CurveNarys): Boolean {
    if (state.selectedCurveNarysId == curve.id) return true

    val pid = curve.parentId ?: curve.parent?.id
    return pid != null && (state.selectedCurve3DId == pid || state.selectedRuledSurfaceId == pid)
}

fun isCurveHoveredPudorys(state: MongeState, curve: CurvePudorys) =
    state.snappedCurvePudorys?.id == curve.id

fun isCurveSelectedPudorys(state: MongeState, curve: CurvePudorys): Boolean {
    if (state.selectedCurvePudorysId == curve.id) return true

    val pid = curve.parentId ?: curve.parent?.id
    return pid != null && (state.selectedCurve3DId == pid || state.selectedRuledSurfaceId == pid)
}

fun DrawScope.drawCurvePath(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    closed: Boolean,
    scale: Float,
    bakedPolyline: Boolean,
) {
    if (bakedPolyline) {
        drawPolylineCurve(points, color, strokeWidth, lineStyle, closed, scale)
    } else {
        drawSmoothCurve(points, color, strokeWidth, lineStyle, closed, 18, scale)
    }
}

fun DrawScope.drawPolylineCurve(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    closed: Boolean = false,
    scale: Float = 1f
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
        if (closed) close()
    }
    drawPath(
        path = path,
        color = color.runtimeDrawColor(),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = lineStyleDashPathEffectPx(lineStyle, scale = scale)
        )
    )
}

