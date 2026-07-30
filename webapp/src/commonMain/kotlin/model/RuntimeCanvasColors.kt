package model

import serialization.toSerializable
import androidx.compose.ui.graphics.Color
import serialization.SettingsManager

val DarkModePlaneColor = Color(0xFF101316)

private var runtimeCanvasColorsEnabled = false

fun <T> withRuntimeCanvasColors(block: () -> T): T {
    val previous = runtimeCanvasColorsEnabled
    runtimeCanvasColorsEnabled = true
    return try {
        block()
    } finally {
        runtimeCanvasColorsEnabled = previous
    }
}

fun Color.runtimeDrawColor(enabled: Boolean = runtimeCanvasColorsEnabled): Color =
    if (enabled && SettingsManager.current.isDarkMode && isPureBlack()) {
        Color.White.copy(alpha = alpha)
    } else {
        this
    }

fun runtimePlaneColor(): Color =
    if (SettingsManager.current.isDarkMode) {
        if (SettingsManager.current.planeColor==Color.White.toSerializable())
        DarkModePlaneColor else SettingsManager.current.planeColor.toColor()
    } else {
        SettingsManager.current.planeColor.toColor()
    }

/**
 * Barva čárové grafiky ve 3D scéně.
 *
 * V tmavém režimu se čistě černá kreslí bíle – proti tmavému pozadí by jinak
 * zapadla. Na rozdíl od 2D plátna se tu nečeká na `withRuntimeCanvasColors`:
 * 3D scéna má vlastní pozadí podle motivu, takže platí vždy.
 */
fun Color.gl3dLineColor(): Color = runtimeDrawColor(enabled = true)

/**
 * Barva plochy tělesa (kužel, válec, koule, rotační i přímková plocha).
 *
 * Čistě černá plocha se kreslí šedě, ne bíle: bílá plocha přes půl scény
 * v tmavém režimu křičí a přebije konstrukci, kvůli které tam je.
 */
fun Color.gl3dSurfaceColor(): Color =
    if (isPureBlack()) Gl3DSurfaceGray.copy(alpha = alpha) else this

private val Gl3DSurfaceGray = Color(0.5f, 0.5f, 0.5f)

private fun Color.isPureBlack(): Boolean =
    red == 0f && green == 0f && blue == 0f
