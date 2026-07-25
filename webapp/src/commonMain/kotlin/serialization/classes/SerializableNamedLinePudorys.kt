package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.Line3DProjectionPudorys
import model.classes.NamedLinePudorys
import model.classes.Point3DPudorys
import serialization.SerializableOffset

@Serializable
data class SerializableNamedLinePudorys(
    val id: String,
    val point: SerializableOffset,
    val direction: SerializableOffset,
    val name: String = ""
)

fun NamedLinePudorys.toSerializable(): SerializableNamedLinePudorys {
    return SerializableNamedLinePudorys(
        id = this.id,
        point = SerializableOffset(this.point.x, this.point.y),
        direction = SerializableOffset.from(this.direction),
        name = this.name ?: ""
    )
}

fun SerializableNamedLinePudorys.toNamedLine(): Line3DProjectionPudorys {
    return Line3DProjectionPudorys(
        point = Point3DPudorys(
            x = this.point.x,
            y = this.point.y,
            id = this.id,
            name = this.name
        ),
        direction = this.direction.toOffset(),
        localName = this.name,
        id = this.id
    )
}
