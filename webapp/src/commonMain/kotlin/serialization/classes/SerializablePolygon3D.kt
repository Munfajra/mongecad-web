package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.LineStyle
import model.classes.RegularPolygon3D
import serialization.SerializableColor

@Serializable
data class SerializableRegularPolygon3D(
    val id: String,
    val name: String,
    val n: Int,
    val planeId: String,
    val vertexPointIds: List<String>,
    val segmentIds3D: List<String>,
    val color: SerializableColor,
    var width: Float,
    var style: LineStyle,

    // projekce – pro snadné mazání/manipulaci
    val vertexPointIdsPudorys: List<String>,
    val vertexPointIdsNarys: List<String>,
    val segmentIdsPudorys: List<String>,
    val segmentIdsNarys: List<String>,
    val segmentIdsAxo: List<String>,
    val creationIndex: Long
)
fun RegularPolygon3D.toSerializable(): SerializableRegularPolygon3D {




    return SerializableRegularPolygon3D(
        id = id,
        name = name,
        n = n,
        planeId = planeId,
        vertexPointIds = vertexPointIds.toList(),
        segmentIds3D = segmentIds3D.toList(),
        color = SerializableColor.from(this.color),
        width = width,
        style   = this.style,
        vertexPointIdsPudorys = vertexPointIdsPudorys.toList(),
        vertexPointIdsNarys = vertexPointIdsNarys.toList(),
        segmentIdsPudorys = segmentIdsPudorys.toList(),
        creationIndex = this.creationIndex,
        segmentIdsNarys = segmentIdsNarys.toList(),
        segmentIdsAxo = segmentIdsAxo.toList(),
    )
}
fun SerializableRegularPolygon3D.toRuntime(): RegularPolygon3D {

    return RegularPolygon3D(
        id = id,
        name = name,
        n = n,
        planeId = planeId,
        vertexPointIds = vertexPointIds,
        segmentIds3D = segmentIds3D,
        vertexPointIdsPudorys = vertexPointIdsPudorys,
        vertexPointIdsNarys = vertexPointIdsNarys,
        segmentIdsPudorys = segmentIdsPudorys,
        segmentIdsNarys = segmentIdsNarys,
        color = color.toColor(),
        width = width,
        style = style,
        creationIndex = this.creationIndex,
        segmentIdsAxo = this.segmentIdsAxo,
    )
}