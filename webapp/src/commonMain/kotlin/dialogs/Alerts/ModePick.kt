package dialogs.Alerts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import dialogs.nameInput.MongeDialog
import model.LocalMongeColors
import model.MongeColorsState
import model.ProjectionMode
import serialization.SettingsManager
import ui.resources.painterResource

private data class WebModeDef(
    val mode: ProjectionMode,
    val title: String,
    val subtitle: String,
    val iconRes: String,
    val accent: Color,
)

@Composable
fun ModePickerDialog(
    onDismiss: () -> Unit,
    onPick: (ProjectionMode) -> Unit,
) {
    val ui = SettingsManager.current.UIscale / 75f
    val colors = LocalMongeColors.current
    val modes = remember {
        listOf(
            WebModeDef(
                mode = ProjectionMode.MONGE,
                title = "Mongeovo promítání",
                subtitle = "Promítání na dvě kolmé průmětny",
                iconRes = "icons/mongeM.svg",
                accent = Color(0xFF4D7CDB),
            ),
            WebModeDef(
                mode = ProjectionMode.PLANE,
                title = "Rovinné rýsování",
                subtitle = "2D konstrukce v jedné rovině",
                iconRes = "icons/geometry.svg",
                accent = Color(0xFF3C8F6A),
            ),
        )
    }

    MongeDialog(
        onDismissRequest = onDismiss,
        width = 620f * ui.dp,
        title = {
            Text(
                text = "Vyberte režim nového výkresu",
                fontSize = 20f * ui.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10f * ui.dp),
            ) {
                modes.forEach { mode ->
                    ModeCardMinimal(
                        definition = mode,
                        colors = colors,
                        onPick = { onPick(mode.mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            ModePickerActionButton(
                text = "Zrušit",
                colors = colors,
                ui = ui,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun ModeCardMinimal(
    definition: WebModeDef,
    colors: MongeColorsState,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = SettingsManager.current.UIscale / 75f
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12f * ui.dp)
    val background by animateColorAsState(
        targetValue = if (hovered) modePickerSurface(colors, "hover") else modePickerSurface(colors, "card"),
        label = "mode_picker_card_bg",
    )
    val border by animateColorAsState(
        targetValue = if (hovered) {
            definition.accent.copy(alpha = if (colors.isDark) 0.62f else 0.40f)
        } else {
            colors.base.copy(alpha = if (colors.isDark) 0.28f else 0.14f)
        },
        label = "mode_picker_card_border",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (hovered) definition.accent else colors.base.copy(alpha = 0.14f),
        label = "mode_picker_card_indicator",
    )

    Row(
        modifier = modifier
            .height(112f * ui.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onPick)
            .padding(12f * ui.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(if (hovered) 4f * ui.dp else 3f * ui.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4f * ui.dp))
                .background(indicatorColor),
        )
        Spacer(Modifier.width(11f * ui.dp))
        Box(
            modifier = Modifier
                .size(40f * ui.dp)
                .clip(RoundedCornerShape(10f * ui.dp))
                .background(definition.accent.copy(alpha = if (hovered) 0.18f else 0.10f))
                .border(
                    1.dp,
                    definition.accent.copy(alpha = if (hovered) 0.42f else 0.18f),
                    RoundedCornerShape(10f * ui.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(definition.iconRes),
                contentDescription = definition.title,
                tint = if (hovered) definition.accent else colors.text.copy(alpha = 0.72f),
                modifier = Modifier.size(23f * ui.dp),
            )
        }
        Spacer(Modifier.width(12f * ui.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = definition.title,
                color = colors.text,
                fontSize = 14f * ui.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4f * ui.dp))
            Text(
                text = definition.subtitle,
                color = colors.text.copy(alpha = 0.62f),
                fontSize = 11f * ui.sp,
                lineHeight = 14f * ui.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModePickerActionButton(
    text: String,
    colors: MongeColorsState,
    ui: Float,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10f * ui.dp)
    val background by animateColorAsState(
        targetValue = if (hovered) modePickerSurface(colors, "hover") else modePickerSurface(colors, "card"),
        label = "mode_picker_action_bg",
    )
    val border by animateColorAsState(
        targetValue = colors.base.copy(
            alpha = if (hovered) {
                if (colors.isDark) 0.52f else 0.26f
            } else {
                if (colors.isDark) 0.42f else 0.20f
            },
        ),
        label = "mode_picker_action_border",
    )

    Row(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13f * ui.dp, vertical = 8f * ui.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = colors.text.copy(alpha = 0.86f),
            fontSize = 13f * ui.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun modePickerSurface(colors: MongeColorsState, role: String): Color =
    when (role) {
        "card" -> if (colors.isDark) Color(0xFF181B1F) else Color(0xFFF8F9FB)
        "hover" -> if (colors.isDark) Color(0xFF1D222B) else Color(0xFFEFF3FA)
        else -> colors.background
    }
