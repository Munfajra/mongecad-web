package ui.mongeui.toolbar


import androidx.compose.foundation.*
import ui.components.TooltipArea
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import draw.mongescreen.labels.screenToLogical
import model.*
import model.axo.AxoMode
import serialization.SettingsManager
import state.MongeState
import ui.components.MongeVerticalToolButton
import ui.resetStavu
import ui.theme.LocalMongeDimens

// 1️⃣ – popis jednoho tlačítka
data class ToolbarAction(
    val obj: Mongeobjects,
    val hovertext: String,
    val iconPath: String,
    val iconSize: Dp = 80.dp,
    val isEnabled: (MongeState) -> Boolean = { true },
    val extraAction: (MongeState) -> Unit = {}          // spustí se PO standardním kliknutí
)

private fun axoToolSetup(state: MongeState){
    if (state.projectionMode == ProjectionMode.AXO){
        state.axoMode = AxoMode.NORMAL_2D
        state.projekcnityp = ProjectionType.AUXILIARY
    }
}
// 2️⃣ – seznam všech tlačítek v požadovaném pořadí
// ---------- 2⃣ seznam tlačítek ----------
private val actions = listOf(

    // 1) Cursor – BEZ extra akce
    ToolbarAction(
        obj        = Mongeobjects.NONE,
        iconPath   = "icons/cursor.svg",
        iconSize   = 40.dp,
        hovertext = "Výběr",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.NONE, s) }
    ),

    // 2) Erase – BEZ extra akce
    ToolbarAction(
        obj        = Mongeobjects.ERASE,
        iconPath   = "icons/eraser.svg",
        iconSize   = 40.dp,
        hovertext = "Smazat objekty",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.ERASE, s) },
        extraAction = { it.projekcnityp = ProjectionType.AUXILIARY
            axoToolSetup(it)}
    ),
    ToolbarAction(
        obj = Mongeobjects.AID_MIDPOINT,
        iconPath = "icons/midpoint.png",
        hovertext = "Střed",
        isEnabled = { s -> isObjectEnabled(Mongeobjects.AID_MIDPOINT, s) },
        extraAction = { it.projekcnityp = ProjectionType.AUXILIARY
        axoToolSetup(it)}
    ),

    // 3) Všechno ostatní – extraAction nastaví AUXILIARY
    ToolbarAction(
        obj        = Mongeobjects.ARC,
        iconPath   = "icons/spline.svg",
        hovertext = "Oblouk",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.ARC, s) },
        extraAction = { it.projekcnityp = ProjectionType.AUXILIARY
            axoToolSetup(it)}
    ),

    ToolbarAction(
        obj        = Mongeobjects.GETDISTANCE,
        iconPath   = "icons/ruler-measure.svg",
        hovertext = "Přenést vzdálenost",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.GETDISTANCE, s) },
        extraAction = { it.projekcnityp = ProjectionType.AUXILIARY
            axoToolSetup(it)}
    ),

    ToolbarAction(
        obj        = Mongeobjects.TYPEDISTANCE,
        iconPath   = "icons/ruler.svg",
        hovertext = "Zadat vzdálenost",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.TYPEDISTANCE, s) },
        extraAction = {
            axoToolSetup(it)
            it.projekcnityp = ProjectionType.AUXILIARY
            it.showTypeDistanceDialog = true        // dialog navíc
        }
    ),

    ToolbarAction(
        obj        = Mongeobjects.GETANGLE,
        iconPath   = "icons/spline-pointer.svg",
        hovertext = "Přenést úhel",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.GETANGLE, s) },
        extraAction = { it.projekcnityp = ProjectionType.AUXILIARY
            axoToolSetup(it)}
    ),

    ToolbarAction(
        obj        = Mongeobjects.TYPEANGLE,
        iconPath   = "icons/typeangle.svg",
        hovertext = "Zadat úhel",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.TYPEANGLE, s) },
        extraAction = {
            axoToolSetup(it)
            it.projekcnityp = ProjectionType.AUXILIARY
            it.showTypeAngleDialog = true           // dialog navíc
        }
    ),

    ToolbarAction(
        obj        = Mongeobjects.TRANSPARALLEL,
        iconPath   = "icons/transparallel.svg",
        hovertext = "Přenést po rovnoběžce",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.TRANSPARALLEL, s) },
        extraAction = {
            axoToolSetup(it)
            it.projekcnityp      = ProjectionType.AUXILIARY
            it.constructionModifier = ConstructionModifier.PARALLEL
        }
    ),

    ToolbarAction(
        obj        = Mongeobjects.TRANSORTH,
        iconPath   = "icons/transorthl.svg",
        hovertext = "Přenést po kolmici",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.TRANSORTH, s) },
        extraAction = {
            axoToolSetup(it)
            it.projekcnityp     = ProjectionType.AUXILIARY
            it.constructionModifier = ConstructionModifier.ORTHOGONAL
        }
    )
)


// 3️⃣ – samotná řada
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionToolbar(state: MongeState) {
    val ui = SettingsManager.current.UIscale/75f
    val dimens = LocalMongeDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.xs)) {
        actions.forEach { action ->
            val selected = state.drawobjects == action.obj
            val enabled  = action.isEnabled(state)
            TooltipArea(
                tooltip = {
                    Box(Modifier.background(Color.DarkGray).padding(6f*ui.dp)) {
                        Text(action.hovertext, color = Color.White)
                    }
                },
                delayMillis = 500,
            ){
            MongeVerticalToolButton(
                enabled    = enabled,
                selected = selected,
                onClick = {
                    if (!enabled || selected) return@MongeVerticalToolButton

                    // standardní obsluha
                    state.drawobjects = action.obj
                    resetStavu(state, resetConstructionModifier = false)

                    // dodatečné kroky (AUXILIARY, dialogy, modifikátory…)
                    action.extraAction(state)
                    updateConstructionInfo(state)
                }
            ) {
                DarkImageLightIcon(
                    darkPainter = painterResource(action.iconPath),
                    contentDescription = action.obj.name,
                    modifier = Modifier.size(dimens.iconMd),
                    isDark = SettingsManager.current.isDarkMode,

                )
            }
        }
        }
    }
}
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PaperPreviewButton(state: MongeState, buttonSize: Dp) {
    val colors = LocalMongeColors.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        SkikoButton(
            onClick = { expanded = !expanded },
            isSelected = state.showPaperPreview || expanded,
            modifier = Modifier
                .width(buttonSize)
                .height(buttonSize)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.Description ,
                    contentDescription = "Formát papíru",
                    modifier = Modifier.size(buttonSize*0.6f),
                )
                Text(state.paperFormat.label, color = colors.text)
            }
        }
        if (expanded) {
            RichPaperPopup(
                onDismiss = { expanded = false },
                ui = SettingsManager.current.UIscale/75f
            ) {
                PaperMenuContent(
                    state = state,
                    onClose = { expanded = false },
                    ui= SettingsManager.current.UIscale/75f
                )
            }
        }
    }
}
@Composable
fun RichPaperPopup(
    onDismiss: () -> Unit,
    ui: Float,
    content: @Composable ColumnScope.() -> Unit,

) {
    val colors = LocalMongeColors.current
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, (105f*ui).toInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .width(320f*ui.dp)
                .shadow(12.dp, RoundedCornerShape(10.dp))
                .background(colors.background, RoundedCornerShape(10.dp))
                .border(1.dp, colors.base.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            content()
        }
    }
}
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PaperMenuContent(
    state: MongeState,
    onClose: () -> Unit,
    ui: Float,
) {
    val colors = LocalMongeColors.current

    // Header
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4f*ui.dp, vertical = 2f*ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Papír", color = colors.text, fontSize = 16f*ui.sp, modifier = Modifier.weight(1f))
        SkikoButton(onClick = onClose, width = 34f*ui.dp, height = 28f*ui.dp) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16f*ui.dp))
        }
    }

    Spacer(Modifier.height(8.dp))

    // KARTA: Náhled + ukotvení
    PaperCard {
        // Náhled papíru (zap/vyp) – necháme to jako jeden řádek s jedním tlačítkem
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10f*ui.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("Náhled papíru", color = colors.text, fontSize = 14f*ui.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.showPaperPreview) "Zobrazeno na plátně" else "Skryto",
                    color = colors.text.copy(alpha = 0.75f),
                    fontSize = 12f*ui.sp
                )
            }
            SkikoButton(
                onClick = { state.showPaperPreview = !state.showPaperPreview },
                isSelected = state.showPaperPreview,
                width = 92f*ui.dp,
                height = 32f*ui.dp
            ) { Text(if (state.showPaperPreview) "Zapnuto" else "Vypnuto",fontSize = 12f*ui.sp) }
        }

        Spacer(Modifier.height(10f*ui.dp))

        // Ukotvení – jedno tlačítko jako toggle (místo Ukotvit + Volně)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10f*ui.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ukotvení", color = colors.text, fontSize = 14f*ui.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.paperAnchorPinned) "Ukotveno k aktuální pozici" else "Volně (sleduje střed)",
                    color = colors.text.copy(alpha = 0.75f),
                    fontSize = 12f*ui.sp
                )
            }
            SkikoButton(
                onClick = {
                    if (!state.paperAnchorPinned) { when (state.projectionMode) {
                        ProjectionMode.AXO -> pinPaperAnchorAxo(state)
                        else -> pinPaperToCurrentPosition(state)
                    }}
                    else unpinPaper(state)
                    state.showPaperPreview = true
                },
                isSelected = state.paperAnchorPinned,
                width = 92f*ui.dp,
                height = 32f*ui.dp
            ) { Text(if (state.paperAnchorPinned) "Ukotveno" else "Volně",fontSize = 12f*ui.sp) }
        }
    }

    Spacer(Modifier.height(10f*ui.dp))

    // KARTA: orientace + formát
    PaperCard {

        Text("Orientace", color = colors.text, fontSize = 13f*ui.sp, modifier = Modifier.padding(horizontal = 2f*ui.dp))

        Spacer(Modifier.height(6f*ui.dp))

        TogglePillRow(
            options = listOf("Na výšku", "Na šířku"),
            selectedIndex = if (state.paperPortrait) 0 else 1,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
            ui=ui,
        ) { idx ->
            state.paperPortrait = (idx == 0)
            state.showPaperPreview = true
        }

        Spacer(Modifier.height(12f*ui.dp))

        Text("Formát", color = colors.text, fontSize = 13f*ui.sp, modifier = Modifier.padding(horizontal = 2f*ui.dp))
        Spacer(Modifier.height(6f*ui.dp))

        // 👇 Kompaktní grid 2 sloupce = méně “seznam tlačítek”
        val fmts = PaperFormat.entries
        val rows = (fmts.size + 1) / 2

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val i1 = r * 2
                    val i2 = i1 + 1

                    PaperFormatChip(
                        fmt = fmts[i1],
                        ui=ui,
                        selected = state.paperFormat == fmts[i1],
                        onClick = {
                            state.paperFormat = fmts[i1]
                            state.showPaperPreview = true
                        },
                        modifier = Modifier.weight(1f)
                    )

                    if (i2 < fmts.size) {
                        PaperFormatChip(
                            fmt = fmts[i2],
                            ui=ui,
                            selected = state.paperFormat == fmts[i2],
                            onClick = {
                                state.paperFormat = fmts[i2]
                                state.showPaperPreview = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PaperFormatChip(
    fmt: PaperFormat,
    ui: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMongeColors.current
    var hovered by remember { mutableStateOf(false) }

    val bg = when {
        selected -> colors.selected.copy(alpha = 0.14f)
        hovered  -> colors.hover.copy(alpha = 0.08f)
        else     -> colors.background.copy(alpha = 0.0f)
    }

    Row(
        modifier = modifier
            .heightIn(min = 40f*ui.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                1.dp,
                (if (selected) colors.selected else colors.base).copy(alpha = if (selected) 0.55f else 0.25f),
                RoundedCornerShape(10.dp)
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 10f*ui.dp, vertical = 8f*ui.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10f*ui.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(fmt.label, color = colors.text, fontSize = 12f*ui.sp, fontWeight = FontWeight.SemiBold)
            Text("${fmt.wMm.toInt()}×${fmt.hMm.toInt()} mm", color = colors.text.copy(alpha = 0.75f), fontSize = 10*ui.sp)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.selected)
    }
}

@Composable
private fun PaperCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalMongeColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.base.copy(alpha = 0.06f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TogglePillRow(
    options: List<String>,
    selectedIndex: Int,
    colors: MongeColorsState,
    modifier: Modifier = Modifier,
    ui: Float,
    height: Dp = 34f*ui.dp,
    onSelect: (Int) -> Unit,

) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.base.copy(alpha = 0.08f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            PillToggleItem(
                text = label,
                selected = index == selectedIndex,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(index) },
                ui = ui
            )
        }
    }
}
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PillToggleItem(
    text: String,
    selected: Boolean,
    colors: MongeColorsState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    ui: Float
) {
    var hovered by remember { mutableStateOf(false) }

    val bg = when {
        selected -> colors.selected.copy(alpha = 0.18f)
        hovered  -> colors.hover.copy(alpha = 0.10f)
        else     -> Color.Transparent
    }

    val textColor = when {
        selected -> colors.selected
        else     -> colors.text
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13f*ui.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
@Suppress("UNUSED_PARAMETER")
fun DarkImageLightIcon(
    isDark: Boolean,
    darkPainter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tintLight: Color = LocalContentColor.current
) {
    Image(
        painter = darkPainter,
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tintLight)
    )
}
fun currentPaperAnchorLogical(state: MongeState): Offset {
    if (state.paperAnchorPinned) {
        val basis = state.basis
        if (basis != null && state.projectionMode == ProjectionMode.AXO) {
            return state.paperAnchorFromOrigin
        }

        return state.paperAnchorLogical
    }

    val screenCenter = Offset(
        state.canvasWidth / 2f,
        state.canvasHeight / 2f
    )

    val basis = state.basis
    if (basis != null && state.projectionMode == ProjectionMode.AXO) {
        return ((screenCenter - state.canvasOffset) / state.scale) - basis.origin
    }

    return screenToLogical(screenCenter, state)
}
fun pinPaperAnchorAxo(state: MongeState) {
    if (state.basis == null) return

    state.paperAnchorFromOrigin = currentPaperAnchorLogical(state)
    state.paperAnchorPinned = true
}
fun pinPaperToCurrentPosition(state: MongeState) {
    state.paperAnchorLogical = currentPaperAnchorLogical(state)
    state.paperAnchorPinned = true
}

fun unpinPaper(state: MongeState) {
    state.paperAnchorPinned = false
}
