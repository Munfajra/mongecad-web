package monge.input.axo.lines

import androidx.compose.ui.geometry.Offset
import model.ProjectionType
import model.axo.AxoMode
import state.MongeState

fun linesRouter(state: MongeState, snappedPointLogical: Offset?){

   val pom = state.projekcnityp == ProjectionType.AUXILIARY
    val single = state.projekcnityp == ProjectionType.SINGLE
    when (state.axoMode){
        AxoMode.NORMAL_2D -> if (pom) handleOverlayLineAxo(state) else if (single) handleAxoLine(state)
        AxoMode.AXO_PUDORYS ->if (single) handleSinglePudorysLineAxo(snappedPointLogical,state)
        AxoMode.AXO_NARYS -> if (single)handleSingleLineNarysAxo(snappedPointLogical,state)
        AxoMode.AXO_BOKORYS -> if (single)handleSingleLineBokorysAxo(snappedPointLogical,state)
    }
}