package monge.input.selection

import androidx.compose.ui.geometry.Offset
import utils.dotProduct

fun projectPointOntoSegment(p: Offset, a: Offset, b: Offset): Offset {
    val ab = b - a
    val ap = p - a
    val t = ap.dotProduct(ab) / ab.getDistanceSquared()

    return when {
        t < 0f -> a
        t > 1f -> b
        else -> a + ab * t
    }
}