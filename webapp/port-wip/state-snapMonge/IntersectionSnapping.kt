package state.snapMonge

import androidx.compose.ui.geometry.Offset
import model.ProjectionMode
import model.classes.Arc2DNarys
import model.classes.Arc2DPudorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.*
import model.classes.NamedLineNarys
import model.classes.NamedLinePudorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import state.SnapIntersectionCache
import utils.toScreen

private const val X12_CLIP_EPS = 1e-4f

/**
 * Bod leží na vykreslené části půdorysné přímky: vlastní ořez (customTrimRange)
 * + efektivní ořez o x₁₂ (zrcadlí drawLinesPudorys). Cross-view kopie nárysové
 * přímky má viditelnou polorovinu obráceně (nárysové z ≥ 0 → převedené y ≤ 0).
 */
internal fun pudorysLineVisibleContains(
    state: state.MongeState,
    line: NamedLinePudorys,
    point: Offset
): Boolean {
    if (!pudorysLineTrimContainsPoint(line, point)) return false
    if (state.projectionMode == ProjectionMode.PLANE || state.projectionMode == ProjectionMode.KOTO) return true
    val clip = line.clipLineX ?: state.defaultClipBelowX12Pudorys
    if (!clip) return true
    return if (line.id.startsWith("cross-view-")) point.y <= X12_CLIP_EPS else point.y >= -X12_CLIP_EPS
}

/** Nárysová obdoba [pudorysLineVisibleContains]; bod v souřadnicích (x, z). */
internal fun narysLineVisibleContains(
    state: state.MongeState,
    line: NamedLineNarys,
    pointXZ: Offset
): Boolean {
    if (!narysLineTrimContainsPoint(line, pointXZ)) return false
    val clip = line.clipLineX ?: state.defaultClipAboveX12Narys
    if (!clip) return true
    return if (line.id.startsWith("cross-view-")) pointXZ.y <= X12_CLIP_EPS else pointXZ.y >= -X12_CLIP_EPS
}

private fun intersectionCacheSignature(
    state: state.MongeState,
    view: String,
    vararg groups: List<Any>
): String = buildString {
    append(view)
    append('|').append(state.sceneVersion)
    append('|').append(state.showConstruction.value)
    append('|').append(state.projectionMode)
    append('|').append(state.defaultClipBelowX12Pudorys)
    append('|').append(state.defaultClipAboveX12Narys)
    for (group in groups) {
        append('|').append(group.size)
        for (obj in group) {
            append(';').append(intersectionCacheKey(obj))
        }
    }
}

private fun intersectionCacheKey(obj: Any): String = when (obj) {
    is NamedLinePudorys ->
        "LP:${obj.id}:${obj.point.x}:${obj.point.y}:${obj.direction.x}:${obj.direction.y}:${obj.hashCode()}"
    is NamedLineNarys ->
        "LN:${obj.id}:${obj.point.x}:${obj.point.z}:${obj.direction.x}:${obj.direction.y}:${obj.hashCode()}"
    is SegmentsPudorys ->
        "SP:${obj.id}:${obj.start.x}:${obj.start.y}:${obj.end.x}:${obj.end.y}:${obj.hashCode()}"
    is SegmentsNarys ->
        "SN:${obj.id}:${obj.start.x}:${obj.start.z}:${obj.end.x}:${obj.end.z}:${obj.hashCode()}"
    is Arc2DPudorys ->
        "AP:${obj.id}:${obj.center.x}:${obj.center.y}:${obj.radius}:${obj.startRad}:${obj.endRad}:${obj.clockwise}:${obj.hashCode()}"
    is Arc2DNarys ->
        "AN:${obj.id}:${obj.center.x}:${obj.center.z}:${obj.radius}:${obj.startRad}:${obj.endRad}:${obj.clockwise}:${obj.hashCode()}"
    is ConicSectionPudorys ->
        "CP:${obj.id}:${obj.a}:${obj.b}:${obj.c}:${obj.d}:${obj.e}:${obj.f}:${obj.isHelpCircle}:${obj.hashCode()}"
    is ConicSectionNarys ->
        "CN:${obj.id}:${obj.a}:${obj.b}:${obj.c}:${obj.d}:${obj.e}:${obj.f}:${obj.isHelpCircle}:${obj.hashCode()}"
    else -> "${obj.javaClass.name}:${obj.hashCode()}"
}

/**
 * Převede půdorysnou pomocnou přímku do nárysových dat tak, aby po nárysovém
 * převodu (x, z) -> (x, -z) zůstala na stejném místě společného Mongeova Canvasu.
 */
private fun HelpLinePudorys.asCanvasNarysLine(): HelpLineNarys = HelpLineNarys(
    point = Point3DNarys(
        x = point.x,
        z = -point.y,
        id = "cross-view-narys-point:$id"
    ),
    direction = Offset(direction.x, -direction.y),
    name = name,
    localColor = localColor,
    localLineStyle = localLineStyle,
    localStrokeWidth = localStrokeWidth,
    lowerSuperscript = lowerSuperscript,
    id = "cross-view-narys-line:$id",
    clipLineX = clipLineX,
    clipLineZ = clipLineY,
    parentAny = parentAny,
    localSuperscript = localSuperscript,
    creationIndex = creationIndex,
    customTrimRange = customTrimRange
)

/** Opačný převod se stejnou canvasovou polohou: nárysové z odpovídá -y. */
private fun HelpLineNarys.asCanvasPudorysLine(): HelpLinePudorys = HelpLinePudorys(
    point = Point3DPudorys(
        x = point.x,
        y = -point.z,
        id = "cross-view-pudorys-point:$id"
    ),
    direction = Offset(direction.x, -direction.y),
    name = name,
    localColor = localColor,
    localLineStyle = localLineStyle,
    localStrokeWidth = localStrokeWidth,
    localSuperscript = localSuperscript,
    lowerSuperscript = lowerSuperscript,
    clipLineX = clipLineX,
    clipLineY = clipLineZ,
    id = "cross-view-pudorys-line:$id",
    parentAny = parentAny,
    creationIndex = creationIndex,
    customTrimRange = customTrimRange
)

private fun HelpSegmentPudorys.asCanvasNarysSegment(): HelpSegmentNarys = HelpSegmentNarys(
    start = Point3DNarys(
        x = start.x,
        z = -start.y,
        id = "cross-view-narys-segment-start:$id"
    ),
    end = Point3DNarys(
        x = end.x,
        z = -end.y,
        id = "cross-view-narys-segment-end:$id"
    ),
    name = name,
    parent = parent,
    localColor = localColor,
    localLineStyle = localLineStyle,
    localStrokeWidth = localStrokeWidth,
    id = "cross-view-narys-segment:$id",
    creationIndex = creationIndex
)

private fun HelpSegmentNarys.asCanvasPudorysSegment(): HelpSegmentPudorys = HelpSegmentPudorys(
    start = Point3DPudorys(
        x = start.x,
        y = -start.z,
        id = "cross-view-pudorys-segment-start:$id"
    ),
    end = Point3DPudorys(
        x = end.x,
        y = -end.z,
        id = "cross-view-pudorys-segment-end:$id"
    ),
    name = name,
    parent = parent,
    localColor = localColor,
    localLineStyle = localLineStyle,
    localStrokeWidth = localStrokeWidth,
    id = "cross-view-pudorys-segment:$id",
    creationIndex = creationIndex
)

internal fun trySnapToNarysIntersections(
    state: state.MongeState,
    screenCursor: Offset
): Offset? {
    if (state.projectionMode == ProjectionMode.KOTO) return null

    val nearPointN = state.pointsNarys.any { point ->
        val screen = Offset(point.x, point.z).toScreen(
            state.scale,
            state.canvasOffset,
            state.canvasHeight,
            state = state,
            state.canvasWidth
        )
        (screen - screenCursor).getDistance() <= state.snapThreshold
    }

    if (nearPointN || state.projectionPhase == "distance_target_place") return null

    val crossViewLinesNarys: List<NamedLineNarys> =
        if (state.projectionMode == ProjectionMode.MONGE && state.showConstruction.value) {
            state.helpLinePudorys.map { it.asCanvasNarysLine() }
        } else {
            emptyList()
        }
    val allLinesNarys: List<NamedLineNarys> =
        if (state.showConstruction.value) {
            state.lines3DNarys + state.lineTracesNarys + state.helpLineNarys + crossViewLinesNarys
        } else {
            state.lines3DNarys + state.lineTracesNarys
        }
    val allArcsNarys = if (state.showConstruction.value) state.arcsNarys else emptyList()
    val crossViewSegmentsNarys: List<SegmentsNarys> =
        if (state.projectionMode == ProjectionMode.MONGE && state.showConstruction.value) {
            state.helpSegmentsPudorys.map { it.asCanvasNarysSegment() }
        } else {
            emptyList()
        }
    val allSegmentsNarys: List<SegmentsNarys> =
        if (state.showConstruction.value) {
            state.helpSegmentsNarys + state.segmentsNarys + crossViewSegmentsNarys
        } else {
            state.segmentsNarys
        }

    val allCirclesNarys =
        if (state.showConstruction.value) state.circlesNarys else state.circlesNarys.filterNot { it.isHelpCircle }
    val intersections = cachedNarysIntersections(
        state = state,
        allLinesNarys = allLinesNarys,
        allArcsNarys = allArcsNarys,
        allSegmentsNarys = allSegmentsNarys,
        allCirclesNarys = allCirclesNarys,
        allConicsNarys = state.conicsNarys
    )

    return intersections
        .map {
            it to (it.toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            ) - screenCursor).getDistance()
        }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= state.snapThreshold }
        ?.first
}

internal fun trySnapToPudorysIntersections(
    state: state.MongeState,
    screenCursor: Offset
): Offset? {
    val nearPoint =
        state.pointsPudorys.any { point ->
            val screen = Offset(point.x, point.y).toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state = state,
                state.canvasWidth
            )
            (screen - screenCursor).getDistance() <= state.snapThreshold
        } ||
            state.aidPointsLogical.any { point ->
                val screen = Offset(point.x, point.y).toScreen(
                    state.scale,
                    state.canvasOffset,
                    state.canvasHeight,
                    state = state,
                    state.canvasWidth
                )
                (screen - screenCursor).getDistance() <= state.snapThreshold
            }

    if (nearPoint || state.projectionPhase == "distance_target_place") return null

    val crossViewLinesPudorys: List<NamedLinePudorys> =
        if (state.projectionMode == ProjectionMode.MONGE && state.showConstruction.value) {
            state.helpLineNarys.map { it.asCanvasPudorysLine() }
        } else {
            emptyList()
        }
    val allLinesPudorys: List<NamedLinePudorys> =
        if (state.showConstruction.value) {
            state.lines3DPudorys + state.lineTracesPudorys + state.helpLinePudorys + crossViewLinesPudorys
        } else {
            state.lines3DPudorys + state.lineTracesPudorys
        }
    val allArcsPudorys = if (state.showConstruction.value) state.arcsPudorys else emptyList()
    val crossViewSegmentsPudorys: List<SegmentsPudorys> =
        if (state.projectionMode == ProjectionMode.MONGE && state.showConstruction.value) {
            state.helpSegmentsNarys.map { it.asCanvasPudorysSegment() }
        } else {
            emptyList()
        }
    val allSegmentsPudorys: List<SegmentsPudorys> =
        if (state.showConstruction.value) {
            state.segmentsPudorys + state.helpSegmentsPudorys + crossViewSegmentsPudorys
        } else {
            state.segmentsPudorys
        }

    val circles =
        if (state.showConstruction.value) state.circlesPudorys else state.circlesPudorys.filterNot { it.isHelpCircle }
    val intersections = cachedPudorysIntersections(
        state = state,
        allLinesPudorys = allLinesPudorys,
        allArcsPudorys = allArcsPudorys,
        allSegmentsPudorys = allSegmentsPudorys,
        allCirclesPudorys = circles,
        allConicsPudorys = state.conicsPudorys
    )

    return intersections
        .map {
            it to (it.toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            ) - screenCursor).getDistance()
        }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= state.snapThreshold }
        ?.first
}

private fun cachedNarysIntersections(
    state: state.MongeState,
    allLinesNarys: List<NamedLineNarys>,
    allArcsNarys: List<Arc2DNarys>,
    allSegmentsNarys: List<SegmentsNarys>,
    allCirclesNarys: List<ConicSectionNarys>,
    allConicsNarys: List<ConicSectionNarys>
): List<Offset> {
    val signature = intersectionCacheSignature(
        state,
        view = "narys",
        allLinesNarys,
        allArcsNarys,
        allSegmentsNarys,
        allCirclesNarys,
        allConicsNarys
    )

    state.cachedNarysIntersections
        ?.takeIf { it.signature == signature }
        ?.let { return it.points }

    val intersections = mutableListOf<Offset>()
    val linesWithVectors = allLinesNarys.map { line ->
        val start = Offset(line.point.x, -line.point.z)
        val dir = Offset(line.direction.x, -line.direction.y)
        Triple(line, start, dir)
    }

    for (i in 0 until linesWithVectors.size) {
        for (j in i + 1 until linesWithVectors.size) {
            val (line1, p1, d1) = linesWithVectors[i]
            val (line2, p2, d2) = linesWithVectors[j]
            computeIntersection(p1, d1, p2, d2)?.let { hit ->
                val localXZ = Offset(hit.x, -hit.y)
                if (narysLineVisibleContains(state, line1, localXZ) &&
                    narysLineVisibleContains(state, line2, localXZ)
                ) {
                    intersections += hit
                }
            }
        }
    }

    for (i in 0 until allArcsNarys.size) {
        for (j in i + 1 until allArcsNarys.size) {
            intersections += intersectArcsNarys(allArcsNarys[i], allArcsNarys[j])
        }
    }

    for ((line, linePoint, lineDir) in linesWithVectors) {
        for (arc in allArcsNarys) {
            intersections += intersectLineWithArcNarys(linePoint, lineDir, arc)
                .filter { narysLineVisibleContains(state, line, Offset(it.x, -it.y)) }
        }
    }

    for ((line, linePoint, lineDir) in linesWithVectors) {
        for (segment in allSegmentsNarys) {
            val a = Offset(segment.start.x, -segment.start.z)
            val b = Offset(segment.end.x, -segment.end.z)
            intersectInfiniteLineWithSegment(linePoint, lineDir, a, b)?.let { hit ->
                if (narysLineVisibleContains(state, line, Offset(hit.x, -hit.y))) intersections += hit
            }
        }
    }

    for (i in 0 until allSegmentsNarys.size) {
        for (j in i + 1 until allSegmentsNarys.size) {
            val a1 = Offset(allSegmentsNarys[i].start.x, -allSegmentsNarys[i].start.z)
            val a2 = Offset(allSegmentsNarys[i].end.x, -allSegmentsNarys[i].end.z)
            val b1 = Offset(allSegmentsNarys[j].start.x, -allSegmentsNarys[j].start.z)
            val b2 = Offset(allSegmentsNarys[j].end.x, -allSegmentsNarys[j].end.z)
            intersectSegments(a1, a2, b1, b2)?.let { intersections += it }
        }
    }

    for (arc in allArcsNarys) {
        for (segment in allSegmentsNarys) {
            val a = Offset(segment.start.x, -segment.start.z)
            val b = Offset(segment.end.x, -segment.end.z)
            intersections += intersectArcWithSegmentNarys(arc, a, b)
        }
    }

    for (circle in allCirclesNarys) {
        for (line in allLinesNarys) {
            intersections += intersectLineWithCircleNarys(line, circle)
                .filter { narysLineVisibleContains(state, line, Offset(it.x, -it.y)) }
        }
        for (segment in allSegmentsNarys) {
            intersections += intersectSegmentWithCircleNarys(segment, circle)
        }
        for (arc in allArcsNarys) {
            intersections += intersectArcWithCircleNarys(arc, circle)
        }
    }

    for (i in 0 until allCirclesNarys.size) {
        for (j in i + 1 until allCirclesNarys.size) {
            intersections += intersectCirclesNarys(allCirclesNarys[i], allCirclesNarys[j])
        }
    }

    for (conic in allConicsNarys) {
        for (line in allLinesNarys) {
            intersections += intersectLineWithConicNarys(line, conic)
                .filter { narysLineVisibleContains(state, line, Offset(it.x, -it.y)) }
        }
        for (segment in allSegmentsNarys) {
            intersections += intersectSegmentWithConicNarys(segment, conic)
        }
    }

    val points = intersections.toList()
    state.cachedNarysIntersections = SnapIntersectionCache(signature, points)
    return points
}

private fun cachedPudorysIntersections(
    state: state.MongeState,
    allLinesPudorys: List<NamedLinePudorys>,
    allArcsPudorys: List<Arc2DPudorys>,
    allSegmentsPudorys: List<SegmentsPudorys>,
    allCirclesPudorys: List<ConicSectionPudorys>,
    allConicsPudorys: List<ConicSectionPudorys>
): List<Offset> {
    val signature = intersectionCacheSignature(
        state,
        view = "pudorys",
        allLinesPudorys,
        allArcsPudorys,
        allSegmentsPudorys,
        allCirclesPudorys,
        allConicsPudorys
    )

    state.cachedPudorysIntersections
        ?.takeIf { it.signature == signature }
        ?.let { return it.points }

    val intersections = mutableListOf<Offset>()
    val linesWithVectors = allLinesPudorys.map { line ->
        Triple(line, Offset(line.point.x, line.point.y), line.direction)
    }

    for (i in 0 until linesWithVectors.size) {
        for (j in i + 1 until linesWithVectors.size) {
            val (line1, p1, d1) = linesWithVectors[i]
            val (line2, p2, d2) = linesWithVectors[j]
            computeIntersection(p1, d1, p2, d2)?.let { hit ->
                if (pudorysLineVisibleContains(state, line1, hit) &&
                    pudorysLineVisibleContains(state, line2, hit)
                ) {
                    intersections += hit
                }
            }
        }
    }

    for ((line, linePoint, lineDir) in linesWithVectors) {
        for (arc in allArcsPudorys) {
            intersections += intersectLineWithArcPudorys(linePoint, lineDir, arc)
                .filter { pudorysLineVisibleContains(state, line, it) }
        }
    }

    for (i in 0 until allArcsPudorys.size) {
        for (j in i + 1 until allArcsPudorys.size) {
            intersections += intersectArcsPudorys(allArcsPudorys[i], allArcsPudorys[j])
        }
    }

    for ((line, linePoint, lineDir) in linesWithVectors) {
        for (segment in allSegmentsPudorys) {
            val a = Offset(segment.start.x, segment.start.y)
            val b = Offset(segment.end.x, segment.end.y)
            intersectInfiniteLineWithSegment(linePoint, lineDir, a, b)?.let { hit ->
                if (pudorysLineVisibleContains(state, line, hit)) intersections += hit
            }
        }
    }

    for (i in 0 until allSegmentsPudorys.size) {
        for (j in i + 1 until allSegmentsPudorys.size) {
            val a1 = Offset(allSegmentsPudorys[i].start.x, allSegmentsPudorys[i].start.y)
            val a2 = Offset(allSegmentsPudorys[i].end.x, allSegmentsPudorys[i].end.y)
            val b1 = Offset(allSegmentsPudorys[j].start.x, allSegmentsPudorys[j].start.y)
            val b2 = Offset(allSegmentsPudorys[j].end.x, allSegmentsPudorys[j].end.y)
            intersectSegments(a1, a2, b1, b2)?.let { intersections += it }
        }
    }

    for (segment in allSegmentsPudorys) {
        val a = Offset(segment.start.x, segment.start.y)
        val b = Offset(segment.end.x, segment.end.y)
        for (arc in allArcsPudorys) {
            intersections += intersectSegmentWithArcPudorys(a, b, arc)
        }
    }

    for (circle in allCirclesPudorys) {
        for (line in allLinesPudorys) {
            intersections += intersectLineWithCirclePudorys(line, circle)
                .filter { pudorysLineVisibleContains(state, line, it) }
        }
        for (segment in allSegmentsPudorys) {
            intersections += intersectSegmentWithCirclePudorys(segment, circle)
        }
        for (arc in allArcsPudorys) {
            intersections += intersectArcWithCirclePudorys(arc, circle)
        }
    }

    for (i in 0 until allCirclesPudorys.size) {
        for (j in i + 1 until allCirclesPudorys.size) {
            intersections += intersectCirclesPudorys(allCirclesPudorys[i], allCirclesPudorys[j])
        }
    }

    for (conic in allConicsPudorys) {
        for (line in allLinesPudorys) {
            intersections += intersectLineWithConicPudorys(line, conic)
                .filter { pudorysLineVisibleContains(state, line, it) }
        }
        for (segment in allSegmentsPudorys) {
            intersections += intersectSegmentWithConicPudorys(segment, conic)
        }
    }

    val points = intersections.toList()
    state.cachedPudorysIntersections = SnapIntersectionCache(signature, points)
    return points
}
