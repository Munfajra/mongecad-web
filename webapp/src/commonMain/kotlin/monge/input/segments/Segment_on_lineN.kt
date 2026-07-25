package monge.input.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.HelpSegmentNarys
import model.classes.Line3DProjectionNarys
import model.classes.Point3DNarys
import model.classes.Segment2DNarys
import monge.input.ConicArcs.single.getLogicalCursorNarys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.dotProduct

fun handleSegmentOnLineN(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    val logical = getLogicalCursorNarys(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection
    )
    if (state.selectedLineForParallelNarys == null && state.selectedSegmentForParallelNarys == null) {
        when (state.projectionPhase){
            "narys_start" -> {
                state.pendingLinePointNarys= Offset(logical.x,logical.y)
                setProjectionPhase("sol_narys_second_point_dir",state)
            }
            "sol_narys_second_point_dir" -> {state.pendingPoint2=Offset(logical.x,logical.y)
                val point1= Point3DNarys(state.pendingLinePointNarys!!.x, state.pendingLinePointNarys!!.y)
                val point2= Point3DNarys(state.pendingPoint2!!.x, state.pendingPoint2!!.y)
                val dir = Offset(point2.x-point1.x,point2.z-point1.z)
                val line = Line3DProjectionNarys(point1, dir, creationIndex = allocIndex(state))
                state.selectedLineForParallelNarys=line
                state.pendingDirectionNarys = line.direction
                setProjectionPhase("sol_begin_segment_nar",state)
                return
            }

        }
    }

    if (state.projectionPhase=="sol_begin_segment_nar"){
        if (state.selectedLineForParallelNarys==null) return
        val linepoint = state.selectedLineForParallelNarys!!.point
        val A = Offset(linepoint.x,linepoint.z)
        val dir =  state.selectedLineForParallelNarys!!.direction
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

        val point = Point3DNarys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
        state.segmentStartNarys = point
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        println("🔹 První bod úsečky (projekce na přímku): $point")
        setProjectionPhase("sol_second_point_narys", state)
        return
    }

    if (state.projectionPhase== "sol_second_point_narys") {
        if (state.selectedLineForParallelNarys==null) return
        val linepoint = state.selectedLineForParallelNarys!!.point
        val A = Offset(linepoint.x,linepoint.z)
        val dir =  state.selectedLineForParallelNarys!!.direction
        val length = dir.getDistance()
        val unitDir = Offset(dir.x / length, dir.y / length)
        val rawClick = Offset(logical.x, logical.y)
        val AP = rawClick - A
        val proj = A + unitDir * (AP.dotProduct(unitDir))

        val end = Point3DNarys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
        val start = state.segmentStartNarys!!
        // ✅ Vytvoření úsečky
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

            // 🧠 Nastavení parentSegment bez copy()
            start.parentSegment = segment
            end.parentSegment = segment
            state.pointsNarys.add(start)
            state.pointsNarys.add(end)
            state.segmentsNarys.add(segment)
            commitSnapshot(state)
            updateConstructionInfo(state)
            repeatCons(state)
            resetStavu(state)
        }
        if (state.projekcnityp== ProjectionType.AUXILIARY) {
            val style = state.currentHelpLineStyleSettings
            val segment = HelpSegmentNarys(
                start = state.segmentStartNarys!!,
                end = end,
                name = "",
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth,
                localColor = style.color, creationIndex = allocIndex(state)
            )
            state.helpSegmentsNarys.add(segment)
            commitSnapshot(state)
            updateConstructionInfo(state)
            repeatCons(state)
            resetStavu(state)
            println("✅ Pomocná úsečka vytvořena")
        }



        // 🔄 Reset
        state.segmentStartNarys = null
        state.pendingDirectionNarys= null
        state.pendingLinePointNarys = null
        state.selectedLineForParallelNarys = null
        state.selectedSegmentForParallelNarys = null
        state.selectedLinesNarys.clear()
        state.selectedSegmentsNarys.clear()
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        setProjectionPhase("narys_start", state)
        updateConstructionInfo(state)
        repeatCons(state)
        resetStavu(state)
    }
}