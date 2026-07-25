package draw.mongescreen.previews.lines.linecomplete

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.VisibleQuad
import draw.mongescreen.previews.lines.axo.drawDashedPreviewLineAxoOverlay
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewBokorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewNarysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineBokorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorysAxo
import model.ConstructionModifier
import model.axo.AxoMode
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.linecomplete.ProjectionKind
import monge.input.axo.lines.linecomplete.projectionKindFromAxoMode
import monge.input.axo.lines.linecomplete.resolveCompletionSecondDirection
import monge.input.axo.points.screenToAxoOverlayLocal
import state.MongeState

private fun MongeState.isDrawingCompletionSecondPoint(kind: ProjectionKind): Boolean {
    val pending = pendingAxoLineCompletion ?: return false
    if (constructionModifier != ConstructionModifier.NONE) return false
    if (projectionKindFromAxoMode(axoMode) != kind) return false
    if (kind == pending.firstKind) return false

    val secondKind = pending.secondKind ?: kind
    return secondKind == kind && completingLineSecondStart != null
}

private fun MongeState.isDrawingCompletionDirectedPreview(kind: ProjectionKind): Boolean {
    val pending = pendingAxoLineCompletion ?: return false
    if (
        constructionModifier != ConstructionModifier.PARALLEL &&
        constructionModifier != ConstructionModifier.ORTHOGONAL
    ) return false
    if (!hasOverlayReference(this)) return false
    if (projectionKindFromAxoMode(axoMode) != kind) return false
    if (kind == pending.firstKind) return false

    val secondKind = pending.secondKind ?: kind
    return secondKind == kind
}

fun DrawScope.drawLineCompletionPreviewPudorys(
    state: MongeState,
    snappedPointLogical: Offset?,
    visibleQuad: VisibleQuad?
) {
    if (state.isDrawingCompletionDirectedPreview(ProjectionKind.PUDORYS)) {
        val cursorLogical = getLogicalCursorAxo(
            snapped = snappedPointLogical,
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
        val direction = resolveCompletionSecondDirection(
            state = state,
            kind = ProjectionKind.PUDORYS,
            modifier = state.constructionModifier
        ) ?: return

        drawDashedParallelLinePreviewPudorysAxo(
            through = Point3DPudorys(cursorLogical.x, cursorLogical.y, name = "?"),
            direction = direction,
            visibleQuad = visibleQuad ?: return,
            scale = state.scale
        )
        return
    }

    if (!state.isDrawingCompletionSecondPoint(ProjectionKind.PUDORYS)) return
    val start = state.completingLineSecondStart ?: return
    val quad = visibleQuad ?: return

    val cursorLogical = getLogicalCursorAxo(
        snapped = snappedPointLogical,
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

    drawDashedPreviewLinePudorysAxo(
        start = Point3DPudorys(start.x, start.y, name = "?"),
        cursorWorld = cursorLogical,
        visibleQuad = quad,
        color = Color.Gray,
        scale = state.scale
    )
}

fun DrawScope.drawLineCompletionPreviewNarys(
    state: MongeState,
    snappedPointLogical: Offset?,
    visibleQuad: VisibleQuad?
) {
    if (state.isDrawingCompletionDirectedPreview(ProjectionKind.NARYS)) {
        val cursorLogical = getLogicalCursorAxo(
            snapped = snappedPointLogical,
            cursor = state.cursorPosition,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            flipX = false,
            flipY = false,
            mode = AxoMode.AXO_NARYS,
            axoModel = state.activeAxoModel
        ) ?: return
        val direction = resolveCompletionSecondDirection(
            state = state,
            kind = ProjectionKind.NARYS,
            modifier = state.constructionModifier
        ) ?: return

        drawDashedParallelLinePreviewNarysAxo(
            through = Point3DNarys(cursorLogical.x, cursorLogical.y, name = "?"),
            direction = direction,
            visibleQuad = visibleQuad ?: return,
            scale = state.scale
        )
        return
    }

    if (!state.isDrawingCompletionSecondPoint(ProjectionKind.NARYS)) return
    val start = state.completingLineSecondStart ?: return
    val quad = visibleQuad ?: return

    val cursorLogical = getLogicalCursorAxo(
        snapped = snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_NARYS,
        axoModel = state.activeAxoModel
    ) ?: return

    drawDashedPreviewLineNarysAxo(
        start = Point3DNarys(start.x, start.y, name = "?"),
        cursorWorld = cursorLogical,
        visibleQuad = quad,
        color = Color.Gray,
        scale = state.scale
    )
}

fun DrawScope.drawLineCompletionPreviewBokorys(
    state: MongeState,
    snappedPointLogical: Offset?,
    visibleQuad: VisibleQuad?
) {
    if (state.isDrawingCompletionDirectedPreview(ProjectionKind.BOKORYS)) {
        val cursorLogical = getLogicalCursorAxo(
            snapped = snappedPointLogical,
            cursor = state.cursorPosition,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            flipX = false,
            flipY = false,
            mode = AxoMode.AXO_BOKORYS,
            axoModel = state.activeAxoModel
        ) ?: return
        val direction = resolveCompletionSecondDirection(
            state = state,
            kind = ProjectionKind.BOKORYS,
            modifier = state.constructionModifier
        ) ?: return

        drawDashedParallelLinePreviewBokorysAxo(
            through = Point3DBokorys(cursorLogical.x, cursorLogical.y, name = "?"),
            direction = direction,
            visibleQuad = visibleQuad ?: return,
            scale = state.scale
        )
        return
    }

    if (!state.isDrawingCompletionSecondPoint(ProjectionKind.BOKORYS)) return
    val start = state.completingLineSecondStart ?: return
    val quad = visibleQuad ?: return

    val cursorLogical = getLogicalCursorAxo(
        snapped = snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_BOKORYS,
        axoModel = state.activeAxoModel
    ) ?: return

    drawDashedPreviewLineBokorysAxo(
        start = Point3DBokorys(start.x, start.y, name = "?"),
        cursorWorld = cursorLogical,
        visibleQuad = quad,
        color = Color.Gray,
        scale = state.scale
    )
}

fun DrawScope.drawLineCompletionPreviewAxoOverlay(state: MongeState) {
    if (state.isDrawingCompletionDirectedPreview(ProjectionKind.AXO)) {
        val basis = state.basis ?: return
        val cursorLocal = state.snappedPointLogical
            ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
        val direction = resolveCompletionSecondDirection(
            state = state,
            kind = ProjectionKind.AXO,
            modifier = state.constructionModifier
        ) ?: return

        drawDashedPreviewLineAxoOverlay(
            state = state,
            localPoint = cursorLocal,
            localDir = direction,
            color = Color.Gray
        )
        return
    }

    if (!state.isDrawingCompletionSecondPoint(ProjectionKind.AXO)) return
    val start = state.completingLineSecondStart ?: return
    val basis = state.basis ?: return

    val cursorLocal = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

    drawDashedPreviewLineAxoOverlay(
        state = state,
        localPoint = start,
        localDir = cursorLocal - start,
        color = Color.Gray
    )
}
