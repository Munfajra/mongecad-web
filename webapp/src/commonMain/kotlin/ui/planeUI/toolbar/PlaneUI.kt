package ui.planeUI.toolbar

import dialogs.batchinput.BatchInputLauncherDialogs
import utils.format
import dialogs.Dialogs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import canvas.AppMongeCanvas
import draw.mongescreen.labels.rememberLabelScale
import model.*
import serialization.SettingsManager
import monge.input.ConicArcs.associated.findNarysConicIdByParent
import monge.input.ConicArcs.associated.findPudorysConicIdByParent
import monge.input.handleClick
import monge.input.lines.ensureAxisExists
import state.MongeState
import state.snapMonge.computeSnappedPoint
import ui.mongeui.VerticalResizeHandleOverlay
import ui.planeUI.toolbar.rightDescriptionBar.RightSidebarPlane
import ui.theme.LocalMongeDimens
import ui.handleCanvasNavigationEvent
import ui.canvasClickChange
import ui.isCanvasClickGesture
import utils.cursorToScreen
import utils.getLogicalCursor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaneUI(state: MongeState, requestGlobalFocus: () -> Unit) {
    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val showDialog = remember { mutableStateOf(false) }
    ensureAxisExists(state)
    val showPlaneDialog = remember { mutableStateOf(false) }

    var setBlack by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentHelpLineStyleSettings) {
        if (state.projectionMode == ProjectionMode.PLANE && !setBlack) {
            state.currentHelpLineStyleSettings.color = Color.Black
            setBlack = true
        }
    }
    val buttonsize = SettingsManager.current.UIscale.dp
    val snappedPointLogical: Offset? by remember(
        state.pointsPudorys,
        state.pointsNarys,
        state.lines3DPudorys,
        state.lines3DNarys,
        state.lineTracesPudorys,
        state.lineTracesNarys,
        state.scale,
        state.canvasOffset,
        state.cursorPosition,
        state.isCtrlPressed
    ) {
        derivedStateOf {
            if (state.isCtrlPressed) return@derivedStateOf null
            computeSnappedPoint(state, state.cursorPosition)

        }
    }
    state.snappedPointLogical =snappedPointLogical
    val labelScale = rememberLabelScale(state)
    LaunchedEffect(labelScale) {
        SettingsManager.runtimeLabelScale = labelScale
    }
    LaunchedEffect(state.pendingMongeModeChange) {
        state.pendingMongeModeChange?.let {
            state.mongeMode = DrawingModeMonge.PUDORYS
            state.pendingMongeModeChange = null
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()

    ) {

        Divider(
            color = colors.base,
            modifier = Modifier
                .fillMaxWidth()
                .height(1*ui.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.toolbarHeight)
                .background(colors.background)
        ) {

            PlaneToolbar(state,
                showDialog = showDialog,
                showParamDialog = state.showParamDialog,
                buttonsize = buttonsize
            )
        }
        BatchInputLauncherDialogs(
            state = state,
            showDialog = showDialog,
            showParamDialog = state.showParamDialog,
            showPlaneDialog = showPlaneDialog
        )
        Divider(
            color = colors.base,
            modifier = Modifier
                .fillMaxWidth()
                .width(1*ui.dp)       // jen 1 dp čára místo zdvojení
        )
//hlavní obsah(nástroje+canvas+pravý panel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        {
            //levý panel nástrojů
            Column(
                modifier = Modifier
                    .width(50f*ui.dp)
                    .fillMaxHeight()
                    .background(colors.background)
                            ) { PlaneLeftPanelToolbar(state) }
            Divider(
                color = colors.base,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1*ui.dp)       // jen 1 dp čára místo zdvojení
            )
            //hlavní scéna
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Řádek přes celou výšku: vlevo canvas se svým horním barem,
                // vpravo panel „Aktuální výběr" (sahá až nahoru, přes úroveň baru).
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val density = LocalDensity.current
                    val minSidebarDp = (280f* ui).dp
                    val maxSidebarDp = (500* ui).dp
                    val minSidebarPx = with(density) { minSidebarDp.toPx() }
                    val maxSidebarPx = with(density) { maxSidebarDp.toPx() }
                    val sidebarWidthPx = state.rightSidebarW.coerceIn(minSidebarPx, maxSidebarPx)
                   Column(
                       modifier = Modifier
                           .weight(1f)
                           .fillMaxHeight()
                   ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24*ui.dp)
                            .background(colors.background)
                            .padding(horizontal = 8*ui.dp, vertical = 4*ui.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val logicalCursor = getLogicalCursor(
                            snappedPointLogical,
                            state.cursorPosition,
                            state.canvasOffset,
                            state.scale,
                            state.canvasWidth,
                            state.canvasHeight,
                            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
                        )
                        val coordText = "x: %.1f, y: %.1f".format(
                            logicalCursor.x / 10f,
                            logicalCursor.y / 10f
                        )
                        Text(
                            color = colors.text,
                            text = coordText,
                            modifier = Modifier.weight(1f),
                            fontSize = 12*ui.sp
                        )
                    }
                    Divider(
                        color = colors.base,
                        modifier = Modifier
                            .fillMaxWidth()
                            .width(1*ui.dp)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.White)
                            .focusRequester(state.focusRequester)
                            .focusable()
                            .pointerInput(state) {
                                while (true) {

                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: continue

                                            val navigationHandled =
                                                handleCanvasNavigationEvent(event, state)
                                            val pScreen = change.position
                                            // Co si vzalo tlačítko v překryvu plátna, do
                                            // konstrukce nepatří. Ťuknutí na „+" jinak přesune
                                            // kurzor konstrukce do rohu plátna a rozdělaná
                                            // konstrukce se dopočítá k nesmyslu. Myš se z toho
                                            // vylíže prvním pohybem, prst žádný další pohyb
                                            // nepošle – proto se to dělo jen dotykem.
                                            if (!change.isConsumed) state.cursorPosition = pScreen

                                            if (
                                                !change.isConsumed &&
                                                !navigationHandled &&
                                                isCanvasClickGesture(change, event, state)
                                            ) {
                                                handleClick(
                                                    cursor = pScreen, // pořád screen
                                                    snappedPointLogical = snappedPointLogical,
                                                    state = state,
                                                    change = canvasClickChange(change)
                                                )
                                            }
                                            val isSecondaryPressed = event.buttons.isSecondaryPressed

                                            if (!state.wasSecondaryPressed && isSecondaryPressed) {
                                                if (state.drawobjects == Mongeobjects.CONICARC || state.drawobjects == Mongeobjects.CONICARCAS) {
                                                    when (state.mongeMode) {
                                                        DrawingModeMonge.PUDORYS -> {
                                                            val conicP = state.activeConicIdForArc
                                                                // Opuštění scope by zahodilo události,
                                                                // které dorazí do jeho znovuzaložení.
                                                                ?: continue
                                                            val current = state.activeArcMode
                                                                ?: state.ellipseArcMode[conicP]
                                                                ?: ArcMode.SHORTEST
                                                            val next = when (current) {
                                                                ArcMode.CCW -> ArcMode.CW
                                                                ArcMode.CW -> ArcMode.CCW
                                                                ArcMode.SHORTEST -> ArcMode.CW
                                                                ArcMode.LONGEST -> ArcMode.CCW
                                                            }
                                                            state.activeArcMode = next
                                                            state.ellipseArcMode[conicP] = next
                                                            // zrcadlo do nárysu pro preview v druhém panelu
                                                            state.activeParentConic3DIdForEllipseArc?.let { parentId ->
                                                                state.findNarysConicIdByParent(parentId)?.let { nId ->
                                                                    state.ellipseArcMode[nId] = next
                                                                }
                                                            }
                                                            println("🔁 Směr elipsového oblouku (P ${conicP}): $next")
                                                        }

                                                        DrawingModeMonge.NARYS -> {
                                                            val conicN = state.activeConicIdForArc
                                                                // Opuštění scope by zahodilo události,
                                                                // které dorazí do jeho znovuzaložení.
                                                                ?: continue
                                                            val current =
                                                                state.activeArcMode ?: state.ellipseArcMode[conicN]
                                                                ?: ArcMode.SHORTEST
                                                            val next = when (current) {
                                                                ArcMode.CCW -> ArcMode.CW
                                                                ArcMode.CW -> ArcMode.CCW
                                                                ArcMode.SHORTEST -> ArcMode.CW
                                                                ArcMode.LONGEST -> ArcMode.CCW
                                                            }
                                                            state.activeArcMode = next
                                                            state.ellipseArcMode[conicN] = next
                                                            // zrcadlo do půdorysu pro preview v druhém panelu
                                                            state.activeParentConic3DIdForEllipseArc?.let { parentId ->
                                                                state.findPudorysConicIdByParent(parentId)?.let { pId ->
                                                                    state.ellipseArcMode[pId] = next
                                                                }
                                                            }
                                                            println("🔁 [N] Směr elipsového oblouku (N ${conicN}): $next")
                                                        }

                                                    }
                                                } else {
                                                    val arcDataComplete =
                                                        state.drawobjects == Mongeobjects.ARC &&
                                                                (
                                                                        (state.arc.arcCenterNarys != null && state.arc.arcRadiusPointNarys != null) ||
                                                                                (state.arc.arcCenterPudorys != null && state.arc.arcRadiusPointPudorys != null)
                                                                        )

                                                    val phaseAllowsFlip = state.projectionPhase in listOf(
                                                        "distance_target_place",
                                                        "angle_new_ray"
                                                    )

                                                    if (arcDataComplete || phaseAllowsFlip) {
                                                        state.arc.arcDirectionClockwise = !state.arc.arcDirectionClockwise
                                                        println("🔁 Směr oblouku změněn na: ${if (state.arc.arcDirectionClockwise) "↻ CW" else "↺ CCW"}")
                                                    }
                                                }
                                            }

                                            state.wasSecondaryPressed = isSecondaryPressed

                                            // Zabírat se smí jen to, co plátno opravdu
                                            // obsloužilo. Bezpodmínečné consume() bralo
                                            // i události prvků nad plátnem – táhlo zoomu
                                            // pak v rovině nešlo chytit prstem ani hrotem,
                                            // protože jeho gesto se hned zrušilo. MONGE
                                            // obrazovka to má takhle od začátku.
                                            val isInteraction =
                                                event.type == PointerEventType.Scroll ||
                                                    event.buttons.isPrimaryPressed ||
                                                    event.buttons.isSecondaryPressed ||
                                                    change.changedToDown() ||
                                                    change.changedToUp()
                                            if (isInteraction) change.consume()

                                            // Zoom kolečkem
                                            if (event.type == PointerEventType.Scroll) {
                                                val scroll = change.scrollDelta
                                                val zoomFactor = 1.3f
                                                val oldScale = state.scale

                                                val newScale =
                                                    (oldScale * if (scroll.y > 0f) (1f / zoomFactor) else zoomFactor)
                                                        .coerceIn(1f, 50f)

                                                val cursor = state.cursorPosition

                                                // stejné flipy jako všude
                                                val flipX = (state.xAxisDirection == XAxisDirection.POSITIVE_LEFT)
                                                val flipY =
                                                    (state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP)

                                                // 1) logical pod kurzorem při starém scale
                                                val logicalAtCursor = getLogicalCursor(
                                                    snapped = null,
                                                    cursor = cursor,
                                                    canvasOffset = state.canvasOffset,
                                                    scale = oldScale,
                                                    canvasWidth = state.canvasWidth,
                                                    canvasHeight = state.canvasHeight,
                                                    flipX = flipX,
                                                    flipY = flipY
                                                )

                                                // 2) změň scale
                                                state.scale = newScale

                                                // 3) nastav offset tak, aby logicalAtCursor zůstal pod kurzorem
                                                val cursorScreen = cursorToScreen(
                                                    cursor = cursor,
                                                    canvasWidth = state.canvasWidth,
                                                    canvasHeight = state.canvasHeight,
                                                    flipX = flipX,
                                                    flipY = flipY
                                                )

                                                state.canvasOffset = cursorScreen - logicalAtCursor * newScale
                                            }


                                        }
                                    }
                                }
                            }
                            .onSizeChanged { state.canvasSizePx = it }
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clipToBounds()
                                .onSizeChanged { s ->
                                    state.canvasWidth = s.width.toFloat()
                                    state.canvasHeight = s.height.toFloat()
                                    state.triggerRedraw++
                                }
                        ) {

                            AppMongeCanvas(state)
                        }
                    }
                   }


                    Box(
                            modifier = Modifier
                            .width(with(density) { sidebarWidthPx.toDp() })
                            .fillMaxHeight()
                            .background(colors.background)
                    ) {
                        RightSidebarPlane(state)
                        VerticalResizeHandleOverlay(
                            colors = colors,
                            hitWidthDp = 12*ui.dp,
                            lineWidthDp = 1*ui.dp,
                            modifier = Modifier.align(Alignment.CenterStart),
                            onDragDeltaX = { dx ->
                                state.rightSidebarW = (state.rightSidebarW - dx)
                                    .coerceIn(minSidebarPx, maxSidebarPx)
                            }
                        )
                    }

                }

            }
        }
    }

    Dialogs(state,requestGlobalFocus)
    //dolní lištička
}
