package dialogs.tools

import dialogs.nameInput.MongeDialog
import dialogs.nameInput.MongeTextField
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.LocalMongeColors
import model.Mongeobjects
import model.ProjectionMode
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.SkikoButton
import ui.mongeui.toolbar.setProjectionPhase

@Composable
fun DistanceInputDialog(
    show: Boolean,
    onDistanceEntered: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val ui = SettingsManager.current.UIscale/75f
    if (!show) return

    val colors = LocalMongeColors.current
    var text by remember { mutableStateOf("") }

    MongeDialog(
        onDismissRequest = onDismiss,

        /* TITULEK */
        title = {
            Text(
                "Zadat vzdálenost",
                fontSize = 18*ui.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        },

        /* TĚLO */
        text = {
            MongeTextField(
                value = text,
                onValueChange = { text = it.filterValidFloatInput() },
                placeholder = "Vzdálenost",
                numericOnly  = true,
                modifier     = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(120*ui.dp),
                onDone = {
                    val v = text.toFloatOrNull()
                    if (v != null && v > 0f) {
                        onDistanceEntered(v * 10f)
                        onDismiss()
                    }
                }
            )
        },

        /* TLAČÍTKA */
        confirmButton = {
            SkikoButton(onClick = {
                val v = text.toFloatOrNull()
                if (v != null && v > 0f) {
                    onDistanceEntered(v * 10f)
                    onDismiss()
                } else {
                    println("⚠️ Neplatná vzdálenost")
                }
            }) {
                Icon(painterResource("icons/check.svg"), null, Modifier.size(24*ui.dp))
                Text("OK", Modifier.padding(horizontal = 8*ui.dp))
            }
        },

        dismissButton = {
            SkikoButton(onClick = onDismiss) { Text("Zrušit") }
        }
    )
}

/* Pomocná funkce pro povolení jen platného float vstupu */
private fun String.filterValidFloatInput(): String =
    replace(',', '.').let { cleaned ->
        if (cleaned.matches(Regex("""-?\d*\.?\d*"""))) cleaned else this
    }

@Composable
fun TypeDistanceDialogHandler(state: MongeState) {
    if (state.drawobjects == Mongeobjects.TYPEDISTANCE && state.showTypeDistanceDialog) {
        DistanceInputDialog(
            show = true,                             // ← přidat
            onDistanceEntered = { distance ->
                state.pendingDistance  = distance
                state.drawobjects      = Mongeobjects.GETDISTANCE
                when (state.projectionMode) {
                    ProjectionMode.AXO -> setProjectionPhase("distance_point3_select_axo", state)
                    else -> setProjectionPhase("distance_point3_select", state)
                }

                println("📐 Zadaná vzdálenost: $distance – pokračuj výběrem bodu")
                state.showTypeDistanceDialog = false
            },
            onDismiss = {
                state.showTypeDistanceDialog = false
                if (state.drawobjects == Mongeobjects.TYPEDISTANCE) {
                    state.drawobjects = Mongeobjects.NONE
                }
            }
        )
    }
}
