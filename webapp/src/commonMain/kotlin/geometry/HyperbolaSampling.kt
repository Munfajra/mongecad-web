package geometry

import geometry.conics.cosh
import geometry.conics.sinh
import model.Offset3D
import model.classes.ConicSection3D
import model.normalize
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Vzorkování větve hyperboly ve 3D.
 *
 * Na desktopu bydlí v `monge/input/intersections/ops/ConicClipping.kt`, ale
 * s průniky nesouvisí – kreslení ji potřebuje pro degenerovaný průmět
 * hyperboly (rovina hyperboly se promítá do přímky).
 */
fun orthoBasis(conic: ConicSection3D): Pair<Offset3D, Offset3D> {
    val uN = conic.u.normalize()
    val vN = (conic.v - uN * (conic.v dot uN)).normalize()
    return uN to vN
}

fun asinhF(x: Float): Float = ln(x + sqrt(x * x + 1f))

fun hyperbolaPointAt(conic: ConicSection3D, t: Float, sign: Float, uN: Offset3D, vN: Offset3D, a: Float, b: Float): Offset3D =
    conic.p0 + uN * (sign * a * cosh(t)) + vN * (b * sinh(t))

fun sampleIntersectionHyperbolaBranchArc3D(
    parent: ConicSection3D,
    branchEnds: Pair<Offset3D, Offset3D>,
    steps: Int = 240
): List<Offset3D> {
    val a = parent.a ?: return emptyList()
    val b = parent.b ?: return emptyList()
    val (uN, vN) = orthoBasis(parent)
    val (a3, b3) = branchEnds
    val t1 = asinhF(((a3 - parent.p0) dot vN) / b)
    val t2 = asinhF(((b3 - parent.p0) dot vN) / b)
    if (!t1.isFinite() || !t2.isFinite()) return emptyList()
    val sign = if (((a3 - parent.p0) dot uN) >= 0f) 1f else -1f
    return List(steps + 1) { i ->
        val t = t1 + (t2 - t1) * i / steps
        hyperbolaPointAt(parent, t, sign, uN, vN, a, b)
    }
}

