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
 * Kuželové plochy kreslené analyticky.
 *
 * ## Jediné místo portu, které nešlo přenést doslova
 *
 * Desktopový `shaders/cone.frag` je na `#version 400 core` a počítá průsečík
 * v **dvojité přesnosti** (`double`, `dvec3`, `dmat3` na 63 místech). GLSL ES
 * 3.00, na kterém WebGL2 stojí, dvojitou přesnost nemá vůbec – není to
 * volitelné rozšíření, prostě v jazyce není. Shader je proto přepsaný do
 * jednoduché přesnosti a robustnost se dohání dvěma věcmi:
 *
 *  1. **Počátek paprsku se posune k vrcholu kužele.** Původní počátek leží na
 *     přední ořezové rovině, tedy až 5000 jednotek daleko; druhé mocniny
 *     takových čísel ukrajují ve `float` většinu platných číslic. Posunutím na
 *     bod paprsku nejbližší vrcholu klesnou vstupy kvadratické rovnice
 *     o několik řádů. Desktop tohle nedělá, protože v `double` nemusí.
 *  2. **Škálování koeficientů** před řešením kvadratické rovnice a stabilní
 *     tvar kořenů, obojí převzaté z desktopu.
 *
 * Kdyby se přesnost přesto někde nedostávala, projeví se to zrněním na
 * siluetě při velkém přiblížení nebo u výkresů s velkými souřadnicemi.
 */
class ConeRenderer(private val gl: Gl) {

    private var program: GlProgram? = null
    private val uniforms = HashMap<String, GlUniform?>()

    val isReady: Boolean get() = program != null

    fun initialize(): Boolean {
        if (program != null) return true
        val prog = gl.createProgram("cone", QUADRIC_FULLSCREEN_VERTEX, FRAGMENT)
        if (prog == null) {
            gl3dLog("ConeRenderer: program se nepodařilo přeložit")
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
        apex: Vec3,
        /** Kvadratická forma v souřadnicích s počátkem ve vrcholu, po sloupcích. */
        quadric: FloatArray,
        planeNormal: Vec3,
        planeK: Float,
        directrixCenter: Vec3,
        directrixU: Vec3,
        directrixV: Vec3,
        directrixInvA2: Float,
        directrixInvB2: Float,
        alpha: Float = 1f,
        tint: Float = 1f,
        depthBias: Float = 0f,
    ) {
        if (program == null) return
        gl.uniform3f(uniforms["uColor"], color.red, color.green, color.blue)
        gl.uniform3f(uniforms["uApex"], apex.x, apex.y, apex.z)
        gl.uniformMatrix3fv(uniforms["uQuadricA"], quadric)
        gl.uniform3f(uniforms["uPlaneN"], planeNormal.x, planeNormal.y, planeNormal.z)
        gl.uniform1f(uniforms["uPlaneK"], planeK)
        gl.uniform3f(
            uniforms["uDirectrixCenter"],
            directrixCenter.x, directrixCenter.y, directrixCenter.z,
        )
        gl.uniform3f(uniforms["uDirectrixU"], directrixU.x, directrixU.y, directrixU.z)
        gl.uniform3f(uniforms["uDirectrixV"], directrixV.x, directrixV.y, directrixV.z)
        gl.uniform1f(uniforms["uDirectrixInvA2"], directrixInvA2)
        gl.uniform1f(uniforms["uDirectrixInvB2"], directrixInvB2)
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
    "uQuadricA", "uApex", "uPlaneN", "uPlaneK",
    "uDirectrixCenter", "uDirectrixU", "uDirectrixV",
    "uDirectrixInvA2", "uDirectrixInvB2",
    "uAlpha", "uTint", "uDepthBias",
)

private const val FRAGMENT = """#version 300 es
precision highp float;

layout(location = 0) out vec4 FragColor;

uniform vec3  uColor;
uniform vec2  uViewport;
uniform mat4  uInvViewProj;
uniform mat4  uViewProj;

uniform mat3  uQuadricA;
uniform vec3  uApex;

uniform vec3  uPlaneN;
uniform float uPlaneK;
uniform vec3  uDirectrixCenter;
uniform vec3  uDirectrixU;
uniform vec3  uDirectrixV;
uniform float uDirectrixInvA2;
uniform float uDirectrixInvB2;

uniform float uAlpha;
uniform float uTint;
uniform float uDepthBias;

bool solveQuadraticStable(float A, float B, float C, out float t0, out float t1) {
    // Škálování srovná řády koeficientů; ve float je to podstatně důležitější
    // než v desktopové double verzi.
    float s = max(max(abs(A), abs(B)), abs(C));
    if (s > 0.0) { A /= s; B /= s; C /= s; }

    if (abs(A) < 1e-12) {
        if (abs(B) < 1e-12) return false;
        float t = -C / B;
        t0 = t; t1 = t;
        return true;
    }

    float disc = B * B - 4.0 * A * C;
    if (disc < 0.0) return false;

    float sq = sqrt(max(0.0, disc));
    float sgn = (B >= 0.0) ? 1.0 : -1.0;
    float q = -0.5 * (B + sgn * sq);

    if (abs(q) < 1e-20) {
        float inv = 0.5 / A;
        float a = (-B - sq) * inv;
        float b = (-B + sq) * inv;
        t0 = min(a, b);
        t1 = max(a, b);
        return true;
    }

    float r0 = q / A;
    float r1 = C / q;
    t0 = min(r0, r1);
    t1 = max(r0, r1);
    return true;
}

/** Podíl vzdálenosti od vrcholu k rovině řídicí kuželosečky; 0..1 je uvnitř. */
float apexPlaneRatio(vec3 pLocal, vec3 planeNhat, float planeK) {
    return dot(planeNhat, pLocal) / planeK;
}

bool insideDirectrixDisk(vec3 p) {
    if (uDirectrixInvA2 <= 0.0 || uDirectrixInvB2 <= 0.0) return false;
    vec3 rel = p - uDirectrixCenter;
    float qu = dot(rel, uDirectrixU);
    float qv = dot(rel, uDirectrixV);
    return qu * qu * uDirectrixInvA2 + qv * qv * uDirectrixInvB2 <= 1.0 + 1e-5;
}

void main() {
    mat3 A = 0.5 * (uQuadricA + transpose(uQuadricA));

    vec2 ndc = (gl_FragCoord.xy / uViewport) * 2.0 - 1.0;
    vec4 pNear4 = uInvViewProj * vec4(ndc, -1.0, 1.0);
    vec4 pFar4  = uInvViewProj * vec4(ndc,  1.0, 1.0);
    vec3 worldNear = pNear4.xyz / pNear4.w;
    vec3 worldFar  = pFar4.xyz / pFar4.w;

    vec3 rd = worldFar - worldNear;
    float rdLen = length(rd);
    if (rdLen < 1e-20) discard;
    rd /= rdLen;

    // Posun počátku na bod paprsku nejbližší vrcholu. Bez toho vstupují do
    // kvadratické rovnice souřadnice o řád 1e3 a jejich druhé mocniny sežerou
    // ve float většinu platných číslic (viz dokumentace třídy).
    float shift = dot(uApex - worldNear, rd);
    vec3 ro = worldNear + shift * rd;

    vec3 roL = ro - uApex;

    float Aq = dot(rd, A * rd);
    float Bq = 2.0 * dot(roL, A * rd);
    float Cq = dot(roL, A * roL);

    vec3 planeN = uPlaneN;
    float nLen = length(planeN);
    if (nLen < 1e-20) discard;
    vec3 planeNhat = planeN / nLen;
    float planeK = uPlaneK / nLen;
    if (abs(planeK) < 1e-12) discard;

    // Zásah musí ležet před kamerou; `shift` posunul počátek, takže se
    // porovnává součet.
    float epsT = 1e-5;
    float tBest = 1e30;
    vec3 pBestW = vec3(0.0);
    bool hitCap = false;

    float t0, t1;
    if (solveQuadraticStable(Aq, Bq, Cq, t0, t1)) {
        if (t0 + shift > epsT) {
            vec3 p0L = roL + t0 * rd;
            float s0 = apexPlaneRatio(p0L, planeNhat, planeK);
            if (s0 > 0.0 && s0 < 1.0) { tBest = t0; pBestW = uApex + p0L; }
        }
        if (t1 + shift > epsT && t1 < tBest) {
            vec3 p1L = roL + t1 * rd;
            float s1 = apexPlaneRatio(p1L, planeNhat, planeK);
            if (s1 > 0.0 && s1 < 1.0) { tBest = t1; pBestW = uApex + p1L; }
        }
    }

    float capDenom = dot(planeNhat, rd);
    if (abs(capDenom) > 1e-12) {
        float tCap = (planeK - dot(planeNhat, roL)) / capDenom;
        if (tCap + shift > epsT && tCap < tBest) {
            vec3 pCapW = ro + tCap * rd;
            if (insideDirectrixDisk(pCapW)) {
                tBest = tCap;
                pBestW = pCapW;
                hitCap = true;
            }
        }
    }

    if (tBest > 1e29) discard;

    vec3 pBestL = pBestW - uApex;

    if (!hitCap) {
        // Pojistka: bod musí ležet na tvořicí přímce, která v rovině řídicí
        // kuželosečky protíná právě ji. Bez toho by se implicitní kvadrika
        // v některých konstrukcích chovala jako neomezený dvojkužel.
        float s = apexPlaneRatio(pBestL, planeNhat, planeK);
        if (s <= 0.0 || s >= 1.0) discard;
        vec3 rel = (uApex + pBestL / s) - uDirectrixCenter;
        float qu = dot(rel, uDirectrixU);
        float qv = dot(rel, uDirectrixV);
        float membership = qu * qu * uDirectrixInvA2 + qv * qv * uDirectrixInvB2;
        if (abs(membership - 1.0) > 0.035) discard;
    }

    vec4 clip = uViewProj * vec4(pBestW, 1.0);
    float depth = 0.5 * (clip.z / clip.w) + 0.5;
    gl_FragDepth = clamp(depth + uDepthBias, 0.0, 1.0);

    vec3 g = A * pBestL;
    float glen = length(g);
    vec3 nW = hitCap ? planeNhat : ((glen < 1e-12) ? (-rd) : (g / glen));
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
