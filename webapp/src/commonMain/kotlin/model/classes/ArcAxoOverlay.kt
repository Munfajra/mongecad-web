package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class ArcAxoOverlay(
    val center: Offset,
    val radius: Float,
    val startRad: Float,
    val endRad: Float,
    var name: String = "oblouk",
    var color: Color = Color.Black,
    var lineStyle: LineStyle = LineStyle.Solid,
    var strokeWidth: Float = 1f,
    val clockwise: Boolean,
    val id: String = UUID.randomUUID().toString(),
    val creationIndex: Long = UNASSIGNED_INDEX
) {
    companion object {
        private val TWO_PI = 2f * kotlin.math.PI.toFloat()
        fun norm(a: Float): Float {
            var x = a % TWO_PI
            if (x < 0f) x += TWO_PI
            return x
        }
        fun sweepSigned(start: Float, end: Float, clockwise: Boolean): Float {
            if (kotlin.math.abs(end - start) >= TWO_PI - 1e-5f) {
                return if (clockwise) -TWO_PI else TWO_PI
            }
            val s = norm(start)
            val e = norm(end)
            return if (!clockwise) {
                var d = e - s
                if (d < 0f) d += TWO_PI
                d
            } else {
                var d = s - e
                if (d < 0f) d += TWO_PI
                -d
            }
        }
    }
    fun sweepSigned(): Float = sweepSigned(startRad, endRad, clockwise)
    fun isFullCircle(): Boolean = kotlin.math.abs(kotlin.math.abs(sweepSigned()) - TWO_PI) <= 1e-5f
}
