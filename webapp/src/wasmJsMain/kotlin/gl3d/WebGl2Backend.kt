package gl3d

import gl3d.api.Gl
import gl3d.api.GlBlend
import gl3d.api.GlBuffer
import gl3d.api.GlCap
import gl3d.api.GlCompare
import gl3d.api.GlFace
import gl3d.api.GlPrimitive
import gl3d.api.GlColorFormat
import gl3d.api.GlFramebuffer
import gl3d.api.GlRenderbuffer
import gl3d.api.GlTexture
import gl3d.api.GlProgram
import gl3d.api.GlStencilOp
import gl3d.api.GlUniform
import gl3d.api.GlVertexArray

internal class WebGlProgramHandle(val js: WebGLProgram) : GlProgram
internal class WebGlBufferHandle(val js: WebGLBuffer?) : GlBuffer
internal class WebGlVaoHandle(val js: WebGLVertexArrayObject?) : GlVertexArray
internal class WebGlUniformHandle(val js: WebGLUniformLocation) : GlUniform
internal class WebGlTextureHandle(val js: WebGLTexture?) : GlTexture
internal class WebGlRenderbufferHandle(val js: WebGLRenderbuffer?) : GlRenderbuffer
internal class WebGlFramebufferHandle(val js: WebGLFramebuffer?) : GlFramebuffer

/** Implementace [Gl] nad WebGL2. Jediné místo v projektu, které sahá na GL. */
internal class WebGl2Backend(private val gl: WebGL2RenderingContext) : Gl {

    override fun viewport(x: Int, y: Int, width: Int, height: Int) =
        gl.viewport(x, y, width, height)

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) =
        gl.clearColor(r, g, b, a)

    override fun clear(color: Boolean, depth: Boolean, stencil: Boolean) {
        var mask = 0
        if (color) mask = mask or GlConst.COLOR_BUFFER_BIT
        if (depth) mask = mask or GlConst.DEPTH_BUFFER_BIT
        if (stencil) mask = mask or GlConst.STENCIL_BUFFER_BIT
        if (mask != 0) gl.clear(mask)
    }

    override fun enable(cap: GlCap) = gl.enable(cap.toGl())
    override fun disable(cap: GlCap) = gl.disable(cap.toGl())

    override fun depthFunc(func: GlCompare) = gl.depthFunc(func.toGl())
    override fun depthMask(writeEnabled: Boolean) = gl.depthMask(writeEnabled)
    override fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) =
        gl.colorMask(r, g, b, a)

    override fun blendFunc(src: GlBlend, dst: GlBlend) =
        gl.blendFunc(src.toGl(), dst.toGl())

    override fun blendFuncSeparate(
        srcRgb: GlBlend,
        dstRgb: GlBlend,
        srcAlpha: GlBlend,
        dstAlpha: GlBlend,
    ) = gl.blendFuncSeparate(srcRgb.toGl(), dstRgb.toGl(), srcAlpha.toGl(), dstAlpha.toGl())

    override fun stencilFunc(func: GlCompare, ref: Int, mask: Int) =
        gl.stencilFunc(func.toGl(), ref, mask)

    override fun stencilOp(sfail: GlStencilOp, dpfail: GlStencilOp, dppass: GlStencilOp) =
        gl.stencilOp(sfail.toGl(), dpfail.toGl(), dppass.toGl())

    override fun stencilMask(mask: Int) = gl.stencilMask(mask)

    override fun createProgram(
        name: String,
        vertexSource: String,
        fragmentSource: String,
    ): GlProgram? =
        compileGlProgram(gl, name, vertexSource, fragmentSource)?.let(::WebGlProgramHandle)

    override fun useProgram(program: GlProgram?) =
        gl.useProgram((program as WebGlProgramHandle?)?.js)

    override fun deleteProgram(program: GlProgram) =
        gl.deleteProgram((program as WebGlProgramHandle).js)

    override fun uniformLocation(program: GlProgram, name: String): GlUniform? =
        gl.getUniformLocation((program as WebGlProgramHandle).js, name)
            ?.let(::WebGlUniformHandle)

    override fun uniform1f(location: GlUniform?, v: Float) =
        gl.uniform1f(location.js(), v)

    override fun uniform2f(location: GlUniform?, x: Float, y: Float) =
        gl.uniform2f(location.js(), x, y)

    override fun uniform3f(location: GlUniform?, x: Float, y: Float, z: Float) =
        gl.uniform3f(location.js(), x, y, z)

    override fun uniform4f(location: GlUniform?, x: Float, y: Float, z: Float, w: Float) =
        gl.uniform4f(location.js(), x, y, z, w)

    override fun uniform1i(location: GlUniform?, v: Int) =
        gl.uniform1i(location.js(), v)

    override fun uniformMatrix4fv(location: GlUniform?, values: FloatArray) =
        gl.uniformMatrix4fv(location.js(), false, values.toFloat32Array())

    override fun uniformMatrix3fv(location: GlUniform?, values: FloatArray) =
        gl.uniformMatrix3fv(location.js(), false, values.toFloat32Array())

    override fun createBuffer(): GlBuffer = WebGlBufferHandle(gl.createBuffer())

    override fun deleteBuffer(buffer: GlBuffer) =
        gl.deleteBuffer((buffer as WebGlBufferHandle).js)

    override fun bindArrayBuffer(buffer: GlBuffer?) =
        gl.bindBuffer(GlConst.ARRAY_BUFFER, (buffer as WebGlBufferHandle?)?.js)

    override fun arrayBufferData(data: FloatArray, floatCount: Int, dynamic: Boolean) =
        gl.bufferData(
            GlConst.ARRAY_BUFFER,
            data.toFloat32Array(floatCount),
            if (dynamic) GlConst.DYNAMIC_DRAW else GlConst.STATIC_DRAW,
        )

    override fun bindElementArrayBuffer(buffer: GlBuffer?) =
        gl.bindBuffer(GlConst.ELEMENT_ARRAY_BUFFER, (buffer as WebGlBufferHandle?)?.js)

    override fun elementArrayBufferData(data: IntArray, count: Int) =
        glBufferDataUint32(
            gl,
            GlConst.ELEMENT_ARRAY_BUFFER,
            data.toUint32Array(count),
            GlConst.STATIC_DRAW,
        )

    override fun createVertexArray(): GlVertexArray = WebGlVaoHandle(gl.createVertexArray())

    override fun deleteVertexArray(vao: GlVertexArray) =
        gl.deleteVertexArray((vao as WebGlVaoHandle).js)

    override fun bindVertexArray(vao: GlVertexArray?) =
        gl.bindVertexArray((vao as WebGlVaoHandle?)?.js)

    override fun vertexAttribPointer(
        index: Int,
        size: Int,
        strideFloats: Int,
        offsetFloats: Int,
    ) = gl.vertexAttribPointer(
        index,
        size,
        GlConst.FLOAT,
        false,
        strideFloats * 4,
        offsetFloats * 4,
    )

    override fun enableVertexAttribArray(index: Int) = gl.enableVertexAttribArray(index)

    override fun vertexAttribDivisor(index: Int, divisor: Int) =
        gl.vertexAttribDivisor(index, divisor)

    override fun drawArrays(mode: GlPrimitive, first: Int, count: Int) =
        gl.drawArrays(mode.toGl(), first, count)

    override fun drawArraysInstanced(
        mode: GlPrimitive,
        first: Int,
        count: Int,
        instanceCount: Int,
    ) = gl.drawArraysInstanced(mode.toGl(), first, count, instanceCount)

    override fun drawElements(mode: GlPrimitive, count: Int, offsetInts: Int) =
        gl.drawElements(mode.toGl(), count, GlConst.UNSIGNED_INT, offsetInts * 4)

    override fun cullFace(face: GlFace) = gl.cullFace(
        when (face) {
            GlFace.FRONT -> GlConst.FRONT
            GlFace.BACK -> GlConst.BACK
        }
    )

    override fun polygonOffset(factor: Float, units: Float) = gl.polygonOffset(factor, units)

    // --- cíle vykreslování ------------------------------------------------

    private var floatColorBuffer: Boolean? = null

    override fun supportsFloatColorBuffer(): Boolean =
        floatColorBuffer ?: runCatching { glEnableFloatColorBuffer(gl) }
            .getOrDefault(false)
            .also { floatColorBuffer = it }

    override fun createTexture(): GlTexture = WebGlTextureHandle(gl.createTexture())

    override fun deleteTexture(texture: GlTexture) =
        gl.deleteTexture((texture as WebGlTextureHandle).js)

    override fun allocateTexture(
        texture: GlTexture,
        format: GlColorFormat,
        width: Int,
        height: Int,
    ) {
        gl.bindTexture(GlConst.TEXTURE_2D, (texture as WebGlTextureHandle).js)
        when (format) {
            GlColorFormat.RGBA8 -> glAllocateTexture(
                gl, GlConst.RGBA8, width, height, GlConst.RGBA, GlConst.UNSIGNED_BYTE,
            )
            // HALF_FLOAT stačí – akumulace i revealage se vejdou do poloviční
            // přesnosti a je to jediný typ, který `EXT_color_buffer_float`
            // garantuje jako renderovatelný.
            GlColorFormat.RGBA16F -> glAllocateTexture(
                gl, GlConst.RGBA16F, width, height, GlConst.RGBA, GlConst.HALF_FLOAT,
            )
        }
        gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MIN_FILTER, GlConst.LINEAR)
        gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MAG_FILTER, GlConst.LINEAR)
        gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_S, GlConst.CLAMP_TO_EDGE)
        gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_T, GlConst.CLAMP_TO_EDGE)
        gl.bindTexture(GlConst.TEXTURE_2D, null)
    }

    override fun bindTexture(unit: Int, texture: GlTexture?) {
        gl.activeTexture(GlConst.TEXTURE0 + unit)
        gl.bindTexture(GlConst.TEXTURE_2D, (texture as WebGlTextureHandle?)?.js)
    }

    override fun createRenderbuffer(): GlRenderbuffer =
        WebGlRenderbufferHandle(gl.createRenderbuffer())

    override fun deleteRenderbuffer(renderbuffer: GlRenderbuffer) =
        gl.deleteRenderbuffer((renderbuffer as WebGlRenderbufferHandle).js)

    override fun allocateDepthStencil(renderbuffer: GlRenderbuffer, width: Int, height: Int) {
        gl.bindRenderbuffer(GlConst.RENDERBUFFER, (renderbuffer as WebGlRenderbufferHandle).js)
        gl.renderbufferStorage(GlConst.RENDERBUFFER, GlConst.DEPTH24_STENCIL8, width, height)
        gl.bindRenderbuffer(GlConst.RENDERBUFFER, null)
    }

    override fun createFramebuffer(): GlFramebuffer =
        WebGlFramebufferHandle(gl.createFramebuffer())

    override fun deleteFramebuffer(framebuffer: GlFramebuffer) =
        gl.deleteFramebuffer((framebuffer as WebGlFramebufferHandle).js)

    override fun bindFramebuffer(framebuffer: GlFramebuffer?) =
        gl.bindFramebuffer(GlConst.FRAMEBUFFER, (framebuffer as WebGlFramebufferHandle?)?.js)

    override fun attachColorTexture(framebuffer: GlFramebuffer, index: Int, texture: GlTexture) {
        gl.bindFramebuffer(GlConst.FRAMEBUFFER, (framebuffer as WebGlFramebufferHandle).js)
        gl.framebufferTexture2D(
            GlConst.FRAMEBUFFER,
            GlConst.COLOR_ATTACHMENT0 + index,
            GlConst.TEXTURE_2D,
            (texture as WebGlTextureHandle).js,
            0,
        )
    }

    override fun attachDepthStencil(framebuffer: GlFramebuffer, renderbuffer: GlRenderbuffer) {
        gl.bindFramebuffer(GlConst.FRAMEBUFFER, (framebuffer as WebGlFramebufferHandle).js)
        gl.framebufferRenderbuffer(
            GlConst.FRAMEBUFFER,
            GlConst.DEPTH_STENCIL_ATTACHMENT,
            GlConst.RENDERBUFFER,
            (renderbuffer as WebGlRenderbufferHandle).js,
        )
    }

    override fun drawBuffers(framebuffer: GlFramebuffer, count: Int) {
        gl.bindFramebuffer(GlConst.FRAMEBUFFER, (framebuffer as WebGlFramebufferHandle).js)
        glDrawBuffersCount(gl, count)
    }

    override fun isFramebufferComplete(framebuffer: GlFramebuffer): Boolean {
        gl.bindFramebuffer(GlConst.FRAMEBUFFER, (framebuffer as WebGlFramebufferHandle).js)
        return gl.checkFramebufferStatus(GlConst.FRAMEBUFFER) == GlConst.FRAMEBUFFER_COMPLETE
    }

    override fun clearColorBuffer(index: Int, r: Float, g: Float, b: Float, a: Float) =
        glClearColorBufferAt(gl, index, r, g, b, a)

    override fun blitColorToScreen(source: GlFramebuffer, width: Int, height: Int) {
        gl.bindFramebuffer(GlConst.READ_FRAMEBUFFER, (source as WebGlFramebufferHandle).js)
        gl.bindFramebuffer(GlConst.DRAW_FRAMEBUFFER, null)
        gl.blitFramebuffer(
            0, 0, width, height,
            0, 0, width, height,
            GlConst.COLOR_BUFFER_BIT,
            GlConst.NEAREST,
        )
        gl.bindFramebuffer(GlConst.FRAMEBUFFER, null)
    }

    /**
     * Kotlin/Wasm nesdílí paměť s JS typed arrays, takže se pixely musí
     * přenést po jednom. Je to jednorázová operace při exportu snímku –
     * u plátna v řádu megapixelů to trvá zlomky sekundy.
     */
    override fun readPixels(width: Int, height: Int): ByteArray {
        if (width <= 0 || height <= 0) return ByteArray(0)
        val pixels = glReadPixelsRgba(gl, width, height)
        val out = ByteArray(width * height * 4)
        for (i in out.indices) out[i] = uint8Get(pixels, i).toByte()
        return out
    }

    override fun rendererInfo(): String =
        runCatching { glRendererInfo(gl) }.getOrDefault("neznámé GPU")

    private fun GlUniform?.js(): WebGLUniformLocation? = (this as WebGlUniformHandle?)?.js
}

private fun GlCap.toGl(): Int = when (this) {
    GlCap.DEPTH_TEST -> GlConst.DEPTH_TEST
    GlCap.BLEND -> GlConst.BLEND
    GlCap.STENCIL_TEST -> GlConst.STENCIL_TEST
    GlCap.CULL_FACE -> GlConst.CULL_FACE
    GlCap.SCISSOR_TEST -> GlConst.SCISSOR_TEST
    GlCap.POLYGON_OFFSET_FILL -> GlConst.POLYGON_OFFSET_FILL
}

private fun GlCompare.toGl(): Int = when (this) {
    GlCompare.NEVER -> GlConst.NEVER
    GlCompare.LESS -> GlConst.LESS
    GlCompare.EQUAL -> GlConst.EQUAL
    GlCompare.LEQUAL -> GlConst.LEQUAL
    GlCompare.GREATER -> GlConst.GREATER
    GlCompare.NOTEQUAL -> GlConst.NOTEQUAL
    GlCompare.GEQUAL -> GlConst.GEQUAL
    GlCompare.ALWAYS -> GlConst.ALWAYS
}

private fun GlBlend.toGl(): Int = when (this) {
    GlBlend.ZERO -> GlConst.ZERO
    GlBlend.ONE -> GlConst.ONE
    GlBlend.SRC_ALPHA -> GlConst.SRC_ALPHA
    GlBlend.ONE_MINUS_SRC_ALPHA -> GlConst.ONE_MINUS_SRC_ALPHA
    GlBlend.SRC_COLOR -> GlConst.SRC_COLOR
    GlBlend.ONE_MINUS_SRC_COLOR -> GlConst.ONE_MINUS_SRC_COLOR
    GlBlend.DST_ALPHA -> GlConst.DST_ALPHA
    GlBlend.ONE_MINUS_DST_ALPHA -> GlConst.ONE_MINUS_DST_ALPHA
}

private fun GlStencilOp.toGl(): Int = when (this) {
    GlStencilOp.KEEP -> GlConst.KEEP
    GlStencilOp.ZERO -> GlConst.ZERO
    GlStencilOp.REPLACE -> GlConst.REPLACE
    GlStencilOp.INCR -> GlConst.INCR
    GlStencilOp.DECR -> GlConst.DECR
    GlStencilOp.INVERT -> GlConst.INVERT
}

private fun GlPrimitive.toGl(): Int = when (this) {
    GlPrimitive.POINTS -> GlConst.POINTS
    GlPrimitive.LINES -> GlConst.LINES
    GlPrimitive.LINE_STRIP -> GlConst.LINE_STRIP
    GlPrimitive.TRIANGLES -> GlConst.TRIANGLES
    GlPrimitive.TRIANGLE_STRIP -> GlConst.TRIANGLE_STRIP
    GlPrimitive.TRIANGLE_FAN -> GlConst.TRIANGLE_FAN
}
