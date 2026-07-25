package draw.mongescreen.previews.polygons


import monge.input.axo.axoOverlayToScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.tools.drawRedCross
import model.Mongeobjects
import model.Offset3D
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.projectPoint3DToAxoLocal


import geometry.Vec3
import geometry.liftNarysToPlane
import geometry.liftPudorysToPlane
import monge.input.planeobjects.conicsections.makeRegularPolygonVertices3D
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld

// ---------- PREVIEW: pravidelný n-úhelník ----------


fun DrawScope.drawRegularPolygonPreview(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    when (state.projectionMode) {
        ProjectionMode.MONGE, ProjectionMode.KOTO -> {
            if (state.drawobjects != Mongeobjects.REGULAR_POLYGON_IN_PLANE) return

            val phase = state.projectionPhase
            val isPreview = phase == "rp_vertex_pud" || phase == "rp_vertex_nar"
            if (!isPreview) return

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

            val plane3D = state.selectedPlaneForCircle ?: return
            val eq = plane3D.equation ?: return
            val nSides = state.regularPolygon.sides.coerceIn(3, 30)

            val centerLogical = state.pendingPoint1 ?: return

            // 1) zvedni střed a "kurzorový" vrchol do 3D podle aktivního pohledu
            val (C3, V3) = when (phase) {
                "rp_vertex_pud" -> {
                    val c3 = liftPudorysToPlane(centerLogical.x, centerLogical.y, eq) ?: return
                    val v3 = liftPudorysToPlane(cursorLogical.x, cursorLogical.y, eq) ?: return
                    c3 to v3
                }

                "rp_vertex_nar" -> {
                    val c3 = liftNarysToPlane(centerLogical.x, -centerLogical.y, eq) ?: return
                    val v3 = liftNarysToPlane(cursorLogical.x, -cursorLogical.y, eq) ?: return
                    c3 to v3
                }

                else -> return
            }

            // 2) invalidní poloměr → nic
            val dx = V3.x - C3.x;
            val dy = V3.y - C3.y;
            val dz = V3.z - C3.z
            if (dx * dx + dy * dy + dz * dz < 1e-8f) return

            // 3) spočti vrcholy a projekce
            val normal = Vec3(eq.a, eq.b, eq.c).normalize()
            val verts3D = makeRegularPolygonVertices3D(
                center = Vec3(C3.x, C3.y, C3.z),
                vertex0 = Vec3(V3.x, V3.y, V3.z),
                n = nSides,
                planeNormal = normal
            )
            val vertsP = verts3D.map { Offset(it.x, it.y) }     // Půdorys
            val vertsN = verts3D.map { Offset(it.x, -it.z) }    // Nárys (kreslíš-li −z)

            // 4) červený křížek ve středu v obou průmětech
            drawRedCross(Offset(C3.x, C3.y), size = 8f, state = state)
            if (state.projectionMode != ProjectionMode.KOTO) {
                drawRedCross(Offset(C3.x, -C3.z), size = 8f, state = state)
            }
            // 5) pevný náhled: šedý čárkovaný obrys v obou pohledech
            drawPolygonOutlinePreviewDashed(vertsP, state)
            if (state.projectionMode != ProjectionMode.KOTO) {
                drawPolygonOutlinePreviewDashed(vertsN, state)
            }
        }
        // AXO větev náhledu n-úhelníku – web axonometrii nekreslí.
        ProjectionMode.AXO -> Unit
        ProjectionMode.PLANE -> {
            if (state.drawobjects != Mongeobjects.REGULAR_POLYGON_IN_PLANE) return

            val phase = state.projectionPhase
            if (phase != "rp_vertex_pud") return   // ✅ preview jen v půdorysu

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

            val nSides = state.regularPolygon.sides.coerceIn(3, 30)
            val centerLogical = state.pendingPoint1 ?: return

            // invalidní poloměr → nic
            val dx = cursorLogical.x - centerLogical.x
            val dy = cursorLogical.y - centerLogical.y
            if (dx * dx + dy * dy < 1e-8f) return

            // 1) spočti 2D vrcholy v půdorysu (stejně jako při finálním vytvoření)
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            val startAngle = kotlin.math.atan2(dy, dx)
            val step = (2.0 * kotlin.math.PI / nSides).toFloat()

            val vertsP: List<Offset> = List(nSides) { i ->
                val a = startAngle + i * step
                Offset(
                    x = centerLogical.x + r * kotlin.math.cos(a),
                    y = centerLogical.y + r * kotlin.math.sin(a)
                )
            }

            // 2) červený křížek jen ve středu v půdorysu
            drawRedCross(centerLogical, size = 8f, state = state)

            // 3) dashed obrys jen v půdorysu
            drawPolygonOutlinePreviewDashed(vertsP, state)
        }

    }

}
// Pevně šedě a čárkovaně (nezávislé na currentLineStyleSettings)
private fun DrawScope.drawPolygonOutlinePreviewDashed(
    ptsLogical: List<Offset>,
    state: MongeState,
    gray: Color = Color(0xFF8A8A8A),
    strokeWidthPx: Float = 1.25f,
    dashOnPx: Float = 8f,
    dashOffPx: Float = 6f
) {
    if (ptsLogical.size < 2) return
    val dash = PathEffect.dashPathEffect(floatArrayOf(dashOnPx, dashOffPx), 0f)
    for (i in ptsLogical.indices) {
        val a = ptsLogical[i].toScreenOld(state.scale, state.canvasOffset)
        val b = ptsLogical[(i + 1) % ptsLogical.size].toScreenOld(state.scale, state.canvasOffset)
        drawLine(
            color = gray,
            start = a,
            end = b,
            strokeWidth = strokeWidthPx,
            pathEffect = dash
        )
    }
}

private fun DrawScope.drawRedCrossAxo(
    centerLocal: Offset,
    state: MongeState,
    size: Float = 8f,
    color: Color = Color.Red
) {
    val basis = state.basis ?: return
    val center = axoOverlayToScreen(centerLocal, state, basis)
    drawLine(color, center.copy(x = center.x - size), center.copy(x = center.x + size), strokeWidth = 1.5f)
    drawLine(color, center.copy(y = center.y - size), center.copy(y = center.y + size), strokeWidth = 1.5f)
}

private fun DrawScope.drawPolygonOutlinePreviewDashedAxo(
    ptsLocal: List<Offset>,
    state: MongeState,
    gray: Color = Color(0xFF8A8A8A),
    strokeWidthPx: Float = 1.25f,
    dashOnPx: Float = 8f,
    dashOffPx: Float = 6f
) {
    if (ptsLocal.size < 2) return
    val basis = state.basis ?: return
    val dash = PathEffect.dashPathEffect(floatArrayOf(dashOnPx, dashOffPx), 0f)
    for (i in ptsLocal.indices) {
        drawLine(
            color = gray,
            start = axoOverlayToScreen(ptsLocal[i], state, basis),
            end = axoOverlayToScreen(ptsLocal[(i + 1) % ptsLocal.size], state, basis),
            strokeWidth = strokeWidthPx,
            pathEffect = dash
        )
    }
}
