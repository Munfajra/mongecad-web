package ui

import androidx.compose.ui.Modifier

/**
 * Desktop řídí kurzor nad canvasem přes AWT okno (`Modifier.windowCursor`), protože
 * Compose `pointerHoverIcon` se tam po kliknutí resetoval na default. Na webu AWT není
 * a tenhle problém taky ne – kurzor drží CSS na canvasu.
 *
 * Modifier tu zůstává se stejným tvarem volání, aby portovaný `AppMongeUI`
 * nemusel nic měnit; samotný tvar kurzoru řeší [setCanvasCursor].
 */
expect class WebCursor

fun Modifier.windowCursor(cursor: WebCursor?): Modifier = this

/** Nastaví CSS kurzor kreslicí plochy (např. "crosshair", "default"). */
expect fun setCanvasCursor(css: String)

/**
 * Protějšek desktopového `createAxoCursor(label)`, který kreslí kurzor s písmenem
 * průmětny (P/N). Na webu zatím mapujeme na křížek – vlastní bitmapový kurzor
 * je samostatný krok.
 */
expect fun createAxoCursor(label: String): WebCursor?
