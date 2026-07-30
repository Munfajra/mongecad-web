package model

import androidx.compose.ui.geometry.Offset
import model.classes.Arc2DNarys
import model.classes.Arc2DPudorys
import model.classes.ConicSectionNarys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import monge.input.meridian.trimConicPolylineByState
import state.MongeState

data class ConicPieceNarys(val conic: ConicSectionNarys) : MeridianPieceNarys {
    override val id: String get() = conic.id

    override fun startXZ(): Offset {
        // bude určeno až podle ořezu; fallback:
        return samplePolylineXZFallbackFirstLast().first()
    }
    override fun endXZ(): Offset {
        return samplePolylineXZFallbackFirstLast().last()
    }

    override fun samplePolylineXZ(state: MongeState, maxError: Float): List<Offset> {
        // 1) vezmi plnou polyline kuželosečky (stejně jako kreslíš)
        val full = buildFullConicPolylineNarys(state, conic, maxError)
        if (full.size < 2) return full

        // 2) aplikuj ořez podle toho, co pro tu kuželosečku existuje:
        val trimmed = trimConicPolylineByState(state, conic.id, full)
        return trimmed
    }

    override fun reversed(): MeridianPieceNarys = ReversedPieceNarys(this)

    private fun samplePolylineXZFallbackFirstLast(): List<Offset> = listOf(Offset.Zero, Offset.Zero) // nebude použito
}
/**
 * Vrátí polyline kuželosečky v nárysu, bez ořezu.
 * Implementuj tak, aby to bylo konzistentní s tím, co už kreslíš.
 */
fun buildFullConicPolylineNarys(
    state: MongeState,
    conic: ConicSectionNarys,
    maxError: Float
): List<Offset> {
    // ✅ doporučení: tady zavolej existující funkci, kterou používáš pro kreslení kuželoseček
    // Např. něco jako: return computeConicPolylineNarys(conic, state, maxError)
    //
    // Pokud zatím nic takového nemáš, tak to uděláme později.
    return emptyList()
}
data class SegmentPieceNarys(
    val seg: SegmentsNarys,
    override val id: String
) : MeridianPieceNarys {

    override fun startXZ(): Offset = Offset(seg.start.x, seg.start.z)
    override fun endXZ(): Offset   = Offset(seg.end.x, seg.end.z)

    override fun samplePolylineXZ(state: MongeState, maxError: Float): List<Offset> {
        // úsečka = jen 2 body (pokud chceš víc kvůli uniformitě, můžeš subdivide)
        return listOf(startXZ(), endXZ())
    }

    override fun reversed(): MeridianPieceNarys =
        ReversedPieceNarys(this)
}
data class ArcPieceNarys(val arc: Arc2DNarys) : MeridianPieceNarys {
    override val id: String get() = arc.id

    private fun ptAtRad(angle: Float): Offset {
        val cx = arc.center.x
        val cz = arc.center.z
        val x = cx + arc.radius * kotlin.math.cos(angle)
        val z = cz + arc.radius * kotlin.math.sin(angle)
        return Offset(x, z)
    }

    override fun startXZ(): Offset = ptAtRad(arc.startRad)
    override fun endXZ(): Offset   = ptAtRad(arc.endRad)

    override fun samplePolylineXZ(state: MongeState, maxError: Float): List<Offset> {
        val r = arc.radius
        val eps = maxError.coerceAtLeast(1e-4f)

        val d = (2f * kotlin.math.acos((1f - (eps / r)).coerceIn(-1f, 1f)))
            .takeIf { it.isFinite() && it > 1e-4f }
            ?: (kotlin.math.PI.toFloat() / 64f)

        val sweep = arc.sweepSigned()
        val steps = kotlin.math.max(2, kotlin.math.ceil(kotlin.math.abs(sweep) / d).toInt())

        val pts = ArrayList<Offset>(steps + 1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val ang = Arc2DNarys.norm(arc.startRad) + sweep * t
            pts += ptAtRad(ang)
        }
        return pts
    }

    override fun reversed(): MeridianPieceNarys = ReversedPieceNarys(this)
}
sealed interface MeridianPieceNarys {
    val id: String
    fun startXZ(): Offset
    fun endXZ(): Offset

    fun samplePolylineXZ(state: MongeState, maxError: Float): List<Offset>

    fun reversed(): MeridianPieceNarys
}
sealed interface MeridianPiecePudorys {
    val id: String
    fun startXY(): Offset
    fun endXY(): Offset

    fun samplePolylineXY(state: MongeState, maxError: Float): List<Offset>

    fun reversed(): MeridianPiecePudorys
}
data class ArcPiecePudorys(val arc: Arc2DPudorys) : MeridianPiecePudorys {
    override val id: String get() = arc.id

    private fun ptAtRad(angle: Float): Offset {
        val cx = arc.center.x
        val cy = arc.center.y
        val x = cx + arc.radius * kotlin.math.cos(angle)
        val y = cy - arc.radius * kotlin.math.sin(angle)
        return Offset(x, y)
    }

    override fun startXY(): Offset =
        ptAtRad(Arc2DPudorys.norm(arc.startRad))

    override fun endXY(): Offset =
        ptAtRad(Arc2DPudorys.norm(arc.endRad))

    override fun samplePolylineXY(state: MongeState, maxError: Float): List<Offset> {
        val r = arc.radius
        val eps = maxError.coerceAtLeast(1e-4f)

        val d = (2f * kotlin.math.acos((1f - (eps / r)).coerceIn(-1f, 1f)))
            .takeIf { it.isFinite() && it > 1e-4f }
            ?: (kotlin.math.PI.toFloat() / 64f)

        val sweep = arc.sweepSigned()
        val steps = kotlin.math.max(2, kotlin.math.ceil(kotlin.math.abs(sweep) / d).toInt())

        val pts = ArrayList<Offset>(steps + 1)
        val start = Arc2DPudorys.norm(arc.startRad)

        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val ang = start + sweep * t
            pts += ptAtRad(ang)
        }
        return pts
    }

    override fun reversed(): MeridianPiecePudorys = ReversedPiecePudorys(this)
}
data class SegmentPiecePudorys(
    val seg: SegmentsPudorys,
    override val id: String
) : MeridianPiecePudorys {

    override fun startXY(): Offset = Offset(seg.start.x, seg.start.y)
    override fun endXY(): Offset   = Offset(seg.end.x, seg.end.y)

    override fun samplePolylineXY(state: MongeState, maxError: Float): List<Offset> {
        return listOf(startXY(), endXY())
    }

    override fun reversed(): MeridianPiecePudorys =
        ReversedPiecePudorys(this)
}