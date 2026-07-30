package ui.mongeui.toolbar

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.LocalMongeColors
import model.MongeColorsState
import state.MongeState
import dialogs.batchinput.BatchInputLauncherToolbar
import ui.components.MongeToolbarGroup
import ui.components.TooltipArea
import ui.theme.LocalMongeDimens

/**
 * Horní lišta. Struktura skupin je stejná jako na desktopu, jen bez těch,
 * které web nemá:
 *   – "Náhled" míří na vestavěný WebGL náhled, ne na OpenGL okno
 *   – z "Celé objekty" chybí šroubovice
 *   – "Převést" (přepnutí do jiného projekčního módu)
 *
 * Souborové a aplikační akce jsou ve společném webovém menu vlevo, aby
 * konstrukční pás zůstal přehledný.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MongeToolbar(
    state: MongeState,
    showDialog: MutableState<Boolean>,
    showParamDialog: MutableState<Boolean>,
    showPlaneDialog: MutableState<Boolean>,
    buttonsize: Dp
) {
    val colors = LocalMongeColors.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val dimens = LocalMongeDimens.current
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
                                    scrollState.scrollBy(deltaY * 50f)
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
            MongeToolbarGroup(title = "Náhled") {
                Preview3DButton(state = state, buttonsize = buttonsize)
            }

            MongeToolbarGroup(title = "Zadání") {
                BatchInputLauncherToolbar(
                    showDialog = showDialog,
                    showParamDialog = showParamDialog,
                    showPlaneDialog = showPlaneDialog,
                    state = state,
                    buttonsize = buttonsize
                )
            }

            MongeToolbarGroup(title = "Konstrukce") {
                ObjectSelectionRow(
                    selectedObject = state.drawobjects,
                    onSelect = { state.drawobjects = it },
                    state = state,
                    buttonsize = buttonsize
                )
            }

            MongeToolbarGroup(title = "Směr") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ParallelToggleButton(state, buttonsize)
                    OrthogonalToggleButton(state, buttonsize)
                }
            }

            MongeToolbarGroup(title = "Průměty / styl") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.xs),
                    horizontalAlignment = Alignment.Start
                ) {
                    ModeToggleButtonNarys(state, buttonsize)
                    ModeToggleButtonPudorys(state, buttonsize)
                }
                ProjectionModeSelector(
                    state = state,
                    selected = state.projekcnityp,
                    onSelect = { state.projekcnityp = it },
                    buttonsize = buttonsize
                )
            }

            MongeToolbarGroup(title = "Celé objekty") {
                Kvadriky(state, buttonsize)
                Telesa(state, buttonsize)
                SolidOfRevolution(state, buttonsize)
                RuledSurfaceButton(state, buttonsize)
                LiftToPlaneButton(buttonsize = buttonsize, state = state)
                IntersectionButton(buttonsize = buttonsize, state = state)
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

@Composable
fun UIline(
    colors: MongeColorsState
) {
    Spacer(modifier = Modifier.width(4.dp))
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(colors.base)
    )

    Spacer(modifier = Modifier.width(4.dp))
}
