package monge.input.intersections

import model.classes.Line3D
import model.classes.Plane3D

/**
 * Operand nástroje PRŮNIK. Web samotné průniky nepočítá (vyřazená featura),
 * ale typ zůstává, protože na něm visí kolekce v MongeState.
 */
enum class IntersectionKind { LINE, PLANE }

sealed class IntersectionOperand {
    abstract val id: String
    abstract val kind: IntersectionKind
    abstract val label: String

    data class LineOp(val line: Line3D) : IntersectionOperand() {
        override val id get() = line.id
        override val kind get() = IntersectionKind.LINE
        override val label get() = line.name.ifBlank { "přímka" }
    }

    data class PlaneOp(val plane: Plane3D) : IntersectionOperand() {
        override val id get() = plane.id
        override val kind get() = IntersectionKind.PLANE
        override val label get() = plane.name
    }
}
