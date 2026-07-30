package model

import androidx.compose.ui.geometry.Offset
import state.MongeState

data class ReversedPieceNarys(val base: MeridianPieceNarys) : MeridianPieceNarys {
    override val id: String get() = base.id
    override fun startXZ(): Offset = base.endXZ()
    override fun endXZ(): Offset   = base.startXZ()

    override fun samplePolylineXZ(state: MongeState, maxError: Float): List<Offset> {
        val pts = base.samplePolylineXZ(state, maxError)
        return pts.asReversed()
    }

    override fun reversed(): MeridianPieceNarys = base
}
data class ReversedPiecePudorys(val base: MeridianPiecePudorys) : MeridianPiecePudorys {
    override val id: String get() = base.id
    override fun startXY(): Offset = base.endXY()
    override fun endXY(): Offset   = base.startXY()

    override fun samplePolylineXY(state: MongeState, maxError: Float): List<Offset> {
        val pts = base.samplePolylineXY(state, maxError)
        return pts.asReversed()
    }

    override fun reversed(): MeridianPiecePudorys = base
}