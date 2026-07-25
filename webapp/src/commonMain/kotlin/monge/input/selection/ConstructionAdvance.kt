package monge.input.selection

import androidx.compose.ui.geometry.Offset
import model.DrawingModeMonge
import model.Mongeobjects
import model.ProjectionMode
import state.MongeState

/**
 * Klik na plátně nejen označí objekt, ale zároveň (jako vedlejší efekt handleClick/handleClickAxo)
 * posune rozpracovanou konstrukci (šroubovice, rotační plocha, jehlan...) do další fáze. Výběr
 * v ObjectListu mění jen selectedLines/selectedPoints/... seznamy, ten "druhý krok" navíc chybí –
 * proto ho po přidání do výběru zavoláme ručně, ať konstrukce reaguje stejně jako na klik do plátna.
 */
fun advancePendingLineOrPointConstruction(state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.HELIX -> Unit
        Mongeobjects.RULED_SURFACE -> Unit
        Mongeobjects.SOLID_OF_REVOLUTION -> {
            when (state.mongeMode) {
                DrawingModeMonge.PUDORYS -> Unit
                DrawingModeMonge.NARYS -> Unit
            }
        }
        else -> {}
    }
}

/** Stejný "druhý krok navíc" jako výše, ale pro výběr kuželosečky (podstava kužele/válce, poledník rotační plochy). */
fun advancePendingConicConstruction(state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.RULED_SURFACE -> Unit
        Mongeobjects.SOLID_OF_REVOLUTION -> {
            when (state.mongeMode) {
                DrawingModeMonge.PUDORYS -> Unit
                DrawingModeMonge.NARYS -> Unit
            }
        }
        Mongeobjects.CONE -> Unit
        // CONE/CYLINDER web nemá – kvadriky jsou vyřazená featura.
        else -> {}
    }
}

/** Stejný "druhý krok navíc" jako výše, ale pro výběr podstavného mnohoúhelníku (jehlan/hranol). */
fun advancePendingPolygonConstruction(state: MongeState) {
    val axoConstruction = state.projectionMode == ProjectionMode.AXO
    when (state.drawobjects) {
        Mongeobjects.PYRAMID -> Unit
        // logicalCursor se v první fázi (výběr podstavy) nepoužívá, jen ve druhé (výška).
        Mongeobjects.PRISM -> Unit
        else -> {}
    }
}
