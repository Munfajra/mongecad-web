package monge.input.segments

import draw.mongescreen.fills.segmentSolidFaces
import model.LineStyle
import model.Point3D
import model.classes.Segment2DAxo
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.SegmentSolid3D
import monge.input.axo.AxoRenderBasis
import monge.input.axo.createAxoRenderBasis
import state.MongeState
import kotlin.math.abs
import kotlin.math.sqrt

// Automatická viditelnost hran hranolů/jehlanů: hrana, která je v daném pohledu ZA stěnou,
// se nastaví na čárkovanou (LineStyle.Dashed), viditelná na plnou (LineStyle.Solid).
//
// Pro konvexní těleso platí přesně: hrana je skrytá právě tehdy, když jsou OBĚ sousední
// stěny odvrácené od pozorovatele (back-facing). Pro nekonvexní podstavu jde o aproximaci.
//
// Konvence pozorovatele (Mongeovo promítání, pozorovatel na kladné poloose dané osy):
//   půdorys  -> shora,   viditelné = větší z   (směr k pozorovateli +z)
//   nárys    -> zepředu, viditelné = větší y   (směr k pozorovateli +y)
//   bokorys  -> z boku,  viditelné = větší x   (směr k pozorovateli +x)
//   axo      -> směr pohledu odvozený z osy promítání (jádro projekce)

private const val VIS_EPS = 1e-4f

private data class V3(val x: Float, val y: Float, val z: Float)

private fun V3.dot(o: V3): Float = x * o.x + y * o.y + z * o.z
private operator fun V3.unaryMinus(): V3 = V3(-x, -y, -z)

private fun cross(a: V3, b: V3): V3 =
    V3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

private fun quantize(v: Float): Int = if (abs(v) < VIS_EPS) 0 else (v / VIS_EPS).toInt()
private fun vKey(p: Point3D): Triple<Int, Int, Int> = Triple(quantize(p.x), quantize(p.y), quantize(p.z))
private fun edgeKey(a: Point3D, b: Point3D): Set<Triple<Int, Int, Int>> = setOf(vKey(a), vKey(b))

private class FaceInfo(val outwardNormal: V3)

// Veřejné API ---------------------------------------------------------------

// Přepočítá a nastaví viditelnost hran tělesa pro všechny průměty. Nekomituje snapshot.
fun applySegmentSolidHiddenLines(state: MongeState, solid: SegmentSolid3D) {
    val faces = segmentSolidFaces(state, solid) ?: return
    if (faces.isEmpty()) return

    val solidCentroid = centroid(faces.flatten())
    val faceInfos = faces.map { face ->
        val raw = newellNormal(face)
        val faceCentroid = centroid(face)
        val toOutside = V3(
            faceCentroid.x - solidCentroid.x,
            faceCentroid.y - solidCentroid.y,
            faceCentroid.z - solidCentroid.z
        )
        FaceInfo(if (raw.dot(toOutside) >= 0f) raw else -raw)
    }

    // Hrana (klíč) -> indexy sousedních stěn.
    val edgeToFaces = HashMap<Set<Triple<Int, Int, Int>>, MutableList<Int>>()
    faces.forEachIndexed { faceIndex, face ->
        for (i in face.indices) {
            val key = edgeKey(face[i], face[(i + 1) % face.size])
            if (key.size == 2) edgeToFaces.getOrPut(key) { mutableListOf() }.add(faceIndex)
        }
    }

    val frontPudorys = faceInfos.map { it.outwardNormal.z > VIS_EPS }
    val frontNarys = faceInfos.map { it.outwardNormal.y > VIS_EPS }
    val frontBokorys = faceInfos.map { it.outwardNormal.x > VIS_EPS }

    val axoDir = axoViewDirection(state)
    val frontAxo = axoDir?.let { d -> faceInfos.map { it.outwardNormal.dot(d) > VIS_EPS } }

    val ids = solid.segmentIds3D.toHashSet()
    val edges = state.segments3D.filter { it.id in ids }

    val pudorysStyles = HashMap<String, LineStyle>()
    val narysStyles = HashMap<String, LineStyle>()
    val bokorysStyles = HashMap<String, LineStyle>()
    val axoStyles = HashMap<String, LineStyle>()

    for (edge in edges) {
        val adjacent = edgeToFaces[edgeKey(edge.start, edge.end)]
        pudorysStyles[edge.id] = styleFor(adjacent, frontPudorys)
        narysStyles[edge.id] = styleFor(adjacent, frontNarys)
        bokorysStyles[edge.id] = styleFor(adjacent, frontBokorys)
        if (frontAxo != null) axoStyles[edge.id] = styleFor(adjacent, frontAxo)
    }

    applyPudorysStyles(state, pudorysStyles)
    applyNarysStyles(state, narysStyles)
    applyBokorysStyles(state, bokorysStyles)
    if (frontAxo != null) applyAxoStyles(state, axoStyles)
}

// Přepočítá viditelnost a obnoví překreslení (pro tlačítko v selection info).
fun recomputeSegmentSolidVisibility(state: MongeState, solidId: String) {
    val solid = state.segmentSolids3D.firstOrNull { it.id == solidId } ?: return
    applySegmentSolidHiddenLines(state, solid)
    state.triggerRedraw++
}

// Pomocné ------------------------------------------------------------------

private fun styleFor(adjacentFaces: List<Int>?, frontFacing: List<Boolean>): LineStyle {
    if (adjacentFaces.isNullOrEmpty()) return LineStyle.Solid
    val anyFront = adjacentFaces.any { frontFacing.getOrNull(it) == true }
    return if (anyFront) LineStyle.Solid else LineStyle.Dashed
}

private fun centroid(points: List<Point3D>): V3 {
    if (points.isEmpty()) return V3(0f, 0f, 0f)
    var sx = 0f; var sy = 0f; var sz = 0f
    for (p in points) { sx += p.x; sy += p.y; sz += p.z }
    val n = points.size.toFloat()
    return V3(sx / n, sy / n, sz / n)
}

// Newellova metoda – robustní normála rovinného (i nekonvexního) polygonu.
private fun newellNormal(face: List<Point3D>): V3 {
    var nx = 0f; var ny = 0f; var nz = 0f
    for (i in face.indices) {
        val a = face[i]
        val b = face[(i + 1) % face.size]
        nx += (a.y - b.y) * (a.z + b.z)
        ny += (a.z - b.z) * (a.x + b.x)
        nz += (a.x - b.x) * (a.y + b.y)
    }
    return V3(nx, ny, nz)
}

// Směr "k pozorovateli" pro axonometrii: jádro projekce (vektor promítaný do nuly),
// orientovaný do kladného oktantu (oko v +,+,+), odkud se těleso v axu standardně pozoruje.
private fun axoViewDirection(state: MongeState): V3? {
    val basis: AxoRenderBasis = createAxoRenderBasis(
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        model = state.activeAxoModel
    ) ?: return null

    val row1 = V3(basis.ex.x, basis.ey.x, basis.ez.x)
    val row2 = V3(basis.ex.y, basis.ey.y, basis.ez.y)
    var v = cross(row1, row2)

    val len = sqrt(v.dot(v))
    if (len < 1e-6f) return null
    v = V3(v.x / len, v.y / len, v.z / len)

    return if (v.x + v.y + v.z < 0f) -v else v
}

// Aplikace stylů do projekčních seznamů (zachová runtime viditelnost showInAxo) -----

private fun Segment2DPudorys.keepVisibility(src: Segment2DPudorys) = apply {
    showInAxoInitial = src.showInAxo; showInAxo = src.showInAxo
}
private fun Segment2DNarys.keepVisibility(src: Segment2DNarys) = apply {
    showInAxoInitial = src.showInAxo; showInAxo = src.showInAxo
}
private fun Segment2DBokorys.keepVisibility(src: Segment2DBokorys) = apply {
    showInAxoInitial = src.showInAxo; showInAxo = src.showInAxo
}
private fun Segment2DAxo.keepVisibility(src: Segment2DAxo) = apply {
    showInAxoInitial = src.showInAxo; showInAxo = src.showInAxo
}

private fun applyPudorysStyles(state: MongeState, styles: Map<String, LineStyle>) {
    if (styles.isEmpty()) return
    state.segmentsPudorys.replaceAll { seg ->
        val style = (seg.parent?.id ?: seg.parentId)?.let { styles[it] }
        if (style != null && style != seg.localLineStyle) seg.copy(localLineStyle = style).keepVisibility(seg) else seg
    }
}

private fun applyNarysStyles(state: MongeState, styles: Map<String, LineStyle>) {
    if (styles.isEmpty()) return
    state.segmentsNarys.replaceAll { seg ->
        val style = (seg.parent?.id ?: seg.parentId)?.let { styles[it] }
        if (style != null && style != seg.localLineStyle) seg.copy(localLineStyle = style).keepVisibility(seg) else seg
    }
}

private fun applyBokorysStyles(state: MongeState, styles: Map<String, LineStyle>) {
    if (styles.isEmpty()) return
    state.segmentsBokorys.replaceAll { seg ->
        val style = (seg.parent?.id ?: seg.parentId)?.let { styles[it] }
        if (style != null && style != seg.localLineStyle) seg.copy(localLineStyle = style).keepVisibility(seg) else seg
    }
}

private fun applyAxoStyles(state: MongeState, styles: Map<String, LineStyle>) {
    if (styles.isEmpty()) return
    state.segmentsAxo.replaceAll { seg ->
        val style = (seg.parent?.id ?: seg.parentId)?.let { styles[it] }
        if (style != null && style != seg.localLineStyle) seg.copy(localLineStyle = style).keepVisibility(seg) else seg
    }
}
