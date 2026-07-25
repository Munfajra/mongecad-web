package monge.input.ConicArcs.associated

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.previews.conicsarcs.projectToParabola
import serialization.commitSnapshot
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import monge.input.ConicArcs.single.getLogicalCursorNarys
import geometry.conics.parabolaParamTForPoint3D
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.getLogicalCursor
import kotlin.math.max


// sign-fix + jemný snap na NÁRYSOVOU parabolu
fun MongeState.snapToNarysParabolaFixSign(narysId: String, p: Offset): Offset {
    val inputs = conicInputPointsNarys[narysId] ?: return p
    val (vertex, focus, third) = inputs
    if (conicsNarys.firstOrNull { it.id == narysId }?.isDegenerate == true) return p
    if (third != Offset.Unspecified) return p // není parabola
    fun err(q: Offset): Float {
        val s = projectToParabola(vertex, focus, q)
        val dx = q.x - s.x; val dy = q.y - s.y
        return dx*dx + dy*dy
    }
    val e1 = err(p)
    val pFlip = Offset(p.x, -p.y)
    val e2 = err(pFlip)
    return if (e2 < e1) pFlip else p
}

// sign-fix + jemný snap na PŮDORYSNOU parabolu
fun MongeState.snapToPudorysParabolaFixSign(pudorysId: String, p: Offset): Offset {
    val inputs = conicInputPointsPudorys[pudorysId] ?: return p
    val (vertex, focus, third) = inputs
    if (third != Offset.Unspecified) return p // není parabola
    fun err(q: Offset): Float {
        val s = projectToParabola(vertex, focus, q)
        val dx = q.x - s.x; val dy = q.y - s.y
        return dx*dx + dy*dy
    }
    val e1 = err(p)
    val pFlip = Offset(p.x, -p.y)
    val e2 = err(pFlip)
    return if (e2 < e1) pFlip else p
}

private fun projectOnDegenerateCarrier(origin: Offset, dirIn: Offset, isLine: Boolean, point: Offset): Offset {
    var dir = dirIn
    val len = dir.getDistance()
    dir = if (len < 1e-6f) Offset(1f, 0f) else dir / len
    fun projectWith(d: Offset): Offset {
        val t = (point.x - origin.x) * d.x + (point.y - origin.y) * d.y
        val tc = if (isLine) t else max(0f, t)
        return origin + d * tc
    }
    if (isLine) return projectWith(dir)

    // U polopřímky může být uložený směr obráceně; vyber bližší variantu.
    val p1 = projectWith(dir)
    val p2 = projectWith(Offset(-dir.x, -dir.y))
    return if ((point - p1).getDistanceSquared() <= (point - p2).getDistanceSquared()) p1 else p2
}

fun MongeState.projectToNarysParabolaConsideringDegeneracy(narysId: String, p: Offset): Offset {
    val inputs = conicInputPointsNarys[narysId] ?: return p
    val (vertex, focus, third) = inputs
    if (third != Offset.Unspecified) return p
    val conic = conicsNarys.firstOrNull { it.id == narysId }
    if (conic?.isDegenerate == true) {
        val dir = conic.degenerateDir ?: (focus - vertex)
        return projectOnDegenerateCarrier(vertex, dir, conic.isLineDegenerate, p)
    }
    return projectToParabola(vertex, focus, p)
}

fun MongeState.projectToPudorysParabolaConsideringDegeneracy(pudorysId: String, p: Offset): Offset {
    val inputs = conicInputPointsPudorys[pudorysId] ?: return p
    val (vertex, focus, third) = inputs
    if (third != Offset.Unspecified) return p
    val conic = conicsPudorys.firstOrNull { it.id == pudorysId }
    if (conic?.isDegenerate == true) {
        val dir = conic.degenerateDir ?: (focus - vertex)
        return projectOnDegenerateCarrier(vertex, dir, conic.isLineDegenerate, p)
    }
    return projectToParabola(vertex, focus, p)
}

fun arcParabolaPudorys3D(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
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

        "pudorys_par3d_start" -> {
            val conicP = state.selectedConicsPudorys.lastOrNull()
                ?: return println("⚠️ Vyber nejdřív parabolu v půdorysu.")
            val inputs = state.conicInputPointsPudorys[conicP.id]
                ?: return println("⚠️ Chybí vstupy paraboly v půdorysu.")
            val (_, _, third) = inputs
            if (third != Offset.Unspecified || state.hyperbolaInputsPudorys.containsKey(conicP.id)) {
                return println("⚠️ Zvolená kuželosečka není parabola.")
            }
            val parent = conicP.parent ?: return println("⚠️ Tahle 2D parabola nemá parent 3D.")

            // zamkni kontext
            state.activeConicIdForArc = conicP.id
            state.activeParentConic3DIdForEllipseArc = parent.id // recyklujeme existující pole
            setProjectionPhase("pudorys_par3d_arc_hold", state)}
        "pudorys_par3d_arc_hold"->{
            val conicP = state.activeConicIdForArc
                ?: return println("⚠️ Chybí zamknutá parabola v půdorysu.")
            val inputs = state.conicInputPointsPudorys[conicP]
                ?: return println("⚠️ Chybí vstupy paraboly v půdorysu.")
            val (vertex, focus, _) = inputs
            // A na parabole v P
            state.pendingArcA_3D = projectToParabola(vertex, focus, logical)
            println("✅ [P→3D] A-půdorys uložen.")
            setProjectionPhase("pudorys_par3d_arc", state)
        }

        "pudorys_par3d_arc" -> {
            val conicId = state.activeConicIdForArc
                ?: return println("⚠️ Chybí zamknutá parabola v půdorysu.")
            val parentId = state.activeParentConic3DIdForEllipseArc
                ?: return println("⚠️ Chybí parent 3D.")
            val parent = state.conics3D.firstOrNull { it.id == parentId }
                ?: return println("⚠️ Parent 3D nenalezen.")

            val inputs = state.conicInputPointsPudorys[conicId]
                ?: return println("⚠️ Chybí vstupy paraboly v půdorysu.")
            val (vertex, focus, third) = inputs
            if (third != Offset.Unspecified) return println("⚠️ Už to není parabola.")

            val A_p = state.pendingArcA_3D
                ?: return println("⚠️ Chybí A-půdorys.")
            val B_p = projectToParabola(vertex, focus, logical)

            // lift P→3D→N
            val (A3D, A_n_raw) = liftPudorysPointTo3DAndNarys(A_p.x, A_p.y, parent) ?: return
            val (B3D, B_n_raw) = liftPudorysPointTo3DAndNarys(B_p.x, B_p.y, parent) ?: return

            // sestra v N: sign-fix + jemný snap
            val narysId = state.findNarysConicIdByParent(parent.id)
            val (A_n, B_n) = if (narysId != null) {
                val A_fixed = state.snapToNarysParabolaFixSign(narysId, A_n_raw)
                val B_fixed = state.snapToNarysParabolaFixSign(narysId, B_n_raw)
                state.projectToNarysParabolaConsideringDegeneracy(narysId, A_fixed) to
                        state.projectToNarysParabolaConsideringDegeneracy(narysId, B_fixed)
            } else A_n_raw to B_n_raw

            // ulož 2D konce
            state.parabolaArcEnds[conicId] = A_p to B_p
            narysId?.let { state.parabolaArcEnds[it] = A_n to B_n }
            // úklid
            val t1 = parabolaParamTForPoint3D(parent, A3D)
            val t2 = parabolaParamTForPoint3D(parent, B3D)

            state.parabolaArcParams3D[parent.id] = t1 to t2
            state.parabolaArcEnds3D[parent.id]   = A3D to B3D
            // state.parabolaArcEnds3D[parent.id] = A3D to B3D

            commitSnapshot(state)
            state.pendingArcA_3D = null
            state.activeConicIdForArc = null
            state.activeParentConic3DIdForEllipseArc = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("pudorys_start", state)
            state.triggerRedraw++

            println("🎯 [P→N] Parabolický oblouk propagován i do nárysu.")

        }
    }
}

fun arcParabolaNarys3D(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
    val logical = getLogicalCursorNarys(
        snappedPointLogical,
        cursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection
    )

    when (state.projectionPhase) {

        // 1) první klik v N: zamkneme N-parabolu + A bod nasnapujeme v N
        "narys_par3d_start" -> {
            val conicN = state.selectedConicsNarys.lastOrNull()
                ?: return println("⚠️ Vyber nejdřív parabolu v nárysu.")

            val inputs = state.conicInputPointsNarys[conicN.id]
                ?: return println("⚠️ Chybí vstupy paraboly v nárysu.")
            val (_, _, third) = inputs
            if (third != Offset.Unspecified || state.hyperbolaInputsNarys.containsKey(conicN.id)) {
                return println("⚠️ Není to parabola.")
            }

            val parent = conicN.parent
                ?: return println("⚠️ Tahle 2D parabola nemá parent 3D.")

            // zamknout kontext mezi kliky
            state.activeConicIdForArc = conicN.id
            state.activeParentConic3DIdForEllipseArc = parent.id
            setProjectionPhase("narys_par3d_arc_hold", state)}
        "narys_par3d_arc_hold"->{
            val conicN = state.activeConicIdForArc
                ?: return println("⚠️ Chybí zamknutá parabola v nárysu.")
            val inputs = state.conicInputPointsNarys[conicN]
                ?: return println("⚠️ Chybí vstupy paraboly v nárysu.")
            val (vertex, focus, _) = inputs
            // A₂ v N (snap)
            state.pendingArcA_3D = projectToParabola(vertex, focus, logical)

            println("✅ [N] A-bod parab. oblouku uložen.")
            setProjectionPhase("narys_par3d_arc", state)
        }

        // 2) druhý klik v N: B nasnapujeme v N → zvedneme oba do 3D → dopočteme P
        "narys_par3d_arc" -> {
            val conicId = state.activeConicIdForArc
                ?: return println("⚠️ Chybí zamknutá parabola v nárysu.")
            val parentId = state.activeParentConic3DIdForEllipseArc
                ?: return println("⚠️ Chybí aktivní parent 3D.")
            val parent = state.conics3D.firstOrNull { it.id == parentId }
                ?: return println("⚠️ Parent 3D nenalezen.")

            val inputs = state.conicInputPointsNarys[conicId]
                ?: return println("⚠️ Chybí vstupy paraboly v nárysu.")
            val (vertex, focus, third) = inputs
            if (third != Offset.Unspecified) return println("⚠️ Už to není parabola.")

            val A_n = state.pendingArcA_3D
                ?: run { setProjectionPhase("narys_par3d_start", state); return println("⚠️ Chybí A-nárys.") }

            // i když klikneš mimo, B se promítne na zamknutou nárysovou parabolu
            val B_n = projectToParabola(vertex, focus, logical)

            // N → 3D → P
            val (A3D, A_p_raw) = liftNarysPointTo3DAndPudorys(A_n.x, A_n.y, parent) ?: return
            val (B3D, B_p_raw) = liftNarysPointTo3DAndPudorys(B_n.x, B_n.y, parent) ?: return

            // sesterská parabola v P: sign-fix + jemný snap
            val pudorysId = state.findPudorysConicIdByParent(parent.id)
            val (A_p, B_p) = if (pudorysId != null) {
                val A_fixed = state.snapToPudorysParabolaFixSign(pudorysId, A_p_raw)
                val B_fixed = state.snapToPudorysParabolaFixSign(pudorysId, B_p_raw)
                state.projectToPudorysParabolaConsideringDegeneracy(pudorysId, A_fixed) to
                        state.projectToPudorysParabolaConsideringDegeneracy(pudorysId, B_fixed)
            } else A_p_raw to B_p_raw

            // sanity: X shoda mezi průměty
            val dxA = kotlin.math.abs(A_n.x - A_p.x)
            val dxB = kotlin.math.abs(B_n.x - B_p.x)
            if (dxA > 1e-4f || dxB > 1e-4f) {
                println("❗ Nesoulad X (N↔P) u paraboly: |A|=$dxA |B|=$dxB")
            }

            // 3D parametry pro OpenGL (t = projekce na û)
            val t1 = parabolaParamTForPoint3D(parent, A3D)
            val t2 = parabolaParamTForPoint3D(parent, B3D)
            state.parabolaArcParams3D[parent.id] = t1 to t2
            state.parabolaArcEnds3D[parent.id]   = A3D to B3D

            // ulož 2D konce
            state.parabolaArcEnds[conicId] = A_n to B_n
            pudorysId?.let { state.parabolaArcEnds[it] = A_p to B_p }
            commitSnapshot(state)
            // úklid
            state.pendingArcA_3D = null
            state.activeConicIdForArc = null
            state.activeParentConic3DIdForEllipseArc = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("narys_start", state)
            state.triggerRedraw++

            println("🎯 [N→3D→P] Parabolický oblouk uložen, t=[$t1..$t2]")

        }
    }
}
