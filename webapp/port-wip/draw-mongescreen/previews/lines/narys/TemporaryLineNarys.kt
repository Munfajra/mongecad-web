package draw.mongescreen.previews.lines.narys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarys
import model.classes.Point3DNarys
import state.MongeState

//sdružené průměty dočasná přímka v nárysu

fun DrawScope.previewAssociatedNarysLineTemporary(state: MongeState){
    if (state.projectionPhase in listOf(
            "projection_line_start_pudorys",
            "projection_line_start_pudorys_dir",
            "parallel_line_point_selection_pudorys_narys_start",
            "orthogonal_line_point_selection_pudorys_narys_start"
        ) &&
        state.pendingXnarys != null &&
        state.pendingZ != null &&
        state.pendingDirectionNarys != null
    ) {
        val start = Point3DNarys(state.pendingXnarys!!, state.pendingZ!!, name = "")
        val dir = state.pendingDirectionNarys
        val dirLength = dir!!.getDistance()
        val dirNorm = if (dirLength < 1e-6f) Offset.Zero else dir / dirLength

        val previewLengthWorld = 1000f / state.scale
        val cursorWorld = Offset(
            x = start.x + dirNorm.x * previewLengthWorld,
            y = start.z + dirNorm.y * previewLengthWorld
        )
        drawDashedPreviewLineNarys(start, cursorWorld, state.scale, state.canvasOffset,
            color = Color.Gray)
    }
}
