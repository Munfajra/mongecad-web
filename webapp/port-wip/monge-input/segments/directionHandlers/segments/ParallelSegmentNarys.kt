package monge.input.segments.directionHandlers.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import serialization.commitSnapshot
import model.*
import model.classes.HelpSegmentNarys
import model.classes.Point3DNarys
import model.classes.Segment2DNarys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.dotProduct
import utils.getLogicalCursor

fun handleParallelSegmentNarys(
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
    state.currentLineStyleSettings

    when (state.projectionPhase) {
        "narys_start" -> {
            if (!tryPickSegmentDirectionNarys(state, orthogonal = false)) return
            setProjectionPhase("segment_parallel_place_line_narys", state)
            return
        }

        "segment_parallel_place_line_narys" -> {
            state.pendingLinePointNarys = Offset(logical.x, -logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("📍 Dočasná přímka rovnoběžná se směrem umístěna bodem: ${state.pendingLinePointNarys}")
            setProjectionPhase("segment_parallel_first_point_narys", state)
            return
        }

        "segment_parallel_first_point_narys" -> {
            val A = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!
            val length = dir.getDistance()
            if (length < 1e-6f) {
                println("❌ Neplatný směr přímky – nulová délka.")
                resetStavu(state)
                return
            }
            val unitDir = Offset(dir.x / length, dir.y / length)
            val rawClick = Offset(logical.x, -logical.y)
            val AP = rawClick - A
            val proj = A + unitDir * (AP.dotProduct(unitDir))

            val point =
                Point3DNarys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            state.segmentStartNarys = point
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🔹 První bod úsečky (projekce na přímku): $point")
            setProjectionPhase("segment_parallel_second_point_narys", state)
            return
        }

        "segment_parallel_second_point_narys" -> {
            val A = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!
            val length = dir.getDistance()
            val unitDir = Offset(dir.x / length, dir.y / length)
            val rawClick = Offset(logical.x, -logical.y)
            val AP = rawClick - A
            val proj = A + unitDir * (AP.dotProduct(unitDir))

            val end =
                Point3DNarys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            val start = state.segmentStartNarys!!
            if (state.projekcnityp== ProjectionType.SINGLE) {

                val style = state.currentLineStyleSettings

                val segment = Segment2DNarys(
                    start = start,
                    end = end,
                    name = "",
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth,
                    localColor = style.color, creationIndex = allocIndex(state)
                )

// ⚠️ Přiřaď parentSegment po vytvoření segmentu
                start.parentSegment = segment
                end.parentSegment = segment
                state.pointsNarys.add(start)
                state.pointsNarys.add(end)
                state.segmentsNarys.add(segment)

            }
            if (state.projekcnityp== ProjectionType.AUXILIARY) {
                val style = state.currentHelpLineStyleSettings
                val segment = HelpSegmentNarys(
                    start = start,
                    end = end,
                    name = "",
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth,
                    localColor = style.color, creationIndex = allocIndex(state)
                )
                state.helpSegmentsNarys.add(segment)
                println("✅ Pomocná rovnoběžná úsečka vytvořena")
            }


            // 🔄 Reset
            commitSnapshot(state)

            state.segmentStartNarys = null
            state.pendingDirectionNarys = null
            state.pendingLinePointNarys = null
            state.selectedLineForParallelNarys = null
            state.selectedSegmentForParallelNarys = null
            state.selectedLinesNarys.clear()
            state.selectedSegmentsNarys.clear()
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("segment_parallel_select_direction_narys", state)
            updateConstructionInfo(state)
            repeatCons(state)
            resetStavu(state)
        }
    }
}
fun handleParallelAssociatedSegmentFromNarys(
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

        // 1. Klik na A2
        "narys_start" -> {
            if (!tryPickSegmentDirectionNarys(state, orthogonal = false)) return
            setProjectionPhase("segment_parallel_place_line_narys", state)
            return
        }

        "segment_parallel_place_line_narys" -> {
            if (state.pendingXA != null || state.pendingXB != null) return
            state.pendingLinePointNarys = Offset(logical.x, -logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("segment_orthogonal_first_point_narys_pudorys", state)
            return
        }

        "segment_orthogonal_first_point_narys_pudorys" -> {

            val A = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!
            val length = dir.getDistance()
            if (length < 1e-6f) return println("❌ Nulový směr")

            val unitDir = Offset(dir.x / length, dir.y / length)
            val raw = Offset(logical.x, -logical.y)
            val proj = A + unitDir * (unitDir.dotProduct(raw - A))

            val point =
                Point3DNarys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            state.segmentStartNarys = point
            state.pendingXA = point.x
            state.pendingZA = point.z
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟥 A_2 uloženo: x=${state.pendingXA}, y=${state.pendingZA}")
            setProjectionPhase("narys_segment_associated_B_narys_start_orthogonal", state)
            return
        }
//B2
        "narys_segment_associated_B_narys_start_orthogonal" -> {
            val A = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!
            val length = dir.getDistance()
            val unitDir = Offset(dir.x / length, dir.y / length)
            val raw = Offset(logical.x, -logical.y)
            val proj = A + unitDir * (unitDir.dotProduct(raw - A))
            val end =
                Point3DNarys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            state.pendingXB = end.x
            state.pendingZB = end.z
            setProjectionPhase("pudorys_segment_associated_A_narys_start", state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟥 B₁ uloženo: x=${state.pendingXB}, y=${state.pendingZB}")
            state.pendingMongeModeChange = DrawingModeMonge.PUDORYS
        }
//A1
        "pudorys_segment_associated_A_narys_start" -> {
            if (!tryPickSegmentDirectionPudorys(state, orthogonal = false)) return
            setProjectionPhase("segment_parallel_place_line_pudorys", state)
            return
        }

        "segment_parallel_place_line_pudorys" -> {
            if (state.pendingZA == null || state.pendingZB == null) return
            state.pendingLinePointPudorys = Offset(logical.x, logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("narys_segment_associated_A_narys_start", state)
            handleProjectionPhase("narys_segment_associated_A_narys_start", state)
            println("🖱️ Klik v nárysu: logical.x = ${logical.x}, -logical.y = ${-logical.y}")
        }
    }
}
