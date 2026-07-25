package draw.mongescreen.labels

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

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






