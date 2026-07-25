package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.Line3D
import model.classes.Line3DProjectionAxo
import serialization.*


@Serializable
data class SerializableLine3DProjectionAxo(
    val id: String,
    val point: SerializablePoint3DAxo,
    val direction: SerializableOffset,
    val localName: String? = null,
    val parentId: String? = null,
    val localColor: SerializableColor? = null,
    val localLineStyle: SerializableLineStyle? = null,
    val localStrokeWidth: Float? = null,
    val localSuperscript: String?,
    val creationIndex: Long,
    val showInAxo: Boolean = true,
    val clipToOctant: Boolean? = null,
    val localCustomTrimRange: SerializableLineTrimRange? = null,
)

fun Line3DProjectionAxo.toSerializable(): SerializableLine3DProjectionAxo {
    return SerializableLine3DProjectionAxo(
        id = this.id,
        point = this.p.toSerializable(),
        direction = SerializableOffset.from(this.direction),
        localName = this.localName,
        parentId = this.parent?.id,
        localColor = this.localColor?.let { SerializableColor.from(it) },
        localLineStyle = this.localLineStyle?.toSerializable(),
        localStrokeWidth = this.localStrokeWidth,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        showInAxo = this.showInAxo,
        clipToOctant = this.clipToOctant,
        localCustomTrimRange = this.localCustomTrimRange?.toSerializable(),
    )
}

fun SerializableLine3DProjectionAxo.toRuntime(
    parents: Map<String, Line3D>
): Line3DProjectionAxo {
    val point = this.point.toRuntime(emptyMap())
    val parent = this.parentId?.let { parents[it] }

    return Line3DProjectionAxo(
        p = point,
        dir= this.direction.toOffset(),
        localName = this.localName,
        parent = parent,
        localColor = this.localColor?.toColor(),
        localLineStyle = this.localLineStyle?.toLineStyle(),
        localStrokeWidth = this.localStrokeWidth,
        id = this.id,
        localSuperscript = this.localSuperscript,
        parentId = this.parentId,
        creationIndex = this.creationIndex,
        showInAxoInitial = this.showInAxo,
        clipToOctant = this.clipToOctant,
        localCustomTrimRange = this.localCustomTrimRange?.toRuntime(),
    )
}
