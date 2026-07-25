package ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import draw.mongescreen.labels.clearSelection
import model.ConstructionModifier
import model.DrawingModeMonge
import model.Mongeobjects
import model.ProjectionType
import monge.input.ConicArcs.associated.arcHyperbolaNarys3DSkipSecond
import monge.input.ConicArcs.associated.arcHyperbolaPudorys3DSkipSecond
import monge.input.ConicArcs.single.arcHyperbolaNarysSkipSecond
import monge.input.ConicArcs.single.arcHyperbolaPudorysSkipSecond
import monge.input.curves.finalizeCurve3DOnEnter
import monge.input.curves.finalizeNarysCurveOnEnter
import monge.input.curves.finalizePudorysCurveOnEnter
import monge.input.lines.directionHandlers.lines.revertLineSelectionPhaseOnModifierOff
import monge.input.lines.toggleAxisVisibilityForMode
import monge.input.planes.completeSpecialCasePlaneNarys
import monge.input.planes.completeSpecialCasePlanePudorys
import serialization.redo
import serialization.undo
import state.MongeState
import ui.mongeui.toolbar.isObjectEnabled
import ui.mongeui.toolbar.mongeObjectToolOrder
import ui.mongeui.toolbar.rightDescriptionBar.triggerProjectionCompletionIfAvailable
import ui.mongeui.toolbar.selectMongeObjectTool
import ui.mongeui.toolbar.updateConstructionInfo
import utils.deleteSelected

/**
 * Globální klávesové zkratky.
 *
 * Portováno z desktopového `handleGlobalKey` v Main.kt. Rozdíly:
 *  – odpadají větve pro AXO/PLANE/KOTO a nástroje, které web nemá,
 *  – místo příkazů menu (Ctrl+N/O/S/E/P) jsou navázané akce ze skupiny
 *    „Soubor“ v liště; Ctrl+E/P (export, tisk) web nemá vůbec,
 *  – accelerátor je Ctrl i Cmd, aby to fungovalo i na Macu bez detekce OS.
 */
fun handleGlobalKey(
    e: KeyEvent,
    state: MongeState,
    onOpen: () -> Unit,
    onSave: () -> Unit,
): Boolean {
    val enabled2 = (state.projectionPhase == "pudorys_start" || state.projectionPhase == "narys_start") &&
            (state.drawobjects == Mongeobjects.POINTS || state.drawobjects == Mongeobjects.LINES ||
                    state.drawobjects == Mongeobjects.PLANE || state.drawobjects == Mongeobjects.SEGMENTS ||
                    state.drawobjects == Mongeobjects.ELLIPSE || state.drawobjects == Mongeobjects.HYPERBOLA ||
                    state.drawobjects == Mongeobjects.PARABOLA || state.drawobjects == Mongeobjects.CIRCLE ||
                    state.drawobjects == Mongeobjects.NONE)

    val enabled3 = when (state.drawobjects) {
        Mongeobjects.PLANE, Mongeobjects.HYPERBOLA, Mongeobjects.PARABOLA,
        Mongeobjects.ELLIPSE, Mongeobjects.CONICARC -> false
        else -> true
    }

    fun isAccelPressed(): Boolean = e.isCtrlPressed || e.isMetaPressed
    fun KeyEvent.isEnter() = key == Key.Enter || key == Key.NumPadEnter

    /**
     * Číslice se čtou i přes `utf16CodePoint`, protože na české klávesnici
     * jsou v horní řadě diakritické znaky (ě, š, č, ř, ž) a `key` by je
     * nerozpoznalo. Stejné mapování jako na desktopu.
     */
    fun KeyEvent.isPhysicalDigit(digit: Int): Boolean {
        val char = utf16CodePoint.takeIf { it > 0 }?.toChar()
        return when (digit) {
            1 -> key == Key.One || key == Key.NumPad1 || char == '1' || char == '+'
            2 -> key == Key.Two || key == Key.NumPad2 || char == '2' || char == 'ě' || char == 'Ě'
            3 -> key == Key.Three || key == Key.NumPad3 || char == '3' || char == 'š' || char == 'Š'
            4 -> key == Key.Four || key == Key.NumPad4 || char == '4' || char == 'č' || char == 'Č'
            5 -> key == Key.Five || key == Key.NumPad5 || char == '5' || char == 'ř' || char == 'Ř'
            6 -> key == Key.Six || key == Key.NumPad6 || char == '6' || char == 'ž' || char == 'Ž'
            else -> false
        }
    }

    if (
        e.type == KeyEventType.KeyUp &&
        (e.key == Key.CtrlLeft || e.key == Key.CtrlRight || e.key == Key.MetaLeft || e.key == Key.MetaRight)
    ) {
        state.isCtrlPressed = false
        return true
    }

    if (e.type != KeyEventType.KeyDown) return false

    // Když je otevřený modální dialog, nech klávesy propadnout do jeho polí
    // a nespouštěj globální zkratky.
    return if (!state.isTextEditing && state.openDialogCount == 0) when {

        // undo / redo
        isAccelPressed() && e.key == Key.Z -> { undo(state); true }
        isAccelPressed() && e.key == Key.Y -> { redo(state); true }

        // soubor
        isAccelPressed() && e.key == Key.O -> { resetKeyboardModifiers(state); onOpen(); true }
        isAccelPressed() && e.key == Key.S -> { resetKeyboardModifiers(state); onSave(); true }

        e.key == Key.Delete -> { deleteSelected(state); true }
        e.key == Key.Escape -> {
            clearSelection(state)
            resetStavu(state)
            state.drawobjects = Mongeobjects.NONE
            true
        }

        e.key == Key.R -> { state.repeatCons.value = !state.repeatCons.value; true }
        e.key == Key.A && !isAccelPressed() -> { toggleAxisVisibilityForMode(state); true }
        e.key == Key.W && !isAccelPressed() -> { toggleConstructionModifier(state, ConstructionModifier.ORTHOGONAL); true }
        e.key == Key.Q && !isAccelPressed() -> { toggleConstructionModifier(state, ConstructionModifier.PARALLEL); true }

        // přepnutí půdorys / nárys
        e.key == Key.Tab -> {
            state.mongeMode = if (state.mongeMode == DrawingModeMonge.NARYS) DrawingModeMonge.PUDORYS
            else DrawingModeMonge.NARYS
            resetStavu(state, resetConstructionModifier = false)
            true
        }

        // typ projekce: Ctrl+1..3
        isAccelPressed() && e.isPhysicalDigit(1) -> {
            state.projekcnityp = ProjectionType.SINGLE
            resetStavu(state)
            updateConstructionInfo(state)
            true
        }
        isAccelPressed() && e.isPhysicalDigit(2) -> {
            if (enabled2) {
                state.projekcnityp = ProjectionType.ASSOCIATED
                resetStavu(state)
                updateConstructionInfo(state)
                true
            } else false
        }
        isAccelPressed() && e.isPhysicalDigit(3) -> {
            if (enabled3) {
                state.projekcnityp = ProjectionType.AUXILIARY
                resetStavu(state)
                updateConstructionInfo(state)
                true
            } else false
        }

        // výběr nástroje: 1-6
        !isAccelPressed() && e.isPhysicalDigit(1) -> selectObjectToolByShortcut(state, 1)
        !isAccelPressed() && e.isPhysicalDigit(2) -> selectObjectToolByShortcut(state, 2)
        !isAccelPressed() && e.isPhysicalDigit(3) -> selectObjectToolByShortcut(state, 3)
        !isAccelPressed() && e.isPhysicalDigit(4) -> selectObjectToolByShortcut(state, 4)
        !isAccelPressed() && e.isPhysicalDigit(5) -> selectObjectToolByShortcut(state, 5)
        !isAccelPressed() && e.isPhysicalDigit(6) -> selectObjectToolByShortcut(state, 6)

        e.key == Key.N -> { state.skipnaming = !state.skipnaming; true }
        e.key == Key.Spacebar -> triggerProjectionCompletionIfAvailable(state)

        e.key == Key.CtrlLeft || e.key == Key.CtrlRight -> { state.isCtrlPressed = true; true }

        // Enter dokončuje rozpracované konstrukce
        e.isEnter() -> {
            if (state.drawobjects == Mongeobjects.CONICARC ||
                state.drawobjects == Mongeobjects.PLANE ||
                state.drawobjects == Mongeobjects.CONICARCAS ||
                state.drawobjects == Mongeobjects.CURVE
            ) {
                when (state.mongeMode) {
                    DrawingModeMonge.PUDORYS -> when (state.projectionPhase) {
                        "pudorys_hyp_second" -> { arcHyperbolaPudorysSkipSecond(state); true }
                        "pudorys_hyp3d_a2", "pudorys_hyp3d_b2" -> { arcHyperbolaPudorys3DSkipSecond(state); true }
                        "pudorys_curve_pick" -> { finalizePudorysCurveOnEnter(state); true }
                        "curve3d_pick_points" -> { finalizeCurve3DOnEnter(state); true }
                        "plane_trace_pudorys_special_direction" -> { completeSpecialCasePlanePudorys(state); true }
                        else -> false
                    }

                    DrawingModeMonge.NARYS -> when (state.projectionPhase) {
                        "narys_hyp_second" -> { arcHyperbolaNarysSkipSecond(state); true }
                        "narys_hyp3d_a2", "narys_hyp3d_b2" -> { arcHyperbolaNarys3DSkipSecond(state); true }
                        "narys_curve_pick" -> { finalizeNarysCurveOnEnter(state); true }
                        "curve3d_pick_points" -> { finalizeCurve3DOnEnter(state); true }
                        "plane_trace_narys_special_direction" -> { completeSpecialCasePlaneNarys(state); true }
                        else -> false
                    }
                }
            } else false
        }

        else -> false
    }
    else false
}

fun resetKeyboardModifiers(state: MongeState) {
    state.isCtrlPressed = false
    state.isShiftPressed = false
}

/** Vybere nástroj v hlavním toolbaru podle číselné zkratky (1-based). */
private fun selectObjectToolByShortcut(state: MongeState, index: Int): Boolean {
    val obj = mongeObjectToolOrder.getOrNull(index - 1) ?: return false
    if (!isObjectEnabled(obj, state)) return false
    selectMongeObjectTool(state, obj)
    return true
}

private fun toggleConstructionModifier(state: MongeState, target: ConstructionModifier) {
    val isSelected = state.constructionModifier == target
    if (isSelected) {
        if (state.drawobjects == Mongeobjects.TRANSPARALLEL || state.drawobjects == Mongeobjects.TRANSORTH) {
            state.drawobjects = Mongeobjects.NONE
            resetStavu(state)
        }
        state.constructionModifier = ConstructionModifier.NONE
        revertLineSelectionPhaseOnModifierOff(state)
    } else {
        if (state.selectedLinesPudorys.isNotEmpty()) {
            state.selectedLineForParallelPudorys = state.selectedLinesPudorys.first()
            state.selectedLineForParallelPlanePudorys = state.selectedLinesPudorys.first()
        }
        state.constructionModifier = target
        if (state.drawobjects == Mongeobjects.TRANSPARALLEL || state.drawobjects == Mongeobjects.TRANSORTH) {
            state.drawobjects = when (target) {
                ConstructionModifier.PARALLEL -> Mongeobjects.TRANSPARALLEL
                else -> Mongeobjects.TRANSORTH
            }
        }
    }
    updateConstructionInfo(state)
}
