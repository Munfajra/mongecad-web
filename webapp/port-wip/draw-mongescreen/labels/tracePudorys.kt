package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.ProjectionMode
import model.classes.PlaneTracePudorys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import monge.input.selection.toggleSelectionPlane
import monge.input.selection.toggleSelectionPudorysLine
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen

@Composable
fun LabelsPudorysTraces(
    state: MongeState,
    projector: (PlaneTracePudorys) -> Offset = { Offset(it.point.x, it.point.y) }
) {
    val scale = rememberLabelScale(state)
    val isAxo = state.projectionMode == ProjectionMode.AXO

    val basePx = Offset(12f, -12f)
    val baseScreenOffsetPx =
        if (SettingsManager.current.scaleLabelsWithCanvas) basePx * scale else basePx

    state.lineTracesPudorys.forEach { trace ->
        if (isAxo && !trace.showInAxo) {
            return@forEach
        }
        if (isAxoAxisId(trace.id) || isAxoAxisId(trace.parent?.id)) {
            return@forEach
        }
        val parentId = trace.parent?.id ?: trace.parentId
        val parentPlane = parentId?.let { id -> state.planes3D.find { it.id == id } } ?: trace.parent
        val mainText = "p\u2081"

        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s

        val textSize = remember(mainText, fontPx) {
            measureSkiaParagraph(mainText, fontPx, "italic")
        }

        val pad = 4f * s
        val hitSize = Size(textSize.width + 2 * pad, textSize.height + 2 * pad)

        val logicalBase =
            if (isAxo) projector(trace)
            else Offset(trace.point.x, trace.point.y)

        val userLogical = state.labelOffsetsTracePudorys[trace.id] ?: Offset.Zero

        val baselineAnchor = if (isAxo) {
            localToScreen(
                local = logicalBase + userLogical,
                scale = state.scale,
                offset = state.canvasOffset
            ) + baseScreenOffsetPx
        } else {
            val logicalBase = Offset(trace.point.x, trace.point.y)
            val userLogical = state.labelOffsetsTracePudorys[trace.id] ?: Offset.Zero
            val logical = logicalBase + userLogical

            logical.toScreen(
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
            key = "pudorys-trace-${trace.id}",
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = Offset(pad, pad) + Offset(0f, shiftY),
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,
            getUserLogical = { state.labelOffsetsTracePudorys[trace.id] ?: Offset.Zero },
            setUserLogical = { state.labelOffsetsTracePudorys[trace.id] = it },
            state = state,
            show3DTag = (parentPlane != null),
            labelScaleForUi = scale,
            hitboxSizePx = hitSize,
            onTap = {
                if (parentPlane != null) {
                    toggleSelectionPlane(parentPlane, state)
                } else {
                    val tracep = state.lineTracesPudorys.find { it.id == trace.id }
                    if (tracep != null) toggleSelectionPudorysLine(tracep, state)
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
                    state.pudorysTracePendingForNaming =
                        state.lineTracesPudorys.find { it.id == trace.id }
                    state.planeNameInput = trace.name.orEmpty()
                    state.isNameConfirmed = false
                    state.showPlaneNamingDialog = true
                }
            }
        )
    }
}
fun DrawScope.drawTracePudorysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    projector: (PlaneTracePudorys) -> Offset = { Offset(it.point.x, it.point.y) }
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

    for (trace in state.lineTracesPudorys) {
        if (isAxo && !trace.showInAxo) continue
        if (isAxoAxisId(trace.id) || isAxoAxisId(trace.parent?.id)) continue

        val upper = trace.name?.takeIf { it.isNotBlank() } ?: continue

        val pos = if (isAxo) {
            val baseLocal = projector(trace)
            val userLocal = state.labelOffsetsTracePudorys[trace.id] ?: Offset.Zero

            localToScreen(
                local = baseLocal + userLocal,
                scale = scale,
                offset = offset
            ) + baseScreenOffsetPx
        } else {
            val logicalBase = Offset(trace.point.x, trace.point.y)
            val userLogical = state.labelOffsetsTracePudorys[trace.id] ?: Offset.Zero

            (logicalBase + userLogical).toScreen(
                scale = scale,
                offset = offset,
                canvasHeight = canvasHeight,
                state = state,
                canvasWidth = canvasWidth
            ) + baseScreenOffsetPx
        }

        val baseName = "p\u2081"

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
