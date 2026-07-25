package dialogs.tools

import dialogs.nameInput.MongeDialog
import dialogs.nameInput.MongeTextField
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
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
fun TypeAngleDialogHandler(state: MongeState) {
    AngleInputDialog(
        show = state.drawobjects == Mongeobjects.TYPEANGLE && state.showTypeAngleDialog,

        onAngleEntered = { radians ->
            state.pendingAngle    = radians
            state.drawobjects     = Mongeobjects.GETANGLE
            when (state.projectionMode){
                ProjectionMode.AXO ->  setProjectionPhase("angle_new_vertex_axo", state)
                else -> setProjectionPhase("angle_new_vertex", state)
            }

            println("📐 Zadaný úhel (rad): $radians")
            state.showTypeAngleDialog = false   // ← zavře se
        },

        onDismiss = {
            state.showTypeAngleDialog = false
            if (state.drawobjects == Mongeobjects.TYPEANGLE) {
                state.drawobjects = Mongeobjects.NONE
            }
        }
    )
}


/*────────────── 1. univerzální dialog ──────────────*/
@Composable
fun AngleInputDialog(
    show: Boolean,
    onAngleEntered: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    var input     by remember { mutableStateOf("") }
    var isDegrees by remember { mutableStateOf(true) }

    fun appendPi() {
        input = if (input.isBlank()) "π" else "${input}π"
    }
    fun confirm() {
        val parsed = parseAngleExpression(input)
        if (parsed != null && parsed > 0f) {
            val rad = if (isDegrees) (parsed.toDouble() * kotlin.math.PI / 180.0).toFloat() else parsed
            onAngleEntered(rad)
            onDismiss()
        } else println("⚠️ Neplatný úhel")
    }

    /* –––––––––  DIALOG ––––––––– */
    MongeDialog(
        onDismissRequest = onDismiss,
        width = 320f*ui.dp,
        title = {
            Text("Zadat úhel", fontSize = 18f*ui.sp, fontWeight = FontWeight.Bold, color = colors.text)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14f*ui.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                /* přepínač Stupně / Radiány */
                Row(horizontalArrangement = Arrangement.spacedBy(32f*ui.dp)) {
                    listOf(true to "Stupně", false to "Radiány").forEach { (deg, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isDegrees == deg,
                                onClick  = { isDegrees = deg },
                                colors   = RadioButtonDefaults.colors(
                                    selectedColor   = colors.selected,
                                    unselectedColor = colors.base
                                )
                            )
                            Text(label, color = colors.text,fontSize = 16f*ui.sp)
                        }
                    }
                }

                /* ───────────── pole + π tlačítko v jednom řádku ───────────── */
                Row(horizontalArrangement = Arrangement.spacedBy(8f*ui.dp),
                    verticalAlignment = Alignment.CenterVertically) {

                    MongeTextField(
                        value         = input,
                        onValueChange = { input = it },
                        placeholder   = if (isDegrees) "Úhel ve °" else "Úhel v rad",
                        numericOnly   = false,
                        modifier      = Modifier.width(200f*ui.dp),
                        onDone        = { confirm() }
                    )

                    SkikoButton(
                        onClick = { appendPi() },
                        width   = 40f*ui.dp,
                        height  = 32f*ui.dp,
                        enabled = !isDegrees          // → aktivní jen v radiánech
                    ) { Text("π") }
                }
            }
        },
        confirmButton = {
            SkikoButton(onClick = { confirm() }) {
                Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
            }
        },
        dismissButton = {
            SkikoButton(onClick = onDismiss) { Text("Zrušit") }
        }
    )
}


fun parseAngleExpression(expr: String): Float? {
    return try {
        val normalized = expr
            .trim()
            .replace(',', '.')
            .replace("π", "pi", ignoreCase = true)
            .replace("pí", "pi", ignoreCase = true)
            .replace(Regex("\\s+"), "")

        if (normalized.isBlank()) return null

        val parser = AngleExpressionParser(normalized)
        val result = parser.parse()

        if (!result.isFinite()) null else result.toFloat()
    } catch (_: Exception) {
        null
    }
}

private class AngleExpressionParser(
    private val input: String
) {
    private var pos = 0

    fun parse(): Double {
        val value = parseExpression()

        if (pos != input.length) {
            error("Unexpected character at $pos")
        }

        return value
    }

    /**
     * expression = term ((+ | -) term)*
     */
    private fun parseExpression(): Double {
        var value = parseTerm()

        while (pos < input.length) {
            value = when (peek()) {
                '+' -> {
                    pos++
                    value + parseTerm()
                }

                '-' -> {
                    pos++
                    value - parseTerm()
                }

                else -> return value
            }
        }

        return value
    }

    /**
     * term = factor ((* | / | implicit multiplication) factor)*
     */
    private fun parseTerm(): Double {
        var value = parseFactor()

        while (pos < input.length) {
            value = when (peek()) {
                '*' -> {
                    pos++
                    value * parseFactor()
                }

                '/' -> {
                    pos++
                    val divisor = parseFactor()
                    if (divisor == 0.0) error("Division by zero")
                    value / divisor
                }

                else -> {
                    if (startsImplicitMultiplication()) {
                        value * parseFactor()
                    } else {
                        return value
                    }
                }
            }
        }

        return value
    }

    /**
     * factor = unary | number | pi | parenthesis
     */
    private fun parseFactor(): Double {
        if (pos >= input.length) {
            error("Unexpected end")
        }

        return when {
            peek() == '+' -> {
                pos++
                parseFactor()
            }

            peek() == '-' -> {
                pos++
                -parseFactor()
            }

            peek() == '(' -> {
                pos++
                val value = parseExpression()

                if (pos >= input.length || peek() != ')') {
                    error("Missing closing parenthesis")
                }

                pos++
                value
            }

            input.startsWith("pi", pos, ignoreCase = true) -> {
                pos += 2
                kotlin.math.PI
            }

            peek().isDigit() || peek() == '.' -> {
                parseNumber()
            }

            else -> error("Unexpected character '${peek()}'")
        }
    }

    private fun parseNumber(): Double {
        val start = pos
        var dotCount = 0

        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
            if (input[pos] == '.') dotCount++
            if (dotCount > 1) error("Invalid number")
            pos++
        }

        val text = input.substring(start, pos)

        if (text == ".") {
            error("Invalid number")
        }

        return text.toDouble()
    }

    private fun startsImplicitMultiplication(): Boolean {
        if (pos >= input.length) return false

        return when {
            peek() == '(' -> true
            input.startsWith("pi", pos, ignoreCase = true) -> true
            peek().isDigit() -> true
            peek() == '.' -> true
            else -> false
        }
    }

    private fun peek(): Char = input[pos]
}
