package gl3d.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import draw.mongescreen.labels.RichLabelPart
import draw.mongescreen.labels.drawRichLabel
import draw.mongescreen.labels.measureRichLabelMetrics
import export.bitmapRenderer.RasterFormat
import export.bitmapRenderer.encodeImage
import export.bitmapRenderer.rgbaToImageBitmap
import export.bitmapRenderer.saveExportedImage
import gl3d.scene.Scene3DLabel

/**
 * Export snímku 3D scény do PNG – webový protějšek `opengl/ExportImg.kt`.
 *
 * Desktop kreslí do vlastního FBO a čte ho `glReadPixels`; tady je to totéž,
 * jen se offscreen cíl už používá kvůli OIT, takže stačí přečíst ten
 * (`SceneRenderer.render(captureRgba = true)`).
 *
 * Popisky nejsou v GL obraze – renderer žádný text neumí a sází je Compose nad
 * plátnem. Do exportu se proto dokreslí tímtéž Skia kódem jako na plátně, aby
 * hotový PNG odpovídal tomu, co uživatel vidí.
 */
fun exportScene3DImage(
    rgba: ByteArray,
    width: Int,
    height: Int,
    labels: List<Scene3DLabel>,
    labelFontPx: Float,
    fileName: String = "mongecad-3d.png",
): Boolean {
    if (width <= 0 || height <= 0 || rgba.size < width * height * 4) return false

    // GL má počátek vlevo dole, obrázek vlevo nahoře.
    val flipped = flipRows(rgba, width, height)
    val glImage = rgbaToImageBitmap(flipped, width, height) ?: return false

    val image = ImageBitmap(width, height, ImageBitmapConfig.Argb8888)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(image),
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        drawImage(glImage)
        for (label in labels) {
            val parts = listOf(RichLabelPart(label.text, label.superscript))
            val metrics = measureRichLabelMetrics(parts, labelFontPx)
            // Stejné umístění jako v náhledu (`SceneLabels`), včetně přidržení
            // popisků os u kraje – jinak by se export lišil od toho, co je vidět.
            val anchor = if (label.centered) {
                Offset(
                    (label.x - metrics.width / 2f)
                        .coerceIn(0f, (width - metrics.width).coerceAtLeast(0f)),
                    (label.y - (metrics.top + metrics.bottom) / 2f)
                        .coerceIn(-metrics.top, height - metrics.bottom),
                )
            } else {
                Offset(label.x, label.y - metrics.top)
            }
            drawRichLabel(
                parts = parts,
                anchor = anchor,
                color = label.color,
                baseFontPx = labelFontPx,
            )
        }
    }

    val bytes = encodeImage(image, RasterFormat.PNG)
    if (bytes.isEmpty()) return false
    saveExportedImage(bytes, fileName, RasterFormat.PNG)
    return true
}

private fun flipRows(rgba: ByteArray, width: Int, height: Int): ByteArray {
    val stride = width * 4
    val out = ByteArray(stride * height)
    for (row in 0 until height) {
        val src = (height - 1 - row) * stride
        rgba.copyInto(out, destinationOffset = row * stride, startIndex = src, endIndex = src + stride)
    }
    return out
}
