package monge.input.axo.points

import utils.System
import model.axo.AxoMode
import monge.input.axo.getLogicalCursorAxo
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

fun handleSinglePudorysPointAxo(
    state: MongeState
) {


    val logical = getLogicalCursorAxo(
        snapped = state.snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_PUDORYS,
        axoModel = state.activeAxoModel
    ) ?: return

    state.pendingX = logical.x
    state.pendingY = logical.y
    state.inputName = ""

    setProjectionPhase("single_pudorys", state)

    state.isNameConfirmed = false
    state.deferSelectionUntil = System.currentTimeMillis() + 100
}