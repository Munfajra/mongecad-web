package gl3d.scene

import androidx.compose.ui.graphics.Color
import gl3d.math.Vec3
import gl3d.render.TriangleBatch
import model.classes.Plane3D
import kotlin.math.abs

/**
 * Uživatelské roviny ze `state.planes3D`.
 *
 * Rovina je nekonečná, kreslí se z ní čtverec o polostraně [size] kolem paty
 * kolmice z počátku – stejně jako `drawPlaneScaled` na desktopu.
 *
 * ## Proč dvě dávky
 *
 * Desktopový `renderPlanesInteractionStyle` kreslí každou rovinu **dvakrát**:
 * jednou s hloubkovým testem `GREATER` (část, kterou zakrývá jiná rovina –
 * ztmavená a průhlednější) a jednou s `LEQUAL` (část vpředu, plnou barvou).
 * Není to obcházení pořadí míchání, jak by se mohlo zdát, ale **záměrný
 * hloubkový klíč**: bez něj se dvě protínající se roviny slijí do jedné barvy
 * a není poznat, která je vpředu.
 *
 * Weighted blended OIT tuhle informaci z principu zahazuje – proto uživatelské
 * roviny přes OIT nejdou. Desktop jimi taky neprochází, OIT tam dostávají jen
 * referenční průmětny.
 */
internal fun collectUserPlanes(
    planes: List<Plane3D>,
    front: TriangleBatch,
    hidden: TriangleBatch,
    size: Float,
    viewDirection: Vec3,
) {
    for (plane in planes) {
        if (!plane.show) continue
        val equation = plane.equation ?: continue

        val normal = Vec3(equation.a, equation.b, equation.c)
        val frame = planeFrame(normal, equation.d) ?: continue

        val facing = abs(normal.normalized() dot viewDirection).coerceIn(0f, 1f)
        val alpha = PLANE_BEHIND_ALPHA +
                (PLANE_FRONT_ALPHA - PLANE_BEHIND_ALPHA) * (0.55f + 0.45f * facing)
        val color = displayPlaneColor(plane.color)

        val a = frame.point(-size, -size)
        val b = frame.point(size, -size)
        val c = frame.point(size, size)
        val d = frame.point(-size, size)

        front.addQuad(a, b, c, d, color.red, color.green, color.blue, alpha)
        hidden.addQuad(
            a, b, c, d,
            color.red * HIDDEN_COLOR_SCALE,
            color.green * HIDDEN_COLOR_SCALE,
            color.blue * HIDDEN_COLOR_SCALE,
            maxOf(PLANE_BEHIND_ALPHA, alpha * 0.22f),
        )
    }
}

/**
 * Rovina se kreslí světlejší, než jakou barvu má její stopa ve 2D – sytá
 * barva by při větší ploše překryla všechno pod sebou. Převzato
 * z `displayPlaneColor` v `opengl/model/Planes.kt`.
 */
private fun displayPlaneColor(color: Color): Color {
    val mix = 0.62f
    return Color(
        red = color.red + (1f - color.red) * mix,
        green = color.green + (1f - color.green) * mix,
        blue = color.blue + (1f - color.blue) * mix,
        alpha = color.alpha,
    )
}

/** Hodnoty z `opengl/Viz.kt` a `renderPlanesInteractionStyle`. */
private const val PLANE_FRONT_ALPHA = 0.4f
private const val PLANE_BEHIND_ALPHA = 0.3f
private const val HIDDEN_COLOR_SCALE = 0.72f
