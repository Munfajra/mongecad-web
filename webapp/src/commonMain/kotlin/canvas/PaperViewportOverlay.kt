package canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import ui.mongeui.toolbar.PaperFormat

/*
 * Overlay výřezu výkresu – ztmaví plochu mimo zvolený formát papíru.
 * Na desktopu je uvnitř `canvas/AppMongeCanvas.kt`.
 */
fun DrawScope.drawPaperViewportOverlayAnchored(
    canvasSizePx: Size,
    logicalAnchor: Offset,
    logicalToScreen: (Offset) -> Offset,
    paper: PaperFormat,
    portrait: Boolean,
    unitsPerMm: Float = 1f,
    dimAlpha: Float = 0.45f,
    cornerRadiusPx: Float = 0f
) {
    val wMm = if (portrait) paper.wMm else paper.hMm
    val hMm = if (portrait) paper.hMm else paper.wMm
    val pxPerUnit = run {
        val a = logicalToScreen(Offset.Zero)
        val b = logicalToScreen(Offset(1f, 0f))
        (b - a).getDistance().coerceAtLeast(0.0001f)
    }
    val pxPerMmCurrent = pxPerUnit * unitsPerMm

    val paperW = wMm * pxPerMmCurrent
    val paperH = hMm * pxPerMmCurrent

    val anchorPx = logicalToScreen(logicalAnchor)

    val paperRect = Rect(
        left = anchorPx.x - paperW / 2f,
        top = anchorPx.y - paperH / 2f,
        right = anchorPx.x + paperW / 2f,
        bottom = anchorPx.y + paperH / 2f
    )

    val dim = Color.Black.copy(alpha = dimAlpha)

    val mask = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, canvasSizePx))
        addRoundRect(RoundRect(paperRect, CornerRadius(cornerRadiusPx, cornerRadiusPx)))
    }

    drawPath(mask, dim)

    drawRoundRect(
        color = Color.White.copy(alpha = 0.03f),
        topLeft = paperRect.topLeft,
        size = paperRect.size,
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
    )

    drawRoundRect(
        color = Color.White.copy(alpha = 0.90f),
        topLeft = paperRect.topLeft,
        size = paperRect.size,
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        style = Stroke(width = 1.2f)
    )
}

