package gl3d.scene

import androidx.compose.ui.graphics.Color
import geometry.conics.ConicType
import geometry.conics.classifyConicFromMatrix
import geometry.conics.sampleParametricEllipse
import geometry.conics.sampleParametricHyperbolaSimple
import geometry.conics.sampleParametricParabola
import gl3d.math.Vec3
import gl3d.math.toVec3
import gl3d.render.LineBatch
import gl3d.render.LinePattern
import gl3d.render.LineStyle3D
import gl3d.render.ScreenProjector
import model.classes.ConicSection3D
import model.classes.CylindricalSurface3D
import model.classes.IntersectionPartKind
import model.classes.Line3D
import model.classes.normalized
import model.classes.pointAtParam
import model.gl3dLineColor
import state.MongeState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zvýraznění vybraných objektů ve 3D – port `drawSelectionHighlight3D`
 * z `opengl/model/SelectionHighlight.kt`.
 *
 * Desktop pro každý typ objektu volá vlastní kreslicí funkci s vlastním VAO.
 * Tady se všechno sbírá do jednoho [LineBatch], protože ve webovém rendereru
 * prochází čárová grafika stejně jedním instancovaným draw callem. Vzor je
 * u všech typů stejný jako na desktopu: **široké halo** v modré a přes něj
 * objekt vlastní barvou o něco silněji, než se kreslí normálně.
 *
 * Batch se kreslí s `depthFunc = ALWAYS` a bez zápisu do hloubky, takže je
 * výběr vidět i skrz tělesa – to je celý smysl zvýraznění.
 *
 * Tělesa se zvýrazňují **obrysem**, ne vyplněním: koule prstencem siluety,
 * kužel a válec dvěma krajními tvořicími přímkami, rotační plocha dvěma
 * krajními tvořicími křivkami. **Přímková plocha se nezvýrazňuje** – desktopový
 * obrys stojí na sjednocení cest (`RuledSurfaceOutlines.kt`), které se
 * neportovalo, a náhrada přes zvýraznění všech tvořic scénu jen zaplaví.
 */
internal fun collectSelectionHighlight(
    state: MongeState,
    batch: LineBatch,
    projector: ScreenProjector,
    view: gl3d.camera.CameraMatrices,
    planeSize: Float,
) {
    val selectedCone = state.selectedCone.firstOrNull()
    // Výběr může přijít dvěma cestami: ze seznamu objektů, který plní přímo
    // trojrozměrné kolekce, nebo klikem na 2D plátně, které označí **průmět**.
    // Průměty drží odkaz na svůj 3D objekt (`parent`/`parentId`), takže se
    // z nich id doplní – jinak zůstane všechno vybrané na plátně ve 3D bez
    // zvýraznění. Kuželosečky to takhle měly od začátku.
    val selectedPointIds = (
        state.selectedPoints3D.map { it.id } +
            state.selectedPointsPudorys.mapNotNull { it.parent?.id } +
            state.selectedPointsNarys.mapNotNull { it.parent?.id } +
            state.selectedPointsBokorys.mapNotNull { it.parent?.id } +
            state.selectedPointsAxo.mapNotNull { it.parent?.id }
        )
        .filter { selectedCone == null || it != selectedCone.apexId }
        .toSet()
    val selectedLineIds = (
        state.selectedLines3D.map { it.id } +
            state.selectedLinesPudorys.mapNotNull { it.parentId } +
            state.selectedLinesNarys.mapNotNull { it.parentId } +
            state.selectedLinesBokorys.mapNotNull { it.parentId } +
            state.selectedLinesAxo.mapNotNull { it.parentId }
        ).toSet()
    val selectedSegmentIds = (
        state.selectedSegments3D.map { it.id } +
            state.selectedSegmentsPudorys.mapNotNull { it.parent?.id } +
            state.selectedSegmentsNarys.mapNotNull { it.parent?.id } +
            state.selectedSegmentsBokorys.mapNotNull { it.parent?.id } +
            state.selectedSegmentsAxo.mapNotNull { it.parentId }
        ).toMutableSet()
    val selectedPlaneIds = state.selectedPlanes.map { it.id }.toSet()
    val selectedConicIds = selectedConic3DIds(state).toMutableSet()
    // Průniková skupina může mít víc částí, ale stav drží jen jedno vybrané id –
    // při vybraném průniku se proto zvýrazní všechny jeho části.
    selectedConicIds += selectedIntersectionPartIds(state, IntersectionPartKind.CONIC3D)
    val selectedCurveIds = (
        selectedIntersectionPartIds(state, IntersectionPartKind.CURVE3D) +
            listOfNotNull(state.selectedCurve3DId)
        ).toSet()

    state.selectedPolygons.firstOrNull()?.segmentIds3D?.let { selectedSegmentIds += it }
    selectedCone?.let { selectedConicIds += it.directrixId }
    state.selectedCylinder.firstOrNull()?.let { cylinder ->
        selectedConicIds += cylinder.directrixId
        cylinder.upperConicId?.let { selectedConicIds += it }
    }
    state.selectedSolidOfRevolutionId?.let { sorId ->
        val narys = state.solidsOfRevolutionNarys.firstOrNull { it.id == sorId }
        val pudorys = state.solidsOfRevolutionPudorys.firstOrNull { it.id == sorId }
        selectedConicIds += narys?.circleIdsPudorys.orEmpty()
        selectedConicIds += narys?.circleIdsNarys.orEmpty()
        selectedConicIds += pudorys?.circleIdsPudorys.orEmpty()
        selectedConicIds += pudorys?.circleIdsNarys.orEmpty()
    }

    if (selectedPointIds.isEmpty() &&
        selectedLineIds.isEmpty() &&
        selectedSegmentIds.isEmpty() &&
        selectedPlaneIds.isEmpty() &&
        selectedConicIds.isEmpty() &&
        selectedCurveIds.isEmpty() &&
        state.selectedCone.isEmpty() &&
        state.selectedCylinder.isEmpty() &&
        state.selectedSpheres3D.isEmpty() &&
        state.selectedSolidOfRevolutionId == null
    ) return

    val camera = CameraBasis.of(view)

    for (line in state.lines3D) {
        if (line.id !in selectedLineIds || !line.show) continue
        val (a, b) = lineEnds(line)
        addHighlightedSegment(
            batch, projector, a, b,
            width = line.strokeWidth,
            color = line.color,
            pattern = line.lineStyle.toPatternValue(),
        )
    }

    for (segment in state.segments3D) {
        if (segment.id !in selectedSegmentIds || !segment.show) continue
        addHighlightedSegment(
            batch, projector, segment.start.toVec3(), segment.end.toVec3(),
            width = segment.strokeWidth,
            color = segment.color,
            pattern = segment.lineStyle.toPatternValue(),
        )
    }

    for (point in state.sharedPoints3D) {
        if (point.id !in selectedPointIds || !point.show) continue
        addRing(batch, projector, point.toVec3(), POINT_RING_RADIUS, camera.right, camera.up, 9.5f, 4.5f)
    }

    val conicsById = state.conics3D.associateBy { it.id }

    selectedCone?.let { cone ->
        val apex = state.sharedPoints3D.firstOrNull { it.id == cone.apexId }
        val directrix = conicsById[cone.directrixId]
        if (apex != null && directrix != null) {
            for ((a, b) in coneSilhouetteGenerators(apex.toVec3(), directrix, projector)) {
                addHighlightedSegment(batch, projector, a, b, width = SILHOUETTE_WIDTH)
            }
        }
    }

    state.selectedCylinder.firstOrNull()?.let { cylinder ->
        val directrix = conicsById[cylinder.directrixId]
        if (directrix != null) {
            for ((a, b) in cylinderSilhouetteGenerators(cylinder, directrix, projector)) {
                addHighlightedSegment(
                    batch, projector, a, b,
                    width = max(SILHOUETTE_WIDTH, cylinder.wireWidth),
                )
            }
        }
    }

    state.selectedSpheres3D.firstOrNull()?.let { sphere ->
        val center = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId }
        if (center != null) {
            val (right, up) = camera.silhouetteAxes()
            addRing(batch, projector, center.toVec3(), sphere.radius, right, up, 9f, 4f)
        }
    }

    state.selectedSolidOfRevolutionId?.let { sorId ->
        // Obrys vybrané rotační plochy: dvě krajní tvořicí křivky. Rovnoběžky
        // (kružnice) se zvýrazňují zvlášť přes `selectedConicIds`. Konvence osy
        // je stejná jako u sítě – nárys +z, půdorys +y.
        val narys = state.solidsOfRevolutionNarys.firstOrNull { it.id == sorId && it.show }
        val pudorys = state.solidsOfRevolutionPudorys.firstOrNull { it.id == sorId && it.show }
        val axisId = narys?.axisLine3DId ?: pudorys?.axisLine3DId
        val axis = axisId?.let { id -> state.lines3D.firstOrNull { it.id == id } }
        if (axis != null) {
            val axisPoint = axis.start.toVec3()
            narys?.let {
                addSorSilhouette(
                    batch, projector, axisPoint, Vec3(0f, 0f, 1f), axis.start.x,
                    it.sampledMeridianPolylineXZ, camera.depth, max(SILHOUETTE_WIDTH, it.strokeWidth),
                )
            }
            pudorys?.let {
                addSorSilhouette(
                    batch, projector, axisPoint, Vec3(0f, 1f, 0f), axis.start.x,
                    it.sampledMeridianPolylineXY, camera.depth, max(SILHOUETTE_WIDTH, it.strokeWidth),
                )
            }
        }
    }

    // Přímková plocha se nezvýrazňuje. Desktopový obrys stojí na sjednocení
    // cest (`RuledSurfaceOutlines.kt`), které se neportovalo, a náhrada přes
    // zvýraznění všech tvořic zaplaví scénu tolika čarami, že je to spíš na
    // obtíž než k užitku.

    for (plane in state.planes3D) {
        if (plane.id !in selectedPlaneIds || !plane.show) continue
        addPlaneHatching(batch, projector, plane.equation ?: continue, planeSize)
    }

    for (conic in state.conics3D) {
        if (conic.id !in selectedConicIds || !conic.show) continue
        addHighlightedConic(state, conic, batch, projector)
    }

    for (curve in state.curves3D) {
        if (curve.id !in selectedCurveIds || !curve.show) continue
        val points = sampleCurve3D(curve, state)
        if (points.size < 2) continue
        batch.addPolyline(points, haloStyle(curve.strokeWidth), projector)
        batch.addPolyline(
            points,
            frontStyle(curve.strokeWidth, curve.color, curve.lineStyle.toPatternValue()),
            projector,
        )
    }
}

// --- styly a základní tvary -----------------------------------------------

/** Modré halo pod objektem; `SelectionHalo` z desktopu. */
private val SELECTION_HALO = Color(0f, 0.4f, 1f, 1f)

/** Poloprůhledná modrá pro tvary, které pod sebou nemají vlastní objekt. */
private val SELECTION_FILL = Color(0f, 0.4f, 1f, 0.3f)

private const val SELECTION_DEPTH_BIAS = -2e-4f
private const val HALO_EXTRA_WIDTH = 7f
private const val FRONT_EXTRA_WIDTH = 1.5f
private const val SILHOUETTE_WIDTH = 3.2f
private const val POINT_RING_RADIUS = 12f
private const val TWO_PI = (2.0 * PI).toFloat()

private fun haloStyle(width: Float) = LineStyle3D.of(
    color = SELECTION_HALO,
    width = width + HALO_EXTRA_WIDTH,
    alpha = SELECTION_HALO.alpha,
    depthBias = SELECTION_DEPTH_BIAS,
)

private fun frontStyle(width: Float, color: Color, pattern: Float) = LineStyle3D.of(
    color = color.gl3dLineColor(),
    width = width + FRONT_EXTRA_WIDTH,
    alpha = color.alpha,
    pattern = pattern,
    depthBias = SELECTION_DEPTH_BIAS,
)

/**
 * Úsek se zvýrazněním. Bez vlastní barvy (siluety těles) se použije
 * poloprůhledná modrá, jak to dělá `drawHighlightedFiniteLine` na desktopu –
 * silueta pod sebou žádný nakreslený objekt nemá.
 */
private fun addHighlightedSegment(
    batch: LineBatch,
    projector: ScreenProjector,
    a: Vec3,
    b: Vec3,
    width: Float,
    color: Color? = null,
    pattern: Float = LinePattern.SOLID,
) {
    if (color == null) {
        batch.addSegment(a, b, haloStyle(width - HALO_EXTRA_WIDTH + 6f), projector)
        batch.addSegment(
            a, b,
            LineStyle3D.of(
                color = SELECTION_FILL,
                width = width + 2f,
                alpha = SELECTION_FILL.alpha,
                depthBias = SELECTION_DEPTH_BIAS,
            ),
            projector,
        )
        return
    }
    batch.addSegment(a, b, haloStyle(width), projector)
    batch.addSegment(a, b, frontStyle(width, color, pattern), projector)
}

/**
 * Prstenec kolem bodu nebo kolem siluety koule. Desktop ho kreslí jako
 * mezikruží z trojúhelníkového pásu; tady stačí uzavřená lomená čára dané
 * tloušťky, protože všechna čárová grafika stejně prochází tlustým rendererem.
 */
private fun addRing(
    batch: LineBatch,
    projector: ScreenProjector,
    center: Vec3,
    radius: Float,
    right: Vec3,
    up: Vec3,
    haloWidth: Float,
    frontWidth: Float,
    segments: Int = 96,
) {
    if (radius <= 0f || right.length() < 1e-6f || up.length() < 1e-6f) return
    val points = ArrayList<Vec3>(segments + 1)
    for (i in 0..segments) {
        val angle = TWO_PI * i / segments
        points += center + right * (cos(angle) * radius) + up * (sin(angle) * radius)
    }
    batch.addPolyline(
        points,
        LineStyle3D.of(
            color = SELECTION_HALO,
            width = haloWidth,
            alpha = SELECTION_HALO.alpha,
            depthBias = SELECTION_DEPTH_BIAS,
        ),
        projector,
    )
    batch.addPolyline(
        points,
        LineStyle3D.of(
            color = SELECTION_FILL,
            width = frontWidth,
            alpha = 0.86f,
            depthBias = SELECTION_DEPTH_BIAS,
        ),
        projector,
    )
}

// --- jednotlivé typy objektů ----------------------------------------------

private fun lineEnds(line: Line3D): Pair<Vec3, Vec3> {
    val trim = line.customTrimRange
    if (trim != null) {
        return line.pointAtParam(trim.min).toVec3() to line.pointAtParam(trim.max).toVec3()
    }
    val direction = line.direction.normalized().toVec3()
    val start = line.start.toVec3()
    return (start - direction * FREE_LINE_HALF_LENGTH) to (start + direction * FREE_LINE_HALF_LENGTH)
}

/** Stejná délka volné přímky jako v [SceneRenderer]. */
private const val FREE_LINE_HALF_LENGTH = 3000f

private fun addHighlightedConic(
    state: MongeState,
    conic: ConicSection3D,
    batch: LineBatch,
    projector: ScreenProjector,
) {
    val branches = sampleConic(conic, state) ?: return
    val polylines = listOfNotNull(
        branches.branch1.takeIf { it.size >= 2 },
        branches.branch2?.takeIf { it.size >= 2 },
    )
    for (branch in polylines) {
        val points = branch.map { it.toVec3() }
        batch.addPolyline(points, haloStyle(conic.strokeWidth), projector)
        batch.addPolyline(
            points,
            frontStyle(conic.strokeWidth, conic.color, conic.lineStyle.toPatternValue()),
            projector,
        )
    }
}

/**
 * Šrafování vybrané roviny.
 *
 * Desktop řeže rovinu krychlí a šrafuje vzniklý mnohoúhelník. Webový renderer
 * ale rovinu kreslí jako čtverec kolem paty kolmice z počátku (viz
 * `collectUserPlanes`), tak se šrafuje týž čtverec – jinak by zvýraznění
 * přesahovalo mimo nakreslenou rovinu.
 */
private fun addPlaneHatching(
    batch: LineBatch,
    projector: ScreenProjector,
    equation: model.classes.PlaneEquation,
    size: Float,
) {
    val frame = planeFrame(Vec3(equation.a, equation.b, equation.c), equation.d) ?: return
    val spacing = (2f * size / 18f).coerceIn(18f, 90f)
    val style = LineStyle3D.of(
        color = SELECTION_FILL,
        width = 1.8f,
        alpha = 0.86f,
        depthBias = SELECTION_DEPTH_BIAS,
    )
    val inset = min(2f * size * 0.08f, spacing * 0.35f)

    var v = -size
    while (v <= size) {
        batch.addSegment(
            frame.point(-size + inset, v),
            frame.point(size - inset, v),
            style,
            projector,
        )
        v += spacing
    }
}

private fun addSorSilhouette(
    batch: LineBatch,
    projector: ScreenProjector,
    axisPoint: Vec3,
    axisDir: Vec3,
    axisX0: Float,
    meridian: List<androidx.compose.ui.geometry.Offset>,
    viewDir: Vec3,
    width: Float,
) {
    if (meridian.isEmpty()) return
    val runs = sorSilhouettePolylines(axisPoint, axisDir, axisX0, meridian, viewDir)
    for (run in runs) {
        for (i in 0 until run.lastIndex) {
            addHighlightedSegment(batch, projector, run[i], run[i + 1], width = width)
        }
    }
}

/**
 * Dvě tvořicí přímky kužele, které v aktuálním pohledu tvoří jeho obrys:
 * z vrcholu se řídicí kuželosečka vidí pod jistým rozsahem úhlů a hledá se
 * největší mezera mezi nimi – její okraje jsou hledané tečné body.
 */
private fun coneSilhouetteGenerators(
    apex: Vec3,
    directrix: ConicSection3D,
    projector: ScreenProjector,
): List<Pair<Vec3, Vec3>> {
    val apexScreen = projector.screenPoint(apex) ?: return emptyList()
    val projected = sampleConicForSilhouette(directrix).mapNotNull { point ->
        val screen = projector.screenPoint(point) ?: return@mapNotNull null
        point to atan2(screen.y - apexScreen.y, screen.x - apexScreen.x)
    }
    if (projected.size < 3) return emptyList()

    val sorted = projected.sortedBy { it.second }
    var largestGap = -1f
    var gapIndex = 0
    for (i in sorted.indices) {
        val a = sorted[i].second
        val b = if (i == sorted.lastIndex) sorted[0].second + TWO_PI else sorted[i + 1].second
        val gap = b - a
        if (gap > largestGap) {
            largestGap = gap
            gapIndex = i
        }
    }
    return listOf(
        apex to sorted[gapIndex].first,
        apex to sorted[(gapIndex + 1) % sorted.size].first,
    )
}

/**
 * Dvě krajní tvořicí přímky válce. Tvořice jsou rovnoběžné, takže obrys tvoří
 * ty dvě, které leží nejdál na obě strany kolmo na jejich obrazovkový směr.
 */
private fun cylinderSilhouetteGenerators(
    surface: CylindricalSurface3D,
    directrix: ConicSection3D,
    projector: ScreenProjector,
): List<Pair<Vec3, Vec3>> {
    val generators = sampleConicForSilhouette(directrix).mapNotNull { base ->
        val top = cylinderTopPoint(surface, base) ?: return@mapNotNull null
        val baseScreen = projector.screenPoint(base) ?: return@mapNotNull null
        val topScreen = projector.screenPoint(top) ?: return@mapNotNull null
        CylinderGenerator(
            base = base,
            top = top,
            screenDx = topScreen.x - baseScreen.x,
            screenDy = topScreen.y - baseScreen.y,
            midX = (baseScreen.x + topScreen.x) * 0.5f,
            midY = (baseScreen.y + topScreen.y) * 0.5f,
        )
    }
    if (generators.size < 3) return emptyList()

    var dirX = 0f
    var dirY = 0f
    var longestDx = 0f
    var longestDy = 0f
    var longestLen = 0f
    for (generator in generators) {
        val length = sqrt(generator.screenDx * generator.screenDx + generator.screenDy * generator.screenDy)
        if (length <= 1e-3f) continue
        dirX += generator.screenDx / length
        dirY += generator.screenDy / length
        if (length > longestLen) {
            longestLen = length
            longestDx = generator.screenDx
            longestDy = generator.screenDy
        }
    }

    val dirLength = sqrt(dirX * dirX + dirY * dirY)
    val edgeDirX: Float
    val edgeDirY: Float
    when {
        dirLength > 1e-3f -> {
            edgeDirX = dirX / dirLength
            edgeDirY = dirY / dirLength
        }
        longestLen > 1e-3f -> {
            edgeDirX = longestDx / longestLen
            edgeDirY = longestDy / longestLen
        }
        else -> return emptyList()
    }

    val normalX = -edgeDirY
    val normalY = edgeDirX
    val first = generators.minByOrNull { it.midX * normalX + it.midY * normalY } ?: return emptyList()
    val second = generators.maxByOrNull { it.midX * normalX + it.midY * normalY } ?: return emptyList()
    if (first === second) return emptyList()

    return listOf(first.base to first.top, second.base to second.top)
}

private class CylinderGenerator(
    val base: Vec3,
    val top: Vec3,
    val screenDx: Float,
    val screenDy: Float,
    val midX: Float,
    val midY: Float,
)

/** Kde tvořice vedená bodem podstavy protne horní omezující rovinu válce. */
private fun cylinderTopPoint(surface: CylindricalSurface3D, base: Vec3): Vec3? {
    val equation = surface.equation ?: return null
    val direction = surface.direction.normalized().toVec3()
    if (direction.length() < 1e-6f) return null
    val denominator = equation.a * direction.x + equation.b * direction.y + equation.c * direction.z
    if (abs(denominator) < 1e-6f) return null
    val t = -((equation.a * base.x + equation.b * base.y + equation.c * base.z + equation.d) / denominator)
    return base + direction * t
}

private fun sampleConicForSilhouette(conic: ConicSection3D): List<Vec3> =
    runCatching {
        when (classifyConicFromMatrix(conic.matrix)) {
            ConicType.ELLIPSE -> sampleParametricEllipse(conic).map { it.toVec3() }
            ConicType.PARABOLA -> sampleParametricParabola(conic).map { it.toVec3() }
            ConicType.HYPERBOLA -> {
                val a = conic.a ?: return@runCatching emptyList()
                val b = conic.b ?: return@runCatching emptyList()
                val branches = sampleParametricHyperbolaSimple(conic, a, b)
                (branches.branch1 + branches.branch2.orEmpty()).map { it.toVec3() }
            }
            ConicType.DEGENERATE -> emptyList()
        }
    }.getOrDefault(emptyList())

// --- pomocné výběry ze stavu ----------------------------------------------

private fun selectedConic3DIds(state: MongeState): Set<String> =
    (state.selectedConicsPudorys.mapNotNull { it.parentId } +
        state.selectedConicsNarys.mapNotNull { it.parentId } +
        state.selectedConicsBokorys.mapNotNull { it.parentId } +
        state.selectedConicsAxo.mapNotNull { it.parentId })
        .toSet()

/**
 * Průniková skupina může mít víc částí (víc větví křivky i víc kuželoseček
 * z jednoho průniku), ale stav drží jen jedno `selectedCurve3DId` – při
 * vybraném průniku se proto zvýrazní všechny jeho části.
 */
private fun selectedIntersectionPartIds(
    state: MongeState,
    kind: IntersectionPartKind,
): Set<String> = state.selectedIntersectionGroupId
    ?.let { id -> state.intersectionGroups.firstOrNull { it.id == id } }
    ?.parts
    ?.filter { it.kind == kind }
    ?.mapTo(mutableSetOf()) { it.id }
    .orEmpty()

/**
 * Osy kamery ve světových souřadnicích, čtené z řádků pohledové matice –
 * `cameraRight`/`cameraUp`/`cameraDepth` z desktopu.
 */
private class CameraBasis(val right: Vec3, val up: Vec3, val depth: Vec3) {

    /**
     * Báze roviny siluety koule: kolmá na pohled a stabilizovaná světovým +Z,
     * takže prstenec obkresluje obrys a ne libovolný kruh na kouli.
     */
    fun silhouetteAxes(): Pair<Vec3, Vec3> {
        var right = (Vec3.UNIT_Z cross depth).normalized()
        if (right.length() < 1e-6f) right = Vec3(1f, 0f, 0f)
        return right to (depth cross right).normalized()
    }

    companion object {
        fun of(view: gl3d.camera.CameraMatrices): CameraBasis {
            val m = view.view.data
            return CameraBasis(
                right = Vec3(m[0], m[4], m[8]).normalized(),
                up = Vec3(m[1], m[5], m[9]).normalized(),
                depth = Vec3(m[2], m[6], m[10]).normalized(),
            )
        }
    }
}
