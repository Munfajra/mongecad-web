package dialogs.specialLineCase

import dialogs.nameInput.MongeDialog
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
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.DrawingModeMonge
import model.LocalMongeColors
import model.SpecialLineCase
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.SkikoButton
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu

//ALERT DIALOG pro speciální přímku (kolmo na x12)
@Composable
fun SpecialLineCasePudorysDialog(state: MongeState) {
    val ui = SettingsManager.current.UIscale/75f
    val open = state.showSpecialLineDialog.value &&
            (state.projectionPhase == "special_line_type_selection" ||
                    state.projectionPhase == "special_line_type_selection_start_narys")

    if (!open) return

    val colors = LocalMongeColors.current

    /* pomocná lambda pro potvrzení */
    fun confirm() {
        when (state.projectionPhase) {
            "special_line_type_selection" -> {
                state.specialLineCase.value = SpecialLineCase.ParallelToPudorys
                state.mongeMode             = DrawingModeMonge.NARYS
                setProjectionPhase("special_case_point_in_narys", state)
            }
            "special_line_type_selection_start_narys" -> {
                state.specialLineCase.value = SpecialLineCase.ParallelToNarys
                state.mongeMode             = DrawingModeMonge.PUDORYS
                setProjectionPhase("special_case_point_in_pudorys", state)
            }
        }
        state.showSpecialLineDialog.value = false
    }
    /* Monge dialog */
    MongeDialog(
        onDismissRequest = { state.showSpecialLineDialog.value = false },
        width = 500f * ui.dp,

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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Speciální poloha přímky",
                        fontSize = 18 * ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    Text(
                        text = "Přímka je kolmá na x₁₂. Vyberte případ.",
                        textAlign = TextAlign.Start,
                        color = colors.text
                    )
                }
            }
        },

        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                SkikoButton(
                    onClick = { confirm() },
                    width = 210f * ui.dp,
                    height = 48f * ui.dp
                ) {
                    Text(
                        text = "Přímka kolmá\nna průmětnu",
                        textAlign = TextAlign.Center,
                        fontSize = 13 * ui.sp
                    )
                }
                SkikoButton(
                    onClick = {
                        resetStavu(state)
                        state.specialLineCase.value = null
                        state.showSpecialLineDialog.value = false

                        // otevři parametrický vstup
                        state.showParamDialog.value = true
                    },
                    width = 210f * ui.dp
                ) {
                    Text("Jiná (par. zadání)")
                }
            }
        },

        dismissButton = {
            SkikoButton(onClick = {
                state.showSpecialLineDialog.value = false
                resetStavu(state)
            }) {
                Text("Zrušit")
            }
        }
    )
}
