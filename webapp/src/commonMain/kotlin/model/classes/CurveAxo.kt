package model.classes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.Offset3D
import model.UNASSIGNED_INDEX
import utils.UUID
data class CurveAxo(
    val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null,
    val parent: Curve3D? = null,
    var name: String = "k",
    var color: Color = Color.Black,
    var strokeWidth: Float = 2f,
    val pointIds: List<String>,   // Point3DAxo.id
    val closed: Boolean = false,
    val lineStyle: LineStyle,
    val creationIndex: Long = UNASSIGNED_INDEX,
    val showInAxoInitial: Boolean = true,
    // Zapečená polylinie ve 3D (world). Když je vyplněná, křivka se kreslí přímo
    // z těchto bodů (projekcí přes basis) a `pointIds` se ignoruje – tím se
    // hustý obrys rotačních ploch nepřidává po bodech do sharedPoints3D/pointsAxo
    // (jinak by spamoval object list). Body se promítají až při kreslení, takže
    // jsou robustní vůči změně axo báze.
    val polyline3D: List<Offset3D>? = null,
) {
    var showInAxo by mutableStateOf(showInAxoInitial)
    val effectiveName: String
        get() = parent?.name ?: name
    val effectiveColor: Color
        get() = parent?.color ?: color
    val effectiveStrokeWidth: Float
        get() = parent?.strokeWidth ?: strokeWidth
    val effectiveLineStyle: LineStyle
        get() = parent?.lineStyle ?: lineStyle
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
}
