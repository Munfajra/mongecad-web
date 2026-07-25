package monge.input.axo.lines


import utils.System
import androidx.compose.ui.geometry.Offset
import model.ConstructionModifier
import model.classes.AxoOverlayLine
import monge.input.axo.AxoRenderBasis
import monge.input.axo.points.screenToAxoOverlayLocal
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import utils.allocIndex


fun Offset.normalizedOrNull(): Offset? {
    val len = getDistance()
    return if (len < 1e-6f) null else this / len
}

fun perpendicular2D(v: Offset): Offset =
    Offset(-v.y, v.x)
private fun projectPudorysDirectionToAxoOverlay(
    dir: Offset,
    basis: AxoRenderBasis
): Offset {
    return basis.ex * dir.x + basis.ey * dir.y
}

private fun projectNarysDirectionToAxoOverlay(
    dir: Offset,
    basis: AxoRenderBasis
): Offset {
    return basis.ex * dir.x + basis.ez * dir.y
}

private fun projectBokorysDirectionToAxoOverlay(
    dir: Offset,
    basis: AxoRenderBasis
): Offset {
    return basis.ey * dir.x + basis.ez * dir.y
}
fun resolveOverlayReferenceDirectionAxo(state: MongeState): Offset? {
    val basis = state.basis ?: return null

    val dirProjected =
        when {
            state.selectedLineForParallelPudorys != null -> {
                projectPudorysDirectionToAxoOverlay(
                    state.selectedLineForParallelPudorys!!.direction,
                    basis
                )
            }

            state.selectedSegmentForParallelPudorys != null -> {
                val seg = state.selectedSegmentForParallelPudorys!!
                val dir = Offset(seg.end.x,seg.end.y) - Offset(seg.start.x,seg.start.y)
                projectPudorysDirectionToAxoOverlay(dir, basis)
            }

            state.selectedLineForParallelNarys != null -> {
                projectNarysDirectionToAxoOverlay(
                    state.selectedLineForParallelNarys!!.direction,
                    basis
                )
            }

            state.selectedSegmentForParallelNarys != null -> {
                val seg = state.selectedSegmentForParallelNarys!!
                val dir = Offset(seg.end.x,seg.end.z) - Offset(seg.start.x,seg.start.z)
                projectNarysDirectionToAxoOverlay(dir, basis)
            }

            state.selectedLineForParallelBokorys != null -> {
                projectBokorysDirectionToAxoOverlay(
                    state.selectedLineForParallelBokorys!!.direction,
                    basis
                )
            }

            state.selectedSegmentForParallelBokorys != null -> {
                val seg = state.selectedSegmentForParallelBokorys!!
                val dir = Offset(seg.end.y,seg.end.z) - Offset(seg.start.y,seg.start.z)
                projectBokorysDirectionToAxoOverlay(dir, basis)
            }
            state.selectedSegmentForParallelAxo!= null ->{
             val seg = state.selectedSegmentForParallelAxo!!
             Offset(seg.end.x,seg.end.y) - Offset(seg.start.x,seg.start.y)
            }
            state.selectedSegmentForParallelAO!= null ->{
                val seg = state.selectedSegmentForParallelAO!!
                Offset(seg.end.x,seg.end.y) - Offset(seg.start.x,seg.start.y)
            }
            state.selectedLineForParallelAxo!= null ->{
                val line = state.selectedLineForParallelAxo!!
             line.dir
            }
            state.selectedLineForParallelAO!= null ->{
                val line = state.selectedLineForParallelAO!!

              line.dir
            }

            else -> null
        }

    return dirProjected?.normalizedOrNull()
}
fun handleOverlayLineAxo(state: MongeState) {
    when (state.constructionModifier) {
        ConstructionModifier.PARALLEL,
        ConstructionModifier.ORTHOGONAL -> {
            handleOPOverlayLineAxo(state)
        }

        ConstructionModifier.NONE -> {
            val basis = state.basis ?: return
            if (state.pendingPoint1 == null) {
                val p1 = state.snappedPointLogical
                    ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
                state.pendingPoint1 = p1
                updateConstructionInfo(state)
            } else {
                val p1 = state.pendingPoint1
                val p2 = state.snappedPointLogical
                    ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

                if (p1 != null) {
                    val dir = Offset(p2.x - p1.x, p2.y - p1.y)

                    val axoOverlayLine = AxoOverlayLine(
                        p = p1,
                        dir = dir,
                        color = state.currentHelpLineStyleSettings.color,
                        creationIndex = allocIndex(state),
                        lineWidth = state.currentHelpLineStyleSettings.strokeWidth,
                        lineStyle = state.currentHelpLineStyleSettings.style
                    )

                    state.pendingAOLine = axoOverlayLine
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    state.isNameConfirmed = false
                    setProjectionPhase("ao_line_naming", state)
                }
            }
        }
    }
}
fun handleOPOverlayLineAxo(state: MongeState) {
    val basis = state.basis ?: return


    if (!hasOverlayReference(state)) {
        pickOverlayReferenceFromCurrentHover(state)
        if (hasOverlayReference(state)) return
        return
    }

    val baseDir = resolveOverlayReferenceDirectionAxo(state)
    if (baseDir == null){
        println("posralo se to a nemám dir")
        return    }

    val finalDir = when (state.constructionModifier) {
        ConstructionModifier.PARALLEL -> baseDir
        ConstructionModifier.ORTHOGONAL -> perpendicular2D(baseDir).normalizedOrNull() ?: return
        else -> return
    }

    val point = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

    val axoOverlayLine = AxoOverlayLine(
        p = point,
        dir = finalDir,
        color = state.currentHelpLineStyleSettings.color,
        creationIndex = allocIndex(state),
        lineWidth = state.currentHelpLineStyleSettings.strokeWidth,
        lineStyle = state.currentHelpLineStyleSettings.style
    )

    state.pendingAOLine = axoOverlayLine
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    state.isNameConfirmed = false
    setProjectionPhase("ao_line_naming", state)

    clearOverlayLineReferenceSelection(state)
}
fun clearOverlayLineReferenceSelection(state: MongeState) {
    state.selectedLineForParallelPudorys = null
    state.selectedLineForParallelNarys = null
    state.selectedLineForParallelBokorys = null

    state.selectedSegmentForParallelPudorys = null
    state.selectedSegmentForParallelNarys = null
    state.selectedSegmentForParallelBokorys = null
    state.selectedLineForParallelAxo = null
    state.selectedLineForParallelAxoSecond = null
    state.selectedLineForParallelAO = null
    state.selectedLineForParallelAOSecond = null
    state.selectedSegmentForParallelAxo = null
    state.selectedSegmentForParallelAO = null
}
fun pickOverlayReferenceFromCurrentHover(state: MongeState) {
    state.selectedLineForParallelPudorys = state.snappedLinePudorys
    state.selectedLineForParallelNarys = state.snappedLineNarys
    state.selectedLineForParallelBokorys = state.snappedLineBokorys
    state.selectedLineForParallelAxo = state.snappedLineAxo
    state.selectedLineForParallelAO  = state.axoOverlayLines.firstOrNull {it.id == state.snappedAOLineId}

    state.selectedSegmentForParallelPudorys = state.snappedSegmentPudorys
    state.selectedSegmentForParallelNarys = state.snappedSegmentNarys
    state.selectedSegmentForParallelBokorys = state.snappedSegmentBokorys
    state.selectedSegmentForParallelAxo = state.snappedSegmentAxo
    state.selectedSegmentForParallelAO = state.axoOverlaySegments.firstOrNull {it.id == state.snappedAOSegmentId}
    updateConstructionInfo(state)
}
fun hasOverlayReference(state: MongeState): Boolean {
    return state.selectedLineForParallelPudorys != null ||
            state.selectedLineForParallelNarys != null ||
            state.selectedLineForParallelBokorys != null ||
            state.selectedSegmentForParallelPudorys != null ||
            state.selectedSegmentForParallelNarys != null ||
            state.selectedSegmentForParallelBokorys != null ||
            state.selectedSegmentForParallelAO!= null ||
            state.selectedSegmentForParallelAxo!= null ||
            state.selectedLineForParallelAO!= null ||
             state.selectedLineForParallelAxo!= null
}
