package monge.input.axo.planes

import utils.System
import androidx.compose.ui.geometry.Offset
import model.ConstructionModifier
import model.ProjectionType
import model.axo.AxoMode
import model.classes.PlaneTraceBokorys
import model.classes.PlaneTraceNarys
import model.classes.PlaneTracePudorys
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.pickOverlayReferenceFromCurrentHover
import monge.input.axo.lines.resolveBokorysDirectionAxo
import monge.input.axo.lines.resolveNarysDirectionAxo
import monge.input.axo.lines.resolvePudorysDirectionAxo
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex

fun planesRouter(state: MongeState, snappedPointLogical: Offset?) {
    if (state.projekcnityp != ProjectionType.SINGLE) return
    when (state.axoMode) {
        AxoMode.AXO_PUDORYS -> handleSinglePudorysTraceAxo(state, snappedPointLogical)
        AxoMode.AXO_NARYS -> handleSingleNarysTraceAxo(state, snappedPointLogical)
        AxoMode.AXO_BOKORYS -> handleSingleBokorysTraceAxo(state, snappedPointLogical)
        AxoMode.NORMAL_2D -> Unit
    }
}

private fun handleSinglePudorysTraceAxo(state: MongeState, snapped: Offset?) {
    val logical = getLogicalCursorAxo(
        snapped = snapped,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_PUDORYS,
        axoModel = state.activeAxoModel
    ) ?: return

    if (tryFinishSingleTraceWithModifierAxo(state, logical, AxoMode.AXO_PUDORYS)) return

    when (state.projectionPhase) {
        "pudorys_start" -> {
            state.firstPlaneTraceStartPudorys = Point3DPudorys(logical.x, logical.y, "")
            setProjectionPhase("plane_trace_single_pudorys_start", state)
            updateConstructionInfo(state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        }
        "plane_trace_single_pudorys_start" -> {
            val start = state.firstPlaneTraceStartPudorys ?: return
            val direction = Offset(logical.x - start.x, logical.y - start.y)
            if (direction.getDistance() == 0f) return
            val trace = PlaneTracePudorys(
                point = start,
                direction = direction,
                localColor = state.currentLineStyleSettings.color,
                localName = "",
                localLineStyle = state.currentLineStyleSettings.style,
                localStrokeWidth = state.currentLineStyleSettings.strokeWidth,
                creationIndex = allocIndex(state)
            )
            state.tracePlanePudorys = trace
            state.pudorysTracePendingForNaming = trace
            state.showPlaneNamingDialog = true
            state.lineTracesPudorys.add(trace)
            resetStavu(state)
        }
    }
}

private fun handleSingleNarysTraceAxo(state: MongeState, snapped: Offset?) {
    val logical = getLogicalCursorAxo(
        snapped = snapped,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_NARYS,
        axoModel = state.activeAxoModel
    ) ?: return

    if (tryFinishSingleTraceWithModifierAxo(state, logical, AxoMode.AXO_NARYS)) return

    when (state.projectionPhase) {
        "pudorys_start" -> {
            state.firstPlaneTraceStartNarys = Point3DNarys(logical.x, logical.y, "")
            setProjectionPhase("plane_trace_single_narys_start", state)
            updateConstructionInfo(state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        }
        "plane_trace_single_narys_start" -> {
            val start = state.firstPlaneTraceStartNarys ?: return
            val direction = Offset(logical.x - start.x, logical.y - start.z)
            if (direction.getDistance() == 0f) return
            val trace = PlaneTraceNarys(
                point = start,
                direction = direction,
                localColor = state.currentLineStyleSettings.color,
                localName = "",
                localLineStyle = state.currentLineStyleSettings.style,
                localStrokeWidth = state.currentLineStyleSettings.strokeWidth,
                creationIndex = allocIndex(state)
            )
            state.tracePlaneNarys = trace
            state.narysTracePendingForNaming = trace
            state.showPlaneNamingDialog = true
            state.lineTracesNarys.add(trace)
            resetStavu(state)
        }
    }
}

private fun handleSingleBokorysTraceAxo(state: MongeState, snapped: Offset?) {
    val logical = getLogicalCursorAxo(
        snapped = snapped,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_BOKORYS,
        axoModel = state.activeAxoModel
    ) ?: return

    if (tryFinishSingleTraceWithModifierAxo(state, logical, AxoMode.AXO_BOKORYS)) return

    when (state.projectionPhase) {
        "pudorys_start" -> {
            state.firstPlaneTraceStartBokorys = Point3DBokorys(logical.x, logical.y, "")
            setProjectionPhase("plane_trace_single_bokorys_start", state)
            updateConstructionInfo(state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        }
        "plane_trace_single_bokorys_start" -> {
            val start = state.firstPlaneTraceStartBokorys ?: return
            val direction = Offset(logical.x - start.y, logical.y - start.z)
            if (direction.getDistance() == 0f) return
            val trace = PlaneTraceBokorys(
                point = start,
                direction = direction,
                localColor = state.currentLineStyleSettings.color,
                localName = "",
                localLineStyle = state.currentLineStyleSettings.style,
                localStrokeWidth = state.currentLineStyleSettings.strokeWidth,
                creationIndex = allocIndex(state)
            )
            state.tracePlaneBokorys = trace
            state.bokorysTracePendingForNaming = trace
            state.showPlaneNamingDialog = true
            state.lineTracesBokorys.add(trace)
            resetStavu(state)
        }
    }
}

private fun tryFinishSingleTraceWithModifierAxo(
    state: MongeState,
    logical: Offset,
    mode: AxoMode
): Boolean {
    val modifier = state.constructionModifier
    if (modifier == ConstructionModifier.NONE) return false

    if (!hasOverlayReference(state)) {
        pickOverlayReferenceFromCurrentHover(state)
        updateConstructionInfo(state)
        return true
    }

    val wantPerpendicular = modifier == ConstructionModifier.ORTHOGONAL
    val direction = when (mode) {
        AxoMode.AXO_PUDORYS -> resolvePudorysDirectionAxo(state, wantPerpendicular)
        AxoMode.AXO_NARYS -> resolveNarysDirectionAxo(state, wantPerpendicular)
        AxoMode.AXO_BOKORYS -> resolveBokorysDirectionAxo(state, wantPerpendicular)
        AxoMode.NORMAL_2D -> null
    } ?: return true

    when (mode) {
        AxoMode.AXO_PUDORYS -> {
            val trace = PlaneTracePudorys(
                point = Point3DPudorys(logical.x, logical.y, ""),
                direction = direction,
                localColor = state.currentLineStyleSettings.color,
                localName = "",
                localLineStyle = state.currentLineStyleSettings.style,
                localStrokeWidth = state.currentLineStyleSettings.strokeWidth,
                creationIndex = allocIndex(state)
            )
            state.tracePlanePudorys = trace
            state.pudorysTracePendingForNaming = trace
            state.lineTracesPudorys.add(trace)
        }
        AxoMode.AXO_NARYS -> {
            val trace = PlaneTraceNarys(
                point = Point3DNarys(logical.x, logical.y, ""),
                direction = direction,
                localColor = state.currentLineStyleSettings.color,
                localName = "",
                localLineStyle = state.currentLineStyleSettings.style,
                localStrokeWidth = state.currentLineStyleSettings.strokeWidth,
                creationIndex = allocIndex(state)
            )
            state.tracePlaneNarys = trace
            state.narysTracePendingForNaming = trace
            state.lineTracesNarys.add(trace)
        }
        AxoMode.AXO_BOKORYS -> {
            val trace = PlaneTraceBokorys(
                point = Point3DBokorys(logical.x, logical.y, ""),
                direction = direction,
                localColor = state.currentLineStyleSettings.color,
                localName = "",
                localLineStyle = state.currentLineStyleSettings.style,
                localStrokeWidth = state.currentLineStyleSettings.strokeWidth,
                creationIndex = allocIndex(state)
            )
            state.tracePlaneBokorys = trace
            state.bokorysTracePendingForNaming = trace
            state.lineTracesBokorys.add(trace)
        }
        AxoMode.NORMAL_2D -> Unit
    }

    state.showPlaneNamingDialog = true
    state.constructionModifier = ConstructionModifier.NONE
    updateConstructionInfo(state)
    resetStavu(state)
    return true
}
