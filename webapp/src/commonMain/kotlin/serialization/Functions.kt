package serialization
import monge.input.ruledsurface.captureRuledSurfaceGeometry

import model.classes.isAxisX
import model.classes.isAxisY
import model.classes.isAxisZ
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import draw.mongescreen.conicarcs.planeEquationFromConic3D
import draw.mongescreen.objects.toModelPlaneEquation


import model.ArcMode
import model.Offset3D
import model.ProjectionMode
import model.classes.*
import monge.input.axo.createAxoRenderBasis
import monge.input.conixections.conjugateDiameterInputFromRadii
import monge.input.planeobjects.conicsections.extractVertexAndAxisFromNarys
import monge.input.planeobjects.conicsections.extractVertexAndAxisFromPudorys
import geometry.conics.ConicType
import geometry.conics.classifyConicFromMatrix
import geometry.conics.computeEllipseAxes3D
import serialization.classes.relinkConics2DTo3D
import state.MongeState
import kotlin.math.abs

/** Commit snapshot se volá vždy PO změně stavu */
fun commitSnapshot(state: MongeState) {
    if (state.isRestoringFromHistory) return
    pushSnapshot(state.history, state.exportSnapshot())
    state.history.debug("save")
    state.historyVersion++
    state.sceneVersion++
    state.isDirty = true
}

fun createSnapshot(state: MongeState): MongeSnapshot {
    repairSphereConicParentIds(state)
    state.ruledSurfaces.forEach { captureRuledSurfaceGeometry(state, it) }
    state.conicsPudorys.forEach { c ->
        c.parent?.id?.let { c.parentId = it }
    }
    state.conicsNarys.forEach { c ->
        c.parent?.id?.let { c.parentId = it }
    }
    state.conicsBokorys.forEach { c ->
        c.parent?.id?.let { c.parentId = it }
    }
    state.conicsAxo.forEach { c ->
        c.parent?.id?.let { c.parentId = it }
    }
    return MongeSnapshot(
        //OBJEKTY
        pointsPudorys = state.pointsPudorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        pointsNarys = state.pointsNarys.map { it.copy(showInAxoInitial = it.showInAxo) },
        pointsBokorys = state.pointsBokorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        pointsAxo = state.pointsAxo.map { it.copy(showInAxoInitial = it.showInAxo) },
        linesPudorys = state.lines3DPudorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        linesNarys = state.lines3DNarys.map { it.copy(showInAxoInitial = it.showInAxo) },
        linesBokorys = state.lines3DBokorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        linesAxo = state.lines3DAxo.map { it.copy(showInAxoInitial = it.showInAxo) },
        segmentsPudorys = state.segmentsPudorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        segmentsNarys = state.segmentsNarys.map { it.copy(showInAxoInitial = it.showInAxo) },
        segmentsBokorys = state.segmentsBokorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        segmentsAxo = state.segmentsAxo.map { it.copy(showInAxoInitial = it.showInAxo) },
        sharedPoints3D = state.sharedPoints3D.map { it.copy() },
        lines3D = state.lines3D.map { it.copy() },
        segments3D = state.segments3D.map { it.copy() },
        segmentSolids3D = state.segmentSolids3D.map { it.copy() },
        planes3D = state.planes3D.map { it.copy() },
        planeTracePudorys = state.lineTracesPudorys.filterNot { it.isVirtual }.map { it.copy() },
        planeTraceNarys = state.lineTracesNarys.filterNot { it.isVirtual }.map { it.copy() },
        planeTraceBokorys = state.lineTracesBokorys.filterNot { it.isVirtual }.map { it.copy() },
        helpLineNarys = state.helpLineNarys.filterNot(predicate = ::isAxisZ).map { it.copy() },
        helpLinePudorys = state.helpLinePudorys.filterNot(::isAxisX).filterNot(::isAxisY).map { it.copy() },
        helpSegmentNarys = state.helpSegmentsNarys.map { it.copy() },
        helpSegmentPudorys = state.helpSegmentsPudorys.map { it.copy() },
        arcsnarys = state.arcsNarys.map { it.copy() },
        arcspudorys = state.arcsPudorys.map { it.copy() },
        arcsbokorys = state.arcsBokorys.map { it.copy() },
        arcsAxoOverlay = state.arcsAxoOverlay.map { it.copy() },
        circlesNarys = state.circlesNarys.map {it.copy()},
        circlesPudorys = state.circlesPudorys.map {it.copy()},
        circlesBokorys = state.circlesBokorys.map {it.copy()},
        aidPointsLogical = state.aidPointsLogical.filterNot { it.id=="origin" }.map {it.copy()},
        axoOverlayPoint = state.axoOverlayPoints.map { it.copy() },
        axoOverlayLine = state.axoOverlayLines.map { it.copy() },
        axoOverlaySegment = state.axoOverlaySegments.map { it.copy() },
        curves3D = state.curves3D.map { it.copy() },
        curvesNarys = state.curvesNarys.map { it.copy(showInAxoInitial = it.showInAxo) },
        curvesPudorys = state.curvesPudorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        curvesBokorys = state.curvesBokorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        curvesAxo = state.curvesAxo.map { it.copy(showInAxoInitial = it.showInAxo) },
        intersectionGroups = state.intersectionGroups.map { it.copy(parts = it.parts.toList()) },
        conicsPudorys = state.conicsPudorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        conicsNarys = state.conicsNarys.map { it.copy(showInAxoInitial = it.showInAxo) },
        conicsBokorys = state.conicsBokorys.map { it.copy(showInAxoInitial = it.showInAxo) },
        conicsAxo = state.conicsAxo.map { it.copy(showInAxoInitial = it.showInAxo) },
        conicInputPointsPudorys = state.conicInputPointsPudorys.toMap(),
        conicInputPointsNarys = state.conicInputPointsNarys.toMap(),
        conicInputPointsBokorys = state.conicInputPointsBokorys.toMap(),
        conicInputPointsAxo = state.conicInputPointsAxo.toMap(),
        conic3D = state.conics3D.map { src -> src.copy().also { it.directrixOfSurfaceIds = src.directrixOfSurfaceIds } },
        conicalSurface = state.conicalSurfaces.map {it.copy()},
        spheres = state.spheres3D.map{it.copy()},
        cylinderSurface = state.cylindricalSurfaces.map{it.copy()},
        ruledSurfaces = state.ruledSurfaces.map { it.copy() },
        solidsOfRevolutionsNarys = state.solidsOfRevolutionNarys.map { it.copy() },
        solidsOfRevolutionsPudorys = state.solidsOfRevolutionPudorys.map { it.copy() },
        //OBLOUKY
        //ELIPSA
        ellipseArcParams3D   = state.ellipseArcParams3D.toMap(),
        ellipseArcEnds3D     = state.ellipseArcEnds3D.toMap(),
        ellipseArcEnds= state.ellipseArcEnds.toMap(),
        ellipseArcMode= state.ellipseArcMode.toMap(),
        conicSegments = state.conicSegments.toMap(),
        // PARABOLA
        parabolaArcParams3D   = state.parabolaArcParams3D.toMap(),
        parabolaArcEnds3D     = state.parabolaArcEnds3D.toMap(),
        parabolaArcEnds= state.parabolaArcEnds.toMap(),
        // HYPERBOLA
        hyperbolaArcParams3D       = state.hyperbolaArcParams3D.toMap(),
        hyperbolaArcEnds3D         = state.hyperbolaArcEnds3D.toMap(),
        hyperbolaArcBranch1 = state.hyperbolaArcBranch1.toMap(),
        hyperbolaArcBranch2 = state.hyperbolaArcBranch2.toMap(),
        hyperbolaInputsPudorys = state.hyperbolaInputsPudorys
            .entries.associate { (k, v) -> k to v },
        hyperbolaInputsNarys   = state.hyperbolaInputsNarys
            .entries.associate { (k, v) -> k to v },
        hyperbolaInputsBokorys = state.hyperbolaInputsBokorys
            .entries.associate { (k, v) -> k to v },
        hyperbolaInputsAxo = state.hyperbolaInputsAxo
            .entries.associate { (k, v) -> k to v },
        polygons = state.polygons3D.map{it.copy()},
        planePolygons2D = state.planePolygons2D.map { it.copy() },
        //KRUŽNICE
        circleArcsPudorys = state.circleArcEnds.map { (circleId, ends) ->
            CircleArcPudorys(
                circleId = circleId,
                a = ends.first,
                b = ends.second,
                mode = state.circleArcMode[circleId] ?: ArcMode.SHORTEST
            )
        },
        circleArcsNarys = state.circleArcEnds.map { (circleId, ends) ->
            CircleArcNarys(
                circleId = circleId,
                a = ends.first,
                b = ends.second,
                mode = state.circleArcMode[circleId] ?: ArcMode.SHORTEST
            )
        },
        //LABELS pozice
        labelOffsetsNarys = state.labelOffsetsNarys.toMap(),
        labelOffsetsPudorys = state.labelOffsetsPudorys.toMap(),
        labelOffsetsBokorys = state.labelOffsetsBokorys.toMap(),
        labelOffsetsAidPoints = state.labelOffsetsAidPoints.toMap(),
        labelOffsetsHelpNarys = state.labelOffsetsHelpNarys.toMap(),
        labelOffsetsHelpPudorys = state.labelOffsetsHelpPudorys.toMap(),
        labelOffsetsTraceNarys = state.labelOffsetsTraceNarys.toMap(),
        labelOffsetsTracePudorys = state.labelOffsetsTracePudorys.toMap(),
        labelOffsetsTraceBokorys = state.labelOffsetsTraceBokorys.toMap(),
        labelOffsetsPointsNarys = state.labelOffsetsPointsNarys.toMap(),
        labelOffsetsPointsPudorys = state.labelOffsetsPointsPudorys.toMap(),
        labelOffsetsPointsBokorys = state.labelOffsetsPointsBokorys.toMap(),
        labelOffsetsPointsAxo = state.labelOffsetsPointsAxo.toMap(),
        labelOffsetsAOPoints = state.labelOffsetsAOPoints.toMap(),
        labelOffsetsAOLine = state.labelOffsetsAOLines.toMap(),
        labelOffsetsAxoLines = state.labelOffsetsAxoLines.toMap(),
        labelOffsetsSegmentsPudorys = state.labelOffsetsSegmentsPudorys.toMap(),
        labelOffsetsSegmentsNarys = state.labelOffsetsSegmentsNarys.toMap(),
        labelOffsetsSegmentsBokorys = state.labelOffsetsSegmentsBokorys.toMap(),
        labelOffsetsSegmentsAxo = state.labelOffsetsSegmentsAxo.toMap(),
        labelOffsetsHelpSegmentsPudorys = state.labelOffsetsHelpSegmentsPudorys.toMap(),
        labelOffsetsHelpSegmentsNarys = state.labelOffsetsHelpSegmentsNarys.toMap(),
        labelOffsetsAOSegments = state.labelOffsetsAOSegments.toMap(),
        axoModel = state.activeAxoModel
    )
}

fun repairSphereConicParentIds(state: MongeState) {
    fun near(a: Float, b: Float, eps: Float = 1e-3f): Boolean =
        abs(a - b) <= eps

    fun hasP(sphereId: String) = state.conicsPudorys.any { it.parentId == sphereId }
    fun hasN(sphereId: String) = state.conicsNarys.any { it.parentId == sphereId }
    fun hasB(sphereId: String) = state.conicsBokorys.any { it.parentId == sphereId }

    state.spheres3D.forEach { sphere ->
        val center = state.sharedPoints3D.firstOrNull { it.id == sphere.centerPoint3DId } ?: return@forEach

        if (!hasP(sphere.id)) {
            state.conicsPudorys.firstOrNull { conic ->
                conic.parent == null &&
                        conic.parentId == null &&
                        conic.tryAsCircleGeom()?.let { g ->
                            near(g.center.x, center.x) &&
                                    near(g.center.y, center.y) &&
                                    near(g.r, sphere.radius)
                        } == true
            }?.parentId = sphere.id
        }

        if (!hasN(sphere.id)) {
            state.conicsNarys.firstOrNull { conic ->
                conic.parent == null &&
                        conic.parentId == null &&
                        conic.tryAsCircleGeomXZ()?.let { g ->
                            near(g.x0, center.x) &&
                                    near(g.z0, center.z) &&
                                    near(g.r, sphere.radius)
                        } == true
            }?.parentId = sphere.id
        }

        if (!hasB(sphere.id)) {
            state.conicsBokorys.firstOrNull { conic ->
                val y0 = -conic.d / 2f
                val z0 = -conic.e / 2f
                conic.parent == null &&
                        conic.parentId == null &&
                        near(conic.a, 1f) &&
                        near(conic.b, 0f) &&
                        near(conic.c, 1f) &&
                        near(y0, center.y) &&
                        near(z0, center.z) &&
                        near(y0 * y0 + z0 * z0 - conic.f, sphere.radius * sphere.radius, 1e-2f)
            }?.parentId = sphere.id
        }
    }
}
fun repairDirectrixOfSurfaceIds(state: MongeState) {
    val conicById = state.conics3D.associateBy { it.id }
    for (cone in state.conicalSurfaces) {
        conicById[cone.directrixId]?.let { it.directrixOfSurfaceIds = it.directrixOfSurfaceIds + cone.id }
    }
    for (cyl in state.cylindricalSurfaces) {
        conicById[cyl.directrixId]?.let { it.directrixOfSurfaceIds = it.directrixOfSurfaceIds + cyl.id }
        cyl.upperConicId?.let { uid -> conicById[uid]?.let { it.directrixOfSurfaceIds = it.directrixOfSurfaceIds + cyl.id } }
    }
}

fun restoreSnapshot(state: MongeState, snapshot: MongeSnapshot) {
    state.activeAxoModel = snapshot.axoModel
    state.basis = createAxoRenderBasis(
        canvasWidth = 0f,
        canvasHeight = 0f,
        model = snapshot.axoModel
    )

    state.circleArcsPudorys.setAll(snapshot.circleArcsPudorys)
    state.axoOverlayPoints.setAll(snapshot.axoOverlayPoint)
    state.axoOverlayLines.setAll(snapshot.axoOverlayLine)
    state.axoOverlaySegments.setAll(snapshot.axoOverlaySegment)
    state.circleArcsNarys.setAll(snapshot.circleArcsNarys)
    state.solidsOfRevolutionNarys.setAll(snapshot.solidsOfRevolutionsNarys)
    state.solidsOfRevolutionPudorys.setAll(snapshot.solidsOfRevolutionsPudorys)
    state.pointsPudorys.setAll(snapshot.pointsPudorys)
    state.pointsNarys.setAll(snapshot.pointsNarys)
    state.pointsBokorys.setAll(snapshot.pointsBokorys)
    state.pointsAxo.setAll(snapshot.pointsAxo)
    state.lines3DPudorys.setAll(snapshot.linesPudorys)
    state.lines3DNarys.setAll(snapshot.linesNarys)
    state.lines3DBokorys.setAll(snapshot.linesBokorys)
    state.lines3DAxo.setAll(snapshot.linesAxo)
    state.segmentsPudorys.setAll(snapshot.segmentsPudorys)
    state.segmentsNarys.setAll(snapshot.segmentsNarys)
    state.segmentsBokorys.setAll(snapshot.segmentsBokorys)
    state.segmentsAxo.setAll(snapshot.segmentsAxo)
    state.sharedPoints3D.setAll(snapshot.sharedPoints3D)
    state.lines3D.setAll(snapshot.lines3D)
    state.segments3D.setAll(snapshot.segments3D)
    state.segmentSolids3D.setAll(snapshot.segmentSolids3D)
    state.planes3D.setAll(snapshot.planes3D)
    state.lineTracesPudorys.setAll(snapshot.planeTracePudorys)
    state.lineTracesNarys.setAll(snapshot.planeTraceNarys)
    state.lineTracesBokorys.setAll(snapshot.planeTraceBokorys)
    state.helpLineNarys.setAll(snapshot.helpLineNarys)
    state.helpLinePudorys.setAll(snapshot.helpLinePudorys)
    state.helpSegmentsNarys.setAll(snapshot.helpSegmentNarys)
    state.helpSegmentsPudorys.setAll(snapshot.helpSegmentPudorys)
    state.arcsNarys.setAll(snapshot.arcsnarys)
    state.arcsPudorys.setAll(snapshot.arcspudorys)
    state.arcsBokorys.setAll(snapshot.arcsbokorys)
    state.arcsAxoOverlay.setAll(snapshot.arcsAxoOverlay)
    state.circlesNarys.setAll(snapshot.circlesNarys)
    state.circlesPudorys.setAll(snapshot.circlesPudorys)
    state.circlesBokorys.setAll(snapshot.circlesBokorys)
    state.aidPointsLogical.setAll(snapshot.aidPointsLogical)
    state.conicsPudorys.setAll(snapshot.conicsPudorys)
    state.conicsNarys.setAll(snapshot.conicsNarys)
    state.conicsBokorys.setAll(snapshot.conicsBokorys)
    state.conicsAxo.setAll(snapshot.conicsAxo)
    state.conicInputPointsNarys.clear()
    state.conicInputPointsPudorys.clear()
    state.conicInputPointsBokorys.clear()
    state.conicInputPointsAxo.clear()
    state.conicInputPointsNarys.putAll(snapshot.conicInputPointsNarys)
    state.conicInputPointsPudorys.putAll(snapshot.conicInputPointsPudorys)
    state.conicInputPointsBokorys.putAll(snapshot.conicInputPointsBokorys)
    state.conicInputPointsAxo.putAll(snapshot.conicInputPointsAxo)
    state.conics3D.setAll(snapshot.conic3D)
    state.conicalSurfaces.setAll(snapshot.conicalSurface)
    state.cylindricalSurfaces.setAll(snapshot.cylinderSurface)
    state.ruledSurfaces.setAll(snapshot.ruledSurfaces)
    state.spheres3D.setAll(snapshot.spheres)
    repairSphereConicParentIds(state)
    repairDirectrixOfSurfaceIds(state)
    state.polygons3D.setAll(snapshot.polygons)
    state.planePolygons2D.setAll(snapshot.planePolygons2D)
    state.selectedPlanePolygons2D.clear()
    state.circleArcEnds.clear()
    state.circleArcMode.clear()

    snapshot.circleArcsPudorys.forEach { arc ->
        state.circleArcEnds[arc.circleId] = arc.a to arc.b
        state.circleArcMode[arc.circleId] = arc.mode
    }
    snapshot.circleArcsNarys.forEach { arc ->
        state.circleArcEnds[arc.circleId] = arc.a to arc.b
        state.circleArcMode[arc.circleId] = arc.mode
    }

    state.triggerRedraw++
    val pointsByIdn = state.pointsNarys.associateBy { it.id }
    state.segmentsNarys.forEach { seg ->
        val start = pointsByIdn[seg.start.id]
        val end = pointsByIdn[seg.end.id]
        if (start != null) start.parentSegment = seg
        if (end != null) end.parentSegment = seg
    }
    val pointsByIdp = state.pointsPudorys.associateBy { it.id }
    state.segmentsPudorys.forEach { seg ->
        val start = pointsByIdp[seg.start.id]
        val end = pointsByIdp[seg.end.id]
        if (start != null) start.parentSegment = seg
        if (end != null) end.parentSegment = seg
    }
    val pointsByIdb = state.pointsBokorys.associateBy { it.id }
    state.segmentsBokorys.forEach { seg ->
        val start = pointsByIdb[seg.start.id]
        val end = pointsByIdb[seg.end.id]
        if (start != null) start.parentSegment = seg
        if (end != null) end.parentSegment = seg
    }
    val pointsByIda = state.pointsAxo.associateBy { it.id }
    state.segmentsAxo.forEach { seg ->
        val start = pointsByIda[seg.start.id]
        val end = pointsByIda[seg.end.id]
        if (start != null) start.parentSegment = seg
        if (end != null) end.parentSegment = seg
    }
    state.curvesPudorys.clear()
    state.curvesNarys.clear()
    state.curves3D.clear()
    state.curvesBokorys.clear()
    state.curvesAxo.clear()
    state.curves3D.setAll(snapshot.curves3D)
    state.curvesNarys.setAll(snapshot.curvesNarys)
    state.curvesPudorys.setAll(snapshot.curvesPudorys)
    state.curvesBokorys.setAll(snapshot.curvesBokorys)
    state.curvesAxo.setAll(snapshot.curvesAxo)
    monge.input.ruledsurface.repairRuledSurfaceDirectrixOrder(state)
    monge.input.ruledsurface.ensureRuledSurfaceOutlines(state)
    monge.input.ruledsurface.ensureRuledSurfaceGeneratorLines(state)
    state.intersectionGroups.setAll(snapshot.intersectionGroups)
    if (state.selectedIntersectionGroupId != null &&
        state.intersectionGroups.none { it.id == state.selectedIntersectionGroupId }
    ) {
        state.selectedIntersectionGroupId = null
    }
    //OBLOUKY
    // ── ELIPSA
    state.ellipseArcParams3D.clear()
    state.ellipseArcEnds3D.clear()
    state.ellipseArcEnds.clear()
    state.ellipseArcMode.clear()
    state.conicSegments.clear()

    state.ellipseArcParams3D.putAll(snapshot.ellipseArcParams3D)
    state.ellipseArcEnds3D.putAll(snapshot.ellipseArcEnds3D)
    state.ellipseArcEnds.putAll(snapshot.ellipseArcEnds)
    state.ellipseArcMode.putAll(snapshot.ellipseArcMode)
    state.conicSegments.putAll(snapshot.conicSegments)

    // ── PARABOLA
    state.parabolaArcParams3D.clear()
    state.parabolaArcEnds3D.clear()
    state.parabolaArcEnds.clear()

    state.parabolaArcParams3D.putAll(snapshot.parabolaArcParams3D)
    state.parabolaArcEnds3D.putAll(snapshot.parabolaArcEnds3D)
    state.parabolaArcEnds.putAll(snapshot.parabolaArcEnds)

    // ── HYPERBOLA
    state.hyperbolaArcParams3D.clear()
    state.hyperbolaArcEnds3D.clear()
    state.hyperbolaArcBranch1.clear()
    state.hyperbolaArcBranch2.clear()

    state.hyperbolaArcParams3D.putAll(snapshot.hyperbolaArcParams3D)
    state.hyperbolaArcEnds3D.putAll(snapshot.hyperbolaArcEnds3D)
    state.hyperbolaArcBranch1.putAll(snapshot.hyperbolaArcBranch1)
    state.hyperbolaArcBranch2.putAll(snapshot.hyperbolaArcBranch2)
    // ── HYPERBOLA
    state.hyperbolaInputsPudorys.clear()
    state.hyperbolaInputsNarys.clear()
    state.hyperbolaInputsBokorys.clear()
    state.hyperbolaInputsAxo.clear()
    state.hyperbolaInputsPudorys.putAll(snapshot.hyperbolaInputsPudorys)
    state.hyperbolaInputsNarys.putAll(snapshot.hyperbolaInputsNarys)
    state.hyperbolaInputsBokorys.putAll(snapshot.hyperbolaInputsBokorys)
    state.hyperbolaInputsAxo.putAll(snapshot.hyperbolaInputsAxo)

    //LABEL POZICE
    state.labelOffsetsNarys.clear()
    state.labelOffsetsPudorys.clear()
    state.labelOffsetsBokorys.clear()
    state.labelOffsetsPointsBokorys.clear()
    state.labelOffsetsPointsAxo.clear()
    state.labelOffsetsTraceBokorys.clear()
    state.labelOffsetsAidPoints.clear()
    state.labelOffsetsAOPoints.clear()
    state.labelOffsetsAOLines.clear()
    state.labelOffsetsAxoLines.clear()
    state.labelOffsetsSegmentsPudorys.clear()
    state.labelOffsetsSegmentsNarys.clear()
    state.labelOffsetsSegmentsBokorys.clear()
    state.labelOffsetsSegmentsAxo.clear()
    state.labelOffsetsHelpSegmentsPudorys.clear()
    state.labelOffsetsHelpSegmentsNarys.clear()
    state.labelOffsetsAOSegments.clear()
    state.labelOffsetsHelpNarys.clear()
    state.labelOffsetsHelpPudorys.clear()
    state.labelOffsetsTraceNarys.clear()
    state.labelOffsetsTracePudorys.clear()
    state.labelOffsetsPointsNarys.clear()
    state.labelOffsetsPointsPudorys.clear()
    state.labelOffsetsNarys.putAll(snapshot.labelOffsetsNarys)
    state.labelOffsetsPudorys.putAll(snapshot.labelOffsetsPudorys)
    state.labelOffsetsBokorys.putAll(snapshot.labelOffsetsBokorys)
    state.labelOffsetsAOLines.putAll(snapshot.labelOffsetsAOLine)
    state.labelOffsetsAidPoints.putAll(snapshot.labelOffsetsAidPoints)
    state.labelOffsetsAOPoints.putAll(snapshot.labelOffsetsAOPoints)
    state.labelOffsetsAxoLines.putAll(snapshot.labelOffsetsAxoLines)
    state.labelOffsetsSegmentsPudorys.putAll(snapshot.labelOffsetsSegmentsPudorys)
    state.labelOffsetsSegmentsNarys.putAll(snapshot.labelOffsetsSegmentsNarys)
    state.labelOffsetsSegmentsBokorys.putAll(snapshot.labelOffsetsSegmentsBokorys)
    state.labelOffsetsSegmentsAxo.putAll(snapshot.labelOffsetsSegmentsAxo)
    state.labelOffsetsHelpSegmentsPudorys.putAll(snapshot.labelOffsetsHelpSegmentsPudorys)
    state.labelOffsetsHelpSegmentsNarys.putAll(snapshot.labelOffsetsHelpSegmentsNarys)
    state.labelOffsetsAOSegments.putAll(snapshot.labelOffsetsAOSegments)
    state.labelOffsetsHelpNarys.putAll(snapshot.labelOffsetsHelpNarys)
    state.labelOffsetsHelpPudorys.putAll(snapshot.labelOffsetsHelpPudorys)
    state.labelOffsetsTraceNarys.putAll(snapshot.labelOffsetsTraceNarys)
    state.labelOffsetsTracePudorys.putAll(snapshot.labelOffsetsTracePudorys)
    state.labelOffsetsTraceBokorys.putAll(snapshot.labelOffsetsTraceBokorys)
    state.labelOffsetsPointsNarys.putAll(snapshot.labelOffsetsPointsNarys)
    state.labelOffsetsPointsPudorys.putAll(snapshot.labelOffsetsPointsPudorys)
    state.labelOffsetsPointsBokorys.putAll(snapshot.labelOffsetsPointsBokorys)
    state.labelOffsetsPointsAxo.putAll(snapshot.labelOffsetsPointsAxo)
    relinkConics2DTo3D(state)
    repairConicsAfterRestore(state)
    relinkConesToDirectrices(state, clear = true)
    relinkCylindersToDirectrices(state, clear = false)
    relinkSegments2DTo3D(state)
}
fun <T> SnapshotStateList<T>.setAll(newList: List<T>) {
    clear()
    addAll(newList)
}
fun <T> MutableList<T>.setAll(newList: List<T>) {
    clear()
    addAll(newList)
}
fun repairConicsAfterRestore(state: MongeState) {
    val haveP = state.conicsPudorys.groupBy { it.parent?.id ?: it.parentId }
    val haveN = state.conicsNarys.groupBy { it.parent?.id ?: it.parentId }
    val haveB = state.conicsBokorys.groupBy { it.parent?.id ?: it.parentId }
    val haveA = state.conicsAxo.groupBy { it.parent?.id ?: it.parentId }

    for (c3 in state.conics3D) {
        val needP = haveP[c3.id].isNullOrEmpty()
        val needN = haveN[c3.id].isNullOrEmpty()
        val needB =  if (state.projectionMode == ProjectionMode.AXO) {haveB[c3.id].isNullOrEmpty()}else { false }
        val needA =  if (state.projectionMode == ProjectionMode.AXO) {haveA[c3.id].isNullOrEmpty()}else { false }


        // DOPLNÍ P
        if (needP) {
            val mP = c3.projectToXY()
            val coeffsP = Matrix3x3.toCoefficients(mP)
            val p = ConicSectionPudorys(
                a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
                d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
                rawName = c3.rawName, localColor = c3.color,
                strokeWidth = c3.strokeWidth, lineStyle = c3.lineStyle,
                parent = c3
            )
            state.conicsPudorys.add(p)
        }

        // DOPLNÍ N
        if (needN) {
            val mN = c3.projectToXZ()
            val coeffsN = Matrix3x3.toCoefficients(mN)
            val n = ConicSectionNarys(
                a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
                d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
                rawName = c3.rawName, localColor = c3.color,
                strokeWidth = c3.strokeWidth, lineStyle = c3.lineStyle,
                parent = c3
            )
            planeNormalUnitOf(c3)?.let { nn ->
                n.isDegenerate = kotlin.math.abs(nn.y) < 1e-6f
                n.isLineDegenerate = false
                n.degenerateDir = null
            }
            state.conicsNarys.add(n)
        }
        if (needB) {
            val mB = c3.projectToYZ()
            val coeffsB = Matrix3x3.toCoefficients(mB)
            val n = ConicSectionBokorys(
                a = coeffsB[0], b = coeffsB[1], c = coeffsB[2],
                d = coeffsB[3], e = coeffsB[4], f = coeffsB[5],
                rawName = c3.rawName, localColor = c3.color,
                strokeWidth = c3.strokeWidth, lineStyle = c3.lineStyle,
                parent = c3
            )
        }
        if (needA) {
            if (state.basis !=null) {
                val mA = c3.projectToAxo(state.basis!!)
                val coeffsA = Matrix3x3.toCoefficients(mA)
                val a = ConicSectionAxo(
                    a = coeffsA[0], b = coeffsA[1], c = coeffsA[2],
                    d = coeffsA[3], e = coeffsA[4], f = coeffsA[5],
                    rawName = c3.rawName, localColor = c3.color,
                    strokeWidth = c3.strokeWidth, lineStyle = c3.lineStyle,
                    parent = c3
                )
            }
        }

        val type = classifyConicFromMatrix(c3.matrix)
        if (type == ConicType.ELLIPSE || type == ConicType.DEGENERATE) {
            state.conicsPudorys.filter { it.parent?.id == c3.id || it.parentId == c3.id }.forEach { ensureEllipseInputsP(state, it, c3) }
            state.conicsNarys.filter { it.parent?.id == c3.id || it.parentId == c3.id }.forEach { ensureEllipseInputsN(state, it, c3) }
            state.conicsBokorys.filter { it.parent?.id == c3.id || it.parentId == c3.id }.forEach {ensureEllipseInputsB(state, it, c3) }
            state.conicsAxo.filter { it.parent?.id == c3.id || it.parentId == c3.id }.forEach { ensureEllipseInputsA(state,it,c3) }
        }
        val isHyperbola = type == ConicType.HYPERBOLA
        if (isHyperbola) {
            val p = state.conicsPudorys.lastOrNull { it.parent === c3 }
            val n = state.conicsNarys.lastOrNull { it.parent === c3 }
            p?.let { rebuildHyperbolaInputsP(state, it) }
            n?.let { rebuildHyperbolaInputsN(state, it) }
        }
    }
}

private data class RestoredEllipseInputs3D(
    val center: Offset3D,
    val firstRadiusEnd: Offset3D,
    val secondRadiusEnd: Offset3D
)

private fun restoredEllipseInputs3D(c3: ConicSection3D): RestoredEllipseInputs3D {
    val axes = runCatching { computeEllipseAxes3D(c3) }.getOrNull()
    if (axes != null && axes.a.isFinite() && axes.b.isFinite() && axes.a > 1e-6f && axes.b > 1e-6f) {
        return RestoredEllipseInputs3D(
            center = c3.p0,
            firstRadiusEnd = c3.p0 + axes.uRotated * axes.a,
            secondRadiusEnd = c3.p0 + axes.vRotated * axes.b
        )
    }

    val a = (c3.a ?: 1f).takeIf { it.isFinite() && kotlin.math.abs(it) > 1e-6f } ?: 1f
    val b = (c3.b ?: a).takeIf { it.isFinite() && kotlin.math.abs(it) > 1e-6f } ?: a
    return RestoredEllipseInputs3D(
        center = c3.p0,
        firstRadiusEnd = c3.p0 + c3.u.normalized() * a,
        secondRadiusEnd = c3.p0 + c3.v.normalized() * b
    )
}

private fun ensureEllipseInputsP(state: MongeState, p: ConicSectionPudorys, c3: ConicSection3D) {
    if (state.conicInputPointsPudorys[p.id] != null) return
    val restored = restoredEllipseInputs3D(c3)
    state.conicInputPointsPudorys[p.id] = conjugateDiameterInputFromRadii(
        center = Offset(restored.center.x, restored.center.y),
        firstRadiusEnd = Offset(restored.firstRadiusEnd.x, restored.firstRadiusEnd.y),
        secondRadiusEnd = Offset(restored.secondRadiusEnd.x, restored.secondRadiusEnd.y)
    )
}

private fun ensureEllipseInputsN(state: MongeState, n: ConicSectionNarys, c3: ConicSection3D) {
    if (state.conicInputPointsNarys[n.id] != null) return
    val restored = restoredEllipseInputs3D(c3)
    state.conicInputPointsNarys[n.id] = conjugateDiameterInputFromRadii(
        center = Offset(restored.center.x, -restored.center.z),
        firstRadiusEnd = Offset(restored.firstRadiusEnd.x, -restored.firstRadiusEnd.z),
        secondRadiusEnd = Offset(restored.secondRadiusEnd.x, -restored.secondRadiusEnd.z)
    )
}
private fun ensureEllipseInputsB(state: MongeState, n: ConicSectionBokorys, c3: ConicSection3D) {
    if (state.conicInputPointsBokorys[n.id] != null) return
    val restored = restoredEllipseInputs3D(c3)
    state.conicInputPointsBokorys[n.id] = conjugateDiameterInputFromRadii(
        center = Offset(restored.center.x, -restored.center.z),
        firstRadiusEnd = Offset(restored.firstRadiusEnd.x, -restored.firstRadiusEnd.z),
        secondRadiusEnd = Offset(restored.secondRadiusEnd.x, -restored.secondRadiusEnd.z)
    )
}
private fun ensureEllipseInputsA(state: MongeState, n: ConicSectionAxo, c3: ConicSection3D) {
    if (state.conicInputPointsAxo[n.id] != null) return
    val basis = state.basis ?: return
    val restored = restoredEllipseInputs3D(c3)
    state.conicInputPointsAxo[n.id] = conjugateDiameterInputFromRadii(
        center = projectPoint3DToAxoLocal(restored.center, basis),
        firstRadiusEnd = projectPoint3DToAxoLocal(restored.firstRadiusEnd, basis),
        secondRadiusEnd = projectPoint3DToAxoLocal(restored.secondRadiusEnd, basis)
    )
}

private fun planeNormalUnitOf(c3: ConicSection3D): Offset3D? {
    val eq = try { toModelPlaneEquation(planeEquationFromConic3D(c3)) }
    catch (_: Throwable) { return null }

    val ax = eq.a; val ay = eq.b; val az = eq.c
    val L = kotlin.math.sqrt(ax*ax + ay*ay + az*az)
    if (!L.isFinite() || L < 1e-9f) return null
    return Offset3D(ax/L, ay/L, az/L)
}


private data class K(val a:Float,val b:Float,val c:Float,val d:Float,val e:Float,val f:Float)

private fun conicCenter2D(k: K): Offset? {
    val bh = k.b*0.5f
    val det = k.a*k.c - bh*bh
    if (kotlin.math.abs(det) < 1e-9f) return null
    val ia =  k.c/det
    val ib = -bh/det
    val ic =  k.a/det
    val x0 = -0.5f*(ia*k.d + ib*k.e)
    val y0 = -0.5f*(ib*k.d + ic*k.e)
    return Offset(x0,y0)
}

private fun asymptoteDirsFromQuadratic(a:Float,b:Float,c:Float): Pair<Offset,Offset> {
    val EPS=1e-7f
    fun norm(v: Offset): Offset {
        val L=kotlin.math.hypot(v.x.toDouble(),v.y.toDouble()).toFloat().coerceAtLeast(EPS)
        return Offset(v.x/L,v.y/L)
    }
    if (kotlin.math.abs(c) >= EPS) {
        val D = b*b - 4f*a*c
        val s = kotlin.math.sqrt(kotlin.math.max(0f,D))
        val m1 = (-b + s)/(2f*c)
        val m2 = (-b - s)/(2f*c)
        return norm(Offset(1f,m1)) to norm(Offset(1f,m2))
    } else if (kotlin.math.abs(b) >= EPS) {
        return norm(Offset(1f, -a/b)) to Offset(0f,1f)
    } else {
        return Offset(1f,0f) to Offset(0f,1f)
    }
}

private fun rebuildHyperbolaInputsP(state: MongeState, p: ConicSectionPudorys) {
    val eq = toModelPlaneEquation(planeEquationFromConic3D(p.parent!!))
    val (vertexP, axisP) = extractVertexAndAxisFromPudorys(p, eq)

    val k = K(p.a,p.b,p.c,p.d,p.e,p.f)
    val C = conicCenter2D(k) ?: return
    val (d1,d2) = asymptoteDirsFromQuadratic(p.a,p.b,p.c)

    val l1 = Line3DProjectionPudorys(point = Point3DPudorys(C.x, C.y), direction = d1)
    val l2 = Line3DProjectionPudorys(point = Point3DPudorys(C.x, C.y), direction = d2)

    state.hyperbolaInputsPudorys[p.id] = ConicInputHyperbolaPudorys(
        vertex = vertexP, axis = axisP, line1 = l1, line2 = l2
    )
}

private fun rebuildHyperbolaInputsN(state: MongeState, n: ConicSectionNarys) {
    val eq = toModelPlaneEquation(planeEquationFromConic3D(n.parent!!))
    val (vertexNnative, axisNnative) = extractVertexAndAxisFromNarys(n, eq)

    val k = K(n.a,n.b,n.c,n.d,n.e,n.f)
    val C = conicCenter2D(k) ?: return
    val (d1nat,d2nat) = asymptoteDirsFromQuadratic(n.a,n.b,n.c) // v NATIVE (x,z)

    val l1 = Line3DProjectionNarys(point = Point3DNarys(C.x, C.y), direction = d1nat)
    val l2 = Line3DProjectionNarys(point = Point3DNarys(C.x, C.y), direction = d2nat)

    state.hyperbolaInputsNarys[n.id] = ConicInputHyperbolaNarys(
        vertex = Offset(vertexNnative.x, -vertexNnative.y),
        axis = Offset(axisNnative.x, -axisNnative.y),
        line1 = l1,
        line2 = l2
    )
}
private fun rebuildHyperbolaInputsB(state: MongeState, b: ConicSectionBokorys){
TODO()
}
fun HistoryState.debug(tag: String) {
    println("[$tag] size=${snapshots.size} cursor=${cursor}")
}



fun undo(state: MongeState) {
    ensureCurrentSaved(state)
    val snap = undo(state.history) ?: return

    applySnapshot(state, snap)
    state.historyVersion++
}
fun redo(state: MongeState) {
    val snap = redo(state.history) ?: return
    applySnapshot(state, snap)
    state.historyVersion++
}
fun relinkConesToDirectrices(state: MongeState, clear: Boolean) {
    if (clear) state.conics3D.forEach { it.directrixOfSurfaceIds = emptySet() }
    val conicById = state.conics3D.associateBy { it.id }
    state.conicalSurfaces.forEach { surface ->
        conicById[surface.directrixId]?.let { c3d ->
            c3d.directrixOfSurfaceIds = c3d.directrixOfSurfaceIds + surface.id
        }
    }
}

fun relinkCylindersToDirectrices(state: MongeState, clear: Boolean) {
    if (clear) state.conics3D.forEach { it.directrixOfSurfaceIds = emptySet() }
    val conicById = state.conics3D.associateBy { it.id }
    state.cylindricalSurfaces.forEach { surface ->
        conicById[surface.directrixId]?.let { c3d ->
            c3d.directrixOfSurfaceIds = c3d.directrixOfSurfaceIds + surface.id
        }
    }
}
