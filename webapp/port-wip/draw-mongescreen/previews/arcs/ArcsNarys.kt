package draw.mongescreen.previews.arcs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import model.XAxisDirection
import model.YAxisDirectionPlane
import state.MongeState
import utils.getLogicalCursor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

fun DrawScope.drawArcPreviewRadiusNarys(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    val center = state.arc.arcCenterNarys ?: return
    if (state.arc.arcRadiusPointNarys != null) return

    val cursorLogical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val radius = hypot(cursorLogical.x - center.x, -cursorLogical.y - center.z)

    drawArc(
        color = Color.Gray,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(
            (center.x - radius) * state.scale,
            (-center.z - radius) * state.scale
        ) + state.canvasOffset,
        size = Size(2 * radius * state.scale, 2 * radius * state.scale),
        style = Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    )
}
fun DrawScope.drawArcPreviewEndNarys(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    val center = state.arc.arcCenterNarys ?: return
    val radiusPoint = state.arc.arcRadiusPointNarys ?: return

    val cursorLogical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    val radius = hypot(radiusPoint.x - center.x, radiusPoint.z - center.z)

    // ✅ 1) Šedá náhledová kružnice – ihned po zadání poloměru
    drawArc(
        color = Color.Gray,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(
            (center.x - radius) * state.scale,
            (-center.z - radius) * state.scale
        ) + state.canvasOffset,
        size = Size(2 * radius * state.scale, 2 * radius * state.scale),
        style = Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    )

    // ✅ 2) Červený oblouk – až po zadání počátečního bodu
    val start = state.arc.arcStartPointNarys ?: return

    val dx1 = start.x - center.x
    val dz1 = start.z - center.z
    val dx2 = cursorLogical.x - center.x
    val dz2 = -cursorLogical.y - center.z

    val angleStartRad = atan2(-dz1, dx1)
    val angleEndRad = atan2(-dz2, dx2)

    var sweepRad = angleEndRad - angleStartRad
    if (state.arc.arcDirectionClockwise) {
        if (sweepRad <= 0f) sweepRad += (2 * PI).toFloat()
    } else {
        if (sweepRad >= 0f) sweepRad -= (2 * PI).toFloat()
    }

    val angleStartDeg = ((angleStartRad.toDouble()) * 180.0 / kotlin.math.PI).toFloat()
    val sweepDeg = ((sweepRad.toDouble()) * 180.0 / kotlin.math.PI).toFloat()

    drawArc(
        color = Color.Red,
        startAngle = angleStartDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(
            (center.x - radius) * state.scale,
            (-center.z - radius) * state.scale
        ) + state.canvasOffset,
        size = Size(2 * radius * state.scale, 2 * radius * state.scale),
        style = Stroke(width = 4f)
    )
}
