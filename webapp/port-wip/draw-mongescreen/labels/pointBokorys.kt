package draw.mongescreen.labels

import utils.withoutProjectionSuffixes
import dialogs.nameInput.withoutProjectionSuffixes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import draw.mongescreen.previews.tools.PdfExportFonts
import model.LocalMongeColors
import model.Mongeobjects
import model.ProjectionMode
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import monge.input.axo.currentAxoLocal
import state.MongeState
import kotlin.math.roundToInt

private data class BokorysPointLabelGroup(
    val key: String,
    val logicalBase: Offset,
    val points: List<Point3DBokorys>
)

private fun bokorysPointGroupKey(offset: Offset): String =
    "${(offset.x * 100f).roundToInt()}:${(offset.y * 100f).roundToInt()}"

private fun Point3DBokorys.labelBaseName(): String =
    parent?.name ?: name.orEmpty().withoutProjectionSuffixes()

private fun Point3DBokorys.labelText(state: MongeState): String {
    val part = labelPart(state)
    return part.base + part.superscript
}

private fun Point3DBokorys.labelPart(state: MongeState): RichLabelPart {
    val baseName = labelBaseName()
    val suffix = if (
        state.projectionMode == ProjectionMode.MONGE ||
        state.projectionMode == ProjectionMode.KOTO ||
        state.projectionMode == ProjectionMode.AXO
    ) "\u2083" else ""
    val sup = (localSuperscript ?: parent?.superscript).orEmpty()
    return RichLabelPart(base = baseName + suffix, superscript = sup)
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

private fun bokorysPointLabelGroups(
    state: MongeState,
    projector: (Point3DBokorys) -> Offset
): List<BokorysPointLabelGroup> =
    state.pointsBokorys
        .filter { it.showInAxo && !it.isSegmentEndpoint && it.labelBaseName().isNotBlank() }
        .groupBy { bokorysPointGroupKey(projector(it)) }
        .map { (key, points) ->
            val sorted = points.sortedBy { it.effectiveCreationIndex }
            BokorysPointLabelGroup(key, projector(sorted.first()), sorted)
        }

private fun axoLabelsByBokorysKey(state: MongeState): Map<String, List<RichLabelPart>> {
    if (state.projectionMode != ProjectionMode.AXO) return emptyMap()
    val basis = state.basis ?: return emptyMap()

    return state.pointsAxo
        .filter { it.showInAxo && it.axoLabelBaseName().isNotBlank() }
        .groupBy { bokorysPointGroupKey(basis.origin + it.currentAxoLocal(basis)) }
        .mapValues { (_, points) ->
            points.sortedBy { it.effectiveCreationIndex }.map { it.axoLabelPart(state) }
        }
}

private fun startBokorysPointRename(state: MongeState, point: Point3DBokorys) {
    if (state.drawobjects != Mongeobjects.NONE) return

    val p = state.sharedPoints3D.firstOrNull { it.id == point.parent?.id }
    if (p != null) {
        state.inputName = p.name
        state.pendingY = p.y
        state.pendingZ = p.z
        state.isNameConfirmed = false
        state.rename.pointBeingRenamed = p
        state.projectionPhase = "bokorys_finalize"
        state.inputSuperscript = p.superscript.emptyIfNullText()
    } else {
        val live = state.pointsBokorys.firstOrNull { it.id == point.id } ?: point
        state.inputName = live.name?.withoutProjectionSuffixes() ?: ""
        state.pendingY = live.y
        state.pendingZ = live.z
        state.isNameConfirmed = false
        state.rename.pointBeingRenamed = live
        state.inputSuperscript = live.localSuperscript.emptyIfNullText()
        state.projectionPhase = "rename_point_pudorys"
    }
}

@Composable
private fun BokorysPointLabelPickerPopup(
    anchor: Offset,
    points: List<Point3DBokorys>,
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
                .background(LocalMongeColors.current.background.copy(alpha = 0.96f), RoundedCornerShape(4.dp))
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
                            startBokorysPointRename(state, point)
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

fun DrawScope.drawPointBokorysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 6f,
    pxFactor: Float = 1f,
    projector: (Point3DBokorys) -> Offset = { Offset(it.y, it.z) }
) {
    val labelScale = exportLabelScale(state, scale, pxFactor)

    val font = fontPx * labelScale
    val baseScreenOffsetPx =
        pointLabelBaseScreenOffsetPx(labelScale, pxFactor)
    val axoLabelsByKey = axoLabelsByBokorysKey(state)

    for (group in bokorysPointLabelGroups(state, projector)) {
        val projectionLabels = group.points.map { it.labelPart(state) }
        val labelParts = (
            projectionLabels +
                if (state.projectionMode == ProjectionMode.AXO) axoLabelsByKey[group.key].orEmpty() else emptyList()
            ).distinctBy { "${it.base}\u0000${it.superscript}" }
        val userLocal = group.points
            .mapNotNull { state.labelOffsetsPointsBokorys[it.id] }
            .firstOrNull() ?: Offset.Zero

        val pos = localToScreen(
            local = group.logicalBase + userLocal,
            scale = scale,
            offset = offset
        ) + baseScreenOffsetPx

        drawRichLabel(
            parts = labelParts,
            anchor = pos,
            color = group.points.first().color,
            baseFontPx = font
        )
    }
}

@Composable
fun LabelsBokorysPoints(
    state: MongeState,
    projector: (Point3DBokorys) -> Offset = { Offset(it.y, it.z) }
) {
    val scale = rememberLabelScale(state)

    val baseScreenOffsetPx =
        pointLabelBaseScreenOffsetPx(scale)

    val isAxo = state.projectionMode == ProjectionMode.AXO
    val axoLabelsByKey = axoLabelsByBokorysKey(state)

    bokorysPointLabelGroups(state, projector).forEach { group ->
        val firstPoint = group.points.first()
        var pickerOpen by remember(group.key, group.points.size) { mutableStateOf(false) }
        val projectionLabels = group.points.map { it.labelPart(state) }
        val labelParts = (
            projectionLabels +
                if (isAxo) axoLabelsByKey[group.key].orEmpty() else emptyList()
            ).distinctBy { "${it.base}\u0000${it.superscript}" }
        val labelKey = labelParts.joinToString("|") { "${it.base}^${it.superscript}" }

        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s

        val metrics = remember(labelKey, fontPx) { measureRichLabelMetrics(labelParts, fontPx) }

        val pad = 4f * s
        val hitSize = Size(metrics.width + 2 * pad, (metrics.bottom - metrics.top) + 2 * pad)

        val baseLocal = group.logicalBase
        val logicalBase = Offset(baseLocal.x, -baseLocal.y)

        val userLogical = group.points
            .mapNotNull { state.labelOffsetsPointsBokorys[it.id] }
            .firstOrNull() ?: Offset.Zero

        val baselineAnchor = localToScreen(
                local = baseLocal + userLogical,
                scale = state.scale,
                offset = state.canvasOffset
            ) + baseScreenOffsetPx


        val textTopLeft = Offset(baselineAnchor.x, baselineAnchor.y + metrics.top)
        val hitTopLeft = textTopLeft - Offset(pad, pad)

        DraggableLabelHitbox(
            key = "bokorys-p-${group.key}",
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = Offset(pad, pad - metrics.top),
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = if (isAxo) baseLocal else logicalBase,
            getUserLogical = {
                group.points.mapNotNull { state.labelOffsetsPointsBokorys[it.id] }.firstOrNull() ?: Offset.Zero
            },
            setUserLogical = { value ->
                group.points.forEach { state.labelOffsetsPointsBokorys[it.id] = value }
            },
            state = state,
            show3DTag = group.points.any { it.parent != null || it.pendingParentLineId != null },
            labelScaleForUi = scale,
            hitboxSizePx = hitSize,
            onTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (state.isShiftPressed) state.selectedPointsBokorys.addAll(group.points)
                    else {
                        state.selectedPointsBokorys.clear()
                        state.selectedPointsBokorys.addAll(group.points)
                    }
                }
            },
            onDoubleTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (group.points.size == 1) startBokorysPointRename(state, firstPoint)
                    else pickerOpen = true
                }
            }
        )

        if (pickerOpen) {
            BokorysPointLabelPickerPopup(
                anchor = hitTopLeft + Offset(0f, hitSize.height + 4f),
                points = group.points,
                state = state,
                onDismiss = { pickerOpen = false }
            )
        }
    }
}
