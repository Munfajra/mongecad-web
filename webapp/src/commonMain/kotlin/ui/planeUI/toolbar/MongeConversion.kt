package ui.planeUI.toolbar

import state.MongeState
import ui.mongeui.toolbar.rightDescriptionBar.setNarysLineProjectionVisible
import ui.mongeui.toolbar.rightDescriptionBar.setPudorysLineProjectionVisible

/*
 * Srovnání výkresu při převodu z roviny do Mongeova promítání.
 *
 * Desktopová `AxoConversionNormalize.kt` (3 281 řádků) řeší hlavně převod
 * do axonometrie, kterou web nemá – sem je vytažené jen to, co potřebuje
 * převod PLANE → Monge.
 */
fun normalizeStateForMongeConversion(state: MongeState) {
    normalizeLinesForMonge(state)
}

fun normalizeLinesForMonge(state: MongeState) {
    state.lines3D.forEach { line ->
        setPudorysLineProjectionVisible(state, line, true)
        setNarysLineProjectionVisible(state, line, true)
    }

    state.lines3DPudorys.forEach { it.showInAxo = true; it.showInAxoInitial = true }
    state.lines3DNarys.forEach { it.showInAxo = true; it.showInAxoInitial = true }

    state.pointsPudorys.forEach { if (it.isProjectedLine) { it.showInAxo = true; it.showInAxoInitial = true } }
    state.pointsNarys.forEach { if (it.isProjectedLine) { it.showInAxo = true; it.showInAxoInitial = true } }
}

