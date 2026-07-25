package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.CurveBokorys
import serialization.SerializableColor
import serialization.SerializableOffset

@Serializable
data class SerializableCurveBokorys(
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
    val polylineLocal: List<SerializableOffset> = emptyList(),
)

fun CurveBokorys.toSerializable(): SerializableCurveBokorys =
    SerializableCurveBokorys(
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
        polylineLocal = polylineLocal?.map { SerializableOffset.from(it) } ?: emptyList(),
    )

fun SerializableCurveBokorys.toRuntime(): CurveBokorys =
    CurveBokorys(
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
        polylineLocal = polylineLocal.ifEmpty { null }?.map { it.toOffset() },
    )
