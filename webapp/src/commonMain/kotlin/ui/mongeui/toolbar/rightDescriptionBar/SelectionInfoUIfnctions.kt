package ui.mongeui.toolbar.rightDescriptionBar

import ui.components.MiniInputField2
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import model.LocalMongeColors
import model.darker
import state.MongeState
import ui.mongeui.toolbar.SkikoButton

@Stable
class UiScale(private val s: Float) {
    fun dp(v: Float): Dp = (v * s).dp
    fun sp(v: Float): TextUnit = (v * s).sp
}
@Composable
fun LabeledRow(
    label: String,
    ui: UiScale = UiScale(1f),
    labelWidth: Dp = ui.dp(88f),

    modifier: Modifier = Modifier,

    contentAlign: Alignment.Horizontal = Alignment.End,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMongeColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ui.dp(4f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.text.copy(alpha = 0.85f),
            fontSize = ui.sp(13f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(labelWidth)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = ui.dp(8f)),
            horizontalAlignment = contentAlign
        ) {
            content()
        }
    }
}

@Composable
fun SimpleNameEditor(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    canApply: Boolean,
    onApply: () -> Unit,
    state: MongeState,
    ui: UiScale,
    enabled: Boolean = true,
    inputWidth: Dp = ui.dp(90f),
    onGreekSymbol: ((String) -> Unit)? = null
) {
    var showGreek by remember { mutableStateOf(false) }
    val applyAndReturnFocus: () -> Unit = {
        onApply()
        state.focusRequester.requestFocus()
    }

    LabeledRow(
        label = label,
        ui = ui,
        contentAlign = Alignment.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (
                    e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) {
                    applyAndReturnFocus()
                    true
                } else false
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ui.dp(5f))
            ) {
                MiniInputField2(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = "Název",
                    numericOnly = false,
                    fontSize = ui.sp(16f),
                    width = inputWidth,
                    height = ui.dp(34f),
                    enabled = enabled,
                    state = state
                )

                if (onGreekSymbol != null) {
                    Box {
                        SkikoButton(
                            width = ui.dp(32f),
                            height = ui.dp(30f),
                            enabled = enabled,
                            onClick = { showGreek = true }
                        ) {
                            Text("β", fontSize = ui.sp(14f))
                        }

                        GreekDropdown(
                            expanded = showGreek,
                            ui = ui,
                            onDismiss = { showGreek = false },
                            onSymbol = onGreekSymbol
                        )
                    }
                }
            }

            Spacer(Modifier.height(ui.dp(7f)))

            ApplySelectionButton(
                ui = ui,
                enabled = enabled && canApply,
                onClick = applyAndReturnFocus
            )
        }
    }
}

@Composable
fun ApplySelectionButton(
    ui: UiScale,
    enabled: Boolean,
    onClick: () -> Unit,
    text: String = "Použít"
) {
    val colors = LocalMongeColors.current

    SkikoButton(
        width = ui.dp(110f),
        height = ui.dp(32f),
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = text,
                tint = colors.text,
                modifier = Modifier.size(ui.dp(17f))
            )

            Spacer(Modifier.width(ui.dp(6f)))

            Text(
                text,
                fontSize = ui.sp(13f)
            )
        }
    }
}

@Composable
fun GreekDropdown(
    expanded: Boolean,
    ui: UiScale,
    onDismiss: () -> Unit,
    onSymbol: (String) -> Unit
) {
    val colors = LocalMongeColors.current

    if (!expanded) return

    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                return IntOffset(
                    x = anchorBounds.left,
                    y = anchorBounds.bottom + 50
                )
            }
        },
        onDismissRequest = onDismiss
    ) {
        Box(
            Modifier
                .background(
                    colors.background.copy(alpha = 0.96f).darker(0.75f),
                    RoundedCornerShape(ui.dp(8f))
                )
                .border(
                    ui.dp(1f),
                    colors.base.copy(alpha = 0.8f),
                    RoundedCornerShape(ui.dp(8f))
                )
                .padding(ui.dp(8f))
        ) {
            val greek = listOf(
                listOf("ρ", "α", "β"),
                listOf("γ", "δ", "ε"),
                listOf("θ", "λ", "π")
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ui.dp(6f))
            ) {
                greek.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                        row.forEach { symbol ->
                            SkikoButton(
                                onClick = {
                                    onSymbol(symbol)
                                    onDismiss()
                                },
                                width = ui.dp(36f),
                                height = ui.dp(32f)
                            ) {
                                Text(symbol, fontSize = ui.sp(15f))
                            }
                        }
                    }
                }
            }
        }
    }
}
