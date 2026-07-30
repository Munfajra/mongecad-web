package monge.input.ruledsurface

import model.Offset3D
import model.classes.Line3D
import model.classes.RuledSurface3D
import model.classes.RuledSurfaceDirectrixKind
import model.classes.toOffset3D
import utils.nullSpaceVector
import utils.solve3x3
import utils.symmetricEigen
import state.MongeState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tři mimoběžné vlastní přímky určují zborcenou kvadriku. Pro centrální případ
 * ji zde rekonstruujeme přímo jako jednodílný hyperboloid, místo abychom
 * vzorkovali příčky v konečném okně tří nekonečných přímek.
 *
 * Implicitní kvadrika má deset koeficientů. Po dosazení každé přímky do její
 * rovnice musí vymizet koeficienty u t², t i konstanta, takže tři přímky dají
 * devět homogenních podmínek a kvadriku určí až na společný násobek. Z jejího
 * středu a vlastních os pak používáme analytické reguly eliptického
 * jednodílného hyperboloidu.
 */

private const val QUADRIC_EPS = 1e-8
private const val HEIGHT_EPS = 1e-4f
private const val FAMILY_SCORE_EPS = 1e-5f

private data class InfiniteLine(val point: Offset3D, val dir: Offset3D)

private data class HyperboloidModel(
    val center: Offset3D,
    val firstAxis: Offset3D,
    val secondAxis: Offset3D,
    val hyperbolicAxis: Offset3D,
    val firstRadius: Float,
    val secondRadius: Float,
    val hyperbolicRadius: Float,
    val generatedHandedness: Float,
    val lowerZ: Float,
    val upperZ: Float,
)

/** Vrátí true, právě když jsou všechny tři řídicí objekty obecné plochy přímky. */
internal fun ruledSurfaceIsThreeLines(surface: RuledSurface3D): Boolean {
    if (surface.directorPlaneId != null) return false
    val refs = listOfNotNull(
        surface.firstBoundaryDirectrix,
        surface.secondBoundaryDirectrix,
        surface.thirdDirectrix,
    )
    return refs.size == 3 && refs.all { it.kind == RuledSurfaceDirectrixKind.LINE }
}

/**
 * Jediná zobrazovaná regula analytického hyperboloidu. Je to regula opačná k
 * trojici zadaných přímek, takže každá vytvořená tvořice všechny tři protíná.
 * Všechny úsečky mají společný ořez z=0 až z=2·z_hrdla.
 */
internal fun lineTransversalReguli(
    state: MongeState,
    surface: RuledSurface3D,
    count: Int,
): List<List<RuledSurfaceGenerator3D>> {
    if (count < 2) return emptyList()
    val lines = surfaceLines(state, surface) ?: return emptyList()
    val model = fitOneSheetHyperboloid(lines) ?: return emptyList()
    val family = sampleHyperboloidRegulus(model, count)
    // Jediná tvořice rovnoběžná s ořezovými rovinami by v uzavřené reguli
    // vyrobila díru. Takovou polohu raději guard odmítne jako celek.
    return listOfNotNull(family.takeIf { it.size == count })
}

/**
 * Kulový konoid daný přímkou, řídicí rovinou a koulí. Rovina rovnoběžná s
 * řídicí rovinou ve vzdálenosti h od středu koule řeže kouli v kružnici. Bod
 * P(h) na řídicí přímce leží ve stejné rovině a určuje dvě tečny kružnice.
 *
 * Obě větve tečen skládáme do jedné uzavřené rodiny. V h = ±r se kružnice
 * smrští na bod; do rodiny proto vložíme právě jednu tvořici P(h)–(C±rn).
 */
internal fun sphericalConoidGeneratorFamily(
    line: Line3D,
    sphereCenter: Offset3D,
    sphereRadius: Float,
    directorNormal: Offset3D,
    count: Int,
): List<RuledSurfaceGenerator3D> {
    if (count < 4 || !sphereRadius.isFinite() || sphereRadius <= HEIGHT_EPS) return emptyList()
    val normal = directorNormal.safeNormalized() ?: return emptyList()
    val direction = line.direction.safeNormalized() ?: return emptyList()
    val planeRate = normal dot direction
    if (abs(planeRate) <= 1e-7f) return emptyList()

    val lineStart = line.start.toOffset3D()
    val lineDistance = ((sphereCenter - lineStart) cross direction).length()
    val tolerance = maxOf(HEIGHT_EPS, sphereRadius * 1e-5f)
    // Sečna ani tečna koule nedává reálnou regulární rodinu v celém rozsahu řezů.
    if (lineDistance <= sphereRadius + tolerance) return emptyList()

    fun generatorAt(height: Float, side: Float): RuledSurfaceGenerator3D? {
        val circleCenter = sphereCenter + normal * height
        val lineParameter = (normal dot (circleCenter - lineStart)) / planeRate
        val linePoint = lineStart + direction * lineParameter
        val fromCircleCenter = linePoint - circleCenter
        val distanceSquared = fromCircleCenter dot fromCircleCenter
        val circleRadiusSquared = maxOf(0f, sphereRadius * sphereRadius - height * height)
        if (distanceSquared <= circleRadiusSquared + tolerance * tolerance) return null

        val distance = sqrt(distanceSquared)
        if (circleRadiusSquared <= tolerance * tolerance) {
            return RuledSurfaceGenerator3D(linePoint, circleCenter)
        }
        val circleRadius = sqrt(circleRadiusSquared)
        val radial = fromCircleCenter * (1f / distance)
        val transverse = (normal cross radial).safeNormalized() ?: return null
        val alongRadial = circleRadiusSquared / distance
        val transverseLength = circleRadius * sqrt(distanceSquared - circleRadiusSquared) / distance
        val contact = circleCenter + radial * alongRadial + transverse * (side * transverseLength)
        return RuledSurfaceGenerator3D(linePoint, contact)
            .takeIf { contact.isFinitePoint() && distance3(linePoint, contact) > HEIGHT_EPS }
    }

    // První větev vede od nejnižšího k nejvyššímu řezu včetně obou pólů.
    // Druhá se vrací bez pólů, aby u tečných rovin vznikla opravdu jen jedna přímka.
    val firstSteps = count / 2
    val secondSteps = count - firstSteps
    val generators = ArrayList<RuledSurfaceGenerator3D>(count)
    for (index in 0..firstSteps) {
        // Kosinové rozložení odpovídá přirozenému oběhu dotykové křivky a
        // zahušťuje řezy u pólů, kde se poloměr kružnice mění jako odmocnina.
        val height = (-sphereRadius * cos(PI * index / firstSteps)).toFloat()
        generators += generatorAt(height, 1f) ?: return emptyList()
    }
    for (index in 1 until secondSteps) {
        val height = (sphereRadius * cos(PI * index / secondSteps)).toFloat()
        generators += generatorAt(height, -1f) ?: return emptyList()
    }
    return generators.takeIf { it.size == count }.orEmpty()
}

private fun surfaceLines(state: MongeState, surface: RuledSurface3D): List<Line3D>? {
    val lines = listOfNotNull(
        surface.firstBoundaryDirectrix,
        surface.secondBoundaryDirectrix,
        surface.thirdDirectrix,
    ).mapNotNull { ref -> ruledSurfaceLine(state, surface, ref) }
    return lines.takeIf { it.size == 3 }
}

private fun fitOneSheetHyperboloid(lines: List<Line3D>): HyperboloidModel? {
    val infinite = lines.map {
        val direction = it.direction.safeNormalized() ?: return null
        InfiniteLine(it.start.toOffset3D(), direction)
    }
    if (!infinite.arePairwiseSkew()) return null

    // Posun a jednotné měřítko výrazně zlepší SVD, aniž by změnily hlavní směry.
    var origin = infinite.map { it.point }.averagePoint()
    repeat(4) {
        origin = infinite.map { line -> closestPointToPointOnLine(line, origin) }.averagePoint()
    }
    val anchors = infinite.map { line -> closestPointToPointOnLine(line, origin) }
    val scale = maxOf(
        1f,
        anchors.maxOf { distance3(it, origin) },
        pairwiseClosestPoints(infinite).maxOfOrNull { distance3(it, origin) } ?: 1f,
    )
    val normalized = infinite.mapIndexed { index, line ->
        InfiniteLine((anchors[index] - origin) * (1f / scale), line.dir)
    }

    val coefficients = quadricThroughLines(normalized) ?: return null
    // Matice kvadratické formy je symetrická, takže stačí Cramer + Jacobi
    // (desktop tu volá EJML, na wasm ho nemáme – viz utils.LinAlg).
    val a = arrayOf(
        doubleArrayOf(coefficients[0], coefficients[3] * 0.5, coefficients[4] * 0.5),
        doubleArrayOf(coefficients[3] * 0.5, coefficients[1], coefficients[5] * 0.5),
        doubleArrayOf(coefficients[4] * 0.5, coefficients[5] * 0.5, coefficients[2]),
    )
    val linearHalfNegated = doubleArrayOf(
        -coefficients[6] * 0.5,
        -coefficients[7] * 0.5,
        -coefficients[8] * 0.5,
    )
    val centerLocal = solve3x3(a, linearHalfNegated) ?: return null
    val centerVector = Offset3D(
        centerLocal[0].toFloat(),
        centerLocal[1].toFloat(),
        centerLocal[2].toFloat(),
    )
    if (!centerVector.isFinitePoint()) return null
    val centerValue = evaluateQuadric(coefficients, centerVector)
    if (!centerValue.isFinite() || abs(centerValue) < QUADRIC_EPS) return null

    val decomposition = symmetricEigen(a) ?: return null
    val eigen = decomposition.mapNotNull { (value, vector) ->
        val axis = Offset3D(
            vector[0].toFloat(),
            vector[1].toFloat(),
            vector[2].toFloat(),
        ).safeNormalized() ?: return@mapNotNull null
        value / -centerValue to axis
    }
    if (eigen.size != 3) return null
    val coefficientScale = eigen.maxOf { abs(it.first) }
    if (coefficientScale < QUADRIC_EPS) return null
    val positive = eigen.filter { it.first > coefficientScale * 1e-6 }
    val negative = eigen.filter { it.first < -coefficientScale * 1e-6 }
    if (positive.size != 2 || negative.size != 1) return null

    val firstAxis = positive[0].second
    var secondAxis = positive[1].second
    val hyperbolicAxis = negative.single().second
    // Analytické vzorce předpokládají pravotočivou ortonormální bázi.
    if (((firstAxis cross secondAxis) dot hyperbolicAxis) < 0f) secondAxis = secondAxis * -1f

    val firstRadius = (scale / sqrt(positive[0].first)).toFloat()
    val secondRadius = (scale / sqrt(positive[1].first)).toFloat()
    val hyperbolicRadius = (scale / sqrt(-negative.single().first)).toFloat()
    if (listOf(firstRadius, secondRadius, hyperbolicRadius).any { !it.isFinite() || it <= HEIGHT_EPS }) return null

    val center = origin + centerVector * scale
    if (!center.z.isFinite() || center.z <= maxOf(HEIGHT_EPS, scale * 1e-5f)) return null
    val lowerZ = 0f
    val upperZ = 2f * center.z

    val inputHandedness = chooseInputHandedness(
        lines = infinite,
        center = center,
        firstAxis = firstAxis,
        secondAxis = secondAxis,
        hyperbolicAxis = hyperbolicAxis,
        firstRadius = firstRadius,
        secondRadius = secondRadius,
        hyperbolicRadius = hyperbolicRadius,
        scale = scale,
    ) ?: return null
    val generatedHandedness = -inputHandedness
    val verticalOscillation = sqrt(
        (firstRadius * firstAxis.z) * (firstRadius * firstAxis.z) +
            (secondRadius * secondAxis.z) * (secondRadius * secondAxis.z)
    )
    val verticalOffset = abs(hyperbolicRadius * hyperbolicAxis.z)
    if (verticalOffset - verticalOscillation <= maxOf(HEIGHT_EPS, scale * 1e-5f)) return null

    return HyperboloidModel(
        center = center,
        firstAxis = firstAxis,
        secondAxis = secondAxis,
        hyperbolicAxis = hyperbolicAxis,
        firstRadius = firstRadius,
        secondRadius = secondRadius,
        hyperbolicRadius = hyperbolicRadius,
        generatedHandedness = generatedHandedness,
        lowerZ = minOf(lowerZ, upperZ),
        upperZ = maxOf(lowerZ, upperZ),
    )
}

/** Devět lineárních podmínek pro deset koeficientů implicitní kvadriky. */
private fun quadricThroughLines(lines: List<InfiniteLine>): DoubleArray? {
    val constraints = Array(9) { DoubleArray(10) }
    lines.forEachIndexed { lineIndex, line ->
        val p = line.point
        val d = line.dir
        val rows = arrayOf(
            doubleArrayOf(
                d.x * d.x.toDouble(), d.y * d.y.toDouble(), d.z * d.z.toDouble(),
                d.x * d.y.toDouble(), d.x * d.z.toDouble(), d.y * d.z.toDouble(),
                0.0, 0.0, 0.0, 0.0,
            ),
            doubleArrayOf(
                2.0 * p.x * d.x, 2.0 * p.y * d.y, 2.0 * p.z * d.z,
                (p.x * d.y + p.y * d.x).toDouble(),
                (p.x * d.z + p.z * d.x).toDouble(),
                (p.y * d.z + p.z * d.y).toDouble(),
                d.x.toDouble(), d.y.toDouble(), d.z.toDouble(), 0.0,
            ),
            doubleArrayOf(
                p.x * p.x.toDouble(), p.y * p.y.toDouble(), p.z * p.z.toDouble(),
                p.x * p.y.toDouble(), p.x * p.z.toDouble(), p.y * p.z.toDouble(),
                p.x.toDouble(), p.y.toDouble(), p.z.toDouble(), 1.0,
            ),
        )
        rows.forEachIndexed { localRow, values ->
            values.forEachIndexed { column, value -> constraints[lineIndex * 3 + localRow][column] = value }
        }
    }
    // nullSpaceVector už vrací normalizovaný vektor jádra
    return nullSpaceVector(constraints)
}

private fun evaluateQuadric(q: DoubleArray, p: Offset3D): Double =
    q[0] * p.x * p.x + q[1] * p.y * p.y + q[2] * p.z * p.z +
        q[3] * p.x * p.y + q[4] * p.x * p.z + q[5] * p.y * p.z +
        q[6] * p.x + q[7] * p.y + q[8] * p.z + q[9]

/** Určí, ve které reguli leží vstupní přímky; vykreslí se opačná. */
private fun chooseInputHandedness(
    lines: List<InfiniteLine>,
    center: Offset3D,
    firstAxis: Offset3D,
    secondAxis: Offset3D,
    hyperbolicAxis: Offset3D,
    firstRadius: Float,
    secondRadius: Float,
    hyperbolicRadius: Float,
    scale: Float,
): Float? {
    fun score(handedness: Float): Float {
        var total = 0f
        for (line in lines) {
            val localX = line.dir dot firstAxis
            val localY = line.dir dot secondAxis
            val localZ = line.dir dot hyperbolicAxis
            if (abs(localZ) < FAMILY_SCORE_EPS) return Float.POSITIVE_INFINITY
            var sinTheta = -localX * hyperbolicRadius / (handedness * firstRadius * localZ)
            var cosTheta = localY * hyperbolicRadius / (handedness * secondRadius * localZ)
            val thetaNorm = sqrt(sinTheta * sinTheta + cosTheta * cosTheta)
            if (thetaNorm < FAMILY_SCORE_EPS) return Float.POSITIVE_INFINITY
            sinTheta /= thetaNorm
            cosTheta /= thetaNorm
            val waistPoint = center + firstAxis * (firstRadius * cosTheta) +
                secondAxis * (secondRadius * sinTheta)
            val candidateDirection = firstAxis * (-handedness * firstRadius * sinTheta) +
                secondAxis * (handedness * secondRadius * cosTheta) +
                hyperbolicAxis * hyperbolicRadius
            val candidateUnit = candidateDirection.safeNormalized() ?: return Float.POSITIVE_INFINITY
            val angularError = 1f - abs(candidateUnit dot line.dir)
            val lineDistance = distanceBetweenParallelLines(waistPoint, candidateUnit, line.point)
            total += 8f * angularError + lineDistance / scale.coerceAtLeast(1f)
        }
        return total / lines.size
    }

    val positiveScore = score(1f)
    val negativeScore = score(-1f)
    val best = minOf(positiveScore, negativeScore)
    if (!best.isFinite() || best > 0.02f) return null
    return if (positiveScore <= negativeScore) 1f else -1f
}

private fun sampleHyperboloidRegulus(
    model: HyperboloidModel,
    count: Int,
): List<RuledSurfaceGenerator3D> {
    val result = ArrayList<RuledSurfaceGenerator3D>(count)
    for (index in 0 until count) {
        val theta = (2.0 * PI * index / count).toFloat()
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        val waistPoint = model.center +
            model.firstAxis * (model.firstRadius * cosTheta) +
            model.secondAxis * (model.secondRadius * sinTheta)
        val direction = model.firstAxis * (-model.generatedHandedness * model.firstRadius * sinTheta) +
            model.secondAxis * (model.generatedHandedness * model.secondRadius * cosTheta) +
            model.hyperbolicAxis * model.hyperbolicRadius
        if (abs(direction.z) <= HEIGHT_EPS) continue
        val lower = waistPoint + direction * ((model.lowerZ - waistPoint.z) / direction.z)
        val upper = waistPoint + direction * ((model.upperZ - waistPoint.z) / direction.z)
        if (lower.isFinitePoint() && upper.isFinitePoint() && distance3(lower, upper) > HEIGHT_EPS) {
            result += RuledSurfaceGenerator3D(lower, upper)
        }
    }
    return result
}

private fun List<InfiniteLine>.arePairwiseSkew(): Boolean {
    for (i in indices) for (j in i + 1 until size) {
        val cross = this[i].dir cross this[j].dir
        val crossLength = cross.length()
        if (crossLength < 1e-5f) return false
        val distance = abs((this[j].point - this[i].point) dot cross) / crossLength
        if (distance < 1e-4f) return false
    }
    return true
}

private fun pairwiseClosestPoints(lines: List<InfiniteLine>): List<Offset3D> = buildList {
    for (i in lines.indices) for (j in i + 1 until lines.size) {
        add(closestPointOnLine(lines[i], lines[j]))
        add(closestPointOnLine(lines[j], lines[i]))
    }
}

private fun closestPointToPointOnLine(line: InfiniteLine, point: Offset3D): Offset3D =
    line.point + line.dir * ((point - line.point) dot line.dir)

private fun closestPointOnLine(target: InfiniteLine, other: InfiniteLine): Offset3D {
    val w0 = target.point - other.point
    val b = target.dir dot other.dir
    val d = target.dir dot w0
    val e = other.dir dot w0
    val denominator = 1f - b * b
    val t = if (abs(denominator) < 1e-8f) -d else (b * e - d) / denominator
    return target.point + target.dir * t
}

private fun distanceBetweenParallelLines(point: Offset3D, direction: Offset3D, otherPoint: Offset3D): Float =
    ((otherPoint - point) cross direction).length()

private fun List<Offset3D>.averagePoint(): Offset3D {
    val sum = reduce { acc, point -> acc + point }
    return sum * (1f / size)
}

private fun Offset3D.safeNormalized(): Offset3D? {
    val length = length()
    return if (!length.isFinite() || length < 1e-8f) null else this * (1f / length)
}

private fun Offset3D.isFinitePoint(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun distance3(a: Offset3D, b: Offset3D): Float = (b - a).length()
