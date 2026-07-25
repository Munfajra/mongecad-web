package draw.mongescreen.previews.segments.pudorys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewPudorys
import model.Mongeobjects
import model.classes.Point3DPudorys
import model.ProjectionType
import state.MongeState
import kotlin.collections.contains

fun DrawScope.previewAssociatedSegmentsPudorysTemporary(state: MongeState){
    if (state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "narys_segment_associated_A_pudorys_start",
            "narys_segment_associated_B_pudorys_start",
            "segment_parallel_place_line_narys",
            "segment_orthogonal_place_line_narys"
        )
    ) {
        val xA = state.pendingXA
        val yA = state.pendingYA
        val xB = state.pendingXB
        val yB = state.pendingYB

        if (xA != null && yA != null && xB != null && yB != null) {
            val start = Point3DPudorys(xA, yA, name = "")
            val end = Offset(xB, yB)

            drawDashedSegmentPreviewPudorys(
                start = start,
                cursorWorld = end,
                scale = state.scale,
                canvasOffset = state.canvasOffset,
                dashLength = 1f,
                gapLength = 0f,
                strokeWidth = 3f,
                color = Color.Red
            )
        }
    }
}
