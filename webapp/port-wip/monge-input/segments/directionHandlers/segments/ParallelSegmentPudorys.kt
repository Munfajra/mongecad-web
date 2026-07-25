package monge.input.segments.directionHandlers.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import serialization.commitSnapshot
import model.*
import model.classes.HelpSegmentPudorys
import model.classes.Point3DPudorys
import model.classes.Segment2DPudorys
import monge.input.segments.addHelpSegmentPudorysAndDetectPlanePolygon
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.dotProduct
import utils.getLogicalCursor

fun handleParallelSegmentPudorys(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    when (state.projectionPhase) {
        "pudorys_start" -> {
            if (!tryPickSegmentDirectionPudorys(state, orthogonal = false)) return
            setProjectionPhase("segment_parallel_place_line_pudorys", state)
            return
        }
    }
    if (state.projectionPhase== "segment_parallel_place_line_pudorys") {
            state.pendingLinePointPudorys = Offset(logical.x, logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("📍 Dočasná přímka rovnoběžná se směrem umístěna bodem: ${state.pendingLinePointPudorys}")
            setProjectionPhase("segment_parallel_first_point_pudorys", state)
            return
        }

    if (state.projectionPhase=="segment_parallel_first_point_pudorys"){
            val A = state.pendingLinePointPudorys!!
            val dir = state.pendingDirection!!
            val length = dir.getDistance()
            if (length < 1e-6f) {
                println("❌ Neplatný směr přímky – nulová délka.")
                resetStavu(state)
                return
            }
            val unitDir = Offset(dir.x / length, dir.y / length)
            val rawClick = Offset(logical.x, logical.y)
            val AP = rawClick - A
            val proj = A + unitDir * (AP.dotProduct(unitDir))

            val point =
                Point3DPudorys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            state.segmentStartPudorys = point
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🔹 První bod úsečky (projekce na přímku): $point")
            setProjectionPhase("segment_parallel_second_point_pudorys", state)
            return
        }

    if (state.projectionPhase== "segment_parallel_second_point_pudorys") {
            val A = state.pendingLinePointPudorys!!
            val dir = state.pendingDirection!!
            val length = dir.getDistance()
            val unitDir = Offset(dir.x / length, dir.y / length)
            val rawClick = Offset(logical.x, logical.y)
            val AP = rawClick - A
            val proj = A + unitDir * (AP.dotProduct(unitDir))

            val end =
                Point3DPudorys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            val start = state.segmentStartPudorys!!
            // ✅ Vytvoření úsečky
            if (state.projekcnityp== ProjectionType.SINGLE) {
                val style = state.currentLineStyleSettings
                val segment = Segment2DPudorys(
                    start = start,
                    end = end,
                    name = "",
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth,
                    localColor = style.color, creationIndex = allocIndex(state)
                )
                // 🧠 Nastavení parentSegment bez copy()
                start.parentSegment = segment
                end.parentSegment = segment
                state.pointsPudorys.add(start)
                state.pointsPudorys.add(end)
                state.segmentsPudorys.add(segment)
                updateConstructionInfo(state)
                repeatCons(state)
                resetStavu(state)
            }
        if (state.projekcnityp== ProjectionType.AUXILIARY) {
            val style = state.currentHelpLineStyleSettings
            val segment = HelpSegmentPudorys(
                start = state.segmentStartPudorys!!,
                end = end,
                name = "",
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth,
                localColor = style.color, creationIndex = allocIndex(state)

            )
            addHelpSegmentPudorysAndDetectPlanePolygon(state, segment)
            updateConstructionInfo(state)
            repeatCons(state)
            resetStavu(state)
            println("✅ Pomocná rovnoběžná úsečka vytvořena")
        }

        commitSnapshot(state)

            // 🔄 Reset
            state.segmentStartPudorys = null
            state.pendingDirection = null
            state.pendingLinePointPudorys = null
            state.selectedLineForParallelPudorys = null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("pudorys_start", state)
        updateConstructionInfo(state)
        repeatCons(state)
        resetStavu(state)
        }
    }
fun handleParallelAssociatedSegmentFromPudorys(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState,
    change: PointerInputChange
) {
    if (!change.changedToDown()) return
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {

        // 1. Klik na A₁
        "pudorys_start" -> {
            if (!tryPickSegmentDirectionPudorys(state, orthogonal = false)) return
            setProjectionPhase("segment_parallel_place_line_pudorys", state)
            return
        }

        "segment_parallel_place_line_pudorys" -> {
            if (state.pendingXA != null || state.pendingXB != null) return
            state.pendingLinePointPudorys = Offset(logical.x, logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("segment_orthogonal_first_point_pudorys_narys", state)
            return
        }

        "segment_orthogonal_first_point_pudorys_narys" -> {

            val A = state.pendingLinePointPudorys!!
            val dir = state.pendingDirection!!
            val length = dir.getDistance()
            if (length < 1e-6f) return println("❌ Nulový směr")

            val unitDir = Offset(dir.x / length, dir.y / length)
            val raw = Offset(logical.x, logical.y)
            val proj = A + unitDir * (unitDir.dotProduct(raw - A))

            val point =
                Point3DPudorys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            state.segmentStartPudorys = point
            state.pendingXA = point.x
            state.pendingYA = point.y
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟥 A₁ uloženo: x=${state.pendingXA}, y=${state.pendingYA}")
            setProjectionPhase("pudorys_segment_associated_B_pudorys_start_orthogonal", state)
            return
        }

        "pudorys_segment_associated_B_pudorys_start_orthogonal" -> {
            val A = state.pendingLinePointPudorys!!
            val dir = state.pendingDirection!!
            val length = dir.getDistance()
            val unitDir = Offset(dir.x / length, dir.y / length)
            val raw = Offset(logical.x, logical.y)
            val proj = A + unitDir * (unitDir.dotProduct(raw - A))
            val end =
                Point3DPudorys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            state.pendingXB = end.x
            state.pendingYB = end.y
            setProjectionPhase("narys_segment_associated_A_pudorys_start", state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟥 B₁ uloženo: x=${state.pendingXB}, y=${state.pendingYB}")
            state.pendingMongeModeChange = DrawingModeMonge.NARYS}

        "narys_segment_associated_A_pudorys_start" -> {
            if (!tryPickSegmentDirectionNarys(state, orthogonal = false)) return
            setProjectionPhase("segment_parallel_place_line_narys", state)
            return
        }

        "segment_parallel_place_line_narys" -> {
            if (state.pendingYA == null || state.pendingYB == null) return
            state.pendingLinePointNarys = Offset(logical.x, -logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("narys_segment_associated_A_pudorys_start", state)
            handleProjectionPhase("narys_segment_associated_A_pudorys_start", state)
            updateConstructionInfo(state)
            return
        }
    }
}
