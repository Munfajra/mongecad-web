package draw.mongescreen.previews.points

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.DrawingModeMonge
import model.Mongeobjects
import model.ProjectionType
import state.MongeState
import utils.toScreenOld


//náhledová svislá čára při výběru průmětů
fun DrawScope.drawVerticalHelperLine(state: MongeState) {
    if (
        (state.mongeMode == DrawingModeMonge.NARYS || state.mongeMode == DrawingModeMonge.PUDORYS) &&
        state.projekcnityp== ProjectionType.ASSOCIATED &&(
        state.drawobjects == Mongeobjects.POINTS &&
        (state.projectionPhase == "pudorys_to_narys_point" || state.projectionPhase == "narys_to_pudorys_point") &&
        state.pendingX != null
    ) || state.drawobjects == Mongeobjects.LINES&& (state.projectionPhase == "special_case_point_in_narys"||state.projectionPhase == "special_case_point_in_pudorys")
        && (state.pendingXnarys != null || state.pendingXpudorys != null)) {

        val pendingX = state.pendingX?: state.pendingXnarys?: state.pendingXpudorys?: return
        val x = Offset(pendingX, 0f).toScreenOld(state.scale, state.canvasOffset).x
        val dashLength = 10f
        val gapLength = 6f
        val maxY = size.height

        var currentY = 0f
        while (currentY < maxY) {
            val endY = (currentY + dashLength).coerceAtMost(maxY)
            drawLine(
                color = Color.Gray,
                start = Offset(x, currentY),
                end = Offset(x, endY),
                strokeWidth = 1f,
            )
            currentY += dashLength + gapLength
        }
    }
}