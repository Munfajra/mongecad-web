package ui

/*
 * Roztažení aplikace přes celé okno.
 *
 * Dvě nezávislé vrstvy, protože každá funguje jinde:
 *
 *  1. **Fullscreen API** – schová rám prohlížeče. Funguje na jakékoli stránce,
 *     tedy i na vývojovém serveru. Zapíná se ale jen tam, kde jde zamknout
 *     Escape (Keyboard Lock): prohlížeč jím fullscreen ukončí a aplikace to
 *     nemůže potlačit, přitom Esc v MongeCADu ruší rozpracovanou konstrukci.
 *  2. **CSS třída `app-maximized`** – roztáhne rám aplikace přes celé okno
 *     stránky. Pravidlo pro ni je ve `style.css` webu, takže má efekt jen na
 *     `aplikace.html`. Na vývojovém serveru appka celé okno vyplňuje sama, tam
 *     tahle vrstva nemá co dělat a jen se přeskočí.
 *
 * Dřív tu byla jen vrstva 2. Na vývojovém serveru proto tlačítko tiše nedělalo
 * nic: `index.html` pravidlo `.app-maximized` nemá a třída se jen přepínala do
 * prázdna.
 */

/**
 * Logování do konzole prohlížeče.
 *
 * `println` z Kotlin/Wasm se do konzole prohlížeče nedostane – proto má i
 * `gl3d` vlastní `console.info` most (`GlDebug.wasmJs.kt`).
 */
@JsFun("(message) => { console.info('[fullscreen] ' + message); }")
private external fun logJs(message: String)

/**
 * Přepne zobrazení a vrátí popis toho, co se skutečně stalo.
 *
 * Zároveň hlídá, jestli přepnutí třídy mělo vůbec nějaký efekt – pokud se rám
 * nezměnil a ani se nejde do fullscreenu, řekne to nahlas místo tichého nic.
 */
@JsFun(
    """() => {
        const el = document.getElementById('webApp');
        if (!el) return 'chyba: #webApp v dokumentu není';

        const goingFullscreen = !(document.fullscreenElement != null
            || el.classList.contains('app-maximized'));
        const heightBefore = el.getBoundingClientRect().height;
        const done = [];

        // 1) Skutečný fullscreen – jen když Escape zůstane aplikaci.
        const canLockEscape = !!(navigator.keyboard && navigator.keyboard.lock);
        if (goingFullscreen) {
            if (canLockEscape && document.documentElement.requestFullscreen) {
                document.documentElement.requestFullscreen()
                    .then(() => navigator.keyboard.lock(['Escape']))
                    .catch((e) => console.info('[fullscreen] Fullscreen API odmítnuto: ' + e));
                done.push('fullscreen');
            }
        } else {
            if (navigator.keyboard && navigator.keyboard.unlock) navigator.keyboard.unlock();
            if (document.fullscreenElement && document.exitFullscreen) {
                document.exitFullscreen().catch(() => {});
                done.push('fullscreen');
            }
        }

        // 2) Rám ve stránce – má efekt jen tam, kde stránka pravidlo zná.
        el.classList.toggle('app-maximized', goingFullscreen);
        document.body.classList.toggle('app-maximized-host', goingFullscreen);
        if (el.getBoundingClientRect().height !== heightBefore) done.push('rám ve stránce');

        // Compose si rozměr plátna bere z kontejneru – ať se o změně dozví
        // hned, i když zrovna nekreslí snímek.
        window.dispatchEvent(new Event('resize'));

        const what = goingFullscreen ? 'zapnuto' : 'vypnuto';
        if (done.length === 0) {
            return what + ', ale bez viditelného efektu – stránka nezná '
                + '.app-maximized a Escape nejde zamknout (Keyboard Lock chybí)';
        }
        return what + ' (' + done.join(' + ') + ')';
    }"""
)
private external fun toggleMaximizedJs(): String

/** Zapnuto = skutečný fullscreen, nebo roztažený rám ve stránce. */
@JsFun(
    """() => {
        const el = document.getElementById('webApp');
        return document.fullscreenElement != null
            || (el != null && el.classList.contains('app-maximized'));
    }"""
)
private external fun isMaximizedJs(): Boolean

actual fun toggleAppFullscreen() {
    runCatching { toggleMaximizedJs() }
        .onSuccess { logJs(it) }
        .onFailure { logJs("přepnutí selhalo: ${it.message}") }
}

actual fun isAppFullscreen(): Boolean = runCatching { isMaximizedJs() }.getOrDefault(false)
