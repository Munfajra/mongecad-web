package monge.input.planeobjects.conicsections

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.ConicSection3D
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.Matrix3x3
import model.classes.projectToXY
import model.classes.projectToXZ
import model.normalize
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import utils.allocIndex
import kotlin.math.abs

fun handleCircleInPlaneConstructionPudorys(logical: Offset, state: MongeState) {
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

    val eq = plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }

    fun liftToPlane(x: Float, y: Float): Point3D {
        val z = -(eq.a * x + eq.b * y + eq.d) / eq.c
        return Point3D(x, y, z, name = "")
    }

    when (state.projectionPhase) {
        "pudorys_start" -> {
            setProjectionPhase("pudorys_ready", state)
        }
        "pudorys_ready" -> {
            state.pendingPoint1 = logical
            setProjectionPhase("circle_plane_radius", state)
        }


        "circle_plane_radius" -> {
            val pCenter2D = state.pendingPoint1!!
            val pRadius2D = logical

            val ptCenter = liftToPlane(pCenter2D.x, pCenter2D.y)
            val ptRadius = liftToPlane(pRadius2D.x, pRadius2D.y)

            val center = Offset3D(ptCenter.x, ptCenter.y, ptCenter.z)
            val radiusVec = Offset3D(
                ptRadius.x - ptCenter.x,
                ptRadius.y - ptCenter.y,
                ptRadius.z - ptCenter.z
            )
            val radius = radiusVec.length()

            val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
            val arbitrary = if (abs(normal.x) < abs(normal.y)) Offset3D(1f, 0f, 0f) else Offset3D(0f, 1f, 0f)
            val v1 = (arbitrary cross normal).normalize()
            val v2 = (normal cross v1).normalize()

            val mat = Matrix3x3.fromCoefficients(
                a = 1f / (radius * radius),
                b = 0f,
                c = 1f / (radius * radius),
                d = 0f,
                e = 0f,
                f = -1f
            )

            val conic3D = ConicSection3D(
                p0 = center,
                u = v1,
                v = v2,
                matrix = mat,
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

            // 🟣 Pomocné body (průměry elipsy)
            val pt1mirror = center - radiusVec       // bod symetrický k ptRadius podle středu
            val pt2 = ptRadius                       // zůstává nezměněno
            val pt3 = center + (radiusVec cross normal).normalize() * radius

            // Projekce pro vykreslení
            val p1 = Offset(pt1mirror.x, pt1mirror.y)
            val p2 = Offset(pt2.x, pt2.y)
            val p3 = Offset(pt3.x, pt3.y)

            val p1n = Offset(pt1mirror.x, -pt1mirror.z)
            val p2n = Offset(pt2.x, -pt2.z)
            val p3n = Offset(pt3.x, -pt3.z)




            state.conics3D.add(conic3D)
            state.conicsPudorys.add(pudorys)
            state.conicsNarys.add(narys)

            state.conicInputPointsPudorys[pudorys.id] = Triple(p1, p2, p3)
            state.conicInputPointsNarys[narys.id] = Triple(p1n, p2n, p3n)
            state.selectedPlaneForCircle = null
            state.selectedPlanes.clear()
            commitSnapshot(state)
            repeatCons(state)
            setProjectionPhase("pudorys_start", state)
            state.inputName = ""
            println("✅ Kružnice v rovině ${plane.name} vytvořena jako ${conic3D.rawName}")
        }
    }
}
fun handleCircleInPlaneConstructionNarys(logical: Offset, state: MongeState) {
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
    val eq = plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }

    // vstup je (x, z) → dopočítáme y z rovnice roviny
    fun liftFromNarys(x: Float, z: Float): Point3D {
        val y = -(eq.a * x + eq.c * z + eq.d) / eq.b
        return Point3D(x, y, z, name = "")
    }

    when (state.projectionPhase) {
        "narys_start" -> {
            setProjectionPhase("narys_ready", state)
        }

        "narys_ready" -> {
            state.pendingPoint1 = logical
            setProjectionPhase("circle_plane_radius_narys", state)
        }

        "circle_plane_radius_narys" -> {
            val pCenter2D = state.pendingPoint1!!
            val pRadius2D = logical

            val ptCenter = liftFromNarys(pCenter2D.x, -pCenter2D.y)
            val ptRadius = liftFromNarys(pRadius2D.x, -pRadius2D.y)

            val center = Offset3D(ptCenter.x, ptCenter.y, ptCenter.z)
            val radiusVec = Offset3D(
                ptRadius.x - ptCenter.x,
                ptRadius.y - ptCenter.y,
                ptRadius.z - ptCenter.z
            )
            val radius = radiusVec.length()

            val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
            val arbitrary = if (abs(normal.x) < abs(normal.y)) Offset3D(1f, 0f, 0f) else Offset3D(0f, 1f, 0f)
            val v1 = (arbitrary cross normal).normalize()
            val v2 = (normal cross v1).normalize()

            val mat = Matrix3x3.fromCoefficients(
                a = 1f / (radius * radius),
                b = 0f,
                c = 1f / (radius * radius),
                d = 0f,
                e = 0f,
                f = -1f
            )

            val conic3D = ConicSection3D(
                p0 = center,
                u = v1,
                v = v2,
                matrix = mat,
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

            // 🟣 Pomocné body
            val pt1mirror = center - radiusVec
            val pt2 = ptRadius
            val pt3 = center + (radiusVec cross normal).normalize() * radius

            val p1 = Offset(pt1mirror.x, pt1mirror.y)
            val p2 = Offset(pt2.x, pt2.y)
            val p3 = Offset(pt3.x, pt3.y)

            val p1n = Offset(pt1mirror.x, -pt1mirror.z)
            val p2n = Offset(pt2.x, -pt2.z)
            val p3n = Offset(pt3.x, -pt3.z)


            state.conics3D.add(conic3D)
            state.conicsPudorys.add(pudorys)
            state.conicsNarys.add(narys)

            state.conicInputPointsPudorys[pudorys.id] = Triple(p1, p2, p3)
            state.conicInputPointsNarys[narys.id] = Triple(p1n, p2n, p3n)
            state.selectedPlaneForCircle = null
            state.selectedPlanes.clear()
            commitSnapshot(state)
            repeatCons(state)
            setProjectionPhase("narys_start", state)
            state.inputName = ""
            println("✅ Kružnice v rovině ${plane.name} vytvořena jako ${conic3D.rawName}")
        }
    }
}
fun startLiftCircleToPlaneFromPudorys(state: MongeState) {
    val conic = state.selectedCirclesPudorys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v půdorysu.")
        return
    }

    state.liftingConicPudorys = conic
    setProjectionPhase("lift_conic_select_plane", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}