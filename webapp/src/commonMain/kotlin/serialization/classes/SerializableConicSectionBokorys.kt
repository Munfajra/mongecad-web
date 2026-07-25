package serialization.classes

import serialization.toSerializable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import model.classes.ConicInputHyperbolaBokorys
import model.classes.ConicSectionBokorys
import serialization.*

@Serializable
data class SerializedConicSectionBokorys(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float,
    val name: String,
    val localColor: SerializableColor,
    val strokeWidth: Float,
    val lineStyle: SerializableLineStyle,
    val parent: String? = null,
    val parentId: String? = null,
    val id: String,
    val isHelpCircle: Boolean,
    val creationIndex: Long,


    // Vstupní body (obecná konstrukce)
    val inputPoint1: SerializableOffset? = null,
    val inputPoint2: SerializableOffset? = null,
    val inputPoint3: SerializableOffset? = null,

    // Specifická konstrukce hyperboly
    val hyperbolaInput: SerializableConicInputHyperbolaBokorys? = null,
    val isDegenerate: Boolean = false,
    val degenerateDir: SerializableOffset? = null,
    val isLine: Boolean = false,
    val showInAxo: Boolean = true
)

fun ConicSectionBokorys.toSerializable(
    inputPoints: Triple<Offset, Offset, Offset>? = null,
    hyperbolaInput: ConicInputHyperbolaBokorys? = null
): SerializedConicSectionBokorys {
    return SerializedConicSectionBokorys(
        a, b, c, d, e, f,
        name = name,
        localColor = SerializableColor.from(localColor?: Color.Black),
        strokeWidth = strokeWidth,
        lineStyle = this.lineStyle.toSerializable(),
        parent = parent?.id,
        parentId = parentId,
        id = id,
        inputPoint1 = inputPoints?.first?.takeIf { it != Offset.Unspecified }?.let { SerializableOffset.from(it) },
        inputPoint2 = inputPoints?.second?.takeIf { it != Offset.Unspecified }?.let { SerializableOffset.from(it) },
        inputPoint3 = inputPoints?.third?.takeIf { it != Offset.Unspecified }?.let { SerializableOffset.from(it) },
        hyperbolaInput = hyperbolaInput?.toSerializable(),
        isHelpCircle = this.isHelpCircle,
        // 🆕 degenerace
        isDegenerate = this.isDegenerate, // pole, co si přidáš do ConicSectionPudorys
        degenerateDir = this.degenerateDir?.let { SerializableOffset.from(it) },
        showInAxo = this.showInAxo,
        creationIndex = this.creationIndex,
        isLine = this.isLineDegenerate
    )
}

fun SerializedConicSectionBokorys.toRuntime(): ConicSectionBokorys{
    return ConicSectionBokorys(
        a, b, c, d, e, f,
        rawName = name,
        localColor = localColor.toColor(),
        strokeWidth = strokeWidth,
        lineStyle = this.lineStyle.toLineStyle(),
        parent = null,
        parentId = parentId,
        id = id,
        isHelpCircle = this.isHelpCircle,
        // 🆕 degenerace
        isDegenerate = this.isDegenerate,
        degenerateDir = this.degenerateDir?.toOffset(),
        isLineDegenerate = this.isLine,
        showInAxoInitial = this.showInAxo,
        creationIndex = this.creationIndex
    )
}
fun SerializedConicSectionBokorys.toHyperbolaInput(): ConicInputHyperbolaBokorys? {
    return hyperbolaInput?.toHyperbolaInput()
}

@Serializable
data class SerializableConicInputHyperbolaBokorys(
    val vertex: SerializableOffset,
    val axis: SerializableOffset,
    val line1: SerializableNamedLineBokorys,
    val line2: SerializableNamedLineBokorys
)

fun ConicInputHyperbolaBokorys.toSerializable(): SerializableConicInputHyperbolaBokorys {
    return SerializableConicInputHyperbolaBokorys(
        vertex = SerializableOffset.from(this.vertex),
        axis = SerializableOffset.from(this.axis),
        line1 = this.line1.toSerializable(),
        line2 = this.line2.toSerializable()
    )
}

fun SerializableConicInputHyperbolaBokorys.toHyperbolaInput(): ConicInputHyperbolaBokorys{
    return ConicInputHyperbolaBokorys(
        vertex = this.vertex.toOffset(),
        axis = this.axis.toOffset(),
        line1 = this.line1.toNamedLine(),
        line2 = this.line2.toNamedLine()
    )
}
