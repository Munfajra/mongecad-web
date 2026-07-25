package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.SphereSurface3D
import serialization.SerializableColor

@Serializable
data class SphereSurface3DSerializable(
    val id: String,
    val centerPoint3DId: String,
    val radius: Float,
    val name: String,
    val color: SerializableColor,
    val creationIndex: Long,
    val fillFaces: Boolean = true
)


fun SphereSurface3D.toSerializable() = SphereSurface3DSerializable(
    id = id,
    centerPoint3DId = centerPoint3DId,
    radius = radius,
    name = name,
    creationIndex = this.creationIndex,
    color = SerializableColor.from(color),
    fillFaces = fillFaces
)

fun SphereSurface3DSerializable.toRuntime() = SphereSurface3D(
    centerPoint3DId = centerPoint3DId,
    radius = radius,
    name = name,
    color = color.toColor(),
    id = id,
    creationIndex = this.creationIndex,
    fillFaces = fillFaces
)