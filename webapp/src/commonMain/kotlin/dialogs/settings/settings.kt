package dialogs.settings

import serialization.toSerializable
import androidx.compose.foundation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import model.LocalMongeColors
import model.MongeColorsState
import serialization.SerializableColor
import serialization.SettingsManager
import serialization.SettingsState
import serialization.defaultUiScaleForCurrentOs
import ui.colorpicker.ColorPickerDropdown
import ui.mongeui.toolbar.SkikoButton
import kotlin.math.roundToInt



private fun settingsSurfaceColor(role: String, colors: MongeColorsState): Color =
    when (role) {
        "dialog" -> if (colors.isDark) Color(0xFF0E1116) else Color(0xFFF6F8FB)
        "panel" -> if (colors.isDark) Color(0xFF11161D) else Color(0xFFFFFFFF)
        "panelAlt" -> if (colors.isDark) Color(0xFF151B23) else Color(0xFFF8FAFC)
        "active" -> if (colors.isDark) Color(0xFF162032) else Color(0xFFEAF2FF)
        else -> colors.background
    }

@Composable
fun SettingsDialog(onClose: () -> Unit) {
    val colors = LocalMongeColors.current

    // aktuální settings
    var working by remember { mutableStateOf(SettingsManager.current) }
    val ui = SettingsManager.current.UIscale/75f
    val scrollState = rememberScrollState()
    val defaults = remember {
        SettingsState(
            ncolor = SerializableColor(1f, 0f, 0f, 1f),
            pcolor = SerializableColor(0f, 0f, 1.0f, 1f),
            rbcolor = SerializableColor(0f, 0.3f, 0.059f, 1f),
            bcolor = SerializableColor(0.9f, 0.9f, 1f, 0.5f),
            msaaSamples = 8,
            snapThreshold = 20f,
            isDarkMode = false,
            isPinkMode = false,
            planeColor = Color.White.toSerializable(),
            labelSizeFixedPx = 40f,
            labelSizeScaledPx = 30f,
            scaleLabelsWithCanvas = true,
            UIscale = defaultUiScaleForCurrentOs(),
            systemCursor = false,
            lineDashDensity = 1f

        )
    }

    Popup(
        popupPositionProvider = CenterPopupPositionProvider,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true)
    ) {
        // Root container
        Box(
            modifier = Modifier
                .widthIn(min = 800*ui.dp, max = 1000*ui.dp)
                .heightIn(min = 600*ui.dp, max = 800*ui.dp)
                .shadow(18*ui.dp, RoundedCornerShape(18*ui.dp))
                .background(settingsSurfaceColor("dialog", colors), RoundedCornerShape(18*ui.dp))
                .border(1*ui.dp, colors.base.copy(alpha = if (colors.isDark) 0.42f else 0.20f), RoundedCornerShape(18*ui.dp))
                .padding(16*ui.dp)

        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12*ui.dp)) {
                Header(colors = colors, onClose = onClose)

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12*ui.dp)
                ) {
                    // Levý sloupec s přepínačem sekcí odpadá – web má jen nastavení
                    // hlavního programu, sekce 3D náhledu sem nepatří.

                    // Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12 * ui.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(end = 12 * ui.dp, bottom = 6 * ui.dp),
                                verticalArrangement = Arrangement.spacedBy(10 * ui.dp)
                            ) {
                            SettingsCard(colors, title = "Program") {
                                LabeledSliderRow(
                                    label = "UI scale",
                                    description = "Měřítko ovládacích prvků aplikace.",
                                    value = working.UIscale,
                                    // Spodní hranice je níž než na desktopu: na úzkých
                                    // displejích si ovládací prvky jinak berou tolik místa,
                                    // že překrývají plátno.
                                    range = 40f..100f,
                                    step = 1f,
                                    valueText = { "${it.roundToInt()} %" },
                                    colors = colors,
                                    uiscale = ui
                                ) { uiV ->
                                    working = working.copy(UIscale = uiV)
                                }
                                // Přepínač motivu je v liště (skupina „Aplikace“).
                                // Systémový kurzor web neřeší – tvar kurzoru nad plátnem
                                // určuje CSS, ne nastavení aplikace.
                            }

                            SettingsCard(colors, title = "Nákresna") {
                                FormRow(
                                    label = "Barva nákresny",
                                    description = "Výchozí barva pracovní plochy.",
                                    colors = colors,
                                    ui = ui
                                ) {
                                    ColorPickerDropdown(
                                        selectedColor = working.planeColor.toColor(),
                                        onColorPreview = { c ->
                                            working = working.copy(planeColor = c.toSerializable())
                                        },
                                        onColorConfirm = { c ->
                                            working = working.copy(planeColor = c.toSerializable())
                                        }
                                    )
                                }

                                LabeledSliderRow(
                                    label = "Práh přichytávání",
                                    description = "Vzdálenost, ve které se kurzor přichytí ke geometrii.",
                                    value = working.snapThreshold,
                                    range = 1f..100f,
                                    step = 1f,
                                    valueText = { "${it.roundToInt()} px" },
                                    colors = colors,
                                    uiscale = ui
                                ) { working = working.copy(snapThreshold = it) }

                                LabeledSliderRow(
                                    label = "Hustota čárkování",
                                    description = "Globální hustota čárek a teček pro styly přímek a úseček.",
                                    value = working.lineDashDensity,
                                    range = 0.5f..3f,
                                    step = 0.25f,
                                    valueText = { "${((it * 100f).roundToInt() / 100f)}×" },
                                    colors = colors,
                                    uiscale = ui
                                ) { working = working.copy(lineDashDensity = it) }
                            }

                            SettingsCard(colors, title = "Popisky") {
                                val activeModel =
                                    if (working.scaleLabelsWithCanvas) working.labelSizeScaledPx else working.labelSizeFixedPx
                                val logicalLabelPxPerMm = 10f

                                LabeledSliderRow(
                                    label = "Velikost popisků",
                                    description = if (working.scaleLabelsWithCanvas)
                                        "Velikost popisků na plátně (doporučeno pro export)"
                                    else
                                        "Pevná obrazovková velikost popisků v pixelech.",
                                    value = activeModel,
                                    range = if (working.scaleLabelsWithCanvas) 5f..200f else 5f..100f,
                                    step = 1f,
                                    valueText = {
                                        if (working.scaleLabelsWithCanvas)
                                            "${((it / logicalLabelPxPerMm) * 10f).roundToInt() / 10f} mm"
                                        else
                                            "${it.roundToInt()} px"
                                    },
                                    colors = colors,
                                    uiscale = ui
                                ) { uiV ->
                                    working =
                                        if (working.scaleLabelsWithCanvas) working.copy(labelSizeScaledPx = uiV)
                                        else working.copy(labelSizeFixedPx = uiV)
                                }

                                ToggleLabelRow(
                                    value = working.scaleLabelsWithCanvas,
                                    colors = colors,
                                    ui = ui
                                ) { working = working.copy(scaleLabelsWithCanvas = it) }
                            }
                        }

                            if (scrollState.maxValue > 0) {
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(scrollState),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .background(color = settingsSurfaceColor("dialog", colors)),
                                    style = ScrollbarStyle(
                                        minimalHeight = 24 * ui.dp,
                                        thickness = 8 * ui.dp,
                                        shape = RoundedCornerShape(100),
                                        hoverDurationMillis = 300,
                                        unhoverColor = colors.buttonColor,
                                        hoverColor = colors.hover
                                    )
                                )
                            }
                        }



                        BottomBar(
                            colors = colors,
                            onReset = { working = defaults },
                            onCancel = onClose,
                            onSave = {
                                SettingsManager.save(working)
                                onClose()
                            }
                        )
                    }
                }
            }
        }
    }
}


/* =========================================================
   Header / Bottom bar
   ========================================================= */

@Composable
private fun Header(colors: MongeColorsState, onClose: () -> Unit) {
    val ui = SettingsManager.current.UIscale/75f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Nastavení",
                fontSize = 20*ui.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text
            )
            Text(
                "Vzhled a chování nákresny",
                fontSize = 12*ui.sp,
                color = colors.text.copy(alpha = 0.65f)
            )
        }

        Spacer(Modifier.weight(1f))

        SkikoButton(onClick = onClose, width = 36*ui.dp, height = 32*ui.dp) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Zavřít",
                modifier = Modifier.size(18*ui.dp),
                tint = colors.text
            )
        }
    }
}

@Composable
private fun BottomBar(
    colors: MongeColorsState,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val ui= SettingsManager.current.UIscale/75f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(settingsSurfaceColor("panel", colors), RoundedCornerShape(14*ui.dp))
            .border(1*ui.dp, colors.base.copy(alpha = if (colors.isDark) 0.34f else 0.16f), RoundedCornerShape(14*ui.dp))
            .padding(horizontal = 10*ui.dp, vertical = 9*ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsActionButton(
            text = "Obnovit výchozí",
            colors = colors,
            ui = ui,
            kind = SettingsButtonKind.Tertiary,
            onClick = onReset
        )

        Spacer(Modifier.weight(1f))

        SettingsActionButton(
            text = "Zrušit",
            colors = colors,
            ui = ui,
            kind = SettingsButtonKind.Secondary,
            onClick = onCancel
        )
        Spacer(Modifier.width(8*ui.dp))

        SettingsActionButton(
            text = "Uložit",
            colors = colors,
            ui = ui,
            kind = SettingsButtonKind.Primary,
            icon = true,
            onClick = onSave
        )
    }
}

private enum class SettingsButtonKind { Primary, Secondary, Tertiary }

@Composable
private fun SettingsActionButton(
    text: String,
    colors: MongeColorsState,
    ui: Float,
    kind: SettingsButtonKind,
    icon: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10*ui.dp)
    val background = when (kind) {
        SettingsButtonKind.Primary -> colors.selected.copy(alpha = if (hovered) 1.0f else 0.92f)
        SettingsButtonKind.Secondary -> settingsSurfaceColor("panelAlt", colors)
        SettingsButtonKind.Tertiary -> Color.Transparent
    }
    val border = when (kind) {
        SettingsButtonKind.Primary -> colors.selected.copy(alpha = 0.72f)
        SettingsButtonKind.Secondary -> colors.base.copy(alpha = if (colors.isDark) 0.42f else 0.20f)
        SettingsButtonKind.Tertiary -> colors.base.copy(alpha = if (hovered) 0.28f else 0.12f)
    }
    val foreground = if (kind == SettingsButtonKind.Primary) Color.White else colors.text.copy(alpha = 0.86f)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(background, shape)
            .border(1.dp, border, shape)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 13*ui.dp, vertical = 8*ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(
                painter = painterResource("icons/check.svg"),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16*ui.dp)
            )
            Spacer(Modifier.width(7*ui.dp))
        }
        Text(text, color = foreground, fontSize = 13*ui.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* =========================================================
   Reusable UI blocks
   ========================================================= */

@Composable
private fun SettingsCard(
    colors: MongeColorsState,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val ui = SettingsManager.current.UIscale/75f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(settingsSurfaceColor("panel", colors), RoundedCornerShape(14*ui.dp))
            .border(1*ui.dp, colors.base.copy(alpha = if (colors.isDark) 0.38f else 0.16f), RoundedCornerShape(14*ui.dp))
            .padding(13*ui.dp),
        verticalArrangement = Arrangement.spacedBy(8*ui.dp)
    ) {
        if (title != null) {
            Text(
                title.uppercase(),
                color = colors.text.copy(alpha = 0.58f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11*ui.sp
            )
        }
        content()
    }
}


@Composable
private fun FormRow(
    label: String,
    colors: MongeColorsState,
    description: String? = null,
    ui: Float,
    controlMinWidth: Dp = 190*ui.dp,
    content: @Composable RowScope.() -> Unit
) {

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 42*ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.text, fontSize = 13*ui.sp, fontWeight = FontWeight.Medium)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    color = colors.text.copy(alpha = 0.58f),
                    fontSize = 11*ui.sp,
                    lineHeight = 14*ui.sp
                )
            }
        }

        Spacer(Modifier.width(12*ui.dp))

        Row(
            modifier = Modifier.widthIn(min = controlMinWidth),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun ValueChip(text: String, colors: MongeColorsState, ui: Float) {
    Box(
        Modifier
            .widthIn(min = 58*ui.dp)
            .background(colors.selected.copy(alpha = if (colors.isDark) 0.18f else 0.09f), RoundedCornerShape(999.dp))
            .border(1*ui.dp, colors.selected.copy(alpha = if (colors.isDark) 0.34f else 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9*ui.dp, vertical = 4*ui.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = colors.text.copy(alpha = 0.90f),
            fontSize = 12*ui.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LabeledSliderRow(
    label: String,
    description: String? = null,
    value: Float,
    uiscale: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    valueText: (Float) -> String,
    colors: MongeColorsState,
    onChange: (Float) -> Unit,

) {
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(range.start, range.endInclusive)
        if (step <= 0) return clamped
        val stepsFromMin = ((clamped - range.start) / step).roundToInt()
        val snapped = range.start + stepsFromMin * step
        // stabilizace (ať to necestuje na float přesnosti)
        return (snapped * 1000f).roundToInt() / 1000f
    }

    var ui by remember(value, range.start, range.endInclusive) {
        mutableStateOf(value.coerceIn(range.start, range.endInclusive))
    }

    FormRow(
        label = label,
        description = description,
        colors = colors,
        ui = uiscale,
        controlMinWidth = 330*uiscale.dp
    ) {
        Slider(
            modifier = Modifier.width(240*uiscale.dp),
            value = ui,
            onValueChange = { raw ->
                ui = raw.coerceIn(range.start, range.endInclusive)
            },
            onValueChangeFinished = {
                val snapped = snap(ui)
                ui = snapped
                onChange(snapped)
            },
            valueRange = range,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = colors.selected,
                activeTrackColor = colors.selected,
                inactiveTrackColor = colors.base.copy(alpha = if (colors.isDark) 0.34f else 0.18f)
            )
        )
        Spacer(Modifier.width(10*uiscale.dp))
        ValueChip(valueText(ui), colors, uiscale)
    }
}

private object CenterPopupPositionProvider : PopupPositionProvider {
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
}

@Composable
private fun ThemeModeSwitch(
    value: Boolean,
    ui: Float,
    colors: MongeColorsState,
    onChange: (Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(999.dp)
    val trackWidth = 70f*ui.dp
    val trackHeight = 34f*ui.dp
    val trackPadding = 4f*ui.dp
    val knobSize = 30f*ui.dp
    val knobOffset by animateDpAsState(
        targetValue = if (value) trackWidth - knobSize - 2f*trackPadding else 0.dp,
        label = "settings_theme_switch_knob"
    )
    val trackBrush = if (value) {
        Brush.horizontalGradient(listOf(Color(0xFF101826), Color(0xFF25324A)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFFFF4BF), Color(0xFFEAF5FF)))
    }
    val knobColor = if (value) Color(0xFF192235) else Color(0xFFFFFDF3)
    val activeTint = if (value) Color(0xFFEAF0FF) else Color(0xFF6D4B00)
    val inactiveTint = colors.text.copy(alpha = 0.36f)

    Box(
        modifier = Modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(shape)
            .background(trackBrush, shape)
            .border(
                1f*ui.dp,
                colors.base.copy(alpha = if (hovered) 0.62f else 0.34f),
                shape
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onChange(!value) }
            .padding(trackPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(knobSize)
                .shadow(if (hovered) 7f*ui.dp else 4f*ui.dp, RoundedCornerShape(999.dp))
                .background(knobColor, RoundedCornerShape(999.dp))
                .border(1f*ui.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource("icons/sun-dim.svg"),
                    contentDescription = "Světlý režim",
                    tint = if (!value) activeTint else inactiveTint,
                    modifier = Modifier.size(17f*ui.dp)
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource("icons/moon.svg"),
                    contentDescription = "Tmavý režim",
                    tint = if (value) activeTint else inactiveTint,
                    modifier = Modifier.size(16f*ui.dp)
                )
            }
        }
    }
}

@Composable
private fun ToggleLabelRow(
    value: Boolean,
    ui: Float,
    colors: MongeColorsState,
    onChange: (Boolean) -> Unit
) {
    FormRow(
        label = "Škálované popisky",
        description = "Popisky mají pevnou velikost nezávisle na zoomu.",
        colors = colors,
        ui = ui,
        controlMinWidth = 120*ui.dp
    ) {
        Checkbox(
            checked = value,
            onCheckedChange = { onChange(!value) },
            modifier = Modifier.width(92*ui.dp).height(32*ui.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = colors.selected,
                uncheckedColor = colors.base.copy(alpha = 0.55f),
                checkmarkColor = Color.White)
        )
    }
}
