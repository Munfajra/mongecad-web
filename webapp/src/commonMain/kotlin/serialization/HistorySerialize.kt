package serialization

import model.classes.isAxis
import model.classes.isAxisProjection
import model.classes.isAxisX
import model.classes.isAxisY
import model.classes.isAxisZ
import model.classes.isAxoPlane
import model.classes.isAxoPlaneTraceBokorys
import model.classes.isAxoPlaneTraceNarys
import model.classes.isAxoPlaneTracePudorys
import model.classes.isX12Line3D
import model.classes.isX12Narys
import model.classes.isX12Pud
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import kotlinx.serialization.Serializable
import model.ArcMode
import model.Offset3D
import model.Point3D
import model.classes.*
import serialization.classes.*
import state.MongeState

@Serializable
data class SerializableOffset(val x: Float, val y: Float) {
    companion object {
        fun from(offset: Offset) = SerializableOffset(offset.x, offset.y)
    }

    fun toOffset(): Offset =
        if (x.isNaN() || y.isNaN()) Offset.Unspecified else Offset(x, y)
}
fun Offset.toSerializable(): SerializableOffset =
    if (!isSpecified) {
        SerializableOffset(Float.NaN, Float.NaN)
    } else {
        SerializableOffset(x, y)
    }

fun Offset3D.toS() = SerializableOffset3D(x, y, z)
fun SerializableOffset3D.toOffset3D() = Offset3D(x, y, z)
@Serializable
data class STriple<A, B, C>(val first: A, val second: B, val third: C)
@Serializable
data class SPair<A, B>(val first: A, val second: B)
@Serializable
data class SerializableMongeSnapshot(
    val pointsPudorys: List<SerializablePoint3DPudorys>,
    val pointsNarys: List<SerializablePoint3DNarys>,
    val pointsBokorys: List<SerializablePoint3DBokorys>,
    val pointsAxo: List<SerializablePoint3DAxo>,
    val linesPudorys: List<SerializableLine3DProjectionPudorys>,
    val linesNarys: List<SerializableLine3DProjectionNarys>,
    val linesBokorys: List<SerializableLine3DProjectionBokorys>,
    val linesAxo: List<SerializableLine3DProjectionAxo>,
    val segmentsPudorys: List<SerializableSegment2DPudorys>,
    val segmentsNarys: List<SerializableSegment2DNarys>,
    val segmentsBokorys: List<SerializableSegment2DBokorys>,
    val segmentsAxo: List<SerializableSegment2DAxo>,
    val sharedPoints3D: List<SerializablePoint3D>,
    val lines3D: List<SerializableLine3D>,
    val segments3D: List<SerializableSegment3D>,
    val segmentSolids3D: List<SerializableSegmentSolid3D> = emptyList(),
    val planes3D: List<SerializablePlane3D>,
    val planeTracePudorys: List<SerializablePlaneTracePudorys>,
    val planeTraceNarys: List<SerializablePlaneTraceNarys>,
    val planeTraceBokorys: List<SerializablePlaneTraceBokorys>,
    val helpLinePudorys: List<SerializableHelpLinePudorys>,
    val helpLineNarys: List<SerializableHelpLineNarys>,
    val helpSegmentNarys: List<SerializableHelpSegmentNarys>,
    val helpSegmentPudorys: List<SerializableHelpSegmentPudorys>,
    val arcspudorys: List<SerializableArc2DPudorys>,
    val arcsnarys: List<SerializableArc2DNarys>,
    val arcsbokorys: List<SerializableArc2DBokorys> = emptyList(),
    val arcsAxoOverlay: List<SerializableArcAxoOverlay> = emptyList(),
    val circlesPudorys: List<SerializedConicSectionPudorys>,
    val circlesNarys: List<SerializedConicSectionNarys>,
    val circlesBokorys: List<SerializedConicSectionBokorys>,
    val aidPointsLogical: List<SerializableAidPointLogical>,
    val axoOverlayPoint: List<SerializableAxoOverlayPoint>,
    val axoOverlayLine: List<SerializableAOLine>,
    val axoOverlaySegment: List<SerializableAOSegment>,
    val conicsPudorys: List<SerializedConicSectionPudorys>,
    val conicsNarys: List<SerializedConicSectionNarys>,
    val conicsBokorys: List<SerializedConicSectionBokorys>,
    val conicsAxo: List<SerializedConicSectionAxo>,
    val conicInputPointsPudorys: Map<String, STriple<SerializableOffset, SerializableOffset, SerializableOffset>>,
    val conicInputPointsNarys: Map<String, STriple<SerializableOffset, SerializableOffset, SerializableOffset>>,
    val conicInputPointsBokorys: Map<String, STriple<SerializableOffset, SerializableOffset, SerializableOffset>>,
    val conicInputPointsAxo: Map<String, STriple<SerializableOffset, SerializableOffset, SerializableOffset>>,
    val conic3D: List<SerializableConicSection3D>,
    val conicalSurface: List<SerializableConicalSurface3D>,
    val cylinderSurface: List<SerializableCylindricalSurface3D>,
    val ruledSurfaces: List<SerializableRuledSurface3D> = emptyList(),
    val solidsOfRevolutionNarys: List<SerializableSolidOfRevolutionNarys>,
    val solidsOfRevolutionPudorys: List<SerializableSolidOfRevolutionPudorys>,
    val circleArcsPudorys: List<SerializableCircleArcPudorys> = emptyList(),
    val circleArcsNarys: List<SerializableCircleArcNarys> = emptyList(),
    // ELIPSA
    val ellipseArcParams3D: Map<String, Pair<Float, Float>> = emptyMap(),
    val ellipseArcEnds3D: Map<String, SPair<SerializableOffset3D, SerializableOffset3D>> = emptyMap(),
    val ellipseArcEnds: Map<String, SPair<SerializableOffset,SerializableOffset>> = emptyMap(),
    val ellipseArcMode: Map<String, ArcMode> = emptyMap(),
    val conicSegments: Map<String, serialization.classes.SerializedConicSegmentation> = emptyMap(),
    // PARABOLA
    val parabolaArcParams3D: Map<String, Pair<Float, Float>> = emptyMap(),
    val parabolaArcEnds3D: Map<String, SPair<SerializableOffset3D, SerializableOffset3D>> = emptyMap(),
    val parabolaArcEnds: Map<String, SPair<SerializableOffset, SerializableOffset>> = emptyMap(),
    // HYPERBOLA
    val hyperbolaArcParams3D: Map<String, SPair<SPair<Float, Float>?, SPair<Float, Float>?>> = emptyMap(),
    val hyperbolaArcEnds3D: Map<String, SPair<SPair<SerializableOffset3D, SerializableOffset3D>?, SPair<SerializableOffset3D, SerializableOffset3D>?>> = emptyMap(),
    val hyperbolaArcBranch1: Map<String, SPair<SerializableOffset, SerializableOffset>> = emptyMap(),
    val hyperbolaArcBranch2: Map<String, SPair<SerializableOffset,SerializableOffset>> = emptyMap(),
    val hyperbolaInputsPudorys: Map<String, SerializableConicInputHyperbolaPudorys> = emptyMap(),
    val hyperbolaInputsNarys: Map<String, SerializableConicInputHyperbolaNarys> = emptyMap(),
    val hyperbolaInputsBokorys: Map<String, SerializableConicInputHyperbolaBokorys> = emptyMap(),
    val hyperbolaInputsAxo: Map<String,SerializableConicInputHyperbolaAxo> = emptyMap(),
    val spheres: List<SphereSurface3DSerializable>,
    val polygons: List<SerializableRegularPolygon3D>,
    val planePolygons2D: List<SerializablePlanePolygon2D> = emptyList(),
    val curves3D: List<SerializableCurve3D>,
    val curvesNarys: List<SerializableCurveNarys>,
    val curvesPudorys: List<SerializableCurvePudorys>,
    val curvesBokorys: List<SerializableCurveBokorys> = emptyList(),
    val curvesAxo: List<SerializableCurveAxo> = emptyList(),
    val intersectionGroups: List<SerializableIntersectionGroup> = emptyList(),
    val labelOffsetsNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsPudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsBokorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsHelpNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsHelpPudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsPointsNarys: Map<String,  SerializableOffset> = emptyMap(),
    val labelOffsetsPointsPudorys: Map<String,  SerializableOffset> = emptyMap(),
    val labelOffsetsPointsBokorys: Map<String,  SerializableOffset> = emptyMap(),
    val labelOffsetsPointsAxo: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsTracePudorys: Map<String,  SerializableOffset> = emptyMap(),
    val labelOffsetsTraceNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsTraceBokorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAidPoints: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAOPoints: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAOLines: Map<String,SerializableOffset> = emptyMap(),
    val labelOffsetsAxoLines: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsSegmentsPudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsSegmentsNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsSegmentsBokorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsSegmentsAxo: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsHelpSegmentsPudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsHelpSegmentsNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAOSegments: Map<String, SerializableOffset> = emptyMap(),
    val axoModel: SerializableAxoModel,
    )
fun MongeSnapshot.toSerializable(): SerializableMongeSnapshot = SerializableMongeSnapshot(
    pointsPudorys = pointsPudorys.map { it.toSerializable() },
    pointsNarys   = pointsNarys.map { it.toSerializable() },
    pointsBokorys = pointsBokorys.map{it.toSerializable() },
    pointsAxo = pointsAxo.map { it.toSerializable() },
    circleArcsPudorys = circleArcsPudorys.map { it.toSerializable() },
    circleArcsNarys = circleArcsNarys.map { it.toSerializable() },
    lines3D = this.lines3D
        .filterNot(::isX12Line3D)
        .filterNot(::isAxis)
        .map { it.toSerializable() },

    linesNarys = this.linesNarys
        .filterNot(::isX12Narys)
        .filterNot(::isAxisProjection)
        .map { it.toSerializable() },

    linesPudorys = this.linesPudorys
        .filterNot(::isX12Pud)
        .filterNot(::isAxisProjection)
        .map { it.toSerializable() },
    linesAxo = this.linesAxo
        .filterNot(::isAxisProjection)
        .map { it.toSerializable() },
    linesBokorys = this.linesBokorys.map{it.toSerializable() },
    segmentsPudorys = segmentsPudorys.map { it.toSerializable() },
    segmentsNarys   = segmentsNarys.map { it.toSerializable() },
    segmentsBokorys = segmentsBokorys.map { it.toSerializable() },
    segmentsAxo = segmentsAxo.map { it.toSerializable() },

    sharedPoints3D = sharedPoints3D.map { it.toSerializable() },
    segments3D    = segments3D.map { it.toSerializable() },
    segmentSolids3D = segmentSolids3D.map { it.toSerializable() },
    planes3D      = planes3D.filterNot(::isAxoPlane).map { it.toSerializable() },
    planeTracePudorys = planeTracePudorys.filterNot(::isAxoPlaneTracePudorys).filterNot { it.isVirtual }.map { it.toSerializable() },
    planeTraceNarys   = planeTraceNarys.filterNot(::isAxoPlaneTraceNarys).filterNot { it.isVirtual }.map { it.toSerializable() },
    planeTraceBokorys = planeTraceBokorys.filterNot(::isAxoPlaneTraceBokorys).filterNot { it.isVirtual }.map{it.toSerializable() },
    helpLinePudorys = helpLinePudorys
        .filterNot(::isAxisX)
        .filterNot(::isAxisY)
        .map { it.toSerializable() },
    helpLineNarys   = helpLineNarys
        .filterNot(::isAxisZ)
        .map { it.toSerializable() },
    helpSegmentNarys   = helpSegmentNarys.map { it.toSerializable() },
    helpSegmentPudorys = helpSegmentPudorys.map { it.toSerializable() },
    arcspudorys = arcspudorys.map { it.toSerializable() },
    arcsnarys   = arcsnarys.map { it.toSerializable() },
    arcsbokorys = arcsbokorys.map { it.toSerializable() },
    arcsAxoOverlay = arcsAxoOverlay.map { it.toSerializable() },
    circlesPudorys = circlesPudorys.map { it.toSerializable() },
    circlesNarys   = circlesNarys.map { it.toSerializable() },
    circlesBokorys = circlesBokorys.map{it.toSerializable() },
    aidPointsLogical = aidPointsLogical.map { it.toSerializable() },
    axoOverlayPoint = axoOverlayPoint.map { it.toSerializable() },
    axoOverlayLine = axoOverlayLine.map { it.toSerializable() },
    axoOverlaySegment = axoOverlaySegment.map{it.toSerializable()},
    conicsPudorys = conicsPudorys.map { it.toSerializable() },
    conicsNarys   = conicsNarys.map { it.toSerializable() },
    conicsBokorys = conicsBokorys.map { it.toSerializable() },
    conicsAxo = conicsAxo.map{it.toSerializable()},
    curvesNarys = curvesNarys.map { it.toSerializable() },
    curvesPudorys = curvesPudorys.map { it.toSerializable() },
    curves3D = curves3D.map { it.toSerializable() },
    curvesBokorys = curvesBokorys.map { it.toSerializable() },
    curvesAxo = curvesAxo.map { it.toSerializable() },
    ruledSurfaces = ruledSurfaces.map { it.toSerializable() },
    intersectionGroups = intersectionGroups.map { group ->
        SerializableIntersectionGroup(
            id = group.id,
            operandAId = group.operandAId,
            operandBId = group.operandBId,
            operandALabel = group.operandALabel,
            operandBLabel = group.operandBLabel,
            parts = group.parts.map { SerializableIntersectionPartRef(it.kind.name, it.id) },
            creationIndex = group.creationIndex
        )
    },
    conicInputPointsPudorys = conicInputPointsPudorys.mapValues { (_, t) ->
        STriple(t.first.toSerializable(), t.second.toSerializable(), t.third.toSerializable())
    },
    conicInputPointsNarys = conicInputPointsNarys.mapValues { (_, t) ->
        STriple(t.first.toSerializable(), t.second.toSerializable(), t.third.toSerializable())
    },
    conicInputPointsBokorys = conicInputPointsBokorys.mapValues { (_, t) ->
        STriple(t.first.toSerializable(), t.second.toSerializable(), t.third.toSerializable())
    },
    conicInputPointsAxo = conicInputPointsAxo.mapValues { (_, t) ->
        STriple(t.first.toSerializable(), t.second.toSerializable(), t.third.toSerializable())
    },

    conic3D         = conic3D.map { it.toSerializable() },
    conicalSurface  = conicalSurface.map { it.toSerializable() },
    cylinderSurface = cylinderSurface.map { it.toSerializable() },
    solidsOfRevolutionNarys = solidsOfRevolutionsNarys.map{it.toSerializable()},
    solidsOfRevolutionPudorys = solidsOfRevolutionsPudorys.map{it.toSerializable()},
    // ELIPSA
    ellipseArcParams3D = ellipseArcParams3D,
    ellipseArcEnds3D = ellipseArcEnds3D.mapValues { SPair(it.value.first.toS(), it.value.second.toS()) },
    ellipseArcEnds = ellipseArcEnds.mapValues { SPair(it.value.first.toSerializable(), it.value.second.toSerializable()) },
    ellipseArcMode = ellipseArcMode,
    conicSegments = conicSegments.mapValues { it.value.toSerialized(it.key) },

    // PARABOLA
    parabolaArcParams3D = parabolaArcParams3D,
    parabolaArcEnds3D = parabolaArcEnds3D.mapValues { SPair(it.value.first.toS(), it.value.second.toS()) },
    parabolaArcEnds = parabolaArcEnds.mapValues { SPair(it.value.first.toSerializable(), it.value.second.toSerializable()) },

    // HYPERBOLA
    hyperbolaArcParams3D = hyperbolaArcParams3D.mapValues { (_, v) ->
        SPair(v.first?.let { SPair(it.first, it.second) }, v.second?.let { SPair(it.first, it.second) })
    },
    hyperbolaArcEnds3D = hyperbolaArcEnds3D.mapValues { (_, v) ->
        SPair(
            v.first?.let { SPair(it.first.toS(), it.second.toS()) },
            v.second?.let { SPair(it.first.toS(), it.second.toS()) }
        )
    },
    hyperbolaArcBranch1 = hyperbolaArcBranch1.mapValues { SPair(it.value.first.toSerializable(), it.value.second.toSerializable()) },
    hyperbolaArcBranch2 = hyperbolaArcBranch2.mapValues { SPair(it.value.first.toSerializable(), it.value.second.toSerializable()) },
    hyperbolaInputsPudorys = hyperbolaInputsPudorys.mapValues { it.value.toSerializable() },
    hyperbolaInputsNarys   = hyperbolaInputsNarys.mapValues   { it.value.toSerializable() },
    hyperbolaInputsBokorys = hyperbolaInputsBokorys.mapValues { it.value.toSerializable() },
    hyperbolaInputsAxo = hyperbolaInputsAxo.mapValues { it.value.toSerializable() },

    spheres  = spheres.map { it.toSerializable() },
    polygons = polygons.map { it.toSerializable() },
    planePolygons2D = planePolygons2D.map { it.toSerializable() },
    labelOffsetsNarys = labelOffsetsNarys.mapValues { it.value.toSerializable() },
    labelOffsetsPudorys = labelOffsetsPudorys.mapValues { it.value.toSerializable() },
    labelOffsetsBokorys = labelOffsetsBokorys.mapValues { it.value.toSerializable() },
    labelOffsetsHelpNarys = labelOffsetsHelpNarys.mapValues { it.value.toSerializable() },
    labelOffsetsHelpPudorys = labelOffsetsHelpPudorys.mapValues { it.value.toSerializable() },
    labelOffsetsPointsNarys = labelOffsetsPointsNarys.mapValues { it.value.toSerializable() },
    labelOffsetsPointsPudorys = labelOffsetsPointsPudorys.mapValues { it.value.toSerializable() },
    labelOffsetsPointsBokorys = labelOffsetsPointsBokorys.mapValues { it.value.toSerializable() },
    labelOffsetsPointsAxo = labelOffsetsPointsAxo.mapValues { it.value.toSerializable() },
    labelOffsetsTracePudorys = labelOffsetsTracePudorys.mapValues { it.value.toSerializable() },
    labelOffsetsTraceNarys = labelOffsetsTraceNarys.mapValues { it.value.toSerializable() },
    labelOffsetsTraceBokorys = labelOffsetsTraceBokorys.mapValues { it.value.toSerializable() },
    labelOffsetsAidPoints = labelOffsetsAidPoints.mapValues { it.value.toSerializable() },
    labelOffsetsAOPoints = labelOffsetsAOPoints.mapValues { it.value.toSerializable() },
    labelOffsetsAOLines = labelOffsetsAOLine.mapValues { it.value.toSerializable() },
    labelOffsetsAxoLines = labelOffsetsAxoLines.mapValues { it.value.toSerializable() },
    labelOffsetsSegmentsPudorys = labelOffsetsSegmentsPudorys.mapValues { it.value.toSerializable() },
    labelOffsetsSegmentsNarys = labelOffsetsSegmentsNarys.mapValues { it.value.toSerializable() },
    labelOffsetsSegmentsBokorys = labelOffsetsSegmentsBokorys.mapValues { it.value.toSerializable() },
    labelOffsetsSegmentsAxo = labelOffsetsSegmentsAxo.mapValues { it.value.toSerializable() },
    labelOffsetsHelpSegmentsPudorys = labelOffsetsHelpSegmentsPudorys.mapValues { it.value.toSerializable() },
    labelOffsetsHelpSegmentsNarys = labelOffsetsHelpSegmentsNarys.mapValues { it.value.toSerializable() },
    labelOffsetsAOSegments = labelOffsetsAOSegments.mapValues { it.value.toSerializable() },
    axoModel = axoModel.toSerializable(),










    )
// SerializableMongeSnapshot → MongeSnapshot
fun SerializableMongeSnapshot.toRuntime(): MongeSnapshot {


    // 1) Nejdřív 3D „rodiče“ + mapy
    val points3D = sharedPoints3D.map { it.toRuntime() }
    val points3DById = points3D.associateBy { it.id }

    val lines3D = lines3D.map { it.toRuntime() }
    val lines3DById = lines3D.associateBy { it.id }

    val segments3D = segments3D.map { it.toRuntime() }
    val segments3DById = segments3D.associateBy { it.id }
    val segmentSolids3D = segmentSolids3D.map { it.toRuntime() }

    val conics3D = conic3D.map { it.toRuntime() }
    conics3D.associateBy { it.id }

    val spheres3D = spheres.map { it.toRuntime() }
    val polygons3D = polygons.map { it.toRuntime() }
    val planePolygons2DRt = planePolygons2D.map { it.toRuntime() }
    val solidsOfRevolution = solidsOfRevolutionNarys.map{it.toRuntime()}
    val solidsOfRevolutionPudorys = solidsOfRevolutionPudorys.map{it.toRuntime()}


    // 2) Projekce bodů (potřebují points3DById)
    val pointsPudorysRt = pointsPudorys.map { it.toRuntime(points3DById) }
    val pointsNarysRt   = pointsNarys  .map { it.toRuntime(points3DById) }
    val pointsBokorysRt   = pointsBokorys  .map { it.toRuntime(points3DById) }
    val pointsAxoRt   = pointsAxo.map{it.toRuntime(points3DById)}
    val pointsPudorysById = pointsPudorysRt.associateBy { it.id }
    val pointsNarysById   = pointsNarysRt  .associateBy { it.id }
    val pointsBokorysById=pointsBokorysRt.associateBy { it.id }
    val pointsAxoById=pointsAxoRt.associateBy { it.id }

    // 3) Projekce linií (často potřebují lines3DById)
    val linesPudorysRt = linesPudorys.map { it.toRuntime(lines3DById) }
    val linesNarysRt   = linesNarys  .map { it.toRuntime(lines3DById) }
    val linesBokorysRt = linesBokorys.map { it.toRuntime(lines3DById) }
    val linesAxoRt = linesAxo.map { it.toRuntime(lines3DById) }


    // 4) Úsečky 2D (typicky potřebují body 2D a segments3DById)
    val segmentsPudorysRt = segmentsPudorys.mapNotNull { it.toRuntime(pointsPudorysById, segments3DById) }
    val segmentsNarysRt   = segmentsNarys  .mapNotNull { it.toRuntime(pointsNarysById,   segments3DById) }
    val segmentsBokorysRt=segmentsBokorys.mapNotNull { it.toRuntime(pointsBokorysById,segments3DById) }
    val segmentsAxoRt=segmentsAxo.mapNotNull { it.toRuntime(pointsAxoById,segments3DById) }

    // 5) Pomocné čáry/úsečky (pokud mají parenty, předávej potřebné mapy)
    val helpLinePudorysRt   = helpLinePudorys.map { it.toRuntime() }
    val helpLineNarysRt     = helpLineNarys.map { it.toRuntime() }
    val helpSegmentPudorysRt= helpSegmentPudorys.map { it.toRuntime(segments3DById) }
    val helpSegmentNarysRt  = helpSegmentNarys  .map { it.toRuntime(segments3DById) }

    // 6) Stopy rovin (pokud se vážou na planeId, můžeš jim tady *ne*napojovat parent – to řeš v applySnapshot)
    val planeTracePudorysRt = planeTracePudorys.map { it.toRuntime() }
    val planeTraceNarysRt   = planeTraceNarys  .map { it.toRuntime() }
    val planeTraceBokorysRt = planeTraceBokorys.map { it.toRuntime() }
    val realPlaneTracePudorysRt = planeTracePudorysRt.filterNot { it.isVirtual }
    val realPlaneTraceNarysRt = planeTraceNarysRt.filterNot { it.isVirtual }
    val realPlaneTraceBokorysRt = planeTraceBokorysRt.filterNot { it.isVirtual }
    val pudByPid = realPlaneTracePudorysRt.associateBy { it.parentId }
    val narByPid = realPlaneTraceNarysRt.associateBy { it.parentId }
    val bokByPid = realPlaneTraceBokorysRt.associateBy { it.parentId }
    val pudById = realPlaneTracePudorysRt.associateBy { it.id }
    val narById = realPlaneTraceNarysRt.associateBy { it.id }
    val bokById = realPlaneTraceBokorysRt.associateBy { it.id }
    val axoModelRt = axoModel.toRuntime()
    val planes3DRt = mutableListOf<Plane3D>()
    for (meta in planes3D) {
        val pid = meta.id
        val traces = meta.equation?.let { tracesFromPlaneEquation(it) }

        val pud = pudByPid[pid] ?: pudById[meta.tracePudorysId]
            ?: traces?.pudorys?.copy(parentId = pid, isVirtual = true)
            ?: PlaneTracePudorys(
                point = Point3DPudorys(0f, 0f, name = "", parent = Point3D(0f, 0f, 0f, "")),
                direction = Offset(1f, 0f),
                parentId = pid,
                id = "${pid}-pudorys-virtual",
                isVirtual = true
            )

        val nar = narByPid[pid] ?: narById[meta.traceNarysId]
            ?: traces?.narys?.copy(parentId = pid, isVirtual = true)
            ?: PlaneTraceNarys(
                point = Point3DNarys(0f, 0f, name = "", parent = Point3D(0f, 0f, 0f, "")),
                direction = Offset(1f, 0f),
                parentId = pid,
                id = "${pid}-narys-virtual",
                isVirtual = true
            )

        val bok = bokByPid[pid] ?: meta.traceBokorysId?.let(bokById::get)
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

        val plane = Plane3D(
            tracePudorys = pud,
            traceNarys = nar,
            traceBokorys = bok,
            name = meta.name,
            equation = meta.equation,
            color = meta.color.toColor(),
            lineStyle = meta.lineStyle.toLineStyle(),
            strokeWidth = meta.strokeWidth,
            id = pid,
            creationIndex = meta.creationIndex
        )
        pud.parent = plane
        nar.parent = plane
        bok.parent = plane
        planes3DRt += plane
    }



    // 7) Oblouky/kružnice v P/N
    val arcsPudorysRt = arcspudorys.map { it.toRuntime() }
    val arcsNarysRt   = arcsnarys  .map { it.toRuntime() }
    val arcsBokorysRt = arcsbokorys.map { it.toRuntime() }
    val arcsAxoOverlayRt = arcsAxoOverlay.map { it.toRuntime() }

    val circlesPudorysRt = circlesPudorys.map { it.toRuntime() }
    val circlesNarysRt   = circlesNarys  .map { it.toRuntime() }
    val circlesBokorysRt= circlesBokorys.map { it.toRuntime() }

    val conicsPudorysRt = conicsPudorys.map { it.toRuntime() }
    val conicsNarysRt   = conicsNarys  .map { it.toRuntime() }
    val conicsBokorysRt = conicsBokorys.map { it.toRuntime() }
    val conicsAxoRt = conicsAxo.map { it.toRuntime() }

    // 8) Vstupní body pro konstrukce
    val conicInputPointsPudorysRt = conicInputPointsPudorys.mapValues { (_, t) ->
        Triple(t.first.toOffset(), t.second.toOffset(), t.third.toOffset())
    }
    val conicInputPointsNarysRt = conicInputPointsNarys.mapValues { (_, t) ->
        Triple(t.first.toOffset(), t.second.toOffset(), t.third.toOffset())
    }
    val conicInputPointsBokorysRt = conicInputPointsBokorys.mapValues { (_, t) ->
        Triple(t.first.toOffset(), t.second.toOffset(), t.third.toOffset())
    }
    val conicInputPointsAxoRt = conicInputPointsAxo.mapValues { (_, t) ->
        Triple(t.first.toOffset(),t.second.toOffset(),t.third.toOffset())
    }

    // 9) Elipsa/Parabola/Hyperbola – mapy
    val ellipseArcParams3DRt = ellipseArcParams3D
    val ellipseArcEnds3DRt = ellipseArcEnds3D.mapValues { Pair(it.value.first.toOffset3D(), it.value.second.toOffset3D()) }
    val ellipseArcEndsRt = ellipseArcEnds.mapValues { Pair(it.value.first.toOffset(), it.value.second.toOffset()) }
    val conicSegmentsRt = conicSegments.mapValues { it.value.toRuntimeSegmentation() }

    val parabolaArcParams3DRt = parabolaArcParams3D
    val parabolaArcEnds3DRt   = parabolaArcEnds3D  .mapValues { Pair(it.value.first.toOffset3D(), it.value.second.toOffset3D()) }
    val parabolaArcEndsRt = parabolaArcEnds.mapValues { Pair(it.value.first.toOffset(), it.value.second.toOffset()) }

    val hyperbolaArcParams3DRt = hyperbolaArcParams3D.mapValues { (_, v) ->
        Pair(v.first?.let { Pair(it.first, it.second) }, v.second?.let { Pair(it.first, it.second) })
    }
    val hyperbolaArcEnds3DRt = hyperbolaArcEnds3D.mapValues { (_, v) ->
        Pair(
            v.first ?.let { Pair(it.first.toOffset3D(), it.second.toOffset3D()) },
            v.second?.let { Pair(it.first.toOffset3D(), it.second.toOffset3D()) }
        )
    }
    val hyperbolaArcBranch1Rt = hyperbolaArcBranch1.mapValues { Pair(it.value.first.toOffset(), it.value.second.toOffset()) }
    val hyperbolaArcBranch2Rt = hyperbolaArcBranch2.mapValues { Pair(it.value.first.toOffset(), it.value.second.toOffset()) }

    val hyperbolaInputsPudorysRt = hyperbolaInputsPudorys.mapValues { it.value.toHyperbolaInput()} // nebo toHyperbolaInput(), dle tvého API
    val hyperbolaInputsNarysRt   = hyperbolaInputsNarys  .mapValues { it.value.toHyperbolaInput() }
    val hyperbolaInputsBokorysRt = hyperbolaInputsBokorys.mapValues { it.value.toHyperbolaInput() }
    val hyperbolaInputsAxoRt = hyperbolaInputsAxo.mapValues { it.value.toHyperbolaInput() }

    // 10) Plochy
    val conicalSurfaceRt   = conicalSurface  .map { it.toRuntime() }
    val cylindricalSurfaceRt = cylinderSurface.map { it.toRuntime() }
    val ruledSurfacesRt = ruledSurfaces.map { it.toRuntime() }
    val curvesPudorys = curvesPudorys.map {it.toRuntime()}
    val curvesNarys = curvesNarys.map {it.toRuntime()}
    val curves3D = curves3D.map {it.toRuntime()}
    val curvesBokorys = curvesBokorys.map { it.toRuntime() }
    val curvesAxo = curvesAxo.map { it.toRuntime() }
    val intersectionGroupsRt = intersectionGroups.map { group ->
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
    // 11) Aid points
    val aidPointsLogicalRt = aidPointsLogical.map { it.toRuntime() }
    val axoOverlayPointRt = axoOverlayPoint.map { it.toRuntime() }
    val axoOverlayLineRt = axoOverlayLine.map { it.toRuntime() }
    val axoOverlaySegmentRt: List<AxoOverlaySegment>   = axoOverlaySegment.map { it.toRuntime() }

    val labelOffsetsNarysRt = labelOffsetsNarys.mapValues { it.value.toOffset() }
    val labelOffsetsPudorysRt = labelOffsetsPudorys.mapValues { it.value.toOffset() }
    val labelOffsetsBokorysRt = labelOffsetsBokorys.mapValues { it.value.toOffset() }
    val labelOffsetsHelpNarysRt = labelOffsetsHelpNarys.mapValues { it.value.toOffset() }
    val labelOffsetsHelpPudorysRt = labelOffsetsHelpPudorys.mapValues { it.value.toOffset() }
    val labelOffsetsPointsNarysRt = labelOffsetsPointsNarys.mapValues { it.value.toOffset() }
    val labelOffsetsPointsPudorysRt = labelOffsetsPointsPudorys.mapValues { it.value.toOffset() }
    val labelOffsetsPointsBokorysRt = labelOffsetsPointsBokorys.mapValues { it.value.toOffset() }
    val labelOffsetsPointsAxoRt = labelOffsetsPointsAxo.mapValues { it.value.toOffset() }
    val labelOffsetsTracePudorysRt = labelOffsetsTracePudorys.mapValues { it.value.toOffset() }
    val labelOffsetsTraceNarysRt = labelOffsetsTraceNarys.mapValues { it.value.toOffset() }
    val labelOffsetsTraceBokorysRt = labelOffsetsTraceBokorys.mapValues { it.value.toOffset() }
    val labelOffsetsAidPointsRt = labelOffsetsAidPoints.mapValues { it.value.toOffset() }
    val labelOffsetsAOPointsRt = labelOffsetsAOPoints.mapValues { it.value.toOffset() }
    val labelOffsetsAOLineRt = labelOffsetsAOLines.mapValues { it.value.toOffset() }
    val labelOffsetsAxoLinesRt = labelOffsetsAxoLines.mapValues { it.value.toOffset() }
    val labelOffsetsSegmentsPudorysRt = labelOffsetsSegmentsPudorys.mapValues { it.value.toOffset() }
    val labelOffsetsSegmentsNarysRt = labelOffsetsSegmentsNarys.mapValues { it.value.toOffset() }
    val labelOffsetsSegmentsBokorysRt = labelOffsetsSegmentsBokorys.mapValues { it.value.toOffset() }
    val labelOffsetsSegmentsAxoRt = labelOffsetsSegmentsAxo.mapValues { it.value.toOffset() }
    val labelOffsetsHelpSegmentsPudorysRt = labelOffsetsHelpSegmentsPudorys.mapValues { it.value.toOffset() }
    val labelOffsetsHelpSegmentsNarysRt = labelOffsetsHelpSegmentsNarys.mapValues { it.value.toOffset() }
    val labelOffsetsAOSegmentsRt = labelOffsetsAOSegments.mapValues { it.value.toOffset() }


    // 12) Finální návrat – jen data, žádné parent napojování (to patří do applySnapshot)
    return MongeSnapshot(
        circleArcsPudorys = circleArcsPudorys.map { it.toRuntime() },
        circleArcsNarys = circleArcsNarys.map { it.toRuntime() },
        pointsPudorys = pointsPudorysRt,
        pointsNarys   = pointsNarysRt,
        pointsBokorys   = pointsBokorysRt,
        pointsAxo       = pointsAxoRt,
        linesPudorys  = linesPudorysRt,
        linesNarys    = linesNarysRt,
        linesBokorys = linesBokorysRt,
        linesAxo = linesAxoRt,
        segmentsPudorys = segmentsPudorysRt,
        segmentsNarys   = segmentsNarysRt,
        segmentsBokorys = segmentsBokorysRt,
        segmentsAxo = segmentsAxoRt,
        sharedPoints3D  = points3D,
        lines3D         = lines3D,
        segments3D      = segments3D,
        segmentSolids3D = segmentSolids3D,
        planes3D        = planes3DRt,
        planeTracePudorys = realPlaneTracePudorysRt,
        planeTraceNarys   = realPlaneTraceNarysRt,
        planeTraceBokorys = realPlaneTraceBokorysRt,
        helpLinePudorys   = helpLinePudorysRt,
        helpLineNarys     = helpLineNarysRt,
        helpSegmentNarys   = helpSegmentNarysRt,
        helpSegmentPudorys = helpSegmentPudorysRt,
        arcspudorys = arcsPudorysRt,
        arcsnarys   = arcsNarysRt,
        arcsbokorys = arcsBokorysRt,
        arcsAxoOverlay = arcsAxoOverlayRt,
        circlesPudorys = circlesPudorysRt,
        circlesNarys   = circlesNarysRt,
        circlesBokorys   = circlesBokorysRt,
        aidPointsLogical = aidPointsLogicalRt,
        conicsPudorys = conicsPudorysRt,
        conicsNarys   = conicsNarysRt,
        conicsBokorys = conicsBokorysRt,
        conicsAxo = conicsAxoRt,
        conicInputPointsPudorys = conicInputPointsPudorysRt,
        conicInputPointsNarys   = conicInputPointsNarysRt,
        conicInputPointsBokorys = conicInputPointsBokorysRt,
        conicInputPointsAxo = conicInputPointsAxoRt,
        conic3D         = conics3D,
        conicalSurface  = conicalSurfaceRt,
        cylinderSurface = cylindricalSurfaceRt,
        ruledSurfaces = ruledSurfacesRt,
        curvesPudorys = curvesPudorys,
        curvesNarys = curvesNarys,
        curves3D = curves3D,
        curvesBokorys = curvesBokorys,
        curvesAxo = curvesAxo,
        intersectionGroups = intersectionGroupsRt,
        // elipsa/parabola/hyperbola
        ellipseArcParams3D = ellipseArcParams3DRt,
        ellipseArcEnds3D   = ellipseArcEnds3DRt,
        ellipseArcEnds = ellipseArcEndsRt,
        ellipseArcMode = ellipseArcMode,
        conicSegments = conicSegmentsRt,
        parabolaArcParams3D   = parabolaArcParams3DRt,
        parabolaArcEnds3D     = parabolaArcEnds3DRt,
        parabolaArcEnds = parabolaArcEndsRt,
        hyperbolaArcParams3D   = hyperbolaArcParams3DRt,
        hyperbolaArcEnds3D     = hyperbolaArcEnds3DRt,
        hyperbolaArcBranch1 = hyperbolaArcBranch1Rt,
        hyperbolaArcBranch2 = hyperbolaArcBranch2Rt,
        hyperbolaInputsPudorys = hyperbolaInputsPudorysRt,
        hyperbolaInputsNarys   = hyperbolaInputsNarysRt,
        hyperbolaInputsBokorys = hyperbolaInputsBokorysRt,
        hyperbolaInputsAxo = hyperbolaInputsAxoRt,
        spheres  = spheres3D,
        polygons = polygons3D,
        planePolygons2D = planePolygons2DRt,
        labelOffsetsNarys = labelOffsetsNarysRt,
        labelOffsetsPudorys = labelOffsetsPudorysRt,
        labelOffsetsBokorys = labelOffsetsBokorysRt,
        labelOffsetsHelpNarys = labelOffsetsHelpNarysRt,
        labelOffsetsHelpPudorys = labelOffsetsHelpPudorysRt,
        labelOffsetsAidPoints =labelOffsetsAidPointsRt,
        labelOffsetsAOPoints = labelOffsetsAOPointsRt,
        labelOffsetsPointsPudorys = labelOffsetsPointsPudorysRt,
        labelOffsetsPointsNarys = labelOffsetsPointsNarysRt,
        labelOffsetsPointsBokorys = labelOffsetsPointsBokorysRt,
        labelOffsetsPointsAxo = labelOffsetsPointsAxoRt,
        labelOffsetsTracePudorys = labelOffsetsTracePudorysRt,
        labelOffsetsTraceNarys = labelOffsetsTraceNarysRt,
        labelOffsetsTraceBokorys = labelOffsetsTraceBokorysRt,
        solidsOfRevolutionsNarys = solidsOfRevolution,
        solidsOfRevolutionsPudorys = solidsOfRevolutionPudorys,
        axoModel =axoModelRt,
        axoOverlayPoint =axoOverlayPointRt,
        axoOverlayLine =axoOverlayLineRt,
        axoOverlaySegment = axoOverlaySegmentRt,
        labelOffsetsAOLine =labelOffsetsAOLineRt,
        labelOffsetsAxoLines = labelOffsetsAxoLinesRt,
        labelOffsetsSegmentsPudorys = labelOffsetsSegmentsPudorysRt,
        labelOffsetsSegmentsNarys = labelOffsetsSegmentsNarysRt,
        labelOffsetsSegmentsBokorys = labelOffsetsSegmentsBokorysRt,
        labelOffsetsSegmentsAxo = labelOffsetsSegmentsAxoRt,
        labelOffsetsHelpSegmentsPudorys = labelOffsetsHelpSegmentsPudorysRt,
        labelOffsetsHelpSegmentsNarys = labelOffsetsHelpSegmentsNarysRt,
        labelOffsetsAOSegments = labelOffsetsAOSegmentsRt,





        )
}

// History.kt
data class HistoryState(
    val snapshots: MutableList<MongeSnapshot> = mutableListOf(),
    var cursor: Int = -1
)

const val MAX_HISTORY = 500

fun canUndo(h: HistoryState) = h.cursor >0

/**
 * Verze pro UI: kromě samotné historie přečte i [MongeState.historyVersion],
 * takže se Compose doví, že má tlačítko překreslit.
 */
fun MongeState.canUndoNow(): Boolean {
    historyVersion
    return canUndo(history)
}

fun MongeState.canRedoNow(): Boolean {
    historyVersion
    return canRedo(history)
}
fun canRedo(h: HistoryState) = h.cursor < h.snapshots.lastIndex
fun pushSnapshot(h: HistoryState, s: MongeSnapshot) {

    // dedup proti CURRENT (cursor) NEJDŘÍV – no-op commit nesmí zahodit redo větev.
    // (Dřív se nejprve ořízla budoucnost a teprve pak se zjistilo, že jde o duplikát,
    //  takže se redo historie ztratila bez vytvoření nové větve.)
    val current = h.snapshots.getOrNull(h.cursor)
    if (current != null && current.normalizedForHistory() == s.normalizedForHistory()) return

    // teprve teď odřízni budoucnost (skutečná nová větev)
    if (h.cursor < h.snapshots.lastIndex) {
        h.snapshots.subList(h.cursor + 1, h.snapshots.size).clear()
    }

    h.snapshots.add(s)
    h.cursor = h.snapshots.lastIndex

    if (h.snapshots.size > MAX_HISTORY) {
        val drop = h.snapshots.size - MAX_HISTORY
        repeat(drop) { h.snapshots.removeAt(0) }
        h.cursor = h.snapshots.lastIndex
    }
}

fun undo(history: HistoryState): MongeSnapshot? {
    if (!canUndo(history)) return null
    history.cursor--
    return history.snapshots[history.cursor]
}

fun redo(history: HistoryState): MongeSnapshot? {
    if (!canRedo(history)) return null
    history.cursor++
    return history.snapshots[history.cursor]
}
fun ensureCurrentSaved(state: MongeState) {
    if (state.isRestoringFromHistory) return
    val h = state.history
    if (h.cursor != h.snapshots.lastIndex) return
    if (state.sceneVersion == state.syncedSceneVersion) return
    val now = state.exportSnapshot()
    val current = h.snapshots.getOrNull(h.cursor)
    if (current == null || current.normalizedForHistory() != now.normalizedForHistory()) {
        pushSnapshot(h, now)
    }
}

fun MongeState.exportSnapshot(): MongeSnapshot = createSnapshot(this)
fun applySnapshot(state: MongeState, snap: MongeSnapshot) {
    state.isRestoringFromHistory = true
    try {
        restoreSnapshot(state, snap)
        relinkPlaneTracesToPlanes(state)
        sanitizePlaneTraceGeometry(state)
        relinkConics2DTo3D(state)
        relinkSegments2DTo3D(state)
        sanitizeDanglingParents(state)
        repairDirectrixOfSurfaceIds(state)

    } finally {
        state.isRestoringFromHistory = false
        state.syncedSceneVersion = state.sceneVersion
    }
}


fun MongeState.initHistory() {
    history = HistoryState()
    pushSnapshot(history, exportSnapshot())
    historyVersion++
    syncedSceneVersion = sceneVersion
    isDirty = false
}

@Serializable
data class MongeHistoryDto(
    val version: Int = 1,
    val cursor: Int,
    val snapshots: List<SerializableMongeSnapshot>
)

fun HistoryState.toDto(): MongeHistoryDto = MongeHistoryDto(
    cursor = cursor,
    snapshots = snapshots.map { it.toSerializable() }
)

fun MongeHistoryDto.toHistoryState(): HistoryState {
    val snaps = snapshots.map { it.toRuntime() }.toMutableList()
    return HistoryState(
        snapshots = snaps,
        cursor = cursor.coerceIn(-1, snaps.lastIndex)
    )
}
@Serializable
data class MongeFileV2(
    val version: Int = 3,
    val state: SerializedMongeState,
    val history: MongeHistoryDto? = null   // null = uložen bez historie
)
private fun relinkPlaneTracesToPlanes(state: MongeState) {
    fun pidOf(t: Trace2DProjection): String? = when (t) {
        is PlaneTracePudorys -> t.parentId ?: t.parent?.id
        is PlaneTraceNarys -> t.parentId ?: t.parent?.id
        is PlaneTraceBokorys -> t.parentId ?: t.parent?.id
    }

    val realPudorys = state.lineTracesPudorys.filterNot { it.isVirtual }
    val realNarys = state.lineTracesNarys.filterNot { it.isVirtual }
    val realBokorys = state.lineTracesBokorys.filterNot { it.isVirtual }
    val pudByPid = realPudorys.associateBy { pidOf(it) }
    val narByPid = realNarys.associateBy { pidOf(it) }
    val bokByPid = realBokorys.associateBy { pidOf(it) }
    val pudById = realPudorys.associateBy { it.id }
    val narById = realNarys.associateBy { it.id }
    val bokById = realBokorys.associateBy { it.id }

    val relinkedPlanes = state.planes3D.map { plane ->
        val pud = (pudByPid[plane.id] ?: pudById[plane.tracePudorys.id] ?: plane.tracePudorys)
            .copy(parent = null, parentId = plane.id)
        val nar = (narByPid[plane.id] ?: narById[plane.traceNarys.id] ?: plane.traceNarys)
            .copy(parent = null, parentId = plane.id)
        val bok = (bokByPid[plane.id] ?: bokById[plane.traceBokorys.id] ?: plane.traceBokorys)
            .copy(parent = null, parentId = plane.id)

        plane.copy(
            tracePudorys = pud,
            traceNarys = nar,
            traceBokorys = bok
        ).also { rebuilt ->
            rebuilt.tracePudorys.parent = rebuilt
            rebuilt.traceNarys.parent = rebuilt
            rebuilt.traceBokorys.parent = rebuilt
        }
    }

    val planesById = relinkedPlanes.associateBy { it.id }
    state.planes3D.setAll(relinkedPlanes)

    val planePudIds = relinkedPlanes.mapTo(mutableSetOf()) { it.tracePudorys.id }
    val planeNarIds = relinkedPlanes.mapTo(mutableSetOf()) { it.traceNarys.id }
    val planeBokIds = relinkedPlanes.mapTo(mutableSetOf()) { it.traceBokorys.id }

    state.lineTracesPudorys.setAll(
        relinkedPlanes.mapNotNull { it.tracePudorys.takeUnless { trace -> trace.isVirtual } } +
                realPudorys.filter { it.id !in planePudIds }.map { t ->
                    val pid = pidOf(t)
                    t.copy(parent = pid?.let(planesById::get), parentId = pid)
                }
    )
    state.lineTracesNarys.setAll(
        relinkedPlanes.mapNotNull { it.traceNarys.takeUnless { trace -> trace.isVirtual } } +
                realNarys.filter { it.id !in planeNarIds }.map { t ->
                    val pid = pidOf(t)
                    t.copy(parent = pid?.let(planesById::get), parentId = pid)
                }
    )
    state.lineTracesBokorys.setAll(
        relinkedPlanes.mapNotNull { it.traceBokorys.takeUnless { trace -> trace.isVirtual } } +
                realBokorys.filter { it.id !in planeBokIds }.map { t ->
                    val pid = pidOf(t)
                    t.copy(parent = pid?.let(planesById::get), parentId = pid)
                }
    )
}

fun relinkSegments2DTo3D(state: MongeState) {
    val seg3DById = state.segments3D.associateBy { it.id }

    state.segmentsPudorys.setAll(
        state.segmentsPudorys.map { s ->
            val pid = s.parentId ?: s.parent?.id
            val p = pid?.let(seg3DById::get)
            s.copy(parent = p, parentId = pid)
        }
    )
    state.segmentsNarys.setAll(
        state.segmentsNarys.map { s ->
            val pid = s.parentId ?: s.parent?.id
            val p = pid?.let(seg3DById::get)
            s.copy(parent = p, parentId = pid)
        }
    )
    state.segmentsBokorys.setAll(
        state.segmentsBokorys.map { s ->
            val pid = s.parentId ?: s.parent?.id
            val p = pid?.let(seg3DById::get)
            s.copy(parent = p, parentId = pid)
        }
    )
    state.segmentsAxo.setAll(
        state.segmentsAxo.map { s ->
            val pid = s.parentId ?: s.parent?.id
            val p = pid?.let(seg3DById::get)
            s.copy(parent = p, parentId = pid)
        }
    )
}
fun sanitizeDanglingParents(state: MongeState) {
    val existing3D = state.lines3D.map { it.id }.toHashSet()

    fun fixLineParent(p: Any) {
        when (p) {
            is Line3DProjectionPudorys -> {
                val pid = p.parent?.id
                if (pid != null && pid !in existing3D) {
                    p.parent = null
                    p.parentId = null
                }
            }
            is Line3DProjectionNarys -> {
                val pid = p.parent?.id
                if (pid != null && pid !in existing3D) {
                    p.parent = null
                    p.parentId = null
                }
            }
            is Line3DProjectionBokorys -> {
                val pid = p.parent?.id
                if (pid != null && pid !in existing3D) {
                    p.parent = null
                    p.parentId = null
                }
            }
        }
    }
    fun fixPointParents(p: Any){
        when (p) {
            is Point3DPudorys -> {
                val pid = p.pendingParentLineId
                if (pid != null && pid !in existing3D) {
                    p.parent = null
                    p.pendingParentLineId = null
                }
            }
            is Point3DNarys -> {
                val pid = p.pendingParentLineId
                if (pid != null && pid !in existing3D) {
                    p.parent = null
                    p.pendingParentLineId = null
                }
            }
            is Point3DBokorys -> {
                val pid = p.pendingParentLineId
                if (pid != null && pid !in existing3D) {
                    p.parent = null
                    p.pendingParentLineId = null
                }
            }
        }
    }
    state.lines3DPudorys.forEach(::fixLineParent)
    state.lines3DBokorys.forEach(::fixPointParents)
    state.lines3DNarys.forEach(::fixLineParent)
    state.pointsPudorys.forEach(::fixPointParents)
    state.pointsNarys.forEach(::fixPointParents)
    state.pointsBokorys.forEach(::fixPointParents)


}
private fun MongeSnapshot.normalizedForHistory(): MongeSnapshot = copy(
    labelOffsetsNarys = emptyMap(),
    labelOffsetsPudorys = emptyMap(),
    labelOffsetsBokorys = emptyMap(),
    labelOffsetsHelpNarys = emptyMap(),
    labelOffsetsHelpPudorys = emptyMap(),
    labelOffsetsPointsNarys = emptyMap(),
    labelOffsetsPointsPudorys = emptyMap(),
    labelOffsetsPointsBokorys = emptyMap(),
    labelOffsetsPointsAxo = emptyMap(),
    labelOffsetsTracePudorys = emptyMap(),
    labelOffsetsTraceNarys = emptyMap(),
    labelOffsetsTraceBokorys = emptyMap(),
    labelOffsetsAidPoints = emptyMap(),
    labelOffsetsAOPoints = emptyMap(),
    labelOffsetsAOLine = emptyMap(),
    labelOffsetsAxoLines = emptyMap(),
    labelOffsetsSegmentsPudorys = emptyMap(),
    labelOffsetsSegmentsNarys = emptyMap(),
    labelOffsetsSegmentsBokorys = emptyMap(),
    labelOffsetsSegmentsAxo = emptyMap(),
    labelOffsetsHelpSegmentsPudorys = emptyMap(),
    labelOffsetsHelpSegmentsNarys = emptyMap(),
    labelOffsetsAOSegments = emptyMap(),
)
