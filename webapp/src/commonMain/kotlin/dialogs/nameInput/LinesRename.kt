package dialogs.nameInput


import androidx.compose.material.LocalTextStyle
import ui.components.MiniInputField
import state.reset3DLineNaming
import utils.withSuffixOnce
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import ui.resources.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import serialization.commitSnapshot
import model.DrawingModeMonge
import model.LocalMongeColors
import model.ProjectionMode
import model.classes.*
import model.darker
import model.lighter
import serialization.SettingsManager
import state.MongeState
import ui.theme.LocalMongeDimens
import ui.mongeui.toolbar.SkikoButton
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex

val LocalDialogFocusReturn = staticCompositionLocalOf<() -> Unit> { {} }

// Aktivní MongeState pro dialogy – umožní MongeDialogu hlásit, že je otevřený,
// takže globální klávesové zkratky (Main.kt) se po dobu jeho zobrazení potlačí.
val LocalDialogHostState = staticCompositionLocalOf<MongeState?> { null }

@Composable
fun MongeDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable RowScope.() -> Unit,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
    ui: Float = SettingsManager.current.UIscale/75f,
    width: Dp = 250f*ui.dp,
    focusBack: FocusRequester? = null,
    dismissOnOutsideClick: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    val requestGlobalFocus = LocalDialogFocusReturn.current
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current

    // Po dobu zobrazení dialogu potlač globální zkratky (píše se do jeho polí).
    val hostState = LocalDialogHostState.current
    DisposableEffect(hostState) {
        hostState?.let { it.openDialogCount++ }
        onDispose { hostState?.let { it.openDialogCount = (it.openDialogCount - 1).coerceAtLeast(0) } }
    }

    fun dismiss() {
        focusManager.clearFocus(force = true)
        if (focusBack != null) focusBack.requestFocus() else requestGlobalFocus()
        onDismissRequest()
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            if (focusBack != null) focusBack.requestFocus() else requestGlobalFocus()
        }
    }

    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {

                    return IntOffset(0, 0)
                }
            }
        },
        onDismissRequest = { dismiss() },
        properties = PopupProperties(focusable = true),
    ) {
        // ✅ SCRIM přes celé okno (to je to "zašednutí")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(dismissOnOutsideClick) {
                    if (!dismissOnOutsideClick) return@pointerInput
                    detectTapGestures(onTap = { dismiss() })
                }
        ) {
            // ✅ samotný dialog – kliky uvnitř nesmí propadnout do scrimu
            Box(
                modifier = modifier
                    .align(Alignment.Center)
                    .pointerInput(Unit) { detectTapGestures(onTap = { /* consume */ }) }
                    .background(
                        colors.background.copy(alpha = 0.95f).darker(0.9f),
                        RoundedCornerShape(dimens.radiusMd)
                    )
                    .border(dimens.borderThin, colors.base, RoundedCornerShape(dimens.radiusMd))
                    .width(width)
            ) {
                Column(
                    modifier = Modifier
                        .padding(dimens.lg)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimens.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) { title() }

                    text()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row { dismissButton?.invoke(this) }
                        Row { confirmButton(this) }
                    }
                }
            }
        }
    }
}


@Composable
fun MongeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    numericOnly: Boolean = false,
    ui: Float = SettingsManager.current.UIscale/75f,
    width: Dp = 180f*ui.dp,
    height: Dp = 50f*ui.dp,
    fontSize: TextUnit = 14f*ui.sp,
    onDone: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    requestInitialFocus: Boolean = false,

    // ✅ kdykoliv se tohle číslo změní, field tvrdě převezme `value`
    forceSetToken: Int = 0,
) {
    val colors = LocalMongeColors.current
    val focusColor = colors.selected
    val unfocusedColor = colors.base

    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = focusRequester ?: internalFocusRequester

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            withFrameNanos { }
            fieldFocusRequester.requestFocus()
        }
    }

    // ✅ interní TextFieldValue (řeší IME/composition/selection)
    var tfv by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value)) }

    // ✅ běžná synchronizace zvenku (např. load stavu)
    LaunchedEffect(value) {
        if (!isFocused && tfv.text != value) {
            tfv = tfv.copy(text = value, selection = TextRange(value.length))
        }
    }

    // ✅ tvrdé přepsání (např. klik na řecké tlačítko)
    LaunchedEffect(forceSetToken) {
        tfv = androidx.compose.ui.text.input.TextFieldValue(
            text = value,
            selection = TextRange(value.length)
        )
    }

    Column(modifier) {
        label?.let {
            Text(it, fontSize = 12f*ui.sp, color = colors.text, modifier = Modifier.padding(bottom = 2f*ui.dp))
        }

        BasicTextField(
            value = tfv,
            onValueChange = { incoming ->
                if (!enabled) return@BasicTextField

                val cleanedText =
                    if (numericOnly) incoming.text
                        .replace(',', '.')
                        .filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }
                    else incoming.text

                // zachovej selection co nejlíp, ale omez ji do rozsahu textu
                val newSel = incoming.selection.constrain(0, cleanedText.length)

                val cleaned = incoming.copy(text = cleanedText, selection = newSel)
                tfv = cleaned
                onValueChange(cleanedText)
            },
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    onDone?.invoke()
                    keyboardActions.onDone?.invoke(this)
                },
                onNext = keyboardActions.onNext,
                onPrevious = keyboardActions.onPrevious,
                onGo = keyboardActions.onGo,
                onSearch = keyboardActions.onSearch,
                onSend = keyboardActions.onSend
            ),
            cursorBrush = SolidColor(focusColor),
            textStyle = TextStyle(
                fontFamily = LocalTextStyle.current.fontFamily,  // dolní indexy – viz ui/theme/AppFont.kt
                fontSize = fontSize,
                color = if (enabled) colors.text else colors.base.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .focusRequester(fieldFocusRequester)
                .width(width)
                .height(height)
                .border(1.dp, if (isFocused) focusColor else unfocusedColor, RoundedCornerShape(4f*ui.dp))
                .background(if (colors.isDark) colors.background.copy(alpha = 0.1f).lighter(0.8f) else
                    colors.background.copy(alpha = 0.1f).darker(0.2f), RoundedCornerShape(4f*ui.dp))
                .padding(horizontal = 4f*ui.dp, vertical = 2f*ui.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (tfv.text.isEmpty()) {
                        Text(
                            placeholder,
                            fontSize = fontSize,
                            color = colors.text,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                    inner()
                }
            }
        )
    }
}

// helper: constrain selection range
private fun TextRange.constrain(min: Int, max: Int): TextRange {
    val s = start.coerceIn(min, max)
    val e = end.coerceIn(min, max)
    return TextRange(s, e)
}


@Composable
fun NameWithIndicesRow(
    name: String,
    onNameChange: (String) -> Unit,
    superscript: String,
    onSupChange: (String) -> Unit,
    onLowerSupChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    lowerSup: Boolean = false,
    lowersuperscript: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),

    showKota: Boolean = false,
    kota: String = "",
    onKotaChange: (String) -> Unit = {},
    kotaLabel: String = "Kóta",
    kotaNumericOnly: Boolean = false,
    onSubmit: (() -> Unit)? = null
) {
    val ui= SettingsManager.current.UIscale/75f
    val idxWidth  = 48f*ui.dp
    val nameFR = remember { FocusRequester() }
    val supFR  = remember { FocusRequester() }
    val lowSupFR = remember { FocusRequester() }
    val kotaFR = remember { FocusRequester() }
    Row(
        modifier = modifier.then(
            if (onSubmit != null) {
                Modifier.onPreviewKeyEvent { e ->
                    if ((e.key == Key.Enter || e.key == Key.NumPadEnter) && e.type == KeyEventType.KeyDown) {
                        onSubmit()
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            }
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8f*ui.dp)
    ) {
        /* 1️⃣ Název */
        MongeTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Název",
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            focusRequester = nameFR,
            requestInitialFocus = true,
            singleLine = true,
            modifier = Modifier
                .width(80f*ui.dp)
                .height(60f*ui.dp)
        )

        /* 2️⃣ Horní a dolní index */
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4f*ui.dp),
            modifier = Modifier.width(idxWidth)
        ) {
            MiniInputField(
                ui=ui,
                value = superscript,
                onValueChange = onSupChange,
                placeholder = "",
                numericOnly = false,
                modifier = Modifier.focusRequester(supFR),
                onTabPrev = { nameFR.requestFocus() },
                onTabNext = {
                    when {
                        lowerSup -> lowSupFR.requestFocus()
                        showKota -> kotaFR.requestFocus()
                        else -> {} // nebo onDone?
                    }
                }
            )

            if (lowerSup) {
                MiniInputField(
                    ui=ui,
                    value = lowersuperscript,
                    onValueChange = onLowerSupChange,
                    placeholder = "",
                    numericOnly = false,
                    modifier = Modifier.focusRequester(lowSupFR),
                    onTabPrev = { supFR.requestFocus() },
                    onTabNext = { if (showKota) kotaFR.requestFocus() }
                )
            }
        }

        /* 3️⃣ Kóta (volitelně) */
        if (showKota) {
            MongeTextField(
                value = kota,
                onValueChange = onKotaChange,
                label = kotaLabel,
                numericOnly = kotaNumericOnly,
                modifier = Modifier.focusRequester(kotaFR),

            )
        }
    }
}


//pojmenovýní nárys přímky
@Composable
fun NameLineNarysDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "single_narys_line" && !state.isNameConfirmed) {

        // Geometrie je hotová už druhým kliknutím. Dialog pouze upravuje její jméno.
        LaunchedEffect(state.rename.lineBeingRenamedNarys?.id) {
            state.rename.lineBeingRenamedNarys?.let { line ->
                if (state.lines3DNarys.none { it.id == line.id }) {
                    state.lines3DNarys.add(line)
                    commitSnapshot(state)
                }
            }
        }

        LaunchedEffect(state.rename.lineBeingRenamedNarys) {
            state.inputName = state.rename.lineBeingRenamedNarys?.name ?: ""
            state.inputSuperscript = ""
        }

        fun confirm() {
            val base = state.inputName.trim()
            val sup  = state.inputSuperscript.trim().takeIf { it.isNotEmpty() }
            val style = state.currentLineStyleSettings
            val subname = if (state.skipnaming) base else base.withSuffixOnce("₂")
                state.rename.lineBeingRenamedNarys?.let { line ->


                    val namedLine = line.copy(
                        localName        = subname,
                        localSuperscript = sup,
                        localColor       = style.color,
                        localLineStyle   = style.style,
                        localStrokeWidth = style.strokeWidth,
                        parentId = state.rename.lineBeingRenamedNarys?.parentId,
                    )
                    val index = state.lines3DNarys.indexOfFirst { it.id == line.id }
                    if (index >= 0) state.lines3DNarys[index] = namedLine else state.lines3DNarys.add(namedLine)
                    println("Přímka pojmenována a přidána: ${namedLine.name}${sup ?: ""}")
                }
            state.isNameConfirmed = true
            setProjectionPhase("narys_start", state)
            state.rename.lineBeingRenamedNarys = null
            state.selectedLineForParallelNarys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }
        if (state.skipnaming) {
            state.inputName=""
            confirm()
        }
            MongeDialog(
                onDismissRequest = {
                    state.isNameConfirmed = true
                    setProjectionPhase("narys_start", state)
                    state.rename.lineBeingRenamedPudorys = null },
                title = {
                    Text(
                        "Název přímky",
                        fontSize = 18f*ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                },
                text = {
                    NameWithIndicesRow(
                        name = state.inputName,
                        onNameChange = { state.inputName = it },
                        superscript = state.inputSuperscript,
                        onSupChange = { state.inputSuperscript = it },
                        onSubmit = { confirm() },
                        modifier     = Modifier.align(Alignment.CenterHorizontally)
                    )
                },
                confirmButton = {
                    SkikoButton(onClick = { confirm() })
                    {
                        Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                        Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                    }
                },
                dismissButton = {
                    SkikoButton(onClick = {state.reset3DLineNaming()
                        repeatCons(state)
                        updateConstructionInfo(state)
                    }) {
                        Text("Zrušit")
                    }},
                ui=ui
            )

        // Enter key = confirm
        LaunchedEffect(Unit) {
            withFrameNanos {
                // posloucháme globální KeyEvent
            }
        }
    }
}
@Composable
fun NamedLineBokorysDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "single_bokorys_line" && !state.isNameConfirmed) {

        LaunchedEffect(state.rename.lineBeingRenamedBokorys?.id) {
            state.rename.lineBeingRenamedBokorys?.let { line ->
                if (state.lines3DBokorys.none { it.id == line.id }) {
                    state.lines3DBokorys.add(line)
                    commitSnapshot(state)
                }
            }
        }

        LaunchedEffect(state.rename.lineBeingRenamedBokorys) {
            state.inputName = state.rename.lineBeingRenamedBokorys?.name ?: ""
            state.inputSuperscript = ""
        }

        fun confirm() {
            val base = state.inputName.trim()
            val sup  = state.inputSuperscript.trim().takeIf { it.isNotEmpty() }
            val style = state.currentLineStyleSettings
            val subname = if (state.skipnaming) base else base.withSuffixOnce("₃")
            state.rename.lineBeingRenamedBokorys?.let { line ->


                val namedLine = line.copy(
                    localName        = subname,
                    localSuperscript = sup,
                    localColor       = style.color,
                    localLineStyle   = style.style,
                    localStrokeWidth = style.strokeWidth,
                    parentId = state.rename.lineBeingRenamedBokorys?.parentId,
                )
                val index = state.lines3DBokorys.indexOfFirst { it.id == line.id }
                if (index >= 0) state.lines3DBokorys[index] = namedLine else state.lines3DBokorys.add(namedLine)
                println("Přímka pojmenována a přidána: ${namedLine.name}${sup ?: ""}")
            }
            state.isNameConfirmed = true
            setProjectionPhase("bokorys_start", state)
            state.rename.lineBeingRenamedBokorys= null
            state.selectedLineForParallelBokorys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }
        if (state.skipnaming) {
            state.inputName=""
            confirm()
        }
        MongeDialog(
            onDismissRequest = {
                state.isNameConfirmed = true
                setProjectionPhase("bokorys_start", state)
                state.rename.lineBeingRenamedBokorys= null },
            title = {
                Text(
                    "Název přímky",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    name = state.inputName,
                    onNameChange = { state.inputName = it },
                    superscript = state.inputSuperscript,
                    onSupChange = { state.inputSuperscript = it },
                    onSubmit = { confirm() },
                    modifier     = Modifier.align(Alignment.CenterHorizontally)
                )
            },
            confirmButton = {
                SkikoButton(onClick = { confirm() })
                {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            },
            dismissButton = {
                SkikoButton(onClick = {state.reset3DLineNaming()
                    repeatCons(state)
                    updateConstructionInfo(state)
                }) {
                    Text("Zrušit")
                }},
            ui=ui
        )

        // Enter key = confirm
        LaunchedEffect(Unit) {
            withFrameNanos {

            }
        }
    }
}
//pojmenování půdorys přímky
@Composable
fun NameLinePudorysDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "single_pudorys_line" && !state.isNameConfirmed) {

        LaunchedEffect(state.rename.lineBeingRenamedPudorys?.id) {
            state.rename.lineBeingRenamedPudorys?.let { line ->
                if (state.lines3DPudorys.none { it.id == line.id }) {
                    state.lines3DPudorys.add(line)
                    commitSnapshot(state)
                }
            }
        }

        LaunchedEffect(state.rename.lineBeingRenamedPudorys) {
            state.inputName = state.rename.lineBeingRenamedPudorys?.name ?: ""
            state.inputSuperscript = ""
        }

        fun confirm() {
            val base = state.inputName.trim()
            val sup = state.inputSuperscript.trim().takeIf { it.isNotEmpty() }
            val style = state.currentLineStyleSettings
            val namesub = if (state.skipnaming) base else base.withSuffixOnce("₁")
                state.rename.lineBeingRenamedPudorys?.let { line ->

                    val namedLine = line.copy(
                        localName = namesub,
                        localSuperscript = sup,
                        localColor = style.color,
                        localLineStyle = style.style,
                        localStrokeWidth = style.strokeWidth,
                        parentId = state.rename.lineBeingRenamedPudorys?.parentId,
                    )
                    val index = state.lines3DPudorys.indexOfFirst { it.id == line.id }
                    if (index >= 0) state.lines3DPudorys[index] = namedLine else state.lines3DPudorys.add(namedLine)
                    println("Přímka v půdorysu pojmenována a přidána: ${namedLine.name}${sup ?: ""}")
                }

            state.isNameConfirmed = true
            setProjectionPhase("pudorys_start", state)
            state.rename.lineBeingRenamedPudorys = null
            state.selectedLineForParallelPudorys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }
        if (state.skipnaming) {
            state.inputName = ""
            confirm()
        }

            MongeDialog(
                onDismissRequest = {
                    state.isNameConfirmed = true
                    setProjectionPhase("pudorys_start", state)
                    state.rename.lineBeingRenamedPudorys = null
                },
                title = {
                    Text(
                        "Název přímky",
                        fontSize = 18f*ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                },
                text = {
                    NameWithIndicesRow(
                        name = state.inputName,
                        onNameChange = { state.inputName = it },
                        superscript = state.inputSuperscript,
                        onSupChange = { state.inputSuperscript = it },
                        onSubmit = { confirm() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                },
                confirmButton = {
                    SkikoButton(onClick = { confirm() })
                    {
                        Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                        Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                    }
                },
                dismissButton = {
                    SkikoButton(onClick = {state.reset3DLineNaming()
                        repeatCons(state)
                        updateConstructionInfo(state)
                    }) {
                        Text("Zrušit")
                    }},
                ui=ui
            )

        LaunchedEffect(Unit) {
            withFrameNanos {
                // posloucháme globální Enter
            }
        }
    }
}
@Composable
fun NameAOLineDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "ao_line_naming" && !state.isNameConfirmed) {

        LaunchedEffect(state.pendingAOLine?.id) {
            state.pendingAOLine?.let { line ->
                if (state.axoOverlayLines.none { it.id == line.id }) {
                    state.axoOverlayLines.add(line)
                    commitSnapshot(state)
                }
            }
        }

        LaunchedEffect(state.pendingAOLine) {
            state.inputName = state.pendingAOLine?.name ?: ""
            state.inputSuperscript = ""
        }

        fun confirm() {
            val base = state.inputName.trim()
            val sup = state.inputLowerSuperscript.trim().takeIf { it.isNotEmpty() }
            val sub = state.inputSuperscript.trim().takeIf { it.isNotEmpty() }

            val namesub = base
            state.pendingAOLine?.let { line ->
                val namedLine = line.copy(
                    name = namesub,
                    lower =  sup,
                    upper = sub,
                )
                val index = state.axoOverlayLines.indexOfFirst { it.id == line.id }
                if (index >= 0) state.axoOverlayLines[index] = namedLine else state.axoOverlayLines.add(namedLine)
                println("Pomocná přímka pojmenována a přidána: ${namedLine.name}${sup ?: ""}${sub ?: ""}")
            }

            state.isNameConfirmed = true
            setProjectionPhase("pudorys_start", state)
            state.pendingAOLine = null
            state.selectedLineForParallelPudorys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }
        if (state.skipnaming) {
            state.inputName = ""
            confirm()
        }

        MongeDialog(
            onDismissRequest = {
                confirm()
                state.isNameConfirmed = true
                setProjectionPhase("pudorys_start", state)
                state.pendingAOLine = null
                state.pendingPoint1 = null
            },
            title = {
                Text(
                    "Název přímky",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    name = state.inputName,
                    onNameChange = { state.inputName = it },
                    superscript = state.inputSuperscript,
                    lowerSup = true,
                    lowersuperscript = state.inputLowerSuperscript,
                    onLowerSupChange = { state.inputLowerSuperscript = it },
                    onSupChange = { state.inputSuperscript = it },
                    onSubmit = { confirm() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            },
            confirmButton = {
                SkikoButton(onClick = { confirm() })
                {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            },
            dismissButton = {
                SkikoButton(onClick = {
                    confirm()
                    state.reset3DLineNaming()
                    repeatCons(state)
                    updateConstructionInfo(state)
                }) {
                    Text("Zrušit")
                }},
            ui=ui
        )

        LaunchedEffect(Unit) {
            withFrameNanos {
            }
        }
    }
}
@Composable
fun NameAxoLineDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "axo_line_naming" && !state.isNameConfirmed) {

        LaunchedEffect(state.pendingAxoLine?.id) {
            state.pendingAxoLine?.let { line ->
                if (state.lines3DAxo.none { it.id == line.id }) {
                    state.lines3DAxo.add(line)
                    commitSnapshot(state)
                }
            }
        }

        LaunchedEffect(state.pendingAxoLine) {
            state.inputName = state.pendingAxoLine?.name ?: ""
            state.inputSuperscript = ""
        }

        fun confirm() {
            val base = state.inputName.trim()
            val sub = state.inputSuperscript.trim().takeIf { it.isNotEmpty() }

            val namesub = base.withSuffixOnce("ₐ")
            state.pendingAxoLine?.let { line ->
                val namedLine = line.copy(
                    localName = namesub,
                    localSuperscript = sub
                )
                val index = state.lines3DAxo.indexOfFirst { it.id == line.id }
                if (index >= 0) state.lines3DAxo[index] = namedLine else state.lines3DAxo.add(namedLine)
                println("Axo přímka pojmenována a přidána: ${namedLine.name}${sub ?: ""}")
            }

            state.isNameConfirmed = true
            setProjectionPhase("pudorys_start", state)
            state.pendingAxoLine = null
            state.selectedLineForParallelPudorys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }
        if (state.skipnaming) {
            state.inputName = ""
            confirm()
        }

        MongeDialog(
            onDismissRequest = {
                confirm()
                state.isNameConfirmed = true
                setProjectionPhase("pudorys_start", state)
                state.pendingAxoLine = null
                state.pendingPoint1 = null
            },
            title = {
                Text(
                    "Název přímky",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    name = state.inputName,
                    onNameChange = { state.inputName = it },
                    superscript = state.inputSuperscript,
                    onSupChange = { state.inputSuperscript = it },
                    onSubmit = { confirm() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            },
            confirmButton = {
                SkikoButton(onClick = { confirm() })
                {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            },
            dismissButton = {
                SkikoButton(onClick = {
                    confirm()
                    state.reset3DLineNaming()
                    repeatCons(state)
                    updateConstructionInfo(state)
                }) {
                    Text("Zrušit")
                }},
            ui=ui
        )

        LaunchedEffect(Unit) {
            withFrameNanos {
            }
        }
    }
}
@Composable
fun RenameAxoLineDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "rename_axol" && !state.isNameConfirmed) {

        /* PŘEDVYPLNĚNÍ */
        LaunchedEffect(state.pendingAxoLine) {
            state.inputName = state.pendingAxoLine?.name?.removeSuffix("ₐ") ?: ""
        }

        /* CONFIRM */
        fun confirm() {
            val base   = state.inputName.trim()
            val upper  = state.inputSuperscript.emptyIfNullText().takeIf { it.isNotEmpty() }
            val axo  = state.pendingAxoLine ?: return
            if (base.isEmpty()) return


            if (axo.parent != null) {
                /* Sdružená přímka */
                val oldPar = axo.parent!!
                val newPar = oldPar.copy(name = base, superscript = upper)

                // 3D parent
                state.lines3D.indexOfFirst { it.id == oldPar.id }.takeIf { it != -1 }?.let {
                    state.lines3D[it] = newPar
                }
                // Nárys projekce
                state.lines3DNarys.find { it.parent === oldPar }?.let { nar ->
                    state.lines3DNarys.indexOfFirst { it.id == nar.id }.takeIf { it != -1 }?.let {
                        state.lines3DNarys[it] =
                            nar.copy(localName = null, localSuperscript = null, parent = newPar, parentId = oldPar.id)
                    }
                }
                // Půdorys projekce
                state.lines3DPudorys.find { it.parent === oldPar }?.let { pud ->
                    state.lines3DPudorys.indexOfFirst { it.id == pud.id }.takeIf { it != -1 }?.let {
                        state.lines3DPudorys[it] =
                            pud.copy(localName = null, localSuperscript = null, parent = newPar, parentId = oldPar.id)
                    }
                }
                //bokorys
                state.lines3DBokorys.find { it.parent === oldPar}?.let { bok ->
                    state.lines3DBokorys.indexOfFirst { it.id == bok.id }.takeIf { it != -1 }?.let {
                        state.lines3DBokorys[it] = bok.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newPar
                        )
                    }
                }
                //axo
                state.lines3DAxo.find { it.parent === oldPar }?.let { axo ->
                    state.lines3DAxo.indexOfFirst { it.id == axo.id }.takeIf { it != -1 }?.let {
                        state.lines3DAxo[it] = axo.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newPar
                        )
                    }
                }

            } else {
                /* Samostatná přímka */
                state.lines3DAxo.indexOfFirst { it.id == axo.id }.takeIf { it != -1 }?.let {
                    state.lines3DAxo[it] = axo.copy(
                        localName        = base.withSuffixOnce("ₐ"),
                        localSuperscript = upper
                    )
                }
            }

            state.isNameConfirmed = true
            commitSnapshot(state)
            state.rename.lineBeingRenamedNarys = null

            repeatCons(state)
            updateConstructionInfo(state)
        }
        /* DIALOG */
        MongeDialog(
            onDismissRequest = { state.isNameConfirmed = true },
            title = {
                Text(
                    "Název přímky",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    name = state.inputName,
                    onNameChange = { state.inputName = it },
                    superscript = state.inputSuperscript,
                    onSupChange = { state.inputSuperscript = it },
                    onSubmit = { confirm() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            },
            confirmButton = {
                SkikoButton(onClick = { confirm() })
                {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            },
            dismissButton = {
                SkikoButton(onClick = {state.reset3DLineNaming()
                    updateConstructionInfo(state)
                    repeatCons(state)}) {
                    Text("Zrušit")
                }},
            ui=ui
        )
    }
}
//přejmenování půdorys přímky
@Composable
fun RenameLinePudorysDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "rename_line_pudorys" && !state.isNameConfirmed) {

        /* Předvyplň – základ + horní index, dolní „₁“ nechte být */
        LaunchedEffect(state.rename.lineBeingRenamedPudorys) {
            val src = state.rename.lineBeingRenamedPudorys

            // základ jména: bez horního i dolního indexu
            state.inputName = when {
                src?.localName != null -> src.localName!!.removeSuffix("₁")
                src?.parent != null    -> src.parent!!.name
                else                   -> ""
            }

            // horní index, pokud existuje
            state.inputSuperscript =
                (src?.localSuperscript ?: src?.parent?.superscript).emptyIfNullText()
        }


        fun confirm() {
            val base = state.inputName.trim()
            val sup  = state.inputSuperscript.emptyIfNullText().takeIf { it.isNotEmpty() }
            val pudorys = state.rename.lineBeingRenamedPudorys ?: return


            if (base.isEmpty()) return

            /* Sdružená vs. samostatná logika */
            if (pudorys.parent != null) {
                val oldParent = pudorys.parent!!
                val newParent = oldParent.copy(name = base, superscript = sup)

                // přepiš parent v lines3D
                state.lines3D.indexOfFirst { it.id == oldParent.id }.takeIf { it != -1 }?.let {
                    state.lines3D[it] = newParent
                }

                // pudorys projekce
                state.lines3DPudorys.indexOfFirst { it.id == pudorys.id }.takeIf { it != -1 }?.let {
                    state.lines3DPudorys[it] = pudorys.copy(
                        localName = null,
                        localSuperscript = null,
                        parent = newParent
                    )
                }

                // narys projekce
                state.lines3DNarys.find { it.parent === oldParent }?.let { narys ->
                    state.lines3DNarys.indexOfFirst { it.id == narys.id }.takeIf { it != -1 }?.let {
                        state.lines3DNarys[it] = narys.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newParent
                        )
                    }
                }
                //bokorys
                state.lines3DBokorys.find { it.parent === oldParent }?.let { bok ->
                    state.lines3DBokorys.indexOfFirst { it.id == bok.id }.takeIf { it != -1 }?.let {
                        state.lines3DBokorys[it] = bok.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newParent
                        )
                    }
                }
                //axo
                state.lines3DAxo.find { it.parent === oldParent }?.let { axo ->
                    state.lines3DAxo.indexOfFirst { it.id == axo.id }.takeIf { it != -1 }?.let {
                        state.lines3DAxo[it] = axo.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newParent
                        )
                    }
                }

            } else {
                // Samostatná přímka
                state.lines3DPudorys.indexOfFirst { it.id == pudorys.id }.takeIf { it != -1 }?.let {
                    state.lines3DPudorys[it] = pudorys.copy(
                        localName = base.withSuffixOnce("₁"),
                        localSuperscript = sup
                    )
                }

            }

            println("Pudorys přímka přejmenována na: $base${sup ?: ""}")

            state.isNameConfirmed = true
            setProjectionPhase("pudorys_start", state)
            state.rename.lineBeingRenamedPudorys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }

            MongeDialog(
                title = {
                    Text(
                        "Přejmenování přímky",
                        fontSize = 18f*ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                },
                onDismissRequest = { state.isNameConfirmed = true },
                text = {
                    NameWithIndicesRow(
                        name = state.inputName,
                        onNameChange = { state.inputName = it },
                        superscript = state.inputSuperscript,
                        onSupChange = { state.inputSuperscript = it },
                        onSubmit = { confirm() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                },
                confirmButton = {
                    SkikoButton(onClick = { confirm() })
                    {
                        Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                        Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                    }
                },
                dismissButton = {
                    SkikoButton(onClick = {state.reset3DLineNaming()
                        repeatCons(state)
                        updateConstructionInfo(state)
                    }) {
                        Text("Zrušit")
                    }},
                ui=ui
            )
    }
}
@Composable
fun RenameLineBokorysDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "rename_line_bokorys" && !state.isNameConfirmed) {

        /* Předvyplň – základ + horní index, dolní „₁“ nechte být */
        LaunchedEffect(state.rename.lineBeingRenamedBokorys) {
            val src = state.rename.lineBeingRenamedBokorys

            // základ jména: bez horního i dolního indexu
            state.inputName = when {
                src?.localName != null -> src.localName!!.removeSuffix("₃")
                src?.parent != null    -> src.parent!!.name
                else                   -> ""
            }

            // horní index, pokud existuje
            state.inputSuperscript =
                (src?.localSuperscript ?: src?.parent?.superscript).emptyIfNullText()
        }


        fun confirm() {
            val base = state.inputName.trim()
            val sup  = state.inputSuperscript.emptyIfNullText().takeIf { it.isNotEmpty() }
            val bokorys = state.rename.lineBeingRenamedBokorys ?: return


            if (base.isEmpty()) return

            /* Sdružená vs. samostatná logika */
            if (bokorys.parent != null) {
                val oldParent = bokorys.parent!!
                val newParent = oldParent.copy(name = base, superscript = sup)

                // přepiš parent v lines3D
                state.lines3D.indexOfFirst { it.id == oldParent.id }.takeIf { it != -1 }?.let {
                    state.lines3D[it] = newParent
                }

                // pudorys projekce
                state.lines3DBokorys.indexOfFirst { it.id == bokorys.id }.takeIf { it != -1 }?.let {
                    state.lines3DBokorys[it] = bokorys.copy(
                        localName = null,
                        localSuperscript = null,
                        parent = newParent
                    )
                }

                // narys projekce
                state.lines3DNarys.find { it.parent === oldParent }?.let { narys ->
                    state.lines3DNarys.indexOfFirst { it.id == narys.id }.takeIf { it != -1 }?.let {
                        state.lines3DNarys[it] = narys.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newParent
                        )
                    }
                }
                state.lines3DPudorys.find { it.parent === oldParent }?.let { pudorys ->
                    state.lines3DPudorys.indexOfFirst { it.id == pudorys.id }.takeIf { it != -1 }?.let {
                        state.lines3DPudorys[it] = pudorys.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newParent
                        )
                    }
                }
                //axo
                state.lines3DAxo.find { it.parent === oldParent }?.let { axo ->
                    state.lines3DAxo.indexOfFirst { it.id == axo.id }.takeIf { it != -1 }?.let {
                        state.lines3DAxo[it] = axo.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newParent
                        )
                    }
                }

            } else {
                // Samostatná přímka
                state.lines3DBokorys.indexOfFirst { it.id == bokorys.id }.takeIf { it != -1 }?.let {
                    state.lines3DBokorys[it] = bokorys.copy(
                        localName = base.withSuffixOnce("₃"),
                        localSuperscript = sup
                    )
                }

            }

            println("Bokorys přímka přejmenována na: $base${sup ?: ""}")

            state.isNameConfirmed = true
            setProjectionPhase("pudorys_start", state)
            state.rename.lineBeingRenamedBokorys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }

        MongeDialog(
            title = {
                Text(
                    "Přejmenování přímky",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            onDismissRequest = { state.isNameConfirmed = true },
            text = {
                NameWithIndicesRow(
                    name = state.inputName,
                    onNameChange = { state.inputName = it },
                    superscript = state.inputSuperscript,
                    onSupChange = { state.inputSuperscript = it },
                    onSubmit = { confirm() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            },
            confirmButton = {
                SkikoButton(onClick = { confirm() })
                {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            },
            dismissButton = {
                SkikoButton(onClick = {state.reset3DLineNaming()
                    repeatCons(state)
                    updateConstructionInfo(state)
                }) {
                    Text("Zrušit")
                }},
            ui=ui
        )
    }
}

//přejmenování nárys přímky
@Composable
fun RenameLineNarysDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "rename_line_narys" && !state.isNameConfirmed) {

        /* PŘEDVYPLNĚNÍ */
        LaunchedEffect(state.rename.lineBeingRenamedNarys) {
            val src = state.rename.lineBeingRenamedNarys
            state.inputName = when {
                src?.localName != null ->
                    src.localName!!.removeSuffix("₂")
                src?.parent != null ->
                    src.parent!!.name
                else -> ""
            }
            state.inputSuperscript =
                (src?.localSuperscript ?: src?.parent?.superscript).emptyIfNullText()
        }

        /* CONFIRM */
        fun confirm() {
            val base   = state.inputName.trim()
            val upper  = state.inputSuperscript.emptyIfNullText().takeIf { it.isNotEmpty() }
            val narys  = state.rename.lineBeingRenamedNarys ?: return
            if (base.isEmpty()) return


            if (narys.parent != null) {
                /* Sdružená přímka */
                val oldPar = narys.parent!!
                val newPar = oldPar.copy(name = base, superscript = upper)

                // 3D parent
                state.lines3D.indexOfFirst { it.id == oldPar.id }.takeIf { it != -1 }?.let {
                    state.lines3D[it] = newPar
                }
                // Nárys projekce
                state.lines3DNarys.indexOfFirst { it.id == narys.id }.takeIf { it != -1 }?.let {
                    state.lines3DNarys[it] =
                        narys.copy(localName = null, localSuperscript = null, parent = newPar, parentId = oldPar.id)
                }
                // Půdorys projekce
                state.lines3DPudorys.find { it.parent === oldPar }?.let { pud ->
                    state.lines3DPudorys.indexOfFirst { it.id == pud.id }.takeIf { it != -1 }?.let {
                        state.lines3DPudorys[it] =
                            pud.copy(localName = null, localSuperscript = null, parent = newPar, parentId = oldPar.id)
                    }
                }
                //bokorys
                state.lines3DBokorys.find { it.parent === oldPar}?.let { bok ->
                    state.lines3DBokorys.indexOfFirst { it.id == bok.id }.takeIf { it != -1 }?.let {
                        state.lines3DBokorys[it] = bok.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newPar
                        )
                    }
                }
                //axo
                state.lines3DAxo.find { it.parent === oldPar }?.let { axo ->
                    state.lines3DAxo.indexOfFirst { it.id == axo.id }.takeIf { it != -1 }?.let {
                        state.lines3DAxo[it] = axo.copy(
                            localName = null,
                            localSuperscript = null,
                            parent = newPar
                        )
                    }
                }

            } else {
                /* Samostatná přímka */
                state.lines3DNarys.indexOfFirst { it.id == narys.id }.takeIf { it != -1 }?.let {
                    state.lines3DNarys[it] = narys.copy(
                        localName        = base.withSuffixOnce("₂"),
                        localSuperscript = upper
                    )
                }
            }

            state.isNameConfirmed = true
            commitSnapshot(state)
            setProjectionPhase("narys_start", state)
            state.rename.lineBeingRenamedNarys = null

            repeatCons(state)
            updateConstructionInfo(state)
        }

        /* DIALOG */
            MongeDialog(
                onDismissRequest = { state.isNameConfirmed = true },
                title = {
                    Text(
                        "Název přímky",
                        fontSize = 18f*ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                },
                text = {
                    NameWithIndicesRow(
                        name = state.inputName,
                        onNameChange = { state.inputName = it },
                        superscript = state.inputSuperscript,
                        onSupChange = { state.inputSuperscript = it },
                        onSubmit = { confirm() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                        // pevný dolní index
                    )
                },
                confirmButton = {
                    SkikoButton(onClick = { confirm() })
                    {
                        Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                        Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                    }
                },
                dismissButton = {
                    SkikoButton(onClick = {state.reset3DLineNaming()
                        updateConstructionInfo(state)
                        repeatCons(state)}) {
                        Text("Zrušit")
                    }},
                ui=ui
            )

    }
}
@Composable
fun RenameHelpLineNarysDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "rename_helpline_narys" && !state.isNameConfirmed) {
        val isNewLine = state.rename.helplineBeingRenamedNarys?.let { pending ->
            state.helpLineNarys.none { it.id == pending.id }
        } == true
        LaunchedEffect(state.rename.helplineBeingRenamedNarys?.id) {
            state.rename.helplineBeingRenamedNarys?.let { line ->
                if (state.helpLineNarys.none { it.id == line.id }) {
                    state.helpLineNarys.add(line)
                    commitSnapshot(state)
                }
            }
        }

        /* PŘEDVYPLNĚNÍ */
        LaunchedEffect(state.rename.helplineBeingRenamedNarys) {
            val src = state.rename.helplineBeingRenamedNarys
            state.inputName = when {
                src?.name != null ->
                    src.name!!
                else -> ""
            }
            state.inputSuperscript =
                src?.localSuperscript.emptyIfNullText()
            state.inputLowerSuperscript =
                src?.lowerSuperscript?: ""
        }

        /* CONFIRM */
        fun confirm() {
            val base   = state.inputName.trim()
            val upper  = state.inputSuperscript.emptyIfNullText().takeIf { it.isNotEmpty() }
            val lower = state.inputLowerSuperscript.trim().takeIf { it.isNotEmpty() }
            val narys  = state.rename.helplineBeingRenamedNarys ?: return
            val existingLine =  state.helpLineNarys.find { it.id == narys.id }
            if (existingLine != null) {
                state.helpLineNarys.indexOfFirst { it.id == narys.id }.takeIf { it != -1 }?.let {
                    state.helpLineNarys[it] = narys.copy(
                        name = base,
                        localSuperscript = upper,
                        lowerSuperscript = lower
                    )
                }
            } else {
                val line = HelpLineNarys(
                    point = narys.point,
                    direction = narys.direction,
                    name = base,
                    lowerSuperscript = lower,
                    localSuperscript = upper,
                    parentAny = null,
                    localLineStyle = narys.lineStyle,
                    localColor = narys.localColor,
                    localStrokeWidth = narys.localStrokeWidth,
                    creationIndex = allocIndex(state)

                )
                state.helpLineNarys.add(line)
            }

            state.isNameConfirmed = true
            setProjectionPhase("narys_start", state)
            state.rename.helplineBeingRenamedNarys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }
        if (state.skipnaming && isNewLine){
            state.inputName = ""
            state.inputSuperscript=""
            state.inputLowerSuperscript = ""
            confirm()
        }
        /* DIALOG */
            MongeDialog(
                onDismissRequest = { state.isNameConfirmed = true },
                title = {
                    Text(
                        "Název pomocné přímky",
                        fontSize = 18f*ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                },
                text = {
                    NameWithIndicesRow(
                        lowerSup = true,
                        lowersuperscript = state.inputLowerSuperscript ,
                        name = state.inputName,
                        onNameChange = { state.inputName = it },
                        superscript = state.inputSuperscript,
                        onSupChange = { state.inputSuperscript = it },
                        onSubmit = { confirm() },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onLowerSupChange = { state.inputLowerSuperscript = it },
                    )
                },
                confirmButton = {
                    SkikoButton(onClick = { confirm() })
                    {
                        Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                        Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                    }
                },
                dismissButton = {
                    SkikoButton(onClick = {state.reset3DLineNaming()
                        updateConstructionInfo(state)
                        repeatCons(state)}) {
                        Text("Zrušit")
                    }},
                ui=ui
            )
    }
}
@Composable
fun RenameHelpLinePudorysDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "rename_helpline_pudorys" && !state.isNameConfirmed) {
        val isNewLine = state.rename.helplineBeingRenamedPudorys?.let { pending ->
            state.helpLinePudorys.none { it.id == pending.id }
        } == true
        LaunchedEffect(state.rename.helplineBeingRenamedPudorys?.id) {
            state.rename.helplineBeingRenamedPudorys?.let { line ->
                if (state.helpLinePudorys.none { it.id == line.id }) {
                    state.helpLinePudorys.add(line)
                    commitSnapshot(state)
                }
            }
        }

        /* PŘEDVYPLNĚNÍ */
        LaunchedEffect(state.rename.helplineBeingRenamedPudorys) {
            val src = state.rename.helplineBeingRenamedPudorys
            state.inputName = when {
                src?.name != null ->
                    src.name!!
                else -> ""
            }
            state.inputSuperscript =
                src?.localSuperscript.emptyIfNullText()
            state.inputLowerSuperscript =
                src?.lowerSuperscript ?: ""
        }

        /* CONFIRM */
        fun confirm() {
            val base = state.inputName.trim()
            val upper = state.inputSuperscript.emptyIfNullText().takeIf { it.isNotEmpty() }
            val lower = state.inputLowerSuperscript.trim().takeIf { it.isNotEmpty() }
            println("jsem tu")
            val pudorys = state.rename.helplineBeingRenamedPudorys ?: return
            val existingLine =  state.helpLinePudorys.find { it.id == pudorys.id }
            if (existingLine != null) {
                state.helpLinePudorys.indexOfFirst { it.id == pudorys.id }.takeIf { it != -1 }?.let {
                    state.helpLinePudorys[it] = pudorys.copy(
                        name = base,
                        localSuperscript = upper,
                        lowerSuperscript = lower
                    )
                }
            } else {
                val line = HelpLinePudorys(
                    point = pudorys.point,
                    direction = pudorys.direction,
                    name = base,
                    lowerSuperscript = lower,
                    localSuperscript = upper,
                    parentAny = null,
                    localLineStyle = pudorys.lineStyle,
                    localColor = pudorys.localColor,
                    localStrokeWidth = pudorys.localStrokeWidth,
                    creationIndex = allocIndex(state)

                )
                state.helpLinePudorys.add(line)
            }
            /* Samostatná přímka */



            state.isNameConfirmed = true
            setProjectionPhase("pudorys_start", state)
            state.rename.helplineBeingRenamedPudorys = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }
        if (state.skipnaming && isNewLine){
            state.inputName = ""
            state.inputSuperscript=""
            state.inputLowerSuperscript = ""
            confirm()
        }
        val label = if (state.projectionMode== ProjectionMode.PLANE) "Název přímky" else "Název pomocné přímky"
        /* DIALOG */
            MongeDialog(
                onDismissRequest = { state.isNameConfirmed = true },
                title = {
                    Text(
                        label,
                        fontSize = 18f*ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                },
                text = {
                    NameWithIndicesRow(
                        lowerSup = true,
                        name = state.inputName,
                        onNameChange = { state.inputName = it },
                        superscript = state.inputSuperscript,
                        onSupChange = { state.inputSuperscript = it },
                        onSubmit = { confirm() },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        lowersuperscript = state.inputLowerSuperscript,
                        onLowerSupChange = { state.inputLowerSuperscript = it },
                    )
                },
                confirmButton = {
                    SkikoButton(onClick = { confirm() })
                    {
                        Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                        Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                    }
                },
                dismissButton = {
                    SkikoButton(onClick = {state.reset3DLineNaming()
                        updateConstructionInfo(state)
                        repeatCons(state)}) {
                        Text("Zrušit")
                    }},
                ui=ui
            )
    }
}
@Composable
fun RenameAOLineDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "rename_aol" && !state.isNameConfirmed) {
        val isNewLine = state.pendingAOLine?.let { pending ->
            state.axoOverlayLines.none { it.id == pending.id }
        } == true
        LaunchedEffect(state.pendingAOLine?.id) {
            state.pendingAOLine?.let { line ->
                if (state.axoOverlayLines.none { it.id == line.id }) {
                    state.axoOverlayLines.add(line)
                    commitSnapshot(state)
                }
            }
        }

        /* PŘEDVYPLNĚNÍ */
        LaunchedEffect(state.pendingAOLine) {
            val src = state.pendingAOLine
            state.inputName = when {
                src?.name != null ->
                    src.name
                else -> ""
            }
            state.inputSuperscript =
                src?.upper.emptyIfNullText()
            state.inputLowerSuperscript =
                src?.lower ?: ""
        }

        /* CONFIRM */
        fun confirm() {
            val base = state.inputName.trim()
            val upper = state.inputSuperscript.emptyIfNullText().takeIf { it.isNotEmpty() }
            val lower = state.inputLowerSuperscript.trim().takeIf { it.isNotEmpty() }
            val aol = state.pendingAOLine ?: return
            val existingLine =  state.axoOverlayLines.find { it.id == aol.id }
            if (existingLine != null) {
                state.axoOverlayLines.indexOfFirst { it.id == aol.id }.takeIf { it != -1 }?.let {
                    state.axoOverlayLines[it] = aol.copy(
                        name = base,
                        upper = upper,
                        lower = lower
                    )
                }
            } else {
                val line = AxoOverlayLine(
                    p = aol.p,
                    dir = aol.dir,
                    name = base,
                    lower = lower,
                    upper = upper,
                    lineStyle = aol.lineStyle,
                    color = aol.color,
                    lineWidth =aol.lineWidth,
                    creationIndex = allocIndex(state)

                )
                state.axoOverlayLines.add(line)
            }

            state.isNameConfirmed = true
            setProjectionPhase("pudorys_start", state)
            state.pendingAOLine = null
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
        }
        if (state.skipnaming && isNewLine){
            state.inputName = ""
            state.inputSuperscript=""
            state.inputLowerSuperscript = ""
            confirm()
        }
        /* DIALOG */
        MongeDialog(
            onDismissRequest = { state.isNameConfirmed = true },
            title = {
                Text(
                    "Název pomocné přímky",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    lowerSup = true,
                    name = state.inputName,
                    onNameChange = { state.inputName = it },
                    superscript = state.inputSuperscript,
                    onSupChange = { state.inputSuperscript = it },
                    onSubmit = { confirm() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    lowersuperscript = state.inputLowerSuperscript,
                    onLowerSupChange = { state.inputLowerSuperscript = it },
                )
            },
            confirmButton = {
                SkikoButton(onClick = { confirm() })
                {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            },
            dismissButton = {
                SkikoButton(onClick = {state.reset3DLineNaming()
                    updateConstructionInfo(state)
                    repeatCons(state)}) {
                    Text("Zrušit")
                }},
            ui=ui
        )
    }
}
//ALERT DIALOG pro pojmenování sdružených průmětů přímky + 3D přímka
@Composable
fun Name3DLineDialog(state: MongeState) {
    val ui= SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    if (state.projectionPhase == "naming_3d_line" && !state.isNameConfirmed) {

        /* Předvyplnění */
        LaunchedEffect(state.rename.lineBeingRenamed3D) {
            val src = state.rename.lineBeingRenamed3D
            state.inputName        = src?.name ?: ""
            state.inputSuperscript = src?.superscript.emptyIfNullText()
        }

        fun confirm() {
            val base = state.inputName.trim().ifBlank { "" }
            val sup  = state.inputSuperscript.emptyIfNullText().takeIf { it.isNotEmpty() }
            val old3D = state.rename.lineBeingRenamed3D ?: return


            /* 1️⃣ Aktualizuj 3D parent */
            val new3D = old3D.copy(name = base, superscript = sup)
            state.lines3D.indexOfFirst { it.id == old3D.id }.takeIf { it != -1 }?.let {
                state.lines3D[it] = new3D
            }

            /* 2️⃣ Aktualizuj pudorys/narys projekce (bez lokálních jmen) */
            state.rename.lineBeingRenamedPudorys?.let { p ->
                val upd = p.copy(localName = null, localSuperscript = null, parent = new3D)
                state.lines3DPudorys.replace(p, upd)
                state.rename.lineBeingRenamedPudorys = upd
            }
            state.rename.lineBeingRenamedNarys?.let { n ->
                val upd = n.copy(localName = null, localSuperscript = null, parent = new3D)
                state.lines3DNarys.replace(n, upd)
                state.rename.lineBeingRenamedNarys = upd
            }

            /* 3️⃣ Aktualizuj body (pokud existují) */
            state.rename.pointPudorysBeingRenamed?.let { pt ->
                state.pointsPudorys.replace(pt, pt.copy(name = base))
            }
            state.rename.pointNarysBeingRenamed?.let { pt ->
                state.pointsNarys.replace(pt, pt.copy(name = base))
            }

            state.reset3DLineNaming()
            commitSnapshot(state)
            repeatCons(state)
            updateConstructionInfo(state)
            resetStavu(state)
        }
        if (state.skipnaming) {
            state.inputName=""
            confirm()
        }

            MongeDialog(
                onDismissRequest = { state.reset3DLineNaming()
                    repeatCons(state)
                    updateConstructionInfo(state)
                                   },
                title = {
                    Text(
                        "Název přímky",
                        fontSize = 18f*ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                },
                text = {
                    NameWithIndicesRow(
                        name = state.inputName,
                        onNameChange = { state.inputName = it },
                        superscript = state.inputSuperscript,
                        onSupChange = { state.inputSuperscript = it },
                        onSubmit = { confirm() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                },
                confirmButton = {
                    SkikoButton(onClick = { confirm() })
                    {
                        Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                        Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                    }
                },
                        dismissButton = {
                    SkikoButton(onClick = {state.reset3DLineNaming()
                        repeatCons(state)
                        updateConstructionInfo(state)
                    }) {
                        Text("Zrušit")
                    }},
                ui=ui
            )

    }
}

/* ——— Pomocné extensiony ——— */
private fun String?.emptyIfNullText(): String {
    val trimmed = this?.trim().orEmpty()
    return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
}

private fun MutableList<Line3DProjectionPudorys>.replace(old: Line3DProjectionPudorys, new: Line3DProjectionPudorys) {
    indexOfFirst { it.id == old.id }.takeIf { it != -1 }?.let { this[it] = new }
}
private fun MutableList<Line3DProjectionNarys>.replace(old: Line3DProjectionNarys, new: Line3DProjectionNarys) {
    indexOfFirst { it.id == old.id }.takeIf { it != -1 }?.let { this[it] = new }
}
private fun MutableList<Point3DPudorys>.replace(old: Point3DPudorys, new: Point3DPudorys) {
    indexOfFirst { it.id == old.id }.takeIf { it != -1 }?.let { this[it] = new }
}
private fun MutableList<Point3DNarys>.replace(old: Point3DNarys, new: Point3DNarys) {
    indexOfFirst { it.id == old.id }.takeIf { it != -1 }?.let { this[it] = new }
}

