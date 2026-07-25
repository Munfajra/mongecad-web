package monge.input.curves

import utils.withSuffixOnce
import dialogs.nameInput.withSuffixOnce
import serialization.commitSnapshot
import model.*
import model.classes.Curve3D
import model.classes.CurveAxo
import model.classes.CurveBokorys
import model.classes.CurveNarys
import model.classes.CurvePudRef
import model.classes.CurvePudorys
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.updateConstructionInfo

private fun findPudorysProjection(state: MongeState, p3: Point3D): Point3DPudorys? =
    state.pointsPudorys.firstOrNull { it.parent?.id == p3.id }

private fun findNarysProjection(state: MongeState, p3: Point3D): Point3DNarys? =
    state.pointsNarys.firstOrNull { it.parent?.id == p3.id }

private fun findBokorysProjection(state: MongeState, p3: Point3D): Point3DBokorys? =
    state.pointsBokorys.firstOrNull { it.parent?.id == p3.id }

private fun findAxoProjection(state: MongeState, p3: Point3D): Point3DAxo? =
    state.pointsAxo.firstOrNull { it.parent?.id == p3.id }

fun addCurve3DWithProjectionsFrom3DPoints(
    state: MongeState,
    points3D: List<Point3D>,
    closed: Boolean = false,
    showProjectionsInAxo: Boolean = true,
): Boolean {
    // 1) validace
    val uniq = points3D.distinctBy { it.id }
    if (uniq.size < 3) {
        state.consInfo.value = "Pro 3D křivku vyber alespoň 3 různé 3D body."
        return false
    }

    // 2) najdi průměty všech bodů
    val pudPts: List<Point3DPudorys?> = uniq.map { findPudorysProjection(state, it) }
    val narPts: List<Point3DNarys?>   = uniq.map { findNarysProjection(state, it) }

    if (pudPts.any { it == null } || narPts.any { it == null }) {
        state.consInfo.value = "Některému 3D bodu chybí půdorysný nebo nárysný průmět – nelze vytvořit 3D křivku."
        return false
    }

    val pud = pudPts.filterNotNull()
    val nar = narPts.filterNotNull()
    val bok = uniq.mapNotNull { findBokorysProjection(state, it) }

    // 3) vytvoř 3D křivku (styl z currentLineStyleSettings)
    val settings = state.currentLineStyleSettings

    val curve3D = Curve3D(
        name = "k",
        color = settings.color,
        strokeWidth = settings.strokeWidth,
        lineStyle = settings.style,
        pointIds = uniq.map { it.id },
        closed = closed,
        creationIndex = state.nextCreationIndex,
    )

    // 4) vytvoř projekce s odkazem na 3D parent
    val curveP = CurvePudorys(
        parentId = curve3D.id,
        name = "k".withSuffixOnce("₁"),
        color = settings.color,
        strokeWidth = settings.strokeWidth,
        lineStyle = settings.style,
        points = pud.map { CurvePudRef.P(it.id) },
        closed = closed,
        parent = curve3D,
        showInAxoInitial = showProjectionsInAxo,
    )

    val curveN = CurveNarys(
        parentId = curve3D.id,
        name = "k".withSuffixOnce("₂"),
        color = settings.color,
        strokeWidth = settings.strokeWidth,
        lineStyle = settings.style,
        pointIds = nar.map { it.id },
        closed = closed,
        parent = curve3D,
        showInAxoInitial = showProjectionsInAxo,
    )

    state.curves3D.add(curve3D)
    state.curvesPudorys.add(curveP)
    state.curvesNarys.add(curveN)

    // bokorys průmět jen pokud mají všechny body bokorysný průmět
    if (bok.size == uniq.size) {
        val curveB = CurveBokorys(
            parentId = curve3D.id,
            name = "k".withSuffixOnce("₃"),
            color = settings.color,
            strokeWidth = settings.strokeWidth,
            lineStyle = settings.style,
            pointIds = bok.map { it.id },
            closed = closed,
            parent = curve3D,
            showInAxoInitial = showProjectionsInAxo,
        )
        state.curvesBokorys.add(curveB)
    }

    // axo průmět jen pokud mají všechny body axo průmět (kreslení z NORMAL_2D overlay)
    val axoPts = uniq.mapNotNull { findAxoProjection(state, it) }
    if (axoPts.size == uniq.size) {
        val curveA = CurveAxo(
            parentId = curve3D.id,
            name = "k",
            color = settings.color,
            strokeWidth = settings.strokeWidth,
            lineStyle = settings.style,
            pointIds = axoPts.map { it.id },
            closed = closed,
            parent = curve3D,
        )
        state.curvesAxo.add(curveA)
    }

    commitSnapshot(state)

    println("3D křivka vytvořena (${uniq.size} bodů).")
    return true
}
fun handleCurve3DPickFromPudorysClick(
    state: MongeState,
    clicked: Point3DPudorys
) {
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "curve3d_pick_points") return

    val p3 = clicked.parent ?: return

    // ✅ klik na první bod = uzavření (bez duplicity)
    if (tryCloseCurve3DPickOnClickFirst(state, p3.id)) return

    // anti-double-click
    if (state.curve3DLastPickedId == p3.id) return

    // ✅ zákaz duplicit (smyčka přes použitý bod)
    if (state.curve3DPickPointIds.contains(p3.id)) {
        state.consInfo.value = "Bod už je v křivce – pro uzavření klikni na první bod."
        return
    }

    state.curve3DPickPointIds.add(p3.id)
    state.curve3DLastPickedId = p3.id
    state.curve3DPickClosed = false

    updateConstructionInfo(state)
}

fun handleCurve3DPickFromNarysClick(
    state: MongeState,
    clicked: Point3DNarys
) {
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "curve3d_pick_points") return

    val p3 = clicked.parent ?: return

    if (tryCloseCurve3DPickOnClickFirst(state, p3.id)) return

    if (state.curve3DLastPickedId == p3.id) return

    if (state.curve3DPickPointIds.contains(p3.id)) {
        state.consInfo.value = "Bod už je v křivce – pro uzavření klikni na první bod."
        return
    }

    state.curve3DPickPointIds.add(p3.id)
    state.curve3DLastPickedId = p3.id
    state.curve3DPickClosed = false

    updateConstructionInfo(state)
}

fun handleCurve3DPickFromBokorysClick(
    state: MongeState,
    clicked: Point3DBokorys
) {
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "curve3d_pick_points") return

    val p3 = clicked.parent ?: return

    if (tryCloseCurve3DPickOnClickFirst(state, p3.id)) return

    if (state.curve3DLastPickedId == p3.id) return

    if (state.curve3DPickPointIds.contains(p3.id)) {
        state.consInfo.value = "Bod už je v křivce – pro uzavření klikni na první bod."
        return
    }

    state.curve3DPickPointIds.add(p3.id)
    state.curve3DLastPickedId = p3.id
    state.curve3DPickClosed = false

    updateConstructionInfo(state)
}

fun addPointToCurve3DPickById(state: MongeState, point3DId: String) {
    if (state.drawobjects != Mongeobjects.CURVE) return
    if (state.projectionPhase != "curve3d_pick_points") return

    if (tryCloseCurve3DPickOnClickFirst(state, point3DId)) return
    if (state.curve3DLastPickedId == point3DId) return
    if (state.curve3DPickPointIds.contains(point3DId)) {
        state.consInfo.value = "Bod už je v křivce – pro uzavření klikni na první bod."
        return
    }

    state.curve3DPickPointIds.add(point3DId)
    state.curve3DLastPickedId = point3DId
    state.curve3DPickClosed = false
    updateConstructionInfo(state)
}

fun finalizeCurve3DOnEnter(state: MongeState): Boolean {
    if (state.drawobjects != Mongeobjects.CURVE) return false
    if (state.projectionPhase != "curve3d_pick_points") return false

    val ids = state.curve3DPickPointIds.toList()
    if (ids.size < 3) {
        state.consInfo.value = "Pro 3D křivku vyber alespoň 3 body."
        return true
    }

    val pts3D = ids.mapNotNull { id -> state.sharedPoints3D.find { it.id == id } }
    if (pts3D.size != ids.size) {
        state.consInfo.value = "Některý 3D bod už neexistuje – začněte znovu."
        state.curve3DPickPointIds.clear()
        state.curve3DLastPickedId = null
        state.curve3DPickClosed = false
        return true
    }

    val ok = addCurve3DWithProjectionsFrom3DPoints(
        state = state,
        points3D = pts3D,
        closed = state.curve3DPickClosed,
        showProjectionsInAxo = state.projectionMode != ProjectionMode.AXO,
    )

    if (ok) {
        state.curve3DPickPointIds.clear()
        state.curve3DLastPickedId = null
        state.curve3DPickClosed = false     // ✅ reset
        repeatCons(state)
    }

    return true
}

private fun tryCloseCurve3DPickOnClickFirst(state: MongeState, clicked3DId: String): Boolean {
    val ids = state.curve3DPickPointIds
    val first = ids.firstOrNull() ?: return false

    // uzavíráme jen když klikneš na první a už máš aspoň 3 body
    if (clicked3DId == first && ids.size >= 3) {
        state.curve3DPickClosed = true
        finalizeCurve3DOnEnter(state) // dokonči hned
        return true
    }
    return false
}
