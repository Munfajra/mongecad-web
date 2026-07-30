package monge.input.intersections.ops

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.fills.segmentSolidFaces
import model.Offset3D
import model.ProjectionMode
import model.SolidOfRevolutionNarys
import model.SolidOfRevolutionPudorys
import model.classes.*
import model.normalize
import monge.input.intersections.*
import monge.input.intersections.IntersectionOperand.SolidOfRevolutionOp
import monge.input.planeobjects.conicsections.addHyperbolaInputsForLift
import monge.input.planeobjects.conicsections.canonizeParabolaFrame
import monge.input.planeobjects.conicsections.extractVertexAndFocusFromConic
import monge.input.planeobjects.conicsections.vertexFocus3DFromLocalConic
import monge.input.ruledsurface.ruledSurfaceFamilyIsClosed
import monge.input.ruledsurface.ruledSurfaceTrimmedPrimaryGrids
import monge.input.ruledsurface.sampleRuledSurfaceTrimmedPrimaryFamilies
import monge.input.ruledsurface.sphericalConoidContactCurve
import state.MongeState
import utils.allocIndex
import utils.update2DSnapshots
import kotlin.math.*

/*
 * Specializované funkce pro průniky jednotlivých dvojic objektů.
 *
 * Sem patří vlastní geometrická konstrukce – dispatcher je volá s konkrétně typovanými
 * operandy. Každá funkce dostává `state`, aby do něj mohla zapsat výsledné body / křivky /
 * průsečnice ve všech potřebných projekcích (půdorys, nárys, bokorys, axo).
 *
 * Konvence: operandy přicházejí v kanonickém pořadí podle IntersectionKind, takže např.
 * přímka × rovina vždy dorazí jako (line, plane), ne naopak.
 */

/**
 * Přímka × přímka → průsečík (bod). Pokud jsou přímky rovnoběžné, totožné nebo
 * mimoběžné, průnik bodem neexistuje → prázdný průnik.
 */
fun intersectLineLine(a: Line3D, b: Line3D, state: MongeState) {
    val p1 = Offset3D(a.start.x, a.start.y, a.start.z)
    val p2 = Offset3D(b.start.x, b.start.y, b.start.z)
    val d1 = a.direction.normalize()
    val d2 = b.direction.normalize()

    val cross = d1 cross d2
    val denom = cross dot cross
    if (denom < 1e-6f) {            // rovnoběžné nebo totožné → žádný osamocený bod
        notifyEmptyIntersection(state)
        return
    }

    val r = p2 - p1
    val t = ((r cross d2) dot cross) / denom
    val s = ((r cross d1) dot cross) / denom
    val pA = p1 + d1 * t
    val pB = p2 + d2 * s
    if ((pA - pB).length() > 1e-2f) {   // mimoběžné → neprotínají se
        notifyEmptyIntersection(state)
        return
    }

    if (!lineTrimContainsPoint(a, pA) || !lineTrimContainsPoint(b, pA)) {
        notifyEmptyIntersection(state)
        return
    }

    addIntersectionPoint3D(state, pA.x, pA.y, pA.z)
}

/**
 * Přímka × rovina → průsečík (bod). Pokud je přímka rovnoběžná s rovinou a neleží
 * v ní, je průnik prázdný. Pokud v rovině leží, je výsledkem sama přímka – ta už
 * existuje, takže nic nepřidáváme.
 */
fun intersectLinePlane(line: Line3D, plane: Plane3D, state: MongeState) {
    val eq = plane.equation
    if (eq == null) {               // bez rovnice neumíme průnik spočítat
        notifyEmptyIntersection(state)
        return
    }
    val n = Offset3D(eq.a, eq.b, eq.c)
    val nLen = n.length().coerceAtLeast(1f)
    val p = Offset3D(line.start.x, line.start.y, line.start.z)
    val dir = line.direction.normalize()

    val denom = n dot dir
    val fp = (n dot p) + eq.d

    if (abs(denom) < 1e-6f * nLen) {
        // přímka je rovnoběžná s rovinou
        if (abs(fp) < 1e-3f * nLen) {
            // leží v rovině → výsledkem je sama tato přímka, nic nepřidáváme
            state.consInfo.value = "Přímka leží v rovině – průnikem je sama přímka."
        } else {
            notifyEmptyIntersection(state)
        }
        return
    }

    val t = -fp / denom
    val pt = p + dir * t
    if (!lineTrimContainsPoint(line, pt)) {
        notifyEmptyIntersection(state)
        return
    }
    addIntersectionPoint3D(state, pt.x, pt.y, pt.z)
}

/**
 * Přímka × kužel → body/úsečka. Boční plocha je kvadrika daná vrcholem a řídicí
 * kuželosečkou; k ní se přičítá průnik s podstavným eliptickým diskem.
 */
fun intersectLineCone(line: Line3D, cone: ConicalSurface3D, state: MongeState) {
    val conic = state.conics3D.find { it.id == cone.directrixId }
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId }
    if (conic == null || apexP == null) { notifyEmptyIntersection(state); return }

    val a3 = Offset3D(apexP.x, apexP.y, apexP.z)
    val p0 = conic.p0; val u = conic.u; val v = conic.v
    val n = u cross v
    val p = Offset3D(line.start.x, line.start.y, line.start.z)
    val d = line.direction.normalize()

    // splývá přímka s površkou? (prochází vrcholem a leží na ploše) → úsečka podstava–vrchol
    val coneScale = (a3 - p0).length().coerceAtLeast(1f)
    if (perpDistanceToLine(a3, p, d) < 1e-3f * coneScale) {
        val nd = n dot d
        if (abs(nd) > 1e-9f) {
            val lambda = -(n dot (a3 - p0)) / nd
            val base = a3 + d * lambda                 // průsečík přímky s rovinou podstavy
            val s = (base - p0) dot u; val t = (base - p0) dot v
            if (isOnConic(conic, s, t)) {
                addLineIntersectionResults(state, line, emptyList(), listOf(LineSegmentHit(base, a3)))
                return
            }
        }
        // prochází vrcholem, ale není to površka → vrchol + případný zásah podstavného disku
        val baseDisk = lineEllipticDiskIntersections(
            p = p,
            d = d,
            planePoint = conic.p0,
            planeNormal = n,
            conic = conic,
            projectToDisk = { it }
        )
        addLineIntersectionResults(state, line, listOf(a3) + baseDisk.points, baseDisk.segments)
        return
    }

    // g(t) = n·(X(t) - A) ; středová projekce: koeficient k/g(t)
    val g0 = n dot (p - a3); val g1 = n dot d
    val k = -(n dot (a3 - p0))
    val apU = (a3 - p0) dot u; val apV = (a3 - p0) dot v
    val au0 = (p - a3) dot u; val au1 = d dot u
    val av0 = (p - a3) dot v; val av1 = d dot v

    // homogenní rovinné souřadnice [S, T, G] = [s·g, t·g, g] (vše afinní v t)
    val (qa, qb, qc) = conicSurfaceQuadratic(
        conic,
        Sa = apU * g0 + k * au0, Sb = apU * g1 + k * au1,
        Ta = apV * g0 + k * av0, Tb = apV * g1 + k * av1,
        Ga = g0, Gb = g1
    )
    val sidePoints = solveLineSurfacePoints(p, d, qa, qb, qc) { x ->
        pointOnFiniteCone(state, cone, x)
    }
    val baseDisk = lineEllipticDiskIntersections(
        p = p,
        d = d,
        planePoint = conic.p0,
        planeNormal = n,
        conic = conic,
        projectToDisk = { it }
    )
    addLineIntersectionResults(state, line, sidePoints + baseDisk.points, baseDisk.segments)
}

/**
 * Přímka × válec → body/úsečka. Boční plocha je kvadrika daná řídicí kuželosečkou
 * a směrem tvořicích přímek; k ní se přičítají průniky s podstavnými eliptickými disky.
 */
fun intersectLineCylinder(line: Line3D, cylinder: CylindricalSurface3D, state: MongeState) {
    val conic = state.conics3D.find { it.id == cylinder.directrixId }
    if (conic == null) { notifyEmptyIntersection(state); return }

    val w = cylinder.direction
    val p0 = conic.p0; val u = conic.u; val v = conic.v
    val n = u cross v
    val nw = n dot w
    if (abs(nw) < 1e-9f) { notifyEmptyIntersection(state); return } // tvořice rovnoběžné s rovinou

    val p = Offset3D(line.start.x, line.start.y, line.start.z)
    val d = line.direction.normalize()

    // splývá přímka s površkou? (rovnoběžná s tvořicemi a leží na ploše) → úsečka podstava–horní hrana
    val dxw = d cross w
    if ((dxw dot dxw) < 1e-9f * (w dot w)) {
        val lambda = (n dot (p - p0)) / nw
        val base = p - w * lambda                      // průmět do roviny podstavy podél tvořice
        val s = (base - p0) dot u; val t = (base - p0) dot v
        if (isOnConic(conic, s, t)) {
            val top = cylinderTopPoint(state, cylinder, base, w)
            if (top != null) addLineIntersectionResults(state, line, emptyList(), listOf(LineSegmentHit(base, top)))
            else addLineIntersectionResults(state, line, listOf(base), emptyList())
            return
        }
        val baseDisk = lineEllipticDiskIntersections(
            p = p,
            d = d,
            planePoint = conic.p0,
            planeNormal = n,
            conic = conic,
            projectToDisk = { it }
        )
        val topDisk = lineCylinderTopDiskIntersections(state, p, d, cylinder)
        addLineIntersectionResults(
            state,
            line,
            baseDisk.points + topDisk.points,
            baseDisk.segments + topDisk.segments
        )
        return
    }

    val rel = p - p0

    // rovinné souřadnice promítnutého bodu (afinní v t), homogenní složka G = 1
    val st0 = projectedCylinderLocalCoordinates(conic, w, rel)
    val stD = projectedCylinderLocalCoordinates(conic, w, d)
    if (st0 == null || stD == null) { notifyEmptyIntersection(state); return }
    val (qa, qb, qc) = conicSurfaceQuadratic(
        conic,
        Sa = st0.first, Sb = stD.first,
        Ta = st0.second, Tb = stD.second,
        Ga = 1f, Gb = 0f
    )
    val sidePoints = solveLineSurfacePoints(p, d, qa, qb, qc) { x ->
        pointOnFiniteCylinder(state, cylinder, x)
    }
    val baseDisk = lineEllipticDiskIntersections(
        p = p,
        d = d,
        planePoint = conic.p0,
        planeNormal = n,
        conic = conic,
        projectToDisk = { it }
    )
    val topDisk = lineCylinderTopDiskIntersections(state, p, d, cylinder)
    addLineIntersectionResults(
        state,
        line,
        sidePoints + baseDisk.points + topDisk.points,
        baseDisk.segments + topDisk.segments
    )
}

/**
 * Přímka × kulová plocha → 0/1/2 body. Pro tečnu vznikne 1 (dvojnásobný) bod.
 */
fun intersectLineSphere(line: Line3D, sphere: SphereSurface3D, state: MongeState) {
    val centerP = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId }
    if (centerP == null) { notifyEmptyIntersection(state); return }

    val c = Offset3D(centerP.x, centerP.y, centerP.z)
    val p = Offset3D(line.start.x, line.start.y, line.start.z)
    val d = line.direction.normalize()
    val e = p - c
    val along = e dot d
    val perp2 = (e dot e) - along * along       // čtverec vzdálenosti přímky od středu
    val r = sphere.radius
    val dist = sqrt(perp2.coerceAtLeast(0f))
    val tol = 1e-3f * r                          // pásmo tečny relativně k poloměru

    when {
        dist > r + tol -> notifyEmptyIntersection(state)         // přímka míjí kouli
        dist >= r - tol -> {                                     // tečna → 1 bod
            val x = p + d * (-along)
            addLineIntersectionResults(state, line, listOf(x), emptyList())
        }
        else -> {                                               // sečna → 2 body
            val h = sqrt((r * r - perp2).coerceAtLeast(0f))
            val points = listOf(-along - h, -along + h).map { t -> p + d * t }
            addLineIntersectionResults(state, line, points, emptyList())
        }
    }
}

/** Kvadratické koeficienty (v t) formy [S T G]·M·[S T G]ᵀ, kde S,T,G jsou afinní v t. */
private fun conicSurfaceQuadratic(
    conic: model.classes.ConicSection3D,
    Sa: Float, Sb: Float,
    Ta: Float, Tb: Float,
    Ga: Float, Gb: Float,
): Triple<Float, Float, Float> {
    val m = conic.matrix
    val m00 = m.m00; val m11 = m.m11; val m22 = m.m22
    val m01 = (m.m01 + m.m10) * 0.5f
    val m02 = (m.m02 + m.m20) * 0.5f
    val m12 = (m.m12 + m.m21) * 0.5f

    val a = m00 * Sb * Sb + m11 * Tb * Tb + m22 * Gb * Gb +
            2f * m01 * Sb * Tb + 2f * m02 * Sb * Gb + 2f * m12 * Tb * Gb
    val b = 2f * m00 * Sa * Sb + 2f * m11 * Ta * Tb + 2f * m22 * Ga * Gb +
            2f * m01 * (Sa * Tb + Sb * Ta) +
            2f * m02 * (Sa * Gb + Sb * Ga) +
            2f * m12 * (Ta * Gb + Tb * Ga)
    val c = m00 * Sa * Sa + m11 * Ta * Ta + m22 * Ga * Ga +
            2f * m01 * Sa * Ta + 2f * m02 * Sa * Ga + 2f * m12 * Ta * Ga
    return Triple(a, b, c)
}

/** Vyřeší a·t² + b·t + c = 0 a přidá body X = P + t·d (0/1/2). Prázdné → dialog. */
private fun solveLineSurfaceAndAdd(
    state: MongeState,
    p: Offset3D,
    d: Offset3D,
    a: Float,
    b: Float,
    c: Float,
    keep: (Offset3D) -> Boolean = { true },
) {
    addLineIntersectionResults(state, solveLineSurfacePoints(p, d, a, b, c, keep), emptyList())
}

private fun solveLineSurfacePoints(
    p: Offset3D,
    d: Offset3D,
    a: Float,
    b: Float,
    c: Float,
    keep: (Offset3D) -> Boolean = { true },
): List<Offset3D> {
    return solveQuadratic(a, b, c)
        .map { t -> p + d * t }
        .filter(keep)
}

private fun lineCylinderTopDiskIntersections(
    state: MongeState,
    p: Offset3D,
    d: Offset3D,
    cylinder: CylindricalSurface3D,
): LineDiskHit {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return LineDiskHit()
    val (topPoint, topNormal) = cylinderTopPlane(state, cylinder) ?: return LineDiskHit()
    return lineEllipticDiskIntersections(
        p = p,
        d = d,
        planePoint = topPoint,
        planeNormal = topNormal,
        conic = conic,
        projectToDisk = { x -> projectCylinderPointToBase(conic, cylinder.direction, x) }
    )
}

private fun projectedCylinderLocalCoordinates(
    conic: ConicSection3D,
    w: Offset3D,
    vector: Offset3D,
): Pair<Float, Float>? {
    val projected = projectCylinderVectorToBasePlane(conic, w, vector) ?: return null
    return conicLocalCoordinates(conic, projected)
}

private fun projectCylinderVectorToBasePlane(
    conic: ConicSection3D,
    w: Offset3D,
    vector: Offset3D,
): Offset3D? {
    val n = conic.u cross conic.v
    val nw = n dot w
    if (abs(nw) < 1e-9f) return null
    return vector - w * ((n dot vector) / nw)
}

private fun conicLocalCoordinates(conic: ConicSection3D, vector: Offset3D): Pair<Float, Float>? {
    val u = conic.u
    val v = conic.v
    val uu = u dot u
    val uv = u dot v
    val vv = v dot v
    val det = uu * vv - uv * uv
    if (abs(det) < 1e-12f) return null
    val ru = vector dot u
    val rv = vector dot v
    val s = (vv * ru - uv * rv) / det
    val t = (uu * rv - uv * ru) / det
    return s to t
}

private fun lineEllipticDiskIntersections(
    p: Offset3D,
    d: Offset3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    conic: ConicSection3D,
    projectToDisk: (Offset3D) -> Offset3D?,
): LineDiskHit {
    val nLen = planeNormal.length()
    val dLen = d.length()
    if (nLen < 1e-9f || dLen < 1e-9f) return LineDiskHit()

    val denom = planeNormal dot d
    val dist = planeNormal dot (p - planePoint)
    val scale = ((p - planePoint).length() + dLen + (conic.a ?: 0f) + (conic.b ?: 0f)).coerceAtLeast(1f)
    val parallelTol = 1e-7f * nLen * dLen
    val planeTol = 1e-4f * nLen * scale

    if (abs(denom) > parallelTol) {
        val t = -dist / denom
        val x = p + d * t
        val diskPoint = projectToDisk(x) ?: return LineDiskHit()
        return if (pointInsideEllipticDisk(conic, diskPoint)) LineDiskHit(points = listOf(x)) else LineDiskHit()
    }
    if (abs(dist) > planeTol) return LineDiskHit()

    val q0 = projectToDisk(p) ?: return LineDiskHit()
    val q1 = projectToDisk(p + d) ?: return LineDiskHit()
    return lineInEllipticDiskPlaneIntersections(q0, q1 - q0, conic) { t -> p + d * t }
}

private fun lineInEllipticDiskPlaneIntersections(
    p: Offset3D,
    d: Offset3D,
    conic: ConicSection3D,
    originalAt: (Float) -> Offset3D,
): LineDiskHit {
    val el = ellipseParamsFromConic3D(conic) ?: return LineDiskHit()
    val u = el.uRot.normalizeOrNull() ?: return LineDiskHit()
    val v = el.vRot.normalizeOrNull() ?: return LineDiskHit()
    val a = el.a.coerceAtLeast(1e-6f)
    val b = el.b.coerceAtLeast(1e-6f)
    val rel = p - el.center3D
    val x0 = (rel dot u) / a
    val y0 = (rel dot v) / b
    val dx = (d dot u) / a
    val dy = (d dot v) / b
    if (dx * dx + dy * dy < 1e-12f) return LineDiskHit()

    val roots = solveQuadratic(
        dx * dx + dy * dy,
        2f * (x0 * dx + y0 * dy),
        x0 * x0 + y0 * y0 - 1f
    ).sorted()
    return when (roots.size) {
        0 -> LineDiskHit()
        1 -> LineDiskHit(points = listOf(originalAt(roots.first())))
        else -> {
            val from = originalAt(roots.first())
            val to = originalAt(roots.last())
            if ((to - from).length() <= 1e-3f) LineDiskHit(points = listOf((from + to) * 0.5f))
            else LineDiskHit(segments = listOf(LineSegmentHit(from, to)))
        }
    }
}

private fun pointInsideEllipticDisk(conic: ConicSection3D, point: Offset3D): Boolean {
    val el = ellipseParamsFromConic3D(conic) ?: return false
    val u = el.uRot.normalizeOrNull() ?: return false
    val v = el.vRot.normalizeOrNull() ?: return false
    val a = el.a.coerceAtLeast(1e-6f)
    val b = el.b.coerceAtLeast(1e-6f)
    val rel = point - el.center3D
    val x = (rel dot u) / a
    val y = (rel dot v) / b
    return x * x + y * y <= 1.002f
}

private fun projectCylinderPointToBase(
    conic: ConicSection3D,
    w: Offset3D,
    point: Offset3D,
): Offset3D? {
    val n = conic.u cross conic.v
    val nw = n dot w
    if (abs(nw) < 1e-9f) return null
    return point - w * ((n dot (point - conic.p0)) / nw)
}

internal data class CylinderCapPlane(
    val point: Offset3D,
    val normal: Offset3D
)

internal fun cylinderCapPlanes(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D
): List<CylinderCapPlane> {
    val baseNormal = (conic.u cross conic.v).normalizeOrNull() ?: return emptyList()
    val out = mutableListOf(CylinderCapPlane(conic.p0, baseNormal))
    cylinderTopPlane(state, cylinder)?.let { (topPoint, topNormal) ->
        out += CylinderCapPlane(topPoint, topNormal)
    }
    return out
}

internal fun clipLineToCylinderCapDisk(
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
    linePoint: Offset3D,
    lineDir: Offset3D
): List<Pair<Float, Float>> {
    val q0 = projectCylinderPointToBase(conic, cylinder.direction, linePoint) ?: return emptyList()
    val q1 = projectCylinderPointToBase(conic, cylinder.direction, linePoint + lineDir) ?: return emptyList()
    val qd = q1 - q0
    val el = ellipseParamsFromConic3D(conic) ?: return emptyList()
    val u = el.uRot.normalizeOrNull() ?: return emptyList()
    val v = el.vRot.normalizeOrNull() ?: return emptyList()
    val a = el.a.coerceAtLeast(1e-6f)
    val b = el.b.coerceAtLeast(1e-6f)
    val rel = q0 - el.center3D
    val x0 = (rel dot u) / a
    val y0 = (rel dot v) / b
    val dx = (qd dot u) / a
    val dy = (qd dot v) / b
    val qa = dx * dx + dy * dy
    if (qa < 1e-12f) return emptyList()
    val qb = 2f * (x0 * dx + y0 * dy)
    val qc = x0 * x0 + y0 * y0 - 1f
    val roots = solveQuadratic(qa, qb, qc).sorted()
    return when (roots.size) {
        0 -> if (qc <= 0f) listOf(Float.NEGATIVE_INFINITY to Float.POSITIVE_INFINITY) else emptyList()
        1 -> listOf(roots.first() to roots.first())
        else -> listOf(roots.first() to roots.last())
    }
}

private fun addLineIntersectionResults(
    state: MongeState,
    line: Line3D,
    points: List<Offset3D>,
    segments: List<LineSegmentHit>,
) {
    addLineIntersectionResults(
        state = state,
        points = points.filter { lineTrimContainsPoint(line, it) },
        segments = segments.mapNotNull { clipSegmentToLineTrim(line, it) }
    )
}

private fun lineTrimContainsPoint(line: Line3D, point: Offset3D): Boolean {
    val range = line.customTrimRange ?: return true
    val t = line.paramAtPoint(point) ?: return false
    return range.contains(t, eps = 1e-3f)
}

private fun clipSegmentToLineTrim(line: Line3D, segment: LineSegmentHit): LineSegmentHit? {
    val range = line.customTrimRange ?: return segment
    val fromT = line.paramAtPoint(segment.from) ?: return null
    val toT = line.paramAtPoint(segment.to) ?: return null

    val segLo = minOf(fromT, toT)
    val segHi = maxOf(fromT, toT)
    val clipLo = maxOf(segLo, range.min)
    val clipHi = minOf(segHi, range.max)
    if (clipLo > clipHi) return null

    val from = line.pointAtParam(clipLo)
    val to = line.pointAtParam(clipHi)
    return LineSegmentHit(from, to)
}

private fun addLineIntersectionResults(
    state: MongeState,
    points: List<Offset3D>,
    segments: List<LineSegmentHit>,
) {
    val cleanSegments = segments
        .filter { (it.to - it.from).length() > 1e-3f }
        .distinctSegmentsByNear()
    val cleanPoints = points
        .distinctPointsByNear()
        .filter { point -> cleanSegments.none { segment -> pointOnSegment(point, segment) } }

    if (cleanPoints.isEmpty() && cleanSegments.isEmpty()) {
        notifyEmptyIntersection(state)
        return
    }
    for (segment in cleanSegments) {
        addIntersectionSegment3D(state, segment.from, segment.to)
    }
    for (point in cleanPoints) {
        addIntersectionPoint3D(state, point.x, point.y, point.z)
    }
}

private fun List<Offset3D>.distinctPointsByNear(eps: Float = 1e-3f): List<Offset3D> {
    val out = mutableListOf<Offset3D>()
    for (point in this) {
        if (out.none { (it - point).length() <= eps }) out += point
    }
    return out
}

private fun List<LineSegmentHit>.distinctSegmentsByNear(eps: Float = 1e-3f): List<LineSegmentHit> {
    val out = mutableListOf<LineSegmentHit>()
    for (segment in this) {
        val duplicate = out.any { existing ->
            ((existing.from - segment.from).length() <= eps && (existing.to - segment.to).length() <= eps) ||
                    ((existing.from - segment.to).length() <= eps && (existing.to - segment.from).length() <= eps)
        }
        if (!duplicate) out += segment
    }
    return out
}

private fun pointOnSegment(point: Offset3D, segment: LineSegmentHit, eps: Float = 1e-3f): Boolean {
    val ab = segment.to - segment.from
    val len2 = ab dot ab
    if (len2 < eps * eps) return (point - segment.from).length() <= eps
    val t = ((point - segment.from) dot ab) / len2
    if (t < -eps || t > 1f + eps) return false
    val nearest = segment.from + ab * t.coerceIn(0f, 1f)
    return (point - nearest).length() <= eps
}

/** Reálné kořeny kvadratické rovnice; tečna (disc≈0 nebo splývající kořeny) → jeden bod. */
private fun solveQuadratic(a: Float, b: Float, c: Float): List<Float> {
    val scale = (abs(a) + abs(b) + abs(c)).coerceAtLeast(1e-12f)
    if (abs(a) < 1e-9f * scale) {                 // degenerace na lineární
        if (abs(b) < 1e-9f * scale) return emptyList()
        return listOf(-c / b)
    }
    val disc = b * b - 4f * a * c
    val tol = 1e-6f * (b * b + abs(4f * a * c)).coerceAtLeast(1f)
    if (disc < -tol) return emptyList()           // míjí plochu
    if (disc <= tol) return listOf(-b / (2f * a)) // tečna → 1 bod
    val sq = sqrt(disc)
    val t1 = (-b - sq) / (2f * a)
    val t2 = (-b + sq) / (2f * a)
    // splývající kořeny (numerická tečna) → jeden bod místo dvou identických
    if (abs(t1 - t2) <= 1e-4f * (abs(t1) + abs(t2)) + 1e-6f) return listOf((t1 + t2) / 2f)
    return listOf(t1, t2)
}

/** Kolmá vzdálenost bodu od přímky (P, jednotkový směr dUnit). */
private fun perpDistanceToLine(point: Offset3D, p: Offset3D, dUnit: Offset3D): Float {
    val e = point - p
    return (e - dUnit * (e dot dUnit)).length()
}

/** Hodnota kuželosečky v rovinných souřadnicích (s,t): A s²+B st+C t²+D s+E t+F. */
private fun conicValue(conic: ConicSection3D, s: Float, t: Float): Float {
    val m = conic.matrix
    val m01 = (m.m01 + m.m10) * 0.5f
    val m02 = (m.m02 + m.m20) * 0.5f
    val m12 = (m.m12 + m.m21) * 0.5f
    return m.m00 * s * s + m.m11 * t * t + m.m22 +
            2f * m01 * s * t + 2f * m02 * s + 2f * m12 * t
}

/** Leží rovinný bod (s,t) na řídicí kuželosečce (relativní tolerance dle měřítka koniky)? */
private fun isOnConic(conic: ConicSection3D, s: Float, t: Float): Boolean {
    val m = conic.matrix
    val m01 = abs((m.m01 + m.m10) * 0.5f)
    val m02 = abs((m.m02 + m.m20) * 0.5f)
    val m12 = abs((m.m12 + m.m21) * 0.5f)
    val mag = (abs(m.m00) + abs(m.m11) + m01) * (s * s + t * t) +
            (m02 + m12) * (abs(s) + abs(t)) + abs(m.m22)
    return abs(conicValue(conic, s, t)) <= 1e-3f * mag.coerceAtLeast(1e-6f)
}

/** Bod, kde tvořice válce procházející [base] protne horní omezení (top rovinu / horní konику). */
private fun cylinderTopPoint(
    state: MongeState,
    cyl: CylindricalSurface3D,
    base: Offset3D,
    w: Offset3D,
): Offset3D? {
    var planePoint: Offset3D? = null
    var planeNormal: Offset3D? = null
    cyl.topPlaneId?.let { id ->
        state.planes3D.find { it.id == id }?.equation?.let { eq ->
            val nn = Offset3D(eq.a, eq.b, eq.c)
            planeNormal = nn
            planePoint = nn * (-eq.d / (nn dot nn))
        }
    }
    if (planeNormal == null) cyl.upperConicId?.let { id ->
        state.conics3D.find { it.id == id }?.let { uc ->
            planeNormal = uc.u cross uc.v
            planePoint = uc.p0
        }
    }
    val pn = planeNormal ?: return null
    val pp = planePoint ?: return null
    val denom = pn dot w
    if (abs(denom) < 1e-9f) return null
    val h = (pn dot (pp - base)) / denom
    return base + w * h
}

/**
 * Rovina × rovina → průsečnice (přímka). Rovnoběžné a různé roviny → prázdný průnik;
 * totožné roviny → nic se nepřidává (výsledkem je celá rovina).
 */
fun intersectPlanePlane(a: Plane3D, b: Plane3D, state: MongeState) {
    val eq1 = a.equation
    val eq2 = b.equation
    if (eq1 == null || eq2 == null) {
        notifyEmptyIntersection(state)
        return
    }
    val n1 = Offset3D(eq1.a, eq1.b, eq1.c)
    val n2 = Offset3D(eq2.a, eq2.b, eq2.c)
    val u = n1 cross n2          // směr průsečnice
    val uLen2 = u dot u

    if (uLen2 < 1e-9f) {
        // normály rovnoběžné → roviny rovnoběžné nebo totožné
        // bod ležící v rovině a → leží i v rovině b? Pak jsou totožné.
        val pOn1 = n1 * (-eq1.d / (n1 dot n1))
        val distToPlane2 = (n2 dot pOn1) + eq2.d
        if (abs(distToPlane2) < 1e-3f * n2.length().coerceAtLeast(1f)) {
            // totožné roviny → nic nepřidáváme
            state.consInfo.value = "Roviny jsou totožné – průnikem je celá rovina."
        } else {
            notifyEmptyIntersection(state)
        }
        return
    }

    // bod na průsečnici nejbližší počátku
    val p1 = -eq1.d
    val p2 = -eq2.d
    val p0 = ((n2 cross u) * p1 + (u cross n1) * p2) * (1f / uLen2)

    addIntersectionLine3D(state, p0, u)
}

/**
 * Rovina × kuželová plocha. Řez počítáme středovým promítnutím z vrcholu do roviny
 * řídicí kuželosečky. Rovina procházející vrcholem je degenerace: tečna dá povrchovou
 * úsečku vrchol–podstava, ostatní případy dle zadání zapisujeme jen jako vrchol.
 */
fun intersectPlaneCone(plane: Plane3D, cone: ConicalSurface3D, state: MongeState) {
    val eq = plane.equation
    val conic = state.conics3D.find { it.id == cone.directrixId }
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId }
    if (eq == null || conic == null || apexP == null) { notifyEmptyIntersection(state); return }

    val planeN = Offset3D(eq.a, eq.b, eq.c)
    val planeNLen = planeN.length()
    if (planeNLen < 1e-9f) { notifyEmptyIntersection(state); return }

    val apex = Offset3D(apexP.x, apexP.y, apexP.z)
    val apexDist = (planeN dot apex) + eq.d
    val coneScale = ((apex - conic.p0).length() + (conic.a ?: 0f) + (conic.b ?: 0f)).coerceAtLeast(1f)
    val apexTol = 1e-4f * planeNLen * coneScale
    if (abs(apexDist) <= apexTol) {
        addConeApexPlaneIntersection(state, conic, apex, planeN, eq.d)
        return
    }

    val baseU = conic.u
    val baseV = conic.v
    val baseN = baseU cross baseV
    val k = baseN dot (conic.p0 - apex)
    if (abs(k) < 1e-9f * baseN.length().coerceAtLeast(1f) * coneScale) {
        notifyEmptyIntersection(state)
        return
    }

    val nUnit = planeN * (1f / planeNLen)
    val cutP0 = planeN * (-eq.d / (planeN dot planeN))
    val (cutU, cutV) = planeBasis(nUnit)

    val rel0 = cutP0 - apex
    val ap = apex - conic.p0
    val apU = ap dot baseU
    val apV = ap dot baseV

    val g0 = baseN dot rel0
    val gX = baseN dot cutU
    val gY = baseN dot cutV
    val xU0 = rel0 dot baseU
    val xUX = cutU dot baseU
    val xUY = cutV dot baseU
    val xV0 = rel0 dot baseV
    val xVX = cutU dot baseV
    val xVY = cutV dot baseV

    val cutMatrix = transformConicByHomogeneousLocal(
        conic.matrix,
        sX = apU * gX + k * xUX,
        sY = apU * gY + k * xUY,
        s0 = apU * g0 + k * xU0,
        tX = apV * gX + k * xVX,
        tY = apV * gY + k * xVY,
        t0 = apV * g0 + k * xV0,
        gX = gX,
        gY = gY,
        g0 = g0
    )

    val rawCut = ConicSection3D(
        p0 = cutP0,
        u = cutU,
        v = cutV,
        matrix = cutMatrix,
        rawName = "ρ",
        color = cone.color,
        strokeWidth = cone.wireWidth,
        creationIndex = allocIndex(state)
    )

    when (classifyConic2D(cutMatrix)) {
        ConicKind.ELLIPSE -> addFiniteConeEllipseIntersection(
            state = state,
            cone = cone,
            baseConic = conic,
            planeN = planeN,
            planeD = eq.d,
            conic = rawCut.copy(rawName = "ε")
        )
        ConicKind.PARABOLA -> addFiniteConeParabolaIntersection(
            state = state,
            cone = cone,
            baseConic = conic,
            planeN = planeN,
            planeD = eq.d,
            conic = rawCut.copy(rawName = "π")
        )
        ConicKind.HYPERBOLA -> {
            val hyperbola = canonicalHyperbolaForSampling(rawCut.copy(rawName = "η", creationIndex = allocIndex(state)))
            if (hyperbola == null) {
                notifyEmptyIntersection(state)
                return
            }
            // řežeme jen polovinu dvojkužele → viditelná je jen jedna větev, a to po podstavu;
            // doplníme i úsečku průniku rovina × podstava (uzavře oblouk u podstavy)
            addFiniteConeHyperbolaIntersection(
                state = state,
                cone = cone,
                baseConic = conic,
                planeN = planeN,
                planeD = eq.d,
                conic = hyperbola
            )
        }
    }
}

/**
 * Spočítá kanonickou kuželosečku řezu kužele rovinou (n·X + d = 0) BEZ zápisu do stavu.
 * Vrací (druh, kuželosečka). Pro elipsu je kuželosečka kanonická (matice 1/a²,1/b²);
 * pro parabolu/hyperbolu vrací surovou koniku v řezové rovině. null = rovina vrcholem
 * nebo degenerace. Sdílí matematiku s [intersectPlaneCone]; používá ji Kužel × Solid.
 */
internal fun coneSectionConic(
    cone: ConicalSurface3D,
    conic: ConicSection3D,
    apex: Offset3D,
    planeN: Offset3D,
    planeD: Float,
    creationIndex: Long,
): Pair<ConicKindPublic, ConicSection3D>? {
    val planeNLen = planeN.length()
    if (planeNLen < 1e-9f) return null

    val apexDist = (planeN dot apex) + planeD
    val coneScale = ((apex - conic.p0).length() + (conic.a ?: 0f) + (conic.b ?: 0f)).coerceAtLeast(1f)
    if (abs(apexDist) <= 1e-4f * planeNLen * coneScale) return null   // rovina prochází vrcholem

    val baseU = conic.u
    val baseV = conic.v
    val baseN = baseU cross baseV
    val k = baseN dot (conic.p0 - apex)
    if (abs(k) < 1e-9f * baseN.length().coerceAtLeast(1f) * coneScale) return null

    val nUnit = planeN * (1f / planeNLen)
    val cutP0 = planeN * (-planeD / (planeN dot planeN))
    val (cutU, cutV) = planeBasis(nUnit)

    val rel0 = cutP0 - apex
    val ap = apex - conic.p0
    val apU = ap dot baseU
    val apV = ap dot baseV

    val g0 = baseN dot rel0
    val gX = baseN dot cutU
    val gY = baseN dot cutV
    val xU0 = rel0 dot baseU
    val xUX = cutU dot baseU
    val xUY = cutV dot baseU
    val xV0 = rel0 dot baseV
    val xVX = cutU dot baseV
    val xVY = cutV dot baseV

    val cutMatrix = transformConicByHomogeneousLocal(
        conic.matrix,
        sX = apU * gX + k * xUX, sY = apU * gY + k * xUY, s0 = apU * g0 + k * xU0,
        tX = apV * gX + k * xVX, tY = apV * gY + k * xVY, t0 = apV * g0 + k * xV0,
        gX = gX, gY = gY, g0 = g0
    )
    val rawCut = ConicSection3D(
        p0 = cutP0, u = cutU, v = cutV, matrix = cutMatrix,
        rawName = "ρ", color = cone.color, strokeWidth = cone.wireWidth, creationIndex = creationIndex
    )

    return when (classifyConic2D(cutMatrix)) {
        ConicKind.ELLIPSE -> {
            val el = ellipseParamsFromConic3D(rawCut) ?: return null
            val matrix = Matrix3x3.fromCoefficients(1f / (el.a * el.a), 0f, 1f / (el.b * el.b), 0f, 0f, -1f)
            ConicKindPublic.ELLIPSE to ConicSection3D(
                p0 = el.center3D, u = el.uRot.normalize(), v = el.vRot.normalize(),
                matrix = matrix, rawName = "ε", color = cone.color, strokeWidth = cone.wireWidth,
                a = el.a, b = el.b, creationIndex = creationIndex
            )
        }
        ConicKind.PARABOLA ->
            ConicKindPublic.PARABOLA to canonizeParabolaFrame(rawCut.copy(rawName = "π"))
        ConicKind.HYPERBOLA -> {
            val hyp = canonicalHyperbolaForSampling(rawCut.copy(rawName = "η")) ?: return null
            ConicKindPublic.HYPERBOLA to hyp
        }
    }
}

/** Druh řezu kužele – veřejná varianta pro [coneSectionConic]. */
internal enum class ConicKindPublic { ELLIPSE, PARABOLA, HYPERBOLA }

/**
 * Rovina × válcová plocha. Je-li rovina rovnoběžná s tvořicemi válce, průnikem jsou
 * 0/1/2 površky oříznuté horní podstavou. V ostatních případech vznikne elipsa:
 * řídicí kuželosečku rovnoběžně promítneme podél tvořic do řezové roviny.
 */
fun intersectPlaneCylinder(plane: Plane3D, cylinder: CylindricalSurface3D, state: MongeState) {
    val eq = plane.equation
    val conic = state.conics3D.find { it.id == cylinder.directrixId }
    if (eq == null || conic == null) { notifyEmptyIntersection(state); return }

    val planeN = Offset3D(eq.a, eq.b, eq.c)
    val planeNLen = planeN.length()
    val w = cylinder.direction
    val wLen = w.length()
    if (planeNLen < 1e-9f || wLen < 1e-9f) { notifyEmptyIntersection(state); return }

    val baseU = conic.u
    val baseV = conic.v
    val baseN = baseU cross baseV
    val baseNw = baseN dot w
    if (abs(baseNw) < 1e-9f * baseN.length().coerceAtLeast(1f) * wLen) {
        notifyEmptyIntersection(state)
        return
    }

    val planeNw = planeN dot w
    val parallelTol = 1e-6f * planeNLen * wLen
    if (abs(planeNw) <= parallelTol) {
        addCylinderGeneratorsInPlane(state, cylinder, conic, planeN, eq.d, w)
        addCylinderPlaneCapChords(state, cylinder, conic, planeN, eq.d)
        return
    }

    val section = cylinderPlaneSectionEllipse(
        cylinder = cylinder,
        conic = conic,
        planeN = planeN,
        planeD = eq.d,
        creationIndex = allocIndex(state)
    )
    if (section == null) {
        notifyEmptyIntersection(state)
        return
    }
    val arcs = clipEllipseConicByPredicate(section) { point ->
        pointOnFiniteCylinder(state, cylinder, point)
    }
    if (arcs.isEmpty()) {
        notifyEmptyIntersection(state)
        return
    }
    addClippedEllipseArcs(state, section, arcs)
    addCylinderPlaneCapChords(state, cylinder, conic, planeN, eq.d)
}

private fun addCylinderPlaneCapChords(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float
) {
    val segments = mutableListOf<LineSegmentHit>()
    for (cap in cylinderCapPlanes(state, cylinder, conic)) {
        val capN = cap.normal
        val lineDir = planeN cross capN
        val dirLen2 = lineDir dot lineDir
        val normalScale = (planeN.length() * capN.length()).coerceAtLeast(1f)
        if (dirLen2 < 1e-12f * normalScale * normalScale) continue

        val planeConst = -planeD
        val capConst = capN dot cap.point
        val linePoint = ((capN cross lineDir) * planeConst + (lineDir cross planeN) * capConst) * (1f / dirLen2)
        for ((t1, t2) in clipLineToCylinderCapDisk(cylinder, conic, linePoint, lineDir)) {
            if (!t1.isFinite() || !t2.isFinite()) continue
            val from = linePoint + lineDir * t1
            val to = linePoint + lineDir * t2
            if ((to - from).length() > 1e-3f) {
                segments += LineSegmentHit(from, to)
            }
        }
    }
    for (segment in segments.distinctSegmentsByNear()) {
        addIntersectionSegment3D(state, segment.from, segment.to)
    }
}

/**
 * Spočítá kanonickou elipsu řezu válce rovinou (n·X + d = 0) BEZ zápisu do stavu.
 * Vrací null, je-li rovina rovnoběžná s tvořicemi nebo řez degeneruje.
 * Sdílí matematiku s [intersectPlaneCylinder]; používá ji průnik Válec × Solid.
 */
internal fun cylinderSectionEllipse(
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float,
    creationIndex: Long,
): ConicSection3D? {
    val planeNLen = planeN.length()
    val w = cylinder.direction
    val wLen = w.length()
    if (planeNLen < 1e-9f || wLen < 1e-9f) return null

    val baseU = conic.u
    val baseV = conic.v
    val baseN = baseU cross baseV
    val baseNw = baseN dot w
    if (abs(baseNw) < 1e-9f * baseN.length().coerceAtLeast(1f) * wLen) return null

    val planeNw = planeN dot w
    if (abs(planeNw) <= 1e-6f * planeNLen * wLen) return null  // rovnoběžná s tvořicemi → površky (spec. případ)

    return cylinderPlaneSectionEllipse(cylinder, conic, planeN, planeD, creationIndex)
}

private fun cylinderPlaneSectionEllipse(
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float,
    creationIndex: Long,
): ConicSection3D? {
    val w = cylinder.direction
    val planeNLen = planeN.length()
    val wLen = w.length()
    val planeNw = planeN dot w
    if (planeNLen < 1e-9f || wLen < 1e-9f || abs(planeNw) < 1e-9f * planeNLen * wLen) return null

    val baseEllipse = ellipseParamsFromConic3D(conic) ?: return null
    val center = baseEllipse.center3D
    val axisU = baseEllipse.uRot * baseEllipse.a
    val axisV = baseEllipse.vRot * baseEllipse.b

    val centerShift = -((planeN dot center) + planeD) / planeNw
    val axisUShift = -(planeN dot axisU) / planeNw
    val axisVShift = -(planeN dot axisV) / planeNw
    val cutCenter = center + w * centerShift
    val cutAxisU = axisU + w * axisUShift
    val cutAxisV = axisV + w * axisVShift

    val nUnit = planeN * (1f / planeNLen)
    val (basisU, basisV) = planeBasisWithHint(nUnit, cutAxisU)
    val ux = basisU dot cutAxisU
    val uy = basisV dot cutAxisU
    val vx = basisU dot cutAxisV
    val vy = basisV dot cutAxisV
    val det = ux * vy - vx * uy
    if (abs(det) < 1e-7f * (cutAxisU.length() * cutAxisV.length()).coerceAtLeast(1f)) return null

    val inv00 = vy / det
    val inv01 = -vx / det
    val inv10 = -uy / det
    val inv11 = ux / det
    val k00 = inv00 * inv00 + inv10 * inv10
    val k01 = inv00 * inv01 + inv10 * inv11
    val k11 = inv01 * inv01 + inv11 * inv11
    val rawMatrix = Matrix3x3.fromCoefficients(k00, 2f * k01, k11, 0f, 0f, -1f)
    val raw = ConicSection3D(
        p0 = cutCenter,
        u = basisU,
        v = basisV,
        matrix = rawMatrix
    )
    val cutEllipse = ellipseParamsFromConic3D(raw) ?: return null

    val matrix = Matrix3x3.fromCoefficients(
        1f / (cutEllipse.a * cutEllipse.a), 0f,
        1f / (cutEllipse.b * cutEllipse.b), 0f, 0f, -1f
    )
    return ConicSection3D(
        p0 = cutEllipse.center3D,
        u = cutEllipse.uRot.normalize(),
        v = cutEllipse.vRot.normalize(),
        matrix = matrix,
        rawName = "ε",
        color = cylinder.color,
        strokeWidth = cylinder.wireWidth,
        a = cutEllipse.a,
        b = cutEllipse.b,
        creationIndex = creationIndex
    )
}

private fun addCylinderGeneratorsInPlane(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float,
    w: Offset3D,
) {
    val ls = planeN dot conic.u
    val lt = planeN dot conic.v
    val l0 = (planeN dot conic.p0) + planeD
    val lineLen2 = ls * ls + lt * lt
    val scale = planeN.length().coerceAtLeast(1f)
    if (lineLen2 < 1e-12f * scale * scale) {
        if (abs(l0) < 1e-3f * scale) {
            state.consInfo.value = "Rovina obsahuje rovinu řídicí kuželosečky – průnikem je celá podstava válce."
        } else {
            notifyEmptyIntersection(state)
        }
        return
    }

    val s0 = -ls * l0 / lineLen2
    val t0 = -lt * l0 / lineLen2
    val dirLen = sqrt(lineLen2)
    val ds = -lt / dirLen
    val dt = ls / dirLen
    val (qa, qb, qc) = conicSurfaceQuadratic(
        conic,
        Sa = s0, Sb = ds,
        Ta = t0, Tb = dt,
        Ga = 1f, Gb = 0f
    )
    val roots = solveQuadratic(qa, qb, qc)
    if (roots.isEmpty()) {
        notifyEmptyIntersection(state)
        return
    }

    for (r in roots) {
        val base = conic.p0 + conic.u * (s0 + ds * r) + conic.v * (t0 + dt * r)
        val top = cylinderTopPoint(state, cylinder, base, w)
        if (top != null) addIntersectionSegment3D(state, base, top)
        else addIntersectionLine3D(state, base, w)
    }
}

private enum class ConicKind { ELLIPSE, PARABOLA, HYPERBOLA }

private fun addConeApexPlaneIntersection(
    state: MongeState,
    conic: ConicSection3D,
    apex: Offset3D,
    planeN: Offset3D,
    planeD: Float,
) {
    val ls = planeN dot conic.u
    val lt = planeN dot conic.v
    val l0 = (planeN dot conic.p0) + planeD
    val lineLen2 = ls * ls + lt * lt
    val scale = planeN.length().coerceAtLeast(1f)
    if (lineLen2 < 1e-12f * scale * scale) {
        addIntersectionPoint3D(state, apex.x, apex.y, apex.z)
        return
    }

    val s0 = -ls * l0 / lineLen2
    val t0 = -lt * l0 / lineLen2
    val dirLen = sqrt(lineLen2)
    val ds = -lt / dirLen
    val dt = ls / dirLen
    val (qa, qb, qc) = conicSurfaceQuadratic(
        conic,
        Sa = s0, Sb = ds,
        Ta = t0, Tb = dt,
        Ga = 1f, Gb = 0f
    )
    val roots = solveQuadratic(qa, qb, qc)
    val basePoints = roots
        .map { r -> conic.p0 + conic.u * (s0 + ds * r) + conic.v * (t0 + dt * r) }
        .distinctPointsByNear()

    when (basePoints.size) {
        0 -> addIntersectionPoint3D(state, apex.x, apex.y, apex.z)
        1 -> addIntersectionSegment3D(state, basePoints.first(), apex)
        else -> {
            for (base in basePoints) {
                addIntersectionSegment3D(state, base, apex)
            }
            addIntersectionSegment3D(state, basePoints[0], basePoints[1])
        }
    }
}

private fun addFiniteConeEllipseIntersection(
    state: MongeState,
    cone: ConicalSurface3D,
    baseConic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float,
    conic: ConicSection3D
) {
    val section = canonicalConeEllipseIntersection(conic, allocIndex(state))
    if (section == null) {
        notifyEmptyIntersection(state)
        return
    }

    val arcs = clipEllipseConicByPredicate(section) { point ->
        pointOnFiniteCone(state, cone, point)
    }
    if (arcs.isEmpty()) {
        notifyEmptyIntersection(state)
        return
    }

    addClippedEllipseArcs(state, section, arcs)
    if (arcs.any { !isFullEllipseArc(it) }) {
        addConeBasePlaneChord(state, baseConic, planeN, planeD)
    }
}

private fun isFullEllipseArc(arc: Pair<Float, Float>): Boolean =
    arc.first <= -PI.toFloat() + 1e-3f && arc.second >= PI.toFloat() - 1e-3f

private fun addFiniteConeParabolaIntersection(
    state: MongeState,
    cone: ConicalSurface3D,
    baseConic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float,
    conic: ConicSection3D
) {
    val section = canonizeParabolaFrame(conic).copy(creationIndex = allocIndex(state))
    val baseHit = coneBasePlaneIntersectionHit(baseConic, planeN, planeD)
    val basePoints = (baseHit.points + baseHit.segments.flatMap { listOf(it.from, it.to) })
        .distinctPointsByNear()
    if (basePoints.size < 2) {
        notifyEmptyIntersection(state)
        return
    }

    val uN = section.u.normalizeOrNull()
    if (uN == null) {
        notifyEmptyIntersection(state)
        return
    }
    val params = basePoints.map { point -> (point - section.p0) dot uN }
    val tMin = params.min()
    val tMax = params.max()
    val span = (tMax - tMin).coerceAtLeast(1f)
    val added = addClippedParabolaArcsByPredicate(
        state = state,
        template = section,
        tStart = tMin - 0.05f * span,
        tEnd = tMax + 0.05f * span,
    ) { point -> pointOnFiniteCone(state, cone, point) }

    if (added == 0) {
        notifyEmptyIntersection(state)
        return
    }
    for (segment in baseHit.segments.distinctSegmentsByNear()) {
        if ((segment.to - segment.from).length() > 1e-3f) {
            addIntersectionSegment3D(state, segment.from, segment.to)
        }
    }
}

private fun addFiniteConeHyperbolaIntersection(
    state: MongeState,
    cone: ConicalSurface3D,
    baseConic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float,
    conic: ConicSection3D
) {
    val baseHit = coneBasePlaneIntersectionHit(baseConic, planeN, planeD)
    val basePoints = (baseHit.points + baseHit.segments.flatMap { listOf(it.from, it.to) })
        .distinctPointsByNear()
    if (basePoints.size < 2) {
        notifyEmptyIntersection(state)
        return
    }

    val added = addClippedHyperbolaArcsByPredicate(
        state = state,
        template = conic,
        limitPoints = basePoints,
    ) { point -> pointOnFiniteCone(state, cone, point) }

    if (added == 0) {
        notifyEmptyIntersection(state)
        return
    }
    for (segment in baseHit.segments.distinctSegmentsByNear()) {
        if ((segment.to - segment.from).length() > 1e-3f) {
            addIntersectionSegment3D(state, segment.from, segment.to)
        }
    }
}

private fun addConeEllipseIntersection(state: MongeState, conic: ConicSection3D) {
    val section = canonicalConeEllipseIntersection(conic, allocIndex(state))
    if (section == null) {
        notifyEmptyIntersection(state)
        return
    }
    addIntersectionConic3D(state, section)
}

private fun canonicalConeEllipseIntersection(conic: ConicSection3D, creationIndex: Long): ConicSection3D? {
    val el = ellipseParamsFromConic3D(conic)
        ?: return null
    val matrix = Matrix3x3.fromCoefficients(
        1f / (el.a * el.a), 0f,
        1f / (el.b * el.b), 0f, 0f, -1f
    )
    return ConicSection3D(
        p0 = el.center3D,
        u = el.uRot.normalize(),
        v = el.vRot.normalize(),
        matrix = matrix,
        rawName = conic.rawName,
        color = conic.color,
        strokeWidth = conic.strokeWidth,
        lineStyle = conic.lineStyle,
        a = el.a,
        b = el.b,
        creationIndex = creationIndex
    )
}

private fun addConeBasePlaneChord(
    state: MongeState,
    baseConic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float
) {
    for (segment in coneBasePlaneIntersectionHit(baseConic, planeN, planeD).segments.distinctSegmentsByNear()) {
        if ((segment.to - segment.from).length() > 1e-3f) {
            addIntersectionSegment3D(state, segment.from, segment.to)
        }
    }
}

private fun coneBasePlaneIntersectionHit(
    baseConic: ConicSection3D,
    planeN: Offset3D,
    planeD: Float
): LineDiskHit {
    val baseN = baseConic.u cross baseConic.v
    val lineDir = planeN cross baseN
    val dirLen2 = lineDir dot lineDir
    val normalScale = (planeN.length() * baseN.length()).coerceAtLeast(1f)
    if (dirLen2 < 1e-12f * normalScale * normalScale) return LineDiskHit()

    val planeConst = -planeD
    val baseConst = baseN dot baseConic.p0
    val linePoint = ((baseN cross lineDir) * planeConst + (lineDir cross planeN) * baseConst) * (1f / dirLen2)
    return lineInEllipticDiskPlaneIntersections(linePoint, lineDir, baseConic) { t ->
        linePoint + lineDir * t
    }
}

internal fun addIntersectionParabola3D(state: MongeState, conic3D: ConicSection3D): ConicSection3D? {
    val conic = styleIntersectionConic3D(conic3D)
    val showOthersInAxo = state.projectionMode != ProjectionMode.AXO
    val coeffP = Matrix3x3.toCoefficients(conic.projectToXY())
    val coeffN = Matrix3x3.toCoefficients(conic.projectToXZ())
    val regularInputP = parabolaInputFromCoefficients(coeffP)
    val regularInputN = parabolaInputFromCoefficients(coeffN)
    val inputP = regularInputP ?: degenerateParabolaInput(conic, "pudorys", state)
    val inputN = regularInputN ?: degenerateParabolaInput(conic, "narys", state)
    if (inputP == null || inputN == null) { notifyEmptyIntersection(state); return null }

    val pudorys = ConicSectionPudorys(
        a = coeffP[0], b = coeffP[1], c = coeffP[2], d = coeffP[3], e = coeffP[4], f = coeffP[5],
        rawName = conic.rawName, localColor = conic.color,
        strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
        parent = conic, parentId = conic.id,
        showInAxoInitial = showOthersInAxo, creationIndex = allocIndex(state)
    )
    val narys = ConicSectionNarys(
        a = coeffN[0], b = coeffN[1], c = coeffN[2], d = coeffN[3], e = coeffN[4], f = coeffN[5],
        rawName = conic.rawName, localColor = conic.color,
        strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
        parent = conic, parentId = conic.id,
        showInAxoInitial = showOthersInAxo, creationIndex = allocIndex(state)
    )
    if (regularInputP == null) markDegenerateParabolaProjection(pudorys, inputP)
    if (regularInputN == null) markDegenerateParabolaProjection(narys, inputN)

    state.conics3D.add(conic)
    state.conicsPudorys.add(pudorys)
    state.conicsNarys.add(narys)
    state.conicInputPointsPudorys[pudorys.id] = inputP
    state.conicInputPointsNarys[narys.id] = inputN

    if (state.projectionMode == ProjectionMode.AXO) {
        val coeffB = Matrix3x3.toCoefficients(conic.projectToYZ())
        val regularInputB = parabolaInputFromCoefficients(coeffB)
        val inputB = regularInputB ?: degenerateParabolaInput(conic, "bokorys", state)
        if (inputB != null) {
            val bokorys = ConicSectionBokorys(
                a = coeffB[0], b = coeffB[1], c = coeffB[2], d = coeffB[3], e = coeffB[4], f = coeffB[5],
                rawName = conic.rawName, localColor = conic.color,
                strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
                parent = conic, parentId = conic.id,
                showInAxoInitial = false, creationIndex = allocIndex(state)
            )
            if (regularInputB == null) markDegenerateParabolaProjection(bokorys, inputB)
            state.conicsBokorys.add(bokorys)
            state.conicInputPointsBokorys[bokorys.id] = inputB
        }
        state.basis?.let { basis ->
            val coeffA = Matrix3x3.toCoefficients(conic.projectToAxo(basis))
            val regularInputA = parabolaInputFromCoefficients(coeffA)
            val inputA = regularInputA ?: degenerateParabolaInput(conic, "axo", state)
            if (inputA != null) {
                val axo = ConicSectionAxo(
                    a = coeffA[0], b = coeffA[1], c = coeffA[2], d = coeffA[3], e = coeffA[4], f = coeffA[5],
                    rawName = conic.rawName, localColor = conic.color,
                    strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
                    parent = conic, parentId = conic.id,
                    showInAxoInitial = true, creationIndex = allocIndex(state)
                )
                if (regularInputA == null) markDegenerateParabolaProjection(axo, inputA)
                state.conicsAxo.add(axo)
                state.conicInputPointsAxo[axo.id] = inputA
            }
        }
    }

    update2DSnapshots(state)
    state.triggerRedraw++
    return conic
}

internal fun addIntersectionHyperbola3D(state: MongeState, conic3D: ConicSection3D): ConicSection3D {
    val conic = styleIntersectionConic3D(conic3D)
    val showOthersInAxo = state.projectionMode != ProjectionMode.AXO
    val coeffP = Matrix3x3.toCoefficients(conic.projectToXY())
    val pudorys = ConicSectionPudorys(
        a = coeffP[0], b = coeffP[1], c = coeffP[2], d = coeffP[3], e = coeffP[4], f = coeffP[5],
        rawName = conic.rawName, localColor = conic.color,
        strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
        parent = conic, parentId = conic.id,
        showInAxoInitial = showOthersInAxo, creationIndex = allocIndex(state)
    )
    val coeffN = Matrix3x3.toCoefficients(conic.projectToXZ())
    val narys = ConicSectionNarys(
        a = coeffN[0], b = coeffN[1], c = coeffN[2], d = coeffN[3], e = coeffN[4], f = coeffN[5],
        rawName = conic.rawName, localColor = conic.color,
        strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
        parent = conic, parentId = conic.id,
        showInAxoInitial = showOthersInAxo, creationIndex = allocIndex(state)
    )

    state.conics3D.add(conic)
    state.conicsPudorys.add(pudorys)
    state.conicsNarys.add(narys)

    var bokorys: ConicSectionBokorys? = null
    var axo: ConicSectionAxo? = null
    if (state.projectionMode == ProjectionMode.AXO) {
        val coeffB = Matrix3x3.toCoefficients(conic.projectToYZ())
        bokorys = ConicSectionBokorys(
            a = coeffB[0], b = coeffB[1], c = coeffB[2], d = coeffB[3], e = coeffB[4], f = coeffB[5],
            rawName = conic.rawName, localColor = conic.color,
            strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
            parent = conic, parentId = conic.id,
            showInAxoInitial = false, creationIndex = allocIndex(state)
        )
        state.conicsBokorys.add(bokorys)
        state.basis?.let { basis ->
            val coeffA = Matrix3x3.toCoefficients(conic.projectToAxo(basis))
            axo = ConicSectionAxo(
                a = coeffA[0], b = coeffA[1], c = coeffA[2], d = coeffA[3], e = coeffA[4], f = coeffA[5],
                rawName = conic.rawName, localColor = conic.color,
                strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
                parent = conic, parentId = conic.id,
                showInAxoInitial = true, creationIndex = allocIndex(state)
            )
            state.conicsAxo.add(axo!!)
        }
    }

    addHyperbolaInputsForLift(state, pudorys = pudorys, narys = narys, bokorys = bokorys, axo = axo)
    update2DSnapshots(state)
    state.triggerRedraw++
    return conic
}

private fun parabolaInputFromCoefficients(coeffs: List<Float>): Triple<Offset, Offset, Offset>? {
    val (vertex, focus) = extractVertexAndFocusFromConic(
        coeffs[0], coeffs[1], coeffs[2], coeffs[3], coeffs[4], coeffs[5]
    ) ?: return null
    if ((focus - vertex).getDistance() < 1e-6f) return null
    return Triple(vertex, focus, Offset.Unspecified)
}

private fun markDegenerateParabolaProjection(conic: ConicSectionPudorys, input: Triple<Offset, Offset, Offset>) {
    conic.isDegenerate = true
    conic.degenerateDir = projectionDirection(input)
}

private fun markDegenerateParabolaProjection(conic: ConicSectionNarys, input: Triple<Offset, Offset, Offset>) {
    conic.isDegenerate = true
    conic.degenerateDir = projectionDirection(input)
}

private fun markDegenerateParabolaProjection(conic: ConicSectionBokorys, input: Triple<Offset, Offset, Offset>) {
    conic.isDegenerate = true
    conic.degenerateDir = projectionDirection(input)
}

private fun markDegenerateParabolaProjection(conic: ConicSectionAxo, input: Triple<Offset, Offset, Offset>) {
    conic.isDegenerate = true
    conic.degenerateDir = projectionDirection(input)
}

private fun projectionDirection(input: Triple<Offset, Offset, Offset>): Offset {
    val d = input.second - input.first
    val len = d.getDistance()
    return if (len < 1e-6f) Offset(1f, 0f) else d / len
}

private fun degenerateParabolaInput(
    conic3D: ConicSection3D,
    view: String,
    state: MongeState,
): Triple<Offset, Offset, Offset>? {
    val (vertex, focus) = vertexFocus3DFromLocalConic(conic3D) ?: return null
    val vp = nativeProject(vertex, view, state)
    val fp = nativeProject(focus, view, state)
    val dirRaw = fp - vp
    val len = dirRaw.getDistance()
    val dir = if (len < 1e-6f) Offset(1f, 0f) else dirRaw / len
    return Triple(vp, vp + dir * 50f, Offset.Unspecified)
}

private fun nativeProject(p: Offset3D, view: String, state: MongeState): Offset =
    when (view) {
        "pudorys" -> Offset(p.x, p.y)
        "narys" -> Offset(p.x, p.z)
        "bokorys" -> Offset(p.y, p.z)
        "axo" -> state.basis?.let { projectPoint3DToAxoLocal(p, it) } ?: Offset.Zero
        else -> Offset.Zero
    }

private fun canonicalHyperbolaForSampling(conic: ConicSection3D): ConicSection3D? {
    val coeffs = Matrix3x3.toCoefficients(conic.matrix)
    val a = coeffs[0]
    val b = coeffs[1]
    val c = coeffs[2]
    val d = coeffs[3]
    val e = coeffs[4]
    val f = coeffs[5]

    val detCenter = 4f * a * c - b * b
    if (abs(detCenter) < 1e-12f) return null
    val cx = (b * e - 2f * c * d) / detCenter
    val cy = (b * d - 2f * a * e) / detCenter
    val centerLocal = Offset(cx, cy)

    val translatedF = a * cx * cx + b * cx * cy + c * cy * cy + d * cx + e * cy + f
    if (abs(translatedF) < 1e-12f) return null

    val q00 = a
    val q01 = b * 0.5f
    val q11 = c
    val trace = q00 + q11
    val disc = sqrt((q00 - q11) * (q00 - q11) + 4f * q01 * q01)
    val lambda1 = 0.5f * (trace + disc)
    val lambda2 = 0.5f * (trace - disc)
    if (abs(lambda1) < 1e-12f || abs(lambda2) < 1e-12f) return null

    val e1 = eigenVectorForSymmetric2D(q00, q01, lambda1)
    val e2 = Offset(-e1.y, e1.x)
    val semiSq1 = -translatedF / lambda1
    val semiSq2 = -translatedF / lambda2
    val realAxis: Offset
    val otherAxis: Offset
    val realLambda: Float
    val otherLambda: Float
    when {
        semiSq1.isFinite() && semiSq1 > 1e-12f -> {
            realAxis = e1
            otherAxis = e2
            realLambda = lambda1
            otherLambda = lambda2
        }
        semiSq2.isFinite() && semiSq2 > 1e-12f -> {
            realAxis = e2
            otherAxis = e1
            realLambda = lambda2
            otherLambda = lambda1
        }
        else -> return null
    }

    val center3D = conic.p0 + conic.u * centerLocal.x + conic.v * centerLocal.y
    var u3 = (conic.u * realAxis.x + conic.v * realAxis.y).normalize()
    var v3 = (conic.u * otherAxis.x + conic.v * otherAxis.y).normalize()
    val n = conic.u cross conic.v
    if (((u3 cross v3) dot n) < 0f) v3 = v3 * -1f

    val scale = -1f / translatedF
    val alpha = realLambda * scale
    val beta = otherLambda * scale
    if (alpha <= 0f || beta >= 0f) return null
    val semiA = 1f / sqrt(alpha)
    val semiB = 1f / sqrt(abs(beta))
    val matrix = Matrix3x3.fromCoefficients(alpha, 0f, beta, 0f, 0f, -1f)

    return conic.copy(
        p0 = center3D,
        u = u3,
        v = v3,
        matrix = matrix,
        a = semiA,
        b = semiB
    )
}

private fun eigenVectorForSymmetric2D(q00: Float, q01: Float, lambda: Float): Offset {
    val raw = if (abs(q01) > 1e-12f || abs(q00 - lambda) > 1e-12f) {
        Offset(q01, lambda - q00)
    } else {
        Offset(1f, 0f)
    }
    val len = raw.getDistance()
    return if (len < 1e-12f) Offset(1f, 0f) else raw / len
}

private fun planeBasis(nUnit: Offset3D): Pair<Offset3D, Offset3D> {
    val helper = if (abs(nUnit.x) < 0.9f) Offset3D(1f, 0f, 0f) else Offset3D(0f, 1f, 0f)
    val u = (helper - nUnit * (helper dot nUnit)).normalize()
    val v = nUnit cross u
    return u to v
}

private fun planeBasisWithHint(nUnit: Offset3D, hint: Offset3D): Pair<Offset3D, Offset3D> {
    val inPlane = hint - nUnit * (hint dot nUnit)
    val u = inPlane.normalizeOrNull() ?: planeBasis(nUnit).first
    val v = nUnit cross u
    return u to v
}

private fun transformConicByAffineLocal(
    matrix: Matrix3x3,
    sX: Float, sY: Float, s0: Float,
    tX: Float, tY: Float, t0: Float,
): Matrix3x3 {
    val m = arrayOf(
        floatArrayOf(sX, sY, s0),
        floatArrayOf(tX, tY, t0),
        floatArrayOf(0f, 0f, 1f)
    )
    val c = arrayOf(
        floatArrayOf(matrix.m00, matrix.m01, matrix.m02),
        floatArrayOf(matrix.m10, matrix.m11, matrix.m12),
        floatArrayOf(matrix.m20, matrix.m21, matrix.m22)
    )

    fun q(i: Int, j: Int): Float {
        var sum = 0f
        for (a in 0..2) for (b in 0..2) sum += m[a][i] * c[a][b] * m[b][j]
        return sum
    }

    return Matrix3x3(
        q(0, 0), q(0, 1), q(0, 2),
        q(1, 0), q(1, 1), q(1, 2),
        q(2, 0), q(2, 1), q(2, 2)
    )
}

private fun transformConicByHomogeneousLocal(
    matrix: Matrix3x3,
    sX: Float, sY: Float, s0: Float,
    tX: Float, tY: Float, t0: Float,
    gX: Float, gY: Float, g0: Float,
): Matrix3x3 {
    val m = arrayOf(
        floatArrayOf(sX, sY, s0),
        floatArrayOf(tX, tY, t0),
        floatArrayOf(gX, gY, g0)
    )
    val c = arrayOf(
        floatArrayOf(matrix.m00, matrix.m01, matrix.m02),
        floatArrayOf(matrix.m10, matrix.m11, matrix.m12),
        floatArrayOf(matrix.m20, matrix.m21, matrix.m22)
    )

    fun q(i: Int, j: Int): Float {
        var sum = 0f
        for (a in 0..2) for (b in 0..2) sum += m[a][i] * c[a][b] * m[b][j]
        return sum
    }

    return Matrix3x3(
        q(0, 0), q(0, 1), q(0, 2),
        q(1, 0), q(1, 1), q(1, 2),
        q(2, 0), q(2, 1), q(2, 2)
    )
}

private fun classifyConic2D(matrix: Matrix3x3): ConicKind {
    val a = matrix.m00
    val b = 2f * matrix.m01
    val c = matrix.m11
    val disc = b * b - 4f * a * c
    val tol = 1e-4f * (b * b + abs(4f * a * c)).coerceAtLeast(1f)
    return when {
        disc > tol -> ConicKind.HYPERBOLA
        disc < -tol -> ConicKind.ELLIPSE
        else -> ConicKind.PARABOLA
    }
}

private fun ellipseParamsFromConic3D(conic: ConicSection3D, eps: Float = 1e-12f): EllipseParam? {
    val a = conic.matrix.m00
    val b = 2f * conic.matrix.m01
    val c = conic.matrix.m11
    val d = 2f * conic.matrix.m02
    val e = 2f * conic.matrix.m12
    val f = conic.matrix.m22

    val detCenter = 4f * a * c - b * b
    if (abs(detCenter) < eps) return null

    val s0 = (-2f * c * d + b * e) / detCenter
    val t0 = (b * d - 2f * a * e) / detCenter

    val s00 = a
    val s01 = b * 0.5f
    val s11 = c
    val tr = s00 + s11
    val detS = s00 * s11 - s01 * s01
    val disc = maxOf(0f, tr * tr - 4f * detS)
    val lambda1 = 0.5f * (tr + sqrt(disc))
    val lambda2 = 0.5f * (tr - sqrt(disc))
    if (abs(lambda1) < eps || abs(lambda2) < eps) return null

    // Vlastní vektor pro lambda1 = kolmice na řádek (S − λ₁·I) s VĚTŠÍ normou.
    // Stará formule (s01, λ₁−s00) krátila katastroficky, když s00 je větší vl. číslo
    // (λ₁ ≈ s00) a s01 je jen float šum → směr os vyšel náhodně. Projevovalo se to
    // u řezu svislého válce vodorovnou rovinou (řez měl správný tvar, ale byl pootočený).
    val r1x = s00 - lambda1; val r1y = s01
    val r2x = s01;           val r2y = s11 - lambda1
    val n1 = r1x * r1x + r1y * r1y
    val n2 = r2x * r2x + r2y * r2y
    val rowScale = (s00 * s00 + s11 * s11 + s01 * s01) * eps
    val (e1x, e1y) = when {
        maxOf(n1, n2) <= rowScale -> 1f to 0f                       // λ₁ ≈ λ₂ (kružnice) – směr libovolný
        n1 >= n2 -> { val len = sqrt(n1); (r1y / len) to (-r1x / len) }
        else     -> { val len = sqrt(n2); (r2y / len) to (-r2x / len) }
    }
    val e2x = -e1y
    val e2y = e1x

    val translatedF = a * s0 * s0 + b * s0 * t0 + c * t0 * t0 + d * s0 + e * t0 + f
    val r1Squared = -translatedF / lambda1
    val r2Squared = -translatedF / lambda2
    if (r1Squared <= eps || r2Squared <= eps) return null

    val center3D = conic.p0 + conic.u * s0 + conic.v * t0
    val uRot = conic.u * e1x + conic.v * e1y
    val vRot = conic.u * e2x + conic.v * e2y
    return EllipseParam(center3D, uRot, vRot, sqrt(r1Squared), sqrt(r2Squared))
}

/**
 * Rovina × kulová plocha → kružnice (uložená jako [ConicSection3D]) a její průměty.
 * Tečná rovina → bod; rovina mimo kouli → prázdný průnik.
 */
fun intersectPlaneSphere(plane: Plane3D, sphere: SphereSurface3D, state: MongeState) {
    val eq = plane.equation
    val centerP = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId }
    if (eq == null || centerP == null) { notifyEmptyIntersection(state); return }

    val n = Offset3D(eq.a, eq.b, eq.c)
    val nLen = n.length()
    if (nLen < 1e-9f) { notifyEmptyIntersection(state); return }
    val nUnit = n * (1f / nLen)
    val cc = Offset3D(centerP.x, centerP.y, centerP.z)
    val r = sphere.radius

    val distSigned = ((n dot cc) + eq.d) / nLen     // vzdálenost středu od roviny
    val ad = abs(distSigned)
    val tol = 1e-3f * r

    if (ad > r + tol) { notifyEmptyIntersection(state); return }   // koule a rovina se míjí

    val center = cc - nUnit * distSigned            // pata kolmice = střed kružnice
    if (ad >= r - tol) {                            // tečná rovina → bod
        addIntersectionPoint3D(state, center.x, center.y, center.z)
        return
    }

    val rho = sqrt((r * r - distSigned * distSigned).coerceAtLeast(0f))
    addCircleInPlane(state, center, nUnit, rho)
}

/** Sestaví kružnici (poloměr ρ) ležící v rovině dané středem a jednotkovou normálou a přidá ji. */
private fun addCircleInPlane(state: MongeState, center: Offset3D, nUnit: Offset3D, rho: Float) {
    val helper = if (abs(nUnit.x) < 0.9f) Offset3D(1f, 0f, 0f) else Offset3D(0f, 1f, 0f)
    val u = (helper - nUnit * (helper dot nUnit)).normalize()
    val v = nUnit cross u
    val matrix = Matrix3x3.fromCoefficients(1f, 0f, 1f, 0f, 0f, -rho * rho)
    val conic3D = ConicSection3D(
        p0 = center, u = u, v = v, matrix = matrix,
        rawName = "k", a = rho, b = rho, creationIndex = allocIndex(state)
    )
    addIntersectionConic3D(state, conic3D)
}

fun intersectConeCone(a: ConicalSurface3D, b: ConicalSurface3D, state: MongeState) {
    addSampledSurfaceIntersectionCurve(
        state = state,
        first = sampledConeGenerators(state, a) ?: run { notifyEmptyIntersection(state); return },
        firstAt = coneGeneratorRefiner(state, a),
        second = { p, d -> lineConeIntersections(p, d, b, state) },
        boundaryIntersections =
            coneBaseBoundaryContinuation(state, a) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneConeSectionBetween(state, b, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneConeSectionRuns(state, b, it.planePoint, it.normalUnit, it.insideFace)
            }.asList() +
            coneBaseBoundaryContinuation(state, b) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneConeSectionBetween(state, a, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneConeSectionRuns(state, a, it.planePoint, it.normalUnit, it.insideFace)
            }.asList(),
    )
}

fun intersectConeCylinder(cone: ConicalSurface3D, cylinder: CylindricalSurface3D, state: MongeState) {
    addSampledSurfaceIntersectionCurve(
        state = state,
        first = sampledConeGenerators(state, cone) ?: run { notifyEmptyIntersection(state); return },
        firstAt = coneGeneratorRefiner(state, cone),
        second = { p, d -> lineCylinderIntersections(p, d, cylinder, state) },
        boundaryIntersections =
            coneBaseBoundaryContinuation(state, cone) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneCylinderSectionBetween(state, cylinder, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneCylinderSectionRuns(state, cylinder, it.planePoint, it.normalUnit, it.insideFace)
            }.asList() +
            cylinderBoundaryContinuations(state, cylinder) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneConeSectionBetween(state, cone, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneConeSectionRuns(state, cone, it.planePoint, it.normalUnit, it.insideFace)
            },
    )
}

fun intersectConeSphere(cone: ConicalSurface3D, sphere: SphereSurface3D, state: MongeState) {
    addSampledSurfaceIntersectionCurve(
        state = state,
        first = sampledConeGenerators(state, cone) ?: run { notifyEmptyIntersection(state); return },
        firstAt = coneGeneratorRefiner(state, cone),
        second = { p, d -> lineSphereIntersections(p, d, sphere, state) },
        boundaryIntersections =
            coneBaseBoundaryContinuation(state, cone) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneSphereSectionBetween(state, sphere, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneSphereSectionRuns(state, sphere, it.planePoint, it.normalUnit, it.insideFace)
            }.asList(),
    )
}

fun intersectCylinderCylinder(a: CylindricalSurface3D, b: CylindricalSurface3D, state: MongeState) {
    addSampledSurfaceIntersectionCurve(
        state = state,
        first = sampledCylinderGenerators(state, a) ?: run { notifyEmptyIntersection(state); return },
        firstAt = cylinderGeneratorRefiner(state, a),
        second = { p, d -> lineCylinderIntersections(p, d, b, state) },
        boundaryIntersections =
            cylinderBoundaryContinuations(state, a) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneCylinderSectionBetween(state, b, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneCylinderSectionRuns(state, b, it.planePoint, it.normalUnit, it.insideFace)
            } +
            cylinderBoundaryContinuations(state, b) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneCylinderSectionBetween(state, a, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneCylinderSectionRuns(state, a, it.planePoint, it.normalUnit, it.insideFace)
            },
    )
}

fun intersectCylinderSphere(cylinder: CylindricalSurface3D, sphere: SphereSurface3D, state: MongeState) {
    addSampledSurfaceIntersectionCurve(
        state = state,
        first = sampledCylinderGenerators(state, cylinder) ?: run { notifyEmptyIntersection(state); return },
        firstAt = cylinderGeneratorRefiner(state, cylinder),
        second = { p, d -> lineSphereIntersections(p, d, sphere, state) },
        boundaryIntersections =
            cylinderBoundaryContinuations(state, cylinder) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneSphereSectionBetween(state, sphere, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneSphereSectionRuns(state, sphere, it.planePoint, it.normalUnit, it.insideFace)
            },
    )
}

// ===================== Přímkové plochy =====================

// Primární sampler konoidů hledá globálně spojitou větev v O(n²), proto jej držíme
// na 192. Pro samotný průnik levně vložíme mezilehlé tvořice lineární interpolací
// stejné sítě; tím zachytíme i krátké části křivky mezi dvěma primárními vzorky.
private const val RULED_INTERSECTION_RULINGS = 192
private const val RULED_INTERSECTION_MESH_RULINGS = RULED_INTERSECTION_RULINGS
private const val RULED_INTERSECTION_SUBDIVISIONS = 4

/** Přímka × přímková plocha – bodově proti bilineárním pásům definiční sítě plochy. */
fun intersectLineRuledSurface(line: Line3D, surface: RuledSurface3D, state: MongeState) {
    val strips = ruledIntersectionStrips(state, surface)
    if (strips.isEmpty()) { notifyEmptyIntersection(state); return }
    val p = Offset3D(line.start.x, line.start.y, line.start.z)
    val hits = lineRuledStripsIntersections(p, line.direction, strips).map { it.point }
    addLineIntersectionResults(state, line, hits, emptyList())
}

/**
 * Rovina × přímková plocha – bod řezu počítáme přímo na každé tvořici.
 *
 * Řez triangulovanou sítí obsahoval v každém pásu ještě vrchol na zvolené diagonále
 * čtyřúhelníku. Tyto pomocné vrcholy neleží na hladkém řezu a v projekci vytvářely
 * pravidelné „zuby“. Přímý průsek roviny s tvořicí dává jeden správně uspořádaný bod
 * na každý parametr plochy a diagonály sítě do výsledku vůbec nevstupují.
 */
fun intersectPlaneRuledSurface(plane: Plane3D, surface: RuledSurface3D, state: MongeState) {
    val eq = plane.equation ?: run { notifyEmptyIntersection(state); return }
    val normalRaw = Offset3D(eq.a, eq.b, eq.c)
    val normalLength = normalRaw.length()
    if (normalLength < 1e-9f) { notifyEmptyIntersection(state); return }
    val normal = normalRaw * (1f / normalLength)
    val planePoint = normalRaw * (-eq.d / (normalRaw dot normalRaw))
    val sections = ruledPlaneSectionsOnGenerators(state, surface, planePoint, normal)
    if (sections.isEmpty()) { notifyEmptyIntersection(state); return }
    sections.forEach { addIntersectionCurve3D(state, it.points, it.closed) }
}

/** Kužel × přímková plocha – boční plášť bodově, podstavný disk řezem řídicí roviny. */
fun intersectConeRuledSurface(cone: ConicalSurface3D, surface: RuledSurface3D, state: MongeState) {
    val boundaries = coneBaseBoundaryContinuation(state, cone) { _, _, _, _, _ -> null }
        .withFullSections { boundary -> ruledPlaneSections(state, surface, boundary) }
        .asList()
    intersectRuledSurfaceByLineHits(
        state, surface,
        second = { p, d -> lineConeIntersections(p, d, cone, state) },
        boundaryIntersections = boundaries,
    )
}

/** Válec × přímková plocha – plášť bodově a obě podstavy přes rovinné řezy. */
fun intersectCylinderRuledSurface(cylinder: CylindricalSurface3D, surface: RuledSurface3D, state: MongeState) {
    val boundaries = cylinderBoundaryContinuations(state, cylinder) { _, _, _, _, _ -> null }
        .withFullSections { boundary -> ruledPlaneSections(state, surface, boundary) }
    intersectRuledSurfaceByLineHits(
        state, surface,
        second = { p, d -> lineCylinderIntersections(p, d, cylinder, state) },
        boundaryIntersections = boundaries,
    )
}

fun intersectSphereRuledSurface(sphere: SphereSurface3D, surface: RuledSurface3D, state: MongeState) {
    sphericalConoidContactCurve(state, surface, sphere.id, RULED_INTERSECTION_RULINGS)?.let { contact ->
        addIntersectionCurve3D(state, contact, closed = true)
        return
    }
    intersectRuledSurfaceByLineHits(state, surface, second = { p, d ->
        lineSphereIntersections(p, d, sphere, state)
    })
}

fun intersectSolidOfRevolutionRuledSurface(
    sor: SolidOfRevolutionOp,
    surface: RuledSurface3D,
    state: MongeState,
) {
    val geometry = sorGeometry(state, sor) ?: run { notifyEmptyIntersection(state); return }
    intersectRuledSurfaceByLineHits(state, surface, second = { p, d ->
        lineSoRIntersections(p, d, geometry)
    })
}

fun intersectSegmentSolidRuledSurface(
    solid: SegmentSolid3D,
    surface: RuledSurface3D,
    state: MongeState,
) {
    intersectRuledSurfaceByLineHits(state, surface, second = { p, d ->
        val d2 = d dot d
        if (d2 < 1e-12f) emptyList()
        else lineSegmentSolidSurfaceHitPoints(state, solid, p, d).map { point ->
            LineHit(((point - p) dot d) / d2, point)
        }
    })
}

/**
 * Přímková plocha × přímková plocha – tvořice první plochy bodově proti
 * bilineárním pásům druhé, se stejným refinovaným tracerem jako u těles.
 * Dřívější sešívání úseček z dvojic trojúhelníků obou sítí se rozpadalo na
 * plovoucí chybě koncových bodů dlouhých úzkých trojúhelníků (křivka se
 * fragmentovala na desítky drobných útržků). Triangulace navíc nesmí být ani
 * zdrojem zásahů: zkroucený čtyřúhelník pásu není rovinný a tečná tvořice
 * proplouvá vrstvou mezi rovinami obou trojúhelníků s mnoha falešnými průsečíky.
 */
fun intersectRuledSurfaceRuledSurface(a: RuledSurface3D, b: RuledSurface3D, state: MongeState) {
    val strips = ruledIntersectionStrips(state, b)
    if (strips.isEmpty()) { notifyEmptyIntersection(state); return }
    intersectRuledSurfaceByLineHits(state, a, second = { p, d ->
        lineRuledStripsIntersections(p, d, strips)
    })
}

/** Společný tracer: každou nesouvislou reguli zpracuje odděleně a prázdný dialog ukáže jen jednou. */
private fun intersectRuledSurfaceByLineHits(
    state: MongeState,
    surface: RuledSurface3D,
    second: (Offset3D, Offset3D) -> List<LineHit>,
    boundaryIntersections: List<BoundaryContinuation> = emptyList(),
) {
    val families = ruledIntersectionGeneratorFamilies(state, surface)
    var added = false
    for ((familyIndex, family) in families.withIndex()) {
        if (addSampledSurfaceIntersectionCurve(
                state = state,
                first = family.samples,
                firstAt = family::generatorAt,
                firstAtPeriod = family.period,
                second = second,
                // Úplné řezy podstavami už obsahují všechny komponenty přímkové
                // plochy, proto je materializujeme jen při první reguli.
                boundaryIntersections = if (familyIndex == 0) boundaryIntersections else emptyList(),
                notifyWhenEmpty = false,
            )
        ) added = true
    }
    if (!added) notifyEmptyIntersection(state)
}

/**
 * Spojitá interpolace tvořic uvnitř pásů definiční sítě plochy. Parametr měříme
 * v indexech základní rodiny; slouží zároveň jako refiner `firstAt` pro bisekci
 * tečných přechodů – bez ní se dvě větve průnikové křivky zastaví o krok
 * vzorkování před tečnou tvořicí a nikdy se nespojí (díra v křivce).
 */
private class RuledGeneratorFamily(
    private val base: List<SampleGenerator>,
    private val closed: Boolean,
    subdivisions: Int,
) {
    /** Perioda parametru pro uzavřenou rodinu; null = rodina otevřená (žádný šev). */
    val period: Float? = if (closed) base.size.toFloat() else null

    val samples: List<SampleGenerator> = run {
        val count = if (closed) base.size * subdivisions else (base.size - 1) * subdivisions + 1
        List(count) { index -> generatorAt(index.toFloat() / subdivisions) }
    }

    fun generatorAt(param: Float): SampleGenerator {
        val n = base.size
        val x = if (closed) param.mod(n.toFloat()) else param.coerceIn(0f, (n - 1).toFloat())
        val index = x.toInt().coerceAtMost(if (closed) n - 1 else n - 2)
        val fraction = x - index
        val a = base[index]
        val b = base[(index + 1) % n]
        val start = a.p + (b.p - a.p) * fraction
        val end = (a.p + a.d) + ((b.p + b.d) - (a.p + a.d)) * fraction
        return SampleGenerator(start, end - start, 0f, 1f, param = param)
    }
}

private fun ruledIntersectionGeneratorFamilies(
    state: MongeState,
    surface: RuledSurface3D,
): List<RuledGeneratorFamily> {
    val closed = ruledSurfaceFamilyIsClosed(state, surface)
    // Trimované rodiny: průnik respektuje uživatelský ořez/přesah tvořic,
    // tedy skutečně vykreslený rozsah plochy.
    return sampleRuledSurfaceTrimmedPrimaryFamilies(state, surface, RULED_INTERSECTION_RULINGS)
        .mapNotNull { generators ->
            val base = generators.mapNotNull { generator ->
                val direction = generator.end - generator.start
                if ((direction dot direction) < 1e-12f) null
                else SampleGenerator(generator.start, direction, 0f, 1f)
            }.takeIf { it.size >= 2 } ?: return@mapNotNull null
            RuledGeneratorFamily(base, closed, RULED_INTERSECTION_SUBDIVISIONS)
        }
}

/**
 * Bilineární pás mezi dvěma sousedními tvořicemi: S(u,w) = s0 + u·e + w·d0 + u·w·f,
 * u napříč pásem, w podél tvořic, obojí v [0,1]. Přesně odpovídá lineární
 * interpolaci rodiny tvořic, kterou používá tracer i hustá síť plochy.
 */
private class RuledSurfaceStrip(val s0: Offset3D, val e: Offset3D, val d0: Offset3D, val f: Offset3D)

private fun ruledIntersectionStrips(
    state: MongeState,
    surface: RuledSurface3D,
): List<RuledSurfaceStrip> {
    val closed = ruledSurfaceFamilyIsClosed(state, surface)
    val strips = ArrayList<RuledSurfaceStrip>()
    for (family in sampleRuledSurfaceTrimmedPrimaryFamilies(state, surface, RULED_INTERSECTION_MESH_RULINGS)) {
        if (family.size < 2) continue
        val pairCount = if (closed) family.size else family.size - 1
        for (index in 0 until pairCount) {
            val a = family[index]
            val b = family[(index + 1) % family.size]
            val d0 = a.end - a.start
            val d1 = b.end - b.start
            strips += RuledSurfaceStrip(a.start, b.start - a.start, d0, d1 - d0)
        }
    }
    return strips
}

/**
 * Průsečíky přímky s bilineárními pásy plochy. Eliminace parametru přímky přes
 * vektorový součin se směrem d dá dvě bilineární rovnice v (u,w); dosazení w(u)
 * vede na kvadratiku v u. Téměř dvojnásobné kořeny (tečná površka) se slučují
 * stejně jako u analytických kvadrik, takže tečna nerozsype zásahy na dvojice.
 */
private fun lineRuledStripsIntersections(
    p: Offset3D,
    d: Offset3D,
    strips: List<RuledSurfaceStrip>,
): List<LineHit> {
    val d2 = d dot d
    if (d2 < 1e-12f) return emptyList()
    // báze kolmá na d pro dvě nezávislé složky vektorové rovnice
    val axis = if (abs(d.x) <= abs(d.y) && abs(d.x) <= abs(d.z)) Offset3D(1f, 0f, 0f)
    else if (abs(d.y) <= abs(d.z)) Offset3D(0f, 1f, 0f)
    else Offset3D(0f, 0f, 1f)
    val b1 = (d cross axis).normalizeOrNull() ?: return emptyList()
    val b2 = (d cross b1).normalizeOrNull() ?: return emptyList()

    val paramEps = 1e-4f
    val hits = mutableListOf<LineHit>()
    var scaleSum = 0f
    for (strip in strips) {
        scaleSum += strip.d0.length()
        val q = strip.s0 - p
        val a1 = q dot b1; val a2 = q dot b2
        val bb1 = strip.e dot b1; val bb2 = strip.e dot b2
        val c1 = strip.d0 dot b1; val c2 = strip.d0 dot b2
        val e1 = strip.f dot b1; val e2 = strip.f dot b2

        // (a2 + u·bb2)(c1 + u·e1) − (a1 + u·bb1)(c2 + u·e2) = 0
        val qa = bb2 * e1 - bb1 * e2
        val qb = a2 * e1 + bb2 * c1 - a1 * e2 - bb1 * c2
        val qc = a2 * c1 - a1 * c2

        fun stripPointAt(u: Float, w: Float): Offset3D =
            strip.s0 + strip.e * u + (strip.d0 + strip.f * u) * w

        fun wAt(u: Float): Float? {
            val den1 = c1 + u * e1
            val den2 = c2 + u * e2
            return when {
                abs(den1) >= abs(den2) && abs(den1) > 1e-9f -> -(a1 + u * bb1) / den1
                abs(den2) > 1e-9f -> -(a2 + u * bb2) / den2
                else -> null
            }
        }

        fun addHit(u: Float, w: Float) {
            val point = stripPointAt(u.coerceIn(0f, 1f), w.coerceIn(0f, 1f))
            val t = ((point - p) dot d) / d2
            if (t.isFinite()) hits += LineHit(t, point)
        }

        val coefScale = (abs(qa) + abs(qb) + abs(qc)).coerceAtLeast(1e-12f)
        if (abs(qa) < 1e-7f * coefScale) {
            if (abs(qb) >= 1e-7f * coefScale) {
                val u = -qc / qb
                if (u.isFinite() && u >= -paramEps && u <= 1f + paramEps) {
                    val w = wAt(u)
                    if (w != null && w >= -paramEps && w <= 1f + paramEps) addHit(u, w)
                }
            }
            continue
        }

        val disc = qb * qb - 4f * qa * qc
        val discTol = 1e-6f * (qb * qb + abs(4f * qa * qc)).coerceAtLeast(1e-12f)
        if (disc > discTol) {
            val sq = sqrt(disc)
            val u1 = (-qb - sq) / (2f * qa)
            val u2 = (-qb + sq) / (2f * qa)
            val roots = if (abs(u1 - u2) <= 1e-4f * (abs(u1) + abs(u2)) + 1e-6f) {
                listOf((u1 + u2) * 0.5f)
            } else {
                listOf(u1, u2)
            }
            for (u in roots) {
                if (!u.isFinite() || u < -paramEps || u > 1f + paramEps) continue
                val w = wAt(u) ?: continue
                if (w < -paramEps || w > 1f + paramEps) continue
                addHit(u, w)
            }
        } else {
            // Tečné dochycení: rodina tvořic je jen chordová aproximace plochy a v
            // pásmu, kde průniková křivka běží téměř rovnoběžně s tvořicemi, míjí
            // interpolovaná tvořice sousední plochu o chordovou chybu → zásahy
            // vypadnou a v křivce zůstane díra. Vrchol kvadratiky je bod největšího
            // přiblížení; přijmeme ho jen při skutečné blízkosti v prostoru
            // (zlomek šířky pásu), takže vzdálené plochy falešný zásah nedostanou.
            val u = (-qb / (2f * qa))
            if (u.isFinite() && u >= -paramEps && u <= 1f + paramEps) {
                val w = wAt(u)
                if (w != null && w >= -paramEps && w <= 1f + paramEps) {
                    val point = stripPointAt(u.coerceIn(0f, 1f), w.coerceIn(0f, 1f))
                    val toPoint = point - p
                    val along = (toPoint dot d) / d2
                    val offAxis = (toPoint - d * along).length()
                    val snapTol = (RULED_STRIP_TANGENT_SNAP_FRAC * strip.e.length()).coerceAtLeast(1e-4f)
                    if (offAxis <= snapTol && along.isFinite()) {
                        hits += LineHit(along, point)
                    }
                }
            }
        }
    }
    // Duplicitní zásahy na sdílené tvořici sousedních pásů slučujeme relativně
    // k velikosti plochy – absolutní 1e-3 je pod plovoucí chybou souřadnic ~10².
    val dedupeEps = 1e-3f * (scaleSum / strips.size.coerceAtLeast(1)).coerceAtLeast(1f)
    return hits.sortedBy { it.t }.distinctByNear(eps = dedupeEps) { it.point }
}

/** Strop prostorové vzdálenosti tečného dochycení jako zlomek šířky pásu (chordová chyba je ~1 % šířky). */
private const val RULED_STRIP_TANGENT_SNAP_FRAC = 0.03f

private data class RuledIntersectionTriangle(val a: Offset3D, val b: Offset3D, val c: Offset3D)

private fun ruledIntersectionTriangles(
    state: MongeState,
    surface: RuledSurface3D,
): List<RuledIntersectionTriangle> {
    val grids = ruledSurfaceTrimmedPrimaryGrids(state, surface, RULED_INTERSECTION_MESH_RULINGS, along = 2)
    val closed = ruledSurfaceFamilyIsClosed(state, surface)
    val triangles = ArrayList<RuledIntersectionTriangle>()
    for (grid in grids) {
        if (grid.size < 2 || grid[0].size < 2) continue
        val rowCount = if (closed) grid.size else grid.size - 1
        for (row in 0 until rowCount) {
            val next = (row + 1) % grid.size
            // Krajní sloupce = skutečné (ořezané/prodloužené) konce tvořic;
            // mezisloupce pásů přesahu leží na téže úsečce.
            val a = grid[row].first()
            val b = grid[next].first()
            val c = grid[next].last()
            val d = grid[row].last()
            triangles += RuledIntersectionTriangle(a, b, c)
            triangles += RuledIntersectionTriangle(a, c, d)
        }
    }
    return triangles
}

/** Řez přímkové plochy rovinou, následně omezený na podstavný disk kužele/válce. */
private fun ruledPlaneSections(
    state: MongeState,
    surface: RuledSurface3D,
    boundary: BoundaryContinuation,
): List<SectionCurve> = ruledPlaneSections(
    state = state,
    surface = surface,
    planePoint = boundary.planePoint,
    planeNormal = boundary.normalUnit,
    inside = boundary.insideFace,
)

private fun ruledPlaneSections(
    state: MongeState,
    surface: RuledSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    inside: (Offset3D) -> Boolean,
): List<SectionCurve> {
    // Hladký řez po tvořicích – stejný jako u rovina × plocha. Řez trojúhelníkovou
    // sítí obsahoval vrcholy na diagonálách čtyřúhelníkových pásů, které na hladké
    // křivce neleží, takže řez podstavou kužele/válce vycházel „zubatý".
    // Ořez na podstavný disk se proto dělá až na hladké křivce.
    val smooth = ruledPlaneSectionsOnGenerators(state, surface, planePoint, planeNormal)
    if (smooth.isNotEmpty()) {
        return smooth.flatMap { curve -> clipSectionCurveByInside(curve, inside) }
    }
    return ruledPlaneSectionsOnMesh(state, surface, planePoint, planeNormal, inside)
}

/** Záložní řez trojúhelníkovou sítí – jen pro degenerace (tvořice ležící v rovině řezu). */
private fun ruledPlaneSectionsOnMesh(
    state: MongeState,
    surface: RuledSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    inside: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val rawSegments = ruledIntersectionTriangles(state, surface).mapNotNull { triangle ->
        trianglePlaneSegment(triangle, planePoint, planeNormal)
    }
    val clipped = rawSegments.flatMap { (a, b) -> clipSegmentByInside(a, b, inside) }
    return stitchSectionSegments(clipped)
}

/** Ořízne hladkou křivku řezu predikátem disku; hranice se dohledá bisekcí na hranách polylinie. */
private fun clipSectionCurveByInside(
    curve: SectionCurve,
    inside: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val points = curve.points
    if (points.size < 2) return emptyList()
    val edgeCount = if (curve.closed) points.size else points.size - 1
    val segments = ArrayList<Pair<Offset3D, Offset3D>>()
    for (index in 0 until edgeCount) {
        segments += clipSegmentByInside(points[index], points[(index + 1) % points.size], inside)
    }
    return stitchSectionSegments(segments)
}

/** Hladký rovinný řez seřazený podle parametru primární rodiny tvořic. */
private fun ruledPlaneSectionsOnGenerators(
    state: MongeState,
    surface: RuledSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
): List<SectionCurve> {
    val closedFamily = ruledSurfaceFamilyIsClosed(state, surface)
    val sections = mutableListOf<SectionCurve>()

    for (family in ruledIntersectionGeneratorFamilies(state, surface)) {
        var containsCoplanarGenerator = false
        val planeHits: (Offset3D, Offset3D) -> List<LineHit> = { p, d ->
            val eps = 1e-6f * d.length().coerceAtLeast(1f)
            val startDistance = (p - planePoint) dot planeNormal
            val denominator = d dot planeNormal
            when {
                abs(denominator) <= eps && abs(startDistance) <= eps -> {
                    // Průnik obsahuje celou tvořici. Jde o degeneraci, pro kterou je
                    // spolehlivější obecný řez trojúhelníkovou sítí níže.
                    containsCoplanarGenerator = true
                    emptyList()
                }
                abs(denominator) <= eps -> emptyList()
                else -> {
                    val t = -startDistance / denominator
                    listOf(LineHit(t, p + d * t))
                }
            }
        }
        // Stejný refinovaný tracer jako u těles: bisekce k tvořici, kde řez opouští
        // plochu (t mimo [0,1] / tečna), jinak konce běhů končí o vzorek dřív.
        val hitSets = buildSampleHitSets(family.samples, family::generatorAt, family.period, planeHits)

        if (containsCoplanarGenerator) {
            // Zachová i případ, kdy je výsledkem přímo jedna z tvořic plochy.
            // Přímo síťový řez: přes ruledPlaneSections by se rekurze zacyklila.
            return ruledPlaneSectionsOnMesh(state, surface, planePoint, planeNormal) { true }
        }

        for (run in mergeSampledRunsAtSharedEndpoints(traceSampledRunsByContinuation(hitSets))) {
            if (run.points.size < 3) continue
            val closed = run.closedHint ||
                (closedFamily && run.sampleHitCount >= family.samples.size - 2 && isClosedBranch(run.points))
            val cleaned = if (closed) run.points.dropRepeatedClosingPoint() else run.points
            val points = if (cleaned.size == 2) {
                listOf(cleaned[0], (cleaned[0] + cleaned[1]) * 0.5f, cleaned[1])
            } else {
                cleaned
            }
            sections += SectionCurve(points, closed)
        }
    }
    return sections
}

private fun List<Offset3D>.dropRepeatedClosingPoint(): List<Offset3D> =
    if (size >= 2 && sameEndpoint(first(), last(), 1e-4f)) dropLast(1) else this

private fun trianglePlaneSegment(
    triangle: RuledIntersectionTriangle,
    planePoint: Offset3D,
    normal: Offset3D,
): Pair<Offset3D, Offset3D>? {
    val vertices = listOf(triangle.a, triangle.b, triangle.c)
    val scale = maxOf(
        (triangle.b - triangle.a).length(),
        (triangle.c - triangle.b).length(),
        (triangle.a - triangle.c).length(),
        1f,
    )
    val eps = 1e-6f * scale
    val distances = vertices.map { (it - planePoint) dot normal }
    if (distances.all { abs(it) <= eps }) return null // plošné splynutí není křivkový průnik

    val points = mutableListOf<Offset3D>()
    fun add(point: Offset3D) {
        if (points.none { (it - point).length() <= eps }) points += point
    }
    for (i in vertices.indices) {
        val a = vertices[i]
        val b = vertices[(i + 1) % vertices.size]
        val da = distances[i]
        val db = distances[(i + 1) % vertices.size]
        if (abs(da) <= eps) add(a)
        if ((da < -eps && db > eps) || (da > eps && db < -eps)) {
            val t = da / (da - db)
            add(a + (b - a) * t)
        }
    }
    if (points.size < 2) return null
    var best = points[0] to points[1]
    var bestLength = (best.second - best.first).length()
    for (i in points.indices) for (j in i + 1 until points.size) {
        val length = (points[j] - points[i]).length()
        if (length > bestLength) {
            best = points[i] to points[j]
            bestLength = length
        }
    }
    return best.takeIf { bestLength > eps }
}

/** Ořízne úsek obecným predikátem; 16 intervalů stačí pro konvexní eliptické disky podstav. */
private fun clipSegmentByInside(
    a: Offset3D,
    b: Offset3D,
    inside: (Offset3D) -> Boolean,
): List<Pair<Offset3D, Offset3D>> {
    val steps = 16
    fun point(t: Float) = a + (b - a) * t
    fun transition(t0: Float, t1: Float, insideAtT1: Boolean): Float {
        var lo = t0
        var hi = t1
        repeat(20) {
            val mid = (lo + hi) * 0.5f
            if (inside(point(mid)) == insideAtT1) hi = mid else lo = mid
        }
        return (lo + hi) * 0.5f
    }

    val out = mutableListOf<Pair<Offset3D, Offset3D>>()
    var runStart: Float? = if (inside(a)) 0f else null
    var previousT = 0f
    var previousInside = inside(a)
    for (i in 1..steps) {
        val t = i.toFloat() / steps
        val currentInside = inside(point(t))
        if (!previousInside && currentInside) runStart = transition(previousT, t, insideAtT1 = true)
        if (previousInside && !currentInside) {
            val end = transition(previousT, t, insideAtT1 = false)
            runStart?.let { start -> if (end - start > 1e-6f) out += point(start) to point(end) }
            runStart = null
        }
        previousT = t
        previousInside = currentInside
    }
    if (previousInside) runStart?.let { start -> if (1f - start > 1e-6f) out += point(start) to b }
    return out
}

private fun stitchSectionSegments(segments: List<Pair<Offset3D, Offset3D>>): List<SectionCurve> {
    if (segments.isEmpty()) return emptyList()
    val all = segments.flatMap { listOf(it.first, it.second) }
    val diagonal = Offset3D(
        all.maxOf { it.x } - all.minOf { it.x },
        all.maxOf { it.y } - all.minOf { it.y },
        all.maxOf { it.z } - all.minOf { it.z },
    ).length()
    val tolerance = max(1e-4f, diagonal * 2e-5f)
    val remaining = mutableListOf<Pair<Offset3D, Offset3D>>()
    for (segment in segments) {
        if ((segment.second - segment.first).length() <= tolerance) continue
        val duplicate = remaining.any { other ->
            (sameEndpoint(segment.first, other.first, tolerance) && sameEndpoint(segment.second, other.second, tolerance)) ||
                (sameEndpoint(segment.first, other.second, tolerance) && sameEndpoint(segment.second, other.first, tolerance))
        }
        if (!duplicate) remaining += segment
    }
    val sections = mutableListOf<SectionCurve>()

    while (remaining.isNotEmpty()) {
        val first = remaining.removeAt(remaining.lastIndex)
        val chain = mutableListOf(first.first, first.second)
        var changed = true
        while (changed) {
            changed = false
            for (index in remaining.indices) {
                val segment = remaining[index]
                when {
                    sameEndpoint(chain.last(), segment.first, tolerance) -> chain += segment.second
                    sameEndpoint(chain.last(), segment.second, tolerance) -> chain += segment.first
                    sameEndpoint(chain.first(), segment.second, tolerance) -> chain.add(0, segment.first)
                    sameEndpoint(chain.first(), segment.first, tolerance) -> chain.add(0, segment.second)
                    else -> continue
                }
                remaining.removeAt(index)
                changed = true
                break
            }
        }
        val closed = chain.size >= 4 && sameEndpoint(chain.first(), chain.last(), tolerance)
        val cleaned = cleanupSampledBranch(if (closed) chain.dropLast(1) else chain, tolerance * 0.25f)
        if (cleaned.size >= 2) {
            val points = if (cleaned.size == 2) listOf(cleaned[0], (cleaned[0] + cleaned[1]) * 0.5f, cleaned[1]) else cleaned
            sections += SectionCurve(points, closed)
        }
    }
    return sections
}

private data class SampleGenerator(
    val p: Offset3D,
    val d: Offset3D,
    val minT: Float,
    val maxT: Float,
    val param: Float = Float.NaN,
)
private data class LineHit(val t: Float, val point: Offset3D)
private data class LineSegmentHit(val from: Offset3D, val to: Offset3D)
private data class LineDiskHit(
    val points: List<Offset3D> = emptyList(),
    val segments: List<LineSegmentHit> = emptyList(),
)
private data class SampleHitSet(val sampleIndex: Int, val hits: List<Offset3D>)
private data class SampledBranchPoint(val sampleIndex: Int, val point: Offset3D)
private data class SampledRun(
    val points: List<Offset3D>,
    val sampleHitCount: Int,
    val closedHint: Boolean = false,
)
private data class BoundaryContinuation(
    val planePoint: Offset3D,
    val normalUnit: Offset3D,
    val insideFace: (Offset3D) -> Boolean,
    val sectionBetween: (from: Offset3D, to: Offset3D, insideFace: (Offset3D) -> Boolean) -> List<Offset3D>?,
    val fullSections: (() -> List<SectionCurve>)? = null,
)
private data class SectionCurve(val points: List<Offset3D>, val closed: Boolean)

private const val INTERSECTION_SAMPLE_COUNT = 960
private const val BOUNDARY_SECTION_SAMPLE_COUNT = 240

private fun sampledConeGenerators(state: MongeState, cone: ConicalSurface3D): List<SampleGenerator>? {
    val conic = state.conics3D.find { it.id == cone.directrixId } ?: return null
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId } ?: return null
    val apex = Offset3D(apexP.x, apexP.y, apexP.z)
    val directrix = ellipseParamsFromConic3D(conic) ?: return null

    return List(INTERSECTION_SAMPLE_COUNT) { i ->
        val t = 2f * PI.toFloat() * i / INTERSECTION_SAMPLE_COUNT
        coneGeneratorAt(apex, directrix, t)
    }
}

private fun sampledCylinderGenerators(state: MongeState, cylinder: CylindricalSurface3D): List<SampleGenerator>? {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return null
    val directrix = ellipseParamsFromConic3D(conic) ?: return null
    val w = cylinder.direction
    if ((w dot w) < 1e-12f) return null

    return List(INTERSECTION_SAMPLE_COUNT) { i ->
        val t = 2f * PI.toFloat() * i / INTERSECTION_SAMPLE_COUNT
        cylinderGeneratorAt(state, cylinder, directrix, t)
    }
}

private fun cylinderGeneratorRefiner(
    state: MongeState,
    cylinder: CylindricalSurface3D,
): ((Float) -> SampleGenerator)? {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return null
    val directrix = ellipseParamsFromConic3D(conic) ?: return null
    return { t -> cylinderGeneratorAt(state, cylinder, directrix, t) }
}

private fun coneGeneratorRefiner(
    state: MongeState,
    cone: ConicalSurface3D,
): ((Float) -> SampleGenerator)? {
    val conic = state.conics3D.find { it.id == cone.directrixId } ?: return null
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId } ?: return null
    val apex = Offset3D(apexP.x, apexP.y, apexP.z)
    val directrix = ellipseParamsFromConic3D(conic) ?: return null
    return { t -> coneGeneratorAt(apex, directrix, t) }
}

private fun coneGeneratorAt(
    apex: Offset3D,
    directrix: EllipseParam,
    t: Float,
): SampleGenerator {
    val base = directrix.center3D +
        directrix.uRot * (directrix.a * cos(t)) +
        directrix.vRot * (directrix.b * sin(t))
    return SampleGenerator(apex, base - apex, 0f, 1f, param = t)
}

private fun cylinderGeneratorAt(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    directrix: EllipseParam,
    t: Float,
): SampleGenerator {
    val w = cylinder.direction
    val base = directrix.center3D +
        directrix.uRot * (directrix.a * cos(t)) +
        directrix.vRot * (directrix.b * sin(t))
    val top = cylinderTopPoint(state, cylinder, base, w)
    val h = top?.let { ((it - base) dot w) / (w dot w) }
    return SampleGenerator(base, w, min(0f, h ?: Float.NEGATIVE_INFINITY), max(0f, h ?: Float.POSITIVE_INFINITY), param = t)
}

private fun addSampledSurfaceIntersectionCurve(
    state: MongeState,
    first: List<SampleGenerator>,
    firstAt: ((Float) -> SampleGenerator)? = null,
    firstAtPeriod: Float? = 2f * PI.toFloat(),
    second: (Offset3D, Offset3D) -> List<LineHit>,
    boundaryIntersections: List<BoundaryContinuation> = emptyList(),
    periodicFirst: Boolean = false,
    notifyWhenEmpty: Boolean = true,
): Boolean {
    val boundarySections = collectBoundaryIntersectionCurves(boundaryIntersections)
    val rawHitSets = buildSampleHitSets(first, firstAt, firstAtPeriod, second)
    val hitSets = if (periodicFirst && firstAt == null) periodicHitSets(rawHitSets) else rawHitSets
    val runs = mergeSampledRunsAtSharedEndpoints(traceSampledRunsByContinuation(hitSets))

    var added = false
    for (run in runs) {
        if (run.points.size < 3) continue
        val naturalClosed = run.closedHint || (run.sampleHitCount >= first.size - 2 && isClosedBranch(run.points))
        val points = if (naturalClosed) run.points else snapOpenRunEndsToBoundary(run.points, boundarySections)
        addIntersectionCurve3D(state, points, closed = naturalClosed)
        added = true
    }
    if (addBoundaryIntersectionCurves(state, boundarySections)) added = true
    if (!added && notifyWhenEmpty) notifyEmptyIntersection(state)
    return added
}

/**
 * Připraví cyklickou rodinu pro lineární tracer. Začátek přesune za nejdelší prázdný
 * úsek a první vzorek zopakuje na konci. Křivka se tak nerozbije jen proto, že její
 * souvislá větev prochází přes libovolně zvolený šev uzavřené řídicí křivky.
 */
private fun periodicHitSets(source: List<SampleHitSet>): List<SampleHitSet> {
    if (source.isEmpty() || source.all { it.hits.isEmpty() }) return source
    val size = source.size
    var bestGapLength = 0
    var bestStart = 0
    var currentLength = 0

    // Dvě periody dovolí najít i prázdný běh rozdělený původním švem.
    for (i in 0 until size * 2) {
        if (source[i % size].hits.isEmpty()) {
            currentLength = (currentLength + 1).coerceAtMost(size)
            if (currentLength > bestGapLength) {
                bestGapLength = currentLength
                bestStart = (i + 1) % size
            }
        } else {
            currentLength = 0
        }
    }

    val rotated = List(size) { source[(bestStart + it) % size].hits }
    val periodic = rotated + listOf(rotated.first())
    return periodic.mapIndexed { index, hits -> SampleHitSet(index, hits) }
}

private fun buildSampleHitSets(
    first: List<SampleGenerator>,
    firstAt: ((Float) -> SampleGenerator)?,
    firstAtPeriod: Float?,
    second: (Offset3D, Offset3D) -> List<LineHit>,
): List<SampleHitSet> {
    fun hitsFor(generator: SampleGenerator): List<Offset3D> =
        second(generator.p, generator.d)
            .filter { it.t.isFinite() && it.t >= generator.minT - 1e-3f && it.t <= generator.maxT + 1e-3f }
            .sortedBy { it.t }
            .distinctByNear { it.point }
            .map { it.point }

    val base = first.map { generator -> generator to hitsFor(generator) }
    if (firstAt == null) return base.mapIndexed { index, (_, hits) -> SampleHitSet(index, hits) }

    val out = mutableListOf<SampleHitSet>()
    fun add(hits: List<Offset3D>) {
        out += SampleHitSet(out.size, hits)
    }

    for (i in base.indices) {
        val (generator, hits) = base[i]
        add(hits)
        val next = base.getOrNull(i + 1) ?: continue
        refinedTransitionHits(generator, hits, next.first, next.second, firstAt, ::hitsFor)?.let(::add)
    }

    val firstSample = base.firstOrNull()
    val lastSample = base.lastOrNull()
    if (firstAtPeriod != null && firstSample != null && lastSample != null &&
        firstSample.first.param.isFinite() && lastSample.first.param.isFinite()
    ) {
        val wrappedFirstGenerator = firstAt(firstSample.first.param + firstAtPeriod)
        val wrappedFirstHits = hitsFor(wrappedFirstGenerator)
        refinedTransitionHits(lastSample.first, lastSample.second, wrappedFirstGenerator, wrappedFirstHits, firstAt, ::hitsFor)?.let(::add)
        if (wrappedFirstHits.isNotEmpty()) add(wrappedFirstHits)
    }
    return out
}

/**
 * Bisekce parametru mezi vzorky, kde se mění POČET zásahů (nejen prázdné ↔ neprázdné):
 * „uvnitř" je strana s více zásahy, konvergujeme k tečné površce, kde mizející dvojice
 * zásahů splývá. Vložený vzorek pak nechá konce větví potkat se v dotykovém bodě –
 * bez něj se větve zastaví až o krok vzorkování dřív a křivka má mezeru.
 */
private fun refinedTransitionHits(
    a: SampleGenerator,
    aHits: List<Offset3D>,
    b: SampleGenerator,
    bHits: List<Offset3D>,
    firstAt: (Float) -> SampleGenerator,
    hitsFor: (SampleGenerator) -> List<Offset3D>,
): List<Offset3D>? {
    if (!a.param.isFinite() || !b.param.isFinite()) return null
    if (aHits.size == bHits.size) return null

    val insideCount = max(aHits.size, bHits.size)
    var inside = if (aHits.size > bHits.size) a.param else b.param
    var outside = if (aHits.size > bHits.size) b.param else a.param
    var best = if (aHits.size > bHits.size) aHits else bHits

    repeat(24) {
        val mid = (inside + outside) * 0.5f
        val hits = hitsFor(firstAt(mid))
        if (hits.size >= insideCount) {
            inside = mid
            best = hits
        } else {
            outside = mid
        }
    }
    return best
}

private fun mergeSampledRunsAtSharedEndpoints(runs: List<SampledRun>): List<SampledRun> {
    val out = runs.toMutableList()
    var changed = true
    while (changed) {
        changed = false
        loop@ for (i in out.indices) {
            if (out[i].closedHint) continue
            for (j in i + 1 until out.size) {
                if (out[j].closedHint) continue
                val merged = mergeRunPair(out[i], out[j]) ?: continue
                out[i] = merged
                out.removeAt(j)
                changed = true
                break@loop
            }
        }
    }
    return out.map { closeRunIfEndpointShared(it) }
}

private fun mergeRunPair(a: SampledRun, b: SampledRun): SampledRun? {
    val ap = a.points
    val bp = b.points
    if (ap.isEmpty() || bp.isEmpty()) return null

    // Konce vzorkovaných větví se přesně nepotkají (bisekce dokonverguje jen přibližně,
    // sekce po stěnách vzorkují nezávisle) → toleranci odvozujeme z LOKÁLNÍ hustoty
    // vzorkování u spojovaných konců. V pásmu, kde křivka běží téměř rovnoběžně
    // s tvořicemi, kroky legitimně rostou a globální průměr by konce nespojil –
    // zbylá by díra a krátké ostrůvky (1–2 body) by se zahodily.
    // Duplicitní/velmi blízké body na švu odstraní cleanupSampledBranch.
    fun tol(aTail: Boolean, bHead: Boolean): Float = runJoinTolerance(
        endLocalStep(ap, tail = aTail),
        endLocalStep(bp, tail = !bHead),
        ap,
        bp,
    )
    val mergedPoints = when {
        sameEndpoint(ap.last(), bp.first(), tol(aTail = true, bHead = true)) -> ap + bp
        sameEndpoint(ap.last(), bp.last(), tol(aTail = true, bHead = false)) -> ap + bp.asReversed()
        sameEndpoint(ap.first(), bp.last(), tol(aTail = false, bHead = false)) -> bp + ap
        sameEndpoint(ap.first(), bp.first(), tol(aTail = false, bHead = true)) -> bp.asReversed() + ap
        else -> return null
    }
    return closeRunIfEndpointShared(
        SampledRun(
            points = cleanupSampledBranch(mergedPoints),
            sampleHitCount = a.sampleHitCount + b.sampleHitCount,
            closedHint = false
        )
    )
}

/** Průměr posledních až tří kroků u zvoleného konce běhu; 0 pro osamocený bod. */
private fun endLocalStep(points: List<Offset3D>, tail: Boolean): Float {
    if (points.size < 2) return 0f
    var sum = 0f
    var count = 0
    if (tail) {
        var i = points.size - 1
        while (i > 0 && count < 3) {
            sum += (points[i] - points[i - 1]).length()
            count++
            i--
        }
    } else {
        var i = 0
        while (i + 1 < points.size && count < 3) {
            sum += (points[i + 1] - points[i]).length()
            count++
            i++
        }
    }
    return if (count == 0) 0f else sum / count
}

private fun closeRunIfEndpointShared(run: SampledRun): SampledRun {
    if (run.closedHint || run.points.size < 4) return run
    val tol = 2f * averageStep(run.points)
    if (!sameEndpoint(run.points.first(), run.points.last(), tol)) return run
    return run.copy(points = run.points.dropLast(1), closedHint = true)
}

private fun runJoinTolerance(
    endStepA: Float,
    endStepB: Float,
    a: List<Offset3D>,
    b: List<Offset3D>,
): Float {
    val avgA = if (a.size >= 2) averageStep(a) else 0f
    val avgB = if (b.size >= 2) averageStep(b) else 0f
    return 2f * maxOf(endStepA, endStepB, 0.5f * max(avgA, avgB))
}

private fun sameEndpoint(a: Offset3D, b: Offset3D, tol: Float = 1e-4f): Boolean =
    (a - b).length() <= tol

private fun traceSampledRunsByContinuation(hitSets: List<SampleHitSet>): List<SampledRun> {
    val branches = mutableListOf<MutableList<SampledBranchPoint>>()
    var active = emptyList<Int>()
    val fallbackJump = continuationFallbackJump(hitSets)

    for ((sampleIndex, hits) in hitSets) {
        if (hits.isEmpty()) {
            // Analytický průsek jedné konkrétní tvořice může v blízkosti tečny
            // numericky vypadnout (diskriminant těsně pod nulou, šev dílčího
            // meridiánu apod.). Krátký výpadek proto větev neukončíme; při dalším
            // zásahu ji stejně musí potvrdit prostorová podmínka pokračování.
            active = active.filter { branchIndex ->
                val last = branches[branchIndex].lastOrNull()?.sampleIndex ?: return@filter false
                sampleIndex - last <= MAX_CONTINUATION_SAMPLE_GAP
            }
            continue
        }

        val previous = active.filter { branchIndex ->
            val last = branches[branchIndex].lastOrNull()?.sampleIndex ?: return@filter false
            sampleIndex - last in 1..MAX_CONTINUATION_SAMPLE_GAP
        }
        if (previous.size > 1 && hits.size == 1) {
            // Tečný bod: do jediného zásahu se sbíhají jen větve, které k němu opravdu
            // dosáhnou – připojení vzdálené větve by nakreslilo tětivu mimo plochu.
            val hit = hits.first()
            val joined = previous.filter { branchIndex ->
                (branches[branchIndex].last().point - hit).length() <=
                    branchContinuationTolerance(branches[branchIndex], fallbackJump)
            }
            if (joined.isNotEmpty()) {
                for (branchIndex in joined) {
                    branches[branchIndex] += SampledBranchPoint(sampleIndex, hit)
                }
                active = joined
                continue
            }
        }

        val assignments = nearestContinuationAssignments(previous, hits, branches, fallbackJump)
        val assignedHits = assignments.map { it.second }.toMutableSet()
        val nextActive = mutableListOf<Int>()

        for ((branchIndex, hitIndex) in assignments) {
            branches[branchIndex] += SampledBranchPoint(sampleIndex, hits[hitIndex])
            nextActive += branchIndex
        }
        for (hitIndex in hits.indices) {
            if (hitIndex in assignedHits) continue
            // Seed z jediné předchozí větve jen pro skutečné tečné rozdvojení (blízko);
            // vzdálený nový zásah je zrod jiné smyčky → větev začíná bez seedu.
            val tangentStart = previous
                .singleOrNull()
                ?.let { branchIndex ->
                    branches[branchIndex].lastOrNull()?.takeIf {
                        sampleIndex - it.sampleIndex in 1..MAX_CONTINUATION_SAMPLE_GAP &&
                            (it.point - hits[hitIndex]).length() <=
                            branchContinuationTolerance(branches[branchIndex], fallbackJump)
                    }
                }
            branches += if (tangentStart != null) {
                mutableListOf(tangentStart, SampledBranchPoint(sampleIndex, hits[hitIndex]))
            } else {
                mutableListOf(SampledBranchPoint(sampleIndex, hits[hitIndex]))
            }
            nextActive += branches.lastIndex
        }
        active = nextActive
    }

    // Krátké běhy (1–2 body) se nechávají projít: v pásmu tečného driftu podél
    // tvořic z nich slučování konců poskládá pokračování hlavní větve; osamocené
    // zbytky zahodí až finální filtr (< 3 body) ve volajícím.
    return branches.mapNotNull { branch ->
        val cleaned = cleanupSampledBranch(branch.map { it.point })
        if (cleaned.isNotEmpty()) SampledRun(cleaned, branch.size) else null
    }
}

/** Nejvýše tři po sobě jdoucí chybějící vzorky; delší mezera už značí jinou komponentu. */
private const val MAX_CONTINUATION_MISSED_SAMPLES = 3
private const val MAX_CONTINUATION_SAMPLE_GAP = MAX_CONTINUATION_MISSED_SAMPLES + 1

/**
 * Strop skoku pro pokračování větve do dalšího vzorku: násobek posledních kroků větve
 * (u tečného přechodu kroky legitimně rostou, ale postupně), s dolní pojistkou [fallback]
 * pro čerstvé větve bez historie. Bez stropu se umírající větev „přecvakne" na zásah
 * z jiné smyčky křivky a vznikne dlouhá tětiva, která na ploše vůbec neleží.
 */
private const val BRANCH_JUMP_FACTOR = 8f

private fun branchContinuationTolerance(branch: List<SampledBranchPoint>, fallback: Float): Float {
    var sum = 0f
    var count = 0
    var i = branch.size - 1
    while (i > 0 && count < 3) {
        sum += (branch[i].point - branch[i - 1].point).length()
        count++
        i--
    }
    if (count == 0) return fallback
    return max(BRANCH_JUMP_FACTOR * sum / count, fallback)
}

/** Pojistka stropu skoku: zlomek prostorového rozsahu všech zásahů (tětivy bývají řádově větší). */
private fun continuationFallbackJump(hitSets: List<SampleHitSet>): Float {
    var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
    var any = false
    for (set in hitSets) for (p in set.hits) {
        any = true
        if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z
    }
    if (!any) return Float.POSITIVE_INFINITY
    val diag = Offset3D(maxX - minX, maxY - minY, maxZ - minZ).length()
    return (0.06f * diag).coerceAtLeast(1e-3f)
}

private fun nearestContinuationAssignments(
    previousBranches: List<Int>,
    hits: List<Offset3D>,
    branches: List<List<SampledBranchPoint>>,
    fallbackJump: Float,
): List<Pair<Int, Int>> {
    if (previousBranches.isEmpty() || hits.isEmpty()) return emptyList()
    val candidates = mutableListOf<Triple<Float, Int, Int>>()
    for (branchIndex in previousBranches) {
        val branch = branches[branchIndex]
        val lastPoint = branch.lastOrNull()?.point ?: continue
        val tolerance = branchContinuationTolerance(branch, fallbackJump)
        for (hitIndex in hits.indices) {
            val dist = (lastPoint - hits[hitIndex]).length()
            if (dist <= tolerance) candidates += Triple(dist, branchIndex, hitIndex)
        }
    }

    val usedBranches = mutableSetOf<Int>()
    val usedHits = mutableSetOf<Int>()
    val out = mutableListOf<Pair<Int, Int>>()
    for ((_, branchIndex, hitIndex) in candidates.sortedBy { it.first }) {
        if (branchIndex in usedBranches || hitIndex in usedHits) continue
        usedBranches += branchIndex
        usedHits += hitIndex
        out += branchIndex to hitIndex
    }
    return out.sortedBy { it.second }
}

private fun List<LineHit>.distinctByNear(eps: Float = 1e-3f, selector: (LineHit) -> Offset3D): List<LineHit> {
    val out = mutableListOf<LineHit>()
    for (hit in this) {
        if (out.none { (selector(it) - selector(hit)).length() <= eps }) out += hit
    }
    return out
}

private fun cleanupSampledBranch(points: List<Offset3D>, eps: Float = 1e-3f): List<Offset3D> {
    val out = mutableListOf<Offset3D>()
    for (p in points) {
        if (out.lastOrNull()?.let { (it - p).length() <= eps } != true) out += p
    }
    return out
}

private fun isClosedBranch(points: List<Offset3D>): Boolean {
    if (points.size < 4) return false
    val scale = points.zipWithNext().map { (a, b) -> (a - b).length() }.average().toFloat().coerceAtLeast(1f)
    return (points.first() - points.last()).length() <= 3f * scale
}

private fun closeWithBoundaryContinuation(
    points: List<Offset3D>,
    continuations: List<BoundaryContinuation>,
): Pair<List<Offset3D>, Boolean>? {
    if (points.size < 3 || continuations.isEmpty()) return null
    val from = points.last()
    val to = points.first()
    for (continuation in continuations) {
        if (!pointOnPlane(from, continuation.planePoint, continuation.normalUnit)) continue
        if (!pointOnPlane(to, continuation.planePoint, continuation.normalUnit)) continue
        val section = continuation.sectionBetween(from, to, continuation.insideFace) ?: continue
        val cleaned = cleanupSampledBranch(section)
        if (cleaned.size < 2) continue
        return (points + cleaned.drop(1)) to true
    }
    return null
}

private fun pointOnPlane(point: Offset3D, planePoint: Offset3D, normalUnit: Offset3D): Boolean {
    val scale = (point - planePoint).length().coerceAtLeast(1f)
    return abs((point - planePoint) dot normalUnit) <= 2e-3f * scale
}

private fun BoundaryContinuation?.asList(): List<BoundaryContinuation> =
    if (this == null) emptyList() else listOf(this)

private fun BoundaryContinuation?.withFullSections(
    fullSections: (BoundaryContinuation) -> List<SectionCurve>,
): BoundaryContinuation? =
    this?.copy(fullSections = { fullSections(this) })

private fun List<BoundaryContinuation>.withFullSections(
    fullSections: (BoundaryContinuation) -> List<SectionCurve>,
): List<BoundaryContinuation> =
    map { boundary -> boundary.copy(fullSections = { fullSections(boundary) }) }

private fun addBoundaryIntersectionCurves(
    state: MongeState,
    sections: List<SectionCurve>,
): Boolean {
    var added = false
    for (section in sections) {
        addIntersectionCurve3D(state, section.points, closed = section.closed)
        added = true
    }
    return added
}

private fun collectBoundaryIntersectionCurves(
    boundaries: List<BoundaryContinuation>,
): List<SectionCurve> {
    val out = mutableListOf<SectionCurve>()
    val seen = mutableListOf<List<Offset3D>>()
    for (boundary in boundaries) {
        val sections = boundary.fullSections?.invoke() ?: continue
        for (section in sections) {
            val cleaned = cleanupSampledBranch(section.points)
            if (cleaned.size < 3) continue
            if (seen.any { sameSampledCurve(it, cleaned) }) continue
            out += SectionCurve(cleaned, section.closed)
            seen += cleaned
        }
    }
    return out
}

private fun snapOpenRunEndsToBoundary(
    points: List<Offset3D>,
    boundarySections: List<SectionCurve>,
): List<Offset3D> {
    if (points.size < 2 || boundarySections.isEmpty()) return points
    val boundaryEnds = boundarySections
        .filter { !it.closed && it.points.size >= 2 }
        .flatMap { listOf(it.points.first(), it.points.last()) }
    if (boundaryEnds.isEmpty()) return points

    val snapDistance = sideBoundarySnapDistance(points, boundaryEnds)
    fun snapped(p: Offset3D): Offset3D {
        val nearest = boundaryEnds.minByOrNull { (it - p).length() } ?: return p
        return if ((nearest - p).length() <= snapDistance) nearest else p
    }

    val out = points.toMutableList()
    out[0] = snapped(out.first())
    out[out.lastIndex] = snapped(out.last())
    return cleanupSampledBranch(out)
}

private fun sideBoundarySnapDistance(points: List<Offset3D>, boundaryEnds: List<Offset3D>): Float {
    val step = points.zipWithNext()
        .map { (a, b) -> (a - b).length() }
        .average()
        .toFloat()
        .takeIf { it.isFinite() && it > 1e-6f }
        ?: 1f
    val all = points + boundaryEnds
    val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
    val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
    val minZ = all.minOf { it.z }; val maxZ = all.maxOf { it.z }
    val diag = Offset3D(maxX - minX, maxY - minY, maxZ - minZ).length()
    return maxOf(4f * step, 0.025f * diag, 1e-3f)
}

private fun sameSampledCurve(a: List<Offset3D>, b: List<Offset3D>): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    val eps = 1e-2f * averageStep(a).coerceAtLeast(averageStep(b)).coerceAtLeast(1f)
    return ((a.first() - b.first()).length() <= eps && (a.last() - b.last()).length() <= eps) ||
            ((a.first() - b.last()).length() <= eps && (a.last() - b.first()).length() <= eps)
}

private fun averageStep(points: List<Offset3D>): Float =
    points.zipWithNext().map { (a, b) -> (a - b).length() }.average().toFloat().coerceAtLeast(1f)

private fun coneBaseBoundaryContinuation(
    state: MongeState,
    cone: ConicalSurface3D,
    sampleSection: (
        planePoint: Offset3D,
        planeNormal: Offset3D,
        from: Offset3D,
        to: Offset3D,
        insideFace: (Offset3D) -> Boolean,
    ) -> List<Offset3D>?,
): BoundaryContinuation? {
    val conic = state.conics3D.find { it.id == cone.directrixId } ?: return null
    val normal = (conic.u cross conic.v).normalizeOrNull() ?: return null
    val inside = ellipseInsidePredicate(conic) ?: return null
    return BoundaryContinuation(
        planePoint = conic.p0,
        normalUnit = normal,
        insideFace = inside,
        sectionBetween = { from, to, insideFace ->
            sampleSection(conic.p0, normal, from, to, insideFace)
        }
    )
}

private fun cylinderBoundaryContinuations(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    sampleSection: (
        planePoint: Offset3D,
        planeNormal: Offset3D,
        from: Offset3D,
        to: Offset3D,
        insideFace: (Offset3D) -> Boolean,
    ) -> List<Offset3D>?,
): List<BoundaryContinuation> {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return emptyList()
    val baseNormal = (conic.u cross conic.v).normalizeOrNull() ?: return emptyList()
    val baseInside = ellipseInsidePredicate(conic) ?: return emptyList()
    val out = mutableListOf(
        BoundaryContinuation(
            planePoint = conic.p0,
            normalUnit = baseNormal,
            insideFace = baseInside,
            sectionBetween = { from, to, insideFace ->
                sampleSection(conic.p0, baseNormal, from, to, insideFace)
            }
        )
    )

    cylinderTopPlane(state, cylinder)?.let { (topPoint, topNormal) ->
        val topInside: (Offset3D) -> Boolean = { p ->
            val w = cylinder.direction
            val n = conic.u cross conic.v
            val nw = n dot w
            if (abs(nw) < 1e-9f) false
            else baseInside(p - w * ((n dot (p - conic.p0)) / nw))
        }
        out += BoundaryContinuation(
            planePoint = topPoint,
            normalUnit = topNormal,
            insideFace = topInside,
            sectionBetween = { from, to, insideFace ->
                sampleSection(topPoint, topNormal, from, to, insideFace)
            }
        )
    }

    return out
}

private fun cylinderTopPlane(state: MongeState, cylinder: CylindricalSurface3D): Pair<Offset3D, Offset3D>? {
    cylinder.topPlaneId?.let { id ->
        state.planes3D.find { it.id == id }?.equation?.let { eq ->
            val normal = Offset3D(eq.a, eq.b, eq.c)
            val len2 = normal dot normal
            if (len2 > 1e-12f) {
                val point = normal * (-eq.d / len2)
                return point to (normal.normalizeOrNull() ?: return null)
            }
        }
    }
    cylinder.upperConicId?.let { id ->
        state.conics3D.find { it.id == id }?.let { conic ->
            val normal = (conic.u cross conic.v).normalizeOrNull() ?: return null
            return conic.p0 to normal
        }
    }
    return null
}

private fun ellipseInsidePredicate(conic: ConicSection3D): ((Offset3D) -> Boolean)? {
    val el = ellipseParamsFromConic3D(conic) ?: return null
    val u = el.uRot.normalizeOrNull() ?: return null
    val v = el.vRot.normalizeOrNull() ?: return null
    val a = el.a.coerceAtLeast(1e-6f)
    val b = el.b.coerceAtLeast(1e-6f)
    return { p ->
        val rel = p - el.center3D
        val x = (rel dot u) / a
        val y = (rel dot v) / b
        x * x + y * y <= 1.002f
    }
}

private fun samplePlaneSphereSectionBetween(
    state: MongeState,
    sphere: SphereSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    from: Offset3D,
    to: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? {
    val centerP = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId } ?: return null
    val center = Offset3D(centerP.x, centerP.y, centerP.z)
    val n = planeNormal.normalizeOrNull() ?: return null
    val dist = (center - planePoint) dot n
    val radius = sphere.radius
    if (abs(dist) >= radius - 1e-4f * radius) return null
    val circleCenter = center - n * dist
    val rho = sqrt((radius * radius - dist * dist).coerceAtLeast(0f))
    val (u, v) = planeBasis(n)
    val conic = ConicSection3D(
        p0 = circleCenter,
        u = u,
        v = v,
        matrix = Matrix3x3.fromCoefficients(1f, 0f, 1f, 0f, 0f, -rho * rho),
        a = rho,
        b = rho
    )
    return sampleConicSectionBetween(conic, ConicKindPublic.ELLIPSE, from, to, insideFace)
}

private fun samplePlaneSphereSectionRuns(
    state: MongeState,
    sphere: SphereSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val centerP = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId } ?: return emptyList()
    val center = Offset3D(centerP.x, centerP.y, centerP.z)
    val n = planeNormal.normalizeOrNull() ?: return emptyList()
    val dist = (center - planePoint) dot n
    val radius = sphere.radius
    if (abs(dist) >= radius - 1e-4f * radius) return emptyList()
    val circleCenter = center - n * dist
    val rho = sqrt((radius * radius - dist * dist).coerceAtLeast(0f))
    val (u, v) = planeBasis(n)
    val conic = ConicSection3D(
        p0 = circleCenter,
        u = u,
        v = v,
        matrix = Matrix3x3.fromCoefficients(1f, 0f, 1f, 0f, 0f, -rho * rho),
        a = rho,
        b = rho
    )
    return sampleFullConicSectionRuns(conic, ConicKindPublic.ELLIPSE, insideFace)
}

private fun samplePlaneCylinderSectionBetween(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    from: Offset3D,
    to: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return null
    val n = planeNormal.normalizeOrNull() ?: return null
    val d = -(n dot planePoint)
    val section = cylinderSectionEllipse(cylinder, conic, n, d, creationIndex = 0L) ?: return null
    return sampleConicSectionBetween(section, ConicKindPublic.ELLIPSE, from, to, insideFace)
}

private fun samplePlaneCylinderSectionRuns(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return emptyList()
    val n = planeNormal.normalizeOrNull() ?: return emptyList()
    val d = -(n dot planePoint)
    val section = cylinderSectionEllipse(cylinder, conic, n, d, creationIndex = 0L) ?: return emptyList()
    return sampleFullConicSectionRuns(section, ConicKindPublic.ELLIPSE) { p ->
        insideFace(p) && pointOnFiniteCylinder(state, cylinder, p)
    }
}

private fun samplePlaneConeSectionBetween(
    state: MongeState,
    cone: ConicalSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    from: Offset3D,
    to: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? {
    val conic = state.conics3D.find { it.id == cone.directrixId } ?: return null
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId } ?: return null
    val apex = Offset3D(apexP.x, apexP.y, apexP.z)
    val n = planeNormal.normalizeOrNull() ?: return null
    val d = -(n dot planePoint)
    val (kind, section) = coneSectionConic(cone, conic, apex, n, d, creationIndex = 0L) ?: return null
    return sampleConicSectionBetween(section, kind, from, to, insideFace)
}

private fun samplePlaneConeSectionRuns(
    state: MongeState,
    cone: ConicalSurface3D,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val conic = state.conics3D.find { it.id == cone.directrixId } ?: return emptyList()
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId } ?: return emptyList()
    val apex = Offset3D(apexP.x, apexP.y, apexP.z)
    val n = planeNormal.normalizeOrNull() ?: return emptyList()
    val d = -(n dot planePoint)
    val (kind, section) = coneSectionConic(cone, conic, apex, n, d, creationIndex = 0L) ?: return emptyList()
    return sampleFullConicSectionRuns(section, kind) { p ->
        insideFace(p) && pointOnFiniteCone(state, cone, p)
    }
}

private fun sampleConicSectionBetween(
    conic: ConicSection3D,
    kind: ConicKindPublic,
    from: Offset3D,
    to: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? =
    when (kind) {
        ConicKindPublic.ELLIPSE -> sampleEllipseSectionBetween(conic, from, to, insideFace)
        ConicKindPublic.PARABOLA -> sampleParabolaSectionBetween(conic, from, to, insideFace)
        ConicKindPublic.HYPERBOLA -> sampleHyperbolaSectionBetween(conic, from, to, insideFace)
    }

private fun sampleFullConicSectionRuns(
    conic: ConicSection3D,
    kind: ConicKindPublic,
    inside: (Offset3D) -> Boolean,
): List<SectionCurve> =
    when (kind) {
        ConicKindPublic.ELLIPSE -> sampleFullEllipseSectionRuns(conic, inside)
        ConicKindPublic.PARABOLA -> sampleFullOpenConicSectionRuns(conic, inside, ::sampleParabolaPointAt)
        ConicKindPublic.HYPERBOLA -> sampleFullHyperbolaSectionRuns(conic, inside)
    }

private fun sampleFullEllipseSectionRuns(
    conic: ConicSection3D,
    inside: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val el = ellipseParamsFromConic3D(conic) ?: return emptyList()
    val u = el.uRot.normalizeOrNull() ?: return emptyList()
    val v = el.vRot.normalizeOrNull() ?: return emptyList()
    val a = el.a.coerceAtLeast(1e-6f)
    val b = el.b.coerceAtLeast(1e-6f)
    fun pointAt(t: Float): Offset3D = el.center3D + u * (a * cos(t)) + v * (b * sin(t))
    val samples = BOUNDARY_SECTION_SAMPLE_COUNT
    fun tAt(i: Int): Float = 2f * PI.toFloat() * i / samples
    fun insideAt(t: Float): Boolean = inside(pointAt(t))
    val flags = List(samples) { i -> insideAt(tAt(i)) }
    if (flags.all { it }) return listOf(SectionCurve(List(samples) { i -> pointAt(tAt(i)) }, closed = true))
    if (flags.none { it }) return emptyList()

    val start = flags.indexOfFirst { !it }
    val runs = mutableListOf<SectionCurve>()
    var i = start
    val end = start + samples
    while (i < end) {
        if (flags[i % samples]) {
            val runStart = i
            while (i < end && flags[i % samples]) i++
            val runEnd = i - 1
            val tStart = refineBoundary(tAt(runStart - 1), tAt(runStart), ::insideAt)
            val tEnd = refineBoundary(tAt(runEnd + 1), tAt(runEnd), ::insideAt)
            val steps = max(8, ceil(abs(tEnd - tStart) / (2f * PI.toFloat()) * samples).toInt())
            val run = List(steps + 1) { k -> pointAt(tStart + (tEnd - tStart) * k / steps) }
            if (run.size >= 3) runs += SectionCurve(run, closed = false)
        } else {
            i++
        }
    }
    return runs
}

private fun sampleFullOpenConicSectionRuns(
    conic: ConicSection3D,
    inside: (Offset3D) -> Boolean,
    pointAt: (ConicSection3D, Float) -> Offset3D?,
): List<SectionCurve> {
    val range = estimateOpenConicRange(conic)
    val samples = BOUNDARY_SECTION_SAMPLE_COUNT * 2
    val points = List(samples + 1) { i ->
        val t = -range + 2f * range * i / samples
        pointAt(conic, t)
    }
    return splitInsideRuns(points, inside)
}

private fun sampleFullHyperbolaSectionRuns(
    conic: ConicSection3D,
    inside: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val range = 4.5f
    val samples = BOUNDARY_SECTION_SAMPLE_COUNT * 2
    val out = mutableListOf<SectionCurve>()
    for (sign in listOf(1f, -1f)) {
        val points = List(samples + 1) { i ->
            val t = -range + 2f * range * i / samples
            sampleHyperbolaPointAt(conic, t, sign)
        }
        out += splitInsideRuns(points, inside)
    }
    return out
}

private fun splitInsideRuns(
    points: List<Offset3D?>,
    inside: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val out = mutableListOf<SectionCurve>()
    var current = mutableListOf<Offset3D>()
    for (p in points) {
        if (p != null && inside(p)) {
            current += p
        } else {
            if (current.size >= 3) out += SectionCurve(current, closed = false)
            current = mutableListOf()
        }
    }
    if (current.size >= 3) out += SectionCurve(current, closed = false)
    return out
}

private fun estimateOpenConicRange(conic: ConicSection3D): Float {
    val scale = ((conic.a ?: 0f) + (conic.b ?: 0f) + conic.u.length() + conic.v.length()).coerceAtLeast(50f)
    return (scale * 3f).coerceIn(100f, 5000f)
}

private fun sampleParabolaPointAt(conic: ConicSection3D, t: Float): Offset3D? {
    val u = conic.u.normalizeOrNull() ?: return null
    val v = (conic.v - u * (conic.v dot u)).normalizeOrNull() ?: return null
    val a = conic.matrix.m00
    if (abs(a) < 1e-6f) return null
    val focal = 1f / (4f * a)
    return conic.p0 + u * t + v * (t * t / (4f * focal))
}

private fun sampleHyperbolaPointAt(conic: ConicSection3D, t: Float, sign: Float): Offset3D? {
    val a = conic.a ?: return null
    val b = conic.b ?: return null
    val u = conic.u.normalizeOrNull() ?: return null
    val v = (conic.v - u * (conic.v dot u)).normalizeOrNull() ?: return null
    return conic.p0 + u * (sign * a * coshLocal(t)) + v * (b * sinhLocal(t))
}

private fun sampleEllipseSectionBetween(
    conic: ConicSection3D,
    from: Offset3D,
    to: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? {
    val el = ellipseParamsFromConic3D(conic) ?: return null
    val u = el.uRot.normalizeOrNull() ?: return null
    val v = el.vRot.normalizeOrNull() ?: return null
    val a = el.a.coerceAtLeast(1e-6f)
    val b = el.b.coerceAtLeast(1e-6f)
    fun param(p: Offset3D): Float {
        val rel = p - el.center3D
        return atan2((rel dot v) / b, (rel dot u) / a)
    }
    fun pointAt(t: Float): Offset3D = el.center3D + u * (a * cos(t)) + v * (b * sin(t))

    val t0 = param(from)
    val t1 = param(to)
    val twoPi = 2f * PI.toFloat()
    fun normalizePositive(x: Float): Float = ((x % twoPi) + twoPi) % twoPi
    fun sample(delta: Float): List<Offset3D> {
        val steps = max(8, ceil(abs(delta) / twoPi * BOUNDARY_SECTION_SAMPLE_COUNT).toInt())
        return List(steps + 1) { i ->
            when (i) {
                0 -> from
                steps -> to
                else -> pointAt(t0 + delta * i / steps)
            }
        }
    }

    val ccw = sample(normalizePositive(t1 - t0))
    val cw = sample(-normalizePositive(t0 - t1))
    return chooseBoundarySection(listOf(ccw, cw), insideFace)
}

private fun sampleParabolaSectionBetween(
    conic: ConicSection3D,
    from: Offset3D,
    to: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? {
    val u = conic.u.normalizeOrNull() ?: return null
    val v = (conic.v - u * (conic.v dot u)).normalizeOrNull() ?: return null
    val a = conic.matrix.m00
    if (abs(a) < 1e-6f) return null
    val focal = 1f / (4f * a)
    val t0 = (from - conic.p0) dot u
    val t1 = (to - conic.p0) dot u
    val steps = max(8, BOUNDARY_SECTION_SAMPLE_COUNT / 2)
    val points = List(steps + 1) { i ->
        when (i) {
            0 -> from
            steps -> to
            else -> {
                val t = t0 + (t1 - t0) * i / steps
                conic.p0 + u * t + v * (t * t / (4f * focal))
            }
        }
    }
    return chooseBoundarySection(listOf(points), insideFace)
}

private fun sampleHyperbolaSectionBetween(
    conic: ConicSection3D,
    from: Offset3D,
    to: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? {
    val a = conic.a ?: return null
    val b = conic.b ?: return null
    val u = conic.u.normalizeOrNull() ?: return null
    val v = (conic.v - u * (conic.v dot u)).normalizeOrNull() ?: return null
    fun branchSign(p: Offset3D): Float {
        val x = (p - conic.p0) dot u
        return if (x >= 0f) 1f else -1f
    }
    val sign = branchSign(from)
    if (sign != branchSign(to)) return null
    fun param(p: Offset3D): Float = asinhLocal(((p - conic.p0) dot v) / b)
    val t0 = param(from)
    val t1 = param(to)
    val steps = max(8, BOUNDARY_SECTION_SAMPLE_COUNT / 2)
    val points = List(steps + 1) { i ->
        when (i) {
            0 -> from
            steps -> to
            else -> {
                val t = t0 + (t1 - t0) * i / steps
                conic.p0 + u * (sign * a * coshLocal(t)) + v * (b * sinhLocal(t))
            }
        }
    }
    return chooseBoundarySection(listOf(points), insideFace)
}

private fun chooseBoundarySection(
    candidates: List<List<Offset3D>>,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? {
    val scored = candidates
        .filter { it.size >= 2 }
        .map { points ->
            val insideCount = points.count(insideFace)
            val length = points.zipWithNext().sumOf { (a, b) -> (a - b).length().toDouble() }.toFloat()
            Triple(points, insideCount.toFloat() / points.size.toFloat(), length)
        }
        .filter { it.second >= 0.55f }
    return scored.maxWithOrNull(compareBy<Triple<List<Offset3D>, Float, Float>> { it.second }.thenByDescending { -it.third })?.first
}

private fun refineBoundary(tOut: Float, tIn: Float, inside: (Float) -> Boolean): Float {
    var lo = tOut
    var hi = tIn
    repeat(24) {
        val mid = (lo + hi) * 0.5f
        if (inside(mid)) hi = mid else lo = mid
    }
    return hi
}

private fun Offset3D.normalizeOrNull(): Offset3D? {
    val len = length()
    return if (len < 1e-9f) null else this * (1f / len)
}

private fun asinhLocal(x: Float): Float = ln(x + sqrt(x * x + 1f))
private fun sinhLocal(x: Float): Float = ((exp(x) - exp(-x)) * 0.5f)
private fun coshLocal(x: Float): Float = ((exp(x) + exp(-x)) * 0.5f)

private fun addIntersectionCurve3D(
    state: MongeState,
    points: List<Offset3D>,
    closed: Boolean,
) {
    val showOthersInAxo = state.projectionMode != ProjectionMode.AXO

    val curve3D = Curve3D(
        name = INTERSECTION_RESULT_NAME,
        color = INTERSECTION_RESULT_COLOR,
        strokeWidth = INTERSECTION_RESULT_STROKE_WIDTH,
        pointIds = emptyList(),
        closed = closed,
        lineStyle = state.currentLineStyleSettings.style,
        creationIndex = allocIndex(state),
        polyline3D = points
    )
    state.curves3D.add(curve3D)
    state.curvesPudorys.add(
        CurvePudorys(
            parentId = curve3D.id,
            name = "${INTERSECTION_RESULT_NAME}₁",
            color = INTERSECTION_RESULT_COLOR,
            strokeWidth = curve3D.strokeWidth,
            lineStyle = curve3D.lineStyle,
            points = emptyList(),
            closed = closed,
            parent = curve3D,
            showInAxoInitial = showOthersInAxo,
            creationIndex = allocIndex(state),
            polylineLocal = points.map { Offset(it.x, it.y) }
        )
    )
    state.curvesNarys.add(
        CurveNarys(
            parentId = curve3D.id,
            parent = curve3D,
            name = "${INTERSECTION_RESULT_NAME}₂",
            color = INTERSECTION_RESULT_COLOR,
            strokeWidth = curve3D.strokeWidth,
            pointIds = emptyList(),
            closed = closed,
            lineStyle = curve3D.lineStyle,
            creationIndex = allocIndex(state),
            showInAxoInitial = showOthersInAxo,
            polylineLocal = points.map { Offset(it.x, it.z) }
        )
    )

    if (state.projectionMode == ProjectionMode.AXO) {
        state.curvesBokorys.add(
            CurveBokorys(
                parentId = curve3D.id,
                parent = curve3D,
                name = "${INTERSECTION_RESULT_NAME}₃",
                color = INTERSECTION_RESULT_COLOR,
                strokeWidth = curve3D.strokeWidth,
                pointIds = emptyList(),
                closed = closed,
                lineStyle = curve3D.lineStyle,
                creationIndex = allocIndex(state),
                showInAxoInitial = false,
                polylineLocal = points.map { Offset(it.y, it.z) }
            )
        )
        if (state.basis != null) {
            state.curvesAxo.add(
                CurveAxo(
                    parentId = curve3D.id,
                    parent = curve3D,
                    name = "${INTERSECTION_RESULT_NAME}ₐ",
                    color = INTERSECTION_RESULT_COLOR,
                    strokeWidth = curve3D.strokeWidth,
                    pointIds = emptyList(),
                    closed = closed,
                    lineStyle = curve3D.lineStyle,
                    creationIndex = allocIndex(state),
                    showInAxoInitial = true,
                    polyline3D = points
                )
            )
        }
    }

    update2DSnapshots(state)
    state.triggerRedraw++
}

private fun lineSphereIntersections(p: Offset3D, d: Offset3D, sphere: SphereSurface3D, state: MongeState): List<LineHit> {
    val centerP = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId } ?: return emptyList()
    val c = Offset3D(centerP.x, centerP.y, centerP.z)
    val e = p - c
    val a = d dot d
    val b = 2f * (e dot d)
    val cc = (e dot e) - sphere.radius * sphere.radius
    return solveQuadratic(a, b, cc).map { t -> LineHit(t, p + d * t) }
}

private fun lineCylinderIntersections(p: Offset3D, d: Offset3D, cylinder: CylindricalSurface3D, state: MongeState): List<LineHit> {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return emptyList()
    val w = cylinder.direction
    val p0 = conic.p0
    val n = conic.u cross conic.v
    val nw = n dot w
    if (abs(nw) < 1e-9f) return emptyList()

    val rel = p - p0
    val st0 = projectedCylinderLocalCoordinates(conic, w, rel) ?: return emptyList()
    val stD = projectedCylinderLocalCoordinates(conic, w, d) ?: return emptyList()
    val (qa, qb, qc) = conicSurfaceQuadratic(
        conic,
        Sa = st0.first, Sb = stD.first,
        Ta = st0.second, Tb = stD.second,
        Ga = 1f, Gb = 0f
    )
    return solveQuadratic(qa, qb, qc)
        .map { t -> LineHit(t, p + d * t) }
        .filter { pointOnFiniteCylinder(state, cylinder, it.point) }
}

private fun lineConeIntersections(p: Offset3D, d: Offset3D, cone: ConicalSurface3D, state: MongeState): List<LineHit> {
    val conic = state.conics3D.find { it.id == cone.directrixId } ?: return emptyList()
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId } ?: return emptyList()
    val a3 = Offset3D(apexP.x, apexP.y, apexP.z)
    val p0 = conic.p0
    val u = conic.u
    val v = conic.v
    val n = u cross v
    val k = n dot (p0 - a3)
    if (abs(k) < 1e-9f) return emptyList()

    val g0 = n dot (p - a3)
    val g1 = n dot d
    val apU = (a3 - p0) dot u
    val apV = (a3 - p0) dot v
    val au0 = (p - a3) dot u
    val au1 = d dot u
    val av0 = (p - a3) dot v
    val av1 = d dot v
    val (qa, qb, qc) = conicSurfaceQuadratic(
        conic,
        Sa = apU * g0 + k * au0, Sb = apU * g1 + k * au1,
        Ta = apV * g0 + k * av0, Tb = apV * g1 + k * av1,
        Ga = g0, Gb = g1
    )
    return solveQuadratic(qa, qb, qc)
        .map { t -> LineHit(t, p + d * t) }
        .filter { pointOnFiniteCone(state, cone, it.point) }
}

private fun pointOnFiniteCone(state: MongeState, cone: ConicalSurface3D, point: Offset3D): Boolean {
    val conic = state.conics3D.find { it.id == cone.directrixId } ?: return false
    val apexP = state.sharedPoints3D.firstOrNull { it.id == cone.apexId } ?: return false
    val apex = Offset3D(apexP.x, apexP.y, apexP.z)
    val n = conic.u cross conic.v
    val denom = n dot (conic.p0 - apex)
    if (abs(denom) < 1e-9f) return false
    val lambda = (n dot (point - apex)) / denom
    return lambda >= -1e-3f && lambda <= 1f + 1e-3f
}

internal fun pointOnFiniteCylinder(state: MongeState, cylinder: CylindricalSurface3D, point: Offset3D): Boolean {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return false
    val w = cylinder.direction
    val n = conic.u cross conic.v
    val nw = n dot w
    if (abs(nw) < 1e-9f) return false
    val base = point - w * ((n dot (point - conic.p0)) / nw)
    val top = cylinderTopPoint(state, cylinder, base, w) ?: return true
    val h = ((top - base) dot w) / (w dot w)
    val lambda = ((point - base) dot w) / (w dot w)
    return lambda >= min(0f, h) - 1e-3f && lambda <= max(0f, h) + 1e-3f
}

/**
 * Kulová × kulová plocha → kružnice (v radikálové rovině kolmé na spojnici středů).
 * Vnější/vnitřní dotyk → bod; koule mimo sebe nebo jedna uvnitř druhé → prázdný průnik.
 */
fun intersectSphereSphere(a: SphereSurface3D, b: SphereSurface3D, state: MongeState) {
    val ca = state.sharedPoints3D.firstOrNull { it.id == a.centerPoint3DId }
    val cb = state.sharedPoints3D.firstOrNull { it.id == b.centerPoint3DId }
    if (ca == null || cb == null) { notifyEmptyIntersection(state); return }

    val c1 = Offset3D(ca.x, ca.y, ca.z)
    val c2 = Offset3D(cb.x, cb.y, cb.z)
    val r1 = a.radius; val r2 = b.radius
    val axis = c2 - c1
    val dd = axis.length()
    if (dd < 1e-9f) { notifyEmptyIntersection(state); return }     // soustředné koule

    val nUnit = axis * (1f / dd)
    val aDist = (dd * dd + r1 * r1 - r2 * r2) / (2f * dd)          // poloha roviny od C1 podél osy
    val center = c1 + nUnit * aDist
    val sumR = r1 + r2
    val diffR = abs(r1 - r2)
    val tol = 1e-3f * maxOf(r1, r2)

    when {
        dd > sumR + tol -> notifyEmptyIntersection(state)                       // koule mimo sebe
        diffR > tol && dd < diffR - tol -> notifyEmptyIntersection(state)       // jedna uvnitř druhé
        dd >= sumR - tol || (diffR > tol && dd <= diffR + tol) ->               // dotyk → bod
            addIntersectionPoint3D(state, center.x, center.y, center.z)
        else -> {                                                              // protínají se → kružnice
            val rho = sqrt((r1 * r1 - aDist * aDist).coerceAtLeast(0f))
            addCircleInPlane(state, center, nUnit, rho)
        }
    }
}

// ===================== Rotační plochy (SoR) =====================

/**
 * Jednotná geometrie rotační plochy nezávislá na průmětně, ve které vznikla.
 * Meridián je převeden do souřadnic (r, h): r = podepsaný poloměr (x meridiánu minus
 * poloha osy), h = výška podél osy. Boční plocha je stoh komolých kuželů – mezi
 * sousedními body meridiánu je poloměr lineární ve výšce; vodorovné segmenty
 * meridiánu tvoří rovinná mezikruží (víka).
 */
private class SorGeometry(
    val center: Offset3D,        // bod na ose v hladině h = 0
    val u: Offset3D,             // radiální báze (jednotková)
    val v: Offset3D,
    val w: Offset3D,             // jednotkový směr osy
    val profile: List<Offset>,   // meridián: x = podepsaný poloměr, y = výška podél osy
) {
    val closedProfile: Boolean =
        profile.size >= 3 && (profile.first() - profile.last()).getDistance() <= 1e-4f
    val scale: Float =
        profile.maxOf { max(abs(it.x), abs(it.y)) }.coerceAtLeast(1f)

    /** kumulativní délky meridiánu – pro rovnoměrné (délkové) převzorkování rovnoběžek */
    val cumulative: FloatArray = FloatArray(profile.size).also { acc ->
        for (i in 1 until profile.size) {
            acc[i] = acc[i - 1] + (profile[i] - profile[i - 1]).getDistance()
        }
    }
    val totalLength: Float get() = cumulative.last().coerceAtLeast(1e-6f)
}

/** Rovnoběžková kružnice: podepsaný poloměr a výška podél osy. */
private data class SorParallel(val r: Float, val h: Float)

/**
 * Sestaví jednotnou geometrii z operandu. Nárysová varianta má osu svisle (kružnice
 * v rovinách z = konst), půdorysová podél y (kružnice v rovinách y = konst) – stejné
 * konvence jako vykreslování výplní a OpenGL mesh.
 */
private fun sorGeometry(state: MongeState, sor: SolidOfRevolutionOp): SorGeometry? =
    sorGeometryFromParts(state, sor.narys, sor.pudorys)

/**
 * Strop počtu bodů meridiánu pro výpočty průniků. Vzorkované meridiány mají až tisíce
 * bodů (vzorkování ~0,5 jednotky); pro průniky stačí hrubší polyline – chordová chyba
 * je zanedbatelná a počty kvadratik/skenů klesnou o řád.
 */
private const val SOR_PROFILE_MAX_POINTS = 512

private fun subsampleProfile(prof: List<Offset>, maxPoints: Int): List<Offset> {
    if (prof.size <= maxPoints) return prof
    val step = (prof.size - 1).toFloat() / (maxPoints - 1)
    return List(maxPoints) { i -> prof[(i * step).toInt().coerceAtMost(prof.lastIndex)] }
}

private fun sorGeometryFromParts(
    state: MongeState,
    narys: SolidOfRevolutionNarys?,
    pudorys: SolidOfRevolutionPudorys?,
    maxProfilePoints: Int = SOR_PROFILE_MAX_POINTS,
): SorGeometry? {
    narys?.let { s ->
        val axis = state.lines3D.firstOrNull { it.id == s.axisLine3DId } ?: return null
        val ax = axis.start.x; val ay = axis.start.y
        val prof = subsampleProfile(s.sampledMeridianPolylineXZ, maxProfilePoints).map { Offset(it.x - ax, it.y) }
        if (prof.size < 2) return null
        return SorGeometry(
            center = Offset3D(ax, ay, 0f),
            u = Offset3D(1f, 0f, 0f), v = Offset3D(0f, 1f, 0f), w = Offset3D(0f, 0f, 1f),
            profile = prof,
        )
    }
    pudorys?.let { s ->
        val axis = state.lines3D.firstOrNull { it.id == s.axisLine3DId } ?: return null
        val ax = axis.start.x; val az = axis.start.z
        val prof = subsampleProfile(s.sampledMeridianPolylineXY, maxProfilePoints).map { Offset(it.x - ax, it.y) }
        if (prof.size < 2) return null
        return SorGeometry(
            center = Offset3D(ax, 0f, az),
            u = Offset3D(1f, 0f, 0f), v = Offset3D(0f, 0f, 1f), w = Offset3D(0f, 1f, 0f),
            profile = prof,
        )
    }
    return null
}

/** Lokální souřadnice bodu vůči ose: (radiální u, radiální v, výška h). */
private fun SorGeometry.localOf(p: Offset3D): Triple<Float, Float, Float> {
    val q = p - center
    return Triple(q dot u, q dot v, q dot w)
}

/** Rovnoběžka v délkovém parametru s ∈ [0, 1] podél meridiánu (lineární interpolace). */
private fun SorGeometry.parallelAt(s: Float): SorParallel {
    val target = s.coerceIn(0f, 1f) * totalLength
    var lo = 0; var hi = profile.lastIndex
    while (lo + 1 < hi) {
        val mid = (lo + hi) / 2
        if (cumulative[mid] <= target) lo = mid else hi = mid
    }
    val segLen = (cumulative[hi] - cumulative[lo]).coerceAtLeast(1e-9f)
    val t = ((target - cumulative[lo]) / segLen).coerceIn(0f, 1f)
    val a = profile[lo]; val b = profile[hi]
    return SorParallel(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

private fun SorGeometry.circlePointAt(par: SorParallel, theta: Float): Offset3D {
    val rho = abs(par.r)
    return center + u * (rho * cos(theta)) + v * (rho * sin(theta)) + w * par.h
}

/**
 * Průsečíky přímky p + t·d s rotační plochou. Segmenty meridiánu s nenulovým stoupáním
 * dávají kvadratiku komolého kužele (r lineární ve výšce, rovnice ρ² = r(h)² platí pro
 * obě znaménka poloměru); vodorovné segmenty jsou mezikruží ve své rovině.
 */
private fun lineSoRIntersections(p: Offset3D, d: Offset3D, geo: SorGeometry): List<LineHit> {
    val (a0, b0, h0) = geo.localOf(p)
    val a1 = d dot geo.u; val b1 = d dot geo.v; val h1 = d dot geo.w
    val eps = 1e-4f * geo.scale
    val hits = mutableListOf<LineHit>()

    for (i in 0 until geo.profile.lastIndex) {
        val s0 = geo.profile[i]; val s1 = geo.profile[i + 1]
        val dh = s1.y - s0.y
        if (abs(dh) <= eps) {
            // vodorovný segment → mezikruží v rovině h = s0.y
            if (abs(h1) < 1e-9f) continue
            val t = (s0.y - h0) / h1
            val ra = a0 + a1 * t; val rb = b0 + b1 * t
            val rho = sqrt(ra * ra + rb * rb)
            val rLo = min(abs(s0.x), abs(s1.x)) - eps
            val rHi = max(abs(s0.x), abs(s1.x)) + eps
            if (rho in rLo..rHi) hits += LineHit(t, p + d * t)
            continue
        }
        val beta = (s1.x - s0.x) / dh
        val c0 = s0.x + beta * (h0 - s0.y)
        val c1 = beta * h1
        val qa = a1 * a1 + b1 * b1 - c1 * c1
        val qb = 2f * (a0 * a1 + b0 * b1 - c0 * c1)
        val qc = a0 * a0 + b0 * b0 - c0 * c0
        val hLo = min(s0.y, s1.y) - eps
        val hHi = max(s0.y, s1.y) + eps
        for (t in solveQuadratic(qa, qb, qc)) {
            val h = h0 + h1 * t
            if (h in hLo..hHi) hits += LineHit(t, p + d * t)
        }
    }
    return hits.sortedBy { it.t }.distinctByNear { it.point }
}

/**
 * Největší t průsečíku paprsku p + t·d s rotační plochou, nebo null když ji míjí.
 * Stejná matematika jako [lineSoRIntersections], ale bez alokací – occlusion tohle
 * volá pro každý bod křivky i klasifikační mřížky, takže na tom stojí plynulost.
 */
private fun sorFrontHitT(p: Offset3D, d: Offset3D, geo: SorGeometry): Float? {
    val q = p - geo.center
    val a0 = q dot geo.u; val b0 = q dot geo.v; val h0 = q dot geo.w
    val a1 = d dot geo.u; val b1 = d dot geo.v; val h1 = d dot geo.w
    val eps = 1e-4f * geo.scale
    var best = Float.NEGATIVE_INFINITY

    fun consider(t: Float, hLo: Float, hHi: Float) {
        if (!t.isFinite() || t <= best) return
        val h = h0 + h1 * t
        if (h >= hLo && h <= hHi) best = t
    }

    for (i in 0 until geo.profile.lastIndex) {
        val s0 = geo.profile[i]; val s1 = geo.profile[i + 1]
        val dh = s1.y - s0.y
        if (abs(dh) <= eps) {
            if (abs(h1) < 1e-9f) continue
            val t = (s0.y - h0) / h1
            if (t <= best) continue
            val ra = a0 + a1 * t; val rb = b0 + b1 * t
            val rho = sqrt(ra * ra + rb * rb)
            if (rho >= min(abs(s0.x), abs(s1.x)) - eps && rho <= max(abs(s0.x), abs(s1.x)) + eps) best = t
            continue
        }
        val beta = (s1.x - s0.x) / dh
        val c0 = s0.x + beta * (h0 - s0.y)
        val c1 = beta * h1
        val qa = a1 * a1 + b1 * b1 - c1 * c1
        val qb = 2f * (a0 * a1 + b0 * b1 - c0 * c1)
        val qc = a0 * a0 + b0 * b0 - c0 * c0
        val hLo = min(s0.y, s1.y) - eps
        val hHi = max(s0.y, s1.y) + eps
        val scale = (abs(qa) + abs(qb) + abs(qc)).coerceAtLeast(1e-12f)
        if (abs(qa) < 1e-9f * scale) {
            if (abs(qb) >= 1e-9f * scale) consider(-qc / qb, hLo, hHi)
        } else {
            val disc = qb * qb - 4f * qa * qc
            if (disc >= 0f) {
                val sq = sqrt(disc)
                consider((-qb - sq) / (2f * qa), hLo, hHi)
                consider((-qb + sq) / (2f * qa), hLo, hHi)
            }
        }
    }
    return if (best.isFinite()) best else null
}

private fun List<Offset3D>.distinctPointsNear(eps: Float = 1e-3f): List<Offset3D> {
    val out = mutableListOf<Offset3D>()
    for (p in this) if (out.none { (it - p).length() <= eps }) out += p
    return out
}

private const val SOR_FAMILY_SAMPLE_COUNT = 640
private const val SOR_SOR_THETA_SCAN = 192

/**
 * Navzorkuje rodinu rovnoběžkových kružnic podél meridiánu a pro každou spočte
 * průsečíky s druhou plochou. Na přechodech prázdné↔neprázdné bisekuje délkový
 * parametr, aby konce větví dokonvergovaly k dotykové rovnoběžce (obdoba
 * [refinedTransitionHits] u površkových rodin). U uzavřeného meridiánu (anuloid)
 * řeší i přechod přes šev.
 */
private fun buildSorFamilyHitSets(
    geo: SorGeometry,
    circleHits: (SorParallel) -> List<Offset3D>,
): List<SampleHitSet> {
    val n = SOR_FAMILY_SAMPLE_COUNT
    val params = if (geo.closedProfile) List(n) { it.toFloat() / n } else List(n) { it.toFloat() / (n - 1) }
    val base = params.map { s -> s to circleHits(geo.parallelAt(s)).distinctPointsNear() }

    val out = mutableListOf<SampleHitSet>()
    fun add(hits: List<Offset3D>) { out += SampleHitSet(out.size, hits) }

    for (i in base.indices) {
        val (s, hits) = base[i]
        add(hits)
        val next = base.getOrNull(i + 1) ?: continue
        if (hits.size != next.second.size) {
            val (sIn, sOut) = if (hits.size > next.second.size) s to next.first else next.first to s
            refinedSorFamilyHits(geo, circleHits, sIn, sOut)?.let(::add)
        }
    }
    if (geo.closedProfile) {
        val firstHits = base.first().second
        val lastHits = base.last().second
        if (lastHits.size != firstHits.size) {
            // šev uzavřeného meridiánu: parallelAt(1) ≡ parallelAt(0)
            val (sIn, sOut) = if (lastHits.size > firstHits.size) base.last().first to 1f else 1f to base.last().first
            refinedSorFamilyHits(geo, circleHits, sIn, sOut)?.let(::add)
        }
        if (firstHits.isNotEmpty()) add(firstHits)
    }
    return out
}

/**
 * Bisekce délkového parametru mezi rovnoběžkami s různým POČTEM průsečíků – „uvnitř" je
 * strana s více zásahy, konverguje k dotykové rovnoběžce (stejný princip jako
 * [refinedTransitionHits] u površkových rodin).
 */
private fun refinedSorFamilyHits(
    geo: SorGeometry,
    circleHits: (SorParallel) -> List<Offset3D>,
    sInside: Float,
    sOutside: Float,
): List<Offset3D>? {
    var inside = sInside
    var outside = sOutside
    var best = circleHits(geo.parallelAt(inside)).distinctPointsNear()
    if (best.isEmpty()) return null
    val insideCount = best.size
    repeat(24) {
        val mid = (inside + outside) * 0.5f
        val hits = circleHits(geo.parallelAt(mid)).distinctPointsNear()
        if (hits.size >= insideCount) { inside = mid; best = hits } else outside = mid
    }
    return best
}

/**
 * Bodově vzorkovaná průniková křivka: rodina rovnoběžek rotační plochy × druhá plocha.
 * Trasování větví, spojování a uzavírání je stejné jako u površkových rodin
 * ([addSampledSurfaceIntersectionCurve]).
 */
private fun addSorFamilyIntersectionCurves(
    state: MongeState,
    geo: SorGeometry,
    circleHits: (SorParallel) -> List<Offset3D>,
    boundaryIntersections: List<BoundaryContinuation> = emptyList(),
) {
    val boundarySections = collectBoundaryIntersectionCurves(boundaryIntersections)
    val hitSets = buildSorFamilyHitSets(geo, circleHits)
    val runs = mergeSampledRunsAtSharedEndpoints(traceSampledRunsByContinuation(hitSets))

    var added = false
    for (run in runs) {
        if (run.points.size < 3) continue
        val naturalClosed = run.closedHint ||
            (run.sampleHitCount >= SOR_FAMILY_SAMPLE_COUNT - 2 && isClosedBranch(run.points))
        val points = if (naturalClosed) run.points else snapOpenRunEndsToBoundary(run.points, boundarySections)
        addIntersectionCurve3D(state, points, closed = naturalClosed)
        added = true
    }
    if (addBoundaryIntersectionCurves(state, boundarySections)) added = true
    if (!added) notifyEmptyIntersection(state)
}

/** Rovnoběžka × rovina (obecná, nekolmá k ose) → 0–2 body: cos(θ−φ) = c / (ρ·|n_uv|). */
private fun sorCirclePlaneHits(
    geo: SorGeometry,
    par: SorParallel,
    planePoint: Offset3D,
    nUnit: Offset3D,
): List<Offset3D> {
    val cc = geo.center + geo.w * par.h
    val rho = abs(par.r)
    val tol = 1e-4f * geo.scale
    if (rho <= tol) {
        // rovnoběžka degenerovaná do bodu na ose
        return if (abs((planePoint - cc) dot nUnit) <= tol) listOf(cc) else emptyList()
    }
    val cu = nUnit dot geo.u
    val cv = nUnit dot geo.v
    val amp = rho * sqrt(cu * cu + cv * cv)
    if (amp < 1e-9f) return emptyList()      // kružnice rovnoběžná s rovinou
    val rhs = nUnit dot (planePoint - cc)
    val ratio = rhs / amp
    if (abs(ratio) > 1f + 1e-4f) return emptyList()
    val phi = atan2(cv, cu)
    val delta = acos(ratio.coerceIn(-1f, 1f))
    return if (delta < 1e-4f || PI.toFloat() - delta < 1e-4f) {
        listOf(geo.circlePointAt(par, phi + delta))
    } else {
        listOf(geo.circlePointAt(par, phi + delta), geo.circlePointAt(par, phi - delta))
    }
}

/** Rovnoběžka × kulová plocha → 0–2 body (koule ∩ rovina kružnice → kružnice; pak kružnice × kružnice). */
private fun sorCircleSphereHits(
    geo: SorGeometry,
    par: SorParallel,
    sphereCenter: Offset3D,
    radius: Float,
): List<Offset3D> {
    val cc = geo.center + geo.w * par.h
    val rho = abs(par.r)
    val tol = 1e-4f * max(geo.scale, radius)
    if (rho <= tol) {
        return if (abs((cc - sphereCenter).length() - radius) <= tol) listOf(cc) else emptyList()
    }
    val dz = (sphereCenter - cc) dot geo.w
    val r2 = radius * radius - dz * dz
    if (r2 < 0f) return emptyList()
    val rPrime = sqrt(r2)
    val proj = sphereCenter - geo.w * dz     // střed koule promítnutý do roviny kružnice
    val e = proj - cc
    val dd = e.length()
    if (dd < 1e-6f) return emptyList()       // soustředné → řeší koaxiální větev
    if (dd > rho + rPrime + tol || dd < abs(rho - rPrime) - tol) return emptyList()
    val a = (dd * dd + rho * rho - r2) / (2f * dd)
    val h2 = rho * rho - a * a
    val eUnit = e * (1f / dd)
    val perp = geo.w cross eUnit             // v rovině kružnice, kolmo na spojnici středů
    val base = cc + eUnit * a
    if (h2 <= tol * tol) return listOf(base)
    val hh = sqrt(h2.coerceAtLeast(0f))
    return listOf(base + perp * hh, base - perp * hh)
}

/**
 * Rovnoběžka plochy A × rotační plocha B (obecná vzájemná poloha os). Lokální souřadnice
 * bodu kružnice v soustavě B jsou afinní v (cos θ, sin θ); kořeny F_B(θ) = 0 se hledají
 * hrubým skenem + bisekcí (bodově – tečné dvojkořeny mohou uniknout). Vodorovné segmenty
 * B (víka) se řeší analyticky jako kružnice × rovina s mezikružním filtrem.
 */
private fun sorCircleSorHits(
    geoA: SorGeometry,
    par: SorParallel,
    geoB: SorGeometry,
): List<Offset3D> {
    val cc = geoA.center + geoA.w * par.h
    val rho = abs(par.r)
    val eps = 1e-4f * max(geoA.scale, geoB.scale)
    if (rho <= eps) return emptyList()

    val q0 = cc - geoB.center
    val a0 = q0 dot geoB.u; val aC = (geoA.u dot geoB.u) * rho; val aS = (geoA.v dot geoB.u) * rho
    val b0 = q0 dot geoB.v; val bC = (geoA.u dot geoB.v) * rho; val bS = (geoA.v dot geoB.v) * rho
    val h0 = q0 dot geoB.w; val hC = (geoA.u dot geoB.w) * rho; val hS = (geoA.v dot geoB.w) * rho
    val hAmp = sqrt(hC * hC + hS * hS)

    val hits = mutableListOf<Offset3D>()
    for (i in 0 until geoB.profile.lastIndex) {
        val s0 = geoB.profile[i]; val s1 = geoB.profile[i + 1]
        val dh = s1.y - s0.y
        val hLo = min(s0.y, s1.y); val hHi = max(s0.y, s1.y)
        if (h0 + hAmp < hLo - eps || h0 - hAmp > hHi + eps) continue    // výškový ořez

        if (abs(dh) <= eps) {
            // víko B (mezikruží): kružnice A × rovina víka + filtr poloměru
            if (hAmp < 1e-9f) continue                                  // kružnice v rovině víka – degenerace
            val ratio = (s0.y - h0) / hAmp
            if (abs(ratio) > 1f + 1e-4f) continue
            val phi = atan2(hS, hC)
            val delta = acos(ratio.coerceIn(-1f, 1f))
            for (theta in listOf(phi + delta, phi - delta)) {
                val c = cos(theta); val s = sin(theta)
                val ra = a0 + aC * c + aS * s
                val rb = b0 + bC * c + bS * s
                val rr = sqrt(ra * ra + rb * rb)
                if (rr in (min(abs(s0.x), abs(s1.x)) - eps)..(max(abs(s0.x), abs(s1.x)) + eps)) {
                    hits += geoA.circlePointAt(par, theta)
                }
            }
            continue
        }

        val beta = (s1.x - s0.x) / dh
        fun g(theta: Float): Float {
            val c = cos(theta); val s = sin(theta)
            val ra = a0 + aC * c + aS * s
            val rb = b0 + bC * c + bS * s
            val hh = h0 + hC * c + hS * s
            val rr = s0.x + beta * (hh - s0.y)
            return ra * ra + rb * rb - rr * rr
        }
        fun hOf(theta: Float): Float = h0 + hC * cos(theta) + hS * sin(theta)

        val m = SOR_SOR_THETA_SCAN
        var prevTheta = 0f
        var prevG = g(0f)
        for (k in 1..m) {
            val theta = 2f * PI.toFloat() * k / m
            val gv = g(theta)
            if (prevG * gv < 0f) {
                var lo = prevTheta; var hi = theta; var gLo = prevG
                repeat(28) {
                    val mid = (lo + hi) * 0.5f
                    val gm = g(mid)
                    if (gLo * gm <= 0f) hi = mid else { lo = mid; gLo = gm }
                }
                val root = (lo + hi) * 0.5f
                if (hOf(root) in (hLo - eps)..(hHi + eps)) hits += geoA.circlePointAt(par, root)
            }
            prevTheta = theta
            prevG = gv
        }
    }
    return hits
}

/** Poloměry rovnoběžkových kružnic ve výšce h (průsečíky meridiánu s vodorovnou hladinou). */
private fun sorParallelCirclesAtHeight(geo: SorGeometry, h: Float): List<Float> {
    val eps = 1e-4f * geo.scale
    val radii = mutableListOf<Float>()
    fun add(r: Float) {
        val rr = abs(r)
        if (radii.none { abs(it - rr) <= 10f * eps }) radii += rr
    }
    for (i in 0 until geo.profile.lastIndex) {
        val s0 = geo.profile[i]; val s1 = geo.profile[i + 1]
        val dh = s1.y - s0.y
        if (abs(dh) <= eps) {
            if (abs(s0.y - h) <= eps) { add(s0.x); add(s1.x) }   // rovina víka → jeho okraje
            continue
        }
        val t = (h - s0.y) / dh
        if (t < -1e-4f || t > 1f + 1e-4f) continue
        add(s0.x + (s1.x - s0.x) * t)
    }
    return radii
}

/** Plný řez rotační plochy rovinou jako navzorkované větve (pro boundary continuations). */
private fun sorPlaneSectionRuns(
    geo: SorGeometry,
    planePoint: Offset3D,
    nUnit: Offset3D,
): List<SampledRun> {
    if ((nUnit cross geo.w).length() < 1e-4f) {
        // rovina kolmá na osu → celé rovnoběžkové kružnice
        val h = (planePoint - geo.center) dot geo.w
        return sorParallelCirclesAtHeight(geo, h)
            .filter { it > 1e-4f * geo.scale }
            .map { rho ->
                val pts = List(BOUNDARY_SECTION_SAMPLE_COUNT) { i ->
                    val t = 2f * PI.toFloat() * i / BOUNDARY_SECTION_SAMPLE_COUNT
                    geo.center + geo.u * (rho * cos(t)) + geo.v * (rho * sin(t)) + geo.w * h
                }
                SampledRun(pts, BOUNDARY_SECTION_SAMPLE_COUNT, closedHint = true)
            }
    }
    val hitSets = buildSorFamilyHitSets(geo) { par -> sorCirclePlaneHits(geo, par, planePoint, nUnit) }
    return mergeSampledRunsAtSharedEndpoints(traceSampledRunsByContinuation(hitSets))
}

private fun splitSampledRunByInside(run: SampledRun, inside: (Offset3D) -> Boolean): List<SectionCurve> {
    val pts = run.points
    if (pts.size < 3) return emptyList()
    val closed = run.closedHint || isClosedBranch(pts)
    if (pts.all(inside)) return listOf(SectionCurve(pts, closed))
    if (!closed) return splitInsideRuns(pts, inside)
    val start = pts.indexOfFirst { !inside(it) }
    if (start < 0) return listOf(SectionCurve(pts, true))
    val rotated = pts.subList(start, pts.size) + pts.subList(0, start)
    return splitInsideRuns(rotated, inside)
}

private fun samplePlaneSoRSectionRuns(
    geo: SorGeometry,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<SectionCurve> {
    val n = planeNormal.normalizeOrNull() ?: return emptyList()
    return sorPlaneSectionRuns(geo, planePoint, n).flatMap { splitSampledRunByInside(it, insideFace) }
}

/** Úsek řezu rotační plochy rovinou mezi dvěma body na hranici stěny druhého tělesa. */
private fun samplePlaneSoRSectionBetween(
    geo: SorGeometry,
    planePoint: Offset3D,
    planeNormal: Offset3D,
    from: Offset3D,
    to: Offset3D,
    insideFace: (Offset3D) -> Boolean,
): List<Offset3D>? {
    val n = planeNormal.normalizeOrNull() ?: return null
    val candidates = mutableListOf<List<Offset3D>>()
    for (run in sorPlaneSectionRuns(geo, planePoint, n)) {
        val pts = run.points
        if (pts.size < 2) continue
        val iFrom = pts.indices.minByOrNull { (pts[it] - from).length() } ?: continue
        val iTo = pts.indices.minByOrNull { (pts[it] - to).length() } ?: continue
        val snapTol = 6f * averageStep(pts)
        if ((pts[iFrom] - from).length() > snapTol || (pts[iTo] - to).length() > snapTol) continue
        if (iFrom == iTo) continue
        val closed = run.closedHint || isClosedBranch(pts)
        if (closed) {
            fun cyclic(iA: Int, iB: Int): List<Offset3D> {
                val out = mutableListOf<Offset3D>()
                var i = iA
                while (true) {
                    out += pts[i]
                    if (i == iB) break
                    i = (i + 1) % pts.size
                }
                return out
            }
            candidates += cyclic(iFrom, iTo)
            candidates += cyclic(iTo, iFrom).asReversed()
        } else {
            candidates += if (iFrom <= iTo) pts.subList(iFrom, iTo + 1)
            else pts.subList(iTo, iFrom + 1).asReversed()
        }
    }
    val best = chooseBoundarySection(candidates, insideFace) ?: return null
    return listOf(from) + best + to             // ukotvení přesně na koncové body
}

/**
 * Přímka × rotační plocha → body (boční plocha po segmentech meridiánu + víka).
 */
fun intersectLineSolidOfRevolution(line: Line3D, sor: SolidOfRevolutionOp, state: MongeState) {
    val geo = sorGeometry(state, sor) ?: run { notifyEmptyIntersection(state); return }
    val p = Offset3D(line.start.x, line.start.y, line.start.z)
    val d = line.direction.normalize()
    val hits = lineSoRIntersections(p, d, geo)
    if (hits.isEmpty()) { notifyEmptyIntersection(state); return }
    addLineIntersectionResults(state, line, hits.map { it.point }, emptyList())
}

/**
 * Rovina × rotační plocha. Rovina kolmá na osu → přesné rovnoběžkové kružnice;
 * jinak bodově vzorkovaná křivka přes rodinu rovnoběžek (2 body na kružnici).
 */
fun intersectPlaneSolidOfRevolution(plane: Plane3D, sor: SolidOfRevolutionOp, state: MongeState) {
    val geo = sorGeometry(state, sor) ?: run { notifyEmptyIntersection(state); return }
    val eq = plane.equation ?: run { notifyEmptyIntersection(state); return }
    val normal = Offset3D(eq.a, eq.b, eq.c)
    val len2 = normal dot normal
    if (len2 < 1e-12f) { notifyEmptyIntersection(state); return }
    val n = normal * (1f / sqrt(len2))
    val planePoint = normal * (-eq.d / len2)

    if ((n cross geo.w).length() < 1e-4f) {
        val h = (planePoint - geo.center) dot geo.w
        val c = geo.center + geo.w * h
        var added = false
        for (r in sorParallelCirclesAtHeight(geo, h)) {
            if (r <= 1e-4f * geo.scale) addIntersectionPoint3D(state, c.x, c.y, c.z)
            else addCircleInPlane(state, c, geo.w, r)
            added = true
        }
        if (!added) notifyEmptyIntersection(state)
        return
    }
    addSorFamilyIntersectionCurves(state, geo, { par -> sorCirclePlaneHits(geo, par, planePoint, n) })
}

/**
 * Kuželová plocha × rotační plocha: površky kužele × boční plocha SoR (bodově),
 * podstava kužele pokračuje řezem rotační plochy v rovině podstavy.
 */
fun intersectConeSolidOfRevolution(cone: ConicalSurface3D, sor: SolidOfRevolutionOp, state: MongeState) {
    val geo = sorGeometry(state, sor) ?: run { notifyEmptyIntersection(state); return }
    addSampledSurfaceIntersectionCurve(
        state = state,
        first = sampledConeGenerators(state, cone) ?: run { notifyEmptyIntersection(state); return },
        firstAt = coneGeneratorRefiner(state, cone),
        second = { p, d -> lineSoRIntersections(p, d, geo) },
        boundaryIntersections =
            coneBaseBoundaryContinuation(state, cone) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneSoRSectionBetween(geo, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneSoRSectionRuns(geo, it.planePoint, it.normalUnit, it.insideFace)
            }.asList(),
    )
}

/**
 * Válcová plocha × rotační plocha: površky válce × boční plocha SoR (bodově),
 * podstavy válce pokračují řezem rotační plochy v rovinách podstav.
 */
fun intersectCylinderSolidOfRevolution(cylinder: CylindricalSurface3D, sor: SolidOfRevolutionOp, state: MongeState) {
    val geo = sorGeometry(state, sor) ?: run { notifyEmptyIntersection(state); return }
    addSampledSurfaceIntersectionCurve(
        state = state,
        first = sampledCylinderGenerators(state, cylinder) ?: run { notifyEmptyIntersection(state); return },
        firstAt = cylinderGeneratorRefiner(state, cylinder),
        second = { p, d -> lineSoRIntersections(p, d, geo) },
        boundaryIntersections =
            cylinderBoundaryContinuations(state, cylinder) { planePoint, planeNormal, from, to, insideFace ->
                samplePlaneSoRSectionBetween(geo, planePoint, planeNormal, from, to, insideFace)
            }.withFullSections {
                samplePlaneSoRSectionRuns(geo, it.planePoint, it.normalUnit, it.insideFace)
            },
    )
}

/**
 * Kulová plocha × rotační plocha. Koule se středem na ose → přesné rovnoběžkové
 * kružnice z meridiánu; jinak bodově přes rodinu rovnoběžek (kružnice × koule analyticky).
 */
fun intersectSphereSolidOfRevolution(sphere: SphereSurface3D, sor: SolidOfRevolutionOp, state: MongeState) {
    val geo = sorGeometry(state, sor) ?: run { notifyEmptyIntersection(state); return }
    val centerP = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId }
        ?: run { notifyEmptyIntersection(state); return }
    val s3 = Offset3D(centerP.x, centerP.y, centerP.z)
    val q = s3 - geo.center
    val radial = q - geo.w * (q dot geo.w)

    if (radial.length() <= 1e-3f * max(geo.scale, sphere.radius)) {
        intersectCoaxialSphereSoR(geo, q dot geo.w, sphere.radius, state)
        return
    }
    addSorFamilyIntersectionCurves(state, geo, { par -> sorCircleSphereHits(geo, par, s3, sphere.radius) })
}

/** Koaxiální koule × SoR: kořeny r(h)² + (h − h_S)² = R² po segmentech meridiánu → kružnice. */
private fun intersectCoaxialSphereSoR(geo: SorGeometry, hS: Float, radius: Float, state: MongeState) {
    val eps = 1e-4f * max(geo.scale, radius)
    val circles = mutableListOf<Offset>()       // (|r|, h)
    fun addCircle(r: Float, h: Float) {
        val rr = abs(r)
        if (circles.none { abs(it.x - rr) <= 10f * eps && abs(it.y - h) <= 10f * eps }) circles += Offset(rr, h)
    }

    for (i in 0 until geo.profile.lastIndex) {
        val s0 = geo.profile[i]; val s1 = geo.profile[i + 1]
        val dh = s1.y - s0.y
        if (abs(dh) <= eps) {
            // víko: r² = R² − (h − h_S)², filtr mezikružím
            val r2 = radius * radius - (s0.y - hS) * (s0.y - hS)
            if (r2 < 0f) continue
            val rr = sqrt(r2)
            if (inSignedRangeSoR(rr, s0.x, s1.x, eps)) addCircle(rr, s0.y)
            continue
        }
        val beta = (s1.x - s0.x) / dh
        val alpha = s0.x - beta * s0.y
        val qa = beta * beta + 1f
        val qb = 2f * (alpha * beta - hS)
        val qc = alpha * alpha + hS * hS - radius * radius
        val hLo = min(s0.y, s1.y) - eps
        val hHi = max(s0.y, s1.y) + eps
        for (h in solveQuadratic(qa, qb, qc)) {
            if (h in hLo..hHi) addCircle(alpha + beta * h, h)
        }
    }

    var added = false
    for (c in circles) {
        val center = geo.center + geo.w * c.y
        if (c.x <= eps) addIntersectionPoint3D(state, center.x, center.y, center.z)
        else addCircleInPlane(state, center, geo.w, c.x)
        added = true
    }
    if (!added) notifyEmptyIntersection(state)
}

/** Leží poloměr r (bez znaménka) v podepsaném rozsahu segmentu meridiánu? */
private fun inSignedRangeSoR(r: Float, e0: Float, e1: Float, eps: Float): Boolean {
    val lo = min(e0, e1) - eps
    val hi = max(e0, e1) + eps
    return r in lo..hi || -r in lo..hi
}

/**
 * Rotační × rotační plocha. Souosé plochy → přesné rovnoběžkové kružnice z průsečíků
 * meridiánů; jinak bodově: rodina rovnoběžek A × boční plocha B (numerické kořeny po θ).
 */
fun intersectSolidOfRevolutionSolidOfRevolution(a: SolidOfRevolutionOp, b: SolidOfRevolutionOp, state: MongeState) {
    val geoA = sorGeometry(state, a) ?: run { notifyEmptyIntersection(state); return }
    val geoB = sorGeometry(state, b) ?: run { notifyEmptyIntersection(state); return }

    val centerOffset = geoB.center - geoA.center
    val radialOffset = centerOffset - geoA.w * (centerOffset dot geoA.w)
    val coaxial = (geoA.w cross geoB.w).length() < 1e-4f &&
        radialOffset.length() <= 1e-3f * max(geoA.scale, geoB.scale)
    if (coaxial) {
        intersectCoaxialSoRSoR(geoA, geoB, state)
        return
    }
    // Numerický sken θ běží pro každou rovnoběžku A × každý segment B → profil B
    // podvzorkuj agresivněji (chordová chyba zůstává zanedbatelná, cena klesne ~3×).
    val geoBCoarse = sorGeometryFromParts(state, b.narys, b.pudorys, maxProfilePoints = 192) ?: geoB
    addSorFamilyIntersectionCurves(state, geoA, { par -> sorCircleSorHits(geoA, par, geoBCoarse) })
}

/** Souosé rotační plochy: |r_A(h)| = |r_B(h)| po dvojicích segmentů meridiánů → kružnice. */
private fun intersectCoaxialSoRSoR(geoA: SorGeometry, geoB: SorGeometry, state: MongeState) {
    val sign = if ((geoA.w dot geoB.w) >= 0f) 1f else -1f
    val delta = (geoB.center - geoA.center) dot geoA.w
    val profB = geoB.profile.map { Offset(it.x, delta + sign * it.y) }   // profil B v soustavě A
    val eps = 1e-4f * max(geoA.scale, geoB.scale)

    val circles = mutableListOf<Offset>()       // (|r|, h)
    fun addCircle(r: Float, h: Float) {
        val rr = abs(r)
        if (circles.none { abs(it.x - rr) <= 10f * eps && abs(it.y - h) <= 10f * eps }) circles += Offset(rr, h)
    }
    fun rAt(s0: Offset, s1: Offset, h: Float): Float =
        s0.x + (s1.x - s0.x) * ((h - s0.y) / (s1.y - s0.y))

    for (i in 0 until geoA.profile.lastIndex) {
        val a0 = geoA.profile[i]; val a1 = geoA.profile[i + 1]
        val dhA = a1.y - a0.y
        for (j in 0 until profB.lastIndex) {
            val b0 = profB[j]; val b1 = profB[j + 1]
            val dhB = b1.y - b0.y
            val hLo = max(min(a0.y, a1.y), min(b0.y, b1.y)) - eps
            val hHi = min(max(a0.y, a1.y), max(b0.y, b1.y)) + eps
            if (hLo > hHi) continue

            when {
                abs(dhA) <= eps && abs(dhB) <= eps -> Unit   // dvě víka v téže rovině – degenerace
                abs(dhA) <= eps -> {
                    val r = rAt(b0, b1, a0.y)
                    if (inSignedRangeSoR(abs(r), a0.x, a1.x, eps)) addCircle(r, a0.y)
                }
                abs(dhB) <= eps -> {
                    val r = rAt(a0, a1, b0.y)
                    if (inSignedRangeSoR(abs(r), b0.x, b1.x, eps)) addCircle(r, b0.y)
                }
                else -> {
                    val betaA = (a1.x - a0.x) / dhA; val alphaA = a0.x - betaA * a0.y
                    val betaB = (b1.x - b0.x) / dhB; val alphaB = b0.x - betaB * b0.y
                    for (sgn in floatArrayOf(1f, -1f)) {
                        val da = betaA - sgn * betaB
                        val dc = alphaA - sgn * alphaB
                        if (abs(da) < 1e-9f) continue        // rovnoběžné meridiány (překryv) – bez izolované kružnice
                        val h = -dc / da
                        if (h in hLo..hHi) addCircle(alphaA + betaA * h, h)
                    }
                }
            }
        }
    }

    var added = false
    for (c in circles) {
        val center = geoA.center + geoA.w * c.y
        if (c.x <= eps) addIntersectionPoint3D(state, center.x, center.y, center.z)
        else addCircleInPlane(state, center, geoA.w, c.x)
        added = true
    }
    if (!added) notifyEmptyIntersection(state)
}

/**
 * Rotační plocha × Solid (hranol/jehlan): pro každou stěnu bodově vzorkovaný řez
 * rotační plochy rovinou stěny, oříznutý na polygon stěny (stejné schéma jako
 * ostatní průniky se Solidy – po stěnách).
 */
fun intersectSolidOfRevolutionSegmentSolid(sor: SolidOfRevolutionOp, solid: SegmentSolid3D, state: MongeState) {
    val geo = sorGeometry(state, sor) ?: run { notifyEmptyIntersection(state); return }
    val faces = segmentSolidFaces(state, solid)?.map { face -> face.map { it.toOffset3D() } } ?: emptyList()
    if (faces.isEmpty()) { notifyEmptyIntersection(state); return }

    // Sekce jednotlivých stěn se vzorkují nezávisle, ale na společných hranách stěn na
    // sebe navazují → posbírej je a sešij sdílenými konci do souvislých křivek.
    val pieces = mutableListOf<SampledRun>()
    for (face in faces) {
        val plane = facePlaneFromPolygon(face) ?: continue
        val face2D = face.map { plane.project(it) }
        val sections = samplePlaneSoRSectionRuns(geo, plane.origin, plane.normal) { p ->
            pointInPolygon2D(plane.project(p), face2D)
        }
        for (section in sections) {
            if (section.points.size < 2) continue
            pieces += SampledRun(section.points, section.points.size, closedHint = section.closed)
        }
    }

    var added = false
    for (run in mergeSampledRunsAtSharedEndpoints(pieces)) {
        if (run.points.size < 2) continue
        addIntersectionCurve3D(state, run.points, closed = run.closedHint)
        added = true
    }
    if (!added) notifyEmptyIntersection(state)
}

// ───────────────── fasáda pro occlusion výplní (draw.mongescreen.fills) ─────────────────

/**
 * Sonda rotační plochy pro hloubkový occlusion výplní: membership predikát (bod uvnitř
 * plného rotačního tělesa), analytická hloubka přední plochy podél paprsku a body pro
 * odhad rozsahu t. Konvence geometrie (osa, meridián) zůstávají zapouzdřené tady.
 */
internal class SorOcclusionProbe(
    val inside: (Offset3D) -> Boolean,
    val frontDepth: (base: Offset3D, dir: Offset3D) -> Float?,
    val characteristicPoints: List<Offset3D>,
)

internal fun sorOcclusionProbe(
    state: MongeState,
    narys: SolidOfRevolutionNarys?,
    pudorys: SolidOfRevolutionPudorys?,
): SorOcclusionProbe? {
    // Klasifikace „kdo je vpředu" srovnává hloubky, které se u tečných průniků liší jen
    // o jednotky – chordová chyba hrubého profilu by je utopila v šumu (pruhy ve výplních).
    // Plný strop 512 bodů; výpočet stejně běží na pozadí (async occlusion cache).
    val geo = sorGeometryFromParts(state, narys, pudorys, maxProfilePoints = SOR_PROFILE_MAX_POINTS) ?: return null

    // uvnitř tělesa = (ρ, h) uvnitř meridiánového polygonu (nebo jeho zrcadla přes osu);
    // pointInPolygon2D bere polygon jako uzavřený a hranici počítá dovnitř
    val inside: (Offset3D) -> Boolean = { p ->
        val q = p - geo.center
        val h = q dot geo.w
        val a = q dot geo.u
        val b = q dot geo.v
        val rho = sqrt(a * a + b * b)
        pointInPolygon2D(Offset(rho, h), geo.profile) || pointInPolygon2D(Offset(-rho, h), geo.profile)
    }

    val frontDepth: (Offset3D, Offset3D) -> Float? = { base, dir ->
        sorFrontHitT(base, dir, geo)
    }

    // rozsah t podél paprsku: vzorky rovnoběžkových prstenců (podvzorkovaný meridián × 8 směrů)
    val ringDirs = List(8) { k ->
        val t = 2f * PI.toFloat() * k / 8
        geo.u * cos(t) + geo.v * sin(t)
    }
    val step = (geo.profile.size / 48).coerceAtLeast(1)
    val characteristicPoints = buildList {
        var i = 0
        while (i < geo.profile.size) {
            val s = geo.profile[i]
            val ringCenter = geo.center + geo.w * s.y
            val rho = abs(s.x)
            for (rd in ringDirs) add(ringCenter + rd * rho)
            i += step
        }
    }

    return SorOcclusionProbe(inside, frontDepth, characteristicPoints)
}

// Strop počtu hranových rovnoběžek na těleso – každá stojí visibleRuns + řez překryvu.
private const val SOR_MAX_EDGE_RIMS = 24

// Roh meridiánu = zlom větší než ~25°; jemnější zlomy jsou chordy vzorkované hladké křivky.
private val SOR_RIM_CORNER_COS = cos(25f * PI.toFloat() / 180f)

/**
 * Hranové rovnoběžky rotační plochy ve 3D: kružnice otevřených konců meridiánu (hrany vík),
 * vnitřních ostrých rohů (límec/hrdlo, okraje mezikruží) a lokálních extrémů poloměru
 * (rovníky – podstatné při pohledu blízko osy). Přes jejich průmět skáče přední hloubka
 * plochy mezi stěnami (pohled do otvoru, přes límec…) – occlusion výplní jimi proto řeže
 * překryv siluet stejně jako viditelnou průnikovou křivkou. Rovnoběžky, které v daném
 * pohledu splynou se siluetou, zahodí sliver guard při řezání.
 */
internal fun sorOpenRimCircles3D(
    state: MongeState,
    narys: SolidOfRevolutionNarys?,
    pudorys: SolidOfRevolutionPudorys?,
): List<List<Offset3D>> {
    val geo = sorGeometryFromParts(state, narys, pudorys) ?: return emptyList()
    val capEps = 1e-3f * geo.scale
    val samples = 96
    val prof = geo.profile

    val rims = ArrayList<Offset>()               // (|r|, h)
    fun add(p: Offset) {
        if (rims.size >= SOR_MAX_EDGE_RIMS) return
        val r = abs(p.x)
        if (r < capEps) return                   // pól/hrot – hrana degeneruje do bodu
        if (rims.none { abs(it.x - r) <= capEps && abs(it.y - p.y) <= capEps }) rims += Offset(r, p.y)
    }

    if (!geo.closedProfile) {
        add(prof.first())
        add(prof.last())
    }

    var lastDrSign = 0
    var firstDrSign = 0
    for (i in 1 until prof.size) {
        val prev = prof[i - 1]
        val cur = prof[i]
        if (i < prof.lastIndex) {
            val next = prof[i + 1]
            val d1 = cur - prev
            val d2 = next - cur
            val l1 = d1.getDistance()
            val l2 = d2.getDistance()
            if (l1 > 1e-6f && l2 > 1e-6f &&
                (d1.x * d2.x + d1.y * d2.y) / (l1 * l2) < SOR_RIM_CORNER_COS
            ) {
                add(cur)
            }
        }
        // extrém poloměru = změna znaménka d|r| (ploché úseky se přemostí)
        val dr = abs(cur.x) - abs(prev.x)
        val sign = if (dr > 0.1f * capEps) 1 else if (dr < -0.1f * capEps) -1 else 0
        if (sign != 0) {
            if (lastDrSign != 0 && sign != lastDrSign) add(prev)
            if (firstDrSign == 0) firstDrSign = sign
            lastDrSign = sign
        }
    }
    // extrém ležící na švu uzavřeného meridiánu (rovník v bodě, kde kresba profilu začala)
    if (geo.closedProfile && firstDrSign != 0 && lastDrSign != 0 && firstDrSign != lastDrSign) {
        add(prof.first())
    }

    return rims.map { rim ->
        val c = geo.center + geo.w * rim.y
        List(samples) { i ->
            val t = 2f * PI.toFloat() * i / samples
            c + (geo.u * cos(t) + geo.v * sin(t)) * rim.x
        }
    }
}

/**
 * Skutečný (nekonvexní) obrys průmětu rotační plochy v daném pohledu – dvě siluetní křivky
 * (levá/pravá) + eliptická víka na koncích otevřeného meridiánu.
 *
 * Krajní body siluety NEleží obecně v konstantním azimutu. Pro rovnoběžné promítání je
 * silueta obálka `F(s,θ) = a(s)·cosθ + b(s)·sinθ + c(s) = 0` (TÁŽ rovnice jako axo-obrys
 * v `buildRevolutionContourGenerator`), kde `a = dH·det(A,V)`, `b = −dH·det(A,U)`,
 * `c = dR·det(U,V)` a A,U,V jsou promítnutá osa/radiální báze. U válce (dR=0) je c=0 a řešení
 * degeneruje na konstantní θ* (meridián); u kužele/hyperboloidu/vázy azimut siluety plyne
 * s výškou → obrys kopíruje SKUTEČNÝ axo-obrys, ne jen meridián. To je podstatné „zeshora",
 * kde se meridiánová aproximace a pravý obrys výrazně rozcházejí.
 *
 * Pohled s paprskem kolmým na osu (nárys/půdorys s osou v průmětně): kružnice se promítají
 * do úseček (det(U,V) ≈ 0) → průmět tělesa je pás {osa(h) ± ê·W(h)}, W(h) = největší poloměr
 * rovnoběžky ve výšce h ([sorEnvelopeSilhouette]). Polygon meridián+zrcadlo tady nestačí:
 * u profilu s více stěnami v téže výšce (límce, anuloid) by even-odd test hlásil uvnitř
 * průmětu falešné díry a occlusion by tam vůbec neřezal.
 *
 * Pro pohled podél osy (osa se promítne do bodu) vrací null → volající použije konvexní obal.
 */
internal fun sorProjectedSilhouette(
    state: MongeState,
    narys: SolidOfRevolutionNarys?,
    pudorys: SolidOfRevolutionPudorys?,
    project: (Offset3D) -> Offset,
): List<Offset>? {
    val geo = sorGeometryFromParts(state, narys, pudorys, maxProfilePoints = 256) ?: return null
    val o = project(geo.center)
    val projAxis = project(geo.center + geo.w) - o
    if (projAxis.getDistance() < 1e-4f) return null              // pohled podél osy → fallback

    val projU = project(geo.center + geo.u) - o                  // promítnutá radiální báze
    val projV = project(geo.center + geo.v) - o
    fun det(a: Offset, b: Offset): Float = a.x * b.y - a.y * b.x
    val detAV = det(projAxis, projV)
    val detAU = det(projAxis, projU)
    val detUV = det(projU, projV)

    val uvScale = projU.getDistance() + projV.getDistance()
    if (abs(detUV) <= 1e-4f * uvScale * uvScale) {
        return sorEnvelopeSilhouette(geo, o, projAxis, projU, projV)
    }

    // Konstantní θ* (silueta válce) = referenční „pravá" strana a fallback, když je obálka
    // lokálně nedefinovaná (R≈0 na vodorovném segmentu meridiánu).
    val n2 = Offset(-projAxis.y, projAxis.x)
    val thetaStar = atan2(projV.x * n2.x + projV.y * n2.y, projU.x * n2.x + projU.y * n2.y)

    val prof = geo.profile
    val lastIdx = prof.lastIndex
    val piF = PI.toFloat()
    val twoPi = 2f * piF

    fun angDist(a: Float, b: Float): Float {
        var d = (a - b) % twoPi
        if (d < -piF) d += twoPi
        if (d > piF) d -= twoPi
        return abs(d)
    }

    // Pro každý vzorek meridiánu spočti dvě siluetní θ (± větev obálky) a označ „pravou"
    // (blíž thetaStar) a „levou". Body větví se u hladké obálky nekříží.
    val rightTh = FloatArray(prof.size)
    val leftTh = FloatArray(prof.size)
    for (i in prof.indices) {
        val prev = if (i == 0) prof[0] else prof[i - 1]
        val next = if (i == lastIdx) prof[lastIdx] else prof[i + 1]
        val dH = next.y - prev.y
        val dR = abs(next.x) - abs(prev.x)
        val a = dH * detAV
        val b = -dH * detAU
        val c = dR * detUV
        val R = sqrt(a * a + b * b)
        val (s1, s2) = if (R < 1e-6f) {
            thetaStar to (thetaStar + piF)
        } else {
            val phi = atan2(b, a)
            val d = acos((-c / R).coerceIn(-1f, 1f))
            (phi + d) to (phi - d)
        }
        if (angDist(s1, thetaStar) <= angDist(s2, thetaStar)) { rightTh[i] = s1; leftTh[i] = s2 }
        else { rightTh[i] = s2; leftTh[i] = s1 }
    }

    fun silPoint(i: Int, theta: Float): Offset {
        val r = abs(prof[i].x)
        return project(geo.center + geo.w * prof[i].y + (geo.u * cos(theta) + geo.v * sin(theta)) * r)
    }
    val right = prof.indices.map { silPoint(it, rightTh[it]) }
    val left = prof.indices.map { silPoint(it, leftTh[it]) }

    // Uzavřený meridián (anuloid): right+left už tvoří uzavřenou smyčku, žádné víko.
    if (geo.closedProfile) return right + left.asReversed()

    // Otevřený meridián: konce jsou kruhová víka (rovnoběžky s r>0). Mezi siluetními konci
    // pravé a levé větve doplň VNĚJŠÍ oblouk elipsy víka (půlka vydouvající pryč od tělesa),
    // jinak by rovná tětiva vynechala eliptickou výduť víka. V nárysu/půdorysu se víko
    // promítne na ostro (degenerovaná elipsa) → guard vrátí rovnou tětivu.
    val capEps = 1e-3f * geo.scale
    fun rimArc(endIndex: Int, oppIndex: Int, thetaFrom: Float, thetaTo: Float): List<Offset> {
        val r = abs(prof[endIndex].x)
        if (r < capEps) return emptyList()                       // pól/hrot – víko degeneruje do bodu
        val capCenter = geo.center + geo.w * prof[endIndex].y
        val cProj = project(capCenter)
        val eU = project(capCenter + geo.u * r) - cProj
        val eV = project(capCenter + geo.v * r) - cProj
        if (abs(det(eU, eV)) < capEps * capEps) return emptyList() // víko na ostro (nárys/půdorys) → rovná tětiva
        val bodyDir = project(geo.center + geo.w * prof[oppIndex].y) - cProj
        var dShort = (thetaTo - thetaFrom) % twoPi
        if (dShort < -piF) dShort += twoPi
        if (dShort > piF) dShort -= twoPi
        val dAlt = if (dShort >= 0f) dShort - twoPi else dShort + twoPi
        fun outerScore(sweep: Float): Float {
            val a = thetaFrom + sweep * 0.5f
            val m = project(capCenter + (geo.u * cos(a) + geo.v * sin(a)) * r) - cProj
            return -(m.x * bodyDir.x + m.y * bodyDir.y)          // větší = víc ven (pryč od tělesa)
        }
        val sweep = if (outerScore(dShort) >= outerScore(dAlt)) dShort else dAlt
        val steps = 24
        return (1 until steps).map { k ->
            val a = thetaFrom + sweep * (k.toFloat() / steps)
            project(capCenter + (geo.u * cos(a) + geo.v * sin(a)) * r)
        }
    }

    val topArc = rimArc(lastIdx, 0, rightTh[lastIdx], leftTh[lastIdx])
    val bottomArc = rimArc(0, lastIdx, leftTh[0], rightTh[0])
    return right + topArc + left.asReversed() + bottomArc
}

/**
 * Silueta pro pohled, ve kterém rovnoběžkové kružnice degenerují do úseček (promítnuté
 * radiální báze jsou kolineární – Monge profilový pohled, obecně paprsek ⟂ osa).
 * Průmět plného rotačního tělesa je pak {osa(h) ± ê·W(h)}: pro každou výšku h rozhoduje
 * jen NEJVĚTŠÍ poloměr rovnoběžky, vnitřní stěny jsou schované. Skoky W(h) (konec límce,
 * víko mezikruží) dají svislou hranu = průmět hranové kružnice, přesně po obrysu.
 */
private fun sorEnvelopeSilhouette(
    geo: SorGeometry,
    o: Offset,
    projAxis: Offset,
    projU: Offset,
    projV: Offset,
): List<Offset>? {
    val prof = geo.profile
    val hEps = 1e-4f * geo.scale
    val lenU = projU.getDistance()
    val lenV = projV.getDistance()
    // amplituda průmětu jednotkové kružnice: kolineární projU/projV → ê·√(|U|²+|V|²)
    val amp = sqrt(lenU * lenU + lenV * lenV)
    if (amp < 1e-6f) return null                     // kružnice do bodů → pohled podél osy
    val dir = (if (lenU >= lenV) projU * (1f / lenU) else projV * (1f / lenV)) * amp

    fun rMaxAt(h: Float): Float {
        var best = 0f
        for (i in 0 until prof.lastIndex) {
            val s0 = prof[i]
            val s1 = prof[i + 1]
            if (h < min(s0.y, s1.y) - hEps || h > max(s0.y, s1.y) + hEps) continue
            val dh = s1.y - s0.y
            val r = if (abs(dh) <= hEps) {
                max(abs(s0.x), abs(s1.x))
            } else {
                val t = ((h - s0.y) / dh).coerceIn(0f, 1f)
                abs(s0.x + (s1.x - s0.x) * t)
            }
            if (r > best) best = r
        }
        return best
    }

    // výšky: vrcholy meridiánu + středy intervalů (křížení obálek segmentů mezi vrcholy)
    val distinct = ArrayList<Float>()
    for (h in prof.map { it.y }.sorted()) {
        if (distinct.isEmpty() || h - distinct.last() > hEps) distinct += h
    }
    if (distinct.size < 2) return null
    val heights = ArrayList<Float>(distinct.size * 2 - 1)
    for (i in distinct.indices) {
        heights += distinct[i]
        if (i < distinct.lastIndex) heights += (distinct[i] + distinct[i + 1]) * 0.5f
    }

    // Vzorek těsně pod a nad každou výškou: na skocích W(h) vyjde svislá hrana přesně,
    // na hladkém průběhu se druhý bod zahodí.
    val delta = 2f * hEps
    fun side(sideSign: Float): List<Offset> {
        val out = ArrayList<Offset>(heights.size * 2)
        for (h in heights) {
            val rBelow = rMaxAt(h - delta)
            val rAbove = rMaxAt(h + delta)
            out += o + projAxis * h + dir * (rBelow * sideSign)
            if (abs(rAbove - rBelow) > hEps) out += o + projAxis * h + dir * (rAbove * sideSign)
        }
        return out
    }
    return side(1f) + side(-1f).asReversed()
}
