package monge.input.axo

import androidx.compose.ui.geometry.Offset
import model.axo.AxoModel
import model.axo.AxoMode
import model.axo.AxoType
import state.MongeState

import utils.cursorToScreen

data class AxoCoords2(val a: Float, val b: Float)
data class AxoRenderBasis(
    val origin: Offset,
    val ex: Offset,
    val ey: Offset,
    val ez: Offset
)
fun Offset.normalizedOrZero(): Offset {
    val len = getDistance()
    return if (len < 1e-6f) Offset.Zero else this / len
}
fun createAxoRenderBasis(
    canvasWidth: Float,
    canvasHeight: Float,
    model: AxoModel
): AxoRenderBasis? {


    val rawEx = model.xPoint - model.origin
    val rawEy = model.yPoint - model.origin
    val rawEz = model.zPoint - model.origin

    val canvasCenter = Offset(canvasWidth / 2f, canvasHeight / 2f)


    if (model.mode == AxoType.OBLIQUE) {
        val base = model.baseUnitScreen ?: 80f
        if (base <= 1e-6f) return null

        return AxoRenderBasis(
            origin = canvasCenter,
            ex = rawEx / base,
            ey = rawEy / base,
            ez = rawEz / base
        )
    }

    val (a, b, c) = recoverInterceptsFromAxoTriangle(model) ?: return null
    if (a <= 1e-6f || b <= 1e-6f || c <= 1e-6f) return null

    val ex = rawEx / a
    val ey = rawEy / b
    val ez = rawEz / c

    return AxoRenderBasis(
        origin = canvasCenter,
        ex = ex,
        ey = ey,
        ez = ez
    )
}
fun solve2x2(v1: Offset, v2: Offset, p: Offset): AxoCoords2? {
    val det = v1.x * v2.y - v1.y * v2.x
    if (kotlin.math.abs(det) < 1e-6f) return null

    val a = (p.x * v2.y - p.y * v2.x) / det
    val b = (v1.x * p.y - v1.y * p.x) / det
    return AxoCoords2(a, b)
}

fun getLogicalCursorAxo(
    snapped: Offset?,
    cursor: Offset,
    canvasOffset: Offset,
    scale: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    flipX: Boolean,
    flipY: Boolean,
    mode: AxoMode,
    axoModel: AxoModel? = null
): Offset? {
    if (snapped != null) return snapped


    return when (mode) {
        AxoMode.NORMAL_2D -> {
            val cursorScreen = cursorToScreen(
                cursor = cursor,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                flipX = flipX,
                flipY = flipY
            )
            (cursorScreen - canvasOffset) / scale
        }

        AxoMode.AXO_PUDORYS,
        AxoMode.AXO_NARYS,
        AxoMode.AXO_BOKORYS -> {
            val model = axoModel ?: return null

            val local = (cursor - canvasOffset) / scale
            val basis = createAxoRenderBasis(
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                model = model
            )?: return  null

            when (mode) {
                AxoMode.AXO_PUDORYS -> unprojectClickToPudorys(local, basis)
                AxoMode.AXO_NARYS -> unprojectClickToNarys(local, basis)
                AxoMode.AXO_BOKORYS -> unprojectClickToBokorys(local, basis)
                else -> null
            }
        }
    }
}
fun unprojectClickToPudorys(
    localPoint: Offset,
    basis: AxoRenderBasis
): Offset? {
    val p = localPoint - basis.origin
    val s = solve2x2(basis.ex, basis.ey, p) ?: return null
    return Offset(s.a, s.b)
}

fun unprojectClickToNarys(
    localPoint: Offset,
    basis: AxoRenderBasis
): Offset? {
    val p = localPoint - basis.origin
    val s = solve2x2(basis.ex, basis.ez, p) ?: return null
    return Offset(s.a, s.b)
}

fun unprojectClickToBokorys(
    localPoint: Offset,
    basis: AxoRenderBasis
): Offset? {
    val p = localPoint - basis.origin
    val s = solve2x2(basis.ey, basis.ez, p) ?: return null
    return Offset(s.a, s.b)
}
fun recoverInterceptsFromAxoTriangle(model: AxoModel): Triple<Float, Float, Float>? {
    val xy = (model.yPoint - model.xPoint).getDistance()
    val yz = (model.zPoint - model.yPoint).getDistance()
    val zx = (model.xPoint - model.zPoint).getDistance()

    val a2 = (xy * xy + zx * zx - yz * yz) / 2f
    val b2 = (xy * xy + yz * yz - zx * zx) / 2f
    val c2 = (yz * yz + zx * zx - xy * xy) / 2f

    if (kotlin.math.abs(a2) <= 1e-6f || kotlin.math.abs(b2) <= 1e-6f || kotlin.math.abs(c2) <= 1e-6f) return null

    val a = kotlin.math.sqrt(kotlin.math.abs(a2))
    val b = kotlin.math.sqrt(kotlin.math.abs(b2))
    val c = kotlin.math.sqrt(kotlin.math.abs(c2))

    return Triple(a, b, c)
}
fun getLogicalCursorAxoOverlay(
    snappedScreen: Offset?,
    cursor: Offset,
    state: MongeState
): Offset? {

    val basis = state.basis
    if (basis == null) {
        println("prazdna baze")
        return null}
    val screen = snappedScreen ?: cursor
    return ((screen - state.canvasOffset) / state.scale) - basis.origin
}
