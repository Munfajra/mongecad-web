package draw.mongescreen

import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.fills.FillView
import draw.mongescreen.fills.computeOcclusionClips
import draw.mongescreen.fills.drawQuadricFills
import draw.mongescreen.fills.drawSegmentSolidsFillNarys
import draw.mongescreen.fills.drawSegmentSolidsFillPudorys
import draw.mongescreen.fills.quadricFillsNarys
import draw.mongescreen.fills.quadricFillsPudorys
import draw.mongescreen.objects.drawRuledSurfaceOutlineNarys
import draw.mongescreen.objects.drawRuledSurfaceOutlinePudorys
import draw.mongescreen.objects.orth.*
import state.MongeState

/**
 * Vykreslení objektů do půdorysu a nárysu.
 *
 * Oproti desktopu chybí axonometrické varianty (drawAll*Axo) – web
 * axonometrii nekreslí.
 */
fun DrawScope.drawAllObjectsPud(state: MongeState, pxPerPtWorkspace: Float) {
    drawCurvePudorys(state)
    drawPointsPudorys(state, pxPerPtWorkspace)
    drawLinesPudorys(state, true, pxPerPtWorkspace)
    val pudView = FillView.pudorys(state)
    val pudQuadricFills = quadricFillsPudorys(state)
    val pudClips = computeOcclusionClips(state, pudView, pudQuadricFills)
    drawSegmentSolidsFillPudorys(state, pudClips)
    drawQuadricFills(pudQuadricFills, pudView.screenMatrix, pudClips)
    drawRuledSurfaceOutlinePudorys(state)
    drawSegmentsPudorys(state, true, pxPerPtWorkspace)
    drawCirclesPudorys(state, true, pxPerPtWorkspace)
    drawAllConicsPudorys(state, pxPerPtWorkspace, true)
}

fun DrawScope.drawAllObjectsNar(state: MongeState, pxPerPtWorkspace: Float) {
    drawPointsNarys(state, pxPerPtWorkspace)
    drawCurveNarys(state)
    drawLinesNarys(state, true, pxPerPtWorkspace)
    val narView = FillView.narys(state)
    val narQuadricFills = quadricFillsNarys(state)
    val narClips = computeOcclusionClips(state, narView, narQuadricFills)
    drawSegmentSolidsFillNarys(state, narClips)
    drawQuadricFills(narQuadricFills, narView.screenMatrix, narClips)
    drawRuledSurfaceOutlineNarys(state)
    drawSegmentsNarys(state, true, pxPerPtWorkspace)
    drawCirclesNarys(state, true, pxPerPtWorkspace)
    drawAllConicsNarys(state, pxPerPtWorkspace, true)
}
