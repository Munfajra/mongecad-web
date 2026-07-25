package monge.input.selection

import model.classes.ConicSection3D
import draw.mongescreen.labels.clearSelection
import state.MongeState

/*
 * Výběr kuželosečky přes všechny její průměty.
 * Dřív v `rightDescriptionBar/ObjectList/RotPloch.kt` u rotačních ploch,
 * i když s nimi nesouvisí – volá to i obecný strom objektů.
 */
fun selectConic3DProjections(
    state: MongeState,
    conic3dId: String,
    clearAllOnClick: Boolean,
) {
    if (clearAllOnClick) clearSelection(state)

    // Conic projekce
    val projP = state.conicsPudorys.firstOrNull { it.parentId == conic3dId }
    val projN = state.conicsNarys.firstOrNull { it.parentId == conic3dId }
    val projB = state.conicsBokorys.firstOrNull { it.parentId == conic3dId }
    val projA = state.conicsAxo.firstOrNull { it.parentId == conic3dId }

    if (projP != null && !state.selectedConicsPudorys.contains(projP)) {
        toggleSelectionPudorysConic(projP, state)
    }
    if (projN != null && !state.selectedConicsNarys.contains(projN)) {
        toggleSelectionNarysConic(projN, state)
    }
    if (projB != null && !state.selectedConicsBokorys.contains(projB)) {
        toggleSelectionBokorysConic(projB, state)
    }
    // AXO nativní průmět (axoMode == NORMAL_2D čte state.selectedConicsAxo, viz decideConicAxoNative)
    if (projA != null && !state.selectedConicsAxo.contains(projA)) {
        state.selectedConicsAxo.add(projA)
    }

    // Fallback: kdyby projekce byly uložené jako circles (pokud to někdy nastane)
    if (projP == null) {
        val cP = state.circlesPudorys.firstOrNull { it.parentId == conic3dId }
        if (cP != null && !state.selectedCirclesPudorys.contains(cP)) {
            toggleSelectionPudorysCircle(cP, state)
        }
    }
    if (projN == null) {
        val cN = state.circlesNarys.firstOrNull { it.parentId == conic3dId }
        if (cN != null && !state.selectedCirclesNarys.contains(cN)) {
            toggleSelectionNarysCircle(cN, state)
        }
    }
}

fun isConic3DSelected(state: MongeState, conic3dId: String): Boolean {
    if (state.selectedConicsPudorys.any { (it.parent?.id ?: it.parentId) == conic3dId }) return true
    if (state.selectedConicsNarys.any { (it.parent?.id ?: it.parentId) == conic3dId }) return true
    if (state.selectedConicsBokorys.any { (it.parent?.id ?: it.parentId) == conic3dId }) return true

    // pokud někdy vybíráš kružnice jako Circle2D:
    if (state.selectedCirclesPudorys.any { it.parentId == conic3dId }) return true
    if (state.selectedCirclesNarys.any { it.parentId == conic3dId }) return true
    if (state.selectedCirclesBokorys.any { it.parentId == conic3dId }) return true

    return false
}

