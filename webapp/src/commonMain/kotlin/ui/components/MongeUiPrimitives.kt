package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import model.LocalMongeColors
import ui.theme.LocalMongeDimens

@Composable
fun MongePanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val shape = RoundedCornerShape(dimens.radiusMd)
    val backgroundColor = if (selected) {
        colors.selected.copy(alpha = 0.10f)
    } else {
        colors.background
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(dimens.borderThin, colors.base.copy(alpha = 0.28f), shape)
            .padding(dimens.md),
        verticalArrangement = Arrangement.spacedBy(dimens.sm),
        content = content
    )
}

@Composable
fun MongeSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    secondary: Boolean = false
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current

    Text(
        text = text,
        color = colors.text.copy(alpha = if (secondary) 0.72f else 0.92f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = dimens.xs)
    )
}

@Composable
fun MongePropertyRow(
    label: String,
    modifier: Modifier = Modifier,
    contentAlign: Alignment.Horizontal = Alignment.End,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.text.copy(alpha = 0.82f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(dimens.md * 7f)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = dimens.sm),
            horizontalAlignment = contentAlign
        ) {
            content()
        }
    }
}

@Composable
fun MongeDivider(modifier: Modifier = Modifier) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current

    Spacer(Modifier.height(dimens.xs))
    androidx.compose.material.Divider(
        color = colors.base.copy(alpha = 0.24f),
        thickness = dimens.borderThin,
        modifier = modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(dimens.xs))
}
