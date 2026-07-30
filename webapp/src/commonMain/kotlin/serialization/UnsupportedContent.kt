package serialization

import model.ProjectionMode
import state.MongeState

/**
 * Kontrola, jestli načtený výkres nesahá mimo to, co webová verze umí.
 *
 * Objekty samotné už web zvládá v celém rozsahu Mongeova promítání – kužely,
 * válce, koule, tělesa, rotační i přímkové plochy, průniky a výplně –, takže
 * se na ně nevaruje. Zůstává jen to, co web opravdu nemá: jiná promítání
 * (axonometrie, kótované promítání) a konstrukce vzniklé přímo v axonometrii.
 */
fun unsupportedContentWarnings(state: MongeState): List<String> {
    val warnings = mutableListOf<String>()

    when (state.projectionMode) {
        ProjectionMode.AXO -> warnings += "axonometrické promítání"
        ProjectionMode.KOTO -> warnings += "kótované promítání"
        ProjectionMode.PLANE, ProjectionMode.MONGE -> Unit
    }

    val axoConstructions =
        state.axoOverlayPoints.size + state.axoOverlayLines.size + state.axoOverlaySegments.size
    if (axoConstructions > 0) {
        warnings += if (axoConstructions == 1) "konstrukce v axonometrii"
        else "$axoConstructions konstrukcí v axonometrii"
    }

    return warnings
}

/** Text varování do dialogu, nebo null, když je výkres plně podporovaný. */
fun unsupportedContentMessage(state: MongeState): String? {
    val warnings = unsupportedContentWarnings(state)
    if (warnings.isEmpty()) return null

    return buildString {
        append("Výkres obsahuje části, které webová verze neumí zobrazit:\n\n• ")
        append(warnings.joinToString("\n• "))
        append("\n\nVýkres se otevře, ale tyhle části se nevykreslí a nepůjdou upravovat. ")
        append("Pokud ho tady uložíte, můžete o ně přijít – pro plnou podporu ")
        append("použijte desktopovou verzi MongeCAD.")
    }
}
