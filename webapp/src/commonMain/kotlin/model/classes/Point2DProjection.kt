package model.classes
import androidx.compose.ui.graphics.Color
import model.Point3D
sealed interface Point2DProjection {
    val id: String
    var name: String?
    val parent: Point3D?
    val localColor: Color?
    val localWidth: Float?
    val localSuperscript: String?
    val showInAxo: Boolean
}
