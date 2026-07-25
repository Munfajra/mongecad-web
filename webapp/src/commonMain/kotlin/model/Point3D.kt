package model

import androidx.compose.ui.graphics.Color
import utils.UUID

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float,
    var name: String,
    var color: Color = Color.Black,
    var width: Float = 1f,
    val id: String = UUID.randomUUID().toString(),
    val superscript: String? = "",
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true

)

operator fun Point3D.minus(offset: Offset3D): Point3D {

    return Point3D(x - offset.x, y - offset.y, z - offset.z, name)
}