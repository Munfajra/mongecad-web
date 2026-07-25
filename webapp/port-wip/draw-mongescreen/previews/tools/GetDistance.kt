package draw.mongescreen.previews.tools

import monge.input.axo.axoOverlayToScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.axo.drawAOSegmentOnScreen
import draw.mongescreen.previews.segments.AO.drawAOPreviewCross
import model.*
import monge.input.axo.points.screenToAxoOverlayLocal
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
//-------------------------AXO
fun DrawScope.getDistanceFirstPointCrossAxo(state: MongeState) {
    val basis = state.basis ?: return

    if (
        (state.drawobjects == Mongeobjects.GETDISTANCE &&
                state.projectionPhase == "distance_point2_select_axo") ||
        state.projectionPhase == "get_kota_p1_axo"
    ) {
        val logical = state.pendingPoint1 ?: return

        drawAOPreviewCross(
            logical,
            state,
            basis,
            Color.Red
        )
    }
}
fun DrawScope.getDistanceSecondPointSegmentToCursorAxo(
    state: MongeState
) {
    val basis = state.basis ?: return

    if (
        (state.drawobjects == Mongeobjects.GETDISTANCE &&
                state.projectionPhase == "distance_point2_select_axo") ||
        state.projectionPhase == "get_kota_p1_axo"
    ) {
        val startLogical = state.pendingPoint1 ?: return

        val cursorLogical = state.snappedPointLogical
            ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

        drawAOSegmentOnScreen(
            state = state,
            startLocal = startLogical,
            endLocal = cursorLogical,
            color = Color.Red,
            lineWidth = 3f,
            lineStyle = LineStyle.Solid,
            pxPerPt = 1f
        )
    }
}
fun DrawScope.drawAODashedCircleOnScreen(
    state: MongeState,
    centerLocal: Offset,
    radiusLocal: Float,
    color: Color = Color.Gray,
    strokeWidth: Float = 1.5f,
    steps: Int = 96
) {
    val basis = state.basis ?: return
    if (radiusLocal < 1e-6f) return

    val path = Path()

    for (i in 0..steps) {
        val a = (2.0 * kotlin.math.PI * i / steps).toFloat()

        val local = centerLocal + Offset(
            x = cos(a) * radiusLocal,
            y = sin(a) * radiusLocal
        )

        val screen = axoOverlayToScreen(
            local = local,
            state = state,
            basis = basis
        )

        if (i == 0) {
            path.moveTo(screen.x, screen.y)
        } else {
            path.lineTo(screen.x, screen.y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    )
}
fun DrawScope.getDistancePointCircleAxo(
    state: MongeState
) {
    val basis = state.basis ?: return

    if (
        state.drawobjects == Mongeobjects.GETDISTANCE &&
        state.projectionPhase == "distance_target_place_axo" &&
        state.pendingPoint3 != null
    ) {
        val centerLogical = state.pendingPoint3!!

        val cursorLogical = state.snappedPointLogical
            ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

        val baseVector = cursorLogical - centerLogical
        val baseLength = baseVector.getDistance()

        val direction = if (baseLength < 1e-6f) {
            Offset(1f, 0f)
        } else {
            val unit = baseVector / baseLength

            if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
                if (state.arc.arcDirectionClockwise) {
                    Offset(unit.y, -unit.x)
                } else {
                    Offset(-unit.y, unit.x)
                }
            } else {
                unit
            }
        }

        val endLogical = centerLogical + direction * baseLength

        drawAODashedCircleOnScreen(
            state = state,
            centerLocal = centerLogical,
            radiusLocal = baseLength,
            color = Color.Gray,
            strokeWidth = 1.5f
        )

        drawAOSegmentOnScreen(
            state = state,
            startLocal = centerLogical,
            endLocal = endLogical,
            color = Color.Red,
            lineWidth = 3f,
            lineStyle = LineStyle.Solid,
            pxPerPt = 1f
        )
    }
}
fun DrawScope.getDistancePointCircleCursorAxo(
    state: MongeState
) {
    val basis = state.basis ?: return

    if (
        state.drawobjects == Mongeobjects.GETDISTANCE &&
        state.projectionPhase == "distance_point3_select_axo" &&
        state.pendingDistance != null
    ) {
        val cursorLogical = state.snappedPointLogical
            ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

        drawAODashedCircleOnScreen(
            state = state,
            centerLocal = cursorLogical,
            radiusLocal = state.pendingDistance!!,
            color = Color.Gray,
            strokeWidth = 1.5f
        )
    }
}
fun DrawScope.handleGetDistanceDrawAxo(state: MongeState) {
    getDistanceFirstPointCrossAxo(state)
    getDistanceSecondPointSegmentToCursorAxo(state)
    getDistancePointCircleCursorAxo(state)
    getDistancePointCircleAxo(state)
}