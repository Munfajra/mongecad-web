package ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dialogs.Alerts.ConfirmCloseDialog
import dialogs.Alerts.ModePickerDialog
import kotlinx.coroutines.launch
import model.AppTab
import model.DrawingTab
import model.AppMenuBus
import model.LocalMenuBus
import model.LocalMongeColors
import model.MenuCommand
import model.ProjectionMode
import model.YAxisDirectionPlane
import model.StartTab
import serialization.initHistory
import serialization.openMongeFile
import serialization.saveMongeFile
import state.MongeStartState
import state.MongeState
import ui.BrowserUnsavedChangesGuard
import ui.handleGlobalKey
import ui.mongeui.AppMongeUI
import ui.planeUI.toolbar.PlaneUI
import ui.planeUI.toolbar.makeMongeTabState
import ui.mongeui.toolbar.adoptLoadedDrawing

/**
 * Hostitel záložek: úvodní obrazovka + libovolný počet otevřených výkresů.
 *
 * Odpovídá desktopovému `AppMongeWithTabs`, ale bez věcí vázaných na desktop –
 * obnovy z autosave, hlídání nezavřených souborů při ukončení, výběru
 * projekčního módu (web umí jen Monge).
 */
@Composable
fun AppMongeWithTabs(
    onOpenWebsite: (String) -> Unit = {},
) {
    val colors = LocalMongeColors.current
    val tabs = remember { mutableStateListOf<AppTab>(StartTab(MongeStartState())) }
    val currentTabIndex = remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val appFocus = remember { FocusRequester() }
    val requestGlobalFocus: () -> Unit = {
        appFocus.requestFocus()
        Unit
    }
    var pendingCloseIndex by remember { mutableStateOf<Int?>(null) }
    var showModePicker by remember { mutableStateOf(false) }
    var pendingNewMode by remember { mutableStateOf<ProjectionMode?>(null) }
    val hasUnsavedChanges by remember {
        derivedStateOf {
            tabs.filterIsInstance<DrawingTab>().any { it.state.isDirty }
        }
    }

    BrowserUnsavedChangesGuard(hasUnsavedChanges)

    fun closeDrawingTab(index: Int) {
        if (tabs.getOrNull(index)?.closable != true) return
        tabs.removeAt(index)
        if (currentTabIndex.value >= tabs.size) {
            currentTabIndex.value = tabs.lastIndex
        }
    }

    fun newDrawingTab(state: MongeState = MongeState().apply { initHistory() }) {
        val id = (tabs.maxOfOrNull { it.id } ?: 0) + 1
        tabs.add(
            DrawingTab(
                id = id,
                state = state,
                // Bez tohohle by záložka zůstala na výchozím MONGE a měla
                // špatnou ikonu i barvu, i když je výkres v rovině.
                mode = state.projectionMode,
                title = state.displayName
            )
        )
        currentTabIndex.value = tabs.lastIndex
    }

    fun newDrawingTab(mode: ProjectionMode) {
        newDrawingTab(
            MongeState().apply {
                projectionMode = mode
                // V rovině roste y nahoru, v Mongeovi dolů – stejně to nastavuje
                // i načítání výkresu (JSONopen).
                if (mode != ProjectionMode.MONGE) {
                    yAxisDirectionPlane = YAxisDirectionPlane.POSITIVE_UP
                }
                displayName = "Výkres ${tabs.size}"
                initHistory()
            }
        )
    }

    fun openDrawingTab() {
        scope.launch {
            try {
                openMongeFile()?.let { loaded ->
                    val fresh = MongeState().apply { initHistory() }
                    adoptLoadedDrawing(fresh, loaded)
                    newDrawingTab(fresh)
                }
            } finally {
                // File picker si převezme fokus i při zrušení výběru.
                requestGlobalFocus()
            }
        }
    }

    LaunchedEffect(pendingNewMode) {
        pendingNewMode?.let { mode ->
            newDrawingTab(mode)
            pendingNewMode = null
            requestGlobalFocus()
        }
    }

    // Přepnutí módu z lišty: výkres se převede a otevře v nové záložce,
    // původní zůstane nedotčený (stejně jako na desktopu).
    val menuBus = remember { AppMenuBus() }
    val activeState = (tabs.getOrNull(currentTabIndex.value) as? DrawingTab)?.state
    LaunchedEffect(menuBus.pending, activeState) {
        val cmd = menuBus.pending ?: return@LaunchedEffect
        val current = activeState
        menuBus.pending = null
        if (current == null) return@LaunchedEffect
        when (cmd) {
            // Do roviny se vstupuje při zakládání výkresu (úvodní obrazovka),
            // opačný převod desktop nenabízí – tady tedy jen rovina → Monge.
            MenuCommand.OpenMonge -> newDrawingTab(makeMongeTabState(current))
            else -> Unit
        }
    }

    CompositionLocalProvider(LocalMenuBus provides menuBus) {
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .focusRequester(appFocus)
            .focusable()
            // Listener musí pracovat se stavem aktivní záložky. Každá záložka
            // vlastní samostatný MongeState, takže kořenový stav aplikace by
            // zkratky prováděl neviditelně mimo právě otevřený výkres.
            .onPreviewKeyEvent { event ->
                val current = activeState
                if (current != null) {
                    handleGlobalKey(
                        e = event,
                        state = current,
                        onOpen = ::openDrawingTab,
                        onSave = { saveMongeFile(current) },
                    )
                } else {
                    // Na úvodní záložce má smysl pouze otevření souboru.
                    val isOpenShortcut =
                        event.type == KeyEventType.KeyDown &&
                            (event.isCtrlPressed || event.isMetaPressed) &&
                            event.key == Key.O
                    if (isOpenShortcut) {
                        openDrawingTab()
                    }
                    isOpenShortcut
                }
            }
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val tab = tabs.getOrNull(currentTabIndex.value)) {
                is StartTab -> StartScreen(
                    onNewDrawing = { mode -> newDrawingTab(mode) },
                    onOpenDrawing = ::openDrawingTab,
                    onSettings = { tab.startState.showSettingsDialog = true },
                    onOpenWebsite = onOpenWebsite
                )

                is DrawingTab -> when (tab.state.projectionMode) {
                    // Rovina má vlastní lištu, levý panel i seznam objektů;
                    // vstupní vrstvu (klikání, snapping) sdílí s Mongem.
                    ProjectionMode.PLANE -> PlaneUI(tab.state, requestGlobalFocus)
                    else -> AppMongeUI(tab.state, requestGlobalFocus)
                }

                null -> Unit
            }

            // Start tab nemá `Dialogs()` (ten visí na MongeState výkresu),
            // takže nastavení otevřené z úvodní obrazovky se vykresluje tady.
            val startTab = tabs.getOrNull(currentTabIndex.value) as? StartTab
            if (startTab != null && startTab.startState.showSettingsDialog) {
                dialogs.settings.SettingsDialog(
                    onClose = { startTab.startState.showSettingsDialog = false }
                )
            }
        }

        // Záložky dole – nahoře je navigace webu, dvě lišty nad sebou by se
        // pohledově praly a plátno by ztratilo výšku.
        Divider(color = colors.base, modifier = Modifier.fillMaxWidth().height(1.dp))
        CustomTabBar(
            tabs = tabs,
            currentTabIndex = currentTabIndex,
            onAddTab = {
                showModePicker = true
            },
            onCloseTab = { index ->
                val drawing = tabs.getOrNull(index) as? DrawingTab
                if (drawing != null) {
                    if (drawing.state.isDirty) {
                        pendingCloseIndex = index
                    } else {
                        closeDrawingTab(index)
                    }
                }
            },
            onRequestCanvasFocus = requestGlobalFocus
        )

        val closeIndex = pendingCloseIndex
        val closingTab = closeIndex?.let { tabs.getOrNull(it) as? DrawingTab }
        if (closeIndex != null && closingTab != null) {
            ConfirmCloseDialog(
                fileName = closingTab.state.displayName,
                onSave = {
                    saveMongeFile(closingTab.state)
                    closeDrawingTab(closeIndex)
                    pendingCloseIndex = null
                    requestGlobalFocus()
                },
                onDontSave = {
                    closingTab.state.isDirty = false
                    closeDrawingTab(closeIndex)
                    pendingCloseIndex = null
                    requestGlobalFocus()
                },
                onCancel = {
                    pendingCloseIndex = null
                    requestGlobalFocus()
                },
            )
        }

        if (showModePicker) {
            ModePickerDialog(
                onDismiss = {
                    showModePicker = false
                    requestGlobalFocus()
                },
                onPick = { mode ->
                    pendingNewMode = mode
                    showModePicker = false
                },
            )
        }
    }
    }

    LaunchedEffect(currentTabIndex.value) {
        requestGlobalFocus()
    }
}
