package monge.input.lines

import model.classes.isAxoPlane
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.Offset3D
import model.Point3D
import model.ProjectionMode
import model.axo.AxoModel
import model.axo.AxoType
import model.classes.*
import state.MongeState

const val X12_LINE_ID = "X12_ID"
private const val X12_NARYS_ID = "X12_ID_N"
private const val X12_PUDORYS_ID = "X12_ID_P"
const val XA_ID = "xa_ID"
const val YA_ID = "ya_ID"
const val ZA_ID = "za_ID"
const val AXO_PLANE_ID = "axo_plane_ID"
const val AXO_PLANE_TRACE_PUDORYS_ID = "axo_pudorys_ID"
const val AXO_PLANE_TRACE_NARYS_ID = "axo_narys_ID"
const val AXO_PLANE_TRACE_BOKORYS_ID = "axo_bokorys_ID"
private val AXO_AXIS_PUDORYS_IDS = setOf("xp_ID", "yp_ID")
private val AXO_AXIS_NARYS_IDS = setOf("xn_ID", "zn_ID")
private val AXO_AXIS_BOKORYS_IDS = setOf("yb_ID", "zb_ID")
private val AXO_AXIS_AXO_IDS = setOf(XA_ID, YA_ID, ZA_ID)

fun isX12Line(line: Line3D?) = line?.id == X12_LINE_ID

fun isX12Projection(line: Line2DProjection?) =
    line?.id == X12_NARYS_ID || line?.id == X12_PUDORYS_ID || line?.parent?.id == X12_LINE_ID

fun createX12ProjectionLines(): Triple<Line3DProjectionNarys, Line3DProjectionPudorys, Line3D> {
    val start     = Point3D(350f, 0f, 0f, "")
    val direction = Offset3D(1f, 0f, 0f)

    val parent = Line3D(
        id = X12_LINE_ID,                 // ⬅︎ pevné ID
        start = start,
        direction = direction,
        name = "x₁₂",
        color = Color.Black,
        lineStyle = LineStyle.Solid,
        strokeWidth = 1f
    )

    val narys = Line3DProjectionNarys(
        id = X12_NARYS_ID,
        point = Point3DNarys(start.x, start.z, ""),
        direction = Offset(direction.x, direction.z),
        parent = parent,
        localName = parent.name
    )

    val pudorys = Line3DProjectionPudorys(
        id = X12_PUDORYS_ID,
        point = Point3DPudorys(start.x, start.y, ""),
        direction = Offset(direction.x, direction.y),
        parent = parent,
        localName = parent.name
    )

    // (Pokud stále používáte combineProjectionsToLine3D, ponechte ho,
    //  ID už máme fixní.)
    return Triple(narys, pudorys, parent)
}
data class AxoAxes (
    val x: Line3D,
    val y: Line3D,
    val z: Line3D,
    val xp: Line3DProjectionPudorys,
    val xn: Line3DProjectionNarys,
    val yp: Line3DProjectionPudorys,
    val yb: Line3DProjectionBokorys,
    val zn: Line3DProjectionNarys,
    val zb: Line3DProjectionBokorys,
    val xa: Line3DProjectionAxo,
    val ya: Line3DProjectionAxo,
    val za: Line3DProjectionAxo,

)
fun createAxoAxis(): AxoAxes {
    val start     = Point3D(0f, 0f, 0f, "")
    val directionX = Offset3D(1f, 0f, 0f)
    val directionY = Offset3D(0f, 1f, 0f)
    val directionZ = Offset3D(0f, 0f, 1f)

    val x = Line3D(
        id = "x_axis",                 // ⬅︎ pevné ID
        start = start,
        direction = directionX,
        name = "x",
        color = Color.Red,
        lineStyle = LineStyle.Solid,
        strokeWidth = 1f
    )
    val y = Line3D(
        id = "y_axis",                 // ⬅︎ pevné ID
        start = start,
        direction = directionY,
        name = "y",
        color = Color.Blue,
        lineStyle = LineStyle.Solid,
        strokeWidth = 1f
    )
    val z = Line3D(
        id = "z_axis",                 // ⬅︎ pevné ID
        start = start,
        direction = directionZ,
        name = "z",
        color = Color(0,102,0),
        lineStyle = LineStyle.Solid,
        strokeWidth = 1f
    )
    val xn = Line3DProjectionNarys(
        id = "xn_ID",
        point = Point3DNarys(start.x, start.z, ""),
        direction = Offset(directionX.x, directionX.z),
        parent = x,
        localName = x.name,
        localColor = Color.Black
    )
    val xp = Line3DProjectionPudorys(
        id = "xp_ID",
        point = Point3DPudorys(start.x, start.y, ""),
        direction = Offset(directionX.x, directionX.y),
        parent = x,
        localName = x.name,
        localColor = Color.Black
    )
    val yb = Line3DProjectionBokorys(
        id = "yb_ID",
        point = Point3DBokorys(start.y, start.z, ""),
        direction = Offset(directionY.y, directionY.z),
        parent = y,
        localName = y.name,
        localColor = Color.Black
    )
    val yp = Line3DProjectionPudorys(
        id = "yp_ID",
        point = Point3DPudorys(start.x, start.y, ""),
        direction = Offset(directionY.x, directionY.y),
        parent = y,
        localName = y.name,
        localColor = Color.Black

    )
    val zn = Line3DProjectionNarys(
        id = "zn_ID",
        point = Point3DNarys(start.x, start.z, ""),
        direction = Offset(directionZ.x, directionZ.z),
        parent = z,
        localName = z.name,
        localColor = Color.Black
    )
    val zb = Line3DProjectionBokorys(
        id = "zb_ID",
        point = Point3DBokorys(start.y, start.z, ""),
        direction = Offset(directionZ.y, directionZ.z),
        parent = z,
        localName = z.name,
        localColor = Color.Black
    )

    // AXO průměty os – jediné, co se reálně vykresluje v AxoCanvas (viz drawAxoLines).
    // Pozice/směr (p/dir) jsou jen placeholdery – díky parentu se přepočítávají
    // live podle aktuální axo báze (currentAxoLineLocal), takže zůstávají přesné
    // i po změně axonometrie. Barva je díky localColor přednostně černá a
    // nezávislá na barvě parenta (Red/Blue/Green používané v OpenGL/Monge).
    val xa = Line3DProjectionAxo(
        id = XA_ID,
        p = Point3DAxo(start.x, start.y),
        dir = Offset(directionX.x, directionX.y),
        parent = x,
        localName = x.name,
        localColor = Color.Black,
        showInAxoInitial = false
    )
    val ya = Line3DProjectionAxo(
        id = YA_ID,
        p = Point3DAxo(start.x, start.y),
        dir = Offset(directionY.x, directionY.y),
        parent = y,
        localName = y.name,
        localColor = Color.Black,
        showInAxoInitial = false
    )
    val za = Line3DProjectionAxo(
        id = ZA_ID,
        p = Point3DAxo(start.x, start.y),
        dir = Offset(directionZ.x, directionZ.y),
        parent = z,
        localName = z.name,
        localColor = Color.Black,
        showInAxoInitial = false
    )

    return AxoAxes(
        x = x,
        y =y,
        z = z,
        xp = xp,
        xn = xn,
        yp = yp,
        yb = yb,
        zn = zn,
        zb = zb,
        xa = xa,
        ya = ya,
        za = za
    )
}
fun ensureAxoAxesExists(state: MongeState) {
    val axes = createAxoAxis()

    if (state.lines3D.none { it.id == "x_axis" }) {
        state.lines3D += axes.x
    }
    if (state.lines3DPudorys.none { it.id == "xp_ID" }) {
        state.lines3DPudorys += axes.xp
    }
    if (state.lines3DNarys.none { it.id == "xn_ID" }) {
        state.lines3DNarys += axes.xn
    }
    if (state.lines3DAxo.none { it.id == XA_ID }) {
        state.lines3DAxo += axes.xa
    }

    if (state.lines3D.none { it.id == "y_axis" }) {
        state.lines3D += axes.y
    }
    if (state.lines3DPudorys.none { it.id == "yp_ID" }) {
        state.lines3DPudorys += axes.yp
    }
    if (state.lines3DBokorys.none { it.id == "yb_ID" }) {
        state.lines3DBokorys += axes.yb
    }
    if (state.lines3DAxo.none { it.id == YA_ID }) {
        state.lines3DAxo += axes.ya
    }

    if (state.lines3D.none { it.id == "z_axis" }) {
        state.lines3D += axes.z
    }
    if (state.lines3DNarys.none { it.id == "zn_ID" }) {
        state.lines3DNarys += axes.zn
    }
    if (state.lines3DBokorys.none { it.id == "zb_ID" }) {
        state.lines3DBokorys += axes.zb
    }
    if (state.lines3DAxo.none { it.id == ZA_ID }) {
        state.lines3DAxo += axes.za
    }
}

fun areAxoAxesVisible(state: MongeState): Boolean =
    state.lines3DPudorys.any { it.id in AXO_AXIS_PUDORYS_IDS && it.showInAxo } ||
            state.lines3DNarys.any { it.id in AXO_AXIS_NARYS_IDS && it.showInAxo } ||
            state.lines3DBokorys.any { it.id in AXO_AXIS_BOKORYS_IDS && it.showInAxo }

/**
 * Vrcholy X/Y/Z referenčního axo trojúhelníku jako průsečíky roviny τ
 * se souřadnicovými osami. Jsou odvozené pouze pro vykreslení, takže se
 * neukládají mezi editovatelné body a nevstupují do výběru ani snappingu.
 */
fun visibleAxoTriangleVertices(state: MongeState): List<Pair<String, Offset3D>> {
    if (!areAxoAxesVisible(state)) return emptyList()

    val equation = state.planes3D.firstOrNull { it.id == AXO_PLANE_ID }?.equation
        ?: return emptyList()
    val eps = 1e-6f
    if (kotlin.math.abs(equation.a) < eps ||
        kotlin.math.abs(equation.b) < eps ||
        kotlin.math.abs(equation.c) < eps
    ) return emptyList()

    val x = -equation.d / equation.a
    val y = -equation.d / equation.b
    val z = -equation.d / equation.c
    if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return emptyList()

    return listOf(
        "X" to Offset3D(x, 0f, 0f),
        "Y" to Offset3D(0f, y, 0f),
        "Z" to Offset3D(0f, 0f, z)
    )
}

fun setAxoAxesVisible(state: MongeState, visible: Boolean) {
    ensureAxoAxesExists(state)
    state.lines3DPudorys.filter { it.id in AXO_AXIS_PUDORYS_IDS }.forEach {
        it.showInAxoInitial = visible
        it.showInAxo = visible
    }
    state.lines3DNarys.filter { it.id in AXO_AXIS_NARYS_IDS }.forEach {
        it.showInAxoInitial = visible
        it.showInAxo = visible
    }
    state.lines3DBokorys.filter { it.id in AXO_AXIS_BOKORYS_IDS }.forEach {
        it.showInAxoInitial = visible
        it.showInAxo = visible
    }

    // Referenční rovina τ se v Compose axo zobrazuje svými třemi stopami.
    // Při skrytí os ji ponecháme ve stavu a jen přepneme viditelnost, aby se
    // po opětovném odkrytí os okamžitě vrátila.
    state.lineTracesPudorys.indexOfFirst { it.id == AXO_PLANE_TRACE_PUDORYS_ID }
        .takeIf { it >= 0 }
        ?.let { index ->
            state.lineTracesPudorys[index] = state.lineTracesPudorys[index].copy(showInAxo = visible)
        }
    state.lineTracesNarys.indexOfFirst { it.id == AXO_PLANE_TRACE_NARYS_ID }
        .takeIf { it >= 0 }
        ?.let { index ->
            state.lineTracesNarys[index] = state.lineTracesNarys[index].copy(showInAxo = visible)
        }
    state.lineTracesBokorys.indexOfFirst { it.id == AXO_PLANE_TRACE_BOKORYS_ID }
        .takeIf { it >= 0 }
        ?.let { index ->
            state.lineTracesBokorys[index] = state.lineTracesBokorys[index].copy(showInAxo = visible)
        }

    state.triggerRedraw++
}

fun toggleAxoAxesVisibility(state: MongeState) {
    setAxoAxesVisible(state, !areAxoAxesVisible(state))
}

/** Přepne viditelnost os podle aktuálního módu (AXO = X/Y/Z osy, jinak Monge/Koto pomocné osy). */
fun toggleAxisVisibilityForMode(state: MongeState) {
    if (state.projectionMode == ProjectionMode.AXO) {
        toggleAxoAxesVisibility(state)
        return
    }

    state.axisVisible = !state.axisVisible

    val x = state.helpLinePudorys.find { it.id == "axisX" }
    val y = state.helpLinePudorys.find { it.id == "axisY" }
    val z = state.helpLineNarys.find { it.id == "axisZ" }
    val origin = state.aidPointsLogical.find { it.id == "origin" }

    state.helpLinePudorys.remove(x)
    state.helpLinePudorys.remove(y)
    state.helpLineNarys.remove(z)
    state.aidPointsLogical.remove(origin)
}

/** Je-li aktuální mód AXO, vrací viditelnost os; jinak `state.axisVisible`. */
fun isAxisVisibleForMode(state: MongeState): Boolean =
    if (state.projectionMode == ProjectionMode.AXO) areAxoAxesVisible(state) else state.axisVisible

// Skutečné 3D osy vykreslované OpenGL: v AXO x_axis/y_axis/z_axis Line3D,
// jinak osa x₁₂. Nesouvisí s `axisVisible` (to jsou pomocné Compose
// konstrukční osy) ani s `toggleAxoAxesVisibility` (ta skrývá jen 2D
// projekce os v Compose axo canvasu) – toto je nezávislý `show` na
// samotném Line3D, který čte OpenGL render (`state.lines3D.filter { it.show }`).
private val OPENGL_AXO_AXIS_3D_IDS = setOf("x_axis", "y_axis", "z_axis")

fun isOpenGlAxisVisible(state: MongeState): Boolean =
    if (state.projectionMode == ProjectionMode.AXO) {
        state.lines3D.any { it.id in OPENGL_AXO_AXIS_3D_IDS && it.show }
    } else {
        state.lines3D.firstOrNull { it.id == X12_LINE_ID }?.show ?: false
    }

fun toggleOpenGlAxisVisibility(state: MongeState) {
    run {
        Snapshot.withMutableSnapshot {
            if (state.projectionMode == ProjectionMode.AXO) {
                val newShow = !isOpenGlAxisVisible(state)
                OPENGL_AXO_AXIS_3D_IDS.forEach { id ->
                    val idx = state.lines3D.indexOfFirst { it.id == id }
                    if (idx >= 0) state.lines3D[idx] = state.lines3D[idx].copy(show = newShow)
                }
            } else {
                val idx = state.lines3D.indexOfFirst { it.id == X12_LINE_ID }
                if (idx >= 0) {
                    val old = state.lines3D[idx]
                    state.lines3D[idx] = old.copy(show = !old.show)
                }
            }
            state.triggerRedraw++
        }
    }
}
fun ensureX12LineExists(state: MongeState) {
    val x12Parents = state.lines3D.filter { it.id == X12_LINE_ID }
    val parent = x12Parents.firstOrNull()
    val x12Narysy = state.lines3DNarys.filter { isX12Projection(it) }
    val x12Pudorysy = state.lines3DPudorys.filter { isX12Projection(it) }

    val needsRepair =
        parent == null ||
                x12Parents.size != 1 ||
                x12Narysy.size != 1 ||
                x12Pudorysy.size != 1 ||
                x12Narysy.singleOrNull()?.parent !== parent ||
                x12Pudorysy.singleOrNull()?.parent !== parent

    if (!needsRepair) return

    val (narysTemplate, pudorysTemplate, lineTemplate) = createX12ProjectionLines()
    val repairedParent = parent ?: lineTemplate

    state.lines3D.removeAll { it.id == X12_LINE_ID && it !== repairedParent }
    if (parent == null) state.lines3D += repairedParent

    x12Narysy.forEach { line ->
        state.selectedLinesNarys.remove(line)
        state.labelOffsetsNarys.remove(line.id)
        if (state.rename.lineBeingRenamedNarys === line) state.rename.lineBeingRenamedNarys = null
    }
    x12Pudorysy.forEach { line ->
        state.selectedLinesPudorys.remove(line)
        state.labelOffsetsPudorys.remove(line.id)
        if (state.rename.lineBeingRenamedPudorys === line) state.rename.lineBeingRenamedPudorys = null
    }

    state.lines3DNarys.removeAll(x12Narysy.toSet())
    state.lines3DPudorys.removeAll(x12Pudorysy.toSet())
    state.lines3DNarys += narysTemplate.copy(parent = repairedParent, parentId = repairedParent.id)
    state.lines3DPudorys += pudorysTemplate.copy(parent = repairedParent, parentId = repairedParent.id)
}
fun createOrigin(): AidPointLogical {

    val originP = AidPointLogical(
        x = 0f, y = 0f,
        name = "0",
        id = "origin"
    )
    return (originP)
}

// Zajistí existenci počátku v state (idempotentní)
fun ensureAxisExists(state: MongeState) {
if (state.axisVisible) {
    val hasP = state.aidPointsLogical.any { it.id == "origin" }
    if (!hasP) {
        val oP = createOrigin()

        state.aidPointsLogical.add(oP)
    }
    val hasY = state.helpLinePudorys.any { it.id == "axisY" }

    if (!hasY) {
        val yP = createAxisY()
        state.helpLinePudorys.add(yP)
    }
}
    if (state.axisVisible&& state.projectionMode == ProjectionMode.MONGE)
    {
        val hasZ = state.helpLineNarys.any { it.id == "axisZ" }

        if (!hasZ) {
            val yP = createAxisZ()
            state.helpLineNarys.add(yP)
        }

    }
    if (state.projectionMode != ProjectionMode.MONGE && state.axisVisible) {
        val hasX = state.helpLinePudorys.any { it.id == "axisX" }

        if (!hasX) {
            val xP = createAxisX()
            state.helpLinePudorys.add(xP)
        }


    }
}
fun createAxisX(): HelpLinePudorys {
    val origin = Point3DPudorys(
        x = 0f,
        y = 0f,
    )
    val axisX = HelpLinePudorys(
        point = origin,
        direction = Offset(1f, 0f),
        name = "x",
        parentAny = null,
        id = "axisX",
        localColor = Color.Gray
    )

    return (axisX)
}
fun createAxisY(): HelpLinePudorys {
    val origin = Point3DPudorys(
        x = 0f,
        y = 0f,
    )
    val axisY = HelpLinePudorys(
        point = origin,
        direction = Offset(0f, 1f),
        name = "y",
        parentAny = null,
        id = "axisY",
        localColor = Color.Gray,
    )

    return (axisY)
}
fun createAxisZ(): HelpLineNarys {
    val origin = Point3DNarys(
        x = 0f,
        z = 0f,
    )
    val axisY = HelpLineNarys(
        point = origin,
        direction = Offset(0f, 1f),
        name = "z",
        parentAny = null,
        id = "axisZ",
        localColor = Color.Gray,
    )

    return (axisY)
}
fun planeEquationFromAxoModel(model: AxoModel): PlaneEquation? {
    val xy = (model.yPoint - model.xPoint).getDistance()
    val yz = (model.zPoint - model.yPoint).getDistance()
    val zx = (model.xPoint - model.zPoint).getDistance()

    val a2 = (xy * xy + zx * zx - yz * yz) / 2f
    val b2 = (xy * xy + yz * yz - zx * zx) / 2f
    val c2 = (yz * yz + zx * zx - xy * xy) / 2f

    if (a2 <= 1e-6f || b2 <= 1e-6f || c2 <= 1e-6f) return null

    val a = kotlin.math.sqrt(a2)
    val b = kotlin.math.sqrt(b2)
    val c = kotlin.math.sqrt(c2)

    return PlaneEquation(
        a = b * c,
        b = a * c,
        c = a * b,
        d = -a * b * c
    )
}

fun createOrUpdateAxoPlane3DFromModel(
    model: AxoModel,
    state: MongeState,
    name: String = "τ"
) {
    // Referenční rovina τ (axo trojúhelník) se přidává u pravoúhlé (ortogonální)
    // axonometrie a u kosoúhlé zadané mezi-osovými úhly (model.referencePlane).
    // U kosoúhlé zadané přes ω + q se nevytváří. Pokud aktuální model rovinu mít
    // nemá (nebo z něj nejde sestavit), případnou starou rovinu odstraníme.
    val wantPlane = model.mode == AxoType.ORTHOGONAL || model.referencePlane
    val equation = if (wantPlane) planeEquationFromAxoModel(model) else null
    val traces = equation?.let { tracesFromPlaneEquation(it) }
    val pudorysRaw = traces?.pudorys
    val narysRaw = traces?.narys
    val bokorysRaw = traces?.bokorys
    if (equation == null || pudorysRaw == null || narysRaw == null || bokorysRaw == null) {
        removeAxoPlane(state)
        return
    }

    val planeId = AXO_PLANE_ID
    val tracePId = AXO_PLANE_TRACE_PUDORYS_ID
    val traceNId = AXO_PLANE_TRACE_NARYS_ID
    val traceBId = AXO_PLANE_TRACE_BOKORYS_ID

    val axoPlane = Plane3D(
        tracePudorys = pudorysRaw,
        traceNarys = narysRaw,
        traceBokorys = bokorysRaw,
        name = name,
        equation = equation,
        color = Color.Black,
        lineStyle = LineStyle.Solid,
        show = false,
        id = planeId
    )

    val tracesVisible = areAxoAxesVisible(state)
    val pudorys = pudorysRaw.copy(
        parent = axoPlane,
        parentId = axoPlane.id,
        id = tracePId,
        showInAxo = tracesVisible
    )

    val narys = narysRaw.copy(
        parent = axoPlane,
        parentId = axoPlane.id,
        id = traceNId,
        showInAxo = tracesVisible
    )

    val bokorys = bokorysRaw.copy(
        parent = axoPlane,
        parentId = axoPlane.id,
        id = traceBId,
        showInAxo = tracesVisible
    )

    val finalPlane = axoPlane.copy(
        tracePudorys = pudorys,
        traceNarys = narys,
        traceBokorys = bokorys
    )


    removeAxoPlane(state)

    state.planes3D.add(finalPlane)
    state.lineTracesPudorys.add(pudorys.copy(parent = finalPlane))
    state.lineTracesNarys.add(narys.copy(parent = finalPlane))
    state.lineTracesBokorys.add(bokorys.copy(parent = finalPlane))
}

// Referenční axo rovina (τ) je - stejně jako osy - vyloučená ze serializace
// (viz isAxoPlane/isAxoPlaneTrace* v JSONsave.kt a HistorySerialize.kt), aby
// nezatěžovala uložený soubor/historii. Musí ale zůstat ve výkresu, dokud
// pro ni má aktivní axonometrie smysl (planeEquationFromAxoModel vrátí
// rovnici - u kosoúhlé axonometrie typicky ne). Voláním na každém
// recomposition (stejně jako ensureAxoAxesExists) ji idempotentně obnoví,
// pokud chybí - po loadu ze souboru i po návratu z historie (undo/redo).
/** Odstraní referenční axo rovinu τ a její stopy z výkresu (idempotentní). */
fun removeAxoPlane(state: MongeState) {
    state.planes3D.removeAll { it.id == AXO_PLANE_ID }
    state.lineTracesPudorys.removeAll { it.id == AXO_PLANE_TRACE_PUDORYS_ID }
    state.lineTracesNarys.removeAll { it.id == AXO_PLANE_TRACE_NARYS_ID }
    state.lineTracesBokorys.removeAll { it.id == AXO_PLANE_TRACE_BOKORYS_ID }
}

fun ensureAxoPlaneExists(state: MongeState) {
    if (state.basis == null) return
    if (state.planes3D.any { it.id == AXO_PLANE_ID }) return
    createOrUpdateAxoPlane3DFromModel(model = state.activeAxoModel, state = state)
}
