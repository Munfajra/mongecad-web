package draw.mongescreen.labels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import serialization.SettingsManager
import serialization.SettingsManager.activeLabelSizePx
import state.MongeState

@Composable
fun rememberLabelScale(state: MongeState): Float {
    val scaleWithCanvas = SettingsManager.current.scaleLabelsWithCanvas

    val anchor = if (scaleWithCanvas) {
        state.labelScaleAnchorPudorys ?: state.scale.also { state.labelScaleAnchorPudorys = it }
    } else 1f

    return if (scaleWithCanvas) (state.scale / anchor) else 1f
}

fun screenToLogical(
    screen: Offset,
    state: MongeState
): Offset {
    val s = state.scale
    val off = state.canvasOffset
    val W = state.canvasWidth
    val H = state.canvasHeight

    val x = if (state.xAxisDirection == XAxisDirection.POSITIVE_LEFT) {
        (W - screen.x - off.x) / s
    } else {
        (screen.x - off.x) / s
    }

    val y = if (state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP) {
        (H - screen.y - off.y) / s
    } else {
        (screen.y - off.y) / s
    }

    return Offset(x, y)
}

private val defaultPointLabelOffsetPx = Offset(6f, -30f)

private fun pointLabelSizeFactor(): Float {
    val referenceSizePx =
        if (SettingsManager.current.scaleLabelsWithCanvas) 30f else 40f

    return (SettingsManager.current.activeLabelSizePx / referenceSizePx)
        .coerceIn(0.25f, 4f)
}

fun pointLabelBaseScreenOffsetPx(labelScale: Float): Offset {
    val canvasScale =
        if (SettingsManager.current.scaleLabelsWithCanvas) labelScale else 1f

    return defaultPointLabelOffsetPx * pointLabelSizeFactor() * canvasScale
}

fun pointLabelBaseScreenOffsetPx(
    labelScale: Float,
    pxFactor: Float
): Offset =
    scaledOffset(defaultPointLabelOffsetPx * pointLabelSizeFactor(), pxFactor) * labelScale

@Composable
fun DraggableLabelHitbox(
    key: Any,
    finalScreen: Offset,
    baseScreenOffsetPx: Offset,
    logicalBase: Offset,
    getUserLogical: () -> Offset,
    setUserLogical: (Offset) -> Unit,
    state: MongeState,
    hitboxSizePx: Size,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    debug: Boolean = false,
    textShiftFromHitboxPx: Offset = Offset.Zero,

    // ✅ nové
    show3DTag: Boolean = false,
    labelScaleForUi: Float = 1f,
) {
    var grabPx = Offset.Zero
    var grabDeltaLogical = Offset.Zero
    var totalDragPx = Offset.Zero
    var startFinalScreen = Offset.Zero

    val density = LocalDensity.current

    val labelsInteractive = (state.drawobjects == Mongeobjects.NONE)

    val dragModifier =
        if (labelsInteractive) {
            Modifier.pointerInput(key) {
                detectDragGestures(
                    onDragStart = { down ->
                        totalDragPx = Offset.Zero
                        grabPx = down
                        startFinalScreen = finalScreen

                        val startLogicalOffset = getUserLogical()

                        val pointerScreen0 =
                            (startFinalScreen + textShiftFromHitboxPx + grabPx) - baseScreenOffsetPx

                        val logical0 = screenToLogical(pointerScreen0, state)
                        grabDeltaLogical = startLogicalOffset - (logical0 - logicalBase)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragPx += dragAmount

                        val pointerScreen =
                            (startFinalScreen + textShiftFromHitboxPx + grabPx + totalDragPx) - baseScreenOffsetPx

                        val logicalNow = screenToLogical(pointerScreen, state)
                        setUserLogical((logicalNow - logicalBase) + grabDeltaLogical)
                    }
                )
            }
        } else Modifier

    val tapModifier =
        if (labelsInteractive) {
            Modifier.pointerInput(key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
        } else Modifier

    Box(
        modifier = Modifier
            .absoluteOffset { IntOffset(finalScreen.x.toInt(), finalScreen.y.toInt()) }
            .size(
                with(density) { hitboxSizePx.width.toDp() },
                with(density) { hitboxSizePx.height.toDp() }
            )
            .then(if (debug) Modifier.border(1.dp, Color.Magenta) else Modifier)
            .then(dragModifier)
            .then(tapModifier)
    ) {
        // ✅ "3D" tag jako Skia text (jen UI overlay, ne export)
        if (show3DTag) {
            Canvas(Modifier.matchParentSize()) {
                val s = if (SettingsManager.current.scaleLabelsWithCanvas) labelScaleForUi else 1f

                // velikost tagu v PX (navázaná na labelSize)
                val tagFontPx = SettingsManager.current.activeLabelSizePx * 0.32f * s

                // kotvení vůči BASE baseline: textShiftFromHitboxPx je baseline pos hlavního textu uvnitř hitboxu
                val dx = -0.95f * tagFontPx  // vlevo
                val dy =  0.85f * tagFontPx  // nahoru

                val tagBaseline = textShiftFromHitboxPx + Offset(dx, -dy)

                // tvoje drawSkiaText bere baseline-left anchor
                drawSkiaText(
                    text = "3D",
                    anchor = tagBaseline,
                    color = Color.Red,
                    fontPx = tagFontPx,
                    typefaceFamily = "italic" // nebo null, pokud chceš default
                )
            }
        }
    }
}



fun formatKota(z: Float): String {
    val rounded = kotlin.math.round(z * 10f) / 10f   // ⬅︎ 1 desetinné místo
    val i = rounded.toInt()
    return if (rounded == i.toFloat()) i.toString()
    else rounded.toString()
}
fun clearSelection (state: MongeState){
    state.selectedRuledSurfaceId = null
    state.selectedIntersectionGroupId = null
    state.selectedConicsAxo.clear()
    state.selectedLinesAxo.clear()
    state.selectedSegmentsAxo.clear()
    state.selectedPointsAxo.clear()
    state.selectedAOSegIds.clear()
    state.selectedAOLineIds.clear()
    state.selectedAOPointIds.clear()
    state.selectedCirclesBokorys.clear()
    state.selectedLinesBokorys.clear()
    state.selectedConicsBokorys.clear()
    state.selectedPointsBokorys.clear()
    state.selectedSegmentsBokorys.clear()
    state.selectedArcsNarys.clear()
    state.selectedPoints3D.clear()
    state.selectedLines3D.clear()
    state.selectedSegments3D.clear()
    state.selectedSegmentSolids3D.clear()
    state.selectedLinesPudorys.clear()
    state.selectedLinesNarys.clear()
    state.selectedAidPointIds.clear()
    state.selectedPointsPudorys.clear()
    state.selectedPointsNarys.clear()
    state.selectedTracesPudorys.clear()
    state.selectedTracesNarys.clear()
    state.selectedPlanes.clear()
    state.selectedConicsNarys.clear()
    state.selectedConicsPudorys.clear()
    state.selectedSegmentsNarys.clear()
    state.selectedSegmentsPudorys.clear()
    state.selectedCirclesNarys.clear()
    state.selectedCirclesPudorys.clear()
    state.selectedCone.clear()
    state.selectedConicalSurface = null
    state.selectedCylinder.clear()
    state.selectedCylindricalSurface = null
    state.selectedSpheres3D.clear()
    state.selectedPolygons.clear()
    state.selectedPolygon=null
    state.selectedPlanePolygons2D.clear()
    state.selectedArcsPudorys.clear()
    state.selectedArcsBokorys.clear()
    state.selectedArcsAxoOverlay.clear()
    state.selectedSolidOfRevolutionId=null
    state.selectedCurveNarysId=null
    state.selectedCurve3DId = null
    state.selectedCurvePudorysId = null
    state.selectedCurveAxoId = null
    state.selectedCurveBokorysId = null
}
fun isAxoAxisId(id: String?): Boolean {
    if (id == null) return false
    return id == "x_axis" ||
            id == "y_axis" ||
            id == "z_axis" ||
            id == "xp_ID" ||
            id == "xn_ID" ||
            id == "yp_ID" ||
            id == "yb_ID" ||
            id == "zn_ID" ||
            id == "zb_ID"||
            id == "axo_bokorys_ID"||
            id == "axo_narys_ID"||
            id == "axo_pudorys_ID"
}
