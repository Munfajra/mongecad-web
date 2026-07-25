package draw.mongescreen.previews.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.DrawingModeMonge
import model.ProjectionMode
import model.classes.PLANE_NARYS_ID
import model.classes.PLANE_PUDORYS_ID
import state.MongeState

// Zavolej uvnitř Canvas/DrawScope (ideálně už po translate(marginPx, marginPx), pokud ho používáš)
fun DrawScope.drawHalfPlaneX12Overlay(
    state: MongeState,
    tint: Color = Color(0xFF2196F3),     // základní modrá
    alpha: Float = 0.08f,                // základní jemná alpha
    selectedTint: Color = Color(0xFF1CD9B3), // růžová pro vybranou průmětnu
    selectedAlpha: Float = 0.3f
) {
    if (state.projectionMode == ProjectionMode.MONGE) {
        val yLine = state.canvasOffset.y    // obrazovková Y-pozice osy x₁₂
        val h = size.height
        val w = size.width

        fun clampTopBottom(topWanted: Float, bottomWanted: Float): Pair<Float, Float> {
            val top = topWanted.coerceIn(0f, h)
            val bottom = bottomWanted.coerceIn(0f, h)
            return top to bottom
        }

        fun fillBand(top: Float, bottom: Float, color: Color, a: Float) {
            val height = (bottom - top).coerceAtLeast(0f)
            if (height > 0f) {
                drawRect(
                    color = color.copy(alpha = a),
                    topLeft = Offset(0f, top),
                    size = Size(w, height)
                )
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 1) Tvůj původní overlay podle aktivního pohledu
        //    PUDORYS → nad osu (0..yLine), NARYS → pod osu (yLine..h)
        when (state.mongeMode) {
            DrawingModeMonge.PUDORYS -> {
                val (top, bottom) = clampTopBottom(0f, yLine)
                fillBand(top, bottom, tint, alpha)
            }

            DrawingModeMonge.NARYS -> {
                val (top, bottom) = clampTopBottom(yLine, h)
                fillBand(top, bottom, tint, alpha)
            }

        }

        // ─────────────────────────────────────────────────────────────
        // 2) Doplňkové zabarvení podle vybrané referenční roviny
        //    (využívá state.selectedPlaneForCircle; pokud máš název s překlepem,
        //     uprav proměnnou zde)
        val selected = state.selectedPlaneForCircle
        if (selected != null) {
            when (selected.id) {
                PLANE_NARYS_ID -> {
                    // Nárysna → „nahoru od x₁₂“ (0..yLine)
                    val (top, bottom) = clampTopBottom(0f, yLine)
                    fillBand(top, bottom, selectedTint, selectedAlpha)
                }

                PLANE_PUDORYS_ID -> {
                    // Půdorysna → „dolů od x₁₂“ (yLine..h)
                    val (top, bottom) = clampTopBottom(yLine, h)
                    fillBand(top, bottom, selectedTint, selectedAlpha)
                }
            }
        }
    }
}
