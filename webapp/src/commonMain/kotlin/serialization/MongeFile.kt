package serialization

import state.MongeState

/**
 * Otevření a uložení výkresu `.monge`.
 *
 * Desktop pracuje přímo se souborem (`PlatformFile`, `java.io.File`), web
 * musí přes prohlížeč – výběr souboru dialogem a uložení stažením. Implementaci
 * proto dodává platforma; formát i parsování jsou společné.
 *
 * Formát: JSON (`MongeFileV2`), volitelně gzipovaný. Desktop ukládá vždy
 * gzipovaně, načítání pozná obojí podle magic bytes.
 */
expect suspend fun openMongeFile(): MongeState?

expect fun saveMongeFile(state: MongeState)

/** Serializace scény do textu .monge – společná pro obě platformy. */
fun serializeMongeState(state: MongeState): String {
    commitSnapshot(state)
    val wrapper = MongeFileV2(
        state = state.toSerialized(),
        history = state.history.toDto()
    )
    return MongeJson.encodeToString(MongeFileV2.serializer(), wrapper)
}
