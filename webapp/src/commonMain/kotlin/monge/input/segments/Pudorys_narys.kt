package monge.input.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import serialization.commitSnapshot
import model.*
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import monge.input.segments.directionHandlers.segments.handleOrthogonalAssociatedSegmentFromPudorys
import monge.input.segments.directionHandlers.segments.handleParallelAssociatedSegmentFromPudorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetAfterAssociated
import ui.resetStavu
import utils.allocIndex
import utils.getLogicalCursor

fun handleAssociatedSegmentFromPudorys(
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
    when (state.constructionModifier){
    (ConstructionModifier.ORTHOGONAL) -> {
        handleOrthogonalAssociatedSegmentFromPudorys(cursor,snappedPointLogical,canvasOffset,scale,state,change)
    }
   (ConstructionModifier.PARALLEL) -> {
       handleParallelAssociatedSegmentFromPudorys(cursor, snappedPointLogical, canvasOffset, scale, state, change)
   }
        else -> {
    when (state.projectionPhase) {

        // 1. Klik na A₁
        "pudorys_start" -> {
            state.pendingXA = logical.x
            state.pendingYA = logical.y
            setProjectionPhase("pudorys_segment_associated_B_pudorys_start", state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟥 A₁ uloženo: x=${state.pendingXA}, y=${state.pendingYA}")
        }

        // 2. Klik na B₁
        "pudorys_segment_associated_B_pudorys_start" -> {
            state.pendingXB = logical.x
            state.pendingYB = logical.y
            setProjectionPhase("narys_segment_associated_A_pudorys_start", state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.pendingMongeModeChange = DrawingModeMonge.NARYS
            println("🟥 B₁ uloženo: x=${state.pendingXB}, y=${state.pendingYB}")
        }

        // 3. Klik na A₂ (s fixním x)
        "narys_segment_associated_A_pudorys_start" -> {
            state.pendingXA ?: return
            val z = -logical.y
            state.pendingZA = z
            setProjectionPhase("narys_segment_associated_B_pudorys_start", state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟦 A₂ uloženo: z=${z}")
        }

        // 4. Klik na B₂ (s fixním x) – dokončení
        "narys_segment_associated_B_pudorys_start" -> {
            val xA = state.pendingXA ?: return
            val yA = state.pendingYA ?: return
            val zA = state.pendingZA ?: return

            val xB = state.pendingXB ?: return
            val yB = state.pendingYB ?: return
            val zB = -logical.y

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
            val n1 =
                Point3DNarys(xA, zA, name = "", parent = A, isSegmentEndpoint = true, creationIndex = allocIndex(state))
            val n2 =
                Point3DNarys(xB, zB, name = "", parent = B, isSegmentEndpoint = true, creationIndex = allocIndex(state))

            val segPudorys = Segment2DPudorys(
                start = p1,
                end = p2,
                parent = segment,
                creationIndex = allocIndex(state),
                parentId = segment.id
            )
            val segNarys = Segment2DNarys(
                start = n1,
                end = n2,
                parent = segment,
                creationIndex = allocIndex(state),
                parentId = segment.id
            )
            if (state.reusingExistingProjection){

                // 1️⃣ původní vybraný pudorysový průmět
                val oldPud  = state.pendingSegmentPudorys.firstOrNull() as Segment2DPudorys

                // 2️⃣ společný 3D parent
                val parentSeg = oldPud.parent ?: segment    // `segment` ses právě vytvořil
                if (oldPud.parent == null) {
                    oldPud.parent = parentSeg
                    oldPud.parentId = parentSeg.id
                    oldPud.start.parentSegment = oldPud
                    oldPud.end.parentSegment   = oldPud
                    parentSeg.color = oldPud.localColor?: oldPud.color
                    parentSeg.strokeWidth = oldPud.localStrokeWidth?: oldPud.strokeWidth
                    parentSeg.name = oldPud.name?: ""
                    parentSeg.lineStyle = oldPud.lineStyle
                    segNarys.localLineStyle = oldPud.localLineStyle
                    parentSeg.color = oldPud.color
                    addSegment3DPlain(state, parentSeg)
                }

                // 3️⃣ napoj novou nárysovou projekci
                segNarys.parent = parentSeg
                segNarys.parentId = parentSeg.id
                n1.parentSegment = segNarys
                n2.parentSegment = segNarys

                state.segmentsNarys += segNarys
                state.pointsNarys   += n1
                state.pointsNarys   += n2

                println("✅ Sdružená úsečka dokončena – připojeno k existujícímu průmětu.")

            } else
            {
                // Propojení bodů zpět na jejich segmenty
                p1.parentSegment = segPudorys
                p2.parentSegment = segPudorys
                n1.parentSegment = segNarys
                n2.parentSegment = segNarys

                // Přidání bodů
                state.pointsPudorys.add(p1)
                state.pointsPudorys.add(p2)
                state.pointsNarys.add(n1)
                state.pointsNarys.add(n2)

                // Přidání 3D bodů
                state.sharedPoints3D.add(A)
                state.sharedPoints3D.add(B)

                // Přidání úseček
                state.segmentsPudorys.add(segPudorys)
                state.segmentsNarys.add(segNarys)
                addSegment3DPlain(state, segment)

                println("✅ Sdružená 3D úsečka vytvořena (z půdorysu): $segment")
                println("✅ Sdružené 3D body vytvořeny : $A a $B")
            }
            // Reset
            commitSnapshot(state)
            state.pendingXA = null
            state.pendingYA = null
            state.pendingZA = null
            state.pendingXB = null
            state.pendingYB = null
            state.pendingZB = null
            repeatCons(state)
            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            resetStavu(state)
            resetAfterAssociated(state)
        }
    }
    }
    }

}
private fun add3DSegmentFromTwo3DPoints(
    state: MongeState,
    a3: Point3D,
    b3: Point3D,
    color: Color = state.currentLineStyleSettings.color,
    width: Float = state.currentLineStyleSettings.strokeWidth
) {
    // degenerate
    val dx = b3.x - a3.x
    val dy = b3.y - a3.y
    val dz = b3.z - a3.z
    if (kotlin.math.abs(dx) < 1e-6f && kotlin.math.abs(dy) < 1e-6f && kotlin.math.abs(dz) < 1e-6f) return

    // 1) 3D segment
    val seg3D = Segment3D(
        name = "",
        start = Point3D(a3.x, a3.y, a3.z, "", creationIndex = allocIndex(state)),
        end = Point3D(b3.x, b3.y, b3.z, "", creationIndex = allocIndex(state)),
        color = color,
        strokeWidth = width, creationIndex = allocIndex(state)
    )

    // 2) Projections (Monge) – segment má 2 koncové body v každé projekci
    val segPud = Segment2DPudorys(
        parentId = seg3D.id,
        start = Point3DPudorys(a3.x, a3.y, creationIndex = allocIndex(state)),
        end = Point3DPudorys(b3.x, b3.y, creationIndex = allocIndex(state)),
        name = "",
        localColor = color,
        localStrokeWidth = width, creationIndex = allocIndex(state)
    )

    val segNar = Segment2DNarys(
        parentId = seg3D.id,
        start = Point3DNarys(a3.x, a3.z),
        end = Point3DNarys(b3.x, b3.z),
        name = "",
        localColor = color,
        localStrokeWidth = width, creationIndex = allocIndex(state)
    )

    // 3) Commit
    addSegment3DPlain(state, seg3D)
    state.segmentsPudorys.add(segPud)
    state.segmentsNarys.add(segNar)

    // 4) Wiring parent refs (pokud to tak děláš i u přímek)
    segPud.parent = seg3D
    segNar.parent = seg3D

    // 5) Naming flow (analogicky jako přímka)
    state.inputName = ""
    state.isNameConfirmed = false


    state.deferSelectionUntil = System.currentTimeMillis() + 100
    println("✅ Přidána 3D úsečka: ${seg3D.name}")
}

fun handleKotoSegmentThroughTwoParentedPointsClick(state: MongeState, clicked: Point3DPudorys) {
    val parentB = clicked.parent ?: return

    val aId = state.kotoSegPickAId
    if (aId == null) {
        state.kotoSegPickAId = clicked.id
        state.kotoSegPickBId = null
        return
    }

    if (clicked.id == aId) return

    val first = state.pointsPudorys.find { it.id == aId } ?: run {
        state.kotoSegPickAId = null
        state.kotoSegPickBId = null
        return
    }

    val parentA = first.parent ?: return



    add3DSegmentFromTwo3DPoints(
        state = state,
        a3 = parentA,
        b3 = parentB
    )
commitSnapshot(state)
    state.kotoSegPickAId = null
    state.kotoSegPickBId = null
    repeatCons(state)
}
