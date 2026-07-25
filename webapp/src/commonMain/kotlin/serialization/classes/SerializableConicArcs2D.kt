package serialization.classes

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import model.ArcMode
import serialization.SerializableOffset
import state.MongeState

@Serializable
data class SerializedEllipseArc2D(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset,
    val mode: ArcMode = ArcMode.SHORTEST
)

@Serializable
data class SerializedParabolaArc2D(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset
)

@Serializable
data class SerializedHyperbolaBranch2D(
    val conicId: String,
    val pA: SerializableOffset,
    val pB: SerializableOffset
)

private fun Offset.toSerializableOffset(): SerializableOffset =
    SerializableOffset(x = x, y = y)

fun MongeState.serializeEllipseArcs2D(conicIds: Set<String>): List<SerializedEllipseArc2D> =
    conicIds.mapNotNull { conicId ->
        val ends = ellipseArcEnds[conicId] ?: return@mapNotNull null
        SerializedEllipseArc2D(
            conicId = conicId,
            pA = ends.first.toSerializableOffset(),
            pB = ends.second.toSerializableOffset(),
            mode = ellipseArcMode[conicId] ?: ArcMode.SHORTEST
        )
    }

fun MongeState.serializeParabolaArcs2D(conicIds: Set<String>): List<SerializedParabolaArc2D> =
    conicIds.mapNotNull { conicId ->
        val ends = parabolaArcEnds[conicId] ?: return@mapNotNull null
        SerializedParabolaArc2D(
            conicId = conicId,
            pA = ends.first.toSerializableOffset(),
            pB = ends.second.toSerializableOffset()
        )
    }

fun MongeState.serializeHyperbolaBranch1_2D(conicIds: Set<String>): List<SerializedHyperbolaBranch2D> =
    conicIds.mapNotNull { conicId ->
        val ends = hyperbolaArcBranch1[conicId] ?: return@mapNotNull null
        SerializedHyperbolaBranch2D(
            conicId = conicId,
            pA = ends.first.toSerializableOffset(),
            pB = ends.second.toSerializableOffset()
        )
    }

fun MongeState.serializeHyperbolaBranch2_2D(conicIds: Set<String>): List<SerializedHyperbolaBranch2D> =
    conicIds.mapNotNull { conicId ->
        val ends = hyperbolaArcBranch2[conicId] ?: return@mapNotNull null
        SerializedHyperbolaBranch2D(
            conicId = conicId,
            pA = ends.first.toSerializableOffset(),
            pB = ends.second.toSerializableOffset()
        )
    }
