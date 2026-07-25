package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.ProjectionMode
import model.classes.PlaneTraceBokorys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import monge.input.selection.toggleSelectionBokorysLine
import monge.input.selection.toggleSelectionPlane
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

@Composable
fun LabelsBokorysTraces(
    state: MongeState,
    projector: (PlaneTraceBokorys) -> Offset = { Offset(it.point.y, it.point.z) }
) {
    val scale = rememberLabelScale(state)
    val isAxo = state.projectionMode == ProjectionMode.AXO

    val basePx = Offset(12f, -12f)
    val baseScreenOffsetPx =
        if (SettingsManager.current.scaleLabelsWithCanvas) basePx * scale else basePx

    state.lineTracesBokorys.forEach { trace ->
        if (isAxo && !trace.showInAxo) {
            return@forEach
        }
        if (isAxoAxisId(trace.id) || isAxoAxisId(trace.parent?.id)) {
            return@forEach
        }
        val parentId = trace.parent?.id ?: trace.parentId
        val parentPlane = parentId?.let { id -> state.planes3D.find { it.id == id } } ?: trace.parent
        val mainText = "b\u2083"

        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s

        val textSize = remember(mainText, fontPx) {
            measureSkiaParagraph(mainText, fontPx, "italic")
        }

        val pad = 4f * s
        val hitSize = Size(textSize.width + 2 * pad, textSize.height + 2 * pad)

        val logicalBase = projector(trace)
        val userLogical = state.labelOffsetsTraceBokorys[trace.id] ?: Offset.Zero

        val baselineAnchor = localToScreen(
            local = logicalBase + userLogical,
            scale = state.scale,
            offset = state.canvasOffset
        ) + baseScreenOffsetPx

        val shiftY = baselineToTopShiftPx(textSize.height)
        val textTopLeft = Offset(baselineAnchor.x, baselineAnchor.y - shiftY)
        val hitTopLeft = textTopLeft - Offset(pad, pad)

        DraggableLabelHitbox(
            key = "bokorys-trace-${trace.id}",
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = Offset(pad, pad) + Offset(0f, shiftY),
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,
            getUserLogical = { state.labelOffsetsTraceBokorys[trace.id] ?: Offset.Zero },
            setUserLogical = { state.labelOffsetsTraceBokorys[trace.id] = it },
            state = state,
            hitboxSizePx = hitSize,
            show3DTag = (parentPlane != null),
            labelScaleForUi = scale,
            onTap = {
                if (parentPlane != null) {
                    toggleSelectionPlane(parentPlane, state)
                } else {
                    val traceb = state.lineTracesBokorys.find { it.id == trace.id }
                    if (traceb != null) toggleSelectionBokorysLine(traceb, state)
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
                    state.bokorysTracePendingForNaming =
                        state.lineTracesBokorys.find { it.id == trace.id }
                    state.planeNameInput = trace.name.orEmpty()
                    state.isNameConfirmed = false
                    state.showPlaneNamingDialog = true
                }
            }
        )
    }
}
fun DrawScope.drawTraceBokorysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    projector: (PlaneTraceBokorys) -> Offset = { Offset(it.point.y, it.point.z) }
) {
    val labelScale = exportLabelScale(state, scale, pxFactor)

    val font = fontPx * labelScale
    val supFont = font * 0.7f

    val baseScreenOffsetPx =
        scaledOffset(Offset(12f, -12f), pxFactor) * labelScale

    val supDy = font * 0.35f

    val indexGap = 2f * labelScale
    val supExtraDx = 2f * pxFactor
    val isAxo = state.projectionMode == ProjectionMode.AXO

    for (trace in state.lineTracesBokorys) {
        if (isAxo && !trace.showInAxo) continue
        if (isAxoAxisId(trace.id) || isAxoAxisId(trace.parent?.id)) continue

        val upper = trace.name?.takeIf { it.isNotBlank() } ?: continue

        val baseLocal = projector(trace)
        val userLocal = state.labelOffsetsTraceBokorys[trace.id] ?: Offset.Zero

        val pos = localToScreen(
            local = baseLocal + userLocal,
            scale = scale,
            offset = offset
        ) + baseScreenOffsetPx

        val baseName = "b\u2083"

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
