package ui

import model.ConstructionModifier
import model.DrawingModeMonge
import model.Mongeobjects
import monge.input.selection.CylinderPhase
import state.MongeState
import ui.mongeui.toolbar.updateConstructionInfo

fun resetStavu(state: MongeState, resetConstructionModifier: Boolean = true) {
    // přímkové plochy web nemá
    val shouldResetConstructionModifier =
        resetConstructionModifier &&
                state.drawobjects != Mongeobjects.TRANSPARALLEL &&
                state.drawobjects != Mongeobjects.TRANSORTH

    state.tempLine = null
    if (shouldResetConstructionModifier) state.constructionModifier = ConstructionModifier.NONE
    state.pendingAxoLineCompletion = null
    state.pendingAxoSegmentCompletion = null
    state.pendingAxoPointCompletion = null
    state.pendingAxoPointCompletionFromNarys = null
    state.pendingAxoPointCompletionFromBokorys = null
    state.pendingAxoPointCompletionFromAxo = null
    state.pendingLineTrimPick = null
    state.completingLineSecondStart = null
    state.completingSegmentSecondStart = null
    state.completingSegmentSecondEnd = null
    state.showLineCompletionErrorDialog = false
    state.lineCompletionErrorMessage = ""
    state.showConstructionErrorDialog = false
    state.constructionErrorMessage = ""
    state.lineStartPoint3DNarys = null
    state.lineStartPoint3DPudorys = null
    state.lineStartPoint3DBokorys = null
    state.selectedPointsPudorys.clear()
    state.selectedPointsNarys.clear()
    state.selectedPointsBokorys.clear()
    state.selectedLinesNarys.clear()
    state.pendingAxoPoint2 = null
    state.pendingAxoLine = null
    state.pendingAOLine = null
    state.pendingAOPoint = null
    state.pendingAOSegment = null
    state.selectedLinesBokorys.clear()
    state.pendingAxoPoint1 = null
    state.hoveredLineNarys = null
    state.selectedLinesPudorys.clear()
    state.selectedLineForParallelNarys = null
    state.selectedLineForParallelPudorys = null
    state.selectedLineForParallelBokorys = null
    state.selectedLineForParallelAxo = null
    state.selectedLineForParallelAO = null
    state.selectedLineForParallelPlanePudorys = null
    state.selectedLineForParallelPlaneNarys = null
    state.selectedSegmentsNarys.clear()
    state.selectedSegmentsPudorys.clear()
    state.selectedSegmentsBokorys.clear()
    state.selectedSegmentsAxo.clear()
    state.selectedLinesAxo.clear()
    state.selectedLineIdsPudorys.clear()
    state.selectedLineIdsNarys.clear()
    state.selectedLineIdsBokorys.clear()
    state.selectedLineIdsAxo.clear()
    state.selectedAOLineIds.clear()
    state.selectedAOSegIds.clear()
    state.selectedSegmentForParallelNarys=null
    state.selectedSegmentForParallelPudorys=null
    state.selectedSegmentForParallelBokorys=null
    state.selectedSegmentForParallelAxo = null
    state.selectedSegmentForParallelAO = null
    state.hoveredLinePudorys = null
    state.projectionPhase = when (state.mongeMode) {
        DrawingModeMonge.PUDORYS -> "pudorys_start"
        DrawingModeMonge.NARYS -> "narys_start"
    }

    state.selectedRevolutionAxis3D = null
    state.selectedMeridianNarysIds.clear()
    state.selectedMeridianPudorysIds.clear()
    state.helixAxis3D = null
    state.helixTangent3D = null
    state.helixStartPoint3D = null
    state.helixStartRadial = null
    state.helixRadius = null
    state.helixReducedHeight = null
    state.helixPitchAngleRadians = null
    state.helixRotationSign = 1
    state.helixRightHanded = true
    state.helixPitchAngleTransferActive = false
    state.showHelixTurnsDialog = false
    state.showHelixPitchDialog = false
    state.midpointPoint1 = null
    state.selectedPlaneForCircle = null
 state.selectedLineForParallelNarysSecond = null
 state.selectedLineForParallelPudorysSecond = null
    state.selectedPlanes.clear()
    state.isNameConfirmed = false
    state.isKotaConfirmed = false
    state.rename.reset()
    state.pendingXnarys = null
    state.pendingXpudorys = null
    state.pendingDirection = null
    state.pendingDirectionNarys = null
    state.pendingY = null
    state.pendingX = null
    state.pendingZ = null
    resetAfterAssociated(state)

    state.tempCircle = null
    state.showSpecialLineDialog.value = false
    state.arc.reset()
    resetPlaneConstruction(state)
    state.activeConicIdForArc = null
    state.pendingHypA1 = null
    state.pendingHypA2 = null
    state.pendingHypBranch1SX = null
    state.pendingHypBranch2SX = null
    state.activeParentConic3DIdForEllipseArc = null
    state.segmentStartNarys = null
    state.segmentStartPudorys = null
    state.segmentStartBokorys = null
    state.pendingXA = null
    state.pendingYA = null
    state.pendingZA = null
    state.pendingXB = null
    state.pendingYB = null
    state.pendingZB = null
    state.pendingLinePointPudorys = null
    state.pendingLinePointNarys = null
    state.pendingLinePointBokorys= null
    state.pendingPoint1 = null
    state.pendingPoint2 = null
    state.pendingAOLine = null
    state.pendingAOSegment = null
    state.pendingPoint3 = null
    state.pendingAngle = null
    state.pendingDistance = null
    state.reusingExistingProjection = false
    state.completionPending = null
    state.inputName = ""
    state.inputKota = ""
    state.pendingMongeModeChange = null
    state.selectedAidPointIds.clear()
    state.pendingConic3DId=null
    state.pendingApex3DId = null
    state.pendingPyramidPolygonId = null
    state.pendingPyramidApexId = null
    state.pendingPrismPolygonId = null
    state.pendingPrismNormal = null
    state.pendingPrismBaseCenter = null
    state.pendingPlatonicPolygonId = null
    state.platonicFlip = false
    state.cylinderPhase = if (state.cylinderPhase == CylinderPhase.PICK_CONIC_PERP || state.cylinderPhase == CylinderPhase.PICK_CENTER_PERP)
        CylinderPhase.PICK_CONIC_PERP else CylinderPhase.PICK_CONIC
    // kvadriky web nemá
    state.suspendRepeatCons = false
    state.kotoLineRefPudorys = null
    state.kotoLinePickedPoints3D.clear()
    state.pendingPlaneTracePudorys = null
    state.kotoSegPickAId = null
    state.kotoSegPickBId = null
    state.kotoLinePickAId = null
    state.kotoLinePickBId = null
    state.planePickAId = null
    state.planePickBId = null
    state.planePickCId = null
    state.linefrom2points = null
    state.selectedLineForPoint=null
    resetAllCurvePickStates(state)
    // AXO overlay web nemá
    updateConstructionInfo(state)

}
fun resetPlaneConstruction(state: MongeState) {
    state.tracePlaneNarys = null
    state.tracePlanePudorys = null
    state.tracePlaneBokorys = null
    state.firstPlaneTraceStartNarys = null
    state.firstPlaneTraceStartPudorys = null
    state.firstPlaneTraceStartBokorys = null
    state.xOnX12Pudorys = null
    state.xOnX12Narys = null
    state.selectedLineForParallelPlanePudorys = null
    state.selectedLineForParallelPlaneNarys = null
    state.skipNextClick = false
    state.selectedLinesPudorys.clear()
    state.selectedLinesNarys.clear()
}
fun resetAfterAssociated (state: MongeState){
    state.pendingSegmentNarys.clear()
    state.pendingSegmentPudorys.clear()
    state.pendingLineNarysCompletion.clear()
    state.pendingLinePudorysCompletion.clear()
}
fun resetPudorysCurvePick(state: MongeState) {
    state.pudorysCurvePickRefs.clear()
    state.pudorysCurveLastPickedKey = null
}
fun resetNarysCurvePick(state: MongeState) {
    state.narysCurvePickPointIds.clear()
    state.narysCurveLastPickedId = null
}
fun resetCurve3DPick(state: MongeState) {
    state.curve3DPickPointIds.clear()
    state.curve3DLastPickedId = null
}
fun resetBokorysСurvePick(state: MongeState) {
    state.bokorysCurvePickPointIds.clear()
    state.bokorysCurveLastPickedId = null
}
fun resetAllCurvePickStates(state: MongeState) {
    resetPudorysCurvePick(state)
    resetNarysCurvePick(state)
    resetBokorysСurvePick(state)
    resetCurve3DPick(state)
}
