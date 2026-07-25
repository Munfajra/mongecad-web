package monge.input.axo.points

import utils.System
import androidx.compose.ui.geometry.Offset
import model.axo.AxoMode
import monge.input.axo.getLogicalCursorAxo
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

fun handleSingleBokorysPointAxo(
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
        mode = AxoMode.AXO_BOKORYS,
        axoModel = state.activeAxoModel
    ) ?: return

    state.pendingY = logical.x
    state.pendingZ = logical.y
    state.inputName = ""

    setProjectionPhase("single_bokorys", state)

    state.isNameConfirmed = false
    state.deferSelectionUntil = System.currentTimeMillis() + 100
}