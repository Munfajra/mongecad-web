package state.snapMonge

import androidx.compose.ui.geometry.Offset
import model.classes.Arc2DNarys
import model.classes.Arc2DPudorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.NamedLineNarys
import model.classes.NamedLinePudorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

fun intersectLineWithCirclePudorys(
    line: NamedLinePudorys,
    circle: ConicSectionPudorys
): List<Offset> {
    // Přímka: bod P + t·v
    val p = line.point
    val v = line.direction
    val dx = v.x
    val dy = v.y
    val x0 = p.x
    val y0 = p.y

    // Kružnice: (x - xc)² + (y - yc)² = r² → obecná forma
    val xc = -circle.d / 2f
    val yc = -circle.e / 2f
    val r2 = xc * xc + yc * yc - circle.f
    if (r2 <= 0f) return emptyList()
    sqrt(r2)

    // Dosadíme parametrické vyjádření přímky do rovnice kružnice
    // => kvadratická rovnice v parametru t

    val dx2 = dx * dx
    val dy2 = dy * dy
    dx * dy

    val a = dx2 + dy2
    val b = 2f * (dx * (x0 - xc) + dy * (y0 - yc))
    val c = (x0 - xc) * (x0 - xc) + (y0 - yc) * (y0 - yc) - r2

    val discriminant = b * b - 4f * a * c
    if (discriminant < 0f) return emptyList()

    val sqrtDisc = sqrt(discriminant)
    val t1 = (-b - sqrtDisc) / (2f * a)
    val t2 = (-b + sqrtDisc) / (2f * a)

    val point1 = Offset(x0 + dx * t1, y0 + dy * t1)
    val point2 = Offset(x0 + dx * t2, y0 + dy * t2)

    return if (discriminant == 0f) listOf(point1) else listOf(point1, point2)
}
fun intersectSegmentWithCirclePudorys(
    segment: SegmentsPudorys,
    circle: ConicSectionPudorys
): List<Offset> {
    val aNew = segment.start
    val bNew = segment.end

    val d = Offset(bNew.x - aNew.x, bNew.y - aNew.y)

    val xc = -circle.d / 2f
    val yc = -circle.e / 2f
    val r2 = xc * xc + yc * yc - circle.f
    if (r2 <= 0f) return emptyList()
    val r = sqrt(r2)

    // Parametrická rovnice úsečky: P(t) = A + t·(B - A), t ∈ [0, 1]
    val dx = d.x
    val dy = d.y
    val x0 = aNew.x
    val y0 = aNew.y

    val a = dx * dx + dy * dy
    val b = 2f * (dx * (x0 - xc) + dy * (y0 - yc))
    val c = (x0 - xc) * (x0 - xc) + (y0 - yc) * (y0 - yc) - r * r

    val disc = b * b - 4f * a * c
    if (disc < 0f) return emptyList()

    val sqrtDisc = sqrt(disc)
    val t1 = (-b - sqrtDisc) / (2f * a)
    val t2 = (-b + sqrtDisc) / (2f * a)

    return listOf(t1, t2)
        .filter { it in 0f..1f }
        .map { t -> Offset(x0 + dx * t, y0 + dy * t) }
}
fun intersectArcWithCirclePudorys(
    arc: Arc2DPudorys,
    circle: ConicSectionPudorys
): List<Offset> {
    val c1 = Offset(arc.center.x, arc.center.y)
    val r1 = arc.radius

    val xc = -circle.d / 2f
    val yc = -circle.e / 2f
    val r2sq = xc * xc + yc * yc - circle.f
    if (r2sq <= 0f) return emptyList()
    val r2 = sqrt(r2sq)
    val c2 = Offset(xc, yc)

    val dx = c2.x - c1.x
    val dy = c2.y - c1.y
    val d = hypot(dx, dy)

    if (d > r1 + r2) return emptyList()
    if (d < abs(r1 - r2)) return emptyList()
    if (d == 0f && r1 == r2) return emptyList()

    val a = (r1 * r1 - r2 * r2 + d * d) / (2f * d)
    val h2 = r1 * r1 - a * a
    if (h2 < 0f) return emptyList()

    val h = sqrt(h2)

    val xm = c1.x + a * dx / d
    val ym = c1.y + a * dy / d

    val rx = -dy * (h / d)
    val ry =  dx * (h / d)

    val p1 = Offset(xm + rx, ym + ry)
    val p2 = Offset(xm - rx, ym - ry)

    return listOf(p1, p2)
        .distinctBy { it }
        .filter { pt ->
            val ang = atan2(-(pt.y - c1.y), pt.x - c1.x) // ✅ sjednocení
            isAngleOnArc(ang, arc)
        }
}
fun intersectCirclesPudorys(
    c1: ConicSectionPudorys,
    c2: ConicSectionPudorys
): List<Offset> {
    val x1 = -c1.d / 2f
    val y1 = -c1.e / 2f
    val r21 = x1 * x1 + y1 * y1 - c1.f
    if (r21 <= 0f) return emptyList()
    val r1 = sqrt(r21)

    val x2 = -c2.d / 2f
    val y2 = -c2.e / 2f
    val r22 = x2 * x2 + y2 * y2 - c2.f
    if (r22 <= 0f) return emptyList()
    val r2 = sqrt(r22)

    val dx = x2 - x1
    val dy = y2 - y1
    val d = hypot(dx, dy)

    // Bez průniku
    if (d > r1 + r2 || d < abs(r1 - r2)) return emptyList()

    // Jeden nebo dva průsečíky
    val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    val h = sqrt(r1 * r1 - a * a)

    val xm = x1 + a * dx / d
    val ym = y1 + a * dy / d

    val rx = -dy * (h / d)
    val ry = dx * (h / d)

    val p1 = Offset(xm + rx, ym + ry)
    val p2 = Offset(xm - rx, ym - ry)

    return if ((p1 - p2).getDistance() < 1e-5f) listOf(p1) else listOf(p1, p2)
}
fun intersectLineWithCircleNarys(
    line: NamedLineNarys,
    circle: ConicSectionNarys
): List<Offset> {
    val p = line.point
    val v = line.direction
    val dx = v.x
    val dz = v.y
    val x0 = p.x
    val z0 = p.z
    val xc = -circle.d / 2f
    val zc = -circle.e / 2f
    val r2 = xc * xc + zc * zc - circle.f
    if (r2 <= 0f) return emptyList()

    val a = dx * dx + dz * dz
    val b = 2f * (dx * (x0 - xc) + dz * (z0 - zc))
    val c = (x0 - xc) * (x0 - xc) + (z0 - zc) * (z0 - zc) - r2

    val discriminant = b * b - 4f * a * c
    if (discriminant < 0f) return emptyList()

    val sqrtDisc = sqrt(discriminant)
    val t1 = (-b - sqrtDisc) / (2f * a)
    val t2 = (-b + sqrtDisc) / (2f * a)

    return if (discriminant == 0f)
        listOf(Offset(x0 + dx * t1, - (z0 + dz * t1)))
    else
        listOf(
            Offset(x0 + dx * t1, - (z0 + dz * t1)),
            Offset(x0 + dx * t2, - (z0 + dz * t2))
        )
}
fun intersectSegmentWithCircleNarys(
    segment: SegmentsNarys,
    circle: ConicSectionNarys
): List<Offset> {
    val aNew = segment.start
    val bNew = segment.end
    val d = Offset(bNew.x - aNew.x, bNew.z - aNew.z)
    val xc = -circle.d / 2f
    val zc = -circle.e / 2f
    val r2 = xc * xc + zc * zc - circle.f
    if (r2 <= 0f) return emptyList()

    val dx = d.x
    val dz = d.y
    val x0 = aNew.x
    val z0 = aNew.z

    val a = dx * dx + dz * dz
    val b = 2f * (dx * (x0 - xc) + dz * (z0 - zc))
    val c = (x0 - xc) * (x0 - xc) + (z0 - zc) * (z0 - zc) - r2

    val disc = b * b - 4f * a * c
    if (disc < 0f) return emptyList()

    val sqrtDisc = sqrt(disc)
    val t1 = (-b - sqrtDisc) / (2f * a)
    val t2 = (-b + sqrtDisc) / (2f * a)

    return listOf(t1, t2)
        .filter { it in 0f..1f }
        .map { t -> Offset(x0 + dx * t, - (z0 + dz * t)) }
}
fun intersectArcWithCircleNarys(
    arc: Arc2DNarys,
    circle: ConicSectionNarys
): List<Offset> {

    // Arc kružnice v Compose: (x, yScreen=-z)
    val c1 = Offset(arc.center.x, -arc.center.z)
    val r1 = arc.radius

    // Circle (ConicSectionNarys) je v "logickém XZ" (x,z), ale u tebe yScreen=-z
    // Střed v XZ:
    val xc = -circle.d / 2f
    val zc = -circle.e / 2f
    val r2sq = xc * xc + zc * zc - circle.f
    if (r2sq <= 0f) return emptyList()
    val r2 = sqrt(r2sq)

    // do Compose:
    val c2 = Offset(xc, -zc)

    val dx = c2.x - c1.x
    val dy = c2.y - c1.y
    val d = hypot(dx, dy)

    if (d > r1 + r2 || d < abs(r1 - r2) || d < 1e-6f) return emptyList()

    val a = (r1 * r1 - r2 * r2 + d * d) / (2f * d)
    val h2 = r1 * r1 - a * a
    if (h2 < 0f) return emptyList()
    val h = sqrt(h2)

    val xm = c1.x + a * dx / d
    val ym = c1.y + a * dy / d

    val rx = -dy * (h / d)
    val ry =  dx * (h / d)

    val p1 = Offset(xm + rx, ym + ry)
    val p2 = Offset(xm - rx, ym - ry)

    fun insideArc(ptCompose: Offset): Boolean {
        // převod do geometrie XZ:
        val x = ptCompose.x
        val z = -ptCompose.y
        val ang = atan2(z - arc.center.z, x - arc.center.x)
        return isAngleOnArc(arc, ang)   // ✅ nový test
    }

    return listOf(p1, p2).filter(::insideArc)
}
fun intersectCirclesNarys(
    c1: ConicSectionNarys,
    c2: ConicSectionNarys
): List<Offset> {
    val x1 = -c1.d / 2f
    val z1 = -c1.e / 2f
    val r21 = x1 * x1 + z1 * z1 - c1.f
    if (r21 <= 0f) return emptyList()
    val r1 = sqrt(r21)

    val x2 = -c2.d / 2f
    val z2 = -c2.e / 2f
    val r22 = x2 * x2 + z2 * z2 - c2.f
    if (r22 <= 0f) return emptyList()
    val r2 = sqrt(r22)

    val dx = x2 - x1
    val dz = z2 - z1
    val d = hypot(dx, dz)

    if (d > r1 + r2 || d < abs(r1 - r2)) return emptyList()

    val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    val h = sqrt(r1 * r1 - a * a)

    val xm = x1 + a * dx / d
    val zm = z1 + a * dz / d

    val rx = -dz * (h / d)
    val rz = dx * (h / d)

    val p1 = Offset(xm + rx, - (zm + rz))
    val p2 = Offset(xm - rx, - (zm - rz))

    return if ((p1 - p2).getDistance() < 1e-5f) listOf(p1) else listOf(p1, p2)
}

 fun makeCircleConicPudorys(center: Offset, r: Float): ConicSectionPudorys {
    val xc = center.x
    val yc = center.y
    val d  = -2f * xc
    val e  = -2f * yc
    val f  = xc*xc + yc*yc - r*r

    // A,B,C jsou implicitně (1,0,1) v tvých intersektech, takže tady stačí d,e,f
    // Zbytek doplň podle své datové třídy (id, name, color, width, ...)
    return ConicSectionPudorys(
        a= 1f,
        b=0f,
        c=1f,
        d = d,
        e = e,
        f = f,
    )
}
fun makeCircleConicNarys(centerX: Float, centerZ: Float, r: Float): ConicSectionNarys {
    val d = -2f * centerX
    val e = -2f * centerZ
    val f = centerX*centerX + centerZ*centerZ - r*r

    return ConicSectionNarys(
        a=1f,
        b=0f,
        c=1f,
        d = d,
        e = e,
        f = f,
    )
}
fun isPointOnCircle(
    pt: Offset,
    center: Offset,
    radius: Float,
    eps: Float
): Boolean = kotlin.math.abs((pt - center).getDistance() - radius) <= eps