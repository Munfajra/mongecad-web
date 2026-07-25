package model

import state.MongeStartState
import state.MongeState
import utils.UUID


sealed interface AppTab {
    val id: Int
    val title: String
    val closable: Boolean
}

data class StartTab(
    val startState: MongeStartState ,
    override val id: Int = 0,
    override val title: String = "Start",
) : AppTab {
    override val closable: Boolean = false
}

data class DrawingTab(
    override val id: Int,
    val state: MongeState,
    var mode: ProjectionMode = ProjectionMode.MONGE,
    val recoveryId: String = UUID.randomUUID().toString(),
    override val title: String = "Monge",
) : AppTab {
    override val closable: Boolean = true
}
