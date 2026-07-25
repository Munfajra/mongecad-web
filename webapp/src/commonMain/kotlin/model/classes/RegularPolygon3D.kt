package model.classes
import utils.UUID
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
data class RegularPolygon3D(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val n: Int,
    val planeId: String,
    val vertexPointIds: List<String>,
    val segmentIds3D: List<String>,
    var color: Color = Color.Black,
    var width: Float = 1f,
    var style: LineStyle,
    // projekce – pro snadné mazání/manipulaci
    val vertexPointIdsPudorys: List<String>,
    val vertexPointIdsNarys: List<String>,
    val segmentIdsPudorys: List<String>,
    val segmentIdsNarys: List<String>,
    val segmentIdsAxo: List<String>,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true
)
data class RegularPolygonSettings(
    var sides: Int = 5  // 3..8
)