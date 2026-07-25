package monge.input.segments.directionHandlers.segments

import monge.input.segments.addSegment3DPlain
import utils.System
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import serialization.commitSnapshot
import model.*
import model.classes.HelpSegmentNarys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.dotProduct
import utils.getLogicalCursor
import kotlin.math.abs

fun handleOrthogonalSegmentNarys(
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
        "narys_start" -> {
            if (!tryPickSegmentDirectionNarys(state, orthogonal = true)) return
            setProjectionPhase("segment_orthogonal_place_line_narys", state)
            return
        }

        "segment_orthogonal_place_line_narys" -> {
            state.pendingLinePointNarys = Offset(logical.x, -logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("segment_orthogonal_first_point_narys", state)
            return
        }

        "segment_orthogonal_first_point_narys" -> {
            val a = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!
            val length = dir.getDistance()
            if (length < 1e-6f) return println("❌ Nulový směr")

            val unitDir = Offset(dir.x / length, dir.y / length)
            val raw = Offset(logical.x, -logical.y)
            val proj = a + unitDir * (unitDir.dotProduct(raw - a))

            val point =
                Point3DNarys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            state.segmentStartNarys = point
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🔹 První bod kolmice: $point")
            setProjectionPhase("segment_orthogonal_second_point_narys", state)
            return
        }

        "segment_orthogonal_second_point_narys" -> {
            val a = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!
            val length = dir.getDistance()
            val unitDir = Offset(dir.x / length, dir.y / length)
            val raw = Offset(logical.x, -logical.y)
            val proj = a + unitDir * (unitDir.dotProduct(raw - a))
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
                updateConstructionInfo(state)
                repeatCons(state)
                commitSnapshot(state)
                resetStavu(state)
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
                updateConstructionInfo(state)
                repeatCons(state)
                commitSnapshot(state)
                resetStavu(state)
                println("✅ Kolmá pomocná úsečka vytvořena")
            }


            // Reset
            state.segmentStartNarys = null
            state.pendingDirectionNarys = null
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
}
fun handleOrthogonalAssociatedSegmentFromNarys(
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
            if (!tryPickSegmentDirectionNarys(state, orthogonal = true)) return
            setProjectionPhase("segment_orthogonal_place_line_narys", state)
            return
        }

        "segment_orthogonal_place_line_narys" -> {
            if (state.pendingXA != null || state.pendingXB != null) return
            state.pendingLinePointNarys = Offset(logical.x, -logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("segment_orthogonal_first_point_narys_pudorys", state)
            return
        }

        "segment_orthogonal_first_point_narys_pudorys" -> {

            val a = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!
            val length = dir.getDistance()
            if (length < 1e-6f) return println("❌ Nulový směr")

            val unitDir = Offset(dir.x / length, dir.y / length)
            val raw = Offset(logical.x, -logical.y)
            val proj = a + unitDir * (unitDir.dotProduct(raw - a))

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
            val a = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!
            val length = dir.getDistance()
            val unitDir = Offset(dir.x / length, dir.y / length)
            val raw = Offset(logical.x, -logical.y)
            val proj = a + unitDir * (unitDir.dotProduct(raw - a))
            val end =
                Point3DNarys(proj.x, proj.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
            state.pendingXB = end.x
            state.pendingZB = end.z
            setProjectionPhase("pudorys_segment_associated_A_narys_start", state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟥 B₁ uloženo: x=${state.pendingXB}, y=${state.pendingZB}")
            state.pendingMongeModeChange = DrawingModeMonge.PUDORYS}
//A1
        "pudorys_segment_associated_A_narys_start" -> {

            if (!tryPickSegmentDirectionPudorys(state, orthogonal = true)) return
            setProjectionPhase("segment_orthogonal_place_line_pudorys", state)
            return
        }

        "segment_orthogonal_place_line_pudorys" -> {
            if (state.pendingZA == null || state.pendingZB == null) return
            state.pendingLinePointPudorys = Offset(logical.x, logical.y)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            setProjectionPhase("narys_segment_associated_A_narys_start", state)
            handleProjectionPhase("narys_segment_associated_A_narys_start", state)
            println("🖱️ Klik v nárysu: logical.x = ${logical.x}, -logical.y = ${-logical.y}")
            updateConstructionInfo(state)
        }}
}
fun handleProjectionPhase(phase: String, state: MongeState) {
    when (phase) {
        "narys_segment_associated_A_narys_start" -> {
            val xA = state.pendingXA ?: return
            val zA = state.pendingZA ?: return
            val xB = state.pendingXB ?: return
            val zB = state.pendingZB ?: return

            fun projectYFromX(xTarget: Float, point: Offset, direction: Offset): Float {
                val dx = direction.x.toDouble()
                val dy = direction.y.toDouble()
                val px = point.x.toDouble()
                val py = point.y.toDouble()
                return if (abs(dx) < 1e-10) py.toFloat() else (py + ((xTarget - px) / dx) * dy).toFloat()
            }

            val yA = projectYFromX(xA, state.pendingLinePointPudorys!!, state.pendingDirection!!)
            val yB = projectYFromX(xB, state.pendingLinePointPudorys!!, state.pendingDirection!!)

            val a = Point3D(xA, yA, zA, name = "", creationIndex = allocIndex(state))
            val b = Point3D(xB, yB, zB, name = "", creationIndex = allocIndex(state))
            val style = state.currentLineStyleSettings
            val segment = Segment3D(
                a,
                b,
                color = style.color,
                strokeWidth = style.strokeWidth,
                lineStyle = style.style,
                creationIndex = allocIndex(state)
            )

            val p1 = Point3DPudorys(
                xA,
                yA,
                name = "",
                parent = a,
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state),
            )
            val p2 = Point3DPudorys(
                xB,
                yB,
                name = "",
                parent = b,
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )
            val n1 = Point3DNarys(
                xA,
                zA,
                name = "",
                parent = a,
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state),
            )
            val n2 =
                Point3DNarys(xB, zB, name = "", parent = b, isSegmentEndpoint = true, creationIndex = allocIndex(state))

            val segmentPudorys =
                Segment2DPudorys(p1, p2, parent = segment, creationIndex = allocIndex(state), parentId = segment.id)
            val segmentNarys =
                Segment2DNarys(n1, n2, parent = segment, creationIndex = allocIndex(state), parentId = segment.id)

            p1.parentSegment = segmentPudorys
            p2.parentSegment = segmentPudorys
            n1.parentSegment = segmentNarys
            n2.parentSegment = segmentNarys

            state.pointsPudorys.addAll(listOf(p1, p2))
            state.pointsNarys.addAll(listOf(n1, n2))
            state.sharedPoints3D.addAll(listOf(a, b))
            addSegment3DPlain(state, segment)
            state.segmentsPudorys.add(segmentPudorys)
            state.segmentsNarys.add(segmentNarys)
            commitSnapshot(state)
            println("✅ Sdružená 3D úsečka vytvořena (kolmice z nárysu): $segment")

            // Reset
            state.pendingXA = null
            state.pendingYA = null
            state.pendingZA = null
            state.pendingXB = null
            state.pendingYB = null
            state.pendingZB = null
            state.segmentStartPudorys = null
            state.pendingDirection = null
            state.pendingLinePointPudorys = null
            state.pendingDirectionNarys = null
            state.pendingLinePointNarys = null
            state.selectedLineForParallelPudorys = null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLineForParallelNarys = null
            state.selectedSegmentForParallelNarys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
            state.selectedLinesNarys.clear()
            state.selectedSegmentsNarys.clear()
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.mongeMode = DrawingModeMonge.NARYS
            repeatCons(state)
            resetStavu(state)
            updateConstructionInfo(state)

        }

        "narys_segment_associated_A_pudorys_start" -> {
            val xA = state.pendingXA ?: return
            val yA = state.pendingYA ?: return
            val xB = state.pendingXB ?: return
            val yB = state.pendingYB ?: return

            val base = state.pendingLinePointNarys!!
            val dir = state.pendingDirectionNarys!!

            fun projectZFromX(xTarget: Float, point: Offset, direction: Offset): Float {
                val dx = direction.x.toDouble()
                val dz = direction.y.toDouble()
                val px = point.x.toDouble()
                val pz = point.y.toDouble()
                return if (abs(dx) < 1e-10) pz.toFloat() else (pz + ((xTarget - px) / dx) * dz).toFloat()
            }

            val zA = projectZFromX(xA, base, dir)
            val zB = projectZFromX(xB, base, dir)

            val a = Point3D(xA, yA, zA, name = "", creationIndex = allocIndex(state))
            val b = Point3D(xB, yB, zB, name = "", creationIndex = allocIndex(state))
            val style = state.currentLineStyleSettings
            val segment = Segment3D(
                a,
                b,
                color = style.color,
                strokeWidth = style.strokeWidth,
                lineStyle = style.style,
                creationIndex = allocIndex(state)
            )

            val p1 = Point3DPudorys(
                xA,
                yA,
                name = "",
                parent = a,
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )
            val p2 = Point3DPudorys(
                xB,
                yB,
                name = "",
                parent = b,
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )
            val n1 =
                Point3DNarys(xA, zA, name = "", parent = a, isSegmentEndpoint = true, creationIndex = allocIndex(state))
            val n2 =
                Point3DNarys(xB, zB, name = "", parent = b, isSegmentEndpoint = true, creationIndex = allocIndex(state))

            val segmentPudorys =
                Segment2DPudorys(p1, p2, parent = segment, creationIndex = allocIndex(state), parentId = segment.id)
            val segmentNarys =
                Segment2DNarys(n1, n2, parent = segment, creationIndex = allocIndex(state), parentId = segment.id)

            p1.parentSegment = segmentPudorys
            p2.parentSegment = segmentPudorys
            n1.parentSegment = segmentNarys
            n2.parentSegment = segmentNarys

            state.pointsPudorys.addAll(listOf(p1, p2))
            state.pointsNarys.addAll(listOf(n1, n2))
            state.sharedPoints3D.addAll(listOf(a, b))
            addSegment3DPlain(state, segment)
            state.segmentsPudorys.add(segmentPudorys)
            state.segmentsNarys.add(segmentNarys)
            commitSnapshot(state)
            println("✅ Sdružená 3D úsečka vytvořena (kolmice z půdorysu): $segment")

            // Reset vstupního stavu
            state.pendingXA = null
            state.pendingYA = null
            state.pendingZA = null
            state.pendingXB = null
            state.pendingYB = null
            state.pendingZB = null
            state.segmentStartPudorys = null
            state.pendingDirection = null
            state.pendingLinePointPudorys = null
            state.pendingDirectionNarys = null
            state.pendingLinePointNarys = null
            state.selectedLineForParallelPudorys = null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLineForParallelNarys = null
            state.selectedSegmentForParallelNarys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
            state.selectedLinesNarys.clear()
            state.selectedSegmentsNarys.clear()
            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            resetStavu(state)
            updateConstructionInfo(state)
            repeatCons(state)

        }

    }
}
