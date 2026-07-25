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
 * Dotyk a stylus:
 *  - prst vždy posouvá canvas a dva prsty současně posouvají + zoomují,
 *  - stylus v NONE režimu posouvá,
 *  - stylus v konstrukčním režimu propadne do click handleru.
 */
fun handleCanvasNavigationEvent(
    event: PointerEvent,
    state: MongeState
): Boolean {
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

    val isTouchPan =
        (
            (change.type == PointerType.Touch && !isBrowserStylus) ||
                isBrowserTouch
            ) &&
            change.pressed
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

    if (isTouchPan || isStylusPan || isMousePan) {
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
 * konstrukci; v NONE režimu je vyhrazený pro pan.
 */
fun isCanvasClickDown(
    change: PointerInputChange,
    event: PointerEvent,
    state: MongeState
): Boolean {
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
