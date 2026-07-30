package gl3d.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import gl3d.math.Mat4
import gl3d.math.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** Vzory čar. Číselné hodnoty musí sedět s `uPattern` v [LineShaders.FRAGMENT]. */
object LinePattern {
    const val SOLID = 0f
    const val DASHED = 1f
    const val DOTTED = 2f
    const val DASH_DOT = 3f
}

/**
 * Čárkovaný překryv má zůstat lehce užší než plná část téhož objektu –
 * převzato z `DASHED_STROKE_WIDTH_FACTOR` v desktopovém `LineTypes.kt`.
 */
private const val DASHED_STROKE_WIDTH_FACTOR = 0.8f

fun lineWidthForPattern(width: Float, pattern: Float): Float =
    if (pattern == LinePattern.DASHED) (width * DASHED_STROKE_WIDTH_FACTOR).coerceAtLeast(0.5f)
    else width

/**
 * Styl jedné kreslicí skupiny. Úseky se stejným stylem jdou na GPU v jednom
 * instancovaném draw callu, takže na počtu skupin (ne úseků) záleží.
 */
data class LineStyle3D(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
    /** Šířka v logických pixelech; renderer ji přenásobí DPR. */
    val width: Float,
    val pattern: Float = LinePattern.SOLID,
    /** Posun v okenní hloubce, jako desktopové `uDepthBias`. */
    val depthBias: Float = 0f,
) {
    companion object {
        fun of(
            color: Color,
            width: Float,
            alpha: Float = color.alpha,
            pattern: Float = LinePattern.SOLID,
            depthBias: Float = 0f,
            dim: Float = 1f,
        ) = LineStyle3D(
            red = color.red * dim,
            green = color.green * dim,
            blue = color.blue * dim,
            alpha = alpha,
            width = width,
            pattern = pattern,
            depthBias = depthBias,
        )
    }
}

/**
 * Přepočet světového bodu na obrazovkové pixely.
 *
 * Slouží jen k délkové souřadnici pro čárkování – ta se na desktopu počítá
 * taky na CPU (`projectedDistancePx` v `opengl/model/Conics.kt`), aby vzor
 * nezáležel na zoomu ani na zkrácení úsečky vůči pohledu.
 */
class ScreenProjector(
    private val viewProjection: Mat4,
    viewportWidth: Int,
    viewportHeight: Int,
) {
    private val halfW = viewportWidth.coerceAtLeast(1) * 0.5f
    private val halfH = viewportHeight.coerceAtLeast(1) * 0.5f

    /** Vzdálenost dvou bodů v obrazovkových pixelech. */
    fun distancePx(a: Vec3, b: Vec3): Float {
        val ca = viewProjection.transformPoint(a)
        val cb = viewProjection.transformPoint(b)
        if (abs(ca[3]) < 1e-9f || abs(cb[3]) < 1e-9f) return 0f
        val dx = (cb[0] / cb[3] - ca[0] / ca[3]) * halfW
        val dy = (cb[1] / cb[3] - ca[1] / ca[3]) * halfH
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Poloha bodu na plátně v pixelech, počátek vlevo nahoře.
     *
     * Osa `y` se obrací: v NDC roste nahoru, na obrazovce dolů. Používá se
     * pro popisky, které kreslí Compose nad plátnem – tam platí obrazovková
     * konvence.
     */
    fun screenPoint(point: Vec3): Offset? {
        val clip = viewProjection.transformPoint(point)
        if (abs(clip[3]) < 1e-9f) return null
        return Offset(
            x = (clip[0] / clip[3] * 0.5f + 0.5f) * (halfW * 2f),
            y = (0.5f - clip[1] / clip[3] * 0.5f) * (halfH * 2f),
        )
    }
}

/**
 * Sběrač úseků k vykreslení.
 *
 * Desktop pro každý objekt zakládá vlastní VAO a VBO uvnitř kreslicí funkce
 * (`glGenVertexArrays()` v každém draw callu v `LineTypes.kt`). To by v
 * prohlížeči bylo drahé – každé GL volání jde přes hranici wasm↔JS – takže se
 * tady všechno nejdřív posbírá do skupin podle stylu a nahraje najednou.
 */
class LineBatch {

    private val groups = LinkedHashMap<LineStyle3D, FloatBuilder>()

    fun isEmpty(): Boolean = groups.isEmpty()

    fun clear() {
        // Buildery se recyklují – při orbitování se batch přestavuje každý
        // snímek a nemá smysl pořád alokovat nová pole.
        for (builder in groups.values) builder.reset()
    }

    /** Jeden úsek. `distA`/`distB` jsou délkové souřadnice pro vzor čáry. */
    fun addSegment(a: Vec3, b: Vec3, style: LineStyle3D, distA: Float, distB: Float) {
        val builder = groups.getOrPut(style) { FloatBuilder() }
        builder.push(a.x, a.y, a.z)
        builder.push(b.x, b.y, b.z)
        builder.push(distA, distB)
    }

    /**
     * Úsek s délkovou souřadnicí spočítanou z promítnutí – to je běžný případ
     * pro přímky, úsečky a stopy.
     */
    fun addSegment(a: Vec3, b: Vec3, style: LineStyle3D, projector: ScreenProjector) {
        addSegment(a, b, style, 0f, projector.distancePx(a, b).coerceAtLeast(1e-6f))
    }

    /** Lomená čára se spojitou délkovou souřadnicí přes všechny úseky. */
    fun addPolyline(points: List<Vec3>, style: LineStyle3D, projector: ScreenProjector) {
        if (points.size < 2) return
        var dist = 0f
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val next = dist + projector.distancePx(a, b)
            addSegment(a, b, style, dist, next)
            dist = next
        }
    }

    /** `action(styl, data, počet použitých floatů, počet úseků)` */
    internal fun forEachGroup(action: (LineStyle3D, FloatArray, Int, Int) -> Unit) {
        for (entry in groups.entries) {
            val builder = entry.value
            val instances = builder.size / FLOATS_PER_SEGMENT
            if (instances > 0) action(entry.key, builder.data, builder.size, instances)
        }
    }

    internal companion object {
        /** posA(3) + posB(3) + dist(2) */
        const val FLOATS_PER_SEGMENT = 8
    }
}

/** Rostoucí pole floatů bez boxování, které by přineslo `MutableList<Float>`. */
internal class FloatBuilder(initialCapacity: Int = 256) {
    var data: FloatArray = FloatArray(initialCapacity)
        private set
    var size: Int = 0
        private set

    fun reset() {
        size = 0
    }

    fun push(a: Float, b: Float) {
        ensure(2)
        data[size++] = a
        data[size++] = b
    }

    fun push(a: Float, b: Float, c: Float) {
        ensure(3)
        data[size++] = a
        data[size++] = b
        data[size++] = c
    }

    private fun ensure(extra: Int) {
        if (size + extra <= data.size) return
        var capacity = data.size * 2
        while (capacity < size + extra) capacity *= 2
        data = data.copyOf(capacity)
    }
}
