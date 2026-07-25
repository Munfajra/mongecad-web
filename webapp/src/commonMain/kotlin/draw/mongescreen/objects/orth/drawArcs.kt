package draw.mongescreen.objects.orth

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.lineStyleDashPathEffectPx
import draw.mongescreen.objects.HOVER_HALO_EXTRA_PX

import draw.mongescreen.objects.PENDING_HALO_EXTRA_PX
import draw.mongescreen.objects.SELECTION_HALO_EXTRA_PX
import model.Mongeobjects
import model.classes.Arc2DNarys
import model.classes.Arc2DPudorys
import model.runtimeDrawColor
import state.MongeState

fun DrawScope.drawArcsPudorys(mongeState: MongeState, pxPerPt: Float) {
    val scale  = mongeState.scale
    val offset = mongeState.canvasOffset
    val isNone = mongeState.drawobjects == Mongeobjects.NONE

    val hoveredId   = mongeState.hoveredArcPudorysId
    val selectedIds = mongeState.selectedArcsPudorys.map { it.id }.toHashSet()

    val meridianIds = mongeState.selectedMeridianPudorysIds
    fun isMeridianSel(arc: Arc2DPudorys) = arc.id in meridianIds


    fun radToComposeDeg(rad: Float): Float {

        return (-rad * 180f / kotlin.math.PI.toFloat())
    }

    fun strokePx(widthPt: Float, pxPerPt: Float) = widthPt * pxPerPt

    for (arc in mongeState.arcsPudorys) {
        val radiusPx = arc.radius * scale
        val topLeft = Offset(
            (arc.center.x - arc.radius) * scale,
            (arc.center.y - arc.radius) * scale
        ) + offset

        // ✅ nový výpočet z radiánů
        val sweepRad = arc.sweepSigned() // CCW +, CW -
        val startDeg = radToComposeDeg(Arc2DPudorys.norm(arc.startRad))
        val sweepDeg = radToComposeDeg(sweepRad)

        val pathEffect = lineStyleDashPathEffectPx(arc.lineStyle, scale = scale)

        val baseStroke = strokePx(arc.strokeWidth, pxPerPt)

        val isSelected = arc.id in selectedIds
        val isHovered  = isNone && hoveredId != null && arc.id == hoveredId
        val isMerSel   = isMeridianSel(arc)

        val color = if (isMerSel) Color(0xFF1CD9B3) else arc.color

        when {
            isMerSel -> drawArc(
                color = Color(0xFF1CD9B3).copy(alpha = 0.45f),
                startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
                topLeft = topLeft, size = Size(2 * radiusPx, 2 * radiusPx),
                style = Stroke(width = baseStroke + PENDING_HALO_EXTRA_PX * pxPerPt, pathEffect = pathEffect)
            )
            isSelected -> drawArc(
                color = mongeState.selectedHaloColor,
                startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
                topLeft = topLeft, size = Size(2 * radiusPx, 2 * radiusPx),
                style = Stroke(width = baseStroke + SELECTION_HALO_EXTRA_PX * pxPerPt, pathEffect = pathEffect)
            )
            isHovered -> drawArc(
                color = mongeState.hoverHaloColor,
                startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
                topLeft = topLeft, size = Size(2 * radiusPx, 2 * radiusPx),
                style = Stroke(width = baseStroke + HOVER_HALO_EXTRA_PX * pxPerPt, pathEffect = pathEffect)
            )
        }

        drawArc(
            color = color.runtimeDrawColor(),
            startAngle = startDeg,
            sweepAngle = sweepDeg,
            useCenter = false,
            topLeft = topLeft,
            size = Size(2 * radiusPx, 2 * radiusPx),
            style = Stroke(width = baseStroke, pathEffect = pathEffect)
        )
    }
}
fun DrawScope.drawArcsNarys(mongeState: MongeState, pxPerPt: Float) {
    val scale = mongeState.scale
    val offset = mongeState.canvasOffset
    val isNone = mongeState.drawobjects == Mongeobjects.NONE

    val hoveredId = mongeState.hoveredArcNarysId
    val selectedIds = mongeState.selectedArcsNarys.map { it.id }.toHashSet()
    val meridianIds = mongeState.selectedMeridianNarysIds
    fun radToComposeDeg(rad: Float): Float {
        // geometrie: +CCW (z nahoru)
        // Compose: +CW (y dolů)
        return (-rad * 180f / kotlin.math.PI.toFloat())
    }

    for (arc in mongeState.arcsNarys) {
        val radiusPx = arc.radius * scale
        val topLeft = Offset(
            (arc.center.x - arc.radius) * scale,
            (-arc.center.z - arc.radius) * scale
        ) + offset
        val isMeridianSel = arc.id in meridianIds
        // ✅ spočti signed sweep v radiánech podle clockwise (geometrie)
        val sweepRad = arc.sweepSigned()          // CCW +, CW -
        val startDeg = radToComposeDeg(Arc2DNarys.norm(arc.startRad))
        val sweepDeg = radToComposeDeg(sweepRad)  // znaménko se otočí stejně jako úhel

        val pathEffect = lineStyleDashPathEffectPx(arc.lineStyle, scale = scale)

        val baseStroke = arc.strokeWidth * pxPerPt

        val isSelected = arc.id in selectedIds
        val isHovered  = isNone && hoveredId != null && arc.id == hoveredId
        val color = if (isMeridianSel) Color(0xFF1CD9B3) else arc.color

        when {
            isMeridianSel -> drawArc(
                color = Color(0xFF1CD9B3).copy(alpha = 0.45f),
                startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
                topLeft = topLeft, size = Size(2 * radiusPx, 2 * radiusPx),
                style = Stroke(width = baseStroke + PENDING_HALO_EXTRA_PX * pxPerPt, pathEffect = pathEffect)
            )
            isSelected -> drawArc(
                color = mongeState.selectedHaloColor,
                startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
                topLeft = topLeft, size = Size(2 * radiusPx, 2 * radiusPx),
                style = Stroke(width = baseStroke + SELECTION_HALO_EXTRA_PX * pxPerPt, pathEffect = pathEffect)
            )
            isHovered -> drawArc(
                color = mongeState.hoverHaloColor,
                startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
                topLeft = topLeft, size = Size(2 * radiusPx, 2 * radiusPx),
                style = Stroke(width = baseStroke + HOVER_HALO_EXTRA_PX * pxPerPt, pathEffect = pathEffect)
            )
        }

        drawArc(
            color = color.runtimeDrawColor(),
            startAngle = startDeg,
            sweepAngle = sweepDeg,
            useCenter = false,
            topLeft = topLeft,
            size = Size(2 * radiusPx, 2 * radiusPx),
            style = Stroke(width = baseStroke, pathEffect = pathEffect)
        )
    }
}
