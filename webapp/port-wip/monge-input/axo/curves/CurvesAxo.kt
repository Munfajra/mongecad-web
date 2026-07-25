package monge.input.axo.curves

import model.DrawingModeMonge
import model.Mongeobjects
import model.axo.AxoMode
import monge.input.curves.addPointToCurve3DPickById
import monge.input.curves.handleBokorysСurvePickPointClick
import monge.input.curves.handleNarysCurvePickPointClick
import monge.input.curves.handlePudorysCurvePickPointClick
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

fun handleCurvesAxo(state: MongeState) {
    if (state.drawobjects != Mongeobjects.CURVE) return

    when (state.axoMode) {
        AxoMode.AXO_PUDORYS -> {
            state.mongeMode = DrawingModeMonge.PUDORYS
            setProjectionPhase("pudorys_curve_pick", state)
            val p = state.snappedPointPudorys ?: return
            handlePudorysCurvePickPointClick(state, p)
        }
        AxoMode.AXO_NARYS -> {
            state.mongeMode = DrawingModeMonge.NARYS
            setProjectionPhase("narys_curve_pick", state)
            val p = state.snappedPointNarys ?: return
            handleNarysCurvePickPointClick(state, p)
        }
        AxoMode.AXO_BOKORYS -> {
            setProjectionPhase("bokorys_curve_pick", state)
            val p = state.snappedPointBokorys ?: return
            handleBokorysСurvePickPointClick(state, p)
        }
        AxoMode.NORMAL_2D -> {
            setProjectionPhase("curve3d_pick_points", state)
            val axoPoint = state.snappedAxoPoint ?: return
            val p3 = axoPoint.parent ?: return
            addPointToCurve3DPickById(state, p3.id)
        }
    }
}
