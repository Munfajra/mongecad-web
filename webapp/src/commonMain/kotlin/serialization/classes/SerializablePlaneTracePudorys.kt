package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.PlaneTracePudorys
import serialization.*

@Serializable
data class SerializablePlaneTracePudorys(
    val id: String,
    val point: SerializablePoint3DPudorys,
    val direction: SerializableOffset,
    val parentId: String? = null,
    val localName: String? = null,
    val localColor: SerializableColor? = null,
    val localLineStyle: SerializableLineStyle? = null,
    val localStrokeWidth: Float? = null,
    val clipLineX: Boolean? = null,
    val clipLineY: Boolean? = null,
    val showInAxo: Boolean = true,
    val isVirtual: Boolean = false,
    val creationIndex: Long
)
fun PlaneTracePudorys.toSerializable(): SerializablePlaneTracePudorys {
    return SerializablePlaneTracePudorys(
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
        clipLineY = this.clipLineY,
        showInAxo = this.showInAxo
    )
}

fun SerializablePlaneTracePudorys.toRuntime(): PlaneTracePudorys {
    return PlaneTracePudorys(
        point = this.point.toRuntime(emptyMap()),
        direction = this.direction.toOffset(),
        parentId = this.parentId,
        parent = null,
        localName = this.localName,
        localColor = this.localColor?.toColor(),
        localLineStyle = this.localLineStyle?.toLineStyle(),
        localStrokeWidth = this.localStrokeWidth,
        id = this.id,
        clipLineX = this.clipLineX,
        clipLineY = this.clipLineY,
        showInAxo = this.showInAxo,
        isVirtual = this.isVirtual || this.id.endsWith("-pudorys-virtual"),
        creationIndex = this.creationIndex
    )
}
