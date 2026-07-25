package monge.input.selection

import draw.mongescreen.labels.clearSelection
import model.classes.Segment2DPudorys
import model.classes.Segment2DNarys
import model.classes.Segment2DBokorys
import model.classes.Segment2DAxo
import state.MongeState

fun togglePolygonSelection(
    state: MongeState,
    polygonId: String,
    clearOthers: Boolean = false
) {
    val poly = state.polygons3D.firstOrNull { it.id == polygonId } ?: return

    if (clearOthers) {
        clearSelection(state)
    }

    // je polygon již vybrán?
    val isSelected = state.selectedPolygons.any { it.id == poly.id } ||
            (state.selectedPolygon?.id == poly.id)

    val segIds = poly.segmentIds3D.toSet()

    if (isSelected) {
        state.selectedSegmentsPudorys.removeAll { selected ->
            (selected as? Segment2DPudorys)?.let { (it.parent?.id ?: it.parentId) in segIds } == true
        }
        state.selectedSegmentsNarys.removeAll { selected ->
            (selected as? Segment2DNarys)?.let { (it.parent?.id ?: it.parentId) in segIds } == true
        }
        state.selectedSegmentsBokorys.removeAll { selected ->
            (selected as? Segment2DBokorys)?.let { (it.parent?.id ?: it.parentId) in segIds } == true
        }
        state.selectedSegmentsAxo.removeAll { selected ->
            (selected.parent?.id ?: selected.parentId) in segIds
        }
    } else {
        state.segmentsPudorys
            .filter { (it.parent?.id ?: it.parentId) in segIds || it.id in poly.segmentIdsPudorys }
            .forEach { seg -> if (state.selectedSegmentsPudorys.none { (it as? Segment2DPudorys)?.id == seg.id }) state.selectedSegmentsPudorys.add(seg) }
        state.segmentsNarys
            .filter { (it.parent?.id ?: it.parentId) in segIds || it.id in poly.segmentIdsNarys }
            .forEach { seg -> if (state.selectedSegmentsNarys.none { (it as? Segment2DNarys)?.id == seg.id }) state.selectedSegmentsNarys.add(seg) }
        state.segmentsBokorys
            .filter { (it.parent?.id ?: it.parentId) in segIds }
            .forEach { seg -> if (state.selectedSegmentsBokorys.none { (it as? Segment2DBokorys)?.id == seg.id }) state.selectedSegmentsBokorys.add(seg) }
        state.segmentsAxo
            .filter { (it.parent?.id ?: it.parentId) in segIds || it.id in poly.segmentIdsAxo }
            .forEach { seg -> if (state.selectedSegmentsAxo.none { it.id == seg.id }) state.selectedSegmentsAxo.add(seg) }
    }

    // Přepni i samotný polygon v selekci
    if (isSelected) {
        state.selectedPolygons.removeAll { it.id == poly.id }
        if (state.selectedPolygon?.id == poly.id) state.selectedPolygon = null
    } else {
        state.selectedPolygons.add(poly)
        state.selectedPolygon = poly
        advancePendingPolygonConstruction(state)
    }

    state.triggerRedraw++
}

fun togglePlanePolygonSelection(
    state: MongeState,
    polygonId: String,
    clearOthers: Boolean = false
) {
    val polygon = state.planePolygons2D.firstOrNull { it.id == polygonId } ?: return
    if (clearOthers) clearSelection(state)

    val isSelected = state.selectedPlanePolygons2D.any { it.id == polygon.id }
    if (isSelected) {
        state.selectedPlanePolygons2D.removeAll { it.id == polygon.id }
    } else {
        state.selectedPlanePolygons2D.add(polygon)
    }
    state.triggerRedraw++
}
