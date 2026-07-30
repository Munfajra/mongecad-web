package gl3d.api

import gl3d.HTMLCanvasElement
import gl3d.WebGl2Backend
import gl3d.getWebGl2Context

/*
 * Hostitelské plátno 3D scény.
 *
 * Leží uvnitř `#webApp` nad Compose plátnem (`z-index: 1`), ale s
 * `pointer-events: none` – veškerý vstup tak propadne na Compose, které si
 * orbit/pan/zoom obslouží stejným způsobem jako 2D plátno. Viz komentář
 * u `GlSurface` v commonMain.
 */

@JsFun(
    """() => {
        const host = document.getElementById('webApp') || document.body;
        if (getComputedStyle(host).position === 'static') host.style.position = 'relative';

        // ComposeViewport si na kontejner připojí shadow DOM (attachShadow,
        // mode OPEN – viz `layerRoot`/`shadowRoot` v ui-wasm-js klibu). Jakmile
        // má element shadow root, jeho light-DOM potomci se vůbec nevykreslují,
        // protože je shadow strom nahradí. Plátno proto musí dovnitř shadow
        // rootu, vedle plátna Composu; do light DOM by bylo neviditelné.
        const mountPoint = host.shadowRoot || host;

        const canvas = document.createElement('canvas');
        canvas.id = 'mongecad-gl';
        canvas.style.position = 'absolute';
        canvas.style.left = '0px';
        canvas.style.top = '0px';
        canvas.style.width = '0px';
        canvas.style.height = '0px';
        // Záporný z-index: plátno leží *pod* plátnem Composu, ale nad pozadím
        // #webApp. Compose si v místě viewportu vyřízne průhlednou díru
        // (BlendMode.Clear v Gl3DViewport), takže je skrz ni 3D vidět a
        // zároveň se všechny dialogy a menu kreslí korektně nad ním.
        canvas.style.zIndex = '-1';
        canvas.style.pointerEvents = 'none';
        canvas.style.display = 'none';
        canvas.__glLost = false;
        canvas.__glRestored = false;
        canvas.__glInShadow = mountPoint !== host;
        canvas.addEventListener('webglcontextlost', (e) => {
            e.preventDefault();
            canvas.__glLost = true;
            console.warn('[gl3d] WebGL kontext ztracen');
        });
        canvas.addEventListener('webglcontextrestored', () => {
            canvas.__glLost = false;
            canvas.__glRestored = true;
            console.info('[gl3d] WebGL kontext obnoven');
        });
        // Před vrstvu Composu i v pořadí dokumentu, ať pořadí vykreslení
        // nezáleží jen na z-indexu.
        mountPoint.insertBefore(canvas, mountPoint.firstChild);
        return canvas;
    }"""
)
private external fun createGlCanvasElement(): HTMLCanvasElement

@JsFun(
    """(canvas, x, y, w, h) => {
        const dpr = window.devicePixelRatio || 1;
        canvas.style.left = x + 'px';
        canvas.style.top = y + 'px';
        canvas.style.width = w + 'px';
        canvas.style.height = h + 'px';
        const pw = Math.max(1, Math.round(w * dpr));
        const ph = Math.max(1, Math.round(h * dpr));
        if (canvas.width !== pw) canvas.width = pw;
        if (canvas.height !== ph) canvas.height = ph;
    }"""
)
private external fun setGlCanvasRect(
    canvas: HTMLCanvasElement,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
)

@JsFun("(canvas) => canvas.width")
private external fun glCanvasWidth(canvas: HTMLCanvasElement): Int

@JsFun("(canvas) => canvas.height")
private external fun glCanvasHeight(canvas: HTMLCanvasElement): Int

@JsFun("(canvas, visible) => { canvas.style.display = visible ? 'block' : 'none'; }")
private external fun setGlCanvasVisible(canvas: HTMLCanvasElement, visible: Boolean)

@JsFun("(canvas) => canvas.__glLost === true")
private external fun isGlContextLost(canvas: HTMLCanvasElement): Boolean

@JsFun(
    """(canvas) => {
        const restored = canvas.__glRestored === true;
        canvas.__glRestored = false;
        return restored;
    }"""
)
private external fun consumeGlContextRestored(canvas: HTMLCanvasElement): Boolean

@JsFun("(canvas) => { if (canvas.parentNode) canvas.parentNode.removeChild(canvas); }")
private external fun removeGlCanvas(canvas: HTMLCanvasElement)

/** Popis skutečného stavu plátna v DOM – pomáhá u problémů se zobrazením. */
@JsFun(
    """(canvas) => {
        const rect = canvas.getBoundingClientRect();
        const style = getComputedStyle(canvas);
        const parent = canvas.parentNode;
        const siblings = Array.from(parent ? parent.children : []).map(
            (el) => el.tagName + (el.id ? '#' + el.id : '') +
                ' z=' + getComputedStyle(el).zIndex
        ).join(' | ');
        return 'plátno rect=' + Math.round(rect.left) + ',' + Math.round(rect.top) +
            ' ' + Math.round(rect.width) + '×' + Math.round(rect.height) +
            ' backing=' + canvas.width + '×' + canvas.height +
            ' display=' + style.display + ' z=' + style.zIndex +
            ' | v shadow rootu=' + (canvas.__glInShadow === true) +
            ' | sourozenci: ' + siblings;
    }"""
)
private external fun describeGlCanvas(canvas: HTMLCanvasElement): String

private class WebGlSurface(
    private val canvas: HTMLCanvasElement,
    override val gl: Gl,
) : GlSurface {

    override val pixelWidth: Int get() = glCanvasWidth(canvas)
    override val pixelHeight: Int get() = glCanvasHeight(canvas)

    override fun setRect(x: Float, y: Float, width: Float, height: Float) {
        setGlCanvasRect(canvas, x, y, width, height)
        if (describedTimes < 2) {
            describedTimes++
            gl3dLog(describeGlCanvas(canvas))
        }
    }

    private var describedTimes = 0

    override fun setVisible(visible: Boolean) = setGlCanvasVisible(canvas, visible)

    override fun consumeContextRestored(): Boolean = consumeGlContextRestored(canvas)

    override fun isContextLost(): Boolean = isGlContextLost(canvas)

    override fun dispose() = removeGlCanvas(canvas)
}

actual fun createGlSurface(): GlSurface? = runCatching {
    gl3dLog("createGlSurface: začínám")
    val canvas = createGlCanvasElement()
    // Stencil je povinný – maskování skrytých hran siluetou těles stojí na
    // stencil bufferu. Hloubka i stencil jsou potřeba jen pro nouzovou cestu
    // bez OIT, která kreslí rovnou na plátno; s OIT má scéna vlastní offscreen
    // cíl.
    //
    // MSAA je schválně vypnuté: vícevzorkový výchozí framebuffer nejde sdílet
    // s OIT bufferem a rozlišení vzorků by si vyžádalo další kopie. Hrany čar
    // se místo toho vyhlazují analyticky ve `LineShaders`, což u tenkých čar
    // vypadá lépe než čtyřnásobné vzorkování.
    val context = getWebGl2Context(canvas, stencil = true, antialias = false)
    if (context == null) {
        gl3dLog("createGlSurface: WebGL2 kontext se nepodařilo získat")
        removeGlCanvas(canvas)
        null
    } else {
        val backend = WebGl2Backend(context)
        gl3dLog("createGlSurface: kontext OK, ${backend.rendererInfo()}")
        WebGlSurface(canvas, backend)
    }
}.onFailure { gl3dLog("createGlSurface: výjimka ${it.message}") }.getOrNull()
