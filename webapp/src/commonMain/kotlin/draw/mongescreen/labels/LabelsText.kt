package draw.mongescreen.labels

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import model.ProjectionMode
import model.runtimeDrawColor
import mongecad.web.generated.resources.Res
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import state.MongeState
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import org.jetbrains.skia.paragraph.TextStyle as SkTextStyle

/**
 * Textové jádro popisků – protějšek desktopové `LabelsExport.kt` bez PDF větve.
 * Kreslení na canvas jede přes Skia paragraph API, které Skiko na wasm má.
 *
 * Jediný reálný rozdíl proti desktopu: fonty se z classpath nedají načíst
 * synchronně, takže se musí přednačíst přes [preloadLabelFonts]. Než doběhnou,
 * kreslí se systémovým fontem.
 */
private const val FONT_ITALIC = "lmroman10-italic.otf"
private const val FONT_GREEK = "latinmodern-math.otf"
private const val FONT_REGULAR = "lmroman10-regular.otf"

/**
 * Záložní rodina pro znaky, které hlavní fonty nemají – dolní indexy
 * průmětů (₀₁₂₃ₐ), horní indexy a řecká písmena.
 *
 * Na desktopu je doplní systémový fallback přes FontMgr.default, ale
 * v prohlížeči žádné systémové fonty nejsou, takže by se vykreslily
 * jako prázdné čtverečky. Rodina se proto uvádí ve `fontFamilies`
 * jako druhá v pořadí – Skia ji použije jen pro chybějící glyfy.
 *
 * Konkrétně Noto Sans, protože to je i to, co jako fallback vybírá
 * fontconfig na Linuxu, takže indexy sedí s desktopem. Pozor na záměnu
 * za DejaVu Sans: ten má dolní indexy navržené na účaří (0…42 % em)
 * místo spuštěné pod něj (−13…31 % em), takže vypadají jako zmenšené
 * písmeno vedle názvu, ne jako index.
 */
private const val FONT_FALLBACK = "NotoSans-Regular.ttf"
const val LABEL_FALLBACK_FAMILY = "fallback"

private var fontCollection: FontCollection? = null

/** Zavolej jednou při startu, ještě před prvním vykreslením popisků. */
suspend fun preloadLabelFonts() {
    if (fontCollection != null) return

    val provider = TypefaceFontProvider()
    var registered = 0

    suspend fun register(path: String, family: String) {
        val bytes = runCatching { Res.readBytes("font/$path") }.getOrNull() ?: return
        val typeface = runCatching { FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) }
            .getOrNull() ?: return
        provider.registerTypeface(typeface, family)
        registered++
    }

    register(FONT_ITALIC, "italic")
    register(FONT_GREEK, "greek")
    register(FONT_REGULAR, "regular")
    register(FONT_FALLBACK, LABEL_FALLBACK_FAMILY)

    fontCollection = FontCollection().apply {
        setDefaultFontManager(FontMgr.default)
        if (registered > 0) setAssetFontManager(provider)
    }
}

val fontCollectionWithAssets: FontCollection
    get() = fontCollection ?: FontCollection().apply {
        setDefaultFontManager(FontMgr.default)
    }.also { fontCollection = it }

/** Požadovaná rodina + záložní, aby chybějící glyfy nezmizely. */
private fun familiesWithFallback(family: String?): Array<String> =
    if (family == null) arrayOf(LABEL_FALLBACK_FAMILY)
    else arrayOf(family, LABEL_FALLBACK_FAMILY)

fun baselineToTopShiftPx(paragraphHeight: Float): Float {
    // typicky ascent ~ 0.78–0.85 výšky; 0.8 je dobrý default
    return paragraphHeight * 0.8f
}

fun measureSkiaParagraph(text: String, fontPx: Float, family: String?): Size {
    val ts = SkTextStyle().apply {
        fontSize = fontPx
        fontFamilies = familiesWithFallback(family)
    }
    val builder = ParagraphBuilder(ParagraphStyle(), fontCollectionWithAssets)
    builder.pushStyle(ts)
    builder.addText(text)
    val p = builder.build()
    p.layout(Float.POSITIVE_INFINITY)
    return Size(p.maxIntrinsicWidth, p.height)
}

private var runtimeLabelColors = false

fun DrawScope.drawSkiaText(
    text: String,
    anchor: Offset,
    color: Color,
    fontPx: Float = 14f,
    typefaceFamily: String? = "italic"
): Float {
    val drawColor = color.runtimeDrawColor(runtimeLabelColors)
    val ts = SkTextStyle().apply {
        this.color = drawColor.toArgb()
        fontSize = fontPx
        fontFamilies = familiesWithFallback(typefaceFamily)
    }

    val builder = ParagraphBuilder(ParagraphStyle(), fontCollectionWithAssets)
    builder.pushStyle(ts)
    builder.addText(text)

    val paragraph = builder.build()
    paragraph.layout(Float.POSITIVE_INFINITY)

    val shiftY = baselineToTopShiftPx(paragraph.height)

    val skCanvas = drawContext.canvas.nativeCanvas
    skCanvas.save()
    skCanvas.translate(anchor.x, anchor.y - shiftY)
    paragraph.paint(skCanvas, 0f, 0f)
    skCanvas.restore()

    return paragraph.maxIntrinsicWidth
}

fun scaledOffset(base: Offset, f: Float) = Offset(base.x * f, base.y * f)

fun exportLabelScale(state: MongeState, exportScale: Float, pxFactor: Float): Float {
    if (!SettingsManager.current.scaleLabelsWithCanvas) return 1f

    val anchor = state.labelScaleAnchorPudorys ?: 1f
    val viewScale = exportScale / pxFactor     // odpovídá preview zoomu
    return viewScale / anchor
}

fun flipIfPlane(y: Float, flipY: Boolean, canvasHeight: Float) =
    if (flipY) canvasHeight - y else y

/** Popisky kreslené přímo do DrawScope; používá je plátno i bitmapový export. */
fun DrawScope.drawLabels(
    state: MongeState,
    scale: Float,
    offset: Offset,
    pxFactor: Float,
    drawHelpers: Boolean,
    runtimeColors: Boolean = false,
    x12RightEdgePx: Float? = null
) {
    val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * pxFactor
    val previous = runtimeLabelColors
    runtimeLabelColors = runtimeColors
    try {
        when (state.projectionMode) {
            ProjectionMode.MONGE -> {
                drawPointPudorysLabelsExport(state, scale, offset, fontPx, pxFactor)
                drawPointNarysLabelsExport(state, scale, offset, fontPx, pxFactor)
                drawLinePudorysLabelsExport(state, scale, offset, x12RightEdgePx ?: size.width, fontPx, pxFactor)
                drawLineNarysLabelsExport(state, scale, offset, fontPx, pxFactor)
                drawSegmentPudorysLabelsExport(state, scale, offset, fontPx, pxFactor, includeHelp = drawHelpers)
                drawSegmentNarysLabelsExport(state, scale, offset, fontPx, pxFactor, includeHelp = drawHelpers)
                drawAidPointLabelsExport(state, scale, offset, fontPx, pxFactor, show = drawHelpers)
            }

            ProjectionMode.PLANE, ProjectionMode.KOTO -> {
                drawPointPudorysLabelsExport(state, scale, offset, fontPx, pxFactor)
                drawLinePudorysLabelsExport(state, scale, offset, x12RightEdgePx ?: size.width, fontPx, pxFactor)
                drawSegmentPudorysLabelsExport(state, scale, offset, fontPx, pxFactor, includeHelp = drawHelpers)
                drawAidPointLabelsExport(state, scale, offset, fontPx, pxFactor, show = drawHelpers)
            }

            ProjectionMode.AXO -> Unit
        }
    } finally {
        runtimeLabelColors = previous
    }
}
