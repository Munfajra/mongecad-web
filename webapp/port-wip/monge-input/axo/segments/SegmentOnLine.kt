package monge.input.axo.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import model.ConstructionModifier
import model.ProjectionType
import model.axo.AxoMode
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.TempSnapLine
import model.classes.TempSnapSpace
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.points.screenToAxoOverlayLocal
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex

private enum class SegmentOnLineTarget(
    val prefix: String,
    val tempSpace: TempSnapSpace
) {
    PUDORYS("sol_pudorys", TempSnapSpace.PUDORYS),
    NARYS("sol_narys", TempSnapSpace.NARYS),
    BOKORYS("sol_bokorys", TempSnapSpace.BOKORYS),
    AO("sol_ao", TempSnapSpace.AO_OVERLAY),
    AXO("sol_axo", TempSnapSpace.AO_OVERLAY)
}

fun handleSegmentOnLineAxo(state: MongeState) {
    state.constructionModifier = ConstructionModifier.NONE

    val target = state.currentSegmentOnLineTarget() ?: return
    val logical = state.currentSegmentOnLineLogical(target) ?: return
    val prefix = target.prefix

    when (state.projectionPhase) {
        "${prefix}_line_second" -> {
            val linePoint = state.pendingPoint1 ?: return
            val direction = logical - linePoint
            if (direction.getDistanceSquared() < 1e-6f) return

            state.pendingDirection = direction
            state.tempLine = TempSnapLine(
                point = linePoint,
                direction = direction,
                id = "${prefix}_temp_line",
                space = target.tempSpace
            )

            setProjectionPhase("${prefix}_segment_start", state)
            state.consInfo.value = "Umístěte začátek úsečky"
            return
        }

        "${prefix}_segment_start" -> {
            val projected = state.projectOnSegmentTempLine(logical) ?: return
            state.storeSegmentOnLineStart(target, projected)
            setProjectionPhase("${prefix}_segment_end", state)
            state.consInfo.value = "Umístěte konec úsečky"
            return
        }

        "${prefix}_segment_end" -> {
            val end = state.projectOnSegmentTempLine(logical) ?: return
            state.createSegmentOnLineResult(target, end)
            state.clearSegmentOnLinePending()
            repeatCons(state)
            updateConstructionInfo(state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            resetStavu(state)
            return
        }

        else -> {
            state.clearSegmentOnLinePending(keepPhase = true)
            state.pendingPoint1 = logical
            setProjectionPhase("${prefix}_line_second", state)
            state.consInfo.value = "Určete směr přímky"
        }
    }
}

private fun MongeState.currentSegmentOnLineTarget(): SegmentOnLineTarget? {
    return when (axoMode) {
        AxoMode.AXO_PUDORYS -> SegmentOnLineTarget.PUDORYS
        AxoMode.AXO_NARYS -> SegmentOnLineTarget.NARYS
        AxoMode.AXO_BOKORYS -> SegmentOnLineTarget.BOKORYS
        AxoMode.NORMAL_2D -> {
            when (projekcnityp) {
                ProjectionType.AUXILIARY -> SegmentOnLineTarget.AO
                ProjectionType.SINGLE -> SegmentOnLineTarget.AXO
                ProjectionType.ASSOCIATED -> null
            }
        }
    }
}

private fun MongeState.currentSegmentOnLineLogical(target: SegmentOnLineTarget): Offset? {
    return when (target) {
        SegmentOnLineTarget.AO,
        SegmentOnLineTarget.AXO -> {
            val basis = basis ?: return null
            snappedPointLogical ?: screenToAxoOverlayLocal(cursorPosition, this, basis)
        }

        SegmentOnLineTarget.PUDORYS -> getLogicalCursorAxo(
            snapped = snappedPointLogical,
            cursor = cursorPosition,
            canvasOffset = canvasOffset,
            scale = scale,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            flipX = false,
            flipY = false,
            mode = AxoMode.AXO_PUDORYS,
            axoModel = activeAxoModel
        )

        SegmentOnLineTarget.NARYS -> getLogicalCursorAxo(
            snapped = snappedPointLogical,
            cursor = cursorPosition,
            canvasOffset = canvasOffset,
            scale = scale,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            flipX = false,
            flipY = false,
            mode = AxoMode.AXO_NARYS,
            axoModel = activeAxoModel
        )

        SegmentOnLineTarget.BOKORYS -> getLogicalCursorAxo(
            snapped = snappedPointLogical,
            cursor = cursorPosition,
            canvasOffset = canvasOffset,
            scale = scale,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            flipX = false,
            flipY = false,
            mode = AxoMode.AXO_BOKORYS,
            axoModel = activeAxoModel
        )
    }
}

private fun MongeState.projectOnSegmentTempLine(point: Offset): Offset? {
    val temp = tempLine ?: return null
    return projectPointOntoLineByPointAndDir(
        p = point,
        linePoint = temp.point,
        lineDir = temp.direction
    )
}

private fun MongeState.storeSegmentOnLineStart(
    target: SegmentOnLineTarget,
    projected: Offset
) {
    when (target) {
        SegmentOnLineTarget.PUDORYS -> {
            segmentStartPudorys = Point3DPudorys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(this)
            )
        }

        SegmentOnLineTarget.NARYS -> {
            segmentStartNarys = Point3DNarys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(this)
            )
        }

        SegmentOnLineTarget.BOKORYS -> {
            segmentStartBokorys = Point3DBokorys(
                projected.x,
                projected.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(this)
            )
        }

        SegmentOnLineTarget.AO,
        SegmentOnLineTarget.AXO -> {
            pendingPoint2 = projected
        }
    }
}

private fun MongeState.createSegmentOnLineResult(
    target: SegmentOnLineTarget,
    end: Offset
) {
    when (target) {
        SegmentOnLineTarget.PUDORYS -> {
            val start = segmentStartPudorys ?: return
            createSegmentPudorysFromPoints(
                state = this,
                start = start,
                end = Point3DPudorys(
                    end.x,
                    end.y,
                    name = "",
                    isSegmentEndpoint = true,
                    creationIndex = allocIndex(this)
                )
            )
        }

        SegmentOnLineTarget.NARYS -> {
            val start = segmentStartNarys ?: return
            createSegmentNarysFromPoints(
                state = this,
                start = start,
                end = Point3DNarys(
                    end.x,
                    end.y,
                    name = "",
                    isSegmentEndpoint = true,
                    creationIndex = allocIndex(this)
                )
            )
        }

        SegmentOnLineTarget.BOKORYS -> {
            val start = segmentStartBokorys ?: return
            createSegmentBokorysFromPoints(
                state = this,
                start = start,
                end = Point3DBokorys(
                    end.x,
                    end.y,
                    name = "",
                    isSegmentEndpoint = true,
                    creationIndex = allocIndex(this)
                )
            )
        }

        SegmentOnLineTarget.AO -> {
            val start = pendingPoint2 ?: return
            createAOSegmentFromPoints(
                state = this,
                start = start,
                end = end
            )
        }

        SegmentOnLineTarget.AXO -> {
            val start = pendingPoint2 ?: return
            createAxoSegmentFromPoints(
                state = this,
                start = Point3DAxo(start.x, start.y),
                end = Point3DAxo(end.x, end.y)
            )
        }
    }
}

private fun MongeState.clearSegmentOnLinePending(keepPhase: Boolean = false) {
    pendingPoint1 = null
    pendingPoint2 = null
    pendingDirection = null
    segmentStartPudorys = null
    segmentStartNarys = null
    segmentStartBokorys = null
    tempLine = null
    if (!keepPhase) projectionPhase = null
}
