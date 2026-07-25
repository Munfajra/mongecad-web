package ui.planeUI.toolbar.rightDescriptionBar

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.Mongeobjects
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.rightDescriptionBar.*


@Composable
fun SelectionInfoPlane(state: MongeState) {
    val listState = rememberLazyListState()
    val ui = SettingsManager.current.UIscale/75f
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12*ui.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4*ui.dp)
        ) {  item {
            if (state.projectionPhase in listOf("pudorys_start","narys_start","")&& state.drawobjects == Mongeobjects.NONE) {
                if (!state.selectedCone.isNotEmpty() && !state.selectedCylinder.isNotEmpty() && !state.selectedPolygons.isNotEmpty() && !state.selectedSpheres3D.isNotEmpty()) {
                    // 🟢 0. Pomocný bod (globální)
                    aidPointEdit(state)
                    // 🟢 1. Bod (půdorys/nárys → Point3D)
                    pointEdit(state)
                    // 🔵 2. Přímka
                    lineEdit(state)
                    // 🔴 3. Úsečka
                    segmentEdit(state)
                    // 🟢 4. Kužsečka
                    conicEdit(state)
                    // 🟢 6. Kružnice
                    circleEdit(state)
                    arcEdit(state)
                    curveEdit(state)

                }

                // 🟣 10. POLYGONS
                polygonEdit(state)
                planePolygonEdit(state)
                val hasSelection =hasSelection(state)
                if (!hasSelection) {
                    InspectorEmptyState(state)
                } }
                else
                {
                    InspectorEmptyState(state)
                }

        }
    }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(8*ui.dp)
            )
        }
    }
