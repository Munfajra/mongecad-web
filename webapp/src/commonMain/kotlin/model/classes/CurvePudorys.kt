package model.classes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class CurvePudorys(
    val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null,
    var name: String = "k",
    var color: Color = Color.Black,
    var strokeWidth: Float = 2f,
    var lineStyle: LineStyle = LineStyle.Solid,
    val parent: Curve3D? = null,
    val points: List<CurvePudRef>,
    val closed: Boolean = false,
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
sealed class CurvePudRef {
    abstract val id: String
    data class P(val pointId: String) : CurvePudRef() { override val id = pointId }
    data class A(val aidId: String)   : CurvePudRef() { override val id = aidId }
    fun key(): String = when (this) {
        is P -> "P:$pointId"
        is A -> "A:$aidId"
    }
}
