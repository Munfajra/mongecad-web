package monge.input.axo.points.pointcomplete

import utils.withSuffixOnce
import dialogs.nameInput.withSuffixOnce
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.Point3D
import model.axo.AxoMode
import model.classes.Point3DAxo
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

data class PendingAxoPointCompletion(
    val pudorysPointId: String
)
fun completeAxoPointP(state: MongeState, pt: Point3DPudorys) {
    state.axoMode = defaultAxoModeForCompletionTarget(ProjectionKind.PUDORYS)

    state.pendingAxoPointCompletion = PendingAxoPointCompletion(
        pudorysPointId = pt.id
    )

    state.tempLine = createTempSnapLineForAxoPointP(
        state = state,
        pt = pt,
        mode = state.axoMode
    )
    setProjectionPhase("axo_complete_point_from_pudorys", state)
}
fun createTempSnapLineForAxoPointP(
    state: MongeState,
    pt: Point3DPudorys,
    mode: AxoMode
): TempSnapLine? {
    return when (mode) {

        AxoMode.AXO_NARYS -> {
            TempSnapLine(
                space = TempSnapSpace.NARYS,
                point = Offset(pt.x, 0f),
                direction = Offset(0f, 1f)
            )
        }

        AxoMode.AXO_BOKORYS -> {
            TempSnapLine(
                space = TempSnapSpace.BOKORYS,
                point = Offset(pt.y, 0f),
                direction = Offset(0f, 1f)
            )
        }

        AxoMode.NORMAL_2D -> {
            val basis = state.basis ?: return null

            TempSnapLine(
                space = TempSnapSpace.AO_OVERLAY,
                point = basis.ex * pt.x + basis.ey * pt.y,
                direction = basis.ez
            )
        }

        else -> null
    }
}
fun finishAxoPointPCompletion(
    state: MongeState,
) {
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
    if (state.projectionPhase != "axo_complete_point_from_pudorys") return

    val pending = state.pendingAxoPointCompletion ?: return

    val pudorys = state.pointsPudorys
        .firstOrNull { it.id == pending.pudorysPointId }
        ?: return cancelAxoPointCompletion(state)

    val x = pudorys.x
    val y = pudorys.y

    val z = when (state.axoMode) {
        AxoMode.AXO_NARYS -> {
            // logical = (x, z)
            logical.y
        }

        AxoMode.AXO_BOKORYS -> {
            // logical = (y, z)
            logical.y
        }

        AxoMode.NORMAL_2D -> {
            val basis = state.basis ?: return
            computeZFromAxoOverlayPoint(
                overlayLocal = logical,
                basis = basis,
                x = x,
                y = y
            )
        }

        else -> return
    }

    createCompletedPoint3DFromPudorys(
        state = state,
        pudorysPt = pudorys,
        x = x,
        y = y,
        z = z
    )

    cancelAxoPointCompletion(state)
}
fun computeZFromAxoOverlayPoint(
    overlayLocal: Offset,
    basis: AxoRenderBasis,
    x: Float,
    y: Float
): Float {
    val base = basis.ex * x + basis.ey * y
    val v = overlayLocal - base

    val ez = basis.ez
    val denom = ez.x * ez.x + ez.y * ez.y
    if (denom < 1e-6f) return 0f

    return (v.x * ez.x + v.y * ez.y) / denom
}
fun cancelAxoPointCompletion(state: MongeState) {
    state.pendingAxoPointCompletion = null
    state.tempLine = null
    state.consInfo.value = ""
    setProjectionPhase("pudorys_start", state)
}
fun createCompletedPoint3DFromPudorys(
    state: MongeState,
    pudorysPt: Point3DPudorys,
    x: Float,
    y: Float,
    z: Float
) {

    val rawName = pointBaseName(pudorysPt, "₁")

    val point3D = Point3D(
        x = x,
        y = y,
        z = z,
        name = rawName,
        color = pointColor(pudorysPt),
        width = pointWidth(pudorysPt),
        superscript = pointSuperscript(pudorysPt),
        creationIndex = allocIndex(state)
    )

    state.sharedPoints3D.add(point3D)

    val newPudorys = pudorysPt.copy(
        x = x,
        y = y,
        parent = point3D,
        name = rawName.withSuffixOnce("₁")
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

    val newAxo = createPointAxoProjection(
        state = state,
        point3D = point3D,
        name = rawName.withSuffixOnce("ₐ")
    )

    applyCompletedPointProjectionVisibility(
        visibleKinds = setOfNotNull(ProjectionKind.PUDORYS, pointCompletionTargetKind(state.axoMode)),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replacePudorysPointKeepingPosition(
        state = state,
        oldPoint = pudorysPt,
        newPoint = newPudorys
    )

    state.pointsNarys.add(newNarys)
    state.pointsBokorys.add(newBokorys)

    if (newAxo != null) {
        state.pointsAxo.add(newAxo)
    }
    commitSnapshot(state)
    cancelAxoPointCompletion(state)
    resetStavu(state)
}
fun createPointAxoProjection(
    state: MongeState,
    point3D: Point3D,
    name: String
): Point3DAxo? {
    val basis = state.basis ?: return null

    val local =
        basis.ex * point3D.x +
                basis.ey * point3D.y +
                basis.ez * point3D.z

    return Point3DAxo(
        x = local.x,
        y = local.y,
        name = name,
        parent = point3D,
        creationIndex = allocIndex(state)
    )
}
fun replacePudorysPointKeepingPosition(
    state: MongeState,
    oldPoint: Point3DPudorys,
    newPoint: Point3DPudorys
) {
    val index = state.pointsPudorys.indexOfFirst { it.id == oldPoint.id }

    if (index >= 0) {
        state.pointsPudorys[index] = newPoint
    } else {
        state.pointsPudorys.add(newPoint)
    }
}
fun refreshAxoPointCompletionTempLine(state: MongeState) {
    if (state.projectionPhase != "axo_complete_point_from_pudorys") return

    val pending = state.pendingAxoPointCompletion ?: return

    val pt = state.pointsPudorys
        .firstOrNull { it.id == pending.pudorysPointId }
        ?: run {
            cancelAxoPointCompletion(state)
            return
        }

    state.tempLine = createTempSnapLineForAxoPointP(
        state = state,
        pt = pt,
        mode = state.axoMode
    )

}
