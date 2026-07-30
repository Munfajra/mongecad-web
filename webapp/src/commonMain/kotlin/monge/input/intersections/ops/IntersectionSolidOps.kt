package monge.input.intersections.ops

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.fills.segmentSolidFaces
import model.Offset3D
import model.classes.ConicalSurface3D
import model.classes.ConicSection3D
import model.classes.CylindricalSurface3D
import model.classes.Line3D
import model.classes.Matrix3x3
import model.classes.Plane3D
import model.classes.SegmentSolid3D
import model.classes.SphereSurface3D
import model.classes.*
import model.normalize
import monge.input.intersections.addIntersectionPoint3D
import monge.input.intersections.addIntersectionSegment3D
import monge.input.intersections.notifyEmptyIntersection
import state.MongeState
import utils.allocIndex
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Průniky se Solidy (hranol/jehlan). Stěny tělesa bereme jako seřazené 3D polygony
 * přes segmentSolidFaces; každá stěna je ohraničený rovinný n-úhelník, na který
 * průnik s plochou ořezáváme (viz ConicClipping / FaceGeometry).
 */

/** Stěny solidu jako seznamy 3D vrcholů (Offset3D). Prázdné, pokud topologii nelze sestavit. */
private fun solidFaces3D(state: MongeState, solid: SegmentSolid3D): List<List<Offset3D>> =
    segmentSolidFaces(state, solid)?.map { face -> face.map { it.toOffset3D() } } ?: emptyList()

/**
 * Bodové zásahy nekonečné přímky do povrchu úsečkového tělesa. Používá je
 * numerický průnik přímkové plochy; parametrický ořez tvořice provádí volající.
 */
internal fun lineSegmentSolidSurfaceHitPoints(
    state: MongeState,
    solid: SegmentSolid3D,
    linePoint: Offset3D,
    lineDirection: Offset3D,
): List<Offset3D> {
    if ((lineDirection dot lineDirection) < 1e-12f) return emptyList()
    val hits = mutableListOf<Offset3D>()
    for (face in solidFaces3D(state, solid)) {
        val plane = facePlaneFromPolygon(face) ?: continue
        val denom = plane.normal dot lineDirection
        if (abs(denom) < 1e-8f) continue
        val t = -plane.signedDistance(linePoint) / denom
        val point = linePoint + lineDirection * t
        if (pointInPolygon2D(plane.project(point), face.map(plane::project)) &&
            hits.none { (it - point).length() <= 1e-3f }
        ) {
            hits += point
        }
    }
    return hits
}

// ─────────────────────────────────────────────────────────────────────────────
// FÁZE 1 – polyline průniky
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Přímka × Solid → průsečíky se stěnami (0/1/2 body) + tětiva uvnitř tělesa (úsečka),
 * jsou-li body právě dva.
 */
fun intersectLineSegmentSolid(line: Line3D, solid: SegmentSolid3D, state: MongeState) {
    val faces = solidFaces3D(state, solid)
    if (faces.isEmpty()) { notifyEmptyIntersection(state); return }

    val p = Offset3D(line.start.x, line.start.y, line.start.z)
    val d = line.direction.normalize()

    data class Hit(val tLine: Float, val point: Offset3D)
    val hits = mutableListOf<Hit>()
    for (face in faces) {
        val plane = facePlaneFromPolygon(face) ?: continue
        val denom = plane.normal dot d
        if (abs(denom) < 1e-7f) continue                      // rovnoběžná se stěnou
        val t = -(plane.signedDistance(p)) / denom
        val x = p + d * t
        if (pointInPolygon2D(plane.project(x), face.map { plane.project(it) })) {
            hits.add(Hit((x - p) dot d, x))
        }
    }

    val unique = mutableListOf<Hit>()
    for (h in hits.sortedBy { it.tLine }) {
        if (unique.none { (it.point - h.point).length() < 1e-4f }) unique.add(h)
    }
    if (unique.isEmpty()) { notifyEmptyIntersection(state); return }

    var added = false
    val clippedSegment = if (unique.size >= 2) {
        clipLineSegmentToCustomTrim(line, unique.first().point, unique.last().point)
    } else {
        null
    }

    if (clippedSegment != null) {
        val segmentLength = (clippedSegment.second - clippedSegment.first).length()
        addIntersectionPoint3D(state, clippedSegment.first.x, clippedSegment.first.y, clippedSegment.first.z)
        if (segmentLength > 1e-4f) {
            addIntersectionPoint3D(state, clippedSegment.second.x, clippedSegment.second.y, clippedSegment.second.z)
            addIntersectionSegment3D(state, clippedSegment.first, clippedSegment.second)
        }
        added = true
    } else {
        for (h in unique) {
            if (lineTrimContainsPoint(line, h.point)) {
                addIntersectionPoint3D(state, h.point.x, h.point.y, h.point.z)
                added = true
            }
        }
    }

    if (!added) notifyEmptyIntersection(state)
}

private fun lineTrimContainsPoint(line: Line3D, point: Offset3D): Boolean {
    val range = line.customTrimRange ?: return true
    val t = line.paramAtPoint(point) ?: return false
    return range.contains(t, eps = 1e-3f)
}

private fun clipLineSegmentToCustomTrim(
    line: Line3D,
    from: Offset3D,
    to: Offset3D
): Pair<Offset3D, Offset3D>? {
    val range = line.customTrimRange ?: return from to to
    val fromT = line.paramAtPoint(from) ?: return null
    val toT = line.paramAtPoint(to) ?: return null

    val segLo = minOf(fromT, toT)
    val segHi = maxOf(fromT, toT)
    val clipLo = maxOf(segLo, range.min)
    val clipHi = minOf(segHi, range.max)
    if (clipLo > clipHi) return null

    return line.pointAtParam(clipLo) to line.pointAtParam(clipHi)
}

/**
 * Rovina × Solid → rovinný n-úhelník řezu jako smyčka 3D úseček. Pro každou stěnu
 * najdeme úsek, kde ji rovina protne (2 průsečíky s hranami stěny) → jedna hrana řezu.
 */
fun intersectPlaneSegmentSolid(plane: Plane3D, solid: SegmentSolid3D, state: MongeState) {
    val eq = plane.equation
    val faces = solidFaces3D(state, solid)
    if (eq == null || faces.isEmpty()) { notifyEmptyIntersection(state); return }

    val n = Offset3D(eq.a, eq.b, eq.c)
    fun f(p: Offset3D) = (n dot p) + eq.d

    var added = false
    for (face in faces) {
        val crossings = mutableListOf<Offset3D>()
        val m = face.size
        for (i in 0 until m) {
            val a = face[i]; val b = face[(i + 1) % m]
            val fa = f(a); val fb = f(b)
            if ((fa <= 0f && fb > 0f) || (fa > 0f && fb <= 0f)) {
                val t = fa / (fa - fb)
                val x = a + (b - a) * t
                if (crossings.none { (it - x).length() < 1e-4f }) crossings.add(x)
            }
        }
        if (crossings.size >= 2) {
            addIntersectionSegment3D(state, crossings[0], crossings[1])
            added = true
        }
    }
    if (!added) notifyEmptyIntersection(state)
}

private fun addCylinderCapSolidSegments(
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
    faces: List<List<Offset3D>>,
    state: MongeState
): Boolean {
    var added = false
    val segments = mutableListOf<Pair<Offset3D, Offset3D>>()

    for (cap in cylinderCapPlanes(state, cylinder, conic)) {
        val capD = cap.normal dot cap.point

        for (face in faces) {
            val facePlane = facePlaneFromPolygon(face) ?: continue
            val dir = cap.normal cross facePlane.normal
            val dirLen2 = dir dot dir
            if (dirLen2 < 1e-10f) continue

            val faceD = facePlane.normal dot facePlane.origin
            val p0 = ((facePlane.normal cross dir) * capD + (dir cross cap.normal) * faceD) * (1f / dirLen2)
            val faceIntervals = clipLineToPolygon2D(
                q0 = facePlane.project(p0),
                qd = Offset(dir dot facePlane.u, dir dot facePlane.v),
                poly = face.map { facePlane.project(it) }
            )
            if (faceIntervals.isEmpty()) continue

            val diskIntervals = clipLineToCylinderCapDisk(cylinder, conic, p0, dir)
            if (diskIntervals.isEmpty()) continue

            for ((s1, s2) in intervalIntersections(faceIntervals, diskIntervals)) {
                if (s2 - s1 < 1e-4f || !s1.isFinite() || !s2.isFinite()) continue
                val a = p0 + dir * s1
                val b = p0 + dir * s2
                if ((b - a).length() < 1e-4f) continue
                if (segments.none { sameSegment(it, a, b) }) {
                    segments += a to b
                }
            }
        }
    }

    for ((a, b) in segments) {
        addIntersectionSegment3D(state, a, b)
        added = true
    }
    return added
}

private fun sameSegment(segment: Pair<Offset3D, Offset3D>, a: Offset3D, b: Offset3D, eps: Float = 1e-3f): Boolean =
    ((segment.first - a).length() <= eps && (segment.second - b).length() <= eps) ||
        ((segment.first - b).length() <= eps && (segment.second - a).length() <= eps)

private fun cylinderGeneratorFaceSegments(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
    face: List<Offset3D>,
    plane: FacePlane,
    planeD: Float,
): List<Pair<Offset3D, Offset3D>> {
    val w = cylinder.direction
    val face2D = face.map { plane.project(it) }
    val qd = Offset(w dot plane.u, w dot plane.v)
    if (qd.x * qd.x + qd.y * qd.y < 1e-12f) return emptyList()

    val segments = mutableListOf<Pair<Offset3D, Offset3D>>()
    for (base in cylinderBasePointsInPlane(conic, plane.normal, planeD)) {
        val heightIntervals = finiteCylinderGeneratorIntervals(state, cylinder, conic, base)
        val faceIntervals = clipLineToPolygon2D(
            q0 = plane.project(base),
            qd = qd,
            poly = face2D
        )
        for ((s1, s2) in intervalIntersections(faceIntervals, heightIntervals)) {
            if (s2 - s1 < 1e-4f || !s1.isFinite() || !s2.isFinite()) continue
            val a = base + w * s1
            val b = base + w * s2
            if ((b - a).length() < 1e-4f) continue
            if (pointOnFiniteCylinder(state, cylinder, a) &&
                pointOnFiniteCylinder(state, cylinder, b) &&
                segments.none { sameSegment(it, a, b) }
            ) {
                segments += a to b
            }
        }
    }
    return segments
}

private fun finiteCylinderGeneratorIntervals(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
    base: Offset3D,
): List<Pair<Float, Float>> {
    val w = cylinder.direction
    val ts = mutableListOf<Float>()
    for (cap in cylinderCapPlanes(state, cylinder, conic)) {
        val denom = cap.normal dot w
        if (abs(denom) < 1e-9f) continue
        val t = (cap.normal dot (cap.point - base)) / denom
        if (ts.none { abs(it - t) <= 1e-4f }) ts += t
    }

    if (ts.size < 2) return listOf(Float.NEGATIVE_INFINITY to Float.POSITIVE_INFINITY)
    val lo = ts.minOrNull() ?: return emptyList()
    val hi = ts.maxOrNull() ?: return emptyList()
    return if (hi - lo > 1e-5f) listOf(lo to hi) else emptyList()
}

private fun cylinderSolidEllipseClipSamples(ellipse: ConicSection3D, planeN: Offset3D, cylinderDir: Offset3D): Int {
    val a = ellipse.a ?: return 1080
    val b = ellipse.b ?: return 1080
    val major = maxOf(abs(a), abs(b))
    val minor = minOf(abs(a), abs(b)).coerceAtLeast(1e-6f)
    val ratio = major / minor
    val nLen = planeN.length()
    val wLen = cylinderDir.length()
    val parallelness = if (nLen < 1e-9f || wLen < 1e-9f) 1f else abs(planeN dot cylinderDir) / (nLen * wLen)

    return when {
        ratio > 1000f || parallelness < 0.002f -> 65536
        ratio > 300f || parallelness < 0.006f -> 32768
        ratio > 100f || parallelness < 0.02f -> 16384
        ratio > 30f -> 4096
        else -> 1080
    }
}

private fun cylinderBasePointsInPlane(
    conic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float,
): List<Offset3D> {
    val ls = planeN dot conic.u
    val lt = planeN dot conic.v
    val l0 = (planeN dot conic.p0) + planeD
    val lineLen2 = ls * ls + lt * lt
    val scale = planeN.length().coerceAtLeast(1f)
    if (lineLen2 < 1e-12f * scale * scale) return emptyList()

    val s0 = -ls * l0 / lineLen2
    val t0 = -lt * l0 / lineLen2
    val dirLen = sqrt(lineLen2)
    val ds = -lt / dirLen
    val dt = ls / dirLen
    val (qa, qb, qc) = conicQuadraticLocal(
        conic,
        sa = s0, sb = ds,
        ta = t0, tb = dt,
        ga = 1f, gb = 0f
    )
    val roots = solveQuadraticLocal(qa, qb, qc)

    val points = mutableListOf<Offset3D>()
    for (r in roots) {
        val point = conic.p0 + conic.u * (s0 + ds * r) + conic.v * (t0 + dt * r)
        if (points.none { (it - point).length() <= 1e-3f }) points += point
    }
    return points
}

private fun conicQuadraticLocal(
    conic: ConicSection3D,
    sa: Float, sb: Float,
    ta: Float, tb: Float,
    ga: Float, gb: Float,
): Triple<Float, Float, Float> {
    val m = conic.matrix
    val m01 = (m.m01 + m.m10) * 0.5f
    val m02 = (m.m02 + m.m20) * 0.5f
    val m12 = (m.m12 + m.m21) * 0.5f

    val a = m.m00 * sb * sb + m.m11 * tb * tb + m.m22 * gb * gb +
        2f * m01 * sb * tb + 2f * m02 * sb * gb + 2f * m12 * tb * gb
    val b = 2f * m.m00 * sa * sb + 2f * m.m11 * ta * tb + 2f * m.m22 * ga * gb +
        2f * m01 * (sa * tb + sb * ta) +
        2f * m02 * (sa * gb + sb * ga) +
        2f * m12 * (ta * gb + tb * ga)
    val c = m.m00 * sa * sa + m.m11 * ta * ta + m.m22 * ga * ga +
        2f * m01 * sa * ta + 2f * m02 * sa * ga + 2f * m12 * ta * ga
    return Triple(a, b, c)
}

private fun solveQuadraticLocal(a: Float, b: Float, c: Float): List<Float> {
    val scale = (abs(a) + abs(b) + abs(c)).coerceAtLeast(1e-12f)
    if (abs(a) < 1e-9f * scale) {
        if (abs(b) < 1e-9f * scale) return emptyList()
        return listOf(-c / b)
    }
    val disc = b * b - 4f * a * c
    val tol = 1e-6f * (b * b + abs(4f * a * c)).coerceAtLeast(1f)
    if (disc < -tol) return emptyList()
    if (disc <= tol) return listOf(-b / (2f * a))
    val sq = sqrt(disc)
    val t1 = (-b - sq) / (2f * a)
    val t2 = (-b + sq) / (2f * a)
    return if (abs(t1 - t2) <= 1e-4f * (abs(t1) + abs(t2)) + 1e-6f) {
        listOf((t1 + t2) / 2f)
    } else {
        listOf(t1, t2)
    }
}

/**
 * Solid × Solid → lomená prostorová čára: pro každou dvojici stěn najdeme průsečnici
 * jejich rovin oříznutou na oba stěnové polygony → 3D úsečka.
 */
fun intersectSegmentSolidSegmentSolid(a: SegmentSolid3D, b: SegmentSolid3D, state: MongeState) {
    val facesA = solidFaces3D(state, a)
    val facesB = solidFaces3D(state, b)
    if (facesA.isEmpty() || facesB.isEmpty()) { notifyEmptyIntersection(state); return }

    var added = false
    for (fA in facesA) {
        val pA = facePlaneFromPolygon(fA) ?: continue
        for (fB in facesB) {
            val pB = facePlaneFromPolygon(fB) ?: continue
            val dir = pA.normal cross pB.normal
            val uLen2 = dir dot dir
            if (uLen2 < 1e-10f) continue                       // rovnoběžné roviny

            // bod na průsečnici obou rovin
            val cA = pA.normal dot pA.origin
            val cB = pB.normal dot pB.origin
            val p0 = ((pB.normal cross dir) * cA + (dir cross pA.normal) * cB) * (1f / uLen2)

            // direction leží v obou rovinách → projekce zachová parametr s
            val segA = clipLineToPolygon2D(pA.project(p0), Offset(dir dot pA.u, dir dot pA.v), fA.map { pA.project(it) })
            if (segA.isEmpty()) continue
            val segB = clipLineToPolygon2D(pB.project(p0), Offset(dir dot pB.u, dir dot pB.v), fB.map { pB.project(it) })
            if (segB.isEmpty()) continue

            for ((s1, s2) in intervalIntersections(segA, segB)) {
                if (s2 - s1 < 1e-4f) continue
                addIntersectionSegment3D(state, p0 + dir * s1, p0 + dir * s2)
                added = true
            }
        }
    }
    if (!added) notifyEmptyIntersection(state)
}

/** Průnik dvou množin parametrických intervalů (na téže přímce). */
private fun intervalIntersections(a: List<Pair<Float, Float>>, b: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    val out = mutableListOf<Pair<Float, Float>>()
    for ((a0, a1) in a) for ((b0, b1) in b) {
        val lo = maxOf(a0, b0); val hi = minOf(a1, b1)
        if (hi > lo) out.add(lo to hi)
    }
    return out
}

// ─────────────────────────────────────────────────────────────────────────────
// FÁZE 2 – kuželosečkové průniky
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Koule × Solid → kružnice (jako elipsa a=b) oříznuté na jednotlivé stěny.
 * Na každé stěně spočítáme řez koule rovinou stěny a oblouky ořízneme na n-úhelník.
 */
fun intersectSphereSegmentSolid(sphere: SphereSurface3D, solid: SegmentSolid3D, state: MongeState) {
    val centerP = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId }
    val faces = solidFaces3D(state, solid)
    if (centerP == null || faces.isEmpty()) { notifyEmptyIntersection(state); return }

    val c = Offset3D(centerP.x, centerP.y, centerP.z)
    val r = sphere.radius
    var added = false
    for (face in faces) {
        val plane = facePlaneFromPolygon(face) ?: continue
        val dist = plane.signedDistance(c)
        if (abs(dist) > r - 1e-4f * r) continue                 // míjí stěnu nebo se jen dotýká
        val rho = sqrt((r * r - dist * dist).coerceAtLeast(0f))
        if (rho < 1e-5f) continue
        val center = c - plane.normal * dist
        val circle = ConicSection3D(
            p0 = center, u = plane.u, v = plane.v,
            matrix = Matrix3x3.fromCoefficients(1f, 0f, 1f, 0f, 0f, -rho * rho),
            rawName = "k", a = rho, b = rho, creationIndex = allocIndex(state)
        )
        val arcs = clipEllipseConicToFace(circle, face)
        if (arcs.isNotEmpty()) {
            addClippedEllipseArcs(state, circle, arcs)
            added = true
        }
    }
    if (!added) notifyEmptyIntersection(state)
}

/** Kužel × Solid – řez kužele rovinou každé stěny oříznutý na stěnu (zatím jen eliptické řezy). */
fun intersectConeSegmentSolid(cone: ConicalSurface3D, solid: SegmentSolid3D, state: MongeState) {
    val conic = state.conics3D.find { it.id == cone.directrixId }
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId }
    val faces = solidFaces3D(state, solid)
    if (conic == null || apexP == null || faces.isEmpty()) { notifyEmptyIntersection(state); return }

    val apex = Offset3D(apexP.x, apexP.y, apexP.z)
    var added = false
    for (face in faces) {
        val plane = facePlaneFromPolygon(face) ?: continue
        val planeD = -(plane.normal dot plane.origin)
        val section = coneSectionConic(cone, conic, apex, plane.normal, planeD, allocIndex(state)) ?: continue
        val (kind, sectionConic) = section
        when (kind) {
            ConicKindPublic.ELLIPSE -> {
                val arcs = clipEllipseConicToFace(sectionConic, face)
                if (arcs.isNotEmpty()) { addClippedEllipseArcs(state, sectionConic, arcs); added = true }
            }
            ConicKindPublic.PARABOLA -> {
                val before = state.conics3D.size
                addClippedParabolaArcs(state, sectionConic, face)
                if (state.conics3D.size > before) added = true
            }
            ConicKindPublic.HYPERBOLA -> {
                val before = state.conics3D.size
                addClippedHyperbolaArcs(state, sectionConic, face)
                if (state.conics3D.size > before) added = true
            }
        }
    }
    if (!added) notifyEmptyIntersection(state)
}

/** Válec × Solid – řez válce rovinou každé stěny oříznutý na stěnu. */
fun intersectCylinderSegmentSolid(cylinder: CylindricalSurface3D, solid: SegmentSolid3D, state: MongeState) {
    val conic = state.conics3D.find { it.id == cylinder.directrixId }
    val faces = solidFaces3D(state, solid)
    if (conic == null || faces.isEmpty()) { notifyEmptyIntersection(state); return }

    var added = false
    val generatorSegments = mutableListOf<Pair<Offset3D, Offset3D>>()
    for (face in faces) {
        val plane = facePlaneFromPolygon(face) ?: continue
        val planeD = -(plane.normal dot plane.origin)
        val w = cylinder.direction
        val parallelTol = 1e-6f * plane.normal.length().coerceAtLeast(1f) * w.length().coerceAtLeast(1f)
        if (abs(plane.normal dot w) <= parallelTol) {
            generatorSegments += cylinderGeneratorFaceSegments(state, cylinder, conic, face, plane, planeD)
            continue
        }

        val ellipse = cylinderSectionEllipse(cylinder, conic, plane.normal, planeD, allocIndex(state)) ?: continue
        val face2D = face.map { plane.project(it) }
        val arcs = clipEllipseConicByPredicate(ellipse, samples = cylinderSolidEllipseClipSamples(ellipse, plane.normal, w)) { point ->
            pointInPolygon2D(plane.project(point), face2D) &&
                pointOnFiniteCylinder(state, cylinder, point)
        }
        if (arcs.isNotEmpty()) {
            addClippedEllipseArcs(state, ellipse, arcs)
            added = true
        }
    }
    val uniqueGeneratorSegments = mutableListOf<Pair<Offset3D, Offset3D>>()
    for ((a, b) in generatorSegments) {
        if (uniqueGeneratorSegments.none { sameSegment(it, a, b) }) uniqueGeneratorSegments += a to b
    }
    for ((a, b) in uniqueGeneratorSegments) {
        addIntersectionSegment3D(state, a, b)
        added = true
    }
    if (addCylinderCapSolidSegments(cylinder, conic, faces, state)) added = true
    if (!added) notifyEmptyIntersection(state)
}
