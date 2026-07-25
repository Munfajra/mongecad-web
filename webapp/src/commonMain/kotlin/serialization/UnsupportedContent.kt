package serialization

import model.ProjectionMode
import state.MongeState

/**
 * Kontrola, jestli načtený výkres nesahá mimo to, co webová verze umí.
 *
 * Rýsování v rovině a průniky už web podporuje, proto se do varování
 * nezahrnují. Upozornění zůstává pro axonometrii, kótované promítání,
 * tělesa, rotační a přímkové plochy a další dosud nepřenesené konstrukce.
 */
fun unsupportedContentWarnings(state: MongeState): List<String> {
    val warnings = mutableListOf<String>()

    when (state.projectionMode) {
        ProjectionMode.AXO -> warnings += "axonometrické promítání"
        ProjectionMode.KOTO -> warnings += "kótované promítání"
        ProjectionMode.PLANE, ProjectionMode.MONGE -> Unit
    }

    fun note(count: Int, singular: String, plural: String) {
        if (count > 0) warnings += if (count == 1) singular else "$count $plural"
    }

    note(state.conicalSurfaces.size, "kužel", "kuželů")
    note(state.cylindricalSurfaces.size, "válec", "válců")
    note(state.spheres3D.size, "koule", "koulí")
    note(state.solidsOfRevolutionNarys.size + state.solidsOfRevolutionPudorys.size,
        "rotační plocha", "rotačních ploch")
    note(state.ruledSurfaces.size, "přímková plocha", "přímkových ploch")
    note(state.segmentSolids3D.size, "těleso z úseček", "těles z úseček")
    note(state.axoOverlayPoints.size + state.axoOverlayLines.size + state.axoOverlaySegments.size,
        "konstrukce v axonometrii", "konstrukcí v axonometrii")

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
