package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
data class AxoOverlayLine(
    override val id: String = utils.UUID.randomUUID().toString(),
    val p: Offset,
    val dir: Offset,
    val name: String? = "",
    val upper: String? = "",
    val lower: String? = "",
    val lineWidth: Float = 1f,
    val lineStyle: LineStyle = LineStyle.Solid,
    val color: Color = Color.Gray,
    val creationIndex: Long = UNASSIGNED_INDEX
): OverlayAxoLine {
    override val point: Offset
        get() = Offset(this.p.x, this.p.y)
    override val direction: Offset
        get() = this.dir
}