@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ui

/**
 * `preventDefault()` je současný standardní způsob vyvolání potvrzení.
 * `returnValue` zůstává kvůli kompatibilitě se staršími prohlížeči.
 */
actual fun setBeforeUnloadGuardEnabled(enabled: Boolean) {
    updateBeforeUnloadGuard(enabled)
}

@JsFun(
    """(enabled) => {
        const key = "__mongecadBeforeUnloadHandler";
        const current = globalThis[key];

        if (enabled) {
            if (current) return;
            const handler = (event) => {
                event.preventDefault();
                event.returnValue = true;
            };
            globalThis[key] = handler;
            window.addEventListener("beforeunload", handler);
        } else if (current) {
            window.removeEventListener("beforeunload", current);
            delete globalThis[key];
        }
    }"""
)
private external fun updateBeforeUnloadGuard(enabled: Boolean)
