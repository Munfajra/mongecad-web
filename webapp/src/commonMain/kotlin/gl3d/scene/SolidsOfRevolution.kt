package gl3d.scene

import androidx.compose.ui.geometry.Offset
import gl3d.math.Vec3
import gl3d.render.Mesh3D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sítě rotačních ploch – port `opengl/model/SoR.kt` (geometrická část).
 *
 * Plocha je P(t, φ) = axisPoint + w·t + r(t)·(u·cosφ + v·sinφ). Ze zadaného
 * meridiánu se nejdřív udělá znaménkový profil (včetně bodů, kde meridián
 * protne osu), pak se orotuje kolem osy. Vrcholy na ose dostávají vlastní
 * vějíř, aby v pólu nevznikl degenerovaný trojúhelník s nulovou normálou.
 */
private const val SOR_SLICES = 128

/** Osová báze (u, v, w); w je normalizovaný směr osy. */
private fun buildAxisBasis(axisDir: Vec3): Triple<Vec3, Vec3, Vec3> {
    val w = axisDir.normalized()
    // zvol "nejmíň rovnoběžný" pomocný vektor
    val tmp = if (abs(w.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(0f, 1f, 0f)
    val u = (tmp cross w).normalized()   // u ⟂ w
    val v = w cross u                    // v ⟂ w i u
    return Triple(u, v, w)
}

private class SoRProfilePoint(val t: Float, val rSigned: Float)

private fun buildSignedProfileWithAxisIntersections(
    meridianXZ: List<Offset>,
    axisX0: Float,
    eps: Float = 1e-6f,
): List<SoRProfilePoint> {
    if (meridianXZ.size < 2) return emptyList()

    val out = mutableListOf<SoRProfilePoint>()

    fun signedR(p: Offset) = p.x - axisX0
    fun addPoint(p: Offset) {
        out += SoRProfilePoint(t = p.y, rSigned = signedR(p))
    }

    addPoint(meridianXZ.first())

    for (i in 0 until meridianXZ.lastIndex) {
        val a = meridianXZ[i]
        val b = meridianXZ[i + 1]

        val ra = signedR(a)
        val rb = signedR(b)

        val aOn = abs(ra) < eps
        val bOn = abs(rb) < eps

        if (!aOn && !bOn && ra * rb < 0f) {
            val alpha = -ra / (rb - ra)
            val tCross = a.y + alpha * (b.y - a.y)
            out += SoRProfilePoint(tCross, 0f)
        }

        addPoint(b)
    }

    val deduplicated = mutableListOf<SoRProfilePoint>()
    for (p in out) {
        val last = deduplicated.lastOrNull()
        if (last == null || abs(last.t - p.t) > eps || abs(last.rSigned - p.rSigned) > eps) {
            deduplicated += p
        }
    }
    return deduplicated
}

/**
 * Bod, ve kterém meridián osu jen protne, musí být v profilu dvakrát: jednou
 * jako konec dolního pásu a jednou jako začátek horního. Jinak by se oba pásy
 * u pólu slily do jednoho vějíře.
 */
private fun splitSharedAxisProfilePoints(
    profile: List<SoRProfilePoint>,
    eps: Float = 1e-6f,
): List<SoRProfilePoint> {
    if (profile.size < 3) return profile
    val out = ArrayList<SoRProfilePoint>(profile.size + 4)
    for (i in profile.indices) {
        val p = profile[i]
        val prev = profile.getOrNull(i - 1)
        val next = profile.getOrNull(i + 1)
        out += p
        if (
            prev != null &&
            next != null &&
            abs(p.rSigned) <= eps &&
            abs(prev.rSigned) > eps &&
            abs(next.rSigned) > eps
        ) {
            out += p
        }
    }
    return out
}

private fun sanitizeMeridian(pts: List<Offset>, eps: Float = 1e-4f): List<Offset> {
    if (pts.isEmpty()) return pts
    val out = ArrayList<Offset>(pts.size)
    out.add(pts.first())
    for (i in 1 until pts.size) {
        val a = out.last()
        val b = pts[i]
        if ((b - a).getDistance() > eps) out.add(b)
    }
    // pokud je uzavřený (poslední ~ první), poslední vyhoď
    if (out.size >= 2 && (out.first() - out.last()).getDistance() <= eps) {
        out.removeAt(out.lastIndex)
    }
    return out
}

/**
 * Rotační plocha z meridiánu. Vrací `null` místo desktopového `require`,
 * protože ve webu není kam vypsat stack trace – rozpracovaná konstrukce se
 * prostě nekreslí.
 */
internal fun buildSoRMesh(
    axisPoint: Vec3,
    axisDir: Vec3,
    axisX0InMeridian: Float,
    meridianXZ: List<Offset>,
    slices: Int = SOR_SLICES,
): Mesh3D? {
    val clean = sanitizeMeridian(meridianXZ, eps = 1e-3f)
    if (clean.size < 2 || slices < 2) return null

    val baseProfile = buildSignedProfileWithAxisIntersections(clean, axisX0InMeridian)
    if (baseProfile.size < 2) return null
    val profile = splitSharedAxisProfilePoints(baseProfile)
    if (profile.size < 2) return null

    val (u, v, w) = buildAxisBasis(axisDir)

    val m = profile.size
    val profT = FloatArray(m) { profile[it].t }
    val profR = FloatArray(m) { abs(profile[it].rSigned) }

    val ring = slices + 1
    val axisRadiusEps = 1e-7f
    val baseVertCount = m * ring
    val extraAxisFanVertices = (0 until m - 1).count { i ->
        (profR[i] < axisRadiusEps) != (profR[i + 1] < axisRadiusEps)
    } * slices
    val vertCount = baseVertCount + extraAxisFanVertices
    val pos = FloatArray(vertCount * 3)
    val nor = FloatArray(vertCount * 3)

    val t0 = axisPoint dot w

    fun derivAt(i: Int): Pair<Float, Float> {
        val i0 = (i - 1).coerceAtLeast(0)
        val i1 = (i + 1).coerceAtMost(m - 1)
        val dt = profT[i1] - profT[i0]
        val dr = profR[i1] - profR[i0]
        if (abs(dt) < 1e-9f && abs(dr) < 1e-9f) return 0f to 0f
        return dt to dr
    }

    var k = 0
    for (i in 0 until m) {
        val t = profT[i] - t0
        val r = profR[i]
        val (dtp, drp) = derivAt(i)

        for (j in 0..slices) {
            val phi = (j.toFloat() / slices.toFloat()) * TWO_PI
            val c = cos(phi)
            val sn = sin(phi)

            val radial = (u * c) + (v * sn)
            val p = axisPoint + (w * t) + (radial * r)

            pos[k] = p.x
            pos[k + 1] = p.y
            pos[k + 2] = p.z

            val tPhi = ((u * (-sn)) + (v * c)) * r
            val tMer = (w * dtp) + (radial * drp)

            var n = tPhi cross tMer
            val nLen = n.length()

            n = if (nLen < 1e-9f) {
                // Pól nebo vodorovná rovnoběžka: normála z limitního směru,
                // v úplně degenerovaném případě aspoň radiální.
                val limit = (radial * dtp) - (w * drp)
                if (limit.length() >= 1e-9f) limit.normalized() else radial
            } else {
                n * (1f / nLen)
            }

            nor[k] = n.x
            nor[k + 1] = n.y
            nor[k + 2] = n.z

            k += 3
        }
    }

    fun vid(i: Int, j: Int) = i * ring + j

    val idx = ArrayList<Int>((m - 1) * slices * 6)
    var nextExtraVertex = baseVertCount

    fun addAxisFanVertex(axisProfileIndex: Int, phi: Float, dtp: Float, drp: Float): Int {
        val radial = (u * cos(phi)) + (v * sin(phi))
        val t = profT[axisProfileIndex] - t0
        val p = axisPoint + (w * t)
        val limit = (radial * dtp) - (w * drp)
        val n = if (limit.length() >= 1e-9f) limit.normalized() else radial

        val out = nextExtraVertex++
        val k3 = out * 3
        pos[k3] = p.x
        pos[k3 + 1] = p.y
        pos[k3 + 2] = p.z
        nor[k3] = n.x
        nor[k3 + 1] = n.y
        nor[k3 + 2] = n.z
        return out
    }

    for (i in 0 until m - 1) {
        val aOnAxis = profR[i] < axisRadiusEps
        val bOnAxis = profR[i + 1] < axisRadiusEps
        if (aOnAxis && bOnAxis) continue

        val segDt = profT[i + 1] - profT[i]
        val segDr = profR[i + 1] - profR[i]
        for (j in 0 until slices) {
            val a = vid(i, j)
            val b = vid(i + 1, j)
            val c = vid(i + 1, j + 1)
            val d = vid(i, j + 1)
            val phiMid = ((j + 0.5f) / slices.toFloat()) * TWO_PI

            when {
                aOnAxis -> {
                    val apex = addAxisFanVertex(i, phiMid, segDt, segDr)
                    idx += apex; idx += b; idx += c
                }
                bOnAxis -> {
                    val apex = addAxisFanVertex(i + 1, phiMid, segDt, segDr)
                    idx += a; idx += apex; idx += d
                }
                else -> {
                    idx += a; idx += b; idx += c
                    idx += a; idx += c; idx += d
                }
            }
        }
    }
    if (nextExtraVertex != vertCount) return null
    if (idx.isEmpty()) return null

    return Mesh3D(pos, nor, idx.toIntArray())
}

/**
 * Analytický obrys (silueta) rotační plochy pro daný směr pohledu – port
 * `sorSilhouettePolylines` z `opengl/model/SoR.kt`.
 *
 * Normála plochy míří `radial·dt − w·dr` (viz normály v [buildSoRMesh]), takže
 * se podmínka siluety `N·d = 0` pro každý bod profilu `(t, r)` redukuje na
 * `a·cosφ + b·sinφ = C` a řeší se přímo. Vznikají dvě krajní tvořicí křivky,
 * stejná role jako dvě tvořicí přímky u válce nebo kužele. Vrací seznam
 * polyčar: v okolí pólů se obrys přeruší, protože tam přechází na rovnoběžku.
 */
internal fun sorSilhouettePolylines(
    axisPoint: Vec3,
    axisDir: Vec3,
    axisX0InMeridian: Float,
    meridianXZ: List<Offset>,
    viewDir: Vec3,
): List<List<Vec3>> {
    val clean = sanitizeMeridian(meridianXZ, eps = 1e-3f)
    if (clean.size < 2) return emptyList()
    val profile = buildSignedProfileWithAxisIntersections(clean, axisX0InMeridian)
    if (profile.size < 2) return emptyList()

    val (u, v, w) = buildAxisBasis(axisDir)
    val d = viewDir.normalized()
    if (d.length() < 1e-7f) return emptyList()
    val du = u dot d
    val dv = v dot d
    val dw = w dot d
    val t0 = axisPoint dot w

    val m = profile.size
    val profT = FloatArray(m) { profile[it].t }
    val profR = FloatArray(m) { abs(profile[it].rSigned) }

    fun derivAt(i: Int): Pair<Float, Float> {
        val i0 = (i - 1).coerceAtLeast(0)
        val i1 = (i + 1).coerceAtMost(m - 1)
        return (profT[i1] - profT[i0]) to (profR[i1] - profR[i0])
    }

    fun pointAt(t: Float, r: Float, phi: Float): Vec3 {
        val radial = (u * cos(phi)) + (v * sin(phi))
        return axisPoint + (w * t) + (radial * r)
    }

    val runs = mutableListOf<List<Vec3>>()
    var runA = mutableListOf<Vec3>()
    var runB = mutableListOf<Vec3>()
    fun flush() {
        if (runA.size >= 2) runs += runA
        if (runB.size >= 2) runs += runB
        runA = mutableListOf()
        runB = mutableListOf()
    }

    for (i in 0 until m) {
        val t = profT[i] - t0
        val r = profR[i]
        val (dt, dr) = derivAt(i)
        val a = dt * du
        val b = dt * dv
        val cc = dw * dr
        val radius = sqrt(a * a + b * b)
        // radius≈0 ⇒ vodorovná rovnoběžka (celý kruh čelem/zády k pozorovateli),
        // |cc|>radius ⇒ v této výšce silueta neexistuje – obrys se přeruší.
        if (radius < 1e-6f || abs(cc) > radius + 1e-4f) {
            flush()
            continue
        }
        val base = atan2(b, a)
        val delta = acos((cc / radius).coerceIn(-1f, 1f))
        runA += pointAt(t, r, base + delta)
        runB += pointAt(t, r, base - delta)
    }
    flush()
    return runs
}

/** Podpis geometrie rotační plochy pro cache sítě. */
internal fun sorMeshSignature(axisStart: Vec3, meridian: List<Offset>): Long {
    var hash = axisStart.x.toRawBits().toLong()
    hash = hash * 31 + axisStart.y.toRawBits()
    hash = hash * 31 + axisStart.z.toRawBits()
    hash = hash * 31 + meridian.size
    meridian.forEach { p ->
        hash = hash * 31 + p.x.toRawBits()
        hash = hash * 31 + p.y.toRawBits()
    }
    return hash
}

private const val TWO_PI = (2.0 * PI).toFloat()
