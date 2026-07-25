package draw.mongescreen.objects.orth

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import draw.mongescreen.DashPatternPx
import draw.mongescreen.lineStyleDashPatternPx
import draw.mongescreen.objects.axo.positiveMod
import draw.mongescreen.objects.HOVER_HALO_EXTRA_PX
import draw.mongescreen.objects.PENDING_HALO_EXTRA_PX
import draw.mongescreen.objects.SELECTION_HALO_EXTRA_PX
import draw.mongescreen.objects.strokePx
import model.*
import model.classes.*
import monge.input.ruledsurface.isPendingRuledSurfaceDirectrix
import state.MongeState
import utils.dotProduct
import kotlin.math.abs


fun dashPxFor(style: LineStyle, scale: Float = 1f): DashPatternPx? = lineStyleDashPatternPx(style, scale)

private val planeTraceSelectionObjects = setOf(
    Mongeobjects.NONE,
    Mongeobjects.PLANE,
    Mongeobjects.PLANE_LIFT,
    Mongeobjects.CYLINDER,
    Mongeobjects.REGULAR_POLYGON_IN_PLANE
)
fun projectedDashPathEffect(
    lineStyle: LineStyle,
    aWorkspace: Offset,
    anchorWorkspace: Offset,
    dirWorkspace: Offset
): PathEffect? {
    val dash = lineStyleDashPatternPx(lineStyle) ?: return null
    val intervalsWorkspace = dash.intervals
    val patternLenWorkspace = dash.len
    val dirLen = dirWorkspace.getDistance()
    if (dirLen < 1e-6f || patternLenWorkspace < 1e-6f) return null
    val phase = positiveMod((aWorkspace - anchorWorkspace).dotProduct(dirWorkspace / dirLen), patternLenWorkspace)
    return PathEffect.dashPathEffect(intervalsWorkspace, phase)
}

private val planeConicObjects = setOf(
    Mongeobjects.CIRCLE,
    Mongeobjects.ELLIPSE,
    Mongeobjects.PARABOLA,
    Mongeobjects.HYPERBOLA
)
private fun shouldHighlightPlaneTraces(state: MongeState): Boolean =
    state.drawobjects in planeTraceSelectionObjects ||
            (state.drawobjects in planeConicObjects && state.projekcnityp == ProjectionType.ASSOCIATED)
fun isPlaneTraceSelectedForDisplay(state: MongeState, parentId: String?): Boolean =
    shouldHighlightPlaneTraces(state) && parentId != null && state.selectedPlanes.any { it.id == parentId }
fun isPlaneTracePendingForDisplay(state: MongeState, parentId: String?): Boolean =
    shouldHighlightPlaneTraces(state) && parentId != null && state.selectedPlaneForCircle?.id == parentId

/**
 * Rovina je „vstupem konstrukce" (zelená) – sjednocené napříč Monge i AXO a všemi konstrukcemi.
 * Zelená se obarví dedikovaná rovina konstrukce (selectedPlaneForCircle) nebo rovina vybraná
 * během libovolné aktivní konstrukce (podstava válce/jehlanu apod.). Běžný výběr roviny
 * v režimu úprav (NONE) zůstává modrý jako u ostatních objektů.
 */
fun isPlaneTraceConstructionInput(state: MongeState, parentId: String?): Boolean {
    if (parentId == null) return false
    if (state.selectedPlaneForCircle?.id == parentId) return true
    if (state.drawobjects == Mongeobjects.RULED_SURFACE &&
        state.pendingRuledSurfaceDirectorPlaneId == parentId
    ) return true
    return state.drawobjects != Mongeobjects.NONE &&
            state.selectedPlanes.any { it.id == parentId }
}
fun isHyperbolaAsymptotePudorys(state: MongeState, line: NamedLinePudorys): Boolean =
    state.drawobjects == Mongeobjects.HYPERBOLA &&
            state.projectionPhase in setOf(
        "hyperbola_asymptote2",
        "hyperbola_vertex",
        "pudorys_asymptote2",
        "pudorys_vertex"
    ) &&
            (state.selectedLineForParallelPudorys === line || state.selectedLineForParallelPudorysSecond === line)
fun isHyperbolaAsymptoteNarys(state: MongeState, line: NamedLineNarys): Boolean =
    state.drawobjects == Mongeobjects.HYPERBOLA &&
            state.projectionPhase in setOf(
        "hyperbola_asymptote2_narys",
        "hyperbola_vertex_narys",
        "narys_asymptote2",
        "narys_vertex"
    ) &&
            (state.selectedLineForParallelNarys === line || state.selectedLineForParallelNarysSecond === line)
fun intersectLineWithView(
    origin: Offset,
    direction: Offset,
    rect: Rect
): Pair<Offset, Offset>? {
    val intersections = mutableListOf<Pair<Float, Offset>>() // t + p

    val edges = listOf(
        Pair(Offset(rect.left, rect.top), Offset(rect.right, rect.top)),     // top
        Pair(Offset(rect.right, rect.top), Offset(rect.right, rect.bottom)), // right
        Pair(Offset(rect.right, rect.bottom), Offset(rect.left, rect.bottom)), // bottom
        Pair(Offset(rect.left, rect.bottom), Offset(rect.left, rect.top))    // left
    )

    for ((a, b) in edges) {
        val edgeDir = b - a
        val denom = direction.x * edgeDir.y - direction.y * edgeDir.x
        if (abs(denom) < 1e-6f) continue // parallel

        val dx = a.x - origin.x
        val dy = a.y - origin.y
        val t = (dx * edgeDir.y - dy * edgeDir.x) / denom
        val u = (dx * direction.y - dy * direction.x) / denom

        if (t.isFinite() && u in 0f..1f) {
            val intersection = origin + direction * t
            intersections.add(t to intersection)
        }
    }

    if (intersections.size < 2) return null

    val sorted = intersections.sortedBy { it.first }
    return sorted.first().second to sorted.last().second
}
fun clipLineAboveX12(p1: Offset, p2: Offset): Pair<Offset, Offset>? {
    val (a, b) = if (p1.y <= p2.y) p1 to p2 else p2 to p1

    return when {
        a.y >= 0f && b.y >= 0f -> a to b // vše nad osou
        a.y < 0f && b.y >= 0f -> {
            val t = -a.y / (b.y - a.y)
            val x = a.x + t * (b.x - a.x)
            Offset(x, 0f) to b
        }
        else -> null // celé pod osou z
    }
}

fun DrawScope.drawScreenBasedWorldRect(state: MongeState): Rect {
    val scale = state.scale
    val canvasOffset = state.canvasOffset

    val marginPx = -10f
    val screenRect = Rect(
        offset = Offset(marginPx, marginPx),
        size = Size(size.width - 2 * marginPx, size.height - 2 * marginPx)
    )

    drawRect(
        color = Color.Green.copy(alpha = 0.4f),
        topLeft = screenRect.topLeft,
        size = screenRect.size,
        style = Stroke(width = 1.dp.toPx())
    )

    val topLeftScreen = screenRect.topLeft
    val bottomRightScreen = screenRect.bottomRight

    val topLeftWorld = Offset(
        (topLeftScreen.x - canvasOffset.x) / scale,
        -(topLeftScreen.y - canvasOffset.y) / scale  // ⬅️ Inverze
    )
    val bottomRightWorld = Offset(
        (bottomRightScreen.x - canvasOffset.x) / scale,
        -(bottomRightScreen.y - canvasOffset.y) / scale  // ⬅️ Inverze
    )

    return Rect(topLeftWorld, bottomRightWorld)
}
fun DrawScope.drawScreenBasedWorldRectPudorys(state: MongeState): Rect {
    val scale = state.scale
    val canvasOffset = state.canvasOffset

    val marginPx = -10f
    val screenRect = Rect(
        offset = Offset(marginPx, marginPx),
        size = Size(size.width - 2 * marginPx, size.height - 2 * marginPx)
    )
    drawRect(
        color = Color(0xFF1CD9B3).copy(alpha = 0.4f),
        topLeft = screenRect.topLeft,
        size = screenRect.size,
        style = Stroke(width = 1.dp.toPx())
    )

    val topLeftScreen = screenRect.topLeft
    val bottomRightScreen = screenRect.bottomRight

    val topLeftWorld = Offset(
        (topLeftScreen.x - canvasOffset.x) / scale,
        (topLeftScreen.y - canvasOffset.y) / scale  // ⬅️ bez inverze
    )
    val bottomRightWorld = Offset(
        (bottomRightScreen.x - canvasOffset.x) / scale,
        (bottomRightScreen.y - canvasOffset.y) / scale  // ⬅️ bez inverze
    )

    return Rect(topLeftWorld, bottomRightWorld)
}
fun clipLineBelowX12(p1: Offset, p2: Offset): Pair<Offset, Offset>? {
    val (a, b) = if (p1.y <= p2.y) p1 to p2 else p2 to p1

    return when {
        a.y >= 0f && b.y >= 0f -> a to b // vše pod osou
        a.y < 0f && b.y >= 0f -> {
            val t = -a.y / (b.y - a.y)
            val x = a.x + t * (b.x - a.x)
            Offset(x, 0f) to b
        }
        else -> null // celé nad osou x₁₂
    }
}
fun DrawScope.drawLinesNarys(state: MongeState,showHelpLine:Boolean,pxPerPt: Float) {
    val scale = state.scale
    val canvasOffset = state.canvasOffset
    val viewRect = drawScreenBasedWorldRect(state)

    val allLines = buildList {
        addAll(state.lines3DNarys)
        addAll(state.lineTracesNarys)
        if (state.showConstruction.value && showHelpLine) {
            addAll(state.helpLineNarys)
        }
    }

    for (line in allLines) {
        val rawStart = Offset(line.point.x, line.point.z)
        val rawDir = Offset(line.direction.x, line.direction.y)
        if (rawDir.getDistanceSquared() < 1e-6f) continue

        // Fix směru: pokud směr jde nahoru (y < 0), otočíme směr i výchozí bod
        val dir = if (rawDir.y < 0f) -rawDir else rawDir
        val start = if (rawDir.y < 0f) rawStart + rawDir else rawStart
        val isPending = when (line) {
            is PlaneTraceNarys ->
                isHyperbolaAsymptoteNarys(state, line) ||
                        isPlaneTraceConstructionInput(state, line.parentId ?: line.parent?.id)
            is Line3DProjectionNarys ->
                isHyperbolaAsymptoteNarys(state, line) ||
                        isPendingRuledSurfaceDirectrix(state, line.parent?.id ?: line.parentId) ||
                        state.selectedLineForParallelNarys === line ||
                        state.selectedLineForParallelNarysSecond === line ||
                        if(line.parent != null) { state.pendingLine3DId == line.parent?.id
                                || state.selectedRevolutionAxis3D?.id == line.parent?.id} else false
            else -> false
        }
        val isHovered =  state.hoveredLineNarys == line
        val isSelected = state.selectedLinesNarys.any { it.id == line.id } ||
                (line is PlaneTraceNarys && isPlaneTraceSelectedForDisplay(state, line.parentId ?: line.parent?.id))
        val color = when {
            isPending -> Color(0xFF1CD9B3)
            else -> line.color
        }
        val stroke = strokePx(line.strokeWidth, pxPerPt)


        val dirLen = dir.getDistance()
        if (dirLen < 1e-6f) continue
        val unitDir = dir / dirLen

        val visible = intersectLineWithView(start, dir, viewRect) ?: continue

        val shouldClip = line.clipLineX ?: state.defaultClipAboveX12Narys

        val clipped = if (shouldClip) {
            clipLineAboveX12(visible.first, visible.second) ?: continue
        } else {
            visible
        }
        val customClipped = clipNarysSegmentByCustomTrim(clipped, line) ?: continue
        val (a, b) = customClipped



        val dash = dashPxFor(line.lineStyle, scale)

        val pathEffect = dash?.let { (intervals, patternLenPx) ->
            val base = Offset(0f, 0f) // fixní bod pro výpočet vzoru

            // projekce vzdálenosti do směru v WORLD
            val distanceToAWorld = (a - base).dotProduct(unitDir)

            // převod na PX (world -> px) jen jednou:
            val distanceToAPx = distanceToAWorld * scale

            // phase musí být v rozsahu [0, patternLenPx)
            val finalPhasePx = ((distanceToAPx % patternLenPx) + patternLenPx) % patternLenPx

            PathEffect.dashPathEffect(intervals, finalPhasePx)
        }

        val path = Path().apply {
            moveTo(a.x * scale + canvasOffset.x, -a.y * scale + canvasOffset.y)
            lineTo(b.x * scale + canvasOffset.x, -b.y * scale + canvasOffset.y)
        }

        when {
            isPending ->
                drawPath(path, Color(0xFF1CD9B3).copy(alpha = 0.45f), style = Stroke(width = stroke + PENDING_HALO_EXTRA_PX * pxPerPt, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = pathEffect))
            isSelected ->
                drawPath(path, state.selectedHaloColor, style = Stroke(width = stroke + SELECTION_HALO_EXTRA_PX * pxPerPt, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = pathEffect))
            isHovered && state.drawobjects == Mongeobjects.NONE ->
                drawPath(path, state.hoverHaloColor, style = Stroke(width = stroke + HOVER_HALO_EXTRA_PX * pxPerPt, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = pathEffect))
        }

        drawPath(
            path = path,
            color = color.runtimeDrawColor(),
            style = Stroke(
                width = stroke,
                pathEffect = pathEffect,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
fun DrawScope.drawLinesPudorys(state: MongeState,showHelpLine: Boolean, pxPerPt: Float) {
    val scale = state.scale
    val canvasOffset = state.canvasOffset
    val viewRect = drawScreenBasedWorldRectPudorys(state)

    val allLines = buildList {
        addAll(state.lines3DPudorys)
        addAll(state.lineTracesPudorys)
        if (state.showConstruction.value&& showHelpLine) {          // ⬅️ jen když mají být vidět
            addAll(state.helpLinePudorys)
        }
    }
    for (line in allLines) {
        val rawStart = Offset(line.point.x, line.point.y)
        val rawDir = Offset(line.direction.x, line.direction.y)
        if (rawDir.getDistanceSquared() < 1e-6f) continue

        // Fix směru: pokud jde směrem dolů (y < 0), otočíme směr i výchozí bod
        val dir = if (rawDir.y < 0f) -rawDir else rawDir
        val start = if (rawDir.y < 0f) rawStart + rawDir else rawStart

        val isSelected =
            state.selectedLinesPudorys.any { it.id == line.id } ||
                    (line is PlaneTracePudorys && isPlaneTraceSelectedForDisplay(state, line.parentId ?: line.parent?.id))
        val isPending = when (line) {
            is PlaneTracePudorys ->
                isHyperbolaAsymptotePudorys(state, line) ||
                        isPlaneTraceConstructionInput(state, line.parentId ?: line.parent?.id)

            is Line3DProjectionPudorys ->
                isHyperbolaAsymptotePudorys(state, line) ||
                        isPendingRuledSurfaceDirectrix(state, line.parent?.id ?: line.parentId) ||
                        state.selectedLineForParallelPudorys === line ||
                        state.selectedLineForParallelPudorysSecond === line ||
                        if (line.parent != null) {
                            state.pendingLine3DId == line.parent?.id||state.selectedLineForPoint?.id==line.parent?.id  || state.selectedRevolutionAxis3D?.id == line.parent?.id
                        }  else false

            else -> false
        }
        val isHovered = state.hoveredLinePudorys == line

        val color = when {
            isPending -> Color(0xFF1CD9B3)
            else -> line.color
        }
        val stroke = strokePx(line.strokeWidth, pxPerPt)


        val dirLen = dir.getDistance()
        if (dirLen < 1e-6f) continue
        val unitDir = dir / dirLen

        val visible = intersectLineWithView(start, dir, viewRect) ?: continue

// efektivní klip: vezmi override pokud je nastaven, jinak global default
        val shouldClip =
            if (state.projectionMode == ProjectionMode.PLANE||state.projectionMode == ProjectionMode.KOTO) false
            else line.clipLineX ?: state.defaultClipBelowX12Pudorys

        val clipped = if (shouldClip) {
            clipLineBelowX12(visible.first, visible.second) ?: continue
        } else {
            visible
        }
        val customClipped = clipPudorysSegmentByCustomTrim(clipped, line) ?: continue
        val (a, b) = customClipped
        // Výpočet pevné fáze čárování


        val dash = dashPxFor(line.lineStyle, scale)
        val pathEffect = dash?.let { (intervals, patternLenPx) ->
            val base = Offset(0f, 0f) // fixní bod pro výpočet vzoru

            // projekce vzdálenosti do směru v WORLD
            val distanceToAWorld = (a - base).dotProduct(unitDir)

            // převod na PX (world -> px) jen jednou:
            val distanceToAPx = distanceToAWorld * scale

            // phase musí být v rozsahu [0, patternLenPx)
            val finalPhasePx = ((distanceToAPx % patternLenPx) + patternLenPx) % patternLenPx

            PathEffect.dashPathEffect(intervals, finalPhasePx)
        }

        val path = Path().apply {
            moveTo(a.x * scale + canvasOffset.x, a.y * scale + canvasOffset.y)
            lineTo(b.x * scale + canvasOffset.x, b.y * scale + canvasOffset.y)
        }

        when {
            isPending ->
                drawPath(path, Color(0xFF1CD9B3).copy(alpha = 0.45f), style = Stroke(width = stroke + PENDING_HALO_EXTRA_PX * pxPerPt, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = pathEffect))
            isSelected ->
                drawPath(path, state.selectedHaloColor, style = Stroke(width = stroke + SELECTION_HALO_EXTRA_PX * pxPerPt, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = pathEffect))
            isHovered && state.drawobjects == Mongeobjects.NONE ->
                drawPath(path, state.hoverHaloColor, style = Stroke(width = stroke + HOVER_HALO_EXTRA_PX * pxPerPt, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = pathEffect))
        }

        drawPath(
            path = path,
            color = color.runtimeDrawColor(),
            style = Stroke(
                width = stroke,
                pathEffect = pathEffect,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
