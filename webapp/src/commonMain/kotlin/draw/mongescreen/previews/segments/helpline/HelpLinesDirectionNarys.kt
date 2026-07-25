package draw.mongescreen.previews.segments.helpline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewTemporarySegmentLineNarys(state: MongeState) {
    val origin = state.pendingLinePointNarys ?: return
    val dir = state.pendingDirectionNarys ?: return

    if (state.projectionPhase in listOf(
        "segment_orthogonal_first_point_narys_pudorys",
        "segment_orthogonal_first_point_narys",
        "segment_orthogonal_second_point_narys",
        "segment_parallel_first_point_narys",
        "segment_parallel_second_point_narys",
            "narys_segment_associated_B_narys_start_orthogonal",
            "trans_parallel_final_point_narys"

    )){

    val length = dir.getDistance()
    if (length < 1e-6f) return

    val unitX = dir.x / length
    val unitY = -dir.y / length

    val cursorWorld = Offset(origin.x + unitX,- origin.y + unitY)
    val start = Point3DPudorys(origin.x,- origin.y, name = "?")

    drawDashedPreviewLinePudorys(
        start = start,
        cursorWorld = cursorWorld,
        color= Color.Gray,
        scale = state.scale,
        canvasOffset = state.canvasOffset,
        clipToBelowX12 = false // nebo true, pokud chceš ořezat pod x₁₂
    )
}}

fun DrawScope.previewTemporarySegmentLinePlacementNarys(state: MongeState, snappedPointLogical: Offset?) {
    val dir = state.pendingDirectionNarys ?: return
    if (state.projectionPhase !in listOf(
            "segment_parallel_place_line_narys",
            "segment_orthogonal_place_line_narys"
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

    val z = -logical.y
    val start = Point3DNarys(logical.x, z, name = "?")
    val cursorWorld = Offset(logical.x + dir.x / length, z + dir.y / length)

    drawDashedPreviewLineNarys(
        start = start,
        cursorWorld = cursorWorld,
        color = Color.Gray,
        scale = state.scale,
        canvasOffset = state.canvasOffset,
        clipToAboveZ = false
    )
}
