package ui

/*
 * Maximalizace se dělá přepnutím CSS třídy, ne Fullscreen API.
 *
 * Fullscreen API totiž nejde odpojit od klávesy Esc – prohlížeč jí vždycky
 * režim ukončí a aplikace to nemůže potlačit. V MongeCADu má ale Esc svoji
 * funkci (ruší rozpracovanou konstrukci a výběr), takže by uživatele při
 * kreslení pokaždé vyhodilo ze zvětšeného zobrazení.
 *
 * Cenou je, že zůstane viditelný rám prohlížeče; appka ale zabere celé okno.
 */
@JsFun(
    """() => {
        const el = document.getElementById('webApp');
        if (!el) return;
        el.classList.toggle('app-maximized');
        document.body.classList.toggle('app-maximized-host');

        // Compose sleduje velikost okna, ne kontejneru – po přepnutí třídy
        // by se plátno přepočítalo až při příštím resize okna (proto se
        // appka „roztáhla“ teprve po hnutí oknem). Resize proto pošleme sami,
        // a ještě jednou po dokreslení, až se layout ustálí.
        window.dispatchEvent(new Event('resize'));
        requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
    }"""
)
private external fun toggleMaximizedJs()

@JsFun(
    """() => {
        const el = document.getElementById('webApp');
        return el != null && el.classList.contains('app-maximized');
    }"""
)
private external fun isMaximizedJs(): Boolean

actual fun toggleAppFullscreen() {
    runCatching { toggleMaximizedJs() }
}

actual fun isAppFullscreen(): Boolean = runCatching { isMaximizedJs() }.getOrDefault(false)
