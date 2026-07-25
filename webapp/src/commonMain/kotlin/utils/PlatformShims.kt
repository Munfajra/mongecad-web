package utils

/**
 * Drobné náhrady JVM typů, na kterých visí MongeState.
 *
 * Wasm běží v jednom vlákně, takže atomicita ani concurrent kolekce nejsou
 * potřeba – jde čistě o to udržet stejný tvar API, aby portovaný kód
 * z desktopu nemusel měnit volání.
 */
class AtomicBoolean(initial: Boolean = false) {
    var value: Boolean = initial

    fun get(): Boolean = value

    fun set(newValue: Boolean) {
        value = newValue
    }

    fun compareAndSet(expected: Boolean, newValue: Boolean): Boolean {
        if (value != expected) return false
        value = newValue
        return true
    }

    fun getAndSet(newValue: Boolean): Boolean {
        val previous = value
        value = newValue
        return previous
    }
}

/** Náhrada za `imgui.type.ImInt` – držák celého čísla s tvarem get()/set(). */
class ImInt(private var data: Int = 0) {
    fun get(): Int = data

    fun set(newValue: Int) {
        data = newValue
    }
}

/**
 * Náhrada za `java.lang.System`. Na JVM se `System.currentTimeMillis()` volá
 * bez importu (java.lang je implicitní), na wasm ne – porty proto přidávají
 * `import utils.System` a volání zůstávají beze změny.
 */
expect object System {
    fun currentTimeMillis(): Long
}
