package serialization.classes

import serialization.toSerializable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.AxoOverlayLine
import serialization.*

@Serializable
data class SerializableAOLine (
    val id: String,
    val point: SerializableOffset,
    val direction: SerializableOffset,
    val name: String? = null,
    val color: SerializableColor? = null,
    val lineStyle: SerializableLineStyle? = null,
    val lineWidth: Float? = null,
    val lower: String? = null,
    val upper: String? = null,
    val creationIndex: Long
)
fun AxoOverlayLine.toSerializable(): SerializableAOLine =
    SerializableAOLine(
        id            = this.id,
        point         = this.p.toSerializable(),
        direction     = SerializableOffset.from(this.dir),
        name          = this.name,
        color         = this.color.toSerializable(),
        lineStyle       = this.lineStyle.toSerializable(),
        lineWidth   = this.lineWidth,
        lower         = this.lower,
        upper = this.upper,
        creationIndex = this.creationIndex

    )
fun SerializableAOLine.toRuntime(): AxoOverlayLine {
    return AxoOverlayLine(
        p            = this.point.toOffset(),
        dir        = this.direction.toOffset(),
        name             = this.name,
        color       = this.color?.toColor()?: Color.Black,
        lineStyle   = this.lineStyle?.toLineStyle()?: LineStyle.Solid,
        lineWidth = this.lineWidth?:1f,
        id               = this.id,
        lower = this.lower,
        upper = this.upper,
        creationIndex = this.creationIndex
    )
}
