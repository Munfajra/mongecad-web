package monge.input.lines.directionHandlers.lines

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.HelpLinePudorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.PlaneTracePudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.combineProjectionsToLine3D
import kotlin.math.abs


//funkce na rovnoběžnost v půdorysu
fun handleParallelLineConstructionPudorys(logical: Offset, state: MongeState){
    if (needsLineDirectionPatternPudorys(state)) {
        tryPickLineDirectionPudorys(state, orthogonal = false)
        return
    }

    if (!state.reusingExistingProjection){
        when (state.drawobjects) {
            Mongeobjects.LINES -> {
                if (state.projekcnityp == ProjectionType.SINGLE) {
                    // 1️⃣ Pokud ještě nebyl vybrán směr, vyber ho a rovnou pokračuj
                    if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
                        val rememberedLine = state.selectedLinesPudorys.firstOrNull()
                        val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()

                        when {
                            rememberedLine != null -> {
                                state.selectedLineForParallelPudorys = rememberedLine
                                println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro rovnoběžnou.")
                                state.consInfo.value = "Umístěte přímku"
                                // 🔁 Okamžitě pokračuj (REKURZE!)
                                handleParallelLineConstructionPudorys(logical, state)
                                return
                            }

                            rememberedSegment != null -> {
                                state.selectedSegmentForParallelPudorys = rememberedSegment
                                println("🟦 Úsečka vybrána jako vzor pro rovnoběžnou.")
                                state.consInfo.value = "Umístěte přímku"

                                // 🔁 Okamžitě pokračuj (REKURZE!)
                                handleParallelLineConstructionPudorys(logical, state)
                                return
                            }

                            else -> {
                                println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                                return
                            }
                        }
                    }

                    // 2️⃣ Směr už máme → pokračuj v konstrukci
                    val basePoint = Point3DPudorys(logical.x, logical.y, name = "?₁")

                    val direction = when {
                        state.selectedLineForParallelPudorys != null -> {
                            state.selectedLineForParallelPudorys!!.direction
                        }

                        state.selectedSegmentForParallelPudorys != null -> {
                            val seg = state.selectedSegmentForParallelPudorys!!
                            Offset(
                                x = seg.end.x - seg.start.x,
                                y = seg.end.y - seg.start.y
                            )
                        }

                        else -> {
                            println("❌ Interní chyba – chybí vzor pro rovnoběžnou.")
                            return
                        }
                    }

                    val newLine = Line3DProjectionPudorys(basePoint, direction, creationIndex = allocIndex(state))

                    state.rename.lineBeingRenamedPudorys = newLine
                    state.inputName = ""
                    state.isNameConfirmed = false
                    setProjectionPhase("single_pudorys_line", state)
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println(
                        "🟢 Vytvořena přímka rovnoběžná s ${
                            state.selectedLineForParallelPudorys?.name
                                ?: state.selectedSegmentForParallelPudorys?.name
                        }, skrze bod $basePoint"
                    )

                    state.selectedLineForParallelPudorys = null
                    state.selectedSegmentForParallelPudorys = null
                    state.selectedLinesPudorys.clear()
                    state.selectedSegmentsPudorys.clear()
                    updateConstructionInfo(state)
                }


            if (state.projekcnityp == ProjectionType.ASSOCIATED) {
                if (
                    state.drawobjects == Mongeobjects.LINES &&
                    state.mongeMode == DrawingModeMonge.PUDORYS &&
                    state.projectionPhase == "parallel_line_point_selection_pudorys_start"
                ) {
                    if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
                        val rememberedLine = state.selectedLinesPudorys.firstOrNull()
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
                                setProjectionPhase("pudorys_start", state)
                                println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                                return
                            }
                        }

                        setProjectionPhase("pudorys_start", state)
                    }

                    // Druhý krok – kliknutí na bod
                    state.pendingXpudorys = logical.x
                    state.pendingY = logical.y

                    val dir = when {
                        state.selectedLineForParallelPudorys != null -> {
                            state.selectedLineForParallelPudorys!!.direction
                        }
                        state.selectedSegmentForParallelPudorys != null -> {
                            val seg = state.selectedSegmentForParallelPudorys!!
                            Offset(
                                x = seg.end.x - seg.start.x,
                                y = seg.end.y - seg.start.y
                            )
                        }
                        else -> {
                            println("❌ Interní chyba – chybí vzor pro rovnoběžnou.")
                            return
                        }
                    }

                    if (dir.getDistance() < 1e-6f) {
                        println("❌ Směr má nulovou délku – krok ignorován")
                    } else if (abs(dir.x) < 1e-6f) {
                        println("📐 Detekován kolmý směr – otevření speciálního dialogu")
                        state.pendingDirection = dir.copy(x = 0f)
                        setProjectionPhase("special_line_type_selection", state)
                        state.showSpecialLineDialog.value = true
                    } else {
                        state.pendingDirection = dir
                        setProjectionPhase("projection_line_start_narys", state)
                        state.mongeMode = DrawingModeMonge.NARYS
                        state.showSpecialLineDialog.value = false
                        println("🟡 Zadaný směr půdorysu: x=${dir.x}, y=${dir.y}")
                    }

                    // Resetujeme výběr
                    state.selectedLineForParallelPudorys = null
                    state.selectedSegmentForParallelPudorys = null
                    state.selectedLinesPudorys.clear()
                    state.selectedSegmentsPudorys.clear()
                    return
                }

                if (
                    state.mongeMode == DrawingModeMonge.PUDORYS &&
                    state.projectionPhase == "parallel_line_point_selection_pudorys_narys_start" &&
                    state.pendingZ != null &&
                    state.pendingDirectionNarys != null
                ) {

                    if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
                        val rememberedLine = state.selectedLinesPudorys.firstOrNull()
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

                    val logicalX = logical.x
                    val logicalY = logical.y
                    state.pendingXpudorys = logicalX
                    state.pendingY = logicalY

                    val dir = when {
                        state.selectedLineForParallelPudorys != null -> {
                            state.selectedLineForParallelPudorys!!.direction
                        }
                        state.selectedSegmentForParallelPudorys != null -> {
                            val seg = state.selectedSegmentForParallelPudorys!!
                            Offset(
                                x = seg.end.x - seg.start.x,
                                y = seg.end.y - seg.start.y
                            )
                        }
                        else -> {
                            println("❌ Interní chyba – chybí vzorová přímka nebo úsečka.")
                            return
                        }
                    }

                    if (dir.getDistance() < 1e-6f) {
                        println("❌ Směr má nulovou délku – krok ignorován")
                        return
                    }

                    if (abs(dir.x) < 1e-6f) {
                        println("❌ Neplatné zadání – přímka kolmá na osu x₁₂")
                        state.selectedLinesPudorys.clear()
                        state.selectedSegmentsPudorys.clear()
                        state.selectedLineForParallelPudorys = null
                        state.selectedSegmentForParallelPudorys = null
                        return
                    } else {
                        val style = state.currentLineStyleSettings
                        val commonName = state.inputName.ifBlank { "" }
                        val sup = state.inputSuperscript.ifBlank { "" }
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
                            direction = dir,
                            localName = commonName,
                            localColor = style.color,
                            localLineStyle = style.style,
                            localStrokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
                        )

                        val line3D = combineProjectionsToLine3D(
                            pudorys = projPudorys,
                            narys = projNarys,
                            name = commonName,
                            sup = sup,state
                        )

                        if (line3D != null) {

                            state.lines3DPudorys.add(projPudorys)
                            state.lines3DNarys.add(projNarys)
                            state.lines3D.add(line3D)
                            state.inputName = ""
                            state.isNameConfirmed = false
                            setProjectionPhase("naming_3d_line", state)
                            state.rename.lineBeingRenamed3D = line3D
                            state.rename.lineBeingRenamedPudorys = projPudorys
                            state.rename.lineBeingRenamedNarys = projNarys
                            state.pendingLinePudorys.value = projPudorys
                            state.pendingLineNarys.value = projNarys
                            state.pendingLine3D.value = line3D
                            projPudorys.parent = line3D
                            projNarys.parent = line3D
                            commitSnapshot(state)
                            println("✅ Přidána 3D přímka: ${line3D.name}")
                            println("   start = (${line3D.start.x}, ${line3D.start.y}, ${line3D.start.z})")
                            println("   dir   = (${line3D.direction.x}, ${line3D.direction.y}, ${line3D.direction.z})")
                        } else {
                            println("❌ Přímku se nepodařilo sestavit.")
                        }

                        // Reset výběru
                        state.selectedLineForParallelPudorys = null
                        state.selectedSegmentForParallelPudorys = null
                        state.selectedLinesPudorys.clear()
                        state.selectedSegmentsPudorys.clear()
                    }
                }
            }
            if (state.projekcnityp == ProjectionType.AUXILIARY) {
                if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
                    val rememberedLine = state.selectedLinesPudorys.firstOrNull()
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
                            println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                            return
                        }
                    }
                }

                val basePoint = Point3DPudorys(logical.x, logical.y, name = "?₁")
                val direction = when {
                    state.selectedLineForParallelPudorys != null -> {
                        state.selectedLineForParallelPudorys!!.direction
                    }
                    state.selectedSegmentForParallelPudorys != null -> {
                        val seg = state.selectedSegmentForParallelPudorys!!
                        Offset(
                            x = seg.end.x - seg.start.x,
                            y = seg.end.y - seg.start.y
                        )
                    }
                    else -> {
                        println("❌ Interní chyba – chybí vzorová přímka nebo úsečka.")
                        return
                    }
                }

                val style = state.currentHelpLineStyleSettings
                val newLine = HelpLinePudorys(
                    point = basePoint,
                    direction = direction,
                    name = "",
                    localColor = style.color,
                    localLineStyle = style.style,
                    localStrokeWidth = style.strokeWidth,
                    parentAny = null, creationIndex = allocIndex(state)
                )

                state.inputName = ""
                state.isNameConfirmed = true
                state.helpLinePudorys.add(newLine)
                state.deferSelectionUntil = System.currentTimeMillis() + 100

                val sourceName = state.selectedLineForParallelPudorys?.name ?: state.selectedSegmentForParallelPudorys?.name ?: "?"
                println("🟢 Vytvořena pomocná přímka rovnoběžná s $sourceName, skrze bod $basePoint")
                commitSnapshot(state)
                // Reset
                state.selectedLineForParallelPudorys = null
                state.selectedSegmentForParallelPudorys = null
                state.selectedLinesPudorys.clear()
                state.selectedSegmentsPudorys.clear()
                state.constructionModifier = ConstructionModifier.PARALLEL
            }
        }
        Mongeobjects.PLANE -> {
            if (state.projekcnityp == ProjectionType.ASSOCIATED) {
                if (state.selectedLineForParallelPlanePudorys == null && state.selectedSegmentForParallelPudorys == null) {
                    val rememberedLine = state.selectedLinesPudorys.firstOrNull()
                    val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()

                    when {
                        rememberedLine != null -> {
                            state.selectedLineForParallelPlanePudorys = rememberedLine
                            println("🟦 Přímka '${rememberedLine.name}' vybraná pro konstrukci roviny.")
                        }
                        rememberedSegment != null -> {
                            state.selectedSegmentForParallelPudorys = rememberedSegment
                            println("🟦 Úsečka vybraná pro konstrukci roviny.")
                        }
                        else -> {
                            println("⚠️ Neoznačena žádná přímka ani úsečka – nejprve vyber jednu kliknutím.")
                            return
                        }
                    }
                }

                // ✅ druhý klik – zvolený bod
                val clickedPoint = Point3DPudorys(logical.x, logical.y, name = "?")
                val direction = when {
                    state.selectedLineForParallelPlanePudorys != null -> {
                        state.selectedLineForParallelPlanePudorys!!.direction
                    }
                    state.selectedSegmentForParallelPudorys != null -> {
                        val seg = state.selectedSegmentForParallelPudorys!!
                        Offset(
                            x = seg.end.x - seg.start.x,
                            y = seg.end.y - seg.start.y
                        )
                    }
                    else -> {
                        println("❌ Interní chyba – chybí vzorová přímka nebo úsečka.")
                        return
                    }
                }

                state.tracePlanePudorys = PlaneTracePudorys(clickedPoint, direction, creationIndex = allocIndex(state))
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("🟢 Vytvořena rovnoběžná půdorysná stopa roviny: ${state.tracePlanePudorys}")




                val base = state.tracePlanePudorys
                if (base != null && abs(base.direction.y) > 0.0001f) {
                    val p = base.point
                    val d = base.direction
                    val t = -p.y / d.y
                    val x = p.x + t * d.x
                    val pointOnX12 = Point3DNarys(x = x, z = 0f, name = "X₁₂")
                    state.xOnX12Narys = pointOnX12
                    println("📍 Vypočten průsečík s x₁₂: $pointOnX12")
                    setProjectionPhase("plane_trace_narys_direction", state)
                    state.mongeMode = DrawingModeMonge.NARYS
                } else {
                    println("❌ Nelze spočítat průsečík s x₁₂ – směr je rovnoběžný, jdeme na speciální případ")
                    setProjectionPhase("plane_trace_narys_special_direction", state)
                    state.mongeMode = DrawingModeMonge.NARYS
                    state.xOnX12Narys = null
                }

                println("🟡 Přepnuto do nárysu – očekávám druhou stopu roviny.")

                // 🔄 reset výběru
                state.selectedLineForParallelPlanePudorys = null
                state.selectedSegmentForParallelPudorys = null
                state.selectedLinesPudorys.clear()
                state.selectedSegmentsPudorys.clear()
                state.constructionModifier = ConstructionModifier.NONE
            }
            if (state.projekcnityp == ProjectionType.SINGLE){
                if (state.selectedLineForParallelPlanePudorys == null && state.selectedSegmentForParallelPudorys == null) {
                    val rememberedLine = state.selectedLinesPudorys.firstOrNull()
                    val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()

                    when {
                        rememberedLine != null -> {
                            state.selectedLineForParallelPlanePudorys = rememberedLine
                            println("🟦 Přímka '${rememberedLine.name}' vybraná pro konstrukci roviny.")
                        }
                        rememberedSegment != null -> {
                            state.selectedSegmentForParallelPudorys = rememberedSegment
                            println("🟦 Úsečka vybraná pro konstrukci roviny.")
                        }
                        else -> {
                            println("⚠️ Neoznačena žádná přímka ani úsečka – nejprve vyber jednu kliknutím.")
                            return
                        }
                    }
                }

                // ✅ druhý klik – zvolený bod
                val clickedPoint = Point3DPudorys(logical.x, logical.y, name = "?")

                val direction = when {
                    state.selectedLineForParallelPlanePudorys != null -> {
                        state.selectedLineForParallelPlanePudorys!!.direction
                    }
                    state.selectedSegmentForParallelPudorys != null -> {
                        val seg = state.selectedSegmentForParallelPudorys!!
                        Offset(
                            x = seg.end.x - seg.start.x,
                            y = seg.end.y - seg.start.y
                        )
                    }
                    else -> {
                        println("❌ Interní chyba – chybí vzorová přímka nebo úsečka.")
                        return
                    }
                }

                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("🟢 Vytvořena kolmá půdorysná stopa roviny: ${state.tracePlanePudorys}")
                if (direction.getDistance() != 0f) {

                    state.tracePlanePudorys = PlaneTracePudorys(
                        clickedPoint,
                        direction,
                        localColor = state.currentLineStyleSettings.color,
                        localName = "",
                        localLineStyle = state.currentLineStyleSettings.style,
                        localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state)
                    )

                    state.pudorysTracePendingForNaming = state.tracePlanePudorys
                    state.showPlaneNamingDialog = true
                    state.lineTracesPudorys.add(state.tracePlanePudorys!!)
                    commitSnapshot(state)
                    resetStavu(state)

                }
            }
        }

        else -> {
            println("⚠️ Konstrukce rovnoběžky není pro tento režim podporována.")
        }
    }
}}
