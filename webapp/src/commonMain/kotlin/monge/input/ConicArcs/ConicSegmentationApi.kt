package monge.input.ConicArcs

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.conicarcs.HyperbolaBasis
import draw.mongescreen.conicarcs.projectParabolaAndParam
import draw.mongescreen.conicarcs.projectToHyperbola
import utils.dot
import model.ArcMode
import model.ConicSegment
import model.ConicSegmentation
import model.DrawingModeMonge
import model.LineStyle
import model.Mongeobjects
import model.ProjectionMode
import model.axo.AxoMode
import monge.input.ConicArcs.associated.findAxoConicIdByParent
import monge.input.ConicArcs.associated.findBokorysConicIdByParent
import monge.input.ConicArcs.associated.findNarysConicIdByParent
import monge.input.ConicArcs.associated.findPudorysConicIdByParent
import monge.input.ConicArcs.single.ellipseBasisFromDiameters
import monge.input.ConicArcs.single.ellipseParamAndProjection
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
// Producentské API pro po částech stylované kuželosečky (conicSegments).
//
// Univerzální setter setConicSegments je základ: jakýkoli producent (ruční akce
// i automatická funkce dle viditelnosti) spočítá parametry úseků v NATIVNÍM
// parametru daného průmětu a zavolá ho.
//   - elipsa:    úhel θ z ellipseParamAndProjection
//   - parabola:  u z projectParabolaAndParam
//   - hyperbola: u z projectToHyperbola (primary = větev sX=+1, secondary = -1)
//
// Automatický occlusion producent (nad draw/mongescreen/fills/FillOcclusion.kt
// a ui/planeUI/toolbar/AxoConversionNormalize.kt buildSoROcclusionTester)
// navzorkuje kuželosečku přes param rozsah, najde změny znaménka occluded(...)
// → dělicí parametry, střídá Solid/Dashed a zavolá setConicSegments. (Zatím
// nepostaveno — sem patří.)
// ─────────────────────────────────────────────────────────────────────────────

fun MongeState.setConicSegments(conicId: String, seg: ConicSegmentation) {
    conicSegments[conicId] = seg
    triggerRedraw++
}

fun MongeState.clearConicSegments(conicId: String) {
    if (conicSegments.remove(conicId) != null) triggerRedraw++
}

private const val TWO_PI = (2.0 * kotlin.math.PI).toFloat()

/**
 * Solidní interval [tStart,tEnd] elipsy pro dané konce (parametry) a [mode].
 * Stejná logika jako v kreslicích funkcích (normAngle/ccwSpan/pickInterval).
 */
private fun ellipseSolidInterval(tA: Float, tB: Float, mode: ArcMode): Pair<Float, Float> {
    fun normAngle(t: Float): Float {
        var x = t % TWO_PI
        if (x < 0f) x += TWO_PI
        return x
    }
    fun ccwSpan(a1: Float, a2: Float): Float {
        val x1 = normAngle(a1); val x2 = normAngle(a2)
        return if (x2 >= x1) x2 - x1 else x2 - x1 + TWO_PI
    }
    val spanCCW = ccwSpan(tA, tB)
    val spanCW = TWO_PI - spanCCW
    return when (mode) {
        ArcMode.SHORTEST -> if (spanCCW <= spanCW) tA to (tA + spanCCW) else tA to (tA - spanCW)
        ArcMode.LONGEST  -> if (spanCCW >= spanCW) tA to (tA + spanCCW) else tA to (tA - spanCW)
        ArcMode.CCW      -> tA to (tA + spanCCW)
        ArcMode.CW       -> tA to (tA - spanCW)
    }
}

/**
 * Segmentace elipsy "plný oblouk A→B + zbytek čárkovaně" v nativním parametru
 * průmětu daného vstupy [p1],[p2],[p3]. [A],[B] jsou 2D body v prostoru průmětu
 * (stejná konvence jako ellipseArcEnds toho průmětu — tj. narys už display (x,−z)).
 */
fun ellipseDashedRestSegmentation(
    p1: Offset, p2: Offset, p3: Offset,
    A: Offset, B: Offset,
    mode: ArcMode = ArcMode.SHORTEST
): ConicSegmentation? {
    if (p3 == Offset.Unspecified) return null
    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    val (tA, _) = ellipseParamAndProjection(basis, A)
    val (tB, _) = ellipseParamAndProjection(basis, B)
    if (!tA.isFinite() || !tB.isFinite()) return null
    val (tStart, tEnd) = ellipseSolidInterval(tA, tB, mode)
    val dir = if (tEnd >= tStart) 1f else -1f
    return ConicSegmentation(
        primary = listOf(
            ConicSegment(tStart, tEnd, LineStyle.Solid),
            ConicSegment(tEnd, tStart + dir * TWO_PI, LineStyle.Dashed)
        )
    )
}

/**
 * Z existujícího jednoduchého oblouku elipsy (ellipseArcEnds[conicId]) udělá
 * segmentaci "plný oblouk + zbytek čárkovaně" pro daný průmět a zapíše ji.
 * [p1],[p2],[p3] jsou vstupy elipsy toho průmětu (conicInputPoints*).
 */
fun MongeState.enableEllipseDashedRest(
    conicId: String, p1: Offset, p2: Offset, p3: Offset
): Boolean {
    val (A, B) = ellipseArcEnds[conicId] ?: return false
    val mode = ellipseArcMode[conicId] ?: ArcMode.SHORTEST
    val seg = ellipseDashedRestSegmentation(p1, p2, p3, A, B, mode) ?: return false
    setConicSegments(conicId, seg)
    return true
}

/**
 * Aplikuje "plný oblouk + zbytek čárkovaně" na všechny průměty 3D elipsy
 * ([parentId]) — půdorys/nárys/bokorys/axo. Vstupy i konce oblouku bere z
 * per-view map, takže nárysový flip z i rozdíl MONGE vs AXO se řeší per průmět
 * (stejně jako propagateEllipseArcToBokorysAndAxo).
 */
/**
 * Segmentace paraboly "viditelný oblouk A→B + čárkované ocásky + ořez extentu".
 * u1,u2 se spočítají z [A],[B] promítnutých na parabolu (vertex,focus). Ocásky
 * sahají za viditelný úsek o margin (default "dost" – uživatel doladí).
 */
fun parabolaVisibleSegmentation(
    vertex: Offset, focus: Offset, A: Offset, B: Offset
): ConicSegmentation? {
    val (u1, _) = projectParabolaAndParam(vertex, focus, A)
    val (u2, _) = projectParabolaAndParam(vertex, focus, B)
    if (!u1.isFinite() || !u2.isFinite()) return null
    val ua = min(u1, u2); val ub = max(u1, u2)
    val span = ub - ua
    val margin = max(span * 1.2f, 20f)
    return ConicSegmentation(
        primary = listOf(
            ConicSegment(ua - margin, ua, LineStyle.Dashed),
            ConicSegment(ua, ub, LineStyle.Solid),
            ConicSegment(ub, ub + margin, LineStyle.Dashed)
        )
    )
}

/** Z existujícího parabolického oblouku (parabolaArcEnds[conicId]) → segmentace viditelnosti. */
fun MongeState.enableParabolaVisibleFromArc(conicId: String, vertex: Offset, focus: Offset): Boolean {
    val (A, B) = parabolaArcEnds[conicId] ?: return false
    val seg = parabolaVisibleSegmentation(vertex, focus, A, B) ?: return false
    setConicSegments(conicId, seg)
    return true
}

private const val PARAM_EPS = 1e-3f

/** Úseky uvnitř omezení [lo,hi]: [lo..vLo dashed, vLo..vHi solid, vHi..hi dashed]. */
private fun clampedVisibilitySegments(lo: Float, hi: Float, vLo: Float, vHi: Float): List<ConicSegment> {
    val a = vLo.coerceIn(lo, hi); val b = vHi.coerceIn(lo, hi)
    val segs = mutableListOf<ConicSegment>()
    if (a - lo > PARAM_EPS) segs += ConicSegment(lo, a, LineStyle.Dashed)
    segs += ConicSegment(a, b, LineStyle.Solid)
    if (hi - b > PARAM_EPS) segs += ConicSegment(b, hi, LineStyle.Dashed)
    return segs
}

/** Přenese úhel do [lo, lo+2π). */
private fun bringAngleInto(t: Float, lo: Float): Float {
    var x = t
    while (x < lo) x += TWO_PI
    while (x >= lo + TWO_PI) x -= TWO_PI
    return x
}

/** Jedna větev hyperboly BEZ omezení: [dashed ocásek, solid A..B, dashed ocásek] + větev (sX). */
private fun hyperbolaBranchVisibleSegments(basis: HyperbolaBasis, A: Offset, B: Offset): Pair<Int, List<ConicSegment>>? {
    val sx = if ((A - basis.center).dot(basis.ex) >= 0f) +1 else -1
    val ua = projectToHyperbola(basis, A, sx)?.u ?: return null
    val ub = projectToHyperbola(basis, B, sx)?.u ?: return null
    if (!ua.isFinite() || !ub.isFinite()) return null
    val lo = min(ua, ub); val hi = max(ua, ub)
    val margin = max(hi - lo, 1.5f)
    return sx to listOf(
        ConicSegment(lo - margin, lo, LineStyle.Dashed),
        ConicSegment(lo, hi, LineStyle.Solid),
        ConicSegment(hi, hi + margin, LineStyle.Dashed)
    )
}

// ── Per-typ finalizery viditelnosti (respektují zachycené omezení = conicArc) ──

private fun MongeState.finalizeEllipseVisibility(conicId: String, p1: Offset, p2: Offset, p3: Offset): Boolean {
    val (visA, visB) = ellipseArcEnds[conicId] ?: return false
    val restriction = visRestrictEllipse
    if (restriction == null) {
        // bez omezení → solid A..B + zbytek celé elipsy čárkovaně
        val seg = ellipseDashedRestSegmentation(p1, p2, p3, visA, visB, ellipseArcMode[conicId] ?: ArcMode.SHORTEST) ?: return false
        setConicSegments(conicId, seg)
        ellipseArcEnds.remove(conicId); ellipseArcMode.remove(conicId)
        return true
    }
    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    val (tV1, _) = ellipseParamAndProjection(basis, visA)
    val (tV2, _) = ellipseParamAndProjection(basis, visB)
    val mode = visRestrictEllipseMode ?: ArcMode.SHORTEST
    val (tR1, _) = ellipseParamAndProjection(basis, restriction.first)
    val (tR2, _) = ellipseParamAndProjection(basis, restriction.second)
    val (s0, s1) = ellipseSolidInterval(tR1, tR2, mode)
    val lo = min(s0, s1); val hi = max(s0, s1)
    val v1 = bringAngleInto(tV1, lo); val v2 = bringAngleInto(tV2, lo)
    val segs = clampedVisibilitySegments(lo, hi, min(v1, v2), max(v1, v2))
    setConicSegments(conicId, ConicSegmentation(segs))
    ellipseArcEnds[conicId] = restriction; ellipseArcMode[conicId] = mode
    return true
}

private fun MongeState.finalizeParabolaVisibility(conicId: String, vertex: Offset, focus: Offset): Boolean {
    val (visA, visB) = parabolaArcEnds[conicId] ?: return false
    val (uV1, _) = projectParabolaAndParam(vertex, focus, visA)
    val (uV2, _) = projectParabolaAndParam(vertex, focus, visB)
    if (!uV1.isFinite() || !uV2.isFinite()) return false
    val restriction = visRestrictParabola
    if (restriction == null) {
        val seg = parabolaVisibleSegmentation(vertex, focus, visA, visB) ?: return false
        setConicSegments(conicId, seg)
        parabolaArcEnds.remove(conicId)
        return true
    }
    val (uR1, _) = projectParabolaAndParam(vertex, focus, restriction.first)
    val (uR2, _) = projectParabolaAndParam(vertex, focus, restriction.second)
    val lo = min(uR1, uR2); val hi = max(uR1, uR2)
    val segs = clampedVisibilitySegments(lo, hi, min(uV1, uV2), max(uV1, uV2))
    setConicSegments(conicId, ConicSegmentation(segs))
    parabolaArcEnds[conicId] = restriction
    return true
}

private fun MongeState.finalizeHyperbolaVisibility(conicId: String): Boolean {
    val input = hyperbolaInputsPudorys[conicId] ?: hyperbolaInputsNarys[conicId]
        ?: hyperbolaInputsBokorys[conicId] ?: hyperbolaInputsAxo[conicId] ?: return false
    val basis = buildHyperbolaBasis(input) ?: return false
    var primary: List<ConicSegment>? = null
    var secondary: List<ConicSegment>? = null
    fun apply(visEnds: Pair<Offset, Offset>?, restrictEnds: Pair<Offset, Offset>?) {
        if (visEnds == null) return
        if (restrictEnds == null) {
            val (sx, segs) = hyperbolaBranchVisibleSegments(basis, visEnds.first, visEnds.second) ?: return
            if (sx >= 0) primary = segs else secondary = segs
            return
        }
        val sx = if ((visEnds.first - basis.center).dot(basis.ex) >= 0f) +1 else -1
        val uV1 = projectToHyperbola(basis, visEnds.first, sx)?.u ?: return
        val uV2 = projectToHyperbola(basis, visEnds.second, sx)?.u ?: return
        val uR1 = projectToHyperbola(basis, restrictEnds.first, sx)?.u ?: return
        val uR2 = projectToHyperbola(basis, restrictEnds.second, sx)?.u ?: return
        if (!uV1.isFinite() || !uV2.isFinite() || !uR1.isFinite() || !uR2.isFinite()) return
        val lo = min(uR1, uR2); val hi = max(uR1, uR2)
        val segs = clampedVisibilitySegments(lo, hi, min(uV1, uV2), max(uV1, uV2))
        if (sx >= 0) primary = segs else secondary = segs
    }
    apply(hyperbolaArcBranch1[conicId], visRestrictHypB1)
    apply(hyperbolaArcBranch2[conicId], visRestrictHypB2)
    if (primary == null && secondary == null) return false
    setConicSegments(conicId, ConicSegmentation(primary ?: emptyList(), secondary))
    if (visRestrictHypB1 != null) hyperbolaArcBranch1[conicId] = visRestrictHypB1!! else hyperbolaArcBranch1.remove(conicId)
    if (visRestrictHypB2 != null) hyperbolaArcBranch2[conicId] = visRestrictHypB2!! else hyperbolaArcBranch2.remove(conicId)
    return true
}

/**
 * Zavolá se TĚSNĚ PŘED commitSnapshot v arc handlerech.
 *  - Probíhá-li authoring viditelnosti → převede právě zadaný úsek na segmentaci
 *    (solid = zadaný úsek) OŘÍZNUTOU na zachycené omezení (conicArc), které pak
 *    obnoví (omezení zůstane zachované).
 *  - Jinak běžný CONICARC právě nastavil nové omezení. Segmentaci necháváme
 *    zachovanou; renderer ji bere jako viditelnost a aktuální conicArc je tvrdý ořez.
 */
fun MongeState.finalizeConicVisibilityIfActive(conicId: String) {
    if (!conicVisibilityAuthoring) {
        triggerRedraw++
        return
    }
    val isHyper = hyperbolaInputsPudorys.containsKey(conicId) ||
        hyperbolaInputsNarys.containsKey(conicId) ||
        hyperbolaInputsBokorys.containsKey(conicId) ||
        hyperbolaInputsAxo.containsKey(conicId)
    if (isHyper) {
        finalizeHyperbolaVisibility(conicId)
    } else {
        val inp = conicInputPointsPudorys[conicId]
            ?: conicInputPointsNarys[conicId]
            ?: conicInputPointsBokorys[conicId]
            ?: conicInputPointsAxo[conicId]
        if (inp != null) {
            if (inp.third != Offset.Unspecified) finalizeEllipseVisibility(conicId, inp.first, inp.second, inp.third)
            else finalizeParabolaVisibility(conicId, inp.first, inp.second)
        }
    }
    visRestrictEllipse = null; visRestrictEllipseMode = null
    visRestrictParabola = null
    visRestrictHypB1 = null; visRestrictHypB2 = null
    conicVisibilityAuthoring = false
}

/**
 * Spustí authoring viditelnosti přímo ze selection info dané kuželosečky (bez
 * nutnosti kuželosečku znovu označovat). Reuse-uje CONICARC arc flow: nastaví
 * cílový průmět, zamkne per-view kuželosečku a skočí rovnou do `_hold` fáze.
 * [prefix] = "pudorys"/"narys"/"bokorys"/"axo". V AXO módu se routuje přes
 * axoMode (handleConicArcAxo), jinak přes mongeMode (MongeClickHandlers).
 * Podporováno: elipsa + parabola + hyperbola.
 */
private fun MongeState.startConicVisibilityForProjection(perViewId: String, prefix: String): Boolean {
    val hyperInputs = when (prefix) {
        "pudorys" -> hyperbolaInputsPudorys
        "narys" -> hyperbolaInputsNarys
        "bokorys" -> hyperbolaInputsBokorys
        else -> hyperbolaInputsAxo
    }
    val inputs = when (prefix) {
        "pudorys" -> conicInputPointsPudorys[perViewId]
        "narys" -> conicInputPointsNarys[perViewId]
        "bokorys" -> conicInputPointsBokorys[perViewId]
        else -> conicInputPointsAxo[perViewId]
    }
    val isHyper = hyperInputs.containsKey(perViewId)
    if (!isHyper && inputs == null) {
        println("⚠️ Kuželosečka v tomto průmětu nemá vstupy.")
        return false
    }
    val phaseSuffix = when {
        isHyper -> "hyp_second_hold"
        inputs != null && inputs.third != Offset.Unspecified -> "ellipse_arc_hold"
        else -> "par_arc_hold"
    }

    // zachyť existující omezení (conicArc), aby ho viditelnost nepřepsala,
    // ale nastavila solid jen v jeho rámci
    visRestrictEllipse = ellipseArcEnds[perViewId]
    visRestrictEllipseMode = ellipseArcMode[perViewId]
    visRestrictParabola = parabolaArcEnds[perViewId]
    visRestrictHypB1 = hyperbolaArcBranch1[perViewId]
    visRestrictHypB2 = hyperbolaArcBranch2[perViewId]

    // reset pending
    pendingArcA = null
    pendingHypA1 = null; pendingHypA2 = null
    pendingHypBranch1SX = null; pendingHypBranch2SX = null
    activeConicIdForArc = perViewId
    conicVisibilityAuthoring = true
    drawobjects = Mongeobjects.CONICARC

    if (projectionMode == ProjectionMode.AXO) {
        axoMode = when (prefix) {
            "pudorys" -> AxoMode.AXO_PUDORYS
            "narys" -> AxoMode.AXO_NARYS
            "bokorys" -> AxoMode.AXO_BOKORYS
            else -> AxoMode.NORMAL_2D
        }
    } else {
        mongeMode = if (prefix == "narys") DrawingModeMonge.NARYS else DrawingModeMonge.PUDORYS
    }

    setProjectionPhase("${prefix}_$phaseSuffix", this)
    triggerRedraw++
    return true
}

fun MongeState.startConicVisibility(parentId: String, prefix: String): Boolean {
    val perViewId = when (prefix) {
        "pudorys" -> findPudorysConicIdByParent(parentId)
        "narys" -> findNarysConicIdByParent(parentId)
        "bokorys" -> findBokorysConicIdByParent(parentId)
        "axo" -> findAxoConicIdByParent(parentId)
        else -> null
    } ?: run { println("⚠️ Průmět kuželosečky nenalezen."); return false }

    return startConicVisibilityForProjection(perViewId, prefix)
}

fun MongeState.startSingleConicVisibility(conicId: String, prefix: String): Boolean =
    startConicVisibilityForProjection(conicId, prefix)

fun MongeState.enableEllipseDashedRestForParent(parentId: String) {
    fun apply(conicId: String?, inputs: Triple<Offset, Offset, Offset>?) {
        if (conicId == null || inputs == null) return
        enableEllipseDashedRest(conicId, inputs.first, inputs.second, inputs.third)
    }
    val pud = conicsPudorys.firstOrNull { it.parentId == parentId || it.parent?.id == parentId }?.id
    apply(pud, pud?.let { conicInputPointsPudorys[it] })
    val nar = conicsNarys.firstOrNull { it.parentId == parentId || it.parent?.id == parentId }?.id
    apply(nar, nar?.let { conicInputPointsNarys[it] })
    val bok = conicsBokorys.firstOrNull { it.parentId == parentId || it.parent?.id == parentId }?.id
    apply(bok, bok?.let { conicInputPointsBokorys[it] })
    val axo = conicsAxo.firstOrNull { it.parentId == parentId || it.parent?.id == parentId }?.id
    apply(axo, axo?.let { conicInputPointsAxo[it] })
}
