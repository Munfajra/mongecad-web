package draw.mongescreen.objects.orth

import draw.mongescreen.objects.drawCurvePath
import draw.mongescreen.objects.isCurveHoveredNarys
import draw.mongescreen.objects.isCurveHoveredPudorys
import draw.mongescreen.objects.isCurveSelectedNarys
import draw.mongescreen.objects.isCurveSelectedPudorys
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.lineStyleDashPathEffectPx
import draw.mongescreen.objects.HOVER_HALO_EXTRA_PX
import draw.mongescreen.objects.PENDING_HALO_EXTRA_PX
import draw.mongescreen.objects.SELECTION_HALO_EXTRA_PX
import draw.mongescreen.objects.axo.*
import geometry.drawSmoothCurve
import geometry.toLogical
import model.*
import model.classes.CurvePudRef
import geometry.logicalToScreen
import monge.input.ruledsurface.isPendingRuledSurfaceDirectrix
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.drawCurveNarys(state: MongeState) {
    val pointsByIdNar = state.pointsNarys.associateBy { it.id }

    state.curvesNarys.forEach { curve ->
        // Obrys přímkové plochy má v Monge vlastní renderer, který jej kromě
        // zvýraznění také ořezává osou x₁₂. Zde by se jinak kreslil podruhé celý.
        if (state.projectionMode == ProjectionMode.MONGE &&
            curve.parentId?.let { parentId -> state.ruledSurfaces.any { it.id == parentId } } == true
        ) return@forEach
        val logicalPts = curve.polylineLocal ?: curve.pointIds.mapNotNull { id ->
            val p = pointsByIdNar[id] ?: return@mapNotNull null
            Offset(p.x, p.z)
        }
        val pts = logicalPts.map { logicalToScreenNarys(it, state) }

        if (pts.size < 2) return@forEach

        val pending = isPendingRuledSurfaceDirectrix(state, curve.parentId ?: curve.parent?.id)
        val selected = isCurveSelectedNarys(state, curve)
        val w = curve.effectiveStrokeWidth
        val bakedPolyline = curve.polylineLocal != null

        if (pending) {
            drawCurvePath(pts, Color(0xFF1CD9B3).copy(alpha = 0.45f), w + PENDING_HALO_EXTRA_PX, curve.effectiveLineStyle, curve.closed, state.scale, bakedPolyline)
        } else if (selected) {
            drawCurvePath(pts, state.selectedHaloColor, w + SELECTION_HALO_EXTRA_PX, curve.effectiveLineStyle, curve.closed, state.scale, bakedPolyline)
        } else if (isCurveHoveredNarys(state, curve)) {
            drawCurvePath(pts, state.hoverHaloColor, w + HOVER_HALO_EXTRA_PX, curve.effectiveLineStyle, curve.closed, state.scale, bakedPolyline)
        }

        drawCurvePath(
            points = pts,
            color = (if (pending) Color(0xFF1CD9B3) else curve.effectiveColor).runtimeDrawColor(),
            strokeWidth = w,
            lineStyle = curve.effectiveLineStyle,
            closed = curve.closed,
            scale = state.scale,
            bakedPolyline = bakedPolyline
        )
    }
}
fun DrawScope.drawCurvePudorys(state: MongeState) {
    val pointsByIdPud = state.pointsPudorys.associateBy { it.id }
    val aidById = state.aidPointsLogical.associateBy { it.id }

    state.curvesPudorys.forEach { curve ->
        if (state.projectionMode == ProjectionMode.MONGE &&
            curve.parentId?.let { parentId -> state.ruledSurfaces.any { it.id == parentId } } == true
        ) return@forEach
        val logicalPts = curve.polylineLocal ?: curve.points.mapNotNull { ref ->
            val logical = when (ref) {
                is CurvePudRef.P -> pointsByIdPud[ref.pointId]?.let { Offset(it.x, it.y) }
                is CurvePudRef.A -> aidById[ref.aidId]?.let { Offset(it.x, it.y) }
            } ?: return@mapNotNull null
            logical
        }
        val pts = logicalPts.map { logicalToScreen(it, state.canvasOffset, state.scale) }

        if (pts.size < 2) return@forEach

        val pending = isPendingRuledSurfaceDirectrix(state, curve.parentId ?: curve.parent?.id)
        val selected = isCurveSelectedPudorys(state, curve)
        val w = curve.effectiveStrokeWidth
        val bakedPolyline = curve.polylineLocal != null

        if (pending) {
            drawCurvePath(pts, Color(0xFF1CD9B3).copy(alpha = 0.45f), w + PENDING_HALO_EXTRA_PX, curve.effectiveLineStyle, curve.closed, state.scale, bakedPolyline)
        } else if (selected) {
            drawCurvePath(pts, state.selectedHaloColor, w + SELECTION_HALO_EXTRA_PX, curve.effectiveLineStyle, curve.closed, state.scale, bakedPolyline)
        } else if (isCurveHoveredPudorys(state, curve)) {
            drawCurvePath(pts, state.hoverHaloColor, w + HOVER_HALO_EXTRA_PX, curve.effectiveLineStyle, curve.closed, state.scale, bakedPolyline)
        }

        drawCurvePath(
            points = pts,
            color = (if (pending) Color(0xFF1CD9B3) else curve.effectiveColor).runtimeDrawColor(),
            strokeWidth = w,
            lineStyle = curve.effectiveLineStyle,
            closed = curve.closed,
            scale = state.scale,
            bakedPolyline = bakedPolyline
        )
    }
}
fun DrawScope.drawCurvePudorysPreview(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    if (state.mongeMode != DrawingModeMonge.PUDORYS) return
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "pudorys_curve_pick") return

    val pickedRefs = state.pudorysCurvePickRefs   // ✅ místo pudorysCurvePickPointIds
    if (pickedRefs.isEmpty()) return

    // cursor v LOGICAL (včetně snapu)
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

    // vybrané body v LOGICAL (Point i AidPoint)
    val ptsLogical: List<Offset> = pickedRefs.mapNotNull { ref ->
        ref.toLogical(state) // ✅ tvoje helper funkce
    }
    if (ptsLogical.isEmpty()) return

    // přidej kurzor jako poslední bod (preview)
    val previewLogical = ptsLogical + cursorLogical

    // LOGICAL -> SCREEN (použij stejnou transformaci jako jinde)
    val ptsScreen = previewLogical.map { logical ->
        logicalToScreen(logical, state.canvasOffset, state.scale)
    }
    if (ptsScreen.size < 2) return

    val previewColor = Color.Gray.copy(alpha = 0.7f)
    val previewWidthPx = 2f

    // 1 vybraný bod + kurzor => jen pomocná čára
    if (ptsScreen.size == 2) {
        drawLine(
            color = previewColor.runtimeDrawColor(),
            start = ptsScreen[0],
            end = ptsScreen[1],
            strokeWidth = previewWidthPx,
            pathEffect = lineStyleDashPathEffectPx(LineStyle.Dashed, scale = state.scale)
        )
        return
    }

    // spline preview: šedá + dashed
    drawSmoothCurve(
        controlPoints = ptsScreen,
        color = previewColor.runtimeDrawColor(),
        strokeWidth = previewWidthPx,
        lineStyle = LineStyle.Dashed,
        closed = state.pudorysCurvePickClosed,
        stepsPerSegment = 18,
        scale = state.scale
    )
}


private fun logicalToScreenNarys(
    logical: Offset,
    state: MongeState
): Offset {
    // nárys: překlop Y (protože u tebe je to “-z”)
    val flipped = Offset(logical.x, -logical.y)
    return logicalToScreen(flipped, state.canvasOffset, state.scale)
}
fun DrawScope.drawCurveNarysPreview(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    if (state.mongeMode != DrawingModeMonge.NARYS) return
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "narys_curve_pick") return

    val pickedIds = state.narysCurvePickPointIds
    if (pickedIds.isEmpty()) return

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

    // flipni Y pro nárys
    val cursorLogicalN = Offset(cursorLogical.x, -cursorLogical.y)

    val ptsLogical = pickedIds.mapNotNull { id ->
        val p = state.pointsNarys.find { it.id == id } ?: return@mapNotNull null
        Offset(p.x, p.z)
    }
    if (ptsLogical.isEmpty()) return

    val previewLogical = ptsLogical + cursorLogicalN

    val ptsScreen = previewLogical.map { logicalToScreenNarys(it, state) }
    if (ptsScreen.size < 2) return

    val previewColor = Color.Gray.copy(alpha = 0.7f)
    val previewWidthPx = 2f

    if (ptsScreen.size == 2) {
        drawLine(
            color = previewColor.runtimeDrawColor(),
            start = ptsScreen[0],
            end = ptsScreen[1],
            strokeWidth = previewWidthPx,
            pathEffect = lineStyleDashPathEffectPx(LineStyle.Dashed, scale = state.scale)
        )
        return
    }

    drawSmoothCurve(
        controlPoints = ptsScreen,
        color = previewColor.runtimeDrawColor(),
        strokeWidth = previewWidthPx,
        lineStyle = LineStyle.Dashed,
        closed = state.narysCurvePickClosed,
        stepsPerSegment = 18,
        scale = state.scale
    )
}
private fun points3DToPudorysScreen(
    pts3D: List<Point3D>,
    state: MongeState
): List<Offset> =
    pts3D.map { p3 ->
        // logical (x,y) -> screen
        logicalToScreen(Offset(p3.x, p3.y), state.canvasOffset, state.scale)
    }
private fun points3DToNarysScreen(
    pts3D: List<Point3D>,
    state: MongeState
): List<Offset> =
    pts3D.map { p3 ->
        // logical v nárysu u tebe: (x,z) -> screen (překlopení řeší logicalToScreenNarys)
        logicalToScreenNarys(Offset(p3.x, p3.z), state)
    }
fun DrawScope.drawCurve3DPreviewPudorys(state: MongeState) {
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "curve3d_pick_points") return

    val ids = state.curve3DPickPointIds
    if (ids.isEmpty()) return

    val pts3D = ids.mapNotNull { id -> state.sharedPoints3D.find { it.id == id } }
    if (pts3D.size < 2) return

    val ptsScreen = points3DToPudorysScreen(pts3D, state)
    if (ptsScreen.size < 2) return

    val previewColor = Color.Gray.copy(alpha = 0.7f)
    val previewWidth = 2f

    // když jsou jen 2 body, klidně jen line (nebo i spline – obojí ok)
    if (ptsScreen.size == 2) {
        drawLine(
            color = previewColor.runtimeDrawColor(),
            start = ptsScreen[0],
            end = ptsScreen[1],
            strokeWidth = previewWidth,
            pathEffect = lineStyleDashPathEffectPx(LineStyle.Dashed, scale = state.scale)
        )
        return
    }

    drawSmoothCurve(
        controlPoints = ptsScreen,
        color = previewColor.runtimeDrawColor(),
        strokeWidth = previewWidth,
        closed = false,
        stepsPerSegment = 18,
        lineStyle = LineStyle.Dashed,
        scale = state.scale
    )
}
fun DrawScope.drawCurve3DPreviewNarys(state: MongeState) {
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "curve3d_pick_points") return

    val ids = state.curve3DPickPointIds
    if (ids.isEmpty()) return

    val pts3D = ids.mapNotNull { id -> state.sharedPoints3D.find { it.id == id } }
    if (pts3D.size < 2) return

    val ptsScreen = points3DToNarysScreen(pts3D, state)
    if (ptsScreen.size < 2) return

    val previewColor = Color.Gray.copy(alpha = 0.7f)
    val previewWidth = 2f

    if (ptsScreen.size == 2) {
        drawLine(
            color = previewColor.runtimeDrawColor(),
            start = ptsScreen[0],
            end = ptsScreen[1],
            strokeWidth = previewWidth,
            pathEffect = lineStyleDashPathEffectPx(LineStyle.Dashed, scale = state.scale)
        )
        return
    }

    drawSmoothCurve(
        controlPoints = ptsScreen,
        color = previewColor.runtimeDrawColor(),
        strokeWidth = previewWidth,
        closed = state.curve3DPickClosed,   // ✅
        stepsPerSegment = 18,
        lineStyle = LineStyle.Dashed,
        scale = state.scale
    )

}
