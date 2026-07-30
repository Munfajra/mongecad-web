package monge.input.meridian


import geometry.sampleCatmullRom
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import serialization.commitSnapshot
import model.*
import model.classes.*
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import utils.UUID
import kotlin.collections.ArrayDeque
import kotlin.collections.first
import kotlin.collections.last
import kotlin.math.abs
import kotlin.math.sqrt


private fun dist2(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

private fun nearestIndex(poly: List<Offset>, p: Offset): Int {
    var bestI = 0
    var bestD = Float.POSITIVE_INFINITY
    for (i in poly.indices) {
        val d = dist2(poly[i], p)
        if (d < bestD) {
            bestD = d
            bestI = i
        }
    }
    return bestI
}

private fun sliceCyclic(poly: List<Offset>, i0: Int, i1: Int): List<Offset> {
    if (poly.isEmpty()) return poly
    val out = ArrayList<Offset>()
    var i = i0
    while (true) {
        out += poly[i]
        if (i == i1) break
        i = (i + 1) % poly.size
        if (out.size > poly.size + 2) break
    }
    return out
}

fun trimConicPolylineByStatePudorys(
    state: MongeState,
    conicId: String,
    full: List<Offset>
): List<Offset> {

    state.ellipseArcEnds[conicId]?.let { (a, b) ->
        val iA = nearestIndex(full, a)
        val iB = nearestIndex(full, b)

        val mode = state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST
        val forward = sliceCyclic(full, iA, iB)
        val backward = sliceCyclic(full, iB, iA)

        return when (mode) {
            ArcMode.SHORTEST -> if (forward.size <= backward.size) forward else backward.asReversed()
            ArcMode.LONGEST  -> if (forward.size >= backward.size) forward else backward.asReversed()
            ArcMode.CCW      -> forward
            ArcMode.CW       -> backward.asReversed()
        }
    }

    state.parabolaArcEnds[conicId]?.let { (a, b) ->
        val iA = nearestIndex(full, a)
        val iB = nearestIndex(full, b)
        val lo = minOf(iA, iB)
        val hi = maxOf(iA, iB)
        return full.subList(lo, hi + 1)
    }

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

    return full
}

data class OrderedPathResultPudorys(
    val ordered: List<MeridianPiecePudorys>,
    val polyline: List<Offset>   // XY
)

private fun lexLessYx(a: Offset, b: Offset): Boolean {
    // v XY prostoru: prioritně menší y, pak menší x
    if (a.y < b.y) return true
    if (a.y > b.y) return false
    return a.x < b.x
}

private fun minPointYx(a: Offset, b: Offset): Offset =
    if (lexLessYx(a, b)) a else b

fun pickDeterministicStartIndexPudorys(remaining: List<MeridianPiecePudorys>): Int {
    var bestIdx = 0
    var bestPoint = minPointYx(remaining[0].startXY(), remaining[0].endXY())

    for (i in 1 until remaining.size) {
        val p = minPointYx(remaining[i].startXY(), remaining[i].endXY())
        if (lexLessYx(p, bestPoint)) {
            bestPoint = p
            bestIdx = i
        }
    }
    return bestIdx
}

fun buildMeridianPathFromSelectedPudorys(
    state: MongeState,
    selected: List<MeridianPiecePudorys>,
    joinTol: Float,
    maxError: Float
): OrderedPathResultPudorys {

    if (selected.isEmpty()) return OrderedPathResultPudorys(emptyList(), emptyList())
    val remaining = selected.toMutableList()

    fun closeEnough(a: Offset, b: Offset) = dist2(a, b) <= joinTol * joinTol

    val startIdx = pickDeterministicStartIndexPudorys(remaining)

    val ordered = ArrayDeque<MeridianPiecePudorys>()
    ordered.addLast(remaining.removeAt(startIdx))

    while (remaining.isNotEmpty()) {
        val head = ordered.first().startXY()
        val tail = ordered.last().endXY()

        var bestIdx = -1
        var bestAttachToTail = true
        var bestFlip = false
        var bestD2 = Float.POSITIVE_INFINITY

        for (i in remaining.indices) {
            val p = remaining[i]
            val s = p.startXY()
            val e = p.endXY()

            val d2_ts = dist2(s, tail)
            if (d2_ts <= joinTol * joinTol && d2_ts < bestD2) {
                bestD2 = d2_ts
                bestIdx = i
                bestAttachToTail = true
                bestFlip = false
            }

            val d2_te = dist2(e, tail)
            if (d2_te <= joinTol * joinTol && d2_te < bestD2) {
                bestD2 = d2_te
                bestIdx = i
                bestAttachToTail = true
                bestFlip = true
            }

            val d2_hs = dist2(s, head)
            if (d2_hs <= joinTol * joinTol && d2_hs < bestD2) {
                bestD2 = d2_hs
                bestIdx = i
                bestAttachToTail = false
                bestFlip = true
            }

            val d2_he = dist2(e, head)
            if (d2_he <= joinTol * joinTol && d2_he < bestD2) {
                bestD2 = d2_he
                bestIdx = i
                bestAttachToTail = false
                bestFlip = false
            }
        }

        if (bestIdx < 0) break

        val next = remaining.removeAt(bestIdx)
        val oriented = if (bestFlip) next.reversed() else next

        if (bestAttachToTail) ordered.addLast(oriented) else ordered.addFirst(oriented)
    }

    val poly = ArrayList<Offset>()
    ordered.forEachIndexed { i, piece ->
        val pts = piece.samplePolylineXY(state, maxError)
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

    return OrderedPathResultPudorys(ordered.toList(), poly)
}

private fun toggleInList(list: SnapshotStateList<String>, id: String) {
    if (list.contains(id)) list.remove(id) else list.add(id)
}

fun startSolidOfRevolutionToolPudorys(state: MongeState) {
    state.drawobjects = Mongeobjects.SOLID_OF_REVOLUTION
    setProjectionPhase("rev_axis_select_pudorys", state)

    state.selectedRevolutionAxis3D = null
    state.selectedMeridianPudorysIds.clear()

    state.previewRevolutionRmax = null
    state.previewRevolutionNecks = emptyList()
}

private fun isAxisPerpendicularToNarys(line: Line3D, eps: Float = 1e-3f): Boolean {
    val d = line.direction.normalize()
    // osa ve směru Y => x≈0, z≈0
    return kotlin.math.abs(d.x) < eps && kotlin.math.abs(d.z) < eps
}

fun finalizeSolidOfRevolutionPudorys(state: MongeState) {
    val axis = state.selectedRevolutionAxis3D ?: return
    val selectedIds = state.selectedMeridianPudorysIds.toList()
    if (selectedIds.isEmpty()) return

    val pieces = resolveSelectedMeridianPiecesPudorys(state, selectedIds)
    if (pieces.isEmpty()) {
        println("❌ Nic nebylo vybráno.")
        return
    }

    val dir = axis.direction.normalize()
    if (abs(dir.x) > 1e-3f || abs(dir.z) > 1e-3f) {
        println("❌ Osa není kolmá k nárysně (mění se x nebo z). x=${dir.x} z=${dir.z}")
        return
    }

    val axisX0 = axis.start.x
    val joinTol = 1f
    val maxError = joinTol * 0.05f
    val axisEps = joinTol * 0.75f

    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)

    val pathRes = buildMeridianPathFromSelectedPudorys(state, pieces, joinTol, maxError)
    val polyXY = pathRes.polyline
    if (polyXY.size < 2) return

    val kinks = findKinksAtPieceJointsXY(
        ordered = pathRes.ordered,
        axisX0 = axisX0,
        joinTol = joinTol,
        minTurnDeg = 8f
    )

    val endpoints = findMeridianEndpointsXY(pathRes.ordered)
    val pMax = farthestPointFromAxisOnMeridianXY(pathRes.ordered, axisX0)
    val rMax = rOf(pMax)
    if (rMax <= axisEps) return

    val pEnd = endpointWithMinRadiusXY(pathRes.ordered, axisX0)
    val rEnd = rOf(pEnd)

    val solidId = UUID.randomUUID().toString()
    val mirroredIds = createMirroredMeridianObjectsPudorys(state, axisX0, selectedIds)

    val createdCircleIdsP = mutableListOf<String>()
    val createdCircleIdsN = mutableListOf<String>()

    fun addParallelCircle(
        y: Float,
        r: Float,
        label: String,
        isHelp: Boolean = true,
        wMul: Float = 1f,
        stylen: LineStyle = LineStyle.Solid,
        stylep:LineStyle = LineStyle.Solid,
        width: Float = state.currentLineStyleSettings.strokeWidth * wMul,
        color: Color = state.currentLineStyleSettings.color
    ) {
        val (pud, nar) = addPerpCircleToAxis_WithInputs_Pudorys(
            state = state,
            axis = axis,
            yLevel = y,
            radius = r,
            rawName = label,
            isHelpCircle = isHelp,
            stylen = stylen,
            stylep = stylep,
            widthOverride = width,
            colorOverride = color
        )
        createdCircleIdsP += pud.id
        createdCircleIdsN += nar.id
    }

    fun addRimIfOpen(p: Offset, label: String) {
        val r = rOf(p)
        if (r <= axisEps) return
        addParallelCircle(y = p.y, r = r, label = label, isHelp = true,stylep= LineStyle.Solid, stylen = LineStyle.Solid)
    }

    if (endpoints.size == 2) {
        val (pStart, pEnd2) = canonicalOrderEndpointsXY(endpoints[0], endpoints[1])
        addRimIfOpen(pStart, "Hrana1")
        addRimIfOpen(pEnd2, "Hrana2")
    } else {
        println("⚠️ endpoints.size=${endpoints.size} endpoints=$endpoints")
    }

    kinks.forEach { k ->
        addParallelCircle(y = k.yLevel, r = k.r, label = "Rovnoběžka", isHelp = true,stylep = LineStyle.Solid, stylen = LineStyle.Dashed)
    }

    addParallelCircle(pMax.y, rMax, "Obrys", wMul = 1f)

    val solid = SolidOfRevolutionPudorys(
        id = solidId,
        axisLine3DId = axis.id,
        meridianIdsPudorys = selectedIds,
        mirroredMeridianIdsPudorys = mirroredIds,
        rMax = rMax,
        neckRadii = emptyList(),
        circleIdsPudorys = createdCircleIdsP,
        circleIdsNarys = createdCircleIdsN,
        sampledMeridianPolylineXY = polyXY,
        creationIndex = allocIndex(state),
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    state.solidsOfRevolutionPudorys += solid
    val segmentsPudorys = state.segmentsPudorys.filter { it.id in selectedIds || it.id in mirroredIds}
    segmentsPudorys.forEach { segment ->
        segment.localColor = solid.color
        segment.localStrokeWidth = solid.strokeWidth
    }
    val helpsegmentsPudorys = state.helpSegmentsPudorys.filter { it.id in selectedIds || it.id in mirroredIds}
    helpsegmentsPudorys.forEach { segment ->
        segment.localColor = solid.color
        segment.localStrokeWidth=solid.strokeWidth
    }
    val arcsPudorys = state.arcsPudorys.filter { it.id in selectedIds || it.id in mirroredIds}
    arcsPudorys.forEach { arc->
        arc.strokeWidth = solid.strokeWidth
        arc.color = solid.color
    }

    commitSnapshot(state)

    state.selectedRevolutionAxis3D = null
    state.selectedMeridianPudorysIds.clear()
    state.drawobjects = Mongeobjects.NONE
    setProjectionPhase("pudorys_start", state)
    resetStavu(state)

    val pA = pathRes.ordered.first().startXY()
    val pB = pathRes.ordered.last().endXY()
    println("end candidates: A=$pA rA=${rOf(pA)} B=$pB rB=${rOf(pB)} chosen=$pEnd rEnd=$rEnd")
}

fun resolveSelectedMeridianPiecesPudorys(
    state: MongeState,
    selectedIds: List<String>
): List<MeridianPiecePudorys> {
    val out = mutableListOf<MeridianPiecePudorys>()

    selectedIds.forEach { id ->
        val segments = state.segmentsPudorys + state.helpSegmentsPudorys
        segments.firstOrNull { it.id == id }?.let { out += SegmentPiecePudorys(it, id) }
        state.arcsPudorys.firstOrNull { it.id == id }?.let { out += ArcPiecePudorys(it) }
        // conics později
    }

    return out
}

private fun mirrorXY(p: Offset, axisX0: Float): Offset =
    Offset(2f * axisX0 - p.x, p.y)

fun addMirroredSegmentPudorys(
    seg: SegmentsPudorys,
    axisX0: Float,
    state: MongeState
): String {
    val a = Offset(seg.start.x, seg.start.y)
    val b = Offset(seg.end.x, seg.end.y)

    val am = mirrorXY(a, axisX0)
    val bm = mirrorXY(b, axisX0)

    val seg2d = Segment2DPudorys(
        start = Point3DPudorys(am.x, am.y, name = seg.name ?: ""),
        end = Point3DPudorys(bm.x, bm.y, name = seg.name ?: ""),
        name = "",
        localColor = Color.DarkGray,
        localLineStyle = LineStyle.Dashed,
        localStrokeWidth = seg.strokeWidth,
        creationIndex = allocIndex(state)
    )

    val hseg = HelpSegmentPudorys(
        start = Point3DPudorys(am.x, am.y, name = seg.name ?: ""),
        end = Point3DPudorys(bm.x, bm.y, name = seg.name ?: ""),
        name = "",
        localColor = Color.DarkGray,
        localLineStyle = LineStyle.Dashed,
        localStrokeWidth = seg.strokeWidth,
        creationIndex = allocIndex(state)
    )

    when (seg) {
        is Segment2DPudorys -> {
            state.segmentsPudorys += seg2d
            return seg2d.id
        }
        is HelpSegmentPudorys -> {
            state.helpSegmentsPudorys += hseg
            return hseg.id
        }
        else -> return seg2d.id
    }
}

private fun mirrorAngleRad(theta: Float): Float {
    val pi = kotlin.math.PI.toFloat()
    return Arc2DPudorys.norm(pi - theta)
}

fun addMirroredArcPudorys(
    arc: Arc2DPudorys,
    axisX0: Float,
    state: MongeState
): String {
    val cxm = 2f * axisX0 - arc.center.x
    val cym = arc.center.y

    val sM = mirrorAngleRad(arc.startRad)
    val eM = mirrorAngleRad(arc.endRad)

    val mirrored = arc.copy(
        center = arc.center.copy(x = cxm, y = cym),
        startRad = sM,
        endRad = eM,
        clockwise = !arc.clockwise,
        name = arc.name + "_m",
        color = Color.DarkGray,
        lineStyle = LineStyle.Dashed,
        strokeWidth = arc.strokeWidth,
        id = UUID.randomUUID().toString(),
        creationIndex = allocIndex(state)
    )

    state.arcsPudorys += mirrored
    state.triggerRedraw++
    return mirrored.id
}

fun createMirroredMeridianObjectsPudorys(
    state: MongeState,
    axisX0: Float,
    selectedIds: List<String>
): List<String> {
    val createdIds = mutableListOf<String>()

    selectedIds.forEach { id ->
        state.allSegmentsPudorys.firstOrNull { it.id == id }?.let { seg ->
            createdIds += addMirroredSegmentPudorys(seg, axisX0, state)
            return@forEach
        }

        state.arcsPudorys.firstOrNull { it.id == id }?.let { arc ->
            createdIds += addMirroredArcPudorys(arc, axisX0, state)
            return@forEach
        }
    }

    return createdIds
}

fun addPerpCircleToAxis_WithInputs_Pudorys(
    state: MongeState,
    axis: Line3D,
    yLevel: Float,
    radius: Float,
    rawName: String,
    isHelpCircle: Boolean = true,
    stylep: LineStyle? = null,
    stylen: LineStyle? = null,
    widthOverride: Float? = null,
    colorOverride: Color? = null
): Pair<ConicSectionPudorys, ConicSectionNarys> {

    val axisX0 = axis.start.x
    val axisZ0 = axis.start.z

    val center3D = Offset3D(axisX0, yLevel, axisZ0)

    // pro osu rovnoběžnou s Y chceme kružnici přímo v rovině XZ
    val u = Offset3D(1f, 0f, 0f)
    val v = Offset3D(0f, 0f, 1f)

    val settings = state.currentLineStyleSettings

    val col = colorOverride ?: settings.color
    val w   = widthOverride ?: settings.strokeWidth
    val stn  = stylen ?: settings.style
    val stp = stylep ?: settings.style

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
        lineStyle = LineStyle.Solid,
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

    // pomocné vstupní body
    val p1p = Offset(axisX0 - radius, yLevel)
    val p2p = Offset(axisX0 + radius, yLevel)
    val p3p = Offset(axisX0, yLevel)

    val p1n = Offset(axisX0 - radius, -axisZ0)
    val p2n = Offset(axisX0 + radius, -axisZ0)
    val p3n = Offset(axisX0, -(axisZ0 + radius))

    state.conics3D += conic3D
    state.conicsPudorys += pud
    state.conicsNarys += nar

    state.conicInputPointsPudorys[pud.id] = Triple(p1p, p2p, p3p)
    state.conicInputPointsNarys[nar.id] = Triple(p1n, p2n, p3n)

    state.triggerRedraw++
    return pud to nar
}
fun farthestPointFromAxisOnArcXY(arc: Arc2DPudorys, axisX0: Float): Offset {
    fun ptAtRad(a: Float): Offset {
        val x = arc.center.x + arc.radius * kotlin.math.cos(a)
        val y = arc.center.y + arc.radius * kotlin.math.sin(a)
        return Offset(x, y)
    }

    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)

    val s0 = Arc2DPudorys.norm(arc.startRad)
    val sweep = arc.sweepSigned()
    val e0 = Arc2DPudorys.norm(s0 + sweep)

    fun onArc(a: Float): Boolean {
        val A = Arc2DPudorys.norm(a)
        val S = s0
        val E = e0
        return if (sweep >= 0f) {
            if (S <= E) (A in S..E) else (A >= S || A <= E)
        } else {
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
        if (rr > bestR) {
            best = p
            bestR = rr
        }
    }
    return best
}

fun farthestPointFromAxisOnMeridianXY(
    ordered: List<MeridianPiecePudorys>,
    axisX0: Float
): Offset {
    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)

    var best = ordered.first().startXY()
    var bestR = rOf(best)

    ordered.forEach { piece ->
        val candidates: List<Offset> = when (piece) {
            is ArcPiecePudorys -> listOf(farthestPointFromAxisOnArcXY(piece.arc, axisX0))
            else -> listOf(piece.startXY(), piece.endXY())
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

private fun endpointWithMinRadiusXY(
    ordered: List<MeridianPiecePudorys>,
    axisX0: Float
): Offset {
    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)
    val pA = ordered.first().startXY()
    val pB = ordered.last().endXY()
    return if (rOf(pA) <= rOf(pB)) pA else pB
}

private fun findMeridianEndpointsXY(
    ordered: List<MeridianPiecePudorys>,
    tol: Float = 1f
): List<Offset> {

    data class Cluster(
        var sumX: Float,
        var sumY: Float,
        var count: Int
    ) {
        fun center(): Offset = Offset(sumX / count, sumY / count)
    }

    val tol2 = tol * tol
    val clusters = mutableListOf<Cluster>()

    fun add(p: Offset) {
        val idx = clusters.indexOfFirst {
            val c = it.center()
            dist2(c, p) <= tol2
        }

        if (idx >= 0) {
            clusters[idx].sumX += p.x
            clusters[idx].sumY += p.y
            clusters[idx].count += 1
        } else {
            clusters += Cluster(p.x, p.y, 1)
        }
    }

    ordered.forEach { piece ->
        add(piece.startXY())
        add(piece.endXY())
    }

    return clusters
        .filter { it.count == 1 }
        .map { it.center() }
}

data class ProfileKinkXY(
    val yLevel: Float,
    val r: Float,
    val point: Offset,
    val turnDeg: Float
)

private fun findKinksAtPieceJointsXY(
    ordered: List<MeridianPiecePudorys>,
    axisX0: Float,
    joinTol: Float,
    minTurnDeg: Float = 8f,
    axisEps: Float = joinTol * 0.75f
): List<ProfileKinkXY> {
    if (ordered.size < 2) return emptyList()

    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)

    fun norm(v: Offset): Offset {
        val len = v.getDistance()
        return if (len < 1e-6f) Offset.Zero else Offset(v.x / len, v.y / len)
    }

    fun dot(a: Offset, b: Offset) = a.x * b.x + a.y * b.y

    fun tangentAtEnd(piece: MeridianPiecePudorys): Offset =
        when (piece) {
            is SegmentPiecePudorys -> norm(piece.endXY() - piece.startXY())
            is ArcPiecePudorys -> piece.tangentAtEndXY()
            else -> norm(piece.endXY() - piece.startXY())
        }

    fun tangentAtStart(piece: MeridianPiecePudorys): Offset =
        when (piece) {
            is SegmentPiecePudorys -> norm(piece.endXY() - piece.startXY())
            is ArcPiecePudorys -> piece.tangentAtStartXY()
            else -> norm(piece.endXY() - piece.startXY())
        }

    val cosTol = kotlin.math.cos(minTurnDeg.toDouble() * kotlin.math.PI / 180.0).toFloat()
    val out = mutableListOf<ProfileKinkXY>()

    for (i in 0 until ordered.lastIndex) {
        val a = ordered[i]
        val b = ordered[i + 1]

        val jointA = a.endXY()
        val jointB = b.startXY()

        if (dist2(jointA, jointB) > joinTol * joinTol) continue

        val joint = Offset(
            (jointA.x + jointB.x) * 0.5f,
            (jointA.y + jointB.y) * 0.5f
        )

        val r = rOf(joint)
        if (r <= axisEps) continue

        val t1 = tangentAtEnd(a)
        val t2 = tangentAtStart(b)
        if (t1 == Offset.Zero || t2 == Offset.Zero) continue

        val c = dot(t1, t2).coerceIn(-1f, 1f)
        if (c >= cosTol) continue

        val turn = (kotlin.math.acos(c).toDouble() * 180.0 / kotlin.math.PI).toFloat()
        out += ProfileKinkXY(
            yLevel = joint.y,
            r = r,
            point = joint,
            turnDeg = turn
        )
    }

    return out
}

private fun ArcPiecePudorys.tangentAtEndXY(): Offset {
    val sweep = arc.sweepSigned()
    val endAng = Arc2DPudorys.norm(arc.startRad + sweep)

    val rx = kotlin.math.cos(endAng)
    val ry = kotlin.math.sin(endAng)

    var tx = -ry
    var ty = rx

    if (sweep < 0f) {
        tx = -tx
        ty = -ty
    }

    val len = kotlin.math.sqrt(tx * tx + ty * ty)
    return if (len < 1e-6f) Offset.Zero else Offset(tx / len, ty / len)
}

private fun ArcPiecePudorys.tangentAtStartXY(): Offset {
    val ang = Arc2DPudorys.norm(arc.startRad)

    val rx = kotlin.math.cos(ang)
    val ry = kotlin.math.sin(ang)

    var tx = -ry
    var ty = rx

    if (arc.sweepSigned() < 0f) {
        tx = -tx
        ty = -ty
    }

    val len = kotlin.math.sqrt(tx * tx + ty * ty)
    return if (len < 1e-6f) Offset.Zero else Offset(tx / len, ty / len)
}

private fun canonicalOrderEndpointsXY(a: Offset, b: Offset): Pair<Offset, Offset> {
    return if (a.x < b.x) a to b
    else if (a.x > b.x) b to a
    else if (a.y <= b.y) a to b
    else b to a
}

private data class CircleXY(val cx: Float, val cy: Float, val r: Float)

private fun ConicSectionPudorys.toCircleXYOrNull(eps: Float = 1e-4f): CircleXY? {
    if (kotlin.math.abs(a - c) > eps) return null
    if (kotlin.math.abs(b) > eps) return null
    if (kotlin.math.abs(a) < 1e-8f || kotlin.math.abs(c) < 1e-8f) return null

    val cx = -d / (2f * a)
    val cy = -e / (2f * c)

    val rr = cx * cx + cy * cy - (f / a)
    if (!rr.isFinite() || rr <= 1e-8f) return null

    return CircleXY(cx, cy, kotlin.math.sqrt(rr))
}

private fun finalizeRevolutionFromCirclePudorys(
    state: MongeState,
    axis: Line3D,
    circle: ConicSectionPudorys
) {
    val axisX0 = axis.start.x
    val joinTol = state.snapThreshold / state.scale
    val axisEps = joinTol * 0.75f

    val c = circle.toCircleXYOrNull() ?: run {
        println("❌ Vybraný objekt není kružnice.")
        return
    }

    val d = kotlin.math.abs(c.cx - axisX0)
    val rOuter = d + c.r
    val rInner = kotlin.math.abs(d - c.r)

    val createdCircleIdsP = mutableListOf<String>()
    val createdCircleIdsN = mutableListOf<String>()

    fun addPerp(y: Float, r: Float, name: String, wMul: Float = 1f,stylen: LineStyle? = null,stylep: LineStyle? = null) {
        val (pud, nar) = addPerpCircleToAxis_WithInputs_Pudorys(
            state = state,
            axis = axis,
            yLevel = y,
            radius = r,
            rawName = name,
            isHelpCircle = true,
            stylen = stylen,
            stylep = stylep,
            widthOverride = state.currentLineStyleSettings.strokeWidth * wMul,
            colorOverride = state.currentLineStyleSettings.color
        )
        createdCircleIdsP += pud.id
        createdCircleIdsN += nar.id
    }

    val yTop = c.cy + c.r
    val yBot = c.cy - c.r

    addPerp(c.cy, rOuter, "k_obrys", wMul = 1.6f, stylen = LineStyle.Solid,stylep= LineStyle.Dashed)
    if (rInner > axisEps) addPerp(c.cy, rInner, "k_hrdlo")
    if (d > axisEps) {
        addPerp(yTop, d, "k_top",stylep= LineStyle.Solid,stylen= LineStyle.Dashed)
        addPerp(yBot, d, "k_bottom",stylep= LineStyle.Solid,stylen= LineStyle.Dashed)
    }

    val solidId = UUID.randomUUID().toString()

    val meridianIds = listOf(circle.id)
    val mirroredIds = createMirroredMeridianObjectsPudorys(state, axisX0, meridianIds).toMutableList()

    val existingMirror = state.circlesPudorys.firstOrNull { other ->
        val oc = other.toCircleXYOrNull()
        oc != null &&
                kotlin.math.abs(oc.cy - c.cy) < joinTol &&
                kotlin.math.abs(oc.r - c.r) < joinTol &&
                kotlin.math.abs(oc.cx - (2f * axisX0 - c.cx)) < joinTol
    }

    if (existingMirror != null) {
        // ✅ mirror už existuje → jen ho přidej do mirroredIds
        if (!mirroredIds.contains(existingMirror.id)) mirroredIds.add(existingMirror.id)
    } else {
        applyOuterSemicirclePudorys(circle, axisX0, state)
        // ✅ mirror neexistuje → vytvoř ho, ulož do state, a přidej id
        val mirroredCircle = mirrorCirclePudorysAcrossAxisX(circle, axisX0, state)
        state.circlesPudorys += mirroredCircle
        mirroredIds.add(mirroredCircle.id)

        applyOuterSemicirclePudorys(mirroredCircle, axisX0, state)
    }
    val sampled = sampleCircleXY(c)

    val solid = SolidOfRevolutionPudorys(
        id = solidId,
        axisLine3DId = axis.id,
        meridianIdsPudorys = meridianIds,
        mirroredMeridianIdsPudorys = mirroredIds,
        rMax = rOuter,
        neckRadii = if (rInner > axisEps) listOf(rInner) else emptyList(),
        circleIdsPudorys = createdCircleIdsP,
        circleIdsNarys = createdCircleIdsN,
        sampledMeridianPolylineXY = sampled,
        creationIndex = allocIndex(state),
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    state.solidsOfRevolutionPudorys += solid
    commitSnapshot(state)

    state.selectedRevolutionAxis3D = null
    state.selectedMeridianPudorysIds.clear()
    state.drawobjects = Mongeobjects.NONE
    resetStavu(state)
    setProjectionPhase("pudorys_start", state)

    println("✅ Anuloid vytvořen v půdorysu: y=${c.cy} rOuter=$rOuter rInner=$rInner")
}

private fun sampleCircleXY(c: CircleXY): List<Offset> {
    val segments = 256
    val out = ArrayList<Offset>(segments + 1)
    for (i in 0..segments) {
        val a = (i.toFloat() / segments.toFloat()) * (2f * kotlin.math.PI.toFloat())
        val x = c.cx + c.r * kotlin.math.cos(a)
        val y = c.cy + c.r * kotlin.math.sin(a)
        out.add(Offset(x, y))
    }
    return out
}

fun solidOfRevolutionPudorys(state: MongeState) {
    when (state.projectionPhase) {
        "rev_axis_select_pudorys" -> {
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

            if (!isAxisPerpendicularToNarys(axis3D)) {
                println("⚠️ Osa musí být kolmá k nárysně.")
                return
            }

            state.selectedRevolutionAxis3D = axis3D
            setProjectionPhase("rev_meridian_select_pudorys", state)
            println("✅ Osa vybrána. Klikáním vybírej poledník v půdorysu.")
        }

        "rev_meridian_select_pudorys" -> {
            val axis = state.selectedRevolutionAxis3D ?: return

            val hitCircle = state.hoveredCirclePudorys
            if (hitCircle != null) {
                finalizeRevolutionFromCirclePudorys(state, axis, hitCircle)
                return
            }

            val hitCurveId = state.selectedCurvePudorysId
            val hitCurve = state.curvesPudorys.firstOrNull { it.id == hitCurveId }
            if (hitCurve != null) {
                finalizeRevolutionFromCurvePudorys(state, axis, hitCurve)
                return
            }

            // ✅ ONE-SHOT: obecná kuželosečka v půdorysu
            // ObjectList vybírá celou rodinu kuželosečky najednou (viz
            // toggleSelectionConic2DFamilyByParentId), takže fallback stačí na půdorysný průmět.
            val hitConic = state.snappedConicPudorys ?: state.selectedConicsPudorys.lastOrNull()
            if (hitConic != null && !hitConic.isHelpCircle) {
                finalizeRevolutionFromConicPudorys(state, axis, hitConic)
                return
            }

            val hit = state.snappedSegmentPudorys ?: state.snappedArcPudorys
            when (hit) {
                is SegmentsPudorys -> {
                    toggleInList(state.selectedMeridianPudorysIds, hit.id)
                    println("přidána úsečka ${hit.id}")
                }
                is Arc2DPudorys -> {
                    toggleInList(state.selectedMeridianPudorysIds, hit.id)
                }
                else -> return
            }
        }
    }
}

fun finalizeRevolutionFromCurvePudorys(
    state: MongeState,
    axis: Line3D,
    curve: CurvePudorys
) {
    if (!isAxisPerpendicularToNarys(axis)) {
        println("⚠️ Osa musí být kolmá k nárysně.")
        return
    }

    val controlXY: List<Offset> = curve.points.mapNotNull { ref ->
        resolveCurvePudRefToOffset(state, ref)
    }

    if (controlXY.size < 2) {
        println("❌ Křivka nemá dost bodů.")
        return
    }

    val sampledXY = sampleCatmullRom(
        points = controlXY,
        stepsPerSegment = 28,
        closed = curve.closed
    )
    if (sampledXY.size < 2) return

    val axisX0 = axis.start.x
    val rs = sampledXY.map { kotlin.math.abs(it.x - axisX0) }
    println("sampled=${sampledXY.size} rMin=${rs.minOrNull()} rMax=${rs.maxOrNull()} joinTol=${state.snapThreshold / state.scale}")
    println("first=${sampledXY.first()} last=${sampledXY.last()}")

    val rMax = sampledXY.maxOf { kotlin.math.abs(it.x - axisX0) }

    val mirroredCurveId = createMirroredCurvePudorys(
        state = state,
        original = curve,
        axisX0 = axisX0,
        namePrefix = ""
    )

    val res = addSilhouetteAndNeckCirclesFromCurveSamplePudorys(
        state = state,
        axis = axis,
        sampledXY = sampledXY,
        closed = curve.closed,
        baseLabelMax = "Obrys",
        baseLabelMin = "Hrdlo"
    )

    val solid = SolidOfRevolutionPudorys(
        id = UUID.randomUUID().toString(),
        axisLine3DId = axis.id,
        meridianIdsPudorys = listOf(curve.id),
        mirroredMeridianIdsPudorys = listOf(mirroredCurveId).filter { it.isNotBlank() },
        rMax = rMax,
        neckRadii = res.neckRadii,
        circleIdsPudorys = res.circleIdsPudorys,
        circleIdsNarys = res.circleIdsNarys,
        sampledMeridianPolylineXY = sampledXY,
        creationIndex = allocIndex(state),
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    state.solidsOfRevolutionPudorys.add(solid)
    val curve = state.curvesPudorys.filter { it.id in listOf(curve.id, mirroredCurveId) }
    curve.forEach { curve ->
        curve.color = solid.color
        curve.strokeWidth = solid.strokeWidth
    }
    commitSnapshot(state)

    state.selectedMeridianPudorysIds.clear()
    state.selectedRevolutionAxis3D = null
    resetStavu(state)
    setProjectionPhase("rev_axis_select_pudorys", state)

    println("✅ Rotační plocha vytvořena z CurvePudorys.")
}

/**
 * ONE-SHOT vytvoření rotační plochy z obecné kuželosečky v půdorysu
 * (elipsa / parabola / hyperbola, včetně jejich oblouků).
 * Kružnice bez ořezu se deleguje na anuloidový flow.
 */
fun finalizeRevolutionFromConicPudorys(
    state: MongeState,
    axis: Line3D,
    conic: ConicSectionPudorys
) {
    if (!isAxisPerpendicularToNarys(axis)) {
        println("⚠️ Osa musí být kolmá k nárysně.")
        return
    }

    val hasTrim = state.ellipseArcEnds.containsKey(conic.id) ||
            state.parabolaArcEnds.containsKey(conic.id) ||
            state.hyperbolaArcBranch1.containsKey(conic.id) ||
            state.hyperbolaArcBranch2.containsKey(conic.id)

    if (!hasTrim && conic.toCircleXYOrNull() != null) {
        finalizeRevolutionFromCirclePudorys(state, axis, conic)
        return
    }

    val sample = sampleConicMeridianPudorys(state, conic) ?: run {
        println("❌ Kuželosečku nelze použít jako meridián (degenerovaná nebo chybí vstupy).")
        return
    }

    val axisX0 = axis.start.x
    val sampledXY =
        if (sample.closed) rotateClosedSampleAwayFromRadiusExtremum(sample.polyline, axisX0)
        else sample.polyline
    if (sampledXY.size < 2) return

    val joinTol = state.snapThreshold / state.scale
    val rMax = sampledXY.maxOf { abs(it.x - axisX0) }
    if (rMax <= joinTol * 0.75f) {
        println("❌ Meridián leží na ose rotace.")
        return
    }

    val mirroredConicId = createMirroredConicPudorys(state, conic, axisX0)

    val res = addSilhouetteAndNeckCirclesFromCurveSamplePudorys(
        state = state,
        axis = axis,
        sampledXY = sampledXY,
        closed = sample.closed,
        baseLabelMax = "Obrys",
        baseLabelMin = "Hrdlo"
    )

    val solid = SolidOfRevolutionPudorys(
        id = UUID.randomUUID().toString(),
        axisLine3DId = axis.id,
        meridianIdsPudorys = listOf(conic.id),
        mirroredMeridianIdsPudorys = listOf(mirroredConicId).filter { it.isNotBlank() },
        rMax = rMax,
        neckRadii = res.neckRadii,
        circleIdsPudorys = res.circleIdsPudorys,
        circleIdsNarys = res.circleIdsNarys,
        sampledMeridianPolylineXY = sampledXY,
        creationIndex = allocIndex(state),
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    state.solidsOfRevolutionPudorys.add(solid)
    commitSnapshot(state)

    state.selectedMeridianPudorysIds.clear()
    state.selectedRevolutionAxis3D = null
    repeatCons(state)

    println("✅ Rotační plocha vytvořena z kuželosečky (půdorys). rMax=$rMax closed=${sample.closed}")
}

private fun mirrorCirclePudorysAcrossAxisX(
    src: ConicSectionPudorys,
    axisX0: Float,
    state: MongeState
): ConicSectionPudorys {
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
        parent = null,
        localColor = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
    )
}


fun addSilhouetteAndNeckCirclesFromCurveSamplePudorys(
    state: MongeState,
    axis: Line3D,
    sampledXY: List<Offset>,
    closed: Boolean,
    baseLabelMax: String = "Obrys",
    baseLabelMin: String = "Hrdlo",
): SilhouetteAndNecksResult {

    val axisX0 = axis.start.x
    val joinTol = state.snapThreshold / state.scale

    val extrema = findRadiusExtremaByTrendXY(
        sampledXY = sampledXY,
        axisX0 = axisX0,
        closed = closed,
        epsR = joinTol * 0.005f,
        includeEndpoints = true
    )

    val maxes = extrema.filter { it.type == ExtremumType.MAX }
    val mins = extrema.filter { it.type == ExtremumType.MIN }

    val idsP = mutableListOf<String>()
    val idsN = mutableListOf<String>()
    val necks = mutableListOf<Float>()

    fun addCircle(y: Float, r: Float, label: String,stylen: LineStyle= LineStyle.Solid,stylep: LineStyle= LineStyle.Solid) {
        val (pud, nar) = addPerpCircleToAxis_WithInputs_Pudorys(
            state = state,
            axis = axis,
            yLevel = y,
            radius = r,
            rawName = label,
            stylen = stylen,
            stylep = stylep,
            widthOverride = state.currentLineStyleSettings.strokeWidth,
            colorOverride = state.currentLineStyleSettings.color
        )
        idsP += pud.id
        idsN += nar.id
    }

    maxes.forEachIndexed { idx, e ->
        val label = if (maxes.size == 1) baseLabelMax else "$baseLabelMax${idx + 1}"
        addCircle(e.p.y, e.r, label,stylep = LineStyle.Dashed,stylen = LineStyle.Solid)
    }

    mins.forEachIndexed { idx, e ->
        val label = if (mins.size == 1) baseLabelMin else "$baseLabelMin${idx + 1}"
        addCircle(e.p.y, e.r, label,stylep = LineStyle.Dashed,stylen = LineStyle.Solid)
        necks += e.r
    }

    return SilhouetteAndNecksResult(
        circleIdsPudorys = idsP,
        circleIdsNarys = idsN,
        neckRadii = necks
    )
}



fun findRadiusExtremaByTrendXY(
    sampledXY: List<Offset>,
    axisX0: Float,
    closed: Boolean,
    epsR: Float,
    includeEndpoints: Boolean = true
): List<RadiusExtremum> {
    if (sampledXY.size < 3) return emptyList()

    fun rOf(p: Offset) = kotlin.math.abs(p.x - axisX0)
    val n = sampledXY.size
    val r = FloatArray(n) { i -> rOf(sampledXY[i]) }

    fun signDr(d: Float): Int = when {
        d > epsR -> 1
        d < -epsR -> -1
        else -> 0
    }

    val s = IntArray(n) { 0 }
    for (i in 1 until n) {
        s[i] = signDr(r[i] - r[i - 1])
    }

    var last = 0
    for (i in 1 until n) {
        if (s[i] != 0) last = s[i] else s[i] = last
    }

    var next = 0
    for (i in (n - 1) downTo 1) {
        if (s[i] != 0) next = s[i] else s[i] = next
    }

    val out = mutableListOf<RadiusExtremum>()

    for (i in 2 until n) {
        val prev = s[i - 1]
        val cur = s[i]
        val idx = i - 1

        if (prev == 1 && cur == -1) {
            out += RadiusExtremum(idx, sampledXY[idx], r[idx], ExtremumType.MAX)
        } else if (prev == -1 && cur == 1) {
            out += RadiusExtremum(idx, sampledXY[idx], r[idx], ExtremumType.MIN)
        }
    }

    if (!closed && includeEndpoints) {
        val firstTrend = s.firstOrNull { it != 0 } ?: 0
        val lastTrend = s.drop(1).lastOrNull { it != 0 } ?: 0

        val t0 = if (firstTrend < 0) ExtremumType.MAX else ExtremumType.MIN
        val tN = if (lastTrend > 0) ExtremumType.MAX else ExtremumType.MIN

        out += RadiusExtremum(0, sampledXY[0], r[0], t0)
        out += RadiusExtremum(n - 1, sampledXY[n - 1], r[n - 1], tN)
    }

    return out
        .distinctBy { it.index to it.type }
        .sortedBy { it.index }
}
private fun resolveCurvePudRefToOffset(
    state: MongeState,
    ref: CurvePudRef
): Offset? {
    return when (ref) {
        is CurvePudRef.P -> {
            val p = state.pointsPudorys.firstOrNull { it.id == ref.pointId } ?: return null
            Offset(p.x, p.y)
        }
        is CurvePudRef.A -> {
            val a = state.aidPointsLogical.firstOrNull { it.id == ref.aidId } ?: return null
            Offset(a.x, a.y)
        }
    }
}
fun createMirroredCurvePudorys(
    state: MongeState,
    original: CurvePudorys,
    axisX0: Float,
    namePrefix: String = ""
): String {

    if (original.points.size < 2) return ""

    val mirroredRefs: List<CurvePudRef> = original.points.mapNotNull { ref ->
        when (ref) {
            is CurvePudRef.P -> {
                val p = state.pointsPudorys.firstOrNull { it.id == ref.pointId } ?: return@mapNotNull null

                val newId = UUID.randomUUID().toString()
                val mp = p.copy(
                    id = newId,
                    x = (2f * axisX0) - p.x,
                    y = p.y,
                    name = "",
                    creationIndex = allocIndex(state)
                )
                state.pointsPudorys.add(mp)

                CurvePudRef.P(newId)
            }

            is CurvePudRef.A -> {
                val a = state.aidPointsLogical.firstOrNull { it.id == ref.aidId } ?: return@mapNotNull null

                val newId = UUID.randomUUID().toString()
                val ma = a.copy(
                    id = newId,
                    x = (2f * axisX0) - a.x,
                    y = a.y,
                    name = "",
                    creationIndex = allocIndex(state)
                )
                state.aidPointsLogical.add(ma)

                CurvePudRef.A(newId)
            }
        }
    }

    if (mirroredRefs.size < 2) return ""

    val mirroredCurveId = UUID.randomUUID().toString()

    val mirrored = original.copy(
        id = mirroredCurveId,
        points = mirroredRefs,
        name = if (original.name.isBlank()) "" else "$namePrefix${original.name}",
        creationIndex = allocIndex(state),
        lineStyle = LineStyle.Dashed,
        strokeWidth = state.currentLineStyleSettings.strokeWidth
    )

    state.curvesPudorys.add(mirrored)
    return mirroredCurveId
}
private fun applyOuterSemicirclePudorys(
    circle: ConicSectionPudorys,
    axisX0: Float,
    state: MongeState
) {
    val xc = -circle.d / 2f
    val zc = -circle.e / 2f
    val r2 = xc * xc + zc * zc - circle.f
    if (r2 <= 0f) return

    val r = sqrt(r2)

    // nárysová "logical" soustava pro kreslení = (x, -z)
    val centerLogical = Offset(xc, zc)

    val top = Offset(centerLogical.x, centerLogical.y - r)
    val bottom = Offset(centerLogical.x, centerLogical.y + r)

    state.circleArcEnds[circle.id] = top to bottom

    // střed vpravo od osy -> chceme pravou půlku
    // střed vlevo od osy  -> chceme levou půlku
    state.circleArcMode[circle.id] =
        if (xc >= axisX0) ArcMode.CCW else ArcMode.CW
}