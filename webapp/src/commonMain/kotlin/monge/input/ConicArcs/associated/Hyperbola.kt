package monge.input.ConicArcs.associated

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.conicarcs.HyperbolaBasis
import draw.mongescreen.conicarcs.hyperbolaBasisFrom
import draw.mongescreen.conicarcs.projectToHyperbola
import draw.mongescreen.conicarcs.snapNarysDegenerateHyperbola
import draw.mongescreen.conicarcs.snapPudorysDegenerateHyperbola
import serialization.commitSnapshot
import model.*
import model.classes.ConicSection3D
import monge.input.ConicArcs.single.getLogicalCursorNarys
import geometry.conics.ConicType
import geometry.conics.classifyConicFromMatrix
import state.MongeState
import state.snapMonge.computeIntersection
import ui.mongeui.toolbar.setProjectionPhase
import utils.dot
import utils.getLogicalCursor
import utils.normalize
import kotlin.math.ln
import kotlin.math.sqrt

/** Najdi sesterskou hyperbolu v N podle parent 3D id. */
private fun MongeState.findNarysHyperbolaIdByParent(parentId: String): String? =
    conicsNarys.firstOrNull { it.parent?.id == parentId }?.id

/** Postav basis hyperboly v 2D z uložených vstupů. */
private fun buildHyperbolaBasis2D_N(state: MongeState, narysId: String): HyperbolaBasis? {
    val input = state.hyperbolaInputsNarys[narysId] ?: return null
    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.x, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.x, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex
    return hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
}

/** Snapni bod v N na hyperbolu; zvol větev podle znaménka projekce na ex. */
private fun snapNarysPointToHyperbola(state: MongeState, narysId: String, p: Offset): Offset {
    state.snapNarysDegenerateHyperbola(narysId, p)?.let { return it.point }
    val basis = buildHyperbolaBasis2D_N(state, narysId) ?: return p
    val sx = if ((p - basis.center).dot(basis.ex) >= 0f) +1 else -1
    val proj = projectToHyperbola(basis, p, sx) ?: return p
    return proj.on
}

/** Snapni bod v P na hyperbolu dané větve (basis už znáš, sx zůstává konstantní v rámci větve). */
private fun snapPudorysPointToHyperbolaOnBranch(basis: HyperbolaBasis, p: Offset, sx: Int): Offset {
    val proj = projectToHyperbola(basis, p, sx) ?: return p
    return proj.on
}

fun arcHyperbolaPudorys3D(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
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

        // 1) vyber hyperbolu v P (parent musí být 3D hyperbola)
        "pudorys_hyp3d_start" -> {
            val conicP = state.selectedConicsPudorys.lastOrNull()
                ?: return println("⚠️ Vyber nejdřív hyperbolu v půdorysu.")
            val parent = conicP.parent
                ?: return println("⚠️ Tahle 2D hyperbola nemá parent 3D.")
            if (classifyConicFromMatrix(parent.matrix) != ConicType.HYPERBOLA) {
                return println("⚠️ Parent 3D není hyperbola.")
            }

            state.activeConicIdForArc = conicP.id
            state.activeParentConic3DIdForEllipseArc = parent.id
            setProjectionPhase("pudorys_hyp3d_b1_hold", state)}
        "pudorys_hyp3d_b1_hold"->{
            val conicP = state.activeConicIdForArc
                ?: return println("⚠️ Chybí aktivní hyperbola (P).")
            val inputP = state.hyperbolaInputsPudorys[conicP]
                ?: return println("⚠️ Tahle kuželosečka v P není hyperbola (chybí vstupy).")
            // postav 2D basis v P (jako v tvém 2D handleru)
            val v1 = inputP.line1.direction.normalize()
            val centerP = computeIntersection(
                Offset(inputP.line1.point.x, inputP.line1.point.y), Offset(inputP.line1.direction.x, inputP.line1.direction.y),
                Offset(inputP.line2.point.x, inputP.line2.point.y), Offset(inputP.line2.direction.x, inputP.line2.direction.y)
            ) ?: inputP.vertex
            val basisP = hyperbolaBasisFrom(centerP, inputP.vertex, v1, inputP.axis)

            // zamkni kontext


            // A1 na P – urč větvení a snap
            val degA1 = state.snapPudorysDegenerateHyperbola(conicP, logical)
            val sx1 = degA1?.side?.takeIf { it != 0 } ?: if ((logical - basisP.center).dot(basisP.ex) >= 0f) +1 else -1
            val A1_p = degA1?.point ?: snapPudorysPointToHyperbolaOnBranch(basisP, logical, sx1)

            // uložit pending pro větev 1 (P + SX), ať máme konzistenci do B1
            state.pendingHypA1 = A1_p
            state.pendingHypBranch1SX = sx1

            println("✅ [H3D] A1 (P) uložen, větev=${if (sx1>0) "pravá" else "levá"}.")
            setProjectionPhase("pudorys_hyp3d_b1", state)
        }

        // 2) B1 v P, zvednutí A1,B1 -> 3D a propagace do N
        "pudorys_hyp3d_b1" -> {
            val conicIdP = state.activeConicIdForArc ?: return println("⚠️ Chybí aktivní hyperbola (P).")
            val parentId = state.activeParentConic3DIdForEllipseArc ?: return println("⚠️ Chybí parent 3D.")
            val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return println("⚠️ Parent 3D nenalezen.")
            val inputP = state.hyperbolaInputsPudorys[conicIdP] ?: return println("⚠️ Chybí vstupy hyperboly v P.")

            // basis v P
            val v1 = inputP.line1.direction.normalize()
            val centerP = computeIntersection(
                Offset(inputP.line1.point.x, inputP.line1.point.y), Offset(inputP.line1.direction.x, inputP.line1.direction.y),
                Offset(inputP.line2.point.x, inputP.line2.point.y), Offset(inputP.line2.direction.x, inputP.line2.direction.y)
            ) ?: inputP.vertex
            val basisP = hyperbolaBasisFrom(centerP, inputP.vertex, v1, inputP.axis)

            val A1_p = state.pendingHypA1 ?: return println("⚠️ Chybí A1 (P).")
            val sx1 = state.pendingHypBranch1SX ?: +1
            val B1_p = state.snapPudorysDegenerateHyperbola(conicIdP, logical, sx1)?.point
                ?: snapPudorysPointToHyperbolaOnBranch(basisP, logical, sx1)

            // P -> 3D -> N
            val (A1_3D, A1_n_raw) = liftPudorysPointTo3DAndNarys(A1_p.x, A1_p.y, parent) ?: return
            val (B1_3D, B1_n_raw) = liftPudorysPointTo3DAndNarys(B1_p.x, B1_p.y, parent) ?: return

            // najdi sesterskou hyperbolu v N a dosnapuj konce i v N
            val narysId = state.findNarysHyperbolaIdByParent(parent.id)
            val (A1_n, B1_n) = if (narysId != null) {
                val degA = state.snapNarysDegenerateHyperbola(narysId, A1_n_raw)
                val degB = state.snapNarysDegenerateHyperbola(narysId, B1_n_raw, degA?.side)
                val A_on = degA?.point ?: snapNarysPointToHyperbola(state, narysId, A1_n_raw)
                val B_on = degB?.point ?: snapNarysPointToHyperbola(state, narysId, B1_n_raw)
                A_on to B_on
            } else A1_n_raw to B1_n_raw

            // 3D parametry a 3D konce pro větev 1
            val (t1, t2) = computeHyperbolaParams(parent, A1_3D, B1_3D)
            state.hyperbolaArcParams3D[parent.id] = (t1 to t2) to null
            state.hyperbolaArcEnds3D[parent.id]   = (A1_3D to B1_3D) to null

            // 2D konce v P a N
            state.hyperbolaArcBranch1[conicIdP] = A1_p to B1_p
            narysId?.let { state.hyperbolaArcBranch1[it] = A1_n to B1_n }

            // úklid pending 1. větve a přechod na 2. větev (volitelné)
            state.pendingHypA1 = null
            state.pendingHypBranch1SX = null

            println("🎯 [H3D] Větev 1 hotová. Klikni A2 (nebo Enter pro přeskočení).")
            setProjectionPhase("pudorys_hyp3d_a2", state)
        }

        // 3) A2 v P (volitelné)
        "pudorys_hyp3d_a2" -> {
            val conicIdP = state.activeConicIdForArc ?: return
            val inputP = state.hyperbolaInputsPudorys[conicIdP] ?: return

            val v1 = inputP.line1.direction.normalize()
            val centerP = computeIntersection(
                Offset(inputP.line1.point.x, inputP.line1.point.y), Offset(inputP.line1.direction.x, inputP.line1.direction.y),
                Offset(inputP.line2.point.x, inputP.line2.point.y), Offset(inputP.line2.direction.x, inputP.line2.direction.y)
            ) ?: inputP.vertex
            val basisP = hyperbolaBasisFrom(centerP, inputP.vertex, v1, inputP.axis)

            val degA2 = state.snapPudorysDegenerateHyperbola(conicIdP, logical)
            val sx2 = degA2?.side?.takeIf { it != 0 } ?: if ((logical - basisP.center).dot(basisP.ex) >= 0f) +1 else -1
            val A2_p = degA2?.point ?: snapPudorysPointToHyperbolaOnBranch(basisP, logical, sx2)

            state.pendingHypA2 = A2_p
            state.pendingHypBranch2SX = sx2

            println("✅ [H3D] A2 (P) uložen, větev=${if (sx2>0) "pravá" else "levá"}. Klikni B2.")
            setProjectionPhase("pudorys_hyp3d_b2", state)
        }

        // 4) B2 v P -> 3D -> N, zapsat druhou větev (nebo použij skip)
        "pudorys_hyp3d_b2" -> {
            val conicIdP = state.activeConicIdForArc ?: return
            val parentId = state.activeParentConic3DIdForEllipseArc ?: return
            val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return
            val inputP = state.hyperbolaInputsPudorys[conicIdP] ?: return

            val v1 = inputP.line1.direction.normalize()
            val centerP = computeIntersection(
                Offset(inputP.line1.point.x, inputP.line1.point.y), Offset(inputP.line1.direction.x, inputP.line1.direction.y),
                Offset(inputP.line2.point.x, inputP.line2.point.y), Offset(inputP.line2.direction.x, inputP.line2.direction.y)
            ) ?: inputP.vertex
            val basisP = hyperbolaBasisFrom(centerP, inputP.vertex, v1, inputP.axis)

            val A2_p = state.pendingHypA2 ?: return println("⚠️ Chybí A2 (P).")
            val sx2 = state.pendingHypBranch2SX ?: +1
            val B2_p = state.snapPudorysDegenerateHyperbola(conicIdP, logical, sx2)?.point
                ?: snapPudorysPointToHyperbolaOnBranch(basisP, logical, sx2)

            // P -> 3D -> N
            val (A2_3D, A2_n_raw) = liftPudorysPointTo3DAndNarys(A2_p.x, A2_p.y, parent) ?: return
            val (B2_3D, B2_n_raw) = liftPudorysPointTo3DAndNarys(B2_p.x, B2_p.y, parent) ?: return

            val narysId = state.findNarysHyperbolaIdByParent(parent.id)
            val (A2_n, B2_n) = if (narysId != null) {
                val degA = state.snapNarysDegenerateHyperbola(narysId, A2_n_raw)
                val degB = state.snapNarysDegenerateHyperbola(narysId, B2_n_raw, degA?.side)
                val A_on = degA?.point ?: snapNarysPointToHyperbola(state, narysId, A2_n_raw)
                val B_on = degB?.point ?: snapNarysPointToHyperbola(state, narysId, B2_n_raw)
                A_on to B_on
            } else A2_n_raw to B2_n_raw

            // 3D parametry a konce pro větev 2 (doplnění do mapy)
            val (firstParams, _) = state.hyperbolaArcParams3D[parent.id] ?: (null to null)
            val (t3, t4) = computeHyperbolaParams(parent, A2_3D, B2_3D)
            state.hyperbolaArcParams3D[parent.id] = firstParams to (t3 to t4)

            val (firstEnds, _) = state.hyperbolaArcEnds3D[parent.id] ?: (null to null)
            state.hyperbolaArcEnds3D[parent.id] = firstEnds to (A2_3D to B2_3D)

            // 2D konce v P a N (větev 2)
            state.hyperbolaArcBranch2[conicIdP] = A2_p to B2_p
            narysId?.let { state.hyperbolaArcBranch2[it] = A2_n to B2_n }
            commitSnapshot(state)
            // úklid
            state.pendingHypA2 = null
            state.pendingHypBranch2SX = null
            state.activeConicIdForArc = null
            state.activeParentConic3DIdForEllipseArc = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("pudorys_start", state)
            state.triggerRedraw++

            println("🏁 [H3D] Větev 2 hotová. Konec.")

        }
    }
}

fun arcHyperbolaPudorys3DSkipSecond(state: MongeState) {
    if (state.projectionPhase == "pudorys_hyp3d_a2" || state.projectionPhase == "pudorys_hyp3d_b2") {
        state.pendingHypA2 = null
        state.pendingHypBranch2SX = null
        state.activeConicIdForArc = null
        state.activeParentConic3DIdForEllipseArc = null
        state.drawobjects = Mongeobjects.NONE
        commitSnapshot(state)
        setProjectionPhase("pudorys_start", state)
        state.triggerRedraw++
        println("⏭️ [H3D] Druhá větev hyperboly přeskočena.")
    }
}

private fun asinh(x: Float): Float = ln(x + sqrt(x * x + 1f))

fun hyperbolaLocalXY(conic: ConicSection3D, p3: Offset3D): Pair<Float, Float> {
    val uN = conic.u.normalized()
    val vGS = conic.v - uN * conic.v.dot(uN)
    val vN = vGS.normalized()
    val r = p3 - conic.p0
    val x = r.dot(uN)
    val y = r.dot(vN)
    return x to y
}


/**
 * Vrátí (t1, t2) pro dva 3D body na stejné větvi hyperboly.
 * Vyžaduje, aby conic.a a conic.b byly známé (stejně jako při vzorkování).
 */
fun computeHyperbolaParams(
    conic: ConicSection3D,
    A3D: Offset3D,
    B3D: Offset3D
): Pair<Float, Float> {
    conic.a ?: error("Missing hyperbola 'a'")
    val b = conic.b ?: error("Missing hyperbola 'b'")

    val (xA, yA) = hyperbolaLocalXY(conic, A3D)
    val (xB, yB) = hyperbolaLocalXY(conic, B3D)

    // větev určuje sign(x); kontrolně můžeš varovat, když jsou body na různých větvích
    if (xA * xB < 0f) {
        println("⚠️ computeHyperbolaParams: A a B jsou na různých větvích (sign(x) se liší). Beru větev dle A.")
    }

    val t1 = asinh(yA / b)
    val t2 = asinh(yB / b)
    return t1 to t2
}
/** Najdi sesterskou hyperbolu v P podle parent 3D id. */
private fun MongeState.findPudorysHyperbolaIdByParent(parentId: String): String? =
    conicsPudorys.firstOrNull { it.parent?.id == parentId }?.id

/** Postav 2D basis hyperboly v P z uložených vstupů. */
private fun buildHyperbolaBasis2D_P(state: MongeState, pudorysId: String): HyperbolaBasis? {
    val input = state.hyperbolaInputsPudorys[pudorysId] ?: return null
    val v1 = input.line1.direction.normalize()
    val center = computeIntersection(
        Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
        Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
    ) ?: input.vertex
    return hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
}

/** Snapni bod v P na hyperbolu; zvol větev podle znaménka projekce na ex. */
private fun snapPudorysPointToHyperbola(state: MongeState, pId: String, p: Offset): Offset {
    state.snapPudorysDegenerateHyperbola(pId, p)?.let { return it.point }
    val basis = buildHyperbolaBasis2D_P(state, pId) ?: return p
    val sx = if ((p - basis.center).dot(basis.ex) >= 0f) +1 else -1
    return projectToHyperbola(basis, p, sx)?.on ?: p
}

/** Snap v N na konkrétní větev (basis znáš, sx držíš konstantní v rámci větve). */
private fun snapNarysPointToHyperbolaOnBranch(basis: HyperbolaBasis, p: Offset, sx: Int): Offset {
    return projectToHyperbola(basis, p, sx)?.on ?: p
}
fun arcHyperbolaNarys3D(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
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

        // 1) výběr hyperboly v N + příprava
        "narys_hyp3d_start" -> {
            val conicN = state.selectedConicsNarys.lastOrNull()
                ?: return println("⚠️ Vyber nejdřív hyperbolu v nárysu.")
            val parent = conicN.parent
                ?: return println("⚠️ Tahle 2D hyperbola nemá parent 3D.")
            if (classifyConicFromMatrix(parent.matrix) != ConicType.HYPERBOLA) {
                return println("⚠️ Parent 3D není hyperbola.")
            }

            // zamkni kontext
            state.activeConicIdForArc = conicN.id
            state.activeParentConic3DIdForEllipseArc = parent.id
            setProjectionPhase("narys_hyp3d_b1_hold", state)
        }
        "narys_hyp3d_b1_hold" ->{
            val conicN = state.activeConicIdForArc
                ?: return println("⚠️ Chybí aktivní hyperbola (N).")
            val inputN = state.hyperbolaInputsNarys[conicN]
                ?: return println("⚠️ Tahle kuželosečka v N není hyperbola (chybí vstupy).")
            // postav basis v N
            val v1 = inputN.line1.direction.normalize()
            val centerN = computeIntersection(
                Offset(inputN.line1.point.x, inputN.line1.point.z), Offset(inputN.line1.direction.x, inputN.line1.direction.y),
                Offset(inputN.line2.point.x, inputN.line2.point.z), Offset(inputN.line2.direction.x, inputN.line2.direction.y)
            ) ?: inputN.vertex
            val basisN = hyperbolaBasisFrom(centerN, inputN.vertex, v1, inputN.axis)



            // A1 v N – urč větev a snap
            val degA1 = state.snapNarysDegenerateHyperbola(conicN, logical)
            val sx1 = degA1?.side?.takeIf { it != 0 } ?: if ((logical - basisN.center).dot(basisN.ex) >= 0f) +1 else -1
            val A1_n = degA1?.point ?: snapNarysPointToHyperbolaOnBranch(basisN, logical, sx1)

            state.pendingHypA1 = A1_n
            state.pendingHypBranch1SX = sx1

            println("✅ [H3D N] A1 (N) uložen, větev=${if (sx1>0) "pravá" else "levá"}.")
            setProjectionPhase("narys_hyp3d_b1", state)
        }

        // 2) B1 v N → 3D → P, zapsat větev 1
        "narys_hyp3d_b1" -> {
            val conicIdN = state.activeConicIdForArc ?: return println("⚠️ Chybí aktivní hyperbola (N).")
            val parentId = state.activeParentConic3DIdForEllipseArc ?: return println("⚠️ Chybí parent 3D.")
            val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return println("⚠️ Parent 3D nenalezen.")
            val inputN = state.hyperbolaInputsNarys[conicIdN] ?: return println("⚠️ Chybí vstupy hyperboly v N.")

            // basis v N
            val v1 = inputN.line1.direction.normalize()
            val centerN = computeIntersection(
                Offset(inputN.line1.point.x, inputN.line1.point.z), Offset(inputN.line1.direction.x, inputN.line1.direction.y),
                Offset(inputN.line2.point.x, inputN.line2.point.z), Offset(inputN.line2.direction.x, inputN.line2.direction.y)
            ) ?: inputN.vertex
            val basisN = hyperbolaBasisFrom(centerN, inputN.vertex, v1, inputN.axis)

            val A1_n = state.pendingHypA1 ?: return println("⚠️ Chybí A1 (N).")
            val sx1 = state.pendingHypBranch1SX ?: +1
            val B1_n = state.snapNarysDegenerateHyperbola(conicIdN, logical, sx1)?.point
                ?: snapNarysPointToHyperbolaOnBranch(basisN, logical, sx1)

            // N -> 3D -> P  (pozor: getLogicalCursorNarys už má správné y; do liftu posíláme (x,y) bez mínusu)
            val (A1_3D, A1_p_raw) = liftNarysPointTo3DAndPudorys(A1_n.x, A1_n.y, parent) ?: return
            val (B1_3D, B1_p_raw) = liftNarysPointTo3DAndPudorys(B1_n.x, B1_n.y, parent) ?: return

            // sestra v P: dosnap na hyperbolu v P
            val pudorysId = state.findPudorysHyperbolaIdByParent(parent.id)
            val (A1_p, B1_p) = if (pudorysId != null) {
                val degA = state.snapPudorysDegenerateHyperbola(pudorysId, A1_p_raw)
                val degB = state.snapPudorysDegenerateHyperbola(pudorysId, B1_p_raw, degA?.side)
                val A_on = degA?.point ?: snapPudorysPointToHyperbola(state, pudorysId, A1_p_raw)
                val B_on = degB?.point ?: snapPudorysPointToHyperbola(state, pudorysId, B1_p_raw)
                A_on to B_on
            } else A1_p_raw to B1_p_raw

            // 3D parametry a konce pro větev 1
            val (t1, t2) = computeHyperbolaParams(parent, A1_3D, B1_3D)
            state.hyperbolaArcParams3D[parent.id] = (t1 to t2) to null
            state.hyperbolaArcEnds3D[parent.id]   = (A1_3D to B1_3D) to null

            // 2D konce v N a P
            state.hyperbolaArcBranch1[conicIdN] = A1_n to B1_n
            pudorysId?.let { state.hyperbolaArcBranch1[it] = A1_p to B1_p }

            // úklid pendings + přechod na A2 (volitelné)
            state.pendingHypA1 = null
            state.pendingHypBranch1SX = null

            println("🎯 [H3D N] Větev 1 hotová. Klikni A2 (nebo Enter pro přeskočení).")
            setProjectionPhase("narys_hyp3d_a2", state)

        }

        // 3) A2 v N (volitelné)
        "narys_hyp3d_a2" -> {
            val conicIdN = state.activeConicIdForArc ?: return
            val inputN = state.hyperbolaInputsNarys[conicIdN] ?: return

            val v1 = inputN.line1.direction.normalize()
            val centerN = computeIntersection(
                Offset(inputN.line1.point.x, inputN.line1.point.z), Offset(inputN.line1.direction.x, inputN.line1.direction.y),
                Offset(inputN.line2.point.x, inputN.line2.point.z), Offset(inputN.line2.direction.x, inputN.line2.direction.y)
            ) ?: inputN.vertex
            val basisN = hyperbolaBasisFrom(centerN, inputN.vertex, v1, inputN.axis)

            val degA2 = state.snapNarysDegenerateHyperbola(conicIdN, logical)
            val sx2 = degA2?.side?.takeIf { it != 0 } ?: if ((logical - basisN.center).dot(basisN.ex) >= 0f) +1 else -1
            val A2_n = degA2?.point ?: snapNarysPointToHyperbolaOnBranch(basisN, logical, sx2)

            state.pendingHypA2 = A2_n
            state.pendingHypBranch2SX = sx2

            println("✅ [H3D N] A2 (N) uložen, větev=${if (sx2>0) "pravá" else "levá"}. Klikni B2.")
            setProjectionPhase("narys_hyp3d_b2", state)
        }

        // 4) B2 v N → 3D → P, zapsat větev 2
        "narys_hyp3d_b2" -> {
            val conicIdN = state.activeConicIdForArc ?: return
            val parentId = state.activeParentConic3DIdForEllipseArc ?: return
            val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return
            val inputN = state.hyperbolaInputsNarys[conicIdN] ?: return

            val v1 = inputN.line1.direction.normalize()
            val centerN = computeIntersection(
                Offset(inputN.line1.point.x, inputN.line1.point.z), Offset(inputN.line1.direction.x, inputN.line1.direction.y),
                Offset(inputN.line2.point.x, inputN.line2.point.z), Offset(inputN.line2.direction.x, inputN.line2.direction.y)
            ) ?: inputN.vertex
            val basisN = hyperbolaBasisFrom(centerN, inputN.vertex, v1, inputN.axis)

            val A2_n = state.pendingHypA2 ?: return println("⚠️ Chybí A2 (N).")
            val sx2 = state.pendingHypBranch2SX ?: +1
            val B2_n = state.snapNarysDegenerateHyperbola(conicIdN, logical, sx2)?.point
                ?: snapNarysPointToHyperbolaOnBranch(basisN, logical, sx2)

            // N -> 3D -> P
            val (A2_3D, A2_p_raw) = liftNarysPointTo3DAndPudorys(A2_n.x, A2_n.y, parent) ?: return
            val (B2_3D, B2_p_raw) = liftNarysPointTo3DAndPudorys(B2_n.x, B2_n.y, parent) ?: return

            val pudorysId = state.findPudorysHyperbolaIdByParent(parent.id)
            val (A2_p, B2_p) = if (pudorysId != null) {
                val degA = state.snapPudorysDegenerateHyperbola(pudorysId, A2_p_raw)
                val degB = state.snapPudorysDegenerateHyperbola(pudorysId, B2_p_raw, degA?.side)
                val A_on = degA?.point ?: snapPudorysPointToHyperbola(state, pudorysId, A2_p_raw)
                val B_on = degB?.point ?: snapPudorysPointToHyperbola(state, pudorysId, B2_p_raw)
                A_on to B_on
            } else A2_p_raw to B2_p_raw

            // 3D parametry a konce pro větev 2 (doplnit do mapy)
            val (firstParams, _) = state.hyperbolaArcParams3D[parent.id] ?: (null to null)
            val (t3, t4) = computeHyperbolaParams(parent, A2_3D, B2_3D)
            state.hyperbolaArcParams3D[parent.id] = firstParams to (t3 to t4)

            val (firstEnds, _) = state.hyperbolaArcEnds3D[parent.id] ?: (null to null)
            state.hyperbolaArcEnds3D[parent.id] = firstEnds to (A2_3D to B2_3D)

            // 2D konce (větev 2)
            state.hyperbolaArcBranch2[conicIdN] = A2_n to B2_n
            pudorysId?.let { state.hyperbolaArcBranch2[it] = A2_p to B2_p }
            commitSnapshot(state)
            // úklid
            state.pendingHypA2 = null
            state.pendingHypBranch2SX = null
            state.activeConicIdForArc = null
            state.activeParentConic3DIdForEllipseArc = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("narys_start", state)
            state.triggerRedraw++

            println("🏁 [H3D N] Větev 2 hotová. Konec.")

        }
    }
}
fun arcHyperbolaNarys3DSkipSecond(state: MongeState) {
    if (state.projectionPhase == "narys_hyp3d_a2" || state.projectionPhase == "narys_hyp3d_b2") {
        state.pendingHypA2 = null
        state.pendingHypBranch2SX = null
        state.activeConicIdForArc = null
        state.activeParentConic3DIdForEllipseArc = null
        state.drawobjects = Mongeobjects.NONE
        commitSnapshot(state)
        setProjectionPhase("narys_start", state)
        state.triggerRedraw++
        println("⏭️ [H3D N] Druhá větev hyperboly přeskočena.")
    }
}

