package export.bitmapRenderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import state.MongeState
import export.pdfRenderer.PdfPage
import export.pdfRenderer.orientedRect
import kotlin.math.ceil

/**
 * Vykreslení výkresu do bitmapy pro export.
 *
 * Desktopová `rasterGenerator.kt` staví na PDFBoxu (`PdfPage`, `PDRectangle`)
 * a kóduje přes AWT `BufferedImage` + ImageIO. Web nemá ani jedno, takže
 * rozměry stránky bere z [PaperFormat] a kódování řeší platforma
 * (viz [encodeImage] – na wasm přes Skia).
 *
 * Samotné kreslení scény je společné: `drawMongeSceneExport`.
 */
enum class RasterFormat { PNG, JPG }

private const val MM_PER_INCH = 25.4f
/**
 * Zeslabení čar při exportu – shodné s desktopem
 * (`export/bitmapRenderer/rasterGenerator.kt`). Bez něj vycházejí čáry
 * dvakrát tlustší, než odpovídá náhledu.
 */
const val EXPORT_STROKE_WIDTH_SCALE = 0.5f

fun generateRasterBytes(
    state: MongeState,
    page: PdfPage,
    dpi: Int,
    marginMm: Float,
    scale: Float,
    offset: Offset,
    portrait: Boolean,
    previewContentPx: Size,
    showHelperConstructions: Boolean,
    showObjectLabels: Boolean,
    format: RasterFormat,
    jpegQuality: Float = 0.92f,
    logicalStrokeScale: Float = 1f
): ByteArray {
    val pageRect = orientedRect(page.toPDRectangle(portrait), portrait)

    val pxPerPt = dpi / 72f
    val marginPt = marginMm / MM_PER_INCH * 72f

    val pageWpx = ceil(pageRect.width * pxPerPt).toInt().coerceAtLeast(64)
    val pageHpx = ceil(pageRect.height * pxPerPt).toInt().coerceAtLeast(64)

    val marginPx = ceil(marginPt * pxPerPt)
    val drawWpx = ceil((pageRect.width - 2f * marginPt) * pxPerPt).toInt().coerceAtLeast(1)
    val drawHpx = ceil((pageRect.height - 2f * marginPt) * pxPerPt).toInt().coerceAtLeast(1)

    // Stejné škálování jako na desktopu – podle šířky náhledu.
    val safePreviewW = previewContentPx.width.coerceAtLeast(1f)
    val safePreviewH = previewContentPx.height.coerceAtLeast(1f)
    val k = minOf(drawWpx / safePreviewW, drawHpx / safePreviewH)
    val exportScale = scale * k
    val exportOffset = Offset(offset.x * k, offset.y * k)

    val img = ImageBitmap(pageWpx, pageHpx, ImageBitmapConfig.Argb8888)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(img),
        size = Size(pageWpx.toFloat(), pageHpx.toFloat())
    ) {
        // stránka – bílé pozadí
        drawRect(Color.White, size = size)

        withTransform({ translate(left = marginPx, top = marginPx) }) {
            clipRect(0f, 0f, drawWpx.toFloat(), drawHpx.toFloat()) {
                drawMongeSceneExport(
                    state = state,
                    scale = exportScale,
                    offset = exportOffset,
                    background = Color.Transparent,
                    pxFactor = k,
                    strokePxFactor = k * logicalStrokeScale * EXPORT_STROKE_WIDTH_SCALE,
                    pointMarkerPxFactor = 1f,
                    drawLabels = showObjectLabels,
                    drawHelpers = showHelperConstructions,
                    x12RightEdgePx = drawWpx.toFloat()
                )
            }

            // rámeček kolem kreslicí oblasti
            val borderPx = 1f
            val inset = borderPx / 2f
            drawRect(
                color = Color.Black,
                topLeft = Offset(inset, inset),
                size = Size(drawWpx - borderPx, drawHpx - borderPx),
                style = Stroke(width = borderPx),
            )
        }
    }
    return encodeImage(img, format, jpegQuality)
}

/** Zakódování bitmapy do PNG/JPG – implementaci dodává platforma. */
expect fun encodeImage(image: ImageBitmap, format: RasterFormat, jpegQuality: Float = 0.92f): ByteArray

/** Nabídne hotový soubor ke stažení / uložení. */
expect fun saveExportedImage(bytes: ByteArray, fileName: String, format: RasterFormat)

/**
 * Bitmapa ze syrových RGBA8 pixelů (řádky shora dolů). Používá ji export 3D
 * scény, kde snímek přichází z `glReadPixels`; `ImageBitmap` samo žádné
 * společné API na zápis pixelů nemá.
 */
expect fun rgbaToImageBitmap(rgba: ByteArray, width: Int, height: Int): ImageBitmap?
