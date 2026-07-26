package ui

/**
 * Schová načítací vrstvu stránky (viz `.app-loading` v style.css).
 *
 * Vrstva žije v HTML, protože se musí ukázat dřív, než se stáhne Wasm –
 * v tu chvíli žádná Compose kompozice ještě neběží. Zhasnout ji naopak umí
 * jen aplikace, která jediná ví, že už má co kreslit.
 */
expect fun notifyWebAppReady()
