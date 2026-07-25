package ui.mongeui.toolbar.rightDescriptionBar

import utils.replaceAll
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.LineStyle
import model.classes.ArcAxoOverlay
import model.classes.Arc2DBokorys
import model.classes.Arc2DNarys
import model.classes.Arc2DPudorys
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.mongeui.toolbar.SkikoButton

@Composable
fun arcEdit(state: MongeState) {
    val selectedArc =
        state.selectedArcsPudorys.firstOrNull()
            ?: state.selectedArcsNarys.firstOrNull()
            ?: state.selectedArcsBokorys.firstOrNull()
            ?: state.selectedArcsAxoOverlay.firstOrNull()

    val currentArc: Any? = when (selectedArc) {
        is Arc2DPudorys -> state.arcsPudorys.find { it.id == selectedArc.id }
        is Arc2DNarys -> state.arcsNarys.find { it.id == selectedArc.id }
        is Arc2DBokorys -> state.arcsBokorys.find { it.id == selectedArc.id }
        is ArcAxoOverlay -> state.arcsAxoOverlay.find { it.id == selectedArc.id }
        else -> null
    }

    currentArc?.let { arc ->
        val id = when (arc) {
            is Arc2DPudorys -> arc.id
            is Arc2DNarys   -> arc.id
            is Arc2DBokorys -> arc.id
            is ArcAxoOverlay -> arc.id
            else -> return
        }

        key(id) {
            EditableArcInfo(
                state = state,
                arc = arc,
                onColorChange = { newColor ->

                    when (arc) {
                        is Arc2DPudorys -> {
                            val updated = arc.copy(color = newColor)
                            state.arcsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is Arc2DNarys -> {
                            val updated = arc.copy(color = newColor)
                            state.arcsNarys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsNarys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is Arc2DBokorys -> {
                            val updated = arc.copy(color = newColor)
                            state.arcsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is ArcAxoOverlay -> {
                            val updated = arc.copy(color = newColor)
                            state.arcsAxoOverlay.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsAxoOverlay.replaceAll { if (it.id == updated.id) updated else it }
                        }
                    }
                    commitSnapshot(state)
                },
                onWidthChange = { newWidth ->
                    when (arc) {
                        is Arc2DPudorys -> {
                            val updated = arc.copy(strokeWidth = newWidth)
                            state.arcsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is Arc2DNarys -> {
                            val updated = arc.copy(strokeWidth = newWidth)
                            state.arcsNarys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsNarys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is Arc2DBokorys -> {
                            val updated = arc.copy(strokeWidth = newWidth)
                            state.arcsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is ArcAxoOverlay -> {
                            val updated = arc.copy(strokeWidth = newWidth)
                            state.arcsAxoOverlay.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsAxoOverlay.replaceAll { if (it.id == updated.id) updated else it }
                        }
                    }
                },
                onStyleChange = { newStyle ->

                    when (arc) {
                        is Arc2DPudorys -> {
                            val updated = arc.copy(lineStyle = newStyle)
                            state.arcsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is Arc2DNarys -> {
                            val updated = arc.copy(lineStyle = newStyle)
                            state.arcsNarys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsNarys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is Arc2DBokorys -> {
                            val updated = arc.copy(lineStyle = newStyle)
                            state.arcsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is ArcAxoOverlay -> {
                            val updated = arc.copy(lineStyle = newStyle)
                            state.arcsAxoOverlay.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedArcsAxoOverlay.replaceAll { if (it.id == updated.id) updated else it }
                        }
                    }
                    commitSnapshot(state)
                },
                onDelete = {
                    deleteArc(state,arc)
                    clearSelection(state)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onApply = { newName ->

                    when (selectedArc) {
                        is Arc2DPudorys -> {
                            state.arcsPudorys.replaceFirst({ it.id == selectedArc.id }) {
                                it.copy(name = newName)
                            }
                        }
                        is Arc2DNarys -> {
                            state.arcsNarys.replaceFirst({ it.id == selectedArc.id }) {
                                it.copy(name = newName)
                            }
                        }
                        is Arc2DBokorys -> {
                            state.arcsBokorys.replaceFirst({ it.id == selectedArc.id }) {
                                it.copy(name = newName)
                            }
                        }
                        is ArcAxoOverlay -> {
                            state.arcsAxoOverlay.replaceFirst({ it.id == selectedArc.id }) {
                                it.copy(name = newName)
                            }
                        }
                    }

                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                uiScale = SettingsManager.current.UIscale/75f
            )
        }
    }
}
@Composable
fun EditableArcInfo(
    arc: Any, // Arc2DPudorys | Arc2DNarys
    onApply: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onDelete: () -> Unit,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val nameNow = when (arc) {
        is Arc2DPudorys -> arc.name
        is Arc2DNarys -> arc.name
        is Arc2DBokorys -> arc.name
        is ArcAxoOverlay -> arc.name
        else -> "oblouk"
    }.trim()
    val objectLabel = when {
        arc is Arc2DPudorys && arc.isFullCircle() -> "Kružnice:"
        arc is ArcAxoOverlay && arc.isFullCircle() -> "Kružnice:"
        else -> "Oblouk:"
    }

    val colorNow = when (arc) {
        is Arc2DPudorys -> arc.color
        is Arc2DNarys -> arc.color
        is Arc2DBokorys -> arc.color
        is ArcAxoOverlay -> arc.color
        else -> Color.Black
    }

    val widthNow = when (arc) {
        is Arc2DPudorys -> arc.strokeWidth
        is Arc2DNarys -> arc.strokeWidth
        is Arc2DBokorys -> arc.strokeWidth
        is ArcAxoOverlay -> arc.strokeWidth
        else -> 1f
    }

    val styleNow = when (arc) {
        is Arc2DPudorys -> arc.lineStyle
        is Arc2DNarys -> arc.lineStyle
        is Arc2DBokorys -> arc.lineStyle
        is ArcAxoOverlay -> arc.lineStyle
        else -> LineStyle.Solid
    }

    val arcId = when (arc) {
        is Arc2DPudorys -> arc.id
        is Arc2DNarys -> arc.id
        is Arc2DBokorys -> arc.id
        is ArcAxoOverlay -> arc.id
        else -> "unknown-arc"
    }

    var pendingName by remember(arcId) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var pendingColor by remember(arcId, colorNow) {
        mutableStateOf(colorNow)
    }

    var sliderValue by remember(arcId, widthNow) {
        mutableStateOf(widthNow)
    }

    var lastAppliedName by remember(arcId) {
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
            label = objectLabel,
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

            MongeInspectorPropertyRow("Šířka:") {
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

            MongeInspectorPropertyRow("Styl čáry:") {
                LineStyleSelector(
                    current = styleNow,
                    onStyleChange = onStyleChange
                )
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
                    "Smazat",
                    fontSize = ui.sp(13f)
                )
            }
        }
    }
}
fun deleteArc (state: MongeState,arc: Any) {
    when (arc) {
        is Arc2DPudorys -> {
            state.arcsPudorys.removeAll { it.id == arc.id }
            state.selectedArcsPudorys.removeAll { it.id == arc.id }
        }
        is Arc2DNarys -> {
            state.arcsNarys.removeAll { it.id == arc.id }
            state.selectedArcsNarys.removeAll { it.id == arc.id }
        }
        is Arc2DBokorys -> {
            state.arcsBokorys.removeAll { it.id == arc.id }
            state.selectedArcsBokorys.removeAll { it.id == arc.id }
        }
        is ArcAxoOverlay -> {
            state.arcsAxoOverlay.removeAll { it.id == arc.id }
            state.selectedArcsAxoOverlay.removeAll { it.id == arc.id }
        }
    }
}
