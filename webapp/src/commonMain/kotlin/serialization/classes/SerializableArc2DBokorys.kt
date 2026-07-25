package serialization.classes

import kotlinx.serialization.Serializable
import model.classes.Arc2DBokorys
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable

@Serializable
data class SerializableArc2DBokorys(
    val center: SerializablePoint3DBokorys,
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
fun Arc2DBokorys.toSerializable(): SerializableArc2DBokorys {
    return SerializableArc2DBokorys(
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
fun SerializableArc2DBokorys.toRuntime(): Arc2DBokorys {
    return Arc2DBokorys(
        center = this.center.toRuntime(),
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
