package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.Offset3D
import model.Point3D
import model.classes.Line3D
import serialization.*

@Serializable
data class SerializableLine3D(
    val id: String,
    val name: String,
    val start: Offset3D,
    val direction: Offset3D,
    val labelOffset: SerializableOffset = SerializableOffset(0f, 0f),
    val color: SerializableColor = SerializableColor(0f, 0f, 0f, 1f),
    val lineStyle: SerializableLineStyle = SerializableLineStyle.Solid,
    val strokeWidth: Float = 1f,
    val superscript: String?,
    val creationIndex: Long,
    val customTrimRange: SerializableLineTrimRange? = null
)
fun Line3D.toSerializable(): SerializableLine3D {
    return SerializableLine3D(
        id = this.id,
        name = this.name,
        start = Offset3D(this.start.x, this.start.y, this.start.z),
        direction = this.direction,
        labelOffset = SerializableOffset.from(this.labelOffset),
        color = SerializableColor.from(this.color),
        lineStyle = this.lineStyle.toSerializable(),
        strokeWidth = this.strokeWidth,
        superscript = this.superscript,
        creationIndex = this.creationIndex,
        customTrimRange = this.customTrimRange?.toSerializable()
    )
}
fun SerializableLine3D.toRuntime(): Line3D {
    return Line3D(
        id = this.id,
        name = this.name,
        start = Point3D(start.x, start.y, start.z, name, color.toColor(), strokeWidth),
        direction = this.direction,
        labelOffset = this.labelOffset.toOffset(),
        color = this.color.toColor(),
        lineStyle = this.lineStyle.toLineStyle(),
        strokeWidth = this.strokeWidth,
        superscript = this.superscript,
        creationIndex = this.creationIndex,
        customTrimRange = this.customTrimRange?.toRuntime()
    )
}
