package monge.input.lines.directionHandlers.lines

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.HelpLineNarys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.PlaneTraceNarys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import utils.combineProjectionsToLine3D
import kotlin.math.abs

//funkce na kolmost v nárysu
fun handleOrthogonalLineConstructionNarys(logical: Offset, state: MongeState) {
    if (needsLineDirectionPatternNarys(state)) {
        tryPickLineDirectionNarys(state, orthogonal = true)
        return
    }

    when (state.drawobjects) {
        Mongeobjects.LINES -> {
            if (state.projekcnityp == ProjectionType.SINGLE) {
                if (state.selectedLineForParallelNarys == null && state.selectedSegmentForParallelNarys == null) {
                    val rememberedLine = state.selectedLinesNarys.firstOrNull()
                    val rememberedSegment = state.selectedSegmentsNarys.firstOrNull()
                    when {
                        rememberedLine != null -> {
                            state.selectedLineForParallelNarys = rememberedLine
                            println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro kolmou.")
                        }
                        rememberedSegment != null -> {
                            state.selectedSegmentForParallelNarys = rememberedSegment
                            println("🟦 Úsečka vybrána jako vzor pro kolmou.")
                        }
                        else -> {
                            println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                            return
                        }
                    }
                }

                val basePoint = Point3DNarys(logical.x, -logical.y, name = "?")

                val originalDirection = when {
                    state.selectedLineForParallelNarys != null -> {
                        state.selectedLineForParallelNarys!!.direction
                    }
                    state.selectedSegmentForParallelNarys != null -> {
                        val seg = state.selectedSegmentForParallelNarys!!
                        Offset(
                            x = seg.end.x - seg.start.x,
                            y = seg.end.z - seg.start.z
                        )
                    }
                    else -> {
                        println("❌ Interní chyba – chybí vzorová přímka nebo úsečka.")
                        return
                    }
                }

                val direction = Offset(-originalDirection.y, originalDirection.x)

                val newLine = Line3DProjectionNarys(basePoint, direction, creationIndex = allocIndex(state))

                state.rename.lineBeingRenamedNarys = newLine
                state.inputName = ""
                state.isNameConfirmed = false
                setProjectionPhase("single_narys_line", state)

                val sourceName = state.selectedLineForParallelNarys?.name ?: state.selectedSegmentForParallelNarys?.name ?: "?"
                println("🟢 Vytvořena přímka kolmá na $sourceName, skrze bod $basePoint")

                // ⬇ Reset stavu
                state.selectedLineForParallelNarys = null
                state.selectedSegmentForParallelNarys = null
                state.selectedLinesNarys.clear()
                state.selectedSegmentsNarys.clear()
                state.constructionModifier = ConstructionModifier.NONE
            }


            if (state.projekcnityp== ProjectionType.ASSOCIATED) {
                if (
                    state.mongeMode == DrawingModeMonge.NARYS &&
                    state.projectionPhase == "orthogonal_line_point_selection_narys_pudorys_start" &&
                    state.pendingY != null &&
                    state.pendingDirection != null
                ) {
                    if (state.selectedLineForParallelNarys == null && state.selectedSegmentForParallelNarys == null) {
                        val rememberedLine = state.selectedLinesNarys.firstOrNull()
                        val rememberedSegment = state.selectedSegmentsNarys.firstOrNull()

                        when {
                            rememberedLine != null -> {
                                state.selectedLineForParallelNarys = rememberedLine
                                println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro kolmou.")
                            }

                            rememberedSegment != null -> {
                                state.selectedSegmentForParallelNarys = rememberedSegment
                                println("🟦 Úsečka vybraná jako vzor pro kolmou.")
                            }

                            else -> {
                                setProjectionPhase("projection_line_start_narys", state)
                                println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                                return
                            }
                        }
                        setProjectionPhase("projection_line_start_narys", state)
                    }

                        val logicalX = logical.x
                        val logicalZ = -logical.y
                        state.pendingXnarys = logicalX
                        state.pendingZ = logicalZ

                        val originalDir = when {
                            state.selectedLineForParallelNarys != null -> {
                                state.selectedLineForParallelNarys!!.direction
                            }

                            state.selectedSegmentForParallelNarys != null -> {
                                val seg = state.selectedSegmentForParallelNarys!!
                                Offset(
                                    x = seg.end.x - seg.start.x,
                                    y = seg.end.z - seg.start.z
                                )
                            }

                            else -> {
                                println("❌ Interní chyba – chybí vzor pro kolmici.")
                                return
                            }
                        }

                        val dir = Offset(-originalDir.y, originalDir.x)

                        if (dir.getDistance() < 1e-6f) {
                            println("❌ Směr má nulovou délku – krok ignorován")
                            return
                        }

                        if (abs(dir.x) < 1e-6f) {
                            println("❌ Neplatné zadání – kolmice rovnoběžná s x₁₂")
                            state.selectedLinesNarys.clear()
                            state.selectedSegmentsNarys.clear()
                            state.selectedLineForParallelNarys = null
                            state.selectedSegmentForParallelNarys = null
                            return
                        } else {
                            val commonName = state.inputName.ifBlank { "" }
                            val sup = state.inputSuperscript.ifBlank { "" }
                            val style = state.currentLineStyleSettings

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

                                projPudorys.parent = line3D
                                projNarys.parent = line3D

                                state.pendingLinePudorys.value = projPudorys
                                state.pendingLineNarys.value = projNarys
                                state.pendingLine3D.value = line3D

                                state.inputName = ""
                                state.isNameConfirmed = false
                                setProjectionPhase("naming_3d_line", state)

                                state.rename.lineBeingRenamed3D = line3D
                                state.rename.lineBeingRenamedPudorys = projPudorys
                                state.rename.lineBeingRenamedNarys = projNarys

                                println("✅ Přidána kolmice jako 3D přímka: ${line3D.name}")
                                commitSnapshot(state)
                            } else {
                                println("❌ Přímku se nepodařilo sestavit.")
                            }

                            // Reset výběru
                            state.selectedLineForParallelNarys = null
                            state.selectedSegmentForParallelNarys = null
                            state.selectedLinesNarys.clear()
                            state.selectedSegmentsNarys.clear()
                            state.deferSelectionUntil = System.currentTimeMillis() + 100
                        }
                }

                if (
                    state.mongeMode == DrawingModeMonge.NARYS &&
                    state.projectionPhase == "orthogonal_line_point_selection_narys_start"
                ) {
                    if (state.selectedLineForParallelNarys == null && state.selectedSegmentForParallelNarys == null) {
                        val rememberedLine = state.selectedLinesNarys.firstOrNull()
                        val rememberedSegment = state.selectedSegmentsNarys.firstOrNull()

                        when {
                            rememberedLine != null -> {
                                state.selectedLineForParallelNarys = rememberedLine
                                println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro kolmou.")
                            }

                            rememberedSegment != null -> {
                                state.selectedSegmentForParallelNarys = rememberedSegment
                                println("🟦 Úsečka vybraná jako vzor pro kolmou.")
                            }

                            else -> {
                                setProjectionPhase("narys_start", state)
                                println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                                return
                            }
                        }

                        setProjectionPhase("narys_start", state)
                    }

                    state.pendingXnarys = logical.x
                    state.pendingZ = -logical.y

                    val originalDir = when {
                        state.selectedLineForParallelNarys != null -> {
                            state.selectedLineForParallelNarys!!.direction
                        }

                        state.selectedSegmentForParallelNarys != null -> {
                            val seg = state.selectedSegmentForParallelNarys!!
                            Offset(
                                x = seg.end.x - seg.start.x,
                                y = seg.end.z - seg.start.z
                            )
                        }

                        else -> {
                            println("❌ Interní chyba – chybí vzor pro kolmici.")
                            return
                        }
                    }

                    val dir = Offset(-originalDir.y, originalDir.x)

                    if (dir.getDistance() < 1e-6f) {
                        println("❌ Směr má nulovou délku – krok ignorován")
                    } else if (abs(dir.x) < 1e-6f) {
                        println("📐 Detekován kolmý směr – otevření speciálního dialogu")
                        state.pendingDirectionNarys = dir.copy(x = 0f)
                        state.showSpecialLineDialog.value = true
                        setProjectionPhase("special_line_type_selection_start_narys", state)
                    } else {
                        state.pendingDirectionNarys = dir
                        setProjectionPhase("projection_line_start_pudorys", state)
                        state.mongeMode = DrawingModeMonge.PUDORYS
                        state.showSpecialLineDialog.value = false
                        println("🟡 Zadaný směr nárysu: x=${dir.x}, y=${dir.y}")
                    }

                    // Reset
                    state.selectedLineForParallelNarys = null
                    state.selectedSegmentForParallelNarys = null
                    state.selectedLinesNarys.clear()
                    state.selectedSegmentsNarys.clear()
                    return
                }
            }
            if (state.projekcnityp == ProjectionType.AUXILIARY) {
                if (state.selectedLineForParallelNarys == null && state.selectedSegmentForParallelNarys == null) {
                    val rememberedLine = state.selectedLinesNarys.firstOrNull()
                    val rememberedSegment = state.selectedSegmentsNarys.firstOrNull()

                    when {
                        rememberedLine != null -> {
                            state.selectedLineForParallelNarys = rememberedLine
                            println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro kolmou.")
                        }
                        rememberedSegment != null -> {
                            state.selectedSegmentForParallelNarys = rememberedSegment
                            println("🟦 Úsečka vybraná jako vzor pro kolmou.")
                        }
                        else -> {
                            println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                            return
                        }
                    }
                }

                val basePoint = Point3DNarys(logical.x, -logical.y, name = "?")

                val originalDir = when {
                    state.selectedLineForParallelNarys != null -> {
                        state.selectedLineForParallelNarys!!.direction
                    }
                    state.selectedSegmentForParallelNarys != null -> {
                        val seg = state.selectedSegmentForParallelNarys!!
                        Offset(
                            x = seg.end.x - seg.start.x,
                            y = seg.end.z - seg.start.z
                        )
                    }
                    else -> {
                        println("❌ Interní chyba – chybí vzor pro kolmici.")
                        return
                    }
                }

                val direction = Offset(-originalDir.y, originalDir.x)

                val style = state.currentHelpLineStyleSettings
                val newLine = HelpLineNarys(
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
                state.helpLineNarys.add(newLine)
                state.deferSelectionUntil = System.currentTimeMillis() + 100

                val sourceName = state.selectedLineForParallelNarys?.name ?: state.selectedSegmentForParallelNarys?.name ?: "?"
                println("🟢 Vytvořena pomocná přímka kolmá na $sourceName, skrze bod $basePoint")

                state.selectedLineForParallelNarys = null
                state.selectedSegmentForParallelNarys = null
                state.selectedLinesNarys.clear()
                state.selectedSegmentsNarys.clear()
                commitSnapshot(state)
            }

        }
        Mongeobjects.PLANE -> {
            if (state.projekcnityp == ProjectionType.ASSOCIATED) {
                if (state.selectedLineForParallelPlaneNarys == null && state.selectedSegmentForParallelNarys == null) {
                    val rememberedLine = state.selectedLinesNarys.firstOrNull()
                    val rememberedSegment = state.selectedSegmentsNarys.firstOrNull()

                    when {
                        rememberedLine != null -> {
                            state.selectedLineForParallelPlaneNarys = rememberedLine
                            println("🟦 Přímka '${rememberedLine.name}' vybraná pro konstrukci roviny.")
                        }
                        rememberedSegment != null -> {
                            state.selectedSegmentForParallelNarys = rememberedSegment
                            println("🟦 Úsečka vybraná pro konstrukci roviny.")
                        }
                        else -> {
                            println("⚠️ Neoznačena žádná přímka ani úsečka – nejprve vyber jednu kliknutím.")
                            return
                        }
                    }
                }

                val clickedPoint = Point3DNarys(logical.x, -logical.y, name = "?")

                val originalDir = when {
                    state.selectedLineForParallelPlaneNarys != null -> {
                        state.selectedLineForParallelPlaneNarys!!.direction
                    }
                    state.selectedSegmentForParallelNarys != null -> {
                        val seg = state.selectedSegmentForParallelNarys!!
                        Offset(
                            x = seg.end.x - seg.start.x,
                            y = seg.end.z - seg.start.z
                        )
                    }
                    else -> {
                        println("❌ Interní chyba – chybí vzor pro rovinu.")
                        return
                    }
                }

                val direction = Offset(-originalDir.y, originalDir.x)

                state.tracePlaneNarys = PlaneTraceNarys(clickedPoint, direction, creationIndex = allocIndex(state))
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("🟢 Vytvořena kolmá půdorysná stopa roviny: ${state.tracePlaneNarys}")

                setProjectionPhase("plane_trace_pudorys_direction", state)
                state.mongeMode = DrawingModeMonge.PUDORYS

                val base = state.tracePlaneNarys
                if (base != null && abs(base.direction.y) > 0.0001f) {
                    val p = base.point
                    val d = base.direction
                    val t = -p.z / d.y
                    val x = p.x + t * d.x
                    val pointOnX12 = Point3DPudorys(x = x, y = 0f, name = "X₁₂")
                    state.xOnX12Pudorys = pointOnX12
                    println("📍 Vypočten průsečík s x₁₂: $pointOnX12")
                } else {
                    println("❌ Nelze spočítat průsečík s x₁₂ – směr je rovnoběžný.")
                    state.xOnX12Pudorys = null
                }

                println("🟡 Přepnuto do nárysu – očekávám druhou stopu roviny.")

                // 🔄 reset
                state.selectedLineForParallelPlaneNarys = null
                state.selectedSegmentForParallelNarys = null
                state.selectedLinesNarys.clear()
                state.selectedSegmentsNarys.clear()
                state.constructionModifier = ConstructionModifier.NONE
            }
            if (state.projekcnityp == ProjectionType.SINGLE){
                if (state.selectedLineForParallelPlaneNarys == null && state.selectedSegmentForParallelNarys == null) {
                    val rememberedLine = state.selectedLinesNarys.firstOrNull()
                    val rememberedSegment = state.selectedSegmentsNarys.firstOrNull()

                    when {
                        rememberedLine != null -> {
                            state.selectedLineForParallelPlaneNarys = rememberedLine
                            println("🟦 Přímka '${rememberedLine.name}' vybraná pro konstrukci roviny.")
                        }

                        rememberedSegment != null -> {
                            state.selectedSegmentForParallelNarys = rememberedSegment
                            println("🟦 Úsečka vybraná pro konstrukci roviny.")
                        }

                        else -> {
                            println("⚠️ Neoznačena žádná přímka ani úsečka – nejprve vyber jednu kliknutím.")
                            return
                        }
                    }
                }

                // ✅ druhý klik – zvolený bod
                val clickedPoint = Point3DNarys(logical.x, -logical.y, name = "?")

                val originalDir = when {
                    state.selectedLineForParallelPlaneNarys != null -> {
                        state.selectedLineForParallelPlaneNarys!!.direction
                    }

                    state.selectedSegmentForParallelNarys != null -> {
                        val seg = state.selectedSegmentForParallelNarys!!
                        Offset(
                            x = seg.end.x - seg.start.x,
                            y = seg.end.z - seg.start.z
                        )
                    }

                    else -> {
                        println("❌ Interní chyba – chybí vzor pro konstrukci roviny.")
                        return
                    }
                }
                val direction = Offset(-originalDir.y, originalDir.x)
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                if (direction.getDistance() != 0f) {

                    state.tracePlaneNarys = PlaneTraceNarys(
                        clickedPoint,
                        direction,
                        localColor = state.currentLineStyleSettings.color,
                        localName = "",
                        localLineStyle = state.currentLineStyleSettings.style,
                        localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state)
                    )
                    println("🟢 Zadána stopa roviny (nárys): ${state.tracePlaneNarys}")
                    state.narysTracePendingForNaming = state.tracePlaneNarys
                    state.showPlaneNamingDialog = true
                    state.lineTracesNarys.add(state.tracePlaneNarys!!)
                    commitSnapshot(state)
                    resetStavu(state)


                }
                println("🟢 Vytvořena nárysová stopa roviny rovnoběžná se zadanou přímkou/úsečkou.")
            }
        }
        else -> {
            println("⚠️ Konstrukce kolmice není pro tento režim podporována.")

        }
    }
}
