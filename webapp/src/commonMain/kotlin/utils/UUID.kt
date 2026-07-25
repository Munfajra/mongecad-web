package utils

import kotlin.random.Random

/**
 * Náhrada za `java.util.UUID` pro wasm.
 *
 * Desktop používá UUID výhradně jako `UUID.randomUUID().toString()` (88 míst),
 * takže stačí držet stejný tvar volání – porty pak mění jen import
 * `java.util.UUID` → `utils.UUID` a nic dalšího.
 */
object UUID {
    fun randomUUID(): String = randomUuidV4String()
}

private const val HEX = "0123456789abcdef"

private fun randomUuidV4String(): String {
    val sb = StringBuilder(36)
    for (i in 0 until 32) {
        when (i) {
            12 -> sb.append('4')                                    // verze 4
            16 -> sb.append(HEX[(Random.nextInt(4) + 8)])            // varianta 10xx
            else -> sb.append(HEX[Random.nextInt(16)])
        }
        if (i == 7 || i == 11 || i == 15 || i == 19) sb.append('-')
    }
    return sb.toString()
}
