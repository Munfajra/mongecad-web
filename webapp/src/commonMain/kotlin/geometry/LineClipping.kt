package geometry

import androidx.compose.ui.geometry.Offset
import model.VisibleQuad


/*
 * Ořez nekonečné přímky na viditelný čtyřúhelník.
 * Dřív v `draw/mongescreen/objects/axo/drawLines.kt`, i když to volá
 * i snapping, PDF export a náhledy konstrukce – s axonometrií nesouvisí.
 */
fun clipInfiniteLineToQuad(
    point: Offset,
    dir: Offset,
    quad: VisibleQuad
): Pair<Offset, Offset>? {
    val ts = quad.edges.mapNotNull { (a, b) ->
        intersectInfiniteLineWithSegmentParam(
            linePoint = point,
            lineDir = dir,
            segA = a,
            segB = b
        )
    }

    if (ts.size < 2) return null

    val tMin = ts.minOrNull() ?: return null
    val tMax = ts.maxOrNull() ?: return null

    val pA = point + dir * tMin
    val pB = point + dir * tMax

    return pA to pB
}




fun intersectInfiniteLineWithSegmentParam(
    linePoint: Offset,
    lineDir: Offset,
    segA: Offset,
    segB: Offset
): Float? {
    val segDir = segB - segA
    val denom = cross(lineDir, segDir)
    if (kotlin.math.abs(denom) < 1e-6f) return null

    val diff = segA - linePoint

    val t = cross(diff, segDir) / denom
    val u = cross(diff, lineDir) / denom

    if (u < -1e-6f || u > 1f + 1e-6f) return null

    return t
}

// snapAxo web nemá, takže 2D kříž je tady.
private fun cross(a: Offset, b: Offset): Float = a.x * b.y - a.y * b.x
