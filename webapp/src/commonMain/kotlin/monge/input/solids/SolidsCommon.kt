package monge.input.solids

import androidx.compose.ui.graphics.Color
import model.LineStyle
import model.Point3D
import model.classes.Segment3D
import monge.input.planeobjects.conicsections.createSegmentProjectionsFor
import monge.input.segments.addSegment3DAndDetectSolids
import state.MongeState
import utils.allocIndex
import utils.UUID
import kotlin.math.sqrt

internal fun solidEdgeLength(a: Point3D, b: Point3D): Float {
    val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

// Vytvoří 3D hranu tělesa (a její detekci). V Monge/KOTO doplní i půdorys/nárys,
// v Axu se průměty řeší hromadně přes applyAxoOnlySolidVisibility. Vrací id 3D úsečky.
internal fun addSolidEdge3D(
    state: MongeState,
    a: Point3D,
    b: Point3D,
    buildAxo: Boolean,
    color: Color,
    width: Float,
    style: LineStyle
): String {
    val seg3 = Segment3D(
        id = UUID.randomUUID().toString(),
        start = a, end = b,
        name = "${a.name}${b.name}",
        color = color, strokeWidth = width,
        creationIndex = allocIndex(state)
    )
    if (!buildAxo) {
        createSegmentProjectionsFor(state, seg3, nameBase = seg3.name, style = style, color = color, width = width)
    }
    addSegment3DAndDetectSolids(state, seg3)
    return seg3.id
}
