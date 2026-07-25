package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class ConicSectionBokorys(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float,
    val rawName: String = "",
    val localColor: Color? = null,
    override val strokeWidth: Float = 1.5f,
    override val lineStyle: LineStyle = LineStyle.Solid,
    override var parent: ConicSection3D? = null,
    override val id: String = UUID.randomUUID().toString(),
    val isHelpCircle: Boolean = false,
    override var parentId: String? = parent?.id,
    // 🆕 degenerace
    var isDegenerate: Boolean = false,
    var degenerateDir: Offset? = null,
    var isLineDegenerate: Boolean = false,
    var showInAxoInitial: Boolean = true,
    val creationIndex: Long = UNASSIGNED_INDEX
) : ConicSection2D {
    override var showInAxo by mutableStateOf(showInAxoInitial)
    override val name: String
        get() = rawName.removeAllSubscripts() + "₂"
    override val color: Color
        get() = parent?.color ?: localColor ?: Color.Black
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
}
data class ConicInputHyperbolaBokorys(
    val vertex: Offset,
    val axis: Offset,
    val line1: NamedLineBokorys,
    val line2: NamedLineBokorys,
    )
