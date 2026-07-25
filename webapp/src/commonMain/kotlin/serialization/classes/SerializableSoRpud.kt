package serialization.classes

import serialization.toSerializable
import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import model.LineStyle
import model.SolidOfRevolutionPudorys
import serialization.SerializableColor
import serialization.SerializableOffset

@Serializable
data class SerializableSolidOfRevolutionPudorys(
    val id: String,
    val axisLine3DId: String,
    val meridianIdsPudorys: List<String>,

    val mirroredMeridianIdsPudorys: List<String> = emptyList(),

    val rMax: Float,
    val neckRadii: List<Float>,
    val circleIdsPudorys: List<String>,
    val circleIdsNarys: List<String>,

    val sampledMeridianPolylineXY: List<SerializableOffset> = emptyList(),

    val creationIndex: Long,
    val lineStyle: LineStyle,
    val strokeWidth: Float,
    val color: SerializableColor,
    val fillFaces: Boolean = true
)
fun SolidOfRevolutionPudorys.toSerializable(): SerializableSolidOfRevolutionPudorys {
    return SerializableSolidOfRevolutionPudorys(
        id = id,
        axisLine3DId = axisLine3DId,
        meridianIdsPudorys = meridianIdsPudorys,
        mirroredMeridianIdsPudorys = mirroredMeridianIdsPudorys,
        rMax = rMax,
        neckRadii = neckRadii,
        circleIdsPudorys = circleIdsPudorys,
        circleIdsNarys = circleIdsNarys,
        sampledMeridianPolylineXY = sampledMeridianPolylineXY.map {
            SerializableOffset(it.x, it.y)
        },
        creationIndex = creationIndex,
        color = SerializableColor.from(this.color),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        fillFaces = fillFaces,
    )
}
fun SerializableSolidOfRevolutionPudorys.toRuntime(): SolidOfRevolutionPudorys {
    return SolidOfRevolutionPudorys(
        id = id,
        axisLine3DId = axisLine3DId,
        meridianIdsPudorys = meridianIdsPudorys,
        mirroredMeridianIdsPudorys = mirroredMeridianIdsPudorys,
        rMax = rMax,
        neckRadii = neckRadii,
        circleIdsPudorys = circleIdsPudorys,
        circleIdsNarys = circleIdsNarys,
        sampledMeridianPolylineXY = sampledMeridianPolylineXY.map {
            Offset(it.x, it.y)
        },
        creationIndex = creationIndex,
        color = color.toColor(),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        fillFaces = fillFaces,
    )
}