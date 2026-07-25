package monge.input.selection

import draw.mongescreen.labels.clearSelection
import model.Mongeobjects
import model.ProjectionMode
import model.classes.Segment2DProjection
import model.classes.SegmentsBokorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import state.MongeState

fun selection (state: MongeState)
{
    val before = snapshotSelection(state)
    val isKoto = state.projectionMode == ProjectionMode.KOTO
    val isAxo = state.projectionMode == ProjectionMode.AXO

    // Při tvorbě kulového konoidu vybírá obrys koule z plátna přímo
    // kulovou plochu, nikoli jen její 2D kuželosečku.
    if (handleRuledSurfaceSphereCanvasSelection(state)) return

    //označení bodů půdorys
    handleSelectionPudorysPoint(state)
    // části rotačních ploch a dalších plošných objektů vybírají celý útvar
    // (i v nástroji PRŮNIK, aby šel hranol/jehlan/rotační plocha vybrat jako operand)
    if (state.drawobjects == Mongeobjects.NONE || state.drawobjects == Mongeobjects.INTERSECTION) {
        val sorSelectedViaPart = handleSelectionSolidOfRevolutionParts(state)
        if (sorSelectedViaPart) return
        val compositeSelectedViaPart = handleSelectionCompositeObjectParts(state)
        if (compositeSelectedViaPart) return
    }
    if (isAxo){
        handleSelectionBokorysPoint(state)
        handleSelectionBokorysLine(state)
        handleSelectionAxoConic(state)
        handleSelectionAOPoint(state)
        handleSelectionAOLine(state)
        handleSelectionAOSegment(state)
        handleSelectionBokorysSegment(state)
        handleSelectionAxoPoint(state)
        handleSelectionAxoLine(state)
        handleSelectionAxoSegment(state)
        handleSelectionAxoOverlayArc(state)
        handleSelectionBokorysArc(state)
    }
    //označení bodů nárys
    if (!isKoto) {
        handleSelectionNarysPoint(state)
    }
    //označení pomocných bodů
    handleSelectionAidPoint(state)
    //označení úseček v půdorysu
    handleSelectionPudorysSegment(state)
    //označení úseček v nárysu
    if (!isKoto) {
        handleSelectionNarysSegment( state)
        //označení přímek nárys
        handleSelectionNarysLine(state)
    }
    //označení přímek v půdorysu
    handleSelectionPudorysLine(state)
    //oblouky
    handleSelectionPudorysArc(state)
    handleSelectionNarysArc(state)



    //kružnice
    handleSelectionPudorysCircle(state)
    if (!isKoto) {
        handleSelectionNarysCircle(state)
    }
    //křivky (snap je nepřesný → nejnižší priorita ve výběru)
    val sorSelectedViaCurve = handleSelectionCurves(state)
    val after = snapshotSelection(state)
    if (state.drawobjects == Mongeobjects.CYLINDER) return
    // výběr rotační plochy přes křivku je vícegrupový – nesmí ho rozbít exkluzivní arbitráž
    if (sorSelectedViaCurve) return

    if (!state.isShiftPressed) {
        val changed = whichGroupChanged(before, after)
        if (changed == null) {
            // klik do prázdna / beze změny → vše zrušit
            clearSelection(state)
        } else {
            // exkluzivní výběr – nech jen skupinu, která se změnila
            clearAllExcept(state, changed)
        }
    }
}

fun MongeState.isConicArcSelectionMode(): Boolean =
    drawobjects == Mongeobjects.CONICARC || drawobjects == Mongeobjects.CONICARCAS

enum class CylinderPhase { IDLE, PICK_CONIC, PICK_DIRECTION, PICK_PLANE, PICK_CONIC_PERP, PICK_CENTER_PERP }
fun SegmentsPudorys.selectionId(): String =
    (this as? Segment2DProjection)?.id
        ?: this.parent?.id
        ?: System.identityHashCode(this).toString()

fun SegmentsNarys.selectionId(): String =
    (this as? Segment2DProjection)?.id
        ?: this.parent?.id
        ?: System.identityHashCode(this).toString()
fun SegmentsBokorys.selectionId(): String =
    (this as? Segment2DProjection)?.id
        ?: this.parent?.id
        ?: System.identityHashCode(this).toString()

private data class SelectionSig(
    val aop: Set<String>,val aol: Set<String>,
    val pP: Set<String>, val pN: Set<String>,
     val pA: Set<String>,
    val pB: Set<String>,
    val aL: Set<String>,
    val sP: Set<String>, val sN: Set<String>,
    val sB: Set<String>,
    val lP: Set<String>, val lN: Set<String>,
    val lB: Set<String>,
    val cP: Set<String>, val cN: Set<String>,
    val cB: Set<String>, val cA: Set<String>,
    val planes: Set<String>, val aids: Set<String>,
    val cirN: Set<String>, val cirP: Set<String>,
    val sphr: Set<String>, val cyl: Set<String>,
    val cone: Set<String>, val solid: Set<String>, val poly: Set<String>,
    val aP: Set<String>, val aN: Set<String>,
    val aB: Set<String>, val aAO: Set<String>,
    val aS: Set<String>, val aSeg: Set<String>,
    val cur: Set<String>,
    val ruled: Set<String>,

)

private fun snapshotSelection(state: MongeState) = SelectionSig(
    aL = state.selectedLinesAxo.map { it.id }.toSet(),
    aSeg = state.selectedSegmentsAxo.map { it.id }.toSet(),
    pP = state.selectedPointsPudorys.map { it.id }.toSet(),
    pA = state.selectedPointsAxo.map { it.id }.toSet(),
    pB = state.selectedPointsBokorys.map { it.id }.toSet(),
    pN = state.selectedPointsNarys.map { it.id }.toSet(),
    sP = state.selectedSegmentsPudorys.map { it.selectionId() }.toSet(),
    sN = state.selectedSegmentsNarys.map { it.selectionId() }.toSet(),
    sB = state.selectedSegmentsBokorys.map { it.selectionId()}.toSet(),
    lP = state.selectedLinesPudorys.map { it.id }.toSet(),
    lN = state.selectedLinesNarys.map { it.id }.toSet(),
    lB = state.selectedLinesBokorys.map { it.id }.toSet(),
    cP = state.selectedConicsPudorys.map { it.id }.toSet(),
    cN = state.selectedConicsNarys.map { it.id }.toSet(),
    cB = state.selectedConicsBokorys.map { it.id }.toSet(),
    cA = state.selectedConicsAxo.map { it.id }.toSet(),
    planes = state.selectedPlanes.map { it.id }.toSet(),
    aids = state.selectedAidPointIds.toSet(),
    aop = state.selectedAOPointIds.toSet(),
    cirN = state.selectedCirclesNarys.map{it.id}.toSet(),
    cirP = state.selectedCirclesPudorys.map{it.id}.toSet(),
    sphr = state.selectedSpheres3D.map{it.id}.toSet(),
    cyl = state.selectedCylinder.map{it.id}.toSet(),
    cone = state.selectedCone.map{it.id}.toSet(),
    solid = state.selectedSegmentSolids3D.map { it.id }.toSet(),
    poly =state.selectedPolygons.map{it.id}.toSet(),
    aP = state.selectedArcsPudorys.map{it.id}.toSet(),
    aN = state.selectedArcsNarys.map{it.id}.toSet(),
    aB = state.selectedArcsBokorys.map{it.id}.toSet(),
    aAO = state.selectedArcsAxoOverlay.map{it.id}.toSet(),
    aol = state.selectedAOLineIds.toSet(),
    aS =state.selectedAOSegIds.toSet(),
    cur = listOfNotNull(
        state.selectedCurve3DId,
        state.selectedCurvePudorysId,
        state.selectedCurveNarysId,
        state.selectedCurveBokorysId,
        state.selectedCurveAxoId
    ).toSet(),
    ruled = setOfNotNull(state.selectedRuledSurfaceId),


)
enum class SelGroup {
    PP,PA, PN,PB, SP, SN,SB,AL,ASEG,
    LP, LN,LB, CP, CN,CB,CA, C2D, PLANES,
    AIDS,AOP,AOL, CIRN, CIRP, CIRC,
    SPHR, CYL, CONE, SOLID, POLY, RULED, ARCP, ARCN, ARCAO,
    ARCB, ARC, AS, CURVE
}


private fun whichGroupChanged(before: SelectionSig, after: SelectionSig): SelGroup? {
    fun ch(b: Set<String>, a: Set<String>) = b != a
    fun grew(b: Set<String>, a: Set<String>) = a.size > b.size

    // ✅ ABSOLUTNÍ PRIORITA: aidpointy
    val ppchanged = ch(before.pP,after.pP)
    if (ppchanged && grew(before.pP, after.pP)) return SelGroup.PP
    if (ppchanged) return SelGroup.PP


    val aidsChanged = ch(before.aids, after.aids)
    if (aidsChanged && grew(before.aids, after.aids)) return SelGroup.AIDS
    if (aidsChanged) return SelGroup.AIDS
    val aopChanged = ch(before.aop, after.aop)
    if (aopChanged && grew(before.aop, after.aop)) return SelGroup.AOP
    if (aopChanged) return SelGroup.AOP
    val aolChanged = ch(before.aol, after.aol)
    if (aolChanged && grew(before.aol, after.aol)) return SelGroup.AOL
    val asChanged = ch(before.aS, after.aS)
    if (asChanged && grew(before.aS, after.aS)) return SelGroup.AS
    // Pouhé zmenšení AO výběru vyhodnoť až po hledání nově přidané skupiny níže.
    // Jinak první klik na jiný typ objektu ponechá prázdnou starou AO skupinu
    // a právě přidaný objekt zase odstraní.

    val arcspChanged = ch(before.aP,after.aP)
    val arcsnChanged = ch(before.aN,after.aN)
    val arcsbChanged = ch(before.aB,after.aB)
    val arcsaoChanged = ch(before.aAO,after.aAO)
    if (arcsnChanged || arcspChanged || arcsbChanged || arcsaoChanged)
    {val grewBefore = before.aN.size + before.aP.size + before.aB.size + before.aAO.size
        val grewAfter = after.aN.size + after.aP.size + after.aB.size + after.aAO.size
        if (grewAfter > grewBefore) return SelGroup.ARC

    }

    // --- 3D tělesa/plošky ---
    val sphrChanged = ch(before.sphr, after.sphr)
    if (sphrChanged && grew(before.sphr, after.sphr)) return SelGroup.SPHR

    val coneChanged = ch(before.cone, after.cone)
    if (coneChanged && grew(before.cone, after.cone)) return SelGroup.CONE

    val solidChanged = ch(before.solid, after.solid)
    if (solidChanged && grew(before.solid, after.solid)) return SelGroup.SOLID

    val cylChanged = ch(before.cyl, after.cyl)
    if (cylChanged && grew(before.cyl, after.cyl)) return SelGroup.CYL

    val polyChanged = ch(before.poly, after.poly)
    if (polyChanged && grew(before.poly, after.poly)) return SelGroup.POLY

    val ruledChanged = ch(before.ruled, after.ruled)
    if (ruledChanged && grew(before.ruled, after.ruled)) return SelGroup.RULED

    // --- coniky (C2D) ---
    val cpChanged = ch(before.cP, after.cP)
    val cnChanged = ch(before.cN, after.cN)
    val cbChanged = ch(before.cB, after.cB)
    val caChanged = ch(before.cA, after.cA)
    if ((cpChanged || cnChanged || cbChanged || caChanged) && after.sphr.isNotEmpty()) {
        return SelGroup.SPHR
    }
    if (cpChanged || cnChanged || cbChanged || caChanged) {
        val grewBefore = before.cP.size + before.cN.size + before.cA.size + before.cB.size
        val grewAfter  = after.cP.size + after.cN.size + after.cA.size + after.cB.size
        if (grewAfter > grewBefore) return SelGroup.C2D
    }
    // --- kružnice (CIRC) ---
    val cirPChanged = ch(before.cirP, after.cirP)
    val cirNChanged = ch(before.cirN, after.cirN)
    if (cirPChanged || cirNChanged) {
        val grewBefore = before.cirP.size + before.cirN.size
        val grewAfter  = after.cirP.size + after.cirN.size
        if (grewAfter > grewBefore) return SelGroup.CIRC
    }
    // Ostatní páry (pořadí může zůstat)
    val pairs = listOf(
        SelGroup.PP     to (before.pP     to after.pP),
        SelGroup.PA     to (before.pA     to after.pA),
        SelGroup.PN     to (before.pN     to after.pN),
        SelGroup.PB     to (before.pB     to after.pB),
        SelGroup.AIDS   to (before.aids   to after.aids),
        SelGroup.AOP    to (before.aop    to after.aop),
        SelGroup.AOL    to (before.aol    to after.aol),
        SelGroup.SP     to (before.sP     to after.sP),
        SelGroup.SN     to (before.sN     to after.sN),
        SelGroup.SB     to (before.sB     to after.sB),
        SelGroup.LP     to (before.lP     to after.lP),
        SelGroup.LN     to (before.lN     to after.lN),
        SelGroup.LB     to (before.lB     to after.lB),
        SelGroup.PLANES to (before.planes to after.planes),
        SelGroup.SPHR   to (before.sphr   to after.sphr),
        SelGroup.CYL    to (before.cyl    to after.cyl),
        SelGroup.CONE   to (before.cone   to after.cone),
        SelGroup.SOLID  to (before.solid  to after.solid),
        SelGroup.POLY   to (before.poly   to after.poly),
        SelGroup.RULED  to (before.ruled to after.ruled),
        SelGroup.ARCP   to (before.aP     to after.aP),
        SelGroup.ARCN   to (before.aN     to after.aN),
        SelGroup.ARCB   to (before.aB     to after.aB),
        SelGroup.ARCAO  to (before.aAO    to after.aAO),
        SelGroup.AS     to (before.aS     to after.aS),
        SelGroup.ASEG   to (before.aSeg   to after.aSeg),
        SelGroup.AL     to (before.aL     to after.aL),
        SelGroup.CURVE  to (before.cur    to after.cur)
    )
    pairs.firstOrNull { (_, s) -> s.second.size > s.first.size && s.first != s.second }?.let { return it.first }
    pairs.firstOrNull { (_, s) -> s.first != s.second }?.let { return it.first }
    if (cpChanged || cnChanged || cbChanged || caChanged) return SelGroup.C2D
    if (cirPChanged || cirNChanged) return SelGroup.CIRC
    if (sphrChanged) return SelGroup.SPHR
    if (coneChanged) return SelGroup.CONE
    if (cylChanged)  return SelGroup.CYL
    if (polyChanged) return SelGroup.POLY
    if (ruledChanged) return SelGroup.RULED

    return null
}


private fun clearAllExcept(state: MongeState, keep: SelGroup) {
    if (keep != SelGroup.ASEG) state.selectedSegmentsAxo.clear()
    if (keep != SelGroup.AL) state.selectedLinesAxo.clear()
    if (keep != SelGroup.PA) state.selectedPointsAxo.clear()
    if (keep != SelGroup.PP) state.selectedPointsPudorys.clear()
    if (keep != SelGroup.PN) state.selectedPointsNarys.clear()
    if (keep != SelGroup.PB) state.selectedPointsBokorys.clear()
    if (keep != SelGroup.SP) state.selectedSegmentsPudorys.clear()
    if (keep != SelGroup.SN) state.selectedSegmentsNarys.clear()
    if (keep != SelGroup.SB) state.selectedSegmentsBokorys.clear()
    if (keep != SelGroup.LP) state.selectedLinesPudorys.clear()
    if (keep != SelGroup.LN) state.selectedLinesNarys.clear()
    if (keep != SelGroup.LB) state.selectedLinesBokorys.clear()

    if (keep != SelGroup.ARCP && keep != SelGroup.ARC) state.selectedArcsPudorys.clear()
    if (keep != SelGroup.ARCN && keep != SelGroup.ARC) state.selectedArcsNarys.clear()
    if (keep != SelGroup.ARCB && keep != SelGroup.ARC) state.selectedArcsBokorys.clear()
    if (keep != SelGroup.ARCAO && keep != SelGroup.ARC) state.selectedArcsAxoOverlay.clear()
    if (keep != SelGroup.CP  && keep != SelGroup.C2D && keep != SelGroup.SPHR) state.selectedConicsPudorys.clear()
    if (keep != SelGroup.CN  && keep != SelGroup.C2D && keep != SelGroup.SPHR) state.selectedConicsNarys.clear()
    if (keep != SelGroup.CB  && keep != SelGroup.C2D && keep != SelGroup.SPHR) state.selectedConicsBokorys.clear()
    if (keep != SelGroup.CA  && keep != SelGroup.C2D && keep != SelGroup.SPHR) state.selectedConicsAxo.clear()

    if (keep != SelGroup.PLANES) state.selectedPlanes.clear()
    if (keep != SelGroup.AIDS) state.selectedAidPointIds.clear()
    if (keep != SelGroup.AOL) state.selectedAOLineIds.clear()
    if (keep != SelGroup.AOP) state.selectedAOPointIds.clear()
    if (keep != SelGroup.AS) state.selectedAOSegIds.clear()
    // Když držíme SPHR (sféru), ponech i její kuželosečkové průměty.
    if (keep != SelGroup.CIRN && keep != SelGroup.CIRC && keep != SelGroup.SPHR) state.selectedCirclesNarys.clear()
    if (keep != SelGroup.CIRP && keep != SelGroup.CIRC && keep != SelGroup.SPHR) state.selectedCirclesPudorys.clear()

    if (keep != SelGroup.SPHR) state.selectedSpheres3D.clear()
    if (keep != SelGroup.CYL)  state.selectedCylinder.clear()
    if (keep != SelGroup.CONE) state.selectedCone.clear()
    if (keep != SelGroup.SOLID) state.selectedSegmentSolids3D.clear()
    if (keep != SelGroup.POLY) state.selectedPolygons.clear()
    if (keep != SelGroup.POLY) state.selectedPlanePolygons2D.clear()
    if (keep != SelGroup.RULED) state.selectedRuledSurfaceId = null
    if (keep != SelGroup.CURVE) clearCurveSelection(state)
}
