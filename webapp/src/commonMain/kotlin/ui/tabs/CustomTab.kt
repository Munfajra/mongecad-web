package ui.tabs

import ui.components.TooltipPlacement
import ui.components.TooltipArea
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import ui.resources.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import model.*
import serialization.SettingsManager


@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun CustomTabBar(
    tabs: SnapshotStateList<AppTab>,
    currentTabIndex: MutableState<Int>,
    onAddTab: () -> Unit,
    onCloseTab: (Int) -> Unit,
    tabWidth: Dp = 158.dp,
    tabHeight: Dp = 32.dp,
    onRequestCanvasFocus: () -> Unit,
) {
    val ui = SettingsManager.current.UIscale/75f
    val tabUi = ui * 1.08f
    val colors = LocalMongeColors.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var editingTabId by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf(TextFieldValue("")) }
    val activeTabId by remember {
        derivedStateOf { tabs.getOrNull(currentTabIndex.value)?.id }
    }

    fun commitRename(tab: DrawingTab) {
        val newName = editingText.text.trim()
        if (newName.isNotEmpty() && newName != tab.state.displayName) {
            tab.state.displayName = newName
            tab.state.isDirty = true
        }
        editingTabId = null
    }

    fun cancelRename() {
        editingTabId = null
    }

    fun startRename(tab: DrawingTab) {
        editingTabId = tab.id
        editingText = TextFieldValue(
            text = tab.state.displayName,
            selection = TextRange(0, tab.state.displayName.length)
        )
    }

    fun activateTab(index: Int) {
        if (index == currentTabIndex.value) {
            onRequestCanvasFocus()
        } else {
            currentTabIndex.value = index
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46f * ui.dp)
            .background(colors.background)
            .border(0.dp, colors.base, RectangleShape)
            .border(1.dp, colors.base.copy(alpha = if (colors.isDark) 0.34f else 0.16f), RectangleShape)
            .horizontalScroll(scroll)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val sd = change.scrollDelta
                val deltaX = when {
                    event.keyboardModifiers.isShiftPressed -> sd.y
                    sd.x != 0f -> sd.x
                    else -> sd.y
                }
                if (deltaX != 0f) {
                    scope.launch { scroll.scrollBy(-deltaX * 40f) }
                }
            }
            .clipToBounds()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight()
        ) {
            tabs.forEachIndexed { index, tab ->
                var isHovered by remember(tab) { mutableStateOf(false) }

                val isActive = tab.id == activeTabId
                val isEditing = tab is DrawingTab && editingTabId == tab.id
                val accent = customTabAccent(tab)
                val bg = remember(accent, colors.isDark, isActive, isHovered, isEditing) {
                    customTabBrush(
                        accent = accent,
                        isDark = colors.isDark,
                        active = isActive,
                        hovered = isHovered,
                        editing = isEditing
                    )
                }
                val borderColor = when {
                    isEditing -> colors.selected.copy(alpha = 0.82f)
                    isActive -> accent.copy(alpha = if (colors.isDark) 0.64f else 0.48f)
                    isHovered -> accent.copy(alpha = if (colors.isDark) 0.42f else 0.30f)
                    else -> colors.base.copy(alpha = if (colors.isDark) 0.34f else 0.16f)
                }
                val textColor = when {
                    isActive -> colors.text
                    isHovered -> colors.text.copy(alpha = 0.92f)
                    else -> colors.text.copy(alpha = if (colors.isDark) 0.72f else 0.68f)
                }
                val editorTextColor = colors.text
                val editorBg = if (colors.isDark) {
                    Color(0xFF111821)
                } else {
                    Color(0xFFFFFFFF)
                }

                val displayName = when (tab) {
                    is StartTab -> "Start"
                    is DrawingTab -> tab.state.displayName
                }
                val width = when (tab){
                    is DrawingTab -> tabWidth*tabUi
                    is StartTab -> 42f*tabUi.dp
                }
                val tabShape = RoundedCornerShape(10f * tabUi.dp)

                // Tooltip jen když má smysl (u DrawingTab)
                val content = @Composable {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4f*tabUi.dp)
                            .drawBehind {
                                if (isActive) {
                                    val glowAlpha = if (colors.isDark) 0.22f else 0.075f
                                    val glowRadius = size.width * if (colors.isDark) 0.92f else 0.76f
                                    val center = Offset(size.width * 0.46f, size.height * 0.58f)

                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            0.00f to accent.copy(alpha = glowAlpha),
                                            0.42f to accent.copy(alpha = glowAlpha * 0.42f),
                                            1.00f to Color.Transparent,
                                            center = center,
                                            radius = glowRadius
                                        ),
                                        radius = glowRadius,
                                        center = center
                                    )

                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            0.00f to accent.copy(alpha = glowAlpha * 0.64f),
                                            0.55f to accent.copy(alpha = glowAlpha * 0.20f),
                                            1.00f to Color.Transparent,
                                            center = Offset(size.width * 0.98f, size.height * 0.52f),
                                            radius = glowRadius
                                        ),
                                        radius = glowRadius,
                                        center = Offset(size.width * 0.98f, size.height * 0.52f)
                                    )
                                }
                            }
                            .clip(tabShape)
                            .background(bg)
                            .border(1.dp, borderColor, tabShape)
                            .width(width)
                            .height(tabHeight*tabUi)
                            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                            .then(
                                if (isEditing) {
                                    Modifier
                                } else {
                                    Modifier.pointerInput(tab.id, index) {
                                        // Žádný onDoubleTap – ten drží onTap ~300 ms (double-tap
                                        // timeout) a způsoboval znatelnou prodlevu při přepínání.
                                        // Přejmenování se spouští klikem na už aktivní tab.
                                        detectTapGestures(
                                            onTap = {
                                                if (tab is DrawingTab && index == currentTabIndex.value) {
                                                    startRename(tab)
                                                } else {
                                                    activateTab(index)
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 10f*tabUi.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Ikonka módu jen pro DrawingTab
                            if (tab is DrawingTab) {
                                val projectionMode = tab.state.projectionMode
                                val modeIconPath = remember(projectionMode) {
                                    when (projectionMode) {
                                        ProjectionMode.MONGE -> "icons/mongeM.svg"
                                        ProjectionMode.AXO -> "icons/axomode.svg"
                                        ProjectionMode.PLANE -> "icons/geometry.svg"
                                        ProjectionMode.KOTO -> "icons/kotomode.svg"
                                    }
                                }
                                val modePainter = painterResource(modeIconPath)

                                Icon(
                                    painter = modePainter,
                                    contentDescription = "Mód",
                                    modifier = Modifier.size(20f*tabUi.dp),
                                    tint = if (isActive || isHovered) accent else textColor.copy(alpha = 0.88f)
                                )

                                Spacer(Modifier.width(7f*tabUi.dp))
                            }
                            if (tab is StartTab){
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Start",
                                    tint = if (isActive || isHovered) accent else textColor,
                                    modifier = Modifier
                                        .size(25f*tabUi.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable (
                                            interactionSource = interaction,
                                            indication = null
                                        ) {
                                            activateTab(index)
                                        }
                                )


                            }
                            else{

                            if (tab is DrawingTab && isEditing) {
                                val focusRequester = remember(tab.id) { FocusRequester() }
                                var editorWasFocused by remember(tab.id) { mutableStateOf(false) }
                                LaunchedEffect(tab.id) {
                                    focusRequester.requestFocus()
                                }

                                BasicTextField(
                                    value = editingText,
                                    onValueChange = { editingText = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = editorTextColor,
                                        fontSize = 13f * tabUi.sp
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6f * tabUi.dp))
                                        .background(editorBg)
                                        .border(1.dp, colors.selected.copy(alpha = 0.7f), RoundedCornerShape(6f * tabUi.dp))
                                        .padding(horizontal = 6f * tabUi.dp, vertical = 2f * tabUi.dp)
                                        .padding(end = 6f * tabUi.dp)
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { focus ->
                                            if (focus.isFocused) {
                                                editorWasFocused = true
                                            } else if (editorWasFocused && editingTabId == tab.id) {
                                                commitRename(tab)
                                            }
                                        }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                            when (event.key) {
                                                Key.Enter, Key.NumPadEnter -> {
                                                    commitRename(tab)
                                                    true
                                                }
                                                Key.Escape -> {
                                                    cancelRename()
                                                    true
                                                }
                                                else -> false
                                            }
                                        }
                                )
                            } else {
                                Text(
                                    text = displayName,
                                    color = textColor,
                                    fontSize = 13f * tabUi.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 7f * tabUi.dp)
                                )
                            }

                            // Close jen pokud tab lze zavřít a není poslední closable
                            if (tab is DrawingTab) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Zavřít",
                                    tint = textColor.copy(alpha = if (isHovered || isActive) 0.86f else 0.52f),
                                    modifier = Modifier
                                        .size(15f*tabUi.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {

                                                onCloseTab(index)
                                        }
                                )
                            }
                        }
                        }
                    }
                }

                if (tab is DrawingTab) {
                    TooltipArea(
                        tooltip = {
                            Box(Modifier.background(color=Color.DarkGray).padding(6f*ui.dp)) {
                                Text(displayName, color = Color.White, fontSize = 12f * ui.sp)
                            }
                        },
                        delayMillis = 250,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            offset = DpOffset(x=10.dp, y=-40.dp),
                        )
                    ) {
                        content()
                    }
                } else {
                    content()
                }
            }

            // "+" tlačítko
            Box(
                modifier = Modifier
                    .height(tabHeight*tabUi)
                    .padding(start = 5f*tabUi.dp, end = 8f*tabUi.dp)
                    .clip(RoundedCornerShape(10f * tabUi.dp))
                    .background(customTabAddButtonBrush(colors.isDark))
                    .border(
                        1.dp,
                        colors.base.copy(alpha = if (colors.isDark) 0.42f else 0.18f),
                        RoundedCornerShape(10f * tabUi.dp)
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onAddTab() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Přidat záložku",
                    tint = colors.text.copy(alpha = if (colors.isDark) 0.78f else 0.68f),
                    modifier = Modifier
                        .padding(horizontal = 8f*tabUi.dp)
                        .size(17f*tabUi.dp)
                )
            }
        }
    }
}

private fun customTabAccent(tab: AppTab): Color =
    when (tab) {
        is StartTab -> Color(0xFF3E7FE8)
        is DrawingTab -> when (tab.state.projectionMode) {
            ProjectionMode.MONGE -> Color(0xFF3E7FE8)
            ProjectionMode.PLANE -> Color(0xFF27A16D)
            ProjectionMode.KOTO -> Color(0xFFE08A2E)
            ProjectionMode.AXO -> Color(0xFF8B6BE8)
        }
    }

private fun customTabBarBrush(isDark: Boolean): Brush =
    if (isDark) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF080A0D),
                0.42f to Color(0xFF0D1116),
                1.00f to Color(0xFF10151B)
            ),
            start = Offset.Zero,
            end = Offset(1400f, 420f)
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFF7FAFD), Color(0xFFEFF5FB), Color(0xFFF8F5EF)),
            start = Offset.Zero,
            end = Offset(1400f, 420f)
        )
    }

private fun customTabBrush(
    accent: Color,
    isDark: Boolean,
    active: Boolean,
    hovered: Boolean,
    editing: Boolean
): Brush {
    val colors = when {
        editing && isDark -> listOf(Color(0xFF121A24), Color(0xFF172233))
        editing -> listOf(Color(0xFFFFFFFF), Color(0xFFEAF2FF))
        active && isDark -> listOf(Color(0xFF11161D), accent.copy(alpha = 0.22f), Color(0xFF151B24))
        active -> listOf(Color(0xFFFFFFFF), accent.copy(alpha = 0.16f), Color(0xFFF8FAFC))
        hovered && isDark -> listOf(Color(0xFF10151B), accent.copy(alpha = 0.12f), Color(0xFF121820))
        hovered -> listOf(Color(0xFFFFFFFF), accent.copy(alpha = 0.08f), Color(0xFFF8FAFC))
        isDark -> listOf(Color(0xFF0D1116), Color(0xFF10151B))
        else -> listOf(Color(0xFFF8FAFC), Color(0xFFFFFFFF))
    }

    return Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset(420f, 120f)
    )
}

private fun customTabAddButtonBrush(isDark: Boolean): Brush =
    if (isDark) {
        Brush.linearGradient(
            listOf(Color(0xFF10151B), Color(0xFF121821)),
            start = Offset.Zero,
            end = Offset(220f, 80f)
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFF8FAFC), Color(0xFFFFFFFF)),
            start = Offset.Zero,
            end = Offset(220f, 80f)
        )
    }
