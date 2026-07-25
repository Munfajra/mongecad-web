package draw.mongescreen.previews.traces.narys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarys
import model.*
import model.classes.Point3DNarys
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.PreviewNarysTraceDirCursor (state: MongeState, snappedPointLogical: Offset?){
    if (state.projectionPhase == "plane_trace_narys_direction" &&
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.xOnX12Narys != null && (state.constructionModifier != ConstructionModifier.PARALLEL && state.constructionModifier != ConstructionModifier.ORTHOGONAL)
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
        val cursorWorld = Offset(cursorLogical.x, -cursorLogical.y)
        drawDashedPreviewLineNarys(state.xOnX12Narys!!, cursorWorld, state.scale, state.canvasOffset,  color = Color.Gray, clipToAboveZ = true)
    }
}
fun DrawScope.PreviewNarysTraceParallelCursor(state: MongeState, snappedPointLogical: Offset?) {
    val baseDirection = state.selectedLineForParallelPlaneNarys?.direction
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
        ConstructionModifier.ORTHOGONAL -> baseDirection?.let { Offset(-it.y, it.x) }
        ConstructionModifier.PARALLEL -> baseDirection
        else -> null
    }

    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        effectiveDirection != null &&
        (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL) &&
        state.xOnX12Narys == null
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

        val through = Point3DNarys(cursorLogical.x, -cursorLogical.y, name = "?")

        drawDashedParallelLinePreviewNarys(
            through = through,
            direction = effectiveDirection,
            scale = state.scale,
            color = Color.Gray,
            canvasOffset = state.canvasOffset,
            clipToAboveZ = true
        )
    }
}

fun DrawScope.PreviewNarysTraceCursor (state: MongeState,snappedPointLogical: Offset?) {
    if ( (state.projectionPhase == "plane_trace_narys_start"||state.projectionPhase== "plane_trace_single_narys_start") &&
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects. PLANE &&
        state.firstPlaneTraceStartNarys != null) {
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
            -cursorLogical.y
        )
        drawDashedPreviewLineNarys(
            start =  state.firstPlaneTraceStartNarys!!,
            cursorWorld = cursorWorld,
            scale =  state.scale,
            color = Color.Gray,
            canvasOffset =  state.canvasOffset,
            clipToAboveZ = true
        )
    }
    if (
        state.projectionPhase == "plane_trace_narys_special_direction" &&
        state.mongeMode == DrawingModeMonge.NARYS &&
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
        val clipAboveX12 = true
        if (clipAboveX12 && yConst > 0f) return

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