package draw.mongescreen.fills

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import model.Offset3D
import model.axo.isAxoAxisOrderMirrored
import monge.input.axo.AxoRenderBasis
import state.MongeState

internal enum class FillViewKind { PUDORYS, NARYS, AXO }

/**
 * Jednotná abstrakce rovnoběžného promítání pro výpočet occlusion výplní.
 *
 * Každý pohled (Monge půdorys/nárys, axonometrie) je rovnoběžné promítání 3D→2D.
 * Occlusion pracuje v **logických** souřadnicích pohledu (bez scale/offset canvasu);
 * převod na obrazovku řeší [screenMatrix] aplikovaná na hotový Path.
 *
 * - [project]  : 3D bod → logické 2D (přesně kopíruje transformace ve výplních fills)
 * - [rayDir]   : jednotkový 3D směr promítacího paprsku orientovaný **k pozorovateli**
 *                (větší posun podél rayDir = blíž pozorovateli)
 * - [rayBase]  : logický 2D bod → libovolný 3D bod, který se do něj promítá
 *                (paprsek pak je rayBase(p) + t·rayDir)
 * - [screenMatrix] : logická 2D → obrazovka (afinní scale+translace)
 */
internal class FillView(
    val project: (Offset3D) -> Offset,
    val rayDir: Offset3D,
    val rayBase: (Offset) -> Offset3D,
    val screenMatrix: Matrix,
    val kind: FillViewKind,
) {
    companion object {
        /** logika*scale + translace jako Compose Matrix (column-major 4×4). */
        private fun affine(scale: Float, tx: Float, ty: Float): Matrix =
            Matrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f,
                    0f, scale, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    tx, ty, 0f, 1f
                )
            )

        /** Půdorys: project=(x,y), hloubka = z (pozorovatel nad scénou v +z). */
        fun pudorys(state: MongeState): FillView = FillView(
            project = { Offset(it.x, it.y) },
            rayDir = Offset3D(0f, 0f, 1f),
            rayBase = { Offset3D(it.x, it.y, 0f) },
            screenMatrix = affine(state.scale, state.canvasOffset.x, state.canvasOffset.y),
            kind = FillViewKind.PUDORYS,
        )

        /**
         * Nárys: project=(x,−z), hloubka = y. Pozorovatel v +y (viz [NARYS_TOWARD_VIEWER]).
         * Logické 2D (px,py) = (x, −z) → x=px, z=−py, y libovolné (0).
         */
        fun narys(state: MongeState): FillView = FillView(
            project = { Offset(it.x, -it.z) },
            rayDir = Offset3D(0f, NARYS_TOWARD_VIEWER, 0f),
            rayBase = { Offset3D(it.x, 0f, -it.y) },
            screenMatrix = affine(state.scale, state.canvasOffset.x, state.canvasOffset.y),
            kind = FillViewKind.NARYS,
        )

        /** Axonometrie: projekce z báze, hloubka = jádro 2×3 projekční matice (i pro kosoúhlé). */
        fun axo(state: MongeState, basis: AxoRenderBasis): FillView {
            // řádky projekční matice P (2×3): logical = P · (x,y,z)
            val r1 = Offset3D(basis.ex.x, basis.ey.x, basis.ez.x)
            val r2 = Offset3D(basis.ex.y, basis.ey.y, basis.ez.y)

            // směr paprsku = jádro P (P·d = 0) = r1 × r2. AXO logické y míří dolů,
            // OpenGL kamera proto používá opačný vektor a ještě korekci zrcadleného
            // pořadí os. Fill zachovává konvenci "větší t = blíž".
            var dir = (r1 cross r2)
            val dlen = dir.length()
            val handedness = if (isAxoAxisOrderMirrored(state.activeAxoModel)) -1f else 1f
            dir = if (dlen < 1e-9f) {
                Offset3D(0f, 0f, 1f)
            } else {
                dir * (AXO_TOWARD_VIEWER * handedness / dlen)
            }

            // pseudoinverze pro rayBase: base = Pᵀ (P Pᵀ)⁻¹ p
            val a = r1 dot r1
            val b = r1 dot r2
            val c = r2 dot r2
            val det = a * c - b * b
            val rayBase: (Offset) -> Offset3D = if (kotlin.math.abs(det) < 1e-12f) {
                { Offset3D(0f, 0f, 0f) }
            } else {
                { p ->
                    // (P Pᵀ)⁻¹ · p
                    val w0 = (c * p.x - b * p.y) / det
                    val w1 = (-b * p.x + a * p.y) / det
                    r1 * w0 + r2 * w1
                }
            }

            val tx = basis.origin.x * state.scale + state.canvasOffset.x
            val ty = basis.origin.y * state.scale + state.canvasOffset.y
            return FillView(
                project = { basis.ex * it.x + basis.ey * it.y + basis.ez * it.z },
                rayDir = dir,
                rayBase = rayBase,
                screenMatrix = affine(state.scale, tx, ty),
                kind = FillViewKind.AXO,
            )
        }

        // Kalibrace znaménka hloubky (kdo je „vpředu"). Ověřit empiricky – při obrácené
        // dominanci výplní jen otočit znaménko.
        private const val NARYS_TOWARD_VIEWER = 1f
        private const val AXO_TOWARD_VIEWER = -1f
    }
}
