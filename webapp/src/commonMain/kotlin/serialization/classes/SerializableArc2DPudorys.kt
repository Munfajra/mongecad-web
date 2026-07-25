package serialization.classes

import kotlinx.serialization.Serializable
import model.classes.Arc2DPudorys
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable


@Serializable
data class SerializableArc2DPudorys(
    val center: SerializablePoint3DPudorys,
    val radius: Float,

    val startRad: Float,
    val endRad: Float,

    var name: String = "oblouk",
    var color: SerializableColor,
    var lineStyle: SerializableLineStyle,
    var strokeWidth: Float,
    val clockwise: Boolean,
    val creationIndex: Long,
    val id: String,
    val showInAxo: Boolean = true
)

fun Arc2DPudorys.toSerializable(): SerializableArc2DPudorys {
    return SerializableArc2DPudorys(
        center = this.center.toSerializable(), // žádné ID!
        radius = this.radius,
        startRad = this.startRad,
        endRad = this.endRad,
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
fun SerializableArc2DPudorys.toRuntime(): Arc2DPudorys{
    return Arc2DPudorys(
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
