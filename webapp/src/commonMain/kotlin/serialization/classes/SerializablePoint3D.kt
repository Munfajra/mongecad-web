package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.Point3D
import serialization.SerializableColor


@Serializable
data class SerializablePoint3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val name: String,
    val color: SerializableColor = SerializableColor(0f, 0f, 0f, 1f),
    val width: Float = 1f,
    val id: String,
    val superscript: String?,
    val creationIndex: Long,

    )
fun Point3D.toSerializable(): SerializablePoint3D {
    return SerializablePoint3D(
        x = this.x,
        y = this.y,
        z = this.z,
        name = this.name,
        color = SerializableColor.from(this.color),
        width = this.width,
        id = this.id,
        superscript = this.superscript,
        creationIndex = this.creationIndex,
    )
}
fun SerializablePoint3D.toRuntime(): Point3D {
    return Point3D(
        x = x,
        y = y,
        z = z,
        name = name,
        color = color.toColor(),
        width = width,
        id = id,
        superscript = superscript,
        creationIndex = this.creationIndex
    )
}
