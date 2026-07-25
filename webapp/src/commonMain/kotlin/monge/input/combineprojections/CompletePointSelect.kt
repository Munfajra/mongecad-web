package monge.input.combineprojections

import model.CompletionRequest
import model.DrawingModeMonge
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState

fun startPoint3DCompletion(state: MongeState) {
    val pudorys = state.selectedPointsPudorys.firstOrNull()
    val narys = state.selectedPointsNarys.firstOrNull()

    when {
        pudorys != null -> {
            state.completionPending = CompletionRequest.Point3DFromProjections(
                existing = pudorys,
                expect = DrawingModeMonge.NARYS
            )
            println("ℹ️ Označ bod v NÁRYSU pro doplnění bodu 3D.")
        }

        narys != null -> {
            state.completionPending = CompletionRequest.Point3DFromProjections(
                existing = narys,
                expect = DrawingModeMonge.PUDORYS
            )
            println("ℹ️ Označ bod v PŮDORYSU pro doplnění bodu 3D.")
        }

        else -> {
            println("⚠️ Označ nejprve jednu projekci bodu.")
        }
    }
}
