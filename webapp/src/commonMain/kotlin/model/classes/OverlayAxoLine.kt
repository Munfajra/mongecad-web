package model.classes
import androidx.compose.ui.geometry.Offset
interface OverlayAxoLine {
    val point: Offset
    val direction: Offset
    val id: String
}