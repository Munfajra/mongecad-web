package gl3d.scene

import geometry.conics.computeEllipseAxes3D
import gl3d.math.Vec3
import gl3d.math.toVec3
import model.classes.ConicSection3D
import kotlin.math.abs

/**
 * Parametry řídicí elipsy kvadriky ve světových souřadnicích.
 *
 * Poloosy a jejich směry počítá už `computeEllipseAxes3D`
 * (`geometry/conics/ConicMath.kt`), chybí jen střed – ten se v 2D
 * souřadnicích roviny kuželosečky najde řešením soustavy pro stacionární bod
 * kvadratické formy. Desktop má totéž v `ellipseFromConic3D`
 * (`monge/input/quadrics/conicalsurface/ConicalSurface.kt`).
 */
internal class EllipseParams(
    val center: Vec3,
    val u: Vec3,
    val v: Vec3,
    val a: Float,
    val b: Float,
) {
    val invA2: Float get() = 1f / (a * a)
    val invB2: Float get() = 1f / (b * b)
}

internal fun ellipseParams(conic: ConicSection3D, eps: Float = 1e-12f): EllipseParams? {
    val m = conic.matrix
    val bigA = m.m00
    val bigB = 2f * m.m01
    val bigC = m.m11
    val bigD = 2f * m.m02
    val bigE = 2f * m.m12

    // Determinant soustavy pro střed; nula znamená parabolu nebo degeneraci,
    // pro které se plocha takhle zadat nedá.
    val det = 4f * bigA * bigC - bigB * bigB
    if (abs(det) < eps) return null

    val centerU = (-2f * bigC * bigD + bigB * bigE) / det
    val centerV = (bigB * bigD - 2f * bigA * bigE) / det

    val axes = computeEllipseAxes3D(conic)
    if (axes.a <= 0f || axes.b <= 0f) return null

    val center = conic.p0 + conic.u * centerU + conic.v * centerV
    return EllipseParams(
        center = center.toVec3(),
        u = axes.uRotated.normalized().toVec3(),
        v = axes.vRotated.normalized().toVec3(),
        a = axes.a,
        b = axes.b,
    )
}

/**
 * Rovina, ve které kuželosečka leží: normála je `u × v`, posun z bodu `p0`.
 * Vrací `(normála, d)` pro tvar `n·x + d = 0`.
 */
internal fun conicPlane(conic: ConicSection3D): Pair<Vec3, Float> {
    val normal = (conic.u cross conic.v).toVec3()
    val d = -(normal.x * conic.p0.x + normal.y * conic.p0.y + normal.z * conic.p0.z)
    return normal to d
}

/**
 * Kvadratická forma kuželové plochy v souřadnicích s počátkem ve vrcholu.
 *
 * Kužel vzniká spojnicemi vrcholu s body řídicí kuželosečky; dosazením
 * parametrizace té spojnice do rovnice kuželosečky vyjde homogenní kvadratická
 * forma `pᵀ·M·p = 0`. Port `buildConeQuadricLocal` z `opengl/model/Cones.kt`.
 *
 * Vrací se jako 9 čísel po sloupcích, protože přesně tak to chce
 * `uniformMatrix3fv` v shaderu.
 */
internal fun coneQuadricLocal(conic: ConicSection3D, apex: Vec3): FloatArray? {
    val u = conic.u.toVec3()
    val v = conic.v.toVec3()
    val p0 = conic.p0.toVec3()

    val rawNormal = u cross v
    if (rawNormal.length() < 1e-8f) return null
    val n = rawNormal.normalized()

    val k = n dot (p0 - apex)
    val toApex = apex - p0

    // Osy soustavy, ve které se rovnice kuželosečky přepíše na kvadriku.
    val a1 = n * (u dot toApex) + u * k
    val a2 = n * (v dot toApex) + v * k
    val a3 = n

    val m = conic.matrix
    val coefA = m.m00
    val coefB = 2f * m.m01
    val coefC = m.m11
    val coefD = 2f * m.m02
    val coefE = 2f * m.m12
    val coefF = m.m22

    val out = FloatArray(9)

    fun addOuter(x: Vec3, y: Vec3, scale: Float) {
        val xs = floatArrayOf(x.x, x.y, x.z)
        val ys = floatArrayOf(y.x, y.y, y.z)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                // Sloupcové uspořádání: index = col * 3 + row.
                out[col * 3 + row] += scale * xs[row] * ys[col]
            }
        }
    }

    fun addSymmetric(x: Vec3, y: Vec3, scale: Float) {
        addOuter(x, y, scale)
        addOuter(y, x, scale)
    }

    addOuter(a1, a1, coefA)
    addOuter(a2, a2, coefC)
    addOuter(a3, a3, coefF)
    addSymmetric(a1, a2, 0.5f * coefB)
    addSymmetric(a1, a3, 0.5f * coefD)
    addSymmetric(a2, a3, 0.5f * coefE)

    return out
}
