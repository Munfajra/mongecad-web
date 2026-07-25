package draw.mongescreen.previews.cursor

import monge.input.axo.axoOverlayToScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.axo.AxoMode
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.getLogicalCursorAxoOverlay
import monge.input.selection.CylinderPhase
import state.MongeState
import utils.getLogicalCursor
import kotlin.collections.contains

fun DrawScope.drawCursorPreviewCross(state: MongeState, snapped: Offset?) {
    if (state.drawobjects == Mongeobjects.NONE ||
        state.drawobjects== Mongeobjects.PLANE_LIFT ||
        (state.drawobjects == Mongeobjects.CYLINDER && state.cylinderPhase == CylinderPhase.PICK_CONIC )||
        (state.drawobjects == Mongeobjects.CYLINDER && state.cylinderPhase == CylinderPhase.PICK_CONIC_PERP )||
        state.drawobjects == Mongeobjects.CONE ) return

    val scale = state.scale
    val canvasOffset = state.canvasOffset

    val canvasWidth = state.canvasWidth    // nebo size.width – podle toho, co je zdroj pravdy u tebe
    val canvasHeight = state.canvasHeight  // (když máš v Canvasu transformaci, typicky sedí state.*)

    val flipX = (state.xAxisDirection == XAxisDirection.POSITIVE_LEFT)
    val flipY = (state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP)

    // 1) logický kurzor jednotně
    val logical = snapped ?: getLogicalCursor(
        snapped = null,
        cursor = state.cursorPosition,
        canvasOffset = canvasOffset,
        scale = scale,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        flipX = flipX,
        flipY = flipY
    )

    // 2) logical -> "canvas-space" (tj. prostor, ve kterém kreslíš geometrii před finální transformací)
    val preview = logical * scale + canvasOffset

    val crossColor = if (state.isCtrlPressed) Color.Red else Color.Gray
    val s = 10f

    drawLine(
        color = crossColor,
        start = Offset(preview.x - s, preview.y),
        end = Offset(preview.x + s, preview.y),
        strokeWidth = 2f
    )
    drawLine(
        color = crossColor,
        start = Offset(preview.x, preview.y - s),
        end = Offset(preview.x, preview.y + s),
        strokeWidth = 2f
    )
}
fun DrawScope.drawCursorPreviewCrossAxo(
    state: MongeState,
    snapped: Offset?
) {
    val finephase = state.projectionPhase in listOf("axo_complete_line_second_projection","axo_complete_line_waiting_for_second_projection",
        "axo_complete_segment_second_projection","axo_complete_segment_waiting_for_second_projection"
    )
    if ((state.drawobjects == Mongeobjects.NONE ||
                state.drawobjects == Mongeobjects.PLANE_LIFT ||
                (state.drawobjects == Mongeobjects.CYLINDER && state.cylinderPhase == CylinderPhase.PICK_CONIC) ||
                state.drawobjects == Mongeobjects.CONE
                )&& !finephase) return
    val logical = snapped ?: getLogicalCursorAxo(
        null,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        flipX = false,
        flipY = false,
        state.axoMode,
        state.activeAxoModel
    ) ?: return

    val crossColor = if (state.isCtrlPressed) Color.Red else Color.Gray

    val invScale = if (state.scale > 1e-6f) 1f / state.scale else 1f
    val s = 10f * invScale
    val stroke = 2f * invScale

    drawLine(
        color = crossColor,
        start = Offset(logical.x - s, logical.y),
        end = Offset(logical.x + s, logical.y),
        strokeWidth = stroke
    )
    drawLine(
        color = crossColor,
        start = Offset(logical.x, logical.y - s),
        end = Offset(logical.x, logical.y + s),
        strokeWidth = stroke
    )
}
fun DrawScope.drawCursorPreviewAO(
    state: MongeState,
    snapped: Offset?
) {
    val finephase = state.projectionPhase in listOf(
        "axo_complete_line_second_projection",
        "axo_complete_line_waiting_for_second_projection",
        "axo_complete_segment_second_projection",
        "axo_complete_segment_waiting_for_second_projection"
    )
    if (state.axoMode != AxoMode.NORMAL_2D) return
    if (state.drawobjects == Mongeobjects.NONE ||
        state.drawobjects == Mongeobjects.PLANE_LIFT ||
        (state.drawobjects == Mongeobjects.CYLINDER && state.cylinderPhase == CylinderPhase.PICK_CONIC) ||
        state.drawobjects == Mongeobjects.CONE
    ) {
        if (!finephase) return
    }

    val basis = state.basis ?: return

    val logical = snapped ?: getLogicalCursorAxoOverlay(
        snappedScreen = null,
        cursor = state.cursorPosition,
        state = state
    ) ?: return

    val scr = axoOverlayToScreen(
        local = logical,
        state = state,
        basis = basis
    )

    val crossColor = if (state.isCtrlPressed) Color.Red else Color.Gray
    val s = 10f
    val stroke = 2f

    drawLine(
        color = crossColor,
        start = Offset(scr.x - s, scr.y),
        end = Offset(scr.x + s, scr.y),
        strokeWidth = stroke
    )
    drawLine(
        color = crossColor,
        start = Offset(scr.x, scr.y - s),
        end = Offset(scr.x, scr.y + s),
        strokeWidth = stroke
    )
}