package ui.mongeui.toolbar.rightDescriptionBar

import utils.replaceAll
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.LineStyle
import model.LocalMongeColors
import model.ProjectionMode
import model.classes.*
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.mongeui.toolbar.SkikoButton

@Composable
fun EditableParentCurveInfo(
    curve: Curve3D,
    canRename: Boolean = true,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onDelete: () -> Unit,
    onApply: (newName: String) -> Unit,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    var pendingColor by remember(curve.id, curve.color) {
        mutableStateOf(curve.color)
    }

    var sliderValue by remember(curve.id, curve.strokeWidth) {
        mutableStateOf(curve.strokeWidth)
    }

    val nameNow = curve.name.trim()

    var pendingName by remember(curve.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(curve.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(curve.id, curve.name) {
        val n = curve.name.trim()
        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }

    val canApply = pendingName.text.trim() != lastAppliedName

    fun apply() {
        if (!canRename || !canApply) return

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
            label = "Křivka:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { apply() },
            state = state,
            enabled = canRename,
            inputWidth = ui.dp(86f)
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

            MongeInspectorPropertyRow("Styl:") {
                LineStyleSelector(
                    current = curve.lineStyle,
                    onStyleChange = onStyleChange
                )
            }

        if (state.projectionMode == ProjectionMode.AXO) {
            val pudorys: CurvePudorys? = state.curvesPudorys.firstOrNull { it.parentId == curve.id }
            val narys: CurveNarys? = state.curvesNarys.firstOrNull { it.parentId == curve.id }
            val bokorys: CurveBokorys? = state.curvesBokorys.firstOrNull { it.parentId == curve.id }
            val axo: CurveAxo? = state.curvesAxo.firstOrNull { it.parentId == curve.id }

            if (pudorys != null || narys != null || bokorys != null || axo != null) {
                MongeDivider()
                MongeInspectorPropertyRow(label = "Průměty:", contentAlign = androidx.compose.ui.Alignment.End) {
                    ProjectionVisibilityToggleStrip(
                        ui = ui,
                        *listOfNotNull(
                            axo?.let { ProjectionVisibilityToggleItem("A", it.showInAxo) { v ->
                                it.showInAxo = v; commitSnapshot(state)
                            } },
                            pudorys?.let { ProjectionVisibilityToggleItem("P", it.showInAxo) { v ->
                                it.showInAxo = v; commitSnapshot(state)
                            } },
                            narys?.let { ProjectionVisibilityToggleItem("N", it.showInAxo) { v ->
                                it.showInAxo = v; commitSnapshot(state)
                            } },
                            bokorys?.let { ProjectionVisibilityToggleItem("B", it.showInAxo) { v ->
                                it.showInAxo = v; commitSnapshot(state)
                            } }
                        ).toTypedArray(),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                Text(
                    "Smazat",
                    fontSize = ui.sp(13f)
                )
            }
        }
    }
}
@Composable
fun EditableCurveProjectionInfo(
    title: String,
    nameNow: String,
    colorNow: Color,
    widthNow: Float,
    styleNow: LineStyle,
    showInAxoNow: Boolean = true,
    canRename: Boolean = true,
    onApplyName: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onShowInAxoChange: ((Boolean) -> Unit)? = null,
    onDelete: () -> Unit,
    state: MongeState,
    uiScale: Float
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    var pendingColor by remember(colorNow) {
        mutableStateOf(colorNow)
    }

    var sliderValue by remember(widthNow) {
        mutableStateOf(widthNow)
    }

    var pendingName by remember(nameNow) {
        mutableStateOf(TextFieldValue(nameNow.trim()))
    }

    var lastAppliedName by remember(nameNow) {
        mutableStateOf(nameNow.trim())
    }

    LaunchedEffect(nameNow) {
        val n = nameNow.trim()

        lastAppliedName = n
        pendingName = TextFieldValue(n)
    }

    val canApply = pendingName.text.trim() != lastAppliedName
    val isAxo = state.projectionMode == ProjectionMode.AXO
    fun apply() {
        if (!canRename || !canApply) return

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
            label = "$title:",
            ui = ui,
            value = pendingName,
            onValueChange = { pendingName = it },
            canApply = canApply,
            onApply = { apply() },
            state = state,
            enabled = canRename,
            inputWidth = ui.dp(86f)
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
            if (onShowInAxoChange != null&&isAxo) {
                val colors = LocalMongeColors.current
                MongeDivider()
                MongeInspectorPropertyRow("Zobrazit:") {
                    Checkbox(
                        checked = showInAxoNow,
                        onCheckedChange = onShowInAxoChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.selected,
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color.White
                        )
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
                    "Smazat",
                    fontSize = ui.sp(13f)
                )
            }
        }
    }
}
@Composable
fun curveEdit(state: MongeState) {

    val sel3DId = state.selectedCurve3DId
    val selPId  = state.selectedCurvePudorysId
    val selNId  = state.selectedCurveNarysId
    val selAId = state.selectedCurveAxoId

    // 1) 3D má prioritu
    val curve3D: Curve3D? = sel3DId?.let { id -> state.curves3D.firstOrNull { it.id == id } }

    if (curve3D != null) {
        key(curve3D.id) {
            EditableParentCurveInfo(
                curve = curve3D,

                onColorChange = { newColor ->
                    val idx = state.curves3D.indexOfFirst { it.id == curve3D.id }
                    if (idx != -1) {
                        val updated = state.curves3D[idx].copy(color = newColor)
                        state.curves3D[idx] = updated
                        relinkCurveProjectionsToParent(state, updated) // ✅
                    }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },

                onWidthChange = { newW ->
                    val idx = state.curves3D.indexOfFirst { it.id == curve3D.id }
                    if (idx != -1) {
                        val updated = state.curves3D[idx].copy(strokeWidth = newW)
                        state.curves3D[idx] = updated
                        relinkCurveProjectionsToParent(state, updated) // ✅
                    }
                    state.triggerRedraw++
                },

                onStyleChange = { newStyle ->
                    val idx = state.curves3D.indexOfFirst { it.id == curve3D.id }
                    if (idx != -1) {
                        val updated = state.curves3D[idx].copy(lineStyle = newStyle)
                        state.curves3D[idx] = updated
                        relinkCurveProjectionsToParent(state, updated) // ✅
                    }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },

                onApply = { newName ->
                    val cleaned = newName.trim()
                    val idx = state.curves3D.indexOfFirst { it.id == curve3D.id }
                    if (idx != -1) {
                        val updated = state.curves3D[idx].copy(name = cleaned)
                        state.curves3D[idx] = updated
                        relinkCurveProjectionsToParent(state, updated) // ✅ (kvůli effectiveName)
                    }
                    commitSnapshot(state)
                    state.triggerRedraw++
                },

                onDelete = {
                    deleteCurve3D(state, curve3D.id)
                    clearSelection(state)
                },

                state = state,
                uiScale = SettingsManager.current.UIscale/75f
            )
        }
        return
    }


    val selBId = state.selectedCurveBokorysId

    // 2) samostatný půdorys
    val curveP: CurvePudorys? = selPId?.let { id -> state.curvesPudorys.firstOrNull { it.id == id } }
    if (curveP != null) {
        curveP.parentId?.takeIf { parentId -> state.curves3D.any { it.id == parentId } }?.let { parentId ->
            state.selectedCurvePudorysId = null
            state.selectedCurve3DId = parentId
            return
        }

        key(curveP.id) {
            EditableCurveProjectionInfo(
                title = "Křivka₁",
                nameNow = curveP.name,
                colorNow = curveP.color,
                widthNow = curveP.strokeWidth,
                styleNow = curveP.lineStyle,
                showInAxoNow = curveP.showInAxo,
                onApplyName = { newName ->
                    val base = newName.trim()
                    val idx = state.curvesPudorys.indexOfFirst { it.id == curveP.id }
                    if (idx != -1) state.curvesPudorys[idx] = state.curvesPudorys[idx].copy(name = base)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onColorChange = { c ->
                    val idx = state.curvesPudorys.indexOfFirst { it.id == curveP.id }
                    if (idx != -1) state.curvesPudorys[idx] = state.curvesPudorys[idx].copy(color = c)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onWidthChange = { w ->
                    val idx = state.curvesPudorys.indexOfFirst { it.id == curveP.id }
                    if (idx != -1) state.curvesPudorys[idx] = state.curvesPudorys[idx].copy(strokeWidth = w)
                    state.triggerRedraw++
                },
                onStyleChange = { s ->
                    val idx = state.curvesPudorys.indexOfFirst { it.id == curveP.id }
                    if (idx != -1) state.curvesPudorys[idx] = state.curvesPudorys[idx].copy(lineStyle = s)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onShowInAxoChange = { show ->
                    val idx = state.curvesPudorys.indexOfFirst { it.id == curveP.id }
                    if (idx != -1) state.curvesPudorys[idx].showInAxo = show
                    commitSnapshot(state); state.triggerRedraw++
                },
                onDelete = {
                    deleteCurvePudorys(state, curveP.id)
                    clearSelection(state)
                },
                state=state,
                uiScale = SettingsManager.current.UIscale/75f
            )
        }
        return
    }

    // 3) samostatný nárys
    val curveN: CurveNarys? = selNId?.let { id -> state.curvesNarys.firstOrNull { it.id == id } }
    if (curveN != null) {
        curveN.parentId?.takeIf { parentId -> state.curves3D.any { it.id == parentId } }?.let { parentId ->
            state.selectedCurveNarysId = null
            state.selectedCurve3DId = parentId
            return
        }

        key(curveN.id) {
            EditableCurveProjectionInfo(
                title = "Křivka₂",
                nameNow = curveN.name,
                colorNow = curveN.color,
                widthNow = curveN.strokeWidth,
                styleNow = curveN.lineStyle,
                showInAxoNow = curveN.showInAxo,
                onApplyName = { newName ->
                    val base = newName.trim()
                    val idx = state.curvesNarys.indexOfFirst { it.id == curveN.id }
                    if (idx != -1) state.curvesNarys[idx] = state.curvesNarys[idx].copy(name = base)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onColorChange = { c ->
                    val idx = state.curvesNarys.indexOfFirst { it.id == curveN.id }
                    if (idx != -1) state.curvesNarys[idx] = state.curvesNarys[idx].copy(color = c)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onWidthChange = { w ->
                    val idx = state.curvesNarys.indexOfFirst { it.id == curveN.id }
                    if (idx != -1) state.curvesNarys[idx] = state.curvesNarys[idx].copy(strokeWidth = w)
                    state.triggerRedraw++
                },
                onStyleChange = { s ->
                    val idx = state.curvesNarys.indexOfFirst { it.id == curveN.id }
                    if (idx != -1) state.curvesNarys[idx] = state.curvesNarys[idx].copy(lineStyle = s)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onShowInAxoChange = { show ->
                    val idx = state.curvesNarys.indexOfFirst { it.id == curveN.id }
                    if (idx != -1) state.curvesNarys[idx].showInAxo = show
                    commitSnapshot(state); state.triggerRedraw++
                },
                onDelete = {
                    deleteCurveNarys(state, curveN.id)
                    clearSelection(state)
                },
                state=state,
                uiScale = SettingsManager.current.UIscale/75f
            )
        }
        return
    }

    // 4) bokorys křivka
    val curveB: CurveBokorys? = selBId?.let { id -> state.curvesBokorys.firstOrNull { it.id == id } }
    if (curveB != null) {
        curveB.parentId?.takeIf { parentId -> state.curves3D.any { it.id == parentId } }?.let { parentId ->
            state.selectedCurveBokorysId = null
            state.selectedCurve3DId = parentId
            return
        }

        key(curveB.id) {
            EditableCurveProjectionInfo(
                title = "Křivka₃",
                nameNow = curveB.name,
                colorNow = curveB.color,
                widthNow = curveB.strokeWidth,
                styleNow = curveB.lineStyle,
                showInAxoNow = curveB.showInAxo,
                onApplyName = { newName ->
                    val base = newName.trim()
                    val idx = state.curvesBokorys.indexOfFirst { it.id == curveB.id }
                    if (idx != -1) state.curvesBokorys[idx] = state.curvesBokorys[idx].copy(name = base)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onColorChange = { c ->
                    val idx = state.curvesBokorys.indexOfFirst { it.id == curveB.id }
                    if (idx != -1) state.curvesBokorys[idx] = state.curvesBokorys[idx].copy(color = c)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onWidthChange = { w ->
                    val idx = state.curvesBokorys.indexOfFirst { it.id == curveB.id }
                    if (idx != -1) state.curvesBokorys[idx] = state.curvesBokorys[idx].copy(strokeWidth = w)
                    state.triggerRedraw++
                },
                onStyleChange = { s ->
                    val idx = state.curvesBokorys.indexOfFirst { it.id == curveB.id }
                    if (idx != -1) state.curvesBokorys[idx] = state.curvesBokorys[idx].copy(lineStyle = s)
                    commitSnapshot(state); state.triggerRedraw++
                },
                onShowInAxoChange = { show ->
                    val idx = state.curvesBokorys.indexOfFirst { it.id == curveB.id }
                    if (idx != -1) state.curvesBokorys[idx].showInAxo = show
                    commitSnapshot(state); state.triggerRedraw++
                },
                onDelete = {
                    deleteCurveBokorys(state, curveB.id)
                    clearSelection(state)
                },
                state=state,
                uiScale = SettingsManager.current.UIscale/75f
            )
        }
        return
    }

    // 5) axo křivka
    val curveA: CurveAxo? = selAId?.let { id -> state.curvesAxo.firstOrNull { it.id == id } }
    if (curveA != null) {
        curveA.parentId?.takeIf { parentId -> state.curves3D.any { it.id == parentId } }?.let { parentId ->
            state.selectedCurveAxoId = null
            state.selectedCurve3DId = parentId
            return
        }

        key(curveA.id) {
            EditableCurveProjectionInfo(
                title = "Křivka axo",
                nameNow = curveA.name,
                colorNow = curveA.color,
                widthNow = curveA.strokeWidth,
                styleNow = curveA.lineStyle,
                showInAxoNow = curveA.showInAxo,
                onApplyName = { newName ->
                    val base = newName.trim()
                    val idx = state.curvesAxo.indexOfFirst { it.id == curveA.id }
                    if (idx != -1) state.curvesAxo[idx] = state.curvesAxo[idx].copy(name = base).also { it.showInAxo = curveA.showInAxo }
                    commitSnapshot(state); state.triggerRedraw++
                },
                onColorChange = { c ->
                    val idx = state.curvesAxo.indexOfFirst { it.id == curveA.id }
                    if (idx != -1) state.curvesAxo[idx] = state.curvesAxo[idx].copy(color = c).also { it.showInAxo = curveA.showInAxo }
                    commitSnapshot(state); state.triggerRedraw++
                },
                onWidthChange = { w ->
                    val idx = state.curvesAxo.indexOfFirst { it.id == curveA.id }
                    if (idx != -1) state.curvesAxo[idx] = state.curvesAxo[idx].copy(strokeWidth = w).also { it.showInAxo = curveA.showInAxo }
                    state.triggerRedraw++
                },
                onStyleChange = { s ->
                    val idx = state.curvesAxo.indexOfFirst { it.id == curveA.id }
                    if (idx != -1) state.curvesAxo[idx] = state.curvesAxo[idx].copy(lineStyle = s).also { it.showInAxo = curveA.showInAxo }
                    commitSnapshot(state); state.triggerRedraw++
                },
                onShowInAxoChange = { show ->
                    val idx = state.curvesAxo.indexOfFirst { it.id == curveA.id }
                    if (idx != -1) state.curvesAxo[idx].showInAxo = show
                    commitSnapshot(state); state.triggerRedraw++
                },
                onDelete = {
                    deleteCurveAxo(state, curveA.id)
                    clearSelection(state)
                },
                state = state,
                uiScale = SettingsManager.current.UIscale / 75f
            )
        }
    }
}
fun deleteCurve3D(state: MongeState, curve3DId: String) {
    val exists = state.curves3D.any { it.id == curve3DId }
    if (!exists) {
        val resolved = resolveSelectedCurve3DId(state, curve3DId)
        if (resolved == null || !state.curves3D.any { it.id == resolved }) return
        deleteCurve3D(state, resolved)
        return
    }

    state.curvesPudorys.removeAll { it.parentId == curve3DId }
    state.curvesNarys.removeAll { it.parentId == curve3DId }
    state.curvesBokorys.removeAll { it.parentId == curve3DId }
    state.curvesAxo.removeAll { it.parentId == curve3DId }
    state.curves3D.removeAll { it.id == curve3DId }

    if (state.selectedCurve3DId == curve3DId) state.selectedCurve3DId = null
    state.selectedCurvePudorysId = null
    state.selectedCurveNarysId = null
    state.selectedCurveBokorysId = null
    state.selectedCurveAxoId = null

    state.triggerRedraw++
    commitSnapshot(state)
}

fun deleteCurvePudorys(state: MongeState, curvePId: String) {
    val fresh = state.curvesPudorys.firstOrNull { it.id == curvePId }
        ?: state.curvesPudorys.firstOrNull { it.id == state.selectedCurvePudorysId }
        ?: return

    val parentId = fresh.parentId
    if (parentId != null && state.curves3D.any { it.id == parentId }) {
        deleteCurve3D(state, parentId)
        return
    }

    state.curvesPudorys.removeAll { it.id == fresh.id }
    if (state.selectedCurvePudorysId == fresh.id) state.selectedCurvePudorysId = null

    state.triggerRedraw++
    commitSnapshot(state)
}

fun deleteCurveNarys(state: MongeState, curveNId: String) {
    val fresh = state.curvesNarys.firstOrNull { it.id == curveNId }
        ?: state.curvesNarys.firstOrNull { it.id == state.selectedCurveNarysId }
        ?: return

    val parentId = fresh.parentId
    if (parentId != null && state.curves3D.any { it.id == parentId }) {
        deleteCurve3D(state, parentId)
        return
    }

    state.curvesNarys.removeAll { it.id == fresh.id }
    if (state.selectedCurveNarysId == fresh.id) state.selectedCurveNarysId = null

    state.triggerRedraw++
    commitSnapshot(state)
}

fun deleteCurveBokorys(state: MongeState, curveBId: String) {
    val fresh = state.curvesBokorys.firstOrNull { it.id == curveBId }
        ?: state.curvesBokorys.firstOrNull { it.id == state.selectedCurveBokorysId }
        ?: return

    val parentId = fresh.parentId
    if (parentId != null && state.curves3D.any { it.id == parentId }) {
        deleteCurve3D(state, parentId)
        return
    }

    state.curvesBokorys.removeAll { it.id == fresh.id }
    if (state.selectedCurveBokorysId == fresh.id) state.selectedCurveBokorysId = null

    state.triggerRedraw++
    commitSnapshot(state)
}

fun deleteCurveAxo(state: MongeState, curveAId: String) {
    val fresh = state.curvesAxo.firstOrNull { it.id == curveAId }
        ?: state.curvesAxo.firstOrNull { it.id == state.selectedCurveAxoId }
        ?: return

    val parentId = fresh.parentId
    if (parentId != null && state.curves3D.any { it.id == parentId }) {
        deleteCurve3D(state, parentId)
        return
    }

    state.curvesAxo.removeAll { it.id == fresh.id }
    if (state.selectedCurveAxoId == fresh.id) state.selectedCurveAxoId = null

    state.triggerRedraw++
    commitSnapshot(state)
}

fun relinkCurveProjectionsToParent(
    state: MongeState,
    updatedParent: Curve3D
) {
    state.curvesPudorys.replaceAll { c ->
        if (c.parentId == updatedParent.id) c.copy(parent = updatedParent) else c
    }
    state.curvesNarys.replaceAll { c ->
        if (c.parentId == updatedParent.id) c.copy(parent = updatedParent) else c
    }
    state.curvesBokorys.replaceAll { c ->
        if (c.parentId == updatedParent.id) c.copy(parent = updatedParent) else c
    }
    state.curvesAxo.replaceAll { c ->
        if (c.parentId == updatedParent.id) c.copy(parent = updatedParent) else c
    }
}
fun resolveSelectedCurve3DId(state: MongeState, fallbackId: String?): String? {
    state.selectedCurve3DId?.let { return it }

    state.selectedCurvePudorysId?.let { pid ->
        val c = state.curvesPudorys.firstOrNull { it.id == pid }
        c?.parentId?.let { return it }
    }
    state.selectedCurveNarysId?.let { nid ->
        val c = state.curvesNarys.firstOrNull { it.id == nid }
        c?.parentId?.let { return it }
    }
    state.selectedCurveBokorysId?.let { bid ->
        val c = state.curvesBokorys.firstOrNull { it.id == bid }
        c?.parentId?.let { return it }
    }
    state.selectedCurveAxoId?.let { aid ->
        val c = state.curvesAxo.firstOrNull { it.id == aid }
        c?.parentId?.takeIf { parentId -> state.curves3D.any { it.id == parentId } }?.let { return it }
    }

    return fallbackId
}
