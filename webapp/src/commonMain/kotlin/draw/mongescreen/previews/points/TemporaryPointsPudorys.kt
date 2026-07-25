package draw.mongescreen.previews.points

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.Mongeobjects
import model.ProjectionType
import state.MongeState
import utils.toScreenOld

fun DrawScope.drawPendingSegmentStartPudorys(state: MongeState) {
    val point = state.segmentStartPudorys ?: return
    val center = Offset(point.x, point.y).toScreenOld(state.scale, state.canvasOffset)

    val size = 6f
    drawLine(
        color = Color.Red,
        start = center - Offset(size, 0f),
        end = center + Offset(size, 0f),
        strokeWidth = 2f
    )
    // Vertikální čára
    drawLine(
        color = Color.Red,
        start = center - Offset(0f, size),
        end = center + Offset(0f, size),
        strokeWidth = 2f
    )
}
//náhledové křížky při sdružených průmětech bodu
fun DrawScope.drawPreviewPointPudorys(state: MongeState) {
    if ((
        state.projectionPhase == "pudorys_to_narys_point" &&
        state.pendingX != null && state.pendingY != null &&
        state.drawobjects == Mongeobjects.POINTS&&
        state.projekcnityp== ProjectionType.ASSOCIATED
    )||(state.pendingX != null && state.pendingY != null && state.projectionPhase in listOf("get_kota","get_kota_p1","KOTO_point"))) {
        val px = state.pendingX!!
        val py = state.pendingY!!

        val preview = Offset(px, py).toScreenOld(state.scale, state.canvasOffset)
        val size = 10f

        drawLine(
            color = Color.Red,
            start = Offset(preview.x - size, preview.y),
            end = Offset(preview.x + size, preview.y),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.Red,
            start = Offset(preview.x, preview.y - size),
            end = Offset(preview.x, preview.y + size),
            strokeWidth = 2f
        )
    }
}
fun DrawScope.drawPreviewPointPudorysTransDir(state: MongeState) {
    if (
        state.projectionPhase ==  "trans_parallel_final_point_pudorys" &&
        state.pendingLinePointPudorys!= null &&
        (state.drawobjects == Mongeobjects.TRANSPARALLEL ||state.drawobjects == Mongeobjects.TRANSORTH)
                ) {
        val px = state.pendingLinePointPudorys!!.x
        val py = state.pendingLinePointPudorys!!.y

        val preview = Offset(px, py).toScreenOld(state.scale, state.canvasOffset)
        val size = 10f

        drawLine(
            color = Color.Red,
            start = Offset(preview.x - size, preview.y),
            end = Offset(preview.x + size, preview.y),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.Red,
            start = Offset(preview.x, preview.y - size),
            end = Offset(preview.x, preview.y + size),
            strokeWidth = 2f
        )
    }
}
