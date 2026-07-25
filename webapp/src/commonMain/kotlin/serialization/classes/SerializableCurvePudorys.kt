package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.CurvePudRef
import model.classes.CurvePudorys
import serialization.SerializableColor
import serialization.SerializableOffset

@Serializable
sealed class SerializableCurvePudRef {

    @Serializable
    @SerialName("p")
    data class P(val pointId: String) : SerializableCurvePudRef()

    @Serializable
    @SerialName("aid")
    data class A(val aidId: String) : SerializableCurvePudRef()
}


@Serializable
data class SerializableCurvePudorys(
    val id: String,
    val parentId: String?,
    val name: String,
    val color: SerializableColor,
    val strokeWidth: Float,
    val lineStyle: LineStyle,
    val points: List<SerializableCurvePudRef>,
    val closed: Boolean,
    val creationIndex: Long,
    val showInAxo: Boolean = true,
    val polylineLocal: List<SerializableOffset> = emptyList(),
)

fun CurvePudorys.toSerializable(): SerializableCurvePudorys =
    SerializableCurvePudorys(
        id = id,
        parentId = parentId,
        name = name,
        color= SerializableColor.from(this.color),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        closed = closed,
        points = points.map { ref ->
            when (ref) {
                is CurvePudRef.P -> SerializableCurvePudRef.P(ref.pointId)
                is CurvePudRef.A -> SerializableCurvePudRef.A(ref.aidId)
            }
        },
        creationIndex = this.creationIndex,
        showInAxo = this.showInAxo,
        polylineLocal = this.polylineLocal?.map { SerializableOffset.from(it) } ?: emptyList(),
    )

fun SerializableCurvePudorys.toRuntime(): CurvePudorys =
    CurvePudorys(
        id = id,
        parentId = parentId,
        name = name,
        color = color.toColor(),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        closed = closed,
        points = points.map { ref ->
            when (ref) {
                is SerializableCurvePudRef.P -> CurvePudRef.P(ref.pointId)
                is SerializableCurvePudRef.A -> CurvePudRef.A(ref.aidId)
            }
        },
        creationIndex = this.creationIndex,
        showInAxoInitial = this.showInAxo,
        polylineLocal = this.polylineLocal.ifEmpty { null }?.map { it.toOffset() },
    )
