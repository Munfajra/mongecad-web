package model.classes
import androidx.compose.ui.graphics.Color
import model.UNASSIGNED_INDEX
import utils.UUID
data class AidPointLogical(
    val x: Float,                     // logická X souřadnice na plátně
    val y: Float,                     // logická Y souřadnice
    var name: String? = null,         // volitelný popisek
    var color: Color = Color.Gray,
    var width: Float = 1f,
    val id: String = UUID.randomUUID().toString(),
    var upperSuperscript: String? = null,
    var lowerSuperscript: String? = null,
    val creationIndex: Long = UNASSIGNED_INDEX
)
