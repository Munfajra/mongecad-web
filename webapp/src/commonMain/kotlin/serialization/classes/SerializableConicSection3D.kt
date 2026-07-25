package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.LineStyle
import model.Offset3D
import model.classes.ConicSection3D
import model.classes.Matrix3x3
import serialization.SerializableColor
import state.MongeState

@Serializable
data class SerializableConicSection3D(
    val p0: Offset3D,
    val u: Offset3D,
    val v: Offset3D,
    val matrix: SerializableMatrix3x3,
    val rawName: String,
    val color: SerializableColor,
    val strokeWidth: Float,
    val lineStyle: LineStyle,
    val id: String,
    val a: Float? = null,
    val b: Float? = null,
    val directrixOfSurfaceIds: List<String> = emptyList(),
    val creationIndex: Long
)

fun ConicSection3D.toSerializable(): SerializableConicSection3D {
    return SerializableConicSection3D(
        p0          = this.p0,
        u           = this.u,
        v           = this.v,
        matrix      = this.matrix.toSerializable(),  // opraveno
        rawName     = this.rawName,
        color       = SerializableColor.from(this.color),
        strokeWidth = this.strokeWidth,
        lineStyle   = this.lineStyle,
        id          = this.id,
        a           = this.a,
        b           = this.b,
        creationIndex = this.creationIndex,
        directrixOfSurfaceIds = directrixOfSurfaceIds.toList()
    )
}

fun SerializableConicSection3D.toRuntime(): ConicSection3D {
    return ConicSection3D(
        p0          = this.p0,
        u           = this.u,
        v           = this.v,
        matrix      = this.matrix.toMatrix3x3(),  // opraveno
        rawName     = this.rawName,
        color       = this.color.toColor(),
        strokeWidth = this.strokeWidth,
        lineStyle   = this.lineStyle,
        id          = this.id,
        a           = this.a,
        b           = this.b,
        creationIndex = this.creationIndex,
    ).also { model ->
        model.directrixOfSurfaceIds = this.directrixOfSurfaceIds.toSet()}
}



@Serializable
data class SerializableMatrix3x3(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float,
    val m20: Float, val m21: Float, val m22: Float
)
fun Matrix3x3.toSerializable(): SerializableMatrix3x3 {
    return SerializableMatrix3x3(
        m00, m01, m02,
        m10, m11, m12,
        m20, m21, m22
    )
}

fun SerializableMatrix3x3.toMatrix3x3(): Matrix3x3 {
    return Matrix3x3(
        m00, m01, m02,
        m10, m11, m12,
        m20, m21, m22
    )
}
fun relinkConics2DTo3D(state: MongeState) {
    val byId = state.conics3D.associateBy { it.id }

    state.conicsPudorys.forEach { c ->
        val pid = c.parentId ?: c.parent?.id
        c.parent = pid?.let { byId[it] }
        if (c.parentId == null) c.parentId = pid   // ⬅️ jen doplnit, NEmazat
    }
    state.conicsNarys.forEach { c ->
        val pid = c.parentId ?: c.parent?.id
        c.parent = pid?.let { byId[it] }
        if (c.parentId == null) c.parentId = pid   // ⬅️ jen doplnit, NEmazat
    }
    state.conicsBokorys.forEach { c ->
        val pid = c.parentId ?: c.parent?.id
        c.parent = pid?.let { byId[it] }
        if (c.parentId == null) c.parentId = pid
    }
    state.conicsAxo.forEach { c ->
        val pid = c.parentId ?: c.parent?.id
        c.parent = pid?.let { byId[it] }
        if (c.parentId == null) c.parentId = pid
    }
}
