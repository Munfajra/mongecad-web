package ui.mongeui.toolbar.rightDescriptionBar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import model.LocalMongeColors
import model.MongeColorsState
import state.MongeState
import ui.components.MongeDivider
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.ObjectList
import ui.theme.LocalMongeDimens


@Composable
fun RightSidebar(state: MongeState) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val density = LocalDensity.current

    val minSelectionDp = dimens.navItemHeight
    val minListDp = dimens.navItemHeight + dimens.xl
    val handleDp = dimens.lg

    var splitAreaHeightPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = dimens.md, horizontal = dimens.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { size ->
                        splitAreaHeightPx = size.height.toFloat()
                    }
            ) {
                val minSelectionPx = with(density) { minSelectionDp.toPx() }
                val minListPx = with(density) { minListDp.toPx() }
                val handlePx = with(density) { handleDp.toPx() }

                val listTitleHeightPx = with(density) {
                    // výška textu + top padding pod ním
                    (dimens.iconLg + dimens.sm).toPx()
                }

                val maxSelectionPx =
                    if (splitAreaHeightPx > 1f) {
                        (splitAreaHeightPx - handlePx - listTitleHeightPx - minListPx)
                            .coerceAtLeast(minSelectionPx)
                    } else {
                        minSelectionPx
                    }

                val availableSelectionRangePx =
                    (maxSelectionPx - minSelectionPx).coerceAtLeast(1f)

                val selectionHeightPx =
                    minSelectionPx + state.rightSidebarSelectionRatio * availableSelectionRangePx

                Box(
                    modifier = Modifier
                        .height(with(density) { selectionHeightPx.toDp() })
                        .fillMaxWidth()
                ) {
                    MongeInspectorSection(
                        title = "Aktuální výběr",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            SelectionInfo(state)
                        }
                    }
                }

                InspectorResizeHandle(
                    heightDp = handleDp,
                    onDragDeltaY = { dy ->
                        val currentHeightPx =
                            minSelectionPx + state.rightSidebarSelectionRatio * availableSelectionRangePx

                        val newHeightPx = (currentHeightPx + dy)
                            .coerceIn(minSelectionPx, maxSelectionPx)

                        state.rightSidebarSelectionRatio =
                            ((newHeightPx - minSelectionPx) / availableSelectionRangePx)
                                .coerceIn(0f, 1f)
                    }
                )

                MongeDivider()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MongeInspectorSection(
                        title = "Objekty ve výkresu",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            ObjectList(
                                state = state,
                                clearAllOnClick = !state.isShiftPressed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DragHandleThin(
    colors: MongeColorsState,
    heightDp: Dp = 14.dp,
    onDragDeltaY: (Float) -> Unit
) {
    InspectorResizeHandle(
        heightDp = heightDp,
        onDragDeltaY = onDragDeltaY
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InspectorResizeHandle(
    heightDp: Dp = 14.dp,
    onDragDeltaY: (Float) -> Unit
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val resizeIcon = remember {
        PointerIcon.Default
    }

    val dragState = rememberDraggableState { delta ->
        // delta je už v px, kladné = dolů
        onDragDeltaY(delta)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            // ✅ důležité: i skoro průhledné pozadí, aby to chytalo pointer
            .clip(RoundedCornerShape(dimens.radiusSm))
            .background(
                if (isHovered) colors.hover.copy(alpha = if (colors.isDark) 0.16f else 0.10f)
                else Color.Transparent
            )
            .pointerHoverIcon(resizeIcon)
            .hoverable(interactionSource)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical
            )
            .padding(vertical = dimens.xs)
    ) {
        // jemná linka
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(dimens.xl)
                .height(dimens.xs)
                .clip(RoundedCornerShape(dimens.radiusSm))
                .background(
                    if (isHovered) colors.selected.copy(alpha = 0.62f)
                    else colors.base.copy(alpha = if (colors.isDark) 0.34f else 0.24f)
                )
        )

    }
}
