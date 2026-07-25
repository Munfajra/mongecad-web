package draw.mongescreen.labels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.tools.PdfExportFonts
import model.Mongeobjects
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.Line3DProjectionBokorys
import model.classes.PlaneTraceBokorys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import dialogs.nameInput.beginPlaneTraceRename
import monge.input.selection.toggleSelectionPlane
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen
import kotlin.math.hypot
import kotlin.math.roundToInt

private sealed interface BokorysLabelSource {
    val id: String
    val effectiveCreationIndex: Long
    fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionBokorys) -> Offset, projectorTrace: (PlaneTraceBokorys) -> Offset): Offset
    fun labelPart(state: MongeState): RichLabelPart?
}

private data class BokorysLineSource(val line: Line3DProjectionBokorys) : BokorysLabelSource {
    override val id: String get() = line.id
    override val effectiveCreationIndex: Long get() = line.effectiveCreationIndex
    override fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionBokorys) -> Offset, projectorTrace: (PlaneTraceBokorys) -> Offset): Offset =
        if (state.projectionMode == ProjectionMode.AXO) {
            val p = line.parent
            if (p != null) projectorLine(line.copy(point = line.point.copy(y = p.start.y, z = p.start.z)))
            else projectorLine(line)
        } else {
            val p = line.parent
            if (p != null) Offset(p.start.y, -p.start.z) else Offset(line.point.y, -line.point.z)
        }
    override fun labelPart(state: MongeState): RichLabelPart? {
        val raw = line.name ?: return null
        val base = if (state.projectionMode == ProjectionMode.PLANE || raw == "\u2083") raw.removeSuffix("\u2083") else raw
        if (base.isBlank()) return null
        return RichLabelPart(base = base, superscript = line.superscript.orEmpty())
    }
}

private data class BokorysTraceSource(val trace: PlaneTraceBokorys) : BokorysLabelSource {
    override val id: String get() = trace.id
    override val effectiveCreationIndex: Long get() = trace.effectiveCreationIndex
    override fun logicalBase(state: MongeState, projectorLine: (Line3DProjectionBokorys) -> Offset, projectorTrace: (PlaneTraceBokorys) -> Offset): Offset =
        if (state.projectionMode == ProjectionMode.AXO) projectorTrace(trace) else Offset(trace.point.y, -trace.point.z)
    override fun labelPart(state: MongeState): RichLabelPart? {
        val sup = trace.name?.takeIf { it.isNotBlank() } ?: return null
        return RichLabelPart(base = "b\u2083", superscript = sup)
    }
}

private data class BokorysLineLabelGroup(
    val key: String,
    val logicalBase: Offset,
    val sources: List<BokorysLabelSource>,
    val parts: List<RichLabelPart>
)

private const val DIR_BUCKET = 0.03f
private const val POS_BUCKET = 0.20f
private const val DIR_BUCKET_AXO = 0.08f
private const val POS_BUCKET_AXO = 0.50f
private fun bucket(v: Float, step: Float): Float = (v / step).roundToInt() * step
private fun bokorysCarrierKey(point: Offset, direction: Offset, dirBucket: Float, posBucket: Float): String {
    val len = hypot(direction.x, direction.y).takeIf { it > 1e-5f } ?: return "deg:${bucket(point.x, posBucket)}:${bucket(point.y, posBucket)}"
    var ux = direction.x / len
    var uy = direction.y / len
    if (ux < 0f || (ux == 0f && uy < 0f)) { ux = -ux; uy = -uy }
    val nx = -uy
    val ny = ux
    val c = nx * point.x + ny * point.y
    return "${bucket(ux, dirBucket)}:${bucket(uy, dirBucket)}:${bucket(c, posBucket)}"
}

private fun BokorysLabelSource.carrierDirection(state: MongeState): Offset {
    val d = when (this) {
        is BokorysLineSource -> line.direction
        is BokorysTraceSource -> trace.direction
    }
    return if (state.projectionMode == ProjectionMode.AXO) d else Offset(d.x, -d.y)
}

private fun BokorysLabelSource.carrierDirection(
    state: MongeState,
    projectorLine: (Line3DProjectionBokorys) -> Offset,
    projectorTrace: (PlaneTraceBokorys) -> Offset
): Offset {
    if (state.projectionMode != ProjectionMode.AXO) return carrierDirection(state)
    return when (this) {
        is BokorysLineSource -> {
            val p = line.parent
            val baseLine = if (p != null) line.copy(point = line.point.copy(y = p.start.y, z = p.start.z)) else line
            val dirY = p?.direction?.y ?: line.direction.x
            val dirZ = p?.direction?.z ?: line.direction.y
            val base = projectorLine(baseLine)
            val tip = projectorLine(
                baseLine.copy(
                    point = baseLine.point.copy(
                        y = baseLine.point.y + dirY,
                        z = baseLine.point.z + dirZ
                    )
                )
            )
            tip - base
        }
        is BokorysTraceSource -> {
            val base = projectorTrace(trace)
            val tip = projectorTrace(
                trace.copy(
                    point = trace.point.copy(
                        y = trace.point.y + trace.direction.x,
                        z = trace.point.z + trace.direction.y
                    )
                )
            )
            tip - base
        }
    }
}

private fun BokorysLabelSource.offsetInState(
    state: MongeState,
    projectorTrace: (PlaneTraceBokorys) -> Offset
): Offset =
    when (this) {
        is BokorysLineSource -> state.labelOffsetsBokorys[line.id] ?: Offset.Zero
        is BokorysTraceSource -> state.labelOffsetsTraceBokorys[trace.id]
            ?: defaultBokorysTraceLabelOffset(state, trace, projectorTrace)
    }

private fun BokorysLabelSource.setOffsetInState(state: MongeState, value: Offset) {
    when (this) {
        is BokorysLineSource -> state.labelOffsetsBokorys[line.id] = value
        is BokorysTraceSource -> state.labelOffsetsTraceBokorys[trace.id] = value
    }
}

private fun bokorysLabelGroups(
    state: MongeState,
    projectorLine: (Line3DProjectionBokorys) -> Offset,
    projectorTrace: (PlaneTraceBokorys) -> Offset
): List<BokorysLineLabelGroup> {
    val lineSources = state.lines3DBokorys
        .filter { it.showInAxo && !isAxoAxisId(it.id) && !isAxoAxisId(it.parent?.id) }
        .map { BokorysLineSource(it) }
    val traceSources = state.lineTracesBokorys
        .filter {
            (state.projectionMode != ProjectionMode.AXO || it.showInAxo) &&
                    !isAxoAxisId(it.id) &&
                    !isAxoAxisId(it.parent?.id)
        }
        .map { BokorysTraceSource(it) }
    return (lineSources + traceSources).let { sources ->
        val dirBucket = if (state.projectionMode == ProjectionMode.AXO) DIR_BUCKET_AXO else DIR_BUCKET
        val posBucket = if (state.projectionMode == ProjectionMode.AXO) POS_BUCKET_AXO else POS_BUCKET
        sources.groupBy {
            val p = it.logicalBase(state, projectorLine, projectorTrace)
            val dir = it.carrierDirection(state, projectorLine, projectorTrace)
            bokorysCarrierKey(p, dir, dirBucket, posBucket)
        }
    }.mapNotNull { (key, sources) ->
        val sorted = sources.sortedBy { it.effectiveCreationIndex }
        val parts = sorted.mapNotNull { it.labelPart(state) }.distinctBy { "${it.base}\u0000${it.superscript}" }
        if (parts.isEmpty()) return@mapNotNull null
        BokorysLineLabelGroup(key, sorted.first().logicalBase(state, projectorLine, projectorTrace), sorted, parts)
    }
}

fun DrawScope.drawLineBokorysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    projector: (Line3DProjectionBokorys) -> Offset = { Offset(it.point.y, it.point.z) },
    traceProjector: (PlaneTraceBokorys) -> Offset = { Offset(it.point.y, it.point.z) }
) {
    val labelScale = exportLabelScale(state, scale, pxFactor)

    val font = fontPx * labelScale

    val baseScreenOffsetPx =
        scaledOffset(Offset(12f, -12f), pxFactor) * labelScale

    val canvasHeight = size.height
    val canvasWidth = size.width
    val isAxo = state.projectionMode == ProjectionMode.AXO

    for (group in bokorysLabelGroups(state, projector, traceProjector)) {
        val rep = group.sources.first()
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
            is BokorysLineSource -> rep.line.color
            is BokorysTraceSource -> rep.trace.color
        }
        drawRichLabel(parts = group.parts, anchor = pos, color = color, baseFontPx = font)
    }
}

@Composable
fun LabelsBokorysLines(
    state: MongeState,
    projector: (Line3DProjectionBokorys) -> Offset = { Offset(it.point.y, it.point.z) },
    traceProjector: (PlaneTraceBokorys) -> Offset = { Offset(it.point.y, it.point.z) }
) {
    val scale = rememberLabelScale(state)
    val isAxo = state.projectionMode == ProjectionMode.AXO

    val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    val isInvertedX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
    val basePx = Offset(if (isInvertedX) -12f else 12f, if (flipY) 12f else -12f)
    val baseScreenOffsetPx =
        if (SettingsManager.current.scaleLabelsWithCanvas) basePx * scale else basePx

    bokorysLabelGroups(state, projector, traceProjector).forEach { group ->
        val rep = group.sources.first()
        val line = group.sources.firstNotNullOfOrNull { (it as? BokorysLineSource)?.line }
        val trace = group.sources.firstNotNullOfOrNull { (it as? BokorysTraceSource)?.trace }

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
            key = "bokorys-line-${group.key}",
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
                        if (state.isShiftPressed) state.selectedLinesBokorys.add(line)
                        else {
                            clearSelection(state)
                            state.selectedLinesBokorys.add(line)
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
                        val live = state.lines3DBokorys.firstOrNull { it.id == line.id }
                            ?: return@DraggableLabelHitbox

                        state.inputName = live.name?.removeSuffix("₃") ?: ""
                        state.isNameConfirmed = false
                        setProjectionPhase("rename_line_bokorys", state)
                        state.rename.lineBeingRenamedBokorys = live
                    } else if (trace != null) {
                        beginPlaneTraceRename(state, trace)
                    }
                }
            }
        )
    }
}
