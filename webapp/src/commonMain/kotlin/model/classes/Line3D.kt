package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.Offset3D
import model.Point3D
import model.UNASSIGNED_INDEX
import model.plus
import utils.UUID
data class Line3D(
    val start: Point3D,
    val direction: Offset3D,
    var name: String = "",
    var labelOffset: Offset = Offset.Zero,
    var color: Color = Color.Black,
    var lineStyle: LineStyle = LineStyle.Solid,
    var strokeWidth: Float = 1f,
    val id: String = UUID.randomUUID().toString(),
    var superscript: String? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true,
    val customTrimRange: LineTrimRange? = null
)
fun Line3D.projectToXZ(): NamedLineNarys = NamedLineNarysImpl(
    id = this.id,
    name = this.name,
    // bod v XZ → Point3DNarys(x, z)
    point = Point3DNarys(x = this.start.x, z = this.start.z),
    // směr v XZ
    direction = Offset(this.direction.x, this.direction.z),
    color = this.color,
    strokeWidth = this.strokeWidth,
    lineStyle = this.lineStyle
)
fun Line3D.projectToXY(): NamedLinePudorys = NamedLinePudorysImpl(
    id = this.id,
    name = this.name,
    // bod v XY → Point3DPudorys(x, y)
    point = Point3DPudorys(x = this.start.x, y = this.start.y),
    // směr v XY
    direction = Offset(this.direction.x, this.direction.y),
    color = this.color,
    strokeWidth = this.strokeWidth,
    lineStyle = this.lineStyle
)
fun Point3D.toOffset3D(): Offset3D {
    return Offset3D(this.x, this.y, this.z)
}
fun Line3D.projectPoint(p: Offset3D): Offset3D {
    val a = this.start       // výchozí bod na přímce
    val v = this.direction     // směrový vektor přímky
    val ap = p - a.toOffset3D()             // vektor z bodu na přímku
    val t = (ap dot v) / (v dot v)  // projekce (parametr t)
    return (a + v * t).toOffset3D()           // výsledek je bod na přímce
}
