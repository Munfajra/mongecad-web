package dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import dialogs.nameInput.MongeDialog
import model.LocalMongeColors
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.SkikoButton
import ui.resources.painterResource

@Composable
fun EmptyIntersectionDialog(state: MongeState) {
    if (!state.showEmptyIntersectionDialog) return
    val ui = SettingsManager.current.UIscale / 75f
    val colors = LocalMongeColors.current

    MongeDialog(
        onDismissRequest = { state.showEmptyIntersectionDialog = false },
        width = 440f * ui.dp,
        title = { },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource("icons/alert-triangle.svg"),
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prázdný průnik",
                        fontSize = 18 * ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    Text(
                        text = "Vybrané objekty se neprotínají – jejich průnik je prázdný.",
                        textAlign = TextAlign.Start,
                        color = colors.text
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                SkikoButton(onClick = { state.showEmptyIntersectionDialog = false }) {
                    Text("OK")
                }
            }
        }
    )
}
