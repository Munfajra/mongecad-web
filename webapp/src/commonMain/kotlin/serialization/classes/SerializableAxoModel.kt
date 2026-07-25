package serialization.classes

import kotlinx.serialization.Serializable
import model.axo.AxoModel
import model.axo.AxoOrientation
import model.axo.AxoType
import serialization.SerializableOffset
import serialization.toSerializable

@Serializable
data class SerializableAxoModel(
    val mode: AxoType,

    val origin: SerializableOffset,
    val xPoint: SerializableOffset,
    val yPoint: SerializableOffset,
    val zPoint: SerializableOffset,

    val keepXYHorizontal: Boolean,
    val lockOriginToOrthocenter: Boolean,

    val orientation: AxoOrientation,

    // Výchozí hodnota kvůli kompatibilitě se staršími uloženými soubory.
    val referencePlane: Boolean = false
)
fun AxoModel.toSerializable(): SerializableAxoModel {
    return SerializableAxoModel(
        mode = mode,

        origin = origin.toSerializable(),
        xPoint = xPoint.toSerializable(),
        yPoint = yPoint.toSerializable(),
        zPoint = zPoint.toSerializable(),

        keepXYHorizontal = keepXYHorizontal,
        lockOriginToOrthocenter = lockOriginToOrthocenter,

        orientation = orientation,
        referencePlane = referencePlane
    )
}
fun SerializableAxoModel.toRuntime(): AxoModel {
    return AxoModel(
        mode = mode,

        origin = origin.toOffset(),
        xPoint = xPoint.toOffset(),
        yPoint = yPoint.toOffset(),
        zPoint = zPoint.toOffset(),

        keepXYHorizontal = keepXYHorizontal,
        lockOriginToOrthocenter = lockOriginToOrthocenter,

        orientation = orientation,
        referencePlane = referencePlane
    )
}