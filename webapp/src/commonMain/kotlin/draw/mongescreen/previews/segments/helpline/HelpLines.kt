package draw.mongescreen.previews.segments.helpline

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewPudorys
import model.*
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import utils.getLogicalCursor
import kotlin.math.abs

fun DrawScope.helpLineSegmentsSecondPointNarys(state: MongeState) {
    if (
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "pudorys_segment_associated_A_narys_start",
            "pudorys_segment_associated_B_narys_start",
            "narys_segment_associated_A_pudorys_start",
            "segment_parallel_place_line_pudorys",
            "segment_orthogonal_place_line_pudorys"
        )
    ) {
        val x = state.pendingXB
        val z = state.pendingZB
        if (x != null && z != null) {

            // Pokud z je (téměř) nulové, vynutíme "čáru" s malým rozdílem
            if (abs(z) < 1e-6f) {
                val start = Point3DNarys(x, 0f, "")
                val end = Offset(x, -50f) // svislice dolů od x₁₂ (v obrazovkových souřadnicích směrem nahoru)
                drawDashedPreviewLineNarys(
                    start = start,
                    cursorWorld = end,
                    color= Color.Gray,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset,
                    clipToAboveZ = false
                )
            } else {
                val start = Point3DNarys(x, z, "")
                val end = Offset(x, 0f)
                drawDashedPreviewLineNarys(
                    start = start,
                    cursorWorld = end,
                    color= Color.Gray,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset,
                    clipToAboveZ = false
                )
            }
        }
    }
}
fun DrawScope.helpLineSegmentsSecondPointPudorys(state: MongeState) {
    if (
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "narys_segment_associated_A_pudorys_start",
            "narys_segment_associated_B_pudorys_start",
            "segment_parallel_place_line_narys",
            "segment_orthogonal_place_line_narys"
        )
    ) {
        val x = state.pendingXB
        val y = state.pendingYB
        if (x != null && y != null) {

            if (abs(y) < 1e-6f) {
                // Pokud bod B₁ leží přesně na ose x₁₂, zobraz krátkou svislici
                val start = Point3DPudorys(x, 0f, "")
                val end = Offset(x, 50f)  // směr dolů na obrazovce
                drawDashedPreviewLinePudorys(
                    start = start,
                    cursorWorld = end,
                    color= Color.Gray,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset
                )
            } else {
                val start = Point3DPudorys(x, y, "")
                val end = Offset(x, 0f)
                drawDashedPreviewLinePudorys(
                    start = start,
                    cursorWorld = end,
                    color= Color.Gray,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset
                )
            }
        }
    }
}
fun DrawScope.helpLineSegmentsFirstPointNarys(state: MongeState) {
    if (
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "pudorys_segment_associated_A_narys_start",
            "segment_parallel_place_line_pudorys",
            "segment_orthogonal_place_line_pudorys"
        )
    ) {
        val x = state.pendingXA
        val z = state.pendingZA
        if (x != null && z != null) {
            if (abs(z) < 1e-6f) {
                // Bod A₂ leží na x₁₂ → vykreslit svislici
                val start = Point3DNarys(x, 0f, "")
                val end = Offset(x, -50f)  // směrem nahoru (v nárysu -z)
                drawDashedPreviewLineNarys(
                    start = start,
                    cursorWorld = end,
                    color= Color.Gray,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset,
                    clipToAboveZ = false
                )
            } else {
                val start = Point3DNarys(x, z, "")
                val end = Offset(x, 0f)
                drawDashedPreviewLineNarys(
                    start = start,
                    cursorWorld = end,
                    color= Color.Gray,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset,
                    clipToAboveZ = false
                )
            }
        }
    }
}
fun DrawScope.helpLineSegmentsFirstPointPudorys(state: MongeState) {
    if (
        state.drawobjects == Mongeobjects.SEGMENTS &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase in listOf(
            "narys_segment_associated_A_pudorys_start",
            "segment_parallel_place_line_narys",
            "segment_orthogonal_place_line_narys"
        )
    ) {
        val x = state.pendingXA
        val y = state.pendingYA
        if (x != null && y != null) {
            if (abs(y) < 1e-6f) {
                // A₁ leží na ose x₁₂ – vykresli krátkou svislici
                val start = Point3DPudorys(x, 0f, "")
                val end = Offset(x, 50f)  // směrem dolů na obrazovce
                drawDashedPreviewLinePudorys(
                    start = start,
                    color= Color.Gray,
                    cursorWorld = end,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset
                )
            } else {
                val start = Point3DPudorys(x, y, "")
                val end = Offset(x, 0f)
                drawDashedPreviewLinePudorys(
                    start = start,
                    color= Color.Gray,
                    cursorWorld = end,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset
                )
            }
        }
    }
}
fun DrawScope.previewSegmentNarysFromAssociatedPointsDir(state: MongeState, snappedPointLogical: Offset?) {
    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.projectionPhase in listOf(
            "narys_segment_associated_A_pudorys_start",
            "segment_parallel_place_line_narys",
            "segment_orthogonal_place_line_narys"
        ) &&
        state.pendingXA != null && state.pendingYA != null &&
        state.pendingXB != null && state.pendingYB != null
    ) {
        val cursorLogical = getLogicalCursor(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
        val fallbackBaseDirection = state.selectedLineForParallelNarys?.direction
            ?: state.selectedLinesNarys.firstOrNull()?.direction
            ?: state.selectedSegmentForParallelNarys?.let {
                Offset(it.end.x - it.start.x, it.end.z - it.start.z)
            }
            ?: state.selectedSegmentsNarys.firstOrNull()?.let {
                Offset(it.end.x - it.start.x, it.end.z - it.start.z)
            }
        val effectiveDirection = state.pendingDirectionNarys ?: run {
            val baseDirection = fallbackBaseDirection ?: return
            when (state.constructionModifier) {
                ConstructionModifier.ORTHOGONAL -> Offset(-baseDirection.y, baseDirection.x)
                ConstructionModifier.PARALLEL -> baseDirection
                else -> return
            }
        }

        val base = state.pendingLinePointNarys ?: Offset(cursorLogical.x, -cursorLogical.y)

        fun projectZFromX(xTarget: Float): Float {
            val dx = effectiveDirection.x.toDouble()
            val dz = effectiveDirection.y.toDouble()
            val px = base.x.toDouble()
            val pz = base.y.toDouble()
            if (abs(dx) < 1e-10) return pz.toFloat()
            val t = (xTarget - px) / dx
            return (pz + t * dz).toFloat()
        }

        val xA = state.pendingXA!!
        state.pendingYA!!
        val xB = state.pendingXB!!
        state.pendingYB!!
        val zA = projectZFromX(xA)
        val zB = projectZFromX(xB)

        val n1 = Point3DNarys(xA, zA, name = "", isSegmentEndpoint = true)
        val n2 = Point3DNarys(xB, zB, name = "", isSegmentEndpoint = true)

        drawDashedSegmentPreviewNarys(
            start = n1,
            cursorWorld = Offset(n2.x, -n2.z),
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            dashLength = 1f,
            gapLength = 0f,
            strokeWidth = 3f,
            color = Color.Red
        )
    }
}
fun DrawScope.previewSegmentPudorysFromAssociatedPointsDir(state: MongeState, snappedPointLogical: Offset?) {
    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.projectionPhase in listOf(
            "pudorys_segment_associated_A_narys_start",
            "segment_parallel_place_line_pudorys",
            "segment_orthogonal_place_line_pudorys"
        ) &&
        state.pendingXA != null && state.pendingZA != null &&
        state.pendingXB != null && state.pendingZB != null
    ) {
        val cursorLogical = getLogicalCursor(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
        val fallbackBaseDirection = state.selectedLineForParallelPudorys?.direction
            ?: state.selectedLinesPudorys.firstOrNull()?.direction
            ?: state.selectedSegmentForParallelPudorys?.let {
                Offset(it.end.x - it.start.x, it.end.y - it.start.y)
            }
            ?: state.selectedSegmentsPudorys.firstOrNull()?.let {
                Offset(it.end.x - it.start.x, it.end.y - it.start.y)
            }
        val effectiveDirection = state.pendingDirection ?: run {
            val baseDirection = fallbackBaseDirection ?: return
            when (state.constructionModifier) {
                ConstructionModifier.ORTHOGONAL -> Offset(-baseDirection.y, baseDirection.x)
                ConstructionModifier.PARALLEL -> baseDirection
                else -> return
            }
        }

        val base = state.pendingLinePointPudorys ?: Offset(cursorLogical.x, cursorLogical.y)

        fun projectYFromX(xTarget: Float): Float {
            val dx = effectiveDirection.x.toDouble()
            val dy = effectiveDirection.y.toDouble()
            val px = base.x.toDouble()
            val py = base.y.toDouble()
            if (abs(dx) < 1e-10) return py.toFloat()
            val t = (xTarget - px) / dx
            return (py + t * dy).toFloat()
        }

        val xA = state.pendingXA!!
        state.pendingZA!!
        val xB = state.pendingXB!!
        state.pendingZB!!
        val yA = projectYFromX(xA)
        val yB = projectYFromX(xB)

        val p1 = Point3DPudorys(xA, yA, name = "", isSegmentEndpoint = true)
        val p2 = Point3DPudorys(xB, yB, name = "", isSegmentEndpoint = true)

        drawDashedSegmentPreviewPudorys(
            start = p1,
            cursorWorld = Offset(p2.x, p2.y),
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            dashLength = 1f,
            gapLength = 0f,
            strokeWidth = 3f,
            color = Color.Red
        )
    }
}
