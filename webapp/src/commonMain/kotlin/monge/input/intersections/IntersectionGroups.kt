package monge.input.intersections
import utils.replaceAll

import androidx.compose.ui.graphics.Color
import draw.mongescreen.labels.clearSelection
import model.LineStyle
import model.classes.*
import monge.input.selection.toggleSelectionLine3D
import monge.input.selection.toggleSelectionPoint3D
import monge.input.selection.toggleSelectionSegment3D
import state.MongeState

data class IntersectionGroupedIds(
    val point3DIds: Set<String>,
    val line3DIds: Set<String>,
    val segment3DIds: Set<String>,
    val conic3DIds: Set<String>,
    val curve3DIds: Set<String>,
)

fun intersectionGroupedIds(state: MongeState): IntersectionGroupedIds {
    val points = mutableSetOf<String>()
    val lines = mutableSetOf<String>()
    val segments = mutableSetOf<String>()
    val conics = mutableSetOf<String>()
    val curves = mutableSetOf<String>()
    state.intersectionGroups.flatMap { it.parts }.forEach { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> points += part.id
            IntersectionPartKind.LINE3D -> lines += part.id
            IntersectionPartKind.SEGMENT3D -> segments += part.id
            IntersectionPartKind.CONIC3D -> conics += part.id
            IntersectionPartKind.CURVE3D -> curves += part.id
        }
    }
    return IntersectionGroupedIds(points, lines, segments, conics, curves)
}

fun selectedIntersectionGroup(state: MongeState): IntersectionGroup? =
    state.selectedIntersectionGroupId?.let { id -> state.intersectionGroups.firstOrNull { it.id == id } }

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
                state.sharedPoints3D.firstOrNull { it.id == part.id }?.let { toggleSelectionPoint3D(it, state) }
            IntersectionPartKind.LINE3D ->
                state.lines3D.firstOrNull { it.id == part.id }?.let { toggleSelectionLine3D(it, state) }
            IntersectionPartKind.SEGMENT3D ->
                state.segments3D.firstOrNull { it.id == part.id }?.let { toggleSelectionSegment3D(it, state) }
            IntersectionPartKind.CONIC3D ->
                selectIntersectionConic3D(state, part.id)
            IntersectionPartKind.CURVE3D ->
                state.selectedCurve3DId = part.id.takeIf { id -> state.curves3D.any { it.id == id } }
        }
    }
}

private fun selectIntersectionConic3D(state: MongeState, conicId: String) {
    state.conicsPudorys.filter { it.parent?.id == conicId || it.parentId == conicId }
        .forEach { if (state.selectedConicsPudorys.none { s -> s.id == it.id }) state.selectedConicsPudorys += it }
    state.conicsNarys.filter { it.parent?.id == conicId || it.parentId == conicId }
        .forEach { if (state.selectedConicsNarys.none { s -> s.id == it.id }) state.selectedConicsNarys += it }
    state.conicsBokorys.filter { it.parent?.id == conicId || it.parentId == conicId }
        .forEach { if (state.selectedConicsBokorys.none { s -> s.id == it.id }) state.selectedConicsBokorys += it }
    state.conicsAxo.filter { it.parent?.id == conicId || it.parentId == conicId }
        .forEach { if (state.selectedConicsAxo.none { s -> s.id == it.id }) state.selectedConicsAxo += it }
}

fun intersectionGroupColor(state: MongeState, group: IntersectionGroup): Color =
    group.parts.firstNotNullOfOrNull { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> state.sharedPoints3D.firstOrNull { it.id == part.id }?.color
            IntersectionPartKind.LINE3D -> state.lines3D.firstOrNull { it.id == part.id }?.color
            IntersectionPartKind.SEGMENT3D -> state.segments3D.firstOrNull { it.id == part.id }?.color
            IntersectionPartKind.CONIC3D -> state.conics3D.firstOrNull { it.id == part.id }?.color
            IntersectionPartKind.CURVE3D -> state.curves3D.firstOrNull { it.id == part.id }?.color
        }
    } ?: Color.Black

fun intersectionGroupStrokeWidth(state: MongeState, group: IntersectionGroup): Float =
    group.parts.firstNotNullOfOrNull { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> state.sharedPoints3D.firstOrNull { it.id == part.id }?.width
            IntersectionPartKind.LINE3D -> state.lines3D.firstOrNull { it.id == part.id }?.strokeWidth
            IntersectionPartKind.SEGMENT3D -> state.segments3D.firstOrNull { it.id == part.id }?.strokeWidth
            IntersectionPartKind.CONIC3D -> state.conics3D.firstOrNull { it.id == part.id }?.strokeWidth
            IntersectionPartKind.CURVE3D -> state.curves3D.firstOrNull { it.id == part.id }?.strokeWidth
        }
    } ?: INTERSECTION_RESULT_STROKE_WIDTH

fun intersectionGroupLineStyle(state: MongeState, group: IntersectionGroup): LineStyle =
    group.parts.firstNotNullOfOrNull { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> null
            IntersectionPartKind.LINE3D -> state.lines3D.firstOrNull { it.id == part.id }?.lineStyle
            IntersectionPartKind.SEGMENT3D -> state.segments3D.firstOrNull { it.id == part.id }?.lineStyle
            IntersectionPartKind.CONIC3D -> state.conics3D.firstOrNull { it.id == part.id }?.lineStyle
            IntersectionPartKind.CURVE3D -> state.curves3D.firstOrNull { it.id == part.id }?.lineStyle
        }
    } ?: LineStyle.Solid

fun applyIntersectionGroupColor(state: MongeState, group: IntersectionGroup, color: Color) {
    group.parts.forEach { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> state.sharedPoints3D.firstOrNull { it.id == part.id }?.color = color
            IntersectionPartKind.LINE3D -> state.lines3D.firstOrNull { it.id == part.id }?.color = color
            IntersectionPartKind.SEGMENT3D -> {
                state.segments3D.firstOrNull { it.id == part.id }?.let {
                    it.color = color
                    it.start.color = color
                    it.end.color = color
                }
            }
            IntersectionPartKind.CONIC3D -> state.conics3D.firstOrNull { it.id == part.id }?.color = color
            IntersectionPartKind.CURVE3D -> state.curves3D.firstOrNull { it.id == part.id }?.color = color
        }
    }
    state.triggerRedraw++
}

fun applyIntersectionGroupStrokeWidth(state: MongeState, group: IntersectionGroup, width: Float) {
    val w = width.coerceAtLeast(0.5f)
    group.parts.forEach { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> state.sharedPoints3D.firstOrNull { it.id == part.id }?.width = w
            IntersectionPartKind.LINE3D -> state.lines3D.firstOrNull { it.id == part.id }?.strokeWidth = w
            IntersectionPartKind.SEGMENT3D -> state.segments3D.firstOrNull { it.id == part.id }?.strokeWidth = w
            IntersectionPartKind.CONIC3D -> applyConicWidth(state, part.id, w)
            IntersectionPartKind.CURVE3D -> applyCurveWidth(state, part.id, w)
        }
    }
    state.triggerRedraw++
}

fun applyIntersectionGroupLineStyle(state: MongeState, group: IntersectionGroup, style: LineStyle) {
    group.parts.forEach { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> Unit
            IntersectionPartKind.LINE3D -> state.lines3D.firstOrNull { it.id == part.id }?.lineStyle = style
            IntersectionPartKind.SEGMENT3D -> applySegmentStyle(state, part.id, style)
            IntersectionPartKind.CONIC3D -> applyConicStyle(state, part.id, style)
            IntersectionPartKind.CURVE3D -> applyCurveStyle(state, part.id, style)
        }
    }
    state.triggerRedraw++
}

fun applyIntersectionGroupShowInAxo(
    state: MongeState,
    group: IntersectionGroup,
    kind: String,
    visible: Boolean,
) {
    val ids = group.parts.map { it.id }.toSet()
    fun point3DMatch(parentId: String?) = parentId in ids
    fun segmentMatch(parentId: String?) = parentId in ids
    fun lineMatch(parentId: String?) = parentId in ids
    fun conicMatch(parentId: String?) = parentId in ids
    fun curveMatch(parentId: String?) = parentId in ids
    when (kind) {
        "A" -> {
            state.pointsAxo.filter { point3DMatch(it.parent?.id) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.lines3DAxo.filter { lineMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.segmentsAxo.filter { segmentMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.conicsAxo.filter { conicMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.curvesAxo.filter { curveMatch(it.parentId) }.forEach { it.showInAxo = visible }
        }
        "P" -> {
            state.pointsPudorys.filter { point3DMatch(it.parent?.id) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.lines3DPudorys.filter { lineMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.segmentsPudorys.filter { segmentMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.conicsPudorys.filter { conicMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.curvesPudorys.filter { curveMatch(it.parentId) }.forEach { it.showInAxo = visible }
        }
        "N" -> {
            state.pointsNarys.filter { point3DMatch(it.parent?.id) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.lines3DNarys.filter { lineMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.segmentsNarys.filter { segmentMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.conicsNarys.filter { conicMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.curvesNarys.filter { curveMatch(it.parentId) }.forEach { it.showInAxo = visible }
        }
        "B" -> {
            state.pointsBokorys.filter { point3DMatch(it.parent?.id) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.lines3DBokorys.filter { lineMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.segmentsBokorys.filter { segmentMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.conicsBokorys.filter { conicMatch(it.parent?.id ?: it.parentId) }.forEach { it.showInAxo = visible; it.showInAxoInitial = visible }
            state.curvesBokorys.filter { curveMatch(it.parentId) }.forEach { it.showInAxo = visible }
        }
    }
    state.triggerRedraw++
}

fun intersectionGroupProjectionVisible(state: MongeState, group: IntersectionGroup, kind: String): Boolean {
    val ids = group.parts.map { it.id }.toSet()
    val values = when (kind) {
        "A" -> state.pointsAxo.filter { it.parent?.id in ids }.map { it.showInAxo } +
                state.lines3DAxo.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.segmentsAxo.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.conicsAxo.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.curvesAxo.filter { it.parentId in ids }.map { it.showInAxo }
        "P" -> state.pointsPudorys.filter { it.parent?.id in ids }.map { it.showInAxo } +
                state.lines3DPudorys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.segmentsPudorys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.conicsPudorys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.curvesPudorys.filter { it.parentId in ids }.map { it.showInAxo }
        "N" -> state.pointsNarys.filter { it.parent?.id in ids }.map { it.showInAxo } +
                state.lines3DNarys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.segmentsNarys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.conicsNarys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.curvesNarys.filter { it.parentId in ids }.map { it.showInAxo }
        "B" -> state.pointsBokorys.filter { it.parent?.id in ids }.map { it.showInAxo } +
                state.lines3DBokorys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.segmentsBokorys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.conicsBokorys.filter { (it.parent?.id ?: it.parentId) in ids }.map { it.showInAxo } +
                state.curvesBokorys.filter { it.parentId in ids }.map { it.showInAxo }
        else -> emptyList()
    }
    return values.any { it }
}

private fun applySegmentStyle(state: MongeState, segmentId: String, style: LineStyle) {
    state.segments3D.firstOrNull { it.id == segmentId }?.lineStyle = style
    state.segmentsPudorys.replaceAll { if ((it.parent?.id ?: it.parentId) == segmentId) it.copy(localLineStyle = style) else it }
    state.segmentsNarys.replaceAll { if ((it.parent?.id ?: it.parentId) == segmentId) it.copy(localLineStyle = style) else it }
    state.segmentsBokorys.replaceAll { if ((it.parent?.id ?: it.parentId) == segmentId) it.copy(localLineStyle = style) else it }
    state.segmentsAxo.replaceAll { if ((it.parent?.id ?: it.parentId) == segmentId) it.copy(localLineStyle = style) else it }
}

private fun applyConicWidth(state: MongeState, conicId: String, width: Float) {
    state.conics3D.firstOrNull { it.id == conicId }?.strokeWidth = width
    state.conicsPudorys.replaceAll { if ((it.parent?.id ?: it.parentId) == conicId) it.copy(strokeWidth = width) else it }
    state.conicsNarys.replaceAll { if ((it.parent?.id ?: it.parentId) == conicId) it.copy(strokeWidth = width) else it }
    state.conicsBokorys.replaceAll { if ((it.parent?.id ?: it.parentId) == conicId) it.copy(strokeWidth = width) else it }
    state.conicsAxo.replaceAll { if ((it.parent?.id ?: it.parentId) == conicId) it.copy(strokeWidth = width) else it }
}

private fun applyConicStyle(state: MongeState, conicId: String, style: LineStyle) {
    val idx = state.conics3D.indexOfFirst { it.id == conicId }
    val updated = if (idx >= 0) state.conics3D[idx].copy(lineStyle = style) else null
    if (idx >= 0 && updated != null) state.conics3D[idx] = updated
    state.conicsPudorys.replaceAll { if ((it.parent?.id ?: it.parentId) == conicId) it.copy(lineStyle = style, parent = updated ?: it.parent) else it }
    state.conicsNarys.replaceAll { if ((it.parent?.id ?: it.parentId) == conicId) it.copy(lineStyle = style, parent = updated ?: it.parent) else it }
    state.conicsBokorys.replaceAll { if ((it.parent?.id ?: it.parentId) == conicId) it.copy(lineStyle = style, parent = updated ?: it.parent) else it }
    state.conicsAxo.replaceAll { if ((it.parent?.id ?: it.parentId) == conicId) it.copy(lineStyle = style, parent = updated ?: it.parent) else it }
}

private fun applyCurveWidth(state: MongeState, curveId: String, width: Float) {
    state.curves3D.firstOrNull { it.id == curveId }?.strokeWidth = width
    state.curvesPudorys.replaceAll { if (it.parentId == curveId) it.copy(strokeWidth = width) else it }
    state.curvesNarys.replaceAll { if (it.parentId == curveId) it.copy(strokeWidth = width) else it }
    state.curvesBokorys.replaceAll { if (it.parentId == curveId) it.copy(strokeWidth = width) else it }
    state.curvesAxo.replaceAll { if (it.parentId == curveId) it.copy(strokeWidth = width) else it }
}

private fun applyCurveStyle(state: MongeState, curveId: String, style: LineStyle) {
    val idx = state.curves3D.indexOfFirst { it.id == curveId }
    val updated = if (idx >= 0) state.curves3D[idx].copy(lineStyle = style) else null
    if (idx >= 0 && updated != null) state.curves3D[idx] = updated
    state.curvesPudorys.replaceAll { if (it.parentId == curveId) it.copy(lineStyle = style, parent = updated ?: it.parent) else it }
    state.curvesNarys.replaceAll { if (it.parentId == curveId) it.copy(lineStyle = style, parent = updated ?: it.parent) else it }
    state.curvesBokorys.replaceAll { if (it.parentId == curveId) it.copy(lineStyle = style, parent = updated ?: it.parent) else it }
    state.curvesAxo.replaceAll { if (it.parentId == curveId) it.copy(lineStyle = style, parent = updated ?: it.parent) else it }
}
