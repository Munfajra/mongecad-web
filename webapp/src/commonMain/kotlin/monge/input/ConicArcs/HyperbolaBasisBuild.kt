package monge.input.ConicArcs

import androidx.compose.ui.geometry.Offset
import draw.mongescreen.conicarcs.HyperbolaBasis
import draw.mongescreen.conicarcs.hyperbolaBasisFrom
import utils.normalize
import state.snapMonge.computeIntersection

/**
 * Sestavení báze hyperboly ze zadaných asymptot a vrcholu.
 * Na desktopu bydlí v `monge/input/axo/ConicArcsAxo.kt`, ale je to
 * geometrie nezávislá na axonometrii – volá ji i segmentace kuželoseček.
 */
fun buildHyperbolaBasis(input: Any?): draw.mongescreen.conicarcs.HyperbolaBasis? {
    return when (input) {
        is model.classes.ConicInputHyperbolaPudorys -> {
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
        }
        is model.classes.ConicInputHyperbolaNarys -> {
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
        }
        is model.classes.ConicInputHyperbolaBokorys -> {
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.y, input.line1.point.z), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.y, input.line2.point.z), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
        }
        is model.classes.ConicInputHyperbolaAxo -> {
            val v1 = input.line1.direction.normalize()
            val center = computeIntersection(
                Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
                Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
            ) ?: input.vertex
            hyperbolaBasisFrom(center, input.vertex, v1, input.axis)
        }
        else -> null
    }
}

