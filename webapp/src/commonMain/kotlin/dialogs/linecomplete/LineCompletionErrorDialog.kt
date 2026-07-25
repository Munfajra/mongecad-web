package dialogs.linecomplete

import dialogs.nameInput.MongeDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.LocalMongeColors
import serialization.SettingsManager
import monge.input.combineprojections.dismissLineCompletionError
import state.MongeState
import ui.mongeui.toolbar.SkikoButton

@Composable
fun LineCompletionErrorDialog(state: MongeState) {
    if (!state.showLineCompletionErrorDialog) return
    val ui = SettingsManager.current.UIscale/75f

    val colors = LocalMongeColors.current

    MongeDialog(
        onDismissRequest = { dismissLineCompletionError(state) },
        width = 420f*ui.dp,
        title = {
            Text(
                text = "Nelze doplnit přímku",
                fontSize = 18*ui.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        },
        text = {
            Text(
                text = state.lineCompletionErrorMessage.ifBlank {
                    "Takové zadané průměty neurčují (jednoznačně) prostorovou přímku"
                },
                textAlign = TextAlign.Center,
                color = colors.text,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                SkikoButton(onClick = { dismissLineCompletionError(state) }) {
                    Text("OK")
                }
            }
        }
    )
}
