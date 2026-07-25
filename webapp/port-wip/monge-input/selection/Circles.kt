package monge.input.selection

import utils.System
import androidx.compose.ui.geometry.Offset
import draw.mongescreen.labels.clearSelection
import model.*
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.SphereSurface3D
import state.MongeState
import state.snapMonge.findNearestAidPointLogical
import utils.getLogicalCursor
import kotlin.math.abs
import kotlin.math.sqrt

// ——— PŮDORYS: klik výběru ———
fun handleSelectionPudorysCircle(
    state: MongeState,
) {
    val allowed = state.drawobjects == Mongeobjects.NONE || state.isConicArcSelectionMode()
    if (!allowed) return
    val snapped = getLogicalCursor(
        state.snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val x = snapped.x
    val y = snapped.y
    val isNearPoint = state.snappedPointPudorys != null
    val isNearAidPoint = state.findNearestAidPointLogical(
        cursorLogical = Offset(x, y),
        snapRadiusLogical = state.snapThreshold / state.scale
    ) != null
    if (isNearPoint || isNearAidPoint) return
    val selected = state.circlesPudorys.findLast { circle ->
        val x0 = -circle.d / 2f
        val y0 = -circle.e / 2f
        val r2 = x0 * x0 + y0 * y0 - circle.f
        if (r2 <= 0f) return@findLast false
        val r = sqrt(r2)
        val dist = (Offset(x, y) - Offset(x0, y0)).getDistance()
        abs(dist - r) <= state.snapThreshold / state.scale
    }

    selected?.let { circle ->

        if (state.drawobjects == Mongeobjects.NONE && trySelectSolidOfRevolutionForPart(state, circle.id, circle.parentId)) {
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        }
        clearSelection(state)
        if (circle.parentId!=null){
            val sphere = state.spheres3D.find { it.id == circle.parentId }?:return
            toggleSelectionSphere3D(sphere,state)}
        else {
        state.selectedCirclesPudorys.add(circle)
            println("🟢 Označena kružnice v PŮDORYSU: ${circle.name}")}


        state.deferSelectionUntil = System.currentTimeMillis() + 100

    }
}
// ——— NÁRYS: klik výběru ———
fun handleSelectionNarysCircle(
    state: MongeState,
) {
    val allowed = state.drawobjects == Mongeobjects.NONE || state.isConicArcSelectionMode()
    if (!allowed) return
    val snapped = getLogicalCursor(
        state.snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val x = snapped.x
    val z = -snapped.y  // nárys: záporná osa Z
    val isNearPoint = state.snappedPointNarys != null
    val isNearAidPoint = state.findNearestAidPointLogical(
        cursorLogical = Offset(x, -z),
        snapRadiusLogical = state.snapThreshold / state.scale
    ) != null
    if (isNearPoint || isNearAidPoint) return
    val selected = state.circlesNarys.findLast { circle ->
        val x0 = -circle.d / 2f
        val z0 = -circle.e / 2f
        val r2 = x0 * x0 + z0 * z0 - circle.f
        if (r2 <= 0f) return@findLast false
        val r = sqrt(r2)
        val dist = (Offset(x, z) - Offset(x0, z0)).getDistance()
        abs(dist - r) <= state.snapThreshold / state.scale
    }

    selected?.let { circle ->
        if (state.drawobjects == Mongeobjects.NONE && trySelectSolidOfRevolutionForPart(state, circle.id, circle.parentId)) {
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return
        }
        clearSelection(state)
        if (circle.parentId!=null){
            val sphere = state.spheres3D.find { it.id == circle.parentId }?:return
            toggleSelectionSphere3D(sphere,state)}
        else {
        state.selectedCirclesNarys.add(circle)
            println("🟣 Označena kružnice v NÁRYSU: ${circle.name}")}
        state.deferSelectionUntil = System.currentTimeMillis() + 100

    }
}

// ——— PŮDORYS: toggle v seznamu/inspektoru ———
fun toggleSelectionPudorysCircle(circle: ConicSectionPudorys, state: MongeState) {
    val removed = state.selectedCirclesPudorys.remove(circle)
    if (!removed) {
        state.selectedCirclesPudorys.add(circle)
        selectSphereFamily(circle.parentId, state)
    } else {
        // pokud vypínám jednu část rodiny, vypni i zbytek
        unselectSphereFamily(circle.parentId, state)
    }
}


// ——— NÁRYS: toggle v seznamu/inspektoru ———
fun toggleSelectionNarysCircle(circle: ConicSectionNarys, state: MongeState) {
    val removed = state.selectedCirclesNarys.remove(circle)
    if (!removed) {
        state.selectedCirclesNarys.add(circle)
        selectSphereFamily(circle.parentId, state)
    } else {
        unselectSphereFamily(circle.parentId, state)
    }
}

private fun <T> MutableList<T>.addIfAbsent(item: T) {
    if (!this.contains(item)) this.add(item)
}

private fun selectSphereFamily(parentId: String?, state: MongeState) {
    if (parentId == null) return
    // 1) sféra
    state.spheres3D.find { it.id == parentId }?.let { sph ->
        state.selectedSpheres3D.addIfAbsent(sph)
        println("označena sféra $sph")
    }
    // 2) průměty sféry uložené jako kuželosečky
    state.conicsPudorys.findLast { it.parentId == parentId }?.let { cP ->
        state.selectedConicsPudorys.addIfAbsent(cP)
    }
    state.conicsNarys.findLast { it.parentId == parentId }?.let { cN ->
        state.selectedConicsNarys.addIfAbsent(cN)
    }
    state.conicsBokorys.findLast { it.parentId == parentId }?.let { cB ->
        state.selectedConicsBokorys.addIfAbsent(cB)
    }
    state.conicsAxo.filter { it.parentId == parentId }.forEach { cA ->
        state.selectedConicsAxo.addIfAbsent(cA)
    }

}

private fun unselectSphereFamily(parentId: String?, state: MongeState) {
    if (parentId == null) return
    state.spheres3D.find { it.id == parentId }?.let { sph ->
        state.selectedSpheres3D.remove(sph)
    }
    state.selectedConicsPudorys.removeAll { it.parentId == parentId }
    state.selectedConicsNarys.removeAll { it.parentId == parentId }
    state.selectedConicsBokorys.removeAll { it.parentId == parentId }
    state.selectedConicsAxo.removeAll { it.parentId == parentId }
}
fun toggleSelectionSphere3D(sphere: SphereSurface3D, state: MongeState) {
    // když byla vybraná → zruš výběr celé rodiny
    val wasSelected = state.selectedSpheres3D.remove(sphere)
    if (wasSelected) {
        state.selectedConicsPudorys.removeAll { it.parentId == sphere.id }
        state.selectedConicsNarys.removeAll { it.parentId == sphere.id }
        state.selectedConicsBokorys.removeAll { it.parentId == sphere.id }
        state.selectedConicsAxo.removeAll { it.parentId == sphere.id }
        println("⚪️ Zrušeno označení sféry ${sphere.name} a jejích projekcí.")
        return
    }

    // jinak přidej sféru + všechny její projekce
    state.conicsPudorys.filter { it.parentId == sphere.id }.forEach { c ->
        state.selectedConicsPudorys.add(c)
    }
    state.conicsNarys.filter { it.parentId == sphere.id }.forEach { c ->
        state.selectedConicsNarys.add(c)
    }
    state.conicsBokorys.filter { it.parentId == sphere.id }.forEach { c ->
        state.selectedConicsBokorys.add(c)
    }
    state.conicsAxo.filter { it.parentId == sphere.id }.forEach { c ->
        state.selectedConicsAxo.add(c)
    }
    state.selectedSpheres3D.add(sphere)

    println("🔵 Označena sféra ${sphere.name} a její projekce.")
}

/**
 * V konstrukci kulového konoidu má klik na libovolný průmět koule přednost
 * před obecným výběrem kuželosečky. Vrátí true, pokud klik zasáhl kouli.
 */
fun handleRuledSurfaceSphereCanvasSelection(state: MongeState): Boolean {
    if (state.drawobjects != Mongeobjects.RULED_SURFACE) return false

    val parentId = listOfNotNull(
        state.snappedConicPudorys?.parentId ?: state.snappedConicPudorys?.parent?.id,
        state.snappedConicNarys?.parentId ?: state.snappedConicNarys?.parent?.id,
        state.snappedConicBokorys?.parentId ?: state.snappedConicBokorys?.parent?.id,
        state.snappedConicAxo?.parentId ?: state.snappedConicAxo?.parent?.id,
        state.hoveredCirclePudorys?.parentId,
        state.hoveredCircleNarys?.parentId,
    ).firstOrNull { id -> state.spheres3D.any { it.id == id } } ?: return false

    val sphere = state.spheres3D.first { it.id == parentId }
    if (!state.isShiftPressed) clearSelection(state)
    toggleSelectionSphere3D(sphere, state)
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    return true
}
fun MongeState.setCircleArc(circleId: String, a: Offset, b: Offset, mode: ArcMode = ArcMode.SHORTEST) {
    circleArcEnds[circleId] = a to b
    circleArcMode[circleId] = mode
    triggerRedraw++
}
