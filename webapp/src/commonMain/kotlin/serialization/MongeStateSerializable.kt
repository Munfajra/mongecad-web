package serialization

import utils.System
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import serialization.classes.*

@Serializable
data class SerializedMongeState(
    val points3D: List<SerializablePoint3D>,
    val lines3D: List<SerializableLine3D>,
    val planes3D: List<SerializablePlane3D>,
    val segments3D: List<SerializableSegment3D>,
    val segmentSolids3D: List<SerializableSegmentSolid3D> = emptyList(),
    val axoOverlayPoint: List<SerializableAxoOverlayPoint>,
    val axoOverlayLine: List<SerializableAOLine>,
    val axoOverlaySegment: List<SerializableAOSegment>,

    val pointsPudorys: List<SerializablePoint3DPudorys>,
    val pointsNarys: List<SerializablePoint3DNarys>,
    val pointsBokorys: List<SerializablePoint3DBokorys>,
    val pointsAxo: List<SerializablePoint3DAxo>,
    val aidPoints: List<SerializableAidPointLogical>,

    val lines3DPudorys: List<SerializableLine3DProjectionPudorys>,
    val lines3DNarys: List<SerializableLine3DProjectionNarys>,
    val lines3DBokorys: List<SerializableLine3DProjectionBokorys>,
    val lines3DAxo: List<SerializableLine3DProjectionAxo>,

    val segmentsPudorys: List<SerializableSegment2DPudorys>,
    val segmentsNarys: List<SerializableSegment2DNarys>,
    val segmentsBokorys: List<SerializableSegment2DBokorys>,
    val segmentsAxo: List<SerializableSegment2DAxo>,

    val planeTracesPudorys: List<SerializablePlaneTracePudorys>,
    val planeTracesNarys: List<SerializablePlaneTraceNarys>,
    val planeTracesBokorys: List<SerializablePlaneTraceBokorys>,

    val helpLinesPudorys: List<SerializableHelpLinePudorys>,
    val helpLinesNarys: List<SerializableHelpLineNarys>,

    val helpSegmentsPudorys: List<SerializableHelpSegmentPudorys>,
    val helpSegmentsNarys: List<SerializableHelpSegmentNarys>,

    val arcsNarys: List<SerializableArc2DNarys>,
    val arcsPudorys: List<SerializableArc2DPudorys>,
    val arcsBokorys: List<SerializableArc2DBokorys> = emptyList(),
    val arcsAxoOverlay: List<SerializableArcAxoOverlay> = emptyList(),

    val circlesPudorys: List<SerializedConicSectionPudorys>,
    val circlesNarys: List<SerializedConicSectionNarys>,
    val circlesBokorys: List<SerializedConicSectionBokorys>,
    val circleArcsPudorys: List<SerializableCircleArcPudorys>,
    val circleArcsNarys: List<SerializableCircleArcNarys>,
    val conicSectionPudorys: List<SerializedConicSectionPudorys>,
    val conicSectionNarys: List<SerializedConicSectionNarys>,
    val conicSectionBokorys: List<SerializedConicSectionBokorys>,
    val conicSectionAxo: List<SerializedConicSectionAxo>,
    val conics3D: List <SerializableConicSection3D>,
    val ellipseArcsPudorys: List<SerializedEllipseArcPudorys> = emptyList(),
    val parabolaArcsPudorys: List<SerializedParabolaArcPudorys> = emptyList(),
    val ellipseArcsNarys: List<SerializedEllipseArcNarys> = emptyList(),
    val parabolaArcsNarys: List<SerializedParabolaArcNarys> = emptyList(),
    val hyperbolaBranch1Pudorys: List<SerializedHyperbolaBranchPudorys> = emptyList(),
    val hyperbolaBranch2Pudorys: List<SerializedHyperbolaBranchPudorys> = emptyList(),
    val hyperbolaBranch1Narys:   List<SerializedHyperbolaBranchNarys>   = emptyList(),
    val hyperbolaBranch2Narys:   List<SerializedHyperbolaBranchNarys>   = emptyList(),
    val ellipseArcsBokorys: List<SerializedEllipseArc2D> = emptyList(),
    val parabolaArcsBokorys: List<SerializedParabolaArc2D> = emptyList(),
    val hyperbolaBranch1Bokorys: List<SerializedHyperbolaBranch2D> = emptyList(),
    val hyperbolaBranch2Bokorys: List<SerializedHyperbolaBranch2D> = emptyList(),
    val ellipseArcsAxo: List<SerializedEllipseArc2D> = emptyList(),
    val parabolaArcsAxo: List<SerializedParabolaArc2D> = emptyList(),
    val hyperbolaBranch1Axo: List<SerializedHyperbolaBranch2D> = emptyList(),
    val hyperbolaBranch2Axo: List<SerializedHyperbolaBranch2D> = emptyList(),
    val ellipseArcs3D: List<SerializedEllipseArc3D> = emptyList(),
    val parabolaArcs3D: List<SerializedParabolaArc3D> = emptyList(),
    val hyperbolaArcs3D: List<SerializedHyperbolaArc3D> = emptyList(),
    val conicSegmentations: List<serialization.classes.SerializedConicSegmentation> = emptyList(),
    val conicalSurfaces: List<SerializableConicalSurface3D> = emptyList(),
    val cylindricalSurfaces: List<SerializableCylindricalSurface3D> =emptyList(),
    val ruledSurfaces: List<SerializableRuledSurface3D> = emptyList(),
    val spheres3D: List<SphereSurface3DSerializable>,
    val polygons3D: List<SerializableRegularPolygon3D> = emptyList(),
    val planePolygons2D: List<SerializablePlanePolygon2D> = emptyList(),
    val projectionMode: ProjectionMode = ProjectionMode.MONGE,
    val xAxisDirection: XAxisDirection = XAxisDirection.POSITIVE_RIGHT,
    val yAxisDirectionPlane: YAxisDirectionPlane = YAxisDirectionPlane.POSITIVE_DOWN,
    val axoModel: SerializableAxoModel,
    val paperAnchorLogical: SerializableOffset? = SerializableOffset(0f,0f),
    val paperAnchorPinned: Boolean? = false,
    val showPaperPreview: Boolean? = false,
    val labelOffsetsNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsPudorys: Map<String,  SerializableOffset> = emptyMap(),
    val labelOffsetsBokorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsHelpNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsHelpPudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsPointsNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsPointsPudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsPointsBokorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsPointsAxo: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsTraceBokorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsTracePudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsTraceNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAidPoints: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAOPoints: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAOLines: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAxoLines: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsSegmentsPudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsSegmentsNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsSegmentsBokorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsSegmentsAxo: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsHelpSegmentsPudorys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsHelpSegmentsNarys: Map<String, SerializableOffset> = emptyMap(),
    val labelOffsetsAOSegments: Map<String, SerializableOffset> = emptyMap(),
    val axisVisible: Boolean? = true,
    val curvesNarys: List<SerializableCurveNarys>,
    val curvesPudorys: List<SerializableCurvePudorys>,
    val curves3D: List<SerializableCurve3D>,
    val curvesBokorys: List<SerializableCurveBokorys> = emptyList(),
    val curvesAxo: List<SerializableCurveAxo> = emptyList(),
    val solidsOfRevolutionsNarys: List<SerializableSolidOfRevolutionNarys>,
    val solidsOfRevolutionsPudorys: List<SerializableSolidOfRevolutionPudorys>,
    val intersectionGroups: List<SerializableIntersectionGroup> = emptyList()
)

@Serializable
data class SerializableIntersectionPartRef(
    val kind: String,
    val id: String
)

@Serializable
data class SerializableIntersectionGroup(
    val id: String,
    val operandAId: String,
    val operandBId: String,
    val operandALabel: String,
    val operandBLabel: String,
    val parts: List<SerializableIntersectionPartRef>,
    val creationIndex: Long
)

@Serializable
data class MongeFileV1(
    val _format: String = "MongeSaveFile",
    val _version: Int = 1,
    val app: String = "MongeApp",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val state: SerializedMongeState
)

@OptIn(ExperimentalSerializationApi::class)
val MongeJson by lazy {
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
        allowSpecialFloatingPointValues = true
    }
}
