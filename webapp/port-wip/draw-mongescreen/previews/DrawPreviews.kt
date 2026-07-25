package draw.mongescreen.previews

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.fills.FillView
import draw.mongescreen.fills.computeOcclusionClips
import draw.mongescreen.fills.drawQuadricFills
import draw.mongescreen.fills.drawSegmentSolidsFillAxo
import draw.mongescreen.fills.quadricFillsAxo
import draw.mongescreen.objects.drawRuledSurfaceOutlineAxo
import draw.mongescreen.objects.axo.*
import draw.mongescreen.objects.orth.*
import draw.mongescreen.previews.arcs.*
import draw.mongescreen.previews.circles.drawCirclePreviewNarys
import draw.mongescreen.previews.circles.drawCirclePreviewPudorys
import draw.mongescreen.previews.conics.*
import draw.mongescreen.previews.conicsarcs.*
import draw.mongescreen.previews.cursor.drawCursorPreviewAO
import draw.mongescreen.previews.cursor.drawCursorPreviewCross
import draw.mongescreen.previews.cursor.drawCursorPreviewCrossAxo
import draw.mongescreen.previews.lines.AO.drawAOLinePreviews
import draw.mongescreen.previews.lines.drawCustomLineTrimPreviewBokorysAxo
import draw.mongescreen.previews.lines.drawCustomLineTrimPreviewNarysAxo
import draw.mongescreen.previews.lines.drawCustomLineTrimPreviewPudorysAxo
import draw.mongescreen.previews.lines.axo.previewSingleAxoLineCursor
import draw.mongescreen.previews.lines.axo.previewSingleAxoParallelOrOrthogonalCursor
import draw.mongescreen.previews.lines.bokorys.previewSingleBokorysLineCursorAxo
import draw.mongescreen.previews.lines.bokorys.previewSingleBokorysParallelCursorAxo
import draw.mongescreen.previews.lines.drawTempSnapLineAxoOverlay
import draw.mongescreen.previews.lines.drawTempSnapLineBokorys
import draw.mongescreen.previews.lines.drawTempSnapLineNarys
import draw.mongescreen.previews.lines.drawTempSnapLinePudorys
import draw.mongescreen.previews.lines.linecomplete.drawLineCompletionPreviewAxoOverlay
import draw.mongescreen.previews.lines.linecomplete.drawLineCompletionPreviewBokorys
import draw.mongescreen.previews.lines.linecomplete.drawLineCompletionPreviewNarys
import draw.mongescreen.previews.lines.linecomplete.drawLineCompletionPreviewPudorys
import draw.mongescreen.previews.lines.narys.*
import draw.mongescreen.previews.lines.pudorys.*
import draw.mongescreen.previews.planes.drawAxoPlanePreviewBokorys
import draw.mongescreen.previews.planes.drawAxoPlanePreviewNarys
import draw.mongescreen.previews.planes.drawAxoPlanePreviewPudorys
import draw.mongescreen.previews.helix.drawHelixPreviewAxo
import draw.mongescreen.previews.helix.drawHelixPreviewBokorysAxo
import draw.mongescreen.previews.helix.drawHelixPreviewNarys
import draw.mongescreen.previews.helix.drawHelixPreviewNarysAxo
import draw.mongescreen.previews.helix.drawHelixPreviewPudorys
import draw.mongescreen.previews.helix.drawHelixPreviewPudorysAxo
import draw.mongescreen.previews.points.*
import draw.mongescreen.previews.polygons.drawRegularPolygonPreview
import draw.mongescreen.previews.segments.AO.previewAOSegmentCursor
import draw.mongescreen.previews.segments.axo.previewAxoSegmentCursor
import draw.mongescreen.previews.segments.bokorys.previewAxoBokorysSegmentCursor
import draw.mongescreen.previews.segments.drawKotoSegmentEndpointHighlight
import draw.mongescreen.previews.segments.helpline.*
import draw.mongescreen.previews.segments.narys.previewAssociatedNarysSegmentCursor
import draw.mongescreen.previews.segments.narys.previewAssociatedSegmentsNarysTemporary
import draw.mongescreen.previews.segments.narys.previewAxoNarysSegmentCursor
import draw.mongescreen.previews.segments.narys.previewNarysSegmentCursor
import draw.mongescreen.previews.segments.pudorys.*
import draw.mongescreen.previews.segments.segmentcomplete.drawSegmentCompletionPreviewAxoOverlay
import draw.mongescreen.previews.segments.segmentcomplete.drawSegmentCompletionPreviewBokorys
import draw.mongescreen.previews.segments.segmentcomplete.drawSegmentCompletionPreviewNarys
import draw.mongescreen.previews.segments.segmentcomplete.drawSegmentCompletionPreviewPudorys
import draw.mongescreen.previews.tools.*
import draw.mongescreen.previews.traces.narys.*
import draw.mongescreen.previews.traces.pudorys.*
import model.ProjectionMode
import model.VisibleQuad
import model.axo.AxoMode
import monge.input.axo.AxoRenderBasis
import monge.input.axo.segments.segmentcomplete.updateAxoSegmentCompletionTempLine
import state.MongeState


fun DrawScope.drawAllDashedPreviewsNarys(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    drawPreviewPointNarys(state)
    drawCurveNarysPreview(state,snappedPointLogical)
    drawCurve3DPreviewNarys(state)
    drawHelixPreviewNarys(state)
    drawOverlayAnglePlacementNarys(state, snappedPointLogical)
    previewSingleNarysLineCursor(state, snappedPointLogical)
    PreviewNarysTraceDirCursor(state,snappedPointLogical)
    PreviewNarysTraceParallelTemporary(state)
    previewAssociatedNarysLineTemporary(state)
    previewAssociatedNarysParallelCursor(state,snappedPointLogical)
    previewAssociatedNarysLineCursor(state,snappedPointLogical)
    PreviewNarysTraceParallelCursor(state,snappedPointLogical)
    PreviewNarysTraceCursor(state,snappedPointLogical)
    PreviewNarysTraceTemporary(state)
    previewNarysSegmentCursor(state,snappedPointLogical)
    previewAssociatedNarysSegmentCursor(state,snappedPointLogical)
    helpLineSegmentsFirstPointNarys(state)
    helpLineSegmentsSecondPointNarys(state)
    previewTemporarySegmentLineNarys(state)
    previewTemporarySegmentLinePlacementNarys(state, snappedPointLogical)
    previewSegmentNarysFromAssociatedPointsDir(state, snappedPointLogical)
    previewAssociatedSegmentsNarysTemporary(state)
    previewNarysTransDirectionCursor(state,snappedPointLogical)
    previewAssociatedNarysLineTemporarySegment(state)
    previewSingleNarysLineCursorSegment(state,snappedPointLogical)
    drawArcPreviewRadiusNarys(state, snappedPointLogical)
    drawArcPreviewEndNarys(state, snappedPointLogical)
    drawSegmentHighlightsNarys(state, true, 1f)
    drawPendingSegmentStartNarys(state)
    drawPreviewPointNarysTransDir(state)
    drawCirclePreviewNarys(state, snappedPointLogical)
    drawEllipseConstructionPreviewNarys(state, snappedPointLogical)
    drawParabolaConstructionPreviewNarys(state, snappedPointLogical)
    drawHyperbolaConstructionNarys(state, snappedPointLogical)
    drawEllipseArcNarys(state, snappedPointLogical)
    drawCircleArcNarys(state,snappedPointLogical)
    drawHyperbolaArcPreviewNarys(state, snappedPointLogical)
    drawEllipseArcPreviewNarys(state, snappedPointLogical)
    drawParabolaArcPreviewNarys(state, snappedPointLogical)
    drawHyperbolaPreviewNarysPlane(snappedPointLogical, state)
    drawHyperbolaPreviewNarys(state, snappedPointLogical)
    drawParabolaArcPreviewNarysAsoc(state, snappedPointLogical)
    drawHyperbolaArcPreviewNarys3D(state, snappedPointLogical)
    drawSpherePreviewNarys(state, snappedPointLogical)

    previewNarysLineSegmentCursor(state,snappedPointLogical)
}
//PŮDORYS

fun DrawScope.drawAllDashedPreviewsPudorys(
    state: MongeState,
    snappedPointLogical: Offset?

){
    if (state.projectionMode== ProjectionMode.KOTO) {
        drawKotoSegmentEndpointHighlight(state)
    }
    drawCurve3DPreviewPudorys(state)
    drawCurvePudorysPreview(state,snappedPointLogical)
    drawHelixPreviewPudorys(state)
    previewAssociatedPudorysLineTemporarySegment(state)
    previewSinglePudorysLineCursorSegment(state,snappedPointLogical)
    previewPudorysLineSegmentCursor(state,snappedPointLogical)
    previewSinglePudorysLineCursor(state,snappedPointLogical)
    previewPudorysTraceCursor(state,snappedPointLogical)
    previewPudorysTraceTemporary (state)
    previewAssociatedPudorysLineTemporary(state)
    previewAssociatedPudorysLineCursor(state,snappedPointLogical)
    previewSinglePudorysParallelCursor(state,snappedPointLogical)
    previewPudorysTraceParallelCursor(state,snappedPointLogical)
    previewPudorysTraceDirCursor(state,snappedPointLogical)
    previewPudorysTraceParallelTemporary(state)
    previewPudorysSegmentCursor(state,snappedPointLogical)
    previewAssociatedPudorysSegmentCursor(state,snappedPointLogical)
    previewAssociatedSegmentsPudorysTemporary(state)
    helpLineSegmentsSecondPointPudorys(state)
    helpLineSegmentsFirstPointPudorys(state)
    previewTemporarySegmentLinePudorys(state)
    previewTemporarySegmentLinePlacementPudorys(state, snappedPointLogical)
    previewSegmentPudorysFromAssociatedPointsDir(state, snappedPointLogical)
    previewPudorysTransDirectionCursor(state,snappedPointLogical)
    drawPreviewPointPudorys(state)
    drawVerticalHelperLine(state)
    drawArcPreviewRadiusPudorys(state, snappedPointLogical)
    drawArcPreviewEndPudorys(state, snappedPointLogical)
    drawSegmentHighlightsPudorys(state, true, 1f)
    drawPendingSegmentStartPudorys(state)
    drawPendingAssociatedSegmentPoints(state)
    handleGetDistanceDraw(state, snappedPointLogical)
    drawOverlayAnglePlacement(state, snappedPointLogical)
    drawPreviewPointPudorysTransDir(state)
    drawEllipseConstructionPreviewPudorys(state, snappedPointLogical)
    drawEllipseConstructionPreviewBothViews(state, snappedPointLogical)
    drawParabolaConstructionPreviewBothViews(state, snappedPointLogical)
    drawParabolaConstructionPreviewPudorys(state, snappedPointLogical)
    drawHyperbolaConstructionPudorys(state, snappedPointLogical)
    drawHyperbolaPreviewPudorys(state, snappedPointLogical)
    drawHyperbolaPreviewPudorysPlane(snappedPointLogical, state)
    drawEllipseArcPudorys(state, snappedPointLogical)
    drawCircleArcPudorys(state,snappedPointLogical)
    drawParabolaArcPreviewPudorys(state, snappedPointLogical)
    drawHyperbolaArcPreviewPudorys(state, snappedPointLogical)
    drawEllipseArcPreviewPudorys(state, snappedPointLogical)
    drawSpherePreviewPudorys(state, snappedPointLogical)
    drawPerpCylinderPreview(state, snappedPointLogical)
    drawPerpPrismPreview(state, snappedPointLogical)
    drawPlatonicPreview(state)
    drawCirclePreviewPudorys(state, snappedPointLogical)
    drawRegularPolygonPreview(state, snappedPointLogical)
    drawCursorPreviewCross(state, snappedPointLogical)
    midPointPreview(state, snappedPointLogical)
    drawParabolaArcPreviewPudorysAsoc(state, snappedPointLogical)
    drawHyperbolaArcPreviewPudorys3D(state, snappedPointLogical)

}



fun DrawScope.drawAllAxoAOPreviews(state: MongeState,pxPerpt: Float) {
    drawHelixPreviewAxo(state)
    previewAOSegmentCursor(state)
    previewAxoSegmentCursor(state)
    drawAOLinePreviews(state,pxPerpt)
    drawCursorPreviewAO(state,state.snappedPointLogical)
    previewSingleAxoParallelOrOrthogonalCursor(state)
    previewSingleAxoLineCursor(state)
    drawSpherePreviewAxo(state, state.snappedPointLogical)
    drawLineCompletionPreviewAxoOverlay(state)
    drawOverlayAnglePlacementAxo(state)
    drawSegmentCompletionPreviewAxoOverlay(state)
    drawPointCompletionPreviewAxoOverlay(state)
    drawEllipseConstructionPreviewGeneralAxo(state, state.snappedPointLogical)
    drawRegularPolygonPreview(state, state.snappedPointLogical)
    drawConicArcPreviewForProjection(state, state.snappedPointLogical,
        state.conicInputPointsAxo, state.hyperbolaInputsAxo, "axo")
    drawPlatonicPreview(state)
    drawPerpCylinderPreview(state,state.snappedPointLogical)
    midPointPreviewAxo(state)
    handleGetDistanceDrawAxo(state)
    drawPendingTransParallelLineAxoOverlay(state)
    drawTempSnapLineAxoOverlay(state)
    drawPerpPrismPreview(state,state.snappedPointLogical)
}
fun DrawScope.drawAllAxoAoObjects(state: MongeState,pxPerpt: Float, renderBasis: AxoRenderBasis,show: Boolean) {
    val axoView = FillView.axo(state, renderBasis)
    val axoQuadricFills = quadricFillsAxo(state, renderBasis)
    val axoClips = computeOcclusionClips(state, axoView, axoQuadricFills)
    drawSegmentSolidsFillAxo(state, renderBasis, axoClips)
    drawQuadricFills(axoQuadricFills, axoView.screenMatrix, axoClips)
    drawRuledSurfaceOutlineAxo(state)
    drawAxoSegments(state,pxPerpt)
    drawAxoLines(state)
    drawAllConicsAxo(state,pxPerpt)
    if (show) {
        drawAOPoints(state,pxPerpt)
        drawAOLines(state,pxPerpt)
        drawArcsAO(state,pxPerpt)
        drawAOSegments(state,pxPerpt)
    }
}
fun DrawScope.drawAxoPudorysPreview(state: MongeState,snappedPointLogical: Offset?,visibleQuad: VisibleQuad?) {

    drawHelixPreviewPudorysAxo(state)

    if (state.axoMode == AxoMode.AXO_PUDORYS) {
        updateAxoSegmentCompletionTempLine(state)

        drawCursorPreviewCrossAxo(state, snappedPointLogical)
        previewSinglePudorysLineCursorAxo(state, snappedPointLogical, visibleQuad)
        drawLineCompletionPreviewPudorys(state, snappedPointLogical, visibleQuad)
        previewSinglePudorysParallelCursorAxo(state, snappedPointLogical, visibleQuad)
        previewAxoPudorysSegmentCursor(state,snappedPointLogical,visibleQuad)
        drawTempSnapLinePudorys(state,visibleQuad)
        drawCustomLineTrimPreviewPudorysAxo(state)
        drawCurvePudorysPickPreviewAxo(state, snappedPointLogical)


    }
    drawSegmentCompletionPreviewPudorys(state)
    drawAxoPlanePreviewPudorys(state, snappedPointLogical, visibleQuad)
    drawPointCompletionPreviewPudorys(state)
    drawEllipseConstructionPreviewPudorysAxo(state, snappedPointLogical)
    drawConicArcPreviewForProjection(state, snappedPointLogical,
        state.conicInputPointsPudorys, state.hyperbolaInputsPudorys, "pudorys")

}
fun DrawScope.drawAxoNarysPreview(state: MongeState,snappedPointLogical: Offset?,visibleQuad: VisibleQuad?) {
    drawHelixPreviewNarysAxo(state)
    if (state.axoMode == AxoMode.AXO_NARYS) {
        updateAxoSegmentCompletionTempLine(state)

        drawCursorPreviewCrossAxo(state, snappedPointLogical)
        previewSingleNarysLineCursorAxo(state,snappedPointLogical,visibleQuad)
        drawLineCompletionPreviewNarys(state, snappedPointLogical, visibleQuad)
        previewAssociatedNarysParallelCursorAxo(state,snappedPointLogical,visibleQuad)
        previewAxoNarysSegmentCursor(state,snappedPointLogical,visibleQuad)
        drawTempSnapLineNarys(state,visibleQuad)
        drawCustomLineTrimPreviewNarysAxo(state)
        drawCurveNarysPickPreviewAxo(state, snappedPointLogical)

    }
    drawSegmentCompletionPreviewNarys(state)
    drawAxoPlanePreviewNarys(state, snappedPointLogical, visibleQuad)
    drawPointCompletionPreviewNarys(state)
    drawEllipseConstructionPreviewNarysAxo(state, snappedPointLogical)
    drawConicArcPreviewForProjection(state, snappedPointLogical,
        state.conicInputPointsNarys, state.hyperbolaInputsNarys, "narys")
}
fun DrawScope.drawAxoBokorysPreview(state: MongeState,snappedPointLogical: Offset?,visibleQuad: VisibleQuad?) {
    drawHelixPreviewBokorysAxo(state)
    if (state.axoMode == AxoMode.AXO_BOKORYS) {
        updateAxoSegmentCompletionTempLine(state)

        drawCursorPreviewCrossAxo(state, snappedPointLogical)
        previewSingleBokorysLineCursorAxo(state,snappedPointLogical,visibleQuad)
        drawLineCompletionPreviewBokorys(state, snappedPointLogical, visibleQuad)
        previewSingleBokorysParallelCursorAxo(state,snappedPointLogical,visibleQuad)
        previewAxoBokorysSegmentCursor(state,snappedPointLogical,visibleQuad)
        drawTempSnapLineBokorys(state,visibleQuad)
        drawCustomLineTrimPreviewBokorysAxo(state)
        drawCurveBokorysPickPreviewAxo(state, snappedPointLogical)
        drawCurve3DPreviewBokorysAxo(state, 1f)
    }
    drawSegmentCompletionPreviewBokorys(state)
    drawAxoPlanePreviewBokorys(state, snappedPointLogical, visibleQuad)
    drawPointCompletionPreviewBokorys(state)
    drawEllipseConstructionPreviewBokorysAxo(state, snappedPointLogical)
    drawHyperbolaArcPreviewBokorys(state, snappedPointLogical)
    drawConicArcPreviewForProjection(state, snappedPointLogical,
        state.conicInputPointsBokorys, state.hyperbolaInputsBokorys, "bokorys")
}
