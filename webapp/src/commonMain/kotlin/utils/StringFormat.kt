package utils

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Náhrada za `java.lang.String.format` pro wasm.
 *
 * Desktop používá formátování hlavně na souřadnice a rozměry
 * (`"x: %.1f, y: %.1f".format(...)`), takže podporujeme podmnožinu,
 * kterou appka reálně volá: %f s přesností, %d, %s a %%.
 * Formátuje se vždy v neutrálním locale s desetinnou tečkou –
 * shodně s tím, jak to dělá desktop s výchozím Locale.US chováním
 * u vykreslovaných popisků.
 */
fun String.format(vararg args: Any?): String {
    val sb = StringBuilder()
    var argIndex = 0
    var i = 0

    while (i < length) {
        val ch = this[i]
        if (ch != '%') {
            sb.append(ch)
            i++
            continue
        }

        if (i + 1 < length && this[i + 1] == '%') {
            sb.append('%')
            i += 2
            continue
        }

        // %[flags][width][.precision]conversion
        var j = i + 1
        val flags = StringBuilder()
        while (j < length && this[j] in "-+ 0,#") {
            flags.append(this[j]); j++
        }

        val width = StringBuilder()
        while (j < length && this[j].isDigit()) {
            width.append(this[j]); j++
        }

        var precision = -1
        if (j < length && this[j] == '.') {
            j++
            val p = StringBuilder()
            while (j < length && this[j].isDigit()) {
                p.append(this[j]); j++
            }
            precision = p.toString().toIntOrNull() ?: 0
        }

        if (j >= length) {           // nedokončený specifikátor – ber doslovně
            sb.append(substring(i))
            break
        }

        val conversion = this[j]
        val arg = args.getOrNull(argIndex++)

        var text = when (conversion) {
            'f', 'F' -> formatFixed(toDoubleOrZero(arg), if (precision < 0) 6 else precision)
            'd' -> toLongOrZero(arg).toString()
            's', 'S' -> arg?.toString() ?: "null"
            else -> {
                argIndex--                    // neznámou konverzi neber jako argument
                "%$conversion"
            }
        }

        if (flags.contains('+') && conversion in "fFd" && !text.startsWith("-")) {
            text = "+$text"
        }

        val w = width.toString().toIntOrNull() ?: 0
        if (text.length < w) {
            val pad = if (flags.contains('0') && !flags.contains('-')) '0' else ' '
            text = if (flags.contains('-')) {
                text.padEnd(w, ' ')
            } else if (pad == '0' && (text.startsWith("-") || text.startsWith("+"))) {
                text[0] + text.substring(1).padStart(w - 1, '0')
            } else {
                text.padStart(w, pad)
            }
        }

        sb.append(text)
        i = j + 1
    }

    return sb.toString()
}

private fun toDoubleOrZero(value: Any?): Double = when (value) {
    is Double -> value
    is Float -> value.toDouble()
    is Number -> value.toDouble()
    else -> 0.0
}

private fun toLongOrZero(value: Any?): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Number -> value.toLong()
    else -> 0L
}

private fun formatFixed(value: Double, decimals: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

    val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
    val magnitude = abs(value)

    if (decimals == 0) {
        val rounded = magnitude.roundToLong()
        return if (negative && rounded != 0L) "-$rounded" else rounded.toString()
    }

    val factor = 10.0.pow(decimals)
    val scaled = (magnitude * factor).roundToLong()
    val whole = scaled / factor.toLong()
    val frac = scaled - whole * factor.toLong()
    val fracText = frac.toString().padStart(decimals, '0')

    val body = "$whole.$fracText"
    return if (negative && scaled != 0L) "-$body" else body
}
