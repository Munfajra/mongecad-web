package utils

/**
 * Práce s indexy u názvů průmětů (A, A₁, A₂ …).
 * Na desktopu tyhle dvě funkce bydlí v `dialogs/nameInput/PointsRename.kt`
 * uprostřed dialogů; tady je držíme zvlášť, aby model nemusel táhnout UI.
 */
fun String.withoutProjectionSuffixes(): String {
    var result = trim()
    val suffixes = listOf("₀", "₁", "₂", "₃", "ₐ")
    var changed: Boolean
    do {
        changed = false
        for (suffix in suffixes) {
            if (result.endsWith(suffix)) {
                result = result.removeSuffix(suffix).trimEnd()
                changed = true
            }
        }
    } while (changed)
    return result
}

fun String.withSuffixOnce(suffix: String): String {
    return withoutProjectionSuffixes() + suffix
}
