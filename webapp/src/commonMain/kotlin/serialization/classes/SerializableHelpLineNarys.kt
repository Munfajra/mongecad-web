package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.HelpLineNarys
import serialization.*

@Serializable
data class SerializableHelpLineNarys(
    val id: String,
    val point: SerializablePoint3DNarys, // ✅ místo pointId
    val direction: SerializableOffset,
    val name: String? = null,
    val color: SerializableColor? = null,
    val lineStyle: SerializableLineStyle? = null,
    val strokeWidth: Float? = null,
    val clipLineX: Boolean? = null,
    val clipLineZ: Boolean? = null,
    val localSuperscript: String? = null,
    val creationIndex: Long,
    val customTrimRange: SerializableLineTrimRange? = null
)

fun HelpLineNarys.toSerializable(): SerializableHelpLineNarys {
    return SerializableHelpLineNarys(
        id = this.id,
        point = this.point.toSerializable(), // ✅
        direction = SerializableOffset.Companion.from(this.direction),
        name = this.name,
        color = this.localColor?.let { SerializableColor.Companion.from(it) },
        lineStyle = this.localLineStyle?.toSerializable(),
        strokeWidth = this.localStrokeWidth,
        clipLineX = this.clipLineX,
        clipLineZ = this.clipLineZ,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        customTrimRange = this.customTrimRange?.toSerializable()
    )
}
fun SerializableHelpLineNarys.toRuntime(): HelpLineNarys {
    val point = this.point.toRuntime() // ✅ nová bezparametrová verze

    return HelpLineNarys(
        point = point,
        direction = this.direction.toOffset(),
        name = this.name,
        localColor = this.color?.toColor(),
        localLineStyle = this.lineStyle?.toLineStyle(),
        localStrokeWidth = this.strokeWidth,
        id = this.id,
        clipLineX = this.clipLineX,
        clipLineZ = this.clipLineZ,
        parentAny = null,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        customTrimRange = this.customTrimRange?.toRuntime()
    )
}
