package draw.mongescreen.previews

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.conicsarcs.*
import state.MongeState

// Kreslí náhledy oblouků kuželoseček (červené) ZNOVU, tentokrát NAD objekty,
// aby je nepřekreslila samotná kuželosečka. Volá se až po drawAllObjects*.
// Všechny funkce jsou gated fázemi, takže mimo authoring nic nekreslí.

fun DrawScope.drawConicArcPreviewsOnTopPudorys(state: MongeState, snappedPointLogical: Offset?) {
    drawEllipseArcPudorys(state, snappedPointLogical)
    drawCircleArcPudorys(state, snappedPointLogical)
    drawParabolaArcPreviewPudorys(state, snappedPointLogical)
    drawHyperbolaArcPreviewPudorys(state, snappedPointLogical)
    drawEllipseArcPreviewPudorys(state, snappedPointLogical)
    drawParabolaArcPreviewPudorysAsoc(state, snappedPointLogical)
    drawHyperbolaArcPreviewPudorys3D(state, snappedPointLogical)
}

fun DrawScope.drawConicArcPreviewsOnTopNarys(state: MongeState, snappedPointLogical: Offset?) {
    drawEllipseArcNarys(state, snappedPointLogical)
    drawCircleArcNarys(state, snappedPointLogical)
    drawHyperbolaArcPreviewNarys(state, snappedPointLogical)
    drawEllipseArcPreviewNarys(state, snappedPointLogical)
    drawParabolaArcPreviewNarys(state, snappedPointLogical)
    drawParabolaArcPreviewNarysAsoc(state, snappedPointLogical)
    drawHyperbolaArcPreviewNarys3D(state, snappedPointLogical)
}

/** AXO: náhled oblouku pro všechny průměty navrch (gated fázemi – jen aktivní kreslí). */
fun DrawScope.drawConicArcPreviewsOnTopAxo(state: MongeState, snappedPointLogical: Offset?) {
    drawConicArcPreviewForProjection(state, snappedPointLogical, state.conicInputPointsPudorys, state.hyperbolaInputsPudorys, "pudorys")
    drawConicArcPreviewForProjection(state, snappedPointLogical, state.conicInputPointsNarys, state.hyperbolaInputsNarys, "narys")
    drawConicArcPreviewForProjection(state, snappedPointLogical, state.conicInputPointsBokorys, state.hyperbolaInputsBokorys, "bokorys")
    drawConicArcPreviewForProjection(state, snappedPointLogical, state.conicInputPointsAxo, state.hyperbolaInputsAxo, "axo")
}
