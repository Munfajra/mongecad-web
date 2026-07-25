package ui.mongeui.toolbar.rightDescriptionBar

import utils.replaceAll
import ui.components.MiniInputField
import dialogs.nameInput.parseKota
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.LineStyle
import model.LocalMongeColors
import model.ProjectionMode
import model.classes.*
import serialization.SettingsManager




import monge.input.combineprojections.CompleteSegmentAdd
import monge.input.combineprojections.upgradePudorysSegmentTo3DWithKotas
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.SkikoButton

private fun Segment2DPudorys.withAxoVisibilityFrom(source: Segment2DPudorys): Segment2DPudorys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
        start.showInAxoInitial = source.start.showInAxo
        start.showInAxo = source.start.showInAxo
        end.showInAxoInitial = source.end.showInAxo
        end.showInAxo = source.end.showInAxo
    }

private fun Segment2DNarys.withAxoVisibilityFrom(source: Segment2DNarys): Segment2DNarys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
        start.showInAxoInitial = source.start.showInAxo
        start.showInAxo = source.start.showInAxo
        end.showInAxoInitial = source.end.showInAxo
        end.showInAxo = source.end.showInAxo
    }

private fun Segment2DBokorys.withAxoVisibilityFrom(source: Segment2DBokorys): Segment2DBokorys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
        start.showInAxoInitial = source.start.showInAxo
        start.showInAxo = source.start.showInAxo
        end.showInAxoInitial = source.end.showInAxo
        end.showInAxo = source.end.showInAxo
    }

private fun Segment2DAxo.withAxoVisibilityFrom(source: Segment2DAxo): Segment2DAxo =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
        start.showInAxoInitial = source.start.showInAxo
        start.showInAxo = source.start.showInAxo
        end.showInAxoInitial = source.end.showInAxo
        end.showInAxo = source.end.showInAxo
    }

@Composable
fun EditableParentSegmentInfo(
    segment: Segment3D,
    pudorys: Segment2DPudorys?,
    narys: Segment2DNarys?,
    bokorys: Segment2DBokorys?,
    axo: Segment2DAxo?,
    on3DStyleChange: (LineStyle) -> Unit,
    onPudorysStyleChange: (LineStyle) -> Unit,
    onAxoStyleChange: (LineStyle) -> Unit,
    onNarysStyleChange: (LineStyle) -> Unit,
    onBokorysStyleChange: (LineStyle) -> Unit,
    onShowPudorysProjectionChange: (Boolean) -> Unit,
    onShowNarysProjectionChange: (Boolean) -> Unit,
    onShowBokorysProjectionChange: (Boolean) -> Unit,
    onShowAxoProjectionChange: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onDelete: () -> Unit,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current

    val isKoto = state.projectionMode == ProjectionMode.KOTO
    val isAxo = state.projectionMode == ProjectionMode.AXO

    val nameNow = segment.name.trim()

    var pendingName by remember(segment.id) {
        mutableStateOf(TextFieldValue(nameNow, selection = TextRange(0, nameNow.length)))
    }

    var lastAppliedName by remember(segment.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(segment.id, segment.name) {
        val n = segment.name.trim()
        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }

    val canApply = pendingName.text.trim() != lastAppliedName

    fun applyName() {
        if (!canApply) return

        val newName = pendingName.text.trim()
        onRename(newName)

        lastAppliedName = newName
    }

    var pendingColor by remember(segment.id, segment.color) {
        mutableStateOf(segment.color)
    }

    var pendingWidth by remember(segment.id, segment.strokeWidth) {
        mutableStateOf(segment.strokeWidth)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        SimpleNameEditor(
            label = "Úsečka 3D:",
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { applyName() },
            state = state,
            ui = ui,
            inputWidth = ui.dp(58f)
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
                label = "Styly:",
                contentAlign = Alignment.End
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ui.dp(4f)),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                        Text("3D", fontSize = ui.sp(12f), color = colors.text)
                        LineStyleSelector(current = segment.lineStyle, onStyleChange = on3DStyleChange)
                    }
                    if (!isKoto && narys != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                            Text("N", fontSize = ui.sp(12f), color = colors.text)
                            LineStyleSelector(current = narys.lineStyle, onStyleChange = onNarysStyleChange)
                        }
                    }
                    pudorys?.let { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                            Text("P", fontSize = ui.sp(12f), color = colors.text)
                            LineStyleSelector(current = p.lineStyle, onStyleChange = onPudorysStyleChange)
                        }
                    }
                    if (isAxo) {
                        bokorys?.let { b ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                                Text("B", fontSize = ui.sp(12f), color = colors.text)
                                LineStyleSelector(current = b.lineStyle, onStyleChange = onBokorysStyleChange)
                            }
                        }
                        axo?.let { a ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                                Text("A", fontSize = ui.sp(12f), color = colors.text)
                                LineStyleSelector(current = a.lineStyle, onStyleChange = onAxoStyleChange)
                            }
                        }
                    }
                }
            }

        if (isAxo) {
            MongeDivider()

            MongeInspectorPropertyRow(
                label = "Zobrazení:",
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

@Composable
fun EditableSegmentProjectionInfo(
    segment: Segment2DProjection,
    onApplyName: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onShowInAxoChange: (Boolean) -> Unit,
    onAddProjection: () -> Unit,
    onDelete: () -> Unit,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current

    val isPlane = state.projectionMode == ProjectionMode.PLANE
    val isKoto = state.projectionMode == ProjectionMode.KOTO
    val isPudSeg = segment is Segment2DPudorys

    val isEditingKotoThis =
        isKoto &&
                isPudSeg &&
                segment.parent == null &&
                state.kotoSegmentEditId == segment.id

    var pendingColor by remember(segment.id, segment.color) {
        mutableStateOf(segment.color)
    }

    var pendingWidth by remember(segment.id, segment.strokeWidth) {
        mutableStateOf(segment.strokeWidth)
    }

    val nameNow = (segment.name ?: "")
        .removeSuffix("₁")
        .removeSuffix("₂")
        .trim()

    var pendingName by remember(segment.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(segment.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(segment.id, segment.name) {
        val n = (segment.name ?: "")
            .removeSuffix("₁")
            .removeSuffix("₂")
            .trim()

        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }

    val canApply = pendingName.text.trim() != lastAppliedName

    fun apply() {
        if (!canApply) return

        val n = pendingName.text
            .trim()
            .removeSuffix("₁")
            .removeSuffix("₂")

        onApplyName(n)

        lastAppliedName = n
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        SimpleNameEditor(
            label = "Úsečka:",
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { apply() },
            state = state,
            ui = ui,
            inputWidth = ui.dp(60f)
        )

        MongeDivider()


            MongeInspectorPropertyRow(
                label = "Barva:",
                contentAlign = Alignment.End
            ) {
                ColorPickerDropdown(
                    selectedColor = pendingColor,
                    onColorPreview = { pendingColor = it },
                    onColorConfirm = onColorChange
                )
            }

            MongeDivider()

            MongeInspectorPropertyRow(
                label = "Šířka:",
                contentAlign = Alignment.End
            ) {
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
                    current = segment.lineStyle,
                    onStyleChange = onStyleChange
                )
            }

        if (state.projectionMode == ProjectionMode.AXO) {
            MongeDivider()

            ProjectionVisibilityToggleRow(
                label = "Zobrazení:",
                checked = when (segment) {
                    is Segment2DPudorys -> segment.showInAxo
                    is Segment2DNarys -> segment.showInAxo
                    is Segment2DBokorys -> segment.showInAxo
                    is Segment2DAxo -> segment.showInAxo
                    else -> true
                },
                onCheckedChange = onShowInAxoChange,
                ui = ui
            )
        }

        if (isKoto) {
            MongeDivider()

            val textLabel = "Určit projekci koncovými body"


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SkikoButton(
                    width =ui.dp(280f) ,
                    height = ui.dp(38f),
                    onClick = {
                        if (
                            isPudSeg &&
                            segment.parent == null
                        ) {
                            state.kotoSegmentEditId = segment.id
                            state.kotoSegmentKotaA = ""
                            state.kotoSegmentKotaB = ""
                            state.kotoHighlightSegmentId = segment.id
                            state.kotoHighlightEndpoint = 1
                        } else {
                            null
                        }
                    },
                    enabled = segment.parent == null
                ) {
                    Text(
                        textLabel,
                        fontSize = ui.sp(13f)
                    )
                }
            }
        }

        if (isEditingKotoThis) {
            val segP = segment as Segment2DPudorys

            MongeDivider()

            MongeInspectorSection("Kóty") {
                MongeInspectorPropertyRow(
                    label = "z(A):",
                    contentAlign = Alignment.End
                ) {
                    KotaHoverField(
                        label = "Kóta A",
                        value = state.kotoSegmentKotaA,
                        onValueChange = {
                            state.kotoSegmentKotaA = it
                        },
                        onHoverChanged = { hovering ->
                            if (hovering) {
                                state.kotoHighlightSegmentId = segP.id
                                state.kotoHighlightEndpoint = 1
                            } else if (
                                state.kotoHighlightSegmentId == segP.id &&
                                state.kotoHighlightEndpoint == 1
                            ) {
                                state.kotoHighlightEndpoint = 0
                                state.kotoHighlightSegmentId = null
                            }
                        }
                    )
                }

                MongeDivider()

                MongeInspectorPropertyRow(
                    label = "z(B):",
                    contentAlign = Alignment.End
                ) {
                    KotaHoverField(
                        label = "Kóta B",
                        value = state.kotoSegmentKotaB,
                        onValueChange = {
                            state.kotoSegmentKotaB = it
                        },
                        onHoverChanged = { hovering ->
                            if (hovering) {
                                state.kotoHighlightSegmentId = segP.id
                                state.kotoHighlightEndpoint = 2
                            } else if (
                                state.kotoHighlightSegmentId == segP.id &&
                                state.kotoHighlightEndpoint == 2
                            ) {
                                state.kotoHighlightEndpoint = 0
                                state.kotoHighlightSegmentId = null
                            }
                        }
                    )
                }
            }

            val zA = remember(state.kotoSegmentKotaA) {
                parseKota(state.kotoSegmentKotaA)
            }

            val zB = remember(state.kotoSegmentKotaB) {
                parseKota(state.kotoSegmentKotaB)
            }

            val canBuild =
                zA != null &&
                        zB != null &&
                        segP.parent == null

            Spacer(Modifier.height(ui.dp(4f)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SkikoButton(
                    width = ui.dp(220f),
                    height = ui.dp(38f),
                    enabled = canBuild,
                    onClick = {
                        val a = zA ?: return@SkikoButton
                        val b = zB ?: return@SkikoButton

                        upgradePudorysSegmentTo3DWithKotas(
                            segP,
                            a,
                            b,
                            state
                        )

                        state.kotoSegmentEditId = null
                        state.kotoSegmentKotaA = ""
                        state.kotoSegmentKotaB = ""
                        state.kotoHighlightSegmentId = null
                        state.kotoHighlightEndpoint = 0
                    }
                ) {
                    Text("Vytvořit 3D úsečku", fontSize = ui.sp(13f))
                }
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
                Text("Smazat", fontSize = ui.sp(13f))
            }
        }
    }
}
@Composable
fun EditableHelpSegmentInfo(
    segment: HelpSegments,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onDelete: () -> Unit,
    onApply: (name: String) -> Unit,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current

    var pendingColor by remember(segment.id, segment.color) {
        mutableStateOf(segment.color)
    }

    var sliderValue by remember(segment.id, segment.strokeWidth) {
        mutableStateOf(segment.strokeWidth)
    }

    val nameNow = (segment.name ?: "").trim()

    var pendingName by remember(segment.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(segment.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(segment.id, segment.name) {
        val n = (segment.name ?: "").trim()
        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }

    val canApply = pendingName.text.trim() != lastAppliedName

    fun apply() {
        if (!canApply) return

        val n = pendingName.text.trim()
        onApply(n)

        lastAppliedName = n
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        val label = if (state.projectionMode == ProjectionMode.PLANE) "Úsečka:" else "Pomocná úsečka:"
        SimpleNameEditor(
            label = label,
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
                    current = segment.lineStyle,
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun KotaHoverField(
    ui: Float = SettingsManager.current.UIscale/75f,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onHoverChanged: (Boolean) -> Unit,
    width: Dp = 140f*ui.dp
) {
    Box(
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) { onHoverChanged(true) }
            .onPointerEvent(PointerEventType.Exit) { onHoverChanged(false) }
    ) {
        MiniInputField(
            ui=ui,
            value = value,
            onValueChange = onValueChange,
            placeholder = label,
            numericOnly = true,
            width = width,
            height = 34.dp
        )
    }
        }



fun deleteSegment3D (state: MongeState, seg3D: Segment3D){

    val parent = seg3D


    // 1) obě projekce téhle 3D úsečky
    val pudorysy = state.segmentsPudorys.filter { it.parent === parent }
    val narysy   = state.segmentsNarys  .filter { it.parent === parent }
    val axo = state.segmentsAxo.filter { it.parent === parent }
    val bokorys = state.segmentsBokorys.filter { it.parent === parent }
    // 2) koncové body obou projekcí (a jejich 3D parenti)
    val endPtsP = pudorysy.flatMap { seg2d ->
        state.pointsPudorys.filter { it.isSegmentEndpoint && it.parentSegment?.id == seg2d.id }
    }
    val endPtsN = narysy.flatMap { seg2d ->
        state.pointsNarys.filter { it.isSegmentEndpoint && it.parentSegment?.id == seg2d.id }
    }
    val endPtsA = axo.flatMap { seg2d->
        state.pointsAxo.filter { it.isSegmentEndpoint && it.parentSegment?.id == seg2d.id }
    }
    val endPtsB = bokorys.flatMap {seg2d ->
        state.pointsBokorys.filter {it.isSegmentEndpoint && it.parentSegment?.id == seg2d.id }
    }
    val endpointParents3D = (endPtsP + endPtsN+endPtsA + endPtsB).mapNotNull { it.parent }.toSet()

    // 2a) smaž koncové body z obou pohledů + jejich výběr
    state.selectedPointsPudorys.removeAll(endPtsP.toSet())
    state.selectedPointsNarys.removeAll(endPtsN.toSet())
    state.selectedPointsAxo.removeAll(endPtsA.toSet())
    state.selectedPointsBokorys.removeAll(endPtsB.toSet())
    state.pointsPudorys.removeAll(endPtsP.toSet())
    state.pointsNarys  .removeAll(endPtsN.toSet())
    state.pointsBokorys.removeAll(endPtsB.toSet())
    state.pointsAxo.removeAll(endPtsA.toSet())


    // 3) zruš výběry úseček
    state.selectedSegmentsPudorys.removeAll(pudorysy.toSet())
    state.selectedSegmentsNarys.removeAll(narysy.toSet())
    state.selectedSegmentsBokorys.removeAll(bokorys.toSet())
    state.selectedSegmentsAxo.removeAll(axo.toSet())

    // 4) smaž projekce úseček
    state.segmentsPudorys.removeAll(pudorysy.toSet())
    state.segmentsNarys  .removeAll(narysy.toSet())
    state.segmentsAxo.removeAll(axo.toSet())
    state.segmentsBokorys.removeAll(bokorys.toSet())
    // 5) smaž 3D parenta úsečky
    state.segments3D.removeAll { it.id == parent.id }

    // 6) SMAZAT parenty koncových bodů + obě jejich projekce
    endpointParents3D.forEach { p3 ->
        // projekce toho 3D bodu
        val projP = state.pointsPudorys.filter { it.parent === p3 }
        val projN = state.pointsNarys  .filter { it.parent === p3 }
        val projB = state.pointsBokorys.filter { it.parent === p3 }
        val projA = state.pointsAxo.filter { it.parent === p3 }

        // úklid výběrů a offsetů
        projP.forEach { pt ->
            state.selectedPointsPudorys.remove(pt)
            state.labelOffsetsPointsPudorys.remove(pt.id)
        }
        projN.forEach { pt ->
            state.selectedPointsNarys.remove(pt)
            state.labelOffsetsPointsNarys.remove(pt.id)
        }
        projB.forEach { pt ->
            state.selectedPointsBokorys.remove(pt)
            state.labelOffsetsPointsBokorys.remove(pt.id)
        }
        projA.forEach { pt ->
            state.selectedPointsAxo.remove(pt)
            state.labelOffsetsPointsAxo.remove(pt.id)
        }

        // smazat projekce
        state.pointsPudorys.removeAll(projP.toSet())
        state.pointsNarys  .removeAll(projN.toSet())
        state.pointsBokorys.removeAll(projB.toSet())
        state.pointsAxo.removeAll(projA.toSet())

        // smazat 3D parent bod
        state.sharedPoints3D.removeAll { it.id == p3.id }

        // pokud zrovna probíhalo přejmenování toho bodu, zruš
        if (state.rename.pointBeingRenamed === p3) state.rename.pointBeingRenamed = null
    }
    commitSnapshot(state)

    state.triggerRedraw++
        }
fun deleteSegment2D(state: MongeState,selectedSegmentRaw: Segment2DProjection){

    when (val seg = selectedSegmentRaw) {
        is Segment2DPudorys -> {
            // odeber výběr
            state.selectedSegmentsPudorys.remove(seg)
            // smaž segment
            state.segmentsPudorys.removeAll { it.id == seg.id }
            // smaž koncové body této úsečky v PŮDORYSU
            state.pointsPudorys.removeAll { it.isSegmentEndpoint && it.parentSegment?.id == seg.id }
            state.selectedPointsPudorys.removeAll { it.isSegmentEndpoint && it.parentSegment?.id == seg.id }

            // pokud měl parenta → zruš parenta a "odpoj" druhý průmět
            seg.parent?.let { parent ->
                // smazat 3D úsečku

                state.segments3D.removeAll { it.id == parent.id }

                // najdi druhý průmět v NÁRYSU a odpoj ho
                state.segmentsNarys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("₁")?.removeSuffix("₂") ?: ""
                    val idx = state.segmentsNarys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsNarys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
                state.segmentsBokorys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("\u2083")?.removeSuffix("₂") ?: ""
                    val idx = state.segmentsBokorys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsBokorys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
                state.segmentsAxo.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("\u2083")?.removeSuffix("\u2083") ?: ""
                    val idx = state.segmentsAxo.indexOf(other)
                    if (idx != -1) {
                        state.segmentsAxo[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
            }
        }

        is Segment2DNarys -> {
            state.selectedSegmentsNarys.remove(seg)
            state.segmentsNarys.removeAll { it.id == seg.id }
            // smaž koncové body této úsečky v NÁRYSU
            state.pointsNarys.removeAll { it.isSegmentEndpoint && it.parentSegment?.id == seg.id }
            state.selectedPointsNarys.removeAll { it.isSegmentEndpoint && it.parentSegment?.id == seg.id }

            seg.parent?.let { parent ->

                state.segments3D.removeAll { it.id == parent.id }

                // najdi druhý průmět v PŮDORYSU a odpoj ho
                state.segmentsPudorys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("₁")?.removeSuffix("₂") ?: ""
                    val idx = state.segmentsPudorys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsPudorys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
                state.segmentsBokorys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("\u2083")?.removeSuffix("₂") ?: ""
                    val idx = state.segmentsBokorys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsBokorys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
                state.segmentsAxo.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("\u2083")?.removeSuffix("\u2083") ?: ""
                    val idx = state.segmentsAxo.indexOf(other)
                    if (idx != -1) {
                        state.segmentsAxo[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
            }
        }
        is Segment2DBokorys -> {
            state.selectedSegmentsBokorys.remove(seg)
            state.segmentsBokorys.removeAll { it.id == seg.id }
            // smaž koncové body této úsečky v NÁRYSU
            state.pointsBokorys.removeAll { it.isSegmentEndpoint && it.parentSegment?.id == seg.id }
            state.selectedPointsBokorys.removeAll { it.isSegmentEndpoint && it.parentSegment?.id == seg.id }

            seg.parent?.let { parent ->

                state.segments3D.removeAll { it.id == parent.id }

                // najdi druhý průmět v PŮDORYSU a odpoj ho
                state.segmentsPudorys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("₁")?.removeSuffix("₂") ?: ""
                    val idx = state.segmentsPudorys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsPudorys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
                state.segmentsNarys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("₁")?.removeSuffix("₂") ?: ""
                    val idx = state.segmentsNarys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsNarys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
                state.segmentsAxo.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("\u2083")?.removeSuffix("\u2083") ?: ""
                    val idx = state.segmentsAxo.indexOf(other)
                    if (idx != -1) {
                        state.segmentsAxo[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
            }
        }
        is Segment2DAxo-> {
            state.selectedSegmentsAxo.remove(seg)
            state.segmentsAxo.removeAll { it.id == seg.id }
            // smaž koncové body této úsečky v NÁRYSU
            state.pointsAxo.removeAll { it.isSegmentEndpoint && it.parentSegment?.id == seg.id }
            state.selectedPointsAxo.removeAll { it.isSegmentEndpoint && it.parentSegment?.id == seg.id }

            seg.parent?.let { parent ->

                state.segments3D.removeAll { it.id == parent.id }

                // najdi druhý průmět v PŮDORYSU a odpoj ho
                state.segmentsPudorys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("₁")?.removeSuffix("₂") ?: ""
                    val idx = state.segmentsPudorys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsPudorys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
                state.segmentsNarys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("₁")?.removeSuffix("₂") ?: ""
                    val idx = state.segmentsNarys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsNarys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
                state.segmentsBokorys.find { it.parent === parent }?.let { other ->
                    val cleanName = other.name?.removeSuffix("\u2083")?.removeSuffix("\u2083") ?: ""
                    val idx = state.segmentsBokorys.indexOf(other)
                    if (idx != -1) {
                        state.segmentsBokorys[idx] = other.copy(
                            name = cleanName,
                            parent = null
                        ).withAxoVisibilityFrom(other)
                    }
                }
            }
        }

        else -> {}
    }
    commitSnapshot(state)

    state.triggerRedraw++
        }
fun deleteHelpSegment2D(state: MongeState,selectedSegmentRaw: HelpSegments){

    when (val seg = selectedSegmentRaw) {
        is HelpSegmentNarys -> {
            // odeber výběr
            state.selectedSegmentsNarys.remove(seg)
            // smaž segment
            state.helpSegmentsNarys.removeAll { it.id == seg.id }
        }

        is HelpSegmentPudorys -> {
            // odeber výběr
            state.selectedSegmentsPudorys.remove(seg)
            monge.input.segments.removePlanePolygonsContainingSegments(state, setOf(seg.id))
            // smaž segment
            state.helpSegmentsPudorys.removeAll { it.id == seg.id }
        }
    }
    commitSnapshot(state)

    state.triggerRedraw++
}
@Composable
fun segmentEdit(state: MongeState){
    val selectedHelpSegment = state.selectedSegmentsPudorys.firstOrNull()
        ?: state.selectedSegmentsNarys.firstOrNull()
    val currentHelpSegment = when (selectedHelpSegment) {
        is HelpSegmentNarys -> state.helpSegmentsNarys.find {it.id == selectedHelpSegment.id}
        is HelpSegmentPudorys -> state.helpSegmentsPudorys.find {it.id == selectedHelpSegment.id}
        else -> null
    }
    currentHelpSegment?.let{helpseg->
        key(selectedHelpSegment) {
            EditableHelpSegmentInfo(
                segment = helpseg,
                onColorChange = { newColor ->
                    val base = newColor

                    val updated = when (helpseg) {
                        is HelpSegmentNarys -> helpseg.copy(localColor = base)
                        is HelpSegmentPudorys -> helpseg.copy(localColor = base)
                        else -> {}
                    }

                    when (updated) {
                        is HelpSegmentPudorys -> {
                            val i = state.helpSegmentsPudorys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.helpSegmentsPudorys[i] = updated
                        }

                        is HelpSegmentNarys -> {
                            val i = state.helpSegmentsNarys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.helpSegmentsNarys[i] = updated
                        }
                    }
                    commitSnapshot(state)

                },
                onWidthChange = { newWidth ->
                    val base = newWidth

                    val updated = when (helpseg) {
                        is HelpSegmentNarys -> helpseg.copy(localStrokeWidth = base)
                        is HelpSegmentPudorys -> helpseg.copy(localStrokeWidth = base)
                        else -> {}
                    }

                    when (updated) {
                        is HelpSegmentPudorys -> {
                            val i = state.helpSegmentsPudorys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.helpSegmentsPudorys[i] = updated
                        }

                        is HelpSegmentNarys -> {
                            val i = state.helpSegmentsNarys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.helpSegmentsNarys[i] = updated
                        }
                    }
                },
                onStyleChange = { newStyle ->
                    val base = newStyle

                    val updated = when (helpseg) {
                        is HelpSegmentNarys -> helpseg.copy(localLineStyle = base)
                        is HelpSegmentPudorys -> helpseg.copy(localLineStyle = base)
                        else -> {}
                    }

                    when (updated) {
                        is HelpSegmentPudorys -> {
                            val i = state.helpSegmentsPudorys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.helpSegmentsPudorys[i] = updated
                        }

                        is HelpSegmentNarys -> {
                            val i = state.helpSegmentsNarys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.helpSegmentsNarys[i] = updated
                        }
                    }
                    commitSnapshot(state)

                },
                onDelete = {
                    deleteHelpSegment2D(state = state, selectedSegmentRaw = helpseg)
                    clearSelection(state)
                },
                onApply = { newName ->
                    applyHelpSegmentRename(helpseg, newName, state)
                },
                state=state,
                uiScale = SettingsManager.current.UIscale / 75f


            )
        }
    }
    val selectedSegmentRaw = state.selectedSegmentsPudorys.firstOrNull()
        ?: state.selectedSegmentsNarys.firstOrNull() ?: state.selectedSegmentsBokorys.firstOrNull() ?: state.selectedSegmentsAxo.firstOrNull()
    val currentSegment = when (selectedSegmentRaw) {
        is Segment2DPudorys -> state.segmentsPudorys.find { it.id == selectedSegmentRaw.id }
        is Segment2DNarys -> state.segmentsNarys.find { it.id == selectedSegmentRaw.id }
        is Segment2DBokorys -> state.segmentsBokorys.find {it.id == selectedSegmentRaw.id}
        is Segment2DAxo -> state.segmentsAxo.find { it.id == selectedSegmentRaw.id }
        else -> null
    }
    val segmentToEdit = if ((state.projectionMode == ProjectionMode.MONGE|| state.projectionMode == ProjectionMode.KOTO
                ||  state.projectionMode == ProjectionMode.AXO)
        && currentSegment?.parent != null) null else currentSegment

    val parentSegment = if (state.projectionMode == ProjectionMode.MONGE|| state.projectionMode == ProjectionMode.KOTO||
        state.projectionMode == ProjectionMode.AXO) currentSegment?.parent else {null}
    parentSegment?.let { seg3D ->
        val pudorys = state.segmentsPudorys.firstOrNull { it.parent === seg3D }
        val narys = state.segmentsNarys.firstOrNull { it.parent === seg3D }
        val bokorys = state.segmentsBokorys.firstOrNull {it.parent === seg3D}
        val axo = state.segmentsAxo.firstOrNull { it.parent === seg3D }

        /* ---------- 2) AĹ˝ POTOM VOLĂME EDITOR ---------- */
        key(seg3D) {
            EditableParentSegmentInfo(
                segment = seg3D,
                pudorys = pudorys,
                narys = narys,
                bokorys = bokorys,
                axo = axo,
                on3DStyleChange = { new ->

                    // 1) vytvoĹ™ NOVĂť objekt se zmÄ›nÄ›nĂ˝m stylem
                    val updated = seg3D.copy(lineStyle = new)

                    // 2) nahraÄŹ v segments3D podle id
                    state.segments3D.replaceAll { seg ->
                        if (seg.id == seg3D.id) updated else seg
                    }

                    // 3) v projekcĂ­ch nahraÄŹ parent
                    state.segmentsPudorys.replaceAll { seg ->
                        if (seg.parent === seg3D) seg.copy(parent = updated).withAxoVisibilityFrom(seg) else seg
                    }
                    state.segmentsNarys.replaceAll { seg ->
                        if (seg.parent === seg3D) seg.copy(parent = updated).withAxoVisibilityFrom(seg) else seg
                    }
                    state.segmentsBokorys.replaceAll { seg ->
                        if (seg.parent === seg3D) seg.copy(parent = updated).withAxoVisibilityFrom(seg) else seg
                    }
                    state.segmentsAxo.replaceAll { seg ->
                        if (seg.parent === seg3D) seg.copy(parent = updated).withAxoVisibilityFrom(seg) else seg
                    }
                    commitSnapshot(state)

                    state.triggerRedraw++
                },
                onPudorysStyleChange = { new ->
                    pudorys?.let {
                        state.segmentsPudorys.replaceAll { seg ->
                            if (seg.id == it.id) seg.copy(localLineStyle = new).withAxoVisibilityFrom(seg) else seg
                        }
                        commitSnapshot(state)

                        state.triggerRedraw++
                    }
                },
                onNarysStyleChange = { new ->
                    narys?.let {
                        state.segmentsNarys.replaceAll { seg ->
                            if (seg.id == it.id) seg.copy(localLineStyle = new).withAxoVisibilityFrom(seg) else seg
                        }
                        commitSnapshot(state)

                        state.triggerRedraw++
                    }
                },
                onBokorysStyleChange = { new ->
                    bokorys?.let {
                        state.segmentsBokorys.replaceAll { seg ->
                            if (seg.id == it.id) seg.copy(localLineStyle = new).withAxoVisibilityFrom(seg) else seg
                        }
                        commitSnapshot(state)

                        state.triggerRedraw++
                    }
                },
                onAxoStyleChange = { new ->
                    axo?.let {
                        state.segmentsAxo.replaceAll { seg ->
                            if (seg.id == it.id) seg.copy(localLineStyle = new).withAxoVisibilityFrom(seg) else seg
                        }
                        commitSnapshot(state)
                        state.triggerRedraw++
                    }
                },
                onShowPudorysProjectionChange = { checked ->
                    pudorys?.let {
                        setSegmentProjectionVisibleWithEndpoints(state, it.id, checked, ProjectionKindSeg.PUDORYS)
                        commitSnapshot(state)
                        state.triggerRedraw++
                    }
                },
                onShowNarysProjectionChange = { checked ->
                    narys?.let {
                        setSegmentProjectionVisibleWithEndpoints(state, it.id, checked, ProjectionKindSeg.NARYS)
                        commitSnapshot(state)
                        state.triggerRedraw++
                    }
                },
                onShowBokorysProjectionChange = { checked ->
                    bokorys?.let {
                        setSegmentProjectionVisibleWithEndpoints(state, it.id, checked, ProjectionKindSeg.BOKORYS)
                        commitSnapshot(state)
                        state.triggerRedraw++
                    }
                },
                onShowAxoProjectionChange = { checked ->
                    axo?.let {
                        setSegmentProjectionVisibleWithEndpoints(state, it.id, checked, ProjectionKindSeg.AXO)
                        commitSnapshot(state)
                        state.triggerRedraw++
                    }
                },
                onRename = { newName ->
                    val cleaned = newName.removeSuffix("₁").removeSuffix("₂")

                    val old = parentSegment
                    val updated = old.copy(name = cleaned)

                    // NahraÄŹ v sharedSegments3D
                    val idx = state.segments3D.indexOfFirst { it.id == old.id }
                    if (idx != -1) {
                        state.segments3D[idx] = updated
                    }

                    // Aktualizuj projekce
                    state.segmentsPudorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.segmentsPudorys[i] = state.segmentsPudorys[i].copy(
                            parent = updated
                        ).withAxoVisibilityFrom(state.segmentsPudorys[i])
                    }

                    state.segmentsNarys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.segmentsNarys[i] = state.segmentsNarys[i].copy(
                            parent = updated
                        ).withAxoVisibilityFrom(state.segmentsNarys[i])
                    }
                    state.segmentsBokorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.segmentsBokorys[i] = state.segmentsBokorys[i].copy(
                            parent = updated
                        ).withAxoVisibilityFrom(state.segmentsBokorys[i])
                    }
                    state.segmentsAxo.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.segmentsAxo[i] = state.segmentsAxo[i].copy(
                            parent = updated
                        ).withAxoVisibilityFrom(state.segmentsAxo[i])
                    }
                    commitSnapshot(state)

                    state.triggerRedraw++
                },
                onColorChange = { newColor ->

                    val old = parentSegment
                    val updated = old.copy(color = newColor)

                    // NahraÄŹ v sharedSegments3D
                    val idx = state.segments3D.indexOfFirst { it.id == old.id }
                    if (idx != -1) {
                        state.segments3D[idx] = updated
                    }

                    // Aktualizuj projekce s novĂ˝m parentem
                    state.segmentsPudorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.segmentsPudorys[i] = state.segmentsPudorys[i].copy(parent = updated)
                            .withAxoVisibilityFrom(state.segmentsPudorys[i])
                    }

                    state.segmentsNarys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.segmentsNarys[i] = state.segmentsNarys[i].copy(parent = updated)
                            .withAxoVisibilityFrom(state.segmentsNarys[i])
                    }
                    state.segmentsBokorys.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.segmentsBokorys[i] = state.segmentsBokorys[i].copy(
                            parent = updated
                        ).withAxoVisibilityFrom(state.segmentsBokorys[i])
                    }
                    state.segmentsAxo.indexOfFirst { it.parent === old }.takeIf { it != -1 }?.let { i ->
                        state.segmentsAxo[i] = state.segmentsAxo[i].copy(
                            parent = updated
                        ).withAxoVisibilityFrom(state.segmentsAxo[i])
                    }
                    commitSnapshot(state)

                    state.triggerRedraw++
                },
                onWidthChange = {
                    parentSegment.strokeWidth = it
                    state.triggerRedraw++
                },
                onDelete = {
                    deleteSegment3D(state = state, seg3D)
                    clearSelection(state)
                },
                state = state,
                uiScale = SettingsManager.current.UIscale/75f


            )
        }
    }

    segmentToEdit?.let { selectedSegmentRaw ->
        key(selectedSegmentRaw.id) {
            EditableSegmentProjectionInfo(
                segment = selectedSegmentRaw,
                onApplyName = { newName ->
                    val base = newName.removeSuffix("₁").removeSuffix("₂")

                    val updated = when (selectedSegmentRaw) {
                        is Segment2DPudorys -> selectedSegmentRaw.copy(name = "$base₁").withAxoVisibilityFrom(selectedSegmentRaw)
                        is Segment2DNarys -> selectedSegmentRaw.copy(name = "$base₂").withAxoVisibilityFrom(selectedSegmentRaw)
                        is Segment2DBokorys -> selectedSegmentRaw.copy(name = "$base\u2083").withAxoVisibilityFrom(selectedSegmentRaw)
                        is Segment2DAxo -> selectedSegmentRaw.copy(name = base).withAxoVisibilityFrom(selectedSegmentRaw)
                        else -> return@EditableSegmentProjectionInfo
                    }

                    when (updated) {
                        is Segment2DPudorys -> {
                            val i = state.segmentsPudorys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.segmentsPudorys[i] = updated
                        }

                        is Segment2DNarys -> {
                            val i = state.segmentsNarys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.segmentsNarys[i] = updated
                        }
                        is Segment2DBokorys -> {
                            val i = state.segmentsBokorys.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.segmentsBokorys[i] = updated
                        }
                        is Segment2DAxo -> {
                            val i = state.segmentsAxo.indexOfFirst { it.id == updated.id }
                            if (i != -1) state.segmentsAxo[i] = updated
                        }
                        else -> return@EditableSegmentProjectionInfo
                    }
                    commitSnapshot(state)

                },
                onColorChange = { newColor ->
                    when (selectedSegmentRaw) {
                        is Segment2DPudorys -> {
                            val i = state.segmentsPudorys.indexOfFirst { it.id == selectedSegmentRaw.id }
                            if (i != -1) {
                                val current = state.segmentsPudorys[i]
                                state.segmentsPudorys[i] = current.copy(localColor = newColor).withAxoVisibilityFrom(current)
                            }
                        }

                        is Segment2DNarys -> {
                            val i = state.segmentsNarys.indexOfFirst { it.id == selectedSegmentRaw.id }
                            if (i != -1) {
                                val current = state.segmentsNarys[i]
                                state.segmentsNarys[i] = current.copy(localColor = newColor).withAxoVisibilityFrom(current)
                            }
                        }

                        is Segment2DBokorys -> {
                            val i = state.segmentsBokorys.indexOfFirst { it.id == selectedSegmentRaw.id }
                            if (i != -1) {
                                val current = state.segmentsBokorys[i]
                                state.segmentsBokorys[i] = current.copy(localColor = newColor).withAxoVisibilityFrom(current)
                            }
                        }

                        is Segment2DAxo -> {
                            val i = state.segmentsAxo.indexOfFirst { it.id == selectedSegmentRaw.id }
                            if (i != -1) {
                                val current = state.segmentsAxo[i]
                                state.segmentsAxo[i] = current.copy(localColor = newColor).withAxoVisibilityFrom(current)
                            }
                        }

                        else -> return@EditableSegmentProjectionInfo
                    }

                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onWidthChange = { newWidth ->
                    when (selectedSegmentRaw) {
                        is Segment2DPudorys -> {
                            val i = state.segmentsPudorys.indexOfFirst { it.id == selectedSegmentRaw.id }
                            if (i != -1) {
                                val current = state.segmentsPudorys[i]
                                state.segmentsPudorys[i] = current.copy(localStrokeWidth = newWidth).withAxoVisibilityFrom(current)
                            }
                        }

                        is Segment2DNarys -> {
                            val i = state.segmentsNarys.indexOfFirst { it.id == selectedSegmentRaw.id }
                            if (i != -1) {
                                val current = state.segmentsNarys[i]
                                state.segmentsNarys[i] = current.copy(localStrokeWidth = newWidth).withAxoVisibilityFrom(current)
                            }
                        }

                        is Segment2DBokorys -> {
                            val i = state.segmentsBokorys.indexOfFirst { it.id == selectedSegmentRaw.id }
                            if (i != -1) {
                                val current = state.segmentsBokorys[i]
                                state.segmentsBokorys[i] = current.copy(localStrokeWidth = newWidth).withAxoVisibilityFrom(current)
                            }
                        }

                        is Segment2DAxo -> {
                            val i = state.segmentsAxo.indexOfFirst { it.id == selectedSegmentRaw.id }
                            if (i != -1) {
                                val current = state.segmentsAxo[i]
                                state.segmentsAxo[i] = current.copy(localStrokeWidth = newWidth).withAxoVisibilityFrom(current)
                            }
                        }

                        else -> return@EditableSegmentProjectionInfo
                    }

                    state.triggerRedraw++
                },
                onStyleChange = { newStyle ->

                    when (selectedSegmentRaw) {
                        is Segment2DPudorys -> {
                            val index = state.segmentsPudorys.indexOf(selectedSegmentRaw)
                            if (index != -1) {
                                state.segmentsPudorys[index] = selectedSegmentRaw.copy(
                                    localLineStyle = newStyle,
                                    id = selectedSegmentRaw.id
                                ).withAxoVisibilityFrom(selectedSegmentRaw)
                            }
                        }

                        is Segment2DNarys -> {
                            val index = state.segmentsNarys.indexOf(selectedSegmentRaw)
                            if (index != -1) {
                                state.segmentsNarys[index] = selectedSegmentRaw.copy(
                                    localLineStyle = newStyle,
                                    id = selectedSegmentRaw.id
                                ).withAxoVisibilityFrom(selectedSegmentRaw)
                            }
                        }
                        is Segment2DBokorys-> {
                            val index = state.segmentsBokorys.indexOf(selectedSegmentRaw)
                            if (index != -1) {
                                state.segmentsBokorys[index] = selectedSegmentRaw.copy(
                                    localLineStyle = newStyle,
                                    id = selectedSegmentRaw.id
                                ).withAxoVisibilityFrom(selectedSegmentRaw)
                            }
                        }
                        is Segment2DAxo -> {
                            val i = state.segmentsAxo.indexOf(selectedSegmentRaw)
                            if (i != -1) {
                                state.segmentsAxo[i]=
                                 selectedSegmentRaw.copy(
                                    localLineStyle = newStyle,
                                    id = selectedSegmentRaw.id
                                ).withAxoVisibilityFrom(selectedSegmentRaw)
                            }
                        }
                        else -> return@EditableSegmentProjectionInfo
                    }
                    commitSnapshot(state)

                },
                onShowInAxoChange = { checked ->
                    when (selectedSegmentRaw) {
                        is Segment2DPudorys -> {
                            setSegmentProjectionVisibleWithEndpoints(state, selectedSegmentRaw.id, checked, ProjectionKindSeg.PUDORYS)
                        }
                        is Segment2DNarys -> {
                            setSegmentProjectionVisibleWithEndpoints(state, selectedSegmentRaw.id, checked, ProjectionKindSeg.NARYS)
                        }
                        is Segment2DBokorys -> {
                            setSegmentProjectionVisibleWithEndpoints(state, selectedSegmentRaw.id, checked, ProjectionKindSeg.BOKORYS)
                        }
                        is Segment2DAxo -> {
                            setSegmentProjectionVisibleWithEndpoints(state, selectedSegmentRaw.id, checked, ProjectionKindSeg.AXO)
                        }
                        else -> {}
                    }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },
                onAddProjection = {
                    if (state.projectionMode == ProjectionMode.MONGE) {
                        CompleteSegmentAdd(state, selectedSegmentRaw)
                    } else if (state.projectionMode == ProjectionMode.AXO) {
                        when (selectedSegmentRaw) {
                            is Segment2DPudorys -> Unit
                            is Segment2DNarys -> Unit
                            is Segment2DBokorys -> Unit
                            is Segment2DAxo -> Unit
                            else -> {}
                        }
                    }

                                  },
                onDelete = {
                    deleteSegment2D(state = state, selectedSegmentRaw = selectedSegmentRaw)
                    clearSelection(state)
                },
                state = state,
                uiScale = SettingsManager.current.UIscale/75f


            )
        }
    }
}
fun applyHelpSegmentRename(helpseg: HelpSegments, newName: String, state: MongeState) {
    val base = newName.trim()

    val updated: HelpSegments? = when (helpseg) {
        is HelpSegmentNarys -> helpseg.copy(name = base)
        is HelpSegmentPudorys -> helpseg.copy(name = base)
        else -> null
    }

    when (updated) {
        is HelpSegmentPudorys -> {
            val i = state.helpSegmentsPudorys.indexOfFirst { it.id == updated.id }
            if (i != -1) state.helpSegmentsPudorys[i] = updated
        }
        is HelpSegmentNarys -> {
            val i = state.helpSegmentsNarys.indexOfFirst { it.id == updated.id }
            if (i != -1) state.helpSegmentsNarys[i] = updated
        }
        null -> return
    }

    commitSnapshot(state)
        }

private enum class ProjectionKindSeg { PUDORYS, NARYS, BOKORYS, AXO }

private fun setSegmentProjectionVisibleWithEndpoints(
    state: MongeState,
    segmentId: String,
    checked: Boolean,
    kind: ProjectionKindSeg
) {
    when (kind) {
        ProjectionKindSeg.PUDORYS -> {
            state.segmentsPudorys.replaceAll { seg ->
                if (seg.id == segmentId) seg.copy(showInAxoInitial = checked).also {
                    it.showInAxo = checked
                    it.start.showInAxoInitial = checked
                    it.start.showInAxo = checked
                    it.end.showInAxoInitial = checked
                    it.end.showInAxo = checked
                } else seg
            }
        }
        ProjectionKindSeg.NARYS -> {
            state.segmentsNarys.replaceAll { seg ->
                if (seg.id == segmentId) seg.copy(showInAxoInitial = checked).also {
                    it.showInAxo = checked
                    it.start.showInAxoInitial = checked
                    it.start.showInAxo = checked
                    it.end.showInAxoInitial = checked
                    it.end.showInAxo = checked
                } else seg
            }
        }
        ProjectionKindSeg.BOKORYS -> {
            state.segmentsBokorys.replaceAll { seg ->
                if (seg.id == segmentId) seg.copy(showInAxoInitial = checked).also {
                    it.showInAxo = checked
                    it.start.showInAxoInitial = checked
                    it.start.showInAxo = checked
                    it.end.showInAxoInitial = checked
                    it.end.showInAxo = checked
                } else seg
            }
        }
        ProjectionKindSeg.AXO -> {
            state.segmentsAxo.replaceAll { seg ->
                if (seg.id == segmentId) seg.copy(showInAxoInitial = checked).also {
                    it.showInAxo = checked
                    it.start.showInAxoInitial = checked
                    it.start.showInAxo = checked
                    it.end.showInAxoInitial = checked
                    it.end.showInAxo = checked
                } else seg
            }
        }
    }
        }
