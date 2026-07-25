package dialogs.batchinput

import ui.components.MiniInputField
import model.classes.dummyBokorys
import model.classes.dummyNarys
import model.classes.dummyPudorys
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import monge.input.axo.projectLine3DToAxoLocal
import monge.input.axo.projectPoint3DToAxo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import serialization.commitSnapshot
import model.LocalMongeColors
import model.Point3D
import model.ProjectionMode
import model.classes.*
import model.darker
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.ConicMenuItem
import ui.mongeui.toolbar.SkikoButton
import utils.allocIndex
import utils.update2DSnapshots
import utils.UUID
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BatchPointDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm3D: (List<Point3D>) -> Unit = {},
    onConfirmPudorys: (List<Point3DPudorys>) -> Unit = {},
    onConfirmNarys: (List<Point3DNarys>) -> Unit = {},
    state: MongeState
) {
    if (!showDialog) return

    val colors = LocalMongeColors.current
    val ui = SettingsManager.current.UIscale/75f
    val scope = rememberCoroutineScope()
    val isMonge = state.projectionMode == ProjectionMode.MONGE || state.projectionMode == ProjectionMode.KOTO||state.projectionMode == ProjectionMode.AXO

    data class PointFields(
        val name: MutableState<String> = mutableStateOf(""),
        val x: MutableState<String> = mutableStateOf(""),
        val y: MutableState<String> = mutableStateOf(""),
        val z: MutableState<String> = mutableStateOf(""),
        val nameFocus: FocusRequester = FocusRequester(),
        val xFocus: FocusRequester = FocusRequester(),
        val yFocus: FocusRequester = FocusRequester(),
        val zFocus: FocusRequester = FocusRequester()
    )

    val points = remember { mutableStateListOf(PointFields()) }

    LaunchedEffect(showDialog) {
        if (showDialog) {
            withFrameNanos { }
            points.firstOrNull()?.nameFocus?.requestFocus()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // Popup má vlastní focus window; po jeho zániku vrať klávesy canvasu/rootu.
            runCatching { state.focusRequester.requestFocus() }
        }
    }

    fun addRowAndFocus() {
        scope.launch {
            val new = PointFields()
            points.add(new)
            delay(40.milliseconds)
            new.nameFocus.requestFocus()
        }
    }

    fun confirmAndMaybeClose(close: Boolean = true) {
        val pts3D = mutableListOf<Point3D>()
        val ptsP  = mutableListOf<Point3DPudorys>()
        val ptsN  = mutableListOf<Point3DNarys>()

        points.forEach { field ->
            val name = field.name.value.trim()
            val x = field.x.value.toFloatOrNull()
            val y = field.y.value.toFloatOrNull()
            val z = field.z.value.toFloatOrNull()

            if (name.isBlank() || x == null) return@forEach

            if (isMonge) {
                when {
                    y != null && z != null -> pts3D += Point3D(x, y, z, name, creationIndex = allocIndex(state))
                    y != null && z == null -> ptsP  += Point3DPudorys(
                        x = x,
                        y = y,
                        name = name,
                        creationIndex = allocIndex(state)
                    )
                    y == null && z != null -> ptsN  += Point3DNarys(
                        x = x,
                        z = z,
                        name = name,
                        creationIndex = allocIndex(state)
                    )
                    else -> Unit
                }
            } else {
                // mimo MONGE: bereme jen X+Y jako půdorys
                if (y != null) ptsP += Point3DPudorys(x = x, y = y, name = name, creationIndex = allocIndex(state))
            }
        }

        if (pts3D.isNotEmpty()) onConfirm3D(pts3D)
        if (ptsP.isNotEmpty())  onConfirmPudorys(ptsP)
        if (ptsN.isNotEmpty())  onConfirmNarys(ptsN)

        if (close) onDismiss()
    }

    fun rowIsValidToSubmit(field: PointFields): Boolean {
        val hasName = field.name.value.isNotBlank()
        val xOk = field.x.value.toFloatOrNull() != null
        val yOk = field.y.value.toFloatOrNull() != null
        val zOk = field.z.value.toFloatOrNull() != null

        if (!hasName || !xOk) return false
        return if (isMonge) {
            (yOk && zOk) || (yOk) || (zOk)
        } else {
            yOk
        }
    }

    fun Modifier.onEnterSubmit(field: PointFields): Modifier = onPreviewKeyEvent { e ->
        if ((e.key == Key.Enter || e.key == Key.NumPadEnter) && e.type == KeyEventType.KeyDown) {
            // chování jako u tebe: potvrdit a zavřít jen když je řádek validní
            if (rowIsValidToSubmit(field)) {
                confirmAndMaybeClose(close = true)
            }
            true
        } else false
    }

    fun Modifier.tabNavigation(current: FocusRequester, focusList: List<FocusRequester>): Modifier =
        onPreviewKeyEvent { e ->
            if (e.key == Key.Tab && e.type == KeyEventType.KeyDown) {
                val i = focusList.indexOf(current)
                if (i in 0 until focusList.lastIndex) {
                    focusList[i + 1].requestFocus()
                } else {
                    addRowAndFocus()
                }
                true
            } else false
        }

    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (windowSize.width - popupContentSize.width) / 2
                val y = (windowSize.height - popupContentSize.height) / 2
                return IntOffset(x, y)
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .padding(28*ui.dp)
                .background(colors.background.copy(alpha = 0.94f).darker(0.9f), RoundedCornerShape(10*ui.dp))
                .border(1.dp, colors.base.copy(alpha = 0.65f), RoundedCornerShape(10*ui.dp))
                .width(if (isMonge) 380*ui.dp else 330*ui.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16*ui.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12*ui.dp)
            ) {
                val fieldWidth = 56*ui.dp
                val fieldHeight = 46*ui.dp
                val fieldSpacing = 6*ui.dp
                val actionWidth = 42*ui.dp

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = "Zadat souřadnice bodů" ,
                        fontSize = 18*ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                }

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(fieldSpacing)) {
                        Spacer(Modifier.width(actionWidth))
                        Text("Název", color = colors.text.copy(alpha = 0.7f), modifier = Modifier.width(56*ui.dp))
                        Text("X",     color = colors.text.copy(alpha = 0.7f), modifier = Modifier.width(fieldWidth))
                        Text("Y",     color = colors.text.copy(alpha = 0.7f), modifier = Modifier.width(fieldWidth))
                        if (isMonge) Text("Z", color = colors.text.copy(alpha = 0.7f), modifier = Modifier.width(fieldWidth))
                        Spacer(Modifier.width(actionWidth))
                    }
                }

                points.forEachIndexed { index, field ->
                    val focusList = if (isMonge)
                        listOf(field.nameFocus, field.xFocus, field.yFocus, field.zFocus)
                    else
                        listOf(field.nameFocus, field.xFocus, field.yFocus)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(fieldSpacing),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.onEnterSubmit(field)
                        ) {
                            Spacer(Modifier.width(actionWidth))
                        MiniInputField(
                            value = field.name.value,
                            ui=ui,
                            onValueChange = { field.name.value = it },
                            placeholder = "A",
                            numericOnly = false,
                            modifier = Modifier
                                .focusRequester(field.nameFocus)
                                .tabNavigation(field.nameFocus, focusList),
                            width = fieldWidth,
                            height = fieldHeight
                        )

                        MiniInputField(
                            ui=ui,
                            value = field.x.value,
                            onValueChange = { field.x.value = it.filterValidFloatInput() },
                            placeholder = "X",
                            modifier = Modifier
                                .focusRequester(field.xFocus)
                                .tabNavigation(field.xFocus, focusList),
                            width = fieldWidth,
                            height = fieldHeight
                        )

                        MiniInputField(
                            ui=ui,
                            value = field.y.value,
                            onValueChange = { field.y.value = it.filterValidFloatInput() },
                            placeholder = "Y",
                            modifier = Modifier
                                .focusRequester(field.yFocus)
                                .tabNavigation(field.yFocus, focusList),
                            width = fieldWidth,
                            height = fieldHeight
                        )

                        if (isMonge) {
                            MiniInputField(
                                ui=ui,
                                value = field.z.value,
                                onValueChange = { field.z.value = it.filterValidFloatInput() },
                                placeholder = "Z",
                                modifier = Modifier
                                    .focusRequester(field.zFocus)
                                    .tabNavigation(field.zFocus, focusList),
                                width = fieldWidth,
                                height = fieldHeight
                            )
                        }
                            val showTrash = points.size > 1
                            Box(modifier = Modifier.width(actionWidth), contentAlignment = Alignment.Center) {
                                if (showTrash) {
                                    SkikoButton(
                                        onClick = { points.removeAt(index) },
                                        width = actionWidth
                                    ) {

                                    }
                                }
                            }
                    }
                        }
                }

                // Add row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SkikoButton(onClick = { addRowAndFocus() }, width = 170*ui.dp) {
                        Icon(
                            painter = painterResource("icons/circle-plus.svg"),
                            contentDescription = "Přidat řádek",
                            modifier = Modifier.size(20*ui.dp)
                        )
                        Text("Přidat řádek", modifier = Modifier.padding(start = 8*ui.dp))
                    }
                }

                // Bottom actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SkikoButton(onClick = onDismiss) { Text("Zrušit") }
                    Spacer(Modifier.width(8*ui.dp))
                    SkikoButton(onClick = { confirmAndMaybeClose(close = true) }) {
                        Icon(
                            painter = painterResource("icons/check.svg"),
                            contentDescription = "Vložit",
                            modifier = Modifier.size(22*ui.dp)
                        )
                        Text("Vložit", modifier = Modifier.padding(start = 8*ui.dp))
                    }
                }
            }
        }
    }
}


private fun String.filterValidFloatInput(): String {
    return replace(',', '.').takeIf {
        it.isEmpty() || it.matches(Regex("""-?\d*\.?\d*"""))
    } ?: ""
}


@Composable
fun CoordinateInputDropdown(
    onBodClick: () -> Unit,
    onPrimkaClick: () -> Unit,
    onRovinaClick: () -> Unit,
    state: MongeState,
    buttonsize: Dp
) {
    var expanded by remember { mutableStateOf(false) }
    val ui = SettingsManager.current.UIscale/75f
    val dropdownButtonSize = Modifier
        .width(buttonsize*1.25f)
        .height(buttonsize)

    Box(modifier = dropdownButtonSize) {
        SkikoButton(
            onClick = { expanded = true },
            isSelected = expanded,
            modifier = Modifier.fillMaxSize(),
            enabled = state.projectionPhase == "pudorys_start" || state.projectionPhase == "narys_start"
        ) {
            Icon(
                painter = painterResource("icons/souradnicovezadani.png"),
                contentDescription = "Souřadnicové zadání",
                modifier = Modifier.size(160*ui.dp)
            )
        }

        if (expanded) {
            RichDropdownPopup(
                ui = ui,
                onDismiss = { expanded = false }
            ) {
                ConicMenuItem(
                    title = "Bod",
                    subtitle = "Zadat souřadnice bodu pro jeden či oba průměty",
                    painter = "icons/point.svg",
                    onClick = {
                        expanded = false
                        onBodClick()
                    },
                    isDark = LocalMongeColors.current.isDark,
                    tint = true,
                    ui=ui
                )
                RichDropdownDivider()

                ConicMenuItem(
                    title = "Přímka",
                    subtitle = "Definovat prostorovou přímku parametricky",
                    painter = "icons/primka.svg",
                    onClick = {
                        expanded = false
                        onPrimkaClick()
                    },
                    isDark = LocalMongeColors.current.isDark,
                    tint = true,
                    ui=ui
                )
                RichDropdownDivider()

                ConicMenuItem(
                    title = "Rovina",
                    subtitle = "Zadat obecnou rovnici roviny Ax+By+Cz+D=0",
                    painter = "icons/rovina.svg",
                    onClick = {
                        expanded = false
                        onRovinaClick()
                    },
                    isDark = LocalMongeColors.current.isDark,
                    tint = true,
                    ui=ui
                )
            }
        }
    }
}
@Composable
fun CoordinateInputDropdownPlane(
    onBodClick: () -> Unit,
    onPrimkaClick: () -> Unit,
    state: MongeState,
    buttonsize: Dp
) {
    var expanded by remember { mutableStateOf(false) }
    val ui = SettingsManager.current.UIscale/75f

    val dropdownButtonSize = Modifier
        .width(buttonsize*1.25f)
        .height(buttonsize)

    Box(modifier = dropdownButtonSize) {
        SkikoButton(
            onClick = { expanded = true },
            isSelected = expanded,
            modifier = Modifier.fillMaxSize(),
            enabled = state.projectionPhase == "pudorys_start"
        ) {
            Icon(
                painter = painterResource("icons/souradnicovezadani.png"),
                contentDescription = "Souřadnicové zadání",
                modifier = Modifier.size(buttonsize*2f)
            )
        }

        if (expanded) {
            RichDropdownPopup(
                ui = ui,
                onDismiss = { expanded = false }
            ) {
                ConicMenuItem(
                    title = "Bod",
                    subtitle = "Zadat souřadnice bodu v rovině",
                    painter= "icons/point.svg",
                    onClick = {
                        expanded = false
                        onBodClick()
                    },
                    isDark = LocalMongeColors.current.isDark,
                    tint = true,
                    ui = ui
                )
                RichDropdownDivider()

                ConicMenuItem(
                    title = "Přímka",
                    subtitle = "Definovat rovinnou přímku parametricky",
                    painter = "icons/primka.svg",
                    onClick = {
                        expanded = false
                        onPrimkaClick()
                    },
                    isDark = LocalMongeColors.current.isDark,
                    tint = true,
                    ui=ui
                )
            }
        }
    }
}

/* ====== Rozšířený Popup s kartami ====== */

@Composable
private fun RichDropdownPopup(
    ui: Float,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMongeColors.current
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, (105f * ui).toInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        // „Karta“ kontejner se stínem a scrollováním
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .width(360f*ui.dp)                       // širší než běžné menu
                .shadow(12.dp, RoundedCornerShape(6.dp))
                .background(colors.background, RoundedCornerShape(6.dp))
                .border(1.dp, colors.base.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .padding(8f*ui.dp)
                .heightIn(max = 420f*ui.dp)             // když je obsah delší, zapne se scroll
                .verticalScroll(scroll)
        ) {
            content()
        }
    }
}

/* ====== Jedna „velká“ položka v menu ====== */

@Composable
private fun RichDropdownDivider() {
    val colors = LocalMongeColors.current
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.base.copy(alpha = 0.15f), RoundedCornerShape(1.dp))
    )
    Spacer(modifier = Modifier.height(8.dp))
}


@Composable
fun BatchInputLauncherToolbar(
    showDialog: MutableState<Boolean>,
    showParamDialog: MutableState<Boolean>,
    showPlaneDialog: MutableState<Boolean>,
    state: MongeState,
    buttonsize: Dp
) {
    CoordinateInputDropdown(
        onBodClick = { showDialog.value = true },
        onPrimkaClick = { showParamDialog.value = true },
        onRovinaClick = { showPlaneDialog.value = true },
        state = state,
        buttonsize = buttonsize
    )
}
@Composable
fun BatchInputLauncherToolbarPlane(
    showDialog: MutableState<Boolean>,
    showParamDialog: MutableState<Boolean>,
    state: MongeState,
    buttonsize: Dp
) {
    CoordinateInputDropdownPlane(
        onBodClick = { showDialog.value = true },
        onPrimkaClick = { showParamDialog.value = true },
        state = state,
        buttonsize = buttonsize

    )
}
@Composable
fun BatchInputLauncherDialogs(
    state: MongeState,
    showDialog: MutableState<Boolean>,
    showParamDialog: MutableState<Boolean>,
    showPlaneDialog: MutableState<Boolean>
) {
    if (showDialog.value) {
        BatchPointDialog(
            showDialog = true,
            onDismiss = { showDialog.value = false },

            // ✅ 3D body (x,y,z vyplněné)
            onConfirm3D = { newPoints3D ->

                for (p in newPoints3D) {
                    val sx = p.x * 10f
                    val sy = p.y * 10f
                    val sz = p.z * 10f

                    val point3D = Point3D(sx, sy, sz, p.name, creationIndex = allocIndex(state))
                    run { state.sharedPoints3D.add(point3D) }

                    state.pointsPudorys.add(
                        Point3DPudorys(
                            sx,
                            sy,
                            p.name,
                            parent = point3D,
                            creationIndex = allocIndex(state)
                        )
                    )
                    state.pointsNarys.add(
                        Point3DNarys(
                            sx,
                            sz,
                            p.name,
                            parent = point3D,
                            creationIndex = allocIndex(state),
                            showInAxoInitial = state.projectionMode != ProjectionMode.AXO
                        )
                    )
                    if (state.projectionMode == ProjectionMode.AXO)
                    {
                        val basis = state.basis ?: continue
                        val axo = projectPoint3DToAxo(point3D, basis)
                        val localRelativeToOrigin = axo - basis.origin
                        state.pointsBokorys.add(
                            Point3DBokorys(
                                sy,
                                sz,
                                p.name,
                                parent = point3D,
                                creationIndex = allocIndex(state),
                                showInAxoInitial = false
                            )
                        )
                        state.pointsAxo.add(
                            Point3DAxo(
                                x = localRelativeToOrigin.x,
                                y = localRelativeToOrigin.y,
                                name = p.name,
                                parent = point3D,
                                isSegmentEndpoint = false,
                                localColor = point3D.color,
                                localWidth = point3D.width,
                                id = UUID.randomUUID().toString(),
                                localSuperscript = point3D.superscript,
                                parentLine = null,
                                pendingParentLineId = null,
                                creationIndex = point3D.creationIndex
                            )
                        )
                    }
                }
                update2DSnapshots(state)
                commitSnapshot(state)
                showDialog.value = false
            },

            // ✅ jen půdorys (x,y; z prázdné)
            onConfirmPudorys = { ptsP ->

                for (p in ptsP) {
                    val sx = p.x * 10f
                    val sy = p.y * 10f
                    // parent = null → samostatná projekce
                    state.pointsPudorys.add(Point3DPudorys(sx, sy, p.name, creationIndex = allocIndex(state)))
                }
                update2DSnapshots(state)
                commitSnapshot(state)
                showDialog.value = false
            },

            // ✅ jen nárys (x,z; y prázdné)
            onConfirmNarys = { ptsN ->

                for (p in ptsN) {
                    val sx = p.x * 10f
                    val sz = p.z * 10f
                    state.pointsNarys.add(Point3DNarys(sx, sz, p.name, creationIndex = allocIndex(state), showInAxoInitial = state.projectionMode != ProjectionMode.AXO))
                }
                update2DSnapshots(state)
                commitSnapshot(state)
                showDialog.value = false
            },
            state = state
        )
    }

    if (showParamDialog.value) {
        CompactParametricLineInputDialog(
            showDialog = showParamDialog.value,
            onDismiss = { showParamDialog.value = false },
            state = state,
            onConfirm = { results ->
                if (results.isEmpty()) return@CompactParametricLineInputDialog



                results.forEach { r ->
                    if (state.projectionMode == ProjectionMode.AXO) {

                        when (r) {
                            is LineInputResult.Line3DResult -> {
                                val point = r.p
                                val direction = r.dir
                                val name = r.name

                                val line3D = Line3D(point, direction, name, creationIndex = allocIndex(state))
                                state.lines3D.add(line3D)
                                val (axop,axodir) = projectLine3DToAxoLocal(line = line3D, basis = state.basis?: return@forEach)
                                if (direction.x == 0f && direction.y == 0f && direction.z != 0f) {
                                    state.pointsPudorys.add(
                                        Point3DPudorys(
                                            x = point.x,
                                            y = point.y,
                                            name = name,
                                            creationIndex = allocIndex(state),
                                            parentLine = line3D,
                                            pendingParentLineId = line3D.id,
                                        )
                                    )
                                    state.lines3DNarys.add(
                                        Line3DProjectionNarys(
                                            Point3DNarys(point.x, point.z, name, creationIndex = allocIndex(state)),
                                            Offset(direction.x, direction.z),
                                            name,
                                            line3D, creationIndex = allocIndex(state),
                                            showInAxoInitial = false
                                        )
                                    )
                                    state.lines3DBokorys.add(
                                        Line3DProjectionBokorys(
                                            Point3DBokorys(point.y, point.z, name, creationIndex = allocIndex(state)),
                                            Offset(direction.y, direction.z),
                                            name,
                                            line3D, creationIndex = allocIndex(state),
                                            showInAxoInitial = false
                                        )
                                    )
                                    state.lines3DAxo.add(
                                        Line3DProjectionAxo(
                                            Point3DAxo(axop.x, axop.y, name, creationIndex = allocIndex(state)),
                                            Offset(axodir.x, axodir.y),
                                            name,
                                            line3D, creationIndex = allocIndex(state)
                                        )
                                    )
                                } else if (direction.x == 0f && direction.y != 0f && direction.z == 0f) {
                                    state.pointsNarys.add(
                                        Point3DNarys(
                                            x = point.x,
                                            z = point.z,
                                            name = name,
                                            creationIndex = allocIndex(state),
                                            parentLine = line3D,
                                            pendingParentLineId = line3D.id,
                                            showInAxoInitial = false
                                        )
                                    )
                                    state.lines3DPudorys.add(
                                        Line3DProjectionPudorys(
                                            Point3DPudorys(point.x, point.y, name, creationIndex = allocIndex(state)),
                                            Offset(direction.x, direction.y),
                                            name,
                                            line3D,
                                            creationIndex = allocIndex(state)
                                        )
                                    )
                                    state.lines3DBokorys.add(
                                        Line3DProjectionBokorys(
                                            Point3DBokorys(point.y, point.z, name, creationIndex = allocIndex(state)),
                                            Offset(direction.y, direction.z),
                                            name,
                                            line3D, creationIndex = allocIndex(state),
                                            showInAxoInitial = false
                                        )
                                    )
                                    state.lines3DAxo.add(
                                        Line3DProjectionAxo(
                                            Point3DAxo(axop.x, axop.y, name, creationIndex = allocIndex(state)),
                                            Offset(axodir.x, axodir.y),
                                            name,
                                            line3D, creationIndex = allocIndex(state)
                                        )
                                    )
                                } else {
                                    state.lines3DPudorys.add(
                                        Line3DProjectionPudorys(
                                            Point3DPudorys(point.x, point.y, name, creationIndex = allocIndex(state)),
                                            Offset(direction.x, direction.y),
                                            name,
                                            line3D,
                                            creationIndex = allocIndex(state)
                                        )
                                    )
                                    state.lines3DNarys.add(
                                        Line3DProjectionNarys(
                                            Point3DNarys(point.x, point.z, name, creationIndex = allocIndex(state)),
                                            Offset(direction.x, direction.z),
                                            name,
                                            line3D, creationIndex = allocIndex(state),
                                            showInAxoInitial = false
                                        )
                                    )
                                    state.lines3DBokorys.add(
                                        Line3DProjectionBokorys(
                                            Point3DBokorys(point.y, point.z, name, creationIndex = allocIndex(state)),
                                            Offset(direction.y, direction.z),
                                            name,
                                            line3D, creationIndex = allocIndex(state),
                                            showInAxoInitial = false
                                        )
                                    )
                                    state.lines3DAxo.add(
                                        Line3DProjectionAxo(
                                            Point3DAxo(axop.x, axop.y, name, creationIndex = allocIndex(state)),
                                            Offset(axodir.x, axodir.y),
                                            name,
                                            line3D, creationIndex = allocIndex(state)
                                        )
                                    )
                                }
                            }

                            else -> {}
                        }


                    } else {
                        when (r) {
                            is LineInputResult.Line3DResult -> {
                                val point = r.p
                                val direction = r.dir
                                val name = r.name

                                val line3D = Line3D(point, direction, name, creationIndex = allocIndex(state))
                                state.lines3D.add(line3D)
                                if (direction.x == 0f && direction.y == 0f && direction.z != 0f) {
                                    state.pointsPudorys.add(
                                        Point3DPudorys(
                                            x = point.x,
                                            y = point.y,
                                            name = name,
                                            creationIndex = allocIndex(state),
                                            parentLine = line3D,
                                            pendingParentLineId = line3D.id,
                                        )
                                    )
                                    state.lines3DNarys.add(
                                        Line3DProjectionNarys(
                                            Point3DNarys(point.x, point.z, name, creationIndex = allocIndex(state)),
                                            Offset(direction.x, direction.z),
                                            name,
                                            line3D, creationIndex = allocIndex(state)
                                        )
                                    )
                                } else if (direction.x == 0f && direction.y != 0f && direction.z == 0f) {
                                    state.pointsNarys.add(
                                        Point3DNarys(
                                            x = point.x,
                                            z = point.z,
                                            name = name,
                                            creationIndex = allocIndex(state),
                                            parentLine = line3D,
                                            pendingParentLineId = line3D.id,
                                        )
                                    )
                                    state.lines3DPudorys.add(
                                        Line3DProjectionPudorys(
                                            Point3DPudorys(point.x, point.y, name, creationIndex = allocIndex(state)),
                                            Offset(direction.x, direction.y),
                                            name,
                                            line3D,
                                            creationIndex = allocIndex(state)
                                        )
                                    )
                                } else {
                                    state.lines3DPudorys.add(
                                        Line3DProjectionPudorys(
                                            Point3DPudorys(point.x, point.y, name, creationIndex = allocIndex(state)),
                                            Offset(direction.x, direction.y),
                                            name,
                                            line3D,
                                            creationIndex = allocIndex(state)
                                        )
                                    )
                                    state.lines3DNarys.add(
                                        Line3DProjectionNarys(
                                            Point3DNarys(point.x, point.z, name, creationIndex = allocIndex(state)),
                                            Offset(direction.x, direction.z),
                                            name,
                                            line3D, creationIndex = allocIndex(state)
                                        )
                                    )
                                }
                            }

                            is LineInputResult.LinePudorysResult -> {
                                // jen půdorysná projekce (2D)
                                state.lines3DPudorys.add(
                                    Line3DProjectionPudorys(
                                        Point3DPudorys(r.x, r.y, r.name, creationIndex = allocIndex(state)),
                                        Offset(r.dx, r.dy),
                                        r.name,
                                        parent = null, creationIndex = allocIndex(state)
                                    )
                                )
                            }

                            is LineInputResult.LineNarysResult -> {
                                // jen nárysná projekce (2D)
                                state.lines3DNarys.add(
                                    Line3DProjectionNarys(
                                        Point3DNarys(r.x, r.z, r.name, creationIndex = allocIndex(state)),
                                        Offset(r.dx, r.dz),
                                        r.name,
                                        parent = null, creationIndex = allocIndex(state)
                                    )
                                )
                            }
                        }
                    }
                }
                commitSnapshot(state)
                update2DSnapshots(state)
            }
        )

    }

    if (showPlaneDialog.value) {
        PlaneEquationInputDialog(
            showDialog = true,
            onDismiss = { showPlaneDialog.value = false },
            onConfirm = { equation, nameText ->


                val (rawTrace1, rawTrace2,rawTrace3) = tracesFromPlaneEquation(equation)
                if (rawTrace1 == null && rawTrace2 == null&& rawTrace3 == null) {
                    println("⚠️ Nelze vytvořit žádnou stopu pro rovnici roviny.")
                    return@PlaneEquationInputDialog
                }

                val plane = Plane3D(
                    tracePudorys = rawTrace1 ?: dummyPudorys(),
                    traceNarys = rawTrace2 ?: dummyNarys(),
                    traceBokorys = rawTrace3 ?: dummyBokorys(),
                    name = nameText.ifBlank { "ρ" },
                    equation = equation, creationIndex = allocIndex(state)
                )

                val pudorysNamed = rawTrace1?.copy(localName = "p₁$nameText", parent = plane, creationIndex = allocIndex(state))
                val narysNamed   = rawTrace2?.copy(localName = "n₂$nameText", parent = plane, creationIndex = allocIndex(state))
                val bokorysNamed   = rawTrace3?.copy(localName = "b₃$nameText", parent = plane, creationIndex = allocIndex(state))

                val planeFinal = plane.copy(
                    tracePudorys = pudorysNamed ?: plane.tracePudorys,
                    traceNarys   = narysNamed   ?: plane.traceNarys,
                    traceBokorys = bokorysNamed ?: plane.traceBokorys
                )

                if (pudorysNamed != null) state.lineTracesPudorys.add(pudorysNamed)
                if (narysNamed   != null) state.lineTracesNarys.add(narysNamed)
                if (bokorysNamed != null) state.lineTracesBokorys.add(bokorysNamed)

                state.planes3D.add(planeFinal)
                showPlaneDialog.value = false
                commitSnapshot(state)
            }
        )
    }
}


