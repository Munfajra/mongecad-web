package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class HelpSegmentNarys(
    override val start: Point3DNarys,
    override val end: Point3DNarys,
    override var name: String? = null,
    override val parent: Segment3D? = null,
    var localColor: Color? = null,
    val localLineStyle: LineStyle? = null,
    var localStrokeWidth: Float? = null,
    override val id: String = UUID.randomUUID().toString(),
    val creationIndex: Long = UNASSIGNED_INDEX
): SegmentsNarys, HelpSegments , Segment2DProjection {
    override val color: Color
        get() = parent?.color ?: localColor ?: Color.Black
    override val lineStyle: LineStyle
        get() = parent?.lineStyle ?: localLineStyle ?: LineStyle.Solid
    override val strokeWidth: Float
        get() = parent?.strokeWidth ?: localStrokeWidth ?: 1f
}
