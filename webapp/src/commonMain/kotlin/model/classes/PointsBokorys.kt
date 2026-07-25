package model.classes
import androidx.compose.ui.graphics.Color
import model.Point3D
interface PointsBokorys {
    val y: Float
    val z: Float
    var name: String?
    val parent: Point3D?
    val color: Color get() = Color.Black
    val width: Float get() = 1f
}