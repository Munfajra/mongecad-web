package monge.input.axo

import androidx.compose.ui.geometry.Offset
import model.classes.Line3D
import model.classes.Line3DProjectionAxo
import model.classes.OverlayAxoLine
import model.classes.OverlayAxoSegment
import model.classes.Point3DAxo
import model.classes.Segment2DAxo
import model.classes.lineParamAt
import model.Offset3D
import model.Point3D
import state.MongeState

fun projectPoint3DToAxoLocal(
    x: Float,
    y: Float,
    z: Float,
    basis: AxoRenderBasis
): Offset {
    return basis.ex * x + basis.ey * y + basis.ez * z
}

fun Point3DAxo.currentAxoLocal(basis: AxoRenderBasis): Offset {
    parent?.let { point3D ->
        return projectPoint3DToAxoLocal(point3D.x, point3D.y, point3D.z, basis)
    }

    return Offset(x, y)
}

fun Line3DProjectionAxo.currentAxoLineLocal(basis: AxoRenderBasis): Pair<Offset, Offset> {
    parent?.let { line3D ->
        val point = projectPoint3DToAxoLocal(
            line3D.start.x,
            line3D.start.y,
            line3D.start.z,
            basis
        )
        val direction = basis.ex * line3D.direction.x +
                basis.ey * line3D.direction.y +
                basis.ez * line3D.direction.z

        return point to direction
    }

    return p.currentAxoLocal(basis) to dir
}

fun OverlayAxoLine.currentAxoLineLocal(basis: AxoRenderBasis): Pair<Offset, Offset> {
    return when (this) {
        is Line3DProjectionAxo -> currentAxoLineLocal(basis)
        else -> point to direction
    }
}

/**
 * Rozsah parametru t, ve kterém přímka start + t·direction leží v 1. oktantu
 * (x ≥ 0, y ≥ 0, z ≥ 0), tj. mezi jejími stopníky. Meze mohou být ±nekonečno
 * (přímka rovnoběžná s průmětnou). Vrací null, pokud přímka 1. oktant mine.
 */
fun Line3D.octantTRange(): Pair<Float, Float>? {
    var tMin = Float.NEGATIVE_INFINITY
    var tMax = Float.POSITIVE_INFINITY
    val eps = 1e-6f

    val coords = listOf(
        start.x to direction.x,
        start.y to direction.y,
        start.z to direction.z
    )

    for ((p, d) in coords) {
        if (kotlin.math.abs(d) < eps) {
            if (p < -eps) return null
        } else {
            val t = -p / d
            if (d > 0f) tMin = maxOf(tMin, t) else tMax = minOf(tMax, t)
        }
    }

    return if (tMin <= tMax) tMin to tMax else null
}

/**
 * Efektivní rozsah ořezu axo průmětu o průmětny. Null znamená "neořezávat":
 * průmět bez parenta, osy, vypnutý přepínač, nebo přímka bez viditelné části
 * v 1. oktantu (ta se nechává celá, aby vůbec šla vidět).
 */
fun Line3DProjectionAxo.axoOctantClipRange(state: MongeState): Pair<Float, Float>? {
    val line3D = parent ?: return null
    val pid = line3D.id
    if (pid == "x_axis" || pid == "y_axis" || pid == "z_axis") return null
    val clip = clipToOctant ?: state.defaultClipAxoLineToOctant
    if (!clip) return null
    return line3D.octantTRange()
}

/**
 * Viditelný rozsah parametru t axo přímky (v parametrizaci `currentAxoLineLocal`):
 * průnik vlastního ořezu (customTrimRange) a ořezu o průmětny. Null = bez omezení.
 * Rozsah může vyjít prázdný (first > second) – přímka pak není vykreslená vůbec.
 */
fun OverlayAxoLine.axoVisibleTRange(state: MongeState): Pair<Float, Float>? {
    val line = this as? Line3DProjectionAxo ?: return null
    var tMin = Float.NEGATIVE_INFINITY
    var tMax = Float.POSITIVE_INFINITY
    line.axoOctantClipRange(state)?.let { (a, b) ->
        tMin = maxOf(tMin, minOf(a, b))
        tMax = minOf(tMax, maxOf(a, b))
    }
    line.customTrimRange?.let {
        tMin = maxOf(tMin, it.min)
        tMax = minOf(tMax, it.max)
    }
    if (tMin == Float.NEGATIVE_INFINITY && tMax == Float.POSITIVE_INFINITY) return null
    return tMin to tMax
}

/**
 * Leží bod na vykreslené části axo přímky? `linePoint`/`lineDir` musí být dvojice
 * z `currentAxoLineLocal` (příp. shodně posunutá, např. o basis.origin), ke které
 * se vztahuje i `point` – parametr t je pak srovnatelný s [axoVisibleTRange].
 */
fun OverlayAxoLine.axoVisibleRangeContains(
    state: MongeState,
    linePoint: Offset,
    lineDir: Offset,
    point: Offset,
    eps: Float = 1e-4f
): Boolean {
    val range = axoVisibleTRange(state) ?: return true
    val t = point.lineParamAt(linePoint, lineDir) ?: return false
    return t >= range.first - eps && t <= range.second + eps
}

fun Segment2DAxo.currentAxoSegmentLocal(basis: AxoRenderBasis): Pair<Offset, Offset> {
    parent?.let { segment3D ->
        return projectPoint3DToAxoLocal(
            segment3D.start.x,
            segment3D.start.y,
            segment3D.start.z,
            basis
        ) to projectPoint3DToAxoLocal(
            segment3D.end.x,
            segment3D.end.y,
            segment3D.end.z,
            basis
        )
    }

    return start.currentAxoLocal(basis) to end.currentAxoLocal(basis)
}

fun OverlayAxoSegment.currentAxoSegmentLocal(basis: AxoRenderBasis): Pair<Offset, Offset> {
    return when (this) {
        is Segment2DAxo -> currentAxoSegmentLocal(basis)
        else -> a to b
    }
}

/*
 * Promítání do axonometrie. Dřív v `draw/mongescreen/objects/axo/drawPoints.kt`
 * uprostřed kreslení bodů, i když je to čistá projekční matematika – volá to
 * i planelift, průniky a přímkové plochy.
 */
fun projectPudorysToAxoWorkspace(local: Offset, basis: AxoRenderBasis): Offset {
    return basis.origin + basis.ex * local.x + basis.ey * local.y
}

fun projectNarysToAxoWorkspace(local: Offset, basis: AxoRenderBasis): Offset {
    return basis.origin + basis.ex * local.x + basis.ez * local.y
}

fun projectBokorysToAxoWorkspace(local: Offset, basis: AxoRenderBasis): Offset {
    return basis.origin + basis.ey * local.x + basis.ez * local.y
}

fun projectPoint3DToAxo( point: Point3D, basis: AxoRenderBasis ): Offset {
    return basis.origin + basis.ex * point.x + basis.ey * point.y + basis.ez * point.z }

fun projectPoint3DToAxoLocal(
    point: Point3D,
    basis: AxoRenderBasis
): Offset {
    return basis.ex * point.x +
            basis.ey * point.y +
            basis.ez * point.z
}

fun projectDirection3DToAxo(
    dir: Offset3D,
    basis: AxoRenderBasis
): Offset {
    return basis.ex * dir.x +
            basis.ey * dir.y +
            basis.ez * dir.z
}

fun projectLine3DToAxoLocal(
    line: Line3D,
    basis: AxoRenderBasis
): Pair<Offset, Offset> {
    return projectPoint3DToAxoLocal(line.start, basis) to
            projectDirection3DToAxo(line.direction, basis)
}

