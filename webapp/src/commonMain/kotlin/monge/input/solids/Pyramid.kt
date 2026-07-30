package monge.input.solids

import utils.withSuffixOnce
import androidx.compose.ui.graphics.Color
import serialization.commitSnapshot
import model.LineStyle
import model.Mongeobjects
import model.Offset3D
import model.Point3D
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.Segment2DAxo
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import model.classes.projectPoint3DToAxoLocal
import monge.input.axo.AxoRenderBasis
import monge.input.planeobjects.conicsections.createSegmentProjectionsFor
import monge.input.segments.addSegment3DAndDetectSolids
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.resetStavu
import utils.allocIndex
import utils.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Konstrukce JEHLANU.
 *
 * Vstup (stejný styl jako kužel – přes snapnuté objekty):
 *  1) RegularPolygon3D – podstava (vezme se z najeté/snapnuté hrany polygonu),
 *  2) Point3D – vrchol (apex) mimo rovinu podstavy.
 *
 * Po doplnění bočních hran (apex → každý vrchol podstavy) se těleso automaticky
 * detekuje jako [model.classes.SegmentSolidType.JEHLAN] v [addSegment3DAndDetectSolids].
 *
 * Funguje shodně v Monge, KOTO i Axo (rozdíl je jen v tom, přes který průmět se
 * objekty snapnou).
 */
fun handlePyramidToolClick(state: MongeState, axoConstruction: Boolean = false) {
    if (state.drawobjects != Mongeobjects.PYRAMID) return

    // --- 1) Pick podstavy (polygon) z najeté hrany v aktuálním pohledu ---
    fun pickSnappedPolygonId(): String? {
        val segId =
            state.snappedSegmentAxo?.let { it.parent?.id ?: it.parentId }
                ?: state.snappedSegmentPudorys?.parent?.id
                ?: state.snappedSegmentNarys?.parent?.id
                ?: state.snappedSegmentBokorys?.parent?.id
        if (segId != null) {
            state.polygons3D.firstOrNull { segId in it.segmentIds3D }?.let { return it.id }
        }
        // Fallback: ručně vybraný polygon
        return state.selectedPolygon?.id
    }

    // --- 2) Pick vrcholu (apex) z najetého bodu v aktuálním pohledu ---
    fun pickSnappedApexId(): String? {
        state.snappedAxoPoint?.parent?.id?.let { return it }
        state.snappedPointPudorys?.parent?.id?.let { return it }
        state.snappedPointNarys?.parent?.id?.let { return it }
        state.snappedPointBokorys?.parent?.id?.let { return it }
        return null
    }

    pickSnappedPolygonId()?.let { state.pendingPyramidPolygonId = it }
    pickSnappedApexId()?.let { state.pendingPyramidApexId = it }

    val polygonId = state.pendingPyramidPolygonId ?: return
    val apexId = state.pendingPyramidApexId ?: return

    val polygon = state.polygons3D.firstOrNull { it.id == polygonId } ?: return
    val apex = state.sharedPoints3D.firstOrNull { it.id == apexId } ?: return

    // Vrchol nesmí být zároveň vrcholem podstavy
    if (apex.id in polygon.vertexPointIds) return

    val baseVerts = polygon.vertexPointIds
        .mapNotNull { id -> state.sharedPoints3D.firstOrNull { it.id == id } }
    if (baseVerts.size < 3) return

    // --- Apex musí ležet MIMO rovinu podstavy ---
    val v0 = baseVerts[0]
    val v1 = baseVerts[1]
    val v2 = baseVerts[2]
    val ux = v1.x - v0.x; val uy = v1.y - v0.y; val uz = v1.z - v0.z
    val wx = v2.x - v0.x; val wy = v2.y - v0.y; val wz = v2.z - v0.z
    val nx = uy * wz - uz * wy
    val ny = uz * wx - ux * wz
    val nz = ux * wy - uy * wx
    val nlen = sqrt(nx * nx + ny * ny + nz * nz)
    if (nlen < 1e-6f) return
    val dist = ((apex.x - v0.x) * nx + (apex.y - v0.y) * ny + (apex.z - v0.z) * nz) / nlen
    if (abs(dist) < 1e-4f) {
        state.consInfo.value = "Vrchol jehlanu leží v rovině podstavy → degenerace."
        return
    }

    // --- Boční hrany: apex → každý vrchol podstavy ---
    val color = state.currentLineStyleSettings.color
    val width = state.currentLineStyleSettings.strokeWidth
    val style = state.currentLineStyleSettings.style

    val basis = state.basis
    val buildAxo = axoConstruction && basis != null

    val lateralSegIds = mutableListOf<String>()
    baseVerts.forEach { base ->
        val seg3 = Segment3D(
            id = UUID.randomUUID().toString(),
            start = base,
            end = apex,
            name = "",
            color = color,
            strokeWidth = width,
            creationIndex = allocIndex(state)
        )
        // V Monge/KOTO vytvoříme jen půdorys + nárys (jako dosud).
        // V Axu se průměty (všechny 4) doplní hromadně níže.
        if (!buildAxo) {
            createSegmentProjectionsFor(
                state = state,
                seg3 = seg3,
                nameBase = seg3.name,
                style = style,
                color = color,
                width = width
            )
        }
        addSegment3DAndDetectSolids(state, seg3)
        lateralSegIds += seg3.id
    }

    // V Axu: doplň axo + bokorys (+ chybějící půdorys/nárys) pro celé těleso
    // a nech viditelnou pouze axonometrickou projekci.
    if (buildAxo) {
        val segmentIds = (polygon.segmentIds3D + lateralSegIds).toHashSet()
        val pointIds = (polygon.vertexPointIds + apex.id).toHashSet()
        applyAxoOnlySolidVisibility(state, segmentIds, pointIds, basis!!, style, color, width)
    }

    state.pendingPyramidPolygonId = null
    state.pendingPyramidApexId = null
    state.consInfo.value = "Jehlan vytvořen."
    println("✅ Jehlan vytvořen: podstava=${polygon.name}, vrchol=${apex.name}")

    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
    state.triggerRedraw++
}

// Zajistí všechny 4 průměty (půdorys/nárys/bokorys/axo) pro úsečky a body tělesa
// a nastaví viditelnost tak, aby byla vidět pouze axonometrická projekce.
// Sdíleno mezi jehlanem a hranolem.
internal fun applyAxoOnlySolidVisibility(
    state: MongeState,
    segmentIds3D: Set<String>,
    vertexPointIds: Set<String>,
    basis: AxoRenderBasis,
    style: LineStyle,
    color: Color,
    width: Float
) {
    // 1) Doplň chybějící průměty úseček (zejména axo + bokorys u bočních hran).
    state.segments3D.filter { it.id in segmentIds3D }.forEach { seg3 ->
        ensureSolidSegmentProjections(state, seg3, basis, style, color, width)
    }

    // 2) Skryj v axu všechny průměty kromě axonometrického.
    state.pointsPudorys.filter { it.parent?.id in vertexPointIds }.forEach { it.showInAxo = false; it.showInAxoInitial = false }
    state.pointsNarys.filter { it.parent?.id in vertexPointIds }.forEach { it.showInAxo = false; it.showInAxoInitial = false }
    state.pointsBokorys.filter { it.parent?.id in vertexPointIds }.forEach { it.showInAxo = false; it.showInAxoInitial = false }
    state.pointsAxo.filter { it.parent?.id in vertexPointIds }.forEach { it.showInAxo = true; it.showInAxoInitial = true }

    state.segmentsPudorys.filter { it.parent?.id in segmentIds3D }.forEach { it.showInAxo = false; it.showInAxoInitial = false }
    state.segmentsNarys.filter { it.parent?.id in segmentIds3D }.forEach { it.showInAxo = false; it.showInAxoInitial = false }
    state.segmentsBokorys.filter { it.parent?.id in segmentIds3D }.forEach { it.showInAxo = false; it.showInAxoInitial = false }
    state.segmentsAxo.filter { it.parent?.id in segmentIds3D }.forEach { it.showInAxo = true; it.showInAxoInitial = true }
}

internal fun ensureSolidPudorys(state: MongeState, p3: Point3D): Point3DPudorys =
    state.pointsPudorys.firstOrNull { it.parent?.id == p3.id }
        ?: Point3DPudorys(
            x = p3.x, y = p3.y,
            name = p3.name.withSuffixOnce("₁"),
            parent = p3, isSegmentEndpoint = true,
            creationIndex = allocIndex(state)
        ).also { state.pointsPudorys.add(it) }

internal fun ensureSolidNarys(state: MongeState, p3: Point3D): Point3DNarys =
    state.pointsNarys.firstOrNull { it.parent?.id == p3.id }
        ?: Point3DNarys(
            x = p3.x, z = p3.z,
            name = p3.name.withSuffixOnce("₂"),
            parent = p3, isSegmentEndpoint = true,
            creationIndex = allocIndex(state)
        ).also { state.pointsNarys.add(it) }

internal fun ensureSolidBokorys(state: MongeState, p3: Point3D): Point3DBokorys =
    state.pointsBokorys.firstOrNull { it.parent?.id == p3.id }
        ?: Point3DBokorys(
            y = p3.y, z = p3.z,
            name = p3.name.withSuffixOnce("₃"),
            parent = p3, isSegmentEndpoint = true,
            creationIndex = allocIndex(state)
        ).also { state.pointsBokorys.add(it) }

internal fun ensureSolidAxo(state: MongeState, p3: Point3D, basis: AxoRenderBasis): Point3DAxo =
    state.pointsAxo.firstOrNull { it.parent?.id == p3.id }
        ?: run {
            val a = projectPoint3DToAxoLocal(Offset3D(p3.x, p3.y, p3.z), basis)
            Point3DAxo(
                x = a.x, y = a.y,
                name = p3.name.withSuffixOnce("ₐ"),
                parent = p3, isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            ).also { state.pointsAxo.add(it) }
        }

internal fun ensureSolidSegmentProjections(
    state: MongeState,
    seg3: Segment3D,
    basis: AxoRenderBasis,
    style: LineStyle,
    color: Color,
    width: Float
) {
    if (state.segmentsPudorys.none { it.parent?.id == seg3.id }) {
        val s = ensureSolidPudorys(state, seg3.start)
        val e = ensureSolidPudorys(state, seg3.end)
        Segment2DPudorys(
            start = s, end = e, name = seg3.name.withSuffixOnce("₁"),
            parent = seg3, parentId = seg3.id,
            localColor = color, localLineStyle = style, localStrokeWidth = width,
            creationIndex = seg3.creationIndex
        ).also { s.parentSegment = it; e.parentSegment = it; state.segmentsPudorys.add(it) }
    }
    if (state.segmentsNarys.none { it.parent?.id == seg3.id }) {
        val s = ensureSolidNarys(state, seg3.start)
        val e = ensureSolidNarys(state, seg3.end)
        Segment2DNarys(
            start = s, end = e, name = seg3.name.withSuffixOnce("₂"),
            parent = seg3, parentId = seg3.id,
            localColor = color, localLineStyle = style, localStrokeWidth = width,
            creationIndex = seg3.creationIndex
        ).also { s.parentSegment = it; e.parentSegment = it; state.segmentsNarys.add(it) }
    }
    if (state.segmentsBokorys.none { it.parent?.id == seg3.id }) {
        val s = ensureSolidBokorys(state, seg3.start)
        val e = ensureSolidBokorys(state, seg3.end)
        Segment2DBokorys(
            start = s, end = e, name = seg3.name.withSuffixOnce("₃"),
            parent = seg3, parentId = seg3.id,
            localColor = color, localLineStyle = style, localStrokeWidth = width,
            creationIndex = seg3.creationIndex
        ).also { s.parentSegment = it; e.parentSegment = it; state.segmentsBokorys.add(it) }
    }
    if (state.segmentsAxo.none { it.parent?.id == seg3.id }) {
        val s = ensureSolidAxo(state, seg3.start, basis)
        val e = ensureSolidAxo(state, seg3.end, basis)
        Segment2DAxo(
            start = s, end = e, name = seg3.name.withSuffixOnce("ₐ"),
            parent = seg3, parentId = seg3.id,
            localColor = color, localLineStyle = style, localStrokeWidth = width,
            creationIndex = seg3.creationIndex
        ).also { s.parentSegment = it; e.parentSegment = it; state.segmentsAxo.add(it) }
    }
}
