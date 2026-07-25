package monge.input.intersections

import model.Offset3D
import model.classes.Line3D
import model.classes.Plane3D
import model.classes.paramAtPoint
import model.normalize
import state.MongeState
import kotlin.math.abs

/** Přímka × přímka. */
fun intersectLineLine(a: Line3D, b: Line3D, state: MongeState) {
    val firstPoint = Offset3D(a.start.x, a.start.y, a.start.z)
    val secondPoint = Offset3D(b.start.x, b.start.y, b.start.z)
    val firstDirection = a.direction.normalize()
    val secondDirection = b.direction.normalize()
    val cross = firstDirection cross secondDirection
    val denominator = cross dot cross

    if (denominator < 1e-6f) {
        notifyEmptyIntersection(state)
        return
    }

    val between = secondPoint - firstPoint
    val firstParameter = ((between cross secondDirection) dot cross) / denominator
    val secondParameter = ((between cross firstDirection) dot cross) / denominator
    val firstHit = firstPoint + firstDirection * firstParameter
    val secondHit = secondPoint + secondDirection * secondParameter

    if ((firstHit - secondHit).length() > 1e-2f ||
        !lineTrimContainsPoint(a, firstHit) ||
        !lineTrimContainsPoint(b, firstHit)
    ) {
        notifyEmptyIntersection(state)
        return
    }

    addIntersectionPoint3D(state, firstHit.x, firstHit.y, firstHit.z)
}

/** Přímka × rovina. Přímka ležící v rovině se znovu nepřidává. */
fun intersectLinePlane(line: Line3D, plane: Plane3D, state: MongeState) {
    val equation = plane.equation
    if (equation == null) {
        notifyEmptyIntersection(state)
        return
    }

    val normal = Offset3D(equation.a, equation.b, equation.c)
    val normalLength = normal.length().coerceAtLeast(1f)
    val point = Offset3D(line.start.x, line.start.y, line.start.z)
    val direction = line.direction.normalize()
    val denominator = normal dot direction
    val pointValue = (normal dot point) + equation.d

    if (abs(denominator) < 1e-6f * normalLength) {
        if (abs(pointValue) < 1e-3f * normalLength) {
            state.consInfo.value = "Přímka leží v rovině – průnikem je sama přímka."
        } else {
            notifyEmptyIntersection(state)
        }
        return
    }

    val hit = point + direction * (-pointValue / denominator)
    if (!lineTrimContainsPoint(line, hit)) {
        notifyEmptyIntersection(state)
        return
    }
    addIntersectionPoint3D(state, hit.x, hit.y, hit.z)
}

/** Rovina × rovina. Výsledkem je průsečnice. */
fun intersectPlanePlane(a: Plane3D, b: Plane3D, state: MongeState) {
    val firstEquation = a.equation
    val secondEquation = b.equation
    if (firstEquation == null || secondEquation == null) {
        notifyEmptyIntersection(state)
        return
    }

    val firstNormal = Offset3D(firstEquation.a, firstEquation.b, firstEquation.c)
    val secondNormal = Offset3D(secondEquation.a, secondEquation.b, secondEquation.c)
    val direction = firstNormal cross secondNormal
    val directionLengthSquared = direction dot direction

    if (directionLengthSquared < 1e-9f) {
        val firstNormalLengthSquared = firstNormal dot firstNormal
        if (firstNormalLengthSquared < 1e-12f) {
            notifyEmptyIntersection(state)
            return
        }
        val pointOnFirst = firstNormal * (-firstEquation.d / firstNormalLengthSquared)
        val distanceToSecond = (secondNormal dot pointOnFirst) + secondEquation.d
        if (abs(distanceToSecond) < 1e-3f * secondNormal.length().coerceAtLeast(1f)) {
            state.consInfo.value = "Roviny jsou totožné – průnikem je celá rovina."
        } else {
            notifyEmptyIntersection(state)
        }
        return
    }

    val point = (
        (secondNormal cross direction) * -firstEquation.d +
            (direction cross firstNormal) * -secondEquation.d
        ) * (1f / directionLengthSquared)
    addIntersectionLine3D(state, point, direction)
}

private fun lineTrimContainsPoint(line: Line3D, point: Offset3D): Boolean {
    val range = line.customTrimRange ?: return true
    val parameter = line.paramAtPoint(point) ?: return false
    return range.contains(parameter, eps = 1e-3f)
}
