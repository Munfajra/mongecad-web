package gl3d.render

/**
 * Shadery pro čárovou grafiku.
 *
 * Port desktopových `shaders/line.vert` a `line.frag` s dvěma nutnými změnami
 * (viz `WEBGL_3D_PLAN.md`, kapitola 4):
 *
 *  1. **Šířka čáry.** WebGL `lineWidth()` ignoruje – vždy kreslí 1 px. Úsek se
 *     proto ve vertex shaderu roztáhne na obrazovkový obdélník. Geometrie je
 *     instancovaná: statický quad ze 6 vrcholů × jedna instance na úsek.
 *
 *  2. **Posun hloubky.** Desktop píše `gl_FragDepth = gl_FragCoord.z + bias`.
 *     Tady se stejný posun udělá už ve vertex shaderu (`z += 2·bias·w`) –
 *     u ortografické projekce je to přesně totéž, ale nevypne to early-Z.
 *     Okenní hloubka je `(ndc.z+1)/2`, proto ta dvojka.
 *
 * `vDist` je průběžná délka v **obrazovkových pixelech**, počítaná na CPU
 * stejně jako na desktopu (`projectedDistancePx`) – vzor čárkování tak zůstává
 * stejně hustý bez ohledu na zoom i na zkrácení úsečky perspektivou pohledu.
 */
internal object LineShaders {

    const val VERTEX = """#version 300 es
precision highp float;

// statický quad: x = podíl podél úseku (0/1), y = strana (-1/+1)
layout(location = 0) in vec2 aCorner;
// po instanci: koncové body úseku a jejich délková souřadnice
layout(location = 1) in vec3 aPosA;
layout(location = 2) in vec3 aPosB;
layout(location = 3) in vec2 aDist;

uniform mat4  uViewProj;
uniform vec2  uViewport;    // v device pixelech
uniform float uWidth;       // šířka čáry v device pixelech
uniform float uDepthBias;   // v okenní hloubce (0..1), jako na desktopu

out float vDist;
out float vSide;

void main() {
    vec4 clipA = uViewProj * vec4(aPosA, 1.0);
    vec4 clipB = uViewProj * vec4(aPosB, 1.0);

    vec2 halfViewport = uViewport * 0.5;
    vec2 screenA = (clipA.xy / clipA.w) * halfViewport;
    vec2 screenB = (clipB.xy / clipB.w) * halfViewport;

    vec2 delta = screenB - screenA;
    float len = length(delta);
    vec2 dir = (len > 1e-6) ? delta / len : vec2(1.0, 0.0);
    vec2 normal = vec2(-dir.y, dir.x);

    vec4 clip = mix(clipA, clipB, aCorner.x);
    vDist = mix(aDist.x, aDist.y, aCorner.x);
    vSide = aCorner.y;

    // Obdélník je o pixel širší než čára – ten pixel navíc je přechodová
    // zóna, ve které fragment shader dopočítá pokrytí (viz vSide).
    // Rozšíření je zadané v pixelech, proto zpět přes poloviční výřez.
    // Násobení clip.w drží tloušťku konstantní i po perspektivním dělení.
    vec2 offsetNdc = (normal * ((uWidth + 1.0) * 0.5) * aCorner.y) / halfViewport;

    gl_Position = vec4(
        clip.xy + offsetNdc * clip.w,
        clip.z + 2.0 * uDepthBias * clip.w,
        clip.w
    );
}
"""

    const val FRAGMENT = """#version 300 es
precision highp float;

in float vDist;
in float vSide;
layout(location = 0) out vec4 FragColor;

uniform vec3  uColor;
uniform float uAlpha;
uniform float uPattern;   // 0=plná, 1=čárkovaná, 2=tečkovaná, 3=čerchovaná
uniform float uWidth;

void main() {
    float d = vDist;
    bool draw = true;

    if (uPattern == 1.0) {
        draw = mod(d, 13.0) < 8.0;
    } else if (uPattern == 2.0) {
        draw = mod(d, 10.0) < 2.0;
    } else if (uPattern == 3.0) {
        float m = mod(d, 18.0);
        draw = (m < 8.0) || (m > 12.0 && m < 14.0);
    }

    if (!draw) discard;

    // Analytické vyhlazení hran. Nahrazuje MSAA, které si plátno nemůže
    // zapnout kvůli sdílení hloubky s OIT bufferem – a u tenkých čar dává
    // hladší výsledek než čtyřnásobné vzorkování.
    float distanceFromCenter = abs(vSide) * (uWidth + 1.0) * 0.5;
    float coverage = clamp(uWidth * 0.5 - distanceFromCenter + 0.5, 0.0, 1.0);
    if (coverage <= 0.0) discard;

    FragColor = vec4(uColor, uAlpha * coverage);
}
"""
}
