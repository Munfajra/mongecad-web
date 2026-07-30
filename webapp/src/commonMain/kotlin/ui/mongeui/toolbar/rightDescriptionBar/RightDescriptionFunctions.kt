package ui.mongeui.toolbar.rightDescriptionBar

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.LocalMongeColors
import model.Mongeobjects
import model.ProjectionMode
import serialization.SettingsManager
import state.MongeState
import ui.components.MongeInspectorPropertyRow
import ui.mongeui.toolbar.updateConstructionInfo
import ui.theme.LocalMongeDimens


@Composable
fun SelectionInfo(state: MongeState) {
    val listState = rememberLazyListState()
    val ui = SettingsManager.current.UIscale/75f
    Box(Modifier.fillMaxSize()) {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 12 * ui.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4 * ui.dp)
            ) {
                item {
                    if (state.projectionPhase in listOf("pudorys_start","narys_start","") && state.drawobjects == Mongeobjects.NONE) {
                        if (state.selectedIntersectionGroupId != null) {
                            intersectionGroupEdit(state)
                        } else if (!state.selectedCone.isNotEmpty()
                            && !state.selectedCylinder.isNotEmpty()
                            && !state.selectedPolygons.isNotEmpty()
                            && !state.selectedSegmentSolids3D.isNotEmpty()
                            && !state.selectedSpheres3D.isNotEmpty()
                            && state.selectedSolidOfRevolutionId == null

                        ) {
                            aidPointEdit(state)
                            pointEdit(state)
                            lineEdit(state)
                            segmentEdit(state)
                            conicEdit(state)
                            circleEdit(state)
                            planeEdit(state)
                            arcEdit(state)
                            curveEdit(state)
                            if (state.projectionMode == ProjectionMode.AXO) {



                            }
                        }

                        sorEditPud(state)
                        sorEdit(state)
                        ruledSurfaceEdit(state)
                        sphereEdit(state)
                        coneEdit(state)
                        cylinderEdit(state)
                        segmentSolidEdit(state)





                        polygonEdit(state)
                        val hasSelection = hasSelection(state)
                        if (!hasSelection) {
                            InspectorEmptyState(state)
                        }
                    } else {
                        InspectorEmptyState(state)}

                }
            }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(8.dp)
        )
    }
}

@Composable
fun InspectorEmptyState(state: MongeState? = null) {
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val shape = RoundedCornerShape(dimens.radiusMd)

    if (state != null) {
        // Drž nápovědu konstrukce čerstvou: kdykoli se změní fáze/nástroj/režim/projekce,
        // přepočítej consInfo, ať tu po dokončení/přepnutí nezůstane viset starý text.
        // (consInfo je jediné místo, kde se hláška zobrazuje, takže refresh stačí tady.)
        LaunchedEffect(
            state.projectionMode,
            state.projectionPhase,
            state.drawobjects,
            state.projekcnityp,
            state.mongeMode,
            state.constructionModifier
        ) {
            updateConstructionInfo(state)
        }
    }

    val consInfo = state?.consInfo?.value?.trim().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.sm)
            .clip(shape)
            .background(colors.base.copy(alpha = if (colors.isDark) 0.10f else 0.045f), shape)
            .border(dimens.borderThin, colors.base.copy(alpha = 0.22f), shape)
            .padding(horizontal = dimens.md, vertical = dimens.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.xs)
    ) {
        if (consInfo.isNotBlank()) {
            // Probíhá konstrukce – místo „nic není vybráno" zobraz nápovědu (dříve v liště nad canvasem).
            Text(
                text = consInfo,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "Není vybrán žádný objekt",
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Klikni na objekt ve výkresu nebo vyber nástroj vlevo.",
                color = colors.text.copy(alpha = 0.70f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun ProjectionVisibilityToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    ui: UiScale,
    modifier: Modifier = Modifier
) {
    MongeInspectorPropertyRow(
        label = label,
        modifier = modifier,
        contentAlign = Alignment.End
    ) {
        CompactProjectionToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            ui = ui
        )
    }
}

data class ProjectionVisibilityToggleItem(
    val label: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

@Composable
fun ProjectionVisibilityToggleStrip(
    ui: UiScale,
    vararg items: ProjectionVisibilityToggleItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ui.dp(7f), Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(ui.dp(5f)))
                    .clickable { item.onCheckedChange(!item.checked) }
                    .padding(horizontal = ui.dp(3f), vertical = ui.dp(2f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ui.dp(2f))
            ) {
                Text(
                    text = item.label,
                    fontSize = ui.sp(10f),
                    fontWeight = FontWeight.SemiBold,
                    color = LocalMongeColors.current.text.copy(alpha = 0.82f),
                    maxLines = 1
                )
                CompactProjectionToggle(
                    checked = item.checked,
                    ui = ui
                )
            }
        }
    }
}

@Composable
private fun CompactProjectionToggle(
    checked: Boolean,
    ui: UiScale,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val colors = LocalMongeColors.current
    val shape = RoundedCornerShape(ui.dp(9f))
    val track = if (checked) colors.selected.copy(alpha = 0.88f) else colors.base.copy(alpha = 0.55f)
    val border = if (checked) colors.selected else colors.text.copy(alpha = 0.22f)
    val thumb = if (checked) Color.White else colors.disabled

    Box(
        modifier = modifier
            .size(width = ui.dp(22f), height = ui.dp(12f))
            .clip(shape)
            .background(track, shape)
            .border(ui.dp(1f), border, shape)
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
            .padding(ui.dp(2f)),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(ui.dp(8f))
                .clip(RoundedCornerShape(ui.dp(4f)))
                .background(thumb)
        )
    }
}
fun hasSelection(state: MongeState): Boolean {
    if(
    state.selectedIntersectionGroupId != null ||
    state.selectedRuledSurfaceId != null ||
    state.selectedAOSegIds.isNotEmpty()||
    state.selectedArcsAxoOverlay.isNotEmpty()||
            state.selectedAOPointIds.isNotEmpty() ||
            state.selectedAidPointIds.isNotEmpty() ||
            state.selectedPointsPudorys.isNotEmpty() ||
            state.selectedPointsNarys.isNotEmpty()||
            state.selectedPointsBokorys.isNotEmpty() ||
            state.selectedPointsAxo.isNotEmpty() ||
            state.selectedLinesBokorys.isNotEmpty() ||
            state.selectedLinesAxo.isNotEmpty()||
            state.selectedLinesNarys.isNotEmpty() ||
            state.selectedLinesPudorys.isNotEmpty() ||
            state.selectedSegmentsPudorys.isNotEmpty() ||
            state.selectedSegmentsNarys.isNotEmpty() ||
            state.selectedSegmentsBokorys.isNotEmpty() ||
            state.selectedSegmentsAxo.isNotEmpty() ||
            state.selectedLines3D.isNotEmpty() ||
            state.selectedConicsNarys.isNotEmpty() ||
            state.selectedConicsPudorys.isNotEmpty() ||
            state.selectedCirclesPudorys.isNotEmpty() ||
            state.selectedCirclesNarys.isNotEmpty() ||
            state.selectedPlanes.isNotEmpty() ||
            state.selectedSegmentSolids3D.isNotEmpty() ||
            state.selectedCone.isNotEmpty() ||
            state.selectedCylinder.isNotEmpty() ||
            state.selectedPolygons.isNotEmpty() ||
            state.selectedPlanePolygons2D.isNotEmpty() ||
            state.selectedArcsPudorys.isNotEmpty() ||
            state.selectedArcsNarys.isNotEmpty() ||
            state.selectedArcsBokorys.isNotEmpty() ||
            (state.selectedCurve3DId != null) ||
            (state.selectedCurvePudorysId != null) ||
            (state.selectedCurveNarysId != null) ||
            (state.selectedCurveBokorysId != null) ||
            (state.selectedCurveAxoId != null) ||
            (state.selectedSolidOfRevolutionId != null)
    ) return true
    else return false
}
