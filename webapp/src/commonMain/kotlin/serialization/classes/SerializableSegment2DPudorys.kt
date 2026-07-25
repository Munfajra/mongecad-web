package serialization.classes

import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.Point3DPudorys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable

@Serializable
data class SerializableSegment2DPudorys(
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
    val startY: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null
)
fun Segment2DPudorys.toSerializable(): SerializableSegment2DPudorys {
    return SerializableSegment2DPudorys(
        id = this.id,
        startId = this.start.id,
        endId = this.end.id,
        name = this.name,
        parentId = this.parent?.id ?: this.parentId,
        localColor = this.localColor?.let { SerializableColor.Companion.from(it) },
        localLineStyle = this.localLineStyle.toSerializable(),
        localStrokeWidth = this.localStrokeWidth,
        showInAxo = this.showInAxo,
        isConicalSilhouette = this.isConicalSilhouette,
        creationIndex = this.creationIndex,
        conicalSurfaceId = this.conicalSurfaceId,
        startX = this.start.x,
        startY = this.start.y,
        endX = this.end.x,
        endY = this.end.y
    )
}
fun SerializableSegment2DPudorys.toRuntime(
    pointsById: Map<String, Point3DPudorys>,
    segmentsById: Map<String, Segment3D>
): Segment2DPudorys? {
    val parent = this.parentId?.let { segmentsById[it] }
    val start = pointsById[this.startId] ?: if (startX != null && startY != null) {
        Point3DPudorys(
            x = startX,
            y = startY,
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
    val end = pointsById[this.endId] ?: if (endX != null && endY != null) {
        Point3DPudorys(
            x = endX,
            y = endY,
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

    val segment = Segment2DPudorys(
        start = start,
        end = end,
        name = this.name,
        parent = parent,
        parentId = this.parentId,
        localColor = this.localColor?.toColor(),
        localLineStyle   = localLineStyle?.toLineStyle() ?: LineStyle.Solid,
        localStrokeWidth = this.localStrokeWidth,
        showInAxoInitial = this.showInAxo,
        id = this.id,
        isConicalSilhouette = this.isConicalSilhouette,
        conicalSurfaceId = this.conicalSurfaceId,
        creationIndex = this.creationIndex
    )

    // 🟢 Doplnit zpětnou vazbu bodům
    start.parentSegment = segment
    end.parentSegment = segment

    return segment
}
