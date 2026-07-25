package monge.input.selection

import draw.mongescreen.labels.clearSelection
import state.MongeState

fun toggleSegmentSolidSelection(
    state: MongeState,
    solidId: String,
    clearOthers: Boolean = false
) {
    val solid = state.segmentSolids3D.firstOrNull { it.id == solidId } ?: return
    if (clearOthers) clearSelection(state)

    val isSelected = state.selectedSegmentSolids3D.any { it.id == solid.id }
    val segmentIds = solid.segmentIds3D.toSet()

    if (isSelected) {
        state.selectedSegmentSolids3D.removeAll { it.id == solid.id }
        state.selectedSegments3D.removeAll { it.id in segmentIds }
        state.selectedSegmentsPudorys.removeAll { it.parent?.id in segmentIds }
        state.selectedSegmentsNarys.removeAll { it.parent?.id in segmentIds }
        state.selectedSegmentsBokorys.removeAll { it.parent?.id in segmentIds }
        state.selectedSegmentsAxo.removeAll { it.parent?.id in segmentIds || it.parentId in segmentIds }
    } else {
        state.selectedSegmentSolids3D.add(solid)
        state.segments3D.filter { it.id in segmentIds }.forEach { seg ->
            if (state.selectedSegments3D.none { it.id == seg.id }) state.selectedSegments3D.add(seg)
        }
        state.segmentsPudorys.filter { it.parent?.id in segmentIds || it.parentId in segmentIds }.forEach { seg ->
            if (state.selectedSegmentsPudorys.none { it.id == seg.id }) state.selectedSegmentsPudorys.add(seg)
        }
        state.segmentsNarys.filter { it.parent?.id in segmentIds || it.parentId in segmentIds }.forEach { seg ->
            if (state.selectedSegmentsNarys.none { it.id == seg.id }) state.selectedSegmentsNarys.add(seg)
        }
        state.segmentsBokorys.filter { it.parent?.id in segmentIds || it.parentId in segmentIds }.forEach { seg ->
            if (state.selectedSegmentsBokorys.none { it.id == seg.id }) state.selectedSegmentsBokorys.add(seg)
        }
        state.segmentsAxo.filter { it.parent?.id in segmentIds || it.parentId in segmentIds }.forEach { seg ->
            if (state.selectedSegmentsAxo.none { it.id == seg.id }) state.selectedSegmentsAxo.add(seg)
        }
    }

    state.triggerRedraw++
}
