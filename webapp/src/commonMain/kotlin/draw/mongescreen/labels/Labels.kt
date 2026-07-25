package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import model.ProjectionMode
import monge.input.axo.AxoRenderBasis
import state.MongeState

/** Popisky nad canvasem pro konstrukce dostupné ve webové verzi. */
@Composable
fun Labels(state: MongeState, snappedPointLogical: Offset?, basis: AxoRenderBasis? = null) {
    when (state.projectionMode) {
        ProjectionMode.MONGE -> {
            LabelsPudorysPoints(state)
            LabelsNarysPoints(state)
            LabelsNarysLines(state)
            LabelsPudorysLines(state)
            LabelsNarysSegments(state, includeHelp = state.showConstruction.value)
            LabelsPudorysSegments(state, includeHelp = state.showConstruction.value)
            if (state.showConstruction.value) {
                LabelsAidPoints(state)
            }
        }

        ProjectionMode.PLANE, ProjectionMode.KOTO -> {
            LabelsPudorysPoints(state)
            LabelsPudorysLines(state)
            LabelsPudorysSegments(state, includeHelp = state.showConstruction.value)
            if (state.showConstruction.value) {
                LabelsAidPoints(state)
            }
        }

        ProjectionMode.AXO -> Unit
    }
}
