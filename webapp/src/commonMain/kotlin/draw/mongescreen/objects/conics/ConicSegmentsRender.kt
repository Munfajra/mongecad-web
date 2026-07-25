package draw.mongescreen.objects.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.conicarcs.HyperbolaBasis
import draw.mongescreen.conicarcs.parabolaBasis
import draw.mongescreen.conicarcs.projectParabolaAndParam
import draw.mongescreen.conicarcs.projectToHyperbola
import model.ArcMode
import model.ConicSegment
import monge.input.ConicArcs.single.ellipseBasisFromDiameters
import monge.input.ConicArcs.single.ellipseParamAndProjection
import utils.dot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * View-agnostické renderery po částech stylovaných kuželoseček. Každý spočítá
 * view-geometrii jednou a pak iteruje úseky, volaje sdílené param jádra
 * (strokeEllipseParamArc / strokeParabolaParamArc / strokeHyperbolaParamArc)
 * s per-úsekovým [ConicSegment.style]. [project] mapuje view-space do plátna
 * (orth: toScreenOld, axo: toScreenAxoLocal / axo projekce, narys: y-flip).
 */

private val TWO_PI = (2.0 * kotlin.math.PI).toFloat()
private const val SEGMENT_EPS = 1e-4f

private fun ellipseSolidInterval(tA: Float, tB: Float, mode: ArcMode): Pair<Float, Float> {
    fun normAngle(t: Float): Float {
        var x = t % TWO_PI
        if (x < 0f) x += TWO_PI
        return x
    }

    fun ccwSpan(a1: Float, a2: Float): Float {
        val x1 = normAngle(a1)
        val x2 = normAngle(a2)
        return if (x2 >= x1) x2 - x1 else x2 - x1 + TWO_PI
    }

    val spanCCW = ccwSpan(tA, tB)
    val spanCW = TWO_PI - spanCCW
    return when (mode) {
        ArcMode.SHORTEST -> if (spanCCW <= spanCW) tA to (tA + spanCCW) else tA to (tA - spanCW)
        ArcMode.LONGEST -> if (spanCCW >= spanCW) tA to (tA + spanCCW) else tA to (tA - spanCW)
        ArcMode.CCW -> tA to (tA + spanCCW)
        ArcMode.CW -> tA to (tA - spanCW)
    }
}

private fun clipLinearSegments(
    segs: List<ConicSegment>,
    loIn: Float,
    hiIn: Float
): List<ConicSegment> {
    if (segs.isEmpty()) return emptyList()
    val lo = min(loIn, hiIn)
    val hi = max(loIn, hiIn)
    if (!lo.isFinite() || !hi.isFinite() || hi - lo <= SEGMENT_EPS) return emptyList()

    return segs.mapNotNull { seg ->
        val forward = seg.end >= seg.start
        val s0 = min(seg.start, seg.end)
        val s1 = max(seg.start, seg.end)
        val a = max(s0, lo)
        val b = min(s1, hi)
        if (!a.isFinite() || !b.isFinite() || b - a <= SEGMENT_EPS) {
            null
        } else if (forward) {
            ConicSegment(a, b, seg.style)
        } else {
            ConicSegment(b, a, seg.style)
        }
    }
}

fun restrictParabolaSegmentsToArc(
    vertex: Offset,
    focus: Offset,
    segs: List<ConicSegment>,
    arcEnds: Pair<Offset, Offset>?
): List<ConicSegment> {
    if (arcEnds == null || segs.isEmpty()) return segs
    val (uA, _) = projectParabolaAndParam(vertex, focus, arcEnds.first)
    val (uB, _) = projectParabolaAndParam(vertex, focus, arcEnds.second)
    if (!uA.isFinite() || !uB.isFinite()) return emptyList()
    return clipLinearSegments(segs, uA, uB)
}

fun restrictHyperbolaSegmentsToArcs(
    basis: HyperbolaBasis,
    forcedBranchSX: Int,
    segs: List<ConicSegment>,
    branch1: Pair<Offset, Offset>?,
    branch2: Pair<Offset, Offset>?
): List<ConicSegment> {
    if (segs.isEmpty()) return emptyList()
    if (branch1 == null && branch2 == null) return segs

    val restriction = listOfNotNull(branch1, branch2).firstOrNull { (a, _) ->
        val sx = if ((a - basis.center).dot(basis.ex) >= 0f) +1 else -1
        sx == forcedBranchSX
    } ?: return emptyList()

    val uA = projectToHyperbola(basis, restriction.first, forcedBranchSX)?.u ?: return emptyList()
    val uB = projectToHyperbola(basis, restriction.second, forcedBranchSX)?.u ?: return emptyList()
    if (!uA.isFinite() || !uB.isFinite()) return emptyList()
    return clipLinearSegments(segs, uA, uB)
}

fun restrictEllipseSegmentsToArc(
    p1: Offset,
    p2: Offset,
    p3: Offset,
    segs: List<ConicSegment>,
    arcEnds: Pair<Offset, Offset>?,
    mode: ArcMode
): List<ConicSegment> {
    if (arcEnds == null || segs.isEmpty()) return segs

    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    val (tA, _) = ellipseParamAndProjection(basis, arcEnds.first)
    val (tB, _) = ellipseParamAndProjection(basis, arcEnds.second)
    if (!tA.isFinite() || !tB.isFinite()) return emptyList()

    val (r0, r1) = ellipseSolidInterval(tA, tB, mode)
    val clipLo = min(r0, r1)
    val clipHi = max(r0, r1)
    if (clipHi - clipLo <= SEGMENT_EPS) return emptyList()

    val result = mutableListOf<ConicSegment>()
    for (seg in segs) {
        val forward = seg.end >= seg.start
        val s0 = min(seg.start, seg.end)
        val s1 = max(seg.start, seg.end)
        for (turn in -2..2) {
            val shift = turn * TWO_PI
            val a = max(s0 + shift, clipLo)
            val b = min(s1 + shift, clipHi)
            if (b - a > SEGMENT_EPS) {
                result += if (forward) {
                    ConicSegment(a, b, seg.style)
                } else {
                    ConicSegment(b, a, seg.style)
                }
            }
        }
    }
    return result
}

fun DrawScope.drawEllipseSegments(
    p1: Offset, p2: Offset, p3: Offset,
    segs: List<ConicSegment>,
    project: (Offset) -> Offset,
    color: Color, strokeWidth: Float, dashScale: Float = 1f
) {
    if (segs.isEmpty()) return
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val u = (p2 - p1) / 2f
    val a = u.getDistance()
    val mirrorP3 = Offset(2f * center.x - p3.x, 2f * center.y - p3.y)
    val v = (p3 - mirrorP3) / 2f
    val b = v.getDistance()
    if (isDegenerateEllipseAxes(u, a, v, b)) return   // degenerované → nechá fallback dispečeru
    val uN = Offset(u.x / a, u.y / a)
    val vN = Offset(v.x / b, v.y / b)
    if (!uN.x.isFinite() || !uN.y.isFinite() || !vN.x.isFinite() || !vN.y.isFinite()) return

    for (seg in segs) {
        strokeEllipseParamArc(
            center = center, a = a, b = b, uN = uN, vN = vN,
            tStart = seg.start, tEnd = seg.end,
            style = seg.style, project = project,
            color = color, strokeWidth = strokeWidth, dashScale = dashScale
        )
    }
}

fun DrawScope.drawParabolaSegments(
    vertex: Offset, focus: Offset,
    segs: List<ConicSegment>,
    project: (Offset) -> Offset,
    color: Color, strokeWidth: Float, dashScale: Float = 1f
) {
    if (segs.isEmpty()) return
    val basis = parabolaBasis(vertex, focus)
    for (seg in segs) {
        strokeParabolaParamArc(
            basis = basis, u1 = seg.start, u2 = seg.end,
            style = seg.style, project = project,
            color = color, strokeWidth = strokeWidth, dashScale = dashScale
        )
    }
}

fun DrawScope.drawHyperbolaBranchSegments(
    basis: HyperbolaBasis,
    forcedBranchSX: Int,
    segs: List<ConicSegment>,
    project: (Offset) -> Offset,
    color: Color, strokeWidth: Float, dashScale: Float = 1f
) {
    if (segs.isEmpty()) return
    // krajní hranice úseků slouží jako ořez → uMax musí je pokrýt (default 6f).
    var maxU = 6f
    for (seg in segs) maxU = max(maxU, max(abs(seg.start), abs(seg.end)))
    for (seg in segs) {
        strokeHyperbolaParamArc(
            basis = basis, forcedBranchSX = forcedBranchSX,
            u1In = seg.start, u2In = seg.end,
            style = seg.style, project = project,
            color = color, strokeWidth = strokeWidth,
            dashScale = dashScale,
            uMax = maxU
        )
    }
}
