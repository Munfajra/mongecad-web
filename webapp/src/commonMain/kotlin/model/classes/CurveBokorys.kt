package model.classes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class CurveBokorys(
    val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null,
    val parent: Curve3D? = null,
    var name: String = "k",
    var color: Color = Color.Black,
    var strokeWidth: Float = 2f,
    val pointIds: List<String>,   // Point3DBokorys.id
    val closed: Boolean = false,
    val lineStyle: LineStyle,
    val creationIndex: Long = UNASSIGNED_INDEX,
    val showInAxoInitial: Boolean = true,
    val polylineLocal: List<Offset>? = null,
) {
    var showInAxo by mutableStateOf(showInAxoInitial)
    val effectiveName: String
        get() = parent?.name ?: name
    val effectiveColor: Color
        get() = parent?.color ?: color
    val effectiveStrokeWidth: Float
        get() = parent?.strokeWidth ?: strokeWidth
    val effectiveLineStyle: LineStyle
        get() = parent?.lineStyle ?: lineStyle
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
}
