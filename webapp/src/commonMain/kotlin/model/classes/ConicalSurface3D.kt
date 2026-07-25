package model.classes
import androidx.compose.ui.graphics.Color
import model.UNASSIGNED_INDEX
import utils.UUID
data class ConicalSurface3D(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "κ",
    val apexId: String,
    val directrixId: String,
    var color: Color = Color.Companion.Black,
    var wireWidth: Float = 1.5f,
    var tessT: Int = 128,
    var isVisible: Boolean = true,
    var edgeSegIdsPudorys2D: MutableList<String> = mutableListOf(),
    var edgeSegIdsNarys2D:   MutableList<String> = mutableListOf(),
    var edgeSegIdsBokorys2D: MutableList<String> = mutableListOf(),
    var edgeSegIdsAxo2D: MutableList<String> = mutableListOf(),
    var edgePointIdsPudorys2D: MutableList<String> = mutableListOf(),
    var edgePointIdsNarys2D:   MutableList<String> = mutableListOf(),
    var edgePointIdsBokorys2D: MutableList<String> = mutableListOf(),
    var edgePointIdsAxo2D: MutableList<String> = mutableListOf(),
    var apexProjPudorysId: String? = null,
    var apexProjNarysId:   String? = null,
    var apexProjBokorysId: String? = null,
    var apexProjAxoId: String? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true,
    var fillFaces: Boolean = true
)
