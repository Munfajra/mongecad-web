package geometry

import androidx.compose.ui.geometry.Offset
import model.ArcMode
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Úhly a rozsahy oblouků.
 *
 * Na desktopu tyhle funkce bydlí v `export/pdfRenderer/circlesPdf.kt`, i když
 * s PDF nesouvisí – volá je i kreslení na canvas. Stejný případ jako
 * `axoOverlayToScreen`.
 */
fun circleAngle(center: Offset, pt: Offset): Float =
    atan2(pt.y - center.y, pt.x - center.x)

private fun ccwDelta(start: Float, end: Float): Float {
    val twoPi = 2f * PI.toFloat()
    var d = (end - start) % twoPi
    if (d < 0f) d += twoPi
    return d
}

fun chooseArcSweep(start: Float, end: Float, mode: ArcMode): Float {
    val twoPi = 2f * PI.toFloat()
    val ccw = ccwDelta(start, end)     // 0..2π
    val cw = ccw - twoPi               // záporný

    return when (mode) {
        ArcMode.SHORTEST -> if (ccw <= PI.toFloat()) ccw else cw
        ArcMode.LONGEST -> if (ccw > PI.toFloat()) ccw else cw
        ArcMode.CCW -> ccw
        ArcMode.CW -> cw
    }
}
