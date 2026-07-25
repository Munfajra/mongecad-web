package monge.input.lines

import utils.System
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import model.DrawingModeMonge
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.axo.AxoMode
import model.classes.*
import monge.input.axo.AxoRenderBasis
import monge.input.axo.createAxoRenderBasis
import monge.input.axo.currentAxoLineLocal
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.projectPoint3DToAxoLocal
import monge.input.axo.points.screenToAxoOverlayLocal
import serialization.commitSnapshot
import state.MongeState
import utils.getLogicalCursor

private data class ActiveLineTrimTarget(
    val targetId: String,
    val parentLineId: String?,
    val view: LineTrimPickView,
    val linePoint: Offset,
    val lineDir: Offset,
    val range: LineTrimRange?
)

data class CustomLineTrimPreview(
    val view: LineTrimPickView,
    val linePoint: Offset,
    val lineDir: Offset,
    val cursorPoint: Offset,
    val firstPoint: Offset?
)

fun beginCustomLineTrim(state: MongeState, target: Any?) {
    val pick = resolveInitialLineTrimPick(state, target ?: selectedLineTrimTarget(state)) ?: return
    state.pendingLineTrimPick = pick
    state.consInfo.value = "Vyber první bod vlastního ořezu"
    state.triggerRedraw++
}

fun clearCustomLineTrim(state: MongeState, target: Any?) {
    val pick = resolveInitialLineTrimPick(state, target ?: selectedLineTrimTarget(state)) ?: return
    applyCustomLineTrimRange(state, pick, null)
    state.pendingLineTrimPick = null
    state.consInfo.value = ""
    state.triggerRedraw++
    commitSnapshot(state)
}

fun handleCustomLineTrimClick(state: MongeState): Boolean {
    val pending = state.pendingLineTrimPick ?: return false
    val param = currentTrimParamAtCursor(state, pending) ?: return true

    val first = pending.firstParam
    if (first == null) {
        state.pendingLineTrimPick = pending.copy(firstParam = param)
        state.consInfo.value = "Vyber druhý bod vlastního ořezu"
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        return true
    }

    applyCustomLineTrimRange(state, pending, LineTrimRange(first, param).normalized())
    state.pendingLineTrimPick = null
    state.consInfo.value = ""
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    state.triggerRedraw++
    commitSnapshot(state)
    return true
}

fun customLineTrimSnapPoint(state: MongeState): Offset? {
    val pending = state.pendingLineTrimPick ?: return null
    val target = activeLineTrimTarget(state, pending) ?: return null
    val cursor = rawCursorForTrimView(state, target.view) ?: return null
    val t = cursor.lineParamAt(target.linePoint, target.lineDir) ?: return null
    val projected = target.linePoint + target.lineDir * t
    setSnappedLineForTrimTarget(state, pending)
    return outputCursorForTrimView(state, target.view, projected)
}

fun customLineTrimPreview(state: MongeState): CustomLineTrimPreview? {
    val pending = state.pendingLineTrimPick ?: return null
    val target = activeLineTrimTarget(state, pending) ?: return null
    val cursor = rawCursorForTrimView(state, target.view) ?: return null
    val currentParam = cursor.lineParamAt(target.linePoint, target.lineDir) ?: return null
    val firstPoint = pending.firstParam?.let { target.linePoint + target.lineDir * it }

    return CustomLineTrimPreview(
        view = target.view,
        linePoint = target.linePoint,
        lineDir = target.lineDir,
        cursorPoint = target.linePoint + target.lineDir * currentParam,
        firstPoint = firstPoint
    )
}

private fun selectedLineTrimTarget(state: MongeState): Any? =
    state.selectedLinesPudorys.firstOrNull()
        ?: state.selectedLinesNarys.firstOrNull()
        ?: state.selectedLinesBokorys.firstOrNull()
        ?: state.selectedLinesAxo.firstOrNull()
        ?: state.selectedLines3D.firstOrNull()

private fun resolveInitialLineTrimPick(state: MongeState, target: Any?): PendingLineTrimPick? {
    target ?: return null
    val parentId = when (target) {
        is Line3D -> target.id
        is Line3DProjectionPudorys -> target.parent?.id
        is Line3DProjectionNarys -> target.parent?.id
        is Line3DProjectionBokorys -> target.parent?.id
        is Line3DProjectionAxo -> target.parent?.id
        else -> null
    }

    if (parentId != null) {
        val view = currentTrimPickView(state)
        return PendingLineTrimPick(
            targetId = projectionIdForParentInView(state, parentId, view) ?: parentId,
            parentLineId = parentId,
            view = view
        )
    }

    return when (target) {
        is Line3DProjectionPudorys ->
            PendingLineTrimPick(target.id, null, LineTrimPickView.PUDORYS)
        is Line3DProjectionNarys ->
            PendingLineTrimPick(target.id, null, LineTrimPickView.NARYS)
        is Line3DProjectionBokorys ->
            PendingLineTrimPick(target.id, null, LineTrimPickView.BOKORYS)
        is Line3DProjectionAxo ->
            PendingLineTrimPick(target.id, null, LineTrimPickView.AXO)
        is HelpLinePudorys ->
            PendingLineTrimPick(target.id, null, LineTrimPickView.PUDORYS)
        is HelpLineNarys ->
            PendingLineTrimPick(target.id, null, LineTrimPickView.NARYS)
        else -> null
    }
}

private fun currentTrimPickView(state: MongeState): LineTrimPickView =
    if (state.projectionMode == ProjectionMode.AXO) {
        when (state.axoMode) {
            AxoMode.AXO_PUDORYS -> LineTrimPickView.PUDORYS
            AxoMode.AXO_NARYS -> LineTrimPickView.NARYS
            AxoMode.AXO_BOKORYS -> LineTrimPickView.BOKORYS
            AxoMode.NORMAL_2D -> LineTrimPickView.AXO
        }
    } else {
        when (state.mongeMode) {
            DrawingModeMonge.PUDORYS -> LineTrimPickView.PUDORYS
            DrawingModeMonge.NARYS -> LineTrimPickView.NARYS
        }
    }

private fun projectionIdForParentInView(
    state: MongeState,
    parentId: String,
    view: LineTrimPickView
): String? =
    when (view) {
        LineTrimPickView.PUDORYS -> state.lines3DPudorys.firstOrNull { it.parent?.id == parentId }?.id
        LineTrimPickView.NARYS -> state.lines3DNarys.firstOrNull { it.parent?.id == parentId }?.id
        LineTrimPickView.BOKORYS -> state.lines3DBokorys.firstOrNull { it.parent?.id == parentId }?.id
        LineTrimPickView.AXO -> state.lines3DAxo.firstOrNull { it.parent?.id == parentId }?.id
    }

private fun activeLineTrimTarget(state: MongeState, pending: PendingLineTrimPick): ActiveLineTrimTarget? {
    pending.parentLineId?.let { parentId ->
        val parent = state.lines3D.firstOrNull { it.id == parentId }
            ?: parentFromProjectionLists(state, parentId)
            ?: return null
        val (point, dir) = when (pending.view) {
            LineTrimPickView.PUDORYS ->
                Offset(parent.start.x, parent.start.y) to Offset(parent.direction.x, parent.direction.y)
            LineTrimPickView.NARYS ->
                Offset(parent.start.x, parent.start.z) to Offset(parent.direction.x, parent.direction.z)
            LineTrimPickView.BOKORYS ->
                Offset(parent.start.y, parent.start.z) to Offset(parent.direction.y, parent.direction.z)
            LineTrimPickView.AXO -> {
                val basis = axoBasis(state) ?: return null
                projectPoint3DToAxoLocal(parent.start.x, parent.start.y, parent.start.z, basis) to
                        (basis.ex * parent.direction.x + basis.ey * parent.direction.y + basis.ez * parent.direction.z)
            }
        }
        if (dir.getDistanceSquared() < 1e-9f) return null
        return ActiveLineTrimTarget(pending.targetId, parentId, pending.view, point, dir, parent.customTrimRange)
    }

    return when (pending.view) {
        LineTrimPickView.PUDORYS -> {
            val line = state.lines3DPudorys.firstOrNull { it.id == pending.targetId }
            if (line != null) {
                val (point, dir) = line.trimLocalLine() ?: return null
                ActiveLineTrimTarget(line.id, null, pending.view, point, dir, line.localCustomTrimRange)
            } else {
                val help = state.helpLinePudorys.firstOrNull { it.id == pending.targetId } ?: return null
                ActiveLineTrimTarget(help.id, null, pending.view, Offset(help.point.x, help.point.y), help.direction, help.customTrimRange)
            }
        }
        LineTrimPickView.NARYS -> {
            val line = state.lines3DNarys.firstOrNull { it.id == pending.targetId }
            if (line != null) {
                val (point, dir) = line.trimLocalLine() ?: return null
                ActiveLineTrimTarget(line.id, null, pending.view, point, dir, line.localCustomTrimRange)
            } else {
                val help = state.helpLineNarys.firstOrNull { it.id == pending.targetId } ?: return null
                ActiveLineTrimTarget(help.id, null, pending.view, Offset(help.point.x, help.point.z), help.direction, help.customTrimRange)
            }
        }
        LineTrimPickView.BOKORYS -> {
            val line = state.lines3DBokorys.firstOrNull { it.id == pending.targetId } ?: return null
            val (point, dir) = line.trimLocalLine() ?: return null
            ActiveLineTrimTarget(line.id, null, pending.view, point, dir, line.localCustomTrimRange)
        }
        LineTrimPickView.AXO -> {
            val line = state.lines3DAxo.firstOrNull { it.id == pending.targetId } ?: return null
            val basis = axoBasis(state) ?: return null
            val (point, dir) = line.currentAxoLineLocal(basis)
            if (dir.getDistanceSquared() < 1e-9f) return null
            ActiveLineTrimTarget(line.id, null, pending.view, point, dir, line.localCustomTrimRange)
        }
    }
}

private fun parentFromProjectionLists(state: MongeState, parentId: String): Line3D? =
    state.lines3DPudorys.firstOrNull { it.parent?.id == parentId }?.parent
        ?: state.lines3DNarys.firstOrNull { it.parent?.id == parentId }?.parent
        ?: state.lines3DBokorys.firstOrNull { it.parent?.id == parentId }?.parent
        ?: state.lines3DAxo.firstOrNull { it.parent?.id == parentId }?.parent

private fun currentTrimParamAtCursor(state: MongeState, pending: PendingLineTrimPick): Float? {
    val target = activeLineTrimTarget(state, pending) ?: return null
    val cursor = rawCursorForTrimView(state, target.view) ?: return null
    val t = cursor.lineParamAt(target.linePoint, target.lineDir) ?: return null
    return t
}

private fun rawCursorForTrimView(state: MongeState, view: LineTrimPickView): Offset? {
    if (state.projectionMode == ProjectionMode.AXO) {
        if (view == LineTrimPickView.AXO) {
            val basis = axoBasis(state) ?: return null
            return screenToAxoOverlayLocal(state.cursorPosition, state, basis)
        }

        val mode = when (view) {
            LineTrimPickView.PUDORYS -> AxoMode.AXO_PUDORYS
            LineTrimPickView.NARYS -> AxoMode.AXO_NARYS
            LineTrimPickView.BOKORYS -> AxoMode.AXO_BOKORYS
            LineTrimPickView.AXO -> AxoMode.NORMAL_2D
        }
        return getLogicalCursorAxo(
            snapped = null,
            cursor = state.cursorPosition,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            flipX = false,
            flipY = false,
            mode = mode,
            axoModel = state.activeAxoModel
        )
    }

    val logical = getLogicalCursor(
        snapped = null,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    return when (view) {
        LineTrimPickView.PUDORYS -> logical
        LineTrimPickView.NARYS -> Offset(logical.x, -logical.y)
        LineTrimPickView.BOKORYS -> logical
        LineTrimPickView.AXO -> null
    }
}

private fun outputCursorForTrimView(
    state: MongeState,
    view: LineTrimPickView,
    point: Offset
): Offset? =
    if (state.projectionMode == ProjectionMode.AXO) {
        point
    } else {
        when (view) {
            LineTrimPickView.PUDORYS -> point
            LineTrimPickView.NARYS -> Offset(point.x, -point.y)
            LineTrimPickView.BOKORYS,
            LineTrimPickView.AXO -> null
        }
    }

private fun axoBasis(state: MongeState): AxoRenderBasis? =
    state.basis ?: createAxoRenderBasis(
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        model = state.activeAxoModel
    )

private fun applyCustomLineTrimRange(
    state: MongeState,
    pick: PendingLineTrimPick,
    range: LineTrimRange?
) {
    pick.parentLineId?.let { parentId ->
        val idx = state.lines3D.indexOfFirst { it.id == parentId }
        val currentParent = if (idx >= 0) state.lines3D[idx] else parentFromProjectionLists(state, parentId)
        currentParent ?: return
        val updated = currentParent.copy(customTrimRange = range)
        if (idx >= 0) state.lines3D[idx] = updated
        replaceParentInProjections(state, parentId, updated)
        replaceParentInSelections(state, parentId, updated)
        return
    }

    when (pick.view) {
        LineTrimPickView.PUDORYS -> {
            if (replaceProjection(state.lines3DPudorys, pick.targetId) { it.copy(localCustomTrimRange = range).withVisibilityFrom(it) }) {
                replaceProjectionSelectionPudorys(state, pick.targetId, range)
            } else {
                replaceHelpLinePudorys(state, pick.targetId, range)
            }
        }
        LineTrimPickView.NARYS -> {
            if (replaceProjection(state.lines3DNarys, pick.targetId) { it.copy(localCustomTrimRange = range).withVisibilityFrom(it) }) {
                replaceProjectionSelectionNarys(state, pick.targetId, range)
            } else {
                replaceHelpLineNarys(state, pick.targetId, range)
            }
        }
        LineTrimPickView.BOKORYS -> {
            if (replaceProjection(state.lines3DBokorys, pick.targetId) { it.copy(localCustomTrimRange = range).withVisibilityFrom(it) }) {
                replaceProjectionSelectionBokorys(state, pick.targetId, range)
            }
        }
        LineTrimPickView.AXO -> {
            replaceProjection(state.lines3DAxo, pick.targetId) { it.copy(localCustomTrimRange = range).withVisibilityFrom(it) }
            replaceProjectionSelectionAxo(state, pick.targetId, range)
        }
    }
}

private inline fun <T> replaceProjection(
    list: SnapshotStateList<T>,
    id: String,
    transform: (T) -> T
): Boolean where T : Line2DProjection {
    val idx = list.indexOfFirst { it.id == id }
    if (idx < 0) return false
    list[idx] = transform(list[idx])
    return true
}

private fun replaceParentInProjections(state: MongeState, parentId: String, parent: Line3D) {
    for (i in state.lines3DPudorys.indices) {
        val old = state.lines3DPudorys[i]
        if (old.parent?.id == parentId) state.lines3DPudorys[i] = old.copy(parent = parent).withVisibilityFrom(old)
    }
    for (i in state.lines3DNarys.indices) {
        val old = state.lines3DNarys[i]
        if (old.parent?.id == parentId) state.lines3DNarys[i] = old.copy(parent = parent).withVisibilityFrom(old)
    }
    for (i in state.lines3DBokorys.indices) {
        val old = state.lines3DBokorys[i]
        if (old.parent?.id == parentId) state.lines3DBokorys[i] = old.copy(parent = parent).withVisibilityFrom(old)
    }
    for (i in state.lines3DAxo.indices) {
        val old = state.lines3DAxo[i]
        if (old.parent?.id == parentId) state.lines3DAxo[i] = old.copy(parent = parent).withVisibilityFrom(old)
    }
}

private fun replaceParentInSelections(state: MongeState, parentId: String, parent: Line3D) {
    for (i in state.selectedLines3D.indices) {
        if (state.selectedLines3D[i].id == parentId) state.selectedLines3D[i] = parent
    }
    for (i in state.selectedLinesPudorys.indices) {
        val old = state.selectedLinesPudorys[i]
        if (old is Line3DProjectionPudorys && old.parent?.id == parentId) {
            state.selectedLinesPudorys[i] = old.copy(parent = parent).withVisibilityFrom(old)
        }
    }
    for (i in state.selectedLinesNarys.indices) {
        val old = state.selectedLinesNarys[i]
        if (old is Line3DProjectionNarys && old.parent?.id == parentId) {
            state.selectedLinesNarys[i] = old.copy(parent = parent).withVisibilityFrom(old)
        }
    }
    for (i in state.selectedLinesBokorys.indices) {
        val old = state.selectedLinesBokorys[i]
        if (old is Line3DProjectionBokorys && old.parent?.id == parentId) {
            state.selectedLinesBokorys[i] = old.copy(parent = parent).withVisibilityFrom(old)
        }
    }
    for (i in state.selectedLinesAxo.indices) {
        val old = state.selectedLinesAxo[i]
        if (old.parent?.id == parentId) state.selectedLinesAxo[i] = old.copy(parent = parent).withVisibilityFrom(old)
    }
}

private fun replaceProjectionSelectionPudorys(state: MongeState, id: String, range: LineTrimRange?) {
    for (i in state.selectedLinesPudorys.indices) {
        val old = state.selectedLinesPudorys[i]
        if (old is Line3DProjectionPudorys && old.id == id) {
            state.selectedLinesPudorys[i] = old.copy(localCustomTrimRange = range).withVisibilityFrom(old)
        }
    }
}

private fun replaceProjectionSelectionNarys(state: MongeState, id: String, range: LineTrimRange?) {
    for (i in state.selectedLinesNarys.indices) {
        val old = state.selectedLinesNarys[i]
        if (old is Line3DProjectionNarys && old.id == id) {
            state.selectedLinesNarys[i] = old.copy(localCustomTrimRange = range).withVisibilityFrom(old)
        }
    }
}

private fun replaceProjectionSelectionBokorys(state: MongeState, id: String, range: LineTrimRange?) {
    for (i in state.selectedLinesBokorys.indices) {
        val old = state.selectedLinesBokorys[i]
        if (old is Line3DProjectionBokorys && old.id == id) {
            state.selectedLinesBokorys[i] = old.copy(localCustomTrimRange = range).withVisibilityFrom(old)
        }
    }
}

private fun replaceProjectionSelectionAxo(state: MongeState, id: String, range: LineTrimRange?) {
    for (i in state.selectedLinesAxo.indices) {
        val old = state.selectedLinesAxo[i]
        if (old.id == id) state.selectedLinesAxo[i] = old.copy(localCustomTrimRange = range).withVisibilityFrom(old)
    }
}

private fun replaceHelpLinePudorys(state: MongeState, id: String, range: LineTrimRange?) {
    val idx = state.helpLinePudorys.indexOfFirst { it.id == id }
    if (idx >= 0) state.helpLinePudorys[idx] = state.helpLinePudorys[idx].copy(customTrimRange = range)
    for (i in state.selectedLinesPudorys.indices) {
        val old = state.selectedLinesPudorys[i]
        if (old is HelpLinePudorys && old.id == id) state.selectedLinesPudorys[i] = old.copy(customTrimRange = range)
    }
}

private fun replaceHelpLineNarys(state: MongeState, id: String, range: LineTrimRange?) {
    val idx = state.helpLineNarys.indexOfFirst { it.id == id }
    if (idx >= 0) state.helpLineNarys[idx] = state.helpLineNarys[idx].copy(customTrimRange = range)
    for (i in state.selectedLinesNarys.indices) {
        val old = state.selectedLinesNarys[i]
        if (old is HelpLineNarys && old.id == id) state.selectedLinesNarys[i] = old.copy(customTrimRange = range)
    }
}

private fun setSnappedLineForTrimTarget(state: MongeState, pending: PendingLineTrimPick) {
    when (pending.view) {
        LineTrimPickView.PUDORYS ->
            state.snappedLinePudorys = state.lines3DPudorys.firstOrNull { it.id == pending.targetId }
                ?: state.helpLinePudorys.firstOrNull { it.id == pending.targetId }
        LineTrimPickView.NARYS ->
            state.snappedLineNarys = state.lines3DNarys.firstOrNull { it.id == pending.targetId }
                ?: state.helpLineNarys.firstOrNull { it.id == pending.targetId }
        LineTrimPickView.BOKORYS ->
            state.snappedLineBokorys = state.lines3DBokorys.firstOrNull { it.id == pending.targetId }
        LineTrimPickView.AXO ->
            state.snappedLineAxo = state.lines3DAxo.firstOrNull { it.id == pending.targetId }
    }
}

private fun Line3DProjectionPudorys.withVisibilityFrom(source: Line3DProjectionPudorys): Line3DProjectionPudorys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
    }

private fun Line3DProjectionNarys.withVisibilityFrom(source: Line3DProjectionNarys): Line3DProjectionNarys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
    }

private fun Line3DProjectionBokorys.withVisibilityFrom(source: Line3DProjectionBokorys): Line3DProjectionBokorys =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
    }

private fun Line3DProjectionAxo.withVisibilityFrom(source: Line3DProjectionAxo): Line3DProjectionAxo =
    apply {
        showInAxoInitial = source.showInAxo
        showInAxo = source.showInAxo
    }
