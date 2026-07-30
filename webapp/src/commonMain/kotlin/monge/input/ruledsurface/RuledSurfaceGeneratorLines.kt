package monge.input.ruledsurface

import androidx.compose.ui.geometry.Offset
import monge.input.axo.projectDirection3DToAxo
import model.LineStyle
import model.Point3D
import model.classes.Line3D
import model.classes.Line3DProjectionAxo
import model.classes.Line3DProjectionBokorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.LineTrimRange
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.RuledSurface3D
import model.classes.projectPoint3DToAxoLocal
import state.MongeState
import utils.allocIndex
import kotlin.math.sqrt

/** Verze materializace tvořic; zvednutí vynutí přegenerování při načtení starších souborů. */
internal const val RULED_GENERATOR_LINES_VERSION = 9

/** Průmět tvořic přímkové plochy adresovaný z přepínačů viditelnosti. */
enum class RuledSurfaceProjection { PUDORYS, NARYS, BOKORYS, AXO }

/** Per-view viditelnost průmětů tvořic; přežívá přegenerování rodiny. */
private data class GeneratorProjectionVisibility(
    val pudorys: Boolean,
    val narys: Boolean,
    val bokorys: Boolean,
    val axo: Boolean,
)

private val DEFAULT_GENERATOR_VISIBILITY = GeneratorProjectionVisibility(
    pudorys = false, narys = false, bokorys = false, axo = true,
)

/**
 * Tvořice plochy jako skutečné objekty Line3D. Ořez (customTrimRange 0..1 přes
 * start→start+direction) drží úsek od jedné řídicí křivky ke druhé, takže je
 * stejný v prostoru i ve všech průmětech; uživatel si ho může přenastavit jako
 * u běžné přímky. Tvořice nemají jméno, aby neplnily plátno popisky.
 */
fun syncRuledSurfaceGeneratorLines(state: MongeState, surface: RuledSurface3D) {
    val visibility = currentGeneratorProjectionVisibility(state, surface)
    removeRuledSurfaceGeneratorLines(state, surface)
    val ids = ArrayList<String>()
    for (generator in sampleRuledSurfaceGenerators(state, surface)) {
        val direction = generator.end - generator.start
        val lengthSquared = direction dot direction
        if (lengthSquared < 1e-8f) continue
        val line3D = Line3D(
            start = Point3D(generator.start.x, generator.start.y, generator.start.z, ""),
            direction = direction,
            name = "",
            color = surface.color,
            lineStyle = LineStyle.Solid,
            strokeWidth = surface.wireWidth,
            creationIndex = allocIndex(state),
            show = surface.show,
            customTrimRange = generatorTrimRange(surface, sqrt(lengthSquared)),
        )
        state.lines3D.add(line3D)
        addGeneratorLineProjections(state, line3D, visibility)
        ids += line3D.id
    }
    surface.generatorLineIds = ids
    surface.generatorLinesVersion = RULED_GENERATOR_LINES_VERSION
}

/**
 * Ořez jedné tvořice z nastavení plochy. Ořezaná strana drží podíl mezi
 * krajními řídicími křivkami (0 = první, 1 = druhá); neořezaná strana se
 * prodlužuje o absolutní přesah, takže je stejně dlouhá u všech tvořic bez
 * ohledu na jejich délku.
 */
internal fun generatorTrimRange(surface: RuledSurface3D, generatorLength: Float): LineTrimRange {
    val min = if (surface.generatorTrimStartEnabled) {
        surface.generatorTrimStart
    } else {
        -surface.generatorOverhangStart / generatorLength
    }
    val max = if (surface.generatorTrimEndEnabled) {
        surface.generatorTrimEnd
    } else {
        1f + surface.generatorOverhangEnd / generatorLength
    }
    return LineTrimRange(min, max).normalized()
}

/**
 * Tvořice s aplikovaným uživatelským ořezem/přesahem plochy. Zdroj pro fill a
 * 3D mesh, aby výplň obkreslovala skutečné konce vykreslených tvořic. Obrys a
 * průniky zůstávají na definičním rozsahu 0..1.
 */
fun trimRuledSurfaceGeneratorForDisplay(
    surface: RuledSurface3D,
    generator: RuledSurfaceGenerator3D,
): RuledSurfaceGenerator3D {
    val direction = generator.end - generator.start
    val lengthSquared = direction dot direction
    if (lengthSquared < 1e-8f) return generator
    val range = generatorTrimRange(surface, sqrt(lengthSquared))
    if (range.min == 0f && range.max == 1f) return generator
    return RuledSurfaceGenerator3D(
        start = generator.start + direction * range.min,
        end = generator.start + direction * range.max,
    )
}

/** Hash nastavení ořezu tvořic pro invalidaci cache geometrií odvozených z ořezu. */
fun ruledSurfaceGeneratorTrimSignature(surface: RuledSurface3D): Int {
    var result = surface.generatorTrimStartEnabled.hashCode()
    result = 31 * result + surface.generatorTrimEndEnabled.hashCode()
    result = 31 * result + surface.generatorTrimStart.hashCode()
    result = 31 * result + surface.generatorTrimEnd.hashCode()
    result = 31 * result + surface.generatorOverhangStart.hashCode()
    result = 31 * result + surface.generatorOverhangEnd.hashCode()
    return result
}

/**
 * Per-view viditelnost pro (pře)generované tvořice. Zdroj pravdy jsou přepínače
 * průmětů plochy (showInAxo obrysových křivek) – nové tvořice se ukážou jen v
 * pohledech, kde je plocha viditelná. Fallback: dosavadní průměty tvořic, pak default.
 */
private fun currentGeneratorProjectionVisibility(
    state: MongeState,
    surface: RuledSurface3D,
): GeneratorProjectionVisibility {
    val ids = surface.generatorLineIds.toSet()
    fun previous(default: Boolean, lookup: () -> Boolean?): Boolean =
        (if (ids.isEmpty()) null else lookup()) ?: default
    return GeneratorProjectionVisibility(
        pudorys = surface.outlineCurveIdPudorys?.let { id -> state.curvesPudorys.firstOrNull { it.id == id }?.showInAxo }
            ?: previous(DEFAULT_GENERATOR_VISIBILITY.pudorys) {
                state.lines3DPudorys.firstOrNull { (it.parent?.id ?: it.parentId) in ids }?.showInAxo
            },
        narys = surface.outlineCurveIdNarys?.let { id -> state.curvesNarys.firstOrNull { it.id == id }?.showInAxo }
            ?: previous(DEFAULT_GENERATOR_VISIBILITY.narys) {
                state.lines3DNarys.firstOrNull { (it.parent?.id ?: it.parentId) in ids }?.showInAxo
            },
        bokorys = surface.outlineCurveIdBokorys?.let { id -> state.curvesBokorys.firstOrNull { it.id == id }?.showInAxo }
            ?: previous(DEFAULT_GENERATOR_VISIBILITY.bokorys) {
                state.lines3DBokorys.firstOrNull { (it.parent?.id ?: it.parentId) in ids }?.showInAxo
            },
        axo = surface.outlineCurveIdAxo?.let { id -> state.curvesAxo.firstOrNull { it.id == id }?.showInAxo }
            ?: previous(DEFAULT_GENERATOR_VISIBILITY.axo) {
                state.lines3DAxo.firstOrNull { (it.parent?.id ?: it.parentId) in ids }?.showInAxo
            },
    )
}

private fun addGeneratorLineProjections(
    state: MongeState,
    line3D: Line3D,
    visibility: GeneratorProjectionVisibility,
) {
    val x0 = line3D.start.x; val y0 = line3D.start.y; val z0 = line3D.start.z
    val dx = line3D.direction.x; val dy = line3D.direction.y; val dz = line3D.direction.z
    state.lines3DPudorys.add(
        Line3DProjectionPudorys(
            point = Point3DPudorys(x0, y0), direction = Offset(dx, dy),
            localName = line3D.name, parent = line3D,
            localColor = line3D.color, localStrokeWidth = line3D.strokeWidth, localLineStyle = line3D.lineStyle,
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.pudorys,
        )
    )
    state.lines3DNarys.add(
        Line3DProjectionNarys(
            point = Point3DNarys(x0, z0), direction = Offset(dx, dz),
            localName = line3D.name, parent = line3D,
            localColor = line3D.color, localStrokeWidth = line3D.strokeWidth, localLineStyle = line3D.lineStyle,
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.narys,
        )
    )
    state.lines3DBokorys.add(
        Line3DProjectionBokorys(
            point = Point3DBokorys(y0, z0), direction = Offset(dy, dz),
            localName = line3D.name, parent = line3D,
            localColor = line3D.color, localStrokeWidth = line3D.strokeWidth, localLineStyle = line3D.lineStyle,
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.bokorys,
        )
    )
    val basis = state.basis
    if (basis != null) {
        val pAxo = projectPoint3DToAxoLocal(model.Offset3D(x0, y0, z0), basis)
        val dAxo = projectDirection3DToAxo(model.Offset3D(dx, dy, dz), basis)
        state.lines3DAxo.add(
            Line3DProjectionAxo(
                p = Point3DAxo(x = pAxo.x, y = pAxo.y), dir = dAxo,
                localName = line3D.name, parent = line3D,
                localColor = line3D.color, localStrokeWidth = line3D.strokeWidth, localLineStyle = line3D.lineStyle,
                creationIndex = allocIndex(state),
                showInAxoInitial = visibility.axo,
                // Ořez tvořice drží customTrimRange; ořez o průmětny (1. oktant)
                // by ho přebil, proto je u tvořic vypnutý bez ohledu na globální default.
                clipToOctant = false,
            )
        )
    }
}

/** Nastaví viditelnost jednoho průmětu všech tvořic plochy (přepínač A/P/N/B). */
fun setRuledSurfaceGeneratorProjectionVisible(
    state: MongeState,
    surface: RuledSurface3D,
    projection: RuledSurfaceProjection,
    visible: Boolean,
) {
    val ids = surface.generatorLineIds.toSet()
    if (ids.isEmpty()) return
    when (projection) {
        RuledSurfaceProjection.PUDORYS -> state.lines3DPudorys.forEach {
            if ((it.parent?.id ?: it.parentId) in ids) it.showInAxo = visible
        }
        RuledSurfaceProjection.NARYS -> state.lines3DNarys.forEach {
            if ((it.parent?.id ?: it.parentId) in ids) it.showInAxo = visible
        }
        RuledSurfaceProjection.BOKORYS -> state.lines3DBokorys.forEach {
            if ((it.parent?.id ?: it.parentId) in ids) it.showInAxo = visible
        }
        RuledSurfaceProjection.AXO -> state.lines3DAxo.forEach {
            if ((it.parent?.id ?: it.parentId) in ids) it.showInAxo = visible
        }
    }
}

/**
 * Po převodu do axonometrie se Monge průměty tvořic schovají (axonometrický
 * zůstává). Uživatel si je může vrátit přepínači průmětů v editaci plochy.
 */
fun hideRuledSurfaceGeneratorMongeProjections(state: MongeState) {
    state.ruledSurfaces.forEach { surface ->
        setRuledSurfaceGeneratorProjectionVisible(state, surface, RuledSurfaceProjection.PUDORYS, false)
        setRuledSurfaceGeneratorProjectionVisible(state, surface, RuledSurfaceProjection.NARYS, false)
        setRuledSurfaceGeneratorProjectionVisible(state, surface, RuledSurfaceProjection.BOKORYS, false)
    }
}

/** Odstraní materializované tvořice plochy (3D přímky i jejich průměty). */
fun removeRuledSurfaceGeneratorLines(state: MongeState, surface: RuledSurface3D) {
    val ids = surface.generatorLineIds.toSet()
    if (ids.isEmpty()) return
    state.lines3D.removeAll { it.id in ids }
    state.lines3DPudorys.removeAll { (it.parent?.id ?: it.parentId) in ids }
    state.lines3DNarys.removeAll { (it.parent?.id ?: it.parentId) in ids }
    state.lines3DBokorys.removeAll { (it.parent?.id ?: it.parentId) in ids }
    state.lines3DAxo.removeAll { (it.parent?.id ?: it.parentId) in ids }
    state.selectedLines3D.removeAll { it.id in ids }
    surface.generatorLineIds = emptyList()
}

/** Propíše barvu, šířku a viditelnost plochy do jejích materializovaných tvořic. */
fun updateRuledSurfaceGeneratorLineStyle(state: MongeState, surface: RuledSurface3D) {
    val ids = surface.generatorLineIds.toSet()
    if (ids.isEmpty()) return
    // Vlastnosti Line3D jsou mutable a průměty je čtou přes parent, takže stačí
    // upravit rodiče na místě; překreslení řídí triggerRedraw volajícího.
    state.lines3D.forEach { line ->
        if (line.id in ids) {
            line.color = surface.color
            line.strokeWidth = surface.wireWidth
            line.show = surface.show
        }
    }
}

/** Přegeneruje tvořice všech ploch (např. po normalizaci axonometrie). */
fun refreshRuledSurfaceGeneratorLines(state: MongeState) {
    state.ruledSurfaces.forEach { surface -> syncRuledSurfaceGeneratorLines(state, surface) }
}

/**
 * Doplní tvořice plochám ze starších souborů/snapshotů, kde ještě nebyly
 * skutečnými přímkami. Aktuální plochy s existujícími přímkami nechává být.
 */
fun ensureRuledSurfaceGeneratorLines(state: MongeState) {
    state.ruledSurfaces.forEach { surface ->
        val complete = surface.generatorLinesVersion >= RULED_GENERATOR_LINES_VERSION &&
            surface.generatorLineIds.isNotEmpty() &&
            surface.generatorLineIds.all { id -> state.lines3D.any { it.id == id } }
        if (!complete && surface.generatorCount > 0) {
            syncRuledSurfaceGeneratorLines(state, surface)
        }
    }
}
