package gl3d.render

import gl3d.api.Gl
import gl3d.api.GlBlend
import gl3d.api.GlCap
import gl3d.api.GlColorFormat
import gl3d.api.GlCompare
import gl3d.api.GlFramebuffer
import gl3d.api.GlPrimitive
import gl3d.api.GlProgram
import gl3d.api.GlRenderbuffer
import gl3d.api.GlTexture
import gl3d.api.GlUniform
import gl3d.api.gl3dLog

/**
 * Weighted blended OIT (McGuire & Bavoil 2013) – míchání průhledných ploch
 * nezávislé na pořadí. Port `opengl/OitRenderer.kt`.
 *
 * ## Proč to tady vypadá jinak než na desktopu
 *
 * Desktop akumuluje do dvou cílů s **různými** blend funkcemi
 * (`glBlendFunci(0, ONE, ONE)` a `glBlendFunci(1, ZERO, ONE_MINUS_SRC_COLOR)`).
 * WebGL2 indexovaný blend v jádře nemá – blend funkce je jedna pro všechny
 * výstupy. Řeší se to logaritmem: revealage je součin `Π(1−aᵢ)`, což je
 * `exp(Σ ln(1−aᵢ))`, takže se dá akumulovat **sčítáním** stejně jako
 * numerátor. Oba cíle tak jedou na `(ONE, ONE)` a kompozit jen umocní.
 *
 * ## Proč se kreslí do offscreen bufferu
 *
 * Průhledný průchod musí testovat hloubku proti neprůhledné geometrii, ale
 * do ní nezapisovat. Desktop si hloubku do OIT bufferu překopíruje
 * `glBlitFramebuffer`. Tady místo toho scéna i OIT sdílí tentýž
 * depth/stencil renderbuffer, takže odpadá kopírování i hádání, jestli má
 * výchozí framebuffer prohlížeče kompatibilní formát hloubky.
 *
 * Vedlejší důsledek: plátno si nesmí zapnout MSAA (vícevzorkový výchozí
 * framebuffer by nešel sdílet), proto se hrany čar vyhlazují analyticky
 * přímo v `LineShaders`.
 */
class OitPipeline(private val gl: Gl) {

    private var composite: GlProgram? = null
    private var uAccum: GlUniform? = null
    private var uReveal: GlUniform? = null

    private var sceneFbo: GlFramebuffer? = null
    private var sceneColor: GlTexture? = null
    private var depthStencil: GlRenderbuffer? = null

    private var oitFbo: GlFramebuffer? = null
    private var accumTexture: GlTexture? = null
    private var revealTexture: GlTexture? = null

    private var width = 0
    private var height = 0

    /** Je OIT k dispozici? Bez plovoucích cílů se musí míchat přímo. */
    var isSupported = false
        private set

    fun initialize(): Boolean {
        if (composite != null) return isSupported

        if (!gl.supportsFloatColorBuffer()) {
            gl3dLog("OIT: chybí EXT_color_buffer_float, průhlednost se míchá přímo")
            isSupported = false
            return false
        }

        val program = gl.createProgram("oitComposite", COMPOSITE_VERTEX, COMPOSITE_FRAGMENT)
        if (program == null) {
            gl3dLog("OIT: kompozitní program se nepodařilo přeložit")
            isSupported = false
            return false
        }
        composite = program
        uAccum = gl.uniformLocation(program, "uAccum")
        uReveal = gl.uniformLocation(program, "uReveal")
        isSupported = true
        return true
    }

    /**
     * Začátek snímku – naváže offscreen cíl scény a vyčistí ho.
     * Vrací `false`, když OIT není k dispozici a má se kreslit rovnou na plátno.
     */
    fun beginScene(
        width: Int,
        height: Int,
        red: Float,
        green: Float,
        blue: Float,
    ): Boolean {
        if (!isSupported) return false
        if (!ensureSize(width, height)) {
            isSupported = false
            return false
        }
        gl.bindFramebuffer(sceneFbo)
        gl.viewport(0, 0, width, height)
        gl.clearColor(red, green, blue, 1f)
        gl.clear(color = true, depth = true, stencil = true)
        return true
    }

    /** Průhledný průchod: akumulace místo přímého míchání. */
    fun beginTransparent() {
        val fbo = oitFbo ?: return
        gl.bindFramebuffer(fbo)
        gl.viewport(0, 0, width, height)
        // Numerátor začíná na nule, součet logaritmů taky – exp(0) = 1,
        // tedy „nic nezakryto“.
        gl.clearColorBuffer(0, 0f, 0f, 0f, 0f)
        gl.clearColorBuffer(1, 0f, 0f, 0f, 0f)
        gl.enable(GlCap.DEPTH_TEST)
        gl.depthFunc(GlCompare.LESS)
        gl.depthMask(false)
        gl.enable(GlCap.BLEND)
        gl.blendFunc(GlBlend.ONE, GlBlend.ONE)
    }

    /** Složí naakumulovanou průhlednost zpět na scénu. */
    fun endTransparent() {
        val program = composite ?: return
        val scene = sceneFbo ?: return

        gl.bindFramebuffer(scene)
        gl.viewport(0, 0, width, height)
        gl.disable(GlCap.DEPTH_TEST)
        gl.depthMask(false)
        gl.enable(GlCap.BLEND)
        // FragColor nese (barva, revealage): výsledek = barva·(1−reveal) + cíl·reveal.
        //
        // Alfa cíle se přitom musí zachovat. Kdyby se míchala stejným
        // vzorcem jako barva, klesla by pod jedničku všude, kde leží rovina –
        // plátno by v těch místech bylo poloprůhledné a prosvítalo by skrz
        // něj Compose pod ním.
        gl.blendFuncSeparate(
            srcRgb = GlBlend.ONE_MINUS_SRC_ALPHA,
            dstRgb = GlBlend.SRC_ALPHA,
            srcAlpha = GlBlend.ZERO,
            dstAlpha = GlBlend.ONE,
        )

        gl.useProgram(program)
        gl.bindTexture(0, accumTexture)
        gl.uniform1i(uAccum, 0)
        gl.bindTexture(1, revealTexture)
        gl.uniform1i(uReveal, 1)

        gl.drawArrays(GlPrimitive.TRIANGLES, 0, 3)

        gl.bindTexture(1, null)
        gl.bindTexture(0, null)
        gl.useProgram(null)

        gl.enable(GlCap.DEPTH_TEST)
        gl.depthMask(true)
        gl.blendFuncSeparate(
            srcRgb = GlBlend.SRC_ALPHA,
            dstRgb = GlBlend.ONE_MINUS_SRC_ALPHA,
            srcAlpha = GlBlend.ONE,
            dstAlpha = GlBlend.ONE_MINUS_SRC_ALPHA,
        )
    }

    /** Konec snímku – hotový obraz na plátno. */
    fun endScene() {
        val scene = sceneFbo ?: return
        gl.blitColorToScreen(scene, width, height)
        gl.bindFramebuffer(null)
    }

    private fun ensureSize(newWidth: Int, newHeight: Int): Boolean {
        if (sceneFbo != null && width == newWidth && height == newHeight) return true
        releaseTargets()
        width = newWidth
        height = newHeight
        if (width <= 0 || height <= 0) return false

        val depth = gl.createRenderbuffer()
        gl.allocateDepthStencil(depth, width, height)
        depthStencil = depth

        val color = gl.createTexture()
        gl.allocateTexture(color, GlColorFormat.RGBA8, width, height)
        sceneColor = color

        val scene = gl.createFramebuffer()
        gl.attachColorTexture(scene, 0, color)
        gl.attachDepthStencil(scene, depth)
        gl.drawBuffers(scene, 1)
        sceneFbo = scene

        val accum = gl.createTexture()
        gl.allocateTexture(accum, GlColorFormat.RGBA16F, width, height)
        accumTexture = accum

        val reveal = gl.createTexture()
        gl.allocateTexture(reveal, GlColorFormat.RGBA16F, width, height)
        revealTexture = reveal

        val oit = gl.createFramebuffer()
        gl.attachColorTexture(oit, 0, accum)
        gl.attachColorTexture(oit, 1, reveal)
        // Tentýž renderbuffer jako scéna – odtud sdílená hloubka.
        gl.attachDepthStencil(oit, depth)
        gl.drawBuffers(oit, 2)
        oitFbo = oit

        val complete = gl.isFramebufferComplete(scene) && gl.isFramebufferComplete(oit)
        if (!complete) {
            gl3dLog("OIT: framebuffer není kompletní, průhlednost se míchá přímo")
            releaseTargets()
            return false
        }
        gl.bindFramebuffer(null)
        return true
    }

    private fun releaseTargets() {
        oitFbo?.let(gl::deleteFramebuffer)
        sceneFbo?.let(gl::deleteFramebuffer)
        accumTexture?.let(gl::deleteTexture)
        revealTexture?.let(gl::deleteTexture)
        sceneColor?.let(gl::deleteTexture)
        depthStencil?.let(gl::deleteRenderbuffer)
        oitFbo = null
        sceneFbo = null
        accumTexture = null
        revealTexture = null
        sceneColor = null
        depthStencil = null
        width = 0
        height = 0
    }

    fun dispose() {
        releaseTargets()
        composite?.let(gl::deleteProgram)
        composite = null
        isSupported = false
    }
}

/** Celoobrazovkový trojúhelník bez bufferu – vrcholy se dopočítají z `gl_VertexID`. */
private const val COMPOSITE_VERTEX = """#version 300 es
precision highp float;

out vec2 vUv;

void main() {
    vec2 p = vec2(
        (gl_VertexID == 1) ? 3.0 : -1.0,
        (gl_VertexID == 2) ? 3.0 : -1.0
    );
    vUv = p * 0.5 + 0.5;
    gl_Position = vec4(p, 0.0, 1.0);
}
"""

private const val COMPOSITE_FRAGMENT = """#version 300 es
precision highp float;

in vec2 vUv;
layout(location = 0) out vec4 FragColor;

uniform sampler2D uAccum;
uniform sampler2D uReveal;

void main() {
    vec4 accum = texture(uAccum, vUv);
    // Součet logaritmů zpět na součin (1−aᵢ) – viz komentář u OitPipeline.
    float reveal = exp(texture(uReveal, vUv).r);

    if (accum.a < 1e-5 && reveal > 1.0 - 1e-5) discard;

    vec3 color = accum.rgb / max(accum.a, 1e-5);
    FragColor = vec4(color, clamp(reveal, 0.0, 1.0));
}
"""
