package model.classes
import kotlinx.serialization.Transient
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class PlaneTraceBokorys(
    override val point: Point3DBokorys,
    override val direction: Offset,
    @Transient override var parent: Plane3D? = null,
    val localName: String? = null,
    val localColor: Color? = null,
    val localLineStyle: LineStyle? = null,
    val localStrokeWidth: Float? = null,
    val localSuperscript: String? = null,
    override val id: String = UUID.randomUUID().toString(),
    override var clipLineY: Boolean? = null,
    override var clipLineZ: Boolean? = null,
    override val parentId: String? = null,
    override val showInAxo: Boolean = true,
    val isVirtual: Boolean = false,
    val creationIndex: Long = UNASSIGNED_INDEX
) : NamedLineBokorys, Trace2DProjection, ClippableInBokorys {
    override val name: String? get() = parent?.name ?: localName
    override val color: Color get() = parent?.color ?: localColor ?: Color.Black
    override val lineStyle: LineStyle get() = parent?.lineStyle ?: localLineStyle ?: LineStyle.Solid
    override val strokeWidth: Float get() = parent?.strokeWidth ?: localStrokeWidth ?: 1f
    override val superscript: String? get() = parent?.superscript ?: localSuperscript
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
    override fun to2DLine(): Line = Line(Offset(point.y, point.z), direction)
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + point.id.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + (localName?.hashCode() ?: 0)
        result = 31 * result + (localColor?.hashCode() ?: 0)
        result = 31 * result + (localLineStyle?.hashCode() ?: 0)
        result = 31 * result + (localStrokeWidth?.hashCode() ?: 0)
        result = 31 * result + (localSuperscript?.hashCode() ?: 0)
        result = 31 * result + (clipLineY?.hashCode() ?: 0)
        result = 31 * result + (clipLineZ?.hashCode() ?: 0)
        result = 31 * result + (parentId?.hashCode() ?: 0)
        result = 31 * result + showInAxo.hashCode()
        result = 31 * result + isVirtual.hashCode()
        return result
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaneTraceBokorys) return false
        return id == other.id &&
                point.id == other.point.id &&
                direction == other.direction &&
                localName == other.localName &&
                localColor == other.localColor &&
                localLineStyle == other.localLineStyle &&
                localStrokeWidth == other.localStrokeWidth &&
                localSuperscript == other.localSuperscript &&
                clipLineY == other.clipLineY &&
                clipLineZ == other.clipLineZ &&
                parentId == other.parentId &&
                showInAxo == other.showInAxo &&
                isVirtual == other.isVirtual
    }
}
