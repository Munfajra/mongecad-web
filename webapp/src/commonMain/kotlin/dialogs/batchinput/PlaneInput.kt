package dialogs.batchinput

import ui.components.MiniInputField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import ui.resources.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import model.LocalMongeColors
import model.classes.PlaneEquation
import model.darker
import model.lighter
import serialization.SettingsManager
import ui.mongeui.toolbar.SkikoButton


class PlaneEquationInputRow {
    val name = mutableStateOf("")
    val a = mutableStateOf("")
    val b = mutableStateOf("")
    val c = mutableStateOf("")
    val d = mutableStateOf("")
    val nameFocus = FocusRequester()
    val aFocus = FocusRequester()
    val bFocus = FocusRequester()
    val cFocus = FocusRequester()
    val dFocus = FocusRequester()
    val shouldRequestFocus = mutableStateOf(false)
}

@Composable
fun PlaneEquationCompactRow(
    row: PlaneEquationInputRow,
    onTabAtEnd: () -> Unit
) {val colors = LocalMongeColors.current
    val focusList = listOf(row.aFocus, row.bFocus, row.cFocus, row.dFocus)
    val ui = SettingsManager.current.UIscale/75f
    fun Modifier.tabNavigation(current: FocusRequester): Modifier = this.onPreviewKeyEvent {
        if (it.key == Key.Tab && it.type == KeyEventType.KeyDown) {
            val index = focusList.indexOf(current)
            if (index in 0 until focusList.lastIndex) {
                focusList[index + 1].requestFocus()
            } else if (index == focusList.lastIndex) {
                onTabAtEnd()
            }
            true
        } else false
    }

    LaunchedEffect(row.shouldRequestFocus.value) {
        if (row.shouldRequestFocus.value) {
            row.nameFocus.requestFocus()
            row.shouldRequestFocus.value = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6*ui.dp)
    ) {
        MiniInputField(
            value = row.a.value,
            ui=ui,
            onValueChange = { row.a.value = it },
            placeholder = "A",
            modifier = Modifier
                .focusRequester(row.aFocus)
                .tabNavigation(row.aFocus)
        )
        Text("· x +", fontSize = 16*ui.sp, color = colors.text)

        MiniInputField(
            value = row.b.value,
            ui=ui,
            onValueChange = { row.b.value = it },
            placeholder = "B",
            modifier = Modifier
                .focusRequester(row.bFocus)
                .tabNavigation(row.bFocus)
        )
        Text("· y +", fontSize = 16*ui.sp, color = colors.text)

        MiniInputField(
            value = row.c.value,
            ui=ui,
            onValueChange = { row.c.value = it },
            placeholder = "C",
            modifier = Modifier
                .focusRequester(row.cFocus)
                .tabNavigation(row.cFocus)
        )
        Text("· z +", fontSize = 16*ui.sp, color = colors.text)

        MiniInputField(
            value = row.d.value,
            ui=ui,
            onValueChange = { row.d.value = it },
            placeholder = "D",
            modifier = Modifier
                .focusRequester(row.dFocus)
                .tabNavigation(row.dFocus)
        )
        Text("= 0", fontSize = 16f*ui.sp, color =colors.text)
    }
}


@Composable
fun PlaneEquationInputDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PlaneEquation, String) -> Unit
) {val colors = LocalMongeColors.current
    val ui = SettingsManager.current.UIscale/75f
    if (!showDialog) return

    var nameText by remember { mutableStateOf("") }
    val row = remember { PlaneEquationInputRow() }
    val backgroundCol = if (colors.isDark) colors.background.copy(alpha = 0.1f).lighter(0.8f) else
            colors.background.copy(alpha = 0.1f).darker(0.2f)
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (windowSize.width - popupContentSize.width) / 2
                val y = (windowSize.height - popupContentSize.height) / 2
                return IntOffset(x, y)
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true), content = {
            Box(
                modifier = Modifier
                    .padding(32*ui.dp)
                    .background(color = colors.background.copy(alpha = 0.9f).darker(0.9f), RoundedCornerShape(8*ui.dp))
                    .border(1.dp, colors.base, RoundedCornerShape(8*ui.dp))
                    .width(480*ui.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16*ui.dp),
                    verticalArrangement = Arrangement.spacedBy(12*ui.dp)
                ) {
                    Row (modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = "Zadat rovnici roviny",
                            fontSize = 18*ui.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vstupní pole pro název
                        Box(
                            modifier = Modifier
                                .width(100*ui.dp)
                                .height(36*ui.dp)
                                .background(backgroundCol, RoundedCornerShape(4*ui.dp))
                                .border(1.dp, colors.base, RoundedCornerShape(4*ui.dp))
                                .padding(horizontal = 8*ui.dp, vertical = 4*ui.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = nameText,
                                onValueChange = { nameText = it },
                                singleLine = true,
                                textStyle = TextStyle(color = colors.text, fontSize = 14*ui.sp),
                                cursorBrush = SolidColor(colors.selected),

                            ) { innerTextField ->
                                if (nameText.isEmpty()) {
                                    Text("název", color = colors.text.copy(alpha = 0.4f), fontSize = 14*ui.sp)
                                }
                                innerTextField()
                            }
                        }

                        Spacer(modifier = Modifier.width(16*ui.dp))

                        // Tabulka s řeckými písmeny 3x3
                        val greek = listOf(
                            listOf("ρ", "α", "β"),
                            listOf("γ", "δ", "ε"),
                            listOf("θ", "λ", "π")
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4*ui.dp)
                        ) {
                            greek.forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4*ui.dp)) {
                                    row.forEach { symbol ->
                                        SkikoButton(
                                            onClick = { nameText += symbol },
                                            width = 32*ui.dp,
                                            height = 32*ui.dp
                                        ) {
                                            Text(symbol, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }


                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PlaneEquationCompactRow(
                            row = row,
                            onTabAtEnd = { /* nic */ }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkikoButton(onClick = onDismiss) {
                            Text("Zrušit")
                        }

                        Spacer(modifier = Modifier.weight(1f)) // → posune další tlačítko doprava

                        SkikoButton(onClick = {
                            val a = row.a.value.toFloatOrNull() ?: 0f
                            val b = row.b.value.toFloatOrNull() ?: 0f
                            val c = row.c.value.toFloatOrNull() ?: 0f
                            val d = row.d.value.toFloatOrNull() ?: 0f
                            val name = nameText

                            onConfirm(PlaneEquation(a, b, c, d * 10f), name)
                        }) {
                            Icon(
                                painter = painterResource("icons/check.svg"),
                                contentDescription = "Potvrdit",
                                modifier = Modifier.size(22*ui.dp),
                            )
                            Text("Vložit", modifier = Modifier.padding(start = 8*ui.dp))
                        }
                    }

                }
            }
        })
}
