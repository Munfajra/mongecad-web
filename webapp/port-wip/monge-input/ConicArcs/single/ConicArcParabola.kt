package monge.input.ConicArcs.single

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.previews.conicsarcs.projectToParabola
import monge.input.ConicArcs.finalizeConicVisibilityIfActive
import serialization.commitSnapshot
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.getLogicalCursor

fun arcParabolaPudorys(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
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
        "pudorys_par_start" -> {
            val conic = state.selectedConicsPudorys.lastOrNull()
                ?: run { println("⚠️ Vyber nejdřív parabolu."); return }

            val inputs = state.conicInputPointsPudorys[conic.id]
            if (inputs == null || inputs.third != Offset.Unspecified) {
                println("⚠️ Zvolená kuželosečka není parabola."); return
            }
            if (state.hyperbolaInputsPudorys.containsKey(conic.id)) {
                println("⚠️ Není to parabola."); return
            }

            state.activeConicIdForArc = conic.id
            setProjectionPhase("pudorys_par_arc_hold", state)}
        "pudorys_par_arc_hold"->{
            val inputs = state.conicInputPointsPudorys[state.activeConicIdForArc]?: return
            val (vertex, focus, _) = inputs
            state.pendingArcA = projectToParabola(vertex, focus, logical)
            println("✅ [P] A-bod parab. oblouku uložen.")
            setProjectionPhase("pudorys_par_arc", state)
        }

        "pudorys_par_arc" -> {
            val conicId = state.activeConicIdForArc
                ?: run { setProjectionPhase("pudorys_par_start", state); return }
            val inputs = state.conicInputPointsPudorys[conicId]
                ?: run { setProjectionPhase("pudorys_par_start", state); return }
            if (inputs.third != Offset.Unspecified) { println("⚠️ Už to není parabola."); return }

            val (vertex, focus, _) = inputs
            val Aproj = state.pendingArcA ?: run {
                setProjectionPhase("pudorys_par_start", state); return
            }
            val Bproj = projectToParabola(vertex, focus, logical)

            state.parabolaArcEnds[conicId] = Aproj to Bproj
            println("🎯 [P] Parabolický oblouk nastaven pro conic=$conicId")
            state.finalizeConicVisibilityIfActive(conicId)
            commitSnapshot(state)
            // úklid
            state.pendingArcA = null
            state.activeConicIdForArc = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("pudorys_start", state)
            state.triggerRedraw++

        }

        else -> Unit
    }
}
fun getLogicalCursorNarys(
    snapped: Offset?,            // může být už v NARYS-logical, nebo v PUDORYS-logical
    cursor: Offset,              // device/screen z pointeru
    canvasOffset: Offset,
    scale: Float,
    canvasWidth: Float,
    canvasHeight: Float,         // sem ti ho dávám konzistentně, i když ho v nárysu typicky nepotřebuješ
    xAxisDirection: XAxisDirection
): Offset {

    // 1) device -> "screen" soustava (stejná jako kreslení; pro X flip)
    //    (PLANE tu neřešíme – jak chceš – nárys je jen MONGE)
    val cursorScreen = if (xAxisDirection == XAxisDirection.POSITIVE_LEFT) {
        Offset(canvasWidth - cursor.x, cursor.y)
    } else {
        cursor
    }

    // 2) screen -> raw logical (inverze scale+offset)
    val raw = (cursorScreen - canvasOffset) / scale

    // 3) raw -> NARYS logical (protože v nárysu máš osu Y obráceně)
    val logicalFromCursor = raw.copy(y = -raw.y)

    if (snapped == null) return logicalFromCursor

    // 4) snapped může být buď už v nárysu, nebo je to půdorysný bod (y opačně)
    val dSame    = (snapped - logicalFromCursor).getDistance()
    val dFlipped = (snapped.copy(y = -snapped.y) - logicalFromCursor).getDistance()

    return if (dSame <= dFlipped) snapped else snapped.copy(y = -snapped.y)
}


fun arcParabolaNarys(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
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

        "narys_par_start" -> {
            val conic = state.selectedConicsNarys.lastOrNull()
                ?: run { println("⚠️ Vyber nejdřív parabolu v nárysu."); return }

            val inputs = state.conicInputPointsNarys[conic.id]
            // parabola: p3 == Unspecified, nesmí být hyperbola
            if (inputs == null || inputs.third != Offset.Unspecified || state.hyperbolaInputsNarys.containsKey(conic.id)) {
                println("⚠️ Zvolená kuželosečka v nárysu není parabola."); return
            }
            val (_, _, _) = inputs

            // zamkni ID paraboly pro druhou fázi
            state.activeConicIdForArc = conic.id
            setProjectionPhase("narys_par_arc_hold", state)}
        "narys_par_arc_hold"->{
            val inputs = state.conicInputPointsNarys[state.activeConicIdForArc]?:return
            val (vertex, focus, _) = inputs
            // A-bod promítni na parabolu
            state.pendingArcA = projectToParabola(vertex, focus, logical)
            println("✅ [N] A-bod parabolického oblouku uložen.")

            setProjectionPhase("narys_par_arc", state)
        }

        "narys_par_arc" -> {
            val conicId = state.activeConicIdForArc
                ?: run { setProjectionPhase("narys_par_start", state); return }

            val inputs = state.conicInputPointsNarys[conicId]
                ?: run { setProjectionPhase("narys_par_start", state); return }
            if (inputs.third != Offset.Unspecified) { println("⚠️ [N] Už to není parabola."); return }

            val (vertex, focus, _) = inputs
            val Aproj = state.pendingArcA ?: run {
                setProjectionPhase("narys_par_start", state); return
            }
            val Bproj = projectToParabola(vertex, focus, logical)

            state.parabolaArcEnds[conicId] = Aproj to Bproj
            println("🎯 [N] Parabolický oblouk nastaven pro conic=$conicId")
            state.finalizeConicVisibilityIfActive(conicId)
            commitSnapshot(state)
            // úklid
            state.pendingArcA = null
            state.activeConicIdForArc = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("narys_start", state)
            state.triggerRedraw++

        }

        else -> Unit
    }
}
