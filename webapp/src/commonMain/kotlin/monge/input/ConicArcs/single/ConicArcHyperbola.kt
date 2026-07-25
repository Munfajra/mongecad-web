package monge.input.ConicArcs.single

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.conicarcs.hyperbolaBasisFrom
import draw.mongescreen.conicarcs.projectToHyperbola
import draw.mongescreen.conicarcs.setHyperbolaBranch1P
import draw.mongescreen.conicarcs.setHyperbolaBranch2P
import monge.input.ConicArcs.finalizeConicVisibilityIfActive
import serialization.commitSnapshot
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import state.MongeState
import state.snapMonge.computeIntersection
import ui.mongeui.toolbar.setProjectionPhase
import utils.dot
import utils.getLogicalCursor
import utils.normalize

fun arcHyperbolaPudorys(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
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

        "pudorys_hyp_start" -> {
            val conicId = state.activeConicIdForArc ?: run {
                val conic = state.selectedConicsPudorys.lastOrNull()
                    ?: run { println("⚠️ Vyber nejdřív hyperbolu."); return }
                state.activeConicIdForArc = conic.id
                conic.id
            }
            state.activeConicIdForArc = conicId
            setProjectionPhase("pudorys_hyp_second_hold", state)}
        "pudorys_hyp_second_hold"->{
            val conicId = state.activeConicIdForArc?: return
            val input = state.hyperbolaInputsPudorys[conicId]
                ?: run { println("⚠️ Tahle kuželosečka není hyperbola."); return }
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
            if (state.pendingHypA1 == null) {
                val sx = if ((logical - basis.center).dot(basis.ex) >= 0f) +1 else -1
                val proj = projectToHyperbola(basis, logical, sx)
                    ?: run { println("⚠️ Projekce A1 selhala."); return }
                state.pendingHypA1 = proj.on
                state.pendingHypBranch1SX = sx
                println("✅ [H] A1 uložen (větev ${if (sx>0) "pravá" else "levá"}).")
            } else {
                val sx = state.pendingHypBranch1SX ?: +1
                val A1 = state.pendingHypA1!!
                val projB1 = projectToHyperbola(basis, logical, sx)
                    ?: run { println("⚠️ Projekce B1 selhala."); return }

                state.setHyperbolaBranch1P(conicId, A1, projB1.on)
                state.pendingHypA1 = null
                state.pendingHypBranch1SX = null
                println("🎯 [H] Větev 1 nastavená. Můžeš kliknout A2/B2, nebo Enter pro konec.")
                setProjectionPhase("pudorys_hyp_second", state)

            }
        }

        "pudorys_hyp_second" -> {
            val conicId = state.activeConicIdForArc ?: run {
                println("⚠️ Chybí aktivní hyperbola – vracím se na začátek.")
                setProjectionPhase("pudorys_hyp_start", state)
                return
            }
            val input = state.hyperbolaInputsPudorys[conicId] ?: return
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)

            if (state.pendingHypA2 == null) {
                val sx = if ((logical - basis.center).dot(basis.ex) >= 0f) +1 else -1
                val proj = projectToHyperbola(basis, logical, sx)
                    ?: run { println("⚠️ Projekce A2 selhala."); return }
                state.pendingHypA2 = proj.on
                state.pendingHypBranch2SX = sx
                println("✅ [H] A2 uložen (větev ${if (sx>0) "pravá" else "levá"}).")
            } else {
                val sx = state.pendingHypBranch2SX ?: +1
                val A2 = state.pendingHypA2!!
                val projB2 = projectToHyperbola(basis, logical, sx)
                    ?: run { println("⚠️ Projekce B2 selhala."); return }

                state.setHyperbolaBranch2P(conicId, A2, projB2.on)
                state.finalizeConicVisibilityIfActive(conicId)
                commitSnapshot(state)
                state.pendingHypA2 = null
                state.pendingHypBranch2SX = null
                state.activeConicIdForArc = null
                state.drawobjects = Mongeobjects.NONE
                setProjectionPhase("pudorys_start", state)
                println("🎯 [H] Větev 2 nastavená. Hotovo.")

            }
        }

        else -> Unit
    }
}

fun arcHyperbolaPudorysSkipSecond(state: MongeState) {
    if (state.projectionPhase == "pudorys_hyp_second") {
        state.activeConicIdForArc?.let { id ->
            state.finalizeConicVisibilityIfActive(id)
            commitSnapshot(state)
        }
        state.pendingHypA2 = null
        state.pendingHypBranch2SX = null
        state.activeConicIdForArc = null
        state.drawobjects = Mongeobjects.NONE
        setProjectionPhase("pudorys_start", state)
        println("⏭️ [H] Druhá větev přeskočena.")
        state.triggerRedraw++
    }
}
fun arcHyperbolaNarys(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
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

        "narys_hyp_start" -> {
            val conicId = state.activeConicIdForArc ?: run {
                val conic = state.selectedConicsNarys.lastOrNull()
                    ?: run { println("⚠️ Vyber nejdřív hyperbolu."); return }
                state.activeConicIdForArc = conic.id
                conic.id
            }
            state.activeConicIdForArc = conicId
            setProjectionPhase("narys_hyp_second_hold", state)}
        "narys_hyp_second_hold"->{
            val conicId = state.activeConicIdForArc?: return
            val input = state.hyperbolaInputsNarys[conicId]
                ?: run { println("⚠️ Tahle kuželosečka není hyperbola."); return }
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)

            if (state.pendingHypA1 == null) {
                val sx = if ((logical - basis.center).dot(basis.ex) >= 0f) +1 else -1
                val proj = projectToHyperbola(basis, logical, sx)
                    ?: run { println("⚠️ [N] Projekce A1 selhala."); return }
                state.pendingHypA1 = proj.on
                state.pendingHypBranch1SX = sx
                println("✅ [N] A1 uložen (větev ${if (sx>0) "pravá" else "levá"}).")
            } else {
                val sx = state.pendingHypBranch1SX ?: +1
                val A1 = state.pendingHypA1!!
                val projB1 = projectToHyperbola(basis, logical, sx)
                    ?: run { println("⚠️ [N] Projekce B1 selhala."); return }
                state.hyperbolaArcBranch1[conicId] = A1 to projB1.on

                state.pendingHypA1 = null
                state.pendingHypBranch1SX = null

                println("🎯 [N] Větev 1 nastavena. Klikni A2/B2, nebo Enter pro konec.")
                setProjectionPhase("narys_hyp_second", state)
            }
        }

        "narys_hyp_second" -> {
            val conicId = state.activeConicIdForArc ?: run {
                println("⚠️ [N] Chybí aktivní hyperbola – vracím se na začátek.")
                setProjectionPhase("narys_hyp_start", state)
                return
            }

            val input = state.hyperbolaInputsNarys[conicId] ?: return
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)

            if (state.pendingHypA2 == null) {
                val sx = if ((logical - basis.center).dot(basis.ex) >= 0f) +1 else -1
                val proj = projectToHyperbola(basis, logical, sx)
                    ?: run { println("⚠️ [N] Projekce A2 selhala."); return }
                state.pendingHypA2 = proj.on
                state.pendingHypBranch2SX = sx
                println("✅ [N] A2 uložen (větev ${if (sx>0) "pravá" else "levá"}).")
            } else {
                val sx = state.pendingHypBranch2SX ?: +1
                val A2 = state.pendingHypA2!!
                val projB2 = projectToHyperbola(basis, logical, sx)
                    ?: run { println("⚠️ [N] Projekce B2 selhala."); return }

                state.hyperbolaArcBranch2[conicId] = A2 to projB2.on
                state.finalizeConicVisibilityIfActive(conicId)
                commitSnapshot(state)
                // konec
                state.pendingHypA2 = null
                state.pendingHypBranch2SX = null
                state.activeConicIdForArc = null
                state.drawobjects = Mongeobjects.NONE
                setProjectionPhase("narys_start", state)
                println("🎯 [N] Větev 2 nastavena. Hotovo.")

            }
        }

        else -> Unit
    }
}
fun arcHyperbolaNarysSkipSecond(state: MongeState) {
    if (state.projectionPhase == "narys_hyp_second") {
        state.activeConicIdForArc?.let { id ->
            state.finalizeConicVisibilityIfActive(id)
            commitSnapshot(state)
        }
        state.pendingHypA2 = null
        state.pendingHypBranch2SX = null
        state.activeConicIdForArc = null
        state.drawobjects = Mongeobjects.NONE
        setProjectionPhase("narys_start", state)
        println("⏭️ [N] Druhá větev přeskočena.")
        state.triggerRedraw++
    }
}
