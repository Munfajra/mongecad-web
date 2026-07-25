package draw.mongescreen.previews.segments.narys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewNarys
import draw.mongescreen.previews.segments.pudorys.drawPreviewCross
import model.*
import model.classes.Point3DNarys
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewNarysSegmentCursor(state: MongeState,snappedPointLogical: Offset?){
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
    val cursorWorld = Offset(
        cursorLogical.x,
        cursorLogical.y)

    if (state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.segmentStartNarys != null
    ) {
        val projectedCursorWorld = if (state.projectionPhase in listOf(
                "segment_parallel_second_point_narys",
                "segment_orthogonal_second_point_narys",
                "narys_segment_associated_B_narys_start_orthogonal"
            )
        ) {
            val origin = state.pendingLinePointNarys
            val direction = state.pendingDirectionNarys
            if (origin != null && direction != null) {
                projectPointOntoDirectionLineNarys(
                    point = Offset(cursorWorld.x, -cursorWorld.y),
                    origin = origin,
                    direction = direction
                )?.let { Offset(it.x, -it.y) } ?: cursorWorld
            } else {
                cursorWorld
            }
        } else {
            cursorWorld
        }

        drawDashedSegmentPreviewNarys(
            start = state.segmentStartNarys!!,
            cursorWorld = projectedCursorWorld,
            scale = state.scale,
            dashLength = 1f,
            gapLength= 0f,
            color = Color.Red,
            strokeWidth = 3f,
            canvasOffset = state.canvasOffset
        )
    }
}

private fun projectPointOntoDirectionLineNarys(
    point: Offset,
    origin: Offset,
    direction: Offset
): Offset? {
    val length = direction.getDistance()
    if (length < 1e-6f) return null
    val unit = direction / length
    val delta = point - origin
    val t = delta.x * unit.x + delta.y * unit.y
    return origin + unit * t
}
fun DrawScope.previewAssociatedNarysSegmentCursor(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "narys_segment_associated_B_narys_start",
            "narys_segment_associated_B_pudorys_start"
        )
    ) {
        val xA = state.pendingXA
        val zA = state.pendingZA
        if (xA != null && zA != null) {
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

            val cursorWorld = when (state.projectionPhase) {
                "narys_segment_associated_B_pudorys_start" -> Offset(cursorLogical.x, cursorLogical.y)
                else -> Offset(cursorLogical.x, cursorLogical.y)
            }

            val start = Point3DNarys(xA, zA, name = "")
            drawDashedSegmentPreviewNarys(
                start = start,
                cursorWorld = cursorWorld,
                scale = state.scale,
                dashLength = 1f,
                gapLength= 0f,
                color = Color.Red,
                strokeWidth = 3f,
                canvasOffset = state.canvasOffset
            )
        }
    }
}




// Vyříznuto: previewAxoNarysSegmentCursor, previewAxoNarysSegmentOnLineCursor – axo/AO varianty; web axonometrii nekreslí.