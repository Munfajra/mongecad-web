package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Zapne nativní varování prohlížeče před zavřením nebo opuštěním stránky.
 *
 * Listener se připojuje pouze po dobu, kdy existují neuložené změny. Vlastní
 * text dialogu ani další tlačítka webová stránka z bezpečnostních důvodů
 * určit nemůže; o podobě dialogu rozhoduje prohlížeč.
 */
@Composable
fun BrowserUnsavedChangesGuard(hasUnsavedChanges: Boolean) {
    DisposableEffect(hasUnsavedChanges) {
        setBeforeUnloadGuardEnabled(hasUnsavedChanges)
        onDispose {
            setBeforeUnloadGuardEnabled(false)
        }
    }
}

internal expect fun setBeforeUnloadGuardEnabled(enabled: Boolean)
