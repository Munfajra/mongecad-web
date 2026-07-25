package dialogs.nameInput


import serialization.commitSnapshot
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import model.DrawingModeMonge
import model.LocalMongeColors
import model.classes.Plane3D
import model.classes.PlaneTraceBokorys
import model.classes.PlaneTraceNarys
import model.classes.PlaneTracePudorys
import model.classes.tracesFromPlaneEquation
import serialization.SettingsManager
import state.MongeState
import ui.resetStavu
import ui.mongeui.toolbar.SkikoButton
import ui.mongeui.toolbar.repeatCons
import state.relinkPlaneToTraces
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import utils.allocIndex

fun beginPlaneRename(state: MongeState, plane: Plane3D) {
    val livePlane = state.planes3D.find { it.id == plane.id } ?: plane

    state.planePendingForNaming = null
    state.narysTracePendingForNaming = null
    state.pudorysTracePendingForNaming = null
    state.bokorysTracePendingForNaming = null
    state.showPlaneNamingDialog = false

    state.rename.planeBeingRenamed = livePlane
    state.planeNameInput = livePlane.name
    state.inputName = livePlane.name
    state.isNameConfirmed = false
    setProjectionPhase("rename_plane", state)
}

fun beginPlaneTraceRename(state: MongeState, trace: PlaneTracePudorys) {
    val parentId = trace.parent?.id ?: trace.parentId
    val parent = parentId?.let { id -> state.planes3D.find { it.id == id } } ?: trace.parent
    if (parent != null) {
        beginPlaneRename(state, parent)
        return
    }

    state.rename.planeBeingRenamed = null
    state.narysTracePendingForNaming = null
    state.bokorysTracePendingForNaming = null
    state.pudorysTracePendingForNaming = state.lineTracesPudorys.find { it.id == trace.id } ?: trace
    state.planeNameInput = trace.name.orEmpty()
    state.isNameConfirmed = false
    state.showPlaneNamingDialog = true
    updateConstructionInfo(state)
}

fun beginPlaneTraceRename(state: MongeState, trace: PlaneTraceNarys) {
    val parentId = trace.parent?.id ?: trace.parentId
    val parent = parentId?.let { id -> state.planes3D.find { it.id == id } } ?: trace.parent
    if (parent != null) {
        beginPlaneRename(state, parent)
        return
    }

    state.rename.planeBeingRenamed = null
    state.pudorysTracePendingForNaming = null
    state.bokorysTracePendingForNaming = null
    state.narysTracePendingForNaming = state.lineTracesNarys.find { it.id == trace.id } ?: trace
    state.planeNameInput = trace.name.orEmpty()
    state.isNameConfirmed = false
    state.showPlaneNamingDialog = true
    updateConstructionInfo(state)
}

fun beginPlaneTraceRename(state: MongeState, trace: PlaneTraceBokorys) {
    val parentId = trace.parent?.id ?: trace.parentId
    val parent = parentId?.let { id -> state.planes3D.find { it.id == id } } ?: trace.parent
    if (parent != null) {
        beginPlaneRename(state, parent)
        return
    }

    state.rename.planeBeingRenamed = null
    state.pudorysTracePendingForNaming = null
    state.narysTracePendingForNaming = null
    state.bokorysTracePendingForNaming = state.lineTracesBokorys.find { it.id == trace.id } ?: trace
    state.planeNameInput = trace.name.orEmpty()
    state.isNameConfirmed = false
    state.showPlaneNamingDialog = true
    updateConstructionInfo(state)
}


//ALERT DIALOG pro pojmenování rovin
@Composable
fun PlaneNameDialog(state: MongeState) {
    val ui = SettingsManager.current.UIscale/75f

    /* co je zrovna v řadě na pojmenování? */
    val mode = when {
        state.planePendingForNaming != null       -> "plane"
        state.narysTracePendingForNaming != null  -> "narysTrace"
        state.pudorysTracePendingForNaming != null-> "pudorysTrace"
        state.bokorysTracePendingForNaming != null -> "bokorysTrace"
        else -> return                             // nic k řešení
    }
    val isConstructionNaming = when (mode) {
        "plane" -> true
        "narysTrace" -> state.narysTracePendingForNaming?.let { pending ->
            state.lineTracesNarys.none { it.id == pending.id }
        } == true
        "pudorysTrace" -> state.pudorysTracePendingForNaming?.let { pending ->
            state.lineTracesPudorys.none { it.id == pending.id }
        } == true
        "bokorysTrace" -> state.bokorysTracePendingForNaming?.let { pending ->
            state.lineTracesBokorys.none { it.id == pending.id }
        } == true
        else -> false
    }

    // Také samostatná stopa je od této chvíle hotová geometrie; dialog ji jen přejmenuje.
    LaunchedEffect(mode,
        state.planePendingForNaming?.id,
        state.narysTracePendingForNaming?.id,
        state.pudorysTracePendingForNaming?.id,
        state.bokorysTracePendingForNaming?.id
    ) {
        state.planePendingForNaming?.let { plane ->
            plane.name = ""
            state.planes3D.firstOrNull { it.id == plane.id }?.name = ""
        }
        state.narysTracePendingForNaming?.let { trace ->
            if (state.lineTracesNarys.none { it.id == trace.id }) state.lineTracesNarys.add(trace)
        }
        state.pudorysTracePendingForNaming?.let { trace ->
            if (state.lineTracesPudorys.none { it.id == trace.id }) state.lineTracesPudorys.add(trace)
        }
        state.bokorysTracePendingForNaming?.let { trace ->
            if (state.lineTracesBokorys.none { it.id == trace.id }) state.lineTracesBokorys.add(trace)
        }
    }

    /* sdílená logika zavření */
    fun dismiss() {
        commitSnapshot(state)
        state.showPlaneNamingDialog = false
        resetStavu(state)
        state.planePendingForNaming     = null
        state.narysTracePendingForNaming= null
        state.pudorysTracePendingForNaming = null
        state.bokorysTracePendingForNaming = null
        state.planeNameInput = ""
        updateConstructionInfo(state)
        repeatCons(state)
    }
    if (state.skipnaming && isConstructionNaming) {
        state.planePendingForNaming?.let { plane ->
            plane.name = ""
            state.planes3D.firstOrNull { it.id == plane.id }?.name = ""
        }
        state.narysTracePendingForNaming?.let { trace ->
            if (state.lineTracesNarys.none { it.id == trace.id }) state.lineTracesNarys.add(trace)
        }
        state.pudorysTracePendingForNaming?.let { trace ->
            if (state.lineTracesPudorys.none { it.id == trace.id }) state.lineTracesPudorys.add(trace)
        }
        state.bokorysTracePendingForNaming?.let { trace ->
            if (state.lineTracesBokorys.none { it.id == trace.id }) state.lineTracesBokorys.add(trace)
        }
        dismiss()
    }

    val (title, onConfirm) = when (mode) {

        /*–––––––– 1) celá rovina ––––––––*/
        "plane" -> "Název roviny" to {
            val name = state.planeNameInput.trim()
            val plane = state.planePendingForNaming ?: return@to
            val eq = state.planePendingForNaming!!.equation ?: return@to
            if (name.isBlank()) return@to

            /* pojmenuj stopu P + N */
            val traceP = PlaneTracePudorys(
                point = plane.tracePudorys.point,
                direction = plane.tracePudorys.direction,
                parent = plane,
                localName = name,
                creationIndex = allocIndex(state)
            )
            val traceN = PlaneTraceNarys(
                point = plane.traceNarys.point,
                direction = plane.traceNarys.direction,
                parent = plane,
                localName = name,
                creationIndex = allocIndex(state)
            )

            plane.name = name
            val (pudt, nart) = tracesFromPlaneEquation(eq)

            val idxP = state.lineTracesPudorys.indexOfFirst { it.parent?.id == plane.id || it.parentId == plane.id }
            val idxN = state.lineTracesNarys.indexOfFirst { it.parent?.id == plane.id || it.parentId == plane.id }
            if (pudt != null) {
                if (idxP != -1) state.lineTracesPudorys[idxP] =
                    state.lineTracesPudorys[idxP].copy(localName = name, parent = plane, parentId = plane.id)
                else state.lineTracesPudorys.add(traceP)
            }
        if (nart != null) {
            if (idxN != -1) state.lineTracesNarys[idxN] = state.lineTracesNarys[idxN].copy(localName = name, parent = plane,parentId = plane.id)
            else state.lineTracesNarys.add(traceN)
        }
            when (state.mongeMode) {
                DrawingModeMonge.NARYS -> setProjectionPhase("pudorys_start",state)
                DrawingModeMonge.PUDORYS -> setProjectionPhase("narys_start",state)
            }
            commitSnapshot(state)
            println("📛 Rovina pojmenována jako: $name")
        }

        /*–––––––– 2) nárysná stopa ––––––––*/
        "narysTrace" -> "Název nárysné stopy" to {
            val name  = state.planeNameInput.trim()
            val pending = state.narysTracePendingForNaming ?: return@to
            if (name.isBlank()) return@to

            val idx = state.lineTracesNarys.indexOfFirst { it.id == pending.id }
            if (idx != -1) state.lineTracesNarys[idx] = state.lineTracesNarys[idx].copy(localName = name)
            else state.lineTracesNarys += pending.copy(localName = name)


            updateConstructionInfo(state)
            println("📛 Stopa (nárys) pojmenována jako: $name")
            commitSnapshot(state)
        }

        /*–––––––– 3) půdorysná stopa ––––––––*/
        // PŮDORYS stopa
        "pudorysTrace" -> "Název půdorysné stopy" to {
            val name  = state.planeNameInput.trim()
            val pending = state.pudorysTracePendingForNaming ?: return@to
            if (name.isBlank()) return@to

            val idx = state.lineTracesPudorys.indexOfFirst { it.id == pending.id }
            if (idx != -1) {
                state.lineTracesPudorys[idx] =
                    state.lineTracesPudorys[idx].copy(localName = name)
            } else {
                // fallback: kdyby pending nebyl v listu (nemělo by nastat)
                state.lineTracesPudorys += pending.copy(localName = name)
            }

            updateConstructionInfo(state)
            println("📛 Stopa (půdorys) pojmenována jako: $name")
            commitSnapshot(state)
        }
        "bokorysTrace" -> "Název bokorysné stopy" to {
            val name  = state.planeNameInput.trim()
            val pending = state.bokorysTracePendingForNaming ?: return@to
            if (name.isBlank()) return@to

            val idx = state.lineTracesBokorys.indexOfFirst { it.id == pending.id }
            if (idx != -1) {
                state.lineTracesBokorys[idx] =
                    state.lineTracesBokorys[idx].copy(localName = name)
            } else {
                // fallback: kdyby pending nebyl v listu (nemělo by nastat)
                state.lineTracesBokorys += pending.copy(localName = name)
            }

            updateConstructionInfo(state)
            println("📛 Stopa (bokorys) pojmenována jako: $name")
            commitSnapshot(state)
        }

        else -> {return }
    }
    fun handleConfirm() {
        onConfirm()
            dismiss()
    }
    /* ––––––––––––––––––––––––––––––––––––––––––––––––––––– */
    MongeDialog(
        onDismissRequest = { dismiss() },

        title = {
            Text(title, fontSize = 18f*ui.sp, fontWeight = FontWeight.Bold,
                color = LocalMongeColors.current.text)
        },

        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12f*ui.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e: androidx.compose.ui.input.key.KeyEvent ->
                        if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter, Key.NumPadEnter -> { handleConfirm(); true }
                            Key.Escape                 -> { dismiss(); true }
                            else                       -> false
                        }
                    }
            ) {
                MongeTextField(
                    value = state.planeNameInput,
                    onValueChange = { state.planeNameInput = it },
                    placeholder = "Název",
                    numericOnly = false,
                    requestInitialFocus = true,
                    modifier = Modifier.width(140f*ui.dp),
                    forceSetToken = state.planeNameForceToken,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleConfirm() })
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4f*ui.dp)) {
                    listOf("α", "β", "γ", "δ", "ρ").forEach { symbol ->
                        SkikoButton(
                            onClick = {
                                state.planeNameInput = symbol
                                state.planeNameForceToken++
                            },
                            width = 32f*ui.dp, height = 32f*ui.dp
                        ) { Text(symbol) }
                    }
                }
            }
        },

        confirmButton = {
            SkikoButton(onClick = { handleConfirm() }) {
                Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
            }
        },
        dismissButton = {
            SkikoButton(onClick = { dismiss() }) { Text("Zrušit") }
        }
    )
}

//přejmenování roviny (start PŮDORYS)
@Composable
fun PlaneRenameDialog(state: MongeState) {
    if (state.projectionPhase != "rename_plane" || state.isNameConfirmed) return

    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    fun currentPlane(): Plane3D? {
        val id = state.rename.planeBeingRenamed?.id ?: return null
        return state.planes3D.firstOrNull { it.id == id } ?: state.rename.planeBeingRenamed
    }

    LaunchedEffect(state.projectionPhase, state.rename.planeBeingRenamed?.id) {
        if (state.projectionPhase == "rename_plane" && !state.isNameConfirmed) {
            state.planeNameInput = currentPlane()?.name.orEmpty()
        }
    }
    fun close() {
        state.isNameConfirmed   = true
        state.rename.planeBeingRenamed = null
        state.planeNameInput       = ""   // ✅
        setProjectionPhase("pudorys_start", state)
    }

    // ✅ společné potvrzení (použije Enter i tlačítko OK)
    fun handleConfirm() {
        val newName = state.planeNameInput.trim()
        val old     = state.rename.planeBeingRenamed
        if (old != null && newName.isNotBlank()) {


            val updated = old.copy(name = newName)

            // nahradit v seznamu rovin
            state.planes3D.indexOfFirst { it.id == old.id }
                .takeIf { it != -1 }?.let { state.planes3D[it] = updated }

            // přepnout parent ve stopách
            state.lineTracesPudorys.indices.forEach { i ->
                val tr = state.lineTracesPudorys[i]
                if (tr.parent?.id == old.id || tr.parentId == old.id) {
                    state.lineTracesPudorys[i] = tr.copy(parent = updated, localName = null,parentId = updated.id)
                }
            }
            state.lineTracesNarys.indices.forEach { i ->
                val tr = state.lineTracesNarys[i]
                if (tr.parent?.id == old.id || tr.parentId == old.id) {
                    state.lineTracesNarys[i] = tr.copy(parent = updated, localName = null,parentId = updated.id)
                }
            }
            state.lineTracesBokorys.indices.forEach { i ->
                val tr = state.lineTracesBokorys[i]
                if (tr.parent?.id == old.id || tr.parentId == old.id) {
                    state.lineTracesBokorys[i] = tr.copy(parent = updated, localName = null,parentId = updated.id)
                }
            }


            updateConstructionInfo(state)
            relinkPlaneToTraces(state, updated, clearLocal = true)
            println("📝 Rovina přejmenována na '$newName'")
            state.rename.planeBeingRenamed = updated
            state.triggerRedraw++
            commitSnapshot(state)
        }
        close()
    }

    MongeDialog(
        onDismissRequest = { close() },

        title = {
            Text("Název roviny", fontSize = 18f*ui.sp, fontWeight = FontWeight.Bold, color = colors.text)
        },

        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12f*ui.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // ⬅️ Enter/Escape platí pro celý obsah dialogu (i když je fokus na tlačítku se symbolem)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter, Key.NumPadEnter -> { handleConfirm(); true }
                            Key.Escape                 -> { close(); true }
                            else                       -> false
                        }
                    }
            ) {
                MongeTextField(
                    value = state.planeNameInput,
                    onValueChange = { state.planeNameInput = it },
                    placeholder = "Název",
                    numericOnly = false,
                    requestInitialFocus = true,
                    modifier = Modifier.width(140f*ui.dp),
                    forceSetToken = state.planeNameForceToken,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleConfirm() })
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4f*ui.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    listOf("α", "β", "γ", "δ", "ρ").forEach { symbol ->
                        SkikoButton(
                            onClick = {
                                state.planeNameInput = symbol
                                state.planeNameForceToken++
                            },
                            width = 32f*ui.dp, height = 32f*ui.dp
                        ) { Text(symbol) }
                    }
                }
            }
        },

        confirmButton = {
            SkikoButton(onClick = { handleConfirm() }) {
                Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
            }
        },

        dismissButton = {
            SkikoButton(onClick = { close() }) { Text("Zrušit") }
        }
    )
}
