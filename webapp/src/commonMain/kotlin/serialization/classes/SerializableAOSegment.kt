package serialization.classes

import serialization.toSerializable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.AxoOverlaySegment
import serialization.*

@Serializable
data class SerializableAOSegment (
    val id: String,
    val start: SerializableOffset,
    val end: SerializableOffset,
    val name: String? = null,
    val color: SerializableColor? = null,
    val lineStyle: SerializableLineStyle? = null,
    val lineWidth: Float? = null,
    val creationIndex: Long
)
fun AxoOverlaySegment.toSerializable(): SerializableAOSegment =
    SerializableAOSegment(
        id            = this.id,
        start         = this.start.toSerializable(),
        end           = this.end.toSerializable(),
        name          = this.name,
        color         = this.color.toSerializable(),
        lineStyle     = this.lineStyle.toSerializable(),
        lineWidth     = this.lineWidth,
        creationIndex = this.creationIndex

    )
fun SerializableAOSegment.toRuntime(): AxoOverlaySegment{
    return AxoOverlaySegment(
        start            = this.start.toOffset(),
        end              = this.end.toOffset(),
        name             = this.name,
        color            = this.color?.toColor()?: Color.Black,
        lineStyle        = this.lineStyle?.toLineStyle()?: LineStyle.Solid,
        lineWidth        = this.lineWidth?:1f,
        id               = this.id,
        creationIndex    = this.creationIndex
    )
}
