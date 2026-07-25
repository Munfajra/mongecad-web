package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import draw.mongescreen.previews.tools.DistanceOrKotaPreviewLabel
import model.ProjectionMode
import monge.input.axo.AxoRenderBasis
import state.MongeState

@Composable
fun Labels(state: MongeState,snappedPointLogical: Offset?,basis: AxoRenderBasis? = null){
    DistanceOrKotaPreviewLabel(state, snappedPointLogical)

    when (state.projectionMode) {
        ProjectionMode.MONGE -> {
            LabelsPudorysPoints(state)
            LabelsNarysPoints (state)
           LabelsNarysLines (state)
             LabelsPudorysLines (state)
             LabelsNarysSegments(state, includeHelp = state.showConstruction.value)
             LabelsPudorysSegments(state, includeHelp = state.showConstruction.value)

             if (state.showConstruction.value) {
                 LabelsAidPoints(state)
                 LabelsPudorysHelpLines (state)
                 LabelsNarysHelpLines (state)
             }
         }
        ProjectionMode.AXO ->{
            if (basis== null) return
            LabelsPudorysPoints(
                state = state,
                projector = { p ->
                    basis.origin +
                            basis.ex * p.x +
                            basis.ey * p.y
                }
            )
            LabelsBokorysPoints(
                state = state,
                projector = { p ->
                    basis.origin +
                            basis.ey * p.y +
                            basis.ez * p.z
                }
            )
            LabelsNarysPoints (state,
                projector = { p ->
                basis.origin +
                        basis.ex * p.x +
                        basis.ez * p.z
            })
            LabelsNarysLines (state, projector ={ l->
                basis.origin+
                        basis.ex*l.point.x+
                        basis.ez*l.point.z},
                traceProjector = { t ->
                    basis.origin +
                            basis.ex * t.point.x +
                            basis.ez * t.point.z
                })
            LabelsNarysSegments(
                state = state,
                includeHelp = false,
                projector = { x, z -> basis.origin + basis.ex * x + basis.ez * z }
            )

            LabelsPudorysLines (state, projector ={ l->
            basis.origin+
            basis.ex*l.point.x+
            basis.ey*l.point.y},
                traceProjector = { t ->
                    basis.origin +
                            basis.ex * t.point.x +
                            basis.ey * t.point.y
                })
            LabelsPudorysSegments(
                state = state,
                includeHelp = false,
                projector = { x, y -> basis.origin + basis.ex * x + basis.ey * y }
            )
            LabelsBokorysLines (state, projector ={ l->
                basis.origin+
                        basis.ey*l.point.y+
                        basis.ez*l.point.z},
                traceProjector = { t ->
                    basis.origin +
                            basis.ey * t.point.y +
                            basis.ez * t.point.z
                })
            LabelsBokorysSegments(
                state = state,
                projector = { y, z -> basis.origin + basis.ey * y + basis.ez * z }
            )
            LabelsOverlayPoints(state)
            LabelsOverlayLines(state)
            LabelsOverlaySegments(state)
            LabelsAxoLines(state)
            LabelsAxoSegments(state)


            LabelsAxoPoints(state)
        }
         ProjectionMode.PLANE, ProjectionMode.KOTO -> {
             LabelsPudorysPoints(state)
             LabelsPudorysLines (state)
             LabelsPudorysSegments(state, includeHelp = state.showConstruction.value)

         if (state.showConstruction.value) {
             LabelsAidPoints(state)
             LabelsPudorysHelpLines (state)
         }
        }
    }

}
