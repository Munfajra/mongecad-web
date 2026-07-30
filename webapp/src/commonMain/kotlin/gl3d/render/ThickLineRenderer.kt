package gl3d.render

import gl3d.api.Gl
import gl3d.api.GlBuffer
import gl3d.api.GlPrimitive
import gl3d.api.GlProgram
import gl3d.api.GlUniform
import gl3d.api.GlVertexArray
import gl3d.api.gl3dLog
import gl3d.math.Mat4

/**
 * Kreslení všech čar scény – přímek, úseček, stop rovin, os, křížků bodů
 * i polylin kuželoseček.
 *
 * Na desktopu má každý typ objektu vlastní kreslicí funkci s `glLineWidth`.
 * Ve WebGL šířka čáry neexistuje, takže tudy prochází úplně všechno a
 * roztažení na obdélník řeší jeden vertex shader. Vedlejší efekt je, že se
 * celá čárová grafika kreslí v řádu jednotek draw callů místo stovek.
 */
class ThickLineRenderer(private val gl: Gl) {

    private var program: GlProgram? = null
    private var quadBuffer: GlBuffer? = null
    private var instanceBuffer: GlBuffer? = null
    private var vao: GlVertexArray? = null

    private var uViewProj: GlUniform? = null
    private var uViewport: GlUniform? = null
    private var uWidth: GlUniform? = null
    private var uDepthBias: GlUniform? = null
    private var uColor: GlUniform? = null
    private var uAlpha: GlUniform? = null
    private var uPattern: GlUniform? = null

    val isReady: Boolean get() = program != null

    fun initialize(): Boolean {
        if (program != null) return true

        val prog = gl.createProgram("line", LineShaders.VERTEX, LineShaders.FRAGMENT)
        if (prog == null) {
            gl3dLog("ThickLineRenderer: program se nepodařilo přeložit")
            return false
        }
        program = prog

        uViewProj = gl.uniformLocation(prog, "uViewProj")
        uViewport = gl.uniformLocation(prog, "uViewport")
        uWidth = gl.uniformLocation(prog, "uWidth")
        uDepthBias = gl.uniformLocation(prog, "uDepthBias")
        uColor = gl.uniformLocation(prog, "uColor")
        uAlpha = gl.uniformLocation(prog, "uAlpha")
        uPattern = gl.uniformLocation(prog, "uPattern")

        val quad = gl.createBuffer()
        quadBuffer = quad
        val instances = gl.createBuffer()
        instanceBuffer = instances

        val array = gl.createVertexArray()
        vao = array

        gl.bindVertexArray(array)

        // Statický obdélník: (podíl podél úseku, strana). Dva trojúhelníky.
        gl.bindArrayBuffer(quad)
        gl.arrayBufferData(
            floatArrayOf(
                0f, -1f,
                1f, -1f,
                1f, 1f,
                0f, -1f,
                1f, 1f,
                0f, 1f,
            )
        )
        gl.vertexAttribPointer(index = 0, size = 2, strideFloats = 2, offsetFloats = 0)
        gl.enableVertexAttribArray(0)
        gl.vertexAttribDivisor(0, 0)

        // Po instanci: posA, posB, (distA, distB)
        gl.bindArrayBuffer(instances)
        val stride = LineBatch.FLOATS_PER_SEGMENT
        gl.vertexAttribPointer(index = 1, size = 3, strideFloats = stride, offsetFloats = 0)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribDivisor(1, 1)
        gl.vertexAttribPointer(index = 2, size = 3, strideFloats = stride, offsetFloats = 3)
        gl.enableVertexAttribArray(2)
        gl.vertexAttribDivisor(2, 1)
        gl.vertexAttribPointer(index = 3, size = 2, strideFloats = stride, offsetFloats = 6)
        gl.enableVertexAttribArray(3)
        gl.vertexAttribDivisor(3, 1)

        gl.bindVertexArray(null)
        gl.bindArrayBuffer(null)
        gl3dLog("ThickLineRenderer: připraven")
        return true
    }

    /**
     * @param pixelScale poměr device pixelů k logickým (devicePixelRatio).
     *   Šířky čar jsou v aplikaci zadané v logických pixelech, výřez je
     *   v device pixelech – bez přenásobení by čáry na retina displeji
     *   vycházely o polovinu tenčí než na desktopu.
     * @param alphaOverride průhlednost společná pro celý batch; slouží
     *   k překreslení zakryté části čar (`HIDDEN_LINE_ALPHA` na desktopu).
     * @param patternOverride vzor společný pro celý batch – zakrytá část se
     *   kreslí čárkovaně bez ohledu na styl objektu.
     */
    fun draw(
        batch: LineBatch,
        viewProjection: Mat4,
        viewportWidth: Int,
        viewportHeight: Int,
        pixelScale: Float,
        alphaOverride: Float? = null,
        patternOverride: Float? = null,
    ) {
        val prog = program ?: return
        val instances = instanceBuffer ?: return
        if (batch.isEmpty()) return

        gl.useProgram(prog)
        gl.bindVertexArray(vao)
        gl.uniformMatrix4fv(uViewProj, viewProjection.data)
        gl.uniform2f(uViewport, viewportWidth.toFloat(), viewportHeight.toFloat())

        batch.forEachGroup { style, data, floatCount, instanceCount ->
            gl.bindArrayBuffer(instances)
            gl.arrayBufferData(data, floatCount, dynamic = true)

            val alpha = alphaOverride ?: style.alpha
            val pattern = patternOverride ?: style.pattern

            gl.uniform3f(uColor, style.red, style.green, style.blue)
            gl.uniform1f(uAlpha, alpha)
            gl.uniform1f(uPattern, pattern)
            gl.uniform1f(uDepthBias, style.depthBias)
            gl.uniform1f(
                uWidth,
                (lineWidthForPattern(style.width, pattern) * pixelScale).coerceAtLeast(1f),
            )

            gl.drawArraysInstanced(GlPrimitive.TRIANGLES, 0, 6, instanceCount)
        }

        gl.bindVertexArray(null)
        gl.bindArrayBuffer(null)
        gl.useProgram(null)
    }

    fun dispose() {
        vao?.let(gl::deleteVertexArray)
        quadBuffer?.let(gl::deleteBuffer)
        instanceBuffer?.let(gl::deleteBuffer)
        program?.let(gl::deleteProgram)
        vao = null
        quadBuffer = null
        instanceBuffer = null
        program = null
    }
}
