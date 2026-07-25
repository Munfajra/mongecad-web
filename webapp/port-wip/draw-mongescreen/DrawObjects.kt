package draw.mongescreen

import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.orth.*
import state.MongeState

/**
 * Vykreslení objektů do půdorysu a nárysu.
 *
 * Oproti desktopu chybí:
 *  – výplně kvadrik a jejich occlusion (balíček draw.mongescreen.fills),
 *  – obrys přímkových ploch,
 *  – axonometrické varianty (drawAll*Axo).
 */
fun DrawScope.drawAllObjectsPud(state: MongeState, pxPerPtWorkspace: Float) {
    drawCurvePudorys(state)
    drawPointsPudorys(state, pxPerPtWorkspace)
    drawLinesPudorys(state, true, pxPerPtWorkspace)
    drawSegmentsPudorys(state, true, pxPerPtWorkspace)
    drawCirclesPudorys(state, true, pxPerPtWorkspace)
    drawAllConicsPudorys(state, pxPerPtWorkspace, true)
}

fun DrawScope.drawAllObjectsNar(state: MongeState, pxPerPtWorkspace: Float) {
    drawPointsNarys(state, pxPerPtWorkspace)
    drawCurveNarys(state)
    drawLinesNarys(state, true, pxPerPtWorkspace)
    drawSegmentsNarys(state, true, pxPerPtWorkspace)
    drawCirclesNarys(state, true, pxPerPtWorkspace)
    drawAllConicsNarys(state, pxPerPtWorkspace, true)
}
