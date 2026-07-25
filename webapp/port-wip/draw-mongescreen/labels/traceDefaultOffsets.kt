package draw.mongescreen.labels

import androidx.compose.ui.geometry.Offset
import model.ProjectionMode
import model.classes.PlaneTraceBokorys
import model.classes.PlaneTraceNarys
import model.classes.PlaneTracePudorys
import state.MongeState
import kotlin.math.abs

private const val TRACE_AXIS_EPS = 1e-5f
private const val TRACE_DEFAULT_LABEL_DISTANCE = 0.75f
private const val TRACE_NEAR_AXIS_PARALLEL_RATIO = 0.12f

private fun Offset.normalizedOrNull(): Offset? {
    val len = getDistance()
    if (len < TRACE_AXIS_EPS) return null
    return this / len
}

private fun defaultTraceOffset(point: Offset, direction: Offset): Offset {
    val axisHits = buildList {
        if (abs(direction.y) >= TRACE_AXIS_EPS) {
            val t = -point.y / direction.y
            add(point + direction * t)
        }
        if (abs(direction.x) >= TRACE_AXIS_EPS) {
            val t = -point.x / direction.x
            add(point + direction * t)
        }
    }

    val preferredDirection =
        if (axisHits.size >= 2) {
            ((axisHits[0] + axisHits[1]) / 2f - point).normalizedOrNull()
        } else {
            ((axisHits.firstOrNull() ?: point) - point).normalizedOrNull()
        }

    val dir =
        preferredDirection
            ?: direction.normalizedOrNull()
            ?: Offset(1f, 0f)

    return dir * TRACE_DEFAULT_LABEL_DISTANCE
}

private fun axisMidpointOrNull(point: Offset, direction: Offset): Offset? {
    val axisHits = buildList {
        if (abs(direction.y) >= TRACE_AXIS_EPS) {
            val t = -point.y / direction.y
            add(point + direction * t)
        }
        if (abs(direction.x) >= TRACE_AXIS_EPS) {
            val t = -point.x / direction.x
            add(point + direction * t)
        }
    }

    if (axisHits.size < 2) return null

    return (axisHits[0] + axisHits[1]) / 2f
}

private fun closestPointToOriginOrNull(point: Offset, direction: Offset): Offset? {
    val len2 = direction.x * direction.x + direction.y * direction.y
    if (len2 < TRACE_AXIS_EPS * TRACE_AXIS_EPS) return null

    val t = -(point.x * direction.x + point.y * direction.y) / len2
    return point + direction * t
}

private fun isNearAxisParallel(direction: Offset): Boolean {
    val len = direction.getDistance()
    if (len < TRACE_AXIS_EPS) return false

    return abs(direction.x / len) < TRACE_NEAR_AXIS_PARALLEL_RATIO ||
            abs(direction.y / len) < TRACE_NEAR_AXIS_PARALLEL_RATIO
}

private fun axoTraceLabelTargetOrNull(point: Offset, direction: Offset): Offset? {
    if (isNearAxisParallel(direction)) {
        closestPointToOriginOrNull(point, direction)?.let { return it }
    }

    return axisMidpointOrNull(point, direction)
        ?: closestPointToOriginOrNull(point, direction)
}

fun defaultPudorysTraceLabelOffset(trace: PlaneTracePudorys): Offset {
    val point = Offset(trace.point.x, trace.point.y)
    return defaultTraceOffset(point, trace.direction)
}

fun defaultPudorysTraceLabelOffset(
    state: MongeState,
    trace: PlaneTracePudorys,
    projector: (PlaneTracePudorys) -> Offset
): Offset {
    if (state.projectionMode != ProjectionMode.AXO) {
        return defaultPudorysTraceLabelOffset(trace)
    }

    val point = Offset(trace.point.x, trace.point.y)
    val target = axoTraceLabelTargetOrNull(point, trace.direction) ?: return defaultTraceOffset(
        point = projector(trace),
        direction = projectedTraceDirection(trace, projector)
    )

    return projector(trace.copy(point = trace.point.copy(x = target.x, y = target.y))) - projector(trace)
}

fun defaultNarysTraceLabelOffset(state: MongeState, trace: PlaneTraceNarys): Offset {
    val point = Offset(trace.point.x, trace.point.z)
    val offset = defaultTraceOffset(point, trace.direction)

    return if (state.projectionMode == ProjectionMode.AXO) {
        offset
    } else {
        Offset(offset.x, -offset.y)
    }
}

fun defaultNarysTraceLabelOffset(
    state: MongeState,
    trace: PlaneTraceNarys,
    projector: (PlaneTraceNarys) -> Offset
): Offset {
    if (state.projectionMode != ProjectionMode.AXO) {
        return defaultNarysTraceLabelOffset(state, trace)
    }

    val point = Offset(trace.point.x, trace.point.z)
    val target = axoTraceLabelTargetOrNull(point, trace.direction) ?: return defaultTraceOffset(
        point = projector(trace),
        direction = projectedTraceDirection(trace, projector)
    )

    return projector(trace.copy(point = trace.point.copy(x = target.x, z = target.y))) - projector(trace)
}

fun defaultBokorysTraceLabelOffset(state: MongeState, trace: PlaneTraceBokorys): Offset {
    val point = Offset(trace.point.y, trace.point.z)
    val offset = defaultTraceOffset(point, trace.direction)

    return if (state.projectionMode == ProjectionMode.AXO) {
        offset
    } else {
        Offset(offset.x, -offset.y)
    }
}

fun defaultBokorysTraceLabelOffset(
    state: MongeState,
    trace: PlaneTraceBokorys,
    projector: (PlaneTraceBokorys) -> Offset
): Offset {
    if (state.projectionMode != ProjectionMode.AXO) {
        return defaultBokorysTraceLabelOffset(state, trace)
    }

    val point = Offset(trace.point.y, trace.point.z)
    val target = axoTraceLabelTargetOrNull(point, trace.direction) ?: return defaultTraceOffset(
        point = projector(trace),
        direction = projectedTraceDirection(trace, projector)
    )

    return projector(trace.copy(point = trace.point.copy(y = target.x, z = target.y))) - projector(trace)
}

private fun <T> projectedTraceDirection(trace: T, projector: (T) -> Offset): Offset {
    return when (trace) {
        is PlaneTracePudorys -> {
            @Suppress("UNCHECKED_CAST")
            val p = projector(trace.copy(point = trace.point.copy(x = trace.point.x + trace.direction.x, y = trace.point.y + trace.direction.y)) as T)
            p - projector(trace)
        }
        is PlaneTraceNarys -> {
            @Suppress("UNCHECKED_CAST")
            val p = projector(trace.copy(point = trace.point.copy(x = trace.point.x + trace.direction.x, z = trace.point.z + trace.direction.y)) as T)
            p - projector(trace)
        }
        is PlaneTraceBokorys -> {
            @Suppress("UNCHECKED_CAST")
            val p = projector(trace.copy(point = trace.point.copy(y = trace.point.y + trace.direction.x, z = trace.point.z + trace.direction.y)) as T)
            p - projector(trace)
        }
        else -> Offset(1f, 0f)
    }
}
