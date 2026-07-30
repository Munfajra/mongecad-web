package draw.mongescreen.objects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.ProjectionMode
import model.classes.RuledSurface3D
import model.classes.projectPoint3DToAxoLocal
import model.runtimeDrawColor
import geometry.logicalToScreen
import state.MongeState

// Tvořice se nekreslí tady: jsou materializované jako skutečné Line3D
// (viz syncRuledSurfaceGeneratorLines) a kreslí je běžná pipeline přímek
// včetně ořezu customTrimRange. Zde zbývá jen odvozený obrys.

private enum class RuledProjection { PUDORYS, NARYS, BOKORYS, AXO }

fun DrawScope.drawRuledSurfaceOutlinePudorys(state: MongeState) {
    for (surface in state.ruledSurfaces) {
        if (!surface.show) continue
        val curve = surface.outlineCurveIdPudorys?.let { id -> state.curvesPudorys.firstOrNull { it.id == id } } ?: continue
        val logical = curve.polylineLocal.orEmpty()
        val edges = clipOutlineAtX12(
            polylineEdges(logical, curve.closed),
            enabled = state.projectionMode == ProjectionMode.MONGE && state.defaultClipBelowX12Pudorys,
        )
        drawSurfaceOutlineEdges(state, surface, edges.map { (a, b) ->
            logicalToScreen(a, state.canvasOffset, state.scale) to logicalToScreen(b, state.canvasOffset, state.scale)
        })
    }
}

fun DrawScope.drawRuledSurfaceOutlineNarys(state: MongeState) {
    for (surface in state.ruledSurfaces) {
        if (!surface.show) continue
        val curve = surface.outlineCurveIdNarys?.let { id -> state.curvesNarys.firstOrNull { it.id == id } } ?: continue
        // Uloženo jako (x, z). Ořez se provádí ještě v této geometrické
        // soustavě (z >= 0), až potom se pro Canvas nárys převádí z na -y.
        val logical = curve.polylineLocal.orEmpty()
        val edges = clipOutlineAtX12(
            polylineEdges(logical, curve.closed),
            enabled = state.projectionMode == ProjectionMode.MONGE && state.defaultClipAboveX12Narys,
        )
        drawSurfaceOutlineEdges(state, surface, edges.map { (a, b) ->
            logicalToScreen(Offset(a.x, -a.y), state.canvasOffset, state.scale) to
                logicalToScreen(Offset(b.x, -b.y), state.canvasOffset, state.scale)
        })
    }
}

// Obrys přímkové plochy v axonometrii se na web neportuje (web nemá AXO mód).

private fun DrawScope.drawSurfaceOutlineEdges(
    state: MongeState,
    surface: RuledSurface3D,
    edges: List<Pair<Offset, Offset>>,
) {
    if (edges.isEmpty()) return
    fun draw(color: androidx.compose.ui.graphics.Color, width: Float) {
        for ((a, b) in edges) drawLine(color, a, b, width)
    }
    if (state.selectedRuledSurfaceId == surface.id) draw(state.selectedHaloColor, surface.wireWidth + 4f)
    draw(surface.color.runtimeDrawColor(), surface.wireWidth)
}

/** Hrany lomené čáry; uzavřená se doplní o hranu z posledního do prvního bodu. */
private fun polylineEdges(
    points: List<Offset>,
    closed: Boolean,
): List<Pair<Offset, Offset>> {
    if (points.size < 2) return emptyList()
    val last = if (closed) points.size else points.size - 1
    return (0 until last).map { i -> points[i] to points[(i + 1) % points.size] }
}

/** Ořízne jednotlivé hrany obrysu na kladnou stranu geometrické osy x₁₂. */
private fun clipOutlineAtX12(
    edges: List<Pair<Offset, Offset>>,
    enabled: Boolean,
): List<Pair<Offset, Offset>> {
    if (!enabled) return edges
    return edges.mapNotNull { (a, b) ->
        val aInside = a.y >= 0f
        val bInside = b.y >= 0f
        when {
            aInside && bInside -> a to b
            !aInside && !bInside -> null
            else -> {
                val t = -a.y / (b.y - a.y)
                val intersection = Offset(a.x + t * (b.x - a.x), 0f)
                if (aInside) a to intersection else intersection to b
            }
        }
    }
}

private fun projectionVisibleInAxo(
    state: MongeState,
    surface: RuledSurface3D,
    projection: RuledProjection,
): Boolean = when (projection) {
    RuledProjection.PUDORYS -> surface.outlineCurveIdPudorys?.let { id ->
        state.curvesPudorys.firstOrNull { it.id == id }?.showInAxo
    } ?: true
    RuledProjection.NARYS -> surface.outlineCurveIdNarys?.let { id ->
        state.curvesNarys.firstOrNull { it.id == id }?.showInAxo
    } ?: true
    RuledProjection.BOKORYS -> surface.outlineCurveIdBokorys?.let { id ->
        state.curvesBokorys.firstOrNull { it.id == id }?.showInAxo
    } ?: true
    RuledProjection.AXO -> surface.outlineCurveIdAxo?.let { id ->
        state.curvesAxo.firstOrNull { it.id == id }?.showInAxo
    } ?: true
}
