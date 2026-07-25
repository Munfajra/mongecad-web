package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
interface NamedLineNarys : NamedLine2D, CustomTrimmedLine2D {
    val point: Point3DNarys
    override val direction: Offset
    override val name: String?
    val color: Color get() = Color.Black
    val strokeWidth: Float get() = 1f
    val lineStyle: LineStyle get() = LineStyle.Solid
    val id: String
    val clipLineX: Boolean? get() = false
    val clipLineZ: Boolean? get() = false
    val parentId: String? get() = null
    val showInAxo: Boolean get() = false
    override val customTrimRange: LineTrimRange? get() = null
    fun to2DLine(): Line
}
data class NamedLineNarysImpl(
    override val id: String,
    override val name: String?,
    override val point: Point3DNarys,
    override val direction: Offset,
    override val color: Color,
    override val strokeWidth: Float,
    override val lineStyle: LineStyle
) : NamedLineNarys {
    override fun to2DLine(): Line {
        TODO("Not yet implemented")
    }
}
