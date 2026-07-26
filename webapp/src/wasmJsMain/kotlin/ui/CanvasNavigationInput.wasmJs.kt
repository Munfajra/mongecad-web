package ui

@JsFun("() => globalThis.__mongecadCanvasPointerType || ''")
private external fun browserCanvasPointerTypeJs(): String

@JsFun("() => globalThis.__mongecadCanvasDownPointerType || ''")
private external fun browserCanvasDownPointerTypeJs(): String

@JsFun("() => Boolean(globalThis.__mongecadCanvasNavigationActive)")
private external fun browserCanvasNavigationActiveJs(): Boolean

internal actual fun browserCanvasPointerType(): String =
    browserCanvasPointerTypeJs()

internal actual fun browserCanvasDownPointerType(): String =
    browserCanvasDownPointerTypeJs()

internal actual fun browserCanvasNavigationActive(): Boolean =
    browserCanvasNavigationActiveJs()
