package monge.input.meridian

import geometry.sampleCatmullRom
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import serialization.commitSnapshot
import model.*
import model.classes.*
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import utils.UUID
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.collections.asReversed
import kotlin.collections.drop
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.find
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.indexOfFirst
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.last
import kotlin.collections.lastIndex
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.collections.maxOf
import kotlin.collections.mutableListOf
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.collections.toMutableList
import kotlin.math.abs
import kotlin.math.sqrt


private fun dist2(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx*dx + dy*dy
}

private fun nearestIndex(poly: List<Offset>, p: Offset): Int {
    var bestI = 0
    var bestD = Float.POSITIVE_INFINITY
    for (i in poly.indices) {
        val d = dist2(poly[i], p)
        if (d < bestD) { bestD = d; bestI = i }
    }
    return bestI
}

private fun sliceCyclic(poly: List<Offset>, i0: Int, i1: Int): List<Offset> {
    // vrátí cestu i0 -> i1 po forward směru přes konec
    if (poly.isEmpty()) return poly
    val out = ArrayList<Offset>()
    var i = i0
    while (true) {
        out += poly[i]
        if (i == i1) break
        i = (i + 1) % poly.size
        // pojistka proti nekonečnu:
        if (out.size > poly.size + 2) break
    }
    return out
}

fun trimConicPolylineByState(
    state: MongeState,
    conicId: String,
    full: List<Offset>
): List<Offset> {

    // 1) elipsa ořez: ellipseArcEnds
    state.ellipseArcEnds[conicId]?.let { (a, b) ->
        val iA = nearestIndex(full, a)
        val iB = nearestIndex(full, b)

        val mode = state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST
        val forward = sliceCyclic(full, iA, iB)
        val backward = sliceCyclic(full, iB, iA)

        return when (mode) {
            ArcMode.SHORTEST -> if (forward.size <= backward.size) forward else backward.asReversed()
            ArcMode.LONGEST  -> if (forward.size >= backward.size) forward else backward.asReversed()
            ArcMode.CCW-> forward
            ArcMode.CW -> backward.asReversed()
        }
    }

    // 2) parabola ořez: parabolaArcEnds
    state.parabolaArcEnds[conicId]?.let { (a, b) ->
        val iA = nearestIndex(full, a)
        val iB = nearestIndex(full, b)
        val lo = minOf(iA, iB)
        val hi = maxOf(iA, iB)
        return full.subList(lo, hi + 1)
    }

    // 3) hyperbola ořez: vezmi větev 1 nebo 2 (pro rotační objekt ti stačí jedna)
    state.hyperbolaArcBranch1[conicId]?.let { (a, b) ->
        val iA = nearestIndex(full, a)
        val iB = nearestIndex(full, b)
        val lo = minOf(iA, iB)
        val hi = maxOf(iA, iB)
        return full.subList(lo, hi + 1)
    }
    state.hyperbolaArcBranch2[conicId]?.let { (a, b) ->
        val iA = nearestIndex(full, a)
        val iB = nearestIndex(full, b)
        val lo = minOf(iA, iB)
        val hi = maxOf(iA, iB)
        return full.subList(lo, hi + 1)
    }

    // default: bez ořezu
    return full
}


data class OrderedPathResult(
    val ordered: List<MeridianPieceNarys>,
    val polyline: List<Offset>  // vzorkovaná spojitá polyline (XZ)
)
private fun lexLessZx(a: Offset, b: Offset): Boolean {
    // v XZ prostoru: a.y je z, a.x je x
    if (a.y < b.y) return true
    if (a.y > b.y) return false
    return a.x < b.x
}
fun pickDeterministicStartIndex(remaining: List<MeridianPieceNarys>): Int {
    var bestIdx = 0
    var bestPoint = minPointZx(remaining[0].startXZ(), remaining[0].endXZ())

    for (i in 1 until remaining.size) {
        val p = minPointZx(remaining[i].startXZ(), remaining[i].endXZ())
        if (lexLessZx(p, bestPoint)) {
            bestPoint = p
            bestIdx = i
        }
    }
    return bestIdx
}
private fun minPointZx(a: Offset, b: Offset): Offset =
    if (lexLessZx(a, b)) a else b
fun buildMeridianPathFromSelected(
    state: MongeState,
    selected: List<MeridianPieceNarys>,
    joinTol: Float,
    maxError: Float
): OrderedPathResult {

    if (selected.isEmpty()) return OrderedPathResult(emptyList(), emptyList())
    val remaining = selected.toMutableList()

    fun closeEnough(a: Offset, b: Offset) = dist2(a, b) <= joinTol * joinTol

    val startIdx = pickDeterministicStartIndex(remaining)

    val ordered = ArrayDeque<MeridianPieceNarys>()
    ordered.addLast(remaining.removeAt(startIdx))

    // přidávej, dokud jde něco napojit
    while (remaining.isNotEmpty()) {
        val head = ordered.first().startXZ()
        val tail = ordered.last().endXZ()

        // najdi nejlepší kandidát na tail i head (nejmenší dist2)
        var bestIdx = -1
        var bestAttachToTail = true
        var bestFlip = false
        var bestD2 = Float.POSITIVE_INFINITY

        for (i in remaining.indices) {
            val p = remaining[i]

            val s = p.startXZ()
            val e = p.endXZ()

            // napojení na tail
            val d2_ts = dist2(s, tail)
            if (d2_ts <= joinTol * joinTol && d2_ts < bestD2) {
                bestD2 = d2_ts
                bestIdx = i
                bestAttachToTail = true
                bestFlip = false // start->end sedí
            }
            val d2_te = dist2(e, tail)
            if (d2_te <= joinTol * joinTol && d2_te < bestD2) {
                bestD2 = d2_te
                bestIdx = i
                bestAttachToTail = true
                bestFlip = true // otočit, aby start byl u tail
            }

            // napojení na head (pozor: musí sedět na start řetězce, takže nový kus musí končit v head)
            val d2_hs = dist2(s, head)
            if (d2_hs <= joinTol * joinTol && d2_hs < bestD2) {
                bestD2 = d2_hs
                bestIdx = i
                bestAttachToTail = false
                bestFlip = true // aby end byl u head (tj. kus otočíme)
            }
            val d2_he = dist2(e, head)
            if (d2_he <= joinTol * joinTol && d2_he < bestD2) {
                bestD2 = d2_he
                bestIdx = i
                bestAttachToTail = false
                bestFlip = false // end je u head už teď
            }
        }

        if (bestIdx < 0) break // nespojitý výběr nebo tolerance moc malá

        val next = remaining.removeAt(bestIdx)
        val oriented = if (bestFlip) next.reversed() else next

        if (bestAttachToTail) ordered.addLast(oriented) else ordered.addFirst(oriented)
    }

    // vzorkuj a spoj do jedné polyline
    val poly = ArrayList<Offset>()
    ordered.forEachIndexed { i, piece ->
        val pts = piece.samplePolylineXZ(state, maxError)
        if (pts.isEmpty()) return@forEachIndexed

        if (i == 0) {
            poly += pts
        } else {
            if (poly.isNotEmpty() && dist2(poly.last(), pts.first()) <= joinTol * joinTol) {
                poly += pts.drop(1)
            } else {
                poly += pts
            }
        }
    }

    return OrderedPathResult(ordered.toList(), poly)
}
private fun toggleInList(list: SnapshotStateList<String>, id: String) {
    if (list.contains(id)) list.remove(id) else list.add(id)
}
fun startSolidOfRevolutionTool(state: MongeState) {
    state.drawobjects = Mongeobjects.SOLID_OF_REVOLUTION
    setProjectionPhase("rev_axis_select", state)

    state.selectedRevolutionAxis3D = null
    state.selectedMeridianNarysIds.clear()

    state.previewRevolutionRmax = null
    state.previewRevolutionNecks = emptyList()
}
private fun isAxisVertical(line: Line3D, eps: Float = 1e-3f): Boolean {
    val d = line.direction.normalize()
    return kotlin.math.abs(d.x) < eps && kotlin.math.abs(d.y) < eps
}

fun finalizeSolidOfRevolution(state: MongeState) {
    val axis = state.selectedRevolutionAxis3D ?: return
    val selectedIds = state.selectedMeridianNarysIds.toList()
    if (selectedIds.isEmpty()) return

    val pieces = resolveSelectedMeridianPiecesNarys(state, selectedIds)


    // guard: osa ⟂ půdorysně
    val dir = axis.direction.normalize()
    if (abs(dir.x) > 1e-3f || abs(dir.y) > 1e-3f) {
        println("❌ Osa není kolmá na půdorysnu (x/y se mění). x=${dir.x} y=${dir.y}")
        return
    }

    val axisX0 = axis.start.x
    val joinTol = 1f
    val maxError = joinTol * 0.05f
    val axisEps = joinTol * 0.75f
    val circleGeomTol = joinTol * 0.5f

    fun rOf(p: Offset): Float = kotlin.math.abs(p.x - axisX0)

    data class ParallelCircleGeom(val z: Float, val r: Float)

    // ✅ topologické konce meridiánu
    val pathRes = buildMeridianPathFromSelected(state, pieces, joinTol, maxError)
    val polyXZ = pathRes.polyline
    if (polyXZ.size < 2) return

    val kinks = findKinksAtPieceJointsXZ(
        ordered = pathRes.ordered,
        axisX0 = axisX0,
        joinTol = joinTol,
        minTurnDeg = 8f
    )
    println("kinks=${kinks.map { "K@z=${it.zLevel} r=${it.r} turn=${it.turnDeg}" }}")

    val endpoints = findMeridianEndpointsXZ(pathRes.ordered)
    println("endPts=$endpoints radii=${endpoints.map { rOf(it) }}")

    // ✅ obrysový bod analyticky (pro arc přesně), ne ze samplu
    val pMax = farthestPointFromAxisOnMeridianXZ(pathRes.ordered, axisX0)
    val rMax = rOf(pMax)
    if (rMax <= axisEps) return

    // ✅ koncový bod meridiánu mimo osu (jen log pro debug)
    val pEnd = endpointWithMinRadius(pathRes.ordered, axisX0)
    val rEnd = rOf(pEnd)

    val solidId = UUID.randomUUID().toString()
    val mirroredIds = createMirroredMeridianObjectsNarys(state, axisX0, selectedIds)

    val createdCircleIdsP = mutableListOf<String>()
    val createdCircleIdsN = mutableListOf<String>()
    val addedParallelCircles = mutableListOf<ParallelCircleGeom>()

    fun alreadyHasParallelCircle(z: Float, r: Float): Boolean {
        return addedParallelCircles.any { existing ->
            abs(existing.z - z) <= circleGeomTol &&
                    abs(existing.r - r) <= circleGeomTol
        }
    }

    fun addParallelCircle(
        z: Float,
        r: Float,
        label: String,
        isHelp: Boolean = true,
        wMul: Float = 1f,
        style: LineStyle = LineStyle.Solid,
        width: Float = state.currentLineStyleSettings.strokeWidth * wMul,
        color: Color = state.currentLineStyleSettings.color
    ) {
        if (r <= axisEps) {
            println("↩️ Přeskakuji kružnici u osy: z=$z r=$r label=$label")
            return
        }

        if (alreadyHasParallelCircle(z, r)) {
            println("↩️ Duplicitní kružnice přeskočena: z=$z r=$r label=$label")
            return
        }

        addedParallelCircles += ParallelCircleGeom(z, r)

        val (pud, nar) = addPerpCircleToAxis_WithInputs(
            state = state,
            axis = axis,
            zLevel = z,
            radius = r,
            rawName = label,
            isHelpCircle = isHelp,
            styleOverridePud = LineStyle.Solid,
            styleOverrideNar= LineStyle.Dashed,
            widthOverride = width,
            colorOverride = color
        )
        createdCircleIdsP += pud.id
        createdCircleIdsN += nar.id
    }

    fun addRimIfOpen(p: Offset, label: String) {
        val r = rOf(p)
        if (r <= axisEps) return
        addParallelCircle(
            z = p.y,
            r = r,
            label = label,
            isHelp = true
        )
    }

    // ✅ koncové hrany (pokud je meridián otevřený)
    if (endpoints.size == 2) {
        val (pStart, pEnd2) = canonicalOrderEndpoints(endpoints[0], endpoints[1])
        addRimIfOpen(pStart, "Hrana1")
        addRimIfOpen(pEnd2, "Hrana2")
    } else {
        println("⚠️ endpoints.size=${endpoints.size} endpoints=$endpoints")
    }

    // ✅ rovnoběžky v lomech
    kinks.forEach { k ->
        addParallelCircle(
            z = k.zLevel,
            r = k.r,
            label = "Rovnoběžka",
            isHelp = true
        )
    }

    // ✅ obrys
    addParallelCircle(
        z = pMax.y,
        r = rMax,
        label = "Obrys",
        isHelp = true,
        wMul = 1f
    )

    val solid = SolidOfRevolutionNarys(
        id = solidId,
        axisLine3DId = axis.id,
        meridianIdsNarys = selectedIds,
        mirroredMeridianIdsNarys = mirroredIds,
        rMax = rMax,
        neckRadii = emptyList(),
        circleIdsPudorys = createdCircleIdsP,
        circleIdsNarys = createdCircleIdsN,
        sampledMeridianPolylineXZ = polyXZ,
        creationIndex = allocIndex(state),
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    state.solidsOfRevolutionNarys += solid

    val segmentsNarys = state.segmentsNarys.filter { it.id in selectedIds || it.id in mirroredIds }
    segmentsNarys.forEach { segment ->
        segment.localColor = solid.color
        segment.localStrokeWidth = solid.strokeWidth
    }

    val helpsegmentsNarys = state.helpSegmentsNarys.filter { it.id in selectedIds || it.id in mirroredIds }
    helpsegmentsNarys.forEach { segment ->
        segment.localColor = solid.color
        segment.localStrokeWidth = solid.strokeWidth
    }

    val arcsNarys = state.arcsNarys.filter { it.id in selectedIds || it.id in mirroredIds }
    arcsNarys.forEach { arc ->
        arc.strokeWidth = solid.strokeWidth
        arc.color = solid.color
    }

    commitSnapshot(state)

    state.selectedRevolutionAxis3D = null
    state.selectedMeridianNarysIds.clear()
    state.drawobjects = Mongeobjects.NONE
    resetStavu(state)
    setProjectionPhase("narys_start", state)

    val pA = pathRes.ordered.first().startXZ()
    val pB = pathRes.ordered.last().endXZ()
    println("end candidates: A=$pA rA=${rOf(pA)}  B=$pB rB=${rOf(pB)}  chosen=$pEnd rEnd=$rEnd")
}
fun resolveSelectedMeridianPiecesNarys(
    state: MongeState,
    selectedIds: List<String>
): List<MeridianPieceNarys> {
    val out = mutableListOf<MeridianPieceNarys>()

    selectedIds.forEach { id ->
        val segments = state.segmentsNarys+state.helpSegmentsNarys
        segments.firstOrNull { it.id == id }?.let { out += SegmentPieceNarys(it, id) }
        state.arcsNarys.firstOrNull { it.id == id }?.let { out += ArcPieceNarys(it) }
        // conics později
    }
    return out
}

private fun mirrorXZ(p: Offset, axisX0: Float): Offset =
    Offset(2f * axisX0 - p.x, p.y)

fun addMirroredSegmentNarys(
    seg: SegmentsNarys,
    axisX0: Float,
    state: MongeState
): String {

    val a = Offset(seg.start.x, seg.start.z)
    val b = Offset(seg.end.x, seg.end.z)

    val am = mirrorXZ(a, axisX0)
    val bm = mirrorXZ(b, axisX0)
    val segn = Segment2DNarys(
        start = Point3DNarys(am.x, /* y? */ am.y, name = seg.name ?: ""),
        end = Point3DNarys(bm.x,  bm.y, name = seg.name ?: ""),
        name = "",
        localColor =  Color.DarkGray,
        localLineStyle = LineStyle.Dashed,
        localStrokeWidth = seg.strokeWidth,
        creationIndex = allocIndex(state))
    val hseg = HelpSegmentNarys(
        start = Point3DNarys(am.x, /* y? */ am.y, name = seg.name ?: ""), // uprav ctor Point3DNarys
        end = Point3DNarys(bm.x,  bm.y, name = seg.name ?: ""),
        name = "",
        localColor =  Color.DarkGray,
        localLineStyle = LineStyle.Dashed,
        localStrokeWidth = seg.strokeWidth,
        creationIndex = allocIndex(state)
    )
    val s = if (seg is Segment2DNarys){segn}else{ hseg}
    when(seg){
        is Segment2DNarys -> {
            state.segmentsNarys += segn
            return segn.id}
        is HelpSegmentNarys -> {
            state.helpSegmentsNarys += hseg
            hseg.id
            return hseg.id


        }

        else -> {
        }
    }
    return s.id
}

private fun mirrorAngleRad(theta: Float): Float {
    val pi = kotlin.math.PI.toFloat()
    return Arc2DNarys.norm(pi - theta)   // norm na 0..2π
}

fun addMirroredArcNarys(
    arc: Arc2DNarys,
    axisX0: Float,
    state: MongeState
): String {

    // 1) mirror center
    val cxm = 2f * axisX0 - arc.center.x
    val czm = arc.center.z

    // 2) mirror absolute angles
    val sM = mirrorAngleRad(arc.startRad)
    val eM = mirrorAngleRad(arc.endRad)

    // 3) dir flips under mirror
    val mirrored = arc.copy(
        center = arc.center.copy(x = cxm, z = czm),
        startRad = sM,
        endRad   = eM,
        clockwise = !arc.clockwise,

        name = arc.name + "_m",
        color = Color.DarkGray,
        lineStyle = LineStyle.Dashed,
        strokeWidth = arc.strokeWidth,
        id = UUID.randomUUID().toString(),
        creationIndex = allocIndex(state)
    )

    state.arcsNarys += mirrored
    state.triggerRedraw++
    return mirrored.id
}
fun createMirroredMeridianObjectsNarys(
    state: MongeState,
    axisX0: Float,
    selectedIds: List<String>
): List<String> {

    val createdIds = mutableListOf<String>()

    selectedIds.forEach { id ->
        // segment?
        state.allSegmentsNarys.firstOrNull { it.id == id }?.let { seg ->
            createdIds += addMirroredSegmentNarys(seg, axisX0, state)
            return@forEach
        }

        // arc?
        state.arcsNarys.firstOrNull { it.id == id }?.let { arc ->
            createdIds += addMirroredArcNarys(arc, axisX0, state)
            return@forEach
        }

        // conic později
    }

    return createdIds
}

fun addPerpCircleToAxis_WithInputs(
    state: MongeState,
    axis: Line3D,          // start/end
    zLevel: Float,         // 3D z
    radius: Float,
    rawName: String,
    isHelpCircle: Boolean = true,
    styleOverridePud: LineStyle? = null,
    styleOverrideNar: LineStyle? = null,
    widthOverride: Float? = null,
    colorOverride: Color? = null
): Pair<ConicSectionPudorys, ConicSectionNarys> {

    val axisDir = Offset3D(
        axis.direction.x,
        axis.direction.y,
        axis.direction.z
    ).normalize()

    val center3D = Offset3D(axis.start.x, axis.start.y, zLevel)

    val arbitrary = if (kotlin.math.abs(axisDir.x) < 0.9f) Offset3D(1f, 0f, 0f) else Offset3D(0f, 1f, 0f)
    val u = (arbitrary cross axisDir).normalize()
    val v = (axisDir cross u).normalize()

    val settings = state.currentLineStyleSettings


    val col = colorOverride ?: settings.color
    val w   = widthOverride ?: settings.strokeWidth
    val stp = styleOverridePud ?: settings.style
    val stn = styleOverrideNar?: settings.style

    val mat = Matrix3x3.fromCoefficients(
        a = 1f / (radius * radius),
        b = 0f,
        c = 1f / (radius * radius),
        d = 0f,
        e = 0f,
        f = -1f
    )

    val conic3D = ConicSection3D(
        p0 = center3D,
        u = u,
        v = v,
        matrix = mat,
        rawName = rawName,
        color = col,
        strokeWidth = w,
        lineStyle = settings.style,
        creationIndex = allocIndex(state)
    )

    val coeffsP = Matrix3x3.toCoefficients(conic3D.projectToXY())
    val coeffsN = Matrix3x3.toCoefficients(conic3D.projectToXZ())

    val pud = ConicSectionPudorys(
        a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
        d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
        rawName = rawName,
        localColor = col,
        strokeWidth = w,
        lineStyle = stp,
        parent = conic3D,
        isHelpCircle = isHelpCircle,
        creationIndex = allocIndex(state)
    )

    val nar = ConicSectionNarys(
        a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
        d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
        rawName = rawName,
        localColor = col,
        strokeWidth = w,
        lineStyle = stn,
        parent = conic3D,
        isHelpCircle = isHelpCircle,
        creationIndex = allocIndex(state)
    )

    // ✅ stejné input body jako v tvé funkci
    val pt1 = center3D - u * radius
    val pt2 = center3D + u * radius
    val pt3 = center3D + v * radius

    val p1  = Offset(pt1.x, pt1.y)
    val p2  = Offset(pt2.x, pt2.y)
    val p3  = Offset(pt3.x, pt3.y)

    val axisX0 = axis.start.x
    val z = zLevel

    val p1n = Offset(axisX0 - radius, -z)
    val p2n = Offset(axisX0 + radius, -z)
// třetí bod dej klidně střed (nebo zase p1n), renderer si při degeneraci vezme úsečku
    val p3n = Offset(axisX0, -z)

    state.conics3D += conic3D

    // ✅ DŮLEŽITÉ: kreslení bere conicsPudorys, snapping chce circlesPudorys → dej do obou
    state.conicsPudorys += pud


    state.conicsNarys += nar

    state.conicInputPointsPudorys[pud.id] = Triple(p1, p2, p3)
    state.conicInputPointsNarys[nar.id] = Triple(p1n, p2n, p3n)

    state.triggerRedraw++
    return pud to nar
}

fun farthestPointFromAxisOnArcXZ(arc: Arc2DNarys, axisX0: Float): Offset {
    fun ptAtRad(a: Float): Offset {
        val x = arc.center.x + arc.radius * kotlin.math.cos(a)
        val z = arc.center.z + arc.radius * kotlin.math.sin(a)
        return Offset(x, z)
    }
    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)

    val s0 = Arc2DNarys.norm(arc.startRad)
    val sweep = arc.sweepSigned()
    val e0 = Arc2DNarys.norm(s0 + sweep)

    // kandidáti: start/end + x-extrema (0, π) pokud leží na zvoleném oblouku
    fun onArc(a: Float): Boolean {
        val A = Arc2DNarys.norm(a)
        val S = s0
        val E = e0
        return if (sweep >= 0f) {
            // CCW S->E
            if (S <= E) (A in S..E) else (A >= S || A <= E)
        } else {
            // CW S->E == CCW E->S
            if (E <= S) (A in E..S) else (A >= E || A <= S)
        }
    }

    val candidates = mutableListOf<Float>()
    candidates += s0
    candidates += e0
    val a0 = 0f
    val aPi = kotlin.math.PI.toFloat()
    if (onArc(a0)) candidates += a0
    if (onArc(aPi)) candidates += aPi

    var best = ptAtRad(candidates.first())
    var bestR = rOf(best)
    for (a in candidates.drop(1)) {
        val p = ptAtRad(a)
        val rr = rOf(p)
        if (rr > bestR) { best = p; bestR = rr }
    }
    return best
}
fun farthestPointFromAxisOnMeridianXZ(
    ordered: List<MeridianPieceNarys>,
    axisX0: Float
): Offset {
    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)

    var best = ordered.first().startXZ()
    var bestR = rOf(best)

    ordered.forEach { piece ->
        val candidates: List<Offset> = when (piece) {
            is ArcPieceNarys -> listOf(farthestPointFromAxisOnArcXZ(piece.arc, axisX0))
            else -> listOf(piece.startXZ(), piece.endXZ()) // segment: stačí konce (pro |x-axis|)
        }

        candidates.forEach { p ->
            val rr = rOf(p)
            if (rr > bestR) {
                bestR = rr
                best = p
            }
        }
    }
    return best
}

private fun endpointWithMinRadius(
    ordered: List<MeridianPieceNarys>,
    axisX0: Float
): Offset {
    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)
    val pA = ordered.first().startXZ()
    val pB = ordered.last().endXZ()
    return if (rOf(pA) <= rOf(pB)) pA else pB
}
private fun findMeridianEndpointsXZ(
    ordered: List<MeridianPieceNarys>,
): List<Offset> {

    data class Cluster(var repr: Offset, var count: Int)

    val tol2 = 1f
    val clusters = mutableListOf<Cluster>()

    fun add(p: Offset) {
        // najdi existující cluster v toleranci
        val idx = clusters.indexOfFirst { (it.repr - p).getDistanceSquared() <= tol2 }
        if (idx >= 0) {
            clusters[idx].count += 1
            // repr nech klidně první, nebo můžeš průměrovat
        } else {
            clusters += Cluster(p, 1)
        }
    }

    ordered.forEach { piece ->
        add(piece.startXZ())
        add(piece.endXZ())
    }

    // konce jsou ty s count==1
    return clusters.filter { it.count == 1 }.map { it.repr }
}

data class ProfileKink(
    val zLevel: Float,
    val r: Float,
    val point: Offset,     // (x,z)
    val turnDeg: Float
)

private fun findKinksAtPieceJointsXZ(
    ordered: List<MeridianPieceNarys>,
    axisX0: Float,
    joinTol: Float,
    minTurnDeg: Float = 8f,          // 5–12° typicky
    axisEps: Float = joinTol * 0.75f
): List<ProfileKink> {
    if (ordered.size < 2) return emptyList()

    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)

    fun norm(v: Offset): Offset {
        val len = v.getDistance()
        return if (len < 1e-6f) Offset.Zero else Offset(v.x / len, v.y / len)
    }
    fun dot(a: Offset, b: Offset) = a.x * b.x + a.y * b.y
    fun tangentAtEnd(piece: MeridianPieceNarys): Offset =
        when (piece) {
            is SegmentPieceNarys -> norm(piece.endXZ() - piece.startXZ())
            is ArcPieceNarys     -> piece.tangentAtEndXZ()
            else -> norm(piece.endXZ() - piece.startXZ())
        }

    fun tangentAtStart(piece: MeridianPieceNarys): Offset =
        when (piece) {
            is SegmentPieceNarys -> norm(piece.endXZ() - piece.startXZ())
            is ArcPieceNarys     -> piece.tangentAtStartXZ()
            else -> norm(piece.endXZ() - piece.startXZ())
        }

    val cosTol = kotlin.math.cos(minTurnDeg.toDouble() * kotlin.math.PI / 180.0).toFloat()

    val out = mutableListOf<ProfileKink>()

    for (i in 0 until ordered.lastIndex) {
        val a = ordered[i]
        val b = ordered[i + 1]

        val joint = a.endXZ() // předpoklad: buildMeridianPathFromSelected je řadí spojitě
        val r = rOf(joint)
        if (r <= axisEps) continue

        val t1 = tangentAtEnd(a)
        val t2 = tangentAtStart(b)
        if (t1 == Offset.Zero || t2 == Offset.Zero) continue

        val c = dot(t1, t2).coerceIn(-1f, 1f)
        if (c >= cosTol) continue // skoro hladké napojení

        val turn = (kotlin.math.acos(c).toDouble() * 180.0 / kotlin.math.PI).toFloat()
        out += ProfileKink(
            zLevel = joint.y,
            r = r,
            point = joint,
            turnDeg = turn
        )
    }

    return out
}
private fun ArcPieceNarys.tangentAtEndXZ(): Offset {
    val sweep = arc.sweepSigned()
    val endAng = Arc2DNarys.norm(arc.startRad + sweep)

    // poloměrový vektor (v lokálních XZ souřadnicích)
    val rx = kotlin.math.cos(endAng)
    val rz = kotlin.math.sin(endAng)

    // tečna kolmá na poloměr
    var tx = -rz
    var tz =  rx

    // pokud je oblouk CW (sweep < 0), otoč směr
    if (sweep < 0f) {
        tx = -tx
        tz = -tz
    }

    val len = kotlin.math.sqrt(tx*tx + tz*tz)
    return if (len < 1e-6f) Offset.Zero else Offset(tx/len, tz/len)
}
private fun ArcPieceNarys.tangentAtStartXZ(): Offset {
    val ang = Arc2DNarys.norm(arc.startRad)

    val rx = kotlin.math.cos(ang)
    val rz = kotlin.math.sin(ang)

    var tx = -rz
    var tz =  rx

    if (arc.sweepSigned() < 0f) {
        tx = -tx
        tz = -tz
    }

    val len = kotlin.math.sqrt(tx*tx + tz*tz)
    return if (len < 1e-6f) Offset.Zero else Offset(tx/len, tz/len)
}

private fun canonicalOrderEndpoints(a: Offset, b: Offset): Pair<Offset, Offset> {
    return if (a.x < b.x) a to b
    else if (a.x > b.x) b to a
    else if (a.y <= b.y) a to b
    else b to a
}

private data class CircleXZ(val cx: Float, val cz: Float, val r: Float)

private fun ConicSectionNarys.toCircleXZOrNull(eps: Float = 1e-4f): CircleXZ? {
    // očekáváme A≈C, B≈0 (u tebe A=C=1, B=0)
    if (kotlin.math.abs(a - c) > eps) return null
    if (kotlin.math.abs(b) > eps) return null
    if (kotlin.math.abs(a) < 1e-8f || kotlin.math.abs(c) < 1e-8f) return null

    val cx = -d / (2f * a)
    val cz = -e / (2f * c)

    val rr = cx*cx + cz*cz - (f / a)   // pro A=1: cx^2+cz^2 - F
    if (!rr.isFinite() || rr <= 1e-8f) return null

    return CircleXZ(cx, cz, kotlin.math.sqrt(rr))
}
private fun finalizeRevolutionFromCircleNarys(
    state: MongeState,
    axis: Line3D,
    circle: ConicSectionNarys
) {
    val axisX0 = axis.start.x
    val joinTol = state.snapThreshold / state.scale
    val axisEps = joinTol * 0.75f

    val c = circle.toCircleXZOrNull() ?: run {
        println("❌ Vybraný objekt není kružnice (nebo je degenerate).")
        return
    }

    val d = kotlin.math.abs(c.cx - axisX0)
    val rOuter = d + c.r
    val rInner = kotlin.math.abs(d - c.r)   // 0 pokud protíná osu

    val createdCircleIdsP = mutableListOf<String>()
    val createdCircleIdsN = mutableListOf<String>()
    fun addPerp(z: Float, r: Float, name: String, wMul: Float = 1f,styleOverridePud: LineStyle = LineStyle.Solid,
                styleOverrideNar: LineStyle = LineStyle.Solid) {
        val (pud, nar) = addPerpCircleToAxis_WithInputs(
            state = state,
            axis = axis,
            zLevel = z,
            radius = r,
            rawName = name,
            isHelpCircle = true,
            styleOverridePud = styleOverridePud,
            styleOverrideNar= styleOverrideNar,
            widthOverride = state.currentLineStyleSettings.strokeWidth * wMul,
            colorOverride = state.currentLineStyleSettings.color
        )
        createdCircleIdsP += pud.id
        createdCircleIdsN += nar.id
    }

    val zTop = c.cz + c.r
    val zBot = c.cz - c.r

// "nahoře" a "dole" na kružnici (v nárysu), rovnoběžky v půdorysu:
    addPerp(c.cz, rOuter, "k_obrys", wMul = 1.6f, styleOverrideNar = LineStyle.Dashed, styleOverridePud = LineStyle.Solid)
    if (rInner > axisEps) addPerp(c.cz, rInner, "k_hrdlo", styleOverrideNar = LineStyle.Dashed, styleOverridePud = LineStyle.Solid)
    if (d > axisEps) {
        addPerp(zTop, d, "k_top", styleOverrideNar = LineStyle.Solid, styleOverridePud = LineStyle.Dashed)
        addPerp(zBot, d, "k_bottom", styleOverrideNar = LineStyle.Solid, styleOverridePud = LineStyle.Dashed)
    }

    val solidId = UUID.randomUUID().toString()

    // meridianIds: u kružnice nech klidně jen tu kružnici
    val meridianIds = listOf(circle.id)
    val mirroredIds = createMirroredMeridianObjectsNarys(state, axisX0, meridianIds).toMutableList()

    val existingMirror = state.circlesNarys.firstOrNull { other ->
        val oc = other.toCircleXZOrNull()
        oc != null &&
                kotlin.math.abs(oc.cz - c.cz) < joinTol &&
                kotlin.math.abs(oc.r  - c.r)  < joinTol &&
                kotlin.math.abs(oc.cx - (2f*axisX0 - c.cx)) < joinTol
    }

    if (existingMirror != null) {
        // ✅ mirror už existuje → jen ho přidej do mirroredIds
        if (!mirroredIds.contains(existingMirror.id)) mirroredIds.add(existingMirror.id)
    } else {
        applyOuterSemicircleNarys(circle, axisX0, state)
        // ✅ mirror neexistuje → vytvoř ho, ulož do state, a přidej id
        val mirroredCircle = mirrorCircleNarysAcrossAxisX(circle, axisX0, state)
        state.circlesNarys += mirroredCircle
        mirroredIds.add(mirroredCircle.id)

        applyOuterSemicircleNarys(mirroredCircle, axisX0, state)
    }
    val sampled = sampleCircleXZ(c)

    val solid = SolidOfRevolutionNarys(
        id = solidId,
        axisLine3DId = axis.id,
        meridianIdsNarys = meridianIds,
        mirroredMeridianIdsNarys = mirroredIds,
        rMax = rOuter,
        neckRadii = if (rInner > axisEps) listOf(rInner) else emptyList(),
        circleIdsPudorys = createdCircleIdsP,
        circleIdsNarys = createdCircleIdsN,     // ✅
        sampledMeridianPolylineXZ = sampled,
        creationIndex = allocIndex(state),
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth,
    )


    state.solidsOfRevolutionNarys += solid

    val segmentsNarys = state.segmentsNarys.filter { it.id in meridianIds }
    segmentsNarys.forEach { segment ->
        segment.localColor = solid.color
    }
    commitSnapshot(state)

    // reset režimu
    state.selectedRevolutionAxis3D = null
    state.selectedMeridianNarysIds.clear()
    state.drawobjects = Mongeobjects.NONE
    resetStavu(state)
    setProjectionPhase("narys_start", state)

    println("✅ Anuloid vytvořen: z=${c.cz} rOuter=$rOuter rInner=$rInner")
}
private fun sampleCircleXZ(c: CircleXZ): List<Offset> {
    val segments = 256
    val out = ArrayList<Offset>(segments + 1)
    for (i in 0..segments) {
        val a = (i.toFloat() / segments.toFloat()) * (2f * kotlin.math.PI.toFloat())
        val x = c.cx + c.r * kotlin.math.cos(a)
        val z = c.cz + c.r * kotlin.math.sin(a)
        out.add(Offset(x, z))
    }
    return out
}
fun solidOfRevolutionNarys(state: MongeState) {
    when (state.projectionPhase) {
        "rev_axis_select" -> {
            val snapped = state.snappedLinePudorys ?: state.snappedLineNarys
            val axis3D = when (snapped) {
                is Line3DProjectionPudorys -> snapped.parent
                is Line3DProjectionNarys -> snapped.parent
                else -> null
            }
                // Osa vybraná přes ObjectList (bez live hoveru nad plátnem).
                ?: (state.selectedLinesPudorys.lastOrNull() as? Line3DProjectionPudorys)?.parent
                ?: (state.selectedLinesNarys.lastOrNull() as? Line3DProjectionNarys)?.parent
                ?: return

            if (!isAxisVertical(axis3D)) {
                println("⚠️ Osa musí být kolmá na půdorysnu (svislá).")
                return
            }

            state.selectedRevolutionAxis3D = axis3D
            setProjectionPhase("rev_meridian_select", state)
            println("✅ Osa vybrána. Klikáním vybírej poledník v nárysu (úsečky/oblouky), nebo klikni na kružnici pro okamžité vytvoření.")
        }

        "rev_meridian_select" -> {
            val axis = state.selectedRevolutionAxis3D ?: return

            // ✅ 1) ONE-SHOT: kružnice v nárysu
            val hitCircle = state.hoveredCircleNarys
            if (hitCircle != null) {
                finalizeRevolutionFromCircleNarys(state, axis, hitCircle)
                return
            }

            // ✅ 1b) ONE-SHOT: křivka v nárysu
            val hitCurveId = state.selectedCurveNarysId
            val hitCurve = state.curvesNarys.firstOrNull(){it.id == hitCurveId}

            if (hitCurve != null) {
                finalizeRevolutionFromCurveNarys(state, axis, hitCurve)
                return
            }

            // ✅ 1c) ONE-SHOT: obecná kuželosečka v nárysu
            // ObjectList vybírá celou rodinu kuželosečky najednou (viz
            // toggleSelectionConic2DFamilyByParentId), takže fallback stačí na nárysový průmět.
            val hitConic = state.snappedConicNarys ?: state.selectedConicsNarys.lastOrNull()
            if (hitConic != null && !hitConic.isHelpCircle) {
                finalizeRevolutionFromConicNarys(state, axis, hitConic)
                return
            }

            // ✅ 2) jinak běžný výběr segment/oblouk
            val hit = state.snappedSegmentNarys ?: state.snappedArcNarys
            when (hit) {
                is SegmentsNarys -> {
                    toggleInList(state.selectedMeridianNarysIds, hit.id)
                    println("přidána úsečka ${hit.id}")
                }
                is Arc2DNarys -> {
                    toggleInList(state.selectedMeridianNarysIds, hit.id)
                }
                else -> return
            }
        }

    }
}
fun finalizeRevolutionFromCurveNarys(state: MongeState, axis: Line3D, curve: CurveNarys) {
    if (!isAxisVertical(axis)) {
        println("⚠️ Osa musí být kolmá na půdorysnu (svislá).")
        return
    }

    val controlXZ: List<Offset> = curve.pointIds.mapNotNull { id ->
        val p = state.pointsNarys.find { it.id == id } ?: return@mapNotNull null
        Offset(p.x, p.z)
    }
    if (controlXZ.size < 2) return

    val sampledXZ = sampleCatmullRom(
        points = controlXZ,
        stepsPerSegment = 28,
        closed = curve.closed
    )
    if (sampledXZ.size < 2) return

    val axisX0 = axis.start.x
    val rs = sampledXZ.map { kotlin.math.abs(it.x - axisX0) }
    println("sampled=${sampledXZ.size} rMin=${rs.minOrNull()} rMax=${rs.maxOrNull()} joinTol=${state.snapThreshold / state.scale}")
    println("first=${sampledXZ.first()} last=${sampledXZ.last()}")

    val rMax = sampledXZ.maxOf { kotlin.math.abs(it.x - axisX0) }

    val mirroredCurveId = createMirroredCurveNarys(
        state = state,
        original = curve,
        axisX0 = axisX0,
        namePrefix = ""
    )

    val res = addSilhouetteAndNeckCirclesFromCurveSample(
        state = state,
        axis = axis,
        sampledXZ = sampledXZ,
        closed = curve.closed,
        baseLabelMax = "Obrys",
        baseLabelMin = "Hrdlo",
    )

    // -------------------------------------------------
    // DEDUPLIKACE vytvořených kružnic podle geometrie
    // -------------------------------------------------
    data class CircleGeom(val z: Float, val r: Float)

    val circleGeomTol = maxOf(0.5f, state.snapThreshold / state.scale * 0.5f)

    fun circleGeomNarys(circle: ConicSectionNarys): CircleGeom? {
        val x0 = -circle.d / 2f
        val z0 = -circle.e / 2f
        val r2 = x0 * x0 + z0 * z0 - circle.f
        if (r2 <= 0f) return null
        return CircleGeom(
            z = z0,
            r = kotlin.math.sqrt(r2)
        )
    }

    fun circlesEquivalent(a: CircleGeom, b: CircleGeom): Boolean {
        return abs(a.z - b.z) <= circleGeomTol &&
                abs(a.r - b.r) <= circleGeomTol
    }

    val keptPudIds = mutableListOf<String>()
    val keptNarIds = mutableListOf<String>()
    val keptGeoms = mutableListOf<CircleGeom>()

    val narIds = res.circleIdsNarys
    val pudIds = res.circleIdsPudorys

    for (i in narIds.indices) {
        val narId = narIds[i]
        val pudId = pudIds.getOrNull(i)

        val narCircle = state.circlesNarys.find { it.id == narId }

        // Když kružnici nenajdu nebo nejde spočítat geometrii,
        // nic nemažu a ID radši ponechám.
        if (narCircle == null) {
            println("⚠️ Nenalezena nárysová kružnice id=$narId při deduplikaci -> ponechávám.")
            if (pudId != null) keptPudIds += pudId
            keptNarIds += narId
            continue
        }

        val geom = circleGeomNarys(narCircle)
        if (geom == null) {
            println("⚠️ Nelze spočítat geometrii kružnice id=$narId -> ponechávám.")
            if (pudId != null) keptPudIds += pudId
            keptNarIds += narId
            continue
        }

        val duplicate = keptGeoms.any { circlesEquivalent(it, geom) }
        if (duplicate) {
            println("↩️ Duplicitní kružnice z křivky odstraněna: z=${geom.z} r=${geom.r} id=$narId")

            if (pudId != null) {
                state.circlesPudorys.removeAll { it.id == pudId }
            }
            state.circlesNarys.removeAll { it.id == narId }
        } else {
            keptGeoms += geom
            if (pudId != null) keptPudIds += pudId
            keptNarIds += narId
        }
    }

    // Kdyby náhodou bylo v pudorysu víc ID než v nárysu, taky je nezahodit:
    if (pudIds.size > narIds.size) {
        for (i in narIds.size until pudIds.size) {
            keptPudIds += pudIds[i]
        }
    }

    val solid = SolidOfRevolutionNarys(
        id = UUID.randomUUID().toString(),
        axisLine3DId = axis.id,
        meridianIdsNarys = listOf(curve.id),
        mirroredMeridianIdsNarys = listOf(mirroredCurveId).filter { it.isNotBlank() },
        rMax = rMax,
        neckRadii = emptyList(),
        circleIdsPudorys = keptPudIds,
        circleIdsNarys = keptNarIds,
        sampledMeridianPolylineXZ = sampledXZ,
        creationIndex = allocIndex(state),
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    val curves = state.curvesNarys.filter { it.id in listOf(curve.id, mirroredCurveId) }
    curves.forEach { curve ->
        curve.color = solid.color
        curve.strokeWidth = solid.strokeWidth
    }

    val points = state.pointsNarys.filter { it.id in curve.pointIds }
    points.forEach { point ->
        point.localColor = solid.color
        point.localWidth = solid.strokeWidth
    }

    state.solidsOfRevolutionNarys.add(solid)
    commitSnapshot(state)

    state.selectedMeridianNarysIds.clear()
    state.selectedRevolutionAxis3D = null
    resetStavu(state)
    setProjectionPhase("rev_axis_select", state)

    println("✅ Rotační plocha vytvořena z CurveNarys (+ deduplikované rovnoběžky).")
}
/**
 * ONE-SHOT vytvoření rotační plochy z obecné kuželosečky v nárysu
 * (elipsa / parabola / hyperbola, včetně jejich oblouků).
 * Kružnice bez ořezu se deleguje na anuloidový flow.
 */
fun finalizeRevolutionFromConicNarys(
    state: MongeState,
    axis: Line3D,
    conic: ConicSectionNarys
) {
    if (!isAxisVertical(axis)) {
        println("⚠️ Osa musí být kolmá na půdorysnu (svislá).")
        return
    }

    val hasTrim = state.ellipseArcEnds.containsKey(conic.id) ||
            state.parabolaArcEnds.containsKey(conic.id) ||
            state.hyperbolaArcBranch1.containsKey(conic.id) ||
            state.hyperbolaArcBranch2.containsKey(conic.id)

    if (!hasTrim && conic.toCircleXZOrNull() != null) {
        finalizeRevolutionFromCircleNarys(state, axis, conic)
        return
    }

    val sample = sampleConicMeridianNarys(state, conic) ?: run {
        println("❌ Kuželosečku nelze použít jako meridián (degenerovaná nebo chybí vstupy).")
        return
    }

    val axisX0 = axis.start.x
    val sampledXZ =
        if (sample.closed) rotateClosedSampleAwayFromRadiusExtremum(sample.polyline, axisX0)
        else sample.polyline
    if (sampledXZ.size < 2) return

    val joinTol = state.snapThreshold / state.scale
    val rMax = sampledXZ.maxOf { abs(it.x - axisX0) }
    if (rMax <= joinTol * 0.75f) {
        println("❌ Meridián leží na ose rotace.")
        return
    }

    val mirroredConicId = createMirroredConicNarys(state, conic, axisX0)

    val res = addSilhouetteAndNeckCirclesFromCurveSample(
        state = state,
        axis = axis,
        sampledXZ = sampledXZ,
        closed = sample.closed,
        baseLabelMax = "Obrys",
        baseLabelMin = "Hrdlo"
    )

    val solid = SolidOfRevolutionNarys(
        id = UUID.randomUUID().toString(),
        axisLine3DId = axis.id,
        meridianIdsNarys = listOf(conic.id),
        mirroredMeridianIdsNarys = listOf(mirroredConicId).filter { it.isNotBlank() },
        rMax = rMax,
        neckRadii = res.neckRadii,
        circleIdsPudorys = res.circleIdsPudorys,
        circleIdsNarys = res.circleIdsNarys,
        sampledMeridianPolylineXZ = sampledXZ,
        creationIndex = allocIndex(state),
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    state.solidsOfRevolutionNarys.add(solid)
    commitSnapshot(state)

    state.selectedMeridianNarysIds.clear()
    state.selectedRevolutionAxis3D = null
    resetStavu(state)
    setProjectionPhase("rev_axis_select", state)

    println("✅ Rotační plocha vytvořena z kuželosečky (nárys). rMax=$rMax closed=${sample.closed}")
}

private fun mirrorCircleNarysAcrossAxisX(
    src: ConicSectionNarys,
    axisX0: Float,
    state: MongeState
): ConicSectionNarys {
    val d = src.d
    val e = src.e
    val f = src.f

    val d2 = -(4f * axisX0 + d)
    val f2 = 4f * axisX0 * axisX0 + 2f * axisX0 * d + f

    return src.copy(
        d = d2,
        e = e,
        f = f2,
        id = UUID.randomUUID().toString(),
        creationIndex = allocIndex(state),
        parent = null
    )
}
private fun applyOuterSemicircleNarys(
    circle: ConicSectionNarys,
    axisX0: Float,
    state: MongeState
) {
    val xc = -circle.d / 2f
    val zc = -circle.e / 2f
    val r2 = xc * xc + zc * zc - circle.f
    if (r2 <= 0f) return

    val r = sqrt(r2)

    // nárysová "logical" soustava pro kreslení = (x, -z)
    val centerLogical = Offset(xc, -zc)

    val top = Offset(centerLogical.x, centerLogical.y - r)
    val bottom = Offset(centerLogical.x, centerLogical.y + r)

    state.circleArcEnds[circle.id] = top to bottom

    // střed vpravo od osy -> chceme pravou půlku
    // střed vlevo od osy  -> chceme levou půlku
    state.circleArcMode[circle.id] =
        if (xc >= axisX0) ArcMode.CCW else ArcMode.CW
}
fun sampleCatmullRom(
    points: List<Offset>,
    stepsPerSegment: Int = 18,
    closed: Boolean = false
): List<Offset> {

    if (points.size < 2) return emptyList()

    val result = mutableListOf<Offset>()
    val pts = if (closed) {
        // u uzavřené křivky vezmeme body cyklicky
        points
    } else {
        points
    }

    val count = pts.size

    fun getPoint(i: Int): Offset {
        return when {
            closed -> pts[(i + count) % count]
            i < 0 -> pts.first()
            i >= count -> pts.last()
            else -> pts[i]
        }
    }

    val segmentCount = if (closed) count else count - 1

    for (i in 0 until segmentCount) {

        val p0 = getPoint(i - 1)
        val p1 = getPoint(i)
        val p2 = getPoint(i + 1)
        val p3 = getPoint(i + 2)

        for (step in 0 until stepsPerSegment) {

            val t = step / stepsPerSegment.toFloat()
            val t2 = t * t
            val t3 = t2 * t

            val x =
                0.5f * (
                        (2f * p1.x) +
                                (-p0.x + p2.x) * t +
                                (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                                (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
                        )

            val y =
                0.5f * (
                        (2f * p1.y) +
                                (-p0.y + p2.y) * t +
                                (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                                (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
                        )

            result.add(Offset(x, y))
        }
    }

    // přidej poslední bod (aby to nebylo useknuté)
    if (!closed) {
        result.add(points.last())
    }

    return result
}

data class SilhouetteAndNecksResult(
    val circleIdsPudorys: List<String>,
    val circleIdsNarys: List<String>,
    val neckRadii: List<Float>
)

fun addSilhouetteAndNeckCirclesFromCurveSample(
    state: MongeState,
    axis: Line3D,
    sampledXZ: List<Offset>,
    closed: Boolean,
    baseLabelMax: String = "Obrys",
    baseLabelMin: String = "Hrdlo",
): SilhouetteAndNecksResult {

    val axisX0 = axis.start.x
    val joinTol = state.snapThreshold / state.scale

    val extrema = findRadiusExtremaByTrend(
        sampledXZ = sampledXZ,
        axisX0 = axisX0,
        closed = closed,
        epsR = joinTol * 0.005f,
        includeEndpoints = true
    )

    val maxes = extrema.filter { it.type == ExtremumType.MAX }
    val mins  = extrema.filter { it.type == ExtremumType.MIN }

    val idsP = mutableListOf<String>()
    val idsN = mutableListOf<String>()
    val necks = mutableListOf<Float>()

    fun addCircle(z: Float, r: Float, label: String) {
        val (pud, nar) = addPerpCircleToAxis_WithInputs(
            state = state,
            axis = axis,
            zLevel = z,
            radius = r,
            rawName = label,
            styleOverridePud = LineStyle.Solid,
            styleOverrideNar = LineStyle.Dashed,
            widthOverride = state.currentLineStyleSettings.strokeWidth,
            colorOverride = state.currentLineStyleSettings.color
        )

        idsP += pud.id
        idsN += nar.id
    }

    maxes.forEachIndexed { idx, e ->
        val label = if (maxes.size == 1) baseLabelMax else "$baseLabelMax${idx + 1}"
        addCircle(e.p.y, e.r, label)
    }

    mins.forEachIndexed { idx, e ->
        val label = if (mins.size == 1) baseLabelMin else "$baseLabelMin${idx + 1}"
        addCircle(e.p.y, e.r, label)
        necks += e.r
    }

    return SilhouetteAndNecksResult(
        circleIdsPudorys = idsP,
        circleIdsNarys = idsN,
        neckRadii = necks
    )
}

enum class ExtremumType { MAX, MIN }

data class RadiusExtremum(
    val index: Int,
    val p: Offset,   // XZ bod (y = z)
    val r: Float,
    val type: ExtremumType
)

fun findRadiusExtremaByTrend(
    sampledXZ: List<Offset>,
    axisX0: Float,
    closed: Boolean,
    epsR: Float,
    includeEndpoints: Boolean = true
): List<RadiusExtremum> {
    if (sampledXZ.size < 3) return emptyList()

    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)
    val n = sampledXZ.size
    val r = FloatArray(n) { i -> rOf(sampledXZ[i]) }

    fun signDr(d: Float): Int = when {
        d > epsR  ->  1
        d < -epsR -> -1
        else      ->  0
    }

    // sign(dr) pro open křivku mezi (i-1)->i
    val s = IntArray(n) { 0 }
    for (i in 1 until n) {
        s[i] = signDr(r[i] - r[i - 1])
    }

    // vyplň nuly: umí +,0,- i -,0,+
    var last = 0
    for (i in 1 until n) {
        if (s[i] != 0) last = s[i] else s[i] = last
    }
    var next = 0
    for (i in (n - 1) downTo 1) {
        if (s[i] != 0) next = s[i] else s[i] = next
    }

    val out = mutableListOf<RadiusExtremum>()

    // maxima: + -> -
    // minima: - -> +
    for (i in 2 until n) {
        val prev = s[i - 1]
        val cur  = s[i]
        val idx = i - 1

        if (prev == 1 && cur == -1) {
            out += RadiusExtremum(idx, sampledXZ[idx], r[idx], ExtremumType.MAX)
        } else if (prev == -1 && cur == 1) {
            out += RadiusExtremum(idx, sampledXZ[idx], r[idx], ExtremumType.MIN)
        }
    }

    if (!closed && includeEndpoints) {
        // Endpointy jsou “extrémy” jen pokud chceš; typ určíme podle prvního/posledního trendu
        val firstTrend = s.firstOrNull { it != 0 } ?: 0
        val lastTrend  = s.drop(1).lastOrNull { it != 0 } ?: 0

        val t0 = if (firstTrend < 0) ExtremumType.MAX else ExtremumType.MIN
        val tN = if (lastTrend > 0) ExtremumType.MAX else ExtremumType.MIN

        out += RadiusExtremum(0, sampledXZ[0], r[0], t0)
        out += RadiusExtremum(n - 1, sampledXZ[n - 1], r[n - 1], tN)
    }

    return out
        .distinctBy { it.index to it.type }
        .sortedBy { it.index }
}
fun createMirroredCurveNarys(
    state: MongeState,
    original: CurveNarys,
    axisX0: Float,
    namePrefix: String = ""   // např. "⟂" nebo "mirror_"
): String {

    // 1) runtime body originálu (v nárysu)
    val origPts = original.pointIds.mapNotNull { pid ->
        state.pointsNarys.find { it.id == pid }
    }
    if (origPts.size < 2) return ""

    // 2) vyrob zrcadlené body
    val mirroredPointIds = origPts.map { p ->
        val newId = UUID.randomUUID().toString()

        // ⚠️ uprav podle svého typu bodu v nárysu:
        val mp = p.copy(
            id = newId,
            x = (2f * axisX0) - p.x,
            z = p.z,
            name = "",
            creationIndex = allocIndex(state),
            localColor = state.currentLineStyleSettings.color,
            localWidth = state.currentLineStyleSettings.strokeWidth
        )

        state.pointsNarys.add(mp)
        newId
    }

    // 3) vyrob zrcadlenou křivku (CurveNarys)
    val mirroredCurveId = UUID.randomUUID().toString()

    val mirrored = original.copy(
        id = mirroredCurveId,
        pointIds = mirroredPointIds,
        name = if (original.name.isBlank()) "" else "$namePrefix${original.name}",
        creationIndex = allocIndex(state),
        lineStyle = LineStyle.Dashed,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    state.curvesNarys.add(mirrored)
    return mirroredCurveId
}