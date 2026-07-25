package model.classes
import androidx.compose.ui.graphics.Color
import model.Offset3D
import model.UNASSIGNED_INDEX
import utils.UUID
data class CylindricalSurface3D(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "σ",
    val directrixId: String,          // 3D kuželosečka (elipsa) – přímka tvořic vychází z ní
    val direction: Offset3D,          // směr tvořic (nemusí být unit; normalize v kódu)
    val topPlaneId: String? = null,           // horní omezující rovina
    val equation: PlaneEquation? =null,
    var color: Color = Color.Black,
    var wireWidth: Float = 1.5f,
    var tessT: Int = 128,
    var isVisible: Boolean = true,
    // hraniční křivky (volitelné – nechávám pro budoucno)
    var lowerConicId: String? = null,
    var upperConicId: String? = null,
    // 2D obrysové segmenty a jejich endpointy
    var edgeSegIdsPudorys2D: MutableList<String> = mutableListOf(),
    var edgeSegIdsNarys2D:   MutableList<String> = mutableListOf(),
    var edgeSegIdsBokorys2D: MutableList<String> = mutableListOf(),
    var edgeSegIdsAxo2D: MutableList<String> = mutableListOf(),
    var edgePointIdsPudorys2D: MutableList<String> = mutableListOf(),
    var edgePointIdsNarys2D:   MutableList<String> = mutableListOf(),
    var edgePointIdsBokorys2D: MutableList<String> = mutableListOf(),
    var edgePointIdsAxo2D: MutableList<String> = mutableListOf(),
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true,
    var fillFaces: Boolean = true
)
