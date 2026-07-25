package model.classes
import model.UNASSIGNED_INDEX
import utils.UUID
enum class IntersectionPartKind {
    POINT3D,
    LINE3D,
    SEGMENT3D,
    CONIC3D,
    CURVE3D
}
data class IntersectionPartRef(
    val kind: IntersectionPartKind,
    val id: String
)
data class IntersectionGroup(
    val id: String = UUID.randomUUID().toString(),
    val operandAId: String,
    val operandBId: String,
    val operandALabel: String,
    val operandBLabel: String,
    val parts: List<IntersectionPartRef>,
    val creationIndex: Long = UNASSIGNED_INDEX
) {
    val displayName: String
        get() = "Průnik $operandALabel ∩ $operandBLabel"
}
