package monge.input.segments

import model.classes.Segment3D
import state.MongeState

/**
 * Přidání 3D úsečky s detekcí těles.
 *
 * Na desktopu se stejná funkce jmenuje `addSegment3DAndDetectSolids`. Web si
 * kvůli historii (tělesa dřív chyběla) drží vlastní název, ale chování je už
 * shodné – po přidání úsečky se zkoumá, jestli úsečky netvoří jehlan/hranol.
 */
fun addSegment3DPlain(state: MongeState, segment: Segment3D) {
    addSegment3DAndDetectSolids(state, segment)
}
