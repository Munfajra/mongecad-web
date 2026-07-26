package ui.mongeui.toolbar.rightDescriptionBar

import model.SOR_BOKORYS_MERIDIAN_ID_PREFIX
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import model.DrawingModeMonge
import model.LocalMongeColors
import model.Mongeobjects
import model.ProjectionMode
import model.classes.*













import monge.input.combineprojections.*
import monge.input.planeobjects.conicsections.*
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

private data class ProjectionOverlayAction(
    val text: String,
    val widthUnits: Float,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProjectionCompletionOverlayButton(state: MongeState) {
    val colors = LocalMongeColors.current
    val ui = remember(SettingsManager.current.UIscale) {
        UiScale(SettingsManager.current.UIscale / 75f)
    }
    state.triggerRedraw
    val action = resolveProjectionOverlayAction(state) ?: return
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    var pointerPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(action.onClick)
    val shape = RoundedCornerShape(ui.dp(10f))
    val targetBackground = when {
        hovered && action.enabled -> colors.selected.copy(alpha = if (colors.isDark) 0.28f else 0.16f)
        action.enabled -> colors.background.copy(alpha = if (colors.isDark) 0.72f else 0.86f)
        else -> colors.background.copy(alpha = if (colors.isDark) 0.42f else 0.58f)
    }
    val targetBorder = when {
        hovered && action.enabled -> colors.selected.copy(alpha = 0.72f)
        action.enabled -> colors.base.copy(alpha = if (colors.isDark) 0.36f else 0.22f)
        else -> colors.base.copy(alpha = if (colors.isDark) 0.22f else 0.14f)
    }
    val targetIcon = when {
        !action.enabled -> colors.disabled.copy(alpha = 0.58f)
        hovered -> colors.selected
        else -> colors.text.copy(alpha = if (colors.isDark) 0.74f else 0.68f)
    }
    val background by animateColorAsState(targetBackground, label = "projection_completion_bg")
    val border by animateColorAsState(targetBorder, label = "projection_completion_border")
    val iconTint by animateColorAsState(targetIcon, label = "projection_completion_icon")
    val scale by animateFloatAsState(
        targetValue = if ((pressed || pointerPressed) && action.enabled) 0.96f else 1f,
        label = "projection_completion_press"
    )

    Box(
        modifier = Modifier
            .size(ui.dp(44f))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background, shape)
            .border(BorderStroke(1.dp, border), shape)
            .pointerHoverIcon(if (action.enabled) PointerIcon.Hand else PointerIcon.Default)
            .hoverable(interactionSource, enabled = action.enabled)
            // Nadřazený handler kreslicí plochy zpracovává primární stisk také.
            // Převzetí celého gesta v Initial pass zabrání tomu, aby drobný pohyb
            // myši mezi stiskem a uvolněním zrušil standardní clickable gesto.
            .pointerInput(action.enabled) {
                if (!action.enabled) return@pointerInput

                // Prst se mezi dotykem a zvednutím vždycky trochu posune a
                // dotyková obrazovka umí ve stejné události poslat i jiný
                // ukazatel. Sleduje se proto právě ten, kterým gesto začalo,
                // a kolem tlačítka je tolerance – jinak ťuknutí prstem
                // u kraje skončí „mimo“ a konstrukce se nedokončí.
                val releaseSlop = 12.dp.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    down.consume()
                    pointerPressed = true

                    var releasedInside = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val released = change.changedToUpIgnoreConsumed()
                            val position = change.position

                            change.consume()

                            if (released) {
                                releasedInside =
                                    position.x in -releaseSlop..(size.width + releaseSlop) &&
                                        position.y in -releaseSlop..(size.height + releaseSlop)
                                break
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        pointerPressed = false
                    }

                    if (releasedInside) currentOnClick()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = action.enabled,
                onClick = action.onClick
            )
            .alpha(if (action.enabled) 1f else 0.54f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AddCircle,
            contentDescription = action.text,
            tint = iconTint,
            modifier = Modifier.size(ui.dp(28f))
        )
    }
}

/**
 * Spustí akci doplnění průmětu (stejná jako klik na [ProjectionCompletionOverlayButton]),
 * pokud je pro aktuální výběr dostupná a povolená. Vrací true, pokud byla akce provedena.
 */
fun triggerProjectionCompletionIfAvailable(state: MongeState): Boolean {
    val action = resolveProjectionOverlayAction(state) ?: return false
    if (!action.enabled) return false
    action.onClick()
    return true
}

private fun resolveProjectionOverlayAction(state: MongeState): ProjectionOverlayAction? {
    if (hasSelectedSolidOfRevolutionPart(state)) return null
    if (hasSelectedPolygon(state)) return null
    if (hasSelectedCylindricalSurface(state)) return null
    selectedPointProjection(state)?.let { return pointCompletionAction(state, it) }
    selectedLineProjection(state)?.let { return lineCompletionAction(state, it) }
    selectedSegmentProjection(state)?.let { return segmentCompletionAction(state, it) }
    selectedTraceProjection(state)?.let { return traceCompletionAction(state, it) }
    selectedConicProjection(state)?.let { return conicCompletionAction(state, it) }
    return null
}

private fun selectedPointProjection(state: MongeState): Point2DProjection? =
    state.selectedPointsPudorys.firstOrNull()
        ?: state.selectedPointsNarys.firstOrNull()
        ?: state.selectedPointsBokorys.firstOrNull()
        ?: state.selectedPointsAxo.firstOrNull()

private fun selectedLineProjection(state: MongeState): Line2DProjection? =
    (state.selectedLinesPudorys.firstOrNull() as? Line2DProjection)
        ?: (state.selectedLinesNarys.firstOrNull() as? Line2DProjection)
        ?: (state.selectedLinesBokorys.firstOrNull() as? Line2DProjection)
        ?: state.selectedLinesAxo.firstOrNull()

private fun selectedSegmentProjection(state: MongeState): Segment2DProjection? =
    (state.selectedSegmentsPudorys.firstOrNull() as? Segment2DProjection)
        ?: (state.selectedSegmentsNarys.firstOrNull() as? Segment2DProjection)
        ?: (state.selectedSegmentsBokorys.firstOrNull() as? Segment2DProjection)
        ?: state.selectedSegmentsAxo.firstOrNull()

private fun selectedTraceProjection(state: MongeState): Trace2DProjection? =
    (state.selectedLinesPudorys.firstOrNull() as? Trace2DProjection)
        ?: (state.selectedLinesNarys.firstOrNull() as? Trace2DProjection)
        ?: (state.selectedLinesBokorys.firstOrNull() as? Trace2DProjection)
        ?: state.selectedTracesPudorys.firstOrNull()
        ?: state.selectedTracesNarys.firstOrNull()
        ?: state.selectedTracesBokorys.firstOrNull()

private fun selectedConicProjection(state: MongeState): ConicSection2D? =
    state.selectedConicsPudorys.firstOrNull()
        ?: state.selectedConicsNarys.firstOrNull()
        ?: state.selectedConicsBokorys.firstOrNull()
        ?: state.selectedConicsAxo.firstOrNull()
        ?: state.selectedCirclesPudorys.firstOrNull()
        ?: state.selectedCirclesNarys.firstOrNull()
        ?: state.selectedCirclesBokorys.firstOrNull()

private fun pointCompletionAction(state: MongeState, point: Point2DProjection): ProjectionOverlayAction? {
    if (point.parent != null) return null
    if (isProjectedLinePoint(point)) return null
    return when (state.projectionMode) {
        ProjectionMode.MONGE -> ProjectionOverlayAction("Dokončit", 100f, true) {
            state.drawobjects = Mongeobjects.NONE
            state.mongeMode = if (point is Point3DPudorys) DrawingModeMonge.NARYS else DrawingModeMonge.PUDORYS
            CompletePointAdd(state)
        }
        ProjectionMode.AXO -> ProjectionOverlayAction("Dokončit", 100f, true) {
            state.drawobjects = Mongeobjects.NONE
            when (point) {
                is Point3DPudorys -> Unit
                is Point3DBokorys -> Unit
                is Point3DNarys -> Unit
                is Point3DAxo -> Unit
            }
        }
        else -> null
    }
}

private fun lineCompletionAction(state: MongeState, line: Line2DProjection): ProjectionOverlayAction? {
    if (line.parent != null || state.projectionMode == ProjectionMode.PLANE) return null
    if (state.projectionMode == ProjectionMode.KOTO) {
        return ProjectionOverlayAction("Určit 3D pomocí 2 bodů", 320f, line is Line3DProjectionPudorys) {
            if (line is Line3DProjectionPudorys) {
                state.linefrom2points = line
                setProjectionPhase("picking_line_points", state)
            }
        }
    }
    return when (state.projectionMode) {
        ProjectionMode.MONGE -> ProjectionOverlayAction("Dokončit", 100f, true) {
            CompleteLineAdd(state, line)
        }
        ProjectionMode.AXO -> ProjectionOverlayAction("Dokončit", 100f, true) {
            state.drawobjects = Mongeobjects.NONE
            state.reusingExistingProjection = false
            when (line) {
                is Line3DProjectionPudorys -> Unit
                is Line3DProjectionNarys -> Unit
                is Line3DProjectionBokorys -> Unit
                is Line3DProjectionAxo -> Unit
            }
        }
        else -> null
    }
}

private fun segmentCompletionAction(state: MongeState, segment: Segment2DProjection): ProjectionOverlayAction? {
    if (isExcludedSegmentForProjectionCompletion(state, segment)) return null
    if (segment.parent != null || state.projectionMode == ProjectionMode.PLANE) return null
    if (state.projectionMode == ProjectionMode.KOTO) return null

    return when (state.projectionMode) {
        ProjectionMode.MONGE -> ProjectionOverlayAction("Doplnit průmět", 140f, true) {
            CompleteSegmentAdd(state, segment)
        }
        ProjectionMode.AXO -> ProjectionOverlayAction("Doplnit průmět", 140f, true) {
            when (segment) {
                is Segment2DPudorys -> Unit
                is Segment2DNarys -> Unit
                is Segment2DBokorys -> Unit
                is Segment2DAxo -> Unit
                else -> {}
            }
        }
        else -> null
    }
}

private fun traceCompletionAction(state: MongeState, trace: Trace2DProjection): ProjectionOverlayAction? {
    if (trace.parent != null || state.projectionMode == ProjectionMode.PLANE) return null
    val isKoto = state.projectionMode == ProjectionMode.KOTO
    val text = if (isKoto) "Dokončit rovinu výběrem bodu" else "Dokončit"
    val enabled = !isKoto || trace is PlaneTracePudorys

    return ProjectionOverlayAction(text, if (isKoto) 320f else 100f, enabled) {
        when (state.projectionMode) {
            ProjectionMode.KOTO -> {
                if (trace is PlaneTracePudorys) {
                    beginPlaneFromTracePickPoint(trace, state, planeName = trace.localName ?: "", superscript = trace.superscript)
                }
            }
            ProjectionMode.AXO -> Unit
            else -> CompletePlaneAdd(state, trace)
        }
    }
}

private fun conicCompletionAction(state: MongeState, conic: ConicSection2D): ProjectionOverlayAction? {
    if (isSphereConicProjection(state, conic)) return null
    if (conic.parent != null || state.projectionMode == ProjectionMode.PLANE) return null
    if (state.selectedCirclesPudorys.any { it.id == conic.id } || state.selectedCirclesNarys.any { it.id == conic.id }) {
        return ProjectionOverlayAction("Dokončit", 100f, conic is ConicSectionPudorys) {
            if (conic is ConicSectionPudorys) startLiftCircleToPlaneFromPudorys(state)
        }
    }

    val conicType = getConicType(
        conic,
        state.conicInputPointsPudorys,
        state.conicInputPointsNarys,
        state.conicInputPointsBokorys,
        state.hyperbolaInputsPudorys,
        state.hyperbolaInputsNarys,
        state.hyperbolaInputsBokorys
    )
    val enabled = conic is ConicSectionPudorys || conic is ConicSectionNarys || conic is ConicSectionBokorys
    return ProjectionOverlayAction("Dokončit", 100f, enabled) {
        when (conic) {
            is ConicSectionPudorys -> when (conicType) {
                "Elipsa" -> startLiftConicToPlaneFromPudorys(state)
                "Hyperbola" -> startLiftHyperbolaToPlaneFromPudorys(state)
                "Parabola" -> startLiftParabolaToPlaneFromPudorys(state)
            }
            is ConicSectionNarys -> when (conicType) {
                "Elipsa" -> startLiftConicToPlaneFromNarys(state)
                "Hyperbola" -> startLiftHyperbolaToPlaneFromNarys(state)
                "Parabola" -> startLiftParabolaToPlaneFromNarys(state)
            }
            is ConicSectionBokorys -> when (conicType) {
                "Elipsa" -> startLiftConicToPlaneFromBokorys(state)
                "Hyperbola" -> startLiftHyperbolaToPlaneFromBokorys(state)
                "Parabola" -> startLiftParabolaToPlaneFromBokorys(state)
            }
            is ConicSectionAxo -> Unit
        }
    }
}

private fun isExcludedSegmentForProjectionCompletion(
    state: MongeState,
    segment: Segment2DProjection
): Boolean {
    if (segment is HelpSegmentPudorys || segment is HelpSegmentNarys) return true

    val surfaceId = when (segment) {
        is Segment2DPudorys -> segment.conicalSurfaceId
        is Segment2DNarys -> segment.conicalSurfaceId
        is Segment2DBokorys -> segment.conicalSurfaceId
        is Segment2DAxo -> segment.conicalSurfaceId
        else -> null
    }

    return state.conicalSurfaces.any { surface ->
        surfaceId == surface.id || segment.id in surface.edgeSegIdsPudorys2D ||
                segment.id in surface.edgeSegIdsNarys2D ||
                segment.id in surface.edgeSegIdsBokorys2D ||
                segment.id in surface.edgeSegIdsAxo2D
    } || state.cylindricalSurfaces.any { surface ->
        surfaceId == surface.id || segment.id in surface.edgeSegIdsPudorys2D ||
                segment.id in surface.edgeSegIdsNarys2D ||
                segment.id in surface.edgeSegIdsBokorys2D ||
                segment.id in surface.edgeSegIdsAxo2D
    }
}

private fun isSphereConicProjection(state: MongeState, conic: ConicSection2D): Boolean {
    val parentId = conic.parentId ?: return false
    return state.spheres3D.any { it.id == parentId }
}

private fun hasSelectedPolygon(state: MongeState): Boolean =
    state.selectedPolygon != null || state.selectedPolygons.isNotEmpty()

private fun hasSelectedCylindricalSurface(state: MongeState): Boolean =
    state.selectedCylindricalSurface != null || state.selectedCylinder.isNotEmpty()

private fun hasSelectedSolidOfRevolutionPart(state: MongeState): Boolean {
    if (state.selectedSolidOfRevolutionId != null) return true

    val solidIds = (state.solidsOfRevolutionNarys.map { it.id } +
            state.solidsOfRevolutionPudorys.map { it.id }).toSet()
    if (solidIds.isEmpty()) return false

    val meridianNarysIds = state.solidsOfRevolutionNarys
        .flatMap { it.meridianIdsNarys + it.mirroredMeridianIdsNarys }
        .toSet()
    val meridianPudorysIds = state.solidsOfRevolutionPudorys
        .flatMap { it.meridianIdsPudorys + it.mirroredMeridianIdsPudorys }
        .toSet()
    val parallelPudorysIds = (state.solidsOfRevolutionNarys.flatMap { it.circleIdsPudorys } +
            state.solidsOfRevolutionPudorys.flatMap { it.circleIdsPudorys }).toSet()
    val parallelNarysIds = (state.solidsOfRevolutionNarys.flatMap { it.circleIdsNarys } +
            state.solidsOfRevolutionPudorys.flatMap { it.circleIdsNarys }).toSet()

    fun isGeneratedSoRPart(id: String, parentId: String? = null): Boolean {
        if (parentId != null && parentId in solidIds) return true
        return (id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) ||
                id.startsWith("sorAxoContour")) &&
                solidIds.any { solidId -> id.contains(solidId) }
    }

    fun segmentParentId(segment: Any): String? = when (segment) {
        is Segment2DPudorys -> segment.parent?.id ?: segment.parentId
        is Segment2DNarys -> segment.parent?.id ?: segment.parentId
        is Segment2DBokorys -> segment.parent?.id ?: segment.parentId
        is Segment2DAxo -> segment.parent?.id ?: segment.parentId
        is HelpSegmentPudorys -> segment.parent?.id
        is HelpSegmentNarys -> segment.parent?.id
        else -> null
    }

    if (state.selectedMeridianNarysIds.any { it in meridianNarysIds }) return true
    if (state.selectedMeridianPudorysIds.any { it in meridianPudorysIds }) return true

    if (state.selectedSegmentsNarys.any { it.id in meridianNarysIds || isGeneratedSoRPart(it.id, segmentParentId(it)) }) return true
    if (state.selectedSegmentsPudorys.any { it.id in meridianPudorysIds || isGeneratedSoRPart(it.id, segmentParentId(it)) }) return true
    if (state.selectedSegmentsBokorys.any { isGeneratedSoRPart(it.id, segmentParentId(it)) }) return true
    if (state.selectedSegmentsAxo.any { isGeneratedSoRPart(it.id, segmentParentId(it)) }) return true

    if (state.selectedPointsNarys.any { it.parentSegment?.id in meridianNarysIds }) return true
    if (state.selectedPointsPudorys.any { it.parentSegment?.id in meridianPudorysIds }) return true
    if (state.selectedPointsBokorys.any { isGeneratedSoRPart(it.parentSegment?.id.orEmpty(), it.parentSegment?.parentId) }) return true
    if (state.selectedPointsAxo.any { isGeneratedSoRPart(it.parentSegment?.id.orEmpty(), it.parentSegment?.parentId) }) return true

    if (state.selectedArcsNarys.any { it.id in meridianNarysIds }) return true
    if (state.selectedArcsPudorys.any { it.id in meridianPudorysIds }) return true
    if (state.selectedArcsBokorys.any { isGeneratedSoRPart(it.id) }) return true
    if (state.selectedArcsAxoOverlay.any { isGeneratedSoRPart(it.id) }) return true

    if (state.selectedConicsPudorys.any { it.id in parallelPudorysIds || it.parentId in solidIds }) return true
    if (state.selectedConicsNarys.any { it.id in parallelNarysIds || it.parentId in solidIds }) return true
    if (state.selectedConicsBokorys.any { it.parentId in solidIds }) return true
    if (state.selectedConicsAxo.any { it.parentId in solidIds }) return true
    if (state.selectedCirclesPudorys.any { it.id in parallelPudorysIds || it.parentId in solidIds }) return true
    if (state.selectedCirclesNarys.any { it.id in parallelNarysIds || it.parentId in solidIds }) return true
    if (state.selectedCirclesBokorys.any { it.parentId in solidIds }) return true

    val selectedCurveIds = setOfNotNull(
        state.selectedCurvePudorysId,
        state.selectedCurveNarysId,
        state.selectedCurveBokorysId,
        state.selectedCurveAxoId
    )
    if (selectedCurveIds.any { id -> isGeneratedSoRPart(id) || id in meridianNarysIds || id in meridianPudorysIds }) {
        return true
    }
    if (state.selectedCurve3DId in solidIds) return true

    return false
}
