package draw.mongescreen.previews.points

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.HOVER_HALO_EXTRA_PX
import draw.mongescreen.objects.POINT_STROKE_WEIGHT
import draw.mongescreen.objects.SELECTION_HALO_EXTRA_PX
import draw.mongescreen.objects.strokePx
import model.DrawingModeMonge
import model.Mongeobjects
import model.classes.CurvePudRef
import model.runtimeDrawColor
import monge.input.tools.logicalToScreen
import state.MongeState

fun DrawScope.drawAidPoints(state: MongeState, pxPerPt: Float) {

    val isPickingPudCurve =
        state.drawobjects == Mongeobjects.CURVE &&
                state.mongeMode == DrawingModeMonge.PUDORYS &&
                (state.projectionPhase == "pudorys_curve_pick"||state.projectionPhase == "pudorys_start")

    val pickedAidIds: Set<String> =
        if (isPickingPudCurve)
            state.pudorysCurvePickRefs
                .mapNotNull { (it as? CurvePudRef.A)?.aidId }
                .toSet()
        else emptySet()

    state.aidPointsLogical.forEach { p ->

        val scr = logicalToScreen(
            logical = Offset(p.x, p.y),
            canvasOffset = state.canvasOffset,
            scale = state.scale
        )

        val isSel   = p.id in state.selectedAidPointIds
        val isHover = state.hoveredAidPointId == p.id

        val isAllowedForCurve = isPickingPudCurve && p.id != "origin"
        val isPickedForCurve  = isPickingPudCurve && pickedAidIds.contains(p.id)&& p.id != "origin"

        val pickedMain  = Color(0xFF00EAA0)
        val pickedGlow  = pickedMain.copy(alpha = 0.18f)
        val pickedRing1 = Color.White.copy(alpha = 0.85f)
        val pickedRing2 = pickedMain.copy(alpha = 0.85f)

        val baseHalf = (1.5f * p.width ) * state.scale
        val sizeBoost = if (isPickedForCurve) 1.15f else 1f
        val half = when {
            isPickedForCurve ->baseHalf * 1.2f
            isSel->baseHalf *1.4f
            isHover-> baseHalf*1.2f
            else           -> baseHalf
        }

        val col = when {
            isPickedForCurve -> pickedMain
            else             -> p.color.runtimeDrawColor()
        }

        val w = p.width * POINT_STROKE_WEIGHT*state.scale
        /* ===============================
           HALO / RING (jen při curve pick režimu)
           =============================== */

        if (isPickedForCurve) {
            val ringRadius = half * 1.6f
            val haloRadius = ringRadius * 1.25f

            drawCircle(
                color = pickedGlow,
                radius = haloRadius,
                center = scr
            )

            drawCircle(
                color = pickedRing1,
                radius = ringRadius,
                center = scr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (w * 1.2f).coerceAtLeast(strokePx(1.2f, pxPerPt))
                )
            )

            drawCircle(
                color = pickedRing2,
                radius = ringRadius * 1.12f,
                center = scr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (w * 0.9f).coerceAtLeast(strokePx(1.0f, pxPerPt))
                )
            )

        } else if (isAllowedForCurve) {

            val ringRadius = half * 1.6f
            val haloRadius = ringRadius * 1.25f

            drawCircle(
                color = p.color.copy(alpha = 0.10f),
                radius = haloRadius,
                center = scr
            )

            drawCircle(
                color = p.color.copy(alpha = 0.60f),
                radius = ringRadius,
                center = scr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (w * 0.8f).coerceAtLeast(strokePx(1.0f, pxPerPt))
                )
            )
        }

        /* ===============================
           KŘÍŽEK
           =============================== */

        if (isSel) {
            drawLine(
                state.selectedHaloColor,
                scr.copy(x = scr.x - half),
                scr.copy(x = scr.x + half),
                strokeWidth = w + SELECTION_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
            drawLine(
                state.selectedHaloColor,
                scr.copy(y = scr.y - half),
                scr.copy(y = scr.y + half),
                strokeWidth = w + SELECTION_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
        } else if (isHover) {
            drawLine(
                state.hoverHaloColor,
                scr.copy(x = scr.x - half),
                scr.copy(x = scr.x + half),
                strokeWidth = w + HOVER_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
            drawLine(
                state.hoverHaloColor,
                scr.copy(y = scr.y - half),
                scr.copy(y = scr.y + half),
                strokeWidth = w + HOVER_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            col,
            scr.copy(x = scr.x - half),
            scr.copy(x = scr.x + half),
            strokeWidth = w,
            cap = StrokeCap.Round
        )
        drawLine(
            col,
            scr.copy(y = scr.y - half),
            scr.copy(y = scr.y + half),
            strokeWidth = w,
            cap = StrokeCap.Round
        )
    }
}

