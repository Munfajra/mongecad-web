package monge.input.lines.directionHandlers.lines

import utils.System
import androidx.compose.ui.geometry.Offset
import draw.mongescreen.labels.clearSelection
import model.Mongeobjects
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

private const val DIRECTION_EPS = 1e-6f

// Navazující (druhý) průmět – po vypnutí modifikátoru se vrací do normální konstrukce průmětů.
// Rozpracovaná pending data prvního průmětu musí zůstat zachována.
private val chainedLineSelectionPhaseReverts = mapOf(
    "parallel_line_point_selection_narys_pudorys_start" to "projection_line_start_narys",
    "orthogonal_line_point_selection_narys_pudorys_start" to "projection_line_start_narys",
    "parallel_line_point_selection_pudorys_narys_start" to "projection_line_start_pudorys",
    "orthogonal_line_point_selection_pudorys_narys_start" to "projection_line_start_pudorys",
)

// První průmět – po vypnutí modifikátoru se vrací na začátek konstrukce.
private val startLineSelectionPhaseReverts = mapOf(
    "parallel_line_point_selection_narys_start" to "narys_start",
    "orthogonal_line_point_selection_narys_start" to "narys_start",
    "parallel_line_point_selection_pudorys_start" to "pudorys_start",
    "orthogonal_line_point_selection_pudorys_start" to "pudorys_start",
)

// Normální fáze rozpracovaného navazujícího průmětu: první průmět už je zadaný
// (drží se v pendingDirection*/pendingZ…), takže při vypnutí modifikátoru tato
// data NESMÍ být smazána, jinak by druhý průmět nešlo dokončit.
private val chainedConstructionPhasesInProgress = setOf(
    "projection_line_start_pudorys",
    "projection_line_start_pudorys_dir",
    "projection_line_start_narys",
    "projection_line_narys_dir",
    "special_case_point_in_narys",
    "special_case_point_in_pudorys",
)

/**
 * Když uživatel během konstrukce přímky vypne modifikátor (rovnoběžnost/kolmost),
 * fáze zůstala nastavená na "..._line_point_selection_...", takže dispatcher pořád
 * routoval do rovnoběžkového/kolmicového handleru a nutil znovu vybrat vzor směru.
 * Tato funkce fázi vrátí zpět na odpovídající normální fázi konstrukce průmětů.
 *
 * @return true, pokud šlo o navazující (chained) druhý průmět – volající pak NESMÍ
 *         smazat rozpracovaná pending data prvního průmětu (pendingDirection*, pendingZ…).
 */
fun revertLineSelectionPhaseOnModifierOff(state: MongeState): Boolean {
    if (state.drawobjects != Mongeobjects.LINES) return false

    // Vyčisti dosud zvolený vzor směru, aby needs...Pattern znovu nezasáhl.
    state.selectedLineForParallelNarys = null
    state.selectedLineForParallelPudorys = null
    state.selectedSegmentForParallelNarys = null
    state.selectedSegmentForParallelPudorys = null

    chainedLineSelectionPhaseReverts[state.projectionPhase]?.let { normalPhase ->
        setProjectionPhase(normalPhase, state)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        state.triggerRedraw++
        return true
    }
    startLineSelectionPhaseReverts[state.projectionPhase]?.let { normalPhase ->
        setProjectionPhase(normalPhase, state)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        state.triggerRedraw++
        return false
    }
    // Už jsme v normální fázi navazujícího průmětu (např. parallel/kolmá přímka
    // v prvním průmětu doběhla) – fázi neměň, jen zachovej rozpracovaná pending data.
    if (state.projectionPhase in chainedConstructionPhasesInProgress) {
        state.triggerRedraw++
        return true
    }
    return false
}

private fun effectiveDirection(base: Offset, orthogonal: Boolean): Offset =
    if (orthogonal) Offset(-base.y, base.x) else base

private fun rememberDirectionPattern(state: MongeState) {
    clearSelection(state)
    state.selectedLineIdsPudorys.clear()
    state.selectedLineIdsNarys.clear()
    state.selectedLineIdsBokorys.clear()
    state.selectedLineIdsAxo.clear()
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    // Právě jsme zvolili vzor směru – nedovol, aby druhý dispatcher ve stejném
    // kliknutí přímku rovnou i umístil (jinak by výběr + umístění splynuly do 1 kliku).
    state.lineDirectionPatternJustPicked = true
    state.consInfo.value = "Umístěte přímku."
    state.triggerRedraw++
}

internal fun needsLineDirectionPatternNarys(state: MongeState): Boolean =
    state.selectedLineForParallelNarys == null &&
        state.selectedSegmentForParallelNarys == null &&
        state.drawobjects == model.Mongeobjects.LINES &&
        (
            state.projekcnityp == model.ProjectionType.SINGLE ||
                state.projekcnityp == model.ProjectionType.AUXILIARY ||
                state.projectionPhase in listOf(
                    "parallel_line_point_selection_narys_start",
                    "parallel_line_point_selection_narys_pudorys_start",
                    "orthogonal_line_point_selection_narys_start",
                    "orthogonal_line_point_selection_narys_pudorys_start"
                )
            )

internal fun needsLineDirectionPatternPudorys(state: MongeState): Boolean =
    state.selectedLineForParallelPudorys == null &&
        state.selectedSegmentForParallelPudorys == null &&
        state.drawobjects == model.Mongeobjects.LINES &&
        (
            state.projekcnityp == model.ProjectionType.SINGLE ||
                state.projekcnityp == model.ProjectionType.AUXILIARY ||
                state.projectionPhase in listOf(
                    "parallel_line_point_selection_pudorys_start",
                    "parallel_line_point_selection_pudorys_narys_start",
                    "orthogonal_line_point_selection_pudorys_start",
                    "orthogonal_line_point_selection_pudorys_narys_start"
                )
            )

internal fun tryPickLineDirectionNarys(state: MongeState, orthogonal: Boolean): Boolean {
    state.selectedLineForParallelNarys?.let { line ->
        state.pendingDirectionNarys = effectiveDirection(line.direction, orthogonal)
        return state.pendingDirectionNarys!!.getDistance() >= DIRECTION_EPS
    }
    state.selectedSegmentForParallelNarys?.let { segment ->
        val base = Offset(segment.end.x - segment.start.x, segment.end.z - segment.start.z)
        state.pendingDirectionNarys = effectiveDirection(base, orthogonal)
        return state.pendingDirectionNarys!!.getDistance() >= DIRECTION_EPS
    }

    val segment = state.snappedSegmentNarys ?: state.selectedSegmentsNarys.firstOrNull()
    if (segment != null) {
        val base = Offset(segment.end.x - segment.start.x, segment.end.z - segment.start.z)
        val direction = effectiveDirection(base, orthogonal)
        if (direction.getDistance() < DIRECTION_EPS) return false
        state.selectedSegmentForParallelNarys = segment
        state.pendingDirectionNarys = direction
        rememberDirectionPattern(state)
        println("Segment v nárysu označen jako vzor směru přímky.")
        return true
    }

    val line = state.snappedLineNarys ?: state.selectedLinesNarys.firstOrNull()
    if (line != null) {
        val direction = effectiveDirection(line.direction, orthogonal)
        if (direction.getDistance() < DIRECTION_EPS) return false
        state.selectedLineForParallelNarys = line
        state.pendingDirectionNarys = direction
        rememberDirectionPattern(state)
        println("Přímka '${line.name}' v nárysu označena jako vzor směru přímky.")
        return true
    }

    println("Neoznačena žádná přímka nebo úsečka pro směr přímky.")
    return false
}

internal fun tryPickLineDirectionPudorys(state: MongeState, orthogonal: Boolean): Boolean {
    state.selectedLineForParallelPudorys?.let { line ->
        state.pendingDirection = effectiveDirection(line.direction, orthogonal)
        return state.pendingDirection!!.getDistance() >= DIRECTION_EPS
    }
    state.selectedSegmentForParallelPudorys?.let { segment ->
        val base = Offset(segment.end.x - segment.start.x, segment.end.y - segment.start.y)
        state.pendingDirection = effectiveDirection(base, orthogonal)
        return state.pendingDirection!!.getDistance() >= DIRECTION_EPS
    }

    val segment = state.snappedSegmentPudorys ?: state.selectedSegmentsPudorys.firstOrNull()
    if (segment != null) {
        val base = Offset(segment.end.x - segment.start.x, segment.end.y - segment.start.y)
        val direction = effectiveDirection(base, orthogonal)
        if (direction.getDistance() < DIRECTION_EPS) return false
        state.selectedSegmentForParallelPudorys = segment
        state.pendingDirection = direction
        rememberDirectionPattern(state)
        println("Segment v půdorysu označen jako vzor směru přímky.")
        return true
    }

    val line = state.snappedLinePudorys ?: state.selectedLinesPudorys.firstOrNull()
    if (line != null) {
        val direction = effectiveDirection(line.direction, orthogonal)
        if (direction.getDistance() < DIRECTION_EPS) return false
        state.selectedLineForParallelPudorys = line
        state.pendingDirection = direction
        rememberDirectionPattern(state)
        println("Přímka '${line.name}' v půdorysu označena jako vzor směru přímky.")
        return true
    }

    println("Neoznačena žádná přímka nebo úsečka pro směr přímky.")
    return false
}
