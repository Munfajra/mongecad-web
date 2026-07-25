# MongeCAD Web Lite

Osekaná webová varianta MongeCADu postavená na Kotlin/Wasm a Compose Multiplatform.

## První etapa

- Mongeovo 2D plátno s osou x₁₂
- body zadané půdorysem a nárysem
- úsečky a přímky mezi existujícími body
- výběr, mazání, posun, zoom a historie zpět/vpřed
- světlý a tmavý motiv

Záměrně chybí OpenGL/3D okno, PDF a tiskový export, výplně a occlusion,
průniky objektů a další výpočetně nebo platformně náročné desktopové funkce.

## Spuštění

```bash
./gradlew wasmJsBrowserDevelopmentRun
```

Produkční sestavení:

```bash
./gradlew wasmJsBrowserDistribution
```

Výstup vznikne v `build/dist/wasmJs/productionExecutable`.
