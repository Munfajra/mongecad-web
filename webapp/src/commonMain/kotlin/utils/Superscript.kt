package utils

fun toSuperscript(text: String): String {
    val superscriptMap = mapOf(
        // latinka
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ',
        'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ', 'k' to 'ᵏ', 'l' to 'ˡ',
        'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ',
        't' to 'ᵗ', 'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ',
        'z' to 'ᶻ',

        // řecká malá – existují jen tyto Unicode horní indexy
        'α' to 'ᵅ',   // alpha
        'β' to 'ᵝ',   // beta
        'γ' to 'ᵞ',   // gamma
        'δ' to 'ᵟ',   // delta
        'ρ' to 'ᵨ',   // rho
        'θ' to 'ᶿ',   // theta   (modifier letter small theta)
        'φ' to 'ᶲ',   // phi     (modifier letter small phi)
        'χ' to 'ᵡ',   // chi

        // velká (upravené náhrady ‒ použijeme stejné jako malé
        // nebo necháme beze změny, pokud preferuješ)
        'Α' to 'ᵅ', 'Β' to 'ᵝ', 'Γ' to 'ᵞ', 'Δ' to 'ᵟ',
        'Ρ' to 'ᵨ', 'Θ' to 'ᶿ', 'Φ' to 'ᶲ', 'Χ' to 'ᵡ'
    )

    return buildString {
        text.forEach { ch -> append(superscriptMap[ch] ?: ch) }
    }
}
