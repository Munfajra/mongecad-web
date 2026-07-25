package monge.input.axo.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.axo.AxoMode
import model.classes.Point3DPudorys
import model.classes.Segment2DPudorys
import model.classes.TempSnapLine
import model.classes.TempSnapSpace
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.pickOverlayReferenceFromCurrentHover
import monge.input.axo.lines.resolvePudorysDirectionAxo
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex

fun axoSegmentPudorys(
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
        mode = AxoMode.AXO_PUDORYS,
        axoModel = state.activeAxoModel
    ) ?: return

    if (isDirected) {
        handleDirectedSegmentPudorysAxo(logical, state)
        return
    }

    // původní obyčejná úsečka
    if (state.segmentStartPudorys == null) {
        val start = Point3DPudorys(
            logical.x,
            logical.y,
            name = "",
            isSegmentEndpoint = true,
            creationIndex = allocIndex(state)
        )

        state.segmentStartPudorys = start
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        state.consInfo.value = "Umístěte druhý bod úsečky"
    } else {
        createSegmentPudorysFromPoints(
            state = state,
            start = state.segmentStartPudorys!!,
            end = Point3DPudorys(
                logical.x,
                logical.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )
        )

        state.segmentStartPudorys = null
        repeatCons(state)
        updateConstructionInfo(state)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        resetStavu(state)
    }
}
fun handleDirectedSegmentPudorysAxo(
    logical: Offset,
    state: MongeState
) {
    if (!hasOverlayReference(state)) {
        pickOverlayReferenceFromCurrentHover(state)

        if (hasOverlayReference(state)) {
            state.consInfo.value = "Umístěte pomocnou přímku"
            setProjectionPhase("pudorys_directed_segment_place_line",state)
        }

        return
    }

    val isOrthogonal = state.constructionModifier == ConstructionModifier.ORTHOGONAL

    val direction = resolvePudorysDirectionAxo(
        state = state,
        wantPerpendicular = isOrthogonal
    ) ?: return

    when (state.projectionPhase) {
        "",
        "pudorys_directed_segment_place_line" -> {
            state.pendingPoint1 = logical
            state.pendingDirection = direction

            state.tempLine = TempSnapLine(
                point = logical,
                direction = direction,
                id = "temp",
                space = TempSnapSpace.PUDORYS
            )

            setProjectionPhase("pudorys_directed_segment_start", state)
            state.consInfo.value = "Umístěte začátek úsečky"
            return
        }
        "pudorys_directed_segment_start" -> {
            val linePoint = state.pendingPoint1  ?: return
            val lineDir = state.pendingDirection ?: direction
            val projected = projectPointOntoLineByPointAndDir(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            val start = Point3DPudorys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )

            state.segmentStartPudorys = start
            setProjectionPhase("pudorys_directed_segment_end",state)
            return
        }

        "pudorys_directed_segment_end" -> {
            val linePoint = state.pendingPoint1 ?: return
            val lineDir = state.pendingDirection ?: direction
            val start = state.segmentStartPudorys ?: return

            val projected = projectPointOntoLineByPointAndDir(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            val end = Point3DPudorys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )

            createSegmentPudorysFromPoints(
                state = state,
                start = start,
                end = end
            )

            state.segmentStartPudorys = null
            state.pendingPoint1 = null
            state.pendingDirection = null
            state.tempLine = null
            state.selectedLineForParallelPudorys = null
            state.selectedSegmentForParallelPudorys = null
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
fun createSegmentPudorysFromPoints(
    state: MongeState,
    start: Point3DPudorys,
    end: Point3DPudorys
) {
    val style = state.currentLineStyleSettings

    val segment = Segment2DPudorys(
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

    if (state.pointsPudorys.none { it.x == start.x && it.y == start.y }) {
        state.pointsPudorys.add(start)
    }

    if (state.pointsPudorys.none { it.x == end.x && it.y == end.y }) {
        state.pointsPudorys.add(end)
    }

    state.segmentsPudorys.add(segment)
    commitSnapshot(state)
}
fun projectPointOntoLineByPointAndDir(
    p: Offset,
    linePoint: Offset,
    lineDir: Offset
): Offset {
    val dLenSq = lineDir.x * lineDir.x + lineDir.y * lineDir.y
    if (dLenSq < 1e-6f) return linePoint

    val ap = p - linePoint
    val t = (ap.x * lineDir.x + ap.y * lineDir.y) / dLenSq

    return linePoint + lineDir * t
}