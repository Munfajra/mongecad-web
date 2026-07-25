package model.classes
import kotlinx.serialization.Transient
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class PlaneTracePudorys(
    override val point: Point3DPudorys,
    override val direction: Offset,
    @Transient override var parent: Plane3D? = null,   // ⬅️ parent NEporovnávat
    val localName: String? = null,
    val localColor: Color? = null,
    val localLineStyle: LineStyle? = null,
    val localStrokeWidth: Float? = null,
    val localSuperscript: String? = null,
    override val id: String = UUID.randomUUID().toString(),
    override var clipLineX: Boolean? = null,
    override var clipLineY: Boolean? = null,
    override val parentId: String? = null,
    override val showInAxo: Boolean = true,
    val isVirtual: Boolean = false,
    val creationIndex: Long = UNASSIGNED_INDEX
) : NamedLinePudorys, Trace2DProjection, ClippableInPudorys {
    override val name: String? get() = parent?.name ?: localName
    override val color: Color get() = parent?.color ?: localColor ?: Color.Black
    override val lineStyle: LineStyle get() = parent?.lineStyle ?: localLineStyle ?: LineStyle.Solid
    override val strokeWidth: Float get() = parent?.strokeWidth ?: localStrokeWidth ?: 1f
    override val superscript: String? get() = parent?.superscript ?: localSuperscript
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
    override fun to2DLine(): Line = Line(Offset(point.x, point.y), direction)
    // ⚠️ HashCode/equals musí ignorovat parent a odvozené vlastnosti!
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + point.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + (localName?.hashCode() ?: 0)
        result = 31 * result + (localColor?.hashCode() ?: 0)
        result = 31 * result + (localLineStyle?.hashCode() ?: 0)
        result = 31 * result + (localStrokeWidth?.hashCode() ?: 0)
        result = 31 * result + (localSuperscript?.hashCode() ?: 0)
        result = 31 * result + (clipLineX?.hashCode() ?: 0)
        result = 31 * result + (clipLineY?.hashCode() ?: 0)
        result = 31 * result + (parentId?.hashCode() ?: 0)
        result = 31 * result + showInAxo.hashCode()
        result = 31 * result + isVirtual.hashCode()
        return result
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaneTracePudorys) return false
        return id == other.id &&
                point == other.point &&
                direction == other.direction &&
                localName == other.localName &&
                localColor == other.localColor &&
                localLineStyle == other.localLineStyle &&
                localStrokeWidth == other.localStrokeWidth &&
                localSuperscript == other.localSuperscript &&
                clipLineX == other.clipLineX &&
                clipLineY == other.clipLineY &&
                parentId == other.parentId &&
                showInAxo == other.showInAxo &&
                isVirtual == other.isVirtual
        // ⬅️ parent NE, a ani name/color/lineStyle/strokeWidth/superscript z parenta neporovnávat
    }
}
