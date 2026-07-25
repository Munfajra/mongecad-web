package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.Point3D
import model.classes.Point3DNarys
import serialization.SerializableColor

@Serializable
data class SerializablePoint3DNarys(
    val id: String,
    val x: Float,
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
fun Point3DNarys.toSerializable(): SerializablePoint3DNarys {
    return SerializablePoint3DNarys(
        id = this.id,
        x = this.x,
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
fun SerializablePoint3DNarys.toRuntime(points3DById: Map<String, Point3D>): Point3DNarys {
    return Point3DNarys(
        x = this.x,
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
fun SerializablePoint3DNarys.toRuntime(): Point3DNarys {
    return Point3DNarys(
        x = this.x,
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