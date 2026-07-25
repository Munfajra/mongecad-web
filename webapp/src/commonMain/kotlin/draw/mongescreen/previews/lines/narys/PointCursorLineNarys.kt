package draw.mongescreen.previews.lines.narys

import monge.input.axo.getLogicalCursorAxo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.VisibleQuad
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarys
import model.*
import model.axo.AxoMode
import model.classes.Point3DNarys


import state.MongeState
import utils.getLogicalCursor

//náhled přímky bod - kursor
fun DrawScope.previewSingleNarysLineCursor(state: MongeState, snappedPointLogical: Offset?) {
    val start = state.lineStartPoint3DNarys ?: return
    val cursorLogical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val cursorWorld = Offset(cursorLogical.x, -cursorLogical.y)

    drawDashedPreviewLineNarys(
        start = start,
        cursorWorld = cursorWorld,
        scale = state.scale,
        canvasOffset = state.canvasOffset,
        color = Color.Gray
    )
}
//náhled sdružené přímky bod- kursor
fun DrawScope.previewAssociatedNarysLineCursor (state: MongeState, snappedPointLogical:Offset? ) {
    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.LINES &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        (state.projectionPhase == "projection_line_narys_dir" ||
                state.projectionPhase == "projection_line_dir_narys_start" ||
                state.projectionPhase == "parallel_line_point_selection_pudorys_narys_start") &&
        state.pendingXnarys != null &&
        (state.pendingY != null || state.pendingZ != null)
    ) {
        val cursorLogical = getLogicalCursor(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
        val cursorWorld = Offset(cursorLogical.x, -cursorLogical.y)
        val start = Point3DNarys(state.pendingXnarys!!, state.pendingZ!!, state.inputName)
        drawDashedPreviewLineNarys(start, cursorWorld, state.scale, state.canvasOffset, color = Color.Gray)
    }

}
//náhled sdružené přímky rovnoběžné - kursor
fun DrawScope.previewAssociatedNarysParallelCursor(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    val baseDirection = state.selectedLineForParallelNarys?.direction
        ?: state.selectedLinesNarys.firstOrNull()?.direction
        ?: state.selectedSegmentForParallelNarys?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.z - it.start.z
            )
        }
        ?: state.selectedSegmentsNarys.firstOrNull()?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.z - it.start.z
            )
        }

    val effectiveDirection = when (state.constructionModifier) {
        ConstructionModifier.ORTHOGONAL -> baseDirection?.let { Offset(-it.y, it.x) }
        ConstructionModifier.PARALLEL -> baseDirection
        else -> null
    }

    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        effectiveDirection != null &&
        (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL)
    ) {
        val cursorLogical = getLogicalCursor(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
        when (state.drawobjects){

        (Mongeobjects.LINES)->{

            val through = Point3DNarys(cursorLogical.x, -cursorLogical.y, name = "?")

            drawDashedParallelLinePreviewNarys(
                through = through,
                direction = effectiveDirection,
                scale = state.scale,
                canvasOffset = state.canvasOffset,
                color = Color.Gray
            )}
            (Mongeobjects.SEGMENTS) -> {
                if (state.projectionPhase ==  "narys_start"||state.projectionPhase== "narys_segment_associated_A_pudorys_start" ){

                    val through = Point3DNarys(cursorLogical.x, -cursorLogical.y, name = "?")

                    drawDashedParallelLinePreviewNarys(
                        through = through,
                        direction = effectiveDirection,
                        scale = state.scale,
                        canvasOffset = state.canvasOffset,
                        color = Color.Gray
                    )}
            }
                else -> {}

    }}
}

fun DrawScope.previewSingleNarysLineCursorAxo(state: MongeState, snappedPointLogical: Offset?,visibleQuad: VisibleQuad?) {
    val start = state.lineStartPoint3DNarys ?: return
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
        AxoMode.AXO_NARYS,
        state.activeAxoModel
    )?:return

    Unit
}
fun DrawScope.previewAssociatedNarysParallelCursorAxo(
    state: MongeState,
    snappedPointLogical: Offset?,
    visibleQuad: VisibleQuad?
) {
    val isOrthogonal = state.constructionModifier == ConstructionModifier.ORTHOGONAL
    val effectiveDirection = Unit?: return

    if (
     state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
        val cursorLogical = getLogicalCursorAxo(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP,
            AxoMode.AXO_NARYS,
            state.activeAxoModel
        )?:return
        val visibleQuad = visibleQuad?: return
        when (state.drawobjects){

            (Mongeobjects.LINES)->{

                val through = Point3DNarys(cursorLogical.x, cursorLogical.y, name = "?")

                Unit}
            else -> {}

        }}
}
