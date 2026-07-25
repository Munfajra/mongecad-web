package serialization.classes

import kotlinx.serialization.Serializable
import model.classes.Segment3D
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable

@Serializable
data class SerializableSegment3D(
    val id: String,
    val name: String,
    val start: SerializablePoint3D,
    val end: SerializablePoint3D,
    val color: SerializableColor = SerializableColor(0f, 0f, 0f, 1f),
    val lineStyle: SerializableLineStyle = SerializableLineStyle.Solid,
    val strokeWidth: Float = 1f,
    val creationIndex: Long
)
fun Segment3D.toSerializable(): SerializableSegment3D {
    return SerializableSegment3D(
        id = this.id,
        name = this.name,
        start = this.start.toSerializable(),
        end = this.end.toSerializable(),
        color = SerializableColor.from(this.color),
        lineStyle = this.lineStyle.toSerializable(),
        creationIndex = this.creationIndex,
        strokeWidth = this.strokeWidth
    )
}
fun SerializableSegment3D.toRuntime(): Segment3D {
    return Segment3D(
        start = this.start.toRuntime(),
        end = this.end.toRuntime(),
        name = this.name,
        color = this.color.toColor(),
        lineStyle = this.lineStyle.toLineStyle(),
        strokeWidth = this.strokeWidth,
        id = this.id,
        creationIndex = this.creationIndex
    )
}