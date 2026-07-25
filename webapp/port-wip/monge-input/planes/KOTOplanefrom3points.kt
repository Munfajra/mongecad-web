package monge.input.planes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.*
import model.classes.Plane3D
import model.classes.PlaneEquation
import model.classes.PlaneTracePudorys
import model.classes.Point3DPudorys
import model.classes.normalized
import model.classes.tracesFromPlaneEquation
import monge.input.combineprojections.safeVirtualTraceBokorys
import monge.input.combineprojections.safeVirtualTraceNarys
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import utils.allocIndex
import utils.UUID

fun planeEquationFrom3Points(
    a: Point3D,
    b: Point3D,
    c: Point3D,
    eps: Float = 1e-6f
): PlaneEquation? {
    val ab = Offset3D(b.x - a.x, b.y - a.y, b.z - a.z)
    val ac = Offset3D(c.x - a.x, c.y - a.y, c.z - a.z)

    val n = ab cross ac
    val nLen = kotlin.math.sqrt(n.x*n.x + n.y*n.y + n.z*n.z)
    if (nLen < eps) return null // kolineární / degenerace

    // klasická rovnice: n·(X - a) = 0  =>  n·X + d = 0, kde d = -n·a
    val d = -(n.x*a.x + n.y*a.y + n.z*a.z)

    return PlaneEquation(
        a = n.x,
        b = n.y,
        c = n.z,
        d = d
    ).normalized()
}
fun createPlaneFrom3Existing3DPoints(
    a: Point3D,
    b: Point3D,
    c: Point3D,
    state: MongeState,
) {
    val eq = planeEquationFrom3Points(a, b, c) ?: return

    val (tPgen, tNgen,tBgen) = tracesFromPlaneEquation(eq)

    val planeId = UUID.randomUUID().toString()

    // pudorysná stopa (pokud null -> virtual)
    val traceP = (tPgen ?: safeVirtualTracePudorys()).copy(
        localName = null,
        localSuperscript = null,
        localColor = null,
        localLineStyle = null,
        localStrokeWidth = null,
        parent = null,
        parentId = planeId
    )

    // nárysná stopa (pokud null -> virtual)
    val traceN = (tNgen ?: safeVirtualTraceNarys()).copy(
        localName = null,
        localSuperscript = null,
        localColor = null,
        localLineStyle = null,
        localStrokeWidth = null,
        parent = null,
        parentId = planeId
    )
    val traceB = (tBgen ?: safeVirtualTraceBokorys()).copy(
        localName = null,
        localSuperscript = null,
        localColor = null,
        localLineStyle = null,
        localStrokeWidth = null,
        parent = null,
        parentId = planeId
    )

    val plane = Plane3D(
        tracePudorys = traceP,
        traceNarys = traceN,
        traceBokorys = traceB,
        name = "",
        superscript = "",
        equation = eq,
        color = Color.Black,
        lineStyle = LineStyle.Solid,
        strokeWidth = 1f,
        id = planeId, creationIndex = allocIndex(state)
    )
    state.planePendingForNaming = plane
    state.showPlaneNamingDialog = true
    state.planeNameInput = ""
    // transient parenty
    plane.tracePudorys.parent = plane
    plane.traceNarys.parent   = plane

    // zapsat do state
    state.lineTracesPudorys.add(plane.tracePudorys)
    state.lineTracesNarys.add(plane.traceNarys)
    state.lineTracesBokorys.add(plane.traceBokorys)
    state.planes3D.add(plane)


    updateConstructionInfo(state)
}
private fun safeVirtualTracePudorys(
    at: Offset = Offset(0f, 0f),
    dir: Offset = Offset(1f, 0f)
) = PlaneTracePudorys(
    point = Point3DPudorys(
        at.x,
        at.y,
        name = "",
        parent = Point3D(at.x, at.y, 0f, "")
    ),
    direction = if (dir.getDistance() < 1e-6f) Offset(1f, 0f) else dir,
    isVirtual = true
)

fun pickFirstSelected3DPoint(state: MongeState): Point3D? {
    val pP = state.selectedPointsPudorys.firstOrNull()?.parent
    if (pP != null) return pP

    val pN = state.selectedPointsNarys.firstOrNull()?.parent
    if (pN != null) return pN

    return null
}
fun handlePlaneFrom3PointsPickByClick(state: MongeState, clicked: Point3DPudorys) {
    val parent3D = clicked.parent ?: return   // musí být 3D bod

    when {
        state.planePickAId == null -> {
            state.planePickAId = clicked.id
            state.planePickBId = null
            state.planePickCId = null
            state.consInfo.value = "Vyberte druhý bod roviny"
            setProjectionPhase("plane_3pts_pick_2", state)
            return
        }

        state.planePickBId == null -> {
            if (clicked.id == state.planePickAId) return
            state.planePickBId = clicked.id
            state.consInfo.value = "Vyberte třetí bod roviny"
            setProjectionPhase("plane_3pts_pick_3", state)
            return
        }

        else -> {
            // třetí bod
            val aId = state.planePickAId ?: return
            val bId = state.planePickBId ?: return
            if (clicked.id == aId || clicked.id == bId) return
            state.planePickCId = clicked.id

            // najdi 3D parenty podle ID průmětů
            val a3 = state.pointsPudorys.find { it.id == aId }?.parent ?: run { resetPlanePick(state); return }
            val b3 = state.pointsPudorys.find { it.id == bId }?.parent ?: run { resetPlanePick(state); return }
            val c3 = parent3D

            createPlaneFrom3Existing3DPoints(a = a3, b = b3, c = c3, state = state)

            // cleanup
            resetPlanePick(state)
            setProjectionPhase("pudorys_start", state)
        }
    }
}

private fun resetPlanePick(state: MongeState) {
    state.planePickAId = null
    state.planePickBId = null
    state.planePickCId = null
    // případně: state.consInfo.value = ""
}
