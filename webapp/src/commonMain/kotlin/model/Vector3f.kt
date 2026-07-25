package model

data class Vector3f(val x: Float, val y: Float, val z: Float) {

    fun plus(other: Vector3f) =
        Vector3f(x + other.x, y + other.y, z + other.z)

    fun minus(other: Vector3f) =
        Vector3f(x - other.x, y - other.y, z - other.z)

    fun times(f: Float) =
        Vector3f(x * f, y * f, z * f)

    fun dot(other: Vector3f): Float =
        x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3f): Vector3f =
        Vector3f(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )

    fun length(): Float =
        kotlin.math.sqrt(x * x + y * y + z * z)

    fun normalize(): Vector3f {
        val len = length()
        if (len < 1e-8f) return Vector3f(0f, 0f, 0f)
        return Vector3f(x / len, y / len, z / len)
    }
}