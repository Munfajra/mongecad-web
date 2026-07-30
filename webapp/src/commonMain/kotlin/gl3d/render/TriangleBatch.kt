package gl3d.render

import gl3d.api.Gl
import gl3d.api.GlBuffer
import gl3d.api.GlPrimitive
import gl3d.api.GlProgram
import gl3d.api.GlUniform
import gl3d.api.GlVertexArray
import gl3d.math.Mat4
import gl3d.math.Vec3

/**
 * Shader pro plošnou grafiku.
 *
 * Desktopový `planes.vert/frag` počítá stínování a průhlednost ve fragment
 * shaderu z uniformů, protože každá rovina jde vlastním draw callem. Tady je
 * barva včetně alfy per-vertex, takže se všechny roviny i s prstenci, kterými
 * jim desktop změkčuje okraje, vejdou do jednoho draw callu. Stínování podle
 * odklonu od pohledu je konstantní na rovinu, takže se spočítá na CPU.
 */
private object PlaneShaders {

    const val VERTEX = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aColor;

uniform mat4 uViewProj;

out vec4 vColor;

void main() {
    vColor = aColor;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
"""

    /** Přímé míchání – použije se, když není k dispozici OIT. */
    const val FRAGMENT_DIRECT = """#version 300 es
precision highp float;

in vec4 vColor;
layout(location = 0) out vec4 FragColor;

void main() {
    FragColor = vColor;
}
"""

    /**
     * Zápis do akumulačních cílů OIT. Váha je konstantní 1, stejně jako
     * v desktopovém `planes.frag` – scéna je ortografická a mělká, takže
     * hloubková váha z původního článku by nic nepřinesla.
     *
     * Revealage jde do logaritmu, aby se dal akumulovat sčítáním; podrobnosti
     * v dokumentaci [gl3d.render.OitPipeline].
     */
    const val FRAGMENT_OIT = """#version 300 es
precision highp float;

in vec4 vColor;
layout(location = 0) out vec4 Accum;
layout(location = 1) out vec4 Reveal;

void main() {
    float a = clamp(vColor.a, 0.0, 1.0);
    Accum = vec4(vColor.rgb * a, a);
    Reveal = vec4(log(max(1.0 - a, 1e-4)), 0.0, 0.0, 0.0);
}
"""
}

/** Sběrač trojúhelníků s barvou po vrcholu. */
class TriangleBatch {

    private val builder = FloatBuilder(1024)

    val isEmpty: Boolean get() = builder.size == 0

    fun clear() = builder.reset()

    fun addTriangle(
        a: Vec3, b: Vec3, c: Vec3,
        red: Float, green: Float, blue: Float, alpha: Float,
    ) {
        push(a, red, green, blue, alpha)
        push(b, red, green, blue, alpha)
        push(c, red, green, blue, alpha)
    }

    /** Čtyřúhelník zadaný v pořadí po obvodu. */
    fun addQuad(
        a: Vec3, b: Vec3, c: Vec3, d: Vec3,
        red: Float, green: Float, blue: Float, alpha: Float,
    ) {
        addTriangle(a, b, c, red, green, blue, alpha)
        addTriangle(a, c, d, red, green, blue, alpha)
    }

    private fun push(p: Vec3, r: Float, g: Float, b: Float, a: Float) {
        builder.push(p.x, p.y, p.z)
        builder.push(r, g)
        builder.push(b, a)
    }

    internal fun data(): FloatArray = builder.data
    internal fun floatCount(): Int = builder.size
    internal fun vertexCount(): Int = builder.size / FLOATS_PER_VERTEX

    internal companion object {
        /** pozice(3) + barva(4) */
        const val FLOATS_PER_VERTEX = 7
    }
}

/** Vykreslení [TriangleBatch]; jeden draw call na celý batch. */
class TriangleRenderer(private val gl: Gl) {

    private var program: GlProgram? = null
    private var oitProgram: GlProgram? = null
    private var buffer: GlBuffer? = null
    private var vao: GlVertexArray? = null
    private var uViewProj: GlUniform? = null
    private var uViewProjOit: GlUniform? = null

    val isReady: Boolean get() = program != null

    fun initialize(): Boolean {
        if (program != null) return true

        val prog = gl.createProgram("plane", PlaneShaders.VERTEX, PlaneShaders.FRAGMENT_DIRECT)
        if (prog == null) {
            gl3d.api.gl3dLog("TriangleRenderer: program se nepodařilo přeložit")
            return false
        }
        program = prog
        uViewProj = gl.uniformLocation(prog, "uViewProj")

        // Volitelný; bez něj se jen nepoužije OIT.
        gl.createProgram("planeOit", PlaneShaders.VERTEX, PlaneShaders.FRAGMENT_OIT)?.let {
            oitProgram = it
            uViewProjOit = gl.uniformLocation(it, "uViewProj")
        }

        val vertexBuffer = gl.createBuffer()
        buffer = vertexBuffer
        val array = gl.createVertexArray()
        vao = array

        gl.bindVertexArray(array)
        gl.bindArrayBuffer(vertexBuffer)
        val stride = TriangleBatch.FLOATS_PER_VERTEX
        gl.vertexAttribPointer(index = 0, size = 3, strideFloats = stride, offsetFloats = 0)
        gl.enableVertexAttribArray(0)
        gl.vertexAttribDivisor(0, 0)
        gl.vertexAttribPointer(index = 1, size = 4, strideFloats = stride, offsetFloats = 3)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribDivisor(1, 0)
        gl.bindVertexArray(null)
        gl.bindArrayBuffer(null)
        return true
    }

    /** Umí renderer zapisovat do akumulačních cílů OIT? */
    val supportsOit: Boolean get() = oitProgram != null

    /** @param oit zapisovat do akumulačních cílů OIT místo přímého míchání. */
    fun draw(batch: TriangleBatch, viewProjection: Mat4, oit: Boolean = false) {
        val useOit = oit && oitProgram != null
        val prog = (if (useOit) oitProgram else program) ?: return
        val vertexBuffer = buffer ?: return
        if (batch.isEmpty) return

        gl.useProgram(prog)
        gl.bindVertexArray(vao)
        gl.bindArrayBuffer(vertexBuffer)
        gl.arrayBufferData(batch.data(), batch.floatCount(), dynamic = true)
        gl.uniformMatrix4fv(if (useOit) uViewProjOit else uViewProj, viewProjection.data)
        gl.drawArrays(GlPrimitive.TRIANGLES, 0, batch.vertexCount())
        gl.bindVertexArray(null)
        gl.bindArrayBuffer(null)
        gl.useProgram(null)
    }

    fun dispose() {
        vao?.let(gl::deleteVertexArray)
        buffer?.let(gl::deleteBuffer)
        program?.let(gl::deleteProgram)
        oitProgram?.let(gl::deleteProgram)
        vao = null
        buffer = null
        program = null
        oitProgram = null
    }
}
