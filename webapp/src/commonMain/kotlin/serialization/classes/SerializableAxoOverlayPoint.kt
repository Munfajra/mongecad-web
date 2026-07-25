package serialization.classes

import serialization.toSerializable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import model.classes.AxoOverlayPoint
import serialization.SerializableColor
import serialization.SerializableOffset

@Serializable
data class SerializableAxoOverlayPoint(
    val id: String,
    val positionLogical: SerializableOffset,
    val name: String? = null,
    val upper: String? = null,
    val lower: String? = null,

    val color: SerializableColor? = null,
    val width: Float = 1f,

    val creationIndex: Long
)

/* AidPointLogical → serializable DTO */
fun AxoOverlayPoint.toSerializable(): SerializableAxoOverlayPoint =
    SerializableAxoOverlayPoint(
        id = this.id,
        positionLogical = SerializableOffset.from(this.positionLogical),
        name = this.name,
        color = this.color.let { SerializableColor.from(it) },
        width = this.width,
        lower = this.lower,
        upper= this.upper,
        creationIndex = this.creationIndex,
    )

/* DTO → AidPointLogical  */
fun SerializableAxoOverlayPoint.toRuntime(): AxoOverlayPoint =
    AxoOverlayPoint(
        id = this.id,
        positionLogical = this.positionLogical.toOffset(),
        name = this.name,
        color = this.color?.toColor() ?: Color.Gray,
        width = this.width,
        lower = this.lower,
        upper= this.upper,
        creationIndex = this.creationIndex,
    )

