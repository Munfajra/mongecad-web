package monge.input.tools

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.Arc2DPudorys
import model.classes.HelpLineNarys
import model.classes.HelpLinePudorys
import model.classes.HelpSegmentNarys
import model.classes.HelpSegmentPudorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.PlaneTraceNarys
import model.classes.PlaneTracePudorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import monge.input.segments.removePlanePolygonsContainingSegments
import monge.input.segments.removePlanePolygonsContainingAidPoints
import state.MongeState
import state.snapMonge.isAngleOnArc
import utils.dotProduct
import utils.getLogicalCursor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

fun eraseObjectAtPudorys(
    state: MongeState,
    cursor: Offset,
    snappedPointLogical: Offset?
) {
    val logicalCursor = getLogicalCursor(
        snappedPointLogical,
        cursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    state.pointsPudorys.findLast { point ->
        val pointOffset = Offset(point.x, point.y)
        (pointOffset - logicalCursor).getDistance() < state.snapThreshold / state.scale
    }?.let {
        state.pointsPudorys.remove(it)

        val parent = it.parent
        if (parent != null) {
            state.sharedPoints3D.remove(parent)

            val other = state.pointsNarys.find { n -> n.parent === parent }
            if (other != null) {
                val index = state.pointsNarys.indexOf(other)
                if (index != -1) {
                    val cleanedName = parent.name  // ← správný aktuální název
                    state.pointsNarys[index] = other.copy(
                        name = cleanedName,
                        parent = null
                    )
                }
            }
        }
        commitSnapshot(state)

    }


// 2️⃣ Smazání přímky
    state.allLinesPudorys.findLast {
        val origin = Offset(it.point.x, it.point.y)
        val dir = it.direction
        val len = dir.getDistance()
        if (len < 1e-6f) return@findLast false
        val unit = Offset(dir.x / len, dir.y / len)
        val ap = logicalCursor - origin
        val projection = origin + unit * (ap.dotProduct(unit))
        val dist = (projection - logicalCursor).getDistance()
        dist < state.snapThreshold / state.scale
    }?.let { line ->
        when (line) {
            is Line3DProjectionPudorys -> {
                if (line.parent?.id == "X12_ID") return
                state.lines3DPudorys.remove(line)

                line.parent?.let { parent ->
                    state.lines3D.remove(parent)

                    val narys = state.lines3DNarys.find { it.parent === parent }
                    if (narys != null) {
                        val index = state.lines3DNarys.indexOf(narys)
                        if (index != -1) {
                            val cleanedName = parent.name
                            state.lines3DNarys[index] = narys.copy(
                                localName = "$cleanedName₂",
                                parent = null
                            )
                        }
                    }
                }
            }

            is HelpLinePudorys -> state.helpLinePudorys.remove(line)

            is PlaneTracePudorys -> {
                state.lineTracesPudorys.remove(line)

                line.parent?.let { parent ->
                    // Smaž samotnou rovinu
                    val removed = state.planes3D.removeAll { it.id == parent.id }
                    println("🧹 Odebrána rovina '${parent.name}': $removed")

                    // Najdi druhou stopu (nárys) a osamostatni ji
                    val orphan = state.lineTracesNarys.find { it.parent?.id == parent.id }
                    if (orphan != null) {
                        val index = state.lineTracesNarys.indexOf(orphan)
                        if (index != -1) {
                            val cleanName = parent.name
                            state.lineTracesNarys[index] = orphan.copy(
                                parent = null,
                                localName = "${cleanName}₂"
                            )
                            println("🧷 Přeživší stopa osamostatněna jako '${cleanName}₂'")
                        }
                    }
                }
            }

        }
        commitSnapshot(state)

        return
    }


    // 3️⃣ Smazání úsečky
    state.allSegmentsPudorys.findLast {
        val a = Offset(it.start.x, it.start.y)
        val b = Offset(it.end.x, it.end.y)
        val ab = b - a
        val abLen2 = ab.getDistanceSquared()
        if (abLen2 < 1e-6f) return@findLast false
        val ap = logicalCursor - a
        val t = ap.dotProduct(ab) / abLen2
        if (t !in 0f..1f) return@findLast false
        val projection = a + ab * t
        (projection - logicalCursor).getDistance() < state.snapThreshold / state.scale
    }?.let { seg ->
        when (seg) {
            is Segment2DPudorys -> {
                // --- 1) koncové body této úsečky v PŮDORYSU ---
                val endPtsP = state.pointsPudorys.filter {
                    it.isSegmentEndpoint && it.parentSegment?.id == seg.id
                }
                // 3D parenti těchto koncových bodů
                val endpointParents3D = endPtsP.mapNotNull { it.parent }.toSet()

                // --- 2) smazat úsečku + její výběr ---
                state.segmentsPudorys.remove(seg)
                state.selectedSegmentsPudorys.remove(seg)

                // --- 3) smazat koncové body v PŮDORYSU (+ výběry / offsety) ---
                endPtsP.forEach { pt ->
                    state.selectedPointsPudorys.remove(pt)
                    state.labelOffsetsPointsPudorys.remove(pt.id)
                }
                state.pointsPudorys.removeAll(endPtsP.toSet())

                // --- 4) pokud měla úsečka parenta: zruš 3D úsečku a odpoj druhý průmět v NÁRYSU ---
                seg.parent?.let { parent ->

                    state.segments3D.removeAll { it.id == parent.id }

                    state.segmentsNarys.find { it.parent === parent }?.let { other ->
                        val cleanName = parent.name  // ponecháváš tvůj původní vzor se "₂"
                        val idx = state.segmentsNarys.indexOf(other)
                        if (idx != -1) {
                            state.segmentsNarys[idx] = other.copy(
                                name = "$cleanName₂",
                                parent = null
                            )
                        }
                    }
                }

                // --- 5) SMAZAT 3D parenty koncových bodů + jejich obě projekce ---
                endpointParents3D.forEach { p3 ->
                    val projP = state.pointsPudorys.filter { it.parent === p3 }
                    val projN = state.pointsNarys  .filter { it.parent === p3 }

                    // úklid výběrů a offsetů
                    projP.forEach { pt ->
                        state.selectedPointsPudorys.remove(pt)
                        state.labelOffsetsPointsPudorys.remove(pt.id)
                    }
                    projN.forEach { pt ->
                        state.selectedPointsNarys.remove(pt)
                        state.labelOffsetsPointsNarys.remove(pt.id)

                    }

                    // smazat projekce bodu
                    state.pointsPudorys.removeAll(projP.toSet())


                    // smazat 3D bod
                    state.sharedPoints3D.removeAll { it.id == p3.id }

                    if (state.rename.pointBeingRenamed === p3) state.rename.pointBeingRenamed = null

                }
                commitSnapshot(state)

            }


            is HelpSegmentPudorys -> {
                removePlanePolygonsContainingSegments(state, setOf(seg.id))
                state.helpSegmentsPudorys.remove(seg)
                state.pointsPudorys.removeAll { it.isSegmentEndpoint && it.parentSegment == seg }
                commitSnapshot(state)

            }
        }

        return
    }


    // 4️⃣ Smazání oblouku
    state.arcsPudorys.findLast { arc ->
        val center = arc.center
        val dx = logicalCursor.x - center.x
        val dy = logicalCursor.y - center.y

        val distance = hypot(dx, dy)
        val radius = arc.radius

        // blízko kružnice
        if (abs(distance - radius) > state.snapThreshold / state.scale) return@findLast false

        // ✅ úhel kurzoru v "geometrii" (y nahoru)
        val a = Arc2DPudorys.norm(atan2(-dy, dx))
        val s = Arc2DPudorys.norm(arc.startRad)
        val sweep = arc.sweepSigned() // signed: CCW +, CW -

        val len = kotlin.math.abs(sweep)

        val d = if (sweep >= 0f) {
            var x = a - s
            if (x < 0f) x += 2f * kotlin.math.PI.toFloat()
            x
        } else {
            var x = s - a
            if (x < 0f) x += 2f * kotlin.math.PI.toFloat()
            x
        }

        d <= len
    }?.let {
        state.arcsPudorys.remove(it)
        commitSnapshot(state)
        return
    }
    // 5️⃣ Smazání kružnice
    state.circlesPudorys.findLast { circle ->
        val x0 = -circle.d / 2f
        val y0 = -circle.e / 2f
        val r2 = x0 * x0 + y0 * y0 - circle.f
        if (r2 <= 0f) return@findLast false

        val r = sqrt(r2)
        val center = Offset(x0, y0)
        val dist = (logicalCursor - center).getDistance()

        abs(dist - r) <= state.snapThreshold / state.scale
    }?.let { circle ->
        state.circlesPudorys.remove(circle)
        commitSnapshot(state)
        return
    }
    // Smazání pomocných bodů
    state.aidPointsLogical.findLast { p ->
        (Offset(p.x, p.y) - logicalCursor).getDistance() <
                state.snapThreshold / state.scale
    }?.let { toRemove ->
        removePlanePolygonsContainingAidPoints(state, setOf(toRemove.id))
        state.aidPointsLogical.remove(toRemove)
        state.selectedAidPointIds.remove(toRemove.id)
        if (state.hoveredAidPointId == toRemove.id) state.hoveredAidPointId = null
        println("🗑️  Pomocný bod smazán: ${toRemove.x}, ${toRemove.y}")
        commitSnapshot(state)
        state.triggerRedraw++
        return
    }

// 6️⃣ Smazání libovolné kuželosečky podle rovnice
    state.snappedConicPudorys?.let { conic ->
        val parent = conic.parent
        if (parent != null) {
            println("🔎 parent id = '${parent.id}'")

            // 1️⃣ Smaž parenta podle ID
            val index = state.conics3D.indexOfFirst { it.id == parent.id }
            if (index != -1) {
                state.conics3D.removeAt(index)
                println("🧹 Odebrán prvek na indexu $index (id=${parent.id})")
            } else {
                println("❌ Nepodařilo se najít kuželosečku podle ID")
            }

            val removed = state.conics3D.removeAll { it.id == parent.id }
            println("🧹 Odebráno 3D kuželoseček: $removed")

            // 2️⃣ Smaž aktuální projekci
            state.conicsPudorys.remove(conic)
            state.conicInputPointsPudorys.remove(conic.id)
            state.hyperbolaInputsPudorys.remove(conic.id)

            // 3️⃣ Najdi přeživší druhou projekci (nárys) a osamostatni ji
            state.conicsNarys.find { it.parent?.id == parent.id }?.let { orphan ->
                val index = state.conicsNarys.indexOf(orphan)
                state.conicsNarys[index] = orphan.copy(parent = null)
            }
            commitSnapshot(state)
            println("🗑️ Smazána projekce + parent. Druhá projekce se osamostatnila.")
        }


    }
}
fun eraseObjectAtNarys(
    state: MongeState,
    cursor: Offset,
    snappedPointLogical: Offset?
) {
    val logicalCursor = getLogicalCursor(
        snappedPointLogical,
        cursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    state.aidPointsLogical.findLast { p ->
        (Offset(p.x, p.y) - logicalCursor).getDistance() <
                state.snapThreshold / state.scale
    }?.let { toRemove ->
        removePlanePolygonsContainingAidPoints(state, setOf(toRemove.id))
        state.aidPointsLogical.remove(toRemove)
        state.selectedAidPointIds.remove(toRemove.id)
        if (state.hoveredAidPointId == toRemove.id) state.hoveredAidPointId = null
        println("🗑️  Pomocný bod smazán: ${toRemove.x}, ${toRemove.y}")
        state.triggerRedraw++
        commitSnapshot(state)
        return
    }
    // 1️⃣ Smazání bodu
    state.pointsNarys.findLast { point ->
        val pointOffset = Offset(point.x, -point.z)
        (pointOffset - logicalCursor).getDistance() < state.snapThreshold / state.scale
    }?.let { point ->
        state.pointsNarys.remove(point)

        point.parent?.let { parent ->
            state.sharedPoints3D.remove(parent)

            // Najdi půdorysový průmět sdružený se stejným parentem
            val pudorys = state.pointsPudorys.find { it.parent === parent }
            if (pudorys != null) {
                val index = state.pointsPudorys.indexOf(pudorys)
                if (index != -1) {
                    val cleanedName = parent.name  // ← správný aktuální název
                    state.pointsPudorys[index] = pudorys.copy(
                        name = cleanedName,
                        parent = null)
                }
            }
        }
        commitSnapshot(state)
        return
    }


    // 2️⃣ Smazání přímky
    state.allLinesNarys.findLast {
        val origin = Offset(it.point.x, -it.point.z)
        val dir = Offset(it.direction.x, -it.direction.y)
        val len = dir.getDistance()
        if (len < 1e-6f) return@findLast false
        val unit = Offset(dir.x / len, dir.y / len)
        val ap = logicalCursor - origin
        val projection = origin + unit * (ap.dotProduct(unit))
        val dist = (projection - logicalCursor).getDistance()
        dist < state.snapThreshold / state.scale
    }?.let { line ->
        when (line) {
            is Line3DProjectionNarys -> {
                if (line.parent?.id == "X12_ID") return
                state.lines3DNarys.remove(line)

                line.parent?.let { parent ->
                    state.lines3D.remove(parent)

                    val pudorys = state.lines3DPudorys.find { it.parent === parent }
                    if (pudorys != null) {
                        val index = state.lines3DPudorys.indexOf(pudorys)
                        if (index != -1) {
                            val cleanName = parent.name  // ✅ nově: jméno z parentu
                            state.lines3DPudorys[index] = pudorys.copy(
                                localName = "$cleanName₁",
                                parent = null
                            )
                        }
                    }
                }
            }

            is HelpLineNarys -> state.helpLineNarys.remove(line)

            is PlaneTraceNarys -> {
                state.lineTracesNarys.remove(line)

                line.parent?.let { parent ->
                    // Smaž samotnou rovinu
                    val removed = state.planes3D.removeAll { it.id == parent.id }
                    println("🧹 Odebrána rovina '${parent.name}': $removed")

                    // Najdi druhou projekci a osamostatni ji
                    val orphan = state.lineTracesPudorys.find { it.parent?.id == parent.id }
                    if (orphan != null) {
                        val index = state.lineTracesPudorys.indexOf(orphan)
                        if (index != -1) {
                            val cleanName = parent.name
                            state.lineTracesPudorys[index] = orphan.copy(
                                parent = null,
                                localName = "${cleanName}₁"
                            )
                            println("🧷 Přeživší stopa osamostatněna jako '${cleanName}₁'")
                        }
                    }
                }
            }
        }
        commitSnapshot(state)
        return
    }



// 3️⃣ Smazání úsečky (NÁRYS)
    state.segmentsNarys.plus(state.helpSegmentsNarys).findLast {
        val a = Offset(it.start.x, -it.start.z)
        val b = Offset(it.end.x, -it.end.z)
        val ab = b - a
        val abLen2 = ab.getDistanceSquared()
        if (abLen2 < 1e-6f) return@findLast false
        val ap = logicalCursor - a
        val t = ap.dotProduct(ab) / abLen2
        if (t !in 0f..1f) return@findLast false
        val projection = a + ab * t
        (projection - logicalCursor).getDistance() < state.snapThreshold / state.scale
    }?.let { seg ->
        when (seg) {

            is Segment2DNarys -> {
                // --- 1) koncové body této úsečky v NÁRYSU ---
                val endPtsN = state.pointsNarys.filter {
                    it.isSegmentEndpoint && it.parentSegment?.id == seg.id
                }
                // 3D parenti těchto koncových bodů
                val endpointParents3D = endPtsN.mapNotNull { it.parent }.toSet()

                // --- 2) smazat úsečku a její NÁRYS koncové body (+ výběry / offsety) ---
                state.segmentsNarys.remove(seg)
                state.selectedSegmentsNarys.remove(seg)

                endPtsN.forEach { pt ->
                    state.selectedPointsNarys.remove(pt)
                    state.labelOffsetsPointsNarys.remove(pt.id)
                }
                state.pointsNarys.removeAll(endPtsN.toSet())

                // --- 3) smazat 3D parenta úsečky a „odpojit“ druhý průmět (PŮDORYS) ---
                seg.parent?.let { parent ->
                    // zruš 3D úsečku

                    state.segments3D.removeAll { it.id == parent.id }

                    // najdi druhý průmět a odpoj ho (ponecháme ho jako samostatný 2D segment)
                    state.segmentsPudorys.find { it.parent === parent }?.let { other ->
                        val cleanName = other.name?.removeSuffix("₁")?.removeSuffix("₂") ?: ""
                        val idx = state.segmentsPudorys.indexOf(other)
                        if (idx != -1) {
                            state.segmentsPudorys[idx] = other.copy(
                                name = cleanName,
                                parent = null
                            )
                        }
                    }
                }

                // --- 4) SMAZAT 3D parenty koncových bodů + jejich obě projekce ---
                endpointParents3D.forEach { p3 ->
                    // projekce toho 3D bodu
                    val projP = state.pointsPudorys.filter { it.parent === p3 }
                    val projN = state.pointsNarys  .filter { it.parent === p3 }

                    // úklid výběrů a offsetů
                    projP.forEach { pt ->
                        state.selectedPointsPudorys.remove(pt)
                        state.labelOffsetsPointsPudorys.remove(pt.id)
                    }
                    projN.forEach { pt ->
                        state.selectedPointsNarys.remove(pt)
                        state.labelOffsetsPointsNarys.remove(pt.id)
                    }

                    // smazat projekce bodu
                    state.pointsNarys  .removeAll(projN.toSet())

                    // smazat 3D parent bod
                    state.sharedPoints3D.removeAll { it.id == p3.id }

                    if (state.rename.pointBeingRenamed === p3) state.rename.pointBeingRenamed = null
                }
            }

            is HelpSegmentNarys -> {
                // pomocná úsečka: smaž ji a její koncové body v NÁRYSU
                state.helpSegmentsNarys.remove(seg)
                val endPtsN = state.pointsNarys.filter { it.isSegmentEndpoint && it.parentSegment == seg }
                endPtsN.forEach { pt ->
                    state.selectedPointsNarys.remove(pt)
                    state.labelOffsetsPointsNarys.remove(pt.id)
                }
                state.pointsNarys.removeAll(endPtsN.toSet())

                // pokud by náhodou koncové body měly 3D parenty (většinou ne), smaž je taky
                endPtsN.mapNotNull { it.parent }.toSet().forEach { p3 ->
                    val projP = state.pointsPudorys.filter { it.parent === p3 }
                    val projN = state.pointsNarys  .filter { it.parent === p3 }
                    projP.forEach { state.labelOffsetsPointsPudorys.remove(it.id); state.selectedPointsPudorys.remove(it) }
                    projN.forEach { state.labelOffsetsPointsNarys.remove(it.id); state.selectedPointsNarys.remove(it) }
                    state.pointsPudorys.removeAll(projP.toSet())
                    state.pointsNarys  .removeAll(projN.toSet())
                    state.sharedPoints3D.removeAll { it.id == p3.id }
                }
            }
        }

        state.triggerRedraw++
        commitSnapshot(state)
        return
    }


// 4️⃣ Smazání oblouku (nově: startRad/endRad)
    val tol = state.snapThreshold / state.scale

    state.arcsNarys.findLast { arc ->
        val cx = arc.center.x
        val cz = arc.center.z

        // cursor v XZ
        val px = logicalCursor.x
        val pz = -logicalCursor.y

        val dx = px - cx
        val dz = pz - cz

        val dist = hypot(dx, dz)
        if (kotlin.math.abs(dist - arc.radius) > tol) return@findLast false

        val a = atan2(dz, dx)          // ✅ geometrický úhel v XZ
        isAngleOnArc(arc, a)           // ✅ sdílená funkce z nové logiky
    }?.let { hit ->
        state.arcsNarys.remove(hit)
        commitSnapshot(state)
        return
    }
    // 5️⃣ Smazání kružnice
    state.circlesNarys.findLast { circle ->
        val x0 = -circle.d / 2f
        val z0 = -circle.e / 2f
        val r2 = x0 * x0 + z0 * z0 - circle.f
        if (r2 <= 0f) return@findLast false

        val r = sqrt(r2)
        val center = Offset(x0, -z0) // Z → -Y
        val dist = (logicalCursor - center).getDistance()

        abs(dist - r) <= state.snapThreshold / state.scale
    }?.let { circle ->
        state.circlesNarys.remove(circle)
        commitSnapshot(state)
        return
    }
    // 6️⃣ Smazání libovolné kuželosečky podle rovnice
    state.snappedConicNarys?.let { conic ->
        val parent = conic.parent

        if (parent != null) {
            println("🔎 parent id = '${parent.id}'")
            val removed = state.conics3D.removeAll { it.id == parent.id }
            println("🧹 Odebráno 3D kuželoseček: $removed")

            // Smaž nárysovou projekci
            state.conicsNarys.remove(conic)
            state.conicInputPointsNarys.remove(conic.id)
            state.hyperbolaInputsNarys.remove(conic.id)

            // Najdi přeživší půdorysovou projekci a nastav jí parent = null
            state.conicsPudorys.find { it.parent?.id == parent.id }?.let { orphan ->
                val index = state.conicsPudorys.indexOf(orphan)
                state.conicsPudorys[index] = orphan.copy(parent = null)
            }

            println("🗑️ Smazána projekce + parent. Druhá projekce se osamostatnila.")
        } else {
            // Samostatná 2D kuželosečka (nárys)
            state.conicsNarys.remove(conic)
            state.conicInputPointsNarys.remove(conic.id)
            state.hyperbolaInputsNarys.remove(conic.id)
            println("🗑️ Smazána samostatná kuželosečka (nárys): ${conic.name}")
        }

        state.snappedConicNarys = null
        commitSnapshot(state)
        return
    }


}
