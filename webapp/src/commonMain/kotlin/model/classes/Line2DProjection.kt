package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
sealed interface Line2DProjection: LinearObject2D  {
    override val parentAny get() = parent
    override val id: String
    val name: String?
    val parent: Line3D?
    override val color: Color
    override val strokeWidth: Float
    override val lineStyle: LineStyle
    val superscript: String?
    val showInAxo: Boolean
}
