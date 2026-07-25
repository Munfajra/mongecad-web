package monge.input.axo.points.pointcomplete

import utils.withSuffixOnce
import dialogs.nameInput.withSuffixOnce
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.Point3D
import model.axo.AxoMode
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.TempSnapLine
import model.classes.TempSnapSpace
import monge.input.axo.AxoRenderBasis
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.linecomplete.ProjectionKind
import monge.input.axo.lines.linecomplete.defaultAxoModeForCompletionTarget
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex

data class PendingAxoPointCompletionFromNarys(
    val narysPointId: String
)
fun completeAxoPointN(state: MongeState, pt: Point3DNarys) {
    state.axoMode = defaultAxoModeForCompletionTarget(ProjectionKind.NARYS)

    state.pendingAxoPointCompletionFromNarys =
        PendingAxoPointCompletionFromNarys(
            narysPointId = pt.id
        )

    state.tempLine = createTempSnapLineForAxoPointN(
        state = state,
        pt = pt,
        mode = state.axoMode
    )

    setProjectionPhase("axo_complete_point_from_narys", state)
}
fun createTempSnapLineForAxoPointN(
    state: MongeState,
    pt: Point3DNarys,
    mode: AxoMode
): TempSnapLine? {
    return when (mode) {

        AxoMode.AXO_PUDORYS -> {
            // půdorys = (x, y)
            // x je pevné, hledáme y
            TempSnapLine(
                space = TempSnapSpace.PUDORYS,
                point = Offset(pt.x, 0f),
                direction = Offset(0f, 1f)
            )
        }

        AxoMode.AXO_BOKORYS -> {
            // bokorys = (y, z)
            // z je pevné, hledáme y
            TempSnapLine(
                space = TempSnapSpace.BOKORYS,
                point = Offset(0f, pt.z),
                direction = Offset(1f, 0f)
            )
        }

        AxoMode.NORMAL_2D -> {
            val basis = state.basis ?: return null

            // v overlay už známe x a z,
            // pohybujeme se po směru osy y
            TempSnapLine(
                space = TempSnapSpace.AO_OVERLAY,
                point = basis.ex * pt.x + basis.ez * pt.z,
                direction = basis.ey
            )
        }

        else -> null
    }
}
fun finishAxoPointNCompletion(
    state: MongeState
) {
    if (state.projectionPhase != "axo_complete_point_from_narys") return

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

    val pending = state.pendingAxoPointCompletionFromNarys ?: return

    val narys = state.pointsNarys
        .firstOrNull { it.id == pending.narysPointId }
        ?: return cancelAxoPointNCompletion(state)

    val x = narys.x
    val z = narys.z

    val y = when (state.axoMode) {

        AxoMode.AXO_PUDORYS -> {
            // logical = (x, y)
            logical.y
        }

        AxoMode.AXO_BOKORYS -> {
            // logical = (y, z)
            logical.x
        }

        AxoMode.NORMAL_2D -> {
            val basis = state.basis ?: return

            computeYFromAxoOverlayPoint(
                overlayLocal = logical,
                basis = basis,
                x = x,
                z = z
            )
        }

        else -> return
    }

    createCompletedPoint3DFromNarys(
        state = state,
        narysPt = narys,
        x = x,
        y = y,
        z = z
    )

    cancelAxoPointNCompletion(state)
}
fun computeYFromAxoOverlayPoint(
    overlayLocal: Offset,
    basis: AxoRenderBasis,
    x: Float,
    z: Float
): Float {
    val base = basis.ex * x + basis.ez * z
    val v = overlayLocal - base

    val ey = basis.ey
    val denom = ey.x * ey.x + ey.y * ey.y

    if (denom < 1e-6f) return 0f

    return (v.x * ey.x + v.y * ey.y) / denom
}
fun cancelAxoPointNCompletion(state: MongeState) {
    state.pendingAxoPointCompletionFromNarys = null
    state.tempLine = null
    state.consInfo.value = ""
    setProjectionPhase("pudorys_start", state)
}
fun createCompletedPoint3DFromNarys(
    state: MongeState,
    narysPt: Point3DNarys,
    x: Float,
    y: Float,
    z: Float
) {
    val rawName = pointBaseName(narysPt, "₂")

    val point3D = Point3D(
        x = x,
        y = y,
        z = z,
        name = rawName,
        color = pointColor(narysPt),
        width = pointWidth(narysPt),
        superscript = pointSuperscript(narysPt),
        creationIndex = allocIndex(state)
    )

    state.sharedPoints3D.add(point3D)

    val newNarys = narysPt.copy(
        x = x,
        z = z,
        parent = point3D,
        name = rawName.withSuffixOnce("₂")
    )

    val newPudorys = Point3DPudorys(
        x = x,
        y = y,
        name = rawName.withSuffixOnce("₁"),
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

    val newAxo = createPointAxoProjection(
        state = state,
        point3D = point3D,
        name = rawName.withSuffixOnce("ₐ")
    )

    applyCompletedPointProjectionVisibility(
        visibleKinds = setOfNotNull(ProjectionKind.NARYS, pointCompletionTargetKind(state.axoMode)),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replaceNarysPointKeepingPosition(
        state = state,
        oldPoint = narysPt,
        newPoint = newNarys
    )

    state.pointsPudorys.add(newPudorys)
    state.pointsBokorys.add(newBokorys)

    if (newAxo != null) {
        state.pointsAxo.add(newAxo)
    }

    commitSnapshot(state)
    cancelAxoPointNCompletion(state)
    resetStavu(state)
}
fun replaceNarysPointKeepingPosition(
    state: MongeState,
    oldPoint: Point3DNarys,
    newPoint: Point3DNarys
) {
    val index = state.pointsNarys.indexOfFirst { it.id == oldPoint.id }

    if (index >= 0) {
        state.pointsNarys[index] = newPoint
    } else {
        state.pointsNarys.add(newPoint)
    }
}
fun refreshAxoPointNCompletionTempLine(state: MongeState) {
    if (state.projectionPhase != "axo_complete_point_from_narys") return

    val pending = state.pendingAxoPointCompletionFromNarys ?: return

    val pt = state.pointsNarys
        .firstOrNull { it.id == pending.narysPointId }
        ?: run {
            cancelAxoPointNCompletion(state)
            return
        }

    state.tempLine = createTempSnapLineForAxoPointN(
        state = state,
        pt = pt,
        mode = state.axoMode
    )
}
