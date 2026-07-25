package state.snapMonge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import utils.dotProduct
import kotlin.math.abs

fun Rect.intersectsCircleNarys(center: Offset, radius: Float): Boolean {
    val cx = center.x
    val cz = center.y // Z souřadnice (nepřevracíme tady)

    val closestX = cx.coerceIn(left, right)
    val closestZ = cz.coerceIn(top, bottom)

    val dx = cx - closestX
    val dz = cz - closestZ

    return dx * dx + dz * dz <= radius * radius
}
fun Rect.intersectsEllipseNarys(center: Offset, u: Offset, v: Offset): Boolean {
    val a = u.getDistance()
    val b = v.getDistance()
    if (a < 1e-6f || b < 1e-6f) return false

    // Vzdálenost ze středu k nejvzdálenějšímu bodu elipsy v ose x a z
    val maxExtent = Offset(
        x = abs(u.x) + abs(v.x),
        y = abs(u.y) + abs(v.y)
    )

    val ellipseBounds = Rect(
        offset = center - maxExtent,
        size = Size(maxExtent.x * 2f, maxExtent.y * 2f)
    )
    return this.overlaps(ellipseBounds)
}
fun projectionOnTempLine(
    screenCur: Offset,
    origin: Offset,
    dir: Offset,
    planeNarys: Boolean,
    state: state.MongeState
): Offset {
    val logical = (screenCur - state.canvasOffset) / state.scale
    val inPlane = if (planeNarys) Offset(logical.x, -logical.y) else logical
    val unit = dir / dir.getDistance()
    return origin + unit * ((inPlane - origin).dotProduct(unit))
}

