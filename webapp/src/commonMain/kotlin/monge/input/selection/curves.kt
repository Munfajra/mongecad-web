package monge.input.selection

import model.SOR_BOKORYS_MERIDIAN_ID_PREFIX
import model.Mongeobjects
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import monge.input.meridian.solidOfRevolutionNarys
import monge.input.meridian.solidOfRevolutionPudorys
import monge.input.ruledsurface.handleRuledSurfaceSelection
import state.MongeState
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.selectSolidOfRevolutionAll

fun clearCurveSelection(state: MongeState) {
    state.selectedCurve3DId = null
    state.selectedCurvePudorysId = null
    state.selectedCurveNarysId = null
    state.selectedCurveBokorysId = null
    state.selectedCurveAxoId = null
}
fun toggleSelectionCurve3D(state: MongeState, curveId: String) {
    val already = state.selectedCurve3DId == curveId

    clearCurveSelection(state)

    if (!already) {
        state.selectedCurve3DId = curveId
        handleRuledSurfaceSelection(state)
    }
}
fun toggleSelectionCurvePudorys(
    state: MongeState,
    curveId: String,
    selectParentObject: Boolean = true,
) {
    val curve = state.curvesPudorys.find { it.id == curveId } ?: return

    val selectParent = selectParentObject && state.drawobjects != Mongeobjects.SOLID_OF_REVOLUTION
    if (selectParent) {
        // Klik na obrys v plátně vybírá plochu; ObjectList a konstrukce si mohou
        // explicitně vyžádat samotný průmět křivky.
        val parentId = curve.parentId
        if (toggleRuledSurfaceFromOutline(state, parentId)) return
        if (parentId != null) {
            toggleSelectionCurve3D(state, parentId)
            return
        }
    }

    val already = state.selectedCurvePudorysId == curveId

    clearCurveSelection(state)

    if (!already) {
        state.selectedCurvePudorysId = curveId
        solidOfRevolutionPudorys(state)
    }
}
fun toggleSelectionCurveNarys(
    state: MongeState,
    curveId: String,
    selectParentObject: Boolean = true,
) {
    val curve = state.curvesNarys.find { it.id == curveId } ?: return

    val selectParent = selectParentObject && state.drawobjects != Mongeobjects.SOLID_OF_REVOLUTION
    if (selectParent) {
        val parentId = curve.parentId
        if (toggleRuledSurfaceFromOutline(state, parentId)) return
        if (parentId != null) {
            toggleSelectionCurve3D(state, parentId)
            return
        }
    }

    val already = state.selectedCurveNarysId == curveId

    clearCurveSelection(state)

    if (!already) {
        state.selectedCurveNarysId = curveId
        solidOfRevolutionNarys(state)
    }
}

fun toggleSelectionCurveBokorys(
    state: MongeState,
    curveId: String,
    selectParentObject: Boolean = true,
) {
    val curve = state.curvesBokorys.find { it.id == curveId } ?: return

    if (selectParentObject) {
        val parentId = curve.parentId
        if (toggleRuledSurfaceFromOutline(state, parentId)) return
        if (parentId != null) {
            toggleSelectionCurve3D(state, parentId)
            return
        }
    }

    val already = state.selectedCurveBokorysId == curveId
    clearCurveSelection(state)

    if (!already) {
        state.selectedCurveBokorysId = curveId
    }
}

fun toggleSelectionCurveAxo(
    state: MongeState,
    curveId: String,
    selectParentObject: Boolean = true,
) {
    val curve = state.curvesAxo.find { it.id == curveId } ?: return

    if (selectParentObject) {
        val parentId = curve.parentId
        if (toggleRuledSurfaceFromOutline(state, parentId)) return
        if (parentId != null) {
            toggleSelectionCurve3D(state, parentId)
            return
        }
    }

    val already = state.selectedCurveAxoId == curveId
    clearCurveSelection(state)

    if (!already) {
        state.selectedCurveAxoId = curveId
    }
}

private fun toggleRuledSurfaceFromOutline(state: MongeState, parentId: String?): Boolean {
    val surface = parentId?.let { id -> state.ruledSurfaces.firstOrNull { it.id == id } } ?: return false
    val already = state.selectedRuledSurfaceId == surface.id
    clearCurveSelection(state)
    state.selectedRuledSurfaceId = if (already) null else surface.id
    return true
}

/**
 * Všechna id, podle kterých může křivka patřit rotační ploše: vlastní id, parent
 * (rodičovská 3D křivka nebo přímo id plochy u bokorysného poledníku / axo obrysu)
 * a id sourozeneckých průmětů téže 3D křivky (poledník bývá v `meridianIds` uložen
 * pod id půdorysného/nárysného průmětu, kdežto v axo klikáš na axo/bokorys průmět).
 */
private fun curveSolidLookupIds(state: MongeState, curveId: String, parentId: String?): Set<String> {
    val ids = linkedSetOf(curveId)
    if (parentId != null) {
        ids += parentId
        state.curvesPudorys.forEach { if (it.parentId == parentId || it.parent?.id == parentId) ids += it.id }
        state.curvesNarys.forEach { if (it.parentId == parentId || it.parent?.id == parentId) ids += it.id }
        state.curvesBokorys.forEach { if (it.parentId == parentId || it.parent?.id == parentId) ids += it.id }
        state.curvesAxo.forEach { if (it.parentId == parentId || it.parent?.id == parentId) ids += it.id }
    }
    return ids
}

/**
 * Pokud křivka patří rotační ploše, vybere celou plochu (všechny její části).
 * Vrací true, pokud se plocha našla a vybrala.
 */
private fun trySelectSolidOfRevolutionForCurve(state: MongeState, curveId: String, parentId: String?): Boolean {
    val clearAllOnClick = !state.isShiftPressed
    val ids = curveSolidLookupIds(state, curveId, parentId)

    state.solidsOfRevolutionNarys.firstOrNull {
        it.id in ids ||
            it.meridianIdsNarys.any { m -> m in ids } ||
            it.mirroredMeridianIdsNarys.any { m -> m in ids }
    }?.let { selectSolidOfRevolutionAll(state, it, clearAllOnClick); return true }

    state.solidsOfRevolutionPudorys.firstOrNull {
        it.id in ids ||
            it.meridianIdsPudorys.any { m -> m in ids } ||
            it.mirroredMeridianIdsPudorys.any { m -> m in ids }
    }?.let { selectSolidOfRevolutionAll(state, it, clearAllOnClick); return true }

    return false
}

fun trySelectSolidOfRevolutionForPart(
    state: MongeState,
    objectId: String,
    parentId: String? = null,
): Boolean {
    val clearAllOnClick = !state.isShiftPressed
    val ids = solidPartLookupIds(state, objectId, parentId)

    state.solidsOfRevolutionNarys.firstOrNull { solid ->
        solid.id in ids ||
            solid.meridianIdsNarys.any { it in ids } ||
            solid.mirroredMeridianIdsNarys.any { it in ids } ||
            solid.circleIdsPudorys.any { it in ids } ||
            solid.circleIdsNarys.any { it in ids } ||
            (objectId.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && objectId.contains(solid.id))
    }?.let { selectSolidOfRevolutionAll(state, it, clearAllOnClick); return true }

    state.solidsOfRevolutionPudorys.firstOrNull { solid ->
        solid.id in ids ||
            solid.meridianIdsPudorys.any { it in ids } ||
            solid.mirroredMeridianIdsPudorys.any { it in ids } ||
            solid.circleIdsPudorys.any { it in ids } ||
            solid.circleIdsNarys.any { it in ids } ||
            (objectId.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && objectId.contains(solid.id))
    }?.let { selectSolidOfRevolutionAll(state, it, clearAllOnClick); return true }

    return false
}

private fun solidPartLookupIds(state: MongeState, objectId: String, parentId: String?): Set<String> {
    val ids = linkedSetOf(objectId)
    if (parentId != null) {
        ids += parentId
        state.conicsPudorys.forEach { if (it.parentId == parentId || it.parent?.id == parentId) ids += it.id }
        state.conicsNarys.forEach { if (it.parentId == parentId || it.parent?.id == parentId) ids += it.id }
        state.conicsBokorys.forEach { if (it.parentId == parentId || it.parent?.id == parentId) ids += it.id }
        state.conicsAxo.forEach { if (it.parentId == parentId || it.parent?.id == parentId) ids += it.id }
        state.circlesPudorys.forEach { if (it.parentId == parentId) ids += it.id }
        state.circlesNarys.forEach { if (it.parentId == parentId) ids += it.id }
    }
    return ids
}

/**
 * Klik na jakoukoliv projekční část meridiánu rotační plochy vybírá celou plochu,
 * stejně jako klik na její křivkové části.
 */
fun handleSelectionSolidOfRevolutionParts(state: MongeState): Boolean {
    state.snappedSegmentPudorys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id, it.parent?.id ?: (it as? Segment2DPudorys)?.parentId)) return true
    }
    state.snappedSegmentNarys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id, it.parent?.id ?: (it as? Segment2DNarys)?.parentId)) return true
    }
    state.snappedSegmentBokorys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id, it.parent?.id ?: (it as? Segment2DBokorys)?.parentId)) return true
    }
    state.snappedArcPudorys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id)) return true
    }
    state.snappedArcNarys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id)) return true
    }
    state.snappedArcBokorys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id)) return true
    }
    state.snappedConicPudorys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id, it.parentId ?: it.parent?.id)) return true
    }
    state.snappedConicNarys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id, it.parentId ?: it.parent?.id)) return true
    }
    state.snappedConicBokorys?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id, it.parentId ?: it.parent?.id)) return true
    }
    state.snappedConicAxo?.let {
        if (trySelectSolidOfRevolutionForPart(state, it.id, it.parentId ?: it.parent?.id)) return true
    }
    return false
}

/**
 * Výběr křivky kliknutím podle aktuálně snapnuté křivky (`state.snappedCurveXxx`).
 * Funguje pro všechny módy – v axo se nastavuje pudorys/narys/bokorys/axo,
 * v Monge/Koto/Plane jen pudorys/narys.
 *
 * Pokud je křivka součástí rotační plochy, vybere se celá plocha (vrací true –
 * volající přeskočí exkluzivní arbitráž výběru, která by vícegrupový výběr zrušila).
 * Body mají přednost: pokud je snapnutý bod, výběr křivky se přeskočí.
 */
fun handleSelectionCurves(state: MongeState): Boolean {
    val pointSnapped =
        state.snappedPointPudorys != null || state.snappedPointNarys != null ||
        state.snappedPointBokorys != null || state.snappedAxoPoint != null ||
        state.snappedAOPoint != null || state.hoveredAidPointId != null
    if (pointSnapped) return false
    val canSelectWholeSor = state.drawobjects == Mongeobjects.NONE

    state.snappedCurvePudorys?.let { c ->
        if (canSelectWholeSor && trySelectSolidOfRevolutionForCurve(state, c.id, c.parentId)) return true
        toggleSelectionCurvePudorys(state, c.id); return false
    }
    state.snappedCurveNarys?.let { c ->
        if (canSelectWholeSor && trySelectSolidOfRevolutionForCurve(state, c.id, c.parentId)) return true
        toggleSelectionCurveNarys(state, c.id); return false
    }
    state.snappedCurveBokorys?.let { c ->
        if (canSelectWholeSor && trySelectSolidOfRevolutionForCurve(state, c.id, c.parentId)) return true
        toggleSelectionCurveBokorys(state, c.id); return false
    }
    state.snappedCurveAxo?.let { c ->
        if (canSelectWholeSor && trySelectSolidOfRevolutionForCurve(state, c.id, c.parentId)) return true
        toggleSelectionCurveAxo(state, c.id); return false
    }
    return false
}
