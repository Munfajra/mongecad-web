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
import ui.resetStavu
import utils.allocIndex
import utils.combineProjectionsToLine3D
import kotlin.math.abs

//funkce na kolmost v půdorysu
fun handleOrthogonalLineConstructionPudorys(logical: Offset, state: MongeState) {
    if (needsLineDirectionPatternPudorys(state)) {
        tryPickLineDirectionPudorys(state, orthogonal = true)
        return
    }

    when (state.drawobjects) {
    Mongeobjects.LINES -> {
        if (state.projekcnityp == ProjectionType.SINGLE) {
            if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
                val rememberedLine = state.selectedLinesPudorys.firstOrNull()
                val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()

                when {
                    rememberedLine != null -> {
                        state.selectedLineForParallelPudorys = rememberedLine
                        println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro kolmou.")
                    }
                    rememberedSegment != null -> {
                        state.selectedSegmentForParallelPudorys = rememberedSegment
                        println("🟦 Úsečka vybraná jako vzor pro kolmou.")
                    }
                    else -> {
                        println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                        return
                    }
                }
            }

            val basePoint = Point3DPudorys(logical.x, logical.y, name = "₁")

            val originalDir = when {
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

            val direction = Offset(-originalDir.y, originalDir.x)
            val newLine = Line3DProjectionPudorys(basePoint, direction, creationIndex = allocIndex(state))

            state.rename.lineBeingRenamedPudorys = newLine
            state.inputName = ""
            state.isNameConfirmed = false
            setProjectionPhase("single_pudorys_line", state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100

            val sourceName = state.selectedLineForParallelPudorys?.name ?: state.selectedSegmentForParallelPudorys?.name ?: ""
            println("🟢 Vytvořena přímka kolmá na $sourceName, skrze bod $basePoint")

            state.selectedLineForParallelPudorys = null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
            state.constructionModifier = ConstructionModifier.NONE
        }


        if (state.projekcnityp == ProjectionType.ASSOCIATED) {
            if (state.drawobjects == Mongeobjects.LINES &&
                state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.projectionPhase == "orthogonal_line_point_selection_pudorys_start"
            ) {
                if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
                    val rememberedLine = state.selectedLinesPudorys.firstOrNull()
                    val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()

                    when {
                        rememberedLine != null -> {
                            state.selectedLineForParallelPudorys = rememberedLine
                            println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro kolmou.")
                        }
                        rememberedSegment != null -> {
                            state.selectedSegmentForParallelPudorys = rememberedSegment
                            println("🟦 Úsečka vybraná jako vzor pro kolmou.")
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

                val originalDir = when {
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
                        println("❌ Interní chyba – chybí vzor pro kolmici.")
                        return
                    }
                }

                val dir = Offset(-originalDir.y, originalDir.x)

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

                // Reset výběru
                state.selectedLineForParallelPudorys = null
                state.selectedSegmentForParallelPudorys = null
                state.selectedLinesPudorys.clear()
                state.selectedSegmentsPudorys.clear()
                return
            }


            if (
                state.mongeMode == DrawingModeMonge.PUDORYS &&
                state.projectionPhase == "orthogonal_line_point_selection_pudorys_narys_start" &&
                state.pendingZ != null &&
                state.pendingDirectionNarys != null
            ) {
                if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
                    val rememberedLine = state.selectedLinesPudorys.firstOrNull()
                    val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()

                    when {
                        rememberedLine != null -> {
                            state.selectedLineForParallelPudorys = rememberedLine
                            println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro kolmou.")
                        }
                        rememberedSegment != null -> {
                            state.selectedSegmentForParallelPudorys = rememberedSegment
                            println("🟦 Úsečka vybraná jako vzor pro kolmou.")
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

                val originalDir = when {
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

                val dir = Offset(-originalDir.y, originalDir.x)

                if (dir.getDistance() < 1e-6f) {
                    println("❌ Směr má nulovou délku – krok ignorován")
                    return
                }

                if (abs(dir.x) < 1e-6f) {
                    println("❌ Neplatné zadání – kolmice rovnoběžná s x₁₂")
                    state.selectedLinesPudorys.clear()
                    state.selectedSegmentsPudorys.clear()
                    state.selectedLineForParallelPudorys = null
                    state.selectedSegmentForParallelPudorys = null
                    return
                }

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

                    projPudorys.parent = line3D
                    projNarys.parent = line3D

                    state.pendingLinePudorys.value = projPudorys
                    state.pendingLineNarys.value = projNarys
                    state.pendingLine3D.value = line3D
                    commitSnapshot(state)
                    state.inputName = ""
                    state.isNameConfirmed = false
                    setProjectionPhase("naming_3d_line", state)
                    state.rename.lineBeingRenamed3D = line3D
                    state.rename.lineBeingRenamedPudorys = projPudorys
                    state.rename.lineBeingRenamedNarys = projNarys

                    println("✅ 3D přímka vytvořena:")
                    println("   start = (${line3D.start.x}, ${line3D.start.y}, ${line3D.start.z})")
                    println("   dir   = (${line3D.direction.x}, ${line3D.direction.y}, ${line3D.direction.z})")
                    println("✅ Přidána 3D kolmice: ${line3D.name}")
                } else {
                    println("❌ Přímku se nepodařilo sestavit.")
                }

                state.selectedLineForParallelPudorys = null
                state.selectedSegmentForParallelPudorys = null
                state.selectedLinesPudorys.clear()
                state.selectedSegmentsPudorys.clear()
            }
        }
        if(state.projekcnityp == ProjectionType.AUXILIARY) {
            if (state.selectedLineForParallelPudorys == null && state.selectedSegmentForParallelPudorys == null) {
                val rememberedLine = state.selectedLinesPudorys.firstOrNull()
                val rememberedSegment = state.selectedSegmentsPudorys.firstOrNull()

                when {
                    rememberedLine != null -> {
                        state.selectedLineForParallelPudorys = rememberedLine
                        println("🟦 Přímka '${rememberedLine.name}' vybrána jako vzor pro kolmou.")
                    }

                    rememberedSegment != null -> {
                        state.selectedSegmentForParallelPudorys = rememberedSegment
                        println("🟦 Úsečka vybraná jako vzor pro kolmou.")
                    }

                    else -> {
                        println("⚠️ Neoznačena žádná přímka – vyber jednu kliknutím.")
                        return
                    }
                }
            }

            val basePoint = Point3DPudorys(logical.x, logical.y, name = "₁")

            val originalDir = when {
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

            val direction = Offset(-originalDir.y, originalDir.x)

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

            val sourceName =
                state.selectedLineForParallelPudorys?.name ?: state.selectedSegmentForParallelPudorys?.name ?: ""
            println("🟢 Vytvořena pomocná přímka kolmá na $sourceName, skrze bod $basePoint")
            commitSnapshot(state)
// Reset
            state.selectedLineForParallelPudorys = null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
            state.constructionModifier = ConstructionModifier.ORTHOGONAL
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
            val clickedPoint = Point3DPudorys(logical.x, logical.y, name = "")

            val originalDir = when {
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

            val direction = Offset(-originalDir.y, originalDir.x)

            state.tracePlanePudorys = PlaneTracePudorys(clickedPoint, direction, creationIndex = allocIndex(state))
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("🟢 Vytvořena kolmá půdorysná stopa roviny: ${state.tracePlanePudorys}")

            setProjectionPhase("plane_trace_narys_direction", state)
            state.mongeMode = DrawingModeMonge.NARYS

            val base = state.tracePlanePudorys
            if (base != null && abs(base.direction.y) > 0.0001f) {
                val p = base.point
                val d = base.direction
                val t = -p.y / d.y
                val x = p.x + t * d.x
                val pointOnX12 = Point3DNarys(x = x, z = 0f, name = "X₁₂")
                state.xOnX12Narys = pointOnX12
                println("📍 Vypočten průsečík s x₁₂: $pointOnX12")
            } else {
                println("❌ Nelze spočítat průsečík s x₁₂ – směr je rovnoběžný.")
                state.xOnX12Narys = null
            }

            println("🟡 Přepnuto do nárysu – očekávám druhou stopu roviny.")

            // 🔄 reset
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
            val clickedPoint = Point3DPudorys(logical.x, logical.y, name = "")

            val originalDir = when {
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

            val direction = Offset(-originalDir.y, originalDir.x)


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
        println("⚠️ Konstrukce kolmice není pro tento režim podporována.")
    }
}
}
