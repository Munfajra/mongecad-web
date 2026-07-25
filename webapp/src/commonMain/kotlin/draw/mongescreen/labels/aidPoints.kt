package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.Mongeobjects
import monge.input.selection.toggleSelectionAidPoint
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen

@Composable
fun LabelsAidPoints(state: MongeState) {
    val scale = rememberLabelScale(state)
    val baseScreenOffsetPx = pointLabelBaseScreenOffsetPx(scale)

    state.aidPointsLogical.forEach { point ->
        val name = point.name?.trim().orEmpty()
        if (name.isBlank()) return@forEach

        val canvasLabelScale =
            if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val baseFontPx =
            SettingsManager.current.activeLabelSizePx * 0.7f * canvasLabelScale
        val superscriptFontPx = baseFontPx * 0.7f
        val subscriptFontPx = baseFontPx * 0.7f

        val superscriptDx = 0.62f * baseFontPx
        val superscriptDy = 0.30f * baseFontPx
        val subscriptDx = 0.62f * baseFontPx
        val subscriptDy = 0.60f * baseFontPx

        val superscript = point.upperSuperscript?.takeIf { it.isNotBlank() }
        val subscript = point.lowerSuperscript?.takeIf { it.isNotBlank() }

        val baseSize = remember(name, baseFontPx) {
            measureSkiaParagraph(name, baseFontPx, "italic")
        }
        val superscriptSize = remember(superscript, superscriptFontPx) {
            if (superscript == null) {
                Size.Zero
            } else {
                measureSkiaParagraph(superscript, superscriptFontPx, "greek")
            }
        }
        val subscriptSize = remember(subscript, subscriptFontPx) {
            if (subscript == null) {
                Size.Zero
            } else {
                measureSkiaParagraph(subscript, subscriptFontPx, "greek")
            }
        }

        val baseShiftY = baselineToTopShiftPx(baseSize.height)
        val baseTop = -baseShiftY
        val baseBottom = baseSize.height - baseShiftY

        val superscriptTop =
            if (superscript == null) {
                baseTop
            } else {
                -superscriptDy - baselineToTopShiftPx(superscriptSize.height)
            }
        val superscriptBottom =
            if (superscript == null) {
                baseBottom
            } else {
                -superscriptDy +
                    (superscriptSize.height - baselineToTopShiftPx(superscriptSize.height))
            }
        val superscriptRight =
            if (superscript == null) baseSize.width
            else superscriptDx + superscriptSize.width

        val subscriptTop =
            if (subscript == null) {
                baseTop
            } else {
                subscriptDy - baselineToTopShiftPx(subscriptSize.height)
            }
        val subscriptBottom =
            if (subscript == null) {
                baseBottom
            } else {
                subscriptDy +
                    (subscriptSize.height - baselineToTopShiftPx(subscriptSize.height))
            }
        val subscriptRight =
            if (subscript == null) baseSize.width
            else subscriptDx + subscriptSize.width

        val top = minOf(baseTop, superscriptTop, subscriptTop)
        val right = maxOf(baseSize.width, superscriptRight, subscriptRight)
        val bottom = maxOf(baseBottom, superscriptBottom, subscriptBottom)
        val padding = 4f * canvasLabelScale
        val hitboxSize = Size(
            width = right + 2f * padding,
            height = bottom - top + 2f * padding,
        )

        val logicalBase = Offset(point.x, point.y)
        val userLogical = state.labelOffsetsAidPoints[point.id] ?: Offset.Zero
        val baselineAnchor =
            (logicalBase + userLogical).toScreen(
                scale = state.scale,
                offset = state.canvasOffset,
                canvasHeight = state.canvasHeight,
                state = state,
                canvasWidth = state.canvasWidth,
            ) + baseScreenOffsetPx
        val hitboxTopLeft =
            baselineAnchor + Offset(0f, top) - Offset(padding, padding)

        DraggableLabelHitbox(
            key = point.id,
            finalScreen = hitboxTopLeft,
            textShiftFromHitboxPx = baselineAnchor - hitboxTopLeft,
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,
            getUserLogical = {
                state.labelOffsetsAidPoints[point.id] ?: Offset.Zero
            },
            setUserLogical = { state.labelOffsetsAidPoints[point.id] = it },
            state = state,
            hitboxSizePx = hitboxSize,
            onTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (state.isShiftPressed) {
                        toggleSelectionAidPoint(point, state)
                    } else {
                        state.selectedAidPointIds.clear()
                        state.selectedAidPointIds.add(point.id)
                    }
                }
            },
            onDoubleTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    val live =
                        state.aidPointsLogical.firstOrNull { it.id == point.id }
                            ?: return@DraggableLabelHitbox
                    val liveName = live.name ?: return@DraggableLabelHitbox

                    state.inputName = liveName
                    state.rename.pointBeingRenamed = null
                    state.pendingAidPoint = live
                    state.isNameConfirmed = false
                    setProjectionPhase("rename_aid", state)
                    state.inputSuperscript = live.upperSuperscript.emptyIfNullText()
                    state.inputLowerSuperscript = live.lowerSuperscript.emptyIfNullText()
                }
            },
        )
    }
}

fun DrawScope.drawAidPointLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 20f,
    pxFactor: Float = 1f,
    show: Boolean,
) {
    if (!show) return

    val labelScale = exportLabelScale(state, scale, pxFactor)
    val baseScreenOffsetPx = pointLabelBaseScreenOffsetPx(labelScale, pxFactor)
    val baseFont = fontPx * labelScale
    val superscriptFont = baseFont * 0.6f
    val subscriptFont = baseFont * 0.6f
    val superscriptDy = 0.6f * baseFont
    val subscriptDy = 0.5f * baseFont

    state.aidPointsLogical.forEach { point ->
        val name = point.name?.trim().orEmpty()
        if (name.isBlank()) return@forEach

        val logicalBase = Offset(point.x, point.y)
        val userLogical = state.labelOffsetsAidPoints[point.id] ?: Offset.Zero
        val position =
            (logicalBase + userLogical).toScreen(
                scale = scale,
                offset = offset,
                canvasHeight = size.height,
                state = state,
                canvasWidth = size.width,
            ) + baseScreenOffsetPx

        val nameWidth = drawSkiaText(
            text = name,
            anchor = position,
            color = point.color,
            fontPx = baseFont,
        )

        val indexGap = 2f * labelScale
        point.lowerSuperscript?.takeIf { it.isNotBlank() }?.let { text ->
            drawSkiaText(
                text = text,
                anchor = position + Offset(
                    nameWidth + indexGap - 3f * pxFactor,
                    subscriptDy,
                ),
                color = point.color,
                fontPx = subscriptFont,
                typefaceFamily = "greek",
            )
        }
        point.upperSuperscript?.takeIf { it.isNotBlank() }?.let { text ->
            drawSkiaText(
                text = text,
                anchor = position + Offset(
                    nameWidth + indexGap + 2f * pxFactor,
                    -superscriptDy,
                ),
                color = point.color,
                fontPx = superscriptFont,
                typefaceFamily = "greek",
            )
        }
    }
}
