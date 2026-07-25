package draw.mongescreen.previews.segments.pudorys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.axo.drawDashedPreviewSegmentPudorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewPudorys
import model.*
import model.axo.AxoMode
import model.classes.Point3DPudorys
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.resolvePudorysDirectionAxo
import monge.input.axo.segments.projectPointOntoLineByPointAndDir
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewPudorysSegmentCursor(state: MongeState, snappedPointLogical: Offset?){
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

    if (state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.segmentStartPudorys != null
    ) {
        val projectedCursorWorld = if (state.projectionPhase in listOf(
                "segment_parallel_second_point_pudorys",
                "segment_orthogonal_second_point_pudorys",
                "pudorys_segment_associated_B_pudorys_start_orthogonal"
            )
        ) {
            val origin = state.pendingLinePointPudorys
            val direction = state.pendingDirection
            if (origin != null && direction != null) {
                projectPointOntoDirectionLine(cursorWorld, origin, direction) ?: cursorWorld
            } else {
                cursorWorld
            }
        } else {
            cursorWorld
        }

        drawDashedSegmentPreviewPudorys(
            start = state.segmentStartPudorys!!,
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

private fun projectPointOntoDirectionLine(
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
fun DrawScope.previewAssociatedPudorysSegmentCursor(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "pudorys_segment_associated_B_narys_start",
            "pudorys_segment_associated_B_pudorys_start"
        )
    ) {
        val xA = state.pendingXA
        val yA = state.pendingYA
        if (xA != null && yA != null) {
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
            val cursorWorld = Offset(cursorLogical.x, cursorLogical.y)

            val start = Point3DPudorys(xA, yA, name = "")
            drawDashedSegmentPreviewPudorys(
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
fun DrawScope.previewAxoPudorysSegmentCursor(
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
        mode = AxoMode.AXO_PUDORYS
    ) ?: return

    if (state.drawobjects == Mongeobjects.SEGMENT_ON_LINE) {
        previewAxoPudorysSegmentOnLineCursor(state, cursorLogical, visibleQuad)
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

        val direction = resolvePudorysDirectionAxo(
            state = state,
            wantPerpendicular = isOrthogonal
        )

        when (state.projectionPhase) {
            "pudorys_directed_segment_place_line" -> {
                if (direction != null) {
                    drawDashedParallelLinePreviewPudorysAxo(
                        through = Point3DPudorys(cursorLogical.x, cursorLogical.y, name = "?"),
                        direction = direction,
                        visibleQuad = visible,
                        scale = state.scale,
                        color = Color.Gray,
                        strokeWidth = 1.5f
                    )
                }
                return
            }

            "pudorys_directed_segment_start",
            "pudorys_directed_segment_end" -> {
                val linePoint = state.pendingPoint1 ?: return
                val lineDir = state.pendingDirection ?: direction ?: return

                drawDashedParallelLinePreviewPudorysAxo(
                    through = Point3DPudorys(linePoint.x, linePoint.y, name = "?"),
                    direction = lineDir,
                    visibleQuad = visible,
                    scale = state.scale,
                    color = Color.Gray,
                    strokeWidth = 1.5f
                )

                if (state.projectionPhase == "pudorys_directed_segment_start") {
                    val projected = projectPointOntoLineByPointAndDir(
                        p = cursorLogical,
                        linePoint = linePoint,
                        lineDir = lineDir
                    )

                    drawPreviewCross(projected, state.scale, Color.Red)
                    return
                }

                if (state.projectionPhase == "pudorys_directed_segment_end") {
                    val start = state.segmentStartPudorys ?: return
                    val projectedEnd = projectPointOntoLineByPointAndDir(
                        p = cursorLogical,
                        linePoint = linePoint,
                        lineDir = lineDir
                    )

                    drawDashedPreviewSegmentPudorysAxo(
                        start = start,
                        cursorWorld = projectedEnd,
                        scale = state.scale,
                        color = Color.Red,
                        strokeWidth = 3f
                    )

                    drawPreviewCross(Offset(start.x, start.y), state.scale, Color.Red)
                    drawPreviewCross(projectedEnd, state.scale, Color.Red)
                    return
                }
            }
        }
    }

    if (state.segmentStartPudorys != null) {
        val point = state.segmentStartPudorys!!

        drawDashedPreviewSegmentPudorysAxo(
            start = point,
            cursorWorld = cursorLogical,
            scale = state.scale,
            color = Color.Red,
            strokeWidth = 3f,
        )

        drawPreviewCross(Offset(point.x, point.y), state.scale, Color.Red)
    }
}

private fun DrawScope.previewAxoPudorysSegmentOnLineCursor(
    state: MongeState,
    cursorLogical: Offset,
    visibleQuad: VisibleQuad?
) {
    val visible = visibleQuad ?: return

    when (state.projectionPhase) {
        "sol_pudorys_line_second" -> {
            val linePoint = state.pendingPoint1 ?: return
            drawDashedParallelLinePreviewPudorysAxo(
                through = Point3DPudorys(linePoint.x, linePoint.y, name = "?"),
                direction = cursorLogical - linePoint,
                visibleQuad = visible,
                scale = state.scale,
                color = Color.Gray,
                strokeWidth = 1.5f
            )
        }

        "sol_pudorys_segment_start" -> {
            val temp = state.tempLine ?: return
            val projected = projectPointOntoLineByPointAndDir(
                p = cursorLogical,
                linePoint = temp.point,
                lineDir = temp.direction
            )
            drawPreviewCross(projected, state.scale, Color.Red)
        }

        "sol_pudorys_segment_end" -> {
            val temp = state.tempLine ?: return
            val start = state.segmentStartPudorys ?: return
            val projectedEnd = projectPointOntoLineByPointAndDir(
                p = cursorLogical,
                linePoint = temp.point,
                lineDir = temp.direction
            )

            drawDashedPreviewSegmentPudorysAxo(
                start = start,
                cursorWorld = projectedEnd,
                scale = state.scale,
                color = Color.Red,
                strokeWidth = 3f
            )

            drawPreviewCross(Offset(start.x, start.y), state.scale, Color.Red)
            drawPreviewCross(projectedEnd, state.scale, Color.Red)
        }
    }
}
fun DrawScope.drawPreviewCross(
    center: Offset,
    scale: Float,
    color: Color = Color.Red
) {
    val invScale = if (scale > 1e-6f) 1f / scale else 1f
    val s = 10f * invScale
    val stroke = 2f * invScale

    drawLine(
        color = color,
        start = Offset(center.x - s, center.y),
        end = Offset(center.x + s, center.y),
        strokeWidth = stroke
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - s),
        end = Offset(center.x, center.y + s),
        strokeWidth = stroke
    )
}
