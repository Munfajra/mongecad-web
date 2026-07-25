package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.PlaneTraceNarys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import monge.input.selection.toggleSelectionNarysLine
import monge.input.selection.toggleSelectionPlane
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen

@Composable
fun LabelsNarysTraces(
    state: MongeState,
    projector: (PlaneTraceNarys) -> Offset = { Offset(it.point.x, it.point.z) }
) {
    val scale = rememberLabelScale(state)
    val isAxo = state.projectionMode == ProjectionMode.AXO

    val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    val isInvertedX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
    val basePx = Offset(if (isInvertedX) -12f else 12f, if (flipY) 12f else -12f)
    val baseScreenOffsetPx =
        if (SettingsManager.current.scaleLabelsWithCanvas) basePx * scale else basePx

    state.lineTracesNarys.forEach { trace ->
        if (isAxo && !trace.showInAxo) {
            return@forEach
        }
        if (isAxoAxisId(trace.id) || isAxoAxisId(trace.parent?.id)) {
            return@forEach
        }
        val parentId = trace.parent?.id ?: trace.parentId
        val parentPlane = parentId?.let { id -> state.planes3D.find { it.id == id } } ?: trace.parent
        val mainText = "n\u2082"


        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s

        val textSize = remember(mainText, fontPx) {
            measureSkiaParagraph(mainText, fontPx, "italic")
        }

        val pad = 4f * s
        val hitSize = Size(textSize.width + 2 * pad, textSize.height + 2 * pad)

        val logicalBase =
            if (isAxo) projector(trace)
            else Offset(trace.point.x, -trace.point.z)

        val userLogical = state.labelOffsetsTraceNarys[trace.id] ?: Offset.Zero

        val baselineAnchor = if (isAxo) {
            localToScreen(
                local = logicalBase + userLogical,
                scale = state.scale,
                offset = state.canvasOffset
            ) + baseScreenOffsetPx
        } else {
            (logicalBase + userLogical).toScreen(
                scale = state.scale,
                offset = state.canvasOffset,
                canvasHeight = state.canvasHeight,
                state = state,
                canvasWidth = state.canvasWidth
            ) + baseScreenOffsetPx
        }

        val shiftY = baselineToTopShiftPx(textSize.height)
        val textTopLeft = Offset(baselineAnchor.x, baselineAnchor.y - shiftY)
        val hitTopLeft = textTopLeft - Offset(pad, pad)

        DraggableLabelHitbox(
            key = "narys-trace-${trace.id}",
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = Offset(pad, pad) + Offset(0f, shiftY),
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,
            getUserLogical = { state.labelOffsetsTraceNarys[trace.id] ?: Offset.Zero },
            setUserLogical = { state.labelOffsetsTraceNarys[trace.id] = it },
            state = state,
            hitboxSizePx = hitSize,
            show3DTag = (parentPlane != null),
            labelScaleForUi = scale,
            onTap = {
                if (parentPlane != null) {
                    toggleSelectionPlane(parentPlane, state)
                } else {
                    val tracen = state.lineTracesNarys.find { it.id == trace.id }
                    if (tracen != null) toggleSelectionNarysLine(tracen, state)
                }
            },
            onDoubleTap = {
                if (parentPlane != null) {
                    val livePlane = state.planes3D.find { it.id == parentPlane.id } ?: parentPlane
                    state.rename.planeBeingRenamed = livePlane
                    state.planeNameInput = livePlane.name
                    state.inputName = livePlane.name
                    state.isNameConfirmed = false
                    setProjectionPhase("rename_plane", state)
                } else {
                    state.narysTracePendingForNaming =
                        state.lineTracesNarys.find { it.id == trace.id }
                    state.planeNameInput = trace.name.orEmpty()
                    state.isNameConfirmed = false
                    state.showPlaneNamingDialog = true
                }
            }
        )
    }
}
fun DrawScope.drawTraceNarysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    projector: (PlaneTraceNarys) -> Offset = { Offset(it.point.x, it.point.z) }
) {
    val labelScale = exportLabelScale(state, scale, pxFactor)

    val font = fontPx * labelScale
    val supFont = font * 0.7f

    val baseScreenOffsetPx =
        scaledOffset(Offset(12f, -12f), pxFactor) * labelScale

    val supDy = font * 0.35f

    val indexGap = 2f * labelScale
    val supExtraDx = 2f * pxFactor

    val canvasHeight = size.height
    val canvasWidth = size.width
    val isAxo = state.projectionMode == ProjectionMode.AXO

    for (trace in state.lineTracesNarys) {
        if (isAxo && !trace.showInAxo) continue
        if (isAxoAxisId(trace.id) || isAxoAxisId(trace.parent?.id)) continue

        val upper = trace.name?.takeIf { it.isNotBlank() } ?: continue

        val pos = if (isAxo) {
            val baseLocal = projector(trace)
            val userLocal = state.labelOffsetsTraceNarys[trace.id] ?: Offset.Zero

            localToScreen(
                local = baseLocal + userLocal,
                scale = scale,
                offset = offset
            ) + baseScreenOffsetPx
        } else {
            val logicalBase = Offset(trace.point.x, -trace.point.z)
            val userLogical = state.labelOffsetsTraceNarys[trace.id] ?: Offset.Zero

            (logicalBase + userLogical).toScreen(
                scale = scale,
                offset = offset,
                canvasHeight = canvasHeight,
                state = state,
                canvasWidth = canvasWidth
            ) + baseScreenOffsetPx
        }

        val baseName = "n\u2082"

        val nameWidth = drawSkiaText(
            text = baseName,
            anchor = pos,
            color = trace.color,
            fontPx = font
        )

        val supPos = pos + Offset(
            nameWidth + indexGap + supExtraDx,
            -supDy
        )

        drawSkiaText(
            text = upper,
            anchor = supPos,
            color = trace.color,
            fontPx = supFont,
            typefaceFamily = "greek"
        )
    }
}
