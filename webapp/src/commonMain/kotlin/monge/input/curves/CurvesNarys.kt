package monge.input.curves

import serialization.commitSnapshot
import model.classes.CurveNarys
import model.DrawingModeMonge
import model.Mongeobjects
import model.classes.Point3DNarys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo

fun handleNarysCurvePickPointClick(
    state: MongeState,
    clicked: Point3DNarys
) {
    if (state.mongeMode != DrawingModeMonge.NARYS) return
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "narys_curve_pick") return

    // ✅ uzavření klikem na první bod (bez duplicity)
    if (tryCloseNarysCurveOnClickFirst(state, clicked.id)) return

    // anti-double-click
    if (state.narysCurveLastPickedId == clicked.id) return

    // ✅ zákaz duplicity (smyčky přes bod)
    if (state.narysCurvePickPointIds.contains(clicked.id)) {
        state.consInfo.value = "Bod už je v křivce – pro uzavření klikni na první bod."
        return
    }

    state.narysCurvePickPointIds.add(clicked.id)
    state.narysCurveLastPickedId = clicked.id
    state.narysCurvePickClosed = false

    updateConstructionInfo(state)
    println("NÁRYS: přidán další bod, ${state.narysCurvePickPointIds.size}")
}

fun finalizeNarysCurveOnEnter(state: MongeState): Boolean {
    if (state.mongeMode != DrawingModeMonge.NARYS) return false
    if (state.drawobjects != Mongeobjects.CURVE) return false
    if (state.projectionPhase != "narys_curve_pick") return false

    val ids = state.narysCurvePickPointIds.toList()

    if (ids.size < 3) {
        state.consInfo.value = "Pro hladkou křivku vyber alespoň 3 body."
        return true
    }

    val ok = ids.all { id -> state.pointsNarys.any { it.id == id } }
    if (!ok) {
        state.consInfo.value = "Některý z vybraných bodů už neexistuje – začněte znovu."
        state.narysCurvePickPointIds.clear()
        state.narysCurveLastPickedId = null
        state.narysCurvePickClosed = false
        return true
    }

    val closedNow = state.narysCurvePickClosed

    val curve = CurveNarys(
        parentId = null,
        name = "k",
        color = state.currentLineStyleSettings.color,
        strokeWidth = state.currentLineStyleSettings.strokeWidth,
        lineStyle = state.currentLineStyleSettings.style,
        pointIds = ids,
        closed = closedNow,                  // ✅
        creationIndex = state.nextCreationIndex,
    )

    state.curvesNarys.add(curve)

    state.narysCurvePickPointIds.clear()
    state.narysCurveLastPickedId = null
    state.narysCurvePickClosed = false       // ✅ reset

    println("NÁRYS: křivka vytvořena (${ids.size} bodů), closed=$closedNow")

    setProjectionPhase("narys_start", state)
    repeatCons(state)
    commitSnapshot(state)
    return true
}

private fun tryCloseNarysCurveOnClickFirst(state: MongeState, clickedId: String): Boolean {
    val ids = state.narysCurvePickPointIds
    val first = ids.firstOrNull() ?: return false

    if (clickedId == first && ids.size >= 3) {
        state.narysCurvePickClosed = true
        finalizeNarysCurveOnEnter(state)
        return true
    }
    return false
}
