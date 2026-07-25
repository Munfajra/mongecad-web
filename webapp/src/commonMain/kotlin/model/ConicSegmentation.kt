package model

/**
 * Po částech stylovaná kuželosečka — jedna kuželosečka vykreslená jako víc
 * střídavých úseků, každý s vlastním [LineStyle] (typicky Solid=viditelně,
 * Dashed=skrytě), stejnou barvou a tloušťkou.
 *
 * Dvě formy:
 *  - [ConicSegmentation]  — per-view, hranice úseků v NATIVNÍM parametru daného
 *    průmětu (elipsa úhel θ, parabola/hyperbola parametr u). Tuhle formu čtou
 *    renderery ([draw.mongescreen.objects.conics]).
 *  - [ConicSegmentation3D] — view-nezávislá, hranice úseků jako 3D body. Tuhle
 *    formu drží auto-producenti a serializace; do per-view parametrů se PŘEPOČÍTÁ
 *    per průmět (kvůli nárysovému flipu z a rozdílu MONGE vs AXO nelze parametr
 *    jen kopírovat).
 *
 * Konvence:
 *  - `primary`   = elipsa (cyklicky, θ) / parabola (u) / hyperbola větev sX=+1.
 *  - `secondary` = jen hyperbola větev sX=-1; jinak null.
 *  - U elipsy musí úseky pokrýt celý rozsah cyklicky; u paraboly/hyperboly
 *    krajní hranice úseků zároveň slouží jako ořez extentu.
 */
data class ConicSegment(
    val start: Float,
    val end: Float,
    val style: LineStyle = LineStyle.Solid
)

data class ConicSegmentation(
    val primary: List<ConicSegment> = emptyList(),
    val secondary: List<ConicSegment>? = null
) {
    fun isEmpty(): Boolean = primary.isEmpty() && (secondary == null || secondary.isEmpty())
}

/** View-nezávislá forma: hranice úseků jako 3D body na kuželosečce. */
data class ConicSegment3D(
    val a: Offset3D,
    val b: Offset3D,
    val style: LineStyle = LineStyle.Solid
)

data class ConicSegmentation3D(
    val primary: List<ConicSegment3D> = emptyList(),
    val secondary: List<ConicSegment3D>? = null
) {
    fun isEmpty(): Boolean = primary.isEmpty() && (secondary == null || secondary.isEmpty())
}
