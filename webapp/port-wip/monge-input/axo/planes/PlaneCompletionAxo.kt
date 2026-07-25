package monge.input.axo.planes

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.axo.AxoMode
import model.classes.*
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.clearOverlayLineReferenceSelection
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.pickOverlayReferenceFromCurrentHover
import monge.input.axo.lines.resolveBokorysDirectionAxo
import monge.input.axo.lines.resolveNarysDirectionAxo
import monge.input.axo.lines.resolvePudorysDirectionAxo
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetPlaneConstruction
import utils.allocIndex
import kotlin.math.abs
import kotlin.math.sqrt

fun beginAxoPlaneCompletionFromTrace(
    state: MongeState,
    trace: Trace2DProjection
) {
    clearOverlayLineReferenceSelection(state)
    state.drawobjects = Mongeobjects.PLANE
    state.reusingExistingProjection = true
    state.projekcnityp = ProjectionType.SINGLE
    state.traceToAttach = trace
    state.constructionModifier = ConstructionModifier.NONE
    state.completionPending = null

    when (trace) {
        is PlaneTracePudorys -> {
            state.tracePlanePudorys = trace
            state.tracePlaneNarys = null
            state.tracePlaneBokorys = null
            setProjectionPhase("axo_plane_complete_from_pudorys", state)
            state.consInfo.value = "Přepni do nárysu nebo bokorysu a klikni pro druhou stopu roviny."
        }
        is PlaneTraceNarys -> {
            state.tracePlaneNarys = trace
            state.tracePlanePudorys = null
            state.tracePlaneBokorys = null
            setProjectionPhase("axo_plane_complete_from_narys", state)
            state.consInfo.value = "Přepni do půdorysu nebo bokorysu a klikni pro druhou stopu roviny."
        }
        is PlaneTraceBokorys -> {
            state.tracePlaneBokorys = trace
            state.tracePlanePudorys = null
            state.tracePlaneNarys = null
            setProjectionPhase("axo_plane_complete_from_bokorys", state)
            state.consInfo.value = "Přepni do půdorysu nebo nárysu a klikni pro druhou stopu roviny."
        }
    }

    state.deferSelectionUntil = System.currentTimeMillis() + 100
    updateConstructionInfo(state)
}

fun handleAxoPlaneCompletionClick(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    val phase = state.projectionPhase
    if (
        phase != "axo_plane_complete_from_pudorys" &&
        phase != "axo_plane_complete_from_narys" &&
        phase != "axo_plane_complete_from_bokorys"
    ) return

    val mode = state.axoMode
    if (!isSecondProjectionModeAllowed(phase, mode)) {
        state.consInfo.value = when (phase) {
            "axo_plane_complete_from_pudorys" -> "Přepni do nárysu nebo bokorysu."
            "axo_plane_complete_from_narys" -> "Přepni do půdorysu nebo bokorysu."
            else -> "Přepni do půdorysu nebo nárysu."
        }
        return
    }
    if (state.constructionModifier != ConstructionModifier.NONE) {
        pickOverlayReferenceFromCurrentHover(state)
        if (!hasOverlayReference(state)) {
            pickOverlayReferenceFromSelection(state)
        }
        if (!hasOverlayReference(state)) {
            state.consInfo.value = "Vyber vzorovou přímku/úsečku pro směr."
            updateConstructionInfo(state)
            return
        }
    }
    val logical = getLogicalCursorAxo(
        snapped = snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = mode,
        axoModel = state.activeAxoModel
    ) ?: return

    val secondTrace = when (phase) {
        "axo_plane_complete_from_pudorys" -> buildSecondTraceFromPudorysBase(state, logical, mode)
        "axo_plane_complete_from_narys" -> buildSecondTraceFromNarysBase(state, logical, mode)
        else -> buildSecondTraceFromBokorysBase(state, logical, mode)
    } ?: run {
        state.consInfo.value = secondTraceFailMessage(phase, mode, state)
        return
    }

    val firstTrace = state.traceToAttach ?: return
    completePlaneFromTwoTraces(state, firstTrace, secondTrace)
}

private fun isSecondProjectionModeAllowed(phase: String, mode: AxoMode): Boolean =
    when (phase) {
        "axo_plane_complete_from_pudorys" -> mode == AxoMode.AXO_NARYS || mode == AxoMode.AXO_BOKORYS
        "axo_plane_complete_from_narys" -> mode == AxoMode.AXO_PUDORYS || mode == AxoMode.AXO_BOKORYS
        "axo_plane_complete_from_bokorys" -> mode == AxoMode.AXO_PUDORYS || mode == AxoMode.AXO_NARYS
        else -> false
    }

internal fun axoPlaneCompletionCrossAnchor(state: MongeState): Offset? {
    val phase = state.projectionPhase ?: return null
    val mode = state.axoMode
    if (!isSecondProjectionModeAllowed(phase, mode)) return null

    return when (phase) {
        "axo_plane_complete_from_pudorys" -> {
            val base = state.tracePlanePudorys ?: return null
            when (mode) {
                AxoMode.AXO_NARYS -> intersectionXWithX12(
                    base.point.x,
                    base.point.y,
                    base.direction.x,
                    base.direction.y
                )?.let { Offset(it, 0f) }

                AxoMode.AXO_BOKORYS -> intersectionYAtXZero(
                    base.point.x,
                    base.point.y,
                    base.direction.x,
                    base.direction.y
                )?.let { Offset(it, 0f) }

                else -> null
            }
        }

        "axo_plane_complete_from_narys" -> {
            val base = state.tracePlaneNarys ?: return null
            when (mode) {
                AxoMode.AXO_PUDORYS -> intersectionXWithX12(
                    base.point.x,
                    base.point.z,
                    base.direction.x,
                    base.direction.y
                )?.let { Offset(it, 0f) }

                AxoMode.AXO_BOKORYS -> intersectionZAtXZero(
                    base.point.x,
                    base.point.z,
                    base.direction.x,
                    base.direction.y
                )?.let { Offset(0f, it) }

                else -> null
            }
        }

        "axo_plane_complete_from_bokorys" -> {
            val base = state.tracePlaneBokorys ?: return null
            when (mode) {
                AxoMode.AXO_PUDORYS -> intersectionYAtZZero(
                    base.point.y,
                    base.point.z,
                    base.direction.x,
                    base.direction.y
                )?.let { Offset(0f, it) }

                AxoMode.AXO_NARYS -> intersectionZAtYZero(
                    base.point.y,
                    base.point.z,
                    base.direction.x,
                    base.direction.y
                )?.let { Offset(0f, it) }

                else -> null
            }
        }

        else -> null
    }
}

private fun secondTraceFailMessage(phase: String, mode: AxoMode, state: MongeState): String {
    val axisMessage = when (phase) {
        "axo_plane_complete_from_pudorys" -> "První stopa nemá průsečík s osou potřebnou pro tento průmět."
        "axo_plane_complete_from_narys" -> "První stopa nemá průsečík s osou potřebnou pro tento průmět."
        else -> "První stopa nemá průsečík s osou potřebnou pro tento průmět."
    }
    val dirMessage = if (state.constructionModifier == ConstructionModifier.NONE) {
        "Klikni do bodu pro směr druhé stopy."
    } else {
        "Nepodařilo se určit směr z vybraného vzoru."
    }
    return if (!isSecondProjectionModeAllowed(phase, mode)) {
        "Neplatný cílový průmět pro dokončení."
    } else {
        "$axisMessage $dirMessage"
    }
}

private fun pickOverlayReferenceFromSelection(state: MongeState) {
    if (state.selectedLineForParallelPudorys == null) {
        state.selectedLineForParallelPudorys = state.selectedLinesPudorys.firstOrNull()
    }
    if (state.selectedLineForParallelNarys == null) {
        state.selectedLineForParallelNarys = state.selectedLinesNarys.firstOrNull()
    }
    if (state.selectedLineForParallelBokorys == null) {
        state.selectedLineForParallelBokorys = state.selectedLinesBokorys.firstOrNull()
    }

    if (state.selectedSegmentForParallelPudorys == null) {
        state.selectedSegmentForParallelPudorys = state.selectedSegmentsPudorys.firstOrNull()
    }
    if (state.selectedSegmentForParallelNarys == null) {
        state.selectedSegmentForParallelNarys = state.selectedSegmentsNarys.firstOrNull()
    }
    if (state.selectedSegmentForParallelBokorys == null) {
        state.selectedSegmentForParallelBokorys = state.selectedSegmentsBokorys.firstOrNull()
    }
}

private fun buildSecondTraceFromPudorysBase(
    state: MongeState,
    clicked: Offset,
    mode: AxoMode
): Trace2DProjection? {
    val base = state.tracePlanePudorys ?: return null
    return when (mode) {
        AxoMode.AXO_NARYS -> {
            val x0 = intersectionXWithX12(base.point.x, base.point.y, base.direction.x, base.direction.y) ?: return null
            val anchor = Point3DNarys(x = x0, z = 0f, name = "")
            val dir = directionForMode(state, mode, clicked, Offset(anchor.x, anchor.z)) ?: return null
            PlaneTraceNarys(anchor, dir, localColor = state.currentLineStyleSettings.color, localLineStyle = state.currentLineStyleSettings.style, localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state))
        }
        AxoMode.AXO_BOKORYS -> {
            val y0 = intersectionYAtXZero(base.point.x, base.point.y, base.direction.x, base.direction.y) ?: return null
            val anchor = Point3DBokorys(y = y0, z = 0f, name = "")
            val dir = directionForMode(state, mode, clicked, Offset(anchor.y, anchor.z)) ?: return null
            PlaneTraceBokorys(anchor, dir, localColor = state.currentLineStyleSettings.color, localLineStyle = state.currentLineStyleSettings.style, localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state))
        }
        else -> null
    }
}

private fun buildSecondTraceFromNarysBase(
    state: MongeState,
    clicked: Offset,
    mode: AxoMode
): Trace2DProjection? {
    val base = state.tracePlaneNarys ?: return null
    return when (mode) {
        AxoMode.AXO_PUDORYS -> {
            val x0 = intersectionXWithX12(base.point.x, base.point.z, base.direction.x, base.direction.y) ?: return null
            val anchor = Point3DPudorys(x = x0, y = 0f, name = "")
            val dir = directionForMode(state, mode, clicked, Offset(anchor.x, anchor.y)) ?: return null
            PlaneTracePudorys(anchor, dir, localColor = state.currentLineStyleSettings.color, localLineStyle = state.currentLineStyleSettings.style, localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state))
        }
        AxoMode.AXO_BOKORYS -> {
            val z0 = intersectionZAtXZero(base.point.x, base.point.z, base.direction.x, base.direction.y) ?: return null
            val anchor = Point3DBokorys(y = 0f, z = z0, name = "")
            val dir = directionForMode(state, mode, clicked, Offset(anchor.y, anchor.z)) ?: return null
            PlaneTraceBokorys(anchor, dir, localColor = state.currentLineStyleSettings.color, localLineStyle = state.currentLineStyleSettings.style, localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state))
        }
        else -> null
    }
}

private fun buildSecondTraceFromBokorysBase(
    state: MongeState,
    clicked: Offset,
    mode: AxoMode
): Trace2DProjection? {
    val base = state.tracePlaneBokorys ?: return null
    return when (mode) {
        AxoMode.AXO_PUDORYS -> {
            val y0 = intersectionYAtZZero(base.point.y, base.point.z, base.direction.x, base.direction.y) ?: return null
            val anchor = Point3DPudorys(x = 0f, y = y0, name = "")
            val dir = directionForMode(state, mode, clicked, Offset(anchor.x, anchor.y)) ?: return null
            PlaneTracePudorys(anchor, dir, localColor = state.currentLineStyleSettings.color, localLineStyle = state.currentLineStyleSettings.style, localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state))
        }
        AxoMode.AXO_NARYS -> {
            val z0 = intersectionZAtYZero(base.point.y, base.point.z, base.direction.x, base.direction.y) ?: return null
            val anchor = Point3DNarys(x = 0f, z = z0, name = "")
            val dir = directionForMode(state, mode, clicked, Offset(anchor.x, anchor.z)) ?: return null
            PlaneTraceNarys(anchor, dir, localColor = state.currentLineStyleSettings.color, localLineStyle = state.currentLineStyleSettings.style, localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state))
        }
        else -> null
    }
}

private fun directionForMode(
    state: MongeState,
    mode: AxoMode,
    clicked: Offset,
    anchor: Offset
): Offset? {
    val modifier = state.constructionModifier
    return if (modifier == ConstructionModifier.NONE) {
        val d = clicked - anchor
        d.takeIf { it.getDistance() > 1e-4f }
    } else {
        val wantPerp = modifier == ConstructionModifier.ORTHOGONAL
        val fromLocalReference = resolveDirectionFromLocalReference(state, mode, wantPerp)
        if (fromLocalReference != null) return fromLocalReference

        val fromLineLike = when (mode) {
            AxoMode.AXO_PUDORYS -> resolvePudorysDirectionAxo(state, wantPerp)
            AxoMode.AXO_NARYS -> resolveNarysDirectionAxo(state, wantPerp)
            AxoMode.AXO_BOKORYS -> resolveBokorysDirectionAxo(state, wantPerp)
            AxoMode.NORMAL_2D -> null
        }
        if (fromLineLike != null) return fromLineLike

        val fromTrace = nearestTraceDirectionForMode(state, mode, clicked)
        if (fromTrace != null) {
            return if (wantPerp) Offset(-fromTrace.y, fromTrace.x).normalizedOrNull() else fromTrace
        }
        null
    }
}

private fun resolveDirectionFromLocalReference(
    state: MongeState,
    mode: AxoMode,
    wantPerpendicular: Boolean
): Offset? {
    val raw = when (mode) {
        AxoMode.AXO_PUDORYS -> {
            state.selectedLineForParallelPudorys?.direction
                ?: state.selectedSegmentForParallelPudorys?.let { Offset(it.end.x - it.start.x, it.end.y - it.start.y) }
        }
        AxoMode.AXO_NARYS -> {
            state.selectedLineForParallelNarys?.direction
                ?: state.selectedSegmentForParallelNarys?.let { Offset(it.end.x - it.start.x, it.end.z - it.start.z) }
        }
        AxoMode.AXO_BOKORYS -> {
            state.selectedLineForParallelBokorys?.direction
                ?: state.selectedSegmentForParallelBokorys?.let { Offset(it.end.y - it.start.y, it.end.z - it.start.z) }
        }
        AxoMode.NORMAL_2D -> null
    } ?: return null

    val base = raw.normalizedOrNull() ?: return null
    return if (wantPerpendicular) Offset(-base.y, base.x).normalizedOrNull() else base
}

private fun nearestTraceDirectionForMode(
    state: MongeState,
    mode: AxoMode,
    clicked: Offset
): Offset? {
    return when (mode) {
        AxoMode.AXO_PUDORYS -> nearestDirectionFromPudorysTrace(state.lineTracesPudorys, clicked)
        AxoMode.AXO_NARYS -> nearestDirectionFromNarysTrace(state.lineTracesNarys, clicked)
        AxoMode.AXO_BOKORYS -> nearestDirectionFromBokorysTrace(state.lineTracesBokorys, clicked)
        AxoMode.NORMAL_2D -> null
    }
}

private fun nearestDirectionFromPudorysTrace(traces: List<PlaneTracePudorys>, clicked: Offset): Offset? {
    var best: Offset? = null
    var bestDist = Float.MAX_VALUE
    for (t in traces) {
        val p = Offset(t.point.x, t.point.y)
        val d = t.direction.normalizedOrNull() ?: continue
        val dist = pointLineDistance(clicked, p, d)
        if (dist < bestDist) {
            bestDist = dist
            best = d
        }
    }
    return best?.takeIf { bestDist < 0.6f }
}

private fun nearestDirectionFromNarysTrace(traces: List<PlaneTraceNarys>, clicked: Offset): Offset? {
    var best: Offset? = null
    var bestDist = Float.MAX_VALUE
    for (t in traces) {
        val p = Offset(t.point.x, t.point.z)
        val d = t.direction.normalizedOrNull() ?: continue
        val dist = pointLineDistance(clicked, p, d)
        if (dist < bestDist) {
            bestDist = dist
            best = d
        }
    }
    return best?.takeIf { bestDist < 0.6f }
}

private fun nearestDirectionFromBokorysTrace(traces: List<PlaneTraceBokorys>, clicked: Offset): Offset? {
    var best: Offset? = null
    var bestDist = Float.MAX_VALUE
    for (t in traces) {
        val p = Offset(t.point.y, t.point.z)
        val d = t.direction.normalizedOrNull() ?: continue
        val dist = pointLineDistance(clicked, p, d)
        if (dist < bestDist) {
            bestDist = dist
            best = d
        }
    }
    return best?.takeIf { bestDist < 0.6f }
}

private fun pointLineDistance(point: Offset, linePoint: Offset, lineDirUnit: Offset): Float {
    val vx = point.x - linePoint.x
    val vy = point.y - linePoint.y
    val nx = -lineDirUnit.y
    val ny = lineDirUnit.x
    return abs(vx * nx + vy * ny) / sqrt(nx * nx + ny * ny)
}

private fun Offset.normalizedOrNull(): Offset? {
    val len = getDistance()
    return if (len < 1e-6f) null else this / len
}

private fun completePlaneFromTwoTraces(
    state: MongeState,
    first: Trace2DProjection,
    second: Trace2DProjection
) {
    val first3 = traceTo3D(first) ?: return
    val second3 = traceTo3D(second) ?: return
    val eq = planeEquationFromTraces(first3.point, first3.dir, second3.dir).normalized()
    val traces = tracesFromPlaneEquation(eq)

    val name = first.name ?: second.name ?: "ρ"
    val superscript = first.superscript ?: second.superscript
    val color = (first as? LinearObject2D)?.color ?: state.currentLineStyleSettings.color
    val style = (first as? LinearObject2D)?.lineStyle ?: state.currentLineStyleSettings.style
    val width = (first as? LinearObject2D)?.strokeWidth ?: state.currentLineStyleSettings.strokeWidth

    val traceP = (traces.pudorys ?: PlaneTracePudorys(Point3DPudorys(0f, 0f), Offset(1f, 0f), isVirtual = true))
    val traceN = (traces.narys ?: PlaneTraceNarys(Point3DNarys(0f, 0f), Offset(1f, 0f), isVirtual = true))
    val traceB = (traces.bokorys ?: PlaneTraceBokorys(Point3DBokorys(0f, 0f), Offset(1f, 0f), isVirtual = true))

    val plane = Plane3D(
        tracePudorys = traceP,
        traceNarys = traceN,
        traceBokorys = traceB,
        name = name,
        superscript = superscript,
        equation = eq,
        color = color,
        lineStyle = style,
        strokeWidth = width,
        creationIndex = allocIndex(state)
    )

    plane.tracePudorys.parent = plane
    plane.traceNarys.parent = plane
    plane.traceBokorys.parent = plane

    attachOrAddTrace(state, first, plane)
    attachOrAddTrace(state, second, plane)

    val firstType = first::class
    val secondType = second::class
    if (firstType != PlaneTracePudorys::class && secondType != PlaneTracePudorys::class) state.lineTracesPudorys.add(plane.tracePudorys)
    if (firstType != PlaneTraceNarys::class && secondType != PlaneTraceNarys::class) state.lineTracesNarys.add(plane.traceNarys)
    if (firstType != PlaneTraceBokorys::class && secondType != PlaneTraceBokorys::class) state.lineTracesBokorys.add(plane.traceBokorys)

    state.planes3D.add(plane)

    // Rovina dokončená z existující stopy převezme název (a horní index) z původní
    // stopy — stejně jako v Monge (viz resolvePlaneNamingAfterCompletion). Dialog na
    // pojmenování se ukáže jen, když ani jedna stopa žádný název nemá.
    val inheritedName = (traceLocalName(first) ?: traceLocalName(second))?.takeIf { it.isNotBlank() }
    if (inheritedName != null) {
        state.planePendingForNaming = null
        state.showPlaneNamingDialog = false
        state.planeNameInput = ""
    } else {
        state.planePendingForNaming = plane
        state.showPlaneNamingDialog = true
        state.planeNameInput = ""
    }

    state.traceToAttach = null
    resetPlaneConstruction(state)
    state.constructionModifier = ConstructionModifier.NONE
    setProjectionPhase("pudorys_start", state)
    updateConstructionInfo(state)
    commitSnapshot(state)
}

private fun attachOrAddTrace(state: MongeState, trace: Trace2DProjection, plane: Plane3D) {
    when (trace) {
        is PlaneTracePudorys -> {
            val updated = plane.tracePudorys.copy(id = trace.id, localName = trace.localName, localSuperscript = trace.localSuperscript)
            val idx = state.lineTracesPudorys.indexOfFirst { it.id == trace.id }
            if (idx >= 0) state.lineTracesPudorys[idx] = updated else state.lineTracesPudorys.add(updated)
        }
        is PlaneTraceNarys -> {
            val updated = plane.traceNarys.copy(id = trace.id, localName = trace.localName, localSuperscript = trace.localSuperscript)
            val idx = state.lineTracesNarys.indexOfFirst { it.id == trace.id }
            if (idx >= 0) state.lineTracesNarys[idx] = updated else state.lineTracesNarys.add(updated)
        }
        is PlaneTraceBokorys -> {
            val updated = plane.traceBokorys.copy(id = trace.id, localName = trace.localName, localSuperscript = trace.localSuperscript)
            val idx = state.lineTracesBokorys.indexOfFirst { it.id == trace.id }
            if (idx >= 0) state.lineTracesBokorys[idx] = updated else state.lineTracesBokorys.add(updated)
        }
    }
}

private fun traceLocalName(trace: Trace2DProjection): String? = when (trace) {
    is PlaneTracePudorys -> trace.localName
    is PlaneTraceNarys -> trace.localName
    is PlaneTraceBokorys -> trace.localName
    else -> null
}

private data class Trace3D(val point: Point3D, val dir: Offset3D)
private fun traceTo3D(trace: Trace2DProjection): Trace3D? = when (trace) {
    is PlaneTracePudorys -> Trace3D(Point3D(trace.point.x, trace.point.y, 0f, ""), Offset3D(trace.direction.x, trace.direction.y, 0f))
    is PlaneTraceNarys -> Trace3D(Point3D(trace.point.x, 0f, trace.point.z, ""), Offset3D(trace.direction.x, 0f, trace.direction.y))
    is PlaneTraceBokorys -> Trace3D(Point3D(0f, trace.point.y, trace.point.z, ""), Offset3D(0f, trace.direction.x, trace.direction.y))
    else -> null
}

private fun directionComponentIsZero(component: Float, first: Float, second: Float): Boolean {
    val length = sqrt(first * first + second * second)
    return length < 1e-6f || abs(component) < 1e-6f * length
}

private fun intersectionXWithX12(px: Float, py: Float, dx: Float, dy: Float): Float? {
    if (directionComponentIsZero(dy, dx, dy)) {
        return if (abs(py) < 1e-6f) px else null
    }
    val t = -py / dy
    return px + t * dx
}

private fun intersectionYAtXZero(px: Float, py: Float, dx: Float, dy: Float): Float? {
    if (directionComponentIsZero(dx, dx, dy)) {
        return if (abs(px) < 1e-6f) py else null
    }
    val t = -px / dx
    return py + t * dy
}

private fun intersectionZAtXZero(px: Float, pz: Float, dx: Float, dz: Float): Float? {
    if (directionComponentIsZero(dx, dx, dz)) {
        return if (abs(px) < 1e-6f) pz else null
    }
    val t = -px / dx
    return pz + t * dz
}

private fun intersectionYAtZZero(py: Float, pz: Float, dy: Float, dz: Float): Float? {
    if (directionComponentIsZero(dz, dy, dz)) {
        return if (abs(pz) < 1e-6f) py else null
    }
    val t = -pz / dz
    return py + t * dy
}

private fun intersectionZAtYZero(py: Float, pz: Float, dy: Float, dz: Float): Float? {
    if (directionComponentIsZero(dy, dy, dz)) {
        return if (abs(py) < 1e-6f) pz else null
    }
    val t = -py / dy
    return pz + t * dz
}
