package utils

/**
 * Náhrada za `java.util.List.replaceAll` (JVM default metoda), kterou wasm nemá.
 *
 * Desktop ji dostává zdarma z Javy, takže volání jako
 * `state.arcsPudorys.replaceAll { ... }` zůstávají beze změny – jen se
 * doplní `import utils.replaceAll`.
 */
fun <T> MutableList<T>.replaceAll(transform: (T) -> T) {
    for (i in indices) {
        this[i] = transform(this[i])
    }
}
