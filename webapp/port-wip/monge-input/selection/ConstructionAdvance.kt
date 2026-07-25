package monge.input.selection

import androidx.compose.ui.geometry.Offset
import model.DrawingModeMonge
import model.Mongeobjects
import model.ProjectionMode
import monge.input.helix.handleHelixObjectListSelection
import monge.input.ruledsurface.handleRuledSurfaceSelection
import monge.input.meridian.solidOfRevolutionNarys
import monge.input.meridian.solidOfRevolutionPudorys
import monge.input.quadrics.conicalsurface.handleConicalToolClick
import monge.input.quadrics.cylindricalsurface.handleCylindricalToolClick
import monge.input.quadrics.cylindricalsurface.handlePerpendicularCylinderClick
import monge.input.solids.handlePrismToolClick
import monge.input.solids.handlePyramidToolClick
import state.MongeState

/**
 * Klik na plátně nejen označí objekt, ale zároveň (jako vedlejší efekt handleClick/handleClickAxo)
 * posune rozpracovanou konstrukci (šroubovice, rotační plocha, jehlan...) do další fáze. Výběr
 * v ObjectListu mění jen selectedLines/selectedPoints/... seznamy, ten "druhý krok" navíc chybí –
 * proto ho po přidání do výběru zavoláme ručně, ať konstrukce reaguje stejně jako na klik do plátna.
 */
fun advancePendingLineOrPointConstruction(state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.HELIX -> handleHelixObjectListSelection(state)
        Mongeobjects.RULED_SURFACE -> handleRuledSurfaceSelection(state)
        Mongeobjects.SOLID_OF_REVOLUTION -> {
            when (state.mongeMode) {
                DrawingModeMonge.PUDORYS -> solidOfRevolutionPudorys(state)
                DrawingModeMonge.NARYS -> solidOfRevolutionNarys(state)
            }
        }
        else -> {}
    }
}

/** Stejný "druhý krok navíc" jako výše, ale pro výběr kuželosečky (podstava kužele/válce, poledník rotační plochy). */
fun advancePendingConicConstruction(state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.RULED_SURFACE -> handleRuledSurfaceSelection(state)
        Mongeobjects.SOLID_OF_REVOLUTION -> {
            when (state.mongeMode) {
                DrawingModeMonge.PUDORYS -> solidOfRevolutionPudorys(state)
                DrawingModeMonge.NARYS -> solidOfRevolutionNarys(state)
            }
        }
        Mongeobjects.CONE -> handleConicalToolClick(state)
        Mongeobjects.CYLINDER -> {
            when (state.cylinderPhase) {
                CylinderPhase.PICK_CONIC, CylinderPhase.PICK_DIRECTION, CylinderPhase.PICK_PLANE ->
                    handleCylindricalToolClick(state)
                CylinderPhase.PICK_CONIC_PERP ->
                    // logicalCursor se v této fázi vůbec nepoužívá (potřebuje ho až PICK_CENTER_PERP).
                    handlePerpendicularCylinderClick(state, Offset.Zero)
                CylinderPhase.IDLE, CylinderPhase.PICK_CENTER_PERP -> {}
            }
        }
        else -> {}
    }
}

/** Stejný "druhý krok navíc" jako výše, ale pro výběr podstavného mnohoúhelníku (jehlan/hranol). */
fun advancePendingPolygonConstruction(state: MongeState) {
    val axoConstruction = state.projectionMode == ProjectionMode.AXO
    when (state.drawobjects) {
        Mongeobjects.PYRAMID -> handlePyramidToolClick(state, axoConstruction)
        // logicalCursor se v první fázi (výběr podstavy) nepoužívá, jen ve druhé (výška).
        Mongeobjects.PRISM -> handlePrismToolClick(state, Offset.Zero, axoConstruction)
        else -> {}
    }
}
