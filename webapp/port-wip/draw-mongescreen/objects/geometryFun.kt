package draw.mongescreen.objects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import draw.mongescreen.lineStyleDashPathEffectPx
import draw.mongescreen.objects.axo.conics.ConicDegeneracyState
import model.ConicCoeffs
import model.LineStyle
import model.classes.PlaneEquation
import utils.toScreenOld
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

fun sinhF(x: Float): Float {
    val ex = exp(x.toDouble())
    val enx = exp((-x).toDouble())
    return ((ex - enx) / 2.0).toFloat()
}

fun coshF(x: Float): Float {
    val ex = exp(x.toDouble())
    val enx = exp((-x).toDouble())
    return ((ex + enx) / 2.0).toFloat()
}

fun acoshF(x: Float): Float {
    val safeX = x.coerceAtLeast(1f)
    return ln((safeX + sqrt(safeX * safeX - 1f)).toDouble()).toFloat()
}
fun Offset.toScreenAxoLocal(
    scale: Float,
    canvasOffset: Offset,
    axoOrigin: Offset
): Offset {
    return (axoOrigin + this).toScreenOld(scale, canvasOffset)
}
fun toModelPlaneEquation(eq: draw.mongescreen.previews.conicsarcs.PlaneEquation): PlaneEquation =
    PlaneEquation(eq.a, eq.b, eq.c, eq.d)
fun invS(a: Float, b: Float, c: Float): Triple<Float, Float, Float>? {
    // inverze S = [[a, b/2], [b/2, c]]
    val bh = b * 0.5f
    val det = a*c - bh*bh
    if (abs(det) < 1e-12f) return null
    val ia =  c / det
    val ib = -bh / det
    val ic =  a / det
    return Triple(ia, ib, ic) // S^{-1} = [[ia, ib],[ib, ic]]
}
fun Float.isFiniteF() = this.isFinite()
fun conicDegeneracy(conic: model.classes.ConicSection2D): ConicDegeneracyState? = when (conic) {
    is model.classes.ConicSectionPudorys -> ConicDegeneracyState(conic.isDegenerate, conic.isLineDegenerate, conic.degenerateDir)
    is model.classes.ConicSectionNarys -> ConicDegeneracyState(conic.isDegenerate, conic.isLineDegenerate, conic.degenerateDir)
    is model.classes.ConicSectionBokorys -> ConicDegeneracyState(
        conic.isDegenerate,
        conic.isLineDegenerate,
        conic.degenerateDir
    )
    is model.classes.ConicSectionAxo -> ConicDegeneracyState(conic.isDegenerate, conic.isLineDegenerate, conic.degenerateDir)
}
fun pathEffectFor(lineStyle: LineStyle, scale: Float = 1f): PathEffect? =
    lineStyleDashPathEffectPx(lineStyle, scale = scale)
fun projectToSegmentCarrier(point: Offset, carrierA: Offset, carrierB: Offset): Offset {
    val ab = carrierB - carrierA
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 < ELLIPSE_EPS * ELLIPSE_EPS) return carrierA
    val ap = point - carrierA
    val t = ((ap.x * ab.x + ap.y * ab.y) / len2).coerceIn(0f, 1f)
    return carrierA + ab * t

}
/** Vrátí dva "nejzazší" XY body ve směru eUnit (pokud existují). null ⇒ degeneruje na přímku. */
fun extremeEndsXY(coeff: ConicCoeffs, eUnit: Offset): Pair<Offset, Offset>? {
    val c0 = conicCenterXY(coeff) ?: return null
    val (a,b,c,_,_,_) = coeff
    val inv = invS(a,b,c) ?: return null
    val (ia, ib, ic) = inv

    val ex = eUnit.x; val ey = eUnit.y
    val s = ia*ex*ex + 2f*ib*ex*ey + ic*ey*ey
    if (abs(s) < 1e-12f) return null

    val f0 = f0Shifted(coeff)
    val rad = -f0 / s
    if (rad <= 1e-12f) return null

    val k = kotlin.math.sqrt(rad)
    val vx = (ia*ex + ib*ey) * k
    val vy = (ib*ex + ic*ey) * k

    val pPlus  = Offset(c0.x + vx, c0.y + vy)
    val pMinus = Offset(c0.x - vx, c0.y - vy)
    return pPlus to pMinus
}
fun conicCenterXY(coeff: ConicCoeffs): Offset? {
    val (a,b,c,d,e,_) = coeff
    val inv = invS(a,b,c) ?: return null
    val (ia, ib, ic) = inv
    // c0 = -1/2 S^{-1} d
    val cx = -0.5f * (ia*d + ib*e)
    val cy = -0.5f * (ib*d + ic*e)
    return Offset(cx, cy)
}

private fun f0Shifted(coeff: ConicCoeffs): Float {
    val (a,b,c,d,e,f) = coeff
    val inv = invS(a,b,c) ?: return f
    val (ia, ib, ic) = inv
    // f0 = f - 1/4 * d^T S^{-1} d
    val quad = ia*d*d + 2f*ib*d*e + ic*e*e
    return f - 0.25f*quad
}
fun strokePx(base: Float, pxPerPt: Float, extra: Float = 0f): Float {
    return (base + extra) * pxPerPt
}