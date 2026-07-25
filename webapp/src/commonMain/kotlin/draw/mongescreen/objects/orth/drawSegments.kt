package draw.mongescreen.objects.orth

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.lineStyleDashPatternPx
import draw.mongescreen.objects.HOVER_HALO_EXTRA_PX

import draw.mongescreen.objects.PENDING_HALO_EXTRA_PX
import draw.mongescreen.objects.SELECTION_HALO_EXTRA_PX
import draw.mongescreen.objects.strokePx
import model.LineStyle
import model.Mongeobjects
import model.runtimeDrawColor
import state.MongeState

//půdorys úsečka
fun DrawScope.drawSegmentsPudorys(
    state: MongeState,
    showHelpLine: Boolean,
    pxPerPt: Float
) {
    val scale = state.scale
    val canvasOffset = state.canvasOffset

    val allSegments = buildList {
        addAll(state.segmentsPudorys)
        if (state.showConstruction.value && showHelpLine) {
            addAll(state.helpSegmentsPudorys)
        }
    }

    val drawableSegments = allSegments.mapNotNull { segment ->
        val a = Offset(segment.start.x, segment.start.y)
        val b = Offset(segment.end.x, segment.end.y)

        val dir = b - a
        val dirLen = dir.getDistance()
        if (dirLen < 1e-6f) return@mapNotNull null
        val baseStroke = strokePx(segment.strokeWidth, pxPerPt)
        val baseColor = segment.color



        DrawableSegment2D(a, b, baseColor, baseStroke, segment.lineStyle)
    }

    val polylines = groupIntoPolylines(drawableSegments)

    for ((styleSeg, points) in polylines) {
        if (points.size < 2) continue

        val closed = isClosedPolyline(points)

        val path = Path().apply {
            val p0 = points[0]
            moveTo(p0.x * scale + canvasOffset.x, p0.y * scale + canvasOffset.y)
            for (i in 1 until points.size) {
                val p = points[i]
                lineTo(p.x * scale + canvasOffset.x, p.y * scale + canvasOffset.y)
            }
            // ⬅️ uzavřené tvary: spoj poslední a první bod s joinem
            if (closed) close()
        }

        val patternPx = lineStylePatternPx(
            style = styleSeg.lineStyle,
            scale = scale
        )
        val pathEffect = patternPx?.let { PathEffect.dashPathEffect(it, 0f) }

        drawPath(
            path = path,
            color = styleSeg.color.runtimeDrawColor(),
            style = Stroke(
                width = styleSeg.stroke,
                pathEffect = pathEffect,
                join = StrokeJoin.Round,
                cap = StrokeCap.Round
            )
        )
    }
}


//nárys úsečka
fun DrawScope.drawSegmentsNarys(
    state: MongeState,
    showHelpLine: Boolean,
    pxPerPt: Float
) {
    val scale = state.scale
    val canvasOffset = state.canvasOffset

    val allSegments = buildList {
        addAll(state.segmentsNarys)
        if (state.showConstruction.value && showHelpLine) {
            addAll(state.helpSegmentsNarys)
        }
    }

    // world = (x, z), na obrazovku pak dáváme (x, -z)
    val drawableSegments = allSegments.mapNotNull { segment ->
        val a = Offset(segment.start.x, segment.start.z)
        val b = Offset(segment.end.x, segment.end.z)

        val dir = b - a
        val dirLen = dir.getDistance()
        if (dirLen < 1e-6f) return@mapNotNull null

        val baseStroke = strokePx(segment.strokeWidth, pxPerPt)
        val baseColor = segment.color

        DrawableSegment2D(a, b, baseColor, baseStroke, segment.lineStyle)
    }

    val polylines = groupIntoPolylines(drawableSegments)

    for ((styleSeg, pointsWorld) in polylines) {
        if (pointsWorld.size < 2) continue

        val closed = isClosedPolyline(pointsWorld)

        val path = Path().apply {
            // při kreslení převádíme (x, z) → (x, -z)
            val p0 = pointsWorld[0]
            moveTo(p0.x * scale + canvasOffset.x, -p0.y * scale + canvasOffset.y)
            for (i in 1 until pointsWorld.size) {
                val p = pointsWorld[i]
                lineTo(p.x * scale + canvasOffset.x, -p.y * scale + canvasOffset.y)
            }
            if (closed) close()
        }

        val patternPx = lineStylePatternPx(
            style = styleSeg.lineStyle,
            scale = scale
        )
        val pathEffect = patternPx?.let { PathEffect.dashPathEffect(it, 0f) }

        drawPath(
            path = path,
            color = styleSeg.color.runtimeDrawColor(),
            style = Stroke(
                width = styleSeg.stroke,
                pathEffect = pathEffect,
                join = StrokeJoin.Round,
                cap = StrokeCap.Round
            )
        )
    }
}
fun DrawScope.drawSegmentHighlightsNarys(
    state: MongeState,
    showHelpLine: Boolean,
    pxPerPt: Float
) {
    val scale = state.scale
    val canvasOffset = state.canvasOffset

    val allSegments = buildList {
        addAll(state.segmentsNarys)
        if (state.showConstruction.value && showHelpLine) {
            addAll(state.helpSegmentsNarys)
        }
    }

    // Tělesa web nemá, takže se nikdy nezvýrazňuje rozpracovaná podstava.

    for (segment in allSegments) {
        val isPending  = segment.parent?.let { state.pendingSegment3DId == it.id } ?: false
        val isPattern  = state.selectedSegmentForParallelNarys === segment
        val isHovered  = state.hoveredSegmentNarys == segment
        val isSelected = state.selectedSegmentsNarys.any { it.id == segment.id }
        val isMeridianSel = state.selectedMeridianNarysIds.contains(segment.id) ||
                (segment.parent?.id?.let { state.selectedMeridianNarysIds.contains(it) } == true)
        val isPendingSolidBase = false
        val needHighlight = isPending || isPattern || isSelected || (isHovered && state.drawobjects == Mongeobjects.NONE) || isMeridianSel || isPendingSolidBase
        if (!needHighlight) continue

        // world = (x, z)
        val aWorld = Offset(segment.start.x, segment.start.z)
        val bWorld = Offset(segment.end.x, segment.end.z)

        val baseStroke = strokePx(segment.strokeWidth, pxPerPt)

        val highlightColor = when {
            isMeridianSel || isPending || isPattern || isPendingSolidBase -> Color(0xFF1CD9B3).copy(alpha = 0.45f)
            isSelected -> state.selectedHaloColor
            else -> state.hoverHaloColor
        }
        val highlightStroke = when {
            isMeridianSel || isPending || isPattern || isPendingSolidBase -> baseStroke + PENDING_HALO_EXTRA_PX * pxPerPt
            isSelected -> baseStroke + SELECTION_HALO_EXTRA_PX * pxPerPt
            else -> baseStroke + HOVER_HALO_EXTRA_PX * pxPerPt
        }

        val aExt = aWorld
        val bExt = bWorld
        val patternPx = lineStylePatternPx(
            style = segment.lineStyle,
            scale = scale
        )
        val pathEffect = patternPx?.let { PathEffect.dashPathEffect(it, 0f) }
        drawLine(
            color = highlightColor,
            start = Offset(aExt.x * scale + canvasOffset.x, -aExt.y * scale + canvasOffset.y),
            end   = Offset(bExt.x * scale + canvasOffset.x, -bExt.y * scale + canvasOffset.y),
            pathEffect = pathEffect,
            strokeWidth = highlightStroke,
            cap = StrokeCap.Round
        )
    }
}


// =================== HELPERY =======================

data class DrawableSegment2D(
    val a: Offset,
    val b: Offset,
    val color: Color,
    val stroke: Float,
    val lineStyle: LineStyle
)

private fun Offset.isCloseTo(other: Offset, eps: Float): Boolean =
    (this - other).getDistance() <= eps

fun isClosedPolyline(points: List<Offset>): Boolean {
    if (points.size < 3) return false
    return points.first().isCloseTo(points.last(), 1e-4f)
}


fun groupIntoPolylines(
    segments: List<DrawableSegment2D>
): List<Pair<DrawableSegment2D, List<Offset>>> {

    val result = mutableListOf<Pair<DrawableSegment2D, List<Offset>>>()

    val byStyle = segments.groupBy { Triple(it.color, it.stroke, it.lineStyle) }

    for ((_, segsSameStyle) in byStyle) {
        val remaining = segsSameStyle.toMutableList()

        while (remaining.isNotEmpty()) {
            val baseSeg = remaining.removeAt(0)
            val poly = mutableListOf(baseSeg.a, baseSeg.b)

            fun tryExtendFront(): Boolean {
                val head = poly.first()
                val idx = remaining.indexOfFirst { s ->
                    s.a.isCloseTo(head, 1e-4f) || s.b.isCloseTo(head, 1e-4f)
                }
                if (idx == -1) return false
                val s = remaining.removeAt(idx)
                when {
                    s.a.isCloseTo(head, 1e-4f) -> poly.add(0, s.b)
                    s.b.isCloseTo(head, 1e-4f) -> poly.add(0, s.a)
                }
                return true
            }

            fun tryExtendBack(): Boolean {
                val tail = poly.last()
                val idx = remaining.indexOfFirst { s ->
                    s.a.isCloseTo(tail, 1e-4f) || s.b.isCloseTo(tail,1e-4f)
                }
                if (idx == -1) return false
                val s = remaining.removeAt(idx)
                when {
                    s.a.isCloseTo(tail, 1e-4f) -> poly.add(s.b)
                    s.b.isCloseTo(tail, 1e-4f) -> poly.add(s.a)
                }
                return true
            }

            var changed: Boolean
            do {
                changed = tryExtendFront() or tryExtendBack()
            } while (changed)

            result += baseSeg to poly
        }
    }

    return result
}


fun DrawScope.drawSegmentHighlightsPudorys(
    state: MongeState,
    showHelpLine: Boolean,
    pxPerPt: Float
) {
    val scale = state.scale
    val canvasOffset = state.canvasOffset

    val allSegments = buildList {
        addAll(state.segmentsPudorys)
        if (state.showConstruction.value && showHelpLine) {
            addAll(state.helpSegmentsPudorys)
        }
    }

    // Tělesa web nemá, takže se nikdy nezvýrazňuje rozpracovaná podstava.

    for (segment in allSegments) {
        val isPending  = segment.parent?.let { state.pendingSegment3DId == it.id } ?: false
        val isPattern  = state.selectedSegmentForParallelPudorys === segment
        val isHovered  = state.hoveredSegmentPudorys == segment
        val isSelected = state.selectedSegmentsPudorys.any { it.id == segment.id }
        val isMeridianSel = state.selectedMeridianPudorysIds.contains(segment.id) ||
                (segment.parent?.id?.let { state.selectedMeridianPudorysIds.contains(it) } == true)
        val isPendingSolidBase = false
        val needHighlight = isPending || isPattern || isSelected || (isHovered && state.drawobjects == Mongeobjects.NONE || state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION) || isMeridianSel || isPendingSolidBase
        if (!needHighlight) continue

        val aWorld = Offset(segment.start.x, segment.start.y)
        val bWorld = Offset(segment.end.x, segment.end.y)

        val baseStroke = strokePx(segment.strokeWidth, pxPerPt)

        val highlightColor = when {
            isMeridianSel || isPending || isPattern || isPendingSolidBase -> Color(0xFF1CD9B3).copy(alpha = 0.45f)
            isSelected -> state.selectedHaloColor
            else -> state.hoverHaloColor
        }
        val highlightStroke = when {
            isMeridianSel || isPending || isPattern || isPendingSolidBase -> baseStroke + PENDING_HALO_EXTRA_PX * pxPerPt
            isSelected -> baseStroke + SELECTION_HALO_EXTRA_PX * pxPerPt
            else -> baseStroke + HOVER_HALO_EXTRA_PX * pxPerPt
        }

        val patternPx = lineStylePatternPx(
            style = segment.lineStyle,
            scale = scale
        )
        val pathEffect = patternPx?.let { PathEffect.dashPathEffect(it, 0f) }
        val aExt = aWorld
        val bExt = bWorld

        drawLine(
            color = highlightColor,
            start = Offset(aExt.x * scale + canvasOffset.x, aExt.y * scale + canvasOffset.y),
            end   = Offset(bExt.x * scale + canvasOffset.x, bExt.y * scale + canvasOffset.y),
            strokeWidth = highlightStroke,
            pathEffect = pathEffect,
            cap = StrokeCap.Round
        )
    }
}
fun lineStylePatternPx(
    style: LineStyle,
    scale: Float = 1f
): FloatArray? {
    return lineStyleDashPatternPx(style, scale)?.intervals
}

fun lineStylePathEffectInWorkspace(style: LineStyle): PathEffect? {
    val intervalsPx = lineStylePatternPx(style) ?: return null
    return PathEffect.dashPathEffect(intervalsPx, 0f)
}