package ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import model.XAxisDirection
import model.YAxisDirectionPlane
import state.MongeState
import utils.System
import utils.cursorToScreen

/**
 * Navigace canvasu sdílená MONGE i PLANE obrazovkou.
 *
 * Myš:
 *  - pravý drag zůstává kompatibilní s desktopem,
 *  - prostřední drag je webová alternativa pro prohlížeče s vlastními
 *    gesty pravého tlačítka (Vivaldi, některé ovladače touchpadů).
 *
 * Dotyk:
 *  - posun i zoom obstarávají dva prsty současně,
 *  - jeden prst kreslí – krátké ťuknutí se chová jako levý klik myší.
 *
 * Stylus:
 *  - kreslí vždy, plátno neposouvá vůbec.
 *
 * Funkce jsou rozšíření [Density], protože všechny tolerance jsou v dp.
 * Compose Web počítá pozice v zařízených pixelech, takže pevná hodnota v px
 * by na displeji s dvojnásobným rozlišením byla dvakrát přísnější – ťuknutí
 * prstem se pak nikdy nevejde do tolerance a nezareaguje.
 */
fun Density.handleCanvasNavigationEvent(
    event: PointerEvent,
    state: MongeState
): Boolean {
    val fingers = event.changes.filter { it.inputKind() == CanvasInputKind.FINGER }
    val pressedFingers = fingers.filter { it.pressed }

    trackTouchTap(fingers, pressedFingers.size)

    if (pressedFingers.size >= 2) {
        applyTwoFingerGesture(state, pressedFingers)
        state.cursorPosition = pressedFingers.centroid(previous = false)
        beginCanvasPan(state)
        event.changes.forEach { it.consume() }
        return true
    }
    TouchGesture.endPinch()

    val change = event.changes.firstOrNull() ?: return false
    when (change.inputKind()) {
        // Hrot ani prst plátno neposouvají – oba propadnou do click handleru.
        CanvasInputKind.STYLUS, CanvasInputKind.FINGER -> {
            endCanvasPan(state)
            return false
        }

        CanvasInputKind.MOUSE -> {
            val navigationButtonDown =
                event.buttons.isSecondaryPressed ||
                    event.buttons.isTertiaryPressed ||
                    browserCanvasNavigationActive()
            val panContinues = state.isPanning && event.type != PointerEventType.Release

            if (navigationButtonDown || panContinues) {
                val drag = change.positionChange()
                if (drag != Offset.Zero) {
                    state.canvasOffset += drag.toCanvasOffsetDelta(state)
                }
                state.cursorPosition = change.position
                beginCanvasPan(state)
                change.consume()
                return true
            }
        }
    }

    endCanvasPan(state)
    return false
}

/**
 * Levý klik myši a hrot stylusu kliknou hned při stisku. Prst kliká ťuknutím,
 * tedy až při zvednutí – kdyby kliknul hned při dotyku, položil by bod ještě
 * dřív, než uživatel stihne přiložit druhý prst k posunu plátna.
 */
fun isCanvasClickGesture(
    change: PointerInputChange,
    event: PointerEvent,
    state: MongeState
): Boolean {
    if (TouchGesture.tapCompleted) return true
    if (!change.changedToDown()) return false

    return when (change.inputKind()) {
        CanvasInputKind.STYLUS -> true
        CanvasInputKind.FINGER -> false
        CanvasInputKind.MOUSE -> event.buttons.isPrimaryPressed
    }
}

/**
 * Compose Web 1.9.x převádí prohlížečové události stylusu na myš nebo dotyk. Malý
 * platformní bridge zachová původní `PointerEvent.pointerType` a stav
 * navigačního tlačítka, aby šel stylus od prstu spolehlivě odlišit.
 */
internal expect fun browserCanvasPointerType(): String

internal expect fun browserCanvasDownPointerType(): String

internal expect fun browserCanvasNavigationActive(): Boolean

private enum class CanvasInputKind { MOUSE, FINGER, STYLUS }

/**
 * Typ vstupu pro právě probíhající gesto.
 *
 * Rozhoduje se podle posledního `pointerdown`, ne podle poslední události
 * vůbec: stylus v dosahu displeje sype `pointermove` i bez doteku a podle
 * nich by se každé ťuknutí prstem tvářilo jako stylus.
 */
private fun PointerInputChange.inputKind(): CanvasInputKind {
    if (type == PointerType.Stylus || type == PointerType.Eraser) {
        return CanvasInputKind.STYLUS
    }
    if (pressed || previousPressed) {
        when (browserCanvasDownPointerType()) {
            "pen" -> return CanvasInputKind.STYLUS
            "touch" -> return CanvasInputKind.FINGER
        }
    }
    if (type == PointerType.Touch) return CanvasInputKind.FINGER
    return CanvasInputKind.MOUSE
}

/** Posun prstu, do kterého se dotyk ještě počítá jako ťuknutí, ne tah. */
private val TAP_SLOP = 20.dp

/**
 * O kolik se musí prsty rozejít, aby gesto přestalo být čistým posunem.
 *
 * Měří se průměrná vzdálenost od těžiště, tedy zhruba polovina rozestupu
 * prstů. Při tažení po skle se rozestup vždycky trochu mění; teprve úmyslné
 * štípnutí ho změní o několik centimetrů.
 */
private val PINCH_SLOP = 32.dp

/**
 * Rozpracované dotykové gesto. Obrazovky se přepínají záložkami, takže na
 * plátnech nikdy nekreslí dvě gesta naráz a stačí jeden sdílený stav.
 */
private object TouchGesture {
    var tapPointer: PointerId? = null
    var tapStart: Offset = Offset.Zero
    var tapMoved: Boolean = false
    var multiTouch: Boolean = false

    /** Platí jen pro právě zpracovávanou událost – čte ho [isCanvasClickGesture]. */
    var tapCompleted: Boolean = false

    var pinchActive: Boolean = false
    var pinchStartSpan: Float = 0f
    var zoomUnlocked: Boolean = false

    fun endPinch() {
        pinchActive = false
        pinchStartSpan = 0f
        zoomUnlocked = false
    }
}

/**
 * Sleduje dotyk od přiložení po zvednutí prstu a rozhodne, jestli z něj bude
 * klik. Ťuknutí ruší druhý prst (to je navigační gesto) i větší posun.
 */
private fun Density.trackTouchTap(
    fingers: List<PointerInputChange>,
    pressedCount: Int
) {
    TouchGesture.tapCompleted = false
    if (fingers.isEmpty()) {
        // Myš a stylus mají vlastní cestu ke kliku.
        TouchGesture.tapPointer = null
        TouchGesture.multiTouch = false
        return
    }

    if (pressedCount >= 2) TouchGesture.multiTouch = true

    val down = fingers.firstOrNull { it.changedToDownIgnoreConsumed() }
    if (down != null && pressedCount == 1 && !TouchGesture.multiTouch) {
        TouchGesture.tapPointer = down.id
        TouchGesture.tapStart = down.position
        TouchGesture.tapMoved = false
    }

    val tracked = fingers.firstOrNull { it.id == TouchGesture.tapPointer }
    if (tracked != null) {
        if ((tracked.position - TouchGesture.tapStart).getDistance() > TAP_SLOP.toPx()) {
            TouchGesture.tapMoved = true
        }
        if (tracked.changedToUpIgnoreConsumed()) {
            TouchGesture.tapCompleted = !TouchGesture.tapMoved && !TouchGesture.multiTouch
            TouchGesture.tapPointer = null
        }
    }

    if (pressedCount == 0) {
        // Všechny prsty nahoře – další dotyk začíná načisto.
        TouchGesture.tapPointer = null
        TouchGesture.multiTouch = false
    }
}

/**
 * Posun a zoom dvěma prsty.
 *
 * Zoom se zapne až po znatelném rozejití prstů. Bez toho by se během posunu
 * pořád trochu měnil i rozestup a měřítko by se pod rukama neustále mrskalo –
 * plátno pak působí, jako by na posun vůbec nereagovalo.
 */
private fun Density.applyTwoFingerGesture(
    state: MongeState,
    touches: List<PointerInputChange>
) {
    val moving = touches.filter { it.previousPressed }
    if (moving.size < 2) {
        // Prst zrovna dosedl – gestu stačí zapamatovat výchozí rozestup.
        val centroid = touches.centroid(previous = false)
        TouchGesture.pinchActive = true
        TouchGesture.pinchStartSpan = touches.averageSpan(centroid, previous = false)
        TouchGesture.zoomUnlocked = false
        return
    }

    val previousCentroid = moving.centroid(previous = true)
    val currentCentroid = moving.centroid(previous = false)
    val previousSpan = moving.averageSpan(previousCentroid, previous = true)
    val currentSpan = moving.averageSpan(currentCentroid, previous = false)

    if (!TouchGesture.pinchActive) {
        TouchGesture.pinchActive = true
        TouchGesture.pinchStartSpan = previousSpan
        TouchGesture.zoomUnlocked = false
    }
    if (
        !TouchGesture.zoomUnlocked &&
        abs(currentSpan - TouchGesture.pinchStartSpan) > PINCH_SLOP.toPx()
    ) {
        TouchGesture.zoomUnlocked = true
    }

    val zoom = if (TouchGesture.zoomUnlocked && previousSpan > 0.01f) {
        currentSpan / previousSpan
    } else {
        1f
    }

    transformCanvas(
        state = state,
        previousCentroid = previousCentroid,
        currentCentroid = currentCentroid,
        zoom = zoom
    )
}

private fun beginCanvasPan(state: MongeState) {
    state.isPanning = true
    state.stopSnap = System.currentTimeMillis() + 200
}

private fun endCanvasPan(state: MongeState) {
    if (!state.isPanning) return
    state.isPanning = false
    state.stopSnap = System.currentTimeMillis() + 80
}

private fun Offset.toCanvasOffsetDelta(state: MongeState): Offset {
    val flipX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
    val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    return Offset(
        x = if (flipX) -x else x,
        y = if (flipY) -y else y
    )
}

private fun transformCanvas(
    state: MongeState,
    previousCentroid: Offset,
    currentCentroid: Offset,
    zoom: Float
) {
    val oldScale = state.scale
    val newScale = (oldScale * zoom).coerceIn(1f, 50f)
    val flipX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
    val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP

    val previousScreen = cursorToScreen(
        cursor = previousCentroid,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = flipX,
        flipY = flipY
    )
    val currentScreen = cursorToScreen(
        cursor = currentCentroid,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = flipX,
        flipY = flipY
    )
    val logicalAtCentroid = (previousScreen - state.canvasOffset) / oldScale

    state.scale = newScale
    state.canvasOffset = currentScreen - logicalAtCentroid * newScale
}

private fun List<PointerInputChange>.centroid(previous: Boolean): Offset {
    var sum = Offset.Zero
    forEach { change ->
        sum += if (previous) change.previousPosition else change.position
    }
    return sum / size.toFloat()
}

private fun List<PointerInputChange>.averageSpan(
    centroid: Offset,
    previous: Boolean
): Float {
    var sum = 0f
    forEach { change ->
        val position = if (previous) change.previousPosition else change.position
        sum += (position - centroid).getDistance()
    }
    return sum / size.toFloat()
}
