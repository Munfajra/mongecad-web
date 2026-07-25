package draw.mongescreen.previews.segments.pudorys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewPudorys
import model.*
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.ConicArcs.single.getLogicalCursorNarys
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewSinglePudorysLineCursorSegment(state: MongeState, snappedPointLogical: Offset?) {
    if (state.projectionPhase=="sol_pudorys_second_point_dir" && state.pendingLinePointPudorys!=null) {
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
            cursorLogical.y
        )
        val point1 = Point3DPudorys(state.pendingLinePointPudorys!!.x, state.pendingLinePointPudorys!!.y)
        drawDashedPreviewLinePudorys(
            start =  point1,
            cursorWorld = cursorWorld,
            scale =  state.scale,
            canvasOffset =  state.canvasOffset,
            color = Color.Gray
        )
    }
}
fun DrawScope.previewAssociatedPudorysLineTemporarySegment(state: MongeState) {
    if ((state.projectionPhase == "sol_begin_segment_pud" || state.projectionPhase == "sol_second_point_pudorys") &&
        state.selectedLineForParallelPudorys != null &&  state.pendingPoint2 != null
    ) {
        val point= state.selectedLineForParallelPudorys!!.point
        val point2 = state.pendingPoint2
        val start = Point3DPudorys(point.x, point.y, name = "")

        val dir = state.pendingDirection!!
        val dirLength = dir.getDistance()
        val dirNorm = if (dirLength < 1e-6f) Offset.Zero else dir / dirLength

        val previewLengthWorld = 1000f / state.scale

        val cursorWorld = Offset(
            x = start.x + dirNorm.x * previewLengthWorld,
            y = start.y + dirNorm.y * previewLengthWorld
        )

        drawDashedPreviewLinePudorys(
            start = start,
            cursorWorld = cursorWorld,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            color = Color.Gray
        )
    }
}
fun DrawScope.previewPudorysLineSegmentCursor(state: MongeState, snappedPointLogical: Offset?){
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

    if (state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.SEGMENT_ON_LINE &&
        state.segmentStartPudorys != null
    ) {
        drawDashedSegmentPreviewPudorys(
            start = state.segmentStartPudorys!!,
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

fun DrawScope.previewSingleNarysLineCursorSegment(state: MongeState, snappedPointLogical: Offset?) {
    if (state.projectionPhase=="sol_narys_second_point_dir" && state.pendingLinePointNarys!=null) {
        val cursorLogical = getLogicalCursorNarys(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection
        )
        val cursorWorld = Offset(
            cursorLogical.x,
            cursorLogical.y
        )
        val point1 = Point3DNarys(state.pendingLinePointNarys!!.x, state.pendingLinePointNarys!!.y)
        drawDashedPreviewLineNarys(
            start =  point1,
            cursorWorld = cursorWorld,
            scale =  state.scale,
            canvasOffset =  state.canvasOffset,
            color = Color.Gray
        )
    }
}
fun DrawScope.previewAssociatedNarysLineTemporarySegment(state: MongeState) {
    if ((state.projectionPhase == "sol_begin_segment_nar" || state.projectionPhase == "sol_second_point_narys") &&
        state.selectedLineForParallelNarys != null &&  state.pendingPoint2 != null
    ) {
        val point= state.selectedLineForParallelNarys!!.point
        val point2 = state.pendingPoint2
        val start = Point3DNarys(point.x, point.z, name = "")

        val dir = state.pendingDirectionNarys!!
        val dirLength = dir.getDistance()
        val dirNorm = if (dirLength < 1e-6f) Offset.Zero else dir / dirLength

        val previewLengthWorld = 1000f / state.scale

        val cursorWorld = Offset(
            x = start.x + dirNorm.x * previewLengthWorld,
            y = start.z + dirNorm.y * previewLengthWorld
        )

        drawDashedPreviewLineNarys(
            start = start,
            cursorWorld = cursorWorld,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            color = Color.Gray
        )
    }
}
fun DrawScope.previewNarysLineSegmentCursor(state: MongeState, snappedPointLogical: Offset?){
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
        state.drawobjects == Mongeobjects.SEGMENT_ON_LINE &&
        state.segmentStartNarys != null
    ) {
        drawDashedSegmentPreviewNarys(
            start = state.segmentStartNarys!!,
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