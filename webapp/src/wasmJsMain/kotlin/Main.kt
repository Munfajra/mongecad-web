import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import ui.MongeCadWebApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "webApp") {
        MongeCadWebApp()
    }
}
