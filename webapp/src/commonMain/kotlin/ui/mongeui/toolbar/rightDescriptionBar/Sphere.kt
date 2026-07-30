package ui.mongeui.toolbar.rightDescriptionBar
import utils.replaceAll

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.*
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.ConicSectionAxo
import model.classes.ConicSectionBokorys
import model.classes.SphereSurface3D
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.SkikoButton

enum class SphereProjectionKind { AXO, PUDORYS, NARYS, BOKORYS }

private fun setSphereProjectionVisibility(
    state: MongeState,
    sphere: SphereSurface3D,
    kind: SphereProjectionKind,
    checked: Boolean
) {
    when (kind) {
        SphereProjectionKind.PUDORYS ->
            state.conicsPudorys.filter { it.parentId == sphere.id }.forEach {
                it.showInAxo = checked; it.showInAxoInitial = checked
            }
        SphereProjectionKind.NARYS ->
            state.conicsNarys.filter { it.parentId == sphere.id }.forEach {
                it.showInAxo = checked; it.showInAxoInitial = checked
            }
        SphereProjectionKind.BOKORYS ->
            state.conicsBokorys.filter { it.parentId == sphere.id }.forEach {
                it.showInAxo = checked; it.showInAxoInitial = checked
            }
        SphereProjectionKind.AXO ->
            state.conicsAxo.filter { it.parentId == sphere.id }.forEach {
                it.showInAxo = checked; it.showInAxoInitial = checked
            }
    }
    commitSnapshot(state)
    state.triggerRedraw++
}

private fun sphereProjectionVisible(
    state: MongeState,
    sphere: SphereSurface3D,
    kind: SphereProjectionKind
): Boolean = when (kind) {
    SphereProjectionKind.PUDORYS ->
        state.conicsPudorys.any { it.parentId == sphere.id && it.showInAxo }
    SphereProjectionKind.NARYS ->
        state.conicsNarys.any { it.parentId == sphere.id && it.showInAxo }
    SphereProjectionKind.BOKORYS ->
        state.conicsBokorys.any { it.parentId == sphere.id && it.showInAxo }
    SphereProjectionKind.AXO ->
        state.conicsAxo.any { it.parentId == sphere.id && it.showInAxo }
}

@Composable
fun EditableSphereInfo(
    sphere: SphereSurface3D,
    onPudorysStyleChange: (LineStyle) -> Unit,
    onNarysStyleChange: (LineStyle) -> Unit,
    onApplyName: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onDelete: () -> Unit,
    state: MongeState,
    uiScale: Float = SettingsManager.current.UIscale/75f
) {
    val ui = remember(uiScale) { UiScale(uiScale) }
    val colors = LocalMongeColors.current

    val narys = state.conicsNarys.firstOrNull { it.parentId == sphere.id }
    val pudorys = state.conicsPudorys.firstOrNull { it.parentId == sphere.id }

    val nameNow = sphere.name.trim()

    var pendingName by remember(sphere.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(sphere.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(sphere.id, sphere.name) {
        val n = sphere.name.trim()
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

    var pendingColor by remember(sphere.id, sphere.color) {
        mutableStateOf(sphere.color)
    }

    val fallbackWidth = narys?.strokeWidth ?: 3f

    var sliderValue by remember(sphere.id, sphere.strokeWidth, fallbackWidth) {
        mutableStateOf(sphere.strokeWidth ?: fallbackWidth)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
            MongeInspectorSection("Kulová plocha") {}

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
                        ProjectionVisibilityToggleItem("A", sphereProjectionVisible(state, sphere, SphereProjectionKind.AXO)) {
                            setSphereProjectionVisibility(state, sphere, SphereProjectionKind.AXO, it)
                        },
                        ProjectionVisibilityToggleItem("P", sphereProjectionVisible(state, sphere, SphereProjectionKind.PUDORYS)) {
                            setSphereProjectionVisibility(state, sphere, SphereProjectionKind.PUDORYS, it)
                        },
                        ProjectionVisibilityToggleItem("N", sphereProjectionVisible(state, sphere, SphereProjectionKind.NARYS)) {
                            setSphereProjectionVisibility(state, sphere, SphereProjectionKind.NARYS, it)
                        },
                        ProjectionVisibilityToggleItem("B", sphereProjectionVisible(state, sphere, SphereProjectionKind.BOKORYS)) {
                            setSphereProjectionVisibility(state, sphere, SphereProjectionKind.BOKORYS, it)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

                MongeDivider()

            ProjectionVisibilityToggleRow(
                label = "Vybarvit:",
                checked = sphere.fillFaces,
                onCheckedChange = { checked ->
                    val idx = state.spheres3D.indexOfFirst { it.id == sphere.id }
                    if (idx >= 0) {
                        val updatedSphere = state.spheres3D[idx].copy(fillFaces = checked)
                        state.spheres3D[idx] = updatedSphere
                        state.selectedSpheres3D.replaceAll { if (it.id == sphere.id) updatedSphere else it }
                    }
                    state.triggerRedraw++
                    commitSnapshot(state)
                },
                ui = ui
            )
                if (state.projectionMode != ProjectionMode.AXO) {
                    narys?.let {
                        MongeDivider()

                        MongeInspectorPropertyRow("Nárys styl:") {
                            LineStyleSelector(
                                current = it.lineStyle,
                                onStyleChange = onNarysStyleChange
                            )
                        }
                    }

                    pudorys?.let {
                        MongeDivider()

                        MongeInspectorPropertyRow("Půdorys styl:") {
                            LineStyleSelector(
                                current = it.lineStyle,
                                onStyleChange = onPudorysStyleChange
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
                        text = "Smazat",
                        fontSize = ui.sp(13f)
                    )
                }
            }
    }
}

fun deleteSphere3D(state: MongeState, parent: SphereSurface3D) {


    // 1) Získej aktuální instance obou projekcí podle parenta
    val p2d = state.conicsPudorys.filter { it.parentId == parent.id }.toList()
    val n2d = state.conicsNarys.filter { it.parentId == parent.id }.toList()
    val b2d = state.conicsBokorys.filter { it.parentId == parent.id }.toList()
    val a2d = state.conicsAxo.filter { it.parentId == parent.id }.toList()

    // 2) Zruš výběry
    state.selectedConicsPudorys.removeAll(p2d.toSet())
    state.selectedConicsNarys.removeAll(n2d.toSet())
    state.selectedConicsBokorys.removeAll(b2d.toSet())
    state.selectedConicsAxo.removeAll(a2d.toSet())


    // 5) Odstraň projekce a 3D z hlavních seznamů (konkrétní instance)
    state.conicsPudorys.removeAll(p2d.toSet())
    state.conicsNarys.removeAll(n2d.toSet())
    state.conicsBokorys.removeAll(b2d.toSet())
    state.conicsAxo.removeAll(a2d.toSet())
    p2d.forEach { state.conicInputPointsPudorys.remove(it.id) }
    n2d.forEach { state.conicInputPointsNarys.remove(it.id) }
    b2d.forEach { state.conicInputPointsBokorys.remove(it.id) }
    a2d.forEach { state.conicInputPointsAxo.remove(it.id) }
    state.spheres3D.remove(parent)

    state.triggerRedraw++
}
@Composable
fun sphereEdit(state: MongeState){
    val selectedSphere = state.selectedSpheres3D.firstOrNull()
    selectedSphere?.let{sphere ->
        key(sphere.id) {
            EditableSphereInfo(
                sphere = sphere,
                onPudorysStyleChange = { newStyle ->
                    for (i in state.conicsPudorys.indices) {
                        val c = state.conicsPudorys[i]
                        if (c.parentId == sphere.id) {
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
                        if (c.parentId == sphere.id) {
                            val up = c.copy(lineStyle = newStyle)
                            state.conicsNarys[i] = up
                        }
                    }
                    commitSnapshot(state)

                    state.triggerRedraw++
                },

                onApplyName = { newName ->

                    // 3D
                    val idx3D = state.spheres3D.indexOfFirst { it.id == sphere.id }
                    if (idx3D >= 0) {
                        val updated3D = state.spheres3D[idx3D].copy(name = newName)
                        state.spheres3D[idx3D] = updated3D
                        state.selectedSpheres3D.replaceAll { if (it.id == sphere.id) updated3D else it }

                        // Půdorysy
                        for (i in state.conicsPudorys.indices) {
                            val c = state.conicsPudorys[i]
                            if (c.parentId == sphere.id) {
                                val up = c.copy(rawName = newName)
                                state.conicsPudorys[i] = up
                            }
                        }
                        // Nárysy
                        for (i in state.conicsNarys.indices) {
                            val c = state.conicsNarys[i]
                            if (c.parentId == sphere.id) {
                                val up = c.copy(rawName = newName)
                                state.conicsNarys[i] = up
                            }
                        }
                        for (i in state.conicsBokorys.indices) {
                            val c = state.conicsBokorys[i]
                            if (c.parentId == sphere.id) {
                                val up = c.copy(rawName = newName)
                                state.conicsBokorys[i] = up
                            }
                        }
                        for (i in state.conicsAxo.indices) {
                            val c = state.conicsAxo[i]
                            if (c.parentId == sphere.id) {
                                val up = c.copy(rawName = newName)
                                state.conicsAxo[i] = up
                            }
                        }
                    }
                    commitSnapshot(state)

                    state.triggerRedraw++
                },

                onColorChange = { newColor ->

                    // 1) přebarvi sféru (id zůstává stejné)
                    val idx3D = state.spheres3D.indexOfFirst { it.id == sphere.id }
                    if (idx3D < 0) return@EditableSphereInfo
                    val sphereId = state.spheres3D[idx3D].id
                    val updatedSphere = state.spheres3D[idx3D].copy(color = newColor)
                    state.spheres3D[idx3D] = updatedSphere
                    state.selectedSpheres3D.replaceAll { if (it.id == sphereId) updatedSphere else it }

                    // 2) helpery pro přebarvení dětí
                    fun recolorP(c: ConicSectionPudorys) =
                        if (c.parentId == sphereId) c.copy(localColor = newColor) else c
                    fun recolorN(c: ConicSectionNarys) =
                        if (c.parentId == sphereId) c.copy(localColor = newColor) else c
                    fun recolorB(c: ConicSectionBokorys) =
                        if (c.parentId == sphereId) c.copy(localColor = newColor) else c
                    fun recolorA(c: ConicSectionAxo) =
                        if (c.parentId == sphereId) c.copy(localColor = newColor) else c

                    // 3) přebarvi všechny projekce sféry
                    state.conicsPudorys.replaceAll { recolorP(it) }
                    state.conicsNarys.replaceAll  { recolorN(it) }
                    state.conicsBokorys.replaceAll { recolorB(it) }
                    state.conicsAxo.replaceAll { recolorA(it) }

                    // 4) a i aktuálně vybrané, aby se UI hned zaktualizovalo
                    state.selectedConicsPudorys.replaceAll { recolorP(it) }
                    state.selectedConicsNarys.replaceAll { recolorN(it) }
                    state.selectedConicsBokorys.replaceAll { recolorB(it) }
                    state.selectedConicsAxo.replaceAll { recolorA(it) }
                    commitSnapshot(state)

                    state.triggerRedraw++
                },

                onWidthChange = { newWidth ->
                    // 3D
                    state.spheres3D.indexOfFirst { it.id == sphere.id }
                        .takeIf { it >= 0 }
                        ?.let { idx ->
                            val updatedSphere = state.spheres3D[idx].copy(strokeWidth = newWidth)
                            state.spheres3D[idx] = updatedSphere
                            state.selectedSpheres3D.replaceAll { if (it.id == sphere.id) updatedSphere else it }
                        }

                    // Půdorysy (jen ty, co patří k tomuto 3D)
                    for (i in state.conicsPudorys.indices) {
                        val c = state.conicsPudorys[i]
                        if (c.parentId == sphere.id) {
                            state.conicsPudorys[i] = c.copy(strokeWidth = newWidth)
                        }
                    }

                    // Nárysy
                    for (i in state.conicsNarys.indices) {
                        val c = state.conicsNarys[i]
                        if (c.parentId == sphere.id) {
                            state.conicsNarys[i] = c.copy(strokeWidth = newWidth)
                        }
                    }
                    for (i in state.conicsBokorys.indices) {
                        val c = state.conicsBokorys[i]
                        if (c.parentId == sphere.id) {
                            state.conicsBokorys[i] = c.copy(strokeWidth = newWidth)
                        }
                    }
                    for (i in state.conicsAxo.indices) {
                        val c = state.conicsAxo[i]
                        if (c.parentId == sphere.id) {
                            state.conicsAxo[i] = c.copy(strokeWidth = newWidth)
                        }
                    }

                    state.triggerRedraw++
                },
                onDelete = {
                    deleteSphere3D(state, sphere)
                    clearSelection(state)
                    commitSnapshot(state)

                },
                state = state

            )
        }

    }
}
