package monge.input.intersections.ops

import androidx.compose.ui.geometry.Offset
import model.Offset3D
import model.Point3D
import model.normalize
import kotlin.math.abs

/**
 * Pomocná rovinná geometrie pro průniky se Solidy (hranol/jehlan).
 *
 * Stěny tělesa získáváme jako seřazené 3D polygony přes
 * [draw.mongescreen.fills.segmentSolidFaces]. Pro oříznutí kuželoseček/úseček na stěnu
 * potřebujeme rovinné operace v rovině stěny – tady jsou.
 */

internal fun Point3D.toOffset3D(): Offset3D = Offset3D(x, y, z)

/** Orientovaná báze roviny stěny + projekce 3D bodů do jejích 2D souřadnic. */
internal class FacePlane(
    val origin: Offset3D,
    val u: Offset3D,        // jednotkový vektor v rovině
    val v: Offset3D,        // jednotkový, kolmý na u, v rovině
    val normal: Offset3D,   // jednotková normála
) {
    /** 2D souřadnice bodu v rovinné bázi (u, v) vzhledem k [origin]. */
    fun project(p: Offset3D): Offset {
        val r = p - origin
        return Offset(r dot u, r dot v)
    }

    /** Znaménková vzdálenost bodu od roviny (podél normály). */
    fun signedDistance(p: Offset3D): Float = (p - origin) dot normal
}

/**
 * Rovina proložená polygonem stěny. Normála přes Newellovu metodu (robustní i pro
 * mírně nerovinné nebo nekonvexní polygony). null, je-li polygon degenerovaný.
 */
internal fun facePlaneFromPolygon(poly: List<Offset3D>): FacePlane? {
    if (poly.size < 3) return null
    var nx = 0f; var ny = 0f; var nz = 0f
    for (i in poly.indices) {
        val cur = poly[i]
        val nxt = poly[(i + 1) % poly.size]
        nx += (cur.y - nxt.y) * (cur.z + nxt.z)
        ny += (cur.z - nxt.z) * (cur.x + nxt.x)
        nz += (cur.x - nxt.x) * (cur.y + nxt.y)
    }
    val normalRaw = Offset3D(nx, ny, nz)
    if (normalRaw.length() < 1e-9f) return null
    val n = normalRaw.normalize()
    val helper = if (abs(n.x) < 0.9f) Offset3D(1f, 0f, 0f) else Offset3D(0f, 1f, 0f)
    val u = (helper - n * (helper dot n)).normalize()
    val v = n cross u
    return FacePlane(poly[0], u, v, n)
}

/**
 * Leží 2D bod uvnitř polygonu (ray-casting)? Body na hranici (do [edgeTol]) bere
 * jako uvnitř – průsečíky přímek/kuželoseček s rovinou stěny totiž typicky padnou
 * přímo na hranu.
 */
internal fun pointInPolygon2D(p: Offset, poly: List<Offset>, edgeTol: Float = 1e-4f): Boolean {
    val n = poly.size
    if (n < 3) return false

    // hrana → uvnitř
    val scale = polygonScale(poly)
    val tol = edgeTol * scale
    for (i in 0 until n) {
        if (distancePointSegment(p, poly[i], poly[(i + 1) % n]) <= tol) return true
    }

    var inside = false
    var j = n - 1
    for (i in 0 until n) {
        val pi = poly[i]; val pj = poly[j]
        if (((pi.y > p.y) != (pj.y > p.y))) {
            val xCross = pi.x + (p.y - pi.y) / (pj.y - pi.y) * (pj.x - pi.x)
            if (p.x < xCross) inside = !inside
        }
        j = i
    }
    return inside
}

private fun polygonScale(poly: List<Offset>): Float {
    var maxd = 1f
    val c = poly.reduce { a, b -> a + b } / poly.size.toFloat()
    for (q in poly) maxd = maxOf(maxd, (q - c).getDistance())
    return maxd
}

private fun distancePointSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 < 1e-12f) return (p - a).getDistance()
    val t = (((p - a).x * ab.x + (p - a).y * ab.y) / len2).coerceIn(0f, 1f)
    val proj = a + ab * t
    return (p - proj).getDistance()
}

/**
 * Oříznutí 2D úsečky [a]→[b] na (i nekonvexní) polygon. Vrací parametrické
 * intervaly t ∈ [0,1], jejichž vnitřek leží uvnitř polygonu.
 */
internal fun clipSegmentToPolygon2D(a: Offset, b: Offset, poly: List<Offset>): List<Pair<Float, Float>> {
    // sortedSetOf je jen na JVM – na wasm stejný efekt přes set a seřazení na konci
    val breaks = mutableSetOf(0f, 1f)
    val n = poly.size
    val d = b - a
    for (i in 0 until n) {
        val e0 = poly[i]; val e1 = poly[(i + 1) % n]
        val t = segmentParamAtPolygonEdge(a, d, e0, e1) ?: continue
        if (t > 1e-6f && t < 1f - 1e-6f) breaks.add(t)
    }
    val sorted = breaks.sorted()
    val out = mutableListOf<Pair<Float, Float>>()
    for (k in 0 until sorted.size - 1) {
        val t0 = sorted[k]; val t1 = sorted[k + 1]
        val mid = (t0 + t1) * 0.5f
        if (pointInPolygon2D(a + d * mid, poly)) out.add(t0 to t1)
    }
    return mergeAdjacent(out)
}

/**
 * Parametrické intervaly s, kde nekonečná přímka {[q0] + s·[qd]} leží uvnitř polygonu.
 * Použito pro průnik Solid×Solid (průsečnice rovin dvou stěn oříznutá na obě stěny).
 */
internal fun clipLineToPolygon2D(q0: Offset, qd: Offset, poly: List<Offset>): List<Pair<Float, Float>> {
    val crossings = mutableListOf<Float>()
    val n = poly.size
    for (i in 0 until n) {
        val e0 = poly[i]; val e1 = poly[(i + 1) % n]
        val s = lineParamAtPolygonEdge(q0, qd, e0, e1) ?: continue
        crossings.add(s)
    }
    if (crossings.size < 2) return emptyList()
    crossings.sort()
    val out = mutableListOf<Pair<Float, Float>>()
    for (k in 0 until crossings.size - 1) {
        val s0 = crossings[k]; val s1 = crossings[k + 1]
        if (s1 - s0 < 1e-6f) continue
        val mid = (s0 + s1) * 0.5f
        if (pointInPolygon2D(q0 + qd * mid, poly)) out.add(s0 to s1)
    }
    return mergeAdjacent(out)
}

/** s, kde {q0 + s·qd} protne úsečku e0→e1 (r ∈ [0,1]); null pokud neprotíná. */
private fun lineParamAtPolygonEdge(q0: Offset, qd: Offset, e0: Offset, e1: Offset): Float? {
    val ed = e1 - e0
    val denom = qd.x * ed.y - qd.y * ed.x
    if (abs(denom) < 1e-9f) return null
    val diff = e0 - q0
    val s = (diff.x * ed.y - diff.y * ed.x) / denom
    val r = (diff.x * qd.y - diff.y * qd.x) / denom
    return if (r in -1e-4f..1f + 1e-4f) s else null
}

/** t ∈ param úsečky {a + t·d}, kde protne hranu e0→e1; null pokud neprotíná. */
private fun segmentParamAtPolygonEdge(a: Offset, d: Offset, e0: Offset, e1: Offset): Float? {
    val s = lineParamAtPolygonEdge(a, d, e0, e1) ?: return null
    return if (s in -1e-4f..1f + 1e-4f) s else null
}

private fun mergeAdjacent(intervals: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (intervals.isEmpty()) return intervals
    val sorted = intervals.sortedBy { it.first }
    val out = mutableListOf(sorted.first())
    for (i in 1 until sorted.size) {
        val last = out.last()
        val cur = sorted[i]
        if (cur.first - last.second < 1e-5f) out[out.size - 1] = last.first to maxOf(last.second, cur.second)
        else out.add(cur)
    }
    return out
}
