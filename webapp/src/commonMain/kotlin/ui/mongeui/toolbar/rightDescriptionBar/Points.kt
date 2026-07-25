package ui.mongeui.toolbar.rightDescriptionBar

import utils.replaceAll
import utils.format
import ui.components.MiniInputField2
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import dialogs.nameInput.parseKota
import utils.withSuffixOnce
import draw.mongescreen.labels.clearSelection
import draw.mongescreen.labels.formatKota
import utils.allocIndex
import utils.update2DSnapshots
import model.*
import model.classes.*

import monge.input.combineprojections.CompletePointAdd
import monge.input.combineprojections.startPoint3DCompletion
import serialization.SettingsManager
import serialization.commitSnapshot
import model.classes.isAxis
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.SkikoButton
import kotlin.math.roundToInt

private fun String?.emptyIfNullText(): String {
    val trimmed = this?.trim().orEmpty()
    return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
}

private fun Point3DPudorys.withAxoVisibilityFrom(source: Point3DPudorys): Point3DPudorys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
    }

private fun Point3DNarys.withAxoVisibilityFrom(source: Point3DNarys): Point3DNarys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
    }

private fun Point3DBokorys.withAxoVisibilityFrom(source: Point3DBokorys): Point3DBokorys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
    }

private fun Point3DAxo.withAxoVisibilityFrom(source: Point3DAxo): Point3DAxo =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
    }

@Composable
fun EditablePointInfo(
    point: Point2DProjection,
    onApply: (newName: String, newSup: String, newKota: String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onSelectAddProjection: () -> Unit,
    onAddProjection: () -> Unit,
    onDelete: () -> Unit,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current

    val isMonge = state.projectionMode == ProjectionMode.MONGE
    val isAxo = state.projectionMode == ProjectionMode.AXO
    val isKoto = state.projectionMode == ProjectionMode.KOTO

    var pendingName by remember(point.id) {
        mutableStateOf(TextFieldValue(point.name?.trim() ?: ""))
    }

    var pendingSup by remember(point.id) {
        mutableStateOf(TextFieldValue(point.localSuperscript.emptyIfNullText()))
    }

    // KOTO: samostatný půdorysný bod zatím kótu nemá – prázdné pole umožní ji přidat.
    var pendingKota by remember(point.id) {
        mutableStateOf(TextFieldValue(""))
    }

    var showGreek by remember { mutableStateOf(false) }

    val pointNameNow = (point.name ?: "").trim()
    val pointSupNow = point.localSuperscript.emptyIfNullText()

    var lastAppliedName by remember(point.id) {
        mutableStateOf(pointNameNow)
    }

    var lastAppliedSup by remember(point.id) {
        mutableStateOf(pointSupNow)
    }

    LaunchedEffect(point.id, point.name, point.localSuperscript) {
        val n = (point.name ?: "").trim()
        val s = point.localSuperscript.emptyIfNullText()

        lastAppliedName = n
        lastAppliedSup = s

        pendingName = TextFieldValue(n)
        pendingSup = TextFieldValue(s)
    }

    val canApply =
        pendingName.text.trim() != lastAppliedName ||
                pendingSup.text.trim() != lastAppliedSup ||
                (isKoto && pendingKota.text.isNotBlank())

    fun apply() {
        if (!canApply) return

        val newName = pendingName.text.trim()
        val newSup = pendingSup.text.trim()

        onApply(newName, newSup, pendingKota.text.trim())

        lastAppliedName = newName
        lastAppliedSup = newSup
        state.focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {

        MongeInspectorPropertyRow(
            label = "Bod:",
            contentAlign = Alignment.End
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (
                        e.type == KeyEventType.KeyDown &&
                        (e.key == Key.Enter || e.key == Key.NumPadEnter)
                    ) {
                        apply()
                        true
                    } else false
                }
            ) {

                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(ui.dp(5f))
                ) {

                    MiniInputField2(
                        value = pendingName,
                        onValueChange = { pendingName = it },
                        placeholder = "Název",
                        numericOnly = false,
                        fontSize = ui.sp(16f),
                        width = ui.dp(58f),
                        height = ui.dp(34f),
                        state = state
                    )

                    MiniInputField2(
                        value = pendingSup,
                        onValueChange = { pendingSup = it },
                        placeholder = "",
                        numericOnly = false,
                        fontSize = ui.sp(13f),
                        width = ui.dp(30f),
                        height = ui.dp(28f),
                        modifier = Modifier.offset(y = -ui.dp(7f)),
                        state = state
                    )

                    Box {

                        SkikoButton(
                            width = ui.dp(32f),
                            height = ui.dp(30f),
                            onClick = { showGreek = true }
                        ) {
                            Text("β", fontSize = ui.sp(14f))
                        }

                        GreekDropdown(
                            expanded = showGreek,
                            ui = ui,
                            onDismiss = { showGreek = false },
                            onSymbol = { symbol ->
                                pendingSup = TextFieldValue(symbol)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(ui.dp(7f)))

                SkikoButton(
                    width = ui.dp(110f),
                    height = ui.dp(32f),
                    enabled = canApply,
                    onClick = { apply() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Použít",
                            tint = colors.text,
                            modifier = Modifier.size(ui.dp(17f))
                        )

                        Spacer(Modifier.width(ui.dp(6f)))

                        Text(
                            "Použít",
                            fontSize = ui.sp(13f)
                        )
                    }
                }
            }
        }

        if (isKoto) {
            MongeInspectorPropertyRow(
                label = "Kóta:",
                contentAlign = Alignment.End
            ) {
                MiniInputField2(
                    value = pendingKota,
                    onValueChange = { pendingKota = it },
                    placeholder = "",
                    numericOnly = true,
                    fontSize = ui.sp(16f),
                    width = ui.dp(58f),
                    height = ui.dp(34f),
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (
                            e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.NumPadEnter)
                        ) {
                            apply()
                            true
                        } else false
                    },
                    state = state
                )
            }
        }

        MongeDivider()

        MongeInspectorPropertyRow(
            label = "Barva:",
            contentAlign = Alignment.End
        ) {
            ColorPickerDropdown(
                selectedColor = point.localColor ?: Color.Black,
                onColorPreview = {},
                onColorConfirm = onColorChange
            )
        }

        MongeDivider()

        MongeInspectorPropertyRow(
            label = "Šířka:",
            contentAlign = Alignment.End
        ) {
            WidthEditor(
                value = point.localWidth ?: 1f,
                onValueChange = onWidthChange,
                state = state
            )
        }


        MongeDivider()

        if (isMonge) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SkikoButton(
                    width = ui.dp(100f),
                    height = ui.dp(36f),
                    onClick = { onSelectAddProjection() },
                    enabled = point.parent == null
                ) {
                    Text(
                        "Sdružit",
                        fontSize = ui.sp(13f)
                    )
                }
            }
        }

        Spacer(Modifier.height(ui.dp(4f)))

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
fun EditableParentPointInfo(
    point: Point3D,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onSuperscriptChange: (String) -> Unit,
    onDelete: () -> Unit,
    state: MongeState,
    uiScale: Float,

    showPudorysProjection: Boolean,
    onShowPudorysProjectionChange: (Boolean) -> Unit,
    showNarysProjection: Boolean,
    onShowNarysProjectionChange: (Boolean) -> Unit,
    showBokorysProjection: Boolean,
    onShowBokorysProjectionChange: (Boolean) -> Unit,
    showAxoProjection: Boolean,
    onShowAxoProjectionChange: (Boolean) -> Unit,

    onApply: (newName: String, newSup: String, newKota: String) -> Unit
) {
    val isAxo = state.projectionMode == ProjectionMode.AXO
    val isKoto = state.projectionMode == ProjectionMode.KOTO
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current

    var pendingName by remember(point.id) {
        mutableStateOf(TextFieldValue(point.name.trim()))
    }

    var pendingSup by remember(point.id) {
        mutableStateOf(TextFieldValue(point.superscript.emptyIfNullText()))
    }

    // KOTO: aktuální kóta bodu (uložená jako z*10) – prázdné pole ji odebere.
    val kotaNow = if (isKoto) formatKota(point.z / 10f) else ""
    var pendingKota by remember(point.id) {
        mutableStateOf(TextFieldValue(kotaNow))
    }

    var showGreek by remember { mutableStateOf(false) }

    val canApply =
        pendingName.text != point.name ||
                pendingSup.text != point.superscript.emptyIfNullText() ||
                (isKoto && pendingKota.text.trim() != kotaNow)

    fun apply() {
        if (canApply) {
            onApply(pendingName.text, pendingSup.text, pendingKota.text.trim())
            state.focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        val label = if (state.projectionMode == ProjectionMode.PLANE) "Bod:" else "Bod 3D:"
        MongeInspectorPropertyRow(
            label = label,
            contentAlign = Alignment.End
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (
                        e.type == KeyEventType.KeyDown &&
                        (e.key == Key.Enter || e.key == Key.NumPadEnter)
                    ) {
                        apply()
                        true
                    } else false
                }
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(ui.dp(5f))
                ) {
                    MiniInputField2(
                        value = pendingName,
                        onValueChange = { pendingName = it },
                        placeholder = "Název",
                        numericOnly = false,
                        fontSize = ui.sp(16f),
                        width = ui.dp(58f),
                        height = ui.dp(34f),
                        state = state
                    )

                    MiniInputField2(
                        value = pendingSup,
                        onValueChange = { pendingSup = it },
                        placeholder = "",
                        numericOnly = false,
                        fontSize = ui.sp(13f),
                        width = ui.dp(30f),
                        height = ui.dp(28f),
                        modifier = Modifier.offset(y = -ui.dp(7f)),
                        state = state
                    )

                    Box {
                        SkikoButton(
                            width = ui.dp(32f),
                            height = ui.dp(30f),
                            onClick = { showGreek = true }
                        ) {
                            Text("β", fontSize = ui.sp(14f))
                        }

                        GreekDropdown(
                            expanded = showGreek,
                            ui = ui,
                            onDismiss = { showGreek = false },
                            onSymbol = { symbol ->
                                pendingSup = TextFieldValue(symbol)
                                onSuperscriptChange(symbol)
                                commitSnapshot(state)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(ui.dp(7f)))

                SkikoButton(
                    width = ui.dp(110f),
                    height = ui.dp(32f),
                    enabled = canApply,
                    onClick = { apply() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Použít",
                            tint = colors.text,
                            modifier = Modifier.size(ui.dp(17f))
                        )
                        Spacer(Modifier.width(ui.dp(6f)))
                        Text("Použít", fontSize = ui.sp(13f))
                    }
                }
            }
        }

        if (isKoto) {
            MongeInspectorPropertyRow(
                label = "Kóta:",
                contentAlign = Alignment.End
            ) {
                MiniInputField2(
                    value = pendingKota,
                    onValueChange = { pendingKota = it },
                    placeholder = "",
                    numericOnly = true,
                    fontSize = ui.sp(16f),
                    width = ui.dp(58f),
                    height = ui.dp(34f),
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (
                            e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.NumPadEnter)
                        ) {
                            apply()
                            true
                        } else false
                    },
                    state = state
                )
            }
        }

        MongeDivider()

        val coords = if (state.projectionMode == ProjectionMode.PLANE)
            "[${(point.x / 10f).pretty()}; ${(point.y / 10f).pretty()}]" else "[${(point.x / 10f).pretty()}; ${(point.y / 10f).pretty()}; ${(point.z / 10f).pretty()}]"
        MongeInspectorPropertyRow(
            label = "Souřadnice:",
            contentAlign = Alignment.End
        ) {
            Text(
                text = coords,
                color = colors.text,
                fontSize = ui.sp(13f),
                fontWeight = FontWeight.Medium
            )
        }

        MongeDivider()


        MongeInspectorPropertyRow(
            label = "Barva:",
            contentAlign = Alignment.End
        ) {
            ColorPickerDropdown(
                selectedColor = point.color,
                onColorPreview = {},
                onColorConfirm = onColorChange
            )
        }

        MongeDivider()

        MongeInspectorPropertyRow(
            label = "Šířka:",
            contentAlign = Alignment.End
        ) {
            WidthEditor(
                value = point.width,
                onValueChange = onWidthChange,
                state = state
            )
        }
        if (isAxo){
            MongeDivider()

            MongeInspectorPropertyRow(
                label = "Zobrazení:",
                contentAlign = Alignment.End
            ) {
                ProjectionVisibilityToggleStrip(
                    ui = ui,
                    ProjectionVisibilityToggleItem("A", showAxoProjection, onShowAxoProjectionChange),
                    ProjectionVisibilityToggleItem("P", showPudorysProjection, onShowPudorysProjectionChange),
                    ProjectionVisibilityToggleItem("N", showNarysProjection, onShowNarysProjectionChange),
                    ProjectionVisibilityToggleItem("B", showBokorysProjection, onShowBokorysProjectionChange),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(ui.dp(4f)))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SkikoButton(
                width = ui.dp(100f),
                height = ui.dp(38f),
                onClick = onDelete
            ) {
                Text("Smazat", fontSize = ui.sp(13f))
            }
        }
    }
}

val defaultColorPalette = listOf(
    Color.Black, Color.Red, Color.Blue, Color.Green,
    Color.Yellow, Color.Magenta, Color.Cyan
)


@Composable
fun WidthEditor(
    value: Float,
    onValueChange: (Float) -> Unit,
    state: MongeState,
    step: Float = 0.25f,
    min: Float = 0.25f,
    max: Float = 5f,
    modifier: Modifier = Modifier
) {
    val colors = LocalMongeColors.current
    val uiS = SettingsManager.current.UIscale/75f
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(min, max)
        val stepsFromMin = ((clamped - min) / step).roundToInt()
        return (min + stepsFromMin * step).let { (it * 1000f).roundToInt() / 1000f }
    }

    // lokální „UI“ hodnota pro plynulé tažení
    var ui by remember { mutableStateOf(value.coerceIn(min, max)) }
    LaunchedEffect(value) { ui = value.coerceIn(min, max) }
    // true, pokud během aktuálního gesta došlo ke skutečné změně -> commit až na konci
    var dirty by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8f*uiS.dp)
    ) {

        MongeStepSlider(
            value = ui,
            onValueChange = {
                if (it != ui) {
                    ui = it
                    onValueChange(it)          // živá změna modelu
                    state.triggerRedraw++      // okamžité překreslení výkresu
                    dirty = true
                }
            },
            onValueChangeFinished = {
                if (dirty) {
                    commitSnapshot(state)      // commit až po dokončení změny
                    state.triggerRedraw++
                    dirty = false
                }
            },
            min = min,
            max = max,
            step = step,
            modifier = Modifier
                .weight(0.85f)
                .padding(end = 4*uiS.dp)
        )
        // číslo může být klidně zaokrouhlené na krok
        Text(
            text = "%.2f".format(snap(ui)),
            color = colors.text,
            modifier = Modifier.width(42f * uiS.dp),
            textAlign = TextAlign.End,
            fontSize = 14f * uiS.sp
        )

    }
}
@Composable
fun MongeStepSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float,
    max: Float,
    step: Float,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {}
) {
    val colors = LocalMongeColors.current
    val uiS = SettingsManager.current.UIscale / 75f

    fun snap(v: Float): Float {
        val clamped = v.coerceIn(min, max)
        val steps = ((clamped - min) / step).roundToInt()
        return min + steps * step
    }

    fun valueToT(v: Float): Float =
        ((v - min) / (max - min)).coerceIn(0f, 1f)

    fun tToValue(t: Float): Float =
        snap(min + t.coerceIn(0f, 1f) * (max - min))

    var dragging by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .height(28f * uiS.dp)
            .pointerInput(value, min, max, step) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val scroll = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f

                        if (scroll != 0f) {
                            val dir = if (scroll > 0f) -1f else 1f
                            onValueChange(snap(value + dir * step))
                            onValueChangeFinished()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(min, max, step) {
                detectTapGestures { offset ->
                    val t = offset.x / size.width
                    onValueChange(tToValue(t))
                    onValueChangeFinished()
                }
            }
            .pointerInput(min, max, step) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val t = offset.x / size.width
                        onValueChange(tToValue(t))
                        dragging = true
                    },
                    onDragEnd = { dragging = false; onValueChangeFinished() },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        val t = change.position.x / size.width
                        onValueChange(tToValue(t))
                        change.consume()
                    }
                )
            }
    ) {
        val centerY = size.height / 2f
        val trackStart = 6f * uiS
        val trackEnd = size.width - 6f * uiS
        val trackWidth = trackEnd - trackStart

        val currentT = valueToT(value)
        val thumbX = trackStart + currentT * trackWidth

        val tickCount = ((max - min) / step).roundToInt()

        drawLine(
            color = colors.base.copy(alpha = 0.45f),
            start = Offset(trackStart, centerY),
            end = Offset(trackEnd, centerY),
            strokeWidth = 3f * uiS,
            cap = StrokeCap.Round
        )

        drawLine(
            color = colors.selected,
            start = Offset(trackStart, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = 3f * uiS,
            cap = StrokeCap.Round
        )

        for (i in 0..tickCount) {
            val x = trackStart + i / tickCount.toFloat() * trackWidth
            val major = i % 2 == 0

            drawLine(
                color = colors.text.copy(alpha = if (major) 0.55f else 0.25f),
                start = Offset(x, centerY - if (major) 5f * uiS else 3f * uiS),
                end = Offset(x, centerY + if (major) 5f * uiS else 3f * uiS),
                strokeWidth = 1f * uiS
            )
        }

        drawRoundRect(
            color = colors.selected,
            topLeft = Offset(thumbX - 4f * uiS, centerY - 9f * uiS),
            size = Size(8f * uiS, 18f * uiS),
            cornerRadius = CornerRadius(2f * uiS, 2f * uiS)
        )

        if (dragging) {
            drawRoundRect(
                color = colors.selected.copy(alpha = 0.18f),
                topLeft = Offset(thumbX - 8f * uiS, centerY - 13f * uiS),
                size = Size(16f * uiS, 26f * uiS),
                cornerRadius = CornerRadius(4f * uiS, 4f * uiS)
            )
        }
    }
}
enum class SupTarget { UPPER, LOWER }
@Composable
fun EditableAidPointInfo(
    point: AidPointLogical,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onDelete: () -> Unit,
    onApply: (name: String, upper: String, lower: String) -> Unit,
    state: MongeState,
    uiScale: Float,
) {
    var pendingColor by remember(point.id) { mutableStateOf(point.color) }
    val colors = LocalMongeColors.current
    val labelWidth = 60*uiScale.dp
    val enabled = point.id != "origin"
    val ui = remember(uiScale) { UiScale(uiScale) }
    val nameNow  = (point.name ?: "").trim()
    val upperNow = point.upperSuperscript.emptyIfNullText()
    val lowerNow = point.lowerSuperscript.emptyIfNullText()

    var pendingName  by remember(point.id) { mutableStateOf(TextFieldValue(nameNow)) }
    var pendingUpper by remember(point.id) { mutableStateOf(TextFieldValue(upperNow)) }
    var pendingLower by remember(point.id) { mutableStateOf(TextFieldValue(lowerNow)) }

    var lastName  by remember(point.id) { mutableStateOf(nameNow) }
    var lastUpper by remember(point.id) { mutableStateOf(upperNow) }
    var lastLower by remember(point.id) { mutableStateOf(lowerNow) }
    var showGreek by remember { mutableStateOf(false) }
    var upperFocused by remember { mutableStateOf(false) }
    var lowerFocused by remember { mutableStateOf(false) }

    var greekTarget by remember(point.id) { mutableStateOf(SupTarget.LOWER) }

    LaunchedEffect(point.id, point.name, point.upperSuperscript, point.lowerSuperscript) {
        val n = (point.name ?: "").trim()
        val u = point.upperSuperscript.emptyIfNullText()
        val l = point.lowerSuperscript.emptyIfNullText()
        lastName = n; lastUpper = u; lastLower = l
        pendingName  = TextFieldValue(n)
        pendingUpper = TextFieldValue(u)
        pendingLower = TextFieldValue(l)
    }

    val canApply =
        pendingName.text.trim()  != lastName ||
                pendingUpper.text.trim() != lastUpper ||
                pendingLower.text.trim() != lastLower

    fun apply() {
        if (!enabled || !canApply) return
        val n = pendingName.text.trim()
        val u = pendingUpper.text.trim()
        val l = pendingLower.text.trim()
        onApply(n, u, l)
        lastName = n; lastUpper = u; lastLower = l
        state.focusRequester.requestFocus()
    }

    val enterHandler = Modifier.onPreviewKeyEvent { e ->
        if (
            e.type == KeyEventType.KeyDown &&
            (e.key == Key.Enter || e.key == Key.NumPadEnter)
        ) {
            apply()
            true
        } else false
    }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {

        /* --- Název + superscripty --- */
        LabeledRow("Bod:", labelWidth=labelWidth) {
            Column(Modifier.fillMaxWidth()) {

                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = enterHandler
                ) {
                    MiniInputField2(
                        value = pendingName,
                        onValueChange = { pendingName = it },
                        placeholder = "",
                        numericOnly = false,
                        fontSize = 16*uiScale.sp,
                        width = 60*uiScale.dp,
                        height = 32*uiScale.dp,
                        enabled = enabled,
                        state=state
                    )

                    Spacer(Modifier.width(6*uiScale.dp))

                    val upperHighlight = upperFocused || (showGreek && greekTarget == SupTarget.UPPER)
                    val lowerHighlight = lowerFocused || (showGreek && greekTarget == SupTarget.LOWER)

                    Column {
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.2*uiScale.dp,
                                    color = if (upperHighlight) colors.selected else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(1*uiScale.dp)
                        ) {
                            MiniInputField2(
                                value = pendingUpper,
                                onValueChange = { pendingUpper = it },
                                placeholder = "",
                                numericOnly = false,
                                fontSize = 10*uiScale.sp,
                                width = 20*uiScale.dp,
                                height = 20*uiScale.dp,
                                enabled = enabled,
                                modifier = Modifier.onFocusChanged {
                                    upperFocused = it.isFocused
                                    if (it.isFocused) greekTarget = SupTarget.UPPER
                                },
                                state=state
                            )
                        }

                        Spacer(Modifier.height(2*uiScale.dp))

                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.2*uiScale.dp,
                                    color = if (lowerHighlight) colors.selected else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(1*uiScale.dp)
                        ) {
                            MiniInputField2(
                                value = pendingLower,
                                onValueChange = { pendingLower = it },
                                placeholder = "",
                                numericOnly = false,
                                fontSize = 10*uiScale.sp,
                                width = 20*uiScale.dp,
                                height = 20*uiScale.dp,
                                enabled = enabled,
                                modifier = Modifier.onFocusChanged {
                                    lowerFocused = it.isFocused
                                    if (it.isFocused) greekTarget = SupTarget.LOWER
                                },
                                state=state
                            )
                        }
                    }
                    Spacer(Modifier.width(6*uiScale.dp))

                    SkikoButton(
                        width = 30*uiScale.dp,
                        height = 30*uiScale.dp,
                        enabled = enabled,
                        onClick = { showGreek = true }
                    ) { Text("β") }
                }

                GreekDropdown(
                    expanded = showGreek,
                    onDismiss = { showGreek = false },
                    onSymbol = { symbol ->
                        when (greekTarget) {
                            SupTarget.UPPER -> pendingUpper = TextFieldValue(symbol)
                            SupTarget.LOWER -> pendingLower = TextFieldValue(symbol)
                        }
                    },
                    ui = ui
                )

                Spacer(Modifier.height(6*uiScale.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    SkikoButton(
                        width = 110*uiScale.dp,
                        height = 32*uiScale.dp,
                        enabled = enabled && canApply,
                        onClick = { apply() }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Použít", tint = colors.text, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Použít")
                    }
                }
            }
        }

        MongeDivider()


            MongeInspectorPropertyRow("Barva:") {
                ColorPickerDropdown(
                    selectedColor = pendingColor,
                    onColorPreview = { pendingColor = it },
                    onColorConfirm = onColorChange
                )
            }

            MongeDivider()

            MongeInspectorPropertyRow("Šířka:") {
                WidthEditor(
                    value = point.width,
                    onValueChange = onWidthChange,
                    state = state
                )
            }

        Spacer(Modifier.height(12*uiScale.dp))

        if (enabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SkikoButton(
                    width = 120*uiScale.dp,
                    height = 32*uiScale.dp,
                    onClick = onDelete
                ) { Text("Smazat bod") }
            }
        }
    }
}
@Composable
fun EditableAOPointInfo(
    point: AxoOverlayPoint,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onDelete: () -> Unit,
    onApply: (name: String, upper: String, lower: String) -> Unit,
    state: MongeState,
    uiScale: Float
) {
    var pendingColor by remember(point.id) { mutableStateOf(point.color) }
    val colors = LocalMongeColors.current
    val labelWidth = 60*uiScale.dp
    val enabled = point.id != "origin"

    val nameNow  = (point.name ?: "").trim()
    val upperNow = point.upper.emptyIfNullText()
    val lowerNow = point.lower.emptyIfNullText()
    val ui = remember(uiScale) { UiScale(uiScale) }

    var pendingName  by remember(point.id) { mutableStateOf(TextFieldValue(nameNow)) }
    var pendingUpper by remember(point.id) { mutableStateOf(TextFieldValue(upperNow)) }
    var pendingLower by remember(point.id) { mutableStateOf(TextFieldValue(lowerNow)) }

    var lastName  by remember(point.id) { mutableStateOf(nameNow) }
    var lastUpper by remember(point.id) { mutableStateOf(upperNow) }
    var lastLower by remember(point.id) { mutableStateOf(lowerNow) }
    var showGreek by remember { mutableStateOf(false) }
    var upperFocused by remember { mutableStateOf(false) }
    var lowerFocused by remember { mutableStateOf(false) }

    var greekTarget by remember(point.id) { mutableStateOf(SupTarget.LOWER) }

    LaunchedEffect(point.id, point.name, point.upper, point.lower) {
        val n = (point.name ?: "").trim()
        val u = point.upper.emptyIfNullText()
        val l = point.lower.emptyIfNullText()
        lastName = n; lastUpper = u; lastLower = l
        pendingName  = TextFieldValue(n)
        pendingUpper = TextFieldValue(u)
        pendingLower = TextFieldValue(l)
    }

    val canApply =
        pendingName.text.trim()  != lastName ||
                pendingUpper.text.trim() != lastUpper ||
                pendingLower.text.trim() != lastLower

    fun apply() {
        if (!enabled || !canApply) return
        val n = pendingName.text.trim()
        val u = pendingUpper.text.trim()
        val l = pendingLower.text.trim()
        onApply(n, u, l)
        lastName = n; lastUpper = u; lastLower = l
        state.focusRequester.requestFocus()
    }

    val enterHandler = Modifier.onPreviewKeyEvent { e ->
        if (
            e.type == KeyEventType.KeyDown &&
            (e.key == Key.Enter || e.key == Key.NumPadEnter)
        ) {
            apply()
            true
        } else false
    }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {

        /* --- Název + superscripty --- */
        LabeledRow("Bod:", labelWidth=labelWidth) {
            Column(Modifier.fillMaxWidth()) {

                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = enterHandler
                ) {
                    MiniInputField2(
                        value = pendingName,
                        onValueChange = { pendingName = it },
                        placeholder = "",
                        numericOnly = false,
                        fontSize = 16*uiScale.sp,
                        width = 60*uiScale.dp,
                        height = 32*uiScale.dp,
                        enabled = enabled,
                        state=state
                    )

                    Spacer(Modifier.width(6*uiScale.dp))

                    val upperHighlight = upperFocused || (showGreek && greekTarget == SupTarget.UPPER)
                    val lowerHighlight = lowerFocused || (showGreek && greekTarget == SupTarget.LOWER)

                    Column {
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.2*uiScale.dp,
                                    color = if (upperHighlight) colors.selected else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(1*uiScale.dp)
                        ) {
                            MiniInputField2(
                                value = pendingUpper,
                                onValueChange = { pendingUpper = it },
                                placeholder = "",
                                numericOnly = false,
                                fontSize = 10*uiScale.sp,
                                width = 20*uiScale.dp,
                                height = 20*uiScale.dp,
                                enabled = enabled,
                                modifier = Modifier.onFocusChanged {
                                    upperFocused = it.isFocused
                                    if (it.isFocused) greekTarget = SupTarget.UPPER
                                },
                                state=state
                            )
                        }

                        Spacer(Modifier.height(2*uiScale.dp))

                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.2*uiScale.dp,
                                    color = if (lowerHighlight) colors.selected else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(1*uiScale.dp)
                        ) {
                            MiniInputField2(
                                value = pendingLower,
                                onValueChange = { pendingLower = it },
                                placeholder = "",
                                numericOnly = false,
                                fontSize = 10*uiScale.sp,
                                width = 20*uiScale.dp,
                                height = 20*uiScale.dp,
                                enabled = enabled,
                                modifier = Modifier.onFocusChanged {
                                    lowerFocused = it.isFocused
                                    if (it.isFocused) greekTarget = SupTarget.LOWER
                                },
                                state=state
                            )
                        }
                    }
                    Spacer(Modifier.width(6*uiScale.dp))

                    SkikoButton(
                        width = 30*uiScale.dp,
                        height = 30*uiScale.dp,
                        enabled = enabled,
                        onClick = { showGreek = true }
                    ) { Text("β") }
                }

                GreekDropdown(
                    expanded = showGreek,
                    onDismiss = { showGreek = false },
                    onSymbol = { symbol ->
                        when (greekTarget) {
                            SupTarget.UPPER -> pendingUpper = TextFieldValue(symbol)
                            SupTarget.LOWER -> pendingLower = TextFieldValue(symbol)
                        }
                    },
                    ui = ui
                )

                Spacer(Modifier.height(6*uiScale.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    SkikoButton(
                        width = 110*uiScale.dp,
                        height = 32*uiScale.dp,
                        enabled = enabled && canApply,
                        onClick = { apply() }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Použít", tint = colors.text, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6*uiScale.dp))
                        Text("Použít")
                    }
                }
            }
        }

        MongeDivider()

        MongeInspectorSection("Vlastnosti") {
            MongeInspectorPropertyRow("Barva:") {
                ColorPickerDropdown(
                    selectedColor = pendingColor,
                    onColorPreview = { pendingColor = it },
                    onColorConfirm = onColorChange
                )
            }

            MongeDivider()

            MongeInspectorPropertyRow("Šířka:") {
                WidthEditor(
                    value = point.width,
                    onValueChange = onWidthChange,
                    state = state
                )
            }
        }

        Spacer(Modifier.height(12*uiScale.dp))

        if (enabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SkikoButton(
                    width = 120*uiScale.dp,
                    height = 32*uiScale.dp,
                    onClick = onDelete
                ) { Text("Smazat bod") }
            }
        }
    }
}
fun pointDelete3D(state: MongeState, pt: Point3D)
{


    val old = pt

    // 1) Najdi projekce (kopie seznamu = žádný ConcurrentModification)
    val pudorysy = state.pointsPudorys.filter { it.parent === old }
    val narysy   = state.pointsNarys  .filter { it.parent === old }
    val axo = state.pointsAxo.filter { it.parent === old }
    val bokorysy = state.pointsBokorys.filter { it.parent === old }

    // 2) Zruš label offsety a výběry projekcí
    pudorysy.forEach { p ->
        state.labelOffsetsPointsPudorys.remove(p.id)
        state.selectedPointsPudorys.remove(p)
    }
    narysy.forEach { p ->
        state.labelOffsetsPointsNarys.remove(p.id)
        state.selectedPointsNarys.remove(p)
    }
    axo.forEach { p ->
        state.labelOffsetsPointsAxo.remove(p.id)
        state.selectedPointsAxo.remove(p)
    }
    bokorysy.forEach {p ->
        state.labelOffsetsPointsBokorys.remove(p.id)
        state.selectedPointsBokorys.remove(p)
    }

    // 3) Smaž projekce ze seznamů
    state.pointsPudorys.removeAll(pudorysy.toSet())
    state.pointsNarys  .removeAll(narysy.toSet())
    state.pointsAxo.removeAll(axo.toSet())
    state.pointsBokorys.removeAll(bokorysy.toSet())

    // 4) Smaž 3D parenta
    state.sharedPoints3D.removeAll { it.id == old.id }

    // 5) Uklid stavy, které by na něj mohly ukazovat
    if (state.rename.pointBeingRenamed === old) state.rename.pointBeingRenamed = null
    commitSnapshot(state)
    state.triggerRedraw++
}
fun pointDelete2D(state: MongeState,pt: Point2DProjection) {
    projectedLineOfPoint(state, pt)?.let { parentLine ->
        deleteLine3D(state, parentLine)
        return
    }


    when (pt) {
        is Point3DPudorys -> {
            // zruš label posun a výběr
            state.labelOffsetsPointsPudorys.remove(pt.id)
            state.selectedPointsPudorys.remove(pt)
            // smaž bod
            state.pointsPudorys.removeAll { it.id == pt.id }
        }
        is Point3DNarys -> {
            state.labelOffsetsPointsNarys.remove(pt.id)
            state.selectedPointsNarys.remove(pt)
            state.pointsNarys.removeAll { it.id == pt.id }
        }
        is Point3DBokorys ->{
            state.labelOffsetsPointsBokorys.remove(pt.id)
            state.selectedPointsBokorys.remove(pt)
            state.pointsBokorys.removeAll { it.id == pt.id }
        }
        is Point3DAxo ->{
            state.labelOffsetsPointsAxo.remove(pt.id)
            state.selectedPointsAxo.remove(pt)
            state.pointsAxo.removeAll { it.id == pt.id }

        }
    }

    // pokud by zrovna probíhalo přejmenování tohoto bodu, ukonči ho
    if (state.rename.pointBeingRenamed === pt) state.rename.pointBeingRenamed = null
    commitSnapshot(state)
    state.triggerRedraw++
}
@Composable
fun aidPointEdit (state: MongeState){
    val selectedAidPointId = state.selectedAidPointIds.firstOrNull()
    val selectedAidPoint = selectedAidPointId?.let { id ->
        state.aidPointsLogical.find { it.id == id }
    }
    selectedAidPoint?.let { ap ->
        key(ap.id) {
            EditableAidPointInfo(
                point = ap,
                onColorChange = { newColor ->
                    val idx = state.aidPointsLogical.indexOfFirst { it.id == ap.id }
                    if (idx != -1) state.aidPointsLogical[idx] =
                        ap.copy(color = newColor)
                    commitSnapshot(state)

                },
                onWidthChange = { newWidth ->
                    val idx = state.aidPointsLogical.indexOfFirst { it.id == ap.id }
                    if (idx != -1) {
                        state.aidPointsLogical[idx] = state.aidPointsLogical[idx].copy(width = newWidth)
                        state.triggerRedraw++
                    }
                },
                onDelete = {
                    monge.input.segments.removePlanePolygonsContainingAidPoints(state, setOf(ap.id))
                    state.aidPointsLogical.removeAll { it.id == ap.id }
                    state.selectedAidPointIds.remove(ap.id)
                    clearSelection(state)
                    commitSnapshot(state)

                },
                onApply = { name, upper, lower ->
                    // najdi bod podle id a přepiš ho (copy/replaceAll podle toho, jak to držíš)
                    state.aidPointsLogical.replaceAll { p ->
                        if (p.id == ap.id) p.copy(
                            name = name.ifEmpty { null },
                            upperSuperscript = upper.ifEmpty { null },
                            lowerSuperscript = lower.ifEmpty { null }
                        ) else p
                    }
                    state.triggerRedraw++
                },
                state=state,
                uiScale = SettingsManager.current.UIscale/75f
            )
        }
    }
}
@Composable
fun aOPointEdit (state: MongeState){
    val selectedAidPointId = state.selectedAOPointIds.firstOrNull()
    val selectedAidPoint = selectedAidPointId?.let { id ->
        state.axoOverlayPoints.find { it.id == id }
    }
    selectedAidPoint?.let { ap ->
        key(ap.id) {
            EditableAOPointInfo(
                point = ap,
                onColorChange = { newColor ->
                    val idx = state.axoOverlayPoints.indexOfFirst { it.id == ap.id }
                    if (idx != -1) state.axoOverlayPoints[idx] =
                        ap.copy(color = newColor)
                    commitSnapshot(state)

                },
                onWidthChange = { newWidth ->
                    val idx = state.axoOverlayPoints.indexOfFirst { it.id == ap.id }
                    if (idx != -1) {
                        state.axoOverlayPoints[idx] = state.axoOverlayPoints[idx].copy(width = newWidth)
                        state.triggerRedraw++
                    }
                },
                onDelete = {
                    state.axoOverlayPoints.removeAll { it.id == ap.id }
                    state.selectedAOPointIds.remove(ap.id)
                    clearSelection(state)
                    commitSnapshot(state)

                },
                onApply = { name, upper, lower ->
                    // najdi bod podle id a přepiš ho (copy/replaceAll podle toho, jak to držíš)
                    state.axoOverlayPoints.replaceAll { p ->
                        if (p.id == ap.id) p.copy(
                            name = name.ifEmpty { null },
                            upper = upper.ifEmpty { null },
                            lower = lower.ifEmpty { null }
                        ) else p
                    }
                    state.triggerRedraw++
                },
                state=state,
                uiScale = SettingsManager.current.UIscale/75f
            )
        }
    }
}
@Composable
fun pointEdit (state: MongeState){
    val selectedPointRaw = state.selectedPointsPudorys.firstOrNull()
        ?: state.selectedPointsNarys.firstOrNull() ?: state.selectedPointsBokorys.firstOrNull() ?: state.selectedPointsAxo.firstOrNull()

    val currentPoint = when (selectedPointRaw) {
        is Point3DPudorys -> state.pointsPudorys.find { it.id == selectedPointRaw.id }
        is Point3DNarys -> state.pointsNarys.find { it.id == selectedPointRaw.id }
        is Point3DBokorys -> state.pointsBokorys.find { it.id == selectedPointRaw.id }
        is Point3DAxo -> state.pointsAxo.find { it.id == selectedPointRaw.id }
        else -> null
    }
    val lineEdit = projectedLineIdOf(selectedPointRaw)

    if (lineEdit != null) {
        val lineAlsoSelected =
            state.selectedLinesPudorys.any { it is Line2DProjection && it.parent?.id == lineEdit } ||
            state.selectedLinesNarys.any { it is Line2DProjection && it.parent?.id == lineEdit } ||
            state.selectedLinesBokorys.any { it is Line2DProjection && it.parent?.id == lineEdit } ||
            state.selectedLinesAxo.any { it is Line2DProjection && it.parent?.id == lineEdit }
        if (lineAlsoSelected) return
    }

    val pointToEdit = if (currentPoint?.parent != null) null else currentPoint

    val point3D = currentPoint?.parent

    point3D?.let { pt ->
        key(pt) {
            val pud = state.pointsPudorys.find { it.parent?.id == pt.id }
            val nar = state.pointsNarys.find { it.parent?.id == pt.id }
            val bok = state.pointsBokorys.find { it.parent?.id == pt.id }
            val axo = state.pointsAxo.find { it.parent?.id == pt.id }
            EditableParentPointInfo(
                point = pt,
                onSuperscriptChange = { newSup ->

                    val cleaned = newSup

                    // Najdi původního parenta
                    val old = pt
                    val updated = old.copy(superscript = cleaned)

                    // Nahraď v sharedPoints3D
                    val idx = state.sharedPoints3D.indexOfFirst { it.id == old.id }
                    if (idx != -1) {
                        state.sharedPoints3D[idx] = updated
                    }

                    // Přejmenuj průměty a přiřaď jim nového parenta
                    state.pointsPudorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsPudorys[i] = state.pointsPudorys[i].copy(
                            localSuperscript = cleaned,
                            parent = updated
                        ).withAxoVisibilityFrom(state.pointsPudorys[i])
                    }
                    state.pointsNarys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsNarys[i] = state.pointsNarys[i].copy(
                            localSuperscript = cleaned,
                            parent = updated
                        ).withAxoVisibilityFrom(state.pointsNarys[i])
                    }
                    state.pointsBokorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsBokorys[i] = state.pointsBokorys[i].copy(
                            localSuperscript = cleaned,
                            parent = updated
                        ).withAxoVisibilityFrom(state.pointsBokorys[i])
                    }
                    state.pointsAxo.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsAxo[i] = state.pointsAxo[i].copy(
                            localSuperscript = cleaned,
                            parent = updated
                        ).withAxoVisibilityFrom(state.pointsAxo[i])
                    }
                    state.triggerRedraw++
                },
                onColorChange = { newColor ->

                    val old = pt
                    val updated = old.copy(color = newColor)

                    // Nahraď v sharedPoints3D
                    val idx = state.sharedPoints3D.indexOfFirst { it.id == old.id }
                    if (idx != -1) {
                        state.sharedPoints3D[idx] = updated
                    }

                    // Aktualizuj projekce s novým parentem
                    state.pointsPudorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsPudorys[i] = state.pointsPudorys[i].copy(parent = updated)
                            .withAxoVisibilityFrom(state.pointsPudorys[i])
                    }
                    state.pointsNarys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsNarys[i] = state.pointsNarys[i].copy(parent = updated)
                            .withAxoVisibilityFrom(state.pointsNarys[i])
                    }
                    state.pointsBokorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsBokorys[i] = state.pointsBokorys[i].copy(parent = updated)
                            .withAxoVisibilityFrom(state.pointsBokorys[i])
                    }
                    state.pointsAxo.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsAxo[i] = state.pointsAxo[i].copy(parent = updated)
                            .withAxoVisibilityFrom(state.pointsAxo[i])
                    }
                    commitSnapshot(state)

                    state.triggerRedraw++
                },
                onWidthChange = { pt.width = it },
                onDelete = {
                    pointDelete3D(state, pt)
                    clearSelection(state)
                },
                state = state,
                onApply = { newName, newSup, newKota ->

                    val cleanedName = newName.removeSuffix("₁").removeSuffix("₂")
                    val cleanedSup = newSup

                    // vezmi aktuální parent podle ID (ne podle reference)
                    val oldIdx = state.sharedPoints3D.indexOfFirst { it.id == pt.id }
                    if (oldIdx == -1) return@EditableParentPointInfo
                    val old = state.sharedPoints3D[oldIdx]

                    // KOTO: prázdná kóta = odeber ji a převeď zpět na samostatný půdorysný bod
                    if (state.projectionMode == ProjectionMode.KOTO && newKota.isBlank()) {
                        kotoRemoveKota(state, old, cleanedName, cleanedSup)
                        commitSnapshot(state)
                        clearSelection(state)
                        state.triggerRedraw++
                        return@EditableParentPointInfo
                    }

                    // KOTO: případná změna kóty se propíše do z parenta i nárysu
                    val newZ = if (state.projectionMode == ProjectionMode.KOTO)
                        (parseKota(newKota) ?: old.z) else old.z

                    val updated = old.copy(name = cleanedName, superscript = cleanedSup, z = newZ)
                    state.sharedPoints3D[oldIdx] = updated

                    // projekce najdi podle parent.id (ne ===)
                    state.pointsPudorys.indexOfFirst { it.parent?.id == updated.id }.takeIf { it != -1 }?.let { i ->
                        state.pointsPudorys[i] = state.pointsPudorys[i].copy(
                            name = cleanedName.withSuffixOnce("₁"),
                            localSuperscript = cleanedSup,
                            parent = updated
                        ).withAxoVisibilityFrom(state.pointsPudorys[i])
                    }
                    state.pointsNarys.indexOfFirst { it.parent?.id == updated.id }.takeIf { it != -1 }?.let { i ->
                        state.pointsNarys[i] = state.pointsNarys[i].copy(
                            name = cleanedName.withSuffixOnce("₂"),
                            localSuperscript = cleanedSup,
                            parent = updated,
                            z = newZ
                        ).withAxoVisibilityFrom(state.pointsNarys[i])
                    }
                    state.pointsBokorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsBokorys[i] = state.pointsBokorys[i].copy(
                            name = cleanedName.withSuffixOnce("\u2083"),
                            localSuperscript = cleanedSup,
                            parent = updated
                        ).withAxoVisibilityFrom(state.pointsBokorys[i])
                    }
                    state.pointsAxo.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.pointsAxo[i] = state.pointsAxo[i].copy(
                            name = cleanedName.withSuffixOnce("ₐ"),
                            localSuperscript = cleanedSup,
                            parent = updated
                        ).withAxoVisibilityFrom(state.pointsAxo[i])
                    }
                    commitSnapshot(state)

                    state.triggerRedraw++
                },
                uiScale = SettingsManager.current.UIscale / 75f,
                showPudorysProjection = pud?.showInAxo ?: false,
                onShowPudorysProjectionChange = { checked ->
                    if (pud == null) {
                        if (checked) {
                            state.pointsPudorys.add(
                                Point3DPudorys(
                                    x = pt.x,
                                    y = pt.y,
                                    name = pt.name,
                                    parent = pt,
                                    localColor = pt.color,
                                    showInAxoInitial = true
                                )
                            )
                            commitSnapshot(state)
                        }
                    } else {
                        pud.showInAxoInitial = checked
                        pud.showInAxo = checked
                        commitSnapshot(state)
                    }
                },
                showNarysProjection = nar?.showInAxo ?: false,
                onShowNarysProjectionChange = { checked ->
                    if (nar == null) {
                        if (checked) {
                            state.pointsNarys.add(
                                Point3DNarys(
                                    x = pt.x,
                                    z = pt.z,
                                    name = pt.name,
                                    parent = pt,
                                    localColor = pt.color,
                                    showInAxoInitial = true
                                )
                            )
                            commitSnapshot(state)
                        }
                    } else {
                        nar.showInAxoInitial = checked
                        nar.showInAxo = checked
                        commitSnapshot(state)
                    }
                },
                showBokorysProjection = bok?.showInAxo?:false,
                onShowBokorysProjectionChange = { checked ->
                    if (bok == null) {
                        if (checked) {
                            state.pointsBokorys.add(
                                Point3DBokorys(
                                    y = pt.y,
                                    z = pt.z,
                                    name = pt.name,
                                    parent = pt,
                                    localColor = pt.color,
                                    showInAxoInitial = true
                                )
                            )
                            commitSnapshot(state)
                        }
                    } else {
                        bok.showInAxoInitial = checked
                        bok.showInAxo = checked
                        commitSnapshot(state)
                    }
                },
                showAxoProjection = axo?.showInAxo ?:false,
                onShowAxoProjectionChange = { checked ->
                    // Vytvoření axo průmětu bodu – web axonometrii nekreslí.
                }
            )
        }
    }

    pointToEdit?.let { pt ->
        key(pt.id) {
            if (lineEdit == null) {
            EditablePointInfo(
                point = pt,
                onApply = { newName, newSup, newKota ->
                    val base = newName.removeSuffix("₁").removeSuffix("₂").trim()
                    val sup  = newSup.trim()
                    val kotaZ = if (state.projectionMode == ProjectionMode.KOTO) parseKota(newKota) else null
                    if (kotaZ != null && pt is Point3DPudorys) {
                        // KOTO: doplnění kóty → vytvoř 3D bod + nárys
                        kotoAddKota(state, pt, base, sup, kotaZ)
                    } else {
                        val updated = when (pt) {
                            is Point3DPudorys -> pt.copy(name = base, localSuperscript = sup).withAxoVisibilityFrom(pt)
                            is Point3DNarys -> pt.copy(name = base, localSuperscript = sup).withAxoVisibilityFrom(pt)
                            is Point3DBokorys -> pt.copy(name = base, localSuperscript = sup).withAxoVisibilityFrom(pt)
                            is Point3DAxo -> pt.copy(name = base, localSuperscript = sup).withAxoVisibilityFrom(pt)
                        }
                        replacePointInStateLists(state, updated)
                    }
                    commitSnapshot(state)
                    },
                onWidthChange = { newWidth ->
                    val updated = when (pt) {
                        is Point3DPudorys -> pt.copy(localWidth = newWidth).withAxoVisibilityFrom(pt)
                        is Point3DNarys -> pt.copy(localWidth = newWidth).withAxoVisibilityFrom(pt)
                        is Point3DBokorys -> pt.copy(localWidth = newWidth).withAxoVisibilityFrom(pt)
                        is Point3DAxo -> pt.copy(localWidth = newWidth).withAxoVisibilityFrom(pt)
                    }
                    replacePointInStateLists(state, updated)
                    state.triggerRedraw++
                },
                onColorChange = { newColor ->
                    when (pt) {
                        is Point3DPudorys -> {
                            val index = state.pointsPudorys.indexOfFirst { it.id == pt.id }
                            if (index != -1) {
                                val upd = pt.copy(localColor = newColor, id = pt.id).withAxoVisibilityFrom(pt)
                                state.pointsPudorys[index] = upd

                            }
                        }
                        is Point3DNarys -> {
                            val index = state.pointsNarys.indexOfFirst { it.id == pt.id }
                            if (index != -1) {
                                val upd = pt.copy(localColor = newColor, id = pt.id).withAxoVisibilityFrom(pt)
                                state.pointsNarys[index] = upd

                            }
                        }
                        is Point3DBokorys -> {
                            val index = state.pointsBokorys.indexOfFirst { it.id == pt.id }
                            if (index != -1) {
                                val upd = pt.copy(localColor = newColor, id = pt.id).withAxoVisibilityFrom(pt)
                                state.pointsBokorys[index] = upd

                            }
                        }
                        is Point3DAxo -> {
                            val index = state.pointsAxo.indexOfFirst { it.id == pt.id }
                            if (index != -1) {
                                val upd = pt.copy(localColor = newColor, id = pt.id).withAxoVisibilityFrom(pt)
                                state.pointsAxo[index] = upd

                            }
                        }
                    }
                    commitSnapshot(state)
                },
                onSelectAddProjection = { startPoint3DCompletion(state) },
                onAddProjection = {
                    when (state.projectionMode){
                        ProjectionMode.MONGE -> {
                            state.drawobjects = Mongeobjects.NONE

                            state.mongeMode =
                                if (pt is Point3DPudorys) DrawingModeMonge.NARYS else DrawingModeMonge.PUDORYS
                            CompletePointAdd(state)
                        }
                        ProjectionMode.AXO -> {
                            state.drawobjects = Mongeobjects.NONE
                            when (pt){
                                is Point3DPudorys -> {Unit}
                                    is Point3DBokorys->{ Unit}
                                    is Point3DNarys->{Unit}
                                    is Point3DAxo ->{Unit}
                            }
                        }
                        else -> {}
                    }
                },
                onDelete = {
                    pointDelete2D(state, pt)
                    clearSelection(state)
                },
                state = state,
                uiScale = SettingsManager.current.UIscale/75f
            )}
            else{
                val parentLine = state.lines3D.firstOrNull(){it.id == lineEdit}
                if (parentLine == null) return
                    val isX12 = parentLine.id == "X12_ID" || isAxis(parentLine)
                    val pProj = state.lines3DPudorys.firstOrNull { it.parent?.id == parentLine.id }
                    val nProj = state.lines3DNarys.firstOrNull { it.parent?.id == parentLine.id }
                    key(parentLine) {

                        EditableParentLineInfo(
                            line = parentLine,
                            canRename = !isX12,
                            onColorChange = { newColor ->

                                val updated = parentLine.copy(color = newColor)

                                // Nahraď v sharedLines3D
                                val idx = state.lines3D.indexOfFirst { it.id == parentLine.id }
                                if (idx != -1) {
                                    state.lines3D[idx] = updated
                                }

                                // Aktualizuj projekce s novým parentem
                                state.lines3DPudorys.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }
                                    ?.let { i ->
                                        state.lines3DPudorys[i] = state.lines3DPudorys[i].copy(parent = updated)
                                    }

                                state.lines3DNarys.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }
                                    ?.let { i ->
                                        state.lines3DNarys[i] = state.lines3DNarys[i].copy(parent = updated)
                                    }
                                state.lines3DBokorys.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }
                                    ?.let { i ->
                                        state.lines3DBokorys[i] = state.lines3DBokorys[i].copy(parent = updated)
                                    }
                                state.lines3DAxo.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }
                                    ?.let { i ->
                                        state.lines3DAxo[i] = state.lines3DAxo[i].copy(parent = updated)
                                    }
                                val pudp = state.pointsPudorys.indexOfFirst { projectedLineIdOf(it) == updated.id }
                                if (pudp != -1 ){
                                    state.pointsPudorys[pudp] = state.pointsPudorys[pudp].copy(localColor = updated.color)
                                        .withAxoVisibilityFrom(state.pointsPudorys[pudp])
                                }
                                val narp = state.pointsNarys.indexOfFirst { projectedLineIdOf(it) == updated.id }
                                if (narp != -1 ){
                                    state.pointsNarys[narp] = state.pointsNarys[narp].copy(localColor = updated.color)
                                        .withAxoVisibilityFrom(state.pointsNarys[narp])
                                }
                                val bokp = state.pointsBokorys.indexOfFirst { projectedLineIdOf(it) == updated.id }
                                if (bokp != -1 ){
                                    state.pointsBokorys[bokp] = state.pointsBokorys[bokp].copy(localColor = updated.color)
                                        .withAxoVisibilityFrom(state.pointsBokorys[bokp])
                                }
                                val axop = state.pointsAxo.indexOfFirst { projectedLineIdOf(it) == updated.id }
                                if (axop != -1 ){
                                    state.pointsAxo[axop] = state.pointsAxo[axop].copy(localColor = updated.color)
                                        .withAxoVisibilityFrom(state.pointsAxo[axop])
                                }
                                state.triggerRedraw++
                                commitSnapshot(state)

                            },
                            onWidthChange = {
                                parentLine.strokeWidth = it
                            },
                            onStyleChange = { newStyle ->

                                val old = parentLine
                                val updated = old.copy(lineStyle = newStyle)

                                // Nahraď v sharedLines3D
                                val idx = state.lines3D.indexOfFirst { it.id == old.id }
                                if (idx != -1) {
                                    state.lines3D[idx] = updated
                                }

                                // Aktualizuj projekce s novým parentem
                                state.lines3DPudorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }
                                    ?.let { i ->
                                        state.lines3DPudorys[i] = state.lines3DPudorys[i].copy(parent = updated)
                                    }

                                state.lines3DNarys.indexOfFirst { it.parent === old }.takeIf { it != -1 }
                                    ?.let { i ->
                                        state.lines3DNarys[i] = state.lines3DNarys[i].copy(parent = updated)
                                    }
                                state.lines3DBokorys.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }
                                    ?.let { i ->
                                        state.lines3DBokorys[i] = state.lines3DBokorys[i].copy(parent = updated)
                                    }
                                state.lines3DAxo.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }
                                    ?.let { i ->
                                        state.lines3DAxo[i] = state.lines3DAxo[i].copy(parent = updated)
                                    }

                                state.triggerRedraw++
                                commitSnapshot(state)

                            },
                            clipOverridePudorys = pProj?.clipLineX,
                            clipOverrideNarys = nProj?.clipLineX,
                            globalDefaultClipPudorys = state.defaultClipBelowX12Pudorys,
                            globalDefaultClipNarys = state.defaultClipAboveX12Narys,
                            onClipOverridePudorysChange = { next ->
                                pProj?.let { pp ->
                                    state.lines3DPudorys.replaceFirst({ it.id == pp.id }) {
                                        it.copy(clipLineX = next)
                                    }
                                }
                                commitSnapshot(state)

                            },
                            onClipOverrideNarysChange = { next ->
                                nProj?.let { np ->
                                    state.lines3DNarys.replaceFirst({ it.id == np.id }) {
                                        it.copy(clipLineX = next)
                                    }
                                }
                                commitSnapshot(state)
                            },
                            onDelete = {
                                if (isX12) return@EditableParentLineInfo
                                deleteLine3D(state, parentLine)
                                clearSelection(state)
                            },
                            onApply = if (isX12) {
                                { _, _ -> }
                            } else { newName, newSup ->

                                val cleanedName = newName.removeSuffix("₁").removeSuffix("₂").trim()
                                val cleanedSup  = newSup.trim().ifEmpty { null }

                                // ✅ vždy pracuj přes ID, ne přes starou referenci
                                val idx = state.lines3D.indexOfFirst { it.id == parentLine.id }


                                val old = state.lines3D[idx]
                                val updated = old.copy(
                                    name = cleanedName,
                                    superscript = cleanedSup
                                )
                                state.lines3D[idx] = updated

                                // ✅ projekce hledat podle parentId (ne === old)
                                val pIndex = state.lines3DPudorys.indexOfFirst { it.parent?.id == updated.id }
                                if (pIndex != -1) {
                                    state.lines3DPudorys[pIndex] = state.lines3DPudorys[pIndex].copy(
                                        localName = cleanedName.withSuffixOnce("₁"),
                                        localSuperscript = cleanedSup,
                                        parent = updated
                                    )
                                }

                                val nIndex = state.lines3DNarys.indexOfFirst { it.parent?.id == updated.id }
                                if (nIndex != -1) {
                                    state.lines3DNarys[nIndex] = state.lines3DNarys[nIndex].copy(
                                        localName = cleanedName.withSuffixOnce("₂"),
                                        localSuperscript = cleanedSup,
                                        parent = updated
                                    )
                                }

                                val pudp = state.pointsPudorys.indexOfFirst { projectedLineIdOf(it) == updated.id }
                                if (pudp != -1 ){
                                    state.pointsPudorys[pudp] = state.pointsPudorys[pudp].copy(
                                        name = cleanedName,
                                        localSuperscript = cleanedSup,
                                        ).withAxoVisibilityFrom(state.pointsPudorys[pudp])
                                }
                                val narp = state.pointsNarys.indexOfFirst { projectedLineIdOf(it) == updated.id }
                                if (narp != -1 ){
                                    state.pointsNarys[narp] = state.pointsNarys[narp].copy(
                                        name = cleanedName,
                                        localSuperscript = cleanedSup,
                                    ).withAxoVisibilityFrom(state.pointsNarys[narp])
                                }
                                val bokp = state.pointsBokorys.indexOfFirst { projectedLineIdOf(it) == updated.id }
                                if (bokp != -1 ){
                                    state.pointsBokorys[bokp] = state.pointsBokorys[bokp].copy(
                                        name = cleanedName,
                                        localSuperscript = cleanedSup,
                                    ).withAxoVisibilityFrom(state.pointsBokorys[bokp])
                                }
                                val axop = state.pointsAxo.indexOfFirst { projectedLineIdOf(it) == updated.id }
                                if (axop != -1 ){
                                    state.pointsAxo[axop] = state.pointsAxo[axop].copy(
                                        name = cleanedName,
                                        localSuperscript = cleanedSup,
                                    ).withAxoVisibilityFrom(state.pointsAxo[axop])
                                }
                                commitSnapshot(state)
                                state.triggerRedraw++
                            },
                            state = state,
                            uiScale = SettingsManager.current.UIscale/75f,
                            showPudorysProjection = showPudorysLineProjection(state, parentLine),
                            onShowPudorysProjectionChange = { checked ->
                                setPudorysLineProjectionVisible(state, parentLine, checked)
                            },
                            showNarysProjection = showNarysLineProjection(state, parentLine),
                            onShowNarysProjectionChange = { checked ->
                                setNarysLineProjectionVisible(state, parentLine, checked)
                            },
                            showBokorysProjection = showBokorysLineProjection(state, parentLine),
                            onShowBokorysProjectionChange = { checked ->
                                setBokorysLineProjectionVisible(state, parentLine, checked)
                            },
                            showAxoProjection = showAxoLineProjection(state, parentLine),
                            onShowAxoProjectionChange = { checked ->
                                setAxoLineProjectionVisible(state, parentLine, checked)
                            }



                        )
                    }
            }

        }
    }
}
fun Float.pretty(): String =
    if (this % 1f == 0f) this.toInt().toString()
    else "%.2f".format(this).trimEnd('0').trimEnd('.')
/** KOTO: přidá kótu samostatnému půdorysnému bodu → vznikne 3D bod (kóta = z) + nárys. */
private fun kotoAddKota(state: MongeState, pud: Point3DPudorys, name: String, sup: String, kotaZ: Float) {
    val i = state.pointsPudorys.indexOfFirst { it.id == pud.id }
    if (i == -1) return
    val cur = state.pointsPudorys[i]
    val parent = Point3D(
        x = cur.x, y = cur.y, z = kotaZ,
        name = name, superscript = sup,
        color = cur.color, width = cur.width,
        creationIndex = allocIndex(state)
    )
    state.pointsPudorys[i] = cur.copy(
        name = name.withSuffixOnce("₁"),
        localSuperscript = sup,
        parent = parent
    )
    state.pointsNarys.add(
        Point3DNarys(
            x = cur.x, z = kotaZ,
            name = name.withSuffixOnce("₂"),
            localSuperscript = sup,
            localColor = cur.color,
            parent = parent,
            creationIndex = allocIndex(state)
        )
    )
    state.sharedPoints3D.add(parent)
    update2DSnapshots(state)
}

/** KOTO: odebere kótu 3D bodu → zůstane samostatný půdorysný bod, nárys i 3D parent se smažou. */
private fun kotoRemoveKota(state: MongeState, parent: Point3D, name: String, sup: String) {
    val narysToRemove = state.pointsNarys.filter { it.parent?.id == parent.id }
    narysToRemove.forEach {
        state.labelOffsetsPointsNarys.remove(it.id)
        state.selectedPointsNarys.remove(it)
    }
    state.pointsNarys.removeAll(narysToRemove.toSet())

    state.pointsPudorys.indexOfFirst { it.parent?.id == parent.id }.takeIf { it != -1 }?.let { i ->
        val old = state.pointsPudorys[i]
        state.pointsPudorys[i] = old.copy(
            name = name,
            localSuperscript = sup,
            parent = null,
            localColor = parent.color,
            localWidth = parent.width
        )
    }
    state.sharedPoints3D.removeAll { it.id == parent.id }
    update2DSnapshots(state)
}

private fun replacePointInStateLists(state: MongeState, p: Point2DProjection) {
    when (p) {
        is Point3DPudorys -> {
            val i = state.pointsPudorys.indexOfFirst { it.id == p.id }
            if (i != -1) state.pointsPudorys[i] = p
        }
        is Point3DNarys -> {
            val i = state.pointsNarys.indexOfFirst { it.id == p.id }
            if (i != -1) state.pointsNarys[i] = p
        }
        is Point3DBokorys -> {
            val i = state.pointsBokorys.indexOfFirst { it.id == p.id }
            if (i != -1) state.pointsBokorys[i] = p
        }
        is Point3DAxo -> {
            val i = state.pointsAxo.indexOfFirst { it.id == p.id }
            if (i != -1) state.pointsAxo[i] = p
        }
    }
}

