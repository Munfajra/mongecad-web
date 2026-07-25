package monge.input.curves

import serialization.commitSnapshot
import model.classes.CurveBokorys
import model.Mongeobjects
import model.classes.Point3DBokorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.updateConstructionInfo

fun handleBokorysСurvePickPointClick(
    state: MongeState,
    clicked: Point3DBokorys
) {
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "bokorys_curve_pick") return

    if (tryCloseBokorysСurveOnClickFirst(state, clicked.id)) return

    if (state.bokorysCurveLastPickedId == clicked.id) return

    if (state.bokorysCurvePickPointIds.contains(clicked.id)) {
        state.consInfo.value = "Bod už je v křivce – pro uzavření klikni na první bod."
        return
    }

    state.bokorysCurvePickPointIds.add(clicked.id)
    state.bokorysCurveLastPickedId = clicked.id
    state.bokorysCurvePickClosed = false

    updateConstructionInfo(state)
}

fun finalizeBokorysCurveOnEnter(state: MongeState): Boolean {
    if (state.drawobjects != Mongeobjects.CURVE) return false
    if (state.projectionPhase != "bokorys_curve_pick") return false

    val ids = state.bokorysCurvePickPointIds.toList()

    if (ids.size < 3) {
        state.consInfo.value = "Pro hladkou křivku vyber alespoň 3 body."
        return true
    }

    val ok = ids.all { id -> state.pointsBokorys.any { it.id == id } }
    if (!ok) {
        state.consInfo.value = "Některý z vybraných bodů už neexistuje – začněte znovu."
        state.bokorysCurvePickPointIds.clear()
        state.bokorysCurveLastPickedId = null
        state.bokorysCurvePickClosed = false
        return true
    }

    val closedNow = state.bokorysCurvePickClosed
    val settings = state.currentLineStyleSettings

    val curve = CurveBokorys(
        parentId = null,
        name = "k",
        color = settings.color,
        strokeWidth = settings.strokeWidth,
        lineStyle = settings.style,
        pointIds = ids,
        closed = closedNow,
        creationIndex = state.nextCreationIndex,
    )

    state.curvesBokorys.add(curve)

    state.bokorysCurvePickPointIds.clear()
    state.bokorysCurveLastPickedId = null
    state.bokorysCurvePickClosed = false

    repeatCons(state)
    commitSnapshot(state)
    return true
}

private fun tryCloseBokorysСurveOnClickFirst(state: MongeState, clickedId: String): Boolean {
    val ids = state.bokorysCurvePickPointIds
    val first = ids.firstOrNull() ?: return false

    if (clickedId == first && ids.size >= 3) {
        state.bokorysCurvePickClosed = true
        finalizeBokorysCurveOnEnter(state)
        return true
    }
    return false
}
