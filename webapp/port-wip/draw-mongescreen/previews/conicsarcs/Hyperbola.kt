package draw.mongescreen.previews.conicsarcs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.orth.conics.drawConicHyperbolaNarys
import draw.mongescreen.objects.orth.conics.drawConicHyperbolaPudorys
import model.*
import model.axo.AxoMode
import model.classes.ConicSection3D
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.PlaneEquation
import monge.input.ConicArcs.associated.liftNarysPointTo3DAndPudorys
import monge.input.ConicArcs.associated.liftPudorysPointTo3DAndNarys
import monge.input.ConicArcs.single.getLogicalCursorNarys
import monge.input.axo.getLogicalCursorAxo
import monge.input.planeobjects.conicsections.conicCenter2D
import monge.input.planeobjects.conicsections.extremeEnds2D
import monge.input.planeobjects.conicsections.liftXYtoPlane
import monge.input.planeobjects.conicsections.liftXZtoPlane
import geometry.conics.cosh
import geometry.conics.sinh
import state.MongeState
import state.snapMonge.computeIntersection
import utils.dot
import utils.getLogicalCursor
import utils.normalize
import utils.toScreenOld
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

data class HyperbolaBasis(
    val center: Offset,
    val ex: Offset,   // hlavnĂ­ osa (unit), smÄ›r na vrchol
    val ey: Offset,   // kolmĂˇ (unit)
    val a: Float,
    val b: Float
)

/** Z tvĂ˝ch vstupĹŻ (stĹ™ed, asymptoty, vrchol, axis) spoÄŤte lokĂˇlnĂ­ bĂˇzi a,b,ex,ey. */
fun hyperbolaBasisFrom(
    center: Offset,
    vertex: Offset,
    asymptote1Dir: Offset, // uĹľ unit, jinak normalizuj
    axis: Offset
): HyperbolaBasis {
    val vc = vertex - center
    val vcLen = vc.getDistance()
    val ex = if (vcLen >= 1e-6f) {
        vc / vcLen
    } else {
        val axisRaw = axis.normalize()
        if ((vc).dot(axisRaw) < 0f) -axisRaw else axisRaw
    }
    val ey = Offset(-ex.y, ex.x)
    val a  = if (vcLen >= 1e-6f) vcLen else 1e-6f

    val v1 = asymptote1Dir.normalize()
    val v1x = v1.dot(ex)
    val v1y = v1.dot(ey)
    val safeV1x = if (abs(v1x) < 1e-6f) 1e-6f * (if (v1x >= 0f) 1f else -1f) else v1x
    val slope = v1y / safeV1x
    val b = (a * abs(slope)).coerceAtLeast(1e-6f)


    return HyperbolaBasis(center, ex, ey, a, b)
}

data class HyperProj(val u: Float, val on: Offset, val sX: Int)


// StabilnĂ­ varianty nezĂˇvislĂ© na JVM/preview
private fun asinhf(x: Float): Float {
    val xd = x.toDouble()
    val y = kotlin.math.ln(xd + kotlin.math.sqrt(xd*xd + 1.0))
    return y.toFloat()
}
private fun acoshf(x: Float): Float {
    val xd = x.toDouble()
    if (xd < 1.0) return 0f  // ochrana proti NaN; pro |x|<1 je acosh nedefinovanĂ© â†’ vraĹĄ 0 jako fallback start
    val y = kotlin.math.ln(xd + kotlin.math.sqrt((xd + 1.0) * (xd - 1.0)))
    return y.toFloat()
}
fun projectToHyperbola(
    basis: HyperbolaBasis,
    pt: Offset,
    forcedSX: Int // +1 pravĂˇ, -1 levĂˇ
): HyperProj? {
    // 0) sanity
    if (!pt.x.isFinite() || !pt.y.isFinite()) return null
    val (C, ex, ey, aRaw, bRaw) = basis
    if (!C.x.isFinite() || !C.y.isFinite()) return null

    // 1) ochrana proti degeneraci bĂˇze (kolaps projekce)
    val EPS = 1e-7f
    val a = if (abs(aRaw) < EPS) return null else aRaw
    val b = if (abs(bRaw) < EPS) return null else bRaw

    val r  = pt - C
    val qx = r.dot(ex)
    val qy = r.dot(ey)
    val sX = if (forcedSX >= 0) +1 else -1

    // 2) robustnĂ­ start (bez JVM asinh/acosh)
    //    - y ~ b*sinh(u)  â†’ u â‰ asinh(qy/b)
    //    - |x| ~ a*cosh(u)â†’ u â‰ acosh(|qx|/a), ale argument musĂ­ bĂ˝t â‰Ą1
    val uFromY = if (abs(b) > EPS)
        asinhf((qy / b).coerceIn(-1e6f, 1e6f))
    else 0f

    val xnorm = if (abs(a) > EPS) (abs(qx) / a) else 1f
    val uFromX = acoshf(max(1f, xnorm))

    var u = clampU(
        if (abs(uFromY) > 0.1f)
            kotlin.math.sign(uFromY) * uFromX
        else
            uFromY
    )

    // 3) funkce a derivace (pozor na pĹ™eteÄŤenĂ­ â†’ clampU drĹľĂ­ u v rozumnĂ©m rozsahu)
    fun f(u: Float): Float {
        val sh = sinh(u); val ch = cosh(u)
        return (sX * a * qx) * sh + (b * qy) * ch - (a*a + b*b) * sh * ch
    }
    fun fp(u: Float): Float {
        val sh = sinh(u); val ch = cosh(u)
        // ch^2 + sh^2 = cosh(2u), ale nechĂˇme explicitnÄ› kvĹŻli ÄŤitelnosti
        return (sX * a * qx) * ch + (b * qy) * sh - (a*a + b*b) * (ch*ch + sh*sh)
    }

    // 4) tlumenĂ˝ Newton s kontrolou
    repeat(8) {
        val den = fp(u)
        val num = f(u)
        if (!den.isFinite() || !num.isFinite()) return@repeat
        if (abs(den) < 1e-6f) return@repeat
        val step = (num / den).coerceIn(-1.0f, 1.0f)
        val uNext = clampU(u - step)
        if (!uNext.isFinite()) return@repeat
        if (abs(uNext - u) < 1e-4f) { u = uNext; return@repeat }
        u = uNext
    }

    // 5) vĂ˝stupnĂ­ bod
    val ch = cosh(u); val sh = sinh(u)
    val x = sX * a * ch
    val y = b * sh
    val on = C + ex * x + ey * y

    return if (on.x.isFinite() && on.y.isFinite()) HyperProj(u, on, sX) else null
}
fun DrawScope.drawHyperbolaBranchArcPudorys(
    basis: HyperbolaBasis,
    A: Offset,
    B: Offset,
    forcedBranchSX: Int,  // +1 nebo -1
    canvasOffset: Offset,
    scale: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    du: Float = 0.05f
) {
    val pA = projectToHyperbola(basis, A, forcedBranchSX) ?: return
    val pB = projectToHyperbola(basis, B, forcedBranchSX) ?: return

    val u1 = clampU(pA.u)
    var u2 = clampU(pB.u)
    if (!u1.isFinite() || !u2.isFinite()) return
    if (abs(u2 - u1) < 1e-5f) u2 = u1 + 1e-3f

    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    val steps = max(2, ceil(L / du).toInt())

    val (C, ex, ey, a, b) = basis
    val path = Path()

    // start
    run {
        val ch = cosh(u1); val sh = sinh(u1)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val pt = C + ex * x + ey * y
        val s = pt.toScreenOld(scale, canvasOffset)
        if (!s.x.isFinite() || !s.y.isFinite()) return
        path.moveTo(s.x, s.y)
    }

    // mezibody
    for (i in 1 until steps) {
        val u = clampU(u1 + dir * (i * (L / steps)))
        val ch = cosh(u); val sh = sinh(u)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val pt = C + ex * x + ey * y
        if (!pt.x.isFinite() || !pt.y.isFinite()) continue
        val s = pt.toScreenOld(scale, canvasOffset)
        if (!s.x.isFinite() || !s.y.isFinite()) continue
        path.lineTo(s.x, s.y)
    }

    // end
    run {
        val ch = cosh(u2); val sh = sinh(u2)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val pt = C + ex * x + ey * y
        val s = pt.toScreenOld(scale, canvasOffset)
        if (!s.x.isFinite() || !s.y.isFinite()) return
        path.lineTo(s.x, s.y)
    }

    val pe = when (lineStyle) {
        LineStyle.Solid  -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot-> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, pathEffect = pe))
}


fun MongeState.setHyperbolaBranch1P(conicId: String, a: Offset, b: Offset) {
    hyperbolaArcBranch1[conicId] = a to b; triggerRedraw++
}
fun MongeState.setHyperbolaBranch2P(conicId: String, a: Offset, b: Offset) {
    hyperbolaArcBranch2[conicId] = a to b; triggerRedraw++
}

fun DrawScope.drawHyperbolaArcPreviewPudorys(state: MongeState, snappedPointLogical: Offset?) {
    if ((state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS) || state.mongeMode != DrawingModeMonge.PUDORYS) return
    val conicId = state.activeConicIdForArc ?: return
    val input = state.hyperbolaInputsPudorys[conicId] ?: return

    // sestav bĂˇzi (stejnÄ› jako v tvĂ©m draweru)
    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex

    val basis = hyperbolaBasisFrom(
        center = center,
        vertex = input.vertex,
        asymptote1Dir = v1,
        axis = input.axis
    )

    val cursor = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {
        "pudorys_hyp_second_hold" -> {
            val A1 = state.pendingHypA1 ?: return
            val sX = if ((A1 - basis.center).dot(basis.ex) >= 0f) +1 else -1
            drawConicHyperbolaPudorys(
                center = basis.center,
                asymptote1 = v1,
                vertex = input.vertex,
                canvasOffset = state.canvasOffset, scale = state.scale,
                color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed,
                axis = input.axis
            )

            // nĂˇhled vÄ›tve 1: A1 â†’ cursor
            drawHyperbolaBranchArcPudorys(
                basis = basis,
                A = A1, B = cursor,
                forcedBranchSX = sX,
                canvasOffset = state.canvasOffset, scale = state.scale,
                color = Color.Blue, strokeWidth =  (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
                lineStyle = LineStyle.Solid
            )
        }

        "pudorys_hyp_second" -> {
            val A2 = state.pendingHypA2 ?: return
            val sX = if ((A2 - basis.center).dot(basis.ex) >= 0f) +1 else -1

            // ĹˇedĂ© pozadĂ­
            drawConicHyperbolaPudorys(
                center = basis.center,
                asymptote1 = v1,
                vertex = input.vertex,
                canvasOffset = state.canvasOffset, scale = state.scale,
                color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed,
                axis = input.axis
            )

            // nĂˇhled vÄ›tve 2
            drawHyperbolaBranchArcPudorys(
                basis = basis,
                A = A2, B = cursor,
                forcedBranchSX = sX,
                canvasOffset = state.canvasOffset, scale = state.scale,
                color = Color.Blue, strokeWidth =  (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
                lineStyle = LineStyle.Solid
            )
        }
    }
}

private const val HYP_U_MAX = 6f // nĂˇhled / interaktivnĂ­ kreslenĂ­

private fun clampU(u: Float) = u.coerceIn(-HYP_U_MAX, HYP_U_MAX)

fun DrawScope.drawHyperbolaBranchArcNarys(
    basis: HyperbolaBasis,
    A: Offset,
    B: Offset,
    forcedBranchSX: Int,  // +1 pravĂˇ (x>0 v lokĂˇlnĂ­m systĂ©mu), -1 levĂˇ
    canvasOffset: Offset,
    scale: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    du: Float = 0.05f
) {
    val pA = projectToHyperbola(basis, A, forcedBranchSX) ?: return
    val pB = projectToHyperbola(basis, B, forcedBranchSX) ?: return

    val u1 = clampU(pA.u)
    var u2 = clampU(pB.u)
    if (!u1.isFinite() || !u2.isFinite()) return
    if (abs(u2 - u1) < 1e-5f) u2 = u1 + 1e-3f

    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    val steps = max(2, ceil(L / du).toInt())

    val (C, ex, ey, a, b) = basis
    val path = Path()

    // start
    run {
        val ch = cosh(u1); val sh = sinh(u1)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val s = (C + ex * x + ey * y).toScreenNarys(scale, canvasOffset)
        if (!s.x.isFinite() || !s.y.isFinite()) return
        path.moveTo(s.x, s.y)
    }

    // mezibody
    for (i in 1 until steps) {
        val u = clampU(u1 + dir * (i * (L / steps)))
        val ch = cosh(u); val sh = sinh(u)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val s = (C + ex * x + ey * y).toScreenNarys(scale, canvasOffset)
        if (!s.x.isFinite() || !s.y.isFinite()) continue
        path.lineTo(s.x, s.y)
    }

    // end
    run {
        val ch = cosh(u2); val sh = sinh(u2)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val s = (C + ex * x + ey * y).toScreenNarys(scale, canvasOffset)
        if (!s.x.isFinite() || !s.y.isFinite()) return
        path.lineTo(s.x, s.y)
    }

    val pe = when (lineStyle) {
        LineStyle.Solid  -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot-> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, pathEffect = pe))
}
fun DrawScope.drawHyperbolaArcPreviewNarys(state: MongeState, snappedPointLogical: Offset?) {
    if ((state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS)|| state.mongeMode != DrawingModeMonge.NARYS) return

    val conicId = state.activeConicIdForArc ?: return
    val input = state.hyperbolaInputsNarys[conicId] ?: return

    val v1 = input.line1.direction.normalize()
    val center =computeIntersection(
        Offset(input.line1.point.x, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.x, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex

    val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
    val cursor = getLogicalCursorNarys(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection
    )

    when (state.projectionPhase) {
        "narys_hyp_second_hold" -> {
            val A1 = state.pendingHypA1 ?: return
            val sX = if ((A1 - basis.center).dot(basis.ex) >= 0f) +1 else -1

            // pozadĂ­ celĂ© hyperboly (slabÄ›) â€“ pouĹľij tvou NARYS kresbu
            drawConicHyperbolaNarys(
                center = basis.center, asymptote1 = v1, vertex = input.vertex,
                canvasOffset = state.canvasOffset, scale = state.scale,
                color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed,
                axis = input.axis
            )

            // nĂˇhled vÄ›tve 1
            drawHyperbolaBranchArcNarys(
                basis = basis, A = A1, B = cursor, forcedBranchSX = sX,
                canvasOffset = state.canvasOffset, scale = state.scale,
                color = Color.Blue, strokeWidth = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
                lineStyle = LineStyle.Solid
            )
        }
        "narys_hyp_second" -> {
            val A2 = state.pendingHypA2 ?: return
            val sX = if ((A2 - basis.center).dot(basis.ex) >= 0f) +1 else -1

            drawConicHyperbolaNarys(
                center = basis.center, asymptote1 = v1, vertex = input.vertex,
                canvasOffset = state.canvasOffset, scale = state.scale,
                color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed,
                axis = input.axis
            )
            drawHyperbolaBranchArcNarys(
                basis = basis, A = A2, B = cursor, forcedBranchSX = sX,
                canvasOffset = state.canvasOffset, scale = state.scale,
                color = Color.Blue, strokeWidth = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
                lineStyle = LineStyle.Solid
            )
        }
    }
}

fun DrawScope.drawHyperbolaArcPreviewBokorys(state: MongeState, snappedPointLogical: Offset?) {
    if ((state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS) ||
        state.axoMode != AxoMode.AXO_BOKORYS) return

    val conicId = state.activeConicIdForArc ?: state.selectedConicsBokorys.lastOrNull()?.id ?: return
    val input = state.hyperbolaInputsBokorys[conicId] ?: return

    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.y, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.y, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex

    val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
    val cursor = getLogicalCursorAxo(
        snapped = snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_BOKORYS,
        axoModel = state.activeAxoModel
    ) ?: return

    when (state.projectionPhase) {
        "bokorys_hyp_second_hold" -> {
            val A1 = state.pendingHypA1 ?: return
            val sX = if ((A1 - basis.center).dot(basis.ex) >= 0f) +1 else -1
            drawHyperbolaBranchArcBokorys(
                basis = basis,
                A = A1,
                B = cursor,
                forcedBranchSX = sX,
                color = Color.Blue,
                strokeWidth = (state.selectedConicsBokorys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
                lineStyle = LineStyle.Solid
            )
        }

        "bokorys_hyp_second" -> {
            val A2 = state.pendingHypA2 ?: return
            val sX = if ((A2 - basis.center).dot(basis.ex) >= 0f) +1 else -1
            drawHyperbolaBranchArcBokorys(
                basis = basis,
                A = A2,
                B = cursor,
                forcedBranchSX = sX,
                color = Color.Blue,
                strokeWidth = (state.selectedConicsBokorys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
                lineStyle = LineStyle.Solid
            )
        }
    }
}

data class HyperbolaPreview3D(
    val pConicId: String?,           // id sesterskĂ© hyperboly v P (pokud existuje)
    val nConicId: String?,           // id sesterskĂ© hyperboly v N (pokud existuje)
    val A_p: Offset?, val B_p: Offset?,   // konce nĂˇhledu v P
    val A_n: Offset?, val B_n: Offset?,   // konce nĂˇhledu v N
    val branchSX_P: Int?,                 // znamĂ©nko vÄ›tve v P pro vykreslenĂ­ (Â±1)
    val branchSX_N: Int?,
    val wrapsVertex: Boolean = false,
    val arcEnds3D: Pair<Offset3D, Offset3D>? = null
)
private fun MongeState.findPudorysHyperbolaIdByParent(parentId: String): String? =
    conicsPudorys.firstOrNull { it.parent?.id == parentId }?.id

private fun MongeState.findNarysHyperbolaIdByParent(parentId: String): String? =
    conicsNarys.firstOrNull { it.parent?.id == parentId }?.id

private fun buildHyperbolaBasis2D_P(state: MongeState, pudorysId: String): HyperbolaBasis? {
    val input = state.hyperbolaInputsPudorys[pudorysId] ?: return null
    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex
    return hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
}

private fun buildHyperbolaBasis2D_N(state: MongeState, narysId: String): HyperbolaBasis? {
    val input = state.hyperbolaInputsNarys[narysId] ?: return null
    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.x, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.x, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex
    return hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
}

private fun buildHyperbolaBasis2D_B(state: MongeState, bokorysId: String): HyperbolaBasis? {
    val input = state.hyperbolaInputsBokorys[bokorysId] ?: return null
    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.y, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.y, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex
    return hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
}

private fun snapPudorysPointToHyperbolaOnBranch(basis: HyperbolaBasis, p: Offset, sx: Int): Offset =
    projectToHyperbola(basis, p, sx)?.on ?: p

private fun snapNarysPointToHyperbolaOnBranch(basis: HyperbolaBasis, p: Offset, sx: Int): Offset =
    projectToHyperbola(basis, p, sx)?.on ?: p

private fun snapBokorysPointToHyperbolaOnBranch(basis: HyperbolaBasis, p: Offset, sx: Int): Offset =
    projectToHyperbola(basis, p, sx)?.on ?: p

fun DrawScope.drawHyperbolaBranchArcBokorys(
    basis: HyperbolaBasis,
    A: Offset,
    B: Offset,
    forcedBranchSX: Int,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    du: Float = 0.05f
) {
    val pA = projectToHyperbola(basis, A, forcedBranchSX) ?: return
    val pB = projectToHyperbola(basis, B, forcedBranchSX) ?: return

    val u1 = clampU(pA.u)
    var u2 = clampU(pB.u)
    if (!u1.isFinite() || !u2.isFinite()) return
    if (abs(u2 - u1) < 1e-5f) u2 = u1 + 1e-3f

    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    val steps = max(2, ceil(L / du).toInt())

    val (C, ex, ey, a, b) = basis
    val path = Path()

    run {
        val ch = cosh(u1); val sh = sinh(u1)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val pt = C + ex * x + ey * y
        if (!pt.x.isFinite() || !pt.y.isFinite()) return
        path.moveTo(pt.x, pt.y)
    }

    for (i in 1 until steps) {
        val u = clampU(u1 + dir * (i * (L / steps)))
        val ch = cosh(u); val sh = sinh(u)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val pt = C + ex * x + ey * y
        if (!pt.x.isFinite() || !pt.y.isFinite()) continue
        path.lineTo(pt.x, pt.y)
    }

    run {
        val ch = cosh(u2); val sh = sinh(u2)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        val pt = C + ex * x + ey * y
        if (!pt.x.isFinite() || !pt.y.isFinite()) return
        path.lineTo(pt.x, pt.y)
    }

    val pe = when (lineStyle) {
        LineStyle.Solid -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot -> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, pathEffect = pe))
}

fun DrawScope.drawHyperbolaBranchArcBokorysDegenerate(
    origin: Offset,
    dirIn: Offset,
    isLine: Boolean,
    A: Offset,
    B: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    var dir = dirIn
    val len = dir.getDistance()
    dir = if (len < 1e-6f) Offset(1f, 0f) else dir / len

    fun projT(p: Offset): Float = (p.x - origin.x) * dir.x + (p.y - origin.y) * dir.y
    var tA = projT(A)
    var tB = projT(B)
    if (!isLine) {
        if (tA < 0f) tA = 0f
        if (tB < 0f) tB = 0f
    }
    val p1 = origin + dir * tA
    val p2 = origin + dir * tB
    if ((p2 - p1).getDistance() < 1e-6f) return

    val pe = when (lineStyle) {
        LineStyle.Solid -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot -> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }
    drawLine(
        color = color,
        start = p1,
        end = p2,
        strokeWidth = strokeWidth,
        pathEffect = pe
    )
}

private fun hyperbolaWrapsVertex(parent: ConicSection3D, a: Offset3D, b: Offset3D): Boolean {
    val (tA, tB) = hyperbolaParams(parent, a, b) ?: return false
    return tA.isFinite() && tB.isFinite() && tA * tB < 0f
}

private data class HyperbolaFrame3D(val u: Offset3D, val v: Offset3D, val a: Float, val b: Float)

private fun hyperbolaFrame(parent: ConicSection3D): HyperbolaFrame3D? {
    val aLen = parent.a ?: return null
    val bLen = parent.b ?: return null
    if (abs(aLen) < 1e-6f || abs(bLen) < 1e-6f) return null
    val uN = parent.u.normalized()
    val vGS = parent.v - uN * parent.v.dot(uN)
    val vN = vGS.normalized()
    return HyperbolaFrame3D(uN, vN, aLen, bLen)
}

private fun hyperbolaParams(parent: ConicSection3D, a: Offset3D, b: Offset3D): Pair<Float, Float>? {
    val frame = hyperbolaFrame(parent) ?: return null
    val tA = kotlin.math.asinh(((a - parent.p0).dot(frame.v) / frame.b).toDouble()).toFloat()
    val tB = kotlin.math.asinh(((b - parent.p0).dot(frame.v) / frame.b).toDouble()).toFloat()
    return if (tA.isFinite() && tB.isFinite()) tA to tB else null
}

private fun hyperbolaBranchSign(parent: ConicSection3D, p: Offset3D): Float {
    val frame = hyperbolaFrame(parent) ?: return 1f
    return if ((p - parent.p0).dot(frame.u) >= 0f) 1f else -1f
}

private fun hyperbolaPointAt(parent: ConicSection3D, t: Float, sx: Float): Offset3D? {
    val frame = hyperbolaFrame(parent) ?: return null
    val ch = cosh(t)
    val sh = sinh(t)
    if (!ch.isFinite() || !sh.isFinite()) return null
    return parent.p0 + frame.u * (sx * frame.a * ch) + frame.v * (frame.b * sh)
}

private fun computeHyperbolaArcPreview3D(
    state: MongeState,
    cursorP: Offset,                // getLogicalCursor(...)
    cursorN: Offset                 // getLogicalCursorNarys(...)
): HyperbolaPreview3D {
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // P-PRVNĂŤ (A1â†’B1 nebo A2â†’B2)
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    if (state.projectionPhase == "pudorys_hyp3d_b1_hold" ||state.projectionPhase == "pudorys_hyp3d_b1" ||
        state.projectionPhase == "pudorys_hyp3d_a2" ||
        state.projectionPhase == "pudorys_hyp3d_b2") {

        val pId = state.activeConicIdForArc ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val conicP = state.conicsPudorys.firstOrNull { it.id == pId } ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val parentId = state.activeParentConic3DIdForEllipseArc ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)

        val inputP = state.hyperbolaInputsPudorys[pId] ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val v1 = inputP.line1.direction.normalize()
        val centerP = computeIntersection(
            Offset(inputP.line1.point.x, inputP.line1.point.y), Offset(inputP.line1.direction.x, inputP.line1.direction.y),
            Offset(inputP.line2.point.x, inputP.line2.point.y), Offset(inputP.line2.direction.x, inputP.line2.direction.y)
        ) ?: inputP.vertex
        val basisP = hyperbolaBasisFrom(centerP, inputP.vertex, v1, inputP.axis)

        val isBranch1 = (state.projectionPhase == "pudorys_hyp3d_b1")
        val A_p = if (isBranch1) state.pendingHypA1 else state.pendingHypA2
            ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val sx = if (isBranch1) (state.pendingHypBranch1SX ?: +1) else (state.pendingHypBranch2SX ?: +1)

        val B_p = if (conicP.isDegenerate) {
            state.snapPudorysDegenerateHyperbola(pId, cursorP, sx)?.point ?: cursorP
        } else {
            snapPudorysPointToHyperbolaOnBranch(basisP, cursorP, sx)
        }

        // P -> 3D -> N (nĂˇrysovĂ˝ â€žplaneâ€ś bod dostaneme z liftu)
        val (A3D, A_n_plane) = liftPudorysPointTo3DAndNarys(A_p!!.x, A_p.y, parent) ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val (B3D, B_n_plane) = liftPudorysPointTo3DAndNarys(B_p.x, B_p.y, parent) ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)

        // dosnap v N (pokud sesterskĂˇ hyperbola existuje)
        val nId = state.findNarysHyperbolaIdByParent(parent.id)
        val (A_n, B_n, sxN) = if (nId != null) {
            val conicN = state.conicsNarys.firstOrNull { it.id == nId }
            val basisN = buildHyperbolaBasis2D_N(state, nId)
            if (conicN?.isDegenerate == true) {
                val aSnap = state.snapNarysDegenerateHyperbola(nId, A_n_plane)
                val bSnap = state.snapNarysDegenerateHyperbola(nId, B_n_plane, aSnap?.side)
                Triple(aSnap?.point ?: A_n_plane, bSnap?.point ?: B_n_plane, aSnap?.side)
            } else if (basisN != null) {
                // vÄ›tev v N drĹľĂ­me konzistentnÄ› podle A_n_plane
                val sxNdet = if ((A_n_plane - basisN.center).dot(basisN.ex) >= 0f) +1 else -1
                val A_on = snapNarysPointToHyperbolaOnBranch(basisN, A_n_plane, sxNdet)
                val B_on = snapNarysPointToHyperbolaOnBranch(basisN, B_n_plane, sxNdet)
                Triple(A_on, B_on, sxNdet)
            } else Triple(A_n_plane, B_n_plane, null)
        } else Triple(A_n_plane, B_n_plane, null)

        return HyperbolaPreview3D(
            pConicId = pId, nConicId = nId,
            A_p = A_p, B_p = B_p,
            A_n = A_n, B_n = B_n,
            branchSX_P = sx, branchSX_N = sxN,
            wrapsVertex = hyperbolaWrapsVertex(parent, A3D, B3D),
            arcEnds3D = A3D to B3D
        )
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // N-PRVNĂŤ (A1â†’B1 nebo A2â†’B2)
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    if (state.projectionPhase == "narys_hyp3d_b1" ||state.projectionPhase == "narys_hyp3d_b1_hold" ||
        state.projectionPhase == "narys_hyp3d_a2" ||
        state.projectionPhase == "narys_hyp3d_b2") {

        val nId = state.activeConicIdForArc ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val conicN = state.conicsNarys.firstOrNull { it.id == nId } ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val parentId = state.activeParentConic3DIdForEllipseArc ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)

        val inputN = state.hyperbolaInputsNarys[nId] ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val v1 = inputN.line1.direction.normalize()
        val centerN = computeIntersection(
            Offset(inputN.line1.point.x, inputN.line1.point.z), Offset(inputN.line1.direction.x, inputN.line1.direction.y),
            Offset(inputN.line2.point.x, inputN.line2.point.z), Offset(inputN.line2.direction.x, inputN.line2.direction.y)
        ) ?: inputN.vertex
        val basisN = hyperbolaBasisFrom(centerN, inputN.vertex, v1, inputN.axis)

        val isBranch1 = (state.projectionPhase == "narys_hyp3d_b1")
        val A_n = if (isBranch1) state.pendingHypA1 else state.pendingHypA2
            ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val sx = if (isBranch1) (state.pendingHypBranch1SX ?: +1) else (state.pendingHypBranch2SX ?: +1)

        val B_n = if (conicN.isDegenerate) {
            state.snapNarysDegenerateHyperbola(nId, cursorN, sx)?.point ?: cursorN
        } else {
            snapNarysPointToHyperbolaOnBranch(basisN, cursorN, sx)
        }

        // N -> 3D -> P
        val (A3D, A_p_plane) = liftNarysPointTo3DAndPudorys(A_n!!.x, A_n.y, parent) ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
        val (B3D, B_p_plane) = liftNarysPointTo3DAndPudorys(B_n.x, B_n.y, parent) ?: return HyperbolaPreview3D(null,null,null,null,null,null,null,null)

        // dosnap v P (pokud sesterskĂˇ hyperbola existuje)
        val pId = state.findPudorysHyperbolaIdByParent(parent.id)
        val (A_p, B_p, sxP) = if (pId != null) {
            val conicP = state.conicsPudorys.firstOrNull { it.id == pId }
            val basisP = buildHyperbolaBasis2D_P(state, pId)
            if (conicP?.isDegenerate == true) {
                val aSnap = state.snapPudorysDegenerateHyperbola(pId, A_p_plane)
                val bSnap = state.snapPudorysDegenerateHyperbola(pId, B_p_plane, aSnap?.side)
                Triple(aSnap?.point ?: A_p_plane, bSnap?.point ?: B_p_plane, aSnap?.side)
            } else if (basisP != null) {
                val sxPdet = if ((A_p_plane - basisP.center).dot(basisP.ex) >= 0f) +1 else -1
                val A_on = snapPudorysPointToHyperbolaOnBranch(basisP, A_p_plane, sxPdet)
                val B_on = snapPudorysPointToHyperbolaOnBranch(basisP, B_p_plane, sxPdet)
                Triple(A_on, B_on, sxPdet)
            } else Triple(A_p_plane, B_p_plane, null)
        } else Triple(A_p_plane, B_p_plane, null)

        return HyperbolaPreview3D(
            pConicId = pId, nConicId = nId,
            A_p = A_p, B_p = B_p,
            A_n = A_n, B_n = B_n,
            branchSX_P = sxP, branchSX_N = sx,
            wrapsVertex = hyperbolaWrapsVertex(parent, A3D, B3D),
            arcEnds3D = A3D to B3D
        )
    }

    return HyperbolaPreview3D(null,null,null,null,null,null,null,null)
}
fun DrawScope.drawHyperbolaArcPreviewPudorys3D(state: MongeState, snappedPointLogical: Offset?) {
    if (state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS)return

    val cursorP = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val cursorN = getLogicalCursorNarys(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection
    )

    val prev = computeHyperbolaArcPreview3D(state, cursorP, cursorN)
    val conicId = prev.pConicId ?: return
    val A = prev.A_p ?: return
    val B = prev.B_p ?: return
    val sx = prev.branchSX_P ?: run {
        // fallback â€“ urÄŤ sĂˇm podle bĂˇze
        val basis = buildHyperbolaBasis2D_P(state, conicId) ?: return
        if ((A - basis.center).dot(basis.ex) >= 0f) +1 else -1
    }

    val input = state.hyperbolaInputsPudorys[conicId] ?: return
    val conicP = state.conicsPudorys.firstOrNull { it.id == conicId }
    if (conicP?.isDegenerate == true) {
        val parent = conicP.parent ?: return
        val rawEq = planeEquationFromConic3D(parent)
        drawHyperbolaBranchArcPudorysDegenerate(
            conicP = conicP,
            A = A,
            B = B,
            eq = PlaneEquation(rawEq.a, rawEq.b, rawEq.c, rawEq.d),
            state = state,
            color = Color.Blue,
            strokeWidth = (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
            showHelpConic = false,
            pxPerPt = 1f,
            wrapsVertex = prev.wrapsVertex,
            arcEnds3D = prev.arcEnds3D
        )
        return
    }
    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex
    val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)

    drawHyperbolaBranchArcPudorys(
        basis = basis,
        A = A, B = B,
        forcedBranchSX = sx,
        canvasOffset = state.canvasOffset, scale = state.scale,
        color = Color.Blue,
        strokeWidth = (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
        lineStyle = LineStyle.Solid
    )
}
fun DrawScope.drawHyperbolaArcPreviewNarys3D(state: MongeState, snappedPointLogical: Offset?) {
    if (state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS) return

    val cursorP = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val cursorN = getLogicalCursorNarys(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection
    )

    val prev = computeHyperbolaArcPreview3D(state, cursorP, cursorN)
    val conicId = prev.nConicId ?: return
    val A = prev.A_n ?: return
    val B = prev.B_n ?: return
    val sx = prev.branchSX_N ?: run {
        val basis = buildHyperbolaBasis2D_N(state, conicId) ?: return
        if ((A - basis.center).dot(basis.ex) >= 0f) +1 else -1
    }

    val input = state.hyperbolaInputsNarys[conicId] ?: return
    val conicN = state.conicsNarys.firstOrNull { it.id == conicId }
    if (conicN?.isDegenerate == true) {
        drawHyperbolaBranchArcNarysDegenerate(
            conicN = conicN,
            A = A,
            B = B,
            state = state,
            color = Color.Blue,
            strokeWidth = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
            showHelpConic = false,
            pxPerPt = 1f,
            wrapsVertex = prev.wrapsVertex,
            arcEnds3D = prev.arcEnds3D
        )
        return
    }
    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.x, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.x, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex
    val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)

    drawHyperbolaBranchArcNarys(
        basis = basis,
        A = A, B = B,
        forcedBranchSX = sx,
        canvasOffset = state.canvasOffset, scale = state.scale,
        color = Color.Blue,
        strokeWidth = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 5f,
        lineStyle = LineStyle.Solid
    )
}

private fun Offset.toScreenOrNull(scale: Float, canvasOffset: Offset): Offset? {
    val sx = x * scale + canvasOffset.x
    val sy = y * scale + canvasOffset.y
    return if (sx.isFinite() && sy.isFinite()) Offset(sx, sy) else null
}

data class DegenerateHyperbolaSnap(val point: Offset, val side: Int)

private fun safeUnit(v: Offset, fallback: Offset = Offset(1f, 0f)): Offset {
    val len = v.getDistance()
    return if (!len.isFinite() || len < 1e-6f) fallback else v / len
}

private fun invS2D(a: Float, b: Float, c: Float): Triple<Float, Float, Float>? {
    val bh = b * 0.5f
    val det = a * c - bh * bh
    if (!det.isFinite() || abs(det) < 1e-12f) return null
    return Triple(c / det, -bh / det, a / det)
}

private fun conicCenter2DLocal(coeff: ConicCoeffs): Offset? {
    val (a, b, c, d, e, _) = coeff
    val (ia, ib, ic) = invS2D(a, b, c) ?: return null
    return Offset(-0.5f * (ia * d + ib * e), -0.5f * (ib * d + ic * e))
}

private fun f0ShiftedLocal(coeff: ConicCoeffs): Float {
    val (a, b, c, d, e, f) = coeff
    val (ia, ib, ic) = invS2D(a, b, c) ?: return f
    return f - 0.25f * (ia * d * d + 2f * ib * d * e + ic * e * e)
}

private fun extremeEnds2DLocal(coeff: ConicCoeffs, eUnit: Offset): Pair<Offset, Offset>? {
    val c0 = conicCenter2DLocal(coeff) ?: return null
    val (a, b, c, _, _, _) = coeff
    val (ia, ib, ic) = invS2D(a, b, c) ?: return null
    val ex = eUnit.x
    val ey = eUnit.y
    val s = ia * ex * ex + 2f * ib * ex * ey + ic * ey * ey
    if (!s.isFinite() || abs(s) < 1e-12f) return null
    val rad = -f0ShiftedLocal(coeff) / s
    if (!rad.isFinite() || rad <= 1e-12f) return null
    val k = kotlin.math.sqrt(rad)
    val vx = (ia * ex + ib * ey) * k
    val vy = (ib * ex + ic * ey) * k
    return Offset(c0.x + vx, c0.y + vy) to Offset(c0.x - vx, c0.y - vy)
}

private fun projectToDegenerateRays(
    center: Offset,
    dirIn: Offset,
    end1: Offset,
    end2: Offset,
    isLine: Boolean,
    point: Offset,
    preferredSide: Int? = null
): DegenerateHyperbolaSnap {
    val dir = safeUnit(dirIn)
    if (isLine || (end1 - end2).getDistance() < 1e-6f) {
        val t = (point - center).dot(dir)
        return DegenerateHyperbolaSnap(center + dir * t, 0)
    }

    fun sideOf(e: Offset): Int = if ((e - center).dot(dir) >= 0f) +1 else -1
    fun projectToEnd(e: Offset): DegenerateHyperbolaSnap {
        val side = sideOf(e)
        val rayDir = dir * side.toFloat()
        val t = max(0f, (point - e).dot(rayDir))
        return DegenerateHyperbolaSnap(e + rayDir * t, side)
    }

    if (preferredSide == +1 || preferredSide == -1) {
        val e = if (sideOf(end1) == preferredSide) end1 else end2
        return projectToEnd(e)
    }

    val q1 = projectToEnd(end1)
    val q2 = projectToEnd(end2)
    return if ((point - q1.point).getDistanceSquared() <= (point - q2.point).getDistanceSquared()) q1 else q2
}

fun MongeState.snapPudorysDegenerateHyperbola(
    conicId: String,
    point: Offset,
    preferredSide: Int? = null
): DegenerateHyperbolaSnap? {
    val conicP = conicsPudorys.firstOrNull { it.id == conicId } ?: return null
    if (!conicP.isDegenerate) return null
    val parent = conicP.parent ?: return null
    val rawEq = planeEquationFromConic3D(parent)
    val eq = PlaneEquation(rawEq.a, rawEq.b, rawEq.c, rawEq.d)
    val coeffP = ConicCoeffs(conicP.a, conicP.b, conicP.c, conicP.d, conicP.e, conicP.f)
    val input = hyperbolaInputsPudorys[conicId]
    val center = conicCenter2DLocal(coeffP) ?: input?.let {
        computeIntersection(
            Offset(it.line1.point.x, it.line1.point.y), Offset(it.line1.direction.x, it.line1.direction.y),
            Offset(it.line2.point.x, it.line2.point.y), Offset(it.line2.direction.x, it.line2.direction.y)
        ) ?: it.vertex
    } ?: point

    val n = Offset3D(eq.a, eq.b, eq.c).normalize()
    var u3 = n.cross(Offset3D(0f, 0f, 1f))
    val u3Len = kotlin.math.sqrt(u3.x * u3.x + u3.y * u3.y + u3.z * u3.z)
    if (!u3Len.isFinite() || u3Len < 1e-12f) u3 = Offset3D(0f, 1f, 0f)
    val uXY = safeUnit(Offset(u3.x, u3.y), conicP.degenerateDir ?: Offset(1f, 0f))

    val sisterN = conicsNarys.firstOrNull { it.parent?.id == parent.id }
    val ends = sisterN?.let {
        val coeffN = ConicCoeffs(it.a, it.b, it.c, it.d, it.e, it.f)
        val eXZ = safeUnit(Offset(u3.x, u3.z))
        extremeEnds2D(coeffN, eXZ)?.let { (pPlusXZ, pMinusXZ) ->
            val p1 = liftXZtoPlane(pPlusXZ.x, pPlusXZ.y, eq).let { p3 -> Offset(p3.x, p3.y) }
            val p2 = liftXZtoPlane(pMinusXZ.x, pMinusXZ.y, eq).let { p3 -> Offset(p3.x, p3.y) }
            p1 to p2
        }
    }
    val (e1, e2) = if (ends != null && !conicP.isLineDegenerate) ends else center to center
    return projectToDegenerateRays(center, uXY, e1, e2, conicP.isLineDegenerate || ends == null, point, preferredSide)
}

fun MongeState.snapNarysDegenerateHyperbola(
    conicId: String,
    point: Offset,
    preferredSide: Int? = null
): DegenerateHyperbolaSnap? {
    val conicN = conicsNarys.firstOrNull { it.id == conicId } ?: return null
    if (!conicN.isDegenerate) return null
    val parent = conicN.parent ?: return null
    val rawEq = planeEquationFromConic3D(parent)
    val eq = PlaneEquation(rawEq.a, rawEq.b, rawEq.c, rawEq.d)

    val n = Offset3D(eq.a, eq.b, eq.c).normalize()
    var u3 = n.cross(Offset3D(0f, 1f, 0f)).normalize()
    if (abs(u3.x) + abs(u3.z) < 1e-9f) u3 = Offset3D(1f, 0f, 0f)
    val uXY = safeUnit(Offset(u3.x, u3.y))
    val fallbackDir = conicN.degenerateDir?.let { Offset(it.x, -it.y) } ?: Offset(1f, 0f)
    val uN = safeUnit(Offset(u3.x, u3.z), fallbackDir)

    val conicP = conicsPudorys.firstOrNull { it.parent?.id == parent.id }
    val coeffP = conicP?.let { ConicCoeffs(it.a, it.b, it.c, it.d, it.e, it.f) }
    val centerXY = coeffP?.let { conicCenter2DLocal(it) } ?: Offset.Zero
    val centerN = liftXYtoPlane(centerXY.x, centerXY.y, eq).let { Offset(it.x, it.z) }
    val endsN = coeffP?.let { extremeEnds2DLocal(it, uXY) }?.let { (pPlus, pMinus) ->
        val e1 = liftXYtoPlane(pPlus.x, pPlus.y, eq).let { p3 -> Offset(p3.x, p3.z) }
        val e2 = liftXYtoPlane(pMinus.x, pMinus.y, eq).let { p3 -> Offset(p3.x, p3.z) }
        e1 to e2
    }
    val (e1, e2) = if (endsN != null && !conicN.isLineDegenerate) endsN else centerN to centerN
    return projectToDegenerateRays(centerN, uN, e1, e2, conicN.isLineDegenerate || endsN == null, point, preferredSide)
}

fun DrawScope.drawHyperbolaBranchArcPudorysDegenerate(
    conicP: ConicSectionPudorys,
    A: Offset, B: Offset,                 // XY body (logickĂ©)
    eq: PlaneEquation,
    state: MongeState,
    color: Color,
    strokeWidth: Float,
    showHelpConic: Boolean,
    pxPerPt: Float,
    wrapsVertex: Boolean = false,
    arcEnds3D: Pair<Offset3D, Offset3D>? = null
) {
    // --- 1) STĹED C v XY (bez pĹ™edÄŤasnĂ˝ch returnĹŻ) ---
    val coeffP = ConicCoeffs(conicP.a, conicP.b, conicP.c, conicP.d, conicP.e, conicP.f)
    var cXY = conicCenter2D(coeffP)
    if (cXY == null) {
        val inpP = state.hyperbolaInputsPudorys[conicP.id]
        cXY = inpP?.let {
            computeIntersection(
                Offset(it.line1.point.x, it.line1.point.y), Offset(it.line1.direction.x, it.line1.direction.y),
                Offset(it.line2.point.x, it.line2.point.y), Offset(it.line2.direction.x, it.line2.direction.y)
            ) ?: it.vertex
        } ?: ((A + B) * 0.5f) // ĂşplnĂ˝ fallback: stĹ™ed segmentu
    }

    // --- 2) SMÄšR uXY = (n Ă— e_z) |_XY s fallbackem ---
    val n = Offset3D(eq.a, eq.b, eq.c).normalize()
    var u3 = n.cross(Offset3D(0f, 0f, 1f))
    val L3 = kotlin.math.sqrt(u3.x*u3.x + u3.y*u3.y + u3.z*u3.z)
    if (!L3.isFinite() || L3 < 1e-12f) { u3 = Offset3D(0f,1f,0f); }
    var uXY = Offset(u3.x, u3.y)
    val L = kotlin.math.sqrt(uXY.x*uXY.x + uXY.y*uXY.y)
    if (!L.isFinite() || L < 1e-12f) uXY = (B - A).let { v ->
        val lv = kotlin.math.sqrt(v.x*v.x + v.y*v.y).coerceAtLeast(1e-6f); Offset(v.x/lv, v.y/lv)
    } else uXY /= L

    // --- 3) KONCE MEZERY E1,E2 (sesterskĂ˝ nĂˇrys â†’ fallback na CÂ±g uXY) ---
    var E1: Offset? = null
    var E2: Offset? = null
    val sisterN = state.conicsNarys.find { it.parent === conicP.parent }
    if (sisterN != null) {
        val coeffN = ConicCoeffs(sisterN.a, sisterN.b, sisterN.c, sisterN.d, sisterN.e, sisterN.f)
        val eXZ = run {
            val v = Offset(u3.x, u3.z)
            val lv = kotlin.math.sqrt(v.x*v.x + v.y*v.y).coerceAtLeast(1e-12f)
            Offset(v.x/lv, v.y/lv)
        }
        val endsXZ = extremeEnds2D(coeffN, eXZ)
        if (endsXZ != null) {
            val (pPlusXZ, pMinusXZ) = endsXZ
            fun toXYsafe(pXZ: Offset): Offset? {
                val P3 = liftXZtoPlane(pXZ.x, pXZ.y, eq)
                val o = Offset(P3.x, P3.y)
                return if (o.x.isFinite() && o.y.isFinite()) o else null
            }
            E1 = toXYsafe(pPlusXZ)
            E2 = toXYsafe(pMinusXZ)
        }
    }
    if (E1 == null || E2 == null) {
        val v1 = state.hyperbolaInputsPudorys[conicP.id]?.vertex ?: cXY
        val g = abs((v1 - cXY).dot(uXY))
        E1 = cXY + uXY * g
        E2 = cXY - uXY * g
    }
    // teÄŹ jsou E1,E2 vĹľdy nÄ›jakĂ© body:
    val e1 = E1
    val e2 = E2

    // --- 5) (volitelnÄ›) PozadĂ­: raye z E1,E2 (nebo â€žjedna pĹ™Ă­mkaâ€ś, kdyĹľ E1â‰E2) ---
    if (state.showConstruction.value && showHelpConic) {
        fun drawRayXY(origin: Offset, dir: Offset) {
            val Lr = 50000f / max(1f, state.scale)
            val s1 = (origin).toScreenOrNull(state.scale, state.canvasOffset) ?: return
            val s2 = (origin + dir*Lr).toScreenOrNull(state.scale, state.canvasOffset) ?: return
            drawLine(Color.Gray.copy(alpha = 0.6f), s1, s2, 0.5f * pxPerPt)
        }
        if ((e1 - e2).getDistance() < 1e-6f) {
            drawRayXY(cXY,  uXY)
            drawRayXY(cXY, -uXY)
        } else {
            drawRayXY(e1,  uXY)
            drawRayXY(e2, -uXY)
        }
    }

    // --- 6) RozdÄ›lenĂ­ podle polopĹ™Ă­mek (podle znamĂ©nek (P-C)Â·uXY) a kreslenĂ­ ---
    // --- 4) Parametry koncĹŻ a smÄ›ry jednotlivĂ˝ch polopĹ™Ă­mek ---
    val tE1 = (e1 - cXY).dot(uXY)
    val tE2 = (e2 - cXY).dot(uXY)
    // smÄ›r polopĹ™Ă­mky vĹľdy â€žod stĹ™edu venâ€ś: z E1 po sign(tE1)*uXY, z E2 po sign(tE2)*uXY
    fun sgn(v: Float) = if (v >= 0f) +1f else -1f
    val dirE1 = uXY * sgn(tE1)
    val dirE2 = uXY * sgn(tE2)

    // --- 5) Snap bodu na â€žsvouâ€ś polopĹ™Ă­mku (k bliĹľĹˇĂ­mu konci) a KLAMP na t>=0 ---
    fun snapToRay(p: Offset): Pair<Offset, Int> {
        val tC = (p - cXY).dot(uXY)           // parametr bodu podĂ©l osy C+tu
        val d1 = abs(tC - tE1)
        val d2 = abs(tC - tE2)
        val useE1 = d1 <= d2                  // ke kterĂ©mu konci je blĂ­Ĺľ
        val E = if (useE1) e1 else e2
        val dir = if (useE1) dirE1 else dirE2
        val tRay = max(0f, (p - E).dot(dir))  // <-- KLAMP NA POLOPĹĂŤMKU
        val q = E + dir * tRay
        return q to (if (useE1) 1 else 2)
    }

// zarovnanĂ© body na pĹ™Ă­sluĹˇnĂ˝ch polopĹ™Ă­mkĂˇch
    val (qA, whichA) = snapToRay(A)
    val (qB, whichB) = snapToRay(B)

    // --- 6) KreslenĂ­: stejnĂ© polopĹ™Ă­mky â†’ jedna ĂşseÄŤka; rĹŻznĂ© â†’ dvÄ› ĂşseÄŤky k E1/E2 ---
    fun drawSeg(p1: Offset, p2: Offset) {
        val s1 = p1.toScreenOrNull(state.scale, state.canvasOffset) ?: return
        val s2 = p2.toScreenOrNull(state.scale, state.canvasOffset) ?: return
        if ((s1 - s2).getDistance() >= 1e-6f) drawLine(color, s1, s2, strokeWidth)
    }

    fun farthestOnSide(side: Int, base: Offset): Offset {
        val parent = conicP.parent ?: return base
        val ends3D = arcEnds3D ?: return base
        val (t1, t2) = hyperbolaParams(parent, ends3D.first, ends3D.second) ?: return base
        val sx3D = hyperbolaBranchSign(parent, ends3D.first)
        val E = if (side == 1) e1 else e2
        val dir = if (side == 1) dirE1 else dirE2
        var best = base
        var bestT = (base - E).dot(dir)
        val steps = 96
        for (i in 0..steps) {
            val t = t1 + (t2 - t1) * (i / steps.toFloat())
            val p3 = hyperbolaPointAt(parent, t, sx3D) ?: continue
            val projected = Offset(p3.x, p3.y)
            val (q, which) = snapToRay(projected)
            if (which != side) continue
            val tq = (q - E).dot(dir)
            if (tq.isFinite() && tq > bestT) {
                bestT = tq
                best = q
            }
        }
        return best
    }

    if (whichA == whichB) {
        // oba konce na stejnĂ© polopĹ™Ă­mce
        if (wrapsVertex && (e1 - e2).getDistance() >= 1e-6f) {
            val E = if (whichA == 1) e1 else e2
            val far = farthestOnSide(whichA, if ((qA - E).getDistance() >= (qB - E).getDistance()) qA else qB)
            drawSeg(E, far)
        } else {
            drawSeg(qA, qB)
        }
    } else {
        // pĹ™es extrĂ©m â†’ pĹ™idej krajnĂ­ body mezery
        val EA = if (whichA == 1) e1 else e2
        val EB = if (whichB == 1) e1 else e2
        drawSeg(EA, qA)
        drawSeg(EB, qB)
    }

}

fun DrawScope.drawHyperbolaBranchArcNarysDegenerate(
    conicN: ConicSectionNarys,
    A: Offset,
    B: Offset,
    state: MongeState,
    color: Color,
    strokeWidth: Float,
    showHelpConic: Boolean,
    pxPerPt: Float,
    wrapsVertex: Boolean = false,
    arcEnds3D: Pair<Offset3D, Offset3D>? = null
) {
    val parent = conicN.parent ?: return
    val rawEq = planeEquationFromConic3D(parent)
    val eq = PlaneEquation(rawEq.a, rawEq.b, rawEq.c, rawEq.d)
    val n = Offset3D(eq.a, eq.b, eq.c).normalize()
    var u3 = n.cross(Offset3D(0f, 1f, 0f)).normalize()
    if (abs(u3.x) + abs(u3.z) < 1e-9f) u3 = Offset3D(1f, 0f, 0f)
    val uXY = safeUnit(Offset(u3.x, u3.y))
    val fallbackDir = conicN.degenerateDir?.let { Offset(it.x, -it.y) } ?: Offset(1f, 0f)
    val uN = safeUnit(Offset(u3.x, u3.z), fallbackDir)

    val conicP = state.conicsPudorys.firstOrNull { it.parent?.id == parent.id }
    val coeffP = conicP?.let { ConicCoeffs(it.a, it.b, it.c, it.d, it.e, it.f) }
    val centerXY = coeffP?.let { conicCenter2DLocal(it) } ?: Offset.Zero
    val center = liftXYtoPlane(centerXY.x, centerXY.y, eq).let { Offset(it.x, it.z) }
    val ends = coeffP?.let { extremeEnds2DLocal(it, uXY) }?.let { (pPlus, pMinus) ->
        val e1 = liftXYtoPlane(pPlus.x, pPlus.y, eq).let { p3 -> Offset(p3.x, p3.z) }
        val e2 = liftXYtoPlane(pMinus.x, pMinus.y, eq).let { p3 -> Offset(p3.x, p3.z) }
        e1 to e2
    }
    val isLine = conicN.isLineDegenerate || ends == null
    val (e1, e2) = if (ends != null && !isLine) ends else center to center

    if (state.showConstruction.value && showHelpConic) {
        fun drawRay(origin: Offset, dir: Offset) {
            val len = 50000f / max(1f, state.scale)
            val s1 = origin.toScreenNarys(state.scale, state.canvasOffset)
            val s2 = (origin + dir * len).toScreenNarys(state.scale, state.canvasOffset)
            if (!s1.x.isFinite() || !s1.y.isFinite() || !s2.x.isFinite() || !s2.y.isFinite()) return
            drawLine(Color.Gray.copy(alpha = 0.6f), s1, s2, 0.5f * pxPerPt)
        }
        if (isLine) {
            drawRay(center, uN)
            drawRay(center, -uN)
        } else {
            drawRay(e1, uN * if ((e1 - center).dot(uN) >= 0f) +1f else -1f)
            drawRay(e2, uN * if ((e2 - center).dot(uN) >= 0f) +1f else -1f)
        }
    }

    val qA = projectToDegenerateRays(center, uN, e1, e2, isLine, A)
    val qB = projectToDegenerateRays(center, uN, e1, e2, isLine, B, if (qA.side == 0) null else qA.side)

    fun drawSeg(p1: Offset, p2: Offset) {
        val s1 = p1.toScreenNarys(state.scale, state.canvasOffset)
        val s2 = p2.toScreenNarys(state.scale, state.canvasOffset)
        if (!s1.x.isFinite() || !s1.y.isFinite() || !s2.x.isFinite() || !s2.y.isFinite()) return
        if ((s1 - s2).getDistance() >= 1e-6f) drawLine(color, s1, s2, strokeWidth)
    }

    fun sideOfE1(): Int = if ((e1 - center).dot(uN) >= 0f) +1 else -1
    fun farthestOnSide(side: Int, base: Offset): Offset {
        val ends3D = arcEnds3D ?: return base
        val (t1, t2) = hyperbolaParams(parent, ends3D.first, ends3D.second) ?: return base
        val sx3D = hyperbolaBranchSign(parent, ends3D.first)
        val E = if (side == sideOfE1()) e1 else e2
        val rayDir = uN * side.toFloat()
        var best = base
        var bestT = (base - E).dot(rayDir)
        val steps = 96
        for (i in 0..steps) {
            val t = t1 + (t2 - t1) * (i / steps.toFloat())
            val p3 = hyperbolaPointAt(parent, t, sx3D) ?: continue
            val projected = Offset(p3.x, p3.z)
            val snap = projectToDegenerateRays(center, uN, e1, e2, isLine, projected, side)
            if (snap.side != side) continue
            val tq = (snap.point - E).dot(rayDir)
            if (tq.isFinite() && tq > bestT) {
                bestT = tq
                best = snap.point
            }
        }
        return best
    }

    if (isLine || qA.side == qB.side) {
        if (!isLine && wrapsVertex) {
            val E = if (qA.side == sideOfE1()) e1 else e2
            val farBase = if ((qA.point - E).getDistance() >= (qB.point - E).getDistance()) qA.point else qB.point
            drawSeg(E, farthestOnSide(qA.side, farBase))
        } else {
            drawSeg(qA.point, qB.point)
        }
    } else {
        val endA = if (qA.side == sideOfE1()) e1 else e2
        val endB = if (qB.side == sideOfE1()) e1 else e2
        drawSeg(endA, qA.point)
        drawSeg(endB, qB.point)
    }
}
