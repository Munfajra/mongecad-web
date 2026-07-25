package monge.input.axo

import androidx.compose.ui.geometry.Offset
import state.MongeState

/**
 * Převod lokální souřadnice AXO overlaye na obrazovku.
 * Na desktopu tahle funkce bydlí v `export/pdfRenderer/pointsPdf.kt`, i když
 * s PDF nemá nic společného – tady je u ostatních axo transformací.
 */
fun axoOverlayToScreen(
    local: Offset,
    state: MongeState,
    basis: AxoRenderBasis
): Offset {
    val p = basis.origin + local
    return p * state.scale + state.canvasOffset
}
