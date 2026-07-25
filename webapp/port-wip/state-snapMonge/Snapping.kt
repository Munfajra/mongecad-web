package state.snapMonge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import export.pdfRenderer.sampleSmoothCurvePoints
import model.classes.CurvePudRef
import model.*
import model.classes.*
import model.classes.ConicSectionNarys
import model.classes.tryAsCircleGeom
import model.classes.tryAsCircleGeomXZ
import monge.input.axo.getLogicalCursorAxo
import monge.input.lines.customLineTrimSnapPoint
import ui.mongeui.toolbar.updateConstructionInfo
import utils.*
import kotlin.math.abs
import kotlin.math.sqrt

private const val SNAP_ELLIPSE_EPS = 1e-5f

private fun degenerateEllipseCarrierFromDiameters(p1: Offset, p2: Offset, p3: Offset): Pair<Offset, Offset>? {
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val u = (p2 - p1) / 2f
    val v = p3 - center
    val a = u.getDistance()
    val b = v.getDistance()
    val cross = abs(u.x * v.y - u.y * v.x)

    if (a >= SNAP_ELLIPSE_EPS && b >= SNAP_ELLIPSE_EPS && cross >= SNAP_ELLIPSE_EPS * a * b) {
        return null
    }

    val dirRaw = if (a >= b) u else v
    val len = dirRaw.getDistance()
    if (len < SNAP_ELLIPSE_EPS) return center to center

    val dir = dirRaw / len
    val radius = sqrt(a * a + b * b)
    return (center - dir * radius) to (center + dir * radius)
}

fun closestPointOnDegenerateEllipse(p1: Offset, p2: Offset, p3: Offset, cursor: Offset): Offset? {
    val (a, b) = degenerateEllipseCarrierFromDiameters(p1, p2, p3) ?: return null
    if ((b - a).getDistanceSquared() < SNAP_ELLIPSE_EPS * SNAP_ELLIPSE_EPS) return a
    return closestPointOnSegment(a, b, cursor)
}

/**
 * Nejbližší bod na vzorkované hladké křivce (lomená čára `sampled`) k logickému kurzoru.
 * Vrací `(projekce, vzdálenost v logice)` nebo null. Vzdálenost se měří v logických
 * souřadnicích – díky uniformnímu měřítku je `screen = logical * scale`.
 */
private fun nearestOnSampledCurve(cursorLogical: Offset, sampled: List<Offset>): Pair<Offset, Float>? {
    if (sampled.size < 2) return null
    var bestPt: Offset? = null
    var bestDistSq = Float.POSITIVE_INFINITY
    for (i in 0 until sampled.size - 1) {
        val a = sampled[i]
        val b = sampled[i + 1]
        val ab = b - a
        val lenSq = ab.getDistanceSquared()
        if (lenSq < 1e-9f) continue
        val ap = cursorLogical - a
        val t = ((ap.x * ab.x + ap.y * ab.y) / lenSq).coerceIn(0f, 1f)
        val proj = a + ab * t
        val dSq = (proj - cursorLogical).getDistanceSquared()
        if (dSq < bestDistSq) {
            bestDistSq = dSq
            bestPt = proj
        }
    }
    return bestPt?.let { it to sqrt(bestDistSq) }
}

fun screenDistance(state: state.MongeState, point: Offset, screenCursor: Offset): Float {
    val screen = point.toScreen(
        state.scale,
        state.canvasOffset,
        state.canvasHeight,
        state,
        state.canvasWidth
    )
    return (screen - screenCursor).getDistance()
}

fun computeSnappedPoint(state: state.MongeState, cursorPosition: Offset): Offset? {
    updateConstructionInfo(state)
    if (state.mongeMode== DrawingModeMonge.NARYS && state.projectionMode == ProjectionMode.KOTO) state.mongeMode = DrawingModeMonge.PUDORYS
    val screenCursor = state.cursorPosition

    val logicalCursor = if(state.projectionMode != ProjectionMode.AXO){ getLogicalCursor(
        null,
        screenCursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )} else {
        getLogicalCursorAxo(
            snapped = null,
            cursor = state.cursorPosition,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            flipX = false,
            flipY = false,
            mode = state.axoMode,
            axoModel = state.activeAxoModel
        )?: return null

    }
    state.hoveredArcPudorysId = null
    state.hoveredArcNarysId = null
    state.snappedCurvePudorys = null
    state.snappedCurveNarys = null
    val worldCursorN = Offset(logicalCursor.x,-logicalCursor.y)
    if (state.isCtrlPressed) return null

    customLineTrimSnapPoint(state)?.let { return it }

    when (val arcSnap = tryConstructionArcSnapPudorys(state, logicalCursor)) {
        is SnapDecision.Handled -> return arcSnap.point
        SnapDecision.NotHandled -> Unit
    }
    when (val arcSnap = tryConstructionArcSnapNarys(state, logicalCursor)) {
        is SnapDecision.Handled -> return arcSnap.point
        SnapDecision.NotHandled -> Unit
    }
    if (state.drawobjects == Mongeobjects.GETDISTANCE &&
        state.projectionPhase == "distance_target_place"
    ) {
        return snapToCircleWithFallback(logicalCursor, state)
    }
    when (val projectionSnap = tryAssociatedProjectionSnap(state, logicalCursor)) {
        is SnapDecision.Handled -> return projectionSnap.point
        SnapDecision.NotHandled -> Unit
    }

    when (val crossSnap = tryConstructionCrossSnap(state, cursorPosition)) {
        is SnapDecision.Handled -> return crossSnap.point
        SnapDecision.NotHandled -> Unit
    }


    when (val temporaryLineSnap = tryTemporaryLineSnap(state)) {
        is SnapDecision.Handled -> return temporaryLineSnap.point
        SnapDecision.NotHandled -> Unit
    }
    val isNearPoint = state.snappedPointPudorys != null
    if (!isNearPoint) {
        val aidSnap = state.findNearestAidPointLogical(
            cursorLogical = logicalCursor,
            snapRadiusLogical = state.snapThreshold / state.scale
        )

        if (aidSnap != null) {
            state.hoveredAidPointId = aidSnap.id
            return Offset(aidSnap.x,aidSnap.y)
        }

    }
    trySnapToNarysIntersections(state, screenCursor)?.let { return it }
    trySnapToPudorysIntersections(state, screenCursor)?.let { return it }

    val snapCandidates = mutableListOf<Pair<Offset, Float>>()

// Snapování pro NÁRYS nebo GETDISTANCE v PUDORYSU
    val enableNarysSnapping = state.projectionMode != ProjectionMode.KOTO

    if (enableNarysSnapping) {

// Oblouky
        state.snappedArcNarys = null
        if (state.showConstruction.value) {
        trySnapToArcNarys(
            logicalCursor = logicalCursor,
            arcs = state.arcsNarys,
            snapThreshold = state.snapThreshold,
            scale = state.scale,
            state
        )?.let { snap ->
            state.hoveredArcNarysId = snap.id
            snapCandidates += snap.snapped to 0f

        }

// Body
            state.snappedPointNarys = null
            state.pointsNarys.forEach { p ->
                val point = Offset(p.x, p.z)
                val world = Offset(point.x, -point.y)
                val screenPt = world.toScreen(
                    state.scale,
                    state.canvasOffset,
                    state.canvasHeight,
                    state,
                    state.canvasWidth
                )
                val d = (screenPt - screenCursor).getDistance()
                if (d <= state.snapThreshold) {
                    state.snappedPointNarys = p
                    snapCandidates += world to (d - state.snapThreshold * 0.6f)

                }
            }
        }




        // V nativním Mongeově nárysu se kreslí všechny lines3DNarys.
        // showInAxo řídí pouze přenesení průmětu do axonometrie a nesmí zde
        // vyřadit např. materializované tvořice přímkové plochy ze snapu.
        val allLines = state.lines3DNarys + state.lineTracesNarys +
            if (state.showConstruction.value) state.helpLineNarys else emptyList()
        state.snappedLineNarys = null
        allLines.forEach { line ->

            val dirXZ = Offset(line.direction.x, line.direction.y) // (x, z)
            val aXZ   = Offset(line.point.x, line.point.z)         // (x, z)
            val bXZ   = aXZ + dirXZ

            val projXZ = projectPointOntoLine(worldCursorN, aXZ to bXZ) // (x, z)

            // ✅ pokud je oříznutá nad x12, povol jen z >= 0
            if ((line.clipLineX==true||line.clipLineX==null )&& projXZ.y < 0f) return@forEach

            // (volitelně) pokud je clipAbove a celá přímka leží pod osou a je rovnoběžná s x12
            if ((line.clipLineX==true||line.clipLineX==null ) && dirXZ.y == 0f && aXZ.y < 0f) return@forEach
            if (!narysLineTrimContainsPoint(line, projXZ)) return@forEach

            val screen = Offset(projXZ.x, -projXZ.y).toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            )

            val dist = (screen - screenCursor).getDistance()
            if (dist <= state.snapThreshold) {
                snapCandidates += Offset(projXZ.x, -projXZ.y) to (dist - state.snapThreshold * 0.5f)
                state.snappedLineNarys = line
            }
        }

// Úsečky
        state.snappedSegmentNarys = null
        val allSegmentsNarys = if (state.showConstruction.value) {state.segmentsNarys + state.helpSegmentsNarys} else state.segmentsNarys
        allSegmentsNarys.forEach { segment ->
            val a = Offset(segment.start.x, segment.start.z) // Z nenegovaný
            val b = Offset(segment.end.x, segment.end.z)

            val ab = b - a
            val abLenSq = ab.getDistanceSquared()
            if (abLenSq >= 1e-6f) {
                val ap = worldCursorN - a
                val t = (ap.x * ab.x + ap.y * ab.y) / abLenSq
                val clampedT = t.coerceIn(0f, 1f)
                val proj = a + ab * clampedT

                val screen = Offset(proj.x, -proj.y).toScreen(
                    state.scale,
                    state.canvasOffset,
                    state.canvasHeight,
                    state,
                    state.canvasWidth
                ) // Z → -Y
                val dist = (screen - screenCursor).getDistance()

                if (dist <= state.snapThreshold) {
                    snapCandidates += Offset(proj.x, -proj.y) to (dist - state.snapThreshold * 0.5f)
                    state.snappedSegmentNarys = segment
                }
            }
        }
        // Kružnice
        val fcircles = if (state.showConstruction.value){ state.circlesNarys} else state.circlesNarys.filterNot{it.isHelpCircle}
        state.hoveredCircleNarys = null
        fcircles.forEach { circle ->
            val x0 = -circle.d / 2f
            val z0 = -circle.e / 2f
            val r2 = x0 * x0 + z0 * z0 - circle.f
            if (r2 > 0f) {
                val r = sqrt(r2)
                val center = Offset(x0, -z0) // převod Z → -Y
                val distToCursor = (logicalCursor - center).getDistance()
                val delta = abs(distToCursor - r)

                if (delta <= state.snapThreshold / state.scale) {
                    val dir = (logicalCursor - center).normalize()
                    val snap = center + dir * r
                    snapCandidates += snap to (delta+ state.snapThreshold * 0.5f)
                    if (state.drawobjects == Mongeobjects.NONE ||
                        state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION ||
                        state.drawobjects == Mongeobjects.RULED_SURFACE
                    ) {
                        state.hoveredCircleNarys = circle
                    }
                }
            }
        }
        // --- Kružnicové kuželosečky v nárysu (snap jako kružnice) ---
        val fConicsN = state.conicsNarys   // <- dosaď svůj seznam


        fConicsN.forEach { k ->
            val cg = k.tryAsCircleGeomXZ() ?: return@forEach

            val r = cg.r
            val center = Offset(cg.x0, -cg.z0) // ✅ flip Z → -Y stejně jako u kružnic

            val distToCursor = (logicalCursor - center).getDistance()
            val delta = abs(distToCursor - r)

            if (delta <= state.snapThreshold / state.scale) {
                val dir = (logicalCursor - center).normalize()
                val snap = center + dir * r
                snapCandidates += snap to (delta + state.snapThreshold * 0.5f)

                // hover si dej radši zvlášť, ať nemícháš "Circle" a "Conic"
                if (state.drawobjects == Mongeobjects.NONE ||
                    state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION ||
                    state.drawobjects == Mongeobjects.RULED_SURFACE
                ) {
                    state.snappedConicNarys = k
                }
            }
        }
        val snappedConics = mutableListOf<Pair<ConicSectionNarys, Float>>()
// Elipsy – nárys (ze vstupních bodů)
        // Kuželosečky – nárys (ze vstupních bodů)
        val conicN = snapConicNarys(state,logicalCursor)
        if (conicN != null)   snapCandidates.add(conicN.bestCandidatePoint to conicN.distanceToCursor)

        // Křivky (nárys) – jen pro výběr v módu NONE (nebo výběr poledníku rotační
        // plochy), snap je nepřesný → nejnižší priorita
        state.snappedCurveNarys = null
        if (state.drawobjects == Mongeobjects.NONE || state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION) {
            val pointsByIdNar = state.pointsNarys.associateBy { it.id }
            state.curvesNarys.forEach { curve ->
                val control = curve.polylineLocal ?: curve.pointIds.mapNotNull { id ->
                    pointsByIdNar[id]?.let { Offset(it.x, it.z) }
                }
                if (control.size < 2) return@forEach
                val sampled = if (curve.polylineLocal != null) control else sampleSmoothCurvePoints(control, curve.closed)
                val near = nearestOnSampledCurve(worldCursorN, sampled) ?: return@forEach
                val distScreen = near.second * state.scale
                if (distScreen <= state.snapThreshold) {
                    snapCandidates += Offset(near.first.x, -near.first.y) to (distScreen + state.snapThreshold * 0.5f)
                    state.snappedCurveNarys = curve
                }
            }
        }
    }

// Snapování pro PŮDORYS nebo GETDISTANCE v NÁRYSU
    val enablePudorysSnapping = true
    if (enablePudorysSnapping) {
        // 🔵 Body
        val sc = state.cursorPosition
        val thr = state.snapThreshold
        val screenBox = Rect(sc.x - thr, sc.y - thr, sc.x + thr, sc.y + thr)
        state.snappedPointPudorys = null
        state.hoveredAidPointId = null
        state.pointsPudorys.forEach { p ->
            val screenPt = Offset(p.x, p.y).toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            )
            if (!screenBox.contains(screenPt)) return@forEach

            val d = (screenPt - sc).getDistance()
            if (d <= thr) {
                state.snappedPointPudorys = p
                snapCandidates += Offset(p.x, p.y) to (d - thr * 0.6f)

            }
        }
        state.snappedArcPudorys = null
        if (state.showConstruction.value) {
            // 🟢 Oblouky
            trySnapToArcPudorys(
                logicalCursor = logicalCursor,
                arcs = state.arcsPudorys,
                snapThreshold = state.snapThreshold,
                scale = state.scale,
                state = state
            )?.let { snap ->
                state.hoveredArcPudorysId = snap.id
                snapCandidates += snap.snapped to 0f
            }

        }

        // 🔷 Přímky
        state.snappedLinePudorys = null
        val allinespudorys = state.lines3DPudorys + state.lineTracesPudorys +
            if (state.showConstruction.value) state.helpLinePudorys else emptyList()
        if (state.hoveredAidPointId == null){
        allinespudorys.forEach { line ->
            val a = Offset(line.point.x, line.point.y)
            val dir = line.direction
            val b = a + dir
            val proj = projectPointOntoLine(logicalCursor, a to b)
            val clipAbove = (line.clipLineX == true||line.clipLineX == null&&state.projectionMode == ProjectionMode.MONGE)
            if (clipAbove && proj.y < 0f) return@forEach

            // (volitelně) pokud je clipAbove a celá přímka leží pod osou a je rovnoběžná s x12
            if (clipAbove && dir.y == 0f && a.y < 0f) return@forEach
            if (!pudorysLineTrimContainsPoint(line, proj)) return@forEach
            val screen = proj.toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            )
            val dist = (screen - screenCursor).getDistance()

            if (dist <= state.snapThreshold) {
                snapCandidates += proj to dist
                state.snappedLinePudorys=line
            }
        }

        // 🟠 Úsečky
        state.snappedSegmentPudorys = null
        val allSegmentsNarys = if (state.showConstruction.value){state.segmentsPudorys + state.helpSegmentsPudorys}else {state.segmentsPudorys}
        allSegmentsNarys.forEach { segment ->
            val a = Offset(segment.start.x, segment.start.y)
            val b = Offset(segment.end.x, segment.end.y)

            val ab = b - a
            val abLenSq = ab.getDistanceSquared()
            if (abLenSq >= 1e-6f) {
                val ap = logicalCursor - a
                val t = (ap.x * ab.x + ap.y * ab.y) / abLenSq
                val clampedT = t.coerceIn(0f, 1f)
                val proj = a + ab * clampedT

                val screen = proj.toScreen(
                    state.scale,
                    state.canvasOffset,
                    state.canvasHeight,
                    state,
                    state.canvasWidth
                )
                val dist = (screen - screenCursor).getDistance()
                if (dist <= state.snapThreshold) {
                    snapCandidates += proj to dist
                    state.snappedSegmentPudorys = segment
                }
            }
        }
        // kružnice
        state.hoveredCirclePudorys = null  // reset na začátku
        val circles =  if (state.showConstruction.value){ state.circlesPudorys} else state.circlesPudorys.filterNot{it.isHelpCircle}

        for (circle in circles) {
            // kružnice z obecného tvaru
            val x0 = -circle.d / 2f
            val y0 = -circle.e / 2f
            val r2 = x0 * x0 + y0 * y0 - circle.f
            if (r2 <= 0f) continue

            val r = sqrt(r2)
            val center = Offset(x0, y0)

            // (1) rychlé vyřazení – klidně může zůstat, ale musí být ve stejné logické soustavě
            // pokud je tvůj snapBoxPudorys už "flipnutý", tohle klidně úplně smaž
            // if (!snapBoxPudorys.overlaps(...)) continue

            // (2) spočítáme screen pozici středu
            val centerScreen = center.toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            )

            // (3) poloměr ve screenu = r * scale
            val rScreen = r * state.scale

            // (4) vzdálenost kurzoru od středu ve screenu
            val distScreen = (centerScreen - screenCursor).getDistance()

            // (5) jak moc jsme mimo kružnici ve screenu
            val deltaScreen = kotlin.math.abs(distScreen - rScreen)
            val snapRadiusLogical = state.snapThreshold / state.scale
            val epsOnCircle = snapRadiusLogical * 0.35f

            val aidSnap = state.findNearestAidPointLogical(
                cursorLogical = logicalCursor,
                snapRadiusLogical = snapRadiusLogical
            )

            if (aidSnap != null) {
                val aidPt = Offset(aidSnap.x, aidSnap.y) // očekávám (x, -z) už správně

                if (isPointOnCircle(aidPt, center, r, epsOnCircle)) {
                    state.hoveredAidPointId = aidSnap.id
                    return aidPt
                }
            }

            if (deltaScreen <= state.snapThreshold) {
                if (state.drawobjects == Mongeobjects.NONE ||
                    state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION ||
                    state.drawobjects == Mongeobjects.RULED_SURFACE
                ) {
                    state.hoveredCirclePudorys = circle
                }

                val dirLogical = (logicalCursor - center).normalize()
                val onCircle = center + dirLogical * r

                // přidej kandidáta, NE return
                snapCandidates += onCircle to (deltaScreen + state.snapThreshold * 0.5f)
            }
        }
        //kružnicové kužsečky
        val conics = state.conicsPudorys // dosaď svůj skutečný seznam
            .asSequence()
            .toList()

        for (k in conics) {
            val cg = k.tryAsCircleGeom() ?: continue

            val center = cg.center
            val r = cg.r

            val centerScreen = center.toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            )

            val rScreen = r * state.scale
            val distScreen = (centerScreen - screenCursor).getDistance()
            val deltaScreen = kotlin.math.abs(distScreen - rScreen)

            if (deltaScreen <= state.snapThreshold) {
                if (state.drawobjects == Mongeobjects.RULED_SURFACE) {
                    state.snappedConicPudorys = k
                }


                val v = logicalCursor - center
                val len = v.getDistance()
                if (len > 1e-9f) {
                    val onCircle = center + v * (r / len)
                    snapCandidates += onCircle to (deltaScreen + state.snapThreshold * 0.5f)
                }
            }
        }



        // 🟣 Elipsy (přes vstupní body)


            val conicP = snapConicPudorys(state,logicalCursor)
            if (conicP != null)   snapCandidates.add(conicP.bestCandidatePoint to conicP.distanceToCursor)

            // Křivky (půdorys) – jen pro výběr v módu NONE (nebo výběr poledníku rotační
            // plochy), snap je nepřesný → nejnižší priorita
            state.snappedCurvePudorys = null
            if (state.drawobjects == Mongeobjects.NONE || state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION) {
                val pointsByIdPud = state.pointsPudorys.associateBy { it.id }
                val aidById = state.aidPointsLogical.associateBy { it.id }
                state.curvesPudorys.forEach { curve ->
                    val control = curve.polylineLocal ?: curve.points.mapNotNull { ref ->
                        when (ref) {
                            is CurvePudRef.P -> pointsByIdPud[ref.pointId]?.let { Offset(it.x, it.y) }
                            is CurvePudRef.A -> aidById[ref.aidId]?.let { Offset(it.x, it.y) }
                        }
                    }
                    if (control.size < 2) return@forEach
                    val sampled = if (curve.polylineLocal != null) control else sampleSmoothCurvePoints(control, curve.closed)
                    val near = nearestOnSampledCurve(logicalCursor, sampled) ?: return@forEach
                    val distScreen = near.second * state.scale
                    if (distScreen <= state.snapThreshold) {
                        snapCandidates += near.first to (distScreen + state.snapThreshold * 0.5f)
                        state.snappedCurvePudorys = curve
                    }
                }
            }
        }
    }




// 🔍 Vyber nejbližší prvek
    return snapCandidates.minByOrNull { it.second }?.first
}
