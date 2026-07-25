package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.CurveNarys
import serialization.SerializableColor
import serialization.SerializableOffset

@Serializable
data class SerializableCurveNarys(
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

fun CurveNarys.toSerializable(): SerializableCurveNarys =
    SerializableCurveNarys(
        id = id,
        parentId = parentId,
        name = name,
        color = SerializableColor.from(this.color),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        pointIds = pointIds,
        closed = closed,
        creationIndex = this.creationIndex,
        showInAxo = this.showInAxo,
        polylineLocal = this.polylineLocal?.map { SerializableOffset.from(it) } ?: emptyList(),
    )

fun SerializableCurveNarys.toRuntime(): CurveNarys =
    CurveNarys(
        id = id,
        parentId = parentId,
        name = name,
        color = color.toColor(),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        pointIds = pointIds,
        closed = closed,
        creationIndex = this.creationIndex,
        showInAxoInitial = this.showInAxo,
        polylineLocal = this.polylineLocal.ifEmpty { null }?.map { it.toOffset() },
    )
