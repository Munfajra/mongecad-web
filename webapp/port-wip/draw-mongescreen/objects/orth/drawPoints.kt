package draw.mongescreen.objects.orth

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.EligiblePointGlow
import draw.mongescreen.objects.EligiblePointRing
import draw.mongescreen.objects.HOVER_HALO_EXTRA_PX
import draw.mongescreen.objects.POINT_STROKE_WEIGHT
import draw.mongescreen.objects.SELECTION_HALO_EXTRA_PX
import draw.mongescreen.objects.strokePx
import model.*
import model.classes.CurvePudRef
import state.MongeState

private fun cross2(a: Offset, b: Offset): Float = a.x * b.y - a.y * b.x
private fun len2(v: Offset): Float = kotlin.math.hypot(v.x, v.y)



private fun distancePointToInfiniteLine(p: Offset, a: Offset, dir: Offset): Float {
    val denom = len2(dir)
    if (denom <= 1e-6f) return Float.POSITIVE_INFINITY
    return kotlin.math.abs(cross2(p - a, dir)) / denom
}
fun isPointOnLine(p: Offset, a: Offset, dir: Offset, eps: Float): Boolean {
    return distancePointToInfiniteLine(p, a, dir) <= eps
}
// Body NÁRYS
fun DrawScope.drawPointsNarys(
    state: MongeState,
    pxPerPt: Float,
    markerPxPerPt: Float = pxPerPt
) {

    // globální režim "teď klikej na body" pro nárys (bez KOTO)
    val highlightClickPointsModeGlobal =
        (state.drawobjects == Mongeobjects.SPHERE || state.drawobjects == Mongeobjects.CONE) &&
                state.mongeMode == DrawingModeMonge.NARYS &&
                state.projectionPhase == "narys_start"

    // ===== KŘIVKY =====
    val isPickingNarysCurve =
        state.drawobjects == Mongeobjects.CURVE &&
                (state.projekcnityp == ProjectionType.SINGLE || state.projekcnityp == ProjectionType.AUXILIARY) &&
                state.mongeMode == DrawingModeMonge.NARYS

    val isPickingCurve3D =
        state.drawobjects == Mongeobjects.CURVE && state.projekcnityp == ProjectionType.ASSOCIATED

    val pickedNarysCurveIds: Set<String> =
        if (isPickingNarysCurve) state.narysCurvePickPointIds.toSet() else emptySet()

    val pickedCurve3DPoint3DIds: Set<String> =
        if (isPickingCurve3D) state.curve3DPickPointIds.toSet() else emptySet()

    val pointIdsUsedInCurvesNarys: Set<String> =
        state.curvesNarys
            .flatMap { it.pointIds }
            .toSet()
    for (point in state.pointsNarys) {

        // ===== allowed (na co jde kliknout) =====
        val isAllowedNarysCurvePickPoint = isPickingNarysCurve                 // standalone: všechny body
        val isAllowedCurve3DPickPoint    = isPickingCurve3D && point.parent != null // 3D: jen s parent

        val isAllowedAnyCurvePickPoint = isAllowedNarysCurvePickPoint || isAllowedCurve3DPickPoint

        // ===== picked (už zahrnuté do křivky) =====
        val isPickedForNarysCurve = isPickingNarysCurve && pickedNarysCurveIds.contains(point.id)

        val isPickedForCurve3D =
            isPickingCurve3D &&
                    point.parent != null &&
                    pickedCurve3DPoint3DIds.contains(point.parent!!.id)

        val isPickedForAnyCurve = isPickedForNarysCurve || isPickedForCurve3D

        // původní “click points mode”
        val highlightClickPointsMode =
            ((point.parent != null) && highlightClickPointsModeGlobal) || isAllowedAnyCurvePickPoint

        val screenX = point.x * state.scale + state.canvasOffset.x
        val screenY = -point.z * state.scale + state.canvasOffset.y
        val center = Offset(screenX, screenY)

        val isSelected = state.selectedPointsNarys.any { it.id == point.id }
        val isHovered  = point == state.snappedPointNarys

        val isPickedApex =
            (state.pendingApex3DId != null && point.parent?.id == state.pendingApex3DId) ||
                    (state.pendingPyramidApexId != null && point.parent?.id == state.pendingPyramidApexId)
        val isInCurve = point.id in pointIdsUsedInCurvesNarys
        val baseSize = strokePx(state.scale, markerPxPerPt)
        val sizeFromWidth = strokePx(point.width * state.scale, markerPxPerPt)

        val pickedMain  = Color(0xFF00EAA0)
        val pickedGlow  = pickedMain.copy(alpha = 0.18f)
        val pickedRing1 = Color(0xFFFFFFFF).copy(alpha = 0.85f)
        val pickedRing2 = pickedMain.copy(alpha = 0.85f)

        val crossColor = when {
            isPickedApex            -> pickedMain
            isPickedForAnyCurve     -> pickedMain
            else                    -> point.color
        }

        val size = when {
            isPickedApex        -> baseSize + sizeFromWidth * 1.2f
            isPickedForAnyCurve -> baseSize + sizeFromWidth * 1.15f
            isInCurve           -> baseSize / 1.5f
            else                -> baseSize + sizeFromWidth
        }

        val lineWidth = point.width * POINT_STROKE_WEIGHT *state.scale

        /* ===============================
           HALO / RING
           =============================== */

        if (isPickedApex || isPickedForAnyCurve) {
            val ringRadius = size * 1.55f
            val haloRadius = ringRadius * 1.25f

            drawCircle(
                color = pickedGlow,
                radius = haloRadius,
                center = center
            )

            drawCircle(
                color = pickedRing1,
                radius = ringRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (lineWidth * 1.2f).coerceAtLeast(strokePx(1.6f, pxPerPt))
                )
            )

            drawCircle(
                color = pickedRing2,
                radius = ringRadius * 1.12f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (lineWidth * 0.9f).coerceAtLeast(strokePx(1.2f, pxPerPt))
                )
            )

        } else if (highlightClickPointsMode) {
            val ringRadius = size * 1.55f
            val haloRadius = ringRadius * 1.25f

            drawCircle(
                color = EligiblePointGlow,
                radius = haloRadius,
                center = center
            )
            drawCircle(
                color = EligiblePointRing,
                radius = ringRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (lineWidth * 0.9f).coerceAtLeast(strokePx(1.2f, pxPerPt))
                )
            )
        }

        /* ===============================
           KŘÍŽEK
           =============================== */

        if (isSelected) {
            drawLine(
                state.selectedHaloColor,
                Offset(screenX - size, screenY),
                Offset(screenX + size, screenY),
                lineWidth + SELECTION_HALO_EXTRA_PX/8 * pxPerPt* state.scale,
                cap = StrokeCap.Round
            )
            drawLine(state.selectedHaloColor,
                Offset(screenX, screenY - size),
                Offset(screenX, screenY + size),
                lineWidth + SELECTION_HALO_EXTRA_PX/8 * pxPerPt* state.scale,
                cap = StrokeCap.Round
            )
        } else if (isHovered) {
            drawLine(
                state.hoverHaloColor,
                Offset(screenX - size, screenY),
                Offset(screenX + size, screenY),
                lineWidth + HOVER_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
            drawLine(
                state.hoverHaloColor,
                Offset(screenX, screenY - size),
                Offset(screenX, screenY + size),
                lineWidth + HOVER_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            crossColor.runtimeDrawColor(),
            Offset(screenX - size, screenY),
            Offset(screenX + size, screenY),
            lineWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            crossColor.runtimeDrawColor(),
            Offset(screenX, screenY - size),
            Offset(screenX, screenY + size),
            lineWidth,
            cap = StrokeCap.Round
        )
    }
}

//body PUDORYS
fun DrawScope.drawPointsPudorys(
    state: MongeState,
    pxPerPt: Float,
    markerPxPerPt: Float = pxPerPt
) {

    // režim "teď klikej na body" – je globální, ne per-p
    val highlightClickPointsModeGlobal =
        (state.projectionMode == ProjectionMode.KOTO &&
                state.projekcnityp == ProjectionType.ASSOCIATED &&
                (state.drawobjects == Mongeobjects.LINES ||
                        state.drawobjects == Mongeobjects.PLANE ||
                        state.drawobjects == Mongeobjects.SEGMENTS))||(state.projectionPhase=="plane_trace_pick_point")|| ((state.drawobjects== Mongeobjects.SPHERE||
                state.drawobjects == Mongeobjects.CONE)
                && state.mongeMode == DrawingModeMonge.PUDORYS&& (state.projectionPhase=="pudorys_start"))

    // najdi ID průmětů bodů vybraných pro rovinu (robustně přes parent.id)

    // ===== KŘIVKY (půdorys standalone / 3D pick) =====
    val isPickingPudCurve =
        state.drawobjects == Mongeobjects.CURVE &&
                (state.projekcnityp == ProjectionType.SINGLE || state.projekcnityp == ProjectionType.AUXILIARY) &&
                state.mongeMode == DrawingModeMonge.PUDORYS

    val isPickingCurve3D =
        state.drawobjects == Mongeobjects.CURVE && state.projekcnityp == ProjectionType.ASSOCIATED

    // body už zahrnuté do křivky:
    // standalone pudorys: ref list může obsahovat P i A, nás zajímá jen P body
    val pickedPudCurvePointIds: Set<String> =
        if (isPickingPudCurve)
            state.pudorysCurvePickRefs
                .mapNotNull { (it as? CurvePudRef.P)?.pointId }
                .toSet()
        else emptySet()
    val pointIdsUsedInCurvesPudorys: Set<String> =
        state.curvesPudorys
            .flatMap { curve ->
                curve.points.mapNotNull { ref ->
                    when (ref) {
                        is CurvePudRef.P -> ref.pointId
                        is CurvePudRef.A -> null
                    }
                }
            }
            .toSet()
    // 3D křivka: držíš 3D ids (Point3D.id), ale v pudorysu máš Point3DPudorys.id
    // takže mapujeme přes parent.id
    val pickedCurve3DPoint3DIds: Set<String> =
        if (isPickingCurve3D) state.curve3DPickPointIds.toSet() else emptySet()

    for (point in state.pointsPudorys) {
        val isPickingLinePoints = (state.projectionPhase == "picking_line_points")
        // ===== KŘIVKY =====

        // můžeš kliknout (standalone pudorys) -> všechny body
        val isAllowedPudCurvePickPoint = isPickingPudCurve

        // můžeš kliknout (3D křivka) -> jen body s parentem
        val isAllowedCurve3DPickPoint = isPickingCurve3D && point.parent != null

        // už vybrané body (zeleně)
        val isPickedForPudCurve = isPickingPudCurve && pickedPudCurvePointIds.contains(point.id)

        val isPickedForCurve3D =
            isPickingCurve3D &&
                    point.parent != null &&
                    pickedCurve3DPoint3DIds.contains(point.parent!!.id)

        val isPickedForAnyCurve = isPickedForPudCurve || isPickedForCurve3D
        val isAllowedAnyCurvePickPoint = isAllowedPudCurvePickPoint || isAllowedCurve3DPickPoint

        val pickedLine = state.linefrom2points?.id?.let { id ->
            state.lines3DPudorys.find { it.id == id }
        }
        val epsLogical = (6f / state.scale).coerceAtLeast(0.5f)
        val pickedLinePoint = pickedLine?.let { Offset(it.point.x, it.point.y) }
        val pickedLineDir   = pickedLine?.direction
        val isAllowedLinePickPoint =
            isPickingLinePoints &&
                    point.parent != null &&
                    pickedLinePoint != null &&
                    pickedLineDir != null &&
                    isPointOnLine(
                        p = Offset(point.x, point.y),
                        a = pickedLinePoint,
                        dir = pickedLineDir,
                        eps = epsLogical
                    )
        // lokální: highlight jen pro 3D body (parent != null) + jen v tom globálním režimu
        val highlightClickPointsMode =
            ((point.parent != null) && highlightClickPointsModeGlobal) || isAllowedLinePickPoint || isAllowedAnyCurvePickPoint


        val screenX = point.x * state.scale + state.canvasOffset.x
        val screenY = point.y * state.scale + state.canvasOffset.y
        val center = Offset(screenX, screenY)

        val isSelected = state.selectedPointsPudorys.any { it.id == point.id }


        val isHovered = point == state.snappedPointPudorys

        // rovina: je tenhle bod A/B průmět?

        val isPickedFirstForKotoLine =
            ((state.projectionMode == ProjectionMode.KOTO) &&
                    state.projekcnityp == ProjectionType.ASSOCIATED &&
                    point.parent != null &&
                    (
                            state.kotoLinePickAId == point.id ||
                                    state.kotoSegPickAId == point.id ||
                                    state.planePickAId == point.id ||
                                    state.planePickBId == point.id ||
                                    state.planePickCId == point.id
                            ))|| (point.parent?.id == state.pendingApex3DId && state.pendingApex3DId!=null )
                    || (point.parent?.id == state.pendingPyramidApexId && state.pendingPyramidApexId!=null )
                    || (isPickingLinePoints && state.kotoLinePickAId == point.id)

        /* ===============================
           DÉLKA KŘÍŽKU = hlavní význam width
           =============================== */

        val baseSize = strokePx(state.scale, markerPxPerPt)
        val sizeFromWidth = strokePx(point.width * state.scale, markerPxPerPt)
        val isInCurve = point.id in pointIdsUsedInCurvesPudorys

        // picked barvy
        val pickedMain = Color(0xFF00EAA0)
        val pickedGlow = pickedMain.copy(alpha = 0.18f)
        val pickedRing1 = Color(0xFFFFFFFF).copy(alpha = 0.85f)
        val pickedRing2 = pickedMain.copy(alpha = 0.85f)

        val crossColor = when {
            isPickedFirstForKotoLine -> pickedMain
            isPickedForAnyCurve      -> pickedMain
            else -> point.color
        }

        val size = when {
            isPickedFirstForKotoLine -> baseSize * 1.2f
            isPickedForAnyCurve -> baseSize* 1.15f
            isInCurve -> baseSize / 1.5f
            else -> baseSize+ sizeFromWidth
        }

        val lineWidth = point.width * POINT_STROKE_WEIGHT*state.scale

        /* ===============================
           HALO / RING
           =============================== */

        if (isPickedFirstForKotoLine || isPickedForAnyCurve) {
            val ringRadius = size * 1.55f
            val haloRadius = ringRadius * 1.25f

            drawCircle(
                color = pickedGlow,
                radius = haloRadius,
                center = center
            )

            drawCircle(
                color = pickedRing1,
                radius = ringRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (lineWidth * 1.2f).coerceAtLeast(strokePx(1.6f, pxPerPt))
                )
            )

            drawCircle(
                color = pickedRing2,
                radius = ringRadius * 1.12f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (lineWidth * 0.9f).coerceAtLeast(strokePx(1.2f, pxPerPt))
                )
            )

        } else if (highlightClickPointsMode) {
            val ringRadius = size * 1.55f
            val haloRadius = ringRadius * 1.25f

            drawCircle(
                color = EligiblePointGlow,
                radius = haloRadius,
                center = center
            )
            drawCircle(
                color = EligiblePointRing,
                radius = ringRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (lineWidth * 0.9f).coerceAtLeast(strokePx(1.2f, pxPerPt))
                )
            )
        }

        /* ===============================
           KŘÍŽEK
           =============================== */

        val pointDrawColor = crossColor

        if (isSelected) {
            drawLine(
                state.selectedHaloColor,
                Offset(screenX - size, screenY),
                Offset(screenX + size, screenY),
                lineWidth + SELECTION_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
            drawLine(
                state.selectedHaloColor,
                Offset(screenX, screenY - size),
                Offset(screenX, screenY + size),
                lineWidth + SELECTION_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
        } else if (isHovered) {
            drawLine(
                state.hoverHaloColor,
                Offset(screenX - size, screenY),
                Offset(screenX + size, screenY),
                lineWidth + HOVER_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
            drawLine(
                state.hoverHaloColor,
                Offset(screenX, screenY - size),
                Offset(screenX, screenY + size),
                lineWidth + HOVER_HALO_EXTRA_PX/8 * pxPerPt * state.scale,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            pointDrawColor.runtimeDrawColor(),
            Offset(screenX - size, screenY),
            Offset(screenX + size, screenY),
            lineWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            pointDrawColor.runtimeDrawColor(),
            Offset(screenX, screenY - size),
            Offset(screenX, screenY + size),
            lineWidth,
            cap = StrokeCap.Round
        )
    }
}