package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.PlaneTraceNarys
import serialization.*

@Serializable
data class SerializablePlaneTraceNarys(
    val id: String,
    val point: SerializablePoint3DNarys,
    val direction: SerializableOffset,
    val parentId: String? = null,
    val localName: String? = null,
    val localColor: SerializableColor? = null,
    val localLineStyle: SerializableLineStyle? = null,
    val localStrokeWidth: Float? = null,
    val clipLineX: Boolean? = null,
    val clipLineZ: Boolean? = null,
    val showInAxo: Boolean = true,
    val isVirtual: Boolean = false,
    val creationIndex: Long
)

fun PlaneTraceNarys.toSerializable(): SerializablePlaneTraceNarys {
    return SerializablePlaneTraceNarys(
        id = this.id,
        point = this.point.toSerializable(),
        direction = SerializableOffset.from(this.direction),
        parentId = this.parent?.id ?: this.parentId,
        localName = this.localName,
        localColor = this.localColor?.let { SerializableColor.from(it) },
        localLineStyle = this.localLineStyle?.toSerializable(),
        localStrokeWidth = this.localStrokeWidth,
        isVirtual = this.isVirtual,
        creationIndex = this.creationIndex,
        clipLineX = this.clipLineX,
        clipLineZ = this.clipLineZ,
        showInAxo = this.showInAxo,
    )
}

fun SerializablePlaneTraceNarys.toRuntime(): PlaneTraceNarys {
    return PlaneTraceNarys(
        point = this.point.toRuntime(emptyMap()),
        direction = this.direction.toOffset(),
        parentId = this.parentId,
        parent = null, // connects later
        localName = this.localName,
        localColor = this.localColor?.toColor(),
        localLineStyle = this.localLineStyle?.toLineStyle(),
        localStrokeWidth = this.localStrokeWidth,
        id = this.id,
        clipLineX = this.clipLineX,
        clipLineZ = this.clipLineZ,
        showInAxo = this.showInAxo,
        isVirtual = this.isVirtual || this.id.endsWith("-narys-virtual"),
        creationIndex = this.creationIndex
    )
}
