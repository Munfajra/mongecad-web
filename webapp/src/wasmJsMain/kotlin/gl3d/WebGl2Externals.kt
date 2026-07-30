package gl3d

/*
 * Vlastní deklarace WebGL2.
 *
 * Projekt nemá `kotlinx-browser` (celý web interop stojí na `@JsFun`) a ani
 * kdyby ho měl, WebGL2 v něm není – obsahuje jen WebGL1. Deklarujeme si proto
 * jen to, co renderer opravdu volá, pod skutečnými jmény globálů v prohlížeči.
 */

external abstract class HTMLElement : JsAny
external abstract class HTMLCanvasElement : JsAny
external abstract class Float32Array : JsAny
external abstract class Uint32Array : JsAny
external abstract class Uint8Array : JsAny

external abstract class WebGLProgram : JsAny
external abstract class WebGLBuffer : JsAny
external abstract class WebGLVertexArrayObject : JsAny
external abstract class WebGLUniformLocation : JsAny
external abstract class WebGLTexture : JsAny
external abstract class WebGLRenderbuffer : JsAny
external abstract class WebGLFramebuffer : JsAny

external abstract class WebGL2RenderingContext : JsAny {

    fun viewport(x: Int, y: Int, width: Int, height: Int)
    fun clearColor(red: Float, green: Float, blue: Float, alpha: Float)
    fun clear(mask: Int)

    fun enable(cap: Int)
    fun disable(cap: Int)

    fun depthFunc(func: Int)
    fun depthMask(flag: Boolean)
    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean)

    fun blendFunc(sfactor: Int, dfactor: Int)
    fun blendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int)

    fun stencilFunc(func: Int, ref: Int, mask: Int)
    fun stencilOp(fail: Int, zfail: Int, zpass: Int)
    fun stencilMask(mask: Int)

    fun useProgram(program: WebGLProgram?)
    fun deleteProgram(program: WebGLProgram?)
    fun getUniformLocation(program: WebGLProgram, name: String): WebGLUniformLocation?

    fun uniform1f(location: WebGLUniformLocation?, x: Float)
    fun uniform2f(location: WebGLUniformLocation?, x: Float, y: Float)
    fun uniform3f(location: WebGLUniformLocation?, x: Float, y: Float, z: Float)
    fun uniform4f(location: WebGLUniformLocation?, x: Float, y: Float, z: Float, w: Float)
    fun uniform1i(location: WebGLUniformLocation?, x: Int)
    fun uniformMatrix4fv(location: WebGLUniformLocation?, transpose: Boolean, value: Float32Array)
    fun uniformMatrix3fv(location: WebGLUniformLocation?, transpose: Boolean, value: Float32Array)

    fun createBuffer(): WebGLBuffer?
    fun deleteBuffer(buffer: WebGLBuffer?)
    fun bindBuffer(target: Int, buffer: WebGLBuffer?)
    fun bufferData(target: Int, srcData: Float32Array, usage: Int)

    fun createVertexArray(): WebGLVertexArrayObject?
    fun deleteVertexArray(vertexArray: WebGLVertexArrayObject?)
    fun bindVertexArray(array: WebGLVertexArrayObject?)

    fun vertexAttribPointer(
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        stride: Int,
        offset: Int,
    )

    fun enableVertexAttribArray(index: Int)
    fun vertexAttribDivisor(index: Int, divisor: Int)
    fun drawArrays(mode: Int, first: Int, count: Int)
    fun drawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int)
    fun drawElements(mode: Int, count: Int, type: Int, offset: Int)

    fun cullFace(mode: Int)
    fun polygonOffset(factor: Float, units: Float)

    fun createTexture(): WebGLTexture?
    fun deleteTexture(texture: WebGLTexture?)
    fun bindTexture(target: Int, texture: WebGLTexture?)
    fun activeTexture(texture: Int)
    fun texParameteri(target: Int, pname: Int, param: Int)

    fun createRenderbuffer(): WebGLRenderbuffer?
    fun deleteRenderbuffer(renderbuffer: WebGLRenderbuffer?)
    fun bindRenderbuffer(target: Int, renderbuffer: WebGLRenderbuffer?)
    fun renderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int)

    fun createFramebuffer(): WebGLFramebuffer?
    fun deleteFramebuffer(framebuffer: WebGLFramebuffer?)
    fun bindFramebuffer(target: Int, framebuffer: WebGLFramebuffer?)
    fun checkFramebufferStatus(target: Int): Int
    fun framebufferTexture2D(
        target: Int,
        attachment: Int,
        textarget: Int,
        texture: WebGLTexture?,
        level: Int,
    )

    fun framebufferRenderbuffer(
        target: Int,
        attachment: Int,
        renderbuffertarget: Int,
        renderbuffer: WebGLRenderbuffer?,
    )

    fun blitFramebuffer(
        srcX0: Int, srcY0: Int, srcX1: Int, srcY1: Int,
        dstX0: Int, dstY0: Int, dstX1: Int, dstY1: Int,
        mask: Int, filter: Int,
    )
}

/** Konstanty WebGL2. Hodnoty jsou stejné jako v desktopovém OpenGL. */
internal object GlConst {
    const val DEPTH_BUFFER_BIT = 0x00000100
    const val STENCIL_BUFFER_BIT = 0x00000400
    const val COLOR_BUFFER_BIT = 0x00004000

    const val POINTS = 0x0000
    const val LINES = 0x0001
    const val LINE_STRIP = 0x0003
    const val TRIANGLES = 0x0004
    const val TRIANGLE_STRIP = 0x0005
    const val TRIANGLE_FAN = 0x0006

    const val NEVER = 0x0200
    const val LESS = 0x0201
    const val EQUAL = 0x0202
    const val LEQUAL = 0x0203
    const val GREATER = 0x0204
    const val NOTEQUAL = 0x0205
    const val GEQUAL = 0x0206
    const val ALWAYS = 0x0207

    const val ZERO = 0
    const val ONE = 1
    const val SRC_COLOR = 0x0300
    const val ONE_MINUS_SRC_COLOR = 0x0301
    const val SRC_ALPHA = 0x0302
    const val ONE_MINUS_SRC_ALPHA = 0x0303
    const val DST_ALPHA = 0x0304
    const val ONE_MINUS_DST_ALPHA = 0x0305

    const val KEEP = 0x1E00
    const val REPLACE = 0x1E01
    const val INCR = 0x1E02
    const val DECR = 0x1E03
    const val INVERT = 0x150A

    const val CULL_FACE = 0x0B44
    const val DEPTH_TEST = 0x0B71
    const val STENCIL_TEST = 0x0B90
    const val BLEND = 0x0BE2
    const val SCISSOR_TEST = 0x0C11

    const val FRONT = 0x0404
    const val BACK = 0x0405
    const val POLYGON_OFFSET_FILL = 0x8037

    const val FLOAT = 0x1406
    const val UNSIGNED_INT = 0x1405
    const val ARRAY_BUFFER = 0x8892
    const val ELEMENT_ARRAY_BUFFER = 0x8893
    const val STATIC_DRAW = 0x88E4
    const val DYNAMIC_DRAW = 0x88E8

    const val TEXTURE_2D = 0x0DE1
    const val TEXTURE0 = 0x84C0
    const val TEXTURE_MAG_FILTER = 0x2800
    const val TEXTURE_MIN_FILTER = 0x2801
    const val TEXTURE_WRAP_S = 0x2802
    const val TEXTURE_WRAP_T = 0x2803
    const val LINEAR = 0x2601
    const val NEAREST = 0x2600
    const val CLAMP_TO_EDGE = 0x812F
    const val RGBA = 0x1908
    const val RGBA8 = 0x8058
    const val RGBA16F = 0x881A
    const val HALF_FLOAT = 0x140B
    const val UNSIGNED_BYTE = 0x1401

    const val RENDERBUFFER = 0x8D41
    const val DEPTH24_STENCIL8 = 0x88F0
    const val DEPTH_STENCIL_ATTACHMENT = 0x821A

    const val FRAMEBUFFER = 0x8D40
    const val READ_FRAMEBUFFER = 0x8CA8
    const val DRAW_FRAMEBUFFER = 0x8CA9
    const val FRAMEBUFFER_COMPLETE = 0x8CD5
    const val COLOR_ATTACHMENT0 = 0x8CE0
}

// --- pomocné JS funkce ---------------------------------------------------

@JsFun(
    // `powerPreference` se schválně nenastavuje: `aplikace.html` přepisuje
    // `getContext` a všem WebGL kontextům vnucuje `low-power`, protože Vivaldi
    // ten výkonný kontext při přehrávání videa na jiné kartě zahazuje.
    """(canvas, stencil, antialias) => canvas.getContext('webgl2', {
        alpha: true,
        depth: true,
        stencil: stencil,
        antialias: antialias,
        premultipliedAlpha: true,
        preserveDrawingBuffer: false
    })"""
)
internal external fun getWebGl2Context(
    canvas: HTMLCanvasElement,
    stencil: Boolean,
    antialias: Boolean,
): WebGL2RenderingContext?

/**
 * Překlad a slinkování programu v jednom JS volání.
 *
 * Kotlinská varianta by musela sahat na `getShaderParameter`, které vrací
 * netypovanou JS hodnotu; takhle je chybová cesta na jednom místě a chyba
 * skončí v konzoli se jménem programu, což se při portu shaderů hodí nejvíc.
 */
@JsFun(
    """(gl, name, vsSrc, fsSrc) => {
        const compile = (type, src, kind) => {
            const sh = gl.createShader(type);
            gl.shaderSource(sh, src);
            gl.compileShader(sh);
            if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
                console.error('[gl3d] ' + name + ' – ' + kind + ' shader:\n' + gl.getShaderInfoLog(sh));
                gl.deleteShader(sh);
                return null;
            }
            return sh;
        };
        const vs = compile(gl.VERTEX_SHADER, vsSrc, 'vertex');
        if (!vs) return null;
        const fs = compile(gl.FRAGMENT_SHADER, fsSrc, 'fragment');
        if (!fs) { gl.deleteShader(vs); return null; }
        const prog = gl.createProgram();
        gl.attachShader(prog, vs);
        gl.attachShader(prog, fs);
        gl.linkProgram(prog);
        gl.detachShader(prog, vs); gl.deleteShader(vs);
        gl.detachShader(prog, fs); gl.deleteShader(fs);
        if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
            console.error('[gl3d] ' + name + ' – link:\n' + gl.getProgramInfoLog(prog));
            gl.deleteProgram(prog);
            return null;
        }
        return prog;
    }"""
)
internal external fun compileGlProgram(
    gl: WebGL2RenderingContext,
    name: String,
    vertexSource: String,
    fragmentSource: String,
): WebGLProgram?

@JsFun(
    """(gl) => {
        const ext = gl.getExtension('WEBGL_debug_renderer_info');
        if (ext) return String(gl.getParameter(ext.UNMASKED_RENDERER_WEBGL));
        return String(gl.getParameter(gl.RENDERER));
    }"""
)
internal external fun glRendererInfo(gl: WebGL2RenderingContext): String

/*
 * Volání, která berou pole nebo `null`. Přes deklarované rozhraní by to
 * znamenalo protahovat JS pole Kotlinem, tady je to jedno krátké JS volání.
 */

@JsFun("(gl) => gl.getExtension('EXT_color_buffer_float') != null")
internal external fun glEnableFloatColorBuffer(gl: WebGL2RenderingContext): Boolean

@JsFun(
    """(gl, count) => {
        const buffers = [];
        for (let i = 0; i < count; i++) buffers.push(gl.COLOR_ATTACHMENT0 + i);
        gl.drawBuffers(buffers);
    }"""
)
internal external fun glDrawBuffersCount(gl: WebGL2RenderingContext, count: Int)

@JsFun("(gl, index, r, g, b, a) => gl.clearBufferfv(gl.COLOR, index, [r, g, b, a])")
internal external fun glClearColorBufferAt(
    gl: WebGL2RenderingContext,
    index: Int,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
)

@JsFun(
    """(gl, internalFormat, width, height, format, type) => {
        gl.texImage2D(gl.TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);
    }"""
)
internal external fun glAllocateTexture(
    gl: WebGL2RenderingContext,
    internalFormat: Int,
    width: Int,
    height: Int,
    format: Int,
    type: Int,
)

@JsFun("(gl, target, srcData, usage) => gl.bufferData(target, srcData, usage)")
internal external fun glBufferDataUint32(
    gl: WebGL2RenderingContext,
    target: Int,
    srcData: Uint32Array,
    usage: Int,
)

@JsFun("(size) => new Float32Array(size)")
internal external fun newFloat32Array(size: Int): Float32Array

@JsFun("(array, index, value) => { array[index] = value; }")
internal external fun float32Set(array: Float32Array, index: Int, value: Float)

@JsFun("(size) => new Uint32Array(size)")
internal external fun newUint32Array(size: Int): Uint32Array

@JsFun("(array, index, value) => { array[index] = value; }")
internal external fun uint32Set(array: Uint32Array, index: Int, value: Int)

/**
 * Přečtení barvy do nového `Uint8Array`. Formát i typ jsou napevno RGBA8 –
 * jediné, co WebGL2 u výchozího framebufferu spolehlivě umí.
 */
@JsFun(
    """(gl, width, height) => {
        const out = new Uint8Array(width * height * 4);
        gl.readPixels(0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, out);
        return out;
    }"""
)
internal external fun glReadPixelsRgba(
    gl: WebGL2RenderingContext,
    width: Int,
    height: Int,
): Uint8Array

@JsFun("(array, index) => array[index]")
internal external fun uint8Get(array: Uint8Array, index: Int): Int

/**
 * Kotlin/Wasm nesdílí lineární paměť s JS typed arrays, takže se floaty
 * musí přepsat po jednom. Je to jediné místo, kde se to děje – veškerá
 * geometrie se cachuje a přelévá jen při změně scény, ne každý snímek
 * (viz `WEBGL_3D_PLAN.md`, bod 4/#11).
 */
internal fun FloatArray.toFloat32Array(count: Int = size): Float32Array {
    val n = count.coerceIn(0, size)
    val out = newFloat32Array(n)
    for (i in 0 until n) float32Set(out, i, this[i])
    return out
}

/** Totéž pro indexy sítí; platí o ní stejná poznámka o cachování jako výše. */
internal fun IntArray.toUint32Array(count: Int = size): Uint32Array {
    val n = count.coerceIn(0, size)
    val out = newUint32Array(n)
    for (i in 0 until n) uint32Set(out, i, this[i])
    return out
}
