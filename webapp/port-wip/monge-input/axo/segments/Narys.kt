package monge.input.axo.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.axo.AxoMode
import model.classes.Point3DNarys
import model.classes.Segment2DNarys
import model.classes.TempSnapLine
import model.classes.TempSnapSpace
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.pickOverlayReferenceFromCurrentHover
import monge.input.axo.lines.resolveNarysDirectionAxo
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex

fun axoSegmentNarys(
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
        mode = AxoMode.AXO_NARYS,
        axoModel = state.activeAxoModel
    ) ?: return

    if (isDirected) {
        handleDirectedSegmentNarysAxo(logical, state)
        return
    }

    // původní obyčejná úsečka
    if (state.segmentStartNarys == null) {
        val start = Point3DNarys(
            logical.x,
            logical.y,
            name = "",
            isSegmentEndpoint = true,
            creationIndex = allocIndex(state)
        )

        state.segmentStartNarys = start
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        state.consInfo.value = "Umístěte druhý bod úsečky"
    } else {
        createSegmentNarysFromPoints(
            state = state,
            start = state.segmentStartNarys!!,
            end = Point3DNarys(
                logical.x,
                logical.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )
        )

        state.segmentStartNarys = null
        repeatCons(state)
        updateConstructionInfo(state)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        resetStavu(state)
    }
}
fun handleDirectedSegmentNarysAxo(
    logical: Offset,
    state: MongeState
) {
    if (!hasOverlayReference(state)) {
        pickOverlayReferenceFromCurrentHover(state)

        if (hasOverlayReference(state)) {
            state.consInfo.value = "Umístěte pomocnou přímku"
            setProjectionPhase("Narys_directed_segment_place_line",state)
        }

        return
    }

    val isOrthogonal = state.constructionModifier == ConstructionModifier.ORTHOGONAL

    val direction = resolveNarysDirectionAxo(
        state = state,
        wantPerpendicular = isOrthogonal
    ) ?: return

    when (state.projectionPhase) {
        "",
        "Narys_directed_segment_place_line" -> {
            state.pendingPoint1 = logical
            state.pendingDirection = direction

            state.tempLine = TempSnapLine(
                point = logical,
                direction = direction,
                id = "temp",
                space = TempSnapSpace.NARYS
            )

            setProjectionPhase("Narys_directed_segment_start", state)
            state.consInfo.value = "Umístěte začátek úsečky"
            return
        }
        "Narys_directed_segment_start" -> {
            val linePoint = state.pendingPoint1  ?: return
            val lineDir = state.pendingDirection ?: direction
            val projected = projectPointOntoLineByPointAndDir(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            val start = Point3DNarys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )

            state.segmentStartNarys = start
            setProjectionPhase("Narys_directed_segment_end",state)
            return
        }

        "Narys_directed_segment_end" -> {
            val linePoint = state.pendingPoint1 ?: return
            val lineDir = state.pendingDirection ?: direction
            val start = state.segmentStartNarys ?: return

            val projected = projectPointOntoLineByPointAndDir(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            val end = Point3DNarys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )

            createSegmentNarysFromPoints(
                state = state,
                start = start,
                end = end
            )

            state.segmentStartNarys = null
            state.pendingPoint1 = null
            state.pendingDirection = null
            state.tempLine = null
            state.selectedLineForParallelNarys = null
            state.selectedSegmentForParallelNarys = null
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
fun createSegmentNarysFromPoints(
    state: MongeState,
    start: Point3DNarys,
    end: Point3DNarys
) {
    val style = state.currentLineStyleSettings

    val segment = Segment2DNarys(
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

    if (state.pointsNarys.none { it.x == start.x && it.z == start.z }) {
        state.pointsNarys.add(start)
    }

    if (state.pointsNarys.none { it.x == end.x && it.z == end.z }) {
        state.pointsNarys.add(end)
    }

    state.segmentsNarys.add(segment)
    commitSnapshot(state)
}