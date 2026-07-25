package monge.input.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.HelpSegmentPudorys
import model.classes.Line3DProjectionPudorys
import model.classes.Point3DPudorys
import model.classes.Segment2DPudorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.dotProduct
import utils.getLogicalCursor

fun handleSegmentOnLineP(
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
    if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
        when (state.projectionPhase){
            "pudorys_start" -> {
                state.pendingLinePointPudorys= Offset(logical.x,logical.y)
                setProjectionPhase("sol_pudorys_second_point_dir",state)
            }
            "sol_pudorys_second_point_dir" -> {state.pendingPoint2=Offset(logical.x,logical.y)
                val point1= Point3DPudorys(state.pendingLinePointPudorys!!.x, state.pendingLinePointPudorys!!.y)
                val point2= Point3DPudorys(state.pendingPoint2!!.x, state.pendingPoint2!!.y)
                val dir = Offset(point2.x-point1.x,point2.y-point1.y)
            val line = Line3DProjectionPudorys(point1, dir, creationIndex = allocIndex(state))
                state.selectedLineForParallelPudorys=line
                state.pendingDirection = line.direction
                setProjectionPhase("sol_begin_segment_pud",state)
                return
            }

        }
    }

    if (state.projectionPhase=="sol_begin_segment_pud"){
        if (state.selectedLineForParallelPudorys==null) return
        val linepoint = state.selectedLineForParallelPudorys!!.point
        val A = Offset(linepoint.x,linepoint.y)
        val dir =  state.selectedLineForParallelPudorys!!.direction
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
        setProjectionPhase("sol_second_point_pudorys", state)
        return
    }

    if (state.projectionPhase== "sol_second_point_pudorys") {
        if (state.selectedLineForParallelPudorys==null) return
        val linepoint = state.selectedLineForParallelPudorys!!.point
        val A = Offset(linepoint.x,linepoint.y)
        val dir =  state.selectedLineForParallelPudorys!!.direction
        val length = dir.getDistance()
        val unitDir = Offset(dir.x / length, dir.y / length)
        val rawClick = Offset(logical.x, logical.y)
        val AP = rawClick - A
        val proj = A + unitDir * (AP.dotProduct(unitDir))

        val end = Point3DPudorys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
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
            commitSnapshot(state)

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
            commitSnapshot(state)
            updateConstructionInfo(state)
            repeatCons(state)
            resetStavu(state)
            println("✅ Pomocná rovnoběžná úsečka vytvořena")
        }


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
