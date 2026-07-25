package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class AxoOverlaySegment(
    override val id: String = UUID.randomUUID().toString(),
    val start: Offset,
    val end: Offset,
    val name: String? = "",
    val lineWidth: Float = 1f,
    val lineStyle: LineStyle = LineStyle.Solid,
    val color: Color = Color.Gray,
    val creationIndex: Long = UNASSIGNED_INDEX
):OverlayAxoSegment {
    override val a: Offset
        get() = Offset(this.start.x, this.start.y)
    override val b: Offset
        get() = Offset(this.end.x, this.end.y)
}