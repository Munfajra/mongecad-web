package draw.mongescreen.previews.segments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import state.MongeState
import utils.toScreen
import utils.toScreenOld

fun DrawScope.drawKotoSegmentEndpointHighlight(state: MongeState) {
    val segId = state.kotoHighlightSegmentId ?: return
    val which = state.kotoHighlightEndpoint
    if (which == 0) return

    val seg = state.segmentsPudorys.toList()
        .firstOrNull { it.id == segId }
        ?: return

    val p = if (which == 1) seg.start else seg.end

    // logické souřadnice
    val logical = Offset(p.x, p.y)

    // převod do screen (stejně jako ostatní kreslení)
    val screen = logical.toScreenOld(
        scale = state.scale,
        offset = state.canvasOffset,
    )

    val radius = 15f
    val stroke = 4f

    // 🔴 ring kolem bodu
    drawCircle(
        color = Color.Red,
        radius = radius,
        center = screen,
        style = Stroke(width = stroke)
    )


}