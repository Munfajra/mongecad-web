package monge.input.intersections

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import model.Mongeobjects
import state.MongeState

/**
 * Desktop pouští výpočet na vlastním vlákně a do stavu zapisuje přes EDT.
 * Wasm je jednovláknový, takže vlákno ani EDT nemáme – místo toho se výpočet
 * odloží do další smyčky událostí. Modální LoadingDialog se díky tomu stihne
 * vykreslit dřív, než dlouhý výpočet zabere UI, a zároveň mezitím blokuje
 * vstup, takže do stavu nezapisuje nikdo jiný.
 */
private val intersectionScope = CoroutineScope(Dispatchers.Main)

/**
 * Vstupní bod nástroje PRŮNIK. Volá se z [handleClick] **po** běhu `selection(state)`,
 * takže už ví, co uživatel tímto klikem označil.
 *
 * Princip: postupně sbírá označené objekty do bufferu [MongeState.intersectionPicks]
 * (1. klik = první operand, 2. klik = druhý). Buffer přežívá i exkluzivní výběr,
 * takže nezáleží, že druhý klik předchozí výběr zruší. Po dvou různých objektech
 * se zavolá [dispatchIntersection] a buffer se vyprázdní.
 *
 * Klik do prázdna (nic neoznačeno) bere jako zrušení rozpracovaného průniku.
 */
fun handleIntersectionClick(state: MongeState) {
    if (state.drawobjects != Mongeobjects.INTERSECTION) return
    if (state.intersectionComputing) return          // předchozí průnik se ještě počítá

    val selected = gatherSelectedOperands(state)

    // klik do prázdna → reset rozpracovaného výběru
    if (selected.isEmpty()) {
        if (state.intersectionPicks.isNotEmpty()) {
            state.intersectionPicks.clear()
            state.consInfo.value = "Průnik: vyber první objekt"
        }
        return
    }

    // přidej nově označené objekty (zachová 1. operand i přes exkluzivní výběr)
    for (op in selected) {
        if (state.intersectionPicks.none { it.id == op.id }) {
            state.intersectionPicks.add(op)
        }
    }

    if (state.intersectionPicks.size < 2) {
        state.consInfo.value =
            "Průnik: vyber druhý objekt (1. = ${state.intersectionPicks.first().label})"
        return
    }

    val a = state.intersectionPicks[0]
    val b = state.intersectionPicks[1]
    state.intersectionPicks.clear()

    state.intersectionComputing = true
    intersectionScope.launch {
        // pusť rekompozici k slovu, ať se LoadingDialog stihne vykreslit
        yield()
        try {
            dispatchIntersection(a, b, state)
            // Occlusion výplní (rozřezání siluet + Path.op) je jinak nejdražší část
            // a běžela by až při prvním překreslení → předpočítej ji rovnou tady.
            draw.mongescreen.fills.warmOcclusionCache(state)
        } catch (t: Throwable) {
            println("❌ Průnik selhal: ${t.message}")
            state.consInfo.value = "Průnik se nepodařilo spočítat."
        } finally {
            state.intersectionComputing = false
        }
    }
}
