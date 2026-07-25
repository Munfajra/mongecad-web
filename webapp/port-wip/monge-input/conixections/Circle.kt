package monge.input.conixections

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.Mongeobjects
import model.ProjectionType
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.updateConstructionInfo
import utils.allocIndex
import kotlin.math.atan2
import kotlin.math.sqrt


fun handleClickCirclePudorys(logical: Offset, state: MongeState) {
    if (state.pendingPoint1 == null) {
        state.pendingPoint1 = logical
        println("🟢 Střed kružnice uložen: $logical")
        state.consInfo.value = "Vyberte poloměr"
    } else {

        val center = state.pendingPoint1!!
        val r = (logical - center).getDistance()
        val x0 = center.x
        val y0 = center.y

        val A = 1f
        val B = 0f
        val C = 1f
        val D = -2 * x0
        val E = -2 * y0
        val F = x0 * x0 + y0 * y0 - r * r

        val settings = state.currentLineStyleSettings
        val helpsettings = state.currentHelpLineStyleSettings
        val color =  when (state.projekcnityp) {
            ProjectionType.SINGLE -> settings.color
            ProjectionType.AUXILIARY-> helpsettings.color
            else -> settings.color
        }
        val width =  when (state.projekcnityp) {
            ProjectionType.SINGLE -> settings.strokeWidth
            ProjectionType.AUXILIARY -> helpsettings.strokeWidth
            else -> settings.strokeWidth
        }
        val style =  when (state.projekcnityp) {
            ProjectionType.SINGLE -> settings.style
            ProjectionType.AUXILIARY -> helpsettings.style
            else -> settings.style
        }
        val isHelpCircle = when (state.projekcnityp) {
            ProjectionType.SINGLE -> false
            ProjectionType.AUXILIARY -> true
            else -> false
        }

        val circle = ConicSectionPudorys(
            a = A,
            b = B,
            c = C,
            d = D,
            e = E,
            f = F,
            rawName =  "k",
            isHelpCircle = isHelpCircle,
            localColor = color,
            strokeWidth = width,
            lineStyle = style,
            parent = null,
            creationIndex = allocIndex(state)
        )

        state.circlesPudorys += circle
        state.pendingPoint1 = null
        if (state.drawobjects != Mongeobjects.SPHERE) {
            commitSnapshot(state)}
        repeatCons(state)
        updateConstructionInfo(state)

    }
}

fun handleClickCircleNarys(logical: Offset, state: MongeState) {
    if (state.pendingPoint1 == null) {
        state.pendingPoint1 = logical
        println("🟢 Střed kružnice (nárys) uložen: $logical")
        state.consInfo.value = "Vyberte poloměr"
    } else {

        val center = state.pendingPoint1!!
        val x0 = center.x
        val z0 = -center.y // nárys: y souřadnice je záporná osa z

        val r = (logical - center).getDistance()

        val A = 1f
        val B = 0f
        val C = 1f
        val D = -2 * x0
        val E = -2 * z0
        val F = x0 * x0 + z0 * z0 - r * r

        val settings = state.currentLineStyleSettings
        val helpsettings = state.currentHelpLineStyleSettings
        val color =  when (state.projekcnityp) {
            ProjectionType.SINGLE -> settings.color
            ProjectionType.AUXILIARY-> helpsettings.color
            else -> settings.color
        }
        val width =  when (state.projekcnityp) {
            ProjectionType.SINGLE -> settings.strokeWidth
            ProjectionType.AUXILIARY-> helpsettings.strokeWidth
            else -> settings.strokeWidth
        }
        val style =  when (state.projekcnityp) {
            ProjectionType.SINGLE -> settings.style
            ProjectionType.AUXILIARY -> helpsettings.style
            else -> settings.style
        }
        val isHelpCircle = when (state.projekcnityp) {
            ProjectionType.SINGLE -> false
            ProjectionType.AUXILIARY-> true
            else -> false
        }

        val circle = ConicSectionNarys(
            a = A,
            b = B,
            c = C,
            d = D,
            e = E,
            f = F,
            isHelpCircle=isHelpCircle,
            rawName =  "k",
            localColor = color,
            strokeWidth = width,
            lineStyle = style,
            parent = null,
            creationIndex = allocIndex(state)
        )


        state.circlesNarys += circle
        state.pendingPoint1 = null
        if (state.drawobjects != Mongeobjects.SPHERE) {
            commitSnapshot(state)}
        repeatCons(state)
        updateConstructionInfo(state)

    }
}
fun projectToCircle(circle: ConicSectionPudorys, pt: Offset): Offset {
    val x0 = -circle.d / 2f
    val y0 = -circle.e / 2f
    val r2 = x0 * x0 + y0 * y0 - circle.f
    val r = sqrt(r2.coerceAtLeast(0f))

    val center = Offset(x0, y0)
    val v = pt - center
    val len = v.getDistance()

    if (len < 1e-6f) {
        return Offset(x0 + r, y0)
    }

    val dir = v / len
    return center + dir * r
}
fun projectToCircleNarys(circle: ConicSectionNarys, pt: Offset): Offset {
    val x0 = -circle.d / 2f
    val z0 = -circle.e / 2f
    val r2 = x0 * x0 + z0 * z0 - circle.f
    val r = sqrt(r2.coerceAtLeast(0f))

    // nárysová kreslicí/logická soustava: (x, -z)
    val center = Offset(x0, -z0)

    val v = pt - center
    val len = v.getDistance()

    if (len < 1e-6f) {
        return Offset(center.x + r, center.y)
    }

    val dir = v / len
    return center + dir * r
}
fun circleAngle(circle: ConicSectionPudorys, ptOnCircle: Offset): Float {
    val x0 = -circle.d / 2f
    val y0 = -circle.e / 2f
    return atan2(ptOnCircle.y - y0, ptOnCircle.x - x0)
}

fun normAngle(a: Float): Float {
    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    var x = a
    while (x < 0f) x += twoPi
    while (x >= twoPi) x -= twoPi
    return x
}

fun ccwDelta(start: Float, end: Float): Float {
    return normAngle(end - start)
}