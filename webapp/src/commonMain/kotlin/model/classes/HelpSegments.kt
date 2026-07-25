package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
interface HelpSegments {
    val id: String
    val name: String?
    val parent: Segment3D?
    val color: Color
    val strokeWidth: Float
    val lineStyle: LineStyle
}