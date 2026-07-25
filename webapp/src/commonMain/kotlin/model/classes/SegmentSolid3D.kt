package model.classes
import androidx.compose.ui.graphics.Color
import model.UNASSIGNED_INDEX
import utils.UUID
enum class SegmentSolidType {
    HRANOL,
    JEHLAN,
    MNOHOSTEN
}
data class SegmentSolid3D(
    val id: String = UUID.randomUUID().toString(),
    val type: SegmentSolidType,
    val segmentIds3D: List<String>,
    val vertexPointIds: List<String>,
    val polygonIds: List<String> = emptyList(),
    val apexPointId: String? = null,
    val baseVertexPointIds: List<String> = emptyList(),
    val name: String = "",
    var color: Color = Color(0x663B82F6),
    var width: Float = 1f,
    var fillFaces: Boolean = true,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true
)
