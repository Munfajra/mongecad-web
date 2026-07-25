package monge.input.ConicArcs.associated

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.ConicSection3D
import monge.input.ConicArcs.single.ellipseBasisFromDiameters
import monge.input.ConicArcs.single.ellipseParamAndProjection
import monge.input.ConicArcs.single.projectToEllipseFromDiameters
import monge.input.ConicArcs.single.setEllipseArc
import monge.input.planeobjects.conicsections.Vec3
import geometry.conics.ConicType
import geometry.conics.classifyConicFromMatrix
import geometry.conics.computeEllipseAxes3D
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.getLogicalCursor

// ────────────────────────────────────────────────────────────────────────────
// Helpery
// ────────────────────────────────────────────────────────────────────────────

private const val EPS = 1e-5f

private fun isEllipseArcCompatible(conic: ConicSection3D): Boolean =
    classifyConicFromMatrix(conic.matrix).let { it == ConicType.ELLIPSE || it == ConicType.DEGENERATE }

data class PlaneEq(val a: Float, val b: Float, val c: Float, val d: Float) {
    fun normal() = Vec3(a, b, c)
}

fun planeEquation(conic: ConicSection3D): PlaneEq {
    val n = conic.u.cross(conic.v)              // normála roviny elipsy
    val a = n.x; val b = n.y; val c = n.z
    val d = -(a * conic.p0.x + b * conic.p0.y + c * conic.p0.z)
    return PlaneEq(a, b, c, d)
}

/** (x,y) z PŮDORYSU → 3D + logický NÁRYS (x,z). */
fun liftPudorysPointTo3DAndNarys(
    x: Float,
    y: Float,
    conic: ConicSection3D
): Pair<Offset3D, Offset>? {
    val eq = planeEquation(conic)
    if (!eq.c.isFinite() || kotlin.math.abs(eq.c) < EPS) return null // rovina ~ kolmá k XY → nedá se z XY zvedat
    val z = -(eq.a * x + eq.b * y + eq.d) / eq.c
    val p3 = Offset3D(x, y, z)
    val pN = Offset(x, z) // logický nárys (x,z); flip(y=-z) až při vykreslení
    return p3 to pN
}

/** t pro P³ᴰ = p0 + u3D*(a cos t) + v3D*(b sin t) */
fun ellipseParamTForPoint3D(conic: ConicSection3D, P3D: Offset3D): Float {
    val axes = runCatching { computeEllipseAxes3D(conic) }.getOrNull()
    val r = P3D - conic.p0
    if (axes == null || axes.a < EPS || axes.b < EPS || !axes.a.isFinite() || !axes.b.isFinite()) {
        return kotlin.math.atan2(r.dot(conic.v), r.dot(conic.u))
    }
    val alpha = r.dot(axes.uRotated)        // = a*cos t
    val beta  = r.dot(axes.vRotated)        // = b*sin t
    val cosT = (alpha / axes.a).coerceIn(-1f, 1f)
    val sinT = (beta  / axes.b).coerceIn(-1f, 1f)
    return kotlin.math.atan2(sinT, cosT)
}
fun MongeState.snapToNarysEllipseFixSign(narysId: String, p: Offset): Offset {
    val inputsN = conicInputPointsNarys[narysId] ?: return p
    val (n1, n2, n3) = inputsN
    if (n3 == Offset.Unspecified) return p

    fun sqr(x: Float) = x * x
    fun dist2(a: Offset, b: Offset) = sqr(a.x - b.x) + sqr(a.y - b.y)

    // varianta 1: bereme p tak jak je
    val snap1 = projectToEllipseFromDiameters(n1, n2, n3, p)
    val d1 = dist2(p, snap1)

    // varianta 2: zkusíme prohozené znaménko osy z (y -> -y)
    val pFlip = Offset(p.x, -p.y)
    val snap2 = projectToEllipseFromDiameters(n1, n2, n3, pFlip)
    val d2 = dist2(pFlip, snap2)

    // vyber variatu, která sedí lépe k elipse
    return if (d2 < d1) pFlip else p
}
fun normalizeArcByMode(t1In: Float, t2In: Float, mode: ArcMode): Pair<Float, Float> {
    val pi = kotlin.math.PI.toFloat()
    val twoPi = 2f * pi
    val halfTurnEps = 1e-5f

    fun positiveModulo(value: Float): Float = (value % twoPi + twoPi) % twoPi
    fun norm(value: Float): Float = positiveModulo(value + pi) - pi

    val t1 = norm(t1In)
    var ccw = positiveModulo(t2In - t1In)

    // Protilehlé konce jsou numericky citlivé: po liftu mezi průměty může být
    // rozdíl jednou o pár ulp menší a podruhé větší než PI. Přesnou půlelipsu
    // proto kanonizujeme na PI a část určí výhradně ArcMode.
    if (kotlin.math.abs(ccw - pi) <= halfTurnEps) ccw = pi

    val delta = when (mode) {
        ArcMode.CCW -> ccw
        ArcMode.CW -> if (ccw <= halfTurnEps) 0f else ccw - twoPi
        ArcMode.SHORTEST -> if (ccw <= pi) ccw else ccw - twoPi
        ArcMode.LONGEST -> when {
            ccw <= halfTurnEps -> -twoPi
            ccw >= pi -> ccw
            else -> ccw - twoPi
        }
    }
    return t1 to (t1 + delta)
}

/**
 * Vybere ze dvou 3D oblouků A–B ten, který obsahuje bod uvnitř oblouku zvoleného
 * v konkrétním průmětu. Na rozdíl od pouhého přenesení CCW/CW funguje i tehdy,
 * když projekce obrátí orientaci parametrizace (typicky nárys se z -> -y).
 */
fun normalizeArcThroughParameter(t1In: Float, t2In: Float, throughIn: Float): Pair<Float, Float> {
    val pi = kotlin.math.PI.toFloat()
    val twoPi = 2f * pi
    fun positiveModulo(value: Float): Float = (value % twoPi + twoPi) % twoPi
    fun norm(value: Float): Float = positiveModulo(value + pi) - pi

    val t1 = norm(t1In)
    val ccw = positiveModulo(t2In - t1In)
    val throughCcw = positiveModulo(throughIn - t1In)
    val followsCcw = throughCcw <= ccw + 1e-5f
    val delta = if (followsCcw) ccw else ccw - twoPi
    return t1 to (t1 + delta)
}

/** Bod uvnitř přesně toho eliptického oblouku, který určuje [mode] v daném průmětu. */
fun ellipseArcMidpointInProjection(
    p1: Offset,
    p2: Offset,
    p3: Offset,
    a: Offset,
    b: Offset,
    mode: ArcMode,
): Offset {
    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    val t1 = ellipseParamAndProjection(basis, a).first
    val t2 = ellipseParamAndProjection(basis, b).first
    val (start, end) = normalizeArcByMode(t1, t2, mode)
    val middle = (start + end) * 0.5f
    return basis.center + Offset(
        basis.a * kotlin.math.cos(middle) * basis.uN.x + basis.b * kotlin.math.sin(middle) * basis.vN.x,
        basis.a * kotlin.math.cos(middle) * basis.uN.y + basis.b * kotlin.math.sin(middle) * basis.vN.y,
    )
}

fun MongeState.findNarysConicIdByParent(parentId: String): String? =
    conicsNarys.firstOrNull { it.parentId == parentId || it.parent?.id == parentId }?.id
fun MongeState.findPudorysConicIdByParent(parentId: String): String? =
    conicsPudorys.firstOrNull { it.parentId == parentId || it.parent?.id == parentId }?.id
fun MongeState.findBokorysConicIdByParent(parentId: String): String? =
    conicsBokorys.firstOrNull { it.parentId == parentId || it.parent?.id == parentId }?.id
fun MongeState.findAxoConicIdByParent(parentId: String): String? =
    conicsAxo.firstOrNull { it.parentId == parentId || it.parent?.id == parentId }?.id

fun MongeState.propagateEllipseArcToBokorysAndAxo(
    parent: model.classes.ConicSection3D,
    A3D: model.Offset3D,
    B3D: model.Offset3D,
    mode: ArcMode
) {
    val bokorysId = findBokorysConicIdByParent(parent.id)
    if (bokorysId != null) {
        val inputsB = conicInputPointsBokorys[bokorysId]
        if (inputsB != null && inputsB.third != Offset.Unspecified) {
            val A_b = projectToEllipseFromDiameters(inputsB.first, inputsB.second, inputsB.third, Offset(A3D.y, A3D.z))
            val B_b = projectToEllipseFromDiameters(inputsB.first, inputsB.second, inputsB.third, Offset(B3D.y, B3D.z))
            ellipseArcEnds[bokorysId] = A_b to B_b
            ellipseArcMode[bokorysId] = mode
        }
    }
    val axoId = findAxoConicIdByParent(parent.id)
    if (axoId != null) {
        val inputsA = conicInputPointsAxo[axoId]
        if (inputsA != null && inputsA.third != Offset.Unspecified) {
            val basis = this.basis
            if (basis != null) {
                val A_a_raw = model.classes.projectPoint3DToAxoLocal(A3D, basis)
                val B_a_raw = model.classes.projectPoint3DToAxoLocal(B3D, basis)
                val A_a = projectToEllipseFromDiameters(inputsA.first, inputsA.second, inputsA.third, A_a_raw)
                val B_a = projectToEllipseFromDiameters(inputsA.first, inputsA.second, inputsA.third, B_a_raw)
                ellipseArcEnds[axoId] = A_a to B_a
                ellipseArcMode[axoId] = mode
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────

fun arcEllipsePudorys3D(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {

        "pudorys_elip3d_start" -> {
            val conicP = state.selectedConicsPudorys.lastOrNull()
                ?: return println("⚠️ Vyber nejdřív elipsu v půdorysu.")

            val parent = conicP.parent
                ?: return println("⚠️ Tahle 2D elipsa nemá parent 3D.")

            if (!isEllipseArcCompatible(parent)) {
                return println("⚠️ Parent 3D není elipsa.")
            }
            state.activeConicIdForArc = conicP.id
            setProjectionPhase("pudorys_elip3d_arc_hold", state)}
        "pudorys_elip3d_arc_hold"->{
            val conicP = state.conicsPudorys.find {it.id == state.activeConicIdForArc}?: return

            val parent = conicP.parent ?: return println("⚠️ Tahle 2D elipsa nemá parent 3D.")


            val inputs = state.conicInputPointsPudorys[state.activeConicIdForArc]
                ?: return println("⚠️ Chybí vstupy elipsy v půdorysu.")
            val (p1, p2, p3) = inputs
            if (p3 == Offset.Unspecified) return println("⚠️ Tohle není elipsa (p3 chybí).")
            // A₁ na 2D elipse (pudorys)
            state.pendingArcA_3D = projectToEllipseFromDiameters(p1, p2, p3, logical)
            state.activeParentConic3DIdForEllipseArc = parent.id
            state.activeConicIdForArc = conicP.id   // <<< ZAMKNOUT 2D elipsu

            println("✅ [3D] A (půdorys) uložen.")
            setProjectionPhase("pudorys_elip3d_arc", state)
        }

        "pudorys_elip3d_arc" -> {
            val conicId = state.activeConicIdForArc
                ?: return println("⚠️ Chybí zamknutá elipsa (restartni výběr).")

            val parentId = state.activeParentConic3DIdForEllipseArc
                ?: return println("⚠️ Chybí aktivní parent 3D.")
            val parent = state.conics3D.firstOrNull { it.id == parentId }
                ?: return println("⚠️ Parent 3D nenalezen.")

            val inputs = state.conicInputPointsPudorys[conicId]
                ?: return println("⚠️ Chybí vstupy elipsy v půdorysu.")
            val (p1, p2, p3) = inputs
            if (p3 == Offset.Unspecified) return println("⚠️ Tohle není elipsa (p3 chybí).")

            val A_p = state.pendingArcA_3D
                ?: run { setProjectionPhase("pudorys_elip3d_start", state); return println("⚠️ Chybí A-půdorys.") }

            // ✨ I když klikneš MIMO, B vezmeme jako projekci kurzoru na ZAMKNUTOU elipsu:
            val B_p = projectToEllipseFromDiameters(p1, p2, p3, logical)

            // … dál vše beze změny …
            val (A3D, A_n_raw) = liftPudorysPointTo3DAndNarys(A_p.x, A_p.y, parent) ?: return
            val (B3D, B_n_raw) = liftPudorysPointTo3DAndNarys(B_p.x, B_p.y, parent) ?: return

            val narysId = state.findNarysConicIdByParent(parent.id)
            val (A_n, B_n) = if (narysId != null) {
                val A_fixed = state.snapToNarysEllipseFixSign(narysId, A_n_raw)
                val B_fixed = state.snapToNarysEllipseFixSign(narysId, B_n_raw)
                val (n1, n2, n3) = state.conicInputPointsNarys[narysId]!!
                projectToEllipseFromDiameters(n1, n2, n3, A_fixed) to
                        projectToEllipseFromDiameters(n1, n2, n3, B_fixed)
            } else A_n_raw to B_n_raw

            val t1 = ellipseParamTForPoint3D(parent, A3D)
            val t2 = ellipseParamTForPoint3D(parent, B3D)
            val mode = state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST
            val middleP = ellipseArcMidpointInProjection(p1, p2, p3, A_p, B_p, mode)
            val middle3D = liftPudorysPointTo3DAndNarys(middleP.x, middleP.y, parent)?.first
            val (t1n, t2n) = middle3D?.let {
                normalizeArcThroughParameter(t1, t2, ellipseParamTForPoint3D(parent, it))
            } ?: normalizeArcByMode(t1, t2, mode)

            state.setEllipseArc(conicId, A_p, B_p, mode)
            narysId?.let { nId ->
                state.ellipseArcEnds[nId] = A_n to B_n
                state.ellipseArcMode[nId] = mode
            }
            state.ellipseArcParams3D[parent.id] = t1n to t2n
            state.ellipseArcEnds3D[parent.id]   = A3D to B3D
            state.propagateEllipseArcToBokorysAndAxo(parent, A3D, B3D, mode)
            commitSnapshot(state)
            state.pendingArcA_3D = null
            state.activeParentConic3DIdForEllipseArc = null
            state.activeConicIdForArc = null
            state.activeArcMode = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("pudorys_start", state)
            state.triggerRedraw++

        }

    }
}
fun liftNarysPointTo3DAndPudorys(
    x: Float,
    z: Float,
    conic: ConicSection3D
): Pair<Offset3D, Offset>? {
    val eq = planeEquation(conic)
    if (!eq.b.isFinite() || kotlin.math.abs(eq.b) < EPS) return null // rovina ~ kolmá k XZ → nelze z XZ zvedat
    val y = -(eq.a * x + eq.c * z + eq.d) / eq.b
    val p3 = Offset3D(x, y, z)
    val pP = Offset(x, y) // logický půdorys (x,y)
    return p3 to pP
}


fun MongeState.snapToPudorysEllipseFixSign(pudorysId: String, p: Offset): Offset {
    val inputsP = conicInputPointsPudorys[pudorysId] ?: return p
    val (p1, p2, p3) = inputsP
    if (p3 == Offset.Unspecified) return p

    fun sqr(x: Float) = x * x
    fun dist2(a: Offset, b: Offset) = sqr(a.x - b.x) + sqr(a.y - b.y)

    val snap1 = projectToEllipseFromDiameters(p1, p2, p3, p)
    val d1 = dist2(p, snap1)

    val pFlip = Offset(p.x, -p.y)
    val snap2 = projectToEllipseFromDiameters(p1, p2, p3, pFlip)
    val d2 = dist2(pFlip, snap2)

    return if (d2 < d1) pFlip else p
}

fun arcEllipseNarys3D(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {

        "narys_elip3d_start" -> {
            val conicN = state.selectedConicsNarys.lastOrNull()
                ?: return println("⚠️ Vyber nejdřív elipsu v nárysu.")

            val parent = conicN.parent
                ?: return println("⚠️ Tahle 2D elipsa nemá parent 3D.")
            if (!isEllipseArcCompatible(parent)) {
                return println("⚠️ Parent 3D není elipsa.")
            }
            state.activeConicIdForArc= conicN.id

            setProjectionPhase("narys_elip3d_arc_hold", state)}
            // A₂ na 2D elipse (nárys)
        "narys_elip3d_arc_hold"->{
            val conicN = state.conicsNarys.find{ it.id == state.activeConicIdForArc}
                ?: return println("⚠️ Vyber nejdřív elipsu v nárysu.")

            val parent = conicN.parent
                ?: return println("⚠️ Tahle 2D elipsa nemá parent 3D.")
            if (!isEllipseArcCompatible(parent)) {
                return println("⚠️ Parent 3D není elipsa.")
            }

            val inputs = state.conicInputPointsNarys[conicN.id]
                ?: return println("⚠️ Chybí vstupy elipsy v nárysu.")
            val (p1, p2, p3) = inputs
            if (p3 == Offset.Unspecified) return println("⚠️ Tohle není elipsa (p3 chybí).")
            state.pendingArcA_3D = projectToEllipseFromDiameters(p1, p2, p3, logical)
            state.activeParentConic3DIdForEllipseArc = parent.id
            state.activeConicIdForArc = conicN.id      // <<< ZAMKNOUT elipsu v N

            println("✅ [3D] A (nárys) uložen.")
            setProjectionPhase("narys_elip3d_arc", state)
        }

        "narys_elip3d_arc" -> {
            // <<< NEČÍST aktuální výběr, ale použít zamknuté ID
            val conicId = state.activeConicIdForArc
                ?: return println("⚠️ Chybí zamknutá elipsa v nárysu (restartni výběr).")

            val parentId = state.activeParentConic3DIdForEllipseArc
                ?: return println("⚠️ Chybí aktivní parent 3D.")
            val parent = state.conics3D.firstOrNull { it.id == parentId }
                ?: return println("⚠️ Parent 3D nenalezen.")

            val inputs = state.conicInputPointsNarys[conicId]
                ?: return println("⚠️ Chybí vstupy elipsy v nárysu.")
            val (p1, p2, p3) = inputs
            if (p3 == Offset.Unspecified) return println("⚠️ Tohle není elipsa (p3 chybí).")

            val A_n = state.pendingArcA_3D
                ?: run {
                    setProjectionPhase("narys_elip3d_start", state)
                    return println("⚠️ Chybí A-nárys.")
                }

            // ✨ I když klikneš MIMO, B vezmeme jako projekci kurzoru na ZAMKNUTOU nárysovou elipsu:
            val B_n = projectToEllipseFromDiameters(p1, p2, p3, logical)

            // ZVEDNUTÍ z nárysu → 3D → hrubý PŮDORYS (x,y)
            val (A3D, A_p_raw) = liftNarysPointTo3DAndPudorys(A_n.x, -A_n.y, parent) ?: return
            val (B3D, B_p_raw) = liftNarysPointTo3DAndPudorys(B_n.x, -B_n.y, parent) ?: return

            val pudorysId = state.findPudorysConicIdByParent(parent.id)
            val (A_p, B_p) = if (pudorysId != null) {
                val A_fixed = state.snapToPudorysEllipseFixSign(pudorysId, A_p_raw)
                val B_fixed = state.snapToPudorysEllipseFixSign(pudorysId, B_p_raw)
                val (q1, q2, q3) = state.conicInputPointsPudorys[pudorysId]!!
                projectToEllipseFromDiameters(q1, q2, q3, A_fixed) to
                        projectToEllipseFromDiameters(q1, q2, q3, B_fixed)
            } else A_p_raw to B_p_raw

            val dxA = kotlin.math.abs(A_n.x - A_p.x)
            val dxB = kotlin.math.abs(B_n.x - B_p.x)
            if (dxA > 1e-4f || dxB > 1e-4f) {
                println("❗ Nesoulad X (N↔P): |A|=$dxA |B|=$dxB – zkontroluj flip/skalování os.")
            }

            val t1 = ellipseParamTForPoint3D(parent, A3D)
            val t2 = ellipseParamTForPoint3D(parent, B3D)
            val mode = state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST
            val middleN = ellipseArcMidpointInProjection(p1, p2, p3, A_n, B_n, mode)
            val middle3D = liftNarysPointTo3DAndPudorys(middleN.x, -middleN.y, parent)?.first
            val (t1n, t2n) = middle3D?.let {
                normalizeArcThroughParameter(t1, t2, ellipseParamTForPoint3D(parent, it))
            } ?: normalizeArcByMode(t1, t2, mode)

            // NÁRYS – nastav oblouk (na zamknuté elipse)
            state.setEllipseArc(conicId, A_n, B_n, mode)

            // PŮDORYS – ulož konce + stejný mode
            pudorysId?.let { pId ->
                state.ellipseArcEnds[pId] = A_p to B_p
                state.ellipseArcMode[pId] = mode
            }

            // 3D – parametry + konce
            state.ellipseArcParams3D[parent.id] = t1n to t2n
            state.ellipseArcEnds3D[parent.id]   = A3D to B3D
            state.propagateEllipseArcToBokorysAndAxo(parent, A3D, B3D, mode)
            commitSnapshot(state)
            state.pendingArcA_3D = null
            state.activeParentConic3DIdForEllipseArc = null
            state.activeConicIdForArc = null
            state.activeArcMode = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("pudorys_start", state)
            state.triggerRedraw++

        }
    }
}
