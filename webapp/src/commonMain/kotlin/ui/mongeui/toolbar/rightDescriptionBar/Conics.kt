package ui.mongeui.toolbar.rightDescriptionBar

import utils.replaceAll
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.LineStyle
import model.LocalMongeColors
import model.ProjectionMode
import monge.input.ConicArcs.startConicVisibility
import monge.input.ConicArcs.startSingleConicVisibility
import model.classes.*
import serialization.SettingsManager
import monge.input.planeobjects.conicsections.*
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.SkikoButton
import kotlin.math.abs

private fun conicDiscriminant(a: Float, b: Float, c: Float): Float = b * b - 4f * a * c

private fun isHyperbolaCoefficients(a: Float, b: Float, c: Float): Boolean =
    conicDiscriminant(a, b, c) > 1e-5f

/**
 * Scale-invariantní test hyperboly z kvadratických koeficientů.
 * Absolutní práh selhává u velkých hyperbol: po [canonizeHyperbolaFrame] jsou
 * koeficienty řádu 1/a², 1/b², takže diskriminant 4/(a²b²) klesne pod 1e-5 a
 * hyperbola se chybně klasifikuje jako elipsa. Práh proto škálujeme velikostí
 * koeficientů.
 */
private fun isHyperbolaCoefficientsScaled(a: Float, b: Float, c: Float): Boolean {
    val scale = maxOf(abs(a), abs(b), abs(c))
    if (!scale.isFinite() || scale < 1e-20f) return false
    return conicDiscriminant(a, b, c) > 1e-6f * scale * scale
}

private fun isHyperbola3D(conic: ConicSection3D): Boolean {
    val coeffs = Matrix3x3.toCoefficients(conic.matrix)
    return isHyperbolaCoefficientsScaled(coeffs[0], coeffs[1], coeffs[2])
}

private data class ConicVisibilityButtonSpec(
    val label: String,
    val prefix: String,
    val parentId: String? = null,
    val conicId: String? = null
)

private fun conicVisibilityButtonAllowed(
    state: MongeState,
    prefix: String,
    showInAxo: Boolean
): Boolean = when (state.projectionMode) {
    ProjectionMode.AXO -> showInAxo
    ProjectionMode.MONGE, ProjectionMode.KOTO -> prefix == "pudorys" || prefix == "narys"
    else -> false
}

private fun parentConicVisibilityButtons(
    state: MongeState,
    parentId: String,
    pudorys: ConicSectionPudorys?,
    narys: ConicSectionNarys?,
    bokorys: ConicSectionBokorys?,
    axo: ConicSectionAxo?
): List<ConicVisibilityButtonSpec> {
    fun item(label: String, prefix: String, conic: ConicSection2D?): ConicVisibilityButtonSpec? {
        if (conic == null || !conicVisibilityButtonAllowed(state, prefix, conic.showInAxo)) return null
        return ConicVisibilityButtonSpec(label = label, prefix = prefix, parentId = parentId)
    }

    return listOfNotNull(
        item("Půdorys", "pudorys", pudorys),
        item("Nárys", "narys", narys),
        item("Bokorys", "bokorys", bokorys),
        item("Axo", "axo", axo)
    )
}

private fun singleConicVisibilityButtons(
    state: MongeState,
    conic: ConicSection2D
): List<ConicVisibilityButtonSpec> {
    val (label, prefix) = when (conic) {
        is ConicSectionPudorys -> "Půdorys" to "pudorys"
        is ConicSectionNarys -> "Nárys" to "narys"
        is ConicSectionBokorys -> "Bokorys" to "bokorys"
        is ConicSectionAxo -> "Axo" to "axo"
    }
    if (!conicVisibilityButtonAllowed(state, prefix, conic.showInAxo)) return emptyList()
    return listOf(ConicVisibilityButtonSpec(label = label, prefix = prefix, conicId = conic.id))
}

@Composable
private fun ConicVisibilityButtons(
    state: MongeState,
    ui: UiScale,
    buttons: List<ConicVisibilityButtonSpec>
) {
    if (buttons.isEmpty()) return
    val colors = LocalMongeColors.current

    MongeInspectorSection("Viditelnost") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ui.dp(6f)),
            horizontalAlignment = Alignment.End
        ) {
            buttons.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ui.dp(6f), Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems.forEach { item ->
                        SkikoButton(
                            onClick = {
                                item.parentId?.let { state.startConicVisibility(it, item.prefix) }
                                    ?: item.conicId?.let { state.startSingleConicVisibility(it, item.prefix) }
                            },
                            width = ui.dp(102f),
                            height = ui.dp(32f)
                        ) {
                            Text(
                                item.label,
                                fontSize = ui.sp(12f),
                                color = colors.buttonColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditableConicInfo(
    conic: ConicSection2D,
    onApplyName: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    on3DStyleChange: (LineStyle) -> Unit = {},
    onPudorysStyleChange: (LineStyle) -> Unit = {},
    onNarysStyleChange: (LineStyle) -> Unit = {},
    onBokorysStyleChange: (LineStyle) -> Unit = {},
    onShowPudorysProjectionChange: (Boolean) -> Unit = {},
    onShowNarysProjectionChange: (Boolean) -> Unit = {},
    onShowBokorysProjectionChange: (Boolean) -> Unit = {},
    onShowAxoProjectionChange: (Boolean) -> Unit = {},
    inputPointsPudorys: Map<String, Triple<Offset, Offset, Offset>>,
    inputPointsNarys: Map<String, Triple<Offset, Offset, Offset>>,
    inputPointsBokorys: Map<String, Triple<Offset, Offset, Offset>>,
    inputPointsAxo: Map<String, Triple<Offset, Offset, Offset>>,
    hyperbolaInputsPudorys: Map<String, ConicInputHyperbolaPudorys>,
    hyperbolaInputsNarys: Map<String, ConicInputHyperbolaNarys>,
    hyperbolaInputsBokorys: Map<String, ConicInputHyperbolaBokorys>,
    hyperbolaInputsAxo: Map<String, ConicInputHyperbolaAxo>,
    onAddProjection: () -> Unit,
    onDelete: () -> Unit,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current

    val isPlane = state.projectionMode == ProjectionMode.PLANE
    val pudorys = conic as? ConicSectionPudorys
    val narys = conic as? ConicSectionNarys
    val bokorys = conic as? ConicSectionBokorys
    val axo = conic as? ConicSectionAxo

    var pendingColor by remember(conic.id, conic.color) {
        mutableStateOf(conic.color)
    }

    var sliderValue by remember(conic.id, conic.strokeWidth) {
        mutableStateOf(conic.strokeWidth)
    }

    val rawNameNow = (conic.name ?: "")
        .removeSuffix("\u2081")
        .removeSuffix("\u2082")
        .removeSuffix("\u2083")
        .trim()

    var pendingName by remember(conic.id) {
        mutableStateOf(TextFieldValue(rawNameNow))
    }

    var lastAppliedName by remember(conic.id) {
        mutableStateOf(rawNameNow)
    }

    fun approxZero(x: Float) = abs(x) < 1e-5f
    fun approxEq(a: Float, b: Float) = abs(a - b) < 1e-5f
    val conicType = when (conic) {
        is ConicSectionPudorys -> when {
            hyperbolaInputsPudorys.containsKey(conic.id) -> "Hyperbola"
            isHyperbolaCoefficients(conic.a, conic.b, conic.c) -> "Hyperbola"
            approxZero(conic.b) && approxEq(conic.a, conic.c) -> "Kružnice"
            inputPointsPudorys[conic.id]?.third == Offset.Unspecified -> "Parabola"
            else -> "Elipsa"
        }
        is ConicSectionNarys -> when {
            hyperbolaInputsNarys.containsKey(conic.id) -> "Hyperbola"
            isHyperbolaCoefficients(conic.a, conic.b, conic.c) -> "Hyperbola"
            approxZero(conic.b) && approxEq(conic.a, conic.c) -> "Kružnice"
            inputPointsNarys[conic.id]?.third == Offset.Unspecified -> "Parabola"
            else -> "Elipsa"
        }
        is ConicSectionBokorys -> when {
            hyperbolaInputsBokorys.containsKey(conic.id) -> "Hyperbola"
            isHyperbolaCoefficients(conic.a, conic.b, conic.c) -> "Hyperbola"
            approxZero(conic.b) && approxEq(conic.a, conic.c) -> "Kružnice"
            inputPointsBokorys[conic.id]?.third == Offset.Unspecified -> "Parabola"
            else -> "Elipsa"
        }

        is ConicSectionAxo ->  when {
            hyperbolaInputsAxo.containsKey(conic.id) -> "Hyperbola"
            isHyperbolaCoefficients(conic.a, conic.b, conic.c) -> "Hyperbola"
            approxZero(conic.b) && approxEq(conic.a, conic.c) -> "Kružnice"
            inputPointsAxo[conic.id]?.third == Offset.Unspecified -> "Parabola"
            else -> "Elipsa"
        }

    }


    LaunchedEffect(conic.id, conic.name) {
        val n = (conic.name ?: "")
            .removeSuffix("\u2081")
            .removeSuffix("\u2082")
            .removeSuffix("\u2083")
            .trim()

        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }

    val canApply = pendingName.text.trim() != lastAppliedName

    fun applyName() {
        if (!canApply) return

        val newName = pendingName.text.trim()

        onApplyName(newName)

        lastAppliedName = newName
    }

    val ignoredConicType: String = when (conic) {

        is ConicSectionPudorys -> {
            when {
                abs(conic.b) < 1e-5f &&
                        abs(conic.a - conic.c) < 1e-5f -> "Kružnice"

                hyperbolaInputsPudorys.containsKey(conic.id) -> "Hyperbola"

                inputPointsPudorys[conic.id]?.third == Offset.Unspecified -> "Parabola"

                else -> "Elipsa"
            }
        }

        is ConicSectionNarys -> {
            when {
                abs(conic.b) < 1e-5f &&
                        abs(conic.a - conic.c) < 1e-5f -> "Kružnice"

                hyperbolaInputsNarys.containsKey(conic.id) -> "Hyperbola"

                inputPointsBokorys[conic.id]?.third == Offset.Unspecified -> "Parabola"

                else -> "Elipsa"
            }
        }

        is ConicSectionBokorys -> {
            when {
                abs(conic.b) < 1e-5f &&
                        abs(conic.a - conic.c) < 1e-5f -> "Kružnice"

                hyperbolaInputsBokorys.containsKey(conic.id) -> "Hyperbola"

                inputPointsNarys[conic.id]?.third == Offset.Unspecified -> "Parabola"

                else -> "Elipsa"
            }
        }

        is ConicSectionAxo -> {
            when {
                abs(conic.b) < 1e-5f &&
                        abs(conic.a - conic.c) < 1e-5f -> "Kružnice"

                hyperbolaInputsBokorys.containsKey(conic.id) -> "Hyperbola"

                inputPointsNarys[conic.id]?.third == Offset.Unspecified -> "Parabola"

                else -> "Elipsa"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {

        SimpleNameEditor(
            label = "Kuželosečka:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { applyName() },
            state = state,
            inputWidth = ui.dp(90f)
        )

        MongeDivider()


            MongeInspectorPropertyRow("Typ:") {
                Text(
                    conicType,
                    fontSize = ui.sp(15f),
                    color = colors.text
                )
            }

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

            MongeInspectorPropertyRow("Styl:") {
                LineStyleSelector(
                    current = conic.lineStyle,
                    onStyleChange = onStyleChange
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
                    *listOfNotNull(
                        axo?.let { ProjectionVisibilityToggleItem("A", it.showInAxo, onShowAxoProjectionChange) },
                        pudorys?.let { ProjectionVisibilityToggleItem("P", it.showInAxo, onShowPudorysProjectionChange) },
                        narys?.let { ProjectionVisibilityToggleItem("N", it.showInAxo, onShowNarysProjectionChange) },
                        bokorys?.let { ProjectionVisibilityToggleItem("B", it.showInAxo, onShowBokorysProjectionChange) }
                    ).toTypedArray(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }



        val visibilityButtons = singleConicVisibilityButtons(state, conic)
        if (visibilityButtons.isNotEmpty()) {
            MongeDivider()
            ConicVisibilityButtons(
                state = state,
                ui = ui,
                buttons = visibilityButtons
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
fun EditableParentConicInfo(
    conic: ConicSection3D,
    on3DStyleChange: (LineStyle) -> Unit,
    onPudorysStyleChange: (LineStyle) -> Unit,
    onNarysStyleChange: (LineStyle) -> Unit,
    onBokorysStyleChange: (LineStyle) -> Unit,
    onAxoStyleChange: (LineStyle) -> Unit,
    onShowPudorysProjectionChange: (Boolean) -> Unit,
    onShowNarysProjectionChange: (Boolean) -> Unit,
    onShowBokorysProjectionChange: (Boolean) -> Unit,
    onShowAxoProjectionChange: (Boolean) -> Unit,
    onApplyName: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onDelete: () -> Unit,
    state: MongeState,
    inputPointsPudorys: Map<String, Triple<Offset, Offset, Offset>>,
    inputPointsNarys: Map<String, Triple<Offset, Offset, Offset>>,
    inputPointsBokorys: Map<String, Triple<Offset, Offset, Offset>>,
    inputPointsAxo: Map<String, Triple<Offset, Offset, Offset>>,
    hyperbolaInputsPudorys: Map<String, ConicInputHyperbolaPudorys>,
    hyperbolaInputsNarys: Map<String, ConicInputHyperbolaNarys>,
    hyperbolaInputsBokorys: Map<String, ConicInputHyperbolaBokorys>,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current

    var sliderValue by remember(conic.id, conic.strokeWidth) {
        mutableStateOf(conic.strokeWidth)
    }

    var pendingColor by remember(conic.id, conic.color) {
        mutableStateOf(conic.color)
    }

    val pudorys =
        state.conicsPudorys.firstOrNull {
            it.parent?.id == conic.id || it.parentId == conic.id
        }

    val narys =
        state.conicsNarys.firstOrNull {
            it.parent?.id == conic.id || it.parentId == conic.id
        }

    val bokorys =
        state.conicsBokorys.firstOrNull {
            it.parent?.id == conic.id || it.parentId == conic.id
        }
    val axo =
        state.conicsAxo.firstOrNull {
            it.parent?.id == conic.id || it.parentId == conic.id
        }

    val nameNow = conic.name.trim()

    var pendingName by remember(conic.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(conic.id) {
        mutableStateOf(nameNow)
    }

    fun approxZero(x: Float) = kotlin.math.abs(x) < 1e-5f
    fun approxEq(a: Float, b: Float) = kotlin.math.abs(a - b) < 1e-5f

    val conicType: String = when {

        // Parent 3D je jediný spolehlivý zdroj: hyperbola se vždy promítá jako
        // hyperbola nebo degenerovaná přímka, nikdy ne jako elipsa/parabola/kružnice.
        // Bez této větve by se typ určoval podle prvního nenulového průmětu (pudorys),
        // který je u hyperboly konstruované v nárysně/bokorysně degenerovaný → "Elipsa".
        isHyperbola3D(conic) -> "Hyperbola"

        pudorys != null -> when {

            hyperbolaInputsPudorys.containsKey(pudorys.id) -> "Hyperbola"

            isHyperbolaCoefficients(pudorys.a, pudorys.b, pudorys.c) -> "Hyperbola"

            approxZero(pudorys.b) &&
                    approxEq(pudorys.a, pudorys.c) -> "Kružnice"

            inputPointsPudorys[pudorys.id]?.third == Offset.Unspecified -> "Parabola"

            else -> "Elipsa"
        }

        narys != null -> when {

            hyperbolaInputsNarys.containsKey(narys.id) -> "Hyperbola"

            isHyperbolaCoefficients(narys.a, narys.b, narys.c) -> "Hyperbola"

            approxZero(narys.b) &&
                    approxEq(narys.a, narys.c) -> "Kružnice"

            inputPointsNarys[narys.id]?.third == Offset.Unspecified -> "Parabola"

            else -> "Elipsa"
        }

        bokorys != null -> when {
            hyperbolaInputsBokorys.containsKey(bokorys.id) -> "Hyperbola"

            isHyperbolaCoefficients(bokorys.a, bokorys.b, bokorys.c) -> "Hyperbola"

            approxZero(bokorys.b) &&
                    approxEq(bokorys.a, bokorys.c) -> "Kružnice"

            inputPointsBokorys[bokorys.id]?.third == Offset.Unspecified -> "Parabola"

            isHyperbola3D(conic) -> "Hyperbola"

            else -> "Elipsa"
        }

        isHyperbola3D(conic) -> "Hyperbola"

        else -> "Kuželosečka"
    }

    LaunchedEffect(conic.id, conic.name) {
        val n = conic.name.trim()

        lastAppliedName = n
        pendingName = TextFieldValue(n)
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

        SimpleNameEditor(
            label = "Kuželosečka:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { applyName() },
            state = state,
            inputWidth = ui.dp(90f)
        )

        MongeDivider()


            MongeInspectorPropertyRow("Typ:") {
                Text(
                    conicType,
                    fontSize = ui.sp(15f),
                    color = colors.text
                )
            }

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



        if (state.projectionMode == ProjectionMode.AXO) {
            MongeDivider()
            MongeInspectorPropertyRow(
                label = "Průměty:",
                contentAlign = Alignment.End
            ) {
                ProjectionVisibilityToggleStrip(
                    ui = ui,
                    *listOfNotNull(
                        axo?.let { ProjectionVisibilityToggleItem("A", it.showInAxo, onShowAxoProjectionChange) },
                        pudorys?.let { ProjectionVisibilityToggleItem("P", it.showInAxo, onShowPudorysProjectionChange) },
                        narys?.let { ProjectionVisibilityToggleItem("N", it.showInAxo, onShowNarysProjectionChange) },
                        bokorys?.let { ProjectionVisibilityToggleItem("B", it.showInAxo, onShowBokorysProjectionChange) }
                    ).toTypedArray(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        MongeDivider()
        MongeInspectorSection("Styly") {
            Column(
                verticalArrangement = Arrangement.spacedBy(ui.dp(4f)),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                    Text("3D", fontSize = ui.sp(12f), color = colors.text)
                    LineStyleSelector(current = conic.lineStyle, onStyleChange = on3DStyleChange)
                }
                narys?.takeIf { it.showInAxo }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                        Text("N", fontSize = ui.sp(12f), color = colors.text)
                        LineStyleSelector(current = it.lineStyle, onStyleChange = onNarysStyleChange)
                    }
                }
                pudorys?.takeIf { it.showInAxo }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                        Text("P", fontSize = ui.sp(12f), color = colors.text)
                        LineStyleSelector(current = it.lineStyle, onStyleChange = onPudorysStyleChange)
                    }
                }
                bokorys?.takeIf { it.showInAxo }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                        Text("B", fontSize = ui.sp(12f), color = colors.text)
                        LineStyleSelector(current = it.lineStyle, onStyleChange = onBokorysStyleChange)
                    }
                }
                axo?.takeIf { it.showInAxo }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                        Text("A", fontSize = ui.sp(12f), color = colors.text)
                        LineStyleSelector(current = it.lineStyle, onStyleChange = onAxoStyleChange)
                    }
                }
            }
        }

        val visibilityButtons = parentConicVisibilityButtons(
            state = state,
            parentId = conic.id,
            pudorys = pudorys,
            narys = narys,
            bokorys = bokorys,
            axo = axo
        )
        if (visibilityButtons.isNotEmpty()) {
            MongeDivider()
            ConicVisibilityButtons(
                state = state,
                ui = ui,
                buttons = visibilityButtons
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
fun getConicType(
    conic: Any,
    inputPointsPudorys: Map<String, Triple<Offset, Offset, Offset>>,
    inputPointsNarys: Map<String, Triple<Offset, Offset, Offset>>,
    inputPointsBokorys: Map<String, Triple<Offset, Offset, Offset>>,
    hyperbolaInputsPudorys: Map<String, *>,
    hyperbolaInputsNarys: Map<String, *>,
    hyperbolaInputsBokorys: Map<String, *>,
): String {
    return when (conic) {
        is ConicSectionPudorys -> {
            when {
                conic.parent?.let { isHyperbola3D(it) } == true -> "Hyperbola"
                hyperbolaInputsPudorys.containsKey(conic.id) -> "Hyperbola"
                conic.b * conic.b - 4f * conic.a * conic.c > 1e-5f -> "Hyperbola"
                abs(conic.b) < 1e-5f && abs(conic.a - conic.c) < 1e-5f -> "Kružnice"
                inputPointsPudorys[conic.id]?.third == Offset.Unspecified -> "Parabola"
                else -> "Elipsa"
            }
        }

        is ConicSectionNarys -> {
            when {
                conic.parent?.let { isHyperbola3D(it) } == true -> "Hyperbola"
                hyperbolaInputsNarys.containsKey(conic.id) -> "Hyperbola"
                conic.b * conic.b - 4f * conic.a * conic.c > 1e-5f -> "Hyperbola"
                abs(conic.b) < 1e-5f && abs(conic.a - conic.c) < 1e-5f -> "Kružnice"
                inputPointsNarys[conic.id]?.third == Offset.Unspecified -> "Parabola"
                else -> "Elipsa"
            }
        }
        is ConicSectionBokorys -> {
            when {
                conic.parent?.let { isHyperbola3D(it) } == true -> "Hyperbola"
                hyperbolaInputsBokorys.containsKey(conic.id) -> "Hyperbola"
                conic.b * conic.b - 4f * conic.a * conic.c > 1e-5f -> "Hyperbola"
                abs(conic.b) < 1e-5f && abs(conic.a - conic.c) < 1e-5f -> "Kružnice"
                inputPointsBokorys[conic.id]?.third == Offset.Unspecified -> "Parabola"
                else -> "Elipsa"
            }
        }

        else -> "Neznámá"
    }
}
fun deleteConic3D(state: MongeState, parent: ConicSection3D) {


    // 1) ZĂ­skej aktuĂˇlnĂ­ instance obou projekcĂ­ podle parenta
    val p2d = state.conicsPudorys.filter { it.parent?.id == parent.id || it.parentId == parent.id }.toList()
    val n2d = state.conicsNarys.filter { it.parent?.id == parent.id || it.parentId == parent.id }.toList()
    val b2d = state.conicsBokorys.filter { it.parent?.id == parent.id || it.parentId == parent.id }.toList()
    val a2d = state.conicsAxo.filter { it.parent?.id == parent.id || it.parentId == parent.id}.toList()

    // 2) ZruĹˇ vĂ˝bÄ›ry
    state.selectedConicsPudorys.removeAll(p2d.toSet())
    state.selectedConicsNarys.removeAll(n2d.toSet())
    state.selectedConicsBokorys.removeAll(b2d.toSet())
    state.selectedConicsAxo.removeAll(a2d.toSet())

    // 3) ÄŚIĹ TÄšNĂŤ MAP & CACHE pro obÄ› projekce
    p2d.forEach { c ->
        state.conicInputPointsPudorys.remove(c.id)
        state.hyperbolaInputsPudorys.remove(c.id)      // pokud existuje
    }
    n2d.forEach { c ->
        state.conicInputPointsNarys.remove(c.id)
        state.hyperbolaInputsNarys.remove(c.id)        // pokud existuje
    }
    b2d.forEach { c ->
        state.conicInputPointsBokorys.remove(c.id)
        state.hyperbolaInputsBokorys.remove(c.id)
    }
    a2d.forEach { c ->
        state.conicInputPointsAxo.remove(c.id)
        state.hyperbolaInputsAxo.remove(c.id)
    }

    // 4) ZruĹˇ preview/temporary stavy, kterĂ© by to jeĹˇtÄ› kreslily
    if (state.liftingConicPudorys?.id == parent.id) state.liftingConicPudorys = null
    if (state.liftingConicNarys?.id == parent.id) state.liftingConicNarys = null
    if (state.liftingConicBokorys?.id == parent.id) state.liftingConicBokorys = null

    // 5) OdstraĹ projekce a 3D z hlavnĂ­ch seznamĹŻ (konkrĂ©tnĂ­ instance)
    state.conicsPudorys.removeAll(p2d.toSet())
    state.conicsNarys.removeAll(n2d.toSet())
    state.conicsBokorys.removeAll(b2d.toSet())
    state.conicsAxo.removeAll(a2d.toSet())
    state.conics3D.remove(parent)
    commitSnapshot(state)
    state.triggerRedraw++
}
fun deleteConic2D(state:MongeState, conic: ConicSection2D)
{

    when (conic) {
        is ConicSectionPudorys -> {
            val pid = conic.parentId
            if (pid != null) {
                // obÄ› projekce tĂ©hle 3D kuĹľeloseÄŤky
                val pudorysy = state.conicsPudorys.filter { it.parentId == pid }
                val narysy   = state.conicsNarys  .filter { it.parentId == pid }
                val bokorysy = state.conicsBokorys.filter{it.parentId == pid}
                val axo = state.conicsAxo.filter{it.parentId == pid}

                // vĂ˝bÄ›ry
                state.selectedConicsPudorys.removeAll(pudorysy.toSet())
                state.selectedConicsNarys.removeAll(narysy.toSet())
                state.selectedConicsBokorys.removeAll(bokorysy.toSet())
                state.selectedConicsAxo.removeAll(axo.toSet())

                // smazat projekce
                state.conicsPudorys.removeAll { it.parentId == pid }
                state.conicsNarys  .removeAll { it.parentId == pid }
                state.conicsBokorys.removeAll { it.parentId == pid }
                state.conicsAxo.removeAll     { it.parentId == pid }

                // smazat 3D parenta
                state.conics3D.removeAll { it.id == pid }
            } else {
                // bez parenta â€“ smaĹľ jen tuto projekci
                state.selectedConicsPudorys.remove(conic)
                state.conicsPudorys.removeAll { it.id == conic.id }
            }
        }

        is ConicSectionNarys -> {
            val pid = conic.parentId
            if (pid != null) {
                val pudorysy = state.conicsPudorys.filter { it.parentId == pid }
                val narysy   = state.conicsNarys  .filter { it.parentId == pid }
                val bokorysy = state.conicsBokorys.filter{it.parentId == pid}
                val axo = state.conicsAxo.filter{it.parentId == pid}

                state.selectedConicsPudorys.removeAll(pudorysy.toSet())
                state.selectedConicsNarys.removeAll(narysy.toSet())
                state.selectedConicsBokorys.removeAll(bokorysy.toSet())
                state.selectedConicsAxo.removeAll(axo.toSet())


                state.conicsPudorys.removeAll { it.parentId == pid }
                state.conicsNarys  .removeAll { it.parentId == pid }
                state.conicsBokorys.removeAll { it.parentId == pid }
                state.conicsAxo.removeAll     { it.parentId == pid }

                state.conics3D.removeAll { it.id == pid }
            } else {
                state.selectedConicsNarys.remove(conic)
                state.conicsNarys.removeAll { it.id == conic.id }
            }
        }

        is ConicSectionBokorys -> {
            val pid = conic.parentId
            if (pid != null) {
                val pudorysy = state.conicsPudorys.filter { it.parentId == pid }
                val narysy   = state.conicsNarys  .filter { it.parentId == pid }
                val bokorysy = state.conicsBokorys.filter{it.parentId == pid}
                val axo = state.conicsAxo.filter{it.parentId == pid}


                state.selectedConicsPudorys.removeAll(pudorysy.toSet())
                state.selectedConicsNarys.removeAll(narysy.toSet())
                state.selectedConicsBokorys.removeAll(bokorysy.toSet())
                state.selectedConicsAxo.removeAll(axo.toSet())


                state.conicsPudorys.removeAll { it.parentId == pid }
                state.conicsNarys  .removeAll { it.parentId == pid }
                state.conicsBokorys.removeAll { it.parentId == pid }
                state.conicsAxo.removeAll     { it.parentId == pid }

                state.conics3D.removeAll { it.id == pid }
            } else {
                state.selectedConicsBokorys.remove(conic)
                state.conicsBokorys.removeAll { it.id == conic.id }
            }
        }
        is ConicSectionAxo -> {
            val pid = conic.parentId
            if (pid != null) {
                val pudorysy = state.conicsPudorys.filter { it.parentId == pid }
                val narysy   = state.conicsNarys  .filter { it.parentId == pid }
                val bokorysy = state.conicsBokorys.filter{it.parentId == pid}
                val axo = state.conicsAxo.filter{it.parentId == pid}


                state.selectedConicsPudorys.removeAll(pudorysy.toSet())
                state.selectedConicsNarys.removeAll(narysy.toSet())
                state.selectedConicsBokorys.removeAll(bokorysy.toSet())
                state.selectedConicsAxo.removeAll(axo.toSet())


                state.conicsPudorys.removeAll { it.parentId == pid }
                state.conicsNarys  .removeAll { it.parentId == pid }
                state.conicsBokorys.removeAll { it.parentId == pid }
                state.conicsAxo.removeAll     { it.parentId == pid }

                state.conics3D.removeAll { it.id == pid }
            } else {
                state.selectedConicsAxo.remove(conic)
                state.conicsAxo.removeAll { it.id == conic.id }
            }
        }

    }
    commitSnapshot(state)
}
fun deleteCircle(state: MongeState,circle: ConicSection2D)
{


    when (circle) {
        is ConicSectionPudorys -> {
            val pid = circle.parentId
            if (pid != null) {
                // obÄ› projekce tĂ©Ĺľe 3D kruĹľnice
                val pudorysy = state.circlesPudorys.filter { it.parentId == pid }
                val narysy = state.circlesNarys.filter { it.parentId == pid }
                val bokorysy = state.circlesBokorys.filter { it.parentId == pid }
                // vĂ˝bÄ›ry (+ pĹ™Ă­p. offsety popiskĹŻ, pokud mĂˇĹˇ mapy)
                state.selectedCirclesPudorys.removeAll(pudorysy.toSet())
                state.selectedCirclesNarys.removeAll(narysy.toSet())
                state.selectedCirclesBokorys.removeAll(bokorysy.toSet())


                // smazat projekce
                state.circlesPudorys.removeAll { it.parentId == pid }
                state.circlesNarys.removeAll { it.parentId == pid }
                state.circlesBokorys.removeAll { it.parentId == pid }


            } else {
                // bez parenta â€“ jen tento prĹŻmÄ›t
                state.selectedCirclesPudorys.remove(circle)
                state.circlesPudorys.removeAll { it.id == circle.id }
                // state.labelOffsetsCirclesPudorys?.remove(circle)
            }
        }



        is ConicSectionNarys -> {
            val pid = circle.parentId
            if (pid != null) {
                val pudorysy = state.circlesPudorys.filter { it.parentId == pid }
                val narysy = state.circlesNarys.filter { it.parentId == pid }
                val bokorysy = state.circlesBokorys.filter { it.parentId == pid }

                state.selectedCirclesPudorys.removeAll(pudorysy.toSet())
                state.selectedCirclesNarys.removeAll(narysy.toSet())
                state.selectedCirclesBokorys.removeAll(bokorysy.toSet())


                state.circlesPudorys.removeAll { it.parentId == pid }
                state.circlesNarys.removeAll { it.parentId == pid }
                state.circlesBokorys.removeAll { it.parentId == pid }

            } else {
                state.selectedCirclesNarys.remove(circle)
                state.circlesNarys.removeAll { it.id == circle.id }
                // state.labelOffsetsCirclesNarys?.remove(circle)
            }
        }

        is ConicSectionBokorys -> {
            val pid = circle.parentId
            if (pid != null) {
                val pudorysy = state.circlesPudorys.filter { it.parentId == pid }
                val narysy = state.circlesNarys.filter { it.parentId == pid }
                val bokorysy = state.circlesBokorys.filter { it.parentId == pid }

                state.selectedCirclesPudorys.removeAll(pudorysy.toSet())
                state.selectedCirclesNarys.removeAll(narysy.toSet())
                state.selectedCirclesBokorys.removeAll(bokorysy.toSet())


                state.circlesPudorys.removeAll { it.parentId == pid }
                state.circlesNarys.removeAll { it.parentId == pid }
                state.circlesBokorys.removeAll { it.parentId == pid }

            } else {
                state.selectedCirclesBokorys.remove(circle)
                state.circlesBokorys.removeAll { it.id == circle.id }
                // state.labelOffsetsCirclesNarys?.remove(circle)
            }
        }
        else -> {}
    }
    commitSnapshot(state)
    state.triggerRedraw++
}

private enum class ProjectionKindConic { PUDORYS, NARYS, BOKORYS,AXO }

private fun setConicProjectionVisible(
    state: MongeState,
    parentId: String,
    checked: Boolean,
    kind: ProjectionKindConic
) {
    when (kind) {
        ProjectionKindConic.PUDORYS -> {
            state.conicsPudorys.replaceAll { conic ->
                if (conic.parent?.id == parentId || conic.parentId == parentId) {
                    conic.copy(showInAxoInitial = checked)
                } else conic
            }
        }
        ProjectionKindConic.NARYS -> {
            state.conicsNarys.replaceAll { conic ->
                if (conic.parent?.id == parentId || conic.parentId == parentId) {
                    conic.copy(showInAxoInitial = checked)
                } else conic
            }
        }
        ProjectionKindConic.BOKORYS -> {
            state.conicsBokorys.replaceAll { conic ->
                if (conic.parent?.id == parentId || conic.parentId == parentId) {
                    conic.copy(showInAxoInitial = checked)
                } else conic
            }
        }
        ProjectionKindConic.AXO -> {
            state.conicsAxo.replaceAll { conic ->
                if (conic.parent?.id == parentId || conic.parentId == parentId) {
                    conic.copy(showInAxoInitial = checked)
                } else conic
            }
        }
    }
}

private fun setSingleConicProjectionVisible(
    state: MongeState,
    conicId: String,
    checked: Boolean,
    kind: ProjectionKindConic
) {
    when (kind) {
        ProjectionKindConic.PUDORYS -> {
            state.conicsPudorys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.circlesPudorys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.selectedConicsPudorys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.selectedCirclesPudorys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
        }
        ProjectionKindConic.NARYS -> {
            state.conicsNarys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.circlesNarys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.selectedConicsNarys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.selectedCirclesNarys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
        }
        ProjectionKindConic.BOKORYS -> {
            state.conicsBokorys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.circlesBokorys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.selectedConicsBokorys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.selectedCirclesBokorys.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
        }
        ProjectionKindConic.AXO -> {
            state.conicsAxo.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
            state.selectedConicsAxo.replaceAll { conic ->
                if (conic.id == conicId) conic.copy(showInAxoInitial = checked) else conic
            }
        }
    }
}

@Composable
fun conicEdit(state: MongeState){
    val selectedConic =
        state.selectedConicsPudorys.firstOrNull()
            ?: state.selectedConicsNarys.firstOrNull()
            ?: state.selectedConicsBokorys.firstOrNull() ?: state.selectedConicsAxo.firstOrNull()

    val currentConic: ConicSection2D? = when (selectedConic) {
        is ConicSectionPudorys -> state.conicsPudorys.find { it.id == selectedConic.id }
        is ConicSectionNarys -> state.conicsNarys.find { it.id == selectedConic.id }
        is ConicSectionBokorys -> state.conicsBokorys.find { it.id == selectedConic.id }
        is ConicSectionAxo -> state.conicsAxo.find { it.id == selectedConic.id }
        else -> null
    }
    val conic3D = (currentConic?.parent?.id ?: currentConic?.parentId).let { parentId ->
        state.conics3D.find { it.id == parentId }
    }
    conic3D?.let { conic ->
        key(conic.id) {
            EditableParentConicInfo(
                conic = conic,


                on3DStyleChange = { newStyle ->
                    // 1) 3D zmÄ›na
                    val idx3D = state.conics3D.indexOfFirst { it.id == conic.id }
                    if (idx3D >= 0) {
                        val updated = state.conics3D[idx3D].copy(lineStyle = newStyle)
                        state.conics3D[idx3D] = updated

                        // 2) pĹ™epoj 2D + NASTAV parentId (musĂ­ bĂ˝t konzistentnĂ­!)
                        for (i in state.conicsPudorys.indices) {
                            val c = state.conicsPudorys[i]
                            if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                state.conicsPudorys[i] = c.copy(parent = updated, parentId = updated.id)
                            }
                        }
                        for (i in state.conicsNarys.indices) {
                            val c = state.conicsNarys[i]
                            if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                state.conicsNarys[i] = c.copy(parent = updated, parentId = updated.id)
                            }
                        }
                        for (i in state.conicsBokorys.indices) {
                            val c = state.conicsBokorys[i]
                            if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                state.conicsBokorys[i] = c.copy(parent = updated, parentId = updated.id)
                            }
                        }
                        for (i in state.conicsAxo.indices) {
                            val c = state.conicsAxo[i]
                            if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                state.conicsAxo[i] = c.copy(parent = updated, parentId = updated.id, lineStyle = newStyle)
                            }
                        }
                    }

                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                onPudorysStyleChange = { newStyle ->

                    for (i in state.conicsPudorys.indices) {
                        val c = state.conicsPudorys[i]
                        if (c.parent?.id == conic.id) {
                            val up = c.copy(lineStyle = newStyle)
                            state.conicsPudorys[i] = up
                        }
                    }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },

                onNarysStyleChange = { newStyle ->

                    for (i in state.conicsNarys.indices) {
                        val c = state.conicsNarys[i]
                        if (c.parent?.id == conic.id) {
                            state.conicsNarys[i] = c.copy(lineStyle = newStyle)
                        }
                    }
                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                onBokorysStyleChange = { newStyle ->
                    for (i in state.conicsBokorys.indices) {
                        val c = state.conicsBokorys[i]
                        if (c.parent?.id == conic.id || c.parentId == conic.id) {
                            state.conicsBokorys[i] = c.copy(lineStyle = newStyle)
                        }
                    }
                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                onAxoStyleChange = { newStyle ->
                    for (i in state.conicsAxo.indices) {
                        val c = state.conicsAxo[i]
                        if (c.parent?.id == conic.id || c.parentId == conic.id) {
                            state.conicsAxo[i] = c.copy(lineStyle = newStyle)
                        }
                    }
                    state.selectedConicsAxo.replaceAll { c ->
                        if (c.parent?.id == conic.id || c.parentId == conic.id) {
                            c.copy(lineStyle = newStyle)
                        } else c
                    }
                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                onShowPudorysProjectionChange = { checked ->
                    setConicProjectionVisible(state, conic.id, checked, ProjectionKindConic.PUDORYS)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onShowNarysProjectionChange = { checked ->
                    setConicProjectionVisible(state, conic.id, checked, ProjectionKindConic.NARYS)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onShowBokorysProjectionChange = { checked ->
                    setConicProjectionVisible(state, conic.id, checked, ProjectionKindConic.BOKORYS)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onShowAxoProjectionChange = { checked ->
                    setConicProjectionVisible(state,conic.id,checked, ProjectionKindConic.AXO)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onColorChange = { newColor ->
                    val idx3D = state.conics3D.indexOfFirst { it.id == conic.id }
                    if (idx3D < 0) return@EditableParentConicInfo

                    val old3D = state.conics3D[idx3D]
                    val updated3D = old3D.copy(color = newColor).apply {
                        directrixOfSurfaceIds = old3D.directrixOfSurfaceIds
                    }
                    state.conics3D[idx3D] = updated3D

                    state.conicsPudorys.replaceAll { p ->
                        val matches = (p.parent?.id == old3D.id) || (p.parentId == old3D.id)
                        if (matches) p.copy(parent = updated3D, parentId = updated3D.id) else p
                    }
                    state.conicsNarys.replaceAll { p ->
                        val matches = (p.parent?.id == old3D.id) || (p.parentId == old3D.id)
                        if (matches) p.copy(parent = updated3D, parentId = updated3D.id) else p
                    }
                    state.conicsBokorys.replaceAll { p ->
                        val matches = (p.parent?.id == old3D.id) || (p.parentId == old3D.id)
                        if (matches) p.copy(parent = updated3D, parentId = updated3D.id) else p
                    }
                    state.conicsAxo.replaceAll { p ->
                        val matches = (p.parent?.id == old3D.id) || (p.parentId == old3D.id)
                        if (matches) p.copy(parent = updated3D, parentId = updated3D.id) else p
                    }
                    state.selectedConicsPudorys.replaceAll { p ->
                        val matches = (p.parent?.id == old3D.id) || (p.parentId == old3D.id)
                        if (matches) p.copy(parent = updated3D, parentId = updated3D.id) else p
                    }
                    state.selectedConicsNarys.replaceAll { p ->
                        val matches = (p.parent?.id == old3D.id) || (p.parentId == old3D.id)
                        if (matches) p.copy(parent = updated3D, parentId = updated3D.id) else p
                    }
                    state.selectedConicsBokorys.replaceAll { p ->
                        val matches = (p.parent?.id == old3D.id) || (p.parentId == old3D.id)
                        if (matches) p.copy(parent = updated3D, parentId = updated3D.id) else p
                    }
                    state.selectedConicsAxo.replaceAll { p ->
                        val matches = (p.parent?.id == old3D.id) || (p.parentId == old3D.id)
                        if (matches) p.copy(parent = updated3D, parentId = updated3D.id) else p
                    }

                    state.triggerRedraw++
                    commitSnapshot(state)
                },


                        onWidthChange = { newWidth ->
                    // 3D
                    state.conics3D.indexOfFirst { it.id == conic.id }
                        .takeIf { it >= 0 }
                        ?.let { idx ->
                            state.conics3D[idx] = state.conics3D[idx].copy(strokeWidth = newWidth)
                        }

                    // PĹŻdorysy (jen ty, co patĹ™Ă­ k tomuto 3D)
                    for (i in state.conicsPudorys.indices) {
                        val c = state.conicsPudorys[i]
                        if (c.parent?.id == conic.id) {
                            state.conicsPudorys[i] = c.copy(strokeWidth = newWidth)
                        }
                    }

                    // NĂˇrysy
                    for (i in state.conicsNarys.indices) {
                        val c = state.conicsNarys[i]
                        if (c.parent?.id == conic.id) {
                            state.conicsNarys[i] = c.copy(strokeWidth = newWidth)
                        }
                    }

                    for (i in state.conicsBokorys.indices) {
                        val c = state.conicsBokorys[i]
                        if (c.parent?.id == conic.id || c.parentId == conic.id) {
                            state.conicsBokorys[i] = c.copy(strokeWidth = newWidth)
                        }
                    }

                    for (i in state.conicsAxo.indices) {
                          val c = state.conicsAxo[i]
                          if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                    state.conicsAxo[i] = c.copy(strokeWidth = newWidth)
                                }
                            }

                    state.triggerRedraw++
                },
                onDelete = {
                    deleteConic3D(state, conic)
                    clearSelection(state)
                },
                state = state,
                onApplyName = { newName ->


                    val idx3D = state.conics3D.indexOfFirst { it.id == conic.id }
                    if (idx3D >= 0) {
                        val updated3D = state.conics3D[idx3D].copy(rawName = newName)
                        state.conics3D[idx3D] = updated3D

                        // PĹŻdorysy + NĂˇrysy: vaĹľ podle parentId || parent?.id
                        for (i in state.conicsPudorys.indices) {
                            val c = state.conicsPudorys[i]
                            if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                state.conicsPudorys[i] = c.copy(rawName = newName, parent = updated3D, parentId = updated3D.id)
                            }
                        }
                        for (i in state.conicsNarys.indices) {
                            val c = state.conicsNarys[i]
                            if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                state.conicsNarys[i] = c.copy(rawName = newName, parent = updated3D, parentId = updated3D.id)
                            }
                        }
                        for (i in state.conicsBokorys.indices) {
                            val c = state.conicsBokorys[i]
                            if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                state.conicsBokorys[i] = c.copy(rawName = newName, parent = updated3D, parentId = updated3D.id)
                            }
                        }
                        for (i in state.conicsAxo.indices) {
                            val c = state.conicsAxo[i]
                            if (c.parent?.id == conic.id || c.parentId == conic.id) {
                                state.conicsAxo[i] = c.copy(rawName = newName, parent = updated3D, parentId = updated3D.id)
                            }
                        }

                        // pokud drĹľĂ­Ĺˇ selected listy (doporuÄŤeno udrĹľet konzistentnĂ­)
                        state.selectedConicsPudorys.replaceAll { p ->
                            val matches = (p.parent?.id == conic.id) || (p.parentId == conic.id)
                            if (matches) p.copy(rawName = newName, parent = updated3D, parentId = updated3D.id) else p
                        }
                        state.selectedConicsNarys.replaceAll { p ->
                            val matches = (p.parent?.id == conic.id) || (p.parentId == conic.id)
                            if (matches) p.copy(rawName = newName, parent = updated3D, parentId = updated3D.id) else p
                        }
                        state.selectedConicsBokorys.replaceAll { p ->
                            val matches = (p.parent?.id == conic.id) || (p.parentId == conic.id)
                            if (matches) p.copy(rawName = newName, parent = updated3D, parentId = updated3D.id) else p
                        }
                        state.selectedConicsAxo.replaceAll { p ->
                            val matches = (p.parent?.id == conic.id) || (p.parentId == conic.id)
                            if (matches) p.copy(rawName = newName, parent = updated3D, parentId = updated3D.id) else p
                        }
                    }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                inputPointsPudorys = state.conicInputPointsPudorys,
                inputPointsNarys = state.conicInputPointsNarys,
                inputPointsBokorys = state.conicInputPointsBokorys,
                inputPointsAxo = state.conicInputPointsAxo,
                hyperbolaInputsPudorys = state.hyperbolaInputsPudorys,
                hyperbolaInputsNarys = state.hyperbolaInputsNarys,
                hyperbolaInputsBokorys = state.hyperbolaInputsBokorys,
                uiScale = SettingsManager.current.UIscale/75f

            )
        }
    }
    if (conic3D == null) {
        currentConic?.let { conic ->
            key(conic.id) {
                EditableConicInfo(
                    conic = conic,
                    onColorChange = { newColor ->

                        val updated = when (conic) {
                            is ConicSectionPudorys -> conic.copy(localColor = newColor)
                            is ConicSectionNarys -> conic.copy(localColor = newColor)
                            is ConicSectionBokorys ->conic.copy(localColor = newColor)
                            is ConicSectionAxo -> conic.copy(localColor = newColor)
                        }
                        when (updated) {
                            is ConicSectionPudorys -> {
                                state.conicsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            }

                            is ConicSectionNarys -> {
                                state.conicsNarys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsNarys.replaceAll { if (it.id == updated.id) updated else it }
                            }

                            is ConicSectionBokorys -> {
                                state.conicsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            }
                            is ConicSectionAxo -> {
                                state.conicsAxo.replaceAll{ if (it.id == updated.id) updated else it }
                                state.selectedConicsAxo.replaceAll { if (it.id == updated.id) updated else it}
                            }
                        }
                        commitSnapshot(state)
                    },
                    onWidthChange = { newWidth ->
                        val updated = when (conic) {
                            is ConicSectionPudorys -> conic.copy(strokeWidth = newWidth)
                            is ConicSectionNarys -> conic.copy(strokeWidth = newWidth)
                            is ConicSectionBokorys -> conic.copy(strokeWidth = newWidth)
                            is ConicSectionAxo -> {conic.copy(strokeWidth = newWidth)}
                        }
                        when (updated) {
                            is ConicSectionPudorys -> {
                                state.conicsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            }

                            is ConicSectionNarys -> {
                                state.conicsNarys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsNarys.replaceAll { if (it.id == updated.id) updated else it }
                            }

                            is ConicSectionBokorys -> {
                                state.conicsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            }
                            is ConicSectionAxo -> {
                                state.conicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                            }
                        }
                    },
                    onStyleChange = { newStyle ->
                        val updated = when (conic) {
                            is ConicSectionPudorys -> conic.copy(lineStyle = newStyle)
                            is ConicSectionNarys -> conic.copy(lineStyle = newStyle)
                            is ConicSectionBokorys -> conic.copy(lineStyle = newStyle)
                            is ConicSectionAxo -> {conic.copy(lineStyle = newStyle)}
                        }
                        when (updated) {
                            is ConicSectionPudorys -> {
                                state.conicsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            }

                            is ConicSectionNarys -> {
                                state.conicsNarys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsNarys.replaceAll { if (it.id == updated.id) updated else it }
                            }

                            is ConicSectionBokorys -> {
                                state.conicsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            }
                            is ConicSectionAxo -> {
                                state.conicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                            }
                        }
                        commitSnapshot(state)
                    },
                    inputPointsPudorys = state.conicInputPointsPudorys,
                    inputPointsNarys = state.conicInputPointsNarys,
                    inputPointsBokorys = state.conicInputPointsBokorys,
                    hyperbolaInputsPudorys = state.hyperbolaInputsPudorys,
                    hyperbolaInputsNarys = state.hyperbolaInputsNarys,
                    hyperbolaInputsBokorys = state.hyperbolaInputsBokorys,
                    onShowPudorysProjectionChange = { checked ->
                        setSingleConicProjectionVisible(state, conic.id, checked, ProjectionKindConic.PUDORYS)
                        commitSnapshot(state)
                        state.triggerRedraw++
                    },
                    onShowNarysProjectionChange = { checked ->
                        setSingleConicProjectionVisible(state, conic.id, checked, ProjectionKindConic.NARYS)
                        commitSnapshot(state)
                        state.triggerRedraw++
                    },
                    onShowBokorysProjectionChange = { checked ->
                        setSingleConicProjectionVisible(state, conic.id, checked, ProjectionKindConic.BOKORYS)
                        commitSnapshot(state)
                        state.triggerRedraw++
                    },
                    onShowAxoProjectionChange = { checked ->
                        setSingleConicProjectionVisible(state, conic.id, checked, ProjectionKindConic.AXO)
                        commitSnapshot(state)
                        state.triggerRedraw++
                    },
                    onAddProjection = {
                        val conicType = getConicType(
                            conic,
                            state.conicInputPointsPudorys,
                            state.conicInputPointsNarys,
                            state.conicInputPointsBokorys,
                            state.hyperbolaInputsPudorys,
                            state.hyperbolaInputsNarys,
                            state.hyperbolaInputsBokorys,
                        )

                        when (conic) {
                            is ConicSectionPudorys -> {
                                when (conicType) {
                                    "Elipsa" -> startLiftConicToPlaneFromPudorys(state)
                                    "Kružnice" -> {}
                                    "Hyperbola" -> startLiftHyperbolaToPlaneFromPudorys(state)
                                    "Parabola" -> startLiftParabolaToPlaneFromPudorys(state)
                                }
                            }

                            is ConicSectionNarys -> {
                                when (conicType) {
                                    "Elipsa" -> startLiftConicToPlaneFromNarys(state)
                                    "Kružnice" -> {}
                                    "Hyperbola" -> startLiftHyperbolaToPlaneFromNarys(state)
                                    "Parabola" -> startLiftParabolaToPlaneFromNarys(state)
                                }
                            }

                            is ConicSectionBokorys -> {
                                when (conicType) {
                                    "Elipsa" -> startLiftConicToPlaneFromBokorys(state)
                                    "Kružnice" -> {}
                                    "Hyperbola" -> startLiftHyperbolaToPlaneFromBokorys(state)
                                    "Parabola" -> startLiftParabolaToPlaneFromBokorys(state)
                                }
                            }
                            is ConicSectionAxo -> TODO()
                        }
                    },
                    onDelete = {
                        deleteConic2D(state, conic)
                        clearSelection(state)
                        state.triggerRedraw++
                    },
                    state=state,
                    onApplyName = { newName ->

                        val updated = when (conic) {
                            is ConicSectionPudorys -> conic.copy(rawName = newName)
                            is ConicSectionNarys -> conic.copy(rawName = newName)
                            is ConicSectionBokorys ->conic.copy(rawName = newName)
                            is ConicSectionAxo -> conic.copy(rawName = newName)
                        }

                        when (updated) {
                            is ConicSectionPudorys -> {
                                state.conicsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            }
                            is ConicSectionNarys -> {
                                state.conicsNarys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsNarys.replaceAll { if (it.id == updated.id) updated else it }
                            }

                            is ConicSectionBokorys -> {
                                state.conicsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            }
                            is ConicSectionAxo -> {
                                state.conicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                                state.selectedConicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                            }
                        }
                        state.triggerRedraw++
                        commitSnapshot(state)

                    },
                    uiScale = SettingsManager.current.UIscale/75f,
                    inputPointsAxo = state.conicInputPointsAxo,
                    hyperbolaInputsAxo = state.hyperbolaInputsAxo,

                )
            }
        }
    }
}
@Composable
fun circleEdit(state: MongeState){
    val selectedCircle =
        state.selectedCirclesPudorys.firstOrNull() ?: state.selectedCirclesNarys.firstOrNull()

    val currentCircle: ConicSection2D? = when (selectedCircle) {
        is ConicSectionPudorys -> state.circlesPudorys.find { it.id == selectedCircle.id }
        is ConicSectionNarys -> state.circlesNarys.find { it.id == selectedCircle.id }
        else -> null
    }
    currentCircle?.let { circle ->
        key(circle.id) {
            EditableConicInfo(
                conic = circle,
                onColorChange = { newColor ->
                    val updated = when (circle) {
                        is ConicSectionPudorys -> circle.copy(localColor = newColor)
                        is ConicSectionNarys -> circle.copy(localColor = newColor)
                        is ConicSectionBokorys -> circle.copy(localColor = newColor)
                        is ConicSectionAxo -> circle.copy(localColor = newColor)
                    }
                    when (updated) {
                        is ConicSectionPudorys -> {
                            state.circlesPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesPudorys.replaceAll { if (it.id == updated.id) updated else it }
                        }

                        is ConicSectionNarys -> {
                            state.circlesNarys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesNarys.replaceAll { if (it.id == updated.id) updated else it }
                        }

                        is ConicSectionBokorys -> {
                            state.circlesBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesBokorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is ConicSectionAxo -> {
                            state.conicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedConicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                        }
                    }
                    commitSnapshot(state)

                },
                onWidthChange = { newWidth ->
                    val updated = when (circle) {
                        is ConicSectionPudorys -> circle.copy(strokeWidth = newWidth)
                        is ConicSectionNarys -> circle.copy(strokeWidth = newWidth)
                        is ConicSectionBokorys -> circle.copy(strokeWidth = newWidth)
                        is ConicSectionAxo -> circle.copy(strokeWidth = newWidth)
                    }
                    when (updated) {
                        is ConicSectionPudorys -> {
                            state.circlesPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesPudorys.replaceAll { if (it.id == updated.id) updated else it }
                        }

                        is ConicSectionNarys -> {
                            state.circlesNarys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesNarys.replaceAll { if (it.id == updated.id) updated else it }
                        }

                        is ConicSectionBokorys -> {
                            state.circlesBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesBokorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is ConicSectionAxo -> {
                            state.conicsAxo.replaceAll{ if (it.id == updated.id) updated else it}
                            state.selectedConicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                        }
                    }
                },
                onStyleChange = { newStyle ->
                    val updated = when (circle) {
                        is ConicSectionPudorys -> circle.copy(lineStyle = newStyle)
                        is ConicSectionNarys -> circle.copy(lineStyle = newStyle)
                        is ConicSectionBokorys -> circle.copy(lineStyle = newStyle)
                        is ConicSectionAxo -> circle.copy(lineStyle = newStyle)
                    }
                    when (updated) {
                        is ConicSectionPudorys -> {
                            state.circlesPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesPudorys.replaceAll { if (it.id == updated.id) updated else it }
                        }

                        is ConicSectionNarys -> {
                            state.circlesNarys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesNarys.replaceAll { if (it.id == updated.id) updated else it }
                        }

                        is ConicSectionBokorys -> {
                            state.circlesBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesBokorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is ConicSectionAxo -> {
                            state.conicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedConicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                        }
                    }
                    commitSnapshot(state)

                },
                inputPointsPudorys = state.conicInputPointsPudorys,
                inputPointsNarys = state.conicInputPointsNarys,
                inputPointsBokorys = state.conicInputPointsBokorys,
                hyperbolaInputsPudorys = state.hyperbolaInputsPudorys,
                hyperbolaInputsNarys = state.hyperbolaInputsNarys,
                hyperbolaInputsBokorys = state.hyperbolaInputsBokorys,
                onShowPudorysProjectionChange = { checked ->
                    setSingleConicProjectionVisible(state, circle.id, checked, ProjectionKindConic.PUDORYS)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onShowNarysProjectionChange = { checked ->
                    setSingleConicProjectionVisible(state, circle.id, checked, ProjectionKindConic.NARYS)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onShowBokorysProjectionChange = { checked ->
                    setSingleConicProjectionVisible(state, circle.id, checked, ProjectionKindConic.BOKORYS)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onShowAxoProjectionChange = { checked ->
                    setSingleConicProjectionVisible(state, circle.id, checked, ProjectionKindConic.AXO)
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onAddProjection = { startLiftCircleToPlaneFromPudorys(state) },
                onDelete = {deleteCircle(state,circle)
                    clearSelection(state)
                },
                state = state,
                onApplyName = { newName ->

                    val updated = when (circle) {
                        is ConicSectionPudorys -> circle.copy(rawName = newName)
                        is ConicSectionNarys -> circle.copy(rawName = newName)
                        is ConicSectionBokorys -> circle.copy(rawName = newName)
                        is ConicSectionAxo -> circle.copy(rawName = newName)
                    }

                    when (updated) {
                        is ConicSectionPudorys -> {
                            state.circlesPudorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesPudorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is ConicSectionNarys -> {
                            state.circlesNarys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesNarys.replaceAll { if (it.id == updated.id) updated else it }
                        }

                        is ConicSectionBokorys -> {
                            state.circlesBokorys.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedCirclesBokorys.replaceAll { if (it.id == updated.id) updated else it }
                        }
                        is ConicSectionAxo -> {
                            state.conicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                            state.selectedConicsAxo.replaceAll { if (it.id == updated.id) updated else it }
                        }
                    }
                    state.triggerRedraw++
                    commitSnapshot(state)

                },
                uiScale = SettingsManager.current.UIscale/75f,
                inputPointsAxo = state.conicInputPointsAxo,
                hyperbolaInputsAxo = state.hyperbolaInputsAxo

            )
        }
    }

}
