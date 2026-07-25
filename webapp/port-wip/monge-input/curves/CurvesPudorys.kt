package monge.input.curves

import serialization.commitSnapshot
import model.classes.AidPointLogical
import model.classes.CurvePudRef
import model.classes.CurvePudorys
import model.DrawingModeMonge
import model.Mongeobjects
import model.classes.Point3DPudorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo

private fun CurvePudRef.existsIn(state: MongeState): Boolean = when (this) {
    is CurvePudRef.P -> state.pointsPudorys.any { it.id == pointId }
    is CurvePudRef.A -> state.aidPointsLogical.any { it.id == aidId } // <- přejmenuj na tvůj list
}

fun handlePudorysCurvePickPointClick(state: MongeState, clicked: Point3DPudorys) =
    handlePudorysCurvePickRef(state, CurvePudRef.P(clicked.id))

fun handlePudorysCurvePickAidPointClick(state: MongeState, clicked: AidPointLogical) =
    handlePudorysCurvePickRef(state, CurvePudRef.A(clicked.id))
private fun handlePudorysCurvePickRef(state: MongeState, ref: CurvePudRef) {
    if (state.mongeMode != DrawingModeMonge.PUDORYS) return
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "pudorys_curve_pick") return

    val key = ref.key()

    // ✅ uzavření klikem na první bod (bez přidání duplicity)
    if (tryClosePudorysCurveOnClickFirst(state, key)) return

    // anti-double-click
    if (state.pudorysCurveLastPickedKey == key) return

    // ✅ zákaz duplicity (smyčka přes použitý bod)
    if (state.pudorysCurvePickRefs.any { it.key() == key }) {
        state.consInfo.value = "Bod už je v křivce – pro uzavření klikni na první bod."
        return
    }

    state.pudorysCurvePickRefs.add(ref)
    state.pudorysCurveLastPickedKey = key
    state.pudorysCurvePickClosed = false

    updateConstructionInfo(state)
    println("přidán další bod, ${state.pudorysCurvePickRefs.size}")
}


fun finalizePudorysCurveOnEnter(state: MongeState): Boolean {
    if (state.mongeMode != DrawingModeMonge.PUDORYS) return false
    if (state.drawobjects != Mongeobjects.CURVE) return false
    if (state.projectionPhase != "pudorys_curve_pick") return false

    val refs = state.pudorysCurvePickRefs.toList()

    if (refs.size < 3) {
        state.consInfo.value = "Pro hladkou křivku vyber alespoň 3 body."
        return true
    }

    val ok = refs.all { it.existsIn(state) }
    if (!ok) {
        state.consInfo.value = "Některý z vybraných bodů už neexistuje – začněte znovu."
        state.pudorysCurvePickRefs.clear()
        state.pudorysCurveLastPickedKey = null
        state.pudorysCurvePickClosed = false
        return true
    }

    val closedNow = state.pudorysCurvePickClosed

    val curve = CurvePudorys(
        parentId = null,
        name = "k",
        color = state.currentLineStyleSettings.color,
        strokeWidth = state.currentLineStyleSettings.strokeWidth,
        lineStyle = state.currentLineStyleSettings.style,
        points = refs,
        closed = closedNow,                 // ✅ tady
        creationIndex = state.nextCreationIndex,
    )

    state.curvesPudorys.add(curve)

    // cleanup + zpět do startu
    state.pudorysCurvePickRefs.clear()
    state.pudorysCurveLastPickedKey = null
    state.pudorysCurvePickClosed = false  // ✅ reset

    println("Křivka vytvořena (${refs.size} bodů), closed=$closedNow")

    setProjectionPhase("pudorys_start", state)
    commitSnapshot(state)
    repeatCons(state)
    return true
}

private fun tryClosePudorysCurveOnClickFirst(state: MongeState, key: String): Boolean {
    val firstKey = state.pudorysCurvePickRefs.firstOrNull()?.key() ?: return false

    if (key == firstKey && state.pudorysCurvePickRefs.size >= 3) {
        state.pudorysCurvePickClosed = true
        finalizePudorysCurveOnEnter(state)
        return true
    }
    return false
}
