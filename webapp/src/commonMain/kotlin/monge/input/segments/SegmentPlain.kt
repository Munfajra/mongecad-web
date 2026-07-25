package monge.input.segments

import model.classes.Segment3D
import state.MongeState

/**
 * Přidání 3D úsečky bez detekce těles.
 *
 * Desktop volá `addSegment3DAndDetectSolids`, která navíc zkoumá, jestli
 * úsečky netvoří jehlan/hranol. Tělesa web nemá, takže se jen přidá úsečka.
 */
fun addSegment3DPlain(state: MongeState, segment: Segment3D) {
    state.segments3D.add(segment)
}
