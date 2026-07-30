package export.bitmapRenderer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.browser.document
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.khronos.webgl.Int8Array
import org.khronos.webgl.set
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/**
 * Kódování bitmapy přes Skia – desktop na to používá AWT `ImageIO`,
 * ten v prohlížeči není. Skiko je součástí Compose, takže nic dalšího
 * není potřeba.
 */
actual fun encodeImage(image: ImageBitmap, format: RasterFormat, jpegQuality: Float): ByteArray {
    val skiaImage = Image.makeFromBitmap(image.asSkiaBitmap())
    val encoded = when (format) {
        RasterFormat.PNG -> skiaImage.encodeToData(EncodedImageFormat.PNG)
        // Skia bere kvalitu JPEG v procentech (0–100).
        RasterFormat.JPG -> skiaImage.encodeToData(
            EncodedImageFormat.JPEG,
            (jpegQuality * 100f).toInt().coerceIn(1, 100)
        )
    }
    return encoded?.bytes ?: ByteArray(0)
}

/** Uložení = stažení souboru prohlížečem. */
actual fun saveExportedImage(bytes: ByteArray, fileName: String, format: RasterFormat) {
    val mime = when (format) {
        RasterFormat.PNG -> "image/png"
        RasterFormat.JPG -> "image/jpeg"
    }

    val parts = JsArray<JsAny?>()
    parts[0] = bytes.toInt8Array()
    val blob = Blob(parts, BlobPropertyBag(type = mime))
    val url = URL.createObjectURL(blob)

    val a = document.createElement("a") as HTMLAnchorElement
    a.href = url
    a.download = fileName
    a.style.display = "none"
    document.body?.appendChild(a)
    a.click()
    document.body?.removeChild(a)
    URL.revokeObjectURL(url)
}

private fun ByteArray.toInt8Array(): Int8Array {
    val out = Int8Array(size)
    for (i in indices) out.set(i, this[i])
    return out
}

/**
 * Bitmapa ze syrových pixelů přes Skia. `Image.makeRaster` bere data tak, jak
 * jsou – `glReadPixels` je dodává jako neprednásobené RGBA, proto
 * `UNPREMUL`; s `PREMUL` by poloprůhledné pixely vyšly moc světlé.
 */
actual fun rgbaToImageBitmap(rgba: ByteArray, width: Int, height: Int): ImageBitmap? {
    if (width <= 0 || height <= 0 || rgba.size < width * height * 4) return null
    val info = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.RGBA_8888,
        alphaType = ColorAlphaType.UNPREMUL,
    )
    return runCatching {
        Image.makeRaster(info, rgba, width * 4).toComposeImageBitmap()
    }.getOrNull()
}
