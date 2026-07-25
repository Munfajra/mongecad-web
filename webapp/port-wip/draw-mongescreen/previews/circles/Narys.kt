package draw.mongescreen.previews.circles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import model.*
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.drawCirclePreviewNarys(state: MongeState, snappedPointLogical: Offset?) {
    if (
        state.drawobjects == Mongeobjects.CIRCLE &&
        state.mongeMode == DrawingModeMonge.NARYS &&
        (state.projekcnityp == ProjectionType.SINGLE||state.projekcnityp == ProjectionType.AUXILIARY) &&
        state.pendingPoint1 != null
    ) {
        val center = state.pendingPoint1!!

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

        val radius = (cursorLogical - center).getDistance()
        val screenCenter = center * state.scale + state.canvasOffset
        val screenRadius = radius * state.scale

        // Šedá čárkovaná kružnice
        drawCircle(
            color = Color.Gray,
            radius = screenRadius,
            center = screenCenter,
            style = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        )

        // Červený křížek ve středu
        val crossSize = 6f
        drawLine(
            color = Color.Red,
            start = screenCenter + Offset(-crossSize, 0f),
            end = screenCenter + Offset(crossSize, 0f),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.Red,
            start = screenCenter + Offset(0f, -crossSize),
            end = screenCenter + Offset(0f, crossSize),
            strokeWidth = 2f
        )
    }
}
