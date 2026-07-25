package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.classes.ConicalSurface3D
import serialization.SerializableColor

@Serializable
data class SerializableConicalSurface3D(
    val id: String,
    val name: String,
    val apexId: String,
    val directrixId: String,
    val color: SerializableColor,
    val wireWidth: Float,
    val tessT: Int,
    val isVisible: Boolean,
    val edgeSegIdsPudorys2D: List<String> = emptyList(),
    val edgeSegIdsNarys2D: List<String> = emptyList(),
    val edgeSegIdsBokorys2D: List<String> = emptyList(),
    val edgeSegIdsAxo2D: List<String> = emptyList(),
    val edgePointIdsPudorys2D: List<String> = emptyList(),
    val edgePointIdsNarys2D: List<String> = emptyList(),
    val edgePointIdsBokorys2D: List<String> = emptyList(),
    val edgePointIdsAxo2D: List<String> = emptyList(),
    val apexProjPudorysId: String? = null,
    val apexProjNarysId: String? = null,
    val apexProjBokorysId: String? = null,
    val apexProjAxoId: String? = null,
    val creationIndex: Long,
    val fillFaces: Boolean = true
)
fun ConicalSurface3D.toSerializable() = SerializableConicalSurface3D(
    id = id,
    name = name,
    apexId = apexId,
    directrixId = directrixId,
    color       = SerializableColor.from(this.color),
    wireWidth = wireWidth,
    tessT = tessT,
    isVisible = isVisible,
    edgeSegIdsPudorys2D = edgeSegIdsPudorys2D.toList(),
    edgeSegIdsNarys2D = edgeSegIdsNarys2D.toList(),
    edgeSegIdsBokorys2D = edgeSegIdsBokorys2D.toList(),
    edgeSegIdsAxo2D = edgeSegIdsAxo2D.toList(),
    edgePointIdsPudorys2D = edgePointIdsPudorys2D.toList(),
    edgePointIdsNarys2D = edgePointIdsNarys2D.toList(),
    edgePointIdsBokorys2D = edgePointIdsBokorys2D.toList(),
    edgePointIdsAxo2D = edgePointIdsAxo2D.toList(),
    apexProjPudorysId = apexProjPudorysId,
    apexProjBokorysId = apexProjBokorysId,
    apexProjAxoId = apexProjAxoId,
    creationIndex = this.creationIndex,
    apexProjNarysId = apexProjNarysId,
    fillFaces = fillFaces
)

fun SerializableConicalSurface3D.toRuntime() = ConicalSurface3D(
    id = id,
    name = name,
    apexId = apexId,
    directrixId = directrixId,
    color = this.color.toColor(),
    wireWidth = wireWidth,
    tessT = tessT,
    isVisible = isVisible,
    edgeSegIdsPudorys2D = edgeSegIdsPudorys2D.toMutableList(),
    edgeSegIdsNarys2D = edgeSegIdsNarys2D.toMutableList(),
    edgeSegIdsBokorys2D = edgeSegIdsBokorys2D.toMutableList(),
    edgeSegIdsAxo2D = edgeSegIdsAxo2D.toMutableList(),
    edgePointIdsPudorys2D = edgePointIdsPudorys2D.toMutableList(),
    edgePointIdsNarys2D = edgePointIdsNarys2D.toMutableList(),
    edgePointIdsBokorys2D = edgePointIdsBokorys2D.toMutableList(),
    edgePointIdsAxo2D = edgePointIdsAxo2D.toMutableList(),
    apexProjPudorysId = apexProjPudorysId,
    apexProjNarysId = apexProjNarysId,
    apexProjBokorysId = apexProjBokorysId,
    apexProjAxoId = apexProjAxoId,
    creationIndex = this.creationIndex,
    fillFaces = fillFaces
)
