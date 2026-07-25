package ui.components

import androidx.compose.material.LocalTextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ui.resources.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalFocusManager
import model.LocalMongeColors
import model.Offset3D
import model.Point3D
import model.ProjectionMode
import model.darker
import model.lighter
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.SkikoButton
import utils.allocIndex

internal val mathTypingRegex = Regex(
    """[0-9+\-*/^().,√a-zA-Z\s]*"""
)


/*
 * Malá vstupní pole. Dřív v `dialogs/batchinput/LineInput.kt`, i když je
 * používá i export, pravý panel a dialogy přejmenování.
 */
@Composable
fun MiniInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    ui: Float,
    fontSize: TextUnit = 14f*ui.sp,
    numericOnly: Boolean = true,

    // ✅ místo onTabAtEnd radši explicitně:
    onTabNext: (() -> Unit)? = null,
    onTabPrev: (() -> Unit)? = null,

    width: Dp = 34f*ui.dp,
    height: Dp = 34f*ui.dp,
    enabled: Boolean = true
) {
    val focusManager = LocalFocusManager.current

    val colors = LocalMongeColors.current
    val focusColor = colors.selected
    val unfocusedColor = colors.base

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        !enabled -> colors.base.copy(alpha = 0.3f)
        isFocused -> focusColor
        else -> unfocusedColor
    }
    val backgroundCol = if (enabled) {
        if (colors.isDark) colors.background.copy(alpha = 0.1f).lighter(0.8f) else
            colors.background.copy(alpha = 0.1f).darker(0.2f)
    }
    else
        colors.background.copy(alpha = 0.05f)

    BasicTextField(
        value = value,
        onValueChange = {
            if (!enabled) return@BasicTextField
            val cleaned = it.replace(',', '.')
            if (!numericOnly || cleaned.isEmpty() || cleaned.matches(Regex("""-?\d*\.?\d*"""))) {
                onValueChange(cleaned)
            }
        },
        enabled = enabled,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(colors.selected),
        textStyle = TextStyle(
            fontFamily = LocalTextStyle.current.fontFamily,  // dolní indexy – viz ui/theme/AppFont.kt
            fontSize = fontSize,
            color = if (enabled) colors.text else colors.text.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        singleLine = true,
        modifier = modifier
            .width(width)
            .height(height)
            .border(1.dp, borderColor, RoundedCornerShape(4f * ui.dp))
            .background(backgroundCol, RoundedCornerShape(4f * ui.dp))
            .padding(horizontal = 2f * ui.dp)
            .onPreviewKeyEvent { event ->
                if (!enabled) return@onPreviewKeyEvent false

                if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                    if (event.isShiftPressed) onTabPrev?.invoke()
                    else onTabNext?.invoke()
                    true
                } else if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    focusManager.clearFocus(force = true)
                    true
                } else {
                    false
                }
            },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = fontSize,
                        color = colors.text.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                inner()
            }
        }
    )
}

@Composable
fun MiniInputField2(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    numericOnly: Boolean = true,
    onTabPrev: (() -> Unit)? = null,
    width: Dp = 34.dp,
    height: Dp = 34.dp,
    enabled: Boolean = true,
    selectAllOnFocus: Boolean = true,
    state: MongeState
) {
    val focusManager = LocalFocusManager.current
    val colors = LocalMongeColors.current
    val focusColor     = colors.selected
    val unfocusedColor = colors.base
    var isFocused by remember { mutableStateOf(false) }


    val borderColor = when {
        !enabled  -> colors.base.copy(alpha = 0.3f)
        isFocused -> focusColor
        else      -> unfocusedColor
    }
    val backgroundCol = if (enabled) {
        if (colors.isDark) colors.background.copy(alpha = 0.1f).lighter(0.8f) else
            colors.background.copy(alpha = 0.1f).darker(0.2f)
    }
    else
        colors.background.copy(alpha = 0.05f)

    BasicTextField(
        value = value,
        onValueChange = { new ->
            if (!enabled) return@BasicTextField
            val cleanedText = new.text.replace(',', '.')
            if (!numericOnly || cleanedText.isEmpty() || cleanedText.matches(Regex("""-?\d*\.?\d*"""))) {
                // zachovej selection/cursor, jen uprav text
                onValueChange(new.copy(text = cleanedText))
            }
        },
        enabled = enabled,
        cursorBrush = SolidColor(colors.selected),
        textStyle = TextStyle(
            fontFamily = LocalTextStyle.current.fontFamily,  // dolní indexy – viz ui/theme/AppFont.kt
            fontSize = fontSize,
            color = if (enabled) colors.text else colors.text.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        singleLine = true,
        modifier = modifier
            .width(width)
            .height(height)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .background(backgroundCol, RoundedCornerShape(4.dp))
            .padding(horizontal = 2.dp)
            .onFocusChanged { f ->
                isFocused = f.isFocused
                state.isTextEditing = f.isFocused
                if (enabled && selectAllOnFocus && f.isFocused) {
                    onValueChange(
                        value.copy(selection = TextRange(0, value.text.length))
                    )
                }
            }
            .onPreviewKeyEvent { event ->

                if (!enabled) return@onPreviewKeyEvent false
                if (!isFocused) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    focusManager.clearFocus(force = true)
                    return@onPreviewKeyEvent true
                }

                false
            },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (value.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = fontSize,
                        color = colors.text.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                inner()
            }
        }
    )
}

@Composable
fun MiniInputField3(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    numericOnly: Boolean = false,
    width: Dp = 34.dp,
    height: Dp = 34.dp,
    enabled: Boolean = true,
    mathInput: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val colors = LocalMongeColors.current
    val focusColor     = colors.selected
    val unfocusedColor = colors.base
    var isFocused by remember { mutableStateOf(false) }


    val borderColor = when {
        !enabled  -> colors.base.copy(alpha = 0.3f)
        isFocused -> focusColor
        else      -> unfocusedColor
    }
    val backgroundCol = if (enabled) {
        if (colors.isDark) colors.background.copy(alpha = 0.1f).lighter(0.8f) else
        colors.background.copy(alpha = 0.1f).darker(0.2f)
    }
    else
        colors.background.copy(alpha = 0.05f)
    BasicTextField(
        value = value,
        onValueChange = { new ->
            if (!enabled) return@BasicTextField

            val cleanedText = new.text
                .replace(',', '.')
                .replace(" ", "")

            val allowed = when {
                mathInput -> cleanedText.matches(mathTypingRegex)
                numericOnly -> cleanedText.isEmpty() ||
                        cleanedText.matches(Regex("""-?\d*\.?\d*"""))
                else -> true
            }

            // Reject disallowed edits and keep the current value untouched – this
            // way typing past a validation rule is a no-op instead of a glitch.
            if (!allowed) return@BasicTextField

            when {
                // Nothing was normalized away – propagate the field's own cursor and
                // selection verbatim, so it behaves like a native text field
                // (selecting all and typing replaces, drag-select works, etc.).
                cleanedText == new.text -> onValueChange(new)

                // Only characters were swapped 1:1 (e.g. ',' -> '.') – keep selection.
                cleanedText.length == new.text.length ->
                    onValueChange(new.copy(text = cleanedText))

                // Characters were removed (e.g. spaces) – collapse the caret safely.
                else -> {
                    val caret = new.selection.end.coerceIn(0, cleanedText.length)
                    onValueChange(new.copy(text = cleanedText, selection = TextRange(caret, caret)))
                }
            }
        },
        enabled = enabled,
        cursorBrush = SolidColor(colors.selected),
        textStyle = TextStyle(
            fontFamily = LocalTextStyle.current.fontFamily,  // dolní indexy – viz ui/theme/AppFont.kt
            fontSize = fontSize,
            color = if (enabled) colors.text else colors.text.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        singleLine = true,
        modifier = modifier
            .width(width)
            .height(height)
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused

                if (nowFocused && !isFocused && enabled) {
                    onValueChange(
                        value.copy(selection = TextRange(0, value.text.length))
                    )
                }

                isFocused = nowFocused
            }
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .background(backgroundCol, RoundedCornerShape(4.dp))
            .padding(horizontal = 2.dp)
            .onPreviewKeyEvent { event ->

                if (!enabled) return@onPreviewKeyEvent false
                if (!isFocused) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    focusManager.clearFocus(force = true)
                    return@onPreviewKeyEvent true
                }

                false
            },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (value.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = fontSize,
                        color = colors.text.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                inner()
            }
        }
    )
}

