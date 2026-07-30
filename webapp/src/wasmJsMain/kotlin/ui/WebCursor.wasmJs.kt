package ui

actual class WebCursor(val css: String)

/**
 * Kurzor nad Compose plátnem.
 *
 * Plátno **není** v light DOM: `ComposeViewport` si na `#webApp` připojí shadow
 * root a kreslí dovnitř něj (viz `gl3d/GlSurface.wasmJs.kt`), takže
 * `document.querySelector('#webApp canvas')` vrací `null` a nastavení kurzoru
 * by tiše nic neudělalo. Hledá se proto uvnitř shadow rootu a vynechává se
 * plátno 3D náhledu, které tam leží vedle toho Compose.
 */
actual fun setCanvasCursor(css: String) {
    setComposeCanvasCursor(css)
}

@JsFun(
    """(css) => {
        const host = document.getElementById('webApp');
        const root = (host && host.shadowRoot) || document;
        const canvas = root.querySelector('canvas:not(#mongecad-gl)');
        if (canvas) canvas.style.cursor = css;
    }"""
)
private external fun setComposeCanvasCursor(css: String)

actual fun createAxoCursor(label: String): WebCursor? =
    if (label.isBlank()) null else WebCursor("crosshair")
