package monge.input.conixections

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.ProjectionMode
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import utils.allocIndex
import kotlin.math.abs


fun conjugateDiameterInputFromRadii(
    center: Offset,
    firstRadiusEnd: Offset,
    secondRadiusEnd: Offset
): Triple<Offset, Offset, Offset> {
    val firstDiameterOpposite = Offset(
        2f * center.x - firstRadiusEnd.x,
        2f * center.y - firstRadiusEnd.y
    )
    return Triple(firstDiameterOpposite, firstRadiusEnd, secondRadiusEnd)
}

fun computeConicFromConjugateRadii(
    center: Offset,
    firstRadiusEnd: Offset,
    secondRadiusEnd: Offset
): ConicSectionPudorys {
    val (p1, p2, p3) = conjugateDiameterInputFromRadii(center, firstRadiusEnd, secondRadiusEnd)
    return computeConicFromConjugateDiameters(p1, p2, p3)
}

fun handleEllipseConstructionPudorys(logical: Offset, state: MongeState) {
    when (state.projectionPhase) {
        "pudorys_start" -> {
            state.pendingPoint1 = logical
            setProjectionPhase("ellipse_point2", state)
            println("střed elipsy")
        }

        "ellipse_point2" -> {
            state.pendingPoint2 = logical
            setProjectionPhase("ellipse_point3", state)
            println("1. sdružený poloměr")
        }

        "ellipse_point3" -> {
            state.pendingPoint3 = logical
            println("2. sdružený poloměr")

            val center = state.pendingPoint1!!
            val firstRadiusEnd = state.pendingPoint2!!
            val secondRadiusEnd = state.pendingPoint3!!

            val (p1, p2, p3) = conjugateDiameterInputFromRadii(center, firstRadiusEnd, secondRadiusEnd)


            val conic = computeConicFromConjugateDiameters(p1, p2, p3)
            val color = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.color
            else state.currentLineStyleSettings.color
            val width = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.strokeWidth
            else state.currentLineStyleSettings.strokeWidth
            val style = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.style
            else state.currentLineStyleSettings.style
            val ellipse = ConicSectionPudorys(
                a = conic.a,
                b = conic.b,
                c = conic.c,
                d = conic.d,
                e = conic.e,
                f = conic.f,
                rawName = state.inputName.takeIf { it.isNotBlank() } ?: "ε",
                localColor = color,
                strokeWidth = width,
                lineStyle = style, creationIndex = allocIndex(state)
            )
            ellipse.isDegenerate = conic.isDegenerate
            ellipse.isLineDegenerate = conic.isLineDegenerate
            ellipse.degenerateDir = conic.degenerateDir

            state.conicsPudorys.add(ellipse)
            state.conicInputPointsPudorys[ellipse.id] = Triple(p1, p2, p3)
            println("elipsa vytvořena, parametry jsou $ellipse")
            commitSnapshot(state)
            // reset stavu
            state.pendingPoint1 = null
            state.pendingPoint2 = null
            state.pendingPoint3 = null
            repeatCons(state)
            setProjectionPhase("pudorys_start", state)
            state.inputName = ""


        }
    }
}
fun computeConicFromConjugateDiameters(
    p1: Offset,
    p2: Offset,
    p3: Offset
): ConicSectionPudorys {
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

    val u1 = Offset((p2.x - p1.x) / 2f, (p2.y - p1.y) / 2f) // ⬅️ poloměr, ne průměr
    val p4 = Offset(2 * center.x - p3.x, 2 * center.y - p3.y)
    val u2 = Offset((p4.x - p3.x) / 2f, (p4.y - p3.y) / 2f)



    val h = center.x
    val k = center.y

    fun pointConic(): ConicSectionPudorys {
        val conic = ConicSectionPudorys(
            a = 1f,
            b = 0f,
            c = 1f,
            d = -2f * h,
            e = -2f * k,
            f = h * h + k * k
        )
        conic.isDegenerate = true
        return conic
    }

    fun doubleLineConic(dir: Offset): ConicSectionPudorys {
        val len = dir.getDistance()
        if (len < 1e-6f) return pointConic()

        val nx = -dir.y / len
        val ny = dir.x / len
        val A = nx * nx
        val B = 2f * nx * ny
        val C = ny * ny
        val D = -2f * A * h - B * k
        val E = -B * h - 2f * C * k
        val F = (nx * h + ny * k) * (nx * h + ny * k)

        val conic = ConicSectionPudorys(A, B, C, D, E, F)
        conic.isDegenerate = true
        conic.isLineDegenerate = true
        conic.degenerateDir = dir / len
        return conic
    }

    val s00 = u1.x * u1.x + u2.x * u2.x
    val s01 = u1.x * u1.y + u2.x * u2.y
    val s11 = u1.y * u1.y + u2.y * u2.y
    val det = s00 * s11 - s01 * s01

    if (abs(det) < 1e-12f) {
        val dir = if (u1.getDistance() >= u2.getDistance()) u1 else u2
        return doubleLineConic(dir)
    }

    val A = s11 / det
    val B = -2f * s01 / det
    val C = s00 / det

    val D = -2 * A * h - B * k
    val E = -B * h - 2 * C * k
    val F = A * h * h + B * h * k + C * k * k - 1f


    return ConicSectionPudorys(A, B, C, D, E, F)
}
fun handleEllipseConstructionNarys(logical: Offset, state: MongeState) {
    when (state.projectionPhase) {
        "narys_start" -> {
            state.pendingPoint1 = logical
            setProjectionPhase("ellipse_point2", state)
            println("střed elipsy (nárys)")
        }

        "ellipse_point2" -> {
            state.pendingPoint2 = logical
            setProjectionPhase("ellipse_point3", state)
            println("1. sdružený poloměr (nárys)")
        }

        "ellipse_point3" -> {
            state.pendingPoint3 = logical
            println("2. sdružený poloměr (nárys)")

            val center = state.pendingPoint1!!
            val firstRadiusEnd = state.pendingPoint2!!
            val secondRadiusEnd = state.pendingPoint3!!
            val (p1, p2, p3) = conjugateDiameterInputFromRadii(center, firstRadiusEnd, secondRadiusEnd)

            // Tady převrátíme body do Z roviny
            val p1z = Offset(p1.x, -p1.y)
            val p2z = Offset(p2.x, -p2.y)
            val p3z = Offset(p3.x, -p3.y)

            // Výpočet koniky pro XY rovinu
            val c = computeConicFromConjugateDiameters(p1z, p2z, p3z)

            // NEpřevracet už žádné koeficienty. Žádné -B, -E!
            val ellipse = ConicSectionNarys(
                a = c.a,
                b = c.b,
                c = c.c,
                d = c.d,
                e = c.e,
                f = c.f,
                rawName = state.inputName.takeIf { it.isNotBlank() } ?: "ε",
                localColor = state.currentLineStyleSettings.color,
                strokeWidth = state.currentLineStyleSettings.strokeWidth,
                lineStyle = state.currentLineStyleSettings.style, creationIndex = allocIndex(state)
            )
            ellipse.isDegenerate = c.isDegenerate
            ellipse.isLineDegenerate = c.isLineDegenerate
            ellipse.degenerateDir = c.degenerateDir?.let { Offset(it.x, -it.y) }


            state.conicsNarys.add(ellipse)
            state.conicInputPointsNarys[ellipse.id] = Triple(p1, p2, p3)
            println("elipsa vytvořena v nárysu: $ellipse")
            commitSnapshot(state)
            state.pendingPoint1 = null
            state.pendingPoint2 = null
            state.pendingPoint3 = null
            repeatCons(state)
            setProjectionPhase("narys_start", state)
            state.inputName = ""


        }

    }
}
