package monge.input.solids

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.DrawingModeMonge
import model.Mongeobjects
import model.Offset3D
import model.Point3D
import model.classes.RegularPolygon3D
import model.classes.Segment3D
import monge.input.axo.points.screenToAxoOverlayLocal
import monge.input.planeobjects.conicsections.createSegmentProjectionsFor
import monge.input.quadrics.cylindricalsurface.computePerpCylinderT
import monge.input.quadrics.cylindricalsurface.computePerpCylinderTAxo
import monge.input.segments.addSegment3DAndDetectSolids
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.resetStavu
import utils.allocIndex
import utils.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Konstrukce KOLMÉHO HRANOLU – obdoba kolmého válce.
 *
 *  1) klik na hranu podstavy (RegularPolygon3D) → uloží se podstava + normála roviny,
 *  2) kurzor nastavuje výšku (kolmo na rovinu podstavy), klikem se hranol vytvoří.
 *
 * Vysunutím podstavy vzniknou horní vrcholy, horní hrany a boční hrany; navazující
 * úsečky se automaticky detekují jako [model.classes.SegmentSolidType.HRANOL].
 *
 * V Axu (axoConstruction) se dotvoří všechny 4 průměty a viditelná zůstane jen
 * axonometrie – stejně jako u jehlanu.
 */
fun handlePrismToolClick(
    state: MongeState,
    logicalCursor: Offset,
    axoConstruction: Boolean = false
) {
    if (state.drawobjects != Mongeobjects.PRISM) return

    // ── Fáze 1: výběr podstavy ──
    if (state.pendingPrismPolygonId == null) {
        val polygonId = pickSnappedPolygonId(state) ?: return
        val polygon = state.polygons3D.firstOrNull { it.id == polygonId } ?: return
        val baseVerts = polygon.vertexPointIds
            .mapNotNull { id -> state.sharedPoints3D.firstOrNull { it.id == id } }
        if (baseVerts.size < 3) return

        val normal = basePlaneNormal(baseVerts) ?: run {
            state.consInfo.value = "Nelze určit normálu roviny podstavy."
            return
        }
        // Pokud je normála v aktuálním pohledu degenerovaná (rovina je kolmá na pohled),
        // přepni pohled tak, aby šlo výšku zadat – stejně jako u kolmého válce.
        if (!axoConstruction) {
            val projP = sqrt(normal.x * normal.x + normal.y * normal.y)
            val projN = sqrt(normal.x * normal.x + normal.z * normal.z)
            val eps = 1e-4f
            if (state.mongeMode == DrawingModeMonge.PUDORYS && projP < eps && projN >= eps) {
                state.mongeMode = DrawingModeMonge.NARYS
            } else if (state.mongeMode == DrawingModeMonge.NARYS && projN < eps && projP >= eps) {
                state.mongeMode = DrawingModeMonge.PUDORYS
            }
        }
        state.pendingPrismPolygonId = polygon.id
        state.pendingPrismNormal = normal
        state.pendingPrismBaseCenter = centroid(baseVerts)
        state.consInfo.value = "Kurzorem nastavte výšku hranolu a klikněte."
        state.triggerRedraw++
        return
    }

    // ── Fáze 2: výška + stavba ──
    val polygon = state.polygons3D.firstOrNull { it.id == state.pendingPrismPolygonId } ?: run {
        resetPrismPending(state); return
    }
    val normal = state.pendingPrismNormal ?: return
    val baseCenter = state.pendingPrismBaseCenter ?: return
    val basis = state.basis

    val cursor = if (axoConstruction && basis != null) {
        state.snappedPointLogical ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
    } else {
        logicalCursor
    }
    val t = if (axoConstruction && basis != null) {
        computePerpCylinderTAxo(cursor, baseCenter, normal, basis)
    } else {
        computePerpCylinderT(state.mongeMode, cursor, baseCenter, normal)
    } ?: return

    if (abs(t) < 1e-3f) {
        state.consInfo.value = "Nulová výška – posuňte kurzor dál od podstavy."
        return
    }

    val baseVerts = polygon.vertexPointIds
        .mapNotNull { id -> state.sharedPoints3D.firstOrNull { it.id == id } }
    if (baseVerts.size < 3) { resetPrismPending(state); return }

    val color = state.currentLineStyleSettings.color
    val width = state.currentLineStyleSettings.strokeWidth
    val style = state.currentLineStyleSettings.style

    val buildAxo = axoConstruction && basis != null
    val n = baseVerts.size

    // Horní vrcholy (vysunuté o normálu * t)
    val topVerts = baseVerts.map { p ->
        Point3D(
            x = p.x + normal.x * t,
            y = p.y + normal.y * t,
            z = p.z + normal.z * t,
            name = if (p.name.isNotEmpty()) "${p.name}'" else "",
            color = color,
            creationIndex = allocIndex(state)
        ).also { state.sharedPoints3D.add(it) }
    }

    val newSegIds = mutableListOf<String>()
    fun addEdge(a: Point3D, b: Point3D) {
        val seg3 = Segment3D(
            id = UUID.randomUUID().toString(),
            start = a, end = b,
            name = "",
            color = color, strokeWidth = width,
            creationIndex = allocIndex(state)
        )
        if (!buildAxo) {
            createSegmentProjectionsFor(state, seg3, nameBase = seg3.name, style = style, color = color, width = width)
        }
        addSegment3DAndDetectSolids(state, seg3)
        newSegIds += seg3.id
    }

    // Horní podstava
    for (i in 0 until n) addEdge(topVerts[i], topVerts[(i + 1) % n])
    // Boční hrany
    for (i in 0 until n) addEdge(baseVerts[i], topVerts[i])

    if (buildAxo) {
        val segmentIds = (polygon.segmentIds3D + newSegIds).toHashSet()
        val pointIds = (polygon.vertexPointIds + topVerts.map { it.id }).toHashSet()
        applyAxoOnlySolidVisibility(state, segmentIds, pointIds, basis!!, style, color, width)
    }

    resetPrismPending(state)
    state.consInfo.value = "Kolmý hranol vytvořen."
    println("✅ Kolmý hranol vytvořen: podstava=${polygon.name}, výška=$t")

    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
    state.triggerRedraw++
}

internal fun resetPrismPending(state: MongeState) {
    state.pendingPrismPolygonId = null
    state.pendingPrismNormal = null
    state.pendingPrismBaseCenter = null
}

// Podstava JEHLANU/HRANOLU, na kterou se právě čeká druhý vstup (vrchol / výška).
// Slouží k zvýraznění hran podstavy v UI (stejně jako u ostatních rozestavěných konstrukcí).
fun pendingSolidBasePolygon(state: MongeState): RegularPolygon3D? {
    val polygonId = when (state.drawobjects) {
        Mongeobjects.PYRAMID -> state.pendingPyramidPolygonId
        Mongeobjects.PRISM -> state.pendingPrismPolygonId
        else -> null
    } ?: return null
    return state.polygons3D.firstOrNull { it.id == polygonId }
}

// Výběr podstavy z najeté hrany polygonu (fallback: ručně vybraný polygon).
internal fun pickSnappedPolygonId(state: MongeState): String? {
    val segId =
        state.snappedSegmentAxo?.let { it.parent?.id ?: it.parentId }
            ?: state.snappedSegmentPudorys?.parent?.id
            ?: state.snappedSegmentNarys?.parent?.id
            ?: state.snappedSegmentBokorys?.parent?.id
    if (segId != null) {
        state.polygons3D.firstOrNull { segId in it.segmentIds3D }?.let { return it.id }
    }
    return state.selectedPolygon?.id
}

internal fun basePlaneNormal(verts: List<Point3D>): Offset3D? {
    if (verts.size < 3) return null
    val v0 = verts[0]; val v1 = verts[1]; val v2 = verts[2]
    val ux = v1.x - v0.x; val uy = v1.y - v0.y; val uz = v1.z - v0.z
    val wx = v2.x - v0.x; val wy = v2.y - v0.y; val wz = v2.z - v0.z
    val nx = uy * wz - uz * wy
    val ny = uz * wx - ux * wz
    val nz = ux * wy - uy * wx
    val len = sqrt(nx * nx + ny * ny + nz * nz)
    if (len < 1e-6f) return null
    return Offset3D(nx / len, ny / len, nz / len)
}

internal fun centroid(verts: List<Point3D>): Offset3D {
    var sx = 0f; var sy = 0f; var sz = 0f
    verts.forEach { sx += it.x; sy += it.y; sz += it.z }
    val k = verts.size.toFloat()
    return Offset3D(sx / k, sy / k, sz / k)
}
