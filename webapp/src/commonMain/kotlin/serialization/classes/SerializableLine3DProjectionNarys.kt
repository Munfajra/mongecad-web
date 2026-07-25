package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.Line3D
import model.classes.Line3DProjectionNarys
import serialization.*

@Serializable
data class SerializableLine3DProjectionNarys(
    val id: String,
    val point: SerializablePoint3DNarys, // ⬅ změna z pointId na celý bod
    val direction: SerializableOffset,
    val localName: String? = null,
    val parentId: String? = null,
    val localColor: SerializableColor? = null,
    val localLineStyle: SerializableLineStyle? = null,
    val localStrokeWidth: Float? = null,
    val localSuperscript: String?,
    val clipLineX: Boolean? = null,
    val clipLineZ: Boolean? = null,
    val creationIndex: Long,
    val showInAxo: Boolean = true,
    val localCustomTrimRange: SerializableLineTrimRange? = null,
)

fun Line3DProjectionNarys.toSerializable(): SerializableLine3DProjectionNarys {
    return SerializableLine3DProjectionNarys(
        id = this.id,
        point = this.point.toSerializable(),
        direction = SerializableOffset.from(this.direction),
        localName = this.localName,
        parentId = this.parent?.id,
        localColor = this.localColor?.let { SerializableColor.Companion.from(it) },
        localLineStyle = this.localLineStyle?.toSerializable(),
        localStrokeWidth = this.localStrokeWidth,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        clipLineX = this.clipLineX,
        clipLineZ = this.clipLineZ,
        showInAxo = this.showInAxo,
        localCustomTrimRange = this.localCustomTrimRange?.toSerializable(),
    )
}

fun SerializableLine3DProjectionNarys.toRuntime(
    parents: Map<String, Line3D>
): Line3DProjectionNarys {
    val point = this.point.toRuntime(emptyMap())
    val parent = this.parentId?.let { parents[it] }

    return Line3DProjectionNarys(
        point = point,
        direction = this.direction.toOffset(),
        localName = this.localName,
        parent = parent,
        localColor = this.localColor?.toColor(),
        localLineStyle = this.localLineStyle?.toLineStyle(),
        localStrokeWidth = this.localStrokeWidth,
        id = this.id,
        localSuperscript = this.localSuperscript,
        clipLineX = this.clipLineX,
        clipLineZ = this.clipLineZ,
        parentId = this.parentId,
        creationIndex = this.creationIndex,
        showInAxoInitial = this.showInAxo,
        localCustomTrimRange = this.localCustomTrimRange?.toRuntime(),
    )
}
