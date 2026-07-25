package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.Point3D
import model.classes.Point3DAxo
import serialization.SerializableColor

@Serializable
data class SerializablePoint3DAxo(
    val id: String,
    val x: Float,
    val y: Float,
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
fun Point3DAxo.toSerializable(): SerializablePoint3DAxo {
    return SerializablePoint3DAxo(
        id = this.id,
        x = this.x,
        y = this.y,
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
fun SerializablePoint3DAxo.toRuntime(points3DById: Map<String,Point3D>): Point3DAxo {
    return Point3DAxo(
        x = this.x,
        y = this.y,
        name = this.name,
        parent = this.parentId?.let { points3DById[it] },
        isSegmentEndpoint = this.isSegmentEndpoint,
        localColor = this.localColor?.toColor(),
        localWidth = this.localWidth,
        id = this.id,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        pendingParentLineId = this.parentLineId,
        showInAxoInitial = this.showInAxo)
}
