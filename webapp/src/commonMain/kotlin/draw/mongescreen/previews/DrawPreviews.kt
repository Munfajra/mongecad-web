package draw.mongescreen.previews

import draw.mongescreen.conicarcs.drawCircleArcNarys
import draw.mongescreen.conicarcs.drawCircleArcPudorys
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope






import draw.mongescreen.objects.orth.*
import draw.mongescreen.previews.arcs.*
import draw.mongescreen.previews.circles.drawCirclePreviewNarys
import draw.mongescreen.previews.circles.drawCirclePreviewPudorys
import draw.mongescreen.previews.conics.*
import draw.mongescreen.conicarcs.*
import draw.mongescreen.previews.cursor.drawCursorPreviewAO
import draw.mongescreen.previews.cursor.drawCursorPreviewCross
import draw.mongescreen.previews.cursor.drawCursorPreviewCrossAxo
import draw.mongescreen.previews.lines.drawCustomLineTrimPreviewBokorysAxo
import draw.mongescreen.previews.lines.drawCustomLineTrimPreviewNarysAxo
import draw.mongescreen.previews.lines.drawCustomLineTrimPreviewPudorysAxo
import draw.mongescreen.previews.lines.bokorys.previewSingleBokorysLineCursorAxo
import draw.mongescreen.previews.lines.bokorys.previewSingleBokorysParallelCursorAxo
import draw.mongescreen.previews.lines.drawTempSnapLineAxoOverlay
import draw.mongescreen.previews.lines.drawTempSnapLineBokorys
import draw.mongescreen.previews.lines.drawTempSnapLineNarys
import draw.mongescreen.previews.lines.drawTempSnapLinePudorys
import draw.mongescreen.previews.lines.narys.*
import draw.mongescreen.previews.lines.pudorys.*
import draw.mongescreen.previews.points.*
import draw.mongescreen.previews.polygons.drawRegularPolygonPreview
import draw.mongescreen.previews.segments.drawKotoSegmentEndpointHighlight
import draw.mongescreen.previews.segments.helpline.*
import draw.mongescreen.previews.segments.narys.previewAssociatedNarysSegmentCursor
import draw.mongescreen.previews.segments.narys.previewAssociatedSegmentsNarysTemporary
import draw.mongescreen.previews.segments.narys.previewNarysSegmentCursor
import draw.mongescreen.previews.segments.pudorys.*
import draw.mongescreen.previews.tools.*
import draw.mongescreen.previews.traces.narys.*
import draw.mongescreen.previews.traces.pudorys.*
import model.ProjectionMode
import model.VisibleQuad
import model.axo.AxoMode


import state.MongeState


fun DrawScope.drawAllDashedPreviewsNarys(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    drawPreviewPointNarys(state)
    drawCurveNarysPreview(state,snappedPointLogical)
    drawCurve3DPreviewNarys(state)

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




// drawAllAxoAOPreviews / drawAllAxoAoObjects / drawAxo*Preview – náhledy
// v axonometrii a v AO overlayi; web axonometrii nekreslí.
