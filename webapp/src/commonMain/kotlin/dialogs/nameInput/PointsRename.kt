package dialogs.nameInput

import utils.System
import utils.withSuffixOnce
import utils.withoutProjectionSuffixes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ui.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import serialization.commitSnapshot
import model.*
import model.axo.AxoMode
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import serialization.SettingsManager
import state.MongeState
import ui.mongeui.toolbar.SkikoButton
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.update2DSnapshots
import kotlin.math.abs

// vytvoření single půdorys bodu pomocí sdružených průmětů + rename
@Composable
fun RenamePointsPudorysDialog(state: MongeState)
{ if (state.projectionMode == ProjectionMode.KOTO) return
    if (state.projectionPhase == "single_pudorys" && state.isNameConfirmed && state.pendingX != null && state.pendingY != null) {
        var name = state.inputName.removeSuffix("₁").removeSuffix("₂").ifBlank { "" }
        val base = state.inputName.trim()
        val sup  = state.inputSuperscript.emptyIfNullText()
        // Najdi existující nárysný bod se stejným jménem a x souřadnicí
        val matchingNarys = state.pointsNarys.find {
            it.name == base && it.superscript == sup &&
                    abs(it.x - state.pendingX!!) < 0.01f
        }

        if (matchingNarys != null) {
            val point3D = Point3D(state.pendingX!!, state.pendingY!!, matchingNarys.z, base, superscript = sup,color=state.currentLineStyleSettings.color, creationIndex = allocIndex(state))

            state.pointsPudorys.add(
                Point3DPudorys(
                    x = state.pendingX!!,
                    y = state.pendingY!!,
                    name = base,
                    localSuperscript = sup,
                    parent = point3D,
                    creationIndex = allocIndex(state)
                )
            )

            matchingNarys.apply {
                name = base
                localSuperscript = sup
                parent = point3D
            }
            run { state.sharedPoints3D.add(point3D) }
            update2DSnapshots(state)
            println("Vytvořen spojený 3D bod: $name")

        } else {
                state.pointsPudorys.add(
                    Point3DPudorys(
                        x = state.pendingX!!,
                        y = state.pendingY!!,
                        name = base,
                        localSuperscript = sup,
                        localColor = state.currentLineStyleSettings.color,
                        parent = null,
                        creationIndex = allocIndex(state)
                    )
                )

                state.pointsPudorysSnapshot.clear()
                state.pointsPudorysSnapshot.addAll(state.pointsPudorys)
                update2DSnapshots(state)
                println("Vytvořen samostatný bod v půdorysu: $name\u2081 + +$sup (x=${state.pendingX}, y=${state.pendingY})")

        }
        commitSnapshot(state)
        // Reset
        state.inputName = ""
        state.inputSuperscript= ""
        state.isNameConfirmed = false
        state.projectionPhase = when (state.mongeMode) {
            DrawingModeMonge.PUDORYS -> "pudorys_start"
            DrawingModeMonge.NARYS -> "narys_start"
        }
        repeatCons(state)
        updateConstructionInfo(state)
        state.pendingX = null
        state.pendingY = null
    }
}
@Composable
fun RenamePointsAxoDialog(state: MongeState)
{
    if (state.projectionMode == ProjectionMode.KOTO) return
    if (state.projectionPhase == "single_axo" && state.isNameConfirmed && state.pendingX != null && state.pendingY != null) {
        val name = state.inputName.removeSuffix("ₐ").removeSuffix("₂").ifBlank { "" }
        val base = state.inputName.trim()
        val sup  = state.inputSuperscript.emptyIfNullText()


            val existing = state.pointsAxo.filterNot { it.isSegmentEndpoint }.find {
                abs(it.x - state.pendingX!!) < 0.01f &&
                        abs(it.y - state.pendingY!!) < 0.01f &&
                        it.parent == null
            }

            if (existing == null) {
                state.pointsAxo.add(
                    Point3DAxo(
                        x = state.pendingX!!,
                        y = state.pendingY!!,
                        name = base,
                        localSuperscript = sup,
                        localColor = state.currentLineStyleSettings.color,
                        parent = null,
                        creationIndex = allocIndex(state)
                    )
                )

                println("Vytvořen samostatný AXO bod: $name+$sup (x=${state.pendingX}, y=${state.pendingY})")

            } else {
                existing.name            = base
                existing.localSuperscript = sup
                println("Bod přejmenován na: $base${sup}")
            }

        commitSnapshot(state)
        // Reset
        state.inputName = ""
        state.inputSuperscript= ""
        state.isNameConfirmed = false
        state.projectionPhase = when (state.mongeMode) {
            DrawingModeMonge.PUDORYS -> "pudorys_start"
            DrawingModeMonge.NARYS -> "narys_start"
        }
        repeatCons(state)
        updateConstructionInfo(state)
        state.pendingX = null
        state.pendingY = null
}
}

/**
 * Zrušení tvorby KOTO bodu: odeber necommitnutý placeholder z LaunchedEffectu.
 * Při přejmenování existujícího bodu (pointBeingRenamed != null) nemaž nic.
 */
private fun cancelKotoPointPlaceholder(state: MongeState) {
    if (state.rename.pointBeingRenamed == null) {
        val placeholder = state.rename.pointPudorysBeingRenamed
        if (placeholder != null && placeholder.name.isNullOrBlank()) {
            state.pointsPudorys.removeAll { it.id == placeholder.id }
            update2DSnapshots(state)
        }
    }
    state.rename.pointPudorysBeingRenamed = null
}

@Composable
fun RedoPointsKotoDialog(state: MongeState) {
    if (state.projectionPhase == "KOTO_point" &&
        state.isNameConfirmed &&
        state.isKotaConfirmed &&
        state.pendingX != null &&
        state.pendingY != null
    ) {
        val base = state.inputName.trim()
        val sup  = state.inputSuperscript.emptyIfNullText()
        val kotaValue: Float? = parseKota(state.inputKota)

        // 👇 defaultní návrat
        val nextPhase = "pudorys_start"
        val point = state.pointsPudorys.find { it.id == state.rename.pointPudorysBeingRenamed?.id }
        val color = point?.color ?: state.currentLineStyleSettings.color
        if (kotaValue != null) {
            val point3D = Point3D(
                x = state.pendingX!!,
                y = state.pendingY!!,
                z = kotaValue,
                name = base,
                superscript = sup,
                creationIndex = allocIndex(state)
            )
            val pudorys = Point3DPudorys(
                x = state.pendingX!!,
                y = state.pendingY!!,
                name = base,
                localSuperscript = sup,
                localColor = color,
                parent = point3D,
                creationIndex = allocIndex(state)
            )
            val narys = Point3DNarys(
                x = state.pendingX!!,
                z = kotaValue,
                name = base,
                localSuperscript = sup,
                localColor = color,
                parent = point3D,
                creationIndex = allocIndex(state)
            )


            state.sharedPoints3D.add(point3D)
            state.pointsPudorys.add(pudorys)
            state.pointsNarys.add(narys)
            state.pointsPudorys.remove(point)
            state.rename.pointPudorysBeingRenamed = null
            commitSnapshot(state)
        } else {
            // Bez kóty: samostatný půdorysný bod (bez 3D bodu).
            // Placeholder z LaunchedEffectu nahraď, ať nevznikne duplicitní bod.
            val pudorys = Point3DPudorys(
                x = state.pendingX!!,
                y = state.pendingY!!,
                name = base,
                localSuperscript = sup,
                localColor = point?.color ?: state.currentLineStyleSettings.color,
                parent = null,
                creationIndex = allocIndex(state)
            )
            state.pointsPudorys.add(pudorys)
            state.pointsPudorys.remove(point)
            state.rename.pointPudorysBeingRenamed = null
            commitSnapshot(state)
        }

        // Reset (ale phase nastav podle nextPhase)
        state.inputName = ""
        state.inputSuperscript = ""
        state.inputKota = ""
        state.isNameConfirmed = false
        state.isKotaConfirmed = false

        state.projectionPhase = nextPhase
        repeatCons(state)
        updateConstructionInfo(state)

        state.pendingX = null
        state.pendingY = null
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
}
@Composable
fun RenamePointsNarysDialog(state: MongeState) {

    if (state.projectionPhase == "single_narys" &&
        state.isNameConfirmed &&
        state.pendingX != null && state.pendingZ != null
    ) {
        /* ---- vstupy ---- */
        val base = state.inputName.trim()
        val sup  = state.inputSuperscript.emptyIfNullText()


            /* ---- 2. zkus najít matching pudorys bod ---- */
            val matchingPudorys = state.pointsPudorys.find {
                it.name == base && it.localSuperscript == sup &&
                        abs(it.x - state.pendingX!!) < 0.01f
            }

            if (matchingPudorys != null) {
                /* --- sdružený 3D bod --- */
                val point3D = Point3D(
                    x = state.pendingX!!,
                    y = matchingPudorys.y,
                    z = state.pendingZ!!,
                    name = base,
                    color = state.currentLineStyleSettings.color,
                    superscript = sup,
                    creationIndex = allocIndex(state)
                )

                state.pointsNarys.add(
                    Point3DNarys(
                        x = state.pendingX!!,
                        z = state.pendingZ!!,
                        name = base,                 // bez dolního indexu
                        localSuperscript = sup,
                        parent = point3D,
                        creationIndex = allocIndex(state)
                    )
                )

                /* synchronizuj pudorys bod */
                matchingPudorys.apply {
                    name            = base
                    localSuperscript = sup
                    parent          = point3D
                }

                run { state.sharedPoints3D.add(point3D) }

                println("Vytvořen spojený 3D bod: $base${sup}  (x=${state.pendingX}, y=${matchingPudorys.y}, z=${state.pendingZ})")

            } else {
                /* --- nový samostatný narys bod --- */
                state.pointsNarys.add(
                    Point3DNarys(
                        x = state.pendingX!!,
                        z = state.pendingZ!!,
                        name = base,
                        localColor = state.currentLineStyleSettings.color,
                        localSuperscript = sup,
                        parent = null,
                        creationIndex = allocIndex(state)
                    )
                )
                println("Vytvořen samostatný bod v nárysu: $base${sup}₂  (x=${state.pendingX}, z=${state.pendingZ})")
            }

        /* ---- obnov snapshoty a reset stavu ---- */
        state.pointsNarysSnapshot.apply {
            clear(); addAll(state.pointsNarys)
        }
        state.pointsPudorysSnapshot.apply {
            clear(); addAll(state.pointsPudorys)
        }
        update2DSnapshots(state)
        commitSnapshot(state)

        state.inputName        = ""
        state.inputSuperscript = ""
        state.isNameConfirmed  = false
        state.projectionPhase  = when (state.mongeMode) {
            DrawingModeMonge.PUDORYS -> "pudorys_start"
            DrawingModeMonge.NARYS   -> "narys_start"
        }
        repeatCons(state)
        updateConstructionInfo(state)
        state.pendingX = null
        state.pendingY = null
    }
}
@Composable
fun RenamePointsBokorysDialog(state: MongeState) {

    if (state.projectionPhase == "single_bokorys" &&
        state.isNameConfirmed &&
        state.pendingY != null && state.pendingZ != null
    ) {
        val base = state.inputName.trim()
        val sup  = state.inputSuperscript.emptyIfNullText()


        /* ---- 1. existující samostatný narys bod (stejné souřadnice) ---- */
        val existingBokorys = state.pointsBokorys.find {
            abs(it.y - state.pendingY!!) < 0.01f &&
                    abs(it.z - state.pendingZ!!) < 0.01f &&
                    it.parent == null
        }

        if (existingBokorys != null) {
            existingBokorys.name            = base
            existingBokorys.localSuperscript = sup
            println("Bod přejmenován na: $base${sup}₃  (x=${existingBokorys.y}, z=${existingBokorys.z})")

        } else {
            state.pointsBokorys.add(
                Point3DBokorys(
                    y = state.pendingY!!,
                    z = state.pendingZ!!,
                    name = base,
                    localColor = state.currentLineStyleSettings.color,
                    localSuperscript = sup,
                    parent = null,
                    creationIndex = allocIndex(state)
                )
            )
            println("Vytvořen samostatný bod v bokorysu: $base${sup}3 (x=${state.pendingY}, z=${state.pendingZ})")
        }
        commitSnapshot(state)

        state.inputName = ""
        state.inputSuperscript = ""
        state.isNameConfirmed = false
        repeatCons(state)
        updateConstructionInfo(state)
        state.pendingX = null
        state.pendingY = null
        resetStavu(state)
    }

}

@Composable
fun NamePointsDialog(state: MongeState) {
    val colors = LocalMongeColors.current
    val ui= SettingsManager.current.UIscale/75f

    if ((state.projectionPhase == "narys_finalize" ||
                state.projectionPhase == "pudorys_finalize" ||
                state.projectionPhase == "rename_point_pudorys" ||
                state.projectionPhase == "single_pudorys" ||
                state.projectionPhase == "single_axo_projection"||
                state.projectionPhase == "single_bokorys" ||
                state.projectionPhase == "single_axo" ||
                state.projectionPhase == "axo_finalize"||
                state.projectionPhase == "bokorys_finalize"||
                state.projectionPhase == "single_narys" ) && !state.isNameConfirmed && state.projectionMode != ProjectionMode.KOTO)
    {
        LaunchedEffect(state.projectionPhase, state.pendingX, state.pendingY, state.pendingZ) {
            commitPendingPointGeometry(state)
        }
        if (state.skipnaming && state.rename.pointBeingRenamed == null){
            state.inputName = ""
            savePoint(state)
        }
            MongeDialog(
            onDismissRequest = {
                state.pendingX = null
                state.pendingY = null
                state.inputName = ""
                state.inputSuperscript= ""
                state.isNameConfirmed = false
                state.projectionPhase = when (state.mongeMode) {
                    DrawingModeMonge.PUDORYS -> "pudorys_start"
                    DrawingModeMonge.NARYS -> "narys_start"
                }
                state.rename.pointBeingRenamed = null
            },
            title = {
                Text(
                    "Název Bodu",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    modifier     = Modifier.align(Alignment.CenterHorizontally),
                    name          = state.inputName,
                    onNameChange  = { state.inputName = it.take(10) },
                    superscript   = state.inputSuperscript,
                    onSupChange   = { state.inputSuperscript = it.take(3) },
                    onSubmit = {
                        savePoint(state)
                        SortMode(state)
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { savePoint(state)
                       SortMode(state)
                        }
                    )
                )
            }
            ,
            confirmButton = {
                SkikoButton(onClick = {savePoint(state )
                SortMode(state)
                   }) {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            }

            ,
            dismissButton = {
                SkikoButton(onClick = {
                    state.pendingX = null
                    state.pendingY = null
                    state.pendingZ = null
                    state.inputName = ""
                    state.isNameConfirmed = false
                    state.projectionPhase = when (state.mongeMode) {
                        DrawingModeMonge.PUDORYS -> "pudorys_start"
                        DrawingModeMonge.NARYS -> "narys_start"
                    }
                    state.rename.pointBeingRenamed = null
                    state.inputSuperscript= ""
                }) {
                    Text("Zrušit")
                }

            }
        )
    }

    if ((state.projectionPhase == "pudorys_finalize" ) && !state.isNameConfirmed && !state.isKotaConfirmed && state.projectionMode == ProjectionMode.KOTO)
    {
        if (state.skipnaming && state.rename.pointBeingRenamed == null){
            state.inputName = ""
            state.inputKota = ""
            savePoint(state)
        }
        MongeDialog(
            onDismissRequest = {
                state.pendingX = null
                state.pendingY = null
                state.inputName = ""
                state.inputSuperscript= ""
                state.isNameConfirmed = false
                state.isKotaConfirmed = false
                state.projectionPhase =  "pudorys_start"
                state.rename.pointBeingRenamed = null
                resetStavu(state)
            },
            title = {
                Text(
                    "Kóta a název bodu",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    showKota = true,
                    kota= state.inputKota,
                    kotaNumericOnly = true,
                    kotaLabel = "Kóta",
                    modifier     = Modifier.align(Alignment.CenterHorizontally),
                    name          = state.inputName,
                    onNameChange  = { state.inputName = it.take(10) },
                    superscript   = state.inputSuperscript,
                    onSupChange   = { state.inputSuperscript = it.take(3) },
                    onKotaChange = {state.inputKota = it },
                    onSubmit = { savePoint(state) },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        savePoint(state)

                    })
                )
            }
            ,
            confirmButton = {

                SkikoButton(onClick = {
                    savePoint(state)

                }) {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            }

            ,
            dismissButton = {
                SkikoButton(onClick = {
                    state.pendingX = null
                    state.pendingY = null
                    state.inputName = ""
                    state.isNameConfirmed = false
                    state.isKotaConfirmed = false
                    state.projectionPhase =  "pudorys_start"
                    state.rename.pointBeingRenamed = null
                    state.inputSuperscript= ""
                    resetStavu(state)
                }) {
                    Text("Zrušit")
                }
            }
        )
    }
    if ((state.projectionPhase == "koto_plane_finalize" ) && !state.isNameConfirmed && state.projectionMode == ProjectionMode.KOTO)
    {
        if (state.skipnaming && state.rename.pointBeingRenamed == null){
            state.inputName = ""
            savePoint(state)
        }
        MongeDialog(
            onDismissRequest = {
                state.pendingX = null
                state.pendingY = null
                state.pendingZ = null
                state.inputName = ""
                state.inputSuperscript= ""
                state.isNameConfirmed = false
                state.isKotaConfirmed = false
                state.projectionPhase =  "pudorys_start"
                state.rename.pointBeingRenamed = null
                resetStavu(state)
            },
            title = {
                Text(
                    "Název bodu",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    modifier     = Modifier.align(Alignment.CenterHorizontally),
                    name          = state.inputName,
                    onNameChange  = { state.inputName = it.take(10) },
                    superscript   = state.inputSuperscript,
                    onSupChange   = { state.inputSuperscript = it.take(3) },
                    onSubmit = { savePoint(state) },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        savePoint(state)

                    })
                )
            }
            ,
            confirmButton = {
                SkikoButton(onClick = {
                    savePoint(state)

                }) {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            }

            ,
            dismissButton = {
                SkikoButton(onClick = {
                    state.pendingX = null
                    state.pendingY = null
                    state.inputName = ""
                    state.isNameConfirmed = false
                    state.isKotaConfirmed = false
                    state.projectionPhase =  "pudorys_start"
                    state.rename.pointBeingRenamed = null
                    state.inputSuperscript= ""
                    resetStavu(state)
                }) {
                    Text("Zrušit")
                }
            }
        )
    }
    if ((state.projectionPhase == "KOTO_point") && !state.isNameConfirmed && !state.isKotaConfirmed
        )
    {
        LaunchedEffect(state.projectionPhase, state.pendingX, state.pendingY) {
            if (state.rename.pointBeingRenamed == null && state.rename.pointPudorysBeingRenamed == null) {
                val x = state.pendingX
                val y = state.pendingY
                if (x != null && y != null) {
                    val point = Point3DPudorys(
                        x, y, name = "", localColor = state.currentLineStyleSettings.color,
                        parent = null, creationIndex = allocIndex(state)
                    )
                    state.pointsPudorys.add(point)
                    state.rename.pointPudorysBeingRenamed = point
                    update2DSnapshots(state)
                    // Bez commitu: prázdný placeholder je jen náhled; commitne se až
                    // finální bod v RedoPointsKotoDialog (jinak vznikaly 2 commity /
                    // prázdný bod navíc při doplnění kóty a názvu).
                }
            }
        }
        if (state.skipnaming && state.rename.pointBeingRenamed == null){
            state.inputName = ""
            state.inputKota=""
            savePoint(state)
        }
        MongeDialog(
            onDismissRequest = {
                cancelKotoPointPlaceholder(state)
                state.pendingX = null
                state.pendingY = null
                state.inputName = ""
                state.inputSuperscript= ""
                state.isNameConfirmed = false
                state.isKotaConfirmed = false
                state.projectionPhase = "pudorys_start"
                state.rename.pointBeingRenamed = null
                resetStavu(state)
            },
            title = {
                Text(
                    "Kóta a název bodu",
                    fontSize = 18f*ui.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                NameWithIndicesRow(
                    showKota = true,
                    kota = state.inputKota,
                    kotaNumericOnly = true,
                    kotaLabel = "Kóta",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    name = state.inputName,
                    onNameChange = { state.inputName = it.take(10) },
                    superscript = state.inputSuperscript,
                    onSupChange = { state.inputSuperscript = it.take(3) },
                    onKotaChange = { state.inputKota = it },
                    onSubmit = { savePoint(state) },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { savePoint(state) }
                    )
                )

                // ✅ HNED POD POLI, CELÁ ŠÍŘKA
                SkikoButton(
                    onClick = {
                        state.drawobjects = Mongeobjects.POINTS
                        setProjectionPhase("get_kota", state) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Změřit kótu", Modifier.padding(horizontal = 8f*ui.dp))
                }
            },
            confirmButton = {
                SkikoButton(onClick = { savePoint(state) }) {
                    Icon(painterResource("icons/check.svg"), null, Modifier.size(24f*ui.dp))
                    Text("OK", Modifier.padding(horizontal = 8f*ui.dp))
                }
            },
            dismissButton = {
                SkikoButton(onClick = {
                    cancelKotoPointPlaceholder(state)
                    state.pendingX = null
                    state.pendingY = null
                    state.inputName = ""
                    state.isNameConfirmed = false
                    state.isKotaConfirmed = false
                    state.projectionPhase = "pudorys_start"
                    state.rename.pointBeingRenamed = null
                    state.inputSuperscript = ""
                    resetStavu(state)
                }) {
                    Text("Zrušit")
                }
            }
        )

    }
}

/** Zapíše dokončenou geometrii před dialogem; dialog už ji pouze přejmenuje. */
private fun commitPendingPointGeometry(state: MongeState) {
    if (state.rename.pointBeingRenamed != null) return
    val x = state.pendingX ?: return
    val style = state.currentLineStyleSettings

    when (state.projectionPhase) {
        "narys_finalize", "pudorys_finalize" -> {
            val y = state.pendingY ?: return
            val z = state.pendingZ ?: return
            val parent = Point3D(x, y, z, "", color = style.color, creationIndex = allocIndex(state))
            val pudorys = Point3DPudorys(x, y, name = "", parent = parent, localColor = style.color, creationIndex = allocIndex(state))
            val narys = Point3DNarys(x, z, name = "", parent = parent, localColor = style.color, creationIndex = allocIndex(state))
            state.sharedPoints3D.add(parent)
            state.pointsPudorys.add(pudorys)
            state.pointsNarys.add(narys)
            state.rename.pointBeingRenamed = parent
            state.pendingX = null; state.pendingY = null; state.pendingZ = null
            state.projectionPhase = "rename_point_pudorys"
            update2DSnapshots(state)
            commitSnapshot(state)
        }
        "single_pudorys" -> {
            val y = state.pendingY ?: return
            val point = Point3DPudorys(x, y, name = "", localColor = style.color, parent = null, creationIndex = allocIndex(state))
            state.pointsPudorys.add(point)
            state.rename.pointBeingRenamed = point
            state.pendingX = null; state.pendingY = null
            state.projectionPhase = "rename_point_pudorys"
            update2DSnapshots(state)
            commitSnapshot(state)
        }
        "single_narys" -> {
            val z = state.pendingZ ?: return
            val point = Point3DNarys(x, z, name = "", localColor = style.color, parent = null, creationIndex = allocIndex(state))
            state.pointsNarys.add(point)
            state.rename.pointBeingRenamed = point
            state.pendingX = null; state.pendingZ = null
            state.projectionPhase = "rename_point_pudorys"
            update2DSnapshots(state)
            commitSnapshot(state)
        }
        "single_bokorys" -> {
            val y = state.pendingY ?: return
            val z = state.pendingZ ?: return
            val point = Point3DBokorys(y, z, name = "", localColor = style.color, parent = null, creationIndex = allocIndex(state))
            state.pointsBokorys.add(point)
            state.rename.pointBeingRenamed = point
            state.pendingY = null; state.pendingZ = null
            state.projectionPhase = "rename_point_pudorys"
            commitSnapshot(state)
        }
        "single_axo" -> {
            val y = state.pendingY ?: return
            val point = Point3DAxo(x, y, name = "", localColor = style.color, parent = null, creationIndex = allocIndex(state))
            state.pointsAxo.add(point)
            state.rename.pointBeingRenamed = point
            state.pendingX = null; state.pendingY = null
            state.projectionPhase = "rename_point_pudorys"
            commitSnapshot(state)
        }
    }
}


//přejmenování pomocných bodů
@Composable
fun RenameAidPointDialog(state: MongeState) {
    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    val point = state.pendingAidPoint ?: return

    // 1) Předvyplnit pole při otevření
    LaunchedEffect(point.id) {
        state.inputName = point.name ?: ""
        state.inputSuperscript = point.upperSuperscript.emptyIfNullText()
        state.inputLowerSuperscript = point.lowerSuperscript.emptyIfNullText()
    }

    fun confirm() {
        val newName  = state.inputName.trim()
        val upperSup = state.inputSuperscript.emptyIfNullText()
        val lowerSup = state.inputLowerSuperscript.emptyIfNullText()



        val idx = state.aidPointsLogical.indexOfFirst { it.id == point.id }
        if (idx != -1) {
            state.aidPointsLogical[idx] = point.copy(
                name = newName,
                upperSuperscript = upperSup.ifEmpty {"" },   // ulož jako null, pokud prázdné
                lowerSuperscript = lowerSup.ifEmpty {"" }
            )
        }

        resetStavu(state)
        state.pendingAidPoint = null
        commitSnapshot(state)
    }
    if (state.skipnaming && state.projectionPhase != "rename_aid"){
        state.inputName = ""
        confirm()
    }
    val text = if(state.projectionMode == ProjectionMode.PLANE) "Název bodu" else "Název pomocného bodu"
    MongeDialog(
        onDismissRequest = {
            state.pendingAidPoint = null
            resetStavu(state)
        },
        title = { Text(text,fontSize = 18*ui.sp, color = colors.text) },
        text = {
            Spacer(Modifier.height(8f*ui.dp))

            NameWithIndicesRow(
                // 2) Řízené vstupy – ber hodnoty ze State, ne z p.*
                name          = state.inputName,
                onNameChange  = { state.inputName = it },

                superscript   = state.inputSuperscript,
                onSupChange   = { state.inputSuperscript = it },
                lowerSup      = true,
                lowersuperscript = state.inputLowerSuperscript,
                onLowerSupChange = { state.inputLowerSuperscript = it },
                onSubmit = { confirm() },
                keyboardOptions  = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions  = KeyboardActions(onDone = { confirm() }),
                modifier         = Modifier.align(Alignment.CenterHorizontally)
            )
        },
        confirmButton = {
            SkikoButton(onClick = { confirm() }) { Text("Uložit") }
        },
        dismissButton = {
            SkikoButton(onClick = {
                state.pendingAidPoint = null
                resetStavu(state)
            }) { Text("Zrušit") }
        }
    )
}
@Composable
fun RenameOverlayPointDialog(state: MongeState) {
    val point = state.pendingAOPoint ?: return
    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    // 1) Předvyplnit pole při otevření
    LaunchedEffect(point.id) {
        state.inputName = point.name ?: ""
        state.inputSuperscript = point.upper.emptyIfNullText()
        state.inputLowerSuperscript = point.lower.emptyIfNullText()
    }

    fun confirm() {
        val newName  = state.inputName.trim()
        val upperSup = state.inputSuperscript.emptyIfNullText()
        val lowerSup = state.inputLowerSuperscript.emptyIfNullText()



        val idx = state.axoOverlayPoints.indexOfFirst { it.id == point.id }
        if (idx != -1) {
            state.axoOverlayPoints[idx] = point.copy(
                name = newName,
                upper = upperSup.ifEmpty {""},   // ulož jako null, pokud prázdné
                lower = lowerSup.ifEmpty {""}
            )
        }

        resetStavu(state)
        state.pendingAOPoint = null
        commitSnapshot(state)
    }
    if (state.skipnaming && state.projectionPhase != "rename_aop"){
        state.inputName = ""
        confirm()
    }
    val text = if (state.projectionMode == ProjectionMode.PLANE) "Název bodu" else "Název pomocného bodu"
    MongeDialog(
        onDismissRequest = {
            state.pendingAOPoint = null
            resetStavu(state)
        },
        title = { Text("Název pomocného bodu",fontSize = 18*ui.sp, color = colors.text) },
        text = {
            Spacer(Modifier.height(8f*ui.dp))

            NameWithIndicesRow(
                // 2) Řízené vstupy – ber hodnoty ze State, ne z p.*
                name          = state.inputName,
                onNameChange  = { state.inputName = it },

                superscript   = state.inputSuperscript,
                onSupChange   = { state.inputSuperscript = it },
                lowerSup      = true,
                lowersuperscript = state.inputLowerSuperscript,
                onLowerSupChange = { state.inputLowerSuperscript = it },
                onSubmit = { confirm() },
                keyboardOptions  = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions  = KeyboardActions(onDone = { confirm() }),
                modifier         = Modifier.align(Alignment.CenterHorizontally)
            )
        },
        confirmButton = {
            SkikoButton(onClick = { confirm() }) { Text("Uložit") }
        },
        dismissButton = {
            SkikoButton(onClick = {
                state.pendingAOPoint = null
                resetStavu(state)
            }) { Text("Zrušit") }
        }
    )
}


private fun savePoint(state: MongeState){

    val sup = state.inputSuperscript.emptyIfNullText()
            val cleaned = state.inputName.withoutProjectionSuffixes().ifBlank { "" }
            val isDeferredSinglePoint =
                        state.projectionPhase == "single_pudorys" ||
                        state.projectionPhase == "single_narys" ||
                        state.projectionPhase == "single_bokorys" ||
                        state.projectionPhase == "single_axo" ||
                        (state.projectionPhase == "KOTO_point" && state.rename.pointBeingRenamed == null)
            if (state.projectionPhase == "narys_finalize" &&
                state.pendingX != null && state.pendingY != null && state.pendingZ != null) {

                val point3D = Point3D(state.pendingX!!, state.pendingY!!, state.pendingZ!!, cleaned, superscript = sup,color=state.currentLineStyleSettings.color, creationIndex = allocIndex(state))

                state.pointsPudorys.add(
                    Point3DPudorys(
                        state.pendingX!!,
                        state.pendingY!!,
                        name = cleaned.withSuffixOnce("₁"),
                        parent = point3D,
                        localSuperscript = sup,
                        creationIndex = allocIndex(state)
                    )
                )
                state.pointsNarys.add(
                    Point3DNarys(
                        state.pendingX!!,
                        state.pendingZ!!,
                        name = cleaned.withSuffixOnce("₂"),
                        parent = point3D,
                        localSuperscript = sup,
                        creationIndex = allocIndex(state)
                    )
                )
                state.sharedPoints3D.add(point3D)
                update2DSnapshots(state)
                println("Zkonstruovaný 3D bod: $cleaned+$sup(x=${state.pendingX}, y=${state.pendingY}, z=${state.pendingZ})")

                state.pendingX = null
                state.pendingY = null
                state.pendingZ = null
                state.inputName = ""
                setProjectionPhase("pudorys_start", state)
                state.isNameConfirmed = false
                state.mongeMode = DrawingModeMonge.PUDORYS
                repeatCons(state)

            }
            if (state.projectionPhase == "pudorys_finalize" &&
                state.pendingX != null && state.pendingY != null && state.pendingZ != null) {

                val point3D = Point3D(state.pendingX!!, state.pendingY!!, state.pendingZ!!, cleaned,  superscript = sup,color=state.currentLineStyleSettings.color, creationIndex = allocIndex(state) )

                state.pointsPudorys.add(
                    Point3DPudorys(
                        state.pendingX!!,
                        state.pendingY!!,
                        name = cleaned.withSuffixOnce("₁"),
                        parent = point3D,
                        localSuperscript = sup,
                        creationIndex = allocIndex(state)
                    )
                )
                state.pointsNarys.add(
                    Point3DNarys(
                        state.pendingX!!,
                        state.pendingZ!!,
                        name = cleaned.withSuffixOnce("₂"),
                        parent = point3D,
                        localSuperscript = sup,
                        creationIndex = allocIndex(state)
                    )
                )
                state.sharedPoints3D.add(point3D)
                update2DSnapshots(state)
                println("Zkonstruovaný 3D bod: $cleaned+$sup (x=${state.pendingX}, y=${state.pendingY}, z=${state.pendingZ})")

                state.pendingX = null
                state.pendingY = null
                state.pendingZ = null
                state.inputName = ""

                setProjectionPhase("narys_start", state)
                state.isNameConfirmed = false

                state.mongeMode = DrawingModeMonge.NARYS
                repeatCons(state)
                updateConstructionInfo(state)}

            if (state.projectionPhase == "koto_plane_finalize"  &&  state.pendingX != null && state.pendingY != null && state.pendingZ != null)
            {
                val x = state.pendingX!!
                val y = state.pendingY!!
                val z = state.pendingZ!!
                val color = state.currentLineStyleSettings.color
                val width = state.currentLineStyleSettings.strokeWidth
                // 3D p
                val p3 = Point3D(
                    x = x,
                    y = y,
                    z = z,
                    name = cleaned,
                    color = color,
                    width = width,
                    creationIndex = allocIndex(state)
                )

                // Projections
                val pPud = Point3DPudorys(
                    x = x,
                    y = y,
                    name = cleaned.withSuffixOnce("₁"),
                    localColor = color,
                    localWidth = width,
                    parent = p3,
                    creationIndex = allocIndex(state)
                )

                val pNar = Point3DNarys(
                    x = x,
                    z = z,
                    name = cleaned.withSuffixOnce("₂"),
                    localColor = color,
                    localWidth = width,
                    parent = p3,
                    creationIndex = allocIndex(state)
                )

                // Parent wiring (jak to děláš jinde)
                pPud.parent = p3
                pNar.parent = p3

                // Commit
                state.sharedPoints3D.add(p3)
                state.pointsPudorys.add(pPud)
                state.pointsNarys.add(pNar)
                setProjectionPhase("pudorys_start", state)
                state.isNameConfirmed = false
                repeatCons(state)
                updateConstructionInfo(state)
            }
            else if (state.rename.pointBeingRenamed != null) {
                val cleaned = state.inputName.withoutProjectionSuffixes().ifBlank { "" }

                when (val p = state.rename.pointBeingRenamed) {
                    is Point3DPudorys -> {
                        val parent = p.parent
                        if (parent != null) {
                            val newParent = parent.copy(
                                name = cleaned,
                                superscript = sup
                            )

                            state.sharedPoints3D.indexOfFirst { it.id == parent.id }
                                .takeIf { it != -1 }?.let { idx ->
                                    state.sharedPoints3D[idx] = newParent
                                }

                            state.pointsPudorys.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    state.pointsPudorys[i] = p.copy(
                                        name = cleaned.withSuffixOnce("₁"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }

                            state.pointsNarys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldNarys = state.pointsNarys[i]
                                    state.pointsNarys[i] = oldNarys.copy(
                                        name = cleaned.withSuffixOnce("₂"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsBokorys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldBokorys = state.pointsBokorys[i]
                                    state.pointsBokorys[i] = oldBokorys.copy(
                                        name = cleaned.withSuffixOnce("₃"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsAxo.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val old = state.pointsAxo[i]
                                    state.pointsAxo[i] = old.copy(
                                        name = cleaned.withSuffixOnce("ₐ"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                        } else {
                            state.pointsPudorys.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    if (state.projectionMode == ProjectionMode.KOTO) {
                                        val newKota = parseKota(state.inputKota)
                                        if (newKota != null) {
                                            val point3D = Point3D(
                                                x = p.x,
                                                y = p.y,
                                                z = newKota,
                                                name = cleaned,
                                                superscript = sup,
                                                color = p.color,
                                                width = p.width,
                                                creationIndex = allocIndex(state)
                                            )
                                            val updatedPudorys = p.copy(
                                                name = cleaned.withSuffixOnce("₁"),
                                                localSuperscript = sup,
                                                parent = point3D
                                            )
                                            state.pointsPudorys[i] = updatedPudorys
                                            state.pointsNarys.add(
                                                Point3DNarys(
                                                    x = p.x,
                                                    z = newKota,
                                                    name = cleaned.withSuffixOnce("₂"),
                                                    localSuperscript = sup,
                                                    localColor = p.color,
                                                    parent = point3D,
                                                    creationIndex = allocIndex(state)
                                                )
                                            )
                                            state.sharedPoints3D.add(point3D)
                                        } else {
                                            state.pointsPudorys[i] = p.copy(name = cleaned, localSuperscript = sup)
                                        }
                                        state.projectionPhase = "pudorys_start"
                                        state.pendingX = null
                                        state.pendingY = null
                                        state.pendingZ = null
                                        state.inputKota = ""
                                    } else {
                                        val matchingNarys = state.pointsNarys.firstOrNull {
                                            it.parent == null &&
                                                it.name?.withoutProjectionSuffixes().orEmpty() == cleaned &&
                                                it.localSuperscript.emptyIfNullText() == sup &&
                                                abs(it.x - p.x) < 0.01f
                                        }
                                        if (matchingNarys == null) {
                                            state.pointsPudorys[i] = p.copy(name = cleaned, localSuperscript = sup)
                                        } else {
                                            val parent3D = Point3D(
                                                p.x, p.y, matchingNarys.z, cleaned,
                                                superscript = sup, color = p.color,
                                                creationIndex = allocIndex(state)
                                            )
                                            state.pointsPudorys[i] = p.copy(
                                                name = cleaned.withSuffixOnce("₁"), localSuperscript = sup, parent = parent3D
                                            )
                                            val narysIndex = state.pointsNarys.indexOfFirst { it.id == matchingNarys.id }
                                            if (narysIndex >= 0) state.pointsNarys[narysIndex] = matchingNarys.copy(
                                                name = cleaned.withSuffixOnce("₂"), localSuperscript = sup, parent = parent3D
                                            )
                                            state.sharedPoints3D.add(parent3D)
                                        }
                                    }
                                }
                        }
                    }

                    is Point3DNarys -> {
                        val parent = p.parent
                        if (parent != null) {
                            val newParent = parent.copy(
                                name = cleaned,
                                superscript = sup
                            )
                            state.sharedPoints3D.indexOfFirst { it.id == parent.id }
                                .takeIf { it != -1 }?.let { idx ->
                                    state.sharedPoints3D[idx] = newParent
                                }

                            state.pointsNarys.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    state.pointsNarys[i] = p.copy(
                                        name = cleaned.withSuffixOnce("₂"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }

                            state.pointsPudorys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldPudorys = state.pointsPudorys[i]
                                    state.pointsPudorys[i] = oldPudorys.copy(
                                        name = cleaned.withSuffixOnce("₁"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsBokorys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldBokorys = state.pointsBokorys[i]
                                    state.pointsBokorys[i] = oldBokorys.copy(
                                        name = cleaned.withSuffixOnce("₃"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsAxo.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val old = state.pointsAxo[i]
                                    state.pointsAxo[i] = old.copy(
                                        name = cleaned.withSuffixOnce("ₐ"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                        } else {
                            state.pointsNarys.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val matchingPudorys = state.pointsPudorys.firstOrNull {
                                        it.parent == null &&
                                            it.name?.withoutProjectionSuffixes().orEmpty() == cleaned &&
                                            it.localSuperscript.emptyIfNullText() == sup &&
                                            abs(it.x - p.x) < 0.01f
                                    }
                                    if (matchingPudorys == null) {
                                        state.pointsNarys[i] = p.copy(name = cleaned, localSuperscript = sup)
                                    } else {
                                        val parent3D = Point3D(
                                            p.x, matchingPudorys.y, p.z, cleaned,
                                            superscript = sup, color = p.color,
                                            creationIndex = allocIndex(state)
                                        )
                                        state.pointsNarys[i] = p.copy(
                                            name = cleaned.withSuffixOnce("₂"), localSuperscript = sup, parent = parent3D
                                        )
                                        val pudorysIndex = state.pointsPudorys.indexOfFirst { it.id == matchingPudorys.id }
                                        if (pudorysIndex >= 0) state.pointsPudorys[pudorysIndex] = matchingPudorys.copy(
                                            name = cleaned.withSuffixOnce("₁"), localSuperscript = sup, parent = parent3D
                                        )
                                        state.sharedPoints3D.add(parent3D)
                                    }
                                }
                        }
                    }
                    is Point3DBokorys -> {
                        val parent = p.parent
                        if (parent != null) {
                            val newParent = parent.copy(
                                name = cleaned,
                                superscript = sup
                            )


                            state.sharedPoints3D.indexOfFirst { it.id == parent.id }
                                .takeIf { it != -1 }?.let { idx ->
                                    state.sharedPoints3D[idx] = newParent
                                }

                            state.pointsBokorys.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    state.pointsBokorys[i] = p.copy(
                                        name = cleaned.withSuffixOnce("₃"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }

                            state.pointsPudorys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldPudorys = state.pointsPudorys[i]
                                    state.pointsPudorys[i] = oldPudorys.copy(
                                        name = cleaned.withSuffixOnce("₃"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsNarys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldNarys = state.pointsNarys[i]
                                    state.pointsNarys[i] = oldNarys.copy(
                                        name = cleaned.withSuffixOnce("₂"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsAxo.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val old = state.pointsAxo[i]
                                    state.pointsAxo[i] = old.copy(
                                        name = cleaned.withSuffixOnce("ₐ"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                        } else {
                            state.pointsBokorys.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    state.pointsBokorys[i] = p.copy(name = cleaned, localSuperscript = sup)
                                }
                        }
                    }
                    is Point3DAxo -> {
                        val parent = p.parent
                        if (parent != null) {
                            val newParent = parent.copy(
                                name = cleaned,
                                superscript = sup
                            )


                            state.sharedPoints3D.indexOfFirst { it.id == parent.id }
                                .takeIf { it != -1 }?.let { idx ->
                                    state.sharedPoints3D[idx] = newParent
                                }
                            state.pointsAxo.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val old = state.pointsAxo[i]
                                    state.pointsAxo[i] = old.copy(
                                        name = cleaned.withSuffixOnce("ₐ"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }

                            state.pointsPudorys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldPudorys = state.pointsPudorys[i]
                                    state.pointsPudorys[i] = oldPudorys.copy(
                                        name = cleaned.withSuffixOnce("₃"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsNarys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldNarys = state.pointsNarys[i]
                                    state.pointsNarys[i] = oldNarys.copy(
                                        name = cleaned.withSuffixOnce("₂"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsBokorys.indexOfFirst { it.parent?.id == parent.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldBokorys = state.pointsBokorys[i]
                                    state.pointsBokorys[i] = oldBokorys.copy(
                                        name = cleaned.withSuffixOnce("₃"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }


                        } else {
                            state.pointsAxo.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    state.pointsAxo[i] = p.copy(name = cleaned, localSuperscript = sup)
                                }
                        }

                    }

                    is Point3D -> {
                        if (state.projectionMode == ProjectionMode.KOTO) {
                            val newKota: Float? = parseKota(state.inputKota)
                            val kotaChanged = (newKota != null && newKota != p.z)

                            // 1) Parent: rename + sup + případně oprava z
                            val newParent = if (kotaChanged) {
                                p.copy(
                                    name = cleaned,
                                    superscript = sup,
                                    z = newKota!!              // ✅ oprava kóty v parentu
                                )
                            } else {
                                p.copy(
                                    name = cleaned,
                                    superscript = sup
                                )
                            }

                            // 2) sharedPoints3D: nahraď podle id
                            state.sharedPoints3D.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { idx ->
                                    state.sharedPoints3D[idx] = newParent
                                }

                            // helper: přenese hodnotu v mapě z oldKey -> newKey (kvůli copy())
                            fun <K, V> MutableMap<K, V>.moveKey(oldKey: K, newKey: K) {
                                val v = this.remove(oldKey) ?: return
                                this[newKey] = v
                            }

                            // 3) PŮDORYS: jen jméno + sup + parent (kóta se tu obvykle neprojeví)
                            state.pointsPudorys.indexOfFirst { it.parent?.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldProj = state.pointsPudorys[i]
                                    val newProj = oldProj.copy(
                                        name = cleaned.withSuffixOnce("₁"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                    state.pointsPudorys[i] = newProj

                                    // pokud máš mapy keyed objektem:
                                    state.labelOffsetsPointsPudorys.moveKey(oldProj.id, newProj.id)
                                }

                            // 4) NÁRYS: rename + sup + parent + ✅ pokud kóta změněna, změň i z projekce
                            state.pointsNarys.indexOfFirst { it.parent?.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val oldProj = state.pointsNarys[i]

                                    val newProj = if (kotaChanged) {
                                        oldProj.copy(
                                            name = cleaned.withSuffixOnce("₂"),
                                            localSuperscript = sup,
                                            parent = newParent,
                                            z = newKota!!          // ✅ oprava nárysu (výška)
                                        )
                                    } else {
                                        oldProj.copy(
                                            name = cleaned.withSuffixOnce("₂"),
                                            localSuperscript = sup,
                                            parent = newParent
                                        )
                                    }

                                    state.pointsNarys[i] = newProj

                                    // mapy keyed objektem:
                                    state.labelOffsetsPointsNarys.moveKey(oldProj.id, newProj.id)
                                }
                            resetStavu(state)
                        }

                        else {
                            val newParent = p.copy(
                            name = cleaned,
                            superscript = sup
                            )

                            state.sharedPoints3D.indexOfFirst { it.id == p.id }
                                .takeIf { it != -1 }?.let { idx ->
                                    state.sharedPoints3D[idx] = newParent
                                }

                            state.pointsPudorys.indexOfFirst { it.parent?.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val old = state.pointsPudorys[i]
                                    state.pointsPudorys[i] = old.copy(
                                        name = cleaned.withSuffixOnce("₁"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsBokorys.indexOfFirst { it.parent?.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val old = state.pointsBokorys[i]
                                    state.pointsBokorys[i] = old.copy(
                                        name = cleaned.withSuffixOnce("₃"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }

                            state.pointsNarys.indexOfFirst { it.parent?.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val old = state.pointsNarys[i]
                                    state.pointsNarys[i] = old.copy(
                                        name = cleaned.withSuffixOnce("₂"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                            state.pointsAxo.indexOfFirst { it.parent?.id == p.id }
                                .takeIf { it != -1 }?.let { i ->
                                    val old = state.pointsAxo[i]
                                    state.pointsAxo[i] = old.copy(
                                        name = cleaned.withSuffixOnce("ₐ"),
                                        localSuperscript = sup,
                                        parent = newParent
                                    )
                                }
                        }

                    }
                }
            }

            if (state.rename.pointBeingRenamed != null) {
                state.rename.pointBeingRenamed = null
                state.rename.pointPudorysBeingRenamed = null
                state.rename.pointNarysBeingRenamed = null
                update2DSnapshots(state)
            }

            state.isNameConfirmed = true
    if (!isDeferredSinglePoint) {
        state.inputName = ""
    }
            if (state.projectionMode == ProjectionMode.KOTO) {state.isKotaConfirmed = true}
    if (!isDeferredSinglePoint) {
        commitSnapshot(state)
    }
}
fun parseKota(text: String): Float? =
    text.trim()
        .replace(',', '.')
        .toFloatOrNull()
        ?.times(10f)

private fun String?.emptyIfNullText(): String {
    val trimmed = this?.trim().orEmpty()
    return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
}

fun SortMode (state: MongeState){


    if (state.projectionPhase != "single_pudorys" &&
        state.projectionPhase != "single_narys" &&
        state.projectionPhase != "single_bokorys"&&
        state.projectionPhase != "single_axo" &&
        state.projectionPhase != "single_axo_projection"
        ) {
        if (state.projectionMode == ProjectionMode.MONGE){


        state.projectionPhase = when (state.mongeMode) {
            DrawingModeMonge.PUDORYS -> "pudorys_start"
            DrawingModeMonge.NARYS -> "narys_start"
        }  }
        if (state.projectionMode == ProjectionMode.AXO){
            when(state.axoMode){
                AxoMode.NORMAL_2D -> if (state.projekcnityp == ProjectionType.AUXILIARY)
                    setProjectionPhase("start",state)
                else setProjectionPhase("axostart",state)
                AxoMode.AXO_PUDORYS -> setProjectionPhase("pudorys_start",state)
                AxoMode.AXO_NARYS -> setProjectionPhase("narys_start",state)
                AxoMode.AXO_BOKORYS -> setProjectionPhase("bokorys_start",state)
            }
        }
        repeatCons(state)
        state.inputSuperscript= ""
    }
}
