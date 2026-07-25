package draw.mongescreen.previews.segments.segmentcomplete

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.axo.drawAOSegmentOnScreen
import draw.mongescreen.previews.segments.AO.drawAOPreviewCross
import draw.mongescreen.previews.segments.pudorys.drawPreviewCross
import model.LineStyle
import model.axo.AxoMode
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.linecomplete.ProjectionKind
import monge.input.axo.lines.linecomplete.projectionKindFromAxoMode
import monge.input.axo.lines.linecomplete.resolveCompletionSecondDirection
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.axo.segments.segmentcomplete.computeAxoSegmentCompletionTempLine
import state.MongeState
import kotlin.math.sqrt

private fun MongeState.fixedSegmentCompletionPoint(kind: ProjectionKind): androidx.compose.ui.geometry.Offset? {
    if (projectionPhase != "axo_complete_segment_second_projection") return null
    val pending = pendingAxoSegmentCompletion ?: return null
    if ((pending.secondKind ?: projectionKindFromAxoMode(axoMode)) != kind) return null
    return completingSegmentSecondStart
}

fun DrawScope.drawSegmentCompletionPreviewPudorys(state: MongeState) {
    drawSegmentCompletionSourceGuide(state, ProjectionKind.PUDORYS)
    if (state.axoMode != AxoMode.AXO_PUDORYS) return
    drawSegmentSecondPointPreviewSegment(state, ProjectionKind.PUDORYS)
    val p = state.fixedSegmentCompletionPoint(ProjectionKind.PUDORYS) ?: return
    drawPreviewCross(center = p, scale = state.scale, color = Color.Red)
}

fun DrawScope.drawSegmentCompletionPreviewNarys(state: MongeState) {
    drawSegmentCompletionSourceGuide(state, ProjectionKind.NARYS)
    if (state.axoMode != AxoMode.AXO_NARYS) return
    drawSegmentSecondPointPreviewSegment(state, ProjectionKind.NARYS)
    val p = state.fixedSegmentCompletionPoint(ProjectionKind.NARYS) ?: return
    drawPreviewCross(center = p, scale = state.scale, color = Color.Red)
}

fun DrawScope.drawSegmentCompletionPreviewBokorys(state: MongeState) {
    drawSegmentCompletionSourceGuide(state, ProjectionKind.BOKORYS)
    if (state.axoMode != AxoMode.AXO_BOKORYS) return
    drawSegmentSecondPointPreviewSegment(state, ProjectionKind.BOKORYS)
    val p = state.fixedSegmentCompletionPoint(ProjectionKind.BOKORYS) ?: return
    drawPreviewCross(center = p, scale = state.scale, color = Color.Red)
}

fun DrawScope.drawSegmentCompletionPreviewAxoOverlay(state: MongeState) {
    if (state.axoMode != AxoMode.NORMAL_2D) return
    drawSegmentSecondPointPreviewSegment(state, ProjectionKind.AXO)
    val p = state.fixedSegmentCompletionPoint(ProjectionKind.AXO) ?: return
    val b = state.basis ?: return
    drawAOPreviewCross(local = p, state = state, basis = b, color = Color.Red)
}

private fun DrawScope.drawSegmentCompletionSourceGuide(
    state: MongeState,
    plane: ProjectionKind
) {
    val pending = state.pendingAxoSegmentCompletion ?: return
    val currentKind = projectionKindFromAxoMode(state.axoMode) ?: return
    val secondKind = pending.secondKind ?: currentKind
    val endpoint = if (state.completingSegmentSecondStart == null) 0 else 1
    if (pending.firstKind != plane) return
    if (secondKind == plane) return

    when (plane) {
        ProjectionKind.PUDORYS -> {
            val seg = state.segmentsPudorys.firstOrNull { it.id == pending.firstProjectionId } ?: return
            val p = if (endpoint == 0) seg.start else seg.end
            val axisPoint = when (secondKind) {
                ProjectionKind.NARYS -> androidx.compose.ui.geometry.Offset(p.x, 0f)
                ProjectionKind.BOKORYS -> androidx.compose.ui.geometry.Offset(0f, p.y)
                else -> return
            }
            drawDashedLocal(p.toOffset(), axisPoint, state.scale, Color.Gray)
        }
        ProjectionKind.NARYS -> {
            val seg = state.segmentsNarys.firstOrNull { it.id == pending.firstProjectionId } ?: return
            val p = if (endpoint == 0) seg.start else seg.end
            val axisPoint = when (secondKind) {
                ProjectionKind.PUDORYS -> androidx.compose.ui.geometry.Offset(p.x, 0f)
                ProjectionKind.BOKORYS -> androidx.compose.ui.geometry.Offset(0f, p.z)
                else -> return
            }
            drawDashedLocal(p.toOffsetN(), axisPoint, state.scale, Color.Gray)
        }
        ProjectionKind.BOKORYS -> {
            val seg = state.segmentsBokorys.firstOrNull { it.id == pending.firstProjectionId } ?: return
            val p = if (endpoint == 0) seg.start else seg.end
            val axisPoint = when (secondKind) {
                ProjectionKind.PUDORYS -> androidx.compose.ui.geometry.Offset(p.y, 0f)
                ProjectionKind.NARYS -> androidx.compose.ui.geometry.Offset(0f, p.z)
                else -> return
            }
            drawDashedLocal(p.toOffsetB(), axisPoint, state.scale, Color.Gray)
        }
        ProjectionKind.AXO -> {}
    }
}

private fun DrawScope.drawSegmentSecondPointPreviewSegment(
    state: MongeState,
    kind: ProjectionKind
) {
    if (state.projectionPhase != "axo_complete_segment_second_projection") return
    val pending = state.pendingAxoSegmentCompletion ?: return
    val currentKind = projectionKindFromAxoMode(state.axoMode) ?: return
    val secondKind = pending.secondKind ?: currentKind
    if (secondKind != kind) return
    val start = state.completingSegmentSecondStart

    val mode = when (kind) {
        ProjectionKind.PUDORYS -> AxoMode.AXO_PUDORYS
        ProjectionKind.NARYS -> AxoMode.AXO_NARYS
        ProjectionKind.BOKORYS -> AxoMode.AXO_BOKORYS
        ProjectionKind.AXO -> AxoMode.NORMAL_2D
    }

    val raw = if (kind == ProjectionKind.AXO) {
        val basis = state.basis ?: return
        state.snappedPointLogical ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
    } else {
        getLogicalCursorAxo(
            snapped = state.snappedPointLogical,
            cursor = state.cursorPosition,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            flipX = false,
            flipY = false,
            mode = mode,
            axoModel = state.activeAxoModel
        ) ?: return
    }

    if (start == null) {
        val canDirectedPreview =
            (state.constructionModifier == model.ConstructionModifier.PARALLEL ||
                    state.constructionModifier == model.ConstructionModifier.ORTHOGONAL) &&
                    hasOverlayReference(state)
        if (!canDirectedPreview) return

        val dir = resolveCompletionSecondDirection(state, currentKind, state.constructionModifier) ?: return
        val firstTemp = state.tempLine ?: return
        val previewStart = projectOnLine(raw, firstTemp.point, firstTemp.direction)
        val secondTemp = computeAxoSegmentCompletionTempLine(state, pending, secondKind, endpoint = 1) ?: return
        val previewEnd = intersectInfiniteLines(previewStart, dir, secondTemp.point, secondTemp.direction) ?: return

        if (kind == ProjectionKind.AXO) {
            drawAOSegmentOnScreen(
                state = state,
                startLocal = previewStart,
                endLocal = previewEnd,
                color = Color.Red,
                lineWidth = 3f,
                lineStyle = LineStyle.Solid,
                pxPerPt = 1f
            )
            val b = state.basis ?: return
            drawAOPreviewCross(previewStart, state, b, Color.Red)
            drawAOPreviewCross(previewEnd, state, b, Color.Red)
        } else {
            drawLine(
                color = Color.Red,
                start = previewStart,
                end = previewEnd,
                strokeWidth = 2f / state.scale,
                alpha = 0.95f
            )
            drawPreviewCross(center = previewStart, scale = state.scale, color = Color.Red)
            drawPreviewCross(center = previewEnd, scale = state.scale, color = Color.Red)
        }
        return
    }

    val temp = state.tempLine ?: return
    val projected = projectOnLine(raw, temp.point, temp.direction)

    if (kind == ProjectionKind.AXO) {
        drawAOSegmentOnScreen(
            state = state,
            startLocal = start,
            endLocal = projected,
            color = Color.Red,
            lineWidth = 3f,
            lineStyle = LineStyle.Solid,
            pxPerPt = 1f
        )
    } else {
        drawLine(
            color = Color.Red,
            start = start,
            end = projected,
            strokeWidth = 2f / state.scale,
            alpha = 0.95f
        )
    }
}

private fun intersectInfiniteLines(
    p1: androidx.compose.ui.geometry.Offset,
    d1: androidx.compose.ui.geometry.Offset,
    p2: androidx.compose.ui.geometry.Offset,
    d2: androidx.compose.ui.geometry.Offset
): androidx.compose.ui.geometry.Offset? {
    val det = d1.x * d2.y - d1.y * d2.x
    if (kotlin.math.abs(det) < 1e-6f) return null
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    val t = (dx * d2.y - dy * d2.x) / det
    return p1 + d1 * t
}

private fun projectOnLine(
    p: androidx.compose.ui.geometry.Offset,
    linePoint: androidx.compose.ui.geometry.Offset,
    lineDir: androidx.compose.ui.geometry.Offset
): androidx.compose.ui.geometry.Offset {
    val len2 = lineDir.x * lineDir.x + lineDir.y * lineDir.y
    if (len2 < 1e-6f) return p
    val v = p - linePoint
    val t = (v.x * lineDir.x + v.y * lineDir.y) / len2
    return linePoint + lineDir * t
}

private fun DrawScope.drawDashedLocal(
    a: androidx.compose.ui.geometry.Offset,
    b: androidx.compose.ui.geometry.Offset,
    scale: Float,
    color: Color
) {
    val d = b - a
    val len = sqrt(d.x * d.x + d.y * d.y)
    if (len < 1e-6f) return
    drawLine(
        color = color,
        start = a,
        end = b,
        strokeWidth = 1f / scale,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f / scale, 6f / scale), 0f),
        alpha = 0.8f
    )
}

private fun model.classes.Point3DPudorys.toOffset() = androidx.compose.ui.geometry.Offset(x, y)
private fun model.classes.Point3DNarys.toOffsetN() = androidx.compose.ui.geometry.Offset(x, z)
private fun model.classes.Point3DBokorys.toOffsetB() = androidx.compose.ui.geometry.Offset(y, z)
