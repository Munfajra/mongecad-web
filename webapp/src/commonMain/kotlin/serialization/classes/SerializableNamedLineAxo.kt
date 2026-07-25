package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.Line3DProjectionAxo
import model.classes.NamedLineAxo
import model.classes.Point3DAxo
import serialization.SerializableOffset

@Serializable
data class SerializableNamedLineAxo(
    val id: String,
    val point: SerializableOffset,
    val direction: SerializableOffset,
    val name: String = ""
)

fun NamedLineAxo.toSerializable(): SerializableNamedLineAxo {
    return SerializableNamedLineAxo(
        id = this.id,
        point = SerializableOffset(this.point.x, this.point.y),
        direction = SerializableOffset.Companion.from(this.direction),
        name = this.name ?: ""
    )
}

fun SerializableNamedLineAxo.toNamedLine(): Line3DProjectionAxo {
    return Line3DProjectionAxo(
        p = Point3DAxo(
            x = this.point.x,
            y = this.point.y,
            id = this.id,
            name = this.name
        ),
        dir = this.direction.toOffset(),
        localName = this.name,
        id = this.id
    )
}