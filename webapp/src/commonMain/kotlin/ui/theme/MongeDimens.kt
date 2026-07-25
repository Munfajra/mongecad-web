package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
data class MongeDimens(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,

    val radiusSm: Dp,
    val radiusMd: Dp,
    val radiusLg: Dp,

    val borderThin: Dp,

    val toolbarHeight: Dp,
    val ribbonButtonWidth: Dp,
    val ribbonButtonHeight: Dp,
    val leftToolWidth: Dp,
    val rightPanelWidth: Dp,
    val bottomBarHeight: Dp,

    val iconSm: Dp,
    val iconMd: Dp,
    val iconLg: Dp,

    val navItemHeight: Dp
)

@Composable
fun rememberMongeDimens(ui: Float): MongeDimens = remember(ui) {
    MongeDimens(
        xs = (4f * ui).dp,
        sm = (8f * ui).dp,
        md = (12f * ui).dp,
        lg = (16f * ui).dp,
        xl = (24f * ui).dp,

        radiusSm = (6f * ui).dp,
        radiusMd = (10f * ui).dp,
        radiusLg = (14f * ui).dp,

        borderThin = (1f * ui).dp,

        toolbarHeight = (108f * ui).dp,
        ribbonButtonWidth = (72f * ui).dp,
        ribbonButtonHeight = (76f * ui).dp,
        leftToolWidth = (42f * ui).dp,
        rightPanelWidth = (260f * ui).dp,
        bottomBarHeight = (28f * ui).dp,

        iconSm = (18f * ui).dp,
        iconMd = (24f * ui).dp,
        iconLg = (32f * ui).dp,

        navItemHeight = (120f * ui).dp
    )
}

val LocalMongeDimens = staticCompositionLocalOf {
    MongeDimens(
        xs = 4.dp,
        sm = 8.dp,
        md = 12.dp,
        lg = 16.dp,
        xl = 24.dp,

        radiusSm = 6.dp,
        radiusMd = 10.dp,
        radiusLg = 14.dp,

        borderThin = 1.dp,

        toolbarHeight = 108.dp,
        ribbonButtonWidth = 72.dp,
        ribbonButtonHeight = 76.dp,
        leftToolWidth = 42.dp,
        rightPanelWidth = 260.dp,
        bottomBarHeight = 28.dp,

        iconSm = 18.dp,
        iconMd = 24.dp,
        iconLg = 32.dp,

        navItemHeight = 120.dp
    )
}
