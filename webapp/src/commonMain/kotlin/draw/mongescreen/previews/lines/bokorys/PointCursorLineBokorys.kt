package draw.mongescreen.previews.lines.bokorys

import monge.input.axo.getLogicalCursorAxo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.VisibleQuad
import model.ConstructionModifier
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.axo.AxoMode
import model.classes.Point3DBokorys


import state.MongeState

fun DrawScope.previewSingleBokorysLineCursorAxo(state: MongeState, snappedPointLogical: Offset?,visibleQuad: VisibleQuad?) {
    val start = state.lineStartPoint3DBokorys ?: return
    val quad = visibleQuad ?: return
    val cursorLogical = getLogicalCursorAxo(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP,
        AxoMode.AXO_BOKORYS,
        state.activeAxoModel
    )?:return

    Unit
}
fun DrawScope.previewSingleBokorysParallelCursorAxo(state: MongeState, snappedPointLogical: Offset?,visibleQuad: VisibleQuad?) {
    val isOrthogonal = state.constructionModifier == ConstructionModifier.ORTHOGONAL
    val effectiveDirection = Unit?: return

    if (
        state.axoMode == AxoMode.AXO_BOKORYS && (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL)
    ) {
        val cursorLogical = getLogicalCursorAxo(
            snappedPointLogical,
            state.cursorPosition,
            axoModel = state.activeAxoModel,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            flipX = false,
            flipY = false,
            mode = AxoMode.AXO_BOKORYS
        ) ?: return
        val visibleQuad = visibleQuad ?: return
        when (state.drawobjects){
            (Mongeobjects.LINES)->{
                val through = Point3DBokorys(cursorLogical.x, cursorLogical.y, name = "?")

                Unit}
            else -> {}

        }}
}

