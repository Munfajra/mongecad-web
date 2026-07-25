package state

import androidx.compose.ui.geometry.Offset

data class SnapIntersectionCache(
    val signature: String,
    val points: List<Offset>
)

data class CachedAOOverlayIntersection(
    val lineA: Any,
    val lineB: Any,
    val intersectionOverlayLocal: Offset
)

data class AOOverlayIntersectionCache(
    val signature: String,
    val intersections: List<CachedAOOverlayIntersection>
)
