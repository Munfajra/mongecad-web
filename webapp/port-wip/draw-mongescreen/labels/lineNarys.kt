package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import model.Mongeobjects
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.Line3DProjectionNarys
import model.classes.PlaneTraceNarys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import dialogs.nameInput.beginPlaneTraceRename
import draw.mongescreen.previews.tools.PdfExportFonts
import monge.input.selection.toggleSelectionPlane
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen
import kotlin.math.hypot
import kotlin.math.roundToInt

private sealed interface NarysLabelSource {
    val id: String
    val effectiveCreationIndex: Long
    fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionNarys) -> Offset, projectorTrace: (PlaneTraceNarys) -> Offset): Offset
    fun labelPart(state: MongeState): RichLabelPart?
}

private data class NarysLineSource(val line: Line3DProjectionNarys) : NarysLabelSource {
    override val id: String get() = line.id
    override val effectiveCreationIndex: Long get() = line.effectiveCreationIndex
    override fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionNarys) -> Offset, projectorTrace: (PlaneTraceNarys) -> Offset): Offset =
        if (state.projectionMode == ProjectionMode.AXO) {
            val p = line.parent
            if (p != null) projectorLine(line.copy(point = line.point.copy(x = p.start.x, z = p.start.z)))
            else projectorLine(line)
        } else {
            val p = line.parent
            if (p != null) Offset(p.start.x, -p.start.z) else Offset(line.point.x, -line.point.z)
        }
    override fun labelPart(state: MongeState): RichLabelPart? {
        val raw = line.name ?: return null
        val base = if (state.projectionMode == ProjectionMode.PLANE || raw == "\u2082") raw.removeSuffix("\u2082") else raw
        if (base.isBlank()) return null
        return RichLabelPart(base = base, superscript = line.superscript.orEmpty())
    }
}

private data class NarysTraceSource(val trace: PlaneTraceNarys) : NarysLabelSource {
    override val id: String get() = trace.id
    override val effectiveCreationIndex: Long get() = trace.effectiveCreationIndex
    override fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionNarys) -> Offset, projectorTrace: (PlaneTraceNarys) -> Offset): Offset =
        if (state.projectionMode == ProjectionMode.AXO) projectorTrace(trace) else Offset(trace.point.x, -trace.point.z)
    override fun labelPart(state: MongeState): RichLabelPart? {
        val sup = trace.name?.takeIf { it.isNotBlank() } ?: return null
        return RichLabelPart(base = "n\u2082", superscript = sup)
    }
}

private data class NarysLineLabelGroup(
    val key: String,
    val logicalBase: Offset,
    val sources: List<NarysLabelSource>,
    val parts: List<RichLabelPart>
)

private const val DIR_BUCKET = 0.03f
private const val POS_BUCKET = 0.20f
private const val DIR_BUCKET_AXO = 0.08f
private const val POS_BUCKET_AXO = 0.50f
private fun bucket(v: Float, step: Float): Float = (v / step).roundToInt() * step
private fun narysCarrierKey(point: Offset, direction: Offset, dirBucket: Float, posBucket: Float): String {
    val len = hypot(direction.x, direction.y).takeIf { it > 1e-5f } ?: return "deg:${bucket(point.x, posBucket)}:${bucket(point.y, posBucket)}"
    var ux = direction.x / len
    var uy = direction.y / len
    if (ux < 0f || (ux == 0f && uy < 0f)) { ux = -ux; uy = -uy }
    val nx = -uy
    val ny = ux
    val c = nx * point.x + ny * point.y
    return "${bucket(ux, dirBucket)}:${bucket(uy, dirBucket)}:${bucket(c, posBucket)}"
}

private fun NarysLabelSource.carrierDirection(state: MongeState): Offset {
    val d = when (this) {
        is NarysLineSource -> line.direction
        is NarysTraceSource -> trace.direction
    }
    return if (state.projectionMode == ProjectionMode.AXO) d else Offset(d.x, -d.y)
}

private fun NarysLabelSource.carrierDirection(
    state: MongeState,
    projectorLine: (Line3DProjectionNarys) -> Offset,
    projectorTrace: (PlaneTraceNarys) -> Offset
): Offset {
    if (state.projectionMode != ProjectionMode.AXO) return carrierDirection(state)
    return when (this) {
        is NarysLineSource -> {
            val p = line.parent
            val baseLine = if (p != null) line.copy(point = line.point.copy(x = p.start.x, z = p.start.z)) else line
            val dirX = p?.direction?.x ?: line.direction.x
            val dirZ = p?.direction?.z ?: line.direction.y
            val base = projectorLine(baseLine)
            val tip = projectorLine(
                baseLine.copy(
                    point = baseLine.point.copy(
                        x = baseLine.point.x + dirX,
                        z = baseLine.point.z + dirZ
                    )
                )
            )
            tip - base
        }
        is NarysTraceSource -> {
            val base = projectorTrace(trace)
            val tip = projectorTrace(
                trace.copy(
                    point = trace.point.copy(
                        x = trace.point.x + trace.direction.x,
                        z = trace.point.z + trace.direction.y
                    )
                )
            )
            tip - base
        }
    }
}

private fun NarysLabelSource.offsetInState(
    state: MongeState,
    projectorTrace: (PlaneTraceNarys) -> Offset
): Offset =
    when (this) {
        is NarysLineSource -> state.labelOffsetsNarys[line.id] ?: Offset.Zero
        is NarysTraceSource -> state.labelOffsetsTraceNarys[trace.id]
            ?: defaultNarysTraceLabelOffset(state, trace, projectorTrace)
    }

private fun NarysLabelSource.setOffsetInState(state: MongeState, value: Offset) {
    when (this) {
        is NarysLineSource -> state.labelOffsetsNarys[line.id] = value
        is NarysTraceSource -> state.labelOffsetsTraceNarys[trace.id] = value
    }
}

private fun narysLabelGroups(
    state: MongeState,
    projectorLine: (Line3DProjectionNarys) -> Offset,
    projectorTrace: (PlaneTraceNarys) -> Offset
): List<NarysLineLabelGroup> {
    val lineSources = state.lines3DNarys
        .filter {
            val isX12 = it.id == "X12_ID" || it.parent?.id == "X12_ID"
            it.showInAxo &&
                    !isAxoAxisId(it.id) &&
                    !isAxoAxisId(it.parent?.id) &&
                    !isX12
        }
        .map { NarysLineSource(it) }
    val traceSources = state.lineTracesNarys
        .filter {
            (state.projectionMode != ProjectionMode.AXO || it.showInAxo) &&
                    !isAxoAxisId(it.id) &&
                    !isAxoAxisId(it.parent?.id)
        }
        .map { NarysTraceSource(it) }
    return (lineSources + traceSources)
        .let { sources ->
            val dirBucket = if (state.projectionMode == ProjectionMode.AXO) DIR_BUCKET_AXO else DIR_BUCKET
            val posBucket = if (state.projectionMode == ProjectionMode.AXO) POS_BUCKET_AXO else POS_BUCKET
            sources.groupBy {
                val p = it.logicalBase(state, projectorLine, projectorTrace)
                val dir = it.carrierDirection(state, projectorLine, projectorTrace)
                narysCarrierKey(p, dir, dirBucket, posBucket)
            }
        }
        .mapNotNull { (key, sources) ->
            val sorted = sources.sortedBy { it.effectiveCreationIndex }
            val parts = sorted.mapNotNull { it.labelPart(state) }.distinctBy { "${it.base}\u0000${it.superscript}" }
            if (parts.isEmpty()) return@mapNotNull null
            NarysLineLabelGroup(key, sorted.first().logicalBase(state, projectorLine, projectorTrace), sorted, parts)
        }
}

fun DrawScope.drawLineNarysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    projector: (Line3DProjectionNarys) -> Offset = { Offset(it.point.x, it.point.z) },
    traceProjector: (PlaneTraceNarys) -> Offset = { Offset(it.point.x, it.point.z) }
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

    for (group in narysLabelGroups(state, projector, traceProjector)) {
        val rep = group.sources.first()
        val repLine = group.sources.firstNotNullOfOrNull { (it as? NarysLineSource)?.line }
        val pos = if (isAxo) {
            val userLocal = rep.offsetInState(state, traceProjector)
            localToScreen(local = group.logicalBase + userLocal, scale = scale, offset = offset) + baseScreenOffsetPx
        } else {
            val userLogical = rep.offsetInState(state, traceProjector)
            (group.logicalBase + userLogical).toScreen(
                scale = scale, offset = offset, canvasHeight = canvasHeight, state = state, canvasWidth = canvasWidth
            ) + baseScreenOffsetPx
        }
        val color = when (rep) {
            is NarysLineSource -> rep.line.color
            is NarysTraceSource -> rep.trace.color
        }
        drawRichLabel(parts = group.parts, anchor = pos, color = color, baseFontPx = font)
    }
}
@Composable
fun LabelsNarysLines(
    state: MongeState,
    projector: (Line3DProjectionNarys) -> Offset = { Offset(it.point.x, it.point.z) },
    traceProjector: (PlaneTraceNarys) -> Offset = { Offset(it.point.x, it.point.z) }
) {

    val scale = rememberLabelScale(state)
    val isAxo = state.projectionMode == ProjectionMode.AXO

    val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    val isInvertedX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
    val basePx = Offset(if (isInvertedX) -12f else 12f, if (flipY) 12f else -12f)

    val baseScreenOffsetPx =
        if (SettingsManager.current.scaleLabelsWithCanvas) basePx * scale else basePx

    narysLabelGroups(state, projector, traceProjector).forEach { group ->
        val rep = group.sources.first()
        val line = group.sources.firstNotNullOfOrNull { (it as? NarysLineSource)?.line }
        val trace = group.sources.firstNotNullOfOrNull { (it as? NarysTraceSource)?.trace }

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
            key = "narys-line-${group.key}",
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = Offset(pad, pad - metrics.top),

            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,

            getUserLogical = { rep.offsetInState(state, traceProjector) },
            setUserLogical = { rep.setOffsetInState(state, it) },

            state = state,
            hitboxSizePx = hitSize,
            show3DTag = (line?.parent != null || line?.parentId != null || trace?.parent != null || trace?.parentId != null),
            labelScaleForUi = scale,

            onTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (line != null) {
                        if (state.isShiftPressed) state.selectedLinesNarys.add(line)
                        else {
                            clearSelection(state)
                            state.selectedLinesNarys.add(line)
                        }
                    } else if (trace != null) {
                        val parentId = trace.parent?.id ?: trace.parentId
                        val parent = parentId?.let { id -> state.planes3D.find { it.id == id } } ?: trace.parent
                        if (parent != null) {
                            if (!state.isShiftPressed) clearSelection(state)
                            toggleSelectionPlane(parent, state)
                        }
                    }
                }
            },

            onDoubleTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (line != null) {
                        val live = state.lines3DNarys.firstOrNull { it.id == line.id } ?: return@DraggableLabelHitbox
                        state.inputName = live.name?.removeSuffix("₂") ?: ""
                        state.isNameConfirmed = false
                        setProjectionPhase("rename_line_narys", state)
                        state.rename.lineBeingRenamedNarys = live
                    } else if (trace != null) {
                        beginPlaneTraceRename(state, trace)
                    }
                }
            }
        )
    }
}
