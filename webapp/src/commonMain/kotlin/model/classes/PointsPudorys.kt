package model.classes
import androidx.compose.ui.graphics.Color
import model.Point3D
interface PointsPudorys {
    val x: Float
    val y: Float
    var name: String?
    val parent: Point3D?
    val color: Color get() = Color.Black
    val width: Float get() = 1f
}