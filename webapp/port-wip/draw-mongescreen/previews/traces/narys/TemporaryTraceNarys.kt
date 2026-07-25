package draw.mongescreen.previews.traces.narys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarys
import model.ConstructionModifier
import model.DrawingModeMonge
import model.Mongeobjects
import state.MongeState

fun DrawScope.PreviewNarysTraceParallelTemporary(state: MongeState) {
    val baseDirection = state.selectedLineForParallelPlaneNarys?.direction
        ?: state.selectedLinesNarys.firstOrNull()?.direction
        ?: state.selectedSegmentForParallelNarys?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.z - it.start.z
            )
        }
        ?: state.selectedSegmentsNarys.firstOrNull()?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.z - it.start.z
            )
        }

    val effectiveDirection = when (state.constructionModifier) {
        ConstructionModifier.ORTHOGONAL -> baseDirection?.let { Offset(-it.y, it.x) }
        ConstructionModifier.PARALLEL -> baseDirection
        else -> null
    }

    if (
        (state.projectionPhase == "plane_trace_narys_direction" ||   state.projectionPhase == "plane_trace_narys_special_direction")&&
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.xOnX12Narys != null &&
        (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL) &&
        effectiveDirection != null
    ) {
        val through = state.xOnX12Narys
        drawDashedParallelLinePreviewNarys(
            through = through!!,
            direction = effectiveDirection,
            scale = state.scale,
            color = Color.Gray,
            canvasOffset = state.canvasOffset,
            clipToAboveZ = true
        )
    }
}

fun DrawScope.PreviewNarysTraceTemporary(state: MongeState){
    if ((( state.projectionPhase == "plane_trace_pudorys_direction"||   state.projectionPhase == "plane_trace_pudorys_special_direction") ) &&
        state.tracePlaneNarys != null) {

        val start = state.tracePlaneNarys!!.point

        val dir =  state.tracePlaneNarys!!.direction
        val dirLength = dir.getDistance()
        val dirNorm = if (dirLength < 1e-6f) Offset.Zero else dir / dirLength

        val previewLengthWorld = 1000f /  state.scale

        val cursorWorld = Offset(
            x = start.x + dirNorm.x * previewLengthWorld,
            y = start.z + dirNorm.y * previewLengthWorld
        )

        drawDashedPreviewLineNarys(
            start = start,
            cursorWorld = cursorWorld,
            scale =  state.scale,
            color = Color.Gray,
            canvasOffset =  state.canvasOffset,
            clipToAboveZ = true
        )
    }
}