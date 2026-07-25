package monge.input.planes

import utils.System
import dialogs.batchinput.dummyBokorys
import dialogs.batchinput.dummyNarys
import dialogs.batchinput.dummyPudorys
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import model.*
import model.classes.Plane3D
import model.classes.PlaneEquation
import model.classes.PlaneTraceNarys
import model.classes.PlaneTracePudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.planeEquationFromTraces
import model.classes.tracesFromPlaneEquation
import monge.input.combineprojections.resolvePlaneNamingAfterCompletion
import monge.input.lines.directionHandlers.lines.handleOrthogonalLineConstructionNarys
import monge.input.lines.directionHandlers.lines.handleOrthogonalLineConstructionPudorys
import monge.input.lines.directionHandlers.lines.handleParallelLineConstructionNarys
import monge.input.lines.directionHandlers.lines.handleParallelLineConstructionPudorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetPlaneConstruction
import ui.resetStavu
import utils.allocIndex
import utils.getLogicalCursor
import kotlin.math.abs

fun handlePlaneConstructionPudorys(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    change: PointerInputChange,
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

    if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.PLANE && state.projekcnityp== ProjectionType.ASSOCIATED) {
        if (state.constructionModifier == ConstructionModifier.PARALLEL && state.projectionPhase=="pudorys_start") {
            handleParallelLineConstructionPudorys(logical, state)
            return
        }
        if (state.constructionModifier == ConstructionModifier.ORTHOGONAL && state.projectionPhase=="pudorys_start") {
            handleOrthogonalLineConstructionPudorys(logical, state)
            return
        }

        when (state.projectionPhase) {
            "pudorys_start" -> {
                val start = Point3DPudorys(logical.x, logical.y, name = "?", parent = null)
                state.firstPlaneTraceStartPudorys = start
                setProjectionPhase("plane_trace_pudorys_start", state)
                println("🟡 Změna projectionPhase na: plane_trace_pudorys_start")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("Začátek první stopy (půdorys): $start")
            }

            "plane_trace_pudorys_start" -> {
                val start = state.firstPlaneTraceStartPudorys ?: return println("❌ Chybí první bod stopy roviny.")
                val direction = Offset(logical.x - start.x, logical.y - start.y)
                if (direction.getDistance() != 0f) {
                    state.tracePlanePudorys = PlaneTracePudorys(start, direction, creationIndex = allocIndex(state))
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    println("🟢 Zadána první stopa roviny (půdorys): ${state.tracePlanePudorys}")
                    setProjectionPhase("plane_trace_narys_direction", state)
                    state.mongeMode = DrawingModeMonge.NARYS

                    state.tracePlanePudorys?.let { base ->
                        if (abs(base.direction.y) > 0.0001f) {
                            val p = base.point
                            val d = base.direction
                            val t = -p.y / d.y
                            val x = p.x + t * d.x
                            val pointOnX12 = Point3DNarys(x = x, z = 0f, name = "X₁₂")
                            state.xOnX12Narys = pointOnX12
                            println("📍 Vypočten průsečík s x₁₂: $pointOnX12")
                        } else {
                            println("❌ Nelze spočítat průsečík s x₁₂ – směr je rovnoběžný.ajaj2")
                            state.xOnX12Narys = null
                            resetStavu(state)
                        }
                    }
                    state.skipNextClick = true
                }
            }
        }
    }
    if( state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.projekcnityp== ProjectionType.ASSOCIATED  &&
        state.projectionPhase == "plane_trace_narys_special_direction" &&
        change.changedToDown() ) {
        state.constructionModifier = ConstructionModifier.PARALLEL


        val base = state.tracePlanePudorys ?: return println("❌ Chybí počáteční bod první stopy.").also {
            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
            resetStavu(state)
        }


        val direction = Offset(100f,0f)
        val click = Offset(logical.x,-logical.y)
        val point = Point3DNarys(click.x, click.y,)
        val traceNarys = PlaneTraceNarys(point, direction, creationIndex = allocIndex(state))
        val yP = base.point.y
        val zN = -traceNarys.point.z

        val xStar = base.point.x

        val P = Offset3D(xStar, yP, 0f)
        val Q = Offset3D(xStar, 0f,  zN)

        val d = Offset3D(1f, 0f, 0f)
        val v = Q - P

        val n = d.cross(v)   // = (0, zN, -yP)


        val a = 0f
        val b = zN
        val c = -yP
        val d0 = -(b * yP + c * 0f)  // = -zN * yP

        val equation = PlaneEquation(a = a, b = b, c = c, d = d0)
        val (_,_,tB) = tracesFromPlaneEquation(equation)
        val traceBokorys = tB?: dummyBokorys()

        val style = state.currentLineStyleSettings

        val plane = Plane3D(
            tracePudorys = base,
            traceNarys = traceNarys,
            traceBokorys = traceBokorys?: dummyBokorys(),
            name = "ρ",
            equation = equation,
            lineStyle = style.style,
            color = style.color,
            strokeWidth = style.strokeWidth
        )


        if (state.reusingExistingProjection){
            state.traceToAttach?.let { trace ->
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.planes3D.add(plane)
                trace.parent = plane
                state.lineTracesNarys.add(traceNarys.copy(parent = plane, parentId = plane.id))
                state.lineTracesBokorys.add(traceBokorys.copy(parent = plane, parentId = plane.id))
                resolvePlaneNamingAfterCompletion(state, plane, trace)
                resetPlaneConstruction(state)

            }} else {
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.planePendingForNaming = plane
            state.showPlaneNamingDialog = true
            state.planeNameInput = ""
            state.planes3D.add(plane)
            state.lineTracesPudorys.add(base.copy(parent = plane, parentId = plane.id))
            state.lineTracesNarys.add(traceNarys.copy(parent = plane,parentId = plane.id))

            println("✅ Rovina vytvořena: $plane")

            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
            resetPlaneConstruction(state)
            repeatCons(state)

// 🔄 Reset
            state.selectedLineForParallelPlaneNarys = null
            state.selectedSegmentForParallelNarys = null
            state.selectedLinesNarys.clear()
            state.selectedSegmentsNarys.clear()
            resetStavu(state)
        }}
    if( state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.projekcnityp== ProjectionType.ASSOCIATED &&
        state.constructionModifier == ConstructionModifier.PARALLEL &&
        state.projectionPhase == "plane_trace_narys_direction" &&
        change.changedToDown() ) {
        if (state.selectedLineForParallelPlaneNarys == null && state.selectedSegmentForParallelNarys == null) {

            // ✅ vyberu první označenou přímku nebo úsečku z nárysu
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

        val base = state.tracePlanePudorys ?: return println("❌ Chybí počáteční bod první stopy.tvojemama").also {
            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
            resetStavu(state)
        }

        val pointOnX12 = state.xOnX12Narys ?: return println("❌ Chybí průsečík s x₁₂.")

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
                println("❌ Interní chyba – chybí vzor pro druhou stopu roviny.")
                return
            }
        }

        val p0 = Point3D(base.point.x, base.point.y, 0f, name = "?")
        val traceNarys = PlaneTraceNarys(pointOnX12, direction, creationIndex = allocIndex(state))
        val v1 = Offset3D(base.direction.x, base.direction.y, 0f)
        val v2 = Offset3D(traceNarys.direction.x, 0f, traceNarys.direction.y)
        val equation = planeEquationFromTraces(p0, v1, v2)
        val style = state.currentLineStyleSettings
        val (_,_,tB) = tracesFromPlaneEquation(equation)
        val traceBokorys = tB?: dummyBokorys()
        val plane = Plane3D(
            tracePudorys = base,
            traceNarys = traceNarys,
            traceBokorys = traceBokorys,
            name = "ρ",
            equation = equation,
            lineStyle = style.style,
            color = style.color,
            strokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
        )


if (state.reusingExistingProjection){
   state.traceToAttach?.let { trace ->
       state.deferSelectionUntil = System.currentTimeMillis() + 100
       state.planes3D.add(plane)
       trace.parent = plane
       state.lineTracesNarys.add(traceNarys.copy(parent = plane,parentId = plane.id))
       state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
       resolvePlaneNamingAfterCompletion(state, plane, trace)

       resetPlaneConstruction(state)
}} else {
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    state.planePendingForNaming = plane
    state.showPlaneNamingDialog = true
    state.planeNameInput = ""
    state.planes3D.add(plane)
        state.lineTracesPudorys.add(base.copy(parent = plane,parentId = plane.id))
        state.lineTracesNarys.add(traceNarys.copy(parent = plane,parentId = plane.id))
    state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
        println("✅ Rovina vytvořena: $plane")

        setProjectionPhase("pudorys_start", state)
        state.mongeMode = DrawingModeMonge.PUDORYS
        resetPlaneConstruction(state)

// 🔄 Reset
        state.selectedLineForParallelPlaneNarys = null
        state.selectedSegmentForParallelNarys = null
        state.selectedLinesNarys.clear()
        state.selectedSegmentsNarys.clear()
        resetStavu(state)
    }}
    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.constructionModifier == ConstructionModifier.ORTHOGONAL &&
        state.projectionPhase == "plane_trace_narys_direction" &&
        change.changedToDown()
    ) {
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
                    println("⚠️ Neoznačena žádná přímka – nejprve vyber jednu kliknutím.")
                    return
                }
            }

            val base = state.tracePlanePudorys ?: return println("❌ Chybí počáteční bod první stopy.").also {
                setProjectionPhase("pudorys_start", state)
                state.mongeMode = DrawingModeMonge.PUDORYS
                resetStavu(state)
            }

            val pointOnX12 = state.xOnX12Narys
            if (pointOnX12 == null) {
                println("❌ Chybí průsečík s osou x₁₂ v nárysu.")
                resetStavu(state)
                return
            }

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
                    println("❌ Interní chyba – chybí vzor pro kolmou stopu.")
                    return
                }
            }

            val direction = Offset(-originalDir.y, originalDir.x)

            val p0 = Point3D(base.point.x, base.point.y, 0f, name = "?")
            val traceNarys = PlaneTraceNarys(pointOnX12, direction, creationIndex = allocIndex(state))
            val v1 = Offset3D(base.direction.x, base.direction.y, 0f)
            val v2 = Offset3D(traceNarys.direction.x, 0f, traceNarys.direction.y)
            val equation = planeEquationFromTraces(p0, v1, v2)
            val style = state.currentLineStyleSettings
            val (_,_,tB) = tracesFromPlaneEquation(equation)
            val traceBokorys = tB?: dummyBokorys()
            val plane = Plane3D(
                tracePudorys = base,
                traceNarys = traceNarys,
                traceBokorys = traceBokorys,
                name = "ρ",
                equation = equation,
                lineStyle = style.style,
                color = style.color,
                strokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
            )

            if (state.reusingExistingProjection){
                state.traceToAttach?.let { trace ->
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    state.planes3D.add(plane)
                    trace.parent = plane
                    state.lineTracesNarys.add(traceNarys.copy(parent = plane,parentId = plane.id))
                    state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
                    resolvePlaneNamingAfterCompletion(state, plane, trace)
                    resetPlaneConstruction(state)

                }} else {
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.planePendingForNaming = plane
                state.showPlaneNamingDialog = true
                state.planeNameInput = ""
                state.planes3D.add(plane)

                state.lineTracesPudorys.add(base.copy(parent = plane,parentId = plane.id))
                state.lineTracesNarys.add(traceNarys.copy(parent = plane,parentId = plane.id))
                state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))

                println("✅ Rovina vytvořena: $plane")

                setProjectionPhase("pudorys_start", state)
                state.mongeMode = DrawingModeMonge.PUDORYS
                resetPlaneConstruction(state)
            }
            // reset výběrů
            state.selectedLineForParallelPlaneNarys = null
            state.selectedSegmentForParallelNarys = null
            state.selectedLinesNarys.clear()
            state.selectedSegmentsNarys.clear()
        }
    }

    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.projekcnityp== ProjectionType.ASSOCIATED &&
        state.constructionModifier == ConstructionModifier.NONE&&
        state.projectionPhase == "plane_trace_narys_direction" &&
        change.changedToDown()
    ) {
        if (state.skipNextClick) {
            state.skipNextClick = false
            println("⏭️ Přeskakuji kliknutí podle skipNextClick.")
            return
        }

        val base = state.tracePlanePudorys ?: return println("❌ Chybí počáteční bod první stopy.").also {
            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
        }

        val p = base.point
        val d = base.direction
        if (abs(d.y) < 0.0001f) {
            println("❌ Půdorysná stopa je rovnoběžná s osou x₁₂ – průsečík nelze spočítat.")
            setProjectionPhase("pudorys_start", state)
            resetStavu(state)
            return
        }

        val t = -p.y / d.y
        val xOnX12 = p.x + t * d.x
        val clickedPoint = Point3DNarys(x = logical.x, z = -logical.y, name = "?")
        val direction = Offset(xOnX12 - clickedPoint.x, -clickedPoint.z)
        if (direction.getDistance() == 0f) {
            println("❌ Směr nárysné stopy je nulový.")
            return
        }

        val traceNarys = PlaneTraceNarys(clickedPoint, direction, creationIndex = allocIndex(state))
        val p0 = Point3D(base.point.x, base.point.y, 0f, name = "?")
        val v1 = Offset3D(base.direction.x, base.direction.y, 0f)
        val v2 = Offset3D(traceNarys.direction.x, 0f, traceNarys.direction.y)
        val equation = planeEquationFromTraces(p0, v1, v2)
        val style = state.currentLineStyleSettings
        val (_,_,tB) = tracesFromPlaneEquation(equation)
        val traceBokorys = tB?: dummyBokorys()
        val plane = Plane3D(
            tracePudorys = base,
            traceNarys=traceNarys,
            traceBokorys=traceBokorys,
            name = "",
            equation = equation,
            lineStyle = style.style,
            color = style.color,
            strokeWidth = style.strokeWidth,
            creationIndex = allocIndex(state)
        )

        if (state.reusingExistingProjection){
            state.traceToAttach?.let { trace ->
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.planes3D.add(plane)
                trace.parent = plane
                state.lineTracesNarys.add(traceNarys.copy(parent = plane,parentId = plane.id))
                state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
                resolvePlaneNamingAfterCompletion(state, plane, trace)
                resetPlaneConstruction(state)
            }} else
            {
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.planePendingForNaming = plane
            state.showPlaneNamingDialog = true
            state.planeNameInput = ""
            state.planes3D.add(plane)

            state.lineTracesPudorys.add(base.copy(parent = plane,parentId = plane.id))
            state.lineTracesNarys.add(traceNarys.copy(parent = plane,parentId = plane.id))
            state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
            println("✅ Rovina vytvořena: $plane")
        }

        setProjectionPhase("pudorys_start", state)
        state.mongeMode = DrawingModeMonge.PUDORYS
        state.tracePlanePudorys = null
        state.firstPlaneTraceStartPudorys = null
        resetPlaneConstruction(state)
    }
}
fun handlePlaneConstructionNarys(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    change: PointerInputChange,
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

    if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.PLANE && state.projekcnityp== ProjectionType.ASSOCIATED) {
        if (state.constructionModifier == ConstructionModifier.PARALLEL && state.projectionPhase=="narys_start") {
            handleParallelLineConstructionNarys(logical, state)
            return
        }
        if (state.constructionModifier == ConstructionModifier.ORTHOGONAL && state.projectionPhase=="narys_start") {
            handleOrthogonalLineConstructionNarys(logical, state)
            return
        }
        when (state.projectionPhase) {
            "narys_start" -> {
                val start = Point3DNarys(logical.x, -logical.y, name = "?", parent = null)
                state.firstPlaneTraceStartNarys = start
                setProjectionPhase("plane_trace_narys_start", state)
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("Začátek první stopy (nárys): $start")
            }

            "plane_trace_narys_start" -> {
                val start = state.firstPlaneTraceStartNarys ?: return println("❌ Chybí první bod stopy roviny.")
                val direction = Offset(logical.x - start.x, -logical.y - start.z)
                if (direction.getDistance() != 0f) {
                    state.tracePlaneNarys = PlaneTraceNarys(start, direction, creationIndex = allocIndex(state))
                    println("🟢 Zadána první stopa roviny (nárys): ${state.tracePlaneNarys}")
                    setProjectionPhase("plane_trace_pudorys_direction", state)
                    state.mongeMode = DrawingModeMonge.PUDORYS

                    val base = state.tracePlaneNarys ?: return println("❌ Chybí stopa roviny.")
                    val p = base.point
                    val d = base.direction

                    if (abs(d.y) > 1e-5f) {
                        val t = -p.z / d.y
                        val x = p.x + t * d.x
                        val pointOnX12 = Point3DPudorys(x = x, y = 0f, name = "X₁₂")
                        state.xOnX12Pudorys = pointOnX12
                        println("📍 Vypočten průsečík s x₁₂: $pointOnX12")
                    } else {
                        println("❌ Nelze spočítat průsečík – směr nárysové stopy je rovnoběžný s x₁₂.")
                        state.xOnX12Pudorys = null
                        resetStavu(state)
                    }
                    state.skipNextClick = true
                }
            }
        }
    }
    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.constructionModifier == ConstructionModifier.PARALLEL &&
        state.projectionPhase == "plane_trace_pudorys_direction" &&
        change.changedToDown()
    ) {
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

            val base = state.tracePlaneNarys ?: return println("❌ Chybí počáteční bod první stopy.").also {
                setProjectionPhase("narys_start", state)
                state.mongeMode = DrawingModeMonge.NARYS
                resetStavu(state)
            }

            val pointOnX12 = state.xOnX12Pudorys
            if (pointOnX12 == null) {
                println("❌ Chybí průsečík s osou x₁₂ v půdorysu.")
                resetStavu(state)
                return
            }

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

            val p0 = Point3D(base.point.x, 0f, base.point.z, name = "?")
            val tracePudorys = PlaneTracePudorys(pointOnX12, direction, creationIndex = allocIndex(state))
            val v1 = Offset3D(base.direction.x, 0f, base.direction.y)
            val v2 = Offset3D(tracePudorys.direction.x, tracePudorys.direction.y, 0f)
            val equation = planeEquationFromTraces(p0, v2, v1)
            val (_,_,tB) = tracesFromPlaneEquation(equation)
            val traceBokorys = tB?: dummyBokorys()
            val style = state.currentLineStyleSettings
            state.deferSelectionUntil = System.currentTimeMillis() + 100

            val plane = Plane3D(
                tracePudorys = tracePudorys,
                traceNarys = base,
                traceBokorys=traceBokorys,
                name = "ρ",
                equation = equation,
                lineStyle = style.style,
                color = style.color,
                strokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
            )

            if (state.reusingExistingProjection){
                state.traceToAttach?.let { trace ->
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    state.planes3D.add(plane)
                    trace.parent = plane
                    state.lineTracesPudorys.add(tracePudorys.copy(parent = plane,parentId = plane.id))
                    state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
                    resolvePlaneNamingAfterCompletion(state, plane, trace)
                    resetPlaneConstruction(state)
                }} else
                {
                state.planePendingForNaming = plane
                state.showPlaneNamingDialog = true
                state.planeNameInput = ""
                state.planes3D.add(plane)

                state.lineTracesNarys.add(base.copy(parent = plane,parentId = plane.id))
                state.lineTracesPudorys.add(tracePudorys.copy(parent = plane,parentId = plane.id))
                state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
                println("✅ Rovina vytvořena: $plane")

                setProjectionPhase("narys_start", state)
                state.mongeMode = DrawingModeMonge.NARYS
                resetPlaneConstruction(state)
            }
            // 🔄 Reset výběrů
            state.selectedLineForParallelPlanePudorys = null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
        }
    }
    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.projectionPhase == "plane_trace_pudorys_special_direction" &&
        change.changedToDown()
    ) {

        val base = state.tracePlaneNarys ?: return println("❌ Chybí počáteční bod první stopy.").also {
            setProjectionPhase("narys_start", state)
            state.mongeMode = DrawingModeMonge.NARYS
            resetStavu(state)
        }

        val direction = Offset(100f, 0f)
        val click = Offset(logical.x, logical.y)
        val pointP = Point3DPudorys(click.x, click.y)         // Půdorysný bod má (x, y)
        val tracePudorys = PlaneTracePudorys(pointP, direction, creationIndex = allocIndex(state))


        val yP = tracePudorys.point.y
        val zN = -base.point.z

        val xStar = base.point.x                               // libovolné stejné x pro oba body

        val P = Offset3D(xStar, yP, 0f)                        // na půdorysné stopě
        val Q = Offset3D(xStar, 0f,  zN)                       // na nárysné stopě

        val d = Offset3D(1f, 0f, 0f)                           // směr stop (|| x)
        val v = Q - P
        val n = d.cross(v)                                     // = (0, zN, -yP)

// Rovnice: a x + b y + c z + d0 = 0
        val a = 0f
        val b = zN
        val c = -yP
        val d0 = -(zN * yP)                                    // POZOR: minus!

        val equation = PlaneEquation(a = a, b = b, c = c, d = d0)
        val (_,_,tB) = tracesFromPlaneEquation(equation)
        val traceBokorys = tB?: dummyBokorys()
        val style = state.currentLineStyleSettings
        val plane = Plane3D(
            tracePudorys = tracePudorys,
            traceNarys = base,
            traceBokorys = traceBokorys,
            name = "ρ",
            equation = equation,
            lineStyle = style.style,
            color = style.color,
            strokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
        )


            if (state.reusingExistingProjection){
                state.traceToAttach?.let { trace ->
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    state.planes3D.add(plane)
                    trace.parent = plane
                    state.lineTracesPudorys.add(tracePudorys.copy(parent = plane,parentId = plane.id))
                    state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
                    resolvePlaneNamingAfterCompletion(state, plane, trace)
                    resetPlaneConstruction(state)
                }} else
                {
                state.planePendingForNaming = plane
                state.showPlaneNamingDialog = true
                state.planeNameInput = ""
                state.planes3D.add(plane)

                state.lineTracesNarys.add(base.copy(parent = plane,parentId = plane.id))
                state.lineTracesPudorys.add(tracePudorys.copy(parent = plane,parentId = plane.id))
                state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))

                println("✅ Rovina vytvořena: $plane")

                setProjectionPhase("narys_start", state)
                state.mongeMode = DrawingModeMonge.NARYS
                resetPlaneConstruction(state)
            }
            // 🔄 Reset výběrů

            state.selectedLineForParallelPlanePudorys = null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
    }
    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.projekcnityp == ProjectionType.ASSOCIATED &&
        state.constructionModifier == ConstructionModifier.ORTHOGONAL &&
        state.projectionPhase == "plane_trace_pudorys_direction" &&
        change.changedToDown()
    ) {
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

            val base = state.tracePlaneNarys ?: return println("❌ Chybí počáteční bod první stopy.").also {
                setProjectionPhase("narys_start", state)
                state.mongeMode = DrawingModeMonge.NARYS
                resetStavu(state)
            }

            val pointOnX12 = state.xOnX12Pudorys
            if (pointOnX12 == null) {
                println("❌ Chybí průsečík s osou x₁₂ v půdorysu.")
                resetStavu(state)
                return
            }

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
                    println("❌ Interní chyba – chybí vzor pro kolmici.")
                    return
                }
            }

            val direction = Offset(-originalDir.y, originalDir.x)

            val p0 = Point3D(base.point.x, 0f, base.point.z, name = "?")
            val tracePudorys = PlaneTracePudorys(pointOnX12, direction, creationIndex = allocIndex(state))
            val v1 = Offset3D(base.direction.x, 0f, base.direction.y)
            val v2 = Offset3D(tracePudorys.direction.x, tracePudorys.direction.y, 0f)
            val equation = planeEquationFromTraces(p0, v2, v1)
            val style = state.currentLineStyleSettings
            val (_,_,tB) = tracesFromPlaneEquation(equation)
            val traceBokorys = tB?: dummyBokorys()
            val plane = Plane3D(
                tracePudorys = tracePudorys,
                traceNarys = base,
                traceBokorys = traceBokorys,
                name = "ρ",
                equation = equation,
                lineStyle = style.style,
                color = style.color,
                strokeWidth = style.strokeWidth, creationIndex = allocIndex(state)
            )

            if (state.reusingExistingProjection){
                state.traceToAttach?.let { trace ->
                    state.deferSelectionUntil = System.currentTimeMillis() + 100
                    state.planes3D.add(plane)
                    trace.parent = plane
                    state.lineTracesPudorys.add(tracePudorys.copy(parent = plane,parentId = plane.id))
                    state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
                    resolvePlaneNamingAfterCompletion(state, plane, trace)
                    resetPlaneConstruction(state)
                }} else
                {
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.planePendingForNaming = plane
                state.showPlaneNamingDialog = true
                state.planeNameInput = ""
                state.planes3D.add(plane)

                state.lineTracesNarys.add(base.copy(parent = plane,parentId = plane.id))
                state.lineTracesPudorys.add(tracePudorys.copy(parent = plane,parentId = plane.id))
                state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))

                println("✅ Rovina vytvořena: $plane")

                setProjectionPhase("narys_start", state)
                state.mongeMode = DrawingModeMonge.NARYS
                resetPlaneConstruction(state)
            }
            // 🔄 reset

            state.selectedLineForParallelPlanePudorys = null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
        }
    }

    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.PLANE &&
        state.projekcnityp== ProjectionType.ASSOCIATED &&
        state.constructionModifier == ConstructionModifier.NONE &&
        state.projectionPhase == "plane_trace_pudorys_direction" &&
        change.changedToDown()
    ) {
        if (state.skipNextClick) {
            state.skipNextClick = false
            println("⏭️ Přeskakuji kliknutí podle skipNextClick.")
            return
        }

        val base = state.tracePlaneNarys ?: return println("❌ Chybí počáteční bod první stopy.").also {
            setProjectionPhase("narys_start", state)
            state.mongeMode = DrawingModeMonge.NARYS
            resetStavu(state)
        }

        val p = base.point
        val d = base.direction
        if (abs(d.y) < 0.0001f) {
            println("❌ Nárysná stopa je rovnoběžná s osou x₁₂ – průsečík nelze spočítat.")
            setProjectionPhase("narys_start", state)
            resetStavu(state)
            return
        }

        val t = -p.z / d.y
        val xOnX12 = p.x + t * d.x
        val clickedPoint = Point3DPudorys(x = logical.x, y = logical.y, name = "?")
        val direction = Offset(clickedPoint.x - xOnX12, clickedPoint.y)


        if (direction.getDistance() == 0f) {
            println("❌ Směr půdorysné stopy je nulový.")
            return
        }

        val tracePudorys = PlaneTracePudorys(clickedPoint, direction, creationIndex = allocIndex(state))
        val p0 = Point3D(base.point.x,0f , base.point.z, name = "?")
        val v1 = Offset3D(base.direction.x, 0f, base.direction.y)
        val v2 = Offset3D(tracePudorys.direction.x, tracePudorys.direction.y,0f )
        val equation = planeEquationFromTraces(p0, v2, v1)
        val style = state.currentLineStyleSettings
        val (_,_,tB) = tracesFromPlaneEquation(equation)
        val traceBokorys = tB?: dummyBokorys()
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        val plane = Plane3D(
            tracePudorys =tracePudorys,
            traceNarys = base,
            traceBokorys = traceBokorys,
            name= "",
            equation,
            lineStyle = style.style,
            color = style.color,
            strokeWidth = style.strokeWidth,
            creationIndex = allocIndex(state)
        )
        if (state.reusingExistingProjection){
            state.traceToAttach?.let { trace ->
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.planes3D.add(plane)
                trace.parent = plane
                state.lineTracesPudorys.add(tracePudorys.copy(parent = plane,parentId = plane.id))
                state.lineTracesBokorys.add(traceBokorys.copy(parent = plane,parentId = plane.id))
                resolvePlaneNamingAfterCompletion(state, plane, trace)
                resetPlaneConstruction(state)
            }} else {
            state.planePendingForNaming = plane
            state.showPlaneNamingDialog = true
            state.planeNameInput = ""
            state.planes3D.add(plane)

            state.lineTracesPudorys.add(tracePudorys.copy(parent = plane, id = tracePudorys.id))
            state.lineTracesNarys.add(base.copy(parent = plane, id = base.id))
            state.lineTracesBokorys.add(traceBokorys.copy(parent = plane, id = base.id))


            println("✅ Rovina vytvořena: $plane")
        }

        setProjectionPhase("narys_start", state)
        state.mongeMode = DrawingModeMonge.NARYS
        resetPlaneConstruction(state)
    }
}
fun handleSingleTraceNarys(cursor: Offset,
                           snappedPointLogical: Offset?,
                           canvasOffset: Offset,
                           scale: Float,
                           state: MongeState)
{
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
    if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.PLANE && state.projekcnityp== ProjectionType.SINGLE) {
        if (state.constructionModifier == ConstructionModifier.PARALLEL && state.projectionPhase=="narys_start") {
            handleParallelLineConstructionNarys(logical, state)
            return
        }
        if (state.constructionModifier == ConstructionModifier.ORTHOGONAL && state.projectionPhase=="narys_start") {
            handleOrthogonalLineConstructionNarys(logical, state)
            return
        }
        when (state.projectionPhase) {
              "narys_start" -> {  val start = Point3DNarys(logical.x, -logical.y, name = "", parent = null)
            state.firstPlaneTraceStartNarys = start
                    setProjectionPhase("plane_trace_single_narys_start", state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("Začátek první stopy (nárys): $start")
        }
            "plane_trace_single_narys_start" -> {
                val start = state.firstPlaneTraceStartNarys ?: return println("❌ Chybí první bod stopy roviny.")
                val direction = Offset(logical.x - start.x, -logical.y - start.z)
                if (direction.getDistance() != 0f) {

                    state.tracePlaneNarys = PlaneTraceNarys(
                        start,
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

                    resetStavu(state)

                }
            }

    }

}
}
fun handleSingleTracePudorys(cursor: Offset,
                           snappedPointLogical: Offset?,
                           canvasOffset: Offset,
                           scale: Float,
                             state: MongeState)
{
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
    if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.PLANE && state.projekcnityp== ProjectionType.SINGLE) {
        if (state.constructionModifier == ConstructionModifier.PARALLEL && state.projectionPhase=="pudorys_start") {
            handleParallelLineConstructionPudorys(logical, state)
            return
        }
        if (state.constructionModifier == ConstructionModifier.ORTHOGONAL && state.projectionPhase=="pudorys_start") {
            handleOrthogonalLineConstructionPudorys(logical, state)
            return
        }
        when (state.projectionPhase) {
            "pudorys_start" -> {  val start = Point3DPudorys(logical.x, logical.y, name = "", parent = null)
                state.firstPlaneTraceStartPudorys = start
                setProjectionPhase("plane_trace_single_pudorys_start", state)
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                println("Začátek první stopy (půdorys): $start")
            }
            "plane_trace_single_pudorys_start" -> {
                val start = state.firstPlaneTraceStartPudorys ?: return println("❌ Chybí první bod stopy roviny.")
                val direction = Offset(logical.x - start.x, logical.y - start.y)
                if (direction.getDistance() != 0f) {

                    state.tracePlanePudorys = PlaneTracePudorys(
                        start,
                        direction,
                        localColor = state.currentLineStyleSettings.color,
                        localName = "",
                        localLineStyle = state.currentLineStyleSettings.style,
                        localStrokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state)
                    )
                    println("🟢 Zadána stopa roviny (půdorys): ${state.tracePlanePudorys}")

                    state.pudorysTracePendingForNaming = state.tracePlanePudorys
                    state.showPlaneNamingDialog = true
                    state.lineTracesPudorys.add(state.tracePlanePudorys!!)
                    repeatCons(state)
                    updateConstructionInfo(state)
                    resetStavu(state)


                }
            }

        }

    }
}
fun completeSpecialCasePlaneNarys (state: MongeState) {
    if (state.projectionPhase == "plane_trace_narys_special_direction") {


        val base = state.tracePlanePudorys ?: return println("❌ Chybí počáteční bod první stopy.").also {
            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
            resetStavu(state)
        }
        if (state.tracePlanePudorys == null) return



        val a = 0f
        val b = 1f
        val c = 0f
        val d0 = -state.tracePlanePudorys!!.point.y

        val equation = PlaneEquation(a = a, b = b, c = c, d = d0)
        val (_,_,tB) = tracesFromPlaneEquation(equation)
        val traceBokorys = tB?: dummyBokorys()
        val style = state.currentLineStyleSettings
        val plane = Plane3D(
            tracePudorys = base,
            traceNarys = dummyNarys(),
            traceBokorys = traceBokorys,
            name = "ρ",
            equation = equation,
            lineStyle = style.style,
            color = style.color,
            strokeWidth = style.strokeWidth
        )


        if (state.reusingExistingProjection) {
            state.traceToAttach?.let { trace ->
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.planes3D.add(plane)
                trace.parent = plane
                resolvePlaneNamingAfterCompletion(state, plane, trace)
                resetPlaneConstruction(state)

            }
        } else {
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.planePendingForNaming = plane
            state.showPlaneNamingDialog = true
            state.planeNameInput = ""
            state.planes3D.add(plane)
            state.lineTracesPudorys.add(base.copy(parent = plane, parentId = plane.id))
            state.lineTracesBokorys.add(traceBokorys.copy(parent = plane, parentId = plane.id))

            println("✅ Rovina vytvořena: $plane")

            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
            resetPlaneConstruction(state)
            repeatCons(state)

// 🔄 Reset
            state.selectedLineForParallelPlaneNarys = null
            state.selectedSegmentForParallelNarys = null
            state.selectedLinesNarys.clear()
            state.selectedSegmentsNarys.clear()
            resetStavu(state)
        }
    }
}
fun completeSpecialCasePlanePudorys (state: MongeState) {
    if (state.projectionPhase == "plane_trace_pudorys_special_direction") {


        val base = state.tracePlaneNarys ?: return println("❌ Chybí počáteční bod první stopy.").also {
            setProjectionPhase("narys_start", state)
            state.mongeMode = DrawingModeMonge.NARYS
            resetStavu(state)
        }
        if (state.tracePlaneNarys == null) return



        val a = 0f
        val b = 0f
        val c = 1f
        val d0 = -state.tracePlaneNarys!!.point.z

        val equation = PlaneEquation(a = a, b = b, c = c, d = d0)
        val (_,_,tB) = tracesFromPlaneEquation(equation)
        val traceBokorys = tB?: dummyBokorys()
        val style = state.currentLineStyleSettings
        val plane = Plane3D(
            tracePudorys = dummyPudorys(),
            traceNarys = base,
            traceBokorys = traceBokorys,
            name = "ρ",
            equation = equation,
            lineStyle = style.style,
            color = style.color,
            strokeWidth = style.strokeWidth
        )


        if (state.reusingExistingProjection) {
            state.traceToAttach?.let { trace ->
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.planes3D.add(plane)
                trace.parent = plane
                resolvePlaneNamingAfterCompletion(state, plane, trace)
                resetPlaneConstruction(state)

            }
        } else {
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            state.planePendingForNaming = plane
            state.showPlaneNamingDialog = true
            state.planeNameInput = ""
            state.planes3D.add(plane)
            state.lineTracesNarys.add(base.copy(parent = plane, parentId = plane.id))
            state.lineTracesBokorys.add(traceBokorys.copy(parent = plane, parentId = plane.id))

            println("✅ Rovina vytvořena: $plane")

            setProjectionPhase("narys_start", state)
            state.mongeMode = DrawingModeMonge.NARYS
            resetPlaneConstruction(state)
            repeatCons(state)

// 🔄 Reset
            state.selectedLineForParallelPlanePudorys= null
            state.selectedSegmentForParallelPudorys = null
            state.selectedLinesPudorys.clear()
            state.selectedSegmentsPudorys.clear()
            resetStavu(state)
        }
    }
}