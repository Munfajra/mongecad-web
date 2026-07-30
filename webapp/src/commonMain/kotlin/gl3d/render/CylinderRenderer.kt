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
 * Válcové plochy kreslené analyticky, stejným způsobem jako koule –
 * celoobrazovkový trojúhelník a průsečík paprsku s plochou v shaderu.
 *
 * Port `shaders/cylinder.frag`. Ten je na rozdíl od `cone.frag` psaný celý
 * v jednoduché přesnosti, takže šel přenést doslova; změnil se jen výstup
 * (jedna barva místo dvou OIT cílů) a hlavička na `#version 300 es`.
 *
 * Plocha je zadaná řídicí elipsou v dolní rovině, směrem tvořic a dvěma
 * omezujícími rovinami. Kromě pláště se testují i obě podstavy.
 */
class CylinderRenderer(private val gl: Gl) {

    private var program: GlProgram? = null
    private val uniforms = HashMap<String, GlUniform?>()

    val isReady: Boolean get() = program != null

    fun initialize(): Boolean {
        if (program != null) return true
        val prog = gl.createProgram("cylinder", QUADRIC_FULLSCREEN_VERTEX, FRAGMENT)
        if (prog == null) {
            gl3dLog("CylinderRenderer: program se nepodařilo přeložit")
            return false
        }
        program = prog
        for (name in UNIFORM_NAMES) uniforms[name] = gl.uniformLocation(prog, name)
        return true
    }

    fun begin(viewProjection: Mat4, invViewProjection: Mat4, width: Int, height: Int) {
        val prog = program ?: return
        gl.useProgram(prog)
        gl.uniform2f(uniforms["uViewport"], width.toFloat(), height.toFloat())
        gl.uniformMatrix4fv(uniforms["uViewProj"], viewProjection.data)
        gl.uniformMatrix4fv(uniforms["uInvViewProj"], invViewProjection.data)
    }

    fun draw(
        color: Color,
        center: Vec3,
        u: Vec3,
        v: Vec3,
        invA2: Float,
        invB2: Float,
        direction: Vec3,
        basePlaneNormal: Vec3,
        basePlaneD: Float,
        topPlaneNormal: Vec3,
        topPlaneD: Float,
        alpha: Float = 1f,
        tint: Float = 1f,
        depthBias: Float = 0f,
    ) {
        if (program == null) return
        gl.uniform3f(uniforms["uColor"], color.red, color.green, color.blue)
        gl.uniform3f(uniforms["uCenter"], center.x, center.y, center.z)
        gl.uniform3f(uniforms["uU"], u.x, u.y, u.z)
        gl.uniform3f(uniforms["uV"], v.x, v.y, v.z)
        gl.uniform1f(uniforms["uInvA2"], invA2)
        gl.uniform1f(uniforms["uInvB2"], invB2)
        gl.uniform3f(uniforms["uDir"], direction.x, direction.y, direction.z)
        gl.uniform3f(uniforms["uPlane0N"], basePlaneNormal.x, basePlaneNormal.y, basePlaneNormal.z)
        gl.uniform1f(uniforms["uPlane0D"], basePlaneD)
        gl.uniform3f(uniforms["uPlane1N"], topPlaneNormal.x, topPlaneNormal.y, topPlaneNormal.z)
        gl.uniform1f(uniforms["uPlane1D"], topPlaneD)
        gl.uniform1f(uniforms["uAlpha"], alpha)
        gl.uniform1f(uniforms["uTint"], tint)
        gl.uniform1f(uniforms["uDepthBias"], depthBias)
        gl.drawArrays(GlPrimitive.TRIANGLES, 0, 3)
    }

    fun end() = gl.useProgram(null)

    fun dispose() {
        program?.let(gl::deleteProgram)
        program = null
        uniforms.clear()
    }
}

private val UNIFORM_NAMES = listOf(
    "uColor", "uViewport", "uInvViewProj", "uViewProj",
    "uCenter", "uU", "uV", "uInvA2", "uInvB2", "uDir",
    "uPlane0N", "uPlane0D", "uPlane1N", "uPlane1D",
    "uAlpha", "uTint", "uDepthBias",
)

/** Sdílený vertex shader všech analytických kvadrik – celoobrazovkový trojúhelník. */
internal const val QUADRIC_FULLSCREEN_VERTEX = """#version 300 es
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

uniform vec3  uCenter;
uniform vec3  uU;
uniform vec3  uV;
uniform float uInvA2;
uniform float uInvB2;

uniform vec3  uDir;

uniform vec3  uPlane0N;  uniform float uPlane0D;
uniform vec3  uPlane1N;  uniform float uPlane1D;

uniform float uAlpha;
uniform float uTint;
uniform float uDepthBias;

bool solveQuadraticStable(float A, float B, float C, out float t0, out float t1) {
    float s = max(max(abs(A), abs(B)), abs(C));
    if (s > 0.0) { A /= s; B /= s; C /= s; }

    const float EPSA = 1e-14;
    if (abs(A) < EPSA) {
        if (abs(B) < 1e-14) return false;
        t0 = -C / B; t1 = 1e30;
        return true;
    }
    float disc = B * B - 4.0 * A * C;
    if (disc < 0.0) return false;

    if (abs(disc) < 1e-18) {
        float t = -B / (2.0 * A);
        t0 = t; t1 = t; return true;
    }
    float sq = sqrt(max(0.0, disc));
    float q  = -0.5 * (B + (B >= 0.0 ? sq : -sq));
    float r0 = q / A;
    float r1 = C / q;
    t0 = (r0 < r1) ? r0 : r1;
    t1 = (r0 < r1) ? r1 : r0;
    return true;
}

bool betweenPlanesAlongDir(vec3 p, out float sFromBase) {
    float n0d = dot(uPlane0N, uDir);
    if (abs(n0d) < 1e-8) {
        if (abs(dot(uPlane0N, p) + uPlane0D) > 1e-6) return false;
        sFromBase = 0.0;
    } else {
        sFromBase = (dot(uPlane0N, p) + uPlane0D) / n0d;
    }

    vec3 r = p - sFromBase * uDir;
    float n1d = dot(uPlane1N, uDir);
    if (abs(n1d) < 1e-8) return false;
    float sTop = -(dot(uPlane1N, r) + uPlane1D) / n1d;

    const float epsBase = -1e-7;
    const float epsTop  =  1e-4;
    if (sTop >= 0.0) return (sFromBase >= epsBase && sFromBase <= sTop - epsTop);
    else             return (sFromBase <= -epsBase && sFromBase >= sTop + epsTop);
}

bool insideBaseDisk(vec3 p) {
    float n0d = dot(uPlane0N, uDir);
    if (abs(n0d) < 1e-10) return false;
    float s = (dot(uPlane0N, p) + uPlane0D) / n0d;
    vec3 r = p - s * uDir;
    float xi  = dot(r - uCenter, uU);
    float eta = dot(r - uCenter, uV);
    return xi * xi * uInvA2 + eta * eta * uInvB2 <= 1.0 + 1e-5;
}

/** Jakobián projekce do dolní roviny podél tvořic – potřeba pro normálu. */
mat3 projectionJacobian(float n0d) {
    return mat3(1.0) - mat3(
        uDir.x * uPlane0N.x, uDir.x * uPlane0N.y, uDir.x * uPlane0N.z,
        uDir.y * uPlane0N.x, uDir.y * uPlane0N.y, uDir.y * uPlane0N.z,
        uDir.z * uPlane0N.x, uDir.z * uPlane0N.y, uDir.z * uPlane0N.z
    ) / n0d;
}

vec3 surfaceNormal(vec3 p, float n0d) {
    float s = (dot(uPlane0N, p) + uPlane0D) / n0d;
    vec3  r = p - s * uDir;
    float xi  = dot(r - uCenter, uU);
    float eta = dot(r - uCenter, uV);
    vec3 g = 2.0 * uInvA2 * xi * uU + 2.0 * uInvB2 * eta * uV;
    return normalize(transpose(projectionJacobian(n0d)) * g);
}

void main() {
    vec2 ndc = (gl_FragCoord.xy / uViewport) * 2.0 - 1.0;
    vec4 pNear4 = uInvViewProj * vec4(ndc, -1.0, 1.0);
    vec4 pFar4  = uInvViewProj * vec4(ndc,  1.0, 1.0);
    vec3 ro = pNear4.xyz / pNear4.w;
    vec3 rf = pFar4.xyz  / pFar4.w;
    vec3 rd = normalize(rf - ro);
    ro += rd * 1e-5;

    float n0d = dot(uPlane0N, uDir);
    if (abs(n0d) < 1e-10) discard;

    // Paprsek se promítne do roviny podstavy podél tvořic; v těchto
    // souřadnicích je průsečík s elipsou obyčejná kvadratická rovnice.
    float s0 = (dot(uPlane0N, ro) + uPlane0D) / n0d;
    vec3  r0 = ro - s0 * uDir;
    float sdir = dot(uPlane0N, rd) / n0d;
    vec3  rdir = rd - sdir * uDir;

    float alpha0 = dot(r0 - uCenter, uU);
    float beta0  = dot(r0 - uCenter, uV);
    float alpha1 = dot(rdir, uU);
    float beta1  = dot(rdir, uV);

    float A = uInvA2 * alpha1 * alpha1 + uInvB2 * beta1 * beta1;
    float B = 2.0 * (uInvA2 * alpha0 * alpha1 + uInvB2 * beta0 * beta1);
    float C = uInvA2 * alpha0 * alpha0 + uInvB2 * beta0 * beta0 - 1.0;

    const float epsT = 1e-5;
    float tBest = 1e30;
    vec3 pW = vec3(0.0);
    bool hitCap = false;
    vec3 capNormal = vec3(0.0);

    float t0, t1;
    if (solveQuadraticStable(A, B, C, t0, t1)) {
        bool valid0 = false, valid1 = false;
        vec3 p0 = vec3(0.0), p1 = vec3(0.0);
        float sTmp;
        if (t0 > epsT) { p0 = ro + t0 * rd; valid0 = betweenPlanesAlongDir(p0, sTmp); }
        if (t1 > epsT) { p1 = ro + t1 * rd; valid1 = betweenPlanesAlongDir(p1, sTmp); }

        if (valid0 && valid1) {
            // Ze dvou zásahů pláště vyhrává ten, co je k pozorovateli
            // otočený čelem – bližší kořen může být odvrácená stěna.
            float f0 = dot(surfaceNormal(p0, n0d), -rd);
            float f1 = dot(surfaceNormal(p1, n0d), -rd);
            if (f1 > f0 + 1e-6 || (abs(f1 - f0) <= 1e-6 && t1 < t0)) { tBest = t1; pW = p1; }
            else                                                     { tBest = t0; pW = p0; }
        } else if (valid0) { tBest = t0; pW = p0; }
        else if (valid1)   { tBest = t1; pW = p1; }
    }

    float den0 = dot(uPlane0N, rd);
    if (abs(den0) > 1e-8) {
        float tCap0 = -(dot(uPlane0N, ro) + uPlane0D) / den0;
        if (tCap0 > epsT && tCap0 < tBest) {
            vec3 pCap0 = ro + tCap0 * rd;
            if (insideBaseDisk(pCap0)) {
                tBest = tCap0; pW = pCap0; hitCap = true;
                capNormal = normalize(uPlane0N);
            }
        }
    }

    float den1 = dot(uPlane1N, rd);
    if (abs(den1) > 1e-8) {
        float tCap1 = -(dot(uPlane1N, ro) + uPlane1D) / den1;
        if (tCap1 > epsT && tCap1 < tBest) {
            vec3 pCap1 = ro + tCap1 * rd;
            if (insideBaseDisk(pCap1)) {
                tBest = tCap1; pW = pCap1; hitCap = true;
                capNormal = normalize(uPlane1N);
            }
        }
    }

    if (tBest >= 1e29) discard;

    vec4 clip = uViewProj * vec4(pW, 1.0);
    float depth = 0.5 * (clip.z / clip.w) + 0.5;
    gl_FragDepth = clamp(depth + uDepthBias, 0.0, 1.0);

    vec3 nW = hitCap ? capNormal : surfaceNormal(pW, n0d);
    if (dot(nW, -rd) < 0.0) nW = -nW;

    vec3 V = -rd;
    vec3 L = normalize(vec3(0.55, 0.70, 0.45));
    vec3 H = normalize(L + V);

    float ndl = max(dot(nW, L), 0.0);
    float ndv = max(dot(nW, V), 0.0);
    float ndh = max(dot(nW, H), 0.0);

    float ambient = 0.22;
    float fill    = 0.15 * max(dot(nW, normalize(vec3(-0.45, -0.30, 0.60))), 0.0);
    float shade   = min(ambient + (1.0 - ambient) * ndl + fill, 1.0);

    float specTight = pow(ndh, 96.0);
    float spec      = 0.9 * specTight + 0.25 * pow(ndh, 12.0);
    float fresnel   = pow(1.0 - ndv, 3.0);

    vec3 rgb = (uColor * shade + (spec + 0.12 * fresnel) * vec3(1.0)) * uTint;
    float a  = clamp(uAlpha + 0.5 * specTight + 0.08 * fresnel, 0.0, 1.0);

    FragColor = vec4(rgb, a);
}
"""
