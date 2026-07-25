package draw.mongescreen.previews.segments.narys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.axo.drawDashedPreviewSegmentNarysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewNarysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewNarys
import draw.mongescreen.previews.segments.pudorys.drawPreviewCross
import model.*
import model.axo.AxoMode
import model.classes.Point3DNarys
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.resolveNarysDirectionAxo
import monge.input.axo.segments.projectPointOntoLineByPointAndDir
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewNarysSegmentCursor(state: MongeState,snappedPointLogical: Offset?){
    val cursorLogical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val cursorWorld = Offset(
        cursorLogical.x,
        cursorLogical.y)

    if (state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.segmentStartNarys != null
    ) {
        val projectedCursorWorld = if (state.projectionPhase in listOf(
                "segment_parallel_second_point_narys",
                "segment_orthogonal_second_point_narys",
                "narys_segment_associated_B_narys_start_orthogonal"
            )
        ) {
            val origin = state.pendingLinePointNarys
            val direction = state.pendingDirectionNarys
            if (origin != null && direction != null) {
                projectPointOntoDirectionLineNarys(
                    point = Offset(cursorWorld.x, -cursorWorld.y),
                    origin = origin,
                    direction = direction
                )?.let { Offset(it.x, -it.y) } ?: cursorWorld
            } else {
                cursorWorld
            }
        } else {
            cursorWorld
        }

        drawDashedSegmentPreviewNarys(
            start = state.segmentStartNarys!!,
            cursorWorld = projectedCursorWorld,
            scale = state.scale,
            dashLength = 1f,
            gapLength= 0f,
            color = Color.Red,
            strokeWidth = 3f,
            canvasOffset = state.canvasOffset
        )
    }
}

private fun projectPointOntoDirectionLineNarys(
    point: Offset,
    origin: Offset,
    direction: Offset
): Offset? {
    val length = direction.getDistance()
    if (length < 1e-6f) return null
    val unit = direction / length
    val delta = point - origin
    val t = delta.x * unit.x + delta.y * unit.y
    return origin + unit * t
}
fun DrawScope.previewAssociatedNarysSegmentCursor(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "narys_segment_associated_B_narys_start",
            "narys_segment_associated_B_pudorys_start"
        )
    ) {
        val xA = state.pendingXA
        val zA = state.pendingZA
        if (xA != null && zA != null) {
            val cursorLogical = getLogicalCursor(
                snappedPointLogical,
                state.cursorPosition,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )

            val cursorWorld = when (state.projectionPhase) {
                "narys_segment_associated_B_pudorys_start" -> Offset(cursorLogical.x, cursorLogical.y)
                else -> Offset(cursorLogical.x, cursorLogical.y)
            }

            val start = Point3DNarys(xA, zA, name = "")
            drawDashedSegmentPreviewNarys(
                start = start,
                cursorWorld = cursorWorld,
                scale = state.scale,
                dashLength = 1f,
                gapLength= 0f,
                color = Color.Red,
                strokeWidth = 3f,
                canvasOffset = state.canvasOffset
            )
        }
    }
}

fun DrawScope.previewAxoNarysSegmentCursor(
    state: MongeState,
    snappedPointLogical: Offset?,
    visibleQuad: VisibleQuad?
) {
    val cursorLogical = getLogicalCursorAxo(
        snapped = snappedPointLogical,
        cursor = state.cursorPosition,
        axoModel = state.activeAxoModel,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_NARYS
    ) ?: return

    if (state.drawobjects == Mongeobjects.SEGMENT_ON_LINE) {
        previewAxoNarysSegmentOnLineCursor(state, cursorLogical, visibleQuad)
        return
    }

    if (state.drawobjects != Mongeobjects.SEGMENTS) return
    if (state.projekcnityp != ProjectionType.SINGLE) return

    val isDirected =
        state.constructionModifier == ConstructionModifier.PARALLEL ||
                state.constructionModifier == ConstructionModifier.ORTHOGONAL

    if (isDirected) {
        val visible = visibleQuad ?: return
        val isOrthogonal = state.constructionModifier == ConstructionModifier.ORTHOGONAL

        val direction = resolveNarysDirectionAxo(
            state = state,
            wantPerpendicular = isOrthogonal
        )

        when (state.projectionPhase) {
            "Narys_directed_segment_place_line" -> {
                if (direction != null) {
                    drawDashedParallelLinePreviewNarysAxo(
                        through = Point3DNarys(cursorLogical.x, cursorLogical.y, name = "?"),
                        direction = direction,
                        visibleQuad = visible,
                        scale = state.scale,
                        color = Color.Gray,
                        strokeWidth = 1.5f
                    )
                }
                return
            }

            "Narys_directed_segment_start",
            "Narys_directed_segment_end" -> {
                val linePoint = state.pendingPoint1 ?: return
                val lineDir = state.pendingDirection ?: direction ?: return

                drawDashedParallelLinePreviewNarysAxo(
                    through = Point3DNarys(linePoint.x, linePoint.y, name = "?"),
                    direction = lineDir,
                    visibleQuad = visible,
                    scale = state.scale,
                    color = Color.Gray,
                    strokeWidth = 1.5f
                )

                if (state.projectionPhase == "Narys_directed_segment_start") {
                    val projected = projectPointOntoLineByPointAndDir(
                        p = cursorLogical,
                        linePoint = linePoint,
                        lineDir = lineDir
                    )

                    drawPreviewCross(projected, state.scale, Color.Red)
                    return
                }

                if (state.projectionPhase == "Narys_directed_segment_end") {
                    val start = state.segmentStartNarys ?: return
                    val projectedEnd = projectPointOntoLineByPointAndDir(
                        p = cursorLogical,
                        linePoint = linePoint,
                        lineDir = lineDir
                    )

                    drawDashedPreviewSegmentNarysAxo(
                        start = start,
                        cursorWorld = projectedEnd,
                        scale = state.scale,
                        color = Color.Red,
                        strokeWidth = 3f
                    )

                    drawPreviewCross(Offset(start.x, start.z), state.scale, Color.Red)
                    drawPreviewCross(projectedEnd, state.scale, Color.Red)
                    return
                }
            }
        }
    }

    if (state.segmentStartNarys != null) {
        val point = state.segmentStartNarys!!

        drawDashedPreviewSegmentNarysAxo(
            start = point,
            cursorWorld = cursorLogical,
            scale = state.scale,
            color = Color.Red,
            strokeWidth = 3f,
        )

        drawPreviewCross(Offset(point.x, point.z), state.scale, Color.Red)
    }
}

private fun DrawScope.previewAxoNarysSegmentOnLineCursor(
    state: MongeState,
    cursorLogical: Offset,
    visibleQuad: VisibleQuad?
) {
    val visible = visibleQuad ?: return

    when (state.projectionPhase) {
        "sol_narys_line_second" -> {
            val linePoint = state.pendingPoint1 ?: return
            drawDashedParallelLinePreviewNarysAxo(
                through = Point3DNarys(linePoint.x, linePoint.y, name = "?"),
                direction = cursorLogical - linePoint,
                visibleQuad = visible,
                scale = state.scale,
                color = Color.Gray,
                strokeWidth = 1.5f
            )
        }

        "sol_narys_segment_start" -> {
            val temp = state.tempLine ?: return
            val projected = projectPointOntoLineByPointAndDir(
                p = cursorLogical,
                linePoint = temp.point,
                lineDir = temp.direction
            )
            drawPreviewCross(projected, state.scale, Color.Red)
        }

        "sol_narys_segment_end" -> {
            val temp = state.tempLine ?: return
            val start = state.segmentStartNarys ?: return
            val projectedEnd = projectPointOntoLineByPointAndDir(
                p = cursorLogical,
                linePoint = temp.point,
                lineDir = temp.direction
            )

            drawDashedPreviewSegmentNarysAxo(
                start = start,
                cursorWorld = projectedEnd,
                scale = state.scale,
                color = Color.Red,
                strokeWidth = 3f
            )

            drawPreviewCross(Offset(start.x, start.z), state.scale, Color.Red)
            drawPreviewCross(projectedEnd, state.scale, Color.Red)
        }
    }
}
