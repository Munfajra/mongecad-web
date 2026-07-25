package draw.mongescreen.previews.lines.pudorys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import model.classes.Point3DPudorys
import state.MongeState

fun DrawScope.previewAssociatedPudorysLineTemporary(state: MongeState) {
    if (state.projectionPhase in listOf(
            "projection_line_start_narys",
            "projection_line_narys_dir",
            "parallel_line_point_selection_narys_pudorys_start",
            "orthogonal_line_point_selection_narys_pudorys_start"
        ) &&
        state.pendingXpudorys != null && state.pendingY != null && state.pendingDirection != null
    ) {

        val start = Point3DPudorys(state.pendingXpudorys!!, state.pendingY!!, name = "")

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
