package ui.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import draw.mongescreen.labels.preloadLabelFonts
import mongecad.web.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.compose.resources.decodeToSvgPainter

/**
 * Webový protějšek desktopového `ui/resources/ClasspathPainterResource.kt`.
 *
 * Signatura `painterResource(String)` je záměrně identická, aby portovaná UI
 * volala ikony úplně stejně jako desktop ("icons/point.svg"). Liší se jen
 * načtení bajtů: desktop je bere z classpath synchronně, web je musí stáhnout
 * asynchronně, takže tu je sdílená cache a ikona doskočí po načtení.
 */
private val resourceBytes = mutableStateMapOf<String, ByteArray>()
private val requested = mutableSetOf<String>()

/** Ikony viditelné hned po startu – přednačteme, ať v liště nic neproblikne. */
private val eagerResources = listOf(
    "icons/point.svg", "icons/pointDark.svg",
    "icons/primka.svg", "icons/primkaDark.svg",
    "icons/usecka.svg", "icons/useckaDark.svg",
    "icons/usecka_naprimce.svg", "icons/usecka_naprimceDark.svg",
    "icons/rovina.svg", "icons/rovinaDark.svg",
    "icons/curves.svg", "icons/curvesDark.svg",
    "icons/kuzelosecka.svg", "icons/kuzeloseckaDark.svg",
    "icons/parallel.svg", "icons/orthogonal.svg",
    "icons/pudorys.png", "icons/NÁRYS.png",
    "icons/cursor.svg", "icons/eraser.svg",
    // úvodní obrazovka
    "icons/ikonaMC.svg", "icons/mongeM.svg", "icons/geometry.svg"
)

suspend fun preloadCoreResources() {
    eagerResources.forEach { loadResource(it) }
    preloadLabelFonts()
}

private suspend fun loadResource(resourcePath: String) {
    if (resourceBytes.containsKey(resourcePath)) return
    val bytes = runCatching { Res.readBytes("files/$resourcePath") }.getOrNull() ?: return
    resourceBytes[resourcePath] = bytes
}

@Composable
fun painterResource(resourcePath: String): Painter {
    val bytes = resourceBytes[resourcePath]
    if (bytes == null) {
        LaunchedEffect(resourcePath) {
            if (requested.add(resourcePath)) loadResource(resourcePath)
        }
        return EmptyPainter
    }
    return when (resourcePath.substringAfterLast('.').lowercase()) {
        "svg" -> rememberSvgResource(resourcePath, bytes)
        "xml" -> rememberVectorXmlResource(resourcePath, bytes)
        else -> rememberBitmapResource(resourcePath, bytes)
    }
}

@Composable
private fun rememberBitmapResource(resourcePath: String, bytes: ByteArray): Painter =
    remember(resourcePath) {
        // Výchozí FilterQuality.Low škáluje bez vyhlazení – bitmapové ikony
        // pak mají zubaté hrany, nejvíc znát na velké předloze (256 px).
        BitmapPainter(bytes.decodeToImageBitmap(), filterQuality = FilterQuality.High)
    }

@Composable
private fun rememberVectorXmlResource(resourcePath: String, bytes: ByteArray): Painter {
    val density = LocalDensity.current
    val imageVector = remember(resourcePath, density) { bytes.decodeToImageVector(density) }
    return rememberVectorPainter(imageVector)
}

@Composable
private fun rememberSvgResource(resourcePath: String, bytes: ByteArray): Painter {
    val density = LocalDensity.current
    return remember(resourcePath, density) { bytes.decodeToSvgPainter(density) }
}

/** Placeholder, dokud se bajty ikony nedonačtou – drží layout, nekreslí nic. */
private object EmptyPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified
    override fun DrawScope.onDraw() = Unit
}
