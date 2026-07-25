package serialization.classes

import kotlinx.serialization.Serializable
import model.classes.Arc2DNarys
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable

@Serializable
data class SerializableArc2DNarys(
    val center: SerializablePoint3DNarys,
    val radius: Float,

    // ✅ absolutní radiány
    val startRad: Float,
    val endRad: Float,

    val name: String,
    val color: SerializableColor,
    val lineStyle: SerializableLineStyle,
    val strokeWidth: Float,
    val clockwise: Boolean,
    val creationIndex: Long,
    val id: String,
    val showInAxo: Boolean = true
)
fun Arc2DNarys.toSerializable(): SerializableArc2DNarys {
    return SerializableArc2DNarys(
        center = this.center.toSerializable(),
        radius = this.radius,
        startRad = this.startRad,
        endRad   = this.endRad,
        name = this.name,
        color = SerializableColor.from(this.color),
        lineStyle = this.lineStyle.toSerializable(),
        strokeWidth = this.strokeWidth,
        clockwise = this.clockwise,
        creationIndex = this.creationIndex,
        id = this.id,
        showInAxo = this.showInAxo
    )
}
fun SerializableArc2DNarys.toRuntime(): Arc2DNarys {
    return Arc2DNarys(
        center = this.center.toRuntime(emptyMap()),
        radius = this.radius,
        startRad = this.startRad,
        endRad   = this.endRad,
        name = this.name,
        color = this.color.toColor(),
        lineStyle = this.lineStyle.toLineStyle(),
        strokeWidth = this.strokeWidth,
        clockwise = this.clockwise,
        id = this.id,
        creationIndex = this.creationIndex,
        showInAxoInitial = this.showInAxo
    ).also { it.showInAxo = this.showInAxo }
}
