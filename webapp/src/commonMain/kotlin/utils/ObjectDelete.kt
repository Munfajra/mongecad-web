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
    groupIds.forEach { Unit }
}

private fun deleteSolidOfRevolutionById(state: MongeState, id: String): Boolean {
    var deleted = false

    state.solidsOfRevolutionNarys.firstOrNull { it.id == id }?.let { sor ->
        deleteIntersectionGroupsTouchingOperands(state, setOf(sor.id))

        deleted = true
    }
    state.solidsOfRevolutionPudorys.firstOrNull { it.id == id }?.let { sor ->
        deleteIntersectionGroupsTouchingOperands(state, setOf(sor.id))

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



fun deleteSelected(state: MongeState) {
    val selectedRuledSurfaceId = state.selectedRuledSurfaceId
    if (selectedRuledSurfaceId != null) {

        clearSelection(state)
        commitSnapshot(state)
        state.triggerRedraw++
        return
    }

    val selectedIntersectionGroupId = state.selectedIntersectionGroupId
    if (selectedIntersectionGroupId != null) {

        clearSelection(state)
        return
    }

    val selectedSolid = state.selectedSegmentSolids3D.firstOrNull()
    if (selectedSolid != null) {

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

    }
    val selectedSoRId = state.selectedSolidOfRevolutionId
    if (selectedSoRId != null) {
        deleteSolidOfRevolutionById(state, selectedSoRId)
    }

    val selectedSphere3D = state.selectedSpheres3D.firstOrNull()
    selectedSphere3D?.let {sphere ->
        deleteIntersectionGroupsTouchingOperands(state, setOf(sphere.id))


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

// Vyříznuto: deleteIntersectionGroup, deleteSegmentSolid – mazání objektů, které web nemá.
