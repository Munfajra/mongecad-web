package export.bitmapRenderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import draw.mongescreen.labels.drawLabels
import draw.mongescreen.objects.orth.drawAllConicsNarys
import draw.mongescreen.objects.orth.drawAllConicsPudorys
import draw.mongescreen.objects.orth.drawArcsNarys
import draw.mongescreen.objects.orth.drawArcsPudorys
import draw.mongescreen.objects.orth.drawCirclesNarys
import draw.mongescreen.objects.orth.drawCirclesPudorys
import draw.mongescreen.objects.orth.drawCurveNarys
import draw.mongescreen.objects.orth.drawCurvePudorys
import draw.mongescreen.objects.orth.drawLinesNarys
import draw.mongescreen.objects.orth.drawLinesPudorys
import draw.mongescreen.objects.orth.drawPointsNarys
import draw.mongescreen.objects.orth.drawPointsPudorys
import draw.mongescreen.objects.orth.drawSegmentsNarys
import draw.mongescreen.objects.orth.drawSegmentsPudorys
import draw.mongescreen.previews.points.drawAidPoints
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import state.MongeState

fun DrawScope.drawMongeSceneExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    drawLabels: Boolean = true,
    drawHelpers: Boolean = true,
    background: Color = Color.White,
    pxFactor: Float,
    strokePxFactor: Float = pxFactor,
    pointMarkerPxFactor: Float = pxFactor,
    x12RightEdgePx: Float? = null
) {
    drawRect(background)

    val oldOffset = state.canvasOffset
    val oldScale  = state.scale
    val oldW = state.canvasWidth
    val oldH = state.canvasHeight
    val oldSizePx = state.canvasSizePx

    try {
        // ✅ exportní canvas rozměry jako v runtime Canvas { state.canvasWidth = size.width ... }
        state.canvasWidth = size.width
        state.canvasHeight = size.height
        state.canvasSizePx = IntSize(size.width.toInt(), size.height.toInt())

        state.canvasOffset = offset
        state.scale = scale

        val pxPerPtWorkspace = strokePxFactor
        val pointMarkerPxPerPt = 1f * pointMarkerPxFactor

        // AXO větev exportu odpadá – web axonometrii nekreslí.

        fun drawExport() {
            if (drawHelpers) {
                drawAidPoints(state, pxPerPtWorkspace)
                drawArcsNarys(state, pxPerPtWorkspace)
                drawArcsPudorys(state, pxPerPtWorkspace)
            }

            when (state.projectionMode) {
                ProjectionMode.MONGE -> {
                    drawPointsNarys(state, pxPerPtWorkspace, pointMarkerPxPerPt)
                    drawPointsPudorys(state, pxPerPtWorkspace, pointMarkerPxPerPt)
                    drawCurvePudorys(state)
                    drawCurveNarys(state)













                    drawSegmentsPudorys(state, showHelpLine = drawHelpers, pxPerPtWorkspace)
                    drawSegmentsNarys(state, showHelpLine = drawHelpers, pxPerPtWorkspace)

                    drawLinesNarys(state, showHelpLine = drawHelpers, pxPerPtWorkspace)
                    drawLinesPudorys(state, showHelpLine = drawHelpers, pxPerPtWorkspace)


                    drawCirclesPudorys(state, showHelpCircle = drawHelpers, pxPerPtWorkspace)
                    drawCirclesNarys(state, showHelpCircle = drawHelpers, pxPerPtWorkspace)

                    drawAllConicsPudorys(state, pxPerPtWorkspace, showHelpConic = drawHelpers)
                    drawAllConicsNarys(state, pxPerPtWorkspace, showHelpConic = drawHelpers)
                }

                ProjectionMode.PLANE, ProjectionMode.KOTO -> {
                    drawPointsPudorys(state, pxPerPtWorkspace, pointMarkerPxPerPt)
                    drawCurvePudorys(state)
                    drawLinesPudorys(state, showHelpLine = drawHelpers, pxPerPtWorkspace)
                    if (state.projectionMode == ProjectionMode.KOTO) {





                    }
                    drawSegmentsPudorys(state, showHelpLine = drawHelpers, pxPerPtWorkspace)
                    drawCirclesPudorys(state, showHelpCircle = drawHelpers, pxPerPtWorkspace)
                    drawAllConicsPudorys(state, pxPerPtWorkspace, showHelpConic = drawHelpers)
                }

                else -> {}
            }
        }

        // ✅ flip úplně stejně jako v runtime: jen scale(x,y), žádný translate
        val flipX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
        val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        val sx = if (flipX) -1f else 1f
        val sy = if (flipY) -1f else 1f

        withTransform({
            scale(sx, sy)
        }) {
            drawExport()
        }

        // ✅ labely mimo transform (stejně jako runtime)
        if (drawLabels) {
            drawLabels(state, scale, offset, pxFactor, drawHelpers, x12RightEdgePx = x12RightEdgePx)
        }

    } finally {
        state.canvasOffset = oldOffset
        state.scale = oldScale
        state.canvasWidth = oldW
        state.canvasHeight = oldH
        state.canvasSizePx = oldSizePx
    }
}

// Vyříznuto: drawAxoSceneExport a výplně kvadrik – web axonometrii
// ani kvadriky nekreslí.
