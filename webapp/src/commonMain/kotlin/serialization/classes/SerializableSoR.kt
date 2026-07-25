package serialization.classes

import serialization.toSerializable
import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import model.LineStyle
import model.SolidOfRevolutionNarys
import serialization.SerializableColor
import serialization.SerializableOffset

@Serializable
data class SerializableSolidOfRevolutionNarys(
    val id: String,
    val axisLine3DId: String,
    val meridianIdsNarys: List<String>,

    val mirroredMeridianIdsNarys: List<String> = emptyList(),

    val rMax: Float,
    val neckRadii: List<Float>,
    val circleIdsPudorys: List<String>,
    val circleIdsNarys: List<String>,

    val sampledMeridianPolylineXZ: List<SerializableOffset> = emptyList(),

    val creationIndex: Long,
    val lineStyle: LineStyle,
    val strokeWidth: Float,
    val color: SerializableColor,
    val fillFaces: Boolean = true
)
fun SolidOfRevolutionNarys.toSerializable(): SerializableSolidOfRevolutionNarys {
    return SerializableSolidOfRevolutionNarys(
        id = id,
        axisLine3DId = axisLine3DId,
        meridianIdsNarys = meridianIdsNarys,
        mirroredMeridianIdsNarys = mirroredMeridianIdsNarys,
        rMax = rMax,
        neckRadii = neckRadii,
        circleIdsPudorys = circleIdsPudorys,
        circleIdsNarys = circleIdsNarys,
        sampledMeridianPolylineXZ = sampledMeridianPolylineXZ.map {
            SerializableOffset(it.x, it.y)
        },
        creationIndex = creationIndex,
        color = SerializableColor.from(this.color),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        fillFaces = fillFaces,
    )
}
fun SerializableSolidOfRevolutionNarys.toRuntime(): SolidOfRevolutionNarys {
    return SolidOfRevolutionNarys(
        id = id,
        axisLine3DId = axisLine3DId,
        meridianIdsNarys = meridianIdsNarys,
        mirroredMeridianIdsNarys = mirroredMeridianIdsNarys,
        rMax = rMax,
        neckRadii = neckRadii,
        circleIdsPudorys = circleIdsPudorys,
        circleIdsNarys = circleIdsNarys,
        sampledMeridianPolylineXZ = sampledMeridianPolylineXZ.map {
            Offset(it.x, it.y)
        },
        creationIndex = creationIndex,
        color = color.toColor(),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle,
        fillFaces = fillFaces,
    )
}