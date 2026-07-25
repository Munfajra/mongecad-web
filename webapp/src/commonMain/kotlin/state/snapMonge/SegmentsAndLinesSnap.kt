package state.snapMonge

import androidx.compose.ui.geometry.Offset
import model.*
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.*
import utils.dotProduct
import utils.getLogicalCursor
import utils.toScreen
import kotlin.math.*


fun projectPointOntoLine(p: Offset, line: Pair<Offset, Offset>): Offset {
    val (a, b) = line
    val ab = b - a
    val ap = p - a
    val abLenSq = ab.x * ab.x + ab.y * ab.y
    if (abLenSq == 0f) return a
    val t = (ap.x * ab.x + ap.y * ab.y) / abLenSq
    return a + ab * t
}
//výpočet průsečíků přímek
fun computeIntersection(p1: Offset, d1: Offset, p2: Offset, d2: Offset): Offset? {
    val det = d1.x * d2.y - d1.y * d2.x
    if (det == 0f) return null // přímky rovnoběžné

    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    val t = (dx * d2.y - dy * d2.x) / det

    return Offset(p1.x + t * d1.x, p1.y + t * d1.y)
}
fun intersectInfiniteLineWithSegment(
    linePoint: Offset,
    lineDir: Offset,
    segA: Offset,
    segB: Offset
): Offset? {
    val p = linePoint
    val r = lineDir
    val q = segA
    val s = segB - segA

    val rxs = r.x * s.y - r.y * s.x
    if (abs(rxs) < 1e-6f) return null // rovnoběžné

    val qp = q - p

    val t = (qp.x * s.y - qp.y * s.x) / rxs  // pozice na přímce (p + t·r)
    val u = (qp.x * r.y - qp.y * r.x) / rxs  // pozice na úsečce (q + u·s)

    if (u !in 0f..1f) return null // mimo úsečku

    return p + r * t
}
fun intersectSegments(
    a1: Offset, a2: Offset,
    b1: Offset, b2: Offset
): Offset? {
    val da = a2 - a1
    val db = b2 - b1
    val dp = a1 - b1
    val dap = Offset(-da.y, da.x)
    val denom = dap.x * db.x + dap.y * db.y
    if (abs(denom) < 1e-6f) return null

    val num = dap.x * dp.x + dap.y * dp.y
    val t = num / denom
    val intersection = b1 + db * t

    // zkontroluj, že leží i na první úsečce
    val s = ((intersection - a1).dotProduct(da)) / da.getDistanceSquared()
    if (s < 0f || s > 1f) return null

    return intersection
}

fun getSnappedPointOnTemporaryLine(
    state: state.MongeState,
    cursorScreenPosition: Offset,
    origin: Offset,
    dir: Offset
): Offset? {
    val dirLen = dir.getDistance()
    if (dirLen < 1e-6f) return origin

    val unit = Offset(dir.x / dirLen, dir.y / dirLen)
    val logicalCursor = getLogicalCursor(
        null,
        cursorScreenPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val projection = origin + unit * ((logicalCursor - origin).dotProduct(unit))
    val screenProjection = projection.toScreen(
        state.scale,
        state.canvasOffset,
        state.canvasHeight,
        state,
        state.canvasWidth
    )
    val intersectionCandidates = mutableListOf<Offset>()
    val tempPoint = Point3DPudorys(origin.x, origin.y)
val tempLine = Line3DProjectionPudorys(tempPoint, dir)
    // Přímky
    for (line in state.lines3DPudorys + state.lineTracesPudorys+state.helpLinePudorys) {
        val start = Offset(line.point.x, line.point.y)
        val d = line.direction
        computeIntersection(origin, dir, start, d)?.let {
            if (pudorysLineVisibleContains(state, line, it)) intersectionCandidates += it
        }
    }

    // Úsečky
    for (seg in state.segmentsPudorys + state.helpSegmentsPudorys) {
        val a = Offset(seg.start.x, seg.start.y)
        val b = Offset(seg.end.x, seg.end.y)
        val ab = b - a
        computeIntersection(origin, dir, a, ab)?.let { inter ->
            val t = ((inter - a).dotProduct(ab)) / ab.getDistanceSquared()
            if (t in 0f..1f) intersectionCandidates += inter
        }
    }

    // Oblouky
    for (arc in state.arcsPudorys) {
        val arcInters = intersectLineWithArcPudorys(origin, dir, arc)
        intersectionCandidates += arcInters
    }
    //Kružnice
    for (circle in state.circlesPudorys) {          // pole/název přizpůsobte tomu, jak kružnice ve stavu skutečně držíte
        val circleInters = intersectLineWithCirclePudorys(
            line = tempLine, // nebo jiný wrapper, pokud potřebujete „pojmenovanou“ přímku
            circle = circle
        )
        intersectionCandidates += circleInters
    }
    // Kuželosečky
    for (conic in state.conicsPudorys) {
        val conicInters = intersectLineWithConicPudorys(
            line = tempLine,
            conic = conic
        )
        intersectionCandidates += conicInters
    }
    // Body
    val tolLogical = state.snapThreshold / state.scale

    val hoveredPointPudorys = state.pointsPudorys.minByOrNull { pt ->
        val ptScreen = Offset(pt.x, pt.y).toScreen(
            state.scale,
            state.canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (ptScreen - cursorScreenPosition).getDistance()
    }?.takeIf { pt ->
        val ptScreen = Offset(pt.x, pt.y).toScreen(
            state.scale,
            state.canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (ptScreen - cursorScreenPosition).getDistance() <= state.snapThreshold
    }

    val pointCandidates = if (hoveredPointPudorys != null) {
        val ptLogical = Offset(hoveredPointPudorys.x, hoveredPointPudorys.y)
        val projLen = (ptLogical - origin).dotProduct(unit) // unit z tvé dočasné přímky v půdorysu
        val projPt  = origin + unit * projLen
        listOf(projPt)
    } else {
        state.pointsPudorys.mapNotNull { pt ->
            val ptLogical = Offset(pt.x, pt.y)
            val projLen = (ptLogical - origin).dotProduct(unit)
            val projPt  = origin + unit * projLen
            val perpDist = (projPt - ptLogical).getDistance()
            if (perpDist <= tolLogical) projPt else null
        }
    }
    val hoveredAidPointPudorys = state.aidPointsLogical.minByOrNull { pt ->
        val ptScreen = Offset(pt.x, pt.y).toScreen(
            state.scale,
            state.canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (ptScreen - cursorScreenPosition).getDistance()
    }?.takeIf { pt ->
        val ptScreen = Offset(pt.x, pt.y).toScreen(
            state.scale,
            state.canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (ptScreen - cursorScreenPosition).getDistance() <= state.snapThreshold
    }
    val aidpointCandidates = if (hoveredAidPointPudorys != null) {
        val ptLogical = Offset(hoveredAidPointPudorys.x, hoveredAidPointPudorys.y)
        val projLen = (ptLogical - origin).dotProduct(unit) // unit z tvé dočasné přímky v půdorysu
        val projPt  = origin + unit * projLen
        listOf(projPt)
    } else {
        state.aidPointsLogical.mapNotNull { pt ->
            val ptLogical = Offset(pt.x, pt.y)
            val projLen = (ptLogical - origin).dotProduct(unit)
            val projPt  = origin + unit * projLen
            val perpDist = (projPt - ptLogical).getDistance()
            if (perpDist <= tolLogical) projPt else null
        }
    }
    // Kombinace a výběr nejbližšího na obrazovce
    val snapped = (intersectionCandidates + pointCandidates+aidpointCandidates)
        .minByOrNull {
            it.toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            ).minus(screenProjection).getDistance() }
        ?.takeIf {
            it.toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            ).minus(screenProjection).getDistance() <= state.snapThreshold }

    return snapped
}

fun getSnappedPointOnTemporaryLineNarys(
    state: state.MongeState,
    cursorScreenPosition: Offset,
    origin: Offset, // v logických souřadnicích, tedy x, -z
    dir: Offset     // směr přímky v logických souřadnicích (např. x, -z)
): Offset? {
    val scale = state.scale
    val canvasOffset = state.canvasOffset
    val snapThreshold = state.snapThreshold

    val dirLen = dir.getDistance()
    if (dirLen < 1e-6f) return origin

// ⬅️ OPRAVA: invertujeme y-složku kvůli z = -y
    val dirCorrected = Offset(dir.x, -dir.y)
    val unit = dirCorrected / dirLen
    val tempPoint = Point3DNarys(origin.x, -origin.y)
    val tempLine = Line3DProjectionNarys(tempPoint, dir)
    val logicalCursor = getLogicalCursor(
        null,
        cursorScreenPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    val projection = origin + unit * ((logicalCursor - origin).dotProduct(unit))

    val screenProjection =
        projection.toScreen(scale, canvasOffset, state.canvasHeight, state, state.canvasWidth)

    val intersectionCandidates = mutableListOf<Offset>()

    // 🟦 Přímky
    for (line in state.lines3DNarys + state.lineTracesNarys+state.helpLineNarys) {
        val start = Offset(line.point.x, -line.point.z)
        val d = Offset(line.direction.x, -line.direction.y)
        computeIntersection(origin, dirCorrected, start, d)?.let {
            if (narysLineVisibleContains(state, line, Offset(it.x, -it.y))) intersectionCandidates += it
        }
    }

    // 🟧 Úsečky
    for (seg in state.segmentsNarys + state.helpSegmentsNarys) {
        val a = Offset(seg.start.x, -seg.start.z)
        val b = Offset(seg.end.x, -seg.end.z)
        val ab = b - a
        computeIntersection(origin, dirCorrected, a, ab)?.let { inter ->
            val t = ((inter - a).dotProduct(ab)) / ab.getDistanceSquared()
            if (t in 0f..1f) intersectionCandidates += inter
        }
    }

    // 🟩 Oblouky
    for (arc in state.arcsNarys) {
        intersectionCandidates += intersectLineWithArcNarys(origin, dirCorrected, arc)
    }
    //Kružnice
    for (circle in state.circlesNarys) {
        val circleInters = intersectLineWithCircleNarys(
            line = tempLine,
            circle = circle
        )
        intersectionCandidates += circleInters
    }
    // 🔵 Kuželosečky
    for (conic in state.conicsNarys) {
        val conicInters = intersectLineWithConicNarys(
            line = tempLine,
            conic = conic
        )
        intersectionCandidates += conicInters
    }

    // 🟨 Body
    val tolLogical = snapThreshold / scale

// 1) Zkus najít nejbližší "hoverovaný" bod v PX
    val hoveredPointNarys = state.pointsNarys.minByOrNull { pt ->
        val ptScreen = Offset(pt.x, -pt.z).toScreen(
            scale,
            canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (ptScreen - cursorScreenPosition).getDistance()
    }?.takeIf { pt ->
        val ptScreen = Offset(pt.x, -pt.z).toScreen(
            scale,
            canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (ptScreen - cursorScreenPosition).getDistance() <= snapThreshold
    }

    val pointCandidates: List<Offset> =
        if (hoveredPointNarys != null) {
            // 2) Máme hoverovaný bod → přidej jeho projekci na dočasnou přímku (bez ohledu na vzdálenost od přímky)
            val ptLogical = Offset(hoveredPointNarys.x, -hoveredPointNarys.z)
            val projLen = (ptLogical - origin).dotProduct(unit)
            val projPt  = origin + unit * projLen
            listOf(projPt)
        } else {
            // 3) Fallback: jako dřív – jen body blízko přímky (v logických jednotkách)
            state.pointsNarys.mapNotNull { pt ->
                val ptLogical = Offset(pt.x, -pt.z)
                val delta = ptLogical - origin
                val projLen = delta.dotProduct(unit)
                val projPt  = origin + unit * projLen
                val perpDist = (projPt - ptLogical).getDistance()
                if (perpDist <= tolLogical) projPt else null
            }
        }
    val hoveredAidPointNarys = state.aidPointsLogical.minByOrNull { pt ->
        val ptScreen = Offset(pt.x, pt.y).toScreen(
            scale,
            canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (ptScreen - cursorScreenPosition).getDistance()
    }?.takeIf { pt ->
        val ptScreen = Offset(pt.x, pt.y).toScreen(
            scale,
            canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (ptScreen - cursorScreenPosition).getDistance() <= snapThreshold
    }
    val aidpointCandidates: List<Offset> =
        if (hoveredAidPointNarys != null) {
            // 2) Máme hoverovaný bod → přidej jeho projekci na dočasnou přímku (bez ohledu na vzdálenost od přímky)
            val ptLogical = Offset(hoveredAidPointNarys.x, -hoveredAidPointNarys.y)
            val projLen = (ptLogical - origin).dotProduct(unit)
            val projPt  = origin + unit * projLen
            listOf(projPt)
        } else {
            // 3) Fallback: jako dřív – jen body blízko přímky (v logických jednotkách)
            state.aidPointsLogical.mapNotNull { pt ->
                val ptLogical = Offset(pt.x, pt.y)
                val delta = ptLogical - origin
                val projLen = delta.dotProduct(unit)
                val projPt  = origin + unit * projLen
                val perpDist = (projPt - ptLogical).getDistance()
                if (perpDist <= tolLogical) projPt else null
            }
        }


    // 📌 Výběr nejbližšího
    val allCandidates = intersectionCandidates + pointCandidates + aidpointCandidates
    val snapped = allCandidates.minByOrNull {
        (it.toScreen(
            scale,
            canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        ) - screenProjection).getDistance()
    }?.takeIf {
        (it.toScreen(
            scale,
            canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        ) - screenProjection).getDistance() <= snapThreshold
    }

    return snapped
}
fun intersectLineWithCircle(a: Offset, dir: Offset, center: Offset, radius: Float): List<Offset> {
    val dx = dir.x
    val dy = dir.y
    val fx = a.x - center.x
    val fy = a.y - center.y

    val aOr = dx * dx + dy * dy
    val bOr = 2 * (fx * dx + fy * dy)
    val cOr = fx * fx + fy * fy - radius * radius

    val dOr = bOr * bOr - 4 * aOr * cOr
    if (dOr < 0) return emptyList()

    val sqrtD = sqrt(dOr)
    val t1 = (-bOr + sqrtD) / (2 * aOr)
    val t2 = (-bOr - sqrtD) / (2 * aOr)

    return listOf(
        a + dir * t1,
        a + dir * t2
    )
}
fun intersectSegmentWithCircle(a: Offset, b: Offset, center: Offset, radius: Float): List<Offset> {
    val dir = b - a
    val candidates = intersectLineWithCircle(a, dir, center, radius)
    return candidates.filter { pt ->
        val t = ((pt - a).dotProduct(dir)) / dir.getDistanceSquared()
        t in 0f..1f
    }
}
private fun syntheticCirclePudorys(center: Offset, r: Float) = ConicSectionPudorys(
    a = 1f, b = 0f, c = 1f,
    d = -2f * center.x,
    e = -2f * center.y,
    f = center.x * center.x + center.y * center.y - r * r,
    rawName = "k"
)

private fun syntheticCircleNarys(centerImage: Offset, r: Float): ConicSectionNarys {
    // v NÁRYSU je y_obraz = -z, takže z0 = -centerImage.y
    val x0 = centerImage.x
    val z0 = -centerImage.y
    return ConicSectionNarys(
        a = 1f, b = 0f, c = 1f,
        d = -2f * x0,
        e = -2f * z0,
        f = x0 * x0 + z0 * z0 - r * r,
        rawName = "k"
    )
}
fun snapToCircleWithFallback(
    worldCursor: Offset,
    state: state.MongeState
): Offset? {
    // aktivní jen v režimu měření vzdálenosti – jinak nezasahuj
    if (state.drawobjects != Mongeobjects.GETDISTANCE ||
        state.projectionPhase != "distance_target_place" ||
        state.pendingPoint3 == null ||
        state.pendingDistance == null) return null

    val center = state.pendingPoint3!!
    val radius = state.pendingDistance!!
    val tol    = state.snapThreshold / state.scale

    val snapCandidates = mutableListOf<Pair<Offset, Float>>()

    // ───────────── N Á R Y S ─────────────
    if (state.mongeMode == DrawingModeMonge.NARYS) {
        // logické "kruhové" centrum v nárysu (x, y_obraz) = (x, -z)
        val syntheticCenterImg = Offset(center.x, center.y)
        val synCircleN = syntheticCircleNarys(syntheticCenterImg, radius)

        // body
        state.pointsNarys.forEach { pt ->
            val pZ = Offset(pt.x, pt.z) // (x, z)
            val d = (pZ - Offset(syntheticCenterImg.x, -syntheticCenterImg.y)).getDistance() // (x, z) vs (x, z0)
            if (abs(d - radius) <= tol) {
                val logical = Offset(pt.x, -pt.z) // převod zpět do (x, y_obraz)
                snapCandidates += logical to (logical - worldCursor).getDistance()
            }
        }

        // oblouky × syntetická kružnice (tvůj helper)
        state.arcsNarys.forEach { arc ->
            val xs = intersectArcWithCircleNarys(arc, synCircleN) // vrací v logickém nárysu (x, y_obraz)
            for (pt in xs) {
                snapCandidates += pt to (pt - worldCursor).getDistance()
            }
        }

        // přímky
        state.allLinesNarys.forEach { line ->
            val a   = Offset(line.point.x, line.point.z)                  // (x, z)
            val dir = Offset(line.direction.x, line.direction.y)          // (x, z)
            val xs  = intersectLineWithCircle(a, dir,
                Offset(syntheticCenterImg.x, -syntheticCenterImg.y), radius)
            for (pt in xs) {
                if (!narysLineVisibleContains(state, line, pt)) continue
                val logical = Offset(pt.x, -pt.y) // (x, z) -> (x, y_obraz)
                snapCandidates += logical to (logical - worldCursor).getDistance()
            }
        }

        // úsečky
        (state.segmentsNarys + state.helpSegmentsNarys).forEach { seg ->
            val a = Offset(seg.start.x, seg.start.z)
            val b = Offset(seg.end.x,   seg.end.z)
            val xs = intersectSegmentWithCircle(a, b,
                Offset(syntheticCenterImg.x, -syntheticCenterImg.y), radius)
            for (pt in xs) {
                val logical = Offset(pt.x, -pt.y)
                snapCandidates += logical to (logical - worldCursor).getDistance()
            }
        }

        // kružnice × syntetická kružnice (tvůj helper)
        state.circlesNarys.forEach { circ ->
            val xs = intersectCirclesNarys(synCircleN, circ) // vrací (x, y_obraz)
            for (pt in xs) {
                snapCandidates += pt to (pt - worldCursor).getDistance()
            }
        }
    }

    // ───────────── P Ů D O R Y S ─────────────
    if (state.mongeMode == DrawingModeMonge.PUDORYS) {
        val synCircleP = syntheticCirclePudorys(center, radius)

        // body
        state.pointsPudorys.forEach { pt ->
            val p = Offset(pt.x, pt.y)
            val d = (p - center).getDistance()
            if (abs(d - radius) <= tol) {
                snapCandidates += p to (p - worldCursor).getDistance()
            }
        }

        // oblouky × syntetická kružnice
        state.arcsPudorys.forEach { arc ->
            val xs = intersectArcWithCirclePudorys(arc, synCircleP) // vrací (x, y)
            for (pt in xs) {
                snapCandidates += pt to (pt - worldCursor).getDistance()
            }
        }

        // přímky
        state.allLinesPudorys.forEach { line ->
            val a = Offset(line.point.x, line.point.y)
            val dir = line.direction
            val xs = intersectLineWithCircle(a, dir, center, radius)
            for (pt in xs) {
                if (!pudorysLineVisibleContains(state, line, pt)) continue
                snapCandidates += pt to (pt - worldCursor).getDistance()
            }
        }

        // úsečky
        (state.segmentsPudorys + state.helpSegmentsPudorys).forEach { seg ->
            val a = Offset(seg.start.x, seg.start.y)
            val b = Offset(seg.end.x,   seg.end.y)
            val xs = intersectSegmentWithCircle(a, b, center, radius)
            for (pt in xs) {
                snapCandidates += pt to (pt - worldCursor).getDistance()
            }
        }

        // kružnice × syntetická kružnice
        state.circlesPudorys.forEach { circ ->
            val xs = intersectCirclesPudorys(synCircleP, circ) // vrací (x, y)
            for (pt in xs) {
                snapCandidates += pt to (pt - worldCursor).getDistance()
            }
        }
    }

    // vyber nejbližší kandidát v toleranci
// 1) nejdřív skutečné snap kandidáty: průsečíky, existující body atd.
    val closest = snapCandidates.minByOrNull { it.second }
    if (closest != null && closest.second <= tol) {
        return closest.first
    }

// 2) střed kružnice v aktuálním logickém pohledu
    val viewCenter = when (state.mongeMode) {
        DrawingModeMonge.NARYS -> Offset(center.x, center.y)
        DrawingModeMonge.PUDORYS -> center
    }

    // 3) snap-kříž podle směru, ne podle blízkosti ke konkrétnímu bodu na kružnici
    val crossTol = tol

    val dx = worldCursor.x - viewCenter.x
    val dy = worldCursor.y - viewCenter.y

    val horizontalDist = abs(dy) // blízkost k vodorovnému směru přes střed
    val verticalDist = abs(dx)   // blízkost ke svislému směru přes střed

    val crossSnapPoint: Offset? =
        when {
            // kurzor je blízko vodorovné osy středu
            horizontalDist <= crossTol && horizontalDist <= verticalDist -> {
                if (dx >= 0f) {
                    Offset(viewCenter.x + radius, viewCenter.y) // vpravo
                } else {
                    Offset(viewCenter.x - radius, viewCenter.y) // vlevo
                }
            }

            // kurzor je blízko svislé osy středu
            verticalDist <= crossTol -> {
                if (dy <= 0f) {
                    Offset(viewCenter.x, viewCenter.y - radius) // nahoře
                } else {
                    Offset(viewCenter.x, viewCenter.y + radius) // dole
                }
            }

            else -> null
        }

    if (crossSnapPoint != null) {
        return crossSnapPoint
    }

// pokud by kurzor byl přesně ve středu, vybereme třeba pravý bod
    if (abs(dx) < 1e-6f && abs(dy) < 1e-6f) {
        return Offset(viewCenter.x + radius, viewCenter.y)
    }

    val ang = atan2(dy, dx)
    return Offset(
        viewCenter.x + radius * cos(ang),
        viewCenter.y + radius * sin(ang)
    )
}
fun snapToCrossWithIntersectionsPudorys(
    state: state.MongeState,
    cursorScreenPosition: Offset,
    base: Offset
): Offset? {

    // kurzor v logických souřadnicích
    val logicalCursor = getLogicalCursor(
        null,
        cursorScreenPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    val thresholdLogical = state.snapThreshold / state.scale
    val dx = kotlin.math.abs(logicalCursor.x - base.x)
    val dy = kotlin.math.abs(logicalCursor.y - base.y)

    val preferVertical   = dx <= thresholdLogical && dx < dy
    val preferHorizontal = dy <= thresholdLogical

    if (!preferVertical && !preferHorizontal) return null

    return if (preferVertical) {
        // 🟦 svislý kříž: dočasná přímka přes base se směrem (0,1)
        getSnappedPointOnTemporaryLine(
            state = state,
            cursorScreenPosition = cursorScreenPosition,
            origin = base,
            dir = Offset(0f, 1f)
        ) ?: Offset(base.x, logicalCursor.y) // fallback na čistý kříž
    } else {
        // 🟧 vodorovný kříž: dočasná přímka přes base se směrem (1,0)
        getSnappedPointOnTemporaryLine(
            state = state,
            cursorScreenPosition = cursorScreenPosition,
            origin = base,
            dir = Offset(1f, 0f)
        ) ?: Offset(logicalCursor.x, base.y)
    }
}
fun snapToCrossWithIntersectionsNarys(
    state: state.MongeState,
    cursorScreenPosition: Offset,
    base: Offset
): Offset? {

    val scale = state.scale
    val snapThreshold = state.snapThreshold

    // kurzor v logických souřadnicích nárysu
    val logicalCursor = getLogicalCursor(
        null,
        cursorScreenPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    val thresholdLogical = snapThreshold / scale
    val dx = kotlin.math.abs(logicalCursor.x - base.x)
    val dy = kotlin.math.abs(logicalCursor.y - base.y)

    val preferVertical   = dx <= thresholdLogical && dx < dy
    val preferHorizontal = dy <= thresholdLogical

    if (!preferVertical && !preferHorizontal) return null

    return if (preferVertical) {
        // svislá dočasná přímka v nárysu
        getSnappedPointOnTemporaryLineNarys(
            state = state,
            cursorScreenPosition = cursorScreenPosition,
            origin = base,
            dir = Offset(0f, 1f)    // pozor: Narys varianta si y sama invertuje
        ) ?: Offset(base.x, logicalCursor.y)
    } else {
        // vodorovná dočasná přímka v nárysu
        getSnappedPointOnTemporaryLineNarys(
            state = state,
            cursorScreenPosition = cursorScreenPosition,
            origin = base,
            dir = Offset(1f, 0f)
        ) ?: Offset(logicalCursor.x, base.y)
    }
}
