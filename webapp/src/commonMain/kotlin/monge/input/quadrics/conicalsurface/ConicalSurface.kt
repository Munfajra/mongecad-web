package monge.input.quadrics.conicalsurface


import androidx.compose.ui.geometry.Offset
import draw.mongescreen.labels.clearSelection
import serialization.commitSnapshot
import model.*
import model.classes.*
import monge.input.ConicArcs.single.setEllipseArc
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.resetStavu
import utils.allocIndex
import kotlin.math.*

fun handleConicalToolClick(state: MongeState, axoConstruction: Boolean = false) {
    if (state.drawobjects != Mongeobjects.CONE) return

    data class PlaneEquation(val a: Float, val b: Float, val c: Float, val d: Float)

    fun cross3(a: Offset3D, b: Offset3D) = Offset3D(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )

    fun dot3(a: Offset3D, b: Offset3D) = a.x * b.x + a.y * b.y + a.z * b.z

    fun planeFromConic3D(conic: ConicSection3D): PlaneEquation {
        val n = cross3(conic.u, conic.v)
        val d = -dot3(n, conic.p0)
        return PlaneEquation(n.x, n.y, n.z, d)
    }

    // -----------------------------
    // 1) Pick vstupy přes SNAPPED
    // -----------------------------

    // Conic pick: vezmi snapped conic v aktuálním pohledu, jinak (bez live hoveru nad
    // plátnem, např. výběr přes ObjectList) poslední vybranou kuželosečku ze seznamu.
    fun pickSnappedConic3DId(): String? {
        val cA = state.snappedConicAxo
        if (cA != null) return cA.parent?.id ?: cA.parentId

        val cP = state.snappedConicPudorys   // <-- uprav jméno, pokud máš jiné
        if (cP != null) return cP.parent?.id ?: cP.parentId

        val cN = state.snappedConicNarys     // <-- uprav jméno, pokud máš jiné
        if (cN != null) return cN.parent?.id ?: cN.parentId

        val cB = state.snappedConicBokorys
        if (cB != null) return cB.parent?.id ?: cB.parentId

        state.selectedConicsPudorys.lastOrNull()?.let { return it.parent?.id ?: it.parentId }
        state.selectedConicsNarys.lastOrNull()?.let { return it.parent?.id ?: it.parentId }
        state.selectedConicsBokorys.lastOrNull()?.let { return it.parent?.id ?: it.parentId }
        state.selectedConicsAxo.lastOrNull()?.let { return it.parent?.id ?: it.parentId }

        return null
    }

    // Apex pick: vezmi snapped bod v aktuálním pohledu
    fun pickSnappedApex3DId(): String? {
        val pA = state.snappedAxoPoint
        if (pA != null) return pA.parent?.id

        val pP = state.snappedPointPudorys
        if (pP != null) return pP.parent?.id

        val pN = state.snappedPointNarys
        if (pN != null) return pN.parent?.id

        val pB = state.snappedPointBokorys
        if (pB != null) return pB.parent?.id

        return null
    }

    val snappedConicId = pickSnappedConic3DId()
    val snappedApexId  = pickSnappedApex3DId()

    if (snappedConicId != null && state.pendingConic3DId != snappedConicId) {
        state.pendingConic3DId = snappedConicId
    }

    if (snappedApexId != null && state.pendingApex3DId != snappedApexId) {
        state.pendingApex3DId = snappedApexId
    }
    // -----------------------------
    // 2) Finalize (stejně jako dnes)
    // -----------------------------
    fun tryFinalize(): Boolean {
        val conic3D = state.conics3D.find { it.id == state.pendingConic3DId } ?: return false

        // (u tebe je tu chyba: hledáš sharedPoints3D dvakrát)
        val apex3D = state.sharedPoints3D.find { it.id == state.pendingApex3DId }
            ?: return false

        val eq = planeFromConic3D(conic3D)
        val s = eq.a * apex3D.x + eq.b * apex3D.y + eq.c * apex3D.z + eq.d
        if (abs(s) < 1e-6f) {
            println("⚠️ Apex leží v rovině řídicí kuželosečky → degenerace (vějíř).")
            return false
        }

        val surface = ConicalSurface3D(
            apexId = apex3D.id,
            directrixId = conic3D.id,
            name = "κ",
            wireWidth = conic3D.strokeWidth,
            color = state.currentLineStyleSettings.color, creationIndex = allocIndex(state)
        )

        conic3D.directrixOfSurfaceIds += surface.id
        state.conicalSurfaces += surface
        rebuildConicalSilhouette2D(state, surface,apex3D)
        if (axoConstruction) {
            hideConeNonAxoProjectionsAfterAxoConstruction(state, surface, apex3D, conic3D)
        }
        state.selectedConicalSurface = surface

        state.pendingConic3DId = null
        state.pendingApex3DId = null
        state.triggerRedraw++
        println("✅ Kužel vytvořen: apex=${apex3D.name}, directrix=${conic3D.name}")
        repeatCons(state)

        // selection už není vstup, takže čistit můžeš jen “pro pořádek”
        clearSelection(state)
        return true
    }

    val done = tryFinalize()

    if (done) {
        commitSnapshot(state)
        resetStavu(state)
    } else {
        // nech pendingy běžet, uživatel doplní druhý klik
    }
}


fun ellipseFromConic3D(conic: ConicSection3D, eps: Float = 1e-12f): EllipseParam? {
    val A = conic.matrix.m00
    val B = 2f * conic.matrix.m01
    val C = conic.matrix.m11
    val D = 2f * conic.matrix.m02
    val E = 2f * conic.matrix.m12
    val F = conic.matrix.m22

    val det = 4f*A*C - B*B
    if (abs(det) < eps) return null // parabola/degenerace

    val a0 = (-2f*C*D + B*E) / det
    val b0 = ( B*D - 2f*A*E) / det

    val s00 = A; val s01 = B*0.5f; val s11 = C
    val tr  = s00 + s11
    val detS = s00*s11 - s01*s01
    val disc = max(0f, tr*tr - 4f*detS)
    val lam1 = 0.5f * (tr + sqrt(disc))
    val lam2 = 0.5f * (tr - sqrt(disc))

    val (e1x, e1y) = if (abs(s01) > eps) {
        val x = s01; val y = (lam1 - s00)
        val n = sqrt(x*x + y*y).coerceAtLeast(eps)
        (x/n) to (y/n)
    } else if (s00 >= s11) 1f to 0f else 0f to 1f  // diagonální S: λ₁ (větší) patří větší diagonále
    val e2x = -e1y; val e2y = e1x

    val Fp = A*a0*a0 + B*a0*b0 + C*b0*b0 + D*a0 + E*b0 + F
    val d1 = -Fp / lam1
    val d2 = -Fp / lam2
    if (d1 <= eps || d2 <= eps) return null

    val a = sqrt(abs(d1))
    val b = sqrt(abs(d2))

    val center3D = conic.p0 + conic.u*a0 + conic.v*b0
    val uRot = conic.u*e1x + conic.v*e1y
    val vRot = conic.u*e2x + conic.v*e2y
    return EllipseParam(center3D, uRot, vRot, a, b)
}
private fun dot3(a: Offset3D, b: Offset3D) = a.x*b.x + a.y*b.y + a.z*b.z
private fun cross3(a: Offset3D, b: Offset3D) = Offset3D(
    a.y*b.z - a.z*b.y,
    a.z*b.x - a.x*b.z,
    a.x*b.y - a.y*b.x
)
// vrátí dva 3D body C(t*) na řídicí elipse, které tvoří v dané projekci siluetu kužele
fun findConeSilhouetteOnEllipse(
    apex: Offset3D,
    el: EllipseParam,
    proj: ProjectionAxis,
    samples: Int = 720
): List<Offset3D> {
    val vProj = if (proj == ProjectionAxis.PUDORYS) Offset3D(0f,0f,1f) else Offset3D(0f,1f,0f)

    fun C(t: Float) = el.center3D + el.uRot * (el.a * cos(t)) +
            el.vRot * (el.b * sin(t))
    fun T(t: Float) = el.uRot * (-el.a * sin(t)) +
            el.vRot * ( el.b * cos(t))
    fun s(t: Float): Float = dot3(vProj, cross3(C(t) - apex, T(t)))

    // ⬇️ NEJDŮLEŽITĚJŠÍ: test, jestli A' leží uvnitř/na/mimo projekci elipsy
    val sMembership = projectedEllipseMembership(apex, el, proj)
    val tol = 1e-3f
    if (sMembership.isFinite()) {
        if (sMembership < 1f - tol) {
            // A' je uvnitř elipsy → žádná reálná tečna ⇒ žádné površky
            return emptyList()
        }
    }

    val twoPi = (2f * PI).toFloat()
    val ts = FloatArray(samples) { i -> i.toFloat()/samples * twoPi }
    val ss = FloatArray(samples) { i -> s(ts[i]) }

    // sign-change seeds
    val seeds = mutableListOf<Float>()
    var lastT = ts[0]; var lastS = ss[0]
    for (i in 1 until samples) {
        val t = ts[i]; val st = ss[i]
        if (lastS == 0f) seeds += lastT
        else if (lastS * st < 0f) {
            val a = lastS / (lastS - st)
            seeds += lastT + a * (t - lastT)
            if (seeds.size >= 2) break
        }
        lastT = t; lastS = st
    }

    // fallback jen když A' je mimo (ne uvnitř); na hraně chceme 1 řešení
    val onBoundary = sMembership.isFinite() && abs(sMembership - 1f) <= tol
    if (seeds.size < (if (onBoundary) 1 else 2)) {
        // vyber minima |s| s rozestupem
        fun angDist(a: Float, b: Float): Float {
            var d = abs(a-b) % twoPi
            if (d > twoPi/2f) d = twoPi - d
            return d
        }
        val want = if (onBoundary) 1 else 2
        val order = (0 until samples).sortedBy { abs(ss[it]) }
        for (idx in order) {
            val t = ts[idx]
            if (seeds.none { angDist(it, t) < 0.4f }) seeds += t
            if (seeds.size == want) break
        }
    }

    // jemné zpřesnění (Newton)
    fun refine(t0: Float): Float {
        val twoPi = (2f * PI).toFloat()
        var t = ((t0 % twoPi) + twoPi) % twoPi
        repeat(8) {
            val h = 1e-3f
            val f = s(t)
            val df = (s(t+h) - s(t-h)) / (2*h)
            if (abs(df) < 1e-8f) return@repeat
            t = t - f/df
            t = ((t % twoPi) + twoPi) % twoPi
            if (abs(f) < 1e-5f) return t
        }
        return t
    }

    val roots = seeds.map { refine(it) }
    return roots.map { C(it) }
}

fun findConeSilhouetteOnEllipseBokorys(
    apex: Offset3D,
    el: EllipseParam,
    samples: Int = 720
): List<Offset3D> = findConeSilhouetteOnProjectedEllipse(
    apex = apex,
    el = el,
    project = { Offset(it.y, it.z) },
    samples = samples
)

fun findConeSilhouetteOnEllipseAxo(
    apex: Offset3D,
    el: EllipseParam,
    basis: monge.input.axo.AxoRenderBasis,
    samples: Int = 720
): List<Offset3D> = findConeSilhouetteOnProjectedEllipse(
    apex = apex,
    el = el,
    project = { projectPoint3DToAxoLocal(it, basis) },
    samples = samples
)

private fun findConeSilhouetteOnProjectedEllipse(
    apex: Offset3D,
    el: EllipseParam,
    project: (Offset3D) -> Offset,
    samples: Int = 720
): List<Offset3D> {
    val apex2 = project(apex)

    fun C(t: Float) = el.center3D + el.uRot * (el.a * cos(t)) +
            el.vRot * (el.b * sin(t))
    fun T(t: Float) = el.uRot * (-el.a * sin(t)) +
            el.vRot * (el.b * cos(t))
    fun det(a: Offset, b: Offset) = a.x * b.y - a.y * b.x
    fun s(t: Float): Float {
        val c2 = project(C(t))
        val t2 = project(T(t))
        return det(c2 - apex2, t2)
    }

    val membership = projectedEllipseMembership2D(apex2, el, project)
    val tol = 1e-3f
    if (membership.isFinite() && membership < 1f - tol) return emptyList()

    val twoPi = (2f * PI).toFloat()
    val ts = FloatArray(samples) { i -> i.toFloat() / samples * twoPi }
    val ss = FloatArray(samples) { i -> s(ts[i]) }

    val seeds = mutableListOf<Float>()
    var lastT = ts[0]
    var lastS = ss[0]
    for (i in 1 until samples) {
        val t = ts[i]
        val st = ss[i]
        if (lastS == 0f) seeds += lastT
        else if (lastS * st < 0f) {
            val a = lastS / (lastS - st)
            seeds += lastT + a * (t - lastT)
            if (seeds.size >= 2) break
        }
        lastT = t
        lastS = st
    }

    val onBoundary = membership.isFinite() && abs(membership - 1f) <= tol
    if (seeds.size < (if (onBoundary) 1 else 2)) {
        fun angDist(a: Float, b: Float): Float {
            var d = abs(a - b) % twoPi
            if (d > twoPi / 2f) d = twoPi - d
            return d
        }
        val want = if (onBoundary) 1 else 2
        val order = (0 until samples).sortedBy { abs(ss[it]) }
        for (idx in order) {
            val t = ts[idx]
            if (seeds.none { angDist(it, t) < 0.4f }) seeds += t
            if (seeds.size == want) break
        }
    }

    fun refine(t0: Float): Float {
        var t = ((t0 % twoPi) + twoPi) % twoPi
        repeat(8) {
            val h = 1e-3f
            val f = s(t)
            val df = (s(t + h) - s(t - h)) / (2 * h)
            if (abs(df) < 1e-8f) return@repeat
            t -= f / df
            t = ((t % twoPi) + twoPi) % twoPi
            if (abs(f) < 1e-5f) return t
        }
        return t
    }

    return seeds.map { C(refine(it)) }
}

fun rebuildConicalSilhouette2D(state: MongeState, surface: ConicalSurface3D,apex: Point3D) {
    println("rebuild cone=${surface.id} …")

    val conic3D = state.conics3D.find      { it.id == surface.directrixId } ?: return
    val el = ellipseFromConic3D(conic3D).also {
        if (it == null) {
            println("ℹ️ Directrix ${conic3D.name} není detekována jako elipsa → siluety se netvoří.")
        }
    } ?: return
    fun apexPudorysProjection(): Point3DPudorys {
        surface.apexProjPudorysId
            ?.let { id -> state.pointsPudorys.firstOrNull { it.id == id } }
            ?.let { return it }

        state.pointsPudorys.firstOrNull { it.parent?.id == apex.id }
            ?.let {
                surface.apexProjPudorysId = it.id
                return it
            }

        val fallback = Point3DPudorys(
            x = apex.x,
            y = apex.y,
            name = "",
            isSegmentEndpoint = true,
            parent = null,
            creationIndex = allocIndex(state)
        )
        state.pointsPudorys += fallback
        surface.edgePointIdsPudorys2D += fallback.id
        return fallback
    }

    fun apexNarysProjection(): Point3DNarys {
        surface.apexProjNarysId
            ?.let { id -> state.pointsNarys.firstOrNull { it.id == id } }
            ?.let { return it }

        state.pointsNarys.firstOrNull { it.parent?.id == apex.id }
            ?.let {
                surface.apexProjNarysId = it.id
                return it
            }

        val fallback = Point3DNarys(
            x = apex.x,
            z = apex.z,
            name = "",
            isSegmentEndpoint = true,
            parent = null,
            creationIndex = allocIndex(state)
        )
        state.pointsNarys += fallback
        surface.edgePointIdsNarys2D += fallback.id
        return fallback
    }
    fun apexBokorysProjection(showInAxo: Boolean): Point3DBokorys {
        surface.apexProjBokorysId
            ?.let { id -> state.pointsBokorys.firstOrNull { it.id == id } }
            ?.let { return it }

        state.pointsBokorys.firstOrNull { it.parent?.id == apex.id }
            ?.let {
                surface.apexProjBokorysId = it.id
                return it
            }

        val fallback = Point3DBokorys(
            y = apex.y,
            z = apex.z,
            name = "",
            isSegmentEndpoint = true,
            parent = null,
            creationIndex = allocIndex(state),
            showInAxoInitial = showInAxo
        )
        fallback.showInAxo = showInAxo
        state.pointsBokorys += fallback
        surface.edgePointIdsBokorys2D += fallback.id
        return fallback
    }
    fun apexAxoProjection(basis: monge.input.axo.AxoRenderBasis): Point3DAxo {
        surface.apexProjAxoId
            ?.let { id -> state.pointsAxo.firstOrNull { it.id == id } }
            ?.let { return it }

        state.pointsAxo.firstOrNull { it.parent?.id == apex.id }
            ?.let {
                surface.apexProjAxoId = it.id
                return it
            }

        val p = projectPoint3DToAxoLocal(Offset3D(apex.x, apex.y, apex.z), basis)
        val fallback = Point3DAxo(
            x = p.x,
            y = p.y,
            name = "",
            isSegmentEndpoint = true,
            parent = null,
            creationIndex = allocIndex(state),
            showInAxoInitial = true
        )
        state.pointsAxo += fallback
        surface.edgePointIdsAxo2D += fallback.id
        return fallback
    }
    // 1) Smazat staré 2D segmenty a jejich endpointy (jen ty, které si kužel založil)
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
    if (surface.edgeSegIdsAxo2D.isNotEmpty()) {
        state.segmentsAxo.removeAll { it.id in surface.edgeSegIdsAxo2D }
        surface.edgeSegIdsAxo2D.clear()
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
    if (surface.edgePointIdsAxo2D.isNotEmpty()) {
        state.pointsAxo.removeAll { it.id in surface.edgePointIdsAxo2D }
        surface.edgePointIdsAxo2D.clear()
    }

    // 2) Spočítat 3D dotykové body (siluety) pro oba pohledy
    val tangentEndsP = mutableListOf<Offset>() // (x, y)
    val tangentEndsN = mutableListOf<Offset>() // (x, z)
    // Helpery: vytvoř 2D endpoint tak, aby se mazal se segmentem
    fun makePudorysEndpoint(x: Float, y: Float): Point3DPudorys {
        val p = Point3DPudorys(
            x = x, y = y,
            name = "",                      // volitelně
            isSegmentEndpoint = true,       // ⟵ důležité pro tvoji delete logiku
            parent = null, creationIndex = allocIndex(state)
        )
        state.pointsPudorys += p
        surface.edgePointIdsPudorys2D += p.id
        return p
    }
    fun makeNarysEndpoint(x: Float, z: Float): Point3DNarys {
        val p = Point3DNarys(
            x = x, z = z,
            name = "",
            isSegmentEndpoint = true,
            parent = null, creationIndex = allocIndex(state)
        )
        state.pointsNarys += p
        surface.edgePointIdsNarys2D += p.id
        return p
    }
    fun makeBokorysEndpoint(y: Float, z: Float, showInAxo: Boolean): Point3DBokorys {
        val p = Point3DBokorys(
            y = y,
            z = z,
            name = "",
            isSegmentEndpoint = true,
            parent = null,
            creationIndex = allocIndex(state),
            showInAxoInitial = showInAxo
        )
        p.showInAxo = showInAxo
        state.pointsBokorys += p
        surface.edgePointIdsBokorys2D += p.id
        return p
    }
    fun makeAxoEndpoint(c3: Offset3D, basis: monge.input.axo.AxoRenderBasis): Point3DAxo {
        val a = projectPoint3DToAxoLocal(c3, basis)
        val p = Point3DAxo(
            x = a.x,
            y = a.y,
            name = "",
            isSegmentEndpoint = true,
            parent = null,
            creationIndex = allocIndex(state),
            showInAxoInitial = true
        )
        state.pointsAxo += p
        surface.edgePointIdsAxo2D += p.id
        return p
    }

    // --- PŮDORYS ---
    val A3 = Offset3D(apex.x, apex.y, apex.z)
    val edgesP = findConeSilhouetteOnEllipse(A3, el, ProjectionAxis.PUDORYS)
    if (edgesP.isNotEmpty()) {
        val start = apexPudorysProjection()
        edgesP.forEachIndexed { i, c3 ->
            val end   = makePudorysEndpoint(c3.x, c3.y)

            val seg = Segment2DPudorys(
                start = start,
                end = end,
                name = "",
                parent = null,
                localColor = surface.color,
                localLineStyle = LineStyle.Solid,
                localStrokeWidth = surface.wireWidth,
                isConicalSilhouette = true,
                conicalSurfaceId = surface.id,
                creationIndex = allocIndex(state)
            )

            start.parentSegment = seg
            end.parentSegment = seg

            state.segmentsPudorys += seg
            surface.edgeSegIdsPudorys2D += seg.id

            tangentEndsP += Offset(end.x, end.y)
        }
    }

    // --- NÁRYS ---
    val edgesN = findConeSilhouetteOnEllipse(A3, el, ProjectionAxis.NARYS)
    if (edgesN.isNotEmpty()) {
        val start = apexNarysProjection()
        edgesN.forEachIndexed { i, c3 ->
            val end   = makeNarysEndpoint(c3.x, c3.z)

            val seg = Segment2DNarys(
                start = start,
                end = end,
                name = "",
                parent = null,
                localColor = surface.color,
                localLineStyle = LineStyle.Solid,
                localStrokeWidth = surface.wireWidth,
                isConicalSilhouette = true,
                conicalSurfaceId = surface.id,
                creationIndex = allocIndex(state)
            )

            start.parentSegment = seg
            end.parentSegment = seg

            state.segmentsNarys += seg
            surface.edgeSegIdsNarys2D += seg.id

            tangentEndsN += Offset(end.x, -end.z)
        }
    }

    // --- BOKORYS ---
    val edgesB = findConeSilhouetteOnEllipseBokorys(A3, el)
    if (edgesB.isNotEmpty()) {
        val start = apexBokorysProjection(showInAxo = false)
        edgesB.forEachIndexed { i, c3 ->
            val end = makeBokorysEndpoint(c3.y, c3.z, showInAxo = false)

            val seg = Segment2DBokorys(
                start = start,
                end = end,
                name = "",
                parent = null,
                localColor = surface.color,
                localLineStyle = LineStyle.Solid,
                localStrokeWidth = surface.wireWidth,
                isConicalSilhouette = true,
                conicalSurfaceId = surface.id,
                showInAxoInitial = false,
                creationIndex = allocIndex(state)
            )
            seg.showInAxo = false

            start.parentSegment = seg
            end.parentSegment = seg

            state.segmentsBokorys += seg
            surface.edgeSegIdsBokorys2D += seg.id
        }
    }

    // --- AXO ---
    val tangentEndsA = mutableListOf<Offset>()
    val basis = state.basis
    if (basis != null) {
        val edgesA = findConeSilhouetteOnEllipseAxo(A3, el, basis)
        if (edgesA.isNotEmpty()) {
            val start = apexAxoProjection(basis)
            edgesA.forEachIndexed { i, c3 ->
                val end = makeAxoEndpoint(c3, basis)

                val seg = Segment2DAxo(
                    start = start,
                    end = end,
                    name = "",
                    parent = null,
                    localColor = surface.color,
                    localLineStyle = LineStyle.Solid,
                    localStrokeWidth = surface.wireWidth,
                    isConicalSilhouette = true,
                    conicalSurfaceId = surface.id,
                    showInAxoInitial = true,
                    creationIndex = allocIndex(state)
                )

                start.parentSegment = seg
                end.parentSegment = seg

                state.segmentsAxo += seg
                surface.edgeSegIdsAxo2D += seg.id

                tangentEndsA += projectPoint3DToAxoLocal(c3, basis)
            }
        }
    }
    // Pomocná: signum s deadzonou
   fun sgn(x: Float, eps: Float = 1e-5f): Int =
        when {
            x > eps  -> 1
            x < -eps -> -1
            else     -> 0
        }
    fun norm3(a: Offset3D): Float = sqrt(a.x*a.x + a.y*a.y + a.z*a.z)
    fun normalize3(a: Offset3D): Offset3D {
        val n = norm3(a); return if (n > 1e-12f) Offset3D(a.x/n, a.y/n, a.z/n) else Offset3D(0f,0f,0f)
    }

    fun ellipsePlaneNormal(el: EllipseParam): Offset3D =
        normalize3(cross3(el.uRot, el.vRot))

// Z roviny directrixu si vem bod P0 a jednotkovou normálu n
    ellipsePlaneNormal(el)      // přidej si k ellipseFromConic3D i plane/equation
    val P0 = el.center3D             // např. el.center3D nebo bod z přímky stopy
    val n  = ellipsePlaneNormal(el)          // normalizovaná normála

    val vProjP = Offset3D(0f, 0f, 1f)
    val vProjN = Offset3D(0f, 1f, 0f)

    val sP = dot3(n, A3 - P0)
    val tP = dot3(n, vProjP)
    val sN = dot3(n, A3 - P0)             // stejné s, jiný t
    val tN = dot3(n, vProjN)

    val sideP = sgn(sP * tP)              // -1 → apex za rovinou, +1 → před
    val sideN = sgn(sN * tN)

    val sideA = if (basis != null) {
        val vProjA = Offset3D(
            basis.ey.x * basis.ez.y - basis.ez.x * basis.ey.y,
            basis.ez.x * basis.ex.y - basis.ex.x * basis.ez.y,
            basis.ex.x * basis.ey.y - basis.ey.x * basis.ex.y
        )
        sgn(sP * dot3(n, vProjA))
    } else 0

// ── PŮDORYS ──
    state.conicsPudorys.firstOrNull {
        it.parent?.id == surface.directrixId || it.parentId == surface.directrixId
    }?.let { c2d ->
        when (sideP) {
            -1 -> { // apex ZA → celá elipsa
                state.ellipseArcEnds.remove(c2d.id)
                state.ellipseArcMode.remove(c2d.id)
            }
            +1 -> { // apex PŘED → výřez mezi tečnami
                if (tangentEndsP.size >= 2) {
                    // obvykle je viditelný "vzdálenější" (kratší) oblouk
                    state.setEllipseArc(c2d.id, tangentEndsP[0], tangentEndsP[1], ArcMode.LONGEST)
                } else {
                    state.ellipseArcEnds.remove(c2d.id)
                    state.ellipseArcMode.remove(c2d.id)
                }
            }
            else -> { // téměř hrana pohledu → fallback
                if (tangentEndsP.size >= 2) {
                    state.setEllipseArc(c2d.id, tangentEndsP[0], tangentEndsP[1], ArcMode.LONGEST)
                } else {
                    state.ellipseArcEnds.remove(c2d.id)
                    state.ellipseArcMode.remove(c2d.id)
                }
            }
        }
    }

// ── NÁRYS ──
    state.conicsNarys.firstOrNull {
        it.parent?.id == surface.directrixId || it.parentId == surface.directrixId
    }?.let { c2d ->
        when (sideN) {
            -1 -> {
                state.ellipseArcEnds.remove(c2d.id)
                state.ellipseArcMode.remove(c2d.id)
            }
            +1 -> {
                if (tangentEndsN.size >= 2) {
                    state.setEllipseArc(c2d.id, tangentEndsN[0], tangentEndsN[1], ArcMode.LONGEST)
                } else {
                    state.ellipseArcEnds.remove(c2d.id)
                    state.ellipseArcMode.remove(c2d.id)
                }
            }
            else -> {
                if (tangentEndsN.size >= 2) {
                    state.setEllipseArc(c2d.id, tangentEndsN[0], tangentEndsN[1], ArcMode.LONGEST)
                } else {
                    state.ellipseArcEnds.remove(c2d.id)
                    state.ellipseArcMode.remove(c2d.id)
                }
            }
        }
    }

// ── AXO ──
    state.conicsAxo.firstOrNull {
        it.parent?.id == surface.directrixId || it.parentId == surface.directrixId
    }?.let { c2d ->
        when (sideA) {
            -1 -> {
                state.ellipseArcEnds.remove(c2d.id)
                state.ellipseArcMode.remove(c2d.id)
            }
            +1 -> {
                if (tangentEndsA.size >= 2) {
                    state.setEllipseArc(c2d.id, tangentEndsA[0], tangentEndsA[1], ArcMode.LONGEST)
                } else {
                    state.ellipseArcEnds.remove(c2d.id)
                    state.ellipseArcMode.remove(c2d.id)
                }
            }
            else -> {
                if (tangentEndsA.size >= 2) {
                    state.setEllipseArc(c2d.id, tangentEndsA[0], tangentEndsA[1], ArcMode.LONGEST)
                } else {
                    state.ellipseArcEnds.remove(c2d.id)
                    state.ellipseArcMode.remove(c2d.id)
                }
            }
        }
    }

    println("edgesP=${edgesP.size}, edgesN=${edgesN.size}")
    state.triggerRedraw++
}

fun rebuildAllConicalSilhouettesAxo(state: MongeState) {
    state.conicalSurfaces.forEach { surface ->
        val apex = state.sharedPoints3D.firstOrNull { it.id == surface.apexId } ?: return@forEach
        rebuildConicalSilhouetteAxo(state, surface, apex)
    }
}

fun rebuildConicalSurfaceForAxoConversion(state: MongeState, surface: ConicalSurface3D) {
    val apex = state.sharedPoints3D.firstOrNull { it.id == surface.apexId } ?: return
    val directrix = state.conics3D.firstOrNull { it.id == surface.directrixId } ?: return

    rebuildConicalSilhouette2D(state, surface, apex)
    hideConeNonAxoProjectionsAfterAxoConstruction(state, surface, apex, directrix)
}

fun rebuildConicalSilhouetteAxo(state: MongeState, surface: ConicalSurface3D, apex: Point3D) {
    val basis = state.basis ?: return
    val conic3D = state.conics3D.firstOrNull { it.id == surface.directrixId } ?: return
    val el = ellipseFromConic3D(conic3D) ?: return

    if (surface.edgeSegIdsAxo2D.isNotEmpty()) {
        state.segmentsAxo.removeAll { it.id in surface.edgeSegIdsAxo2D }
        surface.edgeSegIdsAxo2D.clear()
    }
    if (surface.edgePointIdsAxo2D.isNotEmpty()) {
        state.pointsAxo.removeAll { it.id in surface.edgePointIdsAxo2D }
        surface.edgePointIdsAxo2D.clear()
    }

    fun apexAxoProjection(): Point3DAxo {
        surface.apexProjAxoId
            ?.let { id -> state.pointsAxo.firstOrNull { it.id == id } }
            ?.let { return it }

        state.pointsAxo.firstOrNull { it.parent?.id == apex.id }
            ?.let {
                surface.apexProjAxoId = it.id
                it.showInAxoInitial = true
                it.showInAxo = true
                return it
            }

        val p = projectPoint3DToAxoLocal(Offset3D(apex.x, apex.y, apex.z), basis)
        val fallback = Point3DAxo(
            x = p.x,
            y = p.y,
            name = "",
            isSegmentEndpoint = true,
            parent = null,
            creationIndex = allocIndex(state),
            showInAxoInitial = true
        )
        fallback.showInAxo = true
        state.pointsAxo += fallback
        surface.apexProjAxoId = fallback.id
        surface.edgePointIdsAxo2D += fallback.id
        return fallback
    }

    fun makeAxoEndpoint(c3: Offset3D): Point3DAxo {
        val a = projectPoint3DToAxoLocal(c3, basis)
        val point = Point3DAxo(
            x = a.x,
            y = a.y,
            name = "",
            isSegmentEndpoint = true,
            parent = null,
            creationIndex = allocIndex(state),
            showInAxoInitial = true
        )
        point.showInAxo = true
        state.pointsAxo += point
        surface.edgePointIdsAxo2D += point.id
        return point
    }

    val apex3D = Offset3D(apex.x, apex.y, apex.z)
    val edges = findConeSilhouetteOnEllipseAxo(apex3D, el, basis)
    val tangentEndsA = mutableListOf<Offset>()

    if (edges.isNotEmpty()) {
        val start = apexAxoProjection()
        edges.forEachIndexed { index, tangentPoint ->
            val end = makeAxoEndpoint(tangentPoint)
            val segment = Segment2DAxo(
                start = start,
                end = end,
                name = "",
                parent = null,
                localColor = surface.color,
                localLineStyle = LineStyle.Solid,
                localStrokeWidth = surface.wireWidth,
                isConicalSilhouette = true,
                conicalSurfaceId = surface.id,
                showInAxoInitial = true,
                creationIndex = allocIndex(state)
            )
            segment.showInAxo = true

            start.parentSegment = segment
            end.parentSegment = segment

            state.segmentsAxo += segment
            surface.edgeSegIdsAxo2D += segment.id

            tangentEndsA += projectPoint3DToAxoLocal(tangentPoint, basis)
        }
    }

    val n = run {
        val uRot = el.uRot; val vRot = el.vRot
        val raw = Offset3D(
            uRot.y * vRot.z - uRot.z * vRot.y,
            uRot.z * vRot.x - uRot.x * vRot.z,
            uRot.x * vRot.y - uRot.y * vRot.x
        )
        val len = sqrt(raw.x * raw.x + raw.y * raw.y + raw.z * raw.z)
        if (len > 1e-12f) Offset3D(raw.x / len, raw.y / len, raw.z / len) else Offset3D(0f, 0f, 0f)
    }
    val sP = n.x * (apex3D.x - el.center3D.x) + n.y * (apex3D.y - el.center3D.y) + n.z * (apex3D.z - el.center3D.z)
    val vProjA = Offset3D(
        basis.ey.x * basis.ez.y - basis.ez.x * basis.ey.y,
        basis.ez.x * basis.ex.y - basis.ex.x * basis.ez.y,
        basis.ex.x * basis.ey.y - basis.ey.x * basis.ex.y
    )
    val tA = n.x * vProjA.x + n.y * vProjA.y + n.z * vProjA.z
    val sideA = run {
        val v = sP * tA
        when {
            v > 1e-5f -> 1
            v < -1e-5f -> -1
            else -> 0
        }
    }

    state.conicsAxo.firstOrNull {
        it.parent?.id == surface.directrixId || it.parentId == surface.directrixId
    }?.let { c2d ->
        when (sideA) {
            -1 -> {
                state.ellipseArcEnds.remove(c2d.id)
                state.ellipseArcMode.remove(c2d.id)
            }
            +1 -> {
                if (tangentEndsA.size >= 2) {
                    state.setEllipseArc(c2d.id, tangentEndsA[0], tangentEndsA[1], ArcMode.LONGEST)
                } else {
                    state.ellipseArcEnds.remove(c2d.id)
                    state.ellipseArcMode.remove(c2d.id)
                }
            }
            else -> {
                if (tangentEndsA.size >= 2) {
                    state.setEllipseArc(c2d.id, tangentEndsA[0], tangentEndsA[1], ArcMode.LONGEST)
                } else {
                    state.ellipseArcEnds.remove(c2d.id)
                    state.ellipseArcMode.remove(c2d.id)
                }
            }
        }
    }

    state.triggerRedraw++
}

fun hideConeNonAxoProjectionsAfterAxoConstruction(
    state: MongeState,
    surface: ConicalSurface3D,
    apex: Point3D,
    directrix: ConicSection3D
) {
    fun hidePointP(p: Point3DPudorys) { p.showInAxoInitial = false; p.showInAxo = false }
    fun hidePointN(p: Point3DNarys) { p.showInAxoInitial = false; p.showInAxo = false }
    fun hidePointB(p: Point3DBokorys) { p.showInAxoInitial = false; p.showInAxo = false }
    fun showPointA(p: Point3DAxo) { p.showInAxoInitial = true; p.showInAxo = true }
    fun hideSegmentP(s: Segment2DPudorys) { s.showInAxoInitial = false; s.showInAxo = false }
    fun hideSegmentN(s: Segment2DNarys) { s.showInAxoInitial = false; s.showInAxo = false }
    fun hideSegmentB(s: Segment2DBokorys) { s.showInAxoInitial = false; s.showInAxo = false }

    state.pointsPudorys.filter { it.parent?.id == apex.id || it.id in surface.edgePointIdsPudorys2D }.forEach(::hidePointP)
    state.pointsNarys.filter { it.parent?.id == apex.id || it.id in surface.edgePointIdsNarys2D }.forEach(::hidePointN)
    state.pointsBokorys.filter { it.parent?.id == apex.id || it.id in surface.edgePointIdsBokorys2D }.forEach(::hidePointB)
    state.pointsAxo.filter { it.parent?.id == apex.id || it.id in surface.edgePointIdsAxo2D }.forEach(::showPointA)

    state.segmentsPudorys.filter { it.id in surface.edgeSegIdsPudorys2D }.forEach(::hideSegmentP)
    state.segmentsNarys.filter { it.id in surface.edgeSegIdsNarys2D }.forEach(::hideSegmentN)
    state.segmentsBokorys.filter { it.id in surface.edgeSegIdsBokorys2D }.forEach(::hideSegmentB)
    state.segmentsAxo.filter { it.id in surface.edgeSegIdsAxo2D }.forEach {
        it.showInAxoInitial = true
        it.showInAxo = true
    }

    state.conicsPudorys.filter { it.parent?.id == directrix.id || it.parentId == directrix.id }.forEach {
        it.showInAxoInitial = false
        it.showInAxo = false
    }
    state.conicsNarys.filter { it.parent?.id == directrix.id || it.parentId == directrix.id }.forEach {
        it.showInAxoInitial = false
        it.showInAxo = false
    }
    state.conicsBokorys.filter { it.parent?.id == directrix.id || it.parentId == directrix.id }.forEach {
        it.showInAxoInitial = false
        it.showInAxo = false
    }
    state.conicsAxo.filter { it.parent?.id == directrix.id || it.parentId == directrix.id }.forEach {
        it.showInAxoInitial = true
        it.showInAxo = true
    }
}

private data class V2(val x: Float, val y: Float)
private fun proj2(p: Offset3D, proj: ProjectionAxis) =
    if (proj == ProjectionAxis.PUDORYS) V2(p.x, p.y) else V2(p.x, p.z)

private fun det2(a: V2, b: V2) = a.x*b.y - a.y*b.x
private fun sub(a: V2, b: V2) = V2(a.x - b.x, a.y - b.y)

private fun projectedEllipseMembership(apex: Offset3D, el: EllipseParam, proj: ProjectionAxis, eps: Float = 1e-6f): Float {
    val A = proj2(apex, proj)
    val C = proj2(el.center3D, proj)
    val U = proj2(el.uRot, proj)
    val V = proj2(el.vRot, proj)
    val r = sub(A, C)
    val det = det2(U, V)
    if (abs(det) < eps) return Float.NaN // degenerovaná projekce (elipsa -> úsečka)
    // rozklad r = U*q0 + V*q1
    val q0 = ( r.x*V.y - r.y*V.x) / det
    val q1 = (-r.x*U.y + r.y*U.x) / det
    // elipsa: (q0/a)^2 + (q1/b)^2 = 1
    return (q0/el.a)*(q0/el.a) + (q1/el.b)*(q1/el.b)
}

private fun projectedEllipseMembership2D(
    apex: Offset,
    el: EllipseParam,
    project: (Offset3D) -> Offset,
    eps: Float = 1e-6f
): Float {
    val center = project(el.center3D)
    val u = project(el.center3D + el.uRot) - center
    val v = project(el.center3D + el.vRot) - center
    val r = apex - center
    val det = u.x * v.y - u.y * v.x
    if (abs(det) < eps) return Float.NaN
    val q0 = (r.x * v.y - r.y * v.x) / det
    val q1 = (-r.x * u.y + r.y * u.x) / det
    return (q0 / el.a) * (q0 / el.a) + (q1 / el.b) * (q1 / el.b)
}
