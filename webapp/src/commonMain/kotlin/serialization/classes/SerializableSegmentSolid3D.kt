package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.UNASSIGNED_INDEX
import model.classes.SegmentSolid3D
import model.classes.SegmentSolidType
import serialization.SerializableColor

@Serializable
data class SerializableSegmentSolid3D(
    val id: String,
    val type: SegmentSolidType,
    val segmentIds3D: List<String>,
    val vertexPointIds: List<String>,
    val polygonIds: List<String> = emptyList(),
    val apexPointId: String? = null,
    val baseVertexPointIds: List<String> = emptyList(),
    val name: String = "",
    val color: SerializableColor = SerializableColor(0.23f, 0.51f, 0.96f, 0.4f),
    val width: Float = 1f,
    val fillFaces: Boolean = true,
    val creationIndex: Long = UNASSIGNED_INDEX,
    val show: Boolean = true
)

fun SegmentSolid3D.toSerializable(): SerializableSegmentSolid3D =
    SerializableSegmentSolid3D(
        id = id,
        type = type,
        segmentIds3D = segmentIds3D,
        vertexPointIds = vertexPointIds,
        polygonIds = polygonIds,
        apexPointId = apexPointId,
        baseVertexPointIds = baseVertexPointIds,
        name = name,
        color = SerializableColor.from(color),
        width = width,
        fillFaces = fillFaces,
        creationIndex = creationIndex,
        show = show
    )

fun SerializableSegmentSolid3D.toRuntime(): SegmentSolid3D =
    SegmentSolid3D(
        id = id,
        type = type,
        segmentIds3D = segmentIds3D,
        vertexPointIds = vertexPointIds,
        polygonIds = polygonIds,
        apexPointId = apexPointId,
        baseVertexPointIds = baseVertexPointIds,
        name = name,
        color = color.toColor(),
        width = width,
        fillFaces = fillFaces,
        creationIndex = creationIndex,
        show = show
    )
