package state.snapMonge

import androidx.compose.ui.geometry.Offset
import model.classes.ArcAxoOverlay
import model.classes.Arc2DBokorys
import model.classes.Arc2DNarys
import model.classes.Arc2DPudorys
import utils.dotProduct
import kotlin.math.*


private fun normAngle(a: Float): Float {
    var x = a % TWO_PI
    if (x < 0f) x += TWO_PI
    return x
}

fun isAngleOnArc(aRad: Float, arc: Arc2DPudorys, epsRad: Float = 0f): Boolean {
    val a = normAngle(aRad)
    val s = normAngle(arc.startRad)
    val sweep = arc.sweepSigned() // signed

    val len = kotlin.math.abs(sweep)

    val d = if (sweep >= 0f) {
        var x = a - s
        if (x < 0f) x += TWO_PI
        x
    } else {
        var x = s - a
        if (x < 0f) x += TWO_PI
        x
    }

    return d <= len + epsRad
}
fun isAngleOnArcAxoOverlay(aRad: Float, arc: ArcAxoOverlay, epsRad: Float = 0f): Boolean {
    val a = normAngle(aRad)
    val s = normAngle(arc.startRad)
    val sweep = arc.sweepSigned()

    val len = kotlin.math.abs(sweep)

    val d = if (sweep >= 0f) {
        var x = a - s
        if (x < 0f) x += TWO_PI
        x
    } else {
        var x = s - a
        if (x < 0f) x += TWO_PI
        x
    }

    return d <= len + epsRad
}
fun intersectLineWithArcPudorys(
    linePoint: Offset,
    lineDir: Offset,
    arc: Arc2DPudorys
): List<Offset> {
    val center = Offset(arc.center.x, arc.center.y)
    val dx = lineDir.x
    val dy = lineDir.y
    val fx = linePoint.x - center.x
    val fy = linePoint.y - center.y

    val a = dx * dx + dy * dy
    val b = 2f * (fx * dx + fy * dy)
    val c = fx * fx + fy * fy - arc.radius * arc.radius

    val discriminant = b * b - 4f * a * c
    if (discriminant < 0f) return emptyList()

    val sqrtDisc = sqrt(discriminant)
    val t1 = (-b + sqrtDisc) / (2f * a)
    val t2 = (-b - sqrtDisc) / (2f * a)

    val rawPoints = listOf(t1, t2).map { t ->
        Offset(linePoint.x + dx * t, linePoint.y + dy * t)
    }

    return rawPoints.filter { pt ->
        val ang = atan2(-(pt.y - center.y), pt.x - center.x)
        isAngleOnArc(ang, arc)
    }
}
fun intersectLineWithArcNarys(
    linePoint: Offset,   // Compose (x, yScreen)
    lineDir: Offset,     // Compose
    arc: Arc2DNarys
): List<Offset> {

    val centerCompose = Offset(arc.center.x, -arc.center.z)

    val dx = lineDir.x
    val dy = lineDir.y
    val fx = linePoint.x - centerCompose.x
    val fy = linePoint.y - centerCompose.y

    val a = dx * dx + dy * dy
    val b = 2f * (fx * dx + fy * dy)
    val c = fx * fx + fy * fy - arc.radius * arc.radius

    val disc = b * b - 4f * a * c
    if (disc < 0f) return emptyList()

    val sqrtDisc = sqrt(disc)
    val t1 = (-b + sqrtDisc) / (2f * a)
    val t2 = (-b - sqrtDisc) / (2f * a)

    val raw = listOf(t1, t2).map { t ->
        Offset(linePoint.x + dx * t, linePoint.y + dy * t)
    }

    return raw.filter { pt ->
        val x = pt.x
        val z = -pt.y             // zpět do geometrie
        val ang = atan2(z - arc.center.z, x - arc.center.x)
        isAngleOnArc(arc, ang)
    }
}
fun intersectArcsNarys(arc1: Arc2DNarys, arc2: Arc2DNarys): List<Offset> {

    val c1 = Offset(arc1.center.x, -arc1.center.z)  // Compose
    val c2 = Offset(arc2.center.x, -arc2.center.z)
    val r1 = arc1.radius
    val r2 = arc2.radius

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

    fun inside(arc: Arc2DNarys, ptCompose: Offset): Boolean {
        val x = ptCompose.x
        val z = -ptCompose.y
        val ang = atan2(z - arc.center.z, x - arc.center.x)
        return isAngleOnArc(arc, ang)
    }

    return listOf(p1, p2).filter { inside(arc1, it) && inside(arc2, it) }
}
fun intersectArcsPudorys(arc1: Arc2DPudorys, arc2: Arc2DPudorys): List<Offset> {
    val c1 = Offset(arc1.center.x, arc1.center.y)
    val c2 = Offset(arc2.center.x, arc2.center.y)
    val r1 = arc1.radius
    val r2 = arc2.radius

    val dx = c2.x - c1.x
    val dy = c2.y - c1.y
    val d = hypot(dx, dy)

    // žádné průsečíky / jeden uvnitř druhého / shodné kružnice
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
        .distinctBy { it } // u tečny p1==p2
        .filter { pt ->
            // ✅ úhel v "geometrii" (y nahoru)
            val ang1 = atan2(-(pt.y - c1.y), pt.x - c1.x)
            val ang2 = atan2(-(pt.y - c2.y), pt.x - c2.x)
            isAngleOnArc(ang1, arc1) && isAngleOnArc(ang2, arc2)
        }
}
//snapping k oblouku nárys
data class ArcSnap(val id: String, val snapped: Offset)

fun trySnapToArcPudorys(
    logicalCursor: Offset,
    arcs: List<Arc2DPudorys>,
    snapThreshold: Float,
    scale: Float,
    state: state.MongeState
): ArcSnap? {
    val tol = snapThreshold / scale
    var best: ArcSnap? = null
    var bestDist2 = Float.POSITIVE_INFINITY

    for (arc in arcs) {
        val c = arc.center
        val dx = logicalCursor.x - c.x
        val dy = logicalCursor.y - c.y
        val dist = hypot(dx, dy)

        // blízko kružnice
        if (kotlin.math.abs(dist - arc.radius) > tol) continue

        // ✅ úhel v "geometrii" (y nahoru)
        val ang = atan2(-dy, dx)

        // úhlová tolerance odpovídající lineární toleranci
        val epsRad = if (arc.radius > 1e-6f) tol / arc.radius else 0f
        if (!isAngleOnArc(ang, arc, epsRad)) continue

        // ✅ zpět do půdorysových souřadnic (y dolů)
        val sx = c.x + arc.radius * cos(ang)
        val sy = c.y - arc.radius * sin(ang)
        val snapped = Offset(sx, sy)

        val d2 = (snapped - logicalCursor).getDistanceSquared()
        if (d2 < bestDist2) {
            bestDist2 = d2
            best = ArcSnap(arc.id, snapped)
            state.snappedArcPudorys = arc
        }
        val snapRadiusLogical = state.snapThreshold / state.scale
        val epsOnCircle = snapRadiusLogical * 0.35f

        val aidSnap = state.findNearestAidPointLogical(
            cursorLogical = logicalCursor,
            snapRadiusLogical = snapRadiusLogical
        )

        if (aidSnap != null) {
            val aidPt = Offset(aidSnap.x, aidSnap.y) // očekávám (x, -z) už správně

            if (isPointOnCircle(aidPt, Offset(c.x,c.y), arc.radius, epsOnCircle)) {
                state.hoveredAidPointId = aidSnap.id
                return ArcSnap("",aidPt)
            }
        }
    }

    return best
}

fun trySnapToArcNarys(
    logicalCursor: Offset,     // (x, yScreen)
    arcs: List<Arc2DNarys>,
    snapThreshold: Float,
    scale: Float,
    state: state.MongeState
): ArcSnap? {

    val tol = snapThreshold / scale
    var best: ArcSnap? = null
    var bestDist2 = Float.POSITIVE_INFINITY

    // Cursor v XZ (z je nahoru)
    val px = logicalCursor.x
    val pz = -logicalCursor.y

    for (arc in arcs) {
        val cx = arc.center.x
        val cz = arc.center.z
        val dx = px - cx
        val dz = pz - cz

        val dist = hypot(dx, dz)
        if (kotlin.math.abs(dist - arc.radius) > tol) continue

        val a = atan2(dz, dx)  // ✅ geometrický úhel v XZ
        if (!isAngleOnArc(arc, a)) continue

        // snap na kružnici (geometrie)
        val sx = cx + arc.radius * kotlin.math.cos(a)
        val sz = cz + arc.radius * kotlin.math.sin(a)

        // zpět do Compose (y=-z)
        val snapped = Offset(sx, -sz)

        val d2 = (snapped - logicalCursor).getDistanceSquared()
        if (d2 < bestDist2) {
            bestDist2 = d2
            best = ArcSnap(arc.id, snapped)
            state.snappedArcNarys = arc
        }
    }
    return best
}

fun intersectArcWithSegmentNarys(
    arc: Arc2DNarys,
    segStart: Offset,
    segEnd: Offset
): List<Offset> {
    val dir = segEnd - segStart
    val candidates = intersectLineWithArcNarys(segStart, dir, arc)
    return candidates.filter { point ->
        val t = ((point - segStart).dotProduct(dir)) / dir.getDistanceSquared()
        t in 0f..1f
    }
}
fun intersectSegmentWithArcPudorys(
    segStart: Offset,
    segEnd: Offset,
    arc: Arc2DPudorys
): List<Offset> {
    val dir = segEnd - segStart
    val candidates = intersectLineWithArcPudorys(segStart, dir, arc)
    return candidates.filter { point ->
        val t = ((point - segStart).dotProduct(dir)) / dir.getDistanceSquared()
        t in 0f..1f
    }
}
private const val TWO_PI = 2f * kotlin.math.PI.toFloat()

private fun normAngleRad(a: Float): Float {
    var x = a % TWO_PI
    if (x < 0f) x += TWO_PI
    return x
}

/** signed sweep: CCW +, CW - */
private fun sweepSigned(startRad: Float, endRad: Float, clockwise: Boolean): Float {
    val s = normAngleRad(startRad)
    val e = normAngleRad(endRad)
    return if (!clockwise) {
        var d = e - s
        if (d < 0f) d += TWO_PI
        d
    } else {
        var d = s - e
        if (d < 0f) d += TWO_PI
        -d
    }
}

/** Je úhel `a` na oblouku daném start->end při zvoleném směru (clockwise)? */
fun isAngleOnArc(aRad: Float, arc: Arc2DBokorys, epsRad: Float = 0f): Boolean {
    val a = normAngle(aRad)
    val s = normAngle(arc.startRad)
    val sweep = arc.sweepSigned() // signed

    val len = kotlin.math.abs(sweep)

    val d = if (sweep >= 0f) {
        var x = a - s
        if (x < 0f) x += TWO_PI
        x
    } else {
        var x = s - a
        if (x < 0f) x += TWO_PI
        x
    }

    return d <= len + epsRad
}
fun isAngleOnArc(arc: Arc2DNarys, aRad: Float): Boolean {
    val s0 = normAngleRad(arc.startRad)
    val sw = sweepSigned(arc.startRad, arc.endRad, arc.clockwise)
    val e0 = normAngleRad(s0 + sw)
    val A  = normAngleRad(aRad)

    return if (sw >= 0f) {
        // CCW S->E
        if (s0 <= e0) (A in s0..e0) else (A >= s0 || A <= e0)
    } else {
        // CW S->E == CCW E->S
        if (e0 <= s0) (A in e0..s0) else (A >= e0 || A <= s0)
    }
}

