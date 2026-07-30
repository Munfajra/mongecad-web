# Plán: 3D zobrazení scény ve webové verzi (WebGL2)

Cíl: přenést desktopové okno `opengl/` (LWJGL + GLFW + ImGui + NanoVG) do prohlížeče
jako 3D náhled scény nad stejnými daty z `MongeState`.

Tento dokument nahrazuje položku *„OpenGL/LWJGL okno a 3D renderer“* ze sekce
„Záměrně mimo webovou variantu“ v `MIGRATION_PLAN.md`. Ostatní vyloučené položky
(PDF export, fill occlusion, obecné průniky) plán nemění.

---

## 0. Jak navázat (čti první)

### Kde to stojí

| Etapa | Stav |
|---|---|
| E1 kostra (kontext, matice, kamera, viewport) | ✅ hotovo |
| E2 čáry (osy s ryskami, přímky, úsečky, body, popisky) | ✅ hotovo |
| E3 roviny, kuželosečky, stopy, OIT | ✅ hotovo |
| E4 kvadriky, plochy, skryté hrany | ✅ hotovo |
| E5 výběr a snapy kamery | ✅ hotovo |
| E6 export snímku, dokončení | 🚧 export hotový; zbývá výkonový rozpočet a dokumentace |

> **AXO režim webová verze nemá**, takže se do 3D náhledu neportuje nic, co na
> něm stojí: `ObliqueAxoView.kt`, nativní šikmé promítání, `obliqueBlendFactor`
> ani snapy na axonometrickou průmětnu a bokorysnu. Kamera jede vždy standardní
> orbitální větví.

### Sestavení a ověření

```bash
cd webapp
./gradlew compileKotlinWasmJs   # rychlá kontrola překladu (~10 s)
./gradlew deployToSite          # produkční build do ../app/ (~2 min)
```

Produkční build se přestal vejít do 2 GB haldy Kotlin démona a padal na
„Not enough memory to run compilation“; `gradle.properties` proto drží
`kotlin.daemon.jvmargs=-Xmx4g`.

**`aplikace.html` načítá bundle vždy z `app/mongecad-web.js`.**
`wasmJsBrowserDevelopmentRun` proto změny do stránky nedostane – náhled vždy
vyžaduje `deployToSite` a pak libovolný statický server nad kořenem repa:

```bash
cd .. && python3 -m http.server 8090   # → http://localhost:8090/aplikace.html
```

Ověřuje se **ve Firefoxu**. Chrome dostupný přes nástroje asistenta nemá WebGL
vůbec, takže v něm nenaběhne ani Skiko.

### Ověřování bez ručního klikání

Dvě techniky, které se osvědčily a ušetřily hodně kol:

1. **Izolovaný WebGL2 test v čistém JS.** Zkopírovat shader do samostatné
   HTML stránky, vykreslit, a výsledek ověřit `readPixels` proti analyticky
   spočítané hodnotě. Takhle se ověřil kompozit OIT (na pixel přesně) i to,
   že se všechny tři kvadrikové shadery přeloží jako GLSL ES 300. Screenshoty
   z headless Firefoxu **obsah WebGL plátna nezachytí**, ale text na stránce
   ano – proto výsledky vypisovat do DOM, ne do konzole.
2. **Diagnostika ven přes HTTP.** Když je potřeba dostat čísla z běžící wasm
   aplikace, dočasně v `gl3dLog` posílat `fetch('/__gl3d?m=…')` a číst je
   z logu `python3 -m http.server`. Headless Firefox konzoli nevypisuje.

```bash
firefox --headless --profile <profil> --window-size=1600,900 --screenshot out.png file://…
```

### Mapa kódu

```
commonMain/gl3d/
  api/        Gl (rozhraní nad WebGL2), GlSurface (plátno), gl3dLog
  math/       Mat4, Vec3                      ← náhrada JOML
  camera/     Camera3D, buildCameraMatrices   ← port kamery z RenderScene.kt
  render/     ThickLineRenderer + LineBatch   ← veškerá čárová grafika
              TriangleBatch/TriangleRenderer  ← plošná grafika (roviny, výplně)
              SphereRenderer, CylinderRenderer, ConeRenderer  ← analytické kvadriky
              MeshRenderer + Mesh3D           ← sítě trojúhelníků + GPU cache
              OitPipeline                     ← weighted blended OIT
  camera/     CameraSnaps                     ← přelety na půdorys/nárys
  scene/      SceneRenderer                   ← pořadí průchodů, port renderScene3D
              ReferencePlanes, UserPlanes, PlaneTraces, Conics, QuadricGeometry
              SolidsOfRevolution, RuledSurfaceMeshes, SegmentSolidMeshes
              SelectionHighlight              ← zvýraznění výběru
wasmJsMain/gl3d/
  WebGl2Externals.kt, WebGl2Backend.kt, GlSurface.wasmJs.kt, GlDebug.wasmJs.kt
ui/gl3d/Gl3DViewport.kt                       ← Compose obal, vstup kamery, popisky
```

Vstupní bod je `SceneRenderer.render()` – drží pořadí průchodů podle desktopu
a je to místo, kam se přidávají další typy objektů.

### Na čem se pokračuje

**E6 – dokončení.** Export snímku 3D scény do PNG (`readPixels` + zakódování,
navázat na stávající `export/bitmapRenderer`), výkonový rozpočet a aktualizace
`README.md` / `MIGRATION_PLAN.md` / `changelog.html`.

---

## 1. Výchozí stav

### Co hraje pro nás

| Fakt | Důsledek |
|---|---|
| Webový `MongeState` je téměř zrcadlo desktopového (744 vs. ~800 řádků, stejné názvy kolekcí) | Renderer může číst `lines3D`, `planes3D`, `segments3D`, `conicalSurfaces`, `cylindricalSurfaces`, `spheres3D`, `ruledSurfaces`, `segmentSolids3D`, `intersectionGroups` **beze změny modelu** |
| `geometry/conics/ConicMath.kt` už je port geometrické části desktopového `opengl/model/Conics.kt` | Vzorkování kuželoseček pro 3D je hotové |
| `model/CameraSnap.kt`, `model/CameraAnim.kt` už na webu byly | Snapy a animace kamery stačilo zavolat (E5) |
| `state/MongeState.kt:304` má `isOpenGLWindowRunning` | Hook pro zapnutí/vypnutí panelu už existuje |
| Kvadriky (koule, kužel, válec) se na desktopu kreslí **analyticky ray-castem** přes fullscreen trojúhelník (`sphere.frag`, `cone.frag`, `cylinder.frag`) | Nulová geometrie k přenosu do GPU, výborný poměr kvalita/výkon i na webu |
| Compose Multiplatform wasm už WebGL používá (Skiko) | Kontext WebGL2 je na cílových prohlížečích ověřeně dostupný |

### Co chybí

Webový projekt nemá **žádný** ekvivalent `opengl/` — ani matematiku matic, ani
`ObliqueAxoView`, ani `LabelPlacement3D`, ani `projectToScreen`
(`utils/GeometryUtils.kt:49` to explicitně konstatuje).

### Rozsah desktopového originálu

```
RenderScene.kt              1894   hlavní pipeline, pořadí průchodů, stencil, OIT
SelectionHighlight.kt       1357   zvýraznění výběru
openglWindow.kt              888   GLFW okno, smyčka, snapy kamery  → na webu jinak
UI/ (ImGui)                 2698   ObjectList + SelectionEdit       → na webu zahodit, je v Compose
model/ (bez UI)             ~3900  Points, LineTypes, Segments, Planes, Prumetny,
                                   Traces, Conics, Cones, Cylinders, Spheres, SoR,
                                   RuledSurfaces, SegmentSolids, LabelPlacement3D
ObliqueAxoView.kt            324   čistá matematika, přenositelné 1:1
OitRenderer.kt               188
Camera.kt / ShadersLoad.kt   271
shaders/*.vert,*.frag        851   GLSL 330/400 core
```

K portu tedy reálně jde **≈ 8 000 řádků Kotlinu + 850 řádků GLSL**; ImGui vrstva
(2 700 ř.) odpadá, protože web už má plnohodnotný Compose `ObjectList`
a `RightDescriptionBar`.

---

## 2. Volba technologie

**Doporučení: vlastní WebGL2 backend, port desktopových shaderů.**

| Varianta | Hodnocení |
|---|---|
| **WebGL2 + port shaderů** ✅ | Zachová vizuál 1:1 — analytické kvadriky, OIT, stencilové skryté hrany. GLSL 330 → GLSL ES 300 je mechanická úprava. Nejvíc práce, ale jediná cesta k identickému obrazu. |
| Three.js přes JS interop ❌ | Kvůli analytickým kvadrikám a stencil/OIT pipeline bychom stejně psali vlastní `ShaderMaterial` a `onBeforeRender` hooky — Three.js by přidal ~600 kB a vlastní scénograf navíc, aniž by cokoli ušetřil. |
| WebGPU ❌ | Safari podpora je čerstvá, WGSL by znamenal přepis všech shaderů. Vhodné jako pozdější backend za stejné rozhraní `Gl`. |
| Softwarový rasterizér v Compose Canvas ❌ | Bez depth bufferu; kvadriky a occlusion nejdou. |

---

## 3. Architektura

### 3.1 Umístění canvasu vůči Compose

**Zásadní podrobnost, na kterou se přijde až v praxi:** `ComposeViewport` si na
kontejner připojí **shadow DOM** (`attachShadow`, mode OPEN — v `ui-wasm-js`
klibu jsou řetězce `layerRoot`/`shadowRoot`/`ShadowRootMode`). Skutečná
struktura je:

```
#webApp                          position: fixed, z-index: 9999
 └─ #shadow-root (open)
     ├── <canvas id="mongecad-gl">   z-index -1  … 3D viewport
     └── <div>                       position: relative
          └── <canvas>                          … celé Compose UI
```

Jakmile má element shadow root, jeho **light-DOM potomci se vůbec
nevykreslují** — shadow strom je nahradí. Plátno pověšené na `#webApp`
obvyklým `appendChild` je proto neviditelné: `getBoundingClientRect()` vrací
`0×0` a `getComputedStyle` prázdné hodnoty. Musí dovnitř shadow rootu, vedle
vrstvy Composu.

Compose oznamuje pozici a velikost přes `onGloballyPositioned` v místě, kde je
`Gl3DViewport()` composable; ten volá `syncGlCanvasRect(x, y, w, h)`.

**Problém překryvů — vyřešeno hole-punchem.** GL plátno leží na `z-index: -1`
pod vrstvou Composu a viewport si do Compose plátna vyřízne průhlednou díru:

```kotlin
Modifier.drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }
```

Tím je 3D vidět skrz díru a všechno, co Compose kreslí *potom* — dialogy,
menu, tooltipy — zůstane korektně nad ním.

Nejdřív se zkoušela levnější varianta (GL plátno **nad** Composem, schované po
dobu otevřeného dialogu). Neobstála ze dvou stran: dialogy, které neinkrementují
`state.openDialogCount` (nastavení), se kreslily za plátno a byly z půlky
neviditelné, a u těch ostatních zase 3D panel při každém dialogu zmizel.
`openDialogCount` navíc přes svůj komentář zvyšuje jen `LinesRename`.

### 3.2 Vrstvení kódu

```
src/commonMain/kotlin/gl3d/
  math/          Mat4, Vec3, ortho(), lookAt(), invert(), projectToScreen()   ← náhrada JOML
  camera/        CameraState, orbit/pan/zoom, CameraSnap + CameraAnim glue
  scene/         SceneBuilder: MongeState → seznam kreslicích příkazů
  api/           interface Gl (tenká abstrakce nad GL voláními) + enums
  passes/        port RenderScene.kt: pořadí průchodů, stencil, OIT, bias
  labels/        LabelPlacement3D → screen-space pozice popisků (bez kreslení)

src/wasmJsMain/kotlin/gl3d/
  WebGl2Context.kt    external interface WebGL2RenderingContext (+ typované konstanty)
  WebGl2Backend.kt    implementace Gl
  GlCanvasHost.kt     vytvoření/umístění canvasu, resize, DPR, context-lost
  Shaders.kt          GLSL zdroje jako konstanty (žádný classpath, žádné fetch)
```

Klíčové rozhodnutí: **`interface Gl` místo `expect/actual`.** Renderovací
pipeline zůstane v `commonMain` a je testovatelná proti záznamovému fake
backendu; WebGL2 se dotýká jen tenká vrstva v `wasmJsMain`. Zároveň to otevírá
cestu k WebGPU backendu bez přepisu pipeline.

### 3.3 Vstup a smyčka

- Kamera: `pointerdown/move/up` + `wheel` přímo na GL canvasu (levá = orbit,
  pravá = pan, kolečko = zoom), logika 1:1 z `opengl/Camera.kt:installInputCallbacks`.
- Smyčka: `requestAnimationFrame`, ale **on-demand** — překreslit jen když je
  `dirty` (změna kamery, `sceneVersion`, `triggerRedraw`, resize, běžící
  `CameraAnim`/`obliqueBlend`). Na desktopu běží `glfwSwapInterval(1)` napořád;
  na webu by to zbytečně žralo baterii.
- Žádná vlákna: renderer běží na hlavním vlákně, čte Compose stav přímo. Odpadá
  tím celý problém `[[background-compose-writes-edt]]` z desktopu.

---

## 4. Rozdíly GL 3.3 compat → WebGL2 a jejich řešení

Toto je seznam všeho, co **nelze přenést doslova**. Každá položka má ověřené řešení.

| # | Desktop | Problém na webu | Řešení |
|---|---|---|---|
| 1 | `glMatrixMode`/`glOrtho`/`glMultMatrixf`/`glGetFloatv(GL_MODELVIEW_MATRIX)` (`RenderScene.kt:665-812`) | Fixed-function pipeline ve WebGL2 neexistuje | Vlastní `Mat4` v `gl3d/math`. Matice se dnes stejně jen postaví a hned zase přečte do JOML — nahradí se přímým sestavením `P` a `V`. Přímočará, mechanická změna. |
| 2 | JOML `Matrix4f` | JVM-only | Tentýž `Mat4` (~150 řádků: `ortho`, `mul`, `invert`, `transform`). |
| 3 | `glLineWidth(w)` — 11 volání v `LineTypes.kt`, `Points.kt`, `Segments.kt`, `Traces.kt` | **WebGL šířku čar ignoruje**, vždy 1 px | **Jeden sdílený „thick polyline“ renderer**: expanze na screen-space quady ve vertex shaderu. Desktop už posílá per-vertex délku oblouku (`vertices = x,y,z,arcLength`) kvůli čárkování — ten atribut se použije beze změny. Všechny čárové kresby (přímky, úsečky, stopy, křížky bodů, osy, polyliny kuželoseček) půjdou přes tuto jednu cestu. |
| 4 | OIT: MRT `RGBA16F` + `RGBA16F`, per-attachment blend `glBlendFunci` (`OitRenderer.kt`) | WebGL2 nemá indexovaný blend v jádře (jen rozšíření `EXT_draw_buffers_indexed`) | **Single-target WBOIT**: `accum.rgb` s `ONE, ONE` a `revealage` v alfa kanálu téhož `RGBA16F` cíle s `ZERO, ONE_MINUS_SRC_ALPHA` — to je McGuirova původní varianta a jde přes `blendFuncSeparate`, které WebGL2 má. Když je `EXT_draw_buffers_indexed` k dispozici, použij dvoucílovou variantu 1:1 podle desktopu. |
| 5 | `RGBA16F` render target | Ve WebGL2 nutné rozšíření | `EXT_color_buffer_float` (podpora dnes univerzální). Bez něj degradovat na `RGBA8` + škálování accum, nebo OIT vypnout a řadit průhledné plochy podle hloubky. |
| 6 | `#version 330 core`, `#version 400 core` (cone.frag) | WebGL2 přijímá jen `#version 300 es` | Mechanická úprava: přidat `precision highp float; precision highp int;`, `layout(location=)` u výstupů zůstává, `in/out` beze změny. `cone.frag` je na 400 nejspíš historicky — po převodu ověřit, že nepoužívá nic nad ESSL 3.00. |
| 7 | `gl_FragDepth` (line/sphere/cone/cylinder/quadric frag) | — | ESSL 3.00 `gl_FragDepth` má, funguje. Pozor jen na to, že zápis do něj vypíná early-Z — na mobilních GPU citelné, sledovat ve výkonovém rozpočtu. |
| 8 | Stencil (59 volání, `RenderScene.kt:942+`) — maskování skrytých hran silhouetou těles | — | Funguje, jen si kontext musí vyžádat `{ stencil: true }` a OIT FBO potřebuje `DEPTH24_STENCIL8` místo desktopového `DEPTH_COMPONENT24`. |
| 9 | `glBlitFramebuffer` pro kopii hloubky do OIT FBO | — | WebGL2 `blitFramebuffer` existuje; hloubkový blit vyžaduje shodné formáty. |
| 10 | `glGenVertexArrays()` + `glGenBuffers()` **v každém draw callu** (`LineTypes.kt:52`) | Na desktopu to projde, v prohlížeči je každé GL volání drahé (JS↔wasm hranice) | ✅ hotovo: čárová a plošná grafika jde přes sdílené batche s jedním VBO, sítě přes cache v `MeshRenderer` klíčovanou id objektu a podpisem jeho geometrie. Zároveň nutné, protože bez toho by šlo o leak (desktop VAO nemaže). |
| 11 | `FloatBuffer` → `glBufferData` | Kotlin/Wasm nesdílí paměť s JS typed arrays přímo | Alokovat `Float32Array` jednou na buffer a plnit ji jen při rebuildu (viz #10). **Spike E0** změří cenu přenosu; když bude bolet, existuje cesta přes `Float32Array` view nad wasm pamětí. Scéna je z principu malá (čáry a polyliny), kvadriky negenerují geometrii vůbec — reálně jde jen o SoR a přímkové plochy. |
| 12 | Popisky přes **NanoVG** (`drawAxisLabelNanoVG`, `Traces.kt`) | NanoVG neexistuje | `gl3d/labels` spočítá jen **screen-space pozice**; vykreslí je Compose `Text` v overlay vrstvě nad viewportem. Bonus: reuse už nabundlovaného `latinmodern-math.otf` a jednotná typografie s 2D plátnem. |
| 13 | UI uvnitř GL okna přes **ImGui** (`UI/`, 2 698 ř.) | — | **Neportovat.** Web už má Compose `ObjectList` a `SelectionInfo` v pravém panelu; 3D panel je jen viewport. Ušetří ~2 700 řádků. |
| 14 | GLFW okno, vlákno, `glfwSwapBuffers`, shutdown hooky (`openglWindow.kt`, `InnitOpenGL.kt`) | — | Nahradí `rAF` smyčka + `GlCanvasHost`. Ze souboru se přenáší jen logika snapů kamery (`targetForSnap`, `beginCameraSnapAnimation`, `updateObliqueBlend`, `resetCamera`) — ta je čistě matematická. |
| 15 | Shadery z classpath (`readResourceText`) | — | GLSL jako Kotlin konstanty. Žádný `fetch`, žádný extra roundtrip při startu. |
| 16 | MSAA přes `GL_MULTISAMPLE` + `msaaSamples` v nastavení | — | `{ antialias: true }` v kontextu; počet vzorků prohlížeč nedává na výběr. Položku `msaaSamples` v nastavení nechat skrytou (`Settings.kt:14` už podobné položky řeší). |
| 17 | — | **Ztráta WebGL kontextu** (přepnutí tabu, GPU reset) | `webglcontextlost`/`restored` handler s kompletní reinicializací zdrojů. `aplikace.html:189` už podobný monitor pro Skiko má — rozšířit ho. |
| 18 | Sdílení jednoho GL kontextu | Compose (Skiko) má svůj, my druhý | Dva WebGL kontexty na stránce jsou v pořádku (limit bývá 8–16), ale je nutné hlídat, že si nepřebíráme stav — každý má vlastní. |

---

## 5. Etapy

Každá etapa je samostatně nasaditelná a končí viditelným výsledkem.

> **Stav a další kroky jsou v kapitole 0 nahoře.** Tady zůstává původní členění
> na etapy s poznámkami, co se v které udělalo a na co se přišlo.
>
> Data kvadrik se do webu **načítají** (`JSONopen.kt` plní `spheres3D`,
> `conicalSurfaces`, `cylindricalSurfaces`) – `UnsupportedContent.kt` na ně jen
> upozorňuje kvůli 2D části. E4 tedy není blokovaná portem 2D strany, je to
> čistě práce v `gl3d/`.

### E0 — Spike (½–1 den). **Udělat první, mění zbytek plánu**

Cíl: rozhodnout tři neznámé měřením, ne odhadem.

1. Prázdný `<canvas>` s WebGL2 kontextem uvnitř `#webApp`, jeden trojúhelník
   `#version 300 es`, řízený z Kotlin/Wasm přes `@JsFun`/external interface.
2. **Ověřit hole-punch**: `BlendMode.Clear` v Compose Canvas → je pod ním
   opravdu vidět GL canvas? Rozhoduje mezi „dokovaný panel“ a „celoobrazovkový režim“.
3. **Změřit přenos**: nahrání 100 k floatů do `Float32Array` + `bufferData`,
   kolik ms. Rozhoduje o agresivitě cachování.

**Akceptace:** trojúhelník je vidět v produkčním buildu, obě otázky mají číselnou odpověď.

**Poznámka k průběhu.** Spike se nakonec dělal rovnou jako E1 – WebGL2 kontext,
překlad shaderů a instancované kreslení se ověřily na skutečné scéně místo na
trojúhelníku. **Hole-punch funguje** a je nasazený (viz 3.1), takže varianta
s celoobrazovkovým režimem odpadá. Cenu přenosu dat je potřeba změřit až na
reálné scéně s SoR (etapa E4).

Ladicí postup, který se osvědčil a stojí za zopakování: headless Firefox
(`firefox --headless --profile … URL`) proti lokálnímu `python3 -m http.server`
a aplikace posílá diagnostiku přes `fetch('/__gl3d?m=…')`, takže se čte ze
serverového logu. Konzoli headless Firefox nevypisuje a screenshoty
nezachycují obsah WebGL plátna, tohle je proto jediná cesta, jak se ze
zabalené wasm aplikace dostat k číslům bez ručního klikání.

### E1 — Kostra (2–3 dny) ✅

- `gl3d/math` (Mat4/Vec3), `interface Gl` + `WebGl2Backend`
- `GlCanvasHost`: umístění dle Compose layoutu, DPR, resize, context-lost
- kamera: orbit/pan/zoom, `resetCamera`, ortografická projekce se stejnými
  `zNear/zFar = ±5000` jako desktop
- `rAF` on-demand smyčka
- tlačítko „3D náhled“ do `ToolBar.kt` (skupina „Náhled“, kterou komentář na
  `ToolBar.kt:29` už rezervuje), navázané na `isOpenGLWindowRunning`

**Akceptace:** panel jde otevřít, orbituje se v něm prázdná scéna s barvou pozadí z nastavení.

**Hotovo:** `gl3d/math` (`Mat4`, `Vec3`), `gl3d/api` (`Gl`, `GlSurface`),
`wasmJsMain/gl3d` (WebGL2 externals, backend, plátno v `#webApp` s DPR,
ošetření ztráty kontextu), `gl3d/camera/Camera3D` (orbit/pan/zoom, ortografie
se stejnými `zNear/zFar = ±5000` a výchozí polohou jako `resetCamera`),
`ui/gl3d/Gl3DViewport` s překreslováním na vyžádání, `state.show3DPanel`,
tlačítko ve skupině „Náhled“ a rozdělení kreslicí plochy v `AppMongeUI`.

### E2 — Čáry a průmětny (3–5 dní) — *první opravdu užitečný výstup* 🚧

- **thick polyline renderer** (bod #3 výše) — jádro celého portu, dělá se jednou
- port `line.vert/frag` (včetně čárkování z délky oblouku a `uDepthBias`)
- `model/Points.kt` (křížky), `model/LineTypes.kt` (`Line3D`), `model/Segments.kt`
- `model/Prumetny.kt` — referenční průmětny π, ν, μ
- `drawReferenceAxes3D` včetně kuželových hrotů os a rysek měřítka
- popisky os přes Compose overlay (bod #12)

**Akceptace:** body, přímky, úsečky a průmětny stávajícího výkresu odpovídají
desktopovému 3D oknu při stejné pozici kamery. Vizuální diff proti screenshotu z desktopu.

**Hotovo:** `ThickLineRenderer` + `LineBatch` (instancovaný quad, čárkování
z délky v obrazovkových pixelech jako na desktopu, posun hloubky ve vertex
shaderu místo `gl_FragDepth`), `SceneRenderer` s přímkami (`Line3D` včetně
`customTrimRange`), úsečkami, křížky bodů (se stejným vyřazením koncových bodů
a vrcholů těles jako `opengl/model/Points.kt`), osami s drátěným hrotem
a měřítkovými ryskami (`drawAxisScaleTicks3D`).

**Popisky os** jdou přes Compose overlay podle návrhu v bodě 4/#12: renderer
vrací jen polohy v pixelech (`Axis3DLabel`), text sází Compose ve vrstvě nad
plátnem. Souřadnice sedí 1:1, protože v CMP na webu platí
`density == devicePixelRatio`, takže Compose pixel je totéž co device pixel.

**Ovládání** je nad rámec desktopu rozšířené o dotyk: jeden prst orbit, dva
prsty posun a zároveň pinch zoom. Zoom kolečkem je násobný (`1,12^kroky`)
a krok je omezený na jedno cvaknutí na událost — desktop spoléhá na to, že
GLFW posílá vždy ±1, kdežto prohlížeč posílá i násobně větší hodnoty
(u touchpadu naopak zlomky) a lineární vzorec z desktopu pak přeskočil
půl scény na jedno cvaknutí.

**Zbývá:** popisky stop rovin a referenční průmětny π/ν/μ
(`model/Prumetny.kt`) — ty se dělají až s průhledností v E3, protože bez OIT
by přebily celou scénu.

### E3 — Roviny, kuželosečky, OIT (4–6 dní) ✅

- `model/Planes.kt` + `planes.vert/frag`, `renderPlanesDepthOnly`,
  `renderPlanesInteractionStyle`, `renderReferencePlanesGeoGebraStyle`
- `OitRenderer` v single-target variantě (bod #4)
- `model/Conics.kt` — kreslení už vzorkovaných kuželoseček (matematika hotová
  v `geometry/conics/ConicMath.kt`)
- `model/Traces.kt` — stopy rovin + jejich popisky

**Akceptace:** průhledné roviny se správně prolínají, stopy a kuželosečky sedí,
`COPLANAR_*_DEPTH_BIAS` konstanty dávají stejný výsledek jako na desktopu.

**Hotovo:** referenční průmětny π/ν/μ (`collectReferencePlanes`, port
`renderReferencePlanesGeoGebraStyle`) včetně prstenců, kterými průhlednost
plyne k okraji, a stejných konstant z `Viz.kt`. Nový `TriangleBatch`
+ `TriangleRenderer` s barvou po vrcholu – celá plošná grafika tak jde jedním
draw callem a poslouží i výplním kvadrik v E4. Pořadí průchodů odpovídá
desktopu: běžná geometrie se zápisem do hloubky → roviny bez zápisu (tónují,
co je za nimi) → osy až nakonec, aby se do rovin nezanořily.

**OIT je hotové** (`OitPipeline`) – nasazené rovnou, aby se plošná část
nepřepisovala podruhé, až přijdou kvadriky. Řešení problému z bodu 4/#4
(WebGL2 nemá indexovaný blend) není single-target WBOIT, ale **logaritmus**:
revealage je součin `Π(1−aᵢ)`, tedy `exp(Σ ln(1−aᵢ))`, takže se dá akumulovat
sčítáním úplně stejně jako numerátor. Oba cíle proto jedou na `(ONE, ONE)`,
jedna sdílená blend funkce stačí a nic se neaproximuje.

Sdílení hloubky je vyřešené jinak než na desktopu: scéna i OIT používají
**tentýž depth/stencil renderbuffer**, takže odpadá `glBlitFramebuffer` kopie
hloubky i hádání, jestli má výchozí framebuffer prohlížeče kompatibilní
formát. Cenou je, že se celý snímek kreslí do offscreen cíle a na plátno se
překlopí až hotový, a že plátno nesmí mít MSAA (vícevzorkový framebuffer
nejde sdílet). Hrany čar se proto vyhlazují **analyticky** v `LineShaders`
podle vzdálenosti od osy čáry – u tenkých čar to vypadá lépe než MSAA.

Když chybí `EXT_color_buffer_float` nebo framebuffer nevyjde kompletní, celá
cesta se vypne a průhledné plochy se míchají přímo, seřazené podle hloubky.

Dvě věci ověřené měřením v izolovaném WebGL2 testu (headless Firefox,
`readPixels`): kompozit dává přesně analytickou hodnotu, a alfa cíle se při
kompozitu musí míchat `(ZERO, ONE)` – jinak klesne pod jedničku všude, kde
leží rovina, plátno je tam poloprůhledné a prosvítá skrz něj Compose.

**Uživatelské roviny** (`collectUserPlanes`) a **stopy rovin**
(`collectPlaneTraces`, průsečnice s π/ν/μ) jsou taky hotové.

**Pozor na past:** dvojprůchod v desktopovém `renderPlanesInteractionStyle`
(hloubkový test `GREATER` pro zakrytou část, `LEQUAL` pro přední) **není**
obcházení pořadí míchání, jak by se mohlo zdát, ale **záměrný hloubkový klíč** –
zakrytá část se kreslí ztmavená (×0,72) a průhlednější. Bez něj se dvě
protínající se roviny slijí do jedné barvy a není poznat, která je vpředu.
Weighted blended OIT tuhle informaci z principu zahazuje, takže uživatelské
roviny přes OIT jít **nesmí**; desktop je taky vede mimo něj
(`renderScene3D:904` vs. OIT blok na 1152–1182, kde jsou jen průmětny).

**Druhá past hned vedle:** hloubkový prepass, který ten klíč umožňuje, nesmí
v bufferu zůstat. Rovina je průhledná, takže nemá právo cokoli zakrývat – a
když si svou hloubku ponechá, všechno kreslené později (referenční průmětny,
osy, stopy) za ní zmizí úplně, místo aby skrz ni prosvítalo. Projeví se to
tak, že roviny působí jako zcela neprůhledné, přestože samy vůči sobě
vypadají správně. Hloubka se proto po dvojprůchodu vrátí do stavu „jen čárová
geometrie“ – vyčistí se a čáry se překreslí bez barvy, což je levnější než
jakékoli zálohování.

**Kuželosečky** (`collectConics`) jsou hotové a byly levné: `geometry/conics/
ConicMath.kt` je už dřívější port téhož vzorkovacího kódu z desktopu, jen bez
vazby na GL, takže zbylo klasifikovat, vzít vzorkovač a poslat lomenou čáru
do čárového batche. Výřezy (oblouky) se berou ze stejných map jako na desktopu
(`ellipseArcParams3D`, `parabolaArcParams3D`, `hyperbolaArcParams3D`,
`hyperbolaArcEnds3D`) – bez nich by se z rozpracované konstrukce kreslila vždy
celá kuželosečka.

**Popisky stop** (p₁, n₂, b₃ + horní index roviny) jdou stejnou cestou jako
popisky os, tedy přes Compose overlay.

**E3 je tím hotová.**

### E4 — Kvadriky a plochy (5–7 dní) ✅

- `sphere.frag`, `cone.frag`, `cylinder.frag`, `quadric.frag` — analytický
  ray-cast, přenos je hlavně o převodu na ESSL 3.00
- `model/SoR.kt`, `model/RuledSurfaces.kt`, `model/SegmentSolids.kt` — mesh
  generátory + `sor.vert/frag`; sem míří VBO cache z bodu #10
- kompletní pořadí průchodů z `RenderScene.kt`: depth pre-pass ploch →
  stencilová maska těles → přední/skryté hrany → OIT průhledné plochy

**Akceptace:** koule/kužely/válce/rotační a přímkové plochy včetně tint pro
zadní stěny (`Viz.kt` konstanty) odpovídají desktopu; skryté hrany se čárkují
`HIDDEN_LINE_ALPHA = 0.28` uvnitř siluet.

**Hotovo: koule** (`SphereRenderer`, port `shaders/sphere.vert|frag`). Kreslí
se **analyticky** – jediný celoobrazovkový trojúhelník a fragment shader
počítá průsečík paprsku s koulí, kdo netrefí, zahodí se. Do GPU tím pádem
neputuje žádná geometrie a silueta je hladká v každém přiblížení. Hloubka se
zapisuje ručně přes `gl_FragDepth`, jinak by koule ležela v hloubce
celoobrazovkového trojúhelníku a nic by ji nezakrylo.

Kvadriky jdou do **neprůhledného** průchodu, ne do OIT: desktopová
`QUADRIC_FRONT_ALPHA` je 1, takže obyčejný hloubkový test dá správné zakrytí
čar za nimi. Kvůli tomu se obnova hloubky po průchodu rovin (viz past výše)
musí týkat i kvadrik – proto `drawOpaque(colorEnabled = false)` místo
překreslení samotných čar.

**Hotovo: válce** (`CylinderRenderer`, port `shaders/cylinder.frag`). Ten je
psaný celý v jednoduché přesnosti, takže šel přenést doslova – změnil se jen
výstup a hlavička. Paprsek se promítne do roviny podstavy podél tvořic a tam
je průsečík s elipsou obyčejná kvadratická rovnice. Testují se i obě podstavy
a ze dvou zásahů pláště vyhrává ten otočený k pozorovateli čelem, ne nutně
bližší kořen.

Střed řídicí elipsy web neuměl spočítat (`computeEllipseAxes3D` vrací jen
poloosy a jejich směry), doplňuje ho `ellipseParams` v `QuadricGeometry.kt` –
port `ellipseFromConic3D` z desktopu.

**Hotovo: kužely** – a je to **jediná část celého portu, která nešla přenést
doslova**. `cone.frag` je na `#version 400 core` a počítá průsečík
v dvojité přesnosti (`double`/`dvec3`/`dmat3` na 63 místech). **GLSL ES 3.00
dvojitou přesnost nemá vůbec** – není to volitelné rozšíření, prostě
v jazyce není. Shader je proto přepsaný do jednoduché přesnosti a robustnost
se dohání dvěma věcmi:

1. **Počátek paprsku se posune k vrcholu kužele.** Původní počátek leží na
   přední ořezové rovině, tedy až 5000 jednotek daleko, a druhé mocniny
   takových čísel ukrajují ve `float` většinu platných číslic. Posunutím na
   bod paprsku nejbližší vrcholu klesnou vstupy kvadratické rovnice o několik
   řádů. Desktop tohle nedělá, protože v `double` nemusí.
2. **Škálování koeficientů** a stabilní tvar kořenů, obojí převzaté z desktopu.

Kdyby se přesnost přesto nedostávala, projeví se to zrněním na siluetě při
velkém přiblížení nebo u výkresů s velkými souřadnicemi.

Kvadratickou formu kuželové plochy počítá `coneQuadricLocal`
(`QuadricGeometry.kt`), port `buildConeQuadricLocal`.

**Hotovo: sítě trojúhelníků** – rotační plochy, přímkové plochy a tělesa
z úseček. Je to jediné místo celého portu, kam do GPU putuje netriviální
geometrie, takže tudy prochází nový `MeshRenderer` s **cache podle objektu**:
klíčem je id, podpisem hash jeho geometrie, a dokud se podpis nezmění, mesh se
nepřestavuje ani nepřenáší. Desktop si `glGenVertexArrays` v každém draw callu
dovolit může (a nemaže je, takže tam leakují), v prohlížeči by to bylo drahé.
Sítě chodí jedním prokládaným VBO (pozice + normála) a indexy jako
`UNSIGNED_INT` – kvůli tomu `Gl` přibyly indexové buffery, `drawElements`,
`cullFace` a `polygonOffset`.

Shader je port `sor.frag` znak po znaku, jen s jediným výstupem: sítě jdou
stejně jako kvadriky do **neprůhledného** průchodu, ne do OIT. Ověřený
izolovaným WebGL2 testem – překlad jako GLSL ES 300 i barva na pixel proti
analyticky spočítané hodnotě (0,807622 / 0,036503 → 206, 9, 9).

- **Rotační plochy** (`scene/SolidsOfRevolution.kt`): port
  `buildSoRMeshFromMeridianXZ` včetně znaménkového profilu s průsečíky s osou,
  zdvojení bodu, kde meridián osu jen protne, a vějířů v pólech. Desktopové
  `require` nahradilo `null` – ve webu není kam vypsat stack trace, rozpracovaná
  konstrukce se prostě nekreslí. Konvence osy (+z pro nárysovou, +y pro
  půdorysovou, bez ohledu na orientaci přímky) zůstala.
- **Tělesa z úseček** (`scene/SegmentSolidMeshes.kt`): port
  `buildSegmentSolidMesh` – kanonizace splývajících vrcholů podle kvantované
  polohy, stěny z uložených polygonů nebo z grafu hran, orientace ven,
  otrojúhelníkování uchem. Záporný `polygonOffset` proti z-fightingu
  s koplanární rovinou je taky převzatý.
- **Přímkové plochy** (`scene/RuledSurfaceMeshes.kt`) mají **jiný zdroj
  geometrie než desktop**. Desktop si pro mesh nechá plochu převzorkovat na
  96 tvořic přes `sampleRuledSurfaceTrimmedPrimaryFamilies`
  (`monge/input/ruledsurface/RuledSurfaceGeometry.kt`, ~2 300 řádků, které web
  nemá a které s vykreslováním nesouvisí). Web bere tvořice, které už ve stavu
  jsou: `generatorLineIds` odkazují na skutečné `Line3D` a jejich
  `customTrimRange` drží přesně kreslený úsek. Výplň tak vždy sedí na
  vykreslené tvořice, jen je hrubší – `generatorCount` (výchozí 16) místo 96;
  zhustí se zvýšením počtu tvořic v panelu plochy. `generatorLineIds` je navíc
  zřetězení rodin, takže se hranice hledají znovu podle nápadně většího
  rozestupu – poslední tvořice jedné větve hyperboly není soused první tvořice
  druhé a pás mezi nimi by byl vymyšlený. Uzavřenost rodiny se čte z uložených
  snapshotů, stejně jako ji čte desktopové `isDirectrixClosed`.

**Hotovo: čárkování skrytých hran.** Port bloku „PLANE TRACES / hidden lines“
z `renderScene3D`. Znamenalo to přesunout čárovou geometrii **na konec** snímku
(dřív se kreslila jako první): po rovinách a osách se hloubka i stencil postaví
znovu, **jen z překážek** – hloubka řekne, co je před čím, stencil je
obrazovkové sjednocení uživatelských rovin a vyplněných ploch. Čáry pak jdou
třikrát: mimo překážky bez hloubkového testu plnou barvou, uvnitř překážek
`LEQUAL` plnou barvou a `GREATER` čárkovaně s `HIDDEN_LINE_ALPHA = 0.28`.

Dvě pasti odtud: stencil se **nesmí** stavět z hloubkového prepassu (jinak
druhá překážka přestane čáry klasifikovat všude, kde je před ní jiná – proto
zvlášť průchod s `depthFunc = ALWAYS`), a čáry potřebují koplanární posun
hloubky `−2e-5`, jinak se čára ležící v rovině začne čárkovat sama proti sobě.
Referenční průmětny mezi překážky schválně nepatří; čárkovaly by celou
konstrukci.

### E5 — Výběr a interakce (3–4 dny) ✅

- `SelectionHighlight.kt` — zvýraznění vybraných objektů
- obousměrná vazba na Compose `ObjectList` a `SelectionInfo`
- snapy kamery na Půdorys / Nárys ~~/ Bokorys / Axo~~ (`CameraSnap`,
  `CameraAnim` na webu už existovaly) ~~+ `obliqueBlendFactor` animace
  a `ObliqueAxoView` port~~

**Akceptace:** výběr v pravém panelu se zvýrazní ve 3D a naopak; snapy kamery
plynule animují.

**Hotovo: zvýraznění výběru** (`scene/SelectionHighlight.kt`). Desktop volá pro
každý typ objektu vlastní kreslicí funkci s vlastním VAO; tady se všechno sbírá
do jednoho `LineBatch`, protože čárová grafika stejně prochází jedním
instancovaným draw callem. Vzor je u všech typů stejný jako na desktopu –
**široké modré halo** a přes něj objekt vlastní barvou o něco silněji.
Zvýraznění se kreslí úplně nakonec s `depthFunc = ALWAYS` a bez zápisu do
hloubky, takže je vidět i skrz tělesa; bez toho by vybraná hrana zmizela pod
plochou, na které leží.

Tělesa se zvýrazňují **obrysem**, ne vyplněním: koule prstencem siluety,
kužel a válec dvěma krajními tvořicími přímkami (port `coneSilhouetteGenerators`
a `cylinderSilhouetteGenerators`), rotační plocha dvěma krajními tvořicími
křivkami (port `sorSilhouettePolylines`, přibyl do `SolidsOfRevolution.kt`).
Prstence jdou jako uzavřené lomené čáry místo desktopového mezikruží
z trojúhelníkového pásu – tlustý renderer to zvládne rovnou.

Dvě zjednodušení proti desktopu: přímková plocha dostane své **tvořice** místo
obrysu skládaného sjednocením cest (ten na desktopu běží na vlastním vlákně
s cache a na webu by to znamenalo port `RuledSurfaceOutlines.kt`), a šrafování
vybrané roviny se drží čtverce, který renderer kreslí (`collectUserPlanes`),
místo desktopového řezu krychlí – jinak by zvýraznění přesahovalo mimo
nakreslenou rovinu.

**Hotovo: vazba na pravý panel.** Žádný kód nepotřebovala – obě strany čtou
a píší tentýž `MongeState` (na desktopu je to stejné, jen ten seznam běží
v ImGui uvnitř GL okna). Chyběl jen **impulz k překreslení**: výběr `sceneVersion`
nezvedá, scéna se nemění, mění se jen co je v ní vybrané. Řeší to `snapshotFlow`
nad otiskem výběru, který si řekne o snímek a přitom nerekomponuje viewport.

**Hotovo: snapy kamery** (`camera/CameraSnaps.kt`) na půdorys a nárys plus
reset, s animací přes `CameraAnim` a `easeInOut`. Tlačítka leží přímo nad
plátnem, protože web nemá lištu vloženého náhledu. Ve druhém řádku jsou k nim
**přepínače viditelnosti** (půdorysna, nárysna, stopy, osy) – totéž, co má
desktop v menu „Zobrazení". Vypnutý přepínač se jen ztlumí, takže řádek
nepodskakuje a stav je vidět bez zaškrtávátek.

Past: `showReferencePlanesP/N/B` a `showTraces` byly ve webovém `MongeState`
prosté `var` (na desktopu jsou to `mutableStateOf`), takže by se z nich
nepřekreslila ani lišta, ani scéna. Teď jsou to Compose stav v obou verzích.

**Stejný vzhled dostala i desktopová lišta** (`ui/components/EmbeddedPreviewPane.kt`):
rozbalovací menu „Zobrazení" a „Pohled" s výchozími Material položkami
a `Checkbox` nahradily ploché čipy ve dvou řádcích, `OutlinedTextField`
v exportu vystřídalo `MiniInputField`. Menu zůstalo jen u exportu, ten
potřebuje pole na rozměry.

Dvě odchylky vynucené kreslením na vyžádání: čas animace nejde ze
`System.nanoTime()` (v common Kotlinu není), ale z Compose `withFrameNanos` –
což je navíc přesně ten čas, ke kterému se snímek kreslí; a `advanceCameraSnap`
vrací `true` i v posledním kroku, kdy dosadí přesnou cílovou polohu. Desktop
tam vrací `false`, protože kreslí pořád – tady by se poslední krok nikdy
nevykreslil a přelet by skončil kousek před cílem.

### Doladění podle desktopu (po E5)

Sedm drobností, které se ukázaly až při porovnání se samostatným OpenGL oknem:

1. **Hrot osy je plný kužel**, ne drátěná síť úseček – port `drawConeMesh3D`
   z `opengl/model/LineTypes.kt` do vlastního `TriangleBatch` (plášť do vrcholu
   + vějíř podstavy, 18 segmentů, plná barva osy bez stínování). Kreslí se hned
   po tělech os, se zápisem do hloubky.
2. **Stopy rovin se v záporných souřadnicích zeslabují** – port
   `drawTraceClipped3D`. Půdorysná stopa je plná tam, kde je `y ≥ 0`, nárysná
   nad půdorysnou (`z ≥ 0`); zbytek jde v `weakAlpha = 0,24` a tenčí. Bez toho
   působí konstrukce pod půdorysnou stejně platně jako ta viditelná. Ořez podél
   záporného x řeší desktop jen v AXO, ten na webu není.
   **Šířka se bere z `Plane3D.strokeWidth`**, ne z desktopových napevno
   zadaných 5 px – 3D náhled tak odpovídá tomu, co je nastavené na plátně.
   Zeslabená část si drží desktopový poměr 2,8 : 5.
   Past: číst ji přes `plane.tracePudorys.strokeWidth` **nejde**, i když ten
   getter na rovinu ukazuje (`parent?.strokeWidth`). Po změně šířky se rovina
   nahradí `copy()` a `relinkPlaneToTraces` přepojí jen stopy ve
   `state.lineTraces*`; instance uvnitř `Plane3D` si drží odkaz na předchozí
   kopii, takže 3D náhled kreslil pořád původní šířku.
3. **Bokorysná stopa se nekreslí** – desktop ji zapíná jen v AXO režimu.
4. **Název roviny je horní index** na `p₁`/`n₂` (desktop bere `plane.name`,
   web dřív sahal na `plane.superscript`, který je prázdný).
5. **Popisky bodů a přímek** – dřív měly popisek jen osy. Body kopírují desktop
   (název + horní index, kousek vpravo dolů od značky, bez bodů, které jsou jen
   koncem sdružené úsečky); přímky sedí na pevném podílu vykresleného úseku
   místo desktopového kolizního `placeLabelNearLine3D`.
6. **Popisky sází stejný Skia kód jako 2D plátno** (`draw/mongescreen/labels/`)
   místo Compose `Text`. Tím sedí rodina písma, kurzíva u názvů i sazba horních
   indexů; obyčejný `Text` kreslil bezpatkovým UI fontem. Velikost jde ze
   `activeLabelSizePx * 0,7` jako u 2D popisků, jen bez násobení zoomem plátna.
   Vrstva popisků **musí mít `clipToBounds()`**: sází se přes `nativeCanvas`,
   který o rozvržení Composu neví, takže text objektu mimo výřez jinak přeteče
   do 2D plátna a do UI. Popisky os se u kraje přidržují (`coerceIn`) jako na
   desktopu, aby x/y/z nezmizelo, když hrot vyjede ven; stejné umístění platí
   i pro export, jinak by se lišil od náhledu.
7. **Šířka 3D panelu se táhne** táhlem na hranici s plátnem
   (`state.gl3dSplitRatio`, meze 0,15–0,85). Drží se poměr, ne šířka v
   pixelech, aby rozdělení přežilo změnu velikosti okna; výška obou polovin je
   daná oknem. Že jde o táhlo, se pozná podle trvale viditelného úchytu (tři
   tečky uprostřed), ztmavení a rozšíření čáry pod myší a kurzoru `ew-resize`.
   Kurzor jde přes CSS, protože `PointerIcon` ve společném API žádnou variantu
   pro změnu velikosti nemá; `setCanvasCursor` se u toho musel opravit, aby
   Compose plátno hledal **uvnitř shadow rootu** – v light DOM žádné není,
   takže dosud tiše nedělal nic. Zvýraznění dostal sdílený
   `VerticalResizeHandleOverlay`, tedy i táhlo pravého panelu.

### Druhé kolo doladění

1. **Zvýraznění bralo výběr jen z 3D kolekcí.** `selectedPoints3D`,
   `selectedLines3D` a `selectedSegments3D` plní jen seznam objektů; klik na 2D
   plátně označí **průmět** (`selectedPointsPudorys`, `selectedLinesNarys`, …).
   Ve 3D se proto nezvýraznilo nic z toho, co uživatel vybral na plátně. Teď se
   id doplňují i z průmětů přes jejich `parent`/`parentId` – kuželosečky to
   takhle měly od začátku, ostatní typy ne.
2. **Přímková plocha se nezvýrazňuje vůbec.** Náhrada za neportovaný desktopový
   obrys (zvýraznění všech tvořic) scénu jen zaplavila čarami.
3. **`curves3D` se konečně kreslí** (`scene/Curves.kt`) – port `drawCurve3D`
   včetně Catmull-Rom vzorkování řídicích bodů; průniky, které nesou hotovou
   `polyline3D`, jdou rovnou. Tím pádem se zvýrazňují i části průniků typu
   `CURVE3D`, které se předtím musely vynechávat.
4. **Tmavý režim: čistě černá se ve 3D kreslí bíle** (`Color.gl3dLineColor()`
   v `model/RuntimeCanvasColors.kt`) – přímky, úsečky, křivky, kuželosečky,
   křížky bodů, osy, stopy i všechny popisky. Proti tmavému pozadí černá jinak
   zapadne. Plochy těles (kužel, válec, koule, rotační i přímková plocha,
   tělesa z úseček) mají místo toho `Color.gl3dSurfaceColor()`, která černou
   mění na **šedou**: bílá plocha přes půl scény by přebila konstrukci, kvůli
   které tam je. Sphere/cone/cylinder tu substituci na desktopu měly už dřív
   inline, teď je jednotná. **Tahle změna je i v desktopové verzi**
   (`opengl/model/*`, `RenderScene.kt`, `Traces.kt`).

### E6 — Dokončení (2–3 dny) 🚧

- ✅ export snímku 3D scény do PNG (`ExportImg.kt` → `readPixels` + Skia;
  navázáno na stávající `export/bitmapRenderer`)
- ✅ degradace bez WebGL2: srozumitelná hláška místo prázdného obdélníku
  (`Gl3DUnavailable`)
- ⬜ výkonový rozpočet: cíl 60 fps při orbitu na střední scéně, ověřit i na mobilu
- ⬜ aktualizace `README.md`, `MIGRATION_PLAN.md` a `changelog.html`

**Hotovo: export snímku** (`gl3d/export/Scene3DExport.kt`, tlačítko „Uložit
PNG" v liště nad plátnem). Desktop kreslí do vlastního FBO a čte ho
`glReadPixels`; tady se čte offscreen cíl, který už kvůli OIT existuje – stačilo
`SceneRenderer.render(captureRgba = true)`, který pixely přečte **před**
překlopením na plátno. Bez OIT se čte výchozí framebuffer, tam ale platí
velikost plátna.

Dvě věci, které se musely doplnit:

- **Popisky v GL obraze nejsou** – renderer žádný text neumí a sází je Compose
  nad plátnem. Do exportu se proto dokreslují tímtéž Skia kódem
  (`drawRichLabel`), takže hotový PNG odpovídá tomu, co uživatel vidí. Polohy
  přicházejí z téhož snímku přes `onLabels`.
- **`ImageBitmap` nemá společné API na zápis pixelů**, takže přibyl
  `expect fun rgbaToImageBitmap` (wasmJs přes `Image.makeRaster`, `UNPREMUL` –
  `glReadPixels` vrací neprednásobené RGBA). Řádky se převracejí, počátek GL je
  vlevo dole.

Přenos pixelů z JS do wasm jde po jednom bajtu (sdílená paměť neexistuje).
U plátna v řádu megapixelů to trvá zlomky sekundy a je to jednorázová
operace, takže se to neoptimalizovalo.

**Celkem: ≈ 20–30 člověkodní** při zachování vizuální shody s desktopem.
Samotné E1+E2 (≈ týden) už dají použitelný 3D náhled drátěného modelu.

---

## 6. Rizika

| Riziko | Stav |
|---|---|
| Hole-punch v Compose wasm nefunguje | ✅ **vyřešeno** – funguje; plátno je v shadow rootu na `z-index: -1` a Compose si nad ním vyřízne díru `BlendMode.Clear` |
| `EXT_color_buffer_float` chybí | ✅ **ošetřeno** – OIT se vypne a průhledné plochy se míchají přímo, seřazené podle hloubky |
| Rozjetí kódu vůči desktopu | 🟡 trvalé – portované soubory mají v hlavičce odkaz na desktopový zdroj; matematiku držet znakově shodnou |
| Přenos dat wasm→GPU je pomalý | 🟡 **obejito, ne změřeno** – sítě jdou přes cache v `MeshRenderer` klíčovanou podpisem geometrie, takže při orbitu se nepřenáší nic a cena se platí jen při změně scény. Kolik ta jednorázová cena je (`FloatArray` → `Float32Array` se přepisuje po jednom prvku), zůstává nezměřené; projevilo by se to zásekem po editaci rotační plochy, ne poklesem fps |
| `gl_FragDepth` vypíná early-Z | 🟡 nezměřeno – kvadriky ho používají; při propadu fps na slabších GPU omezit jejich počet na obrazovce |
| Přesnost `float` u kuželů | 🟡 **nedoměřeno** – `cone.frag` je jediný shader přepsaný z `double`; artefakty by se projevily zrněním siluety při velkém přiblížení nebo u velkých souřadnic. Zatím nehlášeno, ale ani cíleně netestováno |
| Velikost wasm bundle | ✅ zanedbatelné – shadery jsou textové konstanty (~30 kB) |

---

## 7. Co dělat dál

1. **Dokončit E6**: výkonový rozpočet (cíl 60 fps při orbitu na střední scéně,
   ověřit i na mobilu) a aktualizace `README.md`, `MIGRATION_PLAN.md`
   a `changelog.html`. Export snímku i hláška při chybějícím WebGL2 jsou hotové.
2. Volitelně **export ve zvolené velikosti**: desktop umí render do FBO
   libovolného rozměru a záběr dorovná `cameraForExportFraming`. Web zatím
   exportuje ve velikosti panelu; offscreen cíl se přitom už alokuje podle
   zadaných rozměrů, takže by stačilo doplnit ten přepočet záběru a vstup na
   šířku/výšku.

### Drobnosti, které zbyly pozadu

- popisky stop rovin a přímek se umisťují na pevný podíl délky (78 % resp.
  72 %); desktop má proti překryvům kolizní algoritmus (`placeLabelNearLine3D`)
  a vede si seznam obsazených obdélníků,
- popisek neomezené přímky sedí kousek od bodu, kterým je zadaná – desktop ho
  umí položit na viditelnou část,
- stopy rovin jdou v `axisBatch` spolu s osami, tedy **mimo** klasifikaci
  skrytých hran; na desktopu jí procházejí (blok je po nich rovnou pojmenovaný),
- výplň přímkových ploch je hrubší než na desktopu (`generatorCount` místo
  96 tvořic) – viz E4; přesnější mesh by znamenal port
  `RuledSurfaceGeometry.kt`,
- vybraná přímková plocha se nezvýrazňuje (`RuledSurfaceOutlines.kt` se
  neportoval),
- v počátku nesedí kulička, kterou desktop kreslí spolu s osami
  (`drawSphereMesh3D` v `drawReferenceAxes3D`),
- osy nemají ztlumený „hidden" průchod za tělesy, který desktop kreslí
  `GL_GREATER` s `HIDDEN_LINE_ALPHA`,
- `Gl3DViewport` se při otevřeném dialogu neschovává, ale ani netestoval –
  hole-punch by měl dialogy nechat nahoře, stojí za ověření,
- `gl3dLog` píše do konzole vždy; kdyby to vadilo, dát mu vypínač.
