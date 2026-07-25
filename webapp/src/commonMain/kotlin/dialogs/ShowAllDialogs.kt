package dialogs

import dialogs.linecomplete.LineCompletionErrorDialog
import dialogs.nameInput.*
import dialogs.specialLineCase.SpecialLineCasePudorysDialog
import dialogs.tools.TypeAngleDialogHandler
import dialogs.tools.TypeDistanceDialogHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import state.MongeState
import ui.mongeui.ReturnFocusWhenClosed

@Composable
fun Dialogs(state: MongeState,requestGlobalFocus: () -> Unit){
    CompositionLocalProvider(
        LocalDialogFocusReturn provides requestGlobalFocus,
        LocalDialogHostState provides state
    ) {
    val namingPhases = setOf(
        "narys_finalize", "pudorys_finalize", "rename_point_pudorys",
        "single_pudorys", "single_narys", "single_bokorys", "single_axo",
        "single_axo_projection", "axo_finalize", "bokorys_finalize",
        "KOTO_point", "koto_plane_finalize", "naming_3d_line",
        "single_pudorys_line", "single_narys_line", "single_bokorys_line",
        "ao_line_naming", "axo_line_naming", "rename_axol",
        "rename_line_pudorys", "rename_line_narys", "rename_line_bokorys",
        "rename_helpline_pudorys", "rename_helpline_narys", "rename_aol",
        "rename_segment_pudorys", "rename_segment_narys", "rename_segment_bokorys",
        "rename_segment_axo", "rename_segment_ao", "rename_plane", "rename_aid", "rename_aop"
    )
    val namingDialogVisible =
        state.showPlaneNamingDialog ||
            state.pendingAidPoint != null || state.pendingAOPoint != null ||
            (!state.isNameConfirmed && state.projectionPhase in namingPhases)
    ReturnFocusWhenClosed(namingDialogVisible, requestGlobalFocus)
//ALERT DIALOG pro speciální přímku

    SpecialLineCasePudorysDialog(state)
    LineCompletionErrorDialog(state)
    ReturnFocusWhenClosed(state.showLineCompletionErrorDialog, requestGlobalFocus)
    ConstructionErrorDialog(state)
    UnsupportedContentDialog(state)
    EmptyIntersectionDialog(state)
    ReturnFocusWhenClosed(state.showEmptyIntersectionDialog, requestGlobalFocus)
    export.ExportDialogPopup(
        show = state.showExportDialog,
        state = state,
        onDismiss = { state.showExportDialog = false }
    )
    ReturnFocusWhenClosed(state.showExportDialog, requestGlobalFocus)
    if (state.showSettingsDialog) {
        dialogs.settings.SettingsDialog(onClose = { state.showSettingsDialog = false })
    }
    ReturnFocusWhenClosed(state.showSettingsDialog, requestGlobalFocus)
    ReturnFocusWhenClosed(state.showConstructionErrorDialog, requestGlobalFocus)

    

    
// modální „počítám průnik" – výpočet běží na pozadí (IntersectionRouter)
    if (state.intersectionComputing) {
        dialogs.Alerts.LoadingDialog(title = "Výpočet průniku")
    }
    ReturnFocusWhenClosed(state.intersectionComputing, requestGlobalFocus)
// modální „převádím do axonometrie" – konverze běží na pozadí (AxoSetupUI)
    if (state.axoConversionComputing) {
        dialogs.Alerts.LoadingDialog(title = "Převod do axonometrie")
    }
    ReturnFocusWhenClosed(state.axoConversionComputing, requestGlobalFocus)
    ReturnFocusWhenClosed(state.showSpecialLineDialog.value, requestGlobalFocus)
//ALERT DIALOG pro pojmenování bodu-funguje
    NamePointsDialog(state)
//přejmenování 3D bodů
    RenamePointsNarysDialog(state)
    RenamePointsBokorysDialog(state)
    RenamePointsAxoDialog(state)
//ALERT DIALOG pro pojmenování sdružených průmětů přímky + 3D přímka
    Name3DLineDialog(state)
//ALERT DIALOG pro pojmenování přímky NARYS
    NameLineNarysDialog(state)
    NamedLineBokorysDialog(state)

    RenameLineBokorysDialog(state)
//ALERT DIALOG pro pojmenování přímky Pudorys
    val showNameLineP =
        state.projectionPhase == "single_pudorys_line" && !state.isNameConfirmed
    NameLinePudorysDialog(state)
    NameAOLineDialog(state)
    NameAxoLineDialog(state)
    ReturnFocusWhenClosed(showNameLineP, requestGlobalFocus)
    RenameAxoLineDialog(state)
//ALERT DIALOG pro pojmenování rovin
    PlaneNameDialog(state)
    ReturnFocusWhenClosed(state.showPlaneNamingDialog, requestGlobalFocus)
// vytvoření single půdorys bodu pomocí sdružených průmětů + rename
    RenamePointsPudorysDialog(state)

    RedoPointsKotoDialog(state)
// přejmenování nárys přímky
    RenameLineNarysDialog(state)
    RenameHelpLineNarysDialog(state)
//přejmenování půdorys přímky
    RenameLinePudorysDialog(state)
    RenameHelpLinePudorysDialog(state)
    RenameAOLineDialog(state)
    RenameSegmentDialog(state)
//přejmenování roviny
    PlaneRenameDialog(state)
//zadání vzdálenosti
    TypeDistanceDialogHandler(state)
    ReturnFocusWhenClosed(state.showTypeDistanceDialog, requestGlobalFocus)
//zadání úhlu
    TypeAngleDialogHandler(state)
    ReturnFocusWhenClosed(state.showTypeAngleDialog, requestGlobalFocus)
// šroubovice

    

    
//přejmenování pomocných bodů
    if (state.pendingAidPoint != null) {
   RenameAidPointDialog(state)
    }
    if (state.pendingAOPoint != null) {
        RenameOverlayPointDialog(state)
    }

    }
}
