package model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color


data class SolidOfRevolutionNarys(
    val id: String,
    val axisLine3DId: String,
    val meridianIdsNarys: List<String>,

    val mirroredMeridianIdsNarys: List<String> = emptyList(),

    val rMax: Float,
    val neckRadii: List<Float>,
    val circleIdsPudorys: List<String>,
    val circleIdsNarys: List<String>,
    val sampledMeridianPolylineXZ: List<Offset> = emptyList(),
    val creationIndex: Long = UNASSIGNED_INDEX,
    val color: Color,
    var strokeWidth: Float= 1f,
    val lineStyle: LineStyle,
    val name: String = "p",
    val show: Boolean = true,
    var fillFaces: Boolean = true,
    var glMesh: GLMesh? = null

)
data class SolidOfRevolutionPudorys(
    val id: String,
    val axisLine3DId: String,
    val meridianIdsPudorys: List<String>,

    val mirroredMeridianIdsPudorys: List<String> = emptyList(),

    val rMax: Float,
    val neckRadii: List<Float>,
    val circleIdsPudorys: List<String>,
    val circleIdsNarys: List<String>,
    val sampledMeridianPolylineXY: List<Offset> = emptyList(),
    val creationIndex: Long = UNASSIGNED_INDEX,
    val color: Color,
    var strokeWidth: Float= 1f,
    val lineStyle: LineStyle,
    val name: String = "p",
    val show: Boolean = true,
    var fillFaces: Boolean = true,
    var glMesh: GLMesh? = null

)
data class GLMesh(
    val vao: Int,
    val vbo: Int,
    val nbo: Int,
    val ebo: Int,
    val indexCount: Int
)