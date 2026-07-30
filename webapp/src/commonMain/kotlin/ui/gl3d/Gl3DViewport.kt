package ui.gl3d

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.mongeui.toolbar.onHoverEnter
import gl3d.api.GlSurface
import gl3d.api.createGlSurface
import gl3d.camera.Camera3D
import gl3d.camera.advanceCameraSnap
import gl3d.camera.cancelCameraSnap
import gl3d.camera.resetCamera3D
import gl3d.export.exportScene3DImage
import draw.mongescreen.labels.RichLabelPart
import draw.mongescreen.labels.drawRichLabel
import draw.mongescreen.labels.measureRichLabelMetrics
import gl3d.scene.Scene3DLabel
import gl3d.scene.SceneRenderer
import model.CameraSnap
import monge.input.lines.isOpenGlAxisVisible
import monge.input.lines.toggleOpenGlAxisVisibility
import model.LocalMongeColors
import model.ProjectionMode
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import state.MongeState

/**
 * 3D náhled scény – webová obdoba samostatného OpenGL okna z desktopu.
 *
 * Vlastní kreslení jde do `<canvas>` mimo Compose (viz `gl3d.api.GlSurface`),
 * který se drží přesně na obdélníku tohoto composable. Tady zůstává jen
 * layout, vstup kamery a řízení překreslování.
 *
 * Překresluje se **na vyžádání**: když se nehýbe kamera ani scéna, žádný
 * snímek se nekreslí. Desktop jede na `glfwSwapInterval(1)` napořád, což by
 * v prohlížeči zbytečně žralo baterii.
 */
@Composable
fun Gl3DViewport(
    state: MongeState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMongeColors.current
    val density = LocalDensity.current.density

    val surface: GlSurface? = remember { createGlSurface() }

    if (surface == null) {
        Gl3DUnavailable(modifier)
        return
    }

    val camera = remember(surface) { Camera3D() }
    val renderer = remember(surface) { SceneRenderer(surface.gl) }
    // Záměrně to není Compose stav: orbit by jinak při každém pohybu myši
    // vyvolal rekompozici celého viewportu, i když se překresluje jen plátno.
    val frames = remember(surface) { FrameTicker() }

    DisposableEffect(surface) {
        surface.setVisible(true)
        onDispose {
            renderer.dispose()
            surface.dispose()
        }
    }

    // Barva pozadí scény jde za motivem aplikace. Smyčka snímků se kvůli
    // přepnutí světlý/tmavý nerestartuje, jen si přečte novou hodnotu.
    val background = rememberUpdatedState(colors.background)

    // Popisky os. Renderer dodá jen polohy, text kreslí Compose nad plátnem.
    var labels by remember(surface) { mutableStateOf<List<Scene3DLabel>>(emptyList()) }

    // Změna výkresu, motivu nebo vynucené překreslení z 2D části aplikace.
    LaunchedEffect(state.sceneVersion, state.triggerRedraw, colors.background) {
        frames.request()
    }

    // Výběr v pravém panelu se má hned zvýraznit i ve 3D, ale `sceneVersion`
    // se při něm nezvedá – scéna se nemění, mění se jen co je v ní vybrané.
    // `snapshotFlow` navíc sleduje výběr bez rekompozice celého viewportu.
    LaunchedEffect(surface) {
        snapshotFlow { selectionSignature(state) }.collect { frames.request() }
    }

    // Požadavek na export snímku. Vyřídí ho až smyčka snímků – číst pixely jde
    // jen bezprostředně po vykreslení, mimo něj je cíl už překlopený na plátno.
    val exportRequested = remember(surface) { booleanArrayOf(false) }

    LaunchedEffect(surface) {
        while (true) {
            val frameTimeNanos = withFrameNanos { it }

            if (surface.isContextLost()) continue
            if (surface.consumeContextRestored()) {
                renderer.invalidateResources()
                frames.forceNextFrame()
            }
            // Přelet kamery na zvolený pohled. Dokud běží, říká si o další
            // snímek – u kreslení na vyžádání by se jinak zastavil hned na
            // začátku.
            if (advanceCameraSnap(state, camera, frameTimeNanos)) frames.request()
            if (!renderer.isReady && !renderer.initialize()) continue

            val exporting = exportRequested[0]
            if (!frames.consumeIfPending() && !exporting) continue
            exportRequested[0] = false

            val width = surface.pixelWidth
            val height = surface.pixelHeight
            var frameLabels: List<Scene3DLabel> = emptyList()
            val pixels = renderer.render(
                state = state,
                camera = camera,
                width = width,
                height = height,
                pixelScale = density,
                background = background.value,
                onLabels = {
                    frameLabels = it
                    labels = it
                },
                captureRgba = exporting,
            )

            if (exporting && pixels != null) {
                exportScene3DImage(
                    rgba = pixels,
                    width = width,
                    height = height,
                    labels = frameLabels,
                    labelFontPx = SettingsManager.current.activeLabelSizePx * LABEL_FONT_FACTOR,
                )
            }
        }
    }

    Box(
        modifier = modifier
            // Průhledná díra do Compose plátna. GL plátno leží pod ním
            // (z-index -1 v shadow rootu), takže se skrz díru ukáže 3D scéna
            // a všechno, co Compose kreslí *potom* – dialogy, menu, tooltipy –
            // zůstane korektně nad ní. Bez tohohle by se buď 3D schovávalo
            // pod UI, nebo by naopak překrývalo otevřené dialogy.
            .drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val size = coordinates.size
                // Compose měří v device pixelech, CSS pozice je v logických –
                // proto dělení hustotou. Backing store si plátno škáluje samo.
                surface.setRect(
                    x = position.x / density,
                    y = position.y / density,
                    width = size.width / density,
                    height = size.height / density,
                )
                frames.request()
            }
            .cameraInput(camera, surface) {
                // Ruční pohyb kamerou ruší rozjetý přelet, stejně jako na desktopu.
                cancelCameraSnap(state)
                frames.request()
            },
    ) {
        SceneLabels(labels = labels, haloColor = colors.background)
        Gl3DOverlayControls(
            state = state,
            onSnap = { snap ->
                state.pendingCameraSnap = snap
                frames.request()
            },
            onReset = {
                resetCamera3D(state, camera)
                frames.request()
            },
            onExport = { exportRequested[0] = true },
            onRedraw = { frames.request() },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )
    }
}

/**
 * Ovládání nad 3D plátnem: pohledy kamery, export a přepínače viditelnosti.
 *
 * Desktop na to má rozbalovací menu v liště nad náhledem
 * (`ui/components/EmbeddedPreviewPane.kt`); tady jsou to ploché „čipy" přímo
 * nad scénou, protože panel žádnou vlastní lištu nemá. Horní řádek jsou akce,
 * spodní přepínače – ty se poznají podle toho, že vypnuté zešednou.
 *
 * Bokorys a AXO se nenabízejí, na desktopu jsou vázané na AXO režim, který
 * webová verze nemá.
 */
@Composable
private fun Gl3DOverlayControls(
    state: MongeState,
    onSnap: (CameraSnap) -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onRedraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.projectionMode != ProjectionMode.KOTO) {
                Gl3DChip("Nárys") { onSnap(CameraSnap.NARYS_FRONT) }
            }
            Gl3DChip("Půdorys") { onSnap(CameraSnap.PUDORYS_TOP) }
            Gl3DChip("Reset") { onReset() }
            Gl3DChip("Uložit PNG") { onExport() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Gl3DChip("Půdorysna", active = state.showReferencePlanesP) {
                state.showReferencePlanesP = !state.showReferencePlanesP
                onRedraw()
            }
            if (state.projectionMode != ProjectionMode.KOTO) {
                Gl3DChip("Nárysna", active = state.showReferencePlanesN) {
                    state.showReferencePlanesN = !state.showReferencePlanesN
                    onRedraw()
                }
            }
            Gl3DChip("Stopy", active = state.showTraces) {
                state.showTraces = !state.showTraces
                onRedraw()
            }
            Gl3DChip("Osy", active = isOpenGlAxisVisible(state)) {
                toggleOpenGlAxisVisibility(state)
                onRedraw()
            }
        }
    }
}

/**
 * Ploché tlačítko nad scénou. `active = null` je akce, `true`/`false`
 * přepínač – vypnutý se jen ztlumí, aby řádek nepodskakoval.
 *
 * Barvy drží stejný recept jako [ui.mongeui.toolbar.SkikoButton]: zapnutý stav
 * není plná výplň akcentem (ta nad scénou svítila a vytrhávala pozornost), ale
 * jen tlumený podklad, akcentní obrys a text. Obrys má i vypnutý stav, jinak by
 * akce jako „Reset" nebo „Uložit PNG" nevypadaly jako tlačítka.
 */
@Composable
private fun Gl3DChip(label: String, active: Boolean? = null, onClick: () -> Unit) {
    val colors = LocalMongeColors.current
    val dark = colors.isDark
    val enabled = active != false
    val shape = RoundedCornerShape(4.dp)

    var hovered by remember { mutableStateOf(false) }

    // Podklad je vždycky trochu neprůhledný, aby text nesplynul se scénou pod ním.
    val surface = colors.background.copy(alpha = if (dark) 0.72f else 0.78f)
    val tint = when {
        active == true -> colors.selected.copy(alpha = if (dark) 0.22f else 0.12f)
        hovered -> colors.hover.copy(alpha = if (dark) 0.16f else 0.08f)
        else -> colors.base.copy(alpha = if (dark) 0.07f else 0.035f)
    }
    val borderColor by animateColorAsState(
        when {
            active == true -> colors.selected.copy(alpha = if (dark) 0.70f else 0.52f)
            !enabled -> colors.disabled.copy(alpha = if (dark) 0.10f else 0.08f)
            hovered -> colors.base.copy(alpha = if (dark) 0.28f else 0.16f)
            else -> colors.base.copy(alpha = if (dark) 0.14f else 0.08f)
        },
        label = "gl3d_chip_border",
    )
    val contentColor = when {
        active == true -> colors.selected
        !enabled -> colors.text.copy(alpha = 0.45f)
        else -> colors.text
    }

    Text(
        text = label,
        color = contentColor,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(shape)
            .background(surface, shape)
            .background(tint, shape)
            .border(1.dp, borderColor, shape)
            .onHoverEnter(onEnter = { hovered = true }, onExit = { hovered = false })
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Popisky nad 3D plátnem – osy, body, přímky a stopy rovin.
 *
 * Desktop je kreslí NanoVG přímo do GL okna; web žádné vykreslování textu v GL
 * nemá, takže renderer dodá jen polohy v pixelech a sazbu obstará Compose.
 * Jde přitom **týmž Skia kódem jako popisky na 2D plátně**
 * (`draw/mongescreen/labels/`), takže sedí rodina písma, kurzíva u názvů
 * i sazba horních indexů; obyčejný `Text` by kreslil bezpatkovým UI fontem.
 *
 * Kolem textu je obrys v barvě pozadí – bez něj popisek splyne s čarami, které
 * pod ním prochází (desktop na to má bílé halo).
 */
@Composable
private fun SceneLabels(labels: List<Scene3DLabel>, haloColor: Color) {
    if (labels.isEmpty()) return
    val fontPx = SettingsManager.current.activeLabelSizePx * LABEL_FONT_FACTOR

    // Popisky se sázejí přes `nativeCanvas`, který o rozvržení Composu neví –
    // bez ořezu by text objektu mimo výřez přetekl do 2D plátna a do UI.
    Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
        for (label in labels) {
            val parts = listOf(RichLabelPart(label.text, label.superscript))
            val metrics = measureRichLabelMetrics(parts, fontPx)
            // Popisky os sedí kotvou na střed, ostatní vlevo nahoře od kotvy –
            // stejné rozdělení jako na desktopu.
            val anchor = if (label.centered) {
                // Popisek osy má zůstat vidět, i když hrot vyjede ven – desktop
                // ho drží u kraje stejně (`anchor.x.coerceIn(...)`).
                Offset(
                    (label.x - metrics.width / 2f)
                        .coerceIn(0f, (size.width - metrics.width).coerceAtLeast(0f)),
                    (label.y - (metrics.top + metrics.bottom) / 2f)
                        .coerceIn(-metrics.top, size.height - metrics.bottom),
                )
            } else {
                Offset(label.x, label.y - metrics.top)
            }

            for (halo in HALO_OFFSETS) {
                drawRichLabel(
                    parts = parts,
                    anchor = Offset(anchor.x + halo.x, anchor.y + halo.y),
                    color = haloColor,
                    baseFontPx = fontPx,
                )
            }
            drawRichLabel(parts = parts, anchor = anchor, color = label.color, baseFontPx = fontPx)
        }
    }
}

/**
 * Poměr velikosti popisku ke stavené hodnotě. Stejný jako u 2D popisků
 * (`activeLabelSizePx * 0.7f`), jen bez násobení zoomem plátna – 3D náhled
 * žádný zoom plátna nemá.
 */
private const val LABEL_FONT_FACTOR = 0.7f

/** Posuny obrysu kolem textu; levnější než skutečný stroke a stačí to. */
private val HALO_OFFSETS = listOf(
    Offset(-1.5f, 0f), Offset(1.5f, 0f), Offset(0f, -1.5f), Offset(0f, 1.5f),
)

/**
 * Otisk aktuálního výběru. Slouží jen k tomu, aby si 3D náhled řekl o nový
 * snímek, když se výběr změní – vazba na `ObjectList` a `SelectionInfo` jinak
 * žádný kód nepotřebuje, protože obě strany čtou a píší tentýž `MongeState`
 * (na desktopu je to stejné, jen je ten seznam v ImGui uvnitř GL okna).
 */
private fun selectionSignature(state: MongeState): String = buildString {
    state.selectedPoints3D.forEach { append(it.id).append(',') }
    append('|')
    state.selectedLines3D.forEach { append(it.id).append(',') }
    append('|')
    state.selectedSegments3D.forEach { append(it.id).append(',') }
    append('|')
    state.selectedPlanes.forEach { append(it.id).append(',') }
    append('|')
    state.selectedPolygons.forEach { append(it.id).append(',') }
    append('|')
    state.selectedConicsPudorys.forEach { append(it.id).append(',') }
    state.selectedConicsNarys.forEach { append(it.id).append(',') }
    state.selectedConicsBokorys.forEach { append(it.id).append(',') }
    state.selectedConicsAxo.forEach { append(it.id).append(',') }
    append('|')
    state.selectedCone.forEach { append(it.id).append(',') }
    state.selectedCylinder.forEach { append(it.id).append(',') }
    state.selectedSpheres3D.forEach { append(it.id).append(',') }
    append('|')
    append(state.selectedSolidOfRevolutionId).append('|')
    append(state.selectedRuledSurfaceId).append('|')
    append(state.selectedCurve3DId).append('|')
    append(state.selectedIntersectionGroupId)
}

/** Jednoduchý „je potřeba nový snímek?“ příznak mimo Compose stav. */
private class FrameTicker {
    private var pending = true

    fun request() {
        pending = true
    }

    fun forceNextFrame() {
        pending = true
    }

    fun consumeIfPending(): Boolean {
        if (!pending) return false
        pending = false
        return true
    }
}

/**
 * Ovládání kamery.
 *
 * Myš má stejné rozdělení jako desktopové okno (`installInputCallbacks`
 * v `opengl/Camera.kt`): levé tlačítko orbit, pravé posun, kolečko zoom.
 * Dotyk desktop neřeší vůbec, tady navíc platí jeden prst = orbit,
 * dva prsty = posun a zároveň pinch zoom.
 *
 * GL plátno má `pointer-events: none`, takže se ukazatel dostane až sem
 * a nemusíme psát žádné DOM listenery.
 */
private fun Modifier.cameraInput(
    camera: Camera3D,
    surface: GlSurface,
    onCameraChanged: () -> Unit,
): Modifier = this.pointerInput(camera, surface) {
    var lastMouse: Offset? = null
    var orbiting = false
    var panning = false

    // Stav gesta prsty. `null` znamená „gesto právě začíná“ – první událost
    // jen zapíše výchozí hodnoty, aby se kamera neskočila o celý dosavadní
    // posun při přechodu mezi jedním a dvěma prsty.
    var lastTouchAnchor: Offset? = null
    var lastTouchSpread: Float? = null

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()

            if (event.type == PointerEventType.Scroll) {
                val scroll = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (scroll != 0f) {
                    // Compose hlásí kladné `y` při odjetí od uživatele,
                    // zoom očekává kladné pro přiblížení – proto znaménko.
                    camera.zoom(-scroll)
                    onCameraChanged()
                }
                event.changes.forEach { it.consume() }
                continue
            }

            val touches = event.changes.filter { it.pressed && it.type == PointerType.Touch }

            when {
                touches.size >= 2 -> {
                    val first = touches[0].position
                    val second = touches[1].position
                    val anchor = (first + second) / 2f
                    val spread = (first - second).getDistance()

                    val previousAnchor = lastTouchAnchor
                    val previousSpread = lastTouchSpread
                    if (previousAnchor != null && previousSpread != null && previousSpread > 1f) {
                        camera.pan(
                            anchor.x - previousAnchor.x,
                            anchor.y - previousAnchor.y,
                            surface.pixelWidth,
                            surface.pixelHeight,
                        )
                        camera.zoomByFactor(spread / previousSpread)
                        onCameraChanged()
                    }
                    lastTouchAnchor = anchor
                    lastTouchSpread = spread
                    event.changes.forEach { it.consume() }
                }

                touches.size == 1 -> {
                    val position = touches[0].position
                    // Po zvednutí jednoho ze dvou prstů je `lastTouchSpread`
                    // nenulový; vynulování ho odliší od pokračujícího orbitu.
                    val previous = lastTouchAnchor.takeIf { lastTouchSpread == null }
                    if (previous != null) {
                        camera.orbit(position.x - previous.x, position.y - previous.y)
                        onCameraChanged()
                    }
                    lastTouchAnchor = position
                    lastTouchSpread = null
                    event.changes.forEach { it.consume() }
                }

                else -> {
                    lastTouchAnchor = null
                    lastTouchSpread = null
                    handleMouse(
                        event = event,
                        camera = camera,
                        surface = surface,
                        onCameraChanged = onCameraChanged,
                        lastPosition = lastMouse,
                        orbiting = orbiting,
                        panning = panning,
                        updateState = { position, orbit, pan ->
                            lastMouse = position
                            orbiting = orbit
                            panning = pan
                        },
                    )
                }
            }
        }
    }
}

/**
 * Myší část [cameraInput]. Vytažená ven jen proto, aby zůstala čitelná vedle
 * dotykových větví – stav si drží volající.
 */
private fun handleMouse(
    event: PointerEvent,
    camera: Camera3D,
    surface: GlSurface,
    onCameraChanged: () -> Unit,
    lastPosition: Offset?,
    orbiting: Boolean,
    panning: Boolean,
    updateState: (Offset?, Boolean, Boolean) -> Unit,
) {
    val change = event.changes.firstOrNull() ?: return

    when (event.type) {
        PointerEventType.Press -> {
            updateState(change.position, event.buttons.isPrimaryPressed, event.buttons.isSecondaryPressed)
            change.consume()
        }

        PointerEventType.Release -> {
            updateState(null, false, false)
            change.consume()
        }

        PointerEventType.Move -> {
            val current = change.position
            updateState(current, orbiting, panning)
            if (lastPosition != null && (orbiting || panning)) {
                val dx = current.x - lastPosition.x
                val dy = current.y - lastPosition.y
                if (orbiting) camera.orbit(dx, dy)
                if (panning) camera.pan(dx, dy, surface.pixelWidth, surface.pixelHeight)
                onCameraChanged()
                change.consume()
            }
        }

        else -> Unit
    }
}

@Composable
private fun Gl3DUnavailable(modifier: Modifier) {
    val colors = LocalMongeColors.current
    Box(
        modifier = modifier.background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Prohlížeč nepodporuje WebGL2, 3D náhled proto není k dispozici.",
            color = colors.text.copy(alpha = 0.75f),
            modifier = Modifier.padding(24.dp),
        )
    }
}
