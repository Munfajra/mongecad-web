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
import model.classes.ConicSection3D
import model.classes.CylindricalSurface3D
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.mongeui.toolbar.SkikoButton

@Composable
fun EditableCylinderInfo(
    cylId: String,
    state: MongeState,
    onApplyName: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onDelete: () -> Unit,
    uiScale: Float
) {
    val cyl = state.cylindricalSurfaces.find { it.id == cylId } ?: return

    val ui = remember(uiScale) { UiScale(uiScale) }
    var pendingColor by remember(cyl.id, cyl.color) {
        mutableStateOf(cyl.color)
    }
    var sliderValue by remember(cyl.id, cyl.wireWidth) {
        mutableStateOf(cyl.wireWidth)
    }

    val nameNow = cyl.name.trim()

    var pendingName by remember(cyl.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(cyl.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(cyl.id, cyl.name) {
        val n = cyl.name.trim()
        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }

    val canApply = pendingName.text.trim() != lastAppliedName
    val visibility = remember(
        cyl.id,
        state.triggerRedraw,
        state.conicsPudorys.size,
        state.conicsNarys.size,
        state.conicsBokorys.size,
        state.conicsAxo.size,
        state.segmentsPudorys.size,
        state.segmentsNarys.size,
        state.segmentsBokorys.size,
        state.segmentsAxo.size
    ) {
        cylinderProjectionVisibility(state, cyl)
    }

    fun applyName() {
        if (!canApply) return

        val newName = pendingName.text.trim()
        onApplyName(newName)

        lastAppliedName = newName
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        SimpleNameEditor(
            label = "Válec:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { applyName() },
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


        MongeDivider()

        if (state.projectionMode == ProjectionMode.AXO) {
            MongeInspectorPropertyRow(
                label = "Průměty:",
                contentAlign = Alignment.End
            ) {
                ProjectionVisibilityToggleStrip(
                    ui = ui,
                    ProjectionVisibilityToggleItem("A", visibility.axo) { checked ->
                        setCylinderProjectionVisible(state, cyl, CylinderProjectionKind.AXO, checked)
                    },
                    ProjectionVisibilityToggleItem("P", visibility.pudorys) { checked ->
                        setCylinderProjectionVisible(state, cyl, CylinderProjectionKind.PUDORYS, checked)
                    },
                    ProjectionVisibilityToggleItem("N", visibility.narys) { checked ->
                        setCylinderProjectionVisible(state, cyl, CylinderProjectionKind.NARYS, checked)
                    },
                    ProjectionVisibilityToggleItem("B", visibility.bokorys) { checked ->
                        setCylinderProjectionVisible(state, cyl, CylinderProjectionKind.BOKORYS, checked)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            MongeDivider()
        }
        MongeDivider()

        ProjectionVisibilityToggleRow(
            label = "Vybarvit:",
            checked = cyl.fillFaces,
            onCheckedChange = { checked ->
                val idx = state.cylindricalSurfaces.indexOfFirst { it.id == cyl.id }
                if (idx >= 0) {
                    val updated = state.cylindricalSurfaces[idx].copy(fillFaces = checked)
                    state.cylindricalSurfaces[idx] = updated
                    state.selectedCylinder.replaceAll { if (it.id == updated.id) updated else it }
                }
                state.triggerRedraw++
                commitSnapshot(state)
            },
            ui = ui
        )


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

private enum class CylinderProjectionKind { AXO, PUDORYS, NARYS, BOKORYS }

private data class CylinderProjectionVisibility(
    val axo: Boolean,
    val pudorys: Boolean,
    val narys: Boolean,
    val bokorys: Boolean
)

private fun cylinderConicIds(cyl: CylindricalSurface3D): Set<String> = buildSet {
    add(cyl.lowerConicId ?: cyl.directrixId)
    add(cyl.directrixId)
    cyl.upperConicId?.let(::add)
}

private fun cylinderProjectionVisibility(
    state: MongeState,
    cyl: CylindricalSurface3D
): CylinderProjectionVisibility {
    val conicIds = cylinderConicIds(cyl)

    fun visibleOrFalse(values: List<Boolean>): Boolean =
        values.isNotEmpty() && values.all { it }

    val axoValues =
        state.conicsAxo.filter { (it.parent?.id ?: it.parentId) in conicIds }.map { it.showInAxo } +
                state.segmentsAxo.filter { it.id in cyl.edgeSegIdsAxo2D }.map { it.showInAxo } +
                state.pointsAxo.filter { it.id in cyl.edgePointIdsAxo2D }.map { it.showInAxo }

    val pudorysValues =
        state.conicsPudorys.filter { (it.parent?.id ?: it.parentId) in conicIds }.map { it.showInAxo } +
                state.segmentsPudorys.filter { it.id in cyl.edgeSegIdsPudorys2D }.map { it.showInAxo } +
                state.pointsPudorys.filter { it.id in cyl.edgePointIdsPudorys2D }.map { it.showInAxo }

    val narysValues =
        state.conicsNarys.filter { (it.parent?.id ?: it.parentId) in conicIds }.map { it.showInAxo } +
                state.segmentsNarys.filter { it.id in cyl.edgeSegIdsNarys2D }.map { it.showInAxo } +
                state.pointsNarys.filter { it.id in cyl.edgePointIdsNarys2D }.map { it.showInAxo }

    val bokorysValues =
        state.conicsBokorys.filter { (it.parent?.id ?: it.parentId) in conicIds }.map { it.showInAxo } +
                state.segmentsBokorys.filter { it.id in cyl.edgeSegIdsBokorys2D }.map { it.showInAxo } +
                state.pointsBokorys.filter { it.id in cyl.edgePointIdsBokorys2D }.map { it.showInAxo }

    return CylinderProjectionVisibility(
        axo = visibleOrFalse(axoValues),
        pudorys = visibleOrFalse(pudorysValues),
        narys = visibleOrFalse(narysValues),
        bokorys = visibleOrFalse(bokorysValues)
    )
}

private fun setCylinderProjectionVisible(
    state: MongeState,
    cyl: CylindricalSurface3D,
    kind: CylinderProjectionKind,
    checked: Boolean
) {
    val conicIds = cylinderConicIds(cyl)

    when (kind) {
        CylinderProjectionKind.AXO -> {
            state.conicsAxo.filter { (it.parent?.id ?: it.parentId) in conicIds }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
            state.segmentsAxo.filter { it.id in cyl.edgeSegIdsAxo2D }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
            state.pointsAxo.filter { it.id in cyl.edgePointIdsAxo2D }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
        }

        CylinderProjectionKind.PUDORYS -> {
            state.conicsPudorys.filter { (it.parent?.id ?: it.parentId) in conicIds }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
            state.segmentsPudorys.filter { it.id in cyl.edgeSegIdsPudorys2D }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
            state.pointsPudorys.filter { it.id in cyl.edgePointIdsPudorys2D }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
        }

        CylinderProjectionKind.NARYS -> {
            state.conicsNarys.filter { (it.parent?.id ?: it.parentId) in conicIds }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
            state.segmentsNarys.filter { it.id in cyl.edgeSegIdsNarys2D }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
            state.pointsNarys.filter { it.id in cyl.edgePointIdsNarys2D }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
        }

        CylinderProjectionKind.BOKORYS -> {
            state.conicsBokorys.filter { (it.parent?.id ?: it.parentId) in conicIds }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
            state.segmentsBokorys.filter { it.id in cyl.edgeSegIdsBokorys2D }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
            state.pointsBokorys.filter { it.id in cyl.edgePointIdsBokorys2D }.forEach {
                it.showInAxoInitial = checked
                it.showInAxo = checked
            }
        }
    }

    commitSnapshot(state)
    state.triggerRedraw++
}

fun deleteCylinder(
    state: MongeState,
    cyl: CylindricalSurface3D,
    removeUpperConicCompletely: Boolean = true,   // smaž horní 3D elipsu + 2D projekce
    clearBaseArcs: Boolean = true                 // zruš výřezy na base projekcích
) {

    val cylId = cyl.id
    val surface = state.cylindricalSurfaces.find { it.id == cylId } ?: return


    // --- 1) Odpoj surface z 3D coniců (base/lower/upper)
    val affected3DIds = buildSet {
        surface.lowerConicId?.let(::add)
        surface.upperConicId?.let(::add)
        surface.directrixId.let(::add) // kompatibilita se starší strukturou
    }
    state.conics3D.replaceAll { c3d ->
        if (c3d.id in affected3DIds) {
            c3d.also { it.directrixOfSurfaceIds = it.directrixOfSurfaceIds - cylId }
        } else c3d
    }

    // --- 2) Najdi 2D projekce base a upper
    val baseId  = surface.lowerConicId ?: surface.directrixId
    val upperId = surface.upperConicId

    val baseP = state.conicsPudorys.filter { (it.parent?.id ?: it.parentId) == baseId }
    val baseN = state.conicsNarys  .filter { (it.parent?.id ?: it.parentId) == baseId }
    val baseB = state.conicsBokorys.filter { (it.parent?.id ?: it.parentId) == baseId }
    val baseA = state.conicsAxo    .filter { (it.parent?.id ?: it.parentId) == baseId }
    val upperP = if (upperId != null) state.conicsPudorys.filter { (it.parent?.id ?: it.parentId) == upperId } else emptyList()
    val upperN = if (upperId != null) state.conicsNarys  .filter { (it.parent?.id ?: it.parentId) == upperId } else emptyList()
    val upperB = if (upperId != null) state.conicsBokorys.filter { (it.parent?.id ?: it.parentId) == upperId } else emptyList()
    val upperA = if (upperId != null) state.conicsAxo    .filter { (it.parent?.id ?: it.parentId) == upperId } else emptyList()

    // --- 3) Smaž obrysové segmenty a jejich endpointy (podle edge seznamů surface)
    run {
        val segPIds = surface.edgeSegIdsPudorys2D.toSet()
        val segNIds = surface.edgeSegIdsNarys2D.toSet()
        val segBIds = surface.edgeSegIdsBokorys2D.toSet()
        val segAIds = surface.edgeSegIdsAxo2D.toSet()

        val ptPToCheck = surface.edgePointIdsPudorys2D.toSet()
        val ptNToCheck = surface.edgePointIdsNarys2D.toSet()
        val ptBToCheck = surface.edgePointIdsBokorys2D.toSet()
        val ptAToCheck = surface.edgePointIdsAxo2D.toSet()

        state.segmentsPudorys.removeAll { it.id in segPIds }
        state.segmentsNarys  .removeAll { it.id in segNIds }
        state.segmentsBokorys.removeAll { it.id in segBIds }
        state.segmentsAxo    .removeAll { it.id in segAIds }

        fun usedElsewhereP(ptId: String) = state.segmentsPudorys.any { it.start.id == ptId || it.end.id == ptId }
        fun usedElsewhereN(ptId: String) = state.segmentsNarys  .any { it.start.id == ptId || it.end.id == ptId }
        fun usedElsewhereB(ptId: String) = state.segmentsBokorys.any { it.start.id == ptId || it.end.id == ptId }
        fun usedElsewhereA(ptId: String) = state.segmentsAxo    .any { it.start.id == ptId || it.end.id == ptId }

        state.pointsPudorys.removeAll { it.id in ptPToCheck && !usedElsewhereP(it.id) }
        state.pointsNarys  .removeAll { it.id in ptNToCheck && !usedElsewhereN(it.id) }
        state.pointsBokorys.removeAll { it.id in ptBToCheck && !usedElsewhereB(it.id) }
        state.pointsAxo    .removeAll { it.id in ptAToCheck && !usedElsewhereA(it.id) }
    }

    // --- 4) Zruš arc výřezy, které válcová plocha nastavila
    if (clearBaseArcs) {
        (baseP + baseN + baseB + baseA).forEach {
            state.ellipseArcEnds.remove(it.id); state.ellipseArcMode.remove(it.id)
        }
    }
    (upperP + upperN + upperB + upperA).forEach {
        state.ellipseArcEnds.remove(it.id); state.ellipseArcMode.remove(it.id)
    }

    // --- 5) Volitelně kompletně odstraníme horní 3D elipsu + její 2D projekce
    if (removeUpperConicCompletely && upperId != null) {
        state.conicsPudorys.removeAll { (it.parent?.id ?: it.parentId) == upperId }
        state.conicsNarys  .removeAll { (it.parent?.id ?: it.parentId) == upperId }
        state.conicsBokorys.removeAll { (it.parent?.id ?: it.parentId) == upperId }
        state.conicsAxo    .removeAll { (it.parent?.id ?: it.parentId) == upperId }
        upperP.forEach { state.conicInputPointsPudorys.remove(it.id) }
        upperN.forEach { state.conicInputPointsNarys.remove(it.id) }
        upperB.forEach { state.conicInputPointsBokorys.remove(it.id) }
        upperA.forEach { state.conicInputPointsAxo.remove(it.id) }

        val upper3D = state.conics3D.find { it.id == upperId }
        if (upper3D != null && upper3D.directrixOfSurfaceIds.isEmpty()) {
            state.conics3D.remove(upper3D)
        }
    }

    // --- 6) Úklid selekcí
    fun isSurfaceConic(pid: String?) = pid == baseId || pid == upperId
    state.selectedConicsPudorys.removeAll { isSurfaceConic(it.parent?.id ?: it.parentId) }
    state.selectedConicsNarys.removeAll { isSurfaceConic(it.parent?.id ?: it.parentId) }
    state.selectedConicsBokorys.removeAll { isSurfaceConic(it.parent?.id ?: it.parentId) }
    state.selectedConicsAxo.removeAll { isSurfaceConic(it.parent?.id ?: it.parentId) }

    state.selectedSegmentsPudorys.removeAll { it.id in surface.edgeSegIdsPudorys2D }
    state.selectedSegmentsNarys.removeAll { it.id in surface.edgeSegIdsNarys2D }
    state.selectedSegmentsBokorys.removeAll { it.id in surface.edgeSegIdsBokorys2D }
    state.selectedSegmentsAxo.removeAll { it.id in surface.edgeSegIdsAxo2D }

    state.selectedPointsPudorys.removeAll { it.id in surface.edgePointIdsPudorys2D }
    state.selectedPointsNarys  .removeAll { it.id in surface.edgePointIdsNarys2D }
    state.selectedPointsBokorys.removeAll { it.id in surface.edgePointIdsBokorys2D }
    state.selectedPointsAxo.removeAll { it.id in surface.edgePointIdsAxo2D }
    state.selectedCylinder.removeAll { it.id == cylId }

    // --- 7) Odstranění samotného surface
    state.cylindricalSurfaces.removeAll { it.id == cylId }
    if (state.projectionPhase == "rename_cyl") state.projectionPhase = ""
    commitSnapshot(state)
    state.triggerRedraw++
}
@Composable
fun cylinderEdit(state:MongeState){
    val selectedCylinder = state.selectedCylinder.firstOrNull()
    selectedCylinder?.let { cyl ->
        key(cyl.id) {
            EditableCylinderInfo(
                cylId = cyl.id,
                state = state,


                onColorChange = { newColor ->

                    // 1) přebarvi surface
                    val cylCurrent = state.cylindricalSurfaces.find { it.id == cyl.id } ?: return@EditableCylinderInfo
                    cylCurrent.color = newColor

                    // 2) všechny 3D kuželosečky, které k válci patří
                    val conicIds = buildSet {
                        cylCurrent.lowerConicId?.let(::add)
                        cylCurrent.upperConicId?.let(::add)
                        // fallback pro starší strukturu:
                        cylCurrent.directrixId.let(::add)
                    }

                    // 3) vytvoř "updated" 3D conics se stejným id, novou barvou a přenesenými referencemi
                    val updated3DById = mutableMapOf<String, ConicSection3D>()
                    for (cid in conicIds) {
                        val old3D = state.conics3D.find { it.id == cid } ?: continue
                        val upd = old3D.copy(color = newColor).apply {
                            directrixOfSurfaceIds = old3D.directrixOfSurfaceIds   // zachovat členství
                        }
                        // nahradit v seznamu
                        state.conics3D.replaceAll { if (it.id == cid) upd else it }
                        updated3DById[cid] = upd
                    }

                    // 4) přepoj 2D projekce těchto 3D conics na nové instance
                    state.conicsPudorys.replaceAll { p ->
                        val upd = updated3DById[p.parent?.id ?: p.parentId]
                        if (upd != null) p.copy(parent = upd, parentId = upd.id) else p
                    }
                    state.conicsNarys.replaceAll { p ->
                        val upd = updated3DById[p.parent?.id ?: p.parentId]
                        if (upd != null) p.copy(parent = upd, parentId = upd.id) else p
                    }
                    state.conicsBokorys.replaceAll { p ->
                        val upd = updated3DById[p.parent?.id ?: p.parentId]
                        if (upd != null) p.copy(parent = upd, parentId = upd.id) else p
                    }
                    state.conicsAxo.replaceAll { p ->
                        val upd = updated3DById[p.parent?.id ?: p.parentId]
                        if (upd != null) p.copy(parent = upd, parentId = upd.id) else p
                    }

                    // 5) obrysové úsečky válce
                    state.segmentsPudorys.replaceAll { s ->
                        if (s.id in cylCurrent.edgeSegIdsPudorys2D) s.copy(localColor = newColor) else s
                    }
                    state.segmentsNarys.replaceAll { s ->
                        if (s.id in cylCurrent.edgeSegIdsNarys2D) s.copy(localColor = newColor) else s
                    }
                    state.segmentsBokorys.replaceAll { s ->
                        if (s.id in cylCurrent.edgeSegIdsBokorys2D) s.copy(localColor = newColor) else s
                    }
                    state.segmentsAxo.replaceAll { s ->
                        if (s.id in cylCurrent.edgeSegIdsAxo2D) s.copy(localColor = newColor) else s
                    }

                    state.selectedConicsPudorys.clear()
                    state.selectedConicsNarys.clear()
                    state.selectedConicsBokorys.clear()
                    state.selectedConicsAxo.clear()
                    state.triggerRedraw++
                    commitSnapshot(state)

                },
                onStrokeWidthChange = { newWidth ->
                    val cylCurrent = state.cylindricalSurfaces.find { it.id == cyl.id } ?: return@EditableCylinderInfo
                    cylCurrent.wireWidth = newWidth

                    val conicIds = cylinderConicIds(cylCurrent)
                    state.conics3D.filter { it.id in conicIds }.forEach { it.strokeWidth = newWidth }
                    state.conicsPudorys.replaceAll { c ->
                        if ((c.parent?.id ?: c.parentId) in conicIds) c.copy(strokeWidth = newWidth) else c
                    }
                    state.conicsNarys.replaceAll { c ->
                        if ((c.parent?.id ?: c.parentId) in conicIds) c.copy(strokeWidth = newWidth) else c
                    }
                    state.conicsBokorys.replaceAll { c ->
                        if ((c.parent?.id ?: c.parentId) in conicIds) c.copy(strokeWidth = newWidth) else c
                    }
                    state.conicsAxo.replaceAll { c ->
                        if ((c.parent?.id ?: c.parentId) in conicIds) c.copy(strokeWidth = newWidth) else c
                    }

                    state.segmentsPudorys.filter { it.id in cylCurrent.edgeSegIdsPudorys2D }.forEach { it.localStrokeWidth = newWidth }
                    state.segmentsNarys.filter { it.id in cylCurrent.edgeSegIdsNarys2D }.forEach { it.localStrokeWidth = newWidth }
                    state.segmentsBokorys.filter { it.id in cylCurrent.edgeSegIdsBokorys2D }.forEach { it.localStrokeWidth = newWidth }
                    state.segmentsAxo.filter { it.id in cylCurrent.edgeSegIdsAxo2D }.forEach { it.localStrokeWidth = newWidth }

                    state.triggerRedraw++
                },
                onDelete = {deleteCylinder(state,cyl)
                    clearSelection(state)},
                onApplyName = { newName ->
                    val cur = state.cylindricalSurfaces.find { it.id == cyl.id } ?: return@EditableCylinderInfo
                    val cleaned = newName.trim()
                    if (cur.name == cleaned) return@EditableCylinderInfo


                    val updated = cur.copy(name = cleaned)
                    state.cylindricalSurfaces.replaceAll { if (it.id == updated.id) updated else it }
                    commitSnapshot(state)

                    // pokud držíš selectedCylinder / selectedCylindricalSurface, přepiš i tam
                    state.triggerRedraw++
                },
                uiScale = SettingsManager.current.UIscale/75f

            )
        }
    }
}
