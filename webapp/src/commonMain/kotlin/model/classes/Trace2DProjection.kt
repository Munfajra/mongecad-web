package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
sealed interface Trace2DProjection: LinearObject2D {
    override val parentAny get() = parent
    override val id: String
    val name: String?
    var parent: Plane3D?
    override val color: Color
    override val strokeWidth: Float
    override val lineStyle: LineStyle
    val superscript: String?
}
interface LinearObject2D {
    val id: String
    val color: Color
    val strokeWidth: Float
    val lineStyle: LineStyle
    val parentAny: Any?
}