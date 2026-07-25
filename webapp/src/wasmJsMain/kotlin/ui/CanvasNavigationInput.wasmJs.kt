package ui

@JsFun("() => globalThis.__mongecadCanvasPointerType || ''")
private external fun browserCanvasPointerTypeJs(): String

@JsFun("() => Boolean(globalThis.__mongecadCanvasNavigationActive)")
private external fun browserCanvasNavigationActiveJs(): Boolean

internal actual fun browserCanvasPointerType(): String =
    browserCanvasPointerTypeJs()

internal actual fun browserCanvasNavigationActive(): Boolean =
    browserCanvasNavigationActiveJs()
