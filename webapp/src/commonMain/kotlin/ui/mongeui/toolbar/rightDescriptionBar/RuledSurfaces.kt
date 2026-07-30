package ui.mongeui.toolbar.rightDescriptionBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import draw.mongescreen.labels.clearSelection
import kotlin.math.roundToInt
import model.LocalMongeColors
import model.ProjectionMode
import model.classes.RuledSurface3D
import monge.input.ruledsurface.RuledSurfaceProjection
import monge.input.ruledsurface.deleteRuledSurface
import monge.input.ruledsurface.recolorRuledSurfaceDirectrices
import monge.input.ruledsurface.setRuledSurfaceGeneratorProjectionVisible
import monge.input.ruledsurface.syncRuledSurfaceGeneratorLines
import monge.input.ruledsurface.updateRuledSurfaceGeneratorLineStyle
import monge.input.ruledsurface.updateRuledSurfaceOutlineStyle
import serialization.SettingsManager
import serialization.commitSnapshot
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.SkikoButton

private enum class RuledSurfaceProjectionKind { AXO, PUDORYS, NARYS, BOKORYS }

private fun ruledSurfaceProjectionVisible(
    state: MongeState,
    surface: RuledSurface3D,
    kind: RuledSurfaceProjectionKind,
): Boolean = when (kind) {
    RuledSurfaceProjectionKind.AXO -> surface.outlineCurveIdAxo?.let { id ->
        state.curvesAxo.firstOrNull { it.id == id }?.showInAxo
    } ?: true
    RuledSurfaceProjectionKind.PUDORYS -> surface.outlineCurveIdPudorys?.let { id ->
        state.curvesPudorys.firstOrNull { it.id == id }?.showInAxo
    } ?: true
    RuledSurfaceProjectionKind.NARYS -> surface.outlineCurveIdNarys?.let { id ->
        state.curvesNarys.firstOrNull { it.id == id }?.showInAxo
    } ?: true
    RuledSurfaceProjectionKind.BOKORYS -> surface.outlineCurveIdBokorys?.let { id ->
        state.curvesBokorys.firstOrNull { it.id == id }?.showInAxo
    } ?: true
}

private fun setRuledSurfaceProjectionVisible(
    state: MongeState,
    surface: RuledSurface3D,
    kind: RuledSurfaceProjectionKind,
    visible: Boolean,
) {
    when (kind) {
        RuledSurfaceProjectionKind.AXO -> surface.outlineCurveIdAxo?.let { id ->
            state.curvesAxo.firstOrNull { it.id == id }?.showInAxo = visible
        }
        RuledSurfaceProjectionKind.PUDORYS -> surface.outlineCurveIdPudorys?.let { id ->
            state.curvesPudorys.firstOrNull { it.id == id }?.showInAxo = visible
        }
        RuledSurfaceProjectionKind.NARYS -> surface.outlineCurveIdNarys?.let { id ->
            state.curvesNarys.firstOrNull { it.id == id }?.showInAxo = visible
        }
        RuledSurfaceProjectionKind.BOKORYS -> surface.outlineCurveIdBokorys?.let { id ->
            state.curvesBokorys.firstOrNull { it.id == id }?.showInAxo = visible
        }
    }
    // Přepínač řídí i materializované tvořice, ne jen obrysovou křivku.
    setRuledSurfaceGeneratorProjectionVisible(
        state, surface,
        when (kind) {
            RuledSurfaceProjectionKind.AXO -> RuledSurfaceProjection.AXO
            RuledSurfaceProjectionKind.PUDORYS -> RuledSurfaceProjection.PUDORYS
            RuledSurfaceProjectionKind.NARYS -> RuledSurfaceProjection.NARYS
            RuledSurfaceProjectionKind.BOKORYS -> RuledSurfaceProjection.BOKORYS
        },
        visible,
    )
    state.triggerRedraw++
    commitSnapshot(state)
}

@Composable
fun ruledSurfaceEdit(state: MongeState) {
    val id = state.selectedRuledSurfaceId ?: return
    val surface = state.ruledSurfaces.firstOrNull { it.id == id } ?: return
    key(surface.id) { EditableRuledSurfaceInfo(state, surface) }
}

@Composable
private fun EditableRuledSurfaceInfo(state: MongeState, surface: RuledSurface3D) {
    val ui = remember { UiScale(SettingsManager.current.UIscale / 75f) }
    var pendingName by remember(surface.id) { mutableStateOf(TextFieldValue(surface.name)) }
    var lastName by remember(surface.id) { mutableStateOf(surface.name) }
    var pendingColor by remember(surface.id, surface.color) { mutableStateOf(surface.color) }
    var width by remember(surface.id, surface.wireWidth) { mutableStateOf(surface.wireWidth) }
    var generatorCount by remember(surface.id, surface.generatorCount) { mutableStateOf(surface.generatorCount.toFloat()) }
    var generatorDirty by remember(surface.id) { mutableStateOf(false) }

    LaunchedEffect(surface.name) {
        pendingName = TextFieldValue(surface.name)
        lastName = surface.name
    }

    fun replace(transform: (RuledSurface3D) -> RuledSurface3D, commit: Boolean = true) {
        var changed = false
        Snapshot.withMutableSnapshot {
            val index = state.ruledSurfaces.indexOfFirst { it.id == surface.id }
            if (index < 0) return@withMutableSnapshot
            val previous = state.ruledSurfaces[index]
            val updated = transform(previous)
            state.ruledSurfaces[index] = updated
            if (previous.color != updated.color) recolorRuledSurfaceDirectrices(state, updated, updated.color)
            updateRuledSurfaceOutlineStyle(state, updated)
            val generatorsChanged = previous.generatorCount != updated.generatorCount ||
                previous.generatorTrimStartEnabled != updated.generatorTrimStartEnabled ||
                previous.generatorTrimEndEnabled != updated.generatorTrimEndEnabled ||
                previous.generatorTrimStart != updated.generatorTrimStart ||
                previous.generatorTrimEnd != updated.generatorTrimEnd ||
                previous.generatorOverhangStart != updated.generatorOverhangStart ||
                previous.generatorOverhangEnd != updated.generatorOverhangEnd
            if (generatorsChanged) {
                syncRuledSurfaceGeneratorLines(state, updated)
            } else {
                updateRuledSurfaceGeneratorLineStyle(state, updated)
            }
            state.triggerRedraw++
            changed = true
        }
        if (changed && commit) commitSnapshot(state)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f)),
    ) {
        MongeInspectorSection(when {
            listOf(surface.firstBoundaryDirectrix, surface.secondBoundaryDirectrix)
                .any { it.kind == model.classes.RuledSurfaceDirectrixKind.SPHERE } -> "Kulový konoid"
            surface.directorPlaneId != null -> "Konoid"
            else -> "Přímková plocha"
        }) {}
        MongeDivider()

        SimpleNameEditor(
            label = "Název:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = pendingName.text.trim() != lastName,
            onApply = {
                val value = pendingName.text.trim()
                replace({ it.copy(name = value) })
                lastName = value
            },
            state = state,
        )

        MongeDivider()
        MongeInspectorSection("") {
            MongeInspectorPropertyRow("Barva:") {
                ColorPickerDropdown(
                    selectedColor = pendingColor,
                    onColorPreview = { pendingColor = it },
                    onColorConfirm = { color ->
                        pendingColor = color
                        replace({ it.copy(color = color) })
                    },
                )
            }
            MongeDivider()
            MongeInspectorPropertyRow("Šířka:") {
                WidthEditor(
                    value = width,
                    onValueChange = { value ->
                        width = value
                        replace({ it.copy(wireWidth = value) }, commit = false)
                    },
                    state = state,
                )
            }
            MongeDivider()
            if (state.projectionMode == ProjectionMode.AXO) {
                MongeInspectorPropertyRow(
                    label = "Průměty:",
                    contentAlign = Alignment.End,
                ) {
                    ProjectionVisibilityToggleStrip(
                        ui = ui,
                        ProjectionVisibilityToggleItem(
                            "A",
                            ruledSurfaceProjectionVisible(state, surface, RuledSurfaceProjectionKind.AXO),
                        ) { setRuledSurfaceProjectionVisible(state, surface, RuledSurfaceProjectionKind.AXO, it) },
                        ProjectionVisibilityToggleItem(
                            "P",
                            ruledSurfaceProjectionVisible(state, surface, RuledSurfaceProjectionKind.PUDORYS),
                        ) { setRuledSurfaceProjectionVisible(state, surface, RuledSurfaceProjectionKind.PUDORYS, it) },
                        ProjectionVisibilityToggleItem(
                            "N",
                            ruledSurfaceProjectionVisible(state, surface, RuledSurfaceProjectionKind.NARYS),
                        ) { setRuledSurfaceProjectionVisible(state, surface, RuledSurfaceProjectionKind.NARYS, it) },
                        ProjectionVisibilityToggleItem(
                            "B",
                            ruledSurfaceProjectionVisible(state, surface, RuledSurfaceProjectionKind.BOKORYS),
                        ) { setRuledSurfaceProjectionVisible(state, surface, RuledSurfaceProjectionKind.BOKORYS, it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MongeDivider()
            }
            MongeInspectorPropertyRow("Tvořicí:") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ui.dp(8f)),
                ) {
                    MongeStepSlider(
                        value = generatorCount,
                        onValueChange = { value ->
                            generatorCount = value
                            generatorDirty = true
                            replace({ it.copy(generatorCount = value.toInt()) }, commit = false)
                        },
                        onValueChangeFinished = {
                            if (generatorDirty) {
                                commitSnapshot(state)
                                generatorDirty = false
                            }
                        },
                        min = 0f,
                        max = 100f,
                        step = 1f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = generatorCount.toInt().toString(),
                        modifier = Modifier.width(ui.dp(34f)),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        MongeDivider()
        GeneratorTrimSection(state, surface, ui, ::replace)

        MongeDivider()
        ProjectionVisibilityToggleRow(
            label = "Vybarvit:",
            checked = surface.fillFaces,
            onCheckedChange = { checked -> replace({ it.copy(fillFaces = checked) }) },
            ui = ui,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SkikoButton(
                width = ui.dp(100f),
                height = ui.dp(38f),
                onClick = {
                    deleteRuledSurface(state, surface.id)
                    clearSelection(state)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
            ) { Text("Smazat", fontSize = ui.sp(13f)) }
        }
    }
}

/**
 * Ořez tvořic plochy. Každá strana má vlastní přepínač: se zapnutým ořezem
 * drží posuvník pozici řezu mezi krajními řídicími křivkami (0–100 %), s
 * vypnutým určuje posuvník absolutní délku přesahu tvořic za řídicí křivku.
 */
@Composable
private fun GeneratorTrimSection(
    state: MongeState,
    surface: RuledSurface3D,
    ui: UiScale,
    replace: ((RuledSurface3D) -> RuledSurface3D, Boolean) -> Unit,
) {
    val colors = LocalMongeColors.current
    var startPercent by remember(surface.id, surface.generatorTrimStart) {
        mutableStateOf(surface.generatorTrimStart * 100f)
    }
    var endPercent by remember(surface.id, surface.generatorTrimEnd) {
        mutableStateOf(surface.generatorTrimEnd * 100f)
    }
    var overhangStart by remember(surface.id, surface.generatorOverhangStart) {
        mutableStateOf(surface.generatorOverhangStart)
    }
    var overhangEnd by remember(surface.id, surface.generatorOverhangEnd) {
        mutableStateOf(surface.generatorOverhangEnd)
    }
    var trimDirty by remember(surface.id) { mutableStateOf(false) }
    fun commitDrag() {
        if (trimDirty) {
            commitSnapshot(state)
            trimDirty = false
        }
    }

    MongeInspectorSection("Ořez tvořic") {
        Text(
            text = "Ořezaná strana: pozice řezu mezi krajními řídicími křivkami " +
                "(0 % = první, 100 % = druhá). Neořezaná strana: délka přesahu za křivku.",
            fontSize = ui.sp(11f),
            color = colors.text.copy(alpha = 0.65f),
        )
        GeneratorTrimSideEditor(
            label = "Začátek:",
            ui = ui,
            trimmed = surface.generatorTrimStartEnabled,
            onTrimmedChange = { checked ->
                replace({ it.copy(generatorTrimStartEnabled = checked) }, true)
            },
            fractionPercent = startPercent,
            onFractionPercentChange = { percent ->
                val limit = if (surface.generatorTrimEndEnabled) endPercent - 1f else 100f
                val value = percent.coerceIn(0f, limit.coerceAtLeast(0f))
                if (value != startPercent) {
                    startPercent = value
                    trimDirty = true
                    replace({ it.copy(generatorTrimStart = value / 100f) }, false)
                }
            },
            overhang = overhangStart,
            onOverhangChange = { value ->
                if (value != overhangStart) {
                    overhangStart = value
                    trimDirty = true
                    replace({ it.copy(generatorOverhangStart = value) }, false)
                }
            },
            onDragFinished = ::commitDrag,
        )
        GeneratorTrimSideEditor(
            label = "Konec:",
            ui = ui,
            trimmed = surface.generatorTrimEndEnabled,
            onTrimmedChange = { checked ->
                replace({ it.copy(generatorTrimEndEnabled = checked) }, true)
            },
            fractionPercent = endPercent,
            onFractionPercentChange = { percent ->
                val limit = if (surface.generatorTrimStartEnabled) startPercent + 1f else 0f
                val value = percent.coerceIn(limit.coerceAtMost(100f), 100f)
                if (value != endPercent) {
                    endPercent = value
                    trimDirty = true
                    replace({ it.copy(generatorTrimEnd = value / 100f) }, false)
                }
            },
            overhang = overhangEnd,
            onOverhangChange = { value ->
                if (value != overhangEnd) {
                    overhangEnd = value
                    trimDirty = true
                    replace({ it.copy(generatorOverhangEnd = value) }, false)
                }
            },
            onDragFinished = ::commitDrag,
        )
    }
}

/** Jedna strana ořezu: hlavička s režimem a přepínačem, pod ní posuvník s hodnotou. */
@Composable
private fun GeneratorTrimSideEditor(
    label: String,
    ui: UiScale,
    trimmed: Boolean,
    onTrimmedChange: (Boolean) -> Unit,
    fractionPercent: Float,
    onFractionPercentChange: (Float) -> Unit,
    overhang: Float,
    onOverhangChange: (Float) -> Unit,
    onDragFinished: () -> Unit,
) {
    val colors = LocalMongeColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = ui.sp(13f),
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (trimmed) "Ořez na křivku" else "Volný přesah",
                fontSize = ui.sp(12f),
                color = colors.text.copy(alpha = 0.7f),
            )
            Checkbox(
                checked = trimmed,
                onCheckedChange = onTrimmedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.selected,
                    uncheckedColor = Color.Gray,
                    checkmarkColor = Color.White,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ui.dp(8f)),
        ) {
            if (trimmed) {
                MongeStepSlider(
                    value = fractionPercent,
                    onValueChange = onFractionPercentChange,
                    onValueChangeFinished = onDragFinished,
                    min = 0f,
                    max = 100f,
                    step = 1f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${fractionPercent.roundToInt()} %",
                    fontSize = ui.sp(12f),
                    color = colors.text,
                    modifier = Modifier.width(ui.dp(46f)),
                    textAlign = TextAlign.End,
                )
            } else {
                MongeStepSlider(
                    value = overhang,
                    onValueChange = onOverhangChange,
                    onValueChangeFinished = onDragFinished,
                    min = 0f,
                    max = 600f,
                    step = 10f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+${overhang.roundToInt()}",
                    fontSize = ui.sp(12f),
                    color = colors.text,
                    modifier = Modifier.width(ui.dp(46f)),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
