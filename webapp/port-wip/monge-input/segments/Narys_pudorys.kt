package monge.input.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import serialization.commitSnapshot
import model.*
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import monge.input.segments.directionHandlers.segments.handleOrthogonalAssociatedSegmentFromNarys
import monge.input.segments.directionHandlers.segments.handleParallelAssociatedSegmentFromNarys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetAfterAssociated
import ui.resetStavu
import utils.allocIndex
import utils.getLogicalCursor

fun handleAssociatedSegmentFromNarys(
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
    when (state.constructionModifier) {
        (ConstructionModifier.ORTHOGONAL) -> {
            handleOrthogonalAssociatedSegmentFromNarys(cursor, snappedPointLogical, canvasOffset, scale, state, change)
        }

        (ConstructionModifier.PARALLEL) -> {
            handleParallelAssociatedSegmentFromNarys(cursor, snappedPointLogical, canvasOffset, scale, state, change)
        }

        else -> {

            when (state.projectionPhase) {

                // 1. Klik na A₂
                "narys_start" -> {
                    state.pendingXA = logical.x
                    state.pendingZA = -logical.y
                    setProjectionPhase("narys_segment_associated_B_narys_start", state)
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println("🟦 A₂ uloženo: x=${state.pendingXA}, z=${state.pendingZA}")
                }

                // 2. Klik na B₂
                "narys_segment_associated_B_narys_start" -> {
                    state.pendingXB = logical.x
                    state.pendingZB = -logical.y
                    setProjectionPhase("pudorys_segment_associated_A_narys_start", state)
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    state.pendingMongeModeChange = DrawingModeMonge.PUDORYS
                    println("🟦 B₂ uloženo: x=${state.pendingXB}, z=${state.pendingZB}")
                }

                // 3. Klik na A₁ (s fixním x)
                "pudorys_segment_associated_A_narys_start" -> {
                    state.pendingXA ?: return
                    val y = logical.y
                    state.pendingYA = y
                    setProjectionPhase("pudorys_segment_associated_B_narys_start", state)
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println("🟥 A₁ uloženo: y=${y}")
                }

                // 4. Klik na B₁ (s fixním x) – finální sestavení
                "pudorys_segment_associated_B_narys_start" -> {
                    val xA = state.pendingXA ?: return
                    val yA = state.pendingYA ?: return
                    val zA = state.pendingZA ?: return

                    val xB = state.pendingXB ?: return
                    val yB = logical.y
                    val zB = state.pendingZB ?: return

                    val A = Point3D(xA, yA, zA, name = "", creationIndex = allocIndex(state))
                    val B = Point3D(xB, yB, zB, name = "", creationIndex = allocIndex(state))
                    val style = state.currentLineStyleSettings
                    val segment = Segment3D(
                        A,
                        B,
                        color = style.color,
                        strokeWidth = style.strokeWidth,
                        lineStyle = style.style,
                        name = "",
                        creationIndex = allocIndex(state)
                    )

                    // Vytvoř body bez parentSegmentu
                    val p1 = Point3DPudorys(
                        xA,
                        yA,
                        name = "",
                        parent = A,
                        isSegmentEndpoint = true,
                        creationIndex = allocIndex(state)
                    )
                    val p2 = Point3DPudorys(
                        xB,
                        yB,
                        name = "",
                        parent = B,
                        isSegmentEndpoint = true,
                        creationIndex = allocIndex(state)
                    )
                    val n1 = Point3DNarys(
                        xA,
                        zA,
                        name = "",
                        parent = A,
                        isSegmentEndpoint = true,
                        creationIndex = allocIndex(state)
                    )
                    val n2 = Point3DNarys(
                        xB,
                        zB,
                        name = "",
                        parent = B,
                        isSegmentEndpoint = true,
                        creationIndex = allocIndex(state)
                    )

                    // Přidání segmentů 2D
                    val segmentPudorys = Segment2DPudorys(
                        p1,
                        p2,
                        parent = segment,
                        creationIndex = allocIndex(state),
                        parentId = segment.id
                    )
                    val segmentNarys = Segment2DNarys(
                        n1,
                        n2,
                        parent = segment,
                        creationIndex = allocIndex(state),
                        parentId = segment.id
                    )

                    if (state.reusingExistingProjection){

                        // 1️⃣ původní vybraný pudorysový průmět
                        val oldNar  = state.pendingSegmentNarys.first() as Segment2DNarys

                        // 2️⃣ společný 3D parent
                        val parentSeg = oldNar.parent ?: segment    // `segment` ses právě vytvořil
                        if (oldNar.parent == null) {
                            oldNar.parent = parentSeg
                            oldNar.parentId = parentSeg.id
                            oldNar.start.parentSegment = oldNar
                            oldNar.end.parentSegment   = oldNar
                            parentSeg.color = oldNar.localColor?: oldNar.color
                            parentSeg.strokeWidth = oldNar.localStrokeWidth?: oldNar.strokeWidth
                            parentSeg.name = oldNar.name?: ""
                            parentSeg.lineStyle = oldNar.localLineStyle
                            addSegment3DAndDetectSolids(state, parentSeg)

                        }

                        // 3️⃣ napoj novou nárysovou projekci
                        segmentPudorys.parent = parentSeg
                        segmentPudorys.parentId = parentSeg.id
                        p1.parentSegment = segmentPudorys
                        p2.parentSegment = segmentPudorys

                        state.segmentsPudorys += segmentPudorys
                        state.pointsPudorys   += p1
                        state.pointsPudorys  += p2

                        println("✅ Sdružená úsečka dokončena – připojeno k existujícímu průmětu.")

                    } else {
                        // Nastavení parentSegment pro body
                        p1.parentSegment = segmentPudorys
                        p2.parentSegment = segmentPudorys
                        n1.parentSegment = segmentNarys
                        n2.parentSegment = segmentNarys

                        // Přidání bodů a segmentů
                        state.pointsPudorys.add(p1)
                        state.pointsPudorys.add(p2)
                        state.pointsNarys.add(n1)
                        state.pointsNarys.add(n2)
                        state.sharedPoints3D.add(A)
                        state.sharedPoints3D.add(B)

                        state.segmentsPudorys.add(segmentPudorys)
                        state.segmentsNarys.add(segmentNarys)
                        addSegment3DAndDetectSolids(state, segment)

                        println("✅ Sdružená 3D úsečka vytvořena: $segment")
                        println("✅ Sdružené 3D body vytvořeny : $A a $B")
                    }
                    commitSnapshot(state)
                    // Reset
                    state.pendingXA = null
                    state.pendingYA = null
                    state.pendingZA = null
                    state.pendingXB = null
                    state.pendingYB = null
                    state.pendingZB = null
                    repeatCons(state)
                    setProjectionPhase("narys_start", state)
                    state.mongeMode = DrawingModeMonge.NARYS
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    resetStavu(state)
                    resetAfterAssociated(state)
                }
            }
        }
    }
}
