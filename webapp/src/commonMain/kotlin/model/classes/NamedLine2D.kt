package model.classes
import androidx.compose.ui.geometry.Offset
sealed interface NamedLine2D {
    val name: String?
    val direction: Offset
}
