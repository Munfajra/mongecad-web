// Webpack hlásí u každého buildu 3 warningy, že bundle přesahuje doporučených
// 244 KiB. To je limit navržený pro běžné JS stránky - Compose/Wasm appka veze
// runtime Skia (skiko.wasm ~8 MiB) a vlastní .wasm, takže se pod něj dostat nedá
// a code splitting tu nedává smysl. Warningy vypínáme, ať jsou v logu vidět
// skutečné problémy.
config.performance = config.performance || {};
config.performance.hints = false;
