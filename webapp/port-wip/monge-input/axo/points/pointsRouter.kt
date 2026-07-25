package monge.input.axo.points

import androidx.compose.ui.geometry.Offset
import model.ProjectionType
import model.axo.AxoMode
import state.MongeState

fun pointsRouter(state: MongeState, snappedPointLogical: Offset?){

    when (state.axoMode){
        AxoMode.NORMAL_2D -> if (state.projekcnityp == ProjectionType.AUXILIARY)addAxoOverlayPoint(state)
        else handleSingleAxoProjectionPoint(state)
        AxoMode.AXO_PUDORYS -> handleSinglePudorysPointAxo(state)
        AxoMode.AXO_NARYS -> handleSingleNarysPointAxo(snappedPointLogical,state)
        AxoMode.AXO_BOKORYS -> handleSingleBokorysPointAxo(snappedPointLogical,state)
    }
}