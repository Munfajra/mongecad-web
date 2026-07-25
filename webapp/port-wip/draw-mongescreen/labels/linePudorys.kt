package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.Mongeobjects
import model.ProjectionMode
import model.classes.Line3DProjectionPudorys
import model.classes.PlaneTracePudorys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import dialogs.nameInput.beginPlaneTraceRename
import draw.mongescreen.previews.tools.PdfExportFonts
import monge.input.selection.toggleSelectionPlane
import monge.input.selection.toggleSelectionPudorysLine
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen
import kotlin.math.hypot
import kotlin.math.roundToInt

private sealed interface PudorysLabelSource {
    val id: String
    val effectiveCreationIndex: Long
    fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionPudorys) -> Offset, projectorTrace: (PlaneTracePudorys) -> Offset): Offset
    fun labelPart(state: MongeState): RichLabelPart?
}

private data class PudorysLineSource(val line: Line3DProjectionPudorys) : PudorysLabelSource {
    override val id: String get() = line.id
    override val effectiveCreationIndex: Long get() = line.effectiveCreationIndex
    override fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionPudorys) -> Offset, projectorTrace: (PlaneTracePudorys) -> Offset): Offset =
        if (state.projectionMode == ProjectionMode.AXO) {
            val p = line.parent
            if (p != null) projectorLine(line.copy(point = line.point.copy(x = p.start.x, y = p.start.y)))
            else projectorLine(line)
        } else {
            val p = line.parent
            if (p != null) Offset(p.start.x, p.start.y) else Offset(line.point.x, line.point.y)
        }

    override fun labelPart(state: MongeState): RichLabelPart? {
        val raw = line.name ?: return null
        val base = if (state.projectionMode == ProjectionMode.PLANE || raw == "\u2081") raw.removeSuffix("\u2081") else raw
        if (base.isBlank()) return null
        return RichLabelPart(base = base, superscript = line.superscript.orEmpty())
    }
}

private data class PudorysTraceSource(val trace: PlaneTracePudorys) : PudorysLabelSource {
    override val id: String get() = trace.id
    override val effectiveCreationIndex: Long get() = trace.effectiveCreationIndex
    override fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionPudorys) -> Offset, projectorTrace: (PlaneTracePudorys) -> Offset): Offset =
        if (state.projectionMode == ProjectionMode.AXO) projectorTrace(trace) else Offset(trace.point.x, trace.point.y)

    override fun labelPart(state: MongeState): RichLabelPart? {
        val sup = trace.name?.takeIf { it.isNotBlank() } ?: return null
        return RichLabelPart(base = "p\u2081", superscript = sup)
    }
}

private data class PudorysLineLabelGroup(
    val key: String,
    val logicalBase: Offset,
    val sources: List<PudorysLabelSource>,
    val parts: List<RichLabelPart>
)

private const val DIR_BUCKET = 0.03f   // tolerance for normalized direction components
private const val POS_BUCKET = 0.20f   // tolerance for line offset (n·p) in logical units
private const val DIR_BUCKET_AXO = 0.08f
private const val POS_BUCKET_AXO = 0.50f
private fun bucket(v: Float, step: Float): Float = (v / step).roundToInt() * step

private fun pudorysCarrierKey(point: Offset, direction: Offset, dirBucket: Float, posBucket: Float): String {
    val len = hypot(direction.x, direction.y).takeIf { it > 1e-5f } ?: return "deg:${bucket(point.x, posBucket)}:${bucket(point.y, posBucket)}"
    var ux = direction.x / len
    var uy = direction.y / len
    if (ux < 0f || (ux == 0f && uy < 0f)) {
        ux = -ux
        uy = -uy
    }
    val nx = -uy
    val ny = ux
    val c = nx * point.x + ny * point.y
    return "${bucket(ux, dirBucket)}:${bucket(uy, dirBucket)}:${bucket(c, posBucket)}"
}

private fun PudorysLabelSource.carrierDirection(
    state: MongeState,
    projectorLine: (Line3DProjectionPudorys) -> Offset,
    projectorTrace: (PlaneTracePudorys) -> Offset
): Offset {
    if (state.projectionMode != ProjectionMode.AXO) {
        return when (this) {
            is PudorysLineSource -> line.direction
            is PudorysTraceSource -> trace.direction
        }
    }

    return when (this) {
        is PudorysLineSource -> {
            val p = line.parent
            val baseLine = if (p != null) line.copy(point = line.point.copy(x = p.start.x, y = p.start.y)) else line
            val dirX = p?.direction?.x ?: line.direction.x
            val dirY = p?.direction?.y ?: line.direction.y
            val base = projectorLine(baseLine)
            val tip = projectorLine(
                baseLine.copy(
                    point = baseLine.point.copy(
                        x = baseLine.point.x + dirX,
                        y = baseLine.point.y + dirY
                    )
                )
            )
            tip - base
        }
        is PudorysTraceSource -> {
            val base = projectorTrace(trace)
            val tip = projectorTrace(
                trace.copy(
                    point = trace.point.copy(
                        x = trace.point.x + trace.direction.x,
                        y = trace.point.y + trace.direction.y
                    )
                )
            )
            tip - base
        }
    }
}

private fun PudorysLabelSource.offsetInState(
    state: MongeState,
    projectorTrace: (PlaneTracePudorys) -> Offset
): Offset =
    when (this) {
        is PudorysLineSource -> state.labelOffsetsPudorys[line.id] ?: Offset.Zero
        is PudorysTraceSource -> state.labelOffsetsTracePudorys[trace.id]
            ?: defaultPudorysTraceLabelOffset(state, trace, projectorTrace)
    }

private fun PudorysLabelSource.setOffsetInState(state: MongeState, value: Offset) {
    when (this) {
        is PudorysLineSource -> state.labelOffsetsPudorys[line.id] = value
        is PudorysTraceSource -> state.labelOffsetsTracePudorys[trace.id] = value
    }
}

private fun pudorysLabelGroups(
    state: MongeState,
    projectorLine: (Line3DProjectionPudorys) -> Offset,
    projectorTrace: (PlaneTracePudorys) -> Offset
): List<PudorysLineLabelGroup> {
    val lineSources = state.lines3DPudorys
        .filter {
            val isX12 = it.id == "X12_ID" || it.parent?.id == "X12_ID"
            it.showInAxo &&
                    !isAxoAxisId(it.id) &&
                    !isAxoAxisId(it.parent?.id) &&
                    !isX12
        }
        .map { PudorysLineSource(it) }
    val traceSources = state.lineTracesPudorys
        .filter {
            (state.projectionMode != ProjectionMode.AXO || it.showInAxo) &&
                    !isAxoAxisId(it.id) &&
                    !isAxoAxisId(it.parent?.id)
        }
        .map { PudorysTraceSource(it) }

    return (lineSources + traceSources)
        .let { sources ->
            val dirBucket = if (state.projectionMode == ProjectionMode.AXO) DIR_BUCKET_AXO else DIR_BUCKET
            val posBucket = if (state.projectionMode == ProjectionMode.AXO) POS_BUCKET_AXO else POS_BUCKET
            sources.groupBy { src ->
                val p = src.logicalBase(state, projectorLine, projectorTrace)
                val dir = src.carrierDirection(state, projectorLine, projectorTrace)
                pudorysCarrierKey(p, dir, dirBucket, posBucket)
            }
        }
        .mapNotNull { (key, sources) ->
            val sorted = sources.sortedBy { it.effectiveCreationIndex }
            val parts = sorted.mapNotNull { it.labelPart(state) }.distinctBy { "${it.base}\u0000${it.superscript}" }
            if (parts.isEmpty()) return@mapNotNull null
            PudorysLineLabelGroup(key, sorted.first().logicalBase(state, projectorLine, projectorTrace), sorted, parts)
        }
}

@Composable
fun LabelsPudorysLines(
    state: MongeState,
    projector: (Line3DProjectionPudorys) -> Offset = { Offset(it.point.x, it.point.y) },
    traceProjector: (PlaneTracePudorys) -> Offset = { Offset(it.point.x, it.point.y) }
) {
    val scale = rememberLabelScale(state)
    val isAxo = state.projectionMode == ProjectionMode.AXO


    val basePx = Offset(12f, -12f)
    val baseScreenOffsetPx =
        if (SettingsManager.current.scaleLabelsWithCanvas) basePx * scale else basePx

    pudorysLabelGroups(state, projector, traceProjector).forEach { group ->
        val rep = group.sources.first()
        val lineRep = (rep as? PudorysLineSource)?.line
        val traceRep = group.sources.firstNotNullOfOrNull { (it as? PudorysTraceSource)?.trace }

        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s
        val labelKey = group.parts.joinToString("|") { "${it.base}^${it.superscript}" }
        val metrics = remember(labelKey, fontPx) { measureRichLabelMetrics(group.parts, fontPx) }

        val pad = 4f * s
        val hitSize = Size(metrics.width + 2 * pad, (metrics.bottom - metrics.top) + 2 * pad)

        val logicalBase = group.logicalBase
        val userLogical = rep.offsetInState(state, traceProjector)

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

        val textTopLeft = Offset(baselineAnchor.x, baselineAnchor.y + metrics.top)
        val hitTopLeft = textTopLeft - Offset(pad, pad)

        DraggableLabelHitbox(
            key = "pudorys-line-${group.key}",
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = Offset(pad, pad - metrics.top),
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,
            getUserLogical = { rep.offsetInState(state, traceProjector) },
            setUserLogical = { value -> rep.setOffsetInState(state, value) },
            state = state,
            hitboxSizePx = hitSize,
            show3DTag = group.sources.any {
                val line = (it as? PudorysLineSource)?.line
                val trace = (it as? PudorysTraceSource)?.trace
                line?.parent != null || line?.parentId != null || trace?.parent != null || trace?.parentId != null
            },
            labelScaleForUi = scale,
            onTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (lineRep != null) {
                        if (state.isShiftPressed) state.selectedLinesPudorys.add(lineRep)
                        else {
                            clearSelection(state)
                            toggleSelectionPudorysLine(lineRep, state)
                        }
                    } else if (traceRep != null) {
                        val parentId = traceRep.parent?.id ?: traceRep.parentId
                        val parent = parentId?.let { id -> state.planes3D.find { it.id == id } } ?: traceRep.parent
                        if (parent != null) {
                            if (!state.isShiftPressed) clearSelection(state)
                            toggleSelectionPlane(parent, state)
                        }
                    }
                }
            },
            onDoubleTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (lineRep != null) {
                        val live = state.lines3DPudorys.firstOrNull { it.id == lineRep.id } ?: return@DraggableLabelHitbox
                        state.inputName = live.name?.removeSuffix("\u2081") ?: ""
                        state.isNameConfirmed = false
                        setProjectionPhase("rename_line_pudorys", state)
                        state.rename.lineBeingRenamedPudorys = live
                    } else if (traceRep != null) {
                        beginPlaneTraceRename(state, traceRep)
                    }
                }
            }
        )
    }
}

fun DrawScope.drawLinePudorysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    canvasWidthPx: Float,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    projector: (Line3DProjectionPudorys) -> Offset = { Offset(it.point.x, it.point.y) },
    traceProjector: (PlaneTracePudorys) -> Offset = { Offset(it.point.x, it.point.y) }
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

    if (state.projectionMode == ProjectionMode.MONGE) {
        drawX12LabelPudorys(
            state = state,
            scale = scale,
            offset = offset,
            canvasWidthPx = canvasWidthPx,
            font = font,
            pxFactor = pxFactor
        )
    }

    for (group in pudorysLabelGroups(state, projector, traceProjector)) {
        val rep = group.sources.first()
        val repLine = group.sources.firstNotNullOfOrNull { (it as? PudorysLineSource)?.line }
        val pos = if (isAxo) {
            val userLocal = rep.offsetInState(state, traceProjector)
            localToScreen(local = group.logicalBase + userLocal, scale = scale, offset = offset) + baseScreenOffsetPx
        } else {
            val userLogical = rep.offsetInState(state, traceProjector)
            (group.logicalBase + userLogical).toScreen(
                scale = scale,
                offset = offset,
                canvasHeight = canvasHeight,
                state = state,
                canvasWidth = canvasWidth
            ) + baseScreenOffsetPx
        }
        drawRichLabel(parts = group.parts, anchor = pos, color = repLine?.color ?: (rep as? PudorysTraceSource)?.trace?.color ?: androidx.compose.ui.graphics.Color.Black, baseFontPx = font)
    }
}

private fun DrawScope.drawX12LabelPudorys(
    state: MongeState,
    scale: Float,
    offset: Offset,
    canvasWidthPx: Float,
    font: Float,
    pxFactor: Float
) {
    val x12 = state.lines3DPudorys.firstOrNull { it.id == "X12_ID" || it.parent?.id == "X12_ID" } ?: return
    if (!x12.showInAxo) return

    val marginPx = 40f * pxFactor
    val axisScreen = Offset(0f, 0f).toScreen(
        scale = scale,
        offset = offset,
        canvasHeight = size.height,
        state = state,
        canvasWidth = size.width
    )

    val userLogicalY = state.labelOffsetsPudorys[x12.id]?.y ?: 0f
    val screenY = axisScreen.y + userLogicalY * scale + font

    val base = "x"
    val baseSize = measureSkiaParagraph(base, fontPx = font, family = "italic")
    val screenX = canvasWidthPx - marginPx - baseSize.width
    val anchor = Offset(screenX, screenY)

    val nameWidth = drawSkiaText(
        text = base,
        anchor = anchor,
        color = x12.color,
        fontPx = font,
        typefaceFamily = "italic"
    )

    drawSkiaText(
        text = "12",
        anchor = anchor + Offset(nameWidth + 1f * pxFactor, font * 0.2f),
        color = x12.color,
        fontPx = font * 0.5f,
        typefaceFamily = "italic"
    )
}
