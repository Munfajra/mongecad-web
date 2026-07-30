package ui.mongeui.toolbar.rightDescriptionBar
import utils.replaceAll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import model.LocalMongeColors
import serialization.commitSnapshot
import model.Point3D
import model.ProjectionMode
import model.classes.Segment3D
import model.classes.SegmentSolid3D
import model.classes.SegmentSolidType
import serialization.SettingsManager
import monge.input.segments.recomputeSegmentSolidVisibility
import monge.input.segments.SegmentSolidShape
import monge.input.segments.segmentSolidSpecialShape
import state.MongeState
import ui.mongeui.toolbar.SkikoButton
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import utils.deleteSegmentSolid

enum class SegmentSolidProjectionKind { AXO, PUDORYS, NARYS, BOKORYS }

@Composable
fun segmentSolidEdit(state: MongeState) {
    val selected = state.selectedSegmentSolids3D.firstOrNull() ?: return
    val solid = state.segmentSolids3D.firstOrNull { it.id == selected.id } ?: return

    key(solid.id) {
        EditableSegmentSolidInfo(
            state = state,
            solid = solid,
            uiScale = SettingsManager.current.UIscale / 75f
        )
    }
}

@Composable
private fun EditableSegmentSolidInfo(
    state: MongeState,
    solid: SegmentSolid3D,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val nameNow = solid.name.trim()
    var pendingName by remember(solid.id) { mutableStateOf(TextFieldValue(nameNow)) }
    var lastAppliedName by remember(solid.id) { mutableStateOf(nameNow) }
    var pendingColor by remember(solid.id, solid.color) { mutableStateOf(solid.color) }
    var widthValue by remember(solid.id, solid.width) { mutableStateOf(solid.width) }
    val canApply = pendingName.text.trim() != lastAppliedName

    LaunchedEffect(solid.id, solid.name) {
        val name = solid.name.trim()
        pendingName = TextFieldValue(name)
        lastAppliedName = name
    }

    fun applyName() {
        if (!canApply) return
        val newName = pendingName.text.trim()
        updateSegmentSolid(state, solid.id) { it.copy(name = newName) }
        lastAppliedName = newName
        commitSnapshot(state)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        MongeInspectorSection(segmentSolidTitle(state, solid)) {
            Text(
                text = "${solid.segmentIds3D.size} hran, ${solid.vertexPointIds.size} vrcholů",
                fontSize = ui.sp(12f),
                color = LocalMongeColors.current.text
            )
        }

        MongeDivider()

        SimpleNameEditor(
            label = "Název:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { applyName() },
            state = state
        )

        MongeDivider()

        MongeInspectorSection("") {
            MongeInspectorPropertyRow("Barva:") {
                ColorPickerDropdown(
                    selectedColor = pendingColor,
                    onColorPreview = { pendingColor = it },
                    onColorConfirm = { color ->
                        pendingColor = color
                        recolorSegmentSolid(state, solid.id, color)
                    }
                )
            }

            MongeDivider()

            MongeInspectorPropertyRow("Šířka:") {
                WidthEditor(
                    value = widthValue,
                    onValueChange = { width ->
                        widthValue = width
                        restyleSegmentSolidWidth(state, solid.id, width, commit = false)
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
                        ProjectionVisibilityToggleItem("A", segmentSolidProjectionVisible(state, solid, SegmentSolidProjectionKind.AXO)) {
                            setSegmentSolidProjectionVisible(state, solid.id, SegmentSolidProjectionKind.AXO, it)
                        },
                        ProjectionVisibilityToggleItem("P", segmentSolidProjectionVisible(state, solid, SegmentSolidProjectionKind.PUDORYS)) {
                            setSegmentSolidProjectionVisible(state, solid.id, SegmentSolidProjectionKind.PUDORYS, it)
                        },
                        ProjectionVisibilityToggleItem("N", segmentSolidProjectionVisible(state, solid, SegmentSolidProjectionKind.NARYS)) {
                            setSegmentSolidProjectionVisible(state, solid.id, SegmentSolidProjectionKind.NARYS, it)
                        },
                        ProjectionVisibilityToggleItem("B", segmentSolidProjectionVisible(state, solid, SegmentSolidProjectionKind.BOKORYS)) {
                            setSegmentSolidProjectionVisible(state, solid.id, SegmentSolidProjectionKind.BOKORYS, it)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                MongeDivider()
            }


            ProjectionVisibilityToggleRow(
                label = "Vybarvit:",
                checked = solid.fillFaces,
                onCheckedChange = { checked ->
                    updateSegmentSolid(state, solid.id) { it.copy(fillFaces = checked) }
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
                    width = ui.dp(180f),
                    height = ui.dp(34f),
                    onClick = {
                        recomputeSegmentSolidVisibility(state, solid.id)
                        commitSnapshot(state)
                    }
                ) {
                    Text(
                        "Přepočítat viditelnost",
                        fontSize = ui.sp(12f)
                    )
                }
            }

            MongeDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SkikoButton(
                    width = ui.dp(140f),
                    height = ui.dp(34f),
                    onClick = { deleteSegmentSolid(state, solid.id) }
                ) {
                    Text(
                        "Smazat těleso",
                        fontSize = ui.sp(12f)
                    )
                }
            }
        }
    }
}

private fun segmentSolidTitle(state: MongeState, solid: SegmentSolid3D): String =
    when (segmentSolidSpecialShape(state, solid)) {
        SegmentSolidShape.KRYCHLE -> "Krychle"
        SegmentSolidShape.CTYRSTEN -> "Čtyřstěn"
        null -> when (solid.type) {
            SegmentSolidType.HRANOL -> "Hranol"
            SegmentSolidType.JEHLAN -> "Jehlan"
            SegmentSolidType.MNOHOSTEN -> "Mnohostěn"
        }
    }

private fun updateSegmentSolid(
    state: MongeState,
    solidId: String,
    update: (SegmentSolid3D) -> SegmentSolid3D
) {
    var updated: SegmentSolid3D? = null
    state.segmentSolids3D.replaceAll { solid ->
        if (solid.id == solidId) update(solid).also { updated = it } else solid
    }
    updated?.let { fresh ->
        state.selectedSegmentSolids3D.replaceAll { if (it.id == solidId) fresh else it }
    }
}

fun recolorSegmentSolid(state: MongeState, solidId: String, color: Color) {
    val solid = state.segmentSolids3D.firstOrNull { it.id == solidId } ?: return
    updateSegmentSolid(state, solidId) { it.copy(color = color) }
    applySegmentSolidColor(state, solid.segmentIds3D.toSet(), color)
    recolorSegmentSolidPolygons(state, solid.polygonIds, color)
    state.triggerRedraw++
    commitSnapshot(state)
}

fun restyleSegmentSolidWidth(state: MongeState, solidId: String, width: Float, commit: Boolean = true) {
    val solid = state.segmentSolids3D.firstOrNull { it.id == solidId } ?: return
    updateSegmentSolid(state, solidId) { it.copy(width = width) }
    applySegmentSolidEdges(state, solid.segmentIds3D.toSet()) { it.copy(strokeWidth = width) }
    restyleSegmentSolidPolygons(state, solid.polygonIds, width)
    state.triggerRedraw++
    if (commit) commitSnapshot(state)
}

fun setSegmentSolidProjectionVisible(
    state: MongeState,
    solidId: String,
    kind: SegmentSolidProjectionKind,
    checked: Boolean
) {
    val solid = state.segmentSolids3D.firstOrNull { it.id == solidId } ?: return
    val ids = solid.segmentIds3D.toSet()

    when (kind) {
        SegmentSolidProjectionKind.PUDORYS -> state.segmentsPudorys.filter { it.parent?.id in ids || it.parentId in ids }.forEach {
            it.showInAxo = checked
            it.showInAxoInitial = checked
            it.start.showInAxo = checked
            it.start.showInAxoInitial = checked
            it.end.showInAxo = checked
            it.end.showInAxoInitial = checked
        }
        SegmentSolidProjectionKind.NARYS -> state.segmentsNarys.filter { it.parent?.id in ids || it.parentId in ids }.forEach {
            it.showInAxo = checked
            it.showInAxoInitial = checked
            it.start.showInAxo = checked
            it.start.showInAxoInitial = checked
            it.end.showInAxo = checked
            it.end.showInAxoInitial = checked
        }
        SegmentSolidProjectionKind.BOKORYS -> state.segmentsBokorys.filter { it.parent?.id in ids || it.parentId in ids }.forEach {
            it.showInAxo = checked
            it.showInAxoInitial = checked
            it.start.showInAxo = checked
            it.start.showInAxoInitial = checked
            it.end.showInAxo = checked
            it.end.showInAxoInitial = checked
        }
        SegmentSolidProjectionKind.AXO -> state.segmentsAxo.filter { it.parent?.id in ids || it.parentId in ids }.forEach {
            it.showInAxo = checked
            it.showInAxoInitial = checked
            it.start.showInAxo = checked
            it.start.showInAxoInitial = checked
            it.end.showInAxo = checked
            it.end.showInAxoInitial = checked
        }
    }

    state.triggerRedraw++
    commitSnapshot(state)
}

private fun segmentSolidProjectionVisible(
    state: MongeState,
    solid: SegmentSolid3D,
    kind: SegmentSolidProjectionKind
): Boolean {
    val ids = solid.segmentIds3D.toSet()
    val values = when (kind) {
        SegmentSolidProjectionKind.PUDORYS ->
            state.segmentsPudorys.filter { it.parent?.id in ids || it.parentId in ids }.map { it.showInAxo }
        SegmentSolidProjectionKind.NARYS ->
            state.segmentsNarys.filter { it.parent?.id in ids || it.parentId in ids }.map { it.showInAxo }
        SegmentSolidProjectionKind.BOKORYS ->
            state.segmentsBokorys.filter { it.parent?.id in ids || it.parentId in ids }.map { it.showInAxo }
        SegmentSolidProjectionKind.AXO ->
            state.segmentsAxo.filter { it.parent?.id in ids || it.parentId in ids }.map { it.showInAxo }
    }
    return values.isNotEmpty() && values.all { it }
}

private fun applySegmentSolidColor(
    state: MongeState,
    segmentIds: Set<String>,
    color: Color
) {
    val pointIds = state.segments3D
        .filter { it.id in segmentIds }
        .flatMap { listOf(it.start.id, it.end.id) }
        .toSet()

    val updatedPointById = mutableMapOf<String, Point3D>()
    state.sharedPoints3D.replaceAll { point ->
        if (point.id in pointIds) {
            point.copy(color = color).also { updatedPointById[it.id] = it }
        } else {
            point
        }
    }
    state.selectedPoints3D.replaceAll { point -> updatedPointById[point.id] ?: point }

    applySegmentSolidEdges(state, segmentIds) { segment ->
        segment.copy(
            start = updatedPointById[segment.start.id] ?: segment.start.copy(color = color),
            end = updatedPointById[segment.end.id] ?: segment.end.copy(color = color),
            color = color
        )
    }

    state.pointsPudorys.forEach { point ->
        updatedPointById[point.parent?.id]?.let { parent ->
            point.parent = parent
            point.localColor = color
        }
    }
    state.pointsNarys.forEach { point ->
        updatedPointById[point.parent?.id]?.let { parent ->
            point.parent = parent
            point.localColor = color
        }
    }
    state.pointsBokorys.forEach { point ->
        updatedPointById[point.parent?.id]?.let { parent ->
            point.parent = parent
            point.localColor = color
        }
    }
    state.pointsAxo.forEach { point ->
        updatedPointById[point.parent?.id]?.let { parent ->
            point.parent = parent
            point.localColor = color
        }
    }
}

private fun applySegmentSolidEdges(
    state: MongeState,
    segmentIds: Set<String>,
    update: (Segment3D) -> Segment3D
) {
    val updatedById = mutableMapOf<String, Segment3D>()
    state.segments3D.replaceAll { segment ->
        if (segment.id in segmentIds) {
            update(segment).also { updatedById[it.id] = it }
        } else {
            segment
        }
    }

    state.segmentsPudorys.replaceAll { seg ->
        updatedById[seg.parent?.id ?: seg.parentId]?.let { parent ->
            seg.copy(parent = parent).also { fresh ->
                fresh.start.parentSegment = fresh
                fresh.end.parentSegment = fresh
            }
        } ?: seg
    }
    state.segmentsNarys.replaceAll { seg ->
        updatedById[seg.parent?.id ?: seg.parentId]?.let { parent ->
            seg.copy(parent = parent).also { fresh ->
                fresh.start.parentSegment = fresh
                fresh.end.parentSegment = fresh
            }
        } ?: seg
    }
    state.segmentsBokorys.replaceAll { seg ->
        updatedById[seg.parent?.id ?: seg.parentId]?.let { parent ->
            seg.copy(parent = parent).also { fresh ->
                fresh.start.parentSegment = fresh
                fresh.end.parentSegment = fresh
            }
        } ?: seg
    }
    state.segmentsAxo.replaceAll { seg ->
        updatedById[seg.parent?.id ?: seg.parentId]?.let { parent ->
            seg.copy(parent = parent).also { fresh ->
                fresh.start.parentSegment = fresh
                fresh.end.parentSegment = fresh
            }
        } ?: seg
    }
    state.selectedSegments3D.replaceAll { updatedById[it.id] ?: it }
}

private fun recolorSegmentSolidPolygons(state: MongeState, polygonIds: List<String>, color: Color) {
    if (polygonIds.isEmpty()) return
    val ids = polygonIds.toSet()
    state.polygons3D.replaceAll { polygon ->
        if (polygon.id in ids) polygon.copy(color = color) else polygon
    }
    state.selectedPolygons.replaceAll { polygon ->
        if (polygon.id in ids) polygon.copy(color = color) else polygon
    }
    state.selectedPolygon = state.selectedPolygon?.let { polygon ->
        if (polygon.id in ids) polygon.copy(color = color) else polygon
    }
}

private fun restyleSegmentSolidPolygons(state: MongeState, polygonIds: List<String>, width: Float) {
    if (polygonIds.isEmpty()) return
    val ids = polygonIds.toSet()
    state.polygons3D.replaceAll { polygon ->
        if (polygon.id in ids) polygon.copy(width = width) else polygon
    }
    state.selectedPolygons.replaceAll { polygon ->
        if (polygon.id in ids) polygon.copy(width = width) else polygon
    }
    state.selectedPolygon = state.selectedPolygon?.let { polygon ->
        if (polygon.id in ids) polygon.copy(width = width) else polygon
    }
}
