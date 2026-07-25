package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.Point3D
import model.UNASSIGNED_INDEX
import utils.UUID
data class Segment3D(
    val start: Point3D,
    val end: Point3D,
    var name: String = "",
    var color: Color = Color.Black,
    var lineStyle: LineStyle = LineStyle.Solid,
    var strokeWidth: Float = 1f,
    val id: String = UUID.randomUUID().toString(),
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true
)
