package monge.input.selection

import utils.System
import androidx.compose.ui.geometry.Offset
import model.*
import model.classes.Segment2DAxo
import model.classes.Segment3D
import model.classes.SegmentsBokorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import monge.input.planeobjects.planelift.decideObjectForLift
import state.MongeState
import utils.getLogicalCursor
import kotlin.math.abs

fun toggleSelectionPudorysSegment(
    segment: SegmentsPudorys,
    state: MongeState
) {

    val existing = state.snappedSegmentPudorys

    if (existing != null) {
        state.selectedSegmentsPudorys.remove(existing)
        println("Odznačen úseček: ${segment.name}")
    } else {
        state.selectedSegmentsPudorys.add(segment)
        decideObjectForLift(state)
        println("Označen úseček: ${segment.name}")
    }
}

fun handleSelectionPudorysSegment(
    state: MongeState
) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    if (state.projectionPhase == "trans_parallel_temp_point_pudorys") {
        return
    }
    val allowed = state.drawobjects== Mongeobjects.NONE || state.drawobjects == Mongeobjects.PLANE_LIFT || state.drawobjects== Mongeobjects.CYLINDER ||
            state.drawobjects == Mongeobjects.TRANSPARALLEL ||
            state.drawobjects == Mongeobjects.TRANSORTH ||
            (state.mongeMode == DrawingModeMonge.PUDORYS &&
           (state.drawobjects == Mongeobjects.SEGMENTS|| state.drawobjects == Mongeobjects.LINES || state.drawobjects == Mongeobjects.PLANE)&&
                    (state.constructionModifier== ConstructionModifier.PARALLEL ||state.constructionModifier== ConstructionModifier.ORTHOGONAL ))
    if (!allowed) return
    if ((state.drawobjects== Mongeobjects.TRANSPARALLEL || state.drawobjects== Mongeobjects.TRANSORTH) && (state.selectedLineForParallelPudorys != null
                || state.selectedLineForParallelNarys != null ||
                state.selectedSegmentForParallelPudorys != null || state.selectedSegmentForParallelNarys != null)){
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
        val nearest = state.snappedSegmentPudorys
        if (nearest != null) {
            val seg = nearest

            if (state.isShiftPressed) {
                toggleSelectionPudorysSegment(seg, state)
                println("Vybrán úseček v PŮDORYSU (se shiftem): ${seg.name}")
            } else {
                state.selectedSegmentsPudorys.clear()
                state.selectedSegmentsPudorys.add(seg)
                decideObjectForLift(state)
                println("Vybrán úseček v PŮDORYSU (bez shiftu): ${seg.name}")
            }

            state.deferSelectionUntil = System.currentTimeMillis() + 100

        }
    }
fun handleSelectionNarysSegment(
    state: MongeState,
) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    if (state.projectionPhase == "trans_parallel_temp_point_narys") {
        return
    }
    val allowed = state.drawobjects== Mongeobjects.NONE || state.drawobjects == Mongeobjects.PLANE_LIFT|| state.drawobjects== Mongeobjects.CYLINDER||
            state.drawobjects == Mongeobjects.TRANSPARALLEL ||
            state.drawobjects == Mongeobjects.TRANSORTH ||
            (state.mongeMode == DrawingModeMonge.NARYS &&
                    (state.drawobjects == Mongeobjects.SEGMENTS|| state.drawobjects == Mongeobjects.LINES || state.drawobjects == Mongeobjects.PLANE)&&
                    (state.constructionModifier== ConstructionModifier.PARALLEL ||state.constructionModifier== ConstructionModifier.ORTHOGONAL ))
    if (!allowed) return
    if ((state.drawobjects== Mongeobjects.TRANSPARALLEL || state.drawobjects== Mongeobjects.TRANSORTH) && (state.selectedLineForParallelPudorys != null
                || state.selectedLineForParallelNarys != null ||
                state.selectedSegmentForParallelPudorys != null || state.selectedSegmentForParallelNarys != null)){
        return
    }
        val snapped = state.snappedPointLogical ?: Offset(
            getLogicalCursor(
                null,
                state.cursorPosition,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            ).x, -getLogicalCursor(
                null,
                state.cursorPosition,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            ).y)
        val snappedX = snapped.x
        val snappedZ = -snapped.y
        val isNearPoint = state.pointsNarys.any {
            abs(it.x - snappedX) < 0.01f && abs(it.z - snappedZ) < 0.01f
        }
        if (isNearPoint) return

        val nearest = state.snappedSegmentNarys
        if (nearest != null) {
            val seg = nearest

            if (state.isShiftPressed) {
                toggleSelectionNarysSegment(seg, state)
                println("Vybrán úseček v NÁRYSU (se shiftem): ${seg.name}")
            } else {
                state.selectedSegmentsNarys.clear()
                state.selectedSegmentsNarys.add(seg)
                decideObjectForLift(state)
                println("Vybrán úseček v NÁRYSU (bez shiftu): ${seg.name}")
            }

            state.deferSelectionUntil = System.currentTimeMillis() + 100

        }

    }
fun toggleSelectionNarysSegment(
    segment: SegmentsNarys,
    state: MongeState
) {
    val tolerance = 0.01f
    val existing = state.selectedSegmentsNarys.find {
        abs(it.start.x - segment.start.x) < tolerance &&
                abs(it.start.z - segment.start.z) < tolerance &&
                abs(it.end.x - segment.end.x) < tolerance &&
                abs(it.end.z - segment.end.z) < tolerance
    }

    if (existing != null) {
        state.selectedSegmentsNarys.remove(existing)
        println("Odznačen úseček: ${segment.name}")
    } else {
        state.selectedSegmentsNarys.add(segment)
        decideObjectForLift(state)
        println("Označen úseček: ${segment.name}")
    }
}

fun toggleSelectionBokorysSegment(
    segment: SegmentsBokorys,
    state: MongeState
) {
    val tolerance = 0.01f
    val existing = state.selectedSegmentsBokorys.find {
        abs(it.start.y - segment.start.y) < tolerance &&
                abs(it.start.z - segment.start.z) < tolerance &&
                abs(it.end.y - segment.end.y) < tolerance &&
                abs(it.end.z - segment.end.z) < tolerance
    }

    if (existing != null) {
        state.selectedSegmentsBokorys.remove(existing)
        println("Odznačen úseček: ${segment.name}")
    } else {
        state.selectedSegmentsBokorys.add(segment)
        decideObjectForLift(state)
        println("Označen úseček: ${segment.name}")
    }
}
fun toggleSelectionSegment3D(s: Segment3D, state: MongeState){
    state.selectedSegments3D.add(s)
    val narys = state.segmentsNarys.firstOrNull(){it.parentId == s.id}
    val pudorys = state.segmentsPudorys.firstOrNull(){it.parentId == s.id}
    if (narys != null) {state.selectedSegmentsNarys.add(narys)}
    if (pudorys!=null) {state.selectedSegmentsPudorys.add(pudorys)}
}
fun handleSelectionAOSegment(state: MongeState) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }

    val isNearPoint = state.snappedPointPudorys != null
    if (isNearPoint) return


    val target = state.axoOverlaySegments.find { it.id == state.snappedAOSegmentId }

    if (target != null) {
        state.selectedAOSegIds.clear()
        state.selectedAOSegIds.add(target.id)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        println("🔵 Vybrána pomocná přímka: ${target.name} (${target.start},${target.end})")
    }
}
fun handleSelectionBokorysSegment(
    state: MongeState
) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    if (state.projectionPhase == "trans_parallel_temp_point_pudorys") {
        return
    }
    val allowed = state.drawobjects== Mongeobjects.NONE || state.drawobjects == Mongeobjects.PLANE_LIFT || state.drawobjects== Mongeobjects.CYLINDER ||
            state.drawobjects == Mongeobjects.TRANSPARALLEL ||
            state.drawobjects == Mongeobjects.TRANSORTH ||
            (
                    (state.drawobjects == Mongeobjects.SEGMENTS|| state.drawobjects == Mongeobjects.LINES || state.drawobjects == Mongeobjects.PLANE)&&
                    (state.constructionModifier== ConstructionModifier.PARALLEL ||state.constructionModifier== ConstructionModifier.ORTHOGONAL ))
    if (!allowed) return
    if ((state.drawobjects== Mongeobjects.TRANSPARALLEL || state.drawobjects== Mongeobjects.TRANSORTH) && (state.selectedLineForParallelPudorys != null
                || state.selectedLineForParallelNarys != null ||
                state.selectedSegmentForParallelPudorys != null || state.selectedSegmentForParallelNarys != null)){
        return
    }

    val nearest = state.snappedSegmentBokorys
    if (nearest != null) {
        val seg = nearest

        if (state.isShiftPressed) {
            toggleSelectionBokorysSegment(seg, state)
            println("Vybrán úseček v BOKORYSU (se shiftem): ${seg.name}")
        } else {
            state.selectedSegmentsBokorys.clear()
            state.selectedSegmentsBokorys.add(seg)
            decideObjectForLift(state)
            println("Vybrán úseček v BOKORYSU (bez shiftu): ${seg.name}")
        }

        state.deferSelectionUntil = System.currentTimeMillis() + 100

    }
}
fun toggleSelectionAxoSegment(
    segment: Segment2DAxo,
    state: MongeState
) {
    val tolerance = 0.01f
    val existing = state.selectedSegmentsAxo.find {
        abs(it.start.x - segment.start.x) < tolerance &&
                abs(it.start.y - segment.start.y) < tolerance &&
                abs(it.end.x - segment.end.x) < tolerance &&
                abs(it.end.y - segment.end.y) < tolerance
    }

    if (existing != null) {
        state.selectedSegmentsAxo.remove(existing)
        println("Odznačena úsečka: ${segment.name}")
    } else {
        state.selectedSegmentsAxo.add(segment)
        decideObjectForLift(state)
        println("Označena úsečka: ${segment.name}")
    }
}
fun handleSelectionAxoSegment(
    state: MongeState
) {
    val isShiftPressed = state.isShiftPressed
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    if (state.projectionPhase == "trans_parallel_temp_point_axo") {
        return
    }
    val allowed = state.drawobjects== Mongeobjects.NONE || state.drawobjects == Mongeobjects.PLANE_LIFT || state.drawobjects== Mongeobjects.CYLINDER ||
            state.drawobjects == Mongeobjects.TRANSPARALLEL ||
            state.drawobjects == Mongeobjects.TRANSORTH ||
            (
                    (state.drawobjects == Mongeobjects.SEGMENTS|| state.drawobjects == Mongeobjects.LINES || state.drawobjects == Mongeobjects.PLANE)&&
                            (state.constructionModifier== ConstructionModifier.PARALLEL ||state.constructionModifier== ConstructionModifier.ORTHOGONAL ))
    if (!allowed) return
    if ((state.drawobjects== Mongeobjects.TRANSPARALLEL || state.drawobjects== Mongeobjects.TRANSORTH) && (state.selectedLineForParallelPudorys != null
                || state.selectedLineForParallelNarys != null ||
                state.selectedSegmentForParallelPudorys != null || state.selectedSegmentForParallelNarys != null)){
        return
    }

    val nearest = state.snappedSegmentAxo
    if (nearest != null) {
        val seg = nearest

        if (isShiftPressed) {
            toggleSelectionAxoSegment(seg, state)
            println("Vybrána AXO úsečka (se shiftem): ${seg.name}")
        } else {
            state.selectedSegmentsAxo.clear()
            state.selectedSegmentsAxo.add(seg)
            decideObjectForLift(state)
            println("Vybrána AXO úsečka (bez shiftu): ${seg.name}")
        }

        state.deferSelectionUntil = System.currentTimeMillis() + 100

    }
}