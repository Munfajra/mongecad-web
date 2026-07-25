package model.classes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
import kotlin.math.abs
import kotlin.math.sqrt
data class ConicSectionAxo(
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
        get() = rawName.removeAllSubscripts() + "₁"
    override val color: Color
        get() = parent?.color ?: localColor ?: Color.Black
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
}
data class CircleGeomAxo(val center: Offset, val r: Float)
fun ConicSectionAxo.tryAsCircleGeom(
    epsRel: Float = 1e-4f,
    epsAbs: Float = 1e-6f
): CircleGeomAxo? {
    val a = this.a
    val b = this.b
    val c = this.c
    val d = this.d
    val e = this.e
    val f = this.f
    val s = maxOf(abs(a), abs(c), 1f)
    fun near(x: Float, y: Float) = abs(x - y) <= epsAbs + epsRel * s
    fun near0(x: Float) = abs(x) <= epsAbs + epsRel * s
    if (near0(a)) return null
    if (!near0(b)) return null
    if (!near(a, c)) return null
    val x0 = -d / (2f * a)
    val y0 = -e / (2f * a)
    val r2 = x0 * x0 + y0 * y0 - f / a
    if (r2 <= 0f) return null
    return CircleGeomAxo(Offset(x0, y0), sqrt(r2))
}
data class ConicInputHyperbolaAxo(
    val vertex: Offset,
    val axis: Offset,
    val line1: NamedLineAxo,
    val line2: NamedLineAxo,
)
