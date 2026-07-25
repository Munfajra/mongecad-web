package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
sealed interface Segment2DProjection {
    val id: String
    val name: String?
    val parent: Segment3D?
    val color: Color
    val strokeWidth: Float
    val lineStyle: LineStyle
}
