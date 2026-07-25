package monge.input.axo.lines.linecomplete

enum class ProjectionKind { PUDORYS, NARYS, BOKORYS, AXO }

data class PendingAxoLineCompletion(
    val firstProjectionId: String,
    val firstKind: ProjectionKind,
    val secondKind: ProjectionKind? = null
)
