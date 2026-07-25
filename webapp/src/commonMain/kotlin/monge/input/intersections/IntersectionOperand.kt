package monge.input.intersections

import model.classes.Line3D
import model.classes.Line3DProjectionBokorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Plane3D
import state.MongeState

/**
 * Druhy objektů dostupné pro průnik ve webové verzi. Toolbar webu umožňuje
 * vytvářet celé 3D přímky a roviny.
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

/**
 * Posbírá právě vybrané 3D přímky a roviny. U přímek se výběr běžně děje přes
 * jejich půdorysný nebo nárysný průmět, proto se vrací jejich 3D parent.
 */
fun gatherSelectedOperands(state: MongeState): List<IntersectionOperand> {
    val result = LinkedHashMap<String, IntersectionOperand>()

    fun addLine(line: Line3D?) {
        if (line != null && line.id !in result) {
            result[line.id] = IntersectionOperand.LineOp(line)
        }
    }

    fun parentById(parentId: String?): Line3D? =
        parentId?.let { id -> state.lines3D.firstOrNull { it.id == id } }

    state.selectedLines3D.forEach(::addLine)
    state.selectedLinesPudorys.forEach {
        addLine((it as? Line3DProjectionPudorys)?.parent ?: parentById(it.parentId))
    }
    state.selectedLinesNarys.forEach {
        addLine((it as? Line3DProjectionNarys)?.parent ?: parentById(it.parentId))
    }
    state.selectedLinesBokorys.forEach {
        addLine((it as? Line3DProjectionBokorys)?.parent ?: parentById(it.parentId))
    }
    state.selectedLinesAxo.forEach {
        addLine(it.parent ?: parentById(it.parentId))
    }
    state.selectedPlanes.forEach {
        if (it.id !in result) {
            result[it.id] = IntersectionOperand.PlaneOp(it)
        }
    }

    return result.values.toList()
}
