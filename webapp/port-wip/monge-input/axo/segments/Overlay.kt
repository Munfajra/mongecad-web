package monge.input.axo.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.classes.AxoOverlaySegment
import model.classes.TempSnapLine
import model.classes.TempSnapSpace
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.normalizedOrNull
import monge.input.axo.lines.perpendicular2D
import monge.input.axo.lines.pickOverlayReferenceFromCurrentHover
import monge.input.axo.lines.resolveOverlayReferenceDirectionAxo
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex

fun handleAOSegment(state: MongeState) {
    val basis = state.basis ?: return

    val logical = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

    when (state.constructionModifier) {
        ConstructionModifier.PARALLEL,
        ConstructionModifier.ORTHOGONAL -> {
            handleDirectedAOSegment(logical, state)
        }

        ConstructionModifier.NONE -> {
            if (state.pendingPoint1 == null) {
                state.pendingPoint1 = logical
            } else {
                val p1 = state.pendingPoint1 ?: return

                createAOSegmentFromPoints(
                    state = state,
                    start = p1,
                    end = logical
                )

                state.pendingPoint1 = null
            }
        }
    }
}

fun handleDirectedAOSegment(
    logical: Offset,
    state: MongeState
) {
    if (!hasOverlayReference(state)) {
        pickOverlayReferenceFromCurrentHover(state)

        if (hasOverlayReference(state)) {
            state.consInfo.value = "Umístěte pomocnou přímku"
            setProjectionPhase("ao_directed_segment_place_line", state)
        }

        return
    }

    val refDir = resolveOverlayReferenceDirectionAxo(state) ?: return

    val direction = when (state.constructionModifier) {
        ConstructionModifier.ORTHOGONAL ->
            perpendicular2D(refDir).normalizedOrNull() ?: return

        ConstructionModifier.PARALLEL ->
            refDir

        else -> return
    }

    when (state.projectionPhase) {
        "",
        "ao_directed_segment_place_line" -> {
            state.pendingPoint1 = logical
            state.pendingDirection = direction

            state.tempLine = TempSnapLine(
                point = logical,
                direction = direction,
                id = "temp_ao_segment_line",
                space = TempSnapSpace.AO_OVERLAY
            )

            setProjectionPhase("ao_directed_segment_start", state)
            state.consInfo.value = "Umístěte začátek úsečky"
            return
        }

        "ao_directed_segment_start" -> {
            val linePoint = state.pendingPoint1 ?: return
            val lineDir = state.pendingDirection ?: direction

            val projected = projectPointOntoLineByPointAndDir(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            state.pendingPoint2 = projected // pokud nemáš, použij pendingPoint2
            setProjectionPhase("ao_directed_segment_end", state)
            state.consInfo.value = "Umístěte konec úsečky"
            return
        }

        "ao_directed_segment_end" -> {
            val linePoint = state.pendingPoint1 ?: return
            val lineDir = state.pendingDirection ?: direction
            val start = state.pendingPoint2 ?: return

            val end = projectPointOntoLineByPointAndDir(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            createAOSegmentFromPoints(
                state = state,
                start = start,
                end = end
            )

            state.pendingPoint1 = null
            state.pendingDirection = null
            state.pendingPoint2 = null
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
fun createAOSegmentFromPoints(
    state: MongeState,
    start: Offset,
    end: Offset
) {
    val style = state.currentHelpLineStyleSettings

    val seg = AxoOverlaySegment(
        start = start,
        end = end,
        color = style.color,
        creationIndex = allocIndex(state),
        lineWidth = style.strokeWidth,
        lineStyle = style.style
    )

    state.axoOverlaySegments += seg
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    state.isNameConfirmed = false
    commitSnapshot(state)
    repeatCons(state)

    println("přidán segment pomocný $seg")
}