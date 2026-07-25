package monge.input.axo.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.classes.Point3DAxo
import model.classes.Segment2DAxo
import model.classes.TempSnapLine
import model.classes.TempSnapSpace
import monge.input.axo.lines.*
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.quadrics.detectQuadricSurfaceAfter2DSegmentAdd
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex

fun handleAxoSegment(state: MongeState) {
    val basis = state.basis ?: return

    val logical = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

    when (state.constructionModifier) {
        ConstructionModifier.PARALLEL,
        ConstructionModifier.ORTHOGONAL -> {
            handleDirectedAxoSegment(logical, state)
        }

        ConstructionModifier.NONE -> {
            if (state.pendingAxoPoint1 == null) {
                state.pendingAxoPoint1 = Point3DAxo(
                    x=logical.x,
                    y=logical.y,
                )
            } else {
                val p1 = state.pendingAxoPoint1 ?: return
                val p2 = Point3DAxo(logical.x,logical.y)

                createAxoSegmentFromPoints(
                    state = state,
                    start = p1,
                    end = p2
                )

                state.pendingAxoPoint1 = null
            }
        }
    }
}

fun handleDirectedAxoSegment(
    logical: Offset,
    state: MongeState
) {
    if (!hasOverlayReference(state)) {
        pickOverlayReferenceFromCurrentHover(state)

        if (hasOverlayReference(state)) {
            state.consInfo.value = "Umístěte pomocnou přímku"
            setProjectionPhase("axo_directed_segment_place_line", state)
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
        "axo_directed_segment_place_line" -> {
            state.pendingAxoPoint1 = Point3DAxo(
                x=logical.x,
                y=logical.y,
            )
            state.pendingDirection = direction

            state.tempLine = TempSnapLine(
                point = logical,
                direction = direction,
                id = "temp_axo_segment_line",
                space = TempSnapSpace.AO_OVERLAY
            )

            setProjectionPhase("axo_directed_segment_start", state)
            state.consInfo.value = "Umístěte začátek úsečky"
            return
        }

        "axo_directed_segment_start" -> {
            val linePoint = state.pendingAxoPoint1 ?: return
            val lineDir = state.pendingDirection ?: direction

            val projected = projectPointOntoLineByPointAndDirAxo(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            state.pendingAxoPoint2 = projected // pokud nemáš, použij pendingPoint2
            setProjectionPhase("axo_directed_segment_end", state)
            state.consInfo.value = "Umístěte konec úsečky"
            return
        }

        "axo_directed_segment_end" -> {
            val linePoint = state.pendingAxoPoint1 ?: return
            val lineDir = state.pendingDirection ?: direction
            val start = state.pendingAxoPoint2 ?: return

            val end = projectPointOntoLineByPointAndDirAxo(
                p = logical,
                linePoint = linePoint,
                lineDir = lineDir
            )

            createAxoSegmentFromPoints(
                state = state,
                start = start,
                end = end
            )

            state.pendingAxoPoint1 = null
            state.pendingDirection = null
            state.pendingAxoPoint2 = null
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
fun createAxoSegmentFromPoints(
    state: MongeState,
    start: Point3DAxo,
    end:Point3DAxo
) {
    val style = state.currentLineStyleSettings

    val seg = Segment2DAxo(
        start = start,
        end = end,
        localColor = style.color,
        name = "",
        creationIndex = allocIndex(state),
        localStrokeWidth = style.strokeWidth,
        localLineStyle = style.style
    )

    start.isSegmentEndpoint = true
    end.isSegmentEndpoint = true
    start.parentSegment = seg
    end.parentSegment = seg

    if (state.pointsAxo.none { it.id == start.id }) state.pointsAxo.add(start)
    if (state.pointsAxo.none { it.id == end.id }) state.pointsAxo.add(end)

    state.segmentsAxo += seg
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    state.isNameConfirmed = false
    detectQuadricSurfaceAfter2DSegmentAdd(state)
    commitSnapshot(state)
    repeatCons(state)

    println("přidán segment pomocný $seg")
}
fun projectPointOntoLineByPointAndDirAxo(
    p: Offset,
    linePoint: Point3DAxo,
    lineDir: Offset
): Point3DAxo {
    val dLenSq = lineDir.x * lineDir.x + lineDir.y * lineDir.y
    if (dLenSq < 1e-6f) return linePoint

    val ap = p - Offset(linePoint.x, linePoint.y)
    val t = (ap.x * lineDir.x + ap.y * lineDir.y) / dLenSq
    val res = Offset(linePoint.x, linePoint.y) + lineDir * t
    return Point3DAxo(x=res.x, y=res.y)
}
