package draw.mongescreen.objects

import androidx.compose.ui.geometry.Offset

data class ConicCoefficients2D(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float
)
fun conicCoefficients(conic: model.classes.ConicSection2D): ConicCoefficients2D? = when (conic) {
    is model.classes.ConicSectionPudorys -> ConicCoefficients2D(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
    is model.classes.ConicSectionNarys -> ConicCoefficients2D(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
    is model.classes.ConicSectionBokorys -> ConicCoefficients2D(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
    is model.classes.ConicSectionAxo -> ConicCoefficients2D(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
}
data class DegenerateConicLine(val origin: Offset, val direction: Offset)

data class DegenerateEllipseParam(
    val center: Offset,
    val dir: Offset,
    val radius: Float,
    val su: Float,
    val sv: Float
)