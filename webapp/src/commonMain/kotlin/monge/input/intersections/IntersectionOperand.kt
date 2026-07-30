package monge.input.intersections
import utils.putIfAbsent

import model.SolidOfRevolutionNarys
import model.SolidOfRevolutionPudorys
import model.classes.ConicalSurface3D
import model.classes.CylindricalSurface3D
import model.classes.Line3D
import model.classes.Line3DProjectionBokorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Plane3D
import model.classes.SegmentSolid3D
import model.classes.SphereSurface3D
import model.classes.RuledSurface3D
import state.MongeState

/**
 * Druhy objektů, mezi nimiž umíme počítat průnik.
 * Pořadí v enumu slouží k normalizaci neuspořádané dvojice (viz [dispatchIntersection]).
 */
enum class IntersectionKind { LINE, PLANE, CONE, CYLINDER, SPHERE, SOLID_OF_REVOLUTION, SEGMENT_SOLID, RULED_SURFACE }

/**
 * Sjednocený obal nad 3D objektem vybraným pro průnik.
 * Drží konkrétní typ tělesa, takže specializované funkce dostanou rovnou typovaný objekt.
 */
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

    data class ConeOp(val cone: ConicalSurface3D) : IntersectionOperand() {
        override val id get() = cone.id
        override val kind get() = IntersectionKind.CONE
        override val label get() = cone.name
    }

    data class CylinderOp(val cylinder: CylindricalSurface3D) : IntersectionOperand() {
        override val id get() = cylinder.id
        override val kind get() = IntersectionKind.CYLINDER
        override val label get() = cylinder.name
    }

    data class SphereOp(val sphere: SphereSurface3D) : IntersectionOperand() {
        override val id get() = sphere.id
        override val kind get() = IntersectionKind.SPHERE
        override val label get() = sphere.name
    }

    /**
     * Rotační plocha. Je vedená jediným [id] ([MongeState.selectedSolidOfRevolutionId]),
     * ale existuje ve dvou nezávislých variantách podle průmětny, ve které vznikla –
     * proto držíme obě (jedna je typicky null).
     */
    data class SolidOfRevolutionOp(
        override val id: String,
        val sorName: String,
        val narys: SolidOfRevolutionNarys?,
        val pudorys: SolidOfRevolutionPudorys?,
    ) : IntersectionOperand() {
        override val kind get() = IntersectionKind.SOLID_OF_REVOLUTION
        override val label get() = sorName.ifBlank { "rotační plocha" }
    }

    /** Těleso z úseček – hranol (HRANOL) nebo jehlan (JEHLAN). Stěny rekonstruujeme topologicky. */
    data class SegmentSolidOp(val solid: SegmentSolid3D) : IntersectionOperand() {
        override val id get() = solid.id
        override val kind get() = IntersectionKind.SEGMENT_SOLID
        override val label get() = solid.name.ifBlank {
            when (solid.type) {
                model.classes.SegmentSolidType.HRANOL -> "hranol"
                model.classes.SegmentSolidType.JEHLAN -> "jehlan"
                model.classes.SegmentSolidType.MNOHOSTEN -> "mnohostěn"
            }
        }
    }

    /** Obecná přímková plocha nebo konoid, numericky reprezentovaný rodinou tvořic. */
    data class RuledSurfaceOp(val surface: RuledSurface3D) : IntersectionOperand() {
        override val id get() = surface.id
        override val kind get() = IntersectionKind.RULED_SURFACE
        override val label get() = surface.name.ifBlank { "přímková plocha" }
    }
}

/**
 * Posbírá aktuálně vybrané objekty (napříč všemi projekcemi) jako [IntersectionOperand].
 * Přímky se řeší přes rodičovský [Line3D] projekčních výběrů, plochy přímo z 3D kolekcí.
 * Deduplikace probíhá podle 3D id, pořadí vkládání je zachováno.
 */
fun gatherSelectedOperands(state: MongeState): List<IntersectionOperand> {
    val out = LinkedHashMap<String, IntersectionOperand>()

    fun addLine(line: Line3D?) {
        if (line != null) out.putIfAbsent(line.id, IntersectionOperand.LineOp(line))
    }

    fun parentById(parentId: String?): Line3D? =
        parentId?.let { pid -> state.lines3D.firstOrNull { it.id == pid } }

    // Některé přímky (osy x/y/z/x₁₂, stopy z planeliftu) mají nastavenou jen
    // přímou referenci `parent`, ale ne `parentId`. Proto u projekcí zkoušíme
    // nejdřív konkrétní `parent` a teprve pak dohledání podle id v lines3D –
    // jinak by takové přímky do průniku tiše nespadly, i když mají 3D parent.
    // přímky – přímý výběr 3D i přes projekce (půdorys / nárys / bokorys / axo)
    state.selectedLines3D.forEach { addLine(it) }
    state.selectedLinesPudorys.forEach { addLine((it as? Line3DProjectionPudorys)?.parent ?: parentById(it.parentId)) }
    state.selectedLinesNarys.forEach { addLine((it as? Line3DProjectionNarys)?.parent ?: parentById(it.parentId)) }
    state.selectedLinesBokorys.forEach { addLine((it as? Line3DProjectionBokorys)?.parent ?: parentById(it.parentId)) }
    state.selectedLinesAxo.forEach { addLine(it.parent ?: parentById(it.parentId)) }

    // plochy / tělesa
    state.selectedPlanes.forEach { out.putIfAbsent(it.id, IntersectionOperand.PlaneOp(it)) }
    state.selectedCone.forEach { out.putIfAbsent(it.id, IntersectionOperand.ConeOp(it)) }
    state.selectedCylinder.forEach { out.putIfAbsent(it.id, IntersectionOperand.CylinderOp(it)) }
    state.selectedSpheres3D.forEach { out.putIfAbsent(it.id, IntersectionOperand.SphereOp(it)) }
    state.selectedRuledSurfaceId?.let { id ->
        state.ruledSurfaces.firstOrNull { it.id == id }?.let { surface ->
            out.putIfAbsent(id, IntersectionOperand.RuledSurfaceOp(surface))
        }
    }

    // tělesa z úseček (hranol/jehlan) – přímý výběr 3D
    state.selectedSegmentSolids3D.forEach { out.putIfAbsent(it.id, IntersectionOperand.SegmentSolidOp(it)) }

    // rotační plocha – jediné vybrané id, dohledej v obou variantách
    state.selectedSolidOfRevolutionId?.let { sid ->
        val nar = state.solidsOfRevolutionNarys.firstOrNull { it.id == sid }
        val pud = state.solidsOfRevolutionPudorys.firstOrNull { it.id == sid }
        if (nar != null || pud != null) {
            val nm = nar?.name ?: pud?.name ?: "p"
            out.putIfAbsent(sid, IntersectionOperand.SolidOfRevolutionOp(sid, nm, nar, pud))
        }
    }

    return out.values.toList()
}
