package ui.colorpicker

import monge.input.intersections.handleIntersectionClick
import mongecad.web.generated.resources.Res
import mongecad.web.generated.resources.latinmodern_math
import org.jetbrains.compose.resources.Font
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import draw.mongescreen.labels.clearSelection
import serialization.canRedoNow
import serialization.canUndoNow
import serialization.redo
import serialization.undo
import model.DrawingModeMonge
import model.ProjectionMode
import model.axo.AxoMode
import model.classes.*
import serialization.SettingsManager
import monge.input.selection.toggleSelectionPlane
import state.MongeState
import ui.mongeui.toolbar.SkikoButton


@Composable
fun homeButton(state: MongeState, modifier: Modifier = Modifier ) {
    val uiS = SettingsManager.current.UIscale/75f
    Surface(
        color = Color.Transparent,
        modifier = modifier
    ) {
        SkikoButton(
            onClick = {
                state.isOffsetInitialized = false
                state.scale = 1f
            },
            modifier = Modifier.size(34f*uiS.dp),

        ) {
            Icon(
                painter = painterResource("icons/home.svg"),
                contentDescription = "Reset view",
                modifier = Modifier.size(32*uiS.dp)
            )
        }
    }
}
@Composable
fun undoButton(state: MongeState, modifier: Modifier = Modifier ) {
    val uiS = SettingsManager.current.UIscale/75f
    Surface(
        color =Color.Transparent,
        modifier = modifier
    ) {
        SkikoButton(
            onClick = {
                undo(state)
            },
            modifier = Modifier.size(34*uiS.dp),
            enabled = state.canUndoNow(),

            ) {
            Icon(
                painter = painterResource("icons/undo.svg"),
                contentDescription = "Reset view",
                modifier = Modifier.size(32*uiS.dp)
            )
        }
    }
}
@Composable
fun redoButton(state: MongeState, modifier: Modifier = Modifier ) {
    val uiS = SettingsManager.current.UIscale/75f
    Surface(
        color = Color.Transparent,
        modifier = modifier
    ) {
        SkikoButton(
            onClick = {
                redo(state)
            },
            modifier = Modifier.size(34*uiS.dp),
            enabled = state.canRedoNow(),

            ) {
            Icon(
                painter = painterResource("icons/redo.svg"),
                contentDescription = "Reset view",
                modifier = Modifier.size(32*uiS.dp)
            )
        }
    }
}
@Composable
fun ReferencePlanesToggleRow(state: MongeState, modifier: Modifier) {
    val uiS = SettingsManager.current.UIscale/75f
    val p1 = remember { makeReferencePudorysna() }
    val p2 = remember { makeReferenceNarysna() }
    val p3 = remember { makeReferenceBokorysna() }

    val selP1 = (state.selectedPlanes.any { it.id == PLANE_PUDORYS_ID }) || (state.selectedPlaneForCircle?.id == PLANE_PUDORYS_ID)
    val selP2 = state.selectedPlanes.any { it.id == PLANE_NARYS_ID }|| (state.selectedPlaneForCircle?.id == PLANE_NARYS_ID)
    val selP3 =  state.selectedPlanes.any {it.id == PLANE_BOKORYS_ID}|| (state.selectedPlaneForCircle?.id== PLANE_BOKORYS_ID)
    // Desktop bere font cestou z classpath; na webu jde přes compose resources.
    val greekFont = FontFamily(Font(Res.font.latinmodern_math))
    val greekSize = 20 * uiS.sp

    Column(verticalArrangement = Arrangement.spacedBy(3*uiS.dp)) {
        Surface(
            color = Color.Transparent,
            modifier = modifier
        ) {
            SkikoButton(
                onClick = {
                    when (state.projectionMode){
                        ProjectionMode.MONGE -> {state.mongeMode = DrawingModeMonge.PUDORYS}
                        ProjectionMode.AXO -> {state.axoMode = AxoMode.AXO_PUDORYS}
                        else -> {}
                    }
                    clearSelection(state)
                    toggleSelectionPlane(p1, state)
                    handleIntersectionClick(state)
                },
                isSelected = selP1,
                modifier = Modifier.size(34*uiS.dp),

                ) {
                Box(Modifier.size(32*uiS.dp), contentAlignment = Alignment.Center) {
                    Text("π", fontFamily = greekFont, fontSize = greekSize,
                        color = LocalContentColor.current, textAlign = TextAlign.Center)
                }
            }
        }
        if (state.projectionMode != ProjectionMode.KOTO) {
            Surface(
                color = Color.Transparent,
                modifier = modifier
            ) {
                SkikoButton(
                    onClick = {
                        when (state.projectionMode){
                            ProjectionMode.MONGE -> {state.mongeMode = DrawingModeMonge.NARYS}
                            ProjectionMode.AXO -> {state.axoMode = AxoMode.AXO_NARYS}
                            else -> {}
                        }
                        clearSelection(state)
                        toggleSelectionPlane(p2, state)
                        handleIntersectionClick(state)
                    },
                    isSelected = selP2,
                    modifier = Modifier.size(34*uiS.dp),

                    ) {
                    Box(Modifier.size(32*uiS.dp), contentAlignment = Alignment.Center) {
                        Text("ν", fontFamily = greekFont, fontSize = greekSize,
                            color = LocalContentColor.current, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (state.projectionMode == ProjectionMode.AXO){
            Surface(
                color = Color.Transparent,
                modifier = modifier
            ) {
                SkikoButton(
                    onClick = {
                        state.axoMode = AxoMode.AXO_BOKORYS
                        clearSelection(state)
                        toggleSelectionPlane(p3, state)
                        handleIntersectionClick(state)
                    },
                    isSelected = selP3,
                    modifier = Modifier.size(34*uiS.dp),

                    ) {
                    Box(Modifier.size(32*uiS.dp), contentAlignment = Alignment.Center) {
                        Text("μ", fontFamily = greekFont, fontSize = greekSize,
                            color = LocalContentColor.current, textAlign = TextAlign.Center)
                    }
                }
            }
        }


    }
}
