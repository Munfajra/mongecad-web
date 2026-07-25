package model.classes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class Arc2DBokorys(
    val center: Point3DBokorys,
    val radius: Float,
    // ✅ absolutní úhly v radiánech
    val startRad: Float,
    val endRad: Float,
    var name: String = "oblouk",
    var color: Color = Color.Black,
    var lineStyle: LineStyle = LineStyle.Solid,
    var strokeWidth: Float = 1f,
    // ✅ volba směru po kružnici (který oblouk mezi start->end)
    val clockwise: Boolean,
    val id: String = UUID.randomUUID().toString(),
    val creationIndex: Long = UNASSIGNED_INDEX,
    var showInAxoInitial: Boolean = true
) {
    var showInAxo by mutableStateOf(showInAxoInitial)
    companion object {
        private val TWO_PI = 2f * kotlin.math.PI.toFloat()
        fun norm(a: Float): Float {
            var x = a % TWO_PI
            if (x < 0f) x += TWO_PI
            return x
        }
        fun sweepSigned(start: Float, end: Float, clockwise: Boolean): Float {
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
}
