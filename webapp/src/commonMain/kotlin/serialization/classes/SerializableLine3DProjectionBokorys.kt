package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.Line3D
import model.classes.Line3DProjectionBokorys
import serialization.*

@Serializable
data class SerializableLine3DProjectionBokorys(
    val id: String,
    val point: SerializablePoint3DBokorys,
    val direction: SerializableOffset,
    val localName: String? = null,
    val parentId: String? = null,
    val localColor: SerializableColor? = null,
    val localLineStyle: SerializableLineStyle? = null,
    val localStrokeWidth: Float? = null,
    val localSuperscript: String?,
    val creationIndex: Long,
    val clipLineY: Boolean? = null,
    val clipLineZ: Boolean? = null,
    val showInAxo: Boolean = true,
    val localCustomTrimRange: SerializableLineTrimRange? = null,
)

fun Line3DProjectionBokorys.toSerializable(): SerializableLine3DProjectionBokorys {
    return SerializableLine3DProjectionBokorys(
        id = this.id,
        point = this.point.toSerializable(),
        direction = SerializableOffset.from(this.direction),
        localName = this.localName,
        parentId = this.parent?.id,
        localColor = this.localColor?.let { SerializableColor.from(it) },
        localLineStyle = this.localLineStyle?.toSerializable(),
        localStrokeWidth = this.localStrokeWidth,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        clipLineY = this.clipLineY,
        clipLineZ = this.clipLineZ,
        showInAxo = this.showInAxo,
        localCustomTrimRange = this.localCustomTrimRange?.toSerializable(),
    )
}

fun SerializableLine3DProjectionBokorys.toRuntime(
    parents: Map<String, Line3D>
): Line3DProjectionBokorys {
    val point = this.point.toRuntime(emptyMap())
    val parent = this.parentId?.let { parents[it] }

    return Line3DProjectionBokorys(
        point = point,
        direction = this.direction.toOffset(),
        localName = this.localName,
        parent = parent,
        localColor = this.localColor?.toColor(),
        localLineStyle = this.localLineStyle?.toLineStyle(),
        localStrokeWidth = this.localStrokeWidth,
        id = this.id,
        localSuperscript = this.localSuperscript,
        parentId = this.parentId,
        creationIndex = this.creationIndex,
        clipLineY = this.clipLineY,
        clipLineZ = this.clipLineZ,
        showInAxoInitial = this.showInAxo,
        localCustomTrimRange = this.localCustomTrimRange?.toRuntime(),
    )
}
