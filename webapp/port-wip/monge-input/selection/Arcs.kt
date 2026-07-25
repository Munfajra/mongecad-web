package monge.input.selection

import utils.System
import androidx.compose.ui.geometry.Offset
import draw.mongescreen.labels.clearSelection
import model.classes.Arc2DNarys
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.ArcAxoOverlay
import model.classes.Arc2DBokorys
import model.classes.Arc2DPudorys
import state.MongeState
import state.snapMonge.findNearestAidPointLogical
import utils.getLogicalCursor

fun handleSelectionPudorysArc(
    state: MongeState,
) {
    val allowed = state.drawobjects == Mongeobjects.NONE
    if (!allowed) return

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


    // pokud jsme u bodu/aidpointu -> oblouk neřeš (priorita bodů/aid)
    if (state.snappedPointPudorys != null) return
    if (state.findNearestAidPointLogical(snapped, state.snapThreshold / state.scale) != null) return

    val selected =state.snappedArcPudorys
    selected?.let { arc ->
        clearSelection(state)
        state.selectedArcsPudorys.add(arc)
        println("🟣 Označen oblouk v PŮDORYSU: ${arc.name}")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
}
fun handleSelectionNarysArc(
    state: MongeState
) {
    val allowed = state.drawobjects == Mongeobjects.NONE
    if (!allowed) return

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


    if (state.snappedPointNarys != null) return
    if (state.findNearestAidPointLogical(snapped, state.snapThreshold / state.scale) != null) return


    val selected = state.snappedArcNarys
    selected?.let { arc ->
        clearSelection(state)
        state.selectedArcsNarys.add(arc)
        println("🟣 Označen oblouk v NÁRYSU: ${arc.name}")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
}
fun toggleSelectionNarysArc(a: Arc2DNarys, state: MongeState) {
    if (state.selectedArcsNarys.contains(a)) state.selectedArcsNarys.remove(a)
    else state.selectedArcsNarys.add(a)
}
fun handleSelectionBokorysArc(
    state: MongeState
) {
    val allowed = state.drawobjects == Mongeobjects.NONE
    if (!allowed) return


    if (state.snappedPointBokorys != null) return

    val selected = state.snappedArcBokorys
    selected?.let { arc ->
        clearSelection(state)
        state.selectedArcsBokorys.add(arc)
        println("🟣 Označen oblouk v BOKORYSU: ${arc.name}")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
}
fun toggleSelectionBokorysArc(a: Arc2DBokorys, state: MongeState) {
    if (state.selectedArcsBokorys.contains(a)) state.selectedArcsBokorys.remove(a)
    else state.selectedArcsBokorys.add(a)
}
fun toggleSelectionPudorysArc(a: Arc2DPudorys, state: MongeState) {
    if (state.selectedArcsPudorys.contains(a)) state.selectedArcsPudorys.remove(a)
    else state.selectedArcsPudorys.add(a)
}
fun handleSelectionAxoOverlayArc(
    state: MongeState,
) {
    val allowed = state.drawobjects == Mongeobjects.NONE
    if (!allowed) return

    val selected = state.snappedArcAxoOverlay
    selected?.let { arc ->
        clearSelection(state)
        state.selectedArcsAxoOverlay.add(arc)
        println("🟣 Označen oblouk v AXO: ${arc.name}")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
}
fun toggleSelectionAxoOverlayArc(a: ArcAxoOverlay, state: MongeState) {
    if (state.selectedArcsAxoOverlay.contains(a)) state.selectedArcsAxoOverlay.remove(a)
    else state.selectedArcsAxoOverlay.add(a)
}