package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class Segment2DAxo(
    val start: Point3DAxo,
    val end: Point3DAxo,
    override var name: String? = null,
    override var parent: Segment3D? = null,
    var localColor: Color? = null,
    val localLineStyle: LineStyle = LineStyle.Solid,
    var localStrokeWidth: Float? = null,
    override val id: String = UUID.randomUUID().toString(),
    val isConicalSilhouette: Boolean = false,
    val conicalSurfaceId: String? = null,
    var parentId: String? = null,
    var showInAxoInitial: Boolean = true,
    val creationIndex: Long = UNASSIGNED_INDEX
) : Segment2DProjection, OverlayAxoSegment {
    var showInAxo by mutableStateOf(showInAxoInitial)
    override val a: Offset
        get() = Offset(this.start.x, this.start.y)
    override val b: Offset
        get() = Offset(this.end.x, this.end.y)
    override val color: Color
        get() = parent?.color ?: localColor ?: Color.Black
    override val lineStyle: LineStyle
        get() = localLineStyle
    override val strokeWidth: Float
        get() = parent?.strokeWidth ?: localStrokeWidth ?: 1f
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
}
