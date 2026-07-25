package monge.input.axo.segments.segmentcomplete

import utils.withSuffixOnce
import utils.System
import dialogs.nameInput.withSuffixOnce
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.Point3D
import model.classes.*
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.linecomplete.ProjectionKind
import monge.input.axo.lines.linecomplete.projectionKindFromAxoMode
import monge.input.axo.lines.linecomplete.resolveCompletionSecondDirection
import monge.input.axo.lines.clearOverlayLineReferenceSelection
import monge.input.axo.lines.pickOverlayReferenceFromCurrentHover
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.axo.points.pointcomplete.computeXFromAxoOverlayPoint
import monge.input.axo.points.pointcomplete.computeYFromAxoOverlayPoint
import monge.input.axo.points.pointcomplete.computeZFromAxoOverlayPoint
import monge.input.axo.points.pointcomplete.createCompletionLineInPlaneFromAxoOverlay
import monge.input.segments.addSegment3DAndDetectSolids
import state.MongeState
import state.snapAxo.computeSnappedPointAxo
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex

data class PendingAxoSegmentCompletion(
    val firstProjectionId: String,
    val firstKind: ProjectionKind,
    val secondKind: ProjectionKind? = null
)

fun completeAxoSegmentFromPudorys(state: MongeState, seg: Segment2DPudorys) {
    clearOverlayLineReferenceSelection(state)
    state.pendingAxoSegmentCompletion = PendingAxoSegmentCompletion(seg.id, ProjectionKind.PUDORYS)
    state.completingSegmentSecondStart = null
    state.completingSegmentSecondEnd = null
    setProjectionPhase("axo_complete_segment_waiting_for_second_projection", state)
    updateAxoSegmentCompletionTempLine(state)
}

fun completeAxoSegmentFromNarys(state: MongeState, seg: Segment2DNarys) {
    clearOverlayLineReferenceSelection(state)
    state.pendingAxoSegmentCompletion = PendingAxoSegmentCompletion(seg.id, ProjectionKind.NARYS)
    state.completingSegmentSecondStart = null
    state.completingSegmentSecondEnd = null
    setProjectionPhase("axo_complete_segment_waiting_for_second_projection", state)
    updateAxoSegmentCompletionTempLine(state)
}

fun completeAxoSegmentFromBokorys(state: MongeState, seg: Segment2DBokorys) {
    clearOverlayLineReferenceSelection(state)
    state.pendingAxoSegmentCompletion = PendingAxoSegmentCompletion(seg.id, ProjectionKind.BOKORYS)
    state.completingSegmentSecondStart = null
    state.completingSegmentSecondEnd = null
    setProjectionPhase("axo_complete_segment_waiting_for_second_projection", state)
    updateAxoSegmentCompletionTempLine(state)
}

fun completeAxoSegmentFromAxo(state: MongeState, seg: Segment2DAxo) {
    clearOverlayLineReferenceSelection(state)
    state.pendingAxoSegmentCompletion = PendingAxoSegmentCompletion(seg.id, ProjectionKind.AXO)
    state.completingSegmentSecondStart = null
    state.completingSegmentSecondEnd = null
    setProjectionPhase("axo_complete_segment_waiting_for_second_projection", state)
    updateAxoSegmentCompletionTempLine(state)
}

fun handleAxoSegmentCompletionClick(state: MongeState) {
    if (
        state.projectionPhase != "axo_complete_segment_waiting_for_second_projection" &&
        state.projectionPhase != "axo_complete_segment_second_projection"
    ) return

    val pending = state.pendingAxoSegmentCompletion ?: return
    val currentKind = projectionKindFromAxoMode(state.axoMode) ?: return

    if (currentKind == pending.firstKind) return

    val effectiveSecondKind = pending.secondKind ?: currentKind
    if (pending.secondKind == null) {
        state.pendingAxoSegmentCompletion = pending.copy(secondKind = effectiveSecondKind)
        setProjectionPhase("axo_complete_segment_second_projection", state)
        updateAxoSegmentCompletionTempLine(state)
    }
    if (effectiveSecondKind != currentKind) return

    val logicalRaw = if (currentKind == ProjectionKind.AXO) {
        val basis = state.basis ?: return
        state.snappedPointLogical ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
    } else {
        getLogicalCursorAxo(
            snapped = state.snappedPointLogical,
            cursor = state.cursorPosition,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            flipX = false,
            flipY = false,
            mode = state.axoMode,
            axoModel = state.activeAxoModel
        ) ?: return
    }
    val logical = projectToTempLineIfNeeded(logicalRaw, state)

    if (
        state.projectionPhase == "axo_complete_segment_second_projection" &&
        state.completingSegmentSecondStart == null &&
        (state.constructionModifier == ConstructionModifier.PARALLEL ||
                state.constructionModifier == ConstructionModifier.ORTHOGONAL)
    ) {
        if (!hasOverlayReference(state)) {
            val savedTemp = state.tempLine
            state.tempLine = null
            state.snappedPointLogical = computeSnappedPointAxo(state)
            pickOverlayReferenceFromCurrentHover(state)
            state.tempLine = savedTemp
            updateAxoSegmentCompletionTempLine(state)
            val refreshedPending = state.pendingAxoSegmentCompletion
            if (refreshedPending != null && isForbiddenCompletionReference(state, refreshedPending)) {
                clearOverlayLineReferenceSelection(state)
                state.consInfo.value = "Jako vzor nelze použít doplňovanou úsečku. Vyber jinou přímku/úsečku."
                return
            }
            if (hasOverlayReference(state)) {
                state.consInfo.value = "Vzorek směru vybrán. Dalším klikem umísti průmět."
                return
            } else {
                state.consInfo.value = "Vyber vzorovou přímku nebo úsečku pro paralelní/kolmý směr."
                return
            }
        } else {
            val refreshedPending = state.pendingAxoSegmentCompletion
            if (refreshedPending != null && isForbiddenCompletionReference(state, refreshedPending)) {
                clearOverlayLineReferenceSelection(state)
                state.consInfo.value = "Jako vzor nelze použít doplňovanou úsečku. Vyber jinou přímku/úsečku."
                return
            }
        }

        val dir = resolveCompletionSecondDirection(
            state = state,
            kind = currentKind,
            modifier = state.constructionModifier
        ) ?: return

        val firstTemp = state.tempLine ?: return
        val startOnFirst = projectOnLine(logical, firstTemp.point, firstTemp.direction)

        state.completingSegmentSecondStart = startOnFirst
        updateAxoSegmentCompletionTempLine(state)

        val secondTemp = state.tempLine ?: run {
            state.completingSegmentSecondStart = null
            return
        }

        val endOnSecond = intersectInfiniteLines(
            p1 = startOnFirst,
            d1 = dir,
            p2 = secondTemp.point,
            d2 = secondTemp.direction
        )

        if (endOnSecond == null) {
            state.consInfo.value = "Nelze dopočítat druhý bod: směr je rovnoběžný s vodicí přímkou."
            state.completingSegmentSecondStart = null
            updateAxoSegmentCompletionTempLine(state)
            return
        }

        state.completingSegmentSecondEnd = endOnSecond
        finishAxoSegmentCompletion(state)
        clearOverlayLineReferenceSelection(state)
        state.constructionModifier = ConstructionModifier.NONE
        return
    }

    if (state.completingSegmentSecondStart == null) {
        state.completingSegmentSecondStart = logical
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        updateAxoSegmentCompletionTempLine(state)
        return
    }

    state.completingSegmentSecondEnd = logical
    finishAxoSegmentCompletion(state)
}

private fun projectOnLine(
    p: Offset,
    linePoint: Offset,
    lineDir: Offset
): Offset {
    val len2 = lineDir.x * lineDir.x + lineDir.y * lineDir.y
    if (len2 < 1e-6f) return p
    val v = p - linePoint
    val t = (v.x * lineDir.x + v.y * lineDir.y) / len2
    return linePoint + lineDir * t
}

private fun intersectInfiniteLines(
    p1: Offset,
    d1: Offset,
    p2: Offset,
    d2: Offset
): Offset? {
    val det = d1.x * d2.y - d1.y * d2.x
    if (kotlin.math.abs(det) < 1e-6f) return null
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    val t = (dx * d2.y - dy * d2.x) / det
    return p1 + d1 * t
}

private fun projectToTempLineIfNeeded(
    p: Offset,
    state: MongeState
): Offset {
    val temp = state.tempLine ?: return p
    val d = temp.direction
    val len2 = d.x * d.x + d.y * d.y
    if (len2 < 1e-6f) return p
    val v = p - temp.point
    val t = (v.x * d.x + v.y * d.y) / len2
    return temp.point + d * t
}

private fun finishAxoSegmentCompletion(state: MongeState) {
    val pending = state.pendingAxoSegmentCompletion ?: return
    val secondKind = pending.secondKind ?: return
    val secondA = state.completingSegmentSecondStart ?: return
    val secondB = state.completingSegmentSecondEnd ?: return
    val basis = state.basis

    val styleSource = originalSegmentProjection(state, pending) ?: return cancelAxoSegmentCompletion(state)
    val firstName = styleSource.name ?: "s"

    val rawName = firstName
        .removeSuffix("₁")
        .removeSuffix("₂")
        .removeSuffix("₃")
        .removeSuffix("ₐ")

    val xyz = when (pending.firstKind) {
        ProjectionKind.PUDORYS -> {
            val seg = state.segmentsPudorys.firstOrNull { it.id == pending.firstProjectionId } ?: return cancelAxoSegmentCompletion(state)
            when (secondKind) {
                ProjectionKind.NARYS -> {
                    listOf(
                        Triple(seg.start.x, seg.start.y, secondA.y),
                        Triple(seg.end.x, seg.end.y, secondB.y)
                    )
                }
                ProjectionKind.BOKORYS -> {
                    listOf(
                        Triple(seg.start.x, seg.start.y, secondA.y),
                        Triple(seg.end.x, seg.end.y, secondB.y)
                    )
                }
                ProjectionKind.AXO -> {
                    if (basis == null) return
                    listOf(
                        Triple(seg.start.x, seg.start.y, computeZFromAxoOverlayPoint(secondA, basis, seg.start.x, seg.start.y)),
                        Triple(seg.end.x, seg.end.y, computeZFromAxoOverlayPoint(secondB, basis, seg.end.x, seg.end.y))
                    )
                }
                ProjectionKind.PUDORYS -> return
            }
        }
        ProjectionKind.NARYS -> {
            val seg = state.segmentsNarys.firstOrNull { it.id == pending.firstProjectionId } ?: return cancelAxoSegmentCompletion(state)
            when (secondKind) {
                ProjectionKind.PUDORYS -> {
                    listOf(
                        Triple(seg.start.x, secondA.y, seg.start.z),
                        Triple(seg.end.x, secondB.y, seg.end.z)
                    )
                }
                ProjectionKind.BOKORYS -> {
                    listOf(
                        Triple(seg.start.x, secondA.x, seg.start.z),
                        Triple(seg.end.x, secondB.x, seg.end.z)
                    )
                }
                ProjectionKind.AXO -> {
                    if (basis == null) return
                    listOf(
                        Triple(seg.start.x, computeYFromAxoOverlayPoint(secondA, basis, seg.start.x, seg.start.z), seg.start.z),
                        Triple(seg.end.x, computeYFromAxoOverlayPoint(secondB, basis, seg.end.x, seg.end.z), seg.end.z)
                    )
                }
                ProjectionKind.NARYS -> return
            }
        }
        ProjectionKind.BOKORYS -> {
            val seg = state.segmentsBokorys.firstOrNull { it.id == pending.firstProjectionId } ?: return cancelAxoSegmentCompletion(state)
            when (secondKind) {
                ProjectionKind.PUDORYS -> {
                    listOf(
                        Triple(secondA.x, seg.start.y, seg.start.z),
                        Triple(secondB.x, seg.end.y, seg.end.z)
                    )
                }
                ProjectionKind.NARYS -> {
                    listOf(
                        Triple(secondA.x, seg.start.y, seg.start.z),
                        Triple(secondB.x, seg.end.y, seg.end.z)
                    )
                }
                ProjectionKind.AXO -> {
                    if (basis == null) return
                    listOf(
                        Triple(computeXFromAxoOverlayPoint(secondA, basis, seg.start.y, seg.start.z), seg.start.y, seg.start.z),
                        Triple(computeXFromAxoOverlayPoint(secondB, basis, seg.end.y, seg.end.z), seg.end.y, seg.end.z)
                    )
                }
                ProjectionKind.BOKORYS -> return
            }
        }
        ProjectionKind.AXO -> {
            val seg = state.segmentsAxo.firstOrNull { it.id == pending.firstProjectionId } ?: return cancelAxoSegmentCompletion(state)
            if (basis == null) return
            when (secondKind) {
                ProjectionKind.PUDORYS -> {
                    listOf(
                        Triple(secondA.x, secondA.y, computeZFromAxoOverlayPoint(Offset(seg.start.x, seg.start.y), basis, secondA.x, secondA.y)),
                        Triple(secondB.x, secondB.y, computeZFromAxoOverlayPoint(Offset(seg.end.x, seg.end.y), basis, secondB.x, secondB.y))
                    )
                }
                ProjectionKind.NARYS -> {
                    listOf(
                        Triple(secondA.x, computeYFromAxoOverlayPoint(Offset(seg.start.x, seg.start.y), basis, secondA.x, secondA.y), secondA.y),
                        Triple(secondB.x, computeYFromAxoOverlayPoint(Offset(seg.end.x, seg.end.y), basis, secondB.x, secondB.y), secondB.y)
                    )
                }
                ProjectionKind.BOKORYS -> {
                    listOf(
                        Triple(computeXFromAxoOverlayPoint(Offset(seg.start.x, seg.start.y), basis, secondA.x, secondA.y), secondA.x, secondA.y),
                        Triple(computeXFromAxoOverlayPoint(Offset(seg.end.x, seg.end.y), basis, secondB.x, secondB.y), secondB.x, secondB.y)
                    )
                }
                ProjectionKind.AXO -> return
            }
        }
    }

    val pointA3D = Point3D(xyz[0].first, xyz[0].second, xyz[0].third, name = "", creationIndex = allocIndex(state))
    val pointB3D = Point3D(xyz[1].first, xyz[1].second, xyz[1].third, name = "", creationIndex = allocIndex(state))
    val seg3D = Segment3D(
        start = pointA3D,
        end = pointB3D,
        name = rawName,
        color = styleSource.color,
        lineStyle = styleSource.lineStyle,
        strokeWidth = styleSource.strokeWidth,
        creationIndex = allocIndex(state)
    )

    var pA = Point3DPudorys(pointA3D.x, pointA3D.y, name = rawName.withSuffixOnce("₁"), isSegmentEndpoint = true, parent = pointA3D)
    var pB = Point3DPudorys(pointB3D.x, pointB3D.y, name = rawName.withSuffixOnce("₁"), isSegmentEndpoint = true, parent = pointB3D)
    var nA = Point3DNarys(pointA3D.x, pointA3D.z, name = rawName.withSuffixOnce("₂"), isSegmentEndpoint = true, parent = pointA3D)
    var nB = Point3DNarys(pointB3D.x, pointB3D.z, name = rawName.withSuffixOnce("₂"), isSegmentEndpoint = true, parent = pointB3D)
    var bA = Point3DBokorys(pointA3D.y, pointA3D.z, name = rawName.withSuffixOnce("₃"), isSegmentEndpoint = true, parent = pointA3D)
    var bB = Point3DBokorys(pointB3D.y, pointB3D.z, name = rawName.withSuffixOnce("₃"), isSegmentEndpoint = true, parent = pointB3D)

    when (pending.firstKind) {
        ProjectionKind.PUDORYS -> {
            val first = state.segmentsPudorys.firstOrNull { it.id == pending.firstProjectionId }
            if (first != null) {
                pA = first.start
                pB = first.end
                pA.parent = pointA3D
                pB.parent = pointB3D
            }
        }
        ProjectionKind.NARYS -> {
            val first = state.segmentsNarys.firstOrNull { it.id == pending.firstProjectionId }
            if (first != null) {
                nA = first.start
                nB = first.end
                nA.parent = pointA3D
                nB.parent = pointB3D
            }
        }
        ProjectionKind.BOKORYS -> {
            val first = state.segmentsBokorys.firstOrNull { it.id == pending.firstProjectionId }
            if (first != null) {
                bA = first.start
                bB = first.end
                bA.parent = pointA3D
                bB.parent = pointB3D
            }
        }
        ProjectionKind.AXO -> {
            val first = state.segmentsAxo.firstOrNull { it.id == pending.firstProjectionId }
            if (first != null) {
                first.start.parent = pointA3D
                first.end.parent = pointB3D
            }
        }
    }

    val segP = Segment2DPudorys(
        start = pA,
        end = pB,
        name = rawName.withSuffixOnce("₁"),
        parent = seg3D,
        localLineStyle = styleSource.lineStyle,
        creationIndex = allocIndex(state)
    )
    val segN = Segment2DNarys(
        start = nA,
        end = nB,
        name = rawName.withSuffixOnce("₂"),
        parent = seg3D,
        localLineStyle = styleSource.lineStyle,
        creationIndex = allocIndex(state)
    )
    val segB = Segment2DBokorys(
        start = bA,
        end = bB,
        name = rawName.withSuffixOnce("₃"),
        parent = seg3D,
        localLineStyle = styleSource.lineStyle,
        creationIndex = allocIndex(state)
    )

    val oldAxo = state.segmentsAxo.firstOrNull { it.id == pending.firstProjectionId }
    val segA = if (pending.firstKind == ProjectionKind.AXO && oldAxo != null) {
        oldAxo.start.parent = pointA3D
        oldAxo.end.parent = pointB3D
        oldAxo.copy(
            parent = seg3D,
            name = rawName.withSuffixOnce("ₐ"),
            localLineStyle = styleSource.lineStyle
        )
    } else {
        basis?.let {
            val aStart = it.ex * pointA3D.x + it.ey * pointA3D.y + it.ez * pointA3D.z
            val aEnd = it.ex * pointB3D.x + it.ey * pointB3D.y + it.ez * pointB3D.z
            Segment2DAxo(
                start = Point3DAxo(aStart.x, aStart.y, name = rawName.withSuffixOnce("ₐ"), isSegmentEndpoint = true, parent = pointA3D),
                end = Point3DAxo(aEnd.x, aEnd.y, name = rawName.withSuffixOnce("ₐ"), isSegmentEndpoint = true, parent = pointB3D),
                name = rawName.withSuffixOnce("ₐ"),
                parent = seg3D,
                localLineStyle = styleSource.lineStyle,
                creationIndex = allocIndex(state)
            )
        }
    }

    applyCompletedSegmentProjectionVisibility(
        visibleKinds = completedVisibleKinds(state, pending),
        pudorys = segP,
        narys = segN,
        bokorys = segB,
        axo = segA
    )

    pA.parentSegment = segP
    pB.parentSegment = segP
    nA.parentSegment = segN
    nB.parentSegment = segN
    bA.parentSegment = segB
    bB.parentSegment = segB
    segA?.let {
        it.start.parentSegment = it
        it.end.parentSegment = it
    }

    syncPointById(state.pointsPudorys, pA)
    syncPointById(state.pointsPudorys, pB)
    syncPointById(state.pointsNarys, nA)
    syncPointById(state.pointsNarys, nB)
    syncPointById(state.pointsBokorys, bA)
    syncPointById(state.pointsBokorys, bB)
    segA?.let {
        syncPointById(state.pointsAxo, it.start)
        syncPointById(state.pointsAxo, it.end)
    }

    replaceOrAddProjection(state, pending.firstKind, pending.firstProjectionId, segP, segN, segB, segA)
    if (pending.firstKind != ProjectionKind.PUDORYS) state.segmentsPudorys.add(segP)
    if (pending.firstKind != ProjectionKind.NARYS) state.segmentsNarys.add(segN)
    if (pending.firstKind != ProjectionKind.BOKORYS) state.segmentsBokorys.add(segB)
    if (pending.firstKind != ProjectionKind.AXO && segA != null) state.segmentsAxo.add(segA)
    state.sharedPoints3D.add(pointA3D)
    state.sharedPoints3D.add(pointB3D)
    if (pending.firstKind != ProjectionKind.PUDORYS) {
        state.pointsPudorys.add(pA); state.pointsPudorys.add(pB)
    }
    if (pending.firstKind != ProjectionKind.NARYS) {
        state.pointsNarys.add(nA); state.pointsNarys.add(nB)
    }
    if (pending.firstKind != ProjectionKind.BOKORYS) {
        state.pointsBokorys.add(bA); state.pointsBokorys.add(bB)
    }
    if (pending.firstKind != ProjectionKind.AXO && segA != null) {
        state.pointsAxo.add(segA.start); state.pointsAxo.add(segA.end)
    }
    addSegment3DAndDetectSolids(state, seg3D)
    commitSnapshot(state)
    cancelAxoSegmentCompletion(state)
    resetStavu(state)
}

private fun replaceOrAddProjection(
    state: MongeState,
    firstKind: ProjectionKind,
    firstId: String,
    segP: Segment2DPudorys,
    segN: Segment2DNarys,
    segB: Segment2DBokorys,
    segA: Segment2DAxo?
) {
    when (firstKind) {
        ProjectionKind.PUDORYS -> {
            val i = state.segmentsPudorys.indexOfFirst { it.id == firstId }
            if (i >= 0) state.segmentsPudorys[i] = segP else state.segmentsPudorys.add(segP)
        }
        ProjectionKind.NARYS -> {
            val i = state.segmentsNarys.indexOfFirst { it.id == firstId }
            if (i >= 0) state.segmentsNarys[i] = segN else state.segmentsNarys.add(segN)
        }
        ProjectionKind.BOKORYS -> {
            val i = state.segmentsBokorys.indexOfFirst { it.id == firstId }
            if (i >= 0) state.segmentsBokorys[i] = segB else state.segmentsBokorys.add(segB)
        }
        ProjectionKind.AXO -> {
            val a = segA ?: return
            val i = state.segmentsAxo.indexOfFirst { it.id == firstId }
            if (i >= 0) state.segmentsAxo[i] = a else state.segmentsAxo.add(a)
        }
    }
}

private fun originalSegmentProjection(
    state: MongeState,
    pending: PendingAxoSegmentCompletion
): Segment2DProjection? {
    return when (pending.firstKind) {
        ProjectionKind.PUDORYS -> state.segmentsPudorys.firstOrNull { it.id == pending.firstProjectionId }
        ProjectionKind.NARYS -> state.segmentsNarys.firstOrNull { it.id == pending.firstProjectionId }
        ProjectionKind.BOKORYS -> state.segmentsBokorys.firstOrNull { it.id == pending.firstProjectionId }
        ProjectionKind.AXO -> state.segmentsAxo.firstOrNull { it.id == pending.firstProjectionId }
    }
}

private fun completedVisibleKinds(
    state: MongeState,
    pending: PendingAxoSegmentCompletion
): Set<ProjectionKind> {
    return setOf(
        pending.firstKind,
        pending.secondKind ?: projectionKindFromAxoMode(state.axoMode) ?: pending.firstKind
    )
}

private fun applyCompletedSegmentProjectionVisibility(
    visibleKinds: Set<ProjectionKind>,
    pudorys: Segment2DPudorys,
    narys: Segment2DNarys,
    bokorys: Segment2DBokorys,
    axo: Segment2DAxo?
) {
    pudorys.setAxoVisibility(ProjectionKind.PUDORYS in visibleKinds)
    narys.setAxoVisibility(ProjectionKind.NARYS in visibleKinds)
    bokorys.setAxoVisibility(ProjectionKind.BOKORYS in visibleKinds)
    axo?.setAxoVisibility(ProjectionKind.AXO in visibleKinds)
}

private fun Segment2DPudorys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
    start.showInAxoInitial = visible
    start.showInAxo = visible
    end.showInAxoInitial = visible
    end.showInAxo = visible
}

private fun Segment2DNarys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
    start.showInAxoInitial = visible
    start.showInAxo = visible
    end.showInAxoInitial = visible
    end.showInAxo = visible
}

private fun Segment2DBokorys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
    start.showInAxoInitial = visible
    start.showInAxo = visible
    end.showInAxoInitial = visible
    end.showInAxo = visible
}

private fun Segment2DAxo.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
    start.showInAxoInitial = visible
    start.showInAxo = visible
    end.showInAxoInitial = visible
    end.showInAxo = visible
}

fun cancelAxoSegmentCompletion(state: MongeState) {
    state.pendingAxoSegmentCompletion = null
    state.completingSegmentSecondStart = null
    state.completingSegmentSecondEnd = null
    state.tempLine = null
    clearOverlayLineReferenceSelection(state)
    state.consInfo.value = ""
    setProjectionPhase("pudorys_start", state)
}

private fun isForbiddenCompletionReference(
    state: MongeState,
    pending: PendingAxoSegmentCompletion
): Boolean {
    if (state.constructionModifier == ConstructionModifier.PARALLEL) return false
    return when (pending.firstKind) {
        ProjectionKind.PUDORYS -> state.selectedSegmentForParallelPudorys?.id == pending.firstProjectionId
        ProjectionKind.NARYS -> state.selectedSegmentForParallelNarys?.id == pending.firstProjectionId
        ProjectionKind.BOKORYS -> state.selectedSegmentForParallelBokorys?.id == pending.firstProjectionId
        ProjectionKind.AXO -> state.selectedSegmentForParallelAxo?.id == pending.firstProjectionId
    }
}

fun updateAxoSegmentCompletionTempLine(state: MongeState) {
    val pending = state.pendingAxoSegmentCompletion ?: return
    val currentKind = projectionKindFromAxoMode(state.axoMode) ?: return
    val secondKind = pending.secondKind ?: currentKind
    if (secondKind == pending.firstKind) {
        state.tempLine = null
        return
    }
    if (currentKind != secondKind) {
        state.tempLine = null
        return
    }

    val endpoint = if (state.completingSegmentSecondStart == null) 0 else 1
    state.tempLine = computeAxoSegmentCompletionTempLine(state, pending, secondKind, endpoint)
}

fun computeAxoSegmentCompletionTempLine(
    state: MongeState,
    pending: PendingAxoSegmentCompletion,
    secondKind: ProjectionKind,
    endpoint: Int
): TempSnapLine? {
    return when (pending.firstKind) {
        ProjectionKind.PUDORYS -> {
            val s = state.segmentsPudorys.firstOrNull { it.id == pending.firstProjectionId } ?: return null
            val p = if (endpoint == 0) s.start else s.end
            when (secondKind) {
                ProjectionKind.NARYS -> TempSnapLine(point = Offset(p.x, 0f), direction = Offset(0f, 1f), space = TempSnapSpace.NARYS)
                ProjectionKind.BOKORYS -> TempSnapLine(point = Offset(p.y, 0f), direction = Offset(0f, 1f), space = TempSnapSpace.BOKORYS)
                ProjectionKind.AXO -> {
                    val b = state.basis ?: return null
                    TempSnapLine(point = b.ex * p.x + b.ey * p.y, direction = b.ez, space = TempSnapSpace.AO_OVERLAY)
                }
                else -> null
            }
        }
        ProjectionKind.NARYS -> {
            val s = state.segmentsNarys.firstOrNull { it.id == pending.firstProjectionId } ?: return null
            val p = if (endpoint == 0) s.start else s.end
            when (secondKind) {
                ProjectionKind.PUDORYS -> TempSnapLine(point = Offset(p.x, 0f), direction = Offset(0f, 1f), space = TempSnapSpace.PUDORYS)
                ProjectionKind.BOKORYS -> TempSnapLine(point = Offset(0f, p.z), direction = Offset(1f, 0f), space = TempSnapSpace.BOKORYS)
                ProjectionKind.AXO -> {
                    val b = state.basis ?: return null
                    TempSnapLine(point = b.ex * p.x + b.ez * p.z, direction = b.ey, space = TempSnapSpace.AO_OVERLAY)
                }
                else -> null
            }
        }
        ProjectionKind.BOKORYS -> {
            val s = state.segmentsBokorys.firstOrNull { it.id == pending.firstProjectionId } ?: return null
            val p = if (endpoint == 0) s.start else s.end
            when (secondKind) {
                ProjectionKind.PUDORYS -> TempSnapLine(point = Offset(0f, p.y), direction = Offset(1f, 0f), space = TempSnapSpace.PUDORYS)
                ProjectionKind.NARYS -> TempSnapLine(point = Offset(0f, p.z), direction = Offset(1f, 0f), space = TempSnapSpace.NARYS)
                ProjectionKind.AXO -> {
                    val b = state.basis ?: return null
                    TempSnapLine(point = b.ey * p.y + b.ez * p.z, direction = b.ex, space = TempSnapSpace.AO_OVERLAY)
                }
                else -> null
            }
        }
        ProjectionKind.AXO -> {
            val s = state.segmentsAxo.firstOrNull { it.id == pending.firstProjectionId } ?: return null
            val p = if (endpoint == 0) s.start else s.end
            val b = state.basis ?: return null
            val local = Offset(p.x, p.y)
            when (secondKind) {
                ProjectionKind.PUDORYS -> {
                    val solved = createCompletionLineInPlaneFromAxoOverlay(
                        overlayLocal = local,
                        planeA = b.ex,
                        planeB = b.ey,
                        freeAxis = b.ez
                    ) ?: return null
                    TempSnapLine(point = solved.point, direction = solved.direction, space = TempSnapSpace.PUDORYS)
                }
                ProjectionKind.NARYS -> {
                    val solved = createCompletionLineInPlaneFromAxoOverlay(
                        overlayLocal = local,
                        planeA = b.ex,
                        planeB = b.ez,
                        freeAxis = b.ey
                    ) ?: return null
                    TempSnapLine(point = solved.point, direction = solved.direction, space = TempSnapSpace.NARYS)
                }
                ProjectionKind.BOKORYS -> {
                    val solved = createCompletionLineInPlaneFromAxoOverlay(
                        overlayLocal = local,
                        planeA = b.ey,
                        planeB = b.ez,
                        freeAxis = b.ex
                    ) ?: return null
                    TempSnapLine(point = solved.point, direction = solved.direction, space = TempSnapSpace.BOKORYS)
                }
                else -> null
            }
        }
    }
}

private fun <T> syncPointById(list: MutableList<T>, point: T) where T : Point2DProjection {
    val idx = list.indexOfFirst { it.id == point.id }
    if (idx >= 0) list[idx] = point
}
