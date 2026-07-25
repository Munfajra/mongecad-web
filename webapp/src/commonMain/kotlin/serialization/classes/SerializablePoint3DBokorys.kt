package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.Point3D
import model.classes.Point3DBokorys
import serialization.SerializableColor

@Serializable
data class SerializablePoint3DBokorys(
    val id: String,
    val y: Float,
    val z: Float,
    val name: String? = null,
    val parentId: String? = null,
    val isSegmentEndpoint: Boolean = false,
    val localColor: SerializableColor? = null,
    val localWidth: Float? = null,
    val localSuperscript: String?,
    val parentLineId: String? = null,
    val creationIndex: Long,
    val showInAxo: Boolean

)
fun Point3DBokorys.toSerializable(): SerializablePoint3DBokorys {
    return SerializablePoint3DBokorys(
        id = this.id,
        y = this.y,
        z = this.z,
        name = this.name,
        parentId = this.parent?.id,
        isSegmentEndpoint = this.isSegmentEndpoint,
        localColor = this.localColor?.let { SerializableColor.from(it) },
        localWidth = this.localWidth,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        parentLineId = pendingParentLineId ?: parentLine?.id,
        showInAxo = this.showInAxo
    )
}
fun SerializablePoint3DBokorys.toRuntime(points3DById: Map<String, Point3D>): Point3DBokorys {
    return Point3DBokorys(
        y = this.y,
        z = this.z,
        name = this.name,
        parent = this.parentId?.let { points3DById[it] },
        isSegmentEndpoint = this.isSegmentEndpoint,
        localColor = this.localColor?.toColor(),
        localWidth = this.localWidth,
        id = this.id,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        pendingParentLineId = this.parentLineId,
        showInAxoInitial = this.showInAxo
    )
}
fun SerializablePoint3DBokorys.toRuntime(): Point3DBokorys {
    return Point3DBokorys(
        y = this.y,
        z = this.z,
        name = this.name,
        parent = null,
        isSegmentEndpoint = this.isSegmentEndpoint,
        localColor = this.localColor?.toColor(),
        localWidth = this.localWidth,
        id = this.id,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        pendingParentLineId = this.parentLineId,
        showInAxoInitial = this.showInAxo
    )
}
