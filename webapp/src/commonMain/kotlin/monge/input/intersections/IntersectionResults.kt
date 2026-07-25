package monge.input.intersections

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.Offset3D
import model.Point3D
import model.ProjectionMode
import model.classes.Line3D
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.normalize
import state.MongeState
import utils.allocIndex
import utils.update2DSnapshots
import utils.withSuffixOnce

const val INTERSECTION_RESULT_NAME = "P"
const val INTERSECTION_RESULT_STROKE_WIDTH = 3f
val INTERSECTION_RESULT_COLOR: Color = Color.Red

fun notifyEmptyIntersection(state: MongeState) {
    state.showEmptyIntersectionDialog = true
    state.consInfo.value = "Průnik je prázdný – objekty se neprotínají."
}

/** Přidá 3D bod průniku a jeho půdorysný a nárysný průmět. */
fun addIntersectionPoint3D(
    state: MongeState,
    x: Float,
    y: Float,
    z: Float,
): Point3D {
    val point3D = Point3D(
        x = x,
        y = y,
        z = z,
        name = INTERSECTION_RESULT_NAME,
        color = INTERSECTION_RESULT_COLOR,
        width = INTERSECTION_RESULT_STROKE_WIDTH,
        creationIndex = allocIndex(state)
    )
    state.sharedPoints3D.add(point3D)

    val showInMonge = state.projectionMode != ProjectionMode.AXO
    state.pointsPudorys.add(
        Point3DPudorys(
            x = x,
            y = y,
            name = INTERSECTION_RESULT_NAME.withSuffixOnce("₁"),
            parent = point3D,
            creationIndex = allocIndex(state),
            showInAxoInitial = showInMonge
        )
    )
    state.pointsNarys.add(
        Point3DNarys(
            x = x,
            z = z,
            name = INTERSECTION_RESULT_NAME.withSuffixOnce("₂"),
            parent = point3D,
            creationIndex = allocIndex(state),
            showInAxoInitial = showInMonge
        )
    )

    update2DSnapshots(state)
    state.triggerRedraw++
    return point3D
}

/** Přidá 3D průsečnici rovin a její půdorysný a nárysný průmět. */
fun addIntersectionLine3D(
    state: MongeState,
    point: Offset3D,
    direction: Offset3D,
): Line3D {
    val normalizedDirection = direction.normalize()
    val start = Point3D(
        x = point.x,
        y = point.y,
        z = point.z,
        name = INTERSECTION_RESULT_NAME,
        color = INTERSECTION_RESULT_COLOR,
        width = INTERSECTION_RESULT_STROKE_WIDTH,
        creationIndex = allocIndex(state)
    )
    val line3D = Line3D(
        start = start,
        direction = normalizedDirection,
        name = INTERSECTION_RESULT_NAME,
        color = INTERSECTION_RESULT_COLOR,
        strokeWidth = INTERSECTION_RESULT_STROKE_WIDTH,
        creationIndex = allocIndex(state)
    )
    state.lines3D.add(line3D)

    val showInMonge = state.projectionMode != ProjectionMode.AXO
    state.lines3DPudorys.add(
        Line3DProjectionPudorys(
            point = Point3DPudorys(
                point.x,
                point.y,
                name = INTERSECTION_RESULT_NAME.withSuffixOnce("₁")
            ),
            direction = Offset(normalizedDirection.x, normalizedDirection.y),
            parent = line3D,
            parentId = line3D.id,
            creationIndex = allocIndex(state),
            showInAxoInitial = showInMonge
        )
    )
    state.lines3DNarys.add(
        Line3DProjectionNarys(
            point = Point3DNarys(
                point.x,
                point.z,
                name = INTERSECTION_RESULT_NAME.withSuffixOnce("₂")
            ),
            direction = Offset(normalizedDirection.x, normalizedDirection.z),
            parent = line3D,
            parentId = line3D.id,
            creationIndex = allocIndex(state),
            showInAxoInitial = showInMonge
        )
    )

    update2DSnapshots(state)
    state.triggerRedraw++
    return line3D
}
