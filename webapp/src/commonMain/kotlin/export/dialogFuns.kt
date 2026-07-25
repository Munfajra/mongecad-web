package export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.MongeColorsState
import serialization.SettingsManager
import ui.mongeui.toolbar.SkikoButton

@Composable
fun ExportHeader(
    colors: MongeColorsState,
    onClose: () -> Unit,
    title: String = "Export",
    subtitle: String = "Nastavte stránku, okraje a viditelnost. Myší upravte náhled."
) {
    val ui = SettingsManager.current.UIscale/75f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, fontSize = 20.sp*ui, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(
                subtitle,
                fontSize = 12.sp*ui,
                color = colors.text.copy(alpha = 0.65f)
            )
        }
        Spacer(Modifier.weight(1f))
        SkikoButton(onClick = onClose, width = 38*ui.dp, height = 32*ui.dp) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Zavřít",
                modifier = Modifier.size(18*ui.dp),
                tint = colors.text
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    colors: MongeColorsState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, colors.base.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = colors.text, fontSize = 13.sp)
        content()
    }
}
@Composable
fun TogglePillRow(
    options: List<String>,
    selectedIndex: Int,
    colors: MongeColorsState,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    val ui = SettingsManager.current.UIscale/75f
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.base.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .border(1*ui.dp, colors.base.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(4*ui.dp),
        horizontalArrangement = Arrangement.spacedBy(6*ui.dp)
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val itemAlpha = if (enabled) 1f else 0.48f
            Box(
                Modifier
                    .weight(1f)
                    .height(32*ui.dp)
                    .background(
                        if (selected) colors.selected.copy(alpha = 0.25f * itemAlpha) else Color.Transparent,
                        RoundedCornerShape(999.dp)
                    )
                    .border(
                        1*ui.dp,
                        if (selected) colors.selected.copy(alpha = 0.40f * itemAlpha) else Color.Transparent,
                        RoundedCornerShape(999.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled
                    ) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) colors.text.copy(alpha = itemAlpha) else colors.text.copy(alpha = 0.75f * itemAlpha),
                    fontSize = 13*ui.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
@Composable
fun ExportFooter(
    colors: MongeColorsState,
    exportLabel: String = "Exportovat",
    exportEnabled: Boolean = true,
    onClose: () -> Unit,
    onExport: () -> Unit
) {
    val ui = SettingsManager.current.UIscale/75f
    Row(
        Modifier
            .fillMaxWidth()
            .background(exportSurfaceColor("panel", colors), RoundedCornerShape(14*ui.dp))
            .border(1*ui.dp, colors.base.copy(alpha = if (colors.isDark) 0.34f else 0.16f), RoundedCornerShape(14*ui.dp))
            .padding(horizontal = 10*ui.dp, vertical = 9*ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExportActionButton(
            text = "Zavřít",
            colors = colors,
            ui = ui,
            kind = ExportButtonKind.Secondary,
            onClick = onClose
        )
        Spacer(Modifier.weight(1f))
        ExportActionButton(
            text = exportLabel,
            colors = colors,
            ui = ui,
            kind = ExportButtonKind.Primary,
            icon = true,
            enabled = exportEnabled,
            onClick = onExport
        )
    }
}
@Composable
fun CheckRow(
    text: String,
    checked: Boolean,
    colors: MongeColorsState,
    onChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val ui = SettingsManager.current.UIscale/75f
    val backgroundColor = when {
        pressed -> colors.hover.copy(alpha = 0.18f)
        hovered -> colors.hover.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36*ui.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(10*ui.dp)
            )
            .hoverable(interactionSource = interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onChange(!checked)
            }
            .padding(horizontal = 8*ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null, // řídí row
            colors = CheckboxDefaults.colors(
                checkedColor = colors.selected,
                uncheckedColor = colors.base.copy(alpha = 0.55f),
                checkmarkColor = Color.White
            )
        )

        Spacer(Modifier.width(8*ui.dp))

        Text(
            text = text,
            color = colors.text,
            fontSize = 13*ui.sp
        )
    }
}
@Composable
fun SliderRow(
    label: String,
    valueText: String,
    colors: MongeColorsState,
    content: @Composable () -> Unit
) {
    val ui = SettingsManager.current.UIscale / 75f

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40f * ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = colors.text,
            fontSize = 13f * ui.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(12f * ui.dp))

        Box(Modifier.width(190f * ui.dp)) {
            content()
        }

        Spacer(Modifier.width(10f * ui.dp))

        Box(
            Modifier
                .widthIn(min = 58f * ui.dp)
                .background(colors.selected.copy(alpha = if (colors.isDark) 0.18f else 0.09f), RoundedCornerShape(999.dp))
                .border(1f * ui.dp, colors.selected.copy(alpha = if (colors.isDark) 0.34f else 0.18f), RoundedCornerShape(999.dp))
                .padding(horizontal = 9f * ui.dp, vertical = 4f * ui.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                valueText,
                color = colors.text.copy(alpha = 0.90f),
                fontSize = 12f * ui.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
enum class ExportButtonKind { Primary, Secondary, Tertiary }
private fun exportSurfaceColor(role: String, colors: MongeColorsState): Color =
    when (role) {
        "panel" -> if (colors.isDark) Color(0xFF11161D) else Color(0xFFFFFFFF)
        "panelAlt" -> if (colors.isDark) Color(0xFF151B23) else Color(0xFFF8FAFC)
        else -> colors.background
    }


@Composable
fun ExportActionButton(
    text: String,
    colors: MongeColorsState,
    ui: Float,
    kind: ExportButtonKind,
    icon: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10f * ui.dp)
    val disabledAlpha = if (enabled) 1f else 0.48f
    val background = when (kind) {
        ExportButtonKind.Primary -> colors.selected.copy(alpha = (if (hovered && enabled) 1.0f else 0.92f) * disabledAlpha)
        ExportButtonKind.Secondary -> exportSurfaceColor("panelAlt", colors)
        ExportButtonKind.Tertiary -> Color.Transparent
    }
    val border = when (kind) {
        ExportButtonKind.Primary -> colors.selected.copy(alpha = 0.72f * disabledAlpha)
        ExportButtonKind.Secondary -> colors.base.copy(alpha = if (colors.isDark) 0.42f else 0.20f)
        ExportButtonKind.Tertiary -> colors.base.copy(alpha = if (hovered) 0.28f else 0.12f)
    }
    val foreground =
        (if (kind == ExportButtonKind.Primary) Color.White else colors.text.copy(alpha = 0.86f))
            .copy(alpha = disabledAlpha)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(background, shape)
            .border(1.dp, border, shape)
            .hoverable(interactionSource)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 13f * ui.dp, vertical = 8f * ui.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(
                painter = painterResource("icons/check.svg"),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16f * ui.dp)
            )
            Spacer(Modifier.width(7f * ui.dp))
        }
        Text(text, color = foreground, fontSize = 13f * ui.sp, fontWeight = FontWeight.SemiBold)
    }
}
fun DrawScope.drawMarginsOverlay(pagePx: Size, marginPx: Float, tint: Color) {
    val p = Path().apply {
        fillType = PathFillType.EvenOdd
        // vnější obdélník (celá stránka)
        addRect(Rect(0f, 0f, pagePx.width, pagePx.height))
        // vnitřní „díra“ = content-box
        addRect(Rect(marginPx, marginPx, pagePx.width - marginPx, pagePx.height - marginPx))
    }
    drawPath(p, tint)  // žádné překryvy, žádné zdvojení barvy
}
