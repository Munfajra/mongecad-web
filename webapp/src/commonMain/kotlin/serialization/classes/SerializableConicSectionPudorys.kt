package serialization.classes

import serialization.toSerializable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import model.classes.ConicInputHyperbolaPudorys
import model.classes.ConicSectionPudorys
import serialization.*

@Serializable
data class SerializableConicInputHyperbolaPudorys(
    val vertex: SerializableOffset,
    val axis: SerializableOffset,
    val line1: SerializableNamedLinePudorys,
    val line2: SerializableNamedLinePudorys
)
fun ConicInputHyperbolaPudorys.toSerializable(): SerializableConicInputHyperbolaPudorys {
    return SerializableConicInputHyperbolaPudorys(
        vertex = SerializableOffset.from(this.vertex),
        axis = SerializableOffset.from(this.axis),
        line1 = this.line1.toSerializable(),
        line2 = this.line2.toSerializable()
    )
}
fun SerializableConicInputHyperbolaPudorys.toHyperbolaInput(): ConicInputHyperbolaPudorys {
    return ConicInputHyperbolaPudorys(
        vertex = this.vertex.toOffset(),
        axis = this.axis.toOffset(),
        line1 = this.line1.toNamedLine(),
        line2 = this.line2.toNamedLine()
    )
}

@Serializable
data class SerializedConicSectionPudorys(
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

    // Pro kružnici / elipsu / parabolu
    val inputPoint1: SerializableOffset? = null,
    val inputPoint2: SerializableOffset? = null,
    val inputPoint3: SerializableOffset? = null,

    // Pro hyperbolu – specifická konstrukce
    val hyperbolaInput: SerializableConicInputHyperbolaPudorys? = null,
    val isDegenerate: Boolean = false,
    val degenerateDir: SerializableOffset? = null,
    val isLine: Boolean = false,
    val showInAxo: Boolean = true,
    val creationIndex: Long

)

fun ConicSectionPudorys.toSerializable(
    inputPoints: Triple<Offset, Offset, Offset>? = null,
    hyperbolaInput: ConicInputHyperbolaPudorys? = null
): SerializedConicSectionPudorys {
    return SerializedConicSectionPudorys(
        a = a,
        b = b,
        c = c,
        d = d,
        e = e,
        f = f,
        name = name,
        localColor = SerializableColor.from(localColor?: Color.Black),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle.toSerializable(),
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


fun SerializedConicSectionPudorys.toRuntime(): ConicSectionPudorys {
    return ConicSectionPudorys(
        a = a,
        b = b,
        c = c,
        d = d,
        e = e,
        f = f,
        rawName = name,
        localColor = localColor.toColor(),
        strokeWidth = strokeWidth,
        lineStyle = lineStyle.toLineStyle(),
        parent = null,            // parent si propojíš zvlášť při rekonstrukci
        parentId = this.parentId,
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

fun SerializedConicSectionPudorys.toHyperbolaInput(): ConicInputHyperbolaPudorys? {
    return hyperbolaInput?.toHyperbolaInput()
}
