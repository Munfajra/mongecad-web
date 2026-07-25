package model

import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import serialization.SettingsManager

enum class LineStyle {
    Solid,
    Dashed,
    Dotted,
    DashDot
}

fun LineStyle.baseDashIntervals(): FloatArray? = when (this) {
    LineStyle.Solid -> null
    LineStyle.Dashed -> floatArrayOf(8f, 6f)
    LineStyle.Dotted -> floatArrayOf(2f, 6f)
    LineStyle.DashDot -> floatArrayOf(8f, 6f, 2f, 6f)
}

fun LineStyle.dashIntervals(scale: Float = 1f, density: Float = SettingsManager.current.lineDashDensity): FloatArray? {
    val base = baseDashIntervals() ?: return null
    val factor = scale / density.coerceAtLeast(0.25f)
    return FloatArray(base.size) { base[it] * factor }
}
fun LineStyle.toCzech(): String = when (this) {
    LineStyle.Solid   -> "Plná"
    LineStyle.Dashed  -> "Čárkovaná"
    LineStyle.Dotted  -> "Tečkovaná"
    LineStyle.DashDot -> "Čerchovaná"
}
data class DashStyle(
    val intervals: FloatArray?, // null = solid
    val phase: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this::class != other?.let { it::class }) return false

        other as DashStyle

        if (phase != other.phase) return false
        if (!intervals.contentEquals(other.intervals)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = phase.hashCode()
        result = 31 * result + (intervals?.contentHashCode() ?: 0)
        return result
    }
}
fun LineStyle.toPathEffect(
    strokeWidthPx: Float,
    gapMultiplier: Float = 1f,
    dashMultiplier: Float = 1f
): PathEffect? {
    val dash = toDashStyle(strokeWidthPx, gapMultiplier, dashMultiplier)
    return dash.intervals?.let { PathEffect.dashPathEffect(it) }
}

fun LineStyle.toDashStyle(
    strokeWidthPx: Float,
    gapMultiplier: Float = 1.0f,
    dashMultiplier: Float = 1.0f
): DashStyle {
    val base = baseDashIntervals() ?: return DashStyle(null)
    val density = SettingsManager.current.lineDashDensity.coerceAtLeast(0.25f)
    val intervals = FloatArray(base.size) { index ->
        val multiplier = if (index % 2 == 0) dashMultiplier else gapMultiplier
        base[index] * strokeWidthPx * multiplier / density
    }
    return DashStyle(intervals)
}

fun buildStroke(
    strokeWidthPx: Float,
    style: LineStyle,
    gapMultiplier: Float = 1.0f,
    dashMultiplier: Float = 1.0f
): Stroke {
    val dash = style.toDashStyle(strokeWidthPx, gapMultiplier, dashMultiplier)

    val pe = dash.intervals?.let { PathEffect.dashPathEffect(it, dash.phase) }
    return Stroke(
        width = strokeWidthPx,
        pathEffect = pe,
        cap = StrokeCap.Round,      // ✅ zaoblené konce jednotlivých “dashů”
        join = StrokeJoin.Round     // ✅ hezčí rohy
    )
}
