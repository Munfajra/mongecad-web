package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.Mongeobjects
import model.Offset3D
import model.ProjectionMode
import model.classes.projectPoint3DToAxoLocal
import monge.input.solids.platonicPreviewEdges
import state.MongeState
import utils.toScreenOld

// Čárkovaný náhled platónského tělesa (krychle / čtyřstěn) během volby orientace.
fun DrawScope.drawPlatonicPreview(state: MongeState) {
    if (state.drawobjects != Mongeobjects.PLATONIC_SOLID) return
    if (state.pendingPlatonicPolygonId == null) return
    val edges = platonicPreviewEdges(state) ?: return

    val previewColor = Color.Gray.copy(alpha = 0.6f)
    val previewWidth = 1.5f
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

    fun drawInView(proj: (Offset3D) -> Offset) {
        fun pt(p: Offset) = p.toScreenOld(state.scale, state.canvasOffset)
        edges.forEach { (a, b) ->
            drawLine(previewColor, pt(proj(a)), pt(proj(b)), previewWidth, pathEffect = dash)
        }
    }

    if (state.projectionMode == ProjectionMode.AXO) {
        val basis = state.basis ?: return
        drawInView { p -> basis.origin + projectPoint3DToAxoLocal(p, basis) }
        return
    }
    drawInView { p -> Offset(p.x, p.y) }
    if (state.projectionMode != ProjectionMode.KOTO) {
        drawInView { p -> Offset(p.x, -p.z) }
    }
}
