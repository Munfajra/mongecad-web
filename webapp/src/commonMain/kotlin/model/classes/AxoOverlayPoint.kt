package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.UNASSIGNED_INDEX
data class AxoOverlayPoint(
    val id: String = utils.UUID.randomUUID().toString(),
    val positionLogical: Offset,
    val name: String? = null,
    val upper: String? = null,
    val lower: String? = null,
    val color: Color = Color.Gray,
    val width: Float = 1f,
    val creationIndex: Long = UNASSIGNED_INDEX
)
