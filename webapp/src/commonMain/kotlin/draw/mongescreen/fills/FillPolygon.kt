package draw.mongescreen.fills

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * 2D polygonová geometrie pro vektorový occlusion:
 * - průnik dvou konvexních polygonů (překryv siluet),
 * - řez (i nekonvexního) polygonu otevřenou lomenou čarou (průnikovou křivkou) na dvě části.
 * Vše v logických souřadnicích pohledu.
 */

internal fun polygonSignedArea(poly: List<Offset>): Float {
    var a = 0f
    for (i in poly.indices) {
        val p = poly[i]
        val q = poly[(i + 1) % poly.size]
        a += p.x * q.y - q.x * p.y
    }
    return a * 0.5f
}

internal fun ensureCcw(poly: List<Offset>): List<Offset> =
    if (polygonSignedArea(poly) < 0f) poly.asReversed() else poly

internal fun polygonCentroid(poly: List<Offset>): Offset {
    var cx = 0f; var cy = 0f; var a = 0f
    for (i in poly.indices) {
        val p = poly[i]; val q = poly[(i + 1) % poly.size]
        val cr = p.x * q.y - q.x * p.y
        a += cr
        cx += (p.x + q.x) * cr
        cy += (p.y + q.y) * cr
    }
    if (abs(a) < 1e-9f) {
        // degenerace → aritmetický průměr
        val n = poly.size.toFloat()
        return Offset(poly.sumOf { it.x.toDouble() }.toFloat() / n, poly.sumOf { it.y.toDouble() }.toFloat() / n)
    }
    a *= 0.5f
    return Offset(cx / (6f * a), cy / (6f * a))
}

/** Průnik dvou KONVEXNÍCH polygonů (Sutherland–Hodgman). [clip] musí být konvexní. Výsledek konvexní. */
internal fun convexPolygonIntersection(subject: List<Offset>, clip: List<Offset>): List<Offset> {
    if (subject.size < 3 || clip.size < 3) return emptyList()
    val clipCcw = ensureCcw(clip)
    var output = ensureCcw(subject)
    for (i in clipCcw.indices) {
        val a = clipCcw[i]
        val b = clipCcw[(i + 1) % clipCcw.size]
        if (output.size < 3) return emptyList()
        val input = output
        val next = ArrayList<Offset>(input.size + 1)
        for (j in input.indices) {
            val cur = input[j]
            val prv = input[(j - 1 + input.size) % input.size]
            val curIn = isLeft(a, b, cur)
            val prvIn = isLeft(a, b, prv)
            if (curIn) {
                if (!prvIn) lineIntersect(prv, cur, a, b)?.let { next.add(it) }
                next.add(cur)
            } else if (prvIn) {
                lineIntersect(prv, cur, a, b)?.let { next.add(it) }
            }
        }
        output = next
    }
    return if (output.size >= 3) output else emptyList()
}

private fun isLeft(a: Offset, b: Offset, p: Offset): Boolean =
    (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) >= -1e-6f

// Průsečík úsečky p→q s přímkou a→b.
private fun lineIntersect(p: Offset, q: Offset, a: Offset, b: Offset): Offset? {
    val r = Offset(q.x - p.x, q.y - p.y)
    val s = Offset(b.x - a.x, b.y - a.y)
    val denom = r.x * s.y - r.y * s.x
    if (abs(denom) < 1e-12f) return null
    val t = ((a.x - p.x) * s.y - (a.y - p.y) * s.x) / denom
    return Offset(p.x + r.x * t, p.y + r.y * t)
}

private data class BoundaryHit(val edge: Int, val t: Float, val point: Offset, val dist: Float)

// Nejbližší bod na hranici polygonu k bodu p (edge index + parametr + projekce + vzdálenost).
private fun closestOnBoundary(poly: List<Offset>, p: Offset): BoundaryHit {
    var best = BoundaryHit(-1, 0f, poly[0], Float.MAX_VALUE)
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[(i + 1) % poly.size]
        val ab = Offset(b.x - a.x, b.y - a.y)
        val len2 = ab.x * ab.x + ab.y * ab.y
        val t = if (len2 < 1e-12f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
        val proj = Offset(a.x + ab.x * t, a.y + ab.y * t)
        val d = (p - proj).getDistance()
        if (d < best.dist) best = BoundaryHit(i, t, proj, d)
    }
    return best
}

// První průsečík polopřímky {o + t·d, t>0} s hranicí polygonu.
private fun rayPolygonFirstHit(poly: List<Offset>, o: Offset, d: Offset): Offset? {
    var bestT = Float.MAX_VALUE
    var best: Offset? = null
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[(i + 1) % poly.size]
        val e = Offset(b.x - a.x, b.y - a.y)
        val denom = d.x * e.y - d.y * e.x
        if (abs(denom) < 1e-12f) continue
        val diff = Offset(a.x - o.x, a.y - o.y)
        val t = (diff.x * e.y - diff.y * e.x) / denom
        val s = (diff.x * d.y - diff.y * d.x) / denom
        if (t > 1e-4f && s in -1e-3f..1f + 1e-3f && t < bestT) {
            bestT = t; best = Offset(o.x + d.x * t, o.y + d.y * t)
        }
    }
    return best
}

/**
 * Zajistí, že konce oblouku [arc] leží na hranici [poly]: konec blízko hranice necháme (řez ho
 * přichytí), vzdálený konec protáhneme podél koncové tečny k hranici. Tím řez proběhne i když má
 * projektovaná křivka mezeru a sama k okraji nedosáhne.
 */
internal fun bridgeArcToBoundary(poly: List<Offset>, arc: List<Offset>, tol: Float): List<Offset> {
    if (arc.size < 2) return arc
    fun anchor(p: Offset, inner: Offset): Offset? {
        val h = closestOnBoundary(poly, p)
        if (h.dist <= tol) return null                 // už u hranice → řez přichytí p sám
        val dx = p.x - inner.x; val dy = p.y - inner.y
        val len = kotlin.math.hypot(dx, dy)
        if (len < 1e-9f) return h.point
        return rayPolygonFirstHit(poly, p, Offset(dx / len, dy / len)) ?: h.point
    }
    val start = anchor(arc.first(), arc[1])
    val end = anchor(arc.last(), arc[arc.size - 2])
    val out = ArrayList<Offset>(arc.size + 2)
    if (start != null) out.add(start)
    out.addAll(arc)
    if (end != null) out.add(end)
    return out
}

/**
 * Rozřízne polygon [poly] otevřenou lomenou čarou [arc] (její konce leží na/blízko hranice) na dvě
 * části. Vrací (face1, face2) nebo null, když konce nejdou spolehlivě přichytit k hranici.
 */
internal fun cutPolygonByArc(poly: List<Offset>, arc: List<Offset>, tol: Float): Pair<List<Offset>, List<Offset>>? {
    if (arc.size < 2 || poly.size < 3) return null
    val hs = closestOnBoundary(poly, arc.first())
    val he = closestOnBoundary(poly, arc.last())
    if (hs.dist > tol || he.dist > tol) return null
    if (hs.edge == he.edge && abs(hs.t - he.t) < 1e-4f) return null

    // vlož oba dělicí body do hranice (seřazené podle t na téže hraně)
    data class Split(val point: Offset, val tag: Int)  // 1 = start, 2 = end
    val baug = ArrayList<Split>(poly.size + 2)
    val startPt = hs.point
    val endPt = he.point
    for (i in poly.indices) {
        baug.add(Split(poly[i], 0))
        val onEdge = ArrayList<Pair<Float, Int>>()
        if (hs.edge == i) onEdge.add(hs.t to 1)
        if (he.edge == i) onEdge.add(he.t to 2)
        onEdge.sortBy { it.first }
        for ((_, tag) in onEdge) baug.add(Split(if (tag == 1) startPt else endPt, tag))
    }
    val idxS = baug.indexOfFirst { it.tag == 1 }
    val idxE = baug.indexOfFirst { it.tag == 2 }
    if (idxS < 0 || idxE < 0) return null

    fun boundaryWalk(from: Int, to: Int): List<Offset> {
        val out = ArrayList<Offset>()
        var i = from
        while (true) {
            out.add(baug[i].point)
            if (i == to) break
            i = (i + 1) % baug.size
        }
        return out
    }

    val arcInterior = if (arc.size > 2) arc.subList(1, arc.size - 1) else emptyList()
    val arcA = boundaryWalk(idxS, idxE)                    // po hranici z p0 do p1
    val arcB = boundaryWalk(idxE, idxS)                    // po hranici z p1 do p0

    val face1 = ArrayList<Offset>(arcA.size + arcInterior.size).apply {
        addAll(arcA)                                        // p0 … p1
        for (k in arcInterior.indices.reversed()) add(arcInterior[k])  // p1 → … → p0 po křivce
    }
    val face2 = ArrayList<Offset>(arcB.size + arcInterior.size).apply {
        addAll(arcB)                                        // p1 … p0
        addAll(arcInterior)                                 // p0 → … → p1 po křivce
    }
    if (face1.size < 3 || face2.size < 3) return null
    // odmítni degenerované/slivery (jinak vznikne tenký artefakt vybarvený špatně)
    val total = abs(polygonSignedArea(poly))
    if (total > 1e-9f) {
        val a1 = abs(polygonSignedArea(face1))
        val a2 = abs(polygonSignedArea(face2))
        if (a1 < total * 0.004f || a2 < total * 0.004f) return null
        // Bowtie guard: korektní řez zachovává plochu (a1+a2 ≈ total). Oblouk vybočující
        // z polygonu vyrábí self-intersecting plošky (laloky osmičky se v podepsané ploše
        // odečtou) → z nich vznikají překryvy protichůdných clipů (bílé „useknuté" pásy).
        if (a1 + a2 !in total * 0.95f..total * 1.05f) return null
    }
    return face1 to face2
}
