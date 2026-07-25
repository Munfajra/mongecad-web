package model


import kotlinx.serialization.Serializable
import kotlin.math.sqrt
@Serializable
data class Offset3D(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Offset3D) = Offset3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Offset3D) = Offset3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Offset3D(x * scalar, y * scalar, z * scalar)

    infix fun dot(other: Offset3D): Float =
        x * other.x + y * other.y + z * other.z

    infix fun cross(other: Offset3D): Offset3D =
        Offset3D(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    fun length(): Float = sqrt(x * x + y * y + z * z)


    fun normalized(): Offset3D {
        val len = length()
        return if (len == 0f) this else Offset3D(x / len, y / len, z / len)
    }

}


fun Offset3D.normalize(): Offset3D {
    val length = kotlin.math.sqrt(x * x + y * y + z * z)
    if (length == 0f) return Offset3D(0f, 0f, 0f)
    return Offset3D(x / length, y / length, z / length)
}
operator fun Point3D.plus(offset: Offset3D) =
    Point3D(this.x + offset.x, this.y + offset.y, this.z + offset.z, this.name)
