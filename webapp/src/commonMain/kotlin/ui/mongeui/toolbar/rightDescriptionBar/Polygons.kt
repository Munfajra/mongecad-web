package ui.mongeui.toolbar.rightDescriptionBar

import utils.replaceAll
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.Point3D
import model.ProjectionMode
import model.classes.Segment2DAxo
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.SkikoButton

enum class PolygonProjectionKind { AXO, PUDORYS, NARYS, BOKORYS }

@Composable
fun EditablePolygonInfo(
    polyId: String,
    state: MongeState,
    onApplyName: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onDelete: () -> Unit,
    onWidthChange: (Float) -> Unit,
    uiScale: Float
) {
    val poly = state.polygons3D.find { it.id == polyId } ?: return

    val ui = remember(uiScale) { UiScale(uiScale) }

    var pendingColor by remember(poly.id, poly.color) {
        mutableStateOf(poly.color)
    }

    var sliderValue by remember(poly.id, poly.width) {
        mutableStateOf(poly.width)
    }

    val nameNow = poly.name.trim()

    var pendingName by remember(poly.id) {
        mutableStateOf(TextFieldValue(nameNow))
    }

    var lastAppliedName by remember(poly.id) {
        mutableStateOf(nameNow)
    }

    LaunchedEffect(poly.id, poly.name) {
        val n = poly.name.trim()
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
                        ProjectionVisibilityToggleItem("A", polygonProjectionVisible(state, poly, PolygonProjectionKind.AXO)) {
                            setPolygonProjectionVisible(state, poly.id, PolygonProjectionKind.AXO, it)
                        },
                        ProjectionVisibilityToggleItem("P", polygonProjectionVisible(state, poly, PolygonProjectionKind.PUDORYS)) {
                            setPolygonProjectionVisible(state, poly.id, PolygonProjectionKind.PUDORYS, it)
                        },
                        ProjectionVisibilityToggleItem("N", polygonProjectionVisible(state, poly, PolygonProjectionKind.NARYS)) {
                            setPolygonProjectionVisible(state, poly.id, PolygonProjectionKind.NARYS, it)
                        },
                        ProjectionVisibilityToggleItem("B", polygonProjectionVisible(state, poly, PolygonProjectionKind.BOKORYS)) {
                            setPolygonProjectionVisible(state, poly.id, PolygonProjectionKind.BOKORYS, it)
                        },
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
                    text = "Smazat",
                    fontSize = ui.sp(13f)
                )
            }
        }
    }
}
@Composable
fun polygonEdit(state: MongeState) {
    val selectedPolygon = state.selectedPolygons.firstOrNull()

    selectedPolygon?.let { poly ->
        key(poly.id) {
            EditablePolygonInfo(
                polyId = poly.id,
                state = state,
                onApplyName = { newName ->
                    renamePolygon(state, poly.id, newName)
                },

                onColorChange = { newColor ->
                    recolorPolygon(state, polyId = poly.id, newColor = newColor)
                },

                onWidthChange = { newWidth ->
                    restylePolygonWidth(state, polyId = poly.id, newWidth = newWidth, commit = false)
                },

                onDelete = {
                    deletePolygon(state, polyId = poly.id)
                    clearSelection(state)
                },
                uiScale = SettingsManager.current.UIscale / 75f
            )
        }
    }
}

fun renamePolygon(state: MongeState, polyId: String, newName: String) {
    val cur = state.polygons3D.find { it.id == polyId } ?: return
    val cleaned = newName.trim()
    if (cur.name == cleaned) return

    val updated = cur.copy(name = cleaned)
    state.polygons3D.replaceAll { if (it.id == updated.id) updated else it }

    if (state.selectedPolygon?.id == updated.id) state.selectedPolygon = updated
    state.selectedPolygons.replaceAll { if (it.id == updated.id) updated else it }

    state.triggerRedraw++
    commitSnapshot(state)
}

fun recolorPolygon(state: MongeState, polyId: String, newColor: Color) {
    val poly = state.polygons3D.find { it.id == polyId } ?: return
    if (poly.color == newColor) return

    // ✅ 0) aktualizuj polygon jako objekt (kvůli UI preview)
    val updatedPoly = poly.copy(color = newColor)
    state.polygons3D.replaceAll { if (it.id == polyId) updatedPoly else it }

    // pokud držíš i selection kopie, srovnej je taky:
    if (state.selectedPolygon?.id == polyId) state.selectedPolygon = updatedPoly
    state.selectedPolygons.replaceAll { if (it.id == polyId) updatedPoly else it }

    // 1) VRCHOLY...
    val updatedPointById = mutableMapOf<String, Point3D>()
    state.sharedPoints3D.replaceAll { p ->
        if (p.id in poly.vertexPointIds) {
            val up = p.copy(color = newColor)
            updatedPointById[up.id] = up
            up
        } else p
    }

    // 1a) přepoj 2D průměty bodů...
    state.selectedPoints3D.replaceAll { updatedPointById[it.id] ?: it }
    state.pointsPudorys.forEach { q ->
        updatedPointById[q.parent?.id]?.let { parent ->
            q.parent = parent
            q.localColor = newColor
        }
    }
    state.pointsNarys.forEach { q ->
        updatedPointById[q.parent?.id]?.let { parent ->
            q.parent = parent
            q.localColor = newColor
        }
    }
    state.pointsBokorys.forEach { q ->
        updatedPointById[q.parent?.id]?.let { parent ->
            q.parent = parent
            q.localColor = newColor
        }
    }
    state.pointsAxo.forEach { q ->
        updatedPointById[q.parent?.id]?.let { parent ->
            q.parent = parent
            q.localColor = newColor
        }
    }

    // 2) 3D ÚSEČKY...
    val updatedSegById = mutableMapOf<String, Segment3D>()
    state.segments3D.replaceAll { s ->
        if (s.id in poly.segmentIds3D) {
            val aUp = updatedPointById[s.start.id] ?: s.start
            val bUp = updatedPointById[s.end.id] ?: s.end
            val up = s.copy(start = aUp, end = bUp, color = newColor)
            updatedSegById[up.id] = up
            up
        } else s
    }

    // 2a) 2D průměty úseček...
    state.selectedSegments3D.replaceAll { updatedSegById[it.id] ?: it }
    state.segmentsPudorys.replaceAll { sp ->
        val pid = sp.parent?.id ?: sp.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { sp.copy(parent = it, localColor = newColor).withEndpointParents() }
                ?: sp.copy(localColor = newColor).withEndpointParents()
        } else sp
    }
    state.segmentsNarys.replaceAll { sn ->
        val pid = sn.parent?.id ?: sn.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { sn.copy(parent = it, localColor = newColor).withEndpointParents() }
                ?: sn.copy(localColor = newColor).withEndpointParents()
        } else sn
    }
    state.segmentsBokorys.replaceAll { sb ->
        val pid = sb.parent?.id ?: sb.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { sb.copy(parent = it, localColor = newColor).withEndpointParents() }
                ?: sb.copy(localColor = newColor).withEndpointParents()
        } else sb
    }
    state.segmentsAxo.replaceAll { sa ->
        val pid = sa.parent?.id ?: sa.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { sa.copy(parent = it, localColor = newColor).withEndpointParents() }
                ?: sa.copy(localColor = newColor).withEndpointParents()
        } else sa
    }
    state.selectedSegmentsPudorys.replaceAll { selected ->
        (selected as? Segment2DPudorys)?.let { seg ->
            val pid = seg.parent?.id ?: seg.parentId
            if (pid != null && pid in poly.segmentIds3D) {
                updatedSegById[pid]?.let { seg.copy(parent = it, localColor = newColor).withEndpointParents() }
                    ?: seg.copy(localColor = newColor).withEndpointParents()
            } else seg
        } ?: selected
    }
    state.selectedSegmentsNarys.replaceAll { selected ->
        (selected as? Segment2DNarys)?.let { seg ->
            val pid = seg.parent?.id ?: seg.parentId
            if (pid != null && pid in poly.segmentIds3D) {
                updatedSegById[pid]?.let { seg.copy(parent = it, localColor = newColor).withEndpointParents() }
                    ?: seg.copy(localColor = newColor).withEndpointParents()
            } else seg
        } ?: selected
    }
    state.selectedSegmentsBokorys.replaceAll { selected ->
        (selected as? Segment2DBokorys)?.let { seg ->
            val pid = seg.parent?.id ?: seg.parentId
            if (pid != null && pid in poly.segmentIds3D) {
                updatedSegById[pid]?.let { seg.copy(parent = it, localColor = newColor).withEndpointParents() }
                    ?: seg.copy(localColor = newColor).withEndpointParents()
            } else seg
        } ?: selected
    }
    state.selectedSegmentsAxo.replaceAll { seg ->
        val pid = seg.parent?.id ?: seg.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { seg.copy(parent = it, localColor = newColor).withEndpointParents() }
                ?: seg.copy(localColor = newColor).withEndpointParents()
        } else seg
    }
    commitSnapshot(state)

    state.triggerRedraw++
}

fun restylePolygonWidth(state: MongeState, polyId: String, newWidth: Float, commit: Boolean = true) {
    val poly = state.polygons3D.find { it.id == polyId } ?: return

    val updatedSegById = mutableMapOf<String, Segment3D>()
    state.segments3D.replaceAll { s ->
        if (s.id in poly.segmentIds3D) {
            val up = s.copy(strokeWidth = newWidth)
            updatedSegById[up.id] = up
            up
        } else s
    }

    // Přepoj 2D průměty na updated parent – díky tomu se tloušťka props dědí
    state.selectedSegments3D.replaceAll { updatedSegById[it.id] ?: it }
    state.segmentsPudorys.replaceAll { sp ->
        val pid = sp.parent?.id ?: sp.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { sp.copy(parent = it, localStrokeWidth = newWidth).withEndpointParents() }
                ?: sp.copy(localStrokeWidth = newWidth).withEndpointParents()
        } else sp
    }
    state.segmentsNarys.replaceAll { sn ->
        val pid = sn.parent?.id ?: sn.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { sn.copy(parent = it, localStrokeWidth = newWidth).withEndpointParents() }
                ?: sn.copy(localStrokeWidth = newWidth).withEndpointParents()
        } else sn
    }
    state.segmentsBokorys.replaceAll { sb ->
        val pid = sb.parent?.id ?: sb.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { sb.copy(parent = it, localStrokeWidth = newWidth).withEndpointParents() }
                ?: sb.copy(localStrokeWidth = newWidth).withEndpointParents()
        } else sb
    }
    state.segmentsAxo.replaceAll { sa ->
        val pid = sa.parent?.id ?: sa.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { sa.copy(parent = it, localStrokeWidth = newWidth).withEndpointParents() }
                ?: sa.copy(localStrokeWidth = newWidth).withEndpointParents()
        } else sa
    }
    state.selectedSegmentsPudorys.replaceAll { selected ->
        (selected as? Segment2DPudorys)?.let { seg ->
            val pid = seg.parent?.id ?: seg.parentId
            if (pid != null && pid in poly.segmentIds3D) {
                updatedSegById[pid]?.let { seg.copy(parent = it, localStrokeWidth = newWidth).withEndpointParents() }
                    ?: seg.copy(localStrokeWidth = newWidth).withEndpointParents()
            } else seg
        } ?: selected
    }
    state.selectedSegmentsNarys.replaceAll { selected ->
        (selected as? Segment2DNarys)?.let { seg ->
            val pid = seg.parent?.id ?: seg.parentId
            if (pid != null && pid in poly.segmentIds3D) {
                updatedSegById[pid]?.let { seg.copy(parent = it, localStrokeWidth = newWidth).withEndpointParents() }
                    ?: seg.copy(localStrokeWidth = newWidth).withEndpointParents()
            } else seg
        } ?: selected
    }
    state.selectedSegmentsBokorys.replaceAll { selected ->
        (selected as? Segment2DBokorys)?.let { seg ->
            val pid = seg.parent?.id ?: seg.parentId
            if (pid != null && pid in poly.segmentIds3D) {
                updatedSegById[pid]?.let { seg.copy(parent = it, localStrokeWidth = newWidth).withEndpointParents() }
                    ?: seg.copy(localStrokeWidth = newWidth).withEndpointParents()
            } else seg
        } ?: selected
    }
    state.selectedSegmentsAxo.replaceAll { seg ->
        val pid = seg.parent?.id ?: seg.parentId
        if (pid != null && pid in poly.segmentIds3D) {
            updatedSegById[pid]?.let { seg.copy(parent = it, localStrokeWidth = newWidth).withEndpointParents() }
                ?: seg.copy(localStrokeWidth = newWidth).withEndpointParents()
        } else seg
    }
    if (commit) commitSnapshot(state)

    state.triggerRedraw++
}

fun setPolygonProjectionVisible(
    state: MongeState,
    polyId: String,
    kind: PolygonProjectionKind,
    checked: Boolean
) {
    val poly = state.polygons3D.firstOrNull { it.id == polyId } ?: return
    when (kind) {
        PolygonProjectionKind.PUDORYS -> polygonPudorysSegments(state, poly).forEach { it.setVisibleWithEndpoints(checked) }
        PolygonProjectionKind.NARYS -> polygonNarysSegments(state, poly).forEach { it.setVisibleWithEndpoints(checked) }
        PolygonProjectionKind.BOKORYS -> polygonBokorysSegments(state, poly).forEach { it.setVisibleWithEndpoints(checked) }
        PolygonProjectionKind.AXO -> polygonAxoSegments(state, poly).forEach { it.setVisibleWithEndpoints(checked) }
    }
    state.triggerRedraw++
    commitSnapshot(state)
}

private fun polygonProjectionVisible(
    state: MongeState,
    poly: model.classes.RegularPolygon3D,
    kind: PolygonProjectionKind
): Boolean {
    val values = when (kind) {
        PolygonProjectionKind.PUDORYS -> polygonPudorysSegments(state, poly).map { it.showInAxo }
        PolygonProjectionKind.NARYS -> polygonNarysSegments(state, poly).map { it.showInAxo }
        PolygonProjectionKind.BOKORYS -> polygonBokorysSegments(state, poly).map { it.showInAxo }
        PolygonProjectionKind.AXO -> polygonAxoSegments(state, poly).map { it.showInAxo }
    }
    return values.isNotEmpty() && values.all { it }
}

private fun polygonPudorysSegments(state: MongeState, poly: model.classes.RegularPolygon3D): List<Segment2DPudorys> {
    val ids = poly.segmentIds3D.toSet()
    val projectionIds = poly.segmentIdsPudorys.toSet()
    return state.segmentsPudorys.filter { it.id in projectionIds || it.parent?.id in ids || it.parentId in ids }
}

private fun polygonNarysSegments(state: MongeState, poly: model.classes.RegularPolygon3D): List<Segment2DNarys> {
    val ids = poly.segmentIds3D.toSet()
    val projectionIds = poly.segmentIdsNarys.toSet()
    return state.segmentsNarys.filter { it.id in projectionIds || it.parent?.id in ids || it.parentId in ids }
}

private fun polygonBokorysSegments(state: MongeState, poly: model.classes.RegularPolygon3D): List<Segment2DBokorys> {
    val ids = poly.segmentIds3D.toSet()
    return state.segmentsBokorys.filter { it.parent?.id in ids || it.parentId in ids }
}

private fun polygonAxoSegments(state: MongeState, poly: model.classes.RegularPolygon3D): List<Segment2DAxo> {
    val ids = poly.segmentIds3D.toSet()
    val projectionIds = poly.segmentIdsAxo.toSet()
    return state.segmentsAxo.filter { it.id in projectionIds || it.parent?.id in ids || it.parentId in ids }
}

private fun Segment2DPudorys.setVisibleWithEndpoints(visible: Boolean) {
    showInAxo = visible
    showInAxoInitial = visible
    start.showInAxo = visible
    start.showInAxoInitial = visible
    end.showInAxo = visible
    end.showInAxoInitial = visible
}

private fun Segment2DNarys.setVisibleWithEndpoints(visible: Boolean) {
    showInAxo = visible
    showInAxoInitial = visible
    start.showInAxo = visible
    start.showInAxoInitial = visible
    end.showInAxo = visible
    end.showInAxoInitial = visible
}

private fun Segment2DBokorys.setVisibleWithEndpoints(visible: Boolean) {
    showInAxo = visible
    showInAxoInitial = visible
    start.showInAxo = visible
    start.showInAxoInitial = visible
    end.showInAxo = visible
    end.showInAxoInitial = visible
}

private fun Segment2DAxo.setVisibleWithEndpoints(visible: Boolean) {
    showInAxo = visible
    showInAxoInitial = visible
    start.showInAxo = visible
    start.showInAxoInitial = visible
    end.showInAxo = visible
    end.showInAxoInitial = visible
}

private fun Segment2DPudorys.withEndpointParents(): Segment2DPudorys = also {
    start.parentSegment = it
    end.parentSegment = it
}

private fun Segment2DNarys.withEndpointParents(): Segment2DNarys = also {
    start.parentSegment = it
    end.parentSegment = it
}

private fun Segment2DBokorys.withEndpointParents(): Segment2DBokorys = also {
    start.parentSegment = it
    end.parentSegment = it
}

private fun Segment2DAxo.withEndpointParents(): Segment2DAxo = also {
    start.parentSegment = it
    end.parentSegment = it
}

fun deletePolygon(state: MongeState, polyId: String) {
    val poly = state.polygons3D.find { it.id == polyId } ?: return


    // Připrav si ID do setů kvůli rychlému membership testu
    val segPIds = poly.segmentIdsPudorys.toHashSet()
    val segNIds = poly.segmentIdsNarys.toHashSet()
    val segAIds = poly.segmentIdsAxo.toHashSet()
    val ptPIds  = poly.vertexPointIdsPudorys.toHashSet()
    val ptNIds  = poly.vertexPointIdsNarys.toHashSet()
    val seg3Ids = poly.segmentIds3D.toHashSet()
    val pt3Ids  = poly.vertexPointIds.toHashSet()

    Snapshot.withMutableSnapshot {
        // 1) zruš výběry, ať nezůstanou dangling references
        state.selectedPolygons.removeAll { it.id == polyId }
        if (state.selectedPolygon?.id == polyId) state.selectedPolygon = null

        unselectPolygonSegmentsP(state, segPIds, seg3Ids)
        unselectPolygonSegmentsN(state, segNIds, seg3Ids)
        state.selectedSegmentsBokorys.removeAll { (it as? Segment2DBokorys)?.let { s -> (s.parent?.id ?: s.parentId) in seg3Ids } == true }
        state.selectedSegmentsAxo.removeAll { (it.parent?.id ?: it.parentId) in seg3Ids || it.id in segAIds }
        state.selectedSegments3D.removeAll { it.id in seg3Ids }
        state.selectedPointsPudorys.removeAll  { it.id in ptPIds }
        state.selectedPointsNarys.removeAll    { it.id in ptNIds }
        state.selectedPointsBokorys.removeAll  { it.parent?.id in pt3Ids || it.parentSegment?.let { s -> (s.parent?.id ?: s.parentId) in seg3Ids } == true }
        state.selectedPointsAxo.removeAll      { it.parent?.id in pt3Ids || it.parentSegment?.let { s -> (s.parent?.id ?: s.parentId) in seg3Ids } == true }

        // 2) odstraň 2D projekce
        state.segmentsPudorys.removeAll { it.id in segPIds }
        state.segmentsNarys.removeAll   { it.id in segNIds }
        state.segmentsBokorys.removeAll { (it.parent?.id ?: it.parentId) in seg3Ids }
        state.segmentsAxo.removeAll     { (it.parent?.id ?: it.parentId) in seg3Ids || it.id in segAIds }
        state.pointsPudorys.removeAll   { it.id in ptPIds }
        state.pointsNarys.removeAll     { it.id in ptNIds }
        state.pointsBokorys.removeAll   { it.parent?.id in pt3Ids || it.parentSegment?.let { s -> (s.parent?.id ?: s.parentId) in seg3Ids } == true }
        state.pointsAxo.removeAll       { it.parent?.id in pt3Ids || it.parentSegment?.let { s -> (s.parent?.id ?: s.parentId) in seg3Ids } == true }

        // 3) odstraň 3D objekty

        state.segments3D.removeAll      { it.id in seg3Ids }
        state.sharedPoints3D.removeAll  { it.id in pt3Ids }

        // 4) nakonec samotný parent polygon
        state.polygons3D.removeAll      { it.id == polyId }
    }
    commitSnapshot(state)

    state.triggerRedraw++
}
fun unselectPolygonSegmentsP(state: MongeState, segPIds: Set<String>, seg3Ids: Set<String>) {
    state.selectedSegmentsPudorys.removeAll { (it as? Segment2DPudorys)?.let { s ->
        s.id in segPIds || (s.parent?.id in seg3Ids)
    } == true }
}

fun unselectPolygonSegmentsN(state: MongeState, segNIds: Set<String>, seg3Ids: Set<String>) {
    state.selectedSegmentsNarys.removeAll { (it as? Segment2DNarys)?.let { s ->
        s.id in segNIds || (s.parent?.id in seg3Ids)
    } == true }
}
