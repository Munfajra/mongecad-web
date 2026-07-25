package ui

import kotlinx.browser.window

actual fun openWebsitePage(path: String) {
    window.open(path, "_blank")
}
