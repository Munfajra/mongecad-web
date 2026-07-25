package serialization

import kotlinx.browser.localStorage
import kotlinx.browser.window

private const val STORAGE_KEY = "mongecad.settings"

actual object SettingsStore {
    actual fun read(): String? = runCatching { localStorage.getItem(STORAGE_KEY) }.getOrNull()

    actual fun write(text: String) {
        runCatching { localStorage.setItem(STORAGE_KEY, text) }
    }

    actual fun prefersDarkMode(): Boolean =
        runCatching { window.matchMedia("(prefers-color-scheme: dark)").matches }.getOrDefault(false)
}
