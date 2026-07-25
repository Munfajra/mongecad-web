package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import draw.mongescreen.objects.orth.conics.drawConicHyperbolaNarys
import draw.mongescreen.objects.orth.conics.drawConicHyperbolaPudorys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLineNarys
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedPreviewLinePudorys
import model.LineStyle
import model.ProjectionMode
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.*
import monge.input.planeobjects.conicsections.*
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld


fun DrawScope.drawHyperbolaPreviewPudorysPlane(snappedPointLogical: Offset?, state: MongeState) {
    // jen v téhle fázi
    if (state.projectionPhase != "pudorys_vertex") return
    val logical = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val asym1 = state.selectedLineForParallelPudorys ?: return
    val asym2 = state.selectedLineForParallelPudorysSecond ?: return
    val plane = state.selectedPlaneForCircle!!


    // pozor na „y→z“ převod
    val vertex2D = logical
    val eq    = plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }
    // zkombinuj 3D hyperbolu
    val conic3D = constructHyperbola3DFromAsymptotesAndVertex(
        asym1, asym2, vertex2D, state.selectedPlaneForCircle!!, state
    ) ?: return
    val p3D1 = liftXYtoPlane(asym1.point.x, asym1.point.y, eq)
    val p3D2 = liftXYtoPlane(asym2.point.x, asym2.point.y, eq)
    val dir3D1 = liftXYtoPlane(
        asym1.point.x + asym1.direction.x,
        asym1.point.y + asym1.direction.y,
        eq
    ) - p3D1
    val dir3D2 = liftXYtoPlane(
        asym2.point.x + asym2.direction.x,
        asym2.point.y + asym2.direction.y,
        eq
    ) - p3D2

    val l1pp = Offset(p3D1.x,p3D1.y)
    val l1pd = Offset(dir3D1.x, dir3D1.y).normalize()
    val l2pp = Offset(p3D2.x, p3D2.y)
    val l2pd = Offset(dir3D2.x, dir3D2.y).normalize()
    val l1pn = Offset(p3D1.x, p3D1.z)
    val l1dn = Offset(dir3D1.x, dir3D1.z).normalize()
    val l2pn = Offset(p3D2.x, p3D2.z)
    val l2dn = Offset(dir3D2.x, dir3D2.z).normalize()


    // vypočti souřadnice konice pro pudorys
    val coeffsP = Matrix3x3.toCoefficients(conic3D.projectToXY())
    val previewP = ConicSectionPudorys(
        a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
        d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
        rawName = conic3D.rawName,
        localColor = Color.Gray,          // klidně šedě
        strokeWidth = 1.5f,
        lineStyle = LineStyle.Dashed,
        parent = conic3D
    )
    val coeffsN = Matrix3x3.toCoefficients(conic3D.projectToXZ())
    val previewN = ConicSectionNarys(
        a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
        d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
        rawName = conic3D.rawName,
        localColor = Color.Gray,          // klidně šedě
        strokeWidth = 1.5f,
        lineStyle = LineStyle.Dashed,
        parent = conic3D
    )
    val center3D     = conic3D.p0
    val axisMain3D   = conic3D.u.normalize()
    val aSemi3D      = conic3D.a!!
    val axisOther3D  = conic3D.v.normalize()
    val bSemi3D      = conic3D.b!!
    // 3D-vrcholy obou os
    val vertexMain3D  = center3D + axisMain3D  * aSemi3D
    val vertexOther3D = center3D + axisOther3D * bSemi3D
    val oppVertex3D  = center3D - axisMain3D * aSemi3D
    // --- PUDORYS / XY projekce ---
    // 4P) promítnout do world-XY
    val centerP      = Offset(center3D.x,      center3D.y)
    val oppMainP = Offset(oppVertex3D.x,    oppVertex3D.y)
    val vMainP       = Offset(vertexMain3D.x,  vertexMain3D.y)
    val vOtherP      = Offset(vertexOther3D.x, vertexOther3D.y)
    val startP       = Point3DPudorys(centerP.x, centerP.y)

    // 5P) vykreslit obě osy čárkovaně a s klipem nad X12
    drawDashedPreviewLinePudorys(
        start          = startP,
        cursorWorld    = vMainP,
        strokeWidth    = 2.dp.toPx(),
        color          = Color.Red,
        scale          = state.scale,
        canvasOffset   = state.canvasOffset,
        clipToBelowX12 = true
    )
    drawDashedPreviewLinePudorys(
        start          = startP,
        cursorWorld    = vOtherP,
        strokeWidth    = 1.dp.toPx(),
        color          = Color.LightGray,
        scale          = state.scale,
        canvasOffset   = state.canvasOffset,
        clipToBelowX12 = true
    )

    // 6P) vykreslit oba vrcholy jako křížky

        val r = 6f
    listOf(vMainP, oppMainP).forEach { worldPt ->
        val screenPt = worldPt.toScreenOld(state.scale, state.canvasOffset)
        drawLine(
            color = Color.Red,
            start = screenPt + Offset(-r, -r),
            end = screenPt + Offset(+r, +r),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.Red,
            start = screenPt + Offset(-r, +r),
            end = screenPt + Offset(+r, -r),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
    if (state.projectionMode == ProjectionMode.KOTO) return
    // --- NÁRYS / XZ projekce ---
    // 4N) promítnout do world-XZ (invert Y až v toScreen)

    val vMainN      = Offset(vertexMain3D.x,   vertexMain3D.z)
    val vOtherN     = Offset(vertexOther3D.x,  vertexOther3D.z)
    val startN      = Point3DNarys(center3D.x, center3D.z)
    val oppMainN = Offset(oppVertex3D.x,    oppVertex3D.z)
    // 5N) vykreslit obě osy čárkovaně a s klipem nad Z

    drawDashedPreviewLineNarys(
        start         = startN,
        cursorWorld   = vMainN,
        strokeWidth   = 1f,
        color         = Color.DarkGray,
        scale         = state.scale,
        canvasOffset  = state.canvasOffset,
        clipToAboveZ  = true
    )
    drawDashedPreviewLineNarys(
        start         = startN,
        cursorWorld   = vOtherN,
        strokeWidth   = 1f,
        color         = Color.LightGray,
        scale         = state.scale,
        canvasOffset  = state.canvasOffset,
        clipToAboveZ  = true
    )

    // 6N) vykreslit oba vrcholy jako křížky
    listOf(vMainN, oppMainN).forEach { worldPt ->
        // XZ → screen (invert Y)
        val screenPt = Offset(worldPt.x, -worldPt.y)
            .toScreenOld(state.scale, state.canvasOffset)
        drawLine(
            color = Color.Red,
            start = screenPt + Offset(-r, -r),
            end = screenPt + Offset(+r, +r),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.Red,
            start = screenPt + Offset(-r, +r),
            end = screenPt + Offset(+r, -r),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }





    // z něj zase získej vrchol + osu
    val (vertexP, axisP) = extractVertexAndAxisFromPudorys(previewP,eq)
    val (vertexN, axisN) = extractVertexAndAxisFromNarys(previewN,eq)
    val centerPH = intersectLines2D(l1pp,l1pd, l2pp,l2pd)
    val centerNH = intersectLines2D(l1pn,l1dn,l2pn,l2dn)
    // a vykresli preview hyperboly
    drawConicHyperbolaPudorys(
        center       = centerPH ?: return,
        vertex       = vertexP,
        asymptote1   = asym1.direction.normalize(),
        canvasOffset = state.canvasOffset,
        scale        = state.scale,
        color        = Color.Gray,
        strokeWidth  = 1.5f,
        lineStyle    = LineStyle.Dashed,
        axis         = axisP
    )
    drawConicHyperbolaNarys(
        center       = centerNH ?: return,
        vertex       = Offset(vertexN.x,-vertexN.y),
        asymptote1   = l1dn,
        canvasOffset = state.canvasOffset,
        scale        = state.scale,
        color        = Color.Gray,
        strokeWidth  = 1.5f,
        lineStyle    = LineStyle.Dashed,
        axis         = Offset(axisN.x,-axisN.y)
    )
}

fun DrawScope.drawHyperbolaPreviewNarysPlane(snappedPointLogical: Offset?, state: MongeState) {
    // jen v téhle fázi
    if (state.projectionPhase != "narys_vertex") return
    val logicalorig = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val logical = Offset(logicalorig.x,-logicalorig.y)
    val asym1 = state.selectedLineForParallelNarys ?: return
    val asym2 = state.selectedLineForParallelNarysSecond ?: return
    val plane = state.selectedPlaneForCircle!!


    // pozor na „y→z“ převod
    val vertex2D = logical
    val eq    = plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }
    // zkombinuj 3D hyperbolu
    val conic3D = constructHyperbola3DFromAsymptotesAndVertexNarys(
        asym1, asym2, vertex2D, state.selectedPlaneForCircle!!, state
    ) ?: return
    val p3D1 = liftXZtoPlane(asym1.point.x, asym1.point.z, eq)
    val p3D2 = liftXZtoPlane(asym2.point.x, asym2.point.z, eq)
    val dir3D1 = liftXZtoPlane(
        asym1.point.x + asym1.direction.x,
        asym1.point.z + asym1.direction.y,
        eq
    ) - p3D1
    val dir3D2 = liftXZtoPlane(
        asym2.point.x + asym2.direction.x,
        asym2.point.z + asym2.direction.y,
        eq
    ) - p3D2

    val l1pp = Offset(p3D1.x,p3D1.y)
    val l1pd = Offset(dir3D1.x, dir3D1.y).normalize()
    val l2pp = Offset(p3D2.x, p3D2.y)
    val l2pd = Offset(dir3D2.x, dir3D2.y).normalize()
    val l1dn = Offset(dir3D1.x, dir3D1.z).normalize()


    // vypočti souřadnice konice pro pudorys
    val coeffsP = Matrix3x3.toCoefficients(conic3D.projectToXY())
    val previewP = ConicSectionPudorys(
        a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
        d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
        rawName = conic3D.rawName,
        localColor = Color.Gray,          // klidně šedě
        strokeWidth = 1.5f,
        lineStyle = LineStyle.Dashed,
        parent = conic3D
    )
    val coeffsN = Matrix3x3.toCoefficients(conic3D.projectToXZ())
    val previewN = ConicSectionNarys(
        a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
        d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
        rawName = conic3D.rawName,
        localColor = Color.Gray,          // klidně šedě
        strokeWidth = 1.5f,
        lineStyle = LineStyle.Dashed,
        parent = conic3D
    )
    val center3D     = conic3D.p0
    val axisMain3D   = conic3D.u.normalize()
    val aSemi3D      = conic3D.a!!
    val axisOther3D  = conic3D.v.normalize()
    val bSemi3D      = conic3D.b!!
    // 3D-vrcholy obou os
    val vertexMain3D  = center3D + axisMain3D  * aSemi3D
    val vertexOther3D = center3D + axisOther3D * bSemi3D
    val oppVertex3D  = center3D - axisMain3D * aSemi3D
    // --- PUDORYS / XY projekce ---
    // 4P) promítnout do world-XY
    val centerP      = Offset(center3D.x,      center3D.y)
    val oppMainP = Offset(oppVertex3D.x,    oppVertex3D.y)
    val vMainP       = Offset(vertexMain3D.x,  vertexMain3D.y)
    val vOtherP      = Offset(vertexOther3D.x, vertexOther3D.y)
    val startP       = Point3DPudorys(centerP.x, centerP.y)

    // 5P) vykreslit obě osy čárkovaně a s klipem nad X12
    drawDashedPreviewLinePudorys(
        start          = startP,
        cursorWorld    = vMainP,
        strokeWidth    = 1f,
        color          = Color.DarkGray,
        scale          = state.scale,
        canvasOffset   = state.canvasOffset,
        clipToBelowX12 = true
    )
    drawDashedPreviewLinePudorys(
        start          = startP,
        cursorWorld    = vOtherP,
        strokeWidth    = 1f,
        color          = Color.DarkGray,
        scale          = state.scale,
        canvasOffset   = state.canvasOffset,
        clipToBelowX12 = true
    )

    // 6P) vykreslit oba vrcholy jako křížky

    val r = 6f
    listOf(vMainP, oppMainP).forEach { worldPt ->
        val screenPt = worldPt.toScreenOld(state.scale, state.canvasOffset)
        drawLine(
            color = Color.Red,
            start = screenPt + Offset(-r, -r),
            end = screenPt + Offset(+r, +r),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.Red,
            start = screenPt + Offset(-r, +r),
            end = screenPt + Offset(+r, -r),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
    // --- NÁRYS / XZ projekce ---
    // 4N) promítnout do world-XZ (invert Y až v toScreen)

    val vMainN      = Offset(vertexMain3D.x,   vertexMain3D.z)
    val vOtherN     = Offset(vertexOther3D.x,  vertexOther3D.z)
    val startN      = Point3DNarys(center3D.x, center3D.z)
    val oppMainN = Offset(oppVertex3D.x,    oppVertex3D.z)
    // 5N) vykreslit obě osy čárkovaně a s klipem nad Z
    drawDashedPreviewLineNarys(
        start         = startN,
        cursorWorld   = vMainN,
        strokeWidth   = 2.dp.toPx(),
        color         = Color.Red,
        scale         = state.scale,
        canvasOffset  = state.canvasOffset,
        clipToAboveZ  = true
    )
    drawDashedPreviewLineNarys(
        start         = startN,
        cursorWorld   = vOtherN,
        strokeWidth   = 1.dp.toPx(),
        color         = Color.DarkGray,
        scale         = state.scale,
        canvasOffset  = state.canvasOffset,
        clipToAboveZ  = true
    )

    // 6N) vykreslit oba vrcholy jako křížky
    listOf(vMainN, oppMainN).forEach { worldPt ->
        // XZ → screen (invert Y)
        val screenPt = Offset(worldPt.x, -worldPt.y)
            .toScreenOld(state.scale, state.canvasOffset)
        drawLine(
            color = Color.Red,
            start = screenPt + Offset(-r, -r),
            end = screenPt + Offset(+r, +r),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.Red,
            start = screenPt + Offset(-r, +r),
            end = screenPt + Offset(+r, -r),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }





    // z něj zase získej vrchol + osu
    val (vertexP, axisP) = extractVertexAndAxisFromPudorys(previewP,eq)
    val (vertexN, axisN) = extractVertexAndAxisFromNarys(previewN,eq)
    val centerPH = intersectLines2D(l1pp,l1pd, l2pp,l2pd)
    val centerNH = Offset(conic3D.p0.x, conic3D.p0.z)
    println("🧪 drawConicHyperbolaNarys:")
    println("center = $centerNH")
    println("vertex = ${Offset(vertexN.x, -vertexN.y)}")
    println("axis = ${Offset(axisN.x, -axisN.y)}")
    println("asymptote1 = $l1dn")
    drawConicHyperbolaNarys(
        center       = centerNH,
        vertex       = Offset(vertexN.x,-vertexN.y),
        asymptote1   = l1dn,
        canvasOffset = state.canvasOffset,
        scale        = state.scale,
        color        = Color.Gray,
        strokeWidth  = 1.5f,
        lineStyle    = LineStyle.Dashed,
        axis         = Offset(axisN.x,-axisN.y)
    )
    // a vykresli preview hyperboly
    drawConicHyperbolaPudorys(
        center       = centerPH ?: return,
        vertex       = vertexP,
        asymptote1   = l1pd,
        canvasOffset = state.canvasOffset,
        scale        = state.scale,
        color        = Color.Gray,
        strokeWidth  = 1.5f,
        lineStyle    = LineStyle.Dashed,
        axis         = axisP
    )

}
