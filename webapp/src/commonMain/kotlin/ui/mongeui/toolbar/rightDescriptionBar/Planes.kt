package ui.mongeui.toolbar.rightDescriptionBar
import utils.replaceAll
import state.relinkPlaneToTraces
import androidx.compose.foundation.layout.*
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Text
import androidx.compose.material.TriStateCheckbox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import serialization.setAll
import model.LineStyle
import model.LocalMongeColors
import model.ProjectionMode
import model.classes.*
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import ui.components.MongeSectionTitle
import ui.mongeui.toolbar.SkikoButton
@Composable
fun EditablePlaneInfo(
    planeId: String,
    state: MongeState,
    onRename: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onDelete: () -> Unit,
    uiScale: Float
) {
    val plane = state.planes3D.find { it.id == planeId } ?: return
    var pendingColor by remember { mutableStateOf(plane.color) }
    var sliderValue  by remember { mutableStateOf(plane.strokeWidth) }
    val colors       = LocalMongeColors.current
    val isKoto = state.projectionMode == ProjectionMode.KOTO
    // --- najdi aktuální stopy téhle roviny ---
    val pTrace = state.lineTracesPudorys.firstOrNull { it.parent?.id == planeId || it.parentId == planeId }
    val nTrace = state.lineTracesNarys.firstOrNull { it.parent?.id == planeId || it.parentId == planeId }
    val bTrace = state.lineTracesBokorys.firstOrNull { it.parent?.id == planeId || it.parentId == planeId }
    val ui = remember(uiScale) { UiScale(uiScale) }
    var pendingName by remember(plane.id) { mutableStateOf(TextFieldValue(plane.name.trim())) }
    var lastAppliedName by remember(plane.id) { mutableStateOf(plane.name.trim()) }
    LaunchedEffect(plane.id, plane.name) {
        val n = plane.name.trim()
        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }
    val canApplyName = pendingName.text.trim() != lastAppliedName
    fun applyName() {
        if (!canApplyName) return
        val n = pendingName.text.trim()
        onRename(n)
        lastAppliedName = n
    }
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        /* ===== Název ===== */
        SimpleNameEditor(
            label = "Rovina:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApplyName,
            onApply = { applyName() },
            state = state,
            onGreekSymbol = { symbol ->
                pendingName = TextFieldValue(symbol)
            }
        )
        MongeDivider()
            MongeInspectorPropertyRow("Barva:") {
                ColorPickerDropdown(
                    selectedColor  = pendingColor,
                    onColorPreview = { pendingColor = it },
                    onColorConfirm = onColorChange
                )
            }
            MongeDivider()
            MongeInspectorPropertyRow("Šířka:") {
                WidthEditor(
                    value = sliderValue,
                    onValueChange = { v ->
                        sliderValue = v
                        onWidthChange(v)
                    },
                    state = state
                )
            }
            MongeDivider()
            MongeInspectorPropertyRow("Styl čáry:") {
                LineStyleSelector(
                    current = plane.lineStyle,
                    onStyleChange = onStyleChange
                )
            }
        MongeDivider()
        if (!isKoto && state.projectionMode == ProjectionMode.AXO) {
            val projectionItems = listOfNotNull(
                pTrace?.let { trace ->
                    ProjectionVisibilityToggleItem("P", trace.showInAxo) { checked ->
                        updatePlaneTracePudorysAxoVisibility(state, trace.id, checked)
                    }
                },
                nTrace?.let { trace ->
                    ProjectionVisibilityToggleItem("N", trace.showInAxo) { checked ->
                        updatePlaneTraceNarysAxoVisibility(state, trace.id, checked)
                    }
                },
                bTrace?.let { trace ->
                    ProjectionVisibilityToggleItem("B", trace.showInAxo) { checked ->
                        updatePlaneTraceBokorysAxoVisibility(state, trace.id, checked)
                    }
                }
            )
            if (projectionItems.isNotEmpty()) {
                LabeledRow(
                    label = "Průměty:",
                    ui = ui,
                    contentAlign = Alignment.End
                ) {
                    ProjectionVisibilityToggleStrip(
                        ui,
                        *projectionItems.toTypedArray()
                    )
                }
                MongeDivider()
            }
            MongeSectionTitle("Ořez os")
            pTrace?.let { trace ->
                PlaneTraceClipRow("Půdorys osa X", trace.clipLineX, state.defaultClipBelowX12Pudorys, ui) { next ->
                    updatePlaneTracePudorysClipX(state, trace.id, next)
                }
                PlaneTraceClipRow("Půdorys osa Y", trace.clipLineY, state.defaultClipLeftOfYAxisPudorys, ui) { next ->
                    updatePlaneTracePudorysClipY(state, trace.id, next)
                }
            }
            nTrace?.let { trace ->
                PlaneTraceClipRow("Nárys osa X", trace.clipLineX, state.defaultClipAboveX12Narys, ui) { next ->
                    updatePlaneTraceNarysClipX(state, trace.id, next)
                }
                PlaneTraceClipRow("Nárys osa Z", trace.clipLineZ, state.defaultClipLeftOfZAxisNarys, ui) { next ->
                    updatePlaneTraceNarysClipZ(state, trace.id, next)
                }
            }
            bTrace?.let { trace ->
                PlaneTraceClipRow("Bokorys osa Y", trace.clipLineY, state.defaultClipBelowYAxisBokorys, ui) { next ->
                    updatePlaneTraceBokorysClipY(state, trace.id, next)
                }
                PlaneTraceClipRow("Bokorys osa Z", trace.clipLineZ, state.defaultClipLeftOfZAxisBokorys, ui) { next ->
                    updatePlaneTraceBokorysClipZ(state, trace.id, next)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SkikoButton(
                    width = 100.dp,
                    height = 40.dp,
                    onClick = onDelete,
                ) { Text("Smazat") }
            }
        }
        if (!isKoto && state.projectionMode != ProjectionMode.AXO){
        /* ===== Ořez X₁₂ (půdorys) ===== */
        MongeSectionTitle("Ořez X₁₂ (půdorys)")
        if (pTrace != null) {
            var uiP by remember(pTrace.id, pTrace.clipLineX) { mutableStateOf(pTrace.clipLineX) }
            LaunchedEffect(pTrace.id, pTrace.clipLineX) { uiP = pTrace.clipLineX }
            val stateP = when (uiP) {
                null  -> ToggleableState.Indeterminate
                true  -> ToggleableState.On
                false -> ToggleableState.Off
            }
            val statusP = when (uiP) {
                null  -> if (state.defaultClipBelowX12Pudorys) "Dle nastavení ✓" else "Dle nastavení ✕"
                true  -> "Ořezáno"
                false -> "Neořezáno"
            }
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(
                    state = stateP,
                    onClick = {
                        val next = when (uiP) { null -> true; true -> false; false -> null }
                        uiP = next
                        // copy-back do hlavního i selected seznamu
                        val i = state.lineTracesPudorys.indexOfFirst { it.id == pTrace.id }
                        if (i >= 0) state.lineTracesPudorys[i] =
                            state.lineTracesPudorys[i].copy(clipLineX = next)
                        val si = state.selectedTracesPudorys.indexOfFirst { it.id == pTrace.id }
                        if (si >= 0) {
                            val old = state.selectedTracesPudorys[si]
                            if (true)
                                state.selectedTracesPudorys[si] = old.copy(clipLineX = next)
                        }
                         commitSnapshot(state)
                    },
                    modifier = Modifier.size(24.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.selected,
                        uncheckedColor = Color.Gray,
                        checkmarkColor = Color.White
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(statusP, fontSize = 14.sp, maxLines = 1, softWrap = false, color = colors.text)
            }
        } else {
            Text("Tahle rovina nemá stopu v půdorysu.", fontSize = 12.sp, color = Color.Gray)
        }
        MongeDivider()
        /* ===== Ořez X₁₂ (nárys) ===== */
        MongeSectionTitle("Ořez X₁₂ (nárys)")
        if (nTrace != null) {
            var uiN by remember(nTrace.id, nTrace.clipLineX) { mutableStateOf(nTrace.clipLineX) }
            LaunchedEffect(nTrace.id, nTrace.clipLineX) { uiN = nTrace.clipLineX }
            val stateN = when (uiN) {
                null  -> ToggleableState.Indeterminate
                true  -> ToggleableState.On
                false -> ToggleableState.Off
            }
            val statusN = when (uiN) {
                null  -> if (state.defaultClipAboveX12Narys) "Dle nastavení ✓" else "Dle nastavení ✕"
                true  -> "Ořezáno"
                false -> "Neořezáno"
            }
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(
                    state = stateN,
                    onClick = {
                        val next = when (uiN) { null -> true; true -> false; false -> null }
                        uiN = next
                        val i = state.lineTracesNarys.indexOfFirst { it.id == nTrace.id }
                        if (i >= 0) state.lineTracesNarys[i] =
                            state.lineTracesNarys[i].copy(clipLineX = next)
                        val si = state.selectedTracesNarys.indexOfFirst { it.id == nTrace.id }
                        if (si >= 0) {
                            val old = state.selectedTracesNarys[si]
                            if (true)
                                state.selectedTracesNarys[si] = old.copy(clipLineX = next)
                        }
                         commitSnapshot(state)
                    },
                    modifier = Modifier.size(24.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.selected,
                        uncheckedColor = Color.Gray,
                        checkmarkColor = Color.White
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(statusN, fontSize = 14.sp, maxLines = 1, softWrap = false, color = colors.text)
            }
        } else {
            Text("Tahle rovina nemá stopu v nárysu.", fontSize = 12.sp, color = Color.Gray)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            SkikoButton(
                width = 100.dp,
                height = 40.dp,
                onClick = onDelete,
                ) { Text("Smazat") }
        }
    }
}
}
// Jednotný vzhled a chování s ořezy přímek (checkbox ukotvený vpravo).
@Composable
private fun PlaneTraceClipRow(
    label: String,
    value: Boolean?,
    globalDefault: Boolean,
    ui: UiScale,
    onChange: (Boolean?) -> Unit
) {
    TriStateClipSettingRow(
        label = label,
        value = value,
        ui = ui,
        globalDefault = globalDefault,
        onChange = onChange
    )
}
private fun updatePlaneTracePudorysClipX(state: MongeState, id: String, value: Boolean?) {
    state.lineTracesPudorys.replaceAll {
        if (it.id == id) it.copy(clipLineX = value) else it
    }
    state.selectedTracesPudorys.replaceAll {
        if (it.id == id) it.copy(clipLineX = value) else it
    }
    commitSnapshot(state)
}
private fun updatePlaneTracePudorysAxoVisibility(state: MongeState, id: String, visible: Boolean) {
    val i = state.lineTracesPudorys.indexOfFirst { it.id == id }
    if (i >= 0) {
        state.lineTracesPudorys[i] = state.lineTracesPudorys[i].copy(showInAxo = visible)
    }
    val si = state.selectedTracesPudorys.indexOfFirst { it.id == id }
    if (si >= 0) {
        state.selectedTracesPudorys[si] = state.selectedTracesPudorys[si].copy(showInAxo = visible)
    }
    state.triggerRedraw++
    commitSnapshot(state)
}
private fun updatePlaneTraceNarysAxoVisibility(state: MongeState, id: String, visible: Boolean) {
    val i = state.lineTracesNarys.indexOfFirst { it.id == id }
    if (i >= 0) {
        state.lineTracesNarys[i] = state.lineTracesNarys[i].copy(showInAxo = visible)
    }
    val si = state.selectedTracesNarys.indexOfFirst { it.id == id }
    if (si >= 0) {
        state.selectedTracesNarys[si] = state.selectedTracesNarys[si].copy(showInAxo = visible)
    }
    state.triggerRedraw++
    commitSnapshot(state)
}
private fun updatePlaneTraceBokorysAxoVisibility(state: MongeState, id: String, visible: Boolean) {
    val i = state.lineTracesBokorys.indexOfFirst { it.id == id }
    if (i >= 0) {
        state.lineTracesBokorys[i] = state.lineTracesBokorys[i].copy(showInAxo = visible)
    }
    val si = state.selectedTracesBokorys.indexOfFirst { it.id == id }
    if (si >= 0) {
        state.selectedTracesBokorys[si] = state.selectedTracesBokorys[si].copy(showInAxo = visible)
    }
    state.triggerRedraw++
    commitSnapshot(state)
}
private fun updatePlaneTracePudorysClipY(state: MongeState, id: String, value: Boolean?) {
    state.lineTracesPudorys.replaceAll {
        if (it.id == id) it.copy(clipLineY = value) else it
    }
    state.selectedTracesPudorys.replaceAll {
        if (it.id == id) it.copy(clipLineY = value) else it
    }
    commitSnapshot(state)
}
private fun updatePlaneTraceNarysClipX(state: MongeState, id: String, value: Boolean?) {
    state.lineTracesNarys.replaceAll {
        if (it.id == id) it.copy(clipLineX = value) else it
    }
    state.selectedTracesNarys.replaceAll {
        if (it.id == id) it.copy(clipLineX = value) else it
    }
    commitSnapshot(state)
}
private fun updatePlaneTraceNarysClipZ(state: MongeState, id: String, value: Boolean?) {
    state.lineTracesNarys.replaceAll {
        if (it.id == id) it.copy(clipLineZ = value) else it
    }
    state.selectedTracesNarys.replaceAll {
        if (it.id == id) it.copy(clipLineZ = value) else it
    }
    commitSnapshot(state)
}
private fun updatePlaneTraceBokorysClipY(state: MongeState, id: String, value: Boolean?) {
    state.lineTracesBokorys.replaceAll {
        if (it.id == id) it.copy(clipLineY = value) else it
    }
    state.selectedTracesBokorys.replaceAll {
        if (it.id == id) it.copy(clipLineY = value) else it
    }
    commitSnapshot(state)
}
private fun updatePlaneTraceBokorysClipZ(state: MongeState, id: String, value: Boolean?) {
    state.lineTracesBokorys.replaceAll {
        if (it.id == id) it.copy(clipLineZ = value) else it
    }
    state.selectedTracesBokorys.replaceAll {
        if (it.id == id) it.copy(clipLineZ = value) else it
    }
    commitSnapshot(state)
}
@Composable
fun EditableTraceProjectionInfo(
    trace: Trace2DProjection,
    onRename: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onAddProjection: () -> Unit,
    onDelete: () -> Unit,
    clipOverride: Boolean?,
    globalDefaultClip: Boolean,
    onClipOverrideChange: (Boolean?) -> Unit,
    axoClipContent: (@Composable () -> Unit)? = null,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    var pendingColor by remember { mutableStateOf(trace.color) }
    var sliderValue by remember { mutableStateOf(trace.strokeWidth) }
    var pendingName by remember(trace.id) { mutableStateOf(TextFieldValue(trace.name.orEmpty().trim())) }
    var lastAppliedName by remember(trace.id) { mutableStateOf(trace.name.orEmpty().trim()) }
    val colors = LocalMongeColors.current
    LaunchedEffect(trace.id, trace.name) {
        val n = trace.name.orEmpty().trim()
        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }
    val canApplyName = pendingName.text.trim() != lastAppliedName
    fun applyName() {
        if (!canApplyName) return
        val n = pendingName.text.trim()
        onRename(n)
        lastAppliedName = n
    }
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        SimpleNameEditor(
            label = "Stopa:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApplyName,
            onApply = { applyName() },
            state = state,
            onGreekSymbol = { symbol ->
                pendingName = TextFieldValue(symbol)
            }
        )
        MongeDivider()
        MongeInspectorSection("") {
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
                    current = trace.lineStyle,
                    onStyleChange = onStyleChange
                )
            }
        }
        MongeDivider()
        val isKoto = state.projectionMode == ProjectionMode.KOTO
        val isAxo = state.projectionMode == ProjectionMode.AXO
if (!isKoto && !isAxo) {
            MongeDivider()
            MongeInspectorSection("Ořez X₁₂") {
                var clipUi by remember(trace.id, clipOverride) { mutableStateOf(clipOverride) }
                LaunchedEffect(trace.id, clipOverride) { clipUi = clipOverride }
                val toggleState = when (clipUi) {
                    null -> ToggleableState.Indeterminate
                    true -> ToggleableState.On
                    false -> ToggleableState.Off
                }
                val statusText = when (clipUi) {
                    null -> if (globalDefaultClip) "Dle nastavení ✓" else "Dle nastavení ✕"
                    true -> "Ořezáno"
                    false -> "Neořezáno"
                }
                Row(
                    Modifier.fillMaxWidth().height(36.dp),
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
                        modifier = Modifier.size(24.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.selected,
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = colors.text
                    )
                }
            }
            MongeDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SkikoButton(
                    width = 100.dp,
                    height = 40.dp,
                    onClick = onDelete,
                ) { Text("Smazat") }
            }
        } else if (isAxo) {
            MongeInspectorSection("Ořez os") {
                axoClipContent?.invoke()
            }
            MongeDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SkikoButton(
                    width = 100.dp,
                    height = 40.dp,
                    onClick = onDelete,
                ) { Text("Smazat") }
            }
        }
    }
}
fun deleteTrace2D (state: MongeState, fresh: Trace2DProjection)
{
    val parent = fresh.parent
    if (parent != null) {
        // 1) Najdi obÄ› stopy tĂ©Ĺľe 3D roviny
        val pudorysy = state.lineTracesPudorys.filter { it.parent === parent }
        val narysy   = state.lineTracesNarys  .filter { it.parent === parent }
        // 2) ZruĹˇ label offsety a vĂ˝bÄ›ry
        pudorysy.forEach { t ->
            state.labelOffsetsTracePudorys.remove(t.id)
            state.selectedTracesPudorys.remove(t)
        }
        narysy.forEach { t ->
            state.labelOffsetsTraceNarys.remove(t.id)
            state.selectedTracesNarys.remove(t)
        }
        // 3) SmaĹľ ze seznamĹŻ
        state.lineTracesPudorys.removeAll(pudorysy.toSet())
        state.lineTracesNarys  .removeAll(narysy.toSet())
        // 4) Ăšklid pĹ™Ă­padnĂ˝ch stavĹŻ
        if (state.rename.planeBeingRenamed === parent) state.rename.planeBeingRenamed = null
        if (state.projectionPhase == "rename_plane" && state.rename.planeBeingRenamed === parent) {
            state.projectionPhase = ""
        }
    } else {
        // Bez parenta â€“ smaĹľ jen tuto projekci
        when (fresh) {
            is PlaneTracePudorys -> {
                state.labelOffsetsTracePudorys.remove(fresh.id)
                state.selectedTracesPudorys.remove(fresh)
                state.lineTracesPudorys.removeAll { it.id == fresh.id }
            }
            is PlaneTraceNarys -> {
                state.labelOffsetsTraceNarys.remove(fresh.id)
                state.selectedTracesNarys.remove(fresh)
                state.lineTracesNarys.removeAll { it.id == fresh.id }
            }
            is PlaneTraceBokorys -> {
                state.labelOffsetsTraceBokorys.remove(fresh.id)
                state.selectedTracesBokorys.remove(fresh)
                state.lineTracesBokorys.removeAll { it.id == fresh.id }
            }
        }
    }
    commitSnapshot(state)
    state.triggerRedraw++
}
fun deleteHelpLinePudorys(state: MongeState, line: HelpLinePudorys) {
    state.helpLinePudorys.removeAll { it.id == line.id }
    commitSnapshot(state)
}
fun deleteHelpLineNarys(state: MongeState, line: HelpLineNarys) {
    state.helpLineNarys.removeAll { it.id == line.id }
    commitSnapshot(state)
}
fun deletePlane(state:MongeState, selectedPlane: Plane3D) {
    val planeId = selectedPlane.id
    if (state.planes3D.none { it.id == planeId }) return
    // 1) Najdi stopy roviny
    val pudorysy = state.lineTracesPudorys.filter { it.parent?.id == planeId }
    val narysy = state.lineTracesNarys.filter { it.parent?.id == planeId }
    val bokorysy = state.lineTracesBokorys.filter { it.parent?.id == planeId }
    // 2) ZruĹˇ vĂ˝bÄ›ry a offsety popiskĹŻ
    pudorysy.forEach { t ->
        state.selectedTracesPudorys.remove(t)
        state.labelOffsetsTracePudorys.remove(t.id)
    }
    narysy.forEach { t ->
        state.selectedTracesNarys.remove(t)
        state.labelOffsetsTraceNarys.remove(t.id)
    }
    bokorysy.forEach { t ->
        state.selectedTracesBokorys.remove(t)
        state.labelOffsetsTraceBokorys.remove(t.id)
    }
    // 3) Smaž stopy
    state.lineTracesPudorys.removeAll(pudorysy.toSet())
    state.lineTracesNarys.removeAll(narysy.toSet())
    state.lineTracesBokorys.removeAll(bokorysy.toSet())
    // 4) Smaž samotnou rovinu
    state.planes3D.removeAll { it.id == planeId }
    // 5) Úklid stavů souvisejících s přejmenováním
    if (state.rename.planeBeingRenamed?.id == planeId) state.rename.planeBeingRenamed = null
    if (state.projectionPhase == "rename_plane") state.projectionPhase = ""
    commitSnapshot(state)
    state.triggerRedraw++
}
@Composable
fun planeEdit(state: MongeState){
    val selectedPlane = state.selectedPlanes.firstOrNull()
    val isPudorysna = selectedPlane?.id == "plane-pudorysna"
    val isNarysna = selectedPlane?.id == "plane-narysna"
    val isBokorysna = selectedPlane?.id == "plane-bokorysna"
    if (isNarysna) {
        Text("Nárysna", color = LocalMongeColors.current.text)
    }
    if (isPudorysna) {
        Text("Půdorysna",color=LocalMongeColors.current.text)
    }
    if (isBokorysna) {
        Text("Bokorysna",color=LocalMongeColors.current.text)
    }
    else {
        selectedPlane?.let { plane ->
        key(plane) {
            EditablePlaneInfo(
                planeId = selectedPlane.id,
                state = state,
                onColorChange = { newColor ->
                    val idx = state.planes3D.indexOfFirst { it.id == selectedPlane.id }; if (idx < 0) return@EditablePlaneInfo
                    val updated = state.planes3D[idx].copy(color = newColor)
                    state.planes3D[idx] = updated
                    relinkPlaneToTraces(state, updated, clearLocal = true)
                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                        onStyleChange = { newStyle ->
                    val idx = state.planes3D.indexOfFirst { it.id == selectedPlane.id }; if (idx < 0) return@EditablePlaneInfo
                    val updated = state.planes3D[idx].copy(lineStyle = newStyle)
                    state.planes3D[idx] = updated
                    relinkPlaneToTraces(state, updated, clearLocal = true)
                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                        onWidthChange = { newWidth ->
                    val idx = state.planes3D.indexOfFirst { it.id == selectedPlane.id }; if (idx < 0) return@EditablePlaneInfo
                    val updated = state.planes3D[idx].copy(strokeWidth = newWidth)
                    state.planes3D[idx] = updated
                    relinkPlaneToTraces(state, updated, clearLocal = false)
                    state.triggerRedraw++
                },
                        onRename = { newName ->
                    val idx = state.planes3D.indexOfFirst { it.id == selectedPlane.id }; if (idx < 0) return@EditablePlaneInfo
                    val updated = state.planes3D[idx].copy(name = newName)
                    state.planes3D[idx] = updated
                    relinkPlaneToTraces(state, updated, clearLocal = true)
                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                        onDelete = {
                    deletePlane(state,plane)
                    clearSelection(state)
                },
                uiScale = SettingsManager.current.UIscale/75f
            )
        }
    }
    }
}
