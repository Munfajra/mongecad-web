package draw.mongescreen.previews.traces.pudorys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import model.ConstructionModifier
import model.DrawingModeMonge
import model.Mongeobjects
import state.MongeState

fun DrawScope.previewPudorysTraceTemporary (state: MongeState) {
    if ((( state.projectionPhase == "plane_trace_narys_direction"||   state.projectionPhase == "plane_trace_narys_special_direction") ) &&
        state.tracePlanePudorys != null) {

        val start = state.tracePlanePudorys!!.point

        val dir =  state.tracePlanePudorys!!.direction
        val dirLength = dir.getDistance()
        val dirNorm = if (dirLength < 1e-6f) Offset.Zero else dir / dirLength

        val previewLengthWorld = 1000f /  state.scale

        val cursorWorld = Offset(
            x = start.x + dirNorm.x * previewLengthWorld,
            y = start.y + dirNorm.y * previewLengthWorld
        )

        drawDashedPreviewLinePudorys(
            start = start,
            cursorWorld = cursorWorld,
            scale =  state.scale,
            canvasOffset =  state.canvasOffset,
            clipToBelowX12 = true,
            color = Color.Gray,
        )
    }
}
fun DrawScope.previewPudorysTraceParallelTemporary(state: MongeState) {
    val baseDirection = state.selectedLineForParallelPlanePudorys?.direction
        ?: state.selectedLinesPudorys.firstOrNull()?.direction
        ?: state.selectedSegmentForParallelPudorys?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.y - it.start.y
            )
        }
        ?: state.selectedSegmentsPudorys.firstOrNull()?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.y - it.start.y
            )
        }

    val effectiveDirection = when (state.constructionModifier) {
        ConstructionModifier.ORTHOGONAL -> baseDirection?.let { Offset(-it.y, it.x) }
        ConstructionModifier.PARALLEL -> baseDirection
        else -> null
    }

    if (
        (state.projectionPhase == "plane_trace_pudorys_direction"|| state.projectionPhase == "plane_trace_pudorys_special_direction") &&
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.xOnX12Pudorys != null &&
        (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL) &&
        effectiveDirection != null
    ) {
        val through = state.xOnX12Pudorys
        drawDashedParallelLinePreviewPudorys(
            through = through!!,
            direction = effectiveDirection,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            clipToBelowX12 = true,
            color = Color.Gray,
        )
    }
}
