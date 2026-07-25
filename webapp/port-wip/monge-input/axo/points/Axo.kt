package monge.input.axo.points

import utils.System
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

fun handleSingleAxoProjectionPoint(
    state: MongeState
) {
    val basis = state.basis?: return
    val logical = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

    state.pendingX = logical.x
    state.pendingY = logical.y
    state.inputName = ""

    setProjectionPhase("single_axo", state)

    state.isNameConfirmed = false
    state.deferSelectionUntil = System.currentTimeMillis() + 100
}