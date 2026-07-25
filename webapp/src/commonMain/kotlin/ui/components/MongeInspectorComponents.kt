package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import model.LocalMongeColors
import ui.theme.LocalMongeDimens

@Composable
fun MongeInspectorSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val dimens = LocalMongeDimens.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.sm)
    ) {
        MongeSectionTitle(title)
        content()
    }
}

@Composable
fun MongeInspectorPropertyRow(
    label: String,
    modifier: Modifier = Modifier,
    contentAlign: Alignment.Horizontal = Alignment.End,
    content: @Composable ColumnScope.() -> Unit
) {
    val dimens = LocalMongeDimens.current

    MongePropertyRow(
        label = label,
        modifier = modifier.heightIn(min = dimens.iconLg),
        contentAlign = contentAlign,
        content = content
    )
}

@Composable
fun MongeObjectListItem(
    text: String,
    selected: Boolean,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    leadingIcon: (@Composable BoxScope.() -> Unit)? = null
) {
    if (!visible) return

    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(dimens.radiusMd)

    val targetBg = when {
        selected -> colors.selected.copy(alpha = if (colors.isDark) 0.20f else 0.12f)
        hovered -> colors.hover.copy(alpha = if (colors.isDark) 0.16f else 0.09f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(targetBg, label = "monge_object_list_bg")
    val scale by animateFloatAsState(if (pressed) 0.99f else 1f, label = "monge_object_list_press")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.leftToolWidth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = dimens.sm, vertical = dimens.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier.size(dimens.iconSm),
                contentAlignment = Alignment.Center,
                content = leadingIcon
            )
            Spacer(Modifier.width(dimens.sm))
        }

        Text(
            text = text,
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .width(dimens.xs)
                    .heightIn(min = dimens.iconMd)
                    .clip(RoundedCornerShape(dimens.radiusSm))
                    .background(colors.selected)
                    .alpha(0.8f)
            )
        }
    }
}
