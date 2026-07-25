package monge.input.selection

import utils.System
import draw.mongescreen.labels.clearSelection
import model.Point3D
import model.classes.ConicSectionAxo
import model.classes.ConicSectionBokorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.ConicalSurface3D
import model.classes.CylindricalSurface3D
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.Segment2DAxo
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.SegmentsBokorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys
import state.MongeState

fun handleSelectionCompositeObjectParts(state: MongeState): Boolean {
    // Klik na "volnou" kuželosečku (typicky průnikovou) nesmí popadnout těleso/plochu,
    // na které leží. Průnikové kuželosečky geometricky leží na kuželi/válci/solidu, takže
    // se vedle nich vždy snapne i obrysová úsečka plochy; ta jinak výběr ukradne dřív, než
    // se ke kuželosečce dostaneme. Pokud je tedy snapnutá kuželosečka, která nepatří žádné
    // ploše, raději ji necháme vybrat normální cestou (handleSelectionPudorysLine atd.).
    if (snappedFreeConicPart(state) != null) return false

    snappedSegment3DId(state)?.let { segmentId ->
        state.segmentSolids3D
            .firstOrNull { segmentId in it.segmentIds3D }
            ?.let {
                toggleSegmentSolidSelection(state, it.id, clearOthers = !state.isShiftPressed)
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                return true
            }
    }

    snappedPolygonSegment(state)?.let { polygonId ->
        togglePolygonSelection(state, polygonId, clearOthers = !state.isShiftPressed)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        return true
    }

    snappedPoint3DId(state)?.let { pointId ->
        state.segmentSolids3D
            .firstOrNull { pointId in it.vertexPointIds }
            ?.let {
                toggleSegmentSolidSelection(state, it.id, clearOthers = !state.isShiftPressed)
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                return true
            }
    }

    snappedSurfacePart(state)?.let { part ->
        findConeForPart(state, part)?.let {
            toggleWholeConicalSurfaceSelection(state, it, clearOthers = !state.isShiftPressed)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return true
        }
        findCylinderForPart(state, part)?.let {
            toggleWholeCylindricalSurfaceSelection(state, it, clearOthers = !state.isShiftPressed)
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            return true
        }
    }

    return false
}

private data class SurfacePart(
    val segmentId: String? = null,
    val conicParentId: String? = null,
    val pointId: String? = null,
    val pointParentId: String? = null,
)

private fun snappedSurfacePart(state: MongeState): SurfacePart? =
    snappedSegmentSurfacePart(state)
        ?: snappedConicSurfacePart(state)
        ?: snappedPointSurfacePart(state)

private fun snappedSegmentSurfacePart(state: MongeState): SurfacePart? {
    state.snappedSegmentPudorys?.let { return SurfacePart(segmentId = it.id) }
    state.snappedSegmentNarys?.let { return SurfacePart(segmentId = it.id) }
    state.snappedSegmentBokorys?.let { return SurfacePart(segmentId = it.id) }
    state.snappedSegmentAxo?.let { return SurfacePart(segmentId = it.id) }
    return null
}

private fun snappedConicSurfacePart(state: MongeState): SurfacePart? {
    state.snappedConicPudorys?.let { return SurfacePart(conicParentId = it.parentIdOrParent()) }
    state.snappedConicNarys?.let { return SurfacePart(conicParentId = it.parentIdOrParent()) }
    state.snappedConicBokorys?.let { return SurfacePart(conicParentId = it.parentIdOrParent()) }
    state.snappedConicAxo?.let { return SurfacePart(conicParentId = it.parentIdOrParent()) }
    return null
}

/**
 * Snapnutá kuželosečka, která nepatří žádné kuželové ani válcové ploše (tedy je „volná“ –
 * např. průniková kuželosečka). Slouží k tomu, aby výběr plochy/tělesa ustoupil přímému
 * výběru takové kuželosečky. Vrací null, když žádná kuželosečka snapnutá není nebo když
 * snapnutá kuželosečka je řídicí/obrysová křivka některé plochy.
 */
private fun snappedFreeConicPart(state: MongeState): SurfacePart? {
    val conicPart = snappedConicSurfacePart(state) ?: return null
    if (findConeForPart(state, conicPart) != null) return null
    if (findCylinderForPart(state, conicPart) != null) return null
    return conicPart
}

private fun snappedPointSurfacePart(state: MongeState): SurfacePart? {
    state.snappedPointPudorys?.let { return SurfacePart(pointId = it.id, pointParentId = it.parent?.id) }
    state.snappedPointNarys?.let { return SurfacePart(pointId = it.id, pointParentId = it.parent?.id) }
    state.snappedPointBokorys?.let { return SurfacePart(pointId = it.id, pointParentId = it.parent?.id) }
    state.snappedAxoPoint?.let { return SurfacePart(pointId = it.id, pointParentId = it.parent?.id) }
    return null
}

private fun snappedSegment3DId(state: MongeState): String? {
    state.snappedSegmentPudorys?.let { return it.parentIdOrParent() }
    state.snappedSegmentNarys?.let { return it.parentIdOrParent() }
    state.snappedSegmentBokorys?.let { return it.parentIdOrParent() }
    state.snappedSegmentAxo?.let { return it.parent?.id ?: it.parentId }
    return null
}

private fun snappedPoint3DId(state: MongeState): String? {
    state.snappedPointPudorys?.parent?.id?.let { return it }
    state.snappedPointNarys?.parent?.id?.let { return it }
    state.snappedPointBokorys?.parent?.id?.let { return it }
    state.snappedAxoPoint?.parent?.id?.let { return it }
    return null
}

private fun snappedPolygonSegment(state: MongeState): String? {
    val segment3DId = snappedSegment3DId(state)
    val pudorysId = state.snappedSegmentPudorys?.id
    val narysId = state.snappedSegmentNarys?.id
    val bokorysId = state.snappedSegmentBokorys?.id
    val axoId = state.snappedSegmentAxo?.id

    return state.polygons3D
        .filter { polygon ->
            segment3DId in polygon.segmentIds3D ||
                pudorysId in polygon.segmentIdsPudorys ||
                narysId in polygon.segmentIdsNarys ||
                axoId in polygon.segmentIdsAxo ||
                bokorysSegmentBelongsToPolygon(state, bokorysId, polygon.segmentIds3D)
        }
        .maxByOrNull { it.creationIndex }
        ?.id
}

private fun bokorysSegmentBelongsToPolygon(
    state: MongeState,
    segmentId: String?,
    polygonSegmentIds3D: List<String>,
): Boolean {
    if (segmentId == null) return false
    return state.segmentsBokorys
        .firstOrNull { it.id == segmentId }
        ?.let { (it.parent?.id ?: it.parentId) in polygonSegmentIds3D }
        ?: false
}

private fun findConeForPart(state: MongeState, part: SurfacePart): ConicalSurface3D? {
    // POZOR: null se nesmí porovnávat na shodu – apexProj*Id (bokorys/axo) jsou v Monge
    // módu null, takže `part.pointId (== null) == cone.apexProjBokorysId (== null)` by
    // jinak označilo VŠECHNY kužely a vybral by se poslední přidaný.
    val conicParentId = part.conicParentId
    val pointParentId = part.pointParentId
    val pointId = part.pointId
    val segmentId = part.segmentId
    return state.conicalSurfaces
        .filter { cone ->
            (conicParentId != null && conicParentId == cone.directrixId) ||
                (pointParentId != null && pointParentId == cone.apexId) ||
                (pointId != null && (
                    pointId == cone.apexProjPudorysId ||
                        pointId == cone.apexProjNarysId ||
                        pointId == cone.apexProjBokorysId ||
                        pointId == cone.apexProjAxoId ||
                        pointId in cone.edgePointIdsPudorys2D ||
                        pointId in cone.edgePointIdsNarys2D ||
                        pointId in cone.edgePointIdsBokorys2D ||
                        pointId in cone.edgePointIdsAxo2D
                    )) ||
                (segmentId != null && (
                    segmentId in cone.edgeSegIdsPudorys2D ||
                        segmentId in cone.edgeSegIdsNarys2D ||
                        segmentId in cone.edgeSegIdsBokorys2D ||
                        segmentId in cone.edgeSegIdsAxo2D ||
                        segmentHasSurfaceId(state, segmentId, cone.id)
                    ))
        }
        .maxByOrNull { it.creationIndex }
}

private fun findCylinderForPart(state: MongeState, part: SurfacePart): CylindricalSurface3D? {
    val conicParentId = part.conicParentId
    val pointId = part.pointId
    val segmentId = part.segmentId
    return state.cylindricalSurfaces
        .filter { cylinder ->
            val conicIds = setOfNotNull(cylinder.directrixId, cylinder.lowerConicId, cylinder.upperConicId)
            (conicParentId != null && conicParentId in conicIds) ||
                (pointId != null && (
                    pointId in cylinder.edgePointIdsPudorys2D ||
                        pointId in cylinder.edgePointIdsNarys2D ||
                        pointId in cylinder.edgePointIdsBokorys2D ||
                        pointId in cylinder.edgePointIdsAxo2D
                    )) ||
                (segmentId != null && (
                    segmentId in cylinder.edgeSegIdsPudorys2D ||
                        segmentId in cylinder.edgeSegIdsNarys2D ||
                        segmentId in cylinder.edgeSegIdsBokorys2D ||
                        segmentId in cylinder.edgeSegIdsAxo2D ||
                        segmentHasSurfaceId(state, segmentId, cylinder.id)
                    ))
        }
        .maxByOrNull { it.creationIndex }
}

private fun segmentHasSurfaceId(state: MongeState, segmentId: String?, surfaceId: String): Boolean {
    if (segmentId == null) return false
    return state.segmentsPudorys.any { it.id == segmentId && it.isConicalSilhouette && it.conicalSurfaceId == surfaceId } ||
        state.segmentsNarys.any { it.id == segmentId && it.isConicalSilhouette && it.conicalSurfaceId == surfaceId } ||
        state.segmentsBokorys.any { it.id == segmentId && it.isConicalSilhouette && it.conicalSurfaceId == surfaceId } ||
        state.segmentsAxo.any { it.id == segmentId && it.isConicalSilhouette && it.conicalSurfaceId == surfaceId }
}

fun toggleWholeConicalSurfaceSelection(
    state: MongeState,
    surface: ConicalSurface3D,
    clearOthers: Boolean = false,
) {
    if (clearOthers) clearSelection(state)
    val already = state.selectedCone.any { it.id == surface.id }
    if (already && !clearOthers) {
        unselectConicalSurfaceParts(state, surface)
        return
    }

    selectConicalSurfaceParts(state, surface)
}

fun toggleWholeCylindricalSurfaceSelection(
    state: MongeState,
    surface: CylindricalSurface3D,
    clearOthers: Boolean = false,
) {
    if (clearOthers) clearSelection(state)
    val already = state.selectedCylinder.any { it.id == surface.id }
    if (already && !clearOthers) {
        unselectCylindricalSurfaceParts(state, surface)
        return
    }

    selectCylindricalSurfaceParts(state, surface)
}

fun selectConicalSurfaceParts(state: MongeState, surface: ConicalSurface3D) {
    addConicProjections(state, setOf(surface.directrixId))
    addSegments(state, surface.edgeSegIdsPudorys2D, surface.edgeSegIdsNarys2D, surface.edgeSegIdsBokorys2D, surface.edgeSegIdsAxo2D, surface.id)
    addPoints(
        state = state,
        point3DIds = setOf(surface.apexId),
        pudorysIds = surface.edgePointIdsPudorys2D + listOfNotNull(surface.apexProjPudorysId),
        narysIds = surface.edgePointIdsNarys2D + listOfNotNull(surface.apexProjNarysId),
        bokorysIds = surface.edgePointIdsBokorys2D + listOfNotNull(surface.apexProjBokorysId),
        axoIds = surface.edgePointIdsAxo2D + listOfNotNull(surface.apexProjAxoId),
    )
    if (state.selectedCone.none { it.id == surface.id }) state.selectedCone.add(surface)
    state.selectedConicalSurface = surface
    state.triggerRedraw++
}

fun selectCylindricalSurfaceParts(state: MongeState, surface: CylindricalSurface3D) {
    addConicProjections(state, setOfNotNull(surface.directrixId, surface.lowerConicId, surface.upperConicId))
    addSegments(state, surface.edgeSegIdsPudorys2D, surface.edgeSegIdsNarys2D, surface.edgeSegIdsBokorys2D, surface.edgeSegIdsAxo2D, surface.id)
    addPoints(
        state = state,
        point3DIds = emptySet(),
        pudorysIds = surface.edgePointIdsPudorys2D,
        narysIds = surface.edgePointIdsNarys2D,
        bokorysIds = surface.edgePointIdsBokorys2D,
        axoIds = surface.edgePointIdsAxo2D,
    )
    if (state.selectedCylinder.none { it.id == surface.id }) state.selectedCylinder.add(surface)
    state.selectedCylindricalSurface = surface
    state.triggerRedraw++
}

private fun unselectConicalSurfaceParts(state: MongeState, surface: ConicalSurface3D) {
    removeConicProjections(state, setOf(surface.directrixId))
    removeSegments(state, surface.edgeSegIdsPudorys2D, surface.edgeSegIdsNarys2D, surface.edgeSegIdsBokorys2D, surface.edgeSegIdsAxo2D, surface.id)
    removePoints(
        state = state,
        point3DIds = setOf(surface.apexId),
        pudorysIds = surface.edgePointIdsPudorys2D + listOfNotNull(surface.apexProjPudorysId),
        narysIds = surface.edgePointIdsNarys2D + listOfNotNull(surface.apexProjNarysId),
        bokorysIds = surface.edgePointIdsBokorys2D + listOfNotNull(surface.apexProjBokorysId),
        axoIds = surface.edgePointIdsAxo2D + listOfNotNull(surface.apexProjAxoId),
    )
    state.selectedCone.removeAll { it.id == surface.id }
    if (state.selectedConicalSurface?.id == surface.id) state.selectedConicalSurface = null
    state.triggerRedraw++
}

private fun unselectCylindricalSurfaceParts(state: MongeState, surface: CylindricalSurface3D) {
    removeConicProjections(state, setOfNotNull(surface.directrixId, surface.lowerConicId, surface.upperConicId))
    removeSegments(state, surface.edgeSegIdsPudorys2D, surface.edgeSegIdsNarys2D, surface.edgeSegIdsBokorys2D, surface.edgeSegIdsAxo2D, surface.id)
    removePoints(
        state = state,
        point3DIds = emptySet(),
        pudorysIds = surface.edgePointIdsPudorys2D,
        narysIds = surface.edgePointIdsNarys2D,
        bokorysIds = surface.edgePointIdsBokorys2D,
        axoIds = surface.edgePointIdsAxo2D,
    )
    state.selectedCylinder.removeAll { it.id == surface.id }
    if (state.selectedCylindricalSurface?.id == surface.id) state.selectedCylindricalSurface = null
    state.triggerRedraw++
}

private fun addConicProjections(state: MongeState, parentIds: Set<String>) {
    state.conicsPudorys.filter { it.parentIdOrParent() in parentIds }.forEach { state.selectedConicsPudorys.addById(it) }
    state.conicsNarys.filter { it.parentIdOrParent() in parentIds }.forEach { state.selectedConicsNarys.addById(it) }
    state.conicsBokorys.filter { it.parentIdOrParent() in parentIds }.forEach { state.selectedConicsBokorys.addById(it) }
    state.conicsAxo.filter { it.parentIdOrParent() in parentIds }.forEach { state.selectedConicsAxo.addById(it) }
}

private fun removeConicProjections(state: MongeState, parentIds: Set<String>) {
    state.selectedConicsPudorys.removeAll { it.parentIdOrParent() in parentIds }
    state.selectedConicsNarys.removeAll { it.parentIdOrParent() in parentIds }
    state.selectedConicsBokorys.removeAll { it.parentIdOrParent() in parentIds }
    state.selectedConicsAxo.removeAll { it.parentIdOrParent() in parentIds }
}

private fun addSegments(
    state: MongeState,
    pudorysIds: Collection<String>,
    narysIds: Collection<String>,
    bokorysIds: Collection<String>,
    axoIds: Collection<String>,
    surfaceId: String,
) {
    state.segmentsPudorys
        .filter { it.id in pudorysIds || (it.isConicalSilhouette && it.conicalSurfaceId == surfaceId) }
        .forEach { segment -> if (state.selectedSegmentsPudorys.none { it.id == segment.id }) state.selectedSegmentsPudorys.add(segment) }
    state.segmentsNarys
        .filter { it.id in narysIds || (it.isConicalSilhouette && it.conicalSurfaceId == surfaceId) }
        .forEach { segment -> if (state.selectedSegmentsNarys.none { it.id == segment.id }) state.selectedSegmentsNarys.add(segment) }
    state.segmentsBokorys
        .filter { it.id in bokorysIds || (it.isConicalSilhouette && it.conicalSurfaceId == surfaceId) }
        .forEach { segment -> if (state.selectedSegmentsBokorys.none { it.id == segment.id }) state.selectedSegmentsBokorys.add(segment) }
    state.segmentsAxo.filter { it.id in axoIds || (it.isConicalSilhouette && it.conicalSurfaceId == surfaceId) }.forEach { state.selectedSegmentsAxo.addById(it) }
}

private fun removeSegments(
    state: MongeState,
    pudorysIds: Collection<String>,
    narysIds: Collection<String>,
    bokorysIds: Collection<String>,
    axoIds: Collection<String>,
    surfaceId: String,
) {
    state.selectedSegmentsPudorys.removeAll { it.id in pudorysIds || (it as? Segment2DPudorys)?.let { s -> s.isConicalSilhouette && s.conicalSurfaceId == surfaceId } == true }
    state.selectedSegmentsNarys.removeAll { it.id in narysIds || (it as? Segment2DNarys)?.let { s -> s.isConicalSilhouette && s.conicalSurfaceId == surfaceId } == true }
    state.selectedSegmentsBokorys.removeAll { it.id in bokorysIds || (it as? Segment2DBokorys)?.let { s -> s.isConicalSilhouette && s.conicalSurfaceId == surfaceId } == true }
    state.selectedSegmentsAxo.removeAll { it.id in axoIds || (it.isConicalSilhouette && it.conicalSurfaceId == surfaceId) }
}

private fun addPoints(
    state: MongeState,
    point3DIds: Set<String>,
    pudorysIds: Collection<String>,
    narysIds: Collection<String>,
    bokorysIds: Collection<String>,
    axoIds: Collection<String>,
) {
    state.sharedPoints3D.filter { it.id in point3DIds }.forEach { state.selectedPoints3D.addById(it) }
    state.pointsPudorys.filter { it.id in pudorysIds }.forEach { state.selectedPointsPudorys.addById(it) }
    state.pointsNarys.filter { it.id in narysIds }.forEach { state.selectedPointsNarys.addById(it) }
    state.pointsBokorys.filter { it.id in bokorysIds }.forEach { state.selectedPointsBokorys.addById(it) }
    state.pointsAxo.filter { it.id in axoIds }.forEach { state.selectedPointsAxo.addById(it) }
}

private fun removePoints(
    state: MongeState,
    point3DIds: Set<String>,
    pudorysIds: Collection<String>,
    narysIds: Collection<String>,
    bokorysIds: Collection<String>,
    axoIds: Collection<String>,
) {
    state.selectedPoints3D.removeAll { it.id in point3DIds }
    state.selectedPointsPudorys.removeAll { it.id in pudorysIds }
    state.selectedPointsNarys.removeAll { it.id in narysIds }
    state.selectedPointsBokorys.removeAll { it.id in bokorysIds }
    state.selectedPointsAxo.removeAll { it.id in axoIds }
}

private fun ConicSectionPudorys.parentIdOrParent(): String? = parentId ?: parent?.id
private fun ConicSectionNarys.parentIdOrParent(): String? = parentId ?: parent?.id
private fun ConicSectionBokorys.parentIdOrParent(): String? = parentId ?: parent?.id
private fun ConicSectionAxo.parentIdOrParent(): String? = parentId ?: parent?.id
private fun SegmentsPudorys.parentIdOrParent(): String? = parent?.id ?: (this as? Segment2DPudorys)?.parentId
private fun SegmentsNarys.parentIdOrParent(): String? = parent?.id ?: (this as? Segment2DNarys)?.parentId
private fun SegmentsBokorys.parentIdOrParent(): String? = parent?.id ?: (this as? Segment2DBokorys)?.parentId

private fun MutableList<ConicSectionPudorys>.addById(value: ConicSectionPudorys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<ConicSectionNarys>.addById(value: ConicSectionNarys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<ConicSectionBokorys>.addById(value: ConicSectionBokorys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<ConicSectionAxo>.addById(value: ConicSectionAxo) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Segment2DPudorys>.addById(value: Segment2DPudorys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Segment2DNarys>.addById(value: Segment2DNarys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Segment2DBokorys>.addById(value: Segment2DBokorys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Segment2DAxo>.addById(value: Segment2DAxo) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Point3D>.addById(value: Point3D) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Point3DPudorys>.addById(value: Point3DPudorys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Point3DNarys>.addById(value: Point3DNarys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Point3DBokorys>.addById(value: Point3DBokorys) {
    if (none { it.id == value.id }) add(value)
}

private fun MutableList<Point3DAxo>.addById(value: Point3DAxo) {
    if (none { it.id == value.id }) add(value)
}
