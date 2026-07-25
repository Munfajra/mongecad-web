package monge.input.axo

import androidx.compose.ui.geometry.Offset
import model.Mongeobjects
import model.Offset3D
import model.axo.AxoMode
import monge.input.ConicArcs.associated.*
import monge.input.ConicArcs.finalizeConicVisibilityIfActive
import monge.input.ConicArcs.single.*
import monge.input.intersections.ops.setIntersectionEllipseArc
import monge.input.selection.setCircleArc
import model.classes.ConicSection3D
import model.classes.projectPoint3DToAxoLocal
import draw.mongescreen.previews.conicsarcs.hyperbolaBasisFrom
import draw.mongescreen.previews.conicsarcs.projectToHyperbola
import draw.mongescreen.previews.conicsarcs.projectToParabola
import serialization.commitSnapshot
import model.ArcMode
import monge.input.axo.points.screenToAxoOverlayLocal
import geometry.conics.ConicType
import geometry.conics.classifyConicFromMatrix
import state.MongeState
import state.snapMonge.computeIntersection
import ui.mongeui.toolbar.setProjectionPhase
import utils.dot
import utils.normalize
import kotlin.math.abs
import kotlin.math.max

private fun getLogicalAxo(state: MongeState, snapped: Offset?): Offset? {
    if (state.axoMode == AxoMode.NORMAL_2D) {
        val basis = state.basis ?: return null
        return snapped ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
    }
    return getLogicalCursorAxo(
        snapped = snapped,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = state.axoMode,
        axoModel = state.activeAxoModel
    )
}

fun handleConicArcAxo(state: MongeState, snappedPointLogical: Offset?) {
    val raw = getLogicalAxo(state, snappedPointLogical) ?: return
    val logical = if (state.axoMode == AxoMode.AXO_NARYS) Offset(raw.x, -raw.y) else raw

    when (state.axoMode) {
        AxoMode.AXO_PUDORYS -> {
            if (state.projectionPhase == "pudorys_start") decideConicPudorys(state)
            handleConicArcGeneric(state, logical,
                conics = state.conicsPudorys,
                circles = state.circlesPudorys,
                selectedConics = state.selectedConicsPudorys,
                selectedCircles = state.selectedCirclesPudorys,
                conicInputs = state.conicInputPointsPudorys,
                hyperbolaInputs = state.hyperbolaInputsPudorys,
                prefix = "pudorys"
            )
        }
        AxoMode.AXO_NARYS -> {
            if (state.projectionPhase == "pudorys_start") decideConicNarys(state)
            handleConicArcGeneric(state, logical,
                conics = state.conicsNarys,
                circles = state.circlesNarys,
                selectedConics = state.selectedConicsNarys,
                selectedCircles = state.selectedCirclesNarys,
                conicInputs = state.conicInputPointsNarys,
                hyperbolaInputs = state.hyperbolaInputsNarys,
                prefix = "narys"
            )
        }
        AxoMode.AXO_BOKORYS -> {
            if (state.projectionPhase == "pudorys_start") decideConicBokorys(state)
            handleConicArcGeneric(state, logical,
                conics = state.conicsBokorys,
                circles = state.circlesBokorys,
                selectedConics = state.selectedConicsBokorys,
                selectedCircles = state.selectedCirclesBokorys,
                conicInputs = state.conicInputPointsBokorys,
                hyperbolaInputs = state.hyperbolaInputsBokorys,
                prefix = "bokorys"
            )
        }
        AxoMode.NORMAL_2D -> {
            if (state.projectionPhase == "pudorys_start") decideConicAxoNative(state)
            handleConicArcGeneric(state, logical,
                conics = state.conicsAxo,
                circles = emptyList(),
                selectedConics = state.selectedConicsAxo,
                selectedCircles = emptyList(),
                conicInputs = state.conicInputPointsAxo,
                hyperbolaInputs = state.hyperbolaInputsAxo,
                prefix = "axo"
            )
        }
    }
}

fun handleConicArcAssociatedAxo(state: MongeState, snappedPointLogical: Offset?) {
    val raw = getLogicalAxo(state, snappedPointLogical) ?: return
    val logical = if (state.axoMode == AxoMode.AXO_NARYS) Offset(raw.x, -raw.y) else raw
    val dummyCursor = Offset.Zero

    when (state.axoMode) {
        AxoMode.AXO_PUDORYS -> {
            if (state.projectionPhase == "pudorys_start") decideConicPudorys(state)
            arcEllipsePudorys3D(state, logical, dummyCursor)
            arcParabolaPudorys3D(state, logical, dummyCursor)
            arcHyperbolaPudorys3D(state, logical, dummyCursor)
        }
        AxoMode.AXO_NARYS -> {
            if (state.projectionPhase == "pudorys_start") decideConicNarys(state)
            arcEllipseNarys3D(state, logical, dummyCursor)
            arcParabolaNarys3D(state, logical, dummyCursor)
            arcHyperbolaNarys3D(state, logical, dummyCursor)
        }
        AxoMode.AXO_BOKORYS -> {
            if (state.projectionPhase == "pudorys_start") decideConicBokorys(state)
            arcEllipseBokorys3DAxo(state, logical)
            arcParabolaBokorys3DAxo(state, logical)
            arcHyperbolaBokorys3DAxo(state, logical)
        }
        AxoMode.NORMAL_2D -> {
            if (state.projectionPhase == "pudorys_start") decideConicAxoNative(state)
            arcEllipseNative3DAxo(state, logical)
            arcParabolaNative3DAxo(state, logical)
            arcHyperbolaNative3DAxo(state, logical)
        }
    }
}

data class LiftedConicArcPoint(
    val p3: Offset3D,
    val pudorys: Offset,
    val narys: Offset,
    val bokorys: Offset,
    val axo: Offset
)

fun liftBokorysPointTo3DAndOthers(
    y: Float,
    z: Float,
    conic: ConicSection3D,
    basis: AxoRenderBasis?
): LiftedConicArcPoint? {
    val eq = planeEquation(conic)
    if (!eq.a.isFinite() || abs(eq.a) < 1e-5f) return null
    val x = -(eq.b * y + eq.c * z + eq.d) / eq.a
    val p3 = Offset3D(x, y, z)
    return LiftedConicArcPoint(
        p3 = p3,
        pudorys = Offset(x, y),
        narys = Offset(x, z),
        bokorys = Offset(y, z),
        axo = basis?.let { projectPoint3DToAxoLocal(p3, it) } ?: Offset.Unspecified
    )
}

fun liftAxoPointTo3DAndOthers(
    axoLocal: Offset,
    basis: AxoRenderBasis,
    conic: ConicSection3D
): LiftedConicArcPoint? {
    val eq = planeEquation(conic)
    val p3 = solve3x3(
        basis.ex.x, basis.ey.x, basis.ez.x, axoLocal.x,
        basis.ex.y, basis.ey.y, basis.ez.y, axoLocal.y,
        eq.a, eq.b, eq.c, -eq.d
    ) ?: return null
    return LiftedConicArcPoint(
        p3 = p3,
        pudorys = Offset(p3.x, p3.y),
        narys = Offset(p3.x, p3.z),
        bokorys = Offset(p3.y, p3.z),
        axo = projectPoint3DToAxoLocal(p3, basis)
    )
}

private fun solve3x3(
    a11: Float, a12: Float, a13: Float, b1: Float,
    a21: Float, a22: Float, a23: Float, b2: Float,
    a31: Float, a32: Float, a33: Float, b3: Float
): Offset3D? {
    fun det(
        m11: Float, m12: Float, m13: Float,
        m21: Float, m22: Float, m23: Float,
        m31: Float, m32: Float, m33: Float
    ): Float =
        m11 * (m22 * m33 - m23 * m32) -
                m12 * (m21 * m33 - m23 * m31) +
                m13 * (m21 * m32 - m22 * m31)

    val d = det(a11, a12, a13, a21, a22, a23, a31, a32, a33)
    if (!d.isFinite() || abs(d) < 1e-6f) return null
    val dx = det(b1, a12, a13, b2, a22, a23, b3, a32, a33)
    val dy = det(a11, b1, a13, a21, b2, a23, a31, b3, a33)
    val dz = det(a11, a12, b1, a21, a22, b2, a31, a32, b3)
    return Offset3D(dx / d, dy / d, dz / d)
}

private fun liftAxoSourcePoint(
    sourcePrefix: String,
    pointOnSourceConic: Offset,
    state: MongeState,
    parent: ConicSection3D
): LiftedConicArcPoint? {
    return when (sourcePrefix) {
        "bokorys" -> liftBokorysPointTo3DAndOthers(pointOnSourceConic.x, pointOnSourceConic.y, parent, state.basis)
        "axo" -> {
            val basis = state.basis ?: return null
            liftAxoPointTo3DAndOthers(pointOnSourceConic, basis, parent)
        }
        else -> null
    }
}

private fun cleanupAssociatedAxo(state: MongeState) {
    state.pendingArcA_3D = null
    state.pendingHypA1 = null
    state.pendingHypA2 = null
    state.pendingHypBranch1SX = null
    state.pendingHypBranch2SX = null
    state.activeConicIdForArc = null
    state.activeParentConic3DIdForEllipseArc = null
    state.activeArcMode = null
    state.drawobjects = Mongeobjects.NONE
    setProjectionPhase("pudorys_start", state)
    state.triggerRedraw++
}

private fun ellipsePointInProjection(
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
    if (inputs.third == Offset.Unspecified) return projected
    return projectToEllipseFromDiameters(inputs.first, inputs.second, inputs.third, projected)
}

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

private fun storeEllipseAssociatedAxo(
    state: MongeState,
    parent: ConicSection3D,
    a: LiftedConicArcPoint,
    b: LiftedConicArcPoint,
    mode: ArcMode,
    sourcePrefix: String,
) {
    val t1 = ellipseParamTForPoint3D(parent, a.p3)
    val t2 = ellipseParamTForPoint3D(parent, b.p3)
    val sourceId = conicIdsByParent(state, parent.id)[sourcePrefix]
    val sourceInputs = when (sourcePrefix) {
        "pudorys" -> sourceId?.let(state.conicInputPointsPudorys::get)
        "narys" -> sourceId?.let(state.conicInputPointsNarys::get)
        "bokorys" -> sourceId?.let(state.conicInputPointsBokorys::get)
        "axo" -> sourceId?.let(state.conicInputPointsAxo::get)
        else -> null
    }
    val middle3D = sourceInputs?.takeIf { it.third != Offset.Unspecified }?.let { inputs ->
        val middle2D = ellipseArcMidpointInProjection(
            inputs.first,
            inputs.second,
            inputs.third,
            projectedPoint(a, sourcePrefix),
            projectedPoint(b, sourcePrefix),
            mode,
        )
        liftAxoSourcePoint(sourcePrefix, middle2D, state, parent)?.p3
    }
    val (t1n, t2n) = middle3D?.let {
        normalizeArcThroughParameter(t1, t2, ellipseParamTForPoint3D(parent, it))
    } ?: normalizeArcByMode(t1, t2, mode)
    // Nastav oblouk ze 3D parametrů přes sdílený recept (řez tělesa / převod Monge↔AXO).
    // Ten promítá konce do každého průmětu se správnou konvencí (nárys flipuje z na −z)
    // a orientaci (CCW/CW) určuje per-průmět z vnitřního bodu oblouku. Tím se sjednotí
    // chování s INTERSECTION a opraví se chybně zrcadlený oblouk v degenerovaném nárysu.
    setIntersectionEllipseArc(state, parent, t1n, t2n)
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
        "axo" -> state.basis?.let { projectPoint3DToAxoLocal(point, it) }
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
        axo = basis?.let { projectPoint3DToAxoLocal(p, it) } ?: Offset.Unspecified
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
        axo = state.basis?.let { projectPoint3DToAxoLocal(p, it) } ?: Offset.Unspecified
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

private fun arcEllipseProjected3DAxo(state: MongeState, logical: Offset, prefix: String) {
    when (state.projectionPhase) {
        "${prefix}_elip3d_start" -> {
            val conic = if (prefix == "bokorys") state.selectedConicsBokorys.lastOrNull() else state.selectedConicsAxo.lastOrNull()
            val parent = conic?.parent ?: return println("⚠️ Tahle 2D elipsa nemá parent 3D.")
            if (classifyConicFromMatrix(parent.matrix).let { it != ConicType.ELLIPSE && it != ConicType.DEGENERATE }) return
            state.activeConicIdForArc = conic.id
            state.activeParentConic3DIdForEllipseArc = parent.id
            setProjectionPhase("${prefix}_elip3d_arc_hold", state)
        }
        "${prefix}_elip3d_arc_hold" -> {
            val conicId = state.activeConicIdForArc ?: return
            state.pendingArcA_3D = ellipsePointInProjection(state, conicId, logical, prefix)
            setProjectionPhase("${prefix}_elip3d_arc", state)
        }
        "${prefix}_elip3d_arc" -> {
            val conicId = state.activeConicIdForArc ?: return
            val parentId = state.activeParentConic3DIdForEllipseArc ?: return
            val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return
            val a2 = state.pendingArcA_3D ?: return
            val b2 = ellipsePointInProjection(state, conicId, logical, prefix)
            val a = liftAxoSourcePoint(prefix, a2, state, parent) ?: return
            val b = liftAxoSourcePoint(prefix, b2, state, parent) ?: return
            val mode = state.ellipseArcMode[conicId] ?: state.activeArcMode ?: ArcMode.SHORTEST
            storeEllipseAssociatedAxo(state, parent, a, b, mode, prefix)
            commitSnapshot(state)
            cleanupAssociatedAxo(state)
        }
    }
}

private fun arcParabolaProjected3DAxo(state: MongeState, logical: Offset, prefix: String) {
    when (state.projectionPhase) {
        "${prefix}_par3d_start" -> {
            val conic = if (prefix == "bokorys") state.selectedConicsBokorys.lastOrNull() else state.selectedConicsAxo.lastOrNull()
            val parent = conic?.parent ?: return println("⚠️ Tahle 2D parabola nemá parent 3D.")
            if (classifyConicFromMatrix(parent.matrix) != ConicType.PARABOLA) return
            state.activeConicIdForArc = conic.id
            state.activeParentConic3DIdForEllipseArc = parent.id
            setProjectionPhase("${prefix}_par3d_arc_hold", state)
        }
        "${prefix}_par3d_arc_hold" -> {
            val conicId = state.activeConicIdForArc ?: return
            state.pendingArcA_3D = parabolaPointInProjection(state, conicId, logical, prefix)
            setProjectionPhase("${prefix}_par3d_arc", state)
        }
        "${prefix}_par3d_arc" -> {
            val conicId = state.activeConicIdForArc ?: return
            val parentId = state.activeParentConic3DIdForEllipseArc ?: return
            val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return
            val a2 = state.pendingArcA_3D ?: return
            val b2 = parabolaPointInProjection(state, conicId, logical, prefix)
            val a = liftAxoSourcePoint(prefix, a2, state, parent) ?: return
            val b = liftAxoSourcePoint(prefix, b2, state, parent) ?: return
            storeParabolaAssociatedAxo(state, parent, a, b)
            commitSnapshot(state)
            cleanupAssociatedAxo(state)
        }
    }
}

private fun arcHyperbolaProjected3DAxo(state: MongeState, logical: Offset, prefix: String) {
    when (state.projectionPhase) {
        "${prefix}_hyp3d_start" -> {
            val conic = if (prefix == "bokorys") state.selectedConicsBokorys.lastOrNull() else state.selectedConicsAxo.lastOrNull()
            val parent = conic?.parent ?: return println("⚠️ Tahle 2D hyperbola nemá parent 3D.")
            if (classifyConicFromMatrix(parent.matrix) != ConicType.HYPERBOLA) return
            state.activeConicIdForArc = conic.id
            state.activeParentConic3DIdForEllipseArc = parent.id
            setProjectionPhase("${prefix}_hyp3d_b1_hold", state)
        }
        "${prefix}_hyp3d_b1_hold" -> {
            val conicId = state.activeConicIdForArc ?: return
            val (a1, sx) = hyperbolaPointInProjection(state, conicId, logical, prefix)
            state.pendingHypA1 = a1
            state.pendingHypBranch1SX = sx
            setProjectionPhase("${prefix}_hyp3d_b1", state)
        }
        "${prefix}_hyp3d_b1" -> {
            val conicId = state.activeConicIdForArc ?: return
            val parentId = state.activeParentConic3DIdForEllipseArc ?: return
            val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return
            val a2 = state.pendingHypA1 ?: return
            val sx = state.pendingHypBranch1SX ?: +1
            val (b2, _) = hyperbolaPointInProjection(state, conicId, logical, prefix, sx)
            val a = liftAxoSourcePoint(prefix, a2, state, parent) ?: return
            val b = liftAxoSourcePoint(prefix, b2, state, parent) ?: return
            storeHyperbolaAssociatedAxoBranch(state, parent, a, b, 1)
            state.pendingHypA1 = null
            state.pendingHypBranch1SX = null
            setProjectionPhase("${prefix}_hyp3d_a2", state)
        }
        "${prefix}_hyp3d_a2" -> {
            val conicId = state.activeConicIdForArc ?: return
            val (a2, sx) = hyperbolaPointInProjection(state, conicId, logical, prefix)
            state.pendingHypA2 = a2
            state.pendingHypBranch2SX = sx
            setProjectionPhase("${prefix}_hyp3d_b2", state)
        }
        "${prefix}_hyp3d_b2" -> {
            val conicId = state.activeConicIdForArc ?: return
            val parentId = state.activeParentConic3DIdForEllipseArc ?: return
            val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return
            val a2 = state.pendingHypA2 ?: return
            val sx = state.pendingHypBranch2SX ?: +1
            val (b2, _) = hyperbolaPointInProjection(state, conicId, logical, prefix, sx)
            val a = liftAxoSourcePoint(prefix, a2, state, parent) ?: return
            val b = liftAxoSourcePoint(prefix, b2, state, parent) ?: return
            storeHyperbolaAssociatedAxoBranch(state, parent, a, b, 2)
            commitSnapshot(state)
            cleanupAssociatedAxo(state)
        }
    }
}

fun arcEllipseBokorys3DAxo(state: MongeState, logical: Offset) = arcEllipseProjected3DAxo(state, logical, "bokorys")
fun arcParabolaBokorys3DAxo(state: MongeState, logical: Offset) = arcParabolaProjected3DAxo(state, logical, "bokorys")
fun arcHyperbolaBokorys3DAxo(state: MongeState, logical: Offset) = arcHyperbolaProjected3DAxo(state, logical, "bokorys")
fun arcEllipseNative3DAxo(state: MongeState, logical: Offset) = arcEllipseProjected3DAxo(state, logical, "axo")
fun arcParabolaNative3DAxo(state: MongeState, logical: Offset) = arcParabolaProjected3DAxo(state, logical, "axo")
fun arcHyperbolaNative3DAxo(state: MongeState, logical: Offset) = arcHyperbolaProjected3DAxo(state, logical, "axo")

// ─── Generic CONICARC handler (works for any projection) ────────────

private fun <C> handleConicArcGeneric(
    state: MongeState,
    logical: Offset,
    conics: List<C>,
    circles: List<C>,
    selectedConics: List<C>,
    selectedCircles: List<C>,
    conicInputs: Map<String, Triple<Offset, Offset, Offset>>,
    hyperbolaInputs: Map<String, *>,
    prefix: String
) where C : model.classes.ConicSection2D {
    val phase = state.projectionPhase

    // ── Circles ──
    when (phase) {
        "${prefix}_cir_start" -> {
            val circle = selectedCircles.lastOrNull() ?: return
            state.activeConicIdForArc = circle.id
            setProjectionPhase("${prefix}_circle_arc_hold", state)
        }
        "${prefix}_circle_arc_hold" -> {
            val conicId = state.activeConicIdForArc ?: return
            val inputs = conicInputs[conicId] ?: return
            val (p1, p2, p3) = inputs
            if (p3 == Offset.Unspecified) return
            state.pendingArcA = projectToEllipseFromDiameters(p1, p2, p3, logical)
            setProjectionPhase("${prefix}_circle_arc", state)
        }
        "${prefix}_circle_arc" -> {
            val conicId = state.activeConicIdForArc ?: return
            val inputs = conicInputs[conicId] ?: return
            val (p1, p2, p3) = inputs
            if (p3 == Offset.Unspecified) return
            val aProj = state.pendingArcA ?: return
            val bProj = projectToEllipseFromDiameters(p1, p2, p3, logical)
            val mode = state.activeArcMode ?: state.circleArcMode[conicId] ?: ArcMode.SHORTEST
            state.setCircleArc(conicId, aProj, bProj, mode)
            commitSnapshot(state)
            cleanup(state)
        }
    }

    // ── Ellipses ──
    when (phase) {
        "${prefix}_elip_start" -> {
            val conic = selectedConics.lastOrNull() ?: return
            state.activeConicIdForArc = conic.id
            setProjectionPhase("${prefix}_ellipse_arc_hold", state)
        }
        "${prefix}_ellipse_arc_hold" -> {
            val conicId = state.activeConicIdForArc ?: return
            val inputs = conicInputs[conicId] ?: return
            if (inputs.third == Offset.Unspecified || hyperbolaInputs.containsKey(conicId)) return
            val (p1, p2, p3) = inputs
            state.pendingArcA = projectToEllipseFromDiameters(p1, p2, p3, logical)
            setProjectionPhase("${prefix}_ellipse_arc", state)
        }
        "${prefix}_ellipse_arc" -> {
            val conicId = state.activeConicIdForArc ?: return
            val inputs = conicInputs[conicId] ?: return
            if (inputs.third == Offset.Unspecified) return
            val (p1, p2, p3) = inputs
            val Aproj = state.pendingArcA ?: return
            val Bproj = projectToEllipseFromDiameters(p1, p2, p3, logical)
            val mode = state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST
            state.setEllipseArc(conicId, Aproj, Bproj, mode)
            state.finalizeConicVisibilityIfActive(conicId)
            commitSnapshot(state)
            cleanup(state)
        }
    }

    // ── Parabolas ──
    when (phase) {
        "${prefix}_par_start" -> {
            val conic = selectedConics.lastOrNull() ?: return
            state.activeConicIdForArc = conic.id
            setProjectionPhase("${prefix}_par_arc_hold", state)
        }
        "${prefix}_par_arc_hold" -> {
            val conicId = state.activeConicIdForArc ?: return
            val inputs = conicInputs[conicId] ?: return
            val (vertex, focus, _) = inputs
            state.pendingArcA = projectToParabola(vertex, focus, logical)
            setProjectionPhase("${prefix}_par_arc", state)
        }
        "${prefix}_par_arc" -> {
            val conicId = state.activeConicIdForArc ?: return
            val inputs = conicInputs[conicId] ?: return
            if (inputs.third != Offset.Unspecified) return
            val (vertex, focus, _) = inputs
            val Aproj = state.pendingArcA ?: return
            val Bproj = projectToParabola(vertex, focus, logical)
            state.parabolaArcEnds[conicId] = Aproj to Bproj
            state.finalizeConicVisibilityIfActive(conicId)
            commitSnapshot(state)
            cleanup(state)
            state.triggerRedraw++
        }
    }

    // ── Hyperbolas ──
    when (phase) {
        "${prefix}_hyp_start" -> {
            val conicId = state.activeConicIdForArc ?: run {
                val conic = selectedConics.lastOrNull() ?: return
                state.activeConicIdForArc = conic.id
                conic.id
            }
            setProjectionPhase("${prefix}_hyp_second_hold", state)
        }
        "${prefix}_hyp_second_hold" -> {
            val conicId = state.activeConicIdForArc ?: return
            val basis = buildHyperbolaBasis(hyperbolaInputs[conicId] ?: return) ?: return
            if (state.pendingHypA1 == null) {
                val sx = if ((logical - basis.center).dot(basis.ex) >= 0f) +1 else -1
                val proj = projectToHyperbola(basis, logical, sx) ?: return
                state.pendingHypA1 = proj.on
                state.pendingHypBranch1SX = sx
            } else {
                val sx = state.pendingHypBranch1SX ?: +1
                val A1 = state.pendingHypA1!!
                val projB1 = projectToHyperbola(basis, logical, sx) ?: return
                state.hyperbolaArcBranch1[conicId] = A1 to projB1.on
                state.pendingHypA1 = null; state.pendingHypBranch1SX = null
                setProjectionPhase("${prefix}_hyp_second", state)
            }
        }
        "${prefix}_hyp_second" -> {
            val conicId = state.activeConicIdForArc ?: return
            val basis = buildHyperbolaBasis(hyperbolaInputs[conicId] ?: return) ?: return
            if (state.pendingHypA2 == null) {
                val sx = if ((logical - basis.center).dot(basis.ex) >= 0f) +1 else -1
                val proj = projectToHyperbola(basis, logical, sx) ?: return
                state.pendingHypA2 = proj.on
                state.pendingHypBranch2SX = sx
            } else {
                val sx = state.pendingHypBranch2SX ?: +1
                val A2 = state.pendingHypA2!!
                val projB2 = projectToHyperbola(basis, logical, sx) ?: return
                state.hyperbolaArcBranch2[conicId] = A2 to projB2.on
                state.finalizeConicVisibilityIfActive(conicId)
                commitSnapshot(state)
                state.pendingHypA2 = null; state.pendingHypBranch2SX = null
                state.activeConicIdForArc = null
                state.drawobjects = Mongeobjects.NONE
                setProjectionPhase("pudorys_start", state)
            }
        }
    }
}

private fun cleanup(state: MongeState) {
    state.pendingArcA = null
    state.activeConicIdForArc = null
    state.activeArcMode = null
    state.drawobjects = Mongeobjects.NONE
    setProjectionPhase("pudorys_start", state)
}

fun buildHyperbolaBasis(input: Any?): draw.mongescreen.previews.conicsarcs.HyperbolaBasis? {
    return when (input) {
        is model.classes.ConicInputHyperbolaPudorys -> {
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
        }
        is model.classes.ConicInputHyperbolaNarys -> {
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
        }
        is model.classes.ConicInputHyperbolaBokorys -> {
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.y, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.y, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
        }
        is model.classes.ConicInputHyperbolaAxo -> {
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
        }
        else -> null
    }
}

// ─── Decide functions ──────────────────────────────────────────────

fun decideConicBokorys(state: MongeState) {
    val conic = state.selectedConicsBokorys.lastOrNull()
    val circle = state.selectedCirclesBokorys.lastOrNull()
    if (conic == null && circle == null) { println("⚠️ Vyber nejdřív kuželosečku"); return }
    if (conic != null) {
        val parent3D = conic.parent
        if (parent3D != null && state.drawobjects == Mongeobjects.CONICARCAS) {
            val type = classifyConicFromMatrix(parent3D.matrix)
            when (type) {
                ConicType.ELLIPSE, ConicType.DEGENERATE -> setProjectionPhase("bokorys_elip3d_start", state)
                ConicType.PARABOLA -> setProjectionPhase("bokorys_par3d_start", state)
                ConicType.HYPERBOLA -> setProjectionPhase("bokorys_hyp3d_start", state)
                else -> {}
            }
            return
        }
        val inputs = state.conicInputPointsBokorys[conic.id]
        val isHyper = state.hyperbolaInputsBokorys.containsKey(conic.id)
        val isParab = (inputs == null || inputs.third == Offset.Unspecified) && !isHyper
        if (state.drawobjects == Mongeobjects.CONICARC) {
            when {
                isHyper -> setProjectionPhase("bokorys_hyp_start", state)
                isParab -> setProjectionPhase("bokorys_par_start", state)
                else -> setProjectionPhase("bokorys_elip_start", state)
            }
        }
    }
    if (circle != null) {
        setProjectionPhase("bokorys_cir_start", state)
    }
}

fun decideConicAxoNative(state: MongeState) {
    val conic = state.selectedConicsAxo.lastOrNull()
    if (conic == null) { println("⚠️ Vyber nejdřív kuželosečku"); return }
    val parent3D = conic.parent
    if (parent3D != null && state.drawobjects == Mongeobjects.CONICARCAS) {
        val type = classifyConicFromMatrix(parent3D.matrix)
        when (type) {
            ConicType.ELLIPSE, ConicType.DEGENERATE -> setProjectionPhase("axo_elip3d_start", state)
            ConicType.PARABOLA -> setProjectionPhase("axo_par3d_start", state)
            ConicType.HYPERBOLA -> setProjectionPhase("axo_hyp3d_start", state)
            else -> {}
        }
        return
    }
    val inputs = state.conicInputPointsAxo[conic.id]
    val isHyper = state.hyperbolaInputsAxo.containsKey(conic.id)
    val isParab = (inputs == null || inputs.third == Offset.Unspecified) && !isHyper
    if (state.drawobjects == Mongeobjects.CONICARC) {
        when {
            isHyper -> setProjectionPhase("axo_hyp_start", state)
            isParab -> setProjectionPhase("axo_par_start", state)
            else -> setProjectionPhase("axo_elip_start", state)
        }
    }
}

fun arcHyperbolaAxoSkipSecond(state: MongeState) {
    val phase = state.projectionPhase
    if (phase?.endsWith("_hyp_second") == true ||
        phase == "bokorys_hyp3d_a2" || phase == "bokorys_hyp3d_b2" ||
        phase == "axo_hyp3d_a2" || phase == "axo_hyp3d_b2"
    ) {
        state.activeConicIdForArc?.let { id ->
            state.finalizeConicVisibilityIfActive(id)
            commitSnapshot(state)
        }
        state.pendingHypA2 = null; state.pendingHypBranch2SX = null
        state.activeConicIdForArc = null
        state.activeParentConic3DIdForEllipseArc = null
        state.drawobjects = Mongeobjects.NONE
        setProjectionPhase("pudorys_start", state)
        state.triggerRedraw++
    }
}
