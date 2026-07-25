package monge.input.intersections

import model.Mongeobjects
import state.MongeState

/**
 * Dva po sobě jdoucí kliky uloží operandy průniku. Webové kombinace
 * přímka/rovina jsou okamžité, takže není potřeba JVM pracovní vlákno.
 */
fun handleIntersectionClick(state: MongeState) {
    if (state.drawobjects != Mongeobjects.INTERSECTION || state.intersectionComputing) return

    val selected = gatherSelectedOperands(state)
    if (selected.isEmpty()) {
        if (state.intersectionPicks.isNotEmpty()) {
            state.intersectionPicks.clear()
            state.consInfo.value = "Průnik: vyberte první objekt."
        }
        return
    }

    selected.forEach { operand ->
        if (state.intersectionPicks.none { it.id == operand.id }) {
            state.intersectionPicks.add(operand)
        }
    }

    if (state.intersectionPicks.size < 2) {
        state.consInfo.value =
            "Průnik: vyberte druhý objekt (1. = ${state.intersectionPicks.first().label})."
        return
    }

    val first = state.intersectionPicks[0]
    val second = state.intersectionPicks[1]
    state.intersectionPicks.clear()

    state.intersectionComputing = true
    try {
        dispatchIntersection(first, second, state)
    } catch (error: Throwable) {
        state.consInfo.value = "Průnik se nepodařilo spočítat."
        println("Průnik selhal: ${error.message}")
    } finally {
        state.intersectionComputing = false
    }
}
