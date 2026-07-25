package draw.mongescreen.objects.orth

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.HOVER_HALO_EXTRA_PX
import draw.mongescreen.objects.SELECTION_HALO_EXTRA_PX
import draw.mongescreen.objects.orth.conics.strokeForStyle
import model.ArcMode
import model.LineStyle
import model.runtimeDrawColor
import monge.input.conixections.normAngle
import state.MongeState
import kotlin.math.sqrt

fun DrawScope.drawCirclesPudorys(state: MongeState, showHelpCircle: Boolean, pxPerPt: Float) {
    val dashMul = 1.0f
    val gapMul  = 1.2f

    for (circle in state.circlesPudorys) {
        val x0 = -circle.d / 2f
        val y0 = -circle.e / 2f
        val r2 = x0 * x0 + y0 * y0 - circle.f
        if (r2 <= 0f) continue

        if (circle.isHelpCircle) {
            val shouldDraw = showHelpCircle && state.showConstruction.value
            if (!shouldDraw) continue
        }

        val radius = sqrt(r2)
        val centerLogical = Offset(x0, y0)
        val screenCenter = centerLogical * state.scale + state.canvasOffset
        val screenRadius = radius * state.scale

        val isHovered = (circle == state.hoveredCirclePudorys)
        val isSelected = state.selectedCirclesPudorys.any { it.id == circle.id }

        val strokePx = circle.strokeWidth * pxPerPt
        val color = circle.localColor ?: Color.Black

        val ends = state.circleArcEnds[circle.id]
        val mode = state.circleArcMode[circle.id] ?: ArcMode.SHORTEST

        if (ends != null) {
            val (A, B) = ends

            if (state.showConstruction.value && showHelpCircle) {
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.6f),
                    radius = screenRadius,
                    center = screenCenter,
                    style = strokeForStyle(
                        strokeWidthPx = 0.5f * pxPerPt,
                        lineStyle = LineStyle.Dashed,
                        dashMul = dashMul,
                        gapMul = gapMul,
                        phase = 0f,
                        scale = state.scale
                    )
                )
            }

            when {
                isSelected -> drawCircleArc(centerLogical, radius, A, B, mode, state.scale, state.canvasOffset,
                    state.selectedHaloColor, circle.strokeWidth + SELECTION_HALO_EXTRA_PX, circle.lineStyle, dashMul, gapMul, pxPerPt)
                isHovered -> drawCircleArc(centerLogical, radius, A, B, mode, state.scale, state.canvasOffset,
                    state.hoverHaloColor, circle.strokeWidth + HOVER_HALO_EXTRA_PX, circle.lineStyle, dashMul, gapMul, pxPerPt)
            }

            drawCircleArc(
                centerLogical = centerLogical,
                radiusLogical = radius,
                a = A,
                b = B,
                mode = mode,
                scale = state.scale,
                canvasOffset = state.canvasOffset,
                color = color,
                strokeWidth = circle.strokeWidth,
                lineStyle = circle.lineStyle,
                dashMul = dashMul,
                gapMul = gapMul,
                pxPerPt = pxPerPt
            )

            continue
        }

        when {
            isSelected -> drawCircle(
                color = state.selectedHaloColor, radius = screenRadius, center = screenCenter,
                style = strokeForStyle(strokePx * 5f, circle.lineStyle, dashMul, gapMul, 0f, state.scale)
            )
            isHovered -> drawCircle(
                color = state.hoverHaloColor, radius = screenRadius, center = screenCenter,
                style = strokeForStyle(strokePx * 3.5f, circle.lineStyle, dashMul, gapMul, 0f, state.scale)
            )
        }

        drawCircle(
            color = color.runtimeDrawColor(),
            radius = screenRadius,
            center = screenCenter,
            style = strokeForStyle(
                strokeWidthPx = strokePx,
                lineStyle = circle.lineStyle,
                dashMul = dashMul,
                gapMul = gapMul,
                phase = 0f,
                scale = state.scale
            )
        )
    }
}
fun DrawScope.drawCirclesNarys(state: MongeState, showHelpCircle: Boolean, pxPerPt: Float) {
    val dashMul = 1.0f
    val gapMul  = 1.2f

    for (circle in state.circlesNarys) {
        val x0 = -circle.d / 2f
        val z0 = -circle.e / 2f
        val r2 = x0 * x0 + z0 * z0 - circle.f
        if (r2 <= 0f) continue

        if (circle.isHelpCircle) {
            val shouldDraw = showHelpCircle && state.showConstruction.value
            if (!shouldDraw) continue
        }

        val radius = sqrt(r2)

        // NÁRYS: logické (x, z) převádíme pro DrawScope na (x, -z)
        val centerLogical = Offset(x0, -z0)
        val screenCenter = centerLogical * state.scale + state.canvasOffset
        val screenRadius = radius * state.scale

        val isHovered = (circle == state.hoveredCircleNarys)
        val isSelected = state.selectedCirclesNarys.any { it.id == circle.id }

        val strokePx = circle.strokeWidth * pxPerPt
        val color = circle.localColor ?: Color.Black

        val ends = state.circleArcEnds[circle.id]
        val mode = state.circleArcMode[circle.id] ?: ArcMode.SHORTEST

        if (ends != null) {
            val (A, B) = ends

            if (state.showConstruction.value && showHelpCircle) {
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.6f),
                    radius = screenRadius,
                    center = screenCenter,
                    style = strokeForStyle(
                        strokeWidthPx = 0.5f * pxPerPt,
                        lineStyle = LineStyle.Dashed,
                        dashMul = dashMul,
                        gapMul = gapMul,
                        phase = 0f,
                        scale = state.scale
                    )
                )
            }

            when {
                isSelected -> drawCircleArc(centerLogical, radius, A, B, mode, state.scale, state.canvasOffset,
                    state.selectedHaloColor, circle.strokeWidth + SELECTION_HALO_EXTRA_PX, circle.lineStyle, dashMul, gapMul, pxPerPt)
                isHovered -> drawCircleArc(centerLogical, radius, A, B, mode, state.scale, state.canvasOffset,
                    state.hoverHaloColor, circle.strokeWidth + HOVER_HALO_EXTRA_PX, circle.lineStyle, dashMul, gapMul, pxPerPt)
            }

            drawCircleArc(
                centerLogical = centerLogical,
                radiusLogical = radius,
                a = A,
                b = B,
                mode = mode,
                scale = state.scale,
                canvasOffset = state.canvasOffset,
                color = color,
                strokeWidth = circle.strokeWidth,
                lineStyle = circle.lineStyle,
                dashMul = dashMul,
                gapMul = gapMul,
                pxPerPt = pxPerPt
            )

            continue
        }

        when {
            isSelected -> drawCircle(
                color = state.selectedHaloColor, radius = screenRadius, center = screenCenter,
                style = strokeForStyle(strokePx * 5f, circle.lineStyle, dashMul, gapMul, 0f, state.scale)
            )
            isHovered -> drawCircle(
                color = state.hoverHaloColor, radius = screenRadius, center = screenCenter,
                style = strokeForStyle(strokePx * 3.5f, circle.lineStyle, dashMul, gapMul, 0f, state.scale)
            )
        }

        drawCircle(
            color = color.runtimeDrawColor(),
            radius = screenRadius,
            center = screenCenter,
            style = strokeForStyle(
                strokeWidthPx = strokePx,
                lineStyle = circle.lineStyle,
                dashMul = dashMul,
                gapMul = gapMul,
                phase = 0f,
                scale = state.scale
            )
        )
    }
}

fun DrawScope.drawCircleArc(
    centerLogical: Offset,
    radiusLogical: Float,
    a: Offset,
    b: Offset,
    mode: ArcMode,
    scale: Float,
    canvasOffset: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    dashMul: Float = 1.0f,
    gapMul: Float = 1.2f,
    pxPerPt: Float = 1f
) {
    val startRad = normAngle(circleAngle(centerLogical, a))
    val endRad   = normAngle(circleAngle(centerLogical, b))
    val sweepRad = chooseArcSweep(startRad, endRad, mode)

    val startDeg = ((startRad.toDouble()) * 180.0 / kotlin.math.PI).toFloat()
    val sweepDeg = ((sweepRad.toDouble()) * 180.0 / kotlin.math.PI).toFloat()

    val screenCenter = centerLogical * scale + canvasOffset
    val screenRadius = radiusLogical * scale
    val topLeft = Offset(
        screenCenter.x - screenRadius,
        screenCenter.y - screenRadius
    )
    val size = androidx.compose.ui.geometry.Size(
        2f * screenRadius,
        2f * screenRadius
    )

    drawArc(
        color = color.runtimeDrawColor(),
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = strokeForStyle(
            strokeWidthPx = strokeWidth * pxPerPt,
            lineStyle = lineStyle,
            dashMul = dashMul,
            gapMul = gapMul,
            phase = 0f,
            scale = scale
        )
    )
}