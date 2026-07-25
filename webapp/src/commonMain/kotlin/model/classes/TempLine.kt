package model.classes
import androidx.compose.ui.geometry.Offset
data class TempSnapLine(
    override val point: Offset,
    override val direction: Offset,
    override val id: String = "temp",
    val space: TempSnapSpace
): OverlayAxoLine
enum class TempSnapSpace {
    PUDORYS,
    NARYS,
    BOKORYS,
    AO_OVERLAY
}