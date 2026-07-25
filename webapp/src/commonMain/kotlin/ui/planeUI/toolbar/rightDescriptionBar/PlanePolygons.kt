package ui.planeUI.toolbar.rightDescriptionBar

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
import draw.mongescreen.labels.clearSelection
import model.LineStyle
import model.classes.HelpSegmentPudorys
import model.classes.PlanePolygon2D
import monge.input.segments.deletePlanePolygon2D
import serialization.SettingsManager
import serialization.commitSnapshot
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.SkikoButton
import ui.mongeui.toolbar.rightDescriptionBar.LineStyleSelector
import ui.mongeui.toolbar.rightDescriptionBar.SimpleNameEditor
import ui.mongeui.toolbar.rightDescriptionBar.UiScale
import ui.mongeui.toolbar.rightDescriptionBar.WidthEditor

@Composable
fun planePolygonEdit(state: MongeState) {
    val selectedPolygon = state.selectedPlanePolygons2D.firstOrNull()

    selectedPolygon?.let { polygon ->
        key(polygon.id) {
            EditablePlanePolygonInfo(
                polygonId = polygon.id,
                state = state,
                onApplyName = { newName ->
                    renamePlanePolygon2D(state, polygon.id, newName)
                },
                onColorChange = { newColor ->
                    recolorPlanePolygon2D(state, polygon.id, newColor)
                },
                onWidthChange = { newWidth ->
                    restylePlanePolygon2DWidth(state, polygon.id, newWidth, commit = false)
                },
                onStyleChange = { newStyle ->
                    restylePlanePolygon2DLineStyle(state, polygon.id, newStyle)
                },
                onDelete = {
                    deletePlanePolygon2D(state, polygon.id)
                    clearSelection(state)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                uiScale = SettingsManager.current.UIscale / 75f
            )
        }
    }
}

@Composable
fun EditablePlanePolygonInfo(
    polygonId: String,
    state: MongeState,
    onApplyName: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onDelete: () -> Unit,
    uiScale: Float
) {
    val polygon = state.planePolygons2D.find { it.id == polygonId } ?: return
    val ui = remember(uiScale) { UiScale(uiScale) }

    var pendingColor by remember(polygon.id, polygon.color) {
        mutableStateOf(polygon.color)
    }
    var pendingWidth by remember(polygon.id, polygon.width) {
        mutableStateOf(polygon.width)
    }

    val nameNow = polygon.name.trim()
    var pendingName by remember(polygon.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }
    var lastAppliedName by remember(polygon.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(polygon.id, polygon.name) {
        val currentName = polygon.name.trim()
        lastAppliedName = currentName
        pendingName = TextFieldValue(currentName)
    }

    val canApply = pendingName.text.trim() != lastAppliedName

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
        MongeInspectorSection("Mnohoúhelník") {}

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
                        onColorChange(color)
                    }
                )
            }

            MongeDivider()

            MongeInspectorPropertyRow("Šířka:") {
                WidthEditor(
                    value = pendingWidth,
                    onValueChange = {
                        pendingWidth = it
                        onWidthChange(it)
                    },
                    state = state
                )
            }

            MongeDivider()

            MongeInspectorPropertyRow(
                label = "Styl čáry:",
                contentAlign = Alignment.End
            ) {
                LineStyleSelector(
                    current = polygon.style,
                    onStyleChange = onStyleChange
                )
            }
        }

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
                    text = "Smazat",
                    fontSize = ui.sp(13f)
                )
            }
        }
    }
}

fun renamePlanePolygon2D(state: MongeState, polygonId: String, newName: String) {
    val current = state.planePolygons2D.find { it.id == polygonId } ?: return
    val cleaned = newName.trim()
    if (current.name == cleaned) return

    replacePlanePolygon2D(state, current.copy(name = cleaned))

    state.triggerRedraw++
    commitSnapshot(state)
}

fun recolorPlanePolygon2D(state: MongeState, polygonId: String, newColor: Color) {
    val polygon = state.planePolygons2D.find { it.id == polygonId } ?: return
    if (polygon.color == newColor) return

    replacePlanePolygon2D(state, polygon.copy(color = newColor))

    val segmentIds = polygon.segmentIdsPudorys.toSet()
    val vertexPointIds = polygon.vertexPointIdsPudorys.toSet()
    val aidPointIds = polygon.vertexAidPointIds.toSet()

    state.helpSegmentsPudorys.replaceAll { segment ->
        if (segment.id in segmentIds) segment.copy(localColor = newColor) else segment
    }
    state.selectedSegmentsPudorys.replaceAll { segment ->
        if (segment.id in segmentIds) {
            (segment as? HelpSegmentPudorys)?.copy(localColor = newColor) ?: segment
        } else {
            segment
        }
    }

    state.pointsPudorys.forEach { point ->
        if (point.id in vertexPointIds) point.localColor = newColor
    }
    state.selectedPointsPudorys.forEach { point ->
        if (point.id in vertexPointIds) point.localColor = newColor
    }
    state.aidPointsLogical.forEach { point ->
        if (point.id in aidPointIds) point.color = newColor
    }

    state.triggerRedraw++
    commitSnapshot(state)
}

fun restylePlanePolygon2DWidth(
    state: MongeState,
    polygonId: String,
    newWidth: Float,
    commit: Boolean = true
) {
    val polygon = state.planePolygons2D.find { it.id == polygonId } ?: return
    if (polygon.width == newWidth) return

    replacePlanePolygon2D(state, polygon.copy(width = newWidth))

    val segmentIds = polygon.segmentIdsPudorys.toSet()

    state.helpSegmentsPudorys.replaceAll { segment ->
        if (segment.id in segmentIds) segment.copy(localStrokeWidth = newWidth) else segment
    }
    state.selectedSegmentsPudorys.replaceAll { segment ->
        if (segment.id in segmentIds) {
            (segment as? HelpSegmentPudorys)?.copy(localStrokeWidth = newWidth) ?: segment
        } else {
            segment
        }
    }

    state.triggerRedraw++
    if (commit) commitSnapshot(state)
}

fun restylePlanePolygon2DLineStyle(state: MongeState, polygonId: String, newStyle: LineStyle) {
    val polygon = state.planePolygons2D.find { it.id == polygonId } ?: return
    if (polygon.style == newStyle) return

    replacePlanePolygon2D(state, polygon.copy(style = newStyle))

    val segmentIds = polygon.segmentIdsPudorys.toSet()
    state.helpSegmentsPudorys.replaceAll { segment ->
        if (segment.id in segmentIds) segment.copy(localLineStyle = newStyle) else segment
    }
    state.selectedSegmentsPudorys.replaceAll { segment ->
        if (segment.id in segmentIds) {
            (segment as? HelpSegmentPudorys)?.copy(localLineStyle = newStyle) ?: segment
        } else {
            segment
        }
    }

    state.triggerRedraw++
    commitSnapshot(state)
}

private fun replacePlanePolygon2D(state: MongeState, updated: PlanePolygon2D) {
    state.planePolygons2D.replaceAll { if (it.id == updated.id) updated else it }
    state.selectedPlanePolygons2D.replaceAll { if (it.id == updated.id) updated else it }
}
