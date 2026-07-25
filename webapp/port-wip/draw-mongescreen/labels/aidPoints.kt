package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.tools.PdfExportFonts
import model.Mongeobjects
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import monge.input.selection.toggleSelectionAidPoint
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen

@Composable
fun LabelsAidPoints(state: MongeState) {
    val scale = rememberLabelScale(state)

    val baseScreenOffsetPx =
        pointLabelBaseScreenOffsetPx(scale)

    state.aidPointsLogical.forEach { pt ->
        val name = pt.name?.trim().orEmpty()
        if (name.isBlank()) return@forEach

        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val baseFontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s


        val supScale = 0.70f
        val subScale = 0.70f
        val supFontPx = baseFontPx * supScale
        val subFontPx = baseFontPx * subScale

        // stejné proporce jako v exportu (jen v px pro overlay)
        val supDx = 0.62f * baseFontPx
        val supDy = 0.30f * baseFontPx
        val subDx = 0.62f * baseFontPx
        val subDy = 0.60f * baseFontPx

        val supTxt = pt.upperSuperscript?.takeIf { it.isNotBlank() }
        val subTxt = pt.lowerSuperscript?.takeIf { it.isNotBlank() }

        // měření
        val baseSize = remember(name, baseFontPx) { measureSkiaParagraph(name, baseFontPx, "italic") }
        val supSize  = remember(supTxt, supFontPx) {
            if (supTxt == null) Size.Zero else measureSkiaParagraph(supTxt, supFontPx, "greek")
        }
        val subSize  = remember(subTxt, subFontPx) {
            if (subTxt == null) Size.Zero else measureSkiaParagraph(subTxt, subFontPx, "greek")
        }

        // baseline->top shift pro BASE text
        val baseShiftY = baselineToTopShiftPx(baseSize.height)

        // bbox v koordinátech relativně k BASE baseline-left (0,0)
        val baseLeft   = 0f
        val baseTop    = -baseShiftY
        val baseRight  = baseSize.width
        val baseBottom = baseSize.height - baseShiftY

        val supLeft   = if (supTxt != null) supDx else 0f
        val supTop    = if (supTxt != null) (-supDy - baselineToTopShiftPx(supSize.height)) else 0f
        val supRight  = if (supTxt != null) (supDx + supSize.width) else 0f
        val supBottom = if (supTxt != null) (-supDy + (supSize.height - baselineToTopShiftPx(supSize.height))) else 0f

        val subLeft   = if (subTxt != null) subDx else 0f
        val subTop    = if (subTxt != null) (subDy - baselineToTopShiftPx(subSize.height)) else 0f
        val subRight  = if (subTxt != null) (subDx + subSize.width) else 0f
        val subBottom = if (subTxt != null) (subDy + (subSize.height - baselineToTopShiftPx(subSize.height))) else 0f

        val left   = minOf(baseLeft, if (supTxt != null) supLeft else baseLeft, if (subTxt != null) subLeft else baseLeft)
        val top    = minOf(baseTop,  if (supTxt != null) supTop  else baseTop,  if (subTxt != null) subTop  else baseTop)
        val right  = maxOf(baseRight, if (supTxt != null) supRight else baseRight, if (subTxt != null) subRight else baseRight)
        val bottom = maxOf(baseBottom, if (supTxt != null) supBottom else baseBottom, if (subTxt != null) subBottom else baseBottom)

        val pad = 4f * s
        val hitSize = Size((right - left) + 2 * pad, (bottom - top) + 2 * pad)

        val logicalBase = Offset(pt.x, pt.y)
        val userLogical = state.labelOffsetsAidPoints[pt.id] ?: Offset.Zero

        // baseline-left anchor pro BASE text
        val baselineAnchor =
            (logicalBase + userLogical).toScreen(
                scale = state.scale,
                offset = state.canvasOffset,
                canvasHeight = state.canvasHeight,
                state = state,
                canvasWidth = state.canvasWidth
            ) + baseScreenOffsetPx
// ✅ bbox je relativně k baselineAnchor (0,0 = baseline-left)
        val hitTopLeft = baselineAnchor + Offset(left, top) - Offset(pad, pad)

// ✅ shift do DraggableLabelHitbox = kde leží BASE baseline vůči hitbox top-left
        val textShiftFromHitboxPx = baselineAnchor - hitTopLeft

        DraggableLabelHitbox(
            key = pt.id,
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = textShiftFromHitboxPx,
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,
            getUserLogical = { state.labelOffsetsAidPoints[pt.id] ?: Offset.Zero },
            setUserLogical = { state.labelOffsetsAidPoints[pt.id] = it },
            state = state,
            hitboxSizePx = hitSize,

            onTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (state.isShiftPressed) toggleSelectionAidPoint(pt, state)
                    else {
                        state.selectedAidPointIds.clear()
                        state.selectedAidPointIds.add(pt.id)
                    }
                }
            },

            onDoubleTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    val live = state.aidPointsLogical.firstOrNull { it.id == pt.id } ?: return@DraggableLabelHitbox
                    val n = live.name ?: return@DraggableLabelHitbox

                    state.inputName = n
                    state.rename.pointBeingRenamed = null
                    state.pendingAidPoint = live
                    state.isNameConfirmed = false
                    setProjectionPhase("rename_aid", state)
                    state.inputSuperscript = live.upperSuperscript.emptyIfNullText()
                    state.inputLowerSuperscript = live.lowerSuperscript.emptyIfNullText()
                }
            }
        )
    }
}
fun DrawScope.drawAidPointLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 20f,
    pxFactor: Float = 1f,
    show: Boolean
) {
    if (!show) return

    val labelScale = exportLabelScale(state, scale, pxFactor)

    val baseScreenOffsetPx = pointLabelBaseScreenOffsetPx(labelScale, pxFactor)

    val baseFont = fontPx * labelScale
    val supScale = 0.6f
    val subScale = 0.6f
    val supFont  = baseFont * supScale
    val subFont  = baseFont * subScale

    val supDy = 0.6f * baseFont
    val subDy = 0.5f * baseFont

    val canvasHeight = size.height
    val canvasWidth  = size.width

    for (pt in state.aidPointsLogical) {
        val name = pt.name?.trim().orEmpty()
        if (name.isBlank()) continue

        val logicalBase = Offset(pt.x, pt.y)
        val userLogical = state.labelOffsetsAidPoints[pt.id] ?: Offset.Zero
        val logical = logicalBase + userLogical

        val pos = logical.toScreen(
            scale = scale,
            offset = offset,
            canvasHeight = canvasHeight,
            state = state,
            canvasWidth = canvasWidth
        ) + baseScreenOffsetPx

        // ✅ BASE (baseline-left)
        drawSkiaText(name, pos, pt.color, fontPx = baseFont)


        val nameWidth = drawSkiaText(name, pos, pt.color, fontPx = baseFont)

        val indexGap = 2f * labelScale
        val subExtraDx = -3f * pxFactor
        val supExtraDx =  2f * pxFactor
// ✅ SUB
        pt.lowerSuperscript?.takeIf { it.isNotBlank() }?.let { subTxt ->
            val subPos = pos + Offset(
                nameWidth + indexGap + subExtraDx,
                subDy
            )

            drawSkiaText(
                subTxt,
                subPos,
                pt.color,
                fontPx = subFont,
                typefaceFamily = "greek"
            )
        }

// ✅ SUP
        pt.upperSuperscript?.takeIf { it.isNotBlank() }?.let { supTxt ->
            val supPos = pos + Offset(
                nameWidth + indexGap + supExtraDx,
                -supDy
            )

            drawSkiaText(
                supTxt,
                supPos,
                pt.color,
                fontPx = supFont,
                typefaceFamily = "greek"
            )
        }
    }
}
