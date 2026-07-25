package draw.mongescreen.previews.tools


import monge.input.axo.axoOverlayToScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import draw.mongescreen.labels.formatKota
import model.LocalMongeColors
import model.Mongeobjects
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import serialization.SettingsManager
import monge.input.axo.points.screenToAxoOverlayLocal
import state.MongeState
import utils.getLogicalCursor
import utils.toScreen
import kotlin.math.roundToInt


@Composable
fun DistanceOrKotaPreviewLabel(
    state: MongeState,
    snappedPointLogical: Offset?,
) {
    val colors = LocalMongeColors.current
    val ui = SettingsManager.current.UIscale/75f
    val isAxo = state.projectionMode == ProjectionMode.AXO


    data class Preview(val a: Offset, val b: Offset, val text: String)

    val preview: Preview? =
        if (isAxo){
            val basis = state.basis?: return
            val cursorLogical = state.snappedPointLogical
                ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)

            when (state.projectionPhase) {
                 "distance_point2_select_axo" -> {
                    if (state.drawobjects != Mongeobjects.GETDISTANCE) return
                    val p1 = state.pendingPoint1 ?: return
                    val dist = (cursorLogical - p1).getDistance() * 0.1f
                    Preview(p1, cursorLogical, formatKota(dist))
                }

                "distance_target_place_axo" -> {
                    if (state.drawobjects != Mongeobjects.GETDISTANCE) return
                    val p3 = state.pendingPoint3 ?: return
                    val dist = state.pendingDistance ?: return
                    Preview(p3, cursorLogical, formatKota(dist / 10f))
                }
                else -> null
            }
        }
        else {
            val cursorLogical = getLogicalCursor(
                snappedPointLogical,
                state.cursorPosition,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )
            when (state.projectionPhase) {
                "get_kota_p1" -> {
                    val p1 = state.pendingPoint1 ?: return
                    val dist = (cursorLogical - p1).getDistance() * 0.1f
                    Preview(p1, cursorLogical, formatKota(dist))
                }

                "distance_point2_select" -> {
                    if (state.drawobjects != Mongeobjects.GETDISTANCE) return
                    val p1 = state.pendingPoint1 ?: return
                    val dist = (cursorLogical - p1).getDistance() * 0.1f
                    Preview(p1, cursorLogical, formatKota(dist))
                }

                "distance_target_place" -> {
                    if (state.drawobjects != Mongeobjects.GETDISTANCE) return
                    val p3 = state.pendingPoint3 ?: return
                    val dist = state.pendingDistance ?: return
                    Preview(p3, cursorLogical, formatKota(dist / 10f))
                }

                else -> null
            }
        }

    val pr = preview ?: return

    val density = LocalDensity.current

    val maxFontPx = 20f*ui     // 👈 nastav si dle oka (např. 40–60)
    val fontPx = maxFontPx.roundToInt()

    val fontSp = with(density) { fontPx.toSp() }

    // === dělej offset ve SCREEN px (stabilní při zoomu) ===
    val aScreen: Offset
    val bScreen: Offset

    if (isAxo) {
        val basis = state.basis ?: return

        aScreen = axoOverlayToScreen(
            local = pr.a,
            state = state,
            basis = basis
        )

        bScreen = axoOverlayToScreen(
            local = pr.b,
            state = state,
            basis = basis
        )
    } else {
        aScreen = pr.a.toScreen(
            scale = state.scale,
            offset = state.canvasOffset,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            state = state
        )

        bScreen = pr.b.toScreen(
            scale = state.scale,
            offset = state.canvasOffset,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            state = state
        )
    }
    val vS = bScreen - aScreen
    val lenS = vS.getDistance()
    if (lenS < 1e-4f) return
    val nS = Offset(-vS.y / lenS, vS.x / lenS)

    val midScreen = Offset((aScreen.x + bScreen.x) * 0.5f, (aScreen.y + bScreen.y) * 0.5f)

    val normalOffsetPx = fontPx * 0.9f
    val upOffsetPx = fontPx * 0.4f

    val final = midScreen + nS * normalOffsetPx + Offset(0f, -upOffsetPx)

    Box(
        modifier = Modifier.absoluteOffset { IntOffset(final.x.toInt(), final.y.toInt()) }
    ) {
        Box(
            modifier = Modifier
                .background(colors.background, RoundedCornerShape(8f*ui.dp))
                .border(1.dp, colors.tab, RoundedCornerShape(8f*ui.dp))
                .padding(horizontal = 8f*ui.dp, vertical = 4f*ui.dp)
        ) {
            Text(
                text = pr.text+"cm",
                color = colors.text,
                fontSize = fontSp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
data class PdfExportFonts(
    val regular: PDFont,
    val italic: PDFont,
    val greek: PDFont
)


