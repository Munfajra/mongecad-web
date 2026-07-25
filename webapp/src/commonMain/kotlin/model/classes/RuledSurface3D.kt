package model.classes
import androidx.compose.ui.graphics.Color
import model.Offset3D
import model.UNASSIGNED_INDEX
import utils.UUID
enum class RuledSurfaceDirectrixKind {
    LINE,
    CURVE,
    CONIC,
    SPHERE,
}
data class RuledSurfaceDirectrixRef(
    val objectId: String,
    val kind: RuledSurfaceDirectrixKind,
)
/** Nezávislá kopie geometrie řídicí křivky v okamžiku vytvoření plochy. */
data class RuledSurfaceDirectrixSnapshot(
    val reference: RuledSurfaceDirectrixRef,
    val components: List<List<Offset3D>>,
    val closed: Boolean,
    val line: Line3D? = null,
    val conic: ConicSection3D? = null,
    val sphereCenter: Offset3D? = null,
    val sphereRadius: Float? = null,
    val ellipseArcParams: Pair<Float, Float>? = null,
    val ellipseArcEnds: Pair<Offset3D, Offset3D>? = null,
    val parabolaArcParams: Pair<Float, Float>? = null,
    val parabolaArcEnds: Pair<Offset3D, Offset3D>? = null,
    val hyperbolaArcParams: Pair<Pair<Float, Float>?, Pair<Float, Float>?>? = null,
    val hyperbolaArcEnds: Pair<Pair<Offset3D, Offset3D>?, Pair<Offset3D, Offset3D>?>? = null,
)
/**
 * Přímková plocha určená třemi řídicími křivkami. U obecné plochy se výsledná
 * dvojice krajních křivek vybírá geometricky ze všech tří referencí; názvy polí
 * first/second proto popisují uložené kandidáty, ne pořadí kliknutí ani pevný
 * ořez.
 *
 * Obecnou přímkovou plochu určuje [thirdDirectrix]. U konoidu je třetí
 * řídicí křivka nevlastní a nahrazuje ji [directorPlaneId]; tvořicí přímky
 * jsou pak rovnoběžné s touto rovinou. Právě jedna z těchto dvou hodnot musí
 * být vyplněná.
 *
 * [generatorCount] ovlivňuje pouze počet vykreslených tvořic. Definiční
 * geometrie plochy a její budoucí průniky se odvozují z uložených snapshotů
 * řídicích objektů, nikoli z aktuální hustoty ani pozdější existence zdrojů.
 *
 * Obrysy jsou odvozené křivky specifické pro jednotlivé pohledy. Jejich ID
 * odkazují na CurvePudorys, CurveNarys, CurveBokorys a CurveAxo.
 */
data class RuledSurface3D(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "φ",
    // Dvě z trojice uložených řídicích objektů. U kulového konoidu je druhým
    // objektem koule, ze které se odvozují řezy rovnoběžné s řídicí rovinou.
    val firstBoundaryDirectrix: RuledSurfaceDirectrixRef,
    val secondBoundaryDirectrix: RuledSurfaceDirectrixRef,
    // Obecná plocha: třetí člen trojice kandidátů.
    val thirdDirectrix: RuledSurfaceDirectrixRef? = null,
    val directrixOrderVersion: Int = 1,
    // Konoid: řídicí rovina nahrazující nevlastní třetí řídicí křivku.
    val directorPlaneId: String? = null,
    // Výpočet plochy po vytvoření nezávisí na existenci původních objektů.
    var directrixSnapshots: List<RuledSurfaceDirectrixSnapshot> = emptyList(),
    var directorPlaneEquation: PlaneEquation? = null,
    var generatorCount: Int = 16,
    // Ořez tvořic. Podíl 0..1 běží po tvořici od první krajní řídicí křivky (0)
    // ke druhé (1). Strana bez ořezu se místo podílu prodlužuje o absolutní
    // přesah za řídicí křivku v jednotkách výkresu.
    var generatorTrimStartEnabled: Boolean = true,
    var generatorTrimEndEnabled: Boolean = true,
    var generatorTrimStart: Float = 0f,
    var generatorTrimEnd: Float = 1f,
    var generatorOverhangStart: Float = 100f,
    var generatorOverhangEnd: Float = 100f,
    var color: Color = Color.Black,
    var wireWidth: Float = 1.5f,
    var fillFaces: Boolean = false,
    var outlineCurveIdPudorys: String? = null,
    var outlineCurveIdNarys: String? = null,
    var outlineCurveIdBokorys: String? = null,
    var outlineCurveIdAxo: String? = null,
    var outlineGeometryVersion: Int = 6,
    // Tvořice materializované jako skutečné Line3D (ořez drží customTrimRange).
    var generatorLineIds: List<String> = emptyList(),
    var generatorLinesVersion: Int = 0,
    val creationIndex: Long = UNASSIGNED_INDEX,
    var show: Boolean = true,
) {
    init {
        require(firstBoundaryDirectrix.objectId != secondBoundaryDirectrix.objectId) {
            "Krajní řídicí křivky přímkové plochy musí být různé."
        }
        require((thirdDirectrix != null) xor (directorPlaneId != null)) {
            "Přímková plocha musí mít právě třetí řídicí křivku, nebo řídicí rovinu."
        }
        require(thirdDirectrix == null || thirdDirectrix.objectId !in setOf(
            firstBoundaryDirectrix.objectId,
            secondBoundaryDirectrix.objectId,
        )) {
            "Třetí řídicí křivka musí být odlišná od krajních řídicích křivek."
        }
        require(generatorCount >= 0) {
            "Počet zobrazených tvořic nesmí být záporný."
        }
        val refs = listOfNotNull(firstBoundaryDirectrix, secondBoundaryDirectrix, thirdDirectrix)
        if (refs.any { it.kind == RuledSurfaceDirectrixKind.SPHERE }) {
            require(
                directorPlaneId != null && thirdDirectrix == null &&
                    refs.count { it.kind == RuledSurfaceDirectrixKind.LINE } == 1 &&
                    refs.count { it.kind == RuledSurfaceDirectrixKind.SPHERE } == 1
            ) {
                "Kulový konoid musí být určen jednou přímkou, jednou koulí a řídicí rovinou."
            }
        }
    }
}
