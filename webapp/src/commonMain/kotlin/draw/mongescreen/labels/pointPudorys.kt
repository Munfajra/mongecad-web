package draw.mongescreen.labels

import utils.withoutProjectionSuffixes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import model.DrawingModeMonge
import model.LocalMongeColors
import model.Mongeobjects
import model.ProjectionMode
import model.classes.Point3DAxo
import model.classes.Point3DPudorys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import monge.input.axo.currentAxoLocal
import state.MongeState
import utils.toScreen
import kotlin.math.roundToInt

private data class PudorysPointLabelGroup(
    val key: String,
    val logicalBase: Offset,
    val points: List<Point3DPudorys>
)

private fun pointGroupKey(offset: Offset): String =
    "${(offset.x * 100f).roundToInt()}:${(offset.y * 100f).roundToInt()}"

private fun Point3DPudorys.labelBaseName(): String =
    parent?.name ?: name.orEmpty().withoutProjectionSuffixes()

private fun Point3DPudorys.labelText(state: MongeState): String {
    val part = labelPart(state)
    return part.base + part.superscript
}

private fun Point3DPudorys.labelPart(state: MongeState): RichLabelPart {
    val baseName = labelBaseName()
    val suffix = if (
        state.projectionMode == ProjectionMode.MONGE ||
        state.projectionMode == ProjectionMode.KOTO ||
        state.projectionMode == ProjectionMode.AXO
    ) "\u2081" else ""
    val kotaSuffix =
        if (state.projectionMode == ProjectionMode.KOTO && parent != null)
            "(${formatKota(parent!!.z / 10f)})"
        else
            ""
    val sup = (localSuperscript ?: parent?.superscript).orEmpty()
    return RichLabelPart(base = baseName + suffix + kotaSuffix, superscript = sup)
}

private fun Point3DAxo.axoLabelBaseName(): String =
    parent?.name ?: name.orEmpty().withoutProjectionSuffixes()

private fun Point3DAxo.axoLabelText(state: MongeState): String {
    val part = axoLabelPart(state)
    return part.base + part.superscript
}

private fun Point3DAxo.axoLabelPart(state: MongeState): RichLabelPart {
    val baseName = axoLabelBaseName()
    val suffix = if (
        state.projectionMode == ProjectionMode.MONGE ||
        state.projectionMode == ProjectionMode.KOTO ||
        state.projectionMode == ProjectionMode.AXO
    ) "\u2090" else ""
    val sup = (localSuperscript ?: parent?.superscript).orEmpty()
    return RichLabelPart(base = baseName + suffix, superscript = sup)
}

private fun pudorysPointLabelGroups(
    state: MongeState,
    projector: (Point3DPudorys) -> Offset
): List<PudorysPointLabelGroup> =
    state.pointsPudorys
        .filter { it.showInAxo && !it.isSegmentEndpoint && it.labelBaseName().isNotBlank() }
        .groupBy { pointGroupKey(projector(it)) }
        .map { (key, points) ->
            val sorted = points.sortedBy { it.effectiveCreationIndex }
            PudorysPointLabelGroup(key, projector(sorted.first()), sorted)
        }

private fun axoLabelsByPudorysKey(state: MongeState): Map<String, List<RichLabelPart>> {
    if (state.projectionMode != ProjectionMode.AXO) return emptyMap()
    val basis = state.basis ?: return emptyMap()

    return state.pointsAxo
        .filter { it.showInAxo && it.axoLabelBaseName().isNotBlank() }
        .groupBy { pointGroupKey(basis.origin + it.currentAxoLocal(basis)) }
        .mapValues { (_, points) ->
            points.sortedBy { it.effectiveCreationIndex }.map { it.axoLabelPart(state) }
        }
}

private fun startPudorysPointRename(state: MongeState, point: Point3DPudorys) {
    if (state.drawobjects != Mongeobjects.NONE) return

    val p = state.sharedPoints3D.firstOrNull { it.id == point.parent?.id }
    if (p != null) {
        state.inputName = p.name
        state.pendingX = p.x
        state.pendingZ = p.z
        state.isNameConfirmed = false
        state.rename.pointBeingRenamed = p
        state.projectionPhase = "pudorys_finalize"
        state.inputSuperscript = p.superscript.emptyIfNullText()
        if (state.projectionMode == ProjectionMode.KOTO) {
            state.isKotaConfirmed = false
            state.inputKota = formatKota(p.z / 10f)
        }
    } else {
        val live = state.pointsPudorys.firstOrNull { it.id == point.id } ?: point
        state.inputName = live.name?.withoutProjectionSuffixes() ?: ""
        state.pendingX = live.x
        state.pendingY = live.y
        state.isNameConfirmed = false
        if (state.projectionMode == ProjectionMode.KOTO) state.isKotaConfirmed = false
        state.rename.pointBeingRenamed = live
        state.inputSuperscript = live.localSuperscript.emptyIfNullText()
        state.projectionPhase =
            if (state.projectionMode == ProjectionMode.KOTO) "KOTO_point" else "rename_point_pudorys"
        if (state.projectionMode == ProjectionMode.KOTO) state.rename.pointPudorysBeingRenamed = live
    }
}

@Composable
private fun PointLabelPickerPopup(
    anchor: Offset,
    points: List<Point3DPudorys>,
    state: MongeState,
    onDismiss: () -> Unit
) {
    Popup(
        offset = IntOffset(anchor.x.toInt(), anchor.y.toInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .background(stateAwarePopupBackground(), RoundedCornerShape(4.dp))
                .border(1.dp, LocalMongeColors.current.base, RoundedCornerShape(4.dp))
                .padding(vertical = 4.dp)
        ) {
            points.forEach { point ->
                Text(
                    text = point.labelText(state),
                    color = LocalMongeColors.current.text,
                    modifier = Modifier
                        .clickable {
                            onDismiss()
                            startPudorysPointRename(state, point)
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun stateAwarePopupBackground(): Color =
    LocalMongeColors.current.background.copy(alpha = 0.96f)

fun DrawScope.drawPointPudorysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 6f,
    pxFactor: Float = 1f,
    projector: (Point3DPudorys) -> Offset = { Offset(it.x, it.y) }
) {
    val labelScale = exportLabelScale(state, scale, pxFactor)

    val font = fontPx * labelScale
    val baseScreenOffsetPx =
        pointLabelBaseScreenOffsetPx(labelScale, pxFactor)

    val canvasHeight = state.canvasHeight
    val canvasWidth = state.canvasWidth
    val axoLabelsByKey = axoLabelsByPudorysKey(state)

    for (group in pudorysPointLabelGroups(state, projector)) {
        val projectionLabels = group.points.map { it.labelPart(state) }
        val labelParts = (
            projectionLabels +
                if (state.projectionMode == ProjectionMode.AXO) axoLabelsByKey[group.key].orEmpty() else emptyList()
            ).distinctBy { "${it.base}\u0000${it.superscript}" }
        val userLogical = group.points
            .mapNotNull { state.labelOffsetsPointsPudorys[it.id] }
            .firstOrNull() ?: Offset.Zero

        val pos =
            if (state.projectionMode == ProjectionMode.AXO) {
                localToScreen(
                    local = group.logicalBase + userLogical,
                    scale = scale,
                    offset = offset
                )
            } else {
                (group.logicalBase + userLogical).toScreen(
                    scale = scale,
                    offset = offset,
                    canvasHeight = canvasHeight,
                    state = state,
                    canvasWidth = canvasWidth
                )
            } + baseScreenOffsetPx

        drawRichLabel(
            parts = labelParts,
            anchor = pos,
            color = group.points.first().color,
            baseFontPx = font
        )
    }
}
@Composable
fun LabelsPudorysPoints(
    state: MongeState,
    projector: (Point3DPudorys) -> Offset = { Offset(it.x, it.y) }
) {
    val scale = rememberLabelScale(state)
    val baseScreenOffsetPx =
        pointLabelBaseScreenOffsetPx(scale)
    val axoLabelsByKey = axoLabelsByPudorysKey(state)

    pudorysPointLabelGroups(state, projector).forEach { group ->
        val firstPoint = group.points.first()
        var pickerOpen by remember(group.key, group.points.size) { mutableStateOf(false) }
        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s
        val projectionLabels = group.points.map { it.labelPart(state) }
        val labelParts = (
            projectionLabels +
                if (state.projectionMode == ProjectionMode.AXO) axoLabelsByKey[group.key].orEmpty() else emptyList()
            ).distinctBy { "${it.base}\u0000${it.superscript}" }
        val labelKey = labelParts.joinToString("|") { "${it.base}^${it.superscript}" }

        val metrics = remember(labelKey, fontPx) { measureRichLabelMetrics(labelParts, fontPx) }

        val pad = 4f * s
        val hitSize = Size(metrics.width + 2 * pad, (metrics.bottom - metrics.top) + 2 * pad)

        val canvasHeight = state.canvasHeight
        val canvasWidth= state.canvasWidth
        val logicalBase = group.logicalBase
        val userLogical = group.points
            .mapNotNull { state.labelOffsetsPointsPudorys[it.id] }
            .firstOrNull() ?: Offset.Zero
        val logical = logicalBase + userLogical

        val baselineAnchor =
            if (state.projectionMode == ProjectionMode.AXO) {
                localToScreen(
                    local = logical,
                    scale = state.scale,
                    offset = state.canvasOffset
                )
            } else {
                logical.toScreen(
                    scale = state.scale,
                    offset = state.canvasOffset,
                    canvasHeight = canvasHeight,
                    state = state,
                    canvasWidth = canvasWidth
                )
            } + baseScreenOffsetPx

        val textTopLeft = Offset(baselineAnchor.x, baselineAnchor.y + metrics.top)

        val hitTopLeft = textTopLeft - Offset(pad, pad)

        DraggableLabelHitbox(
            key = group.key,
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = Offset(pad, pad - metrics.top),
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = logicalBase,
            getUserLogical = {
                group.points.mapNotNull { state.labelOffsetsPointsPudorys[it.id] }.firstOrNull() ?: Offset.Zero
            },
            setUserLogical = { value ->
                group.points.forEach { state.labelOffsetsPointsPudorys[it.id] = value }
            },
            state = state,
            show3DTag = state.projectionMode!= ProjectionMode.PLANE && group.points.any { it.parent != null || it.pendingParentLineId != null },
            labelScaleForUi = scale,
            hitboxSizePx = hitSize,
            onTap = {
                if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.NONE) {
                    if (state.isShiftPressed) state.selectedPointsPudorys.addAll(group.points)
                    else {
                        state.selectedPointsPudorys.clear()
                        state.selectedPointsPudorys.addAll(group.points)
                    }
                }
            },
            onDoubleTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (group.points.size == 1) startPudorysPointRename(state, firstPoint)
                    else pickerOpen = true
                }
            }
        )

        if (pickerOpen) {
            PointLabelPickerPopup(
                anchor = hitTopLeft + Offset(0f, hitSize.height + 4f),
                points = group.points,
                state = state,
                onDismiss = { pickerOpen = false }
            )
        }
    }
}

fun localToScreen(
    local: Offset,
    scale: Float,
    offset: Offset
): Offset {
    return local * scale + offset
}
