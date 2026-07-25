import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.configureWebResources
import ui.MongeCadWebApp

/**
 * Compose resources jsou ve stejné složce jako produkční JS bundle.
 *
 * Výchozí resource loader je ale hledá relativně k HTML dokumentu. Na samostatné
 * preview stránce jsou oba ve stejné složce, zatímco na webu je HTML v kořeni a
 * bundle v `app/`; bez této mapy proto ostrý web žádá `/composeResources/...`
 * místo `/app/composeResources/...`.
 */
@JsFun(
    """(path) => {
        const script = Array.from(document.scripts)
            .find((element) => element.src.includes("mongecad-web.js"));
        return new URL(path, script?.src || document.baseURI).href;
    }"""
)
private external fun resourceUrlNextToBundle(path: String): String

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    configureWebResources {
        resourcePathMapping(::resourceUrlNextToBundle)
    }

    ComposeViewport(viewportContainerId = "webApp") {
        MongeCadWebApp()
    }
}
