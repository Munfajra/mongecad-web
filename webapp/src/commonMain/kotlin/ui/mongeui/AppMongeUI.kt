package ui.mongeui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import canvas.AppMongeCanvas
import dialogs.batchinput.BatchInputLauncherDialogs
import dialogs.Dialogs
import model.*
import monge.input.handleClick
import monge.input.lines.ensureAxisExists
import monge.input.lines.ensureX12LineExists
import serialization.SettingsManager
import state.MongeState
import state.snapMonge.computeSnappedPoint
import ui.WebCursor
import ui.setCanvasCursor
import ui.createAxoCursor
import ui.handleCanvasNavigationEvent
import ui.canvasClickChange
import ui.isCanvasClickGesture
import ui.mongeui.toolbar.LeftPanelToolbar
import ui.mongeui.toolbar.MongeToolbar
import ui.gl3d.Gl3DViewport
import ui.mongeui.toolbar.rightDescriptionBar.RightSidebar
import ui.theme.LocalMongeDimens
import ui.windowCursor
import utils.System
import utils.cursorToScreen
import utils.format
import utils.getLogicalCursor

/**
 * Hlavní obrazovka Mongeova promítání – rozvržení je záměrně identické
 * s desktopem: horní lišta, levý panel nástrojů (50 dp), canvas se svým
 * souřadnicovým barem a pravý panel s táhlem na změnu šířky.
 *
 * Rozdíly oproti desktopu jsou jen tam, kde web nemá odpovídající API:
 *  – kurzor jde přes CSS místo AWT (viz ui/WebCursor.kt),
 *  – odpadají dialogy dávkového zadávání.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppMongeUI(state: MongeState, requestGlobalFocus: () -> Unit) {

    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val showDialog = remember { mutableStateOf(false) }
    val buttonsize = SettingsManager.current.UIscale.dp
    val showPlaneDialog = remember { mutableStateOf(false) }
    val ui = SettingsManager.current.UIscale / 75f
    ensureX12LineExists(state)
    ensureAxisExists(state)
    val density = LocalDensity.current

    val minSidebarDp = 280f * ui
    val maxSidebarDp = 500f * ui
    val minSidebarPx = with(density) { minSidebarDp.dp.toPx() }
    val maxSidebarPx = with(density) { maxSidebarDp.dp.toPx() }
    val sidebarWidthPx = state.rightSidebarW.coerceIn(minSidebarPx, maxSidebarPx)
    val sidebarWidth = with(density) { sidebarWidthPx.toDp() }

    var cachedSnap by remember { mutableStateOf<Offset?>(null) }
    var lastCursorForSnap by remember { mutableStateOf<Offset?>(null) }

    val snappedPointLogical: Offset? by remember(
        state.pointsPudorys,
        state.pointsNarys,
        state.lines3DPudorys,
        state.lines3DNarys,
        state.lineTracesPudorys,
        state.lineTracesNarys,
        state.cursorPosition,
        state.isCtrlPressed,
        state.stopSnap,
    ) {
        derivedStateOf {
            // 0) Ctrl = snap úplně vypnutý
            if (state.isCtrlPressed) return@derivedStateOf null
            if (state.stopSnap > System.currentTimeMillis()) return@derivedStateOf null
            if (state.isPanning) return@derivedStateOf null

            // 1) DEADZONE (screen px)
            lastCursorForSnap?.let { last ->
                val dx = state.cursorPosition.x - last.x
                val dy = state.cursorPosition.y - last.y
                if (dx * dx + dy * dy < 4f) {   // 2px deadzone
                    return@derivedStateOf cachedSnap
                }
            }

            lastCursorForSnap = state.cursorPosition

            val snap = computeSnappedPoint(state, state.cursorPosition)
            cachedSnap = snap
            snap
        }
    }
    state.snappedPointLogical = snappedPointLogical

    LaunchedEffect(state.pendingMongeModeChange) {
        state.pendingMongeModeChange?.let {
            state.mongeMode = it
            state.pendingMongeModeChange = null
        }
    }

    // Blok se musí zapamatovat. Compose porovnává lambdu pointerInputu podle
    // identity, takže nová instance při každé rekompozici zruší rozpracované
    // gesto – a rekompozice tu nastává při každém pohybu kurzoru. Část dotyků
    // se pak ztratila: každé druhé ťuknutí prstem spadlo do zrušeného handleru.
    val canvasPointerHandler: suspend PointerInputScope.() -> Unit =
        remember(state) {
            {
                                        // Jediný awaitPointerEventScope na celou dobu života plátna.
                            // Kdyby se zakládal znovu pro každou událost, všechno, co
                            // dorazí mezi opuštěním a novým vstupem, se zahodí – při
                            // vytížené kompozici (náhledy konstrukcí) tak mizelo
                            // zvednutí prstu i druhý dotek dvouprstého gesta.
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        val navigationHandled =
                                            handleCanvasNavigationEvent(event, state)
                                        // Co si vzalo tlačítko v překryvu plátna, do
                                        // konstrukce nepatří. Ťuknutí na „+" jinak přesune
                                        // kurzor konstrukce do rohu plátna a rozdělaná
                                        // konstrukce se dopočítá k nesmyslu. Myš se z toho
                                        // vylíže prvním pohybem, prst žádný další pohyb
                                        // nepošle – proto se to dělo jen dotykem.
                                        if (!change.isConsumed) state.cursorPosition = change.position
                                        if (
                                            !change.isConsumed &&
                                            !navigationHandled &&
                                            isCanvasClickGesture(change, event, state)
                                        ) {
                                            handleClick(
                                                cursor = state.cursorPosition,
                                                snappedPointLogical = snappedPointLogical,
                                                state = state,
                                                change = canvasClickChange(change)
                                            )
                                        }

                                        val isSecondaryPressed = event.buttons.isSecondaryPressed

                                        // Pravý klik mění orientaci jen ve fázích, ve kterých
                                        // je směr součástí právě rozpracované konstrukce.
                                        // Stejné chování používá desktopová MONGE obrazovka.
                                        if (!state.wasSecondaryPressed && isSecondaryPressed) {
                                            if (
                                                state.drawobjects == Mongeobjects.PLATONIC_SOLID &&
                                                state.pendingPlatonicPolygonId != null
                                            ) {
                                                state.platonicFlip = !state.platonicFlip
                                                state.triggerRedraw++
                                            } else if (
                                                state.drawobjects == Mongeobjects.CONICARC ||
                                                state.drawobjects == Mongeobjects.CONICARCAS
                                            ) {
                                                val conicId = state.activeConicIdForArc
                                                if (conicId != null) {
                                                    val isCircle = when (state.mongeMode) {
                                                        DrawingModeMonge.PUDORYS ->
                                                            state.circlesPudorys.any { it.id == conicId }

                                                        DrawingModeMonge.NARYS ->
                                                            state.circlesNarys.any { it.id == conicId }
                                                    }

                                                    val current = state.activeArcMode
                                                        ?: if (isCircle) {
                                                            state.circleArcMode[conicId]
                                                        } else {
                                                            state.ellipseArcMode[conicId]
                                                        }
                                                        ?: ArcMode.SHORTEST

                                                    val next = when (current) {
                                                        ArcMode.CCW -> ArcMode.CW
                                                        ArcMode.CW -> ArcMode.CCW
                                                        ArcMode.SHORTEST -> ArcMode.CW
                                                        ArcMode.LONGEST -> ArcMode.CCW
                                                    }

                                                    state.activeArcMode = next
                                                    if (isCircle) {
                                                        state.circleArcMode[conicId] = next
                                                    } else {
                                                        state.ellipseArcMode[conicId] = next
                                                    }
                                                }
                                            } else {
                                                val arcDataComplete =
                                                    state.drawobjects == Mongeobjects.ARC &&
                                                        (
                                                            (
                                                                state.arc.arcCenterNarys != null &&
                                                                    state.arc.arcRadiusPointNarys != null
                                                                ) ||
                                                                (
                                                                    state.arc.arcCenterPudorys != null &&
                                                                        state.arc.arcRadiusPointPudorys != null
                                                                    ) ||
                                                                (
                                                                    state.pendingPoint1 != null &&
                                                                        state.pendingPoint2 != null
                                                                    )
                                                            )

                                                val phaseAllowsFlip = state.projectionPhase in listOf(
                                                    "distance_target_place",
                                                    "angle_new_ray",
                                                )

                                                if (arcDataComplete || phaseAllowsFlip) {
                                                    state.arc.arcDirectionClockwise =
                                                        !state.arc.arcDirectionClockwise
                                                }
                                            }
                                        }

                                        state.wasSecondaryPressed = isSecondaryPressed

                                        val isInteraction = event.type == PointerEventType.Scroll ||
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
                                                (state.scale * if (scroll.y > 0f) (1 / zoomFactor) else zoomFactor)
                                                    .coerceIn(1f, 50f)

                                            if (newScale == oldScale) continue

                                            val cursor = state.cursorPosition

                                            val cursorScreen = cursorToScreen(
                                                cursor = cursor,
                                                canvasWidth = state.canvasWidth,
                                                canvasHeight = state.canvasHeight,
                                                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                                                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
                                            )

                                            val logicalAtCursor =
                                                (cursorScreen - state.canvasOffset) / oldScale

                                            state.scale = newScale
                                            state.canvasOffset = cursorScreen - logicalAtCursor * state.scale
                                        }
                                    }
                                }
                            }
            }
        }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Divider(
            color = colors.base,
            modifier = Modifier
                .fillMaxWidth()
                .height(1 * ui.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.toolbarHeight)
                .background(colors.background)
        ) {
            MongeToolbar(
                state,
                showDialog = showDialog,
                showParamDialog = state.showParamDialog,
                showPlaneDialog = showPlaneDialog,
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
                .width(1 * ui.dp)
        )

        // hlavní obsah (nástroje + canvas + pravý panel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // levý panel nástrojů
            Column(
                modifier = Modifier
                    .width(50f * ui.dp)
                    .fillMaxHeight()
                    .background(colors.background)
            ) { LeftPanelToolbar(state) }
            Divider(
                color = colors.base,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1 * ui.dp)
            )

            // hlavní scéna
            val systemCursor = SettingsManager.current.systemCursor
            val mongeModeForCursor = state.mongeMode
            val cursorLabel = when (mongeModeForCursor) {
                DrawingModeMonge.PUDORYS -> "P"
                DrawingModeMonge.NARYS -> "N"
            }
            val desiredCursor: WebCursor? = remember(mongeModeForCursor, cursorLabel, systemCursor) {
                if (systemCursor || cursorLabel.isBlank()) null
                else createAxoCursor(cursorLabel)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Šířky obou polovin kreslicí plochy v pixelech: [0] = 2D
                // plátno, [1] = 3D náhled. Schválně to není Compose stav –
                // táhlo je jen čte při tažení a rekompozice celého UI při
                // každé změně velikosti okna by byla zbytečná.
                val splitAreaWidthPx = remember { intArrayOf(0, 0) }

                // Řádek přes celou výšku: vlevo canvas se svým horním barem,
                // vpravo panel „Aktuální výběr" (sahá až nahoru, přes úroveň baru).
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Poměr 2D plátna a 3D náhledu drží táhlo mezi nimi. Šířka
                    // se počítá ze součtu obou polovin, ne z celého řádku –
                    // pravý panel má pevnou šířku a do poměru nepatří.
                    val splitRatio = if (state.show3DPanel) {
                        state.gl3dSplitRatio.coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO)
                    } else {
                        0f
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f - splitRatio)
                            .fillMaxHeight()
                            .onSizeChanged { splitAreaWidthPx[0] = it.width }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24 * ui.dp)
                                .background(colors.background)
                                .padding(horizontal = 8 * ui.dp, vertical = 4 * ui.dp),
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

                            val coordText = when (state.mongeMode) {
                                DrawingModeMonge.NARYS -> "x: %.1f, z: %.1f".format(
                                    logicalCursor.x / 10f,
                                    -logicalCursor.y / 10f
                                )

                                DrawingModeMonge.PUDORYS -> "x: %.1f, y: %.1f".format(
                                    logicalCursor.x / 10f,
                                    logicalCursor.y / 10f
                                )
                            }
                            Text(
                                color = colors.text,
                                text = coordText,
                                modifier = Modifier.weight(1f),
                                fontSize = 12 * ui.sp
                            )
                        }
                        Divider(
                            color = colors.base,
                            modifier = Modifier
                                .fillMaxWidth()
                                .width(1 * ui.dp)
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.White)
                                .windowCursor(desiredCursor)
                                .focusRequester(state.focusRequester)
                                .focusable()
                                .pointerInput(state, canvasPointerHandler)
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

                    // 3D náhled scény. Desktop na tohle otevírá samostatné
                    // OpenGL okno; na webu je to druhá polovina kreslicí
                    // plochy s vlastním WebGL plátnem. Je schválně mimo Row
                    // s 2D plátnem, aby si nesahaly do vstupu.
                    if (state.show3DPanel) {
                        Box(
                            modifier = Modifier
                                .weight(splitRatio)
                                .fillMaxHeight()
                                .onSizeChanged { splitAreaWidthPx[1] = it.width }
                        ) {
                            Gl3DViewport(
                                state = state,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Táhlo leží na hranici mezi plátnem a 3D scénou.
                            // Kreslí se až po viewportu, aby se neztratilo
                            // v díře vyříznuté do Compose plátna.
                            VerticalResizeHandleOverlay(
                                colors = colors,
                                hitWidthDp = 12f * ui.dp,
                                lineWidthDp = 1f * ui.dp,
                                modifier = Modifier.align(Alignment.CenterStart),
                                onDragDeltaX = { dx ->
                                    val total = (splitAreaWidthPx[0] + splitAreaWidthPx[1]).toFloat()
                                    if (total > 1f) {
                                        state.gl3dSplitRatio =
                                            (state.gl3dSplitRatio - dx / total)
                                                .coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO)
                                    }
                                },
                            )
                        }
                    }

                    // Pravý panel (fixní šířka) + overlay resize handle
                    Box(
                        modifier = Modifier.width(sidebarWidth)
                            .fillMaxHeight()
                            .background(colors.background)
                    ) {
                        RightSidebar(state)

                        VerticalResizeHandleOverlay(
                            colors = colors,
                            hitWidthDp = 12f * ui.dp,
                            lineWidthDp = 1f * ui.dp,
                            modifier = Modifier.align(Alignment.CenterStart),
                            onDragDeltaX = { dx ->
                                state.rightSidebarW = (state.rightSidebarW - dx)
                                    .coerceIn(minSidebarPx, maxSidebarPx)
                            }
                        )
                    }
                }
            }

            Dialogs(state, requestGlobalFocus)
        }
    }
}

fun panDragToCanvasOffsetDelta(drag: Offset, flipX: Boolean, flipY: Boolean): Offset {
    val dx = if (flipX) -drag.x else drag.x
    val dy = if (flipY) -drag.y else drag.y
    return Offset(dx, dy)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VerticalResizeHandleOverlay(
    colors: MongeColorsState,
    modifier: Modifier = Modifier,
    hitWidthDp: Dp = 12.dp,
    lineWidthDp: Dp = 1.dp,
    onDragDeltaX: (Float) -> Unit
) {
    val base = colors.base

    // Že je čára táhlo, se pozná až podle kurzoru a zvýraznění pod myší.
    // Uprostřed je proto trvale vidět úchyt (tři tečky) a při najetí nebo
    // tažení čára ztmavne a rozšíří se.
    var hovered by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging

    val dragState = rememberDraggableState { delta ->
        onDragDeltaX(delta)
    }

    // Kurzor drží CSS na Compose plátně – `PointerIcon` v common API žádnou
    // variantu pro změnu velikosti nemá.
    DisposableEffect(active) {
        setCanvasCursor(if (active) "ew-resize" else "default")
        onDispose { setCanvasCursor("default") }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .offset(x = -hitWidthDp / 2)
            .width(hitWidthDp)
            .background(Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = { dragging = true },
                onDragStopped = { dragging = false },
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (active) lineWidthDp * 3 else lineWidthDp)
                .background(base.copy(alpha = if (active) 0.55f else 0.18f))
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(base.copy(alpha = if (active) 0.85f else 0.45f))
                )
            }
        }
    }
}

@Composable
fun ReturnFocusWhenClosed(
    showing: Boolean,
    requestGlobalFocus: () -> Unit
) {
    var wasShowing by remember { mutableStateOf(false) }
    LaunchedEffect(showing) {
        // pokud byl otevřený a teď se zavřel → vrať fokus
        if (wasShowing && !showing) requestGlobalFocus()
        wasShowing = showing
    }
}

/** Meze poměru 2D plátna a 3D náhledu – ani jedna polovina nesmí zmizet. */
private const val MIN_SPLIT_RATIO = 0.15f
private const val MAX_SPLIT_RATIO = 0.85f
