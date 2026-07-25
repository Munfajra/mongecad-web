package monge.input.axo.segments

import model.Mongeobjects
import model.ProjectionType
import model.axo.AxoMode
import state.MongeState

fun handleSegmentAxo(
    state: MongeState,
) {
    if (state.drawobjects != Mongeobjects.SEGMENTS) return
        when (state.axoMode) {
            AxoMode.AXO_PUDORYS ->{
                axoSegmentPudorys(state)
                }
            AxoMode.NORMAL_2D -> {
                if (state.projekcnityp == ProjectionType.AUXILIARY)handleAOSegment(state)
            else if (state.projekcnityp == ProjectionType.SINGLE) handleAxoSegment(state)}
            AxoMode.AXO_NARYS -> {axoSegmentNarys(state)}
            AxoMode.AXO_BOKORYS ->{ axoSegmentBokorys(state)}
        }


}