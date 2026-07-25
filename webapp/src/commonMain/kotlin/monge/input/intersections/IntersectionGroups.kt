package monge.input.intersections

import draw.mongescreen.labels.clearSelection
import model.classes.IntersectionGroup
import model.classes.IntersectionPartKind
import monge.input.selection.toggleSelectionLine3D
import monge.input.selection.toggleSelectionPoint3D
import state.MongeState

/**
 * Id objektů, které vznikly jako výsledek průniku a v ObjectListu se proto
 * schovávají pod svou skupinu.
 */
data class IntersectionGroupedIds(
    val point3DIds: Set<String> = emptySet(),
    val line3DIds: Set<String> = emptySet(),
    val segment3DIds: Set<String> = emptySet(),
    val conic3DIds: Set<String> = emptySet(),
    val curve3DIds: Set<String> = emptySet(),
)

fun intersectionGroupedIds(state: MongeState): IntersectionGroupedIds {
    val pointIds = mutableSetOf<String>()
    val lineIds = mutableSetOf<String>()
    val segmentIds = mutableSetOf<String>()
    val conicIds = mutableSetOf<String>()
    val curveIds = mutableSetOf<String>()

    state.intersectionGroups.flatMap { it.parts }.forEach { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> pointIds += part.id
            IntersectionPartKind.LINE3D -> lineIds += part.id
            IntersectionPartKind.SEGMENT3D -> segmentIds += part.id
            IntersectionPartKind.CONIC3D -> conicIds += part.id
            IntersectionPartKind.CURVE3D -> curveIds += part.id
        }
    }
    return IntersectionGroupedIds(pointIds, lineIds, segmentIds, conicIds, curveIds)
}

fun selectIntersectionGroup(
    state: MongeState,
    group: IntersectionGroup,
    clearAllOnClick: Boolean,
) {
    if (clearAllOnClick) clearSelection(state)
    state.selectedIntersectionGroupId = group.id
    group.parts.forEach { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D ->
                state.sharedPoints3D.firstOrNull { it.id == part.id }
                    ?.let { toggleSelectionPoint3D(it, state) }
            IntersectionPartKind.LINE3D ->
                state.lines3D.firstOrNull { it.id == part.id }
                    ?.let { toggleSelectionLine3D(it, state) }
            else -> Unit
        }
    }
}
