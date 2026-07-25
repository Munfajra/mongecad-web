package draw.mongescreen.previews.segments.narys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewNarys
import model.Mongeobjects
import model.classes.Point3DNarys
import model.ProjectionType
import state.MongeState

fun DrawScope.previewAssociatedSegmentsNarysTemporary(state: MongeState){
    if (state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "pudorys_segment_associated_A_narys_start",
            "pudorys_segment_associated_B_narys_start",
            "pudorys_segment_associated_A_narys_start_orthogonal",
            "segment_parallel_place_line_pudorys",
            "segment_orthogonal_place_line_pudorys"

        )
    ) {
        val xA = state.pendingXA
        val zA = state.pendingZA
        val xB = state.pendingXB
        val zB = state.pendingZB

        if (xA != null && zA != null && xB != null && zB != null) {
            val start = Point3DNarys(xA, zA,name="")
            val end = Offset(xB, -zB)

            drawDashedSegmentPreviewNarys(
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
