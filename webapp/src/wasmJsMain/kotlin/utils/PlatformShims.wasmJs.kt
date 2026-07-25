package utils

@JsFun("() => Date.now()")
private external fun jsDateNow(): Double

actual object System {
    actual fun currentTimeMillis(): Long = jsDateNow().toLong()
}
