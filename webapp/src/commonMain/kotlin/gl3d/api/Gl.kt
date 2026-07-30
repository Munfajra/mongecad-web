package gl3d.api

/**
 * Tenká abstrakce nad grafickým API.
 *
 * Záměrně **není** `expect/actual`: renderovací pipeline (port desktopového
 * `RenderScene.kt`) tak může celá zůstat v `commonMain`, testovat se proti
 * záznamovému fake backendu a případný pozdější WebGPU backend se přidá bez
 * zásahu do pipeline.
 *
 * Tvar rozhraní kopíruje WebGL2, protože to je cílové API; desktopová GL 3.3
 * volání se na něj mapují 1:1 s výjimkami popsanými ve `WEBGL_3D_PLAN.md`
 * (šířka čar, indexovaný blend, fixní pipeline matic).
 */
interface Gl {

    // --- stav rasterizace -------------------------------------------------

    fun viewport(x: Int, y: Int, width: Int, height: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(color: Boolean = false, depth: Boolean = false, stencil: Boolean = false)

    fun enable(cap: GlCap)
    fun disable(cap: GlCap)

    fun depthFunc(func: GlCompare)
    fun depthMask(writeEnabled: Boolean)
    fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean)

    fun blendFunc(src: GlBlend, dst: GlBlend)
    fun blendFuncSeparate(srcRgb: GlBlend, dstRgb: GlBlend, srcAlpha: GlBlend, dstAlpha: GlBlend)

    fun stencilFunc(func: GlCompare, ref: Int, mask: Int)
    fun stencilOp(sfail: GlStencilOp, dpfail: GlStencilOp, dppass: GlStencilOp)
    fun stencilMask(mask: Int)

    // --- programy ---------------------------------------------------------

    /** Vrací `null` a zaloguje chybu, pokud se shader nepodaří přeložit. */
    fun createProgram(name: String, vertexSource: String, fragmentSource: String): GlProgram?
    fun useProgram(program: GlProgram?)
    fun deleteProgram(program: GlProgram)

    fun uniformLocation(program: GlProgram, name: String): GlUniform?
    fun uniform1f(location: GlUniform?, v: Float)
    fun uniform2f(location: GlUniform?, x: Float, y: Float)
    fun uniform3f(location: GlUniform?, x: Float, y: Float, z: Float)
    fun uniform4f(location: GlUniform?, x: Float, y: Float, z: Float, w: Float)
    fun uniform1i(location: GlUniform?, v: Int)
    /** `values` je sloupcová 4×4 matice, přesně jak ji dává [gl3d.math.Mat4.data]. */
    fun uniformMatrix4fv(location: GlUniform?, values: FloatArray)

    /** `values` je sloupcová 3×3 matice (9 čísel). */
    fun uniformMatrix3fv(location: GlUniform?, values: FloatArray)

    // --- buffery a atributy -----------------------------------------------

    fun createBuffer(): GlBuffer
    fun deleteBuffer(buffer: GlBuffer)
    fun bindArrayBuffer(buffer: GlBuffer?)
    /**
     * Nahraje data do právě navázaného ARRAY_BUFFER.
     *
     * `floatCount` umožňuje poslat jen využitý začátek pole – sběrače
     * geometrie si drží rostoucí buffery, které se recyklují mezi snímky.
     */
    fun arrayBufferData(data: FloatArray, floatCount: Int = data.size, dynamic: Boolean = false)

    fun bindElementArrayBuffer(buffer: GlBuffer?)

    /**
     * Nahraje indexy do právě navázaného ELEMENT_ARRAY_BUFFER; hodnoty se
     * berou jako `UNSIGNED_INT`, takže sítě nejsou omezené na 65 536 vrcholů.
     */
    fun elementArrayBufferData(data: IntArray, count: Int = data.size)

    fun createVertexArray(): GlVertexArray
    fun deleteVertexArray(vao: GlVertexArray)
    fun bindVertexArray(vao: GlVertexArray?)

    fun vertexAttribPointer(index: Int, size: Int, strideFloats: Int, offsetFloats: Int)
    fun enableVertexAttribArray(index: Int)

    /**
     * `divisor = 0` je běžný atribut po vrcholu, `1` po instanci. Instancing
     * je jádro kreslení tlustých čar: statický quad ze 6 vrcholů se instancuje
     * jednou na každý úsek (viz `ThickLineRenderer`).
     */
    fun vertexAttribDivisor(index: Int, divisor: Int)

    fun drawArrays(mode: GlPrimitive, first: Int, count: Int)
    fun drawArraysInstanced(mode: GlPrimitive, first: Int, count: Int, instanceCount: Int)

    /** Kreslení z indexů; `offsetInts` je posun v položkách, ne v bajtech. */
    fun drawElements(mode: GlPrimitive, count: Int, offsetInts: Int = 0)

    /** Která strana se zahazuje, když je zapnutý [GlCap.CULL_FACE]. */
    fun cullFace(face: GlFace)

    /**
     * Posun hloubky vyplněných trojúhelníků. Podstava hranolu bývá koplanární
     * s rovinou, ve které se konstruovala, a stejné hloubky by se praly
     * o pixely; desktop to řeší stejně (`SegmentSolids.kt`).
     */
    fun polygonOffset(factor: Float, units: Float)

    // --- cíle vykreslování ------------------------------------------------

    /**
     * Umí GPU renderovat do plovoucích barevných cílů (`RGBA16F`)?
     *
     * Ve WebGL2 je to rozšíření `EXT_color_buffer_float`. Bez něj nejde
     * postavit akumulační buffer pro OIT a průhledné plochy se musí míchat
     * přímo, seřazené podle hloubky.
     */
    fun supportsFloatColorBuffer(): Boolean

    fun createTexture(): GlTexture
    fun deleteTexture(texture: GlTexture)
    /** Alokuje texturu dané velikosti a formátu, s lineárním filtrem a clamp. */
    fun allocateTexture(texture: GlTexture, format: GlColorFormat, width: Int, height: Int)
    /** Naváže texturu na daný texturovací kanál. */
    fun bindTexture(unit: Int, texture: GlTexture?)

    fun createRenderbuffer(): GlRenderbuffer
    fun deleteRenderbuffer(renderbuffer: GlRenderbuffer)
    fun allocateDepthStencil(renderbuffer: GlRenderbuffer, width: Int, height: Int)

    fun createFramebuffer(): GlFramebuffer
    fun deleteFramebuffer(framebuffer: GlFramebuffer)
    /** `null` znamená plátno, tedy výchozí framebuffer. */
    fun bindFramebuffer(framebuffer: GlFramebuffer?)

    fun attachColorTexture(framebuffer: GlFramebuffer, index: Int, texture: GlTexture)
    fun attachDepthStencil(framebuffer: GlFramebuffer, renderbuffer: GlRenderbuffer)
    /** Zapne zápis do prvních `count` barevných výstupů. */
    fun drawBuffers(framebuffer: GlFramebuffer, count: Int)
    fun isFramebufferComplete(framebuffer: GlFramebuffer): Boolean

    /** Vymaže jeden barevný výstup právě navázaného framebufferu. */
    fun clearColorBuffer(index: Int, r: Float, g: Float, b: Float, a: Float)

    /** Zkopíruje barvu z `source` na plátno (výchozí framebuffer). */
    fun blitColorToScreen(source: GlFramebuffer, width: Int, height: Int)

    /**
     * Přečte barvu právě navázaného framebufferu jako RGBA8, řádek po řádku
     * **zdola nahoru** (počátek GL je vlevo dole). Slouží k exportu snímku.
     */
    fun readPixels(width: Int, height: Int): ByteArray

    // --- diagnostika ------------------------------------------------------

    /** Jméno GPU/ovladače pro log a hlášení chyb. */
    fun rendererInfo(): String
}

// Neprůhledné handly. Backend si za ně schová své nativní objekty.
interface GlProgram
interface GlBuffer
interface GlVertexArray
interface GlUniform
interface GlTexture
interface GlRenderbuffer
interface GlFramebuffer

enum class GlColorFormat { RGBA8, RGBA16F }

enum class GlCap { DEPTH_TEST, BLEND, STENCIL_TEST, CULL_FACE, SCISSOR_TEST, POLYGON_OFFSET_FILL }

enum class GlFace { FRONT, BACK }

enum class GlCompare { NEVER, LESS, EQUAL, LEQUAL, GREATER, NOTEQUAL, GEQUAL, ALWAYS }

enum class GlBlend {
    ZERO, ONE,
    SRC_ALPHA, ONE_MINUS_SRC_ALPHA,
    SRC_COLOR, ONE_MINUS_SRC_COLOR,
    DST_ALPHA, ONE_MINUS_DST_ALPHA,
}

enum class GlStencilOp { KEEP, ZERO, REPLACE, INCR, DECR, INVERT }

enum class GlPrimitive { POINTS, LINES, LINE_STRIP, TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }
