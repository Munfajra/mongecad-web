package monge.input.segments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import model.ConstructionModifier
import model.DrawingModeMonge
import model.Mongeobjects
import model.ProjectionType
import monge.input.segments.directionHandlers.segments.handleOrthogonalSegmentNarys
import monge.input.segments.directionHandlers.segments.handleOrthogonalSegmentPudorys
import monge.input.segments.directionHandlers.segments.handleParallelSegmentNarys
import monge.input.segments.directionHandlers.segments.handleParallelSegmentPudorys
import state.MongeState


fun handleSegmentClick(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState,
    change: PointerInputChange
) {
    if (state.drawobjects == Mongeobjects.SEGMENTS) {
        when (state.projekcnityp) {
            (ProjectionType.SINGLE),(ProjectionType.AUXILIARY) ->
                when (state.constructionModifier) {
                ConstructionModifier.NONE -> {
                    if (state.mongeMode== DrawingModeMonge.NARYS){
                    handleSingleSegmentNarysClick(cursor,snappedPointLogical,canvasOffset,scale,state)
                    }
                    if(state.mongeMode== DrawingModeMonge.PUDORYS) {
                    handleSingleSegmentPudorysClick(cursor,snappedPointLogical,canvasOffset,scale,state)
                    }
                }
                ConstructionModifier.PARALLEL -> {
                    if (state.mongeMode== DrawingModeMonge.PUDORYS){
                        handleParallelSegmentPudorys(cursor,snappedPointLogical,canvasOffset,scale,state)
                }
                    if(state.mongeMode== DrawingModeMonge.NARYS){
                        handleParallelSegmentNarys(cursor,snappedPointLogical,canvasOffset,scale,state)
                    }
                }
                ConstructionModifier.ORTHOGONAL -> {
                    if (state.mongeMode== DrawingModeMonge.NARYS){

                        handleOrthogonalSegmentNarys(cursor,snappedPointLogical,canvasOffset,scale,state)
                    }
                    if (state.mongeMode== DrawingModeMonge.PUDORYS) {
                        handleOrthogonalSegmentPudorys(cursor,snappedPointLogical,canvasOffset,scale,state)
                    }
                }
            }
            (ProjectionType.ASSOCIATED) -> {
                handleAssociatedSegmentFromNarys(cursor, snappedPointLogical, canvasOffset, scale, state, change)
                handleAssociatedSegmentFromPudorys(cursor, snappedPointLogical, canvasOffset, scale, state, change)
            }
            else -> null
        }
    }
}