package monge.input.combineprojections

import model.*
import model.classes.Plane3D
import model.classes.PlaneTraceBokorys
import model.classes.PlaneTraceNarys
import model.classes.PlaneTracePudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.Trace2DProjection
import serialization.commitSnapshot
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import kotlin.math.abs

/**
 * Po dokončení roviny z existující stopy převezme název (a horní index) z původní stopy,
 * místo aby se znovu ptal dialogem. Dialog se zobrazí jen, když stopa žádný název nemá.
 * Volat až PO přidání roviny a nových stop do stavu (kvůli commitSnapshot).
 */
fun resolvePlaneNamingAfterCompletion(state: MongeState, plane: Plane3D, trace: Trace2DProjection) {
    val inheritedName = when (trace) {
        is PlaneTracePudorys -> trace.localName
        is PlaneTraceNarys -> trace.localName
        is PlaneTraceBokorys -> trace.localName
    }?.takeIf { it.isNotBlank() }

    if (inheritedName != null) {
        plane.name = inheritedName
        plane.superscript = when (trace) {
            is PlaneTracePudorys -> trace.localSuperscript
            is PlaneTraceNarys -> trace.localSuperscript
            is PlaneTraceBokorys -> trace.localSuperscript
        }
        state.planePendingForNaming = null
        state.showPlaneNamingDialog = false
        state.planeNameInput = ""
        when (state.mongeMode) {
            DrawingModeMonge.NARYS -> setProjectionPhase("pudorys_start", state)
            DrawingModeMonge.PUDORYS -> setProjectionPhase("narys_start", state)
        }
        commitSnapshot(state)
        resetStavu(state)
        updateConstructionInfo(state)
        repeatCons(state)
        println("📛 Rovina převzala název ze stopy: $inheritedName")
    } else {
        state.planePendingForNaming = plane
        state.showPlaneNamingDialog = true
        state.planeNameInput = ""
    }
}

fun CompletePlaneAdd(state: MongeState,selectedTrace: Trace2DProjection) {

    state.completionPending = null
    state.reusingExistingProjection = false
    state.inputName = ""
    state.projectionPhase = when (state.mongeMode) {
        DrawingModeMonge.PUDORYS -> "pudorys_start"
        DrawingModeMonge.NARYS -> "narys_start"
    }
    val pudorys =selectedTrace as? PlaneTracePudorys
    val narys = selectedTrace as? PlaneTraceNarys

    when {
        pudorys != null -> {
            state.tracePlanePudorys = pudorys
            state.tracePlanePudorys?.let { base ->
                if (abs(base.direction.y) > 0.0001f) {
                    val p = base.point
                    val d = base.direction
                    val t = -p.y / d.y
                    val x = p.x + t * d.x
                    val pointOnX12 = Point3DNarys(x = x, z = 0f, name = "X₁₂")
                    state.xOnX12Narys = pointOnX12
                    println("📍 Vypočten průsečík s x₁₂: $pointOnX12")
                    state.isNameConfirmed = false
                    state.drawobjects = Mongeobjects.PLANE
                    state.projekcnityp = ProjectionType.ASSOCIATED
                    state.pendingMongeModeChange = DrawingModeMonge.NARYS
                    state.reusingExistingProjection = true
                    state.traceToAttach = selectedTrace
                    setProjectionPhase("plane_trace_narys_direction", state)
                } else {
                    println("❌ Nelze spočítat průsečík s x₁₂ – přepínám na speciální případ")
                    state.isNameConfirmed = false
                    state.drawobjects = Mongeobjects.PLANE
                    state.projekcnityp = ProjectionType.ASSOCIATED
                    state.pendingMongeModeChange = DrawingModeMonge.NARYS
                    state.reusingExistingProjection = true
                    state.traceToAttach = selectedTrace
                    setProjectionPhase("plane_trace_narys_special_direction", state)
                }
            }


        }
        narys != null -> {
            state.tracePlaneNarys = narys
            val base = state.tracePlaneNarys ?: return println("❌ Chybí stopa roviny.")
            val p = base.point
            val d = base.direction

            if (abs(d.y) > 1e-5f) {
                val t = -p.z / d.y
                val x = p.x + t * d.x
                val pointOnX12 = Point3DPudorys(x = x, y = 0f, name = "X₁₂")
                state.xOnX12Pudorys = pointOnX12
                println("📍 Vypočten průsečík s x₁₂: $pointOnX12")
                state.isNameConfirmed = false
                state.drawobjects = Mongeobjects.PLANE
                state.projekcnityp = ProjectionType.ASSOCIATED
                setProjectionPhase("plane_trace_pudorys_direction", state)
                state.pendingMongeModeChange = DrawingModeMonge.PUDORYS
                state.traceToAttach = selectedTrace
                state.reusingExistingProjection = true
            } else {
                println("❌ Nelze spočítat průsečík – směr nárysové stopy je rovnoběžný s x₁₂.")
                state.isNameConfirmed = false
                state.drawobjects = Mongeobjects.PLANE
                state.projekcnityp = ProjectionType.ASSOCIATED
                setProjectionPhase("plane_trace_pudorys_special_direction", state)
                state.pendingMongeModeChange = DrawingModeMonge.PUDORYS
                state.traceToAttach = selectedTrace
                state.reusingExistingProjection = true
            }

        }
    }
}