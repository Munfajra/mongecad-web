package monge.input.axo

import androidx.compose.ui.geometry.Offset

/**
 * Axonometrická báze. Web axonometrii nekreslí, ale typ zůstává,
 * protože na něm visí `ConicSection3D.projectToAxo` a odvozená
 * geometrie kuželoseček – vyříznout ho by znamenalo sahat do jádra.
 */
data class AxoCoords2(val a: Float, val b: Float)

data class AxoRenderBasis(
    val origin: Offset,
    val ex: Offset,
    val ey: Offset,
    val ez: Offset
)

fun Offset.normalizedOrZero(): Offset {
    val len = getDistance()
    return if (len < 1e-6f) Offset.Zero else this / len
}
