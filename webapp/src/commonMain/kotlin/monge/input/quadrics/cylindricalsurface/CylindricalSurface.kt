package monge.input.quadrics.cylindricalsurface

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.*
import model.classes.*
import monge.input.ConicArcs.single.ellipseBasisFromDiameters
import monge.input.conixections.conjugateDiameterInputFromRadii
import monge.input.ConicArcs.single.ellipseParamAndProjection
import monge.input.ConicArcs.single.setEllipseArc
import monge.input.quadrics.conicalsurface.ellipseFromConic3D
import model.DrawingModeMonge
import model.classes.PlaneEquation
import monge.input.axo.AxoRenderBasis
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.selection.CylinderPhase
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.resetStavu
import utils.allocIndex
import kotlin.math.*

fun length(v: Offset): Float =
    sqrt(v.x * v.x + v.y * v.y)
fun dot(a: Offset, b: Offset): Float = a.x * b.x + a.y * b.y
fun dot(a: Offset3D, b: Offset3D): Float = a.x*b.x + a.y*b.y + a.z*b.z

private fun Offset.distTo(o: Offset): Float {
    val dx = x - o.x; val dy = y - o.y
    return sqrt(dx*dx + dy*dy)
}

private const val SIL_EPS = 1e-4f

private fun addSilhouetteSegmentP(
    state: MongeState,
    surface: CylindricalSurface3D,
    a: Offset,  // (x,y) spodní
    b: Offset,  // (x,y) horní
    name: String
) {
    if (a.distTo(b) < SIL_EPS) {
        // degenerate → nic nezakládej
        return
    }
    val pA =
        Point3DPudorys(a.x, a.y, name = "", parent = null, isSegmentEndpoint = true, creationIndex = allocIndex(state))
    val pB =
        Point3DPudorys(b.x, b.y, name = "", parent = null, isSegmentEndpoint = true, creationIndex = allocIndex(state))
    val seg = Segment2DPudorys(
        start = pA, end = pB, name = name,
        parent = null,
        localColor = surface.color,
        localLineStyle = LineStyle.Solid,
        localStrokeWidth = surface.wireWidth,
        isConicalSilhouette = true,
        conicalSurfaceId = surface.id, creationIndex = allocIndex(state)
    )
    pA.parentSegment = seg; pB.parentSegment = seg
    state.pointsPudorys += pA; state.pointsPudorys += pB
    state.segmentsPudorys += seg
    surface.edgePointIdsPudorys2D += pA.id; surface.edgePointIdsPudorys2D += pB.id
    surface.edgeSegIdsPudorys2D += seg.id
}

private fun addSilhouetteSegmentN(
    state: MongeState,
    surface: CylindricalSurface3D,
    aXZ: Offset, // (x, -z) spodní
    bXZ: Offset, // (x, -z) horní
    name: String
) {
    if (aXZ.distTo(bXZ) < SIL_EPS) {
        // degenerate → nic nezakládej
        return
    }
    // Pozor: Point3DNarys bere (x, z), ty máš (x, -z)
    val pA = Point3DNarys(
        aXZ.x,
        -aXZ.y,
        name = "",
        parent = null,
        isSegmentEndpoint = true,
        creationIndex = allocIndex(state)
    )
    val pB = Point3DNarys(
        bXZ.x,
        -bXZ.y,
        name = "",
        parent = null,
        isSegmentEndpoint = true,
        creationIndex = allocIndex(state)
    )
    val seg = Segment2DNarys(
        start = pA, end = pB, name = name,
        parent = null,
        localColor = surface.color,
        localLineStyle = LineStyle.Solid,
        localStrokeWidth = surface.wireWidth,
        isConicalSilhouette = true,
        conicalSurfaceId = surface.id, creationIndex = allocIndex(state)
    )
    pA.parentSegment = seg; pB.parentSegment = seg
    state.pointsNarys += pA; state.pointsNarys += pB
    state.segmentsNarys += seg
    surface.edgePointIdsNarys2D += pA.id; surface.edgePointIdsNarys2D += pB.id
    surface.edgeSegIdsNarys2D += seg.id
}

private fun addSilhouetteSegmentB(
    state: MongeState,
    surface: CylindricalSurface3D,
    aYZ: Offset,
    bYZ: Offset,
    name: String,
    showInAxo: Boolean
) {
    if (aYZ.distTo(bYZ) < SIL_EPS) return
    val pA = Point3DBokorys(
        aYZ.x, aYZ.y, name = "", parent = null,
        isSegmentEndpoint = true, creationIndex = allocIndex(state),
        showInAxoInitial = showInAxo
    )
    val pB = Point3DBokorys(
        bYZ.x, bYZ.y, name = "", parent = null,
        isSegmentEndpoint = true, creationIndex = allocIndex(state),
        showInAxoInitial = showInAxo
    )
    pA.showInAxo = showInAxo
    pB.showInAxo = showInAxo
    val seg = Segment2DBokorys(
        start = pA, end = pB, name = name,
        parent = null,
        localColor = surface.color,
        localLineStyle = LineStyle.Solid,
        localStrokeWidth = surface.wireWidth,
        isConicalSilhouette = true,
        conicalSurfaceId = surface.id,
        showInAxoInitial = showInAxo,
        creationIndex = allocIndex(state)
    )
    seg.showInAxo = showInAxo
    pA.parentSegment = seg
    pB.parentSegment = seg
    state.pointsBokorys += pA
    state.pointsBokorys += pB
    state.segmentsBokorys += seg
    surface.edgePointIdsBokorys2D += pA.id
    surface.edgePointIdsBokorys2D += pB.id
    surface.edgeSegIdsBokorys2D += seg.id
}

private fun addSilhouetteSegmentAxo(
    state: MongeState,
    surface: CylindricalSurface3D,
    a3D: Offset3D,
    b3D: Offset3D,
    basis: AxoRenderBasis,
    name: String
) {
    val a = projectPoint3DToAxoLocal(a3D, basis)
    val b = projectPoint3DToAxoLocal(b3D, basis)
    if (a.distTo(b) < SIL_EPS) return

    val pA = Point3DAxo(
        x = a.x, y = a.y,
        name = "",
        parent = null,
        isSegmentEndpoint = true,
        creationIndex = allocIndex(state),
        showInAxoInitial = true
    )
    val pB = Point3DAxo(
        x = b.x, y = b.y,
        name = "",
        parent = null,
        isSegmentEndpoint = true,
        creationIndex = allocIndex(state),
        showInAxoInitial = true
    )
    val seg = Segment2DAxo(
        start = pA, end = pB, name = name,
        parent = null,
        localColor = surface.color,
        localLineStyle = LineStyle.Solid,
        localStrokeWidth = surface.wireWidth,
        isConicalSilhouette = true,
        conicalSurfaceId = surface.id,
        showInAxoInitial = true,
        creationIndex = allocIndex(state)
    )
    pA.parentSegment = seg
    pB.parentSegment = seg
    state.pointsAxo += pA
    state.pointsAxo += pB
    state.segmentsAxo += seg
    surface.edgePointIdsAxo2D += pA.id
    surface.edgePointIdsAxo2D += pB.id
    surface.edgeSegIdsAxo2D += seg.id
}

fun rebuildCylindricalSilhouette2D(state: MongeState, surface: CylindricalSurface3D) {
    // 0) Najdi vstupy
    val baseConic3D = state.conics3D.find { it.id == surface.directrixId } ?: run {
        println("⚠️ Cyl: base conic ${surface.directrixId} nenalezena.")
        return
    }
    val eq = surface.equation?.normalized() ?: run {
        println("⚠️ Cyl: top plane bez rovnice.")
        return
    }
    val n = Offset3D(eq.a, eq.b, eq.c)
    val D = eq.d
    val d = surface.direction.normalized()  // směr tvořic

    val nDotD = n.x*d.x + n.y*d.y + n.z*d.z
    if (abs(nDotD) < 1e-6f) {
        println("⚠️ Cyl: n·d≈0 → směr téměř rovnoběžný s rovinou; obrysy nevytvářím.")
        return
    }

    // 1) Získej elipsu jako u kužele
    val el = ellipseFromConic3D(baseConic3D).also {
        if (it == null) println("ℹ️ Cyl: directrix ${baseConic3D.name} není elipsa → obrys nepostavím.")
    } ?: return

    // 2) Smazat staré 2D segmenty a endpointy vytvořené touto plochou
    if (surface.edgeSegIdsPudorys2D.isNotEmpty()) {
        state.segmentsPudorys.removeAll { it.id in surface.edgeSegIdsPudorys2D }
        surface.edgeSegIdsPudorys2D.clear()
    }
    if (surface.edgeSegIdsNarys2D.isNotEmpty()) {
        state.segmentsNarys.removeAll { it.id in surface.edgeSegIdsNarys2D }
        surface.edgeSegIdsNarys2D.clear()
    }
    if (surface.edgeSegIdsBokorys2D.isNotEmpty()) {
        state.segmentsBokorys.removeAll { it.id in surface.edgeSegIdsBokorys2D }
        surface.edgeSegIdsBokorys2D.clear()
    }
    if (surface.edgePointIdsPudorys2D.isNotEmpty()) {
        state.pointsPudorys.removeAll { it.id in surface.edgePointIdsPudorys2D }
        surface.edgePointIdsPudorys2D.clear()
    }
    if (surface.edgePointIdsNarys2D.isNotEmpty()) {
        state.pointsNarys.removeAll { it.id in surface.edgePointIdsNarys2D }
        surface.edgePointIdsNarys2D.clear()
    }
    if (surface.edgePointIdsBokorys2D.isNotEmpty()) {
        state.pointsBokorys.removeAll { it.id in surface.edgePointIdsBokorys2D }
        surface.edgePointIdsBokorys2D.clear()
    }

    // 3) Vzorkování elipsy v 3D stejně jako u kužele
    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    val S = surface.tessT.coerceAtLeast(64)
    fun C(t: Float) = el.center3D + el.uRot * (el.a * cos(t)) +
            el.vRot * (el.b * sin(t))

    // 4) Extruze bodů do horní roviny: P_top = P + t * d, t = -(n·P + d)/(n·d)
    fun extrudeToTop(P: Offset3D): Offset3D {
        val t = - ((n.x*P.x + n.y*P.y + n.z*P.z) + D) / nDotD
        return Offset3D(P.x + d.x*t, P.y + d.y*t, P.z + d.z*t)
    }

    val lower3D = ArrayList<Offset3D>(S)
    val upper3D = ArrayList<Offset3D>(S)
    for (i in 0 until S) {
        val t = twoPi * i / S
        val p = C(t)
        lower3D += p
        upper3D += extrudeToTop(p)
    }

    // ——— projekce helpery
    fun projP(p: Offset3D) = Offset(p.x, p.y)     // půdorys
    fun projN(p: Offset3D) = Offset(p.x, -p.z)    // nárys
    fun perp2D(v: Offset) = Offset(-v.y, v.x)
    fun norm2D(v: Offset): Offset {
        val L = sqrt(v.x*v.x + v.y*v.y)
        return if (L < 1e-8f) Offset(1f,0f) else Offset(v.x/L, v.y/L)
    }
    fun dot2(a: Offset, b: Offset) = a.x*b.x + a.y*b.y

    // 5) PŮDORYS: dvě podpůrné pozice kolmé na směr tvořic
    run {
        val v2d = projP(d)
        val v2dLen = sqrt(v2d.x*v2d.x + v2d.y*v2d.y)
        if (v2dLen < 1e-6f) {
            // d je kolmý na projekční rovinu → obě podstavy se překrývají, obrysy neexistují
            state.pendingOuterArcP_base = null
            state.pendingOuterArcP_top = null
            return@run
        }

        val lowers2D = lower3D.map(::projP)
        val uppers2D = upper3D.map(::projP)
        val n2d = norm2D(perp2D(v2d))

        var iMin = 0; var iMax = 0
        var minVal = Float.POSITIVE_INFINITY
        var maxVal = Float.NEGATIVE_INFINITY
        for (i in lowers2D.indices) {
            val s = dot2(n2d, lowers2D[i])
            if (s < minVal) { minVal = s; iMin = i }
            if (s > maxVal) { maxVal = s; iMax = i }
        }

        addSilhouetteSegmentP(state, surface, lowers2D[iMin], uppers2D[iMin], "")
        addSilhouetteSegmentP(state, surface, lowers2D[iMax], uppers2D[iMax], "")

        val topC3D = extrudeToTop(el.center3D)
        val topC_P = Offset(topC3D.x, topC3D.y)
        val baseC_P = Offset(el.center3D.x, el.center3D.y)

        val span1 = ((iMax - iMin) % S + S) % S
        val span2 = S - span1
        val iMid1 = (iMin + span1 / 2) % S
        val iMid2 = (iMax + span2 / 2) % S

        val d2d = norm2D(v2d)
        val baseCDir = dot2(d2d, baseC_P)
        val topCDir = dot2(d2d, topC_P)
        val topSide = topCDir - baseCDir

        val mid1DirBase = dot2(d2d, lowers2D[iMid1])
        val outerBaseIdx = if ((mid1DirBase - baseCDir) * topSide < 0) iMid1 else iMid2

        val mid1DirTop = dot2(d2d, uppers2D[iMid1])
        val outerTopIdx = if ((mid1DirTop - topCDir) * topSide > 0) iMid1 else iMid2

        state.pendingOuterArcP_base = PendingArc(
            A = lowers2D[iMin], B = lowers2D[iMax], through = lowers2D[outerBaseIdx]
        )
        state.pendingOuterArcP_top = PendingArc(
            A = uppers2D[iMin], B = uppers2D[iMax], through = uppers2D[outerTopIdx]
        )
    }


    // 6) NÁRYS: totéž
    run {
        val v2d = projN(d)
        val v2dLen = sqrt(v2d.x*v2d.x + v2d.y*v2d.y)
        if (v2dLen < 1e-6f) {
            state.pendingOuterArcN_base = null
            state.pendingOuterArcN_top = null
            return@run
        }

        val lowersN = lower3D.map(::projN)
        val uppersN = upper3D.map(::projN)
        val n2d = norm2D(perp2D(v2d))

        var iMin = 0; var iMax = 0
        var minVal = Float.POSITIVE_INFINITY
        var maxVal = Float.NEGATIVE_INFINITY
        for (i in lowersN.indices) {
            val s = dot2(n2d, lowersN[i])
            if (s < minVal) { minVal = s; iMin = i }
            if (s > maxVal) { maxVal = s; iMax = i }
        }

        addSilhouetteSegmentN(state, surface, lowersN[iMin], uppersN[iMin], "")
        addSilhouetteSegmentN(state, surface, lowersN[iMax], uppersN[iMax], "")

        val topCenter3D = extrudeToTop(el.center3D)
        val topC_N = Offset(topCenter3D.x, -topCenter3D.z)
        val baseC_N = Offset(el.center3D.x, -el.center3D.z)

        val span1 = ((iMax - iMin) % S + S) % S
        val span2 = S - span1
        val iMid1 = (iMin + span1 / 2) % S
        val iMid2 = (iMax + span2 / 2) % S

        val d2d = norm2D(v2d)
        val baseCDir = dot2(d2d, baseC_N)
        val topCDir = dot2(d2d, topC_N)
        val topSide = topCDir - baseCDir

        val mid1DirBase = dot2(d2d, lowersN[iMid1])
        val outerBaseIdx = if ((mid1DirBase - baseCDir) * topSide < 0) iMid1 else iMid2

        val mid1DirTop = dot2(d2d, uppersN[iMid1])
        val outerTopIdx = if ((mid1DirTop - topCDir) * topSide > 0) iMid1 else iMid2

        state.pendingOuterArcN_base = PendingArc(
            A = lowersN[iMin], B = lowersN[iMax], through = lowersN[outerBaseIdx]
        )
        state.pendingOuterArcN_top = PendingArc(
            A = uppersN[iMin], B = uppersN[iMax], through = uppersN[outerTopIdx]
        )
    }

    // 7) BOKORYS: stejná logika jako P/N, projekce (y, z)
    run {
        val v2d = Offset(d.y, d.z)
        val v2dLen = sqrt(v2d.x*v2d.x + v2d.y*v2d.y)
        if (v2dLen < 1e-6f) {
            return@run
        }

        val lowersB = lower3D.map { Offset(it.y, it.z) }
        val uppersB = upper3D.map { Offset(it.y, it.z) }
        val n2d = norm2D(perp2D(v2d))

        var iMin = 0; var iMax = 0
        var minVal = Float.POSITIVE_INFINITY
        var maxVal = Float.NEGATIVE_INFINITY
        for (i in lowersB.indices) {
            val s = dot2(n2d, lowersB[i])
            if (s < minVal) { minVal = s; iMin = i }
            if (s > maxVal) { maxVal = s; iMax = i }
        }

        addSilhouetteSegmentB(state, surface, lowersB[iMin], uppersB[iMin], "", showInAxo = true)
        addSilhouetteSegmentB(state, surface, lowersB[iMax], uppersB[iMax], "", showInAxo = true)
    }

    state.triggerRedraw++
}

fun rebuildCylindricalSilhouetteAxo(
    state: MongeState,
    surface: CylindricalSurface3D,
    basis: AxoRenderBasis
) {
    val baseConic3D = state.conics3D.find { it.id == surface.directrixId } ?: return
    val eq = surface.equation?.normalized() ?: return
    val n = Offset3D(eq.a, eq.b, eq.c)
    val d = surface.direction.normalized()
    val nDotD = n.x * d.x + n.y * d.y + n.z * d.z
    if (abs(nDotD) < 1e-6f) return

    val el = ellipseFromConic3D(baseConic3D) ?: return

    if (surface.edgeSegIdsAxo2D.isNotEmpty()) {
        state.segmentsAxo.removeAll { it.id in surface.edgeSegIdsAxo2D }
        surface.edgeSegIdsAxo2D.clear()
    }
    if (surface.edgePointIdsAxo2D.isNotEmpty()) {
        state.pointsAxo.removeAll { it.id in surface.edgePointIdsAxo2D }
        surface.edgePointIdsAxo2D.clear()
    }

    val samples = surface.tessT.coerceAtLeast(64)
    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    fun C(t: Float) = el.center3D + el.uRot * (el.a * cos(t)) +
            el.vRot * (el.b * sin(t))
    fun extrudeToTop(p: Offset3D): Offset3D {
        val t = -((n.x * p.x + n.y * p.y + n.z * p.z) + eq.d) / nDotD
        return Offset3D(p.x + d.x * t, p.y + d.y * t, p.z + d.z * t)
    }

    val lower3D = ArrayList<Offset3D>(samples)
    val upper3D = ArrayList<Offset3D>(samples)
    for (i in 0 until samples) {
        val p = C(twoPi * i / samples)
        lower3D += p
        upper3D += extrudeToTop(p)
    }

    val normalProj = projectPoint3DToAxoLocal(d, basis)
    val perp = Offset(-normalProj.y, normalProj.x)
    val len = sqrt(perp.x * perp.x + perp.y * perp.y)
    if (len < 1e-6f) return
    val n2d = Offset(perp.x / len, perp.y / len)

    var iMin = 0
    var iMax = 0
    var minVal = Float.POSITIVE_INFINITY
    var maxVal = Float.NEGATIVE_INFINITY
    for (i in lower3D.indices) {
        val p = projectPoint3DToAxoLocal(lower3D[i], basis)
        val s = p.x * n2d.x + p.y * n2d.y
        if (s < minVal) { minVal = s; iMin = i }
        if (s > maxVal) { maxVal = s; iMax = i }
    }

    addSilhouetteSegmentAxo(state, surface, lower3D[iMin], upper3D[iMin], basis, "")
    addSilhouetteSegmentAxo(state, surface, lower3D[iMax], upper3D[iMax], basis, "")
    state.triggerRedraw++
}

fun rebuildAllCylindricalSilhouettesAxo(state: MongeState) {
    val basis = state.basis ?: return
    state.cylindricalSurfaces.forEach { surface ->
        rebuildCylindricalSilhouetteAxo(state, surface, basis)
        applyCylinderOuterArcsBokorysAxo(state, surface)
        resolveCylinderInteriorVisibility2D(state, surface)
    }
}

fun rebuildCylindricalSurfaceForAxoConversion(state: MongeState, surface: CylindricalSurface3D) {
    val directrix = state.conics3D.find { it.id == surface.directrixId } ?: return
    val basis = state.basis ?: return

    rebuildCylindricalSilhouetteAxo(state, surface, basis)
    applyCylinderOuterArcsBokorysAxo(state, surface)
    resolveCylinderInteriorVisibility2D(state, surface)
    hideCylinderNonAxoProjectionsAfterAxoConstruction(state, surface, directrix)
}

fun resetCylinderPending(state: MongeState) {
    state.pendingConic3DId = null
    state.pendingDirection3D = null
    state.pendingLine3DId = null
    state.pendingSegment3DId = null
    state.pendingTopPlane3DId = null
    state.perpCylinderBaseConicId = null
    state.perpCylinderBaseNormal = null
    state.perpCylinderBaseCenter3D = null
    state.perpCylinderBaseU = null
    state.perpCylinderBaseV = null
}

fun buildCylindricalSurfaceFrom(
    state: MongeState,
    baseConic3D: ConicSection3D,
    dir: Offset3D,
    topPlane: Plane3D,
    tess: Int = 128,
    surfaceName: String = "σ"
): CylindricalSurface3D = buildCylindricalSurfaceFrom(
    state, baseConic3D, dir, topPlane.equation!!, topPlane.id, tess, surfaceName
)

fun buildCylindricalSurfaceFrom(
    state: MongeState,
    baseConic3D: ConicSection3D,
    dir: Offset3D,
    topPlaneEq: PlaneEquation,
    topPlaneId: String? = null,
    tess: Int = 128,
    surfaceName: String = "σ"
): CylindricalSurface3D {
    val L = sqrt(dir.x*dir.x + dir.y*dir.y + dir.z*dir.z)
    require(L > 1e-8f) { "Smer tvoric je nulovy." }
    val dN = Offset3D(dir.x / L, dir.y / L, dir.z / L)

    val surface = CylindricalSurface3D(
        name = surfaceName,
        directrixId = baseConic3D.id,
        direction = dN,
        topPlaneId = topPlaneId,
        lowerConicId = baseConic3D.id,
        upperConicId = null,
        color = baseConic3D.color,
        wireWidth = baseConic3D.strokeWidth,
        tessT = tess,
        isVisible = true,
        equation = topPlaneEq, creationIndex = allocIndex(state)
    )

    state.cylindricalSurfaces += surface
    rebuildCylindricalSilhouette2D(state, surface)

    return surface
}
fun handleCylindricalToolClick(state: MongeState, axoConstruction: Boolean = false) {
    if (state.drawobjects != Mongeobjects.CYLINDER) return

    fun norm3(v: Offset3D): Offset3D {
        val L = sqrt(v.x*v.x + v.y*v.y + v.z*v.z)
        require(L > 1e-8f) { "Směr tvořic je nulový." }
        return Offset3D(v.x / L, v.y / L, v.z / L)
    }
    fun dirFrom(line: Line3D): Offset3D = norm3(line.direction)
    fun dirFrom(seg: Segment3D): Offset3D =
        norm3(Offset3D(seg.end.x - seg.start.x, seg.end.y - seg.start.y, seg.end.z - seg.start.z))

    fun clearUiSelectionOnly() {

        try { clearSelection(state) } catch (_: Throwable) {}
    }

    when (state.cylinderPhase) {

        CylinderPhase.PICK_CONIC -> {
            // vezmi poslední vybranou projekční kuželosečku a najdi její parent 3D
            val c2dP = state.selectedConicsPudorys.lastOrNull()
            val c2dN = state.selectedConicsNarys.lastOrNull()
            val c2dA = state.selectedConicsAxo.lastOrNull()
            val parent3D =
                state.snappedConicAxo?.parent ?: state.snappedConicAxo?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: state.snappedConicPudorys?.parent ?: state.snappedConicPudorys?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: state.snappedConicNarys?.parent ?: state.snappedConicNarys?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: state.snappedConicBokorys?.parent ?: state.snappedConicBokorys?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: c2dP?.parent ?: c2dP?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: c2dN?.parent ?: c2dN?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: c2dA?.parent ?: c2dA?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }

            if (parent3D != null) {
                state.pendingConic3DId = parent3D.id
                state.cylinderPhase = CylinderPhase.PICK_DIRECTION
                clearUiSelectionOnly()
                println("🟦 Vybrána kuželosečka '${parent3D.name}'. Teď vyber PŘÍMKU nebo ÚSEČKU pro SMĚR.")
            } else {
                println("⚠️ Klikni na kuželosečku v některé projekci (s parent 3D).")
            }
        }

        CylinderPhase.PICK_DIRECTION -> {
            // priority: přímka → úsečka; akceptuj z obou projekcí (safe-cast na typy s parent)
            var dir: Offset3D? = null

            state.selectedLinesPudorys.lastOrNull()?.let { named ->
                (named as? Line3DProjectionPudorys)?.parent?.let { line3D ->
                    state.pendingLine3DId = line3D.id
                    dir = dirFrom(line3D)
                }
            }
            if (dir == null) {
                state.snappedLineAxo?.parent?.let { line3D ->
                    state.pendingLine3DId = line3D.id
                    dir = dirFrom(line3D)
                }
            }
            if (dir == null) {
                state.selectedLinesNarys.lastOrNull()?.let { named ->
                    (named as? Line3DProjectionNarys)?.parent?.let { line3D ->
                        state.pendingLine3DId = line3D.id
                        dir = dirFrom(line3D)
                    }
                }
            }
            if (dir == null) {
                state.selectedLinesAxo.lastOrNull()?.parent?.let { line3D ->
                    state.pendingLine3DId = line3D.id
                    dir = dirFrom(line3D)
                }
            }
            if (dir == null) {
                state.snappedLineBokorys?.let { named ->
                    (named as? Line3DProjectionBokorys)?.parent?.let { line3D ->
                        state.pendingLine3DId = line3D.id
                        dir = dirFrom(line3D)
                    }
                }
            }
            if (dir == null) {
                state.selectedLinesBokorys.lastOrNull()?.let { named ->
                    (named as? Line3DProjectionBokorys)?.parent?.let { line3D ->
                        state.pendingLine3DId = line3D.id
                        dir = dirFrom(line3D)
                    }
                }
            }
            if (dir == null) {
                state.snappedSegmentAxo?.parent?.let { seg3D ->
                    state.pendingSegment3DId = seg3D.id
                    dir = dirFrom(seg3D)
                }
            }
            if (dir == null) {
                state.snappedSegmentBokorys?.let { seg2d ->
                    (seg2d as? Segment2DBokorys)?.parent?.let { seg3D ->
                        state.pendingSegment3DId = seg3D.id
                        dir = dirFrom(seg3D)
                    }
                }
            }
            if (dir == null) {
                state.selectedSegmentsPudorys.lastOrNull()?.let { seg2d ->
                    (seg2d as? Segment2DPudorys)?.parent?.let { seg3D ->
                        state.pendingSegment3DId = seg3D.id
                        dir = dirFrom(seg3D)
                    }
                }
            }
            if (dir == null) {
                state.selectedSegmentsAxo.lastOrNull()?.parent?.let { seg3D ->
                    state.pendingSegment3DId = seg3D.id
                    dir = dirFrom(seg3D)
                }
            }
            if (dir == null) {
                state.selectedSegmentsBokorys.lastOrNull()?.parent?.let { seg3D ->
                    state.pendingSegment3DId = seg3D.id
                    dir = dirFrom(seg3D)
                }
            }
            if (dir == null) {
                state.selectedSegmentsNarys.lastOrNull()?.let { seg2d ->
                    (seg2d as? Segment2DNarys)?.parent?.let { seg3D ->
                        state.pendingSegment3DId = seg3D.id
                        dir = dirFrom(seg3D)
                    }
                }
            }

            if (dir != null) {
                state.pendingDirection3D = dir
                state.cylinderPhase = CylinderPhase.PICK_PLANE
                clearUiSelectionOnly()
                println("🟦 Směr tvořic uložen. Teď vyber HORNÍ ROVINU.")
            } else {
                println("⚠️ Vyber přímku/úsečku (projekci), ze které získám 3D směr.")
            }
        }

        CylinderPhase.PICK_PLANE -> {
            val plane3D = resolveSelectedPlane3D(state)
            if (plane3D == null) {
                println("CYL ⚠ rovina nenalezena – klikni na stopu roviny.")
                println("    selectedPlanes.last = ${state.selectedPlanes.lastOrNull()?.name}")
                println("    P line last = ${state.selectedLinesPudorys.lastOrNull()?.let { it::class.simpleName }}")
                println("    N line last = ${state.selectedLinesNarys.lastOrNull()?.let { it::class.simpleName }}")
                return
            }

            state.pendingTopPlane3DId = plane3D.id
            // máme z předchozích fází?
            val conic3D = state.conics3D.find { it.id == state.pendingConic3DId }
            val dir = state.pendingDirection3D

            if (conic3D == null || dir == null) {
                println("⚠️ Chybí kuželosečka nebo směr – vrať se a vyber znovu.")
                return
            }

            val planeEq = plane3D.equation?.normalized()
            if (planeEq != null) {
                val nDotD = planeEq.a * dir.x + planeEq.b * dir.y + planeEq.c * dir.z
                if (abs(nDotD) < 1e-6f) {
                    state.constructionErrorMessage =
                        "Směr tvořic je rovnoběžný s vybranou rovinou — nelze sestrojit válcovou plochu."
                    state.showConstructionErrorDialog = true
                    resetCylinderPending(state)
                    state.cylinderPhase = CylinderPhase.PICK_CONIC
                    clearUiSelectionOnly()
                    state.triggerRedraw++
                    return
                }
            }

            try {
                val surface = buildCylindricalSurfaceFrom(
                    state = state,
                    baseConic3D = conic3D,
                    dir = dir,
                    topPlane = plane3D,
                    tess = 128,
                    surfaceName = "σ"
                )
                state.selectedCylindricalSurface = surface
                buildUpperEllipseForCylinder(state, surface, axoConstruction = axoConstruction)

                conic3D.directrixOfSurfaceIds += surface.id
                applyPendingCylinderOuterArcs2D(state, surface)
                applyCylinderOuterArcsBokorysAxo(state, surface)
                resolveCylinderInteriorVisibility2D(state, surface)
                if (axoConstruction) {
                    state.basis?.let { rebuildCylindricalSilhouetteAxo(state, surface, it) }
                    hideCylinderNonAxoProjectionsAfterAxoConstruction(state, surface, conic3D)
                }
                clearUiSelectionOnly()
                println("✅ Válcová plocha vytvořena: base='${conic3D.name}', plane='${plane3D.name}'.")
                commitSnapshot(state)
                resetStavu(state)
                repeatCons(state)
            } catch (e: Exception) {
                state.constructionErrorMessage = e.message ?: "Neočekávaná chyba při konstrukci válcové plochy."
                state.showConstructionErrorDialog = true
                resetCylinderPending(state)
                state.cylinderPhase = CylinderPhase.PICK_CONIC
                clearUiSelectionOnly()
                state.triggerRedraw++
                println("⚠️ Cylinder construction error: ${e.message}")
            }
        }

        CylinderPhase.IDLE, CylinderPhase.PICK_CONIC_PERP, CylinderPhase.PICK_CENTER_PERP -> {}
    }
}

fun handlePerpendicularCylinderClick(
    state: MongeState,
    logicalCursor: Offset,
    axoConstruction: Boolean = false
) {
    if (state.drawobjects != Mongeobjects.CYLINDER) return

    fun clearUiSelectionOnly() {
        try { clearSelection(state) } catch (_: Throwable) {}
    }

    when (state.cylinderPhase) {
        CylinderPhase.PICK_CONIC_PERP -> {
            val c2dP = state.selectedConicsPudorys.lastOrNull()
            val c2dN = state.selectedConicsNarys.lastOrNull()
            val c2dA = state.selectedConicsAxo.lastOrNull()
            val parent3D =
                state.snappedConicAxo?.parent ?: state.snappedConicAxo?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: state.snappedConicPudorys?.parent ?: state.snappedConicPudorys?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: state.snappedConicNarys?.parent ?: state.snappedConicNarys?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: state.snappedConicBokorys?.parent ?: state.snappedConicBokorys?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: c2dP?.parent ?: c2dP?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: c2dN?.parent ?: c2dN?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }
                ?: c2dA?.parent ?: c2dA?.parentId?.let { pid -> state.conics3D.find { it.id == pid } }

            if (parent3D == null) {
                println("Klikni na kuželosečku v některé projekci (s parent 3D).")
                return
            }

            val el = ellipseFromConic3D(parent3D)
            if (el == null) {
                state.constructionErrorMessage = "Vybraná kuželosečka není elipsa."
                state.showConstructionErrorDialog = true
                return
            }

            val normal = safeNorm3(cross3(el.uRot, el.vRot))
            if (len3(normal) < 1e-6f) {
                state.constructionErrorMessage = "Nelze určit normálu roviny podstavy."
                state.showConstructionErrorDialog = true
                return
            }

            val normalProjP = sqrt(normal.x * normal.x + normal.y * normal.y)
            val normalProjN = sqrt(normal.x * normal.x + normal.z * normal.z)
            val EPS_PROJ = 1e-4f

            if (!axoConstruction && normalProjP < EPS_PROJ && normalProjN < EPS_PROJ) {
                state.constructionErrorMessage = "Normála roviny podstavy je degenerovaná v obou pohledech."
                state.showConstructionErrorDialog = true
                resetCylinderPending(state)
                state.cylinderPhase = CylinderPhase.PICK_CONIC_PERP
                clearUiSelectionOnly()
                state.triggerRedraw++
                return
            }

            if (!axoConstruction && state.mongeMode == DrawingModeMonge.PUDORYS && normalProjP < EPS_PROJ) {
                state.mongeMode = DrawingModeMonge.NARYS
            } else if (!axoConstruction && state.mongeMode == DrawingModeMonge.NARYS && normalProjN < EPS_PROJ) {
                state.mongeMode = DrawingModeMonge.PUDORYS
            }

            state.perpCylinderBaseConicId = parent3D.id
            state.perpCylinderBaseNormal = normal
            state.perpCylinderBaseCenter3D = el.center3D
            state.perpCylinderBaseU = el.uRot * el.a
            state.perpCylinderBaseV = el.vRot * el.b

            state.cylinderPhase = CylinderPhase.PICK_CENTER_PERP
            clearUiSelectionOnly()
        }

        CylinderPhase.PICK_CENTER_PERP -> {
            val basis = state.basis
            val cursor = if (axoConstruction && basis != null) {
                state.snappedPointLogical
                    ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
            } else {
                logicalCursor
            }
            val normal = state.perpCylinderBaseNormal ?: return
            val baseCenter = state.perpCylinderBaseCenter3D ?: return
            val conicId = state.perpCylinderBaseConicId ?: return
            val conic3D = state.conics3D.find { it.id == conicId } ?: return

            val t = if (axoConstruction && basis != null) {
                computePerpCylinderTAxo(cursor, baseCenter, normal, basis)
            } else {
                computePerpCylinderT(state.mongeMode, cursor, baseCenter, normal)
            } ?: return
            val upperCenter = Offset3D(
                baseCenter.x + normal.x * t,
                baseCenter.y + normal.y * t,
                baseCenter.z + normal.z * t
            )

            val planeD = -(normal.x * upperCenter.x + normal.y * upperCenter.y + normal.z * upperCenter.z)
            val topEq = PlaneEquation(normal.x, normal.y, normal.z, planeD)

            try {
                val surface = buildCylindricalSurfaceFrom(
                    state = state, baseConic3D = conic3D, dir = normal,
                    topPlaneEq = topEq, tess = 128, surfaceName = "σ"
                )
                state.selectedCylindricalSurface = surface
                buildUpperEllipseForCylinder(state, surface, axoConstruction = axoConstruction)
                conic3D.directrixOfSurfaceIds += surface.id
                applyPendingCylinderOuterArcs2D(state, surface)
                applyCylinderOuterArcsBokorysAxo(state, surface)
                resolveCylinderInteriorVisibility2D(state, surface)
                if (axoConstruction) {
                    basis?.let { rebuildCylindricalSilhouetteAxo(state, surface, it) }
                    hideCylinderNonAxoProjectionsAfterAxoConstruction(state, surface, conic3D)
                }
                clearUiSelectionOnly()
                commitSnapshot(state)
                resetStavu(state)
                repeatCons(state)
            } catch (e: Exception) {
                state.constructionErrorMessage = e.message ?: "Chyba při konstrukci kolmého válce."
                state.showConstructionErrorDialog = true
                resetCylinderPending(state)
                state.cylinderPhase = CylinderPhase.PICK_CONIC_PERP
                clearUiSelectionOnly()
                state.triggerRedraw++
            }
        }

        else -> {}
    }
}

fun computePerpCylinderT(
    mode: DrawingModeMonge, cursor: Offset,
    baseCenter: Offset3D, normal: Offset3D
): Float? {
    // getLogicalCursorNarys returns (x, z) not (x, -z)
    val (baseProj, normalProj) = when (mode) {
        DrawingModeMonge.PUDORYS -> Offset(baseCenter.x, baseCenter.y) to Offset(normal.x, normal.y)
        DrawingModeMonge.NARYS -> Offset(baseCenter.x, baseCenter.z) to Offset(normal.x, normal.z)
    }
    val nLen2 = normalProj.x * normalProj.x + normalProj.y * normalProj.y
    if (nLen2 < 1e-8f) return null
    val delta = cursor - baseProj
    return (delta.x * normalProj.x + delta.y * normalProj.y) / nLen2
}

fun computePerpCylinderTAxo(
    cursorAxo: Offset,
    baseCenter: Offset3D,
    normal: Offset3D,
    basis: AxoRenderBasis
): Float? {
    val baseProj = projectPoint3DToAxoLocal(baseCenter, basis)
    val normalProj = projectPoint3DToAxoLocal(normal, basis)
    val nLen2 = normalProj.x * normalProj.x + normalProj.y * normalProj.y
    if (nLen2 < 1e-8f) return null
    val delta = cursorAxo - baseProj
    return (delta.x * normalProj.x + delta.y * normalProj.y) / nLen2
}

fun hideCylinderNonAxoProjectionsAfterAxoConstruction(
    state: MongeState,
    surface: CylindricalSurface3D,
    directrix: ConicSection3D
) {
    fun hideConicP(c: ConicSectionPudorys) { c.showInAxoInitial = false; c.showInAxo = false }
    fun hideConicN(c: ConicSectionNarys) { c.showInAxoInitial = false; c.showInAxo = false }
    fun showConicA(c: ConicSectionAxo) { c.showInAxoInitial = true; c.showInAxo = true }
    fun hidePointP(p: Point3DPudorys) { p.showInAxoInitial = false; p.showInAxo = false }
    fun hidePointN(p: Point3DNarys) { p.showInAxoInitial = false; p.showInAxo = false }
    fun hidePointB(p: Point3DBokorys) { p.showInAxoInitial = false; p.showInAxo = false }
    fun showPointA(p: Point3DAxo) { p.showInAxoInitial = true; p.showInAxo = true }
    fun hideSegmentP(s: Segment2DPudorys) { s.showInAxoInitial = false; s.showInAxo = false }
    fun hideSegmentN(s: Segment2DNarys) { s.showInAxoInitial = false; s.showInAxo = false }
    fun hideSegmentB(s: Segment2DBokorys) { s.showInAxoInitial = false; s.showInAxo = false }
    fun showSegmentA(s: Segment2DAxo) { s.showInAxoInitial = true; s.showInAxo = true }

    val conicIds = setOfNotNull(directrix.id, surface.upperConicId)
    state.conicsPudorys.filter { (it.parent?.id ?: it.parentId) in conicIds }.forEach(::hideConicP)
    state.conicsNarys.filter { (it.parent?.id ?: it.parentId) in conicIds }.forEach(::hideConicN)
    state.conicsBokorys.filter { (it.parent?.id ?: it.parentId) in conicIds }.forEach {
        it.showInAxoInitial = false
        it.showInAxo = false
    }
    state.conicsAxo.filter { (it.parent?.id ?: it.parentId) in conicIds }.forEach(::showConicA)

    state.pointsPudorys.filter { it.id in surface.edgePointIdsPudorys2D }.forEach(::hidePointP)
    state.pointsNarys.filter { it.id in surface.edgePointIdsNarys2D }.forEach(::hidePointN)
    state.pointsBokorys.filter { it.id in surface.edgePointIdsBokorys2D }.forEach(::hidePointB)
    state.pointsAxo.filter { it.id in surface.edgePointIdsAxo2D }.forEach(::showPointA)

    state.segmentsPudorys.filter { it.id in surface.edgeSegIdsPudorys2D }.forEach(::hideSegmentP)
    state.segmentsNarys.filter { it.id in surface.edgeSegIdsNarys2D }.forEach(::hideSegmentN)
    state.segmentsBokorys.filter { it.id in surface.edgeSegIdsBokorys2D }.forEach(::hideSegmentB)
    state.segmentsAxo.filter { it.id in surface.edgeSegIdsAxo2D }.forEach(::showSegmentA)
}

// ✅ drop-in resolver, dej ho nad handler
private fun resolveSelectedPlane3D(state: MongeState): Plane3D? {
    // primární – sem ti ji ukládá selection code
    state.selectedPlanes.lastOrNull()?.let { return it }

    // případné alternativy, pokud je používáš jinde
    state.selectedPlaneForCircle?.let { return it }

    // z vybraných stop roviny (2D projekce)
    (state.selectedLinesPudorys.lastOrNull() as? PlaneTracePudorys)?.parent?.let { return it }
    (state.selectedLinesNarys.lastOrNull()   as? PlaneTraceNarys)?.parent?.let { return it }
    (state.selectedLinesBokorys.lastOrNull() as? PlaneTraceBokorys)?.parent?.let { return it }
    (state.snappedLinePudorys as? PlaneTracePudorys)?.parent?.let { return it }
    (state.snappedLineNarys as? PlaneTraceNarys)?.parent?.let { return it }
    (state.snappedLineBokorys as? PlaneTraceBokorys)?.parent?.let { return it }

    return null
}

// ==== pomocné mini-utility (lokálně ulož k handleru/builderu) ====
private fun dot3(a: Offset3D, b: Offset3D) = a.x*b.x + a.y*b.y + a.z*b.z
private fun len3(v: Offset3D) = sqrt(dot3(v, v))
private fun norm3(v: Offset3D): Offset3D {
    val L = len3(v); require(L > 1e-8f) { "Nulový vektor." }
    return Offset3D(v.x/L, v.y/L, v.z/L)
}
private fun cross3(a: Offset3D, b: Offset3D) = Offset3D(
    a.y*b.z - a.z*b.y,
    a.z*b.x - a.x*b.z,
    a.x*b.y - a.y*b.x
)
private fun safeNorm3(v: Offset3D): Offset3D {
    val L = len3(v)
    return if (L < 1e-8f) v else Offset3D(v.x/L, v.y/L, v.z/L)
}
private fun outerPlus(col: Offset3D, d: Offset3D, s: Float) =
    Offset3D(col.x + d.x*s, col.y + d.y*s, col.z + d.z*s)

private fun orthoBasisInPlane(n: Offset3D, hint: Offset3D? = null): Pair<Offset3D, Offset3D> {
    val nh = norm3(n)
    // zkus preferovat vektor, který není rovnoběžný s normálou
    val t0 = hint?.let {
        val proj = dot3(it, nh)
        Offset3D(it.x - proj*nh.x, it.y - proj*nh.y, it.z - proj*nh.z)
    } ?: run {
        // fallback podle nejmenší složky normály
        val a = if (abs(nh.x) < 0.9f) Offset3D(1f, 0f, 0f) else Offset3D(0f, 1f, 0f)
        val proj = dot3(a, nh)
        Offset3D(a.x - proj*nh.x, a.y - proj*nh.y, a.z - proj*nh.z)
    }
    val e1 = norm3(t0)
    // e2 = n × e1
    val e2 = norm3(Offset3D(
        nh.y*e1.z - nh.z*e1.y,
        nh.z*e1.x - nh.x*e1.z,
        nh.x*e1.y - nh.y*e1.x
    ))
    return e1 to e2
}

private fun inv2x2(a11: Float, a12: Float, a21: Float, a22: Float): FloatArray? {
    val det = a11*a22 - a12*a21
    if (abs(det) < 1e-10f) return null
    val inv = 1f/det
    // vrátím po řádcích: [i11, i12, i21, i22]
    return floatArrayOf( a22*inv, -a12*inv, -a21*inv, a11*inv )
}

// Sestaví 3×3 matici coniku z A..F podle schématu (A, B/2, C, D/2, E/2, F)

// ==== hlavní: vytvoř horní elipsu v rovině a ulož do stavu ====
fun buildUpperEllipseForCylinder(
    state: MongeState,
    surface: CylindricalSurface3D,
    axoConstruction: Boolean = false
): ConicSection3D? {
    val baseConic3D = state.conics3D.find { it.id == surface.directrixId } ?: return null
    val eqN = (surface.equation ?: return null).normalized()
    val n = Offset3D(eqN.a, eqN.b, eqN.c)
    val Dp = eqN.d
    val d = norm3(surface.direction)

    val el = ellipseFromConic3D(baseConic3D) ?: return null
    val C = el.center3D
    val U = Offset3D(el.uRot.x * el.a, el.uRot.y * el.a, el.uRot.z * el.a) // 1. sloupec M
    val V = Offset3D(el.vRot.x * el.b, el.vRot.y * el.b, el.vRot.z * el.b) // 2. sloupec M

    val nDotD = dot3(n, d)
    if (abs(nDotD) < 1e-8f) return null

    // tC = -(n·C + D) / (n·d)
    val tC = - (dot3(n, C) + Dp) / nDotD
    // row = - (n·M) / (n·d)  → [α, β]
    val alpha = - (dot3(n, U)) / nDotD
    val beta  = - (dot3(n, V)) / nDotD

    val Cprime = Offset3D(C.x + d.x*tC, C.y + d.y*tC, C.z + d.z*tC)
    val Uprime = outerPlus(U, d, alpha) // U' = U + α d
    val Vprime = outerPlus(V, d, beta)  // V' = V + β d

    // Ortonormální báze horní roviny – zkus srovnat e1 s U' kvůli stabilitě
    val (e1, e2) = orthoBasisInPlane(n, hint = Uprime)

    // Vyjádři 3D sloupce U', V' v souřadnicích (e1,e2): A2 = [ [ux, vx], [uy, vy] ] po sloupcích
    fun proj2(c: Offset3D): Pair<Float, Float> = dot3(e1, c) to dot3(e2, c)
    val (ux, uy) = proj2(Uprime)
    val (vx, vy) = proj2(Vprime)

    // Inverze A2, pak K = A2^{-T} A2^{-1}
    val inv = inv2x2(ux, vx, uy, vy) ?: return null
    val i11 = inv[0]; val i12 = inv[1]; val i21 = inv[2]; val i22 = inv[3]
    // K = inv^T * inv
    val K11 = i11*i11 + i21*i21
    val K12 = i11*i12 + i21*i22
    val K22 = i12*i12 + i22*i22

    // Implicitní rovnice v (e1,e2)-souřadnicích se středem v C':  [x y] K [x y]^T - 1 = 0
    val A = K11
    val B = 2f * K12
    val Cc = K22
    val D = 0f
    val E = 0f
    val F = -1f

    val mat = Matrix3x3.fromCoefficients(A, B, Cc, D, E, F)

    val upper = ConicSection3D(
        p0 = Cprime,
        u = e1,
        v = e2,
        matrix = mat,
        rawName = baseConic3D.rawName,
        color = baseConic3D.color,
        strokeWidth = baseConic3D.strokeWidth,
        lineStyle = baseConic3D.lineStyle, creationIndex = allocIndex(state)
    )


    state.conics3D += upper
    addConicProjectionsWithInputPoints(
        state, upper, Cprime, Cprime + Uprime, Cprime + Vprime,
        axoConstruction = axoConstruction
    )
    surface.upperConicId = upper.id
    upper.directrixOfSurfaceIds += surface.id

    return upper
}

fun addConicProjectionsWithInputPoints(
    state: MongeState, conic3D: ConicSection3D,
    center3D: Offset3D, r1_3D: Offset3D, r2_3D: Offset3D,
    axoConstruction: Boolean = false
) {
    val centerP = Offset(center3D.x, center3D.y)
    val r1P = Offset(r1_3D.x, r1_3D.y)
    val r2P = Offset(r2_3D.x, r2_3D.y)

    val centerN = Offset(center3D.x, -center3D.z)
    val r1N = Offset(r1_3D.x, -r1_3D.z)
    val r2N = Offset(r2_3D.x, -r2_3D.z)

    val centerB = Offset(center3D.y, center3D.z)
    val r1B = Offset(r1_3D.y, r1_3D.z)
    val r2B = Offset(r2_3D.y, r2_3D.z)

    // ─────────────────────────────────────────────────────────────────────
    // PUDORYS
    run {
        val pudorysMatrix = conic3D.projectToXY()
        val coeffs1 = Matrix3x3.toCoefficients(pudorysMatrix)
        val a1 = coeffs1[0]; val b1 = coeffs1[1]; val c1 = coeffs1[2]
        val d1 = coeffs1[3]; val e1 = coeffs1[4]; val f1 = coeffs1[5]

        val pudorys = ConicSectionPudorys(
            a = a1, b = b1, c = c1, d = d1, e = e1, f = f1,
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            showInAxoInitial = !axoConstruction,
            creationIndex = allocIndex(state)
        )
        pudorys.showInAxo = !axoConstruction
        state.conicsPudorys.add(pudorys)
        state.conicInputPointsPudorys[pudorys.id] =
            conjugateDiameterInputFromRadii(centerP, r1P, r2P)
    }

    // ─────────────────────────────────────────────────────────────────────
    // NARYS
    run {
        val narysMatrix = conic3D.projectToXZ()
        val coeffs2 = Matrix3x3.toCoefficients(narysMatrix)
        val a2 = coeffs2[0]; val b2 = coeffs2[1]; val c2 = coeffs2[2]
        val d2 = coeffs2[3]; val e2 = coeffs2[4]; val f2 = coeffs2[5]

        val aN = a2; val bN = -b2; val cN = c2; val dN = d2; val eN = -e2; val fN = f2

        val narys = ConicSectionNarys(
            a = aN, b = bN, c = cN, d = dN, e = eN, f = fN,
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            showInAxoInitial = !axoConstruction,
            creationIndex = allocIndex(state)
        )
        narys.showInAxo = !axoConstruction
        state.conicsNarys.add(narys)
        state.conicInputPointsNarys[narys.id] =
            conjugateDiameterInputFromRadii(centerN, r1N, r2N)
    }

    run {
        val bokorysMatrix = conic3D.projectToYZ()
        val coeffs3 = Matrix3x3.toCoefficients(bokorysMatrix)
        val bokorys = ConicSectionBokorys(
            a = coeffs3[0], b = coeffs3[1], c = coeffs3[2],
            d = coeffs3[3], e = coeffs3[4], f = coeffs3[5],
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            showInAxoInitial = !axoConstruction,
            creationIndex = allocIndex(state)
        )
        bokorys.showInAxo = !axoConstruction
        state.conicsBokorys.add(bokorys)
        state.conicInputPointsBokorys[bokorys.id] =
            conjugateDiameterInputFromRadii(centerB, r1B, r2B)
    }

    val basis = state.basis
    if (basis != null) {
        val axoMatrix = conic3D.projectToAxo(basis)
        val coeffsA = Matrix3x3.toCoefficients(axoMatrix)
        val axo = ConicSectionAxo(
            a = coeffsA[0], b = coeffsA[1], c = coeffsA[2],
            d = coeffsA[3], e = coeffsA[4], f = coeffsA[5],
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            showInAxoInitial = true,
            creationIndex = allocIndex(state)
        )
        axo.showInAxo = true
        state.conicsAxo.add(axo)
        state.conicInputPointsAxo[axo.id] = conjugateDiameterInputFromRadii(
            projectPoint3DToAxoLocal(center3D, basis),
            projectPoint3DToAxoLocal(r1_3D, basis),
            projectPoint3DToAxoLocal(r2_3D, basis)
        )
    }
}

private fun applyArcFromPending(
    state: MongeState,
    conicIdCandidate: String?,
    pending: PendingArc?,
    inputPoints: Map<String, Triple<Offset, Offset, Offset>>,
    findConic: (String?) -> Any?
) {
    if (pending == null || conicIdCandidate == null) return

    val conic = findConic(conicIdCandidate) ?: return
    val conicId = when (conic) {
        is ConicSectionPudorys -> conic.id
        is ConicSectionNarys -> conic.id
        else -> return
    }

    val inputs = inputPoints[conicId] ?: return
    val (p1, p2, p3) = inputs
    if (p1 == Offset.Unspecified || p2 == Offset.Unspecified || p3 == Offset.Unspecified) return

    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    if (basis.oneMinusC2 < 1e-4f) return
    val (tA, _) = ellipseParamAndProjection(basis, pending.A)
    val (tB, _) = ellipseParamAndProjection(basis, pending.B)
    val (tT, _) = ellipseParamAndProjection(basis, pending.through)

    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    val ccwSpan = ((tB - tA) % twoPi + twoPi) % twoPi
    val throughDelta = ((tT - tA) % twoPi + twoPi) % twoPi
    val mode = if (throughDelta <= ccwSpan) ArcMode.CCW else ArcMode.CW

    state.setEllipseArc(conicId, pending.A, pending.B, mode)
}

fun applyPendingCylinderOuterArcs2D(state: MongeState, surface: CylindricalSurface3D) {
    fun findP(id: String?) = id?.let { cid ->
        state.conicsPudorys.firstOrNull {
            it.id == cid || (it.parent?.id ?: it.parentId) == cid
        }
    }
    fun findN(id: String?) = id?.let { cid ->
        state.conicsNarys.firstOrNull {
            it.id == cid || (it.parent?.id ?: it.parentId) == cid
        }
    }

    applyArcFromPending(state, surface.directrixId, state.pendingOuterArcP_base,
        state.conicInputPointsPudorys, ::findP)
    applyArcFromPending(state, surface.upperConicId, state.pendingOuterArcP_top,
        state.conicInputPointsPudorys, ::findP)
    applyArcFromPending(state, surface.directrixId, state.pendingOuterArcN_base,
        state.conicInputPointsNarys, ::findN)
    applyArcFromPending(state, surface.upperConicId, state.pendingOuterArcN_top,
        state.conicInputPointsNarys, ::findN)

    state.pendingOuterArcP_base = null
    state.pendingOuterArcP_top  = null
    state.pendingOuterArcN_base = null
    state.pendingOuterArcN_top  = null
}

private fun applyCylinderOuterArcToConic(
    state: MongeState,
    conicIdCandidate: String?,
    pending: PendingArc?,
    inputPoints: Map<String, Triple<Offset, Offset, Offset>>,
    findConicId: (String?) -> String?
) {
    if (pending == null || conicIdCandidate == null) return
    val conicId = findConicId(conicIdCandidate) ?: return
    val inputs = inputPoints[conicId] ?: return
    val (p1, p2, p3) = inputs
    if (p1 == Offset.Unspecified || p2 == Offset.Unspecified || p3 == Offset.Unspecified) return

    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    if (basis.oneMinusC2 < 1e-4f) return
    val (tA, _) = ellipseParamAndProjection(basis, pending.A)
    val (tB, _) = ellipseParamAndProjection(basis, pending.B)
    val (tT, _) = ellipseParamAndProjection(basis, pending.through)

    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    val ccwSpan = ((tB - tA) % twoPi + twoPi) % twoPi
    val throughDelta = ((tT - tA) % twoPi + twoPi) % twoPi
    val mode = if (throughDelta <= ccwSpan) ArcMode.CCW else ArcMode.CW
    state.setEllipseArc(conicId, pending.A, pending.B, mode)
}

private fun cylinderOuterArcsForProjection(
    lower3D: List<Offset3D>,
    upper3D: List<Offset3D>,
    baseCenter: Offset3D,
    topCenter: Offset3D,
    direction: Offset3D,
    project: (Offset3D) -> Offset
): Pair<PendingArc?, PendingArc?> {
    val dir2d = project(direction) - project(Offset3D(0f, 0f, 0f))
    val dirLen = sqrt(dir2d.x * dir2d.x + dir2d.y * dir2d.y)
    if (dirLen < 1e-6f) return null to null

    val lowers = lower3D.map(project)
    val uppers = upper3D.map(project)
    val n2d = Offset(-dir2d.y / dirLen, dir2d.x / dirLen)

    var iMin = 0
    var iMax = 0
    var minVal = Float.POSITIVE_INFINITY
    var maxVal = Float.NEGATIVE_INFINITY
    for (i in lowers.indices) {
        val s = lowers[i].x * n2d.x + lowers[i].y * n2d.y
        if (s < minVal) { minVal = s; iMin = i }
        if (s > maxVal) { maxVal = s; iMax = i }
    }

    val span1 = ((iMax - iMin) % lowers.size + lowers.size) % lowers.size
    val span2 = lowers.size - span1
    val iMid1 = (iMin + span1 / 2) % lowers.size
    val iMid2 = (iMax + span2 / 2) % lowers.size

    val topC = project(topCenter)
    val baseC = project(baseCenter)

    val d2d = Offset(dir2d.x / dirLen, dir2d.y / dirLen)
    val baseCDir = baseC.x * d2d.x + baseC.y * d2d.y
    val topCDir = topC.x * d2d.x + topC.y * d2d.y
    val topSide = topCDir - baseCDir

    val mid1DirBase = lowers[iMid1].x * d2d.x + lowers[iMid1].y * d2d.y
    val outerBaseIdx = if ((mid1DirBase - baseCDir) * topSide < 0) iMid1 else iMid2

    val mid1DirTop = uppers[iMid1].x * d2d.x + uppers[iMid1].y * d2d.y
    val outerTopIdx = if ((mid1DirTop - topCDir) * topSide > 0) iMid1 else iMid2

    return PendingArc(lowers[iMin], lowers[iMax], lowers[outerBaseIdx]) to
            PendingArc(uppers[iMin], uppers[iMax], uppers[outerTopIdx])
}

fun applyCylinderOuterArcsBokorysAxo(state: MongeState, surface: CylindricalSurface3D) {
    val baseConic3D = state.conics3D.find { it.id == surface.directrixId } ?: return
    val eq = surface.equation?.normalized() ?: return
    val el = ellipseFromConic3D(baseConic3D) ?: return
    val basis = state.basis

    val n = Offset3D(eq.a, eq.b, eq.c)
    val d = surface.direction.normalized()
    val nDotD = dot3(n, d)
    if (abs(nDotD) < 1e-8f) return

    val samples = surface.tessT.coerceAtLeast(64)
    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    fun C(t: Float) = el.center3D + el.uRot * (el.a * cos(t)) +
            el.vRot * (el.b * sin(t))
    fun extrudeToTop(p: Offset3D): Offset3D {
        val t = -(dot3(n, p) + eq.d) / nDotD
        return Offset3D(p.x + d.x * t, p.y + d.y * t, p.z + d.z * t)
    }

    val lower3D = ArrayList<Offset3D>(samples)
    val upper3D = ArrayList<Offset3D>(samples)
    for (i in 0 until samples) {
        val p = C(twoPi * i / samples)
        lower3D += p
        upper3D += extrudeToTop(p)
    }
    val topCenter = extrudeToTop(el.center3D)

    fun findB(id: String?) = id?.let { cid ->
        state.conicsBokorys.firstOrNull { it.id == cid || (it.parent?.id ?: it.parentId) == cid }?.id
    }
    val (baseB, topB) = cylinderOuterArcsForProjection(
        lower3D, upper3D, el.center3D, topCenter, d
    ) { Offset(it.y, it.z) }
    applyCylinderOuterArcToConic(state, surface.directrixId, baseB, state.conicInputPointsBokorys, ::findB)
    applyCylinderOuterArcToConic(state, surface.upperConicId, topB, state.conicInputPointsBokorys, ::findB)

    if (basis != null) {
        fun findA(id: String?) = id?.let { cid ->
            state.conicsAxo.firstOrNull { it.id == cid || (it.parent?.id ?: it.parentId) == cid }?.id
        }
        val (baseA, topA) = cylinderOuterArcsForProjection(
            lower3D, upper3D, el.center3D, topCenter, d
        ) { projectPoint3DToAxoLocal(it, basis) }
        applyCylinderOuterArcToConic(state, surface.directrixId, baseA, state.conicInputPointsAxo, ::findA)
        applyCylinderOuterArcToConic(state, surface.upperConicId, topA, state.conicInputPointsAxo, ::findA)
    }
}


fun resolveCylinderInteriorVisibility2D(state: MongeState, surface: CylindricalSurface3D) {
    val base3D = state.conics3D.find { it.id == (surface.lowerConicId ?: surface.directrixId) } ?: return
    val eq = surface.equation ?: return
    val el = ellipseFromConic3D(base3D) ?: return

    val d = surface.direction.normalized()
    val n1 = Offset3D(eq.a, eq.b, eq.c)
    val d1 = eq.d

    val baseCenter = el.center3D
    val nDotD = dot3(n1, d)
    val topCenter = if (abs(nDotD) > 1e-8f) {
        val t = -(dot3(n1, baseCenter) + d1) / nDotD
        Offset3D(baseCenter.x + d.x * t, baseCenter.y + d.y * t, baseCenter.z + d.z * t)
    } else baseCenter

    val vProjP = Offset3D(0f, 0f, 1f)
    val vProjN = Offset3D(0f, 1f, 0f)

    fun removeArc(conicId: String) {
        state.ellipseArcEnds.remove(conicId)
        state.ellipseArcMode.remove(conicId)
    }

    fun findConicId(conics: List<*>, targetId: String): String? {
        for (c in conics) {
            val id: String
            val parentId: String?
            when (c) {
                is ConicSectionPudorys -> { id = c.id; parentId = c.parent?.id ?: c.parentId }
                is ConicSectionNarys -> { id = c.id; parentId = c.parent?.id ?: c.parentId }
                is ConicSectionBokorys -> { id = c.id; parentId = c.parent?.id ?: c.parentId }
                is ConicSectionAxo -> { id = c.id; parentId = c.parent?.id ?: c.parentId }
                else -> continue
            }
            if (id == targetId || parentId == targetId) return id
        }
        return null
    }

    val projections = mutableListOf<Pair<Offset3D, List<*>>>(
        vProjP to state.conicsPudorys,
        vProjN to state.conicsNarys,
        Offset3D(1f, 0f, 0f) to state.conicsBokorys
    )
    state.basis?.let { basis ->
        val rowX = Offset3D(basis.ex.x, basis.ey.x, basis.ez.x)
        val rowY = Offset3D(basis.ex.y, basis.ey.y, basis.ez.y)
        var axoDepth = cross3(rowX, rowY)
        if (len3(axoDepth) > 1e-6f) {
            if (axoDepth.z < 0f) axoDepth = Offset3D(-axoDepth.x, -axoDepth.y, -axoDepth.z)
            projections += axoDepth to state.conicsAxo
        }
    }

    for ((proj, conics) in projections) {

        val baseDepth = dot3(baseCenter, proj)
        val topDepth = dot3(topCenter, proj)

        val baseVisible = baseDepth > topDepth
        val topVisible = topDepth > baseDepth

        if (baseVisible) {
            findConicId(conics, surface.directrixId)?.let(::removeArc)
        }
        if (topVisible) {
            surface.upperConicId?.let { uid ->
                findConicId(conics, uid)?.let(::removeArc)
            }
        }
    }

    state.triggerRedraw++
}
