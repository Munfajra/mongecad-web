package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import serialization.SettingsManager
import state.MongeState
import ui.theme.LocalMongeDimens

/**
 * Přepínač režimu posunu plátna – stejný v MONGE i PLANE panelu nástrojů.
 *
 * Není to konstrukční nástroj, takže nesahá na `state.drawobjects`: rozdělaná
 * konstrukce zůstane, kam se uživatel dopracoval, a po vypnutí se pokračuje
 * dál. Proto taky nesedí v seznamu `actions` vedle bodů a přímek.
 */
@Composable
fun PanModeToolButton(state: MongeState) {
    val ui = SettingsManager.current.UIscale / 75f
    val dimens = LocalMongeDimens.current

    TooltipArea(
        tooltip = {
            Box(Modifier.background(Color.DarkGray).padding(6f * ui.dp)) {
                Text("Posun plátna – táhne myš, hrot i jeden prst", color = Color.White)
            }
        },
        delayMillis = 500,
    ) {
        MongeVerticalToolButton(
            selected = state.panMode,
            onClick = { state.panMode = !state.panMode }
        ) {
            Icon(
                Icons.Outlined.PanTool,
                contentDescription = "Posun plátna",
                modifier = Modifier.size(dimens.iconMd)
            )
        }
    }
}
