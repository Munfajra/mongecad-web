package serialization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Webová varianta nastavení. API drží stejný tvar jako desktopová
 * `serialization/Settings.kt`, aby portované UI nemuselo nic přepisovat –
 * mění se jen úložiště (localStorage místo settings.json) a odpadají
 * položky vázané na věci, které web nemá (recent files, MSAA pro OpenGL).
 */


@Serializable
data class SettingsState(
    val ncolor: SerializableColor = SerializableColor(1f, 0f, 0f, 1f),
    val pcolor: SerializableColor = SerializableColor(0f, 0f, 1.0f, 1f),
    val rbcolor: SerializableColor = SerializableColor(0f, 0.3f, 0.059f, 1f),
    val bcolor: SerializableColor = SerializableColor(0.9f, 0.9f, 1.0f, 0.5f),
    val msaaSamples: Int = 4,
    var labelSizeFixedPx: Float = 20f,
    var labelSizeScaledPx: Float = 30f,
    val snapThreshold: Float = 20f,
    val referencePlaneSize: Float = 350f,
    val isDarkMode: Boolean = false,
    /**
     * Nová instalace (a starší uložená nastavení bez tohoto pole) přebírá
     * světlý/tmavý motiv z `prefers-color-scheme`. Ruční přepnutí hodnotu vypne.
     */
    val useSystemTheme: Boolean = true,
    val isPinkMode: Boolean = false,
    var scaleLabelsWithCanvas: Boolean = true,
    val planeColor: SerializableColor = SerializableColor(1f, 1f, 1f, 1f),
    var UIscale: Float = DEFAULT_UI_SCALE,
    // Na webu nemáme AWT kurzor, custom kurzor kreslíme sami – viz ui/WebCursor.kt.
    var systemCursor: Boolean = false,
    val lineDashDensity: Float = 1f,
    val customColorPalette: List<SerializableColor> = defaultQuickColorPalette
)

/** Desktop volí 60f na Windows, 75f jinde. Web má jednu hodnotu. */
const val DEFAULT_UI_SCALE: Float = 75f

fun defaultUiScaleForCurrentOs(): Float = DEFAULT_UI_SCALE

val defaultQuickColorPalette = listOf(
    SerializableColor(0f, 0f, 0f, 1f),
    SerializableColor(0.35f, 0.35f, 0.35f, 1f),
    SerializableColor(0.62f, 0.62f, 0.62f, 1f),
    SerializableColor(0.88f, 0.09f, 0.09f, 1f),
    SerializableColor(0.05f, 0.22f, 0.95f, 1f),
    SerializableColor(0.02f, 0.55f, 0.18f, 1f),
    SerializableColor(1f, 0.84f, 0.05f, 1f),
    SerializableColor(0.78f, 0.12f, 0.75f, 1f),
    SerializableColor(0.02f, 0.72f, 0.86f, 1f),
    SerializableColor(0.95f, 0.42f, 0.08f, 1f),
    SerializableColor(0.44f, 0.28f, 0.75f, 1f),
    SerializableColor(0.50f, 0.34f, 0.16f, 1f),
    SerializableColor(0.12f, 0.12f, 0.12f, 1f),
    SerializableColor(1f, 1f, 1f, 1f),
    SerializableColor(0.55f, 0.68f, 0.92f, 1f),
    SerializableColor(0.68f, 0.86f, 0.58f, 1f)
)

/**
 * Perzistence nastavení. Desktop píše do settings.json v AppData,
 * web do localStorage – implementaci dodává platforma.
 */
expect object SettingsStore {
    fun read(): String?
    fun write(text: String)

    /**
     * Preferuje uživatel tmavý režim na úrovni systému / prohlížeče?
     * Použije se jen při prvním spuštění, než si nastavení uloží vlastní volbu.
     */
    fun prefersDarkMode(): Boolean
}

object SettingsManager {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    var runtimeLabelScale by mutableStateOf(1f)

    val SettingsState.activeLabelSizePx: Float
        get() = if (scaleLabelsWithCanvas) labelSizeScaledPx else labelSizeFixedPx

    var current by mutableStateOf(
        SettingsState(
            ncolor = SerializableColor(0.5f, 0.6f, 1f, 1f),
            pcolor = SerializableColor(0.5f, 0.6f, 1f, 1f),
            rbcolor = SerializableColor(0.5f, 0.6f, 1f, 1f),
            bcolor = SerializableColor(0.9f, 0.9f, 1f, 0.5f),
            labelSizeFixedPx = 40f,
            labelSizeScaledPx = 30f,
            snapThreshold = 20f,
            referencePlaneSize = 350f,
            isDarkMode = false,
            isPinkMode = false,
            scaleLabelsWithCanvas = true,
            planeColor = SerializableColor(1f, 1f, 1f, 1f),
            UIscale = DEFAULT_UI_SCALE,
            systemCursor = false,
            lineDashDensity = 1f
        )
    )
        private set

    fun load() {
        val stored = SettingsStore.read()
        val loaded = if (stored != null) {
            runCatching { json.decodeFromString<SettingsState>(stored) }.getOrElse { SettingsState() }
        } else {
            SettingsState()
        }
        current = (
            if (loaded.useSystemTheme) {
                loaded.copy(isDarkMode = SettingsStore.prefersDarkMode(), isPinkMode = false)
            } else {
                loaded
            }
        ).withNormalizedQuickPalette()
    }

    fun save(new: SettingsState) {
        current = new
        runCatching { SettingsStore.write(json.encodeToString(current)) }
    }
}

private fun SettingsState.withNormalizedQuickPalette(): SettingsState {
    if (customColorPalette.size >= defaultQuickColorPalette.size) return this
    return copy(
        customColorPalette = customColorPalette + defaultQuickColorPalette.drop(customColorPalette.size)
    )
}
