package canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import draw.mongescreen.drawAllObjectsNar
import draw.mongescreen.drawAllObjectsPud
import draw.mongescreen.handleHoverDetection
import draw.mongescreen.init.initializeCanvasOffsetIfNeeded
import draw.mongescreen.labels.Labels
import draw.mongescreen.labels.drawLabels
import draw.mongescreen.labels.rememberLabelScale
import draw.mongescreen.objects.orth.drawArcsNarys
import draw.mongescreen.objects.orth.drawArcsPudorys
import draw.mongescreen.previews.drawAllDashedPreviewsNarys
import draw.mongescreen.previews.drawAllDashedPreviewsPudorys
import draw.mongescreen.previews.drawConicArcPreviewsOnTopNarys
import draw.mongescreen.previews.drawConicArcPreviewsOnTopPudorys
import draw.mongescreen.previews.lines.drawCustomLineTrimPreviewNarys
import draw.mongescreen.previews.lines.drawCustomLineTrimPreviewPudorys
import draw.mongescreen.previews.points.drawAidPoints
import draw.mongescreen.previews.tools.drawHalfPlaneX12Overlay
import ui.mongeui.toolbar.currentPaperAnchorLogical
import utils.toScreen
import model.LocalMongeColors
import model.Mongeobjects
import model.ProjectionMode
import model.ProjectionType
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.lighter
import model.runtimePlaneColor
import model.withRuntimeCanvasColors
import serialization.SettingsManager
import state.MongeState
import ui.colorpicker.ReferencePlanesToggleRow
import ui.colorpicker.homeButton
import ui.colorpicker.redoButton
import ui.colorpicker.undoButton
import ui.mongeui.toolbar.rightDescriptionBar.ProjectionCompletionOverlayButton

/**
 * Kreslicí plocha Mongeova promítání.
 *
 * Struktura odpovídá desktopu: clipRect → převrácení os podle orientace →
 * objekty → popisky, nad tím overlay popisků a rohová tlačítka.
 *
 * Kreslí body, přímky, úsečky, kružnice, kuželosečky, oblouky a křivky
 * včetně náhledů během konstrukce. Chybí výplně kvadrik a překryv
 * půlroviny x₁₂ (vyřazené featury).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppMongeCanvas(state: MongeState) {
    val snappedPointLogical = state.snappedPointLogical
    val labelScale = rememberLabelScale(state)
    LaunchedEffect(labelScale) {
        SettingsManager.runtimeLabelScale = labelScale
    }
    val colors = LocalMongeColors.current
    LaunchedEffect(colors.selected) {
        state.hoverHaloColor = colors.selected.lighter(0.4f).copy(alpha = 0.38f)
        state.selectedHaloColor = colors.selected.copy(alpha = 0.55f)
    }
    val color = runtimePlaneColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = color)
    ) {
        Canvas(
            modifier = Modifier
                .focusable()
                .fillMaxSize()
                // Klíčem je výkres, ne obsluha. `Modifier.onPointerEvent` si klíčuje
                // podle předané lambdy, a ta tu vzniká znovu při každé rekompozici –
                // tedy při každém pohybu kurzoru. Vstupní uzel se kvůli tomu pořád
                // dokola rušil a zakládal a události, které do toho okna spadly,
                // se ztrácely.
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Move) continue
                            state.cursorPosition = event.changes.first().position
                            handleHoverDetection(state)
                        }
                    }
                }
        ) {
            state.canvasHeight = size.height
            state.canvasWidth = size.width
            clipRect {
                fun drawAll() {
                    val pxPerPtWorkspace = 1f
                    initializeCanvasOffsetIfNeeded(state, size)
                    // Ztmavení nepoužívané půlroviny – ukazuje, do které průmětny
                    // se právě kreslí. Seznam nástrojů je stejný jako na desktopu,
                    // bez těch, které web nemá (šroubovice, rotační plochy).
                    if (state.drawobjects in listOf(
                            Mongeobjects.PARABOLA,
                            Mongeobjects.HYPERBOLA,
                            Mongeobjects.CIRCLE,
                            Mongeobjects.ARC,
                            Mongeobjects.ELLIPSE,
                            Mongeobjects.SEGMENTS,
                            Mongeobjects.PLANE,
                            Mongeobjects.REGULAR_POLYGON_IN_PLANE,
                            Mongeobjects.SEGMENT_ON_LINE,
                            Mongeobjects.LINES,
                            Mongeobjects.ERASE,
                        ) || (state.drawobjects == Mongeobjects.POINTS &&
                            (state.projekcnityp == ProjectionType.ASSOCIATED || state.projekcnityp == ProjectionType.SINGLE))
                        || (state.drawobjects == Mongeobjects.CURVE && state.projekcnityp == ProjectionType.SINGLE)
                    ) {
                        drawHalfPlaneX12Overlay(state)
                    }
                    state.triggerRedraw
                    if (state.showConstruction.value) {
                        drawArcsPudorys(state, pxPerPtWorkspace)
                        drawArcsNarys(state, pxPerPtWorkspace)
                        drawAidPoints(state, pxPerPtWorkspace)
                    }
                    drawAllDashedPreviewsNarys(state, snappedPointLogical)
                    drawAllDashedPreviewsPudorys(state, snappedPointLogical)
                    drawAllObjectsPud(state, pxPerPtWorkspace)
                    drawAllObjectsNar(state, pxPerPtWorkspace)
                    // náhledy oblouků navrch, aby je kuželosečka nepřekryla
                    drawConicArcPreviewsOnTopNarys(state, snappedPointLogical)
                    drawConicArcPreviewsOnTopPudorys(state, snappedPointLogical)
                    drawCustomLineTrimPreviewPudorys(state)
                    drawCustomLineTrimPreviewNarys(state)
                }

                val flipX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
                val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
                val x = if (flipX) -1f else 1f
                val y = if (flipY) -1f else 1f

                withRuntimeCanvasColors {
                    withTransform({ scale(x, y) }) {
                        drawAll()
                    }
                    drawLabels(
                        state,
                        state.scale,
                        state.canvasOffset,
                        1f,
                        state.showConstruction.value,
                        runtimeColors = true
                    )
                }

                if (state.showPaperPreview) {
                    val anchorLogical = currentPaperAnchorLogical(state)

                    drawPaperViewportOverlayAnchored(
                        canvasSizePx = size,
                        logicalAnchor = anchorLogical,
                        logicalToScreen = { logical ->
                            logical.toScreen(
                                state.scale,
                                offset = state.canvasOffset,
                                canvasHeight = state.canvasHeight,
                                state = state,
                                canvasWidth = state.canvasWidth
                            )
                        },
                        paper = state.paperFormat,
                        portrait = state.paperPortrait,
                        unitsPerMm = 1f
                    )
                }
            }
        }

        Labels(state, snappedPointLogical)

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            homeButton(
                state = state,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(0.dp, color = Color.Transparent, shape = RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.height(3.dp))
            undoButton(
                state = state,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(0.dp, color = Color.Transparent, shape = RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.height(3.dp))
            redoButton(
                state = state,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(0.dp, color = Color.Transparent, shape = RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (state.projectionMode == ProjectionMode.MONGE) {
                ReferencePlanesToggleRow(
                    state, modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .border(0.dp, color = Color.Transparent, shape = RoundedCornerShape(5.dp))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ProjectionCompletionOverlayButton(state)
        }

        CanvasZoomControl(
            state = state,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
        )
    }
}
