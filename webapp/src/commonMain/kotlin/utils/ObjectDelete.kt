package utils

import draw.mongescreen.labels.clearSelection
import monge.input.segments.deletePlanePolygon2D
import monge.input.segments.removePlanePolygonsContainingAidPoints
import model.Point3D
import model.classes.*
import serialization.commitSnapshot
import model.classes.isAxis
import model.classes.isAxisProjection
import state.MongeState
import ui.mongeui.toolbar.rightDescriptionBar.*

private fun deleteIntersectionGroupsTouchingOperands(state: MongeState, operandIds: Set<String>) {
    if (operandIds.isEmpty()) return
    val groupIds = state.intersectionGroups
        .filter { it.operandAId in operandIds || it.operandBId in operandIds }
        .map { it.id }
        .toList()
    groupIds.forEach { deleteIntersectionGroup(state, it) }
}

private fun deleteSolidOfRevolutionById(state: MongeState, id: String): Boolean {
    var deleted = false

    state.solidsOfRevolutionNarys.firstOrNull { it.id == id }?.let { sor ->
        deleteIntersectionGroupsTouchingOperands(state, setOf(sor.id))
        // deleteSoR – doplní etapa C (rotační plochy)
        deleted = true
    }
    state.solidsOfRevolutionPudorys.firstOrNull { it.id == id }?.let { sor ->
        deleteIntersectionGroupsTouchingOperands(state, setOf(sor.id))
        // deleteSoRPud – doplní etapa C (rotační plochy)
        deleted = true
    }

    if (deleted) {
        commitSnapshot(state)
        state.triggerRedraw++
    }
    return deleted
}

private fun deleteSolidOfRevolutionOwningAxis(state: MongeState, axisLine3DId: String): Boolean {
    val narysSor = state.solidsOfRevolutionNarys.firstOrNull { it.axisLine3DId == axisLine3DId }
    if (narysSor != null) return deleteSolidOfRevolutionById(state, narysSor.id)

    val pudorysSor = state.solidsOfRevolutionPudorys.firstOrNull { it.axisLine3DId == axisLine3DId }
    if (pudorysSor != null) return deleteSolidOfRevolutionById(state, pudorysSor.id)

    return false
}

private fun segmentPudorysParentId(segment: SegmentsPudorys): String? =
    (segment as? Segment2DPudorys)?.let { it.parent?.id ?: it.parentId } ?: segment.parent?.id

private fun segmentNarysParentId(segment: SegmentsNarys): String? =
    (segment as? Segment2DNarys)?.let { it.parent?.id ?: it.parentId } ?: segment.parent?.id

private fun segmentBokorysParentId(segment: SegmentsBokorys): String? =
    (segment as? Segment2DBokorys)?.let { it.parent?.id ?: it.parentId } ?: segment.parent?.id

private fun renamedPointId(state: MongeState): String? =
    when (val point = state.rename.pointBeingRenamed) {
        is Point3D -> point.id
        is Point3DPudorys -> point.id
        is Point3DNarys -> point.id
        is Point3DBokorys -> point.id
        is Point3DAxo -> point.id
        else -> null
    }

fun deleteIntersectionGroup(state: MongeState, groupId: String): Boolean {
    val group = state.intersectionGroups.firstOrNull { it.id == groupId } ?: run {
        if (state.selectedIntersectionGroupId == groupId) state.selectedIntersectionGroupId = null
        return false
    }

    val pointIds = group.parts
        .filter { it.kind == IntersectionPartKind.POINT3D }
        .mapTo(mutableSetOf()) { it.id }
    val lineIds = group.parts
        .filter { it.kind == IntersectionPartKind.LINE3D }
        .mapTo(mutableSetOf()) { it.id }
    val segmentIds = group.parts
        .filter { it.kind == IntersectionPartKind.SEGMENT3D }
        .mapTo(mutableSetOf()) { it.id }
    val conicIds = group.parts
        .filter { it.kind == IntersectionPartKind.CONIC3D }
        .mapTo(mutableSetOf()) { it.id }
    val curveIds = group.parts
        .filter { it.kind == IntersectionPartKind.CURVE3D }
        .mapTo(mutableSetOf()) { it.id }

    state.segments3D
        .filter { it.id in segmentIds }
        .forEach { segment ->
            pointIds += segment.start.id
            pointIds += segment.end.id
        }
    state.curves3D
        .filter { it.id in curveIds }
        .flatMapTo(pointIds) { it.pointIds }

    val linePIds = state.lines3DPudorys.filter { (it.parent?.id ?: it.parentId) in lineIds }.mapTo(mutableSetOf()) { it.id }
    val lineNIds = state.lines3DNarys.filter { (it.parent?.id ?: it.parentId) in lineIds }.mapTo(mutableSetOf()) { it.id }
    val lineBIds = state.lines3DBokorys.filter { (it.parent?.id ?: it.parentId) in lineIds }.mapTo(mutableSetOf()) { it.id }
    val lineAIds = state.lines3DAxo.filter { (it.parent?.id ?: it.parentId) in lineIds }.mapTo(mutableSetOf()) { it.id }

    val segPIds = state.segmentsPudorys.filter { (it.parent?.id ?: it.parentId) in segmentIds }.mapTo(mutableSetOf()) { it.id }
    val segNIds = state.segmentsNarys.filter { (it.parent?.id ?: it.parentId) in segmentIds }.mapTo(mutableSetOf()) { it.id }
    val segBIds = state.segmentsBokorys.filter { (it.parent?.id ?: it.parentId) in segmentIds }.mapTo(mutableSetOf()) { it.id }
    val segAIds = state.segmentsAxo.filter { (it.parent?.id ?: it.parentId) in segmentIds }.mapTo(mutableSetOf()) { it.id }

    val conicPIds = state.conicsPudorys.filter { (it.parent?.id ?: it.parentId) in conicIds }.mapTo(mutableSetOf()) { it.id }
    val conicNIds = state.conicsNarys.filter { (it.parent?.id ?: it.parentId) in conicIds }.mapTo(mutableSetOf()) { it.id }
    val conicBIds = state.conicsBokorys.filter { (it.parent?.id ?: it.parentId) in conicIds }.mapTo(mutableSetOf()) { it.id }
    val conicAIds = state.conicsAxo.filter { (it.parent?.id ?: it.parentId) in conicIds }.mapTo(mutableSetOf()) { it.id }

    val curvePIds = state.curvesPudorys.filter { it.parentId in curveIds }.mapTo(mutableSetOf()) { it.id }
    val curveNIds = state.curvesNarys.filter { it.parentId in curveIds }.mapTo(mutableSetOf()) { it.id }
    val curveBIds = state.curvesBokorys.filter { it.parentId in curveIds }.mapTo(mutableSetOf()) { it.id }
    val curveAIds = state.curvesAxo.filter { it.parentId in curveIds }.mapTo(mutableSetOf()) { it.id }

    val linePointPIds = state.pointsPudorys.filter { it.parentLine?.id in lineIds || it.pendingParentLineId in lineIds }.mapTo(mutableSetOf()) { it.id }
    val linePointNIds = state.pointsNarys.filter { it.parentLine?.id in lineIds || it.pendingParentLineId in lineIds }.mapTo(mutableSetOf()) { it.id }
    val linePointBIds = state.pointsBokorys.filter { it.parentLine?.id in lineIds || it.pendingParentLineId in lineIds }.mapTo(mutableSetOf()) { it.id }
    val linePointAIds = state.pointsAxo.filter { it.parentLine?.id in lineIds || it.pendingParentLineId in lineIds }.mapTo(mutableSetOf()) { it.id }

    val pointPIds = state.pointsPudorys
        .filter {
            it.parent?.id in pointIds ||
                    it.id in linePointPIds ||
                    it.parentSegment?.let { segment -> segment.id in segPIds || (segment.parent?.id ?: segment.parentId) in segmentIds } == true
        }
        .mapTo(mutableSetOf()) { it.id }
    val pointNIds = state.pointsNarys
        .filter {
            it.parent?.id in pointIds ||
                    it.id in linePointNIds ||
                    it.parentSegment?.let { segment -> segment.id in segNIds || (segment.parent?.id ?: segment.parentId) in segmentIds } == true
        }
        .mapTo(mutableSetOf()) { it.id }
    val pointBIds = state.pointsBokorys
        .filter {
            it.parent?.id in pointIds ||
                    it.id in linePointBIds ||
                    it.parentSegment?.let { segment -> segment.id in segBIds || (segment.parent?.id ?: segment.parentId) in segmentIds } == true
        }
        .mapTo(mutableSetOf()) { it.id }
    val pointAIds = state.pointsAxo
        .filter {
            it.parent?.id in pointIds ||
                    it.id in linePointAIds ||
                    it.parentSegment?.let { segment -> segment.id in segAIds || (segment.parent?.id ?: segment.parentId) in segmentIds } == true
        }
        .mapTo(mutableSetOf()) { it.id }

    linePIds.forEach { state.labelOffsetsPudorys.remove(it) }
    lineNIds.forEach { state.labelOffsetsNarys.remove(it) }
    lineBIds.forEach { state.labelOffsetsBokorys.remove(it) }
    lineAIds.forEach { state.labelOffsetsAxoLines.remove(it) }
    pointPIds.forEach { state.labelOffsetsPointsPudorys.remove(it) }
    pointNIds.forEach { state.labelOffsetsPointsNarys.remove(it) }
    pointBIds.forEach { state.labelOffsetsPointsBokorys.remove(it) }
    pointAIds.forEach { state.labelOffsetsPointsAxo.remove(it) }
    conicPIds.forEach { state.conicInputPointsPudorys.remove(it); state.hyperbolaInputsPudorys.remove(it) }
    conicNIds.forEach { state.conicInputPointsNarys.remove(it); state.hyperbolaInputsNarys.remove(it) }
    conicBIds.forEach { state.conicInputPointsBokorys.remove(it); state.hyperbolaInputsBokorys.remove(it) }
    conicAIds.forEach { state.conicInputPointsAxo.remove(it); state.hyperbolaInputsAxo.remove(it) }

    state.selectedIntersectionGroupId = null
    state.intersectionGroups.removeAll { intersection ->
        intersection.id == group.id || intersection.parts.any { part ->
            when (part.kind) {
                IntersectionPartKind.POINT3D -> part.id in pointIds
                IntersectionPartKind.LINE3D -> part.id in lineIds
                IntersectionPartKind.SEGMENT3D -> part.id in segmentIds
                IntersectionPartKind.CONIC3D -> part.id in conicIds
                IntersectionPartKind.CURVE3D -> part.id in curveIds
            }
        }
    }

    state.selectedPoints3D.removeAll { it.id in pointIds }
    state.selectedLines3D.removeAll { it.id in lineIds }
    state.selectedSegments3D.removeAll { it.id in segmentIds }
    state.selectedPointsPudorys.removeAll { it.id in pointPIds || it.parent?.id in pointIds }
    state.selectedPointsNarys.removeAll { it.id in pointNIds || it.parent?.id in pointIds }
    state.selectedPointsBokorys.removeAll { it.id in pointBIds || it.parent?.id in pointIds }
    state.selectedPointsAxo.removeAll { it.id in pointAIds || it.parent?.id in pointIds }
    state.selectedLinesPudorys.removeAll { it.id in linePIds || (it as? Line3DProjectionPudorys)?.let { line -> (line.parent?.id ?: line.parentId) in lineIds } == true }
    state.selectedLinesNarys.removeAll { it.id in lineNIds || (it as? Line3DProjectionNarys)?.let { line -> (line.parent?.id ?: line.parentId) in lineIds } == true }
    state.selectedLinesBokorys.removeAll { it.id in lineBIds || (it as? Line3DProjectionBokorys)?.let { line -> (line.parent?.id ?: line.parentId) in lineIds } == true }
    state.selectedLinesAxo.removeAll { it.id in lineAIds || (it.parent?.id ?: it.parentId) in lineIds }
    state.selectedSegmentsPudorys.removeAll { it.id in segPIds || segmentPudorysParentId(it) in segmentIds }
    state.selectedSegmentsNarys.removeAll { it.id in segNIds || segmentNarysParentId(it) in segmentIds }
    state.selectedSegmentsBokorys.removeAll { it.id in segBIds || segmentBokorysParentId(it) in segmentIds }
    state.selectedSegmentsAxo.removeAll { it.id in segAIds || (it.parent?.id ?: it.parentId) in segmentIds }
    state.selectedConicsPudorys.removeAll { it.id in conicPIds || (it.parent?.id ?: it.parentId) in conicIds }
    state.selectedConicsNarys.removeAll { it.id in conicNIds || (it.parent?.id ?: it.parentId) in conicIds }
    state.selectedConicsBokorys.removeAll { it.id in conicBIds || (it.parent?.id ?: it.parentId) in conicIds }
    state.selectedConicsAxo.removeAll { it.id in conicAIds || (it.parent?.id ?: it.parentId) in conicIds }
    if (state.selectedCurve3DId in curveIds) state.selectedCurve3DId = null
    if (state.selectedCurvePudorysId in curvePIds) state.selectedCurvePudorysId = null
    if (state.selectedCurveNarysId in curveNIds) state.selectedCurveNarysId = null
    if (state.selectedCurveBokorysId in curveBIds) state.selectedCurveBokorysId = null
    if (state.selectedCurveAxoId in curveAIds) state.selectedCurveAxoId = null

    state.pointsPudorys.removeAll { it.id in pointPIds || it.parent?.id in pointIds }
    state.pointsNarys.removeAll { it.id in pointNIds || it.parent?.id in pointIds }
    state.pointsBokorys.removeAll { it.id in pointBIds || it.parent?.id in pointIds }
    state.pointsAxo.removeAll { it.id in pointAIds || it.parent?.id in pointIds }
    state.lines3DPudorys.removeAll { it.id in linePIds || (it.parent?.id ?: it.parentId) in lineIds }
    state.lines3DNarys.removeAll { it.id in lineNIds || (it.parent?.id ?: it.parentId) in lineIds }
    state.lines3DBokorys.removeAll { it.id in lineBIds || (it.parent?.id ?: it.parentId) in lineIds }
    state.lines3DAxo.removeAll { it.id in lineAIds || (it.parent?.id ?: it.parentId) in lineIds }
    state.segmentsPudorys.removeAll { it.id in segPIds || (it.parent?.id ?: it.parentId) in segmentIds }
    state.segmentsNarys.removeAll { it.id in segNIds || (it.parent?.id ?: it.parentId) in segmentIds }
    state.segmentsBokorys.removeAll { it.id in segBIds || (it.parent?.id ?: it.parentId) in segmentIds }
    state.segmentsAxo.removeAll { it.id in segAIds || (it.parent?.id ?: it.parentId) in segmentIds }
    state.conicsPudorys.removeAll { it.id in conicPIds || (it.parent?.id ?: it.parentId) in conicIds }
    state.conicsNarys.removeAll { it.id in conicNIds || (it.parent?.id ?: it.parentId) in conicIds }
    state.conicsBokorys.removeAll { it.id in conicBIds || (it.parent?.id ?: it.parentId) in conicIds }
    state.conicsAxo.removeAll { it.id in conicAIds || (it.parent?.id ?: it.parentId) in conicIds }
    state.curvesPudorys.removeAll { it.id in curvePIds || it.parentId in curveIds }
    state.curvesNarys.removeAll { it.id in curveNIds || it.parentId in curveIds }
    state.curvesBokorys.removeAll { it.id in curveBIds || it.parentId in curveIds }
    state.curvesAxo.removeAll { it.id in curveAIds || it.parentId in curveIds }

    state.sharedPoints3D.removeAll { it.id in pointIds }
    state.lines3D.removeAll { it.id in lineIds }
    state.segmentSolids3D.removeAll { solid -> solid.segmentIds3D.any { it in segmentIds } }
    state.segments3D.removeAll { it.id in segmentIds }
    state.conics3D.removeAll { it.id in conicIds }
    state.curves3D.removeAll { it.id in curveIds }

    if (renamedPointId(state) in pointIds) state.rename.pointBeingRenamed = null

    commitSnapshot(state)
    state.triggerRedraw++
    return true
}

fun deleteSegmentSolid(state: MongeState, solidId: String): Boolean {
    val solid = state.segmentSolids3D.firstOrNull { it.id == solidId } ?: run {
        state.selectedSegmentSolids3D.removeAll { it.id == solidId }
        return false
    }
    deleteIntersectionGroupsTouchingOperands(state, setOf(solid.id))

    val segmentIds = solid.segmentIds3D.toHashSet()
    val pointIds = (solid.vertexPointIds + state.segments3D
        .filter { it.id in segmentIds }
        .flatMap { listOf(it.start.id, it.end.id) })
        .toHashSet()

    val polygonsToRemove = state.polygons3D.filter { polygon ->
        polygon.id in solid.polygonIds || polygon.segmentIds3D.any { it in segmentIds }
    }
    val polygonIds = polygonsToRemove.map { it.id }.toHashSet()

    val segPIds = (state.segmentsPudorys
        .filter { it.parent?.id in segmentIds || it.parentId in segmentIds }
        .map { it.id } + polygonsToRemove.flatMap { it.segmentIdsPudorys })
        .toHashSet()
    val segNIds = (state.segmentsNarys
        .filter { it.parent?.id in segmentIds || it.parentId in segmentIds }
        .map { it.id } + polygonsToRemove.flatMap { it.segmentIdsNarys })
        .toHashSet()
    val segBIds = state.segmentsBokorys
        .filter { it.parent?.id in segmentIds || it.parentId in segmentIds }
        .map { it.id }
        .toHashSet()
    val segAIds = (state.segmentsAxo
        .filter { it.parent?.id in segmentIds || it.parentId in segmentIds }
        .map { it.id } + polygonsToRemove.flatMap { it.segmentIdsAxo })
        .toHashSet()

    val ptPIds = (polygonsToRemove.flatMap { it.vertexPointIdsPudorys } + state.pointsPudorys
        .filter {
            it.parent?.id in pointIds ||
                    it.parentSegment?.let { segment -> segment.id in segPIds || (segment.parent?.id ?: segment.parentId) in segmentIds } == true
        }
        .map { it.id })
        .toHashSet()
    val ptNIds = (polygonsToRemove.flatMap { it.vertexPointIdsNarys } + state.pointsNarys
        .filter {
            it.parent?.id in pointIds ||
                    it.parentSegment?.let { segment -> segment.id in segNIds || (segment.parent?.id ?: segment.parentId) in segmentIds } == true
        }
        .map { it.id })
        .toHashSet()
    val ptBIds = state.pointsBokorys
        .filter {
            it.parent?.id in pointIds ||
                    it.parentSegment?.let { segment -> segment.id in segBIds || (segment.parent?.id ?: segment.parentId) in segmentIds } == true
        }
        .map { it.id }
        .toHashSet()
    val ptAIds = state.pointsAxo
        .filter {
            it.parent?.id in pointIds ||
                    it.parentSegment?.let { segment -> segment.id in segAIds || (segment.parent?.id ?: segment.parentId) in segmentIds } == true
        }
        .map { it.id }
        .toHashSet()

    ptPIds.forEach { state.labelOffsetsPointsPudorys.remove(it) }
    ptNIds.forEach { state.labelOffsetsPointsNarys.remove(it) }
    ptBIds.forEach { state.labelOffsetsPointsBokorys.remove(it) }
    ptAIds.forEach { state.labelOffsetsPointsAxo.remove(it) }

    state.selectedSegmentSolids3D.removeAll { it.id == solid.id || it.segmentIds3D.any { segmentId -> segmentId in segmentIds } }
    state.selectedSegments3D.removeAll { it.id in segmentIds }
    state.selectedSegmentsPudorys.removeAll { it.id in segPIds || segmentPudorysParentId(it) in segmentIds }
    state.selectedSegmentsNarys.removeAll { it.id in segNIds || segmentNarysParentId(it) in segmentIds }
    state.selectedSegmentsBokorys.removeAll { it.id in segBIds || segmentBokorysParentId(it) in segmentIds }
    state.selectedSegmentsAxo.removeAll { it.id in segAIds || it.parent?.id in segmentIds || it.parentId in segmentIds }

    state.selectedPoints3D.removeAll { it.id in pointIds }
    state.selectedPointsPudorys.removeAll { it.id in ptPIds || it.parent?.id in pointIds }
    state.selectedPointsNarys.removeAll { it.id in ptNIds || it.parent?.id in pointIds }
    state.selectedPointsBokorys.removeAll { it.id in ptBIds || it.parent?.id in pointIds }
    state.selectedPointsAxo.removeAll { it.id in ptAIds || it.parent?.id in pointIds }

    state.selectedPolygons.removeAll { it.id in polygonIds }
    if (state.selectedPolygon?.id in polygonIds) state.selectedPolygon = null

    state.pointsPudorys.removeAll { it.id in ptPIds || it.parent?.id in pointIds }
    state.pointsNarys.removeAll { it.id in ptNIds || it.parent?.id in pointIds }
    state.pointsBokorys.removeAll { it.id in ptBIds || it.parent?.id in pointIds }
    state.pointsAxo.removeAll { it.id in ptAIds || it.parent?.id in pointIds }

    state.segmentsPudorys.removeAll { it.id in segPIds || it.parent?.id in segmentIds || it.parentId in segmentIds }
    state.segmentsNarys.removeAll { it.id in segNIds || it.parent?.id in segmentIds || it.parentId in segmentIds }
    state.segmentsBokorys.removeAll { it.id in segBIds || it.parent?.id in segmentIds || it.parentId in segmentIds }
    state.segmentsAxo.removeAll { it.id in segAIds || it.parent?.id in segmentIds || it.parentId in segmentIds }

    state.polygons3D.removeAll { it.id in polygonIds }
    state.segmentSolids3D.removeAll { it.id == solid.id || it.segmentIds3D.any { segmentId -> segmentId in segmentIds } }
    state.segments3D.removeAll { it.id in segmentIds }
    state.sharedPoints3D.removeAll { it.id in pointIds }

    if (renamedPointId(state) in pointIds) state.rename.pointBeingRenamed = null

    commitSnapshot(state)
    state.triggerRedraw++
    return true
}

fun deleteSelected(state: MongeState) {
    val selectedRuledSurfaceId = state.selectedRuledSurfaceId
    if (selectedRuledSurfaceId != null) {
        // deleteRuledSurface – doplní etapa D (přímkové plochy)
        clearSelection(state)
        commitSnapshot(state)
        state.triggerRedraw++
        return
    }

    val selectedIntersectionGroupId = state.selectedIntersectionGroupId
    if (selectedIntersectionGroupId != null) {
        deleteIntersectionGroup(state, selectedIntersectionGroupId)
        clearSelection(state)
        return
    }

    val selectedSolid = state.selectedSegmentSolids3D.firstOrNull()
    if (selectedSolid != null) {
        deleteSegmentSolid(state, selectedSolid.id)
        clearSelection(state)
        return
    }

    val selectedPlanePolygon = state.selectedPlanePolygons2D.firstOrNull()
    if (selectedPlanePolygon != null && deletePlanePolygon2D(state, selectedPlanePolygon.id)) {
        clearSelection(state)
        commitSnapshot(state)
        state.triggerRedraw++
        return
    }

    if((state.selectedSolidOfRevolutionId?.isEmpty()?: true)
        &&state.selectedCone.isEmpty()&&state.selectedPolygons.isEmpty()&&state.selectedCylinder.isEmpty())
    {

        val selAOseg = state.selectedAOSegIds.firstOrNull()
        selAOseg?.let {
            // deleteAOSegment – AO overlay je jen v axonometrii, web ho nemá
        }
        val selAOl = state.selectedAOLineIds.firstOrNull()
        selAOl?.let { l ->
            state.axoOverlayLines.removeAll { it.id == l }
            state.selectedAOLineIds.remove(l)
            commitSnapshot(state)
        }
        val selAOp = state.selectedAOPointIds.firstOrNull()
        selAOp?.let { p ->
            state.axoOverlayPoints.removeAll { it.id == p }
            state.selectedAOPointIds.remove(p)
            commitSnapshot(state)
        }

        //OBLOUKY
             val selectedArc =
            state.selectedArcsNarys.firstOrNull()
                ?:state.selectedArcsPudorys.firstOrNull()
                ?:state.selectedArcsBokorys.firstOrNull()
                ?:state.selectedArcsAxoOverlay.firstOrNull()
         selectedArc?.let { a->
          deleteArc(state,a)
           }
               val selectedCircle =
            state.selectedCirclesPudorys.firstOrNull()
                ?: state.selectedCirclesNarys.firstOrNull()

         val currentCircle: ConicSection2D? = when (selectedCircle) {
            is ConicSectionPudorys -> state.circlesPudorys.find { it.id == selectedCircle.id }
            is ConicSectionNarys -> state.circlesNarys.find { it.id == selectedCircle.id }
            else -> null
          }
           currentCircle?.let { circle ->
            deleteCircle(state, circle)
          }
           //ROVINY
           val selectedPlane = state.selectedPlanes.firstOrNull()
           selectedPlane?.let { plane ->
            deletePlane(state, plane)
            }
           // KUŽELOSEČKY
          val selectedConic =
            state.selectedConicsPudorys.firstOrNull()
                ?: state.selectedConicsNarys.firstOrNull()
                ?: state.selectedConicsBokorys.firstOrNull()
                ?: state.selectedConicsAxo.firstOrNull()

          val currentConic: ConicSection2D? = when (selectedConic) {
            is ConicSectionPudorys -> state.conicsPudorys.find { it.id == selectedConic.id }
            is ConicSectionNarys -> state.conicsNarys.find { it.id == selectedConic.id }
            is ConicSectionBokorys -> state.conicsBokorys.find { it.id == selectedConic.id }
            is ConicSectionAxo -> state.conicsAxo.find { it.id == selectedConic.id }
            else -> null
         }
         val conic3D = (currentConic?.parent?.id ?: currentConic?.parentId).let { parentId ->
               state.conics3D.find { it.id == parentId }
               }
              conic3D?.let { conic -> deleteConic3D(state, conic) }
         if (conic3D == null) {
            currentConic?.let { conic -> deleteConic2D(state, conic) }
                }
         // Úsečka
          val selectedSegmentRaw = state.selectedSegmentsPudorys.firstOrNull()
              ?: state.selectedSegmentsNarys.firstOrNull()?: state.selectedSegmentsAxo.firstOrNull() ?: state.selectedSegmentsBokorys.firstOrNull()
         val currentSegment = when (selectedSegmentRaw) {
            is Segment2DPudorys -> state.segmentsPudorys.find { it.id == selectedSegmentRaw.id }
            is Segment2DNarys -> state.segmentsNarys.find { it.id == selectedSegmentRaw.id }
             is Segment2DAxo -> state.segmentsAxo.find { it.id == selectedSegmentRaw.id }
             is Segment2DBokorys -> state.segmentsBokorys.find { it.id == selectedSegmentRaw.id }
            else -> null
          }
          val segmentToEdit = if (currentSegment?.parent != null) null else currentSegment

           val parentSegment = currentSegment?.parent
          parentSegment?.let { seg3D ->
               deleteSegment3D(state = state, seg3D)
          }
        segmentToEdit?.let { selectedSegmentRaw ->
            deleteSegment2D(state = state, selectedSegmentRaw = selectedSegmentRaw)
        }
        // Přímka
        val selectedLine3D = state.selectedLines3D.firstOrNull()
        selectedLine3D?.let { line ->
            if (!deleteSolidOfRevolutionOwningAxis(state, line.id) && !isAxis(line)) {
                deleteLine3D(state, line)
            }
        }

        val selectedRaw = state.selectedLinesPudorys.firstOrNull()
            ?: state.selectedLinesNarys.firstOrNull()?:state.selectedLinesBokorys.firstOrNull() ?: state.selectedLinesAxo.firstOrNull()

        val current: LinearObject2D? = when (selectedRaw) {
            is Line3DProjectionPudorys -> state.lines3DPudorys .find { it.id == selectedRaw.id }
            is Line3DProjectionNarys -> state.lines3DNarys   .find { it.id == selectedRaw.id }
            is Line3DProjectionBokorys-> state.lines3DBokorys.find { it.id == selectedRaw.id }
            is Line3DProjectionAxo -> state.lines3DAxo.find { it.id == selectedRaw.id }
            is PlaneTracePudorys -> state.lineTracesPudorys.find { it.id == selectedRaw.id }
            is PlaneTraceNarys -> state.lineTracesNarys .find { it.id == selectedRaw.id }
            is PlaneTraceBokorys -> state.lineTracesBokorys.find { it.id == selectedRaw.id }
            is HelpLinePudorys -> state.helpLinePudorys .find { it.id == selectedRaw.id }
            is HelpLineNarys -> state.helpLineNarys .find { it.id == selectedRaw.id }
            else -> null
        }
        when (current) {
            is Line2DProjection -> {
                if (current.parent != null) {
                    val parentLine = current.parent!!
                    if (isAxis(parentLine)) return
                    deleteLine3D(state, parentLine)

                } else {
                    val selectedLine=current
                    if ( isAxisProjection(selectedLine)) return
                    deleteLine2D(state, selectedLine = selectedLine)

                }
            }
            is Trace2DProjection -> {
                val fresh: Trace2DProjection = when (current) {
                    is PlaneTracePudorys -> state.lineTracesPudorys.firstOrNull { it.id == current.id }
                    is PlaneTraceNarys -> state.lineTracesNarys  .firstOrNull { it.id == current.id }
                    is PlaneTraceBokorys -> state.lineTracesBokorys .firstOrNull { it.id == current.id }
                } ?: return
                deleteTrace2D(state, fresh)
            }
            is HelpLinePudorys -> {
                deleteHelpLinePudorys(state, current)
            }
            is HelpLineNarys -> {
                deleteHelpLineNarys(state, current)
            }
        }
        //BODY
        // 🟢 1. Bod (půdorys/nárys → Point3D)
        val selectedPointRaw = state.selectedPointsPudorys.firstOrNull()
            ?: state.selectedPointsNarys.firstOrNull()?: state.selectedPointsBokorys.firstOrNull()?:
            state.selectedPointsAxo.firstOrNull()

        val currentPoint = when (selectedPointRaw) {
            is Point3DPudorys -> state.pointsPudorys.find { it.id == selectedPointRaw.id }
            is Point3DNarys -> state.pointsNarys.find { it.id == selectedPointRaw.id }
            is Point3DBokorys -> state.pointsBokorys.find { it.id == selectedPointRaw.id }
            is Point3DAxo -> state.pointsAxo.find { it.id == selectedPointRaw.id }
            else -> null
        }

        val parentLine = projectedLineOfPoint(state, currentPoint)
        if (parentLine != null) {
            deleteLine3D(state, parentLine)
        } else {
            val pointToEdit = if (currentPoint?.parent != null) null else currentPoint
            val point3D = currentPoint?.parent

            point3D?.let { pt ->
                pointDelete3D(state, pt)
            }
            pointToEdit?.let { pt ->
                pointDelete2D(state, pt)
            }
        }
        //POMOCNE BODY
        val selectedAidPointId = state.selectedAidPointIds.firstOrNull()
        val selectedAidPoint   = selectedAidPointId?.let { id ->
            state.aidPointsLogical.find { it.id == id }
        }
        selectedAidPoint?.let { ap ->
            removePlanePolygonsContainingAidPoints(state, setOf(ap.id))
            state.aidPointsLogical.removeAll { it.id == ap.id }
            state.selectedAidPointIds.remove(ap.id)
            commitSnapshot(state)

        }

    }
    val selectedCone = state.selectedCone.firstOrNull()
    selectedCone?.let { cone ->
        deleteIntersectionGroupsTouchingOperands(state, setOf(cone.id))
        deleteCone(state, cone)
    }
    val selectedSoRId = state.selectedSolidOfRevolutionId
    if (selectedSoRId != null) {
        deleteSolidOfRevolutionById(state, selectedSoRId)
    }

    val selectedSphere3D = state.selectedSpheres3D.firstOrNull()
    selectedSphere3D?.let {sphere ->
        deleteIntersectionGroupsTouchingOperands(state, setOf(sphere.id))
        deleteSphere3D(state, sphere)

    }
    val selectedHelpSegment = state.selectedSegmentsPudorys.firstOrNull()
        ?: state.selectedSegmentsNarys.firstOrNull()
    val currentHelpSegment = when (selectedHelpSegment) {
        is HelpSegmentNarys -> state.helpSegmentsNarys.find {it.id == selectedHelpSegment.id}
        is HelpSegmentPudorys -> state.helpSegmentsPudorys.find {it.id == selectedHelpSegment.id}
        else -> null
    }
    currentHelpSegment?.let { helpseg ->
        deleteHelpSegment2D(state, helpseg)
    }
    val selectedCylinder = state.selectedCylinder.firstOrNull()
    selectedCylinder?.let { cyl ->
        deleteIntersectionGroupsTouchingOperands(state, setOf(cyl.id))
        deleteCylinder(state, cyl)
    }
    val selectedPolygon = state.selectedPolygons.firstOrNull()
    selectedPolygon?.let{ pol ->
        deletePolygon(state, pol.id)

    }
    // KŘIVKY (3D + projekce)
    run {
        // 1) pokud je vybraná 3D křivka → smaž 3D (smaže i projekce)
        val sel3D = state.selectedCurve3DId
        if (sel3D != null) {
            deleteCurve3D(state, sel3D)
            return@run
        }

        // 2) vybraná půdorysná křivka
        val selP = state.selectedCurvePudorysId
        if (selP != null) {
            val curveP = state.curvesPudorys.firstOrNull { it.id == selP }
            if (curveP != null) {
                // pokud má parenta, smaž parenta
                val parentId = curveP.parentId
                if (parentId != null && state.curves3D.any { it.id == parentId }) deleteCurve3D(state, parentId)
                else deleteCurvePudorys(state, curveP.id)
                return@run
            } else {
                // vybraný id už neexistuje → vyčisti selection
                state.selectedCurvePudorysId = null
            }
        }

        // 3) vybraná nárysná křivka
        val selN = state.selectedCurveNarysId
        if (selN != null) {
            val curveN = state.curvesNarys.firstOrNull { it.id == selN }
            if (curveN != null) {
                val parentId = curveN.parentId
                if (parentId != null && state.curves3D.any { it.id == parentId }) deleteCurve3D(state, parentId)
                else deleteCurveNarys(state, curveN.id)
                return@run
            } else {
                state.selectedCurveNarysId = null
            }
        }

        // 4) vybraná bokorysná křivka
        val selB = state.selectedCurveBokorysId
        if (selB != null) {
            val curveB = state.curvesBokorys.firstOrNull { it.id == selB }
            if (curveB != null) {
                val parentId = curveB.parentId
                if (parentId != null && state.curves3D.any { it.id == parentId }) deleteCurve3D(state, parentId)
                else deleteCurveBokorys(state, curveB.id)
                return@run
            } else {
                state.selectedCurveBokorysId = null
            }
        }

        // 5) vybraná axo křivka
        val selA = state.selectedCurveAxoId
        if (selA != null) {
            val curveA = state.curvesAxo.firstOrNull { it.id == selA }
            if (curveA != null) {
                val parentId = curveA.parentId
                if (parentId != null && state.curves3D.any { it.id == parentId }) deleteCurve3D(state, parentId)
                else deleteCurveAxo(state, curveA.id)
                return@run
            } else {
                state.selectedCurveAxoId = null
            }
        }
    }

    clearSelection(state)
}
