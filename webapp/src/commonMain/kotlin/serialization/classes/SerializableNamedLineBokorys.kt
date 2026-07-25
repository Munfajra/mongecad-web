package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.Line3DProjectionBokorys
import model.classes.NamedLineBokorys
import model.classes.Point3DBokorys
import serialization.SerializableOffset

@Serializable
data class SerializableNamedLineBokorys(
    val id: String,
    val point: SerializableOffset,
    val direction: SerializableOffset,
    val name: String = ""
)

fun NamedLineBokorys.toSerializable(): SerializableNamedLineBokorys {
    return SerializableNamedLineBokorys(
        id = this.id,
        point = SerializableOffset(this.point.y, this.point.z),
        direction = SerializableOffset.from(this.direction),
        name = this.name ?: ""
    )
}

fun SerializableNamedLineBokorys.toNamedLine(): Line3DProjectionBokorys {
    return Line3DProjectionBokorys(
        point = Point3DBokorys(
            y = this.point.x,
            z = this.point.y,
            id = this.id,
            name = this.name
        ),
        direction = this.direction.toOffset(),
        localName = this.name,
        id = this.id
    )
}

