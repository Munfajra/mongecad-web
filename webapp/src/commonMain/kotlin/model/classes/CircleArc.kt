package model.classes
import androidx.compose.ui.geometry.Offset
import model.ArcMode
data class CircleArcPudorys(
    val circleId: String,
    val a: Offset,
    val b: Offset,
    val mode: ArcMode
)
data class CircleArcNarys(
    val circleId: String,
    val a: Offset,
    val b: Offset,
    val mode: ArcMode
)