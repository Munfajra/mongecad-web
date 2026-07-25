package model.axo

import androidx.compose.ui.geometry.Offset

enum class AxoType {
    ORTHOGONAL,
    OBLIQUE
}


enum class AxoOrientation {
    CW,
    CCW
}
data class AxoModel(
    val mode: AxoType = AxoType.ORTHOGONAL,

    val origin: Offset = Offset.Zero,

    val xPoint: Offset = Offset(-80f, 60f),
    val yPoint: Offset = Offset(80f, 60f),
    val zPoint: Offset = Offset(0f, -90f),

    val keepXYHorizontal: Boolean = true,
    val lockOriginToOrthocenter: Boolean = true,

    val orientation: AxoOrientation = AxoOrientation.CW,
    val baseUnitScreen: Float? = null,

    // U kolmé axonometrie se referenční rovina τ (axo trojúhelník) přidává vždy.
    // U kosoúhlé se nepřidává (zadání ω + q), s výjimkou zadání mezi-osovými úhly,
    // kdy je trojúhelník explicitně určen - pak je tento příznak true.
    val referencePlane: Boolean = false
)
private fun signedTriangleArea2(a: Offset, b: Offset, c: Offset): Float {
    return (b.x - a.x) * (c.y - a.y) -
            (b.y - a.y) * (c.x - a.x)
}

private fun axoAxisTriangleSign(model: AxoModel): Float {
    return signedTriangleArea2(
        model.xPoint,
        model.yPoint,
        model.zPoint
    )
}

fun isAxoAxisOrderMirrored(model: AxoModel): Boolean {
    val current = axoAxisTriangleSign(model)

    // Defaultní model bereme jako referenční správnou orientaci.
    val reference = axoAxisTriangleSign(AxoModel())

    if (kotlin.math.abs(current) < 1e-5f) return false
    if (kotlin.math.abs(reference) < 1e-5f) return false

    return current * reference < 0f
}
fun AxoModel.axisUnitLengths(): Triple<Float, Float, Float> {
    val ux = (xPoint - origin).getDistance()
    val uy = (yPoint - origin).getDistance()
    val uz = (zPoint - origin).getDistance()
    return Triple(ux, uy, uz)
}

fun AxoModel.axisUnitRatios(): Triple<Float, Float, Float>? {
    val base = baseUnitScreen ?: return null
    if (base <= 1e-6f) return null

    val (ux, uy, uz) = axisUnitLengths()
    return Triple(ux / base, uy / base, uz / base)
}
data class AxoDerived(
    val xDir: Offset,
    val yDir: Offset,
    val zDir: Offset,

    val unitX: Float,
    val unitY: Float,
    val unitZ: Float,

    val angleXY: Float,
    val angleYZ: Float,
    val angleZX: Float,

    val sideXY: Float,
    val sideYZ: Float,
    val sideZX: Float,

    val orthocenter: Offset?,
    val orthocenterDeviation: Float,
    val isOrthogonal: Boolean
)
data class AxoSector(
    val name: String,      // "xy", "yz", "zx"
    val dirA: Offset,
    val dirB: Offset,
    val startDeg: Float,
    val sweepDeg: Float
)
enum class AxoDragTarget {
    NONE,
    ORIGIN,
    X_POINT,
    Y_POINT,
    Z_POINT,
    X_AXIS,
    Y_AXIS,
    Z_AXIS
}

data class AxoConstraints(
    val keepXYHorizontal: Boolean = true,
    val lockOriginToOrthocenter: Boolean = true
)
