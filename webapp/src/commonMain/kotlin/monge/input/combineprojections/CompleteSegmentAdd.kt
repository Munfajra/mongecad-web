package monge.input.combineprojections

import model.*
import model.classes.Segment2DNarys
import model.classes.Segment2DProjection
import model.classes.Segment2DPudorys
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

fun CompleteSegmentAdd(state: MongeState,segment: Segment2DProjection) {

    state.completionPending = null
    state.reusingExistingProjection = false
    state.inputName = ""
    state.projectionPhase = when (state.mongeMode) {
        DrawingModeMonge.PUDORYS -> "pudorys_start"
        DrawingModeMonge.NARYS -> "narys_start"
    }
    val pudorys = if (segment is Segment2DPudorys) {segment} else null

    val narys =  if (segment is Segment2DNarys) {segment} else null

    when {
        pudorys != null -> {
            state.pendingSegmentPudorys.add(pudorys)
            state.pendingXA = pudorys.start.x
            state.pendingYA =  pudorys.start.y
            state.pendingXB = pudorys.end.x
            state.pendingYB = pudorys.end.y
            state.isNameConfirmed = false
            state.drawobjects = Mongeobjects.SEGMENTS
            state.projekcnityp = ProjectionType.ASSOCIATED
            setProjectionPhase("narys_segment_associated_A_pudorys_start", state)
            state.pendingMongeModeChange = DrawingModeMonge.NARYS
            state.reusingExistingProjection = true
        }
        narys != null -> {
            state.pendingSegmentNarys.add(narys)
            state.pendingXA = narys.start.x
            state.pendingZA =  narys.start.z
            state.pendingXB = narys.end.x
            state.pendingZB = narys.end.z
            state.isNameConfirmed = false
            state.drawobjects = Mongeobjects.SEGMENTS
            state.projekcnityp = ProjectionType.ASSOCIATED
            setProjectionPhase("pudorys_segment_associated_A_narys_start", state)
            state.pendingMongeModeChange = DrawingModeMonge.PUDORYS
            state.reusingExistingProjection = true

        }
    }
}