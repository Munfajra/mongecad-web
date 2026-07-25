package draw.mongescreen.previews.tools

import monge.input.axo.axoOverlayToScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import model.*
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.getDistanceFirstPointCross (state: MongeState){
    if ((state.drawobjects == Mongeobjects.GETDISTANCE &&
        state.projectionPhase == "distance_point2_select"
                )|| state.projectionPhase == "get_kota_p1") {
        val logical = state.pendingPoint1 ?: return
        val screen = logical.toScreenOld(state.scale, state.canvasOffset)

        val crossSize = 6f // délka ramen kříže v pixelech

        drawLine(
            color = Color.Red,
            start = screen + Offset(-crossSize, 0f),
            end = screen + Offset(crossSize, 0f),
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.Red,
            start = screen + Offset(0f, -crossSize),
            end = screen + Offset(0f, crossSize),
            strokeWidth = 1.5f
        )
    }

}
fun DrawScope.getDistanceSecondPointSegmentToCursor(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    if ((
        state.drawobjects == Mongeobjects.GETDISTANCE &&
        state.projectionPhase == "distance_point2_select"
    )||state.projectionPhase == "get_kota_p1") {
        val startLogical = state.pendingPoint1 ?: return

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

        val startScreen = startLogical.toScreenOld(state.scale, state.canvasOffset)
        val endScreen = cursorLogical.toScreenOld(state.scale, state.canvasOffset)

        drawLine(
            color = Color.Red,
            start = startScreen,
            end = endScreen,
            strokeWidth = 3f
        )
    }
}
fun DrawScope.getDistancePointCircle(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    if (
        state.drawobjects == Mongeobjects.GETDISTANCE &&
        state.projectionPhase == "distance_target_place" &&
        state.pendingPoint3 != null
    ) {
        val centerLogical = state.pendingPoint3!!

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

        val baseVector = cursorLogical - centerLogical
        val baseLength = baseVector.getDistance()

        val direction = if (baseLength < 1e-6f) {
            Offset(1f, 0f) // fallback, kdyby kurzor byl přímo ve středu
        } else {
            val unit = baseVector / baseLength
            if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
                if (state.arc.arcDirectionClockwise) Offset(unit.y, -unit.x)
                else Offset(-unit.y, unit.x)
            } else {
                unit
            }
        }

        val endLogical = centerLogical + direction * baseLength
        val radiusPx = baseLength * state.scale

        val centerScreen = centerLogical.toScreenOld(state.scale, state.canvasOffset)
        val endScreen = endLogical.toScreenOld(state.scale, state.canvasOffset)

        val stroke = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        drawCircle(
            color = Color.Gray,
            radius = radiusPx,
            center = centerScreen,
            style = stroke
        )

        drawLine(
            color = Color.Red,
            start = centerScreen,
            end = endScreen,
            strokeWidth = 3f
        )
    }
}

fun DrawScope.getDistancePointCircleCursor(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    if (
        state.drawobjects == Mongeobjects.GETDISTANCE &&
        state.projectionPhase == "distance_point3_select" &&
        state.pendingDistance != null
    ) {
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

        val centerScreen = cursorLogical.toScreenOld(state.scale, state.canvasOffset)
        val radiusPx = state.pendingDistance!! * state.scale

        val stroke = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        drawCircle(
            color = Color.Gray,
            radius = radiusPx,
            center = centerScreen,
            style = stroke
        )
    }
}

fun DrawScope.handleGetDistanceDraw (state: MongeState,snappedPointLogical: Offset?) {
    getDistanceFirstPointCross(state)
    getDistanceSecondPointSegmentToCursor(state,snappedPointLogical)
    getDistancePointCircleCursor(state,snappedPointLogical)
    getDistancePointCircle(state,snappedPointLogical)
}

// Vyříznuto: getDistanceFirstPointCrossAxo, getDistanceSecondPointSegmentToCursorAxo, drawAODashedCircleOnScreen, getDistancePointCircleAxo, getDistancePointCircleCursorAxo, handleGetDistanceDrawAxo – axo/AO varianty; web axonometrii nekreslí.