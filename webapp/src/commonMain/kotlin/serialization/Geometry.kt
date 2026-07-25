package serialization

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import model.LineStyle


@Serializable
data class SerializableOffset3D(val x: Float, val y: Float, val z: Float)
@Serializable
enum class SerializableLineStyle { Solid, Dashed, Dotted, DashDot }
fun LineStyle.toSerializable(): SerializableLineStyle = when (this) {
    LineStyle.Solid -> SerializableLineStyle.Solid
    LineStyle.Dashed -> SerializableLineStyle.Dashed
    LineStyle.Dotted -> SerializableLineStyle.Dotted
    LineStyle.DashDot -> SerializableLineStyle.DashDot
}
fun SerializableLineStyle.toLineStyle(): LineStyle = when (this) {
    SerializableLineStyle.Solid -> LineStyle.Solid
    SerializableLineStyle.Dashed -> LineStyle.Dashed
    SerializableLineStyle.Dotted -> LineStyle.Dotted
    SerializableLineStyle.DashDot -> LineStyle.DashDot
}
@Serializable
data class SerializableColor(val red: Float, val green: Float, val blue: Float, val alpha: Float) {
    companion object {
        fun from(color: Color) = SerializableColor(color.red, color.green, color.blue, color.alpha)
    }
    fun toColor() = Color(red, green, blue, alpha)
}

/**
 * Převod barvy do serializovatelné podoby.
 * Dřív v `dialogs/settings/settings.kt`, i když s dialogem nastavení
 * nesouvisí – patří k SerializableColor výše.
 */
fun Color.toSerializable(): SerializableColor = SerializableColor(red, green, blue, alpha)
