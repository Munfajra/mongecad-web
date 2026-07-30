package monge.input.intersections

import model.classes.IntersectionGroup
import model.classes.IntersectionPartKind
import model.classes.IntersectionPartRef
import serialization.commitSnapshot
import monge.input.intersections.ops.*
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import utils.allocIndex

/**
 * Rozhodne, kterou specializovanou funkci pro průnik zavolat na základě dvojice operandů.
 * Dvojice je **neuspořádaná** – nejprve ji normalizujeme podle [IntersectionKind] (pořadí
 * v enumu), takže pro každou kombinaci stačí jedna větev a jedna funkce.
 */
fun dispatchIntersection(x: IntersectionOperand, y: IntersectionOperand, state: MongeState) {
    // kanonické pořadí: menší kind první (LINE < PLANE < CONE < CYLINDER < SPHERE)
    val (a, b) = if (x.kind.ordinal <= y.kind.ordinal) x to y else y to x
    val before = captureIntersectionResultIds(state)

    // Žádný pre-commit: všechny konstrukce commitují AŽ PO přidání objektu, takže
    // stav před průnikem už v historii je. Průnik je jeden atomický undo krok –
    // stačí jediný commit na konci (jinak by mezi posledním objektem a průnikem
    // vznikl krok navíc).

    when {
        a is IntersectionOperand.LineOp && b is IntersectionOperand.LineOp ->
            intersectLineLine(a.line, b.line, state)
        a is IntersectionOperand.LineOp && b is IntersectionOperand.PlaneOp ->
            intersectLinePlane(a.line, b.plane, state)
        a is IntersectionOperand.LineOp && b is IntersectionOperand.ConeOp ->
            intersectLineCone(a.line, b.cone, state)
        a is IntersectionOperand.LineOp && b is IntersectionOperand.CylinderOp ->
            intersectLineCylinder(a.line, b.cylinder, state)
        a is IntersectionOperand.LineOp && b is IntersectionOperand.SphereOp ->
            intersectLineSphere(a.line, b.sphere, state)

        a is IntersectionOperand.PlaneOp && b is IntersectionOperand.PlaneOp ->
            intersectPlanePlane(a.plane, b.plane, state)
        a is IntersectionOperand.PlaneOp && b is IntersectionOperand.ConeOp ->
            intersectPlaneCone(a.plane, b.cone, state)
        a is IntersectionOperand.PlaneOp && b is IntersectionOperand.CylinderOp ->
            intersectPlaneCylinder(a.plane, b.cylinder, state)
        a is IntersectionOperand.PlaneOp && b is IntersectionOperand.SphereOp ->
            intersectPlaneSphere(a.plane, b.sphere, state)

        a is IntersectionOperand.ConeOp && b is IntersectionOperand.ConeOp ->
            intersectConeCone(a.cone, b.cone, state)
        a is IntersectionOperand.ConeOp && b is IntersectionOperand.CylinderOp ->
            intersectConeCylinder(a.cone, b.cylinder, state)
        a is IntersectionOperand.ConeOp && b is IntersectionOperand.SphereOp ->
            intersectConeSphere(a.cone, b.sphere, state)

        a is IntersectionOperand.CylinderOp && b is IntersectionOperand.CylinderOp ->
            intersectCylinderCylinder(a.cylinder, b.cylinder, state)
        a is IntersectionOperand.CylinderOp && b is IntersectionOperand.SphereOp ->
            intersectCylinderSphere(a.cylinder, b.sphere, state)

        a is IntersectionOperand.SphereOp && b is IntersectionOperand.SphereOp ->
            intersectSphereSphere(a.sphere, b.sphere, state)

        a is IntersectionOperand.LineOp && b is IntersectionOperand.SolidOfRevolutionOp ->
            intersectLineSolidOfRevolution(a.line, b, state)
        a is IntersectionOperand.PlaneOp && b is IntersectionOperand.SolidOfRevolutionOp ->
            intersectPlaneSolidOfRevolution(a.plane, b, state)
        a is IntersectionOperand.ConeOp && b is IntersectionOperand.SolidOfRevolutionOp ->
            intersectConeSolidOfRevolution(a.cone, b, state)
        a is IntersectionOperand.CylinderOp && b is IntersectionOperand.SolidOfRevolutionOp ->
            intersectCylinderSolidOfRevolution(a.cylinder, b, state)
        a is IntersectionOperand.SphereOp && b is IntersectionOperand.SolidOfRevolutionOp ->
            intersectSphereSolidOfRevolution(a.sphere, b, state)
        a is IntersectionOperand.SolidOfRevolutionOp && b is IntersectionOperand.SolidOfRevolutionOp ->
            intersectSolidOfRevolutionSolidOfRevolution(a, b, state)

        a is IntersectionOperand.LineOp && b is IntersectionOperand.SegmentSolidOp ->
            intersectLineSegmentSolid(a.line, b.solid, state)
        a is IntersectionOperand.PlaneOp && b is IntersectionOperand.SegmentSolidOp ->
            intersectPlaneSegmentSolid(a.plane, b.solid, state)
        a is IntersectionOperand.ConeOp && b is IntersectionOperand.SegmentSolidOp ->
            intersectConeSegmentSolid(a.cone, b.solid, state)
        a is IntersectionOperand.CylinderOp && b is IntersectionOperand.SegmentSolidOp ->
            intersectCylinderSegmentSolid(a.cylinder, b.solid, state)
        a is IntersectionOperand.SphereOp && b is IntersectionOperand.SegmentSolidOp ->
            intersectSphereSegmentSolid(a.sphere, b.solid, state)
        a is IntersectionOperand.SolidOfRevolutionOp && b is IntersectionOperand.SegmentSolidOp ->
            intersectSolidOfRevolutionSegmentSolid(a, b.solid, state)

        a is IntersectionOperand.SegmentSolidOp && b is IntersectionOperand.SegmentSolidOp ->
            intersectSegmentSolidSegmentSolid(a.solid, b.solid, state)

        a is IntersectionOperand.LineOp && b is IntersectionOperand.RuledSurfaceOp ->
            intersectLineRuledSurface(a.line, b.surface, state)
        a is IntersectionOperand.PlaneOp && b is IntersectionOperand.RuledSurfaceOp ->
            intersectPlaneRuledSurface(a.plane, b.surface, state)
        a is IntersectionOperand.ConeOp && b is IntersectionOperand.RuledSurfaceOp ->
            intersectConeRuledSurface(a.cone, b.surface, state)
        a is IntersectionOperand.CylinderOp && b is IntersectionOperand.RuledSurfaceOp ->
            intersectCylinderRuledSurface(a.cylinder, b.surface, state)
        a is IntersectionOperand.SphereOp && b is IntersectionOperand.RuledSurfaceOp ->
            intersectSphereRuledSurface(a.sphere, b.surface, state)
        a is IntersectionOperand.SolidOfRevolutionOp && b is IntersectionOperand.RuledSurfaceOp ->
            intersectSolidOfRevolutionRuledSurface(a, b.surface, state)
        a is IntersectionOperand.SegmentSolidOp && b is IntersectionOperand.RuledSurfaceOp ->
            intersectSegmentSolidRuledSurface(a.solid, b.surface, state)
        a is IntersectionOperand.RuledSurfaceOp && b is IntersectionOperand.RuledSurfaceOp ->
            intersectRuledSurfaceRuledSurface(a.surface, b.surface, state)

        else -> {
            state.consInfo.value = "Průnik ${a.kind}–${b.kind} zatím není podporován"
            println("⚠️ Nepodporovaná kombinace průniku: ${a.kind} × ${b.kind}")
        }
    }

    val parts = newIntersectionParts(state, before)
    if (parts.isNotEmpty()) {
        val group = IntersectionGroup(
            operandAId = a.id,
            operandBId = b.id,
            operandALabel = a.label,
            operandBLabel = b.label,
            parts = parts,
            creationIndex = allocIndex(state)
        )
        state.intersectionGroups.add(group)
        state.selectedIntersectionGroupId = group.id
    }

    // Průnik je jeden atomický undo krok, i když vytvoří dvě tečny, dva body
    // nebo více větví bodově vzorkované křivky.
    commitSnapshot(state)
    repeatCons(state)
}

private data class IntersectionResultIds(
    val pointIds: Set<String>,
    val lineIds: Set<String>,
    val segmentIds: Set<String>,
    val conicIds: Set<String>,
    val curveIds: Set<String>,
)

private fun captureIntersectionResultIds(state: MongeState): IntersectionResultIds =
    IntersectionResultIds(
        pointIds = state.sharedPoints3D.mapTo(mutableSetOf()) { it.id },
        lineIds = state.lines3D.mapTo(mutableSetOf()) { it.id },
        segmentIds = state.segments3D.mapTo(mutableSetOf()) { it.id },
        conicIds = state.conics3D.mapTo(mutableSetOf()) { it.id },
        curveIds = state.curves3D.mapTo(mutableSetOf()) { it.id },
    )

private fun newIntersectionParts(
    state: MongeState,
    before: IntersectionResultIds,
): List<IntersectionPartRef> = buildList {
    state.sharedPoints3D
        .filter { it.id !in before.pointIds }
        .forEach { add(IntersectionPartRef(IntersectionPartKind.POINT3D, it.id)) }
    state.lines3D
        .filter { it.id !in before.lineIds }
        .forEach { add(IntersectionPartRef(IntersectionPartKind.LINE3D, it.id)) }
    state.segments3D
        .filter { it.id !in before.segmentIds }
        .forEach { add(IntersectionPartRef(IntersectionPartKind.SEGMENT3D, it.id)) }
    state.conics3D
        .filter { it.id !in before.conicIds }
        .forEach { add(IntersectionPartRef(IntersectionPartKind.CONIC3D, it.id)) }
    state.curves3D
        .filter { it.id !in before.curveIds }
        .forEach { add(IntersectionPartRef(IntersectionPartKind.CURVE3D, it.id)) }
}
