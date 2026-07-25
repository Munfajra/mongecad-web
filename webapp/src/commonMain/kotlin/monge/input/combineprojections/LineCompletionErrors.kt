package monge.input.combineprojections

import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase

fun showLineCompletionError(state: MongeState, message: String) {
    state.lineCompletionErrorMessage = message
    state.showLineCompletionErrorDialog = true
    println(message)
}

fun dismissLineCompletionError(state: MongeState) {
    state.showLineCompletionErrorDialog = false
    state.lineCompletionErrorMessage = ""

    when (state.projectionPhase) {
        "line_finalize_narys_auto",
        "projection_line_pudorys_dir" -> setProjectionPhase("projection_line_start_pudorys_dir", state)

        "line_finalize_pudorys_auto" -> setProjectionPhase("projection_line_narys_dir", state)

        "narys_dir_finalize_auto" -> setProjectionPhase("projection_line_start_pudorys", state)

        "pudorys_dir_finalize_auto" -> setProjectionPhase("projection_line_start_narys", state)
    }
}
