package ui.mongeui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import model.LocalMongeColors
import serialization.SettingsManager
import serialization.openMongeFile
import serialization.saveMongeFile
import state.MongeState
import ui.components.TooltipArea
import ui.isAppFullscreen
import ui.theme.LocalMongeDimens
import ui.toggleAppFullscreen

/**
 * Společné webové menu pro souborové a aplikační akce. Konstrukční toolbar tak
 * zůstává vyhrazený kreslení a stejné menu se používá v MONGE i PLANE.
 */
@Composable
fun WebAppMenuButton(
    state: MongeState,
    buttonsize: Dp,
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(isAppFullscreen()) }

    fun closeAndRun(action: () -> Unit) {
        expanded = false
        action()
    }

    Box {
        TooltipArea(
            tooltip = {
                Box(Modifier.background(Color.DarkGray).padding(dimens.sm)) {
                    Text("Soubor a aplikace", color = Color.White)
                }
            },
            delayMillis = 450
        ) {
            SkikoButton(
                onClick = { expanded = !expanded },
                width = buttonsize,
                height = buttonsize,
                isSelected = expanded
            ) {
                Icon(
                    Icons.Outlined.Menu,
                    contentDescription = "Otevřít menu",
                    tint = colors.buttonColor,
                    modifier = Modifier.size(dimens.iconLg)
                )
            }
        }

        if (expanded) {
            Popup(
                popupPositionProvider = WebMenuPositionProvider(gapPx = (6 * ui).toInt()),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(330 * ui.dp)
                        .heightIn(max = 600 * ui.dp)
                        .shadow(16 * ui.dp, RoundedCornerShape(dimens.radiusMd))
                        .background(colors.background, RoundedCornerShape(dimens.radiusMd))
                        .border(
                            dimens.borderThin,
                            colors.base.copy(alpha = if (colors.isDark) 0.42f else 0.24f),
                            RoundedCornerShape(dimens.radiusMd)
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(dimens.sm)
                ) {
                    Text(
                        text = "MongeCAD Web",
                        color = colors.text,
                        fontSize = 15 * ui.sp,
                        modifier = Modifier.padding(horizontal = dimens.sm, vertical = dimens.xs)
                    )
                    Text(
                        text = "Soubor a nastavení aplikace",
                        color = colors.text.copy(alpha = 0.55f),
                        fontSize = 11 * ui.sp,
                        modifier = Modifier.padding(horizontal = dimens.sm)
                    )

                    WebMenuDivider()
                    WebMenuSectionLabel("SOUBOR")
                    WebMenuItem(
                        icon = Icons.Outlined.FolderOpen,
                        title = "Otevřít výkres",
                        subtitle = "Načíst kompatibilní soubor .monge",
                        onClick = {
                            expanded = false
                            scope.launch {
                                openMongeFile()?.let { loaded -> adoptLoadedDrawing(state, loaded) }
                            }
                        }
                    )
                    WebMenuItem(
                        icon = Icons.Outlined.Save,
                        title = "Uložit výkres",
                        subtitle = "Stáhnout výkres jako soubor .monge",
                        onClick = { closeAndRun { saveMongeFile(state) } }
                    )
                    WebMenuItem(
                        icon = Icons.Outlined.Image,
                        title = "Exportovat obrázek",
                        subtitle = "Bitmapový export aktuálního výkresu",
                        onClick = { closeAndRun { state.showExportDialog = true } }
                    )

                    WebMenuDivider()
                    WebMenuSectionLabel("APLIKACE")
                    WebMenuItem(
                        icon = if (fullscreen) {
                            Icons.Outlined.FullscreenExit
                        } else {
                            Icons.Outlined.Fullscreen
                        },
                        title = if (fullscreen) "Zpět do stránky" else "Celá obrazovka",
                        subtitle = "Přepnout zobrazení aplikace",
                        onClick = {
                            closeAndRun {
                                toggleAppFullscreen()
                                fullscreen = !fullscreen
                            }
                        }
                    )
                    val dark = SettingsManager.current.isDarkMode
                    WebMenuItem(
                        icon = if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        title = if (dark) "Světlý motiv" else "Tmavý motiv",
                        subtitle = "Rychle změnit barevný režim",
                        onClick = {
                            closeAndRun {
                                SettingsManager.save(
                                    SettingsManager.current.copy(
                                        isDarkMode = !dark,
                                        useSystemTheme = false,
                                        isPinkMode = false
                                    )
                                )
                            }
                        }
                    )
                    WebMenuItem(
                        icon = Icons.Outlined.Settings,
                        title = "Nastavení",
                        subtitle = "Vzhled, rýsování a chování aplikace",
                        onClick = { closeAndRun { state.showSettingsDialog = true } }
                    )
                }
            }
        }
    }
}

@Composable
private fun WebMenuSectionLabel(text: String) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    Text(
        text = text,
        color = colors.text.copy(alpha = 0.46f),
        fontSize = 9 * ui.sp,
        modifier = Modifier.padding(horizontal = dimens.sm, vertical = dimens.xs)
    )
}

@Composable
private fun WebMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(dimens.radiusSm)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (hovered) {
                    colors.hover.copy(alpha = if (colors.isDark) 0.18f else 0.09f)
                } else {
                    Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = dimens.sm, vertical = 9 * ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(35 * ui.dp)
                .background(colors.base.copy(alpha = 0.09f), RoundedCornerShape(8 * ui.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.buttonColor,
                modifier = Modifier.size(20 * ui.dp)
            )
        }
        Spacer(Modifier.width(dimens.sm))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.text,
                fontSize = 13 * ui.sp
            )
            Spacer(Modifier.height(1 * ui.dp))
            Text(
                subtitle,
                color = colors.text.copy(alpha = 0.52f),
                fontSize = 10 * ui.sp,
                lineHeight = 13 * ui.sp
            )
        }
    }
}

@Composable
private fun WebMenuDivider() {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    Spacer(Modifier.height(dimens.xs))
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.base.copy(alpha = 0.16f)))
    Spacer(Modifier.height(dimens.xs))
}

private class WebMenuPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX =
            if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val x = preferredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}
