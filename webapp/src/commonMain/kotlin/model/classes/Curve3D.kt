package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.Offset3D
import model.UNASSIGNED_INDEX
import utils.UUID
data class Curve3D(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var color: Color,
    var strokeWidth: Float,
    val pointIds: List<String>,
    val closed: Boolean = false,
    val lineStyle: LineStyle,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true,
    val polyline3D: List<Offset3D>? = null,
)
