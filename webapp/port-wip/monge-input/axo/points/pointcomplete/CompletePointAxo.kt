package monge.input.axo.points.pointcomplete

import utils.withSuffixOnce
import dialogs.nameInput.withSuffixOnce
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.Point3D
import model.axo.AxoMode
import model.classes.*
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.linecomplete.ProjectionKind
import monge.input.axo.lines.linecomplete.defaultAxoModeForCompletionTarget
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import kotlin.math.abs

data class PendingAxoPointCompletionFromAxo(
    val axoPointId: String
)
fun completeAxoPointA(state: MongeState, pt: Point3DAxo) {
    state.axoMode = defaultAxoModeForCompletionTarget(ProjectionKind.AXO)

    state.pendingAxoPointCompletionFromAxo =
        PendingAxoPointCompletionFromAxo(
            axoPointId = pt.id
        )

    state.tempLine = createTempSnapLineForAxoPointA(
        state = state,
        pt = pt,
        mode = state.axoMode
    )

    setProjectionPhase("axo_complete_point_from_axo", state)
}
data class SolvedPlanePoint(
    val point: Offset,
    val direction: Offset
)

fun solve2x2Coordinates(
    v: Offset,
    a: Offset,
    b: Offset
): Offset? {
    val det = a.x * b.y - a.y * b.x
    if (abs(det) < 1e-6f) return null

    val u = (v.x * b.y - v.y * b.x) / det
    val w = (a.x * v.y - a.y * v.x) / det

    return Offset(u, w)
}

fun createCompletionLineInPlaneFromAxoOverlay(
    overlayLocal: Offset,
    planeA: Offset,
    planeB: Offset,
    freeAxis: Offset
): SolvedPlanePoint? {
    val base = solve2x2Coordinates(
        v = overlayLocal,
        a = planeA,
        b = planeB
    ) ?: return null

    val dir = solve2x2Coordinates(
        v = -freeAxis,
        a = planeA,
        b = planeB
    ) ?: return null

    if (dir.getDistanceSquared() < 1e-6f) return null

    return SolvedPlanePoint(
        point = base,
        direction = dir
    )
}
fun createTempSnapLineForAxoPointA(
    state: MongeState,
    pt: Point3DAxo,
    mode: AxoMode
): TempSnapLine? {
    val basis = state.basis ?: return null
    val axoLocal = Offset(pt.x, pt.y)

    return when (mode) {

        AxoMode.AXO_PUDORYS -> {
            // půdorys = (x, y), volná souřadnice je z
            val solved = createCompletionLineInPlaneFromAxoOverlay(
                overlayLocal = axoLocal,
                planeA = basis.ex,
                planeB = basis.ey,
                freeAxis = basis.ez
            ) ?: return null

            TempSnapLine(
                space = TempSnapSpace.PUDORYS,
                point = solved.point,
                direction = solved.direction
            )
        }

        AxoMode.AXO_NARYS -> {
            // nárys = (x, z), volná souřadnice je y
            val solved = createCompletionLineInPlaneFromAxoOverlay(
                overlayLocal = axoLocal,
                planeA = basis.ex,
                planeB = basis.ez,
                freeAxis = basis.ey
            ) ?: return null

            TempSnapLine(
                space = TempSnapSpace.NARYS,
                point = solved.point,
                direction = solved.direction
            )
        }

        AxoMode.AXO_BOKORYS -> {
            // bokorys = (y, z), volná souřadnice je x
            val solved = createCompletionLineInPlaneFromAxoOverlay(
                overlayLocal = axoLocal,
                planeA = basis.ey,
                planeB = basis.ez,
                freeAxis = basis.ex
            ) ?: return null

            TempSnapLine(
                space = TempSnapSpace.BOKORYS,
                point = solved.point,
                direction = solved.direction
            )
        }

        AxoMode.NORMAL_2D -> {
            null
        }

        else -> null
    }
}
fun finishAxoPointACompletion(
    state: MongeState
) {
    if (state.projectionPhase != "axo_complete_point_from_axo") return

    val logical = getLogicalCursorAxo(
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

    val pending = state.pendingAxoPointCompletionFromAxo ?: return

    val axo = state.pointsAxo
        .firstOrNull { it.id == pending.axoPointId }
        ?: return cancelAxoPointACompletion(state)

    val basis = state.basis ?: return

    val axoLocal = Offset(axo.x, axo.y)

    val xyz = when (state.axoMode) {

        AxoMode.AXO_PUDORYS -> {
            // logical = (x, y)
            val x = logical.x
            val y = logical.y

            val z = computeZFromAxoOverlayPoint(
                overlayLocal = axoLocal,
                basis = basis,
                x = x,
                y = y
            )

            Triple(x, y, z)
        }

        AxoMode.AXO_NARYS -> {
            // logical = (x, z)
            val x = logical.x
            val z = logical.y

            val y = computeYFromAxoOverlayPoint(
                overlayLocal = axoLocal,
                basis = basis,
                x = x,
                z = z
            )

            Triple(x, y, z)
        }

        AxoMode.AXO_BOKORYS -> {
            // logical = (y, z)
            val y = logical.x
            val z = logical.y

            val x = computeXFromAxoOverlayPoint(
                overlayLocal = axoLocal,
                basis = basis,
                y = y,
                z = z
            )

            Triple(x, y, z)
        }

        else -> return
    }

    createCompletedPoint3DFromAxo(
        state = state,
        axoPt = axo,
        x = xyz.first,
        y = xyz.second,
        z = xyz.third
    )

    cancelAxoPointACompletion(state)
}
fun cancelAxoPointACompletion(state: MongeState) {
    state.pendingAxoPointCompletionFromAxo = null
    state.tempLine = null

    setProjectionPhase("pudorys_start", state)
    resetStavu(state)
}
fun createCompletedPoint3DFromAxo(
    state: MongeState,
    axoPt: Point3DAxo,
    x: Float,
    y: Float,
    z: Float
) {
    val rawName = pointBaseName(axoPt, "ₐ")

    val point3D = Point3D(
        x = x,
        y = y,
        z = z,
        name = rawName,
        color = pointColor(axoPt),
        width = pointWidth(axoPt),
        superscript = pointSuperscript(axoPt),
        creationIndex = allocIndex(state)
    )

    state.sharedPoints3D.add(point3D)

    val newAxo = axoPt.copy(
        x = axoPt.x,
        y = axoPt.y,
        parent = point3D,
        name = rawName.withSuffixOnce("ₐ")
    )

    val newPudorys = Point3DPudorys(
        x = x,
        y = y,
        name = rawName.withSuffixOnce("₁"),
        parent = point3D,
        creationIndex = allocIndex(state)
    )

    val newNarys = Point3DNarys(
        x = x,
        z = z,
        name = rawName.withSuffixOnce("₂"),
        parent = point3D,
        creationIndex = allocIndex(state)
    )

    val newBokorys = Point3DBokorys(
        y = y,
        z = z,
        name = rawName.withSuffixOnce("₃"),
        parent = point3D,
        creationIndex = allocIndex(state)
    )

    applyCompletedPointProjectionVisibility(
        visibleKinds = setOfNotNull(ProjectionKind.AXO, pointCompletionTargetKind(state.axoMode)),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replaceAxoPointKeepingPosition(
        state = state,
        oldPoint = axoPt,
        newPoint = newAxo
    )

    state.pointsPudorys.add(newPudorys)
    state.pointsNarys.add(newNarys)
    state.pointsBokorys.add(newBokorys)

    commitSnapshot(state)
    cancelAxoPointACompletion(state)
    resetStavu(state)
}
fun replaceAxoPointKeepingPosition(
    state: MongeState,
    oldPoint: Point3DAxo,
    newPoint: Point3DAxo
) {
    val index = state.pointsAxo.indexOfFirst { it.id == oldPoint.id }

    if (index >= 0) {
        state.pointsAxo[index] = newPoint
    } else {
        state.pointsAxo.add(newPoint)
    }
}
fun refreshAxoPointACompletionTempLine(state: MongeState) {
    if (state.projectionPhase != "axo_complete_point_from_axo") return

    val pending = state.pendingAxoPointCompletionFromAxo ?: return

    val pt = state.pointsAxo
        .firstOrNull { it.id == pending.axoPointId }
        ?: run {
            cancelAxoPointACompletion(state)
            return
        }

    state.tempLine = createTempSnapLineForAxoPointA(
        state = state,
        pt = pt,
        mode = state.axoMode
    )
}
