package gl3d.camera

import model.CameraAnim
import model.CameraSnap
import model.easeInOut
import model.normalizeYawDeg
import model.shortestAngleDeltaDeg
import state.MongeState

/**
 * Přelety kamery na pohled shora / zepředu / zboku – port
 * `beginCameraSnapAnimation` a `updateCameraAnimationIfAny` z desktopového
 * `openglWindow.kt` (na desktopu vytažené do `opengl/embedded/PreviewCamera.kt`).
 *
 * Portuje se jen standardní kamera. AXO větev (`ObliqueAxoView`, nativní šikmé
 * promítání, `obliqueBlendFactor`) na webu není, protože tam není ani AXO režim.
 *
 * `CameraSnap`, `CameraAnim` i `easeInOut` byly ve webovém `model/` už dřív,
 * jen je nikdo nevolal.
 */

/**
 * Vezme čekající snap a posune běžící přelet.
 *
 * Vrací `true`, dokud animace běží – renderer si podle toho říká o další
 * snímek, jinak by se u kreslení na vyžádání přelet zastavil hned na začátku.
 *
 * @param frameTimeNanos čas snímku. Desktop si sahá pro `System.nanoTime()`,
 *   ten v common Kotlinu není; hodnota jde z Compose `withFrameNanos`, což je
 *   navíc přesně ten čas, ke kterému se snímek kreslí.
 */
fun advanceCameraSnap(state: MongeState, camera: Camera3D, frameTimeNanos: Long): Boolean {
    state.pendingCameraSnap?.let { snap ->
        state.pendingCameraSnap = null
        beginCameraSnap(state, camera, snap, frameTimeNanos)
    }

    val anim = state.cameraAnim ?: return false
    val elapsed = (frameTimeNanos - anim.startTimeNanos) / 1_000_000.0
    val t = (elapsed / anim.durationMillis).toFloat()
    val u = easeInOut(t.coerceIn(0f, 1f))

    val deltaYaw = shortestAngleDeltaDeg(anim.startYaw, anim.targetYaw)
    camera.yaw = normalizeYawDeg(anim.startYaw + deltaYaw * u)
    camera.pitch = anim.startPitch + (anim.targetPitch - anim.startPitch) * u

    if (t < 1f) return true

    camera.yaw = normalizeYawDeg(anim.targetYaw)
    camera.pitch = anim.targetPitch
    state.cameraAnim = null
    // Dojezd taky posunul kameru, takže si i on říká o snímek. Desktop tady
    // vrací false, protože kreslí pořád; u kreslení na vyžádání by se poslední
    // krok nikdy nevykreslil a přelet by skončil kousek před cílem.
    return true
}

/** Zruší rozjetý i čekající přelet – volá se při ručním pohybu kamerou. */
fun cancelCameraSnap(state: MongeState) {
    state.cameraAnim = null
    state.pendingCameraSnap = null
}

/** Výchozí poloha kamery, `resetCamera` z `openglWindow.kt`. */
fun resetCamera3D(state: MongeState, camera: Camera3D) {
    camera.reset()
    cancelCameraSnap(state)
}

private fun beginCameraSnap(
    state: MongeState,
    camera: Camera3D,
    snap: CameraSnap,
    frameTimeNanos: Long,
) {
    val target = cameraTargetForSnap(snap) ?: return
    state.cameraAnim = CameraAnim(
        startYaw = normalizeYawDeg(camera.yaw),
        startPitch = camera.pitch,
        targetYaw = normalizeYawDeg(target.first),
        targetPitch = target.second,
        startTimeNanos = frameTimeNanos,
        durationMillis = SNAP_DURATION_MILLIS,
    )
}

/**
 * Cílové (yaw, pitch) snapu. Půdorys nemíří přesně na 90°, ale na 89,9° –
 * v pólu je azimut nedefinovaný a kamera by se při dojetí překlopila.
 */
private fun cameraTargetForSnap(snap: CameraSnap): Pair<Float, Float>? = when (snap) {
    CameraSnap.NARYS_FRONT -> 90f to 0f
    CameraSnap.PUDORYS_TOP -> 90f to 89.9f
    CameraSnap.BOKORYS_SIDE -> 0f to 0f
    // Bez AXO režimu nemá kam mířit.
    CameraSnap.AXO_PLANE -> null
}

private const val SNAP_DURATION_MILLIS = 350L
