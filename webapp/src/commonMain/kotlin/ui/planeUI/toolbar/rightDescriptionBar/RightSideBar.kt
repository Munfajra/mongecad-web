package ui.planeUI.toolbar.rightDescriptionBar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import model.LocalMongeColors
import state.MongeState
import ui.components.MongeDivider
import ui.components.MongeInspectorSection
import ui.mongeui.toolbar.rightDescriptionBar.DragHandleThin
import ui.theme.LocalMongeDimens

@Composable
fun RightSidebarPlane(state: MongeState) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val density = LocalDensity.current

    val minSelectionDp = dimens.navItemHeight
    val minListDp = dimens.navItemHeight + dimens.xl
    val handleDp = dimens.lg

    var contentHeightPx by remember { mutableStateOf(0f) }

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
                .onSizeChanged { size: IntSize -> contentHeightPx = size.height.toFloat() }
        ) {
            val minSelectionPx = with(density) { minSelectionDp.toPx() }
            val minListPx = with(density) { minListDp.toPx() }
            val handlePx = with(density) { handleDp.toPx() }
            val reservedPx = with(density) { (dimens.iconLg + dimens.sm + dimens.lg).toPx() } + handlePx

            val maxSelectionPx = if (contentHeightPx > 1f) {
                (contentHeightPx - reservedPx - minListPx).coerceAtLeast(minSelectionPx)
            } else {
                minSelectionPx
            }

            val availableSelectionRangePx =
                (maxSelectionPx - minSelectionPx).coerceAtLeast(1f)

            val selectionHeightPx =
                minSelectionPx +
                        state.rightSidebarSelectionRatio.coerceIn(0f, 1f) *
                        availableSelectionRangePx

            Column(modifier = Modifier.fillMaxSize()) {
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
                            SelectionInfoPlane(state)
                        }
                    }
                }

                DragHandleThin(
                    colors = colors,
                    heightDp = handleDp,
                    onDragDeltaY = { dy ->
                        val currentHeightPx =
                            minSelectionPx +
                                    state.rightSidebarSelectionRatio.coerceIn(0f, 1f) *
                                    availableSelectionRangePx

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
                            ObjectListPlane(state = state, clearAllOnClick = !state.isShiftPressed)
                        }
                    }
                }
            }
        }
    }
}
