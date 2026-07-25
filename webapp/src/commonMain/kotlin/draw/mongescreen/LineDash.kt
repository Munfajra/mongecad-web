package draw.mongescreen

import androidx.compose.ui.graphics.PathEffect
import model.LineStyle
import model.dashIntervals
import serialization.SettingsManager

data class DashPatternPx(val intervals: FloatArray, val len: Float) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DashPatternPx) return false
        return len == other.len && intervals.contentEquals(other.intervals)
    }

    override fun hashCode(): Int {
        var result = len.hashCode()
        result = 31 * result + intervals.contentHashCode()
        return result
    }
}

fun lineStyleDashPatternPx(style: LineStyle, scale: Float = 1f): DashPatternPx? {
    val intervals = style.dashIntervals(scale) ?: return null
    return DashPatternPx(intervals, intervals.sum())
}

fun lineStyleDashPathEffectPx(style: LineStyle, phase: Float = 0f, scale: Float = 1f): PathEffect? {
    val pattern = lineStyleDashPatternPx(style, scale) ?: return null
    return PathEffect.dashPathEffect(pattern.intervals, phase)
}

fun axoPreviewDashPatternPx(): DashPatternPx {
    val density = SettingsManager.current.lineDashDensity.coerceAtLeast(0.25f)
    val intervals = floatArrayOf(
        18f / density,
        14f / density
    )
    return DashPatternPx(intervals, intervals.sum())
}

fun axoPreviewDashPathEffectPx(phase: Float = 0f): PathEffect =
    PathEffect.dashPathEffect(axoPreviewDashPatternPx().intervals, phase)
