# Plán migrace MongeCAD → Web Lite

Webová varianta není kopií desktopového modulu. Sdílí jeho názvosloví a
geometrické konvence, ale má vlastní multiplatformní stav bez závislostí na
AWT/Swingu, souborovém systému, Skia PDF a LWJGL/OpenGL.

## Etapa 1 — funkční 2D jádro (hotovo)

- Kotlin/Wasm a Compose Multiplatform 1.9.3
- centralizovaný `MongeState`
- nárys, půdorys a osa x₁₂
- bod z dvojice sdružených průmětů
- úsečka a přímka mezi existujícími body
- výběr, mazání, pan, zoom, undo/redo
- světlý a tmavý režim
- bodová ikona kreslená přímo v Compose, bez závislosti na fontu nebo SVG filtru

## Etapa 2 — praktická práce s výkresem

- přesné číselné zadání a editace souřadnic
- přichytávání na body, osu x₁₂ a svislé ordinály
- pojmenování, barva, tloušťka a viditelnost objektů
- seznam objektů a vícenásobný výběr
- uložení/načtení lehkého JSON formátu přes prohlížeč
- export 2D náhledu do PNG/SVG

## Etapa 3 — lehké konstrukce

- střed úsečky, rovnoběžka, kolmice
- měření délky a úhlu
- rovina ze tří bodů a její stopy
- jednoduché kružnice a mnohoúhelníky v rovině
- bokorys jako volitelná 2D průmětna

Každou konstrukci je potřeba převést jako čistou matematiku bez vazby na
desktopové dialogy a poté pokrýt testy shodnými referenčními body.

## Záměrně mimo webovou variantu

- OpenGL/LWJGL okno a 3D renderer
- PDF a tiskový export
- fill occlusion a odvozování viditelnosti ploch
- obecné průniky těles, ploch a složených objektů
- rotační, přímkové a kvadratické plochy, pokud vyžadují výše uvedené výpočty
- desktopové dialogy, AWT clipboard a přímý přístup k souborovému systému

Tyto položky se nemají přenášet skrytě jako nefunkční tlačítka. Pokud se někdy
vrátí do rozsahu, musí mít samostatný návrh a měřitelný webový prototyp.
