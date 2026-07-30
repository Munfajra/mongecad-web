package ui.mongeui.toolbar.rightDescriptionBar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import model.LocalMongeColors
import model.ProjectionMode
import monge.input.intersections.applyIntersectionGroupColor
import monge.input.intersections.applyIntersectionGroupLineStyle
import monge.input.intersections.applyIntersectionGroupShowInAxo
import monge.input.intersections.applyIntersectionGroupStrokeWidth
import monge.input.intersections.intersectionGroupColor
import monge.input.intersections.intersectionGroupLineStyle
import monge.input.intersections.intersectionGroupProjectionVisible
import monge.input.intersections.intersectionGroupStrokeWidth
import monge.input.intersections.selectedIntersectionGroup
import model.classes.IntersectionPartKind
import serialization.SettingsManager
import serialization.commitSnapshot
import state.MongeState
import ui.colorpicker.ColorPickerDropdown
import ui.components.MongeDivider
import ui.components.MongeInspectorPropertyRow
import ui.mongeui.toolbar.SkikoButton
import utils.deleteIntersectionGroup

@Composable
fun intersectionGroupEdit(state: MongeState) {
    val group = selectedIntersectionGroup(state) ?: return
    val uiScale = SettingsManager.current.UIscale / 75f
    val ui = remember(uiScale) { UiScale(uiScale) }
    var pendingColor by remember(group.id, intersectionGroupColor(state, group)) {
        mutableStateOf(intersectionGroupColor(state, group))
    }
    var width by remember(group.id, intersectionGroupStrokeWidth(state, group)) {
        mutableStateOf(intersectionGroupStrokeWidth(state, group))
    }
    val hasLineStyleParts = group.parts.any { it.kind != IntersectionPartKind.POINT3D }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ui.dp(10f)),
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f))
    ) {
        Text(
            text = group.displayName,
            fontSize = ui.sp(14f),
            fontWeight = FontWeight.SemiBold,
            color = LocalMongeColors.current.text
        )

        MongeDivider()

        MongeInspectorPropertyRow("Barva:") {
            ColorPickerDropdown(
                selectedColor = pendingColor,
                onColorPreview = { pendingColor = it },
                onColorConfirm = { color ->
                    pendingColor = color
                    applyIntersectionGroupColor(state, group, color)
                    commitSnapshot(state)
                }
            )
        }

        MongeDivider()

        MongeInspectorPropertyRow("Šířka:") {
            WidthEditor(
                value = width,
                onValueChange = {
                    width = it
                    applyIntersectionGroupStrokeWidth(state, group, it)
                },
                state = state
            )
        }

        if (hasLineStyleParts) {
            MongeDivider()

            MongeInspectorPropertyRow("Styl:") {
                LineStyleSelector(
                    current = intersectionGroupLineStyle(state, group),
                    onStyleChange = {
                        applyIntersectionGroupLineStyle(state, group, it)
                        commitSnapshot(state)
                    }
                )
            }
        }

        if (state.projectionMode == ProjectionMode.AXO) {
            MongeDivider()
            MongeInspectorPropertyRow(label = "Průměty:") {
                ProjectionVisibilityToggleStrip(
                    ui = ui,
                    ProjectionVisibilityToggleItem("A", intersectionGroupProjectionVisible(state, group, "A")) {
                        applyIntersectionGroupShowInAxo(state, group, "A", it)
                        commitSnapshot(state)
                    },
                    ProjectionVisibilityToggleItem("P", intersectionGroupProjectionVisible(state, group, "P")) {
                        applyIntersectionGroupShowInAxo(state, group, "P", it)
                        commitSnapshot(state)
                    },
                    ProjectionVisibilityToggleItem("N", intersectionGroupProjectionVisible(state, group, "N")) {
                        applyIntersectionGroupShowInAxo(state, group, "N", it)
                        commitSnapshot(state)
                    },
                    ProjectionVisibilityToggleItem("B", intersectionGroupProjectionVisible(state, group, "B")) {
                        applyIntersectionGroupShowInAxo(state, group, "B", it)
                        commitSnapshot(state)
                    }
                )
            }
        }

        MongeDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SkikoButton(
                width = ui.dp(140f),
                height = ui.dp(34f),
                onClick = { deleteIntersectionGroup(state, group.id) }
            ) {
                Text(
                    "Smazat",
                    fontSize = ui.sp(12f)
                )
            }
        }
    }
}
