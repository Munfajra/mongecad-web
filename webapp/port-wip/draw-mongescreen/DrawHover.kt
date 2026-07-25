package draw.mongescreen

import model.Mongeobjects
import model.classes.PlaneTraceBokorys
import model.classes.PlaneTraceNarys
import model.classes.PlaneTracePudorys
import state.MongeState

fun handleHoverDetection(state: MongeState) {

    state.hoveredTraceNarys = null
    state.hoveredTracePudorys = null
    state.hoveredLineNarys = null
    state.hoveredLinePudorys = null
    state.hoveredLineBokorys = null
    state.hoveredTraceBokorys = null
    state.hoveredAOLineId = null
    state.hoveredAOSegId = null

    when {state.drawobjects == Mongeobjects.NONE -> {

         when (val sn = state.snappedLineNarys) {
             is PlaneTraceNarys -> {
                 val trN = sn
                 state.hoveredTraceNarys = trN

                 val trP = state.lineTracesPudorys.firstOrNull { it.parentId == trN.parentId }
                 state.hoveredTracePudorys = trP
                 val trB = state.lineTracesBokorys.firstOrNull {it.parentId == trN.parentId}
                 state.hoveredTraceBokorys = trB

                 state.hoveredLineBokorys = trB
                 // aby se obarvila i druhá stopa v půdorysu
                 state.hoveredLinePudorys = trP

                 // a aby se obarvila i ta aktuální v nárysu (pokud používáš hoveredLineNarys)
                 state.hoveredLineNarys = trN
             }

             null -> {
             }

             else -> {
                 state.hoveredLineNarys = sn
             }
         }

            state.hoveredSegmentNarys = state.snappedSegmentNarys

            when (val sp = state.snappedLinePudorys) {
                is PlaneTracePudorys -> {
                    val trP = sp
                    state.hoveredTracePudorys = trP

                    val trN = state.lineTracesNarys.firstOrNull { it.parentId == trP.parentId }
                    state.hoveredTraceNarys = trN
                    val trB = state.lineTracesBokorys.firstOrNull {it.parentId == trP.parentId}
                    state.hoveredTraceBokorys = trB
                    state.hoveredLineBokorys = trB
                    state.hoveredLineNarys = trN
                    state.hoveredLinePudorys = trP
                }
                null -> {
                }
                else -> {
                    state.hoveredLinePudorys = sp
                }
            }
        when (val sp = state.snappedLineBokorys) {
            is PlaneTraceBokorys -> {
                val trB = sp
                state.hoveredTraceBokorys = trB

                val trN = state.lineTracesNarys.firstOrNull { it.parentId == trB.parentId }
                state.hoveredTraceNarys = trN
                val trP = state.lineTracesPudorys.firstOrNull {it.parentId == trB.parentId}
                state.hoveredTracePudorys= trP
                state.hoveredLineBokorys = trB
                state.hoveredLineNarys = trN
                state.hoveredLinePudorys = trP
            }
            null -> {
            }
            else -> {
                state.hoveredLineBokorys = sp
            }
        }



        state.hoveredSegmentBokorys = state.snappedSegmentBokorys
            state.hoveredSegmentPudorys = state.snappedSegmentPudorys
        state.hoveredAOLineId = state.snappedAOLineId
        state.hoveredAOSegId = state.snappedAOSegmentId
        }
    }
}