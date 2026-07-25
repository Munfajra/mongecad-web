package model.classes
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
/**
 * Čistě 2D mnohoúhelník používaný v režimu PLANE.
 *
 * Geometrii stále tvoří pomocné půdorysné úsečky. Polygon nad nimi drží jen
 * uspořádané vazby, aby se choval jako složený objekt bez falešných 3D bodů
 * a úseček.
 */
data class PlanePolygon2D(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "P",
    val vertexPointIdsPudorys: List<String>,
    val vertexAidPointIds: List<String> = emptyList(),
    val segmentIdsPudorys: List<String>,
    val color: Color = Color.Black,
    val width: Float = 1f,
    val style: LineStyle = LineStyle.Solid,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true
) {
    val n: Int
        get() = vertexAidPointIds.ifEmpty { vertexPointIdsPudorys }.size
}
