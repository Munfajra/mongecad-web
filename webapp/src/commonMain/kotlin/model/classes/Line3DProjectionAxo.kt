package model.classes
import utils.withSuffixOnce
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.UNASSIGNED_INDEX
import utils.UUID
data class Line3DProjectionAxo(
    val p: Point3DAxo,
    val dir: Offset,
    var localName: String? = null,
    override var parent: Line3D? = null,
    val localColor: Color? = null,
    val localLineStyle: LineStyle? = null,
    val localStrokeWidth: Float? = null,
    val localSuperscript: String? = null,
    override val id: String = UUID.randomUUID().toString(),
    override var parentId: String? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var showInAxoInitial : Boolean = true,
    // ořez axo průmětu o průmětny (mezi stopníky, 1. oktant); null = dle nastavení
    val clipToOctant: Boolean? = null,
    val localCustomTrimRange: LineTrimRange? = null,
) :Line2DProjection, OverlayAxoLine, NamedLineAxo {
    override var showInAxo by mutableStateOf(showInAxoInitial)
    override val name: String?
        get() = parent?.name?.withSuffixOnce("ₐ") ?: localName
    override val point: Offset
        get() = Offset(this.p.x, this.p.y)
    override val direction: Offset
        get() = this.dir
    // localColor vyhrává nad barvou parenta: dovoluje to axo průmětu mít
    // vlastní barvu nezávislou na 3D rodiči (typicky osy x/y/z, které jsou
    // v OpenGL/Monge barevné, ale v AXO překryvu defaultně černé). Pro běžné
    // axo průměty bez explicitní localColor se chování nezměnilo - spadnou
    // na barvu parenta jako dřív.
    override val color: Color
        get() = parent?.color ?: localColor ?: Color.Black
    override val lineStyle: LineStyle
        get() = parent?.lineStyle ?: localLineStyle ?: LineStyle.Solid
    override val strokeWidth: Float
        get() = parent?.strokeWidth ?: localStrokeWidth ?: 1f
    override val superscript: String?
        get() = parent?.superscript ?: localSuperscript
    val effectiveCreationIndex: Long
        get() = parent?.creationIndex ?: creationIndex
    override val customTrimRange: LineTrimRange?
        get() = parent?.customTrimRange ?: localCustomTrimRange
    override fun to2DLine(): Line {
        return Line(Offset(point.x, point.y), dir)
    }
}
