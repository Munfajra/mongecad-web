package ui.colorpicker


import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import model.LocalMongeColors
import model.MongeColorsState
import serialization.SerializableColor
import serialization.SettingsManager
import serialization.defaultQuickColorPalette
import ui.mongeui.toolbar.SkikoButton
import kotlin.math.*

@Composable
private fun ColorSwatch(
    color: Color,
    ui: Float = SettingsManager.current.UIscale/75f,
    selected: Boolean,
    colors: MongeColorsState,
    size: Dp = 30f*ui.dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            selected -> 1.08f
            hovered  -> 1.05f
            else     -> 1.0f
        },
        animationSpec = tween(120),
        label = "swatchScale"
    )

    // “Zvednutí” swatche (jemný desktop feel)
    val lift by animateFloatAsState(
        targetValue = if (hovered && !selected) -2f else 0f,
        animationSpec = tween(120),
        label = "swatchLift"
    )

    val ringAlpha = when {
        selected -> 0.95f
        hovered  -> 0.55f
        else     -> 0.20f
    }

    var shape = RoundedCornerShape(8f*ui.dp)

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                // velmi jemný “shadow” přes alpha + elevation efekt
                shadowElevation = if (hovered || selected) 6f else 0f
                shape = shape
                clip = true
            }
            .clip(shape)
            .background(color)
            .border(
                width = 1.dp,
                color = colors.base.copy(alpha = 0.25f),
                shape = shape
            )
            // ring highlight (vnější)
            .border(
                width = 2f*ui.dp,
                color = colors.selected.copy(alpha = ringAlpha),
                shape = shape
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onClick() }
    )
}

@OptIn(ExperimentalComposeUiApi::class)

@Composable
fun ColorPickerDropdown(
    selectedColor: Color,
    ui: Float = SettingsManager.current.UIscale/75f,
    onColorPreview: (Color) -> Unit,
    onColorConfirm: (Color) -> Unit,
    palette: List<Color>? = null
) {
    val colors = LocalMongeColors.current
    var expanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var openedColor by remember { mutableStateOf(selectedColor) }
    var mixingColor by remember { mutableStateOf(selectedColor) }
    var pickerSeedColor by remember { mutableStateOf(selectedColor) }
    var selectedPaletteIndex by remember { mutableIntStateOf(0) }
    val quickPalette = palette ?: SettingsManager.current.customColorPalette.map { it.toColor() }

    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val padding = 8
                val availableRight = windowSize.width - anchorBounds.right
                val availableLeft = anchorBounds.left

                val x = when {
                    availableRight >= popupContentSize.width + padding ->
                        anchorBounds.right + padding
                    availableLeft >= popupContentSize.width + padding ->
                        anchorBounds.left - popupContentSize.width - padding
                    else -> (windowSize.width - popupContentSize.width) / 2
                }

                val y = anchorBounds.top
                return IntOffset(x, y)
            }
        }
    }

    fun saveQuickPalette(updated: List<Color>) {
        SettingsManager.save(
            SettingsManager.current.copy(
                customColorPalette = updated.map { SerializableColor.from(it) }
            )
        )
    }

    Box {
        val triggerInteraction = remember { MutableInteractionSource() }
        val triggerHovered by triggerInteraction.collectIsHoveredAsState()

        val triggerScale by animateFloatAsState(
            targetValue = if (triggerHovered) 1.06f else 1f,
            animationSpec = tween(120),
            label = "triggerScale"
        )

        val triggerShape = RoundedCornerShape(7.dp)

        Box(
            modifier = Modifier
                .size(22f*ui.dp)
                .graphicsLayer {
                    scaleX = triggerScale
                    scaleY = triggerScale
                    shadowElevation = if (triggerHovered) 6f else 0f
                    shape = triggerShape
                    clip = true
                }
                .clip(triggerShape)
                .background(selectedColor)
                .border(3.dp, colors.selected, triggerShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(triggerInteraction)
                .clickable(
                    interactionSource = triggerInteraction,
                    indication = null
                ) { expanded = true }
        )

        if (expanded) {
            LaunchedEffect(expanded) {
                openedColor = selectedColor
                mixingColor = selectedColor
                pickerSeedColor = selectedColor
                advancedExpanded = false
                selectedPaletteIndex = quickPalette.indexOfFirst { it.sameRgb(selectedColor) }.takeIf { it >= 0 } ?: 0
            }

            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(12f*ui.dp),
                    color = colors.background.copy(alpha = 0.98f),
                    elevation = 10f*ui.dp,
                    modifier = Modifier
                        .width(246f*ui.dp)
                        .animateContentSize(animationSpec = tween(180))
                        .border(
                            1.dp,
                            colors.base.copy(alpha = if (colors.isDark) 0.55f else 0.30f),
                            RoundedCornerShape(12f*ui.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(9f*ui.dp),
                        verticalArrangement = Arrangement.spacedBy(8f*ui.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Barva",
                                    color = colors.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13f*ui.sp
                                )
                                Text(
                                    mixingColor.toHex(),
                                    color = colors.text.copy(alpha = 0.62f),
                                    fontSize = 10.5f*ui.sp
                                )
                            }
                            ColorPreviewCard("Teď", openedColor, colors, ui, compact = true)
                            Spacer(Modifier.width(6f*ui.dp))
                            ColorPreviewCard("Mix", mixingColor, colors, ui, compact = true)
                        }

                        Divider(color = colors.base.copy(alpha = 0.28f))

                        Column(verticalArrangement = Arrangement.spacedBy(6f*ui.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Rychlý výběr",
                                    color = colors.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5f*ui.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "slot ${selectedPaletteIndex + 1}",
                                    color = colors.text.copy(alpha = 0.58f),
                                    fontSize = 10f*ui.sp
                                )
                            }

                            quickPalette.chunked(4).forEachIndexed { rowIndex, rowColors ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6f*ui.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowColors.forEachIndexed { columnIndex, c ->
                                        val index = rowIndex * 4 + columnIndex
                                        ColorSwatch(
                                            color = c,
                                            selected = index == selectedPaletteIndex,
                                            colors = colors,
                                            size = 27f*ui.dp
                                        ) {
                                            selectedPaletteIndex = index
                                            mixingColor = c
                                            pickerSeedColor = c
                                            onColorConfirm(c)
                                        }
                                    }
                                }
                            }
                        }

                        if (advancedExpanded) {
                            Divider(color = colors.base.copy(alpha = 0.28f))

                            Column(verticalArrangement = Arrangement.spacedBy(8f*ui.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Míchání",
                                        color = colors.text,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12f*ui.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        mixingColor.toHex(),
                                        color = colors.text.copy(alpha = 0.68f),
                                        fontSize = 11f*ui.sp,
                                        textAlign = TextAlign.End
                                    )
                                }

                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    HueSaturationPicker(
                                        wheelSize = 180f*ui.dp,
                                        initialColor = pickerSeedColor,
                                        onColorSelected = {
                                            mixingColor = it
                                            onColorPreview(it)
                                        }
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6f*ui.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SkikoButton(
                                        width = 76f*ui.dp,
                                        height = 28f*ui.dp,
                                        onClick = {
                                            val reset = defaultQuickColorPalette.map { it.toColor() }
                                            saveQuickPalette(reset)
                                            selectedPaletteIndex = 0
                                            mixingColor = reset.first()
                                            pickerSeedColor = reset.first()
                                        }
                                    ) {
                                        Text("Reset", fontSize = 10.5f*ui.sp)
                                    }
                                    SkikoButton(
                                        width = 114f*ui.dp,
                                        height = 28f*ui.dp,
                                        onClick = {
                                            if (quickPalette.isNotEmpty()) {
                                                val updated = quickPalette.toMutableList()
                                                val index = selectedPaletteIndex.coerceIn(0, updated.lastIndex)
                                                updated[index] = mixingColor
                                                saveQuickPalette(updated)
                                                selectedPaletteIndex = index
                                                pickerSeedColor = mixingColor
                                            }
                                        }
                                    ) {
                                        Text("Uložit slot", fontSize = 10.5f*ui.sp)
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6f*ui.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SkikoButton(
                                width = 74f*ui.dp,
                                height = 28f*ui.dp,
                                onClick = {
                                    advancedExpanded = !advancedExpanded
                                }
                            ) {
                                Text(
                                    if (advancedExpanded) "Méně" else "Více",
                                    fontSize = 10.5f*ui.sp
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            SkikoButton(
                                width = 72f*ui.dp,
                                height = 28f*ui.dp,
                                onClick = {
                                    expanded = false
                                }
                            ) {
                                Text("Zavřít", fontSize = 10.5f*ui.sp)
                            }
                            SkikoButton(
                                width = 72f*ui.dp,
                                height = 28f*ui.dp,
                                onClick = {
                                    onColorConfirm(mixingColor)
                                    expanded = false
                                }
                            ) {
                                Text("Použít", fontSize = 10.5f*ui.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ColorPreviewCard(
    label: String,
    color: Color,
    colors: MongeColorsState,
    ui: Float,
    compact: Boolean = false
) {
    val width = if (compact) 28f else 34f
    val height = if (compact) 18f else 22f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = colors.text.copy(alpha = 0.62f),
            fontSize = if (compact) 8.5f*ui.sp else 9.5f*ui.sp,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .size(width = width*ui.dp, height = height*ui.dp)
                .clip(RoundedCornerShape(5f*ui.dp))
                .background(color)
                .border(1.dp, colors.base.copy(alpha = 0.65f), RoundedCornerShape(5f*ui.dp))
        )
    }
}

private fun Color.toHex(): String {
    fun channel(value: Float): String =
        (value.coerceIn(0f, 1f) * 255f).roundToInt()
            .coerceIn(0, 255)
            .toString(16)
            .uppercase()
            .padStart(2, '0')

    return "#${channel(red)}${channel(green)}${channel(blue)}"
}

private fun Color.sameRgb(other: Color): Boolean =
    red.closeTo(other.red) && green.closeTo(other.green) && blue.closeTo(other.blue)

private fun Float.closeTo(other: Float): Boolean = abs(this - other) < 0.004f

// java.awt.Color.RGBtoHSB na webu není – stejný převod RGB→HSV v čistém Kotlinu.
private fun Color.toHsvTriple(): Triple<Float, Float, Float> {
    val r = red.coerceIn(0f, 1f)
    val g = green.coerceIn(0f, 1f)
    val b = blue.coerceIn(0f, 1f)

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    return Triple(hue, if (max == 0f) 0f else delta / max, max)
}

@Composable
fun HueSaturationPicker(
    ui: Float = SettingsManager.current.UIscale/75f,
    wheelSize: Dp = 200f*ui.dp,
    initialColor: Color = Color.Red,
    onColorSelected: (Color) -> Unit
) {
    val density = LocalDensity.current
    var wheelTopLeftInWindow by remember { mutableStateOf(Offset.Zero) }
    val initialHsv = remember(initialColor) { initialColor.toHsvTriple() }

    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv.first) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv.second) }
    var value by remember(initialColor) { mutableFloatStateOf(initialHsv.third.coerceIn(0.01f, 1f)) }

    // pro “lupu”
    var isDragging by remember { mutableStateOf(false) }
    var lastPos by remember { mutableStateOf(Offset.Zero) }

    val imageBitmap = remember(wheelSize, density) {
        val sizePx = with(density) { wheelSize.roundToPx() }
        val img = ImageBitmap(sizePx, sizePx, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(img)
        val center = sizePx / 2f
        val radius = center
        val paint = Paint()

        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                val dx = x - center
                val dy = y - center
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= radius) {
                    val s = (dist / radius).coerceIn(0f, 1f)
                    val h = (atan2(dy, dx).toDegrees() + 360f) % 360f
                    paint.color = Color.hsv(h, s, 1f)
                    canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
                } else {
                    // mimo kruh transparent
                    paint.color = Color.Transparent
                    canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
                }
            }
        }
        img
    }

    fun updateFromOffset(pos: Offset) {
        lastPos = pos
        handleColorSelection(pos, density, wheelSize) { (h, s) ->
            hue = h
            saturation = s
            onColorSelected(Color.hsv(hue, saturation, value))
        }
    }

    val selectedColor = remember(hue, saturation, value) {
        Color.hsv(hue, saturation, value)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // KRUH – profi “wheel container”
        Box(
            modifier = Modifier
                .size(wheelSize)
                .onGloballyPositioned { coords ->
                    wheelTopLeftInWindow = coords.positionInWindow()
                }
                .shadow(10f*ui.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            isDragging = true
                            updateFromOffset(pos)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            updateFromOffset(change.position)
                            change.consume()
                        }
                    )
                }
        ) {
            Image(bitmap = imageBitmap, contentDescription = null)

            HSSelectionThumb(
                sizeDp = wheelSize,
                hue = hue,
                saturation = saturation,
                selectedColor = selectedColor
            )
        }

// ✅ lupa mimo wheel (nad oknem)
        if (isDragging) {
            Popup(
                popupPositionProvider = MagnifierPositionProvider(
                    wheelTopLeftInWindow = wheelTopLeftInWindow,
                    localCursorPos = lastPos
                ),
                onDismissRequest = { /* nic */ }
            ) {
                MagnifierBubbleContent(color = selectedColor)
            }
        }


        Spacer(Modifier.height(12f*ui.dp))

        Text("Jas", modifier = Modifier.padding(bottom = 4f*ui.dp))
        BrightnessSlider(
            width = wheelSize,
            hue = hue,
            saturation = saturation,
            value = value
        ) {
            value = it
            onColorSelected(Color.hsv(hue, saturation, value))
        }
    }
}
private class MagnifierPositionProvider(
    private val wheelTopLeftInWindow: Offset,
    private val localCursorPos: Offset
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // absolutní pozice kurzoru v okně
        val cursorX = wheelTopLeftInWindow.x + localCursorPos.x
        val cursorY = wheelTopLeftInWindow.y + localCursorPos.y

        // posun od kurzoru
        var x = (cursorX + 14f).roundToInt()
        var y = (cursorY - 14f - popupContentSize.height).roundToInt()

        // clamp do okna (ať to neutíká pryč)
        x = x.coerceIn(8, windowSize.width - popupContentSize.width - 8)
        y = y.coerceIn(8, windowSize.height - popupContentSize.height - 8)

        return IntOffset(x, y)
    }
}
@Composable
private fun MagnifierBubbleContent(    ui: Float = SettingsManager.current.UIscale/75f,
                                       color: Color) {
    Box(
        modifier = Modifier
            .size(54f*ui.dp)
            .shadow(10f*ui.dp, RoundedCornerShape(14f*ui.dp))
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14f*ui.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(14f*ui.dp))
            .padding(8f*ui.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10f*ui.dp))
                .background(color)
                .border(1.dp, Color.Black.copy(alpha = 0.20f), RoundedCornerShape(10f*ui.dp))
        )
    }
}

private fun handleColorSelection(
    offset: Offset,
    density: Density,
    sizeDp: Dp,
    onHSChange: (Pair<Float, Float>) -> Unit
) {
    with(density) {
        val sizePx = sizeDp.toPx()
        val center = sizePx / 2f
        val dx = offset.x - center
        val dy = offset.y - center
        val dist = sqrt(dx * dx + dy * dy)

        if (dist <= center) {
            val s = (dist / center).coerceIn(0f, 1f)
            val h = (atan2(dy, dx).toDegrees() + 360f) % 360f
            onHSChange(h to s)
        }
    }
}
@Composable
private fun HSSelectionThumb(
    sizeDp: Dp,
    hue: Float,
    saturation: Float,
    selectedColor: Color
) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.toPx() }
    val radius = sizePx / 2f

    // převod HS → pozice v kruhu
    val angleRad = (hue * kotlin.math.PI / 180.0).toFloat()
    val r = saturation * radius

    val x = radius + cos(angleRad) * r
    val y = radius + sin(angleRad) * r

    // kontrastní ring (bílá / černá podle luminance)
    val ring = if (selectedColor.luminance() > 0.55f) Color.Black else Color.White

    // trochu animace (ať to nepůsobí trhaně)
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(80),
        label = "thumbScale"
    )

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        drawCircle(
            color = ring,
            radius = 10f * scale,
            center = Offset(x, y),
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = selectedColor,
            radius = 9f * scale,
            center = Offset(x, y),
            style = Stroke(width = 2f)
        )
    }
}

@Composable
fun BrightnessSlider(
    ui: Float = SettingsManager.current.UIscale/75f,
    width: Dp,
    hue: Float,
    saturation: Float,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val colorStart = Color.Black
    val colorEnd = Color.hsv(hue, saturation, 1f)

    Box(
        modifier = Modifier
            .width(width)
            .height(28f*ui.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.horizontalGradient(listOf(colorStart, colorEnd)))
            .border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8f*ui.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}


private fun Float.toDegrees(): Float = this * 180f / PI.toFloat()
