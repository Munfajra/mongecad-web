package draw.mongescreen.objects.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.objects.*
import draw.mongescreen.conicarcs.HyperbolaBasis
import draw.mongescreen.conicarcs.projectToHyperbola
import model.LineStyle
import model.classes.ConicInputHyperbolaBokorys
import model.classes.ConicInputHyperbolaNarys
import model.classes.ConicInputHyperbolaPudorys
import model.runtimeDrawColor
import state.snapMonge.computeIntersection
import utils.dot
import utils.normalize
import kotlin.math.*


data class ProjectedHyperbolaInput(
    val center: Offset,
    val asymptote1: Offset,
    val vertex: Offset,
    val axis: Offset
)

/**
 * Draws a hyperbola branch arc projected through a lambda (used for axo projections).
 * basis, A, B are in the conic's own 2D coordinate space.
 */
fun DrawScope.drawHyperbolaBranchArcProjected(
    basis: HyperbolaBasis,
    A: Offset,
    B: Offset,
    forcedBranchSX: Int,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    du: Float = 0.05f,
    dashScale: Float = 1f
) {
    val pA = projectToHyperbola(basis, A, forcedBranchSX) ?: return
    val pB = projectToHyperbola(basis, B, forcedBranchSX) ?: return

    strokeHyperbolaParamArc(
        basis = basis, forcedBranchSX = forcedBranchSX,
        u1In = pA.u, u2In = pB.u,
        style = lineStyle, project = project,
        color = color, strokeWidth = strokeWidth,
        du = du, dashScale = dashScale
    )
}

/**
 * Vykreslí jeden úsek jedné větve hyperboly zadaný přímo nativními parametry
 * [u1]..[u2] (cosh/sinh parametr). Jádro sdílené obloukem i po částech
 * stylovanou hyperbolou. [uMax] ořezává extent (default 6; krajní hranice úseků
 * mohou být uživatelský ořez, proto parametrizováno).
 */
fun DrawScope.strokeHyperbolaParamArc(
    basis: HyperbolaBasis,
    forcedBranchSX: Int,
    u1In: Float, u2In: Float,
    style: LineStyle,
    project: (Offset) -> Offset,
    color: Color, strokeWidth: Float,
    du: Float = 0.05f, dashScale: Float = 1f,
    uMax: Float = 6f
) {
    fun clampU(u: Float) = u.coerceIn(-uMax, uMax)

    val u1 = clampU(u1In)
    var u2 = clampU(u2In)
    if (!u1.isFinite() || !u2.isFinite()) return
    if (abs(u2 - u1) < 1e-5f) u2 = u1 + 1e-3f

    val dir = if (u2 >= u1) +1f else -1f
    val L = abs(u2 - u1)
    val steps = max(2, ceil(L / du).toInt())

    val (C, ex, ey, a, b) = basis

    fun pointAt(u: Float): Offset {
        val ch = coshF(u); val sh = sinhF(u)
        val x = forcedBranchSX * a * ch
        val y = b * sh
        return C + ex * x + ey * y
    }

    val path = Path()

    run {
        val projected = project(pointAt(u1))
        if (!projected.x.isFinite() || !projected.y.isFinite()) return
        path.moveTo(projected.x, projected.y)
    }
    for (i in 1 until steps) {
        val u = clampU(u1 + dir * (i * (L / steps)))
        val projected = project(pointAt(u))
        if (!projected.x.isFinite() || !projected.y.isFinite()) continue
        path.lineTo(projected.x, projected.y)
    }
    run {
        val projected = project(pointAt(u2))
        if (!projected.x.isFinite() || !projected.y.isFinite()) return
        path.lineTo(projected.x, projected.y)
    }

    drawPath(path, color.runtimeDrawColor(), style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(style, dashScale), cap = StrokeCap.Round))
}
fun DrawScope.drawDegenerateHyperbolaAxoLocal(
    coeffs: ConicCoefficients2D,
    fallbackDir: Offset?,
    scale: Float,
    canvasOffset: Offset,
    axoOrigin: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val span = (max(size.width, size.height) / scale).coerceAtLeast(1f) * 2f
    val effect = pathEffectFor(lineStyle, scale)
    for (line in degenerateConicLines(coeffs, fallbackDir)) {
        val dir = line.direction.normalize()
        if (dir == Offset.Zero) continue
        drawLine(
            color = color.runtimeDrawColor(),
            start = (line.origin - dir * span).toScreenAxoLocal(scale, canvasOffset, axoOrigin),
            end = (line.origin + dir * span).toScreenAxoLocal(scale, canvasOffset, axoOrigin),
            strokeWidth = strokeWidth,
            pathEffect = effect,
            cap = StrokeCap.Round
        )
    }
}
fun DrawScope.drawHyperbolaAxoLocal(
    center: Offset,
    asymptote1: Offset,
    vertex: Offset,
    axis: Offset,
    scale: Float,
    canvasOffset: Offset,
    axoOrigin: Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle
) {
    val v1 = asymptote1.normalize()
    val vc = vertex - center
    val vcLen = vc.getDistance()
    val finalAxis = if (vcLen >= ELLIPSE_EPS) vc / vcLen else {
        val axisRaw = axis.normalize()
        if (vc.dot(axisRaw) < 0f) -axisRaw else axisRaw
    }
    val otherDir = Offset(-finalAxis.y, finalAxis.x)

    val a = if (vcLen >= ELLIPSE_EPS) vcLen else vc.dot(finalAxis).absoluteValue
    val v1x = v1.dot(finalAxis)
    val v1y = v1.dot(otherDir)
    if (a < ELLIPSE_EPS || abs(v1x) < ELLIPSE_EPS) return
    val b = (a * abs(v1y / v1x)).coerceAtLeast(ELLIPSE_EPS)

    fun point(branchSign: Float, u: Float): Offset {
        return center +
                finalAxis * (branchSign * a * coshF(u)) +
                otherDir * (b * sinhF(u))
    }

    fun drawBranch(branchSign: Float, uMax: Float) {
        val path = Path()
        var started = false
        val steps = (uMax * 180f).toInt().coerceIn(320, 1600)
        for (i in 0..steps) {
            val u = -uMax + (2f * uMax * i / steps)
            val local = point(branchSign, u)
            val screen = local.toScreenAxoLocal(scale, canvasOffset, axoOrigin)
            if (!started) {
                path.moveTo(screen.x, screen.y)
                started = true
            } else {
                path.lineTo(screen.x, screen.y)
            }
        }
        if (started) {
            drawPath(
                path = path,
                color = color.runtimeDrawColor(),
                style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(lineStyle, scale), cap = StrokeCap.Round)
            )
        }
    }

    val logicalSpan = (max(size.width, size.height) / scale).coerceAtLeast(1f)
    val maxT = (logicalSpan / a).coerceIn(2.5f, 50f)
    val uMax = acoshF(maxT).coerceIn(1.5f, 5f)
    drawBranch(+1f, uMax)
    drawBranch(-1f, uMax)
}
fun projectedHyperbolaInput(input: Any?): ProjectedHyperbolaInput? {
    return when (input) {
        is ConicInputHyperbolaPudorys -> {
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.y),
                input.line1.direction,
                Offset(input.line2.point.x, input.line2.point.y),
                input.line2.direction
            ) ?: input.vertex
            ProjectedHyperbolaInput(center, input.line1.direction, input.vertex, input.axis)
        }

        is ConicInputHyperbolaNarys -> {
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.z),
                input.line1.direction,
                Offset(input.line2.point.x, input.line2.point.z),
                input.line2.direction
            ) ?: input.vertex
            ProjectedHyperbolaInput(
                center = Offset(center.x, -center.y),
                asymptote1 = Offset(input.line1.direction.x, -input.line1.direction.y),
                vertex = Offset(input.vertex.x, -input.vertex.y),
                axis = Offset(input.axis.x, -input.axis.y)
            )
        }

        is ConicInputHyperbolaBokorys -> {
            val center = computeIntersection(
                Offset(input.line1.point.y, input.line1.point.z),
                input.line1.direction,
                Offset(input.line2.point.y, input.line2.point.z),
                input.line2.direction
            ) ?: input.vertex
            ProjectedHyperbolaInput(center, input.line1.direction, input.vertex, input.axis)
        }

        else -> null
    }
}
fun DrawScope.drawDegenerateHyperbolaProjected(
    coeffs: ConicCoefficients2D,
    fallbackDir: Offset?,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    dashScale: Float = 1f
) {
    val span = max(size.width, size.height).coerceAtLeast(1f) * 2f
    val effect = pathEffectFor(lineStyle, dashScale)
    for (line in degenerateConicLines(coeffs, fallbackDir)) {
        val dir = line.direction.normalize()
        if (dir == Offset.Zero) continue
        drawLine(
            color = color.runtimeDrawColor(),
            start = project(line.origin - dir * span),
            end = project(line.origin + dir * span),
            strokeWidth = strokeWidth,
            pathEffect = effect,
            cap = StrokeCap.Round
        )
    }
}
fun DrawScope.drawHyperbolaProjected(
    center: Offset,
    asymptote1: Offset,
    vertex: Offset,
    axis: Offset,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    dashScale: Float = 1f
) {
    val v1 = asymptote1.normalize()
    if (v1 == Offset.Zero) return
    val vc = vertex - center
    val vcLen = vc.getDistance()
    val finalAxis = if (vcLen >= ELLIPSE_EPS) vc / vcLen else {
        val axisRaw = axis.normalize()
        if (axisRaw == Offset.Zero) return
        if (vc.dot(axisRaw) < 0f) -axisRaw else axisRaw
    }
    val otherDir = Offset(-finalAxis.y, finalAxis.x)
    val a = if (vcLen >= ELLIPSE_EPS) vcLen else vc.dot(finalAxis).absoluteValue
    val v1x = v1.dot(finalAxis)
    val v1y = v1.dot(otherDir)
    if (a < ELLIPSE_EPS || abs(v1x) < ELLIPSE_EPS) return
    val b = (a * abs(v1y / v1x)).coerceAtLeast(ELLIPSE_EPS)

    fun point(branchSign: Float, u: Float): Offset {
        return center +
                finalAxis * (branchSign * a * coshF(u)) +
                otherDir * (b * sinhF(u))
    }

    fun drawBranch(branchSign: Float, uMax: Float) {
        val path = Path()
        var started = false
        val steps = (uMax * 180f).toInt().coerceIn(320, 1600)
        for (i in 0..steps) {
            val u = -uMax + (2f * uMax * i / steps)
            val projected = project(point(branchSign, u))
            if (!started) {
                path.moveTo(projected.x, projected.y)
                started = true
            } else {
                path.lineTo(projected.x, projected.y)
            }
        }
        if (started) {
            drawPath(
                path = path,
                color = color.runtimeDrawColor(),
                style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(lineStyle, dashScale), cap = StrokeCap.Round)
            )
        }
    }

    val logicalSpan = max(size.width, size.height).coerceAtLeast(1f)
    val maxT = (logicalSpan / a).coerceIn(2.5f, 50f)
    val uMax = acoshF(maxT).coerceIn(1.5f, 5f)
    drawBranch(+1f, uMax)
    drawBranch(-1f, uMax)
}

data class ConicDegeneracyState(
    val isDegenerate: Boolean,
    val isLine: Boolean,
    val dir: Offset?
)
fun degenerateConicLines(coeffs: ConicCoefficients2D, fallbackDir: Offset?): List<DegenerateConicLine> {
    val q11 = coeffs.a
    val q12 = coeffs.b * 0.5f
    val q22 = coeffs.c
    val trace = q11 + q22
    val diff = q11 - q22
    val disc = sqrt(diff * diff + 4f * q12 * q12)
    val lambda1 = (trace + disc) * 0.5f
    val lambda2 = (trace - disc) * 0.5f

    fun eigen(lambda: Float): Offset {
        val raw = if (abs(q12) > ELLIPSE_EPS || abs(q11 - lambda) > ELLIPSE_EPS) {
            Offset(q12, lambda - q11)
        } else {
            Offset(1f, 0f)
        }
        return raw.normalize().takeIf { it != Offset.Zero } ?: Offset(1f, 0f)
    }

    val detS = q11 * q22 - q12 * q12
    if (abs(detS) > ELLIPSE_EPS) {
        val center = Offset(
            (q12 * coeffs.e - q22 * coeffs.d) / (2f * detS),
            (q12 * coeffs.d - q11 * coeffs.e) / (2f * detS)
        )
        val dirs = degenerateCentralDirections(q11, coeffs.b, q22)
        if (dirs != null) {
            return listOf(
                DegenerateConicLine(center, dirs.first),
                DegenerateConicLine(center, dirs.second)
            )
        }
    }

    val lambda = if (abs(lambda1) >= abs(lambda2)) lambda1 else lambda2
    if (abs(lambda) > ELLIPSE_EPS) {
        val normal = eigen(lambda)
        val dir = Offset(-normal.y, normal.x)
        val linearAlongDir = coeffs.d * dir.x + coeffs.e * dir.y
        if (abs(linearAlongDir) <= 1e-3f) {
            val p = coeffs.d * normal.x + coeffs.e * normal.y
            val rootDisc = p * p - 4f * lambda * coeffs.f
            if (rootDisc >= -1e-3f) {
                val root = sqrt(rootDisc.coerceAtLeast(0f))
                val s1 = (-p + root) / (2f * lambda)
                val s2 = (-p - root) / (2f * lambda)
                val line1 = DegenerateConicLine(normal * s1, dir)
                val line2 = DegenerateConicLine(normal * s2, dir)
                return if (abs(s1 - s2) <= 1e-3f) listOf(line1) else listOf(line1, line2)
            }
        }
    }

    val dir = fallbackDir?.normalize()?.takeIf { it != Offset.Zero } ?: Offset(1f, 0f)
    return listOf(DegenerateConicLine(Offset.Zero, dir))
}
private fun degenerateCentralDirections(a: Float, b: Float, c: Float): Pair<Offset, Offset>? {
    val q12 = b * 0.5f
    val trace = a + c
    val disc = sqrt((a - c) * (a - c) + 4f * q12 * q12)
    val l1 = (trace + disc) * 0.5f
    val l2 = (trace - disc) * 0.5f
    if (l1 * l2 > ELLIPSE_EPS) return null

    val e1 = if (abs(q12) > ELLIPSE_EPS || abs(a - l1) > ELLIPSE_EPS) {
        Offset(q12, l1 - a).normalize()
    } else {
        Offset(1f, 0f)
    }
    val e2 = Offset(-e1.y, e1.x)
    val scale1 = sqrt(abs(l2)).coerceAtLeast(ELLIPSE_EPS)
    val scale2 = sqrt(abs(l1)).coerceAtLeast(ELLIPSE_EPS)
    val d1 = (e1 * scale1 + e2 * scale2).normalize()
    val d2 = (e1 * scale1 - e2 * scale2).normalize()
    if (d1 == Offset.Zero || d2 == Offset.Zero) return null
    return d1 to d2
}
