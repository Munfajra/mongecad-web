package draw.mongescreen.previews.tools

import monge.input.axo.axoOverlayToScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.axo.drawAOSegmentOnScreen
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewPudorys
import draw.mongescreen.previews.segments.AO.drawAOPreviewCross
import model.DrawingModeMonge
import model.LineStyle
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.ConicArcs.single.getLogicalCursorNarys
import monge.input.axo.points.screenToAxoOverlayLocal
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld
import kotlin.math.*

fun DrawScope.drawOverlayAnglePlacement(state: MongeState, snappedPointLogical: Offset?) {
    if (state.mongeMode != DrawingModeMonge.PUDORYS) return

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
        "angle_point2", "angle_point3" -> {
            state.pendingPoint1?.let { drawRedCross(state=state,center= it) }
            val center = state.pendingPoint1
            val radiusPoint = state.pendingPoint2
            if (center != null && radiusPoint != null) {
                val radius = (radiusPoint - center).getDistance()
                val dir = cursor - center
                val len = dir.getDistance()
                if (len > 1e-6f) {
                    val clipped = center + dir.normalized() * radius
                    drawRedLine(center, clipped, state)
                }
            }
            if (state.projectionPhase== "angle_point2"){
                val center = Point3DPudorys(state.pendingPoint1!!.x,state.pendingPoint1!!.y, name="")
                drawDashedSegmentPreviewPudorys(center,
                    cursor,
                    state.scale,
                    color=Color.Red,
                    canvasOffset = state.canvasOffset)

            }

        }
    }

    if (state.projectionPhase == "angle_point3") {
        state.pendingPoint2?.let { drawRedCross(state=state,center= it) }
        val center = state.pendingPoint1
        val start = state.pendingPoint2
        if (center != null && start != null) {
            drawRedArc(center, start, cursor, clockwise = state.arc.arcDirectionClockwise,state)
        }

            val centerx = Point3DPudorys(state.pendingPoint1!!.x,state.pendingPoint1!!.y, name="")
            drawDashedSegmentPreviewPudorys(centerx,
                state.pendingPoint2!!,
                state.scale,
                color=Color.Red,
                canvasOffset = state.canvasOffset)

    }

    if (state.projectionPhase == "angle_new_ray") {

        if (state.pendingPoint3 != null) {
            val center = Point3DPudorys(state.pendingPoint3!!.x,state.pendingPoint3!!.y, name="")
            drawDashedSegmentPreviewPudorys(center,
                cursor,
                state.scale,
                color=Color.Red,
                canvasOffset = state.canvasOffset)
            val centerx = state.pendingPoint3
            val angle = state.pendingAngle ?: return
            val adjustedAngle = if (state.arc.arcDirectionClockwise) angle else -angle
            val dir = cursor - centerx!!
            val len = dir.getDistance()
            if (len > 1e-6f) {
                val sinA = sin(adjustedAngle)
                val cosA = cos(adjustedAngle)
                val rotated = Offset(
                    cosA * dir.x - sinA * dir.y,
                    sinA * dir.x + cosA * dir.y
                )


                val arcEnd = centerx + rotated.normalized() * len
                drawRedArc(centerx, cursor, arcEnd, clockwise = state.arc.arcDirectionClockwise,state)
                drawDashedSegmentPreviewPudorys(center,
                    arcEnd,
                    state.scale,
                    color=Color.Red,
                    canvasOffset = state.canvasOffset)
                drawRedCross(state=state,center= arcEnd)
            }
        }
    }
}

/** Stejný náhled GetAngle pro nárys; jeho logická osa z má na Canvasu opačný směr. */
fun DrawScope.drawOverlayAnglePlacementNarys(state: MongeState, snappedPointLogical: Offset?) {
    if (state.mongeMode != DrawingModeMonge.NARYS) return

    val cursor = getLogicalCursorNarys(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection
    )
    fun canvasPoint(point: Offset) = Offset(point.x, -point.y)
    fun dashedSegment(from: Offset, to: Offset) {
        drawDashedSegmentPreviewNarys(
            start = Point3DNarys(from.x, from.y, name = ""),
            cursorWorld = canvasPoint(to),
            scale = state.scale,
            color = Color.Red,
            canvasOffset = state.canvasOffset
        )
    }

    when (state.projectionPhase) {
        "angle_point2", "angle_point3" -> {
            state.pendingPoint1?.let { drawRedCross(state = state, center = canvasPoint(it)) }
            val center = state.pendingPoint1
            val radiusPoint = state.pendingPoint2
            if (center != null && radiusPoint != null) {
                val radius = (radiusPoint - center).getDistance()
                val direction = cursor - center
                if (direction.getDistance() > 1e-6f) {
                    val clipped = center + direction.normalized() * radius
                    drawRedLine(canvasPoint(center), canvasPoint(clipped), state)
                }
            }
            if (state.projectionPhase == "angle_point2" && center != null) {
                dashedSegment(center, cursor)
            }
        }
    }

    if (state.projectionPhase == "angle_point3") {
        state.pendingPoint2?.let { drawRedCross(state = state, center = canvasPoint(it)) }
        val center = state.pendingPoint1
        val start = state.pendingPoint2
        if (center != null && start != null) {
            drawRedArc(
                canvasPoint(center),
                canvasPoint(start),
                canvasPoint(cursor),
                clockwise = !state.arc.arcDirectionClockwise,
                state = state
            )
            dashedSegment(center, start)
        }
    }

    if (state.projectionPhase == "angle_new_ray") {
        val vertex = state.pendingPoint3 ?: return
        val angle = state.pendingAngle ?: return
        val direction = cursor - vertex
        val length = direction.getDistance()

        dashedSegment(vertex, cursor)
        if (length > 1e-6f) {
            val adjustedAngle = if (state.arc.arcDirectionClockwise) angle else -angle
            val rotated = Offset(
                cos(adjustedAngle) * direction.x - sin(adjustedAngle) * direction.y,
                sin(adjustedAngle) * direction.x + cos(adjustedAngle) * direction.y
            )
            val arcEnd = vertex + rotated.normalized() * length
            drawRedArc(
                canvasPoint(vertex),
                canvasPoint(cursor),
                canvasPoint(arcEnd),
                clockwise = !state.arc.arcDirectionClockwise,
                state = state
            )
            dashedSegment(vertex, arcEnd)
            drawRedCross(state = state, center = canvasPoint(arcEnd))
        }
    }
}

fun DrawScope.drawRedCross(center: Offset, size: Float = 8f,state: MongeState) {
    val px = center.toScreenOld(state.scale, state.canvasOffset)
    drawLine(Color.Red, px.copy(x = px.x - size), px.copy(x = px.x + size), strokeWidth = 1.5f)
    drawLine(Color.Red, px.copy(y = px.y - size), px.copy(y = px.y + size), strokeWidth = 1.5f)
}
fun DrawScope.drawRedLine(from: Offset, to: Offset,state: MongeState) {
    drawLine(Color.Red, from.toScreenOld(state.scale, state.canvasOffset), to.toScreenOld(state.scale, state.canvasOffset), strokeWidth = 1.5f)
}
fun DrawScope.drawRedArc(center: Offset, from: Offset, to: Offset, clockwise: Boolean,state: MongeState) {
    val startVec = from - center
    val endVec = to - center

    val startAngle = atan2(startVec.y, startVec.x).toDouble()  // bez negace
    val endAngle = atan2(endVec.y, endVec.x).toDouble()

    val sweep = computeSweep(startAngle, endAngle, clockwise)

    val radius = startVec.getDistance() * state.scale
    val screenCenter = center.toScreenOld(state.scale, state.canvasOffset)

    drawArc(
        color = Color.Red,
        startAngle = ((startAngle) * 180.0 / kotlin.math.PI).toFloat(),
        sweepAngle = ((sweep) * 180.0 / kotlin.math.PI).toFloat(),
        useCenter = false,
        topLeft = Offset(screenCenter.x - radius, screenCenter.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = 1.5f)
    )
}
fun computeSweep(start: Double, end: Double, clockwise: Boolean): Double {
    var sweep = end - start

    // Normalizuj sweep do (-2π, 2π)
    while (sweep <= -PI) sweep += 2 * PI
    while (sweep > PI) sweep -= 2 * PI

    // pokud směr sweepu neodpovídá požadovanému směru, změň
    val correctSweep = if ((clockwise && sweep < 0) || (!clockwise && sweep > 0)) {
        sweep - 2 * PI * sign(sweep)
    } else sweep

    // Pokud je úhel větší než 180°, změň směr
    return if (abs(correctSweep) > PI) {
        -2 * PI * sign(correctSweep) + correctSweep
    } else {
        correctSweep
    }
}
fun Offset.normalized(): Offset {
    val len = getDistance()
    return if (len < 1e-6f) Offset.Zero else this / len
}

fun DrawScope.drawDashedOverlaySegmentAxo(
    from: Offset,
    to: Offset,
    state: MongeState,
    color: Color = Color.Red,
    strokeWidth: Float = 1.5f
) {
    drawAOSegmentOnScreen(
        state = state,
        startLocal = from,
        endLocal = to,
        color = color,
        lineWidth = strokeWidth,
        lineStyle = LineStyle.Dashed,
        pxPerPt = 1f
    )
}
fun DrawScope.drawOverlayAnglePlacementAxo(
    state: MongeState,
) {
    val basis = state.basis ?: return

    val cursor = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

    when (state.projectionPhase) {

        "angle_point2_axo" -> {
            val p1 = state.pendingPoint1

            if (p1 != null) {
                drawAOPreviewCross(p1, state, basis, Color.Red)

                drawDashedOverlaySegmentAxo(
                    from = p1,
                    to = cursor,
                    state = state,
                    color = Color.Red
                )
            }
        }

        "angle_point3_axo" -> {
            val p1 = state.pendingPoint1
            val p2 = state.pendingPoint2

            if (p1 != null) {
                drawAOPreviewCross(p1, state, basis, Color.Red)
            }

            if (p2 != null) {
                drawAOPreviewCross(p2, state, basis, Color.Red)
            }

            if (p1 != null && p2 != null) {
                val radius = (p2 - p1).getDistance()
                val dir = cursor - p1
                val len = dir.getDistance()

                if (len > 1e-6f) {
                    val clipped = p1 + dir.normalized() * radius

                    drawAOSegmentOnScreen(
                        state = state,
                        startLocal = p1,
                        endLocal = clipped,
                        color = Color.Red,
                        lineWidth = 2f,
                        lineStyle = LineStyle.Solid,
                        pxPerPt = 1f
                    )
                }

                drawRedArcAxo(
                    center = p1,
                    from = p2,
                    to = cursor,
                    clockwise = state.arc.arcDirectionClockwise,
                    state = state
                )

                drawDashedOverlaySegmentAxo(
                    from = p1,
                    to = p2,
                    state = state,
                    color = Color.Red
                )
            }
        }

        "angle_new_ray_axo" -> {
            val p3 = state.pendingPoint3 ?: return
            val angle = state.pendingAngle ?: return

            drawDashedOverlaySegmentAxo(
                from = p3,
                to = cursor,
                state = state,
                color = Color.Red
            )

            val adjustedAngle = if (state.arc.arcDirectionClockwise) angle else -angle
            val dir = cursor - p3
            val len = dir.getDistance()

            if (len > 1e-6f) {
                val sinA = sin(adjustedAngle)
                val cosA = cos(adjustedAngle)

                val rotated = Offset(
                    x = cosA * dir.x - sinA * dir.y,
                    y = sinA * dir.x + cosA * dir.y
                )

                val arcEnd = p3 + rotated.normalized() * len

                drawRedArcAxo(
                    center = p3,
                    from = cursor,
                    to = arcEnd,
                    clockwise = state.arc.arcDirectionClockwise,
                    state = state
                )

                drawDashedOverlaySegmentAxo(
                    from = p3,
                    to = arcEnd,
                    state = state,
                    color = Color.Red
                )

                drawAOPreviewCross(arcEnd, state, basis, Color.Red)
            }
        }
    }
}

fun DrawScope.drawRedArcAxo(
    center: Offset,
    from: Offset,
    to: Offset,
    clockwise: Boolean,
    state: MongeState
) {
    val basis = state.basis ?: return

    val startVec = from - center
    val endVec = to - center

    val radius = startVec.getDistance()
    if (radius < 1e-6f) return

    val startAngle = atan2(startVec.y, startVec.x).toDouble()
    val endAngle = atan2(endVec.y, endVec.x).toDouble()
    val sweep = computeSweep(startAngle, endAngle, clockwise)

    val steps = 48
    val path = Path()

    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val a = startAngle + sweep * t

        val local = center + Offset(
            x = cos(a).toFloat() * radius,
            y = sin(a).toFloat() * radius
        )

        val screen = axoOverlayToScreen(local, state, basis)

        if (i == 0) path.moveTo(screen.x, screen.y)
        else path.lineTo(screen.x, screen.y)
    }

    drawPath(
        path = path,
        color = Color.Red,
        style = Stroke(width = 1.5f)
    )
}
