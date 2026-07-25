/*
 * Matematika kuželoseček.
 *
 * Na desktopu tenhle kód bydlí v `opengl/model/Conics.kt` vedle GL vykreslování,
 * ale se samotným OpenGL nemá nic společného – je to čistá geometrie, kterou
 * volá 2D kreslení i vstupní vrstva. Pro web je vytažená sem, GL část
 * (drawConicSection, projekce přes FloatBuffer) zůstala na desktopu.
 */
package geometry.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.classes.ConicSection3D
import model.LineStyle
import model.classes.Matrix3x3
import model.Offset3D
import monge.input.ConicArcs.associated.hyperbolaLocalXY
import kotlin.math.*

private const val ELLIPSE_FULL_STEPS = 720
private const val PARABOLA_FULL_STEPS = 1600
private const val HYPERBOLA_FULL_STEPS = 1200
private const val MAX_CONIC_ARC_STEPS = 4096

private fun arcSteps(span: Float, perUnit: Float, minSteps: Int = 96): Int =
    maxOf(minSteps, ceil(abs(span) * perUnit).toInt()).coerceAtMost(MAX_CONIC_ARC_STEPS)

data class EllipseAxes3D(
    val a: Float,
    val b: Float,
    val uRotated: Offset3D,
    val vRotated: Offset3D
)
data class EllipseAxes(val a: Float, val b: Float, val uRotated: Offset, val vRotated: Offset)

fun computeEllipseAxesWithDirectionsStable(M: Matrix3x3): EllipseAxes {
    // KvadratickĂˇ ÄŤĂˇst
    val a = M.m00.toDouble()
    val b = M.m01.toDouble()
    val c = M.m11.toDouble()
    val f = M.m22.toDouble() // POZOR: musĂ­ bĂ˝t po pĹ™esunu do stĹ™edu (D=E=0). Viz bod 3 nĂ­Ĺľe.

    // Ăšhel rotace â€“ stabilnÄ›
    val theta = 0.5 * atan2(2.0 * b, a - c)

    // VlastnĂ­ hodnoty â€“ stabilnÄ›
    val halfTrace = 0.5 * (a + c)
    val r = hypot(0.5 * (a - c), b)
    val lambdaMax = halfTrace + r
    val lambdaMin = halfTrace - r

    // SmÄ›rovĂ© vektory os v 2D (jednotkovĂ©, ortogonĂˇlnĂ­)
    val u1 = Offset(cos(theta).toFloat(), sin(theta).toFloat())
    val u2 = Offset(-u1.y, u1.x)

    // UrÄŤi, kterĂ˝ smÄ›r patĹ™Ă­ kterĂ© Î» (pro jistotu nehĂˇdej, spoÄŤti RayleighĹŻv podĂ­l)
    fun rayleigh(u: Offset): Double =
        (a * u.x * u.x + 2.0 * b * u.x * u.y + c * u.y * u.y)

    val lamU1 = rayleigh(u1)
    val lamU2 = rayleigh(u2)

    val (dirMin, dirMax) =
        if (abs(lamU1 - lambdaMin) < abs(lamU2 - lambdaMin)) u1 to u2 else u2 to u1

    // PolomÄ›ry: r âť 1/sqrt(Î»). Pro elipsu musĂ­ bĂ˝t -f > 0 a Î» > 0.
    val rMajor = sqrt(max(0.0, -f / lambdaMin)) // delĹˇĂ­
    val rMinor = sqrt(max(0.0, -f / lambdaMax)) // kratĹˇĂ­

    return EllipseAxes(
        a = rMajor.toFloat(),
        b = rMinor.toFloat(),
        uRotated = dirMin,  // major osa
        vRotated = dirMax   // minor osa
    )
}
fun computeEllipseAxes3D(conic: ConicSection3D): EllipseAxes3D {
    val axes2D = computeEllipseAxesWithDirectionsStable(conic.matrix)

    // PĹ™evod do 3D a re-ortogonalizace (pro jistotu)
    var u3D = conic.u * axes2D.uRotated.x + conic.v * axes2D.uRotated.y
    var v3D = conic.u * axes2D.vRotated.x + conic.v * axes2D.vRotated.y

    u3D = u3D.normalized()
    v3D = (v3D - u3D * (v3D dot u3D)).normalized()

    return EllipseAxes3D(axes2D.a, axes2D.b, u3D, v3D)
}
enum class ConicType { ELLIPSE, PARABOLA, HYPERBOLA, DEGENERATE }

private fun det3(m: Matrix3x3): Float =
    m.m00 * (m.m11 * m.m22 - m.m12 * m.m21) -
            m.m01 * (m.m10 * m.m22 - m.m12 * m.m20) +
            m.m02 * (m.m10 * m.m21 - m.m11 * m.m20)

fun classifyConicFromMatrix(m: Matrix3x3): ConicType {
    // KvadratickĂˇ ÄŤĂˇst symetrickĂˇ: [a b; b c]
    val a = m.m00
    val b = m.m01
    val c = m.m11

    // RelativnĂ­ ĹˇkĂˇlovĂˇnĂ­ kvĹŻli stabilitÄ›
    val matrixScale = maxOf(
        abs(m.m00), abs(m.m01), abs(m.m02),
        abs(m.m10), abs(m.m11), abs(m.m12),
        abs(m.m20), abs(m.m21), abs(m.m22),
        1e-12f
    )
    val isCenteredDegenerate = abs(m.m22) <= 1e-6f * matrixScale
    val detFull = det3(m) / (matrixScale * matrixScale * matrixScale)
    if (isCenteredDegenerate && abs(detFull) <= 1e-6f) return ConicType.DEGENERATE

    val quadScale = maxOf(abs(a), abs(b), abs(c), 1e-12f)
    val aN = a / quadScale
    val bN = b / quadScale
    val cN = c / quadScale

    val det = aN * cN - bN * bN
    val eps = 1e-6f  // relativnĂ­ tolerance po normalizaci

    return when {
        det < -eps -> ConicType.HYPERBOLA
        abs(det) <= eps -> ConicType.PARABOLA
        else -> ConicType.ELLIPSE
    }
}

fun sampleParametricEllipse(conic: ConicSection3D): List<Offset3D> {
    val axes = computeEllipseAxes3D(conic)

    return List(ELLIPSE_FULL_STEPS + 1) { i ->
        val t = 2f * kotlin.math.PI.toFloat() * i / ELLIPSE_FULL_STEPS
        val x = axes.a * cos(t)
        val y = axes.b * sin(t)
        conic.p0 + axes.uRotated * x + axes.vRotated * y
    }
}

fun sampleParametricParabola(
    conic: ConicSection3D,
    range: Float = 1000f,
    steps: Int = PARABOLA_FULL_STEPS
): List<Offset3D> {
    val a = conic.matrix.m00
    val focalLength = if (abs(a) > 1e-5f) 1f / (4f * a) else 25f // fallback
    val uN = conic.u.normalized()
    val vGS = conic.v - uN * conic.v.dot(uN)
    val vN = vGS.normalized()

    return List(steps + 1) { i ->
        val t = -range + 2f * range * i / steps
        val x = t
        val y = x * x / (4f * focalLength)
        conic.p0 + uN * x + vN * y
    }
}
fun parabolaParamTForPoint3D(conic: ConicSection3D, P3D: Offset3D): Float {
    val uN = conic.u.normalized()
    val r = P3D - conic.p0
    return r.dot(uN) // protoĹľe v sampleru pouĹľĂ­vĂˇĹˇ x=t
}
fun sampleParametricParabolaArc(
    conic: ConicSection3D,
    t1: Float,
    t2: Float,
    stepsHint: Int = 200
): List<Offset3D> {
    // StejnĂˇ ohniskovĂˇ vzdĂˇlenost jako u plnĂ© verze
    val a = conic.matrix.m00
    val focalLength = if (abs(a) > 1e-5f) 1f / (4f * a) else 25f

    // OrtonormalizovanĂ˝ smÄ›r v rovinÄ› (pro jistotu, kdyby u/v nebyly unit)
    val uN = conic.u.normalized()
    val vGS = conic.v - uN * conic.v.dot(uN)
    val vN = vGS.normalized()

    // PoÄŤet krokĹŻ dle dĂ©lky intervalu |t2-t1|
    val span = abs(t2 - t1)
    val steps = maxOf(stepsHint, arcSteps(span, perUnit = 1.0f, minSteps = 96))
    val dt = if (steps > 0) (t2 - t1) / steps else 0f

    return List(steps + 1) { i ->
        val t = t1 + dt * i
        val x = t
        val y = x * x / (4f * focalLength)
        conic.p0 + uN * x + vN * y
    }
}

fun sampleDegenerateConic(conic: ConicSection3D, range: Float = 1000f): List<Offset3D> {
    val q00 = conic.matrix.m00
    val q01 = conic.matrix.m01
    val q11 = conic.matrix.m11
    val quadDet = q00 * q11 - q01 * q01

    if (abs(quadDet) > 1e-6f) {
        val marker = 0.05f
        val u = conic.u.normalized()
        return listOf(conic.p0 - u * marker, conic.p0 + u * marker)
    }

    val theta = 0.5f * atan2(2f * q01, q00 - q11)
    val dirLocal = Offset(cos(theta), sin(theta))
    val normalLocal = Offset(-dirLocal.y, dirLocal.x)
    val qAlongDir = q00 * dirLocal.x * dirLocal.x + 2f * q01 * dirLocal.x * dirLocal.y + q11 * dirLocal.y * dirLocal.y
    val lineDirLocal = if (abs(qAlongDir) < 1e-5f) dirLocal else normalLocal
    val dir3D = (conic.u * lineDirLocal.x + conic.v * lineDirLocal.y).normalized()
    return listOf(conic.p0 - dir3D * range, conic.p0 + dir3D * range)
}



private fun pointOnEllipse3D(conic: ConicSection3D, t: Float): Offset3D {
    val ax = computeEllipseAxes3D(conic)               // a, b, uRotated(3D), vRotated(3D)
    val u = ax.uRotated.normalized()                   // jistota jednotkovĂ© dĂ©lky
    val v = ax.vRotated.normalized()
    return conic.p0 +
            u * (ax.a * cos(t)) +
            v * (ax.b * sin(t))
}

fun sampleParametricEllipseArc(conic: ConicSection3D, t1: Float, t2: Float): List<Offset3D> {
    val span = abs(t2 - t1)
    val steps = arcSteps(span, perUnit = 96f, minSteps = 96)
    val dt = (t2 - t1) / steps
    return List(steps + 1) { i -> pointOnEllipse3D(conic, t1 + dt * i) }
}

fun sampleParametricHyperbolaArc(
    conic: ConicSection3D,
    a: Float,
    b: Float,
    t1: Float,
    t2: Float,
    branchSign: Int,
    steps: Int = 400
): List<Offset3D> {
    val (uN, vN) = orthonormalBasis(conic)
    val s = if (branchSign >= 0) +1f else -1f
    val tStart = min(t1, t2)
    val tEnd   = max(t1, t2)
    val actualSteps = maxOf(steps, arcSteps(tEnd - tStart, perUnit = 160f, minSteps = 128))

    return List(actualSteps + 1) { i ->
        val t = tStart + (tEnd - tStart) * (i / actualSteps.toFloat())
        val x = s * a * cosh(t)
        val y =      b * sinh(t)
        conic.p0 + uN * x + vN * y
    }
}
fun sampleHyperbolaFromEndsIfAvailable(
    conic: ConicSection3D,
    a: Float,
    b: Float,
    ends: Pair<Offset3D, Offset3D>?,   // koncovĂ© body vÄ›tve (svÄ›tovĂ© souĹ™adnice)
    fallbackArc: Pair<Float, Float>?,  // pokud nejsou ends, pouĹľij tohle
    fallbackSign: Int?,                // pokud nejsou ends, pouĹľij tohle
    steps: Int = 200
): List<Offset3D>? {
    if (ends != null) {
        val A = toLocalXYandT(ends.first,  conic, b)
        val B = toLocalXYandT(ends.second, conic, b)

        // vÄ›tev urÄŤi prĹŻmÄ›rem signĹŻ (mÄ›ly by bĂ˝t stejnĂ©; kdyby ne, rozhodni podle prĹŻmÄ›rnĂ©ho x)
        val s = if ((A.s + B.s) >= 0) +1 else -1

        // t1, t2 vezmi pĹ™Ă­mo z endpointĹŻ â†’ bude to sedÄ›t i pĹ™i single-branch
        return sampleParametricHyperbolaArc(conic, a, b, A.t, B.t, s, steps)
    }

    // fallback: nenĂ­-li ends, sĂˇhni po uloĹľenĂ˝ch t-rezech
    if (fallbackArc != null) {
        val s = fallbackSign ?: +1
        return sampleParametricHyperbolaArc(conic, a, b, fallbackArc.first, fallbackArc.second, s, steps)
    }

    return null
}

fun branchSignFromEnds(
    conic: ConicSection3D,
    ends: Pair<Offset3D, Offset3D>
): Int {
    val (xA, _) = hyperbolaLocalXY(conic, ends.first)
    val (xB, _) = hyperbolaLocalXY(conic, ends.second)
    val avgX = xA + xB
    return if (avgX >= 0f) +1 else -1
}

fun sampleParametricHyperbola(
    conic: ConicSection3D,
    a: Float,
    b: Float,
    arcs: Pair<Pair<Float, Float>?, Pair<Float, Float>?>?,       // uloĹľenĂ© t-Ĺ™ezy
    branchSigns: Pair<Int?, Int?>? = null,                        // uloĹľenĂˇ znamĂ©nka
    ends: Pair<Pair<Offset3D, Offset3D>?, Pair<Offset3D, Offset3D>?>? = null, // â¬…ď¸Ź pĹ™idej
    tRange: Float = 3f,
    steps: Int = HYPERBOLA_FULL_STEPS
): HyperbolaBranches {
    // KdyĹľ nic nenĂ­, vygeneruj obÄ› vÄ›tve plnotuÄŤnÄ›
    if ((arcs == null || (arcs.first == null && arcs.second == null)) && (ends == null || (ends.first == null && ends.second == null))) {
        return sampleParametricHyperbolaSimple(conic, a, b, tRange, steps)
    }

    val pts1 = sampleHyperbolaFromEndsIfAvailable(
        conic, a, b,
        ends = ends?.first,
        fallbackArc  = arcs?.first,
        fallbackSign = branchSigns?.first,
        steps = steps / 2
    )

    val pts2 = sampleHyperbolaFromEndsIfAvailable(
        conic, a, b,
        ends = ends?.second,
        fallbackArc  = arcs?.second,
        fallbackSign = branchSigns?.second,
        steps = steps / 2
    )

    return when {
        pts1 != null && pts2 != null -> HyperbolaBranches(pts1, pts2)
        pts1 != null -> HyperbolaBranches(pts1, null)
        pts2 != null -> HyperbolaBranches(pts2, null)
        else -> sampleParametricHyperbolaSimple(conic, a, b, tRange, steps)
    }
}


fun sampleParametricHyperbolaSimple(
    conic: ConicSection3D,
    a: Float,
    b: Float,
    tRange: Float = 3f,
    steps: Int = HYPERBOLA_FULL_STEPS
): HyperbolaBranches {
    val branch1 = mutableListOf<Offset3D>()
    val branch2 = mutableListOf<Offset3D>()
    val (uN, vN) = orthonormalBasis(conic)

    for (i in 0..steps) {
        val t = -tRange + 2f * tRange * i / steps
        if (abs(t) < 1e-3f) continue

        val x1 =  a * cosh(t)
        val x2 = -a * cosh(t)
        val y  =  b * sinh(t)

        val p1 = conic.p0 + uN * x1 + vN * y
        val p2 = conic.p0 + uN * x2 + vN * y

        branch1 += p1
        branch2 += p2
    }

    return HyperbolaBranches(branch1, branch2)
}


data class HyperbolaBranches(
    val branch1: List<Offset3D>,
    val branch2: List<Offset3D>?
)

private data class OrthoBasis(val uN: Offset3D, val vN: Offset3D)

private fun orthonormalBasis(conic: ConicSection3D): OrthoBasis {
    val uN = conic.u.normalized()
    val vGS = conic.v - uN * conic.v.dot(uN)
    val vN = vGS.normalized()
    return OrthoBasis(uN, vN)
}

private data class LocalXY(val x: Float, val y: Float, val s: Int, val t: Float)

/** PĹ™evod svÄ›tovĂ©ho bodu na (x,y) v lokĂˇlnĂ­ bĂˇzi + urÄŤenĂ­ vÄ›tve a parametru t. */
private fun toLocalXYandT(p: Offset3D, conic: ConicSection3D, b: Float): LocalXY {
    val (uN, vN) = orthonormalBasis(conic)
    val d = p - conic.p0
    val x = d.dot(uN)
    val y = d.dot(vN)

    // t z y = b*sinh(t) je jednoznaÄŤnĂ© (vÄŤ. znamĂ©nka)
    val t = asinh(y / b)

    // vÄ›tev ze znamenka x (x ~ s * a * cosh t, cosh t >= 1)
    val s = if (x >= 0f) +1 else -1

    // volitelnĂ˝ sanity-check: |x| â‰ a*cosh(t); pĹ™i vÄ›tĹˇĂ­m rozjezdu mĹŻĹľeĹˇ invertovat t: t'=-t
    // ale vÄ›tĹˇinou to nenĂ­ nutnĂ©, protoĹľe y=b*sinh(t) nese znamĂ©nko.

    return LocalXY(x, y, s, t)
}

fun cosh(x: Float) = ((exp(x) + exp(-x)) / 2f)
fun sinh(x: Float) = ((exp(x) - exp(-x)) / 2f)
