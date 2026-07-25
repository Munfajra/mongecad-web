package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.orth.conics.drawEllipseFromDiameters
import model.LineStyle
import model.XAxisDirection
import model.YAxisDirectionPlane
import monge.input.conixections.computeConicFromConjugateDiameters
import monge.input.conixections.conjugateDiameterInputFromRadii
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld

fun DrawScope.drawEllipseConstructionPreviewPudorys(state: MongeState, snappedPointLogical: Offset?) {
    val p1 = state.pendingPoint1
    val p2 = state.pendingPoint2
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

    when (state.projectionPhase) {
        "ellipse_point2" -> {
            if (p1 != null) drawRedCross(p1, state = state)
            if (p1 != null) drawDashedLine(p1, cursor, Color.Gray, state = state)
        }

        "ellipse_point3" -> {
            if (p1 != null) drawRedCross(p1, state = state)
            if (p2 != null) drawRedCross(p2, state = state)

            if (p1 != null && p2 != null) {
                val center = p1
                drawDashedLine(center, p2, Color.Gray, state = state)
                drawDashedLine(center, cursor, Color.Gray, state = state)

                if ((cursor - center).getDistance() > 2f / state.scale &&
                    (p2 - center).getDistance() > 2f / state.scale) {

                    runCatching {
                        val (d1, d2, d3) = conjugateDiameterInputFromRadii(center, p2, cursor)
                        computeConicFromConjugateDiameters(d1, d2, d3)
                        drawEllipseFromDiameters(
                            p1 = d1,
                            p2 = d2,
                            p3 = d3,
                            scale = state.scale,
                            canvasOffset = state.canvasOffset,
                            color = Color.LightGray,
                            strokeWidth = 1f,
                            lineStyle = LineStyle.Dashed
                        )
                    }.onFailure {
                        println("⚠️ Náhled elipsy nelze vytvořit: ${it.message}")
                    }
                }
            }
        }
    }
}


fun DrawScope.drawRedCross(center: Offset, state: MongeState, size: Float = 12f, color: Color = Color.Red) {
    val c = center.toScreenOld(state.scale, state.canvasOffset)

    // Horizontální čára
    drawLine(
        color = color,
        start = c - Offset(size, 0f),
        end = c + Offset(size, 0f),
        strokeWidth = 2f
    )

    // Vertikální čára
    drawLine(
        color = color,
        start = c - Offset(0f, size),
        end = c + Offset(0f, size),
        strokeWidth = 2f
    )
}

fun DrawScope.drawDashedLine(a: Offset, b: Offset, color: Color = Color.Gray, strokeWidth: Float = 1f,state: MongeState) {
    val aScr = a.toScreenOld(state.scale, state.canvasOffset)
    val bScr = b.toScreenOld(state.scale, state.canvasOffset)
    drawLine(
        color = color,
        start = aScr,
        end = bScr,
        strokeWidth = strokeWidth,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )
}
fun DrawScope.drawEllipseConstructionPreviewNarys(state: MongeState, snappedPointLogical: Offset?) {
    val p1 = state.pendingPoint1
    val p2 = state.pendingPoint2

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

    when (state.projectionPhase) {
        "ellipse_point2" -> {
            if (p1 != null) drawRedCross(p1, state = state)
            if (p1 != null) drawDashedLine(p1, cursor, Color.Gray, state = state)
        }

        "ellipse_point3" -> {
            if (p1 != null) drawRedCross(p1, state = state)
            if (p2 != null) drawRedCross(p2, state = state)

            if (p1 != null && p2 != null) {
                val center = p1
                drawDashedLine(center, p2, Color.Gray, state = state)
                drawDashedLine(center, cursor, Color.Gray, state = state)

                val dist1 = (cursor - center).getDistance()
                val dist2 = (p2 - center).getDistance()

                if (dist1 > 2f / state.scale && dist2 > 2f / state.scale) {
                    val (d1, d2, d3) = conjugateDiameterInputFromRadii(center, p2, cursor)
                    val d1z = Offset(d1.x, -d1.y)
                    val d2z = Offset(d2.x, -d2.y)
                    val d3z = Offset(d3.x, -d3.y)

                    runCatching {
                        computeConicFromConjugateDiameters(d1z, d2z, d3z)
                        drawEllipseFromDiameters(
                            p1 = d1,
                            p2 = d2,
                            p3 = d3,
                            scale = state.scale,
                            canvasOffset = state.canvasOffset,
                            color = Color.LightGray,
                            strokeWidth = 1f,
                            lineStyle = LineStyle.Dashed
                        )
                    }.onFailure {
                        println("⚠️ Náhled elipsy (nárys) nelze vytvořit: ${it.message}")
                    }
                }
            }
        }
    }
}
