package serialization.classes

import kotlinx.serialization.Serializable
import model.classes.PlanePolygon2D
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toLineStyle
import serialization.toSerializable

@Serializable
data class SerializablePlanePolygon2D(
    val id: String,
    val name: String = "P",
    val vertexPointIdsPudorys: List<String>,
    val vertexAidPointIds: List<String> = emptyList(),
    val segmentIdsPudorys: List<String>,
    val color: SerializableColor,
    val width: Float,
    val style: SerializableLineStyle,
    val creationIndex: Long,
    val show: Boolean = true
)

fun PlanePolygon2D.toSerializable() = SerializablePlanePolygon2D(
    id = id,
    name = name,
    vertexPointIdsPudorys = vertexPointIdsPudorys,
    vertexAidPointIds = vertexAidPointIds,
    segmentIdsPudorys = segmentIdsPudorys,
    color = SerializableColor.from(color),
    width = width,
    style = style.toSerializable(),
    creationIndex = creationIndex,
    show = show
)

fun SerializablePlanePolygon2D.toRuntime() = PlanePolygon2D(
    id = id,
    name = name,
    vertexPointIdsPudorys = vertexPointIdsPudorys,
    vertexAidPointIds = vertexAidPointIds,
    segmentIdsPudorys = segmentIdsPudorys,
    color = color.toColor(),
    width = width,
    style = style.toLineStyle(),
    creationIndex = creationIndex,
    show = show
)
