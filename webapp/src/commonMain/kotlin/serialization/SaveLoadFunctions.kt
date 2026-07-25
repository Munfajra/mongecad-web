package serialization
import state.MongeState

/*
 * Čtení výkresu z textu .monge souboru.
 *
 * Souborové IO (PlatformFile, java.io.File, GZIP streamy) zůstalo na desktopu –
 * web čte a zapisuje přes prohlížeč, viz `serialization/MongeFileWeb.kt`.
 */
fun loadMongeStateFromText(text: String, fileName: String): MongeState {

    runCatching {
        MongeJson.decodeFromString(MongeFileV2.serializer(), text)
    }.getOrNull()?.let { v2 ->
        val st = v2.state.toMongeState()

        if (v2.history != null) {
            st.history = v2.history.toHistoryState()
            st.history.snapshots.getOrNull(st.history.cursor)?.let { snap ->
                applySnapshot(st, snap)
            } ?: st.initHistory()
        } else {
            st.initHistory()
        }

        st.displayName = fileName.substringBeforeLast('.', fileName)
        st.isDirty = false
        return st
    }

    runCatching {
        MongeJson.decodeFromString(MongeFileV1.serializer(), text).state
    }.getOrNull()?.let { v1State ->
        return v1State.toMongeState().apply {
            initHistory()
            displayName = fileName.substringBeforeLast('.', fileName)
            isDirty = false
        }
    }

    val legacy = MongeJson.decodeFromString(SerializedMongeState.serializer(), text)
    return legacy.toMongeState().apply {
        initHistory()
        displayName = fileName.substringBeforeLast('.', fileName)
        isDirty = false
    }
}
