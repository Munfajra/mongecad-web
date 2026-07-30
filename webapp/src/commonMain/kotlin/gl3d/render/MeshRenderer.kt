package gl3d.render

import androidx.compose.ui.graphics.Color
import gl3d.api.Gl
import gl3d.api.GlBuffer
import gl3d.api.GlPrimitive
import gl3d.api.GlProgram
import gl3d.api.GlUniform
import gl3d.api.GlVertexArray
import gl3d.api.gl3dLog
import gl3d.math.Mat4
import gl3d.math.Vec3

/**
 * Síť trojúhelníků se stínovanými normálami – port `shaders/sor.vert|frag`.
 *
 * Kvadriky (koule, válec, kužel) se kreslí analyticky a do GPU neposílají nic;
 * rotační plochy, přímkové plochy a tělesa z úseček jsou naopak **skutečné
 * sítě**. Je to jediné místo celého portu, kde do GPU putuje netriviální
 * geometrie, a proto se mesh drží v [MeshRenderer] nacachovaný a přenáší se
 * jen při změně (viz `WEBGL_3D_PLAN.md`, bod 4/#10 a #11) – desktop si
 * `glGenVertexArrays` v každém draw callu dovolit může, prohlížeč ne.
 */
class Mesh3D(
    /** xyz xyz … */
    val positions: FloatArray,
    /** xyz xyz …, stejný počet jako [positions] */
    val normals: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3

    /** Prokládaná data pro jeden VBO: poz(3) + normála(3) na vrchol. */
    internal fun interleaved(): FloatArray {
        val out = FloatArray(vertexCount * FLOATS_PER_VERTEX)
        for (v in 0 until vertexCount) {
            val src = v * 3
            val dst = v * FLOATS_PER_VERTEX
            out[dst] = positions[src]
            out[dst + 1] = positions[src + 1]
            out[dst + 2] = positions[src + 2]
            out[dst + 3] = normals[src]
            out[dst + 4] = normals[src + 1]
            out[dst + 5] = normals[src + 2]
        }
        return out
    }

    internal companion object {
        const val FLOATS_PER_VERTEX = 6
    }
}

/**
 * Vykreslení sítí s per-objektovou GPU cache.
 *
 * Klíčem je id objektu, podpisem libovolná hodnota odvozená z jeho geometrie;
 * dokud se podpis nezmění, mesh se nepřestavuje ani nepřenáší. Sítě, jejichž
 * objekt ze scény zmizel, uklidí [retain].
 */
class MeshRenderer(private val gl: Gl) {

    private class GpuMesh(
        val vao: GlVertexArray,
        val vbo: GlBuffer,
        val ebo: GlBuffer,
        val indexCount: Int,
        val signature: Long,
    )

    private var program: GlProgram? = null
    private var uMvp: GlUniform? = null
    private var uColor: GlUniform? = null
    private var uAlpha: GlUniform? = null
    private var uTint: GlUniform? = null
    private var uAmbient: GlUniform? = null
    private var uViewDir: GlUniform? = null

    private val meshes = LinkedHashMap<String, GpuMesh>()

    /** Objekty, kterým se v tomto snímku sáhlo na mesh – podklad pro [retain]. */
    private val touched = mutableSetOf<String>()

    val isReady: Boolean get() = program != null

    fun initialize(): Boolean {
        if (program != null) return true
        val prog = gl.createProgram("mesh", VERTEX, FRAGMENT)
        if (prog == null) {
            gl3dLog("MeshRenderer: program se nepodařilo přeložit")
            return false
        }
        program = prog
        uMvp = gl.uniformLocation(prog, "uMVP")
        uColor = gl.uniformLocation(prog, "uColor")
        uAlpha = gl.uniformLocation(prog, "uAlpha")
        uTint = gl.uniformLocation(prog, "uTint")
        uAmbient = gl.uniformLocation(prog, "uAmbient")
        uViewDir = gl.uniformLocation(prog, "uViewDir")
        return true
    }

    /**
     * @param viewProjection matice snímku.
     * @param invViewProjection inverze téže matice; z ní se odvodí směr
     *   povrch → pozorovatel stejně jako `viewDirFromVp` na desktopu. Přes
     *   inverzi proto, že VP obsahuje i případné zrcadlení os z 2D plátna –
     *   směr pohledu vzatý rovnou z kamery by u zrcadlené scény osvětloval
     *   opačnou stranu ploch.
     */
    fun begin(viewProjection: Mat4, invViewProjection: Mat4?) {
        val prog = program ?: return
        touched.clear()
        gl.useProgram(prog)
        gl.uniformMatrix4fv(uMvp, viewProjection.data)
        val viewDir = invViewProjection?.let(::viewDirFromInverseVp) ?: Vec3(0f, 0f, 1f)
        gl.uniform3f(uViewDir, viewDir.x, viewDir.y, viewDir.z)
    }

    /**
     * Nakreslí síť objektu `key`. Když se [signature] liší od nacachované,
     * zavolá se [build] a výsledek se nahraje do GPU; jinak se jen kreslí.
     * `build` vrací `null`, pokud se síť z aktuálního stavu postavit nedá.
     */
    fun draw(
        key: String,
        signature: Long,
        color: Color,
        alpha: Float,
        tint: Float,
        ambient: Float,
        build: () -> Mesh3D?,
    ) {
        if (program == null) return
        touched += key
        val mesh = ensureMesh(key, signature, build) ?: return

        gl.uniform3f(uColor, color.red, color.green, color.blue)
        gl.uniform1f(uAlpha, alpha.coerceIn(0f, 1f))
        gl.uniform1f(uTint, tint)
        gl.uniform1f(uAmbient, ambient)

        gl.bindVertexArray(mesh.vao)
        gl.drawElements(GlPrimitive.TRIANGLES, mesh.indexCount)
        gl.bindVertexArray(null)
    }

    fun end() {
        gl.useProgram(null)
    }

    /** Uvolní sítě objektů, na které se od posledního [begin] nesáhlo. */
    fun retain() {
        if (meshes.keys.size == touched.size && touched.containsAll(meshes.keys)) return
        val stale = meshes.keys.filterNot { it in touched }
        stale.forEach { key -> meshes.remove(key)?.let(::release) }
    }

    /** Zahodí všechny nacachované sítě; program zůstává přeložený. */
    fun clearCache() {
        if (meshes.isEmpty()) return
        meshes.values.forEach(::release)
        meshes.clear()
        touched.clear()
    }

    fun dispose() {
        meshes.values.forEach(::release)
        meshes.clear()
        touched.clear()
        program?.let(gl::deleteProgram)
        program = null
    }

    private fun ensureMesh(key: String, signature: Long, build: () -> Mesh3D?): GpuMesh? {
        meshes[key]?.let { if (it.signature == signature) return it }

        val cpu = build()
        if (cpu == null || cpu.indices.isEmpty()) {
            meshes.remove(key)?.let(::release)
            return null
        }

        meshes.remove(key)?.let(::release)

        val vao = gl.createVertexArray()
        val vbo = gl.createBuffer()
        val ebo = gl.createBuffer()

        gl.bindVertexArray(vao)
        gl.bindArrayBuffer(vbo)
        val interleaved = cpu.interleaved()
        gl.arrayBufferData(interleaved, interleaved.size)
        val stride = Mesh3D.FLOATS_PER_VERTEX
        gl.vertexAttribPointer(index = 0, size = 3, strideFloats = stride, offsetFloats = 0)
        gl.enableVertexAttribArray(0)
        gl.vertexAttribDivisor(0, 0)
        gl.vertexAttribPointer(index = 1, size = 3, strideFloats = stride, offsetFloats = 3)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribDivisor(1, 0)
        // Element buffer je součástí stavu VAO, takže se navazuje uvnitř něj
        // a při kreslení stačí navázat samotné VAO.
        gl.bindElementArrayBuffer(ebo)
        gl.elementArrayBufferData(cpu.indices)
        gl.bindVertexArray(null)
        gl.bindArrayBuffer(null)
        gl.bindElementArrayBuffer(null)

        val mesh = GpuMesh(vao, vbo, ebo, cpu.indices.size, signature)
        meshes[key] = mesh
        return mesh
    }

    private fun release(mesh: GpuMesh) {
        gl.deleteVertexArray(mesh.vao)
        gl.deleteBuffer(mesh.vbo)
        gl.deleteBuffer(mesh.ebo)
    }
}

/**
 * Směr povrch → pozorovatel ve světových souřadnicích – port `viewDirFromVp`
 * z `opengl/model/SoR.kt`, jen s už hotovou inverzí.
 */
private fun viewDirFromInverseVp(inverse: Mat4): Vec3 {
    fun unproject(z: Float): Vec3 {
        val v = inverse.transform(0f, 0f, z, 1f)
        val w = if (v[3] != 0f) v[3] else 1f
        return Vec3(v[0] / w, v[1] / w, v[2] / w)
    }

    val direction = unproject(-1f) - unproject(1f)
    return if (direction.length() > 1e-6f) direction.normalized() else Vec3(0f, 0f, 1f)
}

private const val VERTEX = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNor;

uniform mat4 uMVP;

out vec3 vNor;

void main() {
    vNor = aNor;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

/**
 * Port `shaders/sor.frag`. Desktop píše do dvou cílů OIT; sítě ale jdou
 * stejně jako kvadriky do neprůhledného průchodu (`QUADRIC_FRONT_ALPHA` je 1),
 * takže tady zůstává jediný výstup a míchá se přímo. Stínování je znak po
 * znaku stejné, aby plochy vypadaly jako na desktopu.
 */
private const val FRAGMENT = """#version 300 es
precision highp float;

in vec3 vNor;

uniform vec3  uColor;
uniform float uAlpha;
uniform float uTint;     // 1.0 vpředu, <1.0 za jinou plochou
uniform float uAmbient;
uniform vec3  uViewDir;  // směr povrch -> pozorovatel (world; ortho => konstantní)

layout(location = 0) out vec4 FragColor;

void main() {
    vec3 N = normalize(vNor);
    vec3 V = (dot(uViewDir, uViewDir) < 1e-8) ? vec3(0.0, 0.0, 1.0) : normalize(uViewDir);
    // oboustranné plochy: sviť vždy stranu přivrácenou k pozorovateli
    if (dot(N, V) < 0.0) N = -N;

    vec3 L = normalize(vec3(0.55, 0.70, 0.45));
    vec3 H = normalize(L + V);

    float ndl = max(dot(N, L), 0.0);
    float ndv = max(dot(N, V), 0.0);
    float ndh = max(dot(N, H), 0.0);

    float fill  = 0.15 * max(dot(N, normalize(vec3(-0.45, -0.30, 0.60))), 0.0);
    float shade = min(uAmbient + (1.0 - uAmbient) * ndl + fill, 1.0);

    float specTight = pow(ndh, 96.0);
    float spec      = 0.9 * specTight + 0.25 * pow(ndh, 12.0);
    float fresnel   = pow(1.0 - ndv, 3.0);

    vec3 rgb = (uColor * shade + (spec + 0.12 * fresnel) * vec3(1.0)) * uTint;
    float a  = clamp(uAlpha + 0.5 * specTight + 0.08 * fresnel, 0.0, 1.0);

    FragColor = vec4(rgb, a);
}
"""
