package gl3d.camera

import gl3d.math.Mat4
import gl3d.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Stav orbitální kamery – přesná obdoba desktopového `opengl.CameraState`.
 *
 * Projekce je **ortografická** (deskriptivní geometrie žádnou perspektivu
 * nechce), takže `distance` neurčuje vzdálenost oka, ale šířku výřezu.
 */
class Camera3D {

    var target: Vec3 = DEFAULT_TARGET
    var distance: Float = DEFAULT_DISTANCE
    var yaw: Float = DEFAULT_YAW
    var pitch: Float = DEFAULT_PITCH

    /** Vrátí kameru do výchozí polohy (`resetCamera` z `openglWindow.kt`). */
    fun reset() {
        target = DEFAULT_TARGET
        distance = DEFAULT_DISTANCE
        yaw = DEFAULT_YAW
        pitch = DEFAULT_PITCH
    }

    /** Tažení levým tlačítkem. `dx`/`dy` jsou v obrazovkových pixelech. */
    fun orbit(dx: Float, dy: Float, sign: Float = 1f) {
        yaw += dx * ORBIT_SENSITIVITY * sign
        pitch = (pitch + dy * ORBIT_SENSITIVITY * sign).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    /**
     * Tažení pravým tlačítkem. Posun se počítá tak, aby scéna pod kurzorem
     * držela krok – proto se přepočítává přes velikost výřezu.
     */
    fun pan(dx: Float, dy: Float, viewportWidth: Int, viewportHeight: Int) {
        val vw = viewportWidth.coerceAtLeast(1)
        val vh = viewportHeight.coerceAtLeast(1)
        val aspect = vw.toFloat() / vh.toFloat()

        val axes = screenAxes()
        val worldPerPxX = (2f * distance) / vw.toFloat()
        val worldPerPxY = (2f * distance / aspect) / vh.toFloat()

        target = target + axes.right * (-dx * worldPerPxX) + axes.up * (dy * worldPerPxY)
    }

    /**
     * Zoom po krocích kolečka; kladné `steps` přibližují.
     *
     * Desktop používá lineární `distance *= (1 - y·0,1)`, kde `y` je z GLFW
     * vždy ±1. Prohlížeč ale posílá i několikanásobně větší hodnoty (a u
     * touchpadu naopak zlomky), takže by jedno cvaknutí kolečkem skočilo přes
     * půl scény. Krok je proto omezený na jedno cvaknutí na událost a zoom je
     * násobný – přiblížení a oddálení o stejný počet kroků se vrátí přesně
     * tam, kde začalo.
     */
    fun zoom(steps: Float) {
        val limited = steps.coerceIn(-1f, 1f)
        zoomByFactor(ZOOM_STEP.pow(limited))
    }

    /** Zoom daným poměrem – pro gesto pinch, kde poměr dává roztažení prstů. */
    fun zoomByFactor(factor: Float) {
        if (factor <= 0f || !factor.isFinite()) return
        distance = (distance / factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    /** Směr, kterým se kamera dívá (od oka k cíli je opačný). */
    fun eyeDirection(): Vec3 {
        val yawRad = yaw * DEG_TO_RAD
        val pitchRad = pitch * DEG_TO_RAD
        return Vec3(
            (cos(pitchRad) * cos(yawRad)).toFloat(),
            (cos(pitchRad) * sin(yawRad)).toFloat(),
            sin(pitchRad).toFloat(),
        )
    }

    fun eye(): Vec3 = target + eyeDirection() * distance

    /**
     * Osy obrazovky ve světových souřadnicích.
     *
     * Vodorovná osa musí vyjít jako `eyeDirection × z`, protože pohledová
     * matice staví svoji `side` jako `z × forward`, kde `forward` míří od oka
     * k cíli – tedy opačně než [eyeDirection]. Se špatným znaménkem táhne
     * posun myší vodorovně na opačnou stranu, než se scéna hýbe.
     */
    private fun screenAxes(): ScreenAxes {
        val toEye = eyeDirection().normalized()
        var right = toEye cross Vec3.UNIT_Z
        if (right.length() < 1e-6f) right = Vec3(1f, 0f, 0f)
        right = right.normalized()
        return ScreenAxes(right = right, up = (right cross toEye).normalized())
    }

    private class ScreenAxes(val right: Vec3, val up: Vec3)

    companion object {
        val DEFAULT_TARGET = Vec3(6.67428f, 5.600385f, 0f)
        const val DEFAULT_DISTANCE = 550f
        const val DEFAULT_YAW = 130f
        const val DEFAULT_PITCH = 18f

        const val ORBIT_SENSITIVITY = 0.2f

        /** Poměr přiblížení na jedno cvaknutí kolečkem. */
        const val ZOOM_STEP = 1.12f
        const val MAX_PITCH = 89f
        const val MIN_DISTANCE = 50f
        const val MAX_DISTANCE = 2000f

        private const val DEG_TO_RAD = (PI / 180.0).toFloat()
    }
}

/** Matice jednoho snímku. `invViewProj` potřebují ray-castované kvadriky. */
class CameraMatrices(
    val view: Mat4,
    val projection: Mat4,
    val viewProjection: Mat4,
    val invViewProjection: Mat4?,
    val eye: Vec3,
    /** Směr pohledu ve světových souřadnicích – pro klasifikaci přední/zadní strany. */
    val viewDirection: Vec3,
)

/**
 * Sestavení projekce a pohledu.
 *
 * Port `renderScene3D` z `RenderScene.kt` (řádky 664–795), jen bez fixní
 * pipeline: desktop tam matice poskládá přes `glOrtho`/`glMultMatrixf` a hned
 * si je přečte zpátky, my je stavíme rovnou.
 *
 * `flipX`/`flipY` odpovídají volbě směru os na 2D plátně; v AXO se neuplatňují
 * (tam orientaci určuje axo báze).
 */
fun buildCameraMatrices(
    camera: Camera3D,
    width: Int,
    height: Int,
    flipX: Boolean = false,
    flipY: Boolean = false,
): CameraMatrices {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val aspect = w.toFloat() / h.toFloat()
    val cd = camera.distance

    val projection = Mat4.ortho(
        left = -cd, right = cd,
        bottom = -cd / aspect, top = cd / aspect,
        near = Z_NEAR, far = Z_FAR,
    )

    val eye = camera.eye()
    val forward = (camera.target - eye).normalized()
    var side = Vec3.UNIT_Z cross forward
    if (side.length() < 1e-6f) side = Vec3(1f, 0f, 0f)
    side = side.normalized()
    val up = forward cross side

    // Pořadí je stejné jako `glMultMatrixf(basis)` → `glTranslated(-eye)`
    // → `glScalef(mx, my, 1)` na desktopu; fixní pipeline násobí zprava.
    val view = Mat4.fromBasisRows(side, up, -forward) *
            Mat4.translation(-eye.x, -eye.y, -eye.z) *
            Mat4.scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f, 1f)

    val viewProjection = projection * view

    return CameraMatrices(
        view = view,
        projection = projection,
        viewProjection = viewProjection,
        invViewProjection = viewProjection.inverted(),
        eye = eye,
        viewDirection = forward,
    )
}

/**
 * Stejné hodnoty jako na desktopu. Rozsah je záměrně symetrický a velký –
 * scéna se posouvá kamerou, ne ořezem, a přímky se kreslí do délky 3000.
 */
private const val Z_NEAR = -5000f
private const val Z_FAR = 5000f
