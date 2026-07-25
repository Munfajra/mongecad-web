package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.Curve3D
import serialization.SerializableColor
import serialization.SerializableOffset3D
import serialization.toOffset3D
import serialization.toS

@Serializable
data class SerializableCurve3D(
    val id: String,
    val name: String,
    val color: SerializableColor,
    val strokeWidth: Float,
    val pointIds: List<String>,
    val closed: Boolean,
    val lineStyle: LineStyle,
    val creationIndex: Long,
    val polyline3D: List<SerializableOffset3D> = emptyList(),
)
fun Curve3D.toSerializable(): SerializableCurve3D =
    SerializableCurve3D(
        id = id,
        name = name,
        color= SerializableColor.from(this.color),
        strokeWidth = this.strokeWidth,
        pointIds = this.pointIds,
        closed = this.closed,
        lineStyle = this.lineStyle,
        creationIndex = this.creationIndex,
        polyline3D = this.polyline3D?.map { it.toS() } ?: emptyList(),
    )
fun SerializableCurve3D.toRuntime(): Curve3D =
    Curve3D(
        id = id,
        name = name,
        color = color.toColor(),
        strokeWidth = this.strokeWidth,
        pointIds = this.pointIds,
        closed = this.closed,
        lineStyle = this.lineStyle,
        creationIndex = this.creationIndex,
        polyline3D = this.polyline3D.ifEmpty { null }?.map { it.toOffset3D() },
    )
