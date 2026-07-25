package ui.planeUI.toolbar

import ui.components.TooltipArea
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dialogs.tools.RegularPolygonSidesDialog
import model.*
import serialization.*
import state.MongeState
import ui.components.MongeRibbonButton
import ui.mongeui.toolbar.*
import ui.resetStavu
import ui.theme.LocalMongeDimens


@Composable
fun PlaneObjectSelectionRow(
    selectedObject: Mongeobjects,
    onSelect: (Mongeobjects) -> Unit,
    state: MongeState,
    buttonsize: Dp
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val isDark = colors.isDark
    val ui = SettingsManager.current.UIscale/75f
    Row(
        modifier = Modifier
            .heightIn(min = buttonsize)
            .background(color = colors.background),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ---- běžné nástroje (beze změn) ----
        val items = listOf(
            Triple("Bod", Mongeobjects.POINTS, if (colors.isDark) painterResource("icons/pointDark.svg")else painterResource("icons/point.svg")),
            Triple("Přímka", Mongeobjects.LINES, if (colors.isDark)painterResource("icons/primkaDark.svg") else painterResource("icons/primka.svg")),
            Triple("Úsečka", Mongeobjects.SEGMENTS, if (colors.isDark)painterResource("icons/useckaDark.svg") else painterResource("icons/usecka.svg")),
            Triple("Úsečka na přímce", Mongeobjects.SEGMENT_ON_LINE, if (colors.isDark)painterResource("icons/usecka_naprimceDark.svg") else  painterResource("icons/usecka_naprimce.svg")),
            Triple("Křivka",Mongeobjects.CURVE,if (colors.isDark) painterResource("icons/curvesDark.svg") else painterResource("icons/curves.svg"))


        )
        items.forEach { (label, value, icon) ->
            val enabled = isObjectEnabled(value, state)
            MongeRibbonButton(
                text = label,
                selected = selectedObject == value,
                enabled = enabled,
                onClick = {
                    selectPlaneObjectTool(state, value)
                    onSelect(value)
                },
            ) {
                Image(painter = icon, contentDescription = label, modifier = Modifier.size(dimens.iconLg))
            }
        }

        /* ---------- KUŽELOSEČKY (rich popup) ------------------------------- */
        Box {
            var expanded by remember { mutableStateOf(false) }
            val isAnyConic = selectedObject in listOf(
                Mongeobjects.CIRCLE, Mongeobjects.ELLIPSE, Mongeobjects.PARABOLA,
                Mongeobjects.HYPERBOLA, Mongeobjects.CONICARC,
            )

            MongeRibbonButton(
                text = "Kuželosečka",
                selected = isAnyConic || expanded,
                onClick = { expanded = true
                          state.projekcnityp= ProjectionType.AUXILIARY},
                enabled = (state.drawobjects == Mongeobjects.CIRCLE ||
                        state.drawobjects == Mongeobjects.ELLIPSE ||
                        state.drawobjects == Mongeobjects.PARABOLA ||
                        state.drawobjects == Mongeobjects.HYPERBOLA ||
                        state.drawobjects == Mongeobjects.CONICARC ||
                        (state.projectionPhase == "pudorys_start" || state.projectionPhase == "narys_start"))
            ) {
                Image(
                    painter = if (colors.isDark) painterResource("icons/kuzeloseckaDark.svg") else painterResource("icons/kuzelosecka.svg"),
                    contentDescription = "Kuželosečka",
                    modifier = Modifier.size(dimens.iconLg)
                )
            }

            if (expanded) {
                RichDropdownPopupConics(
                    onDismiss = { expanded = false }
                ) {
                    SectionHeader("Kuželosečky")

                    ConicMenuItem(
                        title = "Kružnice",
                        subtitle = when (state.projekcnityp) {
                            ProjectionType.AUXILIARY -> "Vytvoří kružnici pomocí středu a poloměru"
                            else -> ""
                        },
                        painter = "icons/circle.svg",
                        ui = ui,
                        isDark = isDark
                    ) {
                        resetStavu(state, resetConstructionModifier = false)
                        state.drawobjects = Mongeobjects.CIRCLE
                        onSelect(Mongeobjects.CIRCLE)
                        updateConstructionInfo(state)
                        expanded = false
                    }

                    ConicMenuItem(
                        title = "Elipsa",
                        subtitle = when (state.projekcnityp) {
                            ProjectionType.AUXILIARY -> "Vytvoří elipsu pomocí středu a sdružených průměrů"
                            else -> ""
                        },
                        painter = "icons/ellipse.svg",
                        ui = ui,
                        isDark = isDark
                    ) {
                        resetStavu(state, resetConstructionModifier = false)
                        state.drawobjects = Mongeobjects.ELLIPSE
                        if (state.projekcnityp == ProjectionType.AUXILIARY) state.projekcnityp = ProjectionType.SINGLE
                        onSelect(Mongeobjects.ELLIPSE)
                        updateConstructionInfo(state)
                        expanded = false
                    }

                    ConicMenuItem(
                        title = "Parabola",
                        subtitle = when (state.projekcnityp) {
                            ProjectionType.AUXILIARY -> "Vytvoří parabolu pomocí vrcholu a ohniska"
                            else -> ""
                        },
                        painter = "icons/parabola.svg",
                        ui = ui,
                        isDark = isDark
                    ) {
                        resetStavu(state, resetConstructionModifier = false)
                        state.drawobjects = Mongeobjects.PARABOLA
                        if (state.projekcnityp == ProjectionType.AUXILIARY) state.projekcnityp = ProjectionType.SINGLE
                        onSelect(Mongeobjects.PARABOLA)
                        updateConstructionInfo(state)
                        expanded = false
                    }

                    ConicMenuItem(
                        title = "Hyperbola",
                        subtitle = when (state.projekcnityp) {
                            ProjectionType.AUXILIARY -> "Vytvoří hyperbolu pomocí 2 asymptot a vrcholu"
                            else -> ""
                        },
                        painter = "icons/hyperbola.svg",
                        ui = ui,
                        isDark = isDark
                    ) {
                        resetStavu(state, resetConstructionModifier = false)
                        state.drawobjects = Mongeobjects.HYPERBOLA
                        if (state.projekcnityp == ProjectionType.AUXILIARY) state.projekcnityp = ProjectionType.SINGLE
                        onSelect(Mongeobjects.HYPERBOLA)
                        updateConstructionInfo(state)
                        expanded = false
                    }

                    RichDropdownDivider()

                    SectionHeader("Úseky kuželoseček")

                    ConicMenuItem(
                        title = "Úsek kuželosečky",
                        subtitle = "Zvýrazní část zvolené kuželosečky",
                        painter = "icons/menu_conicarc.svg",
                        ui = ui,
                        isDark = isDark
                    ) {
                        resetStavu(state, resetConstructionModifier = false)
                        state.drawobjects = Mongeobjects.CONICARC
                        onSelect(Mongeobjects.CONICARC)
                        updateConstructionInfo(state)
                        expanded = false
                    }
                }
            }
        }
    }
}

/* ================== UI stavebnice pro „bohatý“ conic popup ================== */


@Composable
private fun SectionHeader(text: String) {
    val colors = LocalMongeColors.current
    Text(
        text = text,
        color = colors.text,
        fontSize = 16.sp,
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
    )
}


@Composable
private fun RichDropdownDivider() {
    val colors = LocalMongeColors.current
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.base.copy(alpha = 0.15f), RoundedCornerShape(1.dp))
    )
    Spacer(modifier = Modifier.height(4.dp))
}


fun isObjectEnabled(obj: Mongeobjects, state: MongeState): Boolean {
    val atStart = state.projectionPhase == "pudorys_start" ||
            state.projectionPhase == "narys_start"

    return when (obj) {
        Mongeobjects.NONE,
        Mongeobjects.ERASE          -> true                       // vždy povoleno

        else -> (state.drawobjects == obj) || atStart             // společná logika
    }
}

/**
 * Pořadí nástrojů v [PlaneObjectSelectionRow] (PLANE) pro klávesové zkratky 1-5.
 * Kuželosečky (rozbalovací) záměrně nejsou součástí.
 */
val planeObjectToolOrder = listOf(
    Mongeobjects.POINTS,
    Mongeobjects.LINES,
    Mongeobjects.SEGMENTS,
    Mongeobjects.SEGMENT_ON_LINE,
    Mongeobjects.CURVE
)

/** Výběr nástroje v PLANE – sdíleno klikem v toolbaru i klávesovou zkratkou. */
fun selectPlaneObjectTool(state: MongeState, value: Mongeobjects) {
    resetStavu(state, resetConstructionModifier = false)
    when (value) {
        Mongeobjects.LINES    -> state.drawobjects = Mongeobjects.LINES
        Mongeobjects.SEGMENTS -> state.drawobjects = Mongeobjects.SEGMENTS
        Mongeobjects.SEGMENT_ON_LINE -> state.drawobjects = Mongeobjects.SEGMENT_ON_LINE
        Mongeobjects.CURVE -> state.drawobjects = Mongeobjects.CURVE
        else                  -> state.drawobjects = value
    }
    state.projekcnityp = ProjectionType.AUXILIARY
    updateConstructionInfo(state)
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun PlaneProjectionModeSelector(
    state: MongeState,
    buttonsize: Dp
) {
    ProjectionStyleOnlyShell {
        HelpLineStyleEditor(state.currentHelpLineStyleSettings)
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AxisOrientationControls(state: MongeState, buttonsize: Dp) {
    var expanded by remember { mutableStateOf(false) }

    val actualButton = buttonsize * 0.5f
    val iconSize = actualButton * 0.68f

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp), // stejné jako jinde
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TooltipArea(
            tooltip = {
                Box(
                    Modifier
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Zobrazit osy (O)", color = Color.White)
                }
            },
            delayMillis = 450
        ) {
        // --- Zobrazení os ---
        SkikoButton(
            onClick = {
                state.axisVisible = !state.axisVisible

                val x = state.helpLinePudorys.find { it.id == "axisX" }
                val y = state.helpLinePudorys.find { it.id == "axisY" }
                val z = state.helpLineNarys.find { it.id == "axisZ" }
                val or = state.aidPointsLogical.find { it.id == "origin" }

                state.helpLinePudorys.remove(x)
                state.helpLinePudorys.remove(y)
                state.helpLineNarys.remove(z)
                state.aidPointsLogical.remove(or)
            },
            width = actualButton,
            height = actualButton,
            isSelected = state.axisVisible,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource("icons/osy.png"),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }

        // --- Orientace os + popup ---
        Box {
            TooltipArea(
                tooltip = {
                    Box(
                        Modifier
                            .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Orientace os", color = Color.White)
                    }
                },
                delayMillis = 450
            ) {
                SkikoButton(
                    onClick = { expanded = !expanded },
                    width = actualButton,
                    height = actualButton,
                    isSelected = expanded,
                    enabled = state.projectionMode != ProjectionMode.AXO
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AxisOrientationIcon(state, iconSize)
                    }
                }
            }

            if (expanded) {
                RichDropdownPopupConics(
                    onDismiss = { expanded = false },
                ) {
                    SectionHeader("Orientace os")

                    AxisToggleXInline(state)

                    if (
                        state.projectionMode == ProjectionMode.PLANE ||
                        state.projectionMode == ProjectionMode.KOTO
                    ) {
                        Spacer(Modifier.height(10.dp))
                        AxisToggleYInline(state)
                    }

                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
@Composable
private fun AxisOrientationIcon(state: MongeState, size: Dp) {
    val painter = when (state.projectionMode) {
        ProjectionMode.MONGE ->
            if (state.xAxisDirection == XAxisDirection.POSITIVE_RIGHT)
                "icons/arrow-right.svg"
            else
                "icons/arrow-left.svg"

        else -> when {
            state.xAxisDirection == XAxisDirection.POSITIVE_RIGHT &&
                    state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP ->
                "icons/xrightyup.svg"

            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT &&
                    state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP ->
                "icons/xleftyup.svg"

            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT &&
                    state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_DOWN ->
                "icons/xleftydown.svg"

            else ->
                "icons/xrightydown.svg"
        }
    }

    Icon(
        painter = painterResource(painter),
        contentDescription = "Orientace os",
        modifier = Modifier.size(size)
    )
}



@Composable
private fun AxisToggleXInline(state: MongeState) {
    val colors = LocalMongeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.hover.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Osa X", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Směr kladné osy X",
                color = colors.text.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkikoButton(
                onClick = { state.xAxisDirection = XAxisDirection.POSITIVE_LEFT },
                width = 40.dp,
                height = 34.dp,
                isSelected = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp)) }

            SkikoButton(
                onClick = { state.xAxisDirection = XAxisDirection.POSITIVE_RIGHT },
                width = 40.dp,
                height = 34.dp,
                isSelected = state.xAxisDirection == XAxisDirection.POSITIVE_RIGHT
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun AxisToggleYInline(state: MongeState) {
    val colors = LocalMongeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.hover.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Osa Y", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Směr kladné osy Y (PLANE/KOTO)",
                color = colors.text.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkikoButton(
                onClick = { state.yAxisDirectionPlane = YAxisDirectionPlane.POSITIVE_UP },
                width = 40.dp,
                height = 34.dp,
                isSelected = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            ) { Icon(Icons.Filled.KeyboardArrowUp, null, Modifier.size(20.dp)) }

            SkikoButton(
                onClick = { state.yAxisDirectionPlane = YAxisDirectionPlane.POSITIVE_DOWN },
                width = 40.dp,
                height = 34.dp,
                isSelected = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_DOWN
            ) { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(20.dp)) }
        }
    }
}


@Composable
fun ModeButtonPlane(buttonsize: Dp
) {    Box {
    var expanded by remember { mutableStateOf(false) }
    val isDark = LocalMongeColors.current.isDark
    val ui = SettingsManager.current.UIscale/75f
    val menuBus = LocalMenuBus.current
    SkikoButton(
        onClick = { expanded = true },
        width = buttonsize,
        height = buttonsize,
        isSelected = expanded,
    ) {
        Icon(
            painter = painterResource("icons/modeswitch.svg"),
            contentDescription = "Přepnutí módu",
            modifier = Modifier.size(buttonsize)
        )
    }

    if (expanded) {
        RichDropdownPopupConics(
            onDismiss = { expanded = false }
        ) {
            SectionHeader("Přepnutí módu")

            ConicMenuItem(
                title = "Mongeovo promítání",
                subtitle = "Celou nákresnu ztotožní s půdorysnou v  mongeově projekci a převede veškeré objekty",
                painter = "icons/mongeM.svg",
                ui= ui,
                isDark = isDark,
            ) {
                menuBus.pending = MenuCommand.OpenMonge
                expanded = false
            }
        }
    }
}
}
@Composable
fun ModeButtonMonge(buttonsize: Dp
) {
    val isDark = LocalMongeColors.current.isDark
    val ui = SettingsManager.current.UIscale/75f
    Box {
    var expanded by remember { mutableStateOf(false) }

    val menuBus = LocalMenuBus.current
    SkikoButton(
        onClick = { expanded = true },
        isSelected = expanded,
        width = buttonsize,
        height = buttonsize,
    ) {
        Icon(
            painter = painterResource("icons/modeswitch.svg"),
            contentDescription = "Přepnutí módu",
            modifier = Modifier.size(buttonsize)
        )
    }

    if (expanded) {
        RichDropdownPopupConics(
            onDismiss = { expanded = false }
        ) {
            SectionHeader("Přepnutí módu")

        }
    }
} }
@Composable
fun ModeButtonAxo(buttonsize: Dp
) {
    val isDark = LocalMongeColors.current.isDark
    val ui = SettingsManager.current.UIscale/75f
    Box {
        var expanded by remember { mutableStateOf(false) }

        val menuBus = LocalMenuBus.current
        SkikoButton(
            onClick = { expanded = true },
            isSelected = expanded,
            width = buttonsize,
            height = buttonsize,
        ) {
            Icon(
                painter = painterResource("icons/modeswitch.svg"),
                contentDescription = "Přepnutí módu",
                modifier = Modifier.size(buttonsize)
            )
        }

        if (expanded) {
            RichDropdownPopupConics(
                onDismiss = { expanded = false }
            ) {
                SectionHeader("Přepnutí módu")

                ConicMenuItem(
                    title = "Mongeovo promítání",
                    subtitle = "Převede projekt do mongeova promítání",
                    painter = "icons/mongeM.svg",
                    ui= ui,
                    isDark = isDark,
                ) {
                    menuBus.pending = MenuCommand.OpenMonge
                    expanded = false
                }
            }
        }
    } }
@Composable
fun ModeButtonKoto(buttonsize: Dp
) {
    val isDark = LocalMongeColors.current.isDark
    val ui = SettingsManager.current.UIscale/75f
    Box {
        var expanded by remember { mutableStateOf(false) }

        val menuBus = LocalMenuBus.current
        SkikoButton(
            onClick = { expanded = true },
            width = buttonsize,
        height = buttonsize,
            isSelected = expanded,
        ) {
            Icon(
                painter = painterResource("icons/modeswitch.svg"),
                contentDescription = "Přepnutí módu",
                modifier = Modifier.size(buttonsize)
            )
        }

        if (expanded) {
            RichDropdownPopupConics(
                onDismiss = { expanded = false }
            ) {
                SectionHeader("Přepnutí módu")

                ConicMenuItem(
                    title = "Mongeovo promítání",
                    subtitle = "Převede projekt do mongeova promítání",
                    painter = "icons/mongeM.svg",
                    ui= ui,
                    isDark = isDark,

                ) {
                    menuBus.pending = MenuCommand.OpenMonge
                    expanded = false
                }
            }
        }
    } }
fun cloneMongeStateViaJson(state: MongeState): MongeState {
    // stejné jako při ukládání - dotáhne pending věci do modelu
    commitSnapshot(state)

    val serialized = state.toSerialized()
    val wrapper = MongeFileV2(version = 2, state = serialized, history = state.history.toDto())

    val jsonText = MongeJson.encodeToString(MongeFileV2.serializer(), wrapper)

    val v2 = MongeJson.decodeFromString(MongeFileV2.serializer(), jsonText)
    val st = v2.state.toMongeState()

    if (v2.history != null) {
        st.history = v2.history.toHistoryState()
        st.history.snapshots.getOrNull(st.history.cursor)?.let { snap ->
            applySnapshot(st, snap)
        } ?: st.initHistory()
    } else {
        st.initHistory()
    }

    // tohle je „klon“, ne soubor:
    st.currentFile = null
    st.isDirty = false

    return st
}
fun makeMongeTabState(statePlane: MongeState): MongeState {
    // 1) klon původního (původní tab se nezmění)
    val st = cloneMongeStateViaJson(statePlane)

    // 2) udělej to samé, co teď děláš přímo na state (ale jen na kopii)
    resetStavu(st)
    st.projectionMode = ProjectionMode.MONGE
    normalizeStateForMongeConversion(st)
    updateConstructionInfo(st)

    // 3) „uložit a znovu otevřít“ efekt - druhý průchod to často hezky znormalizuje
    val normalized = cloneMongeStateViaJson(st)

    // pojmenování
    normalized.displayName = st.displayName + " (Monge)"
    normalized.isDirty = false
    normalized.currentFile = null

    return normalized
}
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PokrocileMenuButtonPlane(state: MongeState,buttonsize: Dp) {
    val colors = LocalMongeColors.current

    var mainPopupVisible by remember { mutableStateOf(false) }
    var showPolyDialog by remember { mutableStateOf(false) }

    RegularPolygonSidesDialog(
        show = showPolyDialog,
        initialSides = state.regularPolygon.sides,
        initialAskName = state.namingPolygon,
        initialStartLetter = state.namingPolygonStartLetter,
        onConfirm = { n, askName, startLetter ->
                state.namingPolygon = askName
            state.namingPolygonStartLetter = startLetter
            state.regularPolygon.sides = n
            state.drawobjects = Mongeobjects.REGULAR_POLYGON_IN_PLANE
            state.projekcnityp = ProjectionType.ASSOCIATED
            setProjectionPhase("pudorys_start", state)
            state.triggerRedraw++
        },
        onDismiss = { showPolyDialog = false }
    )

    fun runAndClose(action: () -> Unit) {
        action()
        mainPopupVisible = false
    }

    Box {
        TooltipArea(
            tooltip = {
                Box(Modifier.background(Color.DarkGray).padding(6.dp)) {
                    Text("Obrazce v rovině", color = colors.text)
                }
            },
            delayMillis = 500,
        ) {
            SkikoButton(
                onClick = { mainPopupVisible = !mainPopupVisible },
                isSelected = mainPopupVisible,
                width = buttonsize,
        height = buttonsize,
            ) {
                Icon(
                    painter = painterResource("icons/pentagon-plus.svg"),
                    contentDescription = "PLANE_OBJECTS",
                )
            }
        }

        if (mainPopupVisible) {
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        // levý okraj tlačítka, těsně pod tlačítkem
                        val x = anchorBounds.left
                        val y = anchorBounds.bottom

                        // (volitelné) jemný posun dolů o pár px:
                        return IntOffset(x, y + 6)
                    }
                },
                onDismissRequest = { mainPopupVisible = false },
                // focusable=true ti dá zavření klikem mimo + ESC
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .background(colors.background, shape = RoundedCornerShape(6.dp))
                        .border(1.dp, colors.base.copy(alpha = 0.35f), shape = RoundedCornerShape(6.dp))
                        .padding(vertical = 6.dp)
                        .width(buttonsize*2.25f)
                ) {
                    val items: List<Pair<String, () -> Unit>> = listOf(
                        "Trojúhelník" to {
                            state.drawobjects = Mongeobjects.REGULAR_POLYGON_IN_PLANE
                            state.regularPolygon.sides = 3
                            setProjectionPhase("pudorys_start", state)
                            state.triggerRedraw++
                        },
                        "Čtverec" to {
                            state.drawobjects = Mongeobjects.REGULAR_POLYGON_IN_PLANE
                            state.regularPolygon.sides = 4
                            setProjectionPhase("pudorys_start", state)
                            state.triggerRedraw++
                        },
                        "Pětiúhelník" to {
                            state.drawobjects = Mongeobjects.REGULAR_POLYGON_IN_PLANE
                            state.regularPolygon.sides = 5
                            setProjectionPhase("pudorys_start", state)
                            state.triggerRedraw++
                        },
                        "n-úhelník" to {
                            showPolyDialog = true
                        }
                    )

                    items.forEach { (label, onClick) ->
                        var isHovered by remember(label) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onHoverEnter(
                                    onEnter = { isHovered = true },
                                    onExit = { isHovered = false }
                                )
                                .background(
                                    if (isHovered)      {  if (colors.isDark) colors.selected else colors.selected.lighter(0.8f)
                    }
                    else colors.background
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    // u n-úhelníku chceme nejdřív zavřít popup, pak ukázat dialog
                                    if (label == "n-úhelník") {
                                        mainPopupVisible = false
                                        onClick()
                                    } else {
                                        runAndClose(onClick)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(label, color = colors.text)
                        }
                    }
                }
            }
        }
    }
}

// Vyříznuto: makeKotoTabState, makeAxoTabState – převod do axonometrie a kótovaného
// promítání; web má jen Monge a rovinu.
