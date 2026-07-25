package monge.input.axo.points

import utils.System
import androidx.compose.ui.geometry.Offset
import model.classes.AxoOverlayPoint
import monge.input.axo.AxoRenderBasis
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import utils.allocIndex

fun addAxoOverlayPoint(state: MongeState) {
    val basis = state.basis ?: return

    val logical = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

    val axoOverlayPoint = AxoOverlayPoint(
        positionLogical = logical,
        color = state.currentHelpLineStyleSettings.color,
        creationIndex = allocIndex(state)
    )

    repeatCons(state)

    state.pendingAOPoint = axoOverlayPoint
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    state.axoOverlayPoints.add(axoOverlayPoint)
}


fun screenToAxoOverlayLocal(
    screen: Offset,
    state: MongeState,
    basis: AxoRenderBasis
): Offset {
    return ((screen - state.canvasOffset) / state.scale) - basis.origin
}
