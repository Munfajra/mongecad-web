package ui.mongeui.toolbar.rightDescriptionBar
import utils.replaceAll

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.ProjectionMode
import model.classes.*
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.mongeui.toolbar.SkikoButton

enum class ConeProjectionKind { AXO, PUDORYS, NARYS, BOKORYS }

fun applyConeColorNoCommit(state: MongeState, coneId: String, newColor: Color) {
    val idx = state.conicalSurfaces.indexOfFirst { it.id == coneId }
    if (idx < 0) return

    val oldCone = state.conicalSurfaces[idx]
    val updatedCone = oldCone.copy(color = newColor)
    state.conicalSurfaces[idx] = updatedCone

    val dirId = updatedCone.directrixId
    var updatedDirectrix = state.conics3D.firstOrNull { it.id == dirId }
    state.conics3D.indexOfFirst { it.id == dirId }.takeIf { it >= 0 }?.let { i ->
        val coloredDirectrix = state.conics3D[i].copy(color = newColor).apply {
            directrixOfSurfaceIds = state.conics3D[i].directrixOfSurfaceIds
        }
        updatedDirectrix = coloredDirectrix
        state.conics3D[i] = coloredDirectrix
    }
    val directrixForProjections = updatedDirectrix

    state.conicsPudorys.replaceAll {
        if (it.parent?.id == dirId || it.parentId == dirId) it.copy(parent = directrixForProjections, parentId = dirId, localColor = newColor) else it
    }
    state.conicsNarys.replaceAll {
        if (it.parent?.id == dirId || it.parentId == dirId) it.copy(parent = directrixForProjections, parentId = dirId, localColor = newColor) else it
    }
    state.conicsBokorys.replaceAll {
        if (it.parent?.id == dirId || it.parentId == dirId) it.copy(parent = directrixForProjections, parentId = dirId, localColor = newColor) else it
    }
    state.conicsAxo.replaceAll {
        if (it.parent?.id == dirId || it.parentId == dirId) it.copy(parent = directrixForProjections, parentId = dirId, localColor = newColor) else it
    }

    val apexId = updatedCone.apexId
    var updatedApex = state.sharedPoints3D.firstOrNull { it.id == apexId }
    state.sharedPoints3D.indexOfFirst { it.id == apexId }.takeIf { it >= 0 }?.let { i ->
        updatedApex = state.sharedPoints3D[i].copy(color = newColor)
        state.sharedPoints3D[i] = updatedApex
    }

    val coneSegmentsP = state.segmentsPudorys.filter { it.conicalSurfaceId == updatedCone.id || it.id in updatedCone.edgeSegIdsPudorys2D }
    val coneSegmentsN = state.segmentsNarys.filter { it.conicalSurfaceId == updatedCone.id || it.id in updatedCone.edgeSegIdsNarys2D }
    val coneSegmentsB = state.segmentsBokorys.filter { it.conicalSurfaceId == updatedCone.id || it.id in updatedCone.edgeSegIdsBokorys2D }
    val coneSegmentsA = state.segmentsAxo.filter { it.conicalSurfaceId == updatedCone.id || it.id in updatedCone.edgeSegIdsAxo2D }

    val pointIdsP = (updatedCone.edgePointIdsPudorys2D + listOfNotNull(updatedCone.apexProjPudorysId) + coneSegmentsP.flatMap { listOf(it.start.id, it.end.id) }).toSet()
    val pointIdsN = (updatedCone.edgePointIdsNarys2D + listOfNotNull(updatedCone.apexProjNarysId) + coneSegmentsN.flatMap { listOf(it.start.id, it.end.id) }).toSet()
    val pointIdsB = (updatedCone.edgePointIdsBokorys2D + listOfNotNull(updatedCone.apexProjBokorysId) + coneSegmentsB.flatMap { listOf(it.start.id, it.end.id) }).toSet()
    val pointIdsA = (updatedCone.edgePointIdsAxo2D + listOfNotNull(updatedCone.apexProjAxoId) + coneSegmentsA.flatMap { listOf(it.start.id, it.end.id) }).toSet()

    state.pointsPudorys.replaceAll {
        if (it.parent?.id == apexId || it.id in pointIdsP) it.copy(parent = if (it.parent?.id == apexId) updatedApex else it.parent, localColor = newColor) else it
    }
    state.pointsNarys.replaceAll {
        if (it.parent?.id == apexId || it.id in pointIdsN) it.copy(parent = if (it.parent?.id == apexId) updatedApex else it.parent, localColor = newColor) else it
    }
    state.pointsBokorys.replaceAll {
        if (it.parent?.id == apexId || it.id in pointIdsB) it.copy(parent = if (it.parent?.id == apexId) updatedApex else it.parent, localColor = newColor) else it
    }
    state.pointsAxo.replaceAll {
        if (it.parent?.id == apexId || it.id in pointIdsA) it.copy(parent = if (it.parent?.id == apexId) updatedApex else it.parent, localColor = newColor) else it
    }

    state.segmentsPudorys.replaceAll { if (it.conicalSurfaceId == updatedCone.id || it.id in updatedCone.edgeSegIdsPudorys2D) it.copy(localColor = newColor) else it }
    state.segmentsNarys.replaceAll { if (it.conicalSurfaceId == updatedCone.id || it.id in updatedCone.edgeSegIdsNarys2D) it.copy(localColor = newColor) else it }
    state.segmentsBokorys.replaceAll { if (it.conicalSurfaceId == updatedCone.id || it.id in updatedCone.edgeSegIdsBokorys2D) it.copy(localColor = newColor) else it }
    state.segmentsAxo.replaceAll { if (it.conicalSurfaceId == updatedCone.id || it.id in updatedCone.edgeSegIdsAxo2D) it.copy(localColor = newColor) else it }

    state.selectedConicsPudorys.clear()
    state.selectedConicsNarys.clear()
    state.selectedConicsBokorys.clear()
    state.selectedConicsAxo.clear()

    state.selectedCone.replaceAll { if (it.id == updatedCone.id) updatedCone else it }
    if (state.selectedConicalSurface?.id == updatedCone.id) {
        state.selectedConicalSurface = updatedCone
    }

    state.triggerRedraw++
}

private fun setConeProjectionVisibility(
    state: MongeState,
    cone: ConicalSurface3D,
    kind: ConeProjectionKind,
    checked: Boolean
) {
    fun setConicP(c: ConicSectionPudorys) { c.showInAxoInitial = checked; c.showInAxo = checked }
    fun setConicN(c: ConicSectionNarys) { c.showInAxoInitial = checked; c.showInAxo = checked }
    fun setConicB(c: ConicSectionBokorys) { c.showInAxoInitial = checked; c.showInAxo = checked }
    fun setConicA(c: ConicSectionAxo) { c.showInAxoInitial = checked; c.showInAxo = checked }
    fun setPointP(p: Point3DPudorys) { p.showInAxoInitial = checked; p.showInAxo = checked }
    fun setPointN(p: Point3DNarys) { p.showInAxoInitial = checked; p.showInAxo = checked }
    fun setPointB(p: Point3DBokorys) { p.showInAxoInitial = checked; p.showInAxo = checked }
    fun setPointA(p: Point3DAxo) { p.showInAxoInitial = checked; p.showInAxo = checked }
    fun setSegmentP(s: Segment2DPudorys) { s.showInAxoInitial = checked; s.showInAxo = checked }
    fun setSegmentN(s: Segment2DNarys) { s.showInAxoInitial = checked; s.showInAxo = checked }
    fun setSegmentB(s: Segment2DBokorys) { s.showInAxoInitial = checked; s.showInAxo = checked }
    fun setSegmentA(s: Segment2DAxo) { s.showInAxoInitial = checked; s.showInAxo = checked }

    val directrixId = cone.directrixId
    val apexId = cone.apexId

    when (kind) {
        ConeProjectionKind.PUDORYS -> {
            state.conicsPudorys.filter { it.parent?.id == directrixId || it.parentId == directrixId }.forEach(::setConicP)
            val segments = state.segmentsPudorys.filter { it.conicalSurfaceId == cone.id || it.id in cone.edgeSegIdsPudorys2D }
            segments.forEach(::setSegmentP)
            val pointIds = (cone.edgePointIdsPudorys2D + listOfNotNull(cone.apexProjPudorysId) + segments.flatMap { listOf(it.start.id, it.end.id) }).toSet()
            state.pointsPudorys.filter { it.id in pointIds || it.parent?.id == apexId }.forEach(::setPointP)
        }

        ConeProjectionKind.NARYS -> {
            state.conicsNarys.filter { it.parent?.id == directrixId || it.parentId == directrixId }.forEach(::setConicN)
            val segments = state.segmentsNarys.filter { it.conicalSurfaceId == cone.id || it.id in cone.edgeSegIdsNarys2D }
            segments.forEach(::setSegmentN)
            val pointIds = (cone.edgePointIdsNarys2D + listOfNotNull(cone.apexProjNarysId) + segments.flatMap { listOf(it.start.id, it.end.id) }).toSet()
            state.pointsNarys.filter { it.id in pointIds || it.parent?.id == apexId }.forEach(::setPointN)
        }

        ConeProjectionKind.BOKORYS -> {
            state.conicsBokorys.filter { it.parent?.id == directrixId || it.parentId == directrixId }.forEach(::setConicB)
            val segments = state.segmentsBokorys.filter { it.conicalSurfaceId == cone.id || it.id in cone.edgeSegIdsBokorys2D }
            segments.forEach(::setSegmentB)
            val pointIds = (cone.edgePointIdsBokorys2D + listOfNotNull(cone.apexProjBokorysId) + segments.flatMap { listOf(it.start.id, it.end.id) }).toSet()
            state.pointsBokorys.filter { it.id in pointIds || it.parent?.id == apexId }.forEach(::setPointB)
        }

        ConeProjectionKind.AXO -> {
            state.conicsAxo.filter { it.parent?.id == directrixId || it.parentId == directrixId }.forEach(::setConicA)
            val segments = state.segmentsAxo.filter { it.conicalSurfaceId == cone.id || it.id in cone.edgeSegIdsAxo2D }
            segments.forEach(::setSegmentA)
            val pointIds = (cone.edgePointIdsAxo2D + listOfNotNull(cone.apexProjAxoId) + segments.flatMap { listOf(it.start.id, it.end.id) }).toSet()
            state.pointsAxo.filter { it.id in pointIds || it.parent?.id == apexId }.forEach(::setPointA)
        }
    }

    commitSnapshot(state)
    state.triggerRedraw++
}

private fun coneProjectionVisible(
    state: MongeState,
    cone: ConicalSurface3D,
    kind: ConeProjectionKind
): Boolean {
    val directrixId = cone.directrixId
    val apexId = cone.apexId
    return when (kind) {
        ConeProjectionKind.PUDORYS ->
            state.conicsPudorys.any { (it.parent?.id == directrixId || it.parentId == directrixId) && it.showInAxo } ||
                    state.segmentsPudorys.any { (it.conicalSurfaceId == cone.id || it.id in cone.edgeSegIdsPudorys2D) && it.showInAxo } ||
                    state.pointsPudorys.any { (it.id == cone.apexProjPudorysId || it.id in cone.edgePointIdsPudorys2D || it.parent?.id == apexId) && it.showInAxo }

        ConeProjectionKind.NARYS ->
            state.conicsNarys.any { (it.parent?.id == directrixId || it.parentId == directrixId) && it.showInAxo } ||
                    state.segmentsNarys.any { (it.conicalSurfaceId == cone.id || it.id in cone.edgeSegIdsNarys2D) && it.showInAxo } ||
                    state.pointsNarys.any { (it.id == cone.apexProjNarysId || it.id in cone.edgePointIdsNarys2D || it.parent?.id == apexId) && it.showInAxo }

        ConeProjectionKind.BOKORYS ->
            state.conicsBokorys.any { (it.parent?.id == directrixId || it.parentId == directrixId) && it.showInAxo } ||
                    state.segmentsBokorys.any { (it.conicalSurfaceId == cone.id || it.id in cone.edgeSegIdsBokorys2D) && it.showInAxo } ||
                    state.pointsBokorys.any { (it.id == cone.apexProjBokorysId || it.id in cone.edgePointIdsBokorys2D || it.parent?.id == apexId) && it.showInAxo }

        ConeProjectionKind.AXO ->
            state.conicsAxo.any { (it.parent?.id == directrixId || it.parentId == directrixId) && it.showInAxo } ||
                    state.segmentsAxo.any { (it.conicalSurfaceId == cone.id || it.id in cone.edgeSegIdsAxo2D) && it.showInAxo } ||
                    state.pointsAxo.any { (it.id == cone.apexProjAxoId || it.id in cone.edgePointIdsAxo2D || it.parent?.id == apexId) && it.showInAxo }
    }
}

@Composable
fun EditableConeInfo(
    coneId: String,
    state: MongeState,
    onApply: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onDelete: () -> Unit,
    uiScale: Float
) {
    val cone = state.conicalSurfaces.find { it.id == coneId } ?: return

    val ui = remember(uiScale) { UiScale(uiScale) }
    var pendingColor by remember(cone.id, cone.color) {
        mutableStateOf(cone.color)
    }
    var sliderValue by remember(cone.id, cone.wireWidth) {
        mutableStateOf(cone.wireWidth)
    }

    val nameNow = cone.name.trim()

    var pendingName by remember(cone.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(cone.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(cone.id, cone.name) {
        val n = cone.name.trim()
        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }

    val canApply = pendingName.text.trim() != lastAppliedName

    fun apply() {
        if (!canApply) return

        val newName = pendingName.text.trim()

        onApply(newName)

        lastAppliedName = newName
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        SimpleNameEditor(
            label = "Kužel:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { apply() },
            state = state
        )


        MongeDivider()


            MongeInspectorPropertyRow("Barva:") {
                ColorPickerDropdown(
                    selectedColor = pendingColor,
                    onColorPreview = { pendingColor = it },
                    onColorConfirm = { c ->
                        pendingColor = c
                        onColorChange(c)
                    }
                )
            }

        MongeDivider()

        MongeInspectorPropertyRow("Šířka:", contentAlign = Alignment.End) {
            WidthEditor(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onStrokeWidthChange(it)
                },
                state = state
            )
        }
        if (state.projectionMode == ProjectionMode.AXO) {
            MongeDivider()

            MongeInspectorPropertyRow(
                label = "Průměty:",
                contentAlign = Alignment.End
            ) {
                ProjectionVisibilityToggleStrip(
                    ui = ui,
                    ProjectionVisibilityToggleItem("A", coneProjectionVisible(state, cone, ConeProjectionKind.AXO)) {
                        setConeProjectionVisibility(state, cone, ConeProjectionKind.AXO, it)
                    },
                    ProjectionVisibilityToggleItem("P", coneProjectionVisible(state, cone, ConeProjectionKind.PUDORYS)) {
                        setConeProjectionVisibility(state, cone, ConeProjectionKind.PUDORYS, it)
                    },
                    ProjectionVisibilityToggleItem("N", coneProjectionVisible(state, cone, ConeProjectionKind.NARYS)) {
                        setConeProjectionVisibility(state, cone, ConeProjectionKind.NARYS, it)
                    },
                    ProjectionVisibilityToggleItem("B", coneProjectionVisible(state, cone, ConeProjectionKind.BOKORYS)) {
                        setConeProjectionVisibility(state, cone, ConeProjectionKind.BOKORYS, it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        MongeDivider()

        ProjectionVisibilityToggleRow(
            label = "Vybarvit:",
            checked = cone.fillFaces,
            onCheckedChange = { checked ->
                val idx = state.conicalSurfaces.indexOfFirst { it.id == cone.id }
                if (idx >= 0) {
                    val updated = state.conicalSurfaces[idx].copy(fillFaces = checked)
                    state.conicalSurfaces[idx] = updated
                    state.selectedCone.replaceAll { if (it.id == updated.id) updated else it }
                    if (state.selectedConicalSurface?.id == updated.id) {
                        state.selectedConicalSurface = updated
                    }
                }
                state.triggerRedraw++
                commitSnapshot(state)
            },
            ui = ui
        )

        MongeDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SkikoButton(
                width = ui.dp(100f),
                height = ui.dp(38f),
                onClick = onDelete
            ) {
                Text(
                    "Smazat",
                    fontSize = ui.sp(13f)
                )
            }
        }
    }
}

fun deleteCone(state: MongeState, cone: ConicalSurface3D) {


    val coneId = cone.id
    val toDelete = state.conicalSurfaces.find { it.id == coneId } ?: return

    // 1) Odváž kužel z directrix (ConicSection3D.directrixOfSurfaceIds)
    state.conics3D.replaceAll { c3d ->
        if (c3d.id != toDelete.directrixId) c3d
        else c3d.also { it.directrixOfSurfaceIds = it.directrixOfSurfaceIds - coneId }
    }

    // 2) Najdi 2D kuželosečky příslušné directrix
    val conic2DP = state.conicsPudorys.firstOrNull { it.parent?.id == toDelete.directrixId || it.parentId == toDelete.directrixId }
    val conic2DN = state.conicsNarys  .firstOrNull { it.parent?.id == toDelete.directrixId || it.parentId == toDelete.directrixId }
    val conic2DA = state.conicsAxo    .firstOrNull { it.parent?.id == toDelete.directrixId || it.parentId == toDelete.directrixId }

    // 3) Najdi siluety robustně (podle conicalSurfaceId i podle edge* seznamů)
    val segPByFlag = state.segmentsPudorys.filter { it.isConicalSilhouette && it.conicalSurfaceId == coneId }
    val segNByFlag = state.segmentsNarys  .filter { it.isConicalSilhouette && it.conicalSurfaceId == coneId }
    val segBByFlag = state.segmentsBokorys.filter { it.isConicalSilhouette && it.conicalSurfaceId == coneId }
    val segAByFlag = state.segmentsAxo.filter { it.isConicalSilhouette && it.conicalSurfaceId == coneId }
    val segPByEdge = state.segmentsPudorys.filter { it.id in toDelete.edgeSegIdsPudorys2D }
    val segNByEdge = state.segmentsNarys  .filter { it.id in toDelete.edgeSegIdsNarys2D  }
    val segBByEdge = state.segmentsBokorys.filter { it.id in toDelete.edgeSegIdsBokorys2D }
    val segAByEdge = state.segmentsAxo.filter { it.id in toDelete.edgeSegIdsAxo2D }

    val segP = (segPByFlag + segPByEdge).distinctBy { it.id }
    val segN = (segNByFlag + segNByEdge).distinctBy { it.id }
    val segB = (segBByFlag + segBByEdge).distinctBy { it.id }
    val segA = (segAByFlag + segAByEdge).distinctBy { it.id }

    val segPIds = segP.map { it.id }.toSet()
    val segNIds = segN.map { it.id }.toSet()
    val segBIds = segB.map { it.id }.toSet()
    val segAIds = segA.map { it.id }.toSet()

    // 4) Body k odstranění = (edge* z kuželu) ∪ (konce nalezených siluet), ale jen pokud nejsou použité jinde
    val ptIdsP = (toDelete.edgePointIdsPudorys2D + segP.flatMap { listOf(it.start.id, it.end.id) }).toMutableSet()
    val ptIdsN = (toDelete.edgePointIdsNarys2D   + segN.flatMap { listOf(it.start.id, it.end.id) }).toMutableSet()
    val ptIdsB = (toDelete.edgePointIdsBokorys2D + segB.flatMap { listOf(it.start.id, it.end.id) }).toMutableSet()
    val ptIdsA = (toDelete.edgePointIdsAxo2D + segA.flatMap { listOf(it.start.id, it.end.id) }).toMutableSet()

    fun usedElsewhereP(ptId: String) = state.segmentsPudorys.any { it.id !in segPIds && (it.start.id == ptId || it.end.id == ptId) }
    fun usedElsewhereN(ptId: String) = state.segmentsNarys  .any { it.id !in segNIds && (it.start.id == ptId || it.end.id == ptId) }
    fun usedElsewhereB(ptId: String) = state.segmentsBokorys.any { it.id !in segBIds && (it.start.id == ptId || it.end.id == ptId) }
    fun usedElsewhereA(ptId: String) = state.segmentsAxo.any { it.id !in segAIds && (it.start.id == ptId || it.end.id == ptId) }

    val ptsPToRemove = ptIdsP
        .filterNot(::usedElsewhereP)
        .filter { id ->
            state.pointsPudorys.firstOrNull { it.id == id }?.let { it.parent?.id != toDelete.apexId } ?: true
        }
        .toSet()
    val ptsNToRemove = ptIdsN
        .filterNot(::usedElsewhereN)
        .filter { id ->
            state.pointsNarys.firstOrNull { it.id == id }?.let { it.parent?.id != toDelete.apexId } ?: true
        }
        .toSet()
    val ptsBToRemove = ptIdsB
        .filterNot(::usedElsewhereB)
        .filter { id ->
            state.pointsBokorys.firstOrNull { it.id == id }?.let { it.parent?.id != toDelete.apexId } ?: true
        }
        .toSet()
    val ptsAToRemove = ptIdsA
        .filterNot(::usedElsewhereA)
        .filter { id ->
            state.pointsAxo.firstOrNull { it.id == id }?.let { it.parent?.id != toDelete.apexId } ?: true
        }
        .toSet()


    state.selectedPointsPudorys.removeAll { it.id in ptsPToRemove }
    state.selectedPointsNarys  .removeAll { it.id in ptsNToRemove }
    state.selectedPointsBokorys.removeAll { it.id in ptsBToRemove }
    state.selectedPointsAxo.removeAll { it.id in ptsAToRemove }

    // 6) Smaž siluety a (bezpečné) body
    state.segmentsPudorys.removeAll { it.id in segPIds }
    state.segmentsNarys  .removeAll { it.id in segNIds }
    state.segmentsBokorys.removeAll { it.id in segBIds }
    state.segmentsAxo.removeAll { it.id in segAIds }
    state.pointsPudorys  .removeAll { it.id in ptsPToRemove }
    state.pointsNarys    .removeAll { it.id in ptsNToRemove }
    state.pointsBokorys.removeAll { it.id in ptsBToRemove }
    state.pointsAxo.removeAll { it.id in ptsAToRemove }

    // 7) Smaž 2D projekce apexu (pokud existují)
    toDelete.apexProjPudorysId?.let { id ->
        state.pointsPudorys.removeAll { it.id == id && it.parent?.id != toDelete.apexId }
        state.selectedPointsPudorys.removeAll { it.id == id && it.parent?.id != toDelete.apexId }
    }
    toDelete.apexProjNarysId?.let { id ->
        state.pointsNarys.removeAll { it.id == id && it.parent?.id != toDelete.apexId }
        state.selectedPointsNarys.removeAll { it.id == id && it.parent?.id != toDelete.apexId }
    }
    toDelete.apexProjBokorysId?.let { id ->
        state.pointsBokorys.removeAll { it.id == id && it.parent?.id != toDelete.apexId }
        state.selectedPointsBokorys.removeAll { it.id == id && it.parent?.id != toDelete.apexId }
    }
    toDelete.apexProjAxoId?.let { id ->
        state.pointsAxo.removeAll { it.id == id && it.parent?.id != toDelete.apexId }
        state.selectedPointsAxo.removeAll { it.id == id && it.parent?.id != toDelete.apexId }
    }

    // 8) Smaž evidované oblouky základny (ať nic nezůstane kreslit)
    conic2DP?.let { state.ellipseArcEnds.remove(it.id); state.ellipseArcMode.remove(it.id) }
    conic2DN?.let { state.ellipseArcEnds.remove(it.id); state.ellipseArcMode.remove(it.id) }
    conic2DA?.let { state.ellipseArcEnds.remove(it.id); state.ellipseArcMode.remove(it.id) }

    // 9) Zruš výběr a smaž samotný kužel
    state.selectedCone.removeAll { it.id == coneId }
    state.conicalSurfaces.removeAll { it.id == coneId }
    if (state.projectionPhase == "rename_cone") state.projectionPhase = ""
    commitSnapshot(state)
    state.triggerRedraw++
}
@Composable
fun coneEdit(state: MongeState){
    val selectedCone = state.selectedCone.firstOrNull()
    selectedCone?.let { cone ->
        key(cone.id) {
            EditableConeInfo(
                coneId = cone.id,
                state = state,
                onApply = { newName ->
                    val cur = state.conicalSurfaces.find { it.id == cone.id } ?: return@EditableConeInfo
                    val cleaned = newName.trim()
                    if (cur.name == cleaned) return@EditableConeInfo


                    val updated = cur.copy(name = cleaned)

                    state.conicalSurfaces.replaceAll { if (it.id == updated.id) updated else it }

                    state.selectedCone.replaceAll { if (it.id == updated.id) updated else it }
                    if (state.selectedConicalSurface?.id == updated.id) {
                        state.selectedConicalSurface = updated
                    }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },

                onColorChange = { newColor ->
                    val coneCurrent = state.conicalSurfaces.find { it.id == cone.id } ?: return@EditableConeInfo
                    applyConeColorNoCommit(state, coneCurrent.id, newColor)
                    commitSnapshot(state)
                },

                onStrokeWidthChange = { newWidth ->
                    val coneCurrent = state.conicalSurfaces.find { it.id == cone.id } ?: return@EditableConeInfo
                    coneCurrent.wireWidth = newWidth

                    val dirId = coneCurrent.directrixId
                    state.conics3D.find { it.id == dirId }?.strokeWidth = newWidth
                    state.conicsPudorys.replaceAll { c ->
                        if ((c.parent?.id ?: c.parentId) == dirId) c.copy(strokeWidth = newWidth) else c
                    }
                    state.conicsNarys.replaceAll { c ->
                        if ((c.parent?.id ?: c.parentId) == dirId) c.copy(strokeWidth = newWidth) else c
                    }
                    state.conicsBokorys.replaceAll { c ->
                        if ((c.parent?.id ?: c.parentId) == dirId) c.copy(strokeWidth = newWidth) else c
                    }
                    state.conicsAxo.replaceAll { c ->
                        if ((c.parent?.id ?: c.parentId) == dirId) c.copy(strokeWidth = newWidth) else c
                    }

                    state.segmentsPudorys.filter { it.conicalSurfaceId == coneCurrent.id || it.id in coneCurrent.edgeSegIdsPudorys2D }.forEach { it.localStrokeWidth = newWidth }
                    state.segmentsNarys.filter { it.conicalSurfaceId == coneCurrent.id || it.id in coneCurrent.edgeSegIdsNarys2D }.forEach { it.localStrokeWidth = newWidth }
                    state.segmentsBokorys.filter { it.conicalSurfaceId == coneCurrent.id || it.id in coneCurrent.edgeSegIdsBokorys2D }.forEach { it.localStrokeWidth = newWidth }
                    state.segmentsAxo.filter { it.conicalSurfaceId == coneCurrent.id || it.id in coneCurrent.edgeSegIdsAxo2D }.forEach { it.localStrokeWidth = newWidth }

                    state.triggerRedraw++
                },

                onDelete = {
                    deleteCone(state, cone)
                    clearSelection(state)
                },
                uiScale = SettingsManager.current.UIscale/75f
            )

        }
    }
}
