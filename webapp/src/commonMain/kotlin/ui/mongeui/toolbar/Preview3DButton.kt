package ui.mongeui.toolbar

import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import state.MongeState
import ui.components.MongeRibbonButton
import ui.resources.painterResource

/**
 * Zapnutí a vypnutí 3D náhledu scény.
 *
 * Desktopová lišta má na tomto místě skupinu „Náhled“, která otevírá
 * samostatné OpenGL okno. Web okna nemá, takže se panel rozbaluje vedle 2D
 * plátna – jinak je to tentýž pohled na tatáž data.
 */
@Composable
fun Preview3DButton(state: MongeState, buttonsize: Dp) {
    MongeRibbonButton(
        text = if (state.show3DPanel) "Skrýt 3D náhled" else "Zobrazit 3D náhled",
        selected = state.show3DPanel,
        onClick = { state.show3DPanel = !state.show3DPanel },
    ) {
        Icon(
            painter = painterResource("icons/openGL.svg"),
            contentDescription = "3D náhled",
            modifier = Modifier.size(buttonsize * 0.6f),
        )
    }
}
