package monge.input.segments.directionHandlers.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import draw.mongescreen.labels.clearSelection
import state.MongeState

private const val DIRECTION_EPS = 1e-6f

private fun effectiveDirection(base: Offset, orthogonal: Boolean): Offset =
    if (orthogonal) Offset(-base.y, base.x) else base

private fun rememberDirectionPattern(state: MongeState) {
    clearSelection(state)
    state.selectedLineIdsPudorys.clear()
    state.selectedLineIdsNarys.clear()
    state.selectedLineIdsBokorys.clear()
    state.selectedLineIdsAxo.clear()
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    state.triggerRedraw++
}

internal fun tryPickSegmentDirectionNarys(state: MongeState, orthogonal: Boolean): Boolean {
    state.selectedLineForParallelNarys?.let { line ->
        state.pendingDirectionNarys = effectiveDirection(line.direction, orthogonal)
        return state.pendingDirectionNarys!!.getDistance() >= DIRECTION_EPS
    }
    state.selectedSegmentForParallelNarys?.let { segment ->
        val base = Offset(segment.end.x - segment.start.x, segment.end.z - segment.start.z)
        state.pendingDirectionNarys = effectiveDirection(base, orthogonal)
        return state.pendingDirectionNarys!!.getDistance() >= DIRECTION_EPS
    }

    val segment = state.snappedSegmentNarys ?: state.selectedSegmentsNarys.firstOrNull()
    if (segment != null) {
        val base = Offset(segment.end.x - segment.start.x, segment.end.z - segment.start.z)
        val direction = effectiveDirection(base, orthogonal)
        if (direction.getDistance() < DIRECTION_EPS) return false
        state.selectedSegmentForParallelNarys = segment
        state.pendingDirectionNarys = direction
        rememberDirectionPattern(state)
        println("Segment v narysu oznacen jako vzor smeru.")
        return true
    }

    val line = state.snappedLineNarys ?: state.selectedLinesNarys.firstOrNull()
    if (line != null) {
        val direction = effectiveDirection(line.direction, orthogonal)
        if (direction.getDistance() < DIRECTION_EPS) return false
        state.selectedLineForParallelNarys = line
        state.pendingDirectionNarys = direction
        rememberDirectionPattern(state)
        println("Primka '${line.name}' v narysu oznacena jako vzor smeru.")
        return true
    }

    println("Neoznacena zadna primka nebo usecka pro smer.")
    return false
}

internal fun tryPickSegmentDirectionPudorys(state: MongeState, orthogonal: Boolean): Boolean {
    state.selectedLineForParallelPudorys?.let { line ->
        state.pendingDirection = effectiveDirection(line.direction, orthogonal)
        return state.pendingDirection!!.getDistance() >= DIRECTION_EPS
    }
    state.selectedSegmentForParallelPudorys?.let { segment ->
        val base = Offset(segment.end.x - segment.start.x, segment.end.y - segment.start.y)
        state.pendingDirection = effectiveDirection(base, orthogonal)
        return state.pendingDirection!!.getDistance() >= DIRECTION_EPS
    }

    val segment = state.snappedSegmentPudorys ?: state.selectedSegmentsPudorys.firstOrNull()
    if (segment != null) {
        val base = Offset(segment.end.x - segment.start.x, segment.end.y - segment.start.y)
        val direction = effectiveDirection(base, orthogonal)
        if (direction.getDistance() < DIRECTION_EPS) return false
        state.selectedSegmentForParallelPudorys = segment
        state.pendingDirection = direction
        rememberDirectionPattern(state)
        println("Segment v pudorysu oznacen jako vzor smeru.")
        return true
    }

    val line = state.snappedLinePudorys ?: state.selectedLinesPudorys.firstOrNull()
    if (line != null) {
        val direction = effectiveDirection(line.direction, orthogonal)
        if (direction.getDistance() < DIRECTION_EPS) return false
        state.selectedLineForParallelPudorys = line
        state.pendingDirection = direction
        rememberDirectionPattern(state)
        println("Primka '${line.name}' v pudorysu oznacena jako vzor smeru.")
        return true
    }

    println("Neoznacena zadna primka nebo usecka pro smer.")
    return false
}
