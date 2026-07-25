package serialization.classes

import kotlinx.serialization.Serializable
import model.classes.Plane3D
import model.classes.PlaneEquation
import serialization.SerializableColor
import serialization.SerializableLineStyle
import serialization.toSerializable


@Serializable
data class SerializablePlane3D(
    val id: String,
    val name: String,
    val tracePudorysId: String,
    val traceNarysId: String,
    val traceBokorysId: String? = null,
    val equation: PlaneEquation? = null,
    val color: SerializableColor = SerializableColor(0f, 0f, 0f, 1f),
    val lineStyle: SerializableLineStyle = SerializableLineStyle.Solid,
    val strokeWidth: Float = 1f,
    val creationIndex: Long
)
fun Plane3D.toSerializable(): SerializablePlane3D {
    return SerializablePlane3D(
        id = this.id,
        name = this.name,
        tracePudorysId = this.tracePudorys.id,
        traceNarysId = this.traceNarys.id,
        traceBokorysId = this.traceBokorys.id,
        equation = this.equation, // PlaneEquation je @Serializable
        color = SerializableColor.from(this.color),
        lineStyle = this.lineStyle.toSerializable(),
        strokeWidth = this.strokeWidth,
        creationIndex = this.creationIndex
    )
}
