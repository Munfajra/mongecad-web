package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class HelpLineNarys(
    override val point: Point3DNarys,
    override val direction: Offset,
    override var name: String? = "",
    val localColor: Color? = null,
    val localLineStyle: LineStyle? = null,
    val localStrokeWidth: Float? = null,
    var lowerSuperscript: String? = null,
    override val id: String = UUID.randomUUID().toString(),
    override var clipLineX: Boolean? = null,
    override var clipLineZ: Boolean? = null,
    override val parentAny: Any?,
    var localSuperscript: String? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    override val customTrimRange: LineTrimRange? = null
) : NamedLineNarys, ClippableInNarys, LinearObject2D {
    override val color: Color
        get() = localColor ?: Color.Black
    override val lineStyle: LineStyle
        get() = localLineStyle ?: LineStyle.Solid
    override val strokeWidth: Float
        get() = localStrokeWidth ?: 1f
    override fun to2DLine(): Line {
        return Line(Offset(point.x, point.z), direction)
}}
