package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.HelpLinePudorys
import model.classes.Point3DPudorys
import serialization.*

@Serializable
data class SerializableHelpLinePudorys(
    val id: String,
    val point: SerializablePoint3DPudorys,
    val direction: SerializableOffset,
    val name: String? = null,
    val color: SerializableColor? = null,
    val lineStyle: SerializableLineStyle? = null,
    val strokeWidth: Float? = null,
    val clipLineX: Boolean? = null,
    val clipLineY: Boolean? = null,
    val localSuperscript: String? = null,
    val creationIndex: Long,
    val customTrimRange: SerializableLineTrimRange? = null
)
fun HelpLinePudorys.toSerializable(): SerializableHelpLinePudorys =
    SerializableHelpLinePudorys(
        id            = this.id,
        point         = this.point.toSerializable(),
        direction     = SerializableOffset.from(this.direction),
        name          = this.name,
        color         = this.localColor?.let { SerializableColor.from(it) },
        lineStyle     = this.localLineStyle?.toSerializable(),
        strokeWidth   = this.localStrokeWidth,
        clipLineX = this.clipLineX,
        clipLineY = this.clipLineY,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        customTrimRange = this.customTrimRange?.toSerializable()
    )

fun SerializablePoint3DPudorys.toPoint3DPudorys(): Point3DPudorys =
    Point3DPudorys(
        x      = this.x,
        y      = this.y,
        name   = this.name,
        parent = null,           // pomocný bod není propojen s 3D
        id     = this.id
    )
fun SerializableHelpLinePudorys.toRuntime(): HelpLinePudorys {
    val point = this.point.toPoint3DPudorys()
    return HelpLinePudorys(
        point            = point,
        direction        = this.direction.toOffset(),
        name             = this.name,
        localColor       = this.color?.toColor(),
        localLineStyle   = this.lineStyle?.toLineStyle(),
        localStrokeWidth = this.strokeWidth,
        id               = this.id,
        clipLineX = this.clipLineX,
        clipLineY = this.clipLineY,
        parentAny = null,
        localSuperscript = this.localSuperscript,
        creationIndex = this.creationIndex,
        customTrimRange = this.customTrimRange?.toRuntime()
    )
}
