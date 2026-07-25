package monge.input.axo.lines

import utils.System
import androidx.compose.ui.geometry.Offset
import model.ConstructionModifier
import model.classes.Line3DProjectionAxo
import model.classes.Point3DAxo
import monge.input.axo.points.screenToAxoOverlayLocal
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import utils.allocIndex


fun handleAxoLine(state: MongeState) {
    when (state.constructionModifier) {
        ConstructionModifier.PARALLEL,
        ConstructionModifier.ORTHOGONAL -> {
            handleOPLineAxo(state)
        }

        ConstructionModifier.NONE -> {
            val basis = state.basis ?: return
            if (state.pendingAxoPoint1 == null) {
                val p = state.snappedPointLogical
                    ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

                val p1 = Point3DAxo(
                    x = p.x,
                    y = p.y,
                )
                state.pendingAxoPoint1 = p1
                updateConstructionInfo(state)
            } else {
                val p1 = state.pendingAxoPoint1
                val p = state.snappedPointLogical
                    ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
                val p2 = Point3DAxo(
                    x = p.x,
                    y = p.y,
                )

                if (p1 != null) {
                    val dir = Offset(p2.x - p1.x, p2.y - p1.y)

                    val axoLine = Line3DProjectionAxo(
                        p = p1,
                        dir = dir,
                        localColor = state.currentLineStyleSettings.color,
                        creationIndex = allocIndex(state),
                        localStrokeWidth = state.currentLineStyleSettings.strokeWidth,
                        localLineStyle = state.currentLineStyleSettings.style
                    )

                    state.pendingAxoLine = axoLine
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    state.isNameConfirmed = false
                    setProjectionPhase("axo_line_naming", state)
                }
            }
        }
    }
}
fun handleOPLineAxo(state: MongeState) {
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

    val p = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
    val point = Point3DAxo(
        x = p.x,
        y = p.y,
    )

    val axoLine = Line3DProjectionAxo(
        p = point,
        dir = finalDir,
        localColor = state.currentLineStyleSettings.color,
        creationIndex = allocIndex(state),
        localStrokeWidth = state.currentLineStyleSettings.strokeWidth,
        localLineStyle = state.currentLineStyleSettings.style
    )

    state.pendingAxoLine = axoLine
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    state.isNameConfirmed = false
    setProjectionPhase("axo_line_naming", state)

    clearOverlayLineReferenceSelection(state)
}