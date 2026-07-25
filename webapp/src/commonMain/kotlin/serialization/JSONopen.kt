package serialization

import utils.replaceAll
import androidx.compose.ui.geometry.Offset
import model.Point3D
import model.ProjectionMode
import model.YAxisDirectionPlane
import model.classes.*
import monge.input.conixections.conjugateDiameterInputFromRadii
import serialization.classes.deserializeConicSegmentations
import serialization.classes.deserializeHyperbolaArcs3D
import serialization.classes.deserializeParabolaArcs3D
import serialization.classes.toHyperbolaInput
import serialization.classes.toRuntime
import state.MongeState
import kotlin.math.sqrt

fun SerializedMongeState.toMongeState(): MongeState {

    val state = MongeState()

    val points3D = points3D.map { it.toRuntime() }
    val points3DById = points3D.associateBy { it.id }
    val aop = axoOverlayPoint.map { it.toRuntime() }
    val aol = axoOverlayLine.map { it.toRuntime() }
    val aos = axoOverlaySegment.map { it.toRuntime() }


    val pointsPudorys = pointsPudorys.map { it.toRuntime(points3DById) }
    val pointsNarys = pointsNarys.map { it.toRuntime(points3DById) }
    val pointsBokorys = pointsBokorys.map { it.toRuntime(points3DById) }
    val pointsAxo = pointsAxo.map { it.toRuntime(points3DById) }
    val aidPointsLogical = this.aidPoints.map { it.toRuntime() }

    val lines3D = lines3D.map { it.toRuntime() }
    val lines3DById = lines3D.associateBy { it.id }
    val pointsBokorysById = pointsBokorys.associateBy { it.id }
    val pointsPudorysById = pointsPudorys.associateBy { it.id }
    val pointsAxoById = pointsAxo.associateBy { it.id }
    val pointsNarysById = pointsNarys.associateBy { it.id }

    val lines3DPudorys = this.lines3DPudorys.map {
        it.toRuntime(lines3DById)
    }
    val lines3DNarys = this.lines3DNarys.map {
        it.toRuntime(lines3DById)
    }
    val lines3DBokorys = this.lines3DBokorys.map {
        it.toRuntime(lines3DById)
    }
    val lines3DAxo = this.lines3DAxo.map { it.toRuntime(lines3DById)  }
    val spheres = this.spheres3D.map {it.toRuntime()}
    val helpLinesNarys = this.helpLinesNarys.map { it.toRuntime() }
    val helpLinesPudorys = this.helpLinesPudorys
        .map { it.toRuntime() }

    val segments3D = this.segments3D.map { it.toRuntime() }
    val segments3DById = segments3D.associateBy { it.id }
    val segmentSolids3D = this.segmentSolids3D.map { it.toRuntime() }
    val conics3D = this.conics3D.map { it.toRuntime() }
    val conics3DById = conics3D.associateBy { it.id }
    val regularPolygons3D = this.polygons3D.map { it.toRuntime() }
    val planePolygons2D = this.planePolygons2D.map { it.toRuntime() }

    val segmentsNarys = this.segmentsNarys.mapNotNull {
        it.toRuntime(pointsNarysById, segments3DById)
    }
    val segmentsPudorys = this.segmentsPudorys.mapNotNull {
        it.toRuntime(pointsPudorysById, segments3DById)
    }
    val segmentsAxo = this.segmentsAxo.mapNotNull {
        it.toRuntime(pointsAxoById, segments3DById)
    }
    val segmentsBokorys = this.segmentsBokorys.mapNotNull {
        it.toRuntime(pointsBokorysById, segments3DById)
    }
    val helpSegmentsNarys = this.helpSegmentsNarys.map { it ->
        it.toRuntime(segments3D.associateBy { it.id })
    }

    val helpSegmentsPudorys = this.helpSegmentsPudorys.map { it ->
        it.toRuntime(segments3D.associateBy { it.id })
    }
    val circlesPudorys = this.circlesPudorys.map {it.toRuntime()}
    val circlesNarys = this.circlesNarys.map {it.toRuntime()}
    val circlesBokorys = this.circlesBokorys.map {it.toRuntime()}

    val conicsNarys= conicSectionNarys.map { it.toRuntime() }
    val conicsPudorys = conicSectionPudorys.map { it.toRuntime() }
    val conicsBokorys = conicSectionBokorys.map { it.toRuntime() }
    val conicsAxo = conicSectionAxo.map{it.toRuntime()}
    val curvesNarys = curvesNarys.map{it.toRuntime()}
    val curvesPudorys = curvesPudorys.map{it.toRuntime()}
    val curves3D = curves3D.map{it.toRuntime()}
    val curvesBokorys = curvesBokorys.map{it.toRuntime()}
    val curvesAxo = curvesAxo.map{it.toRuntime()}
    val intersectionGroups = this.intersectionGroups.map { group ->
        IntersectionGroup(
            id = group.id,
            operandAId = group.operandAId,
            operandBId = group.operandBId,
            operandALabel = group.operandALabel,
            operandBLabel = group.operandBLabel,
            parts = group.parts.mapNotNull { part ->
                runCatching { IntersectionPartKind.valueOf(part.kind) }
                    .getOrNull()
                    ?.let { IntersectionPartRef(it, part.id) }
            },
            creationIndex = group.creationIndex
        )
    }
    val conicInputPointsPudorys = mutableMapOf<String, Triple<Offset, Offset, Offset>>()
    val conicInputPointsAxo = mutableMapOf<String,Triple<Offset,Offset,Offset>>()
    val hyperbolaInputAxo = mutableMapOf<String, ConicInputHyperbolaAxo>()
    val hyperbolaInputsPudorys = mutableMapOf<String, ConicInputHyperbolaPudorys>()
    val parentMappingPudorys = conicSectionPudorys.associate { it.id to it.parent }
    val parentMappingNarys   = conicSectionNarys.associate { it.id to it.parent }
    val parentMappingBokorys = conicSectionBokorys.associate { it.id to it.parent }
    val parentMappingAxo = conicSectionAxo.associate { it.id to it.parent }

    for (conic in conicsPudorys) {
        val parentId = parentMappingPudorys[conic.id]
        val parent = parentId?.let { conics3DById[it] }
        conic.parent = parent
    }
    for (conic in conicsNarys) {
        val parentId = parentMappingNarys[conic.id]
        val parent = parentId?.let { conics3DById[it] }
        conic.parent = parent
    }
    for (conic in conicsBokorys) {
        val parentId = parentMappingBokorys[conic.id]
        val parent = parentId?.let { conics3DById[it] }
        conic.parent = parent
    }
    for (conic in conicsAxo) {
        val parentId = parentMappingAxo[conic.id]
        val parent = parentId?.let { conics3DById[it] }
        conic.parent = parent
    }

    for (serialized in conicSectionPudorys) {
        val id = serialized.id

        val p1 = serialized.inputPoint1?.toOffset()
        val p2 = serialized.inputPoint2?.toOffset()
        val p3 = serialized.inputPoint3?.toOffset()
        if (p1 != null && p2 != null) {
            conicInputPointsPudorys[id] = Triple(p1, p2, p3 ?: Offset.Unspecified)
        }

        val hyperbolaInput = serialized.toHyperbolaInput()
        if (hyperbolaInput != null) {
            hyperbolaInputsPudorys[id] = hyperbolaInput
        }
    }

    val conicInputPointsNarys = mutableMapOf<String, Triple<Offset, Offset, Offset>>()

    val hyperbolaInputsNarys = mutableMapOf<String, ConicInputHyperbolaNarys>()
    val conicInputPointsBokorys = mutableMapOf<String, Triple<Offset, Offset, Offset>>()
    val hyperbolaInputsBokorys = mutableMapOf<String, ConicInputHyperbolaBokorys>()
    for (serialized in conicSectionNarys) {
        val id = serialized.id
        val p1 = serialized.inputPoint1?.toOffset()
        val p2 = serialized.inputPoint2?.toOffset()
        val p3 = serialized.inputPoint3?.toOffset()

        if (p1 != null && p2 != null) {
            conicInputPointsNarys[id] = Triple(p1, p2, p3 ?: Offset.Unspecified)
        }
        val hyperbolaInput = serialized.toHyperbolaInput()
        if (hyperbolaInput != null) {
            hyperbolaInputsNarys[id] = hyperbolaInput
        }
    }
    for (serialized in conicSectionBokorys) {
        val id = serialized.id
        val p1 = serialized.inputPoint1?.toOffset()
        val p2 = serialized.inputPoint2?.toOffset()
        val p3 = serialized.inputPoint3?.toOffset()

        if (p1 != null && p2 != null) {
            conicInputPointsBokorys[id] = Triple(p1, p2, p3 ?: Offset.Unspecified)
        }
        val hyperbolaInput = serialized.toHyperbolaInput()
        if (hyperbolaInput != null) {
            hyperbolaInputsBokorys[id] = hyperbolaInput
        }
    }
    for (serialized in conicSectionAxo) {
        val id = serialized.id
        val p1 = serialized.inputPoint1?.toOffset()
        val p2 = serialized.inputPoint2?.toOffset()
        val p3 = serialized.inputPoint3?.toOffset()

        if (p1 != null && p2 != null) {
            conicInputPointsAxo[id] = Triple(p1, p2, p3 ?: Offset.Unspecified)
        }
        val hyperbolaInput= serialized.toHyperbolaInput()
        if (hyperbolaInput != null) {
            hyperbolaInputAxo[id] = hyperbolaInput
        }
    }

    val conicalSurfaces = this.conicalSurfaces.map { it.toRuntime() }

    val cylindricalSurfaces = this.cylindricalSurfaces.map {it.toRuntime()}
    val ruledSurfaces = this.ruledSurfaces.map { it.toRuntime() }
    val arcsNarys = this.arcsNarys.map { it.toRuntime() }
    val arcsPudorys = this.arcsPudorys.map { it.toRuntime()}
    val arcsBokorys = this.arcsBokorys.map { it.toRuntime() }
    val arcsAxoOverlay = this.arcsAxoOverlay.map { it.toRuntime() }
    val lineTracesNarys = this.planeTracesNarys.map { it.toRuntime() }.filterNot { it.isVirtual }
    val lineTracesPudorys = this.planeTracesPudorys.map { it.toRuntime() }.filterNot { it.isVirtual }
    val lineTracesBokorys = this.planeTracesBokorys.map { it.toRuntime() }.filterNot { it.isVirtual }.toMutableList()

    val pudorysByParentId = lineTracesPudorys.associateBy { it.parentId }
    val narysByParentId = lineTracesNarys.associateBy { it.parentId }
    val bokorysByParentId = lineTracesBokorys.associateBy { it.parentId }
    val pudorysById = lineTracesPudorys.associateBy { it.id }
    val narysById = lineTracesNarys.associateBy { it.id }
    val bokorysById = lineTracesBokorys.associateBy { it.id }

    val planes3D = mutableListOf<Plane3D>()

    for (serializedPlane in this.planes3D) {
        val pid = serializedPlane.id
        val traces = serializedPlane.equation?.let { tracesFromPlaneEquation(it) }

        val pud = pudorysByParentId[pid] ?: pudorysById[serializedPlane.tracePudorysId]
            ?: traces?.pudorys?.copy(parentId = pid, isVirtual = true)
            ?: PlaneTracePudorys(
                point = Point3DPudorys(0f, 0f, name = "", parent = Point3D(0f, 0f, 0f, "")),
                direction = Offset(1f, 0f),
                parentId = pid,
                id = "${pid}-pudorys-virtual",
                isVirtual = true
            )

        val nar = narysByParentId[pid] ?: narysById[serializedPlane.traceNarysId]
            ?: traces?.narys?.copy(parentId = pid, isVirtual = true)
            ?: PlaneTraceNarys(
                point = Point3DNarys(0f, 0f, name = "", parent = Point3D(0f, 0f, 0f, "")),
                direction = Offset(1f, 0f),
                parentId = pid,
                id = "${pid}-narys-virtual",
                isVirtual = true
            )

        val bok = bokorysByParentId[pid] ?: serializedPlane.traceBokorysId?.let(bokorysById::get)
            ?: traces?.bokorys?.copy(parentId = pid, isVirtual = true)
            ?: PlaneTraceBokorys(
                point = Point3DBokorys(
                    y = 0f, z = 0f, name = "",
                    parent = Point3D(0f, 0f, 0f, "")
                ),
                direction = Offset(1f, 0f),
                parentId = pid,
                id = "${pid}-bokorys-virtual",
                isVirtual = true
            )

        if (!pud.isVirtual && lineTracesPudorys.none { it.id == pud.id }) {
        }
        if (lineTracesBokorys.none { it.id == bok.id } && !bok.isVirtual) {
            lineTracesBokorys.add(bok)
        }

        val rovina = Plane3D(
            tracePudorys = pud,
            traceNarys = nar,
            traceBokorys = bok,
            name = serializedPlane.name,
            equation = serializedPlane.equation,
            color = serializedPlane.color.toColor(),
            lineStyle = serializedPlane.lineStyle.toLineStyle(),
            strokeWidth = serializedPlane.strokeWidth,
            id = pid,
            creationIndex = serializedPlane.creationIndex
        )

        pud.parent = rovina
        nar.parent = rovina
        bok.parent = rovina
        planes3D.add(rovina)
    }

    for (plane in planes3D) {
        state.lineTracesPudorys.find { it.parentId == plane.id }?.parent = plane
        state.lineTracesNarys.find { it.parentId == plane.id }?.parent = plane
        state.lineTracesBokorys.find { it.parentId == plane.id }?.parent = plane
    }
    val paperAnchor = if(this.paperAnchorLogical== null) Offset.Zero else this.paperAnchorLogical.toOffset()
    val paperPinned = this.paperAnchorPinned ?: false
    val showPreview = this.showPaperPreview ?: false
    val axisVisible = this.axisVisible ?: false


    state.clearAll()
    this.circleArcsPudorys.forEach { ser ->
        val arc = ser.toRuntime()
        state.circleArcEnds[arc.circleId] = arc.a to arc.b
        state.circleArcMode[arc.circleId] = arc.mode
    }
    this.circleArcsNarys.forEach { ser ->
        val arc = ser.toRuntime()
        state.circleArcEnds[arc.circleId] = arc.a to arc.b
        state.circleArcMode[arc.circleId] = arc.mode
    }

    state.axoOverlayPoints.addAll(aop)
    state.axoOverlayLines.addAll(aol)
    state.axoOverlaySegments.addAll(aos)
    state.solidsOfRevolutionNarys.addAll(solidsOfRevolutionsNarys.map { it.toRuntime() })
    state.solidsOfRevolutionPudorys.addAll(solidsOfRevolutionsPudorys.map { it.toRuntime() })
    state.paperAnchorLogical = paperAnchor
    state.paperAnchorPinned = paperPinned
    state.showPaperPreview = showPreview
    state.axisVisible = axisVisible
    state.projectionMode = this.projectionMode
    state.xAxisDirection = this.xAxisDirection
    state.yAxisDirectionPlane = if (projectionMode == ProjectionMode.MONGE) YAxisDirectionPlane.POSITIVE_DOWN else this.yAxisDirectionPlane
    state.conics3D.addAll(conics3D)
    state.hyperbolaInputsPudorys.putAll(hyperbolaInputsPudorys)
    state.hyperbolaInputsNarys.putAll(hyperbolaInputsNarys)
    state.hyperbolaInputsBokorys.putAll(hyperbolaInputsBokorys)
    state.hyperbolaInputsAxo.putAll(hyperbolaInputAxo)
    state.conicsPudorys.addAll(conicsPudorys)
    state.conicsBokorys.addAll(conicsBokorys)
    state.conicInputPointsPudorys.putAll(conicInputPointsPudorys)
    state.conicInputPointsBokorys.putAll(conicInputPointsBokorys)
    state.conicsNarys.addAll(conicsNarys)
    state.conicInputPointsNarys.putAll(conicInputPointsNarys)
    state.conicsAxo.addAll(conicsAxo)
    state.conicInputPointsAxo.putAll(conicInputPointsAxo)
    state.circlesPudorys.addAll(circlesPudorys)
    state.circlesNarys.addAll(circlesNarys)
    state.circlesBokorys.addAll(circlesBokorys)
    state.sharedPoints3D.addAll(points3D)
    state.pointsPudorys.addAll(pointsPudorys)
    state.pointsNarys.addAll(pointsNarys)
    state.pointsBokorys.addAll(pointsBokorys)
    state.pointsAxo.addAll(pointsAxo)
    state.lines3D.addAll(lines3D)
    state.lines3DPudorys.addAll(lines3DPudorys)
    state.lines3DNarys.addAll(lines3DNarys)
    state.lines3DBokorys.addAll(lines3DBokorys)
    state.lines3DAxo.addAll(lines3DAxo)
    state.helpLineNarys.addAll(helpLinesNarys)
    state.helpLinePudorys.addAll(helpLinesPudorys)
    state.helpSegmentsNarys.addAll(helpSegmentsNarys)
    state.helpSegmentsPudorys.addAll(helpSegmentsPudorys)
    state.segmentsNarys.addAll(segmentsNarys)
    state.segmentsPudorys.addAll(segmentsPudorys)
    state.segmentsBokorys.addAll(segmentsBokorys)
    state.segmentsAxo.addAll(segmentsAxo)
    state.segments3D.addAll(segments3D)
    state.segmentSolids3D.addAll(segmentSolids3D)
    state.arcsNarys.addAll(arcsNarys)
    state.arcsPudorys.addAll(arcsPudorys)
    state.arcsBokorys.addAll(arcsBokorys)
    state.arcsAxoOverlay.addAll(arcsAxoOverlay)
    state.planes3D.addAll(planes3D)
    state.lineTracesNarys.addAll(lineTracesNarys)
    state.lineTracesPudorys.addAll(lineTracesPudorys)
    state.lineTracesBokorys.addAll(lineTracesBokorys)
    state.aidPointsLogical.addAll(aidPointsLogical)
    state.polygons3D.addAll(regularPolygons3D)
    state.planePolygons2D.addAll(planePolygons2D)
    state.curvesNarys.addAll(curvesNarys)
    state.curvesPudorys.addAll(curvesPudorys)
    state.curves3D.addAll(curves3D)
    state.curvesBokorys.addAll(curvesBokorys)
    state.curvesAxo.addAll(curvesAxo)
    state.intersectionGroups.addAll(intersectionGroups)
    val curves3DById = curves3D.associateBy { it.id }
    state.curvesPudorys.replaceAll { c ->
        val p = c.parentId?.let { curves3DById[it] }
        if (p != null) c.copy(parent = p) else c
    }
    state.curvesNarys.replaceAll { c ->
        val p = c.parentId?.let { curves3DById[it] }
        if (p != null) c.copy(parent = p) else c
    }
    state.curvesBokorys.replaceAll { c ->
        val p = c.parentId?.let { curves3DById[it] }
        if (p != null) c.copy(parent = p) else c
    }
    state.curvesAxo.replaceAll { c ->
        val p = c.parentId?.let { curves3DById[it] }
        if (p != null) c.copy(parent = p) else c
    }
    state.axoSetupVisible = false
    state.pointsNarys.forEach { p ->
        p.parentLine = p.pendingParentLineId?.let { lines3DById[it] }
    }
    state.pointsPudorys.forEach { p ->
        p.parentLine = p.pendingParentLineId?.let { lines3DById[it] }
    }
    state.pointsBokorys.forEach { p ->
        p.parentLine = p.pendingParentLineId?.let { lines3DById[it] }
    }
    state.pointsAxo.forEach { p ->
        p.parentLine = p.pendingParentLineId?.let { lines3DById[it] }
    }
    ellipseArcsPudorys.forEach { arc ->
        val inputs = conicInputPointsPudorys[arc.conicId]
        if (inputs != null && inputs.third != Offset.Unspecified) {
            state.ellipseArcEnds[arc.conicId] = arc.pA.toOffset() to arc.pB.toOffset()
            state.ellipseArcMode[arc.conicId] = arc.mode
        } else {
            println("⚠️ Arc přeskočen: nenalezeny vstupy elipsy pro conicId=${arc.conicId}")
        }
    }
    ellipseArcsNarys.forEach { arc ->
        val inputs = state.conicInputPointsNarys[arc.conicId]
        if (inputs != null && inputs.third != Offset.Unspecified) {
            state.ellipseArcEnds[arc.conicId] = arc.pA.toOffset() to arc.pB.toOffset()
            state.ellipseArcMode[arc.conicId] = arc.mode
        } else {
            println("⚠️ [N] Arc přeskočen: nenalezeny vstupy elipsy pro conicId=${arc.conicId}")
        }
    }
    parabolaArcsPudorys.forEach { arc ->
        val inputs = state.conicInputPointsPudorys[arc.conicId]
        if (inputs != null && inputs.third == Offset.Unspecified) {
            state.parabolaArcEnds[arc.conicId] = arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    parabolaArcsNarys.forEach { arc ->
        val inputs = state.conicInputPointsNarys[arc.conicId]
        if (inputs != null && inputs.third == Offset.Unspecified) {
            state.parabolaArcEnds[arc.conicId] = arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    ellipseArcsBokorys.forEach { arc ->
        val inputs = state.conicInputPointsBokorys[arc.conicId]
        if (inputs != null && inputs.third != Offset.Unspecified) {
            state.ellipseArcEnds[arc.conicId] = arc.pA.toOffset() to arc.pB.toOffset()
            state.ellipseArcMode[arc.conicId] = arc.mode
        }
    }
    ellipseArcsAxo.forEach { arc ->
        val inputs = state.conicInputPointsAxo[arc.conicId]
        if (inputs != null && inputs.third != Offset.Unspecified) {
            state.ellipseArcEnds[arc.conicId] = arc.pA.toOffset() to arc.pB.toOffset()
            state.ellipseArcMode[arc.conicId] = arc.mode
        }
    }
    parabolaArcsBokorys.forEach { arc ->
        val inputs = state.conicInputPointsBokorys[arc.conicId]
        if (inputs != null && inputs.third == Offset.Unspecified) {
            state.parabolaArcEnds[arc.conicId] = arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    parabolaArcsAxo.forEach { arc ->
        val inputs = state.conicInputPointsAxo[arc.conicId]
        if (inputs != null && inputs.third == Offset.Unspecified) {
            state.parabolaArcEnds[arc.conicId] = arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    // PŮDORYS – hyperbola větve
    hyperbolaBranch1Pudorys.forEach { arc ->
        // importujeme jen, pokud ten conic je opravdu hyperbola
        if (state.hyperbolaInputsPudorys.containsKey(arc.conicId)) {
            state.hyperbolaArcBranch1[arc.conicId] =
                arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    hyperbolaBranch2Pudorys.forEach { arc ->
        if (state.hyperbolaInputsPudorys.containsKey(arc.conicId)) {
            state.hyperbolaArcBranch2[arc.conicId] =
                arc.pA.toOffset() to arc.pB.toOffset()
        }
    }

    hyperbolaBranch1Narys.forEach { arc ->
        if (state.hyperbolaInputsNarys.containsKey(arc.conicId)) {
            state.hyperbolaArcBranch1[arc.conicId] =
                arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    hyperbolaBranch2Narys.forEach { arc ->
        if (state.hyperbolaInputsNarys.containsKey(arc.conicId)) {
            state.hyperbolaArcBranch2[arc.conicId] =
                arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    hyperbolaBranch1Bokorys.forEach { arc ->
        if (state.hyperbolaInputsBokorys.containsKey(arc.conicId)) {
            state.hyperbolaArcBranch1[arc.conicId] =
                arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    hyperbolaBranch2Bokorys.forEach { arc ->
        if (state.hyperbolaInputsBokorys.containsKey(arc.conicId)) {
            state.hyperbolaArcBranch2[arc.conicId] =
                arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    hyperbolaBranch1Axo.forEach { arc ->
        if (state.hyperbolaInputsAxo.containsKey(arc.conicId)) {
            state.hyperbolaArcBranch1[arc.conicId] =
                arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    hyperbolaBranch2Axo.forEach { arc ->
        if (state.hyperbolaInputsAxo.containsKey(arc.conicId)) {
            state.hyperbolaArcBranch2[arc.conicId] =
                arc.pA.toOffset() to arc.pB.toOffset()
        }
    }
    state.spheres3D.addAll(spheres)
    repairLoadedSphereConics(state)
    state.deserializeHyperbolaArcs3D(hyperbolaArcs3D)
    state.toRuntime(ellipseArcs3D)
    state.deserializeParabolaArcs3D(parabolaArcs3D)
    state.deserializeConicSegmentations(conicSegmentations)
    state.labelOffsetsNarys.clear()
    state.labelOffsetsPointsBokorys.clear()
    state.labelOffsetsPointsAxo.clear()
    state.labelOffsetsTraceBokorys.clear()
    state.labelOffsetsPudorys.clear()
    state.labelOffsetsBokorys.clear()
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
    state.labelOffsetsNarys.clear()
    state.conicalSurfaces.addAll(conicalSurfaces)
    state.cylindricalSurfaces.addAll(cylindricalSurfaces)
    state.ruledSurfaces.addAll(ruledSurfaces)



    state.activeAxoModel =this.axoModel.toRuntime()
    state.labelOffsetsNarys.putAll(this.labelOffsetsNarys.mapValues { it.value.toOffset() })
    state.labelOffsetsPudorys.putAll(this.labelOffsetsPudorys.mapValues { it.value.toOffset() })
    state.labelOffsetsBokorys.putAll(this.labelOffsetsBokorys.mapValues { it.value.toOffset() })
    state.labelOffsetsAidPoints.putAll(this.labelOffsetsAidPoints.mapValues { it.value.toOffset() })
    state.labelOffsetsAOPoints.putAll(this.labelOffsetsAOPoints.mapValues { it.value.toOffset() })
    state.labelOffsetsHelpNarys.putAll(this.labelOffsetsHelpNarys.mapValues { it.value.toOffset() })
    state.labelOffsetsHelpPudorys.putAll(this.labelOffsetsHelpPudorys.mapValues { it.value.toOffset() })
    state.labelOffsetsTraceNarys.putAll(this.labelOffsetsTraceNarys.mapValues { it.value.toOffset() })
    state.labelOffsetsTracePudorys.putAll(this.labelOffsetsTracePudorys.mapValues { it.value.toOffset() })
    state.labelOffsetsPointsNarys.putAll(this.labelOffsetsPointsNarys.mapValues { it.value.toOffset() })
    state.labelOffsetsPointsPudorys.putAll(this.labelOffsetsPointsPudorys.mapValues { it.value.toOffset() })
    state.labelOffsetsPointsBokorys.putAll(this.labelOffsetsPointsBokorys.mapValues { it.value.toOffset() })
    state.labelOffsetsPointsAxo.putAll(this.labelOffsetsPointsAxo.mapValues { it.value.toOffset() })
    state.labelOffsetsTraceBokorys.putAll(this.labelOffsetsTraceBokorys.mapValues{it.value.toOffset()})
    state.labelOffsetsAOLines.putAll(this.labelOffsetsAOLines.mapValues { it.value.toOffset() })
    state.labelOffsetsAxoLines.putAll(this.labelOffsetsAxoLines.mapValues { it.value.toOffset() })
    state.labelOffsetsSegmentsPudorys.putAll(this.labelOffsetsSegmentsPudorys.mapValues { it.value.toOffset() })
    state.labelOffsetsSegmentsNarys.putAll(this.labelOffsetsSegmentsNarys.mapValues { it.value.toOffset() })
    state.labelOffsetsSegmentsBokorys.putAll(this.labelOffsetsSegmentsBokorys.mapValues { it.value.toOffset() })
    state.labelOffsetsSegmentsAxo.putAll(this.labelOffsetsSegmentsAxo.mapValues { it.value.toOffset() })
    state.labelOffsetsHelpSegmentsPudorys.putAll(this.labelOffsetsHelpSegmentsPudorys.mapValues { it.value.toOffset() })
    state.labelOffsetsHelpSegmentsNarys.putAll(this.labelOffsetsHelpSegmentsNarys.mapValues { it.value.toOffset() })
    state.labelOffsetsAOSegments.putAll(this.labelOffsetsAOSegments.mapValues { it.value.toOffset() })
    relinkSegments2DTo3D(state)
    val maxIndex =
        buildList<Long> {
            addAll(aop.map{it.creationIndex})
            addAll(aol.map { it.creationIndex })
            addAll(points3D.map { it.creationIndex })
            addAll(lines3D.map { it.creationIndex })
            addAll(segments3D.map { it.creationIndex })
            addAll(conics3D.map { it.creationIndex })
            addAll(planes3D.map { it.creationIndex })
            addAll(circlesNarys.map { it.creationIndex })
            addAll(circlesPudorys.map { it.creationIndex })
            addAll(segmentsPudorys.map { it.creationIndex })
            addAll(segmentsNarys.map { it.creationIndex })
            addAll(lines3DPudorys.map { it.creationIndex })
            addAll(arcsNarys.map { it.creationIndex })
            addAll(arcsPudorys.map { it.creationIndex })
            addAll(arcsBokorys.map { it.creationIndex })
            addAll(arcsAxoOverlay.map { it.creationIndex })
            addAll(helpSegmentsPudorys.map { it.creationIndex })
            addAll(helpSegmentsNarys.map { it.creationIndex })
            addAll(helpLinesPudorys.map { it.creationIndex })
            addAll(helpLinesNarys.map { it.creationIndex })
            addAll(conicalSurfaces.map { it.creationIndex })
            addAll(cylindricalSurfaces.map { it.creationIndex })
            addAll(ruledSurfaces.map { it.creationIndex })
            addAll(regularPolygons3D.map { it.creationIndex })
            addAll(pointsPudorys.map { it.creationIndex })
            addAll(pointsNarys.map { it.creationIndex })
            addAll(aidPointsLogical.map { it.creationIndex })
            addAll(lineTracesNarys.map { it.creationIndex })
            addAll(lineTracesPudorys.map { it.creationIndex })
            addAll(intersectionGroups.map { it.creationIndex })

        }.maxOrNull() ?: 0L

    state.nextCreationIndex = maxIndex + 1
    repairConicalSurfaceSilhouettes(state)
    sanitizePlaneTraceGeometry(state)


    return state
}

// Starší verze tracesFromPlaneEquation dělila téměř nulovým koeficientem (rovina x=a má
// b,c ~ 1e-8), takže uložené soubory můžou nést bod stopy vzdálený ~1e9 jednotek – přímka
// pak na plátně kvůli ztrátě float přesnosti problikává. Takové stopy přepočítáme z rovnice.
private const val TRACE_COORD_SANE_LIMIT = 1e6f

private fun traceGeometrySane(px: Float, py: Float, direction: Offset): Boolean =
    px.isFinite() && py.isFinite() &&
        direction.x.isFinite() && direction.y.isFinite() &&
        kotlin.math.abs(px) <= TRACE_COORD_SANE_LIMIT &&
        kotlin.math.abs(py) <= TRACE_COORD_SANE_LIMIT

internal fun sanitizePlaneTraceGeometry(state: MongeState) {
    val rebuiltPlanes = state.planes3D.map { plane ->
        val eq = plane.equation ?: return@map plane
        val pudBad = !traceGeometrySane(plane.tracePudorys.point.x, plane.tracePudorys.point.y, plane.tracePudorys.direction)
        val narBad = !traceGeometrySane(plane.traceNarys.point.x, plane.traceNarys.point.z, plane.traceNarys.direction)
        val bokBad = !traceGeometrySane(plane.traceBokorys.point.y, plane.traceBokorys.point.z, plane.traceBokorys.direction)
        if (!pudBad && !narBad && !bokBad) return@map plane

        val traces = tracesFromPlaneEquation(eq)
        val pud = if (pudBad && traces.pudorys != null) {
            plane.tracePudorys.copy(point = traces.pudorys.point, direction = traces.pudorys.direction)
        } else plane.tracePudorys
        val nar = if (narBad && traces.narys != null) {
            plane.traceNarys.copy(point = traces.narys.point, direction = traces.narys.direction)
        } else plane.traceNarys
        val bok = if (bokBad && traces.bokorys != null) {
            plane.traceBokorys.copy(point = traces.bokorys.point, direction = traces.bokorys.direction)
        } else plane.traceBokorys

        plane.copy(tracePudorys = pud, traceNarys = nar, traceBokorys = bok).also { rebuilt ->
            rebuilt.tracePudorys.parent = rebuilt
            rebuilt.traceNarys.parent = rebuilt
            rebuilt.traceBokorys.parent = rebuilt

            val pudIdx = state.lineTracesPudorys.indexOfFirst { it.id == rebuilt.tracePudorys.id }
            if (pudIdx >= 0) state.lineTracesPudorys[pudIdx] = rebuilt.tracePudorys
            val narIdx = state.lineTracesNarys.indexOfFirst { it.id == rebuilt.traceNarys.id }
            if (narIdx >= 0) state.lineTracesNarys[narIdx] = rebuilt.traceNarys
            val bokIdx = state.lineTracesBokorys.indexOfFirst { it.id == rebuilt.traceBokorys.id }
            if (bokIdx >= 0) state.lineTracesBokorys[bokIdx] = rebuilt.traceBokorys
        }
    }
    for (i in rebuiltPlanes.indices) {
        if (state.planes3D[i] !== rebuiltPlanes[i]) state.planes3D[i] = rebuiltPlanes[i]
    }
}

private fun repairLoadedSphereConics(state: MongeState) {
    state.spheres3D.forEach { sphere ->
        state.circlesPudorys.firstOrNull { it.parentId == sphere.id }?.let { circle ->
            if (state.conicsPudorys.none { it.parentId == sphere.id }) {
                state.conicsPudorys.add(circle.copy(showInAxoInitial = false).also { it.showInAxo = false })
            }
        }
        state.circlesNarys.firstOrNull { it.parentId == sphere.id }?.let { circle ->
            if (state.conicsNarys.none { it.parentId == sphere.id }) {
                state.conicsNarys.add(circle.copy(showInAxoInitial = false).also { it.showInAxo = false })
            }
        }
        state.circlesBokorys.firstOrNull { it.parentId == sphere.id }?.let { circle ->
            if (state.conicsBokorys.none { it.parentId == sphere.id }) {
                state.conicsBokorys.add(circle.copy(showInAxoInitial = false).also { it.showInAxo = false })
            }
        }
        state.conicsPudorys.filter { it.parentId == sphere.id }.forEach { conic ->
            if (state.conicInputPointsPudorys[conic.id] == null) {
                state.conicInputPointsPudorys[conic.id] = circleInputPudorys(conic)
            }
        }
        state.conicsNarys.filter { it.parentId == sphere.id }.forEach { conic ->
            if (state.conicInputPointsNarys[conic.id] == null) {
                state.conicInputPointsNarys[conic.id] = circleInputNarys(conic)
            }
        }
        state.conicsBokorys.filter { it.parentId == sphere.id }.forEach { conic ->
            if (state.conicInputPointsBokorys[conic.id] == null) {
                state.conicInputPointsBokorys[conic.id] = circleInputBokorys(conic)
            }
        }
    }
    state.circlesPudorys.removeAll { it.parentId != null && state.spheres3D.any { sphere -> sphere.id == it.parentId } }
    state.circlesNarys.removeAll { it.parentId != null && state.spheres3D.any { sphere -> sphere.id == it.parentId } }
    state.circlesBokorys.removeAll { it.parentId != null && state.spheres3D.any { sphere -> sphere.id == it.parentId } }
}

private fun circleInputPudorys(circle: ConicSectionPudorys): Triple<Offset, Offset, Offset> {
    val center = Offset(-circle.d / (2f * circle.a), -circle.e / (2f * circle.c))
    val r = sqrt((center.x * center.x + center.y * center.y - circle.f / circle.a).coerceAtLeast(0f))
    return conjugateDiameterInputFromRadii(center, center + Offset(r, 0f), center + Offset(0f, r))
}

private fun circleInputNarys(circle: ConicSectionNarys): Triple<Offset, Offset, Offset> {
    val centerXZ = Offset(-circle.d / (2f * circle.a), -circle.e / (2f * circle.c))
    val center = Offset(centerXZ.x, -centerXZ.y)
    val r = sqrt((centerXZ.x * centerXZ.x + centerXZ.y * centerXZ.y - circle.f / circle.a).coerceAtLeast(0f))
    return conjugateDiameterInputFromRadii(center, center + Offset(r, 0f), center + Offset(0f, -r))
}

private fun circleInputBokorys(circle: ConicSectionBokorys): Triple<Offset, Offset, Offset> {
    val center = Offset(-circle.d / (2f * circle.a), -circle.e / (2f * circle.c))
    val r = sqrt((center.x * center.x + center.y * center.y - circle.f / circle.a).coerceAtLeast(0f))
    return conjugateDiameterInputFromRadii(center, center + Offset(r, 0f), center + Offset(0f, r))
}

private fun repairConicalSurfaceSilhouettes(state: MongeState) {
    state.conicalSurfaces.forEach { surface ->
        val segP = state.segmentsPudorys.filter { it.isConicalSilhouette && it.conicalSurfaceId == surface.id }
        val segN = state.segmentsNarys.filter { it.isConicalSilhouette && it.conicalSurfaceId == surface.id }
        val segB = state.segmentsBokorys.filter { it.isConicalSilhouette && it.conicalSurfaceId == surface.id }
        val segA = state.segmentsAxo.filter { it.isConicalSilhouette && it.conicalSurfaceId == surface.id }
        val hasLoadedP = segP.isNotEmpty() ||
                surface.edgeSegIdsPudorys2D.any { id -> state.segmentsPudorys.any { it.id == id } }
        val hasLoadedN = segN.isNotEmpty() ||
                surface.edgeSegIdsNarys2D.any { id -> state.segmentsNarys.any { it.id == id } }
        val hasLoadedB = segB.isNotEmpty() ||
                surface.edgeSegIdsBokorys2D.any { id -> state.segmentsBokorys.any { it.id == id } }
        val hasLoadedA = segA.isNotEmpty() ||
                surface.edgeSegIdsAxo2D.any { id -> state.segmentsAxo.any { it.id == id } }

        if (surface.edgeSegIdsPudorys2D.isEmpty() && segP.isNotEmpty()) {
            surface.edgeSegIdsPudorys2D.addAll(segP.map { it.id })
        }
        if (surface.edgeSegIdsNarys2D.isEmpty() && segN.isNotEmpty()) {
            surface.edgeSegIdsNarys2D.addAll(segN.map { it.id })
        }
        if (surface.edgeSegIdsBokorys2D.isEmpty() && segB.isNotEmpty()) {
            surface.edgeSegIdsBokorys2D.addAll(segB.map { it.id })
        }
        if (surface.edgeSegIdsAxo2D.isEmpty() && segA.isNotEmpty()) {
            surface.edgeSegIdsAxo2D.addAll(segA.map { it.id })
        }
        if (surface.edgePointIdsPudorys2D.isEmpty() && segP.isNotEmpty()) {
            surface.edgePointIdsPudorys2D.addAll(
                segP.flatMap { listOf(it.start.id, it.end.id) }.distinct()
            )
        }
        if (surface.edgePointIdsNarys2D.isEmpty() && segN.isNotEmpty()) {
            surface.edgePointIdsNarys2D.addAll(
                segN.flatMap { listOf(it.start.id, it.end.id) }.distinct()
            )
        }
        if (surface.edgePointIdsBokorys2D.isEmpty() && segB.isNotEmpty()) {
            surface.edgePointIdsBokorys2D.addAll(
                segB.flatMap { listOf(it.start.id, it.end.id) }.distinct()
            )
        }
        if (surface.edgePointIdsAxo2D.isEmpty() && segA.isNotEmpty()) {
            surface.edgePointIdsAxo2D.addAll(
                segA.flatMap { listOf(it.start.id, it.end.id) }.distinct()
            )
        }

        if (!hasLoadedP && !hasLoadedN && !hasLoadedB && !hasLoadedA) {
            val apex = state.sharedPoints3D.firstOrNull { it.id == surface.apexId } ?: return@forEach

        }
    }
}
fun MongeState.clearAll() {
    pointsAxo.clear()
    circleArcsPudorys.clear()
    axoOverlayLines.clear()
    axoOverlaySegments.clear()
    axoOverlayPoints.clear()
    circleArcsNarys.clear()
    solidsOfRevolutionNarys.clear()
    solidsOfRevolutionPudorys.clear()
    sharedPoints3D.clear()
    pointsPudorys.clear()
    pointsNarys.clear()
    aidPointsLogical.clear()
    lines3D.clear()
    lines3DPudorys.clear()
    lines3DNarys.clear()
    lines3DAxo.clear()
    lines3DBokorys.clear()
    conics3D.clear()
    conicsAxo.clear()
    conicsPudorys.clear()
    conicsNarys.clear()
    conicsBokorys.clear()
    helpLinePudorys.clear()
    helpLineNarys.clear()
    curvesNarys.clear()
    curvesPudorys.clear()
    curves3D.clear()
    curvesBokorys.clear()
    curvesAxo.clear()
    segments3D.clear()
    segmentSolids3D.clear()
    segmentsPudorys.clear()
    segmentsNarys.clear()
    segmentsAxo.clear()
    helpSegmentsPudorys.clear()
    helpSegmentsNarys.clear()
    arcsPudorys.clear()
    arcsNarys.clear()
    arcsBokorys.clear()
    arcsAxoOverlay.clear()
    planes3D.clear()
    lineTracesPudorys.clear()
    lineTracesNarys.clear()
    lineTracesBokorys.clear()
    spheres3D.clear()
    conicalSurfaces.clear()
    cylindricalSurfaces.clear()
    ruledSurfaces.clear()
    polygons3D.clear()
    planePolygons2D.clear()
    selectedPlanePolygons2D.clear()
    intersectionGroups.clear()
    selectedIntersectionGroupId = null

}
