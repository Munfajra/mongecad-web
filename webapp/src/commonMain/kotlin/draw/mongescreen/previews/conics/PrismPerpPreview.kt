package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.*
import model.Offset3D
import model.classes.projectPoint3DToAxoLocal
import monge.input.ConicArcs.single.getLogicalCursorNarys
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.quadrics.cylindricalsurface.computePerpCylinderT
import monge.input.quadrics.cylindricalsurface.computePerpCylinderTAxo
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld

// Náhled kolmého hranolu během nastavování výšky kurzorem.
fun DrawScope.drawPerpPrismPreview(state: MongeState, snappedPointLogical: Offset?) {
    if (state.drawobjects != Mongeobjects.PRISM) return
    val polygonId = state.pendingPrismPolygonId ?: return
    val normal = state.pendingPrismNormal ?: return
    val baseCenter = state.pendingPrismBaseCenter ?: return
    val polygon = state.polygons3D.firstOrNull { it.id == polygonId } ?: return
    val baseVerts = polygon.vertexPointIds
        .mapNotNull { id -> state.sharedPoints3D.firstOrNull { it.id == id } }
    if (baseVerts.size < 3) return

    val previewColor = Color.Gray.copy(alpha = 0.5f)
    val previewWidth = 1.5f
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

    fun drawInView(t: Float, proj: (Offset3D) -> Offset) {
        val n = baseVerts.size
        val lower = baseVerts.map { proj(Offset3D(it.x, it.y, it.z)) }
        val upper = baseVerts.map {
            proj(Offset3D(it.x + normal.x * t, it.y + normal.y * t, it.z + normal.z * t))
        }
        fun pt(p: Offset) = p.toScreenOld(state.scale, state.canvasOffset)
        for (i in 0 until n) {
            val j = (i + 1) % n
            drawLine(previewColor, pt(lower[i]), pt(lower[j]), previewWidth, pathEffect = dash)
            drawLine(previewColor, pt(upper[i]), pt(upper[j]), previewWidth, pathEffect = dash)
            drawLine(previewColor, pt(lower[i]), pt(upper[i]), previewWidth, pathEffect = dash)
        }
    }

    if (state.projectionMode == ProjectionMode.AXO) {
        val basis = state.basis ?: return
        val cursor = snappedPointLogical ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
        val t = computePerpCylinderTAxo(cursor, baseCenter, normal, basis) ?: return
        drawInView(t) { p -> basis.origin + projectPoint3DToAxoLocal(p, basis) }
        return
    }

    val cursor = when (state.mongeMode) {
        DrawingModeMonge.NARYS -> getLogicalCursorNarys(
            snappedPointLogical, state.cursorPosition,
            state.canvasOffset, state.scale,
            state.canvasWidth, state.canvasHeight,
            state.xAxisDirection
        )
        DrawingModeMonge.PUDORYS -> getLogicalCursor(
            snappedPointLogical, state.cursorPosition,
            state.canvasOffset, state.scale,
            state.canvasWidth, state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
    }
    val t = computePerpCylinderT(state.mongeMode, cursor, baseCenter, normal) ?: return
    drawInView(t) { p -> Offset(p.x, p.y) }
    drawInView(t) { p -> Offset(p.x, -p.z) }
}
