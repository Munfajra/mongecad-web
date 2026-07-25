package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import ui.components.TooltipArea
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.LocalMongeColors
import ui.theme.LocalMongeDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MongeRibbonButton(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable BoxScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(dimens.radiusMd)

    val targetBg = when {
        selected -> colors.selected.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
        hovered && enabled -> colors.hover.copy(alpha = if (colors.isDark) 0.16f else 0.08f)
        else -> colors.base.copy(alpha = if (colors.isDark) 0.07f else 0.035f)
    }
    val bg by animateColorAsState(targetBg, label = "monge_ribbon_bg")

    val targetBorder = when {
        selected -> colors.selected.copy(alpha = if (colors.isDark) 0.70f else 0.52f)
        hovered && enabled -> colors.base.copy(alpha = if (colors.isDark) 0.28f else 0.16f)
        else -> colors.base.copy(alpha = if (colors.isDark) 0.14f else 0.08f)
    }
    val border by animateColorAsState(targetBorder, label = "monge_ribbon_border")
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "monge_ribbon_press")
    val contentColor = when {
        !enabled -> colors.disabled
        selected -> colors.selected
        else -> colors.buttonColor
    }

    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .clip(RoundedCornerShape(dimens.radiusSm))
                    .background(Color.DarkGray)
                    .padding(horizontal = dimens.sm, vertical = dimens.xs)
            ) {
                Text(text, color = Color.White, fontSize = 12.sp)
            }
        },
        delayMillis = 500,
    ) {
        Column(
            modifier = modifier
                .size(dimens.ribbonButtonWidth, dimens.ribbonButtonHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(bg, shape)
                .border(BorderStroke(dimens.borderThin, border), shape)
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .hoverable(interactionSource = interactionSource, enabled = enabled)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .alpha(if (enabled) 1f else 0.42f)
                .padding(horizontal = dimens.xs, vertical = dimens.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Box(
                    modifier = Modifier.size(dimens.iconLg),
                    contentAlignment = Alignment.Center,
                    content = icon
                )
            }
        }
    }
}

@Composable
fun MongeToolbarGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    showSeparator: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val shape = RoundedCornerShape(dimens.radiusSm)
    val borderColor = colors.buttonColor.copy(alpha = if (colors.isDark) 0.34f else 0.16f)
    val backgroundColor = colors.base.copy(alpha = if (colors.isDark) 0.10f else 0.035f)
    val separatorColor = colors.base.copy(alpha = if (colors.isDark) 0.30f else 0.14f)
    val hasTitle = title != null

    Row(
        modifier = modifier
            .height(dimens.toolbarHeight)
            .padding(vertical = dimens.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = if (hasTitle) dimens.xs else 0.dp)
                    .clip(shape)
                    .background(backgroundColor, shape)
                    .border(dimens.borderThin, borderColor, shape)
                    .padding(
                        start = dimens.sm,
                        top = if (hasTitle) dimens.sm else 0.dp,
                        end = dimens.sm,
                        bottom = 0.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                    content = content
                )
            }

            if (title != null) {
                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text.copy(alpha = if (colors.isDark) 0.68f else 0.60f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(colors.background)
                        .padding(horizontal = dimens.xs)
                )
            }
        }
    }
}

@Composable
fun MongeVerticalToolButton(
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable BoxScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(dimens.radiusMd)

    val targetBg = when {
        selected -> colors.selected.copy(alpha = if (colors.isDark) 0.22f else 0.12f)
        hovered && enabled -> colors.hover.copy(alpha = if (colors.isDark) 0.16f else 0.08f)
        else -> colors.base.copy(alpha = if (colors.isDark) 0.07f else 0.035f)
    }
    val bg by animateColorAsState(targetBg, label = "monge_vertical_bg")
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, label = "monge_vertical_press")
    val contentColor = when {
        !enabled -> colors.disabled
        selected -> colors.selected
        else -> colors.buttonColor
    }

    Box(
        modifier = modifier
            .size(dimens.leftToolWidth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg, shape)
            .border(
                BorderStroke(
                    dimens.borderThin,
                    if (selected) colors.selected.copy(alpha = if (colors.isDark) 0.70f else 0.52f)
                    else colors.base.copy(alpha = if (colors.isDark) 0.14f else 0.08f)
                ),
                shape
            )
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.42f),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(dimens.xs)
                    .background(colors.selected)
            )
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(
                modifier = Modifier.size(dimens.iconMd),
                contentAlignment = Alignment.Center,
                content = icon
            )
        }
    }
}

@Composable
fun MongeNavItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    selectedBackground: Color? = null,
    defaultBackground: Color = Color.Transparent,
    hoverBackground: Color? = null,
    accentColor: Color? = null,
    connectedToEnd: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable BoxScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = if (connectedToEnd) {
        RoundedCornerShape(
            topStart = dimens.radiusMd,
            bottomStart = dimens.radiusMd,
            topEnd = 0.dp,
            bottomEnd = 0.dp
        )
    } else {
        RoundedCornerShape(dimens.radiusMd)
    }

    val targetBg = when {
        selected -> selectedBackground ?: colors.background
        hovered && enabled -> hoverBackground ?: colors.hover.copy(alpha = if (colors.isDark) 0.14f else 0.10f)
        else -> defaultBackground
    }
    val bg by animateColorAsState(targetBg, label = "monge_nav_bg")
    val scale by animateFloatAsState(
        if (!connectedToEnd && pressed && enabled) 0.985f else 1f,
        label = "monge_nav_press"
    )
    val contentAlpha = if (enabled) 1f else 0.42f
    val contentColor = if (selected) colors.selected else colors.text

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.navItemHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg, shape)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .alpha(contentAlpha)
            .padding(horizontal = dimens.md, vertical = dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                Modifier
                    .width(dimens.xs)
                    .height(dimens.iconLg)
                    .clip(RoundedCornerShape(dimens.radiusSm))
                    .background(accentColor ?: colors.selected)
            )
            Spacer(Modifier.width(dimens.sm))
        }

        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(
                modifier = Modifier.size(dimens.iconLg),
                contentAlignment = Alignment.Center,
                content = icon
            )
        }

        Spacer(Modifier.width(dimens.md))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = colors.text.copy(alpha = 0.62f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
