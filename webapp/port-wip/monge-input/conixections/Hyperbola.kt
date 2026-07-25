package monge.input.conixections

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.ConicInputHyperbolaNarys
import model.classes.ConicInputHyperbolaPudorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.NamedLineNarys
import model.classes.NamedLinePudorys
import state.MongeState
import state.snapMonge.computeIntersection
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import utils.allocIndex
import utils.dot
import utils.getLogicalCursor
import utils.normalize
import utils.projectPointOntoLine
import kotlin.math.abs
import kotlin.math.absoluteValue

fun handleHyperbolaConstructionPudorys(snappedPointLogical: Offset?, state: MongeState) {
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
    when (state.projectionPhase) {

        "pudorys_start" -> {
            val line1 = state.selectedLinesPudorys.firstOrNull()
            if (line1 == null) {
                println("⚠️ Neoznačena žádná přímka – vyber jednu kliknutím.")
                return
            }
            state.selectedLineForParallelPudorys = line1
            println("🟦 1. asymptota '${line1.name}' vybrána.")
            setProjectionPhase("hyperbola_asymptote2", state)
        }

        "hyperbola_asymptote2" -> {
            val line2 = state.selectedLinesPudorys.firstOrNull()
            val line1 = state.selectedLineForParallelPudorys

            if (line2 == null || line1 == null || line2 == line1) {
                println("⚠️ Vyber jinou přímku než tu první.")
                return
            }

            state.selectedLineForParallelPudorysSecond = line2
            println("🟦 2. asymptota '${line2.name}' vybrána.")
            setProjectionPhase("hyperbola_vertex", state)

            // Nepotřebujeme dál držet výběr přímek
            state.selectedLinesPudorys.clear()
        }


        "hyperbola_vertex" -> {
            val line1 = state.selectedLineForParallelPudorys ?: return
            val line2 = state.selectedLineForParallelPudorysSecond ?: return

            // Výpočet průsečíku asymptot
            val center = computeIntersection(
                Offset(line1.point.x, line1.point.y),
                Offset(line1.direction.x, line1.direction.y),
                Offset(line2.point.x, line2.point.y),
                Offset(line2.direction.x, line2.direction.y)
            ) ?: run {
                println("❌ Asymptoty se neprotínají.")
                return
            }

            val v1 = line1.direction.normalize()
            val v2 = line2.direction.normalize()

            // 📐 Vypočti hlavní a vedlejší osu hyperboly (mezi asymptotami)
            val axisMain = (v1 + v2).normalize()
            val axisSecondary = Offset(-axisMain.y, axisMain.x)

            // 📌 Promítni kliknutý bod na obě osy
            val projMain = projectPointOntoLine(logical, center, axisMain)
            val projSecondary = projectPointOntoLine(logical, center, axisSecondary)

            val distMain = (projMain - logical).getDistance()
            val distSecondary = (projSecondary - logical).getDistance()

            // 🎯 Zvol vrchol a osu
            val (vertex, axis) = if (distMain < distSecondary) {
                projMain to axisMain
            } else {
                projSecondary to axisSecondary
            }

            val conic = computeHyperbolaFromAsymptotesAndVertex(line1, line2, vertex)
            val color = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.color
            else state.currentLineStyleSettings.color
            val width = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.strokeWidth
            else state.currentLineStyleSettings.strokeWidth
            val style = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.style
            else state.currentLineStyleSettings.style
            val hyperbola = ConicSectionPudorys(
                a = conic.a,
                b = conic.b,
                c = conic.c,
                d = conic.d,
                e = conic.e,
                f = conic.f,
                rawName = state.inputName.takeIf { it.isNotBlank() } ?: "η",
                localColor = color,
                strokeWidth = width,
                lineStyle = style, creationIndex = allocIndex(state)
            )


            state.conicsPudorys.add(hyperbola)
            state.hyperbolaInputsPudorys[hyperbola.id] = ConicInputHyperbolaPudorys(
                vertex = vertex,
                axis = axis,
                line1 = line1,
                line2 = line2
            )

            println("Hyperbola vytvořena. Parametry: $hyperbola")
            commitSnapshot(state)
            // Reset
            state.inputName = ""
            repeatCons(state)
            setProjectionPhase("pudorys_start", state)
            state.selectedLineForParallelPudorys = null
            state.selectedLineForParallelPudorysSecond = null

        }

    }
    }
fun computeConicFromParametricHyperbola(
    center: Offset,
    finalAxis: Offset,   // osová (hlavní) jednotková osa
    otherDir: Offset,    // sdružený směr (kolmý, také jednotkový)
    a: Float,
    b: Float
): ConicSectionPudorys {
    // Parametrická rovnice:
    // x(t) = center + a·t·axis + b·sqrt(t² - 1)·otherDir

    // Cílem je najít rovnici ve tvaru: Ax² + Bxy + Cy² + Dx + Ey + F = 0

    // Označíme si osy
    val u = finalAxis
    val v = otherDir

    // Kvadratické členy:
    // X = x - cx = a·t·u.x + b·sqrt(t²−1)·v.x
    // Y = y - cy = a·t·u.y + b·sqrt(t²−1)·v.y

    // Dosadíme a rozvineme výraz (X, Y) do obecné formy:
    // F(x, y) = ( ( (x - c)·u )² / a² ) - ( ( (x - c)·v )² / b² ) = 1

    // Což rozvineš na kvadratickou formu: Ax² + Bxy + Cy² + Dx + Ey + F = 0

    val a2 = a * a
    val b2 = b * b

    val A = (u.x * u.x) / a2 - (v.x * v.x) / b2
    val B = 2f * ((u.x * u.y) / a2 - (v.x * v.y) / b2)
    val C = (u.y * u.y) / a2 - (v.y * v.y) / b2

    val cx = center.x
    val cy = center.y

    // Lineární členy:
    val D = -2f * A * cx - B * cy
    val E = -2f * C * cy - B * cx

    // Absolutní člen:
    val F = A * cx * cx + B * cx * cy + C * cy * cy - 1f

    return ConicSectionPudorys(
        a = A,
        b = B,
        c = C,
        d = D,
        e = E,
        f = F
    )
}

fun computeHyperbolaFromAsymptotesAndVertex(
    line1: NamedLinePudorys,
    line2: NamedLinePudorys,
    vertex: Offset
): ConicSectionPudorys {
    val center = computeIntersection(
        Offset(line1.point.x, line1.point.y),
        Offset(line1.direction.x, line1.direction.y),
        Offset(line2.point.x, line2.point.y),
        Offset(line2.direction.x, line2.direction.y)
    ) ?: error("❌ Asymptoty se neprotínají.")

    val v1 = line1.direction.normalize()
    val v2 = line2.direction.normalize()

    // Směr osy: osa od středu k vrcholu
    val rawAxis = (vertex - center).normalize()
    val finalAxis = if ((vertex - center).dot(rawAxis) < 0f) -rawAxis else rawAxis
    val otherDir = Offset(-finalAxis.y, finalAxis.x) // kolmá osa

    val a = (vertex - center).dot(finalAxis).absoluteValue

    // Výpočet "b" přes sklon asymptot
    val v1x = v1.dot(finalAxis)
    val v1y = v1.dot(otherDir)
    val v2x = v2.dot(finalAxis)
    val v2y = v2.dot(otherDir)

    val slope1 = v1y / v1x
    val slope2 = v2y / v2x
    val b = a * ((abs(slope1) + abs(slope2)) / 2f)

    return computeConicFromParametricHyperbola(center, finalAxis, otherDir, a, b)
}
fun handleHyperbolaConstructionNarys(logical: Offset, state: MongeState) {
    when (state.projectionPhase) {

        "narys_start" -> {
            val line1 = state.selectedLinesNarys.firstOrNull()
            if (line1 == null) {
                println("⚠️ Neoznačena žádná přímka – vyber jednu kliknutím.")
                return
            }
            state.selectedLineForParallelNarys = line1
            println("🟦 1. asymptota '${line1.name}' vybrána.")
            setProjectionPhase("hyperbola_asymptote2_narys", state)
        }

        "hyperbola_asymptote2_narys" -> {
            val line2 = state.selectedLinesNarys.firstOrNull()
            val line1 = state.selectedLineForParallelNarys

            if (line2 == null || line1 == null || line2 == line1) {
                println("⚠️ Vyber jinou přímku než tu první.")
                return
            }

            state.selectedLineForParallelNarysSecond = line2
            println("🟦 2. asymptota '${line2.name}' vybrána.")
            setProjectionPhase("hyperbola_vertex_narys", state)

            state.selectedLinesNarys.clear()
        }

        "hyperbola_vertex_narys" -> {
            val line1 = state.selectedLineForParallelNarys ?: return
            val line2 = state.selectedLineForParallelNarysSecond ?: return

            val center = computeIntersection(
                Offset(line1.point.x, line1.point.z),
                Offset(line1.direction.x, line1.direction.y),
                Offset(line2.point.x, line2.point.z),
                Offset(line2.direction.x, line2.direction.y)
            ) ?: run {
                println("❌ Asymptoty se neprotínají.")
                return
            }

            val v1 = line1.direction.normalize()
            val v2 = line2.direction.normalize()

            val axisMain = (v1 + v2).normalize()
            val axisSecondary = Offset(-axisMain.y, axisMain.x)

            // 🔄 Inverze osy Y (z pohledu nárysu)
            val logicalNarys = Offset(logical.x, -logical.y)

            val projMain = projectPointOntoLine(logicalNarys, center, axisMain)
            val projSecondary = projectPointOntoLine(logicalNarys, center, axisSecondary)

            val distMain = (projMain - logicalNarys).getDistance()
            val distSecondary = (projSecondary - logicalNarys).getDistance()

            val (vertex, axis) = if (distMain < distSecondary) {
                projMain to axisMain
            } else {
                projSecondary to axisSecondary
            }

            val conic = computeHyperbolaFromAsymptotesAndVertexNarys(line1, line2, vertex)

            val hyperbola = ConicSectionNarys(
                a = conic.a,
                b = conic.b,
                c = conic.c,
                d = conic.d,
                e = conic.e,
                f = conic.f,
                rawName = state.inputName.takeIf { it.isNotBlank() } ?: "η",
                localColor = state.currentLineStyleSettings.color,
                strokeWidth = state.currentLineStyleSettings.strokeWidth,
                lineStyle = state.currentLineStyleSettings.style, creationIndex = allocIndex(state)
            )


            state.conicsNarys.add(hyperbola)
            state.hyperbolaInputsNarys[hyperbola.id] = ConicInputHyperbolaNarys(
                vertex = vertex,
                axis = axis,
                line1 = line1,
                line2 = line2
            )

            println("Hyperbola vytvořena v nárysu: $hyperbola")
            commitSnapshot(state)
            state.inputName = ""
            repeatCons(state)
            setProjectionPhase("narys_start", state)
            state.selectedLineForParallelNarys = null
            state.selectedLineForParallelNarysSecond = null

        }

    }
}
fun computeHyperbolaFromAsymptotesAndVertexNarys(
    line1: NamedLineNarys,
    line2: NamedLineNarys,
    vertex: Offset
): ConicSectionNarys {
    val center = computeIntersection(
        Offset(line1.point.x, line1.point.z),
        Offset(line1.direction.x, line1.direction.y),
        Offset(line2.point.x, line2.point.z),
        Offset(line2.direction.x, line2.direction.y)
    ) ?: error("❌ Asymptoty se neprotínají.")

    val v1 = line1.direction.normalize()
    val v2 = line2.direction.normalize()

    val rawAxis = (vertex - center).normalize()
    val finalAxis = if ((vertex - center).dot(rawAxis) < 0f) -rawAxis else rawAxis
    val otherDir = Offset(-finalAxis.y, finalAxis.x)

    val a = (vertex - center).dot(finalAxis).absoluteValue

    val v1x = v1.dot(finalAxis)
    val v1y = v1.dot(otherDir)
    val v2x = v2.dot(finalAxis)
    val v2y = v2.dot(otherDir)

    val slope1 = v1y / v1x
    val slope2 = v2y / v2x
    val b = a * ((abs(slope1) + abs(slope2)) / 2f)

    return computeConicFromParametricHyperbolaNarys(center, finalAxis, otherDir, a, b)
}
fun computeConicFromParametricHyperbolaNarys(
    center: Offset,
    finalAxis: Offset,   // hlavní osa (normalizovaná)
    otherDir: Offset,    // kolmá vedlejší osa (taky normalizovaná)
    a: Float,
    b: Float
): ConicSectionNarys {
    val a2 = a * a
    val b2 = b * b

    val u = finalAxis
    val v = otherDir

    val A = (u.x * u.x) / a2 - (v.x * v.x) / b2
    val B = 2f * ((u.x * u.y) / a2 - (v.x * v.y) / b2)
    val C = (u.y * u.y) / a2 - (v.y * v.y) / b2

    val cx = center.x
    val cy = center.y

    val D = -2f * A * cx - B * cy
    val E = -2f * C * cy - B * cx
    val F = A * cx * cx + B * cx * cy + C * cy * cy - 1f

    return ConicSectionNarys(
        a = A,
        b = B,
        c = C,
        d = D,
        e = E,
        f = F
    )
}
