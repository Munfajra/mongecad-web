package serialization.classes

import kotlinx.serialization.Serializable
import model.ArcMode
import serialization.SerializableOffset
import state.MongeState

@Serializable
data class SerializedEllipseArcPudorys(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset,
    val mode: ArcMode = ArcMode.SHORTEST
)
fun MongeState.serializeEllipseArcsPudorys(): List<SerializedEllipseArcPudorys> {
    return ellipseArcEnds.map { (conicId, ends) ->
        val (a, b) = ends
        val mode = ellipseArcMode[conicId] ?: ArcMode.SHORTEST
        SerializedEllipseArcPudorys(
            conicId = conicId,
            pA = SerializableOffset(x = a.x, y = a.y),
            pB = SerializableOffset(x = b.x, y = b.y),
            mode = mode
        )

    }
}
@Serializable
data class SerializedParabolaArcPudorys(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset
)
fun MongeState.serializeParabolaArcsPudorys() =
    parabolaArcEnds.map { (id, ends) ->
        val (a,b) = ends
        SerializedParabolaArcPudorys(id,
            pA = SerializableOffset(x = a.x, y = a.y),
            pB = SerializableOffset(x = b.x, y = b.y)
        )
    }
@Serializable
data class SerializedHyperbolaBranchPudorys(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset
)
fun MongeState.serializeHyperbolaBranch1P(): List<SerializedHyperbolaBranchPudorys> =
    hyperbolaArcBranch1.map { (id, ends) ->
        val (a,b) = ends
        SerializedHyperbolaBranchPudorys(id,
            pA = SerializableOffset(x = a.x, y = a.y),
            pB = SerializableOffset(x = b.x, y = b.y)
        )
    }

fun MongeState.serializeHyperbolaBranch2P(): List<SerializedHyperbolaBranchPudorys> =
    hyperbolaArcBranch2.map { (id, ends) ->
        val (a,b) = ends
        SerializedHyperbolaBranchPudorys(id,
            pA = SerializableOffset(x = a.x, y = a.y),
            pB = SerializableOffset(x = b.x, y = b.y)
        )
    }
