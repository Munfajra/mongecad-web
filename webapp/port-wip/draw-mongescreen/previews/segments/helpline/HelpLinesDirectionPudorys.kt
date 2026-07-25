package draw.mongescreen.previews.segments.helpline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import model.classes.Point3DPudorys
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewTemporarySegmentLinePudorys(state: MongeState) {
    val origin = state.pendingLinePointPudorys ?: return
    val dir = state.pendingDirection ?: return

    if (state.projectionPhase in listOf(
            "segment_orthogonal_first_point_pudorys_narys",
            "segment_orthogonal_first_point_pudorys",
            "segment_orthogonal_second_point_pudorys",
            "segment_parallel_first_point_pudorys",
            "segment_parallel_second_point_pudorys",
            "pudorys_segment_associated_B_pudorys_start_orthogonal",
            "trans_parallel_final_point_pudorys"
        )){

    val length = dir.getDistance()
    if (length < 1e-6f) return

    val unitX = dir.x / length
    val unitY = dir.y / length

    val cursorWorld = Offset(origin.x + unitX, origin.y + unitY)
    val start = Point3DPudorys(origin.x, origin.y, name = "?")

    drawDashedPreviewLinePudorys(
        start = start,
        cursorWorld = cursorWorld,
        scale = state.scale,
        color= Color.Gray,
        canvasOffset = state.canvasOffset,
        clipToBelowX12 = false

    )
}}

fun DrawScope.previewTemporarySegmentLinePlacementPudorys(state: MongeState, snappedPointLogical: Offset?) {
    val dir = state.pendingDirection ?: return
    if (state.projectionPhase !in listOf(
            "segment_parallel_place_line_pudorys",
            "segment_orthogonal_place_line_pudorys"
        )
    ) return

    val logical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == model.XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == model.YAxisDirectionPlane.POSITIVE_UP
    )
    val length = dir.getDistance()
    if (length < 1e-6f) return

    val start = Point3DPudorys(logical.x, logical.y, name = "?")
    val cursorWorld = Offset(logical.x + dir.x / length, logical.y + dir.y / length)

    drawDashedPreviewLinePudorys(
        start = start,
        cursorWorld = cursorWorld,
        scale = state.scale,
        color = Color.Gray,
        canvasOffset = state.canvasOffset,
        clipToBelowX12 = false
    )
}
