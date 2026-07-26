package ui

@JsFun(
    """() => {
        const ready = globalThis.__mongecadAppReady;
        if (typeof ready === 'function') ready();
    }"""
)
private external fun notifyWebAppReadyJs()

actual fun notifyWebAppReady() {
    runCatching { notifyWebAppReadyJs() }
}
