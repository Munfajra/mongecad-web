package monge.input.intersections.ops

import geometry.asinhF
import geometry.hyperbolaPointAt
import geometry.orthoBasis
import androidx.compose.ui.geometry.Offset
import model.ArcMode
import model.Offset3D
import model.classes.ConicSection3D
import model.normalize
import model.classes.projectPoint3DToAxoLocal
import monge.input.ConicArcs.associated.findAxoConicIdByParent
import monge.input.ConicArcs.associated.findBokorysConicIdByParent
import monge.input.ConicArcs.associated.findNarysConicIdByParent
import monge.input.ConicArcs.associated.findPudorysConicIdByParent
import monge.input.ConicArcs.single.ellipseBasisFromDiameters
import monge.input.ConicArcs.single.ellipseParamAndProjection
import monge.input.ConicArcs.single.projectToEllipseFromDiameters
import monge.input.ConicArcs.associated.rebuildHyperbolaArcBranchesForAxo
import monge.input.ConicArcs.associated.rebuildParabolaArcForAxo
import monge.input.intersections.addIntersectionConic3D
import geometry.conics.computeEllipseAxes3D
import geometry.conics.cosh
import geometry.conics.sinh
import state.MongeState
import utils.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Oříznutí kuželosečky (elipsy / kružnice) na hranici jedné stěny Solidu.
 *
 * Kuželosečka vznikla řezem plochy (koule/kužel/válec) rovinou stěny, takže leží
 * přesně v rovině té stěny. Oblouky, které leží uvnitř stěnového n-úhelníku,
 * určíme hustým navzorkováním parametru t a testem bod-v-polygonu; výsledkem jsou
 * parametrické intervaly (t1, t2). Protože "jeden průnik = několik kuželoseček",
 * z každého intervalu vznikne samostatná [ConicSection3D] s vlastním obloukem.
 */

private const val TWO_PI = (2.0 * PI).toFloat()
private const val PI_F = PI.toFloat()
private const val CLIP_SAMPLES = 1080

/** Bod na elipse pro parametr t – konzistentní s [opengl.model] vykreslováním oblouků. */
private fun ellipsePointAt(conic: ConicSection3D, t: Float, axesU: Offset3D, axesV: Offset3D, a: Float, b: Float): Offset3D =
    conic.p0 + axesU * (a * cos(t)) + axesV * (b * sin(t))

/**
 * Vrátí parametrické intervaly (t1, t2) oblouků elipsy/kružnice [conic], které leží
 * uvnitř stěnového polygonu [face3D]. Prázdné = elipsa stěnu míjí. Jeden interval
 * (−π, π) = celá elipsa leží uvnitř stěny.
 */
internal fun clipEllipseConicToFace(conic: ConicSection3D, face3D: List<Offset3D>): List<Pair<Float, Float>> {
    if (face3D.size < 3) return emptyList()
    // 2D souřadnice v rovině stěny – bázi bereme z koniky (p0, u, v), aby projekce
    // stěnových vrcholů i vzorků elipsy seděla do téže roviny.
    val p0 = conic.p0; val cu = conic.u; val cv = conic.v
    fun proj(p: Offset3D): Offset { val r = p - p0; return Offset(r dot cu, r dot cv) }
    val poly2D = face3D.map { proj(it) }

    return clipEllipseConicByPredicate(conic) { p ->
        pointInPolygon2D(proj(p), poly2D)
    }
}

/**
 * Obecný ořez elipsy podle prostorového predikátu. Vrací parametrické intervaly
 * částí elipsy, pro které [inside] platí. Používá se i pro ořez řezu válce podstavami.
 */
internal fun clipEllipseConicByPredicate(
    conic: ConicSection3D,
    samples: Int = CLIP_SAMPLES,
    inside: (Offset3D) -> Boolean
): List<Pair<Float, Float>> {
    val axes = runCatching { computeEllipseAxes3D(conic) }.getOrNull() ?: return emptyList()
    val au = axes.uRotated.normalize()
    val av = axes.vRotated.normalize()
    val a = axes.a
    val b = axes.b
    if (!a.isFinite() || !b.isFinite() || a < 1e-6f || b < 1e-6f) return emptyList()

    fun insideAt(t: Float): Boolean =
        inside(ellipsePointAt(conic, t, au, av, a, b))

    val sampleCount = samples.coerceAtLeast(CLIP_SAMPLES)
    val insideSamples = BooleanArray(sampleCount) { i -> insideAt(-PI_F + TWO_PI * i / sampleCount) }
    if (insideSamples.all { it }) return listOf(-PI_F to PI_F)
    if (insideSamples.none { it }) return emptyList()

    fun contT(idx: Int): Float = -PI_F + TWO_PI * idx / sampleCount

    // začni na vzorku mimo polygon → žádný běh nepřekročí "šev" iterace
    val start = (0 until sampleCount).first { !insideSamples[it] }
    val runs = mutableListOf<Pair<Float, Float>>()
    var idx = start
    val end = start + sampleCount
    while (idx < end) {
        if (insideSamples[idx % sampleCount]) {
            val runStart = idx
            while (idx < end && insideSamples[idx % sampleCount]) idx++
            val runEnd = idx - 1
            // upřesni hranice oblouku na skutečné průsečíky s hranou stěny
            val tStart = refineBoundary(contT(runStart - 1), contT(runStart), ::insideAt)
            val tEnd = refineBoundary(contT(runEnd + 1), contT(runEnd), ::insideAt)
            if (tEnd - tStart > 1e-3f) runs.add(tStart to tEnd)
        } else idx++
    }
    return runs
}

/**
 * Obecné oříznutí parametrické křivky na stěnu pro NEcyklické větve (parabola, jedna
 * větev hyperboly). Vzorkuje t ∈ [tStart, tEnd], testuje bod-v-polygonu a vrací běhy
 * "uvnitř" s upřesněnými hranicemi. Projekce do roviny stěny přes bázi (projU, projV).
 */
private fun paramCurveRuns(
    face3D: List<Offset3D>,
    projP0: Offset3D, projU: Offset3D, projV: Offset3D,
    tStart: Float, tEnd: Float, samples: Int,
    pointAt: (Float) -> Offset3D,
): List<Pair<Float, Float>> {
    if (face3D.size < 3 || tEnd <= tStart) return emptyList()
    fun proj(p: Offset3D): Offset { val r = p - projP0; return Offset(r dot projU, r dot projV) }
    val poly2D = face3D.map { proj(it) }
    fun insideAt(t: Float): Boolean = pointInPolygon2D(proj(pointAt(t)), poly2D)
    fun tAt(i: Int): Float = tStart + (tEnd - tStart) * i / samples

    val inside = BooleanArray(samples + 1) { i -> insideAt(tAt(i)) }
    if (inside.none { it }) return emptyList()

    val runs = mutableListOf<Pair<Float, Float>>()
    var i = 0
    while (i <= samples) {
        if (inside[i]) {
            val rs = i
            while (i <= samples && inside[i]) i++
            val re = i - 1
            val a = if (rs > 0) refineBoundary(tAt(rs - 1), tAt(rs), ::insideAt) else tAt(rs)
            val b = if (re < samples) refineBoundary(tAt(re + 1), tAt(re), ::insideAt) else tAt(re)
            if (b - a > 1e-4f * (tEnd - tStart)) runs.add(a to b)
        } else i++
    }
    return runs
}

private fun paramCurveRunsByPredicate(
    tStart: Float,
    tEnd: Float,
    samples: Int,
    insideAt: (Float) -> Boolean,
): List<Pair<Float, Float>> {
    if (tEnd <= tStart) return emptyList()
    fun tAt(i: Int): Float = tStart + (tEnd - tStart) * i / samples

    val inside = BooleanArray(samples + 1) { i -> insideAt(tAt(i)) }
    if (inside.none { it }) return emptyList()

    val runs = mutableListOf<Pair<Float, Float>>()
    var i = 0
    while (i <= samples) {
        if (inside[i]) {
            val rs = i
            while (i <= samples && inside[i]) i++
            val re = i - 1
            val a = if (rs > 0) refineBoundary(tAt(rs - 1), tAt(rs), insideAt) else tAt(rs)
            val b = if (re < samples) refineBoundary(tAt(re + 1), tAt(re), insideAt) else tAt(re)
            if (b - a > 1e-4f * (tEnd - tStart)) runs.add(a to b)
        } else i++
    }
    return runs
}



private fun parabolaFocal(conic: ConicSection3D): Float {
    val a = conic.matrix.m00
    return if (abs(a) > 1e-5f) 1f / (4f * a) else 25f
}

private fun parabolaPointAt(conic: ConicSection3D, t: Float, uN: Offset3D, vN: Offset3D, focal: Float): Offset3D {
    val y = t * t / (4f * focal)
    return conic.p0 + uN * t + vN * y
}


/**
 * Parabolický řez stěny → oblouky paraboly oříznuté na n-úhelník. Každý běh = samostatná
 * parabola (přes [monge.input.intersections.addIntersectionParabola3D]) s nastaveným rozsahem.
 */
internal fun addClippedParabolaArcs(state: MongeState, template: ConicSection3D, face3D: List<Offset3D>) {
    val (uN, vN) = orthoBasis(template)
    val focal = parabolaFocal(template)
    val xs = face3D.map { (it - template.p0) dot uN }
    val span = (xs.max() - xs.min()).coerceAtLeast(1f)
    val margin = 0.02f * span
    val runs = paramCurveRuns(
        face3D, template.p0, template.u, template.v,
        xs.min() - margin, xs.max() + margin, samples = 720,
    ) { t -> parabolaPointAt(template, t, uN, vN, focal) }

    for ((t1, t2) in runs) {
        val conic = addIntersectionParabola3D(state, template.copy(id = UUID.randomUUID().toString())) ?: continue
        val (cuN, cvN) = orthoBasis(conic)
        val cf = parabolaFocal(conic)
        val a3D = parabolaPointAt(conic, t1, cuN, cvN, cf)
        val b3D = parabolaPointAt(conic, t2, cuN, cvN, cf)
        // 3D konce → rebuild nastaví parametry i 2D konce ve všech průmětech (snap, správná konvence)
        state.parabolaArcEnds3D[conic.id] = a3D to b3D
        rebuildParabolaArcForAxo(state, conic)
    }
}

/**
 * Parabolický řez oříznutý prostorovým predikátem. Parametrický interval musí zvenku
 * obklopit očekávaný průnik; typicky se bere z průniku řezové roviny s podstavou.
 */
internal fun addClippedParabolaArcsByPredicate(
    state: MongeState,
    template: ConicSection3D,
    tStart: Float,
    tEnd: Float,
    inside: (Offset3D) -> Boolean
): Int {
    val (uN, vN) = orthoBasis(template)
    val focal = parabolaFocal(template)
    val runs = paramCurveRunsByPredicate(
        tStart = tStart,
        tEnd = tEnd,
        samples = 720,
    ) { t -> inside(parabolaPointAt(template, t, uN, vN, focal)) }

    var added = 0
    for ((t1, t2) in runs) {
        val conic = addIntersectionParabola3D(state, template.copy(id = UUID.randomUUID().toString())) ?: continue
        val (cuN, cvN) = orthoBasis(conic)
        val cf = parabolaFocal(conic)
        val a3D = parabolaPointAt(conic, t1, cuN, cvN, cf)
        val b3D = parabolaPointAt(conic, t2, cuN, cvN, cf)
        state.parabolaArcParams3D[conic.id] = t1 to t2
        state.parabolaArcEnds3D[conic.id] = a3D to b3D
        rebuildParabolaArcForAxo(state, conic)
        added++
    }
    return added
}

/**
 * Hyperbolický řez stěny → oblouky hyperboly oříznuté na n-úhelník (každá větev zvlášť).
 * Každý běh = samostatná hyperbola s jedinou nastavenou větví.
 */
internal fun addClippedHyperbolaArcs(state: MongeState, template: ConicSection3D, face3D: List<Offset3D>) {
    val a = template.a ?: return
    val b = template.b ?: return
    val (uN, vN) = orthoBasis(template)
    val ys = face3D.map { (it - template.p0) dot vN }
    val tLo = asinhF(ys.min() / b)
    val tHi = asinhF(ys.max() / b)
    if (tHi <= tLo) return
    val margin = 0.02f * (tHi - tLo)

    for (sign in listOf(1f, -1f)) {
        val runs = paramCurveRuns(
            face3D, template.p0, template.u, template.v,
            tLo - margin, tHi + margin, samples = 720,
        ) { t -> hyperbolaPointAt(template, t, sign, uN, vN, a, b) }

        for ((t1, t2) in runs) {
            val conic = addIntersectionHyperbola3D(state, template.copy(id = UUID.randomUUID().toString()))
            val ca = conic.a ?: a
            val cb = conic.b ?: b
            val (cuN, cvN) = orthoBasis(conic)
            val a3D = hyperbolaPointAt(conic, t1, sign, cuN, cvN, ca, cb)
            val b3D = hyperbolaPointAt(conic, t2, sign, cuN, cvN, ca, cb)
            // 3D konce jedné větve → rebuild nastaví parametry i 2D větve (snap, správná konvence narysu)
            state.hyperbolaArcEnds3D[conic.id] = (a3D to b3D) to null
            rebuildHyperbolaArcBranchesForAxo(state, conic)
        }
    }
}

/**
 * Hyperbolický řez oříznutý prostorovým predikátem (např. konečný kužel). Při řezu jedné
 * poloviny dvojkužele zůstává viditelná jen jedna větev a jen po podstavu – tu vymezí
 * [inside]. Parametrický rozsah odhadneme z mezních bodů (průnik řezové roviny s podstavou)
 * doplněných o vrchol hyperboly (t = 0); přesné konce dořeže predikát. Obě znaménka větve
 * zkusíme, validní běhy vrátí jen ta správná. Vrací počet vytvořených oblouků.
 */
internal fun addClippedHyperbolaArcsByPredicate(
    state: MongeState,
    template: ConicSection3D,
    limitPoints: List<Offset3D>,
    inside: (Offset3D) -> Boolean,
): Int {
    val a = template.a ?: return 0
    val b = template.b ?: return 0
    val (uN, vN) = orthoBasis(template)
    val ts = limitPoints.map { asinhF(((it - template.p0) dot vN) / b) } + 0f
    val tLo = ts.min()
    val tHi = ts.max()
    val span = (tHi - tLo).coerceAtLeast(1f)
    val tStart = tLo - 0.1f * span
    val tEnd = tHi + 0.1f * span

    var added = 0
    for (sign in listOf(1f, -1f)) {
        val runs = paramCurveRunsByPredicate(tStart, tEnd, samples = 720) { t ->
            inside(hyperbolaPointAt(template, t, sign, uN, vN, a, b))
        }
        for ((t1, t2) in runs) {
            val conic = addIntersectionHyperbola3D(state, template.copy(id = UUID.randomUUID().toString()))
            val ca = conic.a ?: a
            val cb = conic.b ?: b
            val (cuN, cvN) = orthoBasis(conic)
            val a3D = hyperbolaPointAt(conic, t1, sign, cuN, cvN, ca, cb)
            val b3D = hyperbolaPointAt(conic, t2, sign, cuN, cvN, ca, cb)
            state.hyperbolaArcEnds3D[conic.id] = (a3D to b3D) to null
            rebuildHyperbolaArcBranchesForAxo(state, conic)
            added++
        }
    }
    return added
}


/** Bisekce hranice mezi tOut (mimo) a tIn (uvnitř) → t na samé hranici stěny. */
private fun refineBoundary(tOut: Float, tIn: Float, inside: (Float) -> Boolean): Float {
    var lo = tOut; var hi = tIn
    repeat(24) {
        val mid = (lo + hi) * 0.5f
        if (inside(mid)) hi = mid else lo = mid
    }
    return hi
}

/**
 * Pro každý oblouk vytvoří samostatnou [ConicSection3D] (sdílí geometrii šablony,
 * vlastní id) přes [addIntersectionConic3D] a nastaví jí rozsah ve 3D i ve všech 2D
 * průmětech. Celý oblouk (−π, π) vykreslí jako plnou kuželosečku.
 */
internal fun addClippedEllipseArcs(state: MongeState, template: ConicSection3D, arcs: List<Pair<Float, Float>>) {
    for ((t1, t2) in arcs) {
        val conic = addIntersectionConic3D(state, template.copy(id = UUID.randomUUID().toString()))
        val full = t1 <= -PI_F + 1e-3f && t2 >= PI_F - 1e-3f
        if (full) continue   // celá kuželosečka – bez oříznutí
        state.ellipseArcParams3D[conic.id] = t1 to t2
        setIntersectionEllipseArc(state, conic, t1, t2)
    }
}

/**
 * Nastaví eliptický oblouk (t1, t2) ve 3D a ve VŠECH existujících 2D průmětech. Pro každý
 * průmět se směr oblouku (CCW/CW) určí podle promítnutého **vnitřního bodu** oblouku
 * (parametr (t1+t2)/2) – to je nezávislé na orientaci sdružených průměrů daného průmětu,
 * takže oblouk sedí i v axonometrii (kde SHORTEST/LONGEST mohlo vybrat opačnou půlku).
 * Volá se při tvorbě (Monge: půdorys/nárys) i při převodu do AXO (doplní bokorys/axo).
 */
internal fun setIntersectionEllipseArc(state: MongeState, conic: ConicSection3D, t1: Float, t2: Float) {
    val axes = runCatching { computeEllipseAxes3D(conic) }.getOrNull() ?: return
    val au = axes.uRotated.normalize()
    val av = axes.vRotated.normalize()
    val a = axes.a; val b = axes.b
    val a3D = ellipsePointAt(conic, t1, au, av, a, b)
    val b3D = ellipsePointAt(conic, t2, au, av, a, b)
    val m3D = ellipsePointAt(conic, (t1 + t2) * 0.5f, au, av, a, b)

    state.ellipseArcParams3D[conic.id] = t1 to t2
    state.ellipseArcEnds3D[conic.id] = a3D to b3D

    fun apply(id: String?, inputs: Triple<Offset, Offset, Offset>?, project: (Offset3D) -> Offset) {
        if (id == null || inputs == null || inputs.third == Offset.Unspecified) return
        val (q1, q2, q3) = inputs
        val a2 = projectToEllipseFromDiameters(q1, q2, q3, project(a3D))
        val b2 = projectToEllipseFromDiameters(q1, q2, q3, project(b3D))
        val m2 = projectToEllipseFromDiameters(q1, q2, q3, project(m3D))
        val basis = ellipseBasisFromDiameters(q1, q2, q3)
        val tA = ellipseParamAndProjection(basis, a2).first
        val tB = ellipseParamAndProjection(basis, b2).first
        val tM = ellipseParamAndProjection(basis, m2).first
        val mode = if (angleOnCcwInterval(tA, tB, tM)) ArcMode.CCW else ArcMode.CW
        state.ellipseArcEnds[id] = a2 to b2
        state.ellipseArcMode[id] = mode
    }

    apply(state.findPudorysConicIdByParent(conic.id), state.conicInputPointsPudorys[state.findPudorysConicIdByParent(conic.id)]) { Offset(it.x, it.y) }
    apply(state.findNarysConicIdByParent(conic.id), state.conicInputPointsNarys[state.findNarysConicIdByParent(conic.id)]) { Offset(it.x, -it.z) }
    apply(state.findBokorysConicIdByParent(conic.id), state.conicInputPointsBokorys[state.findBokorysConicIdByParent(conic.id)]) { Offset(it.y, it.z) }
    state.basis?.let { basis ->
        apply(state.findAxoConicIdByParent(conic.id), state.conicInputPointsAxo[state.findAxoConicIdByParent(conic.id)]) { projectPoint3DToAxoLocal(it, basis) }
    }
    state.triggerRedraw++
}

/** Je úhel [tMid] na CCW oblouku z [tA] do [tB]? (normalizace do [0, 2π)) */
private fun angleOnCcwInterval(tA: Float, tB: Float, tMid: Float): Boolean {
    val twoPi = (2.0 * PI).toFloat()
    fun norm(x: Float) = ((x % twoPi) + twoPi) % twoPi
    val ab = norm(tB - tA)
    val am = norm(tMid - tA)
    return am <= ab
}
