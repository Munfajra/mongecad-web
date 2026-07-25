package serialization.classes

import kotlinx.serialization.Serializable
import model.ArcMode
import serialization.SerializableOffset
import state.MongeState


@Serializable
data class SerializedEllipseArcNarys(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset,
    val mode: ArcMode = ArcMode.SHORTEST
)

fun MongeState.serializeEllipseArcsNarys(): List<SerializedEllipseArcNarys> =
    ellipseArcEnds.map { (conicId, ends) ->
        val (a, b) = ends
        SerializedEllipseArcNarys(
            conicId = conicId,
            pA = SerializableOffset(x = a.x, y = a.y),
            pB = SerializableOffset(x = b.x, y = b.y),
            mode = ellipseArcMode[conicId] ?: ArcMode.SHORTEST
        )
    }

@Serializable
data class SerializedParabolaArcNarys(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset
)
fun MongeState.serializeParabolaArcsNarys() =
    parabolaArcEnds.map { (id, ends) ->
        val (a,b) = ends
        SerializedParabolaArcNarys(id,
            pA = SerializableOffset(x = a.x, y = a.y),
            pB = SerializableOffset(x = b.x, y = b.y)
        )
    }

@Serializable
data class SerializedHyperbolaBranchNarys(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset
)

fun MongeState.serializeHyperbolaBranch1N(): List<SerializedHyperbolaBranchNarys> =
    hyperbolaArcBranch1.map { (id, ends) ->
        val (a,b) = ends
        SerializedHyperbolaBranchNarys(id,
            pA = SerializableOffset(x = a.x, y = a.y),
            pB = SerializableOffset(x = b.x, y = b.y)
        )
    }

fun MongeState.serializeHyperbolaBranch2N(): List<SerializedHyperbolaBranchNarys> =
    hyperbolaArcBranch2.map { (id, ends) ->
        val (a,b) = ends
        SerializedHyperbolaBranchNarys(id,
            pA = SerializableOffset(x = a.x, y = a.y),
            pB = SerializableOffset(x = b.x, y = b.y)
        )
    }