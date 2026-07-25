package draw.mongescreen.previews.lines

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.axo.drawAOSegmentOnScreen
import draw.mongescreen.objects.axo.drawDashedPreviewSegmentBokorysAxo
import draw.mongescreen.objects.axo.drawDashedPreviewSegmentNarysAxo
import draw.mongescreen.objects.axo.drawDashedPreviewSegmentPudorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewPudorys
import draw.mongescreen.previews.segments.AO.drawAOPreviewCross
import draw.mongescreen.previews.segments.pudorys.drawPreviewCross
import model.LineStyle
import model.classes.LineTrimPickView
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.lines.CustomLineTrimPreview
import monge.input.lines.customLineTrimPreview
import state.MongeState
import utils.toScreenOld

private val trimPreviewColor = Color.Red
private val trimTargetColor = Color(0xFFFF3B30).copy(alpha = 0.55f)

fun DrawScope.drawCustomLineTrimPreviewPudorys(state: MongeState) {
    val preview = customLineTrimPreview(state) ?: return
    if (preview.view != LineTrimPickView.PUDORYS) return

    drawDashedParallelLinePreviewPudorys(
        through = Point3DPudorys(preview.linePoint.x, preview.linePoint.y, name = ""),
        direction = preview.lineDir,
        scale = state.scale,
        canvasOffset = state.canvasOffset,
        dashLength = 14f,
        gapLength = 8f,
        color = trimTargetColor,
        strokeWidth = 2.5f,
        clipToBelowX12 = true
    )

    preview.firstPoint?.let { first ->
        drawDashedSegmentPreviewPudorys(
            start = Point3DPudorys(first.x, first.y, name = ""),
            cursorWorld = preview.cursorPoint,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            dashLength = 1f,
            gapLength = 0f,
            color = trimPreviewColor,
            strokeWidth = 3f,
            alpha = 0.9f
        )
        drawScreenPreviewCross(first.toScreenOld(state.scale, state.canvasOffset))
    }

    drawScreenPreviewCross(preview.cursorPoint.toScreenOld(state.scale, state.canvasOffset))
}

fun DrawScope.drawCustomLineTrimPreviewNarys(state: MongeState) {
    val preview = customLineTrimPreview(state) ?: return
    if (preview.view != LineTrimPickView.NARYS) return

    drawDashedParallelLinePreviewNarys(
        through = Point3DNarys(preview.linePoint.x, preview.linePoint.y, name = ""),
        direction = preview.lineDir,
        scale = state.scale,
        canvasOffset = state.canvasOffset,
        dashLength = 14f,
        gapLength = 8f,
        strokeWidth = 2.5f,
        color = trimTargetColor,
        clipToAboveZ = true
    )

    preview.firstPoint?.let { first ->
        drawDashedSegmentPreviewNarys(
            start = Point3DNarys(first.x, first.y, name = ""),
            cursorWorld = Offset(preview.cursorPoint.x, -preview.cursorPoint.y),
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            dashLength = 1f,
            gapLength = 0f,
            color = trimPreviewColor,
            strokeWidth = 3f,
            alpha = 0.9f
        )
        drawScreenPreviewCross(Offset(first.x, -first.y).toScreenOld(state.scale, state.canvasOffset))
    }

    drawScreenPreviewCross(Offset(preview.cursorPoint.x, -preview.cursorPoint.y).toScreenOld(state.scale, state.canvasOffset))
}

fun DrawScope.drawCustomLineTrimPreviewPudorysAxo(state: MongeState) {
    val preview = customLineTrimPreview(state) ?: return
    if (preview.view != LineTrimPickView.PUDORYS) return

    extendedTrimLine(preview, state)?.let { (a, b) ->
        drawDashedPreviewSegmentPudorysAxo(
            start = Point3DPudorys(a.x, a.y, name = ""),
            cursorWorld = b,
            color = trimTargetColor,
            strokeWidth = 2.5f,
            scale = state.scale,
            style = LineStyle.Solid
        )
    }

    preview.firstPoint?.let { first ->
        drawDashedPreviewSegmentPudorysAxo(
            start = Point3DPudorys(first.x, first.y, name = ""),
            cursorWorld = preview.cursorPoint,
            color = trimPreviewColor,
            strokeWidth = 3f,
            scale = state.scale,
            style = LineStyle.Solid
        )
        drawPreviewCross(first, state.scale, trimPreviewColor)
    }
    drawPreviewCross(preview.cursorPoint, state.scale, trimPreviewColor)
}

fun DrawScope.drawCustomLineTrimPreviewNarysAxo(state: MongeState) {
    val preview = customLineTrimPreview(state) ?: return
    if (preview.view != LineTrimPickView.NARYS) return

    extendedTrimLine(preview, state)?.let { (a, b) ->
        drawDashedPreviewSegmentNarysAxo(
            start = Point3DNarys(a.x, a.y, name = ""),
            cursorWorld = b,
            color = trimTargetColor,
            strokeWidth = 2.5f,
            scale = state.scale,
            style = LineStyle.Solid
        )
    }

    preview.firstPoint?.let { first ->
        drawDashedPreviewSegmentNarysAxo(
            start = Point3DNarys(first.x, first.y, name = ""),
            cursorWorld = preview.cursorPoint,
            color = trimPreviewColor,
            strokeWidth = 3f,
            scale = state.scale,
            style = LineStyle.Solid
        )
        drawPreviewCross(first, state.scale, trimPreviewColor)
    }
    drawPreviewCross(preview.cursorPoint, state.scale, trimPreviewColor)
}

fun DrawScope.drawCustomLineTrimPreviewBokorysAxo(state: MongeState) {
    val preview = customLineTrimPreview(state) ?: return
    if (preview.view != LineTrimPickView.BOKORYS) return

    extendedTrimLine(preview, state)?.let { (a, b) ->
        drawDashedPreviewSegmentBokorysAxo(
            start = Point3DBokorys(a.x, a.y, name = ""),
            cursorWorld = b,
            color = trimTargetColor,
            strokeWidth = 2.5f,
            scale = state.scale,
            style = LineStyle.Solid
        )
    }

    preview.firstPoint?.let { first ->
        drawDashedPreviewSegmentBokorysAxo(
            start = Point3DBokorys(first.x, first.y, name = ""),
            cursorWorld = preview.cursorPoint,
            color = trimPreviewColor,
            strokeWidth = 3f,
            scale = state.scale,
            style = LineStyle.Solid
        )
        drawPreviewCross(first, state.scale, trimPreviewColor)
    }
    drawPreviewCross(preview.cursorPoint, state.scale, trimPreviewColor)
}

fun DrawScope.drawCustomLineTrimPreviewAxoOverlay(state: MongeState) {
    val preview = customLineTrimPreview(state) ?: return
    if (preview.view != LineTrimPickView.AXO) return
    val basis = state.basis ?: return

    extendedTrimLine(preview, state)?.let { (a, b) ->
        drawAOSegmentOnScreen(
            state = state,
            startLocal = a,
            endLocal = b,
            color = trimTargetColor,
            lineWidth = 2.5f,
            lineStyle = LineStyle.Solid,
            pxPerPt = 1f
        )
    }

    preview.firstPoint?.let { first ->
        drawAOSegmentOnScreen(
            state = state,
            startLocal = first,
            endLocal = preview.cursorPoint,
            color = trimPreviewColor,
            lineWidth = 3f,
            lineStyle = LineStyle.Solid,
            pxPerPt = 1f
        )
        drawAOPreviewCross(first, state, basis, trimPreviewColor)
    }
    drawAOPreviewCross(preview.cursorPoint, state, basis, trimPreviewColor)
}

private fun DrawScope.extendedTrimLine(
    preview: CustomLineTrimPreview,
    state: MongeState
): Pair<Offset, Offset>? {
    val len = preview.lineDir.getDistance()
    if (len < 1e-6f) return null
    val unit = preview.lineDir / len
    val scale = state.scale.takeIf { it > 1e-6f } ?: 1f
    val extent = (maxOf(size.width, size.height) / scale * 3f).coerceAtLeast(500f)
    return preview.linePoint - unit * extent to preview.linePoint + unit * extent
}

private fun DrawScope.drawScreenPreviewCross(center: Offset) {
    val half = 10f
    val stroke = 2f
    drawLine(
        color = trimPreviewColor,
        start = center.copy(x = center.x - half),
        end = center.copy(x = center.x + half),
        strokeWidth = stroke
    )
    drawLine(
        color = trimPreviewColor,
        start = center.copy(y = center.y - half),
        end = center.copy(y = center.y + half),
        strokeWidth = stroke
    )
}
