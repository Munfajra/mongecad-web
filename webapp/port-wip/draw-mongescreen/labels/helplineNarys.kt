package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import draw.mongescreen.previews.tools.PdfExportFonts
import model.Mongeobjects
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen

fun DrawScope.drawHelpLineNarysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    show: Boolean
) {
    if (!show) return

    val labelScale = exportLabelScale(state, scale, pxFactor)
    val baseFont = fontPx * labelScale

    val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    val isInvertedX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT

    val basePx = Offset(if (isInvertedX) -12f else 12f, if (flipY) 12f else -12f)
    val baseScreenOffsetPx = scaledOffset(basePx, pxFactor) * labelScale

    val marginPx = 5f * pxFactor

    val supFont = baseFont * 0.70f
    val subFont = baseFont * 0.70f

    val supDy = 0.30f * baseFont
    val subDy = 0.60f * baseFont

    val indexGap = 2f * labelScale
    val subExtraDx = -3f * pxFactor
    val supExtraDx =  2f * pxFactor

    val canvasHeight = size.height
    val canvasWidth = size.width

    for (line in state.helpLineNarys) {
        val isAxisZ = line.id == "axisZ" && state.projectionMode == ProjectionMode.MONGE
        val userLogical = state.labelOffsetsHelpNarys[line.id] ?: Offset.Zero

        val baseName = line.name?.trim().orEmpty()
        if (baseName.isBlank()) continue

        val supTxt = line.localSuperscript?.takeIf { it.isNotBlank() }
        val subTxt = line.lowerSuperscript?.takeIf { it.isNotBlank() }

        val anchor = if (isAxisZ) {
            computeAxisZLabelScreenPosNarys(
                state = state,
                userLogical = userLogical,
                baseScreenOffsetPx = baseScreenOffsetPx,
                marginPx = marginPx
            )
        } else {
            val logicalBase = Offset(line.point.x, -line.point.z)
            val logical = logicalBase + userLogical

            logical.toScreen(
                scale = scale,
                offset = offset,
                canvasHeight = canvasHeight,
                state = state,
                canvasWidth = canvasWidth
            ) + baseScreenOffsetPx
        }

        val nameWidth = drawSkiaText(
            text = baseName,
            anchor = anchor,
            color = line.color,
            fontPx = baseFont
        )

        if (subTxt != null) {
            val subPos = anchor + Offset(
                nameWidth + indexGap + subExtraDx,
                subDy
            )

            drawSkiaText(
                text = subTxt,
                anchor = subPos,
                color = line.color,
                fontPx = subFont,
                typefaceFamily = "greek"
            )
        }

        if (supTxt != null) {
            val supPos = anchor + Offset(
                nameWidth + indexGap + supExtraDx,
                -supDy
            )

            drawSkiaText(
                text = supTxt,
                anchor = supPos,
                color = line.color,
                fontPx = supFont,
                typefaceFamily = "greek"
            )
        }
    }
}

@Composable
fun LabelsNarysHelpLines(state: MongeState) {
    val scale = rememberLabelScale(state)

    val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    val isInvertedX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
    val basePx = Offset(if (isInvertedX) -12f else 12f, if (flipY) 12f else -12f)
    val baseScreenOffsetPx =
        if (SettingsManager.current.scaleLabelsWithCanvas) basePx * scale else basePx

    val marginPx = with(LocalDensity.current) { 5.dp.toPx() }

    state.helpLineNarys.forEach { line ->

        val isAxisZ = line.id == "axisZ" && state.projectionMode == ProjectionMode.MONGE
        val userLogical = state.labelOffsetsHelpNarys[line.id] ?: Offset.Zero

        // ===== osa Z – bez interakce =====
        if (isAxisZ) {
            computeAxisZLabelScreenPosNarys(
                state = state,
                userLogical = userLogical,
                baseScreenOffsetPx = baseScreenOffsetPx,
                marginPx = marginPx
            )
            return@forEach
        }

        val baseName = line.name ?: return@forEach
        if (baseName.isBlank()) return@forEach

        val supTxt = line.localSuperscript?.takeIf { it.isNotBlank() }
        val subTxt = line.lowerSuperscript?.takeIf { it.isNotBlank() }

        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val baseFontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s


        val supScale = 0.70f
        val subScale = 0.70f
        val supFontPx = baseFontPx * supScale
        val subFontPx = baseFontPx * subScale

        val supDx = 0.62f * baseFontPx
        val supDy = 0.30f * baseFontPx
        val subDx = 0.62f * baseFontPx
        val subDy = 0.60f * baseFontPx

        val baseSize = remember(baseName, baseFontPx) {
            measureSkiaParagraph(baseName, baseFontPx, "italic")
        }

        val supSize = remember(supTxt, supFontPx) {
            if (supTxt == null) Size.Zero
            else measureSkiaParagraph(supTxt, supFontPx, "greek")
        }

        val subSize = remember(subTxt, subFontPx) {
            if (subTxt == null) Size.Zero
            else measureSkiaParagraph(subTxt, subFontPx, "greek")
        }

        val baseShiftY = baselineToTopShiftPx(baseSize.height)

        // BASE bounds
        val baseLeft   = 0f
        val baseTop    = -baseShiftY
        val baseRight  = baseSize.width
        val baseBottom = baseSize.height - baseShiftY

        // SUP bounds
        val supLeft   = if (supTxt != null) supDx else 0f
        val supTop    = if (supTxt != null)
            (-supDy - baselineToTopShiftPx(supSize.height)) else 0f
        val supRight  = if (supTxt != null) (supDx + supSize.width) else 0f
        val supBottom = if (supTxt != null)
            (-supDy + (supSize.height - baselineToTopShiftPx(supSize.height))) else 0f

        // SUB bounds
        val subLeft   = if (subTxt != null) subDx else 0f
        val subTop    = if (subTxt != null)
            (subDy - baselineToTopShiftPx(subSize.height)) else 0f
        val subRight  = if (subTxt != null) (subDx + subSize.width) else 0f
        val subBottom = if (subTxt != null)
            (subDy + (subSize.height - baselineToTopShiftPx(subSize.height))) else 0f

        val left   = minOf(baseLeft, supLeft, subLeft)
        val top    = minOf(baseTop, supTop, subTop)
        val right  = maxOf(baseRight, supRight, subRight)
        val bottom = maxOf(baseBottom, supBottom, subBottom)

        val pad = 4f * s
        val hitSize = Size((right - left) + 2 * pad, (bottom - top) + 2 * pad)

        val logicalBase = Offset(line.point.x, -line.point.z)

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
            key = line.id,
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = textShiftFromHitboxPx,
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,
            getUserLogical = { state.labelOffsetsHelpNarys[line.id] ?: Offset.Zero },
            setUserLogical = { state.labelOffsetsHelpNarys[line.id] = it },
            state = state,
            hitboxSizePx = hitSize,


            onTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (state.isShiftPressed) state.selectedLinesNarys.add(line)
                    else {
                        clearSelection(state)
                        state.selectedLinesNarys.add(line)
                    }
                }
            },

            onDoubleTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    val live = state.helpLineNarys.firstOrNull { it.id == line.id }
                        ?: return@DraggableLabelHitbox
                    state.inputName = live.name?.removeSuffix("₂") ?: ""
                    state.isNameConfirmed = false
                    setProjectionPhase("rename_helpline_narys", state)
                    state.rename.helplineBeingRenamedNarys = live
                }
            }
        )
    }
}
fun  computeAxisZLabelScreenPosNarys(
    state: MongeState,
    userLogical: Offset,
    baseScreenOffsetPx: Offset,
    marginPx: Float,
    extraDownPx: Float = 10f,
    axisGapPx: Float = 0f
): Offset {
    val isInvertedX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
    val xPositiveRight = !isInvertedX

    // škáluj jen pokud se škálují labely
    val uiS = if (SettingsManager.current.scaleLabelsWithCanvas) state.scale else 1f
    val gapNow = axisGapPx * uiS
    val extraDownNow = extraDownPx * uiS

    // osa Y (x=0) v obrazovce
    var axisYScreenX0 = state.canvasOffset.x
    if (isInvertedX) axisYScreenX0 = state.canvasWidth - axisYScreenX0

    // ===== nahoře =====
    val screenY = marginPx + extraDownNow + baseScreenOffsetPx.y+20f

    // X posun + user offset v LOGICAL (škáluje se vždy, je to logical)
    var screenX = axisYScreenX0 + (userLogical.x * state.scale) + baseScreenOffsetPx.x
    screenX += if (xPositiveRight) gapNow else -gapNow

    return Offset(screenX, screenY)
}
