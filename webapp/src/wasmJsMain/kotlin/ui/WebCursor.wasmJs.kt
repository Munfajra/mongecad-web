package ui

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

actual class WebCursor(val css: String)

@Suppress("UNUSED_PARAMETER")
actual fun setCanvasCursor(css: String) {
    val canvas = document.querySelector("#webApp canvas") as? HTMLElement ?: return
    canvas.style.cursor = css
}

actual fun createAxoCursor(label: String): WebCursor? =
    if (label.isBlank()) null else WebCursor("crosshair")
