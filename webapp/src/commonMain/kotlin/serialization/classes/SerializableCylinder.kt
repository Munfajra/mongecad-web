package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.Offset3D
import model.classes.CylindricalSurface3D
import model.classes.PlaneEquation
import serialization.SerializableColor
import serialization.SerializableOffset3D

@Serializable
data class SerializableCylindricalSurface3D(
    val id: String,
    val name: String,
    val directrixId: String,
    val direction: SerializableOffset3D,
    val topPlaneId: String?,
    val equation: PlaneEquation?,
    val color: SerializableColor,
    val wireWidth: Float,
    val tessT: Int,
    val isVisible: Boolean,

    var lowerConicId: String? = null,
    var upperConicId: String? = null,
    var edgeSegIdsPudorys2D: MutableList<String> = mutableListOf(),
    var edgeSegIdsNarys2D:   MutableList<String> = mutableListOf(),
    var edgeSegIdsBokorys2D: MutableList<String> = mutableListOf(),
    var edgeSegIdsAxo2D: MutableList<String> = mutableListOf(),
    var edgePointIdsPudorys2D: MutableList<String> = mutableListOf(),
    var edgePointIdsNarys2D:   MutableList<String> = mutableListOf(),
    var edgePointIdsBokorys2D: MutableList<String> = mutableListOf(),
    var edgePointIdsAxo2D: MutableList<String> = mutableListOf(),
    val creationIndex: Long,
    val fillFaces: Boolean = true
)

fun CylindricalSurface3D.toSerializable() = SerializableCylindricalSurface3D(
    id = id,
    name = name,
    directrixId = directrixId,
    direction = SerializableOffset3D(direction.x, direction.y, direction.z),
    color = SerializableColor.from(this.color),
    wireWidth = wireWidth,
    tessT = tessT,
    isVisible = isVisible,
    topPlaneId = topPlaneId,
    lowerConicId = lowerConicId,
    upperConicId = upperConicId,
    edgeSegIdsNarys2D = edgeSegIdsNarys2D,
    edgeSegIdsPudorys2D = edgeSegIdsPudorys2D,
    edgeSegIdsBokorys2D = edgeSegIdsBokorys2D,
    edgeSegIdsAxo2D = edgeSegIdsAxo2D,
    edgePointIdsNarys2D = edgePointIdsNarys2D,
    edgePointIdsPudorys2D = edgePointIdsPudorys2D,
    edgePointIdsBokorys2D = edgePointIdsBokorys2D,
    edgePointIdsAxo2D = edgePointIdsAxo2D,
    creationIndex = this.creationIndex,
    equation = equation,
    fillFaces = fillFaces

)

fun SerializableCylindricalSurface3D.toRuntime() = CylindricalSurface3D(
    id = id,
    name = name,
    directrixId = directrixId,
    color = this.color.toColor(),
    wireWidth = wireWidth,
    tessT = tessT,
    isVisible = isVisible,
    direction = Offset3D(direction.x,direction.y,direction.z),
    topPlaneId = topPlaneId,
    lowerConicId = lowerConicId,
    upperConicId = upperConicId,
    edgeSegIdsNarys2D = edgeSegIdsNarys2D,
    edgeSegIdsPudorys2D = edgeSegIdsPudorys2D,
    edgeSegIdsBokorys2D = edgeSegIdsBokorys2D,
    edgeSegIdsAxo2D = edgeSegIdsAxo2D,
    edgePointIdsNarys2D = edgePointIdsNarys2D,
    edgePointIdsPudorys2D = edgePointIdsPudorys2D,
    edgePointIdsBokorys2D = edgePointIdsBokorys2D,
    edgePointIdsAxo2D = edgePointIdsAxo2D,
    equation = equation,
    creationIndex = this.creationIndex,
    fillFaces = fillFaces
)
