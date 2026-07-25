package monge.input.selection


import draw.mongescreen.labels.clearSelection
import model.classes.ConicSection3D
import model.classes.ConicSectionBokorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.Mongeobjects
import monge.input.planeobjects.planelift.decideObjectForLift
import state.MongeState


fun toggleSelectionPudorysConic(conic: ConicSectionPudorys, state: MongeState) {
    val pid = conic.parentId ?: conic.parent?.id
    if (pid != null && state.spheres3D.any { it.id == pid }) {
        state.spheres3D.find { it.id == pid }?.let { toggleSelectionSphere3D(it, state) }
        return
    }
    if (pid != null) {
        toggleSelectionConic2DFamilyByParentId(pid, state)
        return
    }
    // orphan 2D
    val exists = state.selectedConicsPudorys.any { it.id == conic.id }
    if (exists) state.selectedConicsPudorys.removeAll { it.id == conic.id }
    else {state.selectedConicsPudorys.add(conic)

    decideObjectForLift(state)
    advancePendingConicConstruction(state)}
}
fun toggleSelectionNarysConic(conic: ConicSectionNarys, state: MongeState) {
    val pid = conic.parentId ?: conic.parent?.id
    if (pid != null && state.spheres3D.any { it.id == pid }) {
        state.spheres3D.find { it.id == pid }?.let { toggleSelectionSphere3D(it, state) }
        return
    }
    if (pid != null) {
        toggleSelectionConic2DFamilyByParentId(pid, state)
        return
    }
    val exists = state.selectedConicsNarys.any { it.id == conic.id }
    if (exists) state.selectedConicsNarys.removeAll { it.id == conic.id }
    else {state.selectedConicsNarys.add(conic)
    decideObjectForLift(state)
    advancePendingConicConstruction(state)}
}

fun toggleSelectionBokorysConic(conic: ConicSectionBokorys, state: MongeState) {
    val pid = conic.parentId ?: conic.parent?.id
    if (pid != null && state.spheres3D.any { it.id == pid }) {
        state.spheres3D.find { it.id == pid }?.let { toggleSelectionSphere3D(it, state) }
        return
    }
    if (pid != null) {
        toggleSelectionConic2DFamilyByParentId(pid, state)
        return
    }
    val exists = state.selectedConicsBokorys.any { it.id == conic.id }
    if (exists) state.selectedConicsBokorys.removeAll { it.id == conic.id }
    else {
        state.selectedConicsBokorys.add(conic)
        decideObjectForLift(state)
        advancePendingConicConstruction(state)
    }
}

fun toggleSelectionConic3D(parent: ConicSection3D, state: MongeState) {
    val pid = parent.id
    val pSibs = state.conicsPudorys.filter { it.parentId == pid || it.parent?.id == pid }
    val nSibs = state.conicsNarys  .filter { it.parentId == pid || it.parent?.id == pid }
    val bSibs = state.conicsBokorys.filter { it.parentId == pid || it.parent?.id == pid }
    val aSibs = state.conicsAxo.filter { it.parentId == pid || it.parent?.id == pid }
    if (state.drawobjects== Mongeobjects.CONE) {
        state.pendingConic3DId=parent.id

    }
    // pro robustnost porovnávej podle ID, ne podle instance
    val selP = state.selectedConicsPudorys.map { it.id }.toSet()
    val selN = state.selectedConicsNarys  .map { it.id }.toSet()
    val selB = state.selectedConicsBokorys.map { it.id }.toSet()
    val selA = state.selectedConicsAxo.map { it.id }.toSet()

    val alreadySelected = pSibs.any { it.id in selP } || nSibs.any { it.id in selN } || bSibs.any { it.id in selB } || aSibs.any { it.id in selA }

    if (alreadySelected) {
        state.selectedConicsPudorys.removeAll { it.parentId == pid || it.parent?.id == pid }
        state.selectedConicsNarys.removeAll   { it.parentId == pid || it.parent?.id == pid }
        state.selectedConicsBokorys.removeAll { it.parentId == pid || it.parent?.id == pid }
        state.selectedConicsAxo.removeAll { it.id == pid || it.parent?.id == pid }
    } else {
        state.selectedConicsPudorys.addAll(pSibs)
        state.selectedConicsNarys.addAll(nSibs)
        state.selectedConicsBokorys.addAll(bSibs)
        state.selectedConicsAxo.addAll(aSibs)
        decideObjectForLift(state)
    }

}

fun toggleSelectionConic2DFamilyByParentId(parentId: String, state: MongeState) {
    val pSibs = state.conicsPudorys.filter { it.parentId == parentId || it.parent?.id == parentId }
    val nSibs = state.conicsNarys  .filter { it.parentId == parentId || it.parent?.id == parentId }
    val bSibs = state.conicsBokorys.filter { it.parentId == parentId || it.parent?.id == parentId }
    val aSibs = state.conicsAxo.filter{ it.parentId == parentId || it.parent?.id == parentId }

    val selP = state.selectedConicsPudorys.map { it.id }.toSet()
    val selN = state.selectedConicsNarys  .map { it.id }.toSet()
    val selB = state.selectedConicsBokorys.map { it.id }.toSet()
    val selA = state.selectedConicsAxo.map { it.id }.toSet()
    val already = pSibs.any { it.id in selP } || nSibs.any { it.id in selN } || bSibs.any { it.id in selB } || aSibs.any { it.id in selA }

    if (already) {
        state.selectedConicsPudorys.removeAll { it.parentId == parentId || it.parent?.id == parentId }
        state.selectedConicsNarys.removeAll   { it.parentId == parentId || it.parent?.id == parentId }
        state.selectedConicsBokorys.removeAll { it.parentId == parentId || it.parent?.id == parentId }
        state.selectedConicsAxo.removeAll { it.id == parentId || it.parent?.id == parentId }
        println("Odznačena rodina kuželosečky: $parentId")

    } else {
        state.selectedConicsPudorys.addAll(pSibs)
        state.selectedConicsNarys.addAll(nSibs)
        state.selectedConicsBokorys.addAll(bSibs)
        state.selectedConicsAxo.addAll(aSibs)
        println("Označena rodina kuželosečky: $parentId")
        decideObjectForLift(state)
        advancePendingConicConstruction(state)
    }
}
fun handleSelectionAxoConic(state: MongeState) {
    val conic = state.snappedConicAxo?: return
    if (!state.isShiftPressed) clearSelection(state)
    val pid = conic.parentId ?: conic.parent?.id
    if (pid != null && state.spheres3D.any { it.id == pid }) {
        state.spheres3D.find { it.id == pid }?.let { toggleSelectionSphere3D(it, state) }
        return
    }
    if (pid != null) {
        toggleSelectionConic2DFamilyByParentId(pid, state)
        return
    }
    val exists = state.selectedConicsAxo.any { it.id == conic.id }
    if (exists) state.selectedConicsAxo.removeAll { it.id == conic.id }
    else {
        state.selectedConicsAxo.add(conic)
        decideObjectForLift(state)
    }
}
