package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
interface NamedLinePudorys: NamedLine2D, CustomTrimmedLine2D {
    val id: String
    val point: Point3DPudorys
    override val direction: Offset
    override val name: String?
    val color: Color get() = Color.Black
    val strokeWidth: Float get() = 1f
    val lineStyle: LineStyle get() = LineStyle.Solid
    val clipLineX: Boolean? get() = false
    val clipLineY: Boolean? get() = false
    val parentId: String? get() = null
    val showInAxo: Boolean get() = false
    override val customTrimRange: LineTrimRange? get() = null
    fun to2DLine(): Line
}
data class NamedLinePudorysImpl(
    override val id: String,
    override val name: String?,
    override val point: Point3DPudorys,
    override val direction: Offset,
    override val color: Color,
    override val strokeWidth: Float,
    override val lineStyle: LineStyle
) : NamedLinePudorys {
    override fun to2DLine(): Line {
        TODO("Not yet implemented")
    }
}
