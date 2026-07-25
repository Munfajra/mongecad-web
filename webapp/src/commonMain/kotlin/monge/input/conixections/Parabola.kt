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
import kotlin.math.sqrt

fun handleParabolaConstructionPudorys(logical: Offset, state: MongeState) {
    when (state.projectionPhase) {
        "pudorys_start" -> {
            state.pendingPoint1 = logical // vrchol
            setProjectionPhase("parabola_focus", state)
            println("Vrchol zvolen")
        }

        "parabola_focus" -> {
            state.pendingPoint2 = logical // ohnisko
            val vertex = state.pendingPoint1!!
            val focus = state.pendingPoint2!!
            if ((focus - vertex).getDistance() < 1e-6f) {
                println("⚠️ Vrchol a ohnisko paraboly splývají.")
                return
            }

            val conic = computeParabolaFromVertexAndFocus(vertex, focus)
            val color = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.color
            else state.currentLineStyleSettings.color
            val width = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.strokeWidth
            else state.currentLineStyleSettings.strokeWidth
            val style = if (state.projectionMode== ProjectionMode.PLANE) state.currentHelpLineStyleSettings.style
            else state.currentLineStyleSettings.style
            val parabola = ConicSectionPudorys(
                a = conic.a,
                b = conic.b,
                c = conic.c,
                d = conic.d,
                e = conic.e,
                f = conic.f,
                rawName = state.inputName.takeIf { it.isNotBlank() } ?: "π",
                localColor = color,
                strokeWidth = width,
                lineStyle = style, creationIndex = allocIndex(state)
            )

            state.conicsPudorys.add(parabola)
            state.conicInputPointsPudorys[parabola.id] = Triple(vertex, focus, Offset.Unspecified)
            println("parabola vytvořena, parametry jsou $parabola")
            commitSnapshot(state)
            // Reset stavu
            state.pendingPoint1 = null
            state.pendingPoint2 = null
            repeatCons(state)
            setProjectionPhase("pudorys_start", state)
            state.inputName = ""

        }
    }
}
fun computeParabolaFromVertexAndFocus(
    vertex: Offset,
    focus: Offset
): ConicSectionPudorys {
    val fx = focus.x
    val fy = focus.y
    val vx = vertex.x
    val vy = vertex.y

    // Direktrix leží symetricky za vrcholem (na opačné straně než ohnisko)
    val dx = 2 * vx - fx
    val dy = 2 * vy - fy

    // Normála na direktrix (jednotková)
    val nx = fx - vx
    val ny = fy - vy
    val norm = sqrt(nx * nx + ny * ny)
    val nxn = nx / norm
    val nyn = ny / norm

    // Pravá strana rovnice: vzdálenost bodu P od direktrix = projekce vektoru (P - D) na normálu
    // Levá strana rovnice: vzdálenost bodu P od ohniska

    // A-F koeficienty z rozvoje: (x - fx)^2 + (y - fy)^2 = (nxn*(x - x) + nyn*(y - y))^2
    val A = 1f - nxn * nxn
    val B = -2f * nxn * nyn
    val C = 1f - nyn * nyn

    val dot = dx * nxn + dy * nyn
    val D = -2f * fx + 2f * nxn * dot
    val E = -2f * fy + 2f * nyn * dot
    val F = fx * fx + fy * fy - dot * dot

    return ConicSectionPudorys(
        a = A,
        b = B,
        c = C,
        d = D,
        e = E,
        f = F
    )
}
fun handleParabolaConstructionNarys(logical: Offset, state: MongeState) {
    val logical = Offset(logical.x,-logical.y)
    when (state.projectionPhase) {
        "narys_start" -> {

            state.pendingPoint1 = logical // vrchol
            setProjectionPhase("parabola_focus_narys", state)
            println("Vrchol zvolen (nárys)")
        }

        "parabola_focus_narys" -> {
            state.pendingPoint2 = logical // ohnisko
            val vertex = state.pendingPoint1!!
            val focus = state.pendingPoint2!!
            if ((focus - vertex).getDistance() < 1e-6f) {
                println("⚠️ Vrchol a ohnisko paraboly splývají (nárys).")
                return
            }

            val conic = computeParabolaFromVertexAndFocusNarys(vertex, focus)

            val parabola = ConicSectionNarys(
                a = conic.a,
                b = conic.b,
                c = conic.c,
                d = conic.d,
                e = conic.e,
                f = conic.f,
                rawName = state.inputName.takeIf { it.isNotBlank() } ?: "π",
                localColor = state.currentLineStyleSettings.color,
                strokeWidth = state.currentLineStyleSettings.strokeWidth,
                lineStyle = state.currentLineStyleSettings.style, creationIndex = allocIndex(state)
            )

            state.conicsNarys.add(parabola)
            state.conicInputPointsNarys[parabola.id] = Triple(vertex, focus, Offset.Unspecified)
            println("parabola vytvořena (nárys), parametry jsou $parabola")
            commitSnapshot(state)
            // Reset
            state.pendingPoint1 = null
            state.pendingPoint2 = null
            repeatCons(state)
            setProjectionPhase("narys_start", state)
            state.inputName = ""

        }
    }
}
fun computeParabolaFromVertexAndFocusNarys(
    vertex: Offset,
    focus: Offset
): ConicSectionNarys {
    val fy = focus.x
    val fz = focus.y
    val vy = vertex.x
    val vz = vertex.y

    val dy = 2 * vy - fy
    val dz = 2 * vz - fz

    val ny = fy - vy
    val nz = fz - vz
    val norm = sqrt(ny * ny + nz * nz)
    val nyn = ny / norm
    val nzn = nz / norm

    val A = 1f - nyn * nyn
    val B = -2f * nyn * nzn
    val C = 1f - nzn * nzn

    val dot = dy * nyn + dz * nzn
    val D = -2f * fy + 2f * nyn * dot
    val E = -2f * fz + 2f * nzn * dot
    val F = fy * fy + fz * fz - dot * dot

    return ConicSectionNarys(
        a = A,
        b = B,
        c = C,
        d = D,
        e = E,
        f = F
    )
}
