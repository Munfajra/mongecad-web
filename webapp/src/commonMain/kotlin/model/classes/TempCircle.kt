package model.classes
import androidx.compose.ui.geometry.Offset
data class TempSnapCircle(
    val center: Offset,
    val radius: Float,
    val id: String = "temp_circle",
    val space: TempSnapSpace
)