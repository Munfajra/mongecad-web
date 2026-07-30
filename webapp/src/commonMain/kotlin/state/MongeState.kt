package state


import kotlinx.serialization.Transient
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import utils.ImInt
import model.*
import model.axo.AxoMode
import model.axo.AxoModel
import model.classes.*
import monge.input.axo.AxoRenderBasis
import monge.input.axo.lines.linecomplete.PendingAxoLineCompletion
import monge.input.axo.points.pointcomplete.PendingAxoPointCompletion
import monge.input.axo.points.pointcomplete.PendingAxoPointCompletionFromAxo
import monge.input.axo.points.pointcomplete.PendingAxoPointCompletionFromBokorys
import monge.input.axo.points.pointcomplete.PendingAxoPointCompletionFromNarys
import monge.input.axo.segments.segmentcomplete.PendingAxoSegmentCompletion
import monge.input.selection.CylinderPhase
import serialization.HistoryState
import serialization.SettingsManager
import ui.mongeui.toolbar.PaperFormat
import utils.AtomicBoolean


class MongeState(
    val pointsPudorysSnapshot: MutableList<Point3DPudorys> = mutableListOf(),
    val pointsNarysSnapshot: MutableList<Point3DNarys> = mutableListOf(),
    val pointsPudorys: SnapshotStateList<Point3DPudorys> = mutableStateListOf(),
    val pointsNarys: SnapshotStateList<Point3DNarys> = mutableStateListOf(),
    val pointsAxo: SnapshotStateList<Point3DAxo> = mutableStateListOf(),
    val sharedPoints3D: MutableList<Point3D> = mutableListOf(),
    val lines3DPudorys: SnapshotStateList<Line3DProjectionPudorys> = mutableStateListOf(),
    val lines3DNarys: SnapshotStateList<Line3DProjectionNarys> = mutableStateListOf(),
    val lines3DBokorys: SnapshotStateList<Line3DProjectionBokorys> = mutableStateListOf(),
    val lines3DAxo: SnapshotStateList<Line3DProjectionAxo> = mutableStateListOf(),
    val lines3D: SnapshotStateList<Line3D> = mutableStateListOf(),
    val planes3D: SnapshotStateList<Plane3D> = mutableStateListOf(),
    val helpLinePudorys: SnapshotStateList<HelpLinePudorys> = mutableStateListOf(),
    val helpLineNarys: SnapshotStateList<HelpLineNarys> = mutableStateListOf(),
    val segments3D: SnapshotStateList<Segment3D> = mutableStateListOf(),
    val segmentSolids3D: SnapshotStateList<SegmentSolid3D> = mutableStateListOf(),

    )
 {
     var rightSidebarSelectionRatio by mutableFloatStateOf(0.55f)
     val pointsBokorys: SnapshotStateList<Point3DBokorys> = mutableStateListOf()

     var selectedSolidOfRevolutionId: String? by mutableStateOf(null)
     val allSegmentsNarys: List<SegmentsNarys>
         get() = helpSegmentsNarys + segmentsNarys

     var planePickAId: String? by mutableStateOf(null)
     var planePickBId: String? by mutableStateOf(null)
     var planePickCId: String? by mutableStateOf(null)
     var sceneVersion by mutableStateOf(0)
     @Transient var syncedSceneVersion: Int = 0
     @Transient var cachedNarysIntersections: SnapIntersectionCache? = null
     @Transient var cachedPudorysIntersections: SnapIntersectionCache? = null
     @Transient var cachedAOOverlayIntersections: AOOverlayIntersectionCache? = null
     var rightSidebarW by mutableFloatStateOf(380f)
     var xAxisDirection by mutableStateOf(XAxisDirection.POSITIVE_RIGHT)
     var yAxisDirectionPlane by mutableStateOf(YAxisDirectionPlane.POSITIVE_DOWN)
     var canvasWidth by mutableStateOf(0f)
     var canvasHeight by mutableStateOf(0f)
     var projectionMode by mutableStateOf(ProjectionMode.MONGE)
     @Transient
     var history: HistoryState = HistoryState()

     /**
      * Bumpne se při každé změně historie (commit, undo, redo, init).
      *
      * `history` je @Transient obyčejná proměnná, takže o jejích změnách
      * Compose neví – bez tohohle čítače by se `enabled` u undo/redo tlačítek
      * nikdy nepřepočítalo a zůstala by viset v tom stavu, v jakém se poprvé
      * složila. Čtou ho `canUndoNow()` / `canRedoNow()`.
      */
     var historyVersion by mutableStateOf(0)

     /**
      * Text varování o nepodporovaném obsahu otevřeného výkresu (jen web).
      * null = výkres je plně podporovaný. Viz `serialization/UnsupportedContent.kt`.
      */
     var unsupportedContentMessage: String? by mutableStateOf(null)
     @Transient var isRestoringFromHistory: Boolean = false
     var pendingConic3DId   by mutableStateOf<String?>(null)
     var pendingApex3DId    by mutableStateOf<String?>(null)
     var pendingPyramidPolygonId by mutableStateOf<String?>(null)
     var pendingPyramidApexId    by mutableStateOf<String?>(null)
     var pendingPrismPolygonId   by mutableStateOf<String?>(null)
     var pendingPrismNormal      by mutableStateOf<Offset3D?>(null)
     var pendingPrismBaseCenter  by mutableStateOf<Offset3D?>(null)
     var pendingPlatonicPolygonId by mutableStateOf<String?>(null)
     var platonicFlip            by mutableStateOf(false)
     // Compose stav, ne prosté `var`: přepínají se z lišty nad 3D náhledem,
     // takže se z nich musí umět překreslit jak ta lišta, tak scéna.
     var showReferencePlanesN by mutableStateOf(true)
     var showReferencePlanesP by mutableStateOf(true)
     var showReferencePlanesB by mutableStateOf(true)
     var showTraces by mutableStateOf(true)
     val colorReferencePlanesN: Color
         get() = SettingsManager.current.ncolor.toColor()
     val colorReferencePlanesB: Color
         get() = SettingsManager.current.rbcolor.toColor()
     var labelScaleAnchorPudorys by mutableStateOf<Float?>(null)
    var snappedPointLogical by mutableStateOf<Offset?>(null)
     val scaleLabelsWithCanvas: Boolean
         get() = SettingsManager.current.scaleLabelsWithCanvas
     val colorReferencePlanesP: Color
         get() = SettingsManager.current.pcolor.toColor()
     val msaaSamples: Int
         get() = SettingsManager.current.msaaSamples
     val refPlaneSize: Float
         get() = SettingsManager.current.referencePlaneSize
     var pendingDirection3D: Offset3D? = null
     var pendingLine3DId: String? = null
     var pendingSegment3DId: String? = null
     var pendingTopPlane3DId: String? = null
     var perpCylinderBaseConicId: String? = null
     var perpCylinderBaseNormal: Offset3D? = null
     var perpCylinderBaseCenter3D: Offset3D? = null
     var perpCylinderBaseU: Offset3D? = null
     var perpCylinderBaseV: Offset3D? = null
     var selectedCylindricalSurface: CylindricalSurface3D? by mutableStateOf(null)
     val selectedPolygons = mutableStateListOf<RegularPolygon3D>()
     var selectedPolygon: RegularPolygon3D? by mutableStateOf(null)
     val planeColor: Color
         get() = SettingsManager.current.planeColor.toColor()
     var isTextEditing by mutableStateOf(false)
     // Počet aktuálně otevřených modálních dialogů (MongeDialog). Když > 0,
     // globální klávesové zkratky se ignorují, aby psaní do polí dialogu
     // (např. kóta) nespínalo nástroje z Main.kt.
     var openDialogCount by mutableStateOf(0)
     val selectedSpheres3D = mutableStateListOf<SphereSurface3D>()
     val spheres3D = mutableStateListOf<SphereSurface3D>()
    var pendingId1: String? = null
     var showExportDialog by mutableStateOf(false)
     var showPrintDialog by mutableStateOf(false)
    var pendingMenuCommand by mutableStateOf<MenuCommand?>(null)
     var currentFile: String? = null
     var displayName by mutableStateOf("Výkres")
     var isDirty by mutableStateOf(false)
     var hoverHaloColor by mutableStateOf(Color(0xFF3B7DFF).copy(alpha = 0.38f))
     var selectedHaloColor by mutableStateOf(Color(0xFF0065FF).copy(alpha = 0.7f))
     val showParamDialog =  mutableStateOf(false)
     var inputSuperscript by mutableStateOf("")
     var inputLowerSuperscript by mutableStateOf("")
     var spherePreviewRadius: Float? = null
     var pendingCameraSnap: CameraSnap? = null
     var cameraAnim: CameraAnim? = null
     var canvasSizePx: IntSize = IntSize.Zero
    var traceToAttach: Trace2DProjection? = null
    var showConstruction = mutableStateOf(true)
    var openMenu by mutableStateOf<MenuKind?>(null)
    val pendingLinePudorys = mutableStateOf<Line3DProjectionPudorys?>(null)
     val pendingLinePudorysCompletion = mutableListOf<Line3DProjectionPudorys?>()
    val pendingLineNarys = mutableStateOf<Line3DProjectionNarys?>(null)
     val pendingLineNarysCompletion = mutableListOf<Line3DProjectionNarys?>()
    val pendingLine3D = mutableStateOf<Line3D?>(null)
    val arcsPudorys = mutableStateListOf<Arc2DPudorys>()
    val arcsNarys = mutableStateListOf<Arc2DNarys>()
    val arcsBokorys = mutableStateListOf<Arc2DBokorys>()
    val arcsAxoOverlay = mutableStateListOf<ArcAxoOverlay>()
    val circlesPudorys =  mutableStateListOf<ConicSectionPudorys>()
    val conicsPudorys =  mutableStateListOf<ConicSectionPudorys>()
    val conicsNarys =  mutableStateListOf<ConicSectionNarys>()
     val conicsBokorys =  mutableStateListOf<ConicSectionBokorys>()
     val conicsAxo =  mutableStateListOf<ConicSectionAxo>()
     val circlesNarys =  mutableStateListOf<ConicSectionNarys>()
     val circlesBokorys =  mutableStateListOf<ConicSectionBokorys>()
    val conicInputPointsPudorys = mutableMapOf<String, Triple<Offset, Offset, Offset>>()
    val conicInputPointsNarys = mutableMapOf<String, Triple<Offset, Offset, Offset>>()
     val conicInputPointsBokorys = mutableMapOf<String, Triple<Offset, Offset, Offset>>()
     val conicInputPointsAxo = mutableMapOf<String, Triple<Offset, Offset, Offset>>()

     val hyperbolaInputsPudorys = mutableMapOf<String, ConicInputHyperbolaPudorys>()
    val hyperbolaInputsNarys = mutableMapOf<String, ConicInputHyperbolaNarys>()
     val hyperbolaInputsAxo = mutableMapOf<String, ConicInputHyperbolaAxo>()

     val hyperbolaInputsBokorys= mutableMapOf<String, ConicInputHyperbolaBokorys>()
     var pendingDirection by mutableStateOf<Offset?>(null)
    var pendingDirectionNarys by mutableStateOf<Offset?>(null)
    var pendingLinePointPudorys by mutableStateOf<Offset?> (null)
    var pendingLinePointNarys by mutableStateOf<Offset?> (null)
     var pendingLinePointBokorys by mutableStateOf<Offset?> (null)
     var pendingLineTrimPick by mutableStateOf<PendingLineTrimPick?>(null)
     var basis: AxoRenderBasis? by mutableStateOf(null)
    var visibleQuadPudorys by mutableStateOf<VisibleQuad?>(null)
     var visibleQuadNarys by mutableStateOf<VisibleQuad?>(null)
     var visibleQuadBokorys by mutableStateOf<VisibleQuad?>(null)
     var kotoLinePickAId: String? by mutableStateOf(null)
     var kotoLinePickBId: String? by mutableStateOf(null)

     val visibleLines3DPudorys: List<Line3DProjectionPudorys>
         get() = lines3DPudorys.filter { it.showInAxo }
     val visibleLines3DNarys: List<Line3DProjectionNarys>
         get() = lines3DNarys.filter { it.showInAxo }
     val visibleLines3DBokorys: List<Line3DProjectionBokorys>
         get() = lines3DBokorys.filter { it.showInAxo }
     val visibleLines3DAxo: List<Line3DProjectionAxo>
         get() = lines3DAxo.filter { it.showInAxo }
     val allLinesPudorys: List<NamedLinePudorys>
         get() = visibleLines3DPudorys + lineTracesPudorys + helpLinePudorys
    val allLinesNarys: List<NamedLineNarys>
         get() = visibleLines3DNarys + lineTracesNarys + helpLineNarys
    val allSegmentsPudorys: List<SegmentsPudorys>
         get() = helpSegmentsPudorys + segmentsPudorys
     var consInfo = mutableStateOf("")
     var repeatCons = mutableStateOf(true)
     val conicalSurfaces = mutableStateListOf<ConicalSurface3D>()
     val cylindricalSurfaces = mutableStateListOf<CylindricalSurface3D>()
     val ruledSurfaces = mutableStateListOf<RuledSurface3D>()
     var selectedRuledSurfaceId: String? by mutableStateOf(null)
     var selectedConicalSurface: ConicalSurface3D? by mutableStateOf(null)
     var selectedCone = mutableStateListOf<ConicalSurface3D>()
     var selectedCylinder = mutableStateListOf<CylindricalSurface3D>()
     // Postupně vybírané objekty pro nástroj PRŮNIK (1. a 2. operand, na pořadí nezáleží)
     val intersectionPicks = mutableStateListOf<monge.input.intersections.IntersectionOperand>()
     val pendingRuledSurfaceDirectrices = mutableStateListOf<RuledSurfaceDirectrixRef>()
     var pendingRuledSurfaceDirectorPlaneId: String? by mutableStateOf(null)
     var pendingRuledSurfaceDirectorPlaneEquation: PlaneEquation? by mutableStateOf(null)
     val intersectionGroups = mutableStateListOf<IntersectionGroup>()
     var selectedIntersectionGroupId: String? by mutableStateOf(null)
     private val drawobjectsState = mutableStateOf(Mongeobjects.NONE)
     var drawobjects: Mongeobjects
         get() = drawobjectsState.value
         set(value) {
             drawobjectsState.value = value
             if (value != Mongeobjects.NONE) {
                 panMode = false
                 isPanning = false
             }
         }
     var cylinderPhase by mutableStateOf(CylinderPhase.PICK_CONIC )
    var projekcnityp by mutableStateOf(ProjectionType.SINGLE)
    var mongeMode by mutableStateOf(DrawingModeMonge.PUDORYS)
     var axoMode by mutableStateOf(AxoMode.AXO_PUDORYS)
    var constructionModifier by mutableStateOf(ConstructionModifier.NONE)
    var triggerRedraw by mutableStateOf(0)
     var paperAnchorLogical by mutableStateOf(Offset.Zero)
     var paperAnchorFromOrigin: Offset = Offset.Zero
     var paperAnchorPinned by mutableStateOf(false)

     // v MongeState
     var kotoSegPickAId: String? by mutableStateOf(null)
     var kotoSegPickBId: String? by mutableStateOf(null)
     val circleArcEnds = mutableStateMapOf<String, Pair<Offset, Offset>>()
     val circleArcMode = mutableStateMapOf<String, ArcMode>()
     val circleArcsPudorys = mutableStateListOf<CircleArcPudorys>()
     val circleArcsNarys = mutableStateListOf<CircleArcNarys>()

     var projectionPhase by mutableStateOf<String?>("pudorys_start")
    var inputName by mutableStateOf("")
     var inputKota by mutableStateOf("")
    var isNameConfirmed by mutableStateOf(false)
     var isKotaConfirmed by mutableStateOf(false)
     var axisVisible by mutableStateOf(true)

    var pendingX by mutableStateOf<Float?>(null)
    var pendingY by mutableStateOf<Float?>(null)
    var pendingZ by mutableStateOf<Float?>(null)
    var pendingXpudorys by mutableStateOf<Float?>(null)
    var pendingXnarys by mutableStateOf<Float?>(null)

    var canvasOffset by mutableStateOf(Offset.Zero)
     var cursorPosition by mutableStateOf(Offset.Zero)
    var scale by mutableStateOf(5f)
    var isCtrlPressed by mutableStateOf(false)
    var isShiftPressed by mutableStateOf(false)
    var isOffsetInitialized by mutableStateOf(false)
    var currentLineStyleSettings = LineStyleSettings()
    var currentHelpLineStyleSettings = HelpLineStyleSettings()

     var showTypeDistanceDialog by mutableStateOf(false)
    var showTypeAngleDialog by mutableStateOf(false)
    var showSpecialLineDialog = mutableStateOf(false)
     var showSettingsDialog by mutableStateOf(false)
    var showPlaneNamingDialog by mutableStateOf(false)
    var showLineCompletionErrorDialog by mutableStateOf(false)
    var lineCompletionErrorMessage by mutableStateOf("")
    var showConstructionErrorDialog by mutableStateOf(false)
    var constructionErrorMessage by mutableStateOf("")
    // Varování PŘED převodem Monge→Axo, když výkres obsahuje rotační plochu
    // (obrys v axo je aproximace a může potřebovat ruční úpravy). Dialog se ukáže
    // v původním výkresu; OK pak spustí samotný převod.
    var showAxoSorWarningDialog by mutableStateOf(false)
    // Gate, aby se po potvrzení dialogu převod provedl bez opětovného varování.
    var axoSorWarningAcknowledged by mutableStateOf(false)
    // Upozornění, že průnik dvou vybraných objektů je prázdný (objekty se neprotínají).
    var showEmptyIntersectionDialog by mutableStateOf(false)
    // Průnik se počítá na pozadí (bodově vzorkované křivky můžou trvat i sekundy) →
    // po dobu výpočtu se zobrazuje modální LoadingDialog blokující vstup.
    var intersectionComputing by mutableStateOf(false)
    // Převod objektů do nové axonometrie (obrysy rotačních ploch se trasují na mřížce)
    // běží na pozadí → modální LoadingDialog, stejný vzor jako u průniků.
    var axoConversionComputing by mutableStateOf(false)
     var showPaperPreview by mutableStateOf(false)
     var paperPortrait   by mutableStateOf(true)
     var paperFormat by mutableStateOf(PaperFormat.A4)
     val snapThreshold: Float
         get() = SettingsManager.current.snapThreshold
    val isOpenGLWindowRunning = AtomicBoolean(false)
     /**
      * Je vedle 2D plátna otevřený 3D náhled scény?
      *
      * Desktop na tohle má samostatné OpenGL okno; na webu je to půlka
      * kreslicí plochy s vlastním WebGL plátnem (`ui/gl3d/Gl3DViewport.kt`).
      */
     var show3DPanel by mutableStateOf(false)

     /**
      * Jakou část kreslicí plochy zabírá 3D náhled (0 = nic, 1 = všechno).
      *
      * Výška obou polovin je daná oknem, táhlo mezi nimi tedy mění jen poměr
      * šířek. Drží se poměr, ne šířka v pixelech, aby rozdělení přežilo změnu
      * velikosti okna.
      */
     var gl3dSplitRatio by mutableFloatStateOf(0.5f)
     var kotoLiftLinePickLineId: String? = null
     var selectedLineForPoint: Line3D? = null
    val selectedPointsPudorys = mutableStateListOf<Point3DPudorys>()
     val selectedPointsBokorys = mutableStateListOf<Point3DBokorys>()
     val selectedPointsAxo= mutableStateListOf<Point3DAxo>()
    val selectedPointsNarys = mutableStateListOf<Point3DNarys>()
     val selectedPoints3D = mutableStateListOf<Point3D>()
     val selectedLines3D = mutableStateListOf<Line3D>()
     val selectedSegments3D = mutableStateListOf<Segment3D>()
     val selectedSegmentSolids3D = mutableStateListOf<SegmentSolid3D>()
    val selectedLinesPudorys = mutableStateListOf<NamedLinePudorys>()
     val selectedConicsPudorys = mutableStateListOf<ConicSectionPudorys>()
     var liftingConicPudorys: ConicSectionPudorys? = null
     var liftingConicNarys: ConicSectionNarys? = null
     var liftingConicBokorys: ConicSectionBokorys? = null
     val selectedConicsNarys = mutableStateListOf<ConicSectionNarys>()
     val selectedConicsBokorys = mutableStateListOf<ConicSectionBokorys>()
     val selectedConicsAxo = mutableStateListOf<ConicSectionAxo>()

     val selectedLinesNarys = mutableStateListOf<NamedLineNarys>()
     val selectedLinesBokorys = mutableStateListOf<NamedLineBokorys>()
     val selectedLinesAxo = mutableStateListOf<Line3DProjectionAxo>()
     val selectedCirclesPudorys = mutableStateListOf<ConicSectionPudorys>()
     val selectedCirclesNarys = mutableStateListOf<ConicSectionNarys>()
     val selectedCirclesBokorys = mutableStateListOf<ConicSectionBokorys>()
     val selectedArcsPudorys = mutableStateListOf<Arc2DPudorys>()
     val selectedArcsNarys = mutableStateListOf<Arc2DNarys>()
     val selectedArcsBokorys = mutableStateListOf<Arc2DBokorys>()
     val selectedArcsAxoOverlay = mutableStateListOf<ArcAxoOverlay>()
     var linefrom2points by mutableStateOf<Line3DProjectionPudorys?>(null)
     var nextCreationIndex: Long = 0L

     var selectedLineIdsPudorys = mutableStateListOf<String>()
     var selectedLineIdsNarys   = mutableStateListOf<String>()
     var selectedLineIdsBokorys   = mutableStateListOf<String>()
     var selectedLineIdsAxo   = mutableStateListOf<String>()

     var pendingAidPoint: AidPointLogical? by mutableStateOf(null)
     var pendingAOPoint: AxoOverlayPoint? by mutableStateOf(null)
     var pendingAOLine: AxoOverlayLine? by mutableStateOf(null)
     var pendingAOSegment: AxoOverlaySegment? by mutableStateOf(null)
     var pendingAxoLine: Line3DProjectionAxo? by mutableStateOf(null)
     var snappedConicPudorys: ConicSectionPudorys? = null
     var snappedSegmentNarys: SegmentsNarys? = null
     var snappedSegmentPudorys: SegmentsPudorys? = null
     var snappedSegmentBokorys: SegmentsBokorys? = null
     var snappedSegmentAxo:  Segment2DAxo? = null
     var snappedArcNarys: Arc2DNarys? = null
     var snappedArcPudorys: Arc2DPudorys? = null
     var snappedArcBokorys: Arc2DBokorys? = null
     var snappedArcAxoOverlay: ArcAxoOverlay? = null
     var snappedLinePudorys: NamedLinePudorys? = null
     var snappedLineNarys: NamedLineNarys? = null
     var snappedLineBokorys: NamedLineBokorys? = null
     var snappedLineAxo: Line3DProjectionAxo? = null
     var snappedConicNarys: ConicSectionNarys? = null
     var snappedConicAxo: ConicSectionAxo? = null
     var snappedConicBokorys: ConicSectionBokorys? = null
     var snappedCurvePudorys: CurvePudorys? = null
     var snappedCurveNarys: CurveNarys? = null
     var snappedCurveBokorys: CurveBokorys? = null
     var snappedCurveAxo: CurveAxo? = null

     var snappedPointNarys: Point3DNarys? = null
     var snappedPointBokorys: Point3DBokorys? = null
     var snappedAOPoint: AxoOverlayPoint? = null
     var snappedAxoPoint: Point3DAxo? = null
     var snappedPointPudorys: Point3DPudorys? = null
     var defaultClipAboveX12Narys by mutableStateOf(true)
     var defaultClipLeftOfZAxisNarys by mutableStateOf(true)
     var defaultClipBelowYAxisBokorys by mutableStateOf(true)
     var defaultClipLeftOfZAxisBokorys by mutableStateOf(true)
     var defaultClipBelowX12Pudorys by mutableStateOf(true)
     var defaultClipLeftOfYAxisPudorys by mutableStateOf(true)
     var defaultClipAxoLineToOctant by mutableStateOf(true)
     var showRightSidebar: Boolean = true
     var deferSelectionUntil: Long = 0L
     var stopSnap by mutableStateOf(0L)
     var isPanning by mutableStateOf(false)

     /**
      * Režim posunu plátna z levého panelu. Dokud je zapnutý, posouvá jakýkoli
      * vstup – obě tlačítka myši, hrot i jeden prst – a nic nekreslí. Aktivní
      * nástroj se při zapnutí nemění. Při spuštění libovolného konstrukčního
      * nástroje se režim posunu automaticky vypne v setteru [drawobjects].
      */
     var panMode by mutableStateOf(false)
     var midpointPoint1: Offset? = null

     val ellipseArcEnds = mutableStateMapOf<String, Pair<Offset, Offset>>()
     val ellipseArcMode = mutableStateMapOf<String, ArcMode>()
     var pendingArcA: Offset? = null
     var activeConicIdForArc: String? = null
     // Když je true, arc flow (CONICARC) se po dokončení převede na segmentaci
     // viditelnosti (plný oblouk = viditelně, zbytek čárkovaně) místo prostého oblouku.
     var conicVisibilityAuthoring: Boolean = false
     // Zachycené omezení (conicArc) při startu authoringu viditelnosti – aby ho
     // viditelnost nepřepsala, ale nastavovala solid jen v jeho rámci a omezení
     // se pak obnovilo. null = žádné omezení (kreslí se celá kuželosečka).
     var visRestrictEllipse: Pair<Offset, Offset>? = null
     var visRestrictEllipseMode: ArcMode? = null
     var visRestrictParabola: Pair<Offset, Offset>? = null
     var visRestrictHypB1: Pair<Offset, Offset>? = null
     var visRestrictHypB2: Pair<Offset, Offset>? = null
     val parabolaArcEnds = mutableStateMapOf<String, Pair<Offset, Offset>>()
     val solidsOfRevolutionNarys = mutableStateListOf<SolidOfRevolutionNarys>()
     val solidsOfRevolutionPudorys = mutableStateListOf<SolidOfRevolutionPudorys>()


     val hyperbolaArcBranch1 = mutableStateMapOf<String, Pair<Offset, Offset>>()
     val hyperbolaArcBranch2 = mutableStateMapOf<String, Pair<Offset, Offset>>()

     var pendingHypA1: Offset? = null
     var pendingHypA2: Offset? = null
     var pendingHypBranch1SX: Int? = null
     var pendingHypBranch2SX: Int? = null

     var activeParentConic3DIdForEllipseArc: String? = null
     var activeArcMode: ArcMode? = null

     var pendingArcA_3D: Offset? = null

     val ellipseArcParams3D = mutableStateMapOf<String, Pair<Float, Float>>()
     val ellipseArcEnds3D   = mutableStateMapOf<String, Pair<Offset3D, Offset3D>>()
     val parabolaArcParams3D = mutableStateMapOf<String, Pair<Float, Float>>()
     val parabolaArcEnds3D   = mutableStateMapOf<String, Pair<Offset3D, Offset3D>>()
     var tempLine: TempSnapLine? = null
     var selectedLineForParallelPudorys by mutableStateOf<NamedLinePudorys?>(null)
    var selectedLineForParallelPudorysSecond by mutableStateOf<NamedLinePudorys?>(value=null)
     var selectedLineForParallelNarysSecond by mutableStateOf<NamedLineNarys?>(value=null)
     var selectedLineForParallelNarys by mutableStateOf<NamedLineNarys?>(null)
     var selectedLineForParallelBokorys by mutableStateOf<NamedLineBokorys?>(null)
     var selectedLineForParallelBokorysSecond by mutableStateOf<NamedLineBokorys?>(null)
     var selectedLineForParallelAxo by mutableStateOf<Line3DProjectionAxo?>(null)
     var selectedLineForParallelAxoSecond by mutableStateOf<Line3DProjectionAxo?>(null)
     var selectedLineForParallelAO by mutableStateOf<AxoOverlayLine?>(null)
     var selectedLineForParallelAOSecond by mutableStateOf<AxoOverlayLine?>(null)

     var selectedLineForParallelPlanePudorys by mutableStateOf<NamedLinePudorys?>(null)
    var selectedLineForParallelPlaneNarys by mutableStateOf<NamedLineNarys?>(null)
     var selectedSegmentForParallelNarys by mutableStateOf<SegmentsNarys?>(null)
    var selectedSegmentForParallelPudorys by mutableStateOf<SegmentsPudorys?>(null)
     var selectedSegmentForParallelBokorys by mutableStateOf<SegmentsBokorys?>(null)
     var selectedSegmentForParallelAxo by mutableStateOf<Segment2DAxo?>(null)
     var selectedSegmentForParallelAO by mutableStateOf<AxoOverlaySegment?>(null)


     // parametry oblouků – t1/t2 na každé větvi
     val hyperbolaArcParams3D: MutableMap<String, Pair<Pair<Float, Float>?, Pair<Float, Float>?>> = mutableMapOf()

     // krajní body v 3D – vždy dvojice bodů pro 1. a 2. větev
     val hyperbolaArcEnds3D: MutableMap<String, Pair<Pair<Offset3D, Offset3D>?, Pair<Offset3D, Offset3D>?>> = mutableMapOf()

     // Po částech stylované kuželosečky (střídavě plná/čárkovaná).
     // conicSegments   – per-view render forma (klíč = id per-view kuželosečky,
     //                   stejný keyspace jako ellipseArcEnds), parametry v nativním
     //                   parametru daného průmětu.
     // conicSegments3D – view-nezávislá forma (klíč = ConicSection3D.id), hranice
     //                   úseků jako 3D body; do per-view se přepočítává per průmět.
     val conicSegments = mutableStateMapOf<String, model.ConicSegmentation>()
     val conicSegments3D = mutableStateMapOf<String, model.ConicSegmentation3D>()


     var hoveredSegmentPudorys: SegmentsPudorys? by mutableStateOf(null)
     var hoveredSegmentNarys: SegmentsNarys? by mutableStateOf(null)
     var hoveredSegmentBokorys: SegmentsBokorys? by mutableStateOf(null)
     var hoveredCirclePudorys: ConicSectionPudorys? by mutableStateOf(null)
     var hoveredCircleNarys: ConicSectionNarys? by mutableStateOf(null)


     // v MongeState
     val expandedObjectListKeys = mutableStateListOf<String>()
     val selectedTracesPudorys: MutableList<PlaneTracePudorys> = mutableListOf()
     val selectedTracesNarys:   MutableList<PlaneTraceNarys>   = mutableListOf()
     val selectedTracesBokorys:   MutableList<PlaneTraceBokorys>   = mutableListOf()

     var aidPointsLogical = mutableStateListOf<AidPointLogical>()
     var hoveredAidPointId: String? = null
     var hoveredAOLineId: String? = null
     var hoveredAOSegId: String? = null
     var snappedAOLineId: String? = null
     var snappedAOSegmentId: String? = null
     var hoveredArcPudorysId: String? by mutableStateOf(null)
     var hoveredArcNarysId: String? by mutableStateOf(null)
     var hoveredArcBokorysId: String? by mutableStateOf(null)
     var hoveredArcAxoOverlayId: String? by mutableStateOf(null)
     var selectedAidPointIds = mutableStateListOf<String>()
     var selectedAOPointIds = mutableStateListOf<String>()
     var selectedAOLineIds = mutableStateListOf<String>()
     var selectedAOSegIds = mutableStateListOf<String>()
     var selectedPlaneForCircle: Plane3D? = null
     var planeNameInput by mutableStateOf("")
     var planeNameForceToken by mutableStateOf(0)
    var planePendingForNaming by mutableStateOf<Plane3D?>(null)
     var narysTracePendingForNaming by mutableStateOf<PlaneTraceNarys?>(null)
     var pudorysTracePendingForNaming by mutableStateOf<PlaneTracePudorys?>(null)
     var bokorysTracePendingForNaming by mutableStateOf<PlaneTraceBokorys?>(null)
    var pendingMongeModeChange by mutableStateOf<DrawingModeMonge?>(null)
    /** Pending stav konstrukce oblouku — viz [ArcPendingState]. */
    val arc = ArcPendingState()
    /** Stav probíhajícího inline přejmenování — viz [RenameInProgressState]. */
    val rename = RenameInProgressState()

     val axoOverlayPoints = mutableStateListOf<AxoOverlayPoint>()
    val axoOverlayLines = mutableStateListOf<AxoOverlayLine>()
     //val axoOverlaySegments = mutableStateListOf<AxoOverlaySegment>()

    var wasSecondaryPressed = false

    var lineStartPoint3DPudorys by mutableStateOf<Point3DPudorys?>(null)
    var lineStartPoint3DNarys by mutableStateOf<Point3DNarys?>(null)
     var lineStartPoint3DBokorys by mutableStateOf<Point3DBokorys?>(null)

     var suspendRepeatCons: Boolean = false
     var kotoLineRefPudorys: Line3DProjectionPudorys? = null
     var kotoLinePickedPoints3D: MutableList<Point3D> = mutableListOf()
     var pendingPlaneTracePudorys: PlaneTracePudorys? = null
     var pendingOuterArcP_base: PendingArc? = null
     var pendingOuterArcP_top:  PendingArc? = null
     var pendingOuterArcN_base: PendingArc? = null
     var pendingOuterArcN_top:  PendingArc? = null
    var xOnX12Narys by mutableStateOf<Point3DNarys?>(null)
    var xOnX12Pudorys by mutableStateOf<Point3DPudorys?>(null)
    var firstPlaneTraceStartPudorys by mutableStateOf<Point3DPudorys?>(null)
    var firstPlaneTraceStartNarys by mutableStateOf<Point3DNarys?>(null)
    var firstPlaneTraceStartBokorys by mutableStateOf<Point3DBokorys?>(null)
    var tracePlanePudorys by mutableStateOf<PlaneTracePudorys?>(null)
    var tracePlaneNarys by mutableStateOf<PlaneTraceNarys?>(null)
    var tracePlaneBokorys by mutableStateOf<PlaneTraceBokorys?>(null)
     val lineTracesPudorys = mutableStateListOf<PlaneTracePudorys>()
    val lineTracesNarys = mutableStateListOf<PlaneTraceNarys>()
     val lineTracesBokorys = mutableStateListOf<PlaneTraceBokorys>()
    var specialLineCase = mutableStateOf<SpecialLineCase?>(null)
    val selectedPlanes = mutableStateListOf<Plane3D>()
     val conics3D = mutableStateListOf<ConicSection3D>()
     var regularPolygon = RegularPolygonSettings()
     var namingPolygon: Boolean=  false
     var namingPolygonStartLetter: Char = 'A'
     val polygons3D = mutableStateListOf<RegularPolygon3D>()
     val planePolygons2D = mutableStateListOf<PlanePolygon2D>()
     val selectedPlanePolygons2D = mutableStateListOf<PlanePolygon2D>()
     var pudorysCurvePickRefs: MutableList<CurvePudRef> = mutableListOf()
     var pudorysCurveLastPickedKey: String? = null

     var pudorysCurvePickPointIds: MutableList<String> = mutableListOf()
     val curvesPudorys = mutableStateListOf<CurvePudorys>()
     val curvesNarys = mutableStateListOf<CurveNarys>()
     val curvesBokorys = mutableStateListOf<CurveBokorys>()
     val curvesAxo = mutableStateListOf<CurveAxo>()
     val curves3D = mutableStateListOf<Curve3D>()
     var narysCurvePickPointIds: MutableList<String> = mutableListOf()
     var narysCurveLastPickedId: String? = null
     var bokorysCurvePickPointIds: MutableList<String> = mutableListOf()
     var bokorysCurveLastPickedId: String? = null
     var curve3DPickPointIds: MutableList<String> = mutableListOf() // ukládáme Point3D.id
     var curve3DLastPickedId: String? = null
     var selectedCurve3DId: String? by mutableStateOf(null)
     var selectedCurvePudorysId: String? by mutableStateOf(null)
     var selectedCurveNarysId: String? by mutableStateOf(null)
     var selectedCurveBokorysId: String? by mutableStateOf(null)
     var selectedCurveAxoId: String? by mutableStateOf(null)

     val labelOffsetsNarys = mutableStateMapOf<String, Offset>() // key = lineProjection.id
     val labelOffsetsPudorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsBokorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsHelpNarys = mutableStateMapOf<String, Offset>()
     val labelOffsetsHelpPudorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsPointsNarys = mutableStateMapOf<String, Offset>()
     val labelOffsetsPointsAxo = mutableStateMapOf<String, Offset>()
     val labelOffsetsPointsBokorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsPointsPudorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsTracePudorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsTraceNarys = mutableStateMapOf<String, Offset>()
     val labelOffsetsTraceBokorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsAidPoints = mutableStateMapOf<String, Offset>()
     val labelOffsetsAOPoints = mutableStateMapOf<String, Offset>()
     val labelOffsetsAOLines = mutableStateMapOf<String, Offset>()
     val labelOffsetsAxoLines = mutableStateMapOf<String, Offset>()
     val labelOffsetsSegmentsPudorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsSegmentsNarys = mutableStateMapOf<String, Offset>()
     val labelOffsetsSegmentsBokorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsSegmentsAxo = mutableStateMapOf<String, Offset>()
     val labelOffsetsHelpSegmentsPudorys = mutableStateMapOf<String, Offset>()
     val labelOffsetsHelpSegmentsNarys = mutableStateMapOf<String, Offset>()
     val labelOffsetsAOSegments = mutableStateMapOf<String, Offset>()
     var curve3DPickClosed by mutableStateOf(false)
     var narysCurvePickClosed by mutableStateOf(false)
     var pudorysCurvePickClosed by mutableStateOf(false)
     var bokorysCurvePickClosed by mutableStateOf(false)

     var hoveredLineNarys by mutableStateOf<NamedLineNarys?>(null)
    var hoveredLinePudorys by mutableStateOf<NamedLinePudorys?>(null)
     var hoveredLineBokorys by mutableStateOf<NamedLineBokorys?>(null)
     var hoveredTraceNarys by mutableStateOf<PlaneTraceNarys?>(null)
     var hoveredTracePudorys by mutableStateOf<PlaneTracePudorys?>(null)
     var hoveredTraceBokorys by mutableStateOf<PlaneTraceBokorys?>(null)
     var skipnaming by mutableStateOf(false)


    var segmentStartPudorys: Point3DPudorys? = null
     var segmentStartBokorys: Point3DBokorys? = null
    val segmentsPudorys = mutableStateListOf<Segment2DPudorys>()
    val selectedSegmentsPudorys = mutableStateListOf<SegmentsPudorys>()
     val pendingSegmentPudorys = mutableStateListOf<Segment2DPudorys>()
     val pendingSegmentNarys = mutableStateListOf<Segment2DNarys>()
    var segmentStartNarys: Point3DNarys? = null
    val segmentsNarys = mutableStateListOf<Segment2DNarys>()
     val segmentsBokorys = mutableStateListOf<Segment2DBokorys>()
     val segmentsAxo = mutableStateListOf<Segment2DAxo>()
    val helpSegmentsNarys = mutableStateListOf<HelpSegmentNarys>()
    val helpSegmentsPudorys = mutableStateListOf<HelpSegmentPudorys>()
    val selectedSegmentsNarys = mutableStateListOf<SegmentsNarys>()
     val selectedSegmentsAxo = mutableStateListOf<Segment2DAxo>()
     val selectedSegmentsBokorys = mutableStateListOf<SegmentsBokorys>()
    var pendingXA by mutableStateOf<Float?>(null)
    var pendingZA by mutableStateOf<Float?>(null)
    var pendingXB by mutableStateOf<Float?>(null)
    var pendingZB by mutableStateOf<Float?>(null)
    var pendingYA by mutableStateOf<Float?>(null)
    var pendingYB by mutableStateOf<Float?>(null)

    var pendingPoint1: Offset? by mutableStateOf(null)
     var pendingAxoPoint1: Point3DAxo? by mutableStateOf(null)
     var pendingAxoPoint2: Point3DAxo? by mutableStateOf(null)
    var pendingPoint2: Offset? by mutableStateOf(null)
    var pendingPoint3: Offset? by mutableStateOf(null)
    var pendingDistance: Float? by mutableStateOf(null)
    var pendingAngle: Float? by mutableStateOf(null)
     var pendingAxoPointCompletion: PendingAxoPointCompletion? by mutableStateOf(null)
     var pendingAxoPointCompletionFromNarys: PendingAxoPointCompletionFromNarys? by mutableStateOf(null)
     var pendingAxoPointCompletionFromBokorys by mutableStateOf<PendingAxoPointCompletionFromBokorys?>(null)
     var pendingAxoPointCompletionFromAxo by mutableStateOf<PendingAxoPointCompletionFromAxo?>(null)
     var pendingAxoLineCompletion by mutableStateOf<PendingAxoLineCompletion?>(null)
     var pendingAxoSegmentCompletion by mutableStateOf<PendingAxoSegmentCompletion?>(null)

     var completingLineSecondStart by mutableStateOf<Offset?>(null)
     var completingSegmentSecondStart by mutableStateOf<Offset?>(null)
     var completingSegmentSecondEnd by mutableStateOf<Offset?>(null)
    val focusRequester = FocusRequester()
    var skipNextClick by mutableStateOf(false)
     var storedLineNarysForParallel: Line3DProjectionNarys? = null
     var storedLinePudorysForParallel: Line3DProjectionPudorys? = null



     var completionPending: CompletionRequest? = null
     var reusingExistingProjection: Boolean = false
     // True, pokud byl v aktuálním kliknutí právě zvolen vzor směru přímky (rovnoběžnost/kolmost).
     // Brání tomu, aby druhý dispatcher (NÁRYS) ve stejném kliknutí přímku rovnou i umístil.
     var lineDirectionPatternJustPicked: Boolean = false
     var kotoSegmentEditId by mutableStateOf<String?>(null)
     var kotoSegmentKotaA by mutableStateOf("")
     var kotoSegmentKotaB by mutableStateOf("")

     var tempCircle by mutableStateOf<TempSnapCircle?>(null)
     // zvýraznění bodu v canvasu při editaci kóty
     var kotoHighlightSegmentId by mutableStateOf<String?>(null)
     var kotoHighlightEndpoint by mutableStateOf(0) // 1 = A, 2 = B, 0 = nic3
     val exportW = ImInt(1920)
     val exportH = ImInt(1080)

     var isResizingRightSidebar: Boolean = false

     // 1) vybraná osa
     var selectedRevolutionAxis3D: Line3D? = null

     // Rozpracovaná šroubovice. Výsledná šroubovice se neukládá jako vlastní
     // modelový objekt: po potvrzení vzniká běžná Curve3D a její projekce.
     var helixAxis3D: Line3D? by mutableStateOf(null)
     var helixTangent3D: Line3D? by mutableStateOf(null)
     var helixStartPoint3D: Point3D? by mutableStateOf(null)
     var helixStartRadial: Offset3D? by mutableStateOf(null)
     var helixRadius: Float? by mutableStateOf(null)
     var helixReducedHeight: Float? by mutableStateOf(null)
     var helixPitchAngleRadians: Float? by mutableStateOf(null)
     var helixRotationSign: Int by mutableStateOf(1)
     var helixRightHanded: Boolean by mutableStateOf(true)
     // Dočasné přepnutí do GetAngle při volbě úhlu stoupání šroubovice.
     var helixPitchAngleTransferActive: Boolean by mutableStateOf(false)
     var showHelixTurnsDialog: Boolean by mutableStateOf(false)
     var showHelixPitchDialog: Boolean by mutableStateOf(false)

     // 2) vybrané kousky poledníku v nárysu (ids)
     val selectedMeridianNarysIds = mutableStateListOf<String>()
     val selectedMeridianPudorysIds = mutableStateListOf<String>()

     // 3) live preview výsledku (volitelné)
     var previewRevolutionRmax: Float? = null
     var previewRevolutionNecks: List<Float> = emptyList()
     var activeAxoModel by mutableStateOf(AxoModel())
     var axoSetupVisible by mutableStateOf(true)
     var axoSetupAllowDismissWithoutEdit by mutableStateOf(false)
     // true dokud uživatel poprvé nezvolí axonometrii (nová axo přes AxoSetup nebo
     // převod Monge/KOTO→Axo). Defaultní axo, které se zakládá jen aby vše fungovalo,
     // se do historie nezapisuje – při prvním potvrzení axa se historie resetuje.
     @Transient var axoHistoryPendingReset: Boolean = false
     // Discrete switch for AXO (both ORTHOGONAL and OBLIQUE). false = native
     // AXO pose (and free orbiting away from it) — the screen basis keeps
     // the axo triangle's own constant tilt. true = a canonical, untilted
     // reference-plane snap (Půdorys/Nárys/Bokorys) — same AXO basis (so the
     // screen axis order never changes when activating a view), but with the
     // tilt forced to zero so the view looks like a plain orthographic
     // drawing (see rollOverrideDeg in obliqueAxoViewFromBasis).
     var axoAtReferencePlaneView by mutableStateOf(false)
     // true only right after snapping to AXO_PLANE: render the true oblique
     // (sheared) parallel projection for an OBLIQUE AxoModel instead of the
     // orbitable orthonormal camera approximation — see
     // axoObliqueProjectionView in ObliqueAxoView.kt. Has no effect for
     // ORTHOGONAL models. Reset to false the instant the user orbits (this
     // projection has no yaw/pitch — there is nothing to orbit smoothly).
     var axoObliqueNativeView by mutableStateOf(false)
     // 0 = fully orthonormal approximation, 1 = fully oblique projection.
     // Animated smoothly to avoid visual jumps on transition.
     var obliqueBlendFactor = 0f

     ///AXO
     var axoOverlaySegments = mutableStateListOf<AxoOverlaySegment>()

     /**
      * AO (overlay/konstrukční) kolekce pro snapping a hledání průsečíků. Vrací prázdný
      * seznam, pokud je vypnuté zobrazení konstrukce (showConstruction == false) — pak se
      * na tyto objekty nesnapuje a nevstupují do průsečíků, stejně jako se nekreslí.
      */
     val snapAxoOverlayPoints: List<AxoOverlayPoint>
         get() = if (showConstruction.value) axoOverlayPoints else emptyList()
     val snapAxoOverlayLines: List<AxoOverlayLine>
         get() = if (showConstruction.value) axoOverlayLines else emptyList()
     val snapAxoOverlaySegments: List<AxoOverlaySegment>
         get() = if (showConstruction.value) axoOverlaySegments else emptyList()
     val snapArcsAxoOverlay: List<ArcAxoOverlay>
         get() = if (showConstruction.value) arcsAxoOverlay else emptyList()

 }

class MongeStartState {
    var pendingMenuCommand by mutableStateOf<MenuCommand?>(null)
    var showSettingsDialog by mutableStateOf(false)
    var axoModel by mutableStateOf<AxoModel?>(null)
    var axoSetupVisible by mutableStateOf(false)
}
