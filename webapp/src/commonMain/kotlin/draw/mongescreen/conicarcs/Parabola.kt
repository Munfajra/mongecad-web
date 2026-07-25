package draw.mongescreen.conicarcs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.orth.conics.drawConicParabolaNarys
import draw.mongescreen.objects.orth.conics.drawConicParabolaPudorys
import model.*
import model.classes.ConicSection3D
import monge.input.ConicArcs.associated.*
import monge.input.ConicArcs.single.getLogicalCursorNarys
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Lokální rámec paraboly z (vertex, focus)
data class ParabolaBasis(
    val V: Offset,
    val ex: Offset,   // osa (unit)
    val ey: Offset,   // kolmá (unit)
    val p: Float      // vzdálenost vertex–focus
)

fun parabolaBasis(vertex: Offset, focus: Offset): ParabolaBasis {
    val axis = focus - vertex
    val p = axis.getDistance().coerceAtLeast(1e-6f)
    val ex = Offset(axis.x / p, axis.y / p)
    val ey = Offset(-ex.y, ex.x)
    return ParabolaBasis(vertex, ex, ey, p)
}

private fun toLocal(b: ParabolaBasis, pt: Offset): Offset {
    val r = pt - b.V
    val x = r.x * b.ex.x + r.y * b.ex.y
    val y = r.x * b.ey.x + r.y * b.ey.y
    return Offset(x, y)
}
fun fromLocal(b: ParabolaBasis, q: Offset): Offset =
    b.V + Offset(q.x * b.ex.x + q.y * b.ey.x, q.x * b.ex.y + q.y * b.ey.y)


fun projectParabolaAndParam(vertex: Offset, focus: Offset, pt: Offset): Pair<Float, Offset> {
    val b = parabolaBasis(vertex, focus)
    val q = toLocal(b, pt)     // Q' = (Qx,Qy)
    val p = b.p

    // g(u) = u^3 - (4 p Qx - 8 p^2) u - 8 p^2 Qy = 0
    fun g(u: Float): Float = u*u*u - (4f*p*q.x - 8f*p*p)*u - 8f*p*p*q.y
    fun gp(u: Float): Float = 3f*u*u - (4f*p*q.x - 8f*p*p)

    var u = q.y // dobrý start
    repeat(6) {
        val den = gp(u)
        if (abs(den) < 1e-6f) return@repeat
        val step = g(u) / den
        u -= step
        if (abs(step) < 1e-4f) return@repeat
    }

    val xOn = (u*u) / (4f * p)
    val yOn = u
    val on = fromLocal(b, Offset(xOn, yOn))
    return u to on
}

/** Jen promítni libovolný bod na parabolu danou (vertex,focus). */
fun projectToParabola(vertex: Offset, focus: Offset, pt: Offset): Offset =
    projectParabolaAndParam(vertex, focus, pt).second
fun DrawScope.drawParabolaArcPudorys(
    vertex: Offset,
    focus: Offset,
    A: Offset, B: Offset,            // body (klidně mimo; promítáme)
    canvasOffset: Offset,
    scale: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    tStep: Float = 0.5f              // stejné „krokování“ jako u tebe
) {
    val basis = parabolaBasis(vertex, focus)
    val p = basis.p
    if (p < 1e-6f) return

    // z A,B získáme parametry u1,u2 a přesné on-curve body
    val (u1, Aon) = projectParabolaAndParam(vertex, focus, A)
    val (u2, Bon) = projectParabolaAndParam(vertex, focus, B)

    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    val steps = max(2, ceil(L / tStep).toInt())

    val path = Path()

    // začni přesně na Aon
    run {
        val s = Aon.toScreenOld(scale, canvasOffset)
        path.moveTo(s.x, s.y)
    }

    // mezibody
    for (i in 1 until steps) {
        val u = u1 + dir * (i * (L / steps))
        val x = (u*u) / (4f * p)
        val pt = fromLocal(basis, Offset(x, u))
        val s = pt.toScreenOld(scale, canvasOffset)
        path.lineTo(s.x, s.y)
    }

    // zakonči přesně na Bon
    run {
        val s = Bon.toScreenOld(scale, canvasOffset)
        path.lineTo(s.x, s.y)
    }

    val pe = when (lineStyle) {
        LineStyle.Solid  -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot-> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, pathEffect = pe)
    )
}

data class PlaneEquation(val a: Float, val b: Float, val c: Float, val d: Float)

fun planeEquationFromConic3D(c3: ConicSection3D): PlaneEquation {
    val n = c3.u.cross(c3.v).normalized()      // normála roviny kuželosečky
    val d = -(n.x * c3.p0.x + n.y * c3.p0.y + n.z * c3.p0.z)
    return PlaneEquation(n.x, n.y, n.z, d)
}
fun liftXZtoXY(eq: PlaneEquation, x: Float, z: Float): Offset? {
    if (abs(eq.b) < 1e-6f) return null
    // y = -(a x + c z + d)/b
    val y = -(eq.a * x + eq.c * z + eq.d) / eq.b
    if (!y.isFinite()) return null
    return Offset(x, y)
}

fun DrawScope.drawParabolaDegenerateArcPudorys(
    origin: Offset,            // vrchol degenerované projekce (p1)
    dirIn: Offset,             // uložený směr (normujeme uvnitř)
    isLine: Boolean,           // true = přímka, false = polopřímka
    A: Offset, B: Offset,      // koncové body oblouku (v logickém XY)
    canvasOffset: Offset,
    scale: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    var dir = dirIn
    val L = dir.getDistance()
    dir = if (L < 1e-6f) Offset(1f, 0f) else dir / L

    // Parametrické projekce A,B na přímku (origin + t*dir)
    fun projT(P: Offset): Float = (P.x - origin.x) * dir.x + (P.y - origin.y) * dir.y

    var tA = projT(A)
    var tB = projT(B)

    // na polopřímce ořízneme na t >= 0
    if (!isLine) {
        if (tA < 0f) tA = 0f
        if (tB < 0f) tB = 0f
    }

    val P1 = origin + dir * tA
    val P2 = origin + dir * tB
    if ((P2 - P1).getDistance() < 1e-6f) return

    val pathEffect = when (lineStyle) {
        LineStyle.Solid  -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot-> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }

    drawLine(
        color = color,
        start = P1.toScreenOld(scale, canvasOffset),
        end   = P2.toScreenOld(scale, canvasOffset),
        strokeWidth = strokeWidth,
        pathEffect = pathEffect
    )
}

fun Offset.toScreenNarys(scale: Float, canvasOffset: Offset) =
    this.copy(y = -this.y).toScreenOld(scale, canvasOffset)

/** NÁRYS: oblouk paraboly mezi A a B (A,B promítne na křivku), s převrácenou Y při kreslení */
fun DrawScope.drawParabolaArcNarys(
    vertex: Offset,
    focus: Offset,
    A: Offset, B: Offset,
    canvasOffset: Offset,
    scale: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    tStep: Float = 0.5f
) {
    val basis = parabolaBasis(vertex, focus)
    val p = basis.p
    if (p < 1e-6f) return

    // promítnuté konce + parametry
    val (u1, Aon) = projectParabolaAndParam(vertex, focus, A)
    val (u2, Bon) = projectParabolaAndParam(vertex, focus, B)

    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    val steps = max(2, ceil(L / tStep).toInt())

    val path = Path()

    // start přesně na Aon (se screen flipem pro nárys)
    run {
        val s = Aon.toScreenNarys(scale, canvasOffset)
        path.moveTo(s.x, s.y)
    }

    // mezibody
    for (i in 1 until steps) {
        val u = u1 + dir * (i * (L / steps))
        val x = (u*u) / (4f * p)
        val y = u
        val pt = fromLocal(basis, Offset(x, y))
        val s = pt.toScreenNarys(scale, canvasOffset)
        path.lineTo(s.x, s.y)
    }

    // konec přesně na Bon
    run {
        val s = Bon.toScreenNarys(scale, canvasOffset)
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
fun DrawScope.drawParabolaArcPreviewPudorys(state: MongeState, snappedPointLogical: Offset?) {
    if ((state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS)||
        state.mongeMode != DrawingModeMonge.PUDORYS ||
        state.projectionPhase != "pudorys_par_arc") return

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
    val conicId = state.activeConicIdForArc ?: return
    val inputs = state.conicInputPointsPudorys[conicId] ?: return
    if (inputs.third != Offset.Unspecified || state.pendingArcA == null) return

    val (vertex, focus, _) = inputs
    val Aproj = projectToParabola(vertex, focus, state.pendingArcA!!)
    val Bproj = projectToParabola(vertex, focus, cursor)

    // volitelné: pozadí celé paraboly (slabě)
    drawConicParabolaPudorys(
        vertex = vertex, focus = focus,
        canvasOffset = state.canvasOffset, scale = state.scale,
        color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed
    )

    // náhled oblouku
    drawParabolaArcPudorys(
        vertex = vertex, focus = focus,
        A = Aproj, B = Bproj,
        canvasOffset = state.canvasOffset, scale = state.scale,
        color = Color.Red, strokeWidth =  (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
        lineStyle = LineStyle.Solid
    )
}

fun DrawScope.drawParabolaArcPreviewNarys(state: MongeState, snappedPointLogical: Offset?) {
    if ((state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS) ||
        state.mongeMode != DrawingModeMonge.NARYS ||
        state.projectionPhase != "narys_par_arc") return

    val cursor = getLogicalCursorNarys(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection
    )
    val conicId = state.activeConicIdForArc ?: return
    val inputs = state.conicInputPointsNarys[conicId] ?: return
    if (inputs.third != Offset.Unspecified || state.pendingArcA == null) return

    val (vertex, focus, _) = inputs
    val Aproj = projectToParabola(vertex, focus, state.pendingArcA!!)
    val Bproj = projectToParabola(vertex, focus, cursor)

    // pozadí celé paraboly (slabě)
    drawConicParabolaNarys( // pokud máš NARYS variantu, klidně ji použij
        vertex = vertex, focus = focus,
        canvasOffset = state.canvasOffset, scale = state.scale,
        color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed
    )

    // náhled oblouku (můžeš použít stejnou funkci jako v PUDORYSu)
    drawParabolaArcNarys(
        vertex = vertex, focus = focus,
        A = Aproj, B = Bproj,
        canvasOffset = state.canvasOffset, scale = state.scale,
        color = Color.Red,
        strokeWidth = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
        lineStyle = LineStyle.Solid
    )
}
data class ParabolaPreview(
    val pConicId: String?, val nConicId: String?,
    val A_p: Offset?, val B_p: Offset?,   // konce v P
    val A_n: Offset?, val B_n: Offset?    // konce v N
)


private fun computeParabolaArcPreview(
    state: MongeState,
    cursorP: Offset,
    cursorN: Offset
): ParabolaPreview {
    // ── PŮDORYS → 3D → NÁRYS
    if (state.projectionPhase == "pudorys_par3d_arc") {
        val pId = state.activeConicIdForArc ?: return ParabolaPreview(null,null,null,null,null,null)
        val inputsP = state.conicInputPointsPudorys[pId] ?: return ParabolaPreview(null,null,null,null,null,null)
        val (vP, fP, tP) = inputsP
        if (tP != Offset.Unspecified) return ParabolaPreview(null,null,null,null,null,null)

        val parentId = state.activeParentConic3DIdForEllipseArc ?: return ParabolaPreview(null,null,null,null,null,null)
        val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return ParabolaPreview(null,null,null,null,null,null)

        val A_p = state.pendingArcA_3D ?: return ParabolaPreview(null,null,null,null,null,null)
        val B_p = projectToParabola(vP, fP, cursorP)   // ← používáme P kurzor

        val liftA = liftPudorysPointTo3DAndNarys(A_p.x, A_p.y, parent) ?: return ParabolaPreview(null,null,null,null,null,null)
        val liftB = liftPudorysPointTo3DAndNarys(B_p.x, B_p.y, parent) ?: return ParabolaPreview(null,null,null,null,null,null)
        val (_, A_n_raw) = liftA
        val (_, B_n_raw) = liftB

        val nId = state.findNarysConicIdByParent(parent.id)
        val (A_n, B_n) = if (nId != null) {
            val A_fixed = state.snapToNarysParabolaFixSign(nId, A_n_raw)
            val B_fixed = state.snapToNarysParabolaFixSign(nId, B_n_raw)
            state.projectToNarysParabolaConsideringDegeneracy(nId, A_fixed) to
                    state.projectToNarysParabolaConsideringDegeneracy(nId, B_fixed)
        } else A_n_raw to B_n_raw

        return ParabolaPreview(pId, nId, A_p, B_p, A_n, B_n)
    }

    // ── NÁRYS → 3D → PŮDORYS
    if (state.projectionPhase == "narys_par3d_arc") {
        val nId = state.activeConicIdForArc ?: return ParabolaPreview(null,null,null,null,null,null)
        val inputsN = state.conicInputPointsNarys[nId] ?: return ParabolaPreview(null,null,null,null,null,null)
        val (vN, fN, tN) = inputsN
        if (tN != Offset.Unspecified) return ParabolaPreview(null,null,null,null,null,null)

        val parentId = state.activeParentConic3DIdForEllipseArc ?: return ParabolaPreview(null,null,null,null,null,null)
        val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return ParabolaPreview(null,null,null,null,null,null)

        val A_n = state.pendingArcA_3D ?: return ParabolaPreview(null,null,null,null,null,null)
        val B_n = projectToParabola(vN, fN, cursorN)   // ← používáme N kurzor

        val liftA = liftNarysPointTo3DAndPudorys(A_n.x, A_n.y, parent) ?: return ParabolaPreview(null,null,null,null,null,null)
        val liftB = liftNarysPointTo3DAndPudorys(B_n.x, B_n.y, parent) ?: return ParabolaPreview(null,null,null,null,null,null)
        val (_, A_p_raw) = liftA
        val (_, B_p_raw) = liftB

        val pId = state.findPudorysConicIdByParent(parent.id)
        val (A_p, B_p) = if (pId != null) {
            val A_fixed = state.snapToPudorysParabolaFixSign(pId, A_p_raw)
            val B_fixed = state.snapToPudorysParabolaFixSign(pId, B_p_raw)
            state.projectToPudorysParabolaConsideringDegeneracy(pId, A_fixed) to
                    state.projectToPudorysParabolaConsideringDegeneracy(pId, B_fixed)
        } else A_p_raw to B_p_raw

        return ParabolaPreview(pId, nId, A_p, B_p, A_n, B_n)
    }

    return ParabolaPreview(null,null,null,null,null,null)
}



fun DrawScope.drawParabolaArcPreviewPudorysAsoc(
    state: MongeState,
    snappedPointLogical: Offset?
) {
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
    val prev = computeParabolaArcPreview(state, cursorP, cursorN)

    val pId = prev.pConicId ?: return
    val inputs = state.conicInputPointsPudorys[pId] ?: return
    val (vertex, focus, third) = inputs
    if (third != Offset.Unspecified) return

    val A = prev.A_p ?: return
    val B = prev.B_p ?: return
    val conic = state.conicsPudorys.firstOrNull { it.id == pId } ?: return
    val isDeg = conic.isDegenerate == true
    if (!isDeg) {
        // klasicky křivka
        drawParabolaArcPudorys(
            vertex = vertex, focus = focus,
            A = A, B = B,
            scale = state.scale, canvasOffset = state.canvasOffset,
            color = Color.Red,
            strokeWidth = (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
            lineStyle = LineStyle.Solid
        )
        return
    }
    // ====== DEGENERACE (ray/přímka) ======
    val parent3D = conic.parent // může být null → fallback níže

    // směr ray/přímky
    var dir = conic.degenerateDir ?: (focus - vertex)
    val Ld = dir.getDistance()
    dir = if (Ld < 1e-6f) Offset(1f, 0f) else dir / Ld



    // Základní fallback: projekce A,B na přímku/ray
    fun sOf(P: Offset) = (P.x - vertex.x) * dir.x + (P.y - vertex.y) * dir.y

    // Zkusíme přesnější variantu: vzít on-curve konce z NÁRYSU (už je máš v prev)
    val An = prev.A_n
    val Bn = prev.B_n
    if (parent3D != null && An != null && Bn != null) {
        // 1) v XZ máme on-curve body; potřebujeme je přenést do XY po rovině
        val eqPlane = planeEquationFromConic3D(parent3D)
        val nConic = state.conicsNarys.firstOrNull { it.parent?.id == parent3D.id }
        val vNfN = nConic?.let { state.conicInputPointsNarys[it.id] }  // kvůli u-param
        val (vN, fN, _) = vNfN ?: (Offset.Zero to Offset(1f,0f)).let { Triple(it.first, it.second, Offset.Unspecified) }

        val (uA, AN_on) = projectParabolaAndParam(vN, fN, An)
        val (uB, BN_on) = projectParabolaAndParam(vN, fN, Bn)
        val Axy = liftXZtoXY(eqPlane, AN_on.x, AN_on.y) ?: run {
            drawParabolaDegenerateArcPudorys(
                origin = vertex,
                dirIn = dir,
                isLine = conic.isLineDegenerate,
                A = A,
                B = B,
                canvasOffset = state.canvasOffset,
                scale = state.scale,
                color = Color.Red,
                strokeWidth = (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
                lineStyle = LineStyle.Solid
            )
            return
        }
        val Bxy = liftXZtoXY(eqPlane, BN_on.x, BN_on.y) ?: run {
            drawParabolaDegenerateArcPudorys(
                origin = vertex,
                dirIn = dir,
                isLine = conic.isLineDegenerate,
                A = A,
                B = B,
                canvasOffset = state.canvasOffset,
                scale = state.scale,
                color = Color.Red,
                strokeWidth = (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
                lineStyle = LineStyle.Solid
            )
            return
        }

        // 2) pokus o “až k originu” přes α,β
        val nrm = parent3D.u.cross(parent3D.v).normalized()
        fun liftXZto3D(x: Float, z: Float): Offset3D {
            val d = -(nrm.x*parent3D.p0.x + nrm.y*parent3D.p0.y + nrm.z*parent3D.p0.z)
            if (abs(nrm.y) < 1e-6f) return Offset3D(x, parent3D.p0.y, z)
            val y = -(nrm.x*x + nrm.z*z + d) / nrm.y
            return Offset3D(x, y, z)
        }
        val v3 = liftXZto3D(vN.x, vN.y)
        val f3 = liftXZto3D(fN.x, fN.y)
        val axis3D = (f3 - v3).normalized()
        val nInPlane = nrm.cross(axis3D).normalized()

        val uXY = Offset(nInPlane.x, nInPlane.y)
        val vXY = Offset(axis3D.x,   axis3D.y)
        val p = (f3 - v3).length()
        var alpha = uXY.x*dir.x + uXY.y*dir.y
        var beta  = (vXY.x*dir.x + vXY.y*dir.y) / (4f * p)
        if (beta < 0f) {
            dir = Offset(-dir.x, -dir.y)        // otoč ray
            alpha = -alpha
            beta  = -beta
        }

        val sMin = if (beta > 1e-8f) -alpha*alpha / (4f*beta) else 0f
        val sA = alpha*uA + beta*uA*uA - sMin
        val sB = alpha*uB + beta*uB*uB - sMin

        var s1 = min(sA, sB)
        var s2 = max(sA, sB)
        val sVertex = 0f  // protože posouváš o sMin, vrchol je vždy v 0
        if (sVertex < s1 || sVertex > s2) {
            // rozšiř interval o vrchol
            min(s1, sVertex)
            max(s2, sVertex)
        }
        val isLine = conic.isLineDegenerate
        val EPS = 1e-8f

// beta jsme už případně znegovali tak, aby beta >= 0 (viz předchozí fix)
        if (abs(beta) < EPS) {
            // ~přímka: prostá projekce A,B na dir
            s1 = sOf(Axy); s2 = sOf(Bxy)
        } else {
            // vrchol v parametru u (v XZ)
            val uStar = -alpha / (2f * beta)

            // s posunuté tak, aby vrchol byl v 0
            val sMin = -alpha * alpha / (4f * beta)
            val sA = alpha * uA + beta * uA * uA - sMin
            val sB = alpha * uB + beta * uB * uB - sMin

            var lo = min(sA, sB)
            var hi = max(sA, sB)

            // zahrň vrchol jen když u* leží mezi uA a uB (s tolerancí)
            val crossesVertex = (uA - uStar) * (uB - uStar) <= 1e-6f
            if (crossesVertex) {
                lo = min(lo, 0f)
                hi = max(hi, 0f)
            }

            s1 = lo; s2 = hi
        }

// ray vs. přímka
        if (!isLine) {
            s1 = max(0f, s1)
            s2 = max(0f, s2)
        }
        if (abs(s2 - s1) < 1e-6f) return

        fun sOf(P: Offset) = (P.x - vertex.x) * dir.x + (P.y - vertex.y) * dir.y
        val sAxy = sOf(Axy)
        val sBxy = sOf(Bxy)

        val candidates = mutableListOf(
            sAxy to Axy,
            sBxy to Bxy
        )

// Přidej vrchol jen když oblouk v XZ přechází přes u*:  (uA - u*)(uB - u*) ≤ 0
        val crossesVertex = run {
            val EPS = 1e-8f
            if (abs(beta) < EPS) {
                // přímkový limit: stačí, že 0 leží mezi s(Axy), s(Bxy)
                (min(sAxy, sBxy) - 1e-6f) <= 0f && 0f <= (max(sAxy, sBxy) + 1e-6f)
            } else {
                val uStar = -alpha / (2f * beta)
                (uA - uStar) * (uB - uStar) <= 1e-6f
            }
        }
        if (crossesVertex) candidates += 0f to vertex

// Ořízni na polopřímku (u přímky ponech vše)
        val filtered = if (isLine) candidates else candidates.filter { it.first >= -1e-6f }
        if (filtered.size < 2) return  // nic rozumného k vykreslení

// Vyber skutečné konce podle s (MIN/MAX) a POUŽIJ JE PŘÍMO
        var lo = filtered[0]; var hi = filtered[0]
        for (c in filtered) {
            if (c.first < lo.first) lo = c
            if (c.first > hi.first) hi = c
        }
        val P1 = lo.second
        val P2 = hi.second

        drawParabolaDegenerateArcPudorys(
            origin = vertex,
            dirIn  = dir,          // směr (kvůli ray/šrafování)
            isLine = isLine,
            A = P1,                // ← přesné XY body
            B = P2,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            color = Color.Red,
            strokeWidth = (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
            lineStyle = LineStyle.Solid
        )
        return
}
}


fun DrawScope.drawParabolaArcPreviewNarysAsoc(state: MongeState, snappedPointLogical: Offset?) {
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

    val prev = computeParabolaArcPreview(state, cursorP, cursorN)
    val conicId = prev.nConicId ?: return
    val A = prev.A_n ?: return
    val B = prev.B_n ?: return

    val inputs = state.conicInputPointsNarys[conicId] ?: return
    val (vertex, focus, third) = inputs
    if (third != Offset.Unspecified) return
    val conic = state.conicsNarys.firstOrNull { it.id == conicId } ?: return
    if (conic.isDegenerate) {
        drawParabolaDegenerateArcNarysPreview(
            origin = vertex,
            dirIn = conic.degenerateDir ?: (focus - vertex),
            isLine = conic.isLineDegenerate,
            A = A,
            B = B,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            color = Color.Red,
            strokeWidth = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
            lineStyle = LineStyle.Solid
        )
        return
    }

    drawParabolaArcNarys(
        vertex = vertex,
        focus = focus,
        A = A, B = B,
        scale = state.scale,
        canvasOffset = state.canvasOffset,
        color = Color.Red,
        strokeWidth = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
        lineStyle = LineStyle.Solid
    )
}

private fun DrawScope.drawParabolaDegenerateArcNarysPreview(
    origin: Offset,
    dirIn: Offset,
    isLine: Boolean,
    A: Offset,
    B: Offset,
    scale: Float,
    canvasOffset: Offset,
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
        LineStyle.Solid  -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
        LineStyle.DashDot-> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
    }
    drawLine(
        color = color,
        start = p1.toScreenNarys(scale, canvasOffset),
        end = p2.toScreenNarys(scale, canvasOffset),
        strokeWidth = strokeWidth,
        pathEffect = pe
    )
}