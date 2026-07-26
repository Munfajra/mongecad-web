package ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import model.Mongeobjects
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
 * Jeden prst schválně neposouvá plátno: kdyby posouval i kreslil zároveň,
 * nešlo by obojí rozlišit a konstrukce prstem by byly nepoužitelné.
 *
 * Stylus:
 *  - v NONE režimu posouvá,
 *  - v konstrukčním režimu propadne do click handleru.
 */
fun handleCanvasNavigationEvent(
    event: PointerEvent,
    state: MongeState
): Boolean {
    trackTouchTap(event)

    val touchChanges = event.changes.filter {
        it.type == PointerType.Touch && it.pressed
    }
    val stableTouches = touchChanges.filter { it.previousPressed }

    if (stableTouches.size >= 2) {
        val previousCentroid = stableTouches.centroid(previous = true)
        val currentCentroid = stableTouches.centroid(previous = false)
        val previousSpan = stableTouches.averageSpan(previousCentroid, previous = true)
        val currentSpan = stableTouches.averageSpan(currentCentroid, previous = false)
        val zoom = if (previousSpan > 0.01f) currentSpan / previousSpan else 1f

        transformCanvas(
            state = state,
            previousCentroid = previousCentroid,
            currentCentroid = currentCentroid,
            zoom = zoom
        )
        state.cursorPosition = currentCentroid
        beginCanvasPan(state)
        event.changes.forEach { it.consume() }
        return true
    }

    val change = event.changes.firstOrNull() ?: return false
    val browserPointerType = browserCanvasPointerType()
    val isBrowserStylus = browserPointerType == "pen"
    val isBrowserTouch =
        change.type == PointerType.Mouse && browserPointerType == "touch"
    val navigationMouseButtonDown =
        event.buttons.isSecondaryPressed ||
            event.buttons.isTertiaryPressed ||
            browserCanvasNavigationActive()
    val mousePanContinues =
        state.isPanning && event.type != PointerEventType.Release

    val isStylusPan =
        (
            change.type == PointerType.Stylus ||
                change.type == PointerType.Eraser ||
                isBrowserStylus
            ) &&
            change.pressed &&
            state.drawobjects == Mongeobjects.NONE
    val isMousePan =
        change.type == PointerType.Mouse &&
            !isBrowserStylus &&
            !isBrowserTouch &&
            (navigationMouseButtonDown || mousePanContinues)

    if (isStylusPan || isMousePan) {
        val drag = change.positionChange()
        if (drag != Offset.Zero) {
            state.canvasOffset += drag.toCanvasOffsetDelta(state)
        }
        state.cursorPosition = change.position
        beginCanvasPan(state)
        change.consume()
        return true
    }

    endCanvasPan(state)
    return false
}

/**
 * Levý klik myši se chová jako doposud. Hrot stylusu kliká jen při aktivní
 * konstrukci; v NONE režimu je vyhrazený pro pan. Prst kliká ťuknutím, tedy
 * až při zvednutí – kdyby kliknul hned při dotyku, položil by bod ještě
 * dřív, než uživatel stihne přiložit druhý prst k posunu plátna.
 */
fun isCanvasClickGesture(
    change: PointerInputChange,
    event: PointerEvent,
    state: MongeState
): Boolean {
    if (TouchTap.completedInThisEvent) return true
    if (!change.changedToDown()) return false

    val browserPointerType = browserCanvasPointerType()
    val isBrowserStylus = browserPointerType == "pen"
    if (isBrowserStylus) return state.drawobjects != Mongeobjects.NONE
    if (change.type == PointerType.Mouse && browserPointerType == "touch") return false

    return when (change.type) {
        PointerType.Stylus, PointerType.Eraser -> state.drawobjects != Mongeobjects.NONE
        PointerType.Touch -> false
        else -> event.buttons.isPrimaryPressed
    }
}

/**
 * Compose Web 1.9.x převádí prohlížečové události stylusu na myš nebo dotyk. Malý
 * platformní bridge zachová původní `PointerEvent.pointerType` a stav
 * navigačního tlačítka, aby šel stylus od myši spolehlivě odlišit.
 */
internal expect fun browserCanvasPointerType(): String

internal expect fun browserCanvasNavigationActive(): Boolean

/** Posun prstu (px), do kterého se dotyk ještě počítá jako ťuknutí, ne tah. */
private const val TAP_SLOP_PX = 18f

/**
 * Rozpracované ťuknutí prstem. Obrazovky se přepínají záložkami, takže na
 * plátnech nikdy nekreslí dvě gesta naráz a stačí jeden sdílený stav.
 */
private object TouchTap {
    var pointerId: PointerId? = null
    var startPosition: Offset = Offset.Zero
    var cancelled: Boolean = false

    /** Platí jen pro právě zpracovávanou událost – čte ho [isCanvasClickGesture]. */
    var completedInThisEvent: Boolean = false

    fun reset() {
        pointerId = null
        startPosition = Offset.Zero
        cancelled = false
    }
}

/**
 * Sleduje dotyk od přiložení po zvednutí prstu a rozhodne, jestli z něj bude
 * klik. Ťuknutí ruší druhý prst (to je navigační gesto) i větší posun
 * (to je tah, ne klik).
 */
private fun trackTouchTap(event: PointerEvent) {
    TouchTap.completedInThisEvent = false

    val touchChanges = event.changes.filter { it.isFingerLike() }
    if (touchChanges.isEmpty()) {
        // Myš a stylus mají vlastní cestu ke kliku, rozpracované ťuknutí ruší.
        TouchTap.reset()
        return
    }

    if (touchChanges.count { it.pressed } >= 2) TouchTap.cancelled = true

    if (TouchTap.pointerId == null) {
        val down = touchChanges.firstOrNull { it.changedToDownIgnoreConsumed() }
        if (down != null && touchChanges.count { it.pressed } == 1) {
            TouchTap.pointerId = down.id
            TouchTap.startPosition = down.position
            TouchTap.cancelled = false
        }
    }

    val tracked = touchChanges.firstOrNull { it.id == TouchTap.pointerId } ?: return
    if ((tracked.position - TouchTap.startPosition).getDistance() > TAP_SLOP_PX) {
        TouchTap.cancelled = true
    }

    if (tracked.changedToUpIgnoreConsumed()) {
        TouchTap.completedInThisEvent = !TouchTap.cancelled
        TouchTap.reset()
    }
}

/**
 * Compose Web hlásí prst většinou jako [PointerType.Touch], v některých
 * prohlížečích ale propadne na myš – původní typ pak zná jen bridge z HTML.
 */
private fun PointerInputChange.isFingerLike(): Boolean {
    if (type == PointerType.Touch) return browserCanvasPointerType() != "pen"
    return type == PointerType.Mouse && browserCanvasPointerType() == "touch"
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
