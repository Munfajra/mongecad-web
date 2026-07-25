package ui.mongeui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import model.LocalMongeColors
import state.MongeState
import ui.theme.LocalMongeDimens

@Composable
fun LeftPanelToolbar(state: MongeState){
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
        ActionToolbar(state)
    }
}
