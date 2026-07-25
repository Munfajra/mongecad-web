package draw.mongescreen.labels

import utils.withoutProjectionSuffixes
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
import model.DrawingModeMonge
import model.LocalMongeColors
import model.Mongeobjects
import model.ProjectionMode
import model.classes.Point3DAxo
import model.classes.Point3DNarys
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import monge.input.axo.currentAxoLocal
import state.MongeState
import utils.toScreen
import kotlin.math.roundToInt

private data class NarysPointLabelGroup(
    val key: String,
    val projectorBase: Offset,
    val logicalBase: Offset,
    val points: List<Point3DNarys>
)

private fun narysPointGroupKey(offset: Offset): String =
    "${(offset.x * 100f).roundToInt()}:${(offset.y * 100f).roundToInt()}"

private fun Point3DNarys.labelBaseName(): String =
    parent?.name ?: name.orEmpty().withoutProjectionSuffixes()

private fun Point3DNarys.labelText(state: MongeState): String {
    val part = labelPart(state)
    return part.base + part.superscript
}

private fun Point3DNarys.labelPart(state: MongeState): RichLabelPart {
    val baseName = labelBaseName()
    val suffix = if (
        state.projectionMode == ProjectionMode.MONGE ||
        state.projectionMode == ProjectionMode.KOTO ||
        state.projectionMode == ProjectionMode.AXO
    ) "\u2082" else ""
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

private fun narysPointLabelGroups(
    state: MongeState,
    projector: (Point3DNarys) -> Offset
): List<NarysPointLabelGroup> =
    state.pointsNarys
        .filter { it.showInAxo && !it.isSegmentEndpoint && it.labelBaseName().isNotBlank() }
        .groupBy { narysPointGroupKey(projector(it)) }
        .map { (key, points) ->
            val sorted = points.sortedBy { it.effectiveCreationIndex }
            val projectorBase = projector(sorted.first())
            NarysPointLabelGroup(key, projectorBase, Offset(projectorBase.x, -projectorBase.y), sorted)
        }

private fun axoLabelsByNarysKey(state: MongeState): Map<String, List<RichLabelPart>> {
    if (state.projectionMode != ProjectionMode.AXO) return emptyMap()
    val basis = state.basis ?: return emptyMap()

    return state.pointsAxo
        .filter { it.showInAxo && it.axoLabelBaseName().isNotBlank() }
        .groupBy { narysPointGroupKey(basis.origin + it.currentAxoLocal(basis)) }
        .mapValues { (_, points) ->
            points.sortedBy { it.effectiveCreationIndex }.map { it.axoLabelPart(state) }
        }
}

private fun startNarysPointRename(state: MongeState, point: Point3DNarys) {
    if (state.drawobjects != Mongeobjects.NONE) return

    val p = state.sharedPoints3D.firstOrNull { it.id == point.parent?.id }
    if (p != null) {
        state.inputName = p.name
        state.pendingX = p.x
        state.pendingY = p.y
        state.isNameConfirmed = false
        state.rename.pointBeingRenamed = p
        state.projectionPhase = "narys_finalize"
        state.inputSuperscript = p.superscript.emptyIfNullText()

        if (state.projectionMode == ProjectionMode.KOTO) {
            state.isKotaConfirmed = false
            state.inputKota = formatKota(p.z / 10f)
        }
    } else {
        val live = state.pointsNarys.firstOrNull { it.id == point.id } ?: point
        state.inputName = live.name?.withoutProjectionSuffixes() ?: ""
        state.pendingX = live.x
        state.pendingZ = live.z
        state.isNameConfirmed = false
        state.rename.pointBeingRenamed = live
        state.inputSuperscript = live.localSuperscript.emptyIfNullText()
        state.projectionPhase = "rename_point_pudorys"
        if (state.projectionMode == ProjectionMode.KOTO) state.rename.pointNarysBeingRenamed = live
    }
}

@Composable
private fun NarysPointLabelPickerPopup(
    anchor: Offset,
    points: List<Point3DNarys>,
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
                            startNarysPointRename(state, point)
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun LabelsNarysPoints(state: MongeState,
                      projector: (Point3DNarys) -> Offset = { Offset(it.x, it.z) }) {
    val scale = rememberLabelScale(state)

    // drž stejné jako export/draw: (6, -30) + škálování podle settingu
    val baseScreenOffsetPx =
        pointLabelBaseScreenOffsetPx(scale)
    val axoLabelsByKey = axoLabelsByNarysKey(state)

    narysPointLabelGroups(state, projector).forEach { group ->
        val firstPoint = group.points.first()
        var pickerOpen by remember(group.key, group.points.size) { mutableStateOf(false) }
        val point = firstPoint
        if (!point.showInAxo) return@forEach
        val baseName = point.parent?.name ?: point.name.orEmpty()
        if (point.isSegmentEndpoint || baseName.isBlank()) return@forEach
        val isAxo = state.projectionMode == ProjectionMode.AXO
        val projectionLabels = group.points.map { it.labelPart(state) }
        val labelParts = (
            projectionLabels +
                if (state.projectionMode == ProjectionMode.AXO) axoLabelsByKey[group.key].orEmpty() else emptyList()
            ).distinctBy { "${it.base}\u0000${it.superscript}" }
        val labelKey = labelParts.joinToString("|") { "${it.base}^${it.superscript}" }

        // škálování fontu stejně jako v pudorysu
        val s = if (SettingsManager.current.scaleLabelsWithCanvas) scale else 1f
        val fontPx = SettingsManager.current.activeLabelSizePx * 0.7f * s


        val metrics = remember(labelKey, fontPx) { measureRichLabelMetrics(labelParts, fontPx) }

        val pad = 4f * s
        val hitSize = Size(metrics.width + 2 * pad, (metrics.bottom - metrics.top) + 2 * pad)

        // nárys: logical = (x, -z)
        val baseLocal = group.projectorBase
        val logicalBase = group.logicalBase

        val userLogical = group.points
            .mapNotNull { state.labelOffsetsPointsNarys[it.id] }
            .firstOrNull() ?: Offset.Zero



        // ✅ anchor = baseline-left, STEJNĚ jako v drawSkiaText
        val baselineAnchor = if (isAxo) {
            localToScreen(
                local = baseLocal + userLogical,
                scale = state.scale,
                offset = state.canvasOffset
            ) + baseScreenOffsetPx
        }
        else {
            (logicalBase + userLogical).toScreen(
                scale = state.scale,
                offset = state.canvasOffset,
                canvasHeight = state.canvasHeight,
                state = state,
                canvasWidth = state.canvasWidth
            ) + baseScreenOffsetPx
        }

        // ✅ STEJNÁ korekce jako u pudorysu: baseline -> top-left
        val textTopLeft = Offset(baselineAnchor.x, baselineAnchor.y + metrics.top)

        val hitTopLeft = textTopLeft - Offset(pad, pad)

        DraggableLabelHitbox(
            key = group.key,
            finalScreen = hitTopLeft,
            textShiftFromHitboxPx = Offset(pad, pad - metrics.top),
            baseScreenOffsetPx = baseScreenOffsetPx,
            logicalBase = if (isAxo) baseLocal else logicalBase,
            getUserLogical = {
                group.points.mapNotNull { state.labelOffsetsPointsNarys[it.id] }.firstOrNull() ?: Offset.Zero
            },
            setUserLogical = { value ->
                group.points.forEach { state.labelOffsetsPointsNarys[it.id] = value }
            },
            state = state,
            show3DTag = group.points.any { it.parent != null || it.pendingParentLineId != null },
            labelScaleForUi = scale,
            hitboxSizePx = hitSize,
            onTap = {
                if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.NONE) {
                    if (state.isShiftPressed) state.selectedPointsNarys.addAll(group.points)
                    else {
                        state.selectedPointsNarys.clear()
                        state.selectedPointsNarys.addAll(group.points)
                    }
                }
            },

            onDoubleTap = {
                if (state.drawobjects == Mongeobjects.NONE) {
                    if (group.points.size == 1) startNarysPointRename(state, firstPoint)
                    else pickerOpen = true
                }
            }
        )
        if (pickerOpen) {
            NarysPointLabelPickerPopup(
                anchor = hitTopLeft + Offset(0f, hitSize.height + 4f),
                points = group.points,
                state = state,
                onDismiss = { pickerOpen = false }
            )
        }
    }
}

fun DrawScope.drawPointNarysLabelsExport(
    state: MongeState,
    scale: Float,
    offset: Offset,
    fontPx: Float = 6f,
    pxFactor: Float = 1f,
    projector: (Point3DNarys) -> Offset = { Offset(it.x, it.z) }
) {
    val labelScale = exportLabelScale(state, scale, pxFactor)

    val font = fontPx * labelScale
    val baseScreenOffsetPx =
        pointLabelBaseScreenOffsetPx(labelScale, pxFactor)

    val isAxo = state.projectionMode == ProjectionMode.AXO
    val axoLabelsByKey = axoLabelsByNarysKey(state)

    for (group in narysPointLabelGroups(state, projector)) {
        val projectionLabels = group.points.map { it.labelPart(state) }
        val labelParts = (
            projectionLabels +
                if (isAxo) axoLabelsByKey[group.key].orEmpty() else emptyList()
            ).distinctBy { "${it.base}\u0000${it.superscript}" }

        val pos = if (isAxo) {
            val userLocal = group.points
                .mapNotNull { state.labelOffsetsPointsNarys[it.id] }
                .firstOrNull() ?: Offset.Zero

            localToScreen(
                local = group.projectorBase + userLocal,
                scale = scale,
                offset = offset
            ) + baseScreenOffsetPx
        } else {
            val userLogical = group.points
                .mapNotNull { state.labelOffsetsPointsNarys[it.id] }
                .firstOrNull() ?: Offset.Zero

            (group.logicalBase + userLogical).toScreen(
                scale = scale,
                offset = offset,
                canvasHeight = state.canvasHeight,
                state = state,
                canvasWidth = state.canvasWidth
            ) + baseScreenOffsetPx
        }

        drawRichLabel(
            parts = labelParts,
            anchor = pos,
            color = group.points.first().color,
            baseFontPx = font
        )
    }
}
