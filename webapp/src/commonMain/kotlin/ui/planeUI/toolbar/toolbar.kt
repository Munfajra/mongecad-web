package ui.planeUI.toolbar

import dialogs.batchinput.BatchInputLauncherToolbarPlane
import ui.mongeui.toolbar.WebAppMenuButton
import ui.components.TooltipArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import ui.components.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import model.LocalMongeColors
import state.MongeState
import ui.components.MongeToolbarGroup
import ui.mongeui.toolbar.OrthogonalToggleButton
import ui.mongeui.toolbar.PaperPreviewButton
import ui.mongeui.toolbar.ParallelToggleButton
import ui.mongeui.toolbar.ToolSet
import ui.theme.LocalMongeDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaneToolbar(
    state: MongeState,
    showDialog: MutableState<Boolean>,
    showParamDialog: MutableState<Boolean>,
    buttonsize: Dp
) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = dimens.xs)
                .horizontalScroll(scrollState)
                .height(dimens.toolbarHeight)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f

                            if (deltaY != 0f) {
                                coroutineScope.launch {
                                    scrollState.scrollBy(-deltaY * 50f)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.xs)
        ) {
            MongeToolbarGroup(title = "Menu") {
                WebAppMenuButton(state, buttonsize)
            }

            MongeToolbarGroup(title = "Nástroje") {
                ToolSet(state, buttonsize)
            }

            MongeToolbarGroup(title = "Zadání") {
                BatchInputLauncherToolbarPlane(
                    showDialog = showDialog,
                    showParamDialog = showParamDialog,
                    state = state,
                    buttonsize = buttonsize
                )
            }

            MongeToolbarGroup(title = "Konstrukce") {
                PlaneObjectSelectionRow(
                    selectedObject = state.drawobjects,
                    onSelect = { state.drawobjects = it },
                    state = state,
                    buttonsize
                )
            }

            MongeToolbarGroup(title = "Směr") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ParallelToggleButton(state, buttonsize = buttonsize)
                    OrthogonalToggleButton(state, buttonsize = buttonsize)
                }
            }

            MongeToolbarGroup(title = "Styl") {
                PlaneProjectionModeSelector(state = state, buttonsize = buttonsize)
            }

            MongeToolbarGroup(title = "Mnohoúhelník") {
                PokrocileMenuButtonPlane(state, buttonsize)
            }


            MongeToolbarGroup(title = "Výkres") {
                TooltipArea(
                    tooltip = {
                        Box(Modifier.background(Color.DarkGray).padding(dimens.sm)) {
                            Text("Rozměry výkresu", color = Color.White)
                        }
                    },
                    delayMillis = 500,
                ) {
                    PaperPreviewButton(state, buttonsize)
                }
            }
            MongeToolbarGroup(title = "Vývést") {
                TooltipArea(
                    tooltip = {
                        Box(Modifier.background(Color.DarkGray).padding(dimens.sm)) {
                            Text("Převést do jiného módu", color = Color.White)
                        }
                    },
                    delayMillis = 500,
                ) {
                    ModeButtonPlane(buttonsize)
                }
            }
        }

        HorizontalScrollbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(dimens.xs),
            adapter = rememberScrollbarAdapter(scrollState),
            style = ScrollbarStyle(
                minimalHeight = dimens.xl,
                thickness = dimens.xs,
                shape = RoundedCornerShape(100),
                hoverDurationMillis = 300,
                unhoverColor = colors.base.copy(alpha = if (colors.isDark) 0.18f else 0.12f),
                hoverColor = colors.hover.copy(alpha = if (colors.isDark) 0.34f else 0.22f)
            )
        )
    }
}
