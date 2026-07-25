package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
interface NamedLineBokorys : NamedLine2D, CustomTrimmedLine2D {
    val point: Point3DBokorys
    override val direction: Offset
    override val name: String?
    val color: Color get() = Color.Black
    val strokeWidth: Float get() = 1f
    val lineStyle: LineStyle get() = LineStyle.Solid
    val clipLineY: Boolean? get() = false
    val clipLineZ: Boolean? get() = false
    val id: String
    val parentId: String? get() = null
    val showInAxo: Boolean get() = false
    override val customTrimRange: LineTrimRange? get() = null
    fun to2DLine(): Line
}
