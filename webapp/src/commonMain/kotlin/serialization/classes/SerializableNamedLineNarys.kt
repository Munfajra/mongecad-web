package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.Line3DProjectionNarys
import model.classes.NamedLineNarys
import model.classes.Point3DNarys
import serialization.SerializableOffset
import serialization.SerializableOffset.Companion

@Serializable
data class SerializableNamedLineNarys(
    val id: String,
    val point: SerializableOffset,
    val direction: SerializableOffset,
    val name: String = ""
)

fun NamedLineNarys.toSerializable(): SerializableNamedLineNarys {
    return SerializableNamedLineNarys(
        id = this.id,
        point = SerializableOffset(this.point.x, this.point.z),
        direction = Companion.from(this.direction),
        name = this.name ?: ""
    )
}

fun SerializableNamedLineNarys.toNamedLine(): Line3DProjectionNarys {
    return Line3DProjectionNarys(
        point = Point3DNarys(
            x = this.point.x,
            z = this.point.y,
            id = this.id,
            name = this.name
        ),
        direction = this.direction.toOffset(),
        localName = this.name,
        id = this.id
    )
}

