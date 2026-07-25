package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.PlaneTraceBokorys
import serialization.*
import serialization.SerializableColor.Companion

@Serializable
data class SerializablePlaneTraceBokorys(
    val id: String,
    val point: SerializablePoint3DBokorys,
    val direction: SerializableOffset,
    val parentId: String? = null,
    val localName: String? = null,
    val localColor: SerializableColor? = null,
    val localLineStyle: SerializableLineStyle? = null,
    val localStrokeWidth: Float? = null,
    val isVirtual: Boolean = false,
    val creationIndex: Long,
    val clipLineY: Boolean? = null,
    val clipLineZ: Boolean? = null,
    val showInAxo: Boolean = true,
)

fun PlaneTraceBokorys.toSerializable(): SerializablePlaneTraceBokorys {
    return SerializablePlaneTraceBokorys(
        id = this.id,
        point = this.point.toSerializable(),
        direction = SerializableOffset.from(this.direction),
        parentId = this.parent?.id ?: this.parentId,
        localName = this.localName,
        localColor = this.localColor?.let { Companion.from(it) },
        localLineStyle = this.localLineStyle?.toSerializable(),
        localStrokeWidth = this.localStrokeWidth,
        isVirtual = this.isVirtual,
        creationIndex = this.creationIndex,
        clipLineY = this.clipLineY,
        clipLineZ = this.clipLineZ,
        showInAxo = this.showInAxo,
    )
}

fun SerializablePlaneTraceBokorys.toRuntime(): PlaneTraceBokorys {
    return PlaneTraceBokorys(
        point = this.point.toRuntime(emptyMap()),
        direction = this.direction.toOffset(),
        parentId = this.parentId,
        parent = null, // bude později propojeno v Plane3D
        localName = this.localName,
        localColor = this.localColor?.toColor(),
        localLineStyle = this.localLineStyle?.toLineStyle(),
        localStrokeWidth = this.localStrokeWidth,
        id = this.id,
        isVirtual = this.isVirtual || this.id.endsWith("-bokorys-virtual"),
        creationIndex = this.creationIndex,
        clipLineY = this.clipLineY,
        clipLineZ = this.clipLineZ,
        showInAxo = this.showInAxo,
    )
}
