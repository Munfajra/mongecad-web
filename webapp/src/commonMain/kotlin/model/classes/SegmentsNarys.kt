package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
interface SegmentsNarys {
    val start: Point3DNarys
    val end: Point3DNarys
    var name: String?
    val parent: Segment3D?
    val color: Color get() = Color.Gray
    val strokeWidth: Float get() = 1f
    val lineStyle: LineStyle get() = LineStyle.Solid
    val id: String
}