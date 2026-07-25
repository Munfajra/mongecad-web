package monge.input.axo.lines

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.DrawingModeMonge
import model.Mongeobjects
import model.ProjectionType
import model.axo.AxoMode
import model.classes.Line3DProjectionPudorys
import model.classes.PlaneTracePudorys
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

fun handleSinglePudorysLineAxo(
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
        mode = AxoMode.AXO_PUDORYS,
        axoModel = state.activeAxoModel
    ) ?: return

    if (state.constructionModifier == ConstructionModifier.PARALLEL) {
        handleParallelLineConstructionPudorysAxo(logical, state)
        return
    }
    if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
        handleOrthogonalLineConstructionPudorysAxo(logical,state)
        return
    }
    if (state.constructionModifier == ConstructionModifier.NONE) {
        val logicalX = logical.x
        val logicalY = logical.y

        val existing = state.pointsPudorys.find {
            abs(it.x - logicalX) < 0.01f && abs(it.y - logicalY) < 0.01f
        }

        val newPoint = existing ?: Point3DPudorys(
            x = logicalX,
            y = logicalY,
            name = "₁",
            parent = null
        )

        if (state.lineStartPoint3DPudorys == null) {
            state.lineStartPoint3DPudorys = newPoint
            updateConstructionInfo(state)
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
}
fun handleParallelLineConstructionPudorysAxo(logical: Offset, state: MongeState){
    if (!state.reusingExistingProjection){
        when (state.drawobjects) {
            Mongeobjects.LINES -> {
                if (!hasOverlayReference(state)) {
                    pickOverlayReferenceFromCurrentHover(state)
                    if (hasOverlayReference(state)) return
                    return
                }

                val direction = resolvePudorysDirectionAxo(state,false)?: return



                val basePoint = Point3DPudorys(logical.x, logical.y, name = "₁")
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

fun handleOrthogonalLineConstructionPudorysAxo(logical: Offset, state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.LINES -> {
            if (!hasOverlayReference(state)) {
                pickOverlayReferenceFromCurrentHover(state)
                if (hasOverlayReference(state)) return
                return
            }

            val direction = resolvePudorysDirectionAxo(state,true)?: return



            val basePoint = Point3DPudorys(logical.x, logical.y, name = "₁")


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
        else -> {
            println("⚠️ Konstrukce kolmice není pro tento režim podporována.")
        }
    }
}
fun resolvePudorysDirectionAxo(
    state: MongeState,
    wantPerpendicular: Boolean = false
): Offset? {
    val basis = state.basis ?: return null

    fun fromPudorys(dir: Offset): Offset {
        return if (wantPerpendicular) perpendicular2D(dir) else dir
    }

    fun fromOverlay(dir: Offset): Offset? {
        val overlayDir =
            if (wantPerpendicular) perpendicular2D(dir) else dir

        return projectAxoOverlayToPudorysDirection(overlayDir, basis)
    }

    val dirProjected =
        when {
            state.selectedLineForParallelPudorys != null -> {
                fromPudorys(state.selectedLineForParallelPudorys!!.direction)
            }

            state.selectedSegmentForParallelPudorys != null -> {
                val seg = state.selectedSegmentForParallelPudorys!!
                fromPudorys(
                    Offset(seg.end.x, seg.end.y) - Offset(seg.start.x, seg.start.y)
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
fun projectAxoOverlayToPudorysDirection(
    overlay: Offset,
    basis: AxoRenderBasis
): Offset? {

    val ex = basis.ex
    val ey = basis.ey

    val det = ex.x * ey.y - ex.y * ey.x
    if (kotlin.math.abs(det) < 1e-6f) return null // degenerovaná báze

    val x = (overlay.x * ey.y - overlay.y * ey.x) / det
    val y = (ex.x * overlay.y - ex.y * overlay.x) / det

    return Offset(x, y)
}