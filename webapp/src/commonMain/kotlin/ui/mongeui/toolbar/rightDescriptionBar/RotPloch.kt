package ui.mongeui.toolbar.rightDescriptionBar
import utils.replaceAll

import model.SOR_BOKORYS_MERIDIAN_ID_PREFIX
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
import model.SolidOfRevolutionNarys
import model.SolidOfRevolutionPudorys
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.mongeui.toolbar.SkikoButton

private const val SOR_AXO_CONTOUR_ID_PREFIX = "sorAxoContour"

private fun isSoRAxoContourFor(curveParentId: String?, objectId: String, sorId: String): Boolean =
    curveParentId == sorId && objectId.startsWith(SOR_AXO_CONTOUR_ID_PREFIX)

private fun isSoRBokorysMeridianFor(objectId: String, sorId: String): Boolean =
    objectId.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && objectId.contains(sorId)

private fun deleteSoRAxoContours(state: MongeState, sorId: String) {
    val contourCurves = state.curvesAxo.filter { isSoRAxoContourFor(it.parentId, it.id, sorId) }
    val axoPointIds = contourCurves.flatMap { it.pointIds }.toSet()
    val generatedAxoPoints = state.pointsAxo.filter {
        it.id in axoPointIds || it.id.startsWith(SOR_AXO_CONTOUR_ID_PREFIX) && it.id.contains(sorId)
    }
    val point3DIds = generatedAxoPoints
        .mapNotNull { it.parent?.id }
        .toSet()

    state.curvesAxo.removeAll { isSoRAxoContourFor(it.parentId, it.id, sorId) }
    state.pointsAxo.removeAll { it in generatedAxoPoints || it.parent?.id in point3DIds }
    state.selectedPointsAxo.removeAll {
        it.id in axoPointIds || it.id.startsWith(SOR_AXO_CONTOUR_ID_PREFIX) && it.id.contains(sorId) || it.parent?.id in point3DIds
    }
    state.sharedPoints3D.removeAll { it.id in point3DIds || it.id.startsWith(SOR_AXO_CONTOUR_ID_PREFIX) && it.id.contains(sorId) }
}

private fun deleteSoRBokorysMeridians(state: MongeState, sorId: String) {
    state.curvesBokorys.removeAll {
        it.parentId == sorId && it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX)
    }
    state.arcsBokorys.removeAll { isSoRBokorysMeridianFor(it.id, sorId) }
    state.segmentsBokorys.removeAll { isSoRBokorysMeridianFor(it.id, sorId) }
    state.pointsBokorys.removeAll { isSoRBokorysMeridianFor(it.id, sorId) }
    state.selectedArcsBokorys.removeAll { isSoRBokorysMeridianFor(it.id, sorId) }
    state.selectedSegmentsBokorys.removeAll { isSoRBokorysMeridianFor(it.id, sorId) }
    state.selectedPointsBokorys.removeAll { isSoRBokorysMeridianFor(it.id, sorId) }
}

private fun updateSoRAxoContourColor(state: MongeState, sorId: String, color: Color) {
    state.curvesAxo.replaceAll { curve ->
        if (!isSoRAxoContourFor(curve.parentId, curve.id, sorId)) return@replaceAll curve
        curve.copy(color = color).also { it.showInAxo = curve.showInAxo }
    }
}

private fun updateSoRAxoContourStrokeWidth(state: MongeState, sorId: String, strokeWidth: Float) {
    state.curvesAxo.replaceAll { curve ->
        if (!isSoRAxoContourFor(curve.parentId, curve.id, sorId)) return@replaceAll curve
        curve.copy(strokeWidth = strokeWidth).also { it.showInAxo = curve.showInAxo }
    }
}

private fun updateSoRBokorysMeridianColor(state: MongeState, sorId: String, color: Color) {
    state.curvesBokorys.replaceAll { curve ->
        if (curve.parentId != sorId || !curve.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX)) return@replaceAll curve
        curve.copy(color = color).also { it.showInAxo = curve.showInAxo }
    }
    state.arcsBokorys.forEach { if (isSoRBokorysMeridianFor(it.id, sorId)) it.color = color }
    state.segmentsBokorys.forEach { if (isSoRBokorysMeridianFor(it.id, sorId)) it.localColor = color }
}

private fun updateSoRBokorysMeridianStrokeWidth(state: MongeState, sorId: String, strokeWidth: Float) {
    state.curvesBokorys.replaceAll { curve ->
        if (curve.parentId != sorId || !curve.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX)) return@replaceAll curve
        curve.copy(strokeWidth = strokeWidth).also { it.showInAxo = curve.showInAxo }
    }
    state.arcsBokorys.forEach { if (isSoRBokorysMeridianFor(it.id, sorId)) it.strokeWidth = strokeWidth }
    state.segmentsBokorys.forEach { if (isSoRBokorysMeridianFor(it.id, sorId)) it.localStrokeWidth = strokeWidth }
}

private enum class SoRProjectionKind { AXO, PUDORYS, NARYS, BOKORYS }

private data class SoRProjectionVisibility(
    val axo: Boolean,
    val pudorys: Boolean,
    val narys: Boolean,
    val bokorys: Boolean
)

private fun visibleOrFalse(values: List<Boolean>): Boolean =
    values.isNotEmpty() && values.all { it }

private fun conicParentIdOf(parentId: String?, parent: model.classes.ConicSection3D?): String? =
    parent?.id ?: parentId

private fun deleteSoRParallelConicsByIds(
    state: MongeState,
    circlePIds: Set<String>,
    circleNIds: Set<String>
) {
    val parentIds = HashSet<String>()

    state.conicsPudorys
        .asSequence()
        .filter { it.id in circlePIds }
        .forEach { conicParentIdOf(it.parentId, it.parent)?.let(parentIds::add) }
    state.conicsNarys
        .asSequence()
        .filter { it.id in circleNIds }
        .forEach { conicParentIdOf(it.parentId, it.parent)?.let(parentIds::add) }

    val pudorysIds = state.conicsPudorys
        .filter { it.id in circlePIds || conicParentIdOf(it.parentId, it.parent) in parentIds }
        .map { it.id }
        .toSet()
    val narysIds = state.conicsNarys
        .filter { it.id in circleNIds || conicParentIdOf(it.parentId, it.parent) in parentIds }
        .map { it.id }
        .toSet()
    val bokorysIds = state.conicsBokorys
        .filter { conicParentIdOf(it.parentId, it.parent) in parentIds }
        .map { it.id }
        .toSet()
    val axoIds = state.conicsAxo
        .filter { conicParentIdOf(it.parentId, it.parent) in parentIds }
        .map { it.id }
        .toSet()

    pudorysIds.forEach {
        state.conicInputPointsPudorys.remove(it)
        state.hyperbolaInputsPudorys.remove(it)
        state.ellipseArcEnds.remove(it)
        state.ellipseArcMode.remove(it)
    }
    narysIds.forEach {
        state.conicInputPointsNarys.remove(it)
        state.hyperbolaInputsNarys.remove(it)
        state.ellipseArcEnds.remove(it)
        state.ellipseArcMode.remove(it)
    }
    bokorysIds.forEach {
        state.conicInputPointsBokorys.remove(it)
        state.hyperbolaInputsBokorys.remove(it)
        state.ellipseArcEnds.remove(it)
        state.ellipseArcMode.remove(it)
    }
    axoIds.forEach {
        state.conicInputPointsAxo.remove(it)
        state.hyperbolaInputsAxo.remove(it)
        state.ellipseArcEnds.remove(it)
        state.ellipseArcMode.remove(it)
    }

    state.selectedConicsPudorys.removeAll { it.id in pudorysIds }
    state.selectedConicsNarys.removeAll { it.id in narysIds }
    state.selectedConicsBokorys.removeAll { it.id in bokorysIds }
    state.selectedConicsAxo.removeAll { it.id in axoIds }

    state.conicsPudorys.removeAll { it.id in pudorysIds }
    state.conicsNarys.removeAll { it.id in narysIds }
    state.conicsBokorys.removeAll { it.id in bokorysIds }
    state.conicsAxo.removeAll { it.id in axoIds }

    if (parentIds.isNotEmpty()) {
        state.conics3D.removeAll { it.id in parentIds }
    }
}

private fun deleteProjectedLinePointsByParentId(state: MongeState, lineId: String) {
    state.selectedPointsPudorys.removeAll { projectedLineIdOf(it) == lineId }
    state.selectedPointsNarys.removeAll { projectedLineIdOf(it) == lineId }
    state.selectedPointsBokorys.removeAll { projectedLineIdOf(it) == lineId }
    state.selectedPointsAxo.removeAll { projectedLineIdOf(it) == lineId }

    state.pointsPudorys.removeAll { projectedLineIdOf(it) == lineId }
    state.pointsNarys.removeAll { projectedLineIdOf(it) == lineId }
    state.pointsBokorys.removeAll { projectedLineIdOf(it) == lineId }
    state.pointsAxo.removeAll { projectedLineIdOf(it) == lineId }
}

private fun SoRAffectedIds.parallelProjectionParentIds(state: MongeState): Set<String> =
    parallelConicParentIds3D.ifEmpty {
        buildSet {
            state.conicsPudorys.filter { it.id in parallelConicProjIdsPudorys }
                .forEach { conicParentIdOf(it.parentId, it.parent)?.let(::add) }
            state.conicsNarys.filter { it.id in parallelConicProjIdsNarys }
                .forEach { conicParentIdOf(it.parentId, it.parent)?.let(::add) }
        }
    }

private fun sorProjectionVisibility(state: MongeState, ids: SoRAffectedIds, sorId: String): SoRProjectionVisibility {
    val parentIds = ids.parallelProjectionParentIds(state)
    val axoValues =
        state.curvesAxo.filter { isSoRAxoContourFor(it.parentId, it.id, sorId) }.map { it.showInAxo } +
            state.conicsAxo.filter { conicParentIdOf(it.parentId, it.parent) in parentIds }.map { it.showInAxo }

    val pudorysValues =
        state.segmentsPudorys.filter { it.id in ids.meridianIdsPudorys }.map { it.showInAxo } +
            state.arcsPudorys.filter { it.id in ids.meridianIdsPudorys }.map { it.showInAxo } +
            state.conicsPudorys.filter {
                it.id in ids.meridianIdsPudorys || conicParentIdOf(it.parentId, it.parent) in parentIds
            }.map { it.showInAxo } +
            state.curvesPudorys.filter { it.id in ids.meridianIdsPudorys }.map { it.showInAxo } +
            state.circlesPudorys.filter { it.id in ids.meridianIdsPudorys }.map { it.showInAxo }

    val narysValues =
        state.segmentsNarys.filter { it.id in ids.meridianIdsNarys }.map { it.showInAxo } +
            state.arcsNarys.filter { it.id in ids.meridianIdsNarys }.map { it.showInAxo } +
            state.conicsNarys.filter {
                it.id in ids.meridianIdsNarys || conicParentIdOf(it.parentId, it.parent) in parentIds
            }.map { it.showInAxo } +
            state.curvesNarys.filter { it.id in ids.meridianIdsNarys }.map { it.showInAxo } +
            state.circlesNarys.filter { it.id in ids.meridianIdsNarys }.map { it.showInAxo }

    val bokorysValues =
        state.curvesBokorys.filter {
            it.parentId == sorId && it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX)
        }.map { it.showInAxo } +
            state.arcsBokorys.filter { isSoRBokorysMeridianFor(it.id, sorId) }.map { it.showInAxo } +
            state.segmentsBokorys.filter { isSoRBokorysMeridianFor(it.id, sorId) }.map { it.showInAxo } +
            state.conicsBokorys.filter { conicParentIdOf(it.parentId, it.parent) in parentIds }.map { it.showInAxo }

    return SoRProjectionVisibility(
        axo = visibleOrFalse(axoValues),
        pudorys = visibleOrFalse(pudorysValues),
        narys = visibleOrFalse(narysValues),
        bokorys = visibleOrFalse(bokorysValues)
    )
}

private fun setSoRProjectionVisible(
    state: MongeState,
    ids: SoRAffectedIds,
    sorId: String,
    kind: SoRProjectionKind,
    checked: Boolean
) {
    val parentIds = ids.parallelProjectionParentIds(state)
    fun setConic(conic: model.classes.ConicSection2D) {
        when (conic) {
            is model.classes.ConicSectionPudorys -> conic.showInAxoInitial = checked
            is model.classes.ConicSectionNarys -> conic.showInAxoInitial = checked
            is model.classes.ConicSectionBokorys -> conic.showInAxoInitial = checked
            is model.classes.ConicSectionAxo -> conic.showInAxoInitial = checked
        }
        conic.showInAxo = checked
    }

    when (kind) {
        SoRProjectionKind.AXO -> {
            state.curvesAxo.filter { isSoRAxoContourFor(it.parentId, it.id, sorId) }.forEach {
                it.showInAxo = checked
            }
            state.conicsAxo.filter { conicParentIdOf(it.parentId, it.parent) in parentIds }.forEach(::setConic)
        }

        SoRProjectionKind.PUDORYS -> {
            val mIds = ids.meridianIdsPudorys
            state.segmentsPudorys.filter { it.id in mIds }.forEach { it.showInAxoInitial = checked; it.showInAxo = checked }
            state.arcsPudorys.filter { it.id in mIds }.forEach { it.showInAxoInitial = checked; it.showInAxo = checked }
            state.conicsPudorys.filter { it.id in mIds || conicParentIdOf(it.parentId, it.parent) in parentIds }.forEach(::setConic)
            state.curvesPudorys.filter { it.id in mIds }.forEach { it.showInAxo = checked }
            state.circlesPudorys.filter { it.id in mIds }.forEach { it.showInAxoInitial = checked; it.showInAxo = checked }
        }

        SoRProjectionKind.NARYS -> {
            val mIds = ids.meridianIdsNarys
            state.segmentsNarys.filter { it.id in mIds }.forEach { it.showInAxoInitial = checked; it.showInAxo = checked }
            state.arcsNarys.filter { it.id in mIds }.forEach { it.showInAxoInitial = checked; it.showInAxo = checked }
            state.conicsNarys.filter { it.id in mIds || conicParentIdOf(it.parentId, it.parent) in parentIds }.forEach(::setConic)
            state.curvesNarys.filter { it.id in mIds }.forEach { it.showInAxo = checked }
            state.circlesNarys.filter { it.id in mIds }.forEach { it.showInAxoInitial = checked; it.showInAxo = checked }
        }

        SoRProjectionKind.BOKORYS -> {
            state.curvesBokorys.filter {
                it.parentId == sorId && it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX)
            }.forEach { it.showInAxo = checked }
            state.arcsBokorys.filter { isSoRBokorysMeridianFor(it.id, sorId) }
                .forEach { it.showInAxoInitial = checked; it.showInAxo = checked }
            state.segmentsBokorys.filter { isSoRBokorysMeridianFor(it.id, sorId) }
                .forEach { it.showInAxoInitial = checked; it.showInAxo = checked }
            state.conicsBokorys.filter { conicParentIdOf(it.parentId, it.parent) in parentIds }.forEach(::setConic)
        }
    }

    commitSnapshot(state)
    state.triggerRedraw++
}

@Composable
private fun EditableSoRinfoContent(
    name: String,
    color: Color,
    strokeWidth: Float,
    state: MongeState,
    uiScale: Float,
    projectionVisibility: SoRProjectionVisibility?,
    onProjectionVisibilityChange: (SoRProjectionKind, Boolean) -> Unit,
    fillFaces: Boolean,
    onFillFacesChange: (Boolean) -> Unit,
    onApply: (String) -> Unit,
    onWidthChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    onDelete: () -> Unit
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    var pendingColor by remember(name, color) {
        mutableStateOf(color)
    }

    var sliderValue by remember(name, strokeWidth) {
        mutableStateOf(strokeWidth)
    }

    val nameNow = name.trim()

    var pendingName by remember(nameNow) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(nameNow) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(nameNow) {
        lastAppliedName = nameNow
        pendingName = TextFieldValue(nameNow)
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
            label = "Rot. plocha:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { apply() },
            state = state
        )

        MongeDivider()


            MongeInspectorPropertyRow(
                label = "Barva:",
                contentAlign = Alignment.End
            ) {
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

            MongeInspectorPropertyRow(
                label = "Šířka:",
                contentAlign = Alignment.End
            ) {
                WidthEditor(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onWidthChange(it)
                    },
                    state = state
                )
            }



        MongeDivider()

        if (state.projectionMode == ProjectionMode.AXO && projectionVisibility != null) {
            MongeInspectorPropertyRow(
                label = "Průměty:",
                contentAlign = Alignment.End
            ) {
                ProjectionVisibilityToggleStrip(
                    ui = ui,
                    ProjectionVisibilityToggleItem("A", projectionVisibility.axo) { checked ->
                        onProjectionVisibilityChange(SoRProjectionKind.AXO, checked)
                    },
                    ProjectionVisibilityToggleItem("P", projectionVisibility.pudorys) { checked ->
                        onProjectionVisibilityChange(SoRProjectionKind.PUDORYS, checked)
                    },
                    ProjectionVisibilityToggleItem("N", projectionVisibility.narys) { checked ->
                        onProjectionVisibilityChange(SoRProjectionKind.NARYS, checked)
                    },
                    ProjectionVisibilityToggleItem("B", projectionVisibility.bokorys) { checked ->
                        onProjectionVisibilityChange(SoRProjectionKind.BOKORYS, checked)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            MongeDivider()
        }


        ProjectionVisibilityToggleRow(
            label = "Vybarvit:",
            checked = fillFaces,
            onCheckedChange = onFillFacesChange,
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
@Composable
fun EditableSoRinfo(
    sorId: String,
    state: MongeState,
    onApply: (String) -> Unit,
    onWidthChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    onDelete: () -> Unit,
    uiScale: Float
) {
    val sor = state.solidsOfRevolutionNarys.find { it.id == sorId } ?: return
    val visibility = remember(
        sor.id,
        state.triggerRedraw,
        state.curvesAxo.size,
        state.curvesBokorys.size,
        state.conicsAxo.size,
        state.conicsPudorys.size,
        state.conicsNarys.size,
        state.conicsBokorys.size,
        state.arcsNarys.size,
        state.segmentsNarys.size
    ) {
        collectSoRAffectedIds(state, sor.id)?.let { sorProjectionVisibility(state, it, sor.id) }
    }

    EditableSoRinfoContent(
        name = sor.name,
        color = sor.color,
        strokeWidth = sor.strokeWidth,
        state = state,
        uiScale = uiScale,
        projectionVisibility = visibility,
        onProjectionVisibilityChange = { kind, checked ->
            collectSoRAffectedIds(state, sor.id)?.let { setSoRProjectionVisible(state, it, sor.id, kind, checked) }
        },
        fillFaces = sor.fillFaces,
        onFillFacesChange = { checked ->
            state.solidsOfRevolutionNarys.indexOfFirst { it.id == sor.id }.takeIf { it >= 0 }?.let { idx ->
                state.solidsOfRevolutionNarys[idx] = state.solidsOfRevolutionNarys[idx].copy(fillFaces = checked)
            }
            state.triggerRedraw++
            commitSnapshot(state)
        },
        onApply = onApply,
        onWidthChange = onWidthChange,
        onColorChange = onColorChange,
        onDelete = onDelete
    )
}
@Composable
fun EditableSoRinfoPud(
    sorId: String,
    state: MongeState,
    onApply: (String) -> Unit,
    onWidthChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    onDelete: () -> Unit,
    uiScale: Float
) {
    val sor = state.solidsOfRevolutionPudorys.find { it.id == sorId } ?: return
    val visibility = remember(
        sor.id,
        state.triggerRedraw,
        state.curvesAxo.size,
        state.curvesBokorys.size,
        state.conicsAxo.size,
        state.conicsPudorys.size,
        state.conicsNarys.size,
        state.conicsBokorys.size,
        state.arcsPudorys.size,
        state.segmentsPudorys.size
    ) {
        collectSoRAffectedIdsPud(state, sor.id)?.let { sorProjectionVisibility(state, it, sor.id) }
    }

    EditableSoRinfoContent(
        name = sor.name,
        color = sor.color,
        strokeWidth = sor.strokeWidth,
        state = state,
        uiScale = uiScale,
        projectionVisibility = visibility,
        onProjectionVisibilityChange = { kind, checked ->
            collectSoRAffectedIdsPud(state, sor.id)?.let { setSoRProjectionVisible(state, it, sor.id, kind, checked) }
        },
        fillFaces = sor.fillFaces,
        onFillFacesChange = { checked ->
            state.solidsOfRevolutionPudorys.indexOfFirst { it.id == sor.id }.takeIf { it >= 0 }?.let { idx ->
                state.solidsOfRevolutionPudorys[idx] = state.solidsOfRevolutionPudorys[idx].copy(fillFaces = checked)
            }
            state.triggerRedraw++
            commitSnapshot(state)
        },
        onApply = onApply,
        onWidthChange = onWidthChange,
        onColorChange = onColorChange,
        onDelete = onDelete
    )
}
fun deleteSoR(state: MongeState, sor: SolidOfRevolutionNarys) {

    // 1) sesbírej všechny runtime IDčka, která mají pryč
    val meridianIds = (sor.meridianIdsNarys + sor.mirroredMeridianIdsNarys).distinct()

    val axisId      = sor.axisLine3DId

    // 2) helper: bezpečně odeber z MutableList podle id
    fun <T> MutableList<T>.removeAllById(getId: (T) -> String, id: String) {
        // removeAll je ok i pro mutableStateListOf
        removeAll { getId(it) == id }
    }

    // 3) smaž “meridian piece” v NÁRYSU – tady musíš uvést VŠECHNY listy,
    //    ve kterých se mohou nacházet objekty, které umí být poledníkem
    fun deleteMeridianPieceNarysById(id: String) {
        // ---- přímky / úsečky / oblouky / kuželosečky / obecné křivky / ...
        state.segmentsNarys.removeAllById({ it.id }, id)
        state.helpSegmentsNarys.removeAllById({ it.id }, id)
        state.arcsNarys.removeAllById({ it.id }, id)
        state.conicsNarys.removeAllById({ it.id }, id)
        state.curvesNarys.removeAllById({ it.id }, id)
        state.circlesNarys.removeAllById({ it.id }, id)

    }

    // 5) smaž osu (Line3D) + její projekce, pokud je u tebe osa reprezentovaná i jako projekce
    fun deleteAxisLine3DById(id: String) {
        val axis = state.lines3D.find { it.id == id }?: return
        deleteLine3D(state,axis,true)
        state.lines3D.removeAllById({ it.id }, id)
        state.lines3DPudorys.removeAll { it.id == id || it.parent?.id == id || it.parentId == id }
        state.lines3DNarys.removeAll { it.id == id || it.parent?.id == id || it.parentId == id }
        state.lines3DBokorys.removeAll { it.id == id || it.parent?.id == id || it.parentId == id }
        state.lines3DAxo.removeAll { it.id == id || it.parent?.id == id || it.parentId == id }
        deleteProjectedLinePointsByParentId(state, id)
    }

    // 6) (volitelné, ale doporučené) – před mazáním odznač výběry/hover stavy,
    // aby ti v UI nezůstaly dangling reference
    state.selectedSolidOfRevolutionId= null
    state.selectedMeridianNarysIds.removeAll(meridianIds.toSet()) // podle toho jak máš typ
    // případně: state.clearHoverIfMatches(ids...)

    // 7) proveď mazání všeho obsahu SoR
    val parallelConicPIds = sor.circleIdsPudorys.toSet()
    val parallelConicNIds = sor.circleIdsNarys.toSet()
    deleteSoRParallelConicsByIds(state, parallelConicPIds, parallelConicNIds)
    meridianIds.forEach { deleteMeridianPieceNarysById(it) }
    deleteAxisLine3DById(axisId)
    deleteSoRAxoContours(state, sor.id)
    deleteSoRBokorysMeridians(state, sor.id)

    // 8) nakonec smaž samotnou rotační plochu / těleso ze seznamu SoR
    state.solidsOfRevolutionNarys.removeAll { it.id == sor.id }
}
fun deleteSoRPud(state: MongeState, sor: SolidOfRevolutionPudorys) {

    // 1) sesbírej všechny runtime IDčka, která mají pryč
    val meridianIds = (sor.meridianIdsPudorys + sor.mirroredMeridianIdsPudorys).distinct()

    val axisId      = sor.axisLine3DId

    // 2) helper: bezpečně odeber z MutableList podle id
    fun <T> MutableList<T>.removeAllById(getId: (T) -> String, id: String) {
        // removeAll je ok i pro mutableStateListOf
        removeAll { getId(it) == id }
    }

    // 3) smaž “meridian piece” v NÁRYSU – tady musíš uvést VŠECHNY listy,
    //    ve kterých se mohou nacházet objekty, které umí být poledníkem
    fun deleteMeridianPieceNarysById(id: String) {
        // ---- přímky / úsečky / oblouky / kuželosečky / obecné křivky / ...
        state.segmentsPudorys.removeAllById({ it.id }, id)
        state.helpSegmentsPudorys.removeAllById({ it.id }, id)
        state.arcsPudorys.removeAllById({ it.id }, id)
        state.conicsPudorys.removeAllById({ it.id }, id)
        state.curvesPudorys.removeAllById({ it.id }, id)
        state.circlesPudorys.removeAllById({ it.id }, id)

    }

    // 5) smaž osu (Line3D) + její projekce, pokud je u tebe osa reprezentovaná i jako projekce
    fun deleteAxisLine3DById(id: String) {
        val axis = state.lines3D.find { it.id == id }?: return
        deleteLine3D(state,axis,true)
        state.lines3D.removeAllById({ it.id }, id)
        state.lines3DPudorys.removeAll { it.id == id || it.parent?.id == id || it.parentId == id }
        state.lines3DNarys.removeAll { it.id == id || it.parent?.id == id || it.parentId == id }
        state.lines3DBokorys.removeAll { it.id == id || it.parent?.id == id || it.parentId == id }
        state.lines3DAxo.removeAll { it.id == id || it.parent?.id == id || it.parentId == id }
        deleteProjectedLinePointsByParentId(state, id)
    }

    // 6) (volitelné, ale doporučené) – před mazáním odznač výběry/hover stavy,
    // aby ti v UI nezůstaly dangling reference
    state.selectedSolidOfRevolutionId= null
    state.selectedMeridianPudorysIds.removeAll(meridianIds.toSet()) // podle toho jak máš typ
    // případně: state.clearHoverIfMatches(ids...)

    // 7) proveď mazání všeho obsahu SoR
    val parallelConicPIds = sor.circleIdsPudorys.toSet()
    val parallelConicNIds = sor.circleIdsNarys.toSet()
    deleteSoRParallelConicsByIds(state, parallelConicPIds, parallelConicNIds)
    meridianIds.forEach { deleteMeridianPieceNarysById(it) }
    deleteAxisLine3DById(axisId)
    deleteSoRAxoContours(state, sor.id)
    deleteSoRBokorysMeridians(state, sor.id)

    // 8) nakonec smaž samotnou rotační plochu / těleso ze seznamu SoR
    state.solidsOfRevolutionPudorys.removeAll { it.id == sor.id }
}
private data class SoRAffectedIds(
    val meridianIdsNarys: Set<String>,
    val meridianIdsPudorys: Set<String>,
    val parallelConicProjIdsPudorys: Set<String>,
    val parallelConicProjIdsNarys: Set<String>,
    val parallelConicParentIds3D: Set<String>,
)

private fun collectSoRAffectedIds(state: MongeState, sorId: String): SoRAffectedIds? {
    val sor = state.solidsOfRevolutionNarys.firstOrNull { it.id == sorId } ?: return null

    val meridianIds = (sor.meridianIdsNarys + sor.mirroredMeridianIdsNarys).toSet()
    val pProjIds = sor.circleIdsPudorys.toSet()
    val nProjIds = sor.circleIdsNarys.toSet()

    // parentId z obou projekcí rovnoběžek (3D kružnice)
    val parentIds = HashSet<String>()
    state.conicsPudorys.asSequence()
        .filter { it.id in pProjIds }
        .forEach { conicParentIdOf(it.parentId, it.parent)?.let(parentIds::add) }

    state.conicsNarys.asSequence()
        .filter { it.id in nProjIds }
        .forEach { conicParentIdOf(it.parentId, it.parent)?.let(parentIds::add) }

    return SoRAffectedIds(
        meridianIdsNarys = meridianIds,
        parallelConicProjIdsPudorys = pProjIds,
        parallelConicProjIdsNarys = nProjIds,
        parallelConicParentIds3D = parentIds,
        meridianIdsPudorys = emptySet()
    )
}
private fun collectSoRAffectedIdsPud(state: MongeState, sorId: String): SoRAffectedIds? {
    val sor = state.solidsOfRevolutionPudorys.firstOrNull { it.id == sorId } ?: return null

    val meridianIds = (sor.meridianIdsPudorys + sor.mirroredMeridianIdsPudorys).toSet()
    val pProjIds = sor.circleIdsPudorys.toSet()
    val nProjIds = sor.circleIdsNarys.toSet()

    // parentId z obou projekcí rovnoběžek (3D kružnice)
    val parentIds = HashSet<String>()
    state.conicsPudorys.asSequence()
        .filter { it.id in pProjIds }
        .forEach { conicParentIdOf(it.parentId, it.parent)?.let(parentIds::add) }

    state.conicsNarys.asSequence()
        .filter { it.id in nProjIds }
        .forEach { conicParentIdOf(it.parentId, it.parent)?.let(parentIds::add) }

    return SoRAffectedIds(
        meridianIdsPudorys = meridianIds,
        parallelConicProjIdsPudorys = pProjIds,
        parallelConicProjIdsNarys = nProjIds,
        parallelConicParentIds3D = parentIds,
        meridianIdsNarys = emptySet()
    )
}
fun applySoRColor(state: MongeState, sorId: String, newColor: Color) {
    val sor = state.solidsOfRevolutionNarys.firstOrNull { it.id == sorId } ?: return
    val ids = collectSoRAffectedIds(state, sorId) ?: return

    // 1) samotný SoR (u tebe má color ve data class, takže buď var, nebo copy+replaceAll)
    // Pokud color není var, musíš udělat copy a replaceAll:
    val updatedSor = sor.copy(color = newColor)
    state.solidsOfRevolutionNarys.replaceAll { if (it.id == sorId) updatedSor else it }
    if (state.selectedSolidOfRevolutionId == sorId) {
        state.selectedSolidOfRevolutionId = sorId // necháš, nebo máš selected object ref? podle tebe je id
    }
    updateSoRAxoContourColor(state, sorId, newColor)
    updateSoRBokorysMeridianColor(state, sorId, newColor)

    // 2) MERIDIÁN v NÁRYSU – všechny typy kusů podle id
    val mIds = ids.meridianIdsNarys
    if (mIds.isNotEmpty()) {
        state.segmentsNarys.replaceAll { s -> if (s.id in mIds) s.copy(localColor = newColor) else s }
        state.helpSegmentsNarys.replaceAll { s -> if (s.id in mIds) s.copy(localColor = newColor) else s }
        state.arcsNarys.replaceAll { a -> if (a.id in mIds) a.copy(color = newColor) else a }
        state.conicsNarys.replaceAll { c -> if (c.id in mIds) c.copy(localColor = newColor) else c }
        state.curvesNarys.replaceAll { c -> if (c.id in mIds) c.copy(color = newColor) else c }

        // jen pokud meridian může být i kružnice objektem:
        state.circlesNarys.replaceAll { c -> if (c.id in mIds) c.copy(localColor = newColor) else c }
    }

    // 3) ROVNOBĚŽKY = 3D kružnice + projekce
    val pParents = ids.parallelConicParentIds3D
    if (pParents.isNotEmpty()) {
        // 3D
        state.conics3D.forEach { c3d ->
            if (c3d.id in pParents) c3d.color = newColor
        }
        // projekce (bez ohledu na jejich id, přes parentId je to robustní)
        state.conicsPudorys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(localColor = newColor) else p
        }
        state.conicsNarys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(localColor = newColor) else p
        }
        state.conicsBokorys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(localColor = newColor) else p
        }
        state.conicsAxo.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(localColor = newColor) else p
        }
    } else {
        // fallback (kdybys chtěl přesně jen ids ze SoR):
        val pIds = ids.parallelConicProjIdsPudorys
        val nIds = ids.parallelConicProjIdsNarys
        state.conicsPudorys.replaceAll { p -> if (p.id in pIds) p.copy(localColor = newColor) else p }
        state.conicsNarys.replaceAll { p -> if (p.id in nIds) p.copy(localColor = newColor) else p }
    }

    // 4) selection cache (stejně jako u cone)
    state.selectedConicsPudorys.clear()
    state.selectedConicsNarys.clear()
    state.selectedConicsBokorys.clear()
    state.selectedConicsAxo.clear()


}
fun applySoRColorPud(state: MongeState, sorId: String, newColor: Color) {
    val sor = state.solidsOfRevolutionPudorys.firstOrNull { it.id == sorId } ?: return
    val ids = collectSoRAffectedIdsPud(state, sorId) ?: return

    // 1) samotný SoR (u tebe má color ve data class, takže buď var, nebo copy+replaceAll)
    // Pokud color není var, musíš udělat copy a replaceAll:
    val updatedSor = sor.copy(color = newColor)
    state.solidsOfRevolutionPudorys.replaceAll { if (it.id == sorId) updatedSor else it }
    if (state.selectedSolidOfRevolutionId == sorId) {
        state.selectedSolidOfRevolutionId = sorId // necháš, nebo máš selected object ref? podle tebe je id
    }
    updateSoRAxoContourColor(state, sorId, newColor)
    updateSoRBokorysMeridianColor(state, sorId, newColor)

    // 2) MERIDIÁN v NÁRYSU – všechny typy kusů podle id
    val mIds = ids.meridianIdsPudorys
    if (mIds.isNotEmpty()) {
        state.segmentsPudorys.replaceAll { s -> if (s.id in mIds) s.copy(localColor = newColor) else s }
        state.helpSegmentsPudorys.replaceAll { s -> if (s.id in mIds) s.copy(localColor = newColor) else s }
        state.arcsPudorys.replaceAll { a -> if (a.id in mIds) a.copy(color = newColor) else a }
        state.conicsPudorys.replaceAll { c -> if (c.id in mIds) c.copy(localColor = newColor) else c }
        state.curvesPudorys.replaceAll { c -> if (c.id in mIds) c.copy(color = newColor) else c }

        // jen pokud meridian může být i kružnice objektem:
        state.circlesPudorys.replaceAll { c -> if (c.id in mIds) c.copy(localColor = newColor) else c }
    }

    // 3) ROVNOBĚŽKY = 3D kružnice + projekce
    val pParents = ids.parallelConicParentIds3D
    if (pParents.isNotEmpty()) {
        // 3D
        state.conics3D.forEach { c3d ->
            if (c3d.id in pParents) c3d.color = newColor
        }
        // projekce (bez ohledu na jejich id, přes parentId je to robustní)
        state.conicsPudorys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(localColor = newColor) else p
        }
        state.conicsNarys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(localColor = newColor) else p
        }
        state.conicsBokorys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(localColor = newColor) else p
        }
        state.conicsAxo.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(localColor = newColor) else p
        }
    } else {
        // fallback (kdybys chtěl přesně jen ids ze SoR):
        val pIds = ids.parallelConicProjIdsPudorys
        val nIds = ids.parallelConicProjIdsNarys
        state.conicsPudorys.replaceAll { p -> if (p.id in pIds) p.copy(localColor = newColor) else p }
        state.conicsNarys.replaceAll { p -> if (p.id in nIds) p.copy(localColor = newColor) else p }
    }

    // 4) selection cache (stejně jako u cone)
    state.selectedConicsPudorys.clear()
    state.selectedConicsNarys.clear()
    state.selectedConicsBokorys.clear()
    state.selectedConicsAxo.clear()


}
private fun applySoRStrokeWidthPud(state: MongeState, sorId: String, newW: Float) {
    val sor = state.solidsOfRevolutionPudorys.firstOrNull { it.id == sorId } ?: return
    val ids = collectSoRAffectedIdsPud(state, sorId) ?: return

    // 1) samotný SoR (u tebe strokeWidth je var => můžeš přímo)
    // Pozor: var ve data class uložené v state listu – pokud je to snapshot list, mutace uvnitř
    // nemusí vždy vyvolat recomposition. Bezpečnější je copy+replaceAll.
    val updatedSor = sor.copy(strokeWidth = newW)
    state.solidsOfRevolutionPudorys.replaceAll { if (it.id == sorId) updatedSor else it }
    updateSoRAxoContourStrokeWidth(state, sorId, newW)
    updateSoRBokorysMeridianStrokeWidth(state, sorId, newW)

    // 2) meridián pieces
    val mIds = ids.meridianIdsPudorys
    if (mIds.isNotEmpty()) {
        state.segmentsPudorys.replaceAll { s -> if (s.id in mIds) s.copy(localStrokeWidth = newW) else s }
        state.helpSegmentsPudorys.replaceAll { s -> if (s.id in mIds) s.copy(localStrokeWidth =  newW) else s }
        state.arcsPudorys.replaceAll { a -> if (a.id in mIds) a.copy(strokeWidth = newW) else a }
        state.conicsPudorys.replaceAll { c -> if (c.id in mIds) c.copy(strokeWidth = newW) else c }
        state.curvesPudorys.replaceAll { c -> if (c.id in mIds) c.copy(strokeWidth = newW) else c }
        state.circlesPudorys.replaceAll { c -> if (c.id in mIds) c.copy(strokeWidth = newW) else c }
    }

    // 3) rovnoběžky (projekce + 3D parent)
    val pParents = ids.parallelConicParentIds3D
    if (pParents.isNotEmpty()) {
        // pokud má Conic3D strokeWidth:
        state.conics3D.forEach { c3d ->
            if (c3d.id in pParents) c3d.strokeWidth = newW // pokud nemáš, smaž řádek
        }
        state.conicsPudorys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(strokeWidth = newW) else p
        }
        state.conicsNarys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(strokeWidth = newW) else p
        }
        state.conicsBokorys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(strokeWidth = newW) else p
        }
        state.conicsAxo.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(strokeWidth = newW) else p
        }
    }

    commitSnapshot(state)
    state.triggerRedraw++
}
private fun applySoRStrokeWidth(state: MongeState, sorId: String, newW: Float) {
    val sor = state.solidsOfRevolutionNarys.firstOrNull { it.id == sorId } ?: return
    val ids = collectSoRAffectedIds(state, sorId) ?: return

    // 1) samotný SoR (u tebe strokeWidth je var => můžeš přímo)
    // Pozor: var ve data class uložené v state listu – pokud je to snapshot list, mutace uvnitř
    // nemusí vždy vyvolat recomposition. Bezpečnější je copy+replaceAll.
    val updatedSor = sor.copy(strokeWidth = newW)
    state.solidsOfRevolutionNarys.replaceAll { if (it.id == sorId) updatedSor else it }
    updateSoRAxoContourStrokeWidth(state, sorId, newW)
    updateSoRBokorysMeridianStrokeWidth(state, sorId, newW)

    // 2) meridián pieces
    val mIds = ids.meridianIdsNarys
    if (mIds.isNotEmpty()) {
        state.segmentsNarys.replaceAll { s -> if (s.id in mIds) s.copy(localStrokeWidth = newW) else s }
        state.helpSegmentsNarys.replaceAll { s -> if (s.id in mIds) s.copy(localStrokeWidth =  newW) else s }
        state.arcsNarys.replaceAll { a -> if (a.id in mIds) a.copy(strokeWidth = newW) else a }
        state.conicsNarys.replaceAll { c -> if (c.id in mIds) c.copy(strokeWidth = newW) else c }
        state.curvesNarys.replaceAll { c -> if (c.id in mIds) c.copy(strokeWidth = newW) else c }
        state.circlesNarys.replaceAll { c -> if (c.id in mIds) c.copy(strokeWidth = newW) else c }
    }

    // 3) rovnoběžky (projekce + 3D parent)
    val pParents = ids.parallelConicParentIds3D
    if (pParents.isNotEmpty()) {
        // pokud má Conic3D strokeWidth:
        state.conics3D.forEach { c3d ->
            if (c3d.id in pParents) c3d.strokeWidth = newW // pokud nemáš, smaž řádek
        }
        state.conicsPudorys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(strokeWidth = newW) else p
        }
        state.conicsNarys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(strokeWidth = newW) else p
        }
        state.conicsBokorys.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(strokeWidth = newW) else p
        }
        state.conicsAxo.replaceAll { p ->
            if (conicParentIdOf(p.parentId, p.parent) in pParents) p.copy(strokeWidth = newW) else p
        }
    }

    commitSnapshot(state)
    state.triggerRedraw++
}
@Composable
fun sorEdit(state: MongeState) {
    val selectedSorId = state.selectedSolidOfRevolutionId
    val sor = selectedSorId?.let { id -> state.solidsOfRevolutionNarys.firstOrNull { it.id == id } }

    sor?.let { s ->
        key(s.id) {
            EditableSoRinfo(
                sorId = s.id,
                state = state,
                onApply = { newName ->
                    val cur = state.solidsOfRevolutionNarys.find { it.id == s.id } ?: return@EditableSoRinfo
                    val cleaned = newName.trim()
                    if (cur.name == cleaned) return@EditableSoRinfo

                    val updated = cur.copy(name = cleaned)
                    state.solidsOfRevolutionNarys.replaceAll { if (it.id == updated.id) updated else it }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onColorChange = { newColor ->
                    applySoRColor(state, s.id, newColor)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onWidthChange = {newWidth ->
                    applySoRStrokeWidth(state, sor.id, newWidth)
                },
                onDelete = {
                    deleteSoR(state, s)
                    clearSelection(state)
                },
                uiScale = SettingsManager.current.UIscale / 75f
            )
        }
    }
}
@Composable
fun sorEditPud(state: MongeState) {
    val selectedSorId = state.selectedSolidOfRevolutionId
    val sor = selectedSorId?.let { id -> state.solidsOfRevolutionPudorys.firstOrNull { it.id == id } }

    sor?.let { s ->
        key(s.id) {
            EditableSoRinfoPud(
                sorId = s.id,
                state = state,
                onApply = { newName ->
                    val cur = state.solidsOfRevolutionPudorys.find { it.id == s.id } ?: return@EditableSoRinfoPud
                    val cleaned = newName.trim()
                    if (cur.name == cleaned) return@EditableSoRinfoPud

                    val updated = cur.copy(name = cleaned)
                    state.solidsOfRevolutionPudorys.replaceAll { if (it.id == updated.id) updated else it }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onColorChange = { newColor ->
                    applySoRColorPud(state, s.id, newColor)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onWidthChange = {newWidth ->
                    applySoRStrokeWidthPud(state, sor.id, newWidth)
                },
                onDelete = {
                    deleteSoRPud(state, s)
                    clearSelection(state)
                },
                uiScale = SettingsManager.current.UIscale / 75f
            )
        }
    }
}
