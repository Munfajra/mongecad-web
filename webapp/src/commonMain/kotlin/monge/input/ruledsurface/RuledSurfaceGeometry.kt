package monge.input.ruledsurface

import model.Offset3D
import model.classes.RuledSurface3D
import model.classes.RuledSurfaceDirectrixKind
import model.classes.RuledSurfaceDirectrixRef
import model.classes.RuledSurfaceDirectrixSnapshot
import model.classes.PlaneEquation
import model.classes.normal
import model.classes.toOffset3D
import geometry.conics.classifyConicFromMatrix
import geometry.conics.ConicType
import geometry.conics.branchSignFromEnds
import geometry.conics.sampleParametricEllipse
import geometry.conics.sampleParametricEllipseArc
import geometry.conics.sampleParametricHyperbola
import geometry.conics.sampleParametricParabola
import geometry.conics.sampleParametricParabolaArc
import state.MongeState
import utils.WeakHashMap
import utils.synchronized
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

data class RuledSurfaceGenerator3D(val start: Offset3D, val end: Offset3D)

private const val DIRECTRIX_SAMPLES = 96
private const val LINE_HALF_RANGE = 600f
private const val BOUNDARY_PAIR_EVALUATION_SAMPLES = 24
private const val GUIDE_AVERAGE_ERROR_TOLERANCE = 0.008f
private const val GUIDE_MAX_ERROR_TOLERANCE = 0.025f
private const val MAX_GENERAL_FAMILY_BRANCHES = 16
private const val BRANCH_SEED_SEPARATION = 0.12f
private const val TWO_LINE_TRANSVERSAL_ERROR = 1e-3f
private const val TWO_LINE_SPAN_LIMIT_FACTOR = 8f
private const val WHITNEY_GENERATOR_EXTENSION = 2f
private const val RULED_PARABOLA_RANGE = 600f

private data class GeneralLayoutCacheEntry(
    val geometrySignature: Int,
    val layouts: List<GeneralDirectrixLayout>,
)

private val generalLayoutCache = WeakHashMap<MongeState, MutableMap<String, GeneralLayoutCacheEntry>>()

private data class GeneratorFamiliesCacheEntry(
    val geometrySignature: Int,
    val families: List<List<RuledSurfaceGenerator3D>>,
)

private val generatorFamiliesCache =
    WeakHashMap<MongeState, MutableMap<Pair<String, Int>, GeneratorFamiliesCacheEntry>>()

private data class PrimaryGeneratorFamiliesCacheEntry(
    val geometrySignature: Int,
    val families: List<List<RuledSurfaceGenerator3D>>,
)

private val primaryGeneratorFamiliesCache =
    WeakHashMap<MongeState, MutableMap<Pair<String, Int>, PrimaryGeneratorFamiliesCacheEntry>>()

private data class GeneralDirectrixLayout(
    val firstBoundary: RuledSurfaceDirectrixRef,
    val firstComponentIndex: Int,
    val secondBoundary: RuledSurfaceDirectrixRef,
    val secondComponentIndex: Int,
    val guide: RuledSurfaceDirectrixRef,
    val guideComponentIndex: Int,
    val reverseSecond: Boolean,
    val secondPhase: Float,
    val reverseGuide: Boolean,
    val guidePhase: Float,
    val averageGeneratorLength: Float,
    val averageGuideError: Float,
    val maxGuideError: Float,
)

/**
 * Aproximuje tvořicí přímky z definičních objektů. U obecné plochy se ze tří
 * řídicích křivek globálně vyberou dvě nejvzdálenější krajní, takže výsledné
 * úsečky jsou už ořezané plochou a výsledek nezávisí na pořadí výběru.
 * Hustota je argumentem, aby vykreslení a geometrické výpočty nebyly svázané.
 */
fun sampleRuledSurfaceGenerators(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int = surface.generatorCount,
): List<RuledSurfaceGenerator3D> =
    sampleRuledSurfaceGeneratorFamilies(state, surface, count).flatten()

/**
 * Vrací jednotlivé souvislé rodiny odděleně. Toto rozdělení se musí zachovat
 * při tvorbě fillu, obrysu a meshe: poslední tvořice jedné větve není sousední
 * s první tvořicí jiné větve.
 */
fun sampleRuledSurfaceGeneratorFamilies(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int = surface.generatorCount,
): List<List<RuledSurfaceGenerator3D>> {
    if (count <= 0) return emptyList()
    captureRuledSurfaceGeometry(state, surface)
    val signature = ruledSurfaceDirectrixGeometrySignature(state, surface)
    val cacheKey = surface.id to count
    synchronized(generatorFamiliesCache) {
        generatorFamiliesCache[state]?.get(cacheKey)?.let { cached ->
            if (cached.geometrySignature == signature) return cached.families
        }
    }
    val computed = computeRuledSurfaceGeneratorFamilies(state, surface, count)
    synchronized(generatorFamiliesCache) {
        generatorFamiliesCache.getOrPut(state) { HashMap() }[cacheKey] =
            GeneratorFamiliesCacheEntry(signature, computed)
    }
    return computed
}

private fun computeRuledSurfaceGeneratorFamilies(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> {
    if (ruledSurfaceIsSphericalConoid(surface)) {
        return sampleSphericalConoidGeneratorFamilies(state, surface, count)
    }
    if (surface.directorPlaneId != null) {
        return sampleConoidGeneratorFamilies(state, surface, count)
    }
    if (ruledSurfaceHasTwoLinesAndCurve(surface)) {
        return sampleTwoLinesAndCurveGeneratorFamilies(state, surface, count)
    }
    // Tři mimoběžné přímky mají vlastní analytickou větev jednodílného
    // hyperboloidu; obecná křivková heuristika na nekonečných přímkách selhává.
    if (ruledSurfaceIsThreeLines(surface)) {
        return lineTransversalReguli(state, surface, maxOf(count, 4))
    }

    val layouts = surfaceConsistentLayouts(state, surface, resolveGeneralDirectrixLayouts(state, surface))
    if (layouts.isEmpty()) return emptyList()
    val baseCount = count / layouts.size
    val remainder = count % layouts.size
    return layouts.mapIndexedNotNull { layoutIndex, layout ->
        val countForFamily = baseCount + if (layoutIndex < remainder) 1 else 0
        if (countForFamily == 0) return@mapIndexedNotNull null
        sampleGeneralLayout(state, surface, layout, countForFamily).takeIf { it.size >= 2 }
    }
}

private data class RuledSurfaceSphereGeometry(val center: Offset3D, val radius: Float)

private fun ruledSurfaceIsSphericalConoid(surface: RuledSurface3D): Boolean {
    if (surface.directorPlaneId == null || surface.thirdDirectrix != null) return false
    val refs = listOf(surface.firstBoundaryDirectrix, surface.secondBoundaryDirectrix)
    return refs.count { it.kind == RuledSurfaceDirectrixKind.LINE } == 1 &&
        refs.count { it.kind == RuledSurfaceDirectrixKind.SPHERE } == 1
}

private fun sampleSphericalConoidGeneratorFamilies(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> {
    val refs = listOf(surface.firstBoundaryDirectrix, surface.secondBoundaryDirectrix)
    val lineRef = refs.singleOrNull { it.kind == RuledSurfaceDirectrixKind.LINE } ?: return emptyList()
    val sphereRef = refs.singleOrNull { it.kind == RuledSurfaceDirectrixKind.SPHERE } ?: return emptyList()
    val line = ruledSurfaceLine(state, surface, lineRef) ?: return emptyList()
    val sphere = ruledSurfaceSphere(state, surface, sphereRef) ?: return emptyList()
    val plane = ruledSurfaceDirectorPlaneEquation(state, surface) ?: return emptyList()
    val family = sphericalConoidGeneratorFamily(line, sphere.center, sphere.radius, plane.normal, count)
    return listOfNotNull(family.takeIf { it.size >= 4 })
}

/**
 * Přesná dotyková křivka kulového konoidu na jeho definiční kouli. Koncové
 * body tvořic jsou analyticky spočtené body dotyku, takže zde není potřeba
 * znovu řešit numericky nestabilní dvojnásobný kořen přímka × koule.
 */
internal fun sphericalConoidContactCurve(
    state: MongeState,
    surface: RuledSurface3D,
    sphereId: String,
    count: Int,
): List<Offset3D>? {
    if (!ruledSurfaceIsSphericalConoid(surface)) return null
    val usesSphere = listOf(surface.firstBoundaryDirectrix, surface.secondBoundaryDirectrix)
        .any { it.kind == RuledSurfaceDirectrixKind.SPHERE && it.objectId == sphereId }
    if (!usesSphere) return null
    val sphereRef = listOf(surface.firstBoundaryDirectrix, surface.secondBoundaryDirectrix)
        .single { it.kind == RuledSurfaceDirectrixKind.SPHERE }
    val storedSphere = ruledSurfaceSphere(state, surface, sphereRef) ?: return null
    val selectedSphere = liveRuledSurfaceSphere(state, sphereRef) ?: return null
    val scale = maxOf(1f, storedSphere.radius, selectedSphere.radius)
    if (
        distance(storedSphere.center, selectedSphere.center) > 1e-5f * scale ||
        abs(storedSphere.radius - selectedSphere.radius) > 1e-5f * scale
    ) return null
    val family = sampleSphericalConoidGeneratorFamilies(state, surface, count).singleOrNull()
        ?: return null
    return family.map { it.end }.takeIf { it.size >= 4 }
}

private data class TwoLinesCurveTransversal(
    val curvePoint: Offset3D,
    val firstLinePoint: Offset3D,
    val secondLinePoint: Offset3D,
)

private fun ruledSurfaceHasTwoLinesAndCurve(surface: RuledSurface3D): Boolean {
    if (surface.directorPlaneId != null) return false
    val refs = listOfNotNull(
        surface.firstBoundaryDirectrix,
        surface.secondBoundaryDirectrix,
        surface.thirdDirectrix,
    )
    return refs.size == 3 &&
        refs.count { it.kind == RuledSurfaceDirectrixKind.LINE } == 2 &&
        refs.count { it.kind != RuledSurfaceDirectrixKind.LINE } == 1
}

/**
 * Přímá konstrukce příček jedné křivky a dvou nekonečných přímek. Pro bod P
 * křivky protne rovina (P, L2) přímku L1 v Q1 a rovina (P, L1) přímku L2 v Q2;
 * body P, Q1, Q2 pak leží na jediné společné příčce. Tato projektivní
 * korespondence nejde spolehlivě najít obecným parametrickým solverem.
 */
private fun sampleTwoLinesAndCurveGeneratorFamilies(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> {
    if (count <= 0) return emptyList()
    val refs = listOfNotNull(
        surface.firstBoundaryDirectrix,
        surface.secondBoundaryDirectrix,
        surface.thirdDirectrix,
    )
    val lineRefs = refs.filter { it.kind == RuledSurfaceDirectrixKind.LINE }
    val curveRef = refs.singleOrNull { it.kind != RuledSurfaceDirectrixKind.LINE } ?: return emptyList()
    val firstLine = lineRefs.getOrNull(0)?.let { ref -> ruledSurfaceLine(state, surface, ref) }
        ?: return emptyList()
    val secondLine = lineRefs.getOrNull(1)?.let { ref -> ruledSurfaceLine(state, surface, ref) }
        ?: return emptyList()
    val firstStart = firstLine.start.toOffset3D()
    val secondStart = secondLine.start.toOffset3D()
    val firstDirection = firstLine.direction.normalizedSafe()
    val secondDirection = secondLine.direction.normalizedSafe()
    if ((firstDirection cross secondDirection).let { it dot it } < 1e-10f) return emptyList()

    fun transversalAt(point: Offset3D): TwoLinesCurveTransversal? {
        val secondPlaneNormal = secondDirection cross (point - secondStart)
        val firstDenominator = secondPlaneNormal dot firstDirection
        val firstScale = sqrt(secondPlaneNormal dot secondPlaneNormal).coerceAtLeast(1f)
        if (abs(firstDenominator) <= 1e-7f * firstScale) return null
        val firstParameter = -(secondPlaneNormal dot (firstStart - secondStart)) / firstDenominator
        val firstPoint = firstStart + firstDirection * firstParameter

        val firstPlaneNormal = firstDirection cross (point - firstStart)
        val secondDenominator = firstPlaneNormal dot secondDirection
        val secondScale = sqrt(firstPlaneNormal dot firstPlaneNormal).coerceAtLeast(1f)
        if (abs(secondDenominator) <= 1e-7f * secondScale) return null
        val secondParameter = -(firstPlaneNormal dot (secondStart - firstStart)) / secondDenominator
        val secondPoint = secondStart + secondDirection * secondParameter

        val span = distance(firstPoint, secondPoint)
        if (span < 1e-5f || pointLineDistance(point, firstPoint, secondPoint) / span > TWO_LINE_TRANSVERSAL_ERROR) {
            return null
        }
        return TwoLinesCurveTransversal(point, firstPoint, secondPoint)
    }

    val closed = isDirectrixClosed(state, surface, curveRef)
    val denseCount = maxOf(count * 8, DIRECTRIX_SAMPLES * 2)
    val componentCandidates = sampleDirectrixComponents(state, surface, curveRef).map { component ->
        resamplePolyline(component, denseCount, closed).map(::transversalAt)
    }
    val allCandidates = componentCandidates.flatten().filterNotNull()
    if (allCandidates.size < 2) return emptyList()

    fun point(candidate: TwoLinesCurveTransversal, index: Int): Offset3D = when (index) {
        0 -> candidate.curvePoint
        1 -> candidate.firstLinePoint
        else -> candidate.secondLinePoint
    }
    val boundaryPair = listOf(0 to 1, 0 to 2, 1 to 2).maxByOrNull { pair ->
        allCandidates.map { distance(point(it, pair.first), point(it, pair.second)) }
            .sorted()
            .let { lengths -> lengths[lengths.size / 2] }
    } ?: return emptyList()
    val lengths = allCandidates.map {
        distance(point(it, boundaryPair.first), point(it, boundaryPair.second))
    }.sorted()
    val spanLimit = lengths[lengths.size / 2] * TWO_LINE_SPAN_LIMIT_FACTOR

    val rawFamilies = componentCandidates.flatMap { candidates ->
        val generators = candidates.map { candidate ->
            candidate?.let {
                val start = point(it, boundaryPair.first)
                val end = point(it, boundaryPair.second)
                RuledSurfaceGenerator3D(start, end).takeIf { distance(start, end) <= spanLimit }
            }
        }
        splitNullableGeneratorFamily(generators, closed)
    }
    return distributeGeneratorFamilies(rawFamilies, count)
}

private fun splitNullableGeneratorFamily(
    generators: List<RuledSurfaceGenerator3D?>,
    closed: Boolean,
): List<List<RuledSurfaceGenerator3D>> {
    if (generators.isEmpty()) return emptyList()
    val runs = ArrayList<MutableList<RuledSurfaceGenerator3D>>()
    var current = ArrayList<RuledSurfaceGenerator3D>()
    generators.forEach { generator ->
        if (generator == null) {
            if (current.isNotEmpty()) runs += current
            current = ArrayList()
        } else current += generator
    }
    if (current.isNotEmpty()) runs += current
    if (closed && runs.size >= 2 && generators.first() != null && generators.last() != null) {
        val joined = ArrayList<RuledSurfaceGenerator3D>(runs.last().size + runs.first().size)
        joined += runs.last()
        joined += runs.first()
        runs[0] = joined
        runs.removeAt(runs.lastIndex)
    }
    return runs.filter { it.size >= 2 }
}

private fun sampleConoidGeneratorFamilies(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> {
    val firstComponents = sampleDirectrixComponents(state, surface, surface.firstBoundaryDirectrix)
    val secondComponents = sampleDirectrixComponents(state, surface, surface.secondBoundaryDirectrix)
    val pairs = firstComponents.flatMap { first -> secondComponents.map { second -> first to second } }
    if (pairs.isEmpty()) return emptyList()
    val baseCount = count / pairs.size
    val remainder = count % pairs.size
    return pairs.mapIndexedNotNull { index, (first, second) ->
        val familyCount = baseCount + if (index < remainder) 1 else 0
        if (familyCount < 2) return@mapIndexedNotNull null
        sampleConoidGenerators(state, surface, familyCount, first, second).takeIf { it.size >= 2 }
    }
}

/**
 * Vzorkuje jednu obecnou rodinu tvořic z jejího uloženého rozvržení. Rodina se
 * spočítá v jemném rozlišení a pak převzorkuje tak, aby byly rovnoměrně pokryté
 * OBĚ krajní křivky – korespondence mezi nimi bývá nerovnoměrná, takže rovnoměrné
 * vzorkování jen po první křivce by na druhé nechalo klín bez tvořic.
 */
private fun sampleGeneralLayout(
    state: MongeState,
    surface: RuledSurface3D,
    layout: GeneralDirectrixLayout,
    count: Int,
): List<RuledSurfaceGenerator3D> {
    if (count <= 0) return emptyList()
    val firstRef = layout.firstBoundary
    val secondRef = layout.secondBoundary
    val guideRef = layout.guide
    val first = sampleDirectrixComponents(state, surface, firstRef).getOrNull(layout.firstComponentIndex).orEmpty()
    val second = sampleDirectrixComponents(state, surface, secondRef).getOrNull(layout.secondComponentIndex).orEmpty()
    val guide = sampleDirectrixComponents(state, surface, guideRef).getOrNull(layout.guideComponentIndex).orEmpty()
    if (first.size < 2 || second.size < 2 || guide.size < 2) return emptyList()
    val firstClosed = isDirectrixClosed(state, surface, firstRef)
    val secondClosed = isDirectrixClosed(state, surface, secondRef)
    val guideClosed = isDirectrixClosed(state, surface, guideRef)
    val highResolution = maxOf(count * 3, DIRECTRIX_SAMPLES)
    val firstDense = resamplePolyline(first, highResolution, firstClosed)
    val secondDense = resamplePolyline(second, highResolution, secondClosed)
    val guideDense = resamplePolyline(guide, highResolution, guideClosed)
    val forward = solveGeneralGeneratorFamily(
        first = firstDense,
        firstClosed = firstClosed,
        second = secondDense,
        secondClosed = secondClosed,
        guide = guideDense,
        guideClosed = guideClosed,
        secondRequiresCoverage = secondRef.kind != RuledSurfaceDirectrixKind.LINE,
        guideRequiresCoverage = guideRef.kind != RuledSurfaceDirectrixKind.LINE,
        reverseSecond = layout.reverseSecond,
        secondPhase = layout.secondPhase,
        reverseGuide = layout.reverseGuide,
        guidePhase = layout.guidePhase,
    )
    val reverse = if (
        firstRef.kind != RuledSurfaceDirectrixKind.LINE &&
        secondRef.kind != RuledSurfaceDirectrixKind.LINE
    ) {
        val inverse = inverseGeneralLayoutTransform(layout)
        solveGeneralGeneratorFamily(
            // Druhá krajní křivka je tentokrát plně vzorkovaným parametrem rodiny.
            first = secondDense,
            firstClosed = secondClosed,
            second = firstDense,
            secondClosed = firstClosed,
            guide = guideDense,
            guideClosed = guideClosed,
            secondRequiresCoverage = true,
            guideRequiresCoverage = guideRef.kind != RuledSurfaceDirectrixKind.LINE,
            reverseSecond = inverse.reverseSecond,
            secondPhase = inverse.secondPhase,
            reverseGuide = inverse.reverseGuide,
            guidePhase = inverse.guidePhase,
        )
    } else null
    val dense = mergeBidirectionalGeneratorCoverage(forward, reverse, highResolution, firstClosed)
    return balanceGeneratorCoverage(dense, count, firstClosed)
}

private data class InverseGeneralLayoutTransform(
    val reverseSecond: Boolean,
    val secondPhase: Float,
    val reverseGuide: Boolean,
    val guidePhase: Float,
)

/** Inverzní parametrizace: parametr rodiny se přesune z první na druhou krajní křivku. */
private fun inverseGeneralLayoutTransform(layout: GeneralDirectrixLayout): InverseGeneralLayoutTransform {
    val secondDirection = if (layout.reverseSecond) -1f else 1f
    val guideDirection = if (layout.reverseGuide) -1f else 1f
    val secondIntercept = layout.secondPhase + if (layout.reverseSecond) 1f else 0f
    val guideIntercept = layout.guidePhase + if (layout.reverseGuide) 1f else 0f

    val inverseSecondPhase = if (layout.reverseSecond) layout.secondPhase else -layout.secondPhase
    val inverseGuideDirection = guideDirection * secondDirection
    val inverseGuideIntercept = guideIntercept - inverseGuideDirection * secondIntercept
    val inverseGuideReversed = inverseGuideDirection < 0f
    val inverseGuidePhase = inverseGuideIntercept - if (inverseGuideReversed) 1f else 0f
    return InverseGeneralLayoutTransform(
        reverseSecond = layout.reverseSecond,
        secondPhase = inverseSecondPhase,
        reverseGuide = inverseGuideReversed,
        guidePhase = inverseGuidePhase,
    )
}

/**
 * Spojí vzorky získané z obou krajních křivek. Opačně řešené tvořice se otočí
 * zpět a zařadí podle svého parametru na první křivce, takže fill ani obrys
 * nedostanou dvě nesouvisející posloupnosti.
 */
private fun mergeBidirectionalGeneratorCoverage(
    forward: SolvedGeneralGeneratorFamily,
    reverse: SolvedGeneralGeneratorFamily?,
    resolution: Int,
    firstClosed: Boolean,
): List<RuledSurfaceGenerator3D> {
    data class ParameterizedGenerator(val parameter: Float, val generator: RuledSurfaceGenerator3D)

    val samples = ArrayList<ParameterizedGenerator>(resolution * 2)
    if (forward.hasAcceptableGuideFit) {
        forward.generators.forEachIndexed { index, generator ->
            val parameter = when {
                resolution <= 1 -> 0.5f
                firstClosed -> index.toFloat() / resolution
                else -> index.toFloat() / (resolution - 1)
            }
            samples += ParameterizedGenerator(parameter, generator)
        }
    }
    if (reverse?.hasAcceptableGuideFit == true) {
        reverse.generators.forEachIndexed { index, generator ->
            val firstParameter = reverse.secondParameters.getOrNull(index) ?: return@forEachIndexed
            samples += ParameterizedGenerator(
                parameter = normalizeParameter(firstParameter, firstClosed),
                generator = RuledSurfaceGenerator3D(start = generator.end, end = generator.start),
            )
        }
    }
    if (samples.isEmpty()) return emptyList()

    val minimumSeparation = 0.2f / resolution.coerceAtLeast(1)
    val sorted = samples.sortedBy { it.parameter }
    val merged = ArrayList<RuledSurfaceGenerator3D>(sorted.size)
    var previousParameter = Float.NEGATIVE_INFINITY
    for (sample in sorted) {
        if (sample.parameter - previousParameter <= minimumSeparation) continue
        merged += sample.generator
        previousParameter = sample.parameter
    }
    return merged
}

/**
 * Převzorkuje hustou rodinu na [count] tvořic rovnoměrně v parametru, který je
 * průměrem délkových parametrů obou krajních křivek. Tím se zaplní klín na té
 * krajní křivce, kde by tvořice jinak zhoustly do malého oblouku.
 */
private fun balanceGeneratorCoverage(
    dense: List<RuledSurfaceGenerator3D>,
    count: Int,
    closed: Boolean,
): List<RuledSurfaceGenerator3D> {
    if (dense.size <= count || dense.size < 2) return dense
    // Uzavřenou rodinu doplníme o uzlový bod přes šev (poslední → první), aby se
    // vzorkoval i oblouk mezi koncem a začátkem smyčky.
    val nodes = if (closed) dense + dense.first() else dense
    val n = nodes.size
    val startCum = FloatArray(n)
    val endCum = FloatArray(n)
    for (i in 1 until n) {
        startCum[i] = startCum[i - 1] + distance(nodes[i - 1].start, nodes[i].start)
        endCum[i] = endCum[i - 1] + distance(nodes[i - 1].end, nodes[i].end)
    }
    val startTotal = startCum.last()
    val endTotal = endCum.last()
    if (startTotal < 1e-5f || endTotal < 1e-5f) return pickGeneratorsEvenly(dense, count)
    val balanced = FloatArray(n) { i -> 0.5f * (startCum[i] / startTotal + endCum[i] / endTotal) }
    val span = balanced.last()
    val picked = List(count) { k ->
        val target = span * k / (if (closed) count else count - 1)
        var hi = 1
        while (hi < n && balanced[hi] < target) hi++
        hi = hi.coerceAtMost(n - 1)
        val lo = hi - 1
        // Tvořice se nesmí interpolovat: lineární kombinace dvou platných
        // příček obecně už neprotíná řídicí křivky. Vybereme nejbližší skutečný
        // vzorek z husté, přesně vyřešené rodiny.
        val index = if (target - balanced[lo] <= balanced[hi] - target) lo else hi
        nodes[index]
    }
    return picked.distinct()
}

/**
 * Rozdělí požadovaný počet zobrazených tvořic mezi souvislé rodiny a každou
 * převzorkuje na její podíl. Prázdné rodiny se zahodí.
 */
private fun distributeGeneratorFamilies(
    families: List<List<RuledSurfaceGenerator3D>>,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> {
    val nonEmpty = families.filter { it.size >= 2 }
    if (nonEmpty.isEmpty() || count <= 0) return emptyList()
    val base = count / nonEmpty.size
    val remainder = count % nonEmpty.size
    return nonEmpty.mapIndexedNotNull { index, family ->
        val share = base + if (index < remainder) 1 else 0
        if (share < 2) null else pickGeneratorsEvenly(family, share)
    }
}

private fun pickGeneratorsEvenly(
    family: List<RuledSurfaceGenerator3D>,
    count: Int,
): List<RuledSurfaceGenerator3D> {
    if (count >= family.size) return family
    return List(count) { k -> family[(k.toLong() * (family.size - 1) / (count - 1)).toInt()] }
}

private fun sampleConoidGenerators(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
    first: List<Offset3D> = sampleDirectrix(state, surface, surface.firstBoundaryDirectrix),
    second: List<Offset3D> = sampleDirectrix(state, surface, surface.secondBoundaryDirectrix),
): List<RuledSurfaceGenerator3D> {
    val firstRef = surface.firstBoundaryDirectrix
    val secondRef = surface.secondBoundaryDirectrix
    if (first.size < 2 || second.size < 2) return emptyList()

    val plane = ruledSurfaceDirectorPlaneEquation(state, surface)
    if (plane != null) {
        val normal = plane.normal
        sampleWhitneyUmbrellaGenerators(state, surface, count, normal)?.let { return it }
        sampleParabolicCylinderGenerators(state, surface, count, normal)?.let { return it }
        sampleHyperbolicParaboloidGenerators(state, surface, count, normal)?.let { return it }
        val firstLine = surface.firstBoundaryDirectrix
            .takeIf { it.kind == RuledSurfaceDirectrixKind.LINE }
            ?.let { ref -> ruledSurfaceLine(state, surface, ref) }
        val secondLine = surface.secondBoundaryDirectrix
            .takeIf { it.kind == RuledSurfaceDirectrixKind.LINE }
            ?.let { ref -> ruledSurfaceLine(state, surface, ref) }

        // Nejdůležitější konoidový případ: jedna krajní řídicí křivka je
        // nekonečná přímka. Parametrem rodiny musí být konečná křivka, nikoli
        // libovolně oříznutý úsek přímky. Bod na přímce dopočítáme přesně z
        // podmínky n·(L(t)-C)=0.
        if (firstLine != null && secondLine == null) {
            val denominator = normal dot firstLine.direction
            if (abs(denominator) > 1e-7f) {
                val lineStart = firstLine.start.toOffset3D()
                return resamplePolyline(
                    second,
                    count,
                    isDirectrixClosed(state, surface, secondRef),
                ).map { curvePoint ->
                    val t = (normal dot (curvePoint - lineStart)) / denominator
                    RuledSurfaceGenerator3D(lineStart + firstLine.direction * t, curvePoint)
                }
            }
        }
        if (secondLine != null && firstLine == null) {
            val denominator = normal dot secondLine.direction
            if (abs(denominator) > 1e-7f) {
                val lineStart = secondLine.start.toOffset3D()
                return resamplePolyline(
                    first,
                    count,
                    isDirectrixClosed(state, surface, firstRef),
                ).map { curvePoint ->
                    val t = (normal dot (curvePoint - lineStart)) / denominator
                    RuledSurfaceGenerator3D(curvePoint, lineStart + secondLine.direction * t)
                }
            }
        }

        return sampleBalancedConoidCurveFamily(
            first = first,
            firstClosed = isDirectrixClosed(state, surface, firstRef),
            second = second,
            secondClosed = isDirectrixClosed(state, surface, secondRef),
            normal = normal,
            count = count,
        )
    }
    return emptyList()
}

/**
 * Whitneyho deštník: konoid daný jednou přímkou, parabolou a nevlastní
 * řídicí křivkou. Obecný konoidový sampler převzorkovává podle délky oblouku;
 * na neomezené parabole tím nechá kolem vrcholu obrovskou mezeru. Zde proto
 * zachováme rovnoměrný nativní parametr paraboly a tvořici na obou stranách
 * lehce prodloužíme za její průsečíky s řídicími objekty.
 *
 * `null` znamená, že nejde o Whitneyho kombinaci. Prázdný seznam znamená, že
 * kombinace odpovídá, ale v dané poloze nevytváří konečnou rodinu.
 */
private fun sampleWhitneyUmbrellaGenerators(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
    directorNormal: Offset3D,
): List<RuledSurfaceGenerator3D>? {
    val refs = listOf(surface.firstBoundaryDirectrix, surface.secondBoundaryDirectrix)
    val lineRef = refs.singleOrNull { it.kind == RuledSurfaceDirectrixKind.LINE } ?: return null
    val parabolaRef = refs.singleOrNull { it.kind == RuledSurfaceDirectrixKind.CONIC } ?: return null
    val line = ruledSurfaceLine(state, surface, lineRef) ?: return emptyList()
    val parabola = ruledSurfaceConic(state, surface, parabolaRef) ?: return emptyList()
    if (classifyConicFromMatrix(parabola.matrix) != ConicType.PARABOLA) return null
    if (count < 2) return emptyList()

    val denominator = directorNormal dot line.direction
    if (abs(denominator) <= 1e-7f) return emptyList()
    val lineStart = line.start.toOffset3D()
    val parabolaParams = directrixSnapshot(surface, parabolaRef)?.parabolaArcParams
        ?: state.parabolaArcParams3D[parabolaRef.objectId]
    val parabolaPoints = parabolaParams?.let { (t1, t2) ->
        sampleParametricParabolaArc(
            parabola,
            t1,
            t2,
            stepsHint = maxOf(count - 1, DIRECTRIX_SAMPLES),
        ).pickUniformParameterSamples(count)
    } ?: sampleParametricParabola(
        parabola,
        range = RULED_PARABOLA_RANGE,
        steps = count - 1,
    )

    val lineIsFirst = surface.firstBoundaryDirectrix == lineRef
    return parabolaPoints.map { curvePoint ->
        val t = (directorNormal dot (curvePoint - lineStart)) / denominator
        val linePoint = lineStart + line.direction * t
        val raw = if (lineIsFirst) {
            RuledSurfaceGenerator3D(linePoint, curvePoint)
        } else {
            RuledSurfaceGenerator3D(curvePoint, linePoint)
        }
        val direction = raw.end - raw.start
        RuledSurfaceGenerator3D(
            start = raw.start - direction * WHITNEY_GENERATOR_EXTENSION,
            end = raw.end + direction * WHITNEY_GENERATOR_EXTENSION,
        )
    }
}

/** Vstupní parabola je vzorkovaná rovnoměrně v t, proto vybíráme podle indexu, ne délky. */
private fun List<Offset3D>.pickUniformParameterSamples(count: Int): List<Offset3D> {
    if (isEmpty() || count <= 0) return emptyList()
    if (count == 1) return listOf(this[size / 2])
    if (size == count) return this
    return List(count) { index ->
        val sourceIndex = (index.toLong() * lastIndex / (count - 1)).toInt()
        this[sourceIndex]
    }
}

private data class ParabolaParametricGeometry(
    val vertex: Offset3D,
    val linear: Offset3D,
    val quadratic: Offset3D,
)

/**
 * Dvě shodné posunuté paraboly a řídicí rovina obsahující jejich posunutí
 * určují parabolickou válcovou plochu. Přímé párování parametrů je přesnější
 * než obecné hledání kořenů a nemůže na koncích otevřených parabol vytvořit
 * díru, kvůli které by guard konstrukci odmítl.
 *
 * `null` znamená, že nejde o dvojici tvořící parabolický válec; pak pokračuje
 * obecný konoidový solver.
 */
private fun sampleParabolicCylinderGenerators(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
    directorNormal: Offset3D,
): List<RuledSurfaceGenerator3D>? {
    val refs = listOf(surface.firstBoundaryDirectrix, surface.secondBoundaryDirectrix)
    if (refs.any { it.kind != RuledSurfaceDirectrixKind.CONIC }) return null
    val firstConic = ruledSurfaceConic(state, surface, refs[0]) ?: return emptyList()
    val secondConic = ruledSurfaceConic(state, surface, refs[1]) ?: return emptyList()
    if (
        classifyConicFromMatrix(firstConic.matrix) != ConicType.PARABOLA ||
        classifyConicFromMatrix(secondConic.matrix) != ConicType.PARABOLA
    ) return null
    if (count < 2) return emptyList()

    val first = firstConic.parabolaParametricGeometry() ?: return null
    val second = secondConic.parabolaParametricGeometry() ?: return null
    val orientation = listOf(1f, -1f).minByOrNull { sign ->
        distance(first.linear, second.linear * sign)
    } ?: return null
    val linearError = distance(first.linear, second.linear * orientation)
    val quadraticScale = maxOf(first.quadratic.length(), second.quadratic.length(), 1e-5f)
    val quadraticError = distance(first.quadratic, second.quadratic) / quadraticScale
    if (linearError > 1e-3f || quadraticError > 2e-3f) return null

    val translation = second.vertex - first.vertex
    val translationLength = translation.length()
    val normalLength = directorNormal.length()
    if (translationLength < 1e-5f || normalLength < 1e-7f) return emptyList()
    val planeError = abs(directorNormal dot translation) / (normalLength * translationLength)
    if (planeError > 2e-3f) return null

    fun parameterRange(ref: RuledSurfaceDirectrixRef): Pair<Float, Float> {
        val stored = directrixSnapshot(surface, ref)?.parabolaArcParams
            ?: state.parabolaArcParams3D[ref.objectId]
            ?: (-RULED_PARABOLA_RANGE to RULED_PARABOLA_RANGE)
        return minOf(stored.first, stored.second) to maxOf(stored.first, stored.second)
    }
    val firstRange = parameterRange(refs[0])
    val secondNativeRange = parameterRange(refs[1])
    val secondInFirstParameter = listOf(
        orientation * secondNativeRange.first,
        orientation * secondNativeRange.second,
    ).let { values -> values.min() to values.max() }
    val start = maxOf(firstRange.first, secondInFirstParameter.first)
    val end = minOf(firstRange.second, secondInFirstParameter.second)
    if (!start.isFinite() || !end.isFinite() || end - start < 1e-5f) return emptyList()

    return List(count) { index ->
        val parameter = start + (end - start) * index / (count - 1)
        RuledSurfaceGenerator3D(
            start = first.pointAt(parameter),
            end = second.pointAt(orientation * parameter),
        )
    }
}

private fun model.classes.ConicSection3D.parabolaParametricGeometry(): ParabolaParametricGeometry? {
    val coefficient = matrix.m00
    if (abs(coefficient) <= 1e-5f) return null
    val focalLength = 1f / (4f * coefficient)
    val linear = u.normalizedSafe()
    val transverseRaw = v - linear * (v dot linear)
    val transverseLength = transverseRaw.length()
    if (transverseLength < 1e-7f || !focalLength.isFinite()) return null
    val transverse = transverseRaw * (1f / transverseLength)
    return ParabolaParametricGeometry(
        vertex = p0,
        linear = linear,
        quadratic = transverse * (1f / (4f * focalLength)),
    )
}

private fun ParabolaParametricGeometry.pointAt(parameter: Float): Offset3D =
    vertex + linear * parameter + quadratic * (parameter * parameter)

/**
 * Dvě mimoběžné přímky a nevlastní řídicí křivka daná rovinou určují
 * hyperbolický paraboloid. Pro body
 *
 *     L1(s) = P1 + s D1,  L2(t) = P2 + t D2
 *
 * musí mít tvořice směr rovnoběžný s řídicí rovinou, tedy
 * `n · (L2(t) - L1(s)) = 0`. To dává přímou lineární korespondenci parametrů
 * s a t. Obecný konoidový solver ji na uměle oříznutých přímkách neumí
 * spolehlivě najít a guard pak platnou konstrukci odmítá.
 *
 * `null` znamená, že vstup netvoří dvě přímky. Prázdný seznam znamená, že
 * přímky nejsou mimoběžné nebo řídicí rovina nepokrývá obě celé přímky.
 */
private fun sampleHyperbolicParaboloidGenerators(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
    directorNormal: Offset3D,
): List<RuledSurfaceGenerator3D>? {
    val refs = listOf(surface.firstBoundaryDirectrix, surface.secondBoundaryDirectrix)
    if (refs.any { it.kind != RuledSurfaceDirectrixKind.LINE }) return null
    if (count < 2) return emptyList()

    val firstLine = ruledSurfaceLine(state, surface, refs[0]) ?: return emptyList()
    val secondLine = ruledSurfaceLine(state, surface, refs[1]) ?: return emptyList()
    val firstDirectionLength = firstLine.direction.length()
    val secondDirectionLength = secondLine.direction.length()
    val normalLength = directorNormal.length()
    if (firstDirectionLength < 1e-7f || secondDirectionLength < 1e-7f || normalLength < 1e-7f) {
        return emptyList()
    }

    // Normalizované směry znamenají, že parametry s a t mají jednotku délky;
    // konečný výřez je proto na obou přímkách geometricky srovnatelný.
    val firstDirection = firstLine.direction * (1f / firstDirectionLength)
    val secondDirection = secondLine.direction * (1f / secondDirectionLength)
    val firstStart = firstLine.start.toOffset3D()
    val secondStart = secondLine.start.toOffset3D()
    val betweenStarts = secondStart - firstStart
    val directionCross = firstDirection cross secondDirection
    val crossLength = directionCross.length()
    if (crossLength < 1e-5f) return emptyList()
    val skewDistance = abs(betweenStarts dot directionCross) / crossLength
    val skewTolerance = 1e-4f * maxOf(1f, betweenStarts.length())
    if (skewDistance <= skewTolerance) return emptyList()

    val firstPlaneRate = directorNormal dot firstDirection
    val secondPlaneRate = directorNormal dot secondDirection
    val rateScale = normalLength
    // Jestli je některá rychlost nulová, korespondence pokryje na příslušné
    // přímce jen jediný bod. Taková rodina tedy nemá obě zadané přímky za
    // řídicí křivky a není zde podporovaným hyperbolickým paraboloidem.
    if (abs(firstPlaneRate) <= 1e-7f * rateScale || abs(secondPlaneRate) <= 1e-7f * rateScale) {
        return emptyList()
    }

    // Parametry nejbližších bodů obou mimoběžek slouží pouze jako přirozený
    // střed konečného zobrazeného výřezu.
    val directionDot = (firstDirection dot secondDirection).coerceIn(-1f, 1f)
    val closestDenominator = 1f - directionDot * directionDot
    if (closestDenominator < 1e-7f) return emptyList()
    val firstMinusSecond = firstStart - secondStart
    val firstOffset = firstDirection dot firstMinusSecond
    val secondOffset = secondDirection dot firstMinusSecond
    val closestFirst = (directionDot * secondOffset - firstOffset) / closestDenominator
    val closestSecond = (secondOffset - directionDot * firstOffset) / closestDenominator

    // C + B*t - A*s = 0. Nejbližší bod této přímky v parametrickém prostoru
    // ke dvojici nejbližších bodů odstraní závislost výřezu na počátcích Line3D.
    val planeOffset = directorNormal dot betweenStarts
    val relationError = planeOffset + secondPlaneRate * closestSecond - firstPlaneRate * closestFirst
    val relationNormSquared =
        firstPlaneRate * firstPlaneRate + secondPlaneRate * secondPlaneRate
    if (relationNormSquared < 1e-12f || !relationNormSquared.isFinite()) return emptyList()
    val centerFirst = closestFirst + relationError * firstPlaneRate / relationNormSquared
    val centerSecond = closestSecond - relationError * secondPlaneRate / relationNormSquared

    // Směr (B, A) leží v parametrické přímce -A*ds + B*dt = 0. Rozsah u
    // zvolíme tak, aby žádná z obou přímek nepřesáhla společný limit ±600.
    val relationNorm = sqrt(relationNormSquared)
    val firstParameterRate = secondPlaneRate / relationNorm
    val secondParameterRate = firstPlaneRate / relationNorm
    val halfRange = minOf(
        LINE_HALF_RANGE / abs(firstParameterRate),
        LINE_HALF_RANGE / abs(secondParameterRate),
    )
    if (!halfRange.isFinite() || halfRange < 1e-5f) return emptyList()

    return List(count) { index ->
        val u = -halfRange + 2f * halfRange * index / (count - 1)
        val firstPoint = firstStart + firstDirection * (centerFirst + firstParameterRate * u)
        val secondPoint = secondStart + secondDirection * (centerSecond + secondParameterRate * u)
        RuledSurfaceGenerator3D(firstPoint, secondPoint)
    }
}

/**
 * Ze tří konečných řídicích křivek vybere globálně nejdelší dvojici krajních
 * křivek. Každá ze tří kombinací se nejprve zorientuje tak, aby tvořicí co
 * nejlépe procházely zbývající (prostřední) křivkou. Poté rozhoduje průměrná
 * délka celé rodiny, nikoli délka jedné konkrétní tvořicí.
 *
 * Řazení referencí odstraňuje vliv pořadí, ve kterém uživatel objekty vybral.
 */
private fun resolveGeneralDirectrixLayouts(
    state: MongeState,
    surface: RuledSurface3D,
): List<GeneralDirectrixLayout> {
    val signature = ruledSurfaceDirectrixGeometrySignature(state, surface)
    synchronized(generalLayoutCache) {
        generalLayoutCache[state]?.get(surface.id)?.let { cached ->
            if (cached.geometrySignature == signature) return cached.layouts
        }
    }
    val computed = computeGeneralDirectrixLayouts(state, surface)
    synchronized(generalLayoutCache) {
        generalLayoutCache.getOrPut(state) { HashMap() }[surface.id] =
            GeneralLayoutCacheEntry(signature, computed)
    }
    return computed
}

private fun computeGeneralDirectrixLayouts(
    state: MongeState,
    surface: RuledSurface3D,
): List<GeneralDirectrixLayout> {
    val refs = listOfNotNull(
        surface.firstBoundaryDirectrix,
        surface.secondBoundaryDirectrix,
        surface.thirdDirectrix,
    ).distinct().sortedWith(compareBy({ it.kind.ordinal }, { it.objectId }))
    if (refs.size != 3) return emptyList()

    val componentCounts = refs.associateWith { sampleDirectrixComponents(state, surface, it).size }
    if (componentCounts.values.any { it == 0 }) return emptyList()
    val selections = ArrayList<Map<RuledSurfaceDirectrixRef, Int>>()
    fun enumerate(index: Int, current: MutableMap<RuledSurfaceDirectrixRef, Int>) {
        if (index == refs.size) {
            selections += current.toMap()
            return
        }
        val ref = refs[index]
        for (componentIndex in 0 until componentCounts.getValue(ref)) {
            current[ref] = componentIndex
            enumerate(index + 1, current)
        }
        current.remove(ref)
    }
    enumerate(0, HashMap())

    val validCandidates = selections.flatMap { componentSelection ->
        computeGeneralDirectrixLayoutsForComponents(state, surface, refs, componentSelection)
    }
    val bestGroup = validCandidates.groupBy {
        Triple(it.firstBoundary, it.secondBoundary, it.guide)
    }.maxWithOrNull(compareBy({ (_, branches) ->
        branches.maxOfOrNull { it.averageGeneratorLength } ?: Float.NEGATIVE_INFINITY
    }, { (key, _) ->
        "${key.first.kind}:${key.first.objectId}|${key.second.kind}:${key.second.objectId}"
    }))
    return bestGroup?.value.orEmpty().sortedWith(
        compareBy(
            { it.firstComponentIndex },
            { it.secondComponentIndex },
            { it.guideComponentIndex },
            { it.reverseSecond },
            { it.secondPhase },
            { it.reverseGuide },
            { it.guidePhase },
        )
    )
}

private fun computeGeneralDirectrixLayoutsForComponents(
    state: MongeState,
    surface: RuledSurface3D,
    refs: List<RuledSurfaceDirectrixRef>,
    componentSelection: Map<RuledSurfaceDirectrixRef, Int>,
): List<GeneralDirectrixLayout> {

    val samples = refs.associateWith { ref ->
        val component = sampleDirectrixComponents(state, surface, ref)
            .getOrNull(componentSelection.getValue(ref))
            .orEmpty()
        resamplePolyline(
            component,
            BOUNDARY_PAIR_EVALUATION_SAMPLES,
            isDirectrixClosed(state, surface, ref),
        )
    }
    if (samples.values.any { it.size != BOUNDARY_PAIR_EVALUATION_SAMPLES }) return emptyList()

    val candidates = ArrayList<GeneralDirectrixLayout>()
    for (firstIndex in 0 until refs.lastIndex) {
        for (secondIndex in firstIndex + 1..refs.lastIndex) {
            var firstRef = refs[firstIndex]
            var secondRef = refs[secondIndex]
            // Nekonečnou přímku nepoužíváme jako parametr celé rodiny, pokud
            // je v dvojici i konečná křivka. Její umělý rozsah ±600 slouží jen
            // k numerickému hledání průsečíku, ne jako skutečný ořez.
            if (
                firstRef.kind == RuledSurfaceDirectrixKind.LINE &&
                secondRef.kind != RuledSurfaceDirectrixKind.LINE
            ) {
                val swap = firstRef
                firstRef = secondRef
                secondRef = swap
            }
            val guideRef = refs.first { it != firstRef && it != secondRef }
            val first = samples.getValue(firstRef)
            val second = samples.getValue(secondRef)
            val guide = samples.getValue(guideRef)

            data class Orientation(
                val reverseSecond: Boolean,
                val secondShift: Int,
                val reverseGuide: Boolean,
                val guideShift: Int,
                val averageGuideError: Float,
                val maxGuideError: Float,
                val averageLength: Float,
            )

            val secondClosed = isDirectrixClosed(state, surface, secondRef)
            val guideClosed = isDirectrixClosed(state, surface, guideRef)
            val secondTransforms = sampleTransformations(second, secondClosed)
            val guideTransforms = sampleTransformations(guide, guideClosed)
            val orientations = ArrayList<Orientation>()
            for (secondTransform in secondTransforms) {
                for (guideTransform in guideTransforms) {
                    var errorSum = 0f
                    var errorMax = 0f
                    var lengthSum = 0f
                    for (i in first.indices) {
                        val generatorLength = distance(first[i], secondTransform.points[i])
                        val normalizedError = pointLineDistance(
                            guideTransform.points[i],
                            first[i],
                            secondTransform.points[i],
                        ) / generatorLength.coerceAtLeast(1f)
                        errorSum += normalizedError
                        errorMax = maxOf(errorMax, normalizedError)
                        lengthSum += generatorLength
                    }
                    val candidate = Orientation(
                        reverseSecond = secondTransform.reversed,
                        secondShift = secondTransform.phaseShift,
                        reverseGuide = guideTransform.reversed,
                        guideShift = guideTransform.phaseShift,
                        averageGuideError = errorSum / first.size,
                        maxGuideError = errorMax,
                        averageLength = lengthSum / first.size,
                    )
                    orientations += candidate
                }
            }
            val seeds = ArrayList<Orientation>()
            orientations.sortedWith(
                compareBy<Orientation>({ it.averageGuideError }, { it.maxGuideError }, { -it.averageLength })
            ).forEach { candidate ->
                val distinct = seeds.none { existing ->
                    orientationSeedsNear(
                        candidate.reverseSecond,
                        candidate.secondShift,
                        candidate.reverseGuide,
                        candidate.guideShift,
                        existing.reverseSecond,
                        existing.secondShift,
                        existing.reverseGuide,
                        existing.guideShift,
                        secondClosed,
                        guideClosed,
                    )
                }
                if (distinct && seeds.size < MAX_GENERAL_FAMILY_BRANCHES) seeds += candidate
            }

            val pairSolutions = ArrayList<Pair<GeneralDirectrixLayout, SolvedGeneralGeneratorFamily>>()
            for (seed in seeds) {
                val secondPhase = seed.secondShift.toFloat() / BOUNDARY_PAIR_EVALUATION_SAMPLES
                val guidePhase = seed.guideShift.toFloat() / BOUNDARY_PAIR_EVALUATION_SAMPLES
                val refined = solveGeneralGeneratorFamily(
                    first = first,
                    firstClosed = isDirectrixClosed(state, surface, firstRef),
                    second = second,
                    secondClosed = secondClosed,
                    guide = guide,
                    guideClosed = guideClosed,
                    secondRequiresCoverage = secondRef.kind != RuledSurfaceDirectrixKind.LINE,
                    guideRequiresCoverage = guideRef.kind != RuledSurfaceDirectrixKind.LINE,
                    reverseSecond = seed.reverseSecond,
                    secondPhase = secondPhase,
                    reverseGuide = seed.reverseGuide,
                    guidePhase = guidePhase,
                )
                if (!refined.isValid) continue
                if (pairSolutions.any { (_, solved) ->
                        solvedFamiliesEquivalent(solved, refined, secondClosed)
                    }
                ) continue

                val layout = GeneralDirectrixLayout(
                    firstBoundary = firstRef,
                    firstComponentIndex = componentSelection.getValue(firstRef),
                    secondBoundary = secondRef,
                    secondComponentIndex = componentSelection.getValue(secondRef),
                    guide = guideRef,
                    guideComponentIndex = componentSelection.getValue(guideRef),
                    reverseSecond = seed.reverseSecond,
                    secondPhase = secondPhase,
                    reverseGuide = seed.reverseGuide,
                    guidePhase = guidePhase,
                    averageGeneratorLength = refined.averageGeneratorLength,
                    averageGuideError = refined.averageGuideError,
                    maxGuideError = refined.maxGuideError,
                )
                pairSolutions += layout to refined
            }
            candidates += pairSolutions.map { it.first }
        }
    }
    val validCandidates = candidates.filter {
        it.averageGuideError <= GUIDE_AVERAGE_ERROR_TOLERANCE &&
            it.maxGuideError <= GUIDE_MAX_ERROR_TOLERANCE
    }
    return validCandidates
}

internal fun ruledSurfaceDirectrixGeometrySignature(state: MongeState, surface: RuledSurface3D): Int {
    captureRuledSurfaceGeometry(state, surface)
    var result = surface.directrixOrderVersion
    listOfNotNull(
        surface.firstBoundaryDirectrix,
        surface.secondBoundaryDirectrix,
        surface.thirdDirectrix,
    ).forEach { ref ->
        result = 31 * result + ref.hashCode()
        val snapshot = directrixSnapshot(surface, ref)
        result = 31 * result + (snapshot?.hashCode() ?: when (ref.kind) {
            RuledSurfaceDirectrixKind.LINE -> state.lines3D.firstOrNull { it.id == ref.objectId }?.hashCode()
            RuledSurfaceDirectrixKind.CONIC -> state.conics3D.firstOrNull { it.id == ref.objectId }?.hashCode()
            RuledSurfaceDirectrixKind.CURVE -> state.curves3D.firstOrNull { it.id == ref.objectId }?.let { curve ->
                31 * curve.hashCode() + curve.pointIds.mapNotNull { id ->
                    state.sharedPoints3D.firstOrNull { it.id == id }
                }.hashCode()
            }
            RuledSurfaceDirectrixKind.SPHERE -> liveRuledSurfaceSphere(state, ref)?.hashCode()
        } ?: 0)
        if (snapshot == null && ref.kind == RuledSurfaceDirectrixKind.CONIC) {
            result = 31 * result + state.ellipseArcParams3D[ref.objectId].hashCode()
            result = 31 * result + state.ellipseArcEnds3D[ref.objectId].hashCode()
            result = 31 * result + state.parabolaArcParams3D[ref.objectId].hashCode()
            result = 31 * result + state.parabolaArcEnds3D[ref.objectId].hashCode()
            result = 31 * result + state.hyperbolaArcParams3D[ref.objectId].hashCode()
            result = 31 * result + state.hyperbolaArcEnds3D[ref.objectId].hashCode()
        }
    }
    result = 31 * result + surface.directorPlaneId.hashCode()
    result = 31 * result + ruledSurfaceDirectorPlaneEquation(state, surface).hashCode()
    return result
}

/** Vrací text chyby, pokud trojice nevytváří numericky souvislou reálnou rodinu tvořic. */
fun ruledSurfaceDefinitionError(state: MongeState, surface: RuledSurface3D): String? {
    if (ruledSurfaceIsSphericalConoid(surface)) {
        return if (sampleSphericalConoidGeneratorFamilies(state, surface, 24).any { it.size >= 4 }) null
        else "Kulový konoid vyžaduje přímku vně koule, která není rovnoběžná s řídicí rovinou."
    }
    if (surface.directorPlaneId != null) {
        return if (sampleConoidGeneratorFamilies(state, surface, 24).any { it.size >= 2 }) null
        else "Řídicí rovina nevytváří spojitou rodinu tvořic pokrývající celé obě řídicí křivky."
    }
    if (ruledSurfaceHasTwoLinesAndCurve(surface)) {
        return if (sampleTwoLinesAndCurveGeneratorFamilies(state, surface, 24).any { it.size >= 2 }) null
        else "Vybraná křivka a dvě přímky nemají dostatek konečných společných příček."
    }
    if (ruledSurfaceIsThreeLines(surface)) {
        return if (lineTransversalReguli(state, surface, 8).any { it.size >= 2 }) null
        else "Vybrané tři přímky neurčují podporovaný jednodílný hyperboloid " +
            "se spodní podstavou v půdorysně (musí být navzájem mimoběžné a hrdlo nesmí ležet v z = 0)."
    }
    return if (resolveGeneralDirectrixLayouts(state, surface).isEmpty()) {
        "Vybrané tři křivky neurčují podporovanou souvislou přímkovou plochu. " +
            "Nelze najít jednu společnou orientaci a parametrizaci, při které by všechny " +
            "tvořicí procházely prostřední řídicí křivkou."
    } else null
}

fun ruledSurfaceFamilyIsClosed(state: MongeState, surface: RuledSurface3D): Boolean {
    if (ruledSurfaceIsSphericalConoid(surface)) return true
    if (surface.directorPlaneId != null) {
        val firstIsLine = surface.firstBoundaryDirectrix.kind == RuledSurfaceDirectrixKind.LINE
        val secondIsLine = surface.secondBoundaryDirectrix.kind == RuledSurfaceDirectrixKind.LINE
        return when {
            firstIsLine && !secondIsLine -> isDirectrixClosed(state, surface, surface.secondBoundaryDirectrix)
            secondIsLine && !firstIsLine -> isDirectrixClosed(state, surface, surface.firstBoundaryDirectrix)
            else -> isDirectrixClosed(state, surface, surface.firstBoundaryDirectrix)
        }
    }
    if (ruledSurfaceHasTwoLinesAndCurve(surface)) {
        val curveRef = listOfNotNull(
            surface.firstBoundaryDirectrix,
            surface.secondBoundaryDirectrix,
            surface.thirdDirectrix,
        ).singleOrNull { it.kind != RuledSurfaceDirectrixKind.LINE }
        return curveRef?.let { isDirectrixClosed(state, surface, it) } == true
    }
    if (ruledSurfaceIsThreeLines(surface)) return true
    val firstBoundary = resolveGeneralDirectrixLayouts(state, surface).firstOrNull()?.firstBoundary
        ?: surface.firstBoundaryDirectrix
    return isDirectrixClosed(state, surface, firstBoundary)
}

private fun isLiveDirectrixClosed(state: MongeState, ref: RuledSurfaceDirectrixRef): Boolean = when (ref.kind) {
    RuledSurfaceDirectrixKind.LINE -> false
    RuledSurfaceDirectrixKind.CURVE -> state.curves3D.firstOrNull { it.id == ref.objectId }?.closed == true
    RuledSurfaceDirectrixKind.CONIC -> state.conics3D.firstOrNull { it.id == ref.objectId }?.let {
        classifyConicFromMatrix(it.matrix) == ConicType.ELLIPSE &&
            state.ellipseArcParams3D[ref.objectId] == null
    } == true
    RuledSurfaceDirectrixKind.SPHERE -> true
}

private fun sampleLiveDirectrixComponents(
    state: MongeState,
    ref: RuledSurfaceDirectrixRef,
): List<List<Offset3D>> = when (ref.kind) {
    RuledSurfaceDirectrixKind.LINE -> state.lines3D.firstOrNull { it.id == ref.objectId }?.let { line ->
        val p = line.start.toOffset3D()
        val d = line.direction.normalizedSafe()
        listOf(listOf(p - d * LINE_HALF_RANGE, p + d * LINE_HALF_RANGE))
    }.orEmpty()

    RuledSurfaceDirectrixKind.CURVE -> state.curves3D.firstOrNull { it.id == ref.objectId }?.let { curve ->
        listOf(curve.polyline3D ?: catmullRom(
            curve.pointIds.mapNotNull { id ->
                state.sharedPoints3D.firstOrNull { it.id == id }?.toOffset3D()
            },
            curve.closed,
        ))
    }.orEmpty()

    RuledSurfaceDirectrixKind.CONIC -> state.conics3D.firstOrNull { it.id == ref.objectId }?.let { conic ->
        when (classifyConicFromMatrix(conic.matrix)) {
            ConicType.ELLIPSE -> listOf(
                state.ellipseArcParams3D[conic.id]?.let { (t1, t2) ->
                    sampleParametricEllipseArc(conic, t1, t2)
                } ?: sampleParametricEllipse(conic)
            )
            ConicType.PARABOLA -> listOf(
                state.parabolaArcParams3D[conic.id]?.let { (t1, t2) ->
                    sampleParametricParabolaArc(conic, t1, t2)
                } ?: sampleParametricParabola(conic, range = 600f, steps = DIRECTRIX_SAMPLES)
            )
            ConicType.HYPERBOLA -> {
                val a = conic.a ?: 100f
                val b = conic.b ?: 100f
                val arcs = state.hyperbolaArcParams3D[conic.id]
                val ends = state.hyperbolaArcEnds3D[conic.id]
                val signs = ends?.let { branchEnds ->
                    branchEnds.first?.let { branchSignFromEnds(conic, it) } to
                        branchEnds.second?.let { branchSignFromEnds(conic, it) }
                }
                val branches = sampleParametricHyperbola(
                    conic = conic,
                    a = a,
                    b = b,
                    arcs = arcs,
                    branchSigns = signs,
                    ends = ends,
                    steps = DIRECTRIX_SAMPLES,
                )
                listOfNotNull(
                    branches.branch1.takeIf { it.size >= 2 },
                    branches.branch2?.takeIf { it.size >= 2 },
                )
            }
            ConicType.DEGENERATE -> emptyList()
        }
    }.orEmpty()

    RuledSurfaceDirectrixKind.SPHERE -> emptyList()
}

/**
 * Doplní chybějící geometrický snapshot. U nových ploch se volá ještě před
 * guardem; tento fallback zároveň migruje starší uložené plochy, dokud jejich
 * původní řídicí objekty stále existují.
 */
fun captureRuledSurfaceGeometry(state: MongeState, surface: RuledSurface3D) {
    val references = listOfNotNull(
        surface.firstBoundaryDirectrix,
        surface.secondBoundaryDirectrix,
        surface.thirdDirectrix,
    )
    val byReference = surface.directrixSnapshots.associateBy { it.reference }
    val completed = references.mapNotNull { ref ->
        byReference[ref] ?: captureRuledSurfaceDirectrix(state, ref)
    }
    if (completed.size == references.size && completed != surface.directrixSnapshots) {
        surface.directrixSnapshots = completed
    }
    if (surface.directorPlaneId != null && surface.directorPlaneEquation == null) {
        surface.directorPlaneEquation = state.planes3D
            .firstOrNull { it.id == surface.directorPlaneId }
            ?.equation
            ?: model.classes.referencePlaneById(surface.directorPlaneId)?.equation
    }
}

private fun captureRuledSurfaceDirectrix(
    state: MongeState,
    ref: RuledSurfaceDirectrixRef,
): RuledSurfaceDirectrixSnapshot? {
    if (ref.kind == RuledSurfaceDirectrixKind.SPHERE) {
        val sphere = liveRuledSurfaceSphere(state, ref) ?: return null
        return RuledSurfaceDirectrixSnapshot(
            reference = ref,
            components = emptyList(),
            closed = true,
            sphereCenter = sphere.center.copy(),
            sphereRadius = sphere.radius,
        )
    }
    val components = sampleLiveDirectrixComponents(state, ref)
    if (components.isEmpty()) return null
    val line = if (ref.kind == RuledSurfaceDirectrixKind.LINE) {
        state.lines3D.firstOrNull { it.id == ref.objectId }?.let { source ->
            source.copy(start = source.start.copy(), direction = source.direction.copy())
        }
    } else null
    val conic = if (ref.kind == RuledSurfaceDirectrixKind.CONIC) {
        state.conics3D.firstOrNull { it.id == ref.objectId }?.let { source ->
            source.copy(
                p0 = source.p0.copy(),
                u = source.u.copy(),
                v = source.v.copy(),
                matrix = source.matrix.copy(),
            )
        }
    } else null
    return RuledSurfaceDirectrixSnapshot(
        reference = ref,
        components = components.map { component -> component.map { it.copy() } },
        closed = isLiveDirectrixClosed(state, ref),
        line = line,
        conic = conic,
        ellipseArcParams = state.ellipseArcParams3D[ref.objectId],
        ellipseArcEnds = state.ellipseArcEnds3D[ref.objectId],
        parabolaArcParams = state.parabolaArcParams3D[ref.objectId],
        parabolaArcEnds = state.parabolaArcEnds3D[ref.objectId],
        hyperbolaArcParams = state.hyperbolaArcParams3D[ref.objectId],
        hyperbolaArcEnds = state.hyperbolaArcEnds3D[ref.objectId],
    )
}

private fun directrixSnapshot(
    surface: RuledSurface3D,
    ref: RuledSurfaceDirectrixRef,
): RuledSurfaceDirectrixSnapshot? = surface.directrixSnapshots.firstOrNull { it.reference == ref }

internal fun ruledSurfaceLine(
    state: MongeState,
    surface: RuledSurface3D,
    ref: RuledSurfaceDirectrixRef,
) = directrixSnapshot(surface, ref)?.line
    ?: state.lines3D.firstOrNull { it.id == ref.objectId }

private fun ruledSurfaceConic(
    state: MongeState,
    surface: RuledSurface3D,
    ref: RuledSurfaceDirectrixRef,
) = directrixSnapshot(surface, ref)?.conic
    ?: state.conics3D.firstOrNull { it.id == ref.objectId }

private fun liveRuledSurfaceSphere(
    state: MongeState,
    ref: RuledSurfaceDirectrixRef,
): RuledSurfaceSphereGeometry? = state.spheres3D.firstOrNull { it.id == ref.objectId }?.let { sphere ->
    val center = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId } ?: return@let null
    RuledSurfaceSphereGeometry(center.toOffset3D(), sphere.radius)
}

private fun ruledSurfaceSphere(
    state: MongeState,
    surface: RuledSurface3D,
    ref: RuledSurfaceDirectrixRef,
): RuledSurfaceSphereGeometry? = directrixSnapshot(surface, ref)?.let { snapshot ->
    val center = snapshot.sphereCenter ?: return@let null
    val radius = snapshot.sphereRadius ?: return@let null
    RuledSurfaceSphereGeometry(center, radius)
} ?: liveRuledSurfaceSphere(state, ref)

private fun ruledSurfaceDirectorPlaneEquation(
    state: MongeState,
    surface: RuledSurface3D,
): PlaneEquation? = surface.directorPlaneEquation
    ?: surface.directorPlaneId?.let { id ->
        state.planes3D.firstOrNull { it.id == id }?.equation
            ?: model.classes.referencePlaneById(id)?.equation
    }

private fun isDirectrixClosed(
    state: MongeState,
    surface: RuledSurface3D,
    ref: RuledSurfaceDirectrixRef,
): Boolean = directrixSnapshot(surface, ref)?.closed ?: isLiveDirectrixClosed(state, ref)

private fun sampleDirectrixComponents(
    state: MongeState,
    surface: RuledSurface3D,
    ref: RuledSurfaceDirectrixRef,
): List<List<Offset3D>> = directrixSnapshot(surface, ref)?.components
    ?: sampleLiveDirectrixComponents(state, ref)

private fun sampleDirectrix(
    state: MongeState,
    surface: RuledSurface3D,
    ref: RuledSurfaceDirectrixRef,
): List<Offset3D> = sampleDirectrixComponents(state, surface, ref).firstOrNull().orEmpty()

private data class ConoidCurveRoot(val point: Offset3D, val parameter: Float)

/** Všechny průsečíky křivky s rovinou n·(X-anchor)=0. */
private fun conoidCurveRoots(
    anchor: Offset3D,
    curve: List<Offset3D>,
    normal: Offset3D,
    closed: Boolean,
): List<ConoidCurveRoot> {
    if (curve.size < 2) return emptyList()
    val roots = ArrayList<ConoidCurveRoot>()
    val segmentCount = if (closed) curve.size else curve.lastIndex
    val denominator = if (closed) curve.size.toFloat() else curve.lastIndex.toFloat()
    var closestIndex = 0
    var closestPlaneValue = Float.POSITIVE_INFINITY
    var segmentLengthSum = 0f
    fun add(point: Offset3D, parameter: Float) {
        if (roots.none { distance(it.point, point) <= 1e-4f }) {
            roots += ConoidCurveRoot(point, normalizeParameter(parameter, closed))
        }
    }
    for (i in 0 until segmentCount) {
        val next = (i + 1) % curve.size
        val f0 = normal dot (curve[i] - anchor)
        val f1 = normal dot (curve[next] - anchor)
        if (abs(f0) < abs(closestPlaneValue)) {
            closestPlaneValue = f0
            closestIndex = i
        }
        segmentLengthSum += distance(curve[i], curve[next])
        if (abs(f0) < 1e-5f) add(curve[i], i / denominator)
        if (f0 * f1 < 0f && abs(f0 - f1) > 1e-8f) {
            val alpha = f0 / (f0 - f1)
            add(lerp(curve[i], curve[next], alpha), (i + alpha) / denominator)
        }
    }
    if (roots.isEmpty()) {
        // U tečného dotyku může polygonální aproximace ležet celá těsně na
        // jedné straně roviny. Přijmeme nejbližší skutečný bod křivky pouze v
        // toleranci odvozené z délky jednoho vzorkovacího segmentu.
        val normalLength = sqrt(normal dot normal).coerceAtLeast(1e-6f)
        val averageSegmentLength = segmentLengthSum / segmentCount.coerceAtLeast(1)
        if (abs(closestPlaneValue) / normalLength <= averageSegmentLength * 0.06f) {
            add(curve[closestIndex], closestIndex / denominator)
        }
    }
    return roots
}

/**
 * Vnější (obalová) větev konoidu se vybírá jako jedna globálně spojitá stopa
 * kořenů s plným vinutím po obou křivkách. Rozhoduje hladkost celé rodiny a
 * spojitost parametrů, nikoli lokální délka jednotlivé tvořice.
 */
private fun sampleBalancedConoidCurveFamily(
    first: List<Offset3D>,
    firstClosed: Boolean,
    second: List<Offset3D>,
    secondClosed: Boolean,
    normal: Offset3D,
    count: Int,
): List<RuledSurfaceGenerator3D> {
    if (count <= 0 || first.size < 2 || second.size < 2) return emptyList()
    data class ConoidTrack(
        val generators: List<RuledSurfaceGenerator3D>,
        val score: Float,
    )

    val resolution = maxOf(count * 4, DIRECTRIX_SAMPLES * 2)
    val firstDense = resamplePolyline(first, resolution, firstClosed)
    val secondDense = resamplePolyline(second, resolution, secondClosed)
    val roots = firstDense.map { point -> conoidCurveRoots(point, secondDense, normal, secondClosed) }
    // Obě křivky mají být skutečnými celými řídicími křivkami. Částečná rodina
    // by znovu vyrobila díru; takový případ není touto dvojicí křivek definován.
    if (roots.any { it.isEmpty() }) return emptyList()

    fun unwrapNear(parameter: Float, target: Float): Float {
        if (!secondClosed) return parameter
        var value = parameter
        while (value - target > 0.5f) value -= 1f
        while (value - target < -0.5f) value += 1f
        return value
    }

    val expectedStepMagnitude = 1f / if (firstClosed) resolution else (resolution - 1)
    val tracks = ArrayList<ConoidTrack>()
    for (initialRoot in roots.first()) {
        for (orientation in listOf(1f, -1f)) {
            val generators = ArrayList<RuledSurfaceGenerator3D>(resolution)
            val parameters = ArrayList<Float>(resolution)
            var previousParameter = initialRoot.parameter
            var previousDirection = (initialRoot.point - firstDense.first()).normalizedSafe()
            var parameterError = 0f
            var directionVariation = 0f
            generators += RuledSurfaceGenerator3D(firstDense.first(), initialRoot.point)
            parameters += previousParameter

            for (index in 1 until resolution) {
                val expected = previousParameter + orientation * expectedStepMagnitude
                val chosen = roots[index].map { root -> root to unwrapNear(root.parameter, expected) }
                    .minByOrNull { (_, unwrapped) -> abs(unwrapped - expected) }
                    ?: break
                val parameter = chosen.second
                val direction = (chosen.first.point - firstDense[index]).normalizedSafe()
                parameterError += abs((parameter - previousParameter) - orientation * expectedStepMagnitude)
                directionVariation += 1f - (previousDirection dot direction).coerceIn(-1f, 1f)
                generators += RuledSurfaceGenerator3D(firstDense[index], chosen.first.point)
                parameters += parameter
                previousParameter = parameter
                previousDirection = direction
            }
            if (generators.size != resolution) continue

            val covered = if (secondClosed) {
                abs(parameters.last() - parameters.first()) + expectedStepMagnitude
            } else {
                (parameters.maxOrNull() ?: 0f) - (parameters.minOrNull() ?: 0f)
            }
            if (covered < 0.75f) continue
            val closureError = if (firstClosed && secondClosed) {
                abs((parameters.last() + orientation * expectedStepMagnitude) -
                    (parameters.first() + orientation))
            } else 0f
            val closureDirectionVariation = if (firstClosed) {
                val firstDirection = (generators.first().end - generators.first().start).normalizedSafe()
                val lastDirection = (generators.last().end - generators.last().start).normalizedSafe()
                1f - (firstDirection dot lastDirection).coerceIn(-1f, 1f)
            } else 0f
            tracks += ConoidTrack(
                generators = generators,
                // Globální hladkost vybírá obalovou větev. Délka jednotlivé
                // tvořice zde záměrně vůbec nerozhoduje.
                score = directionVariation / resolution +
                    4f * parameterError / resolution +
                    8f * closureError + closureDirectionVariation,
            )
        }
    }
    val best = tracks.minByOrNull { it.score } ?: return emptyList()
    return balanceGeneratorCoverage(best.generators, count, firstClosed && secondClosed)
}

private fun resamplePolyline(points: List<Offset3D>, count: Int, closed: Boolean = false): List<Offset3D> {
    if (count <= 0 || points.isEmpty()) return emptyList()
    if (count == 1) return listOf(points[points.lastIndex / 2])
    val source = if (closed && distance(points.first(), points.last()) > 1e-5f) points + points.first() else points
    val lengths = FloatArray(source.size)
    for (i in 1 until source.size) lengths[i] = lengths[i - 1] + distance(source[i - 1], source[i])
    val total = lengths.last()
    if (total < 1e-7f) return List(count) { source.first() }
    return List(count) { k ->
        val denominator = if (closed) count else count - 1
        val wanted = total * k / denominator
        var hi = 1
        while (hi < lengths.size && lengths[hi] < wanted) hi++
        hi = hi.coerceAtMost(source.lastIndex)
        val lo = (hi - 1).coerceAtLeast(0)
        val span = lengths[hi] - lengths[lo]
        val t = if (span < 1e-7f) 0f else (wanted - lengths[lo]) / span
        lerp(source[lo], source[hi], t)
    }
}

private data class SampleTransformation(
    val reversed: Boolean,
    val phaseShift: Int,
    val points: List<Offset3D>,
)

private fun sampleTransformations(points: List<Offset3D>, closed: Boolean): List<SampleTransformation> {
    val shifts: Iterable<Int> = if (closed) {
        points.indices
    } else {
        listOf(-points.size / 4, 0, points.size / 4)
    }
    return buildList {
        for (reversed in listOf(false, true)) {
            for (shift in shifts) {
                add(SampleTransformation(reversed, shift, transformSamples(points, closed, reversed, shift)))
            }
        }
    }
}

private fun transformSamples(
    points: List<Offset3D>,
    closed: Boolean,
    reversed: Boolean,
    phaseShift: Int,
): List<Offset3D> {
    if (points.isEmpty()) return emptyList()
    val size = points.size
    return List(size) { index ->
        val rawIndex = if (reversed) size - 1 + phaseShift - index else phaseShift + index
        val sourceIndex = if (closed) {
            (rawIndex % size + size) % size
        } else {
            rawIndex.coerceIn(0, size - 1)
        }
        points[sourceIndex]
    }
}

private data class RefinedGeneratorMatch(
    val end: Offset3D,
    val normalizedGuideError: Float,
    val secondParameter: Float,
    val guideParameter: Float,
)

private data class SolvedGeneralGeneratorFamily(
    val generators: List<RuledSurfaceGenerator3D>,
    val secondParameters: List<Float>,
    val guideParameters: List<Float>,
    val averageGeneratorLength: Float,
    val averageGuideError: Float,
    val maxGuideError: Float,
    val parameterizationValid: Boolean,
) {
    val hasAcceptableGuideFit: Boolean
        get() = generators.isNotEmpty() &&
            averageGuideError <= GUIDE_AVERAGE_ERROR_TOLERANCE &&
            maxGuideError <= GUIDE_MAX_ERROR_TOLERANCE

    val isValid: Boolean
        get() = hasAcceptableGuideFit && parameterizationValid
}

/**
 * Zpřesní společnou globální orientaci/fázi lokálně v parametrech druhé a
 * třetí křivky. Ořezová dvojice zůstává pro celou rodinu stejná, ale nemusíme
 * předpokládat, že tři různé křivky mají totožnou rychlost parametrizace.
 */
private fun solveGeneralGeneratorFamily(
    first: List<Offset3D>,
    firstClosed: Boolean,
    second: List<Offset3D>,
    secondClosed: Boolean,
    guide: List<Offset3D>,
    guideClosed: Boolean,
    secondRequiresCoverage: Boolean,
    guideRequiresCoverage: Boolean,
    reverseSecond: Boolean,
    secondPhase: Float,
    reverseGuide: Boolean,
    guidePhase: Float,
): SolvedGeneralGeneratorFamily {
    if (first.isEmpty() || second.size < 2 || guide.size < 2) {
        return SolvedGeneralGeneratorFamily(
            generators = emptyList(),
            secondParameters = emptyList(),
            guideParameters = emptyList(),
            averageGeneratorLength = 0f,
            averageGuideError = Float.POSITIVE_INFINITY,
            maxGuideError = Float.POSITIVE_INFINITY,
            parameterizationValid = false,
        )
    }
    val generators = ArrayList<RuledSurfaceGenerator3D>(first.size)
    val secondParameters = ArrayList<Float>(first.size)
    val guideParameters = ArrayList<Float>(first.size)
    var lengthSum = 0f
    var errorSum = 0f
    var errorMax = 0f
    // Přiřazení se vlákní z předchozí tvořice (spojitá korespondence), ne z
    // nezávislého lineárního odhadu per index. Jinak lokální hledání shlukuje
    // konce tvořic do části druhé krajní křivky a zbytek nechá bez tvořic.
    val secondDir = if (reverseSecond) -1f else 1f
    val guideDir = if (reverseGuide) -1f else 1f
    var previousFamilyParameter = 0f
    var previousSecond = 0f
    var previousGuide = 0f
    first.forEachIndexed { index, start ->
        val familyParameter = when {
            first.size == 1 -> 0.5f
            firstClosed -> index.toFloat() / first.size
            else -> index.toFloat() / first.lastIndex
        }
        val initialSecond: Float
        val initialGuide: Float
        if (index == 0) {
            initialSecond = transformedParameter(familyParameter, reverseSecond, secondPhase, secondClosed)
            initialGuide = transformedParameter(familyParameter, reverseGuide, guidePhase, guideClosed)
        } else {
            // Předpověď dalšího parametru z předchozího + přírůstek; refine si
            // hodnotu normalizuje (uzavřená křivka se přetočí přes 0/1 sama).
            val step = familyParameter - previousFamilyParameter
            initialSecond = previousSecond + step * secondDir
            initialGuide = previousGuide + step * guideDir
        }
        val match = refineGeneratorMatch(
            start = start,
            second = second,
            secondClosed = secondClosed,
            guide = guide,
            guideClosed = guideClosed,
            initialSecondParameter = initialSecond,
            initialGuideParameter = initialGuide,
        )
        val generatorLength = distance(start, match.end)
        generators += RuledSurfaceGenerator3D(start, match.end)
        secondParameters += match.secondParameter
        guideParameters += match.guideParameter
        lengthSum += generatorLength
        errorSum += match.normalizedGuideError
        errorMax = maxOf(errorMax, match.normalizedGuideError)
        previousSecond = continuousNext(previousSecond, match.secondParameter, secondClosed)
        previousGuide = continuousNext(previousGuide, match.guideParameter, guideClosed)
        previousFamilyParameter = familyParameter
    }
    return SolvedGeneralGeneratorFamily(
        generators = generators,
        secondParameters = secondParameters,
        guideParameters = guideParameters,
        averageGeneratorLength = lengthSum / generators.size,
        averageGuideError = errorSum / generators.size,
        maxGuideError = errorMax,
        parameterizationValid = first.size < 8 || (
            parametersFormWholeContinuousCurve(secondParameters, secondClosed, secondRequiresCoverage) &&
                parametersFormWholeContinuousCurve(guideParameters, guideClosed, guideRequiresCoverage)
            ),
    )
}

private fun orientationSeedsNear(
    reverseSecondA: Boolean,
    secondShiftA: Int,
    reverseGuideA: Boolean,
    guideShiftA: Int,
    reverseSecondB: Boolean,
    secondShiftB: Int,
    reverseGuideB: Boolean,
    guideShiftB: Int,
    secondClosed: Boolean,
    guideClosed: Boolean,
): Boolean {
    if (reverseSecondA != reverseSecondB || reverseGuideA != reverseGuideB) return false
    fun phaseDistance(a: Int, b: Int, closed: Boolean): Float {
        val direct = abs(a - b).toFloat() / BOUNDARY_PAIR_EVALUATION_SAMPLES
        return if (closed) minOf(direct, 1f - direct) else direct
    }
    return phaseDistance(secondShiftA, secondShiftB, secondClosed) < BRANCH_SEED_SEPARATION &&
        phaseDistance(guideShiftA, guideShiftB, guideClosed) < BRANCH_SEED_SEPARATION
}

private fun solvedFamiliesEquivalent(
    first: SolvedGeneralGeneratorFamily,
    second: SolvedGeneralGeneratorFamily,
    secondClosed: Boolean,
): Boolean {
    if (
        first.secondParameters.size != second.secondParameters.size ||
        first.guideParameters.size != second.guideParameters.size
    ) return false

    fun parameterDistance(a: Float, b: Float, closed: Boolean): Float {
        val direct = abs(a - b)
        return if (closed) minOf(direct, 1f - direct) else direct
    }
    val secondDistance = first.secondParameters.indices.sumOf { index ->
        parameterDistance(first.secondParameters[index], second.secondParameters[index], secondClosed).toDouble()
    }.div(first.secondParameters.size).toFloat()
    // Tvořice je určena body na první a druhé krajní křivce. Tatáž
    // přímka může protínat pomocnou křivku ve více bodech; rozdílný
    // parametr takového průsečíku proto nesmí vytvořit falešnou rodinu.
    return secondDistance < 0.04f
}

private fun refineGeneratorMatch(
    start: Offset3D,
    second: List<Offset3D>,
    secondClosed: Boolean,
    guide: List<Offset3D>,
    guideClosed: Boolean,
    initialSecondParameter: Float,
    initialGuideParameter: Float,
): RefinedGeneratorMatch {
    data class Candidate(
        val secondParameter: Float,
        val guideParameter: Float,
        val end: Offset3D,
        val error: Float,
    )

    fun candidate(secondParameter: Float, guideParameter: Float): Candidate {
        val normalizedSecond = normalizeParameter(secondParameter, secondClosed)
        val normalizedGuide = normalizeParameter(guideParameter, guideClosed)
        val end = pointAtNormalizedParameter(second, normalizedSecond, secondClosed)
        val guidePoint = pointAtNormalizedParameter(guide, normalizedGuide, guideClosed)
        val generatorLength = distance(start, end)
        val error = pointLineDistance(guidePoint, start, end) / generatorLength.coerceAtLeast(1f)
        return Candidate(normalizedSecond, normalizedGuide, end, error)
    }

    var best = candidate(initialSecondParameter, initialGuideParameter)
    var step = 0.125f
    for (iteration in 0 until 28) {
        var improved = best
        for (secondDelta in listOf(-step, 0f, step)) {
            for (guideDelta in listOf(-step, 0f, step)) {
                if (secondDelta == 0f && guideDelta == 0f) continue
                val tested = candidate(
                    best.secondParameter + secondDelta,
                    best.guideParameter + guideDelta,
                )
                if (tested.error < improved.error) improved = tested
            }
        }
        if (improved.error + 1e-7f < best.error) {
            best = improved
        } else {
            step *= 0.5f
        }
        if (step < 1e-5f) break
    }
    return RefinedGeneratorMatch(
        end = best.end,
        normalizedGuideError = best.error,
        secondParameter = best.secondParameter,
        guideParameter = best.guideParameter,
    )
}

private fun parametersFormWholeContinuousCurve(
    parameters: List<Float>,
    closed: Boolean,
    requireWholeCoverage: Boolean,
): Boolean {
    if (parameters.size < 2) return true
    if (!closed) {
        val maxJump = parameters.zipWithNext { a, b -> abs(b - a) }.maxOrNull() ?: 0f
        val coverage = (parameters.maxOrNull() ?: 0f) - (parameters.minOrNull() ?: 0f)
        return maxJump <= 0.25f && (!requireWholeCoverage || coverage >= 0.6f)
    }

    fun signedCircularDelta(a: Float, b: Float): Float {
        var delta = b - a
        if (delta > 0.5f) delta -= 1f
        if (delta < -0.5f) delta += 1f
        return delta
    }
    val deltas = parameters.indices.map { index ->
        signedCircularDelta(parameters[index], parameters[(index + 1) % parameters.size])
    }
    val maxJump = deltas.maxOfOrNull(::abs) ?: 0f
    val winding = abs(deltas.sum())
    return maxJump <= 0.25f && (!requireWholeCoverage || winding >= 0.6f)
}

/**
 * Rozbalí normalizovaný parametr [matched] (v [0,1)) na hodnotu nejbližší k
 * [previous], aby běžící parametr rostl monotónně i přes hranici 0/1 – jinak by
 * se predikce dalšího kroku u uzavřené křivky vracela zpět.
 */
private fun continuousNext(previous: Float, matched: Float, closed: Boolean): Float {
    if (!closed) return matched
    var value = matched
    while (value - previous > 0.5f) value -= 1f
    while (value - previous < -0.5f) value += 1f
    return value
}

private fun transformedParameter(
    parameter: Float,
    reversed: Boolean,
    phase: Float,
    closed: Boolean,
): Float = if (closed) {
    normalizeParameter(phase + if (reversed) -parameter else parameter, closed = true)
} else {
    normalizeParameter(phase + if (reversed) 1f - parameter else parameter, closed = false)
}

private fun normalizeParameter(parameter: Float, closed: Boolean): Float =
    if (closed) (parameter % 1f + 1f) % 1f else parameter.coerceIn(0f, 1f)

private fun pointAtNormalizedParameter(
    points: List<Offset3D>,
    parameter: Float,
    closed: Boolean,
): Offset3D {
    if (points.size == 1) return points.first()
    val position = if (closed) {
        normalizeParameter(parameter, true) * points.size
    } else {
        parameter.coerceIn(0f, 1f) * points.lastIndex
    }
    val lowerRaw = floor(position).toInt()
    val lower = if (closed) lowerRaw % points.size else lowerRaw.coerceIn(0, points.lastIndex)
    val upper = if (closed) (lower + 1) % points.size else (lower + 1).coerceAtMost(points.lastIndex)
    return lerp(points[lower], points[upper], position - lowerRaw)
}

private fun catmullRom(control: List<Offset3D>, closed: Boolean, steps: Int = 18): List<Offset3D> {
    if (control.size < 3) return control
    fun get(i: Int): Offset3D = if (closed) control[(i % control.size + control.size) % control.size]
    else control[i.coerceIn(0, control.lastIndex)]
    val result = ArrayList<Offset3D>()
    val segments = if (closed) control.size else control.lastIndex
    for (i in 0 until segments) {
        for (s in if (i == 0) 0..steps else 1..steps) {
            val t = s.toFloat() / steps
            val t2 = t * t
            val t3 = t2 * t
            val p0 = get(i - 1); val p1 = get(i); val p2 = get(i + 1); val p3 = get(i + 2)
            result += (p1 * 2f + (p2 - p0) * t +
                (p0 * 2f - p1 * 5f + p2 * 4f - p3) * t2 +
                (-p0 + p1 * 3f - p2 * 3f + p3) * t3) * 0.5f
        }
    }
    return result
}

private operator fun Offset3D.unaryMinus() = Offset3D(-x, -y, -z)
private fun Offset3D.normalizedSafe(): Offset3D {
    val len = sqrt(this dot this)
    return if (len < 1e-8f) Offset3D(1f, 0f, 0f) else this * (1f / len)
}
private fun lerp(a: Offset3D, b: Offset3D, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
private fun distance(a: Offset3D, b: Offset3D): Float = sqrt((b - a) dot (b - a))
private fun pointLineDistance(point: Offset3D, a: Offset3D, b: Offset3D): Float {
    val d = b - a
    val d2 = d dot d
    if (d2 < 1e-8f) return distance(point, a)
    val cross = (point - a) cross d
    return sqrt((cross dot cross) / d2)
}

// --- Dvojná přímkovost a hustá reprezentace plochy pro obrys ------------------

private const val SURFACE_CONSISTENCY_PROBE = 40
private const val SURFACE_CONSISTENCY_ALONG = 10
private const val SURFACE_CONSISTENCY_TOLERANCE_RATIO = 0.04f

/**
 * Z kandidátních rozvržení nechá jen ta, jejichž tvořice skutečně leží na téže
 * ploše jako primární (nejbohatší) rodina. Zborcená kvadrika je dvojně přímková,
 * takže její druhá regula projde; falešná druhá větev párovací heuristiky se
 * zahodí, a přestane tak plochu opticky protínat.
 */
private fun surfaceConsistentLayouts(
    state: MongeState,
    surface: RuledSurface3D,
    layouts: List<GeneralDirectrixLayout>,
): List<GeneralDirectrixLayout> {
    if (layouts.size <= 1) return layouts
    val componentGroups = layouts.groupBy {
        Triple(it.firstComponentIndex, it.secondComponentIndex, it.guideComponentIndex)
    }
    if (componentGroups.size > 1) {
        return componentGroups.values.flatMap { componentLayouts ->
            surfaceConsistentLayouts(state, surface, componentLayouts)
        }
    }
    val sampled = layouts.mapNotNull { layout ->
        val family = sampleGeneralLayout(state, surface, layout, SURFACE_CONSISTENCY_PROBE)
        if (family.size >= 2) layout to family else null
    }
    if (sampled.isEmpty()) return emptyList()
    val primary = sampled.maxByOrNull { it.second.size } ?: return emptyList()
    val closed = isDirectrixClosed(state, surface, primary.first.firstBoundary)
    val grid = familyToGrid(primary.second, SURFACE_CONSISTENCY_ALONG)
    val triangles = gridTriangles(grid, closed)
    val tolerance = (gridDiagonal(grid) * SURFACE_CONSISTENCY_TOLERANCE_RATIO).coerceAtLeast(1e-2f)
    val survivors = ArrayList<GeneralDirectrixLayout>()
    survivors += primary.first
    for ((layout, family) in sampled) {
        if (layout !== primary.first && familyLiesOnSurface(family, triangles, tolerance)) {
            survivors += layout
        }
    }
    return survivors
}

/**
 * Primární (nejbohatší, souvislá) rodina tvořic bez rozdělení počtu mezi větve.
 * Slouží jako podklad husté sítě pro výpočet obrysu, nezávisle na tom, kolik
 * tvořic se právě kreslí.
 */
fun sampleRuledSurfacePrimaryFamily(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<RuledSurfaceGenerator3D> {
    return sampleRuledSurfacePrimaryFamilies(state, surface, count).firstOrNull().orEmpty()
}

/** Jedna úplná regula pro každou nesouvislou komponentu řídicích křivek. */
fun sampleRuledSurfacePrimaryFamilies(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> {
    if (count <= 0) return emptyList()
    captureRuledSurfaceGeometry(state, surface)
    val signature = ruledSurfaceDirectrixGeometrySignature(state, surface)
    val cacheKey = surface.id to count
    synchronized(primaryGeneratorFamiliesCache) {
        primaryGeneratorFamiliesCache[state]?.get(cacheKey)?.let { cached ->
            if (cached.geometrySignature == signature) return cached.families
        }
    }
    val computed = computeRuledSurfacePrimaryFamilies(state, surface, count)
    synchronized(primaryGeneratorFamiliesCache) {
        primaryGeneratorFamiliesCache.getOrPut(state) { HashMap() }[cacheKey] =
            PrimaryGeneratorFamiliesCacheEntry(signature, computed)
    }
    return computed
}

private fun computeRuledSurfacePrimaryFamilies(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> {
    if (ruledSurfaceIsSphericalConoid(surface)) {
        return sampleSphericalConoidGeneratorFamilies(state, surface, count)
    }
    if (surface.directorPlaneId != null) {
        val firstComponents = sampleDirectrixComponents(state, surface, surface.firstBoundaryDirectrix)
        val secondComponents = sampleDirectrixComponents(state, surface, surface.secondBoundaryDirectrix)
        return firstComponents.flatMap { first ->
            secondComponents.mapNotNull { second ->
                sampleConoidGenerators(state, surface, count, first, second).takeIf { it.size >= 2 }
            }
        }
    }
    if (ruledSurfaceHasTwoLinesAndCurve(surface)) {
        return sampleTwoLinesAndCurveGeneratorFamilies(state, surface, count)
    }
    if (ruledSurfaceIsThreeLines(surface)) {
        return listOfNotNull(lineTransversalReguli(state, surface, count).firstOrNull())
    }
    val layouts = surfaceConsistentLayouts(state, surface, resolveGeneralDirectrixLayouts(state, surface))
    return layouts.groupBy {
        Triple(it.firstComponentIndex, it.secondComponentIndex, it.guideComponentIndex)
    }.values.mapNotNull { componentLayouts ->
        componentLayouts.firstOrNull()?.let { sampleGeneralLayout(state, surface, it, count) }?.takeIf { it.size >= 2 }
    }
}

/**
 * Primární rodiny s aplikovaným uživatelským ořezem/přesahem tvořic. Zdroj pro
 * fill a 3D mesh, aby výplň končila na skutečných koncích vykreslených tvořic.
 * Obrys a průniky dál čtou neořezané rodiny (definiční geometrii plochy).
 */
fun sampleRuledSurfaceTrimmedPrimaryFamilies(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> =
    sampleRuledSurfacePrimaryFamilies(state, surface, count).map { family ->
        family.map { trimRuledSurfaceGeneratorForDisplay(surface, it) }
    }

/** Síť z ořezaných rodin – varianta [ruledSurfacePrimaryGrids] pro fill. */
fun ruledSurfaceTrimmedPrimaryGrids(
    state: MongeState,
    surface: RuledSurface3D,
    rulings: Int,
    along: Int,
): List<List<List<Offset3D>>> {
    if (along < 2) return emptyList()
    return sampleRuledSurfacePrimaryFamilies(state, surface, rulings)
        .filter { it.size >= 2 }
        .map { family -> family.map { trimmedGeneratorGridRow(surface, it, along) } }
}

private const val EXTENSION_GRID_CELLS = 3

/**
 * Vzorky podél jedné tvořice pro fill s ořezem. Jádro mezi krajními řídicími
 * křivkami si drží vlastní rovnoměrné dělení a hranice buněk zůstávají na
 * původním rozsahu; přesah za křivku je samostatný dál dělený pás. Kdyby se
 * dělil rovnoměrně celý prodloužený rozsah, fold průmětu (křížení sousedních
 * tvořic) by padl do jedné dlouhé buňky a narovnaný čtyřúhelník by přetekl
 * daleko za skutečný obrys plochy.
 *
 * Počet sloupců musí být v celé rodině stejný (buňky se párují po sloupcích),
 * proto přítomnost pásů přesahu řídí jen nastavení plochy, ne délka tvořice.
 */
private fun trimmedGeneratorGridRow(
    surface: RuledSurface3D,
    generator: RuledSurfaceGenerator3D,
    along: Int,
): List<Offset3D> {
    val hasStartOverhang = !surface.generatorTrimStartEnabled && surface.generatorOverhangStart > 0f
    val hasEndOverhang = !surface.generatorTrimEndEnabled && surface.generatorOverhangEnd > 0f
    val direction = generator.end - generator.start
    val lengthSquared = direction dot direction
    val range = if (lengthSquared < 1e-8f) null else generatorTrimRange(surface, sqrt(lengthSquared))
    val coreLo = range?.min?.coerceIn(0f, 1f) ?: 0f
    val coreHi = range?.max?.coerceIn(0f, 1f) ?: 1f
    val params = ArrayList<Float>(along + 2 * EXTENSION_GRID_CELLS)
    if (hasStartOverhang) {
        val startParam = range?.min ?: coreLo
        for (k in 0 until EXTENSION_GRID_CELLS) {
            params += startParam + (coreLo - startParam) * k / EXTENSION_GRID_CELLS
        }
    }
    for (k in 0 until along) params += coreLo + (coreHi - coreLo) * k / (along - 1)
    if (hasEndOverhang) {
        val endParam = range?.max ?: coreHi
        for (k in 1..EXTENSION_GRID_CELLS) {
            params += coreHi + (endParam - coreHi) * k / EXTENSION_GRID_CELLS
        }
    }
    return params.map { generator.start + direction * it }
}

/** Hustá souřadnicová síť plochy (řádky = tvořice, sloupce = vzorky po tvořici). */
fun ruledSurfacePrimaryGrid(
    state: MongeState,
    surface: RuledSurface3D,
    rulings: Int,
    along: Int,
): List<List<Offset3D>>? {
    return ruledSurfacePrimaryGrids(state, surface, rulings, along).firstOrNull()
}

fun ruledSurfacePrimaryGrids(
    state: MongeState,
    surface: RuledSurface3D,
    rulings: Int,
    along: Int,
): List<List<List<Offset3D>>> {
    if (along < 2) return emptyList()
    return sampleRuledSurfacePrimaryFamilies(state, surface, rulings)
        .filter { it.size >= 2 }
        .map { familyToGrid(it, along) }
}

private fun familyToGrid(
    generators: List<RuledSurfaceGenerator3D>,
    along: Int,
): List<List<Offset3D>> = generators.map { generator ->
    List(along) { k -> lerp(generator.start, generator.end, k.toFloat() / (along - 1)) }
}

private data class SurfaceTriangle(val a: Offset3D, val b: Offset3D, val c: Offset3D)

private fun gridTriangles(grid: List<List<Offset3D>>, closed: Boolean): List<SurfaceTriangle> {
    val rows = grid.size
    if (rows < 2 || grid[0].size < 2) return emptyList()
    val along = grid[0].size
    val triangles = ArrayList<SurfaceTriangle>()
    val rowCount = if (closed) rows else rows - 1
    for (i in 0 until rowCount) {
        val next = (i + 1) % rows
        for (j in 0 until along - 1) {
            val p00 = grid[i][j]
            val p10 = grid[next][j]
            val p11 = grid[next][j + 1]
            val p01 = grid[i][j + 1]
            triangles += SurfaceTriangle(p00, p10, p11)
            triangles += SurfaceTriangle(p00, p11, p01)
        }
    }
    return triangles
}

private fun gridDiagonal(grid: List<List<Offset3D>>): Float {
    var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
    for (row in grid) for (p in row) {
        if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z
    }
    if (minX > maxX) return 0f
    return distance(Offset3D(minX, minY, minZ), Offset3D(maxX, maxY, maxZ))
}

private fun familyLiesOnSurface(
    family: List<RuledSurfaceGenerator3D>,
    triangles: List<SurfaceTriangle>,
    tolerance: Float,
): Boolean {
    if (triangles.isEmpty()) return false
    val tolerance2 = tolerance * tolerance
    val samples = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    return family.all { generator ->
        samples.all { t ->
            val point = lerp(generator.start, generator.end, t)
            var best = Float.POSITIVE_INFINITY
            for (triangle in triangles) {
                val d = pointTriangleDistanceSq(point, triangle)
                if (d < best) best = d
                if (best <= tolerance2) break
            }
            best <= tolerance2
        }
    }
}

/** Kvadrát vzdálenosti bodu od trojúhelníku (Ericson, Real-Time Collision Detection). */
private fun pointTriangleDistanceSq(p: Offset3D, triangle: SurfaceTriangle): Float {
    val a = triangle.a; val b = triangle.b; val c = triangle.c
    val ab = b - a; val ac = c - a; val ap = p - a
    val d1 = ab dot ap; val d2 = ac dot ap
    if (d1 <= 0f && d2 <= 0f) return sqLen(ap)
    val bp = p - b
    val d3 = ab dot bp; val d4 = ac dot bp
    if (d3 >= 0f && d4 <= d3) return sqLen(bp)
    val vc = d1 * d4 - d3 * d2
    if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
        val v = d1 / (d1 - d3)
        return sqLen(ap - ab * v)
    }
    val cp = p - c
    val d5 = ab dot cp; val d6 = ac dot cp
    if (d6 >= 0f && d5 <= d6) return sqLen(cp)
    val vb = d5 * d2 - d1 * d6
    if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
        val w = d2 / (d2 - d6)
        return sqLen(ap - ac * w)
    }
    val va = d3 * d6 - d5 * d4
    if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
        val w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
        return sqLen((p - b) - (c - b) * w)
    }
    val denominator = 1f / (va + vb + vc)
    val v = vb * denominator
    val w = vc * denominator
    val closest = a + ab * v + ac * w
    return sqLen(p - closest)
}

private fun sqLen(v: Offset3D): Float = v dot v
