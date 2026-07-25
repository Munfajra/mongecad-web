package monge.input.selection

import utils.System
import androidx.compose.ui.geometry.Offset
import draw.mongescreen.labels.clearSelection
import model.*
import model.axo.AxoMode
import model.classes.*
import monge.input.combineprojections.handleCombineProjections
import monge.input.planeobjects.planelift.decideObjectForLift
import monge.input.planeobjects.planelift.finalizePendingConicLiftWithPlane
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.getLogicalCursor
import utils.toScreen
import kotlin.math.abs

private fun markPlaneForInPlaneConstruction(state: MongeState, plane: Plane3D): Boolean {
    if (state.drawobjects !in listOf(
            Mongeobjects.CIRCLE,
            Mongeobjects.PARABOLA,
            Mongeobjects.ELLIPSE,
            Mongeobjects.HYPERBOLA,
            Mongeobjects.REGULAR_POLYGON_IN_PLANE
        )
    ) return false
    if (state.projekcnityp !in listOf(ProjectionType.ASSOCIATED, ProjectionType.SINGLE)) return false

    state.selectedPlanes.clear()
    state.selectedPlaneForCircle = plane
    if (state.projectionMode == ProjectionMode.AXO &&
        state.drawobjects == Mongeobjects.REGULAR_POLYGON_IN_PLANE
    ) {
        setProjectionPhase("rp_center_axo", state)
    }
    state.triggerRedraw++
    return true
}

//označení přímek půdorys
fun toggleSelectionPudorysLine(
    line: NamedLinePudorys,
    state: MongeState
) {
    val tolerance = 0.01f
    val existing = state.selectedLinesPudorys.find {
        abs(it.point.x - line.point.x) < tolerance &&
                abs(it.point.y - line.point.y) < tolerance &&
                abs(it.direction.x - line.direction.x) < tolerance &&
                abs(it.direction.y - line.direction.y) < tolerance
    }
    val pending = state.completionPending
    if (false) return
    if (pending is CompletionRequest.Line3DFromProjections &&
        pending.expect == DrawingModeMonge.PUDORYS &&
        line is Line3DProjectionPudorys
    ) {

        handleCombineProjections(state, line)
        return
    }

    if (existing != null) {
        state.selectedLinesPudorys.remove(existing)
        println("Odznačena přímka v PŮDORYSU: ${line.name}, (x=${line.point.x}, y=${line.point.y})")
    } else {
        state.selectedLinesPudorys.add(line)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("Označena přímka v PŮDORYSU: ${line.name}, (x=${line.point.x}, y=${line.point.y})")
    }
}
fun handleSelectionPudorysLine(
    state: MongeState,
) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    if (state.projectionPhase == "trans_parallel_temp_point_pudorys") {
        return
    }
    val snapped = getLogicalCursor(
        null,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val snappedX = snapped.x
    val snappedY = snapped.y
    val isNearPoint = state.pointsPudorys.any {
        abs(it.x - snappedX) < 0.01f && abs(it.y - snappedY) < 0.01f
    }

    if (isNearPoint) return
    val isNearSegment = (state.segmentsPudorys + state.helpSegmentsPudorys).any { seg ->
        val a = Offset(seg.start.x, seg.start.y)
        val b = Offset(seg.end.x, seg.end.y)
        val projected = projectPointOntoSegment(
            getLogicalCursor(
                null,
                state.cursorPosition,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            ), a, b)
        val projectedScreen = projected.toScreen(
            state.scale,
            state.canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        val distance = (projectedScreen - state.cursorPosition).getDistance()
        distance <= state.snapThreshold*0.6f/state.scale
    }
    if (isNearSegment) return

    /* ───────────────────── hlavní podmínka aktivního režimu ────────────────── */
    val allowed = state.drawobjects == Mongeobjects.RULED_SURFACE || state.drawobjects == Mongeobjects.INTERSECTION || state.drawobjects == Mongeobjects.NONE || state.drawobjects== Mongeobjects.PLANE_LIFT || state.drawobjects == Mongeobjects.HYPERBOLA || (state.drawobjects== Mongeobjects.CYLINDER && state.cylinderPhase == CylinderPhase.PICK_DIRECTION)||
    state.drawobjects == Mongeobjects.TRANSPARALLEL ||
            state.drawobjects == Mongeobjects.TRANSORTH ||
            (state.mongeMode == DrawingModeMonge.PUDORYS &&
                    (state.drawobjects == Mongeobjects.SEGMENTS || state.drawobjects == Mongeobjects.LINES || state.drawobjects == Mongeobjects.PLANE) &&
                    (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL))

    val allowedConicLift = state.drawobjects == Mongeobjects.RULED_SURFACE || state.drawobjects == Mongeobjects.INTERSECTION || state.drawobjects == Mongeobjects.PLANE_LIFT || ((state.drawobjects in listOf(
        Mongeobjects.NONE,
        Mongeobjects.CIRCLE,
        Mongeobjects.PARABOLA,
        Mongeobjects.ELLIPSE,
        Mongeobjects.HYPERBOLA,
        Mongeobjects.CYLINDER,
        Mongeobjects.REGULAR_POLYGON_IN_PLANE
    ) && state.projekcnityp in listOf(ProjectionType.ASSOCIATED, ProjectionType.SINGLE)
            ) ||
            state.projectionPhase in listOf(
        "lift_conic_select_plane",
        "lift_conic_select_plane_narys",
        "lift_parabola_select_plane_pudorys",
        "lift_parabola_select_plane_narys",
        "lift_hyperbola_select_plane_pudorys",
        "lift_hyperbola_select_plane_narys"
    )) || (state.projectionMode == ProjectionMode.KOTO && state.drawobjects == Mongeobjects.POINTS && state.projekcnityp in listOf(ProjectionType.ASSOCIATED, ProjectionType.SINGLE))

    if ((state.drawobjects== Mongeobjects.TRANSPARALLEL || state.drawobjects== Mongeobjects.TRANSORTH) && (state.selectedLineForParallelPudorys != null
                || state.selectedLineForParallelNarys != null ||
                state.selectedSegmentForParallelPudorys != null || state.selectedSegmentForParallelNarys != null)){
        return
    }

    val nearest = state.snappedLinePudorys

         if (nearest != null) {
        val line = nearest

        // 🔁 nová větev: pokud jsme v režimu výběru rovin
        if (allowedConicLift) {
            val planeId = when (val t = line) {
                is PlaneTracePudorys -> t.parentId ?: t.parent?.id
                else -> null
            }

            val plane = planeId?.let { id -> state.planes3D.find { it.id == id } }
            if (plane != null) {
                if (state.isShiftPressed) {
                    toggleSelectionPlane(plane, state) // uvnitř porovnávej přes id!
                    return
                }

                clearSelection(state)
                state.selectedPlanes.clear()
                state.selectedPlanes.add(plane)
                markPlaneForInPlaneConstruction(state, plane)
                if (state.drawobjects == Mongeobjects.PLANE_LIFT) {
                    val eq = plane.equation
                    if (eq == null) {
                        println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                        return
                    }

                    if (isPlaneDegenerateForView(eq, state.mongeMode)) {
                        println("⚠️ Rovina ${plane.name} je v aktuálním pohledu kolmá (degenerace) – výběr zamítnut.")
                        return
                    }
                    setProjectionPhase("ready_planelift", state)
                    state.selectedPlaneForCircle = plane
                    state.triggerRedraw++
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    return
                }

                if (finalizePendingConicLiftWithPlane(state, plane)) return

                if (state.drawobjects == Mongeobjects.RULED_SURFACE) {

                }

                println("Vybrána rovina v PŮDORYSU: ${plane.name}")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                return
            }
        }
             if (allowed) {
        val pending = state.completionPending
        if (pending is CompletionRequest.Line3DFromProjections &&
            pending.expect == DrawingModeMonge.PUDORYS &&
            line is Line3DProjectionPudorys
        ) {

            handleCombineProjections(state, line)
            return
        }
        if (false) return
        // ⚙️ výchozí chování: výběr přímky

        clearSelection(state)
        /* 2) přidej novou přímku do obou kolekcí */
        state.selectedLinesPudorys.add(line)       // instance (starý systém)
        state.selectedLineIdsPudorys.add(line.id)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("Vybrána přímka v PŮDORYSU (bez shiftu): ${line.name}")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        return
    } else {
        val isInParallelConstruction = (
                state.projectionPhase == "pudorys_start" &&
                        state.constructionModifier == ConstructionModifier.PARALLEL
                )
        if (!isInParallelConstruction) {
            state.selectedLinesPudorys.clear()
            state.selectedLinesNarys.clear()
            state.selectedPlanes.clear()
        }
    }
}
            // 🟣 Pokus o výběr kuželosečky podle kurzoru
            val selectedConic = state.snappedConicPudorys ?: return

    val parentId = selectedConic.parentId ?: selectedConic.parent?.id

    if (parentId != null) {
        state.spheres3D.find { it.id == parentId }?.let { sphere ->
            if (!state.isShiftPressed) clearSelection(state)
            toggleSelectionSphere3D(sphere, state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        }
        val parent3D = state.conics3D.find { it.id == parentId }
        if (parent3D != null) {
            if (state.isShiftPressed) toggleSelectionConic3D(parent3D, state)
            else {
                clearSelection(state)
                toggleSelectionConic3D(parent3D, state)
            }
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        } else {
            // fallback: 3D chybí? vyber aspoň obě 2D projekce se stejným parentId
            val pSibs = state.conicsPudorys.filter { it.parentId == parentId || it.parent?.id == parentId }
            val nSibs = state.conicsNarys  .filter { it.parentId == parentId || it.parent?.id == parentId }
            if (!state.isShiftPressed) clearSelection(state)
            state.selectedConicsPudorys.addAll(pSibs)
            state.selectedConicsNarys.addAll(nSibs)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        }
    } else {
        // opravdu orphan 2D → původní chování
        if (state.isShiftPressed) {
            toggleSelectionPudorysConic(selectedConic, state)
        } else {
            clearSelection(state)
            toggleSelectionPudorysConic(selectedConic, state)
        }
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        return
    }
}
//označení přímky nárys
fun toggleSelectionNarysLine(
    line: NamedLineNarys,
    state: MongeState
) {    // 🔁 1. Režim doplnění

    val tolerance = 0.01f
    val existing = state.selectedLinesNarys.find {
        abs(it.point.x - line.point.x) < tolerance &&
                abs(it.point.z - line.point.z) < tolerance &&
                abs(it.direction.x - line.direction.x) < tolerance &&
                abs(it.direction.y - line.direction.y) < tolerance
    }

    if (existing != null) {
        state.selectedLinesNarys.remove(existing)
        println("Odznačena přímka v NÁRYSU: ${line.name}, (x=${line.point.x}, z=${line.point.z})")
    } else {
        val pending = state.completionPending
        if (false) return
        if (pending is CompletionRequest.Line3DFromProjections &&
            pending.expect == DrawingModeMonge.NARYS &&
            line is Line3DProjectionNarys
        ) {
            handleCombineProjections(state, line)
            return
        }
        state.selectedLinesNarys.add(line)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("Označena přímka v NÁRYSU: ${line.name}, (x=${line.point.x}, z=${line.point.z})")
    }
}


fun handleSelectionNarysLine(
    state: MongeState,
) {
    if (state.drawobjects == Mongeobjects.CURVE) return
    if (state.projectionMode == ProjectionMode.PLANE) return
    /* ───────────────────── ochranné bloky ───────────────────── */
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítemfgsdfgs")
        return
    }
    if (state.projectionPhase == "trans_parallel_temp_point_narys") return

    /* ─────────────────────– ignoruj kliky na body/úsečky ────────────────────── */
    val snapped = state.snappedPointNarys
        if (snapped != null&& state.projectionMode != ProjectionMode.AXO) {
            val l = getLogicalCursor(
                null,
                state.cursorPosition,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )
            Offset(l.x, -l.y)          // převod na logické (x, z)

    val isNearPoint = state.pointsNarys.any {
        abs(it.x - snapped.x) < 0.01f && abs(it.z - snapped.z) < 0.01f
    }
    if (isNearPoint) return
        }

    val snappedSegment = state.snappedSegmentNarys
    if (snappedSegment != null&& state.projectionMode != ProjectionMode.AXO) {
    val isNearSegment = (state.segmentsNarys + state.helpSegmentsNarys).any { seg ->
        val a = Offset(seg.start.x, -seg.start.z)
        val b = Offset(seg.end.x,   -seg.end.z)
        val logicalCursor = getLogicalCursor(
            null,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
        val worldCursor   = Offset(logicalCursor.x, logicalCursor.y)
        val projected     = projectPointOntoSegment(worldCursor, a, b)
        val projectedScr = projected.toScreen(
            state.scale,
            state.canvasOffset,
            state.canvasHeight,
            state,
            state.canvasWidth
        )
        (projectedScr - state.cursorPosition).getDistance() <= state.snapThreshold
    }

    if (isNearSegment) return
    }

    /* ───────────────────── hlavní podmínka aktivního režimu ────────────────── */
    val allowed = state.drawobjects == Mongeobjects.RULED_SURFACE || state.drawobjects == Mongeobjects.INTERSECTION || state.drawobjects == Mongeobjects.NONE || (state.drawobjects== Mongeobjects.PLANE_LIFT)||state.drawobjects == Mongeobjects.HYPERBOLA ||  (state.drawobjects== Mongeobjects.CYLINDER && state.cylinderPhase == CylinderPhase.PICK_DIRECTION)||
            state.drawobjects == Mongeobjects.TRANSPARALLEL ||
            state.drawobjects == Mongeobjects.TRANSORTH ||
            (state.mongeMode == DrawingModeMonge.NARYS || state.axoMode == AxoMode.AXO_NARYS)&&
                    (state.drawobjects == Mongeobjects.SEGMENTS || state.drawobjects == Mongeobjects.LINES || state.drawobjects == Mongeobjects.PLANE) &&
                    (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL)


    val allowedConicLift = state.drawobjects == Mongeobjects.RULED_SURFACE || state.drawobjects == Mongeobjects.INTERSECTION || state.drawobjects == Mongeobjects.PLANE_LIFT || (state.drawobjects in listOf(
        Mongeobjects.NONE,
        Mongeobjects.CIRCLE,
        Mongeobjects.PARABOLA,
        Mongeobjects.ELLIPSE,
        Mongeobjects.HYPERBOLA,
        Mongeobjects.CYLINDER,
        Mongeobjects.REGULAR_POLYGON_IN_PLANE
    ) && state.projekcnityp in listOf(ProjectionType.ASSOCIATED, ProjectionType.SINGLE)
            ) ||
            state.projectionPhase in listOf(
        "lift_conic_select_plane",
        "lift_conic_select_plane_narys",
        "lift_parabola_select_plane_pudorys",
        "lift_parabola_select_plane_narys",
        "lift_hyperbola_select_plane_pudorys",
        "lift_hyperbola_select_plane_narys"
    )


    /* ───────────────────── hledání nejbližší přímky ────────────────────────── */

    val nearest = state.snappedLineNarys
    val narysConic = state.snappedConicNarys
    if (narysConic != null) {
        val pid = narysConic.parentId ?: narysConic.parent?.id
        if (!state.isShiftPressed) clearSelection(state)

        if (pid != null) {
            // toggluj RODINU (P+N) podle parentId
            toggleSelectionConic2DFamilyByParentId(pid, state)
        } else {
            // orphan 2D → jen tahle jedna
            toggleSelectionNarysConic(narysConic, state)
        }
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        return
    }
    /* ───────────────────── reakce na výběr / označení ───────────────────────── */
    if (nearest != null) {
        val line = nearest

        /* ▶ výběr roviny, pokud nejsme v paralelním/ortogonálním režimu */
        if (allowedConicLift) {
            val planeId = when (val t = line) {
                is PlaneTraceNarys -> t.parentId ?: t.parent?.id
                else -> null
            }

            val plane = planeId?.let { id -> state.planes3D.find { it.id == id } }

            if (plane != null) {
                if (state.isShiftPressed) {
                    toggleSelectionPlane(plane, state) // uvnitř porovnávej přes id!
                    return
                }

                clearSelection(state)
                state.selectedPlanes.clear()
                state.selectedPlanes.add(plane)
                markPlaneForInPlaneConstruction(state, plane)

                if (state.drawobjects == Mongeobjects.PLANE_LIFT) {
                    val eq = plane.equation
                    if (eq == null) {
                        println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                        return
                    }

                    if (isPlaneDegenerateForView(eq, state.mongeMode)) {
                        println("⚠️ Rovina ${plane.name} je v aktuálním pohledu kolmá (degenerace) – výběr zamítnut.")
                        return
                    }
                    setProjectionPhase("ready_planelift", state)
                    state.selectedPlaneForCircle = plane
                    state.triggerRedraw++
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    return
                }

                if (finalizePendingConicLiftWithPlane(state, plane)) return

                if (state.drawobjects == Mongeobjects.RULED_SURFACE) {

                }

                println("Vybrána rovina v NÁRYSU: ${plane.name}")
                state.deferSelectionUntil = System.currentTimeMillis() + 100

                return
            }
        }


        /* ▶ čeká se na spárování přímek do 3D */
        val pending = state.completionPending
        if (pending is CompletionRequest.Line3DFromProjections &&
            pending.expect == DrawingModeMonge.NARYS &&
            line is Line3DProjectionNarys
        ) {
            handleCombineProjections(state, line)
            return
        }
        if (false) return
        if ((state.drawobjects== Mongeobjects.TRANSPARALLEL || state.drawobjects== Mongeobjects.TRANSORTH) && (state.selectedLineForParallelPudorys != null
                    || state.selectedLineForParallelNarys != null ||
                    state.selectedSegmentForParallelPudorys != null || state.selectedSegmentForParallelNarys != null)){
            return
        }

        /* ▶ běžný výběr přímky */
        if (allowed) {
        if (state.isShiftPressed) {
            toggleSelectionNarysLine(line, state)
            println("Vybrána přímka v NÁRYSU (se Shiftem): ${line.name}")
            return
        }

        clearSelection(state)
        state.selectedLinesNarys.add(line)
        state.selectedLineIdsNarys.add(line.id)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("Vybrána přímka v NÁRYSU (bez Shiftu): ${line.name}")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    } else {
        /* žádná čára poblíž kurzoru → případné zrušení výběru */
        val isParallelConstr =
            state.projectionPhase == "narys_start" &&
                    state.constructionModifier == ConstructionModifier.PARALLEL

        if (!isParallelConstr) {
            state.selectedLinesNarys.clear()
            state.selectedLinesPudorys.clear()
            state.selectedPlanes.clear()
        } else {
            println("🧊 Výběr ponechán během konstrukce rovnoběžné roviny.")
        }

    }
// 🟣 Pokus o výběr kuželosečky podle kurzoru v NÁRYSU

}

}

fun toggleSelectionLine3D(l: Line3D, state: MongeState){
    if (state.selectedLines3D.none { it.id == l.id }) state.selectedLines3D.add(l)

    state.lines3DPudorys.firstOrNull { it.parent?.id == l.id || it.parentId == l.id }?.let {
        if (state.selectedLinesPudorys.none { selected -> selected.id == it.id }) state.selectedLinesPudorys.add(it)
    }
    state.lines3DNarys.firstOrNull { it.parent?.id == l.id || it.parentId == l.id }?.let {
        if (state.selectedLinesNarys.none { selected -> selected.id == it.id }) state.selectedLinesNarys.add(it)
    }
    state.lines3DBokorys.firstOrNull { it.parent?.id == l.id || it.parentId == l.id }?.let {
        if (state.selectedLinesBokorys.none { selected -> selected.id == it.id }) state.selectedLinesBokorys.add(it)
    }
    state.lines3DAxo.firstOrNull { it.parent?.id == l.id || it.parentId == l.id }?.let {
        if (state.selectedLinesAxo.none { selected -> selected.id == it.id }) state.selectedLinesAxo.add(it)
    }
    state.pointsPudorys.firstOrNull { false }?.let {
        if (state.selectedPointsPudorys.none { selected -> selected.id == it.id }) state.selectedPointsPudorys.add(it)
    }
    state.pointsNarys.firstOrNull { false }?.let {
        if (state.selectedPointsNarys.none { selected -> selected.id == it.id }) state.selectedPointsNarys.add(it)
    }
    state.pointsBokorys.firstOrNull { false }?.let {
        if (state.selectedPointsBokorys.none { selected -> selected.id == it.id }) state.selectedPointsBokorys.add(it)
    }
    state.pointsAxo.firstOrNull { false }?.let {
        if (state.selectedPointsAxo.none { selected -> selected.id == it.id }) state.selectedPointsAxo.add(it)
    }

}
fun handleSelectionBokorysLine(
    state: MongeState,
) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    if (state.projectionPhase == "trans_parallel_temp_point_bokorys") {
        return
    }
    val snapped = state.snappedPointBokorys
    if (snapped != null) {
        val snappedX = snapped.y
        val snappedY = snapped.y
        val isNearPoint = state.pointsPudorys.any {
            abs(it.x - snappedX) < 0.01f && abs(it.y - snappedY) < 0.01f
        }

        if (isNearPoint) return
    }

    val isNearSegment = state.snappedSegmentBokorys!=null
    if (isNearSegment) return

    /* ───────────────────── hlavní podmínka aktivního režimu ────────────────── */
    val allowed = state.drawobjects == Mongeobjects.RULED_SURFACE || state.drawobjects == Mongeobjects.INTERSECTION || state.drawobjects == Mongeobjects.NONE || state.drawobjects== Mongeobjects.PLANE_LIFT || state.drawobjects == Mongeobjects.HYPERBOLA || (state.drawobjects== Mongeobjects.CYLINDER && state.cylinderPhase == CylinderPhase.PICK_DIRECTION)||
            state.drawobjects == Mongeobjects.TRANSPARALLEL ||
            state.drawobjects == Mongeobjects.TRANSORTH ||
            (state.mongeMode == DrawingModeMonge.PUDORYS &&
                    (state.drawobjects == Mongeobjects.SEGMENTS || state.drawobjects == Mongeobjects.LINES || state.drawobjects == Mongeobjects.PLANE) &&
                    (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL))

    val allowedConicLift = state.drawobjects == Mongeobjects.RULED_SURFACE || state.drawobjects == Mongeobjects.INTERSECTION || state.drawobjects == Mongeobjects.PLANE_LIFT || ((state.drawobjects in listOf(
        Mongeobjects.NONE,
        Mongeobjects.CIRCLE,
        Mongeobjects.PARABOLA,
        Mongeobjects.ELLIPSE,
        Mongeobjects.HYPERBOLA,
        Mongeobjects.CYLINDER,
        Mongeobjects.REGULAR_POLYGON_IN_PLANE
    ) && state.projekcnityp in listOf(ProjectionType.ASSOCIATED, ProjectionType.SINGLE)
            ) ||
            state.projectionPhase in listOf(
        "lift_conic_select_plane",
        "lift_conic_select_plane_bokorys",
        "lift_parabola_select_plane_bokorys",
        "lift_parabola_select_plane_bokorys",
        "lift_hyperbola_select_plane_bokorys",
        "lift_hyperbola_select_plane_bokorys"
    ))

    if ((state.drawobjects== Mongeobjects.TRANSPARALLEL || state.drawobjects== Mongeobjects.TRANSORTH) && (state.selectedLineForParallelPudorys != null
                || state.selectedLineForParallelNarys != null ||
                state.selectedSegmentForParallelBokorys != null || state.selectedSegmentForParallelBokorys != null)){
        return
    }

    val nearest = state.snappedLineBokorys

    if (nearest != null) {
        val line = nearest

        // 🔁 nová větev: pokud jsme v režimu výběru rovin
        if (allowedConicLift) {
            val planeId = when (val t = line) {
                is PlaneTraceBokorys -> t.parentId ?: t.parent?.id
                else -> null
            }

            val plane = planeId?.let { id -> state.planes3D.find { it.id == id } }
            if (plane != null) {
                if (state.isShiftPressed) {
                    toggleSelectionPlane(plane, state) // uvnitř porovnávej přes id!
                    return
                }
                clearSelection(state)              // nejdřív smaž všechno…
                state.selectedPlanes.clear()       // (volitelné, pokud to clearSelection už dělá)
                state.selectedPlanes.add(plane)
                markPlaneForInPlaneConstruction(state, plane)

                if (state.drawobjects== Mongeobjects.PLANE_LIFT)
                {
                    val eq = plane.equation
                    if (eq == null) {
                        println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                        return
                    }

                    // Degenerace pro bokorysnu = rovina kolmá k bokorysně (normála ⟂ x).
                    val nrm = kotlin.math.sqrt(eq.a * eq.a + eq.b * eq.b + eq.c * eq.c)
                    if (nrm < 1e-9f || kotlin.math.abs(eq.a) / nrm < 1e-4f) {
                        println("⚠️ Rovina ${plane.name} je v bokorysně kolmá (degenerace) – výběr zamítnut.")
                        return
                    }
                    setProjectionPhase("ready_planelift", state)
                    state.selectedPlaneForCircle = plane
                    state.triggerRedraw++
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    return
                }

                if (finalizePendingConicLiftWithPlane(state, plane)) return

                if (state.drawobjects == Mongeobjects.RULED_SURFACE) {

                }

                println("Vybrána rovina v BOKORYSU: ${plane.name}")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                return // ✅ může zůstat, pokud výběr roviny má být samostatný
            }
        }
        if (allowed) {
            if (false) return


            clearSelection(state)
            /* 2) přidej novou přímku do obou kolekcí */
            state.selectedLinesBokorys.add(line)       // instance (starý systém)
            state.selectedLineIdsBokorys.add(line.id)
            decideObjectForLift(state)
            advancePendingLineOrPointConstruction(state)
            println("Vybrána přímka v BOKORYSU (bez shiftu): ${line.name}")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        } else {
            val isInParallelConstruction = (
                    state.projectionPhase == "bokorys_start" &&
                            state.constructionModifier == ConstructionModifier.PARALLEL
                    )
            if (!isInParallelConstruction) {
                state.selectedLinesPudorys.clear()
                state.selectedLinesNarys.clear()
                state.selectedLinesBokorys.clear()
                state.selectedPlanes.clear()
            }
        }
    }
    // 🟣 Pokus o výběr kuželosečky podle kurzoru
    val selectedConic = state.snappedConicBokorys ?: return

    val parentId = selectedConic.parentId ?: selectedConic.parent?.id

    if (parentId != null) {
        state.spheres3D.find { it.id == parentId }?.let { sphere ->
            if (!state.isShiftPressed) clearSelection(state)
            toggleSelectionSphere3D(sphere, state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        }
        val parent3D = state.conics3D.find { it.id == parentId }
        if (parent3D != null) {
            if (state.isShiftPressed) toggleSelectionConic3D(parent3D, state)
            else {
                clearSelection(state)
                toggleSelectionConic3D(parent3D, state)
            }
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        } else {
            // fallback: 3D chybí? vyber aspoň obě 2D projekce se stejným parentId
            val pSibs = state.conicsPudorys.filter { it.parentId == parentId || it.parent?.id == parentId }
            val nSibs = state.conicsNarys  .filter { it.parentId == parentId || it.parent?.id == parentId }
            val bSibs = state.conicsBokorys .filter { it.parentId == parentId || it.parent?.id == parentId }
            if (!state.isShiftPressed) clearSelection(state)
            state.selectedConicsPudorys.addAll(pSibs)
            state.selectedConicsNarys.addAll(nSibs)
            state.selectedConicsBokorys.addAll(bSibs)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        }
    } else {
        // opravdu orphan 2D → původní chování
        if (state.isShiftPressed) {
            toggleSelectionBokorysConic(selectedConic, state)
        } else {
            clearSelection(state)
            toggleSelectionBokorysConic(selectedConic, state)
        }
        return
    }
}
fun toggleSelectionBokorysLine(
    line: NamedLineBokorys,
    state: MongeState
) {    // 🔁 1. Režim doplnění

    val tolerance = 0.01f
    val existing = state.selectedLinesBokorys.find {
        abs(it.point.y - line.point.y) < tolerance &&
                abs(it.point.z - line.point.z) < tolerance &&
                abs(it.direction.x - line.direction.x) < tolerance &&
                abs(it.direction.y - line.direction.y) < tolerance
    }

    if (existing != null) {
        state.selectedLinesBokorys.remove(existing)
        println("Odznačena přímka v BOKORYSU: ${line.name}, (x=${line.point.y}, z=${line.point.z})")
    } else {
        if (false) return
        state.selectedLinesBokorys.add(line)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("Označena přímka v BOKORYSU: ${line.name}, (x=${line.point.y}, z=${line.point.z})")
    }
}

fun handleSelectionAOLine(state: MongeState) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }

    val isNearPoint = state.snappedPointPudorys != null
    if (isNearPoint) return


    val target = state.axoOverlayLines.find { it.id == state.snappedAOLineId }

    if (target != null) {
        state.selectedAOLineIds.clear()
        state.selectedAOLineIds.add(target.id)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        println("🔵 Vybrána pomocná přímka: ${target.name} (${target.p},${target.dir})")
    }
}
fun toggleSelectionAxoLine(
    line: Line3DProjectionAxo,
    state: MongeState
) {    // 🔁 1. Režim doplnění

    val tolerance = 0.01f
    val existing = state.selectedLinesAxo.find {
        abs(it.p.x - line.p.x) < tolerance &&
                abs(it.p.y - line.p.y) < tolerance &&
                abs(it.dir.x - line.dir.x) < tolerance &&
                abs(it.dir.y - line.dir.y) < tolerance
    }

    if (existing != null) {
        state.selectedLinesAxo.remove(existing)
        println("Odznačena AXO přímka : ${line.name}, (x=${line.p.x}, y=${line.p.y})")
    } else {
        if (false) return
        state.selectedLinesAxo.add(line)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("Označena AXO přímka: ${line.name}, (x=${line.p.x}, y=${line.p.y})")
    }
}
fun handleSelectionAxoLine(
    state: MongeState,
) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    if (state.projectionPhase == "trans_parallel_temp_point_axo") {
        return
    }
    val snapped = state.snappedAxoPoint
    if (snapped != null) {
        val snappedX = snapped.y
        val snappedY = snapped.y
        val isNearPoint = state.pointsAxo.any {
            abs(it.x - snappedX) < 0.01f && abs(it.y - snappedY) < 0.01f
        }

        if (isNearPoint) return
    }

    val isNearSegment = state.snappedSegmentAxo!=null
    if (isNearSegment) return

    /* ───────────────────── hlavní podmínka aktivního režimu ────────────────── */
    val allowed = state.drawobjects == Mongeobjects.RULED_SURFACE || state.drawobjects == Mongeobjects.INTERSECTION || state.drawobjects == Mongeobjects.NONE || state.drawobjects== Mongeobjects.PLANE_LIFT || state.drawobjects == Mongeobjects.HYPERBOLA || (state.drawobjects== Mongeobjects.CYLINDER && state.cylinderPhase == CylinderPhase.PICK_DIRECTION)||
            state.drawobjects == Mongeobjects.TRANSPARALLEL ||
            state.drawobjects == Mongeobjects.TRANSORTH ||
            (state.mongeMode == DrawingModeMonge.PUDORYS &&
                    (state.drawobjects == Mongeobjects.SEGMENTS || state.drawobjects == Mongeobjects.LINES || state.drawobjects == Mongeobjects.PLANE) &&
                    (state.constructionModifier == ConstructionModifier.PARALLEL || state.constructionModifier == ConstructionModifier.ORTHOGONAL))

    if ((state.drawobjects== Mongeobjects.TRANSPARALLEL || state.drawobjects== Mongeobjects.TRANSORTH) && (state.selectedLineForParallelPudorys != null
                || state.selectedLineForParallelAxo!= null ||
                state.selectedSegmentForParallelAxo != null || state.selectedSegmentForParallelAxo != null)){
        return
    }

    val nearest = state.snappedLineAxo

    if (nearest != null) {
        val line = nearest

        if (allowed) {
            if (false) return


            clearSelection(state)
            /* 2) přidej novou přímku do obou kolekcí */
            state.selectedLinesAxo.add(line)       // instance (starý systém)
            state.selectedLineIdsAxo.add(line.id)
            decideObjectForLift(state)
            advancePendingLineOrPointConstruction(state)
            println("Vybrána AXO přímka (bez shiftu): ${line.name}")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        } else {
            val isInParallelConstruction = (
                    state.projectionPhase == "axo_start" &&
                            state.constructionModifier == ConstructionModifier.PARALLEL
                    )
            if (!isInParallelConstruction) {
                state.selectedLinesPudorys.clear()
                state.selectedLinesNarys.clear()
                state.selectedLinesBokorys.clear()
                state.selectedLinesAxo.clear()
                state.selectedPlanes.clear()
            }
        }
    }
}
