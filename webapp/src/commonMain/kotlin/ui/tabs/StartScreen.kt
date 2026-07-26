package ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.LocalMongeColors
import model.ProjectionMode
import serialization.SettingsManager
import ui.resources.painterResource
import ui.theme.LocalMongeDimens

private val MONGE_COLOR = Color(0xFF3E7FE8)
private val PLANE_COLOR = Color(0xFF27A16D)
private val DESKTOP_COLOR = Color(0xFF8B63D9)

// Jednotná typografická stupnice StartScreenu.
private const val HERO_TITLE = 25f
private const val SECTION_TITLE = 16f
private const val CARD_TITLE = 15f
private const val BODY_TEXT = 12f
private const val DETAIL_TEXT = 11f
private const val META_TEXT = 10f

@Composable
fun StartScreen(
    onNewDrawing: (ProjectionMode) -> Unit,
    onOpenDrawing: () -> Unit,
    onSettings: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenWebsite: (String) -> Unit,
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    var comparisonExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        StartBackgroundGlow(
            modifier = Modifier.align(Alignment.TopStart),
            color = MONGE_COLOR,
            offsetX = -180f,
            offsetY = -170f,
            size = 620f,
            ui = ui
        )
        StartBackgroundGlow(
            modifier = Modifier.align(Alignment.BottomEnd),
            color = PLANE_COLOR,
            offsetX = 180f,
            offsetY = 170f,
            size = 560f,
            ui = ui
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.xl, vertical = dimens.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 1040 * ui.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    StartSettingsAction(onClick = onSettings)
                }

                StartHero(ui)
                Spacer(Modifier.height(dimens.xl))

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 720 * ui.dp
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.md)) {
                            ModeTile(
                                iconPath = "icons/mongeM.svg",
                                title = "Mongeovo promítání",
                                subtitle = "Půdorys a nárys",
                                accent = MONGE_COLOR,
                                onClick = { onNewDrawing(ProjectionMode.MONGE) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            ModeTile(
                                iconPath = "icons/geometry.svg",
                                title = "Rýsování v rovině",
                                subtitle = "Jedna nákresna",
                                accent = PLANE_COLOR,
                                onClick = { onNewDrawing(ProjectionMode.PLANE) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.md)) {
                            ModeTile(
                                iconPath = "icons/mongeM.svg",
                                title = "Mongeovo promítání",
                                subtitle = "Půdorys a nárys",
                                accent = MONGE_COLOR,
                                onClick = { onNewDrawing(ProjectionMode.MONGE) },
                                modifier = Modifier.weight(1f)
                            )
                            ModeTile(
                                iconPath = "icons/geometry.svg",
                                title = "Rýsování v rovině",
                                subtitle = "Jedna nákresna",
                                accent = PLANE_COLOR,
                                onClick = { onNewDrawing(ProjectionMode.PLANE) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(dimens.md))
                MongeCompatibilityCard(onOpenDrawing, Modifier.fillMaxWidth())

                Spacer(Modifier.height(dimens.md))
                TouchAndTabletCard(Modifier.fillMaxWidth())

                Spacer(Modifier.height(dimens.lg))
                ComparisonDisclosure(
                    expanded = comparisonExpanded,
                    onToggle = { comparisonExpanded = !comparisonExpanded },
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedVisibility(
                    visible = comparisonExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(dimens.md))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val compact = maxWidth < 820 * ui.dp
                            if (compact) {
                                Column(verticalArrangement = Arrangement.spacedBy(dimens.md)) {
                                    WebCapabilitiesCard(Modifier.fillMaxWidth())
                                    DesktopCapabilitiesCard(Modifier.fillMaxWidth())
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(dimens.md),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    WebCapabilitiesCard(Modifier.weight(1f))
                                    DesktopCapabilitiesCard(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(dimens.lg))
            }
        }
    }
}

@Composable
private fun StartHero(ui: Float) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(100 * ui.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource("icons/ikonaMC.svg"),
                contentDescription = "MongeCAD",
                modifier = Modifier.size(84 * ui.dp)
            )
            Text(
                text = "Web",
                color = Color.White,
                fontSize = META_TEXT * ui.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MONGE_COLOR)
                    .border(2.dp, colors.background, CircleShape)
                    .padding(horizontal = 9 * ui.dp, vertical = 4 * ui.dp)
            )
        }
        Spacer(Modifier.height(dimens.sm))
        Text(
            text = "MongeCAD Web",
            color = colors.text,
            fontSize = SECTION_TITLE * ui.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(dimens.md))
        Text(
            text = "Deskriptivní geometrie přímo v prohlížeči",
            color = colors.text,
            fontSize = HERO_TITLE * ui.sp,
            lineHeight = 31 * ui.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StartSettingsAction(onClick: () -> Unit) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(dimens.radiusMd)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(colors.base.copy(alpha = if (hovered) 0.13f else 0.06f))
            .border(1.dp, colors.base.copy(alpha = 0.18f), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = dimens.sm, vertical = 7 * ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Settings,
            contentDescription = null,
            tint = colors.text.copy(alpha = 0.68f),
            modifier = Modifier.size(16 * ui.dp)
        )
        Spacer(Modifier.width(6 * ui.dp))
        Text(
            "Nastavení",
            color = colors.text.copy(alpha = 0.70f),
            fontSize = DETAIL_TEXT * ui.sp
        )
    }
}

@Composable
private fun ModeTile(
    iconPath: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val shape = RoundedCornerShape(dimens.radiusLg)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .heightIn(min = 108 * ui.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (hovered) 0.25f else 0.16f),
                        accent.copy(alpha = if (hovered) 0.10f else 0.045f)
                    )
                ),
                shape
            )
            .border(1.dp, accent.copy(alpha = if (hovered) 0.72f else 0.36f), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(dimens.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52 * ui.dp)
                .background(accent.copy(alpha = 0.13f), RoundedCornerShape(14 * ui.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconPath),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accent),
                modifier = Modifier.size(33 * ui.dp)
            )
        }
        Spacer(Modifier.width(dimens.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.text,
                fontSize = CARD_TITLE * ui.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4 * ui.dp))
            Text(
                subtitle,
                color = colors.text.copy(alpha = 0.62f),
                fontSize = BODY_TEXT * ui.sp,
                lineHeight = 17 * ui.sp
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22 * ui.dp)
        )
    }
}

@Composable
private fun TouchAndTabletCard(modifier: Modifier = Modifier) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val shape = RoundedCornerShape(dimens.radiusLg)

    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.base.copy(alpha = if (colors.isDark) 0.095f else 0.045f), shape)
            .border(1.dp, colors.base.copy(alpha = 0.20f), shape)
            .padding(dimens.lg),
        verticalArrangement = Arrangement.spacedBy(dimens.md)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(42 * ui.dp)
                    .background(PLANE_COLOR.copy(alpha = 0.13f), RoundedCornerShape(11 * ui.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.TouchApp,
                    contentDescription = null,
                    tint = PLANE_COLOR,
                    modifier = Modifier.size(23 * ui.dp)
                )
            }
            Spacer(Modifier.width(dimens.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Dotyk, stylus a tablety",
                    color = colors.text,
                    fontSize = SECTION_TITLE * ui.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4 * ui.dp))
                Text(
                    text = "Webová i desktopová verze MongeCADu podporují dotykové ovládání a stylus. " +
                            "Protože plná aplikace pro Android ani iPadOS není k dispozici, je MongeCAD Web " +
                            "vhodnou alternativou pro tablety.",
                    color = colors.text.copy(alpha = 0.63f),
                    fontSize = BODY_TEXT * ui.sp,
                    lineHeight = 18 * ui.sp
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MONGE_COLOR.copy(alpha = 0.09f), RoundedCornerShape(dimens.radiusMd))
                .padding(horizontal = dimens.md, vertical = dimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Fullscreen,
                contentDescription = null,
                tint = MONGE_COLOR,
                modifier = Modifier.size(19 * ui.dp)
            )
            Spacer(Modifier.width(9 * ui.dp))
            Text(
                text = "Na počítači doporučujeme pro více prostoru přepnout prohlížeč na celou obrazovku (F11).",
                color = colors.text.copy(alpha = 0.72f),
                fontSize = DETAIL_TEXT * ui.sp,
                lineHeight = 16 * ui.sp
            )
        }
    }
}

@Composable
private fun MongeCompatibilityCard(
    onOpenDrawing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val shape = RoundedCornerShape(dimens.radiusLg)

    Row(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MONGE_COLOR.copy(alpha = if (colors.isDark) 0.17f else 0.09f),
                        DESKTOP_COLOR.copy(alpha = if (colors.isDark) 0.14f else 0.065f)
                    )
                ),
                shape
            )
            .border(1.dp, MONGE_COLOR.copy(alpha = 0.25f), shape)
            .padding(dimens.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42 * ui.dp)
                .background(MONGE_COLOR.copy(alpha = 0.13f), RoundedCornerShape(11 * ui.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Save,
                contentDescription = null,
                tint = MONGE_COLOR,
                modifier = Modifier.size(22 * ui.dp)
            )
        }
        Spacer(Modifier.width(dimens.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = ".monge propojuje web a desktop",
                color = colors.text,
                fontSize = SECTION_TITLE * ui.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4 * ui.dp))
            Text(
                text = "Výkresy jsou kompatibilní oběma směry. U prvků, které web neumí, upozorní na omezení.",
                color = colors.text.copy(alpha = 0.63f),
                fontSize = BODY_TEXT * ui.sp,
                lineHeight = 18 * ui.sp
            )
        }
        Spacer(Modifier.width(dimens.md))
        OpenMongeButton(onClick = onOpenDrawing)
    }
}

/** Otevření .monge rovnou z karty o kompatibilitě. */
@Composable
private fun OpenMongeButton(onClick: () -> Unit) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val shape = RoundedCornerShape(dimens.radiusMd)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .clip(shape)
            .background(MONGE_COLOR.copy(alpha = if (hovered) 0.30f else 0.18f), shape)
            .border(1.dp, MONGE_COLOR.copy(alpha = if (hovered) 0.75f else 0.45f), shape)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.md, vertical = dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = MONGE_COLOR,
            modifier = Modifier.size(17 * ui.dp)
        )
        Spacer(Modifier.width(7 * ui.dp))
        Text(
            text = "Otevřít .monge",
            color = colors.text,
            fontSize = BODY_TEXT * ui.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ComparisonDisclosure(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(dimens.radiusLg)

    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.base.copy(alpha = if (hovered) 0.12f else 0.055f))
            .border(1.dp, colors.base.copy(alpha = 0.20f), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(dimens.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40 * ui.dp)
                .background(DESKTOP_COLOR.copy(alpha = 0.12f), RoundedCornerShape(10 * ui.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.DesktopWindows,
                contentDescription = null,
                tint = DESKTOP_COLOR,
                modifier = Modifier.size(21 * ui.dp)
            )
        }
        Spacer(Modifier.width(dimens.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Jaký je rozdíl mezi Web a desktop verzí?",
                color = colors.text,
                fontSize = SECTION_TITLE * ui.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3 * ui.dp))
            Text(
                text = if (expanded) "Kliknutím srovnání zavřete." else "Kliknutím zobrazíte dostupné funkce a omezení.",
                color = colors.text.copy(alpha = 0.56f),
                fontSize = BODY_TEXT * ui.sp
            )
        }
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Skrýt srovnání" else "Zobrazit srovnání",
            tint = colors.text.copy(alpha = 0.65f),
            modifier = Modifier.size(24 * ui.dp)
        )
    }
}

@Composable
private fun WebCapabilitiesCard(modifier: Modifier = Modifier) {
    InfoCard(
        title = "Web",
        icon = Icons.Outlined.CheckCircle,
        accent = PLANE_COLOR,
        modifier = modifier
    ) {
        CapabilityRow(
            icon = Icons.Outlined.CheckCircle,
            title = "Základní rýsování",
            text = "Body, přímky, úsečky, roviny a kuželosečky.",
            accent = PLANE_COLOR
        )
        CapabilityRow(
            icon = Icons.Outlined.FolderOpen,
            title = "Práce se soubory",
            text = "Otevření i uložení formátu .monge.",
            accent = PLANE_COLOR
        )
        CapabilityRow(
            icon = Icons.Outlined.Image,
            title = "Bitmapový export",
            text = "Výkres jako PNG nebo JPG.",
            accent = PLANE_COLOR
        )
    }
}

@Composable
private fun DesktopCapabilitiesCard(modifier: Modifier = Modifier) {
    InfoCard(
        title = "Desktopová aplikace",
        icon = Icons.Outlined.DesktopWindows,
        accent = DESKTOP_COLOR,
        modifier = modifier
    ) {
        CapabilityRow(
            icon = Icons.Outlined.ViewInAr,
            title = "3D náhled",
            text = "Prostorová kontrola konstrukce.",
            accent = DESKTOP_COLOR
        )
        CapabilityRow(
            icon = Icons.Outlined.PictureAsPdf,
            title = "PDF export a tisk",
            text = "Web exportuje jen bitmapu.",
            accent = DESKTOP_COLOR
        )
        CapabilityRow(
            icon = Icons.Outlined.Architecture,
            title = "Složitější objekty",
            text = "Přímá konstrukce rotačních a přímkových ploch, mnohoúhelníků v rovině, " +
                    "hranolů, jehlanů, kulových ploch, kuželů a válců.",
            accent = DESKTOP_COLOR
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val ui = SettingsManager.current.UIscale / 75f
    val shape = RoundedCornerShape(dimens.radiusLg)

    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.base.copy(alpha = if (colors.isDark) 0.095f else 0.045f), shape)
            .border(1.dp, colors.base.copy(alpha = 0.20f), shape)
            .padding(dimens.lg),
        verticalArrangement = Arrangement.spacedBy(dimens.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38 * ui.dp)
                    .background(accent.copy(alpha = 0.13f), RoundedCornerShape(10 * ui.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21 * ui.dp)
                )
            }
            Spacer(Modifier.width(dimens.sm))
            Text(
                title,
                color = colors.text,
                fontSize = CARD_TITLE * ui.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.base.copy(alpha = 0.16f)))
        content()
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    title: String,
    text: String,
    accent: Color,
) {
    val colors = LocalMongeColors.current
    val ui = SettingsManager.current.UIscale / 75f
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(17 * ui.dp)
        )
        Spacer(Modifier.width(9 * ui.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.text,
                fontSize = BODY_TEXT * ui.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2 * ui.dp))
            Text(
                text,
                color = colors.text.copy(alpha = 0.60f),
                fontSize = DETAIL_TEXT * ui.sp,
                lineHeight = 16 * ui.sp
            )
        }
    }
}

@Composable
private fun StartBackgroundGlow(
    modifier: Modifier,
    color: Color,
    offsetX: Float,
    offsetY: Float,
    size: Float,
    ui: Float,
) {
    val colors = LocalMongeColors.current
    Box(
        modifier
            .offset(offsetX * ui.dp, offsetY * ui.dp)
            .size(size * ui.dp)
            .background(
                Brush.radialGradient(
                    listOf(
                        color.copy(alpha = if (colors.isDark) 0.22f else 0.12f),
                        Color.Transparent
                    )
                )
            )
    )
}
