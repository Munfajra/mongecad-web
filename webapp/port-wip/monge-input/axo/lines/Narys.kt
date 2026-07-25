package monge.input.axo.lines

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.DrawingModeMonge
import model.Mongeobjects
import model.ProjectionType
import model.axo.AxoMode
import model.classes.Line3DProjectionNarys
import model.classes.PlaneTraceNarys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.axo.AxoRenderBasis
import monge.input.axo.getLogicalCursorAxo
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import kotlin.math.abs

fun handleSingleLineNarysAxo(
    snappedPointLogical: Offset?,
    state: MongeState
) {
    val logical = getLogicalCursorAxo(
        snapped = snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_NARYS,
        axoModel = state.activeAxoModel
    ) ?: return
    val logicalX = logical.x
    val logicalZ = logical.y

    if (state.constructionModifier == ConstructionModifier.PARALLEL) {
        handleParallelLineConstructionNarysAxo(logical, state)
        return
    }
    if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
        handleOrthogonalLineConstructionNarysAxo(logical, state)
        return
    }

    if (state.drawobjects == Mongeobjects.LINES && state.constructionModifier == ConstructionModifier.NONE) {
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
            updateConstructionInfo(state)
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
}
fun handleOrthogonalLineConstructionNarysAxo(logical: Offset, state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.LINES -> {
            if (!hasOverlayReference(state)) {
                pickOverlayReferenceFromCurrentHover(state)
                if (hasOverlayReference(state)) return
                return
            }

            val direction = resolveNarysDirectionAxo(state,true)?: return
                val basePoint = Point3DNarys(logical.x, logical.y, name = "?")


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

                val clickedPoint = Point3DNarys(logical.x, logical.y, name = "?")

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
fun handleParallelLineConstructionNarysAxo(logical: Offset, state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.LINES -> {
            if (!hasOverlayReference(state)) {
                pickOverlayReferenceFromCurrentHover(state)
                if (hasOverlayReference(state)) return
                return
            }

            val direction = resolveNarysDirectionAxo(state,false)?: return
                val basePoint = Point3DNarys(logical.x, logical.y, name = "?")

                val newLine = Line3DProjectionNarys(basePoint, direction, creationIndex = allocIndex(state))

                state.rename.lineBeingRenamedNarys = newLine
                state.inputName = ""
                state.isNameConfirmed = false
                setProjectionPhase("single_narys_line", state)

                println("🟢 Vytvořena přímka rovnoběžná s ${state.selectedLineForParallelNarys?.name}/${state.selectedSegmentForParallelNarys?.name}, skrze bod $basePoint")

                // ⬇ Reset stavu
                state.selectedLineForParallelNarys = null
                state.selectedSegmentForParallelNarys = null
                state.selectedLinesNarys.clear()
                state.constructionModifier = ConstructionModifier.NONE

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

                // ✅ druhý klik – zvolený bod
                val clickedPoint = Point3DNarys(logical.x, logical.y, name = "?")

                val direction = when {
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

                state.tracePlaneNarys = PlaneTraceNarys(clickedPoint, direction, creationIndex = allocIndex(state))
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("🟢 Vytvořena nárysová stopa roviny rovnoběžná se zadanou přímkou/úsečkou.")



                // 🔍 výpočet průsečíku s osou x₁₂ (pro vizualizaci v půdorysu)
                val base = state.tracePlaneNarys
                if (base != null && abs(base.direction.y) > 0.0001f) {
                    val p = base.point
                    val d = base.direction
                    val t = -p.z / d.y
                    val x = p.x + t * d.x
                    val pointOnX12 = Point3DPudorys(x = x, y = 0f, name = "X₁₂")
                    state.xOnX12Pudorys = pointOnX12
                    println("📍 Vypočten průsečík s x₁₂: $pointOnX12")
                    setProjectionPhase("plane_trace_pudorys_direction", state)
                    state.mongeMode = DrawingModeMonge.PUDORYS
                } else {
                    println("❌ Nelze spočítat průsečík s x₁₂ – směr je rovnoběžný,přepínám na spešl kejs")
                    setProjectionPhase("plane_trace_pudorys_special_direction", state)
                    state.mongeMode = DrawingModeMonge.PUDORYS

                }

                println("🟡 Přepnuto do půdorysu – očekávám druhou stopu roviny.")

                // 🔄 reset přímky/úsečky
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
                val clickedPoint = Point3DNarys(logical.x, logical.y, name = "?")

                val direction = when {
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
            println("⚠️ Konstrukce rovnoběžky není pro tento režim podporována.")
            println("DEBUG 🧠 Aktuální projectionPhase: ${state.projectionPhase}")

        }
    }
}

fun resolveNarysDirectionAxo(
    state: MongeState,
    wantPerpendicular: Boolean = false
): Offset? {
    val basis = state.basis ?: return null

    fun fromNarys(dir: Offset): Offset {
        return if (wantPerpendicular) perpendicular2D(dir) else dir
    }

    fun fromOverlay(dir: Offset): Offset? {
        val overlayDir =
            if (wantPerpendicular) perpendicular2D(dir) else dir

        return projectAxoOverlayToNarysDirection(overlayDir, basis)
    }

    val dirProjected =
        when {
            state.selectedLineForParallelNarys != null -> {
                fromNarys(state.selectedLineForParallelNarys!!.direction)
            }

            state.selectedSegmentForParallelNarys != null -> {
                val seg = state.selectedSegmentForParallelNarys!!
                fromNarys(
                    Offset(seg.end.x, seg.end.z) - Offset(seg.start.x, seg.start.z)
                )
            }

            state.selectedSegmentForParallelAxo != null -> {
                val seg = state.selectedSegmentForParallelAxo!!
                val dir = Offset(seg.end.x, seg.end.y) - Offset(seg.start.x, seg.start.y)
                fromOverlay(dir)
            }

            state.selectedSegmentForParallelAO != null -> {
                val seg = state.selectedSegmentForParallelAO!!
                val dir = Offset(seg.end.x, seg.end.y) - Offset(seg.start.x, seg.start.y)
                fromOverlay(dir)
            }

            state.selectedLineForParallelAxo != null -> {
                fromOverlay(state.selectedLineForParallelAxo!!.dir)
            }

            state.selectedLineForParallelAO != null -> {
                fromOverlay(state.selectedLineForParallelAO!!.dir)
            }

            else -> null
        }

    return dirProjected?.normalizedOrNull()
}
fun projectAxoOverlayToNarysDirection(
    overlay: Offset,
    basis: AxoRenderBasis
): Offset? {

    val ex = basis.ex
    val ez = basis.ez

    val det = ex.x * ez.y - ex.y * ez.x
    if (kotlin.math.abs(det) < 1e-6f) return null // degenerovaná báze

    val x = (overlay.x * ez.y - overlay.y * ez.x) / det
    val z = (ex.x * overlay.y - ex.y * overlay.x) / det

    return Offset(x, z)
}