package serialization.classes

import kotlinx.serialization.Serializable
import model.ArcMode
import model.classes.CircleArcNarys
import serialization.SerializableOffset
import serialization.toSerializable

@Serializable
data class SerializableCircleArcNarys(
    val circleId: String,
    val a: SerializableOffset,
    val b: SerializableOffset,
    val mode: String
)
fun CircleArcNarys.toSerializable(): SerializableCircleArcNarys {
    return SerializableCircleArcNarys(
        circleId = circleId,
        a = a.toSerializable(),
        b = b.toSerializable(),
        mode = mode.name
    )
}
fun SerializableCircleArcNarys.toRuntime(): CircleArcNarys {
    return CircleArcNarys(
        circleId = circleId,
        a = a.toOffset(),
        b = b.toOffset(),
        mode = runCatching { ArcMode.valueOf(mode) }.getOrDefault(ArcMode.SHORTEST)
    )
}