package monge.input.tools

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.AidPointLogical
import model.classes.NamedLineNarys
import model.classes.NamedLinePudorys
import model.classes.Segment2DProjection
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import model.classes.Trace2DProjection
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.dotProduct
import utils.getLogicalCursor

fun handleClickTransParallel(snappedPointLogical: Offset?, state: MongeState, cursorWorld: Offset) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursorWorld,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    val ctx: TransParallelContext = when {
        // PŮDORYS
        state.selectedLineForParallelPudorys is NamedLinePudorys || state.selectedSegmentForParallelPudorys != null ||
                state.selectedLinesPudorys.any { true } || state.selectedSegmentsPudorys.isNotEmpty() -> TransParallelContext(
            isPudorys = true,
            phaseStart = "pudorys_start",
            phaseTemp = "trans_parallel_temp_point_pudorys",
            phaseFinal = "trans_parallel_final_point_pudorys",
            rememberedLine = state.selectedLinesPudorys.firstOrNull { true }
                ?: state.selectedLineForParallelPudorys,
            rememberedSegment = state.selectedSegmentsPudorys.firstOrNull() as? Segment2DProjection
                ?: state.selectedSegmentForParallelPudorys as? Segment2DProjection,
            setSelectedLine = {
                when (it) {
                    is NamedLinePudorys -> state.selectedLineForParallelPudorys = it
                    is Trace2DProjection -> state.selectedLineForParallelPudorys = null
                    else -> println("⚠️ Nepodporovaný typ v setSelectedLine")
                }
            }
            ,
                    setSelectedSegment = { state.selectedSegmentForParallelPudorys = it as SegmentsPudorys },
            getDirection = {
                state.selectedLineForParallelPudorys?.direction
                    ?: state.selectedSegmentForParallelPudorys?.let {
                        Offset(it.end.x - it.start.x, it.end.y - it.start.y)
                    }
            },
            setPendingPoint = { state.pendingLinePointPudorys = it },
            setPendingDir = { state.pendingDirection = it },
            getPendingPoint = { state.pendingLinePointPudorys },
            getPendingDir = { state.pendingDirection },
            resetSelected = {
                state.selectedLineForParallelPudorys = null
                state.selectedSegmentForParallelPudorys = null
            },
            resetPhase = { setProjectionPhase("pudorys_start", state) },
            storeAidPoint = { state.aidPointsLogical += AidPointLogical(it.x, it.y, creationIndex = allocIndex(state)) }
        )

        // NÁRYS
        state.selectedLineForParallelNarys is NamedLineNarys || state.selectedSegmentForParallelNarys != null ||
                state.selectedLinesNarys.any { true } || state.selectedSegmentsNarys.isNotEmpty() -> TransParallelContext(
            isPudorys = false,
            phaseStart = "narys_start",
            phaseTemp = "trans_parallel_temp_point_narys",
            phaseFinal = "trans_parallel_final_point_narys",
            rememberedLine = state.selectedLinesNarys.firstOrNull { true }
                ?: state.selectedLineForParallelNarys,
            rememberedSegment = state.selectedSegmentsNarys.firstOrNull() as? Segment2DProjection
                ?: state.selectedSegmentForParallelNarys as? Segment2DProjection,
            setSelectedLine = { state.selectedLineForParallelNarys = it as NamedLineNarys },
            setSelectedSegment = { state.selectedSegmentForParallelNarys = it as SegmentsNarys },
            getDirection = {
                state.selectedLineForParallelNarys?.direction
                    ?: state.selectedSegmentForParallelNarys?.let {
                        Offset(it.end.x - it.start.x, it.end.z - it.start.z)
                    }
            },
            setPendingPoint = { state.pendingLinePointNarys = it },
            setPendingDir = { state.pendingDirectionNarys = it },
            getPendingPoint = { state.pendingLinePointNarys },
            getPendingDir = { state.pendingDirectionNarys },
            resetSelected = {
                state.selectedLineForParallelNarys = null
                state.selectedSegmentForParallelNarys = null
            },
            resetPhase = { setProjectionPhase("narys_start", state) },
            storeAidPoint = { state.aidPointsLogical += AidPointLogical(
                it.x,
                -it.y,
                creationIndex = allocIndex(state)
            )
            }
        )

        else -> {
            println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
            return
        }
    }


    if (state.projectionPhase !in listOf(ctx.phaseStart, ctx.phaseTemp, ctx.phaseFinal)) {
        state.projectionPhase = ctx.phaseStart
        updateConstructionInfo(state)
    }
    when (state.projectionPhase) {
        ctx.phaseStart -> {
            ctx.rememberedLine?.let { line ->
                when (line) {
                    is NamedLinePudorys -> {
                        ctx.setSelectedLine(line)
                        println("🟦 Přímka '${line.name}' vybrána jako vzor pro rovnoběžnou.")
                    }
                    is NamedLineNarys -> {
                        ctx.setSelectedLine(line)
                        println("🟦 Přímka '${line.name}' vybrána jako vzor pro rovnoběžnou.")
                    }
                    else -> {
                        println("⚠️ Neznámý typ přímky – přerušení.")
                        return
                    }
                }
            }
                ?: ctx.rememberedSegment?.let {
                ctx.setSelectedSegment(it)
                println("🟦 Úsečka vybrána jako vzor pro rovnoběžnou.")
            } ?: run {
                println("⚠️ Neoznačena žádná přímka ani úsečka – vyber jednu kliknutím.")
                return
            }

            state.projectionPhase = ctx.phaseTemp
            updateConstructionInfo(state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        }

        ctx.phaseTemp -> {
            val base = if (ctx.isPudorys) logical else Offset(logical.x, -logical.y)

            val rawDir = ctx.getDirection() ?: run {
                println("⚠️ Chybí směr – přerušení konstrukce.")
                ctx.resetSelected()
                ctx.resetPhase()
                return
            }

            val dir = when (state.constructionModifier) {
                ConstructionModifier.PARALLEL -> rawDir
                ConstructionModifier.ORTHOGONAL -> Offset(-rawDir.y, rawDir.x)
                else -> rawDir
            }

            ctx.setPendingPoint(base)
            ctx.setPendingDir(dir)
            state.projectionPhase = ctx.phaseFinal
            updateConstructionInfo(state)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            println("📐 Zvolen bod pro paralelu – dočasná přímka připravena")
        }

        ctx.phaseFinal -> {
            val A = ctx.getPendingPoint()
            val dir = ctx.getPendingDir()
            if (A == null || dir == null) {
                println("⚠️ Chybí data – přerušení konstrukce.")
                ctx.resetPhase()
                return
            }

            val len = dir.getDistance()
            if (len < 1e-6f) {
                println("⚠️ Směr je příliš krátký")
                return
            }

            val unit = Offset(dir.x / len, dir.y / len)
            val logicalFixed = if (ctx.isPudorys) logical else Offset(logical.x, -logical.y)
            val AP = logicalFixed - A
            val proj = A + unit * (AP.dotProduct(unit))

            state.deferSelectionUntil = System.currentTimeMillis() + 100
            ctx.storeAidPoint(proj)
            println("✅ Bod vložen na rovnoběžku: $proj")

            ctx.resetSelected()
            ctx.setPendingPoint(Offset.Unspecified)
            ctx.setPendingDir(Offset.Unspecified)
            ctx.resetPhase()
            commitSnapshot(state)
            updateConstructionInfo(state)
            repeatCons(state)
            resetStavu(state)
        }
    }
}
