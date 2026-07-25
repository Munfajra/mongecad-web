package monge.input.tools

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.AidPointLogical
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.getLogicalCursor

fun handleClickDistancePlacement(
    snappedPointLogical: Offset?,
    state: MongeState,
    cursorWorld: Offset
) {
    if (state.drawobjects == Mongeobjects.GETDISTANCE) {
        val logical = getLogicalCursor(
            snappedPointLogical,
            cursorWorld,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
        when (state.projectionPhase) {
            "narys_start","pudorys_start" -> {
                state.pendingPoint1 = logical
                setProjectionPhase("distance_point2_select", state)
                println("🟢 První bod uložen")
            }

            "distance_point2_select" -> {
                val p1 = state.pendingPoint1 ?: return
                val dist = (logical - p1).getDistance()
                state.pendingDistance = dist
                setProjectionPhase("distance_point3_select", state)
                println("🔵 Druhý bod uložen, vzdálenost = $dist")
            }

            "distance_point3_select" -> {
                state.pendingPoint3 = logical
                setProjectionPhase("distance_target_place", state)
                println("🟣 Třetí bod uložen")
            }

            "distance_target_place" -> {
                val p3   = state.pendingPoint3 ?: return
                val dist = state.pendingDistance ?: return

                val dir  = logical - p3
                val len  = dir.getDistance()
                if (len < 1e-6f) {
                    println("⚠️ Směr příliš krátký")
                    return
                }

                /* vybereme jednotkový vektor podle modifierů */
                val unit = when {
                    state.constructionModifier == ConstructionModifier.ORTHOGONAL && state.arc.arcDirectionClockwise ->
                        Offset(dir.y, -dir.x) / len         // +90°
                    state.constructionModifier == ConstructionModifier.ORTHOGONAL && !state.arc.arcDirectionClockwise ->
                        Offset(-dir.y, dir.x) / len         // –90°
                    else -> dir / len                       // běžný směr
                }

                val result = p3 + unit * dist              // KONCOVÝ bod v logických souřadnicích



                state.aidPointsLogical += AidPointLogical(result.x, result.y, creationIndex = allocIndex(state))

                state.projectionPhase = when (state.mongeMode) {
                    DrawingModeMonge.NARYS   -> "narys_start"
                    DrawingModeMonge.PUDORYS -> "pudorys_start"
                }
                commitSnapshot(state)
                println("✅ Pomocný bod (LOGICAL) vložen: $result")


                /* reset stavů */
                state.pendingPoint1   = null
                state.pendingPoint3   = null
                state.pendingDistance = null
                repeatCons(state)
                updateConstructionInfo(state)
                resetStavu(state)

            }
        }
    }
}