# Zbytek nezportovaného kódu

Není v source setu. Obsahuje jen to, co web záměrně nemá:
axonometrie (AXO/AO overlay), kvadriky, tělesa, rotační a přímkové plochy,
šroubovice, průniky, PDF export, OpenGL.

## Postup, který funguje

Tranzitivně od volajícího: přidat jeden soubor, zkompilovat, dohledat
chybějící symboly, přidat. Nikdy jich nebývá víc než pár. Kopírovat celý
strom a mazat kaskáduje a nekonverguje.

Pomůcky ve scratchpadu: `whodef.py <symbol>…`, `extract.py`.

## Hotovo

Kreslení všech objektů, vstupní vrstva (klikání + snapping), náhledy
konstrukce, naming dialogy, pravý panel (SelectionInfo + ObjectList),
undo/redo, perzistence .monge.

## Pozor: geometrie na nesprávných místech

Opakovaný vzor napříč celým portem – kód sedí ve složce podle toho, kdo ho
volal první, ne podle toho, co dělá. Postupně vytaženo (a uklizeno i na
desktopu) do `geometry/`, `model/classes/`, `state/`, `ui/components/`.
Viz `MongeApp/src/main/kotlin/geometry/README.md`.

Když port narazí na další takový případ, patří úklid i na desktop –
jinak stejná past čeká každého, kdo ten kód bude chtít použít jinde.
