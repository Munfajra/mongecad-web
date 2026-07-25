package draw.mongescreen.previews.lines.pudorys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.VisibleQuad
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorysAxo
import model.*
import model.axo.AxoMode
import model.classes.Point3DPudorys
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.resolvePudorysDirectionAxo
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewSinglePudorysLineCursor(state: MongeState, snappedPointLogical: Offset?) {
    if ( state.lineStartPoint3DPudorys !=null) {
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
        val cursorWorld = Offset(
            cursorLogical.x,
            cursorLogical.y
        )
        drawDashedPreviewLinePudorys(
            start =  state.lineStartPoint3DPudorys!!,
            cursorWorld = cursorWorld,
            scale =  state.scale,
            canvasOffset =  state.canvasOffset,
            color = Color.Gray
        )
    }
}
fun DrawScope.previewSinglePudorysParallelCursor(state: MongeState, snappedPointLogical: Offset?) {
    val baseDirection = state.selectedLineForParallelPudorys?.direction
        ?: state.selectedLinesPudorys.firstOrNull()?.direction
        ?: state.selectedSegmentForParallelPudorys?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.y - it.start.y
            )
        }
        ?: state.selectedSegmentsPudorys.firstOrNull()?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.y - it.start.y
            )
        }

    val effectiveDirection = when (state.constructionModifier) {
        ConstructionModifier.ORTHOGONAL -> baseDirection?.let { Offset(-it.y, it.x) } // kolmá v půdorysu (x-y)
        ConstructionModifier.PARALLEL -> baseDirection
        else -> null
    }

    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        effectiveDirection != null &&
        (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL)
    ) {
        when (state.drawobjects){
            (Mongeobjects.LINES)->{
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
                val through = Point3DPudorys(cursorLogical.x, cursorLogical.y, name = "?")

                drawDashedParallelLinePreviewPudorys(
                    through = through,
                    direction = effectiveDirection,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset,
                    color = Color.Gray
                )}
            (Mongeobjects.SEGMENTS) -> {
                if (state.projectionPhase ==  "pudorys_start"||state.projectionPhase== "pudorys_segment_associated_A_narys_start"  ){
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
                    val through = Point3DPudorys(cursorLogical.x, cursorLogical.y, name = "?")

                    drawDashedParallelLinePreviewPudorys(
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
fun DrawScope.previewAssociatedPudorysLineCursor(state: MongeState,snappedPointLogical: Offset?) {
    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.LINES &&
        state.projekcnityp== ProjectionType.ASSOCIATED &&
        ( state.projectionPhase == "projection_line_dir_pudorys" || state.projectionPhase == "projection_line_start_pudorys_dir") &&
        state. pendingXpudorys != null &&
        state.pendingY != null
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
        val cursorWorld = Offset(
            cursorLogical.x,
            cursorLogical.y
        )
        drawDashedPreviewLinePudorys(
            start = Point3DPudorys(state.pendingXpudorys!!, state.pendingY!!, state.inputName),
            cursorWorld = cursorWorld,
            scale =  state.scale,
            canvasOffset =  state.canvasOffset,
            color = Color.Gray
        )

    }
}
fun DrawScope.previewSinglePudorysLineCursorAxo(
    state: MongeState,
    snappedPointLogical: Offset?,
    visibleQuad: VisibleQuad?
) {
    val start = state.lineStartPoint3DPudorys ?: return
    val quad = visibleQuad ?: return

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
        mode = AxoMode.AXO_PUDORYS
    ) ?: return

    drawDashedPreviewLinePudorysAxo(
        start = start,
        cursorWorld = cursorLogical,
        visibleQuad = quad,
        color = Color.Gray,
        scale = state.scale
    )
}
fun DrawScope.previewSinglePudorysParallelCursorAxo(state: MongeState, snappedPointLogical: Offset?,visibleQuad: VisibleQuad?) {
    if (state.projekcnityp == ProjectionType.AUXILIARY) return
    val isOrthogonal = state.constructionModifier == ConstructionModifier.ORTHOGONAL
    val effectiveDirection = resolvePudorysDirectionAxo(state,isOrthogonal)?: return
    if (
        state.axoMode == AxoMode.AXO_PUDORYS && (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL)
    ) {
        val cursorLogical = getLogicalCursorAxo(
            snapped = state.snappedPointLogical,
            cursor = state.cursorPosition,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            flipX = false,
            flipY = false,
            mode = AxoMode.AXO_PUDORYS,
            axoModel = state.activeAxoModel
        ) ?: return
        val visibleQuad = visibleQuad ?: return
        when (state.drawobjects){
            (Mongeobjects.LINES)->{
                val through = Point3DPudorys(cursorLogical.x, cursorLogical.y, name = "?")

                drawDashedParallelLinePreviewPudorysAxo(
                    through = through,
                    direction = effectiveDirection,
                    scale = state.scale,
                    visibleQuad = visibleQuad,


                )}
            else -> {}

        }}
}

