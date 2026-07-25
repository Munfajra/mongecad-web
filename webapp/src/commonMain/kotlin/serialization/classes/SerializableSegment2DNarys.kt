package serialization.classes

import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.Point3DNarys
import model.classes.Segment2DNarys
import model.classes.Segment3D
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable

@Serializable
data class SerializableSegment2DNarys(
    val id: String,
    val startId: String,
    val endId: String,
    val name: String? = null,
    val parentId: String?,
    val localColor: SerializableColor? = null,
    val localLineStyle: SerializableLineStyle? = null,
    val localStrokeWidth: Float? = null,
    val showInAxo: Boolean = true,
    val isConicalSilhouette: Boolean = false,
    val conicalSurfaceId: String? = null,
    val creationIndex: Long,
    val startX: Float? = null,
    val startZ: Float? = null,
    val endX: Float? = null,
    val endZ: Float? = null
)
fun Segment2DNarys.toSerializable(): SerializableSegment2DNarys {
    return SerializableSegment2DNarys(
        id = this.id,
        startId = this.start.id,
        endId = this.end.id,
        name = this.name,
        parentId = this.parent?.id ?: this.parentId,
        localColor = this.localColor?.let { SerializableColor.from(it) },
        localLineStyle = this.localLineStyle.toSerializable(),
        localStrokeWidth = this.localStrokeWidth,
        showInAxo = this.showInAxo,
        isConicalSilhouette = this.isConicalSilhouette,
        creationIndex = this.creationIndex,
        conicalSurfaceId = this.conicalSurfaceId,
        startX = this.start.x,
        startZ = this.start.z,
        endX = this.end.x,
        endZ = this.end.z
    )
}
fun SerializableSegment2DNarys.toRuntime(
    pointsById: Map<String, Point3DNarys>,
    segmentsById: Map<String, Segment3D>
): Segment2DNarys? {
    val parent = parentId?.let { segmentsById[it] }
    val start  = pointsById[startId] ?: if (startX != null && startZ != null) {
        Point3DNarys(
            x = startX,
            z = startZ,
            name = "",
            parent = parent?.start,
            isSegmentEndpoint = true,
            localColor = localColor?.toColor(),
            localWidth = localStrokeWidth,
            id = startId,
            creationIndex = creationIndex,
            showInAxoInitial = showInAxo
        )
    } else return null
    val end = pointsById[endId] ?: if (endX != null && endZ != null) {
        Point3DNarys(
            x = endX,
            z = endZ,
            name = "",
            parent = parent?.end,
            isSegmentEndpoint = true,
            localColor = localColor?.toColor(),
            localWidth = localStrokeWidth,
            id = endId,
            creationIndex = creationIndex,
            showInAxoInitial = showInAxo
        )
    } else return null

    val segment = Segment2DNarys(
        start            = start,
        end              = end,
        name             = name,
        parent           = parent,
        parentId         = this.parentId,
        localColor       = localColor?.toColor(),
        localLineStyle   = localLineStyle?.toLineStyle() ?: LineStyle.Solid,
        localStrokeWidth = localStrokeWidth,
        showInAxoInitial = showInAxo,
        id               = id,
        isConicalSilhouette = this.isConicalSilhouette,
        conicalSurfaceId = this.conicalSurfaceId,
        creationIndex = this.creationIndex
    )

    start.parentSegment = segment
    end.parentSegment = segment

    return segment
}
