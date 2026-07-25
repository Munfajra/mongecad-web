package draw.mongescreen.previews.conicsarcs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.orth.drawCircleArc
import model.*
import monge.input.conixections.projectToCircle
import monge.input.conixections.projectToCircleNarys
import state.MongeState
import utils.getLogicalCursor
import kotlin.math.sqrt

fun DrawScope.drawCircleArcPudorys(state: MongeState, snappedPointLogical: Offset?) {
    val cursor = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    if ((state.drawobjects == Mongeobjects.CONICARC || state.drawobjects == Mongeobjects.CONICARCAS) &&
        state.mongeMode == DrawingModeMonge.PUDORYS
    ) {
        val circleId = state.activeConicIdForArc
        val circle = state.circlesPudorys.find { it.id == circleId } ?: return
        val aStored = state.pendingArcA ?: return

        val x0 = -circle.d / 2f
        val y0 = -circle.e / 2f
        val r2 = x0 * x0 + y0 * y0 - circle.f
        if (r2 <= 0f) return

        val radius = sqrt(r2)
        val center = Offset(x0, y0)

        val Aproj = projectToCircle(circle, aStored)
        val Bproj = projectToCircle(circle, cursor)

        drawCircleArc(
            centerLogical = center,
            radiusLogical = radius,
            a = Aproj,
            b = Bproj,
            mode = state.circleArcMode[circleId] ?: ArcMode.SHORTEST,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            color = Color.Red,
            strokeWidth = circle.strokeWidth + 9f,
            lineStyle = LineStyle.Solid,
            pxPerPt = 1f
        )
    }
}
fun DrawScope.drawCircleArcNarys(state: MongeState, snappedPointLogical: Offset?) {
    val cursor = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    if ((state.drawobjects == Mongeobjects.CONICARC || state.drawobjects == Mongeobjects.CONICARCAS) &&
        state.mongeMode == DrawingModeMonge.NARYS
    ) {
        val circleId = state.activeConicIdForArc
        val circle = state.circlesNarys.find { it.id == circleId } ?: return
        val aStored = state.pendingArcA ?: return

        val x0 = -circle.d / 2f
        val z0 = -circle.e / 2f
        val r2 = x0 * x0 + z0 * z0 - circle.f
        if (r2 <= 0f) return

        val radius = sqrt(r2)

        // NÁRYS: geometrický střed je (x0, z0), ale pro DrawScope kreslíme jako (x0, -z0)
        val center = Offset(x0, -z0)

        val Aproj = projectToCircleNarys(circle, aStored)
        val Bproj = projectToCircleNarys(circle, cursor)

        drawCircleArc(
            centerLogical = center,
            radiusLogical = radius,
            a = Aproj,
            b = Bproj,
            mode = state.circleArcMode[circleId] ?: ArcMode.SHORTEST,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            color = Color.Red,
            strokeWidth = circle.strokeWidth + 9f,
            lineStyle = LineStyle.Solid,
            pxPerPt = 1f
        )
    }
}