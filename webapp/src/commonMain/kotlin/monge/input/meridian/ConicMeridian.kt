package monge.input.meridian

import geometry.hyperbolaPointAt

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.conicarcs.HyperbolaBasis
import draw.mongescreen.conicarcs.hyperbolaBasisFrom
import draw.mongescreen.conicarcs.parabolaBasis
import draw.mongescreen.conicarcs.fromLocal
import draw.mongescreen.conicarcs.projectParabolaAndParam
import draw.mongescreen.conicarcs.projectToHyperbola
import model.ArcMode
import model.LineStyle
import model.classes.ConicInputHyperbolaNarys
import model.classes.ConicInputHyperbolaPudorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.NamedLineNarys
import model.classes.NamedLineNarysImpl
import model.classes.NamedLinePudorys
import model.classes.NamedLinePudorysImpl
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.ConicArcs.single.EllipseBasis
import monge.input.ConicArcs.single.ellipseBasisFromDiameters
import monge.input.ConicArcs.single.ellipseParamAndProjection
import state.MongeState
import state.snapMonge.computeIntersection
import utils.allocIndex
import utils.dot
import utils.normalize
import utils.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Navzorkovaný meridián z kuželosečky.
 * Polyline je v "native" souřadnicích meridiánu: nárys (x, z), půdorys (x, y).
 */
data class ConicMeridianSample(
    val polyline: List<Offset>,
    val closed: Boolean
)

// ---------- společné vzorkovací pomůcky ----------

private fun ellipsePointAt(b: EllipseBasis, t: Float): Offset {
    val cosT = cos(t)
    val sinT = sin(t)
    return b.center + Offset(
        b.a * cosT * b.uN.x + b.b * sinT * b.vN.x,
        b.a * cosT * b.uN.y + b.b * sinT * b.vN.y
    )
}

private fun sampleEllipseFull(b: EllipseBasis, steps: Int = 256): List<Offset> {
    val out = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val t = 2f * PI.toFloat() * i / steps
        out += ellipsePointAt(b, t)
    }
    return out
}

private fun normAngle(t: Float): Float {
    val twoPi = (2f * PI).toFloat()
    var x = t % twoPi
    if (x < 0f) x += twoPi
    return x
}

private fun ccwSpan(a1: Float, a2: Float): Float {
    val twoPi = (2f * PI).toFloat()
    val x1 = normAngle(a1)
    val x2 = normAngle(a2)
    return if (x2 >= x1) x2 - x1 else x2 - x1 + twoPi
}

/** Stejná volba intervalu jako v drawEllipseArcFromDiameters. */
private fun pickEllipseIntervalByMode(t1: Float, t2: Float, mode: ArcMode): Pair<Float, Float> {
    val twoPi = (2f * PI).toFloat()
    val a1 = normAngle(t1)
    val a2 = normAngle(t2)
    val ccw = ccwSpan(a1, a2)
    val cw = twoPi - ccw
    return when (mode) {
        ArcMode.CCW      -> a1 to (a1 + ccw)
        ArcMode.CW       -> a1 to (a1 - cw)
        ArcMode.SHORTEST -> if (ccw <= cw) a1 to (a1 + ccw) else a1 to (a1 - cw)
        ArcMode.LONGEST  -> if (ccw >= cw) a1 to (a1 + ccw) else a1 to (a1 - cw)
    }
}

private fun sampleEllipseArc(
    b: EllipseBasis,
    A: Offset,
    B: Offset,
    mode: ArcMode,
    du: Float = 0.02f
): List<Offset> {
    val (tA, Aon) = ellipseParamAndProjection(b, A)
    val (tB, Bon) = ellipseParamAndProjection(b, B)
    val (tStart, tEnd) = pickEllipseIntervalByMode(tA, tB, mode)

    val dir = if (tEnd >= tStart) +1f else -1f
    val length = abs(tEnd - tStart)
    val steps = max(2, ceil(length / du).toInt())

    val out = ArrayList<Offset>(steps + 1)
    out += Aon
    for (i in 1 until steps) {
        val t = tStart + dir * (i * (length / steps))
        out += ellipsePointAt(b, t)
    }
    out += Bon
    return out
}

private fun sampleParabolaByU(vertex: Offset, focus: Offset, u1: Float, u2: Float): List<Offset> {
    val basis = parabolaBasis(vertex, focus)
    val p = basis.p
    if (p < 1e-6f) return emptyList()

    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    if (L < 1e-6f) return emptyList()
    val steps = max(2, ceil(L / 0.5f).toInt()).coerceAtMost(2048)

    val out = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val u = u1 + dir * (i * (L / steps))
        val x = (u * u) / (4f * p)
        out += fromLocal(basis, Offset(x, u))
    }
    return out
}

private fun hyperbolaPointAt(basis: HyperbolaBasis, sX: Int, u: Float): Offset {
    val ch = cosh(u)
    val sh = sinh(u)
    return basis.center + basis.ex * (sX * basis.a * ch) + basis.ey * (basis.b * sh)
}

private fun sampleHyperbolaBranchByU(
    basis: HyperbolaBasis,
    sX: Int,
    u1: Float,
    u2: Float,
    du: Float = 0.02f
): List<Offset> {
    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    if (L < 1e-6f) return emptyList()
    val steps = max(2, ceil(L / du).toInt()).coerceAtMost(4096)

    val out = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val u = u1 + dir * (i * (L / steps))
        out += hyperbolaPointAt(basis, sX, u)
    }
    return out
}

private fun sampleHyperbolaBranchArc(
    basis: HyperbolaBasis,
    A: Offset,
    B: Offset
): List<Offset>? {
    val sX = if ((A - basis.center).dot(basis.ex) >= 0f) +1 else -1
    val pA = projectToHyperbola(basis, A, sX) ?: return null
    val pB = projectToHyperbola(basis, B, sX) ?: return null
    return sampleHyperbolaBranchByU(basis, sX, pA.u, pB.u)
}

// ---------- NÁRYS ----------

/**
 * Navzorkuje kuželosečku v nárysu jako meridián v native (x, z).
 * Respektuje ořezy (ellipseArcEnds / parabolaArcEnds / hyperbolaArcBranch1/2).
 * Vrací null pro degenerované kuželosečky nebo chybějící vstupy.
 */
fun sampleConicMeridianNarys(state: MongeState, conic: ConicSectionNarys): ConicMeridianSample? {
    if (conic.isDegenerate) return null

    // hyperbola
    state.hyperbolaInputsNarys[conic.id]?.let { input ->
        val center = computeIntersection(
            Offset(input.line1.point.x, input.line1.point.z),
            Offset(input.line1.direction.x, input.line1.direction.y),
            Offset(input.line2.point.x, input.line2.point.z),
            Offset(input.line2.direction.x, input.line2.direction.y)
        ) ?: input.vertex

        val basis = hyperbolaBasisFrom(center, input.vertex, input.line1.direction.normalize(), input.axis)
        val branch = state.hyperbolaArcBranch1[conic.id] ?: state.hyperbolaArcBranch2[conic.id]

        val poly = if (branch != null) {
            sampleHyperbolaBranchArc(basis, branch.first, branch.second) ?: return null
        } else {
            // bez ořezu: větev s vrcholem (ex míří na vrchol)
            sampleHyperbolaBranchByU(basis, sX = +1, u1 = -1.6f, u2 = 1.6f)
        }
        if (poly.size < 2) return null
        return ConicMeridianSample(poly, closed = false)
    }

    val input = state.conicInputPointsNarys[conic.id] ?: return null
    val (p1, p2, p3) = input
    if (p1 == Offset.Unspecified || p2 == Offset.Unspecified) return null

    if (p3 == Offset.Unspecified) {
        // parabola – vstupy i konce oblouku jsou v native (x, z)
        val ends = state.parabolaArcEnds[conic.id]
        val (u1, u2) = if (ends != null) {
            projectParabolaAndParam(p1, p2, ends.first).first to
                    projectParabolaAndParam(p1, p2, ends.second).first
        } else {
            val p = (p2 - p1).getDistance().coerceAtLeast(1e-6f)
            val maxU = 40f * sqrt(p)
            -maxU to maxU
        }
        val poly = sampleParabolaByU(p1, p2, u1, u2)
        if (poly.size < 2) return null
        return ConicMeridianSample(poly, closed = false)
    }

    // elipsa – vstupy i konce oblouku jsou v display (x, -z) → flip na native (x, z)
    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    val ends = state.ellipseArcEnds[conic.id]
    val polyDisp: List<Offset>
    val closed: Boolean
    if (ends != null) {
        val mode = state.ellipseArcMode[conic.id] ?: ArcMode.SHORTEST
        polyDisp = sampleEllipseArc(basis, ends.first, ends.second, mode)
        closed = false
    } else {
        polyDisp = sampleEllipseFull(basis)
        closed = true
    }
    if (polyDisp.size < 2) return null
    return ConicMeridianSample(polyDisp.map { Offset(it.x, -it.y) }, closed)
}

// ---------- PŮDORYS ----------

/**
 * Navzorkuje kuželosečku v půdorysu jako meridián v native (x, y).
 */
fun sampleConicMeridianPudorys(state: MongeState, conic: ConicSectionPudorys): ConicMeridianSample? {
    if (conic.isDegenerate) return null

    // hyperbola
    state.hyperbolaInputsPudorys[conic.id]?.let { input ->
        val center = computeIntersection(
            Offset(input.line1.point.x, input.line1.point.y),
            Offset(input.line1.direction.x, input.line1.direction.y),
            Offset(input.line2.point.x, input.line2.point.y),
            Offset(input.line2.direction.x, input.line2.direction.y)
        ) ?: input.vertex

        val basis = hyperbolaBasisFrom(center, input.vertex, input.line1.direction.normalize(), input.axis)
        val branch = state.hyperbolaArcBranch1[conic.id] ?: state.hyperbolaArcBranch2[conic.id]

        val poly = if (branch != null) {
            sampleHyperbolaBranchArc(basis, branch.first, branch.second) ?: return null
        } else {
            sampleHyperbolaBranchByU(basis, sX = +1, u1 = -1.6f, u2 = 1.6f)
        }
        if (poly.size < 2) return null
        return ConicMeridianSample(poly, closed = false)
    }

    val input = state.conicInputPointsPudorys[conic.id] ?: return null
    val (p1, p2, p3) = input
    if (p1 == Offset.Unspecified || p2 == Offset.Unspecified) return null

    if (p3 == Offset.Unspecified) {
        // parabola
        val ends = state.parabolaArcEnds[conic.id]
        val (u1, u2) = if (ends != null) {
            projectParabolaAndParam(p1, p2, ends.first).first to
                    projectParabolaAndParam(p1, p2, ends.second).first
        } else {
            val p = (p2 - p1).getDistance().coerceAtLeast(1e-6f)
            val maxU = 40f * sqrt(p)
            -maxU to maxU
        }
        val poly = sampleParabolaByU(p1, p2, u1, u2)
        if (poly.size < 2) return null
        return ConicMeridianSample(poly, closed = false)
    }

    // elipsa – půdorysové display == native (x, y)
    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    val ends = state.ellipseArcEnds[conic.id]
    val poly: List<Offset>
    val closed: Boolean
    if (ends != null) {
        val mode = state.ellipseArcMode[conic.id] ?: ArcMode.SHORTEST
        poly = sampleEllipseArc(basis, ends.first, ends.second, mode)
        closed = false
    } else {
        poly = sampleEllipseFull(basis)
        closed = true
    }
    if (poly.size < 2) return null
    return ConicMeridianSample(poly, closed)
}

// ---------- zrcadlení kuželosečky přes osu x = h ----------

/**
 * Koeficienty kuželosečky ax²+bxy+cy²+dx+ey+f po substituci x → 2h−x.
 */
private fun mirroredConicCoeffs(
    a: Float, b: Float, c: Float, d: Float, e: Float, f: Float, h: Float
): FloatArray = floatArrayOf(
    a,
    -b,
    c,
    -(4f * h * a + d),
    2f * h * b + e,
    4f * h * h * a + 2f * h * d + f
)

private fun mirrorX(p: Offset, h: Float): Offset =
    if (p == Offset.Unspecified) p else Offset(2f * h - p.x, p.y)

/** Přenese ořezy oblouků (sdílené mapy podle id) z originálu na zrcadlo. */
private fun mirrorArcTrims(state: MongeState, srcId: String, dstId: String, h: Float) {
    state.ellipseArcEnds[srcId]?.let { (a, b) ->
        state.ellipseArcEnds[dstId] = mirrorX(a, h) to mirrorX(b, h)
    }
    state.ellipseArcMode[srcId]?.let { m ->
        state.ellipseArcMode[dstId] = when (m) {
            ArcMode.CCW -> ArcMode.CW
            ArcMode.CW -> ArcMode.CCW
            else -> m
        }
    }
    state.parabolaArcEnds[srcId]?.let { (a, b) ->
        state.parabolaArcEnds[dstId] = mirrorX(a, h) to mirrorX(b, h)
    }
    state.hyperbolaArcBranch1[srcId]?.let { (a, b) ->
        state.hyperbolaArcBranch1[dstId] = mirrorX(a, h) to mirrorX(b, h)
    }
    state.hyperbolaArcBranch2[srcId]?.let { (a, b) ->
        state.hyperbolaArcBranch2[dstId] = mirrorX(a, h) to mirrorX(b, h)
    }
}

private fun mirrorNamedLineNarys(src: NamedLineNarys, h: Float): NamedLineNarys =
    NamedLineNarysImpl(
        id = UUID.randomUUID().toString(),
        name = src.name,
        point = Point3DNarys(x = 2f * h - src.point.x, z = src.point.z),
        direction = Offset(-src.direction.x, src.direction.y),
        color = src.color,
        strokeWidth = src.strokeWidth,
        lineStyle = src.lineStyle
    )

private fun mirrorNamedLinePudorys(src: NamedLinePudorys, h: Float): NamedLinePudorys =
    NamedLinePudorysImpl(
        id = UUID.randomUUID().toString(),
        name = src.name,
        point = Point3DPudorys(x = 2f * h - src.point.x, y = src.point.y),
        direction = Offset(-src.direction.x, src.direction.y),
        color = src.color,
        strokeWidth = src.strokeWidth,
        lineStyle = src.lineStyle
    )

/**
 * Vytvoří zrcadlenou kuželosečku v nárysu (druhá polovina meridiánu),
 * včetně vstupních bodů a ořezů, aby se dala kreslit i snapovat.
 */
fun createMirroredConicNarys(
    state: MongeState,
    original: ConicSectionNarys,
    axisX0: Float
): String {
    val k = mirroredConicCoeffs(original.a, original.b, original.c, original.d, original.e, original.f, axisX0)

    val mirrored = original.copy(
        a = k[0], b = k[1], c = k[2], d = k[3], e = k[4], f = k[5],
        rawName = "",
        lineStyle = LineStyle.Dashed,
        parent = null,
        parentId = null,
        id = UUID.randomUUID().toString(),
        showInAxoInitial = original.showInAxo,
        creationIndex = allocIndex(state)
    )
    state.conicsNarys += mirrored

    state.conicInputPointsNarys[original.id]?.let { (q1, q2, q3) ->
        state.conicInputPointsNarys[mirrored.id] =
            Triple(mirrorX(q1, axisX0), mirrorX(q2, axisX0), mirrorX(q3, axisX0))
    }
    state.hyperbolaInputsNarys[original.id]?.let { inp ->
        state.hyperbolaInputsNarys[mirrored.id] = ConicInputHyperbolaNarys(
            vertex = mirrorX(inp.vertex, axisX0),
            axis = Offset(-inp.axis.x, inp.axis.y),
            line1 = mirrorNamedLineNarys(inp.line1, axisX0),
            line2 = mirrorNamedLineNarys(inp.line2, axisX0)
        )
    }
    mirrorArcTrims(state, original.id, mirrored.id, axisX0)

    state.triggerRedraw++
    return mirrored.id
}

/**
 * Vytvoří zrcadlenou kuželosečku v půdorysu (druhá polovina meridiánu).
 */
fun createMirroredConicPudorys(
    state: MongeState,
    original: ConicSectionPudorys,
    axisX0: Float
): String {
    val k = mirroredConicCoeffs(original.a, original.b, original.c, original.d, original.e, original.f, axisX0)

    val mirrored = original.copy(
        a = k[0], b = k[1], c = k[2], d = k[3], e = k[4], f = k[5],
        rawName = "",
        lineStyle = LineStyle.Dashed,
        parent = null,
        parentId = null,
        id = UUID.randomUUID().toString(),
        showInAxoInitial = original.showInAxo,
        creationIndex = allocIndex(state)
    )
    state.conicsPudorys += mirrored

    state.conicInputPointsPudorys[original.id]?.let { (q1, q2, q3) ->
        state.conicInputPointsPudorys[mirrored.id] =
            Triple(mirrorX(q1, axisX0), mirrorX(q2, axisX0), mirrorX(q3, axisX0))
    }
    state.hyperbolaInputsPudorys[original.id]?.let { inp ->
        state.hyperbolaInputsPudorys[mirrored.id] = ConicInputHyperbolaPudorys(
            vertex = mirrorX(inp.vertex, axisX0),
            axis = Offset(-inp.axis.x, inp.axis.y),
            line1 = mirrorNamedLinePudorys(inp.line1, axisX0),
            line2 = mirrorNamedLinePudorys(inp.line2, axisX0)
        )
    }
    mirrorArcTrims(state, original.id, mirrored.id, axisX0)

    state.triggerRedraw++
    return mirrored.id
}

// ---------- pomůcka pro uzavřené meridiány ----------

/**
 * U uzavřené polyline posune start tak, aby neležel v extrému poloměru –
 * findRadiusExtremaByTrend šev neobchází a extrém na švu by se ztratil
 * (typicky elipsa s průměrem kolmým na osu rotace).
 */
fun rotateClosedSampleAwayFromRadiusExtremum(poly: List<Offset>, axisX0: Float): List<Offset> {
    if (poly.size < 5) return poly

    val hasDupEnd = (poly.first() - poly.last()).getDistanceSquared() < 1e-6f
    val core = if (hasDupEnd) poly.dropLast(1) else poly
    val n = core.size
    if (n < 4) return poly

    fun r(i: Int) = abs(core[((i % n) + n) % n].x - axisX0)

    var start = 0
    var tries = 0
    while (tries < 8) {
        val prev = r(start - 1)
        val cur = r(start)
        val next = r(start + 1)
        val isExtremum = (cur >= prev && cur >= next) || (cur <= prev && cur <= next)
        if (!isExtremum) break
        start = (start + n / 8 + 1) % n
        tries++
    }
    if (start == 0) return poly

    val rotated = ArrayList<Offset>(n + 1)
    for (i in 0 until n) rotated += core[(start + i) % n]
    rotated += core[start]
    return rotated
}
