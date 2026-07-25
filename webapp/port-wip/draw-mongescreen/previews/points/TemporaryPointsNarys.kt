package draw.mongescreen.previews.points

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.Mongeobjects
import model.ProjectionType
import state.MongeState
import utils.toScreenOld

fun DrawScope.drawPendingAssociatedSegmentPoints(state: MongeState) {
    val scale = state.scale
    val offset = state.canvasOffset
    val size = 6f

    fun drawCross(center: Offset) {
        drawLine(
            color = Color.Red,
            start = center - Offset(size, size),
            end = center + Offset(size, size),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.Red,
            start = center + Offset(-size, size),
            end = center + Offset(size, -size),
            strokeWidth = 2f
        )
    }

    if (
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED
    ) {
        val phase = state.projectionPhase

        // === NÁRYS (A₂)
        if (phase in listOf(
                "narys_segment_associated_B_narys_start",
                "pudorys_segment_associated_A_narys_start",
                "pudorys_segment_associated_B_narys_start",
                "segment_parallel_place_line_pudorys",
                "segment_orthogonal_place_line_pudorys"
            ) || phase in listOf(
                "narys_segment_associated_B_pudorys_start",
                "pudorys_segment_associated_A_pudorys_start",
                "pudorys_segment_associated_B_pudorys_start"
            )
        ) {
            val xA = state.pendingXA
            val zA = state.pendingZA
            if (xA != null && zA != null) {
                val screen = Offset(xA, -zA).toScreenOld(scale, offset)
                drawCross(screen)
            }
        }

        // === NÁRYS (B₂)
        if (phase in listOf(
                "pudorys_segment_associated_A_narys_start",
                "pudorys_segment_associated_B_narys_start",
                "narys_segment_associated_A_pudorys_start",
                "segment_parallel_place_line_pudorys",
                "segment_orthogonal_place_line_pudorys"
            )
        ) {
            val xB = state.pendingXB
            val zB = state.pendingZB
            if (xB != null && zB != null) {
                val screen = Offset(xB, -zB).toScreenOld(scale, offset)
                drawCross(screen)
            }
        }

        // === PŮDORYS (A₁)
        if (phase in listOf(
                "pudorys_segment_associated_B_narys_start",
                "pudorys_segment_associated_B_pudorys_start",
                "narys_segment_associated_B_pudorys_start",
                "narys_segment_associated_A_pudorys_start",
                "segment_parallel_place_line_narys",
                "segment_orthogonal_place_line_narys")
        ) {
            val xA = state.pendingXA
            val yA = state.pendingYA
            if (xA != null && yA != null) {
                val screen = Offset(xA, yA).toScreenOld(scale, offset)
                drawCross(screen)
            }
        }

        // === PŮDORYS (B₁)
        if (phase in listOf(
                "narys_segment_associated_B_pudorys_start",
                "narys_segment_associated_A_pudorys_start",
                "narys_segment_associated_B_pudorys_start",
                "segment_parallel_place_line_narys",
                "segment_orthogonal_place_line_narys"

            )) {
            val xB = state.pendingXB
            val yB = state.pendingYB
            if (xB != null && yB != null) {
                val screen = Offset(xB, yB).toScreenOld(scale, offset)
                drawCross(screen)
            }
        }
    }
}

fun DrawScope.drawPendingSegmentStartNarys(state: MongeState) {
    val point = state.segmentStartNarys ?: return
    val center = Offset(
        x = point.x * state.scale + state.canvasOffset.x,
        y = -point.z * state.scale + state.canvasOffset.y
    )

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
fun DrawScope.drawPreviewPointNarys(state: MongeState) {
    if (
        state.projectionPhase == "narys_to_pudorys_point" &&
        state.pendingX != null && state.pendingZ != null &&
        state.drawobjects == Mongeobjects.POINTS &&
        state.projekcnityp== ProjectionType.ASSOCIATED
    ) {
        val px = state.pendingX!!
        val pz = -state.pendingZ!! // Z transformace do 2D (Compose má y dolů)

        val preview = Offset(px, pz).toScreenOld(state.scale, state.canvasOffset)
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
fun DrawScope.drawPreviewPointNarysTransDir(state: MongeState) {
    if (
        state.projectionPhase ==  "trans_parallel_final_point_narys" &&
        state.pendingLinePointNarys!= null &&
        (state.drawobjects == Mongeobjects.TRANSPARALLEL ||state.drawobjects == Mongeobjects.TRANSORTH)
    ) {
        val px = state.pendingLinePointNarys!!.x
        val pz = -state.pendingLinePointNarys!!.y

        val preview = Offset(px,pz).toScreenOld(state.scale, state.canvasOffset)
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
