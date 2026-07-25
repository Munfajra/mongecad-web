package monge.input.axo.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.axo.AxoMode
import model.classes.Point3DBokorys
import model.classes.Segment2DBokorys
import model.classes.TempSnapLine
import model.classes.TempSnapSpace
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.pickOverlayReferenceFromCurrentHover
import monge.input.axo.lines.resolveBokorysDirectionAxo
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex

fun axoSegmentBokorys(
    state: MongeState
) {
    val isDirected =
        state.constructionModifier == ConstructionModifier.PARALLEL ||
                state.constructionModifier == ConstructionModifier.ORTHOGONAL

    val logical = getLogicalCursorAxo(
        snapped = state.snappedPointLogical,
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

    if (isDirected) {
        handleDirectedSegmentBokorysAxo(logical, state)
        return
    }

    // původní obyčejná úsečka
    if (state.segmentStartBokorys == null) {
        val start = Point3DBokorys(
            logical.x,
            logical.y,
            name = "",
            isSegmentEndpoint = true,
            creationIndex = allocIndex(state)
        )

        state.segmentStartBokorys = start
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        state.consInfo.value = "Umístěte druhý bod úsečky"
    } else {
        createSegmentBokorysFromPoints(
            state = state,
            start = state.segmentStartBokorys!!,
            end = Point3DBokorys(
                logical.x,
                logical.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )
        )

        state.segmentStartBokorys = null
        repeatCons(state)
        updateConstructionInfo(state)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        resetStavu(state)
    }
}
fun handleDirectedSegmentBokorysAxo(
    logical: Offset,
    state: MongeState
) {
    if (!hasOverlayReference(state)) {
        pickOverlayReferenceFromCurrentHover(state)

        if (hasOverlayReference(state)) {
            state.consInfo.value = "Umístěte pomocnou přímku"
            setProjectionPhase("Bokorys_directed_segment_place_line",state)
        }

        return
    }

    val isOrthogonal = state.constructionModifier == ConstructionModifier.ORTHOGONAL

    val direction = resolveBokorysDirectionAxo(
        state = state,
        wantPerpendicular = isOrthogonal
    ) ?: return

    when (state.projectionPhase) {
        "",
        "Bokorys_directed_segment_place_line" -> {
            state.pendingPoint1 = logical
            state.pendingDirection = direction

            state.tempLine = TempSnapLine(
                point = logical,
                direction = direction,
                id = "temp",
                space = TempSnapSpace.BOKORYS
            )

            setProjectionPhase("Bokorys_directed_segment_start", state)
            state.consInfo.value = "Umístěte začátek úsečky"
            return
        }
        "Bokorys_directed_segment_start" -> {
            val linePoint = state.pendingPoint1  ?: return
            val lineDir = state.pendingDirection ?: direction
            val projected = projectPointOntoLineByPointAndDir(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            val start = Point3DBokorys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )

            state.segmentStartBokorys = start
            setProjectionPhase("Bokorys_directed_segment_end",state)
            return
        }

        "Bokorys_directed_segment_end" -> {
            val linePoint = state.pendingPoint1 ?: return
            val lineDir = state.pendingDirection ?: direction
            val start = state.segmentStartBokorys ?: return

            val projected = projectPointOntoLineByPointAndDir(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            val end = Point3DBokorys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )

            createSegmentBokorysFromPoints(
                state = state,
                start = start,
                end = end
            )

            state.segmentStartBokorys = null
            state.pendingPoint1 = null
            state.pendingDirection = null
            state.tempLine = null
            state.selectedLineForParallelBokorys = null
            state.selectedSegmentForParallelBokorys = null
            state.selectedLineForParallelAxo = null
            state.selectedSegmentForParallelAxo = null
            state.selectedLineForParallelAO = null
            state.selectedSegmentForParallelAO = null

            repeatCons(state)
            updateConstructionInfo(state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            resetStavu(state)
        }
    }
}
fun createSegmentBokorysFromPoints(
    state: MongeState,
    start: Point3DBokorys,
    end: Point3DBokorys
) {
    val style = state.currentLineStyleSettings

    val segment = Segment2DBokorys(
        start = start,
        end = end,
        name = "",
        localLineStyle = style.style,
        localStrokeWidth = style.strokeWidth,
        localColor = style.color,
        creationIndex = allocIndex(state)
    )

    start.parentSegment = segment
    end.parentSegment = segment

    if (state.pointsBokorys.none { it.y == start.y && it.z == start.z }) {
        state.pointsBokorys.add(start)
    }

    if (state.pointsBokorys.none { it.y == end.y && it.z == end.z }) {
        state.pointsBokorys.add(end)
    }

    state.segmentsBokorys.add(segment)
    commitSnapshot(state)
}