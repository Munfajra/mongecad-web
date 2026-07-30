package monge.input.solids

import serialization.commitSnapshot
import model.Mongeobjects
import model.Offset3D
import model.Point3D
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import kotlin.math.sqrt

/**
 * Platónská tělesa – jen jednoduché případy:
 *  - pravidelný 4-úhelník → KRYCHLE (kolmý hranol s výškou = délka hrany),
 *  - pravidelný 3-úhelník → ČTYŘSTĚN (jehlan s vrcholem nad těžištěm, h = a·√6/3).
 *
 * 1) klik označí podstavu; nevyhovuje-li (jiné n), konstrukce se zruší,
 * 2) zobrazí se čárkovaný náhled; pravým tlačítkem se překlopí strana (nad/pod rovinu),
 *    levým klikem se těleso potvrdí.
 *
 * V Axu se dotvoří všechny průměty a viditelná zůstane jen axonometrie (jako u ostatních těles).
 */
fun handlePlatonicToolClick(state: MongeState, axoConstruction: Boolean = false) {
    if (state.drawobjects != Mongeobjects.PLATONIC_SOLID) return

    // ── Fáze 1: výběr podstavy ──
    if (state.pendingPlatonicPolygonId == null) {
        val polygonId = pickSnappedPolygonId(state) ?: return
        val polygon = state.polygons3D.firstOrNull { it.id == polygonId } ?: return
        if (polygon.n != 3 && polygon.n != 4) {
            state.consInfo.value = "Platónské těleso: podporován je jen pravidelný 3- nebo 4-úhelník."
            return
        }
        state.pendingPlatonicPolygonId = polygon.id
        state.platonicFlip = false
        updateConstructionInfo(state)
        state.triggerRedraw++
        return
    }

    // ── Fáze 2: potvrzení (levý klik) ──
    val polygon = state.polygons3D.firstOrNull { it.id == state.pendingPlatonicPolygonId } ?: run {
        resetPlatonicPending(state); return
    }
    val baseVerts = polygon.vertexPointIds
        .mapNotNull { id -> state.sharedPoints3D.firstOrNull { it.id == id } }
    if (baseVerts.size < 3) { resetPlatonicPending(state); return }
    val normal = basePlaneNormal(baseVerts) ?: run { resetPlatonicPending(state); return }

    val basis = state.basis
    val buildAxo = axoConstruction && basis != null
    val sign = if (state.platonicFlip) -1f else 1f

    val color = state.currentLineStyleSettings.color
    val width = state.currentLineStyleSettings.strokeWidth
    val style = state.currentLineStyleSettings.style

    val newSegIds = mutableListOf<String>()
    val newPointIds = mutableListOf<String>()

    if (polygon.n == 4) {
        // KRYCHLE = kolmý hranol, výška = délka hrany podstavy
        val a = solidEdgeLength(baseVerts[0], baseVerts[1])
        val off = normal * (sign * a)
        val topVerts = baseVerts.map { p ->
            Point3D(
                x = p.x + off.x, y = p.y + off.y, z = p.z + off.z,
                name = if (p.name.isNotEmpty()) "${p.name}'" else "",
                color = color, creationIndex = allocIndex(state)
            ).also { state.sharedPoints3D.add(it) }
        }
        newPointIds += topVerts.map { it.id }
        val n = baseVerts.size
        for (i in 0 until n) newSegIds += addSolidEdge3D(state, topVerts[i], topVerts[(i + 1) % n], buildAxo, color, width, style)
        for (i in 0 until n) newSegIds += addSolidEdge3D(state, baseVerts[i], topVerts[i], buildAxo, color, width, style)
    } else {
        // ČTYŘSTĚN = pravidelný jehlan, vrchol nad těžištěm ve výšce a·√6/3
        val a = solidEdgeLength(baseVerts[0], baseVerts[1])
        val h = a * (sqrt(6f) / 3f)
        val c = centroid(baseVerts)
        val apex = Point3D(
            x = c.x + normal.x * sign * h,
            y = c.y + normal.y * sign * h,
            z = c.z + normal.z * sign * h,
            name = "V", color = color, creationIndex = allocIndex(state)
        ).also { state.sharedPoints3D.add(it) }
        newPointIds += apex.id
        baseVerts.forEach { base -> newSegIds += addSolidEdge3D(state, base, apex, buildAxo, color, width, style) }
    }

    if (buildAxo) {
        val segmentIds = (polygon.segmentIds3D + newSegIds).toHashSet()
        val pointIds = (polygon.vertexPointIds + newPointIds).toHashSet()
        applyAxoOnlySolidVisibility(state, segmentIds, pointIds, basis!!, style, color, width)
    }

    val label = if (polygon.n == 4) "Krychle" else "Čtyřstěn"
    resetPlatonicPending(state)
    state.consInfo.value = "$label vytvořen."
    println("✅ $label vytvořeno: podstava=${polygon.name}")

    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
    state.triggerRedraw++
}

internal fun resetPlatonicPending(state: MongeState) {
    state.pendingPlatonicPolygonId = null
    state.platonicFlip = false
}

// Geometrie náhledu/tělesa: vrátí hrany (dvojice 3D bodů) podle aktuální orientace.
internal fun platonicPreviewEdges(state: MongeState): List<Pair<Offset3D, Offset3D>>? {
    val polygonId = state.pendingPlatonicPolygonId ?: return null
    val polygon = state.polygons3D.firstOrNull { it.id == polygonId } ?: return null
    val baseVerts = polygon.vertexPointIds
        .mapNotNull { id -> state.sharedPoints3D.firstOrNull { it.id == id } }
    if (baseVerts.size < 3) return null
    val normal = basePlaneNormal(baseVerts) ?: return null
    val sign = if (state.platonicFlip) -1f else 1f
    val n = baseVerts.size
    val baseP = baseVerts.map { Offset3D(it.x, it.y, it.z) }

    val edges = ArrayList<Pair<Offset3D, Offset3D>>()
    for (i in 0 until n) edges += baseP[i] to baseP[(i + 1) % n]

    if (polygon.n == 4) {
        val a = solidEdgeLength(baseVerts[0], baseVerts[1])
        val off = normal * (sign * a)
        val top = baseP.map { it + off }
        for (i in 0 until n) edges += top[i] to top[(i + 1) % n]
        for (i in 0 until n) edges += baseP[i] to top[i]
    } else {
        val a = solidEdgeLength(baseVerts[0], baseVerts[1])
        val h = a * (sqrt(6f) / 3f)
        val c = centroid(baseVerts)
        val apex = Offset3D(c.x + normal.x * sign * h, c.y + normal.y * sign * h, c.z + normal.z * sign * h)
        baseP.forEach { edges += it to apex }
    }
    return edges
}
