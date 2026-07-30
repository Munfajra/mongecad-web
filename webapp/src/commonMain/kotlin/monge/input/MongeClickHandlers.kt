package monge.input

import utils.System
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import model.*
import model.classes.Line3DProjectionPudorys
import monge.input.ConicArcs.associated.*
import monge.input.ConicArcs.single.*
import monge.input.combineprojections.handleKotoLiftExistingPudorysLineByTwoParentedPointsClick
import monge.input.combineprojections.handlePlaneFromTracePickPointClick
import monge.input.conixections.*
import monge.input.curves.*
import monge.input.lines.*
import monge.input.intersections.handleIntersectionClick
import monge.input.quadrics.conicalsurface.handleConicalToolClick
import monge.input.quadrics.cylindricalsurface.handleCylindricalToolClick
import monge.input.quadrics.cylindricalsurface.handlePerpendicularCylinderClick
import monge.input.quadrics.spheres.finalizeSphereConstruction
import monge.input.quadrics.spheres.startSphereFromSelection
import monge.input.solids.handlePyramidToolClick
import monge.input.solids.handlePrismToolClick
import monge.input.solids.handlePlatonicToolClick
import monge.input.meridian.solidOfRevolutionNarys
import monge.input.meridian.solidOfRevolutionPudorys
import monge.input.ruledsurface.handleRuledSurfaceSelection
import monge.input.planeobjects.conicsections.*
import monge.input.planes.*
import monge.input.points.*
import monge.input.ConicArcs.single.getLogicalCursorNarys
import monge.input.segments.handleKotoSegmentThroughTwoParentedPointsClick
import monge.input.segments.handleSegmentClick
import monge.input.segments.handleSegmentOnLineN
import monge.input.segments.handleSegmentOnLineP
import monge.input.selection.CylinderPhase
import monge.input.selection.selection
import monge.input.tools.*
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.getLogicalCursor


fun handleClick(
    cursor: Offset,
    snappedPointLogical: Offset?,
    state: MongeState,
    change: PointerInputChange,
)

 {
    // Nová událost kliknutí – resetuj příznak, který brání dvojímu zpracování
    // (výběr vzoru směru + okamžité umístění) v jednom kliknutí.
    state.lineDirectionPatternJustPicked = false

    if (handleCustomLineTrimClick(state)) {
        change.consume()
        return
    }

    // Šroubovici web zatím nemá – tlačítko není v toolbaru, takže se sem nedostane.

    if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION)
    {
        if (state.projectionPhase == "narys_start") {
            setProjectionPhase("rev_axis_select", state)
        }
        solidOfRevolutionNarys(state)
    }
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.SOLID_OF_REVOLUTION)
     {
         if (state.projectionPhase == "pudorys_start") {
             setProjectionPhase("rev_axis_select_pudorys", state)
         }
         solidOfRevolutionPudorys(state)
     }
     if (state.mongeMode== DrawingModeMonge.PUDORYS&&state.drawobjects== Mongeobjects.CURVE&&
         (state.projekcnityp== ProjectionType.SINGLE||state.projekcnityp== ProjectionType.AUXILIARY)) {
         setProjectionPhase("pudorys_curve_pick",state)
         val p = state.snappedPointPudorys
         val a = state.aidPointsLogical.find { it.id== state.hoveredAidPointId}
         println(a)
         if (p!=null){
     handlePudorysCurvePickPointClick(state,p)}
         if (a!=null) {
             handlePudorysCurvePickAidPointClick(state,a)
         }
     }
     if (         state.drawobjects== Mongeobjects.CURVE&&
         state.projekcnityp== ProjectionType.ASSOCIATED) {
         setProjectionPhase("curve3d_pick_points",state)
         val p = state.snappedPointPudorys
         val n = state.snappedPointNarys
         if (p!=null){
           handleCurve3DPickFromPudorysClick( state,p)}
         if (n!=null) {
             handleCurve3DPickFromNarysClick(state,n)
         }
     }


     if (state.mongeMode== DrawingModeMonge.NARYS&&state.drawobjects== Mongeobjects.CURVE&&
         (state.projekcnityp== ProjectionType.SINGLE||state.projekcnityp== ProjectionType.AUXILIARY)) {
         setProjectionPhase("narys_curve_pick",state)
         val p = state.snappedPointNarys
         if (p!=null){
             handleNarysCurvePickPointClick(state,p)}
     }

    //single BODY půdorys
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.POINTS&&
         state.projekcnityp== ProjectionType.SINGLE) {
         handleSinglePudorysPoint(
             cursor = cursor,
             snappedPointLogical = snappedPointLogical,
             canvasOffset = state.canvasOffset,
             scale = state.scale,
             state = state
         )
     }

     //single BODY nárys
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.POINTS&& (state.projekcnityp== ProjectionType.SINGLE)) {
         handleSingleNarysPoint(cursor, snappedPointLogical,
             canvasOffset = state.canvasOffset,
             scale = state.scale,
             state = state)
     }
    //pomocné body
     handleAidPoint(cursor, snappedPointLogical, canvasOffset = state.canvasOffset, scale = state.scale,state)
     //single přímky nárys
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.LINES&& (state.projekcnityp== ProjectionType.SINGLE||state.projekcnityp== ProjectionType.AUXILIARY)) {

         handleSingleLineNarys(
             cursor = cursor,
             snappedPointLogical = snappedPointLogical,
             canvasOffset = state.canvasOffset,
             scale = state.scale,
             state = state
         )
     }

     //single přímky půdorys
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.LINES&& (state.projekcnityp== ProjectionType.SINGLE||state.projekcnityp== ProjectionType.AUXILIARY)) {
         handleSinglePudorysLine(
             cursor = cursor,
             snappedPointLogical = snappedPointLogical,
             canvasOffset = state.canvasOffset,
             scale = state.scale,
             state = state
         )
     }
     //úsečky
     if (state.projectionMode== ProjectionMode.PLANE||state.projectionMode== ProjectionMode.MONGE || (state.projectionMode == ProjectionMode.KOTO && (state.projekcnityp== ProjectionType.SINGLE)||state.projekcnityp== ProjectionType.AUXILIARY)) {
         handleSegmentClick(
             cursor = state.cursorPosition,
             snappedPointLogical = snappedPointLogical,
             canvasOffset = state.canvasOffset,
             scale = state.scale,
             state = state,
             change = change
         )
     }
     if (state.projectionMode == ProjectionMode.KOTO && state.drawobjects == Mongeobjects.SEGMENTS && state.projekcnityp == ProjectionType.ASSOCIATED){
         val p = state.snappedPointPudorys?: return
         handleKotoSegmentThroughTwoParentedPointsClick(state,p)
     }
     //úsečky na přímce
     if (state.drawobjects== Mongeobjects.SEGMENT_ON_LINE && state.mongeMode== DrawingModeMonge.PUDORYS){
     handleSegmentOnLineP(cursor=cursor,
     snappedPointLogical,state.canvasOffset,state.scale,state)
     }
     if (state.drawobjects== Mongeobjects.SEGMENT_ON_LINE && state.mongeMode== DrawingModeMonge.NARYS){
         handleSegmentOnLineN(cursor=cursor,
             snappedPointLogical,state.canvasOffset,state.scale,state)
     }

     //sdružené průměty BODU (start PŮDORYS)
     if (state.projectionMode== ProjectionMode.MONGE) {
         handleCombinedPointProjectionPudorysFirst(
             cursor = cursor,
             snappedPointLogical = snappedPointLogical,
             canvasOffset = state.canvasOffset,
             scale = state.scale,
             state = state
         )
         handleCombinedPointProjectionNarysSecond(
             cursor = cursor,
             snappedPointLogical = snappedPointLogical,
             canvasOffset = state.canvasOffset,
             scale = state.scale,
             state = state
         )
     }

     //sdružené průměty BODU (start NÁRYS)
     handleCombinedPointProjectionNarysFirst(
         cursor = cursor,
         snappedPointLogical = snappedPointLogical,
         canvasOffset = state.canvasOffset,
         scale = state.scale,
         state = state
     )

     //Roviny (start PŮDORYS)
     if (state.projectionMode== ProjectionMode.MONGE){
         handlePlaneConstructionPudorys(
             cursor = cursor,
             snappedPointLogical = snappedPointLogical,
             canvasOffset = state.canvasOffset,
             scale = state.scale,
             change = change,
             state = state
         )}
     if(state.projectionMode== ProjectionMode.KOTO &&state.drawobjects== Mongeobjects.PLANE && state.projekcnityp == ProjectionType.ASSOCIATED){
         val p = state.snappedPointPudorys ?: return
         handlePlaneFrom3PointsPickByClick(state, p)
         change.consume()
         return
     }
     if (state.projectionPhase=="picking_line_points"){
         val p = state.snappedPointPudorys ?: return
         val line = state.linefrom2points?:return
         handleKotoLiftExistingPudorysLineByTwoParentedPointsClick(state,line,p)
     }
     handleGetKotaPoint(snappedPointLogical,state,cursor)
     //Roviny (start NÁRYS)
     handlePlaneConstructionNarys(
         cursor = cursor,
         snappedPointLogical = snappedPointLogical,
         canvasOffset = state.canvasOffset,
         scale = state.scale,
         change = change,
         state = state
     )
     handleSingleTraceNarys(
         cursor = cursor,
         snappedPointLogical = snappedPointLogical,
         canvasOffset = state.canvasOffset,
         scale = state.scale,
         state = state
     )
     handleSingleTracePudorys(
         cursor = cursor,
         snappedPointLogical = snappedPointLogical,
         canvasOffset = state.canvasOffset,
         scale = state.scale,
         state = state
     )

     //sdružená přímka (start PŮDORYS)
     if (state.projectionMode== ProjectionMode.MONGE){
     handleCombinedLineConstructionPUDORYS(
         cursor = cursor,
         snappedPointLogical = snappedPointLogical,
         state = state
     )}
     if (state.projectionMode == ProjectionMode.KOTO &&
         state.mongeMode == DrawingModeMonge.PUDORYS &&
         state.drawobjects == Mongeobjects.LINES &&
         state.projectionPhase == "pudorys_start"&&
         state.projekcnityp== ProjectionType.ASSOCIATED
     ) {
         val p = state.snappedPointPudorys ?: return
         handleKotoLineThroughTwoParentedPointsClick(state, p)
         change.consume()
         return
     }

     //sdružená přímka (start NÁRYS)
     handleCombinedLineConstrucionNARYS(
         cursor=cursor,
         snappedPointLogical=snappedPointLogical,
         state=state
     )
    //oblouky
     handleArcs(   cursor = cursor,
         snappedPointLogical = snappedPointLogical,
         canvasOffset = state.canvasOffset,
         scale = state.scale,
         state = state)

     //označení
     // Výběr kuželosečky umí sám posunout fázi kolmého válce (advancePendingConicConstruction),
     // proto si fázi zapamatujeme – dispatcher níže nesmí tentýž klik zpracovat podruhé.
     val cylinderPhaseBeforeSelection = state.cylinderPhase
     selection(
         state = state,)
     if (state.drawobjects == Mongeobjects.RULED_SURFACE) {
         handleRuledSurfaceSelection(state)
         change.consume()
         return
     }
     //Střed 2 bodů
     if (state.drawobjects== Mongeobjects.AID_MIDPOINT){
     handleClickAidMidpoint(     cursor = cursor,
         snappedPointLogical = snappedPointLogical,
         canvasOffset = state.canvasOffset,
         scale = state.scale,
         state = state)}
     state.deferSelectionUntil = System.currentTimeMillis() + 100
     if (state.projectionMode == ProjectionMode.KOTO &&
         state.drawobjects == Mongeobjects.POINTS &&
         state.projekcnityp == ProjectionType.ASSOCIATED
     ) {
         when (state.projectionPhase) {

             "pudorys_start" -> {
                 val plane = state.selectedPlanes.firstOrNull()
                 if (plane != null) {
                     handleClick_AssociatedPointInPlane_SelectPlane(state, plane)
                     // DŮLEŽITÉ: ukonči zpracování kliku, aby nepropadl do další fáze
                     return
                 }
                 val line = state.snappedLinePudorys as? Line3DProjectionPudorys
                 state.kotoLineRefPudorys = line
                 val parent = line?.parent
                     if (parent !=null) {
                         handleClick_AssociatedPointOnLine_SelectLine(state,parent)
                     }
             }

             "plane_point_pick_xy" -> {
                 val xy = getLogicalCursor(
                     snappedPointLogical,
                     cursor,
                     state.canvasOffset,
                     state.scale,
                     state.canvasWidth,
                     state.canvasHeight,
                     state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                     state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
                 )
                 handleClick_AssociatedPointInPlane_PickXY(state, xy)
                 return
             }
             "line_point_pick_xy" ->{
                 val logical = getLogicalCursor(
                     snappedPointLogical,
                     cursor,
                     state.canvasOffset,
                     state.scale,
                     state.canvasWidth,
                     state.canvasHeight,
                     state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                     state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
                 )
                 handleClick_AssociatedPointOnLine_PickXY(state,logical)
             }
         }
     }

     //GUMA
     if (state.drawobjects == Mongeobjects.ERASE && state.mongeMode == DrawingModeMonge.PUDORYS) {
         eraseObjectAtPudorys(state, cursor,snappedPointLogical)
         state.deferSelectionUntil = System.currentTimeMillis() + 100
         return
     }
     if (state.drawobjects == Mongeobjects.ERASE && state.mongeMode == DrawingModeMonge.NARYS) {
         eraseObjectAtNarys(state, cursor,snappedPointLogical)
         state.deferSelectionUntil = System.currentTimeMillis() + 100
         return
     }
     //KUŽSEČKY
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.CIRCLE && (state.projekcnityp == ProjectionType.SINGLE||state.projekcnityp == ProjectionType.AUXILIARY)) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleClickCirclePudorys(logical, state)
         return
     }
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.CIRCLE&& (state.projekcnityp == ProjectionType.SINGLE||state.projekcnityp == ProjectionType.AUXILIARY)) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleClickCircleNarys(logical, state)
         return
     }

    //elipsy
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.ELLIPSE&& state.projekcnityp == ProjectionType.SINGLE) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleEllipseConstructionPudorys(logical, state)
         return
     }
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.ELLIPSE && state.projekcnityp == ProjectionType.SINGLE) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleEllipseConstructionNarys(logical, state)
         return
     }
     //paraboly
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.PARABOLA&& state.projekcnityp == ProjectionType.SINGLE) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleParabolaConstructionPudorys(logical, state)
         return
     }
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.PARABOLA&& state.projekcnityp == ProjectionType.SINGLE) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleParabolaConstructionNarys(logical, state)
         return
     }
     //hyperboly
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.HYPERBOLA&& state.projekcnityp == ProjectionType.SINGLE) {
         handleHyperbolaConstructionPudorys(snappedPointLogical, state)
         return
     }
     if (state.mongeMode == DrawingModeMonge.NARYS&& state.drawobjects == Mongeobjects.HYPERBOLA&& state.projekcnityp == ProjectionType.SINGLE) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleHyperbolaConstructionNarys(logical, state)
         return
     }
     //sdružené kužsečky v rovině
            //elipsa pudorys -> narys
     if (state.mongeMode == DrawingModeMonge.PUDORYS&& state.drawobjects == Mongeobjects.ELLIPSE&& state.projekcnityp == ProjectionType.ASSOCIATED) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleEllipseInPlaneConstructionPudorys(logical, state)
         return
     }
            //elipsa narys -> pudorys
     if (state.mongeMode == DrawingModeMonge.NARYS&& state.drawobjects == Mongeobjects.ELLIPSE&& state.projekcnityp == ProjectionType.ASSOCIATED) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleEllipseInPlaneConstructionNarys(logical, state)
         return
     }
            //kružnice pudorys -> narys
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.CIRCLE && state.projekcnityp == ProjectionType.ASSOCIATED) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )

         handleCircleInPlaneConstructionPudorys(logical, state)
         return
     }
     //kružnice narys -> pudorys
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.CIRCLE && state.projekcnityp == ProjectionType.ASSOCIATED) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )

         handleCircleInPlaneConstructionNarys(logical, state)
         return
     }
     //parabola pudorys -> narys
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.PARABOLA && state.projekcnityp == ProjectionType.ASSOCIATED) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleParabolaInPlaneConstructionPudorys(logical, state)
         return
     }
     //parabola narys -> pudorys
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.PARABOLA && state.projekcnityp == ProjectionType.ASSOCIATED) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleParabolaInPlaneConstructionNarys(logical, state)
         return
     }
     //hyperbola
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.HYPERBOLA&& state.projekcnityp == ProjectionType.ASSOCIATED) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleHyperbolaInPlaneConstructionPudorys(logical, state)
         return
     }
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.HYPERBOLA&& state.projekcnityp == ProjectionType.ASSOCIATED) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleHyperbolaInPlaneConstructionNarys(logical, state)
         return
     }
    //části kužseček
     if (state.mongeMode == DrawingModeMonge.PUDORYS && (state.drawobjects == Mongeobjects.CONICARC||
         state.drawobjects == Mongeobjects.CONICARCAS)&&state.projectionPhase == "pudorys_start"){
         decideConicPudorys(state)
     }
     if (state.mongeMode == DrawingModeMonge.PUDORYS && (state.drawobjects == Mongeobjects.CONICARC)){
         arcEllipsePudorys(state,snappedPointLogical,cursor)
         arcParabolaPudorys(state,snappedPointLogical,cursor)
         arcHyperbolaPudorys(state,snappedPointLogical,cursor)

     }
     if (state.mongeMode == DrawingModeMonge.PUDORYS && state.drawobjects == Mongeobjects.CONICARCAS ) {
         arcEllipsePudorys3D(state,snappedPointLogical,cursor)
         arcParabolaPudorys3D(state,snappedPointLogical,cursor)
         arcHyperbolaPudorys3D(state,snappedPointLogical,cursor)
     }

     if (state.mongeMode == DrawingModeMonge.NARYS && (state.drawobjects == Mongeobjects.CONICARC||
                 state.drawobjects == Mongeobjects.CONICARCAS)&&state.projectionPhase == "narys_start"){
         decideConicNarys(state)
     }
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.CONICARC){
         arcEllipseNarys(state,snappedPointLogical,cursor)
         arcParabolaNarys(state,snappedPointLogical,cursor)
         arcHyperbolaNarys(state,snappedPointLogical,cursor)

     }
     if (state.mongeMode == DrawingModeMonge.NARYS && state.drawobjects == Mongeobjects.CONICARCAS){
         arcEllipseNarys3D(state,snappedPointLogical,cursor)
         arcParabolaNarys3D(state,snappedPointLogical,cursor)
         arcHyperbolaNarys3D(state,snappedPointLogical,cursor)
     }
     //kužel
     handleConicalToolClick(state=state)
     //jehlan
     handlePyramidToolClick(state)
     //platónské těleso
     handlePlatonicToolClick(state)
     //válec
     handleCylindricalToolClick(state)
     run {
         val logical = if (state.mongeMode == DrawingModeMonge.NARYS)
             getLogicalCursorNarys(snappedPointLogical, cursor, state.canvasOffset, state.scale,
                 state.canvasWidth, state.canvasHeight, state.xAxisDirection)
         else
             getLogicalCursor(snappedPointLogical, cursor, state.canvasOffset, state.scale,
                 state.canvasWidth, state.canvasHeight,
                 state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                 state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP)
         // Pokud klik už posunul fázi PICK_CONIC_PERP → PICK_CENTER_PERP přes výběr kuželosečky,
         // druhé volání by týmž klikem rovnou umístilo horní podstavu.
         val perpPhaseJustAdvanced = cylinderPhaseBeforeSelection == CylinderPhase.PICK_CONIC_PERP &&
             state.cylinderPhase == CylinderPhase.PICK_CENTER_PERP
         if (!perpPhaseJustAdvanced) handlePerpendicularCylinderClick(state, logical)
         //kolmý hranol
         handlePrismToolClick(state, logical)
     }
     //koule
     if (state.drawobjects == Mongeobjects.SPHERE && (state.projectionPhase == "pudorys_start" ||state.projectionPhase == "narys_start"  )){
         startSphereFromSelection(state)
     }
     else if(state.drawobjects == Mongeobjects.SPHERE &&
         (state.projectionPhase == "sphere_radius_pick_pudorys" || state.projectionPhase == "sphere_radius_pick_narys")) {
         finalizeSphereConstruction(state,snappedPointLogical)
     }
     //nuhelník
     if (state.drawobjects == Mongeobjects.REGULAR_POLYGON_IN_PLANE && state.mongeMode == DrawingModeMonge.PUDORYS) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleRegularPolygonInPlaneClickPudorys(state, logical)
     }
     if (state.drawobjects == Mongeobjects.REGULAR_POLYGON_IN_PLANE && state.mongeMode == DrawingModeMonge.NARYS) {
         val logical = getLogicalCursor(
             snappedPointLogical,
             cursor,
             state.canvasOffset,
             state.scale,
             state.canvasWidth,
             state.canvasHeight,
             state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
             state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
         )
         handleRegularPolygonInPlaneClickNarys(state, logical)
     }



     handlePlaneFromTracePickPointClick(state)

//přenesení vzdálenost
     handleClickDistancePlacement(snappedPointLogical,state,cursor)
     //přenesení úhlu
     handleClickAnglePlacement(snappedPointLogical, state, cursor)
     //přenos po rovnoběžce
     if (state.drawobjects== Mongeobjects.TRANSPARALLEL||state.drawobjects== Mongeobjects.TRANSORTH) {
         handleClickTransParallel(snappedPointLogical,state,cursor)
     }

     //průniky objektů (router – rozhoduje podle označené dvojice)
     handleIntersectionClick(state)

     // sem přidáš další větve – nárys, přímky, roviny, atd.
     state.skipNextClick = false

}
