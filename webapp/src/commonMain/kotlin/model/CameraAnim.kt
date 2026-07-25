package model

data class CameraAnim(
    val startYaw: Float,
    val startPitch: Float,
    val targetYaw: Float,
    val targetPitch: Float,
    val startTimeNanos: Long,
    val durationMillis: Long = 350L // nastav podle chuti
)
fun normalizeYawDeg(y: Float): Float {
    var a = y % 360f; if (a < 0f) a += 360f; return a
}
fun shortestAngleDeltaDeg(from: Float, to: Float): Float {
    val a = normalizeYawDeg(from); val b = normalizeYawDeg(to)
    var d = b - a
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

fun easeInOut(t: Float): Float { // smoothstep
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}