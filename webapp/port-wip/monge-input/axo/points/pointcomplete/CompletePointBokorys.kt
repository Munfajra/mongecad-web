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

data class PendingAxoPointCompletionFromBokorys(
    val bokorysPointId: String
)
fun completeAxoPointB(state: MongeState, pt: Point3DBokorys) {
    state.axoMode = defaultAxoModeForCompletionTarget(ProjectionKind.BOKORYS)

    state.pendingAxoPointCompletionFromBokorys =
        PendingAxoPointCompletionFromBokorys(
            bokorysPointId = pt.id
        )

    state.tempLine = createTempSnapLineForAxoPointB(
        state = state,
        pt = pt,
        mode = state.axoMode
    )

    setProjectionPhase("axo_complete_point_from_bokorys", state)
}
fun createTempSnapLineForAxoPointB(
    state: MongeState,
    pt: Point3DBokorys,
    mode: AxoMode
): TempSnapLine? {
    return when (mode) {

        AxoMode.AXO_PUDORYS -> {
            // půdorys = (x, y)
            // y je pevné, hledáme x
            TempSnapLine(
                space = TempSnapSpace.PUDORYS,
                point = Offset(0f, pt.y),
                direction = Offset(1f, 0f)
            )
        }

        AxoMode.AXO_NARYS -> {
            // nárys = (x, z)
            // z je pevné, hledáme x
            TempSnapLine(
                space = TempSnapSpace.NARYS,
                point = Offset(0f, pt.z),
                direction = Offset(1f, 0f)
            )
        }

        AxoMode.NORMAL_2D -> {
            val basis = state.basis ?: return null

            // v overlay známe y a z,
            // pohybujeme se po směru osy x
            TempSnapLine(
                space = TempSnapSpace.AO_OVERLAY,
                point = basis.ey * pt.y + basis.ez * pt.z,
                direction = basis.ex
            )
        }

        else -> null
    }
}
fun finishAxoPointBCompletion(
    state: MongeState
) {
    if (state.projectionPhase != "axo_complete_point_from_bokorys") return

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

    val pending = state.pendingAxoPointCompletionFromBokorys ?: return

    val bokorys = state.pointsBokorys
        .firstOrNull { it.id == pending.bokorysPointId }
        ?: return cancelAxoPointBCompletion(state)

    val y = bokorys.y
    val z = bokorys.z

    val x = when (state.axoMode) {

        AxoMode.AXO_PUDORYS -> {
            // logical = (x, y)
            logical.x
        }

        AxoMode.AXO_NARYS -> {
            // logical = (x, z)
            logical.x
        }

        AxoMode.NORMAL_2D -> {
            val basis = state.basis ?: return

            computeXFromAxoOverlayPoint(
                overlayLocal = logical,
                basis = basis,
                y = y,
                z = z
            )
        }

        else -> return
    }

    createCompletedPoint3DFromBokorys(
        state = state,
        bokorysPt = bokorys,
        x = x,
        y = y,
        z = z
    )

    cancelAxoPointBCompletion(state)
}
fun computeXFromAxoOverlayPoint(
    overlayLocal: Offset,
    basis: AxoRenderBasis,
    y: Float,
    z: Float
): Float {
    val base = basis.ey * y + basis.ez * z
    val v = overlayLocal - base

    val ex = basis.ex
    val denom = ex.x * ex.x + ex.y * ex.y

    if (denom < 1e-6f) return 0f

    return (v.x * ex.x + v.y * ex.y) / denom
}
fun cancelAxoPointBCompletion(state: MongeState) {
    state.pendingAxoPointCompletionFromBokorys = null
    state.tempLine = null
    state.consInfo.value = ""
    setProjectionPhase("pudorys_start", state)
}
fun createCompletedPoint3DFromBokorys(
    state: MongeState,
    bokorysPt: Point3DBokorys,
    x: Float,
    y: Float,
    z: Float
) {
    val rawName = pointBaseName(bokorysPt, "₃")

    val point3D = Point3D(
        x = x,
        y = y,
        z = z,
        name = rawName,
        color = pointColor(bokorysPt),
        width = pointWidth(bokorysPt),
        superscript = pointSuperscript(bokorysPt),
        creationIndex = allocIndex(state)
    )

    state.sharedPoints3D.add(point3D)

    val newBokorys = bokorysPt.copy(
        y = y,
        z = z,
        parent = point3D,
        name = rawName.withSuffixOnce("₃")
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

    val newAxo = createPointAxoProjection(
        state = state,
        point3D = point3D,
        name = rawName.withSuffixOnce("ₐ")
    )

    applyCompletedPointProjectionVisibility(
        visibleKinds = setOfNotNull(ProjectionKind.BOKORYS, pointCompletionTargetKind(state.axoMode)),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replaceBokorysPointKeepingPosition(
        state = state,
        oldPoint = bokorysPt,
        newPoint = newBokorys
    )

    state.pointsPudorys.add(newPudorys)
    state.pointsNarys.add(newNarys)

    if (newAxo != null) {
        state.pointsAxo.add(newAxo)
    }

    commitSnapshot(state)
    cancelAxoPointBCompletion(state)
    resetStavu(state)
}
fun replaceBokorysPointKeepingPosition(
    state: MongeState,
    oldPoint: Point3DBokorys,
    newPoint: Point3DBokorys
) {
    val index = state.pointsBokorys.indexOfFirst { it.id == oldPoint.id }

    if (index >= 0) {
        state.pointsBokorys[index] = newPoint
    } else {
        state.pointsBokorys.add(newPoint)
    }
}
fun refreshAxoPointBCompletionTempLine(state: MongeState) {
    if (state.projectionPhase != "axo_complete_point_from_bokorys") return

    val pending = state.pendingAxoPointCompletionFromBokorys ?: return

    val pt = state.pointsBokorys
        .firstOrNull { it.id == pending.bokorysPointId }
        ?: run {
            cancelAxoPointBCompletion(state)
            return
        }

    state.tempLine = createTempSnapLineForAxoPointB(
        state = state,
        pt = pt,
        mode = state.axoMode
    )
}
