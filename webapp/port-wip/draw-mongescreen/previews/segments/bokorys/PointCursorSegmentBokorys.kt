package draw.mongescreen.previews.segments.bokorys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.axo.drawDashedPreviewSegmentBokorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewBokorysAxo
import draw.mongescreen.previews.segments.pudorys.drawPreviewCross
import model.ConstructionModifier
import model.Mongeobjects
import model.ProjectionType
import model.VisibleQuad
import model.axo.AxoMode
import model.classes.Point3DBokorys
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.resolveBokorysDirectionAxo
import monge.input.axo.segments.projectPointOntoLineByPointAndDir
import state.MongeState

fun DrawScope.previewAxoBokorysSegmentCursor(
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
        mode = AxoMode.AXO_BOKORYS
    ) ?: return

    if (state.drawobjects == Mongeobjects.SEGMENT_ON_LINE) {
        previewAxoBokorysSegmentOnLineCursor(state, cursorLogical, visibleQuad)
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

        val direction = resolveBokorysDirectionAxo(
            state = state,
            wantPerpendicular = isOrthogonal
        )

        when (state.projectionPhase) {
            "Bokorys_directed_segment_place_line" -> {
                if (direction != null) {
                    drawDashedParallelLinePreviewBokorysAxo(
                        through = Point3DBokorys(cursorLogical.x, cursorLogical.y, name = "?"),
                        direction = direction,
                        visibleQuad = visible,
                        scale = state.scale,
                        color = Color.Gray,
                        strokeWidth = 1.5f
                    )
                }
                return
            }

            "Bokorys_directed_segment_start",
            "Bokorys_directed_segment_end" -> {
                val linePoint = state.pendingPoint1 ?: return
                val lineDir = state.pendingDirection ?: direction ?: return

                drawDashedParallelLinePreviewBokorysAxo(
                    through = Point3DBokorys(linePoint.x, linePoint.y, name = "?"),
                    direction = lineDir,
                    visibleQuad = visible,
                    scale = state.scale,
                    color = Color.Gray,
                    strokeWidth = 1.5f
                )

                if (state.projectionPhase == "Bokorys_directed_segment_start") {
                    val projected = projectPointOntoLineByPointAndDir(
                        p = cursorLogical,
                        linePoint = linePoint,
                        lineDir = lineDir
                    )

                    drawPreviewCross(projected, state.scale, Color.Red)
                    return
                }

                if (state.projectionPhase == "Bokorys_directed_segment_end") {
                    val start = state.segmentStartBokorys ?: return
                    val projectedEnd = projectPointOntoLineByPointAndDir(
                        p = cursorLogical,
                        linePoint = linePoint,
                        lineDir = lineDir
                    )

                    drawDashedPreviewSegmentBokorysAxo(
                        start = start,
                        cursorWorld = projectedEnd,
                        scale = state.scale,
                        color = Color.Red,
                        strokeWidth = 3f
                    )

                    drawPreviewCross(Offset(start.y, start.z), state.scale, Color.Red)
                    drawPreviewCross(projectedEnd, state.scale, Color.Red)
                    return
                }
            }
        }
    }

    if (state.segmentStartBokorys != null) {
        val point = state.segmentStartBokorys!!

        drawDashedPreviewSegmentBokorysAxo(
            start = point,
            cursorWorld = cursorLogical,
            scale = state.scale,
            color = Color.Red,
            strokeWidth = 3f,
        )

        drawPreviewCross(Offset(point.y, point.z), state.scale, Color.Red)
    }
}

private fun DrawScope.previewAxoBokorysSegmentOnLineCursor(
    state: MongeState,
    cursorLogical: Offset,
    visibleQuad: VisibleQuad?
) {
    val visible = visibleQuad ?: return

    when (state.projectionPhase) {
        "sol_bokorys_line_second" -> {
            val linePoint = state.pendingPoint1 ?: return
            drawDashedParallelLinePreviewBokorysAxo(
                through = Point3DBokorys(linePoint.x, linePoint.y, name = "?"),
                direction = cursorLogical - linePoint,
                visibleQuad = visible,
                scale = state.scale,
                color = Color.Gray,
                strokeWidth = 1.5f
            )
        }

        "sol_bokorys_segment_start" -> {
            val temp = state.tempLine ?: return
            val projected = projectPointOntoLineByPointAndDir(
                p = cursorLogical,
                linePoint = temp.point,
                lineDir = temp.direction
            )
            drawPreviewCross(projected, state.scale, Color.Red)
        }

        "sol_bokorys_segment_end" -> {
            val temp = state.tempLine ?: return
            val start = state.segmentStartBokorys ?: return
            val projectedEnd = projectPointOntoLineByPointAndDir(
                p = cursorLogical,
                linePoint = temp.point,
                lineDir = temp.direction
            )

            drawDashedPreviewSegmentBokorysAxo(
                start = start,
                cursorWorld = projectedEnd,
                scale = state.scale,
                color = Color.Red,
                strokeWidth = 3f
            )

            drawPreviewCross(Offset(start.y, start.z), state.scale, Color.Red)
            drawPreviewCross(projectedEnd, state.scale, Color.Red)
        }
    }
}
