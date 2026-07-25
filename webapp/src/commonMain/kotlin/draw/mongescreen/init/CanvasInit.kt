package draw.mongescreen.init

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import state.MongeState

fun initializeCanvasOffsetIfNeeded(state: MongeState, canvasSize: Size) {
    if (!state.isOffsetInitialized) {
        state.canvasOffset = Offset(canvasSize.width / 2, canvasSize.height / 2)
        state.isOffsetInitialized = true
    }
}
fun initializeCanvasOffsetIfNeededAxo(state: MongeState, canvasSize: Size) {
    if (!state.isOffsetInitialized) {
        val initialScale = 5f
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

        state.scale = initialScale
        state.canvasOffset = center * (1f - initialScale)
        state.isOffsetInitialized = true
    }
}