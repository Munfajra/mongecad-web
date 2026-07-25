package model.classes
import utils.withSuffixOnce
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class Line3DProjectionBokorys(
    override val point: Point3DBokorys,
    override val direction: Offset,
    var localName: String? = null,
    override var parent: Line3D? = null,
    val localColor: Color? = null,
    val localLineStyle: LineStyle? = null,
    val localStrokeWidth: Float? = null,
    val localSuperscript: String? = null,
    override var clipLineY: Boolean? = null,
    override var clipLineZ: Boolean? = null,
    override val id: String = UUID.randomUUID().toString(),
    override var parentId: String? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var showInAxoInitial : Boolean = true,
    val localCustomTrimRange: LineTrimRange? = null,
    ) : NamedLineBokorys, Line2DProjection, ClippableInBokorys {
    override var showInAxo by mutableStateOf(showInAxoInitial)
    override val name: String?
        get() = parent?.name?.withSuffixOnce("₃") ?: localName
    override val color: Color
        get() = if(parent?.id in listOf("z_axis","x_axis","y_axis"))
            localColor ?:parent?.color ?:  Color.Black
    else
            parent?.color ?: localColor ?: Color.Black
    override val lineStyle: LineStyle
        get() = parent?.lineStyle ?: localLineStyle ?: LineStyle.Solid
    override val strokeWidth: Float
        get() = parent?.strokeWidth ?: localStrokeWidth ?: 1f
    override val superscript: String?
        get() = parent?.superscript ?: localSuperscript
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
    override val customTrimRange: LineTrimRange?
        get() = parent?.customTrimRange ?: localCustomTrimRange
    override fun to2DLine(): Line {
        return Line(Offset(point.y, point.z), direction)
    }
}
