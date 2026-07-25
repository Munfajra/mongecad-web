package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.orth.conics.drawConicParabolaNarys
import draw.mongescreen.objects.orth.conics.drawConicParabolaPudorys
import model.LineStyle
import model.XAxisDirection
import model.YAxisDirectionPlane
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.drawParabolaConstructionPreviewPudorys(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    val vertex = state.pendingPoint1

    // Kurzor převedený na logické souřadnice
    val cursor = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {
        "parabola_focus" -> {
            if (vertex != null) {
                // 🔴 Vrchol
                drawRedCross(vertex, state = state)
                drawDashedLine(vertex, cursor, Color.Gray, state = state)
                // 🟢 Náhled paraboly s kurzorem jako ohniskem
                if ((cursor - vertex).getDistance() > 2f / state.scale) {
                    drawConicParabolaPudorys(
                        vertex = vertex,
                        focus = cursor,
                        canvasOffset = state.canvasOffset,
                        scale = state.scale,
                        color = Color.LightGray,
                        strokeWidth = 1.5f,
                        lineStyle = LineStyle.Dashed
                    )
                }
            }
        }
    }
}
fun DrawScope.drawParabolaConstructionPreviewNarys(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    val vertex = state.pendingPoint1

    // Kurzor převedený na logické souřadnice
    val cursor = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val cursorfixed = Offset(cursor.x,-cursor.y)

    when (state.projectionPhase) {
        "parabola_focus_narys" -> {
            if (vertex != null) {
                // 🔴 Vrchol (křížek)
                drawRedCross(vertex.copy(y = -vertex.y), state = state)
                drawDashedLine(vertex.copy(y = -vertex.y), cursor, Color.Gray, state = state)
                // 🟢 Náhled paraboly
                if ((cursor - vertex).getDistance() > 2f / state.scale) {
                    drawConicParabolaNarys(
                        vertex = vertex,
                        focus = cursorfixed,
                        canvasOffset = state.canvasOffset,
                        scale = state.scale,
                        color = Color.LightGray,
                        strokeWidth = 1.5f,
                        lineStyle = LineStyle.Dashed
                    )
                }
            }
        }
    }
}
