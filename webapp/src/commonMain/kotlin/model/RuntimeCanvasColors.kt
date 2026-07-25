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

private fun Color.isPureBlack(): Boolean =
    red == 0f && green == 0f && blue == 0f
