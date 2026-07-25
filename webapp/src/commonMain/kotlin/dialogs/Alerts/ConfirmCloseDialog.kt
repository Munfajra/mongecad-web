package dialogs.Alerts

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
import ui.mongeui.toolbar.SkikoButton
import ui.resources.painterResource

@Composable
fun ConfirmCloseDialog(
    fileName: String,
    onSave: () -> Unit,
    onDontSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val ui = SettingsManager.current.UIscale / 75f
    val colors = LocalMongeColors.current

    MongeDialog(
        onDismissRequest = onCancel,
        width = 480f * ui.dp,
        dismissOnOutsideClick = false,
        title = {},
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource("icons/alert-triangle.svg"),
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(44f * ui.dp),
                )
                Spacer(Modifier.width(16f * ui.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6f * ui.dp)) {
                    Text(
                        text = "Neuložené změny",
                        fontSize = 18f * ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                    )
                    Text(
                        text = "Uložit změny ve výkresu „$fileName“ před zavřením?",
                        textAlign = TextAlign.Start,
                        color = colors.text.copy(alpha = 0.8f),
                        fontSize = 14f * ui.sp,
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8f * ui.dp, Alignment.End),
            ) {
                SkikoButton(onClick = onCancel) {
                    Text("Zrušit")
                }
                SkikoButton(onClick = onDontSave) {
                    Text("Neukládat", color = Color(0xFFFF6B6B))
                }
                SkikoButton(onClick = onSave, isSelected = true) {
                    Text("Uložit")
                }
            }
        },
    )
}
