package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import utils.normalize
import kotlin.math.abs
import kotlin.math.sqrt
sealed interface ConicSection2D : NamedObject {
    override val id: String
    override val name: String?
    val color: Color
    val strokeWidth: Float
    val lineStyle: LineStyle
    val parent: ConicSection3D?
    val parentId: String?
    var showInAxo: Boolean
}
interface NamedObject {
    val id: String
    val name: String?
}
data class Matrix2x2(
    val m00: Float, val m01: Float,
    val m10: Float, val m11: Float
) {
    fun transpose(): Matrix2x2 = Matrix2x2(m00, m10, m01, m11)
    fun mul(v: Offset): Offset = Offset(
        m00 * v.x + m01 * v.y,
        m10 * v.x + m11 * v.y
    )
    fun eigenDecomposition(): Pair<List<Float>, List<Offset>> {
        val trace = m00 + m11
        val det = m00 * m11 - m01 * m10
        val delta = sqrt((trace * trace - 4 * det).coerceAtLeast(0f))
        val lambda1 = 0.5f * (trace + delta)
        val lambda2 = 0.5f * (trace - delta)
        fun eigenVector(lambda: Float): Offset {
            val eps = 1e-6f
            return when {
                abs(m01) > eps -> Offset(lambda - m11, m01).normalize()
                abs(m10) > eps -> Offset(m10, lambda - m00).normalize()
                abs(m00 - lambda) > eps -> Offset(1f, 0f)
                else -> Offset(0f, 1f)
            }
        }
        val v1 = eigenVector(lambda1)
        val v2 = eigenVector(lambda2)
        return listOf(lambda1, lambda2) to listOf(v1, v2)
    }
}
