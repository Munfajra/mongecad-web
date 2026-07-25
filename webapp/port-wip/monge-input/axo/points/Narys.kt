package monge.input.axo.points

import utils.System
import androidx.compose.ui.geometry.Offset
import model.axo.AxoMode
import monge.input.axo.getLogicalCursorAxo
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

fun handleSingleNarysPointAxo(
    snappedPointLogical: Offset?,
    state: MongeState
) {

    val logical = getLogicalCursorAxo(
        snapped = snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_NARYS,
        axoModel = state.activeAxoModel
    ) ?: return

    state.pendingX = logical.x
    state.pendingZ = logical.y
    state.inputName = ""

    setProjectionPhase("single_narys", state)

    state.isNameConfirmed = false
    state.deferSelectionUntil = System.currentTimeMillis() + 100
}