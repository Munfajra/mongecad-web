package gl3d.math

import model.Offset3D
import model.Point3D
import model.Vector3f
import kotlin.math.sqrt

/**
 * Vektor pro renderer.
 *
 * Aplikace má už tři 3D typy – `Point3D` (pojmenovaný bod scény), `Offset3D`
 * (serializovatelný směr) a `Vector3f` (kamera). Renderer si drží vlastní typ
 * s operátory, aby port matematiky z desktopu (kde se pracuje s JOML a
 * `Vector3f.plus()/times()` jako metodami) šel psát čitelně, a s převody
 * z/na všechny tři existující typy.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    infix fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-8f) ZERO else Vec3(x / len, y / len, z / len)
    }

    fun toVector3f(): Vector3f = Vector3f(x, y, z)
    fun toOffset3D(): Offset3D = Offset3D(x, y, z)

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UNIT_Z = Vec3(0f, 0f, 1f)
    }
}

fun Vector3f.toVec3(): Vec3 = Vec3(x, y, z)
fun Offset3D.toVec3(): Vec3 = Vec3(x, y, z)
fun Point3D.toVec3(): Vec3 = Vec3(x, y, z)
