package serialization.classes

import serialization.toSerializable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import model.classes.AidPointLogical
import serialization.SerializableColor


@Serializable
data class SerializableAidPointLogical(
    val x: Float,
    val y: Float,
    val name: String? = null,
    val color: SerializableColor? = null,
    val width: Float = 1f,
    val id: String,
    val lowerSuperscript: String? = null,
    val upperSuperscript: String? = null,
    val creationIndex: Long
)
/* AidPointLogical → serializable DTO */
fun AidPointLogical.toSerializable(): SerializableAidPointLogical =
    SerializableAidPointLogical(
        x = this.x,
        y = this.y,
        name = this.name,
        color = this.color.let { SerializableColor.from(it) },
        width = this.width,
        id = this.id,
        lowerSuperscript = this.lowerSuperscript,
        upperSuperscript= this.upperSuperscript,
        creationIndex = this.creationIndex,
    )

/* DTO → AidPointLogical  */
fun SerializableAidPointLogical.toRuntime(): AidPointLogical =
    AidPointLogical(
        x = this.x,
        y = this.y,
        name = this.name,
        color = this.color?.toColor() ?: Color.Gray,
        width = this.width,
        id = this.id,
        lowerSuperscript = this.lowerSuperscript,
        upperSuperscript= this.upperSuperscript,
        creationIndex = this.creationIndex,
    )

