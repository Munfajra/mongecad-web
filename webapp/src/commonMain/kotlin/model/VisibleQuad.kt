package model

import androidx.compose.ui.geometry.Offset

data class VisibleQuad(
    val p0: Offset,
    val p1: Offset,
    val p2: Offset,
    val p3: Offset
) {
    val edges: List<Pair<Offset, Offset>>
        get() = listOf(
            p0 to p1,
            p1 to p2,
            p2 to p3,
            p3 to p0
        )
}