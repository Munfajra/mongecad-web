package monge.input.planeobjects.planelift

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import draw.mongescreen.objects.axo.projectDirection3DToAxo
import model.*
import model.classes.*
import monge.input.axo.conixections.liftAxoOverlayPointToPlane
import monge.input.planeobjects.conicsections.*
import monge.input.segments.addSegment3DAndDetectSolids
import serialization.commitSnapshot
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.rightDescriptionBar.getConicType
import ui.resetStavu
import utils.allocIndex

/** Viditelnost jednotlivých průmětů v axonometrii po liftu. */
data class LiftShow(val pud: Boolean, val nar: Boolean, val bok: Boolean, val axo: Boolean)

/**
 * Pravidlo viditelnosti v axonometrii po liftu:
 * - V Monge zachováváme dosavadní konvenci (půdorys+nárys+axo viditelné, bokorys skrytý).
 * - V Axo je viditelný pouze zdrojový průmět + axonometrický; ostatní dostanou showInAxo=false.
 */
fun liftShow(source: String, mongeLift: Boolean): LiftShow =
    if (mongeLift) LiftShow(true, true, false, true)
    else when (source) {
        "pudorys" -> LiftShow(true, false, false, true)
        "narys"   -> LiftShow(false, true, false, true)
        "bokorys" -> LiftShow(false, false, true, true)
        // U axo zdroje necháme viditelný i půdorys, aby bylo poznat, že se objekt opravdu liftnul.
        "axo"     -> LiftShow(true, false, false, true)
        else      -> LiftShow(true, true, true, true)
    }

private fun isMongeLift(state: MongeState): Boolean = state.projectionMode != ProjectionMode.AXO

fun finalizePendingConicLiftWithPlane(state: MongeState, plane: Plane3D): Boolean {
    when (state.projectionPhase) {
        "lift_conic_select_plane" -> {
            val conic = state.liftingConicPudorys ?: return false
            finalizeLiftConicToPlane(state, conic, plane)
            state.liftingConicPudorys = null
        }

        "lift_conic_select_plane_narys" -> {
            val conic = state.liftingConicNarys ?: return false
            finalizeLiftConicToPlaneFromNarys(state, conic, plane)
            state.liftingConicNarys = null
        }

        "lift_conic_select_plane_bokorys" -> {
            val conic = state.liftingConicBokorys ?: return false
            finalizeLiftConicToPlaneFromBokorys(state, conic, plane)
            state.liftingConicBokorys = null
        }

        "lift_parabola_select_plane_pudorys" -> {
            val conic = state.liftingConicPudorys ?: return false
            finalizeLiftParabolaToPlaneFromPudorys(state, conic, plane)
            state.liftingConicPudorys = null
        }

        "lift_parabola_select_plane_narys" -> {
            val conic = state.liftingConicNarys ?: return false
            finalizeLiftParabolaToPlaneFromNarys(state, conic, plane)
            state.liftingConicNarys = null
        }

        "lift_parabola_select_plane_bokorys" -> {
            val conic = state.liftingConicBokorys ?: return false
            finalizeLiftParabolaToPlaneFromBokorys(state, conic, plane)
            state.liftingConicBokorys = null
        }

        "lift_hyperbola_select_plane_pudorys" -> {
            val conic = state.liftingConicPudorys ?: return false
            finalizeLiftHyperbolaToPlaneFromPudorys(state, conic, plane)
            state.liftingConicPudorys = null
        }

        "lift_hyperbola_select_plane_narys" -> {
            val conic = state.liftingConicNarys ?: return false
            finalizeLiftHyperbolaToPlaneFromNarys(state, conic, plane)
            state.liftingConicNarys = null
        }

        "lift_hyperbola_select_plane_bokorys" -> {
            val conic = state.liftingConicBokorys ?: return false
            finalizeLiftHyperbolaToPlaneFromBokorys(state, conic, plane)
            state.liftingConicBokorys = null
        }

        else -> return false
    }

    state.selectedPlanes.clear()
    resetStavu(state)
    return true
}

fun decideObjectForLift (state: MongeState) {
    if (state.projectionPhase == "ready_planelift" && state.drawobjects == Mongeobjects.PLANE_LIFT) {
        val plane = state.selectedPlaneForCircle ?: return
        val eq = plane.equation ?: return
        val pointp = state.selectedPointsPudorys.singleOrNull()
        if (pointp != null) {
            PudorysPointLift(state, eq, pointp)
        }
        val pointn = state.selectedPointsNarys.firstOrNull()
        if (pointn != null) {
            NarysPointLift(state, eq, pointn)
        }
        val linep = state.selectedLinesPudorys.firstOrNull()
        if (linep != null) {
            if (linep is Line3DProjectionPudorys){ PudorysLineLift(state, eq, linep)}
            else return
        }
        val linen = state.selectedLinesNarys.firstOrNull()
        if (linen != null) {
            if (linen is Line3DProjectionNarys){ NarysLineLift(state, eq, linen)}
            else return
        }
        val segmentp = state.selectedSegmentsPudorys.firstOrNull()
        if (segmentp != null) {
            when (segmentp){
                is Segment2DPudorys ->     PudorysSegmentLift(state, eq, segmentp)
                else -> return
            }
        }
        val segmentn = state.selectedSegmentsNarys.firstOrNull()
        if (segmentn != null) {
            when (segmentn){
                is Segment2DNarys -> NarysSegmentLift(state, eq, segmentn)
                else -> return
            }

        }
        val pointb = state.selectedPointsBokorys.firstOrNull()
        if (pointb != null) {
            BokorysPointLift(state, eq, pointb)
        }
        val pointa = state.selectedPointsAxo.firstOrNull()
        if (pointa != null) {
            AxoPointLift(state, plane, pointa)
        }
        val lineb = state.selectedLinesBokorys.firstOrNull()
        if (lineb != null) {
            if (lineb is Line3DProjectionBokorys) { BokorysLineLift(state, eq, lineb) }
            else return
        }
        val linea = state.selectedLinesAxo.firstOrNull()
        if (linea != null) {
            AxoLineLift(state, plane, linea)
        }
        val segmentb = state.selectedSegmentsBokorys.firstOrNull()
        if (segmentb != null) {
            when (segmentb){
                is Segment2DBokorys -> BokorysSegmentLift(state, eq, segmentb)
                else -> return
            }
        }
        val segmenta = state.selectedSegmentsAxo.firstOrNull()
        if (segmenta != null) {
            AxoSegmentLift(state, plane, segmenta)
        }


        when (state.projectionMode) {
            ProjectionMode.AXO -> {
                val conicproj: ConicSection2D? =
                    state.selectedConicsPudorys.firstOrNull() ?: state.selectedConicsNarys.firstOrNull()
                    ?: state.selectedConicsBokorys.firstOrNull()
                val selectedParentId = conicproj?.let { it.parentId ?: it.parent?.id }
                    ?: state.selectedConicsAxo.firstOrNull()?.let { it.parentId ?: it.parent?.id }
                if (selectedParentId != null) {
                    val conic3D = conicproj?.parent ?: state.selectedConicsAxo.firstOrNull()?.parent
                        ?: state.conics3D.find { it.id == selectedParentId }
                        ?: return
                    val typeProjection = conicproj
                        ?: state.conicsPudorys.firstOrNull { it.parentId == selectedParentId || it.parent?.id == selectedParentId }
                        ?: state.conicsNarys.firstOrNull { it.parentId == selectedParentId || it.parent?.id == selectedParentId }
                        ?: state.conicsBokorys.firstOrNull { it.parentId == selectedParentId || it.parent?.id == selectedParentId }
                        ?: return
                    val conicType = getConicType(
                        typeProjection,
                        state.conicInputPointsPudorys,
                        state.conicInputPointsNarys,
                        state.conicInputPointsBokorys,
                        state.hyperbolaInputsPudorys,
                        state.hyperbolaInputsNarys,
                        state.hyperbolaInputsBokorys,
                    )
                    when (conicType) {
                        "Elipsa" -> {
                            val conicp = state.conicsPudorys.firstOrNull{it.parentId == conic3D.id}
                            val conicn = state.conicsNarys.firstOrNull{it.parentId == conic3D.id }
                            val conicb = state.conicsBokorys.firstOrNull{it.parentId == conic3D.id}

                            if (conicp != null && conicp.isDegenerate == false) {finalizeLiftConicToPlane(state, conicp, plane,mongeLift=false)}
                            else if (conicn != null && conicn.isDegenerate == false) {finalizeLiftConicToPlaneFromNarys(state, conicn, plane,mongeLift=false)}
                            else if (conicb != null && conicb.isDegenerate == false) {finalizeLiftConicToPlaneFromBokorys(state, conicb, plane,mongeLift=false)}
                        }
                        "Parabola" -> {
                            val conicp = state.conicsPudorys.firstOrNull{it.parentId == conic3D.id}
                            val conicn = state.conicsNarys.firstOrNull{it.parentId == conic3D.id }
                            val conicb = state.conicsBokorys.firstOrNull{it.parentId == conic3D.id}

                            if (conicp != null && conicp.isDegenerate == false) {finalizeLiftParabolaToPlaneFromPudorys(state, conicp, plane)}
                            else if (conicn != null && conicn.isDegenerate == false) {finalizeLiftParabolaToPlaneFromNarys(state, conicn, plane)}
                            else if (conicb != null && conicb.isDegenerate == false) {finalizeLiftParabolaToPlaneFromBokorys(state, conicb, plane)}
                        }
                        "Kružnice" -> {}
                        "Hyperbola" ->{
                            val conicp = state.conicsPudorys.firstOrNull{it.parentId == conic3D.id}
                            val conicn = state.conicsNarys.firstOrNull{it.parentId == conic3D.id }
                            val conicb = state.conicsBokorys.firstOrNull{it.parentId == conic3D.id}

                            if (conicp != null && conicp.isDegenerate == false) {finalizeLiftHyperbolaToPlaneFromPudorys(state, conicp, plane, mongeLift=false)}
                            else if (conicn != null && conicn.isDegenerate == false) {finalizeLiftHyperbolaToPlaneFromNarys(state, conicn, plane, mongeLift=false)}
                            else if (conicb != null && conicb.isDegenerate == false) {finalizeLiftHyperbolaToPlaneFromBokorys(state, conicb, plane, mongeLift=false)}
                        }
                    }
                } else {
                    // volné 2D koniky (bez parenta) v Axo – směruj do routeru (p/n/b/axo)
                    conicPlaneLiftRouter(state, plane)
                }
            }

            ProjectionMode.MONGE, ProjectionMode.KOTO -> {
                conicPlaneLiftRouter(state, plane)
            }
            ProjectionMode.PLANE -> {
                println("V PLANE nelze")
            }
        }
        state.selectedPlaneForCircle = null
        resetStavu(state)
    }
}

fun conicPlaneLiftRouter (state: MongeState, plane: Plane3D) {
    val mongeLift = isMongeLift(state)
    val conicp = state.selectedConicsPudorys.firstOrNull()
    if (conicp != null) {
        val conicType = getConicType(
            conicp,
            state.conicInputPointsPudorys,
            state.conicInputPointsNarys,
            state.conicInputPointsBokorys,
            state.hyperbolaInputsPudorys,
            state.hyperbolaInputsNarys,
            state.hyperbolaInputsBokorys,
        )
        when (conicType) {
            "Elipsa" -> finalizeLiftConicToPlane(state, conicp, plane, mongeLift)
            "Kružnice" -> {}
            "Hyperbola" -> finalizeLiftHyperbolaToPlaneFromPudorys(state, conicp, plane)
            "Parabola" -> finalizeLiftParabolaToPlaneFromPudorys(state, conicp, plane)
        }

    }
    val conicn = state.selectedConicsNarys.firstOrNull()
    if (conicn != null) {
        val conicType = getConicType(
            conicn,
            state.conicInputPointsPudorys,
            state.conicInputPointsNarys,
            state.conicInputPointsBokorys,
            state.hyperbolaInputsPudorys,
            state.hyperbolaInputsNarys,
            state.hyperbolaInputsBokorys,
        )
        when (conicType) {
            "Elipsa" -> finalizeLiftConicToPlaneFromNarys(state, conicn, plane, mongeLift)
            "Kružnice" -> {}
            "Hyperbola" -> finalizeLiftHyperbolaToPlaneFromNarys(state, conicn, plane)
            "Parabola" -> finalizeLiftParabolaToPlaneFromNarys(state, conicn, plane)
        }
    }
    // Bokorys zdroj (zatím jen elipsa; parabola/hyperbola z bokorysu = follow-up)
    val conicb = state.selectedConicsBokorys.firstOrNull()
    if (conicb != null) {
        val conicType = getConicType(
            conicb,
            state.conicInputPointsPudorys,
            state.conicInputPointsNarys,
            state.conicInputPointsBokorys,
            state.hyperbolaInputsPudorys,
            state.hyperbolaInputsNarys,
            state.hyperbolaInputsBokorys,
        )
        when (conicType) {
            "Elipsa" -> finalizeLiftConicToPlaneFromBokorys(state, conicb, plane, mongeLift)
            "Parabola" -> finalizeLiftParabolaToPlaneFromBokorys(state, conicb, plane)
            "Hyperbola" -> finalizeLiftHyperbolaToPlaneFromBokorys(state, conicb, plane, mongeLift)
            else -> {}
        }
    }
    // Axo zdroj (getConicType axo nezná -> klasifikace přes input mapy)
    val conica = state.selectedConicsAxo.firstOrNull()
    if (conica != null) {
        val isParabola = state.conicInputPointsAxo[conica.id]?.third == Offset.Unspecified
        val isHyperbola = state.hyperbolaInputsAxo.containsKey(conica.id)
        val isCircle = kotlin.math.abs(conica.b) < 1e-5f && kotlin.math.abs(conica.a - conica.c) < 1e-5f
        if (isParabola) {
            finalizeLiftParabolaToPlaneFromAxo(state, conica, plane)
        } else if (!isHyperbola && !isCircle) {
            finalizeLiftConicToPlaneFromAxo(state, conica, plane, mongeLift)
        }
    }
}
/**
 * Doplní k 3D bodu chybějící průměty (kromě [skip], což je zdrojový průmět, který se jen napojuje).
 * showInAxo nastaví dle [show]. Axonometrický průmět vznikne jen pokud existuje axo basis.
 */
private fun addComplementaryPointProjections(
    state: MongeState,
    p3: Point3D,
    show: LiftShow,
    skip: String,
    name: String?
) {
    if (skip != "pudorys") state.pointsPudorys.add(
        Point3DPudorys(p3.x, p3.y, name = name, parent = p3, creationIndex = allocIndex(state), showInAxoInitial = show.pud)
    )
    if (skip != "narys") state.pointsNarys.add(
        Point3DNarys(p3.x, p3.z, name = name, parent = p3, creationIndex = allocIndex(state), showInAxoInitial = show.nar)
    )
    if (skip != "bokorys") state.pointsBokorys.add(
        Point3DBokorys(p3.y, p3.z, name = name, parent = p3, creationIndex = allocIndex(state), showInAxoInitial = show.bok)
    )
    val basis = state.basis
    if (skip != "axo" && basis != null) {
        val axoLocal = projectPoint3DToAxoLocal(Offset3D(p3.x, p3.y, p3.z), basis)
        state.pointsAxo.add(
            Point3DAxo(x = axoLocal.x, y = axoLocal.y, name = name, parent = p3, creationIndex = allocIndex(state), showInAxoInitial = show.axo)
        )
    }
}

fun PudorysPointLift(state: MongeState, eq: PlaneEquation, p: Point3DPudorys) {
    val point = liftPudorysToPlane(p.x, p.y, eq) ?: return
    val point3D = Point3D(p.x, p.y, point.z, name = p.name ?: "", color = p.color, width = p.width, creationIndex = allocIndex(state))
    val show = liftShow("pudorys", isMongeLift(state))
    state.sharedPoints3D.add(point3D)
    p.parent = point3D
    p.showInAxo = show.pud
    addComplementaryPointProjections(state, point3D, show, skip = "pudorys", name = p.name)
    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
fun NarysPointLift(state: MongeState, eq: PlaneEquation, n: Point3DNarys) {
    val lifted = liftNarysToPlane(n.x, n.z, eq) ?: return
    val point3D = Point3D(n.x, lifted.y, n.z, name = n.name ?: "", color = n.color, width = n.width, creationIndex = allocIndex(state))
    val show = liftShow("narys", isMongeLift(state))
    state.sharedPoints3D.add(point3D)
    n.parent = point3D
    n.showInAxo = show.nar
    addComplementaryPointProjections(state, point3D, show, skip = "narys", name = n.name)
    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
fun BokorysPointLift(state: MongeState, eq: PlaneEquation, b: Point3DBokorys) {
    val lifted = liftBokorysToPlane(b.y, b.z, eq) ?: return
    val point3D = Point3D(lifted.x, b.y, b.z, name = b.name ?: "", color = b.color, width = b.width, creationIndex = allocIndex(state))
    val show = liftShow("bokorys", isMongeLift(state))
    state.sharedPoints3D.add(point3D)
    b.parent = point3D
    b.showInAxo = show.bok
    addComplementaryPointProjections(state, point3D, show, skip = "bokorys", name = b.name)
    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
fun AxoPointLift(state: MongeState, plane: Plane3D, a: Point3DAxo) {
    val lifted = liftAxoOverlayPointToPlane(Offset(a.x, a.y), plane, state) ?: return
    val point3D = Point3D(lifted.x, lifted.y, lifted.z, name = a.name ?: "", color = a.color, width = a.width, creationIndex = allocIndex(state))
    val show = liftShow("axo", isMongeLift(state))
    state.sharedPoints3D.add(point3D)
    a.parent = point3D
    a.showInAxo = show.axo
    addComplementaryPointProjections(state, point3D, show, skip = "axo", name = a.name)
    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
/**
 * Doplní k 3D přímce chybějící průměty (kromě [skip] = zdroj, který se jen napojuje).
 */
private fun addComplementaryLineProjections(
    state: MongeState,
    line3D: Line3D,
    show: LiftShow,
    skip: String
) {
    val x0 = line3D.start.x; val y0 = line3D.start.y; val z0 = line3D.start.z
    val dx = line3D.direction.x; val dy = line3D.direction.y; val dz = line3D.direction.z
    if (skip != "pudorys") state.lines3DPudorys.add(
        Line3DProjectionPudorys(
            point = Point3DPudorys(x0, y0), direction = Offset(dx, dy),
            localName = line3D.name, parent = line3D,
            localColor = line3D.color, localStrokeWidth = line3D.strokeWidth, localLineStyle = line3D.lineStyle,
            creationIndex = allocIndex(state), showInAxoInitial = show.pud
        )
    )
    if (skip != "narys") state.lines3DNarys.add(
        Line3DProjectionNarys(
            point = Point3DNarys(x0, z0), direction = Offset(dx, dz),
            localName = line3D.name, parent = line3D,
            localColor = line3D.color, localStrokeWidth = line3D.strokeWidth, localLineStyle = line3D.lineStyle,
            creationIndex = allocIndex(state), showInAxoInitial = show.nar
        )
    )
    if (skip != "bokorys") state.lines3DBokorys.add(
        Line3DProjectionBokorys(
            point = Point3DBokorys(y0, z0), direction = Offset(dy, dz),
            localName = line3D.name, parent = line3D,
            localColor = line3D.color, localStrokeWidth = line3D.strokeWidth, localLineStyle = line3D.lineStyle,
            creationIndex = allocIndex(state), showInAxoInitial = show.bok
        )
    )
    val basis = state.basis
    if (skip != "axo" && basis != null) {
        val pAxo = projectPoint3DToAxoLocal(Offset3D(x0, y0, z0), basis)
        val dAxo = projectDirection3DToAxo(Offset3D(dx, dy, dz), basis)
        state.lines3DAxo.add(
            Line3DProjectionAxo(
                p = Point3DAxo(x = pAxo.x, y = pAxo.y), dir = dAxo,
                localName = line3D.name, parent = line3D,
                localColor = line3D.color, localStrokeWidth = line3D.strokeWidth, localLineStyle = line3D.lineStyle,
                creationIndex = allocIndex(state), showInAxoInitial = show.axo
            )
        )
    }
}

fun PudorysLineLift(state: MongeState, eq: PlaneEquation, line: Line3DProjectionPudorys) {

    // --- 1) vezmi bod a směr z půdorysu (uprav dle tvých polí) ---
    val x0 = line.point.x
    val y0 = line.point.y
    val dx = line.direction.x
    val dy = line.direction.y

    // ochrany
    val len2 = dx*dx + dy*dy
    if (len2 < 1e-10f) return

    // --- 2) z roviny dopočti z0 a z ---
    val a = eq.a
    val b = eq.b
    val c = eq.c
    val d = eq.d

    // když c==0, z z rovnice nevypočítáš jednoznačně (rovina "svislá" vůči z)
    if (kotlin.math.abs(c) < 1e-8f) return

    val z0 = -(a * x0 + b * y0 + d) / c
    val dz = -(a * dx + b * dy) / c

    // --- 3) vytvoř 3D přímku ---
    val line3D = Line3D(
        start = Point3D(x0, y0, z0, ""),
        direction = Offset3D(dx, dy, dz),
        name = line.name?.removeSuffix("\u2081") ?: "",
        color = line.color,
        strokeWidth = line.strokeWidth,
        lineStyle = line.lineStyle, creationIndex = allocIndex(state)
    )

    val show = liftShow("pudorys", isMongeLift(state))
    state.lines3D.add(line3D)
    line.parent = line3D
    line.showInAxo = show.pud
    addComplementaryLineProjections(state, line3D, show, skip = "pudorys")
    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
fun NarysLineLift(state: MongeState, eq: PlaneEquation, line: Line3DProjectionNarys) {

    // --- 1) vezmi bod a směr z nárysu (x,z) ---
    val x0 = line.point.x
    val z0 = line.point.z
    val dx = line.direction.x
    val dz = line.direction.y   // pozor: v nárysu je Offset(x, z) -> .y je "z"

    // ochrany
    val len2 = dx * dx + dz * dz
    if (len2 < 1e-10f) return

    // --- 2) z roviny dopočti y0 a y ---
    val a = eq.a
    val b = eq.b
    val c = eq.c
    val d = eq.d

    // když b==0, y z rovnice nevypočítáš jednoznačně (rovina "svislá" vůči y)
    if (kotlin.math.abs(b) < 1e-8f) return

    // rovina: a*x + b*y + c*z + d = 0  =>  y = -(a*x + c*z + d)/b
    val y0 = -(a * x0 + c * z0 + d) / b

    // derivace podél směru: a*x + b*y + c*z = 0 => y = -(a*x + c*z)/b
    val dy = -(a * dx + c * dz) / b

    // --- 3) vytvoř 3D přímku ---
    val line3D = Line3D(
        Point3D(x0, y0, z0, ""),
        Offset3D(dx, dy, dz),
        line.name?.removeSuffix("\u2082") ?: "",
        color = line.color,
        strokeWidth = line.strokeWidth,
        lineStyle = line.lineStyle, creationIndex = allocIndex(state)
    )

    val show = liftShow("narys", isMongeLift(state))
    state.lines3D.add(line3D)
    line.parent = line3D
    line.showInAxo = show.nar
    addComplementaryLineProjections(state, line3D, show, skip = "narys")
    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
fun BokorysLineLift(state: MongeState, eq: PlaneEquation, line: Line3DProjectionBokorys) {
    val y0 = line.point.y
    val z0 = line.point.z
    val dy = line.direction.x   // bokorys: Offset(y, z) -> .x je "y"
    val dz = line.direction.y   // .y je "z"
    if (dy * dy + dz * dz < 1e-10f) return
    val a = eq.a; val b = eq.b; val c = eq.c; val d = eq.d
    if (kotlin.math.abs(a) < 1e-8f) return
    val x0 = -(b * y0 + c * z0 + d) / a
    val dx = -(b * dy + c * dz) / a

    val line3D = Line3D(
        Point3D(x0, y0, z0, ""),
        Offset3D(dx, dy, dz),
        line.name?.removeSuffix("₃") ?: "",
        color = line.color,
        strokeWidth = line.strokeWidth,
        lineStyle = line.lineStyle, creationIndex = allocIndex(state)
    )
    val show = liftShow("bokorys", isMongeLift(state))
    state.lines3D.add(line3D)
    line.parent = line3D
    line.showInAxo = show.bok
    addComplementaryLineProjections(state, line3D, show, skip = "bokorys")
    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
fun AxoLineLift(state: MongeState, plane: Plane3D, line: Line3DProjectionAxo) {
    val a2 = Offset(line.p.x, line.p.y)
    val b2 = Offset(line.p.x + line.dir.x, line.p.y + line.dir.y)
    val a3 = liftAxoOverlayPointToPlane(a2, plane, state) ?: return
    val b3 = liftAxoOverlayPointToPlane(b2, plane, state) ?: return
    val dx = b3.x - a3.x; val dy = b3.y - a3.y; val dz = b3.z - a3.z
    if (dx * dx + dy * dy + dz * dz < 1e-10f) return

    val line3D = Line3D(
        Point3D(a3.x, a3.y, a3.z, ""),
        Offset3D(dx, dy, dz),
        line.name?.removeSuffix("ₐ") ?: "",
        color = line.color,
        strokeWidth = line.strokeWidth,
        lineStyle = line.lineStyle, creationIndex = allocIndex(state)
    )
    val show = liftShow("axo", isMongeLift(state))
    state.lines3D.add(line3D)
    line.parent = line3D
    line.showInAxo = show.axo
    addComplementaryLineProjections(state, line3D, show, skip = "axo")
    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
/**
 * Doplní k 3D úsečce chybějící průměty (kromě [skip] = zdroj). Pro každý průmět vytvoří
 * koncové body navázané na 3D body [a3]/[b3] a segment navázaný na [seg3D].
 */
private fun addComplementarySegmentProjections(
    state: MongeState,
    seg3D: Segment3D,
    a3: Point3D,
    b3: Point3D,
    show: LiftShow,
    skip: String,
    name: String?,
    color: Color,
    lineStyle: LineStyle,
    strokeWidth: Float
) {
    if (skip != "pudorys") {
        val pA = Point3DPudorys(a3.x, a3.y, name = "", parent = a3, isSegmentEndpoint = true, creationIndex = allocIndex(state), showInAxoInitial = show.pud)
        val pB = Point3DPudorys(b3.x, b3.y, name = "", parent = b3, isSegmentEndpoint = true, creationIndex = allocIndex(state), showInAxoInitial = show.pud)
        val seg = Segment2DPudorys(start = pA, end = pB, name = name, parent = seg3D, localColor = color, localLineStyle = lineStyle, localStrokeWidth = strokeWidth, parentId = seg3D.id, creationIndex = allocIndex(state), showInAxoInitial = show.pud)
        pA.parentSegment = seg; pB.parentSegment = seg
        state.pointsPudorys.add(pA); state.pointsPudorys.add(pB)
        state.segmentsPudorys.add(seg)
    }
    if (skip != "narys") {
        val nA = Point3DNarys(a3.x, a3.z, name = "", parent = a3, isSegmentEndpoint = true, creationIndex = allocIndex(state), showInAxoInitial = show.nar)
        val nB = Point3DNarys(b3.x, b3.z, name = "", parent = b3, isSegmentEndpoint = true, creationIndex = allocIndex(state), showInAxoInitial = show.nar)
        val seg = Segment2DNarys(start = nA, end = nB, name = name, parent = seg3D, localColor = color, localLineStyle = lineStyle, localStrokeWidth = strokeWidth, parentId = seg3D.id, creationIndex = allocIndex(state), showInAxoInitial = show.nar)
        nA.parentSegment = seg; nB.parentSegment = seg
        state.pointsNarys.add(nA); state.pointsNarys.add(nB)
        state.segmentsNarys.add(seg)
    }
    if (skip != "bokorys") {
        val bA = Point3DBokorys(a3.y, a3.z, name = "", parent = a3, isSegmentEndpoint = true, creationIndex = allocIndex(state), showInAxoInitial = show.bok)
        val bB = Point3DBokorys(b3.y, b3.z, name = "", parent = b3, isSegmentEndpoint = true, creationIndex = allocIndex(state), showInAxoInitial = show.bok)
        val seg = Segment2DBokorys(start = bA, end = bB, name = name, parent = seg3D, localColor = color, localLineStyle = lineStyle, localStrokeWidth = strokeWidth, parentId = seg3D.id, creationIndex = allocIndex(state), showInAxoInitial = show.bok)
        bA.parentSegment = seg; bB.parentSegment = seg
        state.pointsBokorys.add(bA); state.pointsBokorys.add(bB)
        state.segmentsBokorys.add(seg)
    }
    val basis = state.basis
    if (skip != "axo" && basis != null) {
        val aAxo = projectPoint3DToAxoLocal(Offset3D(a3.x, a3.y, a3.z), basis)
        val bAxo = projectPoint3DToAxoLocal(Offset3D(b3.x, b3.y, b3.z), basis)
        val axA = Point3DAxo(x = aAxo.x, y = aAxo.y, name = "", parent = a3, isSegmentEndpoint = true, creationIndex = allocIndex(state), showInAxoInitial = show.axo)
        val axB = Point3DAxo(x = bAxo.x, y = bAxo.y, name = "", parent = b3, isSegmentEndpoint = true, creationIndex = allocIndex(state), showInAxoInitial = show.axo)
        val seg = Segment2DAxo(start = axA, end = axB, name = name, parent = seg3D, localColor = color, localLineStyle = lineStyle, localStrokeWidth = strokeWidth, parentId = seg3D.id, creationIndex = allocIndex(state), showInAxoInitial = show.axo)
        axA.parentSegment = seg; axB.parentSegment = seg
        state.pointsAxo.add(axA); state.pointsAxo.add(axB)
        state.segmentsAxo.add(seg)
    }
}

fun PudorysSegmentLift(state: MongeState, eq: PlaneEquation, segPudExisting: Segment2DPudorys) {
    if (segPudExisting.parent != null) return
    val a = eq.a; val b = eq.b; val c = eq.c; val d = eq.d
    if (kotlin.math.abs(c) < 1e-8f) return

    val xA = segPudExisting.start.x; val yA = segPudExisting.start.y
    val xB = segPudExisting.end.x;   val yB = segPudExisting.end.y
    val zA = -(a * xA + b * yA + d) / c
    val zB = -(a * xB + b * yB + d) / c

    val A3 = Point3D(xA, yA, zA, name = "")
    val B3 = Point3D(xB, yB, zB, name = "")
    val seg3D = Segment3D(
        start = A3, end = B3, name = segPudExisting.name ?: "",
        color = segPudExisting.color, strokeWidth = segPudExisting.strokeWidth,
        lineStyle = segPudExisting.lineStyle, creationIndex = allocIndex(state)
    )
    val show = liftShow("pudorys", isMongeLift(state))

    // === REUSE půdorysu ===
    segPudExisting.parent = seg3D
    segPudExisting.parentId = seg3D.id
    segPudExisting.showInAxo = show.pud
    segPudExisting.start.parent = A3
    segPudExisting.end.parent = B3
    segPudExisting.start.parentSegment = segPudExisting
    segPudExisting.end.parentSegment   = segPudExisting
    segPudExisting.start.showInAxo = show.pud
    segPudExisting.end.showInAxo = show.pud

    state.sharedPoints3D.add(A3)
    state.sharedPoints3D.add(B3)
    if (state.pointsPudorys.none { it.id == segPudExisting.start.id }) state.pointsPudorys.add(segPudExisting.start)
    if (state.pointsPudorys.none { it.id == segPudExisting.end.id })   state.pointsPudorys.add(segPudExisting.end)
    addSegment3DAndDetectSolids(state, seg3D)

    addComplementarySegmentProjections(state, seg3D, A3, B3, show, skip = "pudorys", name = segPudExisting.name, color = segPudExisting.color, lineStyle = segPudExisting.lineStyle, strokeWidth = segPudExisting.strokeWidth)

    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}

fun NarysSegmentLift(state: MongeState, eq: PlaneEquation, segNarExisting: Segment2DNarys) {
    if (segNarExisting.parent != null) return
    val a = eq.a; val b = eq.b; val c = eq.c; val d = eq.d
    if (kotlin.math.abs(b) < 1e-8f) return

    val xA = segNarExisting.start.x; val zA = segNarExisting.start.z
    val xB = segNarExisting.end.x;   val zB = segNarExisting.end.z
    val yA = -(a * xA + c * zA + d) / b
    val yB = -(a * xB + c * zB + d) / b

    val A3 = Point3D(xA, yA, zA, name = "")
    val B3 = Point3D(xB, yB, zB, name = "")
    val seg3D = Segment3D(
        start = A3, end = B3, name = segNarExisting.name ?: "",
        color = segNarExisting.color, strokeWidth = segNarExisting.strokeWidth,
        lineStyle = segNarExisting.lineStyle, creationIndex = allocIndex(state)
    )
    val show = liftShow("narys", isMongeLift(state))

    // === REUSE nárysu ===
    segNarExisting.parent = seg3D
    segNarExisting.parentId = seg3D.id
    segNarExisting.showInAxo = show.nar
    segNarExisting.start.parent = A3
    segNarExisting.end.parent = B3
    segNarExisting.start.parentSegment = segNarExisting
    segNarExisting.end.parentSegment   = segNarExisting
    segNarExisting.start.showInAxo = show.nar
    segNarExisting.end.showInAxo = show.nar

    state.sharedPoints3D.add(A3)
    state.sharedPoints3D.add(B3)
    if (state.pointsNarys.none { it.id == segNarExisting.start.id }) state.pointsNarys.add(segNarExisting.start)
    if (state.pointsNarys.none { it.id == segNarExisting.end.id })   state.pointsNarys.add(segNarExisting.end)
    addSegment3DAndDetectSolids(state, seg3D)

    addComplementarySegmentProjections(state, seg3D, A3, B3, show, skip = "narys", name = segNarExisting.name, color = segNarExisting.color, lineStyle = segNarExisting.lineStyle, strokeWidth = segNarExisting.strokeWidth)

    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}

fun BokorysSegmentLift(state: MongeState, eq: PlaneEquation, segBokExisting: Segment2DBokorys) {
    if (segBokExisting.parent != null) return
    val a = eq.a; val b = eq.b; val c = eq.c; val d = eq.d
    if (kotlin.math.abs(a) < 1e-8f) return

    val yA = segBokExisting.start.y; val zA = segBokExisting.start.z
    val yB = segBokExisting.end.y;   val zB = segBokExisting.end.z
    val xA = -(b * yA + c * zA + d) / a
    val xB = -(b * yB + c * zB + d) / a

    val A3 = Point3D(xA, yA, zA, name = "")
    val B3 = Point3D(xB, yB, zB, name = "")
    val seg3D = Segment3D(
        start = A3, end = B3, name = segBokExisting.name ?: "",
        color = segBokExisting.color, strokeWidth = segBokExisting.strokeWidth,
        lineStyle = segBokExisting.lineStyle, creationIndex = allocIndex(state)
    )
    val show = liftShow("bokorys", isMongeLift(state))

    // === REUSE bokorysu ===
    segBokExisting.parent = seg3D
    segBokExisting.parentId = seg3D.id
    segBokExisting.showInAxo = show.bok
    segBokExisting.start.parent = A3
    segBokExisting.end.parent = B3
    segBokExisting.start.parentSegment = segBokExisting
    segBokExisting.end.parentSegment   = segBokExisting
    segBokExisting.start.showInAxo = show.bok
    segBokExisting.end.showInAxo = show.bok

    state.sharedPoints3D.add(A3)
    state.sharedPoints3D.add(B3)
    if (state.pointsBokorys.none { it.id == segBokExisting.start.id }) state.pointsBokorys.add(segBokExisting.start)
    if (state.pointsBokorys.none { it.id == segBokExisting.end.id })   state.pointsBokorys.add(segBokExisting.end)
    addSegment3DAndDetectSolids(state, seg3D)

    addComplementarySegmentProjections(state, seg3D, A3, B3, show, skip = "bokorys", name = segBokExisting.name, color = segBokExisting.color, lineStyle = segBokExisting.lineStyle, strokeWidth = segBokExisting.strokeWidth)

    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}

fun AxoSegmentLift(state: MongeState, plane: Plane3D, segAxoExisting: Segment2DAxo) {
    if (segAxoExisting.parent != null) return
    val a3off = liftAxoOverlayPointToPlane(Offset(segAxoExisting.start.x, segAxoExisting.start.y), plane, state) ?: return
    val b3off = liftAxoOverlayPointToPlane(Offset(segAxoExisting.end.x, segAxoExisting.end.y), plane, state) ?: return

    val A3 = Point3D(a3off.x, a3off.y, a3off.z, name = "")
    val B3 = Point3D(b3off.x, b3off.y, b3off.z, name = "")
    val seg3D = Segment3D(
        start = A3, end = B3, name = segAxoExisting.name ?: "",
        color = segAxoExisting.color, strokeWidth = segAxoExisting.strokeWidth,
        lineStyle = segAxoExisting.lineStyle, creationIndex = allocIndex(state)
    )
    val show = liftShow("axo", isMongeLift(state))

    // === REUSE axo ===
    segAxoExisting.parent = seg3D
    segAxoExisting.parentId = seg3D.id
    segAxoExisting.showInAxo = show.axo
    segAxoExisting.start.parent = A3
    segAxoExisting.end.parent = B3
    segAxoExisting.start.parentSegment = segAxoExisting
    segAxoExisting.end.parentSegment   = segAxoExisting
    segAxoExisting.start.showInAxo = show.axo
    segAxoExisting.end.showInAxo = show.axo

    state.sharedPoints3D.add(A3)
    state.sharedPoints3D.add(B3)
    if (state.pointsAxo.none { it.id == segAxoExisting.start.id }) state.pointsAxo.add(segAxoExisting.start)
    if (state.pointsAxo.none { it.id == segAxoExisting.end.id })   state.pointsAxo.add(segAxoExisting.end)
    addSegment3DAndDetectSolids(state, seg3D)

    addComplementarySegmentProjections(state, seg3D, A3, B3, show, skip = "axo", name = segAxoExisting.name, color = segAxoExisting.color, lineStyle = segAxoExisting.lineStyle, strokeWidth = segAxoExisting.strokeWidth)

    commitSnapshot(state)
    repeatCons(state)
    resetStavu(state)
}
