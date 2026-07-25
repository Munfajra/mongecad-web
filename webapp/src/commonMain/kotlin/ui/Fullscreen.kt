package ui

/**
 * Roztažení aplikace přes celé okno.
 *
 * Na webu appka běží v rámu uvnitř stránky; tohle ji rozšíří přes celé okno
 * prohlížeče (skryje hlavičku a navigaci webu). Záměrně se nepoužívá
 * Fullscreen API – to nejde odpojit od klávesy Esc, kterou appka potřebuje
 * pro rušení konstrukce. Desktop tohle nemá (má vlastní okno).
 */
expect fun toggleAppFullscreen()

/** Je aplikace právě roztažená přes celé okno? */
expect fun isAppFullscreen(): Boolean
