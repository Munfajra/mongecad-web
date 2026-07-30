package draw.mongescreen.fills

import androidx.compose.ui.geometry.Offset
import model.Offset3D
import model.SolidOfRevolutionNarys
import model.SolidOfRevolutionPudorys
import model.classes.ConicSection3D
import model.classes.ConicalSurface3D
import model.classes.CylindricalSurface3D
import model.classes.SegmentSolid3D
import model.classes.SphereSurface3D
import model.normalize
import monge.input.intersections.ops.facePlaneFromPolygon
import monge.input.intersections.ops.pointInPolygon2D
import monge.input.intersections.ops.pointOnFiniteCylinder
import monge.input.intersections.ops.sorOcclusionProbe
import state.MongeState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin

/**
 * Occlusion – hloubka přední plochy tělesa podél promítacího paprsku.
 *
 * Hloubka slouží ke **klasifikaci** (které těleso je v daném bodě blíž pozorovateli), proto stačí
 * numerický scan membership-predikátem podél paprsku – žádná analytická ray×kvadrika algebra.
 * Predikát i rozsah t se předpočítají jednou do [SolidProbe]; grid pak jen posouvá bázi paprsku.
 */

// Sjednocený obal nad tělesem, které umí vyplnit (a tedy occludovat).
internal sealed class OcclusionSolid {
    abstract val id: String

    data class Seg(val solid: SegmentSolid3D) : OcclusionSolid() {
        override val id get() = solid.id
    }

    data class Sph(val sphere: SphereSurface3D) : OcclusionSolid() {
        override val id get() = sphere.id
    }

    data class Cyl(val cylinder: CylindricalSurface3D) : OcclusionSolid() {
        override val id get() = cylinder.id
    }

    data class Con(val cone: ConicalSurface3D) : OcclusionSolid() {
        override val id get() = cone.id
    }

    /** Rotační plocha – hloubkovou sondu staví až [buildSolidProbe] (resolve běží každý frame). */
    class Sor(
        override val id: String,
        val narys: SolidOfRevolutionNarys?,
        val pudorys: SolidOfRevolutionPudorys?,
    ) : OcclusionSolid()

    /** Přímková plocha – hloubka i silueta se odvozují z husté sítě primární reguly. */
    data class Ruled(val surface: model.classes.RuledSurface3D) : OcclusionSolid() {
        override val id get() = surface.id
    }
}

/** Dohledá těleso podle id operandu průniku. */
internal fun resolveOcclusionSolid(state: MongeState, id: String): OcclusionSolid? =
    state.segmentSolids3D.firstOrNull { it.id == id }?.let { OcclusionSolid.Seg(it) }
        ?: state.spheres3D.firstOrNull { it.id == id }?.let { OcclusionSolid.Sph(it) }
        ?: state.cylindricalSurfaces.firstOrNull { it.id == id }?.let { OcclusionSolid.Cyl(it) }
        ?: state.conicalSurfaces.firstOrNull { it.id == id }?.let { OcclusionSolid.Con(it) }
        ?: state.ruledSurfaces.firstOrNull { it.id == id }?.let { OcclusionSolid.Ruled(it) }
        ?: resolveOcclusionSor(state, id)

private fun resolveOcclusionSor(state: MongeState, id: String): OcclusionSolid.Sor? {
    val nar = state.solidsOfRevolutionNarys.firstOrNull { it.id == id }
    val pud = state.solidsOfRevolutionPudorys.firstOrNull { it.id == id }
    if (nar == null && pud == null) return null
    return OcclusionSolid.Sor(id, nar, pud)
}

private const val DIRECTRIX_SAMPLES = 48

/**
 * Předpočítaná sonda tělesa pro rychlý hloubkový scan: membership-predikát [inside] a rozsah
 * parametru t podél paprsku ([tLo], [tHi]). Rozsah t nezávisí na poloze bodu v obraze, protože
 * báze paprsku je vždy kolmá na jeho směr (báze ⟂ směr promítání) → t = bod·dir.
 */
internal class SolidProbe(
    val inside: (Offset3D) -> Boolean,
    val tLo: Float,
    val tHi: Float,
    val frontDepth: ((base: Offset3D, dir: Offset3D) -> Float?)? = null,
)

internal fun buildSolidProbe(state: MongeState, solid: OcclusionSolid, dir: Offset3D): SolidProbe? {
    // Rotační plocha: geometrie, hloubka i membership přicházejí z jedné sondy (intersections/ops).
    if (solid is OcclusionSolid.Sor) {
        val probe = sorOcclusionProbe(state, solid.narys, solid.pudorys) ?: return null
        val range = tRangeOfPoints(probe.characteristicPoints, dir) ?: return null
        return SolidProbe(probe.inside, range.first, range.second, probe.frontDepth)
    }
    if (solid is OcclusionSolid.Ruled) return buildRuledProbe(state, solid.surface, dir)
    val inside = buildInside(state, solid) ?: return null
    val range = solidTRange(state, solid, dir) ?: return null
    if (!range.first.isFinite() || !range.second.isFinite() || range.second <= range.first) return null
    return SolidProbe(inside, range.first, range.second, buildFrontDepth(state, solid, range))
}

private const val PROBE_SAMPLES = 32

/**
 * Hloubka přední (k pozorovateli nejbližší) plochy tělesa jako parametr t na paprsku
 * `base + t·dir`. Větší t = blíž pozorovateli. null když paprsek těleso mine.
 *
 * Analytické [SolidProbe.frontDepth] se věří včetně null (= paprsek MINE) – bez
 * fallbacku na membership scan. Ten by u rotační plochy s otevřeným meridiánem hlásil
 * falešné zásahy: pointInPolygon2D uzavírá profil tětivou, takže oblast mezi meridiánem
 * a tětivou počítá jako vnitřek tělesa (srpek u pasu hyperboloidu by „zasahoval").
 */
internal fun probeFrontDepth(probe: SolidProbe, base: Offset3D, dir: Offset3D): Float? {
    probe.frontDepth?.let { return it(base, dir) }
    val pad = (probe.tHi - probe.tLo) * 0.05f + 1e-3f
    val tHi = probe.tHi + pad
    val tLo = probe.tLo - pad
    for (i in 0..PROBE_SAMPLES) {
        val t = tHi - (tHi - tLo) * i / PROBE_SAMPLES
        if (probe.inside(base + dir * t)) return t
    }
    return null
}

private fun buildFrontDepth(
    state: MongeState,
    solid: OcclusionSolid,
    range: Pair<Float, Float>,
): ((Offset3D, Offset3D) -> Float?)? {
    return when (solid) {
        is OcclusionSolid.Seg -> buildSegmentFrontDepth(state, solid.solid, range)
        is OcclusionSolid.Sph -> buildSphereFrontDepth(state, solid.sphere, range)
        is OcclusionSolid.Cyl -> buildCylinderFrontDepth(state, solid.cylinder, range)
        is OcclusionSolid.Sor,             // řeší se přímo v buildSolidProbe
        is OcclusionSolid.Ruled,           // řeší se přímo v buildSolidProbe
        is OcclusionSolid.Con -> null
    }
}

// ───────────────────────── přímková plocha ─────────────────────────

private const val RULED_PROBE_RULINGS = 192

/**
 * Hloubková sonda přímkové plochy: trojúhelníky husté sítě primární reguly,
 * přední hloubka = největší t průsečíků paprsku se sítí (miss → null, plocha je
 * tenká skořepina bez vnitřku). Prefiltr přes 2D obalové obdélníky trojúhelníků
 * v bázi kolmé na směr promítání drží scan levný i pro tisíce vzorků.
 */
private fun buildRuledProbe(
    state: MongeState,
    surface: model.classes.RuledSurface3D,
    dir: Offset3D,
): SolidProbe? {
    // along=2 stačí: řádky sítě jsou úsečky tvořic, jemnější dělení podél přímky
    // geometrii nemění.
    val grids = monge.input.ruledsurface.ruledSurfaceTrimmedPrimaryGrids(state, surface, RULED_PROBE_RULINGS, 2)
    if (grids.isEmpty()) return null
    val closed = monge.input.ruledsurface.ruledSurfaceFamilyIsClosed(state, surface)

    val u = normalizeOrNull(
        if (abs(dir.x) <= abs(dir.y) && abs(dir.x) <= abs(dir.z)) dir cross Offset3D(1f, 0f, 0f)
        else if (abs(dir.y) <= abs(dir.z)) dir cross Offset3D(0f, 1f, 0f)
        else dir cross Offset3D(0f, 0f, 1f)
    ) ?: return null
    val v = normalizeOrNull(dir cross u) ?: return null

    val vertices = ArrayList<Offset3D>()
    // ploché pole: a, e1=b−a, e2=c−a po třech Offset3D + 2D bounds (uMin,uMax,vMin,vMax)
    val triangles = ArrayList<Offset3D>()
    val bounds = ArrayList<FloatArray>()
    fun addTriangle(a: Offset3D, b: Offset3D, c: Offset3D) {
        triangles += a
        triangles += b - a
        triangles += c - a
        val ua = a dot u; val ub = b dot u; val uc = c dot u
        val va = a dot v; val vb = b dot v; val vc = c dot v
        bounds += floatArrayOf(minOf(ua, ub, uc), maxOf(ua, ub, uc), minOf(va, vb, vc), maxOf(va, vb, vc))
    }
    for (grid in grids) {
        if (grid.size < 2 || grid[0].size < 2) continue
        val rowCount = if (closed) grid.size else grid.size - 1
        for (row in 0 until rowCount) {
            val next = (row + 1) % grid.size
            // Krajní sloupce = skutečné (případně ořezané/prodloužené) konce
            // tvořic; mezisloupce leží na téže úsečce a geometrii nemění.
            val a = grid[row].first()
            val b = grid[next].first()
            val c = grid[next].last()
            val d = grid[row].last()
            addTriangle(a, b, c)
            addTriangle(a, c, d)
            vertices += a
            vertices += d
        }
        vertices += grid.last().first()
        vertices += grid.last().last()
    }
    if (bounds.isEmpty()) return null
    val range = tRangeOfPoints(vertices, dir) ?: return null
    val pad = 1e-3f * (range.second - range.first).coerceAtLeast(1f)

    val frontDepth: (Offset3D, Offset3D) -> Float? = front@{ base, rayDir ->
        val bu = base dot u
        val bv = base dot v
        var best = Float.NEGATIVE_INFINITY
        for (index in bounds.indices) {
            val box = bounds[index]
            if (bu < box[0] - pad || bu > box[1] + pad || bv < box[2] - pad || bv > box[3] + pad) continue
            val a = triangles[index * 3]
            val e1 = triangles[index * 3 + 1]
            val e2 = triangles[index * 3 + 2]
            val h = rayDir cross e2
            val det = e1 dot h
            if (abs(det) < 1e-12f) continue
            val invDet = 1f / det
            val s = base - a
            val uu = (s dot h) * invDet
            if (uu < -1e-4f || uu > 1f + 1e-4f) continue
            val q = s cross e1
            val vv = (rayDir dot q) * invDet
            if (vv < -1e-4f || uu + vv > 1f + 1e-4f) continue
            val t = (e2 dot q) * invDet
            if (t.isFinite() && t > best) best = t
        }
        if (best == Float.NEGATIVE_INFINITY) null else best
    }
    return SolidProbe({ false }, range.first, range.second, frontDepth)
}

private fun buildSphereFrontDepth(
    state: MongeState,
    sphere: SphereSurface3D,
    range: Pair<Float, Float>,
): ((Offset3D, Offset3D) -> Float?)? {
    val c = sphereCenter(state, sphere) ?: return null
    val r = sphere.radius * (1f + 1e-3f)
    val r2 = r * r
    val pad = (range.second - range.first) * 0.05f + 1e-3f
    val lo = range.first - pad
    val hi = range.second + pad
    return { base, rayDir ->
        val oc = base - c
        val a = rayDir dot rayDir
        if (abs(a) < 1e-12f) {
            null
        } else {
            val b = 2f * (oc dot rayDir)
            val cc = (oc dot oc) - r2
            val disc = b * b - 4f * a * cc
            if (disc < -1e-5f) {
                null
            } else {
                val root = sqrt(maxOf(0f, disc))
                val tFront = (-b + root) / (2f * a)
                if (tFront in lo..hi) tFront else null
            }
        }
    }
}

private fun buildCylinderFrontDepth(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    range: Pair<Float, Float>,
): ((Offset3D, Offset3D) -> Float?)? {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return null
    val w = cylinder.direction
    val n = conic.u cross conic.v
    if (abs(n dot w) < 1e-9f) return null

    val capPlanes = cylinderCapPlanesForDepth(state, cylinder, conic)
    val capPoly2D = directrixSamples3D(conic)
        .mapNotNull { conicLocalCoordinates(conic, it - conic.p0)?.let { st -> Offset(st.first, st.second) } }
    val pad = (range.second - range.first) * 0.05f + 1e-3f
    val lo = range.first - pad
    val hi = range.second + pad

    return { base, rayDir ->
        val candidates = ArrayList<Float>(4)
        candidates += rayCylinderSideHits(state, conic, cylinder, base, rayDir, lo, hi)
        for ((planePoint, planeNormal) in capPlanes) {
            val denom = planeNormal dot rayDir
            if (abs(denom) < 1e-9f) continue
            val t = (planeNormal dot (planePoint - base)) / denom
            if (t in lo..hi) {
                val hit = base + rayDir * t
                if (pointInsideCylinderCapDisk(conic, w, capPoly2D, hit)) candidates += t
            }
        }
        candidates.maxOrNull()
    }
}

private fun rayCylinderSideHits(
    state: MongeState,
    conic: ConicSection3D,
    cylinder: CylindricalSurface3D,
    base: Offset3D,
    rayDir: Offset3D,
    lo: Float,
    hi: Float,
): List<Float> {
    val rel = base - conic.p0
    val st0 = projectedCylinderLocalCoordinates(conic, cylinder.direction, rel) ?: return emptyList()
    val stD = projectedCylinderLocalCoordinates(conic, cylinder.direction, rayDir) ?: return emptyList()
    val (qa, qb, qc) = conicSurfaceQuadratic(
        conic,
        sa = st0.first,
        sb = stD.first,
        ta = st0.second,
        tb = stD.second,
        ga = 1f,
        gb = 0f,
    )
    return solveQuadraticLocal(qa, qb, qc)
        .filter { t ->
            t in lo..hi && pointOnFiniteCylinder(state, cylinder, base + rayDir * t)
        }
}

private fun projectedCylinderLocalCoordinates(
    conic: ConicSection3D,
    w: Offset3D,
    vector: Offset3D,
): Pair<Float, Float>? {
    val projected = projectCylinderVectorToBasePlane(conic, w, vector) ?: return null
    return conicLocalCoordinates(conic, projected)
}

private fun projectCylinderVectorToBasePlane(
    conic: ConicSection3D,
    w: Offset3D,
    vector: Offset3D,
): Offset3D? {
    val n = conic.u cross conic.v
    val nw = n dot w
    if (abs(nw) < 1e-9f) return null
    return vector - w * ((n dot vector) / nw)
}

private fun projectCylinderPointToBasePlane(
    conic: ConicSection3D,
    w: Offset3D,
    point: Offset3D,
): Offset3D? {
    val n = conic.u cross conic.v
    val nw = n dot w
    if (abs(nw) < 1e-9f) return null
    return point - w * ((n dot (point - conic.p0)) / nw)
}

private fun conicLocalCoordinates(conic: ConicSection3D, vector: Offset3D): Pair<Float, Float>? {
    val u = conic.u
    val v = conic.v
    val uu = u dot u
    val uv = u dot v
    val vv = v dot v
    val det = uu * vv - uv * uv
    if (abs(det) < 1e-12f) return null
    val ru = vector dot u
    val rv = vector dot v
    return ((vv * ru - uv * rv) / det) to ((uu * rv - uv * ru) / det)
}

private fun conicSurfaceQuadratic(
    conic: ConicSection3D,
    sa: Float,
    sb: Float,
    ta: Float,
    tb: Float,
    ga: Float,
    gb: Float,
): Triple<Float, Float, Float> {
    val m = conic.matrix
    val m01 = (m.m01 + m.m10) * 0.5f
    val m02 = (m.m02 + m.m20) * 0.5f
    val m12 = (m.m12 + m.m21) * 0.5f
    val a = m.m00 * sb * sb + m.m11 * tb * tb + m.m22 * gb * gb +
        2f * m01 * sb * tb + 2f * m02 * sb * gb + 2f * m12 * tb * gb
    val b = 2f * m.m00 * sa * sb + 2f * m.m11 * ta * tb + 2f * m.m22 * ga * gb +
        2f * m01 * (sa * tb + sb * ta) +
        2f * m02 * (sa * gb + sb * ga) +
        2f * m12 * (ta * gb + tb * ga)
    val c = m.m00 * sa * sa + m.m11 * ta * ta + m.m22 * ga * ga +
        2f * m01 * sa * ta + 2f * m02 * sa * ga + 2f * m12 * ta * ga
    return Triple(a, b, c)
}

private fun solveQuadraticLocal(a: Float, b: Float, c: Float): List<Float> {
    val scale = (abs(a) + abs(b) + abs(c)).coerceAtLeast(1e-12f)
    if (abs(a) < 1e-9f * scale) {
        if (abs(b) < 1e-9f * scale) return emptyList()
        return listOf(-c / b)
    }
    val disc = b * b - 4f * a * c
    val tol = 1e-6f * (b * b + abs(4f * a * c)).coerceAtLeast(1f)
    if (disc < -tol) return emptyList()
    if (disc <= tol) return listOf(-b / (2f * a))
    val sq = sqrt(disc)
    val t1 = (-b - sq) / (2f * a)
    val t2 = (-b + sq) / (2f * a)
    if (abs(t1 - t2) <= 1e-4f * (abs(t1) + abs(t2)) + 1e-6f) return listOf((t1 + t2) * 0.5f)
    return listOf(t1, t2)
}

private fun cylinderCapPlanesForDepth(
    state: MongeState,
    cylinder: CylindricalSurface3D,
    conic: ConicSection3D,
): List<Pair<Offset3D, Offset3D>> {
    val baseNormal = normalizeOrNull(conic.u cross conic.v) ?: return emptyList()
    val out = mutableListOf(conic.p0 to baseNormal)
    var hasTopPlane = false
    cylinder.topPlaneId?.let { id ->
        state.planes3D.find { it.id == id }?.equation?.let { eq ->
            val normal = Offset3D(eq.a, eq.b, eq.c)
            val len2 = normal dot normal
            if (len2 > 1e-12f) {
                val point = normal * (-eq.d / len2)
                val unitNormal = normalizeOrNull(normal)
                if (unitNormal != null) {
                    out += point to unitNormal
                    hasTopPlane = true
                }
            }
        }
    }
    if (!hasTopPlane) {
        cylinder.upperConicId?.let { id ->
            state.conics3D.find { it.id == id }?.let { upper ->
                val normal = normalizeOrNull(upper.u cross upper.v)
                if (normal != null) out += upper.p0 to normal
            }
        }
    }
    return out
}

private fun pointInsideCylinderCapDisk(
    conic: ConicSection3D,
    w: Offset3D,
    capPoly2D: List<Offset>,
    point: Offset3D,
): Boolean {
    if (capPoly2D.size < 3) return false
    val base = projectCylinderPointToBasePlane(conic, w, point) ?: return false
    val local = conicLocalCoordinates(conic, base - conic.p0) ?: return false
    return pointInPolygon2D(Offset(local.first, local.second), capPoly2D)
}

private fun normalizeOrNull(v: Offset3D): Offset3D? {
    val len = v.length()
    return if (len < 1e-9f) null else v * (1f / len)
}

private class SegmentHalfspace(
    val plane: monge.input.intersections.ops.FacePlane,
    val side: Float,
)

private fun buildSegmentFrontDepth(
    state: MongeState,
    solid: SegmentSolid3D,
    range: Pair<Float, Float>,
): ((Offset3D, Offset3D) -> Float?)? {
    val faces = segmentSolidFaces(state, solid) ?: return null
    val faces3D = faces.map { face -> face.map { Offset3D(it.x, it.y, it.z) } }
    val verts = faces3D.flatten()
    if (verts.isEmpty()) return null
    val centroid = verts.reduce { a, b -> a + b } * (1f / verts.size)
    val scale = verts.maxOf { (it - centroid).length() }.coerceAtLeast(1f)
    val tol = 1e-4f * scale
    val halfspaces = faces3D.mapNotNull { face ->
        facePlaneFromPolygon(face)?.let { plane ->
            val side = if (plane.signedDistance(centroid) >= 0f) 1f else -1f
            SegmentHalfspace(plane, side)
        }
    }
    if (halfspaces.isEmpty()) return null

    val pad = (range.second - range.first) * 0.05f + 1e-3f
    val rangeLo = range.first - pad
    val rangeHi = range.second + pad

    return front@{ base, rayDir ->
        var enter = rangeLo
        var exit = rangeHi
        for (h in halfspaces) {
            val d0 = h.side * h.plane.signedDistance(base)
            val dd = h.side * (h.plane.normal dot rayDir)
            if (abs(dd) < 1e-9f) {
                if (d0 < -tol) return@front null
            } else {
                val t = (-tol - d0) / dd
                if (dd > 0f) {
                    if (t > enter) enter = t
                } else {
                    if (t < exit) exit = t
                }
                if (enter > exit + 1e-6f) return@front null
            }
        }
        exit.takeIf { it.isFinite() && it >= enter - 1e-6f }
    }
}

// ───────────────────────── membership predikát (předpočítaný jednou) ─────────────────────────

/**
 * Postaví predikát „bod je uvnitř tělesa" s předpočítanými daty (stěny / directrix polygon).
 * Radiální test u válce/kužele je point-in-polygon vůči navzorkované directrix – nezávislý na
 * znaménku/škále matice kuželosečky (to je pro robustnost klíčové).
 */
private fun buildInside(state: MongeState, solid: OcclusionSolid): ((Offset3D) -> Boolean)? {
    return when (solid) {
        is OcclusionSolid.Seg -> buildInsideSegmentSolid(state, solid.solid)
        is OcclusionSolid.Sph -> {
            val c = sphereCenter(state, solid.sphere) ?: return null
            val r = solid.sphere.radius * (1f + 1e-3f)
            { x -> (x - c).length() <= r }
        }
        is OcclusionSolid.Cyl -> buildInsideCylinder(state, solid.cylinder)
        is OcclusionSolid.Con -> buildInsideCone(state, solid.cone)
        is OcclusionSolid.Sor, is OcclusionSolid.Ruled -> null   // řeší se přímo v buildSolidProbe
    }
}

// Konvexní těleso: bod je uvnitř, leží-li na téže straně každé stěny jako těžiště.
private fun buildInsideSegmentSolid(state: MongeState, solid: SegmentSolid3D): ((Offset3D) -> Boolean)? {
    val faces = segmentSolidFaces(state, solid) ?: return null
    val faces3D = faces.map { face -> face.map { Offset3D(it.x, it.y, it.z) } }
    val verts = faces3D.flatten()
    if (verts.isEmpty()) return null
    val centroid = verts.reduce { a, b -> a + b } * (1f / verts.size)
    val scale = verts.maxOf { (it - centroid).length() }.coerceAtLeast(1f)
    val tol = 1e-3f * scale
    val planes = faces3D.mapNotNull { face ->
        facePlaneFromPolygon(face)?.let { it to it.signedDistance(centroid) }
    }
    return { x ->
        planes.all { (plane, dc) ->
            val dx = plane.signedDistance(x)
            !((dc >= 0f && dx < -tol) || (dc < 0f && dx > tol))
        }
    }
}

private fun buildInsideCylinder(state: MongeState, cylinder: CylindricalSurface3D): ((Offset3D) -> Boolean)? {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return null
    val n = conic.u cross conic.v
    val w = cylinder.direction
    val nw = n dot w
    if (abs(nw) < 1e-9f) return null
    val poly2D = directrixSamples3D(conic).map { Offset((it - conic.p0) dot conic.u, (it - conic.p0) dot conic.v) }
    if (poly2D.size < 3) return null
    return { x ->
        // projekce na základnovou rovinu podél tvořicí → radiální (point-in-polygon) + axiální test
        val y = x - w * ((n dot (x - conic.p0)) / nw)
        val y2D = Offset((y - conic.p0) dot conic.u, (y - conic.p0) dot conic.v)
        pointInPolygon2D(y2D, poly2D) && pointOnFiniteCylinder(state, cylinder, x)
    }
}

private fun buildInsideCone(state: MongeState, cone: ConicalSurface3D): ((Offset3D) -> Boolean)? {
    val conic = state.conics3D.find { it.id == cone.directrixId } ?: return null
    val apex = coneApex(state, cone) ?: return null
    val n = conic.u cross conic.v
    val denom = n dot (conic.p0 - apex)
    if (abs(denom) < 1e-9f) return null
    val poly2D = directrixSamples3D(conic).map { Offset((it - conic.p0) dot conic.u, (it - conic.p0) dot conic.v) }
    if (poly2D.size < 3) return null
    return { x ->
        val nd = n dot (x - apex)
        if (abs(nd) < 1e-9f) {
            false
        } else {
            val lambda = nd / denom                 // 0 v apexu, 1 v základně
            if (lambda < -1e-3f || lambda > 1f + 1e-3f) {
                false
            } else {
                // středová projekce z apexu na základnovou rovinu
                val y = apex + (x - apex) * (denom / nd)
                val y2D = Offset((y - conic.p0) dot conic.u, (y - conic.p0) dot conic.v)
                pointInPolygon2D(y2D, poly2D)
            }
        }
    }
}

// ───────────────────────── rozsah t podél paprsku (t = bod·dir) ─────────────────────────

private fun solidTRange(state: MongeState, solid: OcclusionSolid, dir: Offset3D): Pair<Float, Float>? {
    val pts: List<Offset3D> = when (solid) {
        is OcclusionSolid.Seg ->
            segmentSolidFaces(state, solid.solid)?.flatten()?.map { Offset3D(it.x, it.y, it.z) } ?: return null

        is OcclusionSolid.Sph -> {
            val c = sphereCenter(state, solid.sphere) ?: return null
            val tc = c dot dir
            return (tc - solid.sphere.radius) to (tc + solid.sphere.radius)
        }

        is OcclusionSolid.Cyl -> cylinderCharacteristicPoints(state, solid.cylinder) ?: return null
        is OcclusionSolid.Con -> {
            val apex = coneApex(state, solid.cone) ?: return null
            val conic = state.conics3D.find { it.id == solid.cone.directrixId } ?: return null
            listOf(apex) + directrixSamples3D(conic)
        }
        is OcclusionSolid.Sor, is OcclusionSolid.Ruled -> return null   // řeší se přímo v buildSolidProbe
    }
    return tRangeOfPoints(pts, dir)
}

private fun tRangeOfPoints(pts: List<Offset3D>, dir: Offset3D): Pair<Float, Float>? {
    if (pts.isEmpty()) return null
    var lo = Float.POSITIVE_INFINITY
    var hi = Float.NEGATIVE_INFINITY
    for (p in pts) {
        val t = p dot dir
        if (t < lo) lo = t
        if (t > hi) hi = t
    }
    if (!lo.isFinite() || !hi.isFinite() || hi <= lo) return null
    return lo to hi
}

private fun cylinderCharacteristicPoints(state: MongeState, cylinder: CylindricalSurface3D): List<Offset3D>? {
    val conic = state.conics3D.find { it.id == cylinder.directrixId } ?: return null
    val bottom = directrixSamples3D(conic)
    if (bottom.isEmpty()) return null
    // horní podstava: základnové vzorky posunuté na horní ořezovou rovinu podél tvořic
    val caps = monge.input.intersections.ops.cylinderCapPlanes(state, cylinder, conic)
    val top = caps.getOrNull(1)?.let { cap ->
        val w = cylinder.direction
        val denom = cap.normal dot w
        if (abs(denom) < 1e-9f) emptyList()
        else bottom.map { p -> p + w * ((cap.normal dot (cap.point - p)) / denom) }
    } ?: emptyList()
    return bottom + top
}

// ───────────────────────── vzorkování řídicí elipsy ─────────────────────────

/**
 * Body celé řídicí kuželosečky (elipsy) v 3D. Poloosy bereme přes [geometry.conics.computeEllipseAxes3D]
 * (robustní – conic.a/b jsou u directrix elips často null). Prázdné, když nejde o elipsu.
 */
internal fun directrixSamples3D(conic: ConicSection3D): List<Offset3D> {
    val axes = runCatching { geometry.conics.computeEllipseAxes3D(conic) }.getOrNull() ?: return emptyList()
    val au = axes.uRotated.normalize()
    val av = axes.vRotated.normalize()
    val a = axes.a
    val b = axes.b
    if (!a.isFinite() || !b.isFinite() || a < 1e-6f || b < 1e-6f) return emptyList()
    return (0 until DIRECTRIX_SAMPLES).map { i ->
        val t = 2f * kotlin.math.PI.toFloat() * i / DIRECTRIX_SAMPLES
        conic.p0 + au * (a * cos(t)) + av * (b * sin(t))
    }
}

// ───────────────────────── pomocné ─────────────────────────

internal fun sphereCenter(state: MongeState, sphere: SphereSurface3D): Offset3D? =
    state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId }?.let { Offset3D(it.x, it.y, it.z) }

internal fun coneApex(state: MongeState, cone: ConicalSurface3D): Offset3D? =
    state.sharedPoints3D.firstOrNull { it.id == cone.apexId }?.let { Offset3D(it.x, it.y, it.z) }
