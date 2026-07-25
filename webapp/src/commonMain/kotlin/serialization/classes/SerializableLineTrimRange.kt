package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.LineTrimRange

@Serializable
data class SerializableLineTrimRange(
    val start: Float,
    val end: Float
)

fun LineTrimRange.toSerializable(): SerializableLineTrimRange =
    SerializableLineTrimRange(start = start, end = end)

fun SerializableLineTrimRange.toRuntime(): LineTrimRange =
    LineTrimRange(start = start, end = end)
