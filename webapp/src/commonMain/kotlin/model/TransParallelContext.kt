package model

import androidx.compose.ui.geometry.Offset
import model.classes.NamedLine2D
import model.classes.Segment2DProjection

data class TransParallelContext(
    val isPudorys: Boolean,
    val phaseStart: String,
    val phaseTemp: String,
    val phaseFinal: String,
    val rememberedLine: NamedLine2D?,
    val rememberedSegment: Segment2DProjection?,
    val setSelectedLine: (Any) -> Unit,
    val setSelectedSegment: (Segment2DProjection) -> Unit,
    val getDirection: () -> Offset?,
    val setPendingPoint: (Offset) -> Unit,
    val setPendingDir: (Offset) -> Unit,
    val getPendingPoint: () -> Offset?,
    val getPendingDir: () -> Offset?,
    val resetSelected: () -> Unit,
    val resetPhase: () -> Unit,
    val storeAidPoint: (Offset) -> Unit
)
