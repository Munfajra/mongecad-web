package draw.mongescreen.objects.orth.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.lineStyleDashPathEffectPx
import draw.mongescreen.objects.coshF
import draw.mongescreen.objects.pathEffectFor
import draw.mongescreen.objects.sinhF
import model.LineStyle
import model.Offset3D
import model.runtimeDrawColor
import utils.dot
import utils.normalize
import utils.toScreenOld
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sqrt


/**
 * Vykreslí degenerovaný průmět hyperboly navzorkováním skutečné 3D hyperboly a
 * promítnutím každého bodu do pohledu. Degenerovaný průmět (rovina kolmá k
 * průmětně) tak automaticky vyjde buď jako dvě polopřímky s mezerou (vrcholy se
 * promítnou do různých bodů), nebo jako jedna přímka (vrcholy splynou) — což
 * koeficienty zkolabovaného 2D průmětu už nedokáží rozlišit.
 *
 * [viewDrop] mapuje 3D bod do 2D souřadnic pohledu (nárys: (x,z), bokorys: (y,z),
 * axo: projectPoint3DToAxoLocal), [project] mapuje 2D pohled na obrazovku.
 * Funguje jen pro (vycentrovanou) kanonickou matici z canonizeHyperbolaFrame;
 * jinak vrací false a volající použije původní fallback.
 */
// Sdílený 3D rám degenerované hyperboly (střed + hlavní/vedlejší osa a poloosy),
// odvozený z lokální matice rodiče. Obecný – nevyžaduje kanonickou (B≈0) matici.
class DegHyperbola3DFrame(
    val center3D: Offset3D,
    val tDir: Offset3D, val aSemi: Float,
    val cjDir: Offset3D, val bSemi: Float
) {
    companion object {
        const val U_MAX = 6f
        const val STEPS = 700
    }
    fun point(branchSign: Float, t: Float): Offset3D =
        center3D + tDir * (branchSign * aSemi * coshF(t)) + cjDir * (bSemi * sinhF(t))
}
fun degenerateHyperbola3DFrame(conic3D: model.classes.ConicSection3D): DegHyperbola3DFrame? {
    val cs = model.classes.Matrix3x3.toCoefficients(conic3D.matrix)
    val A = cs[0]; val B = cs[1]; val C = cs[2]; val D = cs[3]; val E = cs[4]; val F = cs[5]
    val scaleC = max(max(abs(A), abs(B)), abs(C))
    if (!scaleC.isFinite() || scaleC < 1e-20f) return null

    // střed (obecně, nevyžaduje kanonickou/diagonální matici)
    val det = 4f * A * C - B * B
    if (abs(det) < 1e-9f * scaleC * scaleC) return null
    val cx = (B * E - 2f * C * D) / det
    val cy = (B * D - 2f * A * E) / det
    val fp = A * cx * cx + B * cx * cy + C * cy * cy + D * cx + E * cy + F
    if (abs(fp) < 1e-9f * scaleC) return null

    // hlavní směry = vlastní vektory kvadratické části [[A, B/2],[B/2, C]]
    val q12 = B * 0.5f
    val tr = A + C
    val disc = sqrt((A - C) * (A - C) + 4f * q12 * q12)
    val l1 = (tr + disc) * 0.5f
    val l2 = (tr - disc) * 0.5f
    // hyperbola ⇒ vlastní čísla mají opačná znaménka; jinak nech fallback
    if (l1 * l2 >= -1e-9f * scaleC * scaleC) return null

    fun eigenLocal(lambda: Float): Offset {
        val raw = if (abs(q12) > 1e-12f || abs(A - lambda) > 1e-12f) Offset(q12, lambda - A) else Offset(1f, 0f)
        val len = raw.getDistance()
        return if (len < 1e-12f) Offset(1f, 0f) else raw / len
    }

    val sa1 = -fp / l1 // poloosa² podél e(l1); u hyperboly je právě jedna kladná
    val tLambda: Float; val tSemiSq: Float; val cjLambda: Float; val cjSemiSq: Float
    if (sa1 > 0f) {
        tLambda = l1; tSemiSq = sa1; cjLambda = l2; cjSemiSq = -fp / l2
    } else {
        tLambda = l2; tSemiSq = -fp / l2; cjLambda = l1; cjSemiSq = sa1
    }
    if (tSemiSq <= 0f || !tSemiSq.isFinite()) return null

    val tLocal = eigenLocal(tLambda)
    val cjLocal = eigenLocal(cjLambda)
    return DegHyperbola3DFrame(
        center3D = conic3D.p0 + conic3D.u * cx + conic3D.v * cy,
        tDir = conic3D.u * tLocal.x + conic3D.v * tLocal.y,
        aSemi = sqrt(tSemiSq),
        cjDir = conic3D.u * cjLocal.x + conic3D.v * cjLocal.y,
        bSemi = sqrt(abs(cjSemiSq))
    )
}

fun DrawScope.drawDegenerateHyperbolaFrom3D(
    conic3D: model.classes.ConicSection3D,
    viewDrop: (Offset3D) -> Offset,
    project: (Offset) -> Offset,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    dashScale: Float = 1f
): Boolean {
    val frame = degenerateHyperbola3DFrame(conic3D) ?: return false
    val effect = pathEffectFor(lineStyle, dashScale)

    fun drawBranch(branchSign: Float) {
        val path = Path()
        var started = false
        for (i in 0..DegHyperbola3DFrame.STEPS) {
            val t = -DegHyperbola3DFrame.U_MAX + (2f * DegHyperbola3DFrame.U_MAX * i / DegHyperbola3DFrame.STEPS)
            val p = project(viewDrop(frame.point(branchSign, t)))
            if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
        }
        if (started) drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, pathEffect = effect, cap = StrokeCap.Round)
        )
    }
    drawBranch(+1f)
    drawBranch(-1f)
    return true
}
fun DrawScope.drawConicHyperbolaPudorys(
    center: Offset,
    asymptote1: Offset,
    vertex: Offset,
    canvasOffset: Offset,
    scale: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    axis: Offset,
) {
    val v1 = asymptote1.normalize()
    val vc = vertex - center
    val vcLen = vc.getDistance()
    val finalAxis = if (vcLen >= 1e-6f) vc / vcLen else {
        val axisRaw = axis.normalize()
        if (vc.dot(axisRaw) < 0f) -axisRaw else axisRaw
    }
    val otherDir = Offset(-finalAxis.y, finalAxis.x)

    val a = if (vcLen >= 1e-6f) vcLen else vc.dot(finalAxis).absoluteValue
    val v1x = v1.dot(finalAxis)
    val v1y = v1.dot(otherDir)
    val slope = v1y / v1x
    val b = a * abs(slope)
    if (a < 1e-6f || b < 1e-6f) return

    val uMax = 3.5f
    val steps = 500

    fun drawBranch(branchSign: Float) {
        val points = mutableListOf<Offset>()
        for (i in 0..steps) {
            val u = -uMax + (2f * uMax * i / steps)
            val ch = kotlin.math.cosh(u.toDouble()).toFloat()
            val sh = kotlin.math.sinh(u.toDouble()).toFloat()
            val x = branchSign * a * ch
            val y = b * sh
            val pt = center + finalAxis * x + otherDir * y
            points.add(pt.toScreenOld(scale, canvasOffset))
        }
        drawPathFromPoints(points, color, strokeWidth, lineStyle, scale = scale)
    }

    drawBranch(+1f)
    drawBranch(-1f)
}





fun DrawScope.drawPathFromPoints(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    style: LineStyle,
    phase: Float = 0f,
    scale: Float = 1f
) {
    if (points.size < 2) return

    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }

    val pathEffect = lineStyleDashPathEffectPx(style, phase = phase, scale = scale)

    drawPath(
        path = path,
        color = color.runtimeDrawColor(),
        style = Stroke(
            width = strokeWidth,
            pathEffect = pathEffect,
            cap = if (style == LineStyle.Solid) StrokeCap.Butt else StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

fun DrawScope.drawConicHyperbolaNarys(
    center: Offset,
    asymptote1: Offset,
    vertex: Offset,
    canvasOffset: Offset,
    scale: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: LineStyle,
    axis: Offset,
) {
    val v1 = asymptote1.normalize()
    val vc = vertex - center
    val vcLen = vc.getDistance()
    val finalAxis = if (vcLen >= 1e-6f) vc / vcLen else {
        val axisRaw = axis.normalize()
        if (vc.dot(axisRaw) < 0f) -axisRaw else axisRaw
    }
    val otherDir = Offset(-finalAxis.y, finalAxis.x)

    val a = if (vcLen >= 1e-6f) vcLen else vc.dot(finalAxis).absoluteValue
    val v1x = v1.dot(finalAxis)
    val v1y = v1.dot(otherDir)
    val slope = v1y / v1x
    val b = a * abs(slope)
    if (a < 1e-6f || b < 1e-6f) return

    val uMax = 3.5f
    val steps = 500

    fun drawBranch(branchSign: Float) {
        val points = mutableListOf<Offset>()
        for (i in 0..steps) {
            val u = -uMax + (2f * uMax * i / steps)
            val ch = kotlin.math.cosh(u.toDouble()).toFloat()
            val sh = kotlin.math.sinh(u.toDouble()).toFloat()
            val x = branchSign * a * ch
            val y = b * sh
            val pt = center + finalAxis * x + otherDir * y
            points.add(Offset(pt.x, -pt.y).toScreenOld(scale, canvasOffset))
        }
        drawPathFromPoints(points, color, strokeWidth, lineStyle, scale = scale)
    }

    drawBranch(+1f)
    drawBranch(-1f)
}