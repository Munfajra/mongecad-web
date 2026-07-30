package gl3d.render

import androidx.compose.ui.graphics.Color
import gl3d.api.Gl
import gl3d.api.GlPrimitive
import gl3d.api.GlProgram
import gl3d.api.GlUniform
import gl3d.api.gl3dLog
import gl3d.math.Mat4
import gl3d.math.Vec3

/**
 * Koule kreslené **analyticky**, ne sítí trojúhelníků.
 *
 * Port `shaders/sphere.vert|frag` z desktopu. Kreslí se jediný celoobrazovkový
 * trojúhelník a fragment shader pro každý pixel spočítá průsečík paprsku
 * s koulí; kdo netrefí, zahodí se. Do GPU tím pádem neputuje žádná geometrie
 * a silueta je dokonale hladká v každém přiblížení – proto se to na desktopu
 * dělá takhle a proto to sem šlo přenést skoro doslova.
 *
 * Hloubka se zapisuje ručně přes `gl_FragDepth`, jinak by koule ležela
 * v hloubce celoobrazovkového trojúhelníku a nic by ji nezakrylo.
 */
class SphereRenderer(private val gl: Gl) {

    private var program: GlProgram? = null
    private var uColor: GlUniform? = null
    private var uViewport: GlUniform? = null
    private var uInvViewProj: GlUniform? = null
    private var uViewProj: GlUniform? = null
    private var uAlpha: GlUniform? = null
    private var uTint: GlUniform? = null
    private var uDepthBias: GlUniform? = null
    private var uCenter: GlUniform? = null
    private var uRadius: GlUniform? = null

    val isReady: Boolean get() = program != null

    fun initialize(): Boolean {
        if (program != null) return true
        val prog = gl.createProgram("sphere", VERTEX, FRAGMENT)
        if (prog == null) {
            gl3dLog("SphereRenderer: program se nepodařilo přeložit")
            return false
        }
        program = prog
        uColor = gl.uniformLocation(prog, "uColor")
        uViewport = gl.uniformLocation(prog, "uViewport")
        uInvViewProj = gl.uniformLocation(prog, "uInvViewProj")
        uViewProj = gl.uniformLocation(prog, "uViewProj")
        uAlpha = gl.uniformLocation(prog, "uAlpha")
        uTint = gl.uniformLocation(prog, "uTint")
        uDepthBias = gl.uniformLocation(prog, "uDepthBias")
        uCenter = gl.uniformLocation(prog, "uCenter")
        uRadius = gl.uniformLocation(prog, "uRadius")
        return true
    }

    fun begin(viewProjection: Mat4, invViewProjection: Mat4, width: Int, height: Int) {
        val prog = program ?: return
        gl.useProgram(prog)
        gl.uniform2f(uViewport, width.toFloat(), height.toFloat())
        gl.uniformMatrix4fv(uViewProj, viewProjection.data)
        gl.uniformMatrix4fv(uInvViewProj, invViewProjection.data)
    }

    fun draw(
        center: Vec3,
        radius: Float,
        color: Color,
        alpha: Float = 1f,
        tint: Float = 1f,
        depthBias: Float = 0f,
    ) {
        if (program == null || radius <= 0f) return
        gl.uniform3f(uColor, color.red, color.green, color.blue)
        gl.uniform1f(uAlpha, alpha)
        gl.uniform1f(uTint, tint)
        gl.uniform1f(uDepthBias, depthBias)
        gl.uniform3f(uCenter, center.x, center.y, center.z)
        gl.uniform1f(uRadius, radius)
        gl.drawArrays(GlPrimitive.TRIANGLES, 0, 3)
    }

    fun end() {
        gl.useProgram(null)
    }

    fun dispose() {
        program?.let(gl::deleteProgram)
        program = null
    }
}

private const val VERTEX = """#version 300 es
precision highp float;

void main() {
    vec2 pos;
    if (gl_VertexID == 0)      pos = vec2(-1.0, -1.0);
    else if (gl_VertexID == 1) pos = vec2( 3.0, -1.0);
    else                       pos = vec2(-1.0,  3.0);
    gl_Position = vec4(pos, 0.0, 1.0);
}
"""

private const val FRAGMENT = """#version 300 es
precision highp float;

layout(location = 0) out vec4 FragColor;

uniform vec3  uColor;
uniform vec2  uViewport;
uniform mat4  uInvViewProj;
uniform mat4  uViewProj;
uniform float uAlpha;
uniform float uTint;
uniform float uDepthBias;

uniform vec3  uCenter;
uniform float uRadius;

void main() {
    // Paprsek z pixelu: dva body na přední a zadní ořezové rovině.
    vec2 ndc = (gl_FragCoord.xy / uViewport) * 2.0 - 1.0;
    vec4 pNear4 = uInvViewProj * vec4(ndc, -1.0, 1.0);
    vec4 pFar4  = uInvViewProj * vec4(ndc,  1.0, 1.0);
    vec3 ro = pNear4.xyz / pNear4.w;
    vec3 rf = pFar4.xyz  / pFar4.w;
    vec3 rd = normalize(rf - ro);
    ro += rd * 1e-5;

    vec3 roL = ro - uCenter;

    float Aq = dot(rd, rd);
    float Bq = 2.0 * dot(roL, rd);
    float Cq = dot(roL, roL) - uRadius * uRadius;

    float disc = Bq * Bq - 4.0 * Aq * Cq;
    if (disc < 0.0) discard;

    // Numericky stabilní kořeny – naivní vzorec u velkých poloměrů ztrácí
    // přesnost odečítáním dvou blízkých čísel.
    float sq = sqrt(max(0.0, disc));
    float q  = -0.5 * (Bq + (Bq < 0.0 ? -sq : sq));
    float t0 = q / Aq;
    float t1 = Cq / q;

    float t = 1e30;
    if (t0 > 1e-5) t = min(t, t0);
    if (t1 > 1e-5) t = min(t, t1);
    if (t == 1e30) discard;

    vec3 pW = ro + t * rd;

    vec4 clip = uViewProj * vec4(pW, 1.0);
    float depth = 0.5 * (clip.z / clip.w) + 0.5;
    gl_FragDepth = clamp(depth + uDepthBias, 0.0, 1.0);

    vec3 n = normalize(pW - uCenter);
    vec3 V = -rd;
    vec3 L = normalize(vec3(0.55, 0.70, 0.45));
    vec3 H = normalize(L + V);

    float ndl = max(dot(n, L), 0.0);
    float ndv = max(dot(n, V), 0.0);
    float ndh = max(dot(n, H), 0.0);

    float ambient = 0.22;
    float fill    = 0.15 * max(dot(n, normalize(vec3(-0.45, -0.30, 0.60))), 0.0);
    float shade   = min(ambient + (1.0 - ambient) * ndl + fill, 1.0);

    float specTight = pow(ndh, 96.0);
    float spec      = 0.9 * specTight + 0.25 * pow(ndh, 12.0);
    float fresnel   = pow(1.0 - ndv, 3.0);

    vec3 rgb = (uColor * shade + (spec + 0.12 * fresnel) * vec3(1.0)) * uTint;
    float a  = clamp(uAlpha + 0.5 * specTight + 0.08 * fresnel, 0.0, 1.0);

    FragColor = vec4(rgb, a);
}
"""
