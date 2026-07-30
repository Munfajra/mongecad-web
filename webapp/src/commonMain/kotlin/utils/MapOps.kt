package utils

/**
 * Náhrady za JVM default metody `java.util.Map`, které wasm nemá.
 *
 * Stejná logika jako u [replaceAll]: desktop je dostává zdarma z Javy,
 * takže portovaná volání zůstávají beze změny – jen se doplní import.
 */

/** Vloží hodnotu jen tehdy, pokud klíč zatím není obsazený. Vrací původní hodnotu, nebo null. */
fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? {
    val existing = this[key]
    if (existing == null) {
        this[key] = value
        return null
    }
    return existing
}

/** Vrátí hodnotu pro klíč, nebo [defaultValue], pokud klíč chybí. */
fun <K, V> Map<K, V>.getOrDefault(key: K, defaultValue: V): V = this[key] ?: defaultValue
