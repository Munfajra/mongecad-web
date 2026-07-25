package serialization.classes

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import model.classes.ArcAxoOverlay
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable

@Serializable
data class SerializableArcAxoOverlay(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,

    val startRad: Float,
    val endRad: Float,

    var name: String = "oblouk",
    var color: SerializableColor,
    var lineStyle: SerializableLineStyle,
    var strokeWidth: Float,
    val clockwise: Boolean,
    val creationIndex: Long,
    val id: String
)

fun ArcAxoOverlay.toSerializable(): SerializableArcAxoOverlay {
    return SerializableArcAxoOverlay(
        centerX = this.center.x,
        centerY = this.center.y,
        radius = this.radius,
        startRad = this.startRad,
        endRad = this.endRad,
        name = this.name,
        color = SerializableColor.from(this.color),
        lineStyle = this.lineStyle.toSerializable(),
        strokeWidth = this.strokeWidth,
        clockwise = this.clockwise,
        creationIndex = this.creationIndex,
        id = this.id
    )
}

fun SerializableArcAxoOverlay.toRuntime(): ArcAxoOverlay {
    return ArcAxoOverlay(
        center = Offset(this.centerX, this.centerY),
        radius = this.radius,
        startRad = this.startRad,
        endRad = this.endRad,
        name = this.name,
        color = this.color.toColor(),
        lineStyle = this.lineStyle.toLineStyle(),
        strokeWidth = this.strokeWidth,
        clockwise = this.clockwise,
        id = this.id,
        creationIndex = this.creationIndex
    )
}
