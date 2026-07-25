package draw.mongescreen.labels

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.tools.PdfExportFonts

internal fun String?.emptyIfNullText(): String {
    val trimmed = this?.trim().orEmpty()
    return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
}

data class RichLabelPart(
    val base: String,
    val superscript: String = ""
)

internal data class RichLabelMetrics(
    val width: Float,
    val top: Float,
    val bottom: Float
)

internal fun measureRichLabelMetrics(
    parts: List<RichLabelPart>,
    baseFontPx: Float,
    baseTypeface: String = "italic",
    supTypeface: String = "greek",
    supScale: Float = 0.70f,
    supDyFactor: Float = 0.35f,
    supGapPx: Float = 2f,
    separator: String = "="
): RichLabelMetrics {
    if (parts.isEmpty()) return RichLabelMetrics(0f, 0f, 0f)

    val supFontPx = baseFontPx * supScale
    val supDy = baseFontPx * supDyFactor
    val separatorWidth = measureSkiaParagraph(separator, baseFontPx, baseTypeface).width

    var width = 0f
    var top = 0f
    var bottom = 0f
    var first = true

    parts.forEachIndexed { idx, p ->
        if (idx > 0) width += separatorWidth

        val baseSize = measureSkiaParagraph(p.base, baseFontPx, baseTypeface)
        val baseShiftY = baselineToTopShiftPx(baseSize.height)
        val baseTop = -baseShiftY
        val baseBottom = baseSize.height - baseShiftY
        var partWidth = baseSize.width

        val sup = p.superscript
        if (sup.isNotBlank()) {
            val supSize = measureSkiaParagraph(sup, supFontPx, supTypeface)
            val supShiftY = baselineToTopShiftPx(supSize.height)
            val supTop = -supDy - supShiftY
            val supBottom = -supDy + (supSize.height - supShiftY)
            partWidth += supGapPx + supSize.width

            if (first) {
                top = minOf(baseTop, supTop)
                bottom = maxOf(baseBottom, supBottom)
            } else {
                top = minOf(top, baseTop, supTop)
                bottom = maxOf(bottom, baseBottom, supBottom)
            }
        } else if (first) {
            top = baseTop
            bottom = baseBottom
        } else {
            top = minOf(top, baseTop)
            bottom = maxOf(bottom, baseBottom)
        }

        width += partWidth
        first = false
    }

    return RichLabelMetrics(width = width, top = top, bottom = bottom)
}

internal fun DrawScope.drawRichLabel(
    parts: List<RichLabelPart>,
    anchor: Offset,
    color: Color,
    baseFontPx: Float,
    baseTypeface: String = "italic",
    supTypeface: String = "greek",
    supScale: Float = 0.70f,
    supDyFactor: Float = 0.35f,
    supGapPx: Float = 2f,
    separator: String = "="
): Float {
    if (parts.isEmpty()) return 0f

    val supFontPx = baseFontPx * supScale
    val supDy = baseFontPx * supDyFactor
    val separatorWidth = measureSkiaParagraph(separator, baseFontPx, baseTypeface).width
    var x = anchor.x

    parts.forEachIndexed { idx, p ->
        if (idx > 0) {
            drawSkiaText(
                text = separator,
                anchor = Offset(x, anchor.y),
                color = color,
                fontPx = baseFontPx,
                typefaceFamily = baseTypeface
            )
            x += separatorWidth
        }

        val baseWidth = drawSkiaText(
            text = p.base,
            anchor = Offset(x, anchor.y),
            color = color,
            fontPx = baseFontPx,
            typefaceFamily = baseTypeface
        )

        if (p.superscript.isNotBlank()) {
            drawSkiaText(
                text = p.superscript,
                anchor = Offset(x + baseWidth + supGapPx, anchor.y - supDy),
                color = color,
                fontPx = supFontPx,
                typefaceFamily = supTypeface
            )
        }

        val supWidth = if (p.superscript.isBlank()) 0f else measureSkiaParagraph(p.superscript, supFontPx, supTypeface).width + supGapPx
        x += baseWidth + supWidth
    }

    return x - anchor.x
}



internal fun pdfIndexFont(text: String, fonts: PdfExportFonts): PDFont {
    return when {
        canEncodePdfText(fonts.italic, text) -> fonts.italic
        canEncodePdfText(fonts.greek, text) -> fonts.greek
        else -> fonts.regular
    }
}

private fun pdfTextFont(text: String, preferred: PDFont, fonts: PdfExportFonts): PDFont {
    return when {
        canEncodePdfText(preferred, text) -> preferred
        canEncodePdfText(fonts.greek, text) -> fonts.greek
        canEncodePdfText(fonts.italic, text) -> fonts.italic
        else -> fonts.regular
    }
}


private val pdfSubscriptMap = mapOf(
    '\u2080' to '0',
    '\u2081' to '1',
    '\u2082' to '2',
    '\u2083' to '3',
    '\u2084' to '4',
    '\u2085' to '5',
    '\u2086' to '6',
    '\u2087' to '7',
    '\u2088' to '8',
    '\u2089' to '9',
    '\u208A' to '+',
    '\u208B' to '-',
    '\u208C' to '=',
    '\u208D' to '(',
    '\u208E' to ')',
    '\u2090' to 'a',
    '\u2091' to 'e',
    '\u2092' to 'o',
    '\u2093' to 'x',
    '\u2095' to 'h',
    '\u2096' to 'k',
    '\u2097' to 'l',
    '\u2098' to 'm',
    '\u2099' to 'n',
    '\u209A' to 'p',
    '\u209B' to 's',
    '\u209C' to 't'
)

private data class PdfInlineRun(
    val text: String,
    val isSubscript: Boolean
)






internal fun pdfLabelFontPt(
    fontPx: Float,
    labelScale: Float,
    geometry: PdfExportGeometry
): Float =
    fontPx * labelScale*geometry.previewToPdfScale

fun screenOffsetPxToPdfOffset(
    offsetPx: Offset,
    geometry: PdfExportGeometry
): Offset {
    return Offset(
        offsetPx.x * geometry.previewToPdfScale,
        -offsetPx.y * geometry.previewToPdfScale
    )
}
