package dialogs.tools

import dialogs.nameInput.MongeDialog
import dialogs.nameInput.MongeTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.LocalMongeColors
import serialization.SettingsManager
import ui.mongeui.toolbar.SkikoButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegularPolygonSidesDialog(
    show: Boolean,
    initialSides: Int = 5,
    initialAskName: Boolean = true,
    initialStartLetter: Char = 'A',
    onConfirm: (sides: Int, askName: Boolean, startLetter: Char) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val ui = SettingsManager.current.UIscale / 75f
    val colors = LocalMongeColors.current

    var input by remember(show) {
        mutableStateOf(initialSides.coerceIn(3, 30).toString())
    }

    var askName by remember(show) {
        mutableStateOf(initialAskName)
    }

    var startLetter by remember(show) {
        mutableStateOf(initialStartLetter.uppercaseChar().coerceIn('A', 'Z'))
    }

    fun stepLetter(delta: Int) {
        val next = ('A'.code + (startLetter.code - 'A'.code + delta + 26) % 26).toChar()
        startLetter = next
    }

    val parsed = input.trim().toIntOrNull()
    val isValid = parsed != null && parsed in 3..30

    fun clamp(n: Int) = n.coerceIn(3, 30)

    fun setFromInt(n: Int) {
        input = clamp(n).toString()
    }

    fun step(delta: Int) {
        val cur = input.trim().toIntOrNull() ?: initialSides
        setFromInt(cur + delta)
    }

    fun confirm() {
        val sides = parsed ?: return

        if (sides in 3..30) {
            onConfirm(sides, askName, startLetter)
            onDismiss()
        } else {
            println("⚠️ Neplatný počet stran (povoleno 3–30).")
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    MongeDialog(
        onDismissRequest = onDismiss,
        width = 300f * ui.dp,
        title = {
            Text(
                "Pravidelný n-úhelník",
                fontSize = 16f * ui.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10f * ui.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                        when (ev.key) {
                            Key.DirectionUp -> {
                                step(+1)
                                true
                            }

                            Key.DirectionDown -> {
                                step(-1)
                                true
                            }

                            Key.PageUp -> {
                                step(+5)
                                true
                            }

                            Key.PageDown -> {
                                step(-5)
                                true
                            }

                            Key.Enter, Key.NumPadEnter -> {
                                confirm()
                                true
                            }

                            Key.Escape -> {
                                onDismiss()
                                true
                            }

                            else -> false
                        }
                    }
            ) {
                Text("Počet stran:", color = colors.text)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6f * ui.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SkikoButton(
                        onClick = { step(-1) },
                        width = 32f * ui.dp,
                        height = 32f * ui.dp
                    ) {
                        Text("–", color = colors.text)
                    }

                    MongeTextField(
                        value = input,
                        onValueChange = { s ->
                            if (s.all { it.isDigit() } || s.isBlank()) {
                                input = s
                            }
                        },
                        placeholder = "5",
                        numericOnly = true,
                        modifier = Modifier
                            .width(72f * ui.dp)
                            .focusRequester(focusRequester),
                        onDone = { confirm() },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { confirm() }
                        )
                    )

                    SkikoButton(
                        onClick = { step(+1) },
                        width = 32f * ui.dp,
                        height = 32f * ui.dp
                    ) {
                        Text("+", color = colors.text)
                    }

                    Spacer(Modifier.weight(1f))

                    SkikoButton(
                        onClick = { setFromInt(3) },
                        height = 28f * ui.dp
                    ) {
                        Text("min", color = colors.text)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { askName = !askName }
                ) {
                    Checkbox(
                        checked = askName,
                        onCheckedChange = { askName = it }
                    )

                    Text(
                        "Pojmenovat polygon po vytvoření",
                        color = colors.text,
                        fontSize = 13f * ui.sp
                    )
                }

                if (askName) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6f * ui.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Počáteční písmeno:", color = colors.text, fontSize = 13f * ui.sp)

                        Spacer(Modifier.weight(1f))

                        SkikoButton(
                            onClick = { stepLetter(-1) },
                            width = 32f * ui.dp,
                            height = 32f * ui.dp
                        ) {
                            Text("–", color = colors.text)
                        }

                        Text(
                            startLetter.toString(),
                            color = colors.text,
                            fontSize = 15f * ui.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(24f * ui.dp)
                        )

                        SkikoButton(
                            onClick = { stepLetter(+1) },
                            width = 32f * ui.dp,
                            height = 32f * ui.dp
                        ) {
                            Text("+", color = colors.text)
                        }
                    }
                }

                if (!isValid && input.isNotBlank()) {
                    Text(
                        "Zadejte celé číslo (3–30)",
                        color = Color(0xFFD32F2F),
                        fontSize = 12f * ui.sp
                    )
                }
            }
        },
        confirmButton = {
            SkikoButton(
                onClick = { confirm() },
                height = 32f * ui.dp,
                enabled = isValid
            ) {
                Icon(
                    painterResource("icons/check.svg"),
                    null,
                    Modifier.size(18f * ui.dp)
                )

                Text(
                    "OK",
                    Modifier.padding(start = 6f * ui.dp),
                    color = colors.text,
                    fontSize = 12f * ui.sp
                )
            }
        },
        dismissButton = {
            SkikoButton(
                onClick = onDismiss,
                height = 32f * ui.dp
            ) {
                Text("Zrušit", color = colors.text)
            }
        }
    )
}
