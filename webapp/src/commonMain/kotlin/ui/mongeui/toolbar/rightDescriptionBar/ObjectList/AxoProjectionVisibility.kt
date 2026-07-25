package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import model.ProjectionMode
import state.MongeState

fun showAxoProjectionChildren(state: MongeState): Boolean =
    state.projectionMode == ProjectionMode.AXO
