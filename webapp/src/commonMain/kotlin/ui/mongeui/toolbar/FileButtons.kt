package ui.mongeui.toolbar

import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import model.ProjectionMode
import model.YAxisDirectionPlane
import serialization.applySnapshot
import serialization.exportSnapshot
import serialization.initHistory
import serialization.openMongeFile
import serialization.SettingsManager
import serialization.saveMongeFile
import serialization.unsupportedContentMessage
import state.MongeState
import ui.components.MongeRibbonButton
import ui.isAppFullscreen
import ui.toggleAppFullscreen
import ui.theme.LocalMongeDimens

/**
 * Otevření a uložení výkresu.
 *
 * Desktop tohle nabízí přes menu (`AppMongeMenuBar`), které na webu nedává
 * smysl – prohlížeč má vlastní souborový dialog a ukládá stažením, takže
 * jsou akce přímo v liště.
 */
@Composable
fun OpenDrawingButton(state: MongeState, buttonsize: Dp) {
    val dimens = LocalMongeDimens.current
    val scope = rememberCoroutineScope()

    MongeRibbonButton(
        text = "Otevřít výkres",
        selected = false,
        onClick = {
            scope.launch {
                openMongeFile()?.let { loaded -> adoptLoadedDrawing(state, loaded) }
            }
        }
    ) {
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = "Otevřít výkres",
            modifier = Modifier.size(dimens.iconMd)
        )
    }
}

@Composable
fun SaveDrawingButton(state: MongeState, buttonsize: Dp) {
    val dimens = LocalMongeDimens.current

    MongeRibbonButton(
        text = "Uložit výkres",
        selected = false,
        onClick = { saveMongeFile(state) }
    ) {
        Icon(
            Icons.Outlined.Save,
            contentDescription = "Uložit výkres",
            modifier = Modifier.size(dimens.iconMd)
        )
    }
}

/**
 * Přenese načtený výkres do běžícího stavu.
 *
 * Desktop při otevření vytvoří novou záložku s vlastním MongeState, ale web
 * má jedinou instanci držící celé UI – proto se místo výměny instance
 * překlopí obsah přes snapshot a převezme se historie.
 */
fun adoptLoadedDrawing(state: MongeState, loaded: MongeState) {
    applySnapshot(state, loaded.exportSnapshot())
    // Projekční mód ani orientace os nejsou součástí snapshotu (ten nese jen
    // objekty), takže se musí nastavit zvlášť. PLANE web podporuje přímo,
    // zatímco AXO a KOTO zobrazuje přes Mongeovo plátno. Stav musí odpovídat
    // skutečně použitému plátnu, jinak například export skončí v nepodporované
    // AXO/KOTO větvi a vytvoří prázdný obrázek.
    state.projectionMode = when (loaded.projectionMode) {
        ProjectionMode.AXO, ProjectionMode.KOTO -> ProjectionMode.MONGE
        else -> loaded.projectionMode
    }
    state.xAxisDirection = loaded.xAxisDirection
    state.yAxisDirectionPlane =
        if (state.projectionMode == ProjectionMode.MONGE) {
            YAxisDirectionPlane.POSITIVE_DOWN
        } else {
            loaded.yAxisDirectionPlane
        }
    state.displayName = loaded.displayName
    state.history = loaded.history
    if (state.history.snapshots.isEmpty()) state.initHistory()
    state.isDirty = false
    state.triggerRedraw++

    // Kontrola nad načteným stavem, ne nad `state`: projekční mód ani objekty
    // vyřazených typů applySnapshot nepřenáší a AXO/KOTO se výše převádí na
    // MONGE, takže po překlopení už by nebylo z čeho poznat, co v souboru
    // doopravdy bylo.
    state.unsupportedContentMessage = unsupportedContentMessage(loaded)
}

/** Rychlé přepnutí světlý / tmavý motiv (plná volba je v Nastavení). */
@Composable
fun ThemeToggleButton(state: MongeState, buttonsize: Dp) {
    val dimens = LocalMongeDimens.current
    val dark = SettingsManager.current.isDarkMode

    MongeRibbonButton(
        text = if (dark) "Světlý motiv" else "Tmavý motiv",
        selected = dark,
        onClick = {
            SettingsManager.save(
                SettingsManager.current.copy(
                    isDarkMode = !dark,
                    useSystemTheme = false,
                    isPinkMode = false
                )
            )
        }
    ) {
        Icon(
            if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = "Přepnout motiv",
            modifier = Modifier.size(dimens.iconMd)
        )
    }
}

@Composable
fun SettingsButton(state: MongeState, buttonsize: Dp) {
    val dimens = LocalMongeDimens.current

    MongeRibbonButton(
        text = "Nastavení",
        selected = state.showSettingsDialog,
        onClick = { state.showSettingsDialog = true }
    ) {
        Icon(
            Icons.Outlined.Settings,
            contentDescription = "Nastavení",
            modifier = Modifier.size(dimens.iconMd)
        )
    }
}

/**
 * Roztažení aplikace přes celou obrazovku.
 *
 * Web běží v rámu uvnitř stránky, takže se na plochu monitoru dostane jen
 * takhle – desktop tohle tlačítko nemá, tam je appka v celém okně.
 */
@Composable
fun FullscreenButton(state: MongeState, buttonsize: Dp) {
    val dimens = LocalMongeDimens.current
    // Fullscreen se dá ukončit i klávesou Esc mimo Compose, takže se stav
    // nedrží v proměnné, ale čte se z dokumentu při každé rekompozici.
    var fullscreen by remember { mutableStateOf(isAppFullscreen()) }

    MongeRibbonButton(
        text = if (fullscreen) "Zpět do stránky" else "Celá obrazovka",
        selected = fullscreen,
        onClick = {
            toggleAppFullscreen()
            fullscreen = !fullscreen
        }
    ) {
        Icon(
            if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
            contentDescription = "Celá obrazovka",
            modifier = Modifier.size(dimens.iconMd)
        )
    }
}

/** Export do bitmapy (PNG/JPG). PDF a tisk web nemá – viz export/ExportDialog.kt. */
@Composable
fun ExportButton(state: MongeState, buttonsize: Dp) {
    val dimens = LocalMongeDimens.current

    MongeRibbonButton(
        text = "Exportovat obrázek",
        selected = state.showExportDialog,
        onClick = { state.showExportDialog = true }
    ) {
        Icon(
            Icons.Outlined.Image,
            contentDescription = "Exportovat obrázek",
            modifier = Modifier.size(dimens.iconMd)
        )
    }
}
