package monge.input.planeobjects.conicsections

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import draw.mongescreen.objects.axo.projectPoint3DToAxoLocal
import model.Mongeobjects
import model.Offset3D
import model.Point3D
import model.classes.*
import model.normalize
import monge.input.axo.conixections.liftAxoOverlayPointToPlane
import monge.input.conixections.conjugateDiameterInputFromRadii
import serialization.commitSnapshot
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import utils.allocIndex
import kotlin.math.abs
import kotlin.math.sqrt

private const val PLANE_ELLIPSE_EPS = 1e-6f

private data class PlaneEllipseGeometry(
    val axis1: Offset3D,
    val axis2: Offset3D,
    val matrix: Matrix3x3,
    val isDegenerate: Boolean,
    val isLineDegenerate: Boolean
)

private fun Offset3D.len(): Float = sqrt(x * x + y * y + z * z)

private fun unitInPlane(normal: Offset3D): Offset3D {
    val n = normal.normalize()
    val candidate = if (abs(n.z) < 0.9f) n cross Offset3D(0f, 0f, 1f) else n cross Offset3D(0f, 1f, 0f)
    val len = candidate.len()
    return if (len < PLANE_ELLIPSE_EPS) Offset3D(1f, 0f, 0f) else candidate * (1f / len)
}

private fun planeEllipseGeometry(u1: Offset3D, u2: Offset3D, planeNormal: Offset3D): PlaneEllipseGeometry {
    val l1 = u1.len()
    val l2 = u2.len()
    val cross = u1 cross u2
    val crossLen = cross.len()
    val n = planeNormal.normalize()

    if (l1 < PLANE_ELLIPSE_EPS && l2 < PLANE_ELLIPSE_EPS) {
        val axis1 = unitInPlane(n)
        return PlaneEllipseGeometry(
            axis1 = axis1,
            axis2 = (n cross axis1).normalize(),
            matrix = Matrix3x3.fromCoefficients(1f, 0f, 1f, 0f, 0f, 0f),
            isDegenerate = true,
            isLineDegenerate = false
        )
    }

    if (l1 < PLANE_ELLIPSE_EPS || l2 < PLANE_ELLIPSE_EPS || crossLen < PLANE_ELLIPSE_EPS * l1 * l2) {
        val axis1 = (if (l1 >= l2) u1 else u2).normalize()
        val axis2Raw = n cross axis1
        return PlaneEllipseGeometry(
            axis1 = axis1,
            axis2 = if (axis2Raw.len() < PLANE_ELLIPSE_EPS) unitInPlane(n) else axis2Raw.normalize(),
            matrix = Matrix3x3.fromCoefficients(0f, 0f, 1f, 0f, 0f, 0f),
            isDegenerate = true,
            isLineDegenerate = true
        )
    }

    var normal = cross.normalize()
    if ((normal dot n) < 0f) normal = normal * -1f
    val axis1 = u1.normalize()
    val axis2 = (normal cross axis1).normalize()

    val x1 = u1 dot axis1
    val y1 = u1 dot axis2
    val x2 = u2 dot axis1
    val y2 = u2 dot axis2
    val s00 = x1 * x1 + x2 * x2
    val s01 = x1 * y1 + x2 * y2
    val s11 = y1 * y1 + y2 * y2
    val det = s00 * s11 - s01 * s01

    if (abs(det) < PLANE_ELLIPSE_EPS * PLANE_ELLIPSE_EPS) {
        return PlaneEllipseGeometry(
            axis1 = axis1,
            axis2 = axis2,
            matrix = Matrix3x3.fromCoefficients(0f, 0f, 1f, 0f, 0f, 0f),
            isDegenerate = true,
            isLineDegenerate = true
        )
    }

    return PlaneEllipseGeometry(
        axis1 = axis1,
        axis2 = axis2,
        matrix = Matrix3x3.fromCoefficients(s11 / det, -2f * s01 / det, s00 / det, 0f, 0f, -1f),
        isDegenerate = false,
        isLineDegenerate = false
    )
}

private fun unit2D(v: Offset): Offset {
    val len = v.getDistance()
    return if (len < PLANE_ELLIPSE_EPS) Offset(1f, 0f) else v / len
}

private fun ConicSectionPudorys.applyDegeneracyFrom(geometry: PlaneEllipseGeometry) {
    isDegenerate = geometry.isDegenerate
    isLineDegenerate = geometry.isLineDegenerate
    degenerateDir = if (geometry.isLineDegenerate) unit2D(Offset(geometry.axis1.x, geometry.axis1.y)) else null
}

private fun ConicSectionNarys.applyDegeneracyFrom(geometry: PlaneEllipseGeometry) {
    isDegenerate = geometry.isDegenerate
    isLineDegenerate = geometry.isLineDegenerate
    degenerateDir = if (geometry.isLineDegenerate) unit2D(Offset(geometry.axis1.x, -geometry.axis1.z)) else null
}
private fun ConicSectionBokorys.applyDegeneracyFrom(geometry: PlaneEllipseGeometry) {
    isDegenerate = geometry.isDegenerate
    isLineDegenerate = geometry.isLineDegenerate
    degenerateDir = if (geometry.isLineDegenerate) unit2D(Offset(geometry.axis1.x, -geometry.axis1.z)) else null
}
private fun ConicSectionAxo.applyDegeneracyFrom(geometry: PlaneEllipseGeometry) {
    isDegenerate = geometry.isDegenerate
    isLineDegenerate = geometry.isLineDegenerate
    degenerateDir = if (geometry.isLineDegenerate) unit2D(Offset(geometry.axis1.x, -geometry.axis1.z)) else null
}

fun handleEllipseInPlaneConstructionPudorys(logical: Offset, state: MongeState) {
    if (state.selectedPlaneForCircle == null) {
        val rememberedPlane = state.selectedPlanes.firstOrNull()

        if (state.selectedPlaneForCircle == null) {
            val rememberedPlane = state.selectedPlanes.firstOrNull()

            if (rememberedPlane != null) {
                val eq = rememberedPlane.equation
                if (eq == null) {
                    println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                    return
                }
                val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
                val isVerticalInPudorys = abs(normal.z) < 1e-3f

                if (isVerticalInPudorys) {
                    println("❌ Nelze konstruovat elipsu v půdorysu – rovina '${rememberedPlane.name}' je kolmá k půdorysně.")
                    return
                }

                state.selectedPlaneForCircle = rememberedPlane
                println("🟦 Rovina '${rememberedPlane.name}' vybrána pro konstrukci elipsy.")
            } else {
                println("⚠️ Neoznačena žádná rovina – vyber jednu kliknutím.")
                return
            }
        }
    }
        val plane = state.selectedPlaneForCircle!!
        when (state.projectionPhase) {
            "pudorys_start" -> {
                setProjectionPhase( "ellipse_plane_point1", state)
            }
        "ellipse_plane_point1" -> {
            state.pendingPoint1 = logical
            setProjectionPhase("ellipse_plane_point2", state)
        }

        "ellipse_plane_point2" -> {
            state.pendingPoint2 = logical
            setProjectionPhase("ellipse_plane_point3", state)
        }

        "ellipse_plane_point3" -> {
            val centerP = state.pendingPoint1!!
            val firstRadiusEndP = state.pendingPoint2!!
            val secondRadiusEndP = logical

            val equation = plane.equation ?: run {
                println("❌ Rovina nemá rovnici!")
                return
            }

            fun projectToPlane(x: Float, y: Float): Point3D {
                val z = -(equation.a * x + equation.b * y + equation.d) / equation.c
                return Point3D(x, y, z, name="")
            }

            val centerPt = projectToPlane(centerP.x, centerP.y)
            val firstRadiusPt = projectToPlane(firstRadiusEndP.x, firstRadiusEndP.y)
            val secondRadiusPt = projectToPlane(secondRadiusEndP.x, secondRadiusEndP.y)

            val center = Offset3D(
                centerPt.x,
                centerPt.y,
                centerPt.z
            )

            val u1 = Offset3D(
                firstRadiusPt.x - centerPt.x,
                firstRadiusPt.y - centerPt.y,
                firstRadiusPt.z - centerPt.z
            )

            val u2 = Offset3D(
                secondRadiusPt.x - centerPt.x,
                secondRadiusPt.y - centerPt.y,
                secondRadiusPt.z - centerPt.z
            )

            val geometry = planeEllipseGeometry(u1, u2, Offset3D(equation.a, equation.b, equation.c))

            val conic3D = ConicSection3D(
                p0 = center,
                u = geometry.axis1,
                v = geometry.axis2,
                matrix = geometry.matrix,
                rawName = state.inputName.ifBlank { "ε" },
                color = state.currentLineStyleSettings.color,
                strokeWidth = state.currentLineStyleSettings.strokeWidth,
                lineStyle = state.currentLineStyleSettings.style, creationIndex = allocIndex(state)
            )

            // Projekce
            val pudorysMatrix = conic3D.projectToXY()
            val coeffs1 = Matrix3x3.toCoefficients(pudorysMatrix)
            val a1 = coeffs1[0]
            val b1 = coeffs1[1]
            val c1 = coeffs1[2]
            val d1 = coeffs1[3]
            val e1 = coeffs1[4]
            val f1 = coeffs1[5]

            val narysMatrix = conic3D.projectToXZ()
            val coeffs2 = Matrix3x3.toCoefficients(narysMatrix)
            val a2 = coeffs2[0]
            val b2 = coeffs2[1]
            val c2 = coeffs2[2]
            val d2 = coeffs2[3]
            val e2 = coeffs2[4]
            val f2 = coeffs2[5]


            val pudorys = ConicSectionPudorys(
                a = a1, b = b1, c = c1, d = d1, e = e1, f = f1,
                rawName = conic3D.rawName,
                localColor = conic3D.color,
                strokeWidth = conic3D.strokeWidth,
                lineStyle = conic3D.lineStyle,
                parent = conic3D, creationIndex = allocIndex(state)
            )
            val narys = ConicSectionNarys(
                a = a2, b = b2, c = c2, d = d2, e = e2, f = f2,
                rawName = conic3D.rawName,
                localColor = conic3D.color,
                strokeWidth = conic3D.strokeWidth,
                lineStyle = conic3D.lineStyle,
                parent = conic3D, creationIndex = allocIndex(state)
            )
            pudorys.applyDegeneracyFrom(geometry)
            narys.applyDegeneracyFrom(geometry)

            state.conics3D.add(conic3D)
            state.conicsPudorys.add(pudorys)
            state.conicsNarys.add(narys)

            val pInputs = conjugateDiameterInputFromRadii(centerP, firstRadiusEndP, secondRadiusEndP)
            val centerN = Offset(centerPt.x, -centerPt.z)
            val firstRadiusEndN = Offset(firstRadiusPt.x, -firstRadiusPt.z)
            val secondRadiusEndN = Offset(secondRadiusPt.x, -secondRadiusPt.z)
            val nInputs = conjugateDiameterInputFromRadii(centerN, firstRadiusEndN, secondRadiusEndN)
            state.conicInputPointsPudorys[pudorys.id] = pInputs
            state.conicInputPointsNarys[narys.id] = nInputs
            commitSnapshot(state)

            repeatCons(state)
            setProjectionPhase("pudorys_start", state)
            state.inputName = ""
            state.selectedPlaneForCircle = null
            state.selectedPlanes.clear() // volitelné – smaže zvýraznění roviny

            println("✅ Elipsa vytvořena v rovině ${plane.name} jako ${conic3D.rawName}")
        }
    }
}
fun handleEllipseInPlaneConstructionNarys(logical: Offset, state: MongeState) {
    if (state.selectedPlaneForCircle == null) {
        val rememberedPlane = state.selectedPlanes.firstOrNull()

        if (rememberedPlane != null) {
            val eq = rememberedPlane.equation
            if (eq == null) {
                println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                return
            }

            val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
            val isVerticalInNarys = abs(normal.y) < 1e-3f

            if (isVerticalInNarys) {
                println("❌ Nelze konstruovat elipsu v nárysu – rovina '${rememberedPlane.name}' je kolmá k nárysně.")
                return
            }

            state.selectedPlaneForCircle = rememberedPlane
            println("🟦 Rovina '${rememberedPlane.name}' vybrána pro konstrukci elipsy.")
        } else {
            println("⚠️ Neoznačena žádná rovina – vyber jednu kliknutím.")
            return
        }
    }


    val plane = state.selectedPlaneForCircle!!
    val equation = plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }

    fun liftFromNarys(x: Float, z: Float): Point3D {
        val y = -(equation.a * x + equation.c * z + equation.d) / equation.b
        return Point3D(x, y, z, name = "")
    }

    when (state.projectionPhase) {
        "narys_start" -> {
            setProjectionPhase( "ellipse_plane_point1_narys", state)
        }

        "ellipse_plane_point1_narys" -> {
            state.pendingPoint1 = logical
            setProjectionPhase("ellipse_plane_point2_narys", state)
        }

        "ellipse_plane_point2_narys" -> {
            state.pendingPoint2 = logical
            setProjectionPhase("ellipse_plane_point3_narys", state)
        }

        "ellipse_plane_point3_narys" -> {
            val centerN = state.pendingPoint1!!
            val firstRadiusEndN = state.pendingPoint2!!
            val secondRadiusEndN = logical

            val centerPt = liftFromNarys(centerN.x, -centerN.y)
            val firstRadiusPt = liftFromNarys(firstRadiusEndN.x, -firstRadiusEndN.y)
            val secondRadiusPt = liftFromNarys(secondRadiusEndN.x, -secondRadiusEndN.y)

            val center = Offset3D(
                centerPt.x,
                centerPt.y,
                centerPt.z
            )

            val u1 = Offset3D(
                firstRadiusPt.x - centerPt.x,
                firstRadiusPt.y - centerPt.y,
                firstRadiusPt.z - centerPt.z
            )

            val u2 = Offset3D(
                secondRadiusPt.x - centerPt.x,
                secondRadiusPt.y - centerPt.y,
                secondRadiusPt.z - centerPt.z
            )

            val geometry = planeEllipseGeometry(u1, u2, Offset3D(equation.a, equation.b, equation.c))

            val conic3D = ConicSection3D(
                p0 = center,
                u = geometry.axis1,
                v = geometry.axis2,
                matrix = geometry.matrix,
                rawName = state.inputName.ifBlank { "ε" },
                color = state.currentLineStyleSettings.color,
                strokeWidth = state.currentLineStyleSettings.strokeWidth,
                lineStyle = state.currentLineStyleSettings.style, creationIndex = allocIndex(state)
            )

            val coeffsPudorys = Matrix3x3.toCoefficients(conic3D.projectToXY())
            val coeffsNarys = Matrix3x3.toCoefficients(conic3D.projectToXZ())

            val pudorys = ConicSectionPudorys(
                a = coeffsPudorys[0], b = coeffsPudorys[1], c = coeffsPudorys[2],
                d = coeffsPudorys[3], e = coeffsPudorys[4], f = coeffsPudorys[5],
                rawName = conic3D.rawName,
                localColor = conic3D.color,
                strokeWidth = conic3D.strokeWidth,
                lineStyle = conic3D.lineStyle,
                parent = conic3D, creationIndex = allocIndex(state)
            )

            val narys = ConicSectionNarys(
                a = coeffsNarys[0], b = coeffsNarys[1], c = coeffsNarys[2],
                d = coeffsNarys[3], e = coeffsNarys[4], f = coeffsNarys[5],
                rawName = conic3D.rawName,
                localColor = conic3D.color,
                strokeWidth = conic3D.strokeWidth,
                lineStyle = conic3D.lineStyle,
                parent = conic3D, creationIndex = allocIndex(state)
            )
            pudorys.applyDegeneracyFrom(geometry)
            narys.applyDegeneracyFrom(geometry)


            state.conics3D.add(conic3D)
            state.conicsPudorys.add(pudorys)
            state.conicsNarys.add(narys)

            val centerP = Offset(centerPt.x, centerPt.y)
            val firstRadiusEndP = Offset(firstRadiusPt.x, firstRadiusPt.y)
            val secondRadiusEndP = Offset(secondRadiusPt.x, secondRadiusPt.y)
            val pInputs = conjugateDiameterInputFromRadii(centerP, firstRadiusEndP, secondRadiusEndP)
            val nInputs = conjugateDiameterInputFromRadii(centerN, firstRadiusEndN, secondRadiusEndN)
            state.conicInputPointsPudorys[pudorys.id] = pInputs
            state.conicInputPointsNarys[narys.id] = nInputs
            commitSnapshot(state)
            repeatCons(state)
            setProjectionPhase("narys_start", state)
            state.inputName = ""
            state.selectedPlaneForCircle = null
            state.selectedPlanes.clear()

            println("✅ Elipsa vytvořena v rovině ${plane.name} jako ${conic3D.rawName}")
        }
    }
}
fun startLiftConicToPlaneFromPudorys(state: MongeState) {
    val conic = state.selectedConicsPudorys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v půdorysu.")
        return
    }

    state.liftingConicPudorys = conic

    setProjectionPhase("lift_conic_select_plane", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
fun startLiftConicToPlaneFromNarys(state: MongeState) {
    val conic = state.selectedConicsNarys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v půdorysu.")
        return
    }

    state.liftingConicNarys = conic

    setProjectionPhase("lift_conic_select_plane_narys", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
fun startLiftConicToPlaneFromBokorys(state: MongeState) {
    val conic = state.selectedConicsBokorys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v bokorysu.")
        return
    }

    state.liftingConicBokorys = conic
    setProjectionPhase("lift_conic_select_plane_bokorys", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
fun finalizeLiftConicToPlane(
    state: MongeState,
    conic: ConicSectionPudorys,
    plane: Plane3D,
    mongeLift: Boolean = true
) {
    val eq = plane.equation ?: run {
        println("❌ Vybraná rovina nemá rovnici – nelze pokračovat.")
        return
    }

    val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
    if (abs(normal.z) < 1e-3f) {
        println("❌ Rovina '${plane.name}' je kolmá k půdorysně – nelze do ní zvednout půdorysnou kuželosečku.")
        return
    }

    val input = state.conicInputPointsPudorys[conic.id]
    if (input == null) {
        println("❌ Vstupní body pro kuželosečku nebyly nalezeny.")
        return
    }

    val (p1, p2, p3) = input

    fun liftToPlane(x: Float, y: Float): Point3D {
        val z = -(eq.a * x + eq.b * y + eq.d) / eq.c
        return Point3D(x, y, z, name = "")
    }

    val pt1 = liftToPlane(p1.x, p1.y)
    val pt2 = liftToPlane(p2.x, p2.y)
    val pt3 = liftToPlane(p3.x, p3.y)
    val axoEnabled = state.basis != null
    val p1a = if(axoEnabled) projectPoint3DToAxoLocal(pt1, state.basis!!) else Offset.Zero
    val p2a = if(axoEnabled)projectPoint3DToAxoLocal(pt2, state.basis!!)else Offset.Zero
    val p3a = if(axoEnabled)projectPoint3DToAxoLocal(pt3, state.basis!!)else Offset.Zero
    val center = Offset3D(
        (pt1.x + pt2.x) / 2f,
        (pt1.y + pt2.y) / 2f,
        (pt1.z + pt2.z) / 2f
    )

    val u1 = Offset3D((pt2.x - pt1.x) / 2f, (pt2.y - pt1.y) / 2f, (pt2.z - pt1.z) / 2f)
    val mirrorP3 = Offset3D(2 * center.x - pt3.x, 2 * center.y - pt3.y, 2 * center.z - pt3.z)
    val u2 = Offset3D((mirrorP3.x - pt3.x) / 2f, (mirrorP3.y - pt3.y) / 2f, (mirrorP3.z - pt3.z) / 2f)

    val geometry = planeEllipseGeometry(u1, u2, Offset3D(eq.a, eq.b, eq.c))

    val conic3D = ConicSection3D(
        p0 = center,
        u = geometry.axis1,
        v = geometry.axis2,
        matrix = geometry.matrix,
        rawName = conic.rawName.ifBlank { "ε" },
        color = conic.localColor ?: Color.Black,
        strokeWidth = conic.strokeWidth,
        lineStyle = conic.lineStyle, creationIndex = allocIndex(state)
    )

    val coeffsN = Matrix3x3.toCoefficients(conic3D.projectToXZ())
    val narys = ConicSectionNarys(
        a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
        d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )
    val coeffsB = Matrix3x3.toCoefficients(conic3D.projectToYZ())
    val bokorys = ConicSectionBokorys(
        a = coeffsB[0], b = coeffsB[1], c = coeffsB[2],
        d = coeffsB[3], e = coeffsB[4], f = coeffsB[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )

    val basis = state.basis
    val coeffsAxo   =   if (basis != null) {Matrix3x3.toCoefficients(conic3D.projectToAxo(basis))} else
        null
    val axo = if (coeffsAxo != null)
        ConicSectionAxo(
        a = coeffsAxo[0], b = coeffsAxo[1], c = coeffsAxo[2],
        d = coeffsAxo[3], e = coeffsAxo[4], f = coeffsAxo[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D, creationIndex = allocIndex(state)

    ) else null

    conic.applyDegeneracyFrom(geometry)
    narys.applyDegeneracyFrom(geometry)
    axo?.applyDegeneracyFrom(geometry)

    val p1n = Offset(pt1.x, -pt1.z)
    val p2n = Offset(pt2.x, -pt2.z)
    val p3n = Offset(pt3.x, -pt3.z)

    val p1b = Offset(pt1.y,pt1.z)
    val p2b = Offset(pt2.y,pt2.z)
    val p3b = Offset(pt3.y,pt3.z)

    if (mongeLift) {
        val idxP = state.conicsPudorys.indexOfFirst { it.id == conic.id }
        if (idxP >= 0) {
            state.conicsPudorys[idxP] = state.conicsPudorys[idxP].copy(parent = conic3D, parentId = conic3D.id)
        } else {
            // fallback, kdyby conic nebyl v listu (neměl by nastat)
            conic.parent = conic3D
        }
        state.selectedConicsPudorys.add(state.conicsPudorys[idxP])
    }


// 3) Přidej nové objekty
    state.conics3D.add(conic3D)
    state.conicsNarys.add(narys)
    // Bokorys se přidává vždy (i v Monge), aby měl v SelectionInfo přepínač viditelnosti;
    // u zdroje v půdorysu zůstává v axu skrytý.
    bokorys.showInAxo = false
    state.conicsBokorys.add(bokorys)
    if (axo != null) {
        state.conicsAxo.add(axo)
        state.conicInputPointsAxo[axo.id]= Triple(p1a,p2a,p3a)
    }
    if (!mongeLift) {
        val conicP = ConicSectionPudorys(
            a = conic.a,
            b = conic.b,
            c = conic.c,
            d = conic.d,
            e = conic.e,
            f = conic.f,
            rawName = conic.rawName,
            localColor = conic.localColor ?: Color.Black,
            strokeWidth = conic.strokeWidth,
            lineStyle = conic.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            showInAxoInitial = false,
            creationIndex = allocIndex(state)
        )
        val p1p = Offset(pt1.x,pt1.y)
        val p2p = Offset(pt2.x,pt2.y)
        val p3p = Offset(pt3.x,pt3.y)
        state.conicsPudorys.add(conicP)
        state.conicInputPointsPudorys[conicP.id] = Triple(p1p,p2p,p3p)
    }


// 4) (Volitelné) aktualizuj selection, pokud držíš vybraný půdorys v setu
    state.selectedConicsPudorys.removeAll { it.id == conic.id }


// 5) doplňky, které už máš:
    state.conicInputPointsNarys[narys.id] = Triple(p1n, p2n, p3n)
    state.conicInputPointsBokorys[bokorys.id] = Triple(p1b, p2b, p3b)
    state.liftingConicPudorys = null
    setProjectionPhase("pudorys_start", state)
    state.drawobjects = Mongeobjects.NONE
    state.triggerRedraw++
    println("✅ Kuželosečka '${conic.rawName}' doplněna do roviny '${plane.name}' a vytvořen její nárys.")
    commitSnapshot(state)
}
fun finalizeLiftConicToPlaneFromNarys(
    state: MongeState,
    conic: ConicSectionNarys,
    plane: Plane3D,
    mongeLift: Boolean = true
) {
    val eq = plane.equation ?: run {
        println("❌ Vybraná rovina nemá rovnici – nelze pokračovat.")
        return
    }

    val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
    if (abs(normal.y) < 1e-3f) {
        println("❌ Rovina '${plane.name}' je kolmá k nárysně – nelze do ní zvednout nárysovou kuželosečku.")
        return
    }

    val input = state.conicInputPointsNarys[conic.id]
    if (input == null) {
        println("❌ Vstupní body pro kuželosečku nebyly nalezeny.")
        return
    }

    val (p1, p2, p3) = input

    fun liftToPlane(x: Float, z: Float): Point3D {
        val y = -(eq.a * x + eq.c * z + eq.d) / eq.b
        return Point3D(x, y, z, name = "")
    }

    val pt1 = liftToPlane(p1.x, -p1.y)
    val pt2 = liftToPlane(p2.x, -p2.y)
    val pt3 = liftToPlane(p3.x, -p3.y)

    val axoEnabled = state.basis != null
    val p1a = if(axoEnabled) projectPoint3DToAxoLocal(pt1, state.basis!!) else Offset.Zero
    val p2a = if(axoEnabled)projectPoint3DToAxoLocal(pt2, state.basis!!)else Offset.Zero
    val p3a = if(axoEnabled)projectPoint3DToAxoLocal(pt3, state.basis!!)else Offset.Zero


    val center = Offset3D(
        (pt1.x + pt2.x) / 2f,
        (pt1.y + pt2.y) / 2f,
        (pt1.z + pt2.z) / 2f
    )

    val u1 = Offset3D((pt2.x - pt1.x) / 2f, (pt2.y - pt1.y) / 2f, (pt2.z - pt1.z) / 2f)
    val mirrorP3 = Offset3D(2 * center.x - pt3.x, 2 * center.y - pt3.y, 2 * center.z - pt3.z)
    val u2 = Offset3D((mirrorP3.x - pt3.x) / 2f, (mirrorP3.y - pt3.y) / 2f, (mirrorP3.z - pt3.z) / 2f)

    val geometry = planeEllipseGeometry(u1, u2, Offset3D(eq.a, eq.b, eq.c))

    val conic3D = ConicSection3D(
        p0 = center,
        u = geometry.axis1,
        v = geometry.axis2,
        matrix = geometry.matrix,
        rawName = conic.rawName.ifBlank { "ε" },
        color = conic.localColor ?: Color.Black,
        strokeWidth = conic.strokeWidth,
        lineStyle = conic.lineStyle, creationIndex = allocIndex(state),
    )

    // 🔗 přiřazení parenta
    if (mongeLift) {
        conic.parent = conic3D
    }

    val coeffsP = Matrix3x3.toCoefficients(conic3D.projectToXY())
    val pudorys = ConicSectionPudorys(
        a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
        d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )
    val coeffsB = Matrix3x3.toCoefficients(conic3D.projectToYZ())
    val bokorys = ConicSectionBokorys(
        a = coeffsB[0], b = coeffsB[1], c = coeffsB[2],
        d = coeffsB[3], e = coeffsB[4], f = coeffsB[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )
    val p1b = Offset(pt1.y,pt1.z)
    val p2b = Offset(pt2.y,pt2.z)
    val p3b = Offset(pt3.y,pt3.z)
    val basis = state.basis
    val coeffsAxo   =   if (basis != null) {Matrix3x3.toCoefficients(conic3D.projectToAxo(basis))} else
        null
    val axo = if (coeffsAxo != null)
        ConicSectionAxo(
            a = coeffsAxo[0], b = coeffsAxo[1], c = coeffsAxo[2],
            d = coeffsAxo[3], e = coeffsAxo[4], f = coeffsAxo[5],
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D, creationIndex = allocIndex(state)

        ) else null
    conic.applyDegeneracyFrom(geometry)
    pudorys.applyDegeneracyFrom(geometry)
    axo?.applyDegeneracyFrom(geometry)

    val p1p = Offset(pt1.x, pt1.y)
    val p2p = Offset(pt2.x, pt2.y)
    val p3p = Offset(pt3.x, pt3.y)
    if (!mongeLift) {
        val conicN = ConicSectionNarys(
            a = conic.a,
            b = conic.b,
            c = conic.c,
            d = conic.d,
            e = conic.e,
            f = conic.f,
            rawName = conic.rawName,
            localColor = conic.localColor ?: Color.Black,
            strokeWidth = conic.strokeWidth,
            lineStyle = conic.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            showInAxoInitial = false,
            creationIndex = allocIndex(state)
        )
        val p1n = Offset(pt1.x,-pt1.z)
        val p2n = Offset(pt2.x,-pt2.z)
        val p3n = Offset(pt3.x,-pt3.z)
        state.conicsNarys.add(conicN)
        state.conicInputPointsNarys[conicN.id] = Triple(p1n,p2n,p3n)
    }


    state.conics3D.add(conic3D)
    state.conicsPudorys.add(pudorys)
    // Bokorys se přidává vždy (i v Monge), aby měl v SelectionInfo přepínač viditelnosti;
    // u zdroje v nárysu zůstává v axu skrytý.
    bokorys.showInAxo = false
    state.conicsBokorys.add(bokorys)
    state.conicInputPointsBokorys[bokorys.id] = Triple(p1b, p2b, p3b)
    if (axo!= null) {
        state.conicsAxo.add(axo)
        state.conicInputPointsAxo[axo.id] = Triple(p1a, p2a, p3a)
    }
    state.conicInputPointsPudorys[pudorys.id] = Triple(p1p, p2p, p3p)

    setProjectionPhase("narys_start", state)
    state.drawobjects= Mongeobjects.NONE

    println("✅ Kuželosečka '${conic.rawName}' doplněna do roviny '${plane.name}' a vytvořen její půdorys.")
    commitSnapshot(state)
}

fun finalizeLiftConicToPlaneFromBokorys(
    state: MongeState,
    conic: ConicSectionBokorys,
    plane: Plane3D,
    mongeLift: Boolean = true
) {
    val eq = plane.equation ?: run {
        println("❌ Vybraná rovina nemá rovnici – nelze pokračovat.")
        return
    }

    val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
    if (abs(normal.y) < 1e-3f) {
        println("❌ Rovina '${plane.name}' je kolmá k nárysně – nelze do ní zvednout nárysovou kuželosečku.")
        return
    }

    val input = state.conicInputPointsBokorys[conic.id]
    if (input == null) {
        println("❌ Vstupní body pro kuželosečku nebyly nalezeny.")
        return
    }

    val (p1, p2, p3) = input

    fun liftToPlane(y: Float, z: Float): Point3D {
        val x = -(eq.b * y + eq.c * z + eq.d) / eq.a
        return Point3D(x, y, z, name = "")
    }

    val pt1 = liftToPlane(p1.x, p1.y)
    val pt2 = liftToPlane(p2.x, p2.y)
    val pt3 = liftToPlane(p3.x, p3.y)
    val axoEnabled = state.basis != null
    val p1a = if(axoEnabled) projectPoint3DToAxoLocal(pt1, state.basis!!) else Offset.Zero
    val p2a = if(axoEnabled)projectPoint3DToAxoLocal(pt2, state.basis!!)else Offset.Zero
    val p3a = if(axoEnabled)projectPoint3DToAxoLocal(pt3, state.basis!!)else Offset.Zero
    val center = Offset3D(
        (pt1.x + pt2.x) / 2f,
        (pt1.y + pt2.y) / 2f,
        (pt1.z + pt2.z) / 2f
    )

    val u1 = Offset3D((pt2.x - pt1.x) / 2f, (pt2.y - pt1.y) / 2f, (pt2.z - pt1.z) / 2f)
    val mirrorP3 = Offset3D(2 * center.x - pt3.x, 2 * center.y - pt3.y, 2 * center.z - pt3.z)
    val u2 = Offset3D((mirrorP3.x - pt3.x) / 2f, (mirrorP3.y - pt3.y) / 2f, (mirrorP3.z - pt3.z) / 2f)

    val geometry = planeEllipseGeometry(u1, u2, Offset3D(eq.a, eq.b, eq.c))

    val conic3D = ConicSection3D(
        p0 = center,
        u = geometry.axis1,
        v = geometry.axis2,
        matrix = geometry.matrix,
        rawName = conic.rawName.ifBlank { "ε" },
        color = conic.localColor ?: Color.Black,
        strokeWidth = conic.strokeWidth,
        lineStyle = conic.lineStyle, creationIndex = allocIndex(state),
    )

    // 🔗 přiřazení parenta
    if (mongeLift) {
        conic.parent = conic3D
    }

    val coeffsP = Matrix3x3.toCoefficients(conic3D.projectToXY())
    val pudorys = ConicSectionPudorys(
        a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
        d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )
    val coeffsN = Matrix3x3.toCoefficients(conic3D.projectToXZ())
    val narys = ConicSectionNarys(
        a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
        d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )
    val p1n = Offset(pt1.x, -pt1.z)
    val p2n = Offset(pt2.x, -pt2.z)
    val p3n = Offset(pt3.x, -pt3.z)
    val basis = state.basis
    val coeffsAxo   =   if (basis != null) {Matrix3x3.toCoefficients(conic3D.projectToAxo(basis))} else
        null
    val axo = if (coeffsAxo != null)
        ConicSectionAxo(
            a = coeffsAxo[0], b = coeffsAxo[1], c = coeffsAxo[2],
            d = coeffsAxo[3], e = coeffsAxo[4], f = coeffsAxo[5],
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D, creationIndex = allocIndex(state)

        ) else null
    conic.applyDegeneracyFrom(geometry)
    pudorys.applyDegeneracyFrom(geometry)
    axo?.applyDegeneracyFrom(geometry)

    val p1p = Offset(pt1.x, pt1.y)
    val p2p = Offset(pt2.x, pt2.y)
    val p3p = Offset(pt3.x, pt3.y)
    if (!mongeLift) {
        val conicB = ConicSectionBokorys(
            a = conic.a,
            b = conic.b,
            c = conic.c,
            d = conic.d,
            e = conic.e,
            f = conic.f,
            rawName = conic.rawName,
            localColor = conic.localColor ?: Color.Black,
            strokeWidth = conic.strokeWidth,
            lineStyle = conic.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            showInAxoInitial = false,
            creationIndex = allocIndex(state)
        )
        val p1b = Offset(pt1.y,pt1.z)
        val p2b = Offset(pt2.y,pt2.z)
        val p3b = Offset(pt3.y,pt3.z)
        state.conicsBokorys.add(conicB)
        state.conicInputPointsBokorys[conicB.id] = Triple(p1b,p2b,p3b)
    }


    state.conics3D.add(conic3D)
    state.conicsPudorys.add(pudorys)
    state.conicsNarys.add(narys)
    if (axo!= null) {
        state.conicsAxo.add(axo)
        state.conicInputPointsAxo[axo.id]= Triple(p1a,p2a,p3a)
    }
    state.conicInputPointsPudorys[pudorys.id] = Triple(p1p, p2p, p3p)
    state.conicInputPointsNarys[narys.id] = Triple(p1n, p2n, p3n)
    setProjectionPhase("narys_start", state)
    state.drawobjects= Mongeobjects.NONE

    println("✅ Kuželosečka '${conic.rawName}' doplněna do roviny '${plane.name}' a vytvořen její půdorys.")
    commitSnapshot(state)
}

/**
 * Zvednutí elipsy z axonometrického průmětu do roviny. Vstupní body (3) jsou v axo lokálních
 * souřadnicích a zpětně se promítají na rovinu pomocí [liftAxoOverlayPointToPlane]. Vytvoří 3D
 * koniku a všechny zbývající průměty; viditelný v axo zůstává jen zdrojový axo průmět.
 */
fun finalizeLiftConicToPlaneFromAxo(
    state: MongeState,
    conic: ConicSectionAxo,
    plane: Plane3D,
    mongeLift: Boolean = false
) {
    val eq = plane.equation ?: run {
        println("❌ Vybraná rovina nemá rovnici – nelze pokračovat.")
        return
    }
    val input = state.conicInputPointsAxo[conic.id] ?: run {
        println("❌ Vstupní body pro axo kuželosečku nebyly nalezeny.")
        return
    }
    val (p1, p2, p3) = input

    fun lift(o: Offset): Point3D? =
        liftAxoOverlayPointToPlane(o, plane, state)?.let { Point3D(it.x, it.y, it.z, name = "") }

    val pt1 = lift(p1) ?: return
    val pt2 = lift(p2) ?: return
    val pt3 = lift(p3) ?: return

    val center = Offset3D((pt1.x + pt2.x) / 2f, (pt1.y + pt2.y) / 2f, (pt1.z + pt2.z) / 2f)
    val u1 = Offset3D((pt2.x - pt1.x) / 2f, (pt2.y - pt1.y) / 2f, (pt2.z - pt1.z) / 2f)
    val mirrorP3 = Offset3D(2 * center.x - pt3.x, 2 * center.y - pt3.y, 2 * center.z - pt3.z)
    val u2 = Offset3D((mirrorP3.x - pt3.x) / 2f, (mirrorP3.y - pt3.y) / 2f, (mirrorP3.z - pt3.z) / 2f)

    val geometry = planeEllipseGeometry(u1, u2, Offset3D(eq.a, eq.b, eq.c))

    val conic3D = ConicSection3D(
        p0 = center,
        u = geometry.axis1,
        v = geometry.axis2,
        matrix = geometry.matrix,
        rawName = conic.rawName.ifBlank { "ε" },
        color = conic.localColor ?: Color.Black,
        strokeWidth = conic.strokeWidth,
        lineStyle = conic.lineStyle, creationIndex = allocIndex(state)
    )

    // === REUSE axo zdroje (zůstává viditelný) ===
    conic.parent = conic3D
    conic.parentId = conic3D.id
    conic.showInAxo = true

    val coeffsP = Matrix3x3.toCoefficients(conic3D.projectToXY())
    val pudorys = ConicSectionPudorys(
        a = coeffsP[0], b = coeffsP[1], c = coeffsP[2], d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
        rawName = conic3D.rawName, localColor = conic3D.color, strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle, parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )
    val coeffsN = Matrix3x3.toCoefficients(conic3D.projectToXZ())
    val narys = ConicSectionNarys(
        a = coeffsN[0], b = coeffsN[1], c = coeffsN[2], d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
        rawName = conic3D.rawName, localColor = conic3D.color, strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle, parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )
    val coeffsB = Matrix3x3.toCoefficients(conic3D.projectToYZ())
    val bokorys = ConicSectionBokorys(
        a = coeffsB[0], b = coeffsB[1], c = coeffsB[2], d = coeffsB[3], e = coeffsB[4], f = coeffsB[5],
        rawName = conic3D.rawName, localColor = conic3D.color, strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle, parent = conic3D, creationIndex = allocIndex(state),
        showInAxoInitial = mongeLift
    )

    conic.applyDegeneracyFrom(geometry)
    pudorys.applyDegeneracyFrom(geometry)
    narys.applyDegeneracyFrom(geometry)
    bokorys.applyDegeneracyFrom(geometry)

    val p1p = Offset(pt1.x, pt1.y); val p2p = Offset(pt2.x, pt2.y); val p3p = Offset(pt3.x, pt3.y)
    val p1n = Offset(pt1.x, -pt1.z); val p2n = Offset(pt2.x, -pt2.z); val p3n = Offset(pt3.x, -pt3.z)
    val p1b = Offset(pt1.y, pt1.z); val p2b = Offset(pt2.y, pt2.z); val p3b = Offset(pt3.y, pt3.z)

    state.conics3D.add(conic3D)
    state.conicsPudorys.add(pudorys)
    state.conicsNarys.add(narys)
    state.conicsBokorys.add(bokorys)
    state.conicInputPointsPudorys[pudorys.id] = Triple(p1p, p2p, p3p)
    state.conicInputPointsNarys[narys.id] = Triple(p1n, p2n, p3n)
    state.conicInputPointsBokorys[bokorys.id] = Triple(p1b, p2b, p3b)
    setProjectionPhase("narys_start", state)
    state.drawobjects = Mongeobjects.NONE
    println("✅ Kuželosečka '${conic.rawName}' doplněna z axo do roviny '${plane.name}'.")
    commitSnapshot(state)
}
