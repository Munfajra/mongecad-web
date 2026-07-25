package serialization.classes

import kotlinx.serialization.Serializable
import model.ConicSegment
import model.ConicSegmentation
import model.LineStyle
import state.MongeState

// Serializace po částech stylovaných kuželoseček (conicSegments).
// Ukládá se per-view forma (klíč = id per-view kuželosečky), protože per-view id
// jsou stabilní přes save/load. Vše defaultované → staré .monge soubory se načtou
// beze změny.

@Serializable
data class SerializedConicSegment(
    val start: Float,
    val end: Float,
    val style: LineStyle = LineStyle.Solid
)

@Serializable
data class SerializedConicSegmentation(
    val conicId: String,
    val primary: List<SerializedConicSegment> = emptyList(),
    val secondary: List<SerializedConicSegment>? = null
)

private fun ConicSegment.toSerialized() = SerializedConicSegment(start, end, style)
private fun SerializedConicSegment.toRuntime() = ConicSegment(start, end, style)

// Public helpery pro serializovanou historii (undo/redo do disku).
fun ConicSegmentation.toSerialized(conicId: String): SerializedConicSegmentation =
    SerializedConicSegmentation(
        conicId = conicId,
        primary = primary.map { it.toSerialized() },
        secondary = secondary?.map { it.toSerialized() }
    )

fun SerializedConicSegmentation.toRuntimeSegmentation(): ConicSegmentation =
    ConicSegmentation(
        primary = primary.map { it.toRuntime() },
        secondary = secondary?.map { it.toRuntime() }
    )

fun MongeState.serializeConicSegmentations(): List<SerializedConicSegmentation> =
    conicSegments.mapNotNull { (id, seg) ->
        if (seg.isEmpty()) null
        else SerializedConicSegmentation(
            conicId = id,
            primary = seg.primary.map { it.toSerialized() },
            secondary = seg.secondary?.map { it.toSerialized() }
        )
    }

fun MongeState.deserializeConicSegmentations(list: List<SerializedConicSegmentation>) {
    list.forEach { s ->
        conicSegments[s.conicId] = ConicSegmentation(
            primary = s.primary.map { it.toRuntime() },
            secondary = s.secondary?.map { it.toRuntime() }
        )
    }
}
