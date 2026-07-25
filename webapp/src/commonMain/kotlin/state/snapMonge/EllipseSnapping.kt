package state.snapMonge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.NamedLineNarys
import model.classes.NamedLinePudorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import utils.dot
import kotlin.math.abs
import kotlin.math.hypot

import kotlin.math.sqrt

fun intersectLineWithConicPudorys(
    line: NamedLinePudorys,
    conic: ConicSectionPudorys,
    cursor: Offset = Offset.Zero
): List<Offset> {
    if (!cursor.isSpecified) return emptyList()
    if (!line.direction.isSpecified) return emptyList()

    if (!line.point.x.isFinite() || !line.point.y.isFinite()) return emptyList()

    val coeffs = listOf(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
    if (coeffs.any { !it.isFinite() }) return emptyList()

    val dxu = line.direction.x
    val dyu = line.direction.y
    val len = hypot(dxu, dyu)
    if (!len.isFinite() || len < 1e-6f) return emptyList()

    val dx = dxu / len
    val dy = dyu / len

    val base = line.point
    val t0 = (cursor.x - base.x) * dx + (cursor.y - base.y) * dy
    val x0 = base.x + dx * t0
    val y0 = base.y + dy * t0
    if (!t0.isFinite() || !x0.isFinite() || !y0.isFinite()) return emptyList()

    val aOr = conic.a
    val bOr = conic.b
    val cOr = conic.c
    val dOr = conic.d
    val eOr = conic.e
    val fOr = conic.f

    val qa = aOr * dx * dx +
            bOr * dx * dy +
            cOr * dy * dy

    val qb = 2f * aOr * x0 * dx +
            bOr * (x0 * dy + y0 * dx) +
            2f * cOr * y0 * dy +
            dOr * dx +
            eOr * dy

    val qc = aOr * x0 * x0 +
            bOr * x0 * y0 +
            cOr * y0 * y0 +
            dOr * x0 +
            eOr * y0 +
            fOr

    if (!qa.isFinite() || !qb.isFinite() || !qc.isFinite()) return emptyList()

    val result = mutableListOf<Offset>()

    if (abs(qa) < 1e-6f) {
        if (abs(qb) < 1e-6f) return emptyList()
        val t = -qc / qb
        if (!t.isFinite()) return emptyList()

        val p = Offset(x0 + dx * t, y0 + dy * t)
        if (!p.isSpecified) return emptyList()

        if (abs(evaluateConicAt(p, conic)) < 1e-2f) result.add(p)
        return result
    }

    val discriminant = qb * qb - 4f * qa * qc
    if (!discriminant.isFinite() || discriminant < 0f) return emptyList()

    val sqrtDisc = sqrt(discriminant)
    val denom = 2f * qa
    if (!denom.isFinite() || abs(denom) < 1e-6f) return emptyList()

    val t1 = (-qb - sqrtDisc) / denom
    val t2 = (-qb + sqrtDisc) / denom
    if (!t1.isFinite() || !t2.isFinite()) return emptyList()

    val p1 = Offset(x0 + dx * t1, y0 + dy * t1)
    val p2 = Offset(x0 + dx * t2, y0 + dy * t2)

    val tol = 1e-2f
    if (p1.isSpecified && abs(evaluateConicAt(p1, conic)) < tol) result.add(p1)
    if (p2.isSpecified && abs(evaluateConicAt(p2, conic)) < tol) result.add(p2)

    return result
}

fun evaluateConicAt(p: Offset, c: ConicSectionPudorys): Float {
    if (!p.isSpecified) {
        error("evaluateConicAt: p is Offset.Unspecified")
    }

    val x = p.x
    val y = p.y
    return c.a * x * x +
            c.b * x * y +
            c.c * y * y +
            c.d * x +
            c.e * y +
            c.f
}
fun evaluateConicAt(p: Offset, c: ConicSectionNarys): Float {
    if (!p.isSpecified) {
        error("evaluateConicAt: p is Offset.Unspecified")
    }
    val x = p.x
    val z = -p.y // Y → -Z
    return c.a * x * x +
            c.b * x * z +
            c.c * z * z +
            c.d * x +
            c.e * z +
            c.f
}
fun intersectSegmentWithConicPudorys(
    segment: SegmentsPudorys,
    conic: ConicSectionPudorys
): List<Offset> {
    val aOr = segment.start
    val bOr = segment.end

    val dx = bOr.x - aOr.x
    val dy = bOr.y - aOr.y
    val x0 = aOr.x
    val y0 = aOr.y

    val a = conic.a * dx * dx +
            conic.b * dx * dy +
            conic.c * dy * dy

    val b = 2f * conic.a * x0 * dx +
            conic.b * (x0 * dy + y0 * dx) +
            2f * conic.c * y0 * dy +
            conic.d * dx +
            conic.e * dy

    val c = conic.a * x0 * x0 +
            conic.b * x0 * y0 +
            conic.c * y0 * y0 +
            conic.d * x0 +
            conic.e * y0 +
            conic.f

    val discriminant = b * b - 4f * a * c
    if (discriminant < 0f) return emptyList()

    val sqrtDisc = sqrt(discriminant)
    val denom = 2f * a

    val t1 = (-b - sqrtDisc) / denom
    val t2 = (-b + sqrtDisc) / denom

    return listOf(t1, t2)
        .filter { it in 0f..1f }
        .map { t -> Offset(x0 + dx * t, y0 + dy * t) }
}
fun intersectLineWithConicNarys(
    line: NamedLineNarys,
    conic: ConicSectionNarys,
    cursor: Offset = Offset.Zero
): List<Offset> {
    if (!cursor.isSpecified) return emptyList()
    if (!line.direction.isSpecified) return emptyList()

    if (!line.point.x.isFinite() || !line.point.z.isFinite()) return emptyList()
    val dxu = line.direction.x
    val dzu = line.direction.y
    val len = hypot(dxu, dzu)
    if (len < 1e-6f) return emptyList()

    val dx = dxu / len
    val dz = dzu / len

    val baseX = line.point.x
    val baseZ = line.point.z
    val cursorZ = -cursor.y // převod z Y na Z

    // Projekce kurzoru na přímku v (x, z)
    val t0 = (cursor.x - baseX) * dx + (cursorZ - baseZ) * dz
    val x0 = baseX + dx * t0
    val z0 = baseZ + dz * t0

    // Normální výpočet kvadratické rovnice
    val a = conic.a * dx * dx +
            conic.b * dx * dz +
            conic.c * dz * dz

    val b = 2f * conic.a * x0 * dx +
            conic.b * (x0 * dz + z0 * dx) +
            2f * conic.c * z0 * dz +
            conic.d * dx +
            conic.e * dz

    val c = conic.a * x0 * x0 +
            conic.b * x0 * z0 +
            conic.c * z0 * z0 +
            conic.d * x0 +
            conic.e * z0 +
            conic.f

    val result = mutableListOf<Offset>()
    val tol = 1e-2f

    if (abs(a) < 1e-6f) {
        if (abs(b) < 1e-6f) return emptyList()
        val t = -c / b
        val x = x0 + dx * t
        val z = z0 + dz * t
        val pt = Offset(x, -z) // převod zpět do nárysového souřadnicového systému
        if (abs(evaluateConicAt(pt, conic)) < tol) result.add(pt)
        return result
    }

    val discriminant = b * b - 4f * a * c
    if (discriminant < 0f) return emptyList()

    val sqrtDisc = sqrt(discriminant)
    val denom = 2f * a

    val t1 = (-b - sqrtDisc) / denom
    val t2 = (-b + sqrtDisc) / denom

    val p1 = Offset(x0 + dx * t1, -(z0 + dz * t1))
    val p2 = Offset(x0 + dx * t2, -(z0 + dz * t2))

    if (abs(evaluateConicAt(p1, conic)) < tol) result.add(p1)
    if (abs(evaluateConicAt(p2, conic)) < tol) result.add(p2)

    return result
}

fun intersectSegmentWithConicNarys(
    segment: SegmentsNarys,
    conic: ConicSectionNarys
): List<Offset> {
    val aOr = segment.start
    val bOr = segment.end

    val x0 = aOr.x
    val z0 = aOr.z
    val dx = bOr.x - aOr.x
    val dz = bOr.z - aOr.z

    val a = conic.a * dx * dx +
            conic.b * dx * dz +
            conic.c * dz * dz

    val b = 2f * conic.a * x0 * dx +
            conic.b * (x0 * dz + z0 * dx) +
            2f * conic.c * z0 * dz +
            conic.d * dx +
            conic.e * dz

    val c = conic.a * x0 * x0 +
            conic.b * x0 * z0 +
            conic.c * z0 * z0 +
            conic.d * x0 +
            conic.e * z0 +
            conic.f

    val discriminant = b * b - 4f * a * c
    if (discriminant < 0f) return emptyList()

    val sqrtDisc = sqrt(discriminant)
    val denom = 2f * a

    val t1 = (-b - sqrtDisc) / denom
    val t2 = (-b + sqrtDisc) / denom

    return listOf(t1, t2)
        .filter { it in 0f..1f }
        .map { t ->
            val x = x0 + dx * t
            val z = z0 + dz * t
            Offset(x, -z) // Z → -Y převod do nárysu
        }
}
fun closestPointOnSegment(a: Offset, b: Offset, p: Offset): Offset {
    val ab = b - a
    val t = ((p - a).dot(ab)) / ab.getDistanceSquared()
    return when {
        t <= 0f -> a
        t >= 1f -> b
        else -> a + ab * t
    }
}
