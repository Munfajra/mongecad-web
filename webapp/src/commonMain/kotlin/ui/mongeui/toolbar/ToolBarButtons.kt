package ui.mongeui.toolbar

import ui.colorpicker.HueSaturationPicker
import ui.planeUI.toolbar.AxisOrientationControls
import utils.format
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import ui.components.TooltipArea
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import model.*
import model.axo.AxoMode
import model.classes.HelpLineStyleSettings
import serialization.SettingsManager
import monge.input.lines.directionHandlers.lines.revertLineSelectionPhaseOnModifierOff
import state.MongeState
import ui.components.MongeRibbonButton
import ui.theme.LocalMongeDimens
import ui.resetStavu
import monge.input.selection.CylinderPhase
import monge.input.meridian.startSolidOfRevolutionTool
import monge.input.meridian.startSolidOfRevolutionToolPudorys
import monge.input.ruledsurface.startRuledSurfaceTool
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SkikoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 100.dp,
    height: Dp = 40.dp,
    isSelected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hoverState = remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(4.dp)

    val targetBg = when {
        !enabled -> colors.base.copy(alpha = if (colors.isDark) 0.05f else 0.025f)
        isPressed -> colors.selected.copy(alpha = if (colors.isDark) 0.18f else 0.10f)
        isSelected -> colors.selected.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
        hoverState.value -> colors.hover.copy(alpha = if (colors.isDark) 0.16f else 0.08f)
        else -> colors.base.copy(alpha = if (colors.isDark) 0.07f else 0.035f)
    }
    val bg by animateColorAsState(targetBg, label = "skiko_btn_bg")

    val targetBorder = when {
        !enabled -> colors.disabled.copy(alpha = if (colors.isDark) 0.10f else 0.08f)
        isSelected -> colors.selected.copy(alpha = if (colors.isDark) 0.70f else 0.52f)
        hoverState.value -> colors.base.copy(alpha = if (colors.isDark) 0.28f else 0.16f)
        else -> colors.base.copy(alpha = if (colors.isDark) 0.14f else 0.08f)
    }
    val borderColor by animateColorAsState(targetBorder, label = "skiko_btn_border")

    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.97f else 1f,
        label = "skiko_btn_scale"
    )

    val contentColor = when {
        !enabled -> colors.disabled
        isSelected -> colors.selected
        hoverState.value -> if (colors.isDark) colors.buttonColor.lighter(0.8f) else colors.buttonColor.darker(0.68f)
        else -> colors.buttonColor
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> hoverState.value = true
                is HoverInteraction.Exit  -> hoverState.value = false
            }
        }
    }

    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, borderColor), shape)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    }
}



@Composable
fun SkikoRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ui: Float = SettingsManager.current.UIscale/75f,
    width: Dp = 140f*ui.dp,
    height: Dp = 40f*ui.dp,
    enabled: Boolean = true,
    textsize: TextUnit
) {
    // primární tlačítko
    SkikoButton(
        onClick     = onClick,
        isSelected  = selected,
        enabled     = enabled,         // ← přeposláno dál
        width       = width,
        height      = height,
        modifier    = modifier
    ) {
        // volitelné zeslabení barev, když není povoleno
        val contentAlpha = if (enabled) 1f else 0.4f     // 40 % krytí při disabled

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8f*ui.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Canvas(modifier = Modifier.size(12f*ui.dp)) {
                drawCircle(
                    color = Color.LightGray.copy(alpha = contentAlpha),
                    radius = size.minDimension / 2
                )
                if (selected) {
                    drawCircle(
                        color = Color(0xFF3d92ff).copy(alpha = contentAlpha),
                        radius = size.minDimension / 3
                    )
                }
            }
            Text(
                text,
                modifier  = Modifier.padding(start = 6.dp),
                fontSize = textsize,
                textAlign = TextAlign.Start,
                color     = LocalContentColor.current.copy(alpha = contentAlpha)
            )
        }
    }
}


@Composable
fun ModeToggleButtonPudorys(state: MongeState,buttonsize: Dp) {
    val isSelected = state.mongeMode == DrawingModeMonge.PUDORYS
    SkikoButton(
        onClick = {
            if (!isSelected) {
                state.mongeMode = DrawingModeMonge.PUDORYS
                if (state.drawobjects != Mongeobjects.GETDISTANCE
                    && state.drawobjects != Mongeobjects.TRANSPARALLEL
                    && state.drawobjects != Mongeobjects.TRANSORTH) {
                    resetStavu(state, resetConstructionModifier = false)
                }
            }
        },
        isSelected = isSelected,
        modifier = Modifier.size(buttonsize*1.25f, buttonsize/2f),

        ) {
        Icon(
            painter = painterResource("icons/pudorys.png"),
            contentDescription = "PŮDORYS",
            modifier = Modifier.size(80.dp)
        )
    }
}
@Composable
fun ModeToggleButtonNarys(state: MongeState, buttonsize: Dp) {
    val isSelected = state.mongeMode == DrawingModeMonge.NARYS
    SkikoButton(
        onClick = {
            if (!isSelected) {
                state.mongeMode = DrawingModeMonge.NARYS
                if (state.drawobjects != Mongeobjects.GETDISTANCE
                    && state.drawobjects != Mongeobjects.TRANSPARALLEL
                    && state.drawobjects != Mongeobjects.TRANSORTH) {
                    resetStavu(state, resetConstructionModifier = false)
                }
            }
            updateConstructionInfo(state)
        },
        isSelected = isSelected,
        modifier = Modifier.size(buttonsize*1.25f, buttonsize/2f),

    ) {
        Icon(
            painter = painterResource("icons/NÁRYS.png"),
            contentDescription = "NÁRYS",
            modifier = Modifier.size(buttonsize)
        )


    }
}
@Composable
fun ParallelToggleButton(state: MongeState,buttonsize: Dp) {
    val isSelected = state.constructionModifier == ConstructionModifier.PARALLEL

    SkikoButton(
        onClick = {
            if (isSelected) {
                if (state.drawobjects== Mongeobjects.TRANSPARALLEL||state.drawobjects== Mongeobjects.TRANSORTH){
                    state.drawobjects = Mongeobjects.NONE
                    resetStavu(state)
                }
                state.constructionModifier = ConstructionModifier.NONE
                // Vrať fázi do normální konstrukce průmětů; u navazujícího druhého
                // průmětu zachovej rozpracovaná pending data prvního průmětu.
                val midChain = revertLineSelectionPhaseOnModifierOff(state)
                state.selectedLineForParallelPudorys = null
                state.selectedLineForParallelPlanePudorys = null
                if (!midChain) {
                    state.pendingLinePointNarys = null
                    state.pendingLinePointPudorys = null
                    state.pendingDirectionNarys = null
                    state.pendingDirection = null
                }
                println("🚫 Rovnoběžnost zrušena.")
            } else {
                if (state.selectedLinesPudorys.isNotEmpty()) {
                    state.selectedLineForParallelPudorys = state.selectedLinesPudorys.first()
                    state.selectedLineForParallelPlanePudorys = state.selectedLinesPudorys.first()
                    println("🟦 Přímka '${state.selectedLineForParallelPudorys?.name}' automaticky nastavena jako vzor pro rovnoběžnou.")
                }
                state.constructionModifier = ConstructionModifier.PARALLEL
                if (state.drawobjects== Mongeobjects.TRANSPARALLEL||state.drawobjects== Mongeobjects.TRANSORTH){
                    state.drawobjects = Mongeobjects.TRANSPARALLEL
                }
                state.lineStartPoint3DNarys = null
                state.lineStartPoint3DPudorys = null
            }
            updateConstructionInfo(state)
        },
        modifier = Modifier
            .width(buttonsize/2f)
            .height(buttonsize/2f),
        isSelected=isSelected,
    ) {
        Icon(
            painter = painterResource("icons/parallel.svg"),
            contentDescription = "NÁRYS",
            modifier = Modifier.size(buttonsize)
        )
    }
}
@Composable
fun OrthogonalToggleButton(state: MongeState,buttonsize: Dp) {
    val isSelected = state.constructionModifier == ConstructionModifier.ORTHOGONAL
    SkikoButton(
        onClick = {
            if (isSelected) {if (state.drawobjects== Mongeobjects.TRANSPARALLEL||state.drawobjects== Mongeobjects.TRANSORTH){
                state.drawobjects = Mongeobjects.NONE
                resetStavu(state)
            }
                state.constructionModifier = ConstructionModifier.NONE
                // Vrať fázi do normální konstrukce průmětů (jinak by dispatcher pořád
                // routoval do kolmicového handleru a nutil znovu vybrat vzor směru).
                revertLineSelectionPhaseOnModifierOff(state)
                state.selectedLineForParallelPudorys = null
                state.selectedLineForParallelPlanePudorys = null

                println("🚫 Kolmost zrušena.")
            } else {
                if (state.selectedLinesPudorys.isNotEmpty()) {
                    state.selectedLineForParallelPudorys = state.selectedLinesPudorys.first()
                    state.selectedLineForParallelPlanePudorys = state.selectedLinesPudorys.first()
                    println("🟦 Přímka '${state.selectedLineForParallelPudorys?.name}' automaticky nastavena jako vzor pro kolmou.")
                }
                state.constructionModifier = ConstructionModifier.ORTHOGONAL
                if (state.drawobjects== Mongeobjects.TRANSPARALLEL||state.drawobjects== Mongeobjects.TRANSORTH){
                    state.drawobjects = Mongeobjects.TRANSORTH
                }
                state.lineStartPoint3DNarys = null
                state.lineStartPoint3DPudorys = null
            }
            updateConstructionInfo(state)
        },
        modifier = Modifier
            .width(buttonsize/2f)
            .height(buttonsize/2f),
        isSelected=isSelected,
    ) {
        Icon(
            painter = painterResource("icons/orthogonal.svg"),
            contentDescription = "NÁRYS",
            modifier = Modifier.size(80.dp)
        )
    }
}
@Composable
fun ObjectSelectionRow(
    selectedObject: Mongeobjects,
    onSelect: (Mongeobjects) -> Unit,
    state: MongeState,
    buttonsize: Dp
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
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
            Triple("Rovina", Mongeobjects.PLANE,if (colors.isDark) painterResource("icons/rovinaDark.svg") else painterResource("icons/rovina.svg")),
            Triple("Křivka",Mongeobjects.CURVE,if (colors.isDark) painterResource("icons/curvesDark.svg") else painterResource("icons/curves.svg"))

        )
        items.forEach { (label, value, icon) ->
            val enabled = isObjectEnabled(value, state)
            MongeRibbonButton(
                text = label,
                selected = selectedObject == value,
                enabled = enabled,
                onClick = {
                    selectMongeObjectTool(state, value)
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
                Mongeobjects.HYPERBOLA, Mongeobjects.CONICARC, Mongeobjects.CONICARCAS
            )

            MongeRibbonButton(
                text = "Kuželosečka",
                selected = isAnyConic || expanded,
                onClick = { expanded = true },
                enabled = (state.drawobjects == Mongeobjects.CIRCLE ||
                        state.drawobjects == Mongeobjects.ELLIPSE ||
                        state.drawobjects == Mongeobjects.PARABOLA ||
                        state.drawobjects == Mongeobjects.HYPERBOLA ||
                        state.drawobjects == Mongeobjects.CONICARC ||
                        state.drawobjects == Mongeobjects.CONICARCAS ||
                        (state.projectionPhase == "pudorys_start" || state.projectionPhase == "narys_start"))
            ) {
                Image(
                    painter = if (colors.isDark)painterResource("icons/kuzeloseckaDark.svg")else  painterResource("icons/kuzelosecka.svg"),
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
                            ProjectionType.SINGLE -> "Vytvoří kružnici pomocí středu a poloměru"
                            ProjectionType.ASSOCIATED -> "Vytvoří kružnici ve zvolené rovině pomocí středu a poloměru"
                            ProjectionType.AUXILIARY -> "Vytvoří pomocnou kružnici pomocí středu a poloměru"
                        },
                       painter = "icons/circle.svg",
                        isDark = SettingsManager.current.isDarkMode,
                        ui = ui
                    ) {
                        resetStavu(state, resetConstructionModifier = false)
                        state.drawobjects = Mongeobjects.CIRCLE
                        onSelect(Mongeobjects.CIRCLE)
                        updateConstructionInfo(state)
                        expanded = false
                    }

                    ConicMenuItem(
                        isDark = SettingsManager.current.isDarkMode,
                        title = "Elipsa",
                        subtitle = when (state.projekcnityp) {
                            ProjectionType.SINGLE -> "Vytvoří elipsu pomocí středu a sdružených průměrů"
                            ProjectionType.ASSOCIATED -> "Vytvoří elipsu ve zvolené rovině pomocí středu a poloměru"
                            ProjectionType.AUXILIARY -> "Vytvoří elipsu ve zvolené rovině pomocí středu a poloměru"
                        },
                        painter = "icons/ellipse.svg",
                        ui = ui
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
                            ProjectionType.SINGLE -> "Vytvoří parabolu pomocí vrcholu a ohniska"
                            ProjectionType.ASSOCIATED -> "Vytvoří parabolu ve zvolené rovině pomocí průmětů vrcholu a ohniska"
                            ProjectionType.AUXILIARY ->  "Vytvoří parabolu ve zvolené rovině pomocí průmětů vrcholu a ohniska"
                        },
                        painter = "icons/parabola.svg",
                        isDark = SettingsManager.current.isDarkMode,
                        ui = ui
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
                            ProjectionType.SINGLE -> "Vytvoří hyperbolu pomocí 2 asymptot a vrcholu"
                            ProjectionType.ASSOCIATED -> "Vytvoří hyperbolu ve zvolené rovině pomocí průmětů asymptot a vrcholu"
                            ProjectionType.AUXILIARY ->  "Vytvoří hyperbolu ve zvolené rovině pomocí průmětů asymptot a vrcholu"
                        },
                        painter = "icons/hyperbola.svg",
                        isDark = SettingsManager.current.isDarkMode,
                        ui = ui
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
                        title = "Úsek kuželosečky (samostatný)",
                        subtitle = "Zvýrazní část zvolené kuželosečky bez vazby na 3D objekt",
                        painter = "icons/menu_conicarc.svg",
                        isDark = SettingsManager.current.isDarkMode,
                        ui = ui
                    ) {
                        resetStavu(state, resetConstructionModifier = false)
                        state.drawobjects = Mongeobjects.CONICARC
                        onSelect(Mongeobjects.CONICARC)
                        updateConstructionInfo(state)
                        expanded = false
                    }


                    ConicMenuItem(
                        title = "Úsek kuželosečky (sdružený)",
                        subtitle = "Ořeže kuželosečku ve zvolené rovině",
                        painter = "icons/menu_conicarcas.svg",
                        isDark = SettingsManager.current.isDarkMode,
                        ui = ui
                    ) {
                        resetStavu(state, resetConstructionModifier = false)
                        state.drawobjects = Mongeobjects.CONICARCAS
                        onSelect(Mongeobjects.CONICARCAS)
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
fun RichDropdownPopupConics(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 105),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .width(380*ui.dp)
                .shadow(12*ui.dp, RoundedCornerShape(8.dp))
                .background(colors.background, RoundedCornerShape(8.dp))
                .border(1*ui.dp, colors.base.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(10*ui.dp)
                .heightIn(max = 460*ui.dp)
                .verticalScroll(scroll)
        ) {
            content()
        }
    }
}

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
fun ConicMenuItem(
    title: String,
    ui: Float,
    tint: Boolean? = false,
    subtitle: String,
    painter: String,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMongeColors.current

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg =
        if (hovered) {
            if (colors.isDark) colors.selected else colors.selected.lighter(0.8f)
        } else {
            colors.background
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84f * ui.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                onClick()
            }
            .padding(12f * ui.dp),
        horizontalArrangement = Arrangement.spacedBy(12f * ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40f * ui.dp),
            contentAlignment = Alignment.CenterStart
        ) {

                Icon(
                    painter = painterResource(painter),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    tint = colors.text
                )

        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2f * ui.dp)
        ) {
            Text(
                text = title,
                color = colors.text,
                fontSize = 18f * ui.sp
            )

            Text(
                text = subtitle,
                color = colors.text,
                fontSize = 13f * ui.sp,
                lineHeight = 16f * ui.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
            state.projectionPhase == "narys_start" ||     state.projectionPhase == "bokorys_start"

    return when (obj) {
        Mongeobjects.NONE          -> true                       // vždy povoleno

        else -> (state.drawobjects == obj) || atStart             // společná logika
    }
}

/**
 * Pořadí nástrojů v [ObjectSelectionRow] (Monge/AXO/KOTO) pro klávesové zkratky 1-6.
 * Kuželosečky (rozbalovací) záměrně nejsou součástí.
 */
val mongeObjectToolOrder = listOf(
    Mongeobjects.POINTS,
    Mongeobjects.LINES,
    Mongeobjects.SEGMENTS,
    Mongeobjects.SEGMENT_ON_LINE,
    Mongeobjects.PLANE,
    Mongeobjects.CURVE
)

/** Výběr nástroje v Monge/AXO/KOTO – sdíleno klikem v toolbaru i klávesovou zkratkou. */
fun selectMongeObjectTool(state: MongeState, value: Mongeobjects) {
    resetStavu(state, resetConstructionModifier = false)
    when (value) {
        Mongeobjects.LINES    -> state.drawobjects = Mongeobjects.LINES
        Mongeobjects.PLANE    -> {
            state.drawobjects = Mongeobjects.PLANE
            if (state.projekcnityp == ProjectionType.AUXILIARY) state.projekcnityp = ProjectionType.ASSOCIATED
        }
        Mongeobjects.SEGMENTS -> state.drawobjects = Mongeobjects.SEGMENTS
        Mongeobjects.SEGMENT_ON_LINE -> {
            state.drawobjects = Mongeobjects.SEGMENT_ON_LINE
            if (state.projekcnityp == ProjectionType.ASSOCIATED) state.projekcnityp = ProjectionType.SINGLE
        }
        Mongeobjects.CURVE -> {
            state.drawobjects = Mongeobjects.CURVE
            if (state.projekcnityp == ProjectionType.AUXILIARY) state.projekcnityp = ProjectionType.SINGLE
        }
        else                  -> state.drawobjects = value
    }
    updateConstructionInfo(state)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiftToPlaneButton(
    state: MongeState,
    buttonsize: Dp,
) {
    TooltipArea(
        tooltip = {
            Box(Modifier.background(Color.DarkGray).padding(6.dp)) {
                Text("Doplnit průmět objektu v rovině", color = Color.White)
            }
        },
        delayMillis = 500,
    ) {
        SkikoButton(
            onClick = { state.drawobjects = Mongeobjects.PLANE_LIFT },
            width = buttonsize,
            height = buttonsize,
            enabled = state.projectionPhase in listOf("pudorys_start", "narys_start"),
            isSelected = state.drawobjects == Mongeobjects.PLANE_LIFT
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource("icons/complete_object.svg"),
                    contentDescription = "Doplnit průmět objektu v rovině",
                    modifier = Modifier.size(buttonsize)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntersectionButton(
    state: MongeState,
    buttonsize: Dp,
) {
    TooltipArea(
        tooltip = {
            Box(Modifier.background(Color.DarkGray).padding(6.dp)) {
                Text("Průnik objektů", color = Color.White)
            }
        },
        delayMillis = 500,
    ) {
        SkikoButton(
            onClick = {
                state.drawobjects = Mongeobjects.INTERSECTION
                state.intersectionPicks.clear()
            },
            width = buttonsize,
            height = buttonsize,
            enabled = state.projectionPhase in listOf("pudorys_start", "narys_start"),
            isSelected = state.drawobjects == Mongeobjects.INTERSECTION
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource("icons/intersect.svg"),
                    contentDescription = "Průnik objektů",
                    modifier = Modifier.size(buttonsize)
                )
            }
        }
    }
}



@Composable
fun ProjectionModeSelector(
    selected: ProjectionType,
    onSelect: (ProjectionType) -> Unit,
    state: MongeState,
    buttonsize: Dp
) {
    val canUseSingle =
            (state.drawobjects == Mongeobjects.POINTS|| state.drawobjects == Mongeobjects.LINES||
                    state.drawobjects == Mongeobjects.PLANE|| state.drawobjects== Mongeobjects.SEGMENTS ||
                    state.drawobjects == Mongeobjects.ELLIPSE || state.drawobjects == Mongeobjects.HYPERBOLA ||
                    state.drawobjects == Mongeobjects.PARABOLA || state.drawobjects == Mongeobjects.CIRCLE ||
                    state.drawobjects == Mongeobjects.CURVE||
                    state.drawobjects== Mongeobjects.NONE||state.drawobjects == Mongeobjects.SEGMENT_ON_LINE)

    val canUseAssociated =
            state.drawobjects in listOf(Mongeobjects.POINTS,
        Mongeobjects.LINES, Mongeobjects.PLANE, Mongeobjects.SEGMENTS, Mongeobjects.ELLIPSE, Mongeobjects.HYPERBOLA,
        Mongeobjects.PARABOLA, Mongeobjects.CIRCLE, Mongeobjects.CURVE, Mongeobjects.NONE, Mongeobjects.REGULAR_POLYGON_IN_PLANE,
        Mongeobjects.CYLINDER, Mongeobjects.CONE, Mongeobjects.CONICARCAS, Mongeobjects.SOLID_OF_REVOLUTION,
        Mongeobjects.SPHERE, Mongeobjects.INTERSECTION, Mongeobjects.PLANE_LIFT, Mongeobjects.PRISM, Mongeobjects.PYRAMID)


    val canUseAuxiliary =
        state.drawobjects in listOf(Mongeobjects.POINTS, Mongeobjects.LINES, Mongeobjects.SEGMENT_ON_LINE, Mongeobjects.CIRCLE, Mongeobjects.ARC,
            Mongeobjects.AID_MIDPOINT,
            Mongeobjects.SEGMENTS,
        Mongeobjects.ERASE,
        Mongeobjects.GETANGLE,
        Mongeobjects.GETDISTANCE,
        Mongeobjects.TRANSPARALLEL,
        Mongeobjects.TRANSORTH,
        Mongeobjects.TYPEDISTANCE,
        Mongeobjects.TYPEANGLE,
        Mongeobjects.NONE)

    ProjectionSelectorShell(buttonsize = buttonsize) {
        ProjectionSelectorMainRow(
            modeContent = {
                ProjectionModeChip(
                    text = "Samostatný",
                    selected = selected == ProjectionType.SINGLE,
                    enabled = canUseSingle,
                    onClick = { onSelect(ProjectionType.SINGLE); resetStavu(state); updateConstructionInfo(state) }
                )
                ProjectionModeChip(
                    text = "Sdružené",
                    selected = selected == ProjectionType.ASSOCIATED,
                    enabled = canUseAssociated,
                    onClick = { onSelect(ProjectionType.ASSOCIATED); resetStavu(state); updateConstructionInfo(state) }
                )
            }
        ) {
            LineStyleEditor(state.currentLineStyleSettings)
        }
        ProjectionSelectorDivider()
        ProjectionSelectorSingleRow(
            mode = {
                ProjectionModeChip(
                    text = "Pomocná",
                    selected = selected == ProjectionType.AUXILIARY,
                    enabled = canUseAuxiliary,
                    onClick = { onSelect(ProjectionType.AUXILIARY); resetStavu(state); updateConstructionInfo(state) }
                )
            },
            styleContent = {
                HelpLineStyleEditor(state.currentHelpLineStyleSettings)
            }
        )
    }
}

enum class Submenu { KUZSECKY, NUHELNIK }

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun ProjectionSelectorShell(
    buttonsize: Dp,
    width: Dp = 202f * (SettingsManager.current.UIscale / 75f).dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    val ui = SettingsManager.current.UIscale / 75f
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(5f * ui.dp))
            .background(colors.base.copy(alpha = if (colors.isDark) 0.08f else 0.035f))
            .border(1.dp, colors.base.copy(alpha = if (colors.isDark) 0.22f else 0.12f), RoundedCornerShape(5f * ui.dp))
            .padding(horizontal = 4f * ui.dp, vertical = 4f * ui.dp),
        verticalArrangement = Arrangement.spacedBy(3f * ui.dp)
    ) {
        content()
    }
}

@Composable
fun ProjectionStyleOnlyShell(
    content: @Composable () -> Unit
) {
    val colors = LocalMongeColors.current
    val ui = SettingsManager.current.UIscale / 75f
    Box(
        modifier = Modifier
            .width(112f * ui.dp)
            .clip(RoundedCornerShape(5f * ui.dp))
            .background(colors.base.copy(alpha = if (colors.isDark) 0.08f else 0.035f))
            .border(1.dp, colors.base.copy(alpha = if (colors.isDark) 0.22f else 0.12f), RoundedCornerShape(5f * ui.dp))
            .padding(horizontal = 4f * ui.dp, vertical = 4f * ui.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.height(24f * ui.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    }
}

@Composable
fun ProjectionSelectorMainRow(
    modeContent: @Composable ColumnScope.() -> Unit,
    styleContent: @Composable () -> Unit
) {
    val ui = SettingsManager.current.UIscale / 75f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5f * ui.dp)
    ) {
        Column(
            modifier = Modifier.width(84f * ui.dp),
            verticalArrangement = Arrangement.spacedBy(2f * ui.dp)
        ) {
            modeContent()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44f * ui.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            styleContent()
        }
    }
}

@Composable
fun ProjectionSelectorSingleRow(
    mode: @Composable () -> Unit,
    styleContent: @Composable () -> Unit,
    modeWidth: Dp = 84f * (SettingsManager.current.UIscale / 75f).dp
) {
    val ui = SettingsManager.current.UIscale / 75f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5f * ui.dp)
    ) {
        Box(modifier = Modifier.width(modeWidth)) {
            mode()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(24f * ui.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            styleContent()
        }
    }
}

@Composable
fun ProjectionSelectorDivider() {
    Spacer(Modifier.height(1.dp))
}

@Composable
fun ProjectionModeChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalMongeColors.current
    val ui = SettingsManager.current.UIscale / 75f
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4f * ui.dp)
    val bg = when {
        !enabled -> Color.Transparent
        selected -> colors.selected.copy(alpha = if (colors.isDark) 0.26f else 0.14f)
        hovered -> colors.hover.copy(alpha = if (colors.isDark) 0.22f else 0.10f)
        else -> colors.background.copy(alpha = if (colors.isDark) 0.22f else 0.65f)
    }
    val border = when {
        selected -> colors.selected.copy(alpha = 0.72f)
        hovered -> colors.base.copy(alpha = 0.34f)
        else -> colors.base.copy(alpha = if (colors.isDark) 0.22f else 0.14f)
    }
    val textColor = when {
        !enabled -> colors.disabled.copy(alpha = 0.55f)
        selected -> colors.selected
        else -> colors.text.copy(alpha = 0.86f)
    }

    Box(
        modifier = Modifier
            .height(21f * ui.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.58f)
            .padding(horizontal = 5f * ui.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4f * ui.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5f * ui.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) colors.selected else colors.base.copy(alpha = 0.34f))
            )
            Text(
                text = text,
                color = textColor,
                fontSize = 9.5f * ui.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LineStyleEditor(settings: LineStyleSettings, ui: Float = SettingsManager.current.UIscale/75f) {
    StyleEditorStrip(
        color = settings.color,
        lineStyle = settings.style,
        strokeWidth = settings.strokeWidth,
        onColorChange = { settings.color = it },
        onStyleChange = { settings.style = it },
        onWidthChange = { settings.strokeWidth = it },
        ui = ui
    )
}

@Composable
fun HelpLineStyleEditor(settings: HelpLineStyleSettings, ui: Float = SettingsManager.current.UIscale/75f) {
    StyleEditorStrip(
        color = settings.color,
        lineStyle = settings.style,
        strokeWidth = settings.strokeWidth,
        onColorChange = { settings.color = it },
        onStyleChange = { settings.style = it },
        onWidthChange = { settings.strokeWidth = it },
        ui = ui
    )
}

@Composable
private fun StyleEditorStrip(
    color: Color,
    lineStyle: LineStyle,
    strokeWidth: Float,
    onColorChange: (Color) -> Unit,
    onStyleChange: (LineStyle) -> Unit,
    onWidthChange: (Float) -> Unit,
    ui: Float = SettingsManager.current.UIscale / 75f
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    var expanded by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    Box {
        StyleSummaryButton(
            color = color,
            lineStyle = lineStyle,
            strokeWidth = strokeWidth,
            ui = ui,
            onClick = { expanded = true }
        )

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, (30f * ui).roundToInt()),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(if (advanced) 226f * ui.dp else 204f * ui.dp)
                        .shadow(10f * ui.dp, RoundedCornerShape(6f * ui.dp))
                        .background(colors.background, RoundedCornerShape(6f * ui.dp))
                        .border(dimens.borderThin, colors.base.copy(alpha = if (colors.isDark) 0.36f else 0.18f), RoundedCornerShape(6f * ui.dp))
                        .padding(8f * ui.dp),
                    verticalArrangement = Arrangement.spacedBy(8f * ui.dp)
                ) {
                    InlineToolbarColorPalette(
                        current = color,
                        onColorChange = onColorChange,
                        ui = ui,
                        expanded = advanced
                    )
                    ToolbarAdvancedColorMixer(
                        visible = advanced,
                        color = color,
                        onColorChange = onColorChange,
                        ui = ui
                    )
                    InlineToolbarLineStylePicker(
                        current = lineStyle,
                        color = color,
                        strokeWidth = strokeWidth,
                        onStyleChange = onStyleChange,
                        ui = ui
                    )
                    ToolbarWidthStepper(
                        value = strokeWidth,
                        onValueChange = onWidthChange,
                        ui = ui
                    )
                    ToolbarPopupFooter(
                        expanded = advanced,
                        ui = ui,
                        onToggle = { advanced = !advanced }
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleSummaryButton(
    color: Color,
    lineStyle: LineStyle,
    strokeWidth: Float,
    ui: Float,
    onClick: () -> Unit
) {
    val colors = LocalMongeColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4f * ui.dp)
    Row(
        modifier = Modifier
            .width(104f * ui.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(
                if (hovered) colors.hover.copy(alpha = if (colors.isDark) 0.18f else 0.08f)
                else colors.background.copy(alpha = if (colors.isDark) 0.26f else 0.68f),
                shape
            )
            .border(1.dp, colors.base.copy(alpha = if (hovered) 0.32f else 0.14f), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6f * ui.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6f * ui.dp)
    ) {
        Box(
            modifier = Modifier
                .size(13f * ui.dp)
                .clip(RoundedCornerShape(2f * ui.dp))
                .background(color)
                .border(1.dp, colors.base.copy(alpha = 0.46f), RoundedCornerShape(2f * ui.dp))
        )
        StyleLinePreview(
            color = color,
            lineStyle = lineStyle,
            strokeWidth = strokeWidth,
            modifier = Modifier.width(36f * ui.dp)
        )
        Text(
            text = "%.1f".format(strokeWidth),
            color = colors.text.copy(alpha = 0.86f),
            fontSize = 10f * ui.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(28f * ui.dp)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.text.copy(alpha = 0.48f),
            modifier = Modifier
                .size(12f * ui.dp)
                .graphicsLayer { rotationZ = 90f }
        )
    }
}

@Composable
private fun InlineToolbarColorPalette(
    current: Color,
    onColorChange: (Color) -> Unit,
    ui: Float,
    expanded: Boolean
) {
    val colors = LocalMongeColors.current
    val palette = remember(current, SettingsManager.current.customColorPalette, expanded) {
        val base = listOf(
            current,
            Color(0f, 0f, 0f, 1f),
            Color(0.35f, 0.35f, 0.35f, 1f),
            Color(0.88f, 0.09f, 0.09f, 1f),
            Color(0.05f, 0.22f, 0.95f, 1f),
            Color(0.02f, 0.55f, 0.18f, 1f),
            Color(1f, 0.84f, 0.05f, 1f),
            Color(0.02f, 0.72f, 0.86f, 1f)
        )
        val custom = if (expanded) SettingsManager.current.customColorPalette.map { it.toColor() } else emptyList()
        (base + custom).distinctBy { it.colorKey() }.take(if (expanded) 16 else 8)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5f * ui.dp)
    ) {
        Text(
            text = "Barva",
            color = colors.text.copy(alpha = 0.58f),
            fontSize = 9f * ui.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        palette.chunked(8).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5f * ui.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { swatch ->
                    InlineToolbarColorSwatch(
                        color = swatch,
                        selected = swatch.sameRgb(current),
                        ui = ui,
                        onClick = { onColorChange(swatch) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineToolbarColorSwatch(
    color: Color,
    selected: Boolean,
    ui: Float,
    onClick: () -> Unit
) {
    val colors = LocalMongeColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(3f * ui.dp)
    Box(
        modifier = Modifier
            .size(18f * ui.dp)
            .clip(shape)
            .background(color, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = when {
                    selected -> colors.selected
                    hovered -> colors.text.copy(alpha = 0.38f)
                    else -> colors.base.copy(alpha = 0.34f)
                },
                shape = shape
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun ToolbarAdvancedColorMixer(
    visible: Boolean,
    color: Color,
    onColorChange: (Color) -> Unit,
    ui: Float
) {
    if (!visible) return

    var wheelSeedColor by remember { mutableStateOf(color) }
    LaunchedEffect(visible) {
        if (visible) {
            wheelSeedColor = color
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        HueSaturationPicker(
            ui = ui,
            wheelSize = 158f * ui.dp,
            initialColor = wheelSeedColor,
            onColorSelected = onColorChange
        )
    }
}

@Composable
private fun ToolbarPopupFooter(
    expanded: Boolean,
    ui: Float,
    onToggle: () -> Unit
) {
    val colors = LocalMongeColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4f * ui.dp)
    Box(
        modifier = Modifier
            .height(24f * ui.dp)
            .clip(shape)
            .background(
                if (hovered) colors.selected.copy(alpha = if (colors.isDark) 0.18f else 0.08f)
                else colors.base.copy(alpha = if (colors.isDark) 0.14f else 0.06f),
                shape
            )
            .border(1.dp, colors.base.copy(alpha = 0.16f), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 8f * ui.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (expanded) "Méně" else "Rozšířené",
            color = colors.text.copy(alpha = 0.82f),
            fontSize = 10f * ui.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun InlineToolbarLineStylePicker(
    current: LineStyle,
    color: Color,
    strokeWidth: Float,
    onStyleChange: (LineStyle) -> Unit,
    ui: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5f * ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineStyle.entries.forEach { style ->
            InlineToolbarLineStyleButton(
                style = style,
                selected = style == current,
                color = color,
                strokeWidth = strokeWidth,
                ui = ui,
                modifier = Modifier.weight(1f),
                onClick = { onStyleChange(style) }
            )
        }
    }
}

@Composable
private fun InlineToolbarLineStyleButton(
    style: LineStyle,
    selected: Boolean,
    color: Color,
    strokeWidth: Float,
    ui: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalMongeColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4f * ui.dp)
    Box(
        modifier = modifier
            .height(24f * ui.dp)
            .clip(shape)
            .background(
                when {
                    selected -> colors.selected.copy(alpha = if (colors.isDark) 0.24f else 0.12f)
                    hovered -> colors.hover.copy(alpha = if (colors.isDark) 0.16f else 0.07f)
                    else -> colors.base.copy(alpha = if (colors.isDark) 0.14f else 0.05f)
                },
                shape
            )
            .border(
                1.dp,
                if (selected) colors.selected.copy(alpha = 0.72f) else colors.base.copy(alpha = 0.18f),
                shape
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 5f * ui.dp),
        contentAlignment = Alignment.Center
    ) {
        StyleLinePreview(
            color = color,
            lineStyle = style,
            strokeWidth = strokeWidth,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun Color.colorKey(): String =
    "${(red * 255f).roundToInt()}-${(green * 255f).roundToInt()}-${(blue * 255f).roundToInt()}-${(alpha * 255f).roundToInt()}"

private fun Color.sameRgb(other: Color): Boolean =
    (red - other.red).let { kotlin.math.abs(it) < 0.004f } &&
            (green - other.green).let { kotlin.math.abs(it) < 0.004f } &&
            (blue - other.blue).let { kotlin.math.abs(it) < 0.004f }

// java.awt.Color.RGBtoHSB na webu není – stejný převod RGB→HSV v čistém Kotlinu.
private fun Color.toToolbarHsv(): Triple<Float, Float, Float> {
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

    val saturation = if (max == 0f) 0f else delta / max
    return Triple(hue / 360f, saturation, max)
}

@Composable
private fun ToolbarWidthStepper(
    value: Float,
    onValueChange: (Float) -> Unit,
    ui: Float,
    step: Float = 0.5f,
    min: Float = 0.5f,
    max: Float = 10f
) {
    val colors = LocalMongeColors.current
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(min, max)
        val stepsFromMin = ((clamped - min) / step).roundToInt()
        return (min + stepsFromMin * step).let { (it * 1000f).roundToInt() / 1000f }
    }

    var local by remember { mutableStateOf(value.coerceIn(min, max)) }
    LaunchedEffect(value) { local = value.coerceIn(min, max) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6f * ui.dp)
    ) {
        WidthStepButton(text = "-", ui = ui) {
            val next = snap(local - step)
            local = next
            onValueChange(next)
        }
        Box(
            modifier = Modifier
                .height(26f * ui.dp)
                .weight(1f)
                .clip(RoundedCornerShape(4f * ui.dp))
                .background(colors.base.copy(alpha = if (colors.isDark) 0.18f else 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "%.1f".format(snap(local)),
                color = colors.text,
                fontSize = 12f * ui.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        WidthStepButton(text = "+", ui = ui) {
            val next = snap(local + step)
            local = next
            onValueChange(next)
        }
    }
}

@Composable
private fun WidthStepButton(
    text: String,
    ui: Float,
    onClick: () -> Unit
) {
    val colors = LocalMongeColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4f * ui.dp)
    Box(
        modifier = Modifier
            .size(width = 32f * ui.dp, height = 26f * ui.dp)
            .clip(shape)
            .background(
                if (hovered) colors.selected.copy(alpha = if (colors.isDark) 0.22f else 0.10f)
                else colors.base.copy(alpha = if (colors.isDark) 0.18f else 0.07f),
                shape
            )
            .border(1.dp, colors.base.copy(alpha = 0.20f), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.text,
            fontSize = 14f * ui.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun StyleLinePreview(
    color: Color,
    lineStyle: LineStyle,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
    ui: Float = SettingsManager.current.UIscale / 75f
) {
    val isDark =  LocalMongeColors.current.isDark
    Canvas(modifier = modifier.height(16f * ui.dp)) {
        val y = size.height / 2f
        val start = 1f * ui
        val end = size.width - 1f * ui
        val width = strokeWidth.coerceIn(1f, 6f) * ui

        fun drawSegment(a: Float, b: Float) {
            drawLine(
                color = if (color==Color.Black && isDark){
                    Color.White
                } else color,
                start = Offset(a.coerceAtMost(end), y),
                end = Offset(b.coerceAtMost(end), y),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }

        when (lineStyle) {
            LineStyle.Solid -> drawSegment(start, end)
            LineStyle.Dashed -> {
                var x = start
                while (x < end) {
                    drawSegment(x, x + 10f * ui)
                    x += 16f * ui
                }
            }
            LineStyle.Dotted -> {
                var x = start
                while (x < end) {
                    drawCircle(color = if (color==Color.Black && isDark){
                        Color.White
                    } else color, radius = width * 0.55f, center = Offset(x, y))
                    x += 7f * ui
                }
            }
            LineStyle.DashDot -> {
                var x = start
                while (x < end) {
                    drawSegment(x, x + 10f * ui)
                    x += 14f * ui
                    drawCircle(color = if (color==Color.Black && isDark){
                        Color.White
                    } else color, radius = width * 0.50f, center = Offset(x, y))
                    x += 8f * ui
                }
            }
        }
    }
}
@Composable
fun Modifier.onHoverEnter(
    onEnter: () -> Unit,
    onExit: (() -> Unit)? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> onEnter()
                is HoverInteraction.Exit -> onExit?.invoke()
            }
        }
    }

    return this.hoverable(interactionSource = interactionSource)
}
@Composable
fun fontFromDp(dp: Dp, factor: Float = 0.15f): TextUnit {
    val density = LocalDensity.current
    return with(density) {
        (dp.toPx() * factor).toSp()
    }
}

@Composable
fun ToolSet(state: MongeState, buttonsize: Dp) {
    val isPlane = state.projectionMode == ProjectionMode.PLANE
    val ui = SettingsManager.current.UIscale/75f
    Row(
        horizontalArrangement = Arrangement.spacedBy(8*ui.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8*ui.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8*ui.dp)) {
                RepeatButton(state, buttonsize)
                if (!isPlane) {
                    CropButton(state, buttonsize)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8*ui.dp)) {
                Pojmenovat(state, buttonsize)
                if (!isPlane) {
                    ShowHelpConstructionButton(state, buttonsize)
                }
            }
        }

        AxisOrientationControls(state, buttonsize)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolToggleIconButton(
    tooltipText: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    painterPath: String,
    buttonSize: Dp,
    enabled: Boolean = true,
    buttonHeight: Dp? = null,
) {
    // tlačítko máš 0.5 * buttonsize => ikonu uděláme cca 60–70 % z tlačítka
    val actualButton = buttonSize * 0.5f
    val actualHeight = buttonHeight ?: actualButton
    val iconSize = actualButton * 0.68f

    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(tooltipText, color = Color.White)
            }
        },
        delayMillis = 450
    ) {
        SkikoButton(
            onClick = onClick,
            width = actualButton,
            height = actualHeight,
            isSelected = isSelected,
            enabled = enabled
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(painterPath),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
fun RepeatButton(state: MongeState, buttonsize: Dp) {
    ToolToggleIconButton(
        tooltipText = "Opakovat poslední nástroje (R)",
        isSelected = !state.repeatCons.value, // nechávám tvoji logiku
        onClick = { state.repeatCons.value = !state.repeatCons.value },
        painterPath = if (!state.repeatCons.value) "icons/refresh.svg" else "icons/refresh-off.svg" ,
        buttonSize = buttonsize
    )
}


@Composable
fun CropButton(state: MongeState, buttonsize: Dp) {
    val enabled = if (state.projectionMode == ProjectionMode.MONGE) true else false
    ToolToggleIconButton(
        tooltipText = "Ořezávání osou x₁₂",
        isSelected = state.defaultClipAboveX12Narys && state.defaultClipBelowX12Pudorys,
        onClick = {
            val newVal = !(state.defaultClipAboveX12Narys && state.defaultClipBelowX12Pudorys)
            state.defaultClipAboveX12Narys = newVal
            state.defaultClipBelowX12Pudorys = newVal
            state.triggerRedraw++
        },
        painterPath = "icons/orez.png",
        buttonSize = buttonsize,
        enabled = enabled
    )
}

@Composable
fun ShowHelpConstructionButton(state: MongeState, buttonsize: Dp) {
    val isChecked = state.showConstruction
    val enabled = state.projectionMode in listOf(ProjectionMode.MONGE, ProjectionMode.KOTO, ProjectionMode.AXO)

    ToolToggleIconButton(
        tooltipText = if (isChecked.value) "Skrýt pomocné konstrukce" else "Zobrazit pomocné konstrukce",
        isSelected = !isChecked.value, // opět zachovávám tvůj význam (selected = skrývá)
        onClick = { isChecked.value = !isChecked.value },
        painterPath = if (isChecked.value)"icons/eye.svg" else "icons/eye-off.svg",
        buttonSize = buttonsize,
        enabled = enabled
    )
}

@Composable
fun Pojmenovat(state: MongeState, buttonsize: Dp) {
    ToolToggleIconButton(
        tooltipText = "Pojmenovávat objekty (N)",
        isSelected = !state.skipnaming,
        onClick = { state.skipnaming = !state.skipnaming },
        painterPath = "icons/pojmenovat.svg",
        buttonSize = buttonsize
    )
}

@Composable
private fun RichDropdownPopupQuadrics(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 105),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .width(380*ui.dp)
                .shadow(12*ui.dp, RoundedCornerShape(8.dp))
                .background(colors.background, RoundedCornerShape(8.dp))
                .border(1*ui.dp, colors.base.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(10*ui.dp)
                .heightIn(max = 460*ui.dp)
                .verticalScroll(scroll)
        ) {
            content()
        }
    }
}

/**
 * Nabídka kvadrik. Oproti desktopu bez axonometrických větví – web má jen
 * Mongeovo promítání a rýsování v rovině.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun Kvadriky(state: MongeState, buttonsize: Dp) {
    val colors = LocalMongeColors.current
    var mainPopupVisible by remember { mutableStateOf(false) }
    val ui = SettingsManager.current.UIscale/75f
    fun runAndClose(action: () -> Unit) {
        action()
        mainPopupVisible = false
    }

    Box {
        TooltipArea(
            tooltip = {
                Box(Modifier.background(Color.DarkGray).padding(6.dp)) {
                    Text("Kvadriky", color = Color.White)
                }
            },
            delayMillis = 500,
        ) {
            SkikoButton(
                onClick = { mainPopupVisible = !mainPopupVisible },
                isSelected = mainPopupVisible,
                width = buttonsize,
                height = buttonsize,
                enabled = state.projectionPhase in listOf("pudorys_start","narys_start"),
            ) {
                Icon(
                    painter = painterResource("icons/quadrics.svg"),
                    contentDescription = "Kvadriky"
                )
            }
        }

        if (mainPopupVisible) {
            RichDropdownPopupQuadrics(
                onDismiss = { mainPopupVisible = false }
            ) {
                SectionHeader("Kvadriky")
                Divider(color = colors.base.copy(alpha = 0.25f))
                // ───────── Singulární ─────────
                SectionHeader("Singulární")

                ConicMenuItem(
                    title = "Kužel",
                    subtitle = "Podstavná elipsa + vrchol",
                    painter = "icons/cone.svg",
                    onClick = {
                        runAndClose {
                            state.drawobjects = Mongeobjects.CONE
                            state.projekcnityp = ProjectionType.ASSOCIATED
                        }
                    },
                    isDark = SettingsManager.current.isDarkMode,
                    ui = ui
                )

                ConicMenuItem(
                    title = "Válec",
                    subtitle = "Podstavná elipsa, směr válce, rovina druhé podstavy",
                    painter = "icons/cylinder.svg",
                    onClick = {
                        runAndClose {
                            state.drawobjects = Mongeobjects.CYLINDER
                            state.cylinderPhase = CylinderPhase.PICK_CONIC
                            state.projekcnityp = ProjectionType.ASSOCIATED
                        }
                    },
                    isDark = SettingsManager.current.isDarkMode,
                    ui = ui
                )

                ConicMenuItem(
                    title = "Kolmý válec",
                    subtitle = "Podstavná elipsa + střed horní podstavy",
                    painter = "icons/cylinder.svg",
                    onClick = {
                        runAndClose {
                            state.drawobjects = Mongeobjects.CYLINDER
                            state.cylinderPhase = CylinderPhase.PICK_CONIC_PERP
                            state.projekcnityp = ProjectionType.ASSOCIATED
                        }
                    },
                    isDark = SettingsManager.current.isDarkMode,
                    ui = ui
                )

                Spacer(Modifier.height(6.dp))
                Divider(color = colors.base.copy(alpha = 0.25f))
                Spacer(Modifier.height(6.dp))

                // ───────── Regulární ─────────
                SectionHeader("Regulární")

                ConicMenuItem(
                    title = "Kulová plocha",
                    subtitle = "Určená pomocí středu a poloměru",
                    painter = "icons/sphere.svg",
                    onClick = {
                        runAndClose {
                            state.drawobjects = Mongeobjects.SPHERE
                            state.projekcnityp = ProjectionType.ASSOCIATED
                        }
                    },
                    isDark = SettingsManager.current.isDarkMode,
                    ui = ui
                )
            }
        }
    }
}

/**
 * Nabídka těles. Oproti desktopu bez axonometrických větví.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun Telesa(state: MongeState, buttonsize: Dp) {
    val colors = LocalMongeColors.current
    var mainPopupVisible by remember { mutableStateOf(false) }
    val ui = SettingsManager.current.UIscale / 75f
    fun runAndClose(action: () -> Unit) {
        action()
        mainPopupVisible = false
    }

    Box {
        TooltipArea(
            tooltip = {
                Box(Modifier.background(Color.DarkGray).padding(6.dp)) {
                    Text("Tělesa", color = Color.White)
                }
            },
            delayMillis = 500,
        ) {
            SkikoButton(
                onClick = { mainPopupVisible = !mainPopupVisible },
                isSelected = mainPopupVisible,
                width = buttonsize,
                height = buttonsize,
                enabled = state.projectionPhase in listOf("pudorys_start", "narys_start"),
            ) {
                Icon(
                    painter = painterResource("icons/hexagonal-prism-plus.svg"),
                    contentDescription = "Tělesa"
                )
            }
        }

        if (mainPopupVisible) {
            RichDropdownPopupQuadrics(
                onDismiss = { mainPopupVisible = false }
            ) {
                SectionHeader("Tělesa")
                Divider(color = colors.base.copy(alpha = 0.25f))

                ConicMenuItem(
                    title = "Jehlan",
                    subtitle = "n-úhelníková podstava + vrchol",
                    painter = "icons/brand-prisma.svg",
                    onClick = {
                        runAndClose {
                            state.drawobjects = Mongeobjects.PYRAMID
                            state.projekcnityp = ProjectionType.ASSOCIATED
                        }
                    },
                    isDark = SettingsManager.current.isDarkMode,
                    ui = ui
                )

                ConicMenuItem(
                    title = "Kolmý hranol",
                    subtitle = "n-úhelníková podstava + výška",
                    painter = "icons/rectangular-prism.svg",
                    onClick = {
                        runAndClose {
                            state.drawobjects = Mongeobjects.PRISM
                            state.projekcnityp = ProjectionType.ASSOCIATED
                            state.pendingPrismPolygonId = null
                            state.pendingPrismNormal = null
                            state.pendingPrismBaseCenter = null
                        }
                    },
                    isDark = SettingsManager.current.isDarkMode,
                    ui = ui
                )

                ConicMenuItem(
                    title = "Pravidelný mnohostěn",
                    subtitle = "Krychle nebo čtyřstěn",
                    painter = "icons/cube.svg",
                    onClick = {
                        runAndClose {
                            state.drawobjects = Mongeobjects.PLATONIC_SOLID
                            state.projekcnityp = ProjectionType.ASSOCIATED
                            state.pendingPlatonicPolygonId = null
                            state.platonicFlip = false
                        }
                    },
                    isDark = SettingsManager.current.isDarkMode,
                    ui = ui
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuledSurfaceButton(
    state: MongeState,
    buttonsize: Dp,
) {
    TooltipArea(
        tooltip = {
            Box(Modifier.background(Color.DarkGray).padding(6.dp)) {
                Text("Přímková plocha", color = Color.White)
            }
        },
        delayMillis = 500,
    ) {
        SkikoButton(
            onClick = { startRuledSurfaceTool(state) },
            width = buttonsize,
            height = buttonsize,
            enabled = state.projectionMode == ProjectionMode.MONGE &&
                    (state.drawobjects == Mongeobjects.RULED_SURFACE ||
                            state.projectionPhase in listOf("pudorys_start", "narys_start")),
            isSelected = state.drawobjects == Mongeobjects.RULED_SURFACE,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource("icons/konoid.svg"),
                    contentDescription = "Přímková plocha",
                    modifier = Modifier.size(buttonsize),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SolidOfRevolution(
    state: MongeState,
    buttonsize: Dp,
) {
    val iconSize = buttonsize
    TooltipArea(
        tooltip = {
            Box(Modifier.background(Color.DarkGray).padding(6.dp)) {
                Text("Rotační těleso", color = Color.White)
            }
        },
        delayMillis = 500,
    ) {
        Box {
            SkikoButton(
                onClick = {
                    state.drawobjects = Mongeobjects.SOLID_OF_REVOLUTION
                    if (state.mongeMode == DrawingModeMonge.NARYS) startSolidOfRevolutionTool(state)
                    else startSolidOfRevolutionToolPudorys(state)
                },
                enabled = state.projectionPhase in listOf("pudorys_start", "narys_start"),
                width = buttonsize,
                height = buttonsize,
                isSelected = state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource("icons/rot.svg"),
                        contentDescription = "Rotační těleso",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
