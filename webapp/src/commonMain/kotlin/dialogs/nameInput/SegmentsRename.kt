package dialogs.nameInput

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.LocalMongeColors
import model.classes.AxoOverlaySegment
import model.classes.HelpSegmentNarys
import model.classes.HelpSegmentPudorys
import model.classes.Segment2DAxo
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import model.classes.SegmentsBokorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import serialization.SettingsManager
import serialization.commitSnapshot
import state.MongeState
import ui.mongeui.toolbar.SkikoButton
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo

private const val RENAME_SEGMENT_PUDORYS = "rename_segment_pudorys"
private const val RENAME_SEGMENT_NARYS = "rename_segment_narys"
private const val RENAME_SEGMENT_BOKORYS = "rename_segment_bokorys"
private const val RENAME_SEGMENT_AXO = "rename_segment_axo"
private const val RENAME_SEGMENT_AO = "rename_segment_ao"

private fun Segment2DPudorys.parentFromState(state: MongeState): Segment3D? =
    parent ?: parentId?.let { id -> state.segments3D.firstOrNull { it.id == id } }

private fun Segment2DNarys.parentFromState(state: MongeState): Segment3D? =
    parent ?: parentId?.let { id -> state.segments3D.firstOrNull { it.id == id } }

private fun Segment2DBokorys.parentFromState(state: MongeState): Segment3D? =
    parent ?: parentId?.let { id -> state.segments3D.firstOrNull { it.id == id } }

private fun Segment2DAxo.parentFromState(state: MongeState): Segment3D? =
    parent ?: parentId?.let { id -> state.segments3D.firstOrNull { it.id == id } }

private fun SegmentsPudorys.renameName(state: MongeState): String =
    when (this) {
        is Segment2DPudorys -> parentFromState(state)?.name ?: name.orEmpty()
        is HelpSegmentPudorys -> parent?.name ?: name.orEmpty()
        else -> parent?.name ?: name.orEmpty()
    }

private fun SegmentsNarys.renameName(state: MongeState): String =
    when (this) {
        is Segment2DNarys -> parentFromState(state)?.name ?: name.orEmpty()
        is HelpSegmentNarys -> parent?.name ?: name.orEmpty()
        else -> parent?.name ?: name.orEmpty()
    }

private fun SegmentsBokorys.renameName(state: MongeState): String =
    when (this) {
        is Segment2DBokorys -> parentFromState(state)?.name ?: name.orEmpty()
        else -> parent?.name ?: name.orEmpty()
    }

private fun Segment2DAxo.renameName(state: MongeState): String =
    parentFromState(state)?.name ?: name.orEmpty()

private fun AxoOverlaySegment.renameName(): String =
    name.orEmpty()

private fun refreshSegmentSelections(state: MongeState) {
    val segments3DById = state.segments3D.associateBy { it.id }
    state.selectedSegments3D.indices.forEach { i ->
        state.selectedSegments3D[i] = segments3DById[state.selectedSegments3D[i].id] ?: state.selectedSegments3D[i]
    }

    val pudorysById = (state.segmentsPudorys + state.helpSegmentsPudorys).associateBy { it.id }
    state.selectedSegmentsPudorys.indices.forEach { i ->
        state.selectedSegmentsPudorys[i] = pudorysById[state.selectedSegmentsPudorys[i].id] ?: state.selectedSegmentsPudorys[i]
    }

    val narysById = (state.segmentsNarys + state.helpSegmentsNarys).associateBy { it.id }
    state.selectedSegmentsNarys.indices.forEach { i ->
        state.selectedSegmentsNarys[i] = narysById[state.selectedSegmentsNarys[i].id] ?: state.selectedSegmentsNarys[i]
    }

    val bokorysById = state.segmentsBokorys.associateBy { it.id }
    state.selectedSegmentsBokorys.indices.forEach { i ->
        state.selectedSegmentsBokorys[i] = bokorysById[state.selectedSegmentsBokorys[i].id] ?: state.selectedSegmentsBokorys[i]
    }

    val axoById = state.segmentsAxo.associateBy { it.id }
    state.selectedSegmentsAxo.indices.forEach { i ->
        state.selectedSegmentsAxo[i] = axoById[state.selectedSegmentsAxo[i].id] ?: state.selectedSegmentsAxo[i]
    }
}

private fun renameSegmentParent(state: MongeState, oldParent: Segment3D, newName: String) {
    val newParent = oldParent.copy(name = newName)
    state.segments3D.indexOfFirst { it.id == oldParent.id }.takeIf { it >= 0 }?.let { idx ->
        state.segments3D[idx] = newParent
    }

    state.segmentsPudorys.indices.forEach { idx ->
        val s = state.segmentsPudorys[idx]
        val parentId = s.parent?.id ?: s.parentId
        if (parentId == oldParent.id) {
            state.segmentsPudorys[idx] = s.copy(name = null, parent = newParent, parentId = oldParent.id)
        }
    }
    state.segmentsNarys.indices.forEach { idx ->
        val s = state.segmentsNarys[idx]
        val parentId = s.parent?.id ?: s.parentId
        if (parentId == oldParent.id) {
            state.segmentsNarys[idx] = s.copy(name = null, parent = newParent, parentId = oldParent.id)
        }
    }
    state.segmentsBokorys.indices.forEach { idx ->
        val s = state.segmentsBokorys[idx]
        val parentId = s.parent?.id ?: s.parentId
        if (parentId == oldParent.id) {
            state.segmentsBokorys[idx] = s.copy(name = null, parent = newParent, parentId = oldParent.id)
        }
    }
    state.segmentsAxo.indices.forEach { idx ->
        val s = state.segmentsAxo[idx]
        val parentId = s.parent?.id ?: s.parentId
        if (parentId == oldParent.id) {
            state.segmentsAxo[idx] = s.copy(name = null, parent = newParent, parentId = oldParent.id)
        }
    }
    state.helpSegmentsPudorys.indices.forEach { idx ->
        val s = state.helpSegmentsPudorys[idx]
        if (s.parent?.id == oldParent.id) {
            state.helpSegmentsPudorys[idx] = s.copy(name = null, parent = newParent)
        }
    }
    state.helpSegmentsNarys.indices.forEach { idx ->
        val s = state.helpSegmentsNarys[idx]
        if (s.parent?.id == oldParent.id) {
            state.helpSegmentsNarys[idx] = s.copy(name = null, parent = newParent)
        }
    }

    refreshSegmentSelections(state)
}

private fun renamePudorysSegment(state: MongeState, segment: SegmentsPudorys, newName: String) {
    val parent = when (segment) {
        is Segment2DPudorys -> segment.parentFromState(state)
        is HelpSegmentPudorys -> segment.parent
        else -> segment.parent
    }
    if (parent != null) {
        renameSegmentParent(state, parent, newName)
        return
    }

    when (segment) {
        is Segment2DPudorys -> state.segmentsPudorys.indexOfFirst { it.id == segment.id }.takeIf { it >= 0 }?.let { idx ->
            state.segmentsPudorys[idx] = segment.copy(name = newName)
        }
        is HelpSegmentPudorys -> state.helpSegmentsPudorys.indexOfFirst { it.id == segment.id }.takeIf { it >= 0 }?.let { idx ->
            state.helpSegmentsPudorys[idx] = segment.copy(name = newName)
        }
        else -> segment.name = newName
    }
    refreshSegmentSelections(state)
}

private fun renameNarysSegment(state: MongeState, segment: SegmentsNarys, newName: String) {
    val parent = when (segment) {
        is Segment2DNarys -> segment.parentFromState(state)
        is HelpSegmentNarys -> segment.parent
        else -> segment.parent
    }
    if (parent != null) {
        renameSegmentParent(state, parent, newName)
        return
    }

    when (segment) {
        is Segment2DNarys -> state.segmentsNarys.indexOfFirst { it.id == segment.id }.takeIf { it >= 0 }?.let { idx ->
            state.segmentsNarys[idx] = segment.copy(name = newName)
        }
        is HelpSegmentNarys -> state.helpSegmentsNarys.indexOfFirst { it.id == segment.id }.takeIf { it >= 0 }?.let { idx ->
            state.helpSegmentsNarys[idx] = segment.copy(name = newName)
        }
        else -> segment.name = newName
    }
    refreshSegmentSelections(state)
}

private fun renameBokorysSegment(state: MongeState, segment: SegmentsBokorys, newName: String) {
    val parent = when (segment) {
        is Segment2DBokorys -> segment.parentFromState(state)
        else -> segment.parent
    }
    if (parent != null) {
        renameSegmentParent(state, parent, newName)
        return
    }

    when (segment) {
        is Segment2DBokorys -> state.segmentsBokorys.indexOfFirst { it.id == segment.id }.takeIf { it >= 0 }?.let { idx ->
            state.segmentsBokorys[idx] = segment.copy(name = newName)
        }
        else -> segment.name = newName
    }
    refreshSegmentSelections(state)
}

private fun renameAxoSegment(state: MongeState, segment: Segment2DAxo, newName: String) {
    val parent = segment.parentFromState(state)
    if (parent != null) {
        renameSegmentParent(state, parent, newName)
        return
    }

    state.segmentsAxo.indexOfFirst { it.id == segment.id }.takeIf { it >= 0 }?.let { idx ->
        state.segmentsAxo[idx] = segment.copy(name = newName)
    }
    refreshSegmentSelections(state)
}

private fun renameAOSegment(state: MongeState, segment: AxoOverlaySegment, newName: String) {
    state.axoOverlaySegments.indexOfFirst { it.id == segment.id }.takeIf { it >= 0 }?.let { idx ->
        state.axoOverlaySegments[idx] = segment.copy(name = newName)
    }
}

private fun activeSegmentRenameName(state: MongeState): String =
    when (state.projectionPhase) {
        RENAME_SEGMENT_PUDORYS -> state.rename.segmentBeingRenamedPudorys?.renameName(state).orEmpty()
        RENAME_SEGMENT_NARYS -> state.rename.segmentBeingRenamedNarys?.renameName(state).orEmpty()
        RENAME_SEGMENT_BOKORYS -> state.rename.segmentBeingRenamedBokorys?.renameName(state).orEmpty()
        RENAME_SEGMENT_AXO -> state.rename.segmentBeingRenamedAxo?.renameName(state).orEmpty()
        RENAME_SEGMENT_AO -> state.pendingAOSegment?.renameName().orEmpty()
        else -> ""
    }

private fun activeSegmentRenameToken(state: MongeState): String? =
    when (state.projectionPhase) {
        RENAME_SEGMENT_PUDORYS -> state.rename.segmentBeingRenamedPudorys?.id
        RENAME_SEGMENT_NARYS -> state.rename.segmentBeingRenamedNarys?.id
        RENAME_SEGMENT_BOKORYS -> state.rename.segmentBeingRenamedBokorys?.id
        RENAME_SEGMENT_AXO -> state.rename.segmentBeingRenamedAxo?.id
        RENAME_SEGMENT_AO -> state.pendingAOSegment?.id
        else -> null
    }

private fun returnPhaseForSegmentRename(phase: String?): String =
    when (phase) {
        RENAME_SEGMENT_NARYS -> "narys_start"
        RENAME_SEGMENT_BOKORYS -> "bokorys_start"
        else -> "pudorys_start"
    }

private fun clearSegmentRenameTargets(state: MongeState) {
    state.rename.segmentBeingRenamedPudorys = null
    state.rename.segmentBeingRenamedNarys = null
    state.rename.segmentBeingRenamedBokorys = null
    state.rename.segmentBeingRenamedAxo = null
    state.pendingAOSegment = null
}

private fun confirmSegmentRename(state: MongeState) {
    val phase = state.projectionPhase
    val newName = state.inputName.trim()

    when (phase) {
        RENAME_SEGMENT_PUDORYS -> state.rename.segmentBeingRenamedPudorys?.let { renamePudorysSegment(state, it, newName) }
        RENAME_SEGMENT_NARYS -> state.rename.segmentBeingRenamedNarys?.let { renameNarysSegment(state, it, newName) }
        RENAME_SEGMENT_BOKORYS -> state.rename.segmentBeingRenamedBokorys?.let { renameBokorysSegment(state, it, newName) }
        RENAME_SEGMENT_AXO -> state.rename.segmentBeingRenamedAxo?.let { renameAxoSegment(state, it, newName) }
        RENAME_SEGMENT_AO -> state.pendingAOSegment?.let { renameAOSegment(state, it, newName) }
        else -> return
    }

    state.isNameConfirmed = true
    setProjectionPhase(returnPhaseForSegmentRename(phase), state)
    clearSegmentRenameTargets(state)
    commitSnapshot(state)
    repeatCons(state)
    updateConstructionInfo(state)
}

@Composable
fun RenameSegmentDialog(state: MongeState) {
    val phase = state.projectionPhase
    val isSegmentRename = phase == RENAME_SEGMENT_PUDORYS ||
            phase == RENAME_SEGMENT_NARYS ||
            phase == RENAME_SEGMENT_BOKORYS ||
            phase == RENAME_SEGMENT_AXO ||
            phase == RENAME_SEGMENT_AO

    if (!isSegmentRename || state.isNameConfirmed) return

    val ui = SettingsManager.current.UIscale / 75f
    val colors = LocalMongeColors.current
    val token = activeSegmentRenameToken(state)

    LaunchedEffect(phase, token) {
        state.inputName = activeSegmentRenameName(state)
    }

    fun dismiss() {
        state.isNameConfirmed = true
        setProjectionPhase(returnPhaseForSegmentRename(phase), state)
        clearSegmentRenameTargets(state)
    }

    MongeDialog(
        onDismissRequest = { dismiss() },
        title = {
            Text(
                "Název úsečky",
                fontSize = (18f * ui).sp,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        },
        text = {
            SegmentNameField(state) { confirmSegmentRename(state) }
        },
        confirmButton = {
            SegmentRenameConfirmButton(ui) { confirmSegmentRename(state) }
        },
        dismissButton = {
            SkikoButton(onClick = { dismiss() }) {
                Text("Zrušit")
            }
        },
        ui = ui
    )
}

@Composable
private fun ColumnScope.SegmentNameField(
    state: MongeState,
    onSubmit: () -> Unit
) {
    MongeTextField(
        value = state.inputName,
        onValueChange = { state.inputName = it },
        label = "Název",
        onDone = onSubmit,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
}

@Composable
private fun RowScope.SegmentRenameConfirmButton(
    ui: Float,
    onClick: () -> Unit
) {
    SkikoButton(onClick = onClick) {
        Icon(painterResource("icons/check.svg"), null, Modifier.size((24f * ui).dp))
        Text("OK", Modifier.padding(horizontal = (8f * ui).dp))
    }
}
