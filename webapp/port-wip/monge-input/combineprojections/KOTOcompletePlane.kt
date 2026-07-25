package monge.input.combineprojections

import utils.System
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import serialization.commitSnapshot
import model.*
import model.classes.Plane3D
import model.classes.PlaneEquation
import model.classes.PlaneTraceBokorys
import model.classes.PlaneTraceNarys
import model.classes.PlaneTracePudorys
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.normalized
import model.classes.planeEquationFromTraces
import model.classes.tracesFromPlaneEquation
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.UUID

fun planeEquationFromTracePudorysAndPoint(
    trace: PlaneTracePudorys,
    p: Point3D,
    eps: Float = 1e-6f
): PlaneEquation? {
    // bod na stopě v půdorysu (z = 0)
    val p0 = Point3D(trace.point.x, trace.point.y, 0f, "")

    // směr stopy v půdorysu (XY)
    val d1 = Offset3D(trace.direction.x, trace.direction.y, 0f)

    // druhý směr: z p0 do 3D bodu
    val d2 = Offset3D(p.x - p0.x, p.y - p0.y, p.z - p0.z)

    // degenerace: bod leží na stopě (nebo skoro), normála ~ 0
    val n = d1 cross d2
    val nLen = kotlin.math.sqrt(n.x*n.x + n.y*n.y + n.z*n.z)
    if (nLen < eps) return null

    return planeEquationFromTraces(p0, d1, d2).normalized()
}
fun createPlaneFromPudorysTraceAndExisting3DPoint(
    tracePudorys: PlaneTracePudorys,
    pickedPointProjection: Point3DPudorys,
    state: MongeState,
    planeName: String = "ρ",
    superscript: String? = null
) {
    val p3 = pickedPointProjection.parent
        ?: return  // musí to být bod s 3D parentem

    val eq = planeEquationFromTracePudorysAndPoint(tracePudorys, p3) ?: return

    // z rovnice vygenerujeme stopy (může vrátit null -> virtual)
    val (tPgen, tNgen,tBgen) = tracesFromPlaneEquation(eq)
    val traceN = (tNgen ?: safeVirtualTraceNarys()).copy(

        localName = null,
        localSuperscript = null,
        localColor = null,
        localLineStyle = null,
        localStrokeWidth = null
    )
    val traceB = (tBgen ?: safeVirtualTraceBokorys()).copy(

        localName = null,
        localSuperscript = null,
        localColor = null,
        localLineStyle = null,
        localStrokeWidth = null
    )

    // vytvoř rovinu – nejdřív bez parentů v tracech (nastavíme po vytvoření)
    val planeId = UUID.randomUUID().toString()

    // ✅ klíč: zachovej původní stopu (geometrie + id), ale doplň parentId
    val upgradedTraceP = tracePudorys.copy(
        parent = null,          // transient, nastavíme za chvíli
        parentId = planeId
    )

    val upgradedTraceN = traceN.copy(
        parent = null,
        parentId = planeId
    )
    val upgradedTraceB = traceB.copy(
        parent = null,
        parentId = planeId
    )

    val plane = Plane3D(
        tracePudorys = upgradedTraceP,
        traceNarys = upgradedTraceN,
        traceBokorys = upgradedTraceB,
        name = planeName,
        superscript = superscript,
        equation = eq,
        // vzhled roviny klidně převezmi ze stopy, pokud chceš:
        color = tracePudorys.localColor ?: Color.Black,
        lineStyle = tracePudorys.localLineStyle ?: LineStyle.Solid,
        strokeWidth = tracePudorys.localStrokeWidth ?: 1f,
        id = planeId,
        creationIndex = allocIndex(state)
    )

    // nastav transient parenty (teď už můžeme)
    plane.tracePudorys.parent = plane
    plane.traceNarys.parent   = plane

    val idxP = state.lineTracesPudorys.indexOfFirst { it.id == tracePudorys.id }
    if (idxP >= 0) {
        state.lineTracesPudorys[idxP] = plane.tracePudorys
    } else {
        // fallback (nemělo by nastat): přidej
        state.lineTracesPudorys.add(plane.tracePudorys)
    }

    state.lineTracesNarys.add(plane.traceNarys)
    state.planes3D.add(plane)
    updateConstructionInfo(state)

}
fun safeVirtualTraceNarys(
    at: Offset = Offset(0f, 0f),
    dir: Offset = Offset(1f, 0f)
) = PlaneTraceNarys(
    point = Point3DNarys(
        at.x,
        at.y,
        name = "",
        parent = Point3D(at.x, 0f, at.y, "")
    ),
    direction = if (dir.getDistance() < 1e-6f) Offset(1f, 0f) else dir,
    isVirtual = true
)
fun safeVirtualTraceBokorys(
    at: Offset = Offset(0f, 0f),
    dir: Offset = Offset(1f, 0f)
) = PlaneTraceBokorys(
    point = Point3DBokorys(
        at.x,
        at.y,
        name = "",
        parent = Point3D(at.x, 0f, at.y, "")
    ),
    direction = if (dir.getDistance() < 1e-6f) Offset(1f, 0f) else dir,
    isVirtual = true
)
fun beginPlaneFromTracePickPoint(
    trace: PlaneTracePudorys,
    state: MongeState,
    planeName: String = "ρ",
    superscript: String? = null
) {
    state.pendingPlaneTracePudorys = trace

    // můžeš si uložit i jméno roviny do state, když nechceš parametry tahat dál:
    state.inputName = planeName
    state.inputSuperscript = superscript.orEmpty()

    // fáze: čekáme na klik na existující 3D bod
    setProjectionPhase("plane_trace_pick_point", state)

    state.deferSelectionUntil = System.currentTimeMillis() + 100
    updateConstructionInfo(state)
}
fun handlePlaneFromTracePickPointClick(
    state: MongeState
) {
    if (state.projectionPhase != "plane_trace_pick_point") return

    val trace = state.pendingPlaneTracePudorys ?: return


    val picked = state.selectedPointsPudorys.firstOrNull()

    // musí mít 3D parent
    if (picked?.parent == null) {
        resetStavu(state)
        return
    }
    if (picked.parent!!.z == 0f) {
        resetStavu(state)
        return
    }


    val planeName = state.inputName.ifBlank { "ρ" }
    val sup = state.inputSuperscript.trim().ifBlank { null }

    createPlaneFromPudorysTraceAndExisting3DPoint(
        tracePudorys = trace,
        pickedPointProjection = picked,
        state = state,
        planeName = planeName,
        superscript = sup
    )

    // uklid
    state.pendingPlaneTracePudorys = null
    setProjectionPhase("pudorys_start", state)
    updateConstructionInfo(state)
    resetStavu(state)
    commitSnapshot(state)
}
