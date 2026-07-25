package draw.mongescreen.previews.traces.pudorys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import model.*
import model.classes.Point3DPudorys
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.previewPudorysTraceCursor(state: MongeState, snappedPointLogical:Offset?) {
    if (( state.projectionPhase == "plane_trace_pudorys_start" || state.projectionPhase ==  "plane_trace_single_pudorys_start")&&
        state. mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects. PLANE &&
        state.firstPlaneTraceStartPudorys != null) {
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
        val cursorWorld = Offset(
            cursorLogical.x,
            cursorLogical.y
        )
        val clip = if (state.projectionMode == ProjectionMode.KOTO) false else true
        drawDashedPreviewLinePudorys(
            start =  state.firstPlaneTraceStartPudorys!!,
            cursorWorld = cursorWorld,
            scale =  state.scale,
            canvasOffset =  state.canvasOffset,
            clipToBelowX12 = clip,
            color = Color.Gray,
        )
    }
}
fun DrawScope.previewPudorysTraceParallelCursor(state: MongeState, snappedPointLogical: Offset?) {
    val baseDirection = state.selectedLineForParallelPlanePudorys?.direction
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
        ConstructionModifier.ORTHOGONAL -> baseDirection?.let { Offset(-it.y, it.x) }
        ConstructionModifier.PARALLEL -> baseDirection
        else -> null
    }

    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        effectiveDirection != null &&
        (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL) &&
        state.xOnX12Pudorys == null
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
            clipToBelowX12 = true,
            color = Color.Gray,
        )
    }
}

fun DrawScope.previewPudorysTraceDirCursor(state: MongeState,snappedPointLogical: Offset?){
    if ((state.projectionPhase == "plane_trace_pudorys_direction" )&&
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.xOnX12Pudorys != null && (state.constructionModifier != ConstructionModifier.PARALLEL && state.constructionModifier != ConstructionModifier.ORTHOGONAL)
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
        val cursorWorld = Offset(cursorLogical.x, cursorLogical.y)
        drawDashedPreviewLinePudorys(
            start = state.xOnX12Pudorys!!,
            cursorWorld,
            scale = state.scale,
            color = Color.Gray,
            canvasOffset = state.canvasOffset,
            clipToBelowX12 = true,

        )
    }
    if (
        state.projectionPhase == "plane_trace_pudorys_special_direction" &&
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.PLANE
    ) {
        // kurzor v logických souřadnicích
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
        val yConst = cursorLogical.y

        // aktuální viditelný obdélník ve světě (logické souřadnice)
        val worldLeft  = -state.canvasOffset.x / state.scale
        val worldRight = (size.width - state.canvasOffset.x) / state.scale

        // malá rezerva mimo okraj, ať to netrhá při panu
        val pad = (50f / state.scale)

        val p1World = Offset(worldLeft - pad,  yConst)
        val p2World = Offset(worldRight + pad, yConst)

        // převod do screenu
        fun Offset.toScreen(scale: Float, off: Offset) = this * scale + off
        val p1Screen = p1World.toScreen(state.scale, state.canvasOffset)
        val p2Screen = p2World.toScreen(state.scale, state.canvasOffset)

        // volitelně: ořez podle x12 (např. jen "pod" x12) v PŮDORYSU: y >= 0
        val clipBelowX12 = true
        if (clipBelowX12 && yConst < 0f) return

        // kreslení – přerušovaně
        drawLine(
            color = Color.Gray,
            start = p1Screen,
            end = p2Screen,
            strokeWidth = 1f, // případně state.previewStrokeWidth
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    }

}