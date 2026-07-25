package state

import model.DrawingModeMonge

/*
 * Reset rozpracovaného pojmenování 3D přímky. Dřív v
 * `dialogs/nameInput/LinesRename.kt`, ale je to operace nad MongeState,
 * kterou volá i doplňování průmětů.
 */
fun MongeState.reset3DLineNaming() {
    isNameConfirmed = true
    rename.helplineBeingRenamedNarys = null
    rename.helplineBeingRenamedPudorys = null
    projectionPhase = when (mongeMode) {
        DrawingModeMonge.PUDORYS -> {"pudorys_start"}
        DrawingModeMonge.NARYS -> "narys_start"
    }

    inputName = ""
    rename.lineBeingRenamed3D = null
    rename.lineBeingRenamedNarys = null
    pendingAOLine = null
    pendingPoint1 = null
    rename.lineBeingRenamedPudorys = null
    rename.pointNarysBeingRenamed = null
    rename.pointPudorysBeingRenamed = null
    pendingX = null
    pendingY = null
    pendingZ = null
    pendingDirection = null
    pendingDirectionNarys = null
    showSpecialLineDialog.value = false
}

