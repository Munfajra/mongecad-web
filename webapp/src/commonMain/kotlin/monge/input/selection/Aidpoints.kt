package monge.input.selection

import utils.System
import model.Mongeobjects
import model.classes.AidPointLogical
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.AxoOverlayPoint
import monge.input.axo.getLogicalCursorAxoOverlay
import state.MongeState
import state.snapMonge.findNearestAOPoint
import state.snapMonge.findNearestAidPointLogical
import utils.getLogicalCursor

fun toggleSelectionAidPoint(
    point: AidPointLogical,
    state: MongeState
) {
    val already = point.id in state.selectedAidPointIds

    if (already) {
        state.selectedAidPointIds.remove(point.id)
        println("🟡 Odznačen pomocný bod (${point.x}, ${point.y})")
    } else {
        state.selectedAidPointIds.add(point.id)
        println("🟢 Označen pomocný bod (${point.x}, ${point.y})")
    }

    state.deferSelectionUntil = System.currentTimeMillis() + 100
}
fun toggleSelectionOverlayPoint(
    point: AxoOverlayPoint,
    state: MongeState
) {
    val already = point.id in state.selectedAOPointIds

    if (already) {
        state.selectedAOPointIds.remove(point.id)
        println("🟡 Odznačen pomocný bod (${point.positionLogical})")
    } else {
        state.selectedAOPointIds.add(point.id)
        println("🟢 Označen pomocný bod (${point.positionLogical})")
    }

    state.deferSelectionUntil = System.currentTimeMillis() + 100
}
fun handleSelectionAidPoint(

    state: MongeState
) {if (System.currentTimeMillis() < state.deferSelectionUntil) {
    println("⛔️ Výběr zablokován časovým štítem")
    return
}
    val isNearPoint = state.snappedPointPudorys != null
    if(isNearPoint) return
    if (state.drawobjects == Mongeobjects.CURVE) return

    val snapped = getLogicalCursor(
        state.snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )


    val target = state.findNearestAidPointLogical(
        cursorLogical = snapped,
        snapRadiusLogical = state.snapThreshold / state.scale
    )
    if (target!= null) {
            state.selectedAidPointIds.clear()
            state.selectedAidPointIds.add(target.id)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🔵 Vybrán pomocný bod: ${target.name} (${target.x}, ${target.y})")
    }
}
fun handleSelectionAOPoint(state: MongeState) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }

    val isNearPoint = state.snappedPointPudorys != null
    if (isNearPoint) return

    val cursorLogical = state.snappedPointLogical ?: getLogicalCursorAxoOverlay(
        snappedScreen = null,
        cursor = state.cursorPosition,
        state = state
    ) ?: return

    val target = state.snappedAOPoint
    if (target != null) {
        state.selectedAOPointIds.clear()
        state.selectedAOPointIds.add(target.id)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        println("🔵 Vybrán pomocný bod: ${target.name} (${target.positionLogical})")
    }
}