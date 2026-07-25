package dialogs.batchinput

import ui.components.MiniInputField
import dialogs.LineInputRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ui.resources.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalFocusManager
import model.LocalMongeColors
import model.Offset3D
import model.Point3D
import model.ProjectionMode
import model.darker
import model.lighter
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.SkikoButton
import utils.allocIndex

@Composable
fun ParametricLineCompactRow(
    row: LineInputRow,
    onTabAtEnd: () -> Unit,
    showZ: Boolean
) {
    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current

    LaunchedEffect(row.shouldRequestFocus.value) {
        if (row.shouldRequestFocus.value) {
            row.nameFocus.requestFocus()
            row.shouldRequestFocus.value = false
        }
    }

    // ✅ když nejsme v MONGE, Z/dZ pole nemají být ani vidět ani používané
    LaunchedEffect(showZ) {
        if (!showZ) {
            row.pz.value = ""
            row.dz.value = ""
        }
    }

    // ✅ focus order podle showZ
    val focusList = remember(showZ) {
        if (showZ) listOf(
            row.nameFocus,
            row.pxFocus, row.pyFocus, row.pzFocus,
            row.dxFocus, row.dyFocus, row.dzFocus
        ) else listOf(
            row.nameFocus,
            row.pxFocus, row.pyFocus,
            row.dxFocus, row.dyFocus
        )
    }

    fun Modifier.tabNavigation(current: FocusRequester): Modifier =
        this.onPreviewKeyEvent {
            if (it.key == Key.Tab && it.type == KeyEventType.KeyDown) {
                val index = focusList.indexOf(current)
                if (index in 0 until focusList.lastIndex) {
                    focusList[index + 1].requestFocus()
                } else if (index == focusList.lastIndex) {
                    onTabAtEnd()
                }
                true
            } else false
        }

    // ✅ seznamy trojic/dvojic podle showZ
    val coords = remember(showZ) {
        buildList {
            add(Triple(row.px, row.pxFocus, "X"))
            add(Triple(row.py, row.pyFocus, "Y"))
            if (showZ) add(Triple(row.pz, row.pzFocus, "Z"))
        }
    }
    val dirs = remember(showZ) {
        buildList {
            add(Triple(row.dx, row.dxFocus, "x"))
            add(Triple(row.dy, row.dyFocus, "y"))
            if (showZ) add(Triple(row.dz, row.dzFocus, "z"))
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .wrapContentWidth()
            .horizontalScroll(rememberScrollState())
            .padding(end = 16f*ui.dp)
    ) {
        MiniInputField(
            value = row.name.value,
            onValueChange = { row.name.value = it },
            ui=ui,
            placeholder = "Název",
            fontSize = 13f*ui.sp,
            numericOnly = false,
            width = 50f*ui.dp,
            modifier = Modifier
                .focusRequester(row.nameFocus)
                .tabNavigation(row.nameFocus)
        )

        Text("≡", fontSize = 25f*ui.sp, color = colors.text)
        Text("[", fontSize = 25f*ui.sp, color = colors.text)

        coords.forEachIndexed { i, (stateVal, focus, placeholder) ->
            MiniInputField(
                value = stateVal.value,
                ui=ui,
                onValueChange = { stateVal.value = it },
                placeholder = placeholder,
                numericOnly = true,
                modifier = Modifier
                    .focusRequester(focus)
                    .tabNavigation(focus)
            )
            if (i < coords.lastIndex) Text(";", fontSize = 21f*ui.sp, color = colors.text)
        }

        Text("] + t(", fontSize = 21f*ui.sp, color = colors.text)

        dirs.forEachIndexed { i, (stateVal, focus, placeholder) ->
            MiniInputField(
                value = stateVal.value,
                ui=ui,
                onValueChange = { stateVal.value = it },
                placeholder = placeholder,
                numericOnly = true,
                modifier = Modifier
                    .focusRequester(focus)
                    .tabNavigation(focus)
            )
            if (i < dirs.lastIndex) Text(";", fontSize = 21f*ui.sp, color = colors.text)
        }

        Text(")", fontSize = 21f*ui.sp, color = colors.text)
        Text(
            "; t",
            fontStyle = FontStyle.Italic,
            fontSize = 21f*ui.sp,
            modifier = Modifier.padding(start = 4f*ui.dp),
            color = colors.text
        )
        Text("∈ ℝ", fontSize = 21f*ui.sp, color = colors.text)
    }
}

sealed interface LineInputResult {
    val name: String

    data class Line3DResult(
        val p: Point3D,
        val dir: Offset3D,
        override val name: String
    ) : LineInputResult

    data class LinePudorysResult(
        val x: Float,
        val y: Float,
        val dx: Float,
        val dy: Float,
        override val name: String
    ) : LineInputResult

    data class LineNarysResult(
        val x: Float,
        val z: Float,
        val dx: Float,
        val dz: Float,
        override val name: String
    ) : LineInputResult
}
@Composable
fun CompactParametricLineInputDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    state: MongeState,
    onConfirm: (List<LineInputResult>) -> Unit
) {
    val ui = SettingsManager.current.UIscale/75f
    if (!showDialog) return
    val colors = LocalMongeColors.current
    val isMonge = state.projectionMode == ProjectionMode.MONGE || state.projectionMode == ProjectionMode.KOTO || state.projectionMode == ProjectionMode.AXO

    val lines = remember { mutableStateListOf(LineInputRow()) }
    val scope = rememberCoroutineScope()

    fun tryParse(v: String): Float? = v.trim().replace(',', '.').toFloatOrNull()

    fun addRow() {
        val newRow = LineInputRow()
        newRow.shouldRequestFocus.value = true
        lines.add(newRow)
    }

    fun parseAll(): List<LineInputResult> {
        val out = mutableListOf<LineInputResult>()

        lines.forEach { row ->
            val allBlank =
                row.name.value.isBlank() &&
                        row.px.value.isBlank() &&
                        row.py.value.isBlank() &&
                        row.pz.value.isBlank() &&
                        row.dx.value.isBlank() &&
                        row.dy.value.isBlank() &&
                        row.dz.value.isBlank()

            if (allBlank) return@forEach

            val name = row.name.value.trim().ifBlank { "?" }

            val x  = tryParse(row.px.value)
            val y  = tryParse(row.py.value)
            val z  = tryParse(row.pz.value)
            val dx = tryParse(row.dx.value)
            val dy = tryParse(row.dy.value)
            val dz = tryParse(row.dz.value)

            if (isMonge) {
                when {
                    // 3D
                    x != null && y != null && z != null && dx != null && dy != null && dz != null -> {
                        out += LineInputResult.Line3DResult(
                            p = Point3D(x * 10f, y * 10f, z * 10f, name, creationIndex = allocIndex(state)),
                            dir = Offset3D(dx, dy, dz),
                            name = name
                        )
                    }

                    // Pudorys projection: (x,y) + (x,y)
                    x != null && y != null && dx != null && dy != null && z == null && dz == null -> {
                        out += LineInputResult.LinePudorysResult(
                            x = x * 10f,
                            y = y * 10f,
                            dx = dx,
                            dy = dy,
                            name = name+"\u2081"
                        )
                    }

                    // Narys projection: (x,z) + (x,z)
                    x != null && z != null && dx != null && dz != null && y == null && dy == null -> {
                        out += LineInputResult.LineNarysResult(
                            x = x * 10f,
                            z = z * 10f,
                            dx = dx,
                            dz = dz,
                            name = name+"\u2082"
                        )
                    }

                    else -> Unit // nevalidní/ambivalentní kombinace -> ignoruj
                }
            } else {
                // mimo MONGE: bereme jen XY + dxdy
                if (x != null && y != null && dx != null && dy != null) {
                    out += LineInputResult.LinePudorysResult(
                        x = x * 10f,
                        y = y * 10f,
                        dx = dx,
                        dy = dy,
                        name = name
                    )
                }
            }
        }

        return out
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
                .padding(32f*ui.dp)
                .background(colors.background.copy(alpha = 0.94f).darker(0.9f), RoundedCornerShape(10f*ui.dp))
                .border(1.dp, colors.base.copy(alpha = 0.65f), RoundedCornerShape(10f*ui.dp))
                .width(620f*ui.dp)
                .padding(16f*ui.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18f*ui.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Zadat přímku", fontSize = 18f*ui.sp, fontWeight = FontWeight.Bold, color = colors.text)

                Text(
                    text = if (isMonge)
                        "Název ≡ [X;Y;Z] + t·(x;y;z). Pro jeden průmět zadejte pouze 2 souřadnice"
                    else
                        "Název ≡ [X;Y] + t·(x;y)",
                    color = colors.text.copy(alpha = 0.85f), fontSize = 16f*ui.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(14f*ui.dp)) {
                    lines.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            ParametricLineCompactRow(
                                row = row,
                                showZ = isMonge,          // ✅ tohle přidáš
                                onTabAtEnd = { addRow() }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkikoButton(onClick = { addRow() }) {
                        Icon(
                            painter = painterResource("icons/circle-plus.svg"),
                            contentDescription = "Přidat řádek",
                            modifier = Modifier.size(22f*ui.dp),
                        )
                        Text("Přidat řádek", modifier = Modifier.padding(start = 8f*ui.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8f*ui.dp)) {
                        SkikoButton(onClick = onDismiss) { Text("Zrušit") }

                        SkikoButton(onClick = {
                            val parsed = parseAll()
                            if (parsed.isNotEmpty()) {
                                onConfirm(parsed)
                                onDismiss()
                            }
                        }) {
                            Icon(
                                painter = painterResource("icons/check.svg"),
                                contentDescription = "Vložit",
                                modifier = Modifier.size(22f*ui.dp),
                            )
                            Text("Vložit", modifier = Modifier.padding(start = 8f*ui.dp))
                        }
                    }
                }
            }
        }
    }
}


