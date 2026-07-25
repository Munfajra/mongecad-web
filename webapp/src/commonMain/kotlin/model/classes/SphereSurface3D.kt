package model.classes
import androidx.compose.ui.graphics.Color
import model.UNASSIGNED_INDEX
import utils.UUID
data class SphereSurface3D(
    val centerPoint3DId: String,
    val radius: Float,
    val name: String = "σ",
    val color: Color = Color(0xFF1565C0),
    val id: String = UUID.randomUUID().toString(),
    val strokeWidth: Float? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true,
    var fillFaces: Boolean = true
)