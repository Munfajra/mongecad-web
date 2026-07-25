package geometry

import model.classes.PlaneEquation

/*
 * Vektor v prostoru a zvedání bodu z průmětu do roviny.
 *
 * Dřív v `monge/input/planeobjects/conicsections/Polygon.kt` u obsluhy
 * n-úhelníků, i když to používá i geometrie kuželoseček.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x+o.x, y+o.y, z+o.z)
    operator fun minus(o: Vec3) = Vec3(x-o.x, y-o.y, z-o.z)
    operator fun times(s: Float) = Vec3(x*s, y*s, z*s)
    fun dot(o: Vec3) = x*o.x + y*o.y + z*o.z
    fun cross(o: Vec3) = Vec3(
        y*o.z - z*o.y,
        z*o.x - x*o.z,
        x*o.y - y*o.x
    )
    fun norm() = kotlin.math.sqrt((x*x + y*y + z*z).toDouble()).toFloat()
    fun normalize(): Vec3 {
        val n = norm().coerceAtLeast(1e-8f)
        return Vec3(x/n, y/n, z/n)
    }
}

fun liftPudorysToPlane(x: Float, y: Float, plane: PlaneEquation): Vec3? {
    if (kotlin.math.abs(plane.c) < 1e-7f) return null
    val z = -(plane.a*x + plane.b*y + plane.d) / plane.c
    return Vec3(x, y, z)
}

fun liftNarysToPlane(x: Float, z: Float, plane: PlaneEquation): Vec3? {
    if (kotlin.math.abs(plane.b) < 1e-7f) return null
    val y = -(plane.a*x + plane.c*z + plane.d) / plane.b
    return Vec3(x, y, z)
}

fun liftBokorysToPlane(y: Float, z: Float, plane: PlaneEquation): Vec3? {
    if (kotlin.math.abs(plane.a) < 1e-7f) return null
    val x = -(plane.b*y + plane.c*z + plane.d) / plane.a
    return Vec3(x, y, z)
}

