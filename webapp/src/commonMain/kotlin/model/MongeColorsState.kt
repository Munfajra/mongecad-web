package model

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import serialization.SettingsManager
import ui.theme.LocalMongeDimens
import androidx.compose.material.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import ui.theme.rememberAppFontFamily
import ui.theme.rememberMongeDimens

class MongeColorsState(
    baseColor: Color,
    textColor: Color,
    backgroundColor: Color,
    hoverColor: Color,
    selectedColor: Color,
    disabledColor: Color,
    tabColor: Color,
    buttonColor: Color,
) {
    var base by mutableStateOf(baseColor)
        private set
    var text by mutableStateOf(textColor)
        private set
    var background by mutableStateOf(backgroundColor)
        private set
    var hover by mutableStateOf(hoverColor)
        private set
    var selected by mutableStateOf(selectedColor)
        private set
    var disabled by mutableStateOf(disabledColor)
        private set
    var tab by mutableStateOf(tabColor)
        private set
    var buttonColor by mutableStateOf(buttonColor)
    private set

    var isDark by mutableStateOf(false)
        private set

    fun apply(p: MongePalette, dark: Boolean) {
        base = p.base
        text = p.text
        background = p.background
        hover = p.hover
        selected = p.selected
        disabled = p.disabled
        tab = p.tab
        isDark = dark
        buttonColor = p.buttonColor
    }
}
data class MongePalette(
    val base: Color,
    val text: Color,
    val background: Color,
    val hover: Color,
    val selected: Color,
    val disabled: Color,
    val tab: Color,
    val buttonColor: Color,
)
val MongePaletteLight = MongePalette(
    base = Color(0xFF6B6E72),
    text = Color(0xFF1F2933),
    background = Color(0xFFFFFFFF),
    hover = Color(0xFF868686),
    selected = Color(0xFF4D7CDB),
    disabled = Color(0xFF9CA3AF),
    tab = Color(0xFF6B84D5).lighter(0.5f),
    buttonColor = Color(0xFF242424),
)

val MongePaletteDark = MongePalette(
    base = Color(0xFF323236),
    text = Color(0xA8FFFFFF),
    background = Color(0xFF191A1C),
    hover = Color(0xFF7B7B7B),
    selected = Color(0xFF4D7CDB),
    disabled = Color(0xFF94A3B8),
    tab = Color(0xFF5488F3),
    buttonColor = Color(0xFFBABABA),
)
val MongePalettePink = MongePalette(
    base = Color(0xFF8B8B8B),
    text = Color(0xFF000000),
    background = Color(0xFFFFB6FF),
    hover = Color(0xFFFF5454),
    selected = Color(0xFFCC009B),
    disabled = Color(0xFF797979),
    tab = Color(0xFFEB9BFF),
    buttonColor = Color(0xFF6F0076),
)


fun Color.darker(factor: Float = 0.8f): Color =
    Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha
    )

val LocalMongeColors = staticCompositionLocalOf<MongeColorsState> {
    error("MongeColorsState not provided")
}
fun Color.lighter(by: Float = 0.2f): Color {
    val k = by.coerceIn(0f, 1f)
    fun up(c: Float) = (c + (1f - c) * k).coerceIn(0f, 1f)
    return Color(
        red   = up(red),
        green = up(green),
        blue  = up(blue),
        alpha = alpha
    )
}

@Composable
fun MongeTheme(
    dark: Boolean = false,
    pink: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (dark) MongePaletteDark else if (pink) MongePalettePink else MongePaletteLight
    val ui = SettingsManager.current.UIscale / 75f
    val dimens = rememberMongeDimens(ui)

    val state = remember {
        MongeColorsState(
            baseColor = palette.base,
            textColor = palette.text,
            backgroundColor = palette.background,
            hoverColor = palette.hover,
            selectedColor = palette.selected,
            disabledColor = palette.disabled,
            tabColor = palette.tab,
            buttonColor = palette.buttonColor,
        )
    }

    LaunchedEffect(palette, dark) {
        state.apply(palette, dark)
    }

    // Výchozí písmo pro veškerý text v UI – viz ui/theme/AppFont.kt.
    // Bez něj by dolní indexy průmětů vycházely v prohlížeči jako čtverečky.
    val appFont = rememberAppFontFamily()
    val baseTextStyle = LocalTextStyle.current.merge(TextStyle(fontFamily = appFont))

    CompositionLocalProvider(
        LocalMongeColors provides state,
        LocalMongeDimens provides dimens,
        LocalTextStyle provides baseTextStyle
    ) {
        content()
    }
}
