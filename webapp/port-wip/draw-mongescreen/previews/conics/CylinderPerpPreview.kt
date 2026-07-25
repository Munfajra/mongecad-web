package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.orth.conics.drawEllipseFromDiameters
import model.*
import monge.input.ConicArcs.single.getLogicalCursorNarys
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.conixections.conjugateDiameterInputFromRadii
import monge.input.quadrics.cylindricalsurface.computePerpCylinderT
import monge.input.quadrics.cylindricalsurface.computePerpCylinderTAxo
import monge.input.selection.CylinderPhase
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun DrawScope.drawPerpCylinderPreview(state: MongeState, snappedPointLogical: Offset?) {
    if (state.drawobjects != Mongeobjects.CYLINDER) return
    if (state.cylinderPhase != CylinderPhase.PICK_CENTER_PERP) return

    val normal = state.perpCylinderBaseNormal ?: return
    val baseCenter = state.perpCylinderBaseCenter3D ?: return
    val baseU = state.perpCylinderBaseU ?: return
    val baseV = state.perpCylinderBaseV ?: return

    if (state.projectionMode == ProjectionMode.AXO) {
        val basis = state.basis ?: return
        val cursor = snappedPointLogical
            ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
        val t = computePerpCylinderTAxo(cursor, baseCenter, normal, basis) ?: return
        val upperCenter = Offset3D(
            baseCenter.x + normal.x * t,
            baseCenter.y + normal.y * t,
            baseCenter.z + normal.z * t
        )

        val S = 64
        val twoPi = (2.0 * kotlin.math.PI).toFloat()
        val lower3D = ArrayList<Offset3D>(S)
        val upper3D = ArrayList<Offset3D>(S)
        for (i in 0 until S) {
            val angle = twoPi * i / S
            val offset3D = Offset3D(
                baseU.x * cos(angle) + baseV.x * sin(angle),
                baseU.y * cos(angle) + baseV.y * sin(angle),
                baseU.z * cos(angle) + baseV.z * sin(angle)
            )
            lower3D += baseCenter + offset3D
            upper3D += upperCenter + offset3D
        }

        drawPerpCylinderInView(this, state, upperCenter, baseU, baseV, lower3D, upper3D, normal) { p ->
            basis.origin + model.classes.projectPoint3DToAxoLocal(p, basis)
        }
        return
    }

    val cursor = when (state.mongeMode) {
        DrawingModeMonge.NARYS -> getLogicalCursorNarys(
            snappedPointLogical, state.cursorPosition,
            state.canvasOffset, state.scale,
            state.canvasWidth, state.canvasHeight,
            state.xAxisDirection
        )
        DrawingModeMonge.PUDORYS -> getLogicalCursor(
            snappedPointLogical, state.cursorPosition,
            state.canvasOffset, state.scale,
            state.canvasWidth, state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
    }

    val t = computePerpCylinderT(state.mongeMode, cursor, baseCenter, normal) ?: return
    val upperCenter = Offset3D(
        baseCenter.x + normal.x * t,
        baseCenter.y + normal.y * t,
        baseCenter.z + normal.z * t
    )

    val S = 64
    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    val lower3D = ArrayList<Offset3D>(S)
    val upper3D = ArrayList<Offset3D>(S)
    for (i in 0 until S) {
        val angle = twoPi * i / S
        val cosA = cos(angle)
        val sinA = sin(angle)
        val offset3D = Offset3D(
            baseU.x * cosA + baseV.x * sinA,
            baseU.y * cosA + baseV.y * sinA,
            baseU.z * cosA + baseV.z * sinA
        )
        lower3D += baseCenter + offset3D
        upper3D += upperCenter + offset3D
    }

    drawPerpCylinderInView(this, state, upperCenter, baseU, baseV, lower3D, upper3D, normal) { p -> Offset(p.x, p.y) }
    drawPerpCylinderInView(this, state, upperCenter, baseU, baseV, lower3D, upper3D, normal) { p -> Offset(p.x, -p.z) }
}

private fun drawPerpCylinderInView(
    scope: DrawScope, state: MongeState,
    upperCenter: Offset3D, baseU: Offset3D, baseV: Offset3D,
    lower3D: List<Offset3D>, upper3D: List<Offset3D>,
    normal: Offset3D,
    proj: (Offset3D) -> Offset
) {
    val previewColor = Color.Gray.copy(alpha = 0.5f)
    val previewWidth = 1.5f
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)

    val r1UpperP = proj(upperCenter + baseU)
    val r2UpperP = proj(upperCenter + baseV)
    val upperCenterP = proj(upperCenter)

    val inputs = conjugateDiameterInputFromRadii(upperCenterP, r1UpperP, r2UpperP)

    scope.drawEllipseFromDiameters(
        p1 = inputs.first, p2 = inputs.second, p3 = inputs.third,
        scale = state.scale, canvasOffset = state.canvasOffset,
        color = previewColor, strokeWidth = previewWidth,
        lineStyle = LineStyle.Dashed
    )

    val normalProj = proj(normal) - proj(Offset3D(0f, 0f, 0f))
    val perpDir = Offset(-normalProj.y, normalProj.x)
    val perpLen = sqrt(perpDir.x * perpDir.x + perpDir.y * perpDir.y)
    if (perpLen < 1e-6f) return
    val perpNorm = Offset(perpDir.x / perpLen, perpDir.y / perpLen)

    val lowerSamples = lower3D.map(proj)
    val upperSamples = upper3D.map(proj)

    var iMin = 0; var iMax = 0
    var minVal = Float.POSITIVE_INFINITY; var maxVal = Float.NEGATIVE_INFINITY
    for (i in lowerSamples.indices) {
        val s = lowerSamples[i].x * perpNorm.x + lowerSamples[i].y * perpNorm.y
        if (s < minVal) { minVal = s; iMin = i }
        if (s > maxVal) { maxVal = s; iMax = i }
    }

    fun screenPt(p: Offset) = p.toScreenOld(state.scale, state.canvasOffset)

    scope.drawLine(previewColor, screenPt(lowerSamples[iMin]), screenPt(upperSamples[iMin]), previewWidth, pathEffect = dashEffect)
    scope.drawLine(previewColor, screenPt(lowerSamples[iMax]), screenPt(upperSamples[iMax]), previewWidth, pathEffect = dashEffect)
}
