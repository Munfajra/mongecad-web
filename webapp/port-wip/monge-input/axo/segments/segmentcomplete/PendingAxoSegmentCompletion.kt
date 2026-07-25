package monge.input.axo.segments.segmentcomplete

import monge.input.axo.lines.linecomplete.ProjectionKind

data class PendingAxoSegmentCompletion(
    val firstProjectionId: String,
    val firstKind: ProjectionKind,
    val secondKind: ProjectionKind? = null
)
