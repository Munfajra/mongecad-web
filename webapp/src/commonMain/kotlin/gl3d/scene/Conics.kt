package gl3d.scene

import geometry.conics.ConicType
import geometry.conics.HyperbolaBranches
import geometry.conics.branchSignFromEnds
import geometry.conics.classifyConicFromMatrix
import geometry.conics.sampleDegenerateConic
import geometry.conics.sampleParametricEllipse
import geometry.conics.sampleParametricEllipseArc
import geometry.conics.sampleParametricHyperbola
import geometry.conics.sampleParametricParabola
import geometry.conics.sampleParametricParabolaArc
import gl3d.math.toVec3
import gl3d.render.LineBatch
import gl3d.render.LineStyle3D
import gl3d.render.ScreenProjector
import model.classes.ConicSection3D
import state.MongeState
import model.gl3dLineColor

/**
 * Kuželosečky ve 3D – port smyčky nad `state.conics3D` z `renderScene3D`
 * a `drawConicSection` z `opengl/model/Conics.kt`.
 *
 * Vzorkování samotné se neportovalo: `geometry/conics/ConicMath.kt` je už
 * dřívější port téhož kódu z desktopu, jen bez vazby na GL. Tady zbývá
 * klasifikovat, vzít správný vzorkovač a poslat lomenou čáru do batche.
 *
 * Oblouky (výřezy) se berou ze stejných map jako na desktopu – bez nich by se
 * z rozpracované konstrukce kreslila vždy celá kuželosečka.
 */
internal fun collectConics(
    state: MongeState,
    batch: LineBatch,
    projector: ScreenProjector,
    depthBias: Float = 0f,
) {
    for (conic in state.conics3D) {
        if (!conic.show) continue

        val branches = sampleConic(conic, state) ?: continue
        val style = LineStyle3D.of(
            color = conic.color.gl3dLineColor(),
            width = conic.strokeWidth,
            pattern = conic.lineStyle.toPatternValue(),
            depthBias = depthBias,
        )

        batch.addPolyline(branches.branch1.map { it.toVec3() }, style, projector)
        branches.branch2?.let { batch.addPolyline(it.map { p -> p.toVec3() }, style, projector) }
    }
}

/**
 * Degenerovaná kuželosečka je i nouzová větev: když vzorkování selže (typicky
 * chybějící poloosy u hyperboly), desktop kreslí degenerovaný tvar místo aby
 * objekt zmizel. Stejně tak tady.
 */
internal fun sampleConic(conic: ConicSection3D, state: MongeState): HyperbolaBranches? =
    runCatching {
        when (classifyConicFromMatrix(conic.matrix)) {
            ConicType.ELLIPSE -> {
                val arc = state.ellipseArcParams3D[conic.id]
                val points = if (arc != null) {
                    sampleParametricEllipseArc(conic, arc.first, arc.second)
                } else {
                    sampleParametricEllipse(conic)
                }
                HyperbolaBranches(points, null)
            }

            ConicType.PARABOLA -> {
                val arc = state.parabolaArcParams3D[conic.id]
                val points = if (arc != null) {
                    sampleParametricParabolaArc(conic, arc.first, arc.second)
                } else {
                    sampleParametricParabola(conic)
                }
                HyperbolaBranches(points, null)
            }

            ConicType.HYPERBOLA -> {
                val a = conic.a ?: return@runCatching HyperbolaBranches(
                    sampleDegenerateConic(conic), null,
                )
                val b = conic.b ?: return@runCatching HyperbolaBranches(
                    sampleDegenerateConic(conic), null,
                )
                val ends = state.hyperbolaArcEnds3D[conic.id]
                sampleParametricHyperbola(
                    conic = conic,
                    a = a,
                    b = b,
                    arcs = state.hyperbolaArcParams3D[conic.id],
                    branchSigns = ends?.first?.let { branchSignFromEnds(conic, it) } to
                            ends?.second?.let { branchSignFromEnds(conic, it) },
                    ends = ends,
                )
            }

            ConicType.DEGENERATE -> HyperbolaBranches(sampleDegenerateConic(conic), null)
        }
    }.getOrElse {
        runCatching { HyperbolaBranches(sampleDegenerateConic(conic), null) }.getOrNull()
    }
