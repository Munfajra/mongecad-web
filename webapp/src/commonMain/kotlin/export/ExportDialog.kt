package export

import ui.components.MiniInputField
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.DropdownMenu
import dialogs.Alerts.LoadingDialog
import export.bitmapRenderer.RasterFormat
import export.bitmapRenderer.drawMongeSceneExport
import export.bitmapRenderer.generateRasterBytes
import export.bitmapRenderer.saveExportedImage
import export.pdfRenderer.PdfPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.LocalMongeColors
import model.MongeColorsState
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import monge.input.axo.createAxoRenderBasis
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.PaperFormat
import ui.mongeui.toolbar.SkikoButton
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

enum class ExportFormat { PNG, JPG }

private fun pdfPageFromPaperFormat(paper: PaperFormat): PdfPage = when (paper) {
    PaperFormat.A0 -> PdfPage.A0
    PaperFormat.A1 -> PdfPage.A1
    PaperFormat.A2 -> PdfPage.A2
    PaperFormat.A3 -> PdfPage.A3
    PaperFormat.A4 -> PdfPage.A4
    PaperFormat.A5 -> PdfPage.A5
}
private fun exportAxoOriginLogical(state: MongeState): Offset? {
    val canvasWidth = when {
        state.canvasWidth > 0f -> state.canvasWidth
        state.canvasSizePx.width > 0 -> state.canvasSizePx.width.toFloat()
        else -> return state.basis?.origin
    }
    val canvasHeight = when {
        state.canvasHeight > 0f -> state.canvasHeight
        state.canvasSizePx.height > 0 -> state.canvasSizePx.height.toFloat()
        else -> return state.basis?.origin
    }

    return createAxoRenderBasis(
        model = state.activeAxoModel,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight
    )?.origin ?: state.basis?.origin
}
private fun exportInitialLogicalCenter(state: MongeState): Offset {
    if (state.projectionMode == ProjectionMode.AXO) {
        val origin = exportAxoOriginLogical(state) ?: Offset.Zero
        return if (state.paperAnchorPinned) origin + state.paperAnchorFromOrigin else origin
    }

    return if (state.paperAnchorPinned) state.paperAnchorLogical else Offset.Zero
}
private fun zoomTToScale(t: Float, oneToOne: Float): Float {
    val minS = oneToOne * 0.10f
    val maxS = oneToOne * 8.00f
    val tt = t.coerceIn(0f, 1f).toDouble()
    val ratio = (maxS / minS).toDouble()
    return (minS.toDouble() * ratio.pow(tt)).toFloat()
}
private fun scaleToZoomT(scale: Float, oneToOne: Float): Float {
    val minS = oneToOne * 0.10f
    val maxS = oneToOne * 8.00f
    val s = scale.coerceIn(minS, maxS).toDouble()
    val minD = minS.toDouble()
    val maxD = maxS.toDouble()
    return (ln(s / minD) / ln(maxD / minD)).toFloat().coerceIn(0f, 1f)
}
private fun applyZoomAtPivot(
    oldScale: Float,
    newScale: Float,
    pivotScreen: Offset,
    contentOrigin: Offset,
    pageOffset: Offset
): Offset {
    val visualOffset = contentOrigin + pageOffset
    val logicalAtPivot = (pivotScreen - visualOffset) / oldScale
    return pivotScreen - contentOrigin - logicalAtPivot * newScale
}
@Composable
fun ExportDialogPopup(
    show: Boolean,
    state: MongeState,
    onDismiss: () -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormat.PNG) }
    if (!show) return
    val colors = LocalMongeColors.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequester, show) {
        if (show) {
            // počkáme, než se připojí do stromu
            delay(50.milliseconds)
            focusRequester.requestFocus()
        }
    }
    val ui = SettingsManager.current.UIscale/75f
    var userTouched by remember(show) { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var pageOffset by remember { mutableStateOf(Offset.Zero) }
    var lastContentOrigin by remember { mutableStateOf(Offset.Zero) }
    var page by remember(show) {
        mutableStateOf(PdfPage.A4)
    }
    var marginMm by remember { mutableStateOf(10f) }
    var portrait by remember { mutableStateOf(true) } // ⬅ přepínač orientace
    var showHelperConstructions by mutableStateOf(true)
    var pendingLogicalCenter by remember { mutableStateOf<Offset?>(null) }
    var showObjectLabels      by mutableStateOf(true)
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val DIALOG_WIDTH = 1080f*ui.dp
    val DIALOG_MIN_HEIGHT = 700f*ui.dp
    val PREVIEW_HEIGHT = 800f*ui.dp
    val RIGHT_PANEL_WIDTH = 400f*ui.dp
    var initApplied by remember(show) { mutableStateOf(false) }
    val density = LocalDensity.current.density
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect

        // orientace si klidně ber z aktuálního stavu / paperPreview nastavení
        portrait = state.paperPortrait

        // ✅ ukotvení jen když je papír "přichycený"
        pendingLogicalCenter = if (state.paperAnchorPinned) exportInitialLogicalCenter(state) else null
        userTouched = state.paperAnchorPinned  // když je pinned, nechceme auto-center na (0,0)

        // Tisk web nemá – tiskárny se nezjišťují.
    }
    // LaunchedEffect(selectedPrinter) – zjišťování formátů tiskárny; web nemá tisk.
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize
            ) = IntOffset(
                (windowSize.width - popupContentSize.width) / 2,
                (windowSize.height - popupContentSize.height) / 2
            )
        },
        onDismissRequest = {},
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // scrim
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color(0xAA000000))
            )

// dialog shell
            Box(
                Modifier
                    .padding(28f*ui.dp)
                    .shadow(18.dp, RoundedCornerShape(14f*ui.dp))
                    .background(colors.background.copy(alpha = 0.98f), RoundedCornerShape(14f*ui.dp))
                    .border(1.dp, colors.base.copy(alpha = 0.30f), RoundedCornerShape(14f*ui.dp))
                    .width(DIALOG_WIDTH)
                    .heightIn(min = DIALOG_MIN_HEIGHT)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.Escape) { onDismiss(); true } else false
                    }
            ) {
                Column(Modifier.padding(16f*ui.dp), verticalArrangement = Arrangement.spacedBy(12f*ui.dp)) {
                    ExportHeader(colors, onDismiss)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12f*ui.dp)) {
                        var previewContentPx by remember { mutableStateOf(Size.Zero) }
                        var previewCanvasSize by remember { mutableStateOf(IntSize.Zero) }
                        var contentOriginPreview by remember { mutableStateOf(Offset.Zero) }
                        var pivotCenter by remember { mutableStateOf(Offset.Zero) }
                        var oneToOneScale by remember { mutableStateOf(1f) }

                        // LEVÁ STRANA — flexibilní šířka, pevná výška
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)                // ⟵ místo fillMaxWidth()
                                .height(PREVIEW_HEIGHT)
                        ) {

                            val paper = state.paperFormat // rozvržení vždy dle formátu výkresu (paperview)
                            val wMm = if (portrait) paper.wMm else paper.hMm
                            val hMm = if (portrait) paper.hMm else paper.wMm

                            val aspect = wMm / hMm

                            val availW = maxWidth
                            val availH = maxHeight

                            val wIfFitByHeight = availH * aspect
                            val (targetW, targetH) =
                                if (wIfFitByHeight <= availW) wIfFitByHeight to availH
                                else availW to (availW / aspect)

                            val pageW by animateDpAsState(targetW, label = "pageW")
                            val pageH by animateDpAsState(targetH, label = "pageH")

// až po onSizeChanged:
                            val pxPerMmNow =
                                if (previewCanvasSize.width == 0 || previewCanvasSize.height == 0) 0f
                                else min(
                                    previewCanvasSize.width.toFloat() / wMm,
                                    previewCanvasSize.height.toFloat() / hMm
                                )
                            val marginPxPreviewNow = marginMm * pxPerMmNow
                            val unitsPerMm = 1f
                            val printScale1to1 = if (pxPerMmNow == 0f) 1f else (pxPerMmNow / unitsPerMm)



                            SideEffect {
                                contentOriginPreview = Offset(marginPxPreviewNow, marginPxPreviewNow)
                                pivotCenter = Offset(previewCanvasSize.width / 2f, previewCanvasSize.height / 2f)

                                oneToOneScale = printScale1to1
                                if (!userTouched) scale = printScale1to1
                            }
                            LaunchedEffect(show, previewCanvasSize, pxPerMmNow, printScale1to1, pendingLogicalCenter) {
                                if (!show) return@LaunchedEffect
                                if (initApplied) return@LaunchedEffect
                                if (previewCanvasSize.width == 0 || previewCanvasSize.height == 0) return@LaunchedEffect
                                if (pxPerMmNow == 0f) return@LaunchedEffect

                                val contentOrigin = Offset(marginMm * pxPerMmNow, marginMm * pxPerMmNow)
                                val pivot = Offset(previewCanvasSize.width / 2f, previewCanvasSize.height / 2f)

                                // 1) default: scale = 1:1
                                scale = printScale1to1

                                // 2) offset:
                                // - když je pinned, vycentruj na pinned anchor
                                // - když není pinned, dej default "0,0 do středu"
                                val logicalCenter = pendingLogicalCenter ?: exportInitialLogicalCenter(state)
                                pageOffset = pivot - contentOrigin - logicalCenter * scale

                                // když tohle proběhne, nechceme, aby nám to jiné efekty znovu přepsaly
                                initApplied = true
                                pendingLogicalCenter = null
                                userTouched = true // zabrání dalším "auto" resetům
                            }


                            // vycentrovaný Canvas, bez žádného bílého boxu okolo
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Canvas(
                                    modifier = Modifier
                                        .size(pageW, pageH)
                                        .shadow(8f*ui.dp, RoundedCornerShape(6f*ui.dp))
                                        .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(6f*ui.dp))
                                        .clip(RoundedCornerShape(6f*ui.dp))
                                        .clipToBounds()
                                        .onSizeChanged { previewCanvasSize = it }
                                        .pointerInput(state.paperAnchorPinned) {
                                            if (state.paperAnchorPinned) return@pointerInput
                                            awaitPointerEventScope {
                                                var lastPos: Offset? = null

                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull() ?: continue

                                                    val rightDown = change.pressed && event.buttons.isSecondaryPressed

                                                    if (rightDown) {
                                                        val prev = lastPos
                                                        val drag = if (prev != null) (change.position - prev) else Offset.Zero
                                                        lastPos = change.position

                                                        if (drag.getDistance() > 0f) {
                                                            val fixedX =
                                                                if (state.xAxisDirection == XAxisDirection.POSITIVE_LEFT) -drag.x else drag.x
                                                            val fixedY =
                                                                if (state.yAxisDirectionPlane== YAxisDirectionPlane.POSITIVE_UP) -drag.y else drag.y

                                                            pageOffset += Offset(fixedX, fixedY)
                                                        }

                                                        change.consume()
                                                    } else {
                                                        lastPos = null
                                                    }
                                                }
                                            }
                                        }

                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val e = awaitPointerEvent()
                                                    val ch = e.changes.firstOrNull() ?: continue
                                                    val s = ch.scrollDelta.y
                                                    if (s != 0f) {
                                                        if (state.paperAnchorPinned) {
                                                            ch.consume()
                                                            continue
                                                        }
                                                        userTouched = true
                                                        val z = 1.3f
                                                        val old = scale
                                                        val next = (scale * if (s > 0f) 1 / z else z).coerceIn(0.1f, 50f)

                                                        val pxPerMm = pxPerMmNow
                                                        val contentOrigin = Offset(marginMm * pxPerMm, marginMm * pxPerMm)
                                                        val visualOffset = contentOrigin + pageOffset
                                                        // ⬇︎ flip vstupu při PLANE
                                                        val screenForLogic = if (state.projectionMode == ProjectionMode.PLANE) {
                                                            Offset(ch.position.x, size.height - ch.position.y)
                                                        } else {
                                                            ch.position
                                                        }

                                                        // ohnisko v "logických" souřadnicích (před zoomem)
                                                        val logical = (screenForLogic - visualOffset) / old

                                                        // nový pageOffset tak, aby kurzor byl samodružný
                                                        pageOffset = screenForLogic - contentOrigin - logical * next
                                                        scale = next
                                                    }
                                                }
                                            }
                                        }

                                ) {
                                    val pxPerMm = pxPerMmNow
                                    val marginPx = marginMm * pxPerMm
                                    val contentOrigin = Offset(marginPx, marginPx)

                                    if (contentOrigin != lastContentOrigin) {
                                        pageOffset += (lastContentOrigin - contentOrigin)
                                        lastContentOrigin = contentOrigin
                                    }

                                    val visualOffset = contentOrigin + pageOffset

                                    val drawWpx = size.width  - 2f * marginPx
                                    val drawHpx = size.height - 2f * marginPx
                                    previewContentPx = Size(drawWpx.coerceAtLeast(1f), drawHpx.coerceAtLeast(1f))

                                    drawMongeSceneExport(
                                        state = state,
                                        scale = scale,
                                        offset = visualOffset,
                                        background = Color.White,
                                        pxFactor = 1f,
                                        strokePxFactor = if (state.projectionMode == ProjectionMode.AXO) {
                                            1f
                                        } else {
                                            if (oneToOneScale > 1e-6f) scale / oneToOneScale else 1f
                                        },
                                        drawLabels = showObjectLabels,
                                        drawHelpers = showHelperConstructions,
                                        x12RightEdgePx = size.width - marginPx
                                        // nebo to přejmenuj, viz níž
                                    )

                                    drawMarginsOverlay(pagePx = size, marginPx = marginPx, tint = Color(0x33FF0000))
                                }
                            }
                        }
                        var dpiText by remember { mutableStateOf("300") } // výchozí DPI jako text
                        val dpi = dpiText.toIntOrNull()?.coerceAtLeast(72) ?: 300 // minimálně 72
                        // --------- PRAVÁ ČÁST: jen orientace + okraj ----------
                        val rightScroll = rememberScrollState()

                        Column(
                            modifier = Modifier
                                .width(RIGHT_PANEL_WIDTH)
                                .height(PREVIEW_HEIGHT) // může zůstat, ale bude scroll
                                .verticalScroll(rightScroll)
                                .padding(bottom = 12f*ui.dp), // ať se to nedotýká footeru
                            verticalArrangement = Arrangement.spacedBy(12f*ui.dp)
                        ) {

                            // Sekce Tiskárna – web netiskne.

                            SectionCard("Stránka", colors) {
                                TogglePillRow(
                                    options = listOf("Na výšku", "Na šířku"),
                                    selectedIndex = if (portrait) 0 else 1,
                                    colors = colors,
                                    enabled = !state.paperAnchorPinned
                                ) { portrait = (it == 0) }
                                if (state.paperAnchorPinned) {
                                    Text(
                                        "Při ukotveném náhledu nelze měnit orientaci stránky.",
                                        color = colors.text.copy(alpha = 0.62f),
                                        fontSize = 11f * ui.sp
                                    )
                                }
                            }

                            SectionCard("Rozvržení", colors) {
                                SliderRow("Okraje", "${marginMm.toInt()} mm", colors) {
                                    Slider(
                                        value = marginMm,
                                        onValueChange = { marginMm = it },
                                        valueRange = 0f..50f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.selected,
                                            activeTrackColor = colors.selected,
                                            inactiveTrackColor = colors.base.copy(alpha = 0.20f)
                                        )
                                    )
                                }

                            }

                            SectionCard("Náhled", colors) {

                                val canZoom = oneToOneScale > 0f && previewCanvasSize.width > 0
                                val zoomT = if (canZoom) scaleToZoomT(scale, oneToOneScale) else 0f
                                val zoomPercent = if (canZoom) ((scale / oneToOneScale) * 100f).roundToInt() else 0

                                SliderRow("Zoom", "$zoomPercent %", colors) {
                                    Slider(
                                        value = zoomT,
                                        enabled = !state.paperAnchorPinned,
                                        onValueChange = { t ->
                                            if (!canZoom) return@Slider
                                            val newScale = zoomTToScale(t, oneToOneScale)

                                            pageOffset = applyZoomAtPivot(
                                                oldScale = scale,
                                                newScale = newScale,
                                                pivotScreen = pivotCenter,
                                                contentOrigin = contentOriginPreview,
                                                pageOffset = pageOffset
                                            )
                                            scale = newScale
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.selected,
                                            activeTrackColor = colors.selected,
                                            inactiveTrackColor = colors.base.copy(alpha = 0.20f)
                                        )
                                    )
                                }
                                if(state.paperAnchorPinned){
                                    Column(verticalArrangement = Arrangement.spacedBy(8f * ui.dp)) {
                                        Text(
                                            "Ukotvený náhled nejde posouvat ani zoomovat.",
                                            color = colors.text.copy(alpha = 0.68f),
                                            fontSize = 12f * ui.sp
                                        )
                                        ExportActionButton(
                                            text = "Uvolnit náhled",
                                            colors = colors,
                                            ui = ui,
                                            kind = ExportButtonKind.Secondary,
                                            onClick = { state.paperAnchorPinned = false }
                                        )
                                    }
                                }
                                if (!state.paperAnchorPinned){
                                    Row(horizontalArrangement = Arrangement.spacedBy(8f*ui.dp)) {
                                        ExportActionButton(
                                            text = "1:1",
                                            colors = colors,
                                            ui = ui,
                                            kind = ExportButtonKind.Secondary,
                                            onClick = {
                                                if (canZoom) {
                                                    val newScale = oneToOneScale
                                                    pageOffset = applyZoomAtPivot(
                                                        scale,
                                                        newScale,
                                                        pivotCenter,
                                                        contentOriginPreview,
                                                        pageOffset
                                                    )
                                                    scale = newScale
                                                }
                                            }
                                        )

                                        ExportActionButton(
                                            text = "Reset",
                                            colors = colors,
                                            ui = ui,
                                            kind = ExportButtonKind.Secondary,
                                            onClick = {
                                                if (canZoom) {
                                                    scale = oneToOneScale
                                                    pageOffset = pivotCenter - contentOriginPreview - exportInitialLogicalCenter(state) * scale
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            SectionCard("Zobrazit", colors) {
                                if (state.projectionMode != ProjectionMode.PLANE) {
                                    CheckRow(
                                        "Pomocné konstrukce",
                                        showHelperConstructions,
                                        colors
                                    ) { showHelperConstructions = it }
                                }
                                CheckRow("Názvy objektů", showObjectLabels, colors) { showObjectLabels = it }
                            }

                            SectionCard("Výstup", colors) {
                                // Volba Bitmapa/PDF odpadá – web PDF negeneruje.
                                run {
                                    TogglePillRow(
                                        options = listOf("PNG", "JPG"),
                                        selectedIndex = if (format == ExportFormat.PNG) 0 else 1,
                                        colors = colors
                                    ) { selected ->
                                        format = if (selected == 0) ExportFormat.PNG else ExportFormat.JPG
                                    }

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10f * ui.dp)
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Rozlišení", color = colors.text, fontSize = 13f * ui.sp)
                                        }
                                        MiniInputField(
                                            ui = ui,
                                            value = dpiText,
                                            onValueChange = { dpiText = it },
                                            placeholder = "300",
                                            width = 82.dp
                                        )
                                        Text("DPI", color = colors.text.copy(alpha = 0.70f), fontSize = 12f * ui.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            ExportFooter(
                                colors = colors,
                                exportLabel = when (format) {
                                    ExportFormat.PNG -> "Exportovat PNG"
                                    ExportFormat.JPG -> "Exportovat JPG"
                                },
                                exportEnabled = !isExporting,
                                onClose = onDismiss,
                                onExport = {
                                    scope.launch {
                                        isExporting = true
                                        try {
                                            val strokeScale =
                                                if (oneToOneScale > 1e-6f) scale / oneToOneScale else 1f
                                            // Bere přesně ty hodnoty, které řídí náhled výše
                                            // (page/portrait/scale/pageOffset/previewContentPx),
                                            // takže výsledek odpovídá tomu, co uživatel vidí.
                                            val fmt = when (format) {
                                                ExportFormat.PNG -> RasterFormat.PNG
                                                ExportFormat.JPG -> RasterFormat.JPG
                                            }
                                            val bytes = generateRasterBytes(
                                                state, page, dpi, marginMm, scale, pageOffset, portrait,
                                                previewContentPx, showHelperConstructions, showObjectLabels,
                                                format = fmt,
                                                logicalStrokeScale = strokeScale
                                            )
                                            val ext = if (fmt == RasterFormat.PNG) "png" else "jpg"
                                            val base = state.displayName.ifBlank { "Vykres" }
                                            saveExportedImage(bytes, "$base.$ext", fmt)
                                            onDismiss()
                                        } finally {
                                            isExporting = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                    }
                }
            }
        }

        if (isExporting) {
            LoadingDialog(
                title = when (format) {
                    ExportFormat.PNG -> "Generování PNG"
                    ExportFormat.JPG -> "Generování JPG"
                }
            )
        }
    }
@Composable
private fun ExportDropdownRow(
    label: String,
    valueText: String,
    entries: List<String>,
    selectedIndex: Int,
    colors: MongeColorsState,
    ui: Float,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10f * ui.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.text, fontSize = 13f * ui.sp)
        }
        Box {
            SkikoButton(
                onClick = { expanded = true },
                width = 170f * ui.dp,
                height = 32f * ui.dp
            ) {
                Text(
                    valueText,
                    fontSize = 12f * ui.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(colors.background)
                    .width(220f * ui.dp)
            ) {
                entries.forEachIndexed { i, entry ->
                    SkikoButton(
                        onClick = {
                            onSelect(i)
                            expanded = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        height = 32f * ui.dp,
                        isSelected = i == selectedIndex
                    ) {
                        Text(
                            entry,
                            fontSize = 12f * ui.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
