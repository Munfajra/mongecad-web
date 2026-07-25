package monge.input.lines

import utils.System
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.*
import model.classes.HelpLineNarys
import model.classes.HelpLinePudorys
import model.classes.Line3D
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.combineprojections.NarysFinalizeLineAuto
import monge.input.combineprojections.NarysFinalizeLineDirAuto
import monge.input.combineprojections.PudorysFinalizeLineAuto
import monge.input.combineprojections.PudorysFinalizeLineDirAuto
import monge.input.combineprojections.handleSpecialCaseLineCompletionNarys
import monge.input.combineprojections.handleSpecialCaseLineCompletionPudorys
import monge.input.lines.directionHandlers.lines.handleOrthogonalLineConstructionNarys
import monge.input.lines.directionHandlers.lines.handleOrthogonalLineConstructionPudorys
import monge.input.lines.directionHandlers.lines.handleParallelLineConstructionNarys
import monge.input.lines.directionHandlers.lines.handleParallelLineConstructionPudorys
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.allocIndex
import utils.combineProjectionsToLine3D
import utils.getLogicalCursor
import kotlin.math.abs

//single NÁRYS
fun handleSingleLineNarys(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val logicalX = logical.x
    val logicalZ = -logical.y

    if (state.constructionModifier == ConstructionModifier.PARALLEL) {
        handleParallelLineConstructionNarys(logical, state)
        return
    }
    if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
        handleOrthogonalLineConstructionNarys(logical, state)
        return
    }

    if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.LINES && state.constructionModifier == ConstructionModifier.NONE && state.projekcnityp== ProjectionType.SINGLE) {
        val existing = state.pointsNarys.find {
            abs(it.x - logicalX) < 0.01f && abs(it.z - logicalZ) < 0.01f
        }

        val newPoint = existing ?: Point3DNarys(
            x = logicalX,
            z = logicalZ,
            name = state.inputName.ifBlank { "?" },
            parent = null
        )

        if (state.lineStartPoint3DNarys == null) {
            state.lineStartPoint3DNarys = newPoint
            println("Začátek přímky (nárys): $newPoint")
            state.consInfo.value = "Umístěte druhý bod přímky"
        } else {

            val start = state.lineStartPoint3DNarys!!
            val direction = Offset(newPoint.x - start.x, newPoint.z - start.z)

            if (direction.getDistance() != 0f) {
                val tempLine = Line3DProjectionNarys(start, direction, creationIndex = allocIndex(state))

                state.inputName = ""
                state.isNameConfirmed = false
                setProjectionPhase("single_narys_line", state)
                state.rename.lineBeingRenamedNarys = tempLine

                println("Přímka přidána z $start se směrem $direction")
            }

            state.lineStartPoint3DNarys = null
        }
    }
    if (state.projekcnityp == ProjectionType.AUXILIARY && state.constructionModifier== ConstructionModifier.NONE)
    {
        val logicalX = logical.x
        val logicalZ = -logical.y

        val existing = state.pointsNarys.find {
            abs(it.x - logicalX) < 0.01f && abs(it.z - logicalZ) < 0.01f
        }

        val newPoint = existing ?: Point3DNarys(
            x = logicalX,
            z = logicalZ,
            name = "?",
            parent = null
        )

        if (state.lineStartPoint3DNarys == null) {
            state.lineStartPoint3DNarys = newPoint
            println("Začátek pomocné přímky (nárys): $newPoint")
            state.consInfo.value = "Umístěte druhý bod přímky"
        } else {
            val start = state.lineStartPoint3DNarys!!
            val direction = Offset(newPoint.x - start.x, newPoint.z - start.z)
            val style = state.currentHelpLineStyleSettings
            if (direction.getDistance() != 0f) {
                val tempLine = HelpLineNarys(
                    point = start,
                    direction = direction,
                    name = "",
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth,
                    parentAny = null, creationIndex = allocIndex(state)
                )


                state.rename.helplineBeingRenamedNarys = tempLine
                state.inputName = ""
                state.isNameConfirmed = false
                setProjectionPhase("rename_helpline_narys" , state)
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("Přidána: pomocná přímka bodu $start, směr $direction")
            }

            state.lineStartPoint3DNarys = null
        }
    }
}

//single PŮDORYS
fun handleSinglePudorysLine(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    if (state.constructionModifier == ConstructionModifier.PARALLEL) {
        handleParallelLineConstructionPudorys(logical, state)
        return
    }
    if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
        handleOrthogonalLineConstructionPudorys(logical,state)
        return
    }
    if (state.projekcnityp == ProjectionType.SINGLE&&state.constructionModifier == ConstructionModifier.NONE) {
            val logicalX = logical.x
            val logicalY = logical.y

            val existing = state.pointsPudorys.find {
                abs(it.x - logicalX) < 0.01f && abs(it.y - logicalY) < 0.01f
            }

            val newPoint = existing ?: Point3DPudorys(
                x = logicalX,
                y = logicalY,
                name = "?₁",
                parent = null
            )

            if (state.lineStartPoint3DPudorys == null) {
                state.lineStartPoint3DPudorys = newPoint
                println("Začátek přímky (půdorys): $newPoint")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
            } else {
                val start = state.lineStartPoint3DPudorys!!
                val direction = Offset(newPoint.x - start.x, newPoint.y - start.y)

                if (direction.getDistance() != 0f) {
                    val tempLine = Line3DProjectionPudorys(start, direction, creationIndex = allocIndex(state))

                    state.inputName = ""
                    state.isNameConfirmed = false
                    setProjectionPhase("single_pudorys_line", state)
                    state.rename.lineBeingRenamedPudorys = tempLine
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println("Přímka připravena na pojmenování: z bodu $start, směr $direction")
                }

                state.lineStartPoint3DPudorys = null
            }
        }
    if (state.projekcnityp == ProjectionType.AUXILIARY&&state.constructionModifier == ConstructionModifier.NONE)
        {
            val logicalX = logical.x
            val logicalY = logical.y

            val existing = state.pointsPudorys.find {
                abs(it.x - logicalX) < 0.01f && abs(it.y - logicalY) < 0.01f
            }

            val newPoint = existing ?: Point3DPudorys(
                x = logicalX,
                y = logicalY,
                name = "?",
                parent = null
            )

            if (state.lineStartPoint3DPudorys == null) {
                state.lineStartPoint3DPudorys = newPoint
                println("Začátek přímky (půdorys): $newPoint")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.consInfo.value = "Umístěte druhý bod přímky"
            } else {
                val start = state.lineStartPoint3DPudorys!!
                val direction = Offset(newPoint.x - start.x, newPoint.y - start.y)
                val style = state.currentHelpLineStyleSettings
                if (direction.getDistance() != 0f) {
                    val tempLine = HelpLinePudorys(
                        point = start,
                        direction = direction,
                        name = "",
                        localColor = style.color,
                        localLineStyle = style.style,
                        localStrokeWidth = style.strokeWidth,
                        parentAny = null, creationIndex = allocIndex(state)
                    )
                    state.rename.helplineBeingRenamedPudorys = tempLine
                    state.isNameConfirmed = false
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    setProjectionPhase("rename_helpline_pudorys",state)
                    println("Přidána: pomocná přímka bodu $start, směr $direction")

                }

                state.lineStartPoint3DPudorys = null
            }
        }
}

//sdružené průměty (start PŮDORYS)
fun handleCombinedLineConstructionPUDORYS(
    cursor: Offset,
    snappedPointLogical: Offset?,
    state: MongeState
){
    when {
        state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projectionPhase == "pudorys_start"&&
                state.projekcnityp== ProjectionType.ASSOCIATED
            -> {
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
            if (state.constructionModifier == ConstructionModifier.PARALLEL) {
                setProjectionPhase("parallel_line_point_selection_pudorys_start", state)
                handleParallelLineConstructionPudorys(logical,state)
                return
            }
            if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
                setProjectionPhase("orthogonal_line_point_selection_pudorys_start", state)
              handleOrthogonalLineConstructionPudorys(logical, state)
                return
            }else{
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
                println("Zvolený bod: $logical")
                state.pendingXpudorys = logical.x
                state.pendingY = logical.y
                setProjectionPhase("projection_line_dir_pudorys", state)
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("🟡 Zadaný bod půdorysu: (${state.pendingXpudorys}, ${state.pendingY})")}}
        state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp == ProjectionType.ASSOCIATED &&
                state.projectionPhase in listOf(
                    "parallel_line_point_selection_pudorys_start",
                    "parallel_line_point_selection_pudorys_narys_start"
                ) -> {
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
            handleParallelLineConstructionPudorys(logical, state)
            return
        }
        state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp == ProjectionType.ASSOCIATED &&
                state.projectionPhase in listOf(
                    "orthogonal_line_point_selection_pudorys_start",
                    "orthogonal_line_point_selection_pudorys_narys_start"
                ) -> {
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
            handleOrthogonalLineConstructionPudorys(logical, state)
            return
        }
        state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp == ProjectionType.ASSOCIATED &&
                state.projectionPhase == "projection_line_dir_pudorys" &&
                state.pendingXpudorys != null && state.pendingY != null ->
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
            val dir = Offset(logical.x - state.pendingXpudorys!!, logical.y - state.pendingY!!)

            if (dir.getDistance() < 1e-6f) {
                println("❌ Směr má nulovou délku – krok ignorován")
            } else {
                if (abs(dir.x) < 1e-6f) {
                    // Kolmý směr na x₁₂
                    println("📐 Detekován kolmý směr – otevření speciálního dialogu")
                    state.pendingDirection = dir.copy(x = 0f) // přesně s nulovým x
                    setProjectionPhase("special_line_type_selection", state)
                    state.showSpecialLineDialog.value = true
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                } else {
                    // Standardní případ
                    state.pendingDirection = dir
                    setProjectionPhase("projection_line_start_narys", state)
                    state.mongeMode = DrawingModeMonge.NARYS
                    state.showSpecialLineDialog.value = false
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println("🟡 Zadaný směr půdorysu: x=${dir.x}, y=${dir.y}")
                }
            }

            state.pendingDirection = dir
        }
        state.mongeMode == DrawingModeMonge.NARYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projectionPhase == "projection_line_start_narys" &&
                state.projekcnityp== ProjectionType.ASSOCIATED &&
                state.pendingY != null && state.pendingDirection != null -> {

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
            val logicalX = logical.x
            val logicalZ = -logical.y
            if (!state.reusingExistingProjection && state.constructionModifier == ConstructionModifier.PARALLEL){
                setProjectionPhase("parallel_line_point_selection_narys_pudorys_start", state)
                handleParallelLineConstructionNarys(logical, state)
                return
            }
            if ( !state.reusingExistingProjection && state.constructionModifier == ConstructionModifier.ORTHOGONAL){
                setProjectionPhase("orthogonal_line_point_selection_narys_pudorys_start", state)
                handleOrthogonalLineConstructionNarys(logical, state)
                return
            }
            if( state.reusingExistingProjection && (state.constructionModifier== ConstructionModifier.ORTHOGONAL || state.constructionModifier== ConstructionModifier.PARALLEL)){

                setProjectionPhase("pudorys_dir_finalize_auto", state)
               PudorysFinalizeLineDirAuto(state, snappedPointLogical,cursor)
            } else {
                state.pendingXnarys = logicalX  // ⬅️ NEPŘEBÍRÁME z půdorysu, ale zadáváme zcela nově
                state.pendingZ = logicalZ

                println("📌 Zadaný bod v nárysu: x=${state.pendingXnarys}, z=$logicalZ")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                setProjectionPhase("projection_line_narys_dir", state)
            }
                }

        state.mongeMode == DrawingModeMonge.NARYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp== ProjectionType.ASSOCIATED &&
                state.projectionPhase == "projection_line_narys_dir" &&
                state.pendingXnarys != null && state.pendingY != null && state.pendingZ != null &&
                state.pendingDirection != null -> {
            if (state.reusingExistingProjection){ setProjectionPhase("line_finalize_pudorys_auto", state)
                PudorysFinalizeLineAuto(state,snappedPointLogical,cursor)}
            else {
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
            val dzNarys = logicalZ - state.pendingZ!!

            if (abs(dxNarys) < 1e-6f && abs(dzNarys) < 1e-6f) {
                println("❌ Neplatný směr – nulová délka")

            }
            if(abs(dxNarys)<1e-6f){
                println("❌ Neplatná přímka")
             return
            }else {
                val dirNarys = Offset(dxNarys, dzNarys)
                val commonName = state.inputName.ifBlank { "" }
                val sup = state.inputSuperscript.ifBlank { "" }
                val style = state.currentLineStyleSettings
                // 1. Vytvoř originální projekce podle uživatele
                val projPudorys = Line3DProjectionPudorys(
                    point = Point3DPudorys(state.pendingXpudorys!!, state.pendingY!!, name = commonName),
                    direction = state.pendingDirection!!,
                    localName = commonName,
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
                )

                val projNarys = Line3DProjectionNarys(
                    point = Point3DNarys(state.pendingXnarys!!, state.pendingZ!!, name = commonName),
                    direction = dirNarys,
                    localName = commonName,
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
                )

                // 2. Vytvoř 3D přímku z těchto dvou projekcí
                val line3D = combineProjectionsToLine3D(
                    pudorys = projPudorys,
                    narys = projNarys,
                    name = commonName,
                    sup = sup,state
                )

                if (line3D != null) {


                    // 4. Ulož do seznamů

                    state.lines3DPudorys.add(projPudorys)
                    state.lines3DNarys.add(projNarys)
                    state.lines3D.add(line3D)


                    state.inputName = ""
                    state.isNameConfirmed = false
                    setProjectionPhase("naming_3d_line", state)
                    state.rename.lineBeingRenamed3D = line3D
                    state.rename.lineBeingRenamedPudorys = projPudorys
                    state.rename.lineBeingRenamedNarys = projNarys
                    projPudorys.parent = line3D
                    projNarys.parent = line3D
                    projPudorys.parentId = line3D.id
                    projNarys.parentId = line3D.id
                    state.pendingLinePudorys.value = projPudorys
                    state.pendingLineNarys.value = projNarys
                    state.pendingLine3D.value = line3D
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println("✅ Přidána 3D přímka: ${line3D.name}")
                } else {
                    println("❌ Přímku se nepodařilo sestavit.")
                }
            }
            }
        }
        state.mongeMode == DrawingModeMonge.NARYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp== ProjectionType.ASSOCIATED &&
                state.projectionPhase == "special_case_point_in_narys" &&
                state.specialLineCase.value == SpecialLineCase.ParallelToPudorys &&
                state.pendingXpudorys != null && state.pendingY != null &&
                state.pendingDirection != null -> {
            if (state.reusingExistingProjection) {
                handleSpecialCaseLineCompletionNarys(state,snappedPointLogical,cursor)

            } else {

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

                val commonName = state.inputName.ifBlank { "?" }
                val style = state.currentLineStyleSettings
                // 1. Vytvoř obě projekce jako obvykle
                val projPudorys = Line3DProjectionPudorys(
                    point = Point3DPudorys(state.pendingXpudorys!!, state.pendingY!!, name = ""),
                    direction = state.pendingDirection!!,
                    localName = commonName,
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
                )


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
                    color = style.color,
                    strokeWidth = style.strokeWidth,
                    lineStyle = style.style, creationIndex = allocIndex(state)

                )
                val projNarys = Point3DNarys(
                    state.pendingXpudorys!!,
                    pendingZ,
                    name = "".removeSuffix("₁").removeSuffix("₂"),
                    parentLine = line3D,
                    pendingParentLineId = line3D.id
                )

                projNarys.parentLine = line3D
                // 3. Ulož do seznamů + propojení
                state.lines3DPudorys.add(projPudorys)
                state.pointsNarys.add(projNarys)
                state.lines3D.add(line3D)

                projPudorys.parentId = line3D.id


                state.rename.lineBeingRenamed3D = line3D
                state.rename.lineBeingRenamedPudorys = projPudorys
                state.rename.pointNarysBeingRenamed = projNarys
                setProjectionPhase("naming_3d_line", state)
                projPudorys.parent = line3D
                state.pendingLinePudorys.value = projPudorys
                state.pendingLine3D.value = line3D
                state.deferSelectionUntil = System.currentTimeMillis() + 100
            }
        }
    }
}
//sdružené průměty (start NÁRYS)
fun handleCombinedLineConstrucionNARYS(
    cursor: Offset,
    snappedPointLogical: Offset?,
    state: MongeState
){
    // Pokud v tomto kliknutí právě proběhl výběr vzoru směru (obsloužil ho dřívější
    // dispatcher PŮDORYS), nesmíme přímku ve stejném kliknutí rovnou i umístit.
    if (state.lineDirectionPatternJustPicked) return

    when {
        state.mongeMode == DrawingModeMonge.NARYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp== ProjectionType.ASSOCIATED &&
                state.projectionPhase == "narys_start"
            -> {
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
            if (state.constructionModifier == ConstructionModifier.PARALLEL&& !state.reusingExistingProjection) {
                setProjectionPhase("parallel_line_point_selection_narys_start", state)
                handleParallelLineConstructionNarys(logical, state)
                return
            }
            if (state.constructionModifier == ConstructionModifier.ORTHOGONAL && !state.reusingExistingProjection) {
                setProjectionPhase("orthogonal_line_point_selection_narys_start", state)
                handleOrthogonalLineConstructionNarys(logical, state)
                return

            } else {
                state.pendingXnarys = logical.x
                state.pendingZ = -logical.y
                setProjectionPhase("projection_line_dir_narys_start", state)
            }
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟡 Zadaný bod nárysu: (${state.pendingXpudorys}, ${state.pendingZ})")}
        state.mongeMode == DrawingModeMonge.NARYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp == ProjectionType.ASSOCIATED &&
                state.projectionPhase in listOf(
                    "parallel_line_point_selection_narys_start",
                    "parallel_line_point_selection_narys_pudorys_start"
                ) -> {
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
            handleParallelLineConstructionNarys(logical, state)
            return
        }
        state.mongeMode == DrawingModeMonge.NARYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp == ProjectionType.ASSOCIATED &&
                state.projectionPhase in listOf(
                    "orthogonal_line_point_selection_narys_start",
                    "orthogonal_line_point_selection_narys_pudorys_start"
                ) -> {
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
            handleOrthogonalLineConstructionNarys(logical, state)
            return
        }
        state.mongeMode == DrawingModeMonge.NARYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp== ProjectionType.ASSOCIATED &&
                state.projectionPhase == "projection_line_dir_narys_start" &&
                state.pendingXnarys != null && state.pendingZ != null ->
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
            val dir = Offset(logical.x - state.pendingXnarys!!, -logical.y - state.pendingZ!!)

            if (dir.getDistance() < 1e-6f) {
                println("❌ Směr má nulovou délku – krok ignorován")
            } else {
                if (abs(dir.x) < 1e-6f) {
                    // Kolmý směr na x₁₂
                    println("📐 Detekován kolmý směr – otevření speciálního dialogu")
                    state.pendingDirectionNarys = dir.copy(x = 0f) // přesně s nulovým x
                    setProjectionPhase("special_line_type_selection_start_narys", state)
                    state.showSpecialLineDialog.value = true
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                } else {
                    // Standardní případ
                    state.pendingDirectionNarys = dir
                    setProjectionPhase("projection_line_start_pudorys", state)
                    state.mongeMode = DrawingModeMonge.PUDORYS
                    state.showSpecialLineDialog.value = false
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println("🟡 Zadaný směr nárysu: x=${dir.x}, z=${dir.y}")
                }
            }
            state.pendingDirectionNarys = dir
        }
        state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp== ProjectionType.ASSOCIATED &&
                state.projectionPhase == "projection_line_start_pudorys" &&
                state.pendingZ != null && state.pendingDirectionNarys != null -> {
            if( state.reusingExistingProjection && (state.constructionModifier== ConstructionModifier.ORTHOGONAL || state.constructionModifier== ConstructionModifier.PARALLEL)){

                setProjectionPhase("narys_dir_finalize_auto", state)
                NarysFinalizeLineDirAuto(state, snappedPointLogical,cursor)
            } else {
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
            if (state.constructionModifier == ConstructionModifier.PARALLEL) {
                setProjectionPhase("parallel_line_point_selection_pudorys_narys_start", state)
                handleParallelLineConstructionPudorys(logical,state)
                return
            }
            if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
                setProjectionPhase("orthogonal_line_point_selection_pudorys_narys_start", state)
                handleOrthogonalLineConstructionPudorys(logical,state)
                return
            }
            else {


                state.pendingXpudorys =
                    logicalX  // ⬅️ NEPŘEBÍRÁME z půdorysu, ale zadáváme zcela nově
                state.pendingY = logicalY
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("📌 Zadaný bod v půdorysu: x=${state.pendingXpudorys}, y=$logicalY")

                setProjectionPhase("projection_line_start_pudorys_dir", state)
            }
        }}
        state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp== ProjectionType.ASSOCIATED &&
                state.projectionPhase == "projection_line_start_pudorys_dir" &&
                state.constructionModifier == ConstructionModifier.NONE &&
                state.pendingXpudorys != null && state.pendingY != null && state.pendingZ != null &&
                state.pendingDirectionNarys != null -> {
            if (state.reusingExistingProjection){ setProjectionPhase("line_finalize_narys_auto", state)
                NarysFinalizeLineAuto(state,snappedPointLogical,cursor)}
            else {
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

            if (abs(dxPudorys) < 1e-6f && abs(dyPudorys) < 1e-6f) {
                println("❌ Neplatný směr – nulová délka")
            }
            if(abs(dxPudorys)<1e-6f){
                println("❌ Neplatná přímka")
                return
            }else {

                val dirPudorys = Offset(dxPudorys, dyPudorys)
                val commonName = state.inputName.ifBlank { "" }
                val sup = state.inputSuperscript.ifBlank { "" }
                val style = state.currentLineStyleSettings
                // 1. Vytvoř originální projekce podle uživatele
                val projNarys = Line3DProjectionNarys(
                    point = Point3DNarys(state.pendingXnarys!!, state.pendingZ!!, name = commonName),
                    direction = state.pendingDirectionNarys!!,
                    localName = commonName,
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
                )

                val projPudorys = Line3DProjectionPudorys(
                    point = Point3DPudorys(state.pendingXpudorys!!, state.pendingY!!, name = commonName),
                    direction = dirPudorys,
                    localName = commonName,
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
                )

                // 2. Vytvoř 3D přímku z těchto dvou projekcí
                val line3D = combineProjectionsToLine3D(
                    pudorys = projPudorys,
                    narys = projNarys,
                    name = commonName,
                    sup = sup,state
                )

                if (line3D != null) {

                    // 4. Ulož do seznamů
                    state.lines3DPudorys.add(projPudorys)
                    state.lines3DNarys.add(projNarys)
                    state.lines3D.add(line3D)
                    projPudorys.parentId = line3D.id
                    projNarys.parentId = line3D.id

                    state.inputName = ""
                    state.isNameConfirmed = false
                    setProjectionPhase("naming_3d_line", state)
                    state.rename.lineBeingRenamed3D = line3D
                    state.rename.lineBeingRenamedPudorys = projPudorys
                    state.rename.lineBeingRenamedNarys = projNarys
                    projPudorys.parent = line3D
                    projNarys.parent = line3D
                    state.pendingLinePudorys.value = projPudorys
                    state.pendingLineNarys.value = projNarys
                    state.pendingLine3D.value = line3D
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println("✅ Přidána 3D přímka: ${line3D.name}")

                } else {
                    println("❌ Přímku se nepodařilo sestavit.")
                }
            }}
        }
        state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.drawobjects == Mongeobjects.LINES &&
                state.projekcnityp== ProjectionType.ASSOCIATED &&
                state.projectionPhase == "special_case_point_in_pudorys" &&
                state.specialLineCase.value == SpecialLineCase.ParallelToNarys &&
                state.pendingXnarys != null && state.pendingZ != null &&
                state.pendingDirectionNarys != null -> {
                    if (state.reusingExistingProjection){
                        handleSpecialCaseLineCompletionPudorys(state,snappedPointLogical,cursor)
                    }
            else {
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

                        val commonName = state.inputName.ifBlank { "" }.removeSuffix("₁").removeSuffix("₂")
                        val style = state.currentLineStyleSettings
                        // 1. Vytvoř obě projekce jako obvykle
                        val projNarys = Line3DProjectionNarys(
                            point = Point3DNarys(state.pendingXnarys!!, state.pendingZ!!, name = commonName),
                            direction = state.pendingDirectionNarys!!,
                            localName = commonName,
                            localColor = style.color,
                            localLineStyle = style.style,
                            localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
                        )


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
                        // 3. Ulož do seznamů + propojení
                        state.lines3DNarys.add(projNarys)
                        state.pointsPudorys.add(projPudorys)
                        state.lines3D.add(line3D)
                        projNarys.parentId = line3D.id
                        state.rename.lineBeingRenamed3D = line3D
                        state.rename.lineBeingRenamedNarys = projNarys
                        state.rename.pointPudorysBeingRenamed = projPudorys

                        setProjectionPhase("naming_3d_line", state)
                        projNarys.parent = line3D
                        state.pendingLineNarys.value = projNarys
                        state.pendingLine3D.value = line3D
                    }
        }
    }
}
private fun add3DLineFromTwo3DPoints(
    state: MongeState,
    a3: Point3D,
    b3: Point3D,
    color: Color = state.currentLineStyleSettings.color,
    width: Float = state.currentLineStyleSettings.strokeWidth
) {
    // degenerate
    val dx = b3.x - a3.x
    val dy = b3.y - a3.y
    val dz = b3.z - a3.z
    if (kotlin.math.abs(dx) < 1e-6f && kotlin.math.abs(dy) < 1e-6f && kotlin.math.abs(dz) < 1e-6f) return

    // 1) 3D line
    val line3D = Line3D(
        name = "",                 // nebo default "p"
        start = Point3D(a3.x, a3.y, a3.z, ""),
        direction = Offset3D(dx, dy, dz),
        color = color,
        strokeWidth = width, creationIndex = allocIndex(state)
    )

    // 2) Projections (Monge)
    val projPud = Line3DProjectionPudorys(
        parentId = line3D.id,
        point = Point3DPudorys(a3.x, a3.y),
        direction = Offset(dx, dy),
        localName = "",
        localColor = color,
        localStrokeWidth = width, creationIndex = allocIndex(state)
    )

    val projNar = Line3DProjectionNarys(
        parentId = line3D.id,
        // parametricky: bod + směr v nárysu (x,z)
        point = Point3DNarys(a3.x, a3.z),
        direction = Offset(dx, dz),
        localName = "",
        localColor = color,
        localStrokeWidth = width, creationIndex = allocIndex(state)
    )

    // 3) Commit into state
    state.lines3D.add(line3D)
    state.lines3DPudorys.add(projPud)
    state.lines3DNarys.add(projNar)
    projPud.parentId = line3D.id
    projNar.parentId = line3D.id
    state.inputName = ""
    state.isNameConfirmed = false
    setProjectionPhase("naming_3d_line", state)
    state.rename.lineBeingRenamed3D = line3D
    state.rename.lineBeingRenamedPudorys = projPud
    state.rename.lineBeingRenamedNarys = projNar
    projPud.parent = line3D
    projNar.parent = line3D
    state.pendingLinePudorys.value = projPud
    state.pendingLineNarys.value = projNar
    state.pendingLine3D.value = line3D
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    println("✅ Přidána 3D přímka: ${line3D.name}")

}
fun handleKotoLineThroughTwoParentedPointsClick(state: MongeState, clicked: Point3DPudorys) {
    val aParent = clicked.parent ?: return  // musí mít 3D parenta
    state.consInfo.value = "Vyberte druhý bod přímky"

    val aId = state.kotoLinePickAId
    val bId = state.kotoLinePickBId

    if (aId == null) {
        state.kotoLinePickAId = clicked.id
        state.kotoLinePickBId = null
        return
    }

    // druhý bod
    if (clicked.id == aId) return

    // najdi první bod z id
    val first = state.pointsPudorys.find { it.id == aId } ?: run {
        state.kotoLinePickAId = null
        state.kotoLinePickBId = null
        return
    }

    val bParent = clicked.parent ?: return
    val a3 = first.parent ?: return



    add3DLineFromTwo3DPoints(
        state = state,
        a3 = a3,
        b3 = bParent
    )

    // cleanup
    state.kotoLinePickAId = null
    state.kotoLinePickBId = null
}
