package canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import kotlin.math.ln
import kotlin.math.pow
import model.LocalMongeColors
import serialization.SettingsManager
import state.MongeState
import ui.MAX_CANVAS_SCALE
import ui.MIN_CANVAS_SCALE
import ui.mongeui.toolbar.SkikoButton
import ui.zoomCanvasAroundCenter

/** O kolik zvětší nebo zmenší jedno klepnutí na „+" a „−". */
private const val ZOOM_STEP = 1.25f

/**
 * Ovladač měřítka pod nákresnou.
 *
 * Zoom jde i gesty a kolečkem, na tabletu bez klávesnice je ale štípání
 * dvěma prsty jediná cesta – a při rozkreslené konstrukci se špatně trefuje.
 * Táhlo je proto logaritmické: krajní hodnoty měřítka se liší padesátkrát,
 * lineární stupnice by celý použitelný rozsah nacpala do prvních procent.
 */
@Composable
fun CanvasZoomControl(state: MongeState, modifier: Modifier = Modifier) {
    val colors = LocalMongeColors.current
    val ui = SettingsManager.current.UIscale / 75f
    val span = MAX_CANVAS_SCALE / MIN_CANVAS_SCALE
    val position = (ln(state.scale / MIN_CANVAS_SCALE) / ln(span)).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8f * ui.dp))
            .background(colors.background.copy(alpha = if (colors.isDark) 0.82f else 0.88f))
            .border(
                1.dp,
                colors.base.copy(alpha = if (colors.isDark) 0.28f else 0.16f),
                RoundedCornerShape(8f * ui.dp)
            )
            .padding(horizontal = 6f * ui.dp, vertical = 4f * ui.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4f * ui.dp)
    ) {
        SkikoButton(
            onClick = { zoomCanvasAroundCenter(state, state.scale / ZOOM_STEP) },
            enabled = state.scale > MIN_CANVAS_SCALE,
            width = 30f * ui.dp,
            height = 30f * ui.dp
        ) {
            Icon(
                Icons.Outlined.Remove,
                contentDescription = "Oddálit",
                modifier = Modifier.size(18f * ui.dp)
            )
        }

        Slider(
            value = position,
            onValueChange = { zoomCanvasAroundCenter(state, MIN_CANVAS_SCALE * span.pow(it)) },
            modifier = Modifier.width(120f * ui.dp),
            colors = SliderDefaults.colors(
                thumbColor = colors.selected,
                activeTrackColor = colors.selected,
                inactiveTrackColor = colors.base.copy(alpha = 0.35f)
            )
        )

        SkikoButton(
            onClick = { zoomCanvasAroundCenter(state, state.scale * ZOOM_STEP) },
            enabled = state.scale < MAX_CANVAS_SCALE,
            width = 30f * ui.dp,
            height = 30f * ui.dp
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Přiblížit",
                modifier = Modifier.size(18f * ui.dp)
            )
        }

        Text(
            text = state.scale.zoomLabel(),
            color = colors.text,
            fontSize = 12f * ui.sp,
            modifier = Modifier.padding(horizontal = 4f * ui.dp)
        )
    }
}

/** Měřítko jako násobek, tedy „2,5×". Desetinná čárka kvůli českému webu. */
private fun Float.zoomLabel(): String {
    val tenths = (this * 10f + 0.5f).toInt()
    return "${tenths / 10},${tenths % 10}×"
}
