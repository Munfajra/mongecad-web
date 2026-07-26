package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import model.LocalMongeColors
import model.MongeTheme
import serialization.SettingsManager
import ui.tabs.AppMongeWithTabs
import ui.resources.preloadCoreResources

/**
 * Webový protějšek `App()` z desktopového Main.kt.
 *
 * Desktop tu otevírá `Window(...)` s AWT ikonou, drag&dropem .monge souborů
 * a kontrolou aktualizací – to všechno na webu odpadá. Zůstává ale stejné
 * pořadí: načíst nastavení → MongeTheme(dark/pink) → AppMongeUI.
 */
@Composable
fun MongeCadWebApp() {
    var booted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SettingsManager.load()
        preloadCoreResources()
        booted = true

        // Načítací vrstva stránky zhasne až nad hotovým UI. První snímek po
        // `booted` ještě jen rekomponuje, kreslí se ten následující – proto
        // se čeká na dva.
        withFrameNanos { }
        withFrameNanos { }
        notifyWebAppReady()
    }

    MongeTheme(
        dark = SettingsManager.current.isDarkMode,
        pink = SettingsManager.current.isPinkMode
    ) {
        val colors = LocalMongeColors.current
        Box(
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            if (!booted) {
                Text("Načítám MongeCAD…", color = colors.text)
            } else {
                AppMongeWithTabs(onOpenWebsite = ::openWebsitePage)
            }
        }
    }
}
