package draw.mongescreen.labels

import monge.input.axo.axoOverlayToScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.previews.tools.PdfExportFonts
import model.Mongeobjects
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.AxoOverlaySegment
import model.classes.HelpSegmentNarys
import model.classes.HelpSegmentPudorys
import model.classes.Segment2DAxo
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.SegmentsBokorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import monge.input.axo.AxoRenderBasis
import monge.input.axo.currentAxoSegmentLocal
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.toScreen

private data class SegmentLabelEntry(
    val id: String,
    val name: String,
    val color: Color,
    val logicalBase: Offset,
    val userOffsets: MutableMap<String, Offset>,
    val show3DTag: Boolean,
    val onTap: () -> Unit = {},
    val onDoubleTap: () -> Unit = {}
) {
    val parts: List<RichLabelPart>
        get() = listOf(RichLabelPart(name))
}

private fun trimmedSegmentName(name: String?): String? =
    name?.trim()?.takeIf { it.isNotBlank() }

private fun Segment2DPudorys.parentIdEffective(): String? = parent?.id ?: parentId
private fun Segment2DNarys.parentIdEffective(): String? = parent?.id ?: parentId
private fun Segment2DBokorys.parentIdEffective(): String? = parent?.id ?: parentId
private fun Segment2DAxo.parentIdEffective(): String? = parent?.id ?: parentId

private fun SegmentsPudorys.effectiveName(state: MongeState): String? {
    val parentName = when (this) {
        is Segment2DPudorys -> parent?.name ?: parentId?.let { id -> state.segments3D.firstOrNull { it.id == id }?.name }
        is HelpSegmentPudorys -> parent?.name
        else -> parent?.name
    }
    return trimmedSegmentName(parentName ?: name)
}

private fun SegmentsNarys.effectiveName(state: MongeState): String? {
    val parentName = when (this) {
        is Segment2DNarys -> parent?.name ?: parentId?.let { id -> state.segments3D.firstOrNull { it.id == id }?.name }
        is HelpSegmentNarys -> parent?.name
        else -> parent?.name
    }
    return trimmedSegmentName(parentName ?: name)
}

private fun SegmentsBokorys.effectiveName(state: MongeState): String? {
    val parentName = when (this) {
        is Segment2DBokorys -> parent?.name ?: parentId?.let { id -> state.segments3D.firstOrNull { it.id == id }?.name }
        else -> parent?.name
    }
    return trimmedSegmentName(parentName ?: name)
}

private fun Segment2DAxo.effectiveName(state: MongeState): String? {
    val parentName = parent?.name ?: parentId?.let { id -> state.segments3D.firstOrNull { it.id == id }?.name }
    return trimmedSegmentName(parentName ?: name)
}

private fun AxoOverlaySegment.effectiveName(): String? =
    trimmedSegmentName(name)

private fun segmentLabelBasePx(): Offset = Offset(12f, -12f)

private fun narysLikeSegmentLabelBasePx(state: MongeState): Offset {
    val flipY = state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    val flipX = state.xAxisDirection == XAxisDirection.POSITIVE_LEFT
    return Offset(if (flipX) -12f else 12f, if (flipY) 12f else -12f)
}

private fun screenProjectorForOrth(state: MongeState): (Offset) -> Offset = { logical ->
    logical.toScreen(
        scale = state.scale,
        offset = state.canvasOffset,
        canvasHeight = state.canvasHeight,
        state = state,
        canvasWidth = state.canvasWidth
    )
}

private fun screenProjectorForWorkspace(state: MongeState): (Offset) -> Offset = { workspace ->
    localToScreen(workspace, state.scale, state.canvasOffset)
}

@Composable
private fun SegmentLabelHitbox(
    state: MongeState,
    entry: SegmentLabelEntry,
    keyPrefix: String,
    baseScreenOffsetPx: Offset,
    screenProjector: (Offset) -> Offset,
    labelScale: Float
) {
    val s = if (SettingsManager.current.scaleLabelsWithCanvas) labelScale else 1f
    val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s
    val metrics = remember(entry.name, fontPx) { measureRichLabelMetrics(entry.parts, fontPx) }

    val pad = 4f * s
    val hitSize = Size(metrics.width + 2f * pad, (metrics.bottom - metrics.top) + 2f * pad)

    val userLogical = entry.userOffsets[entry.id] ?: Offset.Zero
    val baselineAnchor = screenProjector(entry.logicalBase + userLogical) + baseScreenOffsetPx
    val textTopLeft = Offset(baselineAnchor.x, baselineAnchor.y + metrics.top)
    val hitTopLeft = textTopLeft - Offset(pad, pad)

    DraggableLabelHitbox(
        key = "$keyPrefix-${entry.id}",
        finalScreen = hitTopLeft,
        textShiftFromHitboxPx = Offset(pad, pad - metrics.top),
        baseScreenOffsetPx = baseScreenOffsetPx,
        logicalBase = entry.logicalBase,
        getUserLogical = { entry.userOffsets[entry.id] ?: Offset.Zero },
        setUserLogical = { value -> entry.userOffsets[entry.id] = value },
        state = state,
        hitboxSizePx = hitSize,
        show3DTag = entry.show3DTag,
        labelScaleForUi = labelScale,
        onTap = entry.onTap,
        onDoubleTap = entry.onDoubleTap
    )
}

private fun selectPudorysSegment(state: MongeState, segment: SegmentsPudorys) {
    if (state.drawobjects != Mongeobjects.NONE) return
    if (state.isShiftPressed) {
        val idx = state.selectedSegmentsPudorys.indexOfFirst { it.id == segment.id }
        if (idx >= 0) state.selectedSegmentsPudorys.removeAt(idx) else state.selectedSegmentsPudorys.add(segment)
    } else {
        clearSelection(state)
        state.selectedSegmentsPudorys.add(segment)
    }
}

private fun selectNarysSegment(state: MongeState, segment: SegmentsNarys) {
    if (state.drawobjects != Mongeobjects.NONE) return
    if (state.isShiftPressed) {
        val idx = state.selectedSegmentsNarys.indexOfFirst { it.id == segment.id }
        if (idx >= 0) state.selectedSegmentsNarys.removeAt(idx) else state.selectedSegmentsNarys.add(segment)
    } else {
        clearSelection(state)
        state.selectedSegmentsNarys.add(segment)
    }
}

private fun selectBokorysSegment(state: MongeState, segment: SegmentsBokorys) {
    if (state.drawobjects != Mongeobjects.NONE) return
    if (state.isShiftPressed) {
        val idx = state.selectedSegmentsBokorys.indexOfFirst { it.id == segment.id }
        if (idx >= 0) state.selectedSegmentsBokorys.removeAt(idx) else state.selectedSegmentsBokorys.add(segment)
    } else {
        clearSelection(state)
        state.selectedSegmentsBokorys.add(segment)
    }
}

private fun selectAxoSegment(state: MongeState, segment: Segment2DAxo) {
    if (state.drawobjects != Mongeobjects.NONE) return
    if (state.isShiftPressed) {
        val idx = state.selectedSegmentsAxo.indexOfFirst { it.id == segment.id }
        if (idx >= 0) state.selectedSegmentsAxo.removeAt(idx) else state.selectedSegmentsAxo.add(segment)
    } else {
        clearSelection(state)
        state.selectedSegmentsAxo.add(segment)
    }
}

private fun selectAOSegment(state: MongeState, segment: AxoOverlaySegment) {
    if (state.drawobjects != Mongeobjects.NONE) return
    if (state.isShiftPressed) {
        if (segment.id in state.selectedAOSegIds) state.selectedAOSegIds.remove(segment.id)
        else state.selectedAOSegIds.add(segment.id)
    } else {
        clearSelection(state)
        state.selectedAOSegIds.add(segment.id)
    }
}

private fun beginPudorysSegmentRename(state: MongeState, segment: SegmentsPudorys) {
    if (state.drawobjects != Mongeobjects.NONE) return
    val live = state.segmentsPudorys.firstOrNull { it.id == segment.id }
        ?: state.helpSegmentsPudorys.firstOrNull { it.id == segment.id }
        ?: segment
    state.inputName = live.effectiveName(state).orEmpty()
    state.isNameConfirmed = false
    state.rename.segmentBeingRenamedPudorys = live
    setProjectionPhase("rename_segment_pudorys", state)
}

private fun beginNarysSegmentRename(state: MongeState, segment: SegmentsNarys) {
    if (state.drawobjects != Mongeobjects.NONE) return
    val live = state.segmentsNarys.firstOrNull { it.id == segment.id }
        ?: state.helpSegmentsNarys.firstOrNull { it.id == segment.id }
        ?: segment
    state.inputName = live.effectiveName(state).orEmpty()
    state.isNameConfirmed = false
    state.rename.segmentBeingRenamedNarys = live
    setProjectionPhase("rename_segment_narys", state)
}

private fun beginBokorysSegmentRename(state: MongeState, segment: SegmentsBokorys) {
    if (state.drawobjects != Mongeobjects.NONE) return
    val live = state.segmentsBokorys.firstOrNull { it.id == segment.id } ?: segment
    state.inputName = live.effectiveName(state).orEmpty()
    state.isNameConfirmed = false
    state.rename.segmentBeingRenamedBokorys = live
    setProjectionPhase("rename_segment_bokorys", state)
}

private fun beginAxoSegmentRename(state: MongeState, segment: Segment2DAxo) {
    if (state.drawobjects != Mongeobjects.NONE) return
    val live = state.segmentsAxo.firstOrNull { it.id == segment.id } ?: segment
    state.inputName = live.effectiveName(state).orEmpty()
    state.isNameConfirmed = false
    state.rename.segmentBeingRenamedAxo = live
    setProjectionPhase("rename_segment_axo", state)
}

private fun beginAOSegmentRename(state: MongeState, segment: AxoOverlaySegment) {
    if (state.drawobjects != Mongeobjects.NONE) return
    val live = state.axoOverlaySegments.firstOrNull { it.id == segment.id } ?: segment
    state.inputName = live.effectiveName().orEmpty()
    state.isNameConfirmed = false
    state.pendingAOSegment = live
    setProjectionPhase("rename_segment_ao", state)
}

private fun pudorysSegmentEntries(
    state: MongeState,
    includeRegular: Boolean,
    includeHelp: Boolean,
    projector: (Float, Float) -> Offset
): List<SegmentLabelEntry> {
    val entries = mutableListOf<SegmentLabelEntry>()
    if (includeRegular) {
        state.segmentsPudorys
            .filter { state.projectionMode != ProjectionMode.AXO || it.showInAxo }
            .mapNotNullTo(entries) { segment ->
                val name = segment.effectiveName(state) ?: return@mapNotNullTo null
                val midX = (segment.start.x + segment.end.x) / 2f
                val midY = (segment.start.y + segment.end.y) / 2f
                SegmentLabelEntry(
                    id = segment.id,
                    name = name,
                    color = segment.color,
                    logicalBase = if (state.projectionMode == ProjectionMode.AXO) projector(midX, midY) else Offset(midX, midY),
                    userOffsets = state.labelOffsetsSegmentsPudorys,
                    show3DTag = segment.parentIdEffective() != null,
                    onTap = { selectPudorysSegment(state, segment) },
                    onDoubleTap = { beginPudorysSegmentRename(state, segment) }
                )
            }
    }
    if (includeHelp && state.projectionMode != ProjectionMode.AXO) {
        state.helpSegmentsPudorys.mapNotNullTo(entries) { segment ->
            val name = segment.effectiveName(state) ?: return@mapNotNullTo null
            val midX = (segment.start.x + segment.end.x) / 2f
            val midY = (segment.start.y + segment.end.y) / 2f
            SegmentLabelEntry(
                id = segment.id,
                name = name,
                color = segment.color,
                logicalBase = Offset(midX, midY),
                userOffsets = state.labelOffsetsHelpSegmentsPudorys,
                show3DTag = segment.parent != null,
                onTap = { selectPudorysSegment(state, segment) },
                onDoubleTap = { beginPudorysSegmentRename(state, segment) }
            )
        }
    }
    return entries
}

private fun narysSegmentEntries(
    state: MongeState,
    includeRegular: Boolean,
    includeHelp: Boolean,
    projector: (Float, Float) -> Offset
): List<SegmentLabelEntry> {
    val entries = mutableListOf<SegmentLabelEntry>()
    if (includeRegular) {
        state.segmentsNarys
            .filter { state.projectionMode != ProjectionMode.AXO || it.showInAxo }
            .mapNotNullTo(entries) { segment ->
                val name = segment.effectiveName(state) ?: return@mapNotNullTo null
                val midX = (segment.start.x + segment.end.x) / 2f
                val midZ = (segment.start.z + segment.end.z) / 2f
                SegmentLabelEntry(
                    id = segment.id,
                    name = name,
                    color = segment.color,
                    logicalBase = if (state.projectionMode == ProjectionMode.AXO) projector(midX, midZ) else Offset(midX, -midZ),
                    userOffsets = state.labelOffsetsSegmentsNarys,
                    show3DTag = segment.parentIdEffective() != null,
                    onTap = { selectNarysSegment(state, segment) },
                    onDoubleTap = { beginNarysSegmentRename(state, segment) }
                )
            }
    }
    if (includeHelp && state.projectionMode != ProjectionMode.AXO) {
        state.helpSegmentsNarys.mapNotNullTo(entries) { segment ->
            val name = segment.effectiveName(state) ?: return@mapNotNullTo null
            val midX = (segment.start.x + segment.end.x) / 2f
            val midZ = (segment.start.z + segment.end.z) / 2f
            SegmentLabelEntry(
                id = segment.id,
                name = name,
                color = segment.color,
                logicalBase = Offset(midX, -midZ),
                userOffsets = state.labelOffsetsHelpSegmentsNarys,
                show3DTag = segment.parent != null,
                onTap = { selectNarysSegment(state, segment) },
                onDoubleTap = { beginNarysSegmentRename(state, segment) }
            )
        }
    }
    return entries
}

private fun bokorysSegmentEntries(
    state: MongeState,
    projector: (Float, Float) -> Offset
): List<SegmentLabelEntry> =
    state.segmentsBokorys
        .filter { state.projectionMode != ProjectionMode.AXO || it.showInAxo }
        .mapNotNull { segment ->
            val name = segment.effectiveName(state) ?: return@mapNotNull null
            val midY = (segment.start.y + segment.end.y) / 2f
            val midZ = (segment.start.z + segment.end.z) / 2f
            SegmentLabelEntry(
                id = segment.id,
                name = name,
                color = segment.color,
                logicalBase = if (state.projectionMode == ProjectionMode.AXO) projector(midY, midZ) else Offset(midY, -midZ),
                userOffsets = state.labelOffsetsSegmentsBokorys,
                show3DTag = segment.parentIdEffective() != null,
                onTap = { selectBokorysSegment(state, segment) },
                onDoubleTap = { beginBokorysSegmentRename(state, segment) }
            )
        }

private fun axoSegmentEntries(state: MongeState, basis: AxoRenderBasis): List<SegmentLabelEntry> =
    state.segmentsAxo
        .filter { it.showInAxo }
        .mapNotNull { segment ->
            val name = segment.effectiveName(state) ?: return@mapNotNull null
            val (a, b) = segment.currentAxoSegmentLocal(basis)
            SegmentLabelEntry(
                id = segment.id,
                name = name,
                color = segment.color,
                logicalBase = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f),
                userOffsets = state.labelOffsetsSegmentsAxo,
                show3DTag = segment.parentIdEffective() != null,
                onTap = { selectAxoSegment(state, segment) },
                onDoubleTap = { beginAxoSegmentRename(state, segment) }
            )
        }

private fun aoSegmentEntries(state: MongeState): List<SegmentLabelEntry> =
    state.axoOverlaySegments.mapNotNull { segment ->
        val name = segment.effectiveName() ?: return@mapNotNull null
        SegmentLabelEntry(
            id = segment.id,
            name = name,
            color = segment.color,
            logicalBase = Offset((segment.start.x + segment.end.x) / 2f, (segment.start.y + segment.end.y) / 2f),
            userOffsets = state.labelOffsetsAOSegments,
            show3DTag = false,
            onTap = { selectAOSegment(state, segment) },
            onDoubleTap = { beginAOSegmentRename(state, segment) }
        )
    }

@Composable
fun LabelsPudorysSegments(
    state: MongeState,
    includeRegular: Boolean = true,
    includeHelp: Boolean = false,
    projector: (Float, Float) -> Offset = { x, y -> Offset(x, y) }
) {
    val labelScale = rememberLabelScale(state)
    val basePx = segmentLabelBasePx()
    val baseScreenOffsetPx = if (SettingsManager.current.scaleLabelsWithCanvas) basePx * labelScale else basePx
    val screenProjector =
        if (state.projectionMode == ProjectionMode.AXO) screenProjectorForWorkspace(state) else screenProjectorForOrth(state)

    pudorysSegmentEntries(state, includeRegular, includeHelp, projector).forEach { entry ->
        SegmentLabelHitbox(state, entry, "pudorys-segment", baseScreenOffsetPx, screenProjector, labelScale)
    }
}

@Composable
fun LabelsNarysSegments(
    state: MongeState,
    includeRegular: Boolean = true,
    includeHelp: Boolean = false,
    projector: (Float, Float) -> Offset = { x, z -> Offset(x, z) }
) {
    val labelScale = rememberLabelScale(state)
    val basePx = narysLikeSegmentLabelBasePx(state)
    val baseScreenOffsetPx = if (SettingsManager.current.scaleLabelsWithCanvas) basePx * labelScale else basePx
    val screenProjector =
        if (state.projectionMode == ProjectionMode.AXO) screenProjectorForWorkspace(state) else screenProjectorForOrth(state)

    narysSegmentEntries(state, includeRegular, includeHelp, projector).forEach { entry ->
        SegmentLabelHitbox(state, entry, "narys-segment", baseScreenOffsetPx, screenProjector, labelScale)
    }
}

@Composable
fun LabelsBokorysSegments(
    state: MongeState,
    projector: (Float, Float) -> Offset = { y, z -> Offset(y, z) }
) {
    val labelScale = rememberLabelScale(state)
    val basePx = narysLikeSegmentLabelBasePx(state)
    val baseScreenOffsetPx = if (SettingsManager.current.scaleLabelsWithCanvas) basePx * labelScale else basePx
    val screenProjector =
        if (state.projectionMode == ProjectionMode.AXO) screenProjectorForWorkspace(state) else screenProjectorForOrth(state)

    bokorysSegmentEntries(state, projector).forEach { entry ->
        SegmentLabelHitbox(state, entry, "bokorys-segment", baseScreenOffsetPx, screenProjector, labelScale)
    }
}

@Composable
fun LabelsAxoSegments(state: MongeState) {
    val basis = state.basis ?: return
    val labelScale = rememberLabelScale(state)
    val basePx = segmentLabelBasePx()
    val baseScreenOffsetPx = if (SettingsManager.current.scaleLabelsWithCanvas) basePx * labelScale else basePx
    val screenProjector: (Offset) -> Offset = { local -> axoOverlayToScreen(local, state, basis) }

    axoSegmentEntries(state, basis).forEach { entry ->
        SegmentLabelHitbox(state, entry, "axo-segment", baseScreenOffsetPx, screenProjector, labelScale)
    }
}

@Composable
fun LabelsOverlaySegments(state: MongeState) {
    val basis = state.basis ?: return
    val labelScale = rememberLabelScale(state)
    val basePx = segmentLabelBasePx()
    val baseScreenOffsetPx = if (SettingsManager.current.scaleLabelsWithCanvas) basePx * labelScale else basePx
    val screenProjector: (Offset) -> Offset = { local -> axoOverlayToScreen(local, state, basis) }

    aoSegmentEntries(state).forEach { entry ->
        SegmentLabelHitbox(state, entry, "ao-segment", baseScreenOffsetPx, screenProjector, labelScale)
    }
}

private fun DrawScope.drawSegmentLabelEntries(
    entries: List<SegmentLabelEntry>,
    state: MongeState,
    scale: Float,
    fontPx: Float,
    pxFactor: Float,
    basePx: Offset,
    screenProjector: (Offset) -> Offset
) {
    val labelScale = exportLabelScale(state, scale, pxFactor)
    val font = fontPx * labelScale
    val baseScreenOffsetPx = scaledOffset(basePx, pxFactor) * labelScale

    entries.forEach { entry ->
        val userLogical = entry.userOffsets[entry.id] ?: Offset.Zero
        val anchor = screenProjector(entry.logicalBase + userLogical) + baseScreenOffsetPx
        drawRichLabel(
            parts = entry.parts,
            anchor = anchor,
            color = entry.color,
            baseFontPx = font
        )
    }
}

fun DrawScope.drawSegmentPudorysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    includeRegular: Boolean = true,
    includeHelp: Boolean = false,
    projector: (Float, Float) -> Offset = { x, y -> Offset(x, y) }
) {
    val screenProjector =
        if (state.projectionMode == ProjectionMode.AXO) {
            { p: Offset -> localToScreen(p, scale, offset) }
        } else {
            { p: Offset -> p.toScreen(scale, offset, size.height, state, size.width) }
        }
    drawSegmentLabelEntries(
        entries = pudorysSegmentEntries(state, includeRegular, includeHelp, projector),
        state = state,
        scale = scale,
        fontPx = fontPx,
        pxFactor = pxFactor,
        basePx = segmentLabelBasePx(),
        screenProjector = screenProjector
    )
}

fun DrawScope.drawSegmentNarysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    includeRegular: Boolean = true,
    includeHelp: Boolean = false,
    projector: (Float, Float) -> Offset = { x, z -> Offset(x, z) }
) {
    val screenProjector =
        if (state.projectionMode == ProjectionMode.AXO) {
            { p: Offset -> localToScreen(p, scale, offset) }
        } else {
            { p: Offset -> p.toScreen(scale, offset, size.height, state, size.width) }
        }
    drawSegmentLabelEntries(
        entries = narysSegmentEntries(state, includeRegular, includeHelp, projector),
        state = state,
        scale = scale,
        fontPx = fontPx,
        pxFactor = pxFactor,
        basePx = segmentLabelBasePx(),
        screenProjector = screenProjector
    )
}

fun DrawScope.drawSegmentBokorysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 14f,
    pxFactor: Float = 1f,
    projector: (Float, Float) -> Offset = { y, z -> Offset(y, z) }
) {
    val screenProjector =
        if (state.projectionMode == ProjectionMode.AXO) {
            { p: Offset -> localToScreen(p, scale, offset) }
        } else {
            { p: Offset -> p.toScreen(scale, offset, size.height, state, size.width) }
        }
    drawSegmentLabelEntries(
        entries = bokorysSegmentEntries(state, projector),
        state = state,
        scale = scale,
        fontPx = fontPx,
        pxFactor = pxFactor,
        basePx = segmentLabelBasePx(),
        screenProjector = screenProjector
    )
}

fun DrawScope.drawAxoSegmentLabels(
    state: MongeState,
    fontPx: Float = 14f,
    pxFactor: Float = 1f
) {
    val basis = state.basis ?: return
    val screenProjector: (Offset) -> Offset = { local -> axoOverlayToScreen(local, state, basis) }
    drawSegmentLabelEntries(
        entries = axoSegmentEntries(state, basis),
        state = state,
        scale = state.scale,
        fontPx = fontPx,
        pxFactor = pxFactor,
        basePx = segmentLabelBasePx(),
        screenProjector = screenProjector
    )
}

fun DrawScope.drawAxoOverlaySegmentLabels(
    state: MongeState,
    fontPx: Float = 14f,
    pxFactor: Float = 1f
) {
    val basis = state.basis ?: return
    val screenProjector: (Offset) -> Offset = { local -> axoOverlayToScreen(local, state, basis) }
    drawSegmentLabelEntries(
        entries = aoSegmentEntries(state),
        state = state,
        scale = state.scale,
        fontPx = fontPx,
        pxFactor = pxFactor,
        basePx = segmentLabelBasePx(),
        screenProjector = screenProjector
    )
}






