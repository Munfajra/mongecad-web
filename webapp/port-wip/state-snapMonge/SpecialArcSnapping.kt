package state.snapMonge

import androidx.compose.ui.geometry.Offset
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.NamedLineNarys
import model.classes.NamedLinePudorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal sealed interface SnapDecision {
    data object NotHandled : SnapDecision
    data class Handled(val point: Offset?) : SnapDecision
}

internal fun tryConstructionArcSnapPudorys(
    state: state.MongeState,
    logicalCursor: Offset
): SnapDecision {
    if (!state.arc.isSnappingToArc) return SnapDecision.NotHandled

    val center = state.arc.arcCenterPudorys ?: return SnapDecision.Handled(null)
    val radiusPoint = state.arc.arcRadiusPointPudorys ?: return SnapDecision.Handled(null)

    val centerLogical = Offset(center.x, center.y)
    val radius = hypot(radiusPoint.x - center.x, radiusPoint.y - center.y)
    if (radius <= 1e-6f) return SnapDecision.Handled(centerLogical)

    val direction = logicalCursor - centerLogical
    val angle = atan2(direction.y, direction.x)
    val snappedOnCircle = Offset(
        center.x + radius * cos(angle),
        center.y + radius * sin(angle)
    )

    val circleConic = makeCircleConicPudorys(centerLogical, radius)
    val intersections = mutableListOf<Offset>()
    val snapRadiusLogical = state.snapThreshold / state.scale
    val epsOnCircle = snapRadiusLogical * 0.35f

    val aidSnap = state.findNearestAidPointLogical(
        cursorLogical = logicalCursor,
        snapRadiusLogical = snapRadiusLogical
    )
    if (aidSnap != null) {
        val aidPt = Offset(aidSnap.x, aidSnap.y)
        if (isPointOnCircle(aidPt, centerLogical, radius, epsOnCircle)) {
            state.hoveredAidPointId = aidSnap.id
            return SnapDecision.Handled(aidPt)
        }
    }

    val allLines: List<NamedLinePudorys> =
        state.lines3DPudorys + state.lineTracesPudorys + state.helpLinePudorys
    for (line in allLines) {
        intersections += intersectLineWithCirclePudorys(line, circleConic)
    }

    val allSegments: List<SegmentsPudorys> =
        state.segmentsPudorys + state.helpSegmentsPudorys
    for (segment in allSegments) {
        intersections += intersectSegmentWithCirclePudorys(segment, circleConic)
    }

    for (arc in state.arcsPudorys) {
        intersections += intersectArcWithCirclePudorys(arc, circleConic)
    }

    val allConics: List<ConicSectionPudorys> = state.circlesPudorys
    for (circle in allConics) {
        intersections += intersectCirclesPudorys(circle, circleConic)
    }

    val bestIntersection = intersections
        .asSequence()
        .distinctBy { "${(it.x * 10000f).toInt()}_${(it.y * 10000f).toInt()}" }
        .map { point -> point to (point - logicalCursor).getDistance() }
        .filter { (_, dist) -> dist <= snapRadiusLogical }
        .minByOrNull { it.second }
        ?.first

    return SnapDecision.Handled(bestIntersection ?: snappedOnCircle)
}

internal fun tryConstructionArcSnapNarys(
    state: state.MongeState,
    logicalCursor: Offset
): SnapDecision {
    if (!state.arc.isSnappingToArcNarysOnly) return SnapDecision.NotHandled

    val center = state.arc.arcCenterNarys ?: return SnapDecision.Handled(null)
    val radiusPoint = state.arc.arcRadiusPointNarys ?: return SnapDecision.Handled(null)

    val centerLogical = Offset(center.x, -center.z)
    val radius = hypot(radiusPoint.x - center.x, radiusPoint.z - center.z)
    if (radius <= 1e-6f) return SnapDecision.Handled(centerLogical)

    val direction = logicalCursor - centerLogical
    val angle = atan2(direction.y, direction.x)
    val snappedOnCircle = Offset(
        centerLogical.x + radius * cos(angle),
        centerLogical.y + radius * sin(angle)
    )

    val circleConic = makeCircleConicNarys(center.x, center.z, radius)
    val intersections = mutableListOf<Offset>()
    val snapRadiusLogical = state.snapThreshold / state.scale
    val epsOnCircle = snapRadiusLogical * 0.35f

    val aidSnap = state.findNearestAidPointLogical(
        cursorLogical = logicalCursor,
        snapRadiusLogical = snapRadiusLogical
    )
    if (aidSnap != null) {
        val aidPt = Offset(aidSnap.x, aidSnap.y)
        if (isPointOnCircle(aidPt, centerLogical, radius, epsOnCircle)) {
            state.hoveredAidPointId = aidSnap.id
            return SnapDecision.Handled(aidPt)
        }
    }

    val allLines: List<NamedLineNarys> =
        state.lines3DNarys + state.lineTracesNarys + state.helpLineNarys
    for (line in allLines) {
        intersections += intersectLineWithCircleNarys(line, circleConic)
    }

    val allSegments: List<SegmentsNarys> =
        state.segmentsNarys + state.helpSegmentsNarys
    for (segment in allSegments) {
        intersections += intersectSegmentWithCircleNarys(segment, circleConic)
    }

    for (arc in state.arcsNarys) {
        intersections += intersectArcWithCircleNarys(arc, circleConic)
    }

    val allConics: List<ConicSectionNarys> = state.circlesNarys
    for (circle in allConics) {
        intersections += intersectCirclesNarys(circle, circleConic)
    }

    val bestIntersection = intersections
        .asSequence()
        .distinctBy { "${(it.x * 10000f).toInt()}_${(it.y * 10000f).toInt()}" }
        .map { point -> point to (point - logicalCursor).getDistance() }
        .filter { (_, dist) -> dist <= snapRadiusLogical }
        .minByOrNull { it.second }
        ?.first

    return SnapDecision.Handled(bestIntersection ?: snappedOnCircle)
}
