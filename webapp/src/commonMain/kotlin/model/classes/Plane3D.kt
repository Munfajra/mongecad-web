package model.classes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import model.LineStyle
import model.Offset3D
import model.Point3D
import model.UNASSIGNED_INDEX
import utils.UUID
import kotlin.math.sqrt
data class Plane3D(
    val tracePudorys: PlaneTracePudorys,
    val traceNarys: PlaneTraceNarys,
    val traceBokorys: PlaneTraceBokorys,
    var name: String = "?",
    val equation: PlaneEquation? = null,
    var color: Color = Color.Black,
    var lineStyle: LineStyle = LineStyle.Solid,
    var strokeWidth: Float = 1f,
    val id: String = UUID.randomUUID().toString(),
    var superscript: String? = null,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true
)
const val PLANE_PUDORYS_ID = "plane-pudorysna"
const val PLANE_NARYS_ID   = "plane-narysna"
const val PLANE_BOKORYS_ID   = "plane-bokorysna"
private fun safeVirtualTracePudorys(
    at: Offset = Offset(0f, 0f),
    dir: Offset = Offset(1f, 0f)
) = PlaneTracePudorys(
    point = Point3DPudorys(at.x, at.y, name = "", parent = Point3D(at.x, at.y, 0f, "")),
    direction = if (dir.getDistance() < 1e-6f) Offset(1f, 0f) else dir,
    isVirtual = true
)
private fun safeVirtualTraceBokorys(
    at: Offset = Offset(0f, 0f),
    dir: Offset = Offset(1f, 0f)
) = PlaneTraceBokorys(
    point = Point3DBokorys(
        at.x, at.y,
        name = "",
        parent = Point3D(0f, at.x, at.y, "")
    ),
    direction = if (dir.getDistance() < 1e-6f) Offset(1f, 0f) else dir,
    isVirtual = true
)
fun safeVirtualTraceNarys(
    at: Offset = Offset(0f, 0f),
    dir: Offset = Offset(1f, 0f)
) = PlaneTraceNarys(
    point = Point3DNarys(at.x, at.y, name = "", parent = Point3D(at.x, 0f, at.y, "")),
    direction = if (dir.getDistance() < 1e-6f) Offset(1f, 0f) else dir,
    isVirtual = true
)
data class PlaneTraces3(
    val pudorys: PlaneTracePudorys?,
    val narys: PlaneTraceNarys?,
    val bokorys: PlaneTraceBokorys?
)
fun makeReferencePudorysna(): Plane3D {
    val eq = PlaneEquation(a = 0f, b = 0f, c = 1f, d = 0f) // z = 0
    val (tP, tN,tB) = tracesFromPlaneEquation(eq)
    // Degenerace: pudorysná stopa neexistuje (0=0), nárysná existuje (z=0 v XZ)
    val traceP = tP ?: safeVirtualTracePudorys()      // nebude se kreslit ani snappovat
    val traceN = tN ?: safeVirtualTraceNarys()
    val traceB = tB ?: safeVirtualTraceBokorys()
    return Plane3D(
        id = PLANE_PUDORYS_ID,
        name = "π₁",
        equation = eq,
        tracePudorys = traceP,
        traceNarys = traceN,
        traceBokorys = traceB
    )
}
fun makeReferenceNarysna(): Plane3D {
    val eq = PlaneEquation(a = 0f, b = 1f, c = 0f, d = 0f) // y = 0
    val (tP, tN,tB) = tracesFromPlaneEquation(eq)
    val traceP = tP ?: safeVirtualTracePudorys()
    val traceN = tN ?: safeVirtualTraceNarys()
    val traceB = tB ?: safeVirtualTraceBokorys()
    return Plane3D(
        id = PLANE_NARYS_ID,
        name = "π₂",
        equation = eq,
        tracePudorys = traceP,
        traceNarys = traceN,
        traceBokorys = traceB
    )
}
fun makeReferenceBokorysna(): Plane3D {
    val eq = PlaneEquation(a = 1f, b = 0f, c = 0f, d = 0f) // x = 0
    val (tP, tN,tB) = tracesFromPlaneEquation(eq)
    val traceP = tP ?: safeVirtualTracePudorys()
    val traceN = tN ?: safeVirtualTraceNarys()
    val traceB = tB ?: safeVirtualTraceBokorys()
    return Plane3D(
        id = PLANE_BOKORYS_ID,
        name = "μ\u2083",
        equation = eq,
        tracePudorys = traceP,
        traceNarys = traceN,
        traceBokorys = traceB
    )
}
/** Vrátí jednu ze tří základních průměten, které nejsou uložené v `state.planes3D`. */
fun referencePlaneById(id: String): Plane3D? = when (id) {
    PLANE_PUDORYS_ID -> makeReferencePudorysna()
    PLANE_NARYS_ID -> makeReferenceNarysna()
    PLANE_BOKORYS_ID -> makeReferenceBokorysna()
    else -> null
}
@Serializable
data class PlaneEquation(val a: Float, val b: Float, val c: Float, val d: Float)
fun planeEquationFromTraces(p0: Point3D, d1: Offset3D, d2: Offset3D): PlaneEquation {
    val n = d1 cross d2
    val a = n.x
    val b = n.y
    val c = n.z
    val d = -(a * p0.x + b * p0.y + c * p0.z)
    return PlaneEquation(a, b, c, d)
}
// Bez závislosti na jiných helper funkcích
fun PlaneEquation.normalized(eps: Float = 1e-8f): PlaneEquation {
    val len = sqrt(a*a + b*b + c*c)
    if (len < eps) return this  // degenerovaná rovina, necháme jak je
    val inv = 1f / len
    return PlaneEquation(a * inv, b * inv, c * inv, d * inv)
}
val PlaneEquation.normal: Offset3D
    get() = Offset3D(a, b, c)
private fun defaultTraceShowInAxo(point: Offset, direction: Offset, eps: Float = 1e-5f): Boolean {
    val parallelToLocalYInNegativeX =
        kotlin.math.abs(direction.x) < eps && point.x < -eps
    val parallelToLocalXInNegativeY =
        kotlin.math.abs(direction.y) < eps && point.y < -eps
    return !parallelToLocalYInNegativeX && !parallelToLocalXInNegativeY
}
fun tracesFromPlaneEquation(eq: PlaneEquation): PlaneTraces3 {
    val (a, b, c, d) = eq
    // Parametrizaci stopy volíme podle DOMINANTNÍHO koeficientu, ne podle `!= 0f`.
    // Dělení miniaturním koeficientem (např. rovina x=a má b,c ~ 1e-8 z float šumu)
    // by dalo bod stopy vzdálený ~1e9 jednotek a přímka by na plátně kvůli ztrátě
    // float přesnosti poskakovala (problikávající stopa).
    val tracePudorys = run {
        val p1: Point3D?
        val p2: Point3D?
        if (kotlin.math.abs(b) >= kotlin.math.abs(a) && b != 0f) {
            p1 = Point3D(0f, -d / b, 0f, "")
            p2 = Point3D(1f, -(a + d) / b, 0f, "")
        } else if (a != 0f) {
            p1 = Point3D(-d / a, 0f, 0f, "")
            p2 = Point3D(-(b + d) / a, 1f, 0f, "")
        } else {
            p1 = null
            p2 = null
        }
        if (p1 != null && p2 != null) {
            val dir = Offset(p2.x - p1.x, p2.y - p1.y).let {
                if (it.getDistance() < 1e-6f) Offset(1f, 0f) else it
            }
            val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
            PlaneTracePudorys(
                point = Point3DPudorys(
                    center.x,
                    center.y,
                    name = "",
                    parent = Point3D(center.x, center.y, 0f, "")
                ),
                direction = dir,
                showInAxo = defaultTraceShowInAxo(center, dir)
            )
        } else null
    }
    val traceNarys = run {
        val q1: Point3D?
        val q2: Point3D?
        if (kotlin.math.abs(c) >= kotlin.math.abs(a) && c != 0f) {
            q1 = Point3D(0f, 0f, -d / c, "")
            q2 = Point3D(1f, 0f, -(a + d) / c, "")
        } else if (a != 0f) {
            q1 = Point3D(-d / a, 0f, 0f, "")
            q2 = Point3D(-(c + d) / a, 0f, 1f, "")
        } else {
            q1 = null
            q2 = null
        }
        if (q1 != null && q2 != null) {
            val dir = Offset(q2.x - q1.x, q2.z - q1.z).let {
                if (it.getDistance() < 1e-6f) Offset(1f, 0f) else it
            }
            val center = Offset((q1.x + q2.x) / 2f, (q1.z + q2.z) / 2f)
            PlaneTraceNarys(
                point = Point3DNarys(
                    center.x,
                    center.y,
                    name = "",
                    parent = Point3D(center.x, 0f, center.y, "")
                ),
                direction = dir,
                showInAxo = defaultTraceShowInAxo(center, dir)
            )
        } else null
    }
    val traceBokorys = run {
        val r1: Point3D?
        val r2: Point3D?
        if (kotlin.math.abs(c) >= kotlin.math.abs(b) && c != 0f) {
            r1 = Point3D(0f, 0f, -d / c, "")
            r2 = Point3D(0f, 1f, -(b + d) / c, "")
        } else if (b != 0f) {
            r1 = Point3D(0f, -d / b, 0f, "")
            r2 = Point3D(0f, -(c + d) / b, 1f, "")
        } else {
            r1 = null
            r2 = null
        }
        if (r1 != null && r2 != null) {
            val dir = Offset(r2.y - r1.y, r2.z - r1.z).let {
                if (it.getDistance() < 1e-6f) Offset(1f, 0f) else it
            }
            val center = Offset((r1.y + r2.y) / 2f, (r1.z + r2.z) / 2f)
            PlaneTraceBokorys(
                point = Point3DBokorys(
                    center.x,
                    center.y,
                    name = "",
                    parent = Point3D(0f, center.x, center.y, "")
                ),
                direction = dir,
                showInAxo = defaultTraceShowInAxo(center, dir)
            )
        } else null
    }
    return PlaneTraces3(tracePudorys, traceNarys, traceBokorys)
}
