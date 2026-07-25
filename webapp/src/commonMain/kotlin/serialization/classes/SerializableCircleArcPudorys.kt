package serialization.classes

import kotlinx.serialization.Serializable
import model.ArcMode
import model.classes.CircleArcPudorys
import serialization.SerializableOffset
import serialization.toSerializable

@Serializable
data class SerializableCircleArcPudorys(
    val circleId: String,
    val a: SerializableOffset,
    val b: SerializableOffset,
    val mode: String
)
fun CircleArcPudorys.toSerializable(): SerializableCircleArcPudorys {
    return SerializableCircleArcPudorys(
        circleId = circleId,
        a = a.toSerializable(),
        b = b.toSerializable(),
        mode = mode.name
    )
}
fun SerializableCircleArcPudorys.toRuntime(): CircleArcPudorys {
    return CircleArcPudorys(
        circleId = circleId,
        a = a.toOffset(),
        b = b.toOffset(),
        mode = runCatching { ArcMode.valueOf(mode) }.getOrDefault(ArcMode.SHORTEST)
    )
}