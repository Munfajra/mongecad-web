package ui.mongeui.toolbar.rightDescriptionBar



import utils.replaceAll
import model.classes.isAxisX
import model.classes.isAxisY
import ui.components.MiniInputField2
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import utils.withSuffixOnce
import draw.mongescreen.labels.clearSelection
import model.*
import model.classes.*





import monge.input.combineprojections.CompleteLineAdd
import monge.input.combineprojections.CompletePlaneAdd
import monge.input.combineprojections.beginPlaneFromTracePickPoint
import monge.input.combineprojections.startLine3DCompletion
import monge.input.lines.*
import serialization.SettingsManager
import serialization.commitSnapshot
import model.classes.isAxis
import model.classes.isAxisProjection
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongePropertyRow
import ui.components.MongeSectionTitle
import ui.mongeui.toolbar.SkikoButton
import ui.mongeui.toolbar.setProjectionPhase


@Composable

private fun LineVisualEditor(

    color: Color,

    width: Float,

    style: LineStyle,

    ui: UiScale,

    onColorChange: (Color) -> Unit,

    onWidthChange: (Float) -> Unit,

    onStyleChange: (LineStyle) -> Unit,

    state: MongeState

) {

    var pendingColor by remember(color) {

        mutableStateOf(color)

    }



    var pendingWidth by remember(width) {

        mutableStateOf(width)

    }
        MongePropertyRow(label = "Barva:") {

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



    MongePropertyRow(label = "Šířka:") {

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



    MongePropertyRow(label = "Styl čáry:") {

        LineStyleSelector(

            current = style,

            onStyleChange = onStyleChange

        )

    }

}

@Composable

private fun LineNameEditor(

    name: String,

    superscript: String,

    enabled: Boolean,

    state: MongeState,

    ui: UiScale,

    onApply: (String, String) -> Unit

) {

    val label = "Přímka"

    val colors = LocalMongeColors.current

    val normalizedSuperscript = superscript.emptyIfNullText()



    var pendingName by remember(name) {

        mutableStateOf(TextFieldValue(name.trim()))

    }



    var pendingSup by remember(normalizedSuperscript) {

        mutableStateOf(TextFieldValue(normalizedSuperscript))

    }



    var lastName by remember(name) {

        mutableStateOf(name.trim())

    }



    var lastSup by remember(normalizedSuperscript) {

        mutableStateOf(normalizedSuperscript)

    }



    var showGreek by remember { mutableStateOf(false) }



    LaunchedEffect(name, superscript) {

        val n = name.trim()

        val s = superscript.emptyIfNullText()



        lastName = n

        lastSup = s



        pendingName = TextFieldValue(n)

        pendingSup = TextFieldValue(s)

    }



    val canApply =

        pendingName.text.trim() != lastName ||

                pendingSup.text.trim() != lastSup



    fun apply() {

        if (!enabled || !canApply) return



        val n = pendingName.text.trim()

        val s = pendingSup.text.trim()



        onApply(n, s)



        lastName = n

        lastSup = s

        state.focusRequester.requestFocus()

    }



    LabeledRow(

        label = label,

        ui = ui,

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

                    enabled = enabled,

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

                    enabled = enabled,

                    modifier = Modifier.offset(y = -ui.dp(7f)),

                    state = state

                )



                Box {

                    SkikoButton(

                        width = ui.dp(32f),

                        height = ui.dp(30f),

                        enabled = enabled,

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

                enabled = enabled && canApply,

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

}



private fun String.emptyIfNullText(): String {

    val trimmed = trim()

    return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed

}

private fun String.lineBaseNameForEditor(): String =
    trim()
        .removeSuffix("₁")
        .removeSuffix("₂")
        .removeSuffix("₃")
        .removeSuffix("ₐ")
        .removeSuffix("₀")
        .removeSuffix("â‚")
        .removeSuffix("â‚‚")
        .removeSuffix("â‚")
        .removeSuffix("â‚")
        .trim()

private fun axoLocalNameForEditor(base: String): String =
    base.withSuffixOnce("ₐ")



private fun Line3DProjectionPudorys.withAxoVisibilityFrom(source: Line3DProjectionPudorys): Line3DProjectionPudorys =

    apply {

        showInAxoInitial = source.showInAxo

        showInAxo = source.showInAxo

    }



private fun Line3DProjectionNarys.withAxoVisibilityFrom(source: Line3DProjectionNarys): Line3DProjectionNarys =

    apply {

        showInAxoInitial = source.showInAxo

        showInAxo = source.showInAxo

    }



private fun Line3DProjectionBokorys.withAxoVisibilityFrom(source: Line3DProjectionBokorys): Line3DProjectionBokorys =

    apply {

        showInAxoInitial = source.showInAxo

        showInAxo = source.showInAxo

    }



private fun Line3DProjectionAxo.withAxoVisibilityFrom(source: Line3DProjectionAxo): Line3DProjectionAxo =

    apply {

        showInAxoInitial = source.showInAxo

        showInAxo = source.showInAxo

    }



@Composable

fun EditableParentLineInfo(
    line: Line3D,
    canRename: Boolean = true,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    clipOverridePudorys: Boolean?,
    clipOverrideNarys: Boolean?,
    globalDefaultClipPudorys: Boolean,
    globalDefaultClipNarys: Boolean,
    onClipOverridePudorysChange: (Boolean?) -> Unit,
    onClipOverrideNarysChange: (Boolean?) -> Unit,
    onDelete: () -> Unit,
    onApply: (newName: String, newSup: String) -> Unit,
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
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current
    val isMonge = state.projectionMode == ProjectionMode.MONGE
    val isAxo = state.projectionMode == ProjectionMode.AXO
    val isAxis = isAxis(line)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        LineNameEditor(
            name = line.name,
            superscript = line.superscript ?: "",
            enabled = canRename,
            state = state,
            ui = ui,
            onApply = onApply
        )

        MongeDivider()
        LineVisualEditor(
            color = line.color,
            width = line.strokeWidth,
            style = line.lineStyle,
            ui = ui,
            onColorChange = onColorChange,
            onWidthChange = onWidthChange,
            onStyleChange = onStyleChange,
            state = state
        )

        if (isAxo && !isAxis) {
            MongeDivider()
            LabeledRow(
                label = "Průměty:",
                ui = ui,
                contentAlign = Alignment.End
            ) {
                ProjectionVisibilityToggleStrip(
                    ui = ui,
                    ProjectionVisibilityToggleItem("A", showAxoProjection, onShowAxoProjectionChange),
                    ProjectionVisibilityToggleItem("P", showPudorysProjection, onShowPudorysProjectionChange),
                    ProjectionVisibilityToggleItem("N", showNarysProjection, onShowNarysProjectionChange),
                    ProjectionVisibilityToggleItem("B", showBokorysProjection, onShowBokorysProjectionChange)
                )
            }

            val pProj = state.lines3DPudorys.firstOrNull { it.parent?.id == line.id || it.parentId == line.id }
            val nProj = state.lines3DNarys.firstOrNull { it.parent?.id == line.id || it.parentId == line.id }
            val bProj = state.lines3DBokorys.firstOrNull { it.parent?.id == line.id || it.parentId == line.id }
            val aProj = state.lines3DAxo.firstOrNull { it.parent?.id == line.id || it.parentId == line.id }
            val hasClipRows =
                (aProj?.showInAxo == true) ||
                (pProj?.showInAxo == true) ||
                (nProj?.showInAxo == true) ||
                (bProj?.showInAxo == true)

            if (hasClipRows) {
                MongeDivider()
                MongeSectionTitle("Ořez průmětů")

                if (aProj?.showInAxo == true) {
                    TriStateClipSettingRow("Axo – o průmětny", aProj.clipToOctant, ui, state.defaultClipAxoLineToOctant) { next ->
                        state.lines3DAxo.replaceAll { if (it.id == aProj.id) it.copy(clipToOctant = next).withAxoVisibilityFrom(it) else it }
                        state.selectedLinesAxo.replaceAll {
                            if (it.id == aProj.id) it.copy(clipToOctant = next).withAxoVisibilityFrom(it) else it
                        }
                        commitSnapshot(state)
                    }
                }

                if (pProj?.showInAxo == true) {
                    TriStateClipSettingRow("Půdorys – osa X", pProj.clipLineX, ui, state.defaultClipBelowX12Pudorys) { next ->
                        state.lines3DPudorys.replaceAll { if (it.id == pProj.id) it.copy(clipLineX = next).withAxoVisibilityFrom(it) else it }
                        state.selectedLinesPudorys.replaceAll {
                            if (it.id == pProj.id && it is Line3DProjectionPudorys) it.copy(clipLineX = next).withAxoVisibilityFrom(it) else it
                        }
                        commitSnapshot(state)
                    }
                    TriStateClipSettingRow("Půdorys – osa Y", pProj.clipLineY, ui, state.defaultClipLeftOfYAxisPudorys) { next ->
                        state.lines3DPudorys.replaceAll { if (it.id == pProj.id) it.copy(clipLineY = next).withAxoVisibilityFrom(it) else it }
                        state.selectedLinesPudorys.replaceAll {
                            if (it.id == pProj.id && it is Line3DProjectionPudorys) it.copy(clipLineY = next).withAxoVisibilityFrom(it) else it
                        }
                        commitSnapshot(state)
                    }
                }

                if (nProj?.showInAxo == true) {
                    TriStateClipSettingRow("Nárys – osa X", nProj.clipLineX, ui, state.defaultClipAboveX12Narys) { next ->
                        state.lines3DNarys.replaceAll { if (it.id == nProj.id) it.copy(clipLineX = next).withAxoVisibilityFrom(it) else it }
                        state.selectedLinesNarys.replaceAll {
                            if (it.id == nProj.id && it is Line3DProjectionNarys) it.copy(clipLineX = next).withAxoVisibilityFrom(it) else it
                        }
                        commitSnapshot(state)
                    }
                    TriStateClipSettingRow("Nárys – osa Z", nProj.clipLineZ, ui, state.defaultClipLeftOfZAxisNarys) { next ->
                        state.lines3DNarys.replaceAll { if (it.id == nProj.id) it.copy(clipLineZ = next).withAxoVisibilityFrom(it) else it }
                        state.selectedLinesNarys.replaceAll {
                            if (it.id == nProj.id && it is Line3DProjectionNarys) it.copy(clipLineZ = next).withAxoVisibilityFrom(it) else it
                        }
                        commitSnapshot(state)
                    }
                }

                if (bProj?.showInAxo == true) {
                    TriStateClipSettingRow("Bokorys – osa Y", bProj.clipLineY, ui, state.defaultClipBelowYAxisBokorys) { next ->
                        state.lines3DBokorys.replaceAll { if (it.id == bProj.id) it.copy(clipLineY = next).withAxoVisibilityFrom(it) else it }
                        state.selectedLinesBokorys.replaceAll {
                            if (it.id == bProj.id && it is Line3DProjectionBokorys) it.copy(clipLineY = next).withAxoVisibilityFrom(it) else it
                        }
                        commitSnapshot(state)
                    }
                    TriStateClipSettingRow("Bokorys – osa Z", bProj.clipLineZ, ui, state.defaultClipLeftOfZAxisBokorys) { next ->
                        state.lines3DBokorys.replaceAll { if (it.id == bProj.id) it.copy(clipLineZ = next).withAxoVisibilityFrom(it) else it }
                        state.selectedLinesBokorys.replaceAll {
                            if (it.id == bProj.id && it is Line3DProjectionBokorys) it.copy(clipLineZ = next).withAxoVisibilityFrom(it) else it
                        }
                        commitSnapshot(state)
                    }
                }
            }
        }

        if (canRename && isMonge) {
            MongeDivider()
            MongeSectionTitle("Ořez X₁₂ (půdorys)")
            var uiP by remember(line.id, clipOverridePudorys) {
                mutableStateOf(clipOverridePudorys)
            }
            LaunchedEffect(line.id, clipOverridePudorys) {
                uiP = clipOverridePudorys
            }
            val stateP = when (uiP) {
                null -> ToggleableState.Indeterminate
                true -> ToggleableState.On
                false -> ToggleableState.Off
            }
            val statusP = when (uiP) {
                null -> if (globalDefaultClipPudorys) "Dle nastavení ✓" else "Dle nastavení ✕"
                true -> "Ořezáno"
                false -> "Neořezáno"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ui.dp(36f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TriStateCheckbox(
                    state = stateP,
                    onClick = {
                        val next = when (uiP) {
                            null -> true
                            true -> false
                            false -> null
                        }
                        uiP = next
                        onClipOverridePudorysChange(next)
                    },
                    modifier = Modifier.size(ui.dp(24f)),
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.selected,
                        uncheckedColor = Color.Gray,
                        checkmarkColor = Color.White
                    )
                )
                Spacer(Modifier.width(ui.dp(8f)))
                Text(
                    statusP,
                    fontSize = ui.sp(14f),
                    maxLines = 1,
                    softWrap = false,
                    color = colors.text
                )
            }
            MongeDivider()
            MongeSectionTitle("Ořez X₁₂ (nárys)")
            var uiN by remember(line.id, clipOverrideNarys) {

                mutableStateOf(clipOverrideNarys)

            }



            LaunchedEffect(line.id, clipOverrideNarys) {

                uiN = clipOverrideNarys

            }



            val stateN = when (uiN) {

                null -> ToggleableState.Indeterminate

                true -> ToggleableState.On

                false -> ToggleableState.Off

            }



            val statusN = when (uiN) {

                null -> if (globalDefaultClipNarys) "Dle nastavení ✓" else "Dle nastavení ✕"

                true -> "Ořezáno"

                false -> "Neořezáno"

            }



            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(ui.dp(36f)),

                verticalAlignment = Alignment.CenterVertically

            ) {

                TriStateCheckbox(

                    state = stateN,

                    onClick = {

                        val next = when (uiN) {

                            null -> true

                            true -> false

                            false -> null

                        }



                        uiN = next

                        onClipOverrideNarysChange(next)

                    },

                    modifier = Modifier.size(ui.dp(24f)),

                    colors = CheckboxDefaults.colors(

                        checkedColor = colors.selected,

                        uncheckedColor = Color.Gray,

                        checkmarkColor = Color.White

                    )

                )



                Spacer(Modifier.width(ui.dp(8f)))



                Text(

                    statusN,

                    fontSize = ui.sp(14f),

                    maxLines = 1,

                    softWrap = false,

                    color = colors.text

                )

            }

        }



        if (canRename) {

            MongeDivider()

            CustomLineTrimControls(

                state = state,

                target = line,

                hasCustomTrim = line.customTrimRange != null,

                ui = ui,

                enabled = !isAxis(line)

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



/* --- jednoduchĂ˝ vizuĂˇlnĂ­ oddÄ›lovaÄŤ sekcĂ­ --- */

@Composable

fun SectionDivider() {

    val colors = LocalMongeColors.current

    Spacer(Modifier.height(8.dp))

    Divider(color = colors.base.lighter(0.6f)) // pouĹľij material/material3 podle toho, co mĂˇĹˇ v projektu

    Spacer(Modifier.height(8.dp))

}



/**
 * Jednotný řádek nastavení ořezu (přímky i roviny): popisek vlevo, stavový
 * text uprostřed a tri-state checkbox ukotvený na pravém okraji, aby
 * "necestoval" podle šířky stavového textu a šel rychle přepínat.
 * Cyklus: dle nastavení (null) → ořezáno (true) → neořezáno (false).
 */
@Composable

fun TriStateClipSettingRow(

    label: String,

    value: Boolean?,

    ui: UiScale,

    globalDefault: Boolean? = null,

    onChange: (Boolean?) -> Unit

) {

    val colors = LocalMongeColors.current

    val toggleState = when (value) {

        null -> ToggleableState.Indeterminate

        true -> ToggleableState.On

        false -> ToggleableState.Off

    }

    val statusText = when (value) {

        null -> when (globalDefault) {

            true -> "Dle nastavení ✓"

            false -> "Dle nastavení ✕"

            null -> "Dle nastavení ✓"

        }

        true -> "Ořezáno"

        false -> "Neořezáno"

    }



    Row(

        modifier = Modifier
            .fillMaxWidth()
            .height(ui.dp(36f)),

        verticalAlignment = Alignment.CenterVertically

    ) {

        Text(

            label,

            fontSize = ui.sp(13f),

            color = colors.text,

            modifier = Modifier.weight(1f)

        )



        Text(

            statusText,

            fontSize = ui.sp(13f),

            maxLines = 1,

            softWrap = false,

            color = colors.text

        )



        Spacer(Modifier.width(ui.dp(8f)))



        TriStateCheckbox(

            state = toggleState,

            onClick = {

                val next = when (value) {

                    null -> true

                    true -> false

                    false -> null

                }

                onChange(next)

            },

            modifier = Modifier.size(ui.dp(24f)),

            colors = CheckboxDefaults.colors(

                checkedColor = colors.selected,

                uncheckedColor = Color.Gray,

                checkmarkColor = Color.White

            )

        )

    }

}



@Composable

private fun CustomLineTrimControls(

    state: MongeState,

    target: Any,

    hasCustomTrim: Boolean,

    ui: UiScale,

    enabled: Boolean = true

) {

    MongeSectionTitle("Vlastní ořez")

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.End

    ) {

        if (hasCustomTrim) {

            SkikoButton(

                width = ui.dp(92f),

                height = ui.dp(38f),

                onClick = { clearCustomLineTrim(state, target) },

                enabled = enabled

            ) {

                Text("Zrušit", fontSize = ui.sp(13f))

            }

            Spacer(Modifier.width(ui.dp(8f)))

        }

        SkikoButton(

            width = ui.dp(132f),

            height = ui.dp(38f),

            onClick = { beginCustomLineTrim(state, target) },

            enabled = enabled

        ) {

            Text("Vlastní ořez", fontSize = ui.sp(13f))

        }

    }

}



@Composable

fun EditableLineProjectionInfo(

    line: Line2DProjection,

    onColorChange: (Color) -> Unit,

    onWidthChange: (Float) -> Unit,

    onStyleChange: (LineStyle) -> Unit,

    onSelectProjection: () -> Unit,

    onAddProjection: () -> Unit,

    clipOverride: Boolean?,

    globalDefaultClip: Boolean,

    onClipOverrideChange: (Boolean?) -> Unit,

    onDelete: () -> Unit,

    state: MongeState,

    onApply: (newName: String, newSup: String) -> Unit,

    uiScale: Float

) {

    val colors = LocalMongeColors.current



    val isPlane = state.projectionMode == ProjectionMode.PLANE

    val isKoto = state.projectionMode == ProjectionMode.KOTO

    val isMonge = state.projectionMode == ProjectionMode.MONGE

    val isAxo = state.projectionMode == ProjectionMode.AXO

    val isAxis = isAxisProjection(line)

    val ui = remember(uiScale) { UiScale(uiScale) }



    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(ui.dp(10f)),

        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))

    ) {



        LineNameEditor(

            name = (line.name ?: "").lineBaseNameForEditor(),

            superscript = line.superscript ?: "",

            enabled = !isAxis,

            state = state,

            ui = ui,

            onApply = onApply

        )



        MongeDivider()



        LineVisualEditor(

            color = line.color,

            width = line.strokeWidth,

            style = line.lineStyle,

            ui = ui,

            onColorChange = onColorChange,

            onWidthChange = onWidthChange,

            onStyleChange = onStyleChange,

            state = state

        )
        if (!isPlane && !isKoto) {
            MongeDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SkikoButton(
                    width = ui.dp(100f),
                    height = ui.dp(38f),
                    onClick = onSelectProjection,
                    enabled = line.parent == null
                ) {
                    Text(
                        "Sdružit",
                        fontSize = ui.sp(13f)
                    )
                }
            }



            if (isMonge) {



                MongeDivider()



                MongeSectionTitle("Ořez X₁₂")



                var clipUi by remember(line.id, clipOverride) {

                    mutableStateOf(clipOverride)

                }



                LaunchedEffect(line.id, clipOverride) {

                    clipUi = clipOverride

                }



                val toggleState = when (clipUi) {

                    null -> ToggleableState.Indeterminate

                    true -> ToggleableState.On

                    false -> ToggleableState.Off

                }



                val statusText = when (clipUi) {

                    null ->

                        if (globalDefaultClip)

                            "Dle nastavení ✓"

                        else

                            "Dle nastavení ✕"



                    true -> "Ořezáno"

                    false -> "Neořezáno"

                }



                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .height(ui.dp(36f)),

                    verticalAlignment = Alignment.CenterVertically

                ) {



                    TriStateCheckbox(

                        state = toggleState,

                        onClick = {

                            val next = when (clipUi) {

                                null -> true

                                true -> false

                                false -> null

                            }



                            clipUi = next

                            onClipOverrideChange(next)

                        },

                        modifier = Modifier.size(ui.dp(24f)),

                        colors = CheckboxDefaults.colors(

                            checkedColor = colors.selected,

                            uncheckedColor = Color.Gray,

                            checkmarkColor = Color.White

                        )

                    )



                    Spacer(Modifier.width(ui.dp(8f)))



                    Text(

                        statusText,

                        fontSize = ui.sp(14f),

                        maxLines = 1,

                        softWrap = false,

                        color = colors.text

                    )

                }

            }



            if (isAxo && line.showInAxo && line !is Line3DProjectionAxo) {

                MongeDivider()



                MongeSectionTitle("Ořez průmětu")



                when (line) {

                    is Line3DProjectionPudorys -> {

                        TriStateClipSettingRow("Osa X", line.clipLineX, ui, state.defaultClipBelowX12Pudorys) { next ->

                            state.lines3DPudorys.replaceAll { if (it.id == line.id) it.copy(clipLineX = next).withAxoVisibilityFrom(it) else it }

                            state.selectedLinesPudorys.replaceAll {

                                if (it.id == line.id && it is Line3DProjectionPudorys) it.copy(clipLineX = next).withAxoVisibilityFrom(it) else it

                            }

                            commitSnapshot(state)

                        }

                        TriStateClipSettingRow("Osa Y", line.clipLineY, ui, state.defaultClipLeftOfYAxisPudorys) { next ->

                            state.lines3DPudorys.replaceAll { if (it.id == line.id) it.copy(clipLineY = next).withAxoVisibilityFrom(it) else it }

                            state.selectedLinesPudorys.replaceAll {

                                if (it.id == line.id && it is Line3DProjectionPudorys) it.copy(clipLineY = next).withAxoVisibilityFrom(it) else it

                            }

                            commitSnapshot(state)

                        }

                    }



                    is Line3DProjectionNarys -> {

                        TriStateClipSettingRow("Osa X", line.clipLineX, ui, state.defaultClipAboveX12Narys) { next ->

                            state.lines3DNarys.replaceAll { if (it.id == line.id) it.copy(clipLineX = next).withAxoVisibilityFrom(it) else it }

                            state.selectedLinesNarys.replaceAll {

                                if (it.id == line.id && it is Line3DProjectionNarys) it.copy(clipLineX = next).withAxoVisibilityFrom(it) else it

                            }

                            commitSnapshot(state)

                        }

                        TriStateClipSettingRow("Osa Z", line.clipLineZ, ui, state.defaultClipLeftOfZAxisNarys) { next ->

                            state.lines3DNarys.replaceAll { if (it.id == line.id) it.copy(clipLineZ = next).withAxoVisibilityFrom(it) else it }

                            state.selectedLinesNarys.replaceAll {

                                if (it.id == line.id && it is Line3DProjectionNarys) it.copy(clipLineZ = next).withAxoVisibilityFrom(it) else it

                            }

                            commitSnapshot(state)

                        }

                    }



                    is Line3DProjectionBokorys -> {

                        TriStateClipSettingRow("Osa Y", line.clipLineY, ui, state.defaultClipBelowYAxisBokorys) { next ->

                            state.lines3DBokorys.replaceAll { if (it.id == line.id) it.copy(clipLineY = next).withAxoVisibilityFrom(it) else it }

                            state.selectedLinesBokorys.replaceAll {

                                if (it.id == line.id && it is Line3DProjectionBokorys) it.copy(clipLineY = next).withAxoVisibilityFrom(it) else it

                            }

                            commitSnapshot(state)

                        }

                        TriStateClipSettingRow("Osa Z", line.clipLineZ, ui, state.defaultClipLeftOfZAxisBokorys) { next ->

                            state.lines3DBokorys.replaceAll { if (it.id == line.id) it.copy(clipLineZ = next).withAxoVisibilityFrom(it) else it }

                            state.selectedLinesBokorys.replaceAll {

                                if (it.id == line.id && it is Line3DProjectionBokorys) it.copy(clipLineZ = next).withAxoVisibilityFrom(it) else it

                            }

                            commitSnapshot(state)

                        }

                    }



                    else -> Unit

                }

            }



            MongeDivider()

            CustomLineTrimControls(

                state = state,

                target = line,

                hasCustomTrim = (line as? CustomTrimmedLine2D)?.customTrimRange != null,

                ui = ui,

                enabled = !isAxis

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



@Composable

fun EditableHelpLineNarysInfo(

    line: HelpLineNarys,

    onColorChange: (Color) -> Unit,

    onWidthChange: (Float) -> Unit,

    onStyleChange: (LineStyle) -> Unit,

    clipOverride: Boolean?,

    globalDefaultClip: Boolean,

    onClipOverrideChange: (Boolean?) -> Unit,

    onDelete: () -> Unit,

    state: MongeState,

    uiScale: Float,

    onApply: (name: String, upper: String, lower: String) -> Unit

) {

    val ui = remember(uiScale) { UiScale(uiScale) }

    val colors = LocalMongeColors.current



    val isAxis = line.id == "axisZ"

    val enabled = !isAxis



    var pendingColor by remember(line.id, line.color) {

        mutableStateOf(line.color)

    }



    var sliderValue by remember(line.id, line.strokeWidth) {

        mutableStateOf(line.strokeWidth)

    }



    val nameNow = (line.name ?: "").trim()

    val upperNow = (line.localSuperscript ?: "").trim()

    val lowerNow = (line.lowerSuperscript ?: "").trim()



    var pendingName by remember(line.id) {

        mutableStateOf(TextFieldValue(nameNow))

    }



    var pendingUpper by remember(line.id) {

        mutableStateOf(TextFieldValue(upperNow))

    }



    var pendingLower by remember(line.id) {

        mutableStateOf(TextFieldValue(lowerNow))

    }



    var lastName by remember(line.id) {

        mutableStateOf(nameNow)

    }



    var lastUpper by remember(line.id) {

        mutableStateOf(upperNow)

    }



    var lastLower by remember(line.id) {

        mutableStateOf(lowerNow)

    }



    var showGreek by remember { mutableStateOf(false) }

    var upperFocused by remember { mutableStateOf(false) }

    var lowerFocused by remember { mutableStateOf(false) }

    var greekTarget by remember(line.id) {

        mutableStateOf(SupTarget.LOWER)

    }



    LaunchedEffect(

        line.id,

        line.name,

        line.localSuperscript,

        line.lowerSuperscript

    ) {

        val n = (line.name ?: "").trim()

        val u = (line.localSuperscript ?: "").trim()

        val l = (line.lowerSuperscript ?: "").trim()



        lastName = n

        lastUpper = u

        lastLower = l



        pendingName = TextFieldValue(n)

        pendingUpper = TextFieldValue(u)

        pendingLower = TextFieldValue(l)

    }



    val canApply =

        pendingName.text.trim() != lastName ||

                pendingUpper.text.trim() != lastUpper ||

                pendingLower.text.trim() != lastLower



    fun apply() {

        if (!enabled || !canApply) return



        val n = pendingName.text.trim()

        val u = pendingUpper.text.trim()

        val l = pendingLower.text.trim()



        onApply(n, u, l)



        lastName = n

        lastUpper = u

        lastLower = l

        state.focusRequester.requestFocus()

    }



    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(ui.dp(10f)),

        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))

    ) {

        val title = if (isAxis) "Osa:" else "Přímka:"


        LabeledRow(

            label = title,

            ui = ui,

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

                    horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))

                ) {

                    MiniInputField2(

                        value = pendingName,

                        onValueChange = { pendingName = it },

                        placeholder = "Název",

                        numericOnly = false,

                        fontSize = ui.sp(16f),

                        width = ui.dp(60f),

                        height = ui.dp(34f),

                        enabled = enabled,

                        state = state

                    )



                    val upperHighlight =

                        upperFocused || (showGreek && greekTarget == SupTarget.UPPER)



                    val lowerHighlight =

                        lowerFocused || (showGreek && greekTarget == SupTarget.LOWER)



                    Column(

                        horizontalAlignment = Alignment.CenterHorizontally

                    ) {

                        Box(

                            modifier = Modifier

                                .border(

                                    width = ui.dp(1.2f),

                                    color = if (upperHighlight) colors.selected else Color.Transparent,

                                    shape = RoundedCornerShape(ui.dp(4f))

                                )

                                .padding(ui.dp(1f))

                        ) {

                            MiniInputField2(

                                value = pendingUpper,

                                onValueChange = { pendingUpper = it },

                                placeholder = "",

                                numericOnly = false,

                                fontSize = ui.sp(10f),

                                width = ui.dp(22f),

                                height = ui.dp(20f),

                                enabled = enabled,

                                modifier = Modifier.onFocusChanged {

                                    upperFocused = it.isFocused

                                    if (it.isFocused) greekTarget = SupTarget.UPPER

                                },

                                state = state

                            )

                        }



                        Spacer(Modifier.height(ui.dp(2f)))



                        Box(

                            modifier = Modifier

                                .border(

                                    width = ui.dp(1.2f),

                                    color = if (lowerHighlight) colors.selected else Color.Transparent,

                                    shape = RoundedCornerShape(ui.dp(4f))

                                )

                                .padding(ui.dp(1f))

                        ) {

                            MiniInputField2(

                                value = pendingLower,

                                onValueChange = { pendingLower = it },

                                placeholder = "",

                                numericOnly = false,

                                fontSize = ui.sp(10f),

                                width = ui.dp(22f),

                                height = ui.dp(20f),

                                enabled = enabled,

                                modifier = Modifier.onFocusChanged {

                                    lowerFocused = it.isFocused

                                    if (it.isFocused) greekTarget = SupTarget.LOWER

                                },

                                state = state

                            )

                        }

                    }



                    Box {

                        SkikoButton(

                            width = ui.dp(32f),

                            height = ui.dp(30f),

                            enabled = enabled,

                            onClick = { showGreek = true }

                        ) {

                            Text(

                                "β",

                                fontSize = ui.sp(14f)

                            )

                        }



                        GreekDropdown(

                            expanded = showGreek,

                            ui = ui,

                            onDismiss = { showGreek = false },

                            onSymbol = { symbol ->

                                when (greekTarget) {

                                    SupTarget.UPPER ->

                                        pendingUpper = TextFieldValue(symbol)



                                    SupTarget.LOWER ->

                                        pendingLower = TextFieldValue(symbol)

                                }

                            }

                        )

                    }

                }



                Spacer(Modifier.height(ui.dp(7f)))



                SkikoButton(

                    width = ui.dp(110f),

                    height = ui.dp(32f),

                    enabled = enabled && canApply,

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



        if (!isAxis) {

            MongeDivider()



            LabeledRow(

                label = "Barva:",

                ui = ui,

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



            LabeledRow(

                label = "Šířka:",

                ui = ui,

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



            LabeledRow(

                label = "Styl čáry:",

                ui = ui,

                contentAlign = Alignment.End

            ) {

                LineStyleSelector(

                    current = line.lineStyle,

                    onStyleChange = onStyleChange

                )

            }



            MongeDivider()



            MongeSectionTitle("Ořez X₁₂")



            var clipUi by remember(line.id, clipOverride) {

                mutableStateOf(clipOverride)

            }



            LaunchedEffect(line.id, clipOverride) {

                clipUi = clipOverride

            }



            val toggleState = when (clipUi) {

                null -> ToggleableState.Indeterminate

                true -> ToggleableState.On

                false -> ToggleableState.Off

            }



            val statusText = when (clipUi) {

                null ->

                    if (globalDefaultClip)

                        "Dle nastavení ✓"

                    else

                        "Dle nastavení ✕"



                true -> "Ořezáno"

                false -> "Neořezáno"

            }



            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(ui.dp(36f)),

                verticalAlignment = Alignment.CenterVertically

            ) {

                TriStateCheckbox(

                    state = toggleState,

                    onClick = {

                        val next = when (clipUi) {

                            null -> true

                            true -> false

                            false -> null

                        }



                        clipUi = next

                        onClipOverrideChange(next)

                    },

                    modifier = Modifier.size(ui.dp(24f)),

                    colors = CheckboxDefaults.colors(

                        checkedColor = colors.selected,

                        uncheckedColor = Color.Gray,

                        checkmarkColor = Color.White

                    )

                )



                Spacer(Modifier.width(ui.dp(8f)))



                Text(

                    statusText,

                    fontSize = ui.sp(14f),

                    maxLines = 1,

                    softWrap = false,

                    color = colors.text

                )

            }



            MongeDivider()

            CustomLineTrimControls(

                state = state,

                target = line,

                hasCustomTrim = line.customTrimRange != null,

                ui = ui,

                enabled = enabled

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

}

@Composable

fun EditableHelpLinePudorysInfo(

    line: HelpLinePudorys,

    onColorChange: (Color) -> Unit,

    onWidthChange: (Float) -> Unit,

    onStyleChange: (LineStyle) -> Unit,

    clipOverride: Boolean?,

    globalDefaultClip: Boolean,

    onClipOverrideChange: (Boolean?) -> Unit,

    onDelete: () -> Unit,

    state: MongeState,

    onApply: (name: String, upper: String, lower: String) -> Unit,

    uiScale: Float,

) {

    val ui = remember(uiScale) { UiScale(uiScale) }

    val colors = LocalMongeColors.current



    val isAxisX = line.id == "axisX"

    val isAxisY = line.id == "axisY"

    val isAxis = isAxisX || isAxisY

    val enabled = !isAxis



    val isPlane = state.projectionMode == ProjectionMode.PLANE



    var pendingColor by remember(line.id, line.color) {

        mutableStateOf(line.color)

    }



    var sliderValue by remember(line.id, line.strokeWidth) {

        mutableStateOf(line.strokeWidth)

    }



    val nameNow = (line.name ?: "").trim()

    val upperNow = (line.localSuperscript ?: "").trim()

    val lowerNow = (line.lowerSuperscript ?: "").trim()



    var pendingName by remember(line.id) {

        mutableStateOf(TextFieldValue(nameNow))

    }



    var pendingUpper by remember(line.id) {

        mutableStateOf(TextFieldValue(upperNow))

    }



    var pendingLower by remember(line.id) {

        mutableStateOf(TextFieldValue(lowerNow))

    }



    var lastName by remember(line.id) {

        mutableStateOf(nameNow)

    }



    var lastUpper by remember(line.id) {

        mutableStateOf(upperNow)

    }



    var lastLower by remember(line.id) {

        mutableStateOf(lowerNow)

    }



    var showGreek by remember { mutableStateOf(false) }

    var upperFocused by remember { mutableStateOf(false) }

    var lowerFocused by remember { mutableStateOf(false) }



    var greekTarget by remember(line.id) {

        mutableStateOf(SupTarget.LOWER)

    }



    LaunchedEffect(

        line.id,

        line.name,

        line.localSuperscript,

        line.lowerSuperscript

    ) {

        val n = (line.name ?: "").trim()

        val u = (line.localSuperscript ?: "").trim()

        val l = (line.lowerSuperscript ?: "").trim()



        lastName = n

        lastUpper = u

        lastLower = l



        pendingName = TextFieldValue(n)

        pendingUpper = TextFieldValue(u)

        pendingLower = TextFieldValue(l)

    }



    val canApply =

        pendingName.text.trim() != lastName ||

                pendingUpper.text.trim() != lastUpper ||

                pendingLower.text.trim() != lastLower



    fun apply() {

        if (!enabled || !canApply) return



        val n = pendingName.text.trim()

        val u = pendingUpper.text.trim()

        val l = pendingLower.text.trim()



        onApply(n, u, l)



        lastName = n

        lastUpper = u

        lastLower = l

        state.focusRequester.requestFocus()

    }



    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(ui.dp(10f)),

        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))

    ) {

        val title = if (isAxis) "Osa:" else "Přímka:"



        LabeledRow(

            label = title,

            ui = ui,

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

                    horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))

                ) {

                    MiniInputField2(

                        value = pendingName,

                        onValueChange = { pendingName = it },

                        placeholder = "Název",

                        numericOnly = false,

                        fontSize = ui.sp(16f),

                        width = ui.dp(60f),

                        height = ui.dp(34f),

                        enabled = enabled,

                        state = state

                    )



                    val upperHighlight =

                        upperFocused || (showGreek && greekTarget == SupTarget.UPPER)



                    val lowerHighlight =

                        lowerFocused || (showGreek && greekTarget == SupTarget.LOWER)



                    Column(

                        horizontalAlignment = Alignment.CenterHorizontally

                    ) {

                        Box(

                            modifier = Modifier

                                .border(

                                    width = ui.dp(1.2f),

                                    color = if (upperHighlight) colors.selected else Color.Transparent,

                                    shape = RoundedCornerShape(ui.dp(4f))

                                )

                                .padding(ui.dp(1f))

                        ) {

                            MiniInputField2(

                                value = pendingUpper,

                                onValueChange = { pendingUpper = it },

                                placeholder = "",

                                numericOnly = false,

                                fontSize = ui.sp(10f),

                                width = ui.dp(22f),

                                height = ui.dp(20f),

                                enabled = enabled,

                                modifier = Modifier.onFocusChanged {

                                    upperFocused = it.isFocused



                                    if (it.isFocused) {

                                        greekTarget = SupTarget.UPPER

                                    }

                                },

                                state = state

                            )

                        }



                        Spacer(Modifier.height(ui.dp(2f)))



                        Box(

                            modifier = Modifier

                                .border(

                                    width = ui.dp(1.2f),

                                    color = if (lowerHighlight) colors.selected else Color.Transparent,

                                    shape = RoundedCornerShape(ui.dp(4f))

                                )

                                .padding(ui.dp(1f))

                        ) {

                            MiniInputField2(

                                value = pendingLower,

                                onValueChange = { pendingLower = it },

                                placeholder = "",

                                numericOnly = false,

                                fontSize = ui.sp(10f),

                                width = ui.dp(22f),

                                height = ui.dp(20f),

                                enabled = enabled,

                                modifier = Modifier.onFocusChanged {

                                    lowerFocused = it.isFocused



                                    if (it.isFocused) {

                                        greekTarget = SupTarget.LOWER

                                    }

                                },

                                state = state

                            )

                        }

                    }



                    Box {

                        SkikoButton(

                            width = ui.dp(32f),

                            height = ui.dp(30f),

                            enabled = enabled,

                            onClick = { showGreek = true }

                        ) {

                            Text(

                                "β",

                                fontSize = ui.sp(14f)

                            )

                        }



                        GreekDropdown(

                            expanded = showGreek,

                            onDismiss = { showGreek = false },

                            onSymbol = { symbol ->

                                when (greekTarget) {

                                    SupTarget.UPPER ->

                                        pendingUpper = TextFieldValue(symbol)



                                    SupTarget.LOWER ->

                                        pendingLower = TextFieldValue(symbol)

                                }

                            },

                            ui = ui

                        )

                    }

                }



                Spacer(Modifier.height(ui.dp(7f)))



                SkikoButton(

                    width = ui.dp(110f),

                    height = ui.dp(32f),

                    enabled = enabled && canApply,

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



        if (!isAxis) {



            MongeDivider()



            LabeledRow(

                label = "Barva:",

                ui = ui,

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



            LabeledRow(

                label = "Šířka:",

                ui = ui,

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



            LabeledRow(

                label = "Styl čáry:",

                ui = ui,

                contentAlign = Alignment.End

            ) {

                LineStyleSelector(

                    current = line.lineStyle,

                    onStyleChange = onStyleChange

                )

            }



            if (!isPlane) {



                MongeDivider()



                MongeSectionTitle("Ořez X₁₂")



                var clipUi by remember(line.id, clipOverride) {

                    mutableStateOf(clipOverride)

                }



                LaunchedEffect(line.id, clipOverride) {

                    clipUi = clipOverride

                }



                val toggleState = when (clipUi) {

                    null -> ToggleableState.Indeterminate

                    true -> ToggleableState.On

                    false -> ToggleableState.Off

                }



                val statusText = when (clipUi) {

                    null ->

                        if (globalDefaultClip)

                            "Dle nastavení ✓"

                        else

                            "Dle nastavení ✕"



                    true -> "Ořezáno"

                    false -> "Neořezáno"

                }



                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .height(ui.dp(36f)),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    TriStateCheckbox(

                        state = toggleState,

                        onClick = {

                            val next = when (clipUi) {

                                null -> true

                                true -> false

                                false -> null

                            }



                            clipUi = next

                            onClipOverrideChange(next)

                        },

                        modifier = Modifier.size(ui.dp(24f)),

                        colors = CheckboxDefaults.colors(

                            checkedColor = colors.selected,

                            uncheckedColor = Color.Gray,

                            checkmarkColor = Color.White

                        )

                    )



                    Spacer(Modifier.width(ui.dp(8f)))



                    Text(

                        statusText,

                        fontSize = ui.sp(14f),

                        maxLines = 1,

                        softWrap = false,

                        color = colors.text

                    )

                }

            }



            if (!isPlane) {

                MongeDivider()

                CustomLineTrimControls(

                    state = state,

                    target = line,

                    hasCustomTrim = line.customTrimRange != null,

                    ui = ui,

                    enabled = enabled

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

}



@Composable

fun LineStylePreview(style: LineStyle, modifier: Modifier = Modifier) {

    val colors = LocalMongeColors.current

    Canvas(modifier = modifier.height(20.dp).fillMaxWidth()) {

        val y = size.height / 2

        Paint().apply {

            color = colors.background

            strokeWidth = 3f

            isAntiAlias = true

        }



        val pathEffect = when (style) {

            LineStyle.Solid -> null

            LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(12f, 8f))

            LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(2f, 8f))

            LineStyle.DashDot -> PathEffect.dashPathEffect(floatArrayOf(10f, 5f, 2f, 5f))

        }



        drawLine(

            color = colors.text,

            start = Offset(0f, y),

            end = Offset(size.width, y),

            strokeWidth = 3f,

            pathEffect = pathEffect

        )

    }

}

inline fun <T> SnapshotStateList<T>.replaceFirst(

    crossinline match: (T) -> Boolean,

    crossinline transform: (T) -> T

) {

    val idx = indexOfFirst(match)

    if (idx >= 0) this[idx] = transform(this[idx])

}

fun applyClipOverrideTo(obj: LinearObject2D?, value: Boolean?, state: MongeState) {

    when (obj) {

        is Line3DProjectionPudorys -> {

            val i  = state.lines3DPudorys.indexOfFirst { it.id == obj.id }

            if (i >= 0) state.lines3DPudorys[i] =

                state.lines3DPudorys[i].copy(clipLineX = value).withAxoVisibilityFrom(state.lines3DPudorys[i])



            val si = state.selectedLinesPudorys.indexOfFirst { it.id == obj.id }

            if (si >= 0) {

                val old = state.selectedLinesPudorys[si]

                if (old is Line3DProjectionPudorys)

                    state.selectedLinesPudorys[si] = old.copy(clipLineX = value).withAxoVisibilityFrom(old)

            }

        }



        is Line3DProjectionNarys -> {

            val i  = state.lines3DNarys.indexOfFirst { it.id == obj.id }

            if (i >= 0) state.lines3DNarys[i] =

                state.lines3DNarys[i].copy(clipLineX = value).withAxoVisibilityFrom(state.lines3DNarys[i])



            val si = state.selectedLinesNarys.indexOfFirst { it.id == obj.id }

            if (si >= 0) {

                val old = state.selectedLinesNarys[si]

                if (old is Line3DProjectionNarys)

                    state.selectedLinesNarys[si] = old.copy(clipLineX = value).withAxoVisibilityFrom(old)

            }

        }



        is PlaneTracePudorys -> {

            val i  = state.lineTracesPudorys.indexOfFirst { it.id == obj.id }

            if (i >= 0) state.lineTracesPudorys[i] =

                state.lineTracesPudorys[i].copy(clipLineX = value)



            val si = state.selectedTracesPudorys.indexOfFirst { it.id == obj.id }

            if (si >= 0) {

                val old = state.selectedTracesPudorys[si]

                state.selectedTracesPudorys[si] = old.copy(clipLineX = value)

            }

        }



        is PlaneTraceNarys -> {

            val i  = state.lineTracesNarys.indexOfFirst { it.id == obj.id }

            if (i >= 0) state.lineTracesNarys[i] =

                state.lineTracesNarys[i].copy(clipLineX = value)



            val si = state.selectedTracesNarys.indexOfFirst { it.id == obj.id }

            if (si >= 0) {

                val old = state.selectedTracesNarys[si]

                state.selectedTracesNarys[si] = old.copy(clipLineX = value)

            }

        }

        is HelpLineNarys -> {

            val i  = state.helpLineNarys.indexOfFirst { it.id == obj.id }

            if (i >= 0) state.helpLineNarys[i] =

                state.helpLineNarys[i].copy(clipLineX = value)



            val si = state.selectedLinesNarys.indexOfFirst { it.id == obj.id }

            if (si >= 0) {

                val old = state.selectedLinesNarys[si]

                if (old is HelpLineNarys)

                    state.selectedLinesNarys[si] = old.copy(clipLineX = value)

            }

        }



        is HelpLinePudorys -> {

            val i  = state.helpLinePudorys.indexOfFirst { it.id == obj.id }

            if (i >= 0) state.helpLinePudorys[i] =

                state.helpLinePudorys[i].copy(clipLineX = value)



            val si = state.selectedLinesPudorys.indexOfFirst { it.id == obj.id }

            if (si >= 0) {

                val old = state.selectedLinesPudorys[si]

                if (old is HelpLinePudorys)

                    state.selectedLinesPudorys[si] = old.copy(clipLineX = value)

            }

        }

        else -> return

    }

    commitSnapshot(state)

}





@OptIn(ExperimentalComposeUiApi::class)

@Composable

fun LineStyleSelector(

    current: LineStyle,

    onStyleChange: (LineStyle) -> Unit

) {

    val colors = LocalMongeColors.current

    var expanded by remember { mutableStateOf(false) }



    Box {

        // VĂ˝bÄ›rovĂ˝ box

        SkikoButton(

            onClick = { expanded = true },

            modifier = Modifier.width(90.dp),

            height = 32.dp,

            isSelected = expanded// nebo true, pokud chceĹˇ jinĂ˝ vzhled

        ) {

            LineStylePreview(

                style = current,

                modifier = Modifier

                    .width(120.dp)

                    .height(20.dp)

            )

        }



        // Menu s vlastnĂ­mi SkikoButtony

        DropdownMenu(

            expanded = expanded,

            onDismissRequest = { expanded = false },

            modifier = Modifier

                .background(colors.background)

                .width(120.dp)

        ) {

            LineStyle.entries.forEach { style ->

                SkikoButton(

                    onClick = {

                        onStyleChange(style)

                        expanded = false

                    },

                    modifier = Modifier.fillMaxWidth(),

                    height = 32.dp,

                    isSelected = style == current

                ) {

                    LineStylePreview(

                        style = style,

                        modifier = Modifier

                            .fillMaxWidth()

                            .height(20.dp)

                    )

                    Spacer(modifier = Modifier.width(10.dp))

                }

            }

        }

    }

}

fun deleteLine3D(state: MongeState,parentLine: Line3D,skipcommit: Boolean = false)

{

    if (isX12Line(parentLine) || isAxis(parentLine)) return



    val old = parentLine



    // 1) Najdi projekce (kopie kolekcĂ­ kvĹŻli bezpeÄŤĂ­ pĹ™i mazĂˇnĂ­)

    val pudorysy = state.lines3DPudorys.filter { it.parent === old || it.parent?.id == old.id || it.parentId == old.id }

    val narysy   = state.lines3DNarys  .filter { it.parent === old || it.parent?.id == old.id || it.parentId == old.id }

    val bokorysy = state.lines3DBokorys.filter { it.parent === old || it.parent?.id == old.id || it.parentId == old.id }

    val axo = state.lines3DAxo.filter { it.parent === old || it.parent?.id == old.id || it.parentId == old.id }

    val pudorysPoints = state.pointsPudorys.filter { isProjectedLinePointOf(it, old) }

    val narysPoints = state.pointsNarys.filter { isProjectedLinePointOf(it, old) }

    val bokorysPoints = state.pointsBokorys.filter { isProjectedLinePointOf(it, old) }

    val axoPoints = state.pointsAxo.filter { isProjectedLinePointOf(it, old) }



    // 2) ZruĹˇ label offsety a vĂ˝bÄ›r projekcĂ­

    pudorysy.forEach { l ->

        state.labelOffsetsPudorys.remove(l.id)

        state.selectedLinesPudorys.remove(l)

        if (state.rename.lineBeingRenamedPudorys === l) state.rename.lineBeingRenamedPudorys = null

    }

    narysy.forEach { l ->

        state.labelOffsetsNarys.remove(l.id)

        state.selectedLinesNarys.remove(l)

        if (state.rename.lineBeingRenamedNarys === l) state.rename.lineBeingRenamedNarys = null

    }

    bokorysy.forEach { l ->

        state.labelOffsetsBokorys.remove(l.id)

        state.selectedLinesBokorys.remove(l)

        if (state.rename.lineBeingRenamedBokorys === l) state.rename.lineBeingRenamedBokorys = null

    }

    axo.forEach { l ->

        state.labelOffsetsAxoLines.remove(l.id)

        state.selectedLinesAxo.remove(l)

    }

    pudorysPoints.forEach { p ->

        state.labelOffsetsPointsPudorys.remove(p.id)

        state.selectedPointsPudorys.remove(p)

        if (state.rename.pointBeingRenamed === p) state.rename.pointBeingRenamed = null

        if (state.rename.pointPudorysBeingRenamed === p) state.rename.pointPudorysBeingRenamed = null

    }

    narysPoints.forEach { p ->

        state.labelOffsetsPointsNarys.remove(p.id)

        state.selectedPointsNarys.remove(p)

        if (state.rename.pointBeingRenamed === p) state.rename.pointBeingRenamed = null

        if (state.rename.pointNarysBeingRenamed === p) state.rename.pointNarysBeingRenamed = null

    }

    bokorysPoints.forEach { p ->

        state.labelOffsetsPointsBokorys.remove(p.id)

        state.selectedPointsBokorys.remove(p)

        if (state.rename.pointBeingRenamed === p) state.rename.pointBeingRenamed = null

    }

    axoPoints.forEach { p ->

        state.labelOffsetsPointsAxo.remove(p.id)

        state.selectedPointsAxo.remove(p)

        if (state.rename.pointBeingRenamed === p) state.rename.pointBeingRenamed = null

    }





    // 3) SmaĹľ projekce ze seznamĹŻ

    state.lines3DPudorys.removeAll(pudorysy.toSet())

    state.lines3DNarys  .removeAll(narysy.toSet())

    state.lines3DBokorys .removeAll(bokorysy.toSet())

    state.lines3DAxo.removeAll(axo.toSet())

    state.pointsPudorys.removeAll(pudorysPoints.toSet())

    state.pointsNarys.removeAll(narysPoints.toSet())

    state.pointsBokorys.removeAll(bokorysPoints.toSet())

    state.pointsAxo.removeAll(axoPoints.toSet())



    // 4) SmaĹľ 3D parenta

    state.lines3D.removeAll { it.id == old.id }

    state.selectedLines3D.removeAll { it.id == old.id }



    // 5) Ăšklid pĹ™Ă­padnĂ˝ch stavĹŻ/renamĹŻ ukazujĂ­cĂ­ch na parenta

    if (state.rename.lineBeingRenamedPudorys?.parent === old || state.rename.lineBeingRenamedPudorys?.parent?.id == old.id) {

        state.rename.lineBeingRenamedPudorys = null

    }

    if (state.rename.lineBeingRenamedNarys?.parent === old || state.rename.lineBeingRenamedNarys?.parent?.id == old.id) {

        state.rename.lineBeingRenamedNarys = null

    }

    if (state.rename.lineBeingRenamedBokorys?.parent === old || state.rename.lineBeingRenamedBokorys?.parent?.id == old.id) {

        state.rename.lineBeingRenamedBokorys = null

    }



    if (!skipcommit) {

        commitSnapshot(state)

    }

    state.triggerRedraw++

}

fun deleteLine2D(state: MongeState,selectedLine: Line2DProjection){

    selectedLine.parent?.let { parent ->

        if (isX12Line(parent) || isAxis(parent)) return

    }

    if (isX12Projection(selectedLine) || isAxisProjection(selectedLine)) return





    val parent = selectedLine.parent

    if (parent != null) {

        deleteLine3D(state, parent)

        return

    } else {

        // Bez parenta â€“ smaĹľ jen tuhle projekci

        when (selectedLine) {

            is Line3DProjectionPudorys -> {

                state.labelOffsetsPudorys.remove(selectedLine.id)

                state.selectedLinesPudorys.remove(selectedLine)

                state.lines3DPudorys.removeAll { it.id == selectedLine.id }

            }

            is Line3DProjectionNarys -> {

                state.labelOffsetsNarys.remove(selectedLine.id)

                state.selectedLinesNarys.remove(selectedLine)

                state.lines3DNarys.removeAll { it.id == selectedLine.id }

            }

            is Line3DProjectionBokorys -> {

                state.labelOffsetsBokorys.remove(selectedLine.id)

                state.selectedLinesBokorys.remove(selectedLine)

                state.lines3DBokorys.removeAll { it.id == selectedLine.id }

            }

            is Line3DProjectionAxo -> {

                state.labelOffsetsAxoLines.remove(selectedLine.id)

                state.selectedLinesAxo.remove(selectedLine)

                state.lines3DAxo.removeAll { it.id == selectedLine.id }

            }

        }

    }

    commitSnapshot(state)



    state.triggerRedraw++

}

@Composable

fun lineEdit(state: MongeState){

    val selectedRaw = state.selectedLinesPudorys.firstOrNull()

        ?: state.selectedLinesNarys.firstOrNull()?: state.selectedLinesBokorys.firstOrNull() ?: state.selectedLinesAxo.firstOrNull()



// globĂˇlnĂ­ instance

    val current: LinearObject2D? = when (selectedRaw) {

        is Line3DProjectionPudorys -> state.lines3DPudorys.find { it.id == selectedRaw.id }

        is Line3DProjectionNarys -> state.lines3DNarys.find { it.id == selectedRaw.id }

        is Line3DProjectionBokorys -> state.lines3DBokorys.find { it.id == selectedRaw.id }

        is Line3DProjectionAxo -> when (selectedRaw.id) {
            XA_ID -> state.lines3DPudorys.find { it.id == "xp_ID" }
            YA_ID -> state.lines3DPudorys.find { it.id == "yp_ID" }
            ZA_ID -> state.lines3DNarys.find { it.id == "zn_ID" }
            else -> state.lines3DAxo.find { it.id == selectedRaw.id }
        }

        is PlaneTracePudorys -> state.lineTracesPudorys.find { it.id == selectedRaw.id }

        is PlaneTraceNarys -> state.lineTracesNarys.find { it.id == selectedRaw.id }

        is PlaneTraceBokorys -> state.lineTracesBokorys.find { it.id == selectedRaw.id }

        is HelpLinePudorys -> state.helpLinePudorys.find { it.id == selectedRaw.id }

        is HelpLineNarys -> state.helpLineNarys.find { it.id == selectedRaw.id }

        else -> null

    }

    when (current) {

        is Line2DProjection -> {



            val clipOverride: Boolean? = when (current) {

                is ClippableInPudorys -> current.clipLineX

                is ClippableInNarys -> current.clipLineX

                else -> null // nebo dÄ›diÄŤnĂ˝ default

            }



            val globalDefaultClip: Boolean = when (current) {

                is ClippableInPudorys -> state.defaultClipBelowX12Pudorys

                is ClippableInNarys -> state.defaultClipAboveX12Narys

                else -> true // fallback na pĹŻvodnĂ­ chovĂˇnĂ­

            }

            // AXO průměty os (xa/ya/za) mají vlastní nezávislou localColor (viz

            // Line3DProjectionAxo.color) - nesmí se proto editovat přes parenta,

            // jinak by přebarvení v AxoCanvas změnilo i barvu osy v OpenGL/Monge.

            // Pošli je do "standalone" editoru níž (else větev), stejně jako

            // projekce bez parenta.

            val isAxoAxisLine = current is Line3DProjectionAxo && isAxisProjection(current)

            if (current.parent != null && !isAxoAxisLine) {

                val parentLine = current.parent!!

                val isX12 = parentLine.id == "X12_ID" || isAxis(parentLine)

                val pProj = state.lines3DPudorys.firstOrNull { it.parent?.id == parentLine.id }

                val nProj = state.lines3DNarys.firstOrNull { it.parent?.id == parentLine.id }

                key(parentLine) {





                    EditableParentLineInfo(

                        line = parentLine,

                        canRename = !isX12,

                        onColorChange = { newColor ->



                            val updated = parentLine.copy(color = newColor)



                            // NahraÄŹ v sharedLines3D

                            val idx = state.lines3D.indexOfFirst { it.id == parentLine.id }

                            if (idx != -1) {

                                state.lines3D[idx] = updated

                            }



                            // Aktualizuj projekce s novĂ˝m parentem

                            state.lines3DPudorys.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }

                                ?.let { i ->

                                    state.lines3DPudorys[i] = state.lines3DPudorys[i].copy(parent = updated)

                                        .withAxoVisibilityFrom(state.lines3DPudorys[i])

                                }



                            state.lines3DNarys.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }

                                ?.let { i ->

                                    state.lines3DNarys[i] = state.lines3DNarys[i].copy(parent = updated)

                                        .withAxoVisibilityFrom(state.lines3DNarys[i])

                                }

                            state.lines3DBokorys.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }

                                ?.let { i ->

                                    state.lines3DBokorys[i] = state.lines3DBokorys[i].copy(parent = updated)

                                        .withAxoVisibilityFrom(state.lines3DBokorys[i])

                                }

                            state.lines3DAxo.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }

                                ?.let { i ->

                                    state.lines3DAxo[i] = state.lines3DAxo[i].copy(parent = updated)

                                        .withAxoVisibilityFrom(state.lines3DAxo[i])

                                }



                            val pudp = state.pointsPudorys.indexOfFirst { projectedLineIdOf(it) == updated.id }

                            if (pudp != -1 ){

                                state.pointsPudorys[pudp] = state.pointsPudorys[pudp].copy(localColor = updated.color)

                            }

                            val narp = state.pointsNarys.indexOfFirst { projectedLineIdOf(it) == updated.id }

                            if (narp != -1 ){

                                state.pointsNarys[narp] = state.pointsNarys[narp].copy(localColor = updated.color)

                            }

                            val bokp = state.pointsBokorys.indexOfFirst { projectedLineIdOf(it) == updated.id }

                            if (bokp != -1 ){

                                state.pointsBokorys[bokp] = state.pointsBokorys[bokp].copy(localColor = updated.color)

                            }

                            val ap = state.pointsAxo.indexOfFirst { projectedLineIdOf(it) == updated.id }

                            if (ap != -1 ){

                                state.pointsAxo[ap] = state.pointsAxo[ap].copy(localColor = updated.color)

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



                            // NahraÄŹ v sharedLines3D

                            val idx = state.lines3D.indexOfFirst { it.id == old.id }

                            if (idx != -1) {

                                state.lines3D[idx] = updated

                            }



                            // Aktualizuj projekce s novĂ˝m parentem

                            state.lines3DPudorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }

                                ?.let { i ->

                                    state.lines3DPudorys[i] = state.lines3DPudorys[i].copy(parent = updated)

                                        .withAxoVisibilityFrom(state.lines3DPudorys[i])

                                }



                            state.lines3DNarys.indexOfFirst { it.parent === old }.takeIf { it != -1 }

                                ?.let { i ->

                                    state.lines3DNarys[i] = state.lines3DNarys[i].copy(parent = updated)

                                        .withAxoVisibilityFrom(state.lines3DNarys[i])

                                }

                            state.lines3DBokorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }

                                ?.let { i ->

                                    state.lines3DBokorys[i] = state.lines3DBokorys[i].copy(parent = updated)

                                        .withAxoVisibilityFrom(state.lines3DBokorys[i])

                                }

                            state.lines3DAxo.indexOfFirst { it.parent === parentLine }.takeIf { it != -1 }

                                ?.let { i ->

                                    state.lines3DAxo[i] = state.lines3DAxo[i].copy(parent = updated)

                                        .withAxoVisibilityFrom(state.lines3DAxo[i])

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

                                    it.copy(clipLineX = next).withAxoVisibilityFrom(it)

                                }

                            }

                            commitSnapshot(state)



                        },

                        onClipOverrideNarysChange = { next ->

                            nProj?.let { np ->

                                state.lines3DNarys.replaceFirst({ it.id == np.id }) {

                                    it.copy(clipLineX = next).withAxoVisibilityFrom(it)

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



                            val cleanedName = newName.lineBaseNameForEditor()

                            val cleanedSup  = newSup.trim().ifEmpty { null }



                            // âś… vĹľdy pracuj pĹ™es ID, ne pĹ™es starou referenci

                            val idx = state.lines3D.indexOfFirst { it.id == parentLine.id }





                            val old = state.lines3D[idx]

                            val updated = old.copy(

                                name = cleanedName,

                                superscript = cleanedSup

                            )

                            state.lines3D[idx] = updated



                            // âś… projekce hledat podle parentId (ne === old)

                            val pIndex = state.lines3DPudorys.indexOfFirst { it.parent?.id == updated.id }

                            if (pIndex != -1) {

                                state.lines3DPudorys[pIndex] = state.lines3DPudorys[pIndex].copy(

                                    localName = cleanedName.withSuffixOnce("â‚"),

                                    localSuperscript = cleanedSup,

                                    parent = updated

                                ).withAxoVisibilityFrom(state.lines3DPudorys[pIndex])

                            }



                            val nIndex = state.lines3DNarys.indexOfFirst { it.parent?.id == updated.id }

                            if (nIndex != -1) {

                                state.lines3DNarys[nIndex] = state.lines3DNarys[nIndex].copy(

                                    localName = cleanedName.withSuffixOnce("â‚‚"),

                                    localSuperscript = cleanedSup,

                                    parent = updated

                                ).withAxoVisibilityFrom(state.lines3DNarys[nIndex])

                            }

                            val bIndex = state.lines3DBokorys.indexOfFirst { it.parent?.id == updated.id }

                            if (bIndex != -1) {

                                state.lines3DBokorys[bIndex] = state.lines3DBokorys[bIndex].copy(

                                    localName = cleanedName.withSuffixOnce("â‚"),

                                    localSuperscript = cleanedSup,

                                    parent = updated

                                ).withAxoVisibilityFrom(state.lines3DBokorys[bIndex])

                            }

                            val aIndex = state.lines3DAxo.indexOfFirst { it.parent?.id == updated.id }

                            if (aIndex != -1) {

                                state.lines3DAxo[aIndex] = state.lines3DAxo[aIndex].copy(

                                    localName = cleanedName.withSuffixOnce("ₐ"),

                                    localSuperscript = cleanedSup,

                                    parent= updated

                                ).withAxoVisibilityFrom(state.lines3DAxo[aIndex])

                            }

                            val pudp = state.pointsPudorys.indexOfFirst { projectedLineIdOf(it) == updated.id }

                            if (pudp != -1 ){

                                state.pointsPudorys[pudp] = state.pointsPudorys[pudp].copy(

                                    name = cleanedName,

                                    localSuperscript = cleanedSup,



                                )

                            }

                            val narp = state.pointsNarys.indexOfFirst { projectedLineIdOf(it) == updated.id }

                            if (narp != -1 ){

                                state.pointsNarys[narp] = state.pointsNarys[narp].copy(

                                    name = cleanedName,

                                    localSuperscript = cleanedSup,

                                )

                            }

                            val bokp = state.pointsBokorys.indexOfFirst { projectedLineIdOf(it) == updated.id }

                            if (bokp != -1 ){

                                state.pointsBokorys[bokp] = state.pointsBokorys[bokp].copy(

                                    name = cleanedName,

                                    localSuperscript = cleanedSup,

                                )

                            }

                            val ap = state.pointsAxo.indexOfFirst { projectedLineIdOf(it) == updated.id }

                            if (ap != -1) {

                                state.pointsAxo[ap] = state.pointsAxo[ap].copy(

                                    name = cleanedName,

                                    localSuperscript = cleanedSup,

                                )

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

            } else {



                key(current.id) {

                    val selectedLine = current

                    EditableLineProjectionInfo(

                        line = selectedLine,

                        onColorChange = { newColor ->

                            when (selectedLine) {

                                is Line3DProjectionPudorys -> {

                                    val updated = selectedLine.copy(localColor = newColor).withAxoVisibilityFrom(selectedLine)

                                    state.lines3DPudorys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesPudorys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLinePudorys else it

                                    }

                                }



                                is Line3DProjectionNarys -> {

                                    val updated = selectedLine.copy(localColor = newColor).withAxoVisibilityFrom(selectedLine)

                                    state.lines3DNarys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesNarys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLineNarys else it

                                    }

                                }

                                is Line3DProjectionBokorys -> {

                                    val updated = selectedLine.copy(localColor = newColor).withAxoVisibilityFrom(selectedLine)

                                    state.lines3DBokorys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesBokorys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLineBokorys else it

                                    }

                                }

                                is Line3DProjectionAxo -> {

                                    val updated = selectedLine.copy(localColor = newColor).withAxoVisibilityFrom(selectedLine)

                                    state.lines3DAxo.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesAxo.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                }

                            }

                            commitSnapshot(state)



                        },

                        onWidthChange = { newWidth ->

                            when (selectedLine) {



                                /* ---- PĹŻdorys ---- */

                                is Line3DProjectionPudorys -> {

                                    val updated = selectedLine.copy(localStrokeWidth = newWidth).withAxoVisibilityFrom(selectedLine)



                                    /* pĹ™epiĹˇ v datovĂ©m seznamu */

                                    state.lines3DPudorys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }



                                    /* pĹ™epiĹˇ i ve vĂ˝bÄ›ru (instance) */

                                    state.selectedLinesPudorys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLinePudorys else it

                                    }

                                }



                                /* ---- NĂˇrys ---- */

                                is Line3DProjectionNarys -> {

                                    val updated = selectedLine.copy(localStrokeWidth = newWidth).withAxoVisibilityFrom(selectedLine)



                                    state.lines3DNarys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }



                                    state.selectedLinesNarys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLineNarys else it

                                    }

                                }

                                is Line3DProjectionBokorys -> {

                                    val updated = selectedLine.copy(localStrokeWidth = newWidth).withAxoVisibilityFrom(selectedLine)

                                    state.lines3DBokorys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesBokorys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLineBokorys else it

                                    }

                                }

                                is Line3DProjectionAxo -> {

                                    val updated = selectedLine.copy(localStrokeWidth = newWidth).withAxoVisibilityFrom(selectedLine)

                                    state.lines3DAxo.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesAxo.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                }

                            }

                        },

                        onStyleChange = { newStyle ->



                            when (selectedLine) {



                                /* ---- PĹŻdorys ---- */

                                is Line3DProjectionPudorys -> {

                                    val updated = selectedLine.copy(localLineStyle = newStyle).withAxoVisibilityFrom(selectedLine)



                                    // datovĂ˝ seznam

                                    state.lines3DPudorys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    // instance ve vĂ˝bÄ›ru

                                    state.selectedLinesPudorys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLinePudorys else it

                                    }

                                }



                                /* ---- NĂˇrys ---- */

                                is Line3DProjectionNarys -> {

                                    val updated = selectedLine.copy(localLineStyle = newStyle).withAxoVisibilityFrom(selectedLine)



                                    state.lines3DNarys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesNarys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLineNarys else it

                                    }

                                }

                                /* ----Bokorys---- */

                                is Line3DProjectionBokorys -> {

                                    val updated = selectedLine.copy(localLineStyle = newStyle).withAxoVisibilityFrom(selectedLine)



                                    state.lines3DBokorys.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesBokorys.replaceAll {

                                        if (it.id == updated.id) updated as NamedLineBokorys else it

                                    }

                                }

                                is Line3DProjectionAxo -> {

                                    val updated = selectedLine.copy(localLineStyle = newStyle).withAxoVisibilityFrom(selectedLine)



                                    state.lines3DAxo.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                    state.selectedLinesAxo.replaceAll {

                                        if (it.id == updated.id) updated else it

                                    }

                                }

                            }

                            commitSnapshot(state)



                        },

                        onSelectProjection = {

                            if(state.projectionMode == ProjectionMode.KOTO) {

                                when (selectedLine) {

                                is Line3DProjectionPudorys -> {

                                    state.linefrom2points = selectedLine

                                    setProjectionPhase("picking_line_points",state)



                                }

                                    else -> {}

                                }

                            }

                            else if (state.projectionMode == ProjectionMode.AXO) {

                                state.drawobjects = Mongeobjects.NONE

                                state.reusingExistingProjection = true

                                when (current) {

                                    is Line3DProjectionPudorys -> Unit

                                    is Line3DProjectionNarys -> Unit

                                    is Line3DProjectionBokorys -> Unit

                                    is Line3DProjectionAxo -> Unit

                                }

                            }

                            else {

                                startLine3DCompletion(state)

                                state.consInfo.value = "Vyberte druhý průmět přímky"

                            }

                        },

                        onAddProjection = {

                            when (state.projectionMode){

                                ProjectionMode.MONGE ->{CompleteLineAdd(state, current)}

                                ProjectionMode.AXO -> {

                                        state.drawobjects = Mongeobjects.NONE

                                        state.reusingExistingProjection = false

                                        when (current){

                                            is Line3DProjectionPudorys -> {Unit}

                                            is Line3DProjectionNarys->{ Unit }

                                            is Line3DProjectionBokorys->{ Unit }

                                            is Line3DProjectionAxo ->{ Unit }

                                    }

                                }



                                else -> {}

                            }







                                          },

                        clipOverride = clipOverride,           // z tvojĂ­ projekce

                        globalDefaultClip = globalDefaultClip,

                        onClipOverrideChange = { newVal ->

                            applyClipOverrideTo(current, newVal, state)

                            state.triggerRedraw

                        },

                        onDelete = {

                            deleteLine2D(state, selectedLine = selectedLine)

                            clearSelection(state)

                        },

                        state = state,

                        onApply = { newName, newSup ->



                            val base = newName.lineBaseNameForEditor()

                            val sup  = newSup.trim()



                            // 1) Aktualizuj parent, pokud existuje (ideĂˇlnÄ› copy, ale ty tu parent mutujeĹˇ)

                            selectedLine.parent?.let { parent ->

                                parent.name = base

                                parent.superscript = sup

                            }



                            // 2) VytvoĹ™ updatedCurrent (name + sup najednou)

                            val updatedCurrent = when (selectedLine) {

                                is Line3DProjectionPudorys -> selectedLine.copy(

                                    localName = "$base₁",

                                    localSuperscript = sup

                                ).withAxoVisibilityFrom(selectedLine)

                                is Line3DProjectionNarys -> selectedLine.copy(

                                    localName = "$base₂",

                                    localSuperscript = sup

                                ).withAxoVisibilityFrom(selectedLine)

                                is Line3DProjectionBokorys -> selectedLine.copy(

                                    localName = "$base₃",

                                    localSuperscript = sup

                                ).withAxoVisibilityFrom(selectedLine)

                                is Line3DProjectionAxo -> selectedLine.copy(

                                    localName = axoLocalNameForEditor(base),

                                    localSuperscript = sup

                                ).withAxoVisibilityFrom(selectedLine)

                            }



                            // 3) ZapiĹˇ do datovĂ˝ch seznamĹŻ

                            when (updatedCurrent) {

                                is Line3DProjectionPudorys ->

                                    state.lines3DPudorys.replaceAll { if (it.id == updatedCurrent.id) updatedCurrent else it }

                                is Line3DProjectionNarys ->

                                    state.lines3DNarys.replaceAll { if (it.id == updatedCurrent.id) updatedCurrent else it }

                                is Line3DProjectionBokorys ->

                                    state.lines3DBokorys.replaceAll { if (it.id == updatedCurrent.id) updatedCurrent else it }

                                is Line3DProjectionAxo ->

                                    state.lines3DAxo.replaceAll { if (it.id == updatedCurrent.id) updatedCurrent else it }



                            }



                            // 4) Najdi a pĹ™epiĹˇ DRUHOU projekci podle parentId (ne === reference)

                            val parentId = selectedLine.parent?.id

                            if (parentId != null) {

                                when (selectedLine) {

                                    is Line3DProjectionPudorys -> {

                                        val othern = state.lines3DNarys.find { it.parent?.id == parentId }

                                        if (othern != null) {

                                            val updatedOther = othern.copy(

                                                localName = "$base₂",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(othern)

                                            state.lines3DNarys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                        val otherb = state.lines3DBokorys.find { it.parent?.id == parentId }

                                        if (otherb != null) {

                                            val updatedOther = otherb.copy(

                                                localName = "$base₃",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(otherb)

                                            state.lines3DBokorys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                        val othera = state.lines3DAxo.find { it.parent?.id == parentId }

                                        if (othera != null) {

                                            val updatedOther = othera.copy(

                                                localName = base.withSuffixOnce("ₐ"),

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(othera)

                                            state.lines3DAxo.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                    }

                                    is Line3DProjectionNarys -> {

                                        val other = state.lines3DPudorys.find { it.parent?.id == parentId }

                                        if (other != null) {

                                            val updatedOther = other.copy(

                                                localName = "$base₁",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(other)

                                            state.lines3DPudorys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                        val otherb = state.lines3DBokorys.find { it.parent?.id == parentId }

                                        if (otherb != null) {

                                            val updatedOther = otherb.copy(

                                                localName = "$base₃",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(otherb)

                                            state.lines3DBokorys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                        val othera = state.lines3DAxo.find { it.parent?.id == parentId }

                                        if (othera != null) {

                                            val updatedOther = othera.copy(

                                                localName = base.withSuffixOnce("ₐ"),

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(othera)

                                            state.lines3DAxo.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                    }



                                    is Line3DProjectionBokorys -> {

                                        val other = state.lines3DPudorys.find { it.parent?.id == parentId }

                                        if (other != null) {

                                            val updatedOther = other.copy(

                                                localName = "$base₁",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(other)

                                            state.lines3DPudorys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                        val othern = state.lines3DNarys.find { it.parent?.id == parentId }

                                        if (othern != null) {

                                            val updatedOther = othern.copy(

                                                localName = "$base₂",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(othern)

                                            state.lines3DNarys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                        val othera = state.lines3DAxo.find { it.parent?.id == parentId }

                                        if (othera != null) {

                                            val updatedOther = othera.copy(

                                                localName = base.withSuffixOnce("ₐ"),

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(othera)

                                            state.lines3DAxo.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                    }

                                    is Line3DProjectionAxo -> {

                                        val othern = state.lines3DNarys.find { it.parent?.id == parentId }

                                        if (othern != null) {

                                            val updatedOther = othern.copy(

                                                localName = "$base₂",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(othern)

                                            state.lines3DNarys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                        val otherb = state.lines3DBokorys.find { it.parent?.id == parentId }

                                        if (otherb != null) {

                                            val updatedOther = otherb.copy(

                                                localName = "$base₃",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(otherb)

                                            state.lines3DBokorys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                        val other = state.lines3DPudorys.find { it.parent?.id == parentId }

                                        if (other != null) {

                                            val updatedOther = other.copy(

                                                localName = "$base₁",

                                                localSuperscript = sup

                                            ).withAxoVisibilityFrom(other)

                                            state.lines3DPudorys.replaceAll { if (it.id == updatedOther.id) updatedOther else it }

                                        }

                                    }

                                }

                            }



                            // 5) Aktualizuj vĂ˝bÄ›r (selectedLines...) pro aktuĂˇlnĂ­ projekci

                            when (updatedCurrent) {

                                is Line3DProjectionPudorys -> {

                                    state.selectedLinesPudorys.replaceAll {

                                        if (it.id == updatedCurrent.id) updatedCurrent as NamedLinePudorys else it

                                    }

                                }

                                is Line3DProjectionNarys -> {

                                    state.selectedLinesNarys.replaceAll {

                                        if (it.id == updatedCurrent.id) updatedCurrent as NamedLineNarys else it

                                    }

                                }

                                is Line3DProjectionBokorys -> {

                                    state.selectedLinesBokorys.replaceAll {

                                        if (it.id == updatedCurrent.id) updatedCurrent as NamedLineBokorys else it

                                    }

                                }



                                is Line3DProjectionAxo -> {

                                    state.selectedLinesAxo.replaceAll {

                                        if (it.id == updatedCurrent.id) updatedCurrent else it

                                    }

                                }

                            }

                            commitSnapshot(state)

                            state.triggerRedraw++

                        },

                        uiScale = SettingsManager.current.UIscale/75f

                    )

                }

            }

        }

        is Trace2DProjection -> {

            // 1) vĹľdy ÄŤerstvĂˇ instance ze stavu

            val fresh: Trace2DProjection = when (current) {

                is PlaneTracePudorys -> state.lineTracesPudorys.firstOrNull { it.id == current.id }

                is PlaneTraceNarys -> state.lineTracesNarys.firstOrNull { it.id == current.id }

                is PlaneTraceBokorys -> state.lineTracesBokorys.firstOrNull { it.id == current.id }

            } ?: return



            // 2) props pro UI

            val clipOverride: Boolean? = when (fresh) {

                is PlaneTracePudorys -> fresh.clipLineX

                is PlaneTraceNarys -> fresh.clipLineX



                else -> {false}

            }

            val globalDefault: Boolean = when (fresh) {

                is PlaneTracePudorys -> state.defaultClipBelowX12Pudorys

                is PlaneTraceNarys -> state.defaultClipAboveX12Narys

                is PlaneTraceBokorys -> false

            }



            EditableTraceProjectionInfo(

                trace = fresh,



                onRename = { newName ->

                    val base = newName.removeSuffix("â‚").removeSuffix("â‚‚")

                    when (fresh) {

                        is PlaneTracePudorys -> {

                            val updated = fresh.copy(localName = base)

                            state.lineTracesPudorys.replaceAll { if (it.id == updated.id) updated else it }

                            state.selectedTracesPudorys.replaceAll { if (it.id == updated.id) updated else it }

                        }



                        is PlaneTraceNarys -> {

                            val updated = fresh.copy(localName = base)

                            state.lineTracesNarys.replaceAll { if (it.id == updated.id) updated else it }

                            state.selectedTracesNarys.replaceAll { if (it.id == updated.id) updated else it }

                        }



                        is PlaneTraceBokorys -> {

                            val updated = fresh.copy(localName = base)

                            state.lineTracesBokorys.replaceAll { if (it.id == updated.id) updated else it }

                            state.selectedTracesBokorys.replaceAll { if (it.id == updated.id) updated else it }

                        }

                    }

                    commitSnapshot(state)



                },



                onColorChange = { newColor ->

                    when (fresh) {

                        is PlaneTracePudorys -> {

                            val updated = fresh.copy(localColor = newColor)

                            state.lineTracesPudorys.replaceAll { if (it.id == updated.id) updated else it }

                            state.selectedTracesPudorys.replaceAll { if (it.id == updated.id) updated else it }

                        }



                        is PlaneTraceNarys -> {

                            val updated = fresh.copy(localColor = newColor)

                            state.lineTracesNarys.replaceAll { if (it.id == updated.id) updated else it }

                            state.selectedTracesNarys.replaceAll { if (it.id == updated.id) updated else it }

                        }



                        is PlaneTraceBokorys -> {

                            val updated = fresh.copy(localColor = newColor)

                            state.lineTracesBokorys.replaceAll { if (it.id == updated.id) updated else it }

                            state.selectedTracesBokorys.replaceAll { if (it.id == updated.id) updated else it }

                        }

                    }

                    commitSnapshot(state)



                },



                onWidthChange = { newWidth ->

                    when (fresh) {

                        is PlaneTracePudorys -> {

                            val upd = fresh.copy(localStrokeWidth = newWidth)

                            state.lineTracesPudorys.replaceAll { if (it.id == upd.id) upd else it }

                            state.selectedTracesPudorys.replaceAll { if (it.id == upd.id) upd else it }

                        }



                        is PlaneTraceNarys -> {

                            val upd = fresh.copy(localStrokeWidth = newWidth)

                            state.lineTracesNarys.replaceAll { if (it.id == upd.id) upd else it }

                            state.selectedTracesNarys.replaceAll { if (it.id == upd.id) upd else it }

                        }



                        is PlaneTraceBokorys -> {

                            val upd = fresh.copy(localStrokeWidth = newWidth)

                            state.lineTracesBokorys.replaceAll { if (it.id == upd.id) upd else it }

                            state.selectedTracesBokorys.replaceAll { if (it.id == upd.id) upd else it }

                        }

                    }

                },



                onStyleChange = { newStyle ->

                    when (fresh) {

                        is PlaneTracePudorys -> {

                            val upd = fresh.copy(localLineStyle = newStyle)

                            state.lineTracesPudorys.replaceAll { if (it.id == upd.id) upd else it }

                            state.selectedTracesPudorys.replaceAll { if (it.id == upd.id) upd else it }

                        }



                        is PlaneTraceNarys -> {

                            val upd = fresh.copy(localLineStyle = newStyle)

                            state.lineTracesNarys.replaceAll { if (it.id == upd.id) upd else it }

                            state.selectedTracesNarys.replaceAll { if (it.id == upd.id) upd else it }

                        }



                        is PlaneTraceBokorys -> {

                            val upd = fresh.copy(localLineStyle = newStyle)

                            state.lineTracesBokorys.replaceAll { if (it.id == upd.id) upd else it }

                            state.selectedTracesBokorys.replaceAll { if (it.id == upd.id) upd else it }

                        }

                    }

                    commitSnapshot(state)



                },

                onAddProjection = {

                    if(state.projectionMode== ProjectionMode.KOTO) {

                        when (fresh) {

                            is PlaneTracePudorys -> {

                        beginPlaneFromTracePickPoint(fresh,state, planeName = fresh.localName?:"", superscript = fresh.superscript)}

                            else -> {}}

                    }

                    else if (state.projectionMode == ProjectionMode.AXO) {



                    }

                    else {

                    CompletePlaneAdd(state, fresh) }},



                clipOverride = clipOverride,

                globalDefaultClip = globalDefault,

                onClipOverrideChange = { next ->

                    applyClipOverrideTo(fresh as LinearObject2D, next, state)

                    commitSnapshot(state)



                },

                axoClipContent = {

                    val traceUi = remember(SettingsManager.current.UIscale) {

                        UiScale(SettingsManager.current.UIscale / 75f)

                    }

                    when (fresh) {

                        is PlaneTracePudorys -> {

                            TriStateClipSettingRow("Osa X", fresh.clipLineX, traceUi, globalDefault = state.defaultClipBelowX12Pudorys) { next ->

                                val updated = fresh.copy(clipLineX = next)

                                state.lineTracesPudorys.replaceAll { if (it.id == updated.id) updated else it }

                                state.selectedTracesPudorys.replaceAll { if (it.id == updated.id) updated else it }

                                commitSnapshot(state)

                            }

                            TriStateClipSettingRow("Osa Y", fresh.clipLineY, traceUi, globalDefault = state.defaultClipLeftOfYAxisPudorys) { next ->

                                val updated = fresh.copy(clipLineY = next)

                                state.lineTracesPudorys.replaceAll { if (it.id == updated.id) updated else it }

                                state.selectedTracesPudorys.replaceAll { if (it.id == updated.id) updated else it }

                                commitSnapshot(state)

                            }

                        }



                        is PlaneTraceNarys -> {

                            TriStateClipSettingRow("Osa X", fresh.clipLineX, traceUi, globalDefault = state.defaultClipAboveX12Narys) { next ->

                                val updated = fresh.copy(clipLineX = next)

                                state.lineTracesNarys.replaceAll { if (it.id == updated.id) updated else it }

                                state.selectedTracesNarys.replaceAll { if (it.id == updated.id) updated else it }

                                commitSnapshot(state)

                            }

                            TriStateClipSettingRow("Osa Z", fresh.clipLineZ, traceUi, globalDefault = state.defaultClipLeftOfZAxisNarys) { next ->

                                val updated = fresh.copy(clipLineZ = next)

                                state.lineTracesNarys.replaceAll { if (it.id == updated.id) updated else it }

                                state.selectedTracesNarys.replaceAll { if (it.id == updated.id) updated else it }

                                commitSnapshot(state)

                            }

                        }



                        is PlaneTraceBokorys -> {

                            TriStateClipSettingRow("Osa Y", fresh.clipLineY, traceUi, globalDefault = state.defaultClipBelowYAxisBokorys) { next ->

                                val updated = fresh.copy(clipLineY = next)

                                state.lineTracesBokorys.replaceAll { if (it.id == updated.id) updated else it }

                                state.selectedTracesBokorys.replaceAll { if (it.id == updated.id) updated else it }

                                commitSnapshot(state)

                            }

                            TriStateClipSettingRow("Osa Z", fresh.clipLineZ, traceUi, globalDefault = state.defaultClipLeftOfZAxisBokorys) { next ->

                                val updated = fresh.copy(clipLineZ = next)

                                state.lineTracesBokorys.replaceAll { if (it.id == updated.id) updated else it }

                                state.selectedTracesBokorys.replaceAll { if (it.id == updated.id) updated else it }

                                commitSnapshot(state)

                            }

                        }

                    }

                },

                onDelete = {

                    deleteTrace2D(state, fresh)

                    clearSelection(state)

                },

                state = state,

                uiScale = SettingsManager.current.UIscale/75f



            )

        }

        is HelpLineNarys -> {

            val clipOverride: Boolean? = current.clipLineX

            val globalDefaultClip: Boolean = state.defaultClipAboveX12Narys



            key(current.id) {

                val selectedLine = current

                EditableHelpLineNarysInfo(

                    line = selectedLine,

                    onColorChange = { newColor ->

                        val updated = selectedLine.copy(localColor = newColor)

                        state.helpLineNarys.replaceAll { if (it.id == updated.id) updated else it }

                        commitSnapshot(state)



                    },

                    onWidthChange = { newWidth ->

                        val updated = selectedLine.copy(localStrokeWidth = newWidth)



                        state.helpLineNarys.replaceAll { if (it.id == updated.id) updated else it }

                    },



                    onStyleChange = { newStyle ->

                        val updated = selectedLine.copy(localLineStyle = newStyle)

                        // datovĂ˝ seznam

                        state.helpLineNarys.replaceAll { if (it.id == updated.id) updated else it }

                        commitSnapshot(state)



                    },

                    clipOverride = clipOverride,           // z tvojĂ­ projekce

                    globalDefaultClip = globalDefaultClip,

                    onClipOverrideChange = { newVal ->

                        applyClipOverrideTo(current, newVal, state)

                        state.triggerRedraw++

                    },

                    onDelete = {

                        deleteHelpLineNarys(state, selectedLine)

                        clearSelection(state)

                    },

                    onApply = { name, upper, lower ->



                        val idx = state.helpLineNarys.indexOfFirst { it.id ==current.id }

                        if (idx >= 0) {

                            state.helpLineNarys[idx] = state.helpLineNarys[idx].copy(

                                name = name.ifBlank { null },

                                localSuperscript = upper.ifBlank { null },

                                lowerSuperscript = lower.ifBlank { null }

                            )

                        }

                        commitSnapshot(state)

                    },

                    state=state,

                    uiScale = SettingsManager.current.UIscale/75f







                )



            }

        }

        is HelpLinePudorys -> {

            val clipOverride: Boolean? = if (current.id == "axisX") current.clipLineY else current.clipLineX

            val globalDefaultClip: Boolean =
                if (current.id == "axisX") state.defaultClipLeftOfYAxisPudorys else state.defaultClipBelowX12Pudorys



            key(current.id) {

                val selectedLine = current

                EditableHelpLinePudorysInfo(

                    line = selectedLine,

                    onColorChange = { newColor ->

                        val updated = selectedLine.copy(localColor = newColor)

                        state.helpLinePudorys.replaceAll { if (it.id == updated.id) updated else it }

                        commitSnapshot(state)



                    },

                    onWidthChange = { newWidth ->

                        val updated = selectedLine.copy(localStrokeWidth = newWidth)



                        state.helpLinePudorys.replaceAll { if (it.id == updated.id) updated else it }

                    },



                    onStyleChange = { newStyle ->

                        val updated = selectedLine.copy(localLineStyle = newStyle)

                        // datovĂ˝ seznam

                        state.helpLinePudorys.replaceAll { if (it.id == updated.id) updated else it }

                        commitSnapshot(state)



                    },

                    clipOverride = clipOverride,           // z tvojĂ­ projekce

                    globalDefaultClip = globalDefaultClip,

                    onClipOverrideChange = { newVal ->

                        if (current.id == "axisX") {
                            state.helpLinePudorys.replaceAll {
                                if (it.id == current.id) it.copy(clipLineY = newVal) else it
                            }
                            state.selectedLinesPudorys.replaceAll {
                                if (it.id == current.id && it is HelpLinePudorys) it.copy(clipLineY = newVal) else it
                            }
                        } else {
                            applyClipOverrideTo(current, newVal, state)
                        }

                        state.triggerRedraw++

                    },

                    onDelete = {

                        deleteHelpLinePudorys(state, selectedLine)

                        clearSelection(state)

                    },

                    onApply = { name, upper, lower ->

                        val idx = state.helpLinePudorys.indexOfFirst { it.id == current.id }

                        if (idx >= 0) {

                            state.helpLinePudorys[idx] =   state.helpLinePudorys[idx].copy(

                                name = name.ifBlank { null },

                                localSuperscript = upper.ifBlank { null },

                                lowerSuperscript = lower.ifBlank { null }

                            )

                        }

                        commitSnapshot(state)

                    },

                    state = state,

                    uiScale = SettingsManager.current.UIscale/75f







                )



            }

        }

    }

}
