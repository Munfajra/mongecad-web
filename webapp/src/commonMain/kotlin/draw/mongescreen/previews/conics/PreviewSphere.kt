package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.orth.conics.drawEllipseFromDiameters
import model.*
import monge.input.ConicArcs.single.getLogicalCursorNarys
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.conixections.conjugateDiameterInputFromRadii
import state.MongeState
import utils.getLogicalCursor

private fun updateSpherePreviewRadius(state: MongeState, snappedPointLogical: Offset?) {
    val centerId = state.pendingId1 ?: return
    val center2D = when (state.mongeMode) {
        DrawingModeMonge.PUDORYS -> {
            state.pointsPudorys.find { it.parent?.id == centerId }?.let { Offset(it.x, it.y) }
        }
        DrawingModeMonge.NARYS -> {
            state.pointsNarys.find { it.parent?.id == centerId }?.let { Offset(it.x, it.z) }
        }
    } ?: return

    val cursor = when (state.mongeMode) {
        DrawingModeMonge.NARYS -> getLogicalCursorNarys(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection
        )
        DrawingModeMonge.PUDORYS -> getLogicalCursor(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
    }
    val r = (cursor - center2D).getDistance()
    state.spherePreviewRadius = if (r > 0f) r else null
}
private fun DrawScope.drawDashedCirclePreviewAt(centerLogical: Offset, rLogical: Float, state: MongeState) {
    val screenCenter = centerLogical * state.scale + state.canvasOffset
    val screenRadius = rLogical * state.scale
    drawCircle(
        color = Color.Gray,
        radius = screenRadius,
        center = screenCenter,
        style = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
        )
    )
    val cross = 6f
    drawLine(Color.Red, screenCenter + Offset(-cross, 0f), screenCenter + Offset(cross, 0f), 2f)
    drawLine(Color.Red, screenCenter + Offset(0f, -cross), screenCenter + Offset(0f, cross), 2f)
}
fun DrawScope.drawSpherePreviewPudorys(state: MongeState, snappedPointLogical: Offset?) {
    if (state.drawobjects != Mongeobjects.SPHERE) return
    val centerId = state.pendingId1 ?: return
    // Aktualizuj r jen pokud je aktivní výběr poloměru
    if (state.projectionPhase == "sphere_radius_pick_pudorys") {
        updateSpherePreviewRadius(state, snappedPointLogical)
    }
    val r = state.spherePreviewRadius ?: return

    // centrum PUDORYS
    state.pointsPudorys.find { it.parent?.id == centerId }?.let {
        drawDashedCirclePreviewAt(Offset(it.x, it.y), r, state)
    }
}
fun DrawScope.drawSpherePreviewNarys(state: MongeState, snappedPointLogical: Offset?) {
    if (state.drawobjects != Mongeobjects.SPHERE) return
    val centerId = state.pendingId1 ?: return
    if (state.projectionPhase == "sphere_radius_pick_narys") {
        updateSpherePreviewRadius(state, snappedPointLogical)
    }
    val r = state.spherePreviewRadius ?: return
    // centrum NÁRYS
    state.pointsNarys.find { it.parent?.id == centerId }?.let {
        drawDashedCirclePreviewAt(Offset(it.x, -it.z), r, state)
    }
}

// Axonometrický náhled koule se na web neportuje (web nemá AXO mód).
