package model.classes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import model.Point3D
import model.UNASSIGNED_INDEX
import utils.UUID
data class Point3DNarys(
    override val x: Float,
    override val z: Float,
    override var name: String? = null,
    override var parent: Point3D? = null,
    var isSegmentEndpoint: Boolean = false,
    override var localColor: Color? = null,
    override var localWidth: Float? = null,
    override val id: String = UUID.randomUUID().toString(),
    override var localSuperscript: String? = null,
    var parentLine: Line3D? = null,
    var pendingParentLineId: String? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var showInAxoInitial : Boolean = true,
): PointsNarys, Point2DProjection {
    override var showInAxo by mutableStateOf(showInAxoInitial)
    override val color: Color
        get() = parent?.color ?: localColor ?: Color.Black
    override val width: Float
        get() = parent?.width ?: localWidth ?: 1f
    val superscript: String?
        get() = parent?.superscript ?: localSuperscript
    var parentSegment: Segment2DNarys? = null
    val isProjectedLine: Boolean get() = pendingParentLineId != null || parentLine != null
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
}
