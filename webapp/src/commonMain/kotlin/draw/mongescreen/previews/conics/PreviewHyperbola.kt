package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import draw.mongescreen.objects.orth.conics.drawConicHyperbolaNarys
import draw.mongescreen.objects.orth.conics.drawConicHyperbolaPudorys
import model.LineStyle
import model.XAxisDirection
import model.YAxisDirectionPlane
import state.MongeState
import state.snapMonge.computeIntersection
import utils.getLogicalCursor
import utils.normalize
import utils.projectPointOntoLine
import utils.toScreenOld

fun DrawScope.drawHyperbolaConstructionPudorys(state: MongeState, snappedPointLogical: Offset?) {
    if (state.projectionPhase != "hyperbola_vertex") return
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

    val line1 = state.selectedLineForParallelPudorys ?: return
    val line2 = state.selectedLineForParallelPudorysSecond ?: return

    // Spočítej průsečík asymptot
    val center = computeIntersection(
        Offset(line1.point.x, line1.point.y),
        Offset(line1.direction.x, line1.direction.y),
        Offset(line2.point.x, line2.point.y),
        Offset(line2.direction.x, line2.direction.y)
    ) ?: return

    val v1 = line1.direction.normalize()
    val v2 = line2.direction.normalize()
    val axisMain = (v1 + v2).normalize()
    val axisSecondary = Offset(-axisMain.y, axisMain.x)

    val projMain = projectPointOntoLine(cursorLogical, center, axisMain)
    val projSecondary = projectPointOntoLine(cursorLogical, center, axisSecondary)

    val distMain = (projMain - cursorLogical).getDistance()
    val distSecondary = (projSecondary - cursorLogical).getDistance()

    val (selectedAxis, selectedProj) = if (distMain < distSecondary) {
        axisMain to projMain
    } else {
        axisSecondary to projSecondary
    }

    val otherAxis = if (selectedAxis == axisMain) axisSecondary else axisMain

    // 📏 Délka os pro kreslení
    val len = 1000f

    // 🟡 Osa, kterou uživatel pravděpodobně vybírá (plnější barva)
    drawLine(
        color = Color.Red,
        start = (center - selectedAxis * len).toScreenOld(state.scale, state.canvasOffset),
        end = (center + selectedAxis * len).toScreenOld(state.scale, state.canvasOffset),
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )

    // ⚪ Druhá osa (čárkovaně)
    drawLine(
        color = Color.LightGray,
        start = (center - otherAxis * len).toScreenOld(state.scale, state.canvasOffset),
        end = (center + otherAxis * len).toScreenOld(state.scale, state.canvasOffset),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )

    // 🔵 Promítané body
    drawRedCross(center = if (selectedProj == projMain) projSecondary else projMain, state = state, size = 8f, color = Color.LightGray)
    drawRedCross(
        center = selectedProj,
        state = state
    )

}
fun DrawScope.drawHyperbolaPreviewPudorys(state: MongeState,snappedPointLogical: Offset?) {
    if (state.projectionPhase != "hyperbola_vertex") return

    val line1 = state.selectedLineForParallelPudorys ?: return
    val line2 = state.selectedLineForParallelPudorysSecond ?: return

    val center = computeIntersection(
        Offset(line1.point.x, line1.point.y),
        Offset(line1.direction.x, line1.direction.y),
        Offset(line2.point.x, line2.point.y),
        Offset(line2.direction.x, line2.direction.y)
    ) ?: return

    val v1 = line1.direction.normalize()
    val v2 = line2.direction.normalize()
    val axisMain = (v1 + v2).normalize()
    val axisSecondary = Offset(-axisMain.y, axisMain.x)

    val logical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    val projMain = projectPointOntoLine(logical, center, axisMain)
    val projSecondary = projectPointOntoLine(logical, center, axisSecondary)

    val distMain = (projMain - logical).getDistance()
    val distSecondary = (projSecondary - logical).getDistance()

    val (vertex, axis) = if (distMain < distSecondary) {
        projMain to axisMain
    } else {
        projSecondary to axisSecondary
    }

    drawConicHyperbolaPudorys(
        center = center,
        asymptote1 = line1.direction,
        vertex = vertex,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        color = Color.Gray,
        strokeWidth = 1.5f,
        lineStyle = LineStyle.Dashed,
        axis = axis
    )
}
fun DrawScope.drawHyperbolaConstructionNarys(state: MongeState, snappedPointLogical: Offset?) {
    if (state.projectionPhase != "hyperbola_vertex_narys") return
    val cursorLogical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    ).let { Offset(it.x, -it.y) }  // Inverze nárysových souřadnic

    val line1 = state.selectedLineForParallelNarys ?: return
    val line2 = state.selectedLineForParallelNarysSecond ?: return

    val center = computeIntersection(
        Offset(line1.point.x, line1.point.z),
        Offset(line1.direction.x, line1.direction.y),
        Offset(line2.point.x, line2.point.z),
        Offset(line2.direction.x, line2.direction.y)
    ) ?: return

    val v1 = line1.direction.normalize()
    val v2 = line2.direction.normalize()
    val axisMain = (v1 + v2).normalize()
    val axisSecondary = Offset(-axisMain.y, axisMain.x)

    val projMain = projectPointOntoLine(cursorLogical, center, axisMain)
    val projSecondary = projectPointOntoLine(cursorLogical, center, axisSecondary)

    val distMain = (projMain - cursorLogical).getDistance()
    val distSecondary = (projSecondary - cursorLogical).getDistance()

    val (selectedAxis, selectedProj) = if (distMain < distSecondary) {
        axisMain to projMain
    } else {
        axisSecondary to projSecondary
    }

    val otherAxis = if (selectedAxis == axisMain) axisSecondary else axisMain
    val len = 1000f

    // 🟡 Aktivní osa
    val startSelected = (center - selectedAxis * len).invertY().toScreenOld(state.scale, state.canvasOffset)
    val endSelected = (center + selectedAxis * len).invertY().toScreenOld(state.scale, state.canvasOffset)

    drawLine(
        color = Color.Red,
        start = startSelected,
        end = endSelected,
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )

    val startOther = (center - otherAxis * len).invertY().toScreenOld(state.scale, state.canvasOffset)
    val endOther = (center + otherAxis * len).invertY().toScreenOld(state.scale, state.canvasOffset)

    drawLine(
        color = Color.LightGray,
        start = startOther,
        end = endOther,
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )


    // 🔴 Promítané body
    drawRedCross(center = (if (selectedProj == projMain) projSecondary else projMain).invertY(), state = state, size = 8f, color = Color.LightGray)
    drawRedCross(center = selectedProj.invertY(), state = state)
}
fun Offset.invertY(): Offset = Offset(this.x, -this.y)
fun DrawScope.drawHyperbolaPreviewNarys(state: MongeState, snappedPointLogical: Offset?) {
    if (state.projectionPhase != "hyperbola_vertex_narys") return

    val line1 = state.selectedLineForParallelNarys ?: return
    val line2 = state.selectedLineForParallelNarysSecond ?: return

    val center = computeIntersection(
        Offset(line1.point.x, line1.point.z),
        Offset(line1.direction.x, line1.direction.y),
        Offset(line2.point.x, line2.point.z),
        Offset(line2.direction.x, line2.direction.y)
    ) ?: return

    val v1 = line1.direction.normalize()
    val v2 = line2.direction.normalize()
    val axisMain = (v1 + v2).normalize()
    val axisSecondary = Offset(-axisMain.y, axisMain.x)

    val logical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    ).let { Offset(it.x, -it.y) }

    val projMain = projectPointOntoLine(logical, center, axisMain)
    val projSecondary = projectPointOntoLine(logical, center, axisSecondary)

    val distMain = (projMain - logical).getDistance()
    val distSecondary = (projSecondary - logical).getDistance()

    val (vertex, axis) = if (distMain < distSecondary) {
        projMain to axisMain
    } else {
        projSecondary to axisSecondary
    }

    drawConicHyperbolaNarys(
        center = center,
        asymptote1 = line1.direction,
        vertex = vertex,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        color = Color.Gray,
        strokeWidth = 1.5f,
        lineStyle = LineStyle.Dashed,
        axis = axis
    )
}

