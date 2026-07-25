package monge.input.tools

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.classes.AidPointLogical
import model.XAxisDirection
import model.YAxisDirectionPlane
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.getLogicalCursor

fun handleClickAidMidpoint(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    if (state.midpointPoint1 == null) {

        state.midpointPoint1 = logical

        println("🟢 První bod pro střed uložen: $logical")
    } else {


        val p1 = state.midpointPoint1!!
        val p2 = logical
        val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)


        val newAid = AidPointLogical(x = mid.x, y = mid.y, creationIndex = allocIndex(state))
        state.aidPointsLogical.add(newAid)


        println("🔵 Přidán střed")
        updateConstructionInfo(state)
        repeatCons(state)

        state.midpointPoint1 = null
        commitSnapshot(state)
        resetStavu(state)
    }
}
