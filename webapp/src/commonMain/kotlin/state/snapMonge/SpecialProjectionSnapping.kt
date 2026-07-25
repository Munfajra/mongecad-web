package state.snapMonge

import androidx.compose.ui.geometry.Offset
import model.ConstructionModifier
import model.Mongeobjects
import model.ProjectionType
import model.XAxisDirection
import model.YAxisDirectionPlane
import utils.dotProduct
import utils.getLogicalCursor

private fun snapToImageCross(
    state: state.MongeState,
    cursorPosition: Offset,
    base: Offset
): Offset? {
    val cursor = getLogicalCursor(
        null,
        cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val tolerance = state.snapThreshold / state.scale
    val dx = kotlin.math.abs(cursor.x - base.x)
    val dy = kotlin.math.abs(cursor.y - base.y)

    return when {
        dx <= tolerance && dx < dy -> Offset(base.x, cursor.y)
        dy <= tolerance -> Offset(cursor.x, base.y)
        else -> null
    }
}

private fun snapOnAssociatedVerticals(
    state: state.MongeState,
    fixedX: Float,
    screenCursor: Offset
): Offset {
    val originN = Offset(fixedX, 0f)
    val dirN = Offset(0f, -100f)
    val snappedN = getSnappedPointOnTemporaryLineNarys(state, screenCursor, originN, dirN)

    val originP = Offset(fixedX, 0f)
    val dirP = Offset(0f, 100f)
    val snappedP = getSnappedPointOnTemporaryLine(state, screenCursor, originP, dirP)
    val projP = if (snappedP == null) {
        projectionOnTempLine(screenCursor, originP, dirP, planeNarys = false, state)
    } else {
        null
    }

    return when {
        snappedN != null -> snappedN
        snappedP != null -> snappedP
        projP != null -> projP
        else -> originN
    }
}

internal fun tryAssociatedProjectionSnap(
    state: state.MongeState,
    logicalCursor: Offset
): SnapDecision {
    if (
        state.drawobjects == Mongeobjects.POINTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        (state.projectionPhase == "pudorys_to_narys_point" || state.projectionPhase == "narys_to_pudorys_point") &&
        state.pendingX != null
    ) {
        return SnapDecision.Handled(
            snapOnAssociatedVerticals(state, state.pendingX!!, state.cursorPosition)
        )
    }

    if (
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.constructionModifier != ConstructionModifier.ORTHOGONAL &&
        state.constructionModifier != ConstructionModifier.PARALLEL &&
        state.projectionPhase in listOf(
            "pudorys_segment_associated_A_narys_start",
            "pudorys_segment_associated_B_narys_start",
            "narys_segment_associated_A_pudorys_start",
            "narys_segment_associated_B_pudorys_start"
        )
    ) {
        val fixedX = when (state.projectionPhase) {
            "pudorys_segment_associated_A_narys_start",
            "narys_segment_associated_A_pudorys_start" -> state.pendingXA
            "pudorys_segment_associated_B_narys_start",
            "narys_segment_associated_B_pudorys_start" -> state.pendingXB
            else -> null
        } ?: return SnapDecision.Handled(logicalCursor)

        return SnapDecision.Handled(
            snapOnAssociatedVerticals(state, fixedX, state.cursorPosition)
        )
    }

    if (
        state.drawobjects == Mongeobjects.LINES &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf("special_case_point_in_narys", "special_case_point_in_pudorys")
    ) {
        val fixedX = when (state.projectionPhase) {
            "special_case_point_in_narys" -> state.pendingXpudorys
            "special_case_point_in_pudorys" -> state.pendingXnarys
            else -> null
        } ?: return SnapDecision.Handled(logicalCursor)

        return SnapDecision.Handled(
            snapOnAssociatedVerticals(state, fixedX, state.cursorPosition)
        )
    }

    return SnapDecision.NotHandled
}

internal fun tryConstructionCrossSnap(
    state: state.MongeState,
    cursorPosition: Offset
): SnapDecision {
    fun snapFromPudorys(base: Offset): SnapDecision? =
        snapToCrossWithIntersectionsPudorys(state, cursorPosition, base)
            ?.let(SnapDecision::Handled)

    fun snapFromNarys(base: Offset): SnapDecision? =
        snapToCrossWithIntersectionsNarys(state, cursorPosition, base)
            ?.let(SnapDecision::Handled)

    fun snapFromImageCross(base: Offset): SnapDecision? =
        snapToImageCross(state, cursorPosition, base)
            ?.let(SnapDecision::Handled)

    if (
        state.drawobjects == Mongeobjects.GETDISTANCE &&
        state.projectionPhase == "distance_point2_select" &&
        state.pendingPoint1 != null
    ) {
        snapFromImageCross(state.pendingPoint1!!)?.let { return it }
    }

    if (
        (state.drawobjects == Mongeobjects.CIRCLE || state.drawobjects == Mongeobjects.ELLIPSE) &&
        state.pendingPoint1 != null
    ) {
        snapFromImageCross(state.pendingPoint1!!)?.let { return it }
    }

    if (
        state.drawobjects == Mongeobjects.PLANE &&
        state.constructionModifier == ConstructionModifier.NONE
    ) {
        when (state.projectionPhase) {
            "plane_trace_pudorys_start",
            "plane_trace_single_pudorys_start" -> {
                val start = state.firstPlaneTraceStartPudorys
                if (start != null) {
                    snapFromImageCross(Offset(start.x, start.y))?.let { return it }
                }
            }

            "plane_trace_narys_start",
            "plane_trace_single_narys_start" -> {
                val start = state.firstPlaneTraceStartNarys
                if (start != null) {
                    snapFromImageCross(Offset(start.x, -start.z))?.let { return it }
                }
            }

            "plane_trace_pudorys_direction" -> {
                val start = state.xOnX12Pudorys
                if (start != null) {
                    snapFromImageCross(Offset(start.x, start.y))?.let { return it }
                }
            }

            "plane_trace_narys_direction" -> {
                val start = state.xOnX12Narys
                if (start != null) {
                    snapFromImageCross(Offset(start.x, -start.z))?.let { return it }
                }
            }
        }
    }

    if (state.drawobjects == Mongeobjects.LINES && state.constructionModifier == ConstructionModifier.NONE) {
        if (state.lineStartPoint3DPudorys != null && state.projectionPhase in listOf(
                "pudorys_start",
                "projection_line_dir_pudorys",
                "pudorys_dir_finalize_auto"
            )
        ) {
            val base = Offset(state.lineStartPoint3DPudorys!!.x, state.lineStartPoint3DPudorys!!.y)
            snapToCrossWithIntersectionsPudorys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
        if (state.lineStartPoint3DNarys != null && state.projectionPhase in listOf(
                "narys_start",
                "projection_line_dir_narys",
                "narys_dir_finalize_auto"
            )
        ) {
            val base = Offset(state.lineStartPoint3DNarys!!.x, -state.lineStartPoint3DNarys!!.z)
            snapToCrossWithIntersectionsNarys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
        if (state.pendingXpudorys != null && state.pendingY != null && state.projectionPhase in listOf(
                "projection_line_dir_pudorys",
                "projection_line_start_pudorys_dir",
                "pudorys_dir_finalize_auto"
            )
        ) {
            val base = Offset(state.pendingXpudorys!!, state.pendingY!!)
            snapToCrossWithIntersectionsPudorys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
        if (state.pendingXnarys != null && state.pendingZ != null && state.projectionPhase in listOf(
                "projection_line_dir_narys_start",
                "projection_line_narys_dir",
                "narys_dir_finalize_auto"
            )
        ) {
            val base = Offset(state.pendingXnarys!!, -state.pendingZ!!)
            snapToCrossWithIntersectionsNarys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
    }

    if (state.drawobjects == Mongeobjects.SEGMENTS && state.constructionModifier == ConstructionModifier.NONE) {
        if (state.segmentStartPudorys != null) {
            val base = Offset(state.segmentStartPudorys!!.x, state.segmentStartPudorys!!.y)
            snapToCrossWithIntersectionsPudorys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
        if (state.segmentStartNarys != null) {
            val base = Offset(state.segmentStartNarys!!.x, -state.segmentStartNarys!!.z)
            snapToCrossWithIntersectionsNarys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
        if (
            state.pendingXA != null &&
            state.pendingYA != null &&
            state.projectionPhase == "pudorys_segment_associated_B_pudorys_start"
        ) {
            val base = Offset(state.pendingXA!!, state.pendingYA!!)
            snapToCrossWithIntersectionsPudorys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
        if (
            state.pendingXA != null &&
            state.pendingZA != null &&
            state.projectionPhase == "narys_segment_associated_B_narys_start"
        ) {
            val base = Offset(state.pendingXA!!, -state.pendingZA!!)
            snapToCrossWithIntersectionsNarys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
    }

    if (
        state.drawobjects == Mongeobjects.SEGMENT_ON_LINE &&
        state.constructionModifier == ConstructionModifier.NONE &&
        state.projectionPhase in listOf("sol_narys_second_point_dir", "sol_pudorys_second_point_dir")
    ) {
        if (state.pendingLinePointPudorys != null) {
            val base = Offset(state.pendingLinePointPudorys!!.x, state.pendingLinePointPudorys!!.y)
            snapToCrossWithIntersectionsPudorys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
        if (state.pendingLinePointNarys != null) {
            val base = Offset(state.pendingLinePointNarys!!.x, -state.pendingLinePointNarys!!.y)
            snapToCrossWithIntersectionsNarys(state, cursorPosition, base)?.let {
                return SnapDecision.Handled(it)
            }
        }
    }

    return SnapDecision.NotHandled
}

internal fun tryTemporaryLineSnap(state: state.MongeState): SnapDecision {
    if (!shouldSnapToTemporaryLine(state)) return SnapDecision.NotHandled

    val isKotoLinePick =
        state.projectionPhase in listOf("KOTO_line_point1", "KOTO_line_point2", "line_point_pick_xy") &&
            state.kotoLineRefPudorys != null

    val originN: Offset =
        if (!isKotoLinePick) {
            if (state.pendingLinePointNarys != null) {
                Offset(state.pendingLinePointNarys!!.x, -state.pendingLinePointNarys!!.y)
            } else {
                Offset(state.pendingLinePointPudorys!!.x, state.pendingLinePointPudorys!!.y)
            }
        } else {
            when {
                isKotoLinePick -> {
                    val ref = state.kotoLineRefPudorys!!
                    Offset(ref.point.x, ref.point.y)
                }
                state.pendingLinePointPudorys != null -> state.pendingLinePointPudorys!!
                else -> Offset(state.pendingLinePointNarys!!.x, -state.pendingLinePointNarys!!.y)
            }
        }

    val dirN: Offset =
        if (!isKotoLinePick) {
            if (state.pendingDirectionNarys != null) {
                Offset(state.pendingDirectionNarys!!.x, state.pendingDirectionNarys!!.y)
            } else {
                Offset(state.pendingDirection!!.x, -state.pendingDirection!!.y)
            }
        } else {
            when {
                isKotoLinePick -> {
                    val ref = state.kotoLineRefPudorys!!
                    ref.direction
                }
                state.pendingDirection != null -> state.pendingDirection!!
                else -> Offset(state.pendingDirectionNarys!!.x, -state.pendingDirectionNarys!!.y)
            }
        }

    val originP: Offset =
        when {
            isKotoLinePick -> {
                val ref = state.kotoLineRefPudorys!!
                Offset(ref.point.x, ref.point.y)
            }
            state.pendingLinePointPudorys != null -> state.pendingLinePointPudorys!!
            else -> Offset(state.pendingLinePointNarys!!.x, -state.pendingLinePointNarys!!.y)
        }

    val dirP: Offset =
        when {
            isKotoLinePick -> {
                val ref = state.kotoLineRefPudorys!!
                ref.direction
            }
            state.pendingDirection != null -> state.pendingDirection!!
            else -> Offset(state.pendingDirectionNarys!!.x, -state.pendingDirectionNarys!!.y)
        }

    val lenN = dirN.getDistance()
    val lenP = dirP.getDistance()
    val screenCursor = state.cursorPosition

    val snappedN: Offset? =
        if (lenN > 1e-6f) {
            if (!isKotoLinePick) getSnappedPointOnTemporaryLineNarys(state, screenCursor, originN, dirN) else null
        } else {
            null
        }

    val snappedP: Offset? =
        if (lenP > 1e-6f) getSnappedPointOnTemporaryLine(state, screenCursor, originP, dirP) else null

    val projP: Offset? =
        if (snappedP == null && lenP > 1e-6f) {
            projectCursorOnTemporaryLine(state, screenCursor, originP, dirP, planeNarys = false)
        } else {
            null
        }

    return SnapDecision.Handled(
        when {
            snappedN != null -> snappedN
            snappedP != null -> snappedP
            projP != null -> projP
            else -> originN
        }
    )
}

private fun shouldSnapToTemporaryLine(state: state.MongeState): Boolean {
    return (
        state.projectionPhase in listOf(
            "segment_parallel_first_point_narys",
            "segment_parallel_second_point_narys",
            "segment_orthogonal_first_point_narys",
            "narys_segment_associated_A_pudorys_start_orthogonal",
            "narys_segment_associated_B_narys_start_orthogonal",
            "narys_segment_associated_B_narys_start_orthogonal",
            "segment_orthogonal_second_point_narys",
            "trans_parallel_final_point_narys",
            "segment_parallel_first_point_pudorys",
            "segment_parallel_second_point_pudorys",
            "segment_orthogonal_first_point_pudorys",
            "segment_orthogonal_first_point_pudorys_narys",
            "segment_orthogonal_first_point_pudorys_narys",
            "pudorys_segment_associated_B_pudorys_start_orthogonal",
            "segment_orthogonal_second_point_pudorys",
            "trans_parallel_final_point_pudorys"
        ) &&
            (
                (state.pendingLinePointNarys != null && state.pendingDirectionNarys != null) ||
                    (state.pendingLinePointPudorys != null && state.pendingDirection != null)
                )
        ) ||
        (
            state.projectionPhase in listOf(
                "sol_begin_segment_pud",
                "sol_second_point_pudorys",
                "sol_begin_segment_nar",
                "sol_second_point_narys"
            ) &&
                (state.selectedLineForParallelPudorys != null || state.selectedLineForParallelNarys != null)
            ) ||
        (state.projectionPhase in listOf("KOTO_line_point1", "KOTO_line_point2") && state.kotoLineRefPudorys != null) ||
        (state.projectionPhase == "line_point_pick_xy" && state.selectedLineForPoint != null)
}

private fun projectCursorOnTemporaryLine(
    state: state.MongeState,
    screenCursor: Offset,
    origin: Offset,
    dir: Offset,
    planeNarys: Boolean
): Offset {
    val logical = getLogicalCursor(
        null,
        screenCursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val inPlane = if (planeNarys) Offset(logical.x, -logical.y) else logical
    val unit = dir / dir.getDistance()
    return origin + unit * ((inPlane - origin).dotProduct(unit))
}
