package ui.planeUI.toolbar

import ui.components.TooltipArea
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import ui.resources.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import model.ConstructionModifier
import model.LocalMongeColors
import model.Mongeobjects
import serialization.SettingsManager
import state.MongeState
import ui.components.MongeVerticalToolButton
import ui.mongeui.toolbar.DarkImageLightIcon
import ui.mongeui.toolbar.ToolbarAction
import ui.mongeui.toolbar.isObjectEnabled
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import ui.theme.LocalMongeDimens

@Composable
fun PlaneLeftPanelToolbar(state: MongeState){
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .padding(top = dimens.xs, start = dimens.xs, end = dimens.xs)
            .clip(RoundedCornerShape(dimens.radiusMd))
            .background(colors.base.copy(alpha = if (colors.isDark) 0.08f else 0.045f))
            .padding(dimens.xs)
            .verticalScroll(scrollState)
    ) {
        PlaneActionToolbar(state)
    }
}
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
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.ERASE, s) }
    ),
    ToolbarAction(
        obj = Mongeobjects.AID_MIDPOINT,
        iconPath = "icons/midpoint.png",
        hovertext = "Střed",
        isEnabled = { s -> isObjectEnabled(Mongeobjects.AID_MIDPOINT, s) },
    ),

    ToolbarAction(
        obj        = Mongeobjects.ARC,
        iconPath   = "icons/spline.svg",
        hovertext = "Oblouk",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.ARC, s) },
    ),

    ToolbarAction(
        obj        = Mongeobjects.GETDISTANCE,
        iconPath   = "icons/ruler-measure.svg",
        hovertext = "Přenést vzdálenost",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.GETDISTANCE, s) },
    ),

    ToolbarAction(
        obj        = Mongeobjects.TYPEDISTANCE,
        iconPath   = "icons/ruler.svg",
        hovertext = "Zadat vzdálenost",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.TYPEDISTANCE, s) },
        extraAction = {
            it.showTypeDistanceDialog = true        // dialog navíc
        }
    ),

    ToolbarAction(
        obj        = Mongeobjects.GETANGLE,
        iconPath   = "icons/spline-pointer.svg",
        hovertext = "Přenést úhel",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.GETANGLE, s) },
    ),

    ToolbarAction(
        obj        = Mongeobjects.TYPEANGLE,
        iconPath   = "icons/typeangle.svg",
        hovertext = "Zadat úhel",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.TYPEANGLE, s) },
        extraAction = {
            it.showTypeAngleDialog = true           // dialog navíc
        }
    ),

    ToolbarAction(
        obj        = Mongeobjects.TRANSPARALLEL,
        iconPath   = "icons/transparallel.svg",
        hovertext = "Přenést po rovnoběžce",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.TRANSPARALLEL, s) },
        extraAction = {
            it.constructionModifier = ConstructionModifier.PARALLEL
        }
    ),

    ToolbarAction(
        obj        = Mongeobjects.TRANSORTH,
        iconPath   = "icons/transorthl.svg",
        hovertext = "Přenést po kolmici",
        isEnabled  = { s -> isObjectEnabled(Mongeobjects.TRANSORTH, s) },
        extraAction = {
            it.constructionModifier = ConstructionModifier.ORTHOGONAL
        }
    )
)


// 3️⃣ – samotná řada
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaneActionToolbar(state: MongeState) {
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
