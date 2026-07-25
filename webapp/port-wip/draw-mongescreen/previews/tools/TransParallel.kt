package draw.mongescreen.previews.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.axo.drawInfiniteAOLineOnScreen
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorys
import model.*
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.axo.lines.normalizedOrNull
import monge.input.axo.points.screenToAxoOverlayLocal
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewPudorysTransDirectionCursor(state: MongeState, snappedPointLogical: Offset?) {
    val baseDirection = state.selectedLineForParallelPudorys?.direction
        ?: state.selectedLinesPudorys.firstOrNull()?.direction
        ?: state.selectedSegmentForParallelPudorys?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.y - it.start.y
            )
        }
        ?: state.selectedSegmentsPudorys.firstOrNull()?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.y - it.start.y
            )
        }

    val effectiveDirection = when (state.constructionModifier) {
        ConstructionModifier.ORTHOGONAL -> baseDirection?.let { Offset(-it.y, it.x) } // kolmá v půdorysu (x-y)
        ConstructionModifier.PARALLEL -> baseDirection
        else -> null
    }

    if (
        (state.drawobjects == Mongeobjects.TRANSPARALLEL || state.drawobjects == Mongeobjects.TRANSORTH) &&
        effectiveDirection != null && state.projectionPhase == "trans_parallel_temp_point_pudorys" &&
        (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL)
    ) {
        val cursorLogical = getLogicalCursor(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
        val through = Point3DPudorys(cursorLogical.x, cursorLogical.y, name = "?")

        drawDashedParallelLinePreviewPudorys(
                    through = through,
                    direction = effectiveDirection,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset,
                    color = Color.Gray
                )}
}
fun DrawScope.previewNarysTransDirectionCursor(state: MongeState, snappedPointLogical: Offset?) {
    val baseDirection = state.selectedLineForParallelNarys?.direction
        ?: state.selectedLinesNarys.firstOrNull()?.direction
        ?: state.selectedSegmentForParallelNarys?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.z - it.start.z
            )
        }
        ?: state.selectedSegmentsNarys.firstOrNull()?.let {
            Offset(
                x = it.end.x - it.start.x,
                y = it.end.z - it.start.z
            )
        }

    val effectiveDirection = when (state.constructionModifier) {
        ConstructionModifier.ORTHOGONAL -> baseDirection?.let { Offset(-it.y, it.x) } // kolmá v půdorysu (x-y)
        ConstructionModifier.PARALLEL -> baseDirection
        else -> null
    }

    if (
        (state.drawobjects == Mongeobjects.TRANSPARALLEL || state.drawobjects == Mongeobjects.TRANSORTH) &&
        effectiveDirection != null && state.projectionPhase == "trans_parallel_temp_point_narys" &&
        (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL)
    ) {
        val cursorLogical = snappedPointLogical
            ?: ((state.cursorPosition - state.canvasOffset) / state.scale)
        val through = Point3DNarys(cursorLogical.x, -cursorLogical.y, name = "?")

        drawDashedParallelLinePreviewNarys(
            through = through,
            direction = effectiveDirection,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            color = Color.Gray
        )}
}
fun DrawScope.drawPendingTransParallelLineAxoOverlay(
    state: MongeState
) {
    if (state.projectionPhase != "axo_trans_parallel_place_line") return

    val basis = state.basis ?: return

    val p = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(
            screen = state.cursorPosition,
            state = state,
            basis = basis
        )

    val dir = state.pendingDirection?.normalizedOrNull() ?: return


    drawInfiniteAOLineOnScreen(
        state = state,
        throughLocal = p,
        directionLocal = dir,
        color = Color.Gray,
        lineWidth = 1f,
        lineStyle = LineStyle.Dashed,
        pxPerPt = 1f,
        alpha = 0.45f,
        previewDashPattern = true
    )
}
