package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.CurveAxo
import serialization.SerializableColor
import serialization.SerializableOffset3D
import serialization.toOffset3D
import serialization.toS

@Serializable
data class SerializableCurveAxo(
    val id: String,
    val parentId: String?,
    val name: String,
    val color: SerializableColor,
    val strokeWidth: Float,
    val lineStyle: LineStyle,
    val pointIds: List<String>,
    val closed: Boolean,
    val creationIndex: Long,
    val showInAxo: Boolean = true,
    val polyline3D: List<SerializableOffset3D> = emptyList(),
)

fun CurveAxo.toSerializable(): SerializableCurveAxo =
    SerializableCurveAxo(
        id = id,
        parentId = parentId,
        name = name,
        color = SerializableColor.from(color),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        pointIds = pointIds,
        closed = closed,
        creationIndex = creationIndex,
        showInAxo = showInAxo,
        polyline3D = polyline3D?.map { it.toS() } ?: emptyList(),
    )

fun SerializableCurveAxo.toRuntime(): CurveAxo =
    CurveAxo(
        id = id,
        parentId = parentId,
        name = name,
        color = color.toColor(),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        pointIds = pointIds,
        closed = closed,
        creationIndex = creationIndex,
        showInAxoInitial = showInAxo,
        polyline3D = polyline3D.ifEmpty { null }?.map { it.toOffset3D() },
    )
