package monge.input.combineprojections

import utils.System
import androidx.compose.ui.geometry.Offset
import dialogs.nameInput.reset3DLineNaming
import serialization.commitSnapshot
import model.*
import model.classes.Line3D
import model.classes.Line2DProjection
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetAfterAssociated
import ui.resetStavu
import utils.allocIndex
import utils.combineProjectionsToLine3D
import utils.getLogicalCursor
import kotlin.math.abs

private const val LINE_COMPLETION_EPS = 1e-6f
private const val LINE_COMPLETION_PERPENDICULAR_TO_X12_MESSAGE =
    "Přímka má průmět kolmý na osu x₁₂. Z těchto dvou čárových průmětů nelze jednoznačně sestavit prostorovou přímku."

fun CompleteLineAdd(state: MongeState, projection: Line2DProjection? = null) {

    // Overlay i panel objektů znají přesný průmět, na kterém uživatel stiskl
    // „Dokončit“. Globální výběr může při construction modifieru obsahovat také
    // starší vzorovou přímku, proto jej používáme jen jako fallback.
    val pudorys = when (projection) {
        is Line3DProjectionPudorys -> projection
        null -> state.selectedLinesPudorys.lastOrNull() as? Line3DProjectionPudorys
        else -> null
    }
    val narys = when (projection) {
        is Line3DProjectionNarys -> projection
        null -> state.selectedLinesNarys.lastOrNull() as? Line3DProjectionNarys
        else -> null
    }

    // Nové doplnění nesmí zdědit průmět ani konstrukční vzor z minulého pokusu.
    state.pendingLinePudorysCompletion.clear()
    state.pendingLineNarysCompletion.clear()
    state.storedLinePudorysForParallel = null
    state.storedLineNarysForParallel = null
    state.selectedLineForParallelPudorys = null
    state.selectedLineForParallelNarys = null
    state.selectedSegmentForParallelPudorys = null
    state.selectedSegmentForParallelNarys = null

    // V cílové průmětně se má případný construction vzor vybrat znovu.
    if (pudorys != null) {
        state.selectedLinesNarys.clear()
        state.selectedLineIdsNarys.clear()
        state.selectedSegmentsNarys.clear()
    } else if (narys != null) {
        state.selectedLinesPudorys.clear()
        state.selectedLineIdsPudorys.clear()
        state.selectedSegmentsPudorys.clear()
    }

    state.completionPending = null
    state.reusingExistingProjection = false
    state.inputName = ""
    state.projectionPhase = when (state.mongeMode) {
        DrawingModeMonge.PUDORYS -> "pudorys_start"
        DrawingModeMonge.NARYS -> "narys_start"
    }
    when {
        pudorys != null -> {
            if (pudorys.direction.x==0f){
                state.mongeMode = DrawingModeMonge.NARYS
                state.projekcnityp= ProjectionType.ASSOCIATED
                state.drawobjects = Mongeobjects.LINES
                state.specialLineCase.value = SpecialLineCase.ParallelToPudorys
                state.pendingXpudorys=pudorys.point.x
                state.pendingY = pudorys.point.y
                state.pendingDirection = pudorys.direction
                state.reusingExistingProjection = true
                state.isNameConfirmed = false
                state.pendingLinePudorysCompletion += pudorys

                setProjectionPhase("special_case_point_in_narys",state)
            } else {
            state.pendingLinePudorysCompletion += pudorys
            state.pendingXpudorys = pudorys.point.x
            state.pendingY = pudorys.point.y
            state.pendingDirection = pudorys.direction
            state.isNameConfirmed = false
            state.drawobjects = Mongeobjects.LINES
            state.projekcnityp = ProjectionType.ASSOCIATED
            setProjectionPhase("projection_line_start_narys", state)
            state.pendingMongeModeChange = DrawingModeMonge.NARYS
            state.reusingExistingProjection = true
        }
        }
        narys != null -> {
            if (narys.direction.x==0f){
                state.mongeMode = DrawingModeMonge.PUDORYS
                state.projekcnityp= ProjectionType.ASSOCIATED
                state.drawobjects = Mongeobjects.LINES
                state.specialLineCase.value = SpecialLineCase.ParallelToNarys
                state.pendingXnarys=narys.point.x
                state.pendingZ = narys.point.z
                state.pendingDirectionNarys = narys.direction
                state.reusingExistingProjection = true
                state.isNameConfirmed = false
                state.pendingLineNarysCompletion += narys

                setProjectionPhase("special_case_point_in_pudorys",state)

            }else {
                state.pendingLineNarysCompletion += narys
                state.pendingXnarys = narys.point.x
                state.pendingZ = narys.point.z
                state.pendingDirectionNarys = narys.direction
                state.isNameConfirmed = false
                state.drawobjects = Mongeobjects.LINES
                state.projekcnityp = ProjectionType.ASSOCIATED
                setProjectionPhase("projection_line_start_pudorys", state)
                state.pendingMongeModeChange = DrawingModeMonge.PUDORYS
                state.reusingExistingProjection = true
            }
        }
    }
}
fun NarysFinalizeLineAuto(state: MongeState,snappedPointLogical: Offset?,cursor: Offset) {
    if (state.projectionPhase == "line_finalize_narys_auto") {
        if (state.pendingXnarys != null && state.pendingDirectionNarys != null && state.pendingZ != null) {
            val logical =
                getLogicalCursor(
                    snappedPointLogical,
                    cursor,
                    state.canvasOffset,
                    state.scale,
                    state.canvasWidth,
                    state.canvasHeight,
                    state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                    state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
                )
            val logicalY = logical.y

            val dxPudorys = logical.x - state.pendingXpudorys!!
            val dyPudorys = logicalY - state.pendingY!!

            if (abs(dxPudorys) < LINE_COMPLETION_EPS && abs(dyPudorys) < LINE_COMPLETION_EPS) {
                showLineCompletionError(state, "Neplatný směr – nulová délka.")
                return
            }
            if (abs(dxPudorys) < LINE_COMPLETION_EPS) {
                showLineCompletionError(state, LINE_COMPLETION_PERPENDICULAR_TO_X12_MESSAGE)
                setProjectionPhase("projection_line_start_pudorys_dir",state)
                return
            } else {
                val selectedNarys =
                    state.pendingLineNarysCompletion.singleOrNull()
                        ?: run {
                            showLineCompletionError(state, "Není vybrána právě jedna přímka v nárysu.")
                            return
                        }
                Offset(dxPudorys, dyPudorys)
                val commonName = (selectedNarys.name ?: "").removeSuffix("₂")
                val commonSup = (selectedNarys.superscript ?: "")
                val style = state.currentLineStyleSettings
                // 1. Vytvoř originální projekce podle uživatele
                val projPudorys = Line3DProjectionPudorys(
                    point = Point3DPudorys(state.pendingXpudorys!!, state.pendingY!!, name = commonName),
                    direction = Offset(dxPudorys, dyPudorys),
                    localName = commonName,
                    localSuperscript = commonSup,
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth,
                    creationIndex = allocIndex(state)
                )



                // 2. Vytvoř 3D přímku z těchto dvou projekcí
                val line3D = combineProjectionsToLine3D(
                    pudorys = projPudorys,
                    narys = selectedNarys,
                    name = commonName,
                    sup = commonSup, state
                )


                /* přiřazení parentů */
                selectedNarys.parent = line3D
                projPudorys.parent = line3D
                selectedNarys.parentId = line3D.id
                projPudorys.parentId = line3D.id

                /* ulož jen to, co tam není */
                if (!state.lines3DPudorys.any { it.id == projPudorys.id })
                    state.lines3DPudorys.add(projPudorys)

                if (!state.lines3D.any { it.id == line3D.id })
                    state.lines3D.add(line3D)

                /* UI housekeeping */
                state.inputName = ""
                state.isNameConfirmed = true
                state.rename.lineBeingRenamed3D = line3D
                state.rename.lineBeingRenamedPudorys = projPudorys
                state.rename.lineBeingRenamedNarys = selectedNarys

                state.deferSelectionUntil = System.currentTimeMillis() + 100

                state.drawobjects = Mongeobjects.NONE
                resetStavu(state)


                println("✅ 3D přímka vytvořena: ${line3D.name}")
                commitSnapshot(state)
            }
        }
    }
}
fun NarysFinalizeLineDirAuto(
    state: MongeState,
    snappedPointLogical: Offset?,
    cursor: Offset
) {
    if (state.projectionPhase != "narys_dir_finalize_auto") return

    if (state.storedLineNarysForParallel == null &&
        state.pendingLineNarysCompletion.isNotEmpty()
    ) {
        state.storedLineNarysForParallel = state.pendingLineNarysCompletion.firstOrNull()
    }

    when (state.constructionModifier) {
        ConstructionModifier.PARALLEL -> {

            /* ------ 1️⃣  Vyber vzor v půdorysu (beze změny) ------------ */
            if (state.selectedLineForParallelPudorys == null &&
                state.selectedSegmentForParallelPudorys == null
            ) {
                val rememberedLine    = state.selectedLinesPudorys.firstOrNull()
                val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()
                when {
                    rememberedLine != null -> {
                        state.selectedLineForParallelPudorys = rememberedLine
                        println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro rovnoběžnou.")
                    }
                    rememberedSegment != null -> {
                        state.selectedSegmentForParallelPudorys = rememberedSegment
                        println("🟦 Úsečka vybrána jako vzor pro rovnoběžnou.")
                    }
                    else -> {
                        setProjectionPhase("projection_line_start_pudorys", state)
                        println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                        return
                    }
                }
                setProjectionPhase("projection_line_start_pudorys", state)
            }

            /* ------ 2️⃣  Kurzor a směr (beze změny) -------------------- */
            val logical = getLogicalCursor(
                snappedPointLogical,
                cursor,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )
            val logicalX = logical.x
            val logicalY = logical.y
            state.pendingXpudorys = logicalX
            state.pendingY        = logicalY

            val dir = when {
                state.selectedLineForParallelPudorys != null ->
                    state.selectedLineForParallelPudorys!!.direction
                state.selectedSegmentForParallelPudorys != null -> {
                    val seg = state.selectedSegmentForParallelPudorys!!
                    Offset(seg.end.x - seg.start.x, seg.end.y - seg.start.y)
                }
                else -> {
                    showLineCompletionError(state, "Interní chyba – chybí vzorová přímka nebo úsečka."); return
                }
            }

            if (dir.getDistance() < 1e-6f) {
                showLineCompletionError(state, "Směr má nulovou délku – krok ignorován."); return
            }
            if (abs(dir.x) < 1e-6f) {
                showLineCompletionError(state, LINE_COMPLETION_PERPENDICULAR_TO_X12_MESSAGE)
                clearParallelSelections(state); return
            }

            /* ------ 3️⃣  NÁRYSNÁ přímka – musí být 1× vybraná ---------- */
            val selectedNarys = state.storedLineNarysForParallel
                ?: run {
                    showLineCompletionError(state, "Neuložená nárysná přímka – nejprve ji označ v nárysu."); return
                }


            /* ------ 4️⃣  Vytvoř P-projekci a 3-D přímku ---------------- */
            val style      = state.currentLineStyleSettings
            val commonName = (selectedNarys.name ?: "").removeSuffix("₂")
            val commonSup = (selectedNarys.superscript?:"")

            val projPudorys = Line3DProjectionPudorys(
                point = Point3DPudorys(logicalX, logicalY, name = commonName),
                direction = dir,
                localName = commonName,
                localSuperscript = commonSup,
                localColor = style.color,
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth,
                creationIndex = allocIndex(state)
            )

            val line3D = combineProjectionsToLine3D(
                pudorys = projPudorys,
                narys   = selectedNarys,
                name    = commonName,
                sup = commonSup,state
            )

            /* ------ 5️⃣  Ulož do stavů + housekeeping ------------------ */

            state.lines3DPudorys.add(projPudorys)
            state.lines3D.add(line3D)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.inputName = ""
            state.isNameConfirmed = false
            state.rename.lineBeingRenamed3D      = line3D
            state.rename.lineBeingRenamedPudorys = projPudorys
            state.pendingLinePudorys.value= projPudorys
            state.pendingLine3D.value     = line3D

            projPudorys.parent = line3D
            selectedNarys.parent = line3D
            projPudorys.parentId = line3D.id
            selectedNarys.parentId = line3D.id

            println("✅ Přidána 3D přímka: ${line3D.name}")


            /* ------ 6️⃣  Reset výběrů ---------------------------------- */
            state.drawobjects = Mongeobjects.NONE
            clearParallelSelections(state)
            resetStavu(state)
            resetAfterAssociated(state)
            commitSnapshot(state)
        }

        ConstructionModifier.ORTHOGONAL -> {
            /* 1️⃣ vzor v půdorysu – stejná logika */
            if (state.selectedLineForParallelPudorys == null &&
                state.selectedSegmentForParallelPudorys == null
            ) {
                val rememberedLine    = state.selectedLinesPudorys.firstOrNull()
                val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()
                when {
                    rememberedLine != null -> state.selectedLineForParallelPudorys = rememberedLine
                    rememberedSegment != null -> state.selectedSegmentForParallelPudorys = rememberedSegment
                    else -> {
                        setProjectionPhase("projection_line_start_pudorys", state)
                        println("⚠️ Vyber v půdorysu přímku/úsečku, ke které má být kolmá.")
                        return
                    }
                }
                setProjectionPhase("projection_line_start_pudorys", state)
            }

            /* 2️⃣ kurzor */
            val logical = getLogicalCursor(
                snappedPointLogical,
                cursor,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )
            val logicalX = logical.x
            val logicalY = logical.y
            state.pendingXpudorys = logicalX
            state.pendingY        = logicalY

            /* 3️⃣ SMĚR ⟂ – otočení o 90° */
            val baseDir = when {
                state.selectedLineForParallelPudorys != null ->
                    state.selectedLineForParallelPudorys!!.direction
                else -> {
                    val seg = state.selectedSegmentForParallelPudorys!!
                    Offset(seg.end.x - seg.start.x, seg.end.y - seg.start.y)
                }
            }

            if (baseDir.getDistance() < 1e-6f) {
                showLineCompletionError(state, "Směr má nulovou délku – krok ignorován."); return
            }

            // kolmý vektor (−y, x)
            val dir = Offset(-baseDir.y, baseDir.x)

            if (abs(dir.x) < 1e-6f) {
                showLineCompletionError(state, LINE_COMPLETION_PERPENDICULAR_TO_X12_MESSAGE)
                clearParallelSelections(state); return
            }

            /* 4️⃣ uložená nárysná přímka */
            val selectedNarys = state.storedLineNarysForParallel
                ?: run { showLineCompletionError(state, "Neuložená nárysná přímka – nejprve ji označ v nárysu."); return }

            /* 5️⃣ vytvoř projekci + 3-D přímku (stejné jako v PARALLEL) */
            val style      = state.currentLineStyleSettings
            val commonName = (selectedNarys.name ?: "").removeSuffix("₂")
            val commonSup = (selectedNarys.superscript?:"")

            val projPudorys = Line3DProjectionPudorys(
                point = Point3DPudorys(logicalX, logicalY, name = commonName),
                direction = dir,
                localName = commonName,
                localSuperscript = commonSup,
                localColor = style.color,
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
            )

            val line3D = combineProjectionsToLine3D(
                pudorys = projPudorys,
                narys   = selectedNarys,
                name    = commonName,
                sup = commonSup,state
            )

            /* 6️⃣ housekeeping + reset (stejné) */

            state.lines3DPudorys.add(projPudorys)
            state.lines3D.add(line3D)

            state.inputName = ""
            state.isNameConfirmed = false
            state.rename.lineBeingRenamed3D      = line3D
            state.rename.lineBeingRenamedPudorys = projPudorys
            state.pendingLinePudorys.value= projPudorys
            state.pendingLine3D.value     = line3D

            projPudorys.parent = line3D
            selectedNarys.parent = line3D
            projPudorys.parentId = line3D.id
            selectedNarys.parentId = line3D.id
            println("✅ Přidána KOLMÁ 3D přímka: ${line3D.name}")

            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.drawobjects = Mongeobjects.NONE
            clearParallelSelections(state)
            resetAfterAssociated(state)
            resetStavu(state)
            commitSnapshot(state)
        }
        else -> return
    }
}

/* reset helper */
private fun clearParallelSelections(state: MongeState) {
    state.selectedLineForParallelPudorys   = null
    state.selectedSegmentForParallelPudorys= null
    state.selectedLinesPudorys.clear()
    state.selectedSegmentsPudorys.clear()
    state.selectedLinesNarys.clear()
    state.storedLineNarysForParallel = null
    state.storedLinePudorysForParallel = null

}


fun PudorysFinalizeLineAuto(state: MongeState,snappedPointLogical: Offset?,cursor: Offset) {
    if (state.projectionPhase == "line_finalize_pudorys_auto") {
        if (state.pendingXnarys != null && state.pendingDirection != null && state.pendingZ != null) {
            val logical =
                getLogicalCursor(
                    snappedPointLogical,
                    cursor,
                    state.canvasOffset,
                    state.scale,
                    state.canvasWidth,
                    state.canvasHeight,
                    state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                    state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
                )
            val logicalZ = -logical.y

            val dxNarys = logical.x - state.pendingXnarys!!
            val dyNarys = logicalZ - state.pendingZ!!

            if (abs(dxNarys) < LINE_COMPLETION_EPS && abs(dyNarys) < LINE_COMPLETION_EPS) {
                showLineCompletionError(state, "Neplatný směr – nulová délka.")
                return
            }
            if (abs(dxNarys) < LINE_COMPLETION_EPS) {
                showLineCompletionError(state, LINE_COMPLETION_PERPENDICULAR_TO_X12_MESSAGE)
               setProjectionPhase("projection_line_narys_dir",state)
                return

            } else {
                val selectedPudorys =
                    state.pendingLinePudorysCompletion.singleOrNull()
                        ?: run {
                            showLineCompletionError(state, "Není vybrána právě jedna přímka v půdorysu.")
                            return
                        }
                Offset(dxNarys, dyNarys)
                val commonName = (selectedPudorys.name ?: "").removeSuffix("₁")
                val commonSup = (selectedPudorys.superscript?:"")
                val style = state.currentLineStyleSettings
                // 1. Vytvoř originální projekce podle uživatele
                val projNarys = Line3DProjectionNarys(
                    point = Point3DNarys(state.pendingXnarys!!, state.pendingZ!!, name = commonName),
                    direction = Offset(dxNarys, dyNarys),
                    localName = commonName,
                    localSuperscript = commonSup,
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
                )



                // 2. Vytvoř 3D přímku z těchto dvou projekcí
                val line3D = combineProjectionsToLine3D(
                    pudorys = selectedPudorys,
                    narys = projNarys,
                    name = commonName,
                    sup = commonSup,state
                )


                selectedPudorys.parent = line3D
                projNarys.parent = line3D
                selectedPudorys.parentId = line3D.id
                projNarys.parentId = line3D.id

                if (!state.lines3DNarys.any { it.id == projNarys.id })
                    state.lines3DNarys.add(projNarys)

                if (!state.lines3D.any { it.id == line3D.id })
                    state.lines3D.add(line3D)



                state.inputName = ""
                state.isNameConfirmed = true
                state.rename.lineBeingRenamed3D = line3D
                state.rename.lineBeingRenamedPudorys = selectedPudorys
                state.rename.lineBeingRenamedNarys = projNarys
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.drawobjects = Mongeobjects.NONE

                resetStavu(state)
                resetAfterAssociated(state)


                println("✅ 3D přímka vytvořena: ${line3D.name}")
                commitSnapshot(state)
            }
        }
    }
}
fun PudorysFinalizeLineDirAuto(
    state: MongeState,
    snappedPointLogical: Offset?,
    cursor: Offset
) {
    if (state.projectionPhase != "pudorys_dir_finalize_auto") return

    if (state.storedLinePudorysForParallel == null &&
        state.pendingLinePudorysCompletion.isNotEmpty()
    ) {
        state.storedLinePudorysForParallel =
            state.pendingLinePudorysCompletion.firstOrNull()
    }

    when (state.constructionModifier) {
        ConstructionModifier.PARALLEL -> {

            if (state.selectedLineForParallelNarys == null &&
                state.selectedSegmentForParallelNarys == null
            ) {
                val rememberedLine    = state.selectedLinesNarys.firstOrNull()
                val rememberedSegment = state.selectedSegmentsNarys.firstOrNull()
                when {
                    rememberedLine != null -> {
                        state.selectedLineForParallelNarys = rememberedLine
                        println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro rovnoběžnou.")
                    }
                    rememberedSegment != null -> {
                        state.selectedSegmentForParallelNarys = rememberedSegment
                        println("🟦 Úsečka vybrána jako vzor pro rovnoběžnou.")
                    }
                    else -> {
                        setProjectionPhase("projection_line_start_narys", state)
                        println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                        return
                    }
                }
                setProjectionPhase("projection_line_start_narys", state)
            }

            val logical = getLogicalCursor(
                snappedPointLogical,
                cursor,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )
            val logicalX = logical.x
            val logicalZ = -logical.y
            state.pendingXnarys = logicalX
            state.pendingZ      = logicalZ

            val dir = when {
                state.selectedLineForParallelNarys!= null ->
                    state.selectedLineForParallelNarys!!.direction
                state.selectedSegmentForParallelNarys != null -> {
                    val seg = state.selectedSegmentForParallelNarys!!
                    Offset(seg.end.x - seg.start.x, seg.end.z - seg.start.z)
                }
                else -> {
                    showLineCompletionError(state, "Interní chyba – chybí vzorová přímka nebo úsečka."); return
                }
            }

            if (dir.getDistance() < 1e-6f) {
                showLineCompletionError(state, "Směr má nulovou délku – krok ignorován."); return
            }
            if (abs(dir.x) < 1e-6f) {
                showLineCompletionError(state, LINE_COMPLETION_PERPENDICULAR_TO_X12_MESSAGE)
                clearParallelSelections(state); return
            }


            val selectedPudorys = state.storedLinePudorysForParallel
                ?: run {
                    showLineCompletionError(state, "Neuložená půdorysná přímka – nejprve ji označ v půdorysu."); return
                }


            /* ------ 4️⃣  Vytvoř P-projekci a 3-D přímku ---------------- */
            val style      = state.currentLineStyleSettings
            val commonName = (selectedPudorys.name ?: "").removeSuffix("₁")
            val commonSup = (selectedPudorys.superscript?:"")

            val projNarys = Line3DProjectionNarys(
                point = Point3DNarys(logicalX, logicalZ, name = commonName),
                direction = dir,
                localName = commonName,
                localSuperscript = commonSup,
                localColor = style.color,
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
            )

            val line3D = combineProjectionsToLine3D(
                pudorys = selectedPudorys,
                narys   = projNarys,
                name    = commonName,
                sup = commonSup,state
            )

            /* ------ 5️⃣  Ulož do stavů + housekeeping ------------------ */

            state.lines3DNarys.add(projNarys)
            state.lines3D.add(line3D)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.inputName = ""
            state.isNameConfirmed = false
            state.rename.lineBeingRenamed3D      = line3D
            state.rename.lineBeingRenamedNarys = projNarys
            state.pendingLineNarys.value= projNarys
            state.pendingLine3D.value     = line3D

            selectedPudorys.parent = line3D
            projNarys.parent = line3D
            selectedPudorys.parentId = line3D.id
            projNarys.parentId = line3D.id
            println("✅ Přidána 3D přímka: ${line3D.name}")

            /* ------ 6️⃣  Reset výběrů ---------------------------------- */
            state.drawobjects = Mongeobjects.NONE
            clearParallelSelections(state)
            resetStavu(state)
            resetAfterAssociated(state)
            commitSnapshot(state)
        }

        ConstructionModifier.ORTHOGONAL -> {
            /* 1️⃣ vzor v půdorysu – stejná logika */
            if (state.selectedLineForParallelNarys == null &&
                state.selectedSegmentForParallelNarys == null
            ) {
                val rememberedLine    = state.selectedLinesNarys.firstOrNull()
                val rememberedSegment = state.selectedSegmentsNarys.firstOrNull()
                when {
                    rememberedLine != null -> state.selectedLineForParallelNarys = rememberedLine
                    rememberedSegment != null -> state.selectedSegmentForParallelNarys = rememberedSegment
                    else -> {
                        setProjectionPhase("projection_line_start_narys", state)
                        println("⚠️ Vyber v půdorysu přímku/úsečku, ke které má být kolmá.")
                        return
                    }
                }
                setProjectionPhase("projection_line_start_narys", state)
            }

            /* 2️⃣ kurzor */
            val logical = getLogicalCursor(
                snappedPointLogical,
                cursor,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )
            val logicalX = logical.x
            val logicalZ = -logical.y
            state.pendingXpudorys = logicalX
            state.pendingZ        = logicalZ

            /* 3️⃣ SMĚR ⟂ – otočení o 90° */
            val baseDir = when {
                state.selectedLineForParallelNarys != null ->
                    state.selectedLineForParallelNarys!!.direction
                else -> {
                    val seg = state.selectedSegmentForParallelNarys!!
                    Offset(seg.end.x - seg.start.x, seg.end.z - seg.start.z)
                }
            }

            if (baseDir.getDistance() < 1e-6f) {
                showLineCompletionError(state, "Směr má nulovou délku – krok ignorován."); return
            }

            // kolmý vektor (−y, x)
            val dir = Offset(-baseDir.y, baseDir.x)

            if (abs(dir.x) < 1e-6f) {
                showLineCompletionError(state, LINE_COMPLETION_PERPENDICULAR_TO_X12_MESSAGE)
                clearParallelSelections(state); return
            }

            /* 4️⃣ uložená nárysná přímka */
            val selectedPudorys= state.storedLinePudorysForParallel
                ?: run { showLineCompletionError(state, "Neuložená půdorysná přímka – nejprve ji označ v půdorysu."); return }

            /* 5️⃣ vytvoř projekci + 3-D přímku (stejné jako v PARALLEL) */
            val style      = state.currentLineStyleSettings
            val commonName = (selectedPudorys.name ?: "").removeSuffix("₁")
            val commonSup = (selectedPudorys.superscript?:"")

            val projNarys = Line3DProjectionNarys(
                point = Point3DNarys(logicalX, logicalZ, name = commonName),
                direction = dir,
                localName = commonName,
                localSuperscript = commonSup,
                localColor = style.color,
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
            )

            val line3D = combineProjectionsToLine3D(
                pudorys = selectedPudorys,
                narys   = projNarys,
                name    = commonName,
                sup = commonSup,state
            )

            /* 6️⃣ housekeeping + reset (stejné) */

            state.lines3DNarys.add(projNarys)
            state.lines3D.add(line3D)

            state.inputName = ""
            state.isNameConfirmed = false
            state.rename.lineBeingRenamed3D      = line3D
            state.rename.lineBeingRenamedNarys = projNarys
            state.pendingLineNarys.value= projNarys
            state.pendingLine3D.value     = line3D

            projNarys.parent = line3D
            selectedPudorys.parent = line3D
            selectedPudorys.parentId = line3D.id
            projNarys.parentId = line3D.id
            println("✅ Přidána KOLMÁ 3D přímka: ${line3D.name}")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.drawobjects = Mongeobjects.NONE
            clearParallelSelections(state)

            resetStavu(state)
            resetAfterAssociated(state)
            commitSnapshot(state)
        }
        else -> return
    }
}

fun handleSpecialCaseLineCompletionNarys(state: MongeState,snappedPointLogical: Offset?,cursor: Offset)
{


    val logical =
        getLogicalCursor(
            snappedPointLogical,
            cursor,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
    logical.x
    val logicalZ = -logical.y
    val pendingZ = logicalZ

    val projPudorys = state.pendingLinePudorysCompletion.firstOrNull()?: return
    // Název a horní index přebíráme z původního průmětu, dialog jen jako fallback
    val inheritedName = (projPudorys.name ?: "").removeSuffix("₁")
    val commonName = inheritedName.ifBlank { state.inputName.ifBlank { "?" } }
    val commonSup = projPudorys.superscript
    val style = state.currentLineStyleSettings


    // 2. Přímo vytvoř 3D přímku (nepoužíváme combineProjectionsToLine3D)
    val start3D = Point3D(state.pendingXpudorys!!, state.pendingY!!, pendingZ, name = commonName)

    val direction3D = Offset3D(
        x = 0f,
        y = state.pendingDirection!!.y,
        z = 0f // Přímka je rovnoběžná s půdorysnou rovinou
    )

    val line3D = Line3D(
        start = start3D,
        direction = direction3D,
        name = commonName,
        superscript = commonSup,
        color = style.color,
        strokeWidth = style.strokeWidth,
        lineStyle = style.style, creationIndex = allocIndex(state)

    )
    val projNarys = Point3DNarys(
        state.pendingXpudorys!!,
        pendingZ,
        name = commonName,
        parentLine = line3D,
        pendingParentLineId = line3D.id
    )

    projNarys.parentLine = line3D

    state.pointsNarys.add(projNarys)
    state.lines3D.add(line3D)

    projPudorys.parentId = line3D.id

    projPudorys.parent = line3D
    state.pendingLinePudorys.value = projPudorys
    state.pendingLine3D.value = line3D
    state.deferSelectionUntil = System.currentTimeMillis() + 100

    if (inheritedName.isNotBlank()) {
        // Průmět už měl název – nepokládáme znovu dotaz, jen dokončíme
        state.reset3DLineNaming()
        commitSnapshot(state)
        repeatCons(state)
        updateConstructionInfo(state)
        resetStavu(state)
        resetAfterAssociated(state)
    } else {
        state.rename.lineBeingRenamed3D = line3D
        state.rename.lineBeingRenamedPudorys = projPudorys
        state.rename.pointNarysBeingRenamed = projNarys
        setProjectionPhase("naming_3d_line", state)
    }
}
fun handleSpecialCaseLineCompletionPudorys(state: MongeState, snappedPointLogical: Offset?, cursor: Offset){
    val logical =
        getLogicalCursor(
            snappedPointLogical,
            cursor,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
    val pendingY = logical.y

    val projNarys = state.pendingLineNarysCompletion.firstOrNull()?:return
    // Název a horní index přebíráme z původního průmětu, dialog jen jako fallback
    val inheritedName = (projNarys.name ?: "").removeSuffix("₂")
    val commonName = inheritedName.ifBlank { state.inputName.ifBlank { "" }.removeSuffix("₁").removeSuffix("₂") }
    val commonSup = projNarys.superscript
    val style = state.currentLineStyleSettings


    val start3D = Point3D(state.pendingXnarys!!, pendingY, state.pendingZ!!, name = commonName)

    val direction3D = Offset3D(
        x = 0f,
        y = 0f,
        z = state.pendingDirectionNarys!!.y
    )

    val line3D = Line3D(
        start = start3D,
        direction = direction3D,
        name = commonName,
        superscript = commonSup,
        color = style.color,
        strokeWidth = style.strokeWidth,
        lineStyle = style.style, creationIndex = allocIndex(state)
    )
    val projPudorys = Point3DPudorys(
        state.pendingXnarys!!,
        pendingY,
        name = commonName,
        parentLine = line3D,
        pendingParentLineId = line3D.id
    )

    projPudorys.parentLine = line3D
    state.pointsPudorys.add(projPudorys)
    state.lines3D.add(line3D)
    projNarys.parentId = line3D.id

    projNarys.parent = line3D
    state.pendingLineNarys.value = projNarys
    state.pendingLine3D.value = line3D

    if (inheritedName.isNotBlank()) {
        // Průmět už měl název – nepokládáme znovu dotaz, jen dokončíme
        state.reset3DLineNaming()
        commitSnapshot(state)
        repeatCons(state)
        updateConstructionInfo(state)
        resetStavu(state)
        resetAfterAssociated(state)
    } else {
        state.rename.lineBeingRenamed3D = line3D
        state.rename.lineBeingRenamedNarys = projNarys
        state.rename.pointPudorysBeingRenamed = projPudorys
        setProjectionPhase("naming_3d_line", state)
    }
}
