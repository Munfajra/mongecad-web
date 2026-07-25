package monge.input.intersections

import model.classes.IntersectionGroup
import model.classes.IntersectionPartKind
import model.classes.IntersectionPartRef
import serialization.commitSnapshot
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import utils.allocIndex

/**
 * Vybere výpočet podle neuspořádané dvojice operandů a seskupí nově vzniklé
 * objekty do jedné položky ObjectListu.
 */
fun dispatchIntersection(
    first: IntersectionOperand,
    second: IntersectionOperand,
    state: MongeState,
) {
    val (a, b) =
        if (first.kind.ordinal <= second.kind.ordinal) first to second else second to first
    val pointIdsBefore = state.sharedPoints3D.mapTo(mutableSetOf()) { it.id }
    val lineIdsBefore = state.lines3D.mapTo(mutableSetOf()) { it.id }

    when {
        a is IntersectionOperand.LineOp && b is IntersectionOperand.LineOp ->
            intersectLineLine(a.line, b.line, state)
        a is IntersectionOperand.LineOp && b is IntersectionOperand.PlaneOp ->
            intersectLinePlane(a.line, b.plane, state)
        a is IntersectionOperand.PlaneOp && b is IntersectionOperand.PlaneOp ->
            intersectPlanePlane(a.plane, b.plane, state)
    }

    val parts = buildList {
        state.sharedPoints3D
            .filter { it.id !in pointIdsBefore }
            .forEach { add(IntersectionPartRef(IntersectionPartKind.POINT3D, it.id)) }
        state.lines3D
            .filter { it.id !in lineIdsBefore }
            .forEach { add(IntersectionPartRef(IntersectionPartKind.LINE3D, it.id)) }
    }
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

    commitSnapshot(state)
    repeatCons(state)
}
