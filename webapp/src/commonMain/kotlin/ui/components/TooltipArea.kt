package ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay

/**
 * Náhrada desktopového `androidx.compose.foundation.TooltipArea`, který
 * v Compose pro web není. Signatura je schválně stejná (tooltip, delayMillis,
 * content), takže portovaná volání z desktopu zůstávají beze změny – mění se
 * jen import na `ui.components.TooltipArea`.
 *
 * Tooltip se ukazuje pod kurzorem po prodlevě, stejně jako na desktopu.
 */
/**
 * Umístění tooltipu. Desktopové API zná víc variant, web používá vždycky
 * pozici u kurzoru – parametr tu je jen proto, aby portovaná volání seděla.
 */
object TooltipPlacement {
    data class CursorPoint(val offset: DpOffset = DpOffset.Zero)
}

@Composable
fun TooltipArea(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 500,
    @Suppress("UNUSED_PARAMETER")
    tooltipPlacement: TooltipPlacement.CursorPoint = TooltipPlacement.CursorPoint(),
    content: @Composable () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf(IntOffset.Zero) }

    LaunchedEffect(hovered) {
        if (!hovered) {
            visible = false
        } else {
            delay(delayMillis.toLong())
            visible = true
        }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Enter -> hovered = true
                        PointerEventType.Exit -> hovered = false
                        PointerEventType.Press -> hovered = false
                        PointerEventType.Move -> {
                            event.changes.firstOrNull()?.position?.let {
                                cursor = IntOffset(it.x.toInt(), it.y.toInt())
                            }
                        }
                    }
                }
            }
        }
    ) {
        content()

        if (visible) {
            Popup(popupPositionProvider = cursorAnchoredBelow(cursor)) {
                tooltip()
            }
        }
    }
}

private fun cursorAnchoredBelow(cursor: IntOffset) = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.left + cursor.x)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.top + cursor.y + 20
        // Když se pod kurzorem nevejde, překlop nad něj – ať tooltip nezmizí za okrajem.
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top + cursor.y - popupContentSize.height - 8).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}
