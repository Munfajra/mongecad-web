package monge.input.ConicArcs.associated

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.conicarcs.projectToHyperbola
import draw.mongescreen.conicarcs.projectToParabola
import geometry.buildHyperbolaBasis
import model.Offset3D
import model.classes.ConicSection3D
import state.MongeState
import utils.dot
import kotlin.math.abs
import kotlin.math.max

/*
 * Přepočet 2D konců obloukových řezů ze 3D konců.
 *
 * Na desktopu je tenhle kód v monge/input/axo/ConicArcsAxo.kt, protože vznikl
 * kvůli převodu Monge -> AXO. Název "ForAxo" ale klame: funkce dopočítávají
 * konce ve VŠECH existujících průmětech, takže je potřebuje i ořez kuželoseček
 * v Mongeově promítání (monge/input/intersections/ops/ConicClipping.kt).
 * Web proto přebírá jen tuhle část, bez axonometrických vstupních bodů.
 */
data class LiftedConicArcPoint(
    val p3: Offset3D,
    val pudorys: Offset,
    val narys: Offset,
    val bokorys: Offset,
    val axo: Offset
)


private fun parabolaPointInProjection(
    state: MongeState,
    conicId: String,
    projected: Offset,
    prefix: String
): Offset {
    val inputs = when (prefix) {
        "pudorys" -> state.conicInputPointsPudorys[conicId]
        "narys" -> state.conicInputPointsNarys[conicId]
        "bokorys" -> state.conicInputPointsBokorys[conicId]
        "axo" -> state.conicInputPointsAxo[conicId]
        else -> null
    } ?: return projected
    if (inputs.third != Offset.Unspecified) return projected
    parabolaProjectionDegeneracy(state, conicId, prefix, inputs)?.let { deg ->
        return projectOnDegenerateParabolaCarrier(deg.origin, deg.dir, deg.isLine, projected)
    }
    return projectToParabola(inputs.first, inputs.second, projected)
}
private data class ParabolaProjectionDegeneracy(
    val origin: Offset,
    val dir: Offset,
    val isLine: Boolean
)

private fun parabolaProjectionDegeneracy(
    state: MongeState,
    conicId: String,
    prefix: String,
    inputs: Triple<Offset, Offset, Offset>
): ParabolaProjectionDegeneracy? {
    return when (prefix) {
        "pudorys" -> state.conicsPudorys.firstOrNull { it.id == conicId }?.takeIf { it.isDegenerate }?.let {
            ParabolaProjectionDegeneracy(inputs.first, it.degenerateDir ?: (inputs.second - inputs.first), it.isLineDegenerate)
        }
        "narys" -> state.conicsNarys.firstOrNull { it.id == conicId }?.takeIf { it.isDegenerate }?.let {
            ParabolaProjectionDegeneracy(inputs.first, it.degenerateDir ?: (inputs.second - inputs.first), it.isLineDegenerate)
        }
        "bokorys" -> state.conicsBokorys.firstOrNull { it.id == conicId }?.takeIf { it.isDegenerate }?.let {
            ParabolaProjectionDegeneracy(inputs.first, it.degenerateDir ?: (inputs.second - inputs.first), it.isLineDegenerate)
        }
        "axo" -> state.conicsAxo.firstOrNull { it.id == conicId }?.takeIf { it.isDegenerate }?.let {
            ParabolaProjectionDegeneracy(inputs.first, it.degenerateDir ?: (inputs.second - inputs.first), it.isLineDegenerate)
        }
        else -> null
    }
}

private fun projectOnDegenerateParabolaCarrier(
    origin: Offset,
    dirIn: Offset,
    isLine: Boolean,
    point: Offset
): Offset {
    val len = dirIn.getDistance()
    val dir = if (len < 1e-6f) Offset(1f, 0f) else dirIn / len
    fun projectWith(d: Offset): Offset {
        val t = (point.x - origin.x) * d.x + (point.y - origin.y) * d.y
        val tc = if (isLine) t else max(0f, t)
        return origin + d * tc
    }
    if (isLine) return projectWith(dir)

    val p1 = projectWith(dir)
    val p2 = projectWith(Offset(-dir.x, -dir.y))
    return if ((point - p1).getDistanceSquared() <= (point - p2).getDistanceSquared()) p1 else p2
}

private fun hyperbolaPointInProjection(
    state: MongeState,
    conicId: String,
    projected: Offset,
    prefix: String,
    sx: Int? = null
): Pair<Offset, Int> {
    val input = when (prefix) {
        "pudorys" -> state.hyperbolaInputsPudorys[conicId]
        "narys" -> state.hyperbolaInputsNarys[conicId]
        "bokorys" -> state.hyperbolaInputsBokorys[conicId]
        "axo" -> state.hyperbolaInputsAxo[conicId]
        else -> null
    } ?: return projected to (sx ?: +1)
    val basis = buildHyperbolaBasis(input) ?: return projected to (sx ?: +1)
    val branch = sx ?: if ((projected - basis.center).dot(basis.ex) >= 0f) +1 else -1
    return (projectToHyperbola(basis, projected, branch)?.on ?: projected) to branch
}

private fun conicIdsByParent(state: MongeState, parentId: String): Map<String, String?> = mapOf(
    "pudorys" to state.findPudorysConicIdByParent(parentId),
    "narys" to state.findNarysConicIdByParent(parentId),
    "bokorys" to state.findBokorysConicIdByParent(parentId),
    "axo" to state.findAxoConicIdByParent(parentId)
)

private fun projectedPoint(lifted: LiftedConicArcPoint, prefix: String): Offset =
    when (prefix) {
        "pudorys" -> lifted.pudorys
        "narys" -> lifted.narys
        "bokorys" -> lifted.bokorys
        "axo" -> lifted.axo
        else -> Offset.Unspecified
    }

private fun storeParabolaAssociatedAxo(
    state: MongeState,
    parent: ConicSection3D,
    a: LiftedConicArcPoint,
    b: LiftedConicArcPoint
) {
    val t1 = geometry.conics.parabolaParamTForPoint3D(parent, a.p3)
    val t2 = geometry.conics.parabolaParamTForPoint3D(parent, b.p3)
    conicIdsByParent(state, parent.id).forEach { (prefix, id) ->
        if (id != null) {
            val degenerateEnds = degenerateParabolaArcRangeInProjection(state, parent, id, prefix, t1, t2)
            val a2 = degenerateEnds?.first ?: parabolaPointInProjection(state, id, projectedPoint(a, prefix), prefix)
            val b2 = degenerateEnds?.second ?: parabolaPointInProjection(state, id, projectedPoint(b, prefix), prefix)
            state.parabolaArcEnds[id] = a2 to b2
        }
    }
    state.parabolaArcParams3D[parent.id] = t1 to t2
    state.parabolaArcEnds3D[parent.id] = a.p3 to b.p3
}

private fun degenerateParabolaArcRangeInProjection(
    state: MongeState,
    parent: ConicSection3D,
    conicId: String,
    prefix: String,
    t1: Float,
    t2: Float
): Pair<Offset, Offset>? {
    val inputs = when (prefix) {
        "pudorys" -> state.conicInputPointsPudorys[conicId]
        "narys" -> state.conicInputPointsNarys[conicId]
        "bokorys" -> state.conicInputPointsBokorys[conicId]
        "axo" -> state.conicInputPointsAxo[conicId]
        else -> null
    } ?: return null
    if (inputs.third != Offset.Unspecified) return null
    val deg = parabolaProjectionDegeneracy(state, conicId, prefix, inputs) ?: return null
    val len = deg.dir.getDistance()
    val dir = if (len < 1e-6f) Offset(1f, 0f) else deg.dir / len

    val points = geometry.conics.sampleParametricParabolaArc(parent, t1, t2, stepsHint = 360)
        .mapNotNull { project3DToParabolaPrefix(state, it, prefix) }
        .toMutableList()
    if (t1 <= 0f && t2 >= 0f || t2 <= 0f && t1 >= 0f) {
        geometry.conics.sampleParametricParabolaArc(parent, 0f, 0f, stepsHint = 1)
            .firstOrNull()
            ?.let { project3DToParabolaPrefix(state, it, prefix) }
            ?.let { points += it }
    }
    degenerateParabolaCarrierExtremeT(state, parent, prefix, deg.origin, dir, t1, t2)
        ?.let { tExtreme ->
            geometry.conics.sampleParametricParabolaArc(parent, tExtreme, tExtreme, stepsHint = 1)
                .firstOrNull()
                ?.let { project3DToParabolaPrefix(state, it, prefix) }
                ?.let { points += it }
        }
    if (points.isEmpty()) return null

    val scalars = points.map { p ->
        val raw = ((p.x - deg.origin.x) * dir.x + (p.y - deg.origin.y) * dir.y)
        if (deg.isLine) raw else max(0f, raw)
    }
    val sMin = scalars.min()
    val sMax = scalars.max()
    return deg.origin + dir * sMin to deg.origin + dir * sMax
}

private fun degenerateParabolaCarrierExtremeT(
    state: MongeState,
    parent: ConicSection3D,
    prefix: String,
    origin: Offset,
    dir: Offset,
    t1: Float,
    t2: Float
): Float? {
    fun scalarAt(t: Float): Float? {
        val point3D = geometry.conics.sampleParametricParabolaArc(parent, t, t, stepsHint = 1).firstOrNull()
            ?: return null
        val point2D = project3DToParabolaPrefix(state, point3D, prefix) ?: return null
        return (point2D.x - origin.x) * dir.x + (point2D.y - origin.y) * dir.y
    }

    val s0 = scalarAt(0f) ?: return null
    val sp = scalarAt(1f) ?: return null
    val sm = scalarAt(-1f) ?: return null
    val a = (sp + sm - 2f * s0) * 0.5f
    val b = (sp - sm) * 0.5f
    if (abs(a) < 1e-6f) return null
    val t = -b / (2f * a)
    val lo = minOf(t1, t2)
    val hi = maxOf(t1, t2)
    return if (t.isFinite() && t >= lo - 1e-4f && t <= hi + 1e-4f) t else null
}

private fun project3DToParabolaPrefix(state: MongeState, point: Offset3D, prefix: String): Offset? =
    when (prefix) {
        "pudorys" -> Offset(point.x, point.y)
        "narys" -> Offset(point.x, point.z)
        "bokorys" -> Offset(point.y, point.z)
        // web axonometrii nekreslí – axo průmět se nikdy nepoužije
        "axo" -> null
        else -> null
    }

/**
 * Znovu sestaví 2D větve oblouků hyperboly pro všechny průměty z 3D koncových bodů
 * uložených v [MongeState.hyperbolaArcEnds3D] – stejným mechanismem jako přímá AXO
 * konstrukce ([storeHyperbolaAssociatedAxoBranch] + [hyperbolaPointInProjection]).
 * Používá se při převodu Monge -> AXO, aby oblouky seděly i v nově vzniklých
 * (bokorys/axo) a přepočítaných (půdorys/nárys) průmětech. Předpokládá, že vstupy
 * hyperboly (hyperbolaInputs*) jsou už přepočítané.
 */

fun rebuildHyperbolaArcBranchesForAxo(state: MongeState, parent: ConicSection3D) {
    val ends = state.hyperbolaArcEnds3D[parent.id] ?: return
    val basis = state.basis
    fun lifted(p: Offset3D) = LiftedConicArcPoint(
        p3 = p,
        pudorys = Offset(p.x, p.y),
        narys = Offset(p.x, p.z),
        bokorys = Offset(p.y, p.z),
        axo = Offset.Unspecified // web axonometrii nekreslí
    )
    ends.first?.let { (a, b) -> storeHyperbolaAssociatedAxoBranch(state, parent, lifted(a), lifted(b), 1) }
    ends.second?.let { (a, b) -> storeHyperbolaAssociatedAxoBranch(state, parent, lifted(a), lifted(b), 2) }
}

/** Lifted bod ze 3D do všech průmětů (narys (x,z) v koeficientové konvenci, axo dle báze). */
private fun liftedConicArcPoint(state: MongeState, p: Offset3D): LiftedConicArcPoint =
    LiftedConicArcPoint(
        p3 = p,
        pudorys = Offset(p.x, p.y),
        narys = Offset(p.x, p.z),
        bokorys = Offset(p.y, p.z),
        axo = Offset.Unspecified // web axonometrii nekreslí
    )

/**
 * Znovu sestaví 2D konce parabolického oblouku pro všechny EXISTUJÍCÍ průměty z 3D
 * konců ([MongeState.parabolaArcEnds3D]) – snap na vstupy každého průmětu. Používá se
 * při tvorbě (Monge) i při převodu Monge -> AXO. Předpokládá hotové vstupy parabol.
 */
fun rebuildParabolaArcForAxo(state: MongeState, parent: ConicSection3D) {
    val ends = state.parabolaArcEnds3D[parent.id] ?: return
    storeParabolaAssociatedAxo(state, parent, liftedConicArcPoint(state, ends.first), liftedConicArcPoint(state, ends.second))
}

private fun storeHyperbolaAssociatedAxoBranch(
    state: MongeState,
    parent: ConicSection3D,
    a: LiftedConicArcPoint,
    b: LiftedConicArcPoint,
    branchIndex: Int
) {
    conicIdsByParent(state, parent.id).forEach { (prefix, id) ->
        if (id != null) {
            val (a2, sx) = hyperbolaPointInProjection(state, id, projectedPoint(a, prefix), prefix)
            val (b2, _) = hyperbolaPointInProjection(state, id, projectedPoint(b, prefix), prefix, sx)
            if (branchIndex == 1) state.hyperbolaArcBranch1[id] = a2 to b2
            else state.hyperbolaArcBranch2[id] = a2 to b2
            if (prefix == "narys") {
                val nConic = state.conicsNarys.firstOrNull { it.id == id }
                println("🔎 HYPARC narys br=$branchIndex id=$id " +
                    "in=${projectedPoint(a, prefix)}->${projectedPoint(b, prefix)} " +
                    "snap=$a2->$b2 sx=$sx " +
                    "hasInput=${state.hyperbolaInputsNarys.containsKey(id)} " +
                    "isDegenerate=${nConic?.isDegenerate} " +
                    "p3=${a.p3}->${b.p3}")
            }
        }
    }

    val (t1, t2) = computeHyperbolaParams(parent, a.p3, b.p3)
    val (oldParams1, oldParams2) = state.hyperbolaArcParams3D[parent.id] ?: (null to null)
    val (oldEnds1, oldEnds2) = state.hyperbolaArcEnds3D[parent.id] ?: (null to null)
    if (branchIndex == 1) {
        state.hyperbolaArcParams3D[parent.id] = (t1 to t2) to oldParams2
        state.hyperbolaArcEnds3D[parent.id] = (a.p3 to b.p3) to oldEnds2
    } else {
        state.hyperbolaArcParams3D[parent.id] = oldParams1 to (t1 to t2)
        state.hyperbolaArcEnds3D[parent.id] = oldEnds1 to (a.p3 to b.p3)
    }
}
